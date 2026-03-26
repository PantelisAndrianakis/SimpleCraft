package simplecraft.settings;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.lang.management.ManagementFactory;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Discord Rich Presence integration via raw IPC protocol.<br>
 * No external libraries required. Connects to Discord's local IPC pipe and sends SET_ACTIVITY commands using the binary frame protocol.<br>
 * <br>
 * Requires Java 16+ for Unix domain socket support on Linux/Mac.<br>
 * Windows uses named pipes via RandomAccessFile.<br>
 * <br>
 * Usage:<br>
 * 
 * <pre>
 * DiscordRichPresenceManager discord = new DiscordRichPresenceManager();
 * discord.connect();
 * discord.updatePresence("Exploring", "Playing MyWorld", "simplecraft_logo", "SimpleCraft");
 * discord.disconnect();
 * </pre>
 * 
 * @author Pantelis Andrianakis
 * @since March 26th 2026
 */
public class DiscordRichPresenceManager
{
	// Discord IPC opcodes.
	private static final int OP_HANDSHAKE = 0;
	private static final int OP_FRAME = 1;
	// private static final int OP_CLOSE = 2;
	// private static final int OP_PING = 3;
	// private static final int OP_PONG = 4;
	
	// Unique application id from https://discord.com/developers/applications.
	private static final String APPLICATION_ID = "1483415192237772994";
	
	private final int _pid;
	private final long _startEpochSeconds;
	
	private volatile boolean _connected;
	private OutputStream _outputStream;
	private InputStream _inputStream;
	private Closeable _connection; // The underlying pipe or socket channel.
	
	/**
	 * Create a new Discord Rich Presence manager.
	 * @param applicationId Your Discord Application ID from the Developer Portal
	 */
	public DiscordRichPresenceManager()
	{
		_pid = getProcessId();
		_startEpochSeconds = System.currentTimeMillis() / 1000;
	}
	
	/**
	 * Attempt to connect to Discord on a daemon background thread. Fails silently if Discord is not running.
	 */
	public void connect()
	{
		final Thread thread = new Thread(() ->
		{
			try
			{
				openConnection();
				sendHandshake();
				readResponse(); // Read the READY dispatch to confirm connection.
				_connected = true;
				System.out.println("[Discord] Rich Presence connected.");
				
				updatePresence("Playing", null);
			}
			catch (Exception e)
			{
				System.out.println("[Discord] Not available: " + e.getMessage());
				closeQuietly();
			}
		}, "DiscordRPC-Connect");
		thread.setDaemon(true);
		thread.start();
	}
	
	/**
	 * Update the Rich Presence displayed in Discord.
	 * @param details The top detail line (e.g. "Exploring the Overworld")
	 * @param state The bottom state line (e.g. "Playing MyWorld"), or null
	 * @param largeImageKey The asset key for the large image, or null
	 * @param largeImageText Tooltip for the large image, or null
	 */
	public void updatePresence(String details, String state, String largeImageKey, String largeImageText)
	{
		if (!_connected)
		{
			return;
		}
		
		try
		{
			final StringBuilder activity = new StringBuilder();
			activity.append("{");
			
			// Details line.
			if (details != null)
			{
				activity.append("\"details\":\"").append(escapeJson(details)).append("\",");
			}
			
			// State line.
			if (state != null)
			{
				activity.append("\"state\":\"").append(escapeJson(state)).append("\",");
			}
			
			// Timestamps (shows elapsed time).
			activity.append("\"timestamps\":{\"start\":").append(_startEpochSeconds).append("},");
			
			// Assets (images).
			if (largeImageKey != null)
			{
				activity.append("\"assets\":{");
				activity.append("\"large_image\":\"").append(escapeJson(largeImageKey)).append("\"");
				if (largeImageText != null)
				{
					activity.append(",\"large_text\":\"").append(escapeJson(largeImageText)).append("\"");
				}
				activity.append("},");
			}
			
			// Activity type 0 = Playing.
			activity.append("\"type\":0");
			activity.append("}");
			
			final String nonce = UUID.randomUUID().toString();
			final String payload = "{\"cmd\":\"SET_ACTIVITY\",\"args\":{\"pid\":" + _pid + ",\"activity\":" + activity + "},\"nonce\":\"" + nonce + "\"}";
			
			sendFrame(OP_FRAME, payload);
			readResponse(); // Read acknowledgement.
		}
		catch (Exception e)
		{
			System.out.println("[Discord] Failed to update presence: " + e.getMessage());
			_connected = false;
			closeQuietly();
		}
	}
	
