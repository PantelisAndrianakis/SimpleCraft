package simplecraft.world;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

/**
 * Handles collapse of unsupported player-placed blocks.<br>
 * When any block is broken, nearby player-placed blocks are checked for grounding.<br>
 * A player-placed block is grounded if it can trace a path through solid blocks<br>
 * (player-placed or natural) to at least one world-generated solid block.<br>
 * If an entire connected group of player-placed blocks has no path to natural terrain,<br>
 * the whole group collapses.<br>
 * <br>
 * World-generated blocks never need support checks, so natural overhangs<br>
 * and cave ceilings remain intact.
 * @author Pantelis Andrianakis
 * @since March 2nd 2026
 */
public class BlockSupport
{
	// ========================================================
	// Constants.
	// ========================================================
	
	/** Maximum number of blocks to traverse when checking grounding (safety cap). */
	private static final int MAX_FLOOD = 200;
	
	/** Maximum total blocks that can collapse in one event (safety cap). */
	private static final int MAX_COLLAPSE = 100;
	
	/**
	 * Support neighbor offsets: DOWN, NORTH, SOUTH, EAST, WEST.<br>
	 * UP does not count as support — hanging blocks fall.
	 */
	// @formatter:off
	private static final int[][] SUPPORT_OFFSETS =
	{
		{ 0, -1, 0 },  // DOWN
		{ 0, 0, 1 },   // NORTH
		{ 0, 0, -1 },  // SOUTH
		{ 1, 0, 0 },   // EAST
		{ -1, 0, 0 }   // WEST
	};
	// @formatter:on
	
	/** All 6 neighbor offsets for finding adjacent blocks to re-check. */
	// @formatter:off
	private static final int[][] ALL_NEIGHBOR_OFFSETS =
	{
		{ 0, 1, 0 },   // UP
		{ 0, -1, 0 },  // DOWN
		{ 0, 0, 1 },   // NORTH
		{ 0, 0, -1 },  // SOUTH
		{ 1, 0, 0 },   // EAST
		{ -1, 0, 0 }   // WEST
	};
	// @formatter:on
	
	// ========================================================
	// Public API.
	// ========================================================
	
	/**
	 * Checks if any player-placed blocks adjacent to the broken position have lost<br>
	 * their grounding and should collapse. Handles chain reactions.<br>
	 * Called after ANY block is broken (player-placed or natural).<br>
	 * <br>
	 * A connected group of player-placed blocks is grounded if ANY block in the group<br>
	 * has a solid world-generated (non-player-placed) neighbor below or horizontally.<br>
	 * If the group has no path to natural terrain, the entire group collapses.
	 * @param world the game world
	 * @param bx the world X of the broken block
	 * @param by the world Y of the broken block
	 * @param bz the world Z of the broken block
	 */
	public static void checkSupport(World world, int bx, int by, int bz)
	{
		// Track all blocks already confirmed grounded or already collapsed to avoid rechecking.
		final Set<Long> confirmedGrounded = new HashSet<>();
		final Set<Long> confirmedCollapsed = new HashSet<>();
		int totalCollapsed = 0;
		
		// Gather unique player-placed neighbors of the broken block to check.
		final Queue<Long> candidateQueue = new ArrayDeque<>();
		final Set<Long> candidateSeen = new HashSet<>();
		
		for (int[] offset : ALL_NEIGHBOR_OFFSETS)
		{
			final int nx = bx + offset[0];
			final int ny = by + offset[1];
			final int nz = bz + offset[2];
			
			if (world.isPlayerPlaced(nx, ny, nz))
			{
				final Block block = world.getBlock(nx, ny, nz);
				if (block != Block.AIR)
				{
					final long key = packPos(nx, ny, nz);
					if (candidateSeen.add(key))
					{
						candidateQueue.add(key);
					}
				}
			}
		}
		
		boolean anyRemoved = false;
		
		// Process candidates — each may trigger cascade checks.
		while (!candidateQueue.isEmpty() && totalCollapsed < MAX_COLLAPSE)
		{
			final long startKey = candidateQueue.poll();
			
			// Skip if already resolved.
			if (confirmedGrounded.contains(startKey) || confirmedCollapsed.contains(startKey))
			{
				continue;
			}
			
			final int sx = unpackX(startKey);
			final int sy = unpackY(startKey);
			final int sz = unpackZ(startKey);
			
			// Skip if already destroyed by a prior group collapse.
			if (world.getBlock(sx, sy, sz) == Block.AIR)
			{
				continue;
			}
			
			// Flood-fill through player-placed blocks to find connected group.
			final FloodResult result = floodFillGroup(world, sx, sy, sz, confirmedGrounded);
			
			if (result._grounded)
			{
				// Entire group is grounded — mark all as confirmed.
				confirmedGrounded.addAll(result._group);
			}
			else
			{
				// No natural support — collapse the entire group.
				for (long key : result._group)
				{
					if (totalCollapsed >= MAX_COLLAPSE)
					{
						break;
					}
					
					final int px = unpackX(key);
					final int py = unpackY(key);
					final int pz = unpackZ(key);
					
					final Block block = world.getBlock(px, py, pz);
					if (block == Block.AIR)
					{
						continue;
					}
					
					world.setBlockNoRebuild(px, py, pz, Block.AIR);
					world.clearPlayerPlaced(px, py, pz);
					confirmedCollapsed.add(key);
					totalCollapsed++;
					anyRemoved = true;
					
					// TODO: Drop item at position (Session 19).
					System.out.println("Unsupported player block " + block.name() + " collapsed at [" + px + ", " + py + ", " + pz + "]");
					
					// Collapsing this block may unground adjacent groups — add their neighbors.
					for (int[] offset : ALL_NEIGHBOR_OFFSETS)
					{
						final int nx = px + offset[0];
						final int ny = py + offset[1];
						final int nz = pz + offset[2];
						final long nKey = packPos(nx, ny, nz);
						
						if (!confirmedGrounded.contains(nKey) && !confirmedCollapsed.contains(nKey) && candidateSeen.add(nKey))
						{
							if (world.isPlayerPlaced(nx, ny, nz) && world.getBlock(nx, ny, nz) != Block.AIR)
							{
								candidateQueue.add(nKey);
							}
						}
					}
				}
			}
		}
		
		// Batched rebuild if any blocks were removed.
		if (anyRemoved)
		{
			world.rebuildDirtyRegionsImmediate();
		}
	}
	
