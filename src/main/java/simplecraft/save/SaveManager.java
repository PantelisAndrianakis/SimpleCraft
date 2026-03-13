package simplecraft.save;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import simplecraft.SimpleCraft;
import simplecraft.player.PlayerController;
import simplecraft.world.Block;
import simplecraft.world.DayNightCycle;
import simplecraft.world.Region;
import simplecraft.world.World;
import simplecraft.world.WorldInfo;
import simplecraft.world.entity.TileEntityManager;

/**
 * Handles saving and loading world state to/from the active world's directory.<br>
 * Use %USERPROFILE% on Windows.<br>
 * <br>
 * Save directory structure per world:<br>
 * 
 * <pre>
 * ~/.simplecraft/worlds/My_World/
 * ├── world_info.txt     (already exists from WorldInfo)
 * ├── world.dat          (modified region block data, GZip compressed)
 * ├── player.dat         (player position, health, time, respawn point)
 * └── tile_entities.dat  (campfires, chests, crafting tables, furnaces)
 * </pre>
 * 
 * <br>
 * Only regions that have been modified by the player are saved in world.dat.<br>
 * Each modified region stores its full block data, player-placed positions,<br>
 * and player-removed positions.<br>
 * <br>
 * Error handling: never crashes on disk errors — logs and skips.
 * @author Pantelis Andrianakis
 * @since March 9th 2026
 */
public class SaveManager
{
	// ========================================================
	// File Names.
	// ========================================================
	
	private static final String WORLD_FILE = "world.dat";
	private static final String PLAYER_FILE = "player.dat";
	private static final String TILE_ENTITY_FILE = "tile_entities.dat";
	
	// ========================================================
	// Saved Data Containers.
	// ========================================================
	
	/**
	 * Container for saved region data loaded from world.dat.<br>
	 * Holds the raw block bytes and player-placed/removed position sets<br>
	 * for a single region that was modified by the player.
	 */
	public static class SavedRegionData
	{
		private final byte[] _blockData;
		private final Set<Long> _playerPlaced;
		private final Set<Long> _playerRemoved;
		
		public SavedRegionData(byte[] blockData, Set<Long> playerPlaced, Set<Long> playerRemoved)
		{
			_blockData = blockData;
			_playerPlaced = playerPlaced;
			_playerRemoved = playerRemoved;
		}
		
		public byte[] getBlockData()
		{
			return _blockData;
		}
		
		public Set<Long> getPlayerPlaced()
		{
			return _playerPlaced;
		}
		
		public Set<Long> getPlayerRemoved()
		{
			return _playerRemoved;
		}
	}
	
	/**
	 * Container for saved player data loaded from player.dat.<br>
	 * Holds player position, health, selected block, time of day, and respawn points.
	 */
	public static class PlayerSaveData
	{
		private float _posX;
		private float _posY;
		private float _posZ;
		private float _health;
		private int _selectedBlockOrdinal;
		private float _timeOfDay;
		private float _initialSpawnX;
		private float _initialSpawnY;
		private float _initialSpawnZ;
		private boolean _hasCampfireSpawn;
		private float _campfireSpawnX;
		private float _campfireSpawnY;
		private float _campfireSpawnZ;
		
		public float getPosX()
		{
			return _posX;
		}
		
		public float getPosY()
		{
			return _posY;
		}
		
		public float getPosZ()
		{
			return _posZ;
		}
		
		public float getHealth()
		{
			return _health;
		}
		
		public int getSelectedBlockOrdinal()
		{
			return _selectedBlockOrdinal;
		}
		
		public float getTimeOfDay()
		{
			return _timeOfDay;
		}
		
		public float getInitialSpawnX()
		{
			return _initialSpawnX;
		}
		
		public float getInitialSpawnY()
		{
			return _initialSpawnY;
		}
		
		public float getInitialSpawnZ()
		{
			return _initialSpawnZ;
		}
		
