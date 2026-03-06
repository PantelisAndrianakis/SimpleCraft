package simplecraft.enemy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import com.jme3.math.Vector3f;

import simplecraft.world.Block;
import simplecraft.world.World;

/**
 * A* grid pathfinder for the voxel world.<br>
 * Operates on the XZ block grid, treating each block column as a node.<br>
 * Accounts for ground height, step-up limits, headroom, water avoidance,<br>
 * and diagonal corner-cutting prevention.<br>
 * <br>
 * Usage: call {@link #findPath(World, Vector3f, Vector3f, int, int)} to get<br>
 * a list of waypoints from start to goal, or an empty list if no path exists.
 * @author Pantelis Andrianakis
 * @since March 6th 2026
 */
public class Pathfinder
{
	// ------------------------------------------------------------------
	// Constants.
	// ------------------------------------------------------------------
	
	/** Maximum number of nodes to explore before giving up (prevents lag spikes). */
	private static final int MAX_NODES = 400;
	
	/** Maximum fall distance in blocks that an enemy will accept. */
	private static final int MAX_FALL = 4;
	
	/** Maximum step-up height (blocks). */
	private static final int MAX_STEP_UP = 1;
	
	/** Downward search range when looking for ground at a column. */
	private static final int GROUND_SEARCH_DOWN = 16;
	
	/** Cost for cardinal movement (N, S, E, W). */
	private static final float CARDINAL_COST = 1.0f;
	
	/** Cost for diagonal movement. */
	private static final float DIAGONAL_COST = 1.414f;
	
	/** Small vertical offset to keep waypoint above block surface. */
	private static final float GROUND_OFFSET = 0.55f;
	
	/** 8-direction offsets: dx, dz pairs (4 cardinal + 4 diagonal). */
	private static final int[][] DIRECTIONS =
	{
		// @formatter:off
		{1, 0}, {-1, 0}, {0, 1}, {0, -1}, // Cardinal.
		{1, 1}, {1, -1}, {-1, 1}, {-1, -1} // Diagonal.
		// @formatter:on
	};
	
	/**
	 * Private constructor — utility class.
	 */
	private Pathfinder()
	{
	}
	
	// ------------------------------------------------------------------
	// Public API.
	// ------------------------------------------------------------------
	
