package simplecraft.state;

import com.jme3.app.Application;
import com.jme3.input.controls.ActionListener;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.material.RenderState.FaceCullMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;

import simplecraft.SimpleCraft;
import simplecraft.input.GameInputManager;
import simplecraft.state.GameStateManager.GameState;
import simplecraft.world.Block;
import simplecraft.world.Chunk;
import simplecraft.world.ChunkMeshBuilder;
import simplecraft.world.ChunkMeshBuilder.ChunkMeshResult;
import simplecraft.world.TextureAtlas;
import simplecraft.world.WorldInfo;

/**
 * Playing state - the main game scene.<br>
 * Uses the active world from {@link SimpleCraft#getActiveWorld()} for world name and seed.<br>
 * Creates a test chunk with ALL block types to verify textures,<br>
 * face culling, multi-face rendering, cross-billboards, and transparency.<br>
 * Listens for the PAUSE action (Escape) to open the pause menu.<br>
 * The pause listener is registered in initialize/cleanup so it remains<br>
 * active even when the state is disabled (paused overlay is showing).
 * @author Pantelis Andrianakis
 * @since February 18th 2026
 */
public class PlayingState extends FadeableAppState
{
	private DirectionalLight _sun;
	private AmbientLight _ambient;
	private ActionListener _pauseListener;
	private Node _chunkNode;
	private TextureAtlas _textureAtlas;
	private WorldInfo _activeWorld;
	
	public PlayingState()
	{
		// Set fade in/out with black color.
		setFadeIn(1.0f, new ColorRGBA(0, 0, 0, 1));
		setFadeOut(1.0f, new ColorRGBA(0, 0, 0, 1));
	}
	
	@Override
	protected void initialize(Application app)
	{
		// Register the pause listener once. It stays active even when disabled, but the guard inside only triggers pause when the state is enabled.
		final SimpleCraft simpleCraft = SimpleCraft.getInstance();
		
		_pauseListener = (String name, boolean isPressed, float tpf) ->
		{
			if (!isPressed)
			{
				return;
			}
			
			if (!GameInputManager.PAUSE.equals(name))
			{
				return;
			}
			
			// Only open pause if we are currently in PLAYING state (not already paused).
			final GameStateManager gsm = simpleCraft.getGameStateManager();
			if (gsm.getCurrentState() == GameState.PLAYING)
			{
				gsm.switchTo(GameState.PAUSED);
			}
		};
		
		simpleCraft.getInputManager().addListener(_pauseListener, GameInputManager.PAUSE);
	}
	
	@Override
	protected void cleanup(Application app)
	{
		// Remove the pause listener when this state is permanently detached.
		if (_pauseListener != null)
		{
			SimpleCraft.getInstance().getInputManager().removeListener(_pauseListener);
			_pauseListener = null;
		}
	}
	
	@Override
	protected void onEnterState()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		
		// Get the active world from the application.
		_activeWorld = app.getActiveWorld();
		
		if (_activeWorld != null)
		{
			// Update last played timestamp and save.
			_activeWorld.setLastPlayedAt(System.currentTimeMillis());
			WorldInfo.save(_activeWorld, _activeWorld.getWorldDirectory());
			
			System.out.println("Entering world: " + _activeWorld.getName() + " with seed: " + _activeWorld.getSeed());
		}
		else
		{
			System.err.println("WARNING: No active world set. PlayingState entered without WorldInfo.");
		}
		
		System.out.println("PlayingState entered.");
		
		// Set blue sky background.
		app.getViewPort().setBackgroundColor(new ColorRGBA(0.53f, 0.81f, 0.92f, 1.0f));
		
		// Add sun (directional light from upper-right).
		_sun = new DirectionalLight();
		_sun.setDirection(new Vector3f(-0.5f, -1.0f, -0.5f).normalizeLocal());
		_sun.setColor(ColorRGBA.White);
		app.getRootNode().addLight(_sun);
		
