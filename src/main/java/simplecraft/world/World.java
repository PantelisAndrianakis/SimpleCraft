package simplecraft.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.material.RenderState.FaceCullMode;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;

import simplecraft.world.RegionLoader.ReadyRegion;
import simplecraft.world.RegionMeshBuilder.RegionMeshResult;

/**
 * Manages a collection of regions that form the game world.<br>
 * Regions are stored in a HashMap keyed by a packed long of (regionX, regionZ).<br>
 * Provides world-coordinate block access that automatically resolves to the correct region.<br>
 * Dynamically loads and unloads regions around the camera position each frame.<br>
 * <br>
 * Terrain generation and mesh vertex building run on background threads via {@link RegionLoader}.<br>
 * Each frame the main thread polls for completed results and performs only the lightweight<br>
 * jME3 Mesh creation and scene-graph attach, limited to {@link #ATTACHES_PER_FRAME}.<br>
 * The first update bypasses the throttle so the initial view is fully populated<br>
 * (hidden behind the fade-in transition).<br>
 * <br>
 * Block changes are queued and processed asynchronously to prevent main-thread stalls.<br>
 * Includes mesh dirty flag optimization to avoid unnecessary rebuilds.
 * @author Pantelis Andrianakis
 * @since February 23rd 2026
 */
public class World
{
	// ========================================================
	// Constants.
	// ========================================================
	
	/** Water level constant exposed for gameplay systems (e.g. player swimming). */
	public static final int WATER_LEVEL = TerrainGenerator.WATER_LEVEL;
	
	/** Maximum number of regions to attach per frame (main-thread budget). */
	private static final int ATTACHES_PER_FRAME = 2;
	
	/** Maximum number of block changes to process per frame. */
	private static final int BLOCK_CHANGES_PER_FRAME = 5;
	
	/** Maximum number of remesh requests to submit per frame. */
	private static final int REMESH_REQUESTS_PER_FRAME = 4;
	
	/** Cardinal neighbor offsets for region remeshing (N, S, E, W). */
	// @formatter:off
	private static final int[][] NEIGHBOR_OFFSETS = { { 0, 1 }, { 0, -1 }, { 1, 0 }, { -1, 0 } };
	// @formatter:on
	
	// ========================================================
	// Inner Classes.
	// ========================================================
	
	/**
	 * Represents a pending block change.
	 */
	private static class BlockChange
	{
		final int _worldX;
		final int _worldY;
		final int _worldZ;
		final Block _newBlock;
		
		BlockChange(int x, int y, int z, Block block)
		{
			_worldX = x;
			_worldY = y;
			_worldZ = z;
			_newBlock = block;
		}
	}
	
	// ========================================================
	// Fields.
	// ========================================================
	
	private final Map<Long, Region> _regions = new HashMap<>();
	private final Map<Long, List<Geometry>> _regionGeometries = new HashMap<>();
	private final Node _worldNode = new Node("WorldNode");
	private final Material _opaqueMaterial;
	private final Material _transparentMaterial;
	private final Material _billboardMaterial;
	private final long _seed;
	
	/** Background terrain generator and mesh builder. */
	private final RegionLoader _regionLoader;
	
	/** Cached set of desired region keys for the current camera position and render distance. */
	private Set<Long> _desiredRegions = new HashSet<>();
	
	/** Last known camera region coordinate. Initialized to MIN_VALUE so the first update always triggers. */
	private int _lastCameraRegionX = Integer.MIN_VALUE;
	private int _lastCameraRegionZ = Integer.MIN_VALUE;
	
	/** Last known render distance. Initialized to -1 so the first update always triggers. */
	private int _lastRenderDistance = -1;
	
	/** True until the first batch of regions is attached. Bypasses throttle for initial view. */
	private boolean _initialLoad = true;
	
	/** Queue of pending block changes. */
	private final ConcurrentLinkedQueue<BlockChange> _pendingChanges = new ConcurrentLinkedQueue<>();
	
	/** Set of regions needing remesh due to block changes. */
	private final Set<Long> _pendingRemesh = new HashSet<>();
	
	// ========================================================
	// Constructor.
	// ========================================================
	
