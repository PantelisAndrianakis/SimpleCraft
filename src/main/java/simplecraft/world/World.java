package simplecraft.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.material.RenderState.FaceCullMode;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;

import simplecraft.world.RegionMeshBuilder.RegionMeshResult;

/**
 * Manages a collection of regions that form the game world.<br>
 * Regions are stored in a HashMap keyed by a packed long of (regionX, regionZ).<br>
 * Provides world-coordinate block access that automatically resolves to the correct region.<br>
 * Dynamically loads and unloads regions around the camera position each frame.
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
	
	/** Last known camera region coordinate. Initialized to MIN_VALUE so the first update always triggers. */
	private int _lastCameraRegionX = Integer.MIN_VALUE;
	private int _lastCameraRegionZ = Integer.MIN_VALUE;
	
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
	 * Newly loaded regions are generated, meshed, and attached synchronously.<br>
	 * Unloaded regions are removed from the map and detached from the scene.<br>
	 * Border regions adjacent to changes are remeshed to maintain seamless terrain.
	 * @param cameraPos the camera's current world position
	 * @param renderDistance the render distance in regions (from SettingsManager)
	 */
	public void update(Vector3f cameraPos, int renderDistance)
	{
		// Calculate which region the camera is currently in.
		final int camRegionX = Math.floorDiv((int) Math.floor(cameraPos.x), Region.SIZE_XZ);
		final int camRegionZ = Math.floorDiv((int) Math.floor(cameraPos.z), Region.SIZE_XZ);
		
		// Only process if the camera moved to a different region.
		if (camRegionX == _lastCameraRegionX && camRegionZ == _lastCameraRegionZ)
		{
			return;
		}
		
		_lastCameraRegionX = camRegionX;
		_lastCameraRegionZ = camRegionZ;
		
		// Determine which regions should be loaded.
		final Set<Long> desired = getDesiredRegions(camRegionX, camRegionZ, renderDistance);
		final Set<Long> loaded = new HashSet<>(_regions.keySet());
		
		// Diff: desired minus loaded = toLoad. Loaded minus desired = toUnload.
		final Set<Long> toLoad = new HashSet<>(desired);
		toLoad.removeAll(loaded);
		
		final Set<Long> toUnload = new HashSet<>(loaded);
		toUnload.removeAll(desired);
		
		// Nothing changed (e.g. moved within same region but with same radius).
		if (toLoad.isEmpty() && toUnload.isEmpty())
		{
			return;
		}
		
		// --- Unload ---
		for (long key : toUnload)
		{
			detachRegionGeometry(key);
			_regions.remove(key);
		}
		
		// --- Load (Pass 1: Generate terrain for all new regions before meshing) ---
		// All new regions must exist in the map before meshing so cross-region neighbor lookups work.
		for (long key : toLoad)
		{
			final int cx = regionKeyX(key);
			final int cz = regionKeyZ(key);
			final Region region = new Region(cx, cz);
			TerrainGenerator.generateRegion(region, _seed);
			TreeGenerator.generateTrees(region, _seed);
			_regions.put(key, region);
		}
		
		// --- Load (Pass 2: Build meshes and attach geometry) ---
		for (long key : toLoad)
		{
			final Region region = _regions.get(key);
			attachRegionGeometry(region);
		}
		
		// --- Remesh border neighbors to maintain seamless terrain ---
		final Set<Long> toRemesh = new HashSet<>();
		
		// Neighbors of newly loaded regions that were already loaded need remeshing
		// (their boundary faces should now be culled against the new region).
		for (long key : toLoad)
		{
			final int cx = regionKeyX(key);
			final int cz = regionKeyZ(key);
			for (int[] offset : NEIGHBOR_OFFSETS)
			{
				final long neighborKey = regionKey(cx + offset[0], cz + offset[1]);
				if (_regions.containsKey(neighborKey) && !toLoad.contains(neighborKey))
				{
					toRemesh.add(neighborKey);
				}
			}
		}
		
		// Neighbors of unloaded regions that are still loaded need remeshing
		// (their boundary faces should now be visible since the neighbor is gone).
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
		
		// Remesh all affected border regions.
		for (long key : toRemesh)
		{
			final Region region = _regions.get(key);
			if (region != null)
			{
				detachRegionGeometry(key);
				attachRegionGeometry(region);
			}
		}
		
		System.out.println("World: Loaded " + toLoad.size() + ", unloaded " + toUnload.size() + ", remeshed " + toRemesh.size() + ", total " + _regions.size() + " regions (camera region: " + camRegionX + ", " + camRegionZ + ")");
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
		final RegionMeshResult meshResult = RegionMeshBuilder.buildRegionMesh(region, this::getBlock);
		
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
