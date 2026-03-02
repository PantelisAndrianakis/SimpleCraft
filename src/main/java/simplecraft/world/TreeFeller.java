package simplecraft.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Handles tree felling when a WOOD block is broken.<br>
 * Uses a two-phase approach to correctly handle adjacent trees with overlapping canopies:<br>
 * Phase 1 finds all connected WOOD via BFS (through WOOD only, never downward).<br>
 * Phase 2 checks surrounding LEAVES for support from surviving WOOD blocks.<br>
 * <br>
 * Returns an ordered list of block positions for the destruction queue to process<br>
 * with staggered timing and visual effects. Block data is set to AIR silently<br>
 * (for immediate game logic correctness) but visual mesh updates are deferred.
 * @author Pantelis Andrianakis
 * @since March 2nd 2026
 */
public class TreeFeller
{
	// ========================================================
	// Constants.
	// ========================================================
	
	/** Maximum number of WOOD blocks to collect (safety cap). */
	private static final int MAX_WOOD = 200;
	
	/** Maximum search radius from the origin in any axis (safety). */
	private static final int MAX_RADIUS = 32;
	
	/** Manhattan distance to search for LEAVES around each destroyed WOOD block. */
	private static final int LEAF_SEARCH_RADIUS = 4;
	
	/** Manhattan distance to search for surviving WOOD that supports a leaf. */
	private static final int LEAF_SUPPORT_RADIUS = 4;
	
	/** BFS neighbor offsets: UP, NORTH, SOUTH, EAST, WEST (no DOWN). */
	// @formatter:off
	private static final int[][] BFS_OFFSETS =
	{
		{ 0, 1, 0 },  // UP
		{ 0, 0, 1 },  // NORTH
		{ 0, 0, -1 }, // SOUTH
		{ 1, 0, 0 },  // EAST
		{ -1, 0, 0 }  // WEST
	};
	// @formatter:on
	
	// ========================================================
	// Result Class.
	// ========================================================
	
	/**
	 * Contains the ordered list of blocks to destroy from a felled tree.<br>
	 * Wood blocks are sorted bottom-to-top, followed by leaves, then decorations.<br>
	 * Each entry contains the world position and the original block type.
	 */
	public static class FellingResult
	{
		private final List<int[]> _blocks;
		private final List<Block> _blockTypes;
		
		FellingResult(List<int[]> blocks, List<Block> blockTypes)
		{
			_blocks = blocks;
			_blockTypes = blockTypes;
		}
		
		public boolean isEmpty()
		{
			return _blocks.isEmpty();
		}
		
		public int size()
		{
			return _blocks.size();
		}
		
		public List<int[]> getBlocks()
		{
			return _blocks;
		}
		
		public List<Block> getBlockTypes()
		{
			return _blockTypes;
		}
	}
	
	// ========================================================
	// Public API.
	// ========================================================
	