	/**
	 * Creates a new World with the given seed and shared atlas material.
	 * @param seed the numeric seed from WorldInfo for terrain generation
	 * @param sharedMaterial the atlas material from TextureAtlas
	 */
	public World(long seed, Material sharedMaterial)
	{
		_seed = seed;
		
		// Opaque material — used as-is for solid blocks.
		_opaqueMaterial = sharedMaterial;
		
		// Transparent material — shared by all transparent geometry (water, leaves).
		_transparentMaterial = sharedMaterial.clone();
		_transparentMaterial.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
		_transparentMaterial.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
		
		// Billboard material — shared by all billboard geometry (flowers, torches).
		_billboardMaterial = sharedMaterial.clone();
		_billboardMaterial.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
		
		_regionLoader = new RegionLoader(seed);
	}
	
	// ========================================================
	// Region Key Encoding.
	// ========================================================
	
	/**
	 * Packs two int region coordinates into a single long key.<br>
	 * Upper 32 bits = regionX, lower 32 bits = regionZ (unsigned).
	 */
	private static long regionKey(int regionX, int regionZ)
	{
		return ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
	}
	
	/**
	 * Extracts the regionX (upper 32 bits) from a packed region key.
	 */
	private static int regionKeyX(long key)
	{
		return (int) (key >> 32);
	}
	
	/**
	 * Extracts the regionZ (lower 32 bits) from a packed region key.
	 */
	private static int regionKeyZ(long key)
	{
		return (int) key;
	}
	
	// ========================================================
	// Dynamic Region Loading.
	// ========================================================
	
	/**
	 * Updates the loaded region set based on the camera position.<br>
	 * Also processes pending block changes and remesh requests.
	 * @param cameraPos the camera's current world position
	 * @param renderDistance the render distance in regions (from SettingsManager)
	 */
	public void update(Vector3f cameraPos, int renderDistance)
	{
		// Process pending block changes (limited per frame).
		processBlockChanges();
		
		// Calculate which region the camera is currently in.
		final int camRegionX = Math.floorDiv((int) Math.floor(cameraPos.x), Region.SIZE_XZ);
		final int camRegionZ = Math.floorDiv((int) Math.floor(cameraPos.z), Region.SIZE_XZ);
		
		// -------------------------------------------------------
		// Phase 1: Diff detection (only when camera region or render distance changes).
		// -------------------------------------------------------
		final boolean changed = (camRegionX != _lastCameraRegionX) || (camRegionZ != _lastCameraRegionZ) || (renderDistance != _lastRenderDistance);
		
		if (changed)
		{
			_lastCameraRegionX = camRegionX;
			_lastCameraRegionZ = camRegionZ;
			_lastRenderDistance = renderDistance;
			
			// Determine which regions should be loaded.
			_desiredRegions = getDesiredRegions(camRegionX, camRegionZ, renderDistance);
			final Set<Long> loaded = new HashSet<>(_regions.keySet());
			
			// --- Unload (immediate — removing geometry is cheap) ---
			final Set<Long> toUnload = new HashSet<>(loaded);
			toUnload.removeAll(_desiredRegions);
			
			for (long key : toUnload)
			{
				detachRegionGeometry(key);
				_regions.remove(key);
				_regionLoader.cancelPending(key);
				_regionLoader.removeFromCache(regionKeyX(key), regionKeyZ(key));
			}
			
			// Cancel any other pending tasks that are no longer desired.
			for (long key : loaded)
			{
				if (!_desiredRegions.contains(key))
				{
					_regionLoader.cancelPending(key);
				}
			}
			
			// Submit new regions to background loader, sorted by distance (closest first).
			final List<long[]> toSubmit = new ArrayList<>();
			for (long key : _desiredRegions)
			{
				if (!_regions.containsKey(key) && !_regionLoader.isPending(key))
				{
					final int rx = regionKeyX(key);
					final int rz = regionKeyZ(key);
					final int dx = rx - camRegionX;
					final int dz = rz - camRegionZ;
					toSubmit.add(new long[]
					{
						key,
						(dx * dx) + (dz * dz)
					});
				}
			}
			toSubmit.sort(Comparator.comparingLong(a -> a[1]));
			
			for (long[] entry : toSubmit)
			{
				final int rx = regionKeyX(entry[0]);
				final int rz = regionKeyZ(entry[0]);
				_regionLoader.requestLoad(rx, rz);
			}
			
			// Mark neighbors of unloaded regions for async remesh (their boundary faces changed).
			if (!toUnload.isEmpty())
			{
				markNeighborsForRemesh(toUnload, null);
			}
		}
		
		// -------------------------------------------------------
		// Phase 2: Submit pending remesh requests (only if dirty).
		// -------------------------------------------------------
		submitRemeshRequests();
		
		// -------------------------------------------------------
		// Phase 3: Poll completed regions, create meshes, and attach (every frame).
		// -------------------------------------------------------
		int attachedCount = 0;
		final Set<Long> newlyLoaded = new HashSet<>();
		
		ReadyRegion ready;
		while ((ready = _regionLoader.pollReady()) != null)
		{
			final Region region = ready.getRegion();
			final long key = regionKey(region.getRegionX(), region.getRegionZ());
			
			// Discard if no longer desired (camera moved away during generation).
			if (!_desiredRegions.contains(key))
			{
				continue;
			}
			
			if (_regions.containsKey(key))
			{
				// Remesh result for an already-loaded region — swap geometry.
				detachRegionGeometry(key);
				attachRegionGeometryFromData(region, ready.getMeshData());
				
				// Mark clean on main thread (safe — no race with background markMeshDirty).
				region.markMeshClean();
			}
			else
			{
				// New region — add to map and attach (do NOT mark clean, needs remesh for boundaries).
				_regions.put(key, region);
				attachRegionGeometryFromData(region, ready.getMeshData());
				newlyLoaded.add(key);
			}
			
			attachedCount++;
			
			// Throttle: limit main-thread work per frame (bypass during initial load).
			if (!_initialLoad && attachedCount >= ATTACHES_PER_FRAME)
			{
				break;
			}
		}
		
		// Mark existing neighbors of newly loaded regions for remesh.
		// Their boundary faces changed because a new region appeared next to them.
		// New regions themselves are already clean if all neighbors were cached during build,
		// or dirty if not (will be remeshed when their missing neighbors eventually load).
		if (!newlyLoaded.isEmpty())
		{
			_initialLoad = false;
			markNeighborsForRemesh(newlyLoaded, newlyLoaded);
		}
	}
	