	/**
	 * Finds a path from start to goal on the XZ block grid using A*.<br>
	 * Returns a list of world-space waypoints (with Y set to ground height),<br>
	 * or an empty list if no path is found within the search budget.
	 * @param world the voxel world for block queries
	 * @param start the starting world position (enemy feet)
	 * @param goal the goal world position (player feet)
	 * @param headroom number of air blocks needed above ground (2 for humanoids, 1 for small enemies)
	 * @param maxRange maximum search distance in blocks from start (limits search area)
	 * @return list of waypoints from start to goal, empty if unreachable
	 */
	public static List<Vector3f> findPath(World world, Vector3f start, Vector3f goal, int headroom, int maxRange)
	{
		final int startX = (int) Math.floor(start.x);
		final int startZ = (int) Math.floor(start.z);
		final int startY = (int) Math.floor(start.y);
		final int goalX = (int) Math.floor(goal.x);
		final int goalZ = (int) Math.floor(goal.z);
		
		// If start and goal are in the same block column, no path needed.
		if (startX == goalX && startZ == goalZ)
		{
			return Collections.emptyList();
		}
		
		// Find ground Y at start and goal.
		final int startGroundY = findGroundY(world, startX, startZ, startY);
		final int goalGroundY = findGroundY(world, goalX, goalZ, (int) Math.floor(goal.y));
		if (startGroundY < 0 || goalGroundY < 0)
		{
			return Collections.emptyList();
		}
		
		// Open set (priority queue sorted by f = g + h).
		final PriorityQueue<PathNode> openSet = new PriorityQueue<>();
		// Map from packed (x, z) to best-known PathNode for that column.
		final Map<Long, PathNode> allNodes = new HashMap<>();
		
		final PathNode startNode = new PathNode(startX, startZ, startGroundY, 0, octileDistance(startX, startZ, goalX, goalZ), null);
		openSet.add(startNode);
		allNodes.put(packKey(startX, startZ), startNode);
		
		int explored = 0;
		
		while (!openSet.isEmpty() && explored < MAX_NODES)
		{
			final PathNode current = openSet.poll();
			
			// Skip if a better path to this node was already found.
			if (current._closed)
			{
				continue;
			}
			current._closed = true;
			explored++;
			
			// Goal reached?
			if (current._x == goalX && current._z == goalZ)
			{
				return reconstructPath(current);
			}
			
			// Explore neighbors (8 directions).
			for (int d = 0; d < DIRECTIONS.length; d++)
			{
				final int nx = current._x + DIRECTIONS[d][0];
				final int nz = current._z + DIRECTIONS[d][1];
				
				// Range check — don't search too far from start.
				final int distFromStart = Math.max(Math.abs(nx - startX), Math.abs(nz - startZ));
				if (distFromStart > maxRange)
				{
					continue;
				}
				
				// Diagonal corner-cutting prevention: both adjacent cardinals must be passable.
				if (d >= 4) // Diagonal directions.
				{
					final int adjX = current._x + DIRECTIONS[d][0];
					final int adjZ = current._z;
					final int adj2X = current._x;
					final int adj2Z = current._z + DIRECTIONS[d][1];
					
					if (!isColumnPassable(world, adjX, adjZ, current._groundY, headroom) || !isColumnPassable(world, adj2X, adj2Z, current._groundY, headroom))
					{
						continue;
					}
				}
				
				// Find ground at neighbor.
				final int neighborGroundY = findGroundY(world, nx, nz, current._groundY);
				if (neighborGroundY < 0)
				{
					continue; // No valid ground.
				}
				
				// Check step constraints.
				final int heightDiff = neighborGroundY - current._groundY;
				if (heightDiff > MAX_STEP_UP)
				{
					continue; // Wall too high.
				}
				if (heightDiff < -MAX_FALL)
				{
					continue; // Drop too far.
				}
				
				// Headroom check at destination.
				if (!hasHeadroom(world, nx, neighborGroundY, nz, headroom))
				{
					continue;
				}
				
				// Water check — land enemies avoid water.
				final Block footBlock = world.getBlock(nx, neighborGroundY, nz);
				if (footBlock.isLiquid())
				{
					continue;
				}
				
				// Cost calculation.
				final float moveCost = (d < 4) ? CARDINAL_COST : DIAGONAL_COST;
				// Penalize step-ups slightly to prefer flat paths.
				final float stepPenalty = (heightDiff > 0) ? 0.5f : 0;
				final float newG = current._g + moveCost + stepPenalty;
				
				final long key = packKey(nx, nz);
				final PathNode existing = allNodes.get(key);
				
				if (existing != null && existing._g <= newG)
				{
					continue; // Already have a better or equal path.
				}
				
				// If there was an old entry, mark it stale so the PQ skips it.
				if (existing != null)
				{
					existing._closed = true;
				}
				
				final float h = octileDistance(nx, nz, goalX, goalZ);
				final PathNode neighbor = new PathNode(nx, nz, neighborGroundY, newG, h, current);
				allNodes.put(key, neighbor);
				openSet.add(neighbor);
			}
		}
		
		// No path found within budget — return partial path to closest explored node.
		return findClosestPartialPath(allNodes, goalX, goalZ);
	}
	
	// ------------------------------------------------------------------
	// Path reconstruction.
	// ------------------------------------------------------------------
	
	/**
	 * Reconstructs the waypoint list from goal node back to start, then reverses it.<br>
	 * Waypoints are placed at block center (x + 0.5, z + 0.5) with Y at ground height.
	 */
	private static List<Vector3f> reconstructPath(PathNode goalNode)
	{
		final List<Vector3f> path = new ArrayList<>();
		PathNode node = goalNode;
		
		while (node._parent != null) // Skip the start node itself.
		{
			path.add(new Vector3f(node._x + GROUND_OFFSET, node._groundY + 0.05f, node._z + GROUND_OFFSET));
			node = node._parent;
		}
		
		Collections.reverse(path);
		return path;
	}
	