		public boolean hasCampfireSpawn()
		{
			return _hasCampfireSpawn;
		}
		
		public float getCampfireSpawnX()
		{
			return _campfireSpawnX;
		}
		
		public float getCampfireSpawnY()
		{
			return _campfireSpawnY;
		}
		
		public float getCampfireSpawnZ()
		{
			return _campfireSpawnZ;
		}
	}
	
	// ========================================================
	// Save.
	// ========================================================
	
	/**
	 * Saves the current world state to the active world's directory.<br>
	 * Writes world.dat (modified regions), player.dat (player state),<br>
	 * and tile_entities.dat (all tile entities).<br>
	 * Also updates WorldInfo.lastPlayedAt and re-saves world_info.txt.
	 * @param world the game world
	 * @param player the player controller
	 * @param dayNightCycle the day/night cycle (for time of day)
	 */
	public static void save(World world, PlayerController player, DayNightCycle dayNightCycle)
	{
		final WorldInfo activeWorld = SimpleCraft.getInstance().getActiveWorld();
		if (activeWorld == null)
		{
			System.err.println("SaveManager: No active world — cannot save.");
			return;
		}
		
		final Path worldDir = activeWorld.getWorldDirectory();
		
		try
		{
			Files.createDirectories(worldDir);
		}
		catch (IOException e)
		{
			System.err.println("SaveManager: Failed to create world directory: " + e.getMessage());
			return;
		}
		
		// Save modified region data.
		saveWorldData(world, worldDir);
		
		// Save player state.
		savePlayerData(player, dayNightCycle, worldDir);
		
		// Save tile entities.
		saveTileEntityData(world.getTileEntityManager(), worldDir);
		
		// Update last played timestamp.
		activeWorld.setLastPlayedAt(System.currentTimeMillis());
		WorldInfo.save(activeWorld, worldDir);
		
		System.out.println("SaveManager: World saved to " + worldDir);
	}
	
	/**
	 * Saves modified region data to world.dat (GZip compressed).<br>
	 * Format: [int regionCount] then per region:<br>
	 * [int regionX][int regionZ][byte[16*128*16] blockData]<br>
	 * [int playerPlacedCount][long... packedPositions]<br>
	 * [int playerRemovedCount][long... packedPositions]
	 */
	private static void saveWorldData(World world, Path worldDir)
	{
		final List<Region> modifiedRegions = world.getModifiedRegions();
		if (modifiedRegions.isEmpty())
		{
			System.out.println("SaveManager: No modified regions to save.");
			return;
		}
		
		final Path file = worldDir.resolve(WORLD_FILE);
		
		try (OutputStream os = Files.newOutputStream(file);
			GZIPOutputStream gzip = new GZIPOutputStream(os);
			DataOutputStream out = new DataOutputStream(gzip))
		{
			out.writeInt(modifiedRegions.size());
			
			for (Region region : modifiedRegions)
			{
				// Region coordinates.
				out.writeInt(region.getRegionX());
				out.writeInt(region.getRegionZ());
				
				// Raw block data (flat byte array).
				final byte[] blockData = region.getRawBlockData();
				out.write(blockData);
				
				// Player-placed positions.
				final Set<Long> playerPlaced = region.getPlayerPlacedSet();
				out.writeInt(playerPlaced.size());
				for (long packed : playerPlaced)
				{
					out.writeLong(packed);
				}
				
				// Player-removed positions.
				final Set<Long> playerRemoved = region.getPlayerRemovedSet();
				out.writeInt(playerRemoved.size());
				for (long packed : playerRemoved)
				{
					out.writeLong(packed);
				}
			}
			
			System.out.println("SaveManager: Saved " + modifiedRegions.size() + " modified regions.");
		}
		catch (IOException e)
		{
			System.err.println("SaveManager: Failed to save world data: " + e.getMessage());
		}
	}
	
