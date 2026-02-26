package simplecraft.world;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import simplecraft.world.RegionMeshBuilder.RegionMeshData;

/**
 * Generates regions and builds mesh data on background threads.<br>
 * Terrain generation, tree placement, and vertex array building all run off-thread.<br>
 * Completed results are placed in a concurrent queue for the main thread to poll.<br>
 * The main thread only needs to create jME3 Mesh objects and attach geometry (fast).<br>
 * <br>
 * Supports both initial region generation and remeshing of existing regions after block changes.<br>
 * Includes dirty flag checking to avoid unnecessary rebuilds.
 * @author Pantelis Andrianakis
 * @since February 26th 2026
 */
public class RegionLoader
{
	// ========================================================
	// Constants.
	// ========================================================
	
	/** Number of background threads for terrain generation and mesh building. */
	private static final int THREAD_COUNT = 2;
	
	// ========================================================
	// Ready Result Container.
	// ========================================================
	
	/**
	 * Holds a completed region with its pre-built mesh data.<br>
	 * Produced by background threads, consumed by the main thread.
	 */
	public static class ReadyRegion
	{
		private final Region _region;
		private final RegionMeshData _meshData;
		
		public ReadyRegion(Region region, RegionMeshData meshData)
		{
			_region = region;
			_meshData = meshData;
		}
		
		public Region getRegion()
		{
			return _region;
		}
		
		public RegionMeshData getMeshData()
		{
			return _meshData;
		}
	}
	
	// ========================================================
	// Fields.
	// ========================================================
	
	private final ExecutorService _executor;
	private final ConcurrentLinkedQueue<ReadyRegion> _readyQueue = new ConcurrentLinkedQueue<>();
	private final Set<Long> _pendingKeys = ConcurrentHashMap.newKeySet();
	private final long _seed;
	
	/** Cache for quick region lookup during remesh. */
	private final Map<Long, Region> _regionCache = new ConcurrentHashMap<>();
	
	// ========================================================
	// Constructor.
	// ========================================================
	
	/**
	 * Creates a new RegionLoader with background worker threads.
	 * @param seed the world seed for terrain generation
	 */
	public RegionLoader(long seed)
	{
		_seed = seed;
		_executor = Executors.newFixedThreadPool(THREAD_COUNT, runnable ->
		{
			final Thread thread = new Thread(runnable, "RegionLoader");
			thread.setDaemon(true);
			return thread;
		});
	}
	
	// ========================================================
	// Region Key Encoding.
	// ========================================================
	
	/**
	 * Packs two int region coordinates into a single long key.
	 */
	private static long regionKey(int regionX, int regionZ)
	{
		return ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
	}
	
	// ========================================================
	// Load Requests.
	// ========================================================
	
	/**
	 * Submits a region for background generation and mesh building if not already pending.<br>
	 * The background thread performs terrain generation, tree placement, and vertex array<br>
	 * building. The completed result appears in {@link #pollReady()} once finished.
	 * @param regionX the region X coordinate
	 * @param regionZ the region Z coordinate
	 * @return true if the request was submitted, false if already pending
	 */
	public boolean requestLoad(int regionX, int regionZ)
	{
		final long key = regionKey(regionX, regionZ);
		if (!_pendingKeys.add(key))
		{
			return false; // Already pending.
		}
		
		_executor.submit(() ->
		{
			// Check if still pending (may have been cancelled while queued).
			if (!_pendingKeys.contains(key))
			{
				return;
			}
			
			// Heavy work 1: Generate terrain and trees.
			final Region region = new Region(regionX, regionZ);
			TerrainGenerator.generateRegion(region, _seed);
			TreeGenerator.generateTrees(region, _seed);
			
			// Check again after terrain gen (cancel may have arrived).
			if (!_pendingKeys.contains(key))
			{
				return;
			}
			
			// Cache the region for potential future remesh.
			_regionCache.put(key, region);
			
			// Heavy work 2: Build mesh vertex arrays using thread-safe cache for cross-region lookups.
			// Neighbors already in the cache get correct boundary faces on the first build.
			// Neighbors not yet cached return AIR (same as before), fixed by later remesh.
			final RegionMeshData meshData = RegionMeshBuilder.buildRegionMeshData(region, this::getBlock);
			
			// Check if all cardinal neighbors were available during the build.
			// If yes, boundary faces are correct — mark clean so no remesh is needed.
			// If no, leave dirty (Region constructor sets _meshDirty = true).
			final boolean allNeighborsCached = _regionCache.containsKey(regionKey(regionX - 1, regionZ)) && _regionCache.containsKey(regionKey(regionX + 1, regionZ)) && _regionCache.containsKey(regionKey(regionX, regionZ - 1)) && _regionCache.containsKey(regionKey(regionX, regionZ + 1));
			if (allNeighborsCached)
			{
				region.markMeshClean();
			}
			
			_readyQueue.add(new ReadyRegion(region, meshData));
		});
		
		return true;
	}
	