	/**
	 * Processes pending block changes (limited per frame).
	 */
	private void processBlockChanges()
	{
		int processed = 0;
		BlockChange change;
		while ((change = _pendingChanges.poll()) != null && processed < BLOCK_CHANGES_PER_FRAME)
		{
			applyBlockChange(change);
			processed++;
		}
	}
	
	/**
	 * Applies a single block change to the world.
	 */
	private void applyBlockChange(BlockChange change)
	{
		// Out of vertical bounds.
		if (change._worldY < 0 || change._worldY >= Region.SIZE_Y)
		{
			return;
		}
		
		// Convert world to region coordinates using floor division.
		final int regionX = Math.floorDiv(change._worldX, Region.SIZE_XZ);
		final int regionZ = Math.floorDiv(change._worldZ, Region.SIZE_XZ);
		final long key = regionKey(regionX, regionZ);
		
		final Region region = _regions.get(key);
		if (region == null)
		{
			return;
		}
		
		// Convert world to local coordinates using floor modulus.
		final int localX = Math.floorMod(change._worldX, Region.SIZE_XZ);
		final int localZ = Math.floorMod(change._worldZ, Region.SIZE_XZ);
		
		// Update block (this will mark region dirty if block actually changed).
		region.setBlock(localX, change._worldY, localZ, change._newBlock);
		
		// Update cache.
		_regionLoader.updateRegionCache(region);
		
		// Mark for remesh (will only rebuild if dirty).
		markForRemesh(key);
		
		// Also mark neighbors (since their faces might change).
		for (int[] offset : NEIGHBOR_OFFSETS)
		{
			final long neighborKey = regionKey(regionX + offset[0], regionZ + offset[1]);
			final Region neighbor = _regions.get(neighborKey);
			if (neighbor != null)
			{
				// Neighbor is dirty because its face visibility changed.
				neighbor.markMeshDirty();
				_regionLoader.updateRegionCache(neighbor);
				markForRemesh(neighborKey);
			}
		}
	}
	
	/**
	 * Marks a region for remesh due to block changes.
	 */
	private void markForRemesh(long key)
	{
		if (_regions.containsKey(key))
		{
			synchronized (_pendingRemesh)
			{
				_pendingRemesh.add(key);
			}
		}
	}
	