		// Add ambient light for soft fill lighting.
		_ambient = new AmbientLight();
		_ambient.setColor(new ColorRGBA(0.4f, 0.4f, 0.4f, 1.0f));
		app.getRootNode().addLight(_ambient);
		
		// Build texture atlas.
		_textureAtlas = new TextureAtlas();
		_textureAtlas.buildAtlas(app.getAssetManager());
		// _textureAtlas.saveDebugAtlas("debug_atlas.png"); // Save debug image when needed.
		final Material atlasMaterial = _textureAtlas.createMaterial(app.getAssetManager());
		
		// Create a test chunk with varied terrain for visual verification.
		final Chunk chunk = new Chunk(0, 0);
		buildTestScene(chunk);
		
		// Build the chunk meshes.
		final ChunkMeshResult meshResult = ChunkMeshBuilder.buildChunkMesh(chunk);
		
		// Attach meshes to a chunk node.
		_chunkNode = new Node("Chunk_0_0");
		
		// Opaque mesh (grass, dirt, stone, wood, sand, iron_ore, bedrock, chest, crafting_table, furnace).
		final Mesh opaqueMesh = meshResult.getOpaqueMesh();
		if (opaqueMesh != null)
		{
			final Geometry opaqueGeometry = new Geometry("ChunkOpaque", opaqueMesh);
			opaqueGeometry.setMaterial(atlasMaterial);
			_chunkNode.attachChild(opaqueGeometry);
			
			System.out.println("Opaque mesh — vertices: " + opaqueMesh.getVertexCount() + ", triangles: " + opaqueMesh.getTriangleCount());
		}
		
		// Transparent mesh (leaves, water).
		final Mesh transparentMesh = meshResult.getTransparentMesh();
		if (transparentMesh != null)
		{
			// Clone material for transparent rendering.
			// - FaceCullMode.Off: leaves and water visible from both sides.
			// - BlendMode.Alpha: water pixels (alpha=160) render semi-transparent.
			// - Bucket.Transparent: depth-sorted for correct blending.
			// Leaves opaque pixels (alpha=255) still render solid; holes (alpha=0) are discarded.
			final Material transparentMaterial = atlasMaterial.clone();
			transparentMaterial.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
			transparentMaterial.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
			
			final Geometry transparentGeometry = new Geometry("ChunkTransparent", transparentMesh);
			transparentGeometry.setMaterial(transparentMaterial);
			transparentGeometry.setQueueBucket(Bucket.Transparent);
			_chunkNode.attachChild(transparentGeometry);
			
			System.out.println("Transparent mesh — vertices: " + transparentMesh.getVertexCount() + ", triangles: " + transparentMesh.getTriangleCount());
		}
		
		// Billboard mesh (flowers, torches, campfires, berry bushes).
		final Mesh billboardMesh = meshResult.getBillboardMesh();
		if (billboardMesh != null)
		{
			// Clone material with face culling disabled — billboard quads must be visible from both sides.
			final Material billboardMaterial = atlasMaterial.clone();
			billboardMaterial.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
			
			final Geometry billboardGeometry = new Geometry("ChunkBillboard", billboardMesh);
			billboardGeometry.setMaterial(billboardMaterial);
			_chunkNode.attachChild(billboardGeometry);
			
			System.out.println("Billboard mesh — vertices: " + billboardMesh.getVertexCount() + ", triangles: " + billboardMesh.getTriangleCount());
		}
		
		app.getRootNode().attachChild(_chunkNode);
		
		// Position camera to overlook the entire test scene.
		app.getCamera().setLocation(new Vector3f(8, 12, 20));
		app.getCamera().lookAt(new Vector3f(8, 3, 8), Vector3f.UNIT_Y);
		
		// Disable cursor for first-person control.
		app.getInputManager().setCursorVisible(false);
		
