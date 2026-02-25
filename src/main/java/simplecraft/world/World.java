package simplecraft.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.material.RenderState.FaceCullMode;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;

import simplecraft.world.RegionMeshBuilder.MeshBuffers;
import simplecraft.world.RegionMeshBuilder.RegionMeshResult;

/**
 * Manages a collection of regions that form the game world.<br>
 * Regions are stored in a HashMap keyed by a packed long of (regionX, regionZ).<br>
 * Provides world-coordinate block access that automatically resolves to the correct region.<br>
 * Dynamically loads and unloads regions around the camera position each frame.<br>
 * Uses a throttled priority queue to spread region loading across multiple frames,<br>
 * processing up to {@link #REGIONS_PER_FRAME} regions per update to avoid stuttering.
 * @author Pantelis Andrianakis
 * @since February 23rd 2026
 */
public class World
{
	// ========================================================
	// Constants
	// ========================================================
	
	/** Water level constant exposed for gameplay systems (e.g. player swimming). */
	public static final int WATER_LEVEL = TerrainGenerator.WATER_LEVEL;
	
	/** Maximum number of regions to generate and attach per frame. */
	private static final int REGIONS_PER_FRAME = 1;
	
	/** Cardinal neighbor offsets for region remeshing (N, S, E, W). */
	// @formatter:off
	private static final int[][] NEIGHBOR_OFFSETS = { { 0, 1 }, { 0, -1 }, { 1, 0 }, { -1, 0 } };
	// @formatter:on
	
	// ========================================================
	// Fields
	// ========================================================
	
	private final Map<Long, Region> _regions = new HashMap<>();
	private final Map<Long, List<Geometry>> _regionGeometries = new HashMap<>();
	private final Node _worldNode = new Node("WorldNode");
	private final Material _sharedMaterial;
	private final long _seed;
	
	/** Reusable mesh buffers to reduce GC pressure during region meshing. */
	private final MeshBuffers _meshBuffers = new MeshBuffers();
	
	/** Priority queue of region coordinate pairs (regionX, regionZ) awaiting generation and meshing. */
	private final PriorityQueue<int[]> _loadQueue = new PriorityQueue<>(Comparator.comparingInt(a -> a[2]));
	
	/** Tracks region keys already present in the load queue to avoid duplicate entries. */
	private final Set<Long> _queuedKeys = new HashSet<>();
	
	/** Last known camera region coordinate. Initialized to MIN_VALUE so the first update always triggers. */
	private int _lastCameraRegionX = Integer.MIN_VALUE;
	private int _lastCameraRegionZ = Integer.MIN_VALUE;
	
	/** Last known render distance. Initialized to -1 so the first update always triggers. */
	private int _lastRenderDistance = -1;
	
	/** True until the first update completes. Bypasses throttle so all initial regions load at once. */
	private boolean _initialLoad = true;
	
	// ========================================================
	// Constructor
	// ========================================================
	
	/**
	 * Creates a new World with the given seed and shared atlas material.
	 * @param seed the numeric seed from WorldInfo for terrain generation
	 * @param sharedMaterial the atlas material from TextureAtlas
	 */
	public World(long seed, Material sharedMaterial)
	{
		_seed = seed;
		_sharedMaterial = sharedMaterial;
	}
	
	// ========================================================
	// Region Key Encoding
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
	// Dynamic Region Loading
	// ========================================================
	