	/**
	 * Submits pending remesh requests to the background loader.<br>
	 * Skips regions that are already clean (no changes).
	 */
	private void submitRemeshRequests()
	{
		synchronized (_pendingRemesh)
		{
			if (_pendingRemesh.isEmpty())
			{
				return;
			}
		}
		
		int submitted = 0;
		final Set<Long> toRemove = new HashSet<>();
		
		synchronized (_pendingRemesh)
		{
			for (long key : _pendingRemesh)
			{
				if (submitted >= REMESH_REQUESTS_PER_FRAME)
				{
					break;
				}
				
				final int rx = regionKeyX(key);
				final int rz = regionKeyZ(key);
				
				// Submit remesh request (uses thread-safe cache for neighbor lookups).
				if (_regionLoader.requestRemesh(rx, rz))
				{
					toRemove.add(key);
					submitted++;
				}
				else
				{
					// If request failed (e.g. region clean or already pending), remove from pending.
					toRemove.add(key);
				}
			}
			
			_pendingRemesh.removeAll(toRemove);
		}
	}
	
	/**
	 * Returns the set of region keys within the given radius of the center region.<br>
	 * Produces a (2×radius+1)² square grid of keys.
	 * @param centerX the center region X coordinate
	 * @param centerZ the center region Z coordinate
	 * @param radius the radius in regions
	 * @return a Set of packed region keys
	 */
	private Set<Long> getDesiredRegions(int centerX, int centerZ, int radius)
	{
		final Set<Long> keys = new HashSet<>();
		for (int cx = centerX - radius; cx <= centerX + radius; cx++)
		{
			for (int cz = centerZ - radius; cz <= centerZ + radius; cz++)
			{
				keys.add(regionKey(cx, cz));
			}
		}
		return keys;
	}
	
	// ========================================================
	// Region Geometry.
	// ========================================================
	
	/**
	 * Creates jME3 Meshes from pre-built vertex arrays and attaches geometry to the world node.<br>
	 * Used for newly loaded regions where mesh data was built on a background thread.<br>
	 * This is the lightweight path: only DirectBuffer allocation and scene-graph attach.
	 */
	private void attachRegionGeometryFromData(Region region, RegionMeshBuilder.RegionMeshData meshData)
	{
		final RegionMeshResult meshResult = RegionMeshBuilder.createMeshes(meshData);
		attachGeometries(region, meshResult);
	}
	
	/**
	 * Attaches up to three geometries (opaque, transparent, billboard) from a mesh result.<br>
	 * Shared by both the background-data path and the remesh path.
	 */
	private void attachGeometries(Region region, RegionMeshResult meshResult)
	{
		final int cx = region.getRegionX();
		final int cz = region.getRegionZ();
		final long key = regionKey(cx, cz);
		final String regionName = "Region_" + cx + "_" + cz;
		final Vector3f regionOffset = new Vector3f(cx * Region.SIZE_XZ, 0, cz * Region.SIZE_XZ);
		
		final List<Geometry> geometries = new ArrayList<>(3);
		
		// Opaque mesh (solid cubes: grass, dirt, stone, wood, sand, ores, etc.).
		final Mesh opaqueMesh = meshResult.getOpaqueMesh();
		if (opaqueMesh != null)
		{
			final Geometry opaqueGeometry = new Geometry(regionName + "_Opaque", opaqueMesh);
			opaqueGeometry.setMaterial(_opaqueMaterial);
			opaqueGeometry.setLocalTranslation(regionOffset);
			_worldNode.attachChild(opaqueGeometry);
			geometries.add(opaqueGeometry);
		}
		
		// Transparent mesh (leaves, water).
		final Mesh transparentMesh = meshResult.getTransparentMesh();
		if (transparentMesh != null)
		{
			final Geometry transparentGeometry = new Geometry(regionName + "_Transparent", transparentMesh);
			transparentGeometry.setMaterial(_transparentMaterial);
			transparentGeometry.setQueueBucket(Bucket.Transparent);
			transparentGeometry.setLocalTranslation(regionOffset);
			_worldNode.attachChild(transparentGeometry);
			geometries.add(transparentGeometry);
		}
		
		// Billboard mesh (flowers, torches, campfires, berry bushes).
		final Mesh billboardMesh = meshResult.getBillboardMesh();
		if (billboardMesh != null)
		{
			final Geometry billboardGeometry = new Geometry(regionName + "_Billboard", billboardMesh);
			billboardGeometry.setMaterial(_billboardMaterial);
			billboardGeometry.setLocalTranslation(regionOffset);
			_worldNode.attachChild(billboardGeometry);
			geometries.add(billboardGeometry);
		}
		
		_regionGeometries.put(key, geometries);
	}
	
