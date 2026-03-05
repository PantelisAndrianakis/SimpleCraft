package simplecraft.world;

import java.util.HashSet;
import java.util.Set;

/**
 * A 16-wide, 128-tall, 16-deep container of blocks.<br>
 * Block data is stored as byte ordinals for memory efficiency.<br>
 * Includes mesh dirty flags for intelligent rebuilding.<br>
 * Tracks which blocks were placed by the player (for support physics).<br>
 * Tracks which blocks were removed by the player (to prevent enemy spawning).<br>
 * Precomputes sky light data for vertex-color-based lighting.
 * @author Pantelis Andrianakis
 * @since February 21st 2026
 */
public class Region
{
	// ========================================================
	// Constants.
	// ========================================================
	
	public static final int SIZE_XZ = 16;
	public static final int SIZE_Y = 128;
	
	/** Light falloff per block below the sky ceiling. depth 1 = 0.75, depth 4+ = 0.05. */
	private static final float SKY_LIGHT_FALLOFF = 0.25f;
	
	/** Minimum sky light level for deep underground blocks. */
	private static final float SKY_LIGHT_MINIMUM = 0.001f;
	
	// ========================================================
	// Fields.
	// ========================================================
	
	private final byte[][][] _blocks = new byte[SIZE_XZ][SIZE_Y][SIZE_XZ];
	private final int _regionX;
	private final int _regionZ;
	
	/** True if the mesh needs to be rebuilt due to block changes. */
	private boolean _meshDirty = true;
	
	/** Timestamp of last mesh build (for debugging/performance monitoring). */
	private long _lastMeshBuildTime = 0;
	
	/** Set of packed local positions of player-placed blocks. */
	private final Set<Long> _playerPlacedBlocks = new HashSet<>();
	
	/** Set of packed local positions of player-removed blocks (prevents enemy spawning). */
	private final Set<Long> _playerRemovedBlocks = new HashSet<>();
	
	/**
	 * Per-column sky light ceiling: the Y of the highest solid/leaf block.<br>
	 * Indexed as [x * SIZE_XZ + z]. Anything at or above this Y has full sky light.
	 */
	private final int[] _skyLightHeight = new int[SIZE_XZ * SIZE_XZ];
	
	/**
	 * Per-column flag: true if the ceiling block is LEAVES (partial shade),<br>
	 * false if it's a solid block (full underground darkness).<br>
	 * Leaves cast soft dappled shadows; solid blocks create cave-like darkness.
	 */
	private final boolean[] _skyLightIsLeaves = new boolean[SIZE_XZ * SIZE_XZ];
	
	/** True if sky light data needs to be recomputed before next mesh build. */
	private boolean _skyLightDirty = true;
	
	// ========================================================
	// Constructor.
	// ========================================================
	
	public Region(int regionX, int regionZ)
	{
		_regionX = regionX;
		_regionZ = regionZ;
	}
	
	// ========================================================
	// Position.
	// ========================================================
	
	public int getRegionX()
	{
		return _regionX;
	}
	
	public int getRegionZ()
	{
		return _regionZ;
	}
	
	/**
	 * Returns the world X coordinate of this region's origin (local x=0).
	 */
	public int getWorldX()
	{
		return _regionX * SIZE_XZ;
	}
	
	/**
	 * Returns the world Z coordinate of this region's origin (local z=0).
	 */
	public int getWorldZ()
	{
		return _regionZ * SIZE_XZ;
	}
	
	// ========================================================
	// Mesh Dirty Flag.
	// ========================================================
	
	/**
	 * Marks the region's mesh as dirty, requiring a rebuild. Called when blocks in this region change.
	 */
	public void markMeshDirty()
	{
		_meshDirty = true;
	}
	
	/**
	 * Returns true if the region's mesh needs to be rebuilt.
	 */
	public boolean isMeshDirty()
	{
		return _meshDirty;
	}
	
	/**
	 * Marks the region's mesh as clean after a successful rebuild.
	 */
	public void markMeshClean()
	{
		_meshDirty = false;
		_lastMeshBuildTime = System.currentTimeMillis();
	}
	
	/**
	 * Returns the timestamp of the last mesh build (for debugging).
	 */
	public long getLastMeshBuildTime()
	{
		return _lastMeshBuildTime;
	}
	
	// ========================================================
	// Block Access.
	// ========================================================
	
	/**
	 * Returns the block at the given local coordinates.<br>
	 * Returns AIR for out-of-bounds positions.
	 */
	public Block getBlock(int x, int y, int z)
	{
		if (!isInBounds(x, y, z))
		{
			return Block.AIR;
		}
		
		return Block.fromOrdinal(_blocks[x][y][z]);
	}
	
