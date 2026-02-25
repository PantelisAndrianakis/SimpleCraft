package simplecraft.state;

import com.jme3.app.Application;
import com.jme3.input.controls.ActionListener;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;

import simplecraft.SimpleCraft;
import simplecraft.input.GameInputManager;
import simplecraft.state.GameStateManager.GameState;
import simplecraft.world.TextureAtlas;
import simplecraft.world.World;
import simplecraft.world.WorldInfo;

/**
 * Playing state - the main game scene.<br>
 * Uses the active world from {@link SimpleCraft#getActiveWorld()} for world name and seed.<br>
 * Creates a dynamically loaded world via {@link World#update(Vector3f, int)}.<br>
 * Regions are loaded and unloaded each frame based on camera position and render distance.<br>
 * Listens for the PAUSE action (Escape) to open the pause menu.<br>
 * The pause listener is registered in initialize/cleanup so it remains<br>
 * active even when the state is disabled (paused overlay is showing).<br>
 * <br>
 * The {@code _paused} flag prevents the world from being torn down and rebuilt<br>
 * during the pause overlay transition (disable → re-enable cycle).
 * @author Pantelis Andrianakis
 * @since February 18th 2026
 */
public class PlayingState extends FadeableAppState
{
	private DirectionalLight _sun;
	private AmbientLight _ambient;
	private ActionListener _pauseListener;
	private TextureAtlas _textureAtlas;
	private World _world;
	private WorldInfo _activeWorld;
	
	/** True while the game is paused. Set before the state transition to prevent teardown. */
	private boolean _paused;
	
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
				// Set paused flag BEFORE the state transition so onExitState knows to preserve the world.
				_paused = true;
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
		
		// Safety net: destroy world if still alive when state is detached.
		destroyWorld();
	}
	
	@Override
	protected void onEnterState()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		
		// Returning from pause — world is still alive, just restore controls.
		if (_world != null)
		{
			// Reset paused flag so a subsequent onExitState (e.g. Return to Main Menu) does full teardown.
			_paused = false;
			app.getInputManager().setCursorVisible(false);
			app.getFlyByCamera().setEnabled(true);
			return;
		}
		
		// First entry — full world setup.
		
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
		
		// Build texture atlas and create shared material.
		_textureAtlas = new TextureAtlas();
		_textureAtlas.buildAtlas(app.getAssetManager());
		// _textureAtlas.saveDebugAtlas("debug_atlas.png"); // Save debug image when needed.
		final Material atlasMaterial = _textureAtlas.createMaterial(app.getAssetManager());
		
		// Get seed from active world.
		final long seed = _activeWorld != null ? _activeWorld.getSeedValue() : 0;
		
		// Create world. Initial regions are loaded dynamically by the first update() call.
		_world = new World(seed, atlasMaterial);
		
		// Attach world node to the scene.
		app.getRootNode().attachChild(_world.getWorldNode());
		
		// Disable cursor for first-person control.
		app.getInputManager().setCursorVisible(false);
		
		// Enable fly camera for free movement.
		app.getFlyByCamera().setEnabled(true);
		app.getFlyByCamera().setMoveSpeed(10f);
		
		// Position camera high above the center to overlook the region grid.
		app.getCamera().setLocation(new Vector3f(64, 50, 64));
		app.getCamera().lookAt(new Vector3f(0, 32, 0), Vector3f.UNIT_Y);
	}
	
	@Override
	public void update(float tpf)
	{
		super.update(tpf);
		
		if (_world != null)
		{
			final SimpleCraft app = SimpleCraft.getInstance();
			final int renderDistance = app.getSettingsManager().getRenderDistance();
			_world.update(app.getCamera().getLocation(), renderDistance);
		}
	}
	
	@Override
	protected void onExitState()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		
		// Always disable controls (both for pause and full exit).
		app.getInputManager().setCursorVisible(true);
		app.getFlyByCamera().setEnabled(false);
		
		// If pausing, keep the world alive — only disable controls above.
		if (_paused)
		{
			return;
		}
		
		// Full exit (returning to main menu) — tear down everything.
		destroyWorld();
	}
	
	/**
	 * Destroys the world, lights, and all associated resources.<br>
	 * Called when truly leaving the game session (not when pausing).
	 */
	public void destroyWorld()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		
		// Reset paused flag.
		_paused = false;
		
		// Clean up world geometry.
		if (_world != null)
		{
			app.getRootNode().detachChild(_world.getWorldNode());
			_world = null;
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