	/**
	 * Submits a remesh request for an existing region.<br>
	 * Unlike load, this does not regenerate terrain — it only rebuilds the mesh<br>
	 * using the existing block data. Cross-region neighbor lookups use the internal<br>
	 * region cache (ConcurrentHashMap) for thread-safe access.<br>
	 * <br>
	 * Checks the region's dirty flag and skips rebuild if mesh is already clean.<br>
	 * The dirty flag is NOT cleared here — the main thread clears it when the result is polled.
	 * @param regionX the region X coordinate
	 * @param regionZ the region Z coordinate
	 * @return true if the request was submitted, false if already pending or mesh is clean
	 */
	public boolean requestRemesh(int regionX, int regionZ)
	{
		final long key = regionKey(regionX, regionZ);
		
		// Get existing region from cache.
		final Region region = _regionCache.get(key);
		if (region == null)
		{
			// Region not in cache — cannot remesh.
			return false;
		}
		
		// Skip if mesh is clean (no changes since last build).
		if (!region.isMeshDirty())
		{
			return false; // Mesh is already up to date.
		}
		
		if (!_pendingKeys.add(key))
		{
			return false; // Already pending.
		}
		
		_executor.submit(() ->
		{
			// Check if still pending (may have been cancelled while queued).
			if (!_pendingKeys.contains(key))
			{
				return;
			}
			
			// Build mesh vertex arrays using thread-safe cache for cross-region lookups.
			final RegionMeshData meshData = RegionMeshBuilder.buildRegionMeshData(region, this::getBlock);
			
			// Do NOT mark clean here — main thread marks clean when the result is polled.
			// This avoids a race where background markMeshClean overwrites a main-thread markMeshDirty.
			_readyQueue.add(new ReadyRegion(region, meshData));
		});
		
		return true;
	}
	
	// ========================================================
	// Result Polling.
	// ========================================================
	
	/**
	 * Polls a completed region with pre-built mesh data from the ready queue.<br>
	 * Returns null if no regions are ready yet. Removes the region from the pending set.
	 * @return a ReadyRegion with Region and RegionMeshData, or null
	 */
	public ReadyRegion pollReady()
	{
		final ReadyRegion result = _readyQueue.poll();
		if (result != null)
		{
			final Region region = result.getRegion();
			_pendingKeys.remove(regionKey(region.getRegionX(), region.getRegionZ()));
		}
		return result;
	}
	
	// ========================================================
	// State Queries.
	// ========================================================
	
	/**
	 * Returns true if the given region is pending generation (submitted but not yet polled).
	 */
	public boolean isPending(long key)
	{
		return _pendingKeys.contains(key);
	}
	
	/**
	 * Cancels a pending region request.<br>
	 * If the background task has not started, it will skip generation.<br>
	 * If already generating, the result will be discarded at poll time.
	 */
	public void cancelPending(long key)
	{
		_pendingKeys.remove(key);
	}
	
	/**
	 * Returns the number of regions currently being generated or awaiting generation.
	 */
	public int getPendingCount()
	{
		return _pendingKeys.size();
	}
	
	/**
	 * Returns the number of completed regions waiting to be polled.
	 */
	public int getReadyCount()
	{
		return _readyQueue.size();
	}
	
	// ========================================================
	// Thread-Safe Block Access.
	// ========================================================
	
	/**
	 * Returns the block at the given world coordinates using the region cache.<br>
	 * Thread-safe: reads from ConcurrentHashMap, safe to call from background threads.<br>
	 * Returns AIR if the region is not cached or coordinates are out of Y bounds.
	 */
	public Block getBlock(int worldX, int worldY, int worldZ)
	{
		// Out of vertical bounds.
		if (worldY < 0 || worldY >= Region.SIZE_Y)
		{
			return Block.AIR;
		}
		
		// Convert world to region coordinates using floor division.
		final int regionX = Math.floorDiv(worldX, Region.SIZE_XZ);
		final int regionZ = Math.floorDiv(worldZ, Region.SIZE_XZ);
		
		final Region region = _regionCache.get(regionKey(regionX, regionZ));
		if (region == null)
		{
			return Block.AIR;
		}
		
		// Convert world to local coordinates using floor modulus.
		final int localX = Math.floorMod(worldX, Region.SIZE_XZ);
		final int localZ = Math.floorMod(worldZ, Region.SIZE_XZ);
		
		return region.getBlock(localX, worldY, localZ);
	}
	
	// ========================================================
	// Region Cache Management.
	// ========================================================
	
	/**
	 * Gets a region from the cache by coordinates.
	 */
	public Region getCachedRegion(int regionX, int regionZ)
	{
		return _regionCache.get(regionKey(regionX, regionZ));
	}
	
	/**
	 * Updates the region cache with a region that has been modified.<br>
	 * Called by the main thread after applying block changes.
	 */
	public void updateRegionCache(Region region)
	{
		final long key = regionKey(region.getRegionX(), region.getRegionZ());
		_regionCache.put(key, region);
	}
	
	/**
	 * Removes a region from the cache when it's unloaded.
	 */
	public void removeFromCache(int regionX, int regionZ)
	{
		final long key = regionKey(regionX, regionZ);
		_regionCache.remove(key);
	}
	
	// ========================================================
	// Shutdown.
	// ========================================================
	
	/**
	 * Shuts down background threads and clears all pending and ready regions.<br>
	 * Called when the world is destroyed (e.g. returning to main menu).
	 */
	public void shutdown()
	{
		_executor.shutdownNow();
		_readyQueue.clear();
		_pendingKeys.clear();
		_regionCache.clear();
	}
}