	/**
	 * Sets the block at the given local coordinates.<br>
	 * Silently ignores out-of-bounds positions.<br>
	 * Automatically marks the mesh and sky light as dirty.
	 */
	public void setBlock(int x, int y, int z, Block block)
	{
		if (!isInBounds(x, y, z))
		{
			return;
		}
		
		final byte newOrdinal = (byte) block.ordinal();
		
		// Only mark dirty if the block actually changed.
		if (_blocks[x][y][z] != newOrdinal)
		{
			_blocks[x][y][z] = newOrdinal;
			markMeshDirty();
			_skyLightDirty = true;
		}
	}
	
	/**
	 * Sets the block at the given local coordinates WITHOUT marking the mesh dirty.<br>
	 * Used by the destruction queue to update block data immediately for game logic<br>
	 * while deferring the visual mesh rebuild to a later time.
	 */
	public void setBlockSilent(int x, int y, int z, Block block)
	{
		if (!isInBounds(x, y, z))
		{
			return;
		}
		
		_blocks[x][y][z] = (byte) block.ordinal();
		_skyLightDirty = true;
	}
	
	/**
	 * Returns true if the given local coordinates are within region bounds.
	 */
	public boolean isInBounds(int x, int y, int z)
	{
		return x >= 0 && x < SIZE_XZ && y >= 0 && y < SIZE_Y && z >= 0 && z < SIZE_XZ;
	}
	
	// ========================================================
	// Sky Light System.
	// ========================================================
	
	/**
	 * Computes the sky light ceiling height for every (x, z) column in this region.<br>
	 * For each column, scans from the top of the region downward to find the first block<br>
	 * that blocks sunlight (solid blocks and LEAVES). Water does NOT block sunlight.<br>
	 * <br>
	 * Called automatically before mesh builds when the sky light data is dirty.
	 */
	public void computeSkyLight()
	{
		// Phase 1: Compute raw column ceilings.
		for (int x = 0; x < SIZE_XZ; x++)
		{
			for (int z = 0; z < SIZE_XZ; z++)
			{
				final int index = x * SIZE_XZ + z;
				int ceilingY = 0;
				boolean isLeaves = false;
				for (int y = SIZE_Y - 1; y >= 0; y--)
				{
					final Block block = Block.fromOrdinal(_blocks[x][y][z]);
					if (block.isSolid())
					{
						ceilingY = y;
						isLeaves = false;
						break;
					}
					if (block == Block.LEAVES)
					{
						ceilingY = y;
						isLeaves = true;
						break;
					}
				}
				_skyLightHeight[index] = ceilingY;
				_skyLightIsLeaves[index] = isLeaves;
			}
		}
		
		_skyLightDirty = false;
	}
	
	/**
	 * Ensures sky light data is up to date. Call before mesh building.<br>
	 * Only recomputes if the data has been marked dirty by block changes.
	 */
	public void ensureSkyLightComputed()
	{
		if (_skyLightDirty)
		{
			computeSkyLight();
		}
	}
	
	/** Light level under a leaf canopy — soft dappled shade. */
	private static final float LEAF_SHADOW_LIGHT = 0.55f;
	
	/**
	 * Returns the sky light factor for a block at the given local coordinates.<br>
	 * Returns 1.0 for blocks at or above the sky ceiling (full sunlight).<br>
	 * <br>
	 * <b>Under leaves:</b> Returns a constant soft shade ({@value #LEAF_SHADOW_LIGHT}).<br>
	 * Tree canopies filter light but don't create cave-like darkness.<br>
	 * <br>
	 * <b>Under solid blocks:</b> Drops by {@value #SKY_LIGHT_FALLOFF} per block of depth,<br>
	 * minimum {@value #SKY_LIGHT_MINIMUM}. Horizontal spread from nearby open columns<br>
	 * creates gradual falloff into tunnels instead of abrupt cutoffs.<br>
	 * <br>
	 * Does NOT check bounds — caller must ensure valid coordinates.
	 * @param x local X coordinate (0..15)
	 * @param y local Y coordinate (0..127)
	 * @param z local Z coordinate (0..15)
	 * @return sky light factor in the range [0.05, 1.0]
	 */
	public float getSkyLight(int x, int y, int z)
	{
		final int index = x * SIZE_XZ + z;
		final int ceilingY = _skyLightHeight[index];
		
		if (y >= ceilingY)
		{
			return 1.0f;
		}
		
		// Under a leaf canopy: soft constant shade.
		if (_skyLightIsLeaves[index])
		{
			return LEAF_SHADOW_LIGHT;
		}
		
		// Underground: progressive darkness based on depth below ceiling.
		final int depth = ceilingY - y;
		final float light = 1.0f - (depth * SKY_LIGHT_FALLOFF);
		return Math.max(light, SKY_LIGHT_MINIMUM);
	}
	