	/**
	 * Saves player state to player.dat.<br>
	 * Format: position (3 floats), health (float), selectedBlock ordinal (int),<br>
	 * timeOfDay (float), initialSpawn (3 floats), hasCampfireSpawn (boolean),<br>
	 * campfireSpawn (3 floats if hasCampfireSpawn).
	 */
	private static void savePlayerData(PlayerController player, DayNightCycle dayNightCycle, Path worldDir)
	{
		final Path file = worldDir.resolve(PLAYER_FILE);
		
		try (OutputStream os = Files.newOutputStream(file);
			DataOutputStream out = new DataOutputStream(os))
		{
			// Position.
			out.writeFloat(player.getPosition().x);
			out.writeFloat(player.getPosition().y);
			out.writeFloat(player.getPosition().z);
			
			// Health.
			out.writeFloat(player.getHealth());
			
			// Selected block ordinal.
			final Block block = player.getSelectedBlock();
			out.writeInt(block != null ? block.ordinal() : 0);
			
			// Time of day.
			out.writeFloat(dayNightCycle.getTimeOfDay());
			
			// Initial spawn point.
			out.writeFloat(player.getInitialSpawn().x);
			out.writeFloat(player.getInitialSpawn().y);
			out.writeFloat(player.getInitialSpawn().z);
			
			// Campfire spawn point.
			final boolean hasCampfire = player.getCampfireSpawn() != null;
			out.writeBoolean(hasCampfire);
			if (hasCampfire)
			{
				out.writeFloat(player.getCampfireSpawn().x);
				out.writeFloat(player.getCampfireSpawn().y);
				out.writeFloat(player.getCampfireSpawn().z);
			}
		}
		catch (IOException e)
		{
			System.err.println("SaveManager: Failed to save player data: " + e.getMessage());
		}
	}
	
	/**
	 * Saves tile entity data to tile_entities.dat.<br>
	 * Uses TileEntityManager's built-in serialization.
	 */
	private static void saveTileEntityData(TileEntityManager tileEntityManager, Path worldDir)
	{
		final Path file = worldDir.resolve(TILE_ENTITY_FILE);
		
		try (BufferedWriter writer = Files.newBufferedWriter(file))
		{
			final String data = tileEntityManager.serializeAll();
			writer.write(data);
		}
		catch (IOException e)
		{
			System.err.println("SaveManager: Failed to save tile entity data: " + e.getMessage());
		}
	}
	
	// ========================================================
	// Load.
	// ========================================================
	
	/**
	 * Loads player save data from player.dat in the active world's directory.
	 * @return the loaded player data, or null if no save exists or read fails
	 */
	public static PlayerSaveData loadPlayerData()
	{
		final WorldInfo activeWorld = SimpleCraft.getInstance().getActiveWorld();
		if (activeWorld == null)
		{
			return null;
		}
		
		final Path file = activeWorld.getWorldDirectory().resolve(PLAYER_FILE);
		if (!Files.exists(file))
		{
			return null;
		}
		
		try (InputStream is = Files.newInputStream(file);
			DataInputStream in = new DataInputStream(is))
		{
			final PlayerSaveData data = new PlayerSaveData();
			
			// Position.
			data._posX = in.readFloat();
			data._posY = in.readFloat();
			data._posZ = in.readFloat();
			
			// Health.
			data._health = in.readFloat();
			
			// Selected block ordinal.
			data._selectedBlockOrdinal = in.readInt();
			
			// Time of day.
			data._timeOfDay = in.readFloat();
			
			// Initial spawn point.
			data._initialSpawnX = in.readFloat();
			data._initialSpawnY = in.readFloat();
			data._initialSpawnZ = in.readFloat();
			
			// Campfire spawn point.
			data._hasCampfireSpawn = in.readBoolean();
			if (data._hasCampfireSpawn)
			{
				data._campfireSpawnX = in.readFloat();
				data._campfireSpawnY = in.readFloat();
				data._campfireSpawnZ = in.readFloat();
			}
			
			System.out.println("SaveManager: Loaded player data from " + file);
			return data;
		}
		catch (IOException e)
		{
			System.err.println("SaveManager: Failed to load player data: " + e.getMessage());
			return null;
		}
	}
	
