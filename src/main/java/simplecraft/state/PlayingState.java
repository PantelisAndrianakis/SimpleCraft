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

import simplecraft.SimpleCraft;
import simplecraft.input.GameInputManager;
import simplecraft.state.GameStateManager.GameState;
import simplecraft.world.ChunkMeshBuilder;

/**
 * Playing state - the main game scene.<br>
 * Currently displays a single test cube to verify the mesh pipeline.<br>
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
	private Geometry _testCubeGeometry;
	
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
		
		// Build and attach test cube.
		final Mesh cubeMesh = ChunkMeshBuilder.buildSingleCube();
		_testCubeGeometry = new Geometry("TestCube", cubeMesh);
		
		// Use Lighting material with a solid green color (no texture needed for this test).
		final Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Light/Lighting.j3md");
		mat.setBoolean("UseMaterialColors", true);
		mat.setColor("Diffuse", new ColorRGBA(0.4f, 0.8f, 0.3f, 1.0f));
		mat.setColor("Ambient", new ColorRGBA(0.4f, 0.8f, 0.3f, 1.0f));
		_testCubeGeometry.setMaterial(mat);
		
		app.getRootNode().attachChild(_testCubeGeometry);
		
		System.out.println("Test cube attached — vertices: " + cubeMesh.getVertexCount() + ", triangles: " + cubeMesh.getTriangleCount());
		
		// Position camera to see the cube.
		app.getCamera().setLocation(new Vector3f(3, 3, 3));
		app.getCamera().lookAt(new Vector3f(0.5f, 0.5f, 0.5f), Vector3f.UNIT_Y);
		
		// Disable cursor for first-person control.
		app.getInputManager().setCursorVisible(false);
	}
	
	@Override
	protected void onExitState()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		
		// Re-enable cursor when leaving game.
		app.getInputManager().setCursorVisible(true);
		
		// Clean up test cube.
		if (_testCubeGeometry != null)
		{
			app.getRootNode().detachChild(_testCubeGeometry);
			_testCubeGeometry = null;
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