	/**
	 * When A* doesn't reach the goal, returns a partial path to the closest explored node.<br>
	 * This ensures the enemy at least moves toward the player even when no full path exists.
	 */
	private static List<Vector3f> findClosestPartialPath(Map<Long, PathNode> allNodes, int goalX, int goalZ)
	{
		PathNode best = null;
		float bestDist = Float.MAX_VALUE;
		
		for (PathNode node : allNodes.values())
		{
			if (!node._closed)
			{
				continue;
			}
			
			final float dist = octileDistance(node._x, node._z, goalX, goalZ);
			if (dist < bestDist)
			{
				bestDist = dist;
				best = node;
			}
		}
		
		if (best != null && best._parent != null)
		{
			return reconstructPath(best);
		}
		
		return Collections.emptyList();
	}
	
	// ------------------------------------------------------------------
	// World query helpers.
	// ------------------------------------------------------------------
	
	/**
	 * Finds the ground Y (top of highest solid block) at the given XZ column,<br>
	 * searching upward for step-ups and downward for drops.
	 * @return Y of feet level (top of solid), or -1 if no valid ground
	 */
	private static int findGroundY(World world, int bx, int bz, int referenceY)
	{
		for (int y = referenceY + MAX_STEP_UP; y >= referenceY - GROUND_SEARCH_DOWN; y--)
		{
			if (y < 0)
			{
				break;
			}
			
			final Block block = world.getBlock(bx, y, bz);
			final Block above = world.getBlock(bx, y + 1, bz);
			
			if (block.isSolid() && !above.isSolid())
			{
				return y + 1; // Feet stand on top of the solid block.
			}
		}
		
		return -1;
	}
	
	/**
	 * Checks if a column is passable at approximately the given ground Y.<br>
	 * Used for diagonal corner-cutting prevention.
	 */
	private static boolean isColumnPassable(World world, int bx, int bz, int referenceY, int headroom)
	{
		final int groundY = findGroundY(world, bx, bz, referenceY);
		if (groundY < 0)
		{
			return false;
		}
		
		// Step constraint.
		final int heightDiff = groundY - referenceY;
		if (heightDiff > MAX_STEP_UP || heightDiff < -MAX_FALL)
		{
			return false;
		}
		
		// Headroom.
		if (!hasHeadroom(world, bx, groundY, bz, headroom))
		{
			return false;
		}
		
		// Not water.
		return !world.getBlock(bx, groundY, bz).isLiquid();
	}
	
	/**
	 * Checks that there are enough non-solid blocks above ground for the enemy to fit.
	 */
	private static boolean hasHeadroom(World world, int bx, int groundY, int bz, int headroom)
	{
		for (int h = 0; h < headroom; h++)
		{
			if (world.getBlock(bx, groundY + h, bz).isSolid())
			{
				return false;
			}
		}
		return true;
	}
	
	// ------------------------------------------------------------------
	// Heuristic and utility.
	// ------------------------------------------------------------------
	
	/**
	 * Octile distance heuristic (allows 8-directional movement).
	 */
	private static float octileDistance(int x1, int z1, int x2, int z2)
	{
		final int dx = Math.abs(x1 - x2);
		final int dz = Math.abs(z1 - z2);
		return CARDINAL_COST * Math.max(dx, dz) + (DIAGONAL_COST - CARDINAL_COST) * Math.min(dx, dz);
	}
	
	/**
	 * Packs (x, z) into a single long key for hash map lookups.
	 */
	private static long packKey(int x, int z)
	{
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}
	
	// ------------------------------------------------------------------
	// Internal path node.
	// ------------------------------------------------------------------
	
	/**
	 * A single node in the A* search graph.<br>
	 * Represents one block column (x, z) with its known ground height.
	 */
	private static class PathNode implements Comparable<PathNode>
	{
		final int _x;
		final int _z;
		final int _groundY;
		final float _g; // Cost from start.
		final float _h; // Heuristic to goal.
		final PathNode _parent;
		boolean _closed; // True when fully explored or superseded.
		
		PathNode(int x, int z, int groundY, float g, float h, PathNode parent)
		{
			_x = x;
			_z = z;
			_groundY = groundY;
			_g = g;
			_h = h;
			_parent = parent;
			_closed = false;
		}
		
		@Override
		public int compareTo(PathNode other)
		{
			return Float.compare(_g + _h, other._g + other._h);
		}
	}
}
