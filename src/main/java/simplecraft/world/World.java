package simplecraft.world;

import java.util.HashMap;
import java.util.Map;

import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.material.RenderState.FaceCullMode;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;

import simplecraft.world.ChunkMeshBuilder.ChunkMeshResult;

/**
 * Manages a collection of chunks that form the game world.<br>
 * Chunks are stored in a HashMap keyed by a packed long of (chunkX, chunkZ).<br>
 * Provides world-coordinate block access that automatically resolves to the correct chunk.
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
	
	// ========================================================
	// Fields
	// ========================================================
	
	private final Map<Long, Chunk> _chunks = new HashMap<>();
	private final Node _worldNode = new Node("WorldNode");
	private final Material _sharedMaterial;
	private final long _seed;
	
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
	// Chunk Key Encoding
	// ========================================================
	
	/**
	 * Packs two int chunk coordinates into a single long key.<br>
	 * Upper 32 bits = chunkX, lower 32 bits = chunkZ (unsigned).
	 */
	private static long chunkKey(int chunkX, int chunkZ)
	{
		return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
	}
	
	// ========================================================
	// Initial Generation
	// ========================================================
	
	/**
	 * Generates a (2×radius+1)² grid of noise-based terrain chunks centered on the origin.<br>
	 * Each chunk is generated using TerrainGenerator, meshed, and attached to the world node.
	 * @param radius the radius in chunks around the origin (e.g. 4 produces a 9×9 grid)
	 */
	public void generateInitialChunks(int radius)
	{
		final int chunkCount = (2 * radius + 1) * (2 * radius + 1);
		
		// Pass 1: Generate terrain for all chunks and store them.
		// All chunks must exist before meshing so cross-chunk neighbor lookups work.
		for (int cx = -radius; cx <= radius; cx++)
		{
			for (int cz = -radius; cz <= radius; cz++)
			{
				final Chunk chunk = new Chunk(cx, cz);
				TerrainGenerator.generateChunk(chunk, _seed);
				TreeGenerator.generateTrees(chunk, _seed);
				_chunks.put(chunkKey(cx, cz), chunk);
			}
		}
		
		// Pass 2: Build meshes and attach geometry (now all neighbors are available).
		int meshed = 0;
		for (int cx = -radius; cx <= radius; cx++)
		{
			for (int cz = -radius; cz <= radius; cz++)
			{
				final Chunk chunk = _chunks.get(chunkKey(cx, cz));
				attachChunkGeometry(chunk);
				meshed++;
			}
		}
		
		System.out.println("World: Generated " + meshed + "/" + chunkCount + " chunks (radius=" + radius + ", seed=" + _seed + ")");
	}
	
	// ========================================================
	// Chunk Geometry
	// ========================================================
	
	/**
	 * Builds meshes for a chunk and attaches the resulting geometries to the world node.<br>
	 * Creates up to three geometries per chunk: opaque, transparent, and billboard.
	 */
	private void attachChunkGeometry(Chunk chunk)
	{
		final ChunkMeshResult meshResult = ChunkMeshBuilder.buildChunkMesh(chunk, this::getBlock);
		
		final int cx = chunk.getChunkX();
		final int cz = chunk.getChunkZ();
		final String chunkName = "Chunk_" + cx + "_" + cz;
		final Vector3f chunkOffset = new Vector3f(cx * Chunk.SIZE_XZ, 0, cz * Chunk.SIZE_XZ);
		
		// Opaque mesh (solid cubes: grass, dirt, stone, wood, sand, ores, etc.).
		final Mesh opaqueMesh = meshResult.getOpaqueMesh();
		if (opaqueMesh != null)
		{
			final Geometry opaqueGeometry = new Geometry(chunkName + "_Opaque", opaqueMesh);
			opaqueGeometry.setMaterial(_sharedMaterial);
			opaqueGeometry.setLocalTranslation(chunkOffset);
			_worldNode.attachChild(opaqueGeometry);
		}
		
		// Transparent mesh (leaves, water).
		final Mesh transparentMesh = meshResult.getTransparentMesh();
		if (transparentMesh != null)
		{
			final Material transparentMaterial = _sharedMaterial.clone();
			transparentMaterial.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
			transparentMaterial.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
			
			final Geometry transparentGeometry = new Geometry(chunkName + "_Transparent", transparentMesh);
			transparentGeometry.setMaterial(transparentMaterial);
			transparentGeometry.setQueueBucket(Bucket.Transparent);
			transparentGeometry.setLocalTranslation(chunkOffset);
			_worldNode.attachChild(transparentGeometry);
		}
		
		// Billboard mesh (flowers, torches, campfires, berry bushes).
		final Mesh billboardMesh = meshResult.getBillboardMesh();
		if (billboardMesh != null)
		{
			final Material billboardMaterial = _sharedMaterial.clone();
			billboardMaterial.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
			
			final Geometry billboardGeometry = new Geometry(chunkName + "_Billboard", billboardMesh);
			billboardGeometry.setMaterial(billboardMaterial);
			billboardGeometry.setLocalTranslation(chunkOffset);
			_worldNode.attachChild(billboardGeometry);
		}
	}
	
	// ========================================================
	// World-Coordinate Block Access
	// ========================================================
	
	/**
	 * Returns the block at the given world coordinates.<br>
	 * Converts world coordinates to chunk + local coordinates automatically.<br>
	 * Returns AIR if the chunk is not loaded or coordinates are out of Y bounds.
	 */
	public Block getBlock(int worldX, int worldY, int worldZ)
	{
		// Out of vertical bounds.
		if (worldY < 0 || worldY >= Chunk.SIZE_Y)
		{
			return Block.AIR;
		}
		
		// Convert world to chunk coordinates using floor division.
		final int chunkX = Math.floorDiv(worldX, Chunk.SIZE_XZ);
		final int chunkZ = Math.floorDiv(worldZ, Chunk.SIZE_XZ);
		
		final Chunk chunk = _chunks.get(chunkKey(chunkX, chunkZ));
		if (chunk == null)
		{
			return Block.AIR;
		}
		
		// Convert world to local coordinates using floor modulus.
		final int localX = Math.floorMod(worldX, Chunk.SIZE_XZ);
		final int localZ = Math.floorMod(worldZ, Chunk.SIZE_XZ);
		
		return chunk.getBlock(localX, worldY, localZ);
	}
	
	/**
	 * Sets the block at the given world coordinates.<br>
	 * Converts world coordinates to chunk + local coordinates automatically.<br>
	 * Silently ignores if the chunk is not loaded or coordinates are out of Y bounds.
	 */
	public void setBlock(int worldX, int worldY, int worldZ, Block block)
	{
		// Out of vertical bounds.
		if (worldY < 0 || worldY >= Chunk.SIZE_Y)
		{
			return;
		}
		
		// Convert world to chunk coordinates using floor division.
		final int chunkX = Math.floorDiv(worldX, Chunk.SIZE_XZ);
		final int chunkZ = Math.floorDiv(worldZ, Chunk.SIZE_XZ);
		
		final Chunk chunk = _chunks.get(chunkKey(chunkX, chunkZ));
		if (chunk == null)
		{
			return;
		}
		
		// Convert world to local coordinates using floor modulus.
		final int localX = Math.floorMod(worldX, Chunk.SIZE_XZ);
		final int localZ = Math.floorMod(worldZ, Chunk.SIZE_XZ);
		
		chunk.setBlock(localX, worldY, localZ, block);
	}
	
	// ========================================================
	// Accessors
	// ========================================================
	
	/**
	 * Returns the world scene node containing all chunk geometries.
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
	 * Returns the chunk at the given chunk coordinates, or null if not loaded.
	 */
	public Chunk getChunk(int chunkX, int chunkZ)
	{
		return _chunks.get(chunkKey(chunkX, chunkZ));
	}
	
	/**
	 * Returns the number of loaded chunks.
	 */
	public int getChunkCount()
	{
		return _chunks.size();
	}
}
