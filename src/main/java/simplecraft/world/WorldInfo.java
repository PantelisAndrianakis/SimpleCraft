package simplecraft.world;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Metadata about a saved world.<br>
 * Each world lives in {@code ~/.simplecraft/worlds/{dirName}/} with a {@code world_info.txt} file.<br>
 * The directory name is a sanitized version of the display name.<br>
 * The seed is always stored as text. A numeric value for world generation<br>
 * is derived on demand via {@link #getSeedValue()}.
 * @author Pantelis Andrianakis
 * @since February 23rd 2026
 */
public class WorldInfo
{
	private static final String WORLDS_FOLDER = "worlds";
	private static final String INFO_FILE = "world_info.txt";
	private static final String APP_FOLDER = ".simplecraft";
	
	private String _name;
	private String _seed;
	private long _createdAt;
	private long _lastPlayedAt;
	
	/**
	 * Create a new WorldInfo with the given name and seed text.<br>
	 * Sets createdAt and lastPlayedAt to now.
	 * @param name The display name
	 * @param seed The seed text (displayed to player, converted to long for generation)
	 */
	public WorldInfo(String name, String seed)
	{
		_name = name;
		_seed = seed;
		_createdAt = System.currentTimeMillis();
		_lastPlayedAt = _createdAt;
	}
	
	/**
	 * Private constructor for loading from file.
	 */
	private WorldInfo()
	{
	}
	
	public String getName()
	{
		return _name;
	}
	
	public void setName(String name)
	{
		_name = name;
	}
	
	/**
	 * Get the seed text as entered by the player.
	 * @return The seed text
	 */
	public String getSeed()
	{
		return _seed;
	}
	
	public void setSeed(String seed)
	{
		_seed = seed;
	}
	
	/**
	 * Get the numeric seed value for world generation.<br>
	 * If the seed text is a valid long, returns it directly.<br>
	 * Otherwise, hashes the string to produce a long.
	 * @return The numeric seed for terrain generation
	 */
	public long getSeedValue()
	{
		if (_seed == null || _seed.isEmpty())
		{
			return 0;
		}
		
		try
		{
			return Long.parseLong(_seed);
		}
		catch (NumberFormatException e)
		{
			return _seed.hashCode();
		}
	}
	
	public long getCreatedAt()
	{
		return _createdAt;
	}
	
	public void setCreatedAt(long createdAt)
	{
		_createdAt = createdAt;
	}
	
	public long getLastPlayedAt()
	{
		return _lastPlayedAt;
	}
	
	public void setLastPlayedAt(long lastPlayedAt)
	{
		_lastPlayedAt = lastPlayedAt;
	}
	
	/**
	 * Get the sanitized directory name for this world.<br>
	 * Replaces spaces with underscores and strips unsafe characters.
	 * @return The directory-safe name
	 */
	public String getDirectoryName()
	{
		return sanitizeName(_name);
	}
	
	/**
	 * Get the full path to this world's directory.
	 * @return The world directory path
	 */
	public Path getWorldDirectory()
	{
		return getWorldsDirectory().resolve(getDirectoryName());
	}
	
	// --- Static persistence methods ---
	
	/**
	 * Get the root worlds directory ({@code ~/.simplecraft/worlds/}).
	 * @return The worlds directory path
	 */
	public static Path getWorldsDirectory()
	{
		return Paths.get(System.getProperty("user.home"), APP_FOLDER, WORLDS_FOLDER);
	}
	
	/**
	 * Save world metadata to {@code world_info.txt} in the given directory.
	 * @param info The world info to save
	 * @param worldDir The world directory
	 */
	public static void save(WorldInfo info, Path worldDir)
	{
		try
		{
			Files.createDirectories(worldDir);
			
			final Path infoFile = worldDir.resolve(INFO_FILE);
			try (BufferedWriter writer = Files.newBufferedWriter(infoFile))
			{
				writer.write("name=" + info.getName());
				writer.newLine();
				writer.write("seed=" + info.getSeed());
				writer.newLine();
				writer.write("createdAt=" + info.getCreatedAt());
				writer.newLine();
				writer.write("lastPlayedAt=" + info.getLastPlayedAt());
				writer.newLine();
			}
			
			System.out.println("Saved world info: " + info.getName() + " → " + infoFile);
		}
		catch (IOException e)
		{
			System.err.println("ERROR: Failed to save world info: " + e.getMessage());
		}
	}
	
	/**
	 * Load world metadata from {@code world_info.txt} in the given directory.
	 * @param worldDir The world directory
	 * @return The loaded WorldInfo, or null if the file doesn't exist or is invalid
	 */
	public static WorldInfo load(Path worldDir)
	{
		final Path infoFile = worldDir.resolve(INFO_FILE);
		if (!Files.exists(infoFile))
		{
			return null;
		}
		
		final WorldInfo info = new WorldInfo();
		
		try (BufferedReader reader = Files.newBufferedReader(infoFile))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				final int equalsIndex = line.indexOf('=');
				if (equalsIndex < 0)
				{
					continue;
				}
				
				final String key = line.substring(0, equalsIndex).trim();
				final String value = line.substring(equalsIndex + 1).trim();
				
				switch (key)
				{
					case "name":
					{
						info._name = value;
						break;
					}
					case "seed":
					{
						info._seed = value;
						break;
					}
					case "createdAt":
					{
						info._createdAt = Long.parseLong(value);
						break;
					}
					case "lastPlayedAt":
					{
						info._lastPlayedAt = Long.parseLong(value);
						break;
					}
				}
			}
		}
		catch (IOException | NumberFormatException e)
		{
			System.err.println("ERROR: Failed to load world info from " + worldDir + ": " + e.getMessage());
			return null;
		}
		
		// Validate required fields.
		if (info._name == null || info._name.isEmpty())
		{
			System.err.println("WARNING: World info missing name in " + worldDir);
			return null;
		}
		
		return info;
	}
	
	/**
	 * Scan the worlds directory and load all valid worlds.<br>
	 * Returns them sorted by lastPlayedAt (most recent first).
	 * @return List of loaded WorldInfo objects
	 */
	public static List<WorldInfo> loadAllWorlds()
	{
		final List<WorldInfo> worlds = new ArrayList<>();
		final Path worldsDir = getWorldsDirectory();
		
		if (!Files.exists(worldsDir))
		{
			return worlds;
		}
		
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(worldsDir))
		{
			for (Path entry : stream)
			{
				if (!Files.isDirectory(entry))
				{
					continue;
				}
				
				final WorldInfo info = load(entry);
				if (info != null)
				{
					worlds.add(info);
				}
			}
		}
		catch (IOException e)
		{
			System.err.println("ERROR: Failed to scan worlds directory: " + e.getMessage());
		}
		
		// Sort by lastPlayedAt descending (most recent first).
		worlds.sort(Comparator.comparingLong(WorldInfo::getLastPlayedAt).reversed());
		
		return worlds;
	}
	
	/**
	 * Delete a world directory and all its contents.
	 * @param info The world to delete
	 * @return true if deletion succeeded
	 */
	public static boolean delete(WorldInfo info)
	{
		final Path worldDir = info.getWorldDirectory();
		if (!Files.exists(worldDir))
		{
			return false;
		}
		
		try (Stream<Path> walk = Files.walk(worldDir))
		{
			// Delete files first (deepest first), then directories.
			walk.sorted(Comparator.reverseOrder()).forEach(path ->
			{
				try
				{
					Files.delete(path);
				}
				catch (IOException e)
				{
					System.err.println("ERROR: Failed to delete " + path + ": " + e.getMessage());
				}
			});
			
			System.out.println("Deleted world: " + info.getName());
			return true;
		}
		catch (IOException e)
		{
			System.err.println("ERROR: Failed to delete world directory: " + e.getMessage());
			return false;
		}
	}
	
	/**
	 * Check if a world with the given name already exists on disk.
	 * @param name The display name to check
	 * @return true if a directory with the sanitized name exists
	 */
	public static boolean worldExists(String name)
	{
		final Path worldDir = getWorldsDirectory().resolve(sanitizeName(name));
		return Files.exists(worldDir);
	}
	
	/**
	 * Sanitize a world name for use as a directory name.<br>
	 * Replaces spaces with underscores, strips unsafe characters, and converts to lowercase.
	 * @param name The display name
	 * @return The sanitized directory name
	 */
	public static String sanitizeName(String name)
	{
		if (name == null || name.isEmpty())
		{
			return "unnamed";
		}
		
		// Replace spaces with underscores.
		String sanitized = name.replace(' ', '_');
		
		// Strip characters unsafe for directory names.
		sanitized = sanitized.replaceAll("[^a-zA-Z0-9_\\-]", "");
		
		// Convert to lowercase for consistency.
		sanitized = sanitized.toLowerCase();
		
		// Ensure non-empty result.
		if (sanitized.isEmpty())
		{
			sanitized = "unnamed";
		}
		
		return sanitized;
	}
	
	/**
	 * Generate a random seed string.
	 * @return A random numeric seed as text
	 */
	public static String randomSeed()
	{
		return String.valueOf(System.nanoTime());
	}
}