	// ========================================================
	// Flood Fill Grounding Check.
	// ========================================================
	
	/**
	 * Result of a flood-fill group search.
	 */
	private static class FloodResult
	{
		/** All player-placed block positions in the connected group. */
		final Set<Long> _group;
		
		/** True if the group is grounded (touches natural terrain). */
		final boolean _grounded;
		
		FloodResult(Set<Long> group, boolean grounded)
		{
			_group = group;
			_grounded = grounded;
		}
	}
	
	/**
	 * Flood-fills through connected player-placed blocks starting from the given position.<br>
	 * At each block, checks the 5 support directions (DOWN + 4 horizontal) for a solid<br>
	 * world-generated block. If found, the group is grounded and the search stops early.<br>
	 * Also stops early if any block in the group is adjacent to an already confirmed-grounded block.
	 * @param world the game world
	 * @param startX starting world X
	 * @param startY starting world Y
	 * @param startZ starting world Z
	 * @param confirmedGrounded set of positions already known to be grounded
	 * @return the connected group and whether it is grounded
	 */
	private static FloodResult floodFillGroup(World world, int startX, int startY, int startZ, Set<Long> confirmedGrounded)
	{
		final Set<Long> group = new HashSet<>();
		final Queue<long[]> queue = new ArrayDeque<>();
		
		final long startKey = packPos(startX, startY, startZ);
		group.add(startKey);
		queue.add(new long[]
		{
			startX,
			startY,
			startZ
		});
		
		while (!queue.isEmpty() && group.size() < MAX_FLOOD)
		{
			final long[] pos = queue.poll();
			final int px = (int) pos[0];
			final int py = (int) pos[1];
			final int pz = (int) pos[2];
			
			// Check if this block touches natural terrain via support directions.
			for (int[] offset : SUPPORT_OFFSETS)
			{
				final int nx = px + offset[0];
				final int ny = py + offset[1];
				final int nz = pz + offset[2];
				
				final Block neighbor = world.getBlock(nx, ny, nz);
				if (neighbor.isSolid() && !world.isPlayerPlaced(nx, ny, nz))
				{
					// Found natural support — entire group is grounded.
					return new FloodResult(group, true);
				}
				
				// Also check if adjacent to a confirmed-grounded player block.
				if (neighbor.isSolid() && confirmedGrounded.contains(packPos(nx, ny, nz)))
				{
					return new FloodResult(group, true);
				}
			}
			
			// Expand through adjacent player-placed blocks (all 6 directions for connectivity).
			for (int[] offset : ALL_NEIGHBOR_OFFSETS)
			{
				final int nx = px + offset[0];
				final int ny = py + offset[1];
				final int nz = pz + offset[2];
				final long nKey = packPos(nx, ny, nz);
				
				if (group.contains(nKey))
				{
					continue;
				}
				
				if (world.isPlayerPlaced(nx, ny, nz) && world.getBlock(nx, ny, nz) != Block.AIR)
				{
					group.add(nKey);
					queue.add(new long[]
					{
						nx,
						ny,
						nz
					});
				}
			}
		}
		
		// Exhausted search without finding natural support — not grounded.
		return new FloodResult(group, false);
	}
	
	// ========================================================
	// Position Packing Utilities.
	// ========================================================
	
	/**
	 * Packs world coordinates into a single long key.<br>
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
