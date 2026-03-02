package simplecraft.world;

import java.util.HashSet;
import java.util.Set;

/**
 * A 16-wide, 128-tall, 16-deep container of blocks.<br>
 * Block data is stored as byte ordinals for memory efficiency.<br>
 * Includes mesh dirty flags for intelligent rebuilding.<br>
 * Tracks which blocks were placed by the player (for support physics).
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
	 * Automatically marks the mesh as dirty.
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
		}
	}
	
	/**
	 * Returns true if the given local coordinates are within region bounds.
	 */
	public boolean isInBounds(int x, int y, int z)
	{
		return x >= 0 && x < SIZE_XZ && y >= 0 && y < SIZE_Y && z >= 0 && z < SIZE_XZ;
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
	// Utilities.
	// ========================================================
	
	/**
	 * Fills the region with a flat terrain for testing.<br>
	 * BEDROCK at y=0, STONE up to height-3, DIRT up to height-1, GRASS at height, AIR above.<br>
	 * Automatically marks mesh as dirty.
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