	/**
	 * Detaches all geometries for the region at the given key from the world node.<br>
	 * Removes the geometry references from the tracking map.
	 */
	private void detachRegionGeometry(long key)
	{
		final List<Geometry> geometries = _regionGeometries.remove(key);
		if (geometries != null)
		{
			for (Geometry geometry : geometries)
			{
				_worldNode.detachChild(geometry);
			}
		}
	}
	
	// ========================================================
	// Neighbor Remeshing.
	// ========================================================
	
	/**
	 * Marks loaded cardinal neighbors of the given region keys for async remesh.<br>
	 * Used after loading or unloading regions to queue boundary face updates.<br>
	 * The actual mesh rebuild runs on background threads via {@link #submitRemeshRequests()}.
	 * @param regionKeys the keys whose neighbors should be marked for remesh
	 * @param exclude optional set of keys to skip (e.g. newly loaded regions already marked), may be null
	 */
	private void markNeighborsForRemesh(Set<Long> regionKeys, Set<Long> exclude)
	{
		for (long key : regionKeys)
		{
			final int cx = regionKeyX(key);
			final int cz = regionKeyZ(key);
			for (int[] offset : NEIGHBOR_OFFSETS)
			{
				final long neighborKey = regionKey(cx + offset[0], cz + offset[1]);
				if (_regions.containsKey(neighborKey) && (exclude == null || !exclude.contains(neighborKey)))
				{
					final Region neighbor = _regions.get(neighborKey);
					if (neighbor != null)
					{
						neighbor.markMeshDirty();
						_regionLoader.updateRegionCache(neighbor);
						markForRemesh(neighborKey);
					}
				}
			}
		}
	}
	
	// ========================================================
	// World-Coordinate Block Access.
	// ========================================================
	
	/**
	 * Returns the block at the given world coordinates.<br>
	 * Converts world coordinates to region + local coordinates automatically.<br>
	 * Returns AIR if the region is not loaded or coordinates are out of Y bounds.
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
		
		final Region region = _regions.get(regionKey(regionX, regionZ));
		if (region == null)
		{
			return Block.AIR;
		}
		
		// Convert world to local coordinates using floor modulus.
		final int localX = Math.floorMod(worldX, Region.SIZE_XZ);
		final int localZ = Math.floorMod(worldZ, Region.SIZE_XZ);
		
		return region.getBlock(localX, worldY, localZ);
	}
	
	/**
	 * Sets the block at the given world coordinates.<br>
	 * Converts world coordinates to region + local coordinates automatically.<br>
	 * The actual block change is queued and applied asynchronously to avoid main-thread stalls.
	 */
	public void setBlock(int worldX, int worldY, int worldZ, Block block)
	{
		_pendingChanges.add(new BlockChange(worldX, worldY, worldZ, block));
	}
	
	// ========================================================
	// Lifecycle.
	// ========================================================
	
	/**
	 * Shuts down background threads and releases resources.<br>
	 * Must be called when the world is destroyed (e.g. returning to main menu).
	 */
	public void shutdown()
	{
		_regionLoader.shutdown();
		_pendingChanges.clear();
		synchronized (_pendingRemesh)
		{
			_pendingRemesh.clear();
		}
	}
	
	// ========================================================
	// Accessors.
	// ========================================================
	
	/**
	 * Returns the world scene node containing all region geometries.
	 */
	public Node getWorldNode()
	{
		return _worldNode;
	}
	
	/**
	 * Returns the numeric seed used for this world.
	 */
	public long getSeed()
	{
		return _seed;
	}
	
	/**
	 * Returns the region at the given region coordinates, or null if not loaded.
	 */
	public Region getRegion(int regionX, int regionZ)
	{
		return _regions.get(regionKey(regionX, regionZ));
	}
	
	/**
	 * Returns the number of loaded regions.
	 */
	public int getRegionCount()
	{
		return _regions.size();
	}
}
