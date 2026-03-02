package simplecraft.state;

import java.awt.Font;

import com.jme3.app.Application;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.controls.ActionListener;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.FogFilter;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;

import simplecraft.SimpleCraft;
import simplecraft.input.GameInputManager;
import simplecraft.player.BlockInteraction;
import simplecraft.player.PlayerController;
import simplecraft.player.PlayerHUD;
import simplecraft.state.GameStateManager.GameState;
import simplecraft.ui.FontManager;
import simplecraft.world.Block;
import simplecraft.world.Region;
import simplecraft.world.TextureAtlas;
import simplecraft.world.World;
import simplecraft.world.WorldInfo;

/**
 * Playing state - the main game scene.<br>
 * Uses the active world from {@link SimpleCraft#getActiveWorld()} for world name and seed.<br>
 * Creates a dynamically loaded world via {@link World#update(Vector3f, int)}.<br>
 * Regions are loaded and unloaded each frame based on player position and render distance.<br>
 * Listens for the PAUSE action (Escape) to open the pause menu.<br>
 * The pause listener is registered in initialize/cleanup so it remains<br>
 * active even when the state is disabled (paused overlay is showing).<br>
 * <br>
 * On first entry a black loading screen is shown while the world generates terrain<br>
 * around the spawn position. Once terrain is available the player is placed on the<br>
 * highest solid block, the loading screen is removed, and gameplay begins.<br>
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
	private FilterPostProcessor _fpp;
	private FogFilter _fogFilter;
	private PlayerController _playerController;
	private BlockInteraction _blockInteraction;
	private PlayerHUD _playerHUD;
	
	/** Sky color used for both viewport background and fog blending. */
	private static final ColorRGBA SKY_COLOR = new ColorRGBA(0.53f, 0.81f, 0.92f, 1.0f);
	
	/** Whether distance fog is enabled. */
	private static final boolean FOG_ENABLED = true;
	
	/** Fog density controls how quickly fog thickens with distance. */
	private static final float FOG_DENSITY = 1.0f;
	
	/**
	 * Fog distance multiplier relative to render distance.<br>
	 * Higher values push fog further out (clearer nearby, less coverage at edge).<br>
	 * Lower values bring fog closer (more coverage, hides pop-in better).<br>
	 * Recommended range: 1.0 (heavy) to 5.0 (subtle).
	 */
	private static final float FOG_DISTANCE_MULTIPLIER = 5.0f;
	
	/** Last render distance used to update fog. Avoids recalculating every frame. */
	private int _lastFogRenderDistance = -1;
	
	/** True while the game is paused. Set before the state transition to prevent teardown. */
	private boolean _paused;
	
	// ========================================================
	// Loading Screen / Pending Spawn.
	// ========================================================
	
	/** Spawn X coordinate. */
	private static final int SPAWN_X = 64;
	
	/** Spawn Z coordinate. */
	private static final int SPAWN_Z = 64;
	
	/** Fallback spawn Y if terrain never loads (safety net). */
	private static final int SPAWN_FALLBACK_Y = 80;
	
	/** Maximum frames to wait for terrain before using fallback spawn height. */
	private static final int SPAWN_TIMEOUT_FRAMES = 300;
	
	/** True while waiting for terrain to generate at the spawn position. */
	private boolean _pendingSpawn;
	
	/** Frame counter while waiting for terrain. */
	private int _spawnWaitFrames;
	
	/** Loading screen container node attached to guiNode. */
	private Node _loadingScreenNode;
	
	/** Animated dot counter for "Loading..." text. */
	private float _loadingDotTimer;
	
	/** The "Loading" BitmapText (updated with animated dots). */
	private BitmapText _loadingText;
	
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
			
			// Only open pause if we are currently in PLAYING state (not already paused) and not still loading.
			final GameStateManager gsm = simpleCraft.getGameStateManager();
			if (gsm.getCurrentState() == GameState.PLAYING && !_pendingSpawn)
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
		if (_world != null && !_pendingSpawn)
		{
			// Reset paused flag so a subsequent onExitState (e.g. Return to Main Menu) does full teardown.
			_paused = false;
			app.getInputManager().setCursorVisible(false);
			_playerController.registerInput();
			_blockInteraction.registerInput();
			
			// Show HUD again when returning from pause.
			if (_playerHUD == null)
			{
				createHUD();
			}
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
		
		// Show loading screen immediately (black background covers everything).
		showLoadingScreen();
		
		// Set black background during loading (sky color applied after loading completes).
		app.getViewPort().setBackgroundColor(ColorRGBA.Black);
		
		// Add sun (directional light from upper-right).
		_sun = new DirectionalLight();
		_sun.setDirection(new Vector3f(-0.5f, -1.0f, -0.5f).normalizeLocal());
		_sun.setColor(ColorRGBA.White);
		app.getRootNode().addLight(_sun);
		
		// Add ambient light for soft fill lighting.
		_ambient = new AmbientLight();
		_ambient.setColor(new ColorRGBA(0.4f, 0.4f, 0.4f, 1.0f));
		app.getRootNode().addLight(_ambient);
		
		// Set up distance fog to hide terrain pop-in at render distance edges.
		if (FOG_ENABLED)
		{
			_fogFilter = new FogFilter();
			_fogFilter.setFogColor(SKY_COLOR);
			_fogFilter.setFogDensity(FOG_DENSITY);
			_fogFilter.setFogDistance(calculateFogDistance(app.getSettingsManager().getRenderDistance()));
			
			_fpp = new FilterPostProcessor(app.getAssetManager());
			_fpp.addFilter(_fogFilter);
			app.getViewPort().addProcessor(_fpp);
		}
		
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
		
		// Disable the default fly camera — PlayerController handles all camera work.
		app.getFlyByCamera().setEnabled(false);
		app.getFlyByCamera().setDragToRotate(true);
		
		// Begin pending spawn — world needs to generate terrain before we can place the player.
		_pendingSpawn = true;
		_spawnWaitFrames = 0;
		
		// Kick off region loading around the spawn position.
		final int renderDistance = app.getSettingsManager().getRenderDistance();
		_world.update(new Vector3f(SPAWN_X, SPAWN_FALLBACK_Y, SPAWN_Z), renderDistance);
		
		// Hide cursor during loading.
		app.getInputManager().setCursorVisible(false);
	}
	
	@Override
	public void update(float tpf)
	{
		super.update(tpf);
		
		// While waiting for terrain to generate, check each frame.
		if (_pendingSpawn && _world != null)
		{
			updateLoadingScreen(tpf);
			
			final SimpleCraft app = SimpleCraft.getInstance();
			final int renderDistance = app.getSettingsManager().getRenderDistance();
			
			// Keep ticking the world so async region loading progresses.
			_world.update(new Vector3f(SPAWN_X, SPAWN_FALLBACK_Y, SPAWN_Z), renderDistance);
			
			_spawnWaitFrames++;
			
			// Scan for terrain at spawn position.
			int spawnY = -1;
			for (int y = 255; y >= 0; y--)
			{
				final Block block = _world.getBlock(SPAWN_X, y, SPAWN_Z);
				if (block != Block.AIR)
				{
					spawnY = y + 1; // Stand on top of it.
					System.out.println("Spawn terrain found: " + block.name() + " at Y=" + y + ", spawning player at Y=" + spawnY + " (waited " + _spawnWaitFrames + " frames)");
					break;
				}
			}
			
			// Use fallback if timeout reached.
			if (spawnY < 0 && _spawnWaitFrames >= SPAWN_TIMEOUT_FRAMES)
			{
				spawnY = SPAWN_FALLBACK_Y;
				System.out.println("Spawn timeout after " + _spawnWaitFrames + " frames, using fallback Y=" + spawnY);
			}
			
			// If we found a valid spawn, finalize setup.
			if (spawnY >= 0)
			{
				finalizeSpawn(spawnY);
			}
			
			return;
		}
		
		// Normal gameplay loop.
		if (_world != null && _playerController != null)
		{
			// Update player movement and camera.
			_playerController.update(tpf);
			
			final SimpleCraft app = SimpleCraft.getInstance();
			final int renderDistance = app.getSettingsManager().getRenderDistance();
			_world.update(_playerController.getPosition(), renderDistance);
			
			// Update block interaction (raycasting, breaking, placing).
			if (_blockInteraction != null)
			{
				_blockInteraction.setShowHighlight(app.getSettingsManager().isShowHighlight());
				_blockInteraction.update(tpf);
			}
			
			// Update fog distance when render distance changes.
			if (_fogFilter != null && renderDistance != _lastFogRenderDistance)
			{
				_lastFogRenderDistance = renderDistance;
				_fogFilter.setFogDistance(calculateFogDistance(renderDistance));
			}
			
			// Update HUD with current player and interaction state.
			if (_playerHUD != null)
			{ // @formatter:off
				_playerHUD.update(
					_playerController.getHealth(),
					_playerController.getMaxHealth(),
					_playerController.getAir(),
					_playerController.getMaxAir(),
					_playerController.isHeadSubmerged(),
					_playerController.getSelectedBlock(),
					_blockInteraction != null ? _blockInteraction.getHitsDelivered() : 0,
					_blockInteraction != null ? _blockInteraction.getHitsRequired() : 0,
					_blockInteraction != null && _blockInteraction.isBreaking(),
					app.getSettingsManager().isShowCrosshair()
				);
			} // @formatter:on
		}
	}
	
	@Override
	protected void onExitState()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		
		// Always disable controls (both for pause and full exit).
		app.getInputManager().setCursorVisible(true);
		if (_playerController != null)
		{
			_playerController.unregisterInput();
		}
		if (_blockInteraction != null)
		{
			_blockInteraction.unregisterInput();
		}
		
		// If pausing, keep the world alive — only disable controls above.
		if (_paused)
		{
			// Clean up HUD during pause so it doesn't overlap the pause menu.
			cleanupHUD();
			return;
		}
		
		// Full exit (returning to main menu) — tear down everything.
		destroyWorld();
	}
	
	// ========================================================
	// Loading Screen.
	// ========================================================
	
	/**
	 * Creates and shows a black loading screen with centered "Loading..." text.
	 */
	private void showLoadingScreen()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		final int screenWidth = app.getCamera().getWidth();
		final int screenHeight = app.getCamera().getHeight();
		
		_loadingScreenNode = new Node("LoadingScreen");
		
		// Full-screen black quad.
		final Quad bgQuad = new Quad(screenWidth, screenHeight);
		final Geometry bgGeom = new Geometry("LoadingBg", bgQuad);
		final Material bgMat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
		bgMat.setColor("Color", new ColorRGBA(0, 0, 0, 1));
		bgMat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
		bgGeom.setMaterial(bgMat);
		bgGeom.setQueueBucket(Bucket.Gui);
		bgGeom.setLocalTranslation(0, 0, 0);
		_loadingScreenNode.attachChild(bgGeom);
		
		// "Loading..." text centered on screen using linocut font.
		final int fontSize = Math.max(20, (int) (screenHeight * 0.04f));
		final BitmapFont font = FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_LINOCUT_PATH, Font.PLAIN, fontSize);
		_loadingText = new BitmapText(font);
		_loadingText.setText("Loading...");
		_loadingText.setSize(fontSize);
		_loadingText.setColor(new ColorRGBA(0.85f, 0.85f, 0.85f, 1.0f));
		
		final float textWidth = _loadingText.getLineWidth();
		final float textHeight = _loadingText.getLineHeight();
		_loadingText.setLocalTranslation((screenWidth - textWidth) / 2f, (screenHeight + textHeight) / 2f, 1);
		_loadingScreenNode.attachChild(_loadingText);
		
		_loadingDotTimer = 0;
		
		// Attach above everything else in the GUI.
		app.getGuiNode().attachChild(_loadingScreenNode);
	}
	
	/**
	 * Animates the loading screen text dots.
	 */
	private void updateLoadingScreen(float tpf)
	{
		if (_loadingText == null)
		{
			return;
		}
		
		_loadingDotTimer += tpf;
		
		// Cycle dots every 0.5 seconds: "Loading", "Loading.", "Loading..", "Loading..."
		final int dots = ((int) (_loadingDotTimer / 0.5f)) % 4;
		final StringBuilder sb = new StringBuilder("Loading");
		for (int i = 0; i < dots; i++)
		{
			sb.append('.');
		}
		_loadingText.setText(sb.toString());
		
		// Re-center (width changes as dots change).
		final SimpleCraft app = SimpleCraft.getInstance();
		final int screenWidth = app.getCamera().getWidth();
		final int screenHeight = app.getCamera().getHeight();
		final float textWidth = _loadingText.getLineWidth();
		final float textHeight = _loadingText.getLineHeight();
		_loadingText.setLocalTranslation((screenWidth - textWidth) / 2f, (screenHeight + textHeight) / 2f, 1);
	}
	
	/**
	 * Removes the loading screen from the GUI.
	 */
	private void hideLoadingScreen()
	{
		if (_loadingScreenNode != null)
		{
			SimpleCraft.getInstance().getGuiNode().detachChild(_loadingScreenNode);
			_loadingScreenNode = null;
			_loadingText = null;
		}
	}
	
	// ========================================================
	// Spawn Finalization.
	// ========================================================
	
	/**
	 * Called once terrain is available at the spawn position.<br>
	 * Creates the player controller, block interaction, and HUD,<br>
	 * then removes the loading screen and begins gameplay.
	 * @param spawnY the Y coordinate to place the player (feet level)
	 */
	private void finalizeSpawn(int spawnY)
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		
		_pendingSpawn = false;
		
		// Set sky background color now that loading is complete.
		app.getViewPort().setBackgroundColor(SKY_COLOR);
		
		// Create and initialize the player controller with world reference for collision.
		_playerController = new PlayerController(app.getCamera(), app.getInputManager(), _world);
		_playerController.setPosition(SPAWN_X, spawnY, SPAWN_Z);
		_playerController.registerInput();
		
		// Create and initialize block interaction (raycasting, breaking, placing).
		_blockInteraction = new BlockInteraction(app.getCamera(), app.getInputManager(), _world, _playerController, app.getAssetManager());
		_blockInteraction.registerInput();
		app.getRootNode().attachChild(_blockInteraction.getOverlayNode());
		app.getRootNode().attachChild(_blockInteraction.getDestructionEffectsNode());
		
		// Create the player HUD.
		createHUD();
		
		// Remove the loading screen.
		hideLoadingScreen();
		
		// Ensure cursor is hidden for first-person control.
		app.getInputManager().setCursorVisible(false);
		
		System.out.println("World loaded. Player spawned at [" + SPAWN_X + ", " + spawnY + ", " + SPAWN_Z + "]");
	}
	
	// ========================================================
	// HUD Management.
	// ========================================================
	
	/**
	 * Creates the player HUD and links it to the block interaction handler.
	 */
	private void createHUD()
	{
		_playerHUD = new PlayerHUD();
		_playerHUD.setBlockInteraction(_blockInteraction);
	}
	
	/**
	 * Removes the player HUD from the screen.
	 */
	private void cleanupHUD()
	{
		if (_playerHUD != null)
		{
			_playerHUD.cleanup();
			_playerHUD = null;
		}
	}
	
	// ========================================================
	// World Teardown.
	// ========================================================
	
	/**
	 * Destroys the world, lights, and all associated resources.<br>
	 * Called when truly leaving the game session (not when pausing).
	 */
	public void destroyWorld()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		
		// Reset paused flag.
		_paused = false;
		_pendingSpawn = false;
		
		// Clean up loading screen if still showing.
		hideLoadingScreen();
		
		// Clean up HUD.
		cleanupHUD();
		
		// Clean up player controller.
		if (_playerController != null)
		{
			_playerController.unregisterInput();
			_playerController = null;
		}
		
		// Clean up block interaction.
		if (_blockInteraction != null)
		{
			_blockInteraction.unregisterInput();
			_blockInteraction.cleanupDestructionQueue();
			app.getRootNode().detachChild(_blockInteraction.getOverlayNode());
			app.getRootNode().detachChild(_blockInteraction.getDestructionEffectsNode());
			_blockInteraction = null;
		}
		
		// Clean up world geometry.
		if (_world != null)
		{
			_world.shutdown();
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
		
		// Clean up post-processing (fog).
		if (_fpp != null)
		{
			app.getViewPort().removeProcessor(_fpp);
			_fpp = null;
			_fogFilter = null;
			_lastFogRenderDistance = -1;
		}
		
		_textureAtlas = null;
		_activeWorld = null;
		
		// Clear active world reference when leaving the game session.
		app.setActiveWorld(null);
	}
	
	/**
	 * Calculates the fog distance based on render distance.<br>
	 * Fog fully obscures terrain at the outermost ring of regions,<br>
	 * hiding pop-in as new regions load at the boundary.
	 * @param renderDistance the render distance in regions
	 * @return the fog distance in world units (blocks)
	 */
	private static float calculateFogDistance(int renderDistance)
	{
		return renderDistance * Region.SIZE_XZ * FOG_DISTANCE_MULTIPLIER;
	}
}
