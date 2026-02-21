package simplecraft.state;

import com.jme3.app.Application;
import com.jme3.input.controls.ActionListener;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;

import simplecraft.SimpleCraft;
import simplecraft.input.GameInputManager;
import simplecraft.state.GameStateManager.GameState;
import simplecraft.world.Chunk;
import simplecraft.world.ChunkMeshBuilder;
import simplecraft.world.ChunkMeshBuilder.ChunkMeshResult;

/**
 * Playing state - the main game scene.<br>
 * Creates a flat test chunk and builds the chunk mesh to verify<br>
 * full chunk iteration, face culling, and multi-block rendering.<br>
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
		
		// Create a test chunk with flat terrain.
		final Chunk chunk = new Chunk(0, 0);
		chunk.fillFlat(4);
		
		// Build the chunk meshes.
		final ChunkMeshResult meshResult = ChunkMeshBuilder.buildChunkMesh(chunk);
		
		// Attach meshes to a chunk node.
		_chunkNode = new Node("Chunk_0_0");
		
		// Opaque mesh (grass, dirt, stone, bedrock).
		final Mesh opaqueMesh = meshResult.getOpaqueMesh();
		if (opaqueMesh != null)
		{
			final Geometry opaqueGeometry = new Geometry("ChunkOpaque", opaqueMesh);
			final Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Light/Lighting.j3md");
			mat.setBoolean("UseMaterialColors", true);
			mat.setColor("Diffuse", new ColorRGBA(0.4f, 0.8f, 0.3f, 1.0f));
			mat.setColor("Ambient", new ColorRGBA(0.4f, 0.8f, 0.3f, 1.0f));
			opaqueGeometry.setMaterial(mat);
			_chunkNode.attachChild(opaqueGeometry);
			
			System.out.println("Opaque mesh — vertices: " + opaqueMesh.getVertexCount() + ", triangles: " + opaqueMesh.getTriangleCount());
		}
		
		// Transparent mesh (leaves, water) — not present in fillFlat but ready for future use.
		final Mesh transparentMesh = meshResult.getTransparentMesh();
		if (transparentMesh != null)
		{
			final Geometry transparentGeometry = new Geometry("ChunkTransparent", transparentMesh);
			final Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Light/Lighting.j3md");
			mat.setBoolean("UseMaterialColors", true);
			mat.setColor("Diffuse", new ColorRGBA(0.3f, 0.5f, 0.9f, 0.7f));
			mat.setColor("Ambient", new ColorRGBA(0.3f, 0.5f, 0.9f, 0.7f));
			transparentGeometry.setMaterial(mat);
			_chunkNode.attachChild(transparentGeometry);
			
			System.out.println("Transparent mesh — vertices: " + transparentMesh.getVertexCount() + ", triangles: " + transparentMesh.getTriangleCount());
		}
		
		// Billboard mesh (flowers, torches, campfires) — not present in fillFlat but ready for future use.
		final Mesh billboardMesh = meshResult.getBillboardMesh();
		if (billboardMesh != null)
		{
			final Geometry billboardGeometry = new Geometry("ChunkBillboard", billboardMesh);
			final Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Light/Lighting.j3md");
			mat.setBoolean("UseMaterialColors", true);
			mat.setColor("Diffuse", new ColorRGBA(0.9f, 0.3f, 0.3f, 1.0f));
			mat.setColor("Ambient", new ColorRGBA(0.9f, 0.3f, 0.3f, 1.0f));
			billboardGeometry.setMaterial(mat);
			_chunkNode.attachChild(billboardGeometry);
			
			System.out.println("Billboard mesh — vertices: " + billboardMesh.getVertexCount() + ", triangles: " + billboardMesh.getTriangleCount());
		}
		
		app.getRootNode().attachChild(_chunkNode);
		
		// Position camera high enough to see the flat platform.
		app.getCamera().setLocation(new Vector3f(24, 16, 24));
		app.getCamera().lookAt(new Vector3f(8, 2, 8), Vector3f.UNIT_Y);
		
		// Disable cursor for first-person control.
		app.getInputManager().setCursorVisible(false);
	}
	
	@Override
	protected void onExitState()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		
		// Re-enable cursor when leaving game.
		app.getInputManager().setCursorVisible(true);
		
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
	}
	
	@Override
	protected void onUpdateState(float tpf)
	{
		// Game logic updates will go here in future sessions.
	}
}
