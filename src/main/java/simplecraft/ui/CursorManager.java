package simplecraft.ui;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.IntBuffer;
import java.util.EnumMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.lwjgl.BufferUtils;

import com.jme3.cursors.plugins.JmeCursor;
import com.jme3.input.InputManager;

/**
 * Utility class for loading, caching, and applying custom mouse cursors.<br>
 * Cursors are loaded once on first use and cached for fast switching.
 * @author Pantelis Andrianakis
 * @since February 18th 2026
 */
public class CursorManager
{
	private static final String CURSOR_DIR = "assets/images/ui/cursors/";
	private static final int CURSOR_SIZE = 32;
	
	private static final Map<CursorType, JmeCursor> CURSOR_CACHE = new EnumMap<>(CursorType.class);
	
	private static InputManager _inputManager;
	private static CursorType _activeCursor;
	
	/**
	 * Available cursor types and their image files.
	 */
	public enum CursorType
	{
		ARROW("arrow_cursor.png", 0, 0),
		// COMBAT("combat_cursor.png", 16, 16),
		// CROSSHAIR("crosshair_cursor.png", 16, 16),
		// INTERACT("interact_cursor.png", 0, 0),
		;
		
		private final String _fileName;
		private final int _hotSpotX;
		private final int _hotSpotY;
		
		CursorType(String fileName, int hotSpotX, int hotSpotY)
		{
			_fileName = fileName;
			_hotSpotX = hotSpotX;
			_hotSpotY = hotSpotY;
		}
		
		public String getFileName()
		{
			return _fileName;
		}
		
		public int getHotSpotX()
		{
			return _hotSpotX;
		}
		
		public int getHotSpotY()
		{
			return _hotSpotY;
		}
	}
	
	/**
	 * Initializes the CursorManager with the given {@link InputManager} and sets the default cursor.
	 * @param inputManager the {@link InputManager} to apply cursors to
	 */
	public static void initialize(InputManager inputManager)
	{
		_inputManager = inputManager;
		setCursor(CursorType.ARROW);
	}
	
	/**
	 * Switches the active cursor to the given {@link CursorType}.<br>
	 * The cursor is loaded and cached on first use.
	 * @param type the {@link CursorType} to activate
	 */
	public static void setCursor(CursorType type)
	{
		if (_inputManager == null)
		{
			System.err.println("CursorManager not initialized.");
			return;
		}
		
		if (type == _activeCursor)
		{
			return;
		}
		
		JmeCursor cursor = CURSOR_CACHE.get(type);
		if (cursor == null)
		{
			cursor = loadCursor(type);
			if (cursor == null)
			{
				return;
			}
			CURSOR_CACHE.put(type, cursor);
		}
		
		_inputManager.setMouseCursor(cursor);
		_activeCursor = type;
	}
	
	/**
	 * Returns the currently active {@link CursorType}.
	 * @return the active {@link CursorType}, or {@code null} if none is set
	 */
	public static CursorType getActiveCursor()
	{
		return _activeCursor;
	}
	
	/**
	 * Preloads all cursor types into the cache.<br>
	 * Optional - cursors are also loaded on demand by {@link #setCursor(CursorType)}.
	 */
	public static void preloadAll()
	{
		for (CursorType type : CursorType.values())
		{
			if (!CURSOR_CACHE.containsKey(type))
			{
				final JmeCursor cursor = loadCursor(type);
				if (cursor != null)
				{
					CURSOR_CACHE.put(type, cursor);
				}
			}
		}
		
		System.out.println("Preloaded " + CURSOR_CACHE.size() + "/" + CursorType.values().length + " cursors.");
	}
	
	/**
	 * Loads a cursor image from disk, resizes it, and creates a {@link JmeCursor}.
	 * @param type the {@link CursorType} to load
	 * @return the created {@link JmeCursor}, or {@code null} on failure
	 */
	private static JmeCursor loadCursor(CursorType type)
	{
		final String path = CURSOR_DIR + type.getFileName();
		final File cursorFile = new File(path);
		if (!cursorFile.exists())
		{
			System.err.println("Cursor file not found: " + cursorFile.getAbsolutePath());
			return null;
		}
		
		try
		{
			// Load and resize the cursor image.
			final BufferedImage original = ImageIO.read(cursorFile);
			if (original == null)
			{
				System.err.println("Failed to decode cursor image: " + path);
				return null;
			}
			
			final BufferedImage resized = new BufferedImage(CURSOR_SIZE, CURSOR_SIZE, BufferedImage.TYPE_INT_ARGB);
			final Graphics2D g2d = resized.createGraphics();
			g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g2d.drawImage(original, 0, 0, CURSOR_SIZE, CURSOR_SIZE, null);
			g2d.dispose();
			
			// Convert to IntBuffer with flipped Y-axis (JmeCursor expects bottom-up data).
			final IntBuffer imageData = BufferUtils.createIntBuffer(CURSOR_SIZE * CURSOR_SIZE);
			for (int y = CURSOR_SIZE - 1; y >= 0; y--)
			{
				for (int x = 0; x < CURSOR_SIZE; x++)
				{
					imageData.put(resized.getRGB(x, y));
				}
			}
			imageData.flip();
			
			// Create the JmeCursor.
			final JmeCursor cursor = new JmeCursor();
			cursor.setWidth(CURSOR_SIZE);
			cursor.setHeight(CURSOR_SIZE);
			cursor.setNumImages(1);
			cursor.setImagesData(imageData);
			cursor.setxHotSpot(type.getHotSpotX());
			cursor.setyHotSpot(CURSOR_SIZE - 1 - type.getHotSpotY());
			
			System.out.println("Loaded cursor: " + path + " (" + original.getWidth() + "x" + original.getHeight() + " -> " + CURSOR_SIZE + "x" + CURSOR_SIZE + ")");
			
			return cursor;
		}
		catch (IOException e)
		{
			System.err.println("Failed to load cursor: " + path + " - " + e.getMessage());
			return null;
		}
	}
}
