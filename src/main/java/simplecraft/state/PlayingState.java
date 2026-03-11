package simplecraft.state;

import java.awt.Font;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.jme3.app.Application;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.MouseButtonTrigger;
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
import simplecraft.audio.AudioManager;
import simplecraft.audio.MusicManager;
import simplecraft.combat.CombatSystem;
import simplecraft.enemy.Enemy;
import simplecraft.enemy.EnemyLighting;
import simplecraft.enemy.SpawnSystem;
import simplecraft.input.GameInputManager;
import simplecraft.player.BlockInteraction;
import simplecraft.player.PlayerController;
import simplecraft.player.PlayerHUD;
import simplecraft.save.SaveManager;
import simplecraft.save.SaveManager.PlayerSaveData;
import simplecraft.save.SaveManager.SavedRegionData;
import simplecraft.state.GameStateManager.GameState;
import simplecraft.ui.FontManager;
import simplecraft.ui.QuestionManager;
import simplecraft.world.Block;
import simplecraft.world.DayNightCycle;
import simplecraft.world.Region;
import simplecraft.world.RegionMeshBuilder;
import simplecraft.world.TextureAtlas;
import simplecraft.world.World;
import simplecraft.world.WorldInfo;
import simplecraft.world.entity.TileEntityManager;

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
 * during the pause overlay transition (disable → re-enable cycle).<br>
 * <br>
 * Lighting is fully baked into vertex colors (sky light + directional face shading).<br>
 * No scene lights (DirectionalLight, AmbientLight) are needed — materials use Unshaded.j3md.<br>
 * The {@link DayNightCycle} modulates sky brightness and tint, which are applied to<br>
 * vertex colors during region mesh rebuilds. Viewport background color and fog color<br>
 * are updated every frame to match the current sky state.<br>
 * <br>
 * <b>Combat priority:</b> On left click, the combat system raycasts for enemies first.<br>
 * If an enemy is in the crosshair, block interaction attack is suppressed. This prevents<br>
 * accidentally mining blocks behind enemies while fighting.
 * @author Pantelis Andrianakis
 * @since February 18th 2026
 */
public class PlayingState extends FadeableAppState
{
	private ActionListener _pauseListener;
	private TextureAtlas _textureAtlas;
	private World _world;
	private WorldInfo _activeWorld;
	private FilterPostProcessor _fpp;
	private FogFilter _fogFilter;
	private PlayerController _playerController;
	private BlockInteraction _blockInteraction;
	private PlayerHUD _playerHUD;
	
	/** Manages automatic enemy spawning, despawning, and updates. */
	private SpawnSystem _spawnSystem;
	
	/** Manages enemy → player damage, player → enemy attacks, screen flashes, and death healing drops. */
	private CombatSystem _combatSystem;
	
	/** Manages the day/night cycle — sky brightness, tint, and viewport color. */
	private DayNightCycle _dayNightCycle;
	
	/** Orchestrates music transitions based on day/night phase, underground, and player submersion. */
	private MusicManager _musicManager;
	
	/** Whether the player is currently dead (death screen showing). */
	private boolean _playerDead;
	
	/** Listener for respawn click while dead. */
	private ActionListener _respawnListener;
	
	/** Action name for the respawn click. */
	private static final String ACTION_RESPAWN = "RESPAWN_CLICK";
	
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
	
	/** Starting time of day for new worlds (0.3 = early morning). */
	private static final float STARTING_TIME_OF_DAY = 0.3f;
	
	/** Loaded player save data for restoring state after spawn. Null if no save exists. */
	private PlayerSaveData _playerSaveData;
	
	/** Loaded tile entity save data for restoring after spawn. Null if no save exists. */
	private String _tileEntitySaveData;
	
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
	
	/**
	 * Minimum region radius around the spawn point that must be fully loaded<br>
	 * before the loading screen is removed. A radius of 2 means a 5×5 grid<br>
	 * (25 regions), ensuring the player never sees terrain pop-in on spawn.
	 */
	private static final int SPAWN_MIN_REGION_RADIUS = 2;
	