	/**
	 * Loads modified region data from world.dat (GZip compressed) in the active world's directory.<br>
	 * Returns a map of packed region key → SavedRegionData.
	 * @return the loaded region data map, or null if no save exists or read fails
	 */
	public static ConcurrentHashMap<Long, SavedRegionData> loadWorldData()
	{
		final WorldInfo activeWorld = SimpleCraft.getInstance().getActiveWorld();
		if (activeWorld == null)
		{
			return null;
		}
		
		final Path file = activeWorld.getWorldDirectory().resolve(WORLD_FILE);
		if (!Files.exists(file))
		{
			return null;
		}
		
		try (InputStream is = Files.newInputStream(file);
			GZIPInputStream gzip = new GZIPInputStream(is);
			DataInputStream in = new DataInputStream(gzip))
		{
			final int regionCount = in.readInt();
			final ConcurrentHashMap<Long, SavedRegionData> savedData = new ConcurrentHashMap<>();
			
			for (int i = 0; i < regionCount; i++)
			{
				// Region coordinates.
				final int regionX = in.readInt();
				final int regionZ = in.readInt();
				
				// Raw block data.
				final int dataSize = Region.SIZE_XZ * Region.SIZE_Y * Region.SIZE_XZ;
				final byte[] blockData = new byte[dataSize];
				in.readFully(blockData);
				
				// Player-placed positions.
				final int placedCount = in.readInt();
				final Set<Long> playerPlaced = new HashSet<>();
				for (int j = 0; j < placedCount; j++)
				{
					playerPlaced.add(in.readLong());
				}
				
				// Player-removed positions.
				final int removedCount = in.readInt();
				final Set<Long> playerRemoved = new HashSet<>();
				for (int j = 0; j < removedCount; j++)
				{
					playerRemoved.add(in.readLong());
				}
				
				final long key = World.packRegionKey(regionX, regionZ);
				savedData.put(key, new SavedRegionData(blockData, playerPlaced, playerRemoved));
			}
			
			in.close();
			
			System.out.println("SaveManager: Loaded " + regionCount + " saved regions from " + file);
			return savedData;
		}
		catch (IOException e)
		{
			System.err.println("SaveManager: Failed to load world data: " + e.getMessage());
			return null;
		}
	}
	
	/**
	 * Loads tile entity data from tile_entities.dat in the active world's directory.
	 * @return the serialized tile entity string, or null if no save exists or read fails
	 */
	public static String loadTileEntityData()
	{
		final WorldInfo activeWorld = SimpleCraft.getInstance().getActiveWorld();
		if (activeWorld == null)
		{
			return null;
		}
		
		final Path file = activeWorld.getWorldDirectory().resolve(TILE_ENTITY_FILE);
		if (!Files.exists(file))
		{
			return null;
		}
		
		try (BufferedReader reader = Files.newBufferedReader(file))
		{
			final StringBuilder sb = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (sb.length() > 0)
				{
					sb.append('\n');
				}
				sb.append(line);
			}
			
			System.out.println("SaveManager: Loaded tile entity data from " + file);
			return sb.toString();
		}
		catch (IOException e)
		{
			System.err.println("SaveManager: Failed to load tile entity data: " + e.getMessage());
			return null;
		}
	}
	
	// ========================================================
	// Utility.
	// ========================================================
	
	/**
	 * Returns true if the given world has save data (world.dat exists).
	 * @param world the world info to check
	 * @return true if a save file exists
	 */
	public static boolean hasSaveData(WorldInfo world)
	{
		if (world == null)
		{
			return false;
		}
		
		final Path file = world.getWorldDirectory().resolve(WORLD_FILE);
		return Files.exists(file);
	}
}