	// ========================================================
	// Player-Placed Block Tracking.
	// ========================================================
	
	/**
	 * Packs local region coordinates into a single long key for the player-placed set.
	 */
	private static long packLocalPos(int x, int y, int z)
	{
		return ((long) x << 16) | ((long) y << 8) | (z & 0xFF);
	}
	
	/**
	 * Marks the block at the given local coordinates as player-placed.
	 */
	public void markPlayerPlaced(int x, int y, int z)
	{
		if (isInBounds(x, y, z))
		{
			_playerPlacedBlocks.add(packLocalPos(x, y, z));
		}
	}
	
	/**
	 * Clears the player-placed flag for the block at the given local coordinates.
	 */
	public void clearPlayerPlaced(int x, int y, int z)
	{
		if (isInBounds(x, y, z))
		{
			_playerPlacedBlocks.remove(packLocalPos(x, y, z));
		}
	}
	
	/**
	 * Returns true if the block at the given local coordinates was placed by the player.
	 */
	public boolean isPlayerPlaced(int x, int y, int z)
	{
		if (!isInBounds(x, y, z))
		{
			return false;
		}
		
		return _playerPlacedBlocks.contains(packLocalPos(x, y, z));
	}
	
	/**
	 * Returns the set of packed positions of player-placed blocks.<br>
	 * Used for serialization (save/load in Session 17).
	 */
	public Set<Long> getPlayerPlacedSet()
	{
		return _playerPlacedBlocks;
	}
	
	// ========================================================
	// Player-Removed Block Tracking.
	// ========================================================
	
	/**
	 * Marks the block at the given local coordinates as player-removed.<br>
	 * Used to prevent enemy spawning in areas the player has excavated.
	 */
	public void markPlayerRemoved(int x, int y, int z)
	{
		if (isInBounds(x, y, z))
		{
			_playerRemovedBlocks.add(packLocalPos(x, y, z));
		}
	}
	
	/**
	 * Clears the player-removed flag for the block at the given local coordinates.
	 */
	public void clearPlayerRemoved(int x, int y, int z)
	{
		if (isInBounds(x, y, z))
		{
			_playerRemovedBlocks.remove(packLocalPos(x, y, z));
		}
	}
	
	/**
	 * Returns true if the block at the given local coordinates was removed by the player.
	 */
	public boolean isPlayerRemoved(int x, int y, int z)
	{
		if (!isInBounds(x, y, z))
		{
			return false;
		}
		
		return _playerRemovedBlocks.contains(packLocalPos(x, y, z));
	}
	
	/**
	 * Returns the set of packed positions of player-removed blocks.<br>
	 * Used for serialization (save/load in Session 17).
	 */
	public Set<Long> getPlayerRemovedSet()
	{
		return _playerRemovedBlocks;
	}
	
	// ========================================================
	// Utilities.
	// ========================================================
	
	/**
	 * Fills the region with a flat terrain for testing.<br>
	 * BEDROCK at y=0, STONE up to height-3, DIRT up to height-1, GRASS at height, AIR above.<br>
	 * Automatically marks mesh and sky light as dirty.
	 * @param height the Y level of the grass surface layer
	 */
	public void fillFlat(int height)
	{
		for (int x = 0; x < SIZE_XZ; x++)
		{
			for (int z = 0; z < SIZE_XZ; z++)
			{
				for (int y = 0; y < SIZE_Y; y++)
				{
					Block block;
					if (y == 0)
					{
						block = Block.BEDROCK;
					}
					else if (y < height - 2)
					{
						block = Block.STONE;
					}
					else if (y < height)
					{
						block = Block.DIRT;
					}
					else if (y == height)
					{
						block = Block.GRASS;
					}
					else
					{
						block = Block.AIR;
					}
					_blocks[x][y][z] = (byte) block.ordinal();
				}
			}
		}
		
		// Mark dirty after filling.
		markMeshDirty();
		_skyLightDirty = true;
	}
	
	/**
	 * Returns the Y coordinate of the highest non-AIR block in the given column.<br>
	 * Returns -1 if the entire column is AIR.
	 */
	public int getHighestBlock(int x, int z)
	{
		if (x < 0 || x >= SIZE_XZ || z < 0 || z >= SIZE_XZ)
		{
			return -1;
		}
		
		for (int y = SIZE_Y - 1; y >= 0; y--)
		{
			if (_blocks[x][y][z] != Block.AIR.ordinal())
			{
				return y;
			}
		}
		
		return -1;
	}
}