	/**
	 * Convenience overload: update with details and state only, using default image.
	 * @param details The top detail line
	 * @param state The bottom state line, or null
	 */
	public void updatePresence(String details, String state)
	{
		updatePresence(details, state, "simplecraft_logo", "SimpleCraft");
	}
	
	/**
	 * Clear the Rich Presence (show nothing).
	 */
	public void clearPresence()
	{
		if (!_connected)
		{
			return;
		}
		
		try
		{
			final String nonce = UUID.randomUUID().toString();
			final String payload = "{\"cmd\":\"SET_ACTIVITY\",\"args\":{\"pid\":" + _pid + "},\"nonce\":\"" + nonce + "\"}";
			sendFrame(OP_FRAME, payload);
			readResponse();
		}
		catch (Exception e)
		{
			System.out.println("[Discord] Failed to clear presence: " + e.getMessage());
		}
	}
	
	/**
	 * Disconnect from Discord IPC. Call on app shutdown.
	 */
	public void disconnect()
	{
		// Mark disconnected first so no further writes are attempted during shutdown.
		_connected = false;
		
		if (_connection != null)
		{
			// Do not send OP_CLOSE here. On some systems (especially Windows named pipes),
			// a final blocking write can hang during process shutdown and freeze app exit.
			// Closing the streams/pipe directly is sufficient for teardown.
			closeQuietly();
			System.out.println("[Discord] Rich Presence disconnected.");
		}
	}
	
	public boolean isConnected()
	{
		return _connected;
	}
	
	// -----------------------------------------------------------------------------------
	// Internal IPC implementation.
	// -----------------------------------------------------------------------------------
	
	/**
	 * Open the IPC connection to Discord. Tries pipe indices 0-9.
	 */
	private void openConnection() throws IOException
	{
		final boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
		
		for (int i = 0; i < 10; i++)
		{
			try
			{
				if (isWindows)
				{
					openWindowsPipe(i);
				}
				else
				{
					openUnixSocket(i);
				}
				
				return; // Success.
			}
			catch (Exception e)
			{
				// Try next pipe index.
			}
		}
		
		throw new IOException("Could not connect to Discord IPC (pipes 0-9 exhausted).");
	}
	
	/**
	 * Windows: Connect via named pipe using RandomAccessFile.
	 */
	private void openWindowsPipe(int pipeIndex) throws IOException
	{
		final String pipePath = "\\\\.\\pipe\\discord-ipc-" + pipeIndex;
		final RandomAccessFile pipe = new RandomAccessFile(pipePath, "rw");
		
		_connection = pipe;
		_outputStream = new OutputStream()
		{
			@Override
			public void write(int b) throws IOException
			{
				pipe.write(b);
			}
			
			@Override
			public void write(byte[] b, int off, int len) throws IOException
			{
				pipe.write(b, off, len);
			}
		};
		_inputStream = new InputStream()
		{
			@Override
			public int read() throws IOException
			{
				return pipe.read();
			}
			
			@Override
			public int read(byte[] b, int off, int len) throws IOException
			{
				return pipe.read(b, off, len);
			}
		};
	}
	
	/**
	 * Linux/Mac: Connect via Unix domain socket (Java 16+).
	 */
	private void openUnixSocket(int pipeIndex) throws IOException
	{
		final String socketPath = getUnixSocketPath(pipeIndex);
		final SocketAddress address = UnixDomainSocketAddress.of(Path.of(socketPath));
		final SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
		channel.connect(address);
		channel.configureBlocking(true);
		
		_connection = channel;
		_outputStream = Channels.newOutputStream(channel);
		_inputStream = Channels.newInputStream(channel);
	}
	
	/**
	 * Resolve the Unix socket path, checking standard directories.
	 */
	private String getUnixSocketPath(int pipeIndex)
	{
		// Check XDG_RUNTIME_DIR first (standard on modern Linux).
		String dir = System.getenv("XDG_RUNTIME_DIR");
		if (dir != null)
		{
			return dir + "/discord-ipc-" + pipeIndex;
		}
		
		// Check TMPDIR (macOS often uses this).
		dir = System.getenv("TMPDIR");
		if (dir != null)
		{
			return dir + "/discord-ipc-" + pipeIndex;
		}
		
		// Check TMP, TEMP.
		dir = System.getenv("TMP");
		if (dir != null)
		{
			return dir + "/discord-ipc-" + pipeIndex;
		}
		
		dir = System.getenv("TEMP");
		if (dir != null)
		{
			return dir + "/discord-ipc-" + pipeIndex;
		}
		
		// Default fallback.
		return "/tmp/discord-ipc-" + pipeIndex;
	}
	