	/**
	 * Identifies all blocks that should be destroyed when a tree is felled.<br>
	 * Sets all identified blocks to AIR silently (for immediate game logic correctness).<br>
	 * Returns an ordered list for the destruction queue to handle visual updates.<br>
	 * The broken block must already be set to AIR before calling this method.
	 * @param world the game world
	 * @param x the world X of the broken WOOD block
	 * @param y the world Y of the broken WOOD block
	 * @param z the world Z of the broken WOOD block
	 * @return ordered list of blocks to visually destroy, or empty result if no tree found
	 */
	public static FellingResult fellTree(World world, int x, int y, int z)
	{
		// Phase 1: Find connected WOOD via BFS.
		final Set<Long> woodToDestroy = findConnectedWood(world, x, y, z);
		if (woodToDestroy.isEmpty())
		{
			return new FellingResult(new ArrayList<>(), new ArrayList<>());
		}
		
		// Phase 2: Find unsupported LEAVES.
		final Set<Long> leavesToDestroy = findUnsupportedLeaves(world, woodToDestroy);
		
		// Phase 3: Find billboard decorations on top of destroyed blocks.
		final Set<Long> allDestroyed = new HashSet<>(woodToDestroy);
		allDestroyed.addAll(leavesToDestroy);
		final Set<Long> decorationsToDestroy = findDecorations(world, allDestroyed);
		
		// Build ordered result: wood (bottom to top), then leaves, then decorations.
		final List<int[]> blocks = new ArrayList<>();
		final List<Block> blockTypes = new ArrayList<>();
		
		// Sort wood by Y ascending (bottom first for visual cascade).
		final List<Long> sortedWood = new ArrayList<>(woodToDestroy);
		sortedWood.sort(Comparator.comparingInt(TreeFeller::unpackY));
		
		for (long key : sortedWood)
		{
			final int bx = unpackX(key);
			final int by = unpackY(key);
			final int bz = unpackZ(key);
			blockTypes.add(world.getBlock(bx, by, bz));
			blocks.add(new int[]
			{
				bx,
				by,
				bz
			});
		}
		
		for (long key : leavesToDestroy)
		{
			final int bx = unpackX(key);
			final int by = unpackY(key);
			final int bz = unpackZ(key);
			blockTypes.add(world.getBlock(bx, by, bz));
			blocks.add(new int[]
			{
				bx,
				by,
				bz
			});
		}
		
		for (long key : decorationsToDestroy)
		{
			final int bx = unpackX(key);
			final int by = unpackY(key);
			final int bz = unpackZ(key);
			blockTypes.add(world.getBlock(bx, by, bz));
			blocks.add(new int[]
			{
				bx,
				by,
				bz
			});
		}
		
		// Set all blocks to AIR silently (game logic is immediately correct).
		for (int[] pos : blocks)
		{
			world.setBlockSilent(pos[0], pos[1], pos[2], Block.AIR);
			world.clearPlayerPlaced(pos[0], pos[1], pos[2]);
		}
		
		return new FellingResult(blocks, blockTypes);
	}
	
	// ========================================================
	// Phase 1: Find Connected WOOD.
	// ========================================================
	
	/**
	 * BFS through WOOD blocks starting from the neighbors of the broken position.<br>
	 * Only spreads UP and horizontally (never DOWN). Only traverses WOOD blocks.
	 */
	private static Set<Long> findConnectedWood(World world, int originX, int originY, int originZ)
	{
		final Set<Long> visited = new HashSet<>();
		final Set<Long> woodToDestroy = new HashSet<>();
		final Queue<long[]> queue = new ArrayDeque<>();
		
		// Seed with neighbors of the broken position (broken block is already AIR).
		for (int[] offset : BFS_OFFSETS)
		{
			final int nx = originX + offset[0];
			final int ny = originY + offset[1];
			final int nz = originZ + offset[2];
			
			// Never search below the broken position.
			if (ny < originY)
			{
				continue;
			}
			
			final long key = packPos(nx, ny, nz);
			if (visited.add(key))
			{
				queue.add(new long[]
				{
					nx,
					ny,
					nz
				});
			}
		}
		
		while (!queue.isEmpty() && woodToDestroy.size() < MAX_WOOD)
		{
			final long[] pos = queue.poll();
			final int px = (int) pos[0];
			final int py = (int) pos[1];
			final int pz = (int) pos[2];
			
			if (world.getBlock(px, py, pz) != Block.WOOD)
			{
				continue;
			}
			
			woodToDestroy.add(packPos(px, py, pz));
			
			// Expand BFS through WOOD neighbors.
			for (int[] offset : BFS_OFFSETS)
			{
				final int nx = px + offset[0];
				final int ny = py + offset[1];
				final int nz = pz + offset[2];
				
				// Never search below the broken position.
				if (ny < originY)
				{
					continue;
				}
				
				// Safety radius check.
				if (Math.abs(nx - originX) > MAX_RADIUS || Math.abs(ny - originY) > MAX_RADIUS || Math.abs(nz - originZ) > MAX_RADIUS)
				{
					continue;
				}
				
				final long key = packPos(nx, ny, nz);
				if (visited.add(key))
				{
					queue.add(new long[]
					{
						nx,
						ny,
						nz
					});
				}
			}
		}
		
		return woodToDestroy;
	}
	
	// ========================================================
	// Phase 2: Find Unsupported LEAVES.
	// ========================================================
	