		// Enable fly camera for free movement.
		app.getFlyByCamera().setEnabled(true);
		app.getFlyByCamera().setMoveSpeed(10f);
	}
	
	/**
	 * Fills the chunk with a diverse set of blocks for visual testing.<br>
	 * Layout (viewed from above, south-to-north = increasing Z):<br>
	 * 
	 * <pre>
	 * - Bedrock floor at Y=0
	 * - Stone layer at Y=1 with iron ore veins
	 * - Dirt layer at Y=2
	 * - Grass surface at Y=3
	 * - Sand patch on grass
	 * - Water pool (2-deep)
	 * - Wood column with leaves canopy
	 * - Chest, Crafting Table, Furnace in a row
	 * - Flowers (all 4 types) on grass
	 * - Berry bush, Campfire, Torch on grass
	 * </pre>
	 */
	private void buildTestScene(Chunk chunk)
	{
		// -------------------------------------------------
		// Layer 0: Bedrock floor (16x16)
		// -------------------------------------------------
		for (int x = 0; x < 16; x++)
		{
			for (int z = 0; z < 16; z++)
			{
				chunk.setBlock(x, 0, z, Block.BEDROCK);
			}
		}
		
		// -------------------------------------------------
		// Layer 1: Stone with scattered iron ore
		// -------------------------------------------------
		for (int x = 0; x < 16; x++)
		{
			for (int z = 0; z < 16; z++)
			{
				chunk.setBlock(x, 1, z, Block.STONE);
			}
		}
		// Iron ore vein at a few spots.
		chunk.setBlock(3, 1, 3, Block.IRON_ORE);
		chunk.setBlock(4, 1, 3, Block.IRON_ORE);
		chunk.setBlock(3, 1, 4, Block.IRON_ORE);
		chunk.setBlock(10, 1, 8, Block.IRON_ORE);
		chunk.setBlock(11, 1, 8, Block.IRON_ORE);
		
		// -------------------------------------------------
		// Layer 2: Dirt
		// -------------------------------------------------
		for (int x = 0; x < 16; x++)
		{
			for (int z = 0; z < 16; z++)
			{
				chunk.setBlock(x, 2, z, Block.DIRT);
			}
		}
		
		// -------------------------------------------------
		// Layer 3: Grass surface (except where sand/water)
		// -------------------------------------------------
		for (int x = 0; x < 16; x++)
		{
			for (int z = 0; z < 16; z++)
			{
				chunk.setBlock(x, 3, z, Block.GRASS);
			}
		}
		
		// -------------------------------------------------
		// Sand patch (x=12-15, z=0-3)
		// -------------------------------------------------
		for (int x = 12; x < 16; x++)
		{
			for (int z = 0; z < 4; z++)
			{
				chunk.setBlock(x, 3, z, Block.SAND);
			}
		}
		
		// -------------------------------------------------
		// Water pool (x=12-15, z=5-8, depth 2)
		// Remove grass at water surface, put water at Y=2 and Y=3.
		// -------------------------------------------------
		for (int x = 12; x < 16; x++)
		{
			for (int z = 5; z < 9; z++)
			{
				chunk.setBlock(x, 2, z, Block.WATER);
				chunk.setBlock(x, 3, z, Block.WATER);
			}
		}
		
		// -------------------------------------------------
		// Wood column (x=2, z=8) — trunk 4 blocks tall
		// -------------------------------------------------
		for (int y = 4; y <= 7; y++)
		{
			chunk.setBlock(2, y, 8, Block.WOOD);
		}
		
		// -------------------------------------------------
		// Leaves canopy — filled volume (Y=5 to Y=9)
		// Wider at middle, tapers at top and bottom.
		// -------------------------------------------------
		for (int y = 5; y <= 9; y++)
		{
			int radius;
			if (y == 5 || y == 9)
			{
				radius = 1; // Narrow at bottom and top.
			}
			else if (y == 8)
			{
				radius = 2; // Medium upper layer.
			}
			else
			{
				radius = 3; // Full width at Y=6 and Y=7.
			}
			
			for (int dx = -radius; dx <= radius; dx++)
			{
				for (int dz = -radius; dz <= radius; dz++)
				{
					final int lx = 2 + dx;
					final int lz = 8 + dz;
					if (lx < 0 || lx >= 16 || lz < 0 || lz >= 16)
					{
						continue;
					}
					
					// Skip corners for rounder canopy.
					if (Math.abs(dx) == radius && Math.abs(dz) == radius)
					{
						continue;
					}
					
					// Don't overwrite the trunk.
					if (dx == 0 && dz == 0 && y <= 7)
					{
						continue;
					}
					
					chunk.setBlock(lx, y, lz, Block.LEAVES);
				}
			}
		}
		
		// -------------------------------------------------
		// Tile entities row at Z=12, on grass (Y=4)
		// -------------------------------------------------
		// Chest at x=4 — front faces NORTH (toward +Z).
		chunk.setBlock(4, 4, 12, Block.CHEST);
		
		// Crafting table at x=6.
		chunk.setBlock(6, 4, 12, Block.CRAFTING_TABLE);
		
		// Furnace at x=8 — front faces NORTH.
		chunk.setBlock(8, 4, 12, Block.FURNACE);
		
		// -------------------------------------------------
		// Flowers row at Z=5, on grass (Y=4)
		// -------------------------------------------------
		chunk.setBlock(4, 4, 5, Block.RED_POPPY);
		chunk.setBlock(5, 4, 5, Block.DANDELION);
		chunk.setBlock(6, 4, 5, Block.BLUE_ORCHID);
		chunk.setBlock(7, 4, 5, Block.WHITE_DAISY);
		
		// -------------------------------------------------
		// Berry bush, Campfire, Torch at Z=3, on grass (Y=4)
		// -------------------------------------------------
		chunk.setBlock(6, 4, 3, Block.BERRY_BUSH);
		chunk.setBlock(8, 4, 3, Block.CAMPFIRE);
		chunk.setBlock(10, 4, 3, Block.TORCH);
		
		// -------------------------------------------------
		// Tall grass scattered on surface (Y=4)
		// -------------------------------------------------
		chunk.setBlock(3, 4, 2, Block.TALL_GRASS);
		chunk.setBlock(5, 4, 1, Block.TALL_GRASS);
		chunk.setBlock(7, 4, 6, Block.TALL_GRASS);
		chunk.setBlock(9, 4, 2, Block.TALL_GRASS);
		chunk.setBlock(11, 4, 5, Block.TALL_GRASS);
		chunk.setBlock(3, 4, 10, Block.TALL_GRASS);
		chunk.setBlock(5, 4, 14, Block.TALL_GRASS);
		chunk.setBlock(9, 4, 10, Block.TALL_GRASS);
		chunk.setBlock(11, 4, 14, Block.TALL_GRASS);
		chunk.setBlock(14, 4, 12, Block.TALL_GRASS);
		
		System.out.println("Test scene built: bedrock → stone → dirt → grass surface with all block types.");
	}
	
	@Override
	protected void onExitState()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		
		// Re-enable cursor when leaving game.
		app.getInputManager().setCursorVisible(true);
		
		// Disable fly camera to avoid input conflicts with menus.
		app.getFlyByCamera().setEnabled(false);
		
		// Clean up chunk geometry.
		if (_chunkNode != null)
		{
			app.getRootNode().detachChild(_chunkNode);
			_chunkNode = null;
		}
		
		// Clean up lights.
		if (_sun != null)
		{
			app.getRootNode().removeLight(_sun);
			_sun = null;
		}
		
		if (_ambient != null)
		{
			app.getRootNode().removeLight(_ambient);
			_ambient = null;
		}
		
		_textureAtlas = null;
		_activeWorld = null;
		
		// Clear active world reference when leaving the game session.
		app.setActiveWorld(null);
	}
}