	/** True while waiting for terrain to generate at the spawn position. */
	private boolean _pendingSpawn;
	
	/** Frame counter while waiting for terrain. */
	private int _spawnWaitFrames;
	
	/** Target X coordinate for pending spawn (initial or respawn). */
	private int _spawnTargetX;
	
	/** Target Z coordinate for pending spawn (initial or respawn). */
	private int _spawnTargetZ;
	
	/** Y coordinate to scan downward from when looking for terrain (255 for surface, lower for campfire). */
	private int _spawnScanFromY;
	
	/** Fallback Y if terrain never loads within the timeout. */
	private int _spawnFallbackY;
	
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
			
			// Only open pause if we are currently in PLAYING state (not already paused) and not still loading or dead.
			final GameStateManager gsm = simpleCraft.getGameStateManager();
			if (gsm.getCurrentState() == GameState.PLAYING && !_pendingSpawn && !_playerDead && !QuestionManager.isActive())
			{
				// Auto-save before pausing.
				if (_world != null && _playerController != null && _dayNightCycle != null)
				{
					SaveManager.save(_world, _playerController, _dayNightCycle);
				}
				
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
		
		// No scene lights needed — lighting is fully baked into vertex colors via Unshaded materials.
		// Sky light + directional face shading is computed per-vertex in RegionMeshBuilder.
		
		// Check for existing save data before creating the day/night cycle.
		// Load player data first so we can use saved time of day.
		_playerSaveData = SaveManager.loadPlayerData();
		_tileEntitySaveData = SaveManager.loadTileEntityData();
		
		// Initialize the day/night cycle. Use saved time if loading, otherwise start at early morning.
		final float startTime = _playerSaveData != null ? _playerSaveData.getTimeOfDay() : STARTING_TIME_OF_DAY;
		_dayNightCycle = new DayNightCycle(startTime);
		
		// Set initial terrain lighting so regions built during loading use the correct values.
		final ColorRGBA initialTint = _dayNightCycle.getTerrainTint();
		RegionMeshBuilder.setDayNightParams(_dayNightCycle.getTerrainBrightness(), initialTint.r, initialTint.g, initialTint.b);
		
		// Set up distance fog to hide terrain pop-in at render distance edges.
		if (FOG_ENABLED)
		{
			_fogFilter = new FogFilter();
			_fogFilter.setFogColor(_dayNightCycle.getSkyColor());
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
		
		// Create world with day/night cycle reference for vertex color modulation.
		_world = new World(seed, atlasMaterial);
		RegionMeshBuilder.setGlobalTileEntityManager(_world.getTileEntityManager());
		_world.setDayNightCycle(_dayNightCycle);
		EnemyLighting.setWorld(_world);
		
		// Load saved region data and feed to the world for deferred application.
		final ConcurrentHashMap<Long, SavedRegionData> savedRegions = SaveManager.loadWorldData();
		if (savedRegions != null && !savedRegions.isEmpty())
		{
			_world.setSavedRegionData(savedRegions);
			System.out.println("PlayingState: Loaded " + savedRegions.size() + " saved regions for deferred application.");
		}
		
		// Attach world node to the scene.
		app.getRootNode().attachChild(_world.getWorldNode());
		
		// Disable the default fly camera — PlayerController handles all camera work.
		app.getFlyByCamera().setEnabled(false);
		app.getFlyByCamera().setDragToRotate(true);
		
		// Begin pending spawn — world needs to generate terrain before we can place the player.
		_pendingSpawn = true;
		_spawnWaitFrames = 0;
		_spawnTargetX = _playerSaveData != null ? (int) _playerSaveData.getPosX() : SPAWN_X;
		_spawnTargetZ = _playerSaveData != null ? (int) _playerSaveData.getPosZ() : SPAWN_Z;
		_spawnScanFromY = 255;
		_spawnFallbackY = SPAWN_FALLBACK_Y;
		
		// Kick off region loading around the spawn/saved position.
		final int renderDistance = app.getSettingsManager().getRenderDistance();
		_world.update(new Vector3f(_spawnTargetX, SPAWN_FALLBACK_Y, _spawnTargetZ), renderDistance);
		
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
			_world.update(new Vector3f(_spawnTargetX, _spawnFallbackY, _spawnTargetZ), renderDistance);
			
			_spawnWaitFrames++;
			
			// Scan for terrain at the target position.
			int spawnY = -1;
			for (int y = _spawnScanFromY; y >= 0; y--)
			{
				final Block block = _world.getBlock(_spawnTargetX, y, _spawnTargetZ);
				if (block != Block.AIR && !block.isLiquid())
				{
					spawnY = y + 1; // Stand on top of it.
					break;
				}
			}
			
			// Use fallback if timeout reached.
			if (spawnY < 0 && _spawnWaitFrames >= SPAWN_TIMEOUT_FRAMES)
			{
				spawnY = _spawnFallbackY;
				System.out.println("Spawn timeout after " + _spawnWaitFrames + " frames, using fallback Y=" + spawnY);
			}
			
			// Even after finding terrain, wait for nearby regions to load so the
			// player doesn't see terrain pop-in. Skip this check on timeout.
			if (spawnY >= 0 && _spawnWaitFrames < SPAWN_TIMEOUT_FRAMES)
			{
				if (!areSpawnRegionsReady(_spawnTargetX, _spawnTargetZ))
				{
					// Terrain found but surrounding regions still loading — keep waiting.
					return;
				}
			}
			
			// If we found a valid spawn (and regions are ready), finalize setup.
			if (spawnY >= 0)
			{
				System.out.println("Spawn ready at Y=" + spawnY + " (waited " + _spawnWaitFrames + " frames)");
				finalizeSpawn(spawnY);
			}
			
			return;
		}
		
		// Normal gameplay loop.
		if (_world != null && _playerController != null)
		{
			// Update day/night cycle.
			if (_dayNightCycle != null)
			{
				_dayNightCycle.update(tpf);
				
				final SimpleCraft app = SimpleCraft.getInstance();
				
				// Push fixed terrain brightness and tint to the mesh builder.
				// These are discrete day/night values (not continuous) so all regions
				// rebuild to the same brightness, preventing visual seams.
				final ColorRGBA terrainTint = _dayNightCycle.getTerrainTint();
				RegionMeshBuilder.setDayNightParams(_dayNightCycle.getTerrainBrightness(), terrainTint.r, terrainTint.g, terrainTint.b);
				
				// Apply viewport background sky color from cycle.
				app.getViewPort().setBackgroundColor(_dayNightCycle.getSkyColor());
				
				// Update fog color to match sky for seamless blending.
				if (_fogFilter != null)
				{
					_fogFilter.setFogColor(_dayNightCycle.getSkyColor());
				}
				
				// Rebuild all visible region meshes when terrain brightness changes enough during transition.
				if (_dayNightCycle.isTerrainRebuildNeeded())
				{
					_world.rebuildVisibleMeshes();
				}
				
				// Update enemy lighting tint for the current time of day (smooth, per-frame).
				EnemyLighting.setDayNightTint(_dayNightCycle.getSkyTint());
			}
			
			// Update music transitions (water, underground, day/night).
			if (_musicManager != null)
			{
				final Vector3f pos = _playerController.getPosition();
				final boolean underground = _world.isUnderground((int) pos.x, (int) pos.y, (int) pos.z);
				_musicManager.update(tpf, _playerController.isInWater(), underground);
			}
			
			// Update player movement and camera.
			_playerController.update(tpf);
			
			// Check for fall damage / drowning screen flashes.
			// These are detected via flags set during playerController.update().
			if (_combatSystem != null)
			{
				if (_playerController.wasDamageTakenThisFrame())
				{
					_combatSystem.triggerDamageFlash();
				}
				if (_playerController.wasHealedThisFrame())
				{
					_combatSystem.triggerHealFlash();
				}
			}
			
			final SimpleCraft app = SimpleCraft.getInstance();
			final int renderDistance = app.getSettingsManager().getRenderDistance();
			_world.update(_playerController.getPosition(), renderDistance);
			
			// --- Player → Enemy attack priority ---
			// Check enemies FIRST on left-click. If the crosshair is on an enemy,
			// suppress block interaction's attack so we don't mine blocks behind enemies.
			boolean suppressBlockAttack = false;
			if (_combatSystem != null && _spawnSystem != null && !_playerDead)
			{
				if (_blockInteraction != null && _blockInteraction.isAttackHeld())
				{
					final List<Enemy> enemies = _spawnSystem.getEnemies();
					suppressBlockAttack = _combatSystem.tryPlayerAttack(app.getCamera(), enemies, _world);
				}
			}
			
			// Update block interaction (raycasting, breaking, placing).
			if (_blockInteraction != null && !_playerDead)
			{
				_blockInteraction.setAttackSuppressed(suppressBlockAttack);
				_blockInteraction.setShowHighlight(app.getSettingsManager().isShowHighlight());
				_blockInteraction.update(tpf);
			}
			
			// Update enemy spawn system (spawning, despawning, AI, animation).
			if (_spawnSystem != null)
			{
				_spawnSystem.update(_playerController.getPosition(), _playerController.isInWater(), _world, tpf);
			}
			
			// Update tile entity manager (campfire particles, furnace timers, etc.).
			final TileEntityManager tileEntityManager = _world.getTileEntityManager();
			if (tileEntityManager != null)
			{
				tileEntityManager.update(tpf);
			}
			
			// Update combat system (enemy attacks, screen flash fade).
			if (_combatSystem != null && _spawnSystem != null)
			{
				_combatSystem.update(_playerController, _spawnSystem.getEnemies(), _world, tpf);
			}
			
			// --- Death detection and respawn ---
			if (_playerController.isDead() && !_playerDead)
			{
				// Player just died — show death screen.
				_playerDead = true;
				
				// Dismiss any open question dialog (e.g. campfire respawn prompt).
				QuestionManager.dismiss();
				
				// Unregister gameplay input.
				_playerController.unregisterInput();
				if (_blockInteraction != null)
				{
					_blockInteraction.unregisterInput();
				}
				
				// Show death screen overlay.
				if (_playerHUD != null)
				{
					_playerHUD.showDeathScreen(_playerController.getDeathCause());
				}
				
				// Show cursor and register respawn click listener.
				app.getInputManager().setCursorVisible(true);
				
				if (_respawnListener == null)
				{
					_respawnListener = (String name, boolean isPressed, float t) ->
					{
						if (isPressed && ACTION_RESPAWN.equals(name) && _playerDead && !_pendingSpawn)
						{
							performRespawn();
						}
					};
				}
				
				if (!app.getInputManager().hasMapping(ACTION_RESPAWN))
				{
					app.getInputManager().addMapping(ACTION_RESPAWN, new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
				}
				app.getInputManager().addListener(_respawnListener, ACTION_RESPAWN);
				
				System.out.println("Player died! Cause: " + _playerController.getDeathCause());
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
	
	/**
	 * Initiates respawn by showing a loading screen and entering the pending spawn loop.<br>
	 * Reuses the same {@code _pendingSpawn} mechanism as the initial world entry.<br>
	 * The player controller already exists, so {@link #finalizeSpawn(int)} detects<br>
	 * this and performs a lightweight respawn instead of full world initialization.
	 */
	private void performRespawn()
	{
		if (!_playerDead)
		{
			return;
		}
		
		final SimpleCraft app = SimpleCraft.getInstance();
		
		// Get the active respawn point (campfire if set, otherwise initial spawn).
		final Vector3f respawnPoint = _playerController.getActiveRespawnPoint();
		_spawnTargetX = (int) respawnPoint.x;
		_spawnTargetZ = (int) respawnPoint.z;
		
		// For campfire spawns: scan downward from the saved Y to avoid landing on a ceiling.
		// For initial spawn: scan from the world top (surface is always open sky).
		final boolean hasCampfire = _playerController.getCampfireSpawn() != null;
		_spawnScanFromY = hasCampfire ? (int) respawnPoint.y : 255;
		_spawnFallbackY = hasCampfire ? (int) respawnPoint.y : SPAWN_FALLBACK_Y;
		
		// Hide death screen, clean up HUD, and show loading screen.
		if (_playerHUD != null)
		{
			_playerHUD.hideDeathScreen();
		}
		cleanupHUD();
		cleanupRespawnListener();
		showLoadingScreen();
		
		// Hide cursor during loading.
		app.getInputManager().setCursorVisible(false);
		
		// Enter pending spawn — the existing loop will tick the world and call finalizeSpawn.
		_pendingSpawn = true;
		_spawnWaitFrames = 0;
		
		System.out.println("Respawn initiated — waiting for terrain at [" + _spawnTargetX + ", " + _spawnTargetZ + "]...");
	}
	
	/**
	 * Removes the respawn click listener if active.
	 */
	private void cleanupRespawnListener()
	{
		if (_respawnListener != null)
		{
			SimpleCraft.getInstance().getInputManager().removeListener(_respawnListener);
		}
	}
	
	@Override
	protected void onExitState()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		
		// Clean up respawn listener if active.
		cleanupRespawnListener();
		
		// Dismiss any open question dialog (e.g. campfire respawn prompt).
		QuestionManager.dismiss();
		
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
		
		// Full exit (returning to main menu) — save and tear down everything.
		if (_world != null && _playerController != null && _dayNightCycle != null)
		{
			SaveManager.save(_world, _playerController, _dayNightCycle);
		}
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
	 * Returns true if a minimum grid of regions around the spawn point is loaded.<br>
	 * Checks a (2×{@value #SPAWN_MIN_REGION_RADIUS}+1)² square grid, ensuring the<br>
	 * player won't see terrain pop-in when the loading screen is removed.
	 * @param worldX spawn world X coordinate
	 * @param worldZ spawn world Z coordinate
	 * @return true if all required regions are loaded
	 */
	private boolean areSpawnRegionsReady(int worldX, int worldZ)
	{
		final int centerRX = Math.floorDiv(worldX, Region.SIZE_XZ);
		final int centerRZ = Math.floorDiv(worldZ, Region.SIZE_XZ);
		
		for (int rx = centerRX - SPAWN_MIN_REGION_RADIUS; rx <= centerRX + SPAWN_MIN_REGION_RADIUS; rx++)
		{
			for (int rz = centerRZ - SPAWN_MIN_REGION_RADIUS; rz <= centerRZ + SPAWN_MIN_REGION_RADIUS; rz++)
			{
				if (_world.getRegion(rx, rz) == null)
				{
					return false;
				}
			}
		}
		
		return true;
	}
	
	/**
	 * Called once terrain is available at the spawn position and nearby regions are loaded.<br>
	 * On initial entry: creates the player controller, block interaction, and HUD.<br>
	 * On respawn: places the player, re-registers input, and recreates the HUD.<br>
	 * In both cases, removes the loading screen and begins gameplay.
	 * @param spawnY the Y coordinate to place the player (feet level)
	 */
	private void finalizeSpawn(int spawnY)
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		
		_pendingSpawn = false;
		
		// --- Respawn path: player controller already exists (death → respawn). ---
		if (_playerController != null)
		{
			// Place the player at the confirmed terrain position.
			_playerController.respawn(new Vector3f(_spawnTargetX + 0.5f, spawnY, _spawnTargetZ + 0.5f));
			_playerDead = false;
			
			// Re-register gameplay input.
			_playerController.registerInput();
			if (_blockInteraction != null)
			{
				_blockInteraction.registerInput();
			}
			
			// Recreate the HUD (was cleaned up when the loading screen was shown).
			createHUD();
			
			// Remove the loading screen.
			hideLoadingScreen();
			
			// Ensure cursor is hidden for first-person control.
			app.getInputManager().setCursorVisible(false);
			
			System.out.println("Player respawned at [" + _spawnTargetX + ", " + spawnY + ", " + _spawnTargetZ + "] with full health.");
			return;
		}
		
		// --- Initial spawn path: first entry into the world. ---
		
		// Set sky background color from the day/night cycle now that loading is complete.
		if (_dayNightCycle != null)
		{
			app.getViewPort().setBackgroundColor(_dayNightCycle.getSkyColor());
		}
		
		// Create and initialize the player controller with world reference for collision.
		_playerController = new PlayerController(app.getCamera(), app.getInputManager(), _world, app.getAudioManager());
		
		// Restore player state from save data if available.
		if (_playerSaveData != null)
		{
			// Use saved position (the spawnY from terrain scan is a fallback — saved position is authoritative).
			_playerController.setPosition(_playerSaveData.getPosX(), _playerSaveData.getPosY(), _playerSaveData.getPosZ());
			_playerController.setHealth(_playerSaveData.getHealth());
			
			// Restore initial spawn point.
			_playerController.setInitialSpawn(_playerSaveData.getInitialSpawnX(), _playerSaveData.getInitialSpawnY(), _playerSaveData.getInitialSpawnZ());
			
			// Restore selected block.
			final int ordinal = _playerSaveData.getSelectedBlockOrdinal();
			final Block[] blocks = Block.values();
			if (ordinal >= 0 && ordinal < blocks.length)
			{
				_playerController.setSelectedBlock(blocks[ordinal]);
			}
			
			// Restore campfire spawn point.
			if (_playerSaveData.hasCampfireSpawn())
			{
				_playerController.setCampfireSpawnDirect(new Vector3f(_playerSaveData.getCampfireSpawnX(), _playerSaveData.getCampfireSpawnY(), _playerSaveData.getCampfireSpawnZ()));
			}
			
			System.out.println("Restored player from save: pos=[" + _playerSaveData.getPosX() + ", " + _playerSaveData.getPosY() + ", " + _playerSaveData.getPosZ() + "], health=" + _playerSaveData.getHealth());
		}
		else
		{
			// New world — use default spawn position.
			_playerController.setPosition(SPAWN_X, spawnY, SPAWN_Z);
			_playerController.setInitialSpawn(SPAWN_X, spawnY, SPAWN_Z);
		}
		
		_playerController.registerInput();
		
		// Create and initialize block interaction (raycasting, breaking, placing).
		_blockInteraction = new BlockInteraction(app.getCamera(), app.getInputManager(), _world, _playerController, app.getAssetManager(), app.getAudioManager());
		_blockInteraction.registerInput();
		app.getRootNode().attachChild(_blockInteraction.getOverlayNode());
		app.getRootNode().attachChild(_blockInteraction.getDestructionEffectsNode());
		
		// Restore saved tile entities BEFORE attaching tile entity visual node,
		// so particles and other visuals are created during deserialization.
		final TileEntityManager tileEntityManager = _world.getTileEntityManager();
		if (tileEntityManager != null)
		{
			if (_tileEntitySaveData != null && !_tileEntitySaveData.isEmpty())
			{
				tileEntityManager.deserializeAll(_tileEntitySaveData, _world);
				System.out.println("Restored tile entities from save data.");
			}
			
			app.getRootNode().attachChild(tileEntityManager.getNode());
		}
		
		// Re-propagate block light for all light-emitting blocks (torches, campfires).
		// Block light data is not persisted — only block types and tile entities are saved.
		// This restores the lighting state so torches and campfires illuminate their surroundings.
		_world.repropagateAllBlockLights();
		
		// Rebuild all loaded region meshes now that tile entities are populated.
		// FLAT_PANEL blocks (doors, windows) require TileEntityManager data for correct
		// geometry (facing direction, open/closed state), which was unavailable during
		// initial background mesh building when the TileEntityManager was still empty.
		_world.rebuildAllLoadedRegions();
		
		// Clear save data references — no longer needed.
		_playerSaveData = null;
		_tileEntitySaveData = null;
		
		// Create the player HUD.
		createHUD();
		
		// Initialize the enemy spawn system.
		final Node enemyNode = new Node("Enemies");
		app.getRootNode().attachChild(enemyNode);
		_spawnSystem = new SpawnSystem(enemyNode, app.getAssetManager(), _world.getSeed());
		_spawnSystem.setPlayerSpawnZone(SPAWN_X, SPAWN_Z);
		_spawnSystem.setDayNightCycle(_dayNightCycle);
		_spawnSystem.setAudioManager(app.getAudioManager());
		
		// Initialize the combat system (screen flashes, enemy → player damage, player → enemy attacks).
		_combatSystem = new CombatSystem(app.getAudioManager());
		
		// Wire combat references so SpawnSystem can trigger death healing drops.
		_spawnSystem.setCombatReferences(_combatSystem, _playerController);
		
		// Start music matching the current time of day.
		final AudioManager audioManager = app.getAudioManager();
		if (audioManager != null)
		{
			final String startTrack = _dayNightCycle.isNight() ? MusicManager.NIGHT_MUSIC_PATH : MusicManager.DAY_MUSIC_PATH;
			audioManager.fadeInMusic(startTrack, 3.0f);
			
			// Initialize the music manager (orchestrates day/night and water music transitions).
			_musicManager = new MusicManager(audioManager, _dayNightCycle);
		}
		
		// Remove the loading screen.
		hideLoadingScreen();
		
		// Ensure cursor is hidden for first-person control.
		app.getInputManager().setCursorVisible(false);
		
		final Vector3f pos = _playerController.getPosition();
		System.out.println("World loaded. Player at [" + pos.x + ", " + pos.y + ", " + pos.z + "]");
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
	 * Destroys the world and all associated resources.<br>
	 * Called when truly leaving the game session (not when pausing).<br>
	 * No scene lights to remove — lighting is baked into vertex colors.
	 */
	public void destroyWorld()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		
		// Reset paused flag.
		_paused = false;
		_pendingSpawn = false;
		_playerDead = false;
		
		// Clean up loading screen if still showing.
		hideLoadingScreen();
		
		// Clean up HUD.
		cleanupHUD();
		
		// Clean up respawn listener.
		cleanupRespawnListener();
		
		// Dismiss any open question dialog (e.g. campfire respawn prompt).
		QuestionManager.dismiss();
		
		// Clean up combat system.
		if (_combatSystem != null)
		{
			_combatSystem.cleanup();
			_combatSystem = null;
		}
		
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
		
		// Clean up enemy spawn system.
		if (_spawnSystem != null)
		{
			app.getRootNode().detachChild(_spawnSystem.getEnemyNode());
			_spawnSystem = null;
		}
		
		// Clean up world geometry.
		if (_world != null)
		{
			// Detach tile entity visual node before world shutdown.
			final TileEntityManager tileEntityManager = _world.getTileEntityManager();
			if (tileEntityManager != null)
			{
				app.getRootNode().detachChild(tileEntityManager.getNode());
			}
			
			RegionMeshBuilder.setGlobalTileEntityManager(null);
			_world.shutdown();
			app.getRootNode().detachChild(_world.getWorldNode());
			_world = null;
		}
		
		// Clean up post-processing (fog).
		if (_fpp != null)
		{
			app.getViewPort().removeProcessor(_fpp);
			_fpp = null;
			_fogFilter = null;
			_lastFogRenderDistance = -1;
		}
		
		// Clean up day/night cycle.
		_dayNightCycle = null;
		
		// Clean up music manager.
		_musicManager = null;
		
		_textureAtlas = null;
		_activeWorld = null;
		_playerSaveData = null;
		_tileEntitySaveData = null;
		
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