	/**
	 * Send the IPC handshake (opcode 0).
	 */
	private void sendHandshake() throws IOException
	{
		final String payload = "{\"v\":1,\"client_id\":\"" + APPLICATION_ID + "\"}";
		sendFrame(OP_HANDSHAKE, payload);
	}
	
	/**
	 * Send a framed IPC message: [opcode:4 bytes LE][length:4 bytes LE][json payload].
	 */
	private void sendFrame(int opcode, String jsonPayload) throws IOException
	{
		final byte[] payloadBytes = jsonPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		final ByteBuffer header = ByteBuffer.allocate(8);
		header.order(ByteOrder.LITTLE_ENDIAN);
		header.putInt(opcode);
		header.putInt(payloadBytes.length);
		header.flip();
		
		_outputStream.write(header.array());
		_outputStream.write(payloadBytes);
		_outputStream.flush();
	}
	
	/**
	 * Read one framed IPC response. Returns the JSON payload as a String.
	 */
	private String readResponse() throws IOException
	{
		// Read 8-byte header.
		final byte[] headerBytes = readExact(8);
		final ByteBuffer header = ByteBuffer.wrap(headerBytes);
		header.order(ByteOrder.LITTLE_ENDIAN);
		
		// final int opcode = header.getInt(); // Available if needed for dispatch routing.
		header.getInt(); // Skip opcode.
		final int length = header.getInt();
		
		if (length <= 0 || length > 65536)
		{
			throw new IOException("Invalid IPC frame length: " + length);
		}
		
		// Read payload.
		final byte[] payload = readExact(length);
		return new String(payload, java.nio.charset.StandardCharsets.UTF_8);
	}
	
	/**
	 * Read exactly N bytes from the input stream.
	 */
	private byte[] readExact(int n) throws IOException
	{
		final byte[] buffer = new byte[n];
		int offset = 0;
		while (offset < n)
		{
			final int read = _inputStream.read(buffer, offset, n - offset);
			if (read == -1)
			{
				throw new IOException("Discord IPC pipe closed unexpectedly.");
			}
			
			offset += read;
		}
		
		return buffer;
	}
	
	/**
	 * Escape a string for safe JSON embedding.
	 */
	private static String escapeJson(String value)
	{
		if (value == null)
		{
			return "";
		}
		
		final StringBuilder sb = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++)
		{
			final char c = value.charAt(i);
			switch (c)
			{
				case '"':
				{
					sb.append("\\\"");
					break;
				}
				case '\\':
				{
					sb.append("\\\\");
					break;
				}
				case '\n':
				{
					sb.append("\\n");
					break;
				}
				case '\r':
				{
					sb.append("\\r");
					break;
				}
				case '\t':
				{
					sb.append("\\t");
					break;
				}
				default:
				{
					if (c < 0x20)
					{
						sb.append(String.format("\\u%04x", (int) c));
					}
					else
					{
						sb.append(c);
					}
					break;
				}
			}
		}
		
		return sb.toString();
	}
	
	/**
	 * Get the current process ID.
	 */
	private static int getProcessId()
	{
		try
		{
			// Java 9+ approach.
			return (int) ProcessHandle.current().pid();
		}
		catch (Exception e)
		{
			try
			{
				// Fallback: parse from ManagementFactory.
				final String jvmName = ManagementFactory.getRuntimeMXBean().getName();
				return Integer.parseInt(jvmName.split("@")[0]);
			}
			catch (Exception e2)
			{
				return 0;
			}
		}
	}
	
	/**
	 * Quietly close the connection and streams.
	 */
	private void closeQuietly()
	{
		try
		{
			if (_inputStream != null)
			{
				_inputStream.close();
			}
		}
		catch (Exception e)
		{
			// Ignore.
		}
		
		try
		{
			if (_outputStream != null)
			{
				_outputStream.close();
			}
		}
		catch (Exception e)
		{
			// Ignore.
		}
		
		try
		{
			if (_connection != null)
			{
				_connection.close();
			}
		}
		catch (Exception e)
		{
			// Ignore.
		}
		
		_inputStream = null;
		_outputStream = null;
		_connection = null;
	}
}