	/**
	 * For each destroyed WOOD block, searches within Manhattan distance 3 for LEAVES.<br>
	 * Each found leaf is then checked: if no surviving WOOD exists within Manhattan<br>
	 * distance 4, the leaf is marked for destruction.
	 */
	private static Set<Long> findUnsupportedLeaves(World world, Set<Long> woodToDestroy)
	{
		// Collect candidate LEAVES near destroyed WOOD.
		final Set<Long> leavesToCheck = new HashSet<>();
		
		for (long woodKey : woodToDestroy)
		{
			final int wx = unpackX(woodKey);
			final int wy = unpackY(woodKey);
			final int wz = unpackZ(woodKey);
			
			// Search within Manhattan distance LEAF_SEARCH_RADIUS.
			for (int dx = -LEAF_SEARCH_RADIUS; dx <= LEAF_SEARCH_RADIUS; dx++)
			{
				final int remainYZ = LEAF_SEARCH_RADIUS - Math.abs(dx);
				for (int dy = -remainYZ; dy <= remainYZ; dy++)
				{
					final int remainZ = remainYZ - Math.abs(dy);
					for (int dz = -remainZ; dz <= remainZ; dz++)
					{
						final int lx = wx + dx;
						final int ly = wy + dy;
						final int lz = wz + dz;
						
						if (world.getBlock(lx, ly, lz) == Block.LEAVES)
						{
							leavesToCheck.add(packPos(lx, ly, lz));
						}
					}
				}
			}
		}
		
		// Check each candidate leaf for surviving WOOD support.
		final Set<Long> leavesToDestroy = new HashSet<>();
		
		for (long leafKey : leavesToCheck)
		{
			final int lx = unpackX(leafKey);
			final int ly = unpackY(leafKey);
			final int lz = unpackZ(leafKey);
			
			if (!hasSupport(world, lx, ly, lz, woodToDestroy))
			{
				leavesToDestroy.add(leafKey);
			}
		}
		
		return leavesToDestroy;
	}
	
	/**
	 * Checks if a leaf at the given position is supported by any WOOD block<br>
	 * within Manhattan distance {@link #LEAF_SUPPORT_RADIUS} that is NOT in the destroy set.
	 */
	private static boolean hasSupport(World world, int lx, int ly, int lz, Set<Long> woodToDestroy)
	{
		for (int dx = -LEAF_SUPPORT_RADIUS; dx <= LEAF_SUPPORT_RADIUS; dx++)
		{
			final int remainYZ = LEAF_SUPPORT_RADIUS - Math.abs(dx);
			for (int dy = -remainYZ; dy <= remainYZ; dy++)
			{
				final int remainZ = remainYZ - Math.abs(dy);
				for (int dz = -remainZ; dz <= remainZ; dz++)
				{
					final int cx = lx + dx;
					final int cy = ly + dy;
					final int cz = lz + dz;
					
					if (world.getBlock(cx, cy, cz) == Block.WOOD && !woodToDestroy.contains(packPos(cx, cy, cz)))
					{
						return true;
					}
				}
			}
		}
		
		return false;
	}
	
	// ========================================================
	// Phase 3: Find Billboard Decorations.
	// ========================================================
	
	private static Set<Long> findDecorations(World world, Set<Long> allDestroyed)
	{
		final Set<Long> decorationsToDestroy = new HashSet<>();
		
		for (long key : allDestroyed)
		{
			final int bx = unpackX(key);
			final int by = unpackY(key);
			final int bz = unpackZ(key);
			
			final Block above = world.getBlock(bx, by + 1, bz);
			if (above.getRenderMode() == Block.RenderMode.CROSS_BILLBOARD)
			{
				final long aboveKey = packPos(bx, by + 1, bz);
				if (!allDestroyed.contains(aboveKey))
				{
					decorationsToDestroy.add(aboveKey);
				}
			}
		}
		
		return decorationsToDestroy;
	}
	
	// ========================================================
	// Position Packing Utilities.
	// ========================================================
	
	/**
	 * Packs world coordinates into a single long key.<br>
	 * Supports negative coordinates via offset to ensure positive bit patterns.<br>
	 * Each component uses 20 bits with offset 0x80000, supporting ±524K range.
	 */
	private static long packPos(int x, int y, int z)
	{
		return ((long) (x + 0x80000) << 40) | ((long) (y + 0x80000) << 20) | (z + 0x80000);
	}
	
	private static int unpackX(long packed)
	{
		return (int) ((packed >>> 40) & 0xFFFFF) - 0x80000;
	}
	
	private static int unpackY(long packed)
	{
		return (int) ((packed >>> 20) & 0xFFFFF) - 0x80000;
	}
	
	private static int unpackZ(long packed)
	{
		return (int) (packed & 0xFFFFF) - 0x80000;
	}
}