	/**
	 * Updates the loaded region set based on the camera position.<br>
	 * Calculates the camera's current region coordinate and, if it has changed,<br>
	 * determines which regions to load and unload based on the render distance.<br>
	 * Newly needed regions are added to a priority queue sorted by distance to the camera.<br>
	 * Each frame, up to {@link #REGIONS_PER_FRAME} regions are dequeued, generated, meshed,<br>
	 * and attached. Unloaded regions are removed immediately.<br>
	 * Border regions adjacent to newly loaded or unloaded regions are remeshed to maintain<br>
	 * seamless terrain.
	 * @param cameraPos the camera's current world position
	 * @param renderDistance the render distance in regions (from SettingsManager)
	 */
	public void update(Vector3f cameraPos, int renderDistance)
	{
		// Calculate which region the camera is currently in.
		final int camRegionX = Math.floorDiv((int) Math.floor(cameraPos.x), Region.SIZE_XZ);
		final int camRegionZ = Math.floorDiv((int) Math.floor(cameraPos.z), Region.SIZE_XZ);
		
		// Check if the camera moved to a different region or render distance changed.
		final boolean cameraRegionChanged = (camRegionX != _lastCameraRegionX) || (camRegionZ != _lastCameraRegionZ) || (renderDistance != _lastRenderDistance);
		
		if (cameraRegionChanged)
		{
			_lastCameraRegionX = camRegionX;
			_lastCameraRegionZ = camRegionZ;
			_lastRenderDistance = renderDistance;
			
			// Determine which regions should be loaded.
			final Set<Long> desired = getDesiredRegions(camRegionX, camRegionZ, renderDistance);
			final Set<Long> loaded = new HashSet<>(_regions.keySet());
			
			// --- Unload (immediate) ---
			final Set<Long> toUnload = new HashSet<>(loaded);
			toUnload.removeAll(desired);
			
			for (long key : toUnload)
			{
				detachRegionGeometry(key);
				_regions.remove(key);
			}
			
			// Remove queued entries that are no longer desired (camera moved away).
			_loadQueue.removeIf(entry ->
			{
				final long key = regionKey(entry[0], entry[1]);
				if (!desired.contains(key))
				{
					_queuedKeys.remove(key);
					return true;
				}
				return false;
			});
			
			// Re-prioritize existing queue entries with updated distances.
			if (!_loadQueue.isEmpty())
			{
				final List<int[]> existing = new ArrayList<>(_loadQueue);
				_loadQueue.clear();
				for (int[] entry : existing)
				{
					final int dx = entry[0] - camRegionX;
					final int dz = entry[1] - camRegionZ;
					entry[2] = (dx * dx) + (dz * dz);
					_loadQueue.add(entry);
				}
			}
			
			// Queue new regions that are neither loaded nor already queued.
			for (long key : desired)
			{
				if (!loaded.contains(key) && !_queuedKeys.contains(key))
				{
					final int rx = regionKeyX(key);
					final int rz = regionKeyZ(key);
					final int dx = rx - camRegionX;
					final int dz = rz - camRegionZ;
					final int distanceSq = (dx * dx) + (dz * dz);
					_loadQueue.add(new int[]
					{
						rx,
						rz,
						distanceSq
					});
					_queuedKeys.add(key);
				}
			}
			
			// Remesh neighbors of unloaded regions (their boundary faces should now be visible).
			if (!toUnload.isEmpty())
			{
				final Set<Long> toRemesh = new HashSet<>();
				
				for (long key : toUnload)
				{
					final int cx = regionKeyX(key);
					final int cz = regionKeyZ(key);
					for (int[] offset : NEIGHBOR_OFFSETS)
					{
						final long neighborKey = regionKey(cx + offset[0], cz + offset[1]);
						if (_regions.containsKey(neighborKey))
						{
							toRemesh.add(neighborKey);
						}
					}
				}
				
				for (long key : toRemesh)
				{
					final Region region = _regions.get(key);
					if (region != null)
					{
						detachRegionGeometry(key);
						attachRegionGeometry(region);
					}
				}
			}
		}
		
		// --- Process load queue (throttled) ---
		if (_loadQueue.isEmpty())
		{
			return;
		}
		
		int loadedCount = 0;
		final Set<Long> newlyLoaded = new HashSet<>();
		final List<long[]> batchEntries = new ArrayList<>();
		
		// --- Pass 1: Generate terrain for all batch regions before meshing ---
		// All regions must exist in the map so cross-region neighbor lookups work within the batch.
		while (!_loadQueue.isEmpty() && (_initialLoad || loadedCount < REGIONS_PER_FRAME))
		{
			final int[] entry = _loadQueue.poll();
			final int rx = entry[0];
			final int rz = entry[1];
			final long key = regionKey(rx, rz);
			_queuedKeys.remove(key);
			
			// Skip if already loaded (e.g. duplicate or race condition).
			if (_regions.containsKey(key))
			{
				continue;
			}
			
			// Generate terrain and trees.
			final Region region = new Region(rx, rz);
			TerrainGenerator.generateRegion(region, _seed);
			TreeGenerator.generateTrees(region, _seed);
			_regions.put(key, region);
			
			newlyLoaded.add(key);
			batchEntries.add(new long[]
			{
				key
			});
			loadedCount++;
		}
		
		// --- Pass 2: Build meshes and attach geometry ---
		for (long[] entry : batchEntries)
		{
			final Region region = _regions.get(entry[0]);
			attachRegionGeometry(region);
		}
		
		// Remesh border neighbors of newly loaded regions.
		if (!newlyLoaded.isEmpty())
		{
			// Clear initial load flag after the first batch.
			_initialLoad = false;
			final Set<Long> toRemesh = new HashSet<>();
			
			for (long key : newlyLoaded)
			{
				final int cx = regionKeyX(key);
				final int cz = regionKeyZ(key);
				for (int[] offset : NEIGHBOR_OFFSETS)
				{
					final long neighborKey = regionKey(cx + offset[0], cz + offset[1]);
					if (_regions.containsKey(neighborKey) && !newlyLoaded.contains(neighborKey))
					{
						toRemesh.add(neighborKey);
					}
				}
			}
			
			for (long key : toRemesh)
			{
				final Region region = _regions.get(key);
				if (region != null)
				{
					detachRegionGeometry(key);
					attachRegionGeometry(region);
				}
			}
			
			System.out.println("World: Loaded " + loadedCount + ", queued " + _loadQueue.size() + ", total " + _regions.size() + " regions (camera region: " + camRegionX + ", " + camRegionZ + ")");
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
	// Region Geometry
	// ========================================================
	
	/**
	 * Builds meshes for a region and attaches the resulting geometries to the world node.<br>
	 * Creates up to three geometries per region: opaque, transparent, and billboard.<br>
	 * Stores geometry references for later detachment.
	 */
	private void attachRegionGeometry(Region region)
	{
		final RegionMeshResult meshResult = RegionMeshBuilder.buildRegionMesh(region, this::getBlock, _meshBuffers);
		
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
			opaqueGeometry.setMaterial(_sharedMaterial);
			opaqueGeometry.setLocalTranslation(regionOffset);
			_worldNode.attachChild(opaqueGeometry);
			geometries.add(opaqueGeometry);
		}
		
		// Transparent mesh (leaves, water).
		final Mesh transparentMesh = meshResult.getTransparentMesh();
		if (transparentMesh != null)
		{
			final Material transparentMaterial = _sharedMaterial.clone();
			transparentMaterial.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
			transparentMaterial.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
			
			final Geometry transparentGeometry = new Geometry(regionName + "_Transparent", transparentMesh);
			transparentGeometry.setMaterial(transparentMaterial);
			transparentGeometry.setQueueBucket(Bucket.Transparent);
			transparentGeometry.setLocalTranslation(regionOffset);
			_worldNode.attachChild(transparentGeometry);
			geometries.add(transparentGeometry);
		}
		
		// Billboard mesh (flowers, torches, campfires, berry bushes).
		final Mesh billboardMesh = meshResult.getBillboardMesh();
		if (billboardMesh != null)
		{
			final Material billboardMaterial = _sharedMaterial.clone();
			billboardMaterial.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
			
			final Geometry billboardGeometry = new Geometry(regionName + "_Billboard", billboardMesh);
			billboardGeometry.setMaterial(billboardMaterial);
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
	// World-Coordinate Block Access
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
	 * Silently ignores if the region is not loaded or coordinates are out of Y bounds.
	 */
	public void setBlock(int worldX, int worldY, int worldZ, Block block)
	{
		// Out of vertical bounds.
		if (worldY < 0 || worldY >= Region.SIZE_Y)
		{
			return;
		}
		
		// Convert world to region coordinates using floor division.
		final int regionX = Math.floorDiv(worldX, Region.SIZE_XZ);
		final int regionZ = Math.floorDiv(worldZ, Region.SIZE_XZ);
		
		final Region region = _regions.get(regionKey(regionX, regionZ));
		if (region == null)
		{
			return;
		}
		
		// Convert world to local coordinates using floor modulus.
		final int localX = Math.floorMod(worldX, Region.SIZE_XZ);
		final int localZ = Math.floorMod(worldZ, Region.SIZE_XZ);
		
		region.setBlock(localX, worldY, localZ, block);
	}
	
	// ========================================================
	// Accessors
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
