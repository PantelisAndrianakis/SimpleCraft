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
import simplecraft.effects.ParticleManager;
import simplecraft.enemy.Enemy;
import simplecraft.enemy.EnemyAI;
import simplecraft.enemy.EnemyLighting;
import simplecraft.enemy.SpawnSystem;
import simplecraft.input.GameInputManager;
import simplecraft.item.DropManager;
import simplecraft.item.ItemInstance;
import simplecraft.item.ItemTextureResolver;
import simplecraft.item.ItemType;
import simplecraft.player.BlockInteraction;
import simplecraft.player.ChestScreen;
import simplecraft.player.CraftingScreen;
import simplecraft.player.FurnaceScreen;
import simplecraft.player.InventoryScreen;
import simplecraft.player.PlayerController;
import simplecraft.player.PlayerHUD;
import simplecraft.player.ViewmodelRenderer;
import simplecraft.save.SaveManager;
import simplecraft.save.SaveManager.PlayerSaveData;
import simplecraft.save.SaveManager.SavedRegionData;
import simplecraft.state.GameStateManager.GameState;
import simplecraft.ui.FontManager;
import simplecraft.ui.MessageManager;
import simplecraft.ui.QuestionManager;
import simplecraft.world.Block;
import simplecraft.world.DayNightCycle;
import simplecraft.world.Region;
import simplecraft.world.RegionMeshBuilder;
import simplecraft.world.TerrainGenerator;
import simplecraft.world.TextureAtlas;
import simplecraft.world.World;
import simplecraft.world.WorldInfo;
import simplecraft.world.boss.BossArenaManager;
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
 * highest solid block, the loading screen is removed and gameplay begins.<br>
 * <br>
 * The {@code _paused} flag prevents the world from being torn down and rebuilt<br>
 * during the pause overlay transition (disable -> re-enable cycle).<br>
 * <br>
 * Lighting is fully baked into vertex colors (sky light + directional face shading).<br>
 * No scene lights (DirectionalLight, AmbientLight) are needed - materials use Unshaded.j3md.<br>
 * The {@link DayNightCycle} modulates sky brightness and tint, which are applied to<br>
 * vertex colors during region mesh rebuilds. Viewport background color and fog color<br>
 * are updated every frame to match the current sky state.<br>
 * <br>
 * <b>Combat priority:</b> On left click, the combat system raycasts for enemies first.<br>
 * If an enemy is in the crosshair, block interaction attack is suppressed. This prevents<br>
 * accidentally mining blocks behind enemies while fighting.<br>
 * <br>
 * <b>Inventory:</b> Tab toggles the inventory screen. While open, player movement and<br>
 * block interaction are disabled. Escape also closes the inventory if it is open.
 * @author Pantelis Andrianakis
 * @since February 18th 2026
 */
public class PlayingState extends FadeableAppState
{
	private ActionListener _pauseListener;
	private ActionListener _inventoryListener;
	private TextureAtlas _textureAtlas;
	
	/** The shared texture atlas material used for all block rendering and mini-cube drops. */
	private Material _atlasMaterial;
	private World _world;
	private WorldInfo _activeWorld;
	private FilterPostProcessor _fpp;
	private FogFilter _fogFilter;
	private PlayerController _playerController;
	private BlockInteraction _blockInteraction;
	private PlayerHUD _playerHUD;
	private InventoryScreen _inventoryScreen;
	private CraftingScreen _craftingScreen;
	private ChestScreen _chestScreen;
	private FurnaceScreen _furnaceScreen;
	
	/** Manages automatic enemy spawning, despawning and updates. */
	private SpawnSystem _spawnSystem;
	
	/** Manages enemy -> player damage, player -> enemy attacks, screen flashes and death healing drops. */
	private CombatSystem _combatSystem;
	
	/** Manages dropped items in the world (enemy death drops, pickup, despawn). */
	private DropManager _dropManager;
	
	/** Manages the day/night cycle - sky brightness, tint and viewport color. */
	private DayNightCycle _dayNightCycle;
	
	/** Orchestrates music transitions based on day/night phase, underground and player submersion. */
	private MusicManager _musicManager;
	
	/** Manages particle effects for block breaking and combat damage. */
	private ParticleManager _particleManager;
	
	/** Renders the held item sprite in the lower-right of the screen. */
	private ViewmodelRenderer _viewmodelRenderer;
	
	/** Manages teleportation between the main world and the Dragon's Lair boss arena. */
	private BossArenaManager _bossArenaManager;
	
	// --- Held torch dynamic light ---
	
	/** Light level emitted by a held torch. */
	private static final int HELD_TORCH_LIGHT_LEVEL = 10;
	
	/** Minimum seconds between held torch light updates to prevent rebuild spam. */
	private static final float HELD_TORCH_UPDATE_COOLDOWN = 0.2f;
	
	/** Block position of the last held torch light propagation (null = no active light). */
	private int[] _heldTorchLightPos;
	
	/** Cooldown timer for held torch light updates. */
	private float _heldTorchCooldown;
	
	/** Whether the player is currently dead (death screen showing). */
	private boolean _playerDead;
	
	/** Whether the dragon death has been processed (Recall Orb spawned, message shown). */
	private boolean _dragonDeathProcessed;
	
	/**
	 * True while the player has been spawned but hasn't touched the ground yet.<br>
	 * The loading screen stays visible during this phase so the player never sees<br>
	 * terrain pop-in or the fall from spawn height to the surface.
	 */
	private boolean _waitingForGround;
	
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
	
	/** True for one frame while waiting for the arena to generate (loading screen visible). */
	private boolean _pendingArenaEntry;
	
	/** Frame counter for pending arena entry (ensures loading screen renders before heavy generation). */
	private int _arenaEntryWaitFrames;
	
	/** Time when the state was last entered (for pause debouncing). */
	private long _lastEnterTime;
	
	/** Minimum time in milliseconds that must pass after entering before pause can be triggered again. */
	private static final long PAUSE_DEBOUNCE_TIME = 333; // 333ms delay.
	
	/** Starting time of day for new worlds (0.3 = early morning). */
	private static final float STARTING_TIME_OF_DAY = 0.3f;
	
	/** Loaded player save data for restoring state after spawn. Null if no save exists. */
	private PlayerSaveData _playerSaveData;
	
	/** Loaded tile entity save data for restoring after spawn. Null if no save exists. */
	private String _tileEntitySaveData;
	
	/** Loaded inventory save data for restoring after spawn. Null if no save exists. */
	private String _inventorySaveData;
	
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
	private static final int SPAWN_TIMEOUT_FRAMES = 600;
	
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
	
	/** True only for a brand-new world (no save data) - triggers safe surface search on initial spawn. */
	private boolean _newWorldSpawn;
	
	/** Spiral search step index for finding a safe spawn surface (GRASS with AIR above). */
	private int _spawnSearchStep;
	
	/**
	 * Minimum grass radius around the spawn point.<br>
	 * All blocks within this radius (a 9×9 area for radius 4) must have a GRASS surface with AIR above, ensuring the player spawns on open grassland.
	 */
	private static final int SPAWN_GRASS_RADIUS = 4;
	
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
			
			// Don't process PAUSE if we're in PAUSED state.
			final GameStateManager gsm = simpleCraft.getGameStateManager();
			if (gsm.getCurrentState() == GameState.PAUSED)
			{
				// Let the pause menu handle the ESC key.
				return;
			}
			
			// Debounce pause key to prevent immediate re-pausing after returning from pause.
			final long currentTime = System.currentTimeMillis();
			if (currentTime - _lastEnterTime < PAUSE_DEBOUNCE_TIME)
			{
				System.out.println("PlayingState: Debouncing PAUSE - too soon after enter (" + (currentTime - _lastEnterTime) + "ms)");
				return;
			}
			
			// If inventory is open, close it instead of opening pause.
			if (_inventoryScreen != null && _inventoryScreen.isOpen())
			{
				_inventoryScreen.close();
				return;
			}
			
			// If crafting screen is open, close it instead of opening pause.
			if (_craftingScreen != null && _craftingScreen.isOpen())
			{
				_craftingScreen.close();
				return;
			}
			
			// If chest screen is open, close it instead of opening pause.
			if (_chestScreen != null && _chestScreen.isOpen())
			{
				_chestScreen.close();
				return;
			}
			
			// If furnace screen is open, close it instead of opening pause.
			if (_furnaceScreen != null && _furnaceScreen.isOpen())
			{
				_furnaceScreen.close();
				return;
			}
			
			// Only open pause if we are currently in PLAYING state (not already paused) and not still loading or dead.
			if (gsm.getCurrentState() == GameState.PLAYING && !_pendingSpawn && !_waitingForGround && !_playerDead && !QuestionManager.isActive())
			{
				// Auto-save before pausing (skip if in boss arena - arena is not saved).
				if (_world != null && _playerController != null && _dayNightCycle != null && (_bossArenaManager == null || !_bossArenaManager.isInArena()))
				{
					SaveManager.save(_world, _playerController, _dayNightCycle);
				}
				
				// Set paused flag BEFORE the state transition so onExitState knows to preserve the world.
				_paused = true;
				gsm.switchTo(GameState.PAUSED);
			}
		};
		
		simpleCraft.getGameInputManager().addTrackedListener(_pauseListener, GameInputManager.PAUSE);
		
		// Register inventory toggle listener (Tab key).
		_inventoryListener = (String name, boolean isPressed, float tpf) ->
		{
			if (!isPressed)
			{
				return;
			}
			
			if (!GameInputManager.INVENTORY.equals(name))
			{
				return;
			}
			
			// Only toggle inventory during active gameplay (not loading, dead, paused, or in question dialog).
			final GameStateManager gsm = simpleCraft.getGameStateManager();
			if (gsm.getCurrentState() != GameState.PLAYING || _pendingSpawn || _waitingForGround || _playerDead || QuestionManager.isActive())
			{
				return;
			}
			
			if (_inventoryScreen != null)
			{
				if (_inventoryScreen.isOpen())
				{
					_inventoryScreen.close();
				}
				else
				{
					// Close crafting screen if open before opening inventory.
					if (_craftingScreen != null && _craftingScreen.isOpen())
					{
						_craftingScreen.close();
					}
					
					// Close chest screen if open before opening inventory.
					if (_chestScreen != null && _chestScreen.isOpen())
					{
						_chestScreen.close();
					}
					
					// Close furnace screen if open before opening inventory.
					if (_furnaceScreen != null && _furnaceScreen.isOpen())
					{
						_furnaceScreen.close();
					}
					
					_inventoryScreen.open();
				}
			}
			else if (_craftingScreen != null && _craftingScreen.isOpen())
			{
				_craftingScreen.close();
			}
			else if (_chestScreen != null && _chestScreen.isOpen())
			{
				_chestScreen.close();
			}
			else if (_furnaceScreen != null && _furnaceScreen.isOpen())
			{
				_furnaceScreen.close();
			}
		};
		
		simpleCraft.getGameInputManager().addTrackedListener(_inventoryListener, GameInputManager.INVENTORY);
	}
	
	@Override
	protected void cleanup(Application app)
	{
		// Remove the pause listener when this state is permanently detached.
		if (_pauseListener != null)
		{
			SimpleCraft.getInstance().getGameInputManager().removeTrackedListener(_pauseListener);
			_pauseListener = null;
		}
		
		// Remove the inventory listener.
		if (_inventoryListener != null)
		{
			SimpleCraft.getInstance().getGameInputManager().removeTrackedListener(_inventoryListener);
			_inventoryListener = null;
		}
		
		// Safety net: destroy world if still alive when state is detached.
		destroyWorld();
	}
	
	@Override
	protected void onEnterState()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		
		// Record the time we entered this state (for pause debouncing).
		_lastEnterTime = System.currentTimeMillis();
		
		// Returning from pause - world is still alive, just restore controls.
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
		
		// First entry - full world setup.
		
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
		
		// No scene lights needed - lighting is fully baked into vertex colors via Unshaded materials.
		// Sky light + directional face shading is computed per-vertex in RegionMeshBuilder.
		
		// Check for existing save data before creating the day/night cycle.
		// Load player data first so we can use saved time of day.
		_playerSaveData = SaveManager.loadPlayerData();
		_tileEntitySaveData = SaveManager.loadTileEntityData();
		_inventorySaveData = SaveManager.loadInventoryData();
		
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
		_atlasMaterial = atlasMaterial;
		
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
		
		// Disable the default fly camera - PlayerController handles all camera work.
		app.getFlyByCamera().setEnabled(false);
		app.getFlyByCamera().setDragToRotate(true);
		
		// Begin pending spawn - world needs to generate terrain before we can place the player.
		_pendingSpawn = true;
		_spawnWaitFrames = 0;
		_spawnTargetX = _playerSaveData != null ? (int) _playerSaveData.getPosX() : SPAWN_X;
		_spawnTargetZ = _playerSaveData != null ? (int) _playerSaveData.getPosZ() : SPAWN_Z;
		_spawnScanFromY = 255;
		_spawnFallbackY = SPAWN_FALLBACK_Y;
		_newWorldSpawn = (_playerSaveData == null);
		_spawnSearchStep = 0;
		
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
		
		// --- Pending arena entry: loading screen is visible, generate and enter arena. ---
		if (_pendingArenaEntry && _world != null && _playerController != null)
		{
			updateLoadingScreen(tpf);
			_arenaEntryWaitFrames++;
			
			// Wait at least 2 frames so the loading screen is guaranteed to render
			// before the heavy arena generation blocks the main thread.
			if (_arenaEntryWaitFrames < 2)
			{
				return;
			}
			
			_pendingArenaEntry = false;
			_arenaEntryWaitFrames = 0;
			
			if (_bossArenaManager != null)
			{
				_bossArenaManager.enterArena(_playerController, this);
			}
			
			hideLoadingScreen();
			
			// Show boss health bar.
			if (_playerHUD != null)
			{
				_playerHUD.showBossHealthBar();
			}
			
			// Reset dragon death tracking.
			_dragonDeathProcessed = false;
			
			// Re-register input for gameplay in the arena.
			_playerController.registerInput();
			if (_blockInteraction != null)
			{
				_blockInteraction.registerInput();
			}
			
			// Fade to night music for the ominous arena atmosphere.
			final SimpleCraft arenaApp = SimpleCraft.getInstance();
			final AudioManager arenaAudio = arenaApp.getAudioManager();
			if (arenaAudio != null)
			{
				arenaAudio.fadeInMusic(MusicManager.NIGHT_MUSIC_PATH, 1.5f);
			}
			
			arenaApp.getInputManager().setCursorVisible(false);
			return;
		}
		
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
			
			// Head clearance check: ensure 2 blocks of air (feet + head) above spawn Y.
			// If the player would spawn under a tree canopy or overhang, push upward.
			if (spawnY >= 0)
			{
				while (spawnY < Region.SIZE_Y - 2)
				{
					final Block feetBlock = _world.getBlock(_spawnTargetX, spawnY, _spawnTargetZ);
					final Block headBlock = _world.getBlock(_spawnTargetX, spawnY + 1, _spawnTargetZ);
					
					// Accept only AIR or non‑solid decorations (flowers, etc.) – no liquids.
					if ((feetBlock == Block.AIR || feetBlock.isDecoration()) && (headBlock == Block.AIR || headBlock.isDecoration()))
					{
						break;
					}
					
					spawnY++;
				}
			}
			
			// Reject spawn positions above water - the player would immediately fall in.
			// The head clearance check pushes spawnY above the water surface, but gravity
			// pulls the player right back into the water on the first frame.
			if (spawnY >= 0 && spawnY <= TerrainGenerator.WATER_LEVEL + 1)
			{
				final Block blockBelow = _world.getBlock(_spawnTargetX, spawnY - 1, _spawnTargetZ);
				if (blockBelow.isLiquid())
				{
					spawnY = -1;
				}
			}
			
			// --- Safe surface search for new worlds only. ---
			// Multi-step spiral search per frame: find a 9×9 area of GRASS blocks all at the same Y level.
			// Gate on center region loaded rather than spawnY - the spawn column may be
			// underwater (spawnY rejected above) but loaded regions still exist to search.
			final boolean centerRegionLoaded = _world.getRegion(Math.floorDiv(SPAWN_X, Region.SIZE_XZ), Math.floorDiv(SPAWN_Z, Region.SIZE_XZ)) != null;
			if (_newWorldSpawn && centerRegionLoaded)
			{
				final int STEPS_PER_FRAME = 20; // Reduced to avoid frame stutter.
				final int REGION_RADIUS_NEEDED = 1; // Need at least 1 region loaded around check point.
				
				boolean foundFlatGrassland = false;
				int bestY = -1;
				
				// Check multiple spiral positions this frame.
				for (int step = 0; step < STEPS_PER_FRAME && !foundFlatGrassland; step++)
				{
					// Calculate current search position.
					final int[] offset = spiralOffset(_spawnSearchStep);
					final int checkX = SPAWN_X + offset[0];
					final int checkZ = SPAWN_Z + offset[1];
					
					// PAUSE if regions aren't loaded around this position.
					// Without this, getBlock() returns garbage/unloaded data.
					// Don't advance _spawnSearchStep - retry this position next frame
					// when the region is likely loaded. Since regions load outward from
					// the center, positions beyond this one are also unlikely to be ready.
					if (!areRegionsLoadedAround(checkX, checkZ, REGION_RADIUS_NEEDED))
					{
						break; // Stop this frame's search - retry same position next frame.
					}
					
					// Find the surface Y at this position.
					int centerSurfaceY = -1;
					for (int y = _spawnScanFromY; y >= 0; y--)
					{
						final Block b = _world.getBlock(checkX, y, checkZ);
						if (b != Block.AIR && !b.isLiquid())
						{
							if (b == Block.GRASS)
							{
								centerSurfaceY = y;
							}
							break;
						}
					}
					
					// If center is grass, check the surrounding area for flat grassland.
					if (centerSurfaceY >= 0)
					{
						boolean allGrass = true;
						
						for (int dx = -SPAWN_GRASS_RADIUS; dx <= SPAWN_GRASS_RADIUS && allGrass; dx++)
						{
							for (int dz = -SPAWN_GRASS_RADIUS; dz <= SPAWN_GRASS_RADIUS && allGrass; dz++)
							{
								final int neighborX = checkX + dx;
								final int neighborZ = checkZ + dz;
								
								// Double-check neighbor regions are loaded too.
								if (!areRegionsLoadedAround(neighborX, neighborZ, 0))
								{
									allGrass = false;
									break;
								}
								
								int neighborSurfaceY = -1;
								Block neighborSurfaceBlock = Block.AIR;
								
								for (int y = _spawnScanFromY; y >= 0; y--)
								{
									final Block b = _world.getBlock(neighborX, y, neighborZ);
									if (b != Block.AIR && !b.isLiquid())
									{
										neighborSurfaceY = y;
										neighborSurfaceBlock = b;
										break;
									}
								}
								
								if (neighborSurfaceY != centerSurfaceY || neighborSurfaceBlock != Block.GRASS)
								{
									allGrass = false;
									break;
								}
								
								final Block feetBlock = _world.getBlock(neighborX, centerSurfaceY + 1, neighborZ);
								final Block headBlock = _world.getBlock(neighborX, centerSurfaceY + 2, neighborZ);
								if ((feetBlock != Block.AIR && !feetBlock.isDecoration()) || (headBlock != Block.AIR && !headBlock.isDecoration()))
								{
									allGrass = false;
									break;
								}
							}
						}
						
						if (allGrass)
						{
							foundFlatGrassland = true;
							bestY = centerSurfaceY;
							_spawnTargetX = checkX;
							_spawnTargetZ = checkZ;
							System.out.println("Found flat grassland at [" + _spawnTargetX + ", " + bestY + ", " + _spawnTargetZ + "] after " + _spawnSearchStep + " steps (9×9 area verified)");
						}
					}
					
					_spawnSearchStep++;
					
					// Log every 500 valid checks (not skips).
					if (_spawnSearchStep % 500 == 0)
					{
						System.out.println("Searching for flat grassland... step " + _spawnSearchStep + " checked at [" + checkX + ", " + checkZ + "]");
					}
				}
				
				if (foundFlatGrassland)
				{
					spawnY = bestY + 1;
					_newWorldSpawn = false;
				}
				else
				{
					// Not found yet - need to load more regions.
					spawnY = -1;
				}
			}
			
			// Use fallback if timeout reached and terrain scan still hasn't found ground.
			if (spawnY < 0 && _spawnWaitFrames >= SPAWN_TIMEOUT_FRAMES)
			{
				if (_newWorldSpawn)
				{
					// Grassland search timed out - abandon the strict 9×9 GRASS requirement.
					// Find the nearest dry column (above water level) in loaded regions.
					_newWorldSpawn = false;
					System.out.println("Grassland search timeout after " + _spawnWaitFrames + " frames - switching to dry column search.");
				}
				
				// Try to find the nearest dry column in loaded regions.
				final int[] drySpawn = findNearestDryColumn();
				if (drySpawn != null)
				{
					_spawnTargetX = drySpawn[0];
					_spawnTargetZ = drySpawn[1];
					spawnY = drySpawn[2] + 1; // Stand on top.
					System.out.println("Found dry column at [" + _spawnTargetX + ", " + spawnY + ", " + _spawnTargetZ + "] after " + _spawnWaitFrames + " frames.");
				}
				else if (_spawnWaitFrames >= SPAWN_TIMEOUT_FRAMES * 2)
				{
					// No dry land anywhere in the loaded area - entire region is ocean.
					// Accept the original spawn position on the highest solid block as a last resort.
					for (int y = _spawnScanFromY; y >= 0; y--)
					{
						final Block b = _world.getBlock(_spawnTargetX, y, _spawnTargetZ);
						if (b != Block.AIR && !b.isLiquid())
						{
							spawnY = y + 1;
							break;
						}
					}
					
					// If still nothing (all AIR), use the fallback height.
					if (spawnY < 0)
					{
						spawnY = _spawnFallbackY;
					}
					
					System.out.println("WARN: No dry land found in loaded area after " + _spawnWaitFrames + " frames - accepting spawn at Y=" + spawnY);
				}
			}
			
			// Always wait for nearby regions to load before placing the player.
			// Without this, the player can be placed before terrain data exists,
			// causing them to spawn inside the ground when blocks load around them.
			if (spawnY >= 0)
			{
				if (!areSpawnRegionsReady(_spawnTargetX, _spawnTargetZ))
				{
					// Terrain found but surrounding regions still loading - keep waiting.
					// As a safety net, only keep waiting up to triple the normal timeout.
					if (_spawnWaitFrames < SPAWN_TIMEOUT_FRAMES * 3)
					{
						return;
					}
					
					System.out.println("Region wait timeout after " + _spawnWaitFrames + " frames - forcing spawn.");
				}
				
				// Final safety check: verify the chosen position is not underwater.
				// Catches edge cases like forced spawn after region wait timeout.
				final Block belowSpawn = _world.getBlock(_spawnTargetX, spawnY - 1, _spawnTargetZ);
				if (belowSpawn.isLiquid() || spawnY <= TerrainGenerator.WATER_LEVEL)
				{
					// Position is wet - reject and keep searching.
					// Only give up and accept a wet spawn as an extreme last resort.
					if (_spawnWaitFrames < SPAWN_TIMEOUT_FRAMES * 3)
					{
						return;
					}
					
					System.out.println("WARN: All spawn attempts exhausted after " + _spawnWaitFrames + " frames - accepting wet spawn at Y=" + spawnY);
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
		
		// --- Waiting for the player to physically touch the ground after spawn. ---
		// The loading screen stays visible while the player falls from spawn height
		// to the surface. World and player physics keep ticking behind the screen
		// so terrain continues loading and gravity settles the player.
		if (_waitingForGround && _world != null && _playerController != null)
		{
			updateLoadingScreen(tpf);
			
			// Keep ticking the world so async region loading and mesh building continue.
			final SimpleCraft groundApp = SimpleCraft.getInstance();
			final int groundRenderDist = groundApp.getSettingsManager().getRenderDistance();
			_world.update(_playerController.getPosition(), groundRenderDist);
			
			// Update player physics (gravity, collision) so they actually fall and land.
			_playerController.update(tpf);
			
			if (_playerController.isOnGround())
			{
				_waitingForGround = false;
				hideLoadingScreen();
				
				// Ensure cursor is hidden for first-person control.
				groundApp.getInputManager().setCursorVisible(false);
				
				final Vector3f landPos = _playerController.getPosition();
				System.out.println("Player touched ground at [" + landPos.x + ", " + landPos.y + ", " + landPos.z + "] - loading screen removed.");
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
			// Skip in boss arena - night music is forced and should not be overridden.
			if (_musicManager != null && !isInBossArena())
			{
				final Vector3f pos = _playerController.getPosition();
				final boolean underground = _world.isUnderground((int) pos.x, (int) pos.y, (int) pos.z);
				_musicManager.update(tpf, _playerController.isInWater(), underground);
			}
			
			// Determine if inventory or crafting screen is open (skip player movement and block interaction when open).
			final boolean inventoryOpen = _inventoryScreen != null && _inventoryScreen.isOpen();
			final boolean craftingOpen = _craftingScreen != null && _craftingScreen.isOpen();
			final boolean chestOpen = _chestScreen != null && _chestScreen.isOpen();
			final boolean furnaceOpen = _furnaceScreen != null && _furnaceScreen.isOpen();
			final boolean screenOpen = inventoryOpen || craftingOpen || chestOpen || furnaceOpen;
			
			// Update player movement and camera (skip when a screen is open).
			if (!screenOpen)
			{
				_playerController.update(tpf);
			}
			else
			{
				// Still update camera position so it doesn't glitch.
				// Player update handles camera - when a screen is open, input is unregistered
				// so no movement occurs, but we still need to call update for camera positioning.
				_playerController.update(tpf);
				
				// Update the active screen.
				if (inventoryOpen)
				{
					_inventoryScreen.update(tpf);
				}
				
				if (craftingOpen)
				{
					_craftingScreen.update(tpf);
				}
				
				if (chestOpen)
				{
					_chestScreen.update(tpf);
				}
				
				if (furnaceOpen)
				{
					_furnaceScreen.update(tpf);
				}
			}
			
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
			
			// Update viewmodel (held item sprite - swap, swing, bob).
			if (_viewmodelRenderer != null)
			{
				_viewmodelRenderer.update(SimpleCraft.getInstance().getCamera(), _playerController.getInventory(), _playerController.isMoving() && _playerController.isOnGround(), tpf);
			}
			
			// Update held torch dynamic light.
			// When the player holds a torch, propagate block light at their feet.
			// Only updates on block boundary crossings with a cooldown to limit rebuild cost.
			updateHeldTorchLight(tpf);
			
			final SimpleCraft app = SimpleCraft.getInstance();
			final int renderDistance = app.getSettingsManager().getRenderDistance();
			_world.update(_playerController.getPosition(), renderDistance);
			
			// --- Player -> Enemy attack priority ---
			// Check enemies FIRST on left-click. If the crosshair is on an enemy,
			// suppress block interaction's attack so we don't mine blocks behind enemies.
			// Skip combat when a screen is open or in the boss arena.
			boolean suppressBlockAttack = false;
			if (_combatSystem != null && _spawnSystem != null && !_playerDead && !screenOpen && !isInBossArena())
			{
				if (_blockInteraction != null && _blockInteraction.isAttackHeld())
				{
					final List<Enemy> enemies = _spawnSystem.getEnemies();
					suppressBlockAttack = _combatSystem.tryPlayerAttack(app.getCamera(), enemies, _world, _playerController);
				}
			}
			
			// Trigger viewmodel swing on any left-click (air, block, or enemy).
			// triggerSwing() is idempotent - only starts if not already swinging.
			if (_viewmodelRenderer != null && _blockInteraction != null && _blockInteraction.isAttackHeld() && !_playerDead && !screenOpen)
			{
				_viewmodelRenderer.triggerSwing();
			}
			
			// Update block interaction (raycasting, breaking, placing).
			// Skip when a screen is open.
			if (_blockInteraction != null && !_playerDead && !screenOpen)
			{
				_blockInteraction.setAttackSuppressed(suppressBlockAttack);
				_blockInteraction.setShowHighlight(app.getSettingsManager().isShowHighlight());
				_blockInteraction.update(tpf);
			}
			
			// Update enemy spawn system (spawning, despawning, AI, animation).
			// Skip in boss arena - main world enemies are detached and should not update.
			if (_spawnSystem != null && !isInBossArena())
			{
				_spawnSystem.update(_playerController.getPosition(), _playerController.isInWater(), _world, tpf);
			}
			
			// Update dropped items (bob animation, pickup checks, expiration).
			// Skip in boss arena - main world drops are detached.
			if (_dropManager != null && !_playerDead && !isInBossArena())
			{
				_dropManager.update(_playerController.getPosition(), _playerController.getInventory(), tpf);
			}
			
			// Update tile entity manager (campfire particles, furnace timers, etc.).
			final TileEntityManager tileEntityManager = _world.getTileEntityManager();
			if (tileEntityManager != null)
			{
				tileEntityManager.update(tpf);
			}
			
			// Update particle effects (auto-cleanup of expired emitters).
			if (_particleManager != null)
			{
				_particleManager.update(tpf);
			}
			
			// Update combat system (enemy attacks, screen flash fade).
			// Skip in boss arena - no enemies to fight and spawn system is paused.
			if (_combatSystem != null && _spawnSystem != null && !isInBossArena())
			{
				_combatSystem.update(_playerController, _spawnSystem.getEnemies(), _world, tpf);
			}
			
			// --- Boss Arena Dragon Updates ---
			if (isInBossArena() && _bossArenaManager != null)
			{
				final simplecraft.enemy.Enemy dragon = _bossArenaManager.getDragon();
				
				// Update dragon AI, animation and arena drops.
				_bossArenaManager.update(_playerController, _world, tpf);
				
				// Player -> Dragon attack.
				if (_combatSystem != null && !_playerDead && !screenOpen && dragon != null && dragon.isAlive() && !dragon.isDying())
				{
					if (_blockInteraction != null && _blockInteraction.isAttackHeld())
					{
						suppressBlockAttack = _combatSystem.tryPlayerAttackDragon(app.getCamera(), dragon, _world, _playerController);
					}
				}
				
				// Dragon -> Player combat (bite, tail swipe, charge contact damage).
				if (_combatSystem != null && dragon != null)
				{
					_combatSystem.updateDragonCombat(_playerController, dragon, tpf);
				}
				else if (_combatSystem != null)
				{
					// No dragon - still update flash fade.
					_combatSystem.updateDragonCombat(_playerController, null, tpf);
				}
				
				// Dragon death detection.
				if (dragon != null && dragon.isDying() && !_dragonDeathProcessed)
				{
					_dragonDeathProcessed = true;
					
					// Spawn drops via the normal drop table (Recall Orb + Gold Bars).
					if (_combatSystem != null)
					{
						_combatSystem.onEnemyDeath(_playerController, dragon);
					}
					
					// Show victory message.
					MessageManager.show("The Dragon has been slain!", 5.0f);
					
					// Hide boss health bar.
					if (_playerHUD != null)
					{
						_playerHUD.hideBossHealthBar();
					}
					
					System.out.println("PlayingState: Dragon killed! Recall Orb spawned.");
				}
				
				// Dragon fully dead (animation complete) - clean up model from scene.
				if (dragon != null && !dragon.isAlive() && _dragonDeathProcessed)
				{
					_bossArenaManager.onDragonDeathComplete();
				}
				
				// Update boss health bar.
				if (_playerHUD != null && dragon != null && dragon.isAlive())
				{
					_playerHUD.updateBossHealthBar(dragon.getHealth(), dragon.getMaxHealth(), dragon.getBossPhase());
				}
			}
			
			// --- Death detection and respawn ---
			if (_playerController.isDead() && !_playerDead)
			{
				// Player just died - show death screen.
				// If in boss arena, the arena stays visible behind the death screen.
				// Arena exit happens later in performRespawn() when the player clicks Respawn.
				_playerDead = true;
				
				// Remove held torch light before death screen.
				removeHeldTorchLight();
				
				// Close inventory if open.
				if (_inventoryScreen != null && _inventoryScreen.isOpen())
				{
					_inventoryScreen.close();
				}
				
				// Close crafting screen if open.
				if (_craftingScreen != null && _craftingScreen.isOpen())
				{
					_craftingScreen.close();
				}
				
				// Close chest screen if open.
				if (_chestScreen != null && _chestScreen.isOpen())
				{
					_chestScreen.close();
				}
				
				// Close furnace screen if open.
				if (_furnaceScreen != null && _furnaceScreen.isOpen())
				{
					_furnaceScreen.close();
				}
				
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
			
			// Safety guard: hide boss bar if we left the arena (e.g. Recall Orb victory exit).
			if (_playerHUD != null && !isInBossArena())
			{
				_playerHUD.hideBossHealthBar();
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
					_blockInteraction != null ? _blockInteraction.getHitsDelivered() : 0,
					_blockInteraction != null ? _blockInteraction.getHitsRequired() : 0,
					_blockInteraction != null && _blockInteraction.isBreaking(),
					app.getSettingsManager().isShowCrosshair()
				);
			} // @formatter:on
		}
	}
	
	// ========================================================
	// Held Torch Light.
	// ========================================================
	
	/**
	 * Updates the dynamic block light emitted by a held torch.<br>
	 * Propagates light at the player's feet when holding a torch, removes it when<br>
	 * switching to another item. Only fires on block boundary crossings with a<br>
	 * cooldown to prevent rebuild spam while walking quickly.
	 * @param tpf time per frame
	 */
	private void updateHeldTorchLight(float tpf)
	{
		if (_world == null || _playerController == null || _playerDead)
		{
			return;
		}
		
		// Tick cooldown.
		if (_heldTorchCooldown > 0)
		{
			_heldTorchCooldown -= tpf;
		}
		
		// Check if the player is holding a torch.
		final ItemInstance held = _playerController.getInventory().getSelectedItem();
		final boolean holdingTorch;
		if (held != null && held.getTemplate().getType() == ItemType.BLOCK)
		{
			final Block block = held.getTemplate().getPlacesBlock();
			holdingTorch = (block == Block.TORCH);
		}
		else
		{
			holdingTorch = false;
		}
		
		if (!holdingTorch)
		{
			// Not holding a torch - remove any active held torch light.
			removeHeldTorchLight();
			return;
		}
		
		// Player is holding a torch - check if they moved to a new block.
		final Vector3f pos = _playerController.getPosition();
		final int bx = (int) Math.floor(pos.x);
		final int by = (int) Math.floor(pos.y);
		final int bz = (int) Math.floor(pos.z);
		
		if (_heldTorchLightPos != null && _heldTorchLightPos[0] == bx && _heldTorchLightPos[1] == by && _heldTorchLightPos[2] == bz)
		{
			// Same block - no update needed.
			return;
		}
		
		// Cooldown check - skip update if too soon after last one.
		if (_heldTorchCooldown > 0)
		{
			return;
		}
		
		// Remove old light, propagate at new position.
		removeHeldTorchLight();
		_world.propagateBlockLight(bx, by, bz, HELD_TORCH_LIGHT_LEVEL);
		_heldTorchLightPos = new int[]
		{
			bx,
			by,
			bz
		};
		_heldTorchCooldown = HELD_TORCH_UPDATE_COOLDOWN;
	}
	
	/**
	 * Removes the currently active held torch light, if any.<br>
	 * Called when the player switches away from a torch, dies, or the world is destroyed.
	 */
	private void removeHeldTorchLight()
	{
		if (_heldTorchLightPos != null && _world != null)
		{
			_world.removeBlockLight(_heldTorchLightPos[0], _heldTorchLightPos[1], _heldTorchLightPos[2]);
			_heldTorchLightPos = null;
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
		
		// If dying in the boss arena, exit the arena first to restore the main world.
		// This happens here (on respawn click) instead of on death detection, so the
		// death screen shows the arena behind it rather than the normal world.
		if (_bossArenaManager != null && _bossArenaManager.isInArena())
		{
			_bossArenaManager.exitArena(_playerController, this, false);
			System.out.println("PlayingState: Exited boss arena on respawn.");
			
			// Hide boss health bar on arena death exit.
			if (_playerHUD != null)
			{
				_playerHUD.hideBossHealthBar();
			}
			
			// Restore music to match the current time of day.
			final AudioManager respawnAudio = app.getAudioManager();
			if (respawnAudio != null && _dayNightCycle != null)
			{
				final String track = _dayNightCycle.isNight() ? MusicManager.NIGHT_MUSIC_PATH : MusicManager.DAY_MUSIC_PATH;
				respawnAudio.fadeInMusic(track, 2.0f);
			}
		}
		
		// Get the active respawn point (campfire if set, otherwise initial spawn).
		final Vector3f respawnPoint = _playerController.getActiveRespawnPoint();
		_spawnTargetX = (int) respawnPoint.x;
		_spawnTargetZ = (int) respawnPoint.z;
		
		System.out.println("Respawn target: [" + _spawnTargetX + ", " + respawnPoint.y + ", " + _spawnTargetZ + "] (campfire=" + (_playerController.getCampfireSpawn() != null) + ")");
		
		// Pre-position the player at the respawn target immediately.
		// This prevents the player controller from having stale arena coordinates
		// while the main world regions are loading during the pending spawn loop.
		_playerController.setPosition(_spawnTargetX + 0.5f, respawnPoint.y, _spawnTargetZ + 0.5f);
		
		// For campfire spawns: scan downward from the saved Y to avoid landing on a ceiling.
		// For initial spawn: scan from the world top (surface is always open sky).
		final boolean hasCampfire = _playerController.getCampfireSpawn() != null;
		_spawnScanFromY = hasCampfire ? (int) respawnPoint.y : 255;
		_spawnFallbackY = hasCampfire ? (int) respawnPoint.y : SPAWN_FALLBACK_Y;
		
		// Ensure safe-surface spiral search is disabled (only used for brand-new worlds).
		_newWorldSpawn = false;
		_spawnSearchStep = 0;
		
		// Hide death screen, clean up HUD and show loading screen.
		if (_playerHUD != null)
		{
			_playerHUD.hideDeathScreen();
		}
		
		cleanupHUD();
		cleanupRespawnListener();
		showLoadingScreen();
		
		// Hide cursor during loading.
		app.getInputManager().setCursorVisible(false);
		
		// Kick off region loading around the respawn position immediately,
		// so background threads start generating terrain this frame rather than
		// waiting until the first iteration of the pending spawn loop.
		final int renderDistance = app.getSettingsManager().getRenderDistance();
		_world.update(new Vector3f(_spawnTargetX, _spawnFallbackY, _spawnTargetZ), renderDistance);
		
		// Enter pending spawn - the existing loop will tick the world and call finalizeSpawn.
		_pendingSpawn = true;
		_spawnWaitFrames = 0;
		
		System.out.println("Respawn initiated - waiting for terrain at [" + _spawnTargetX + ", " + _spawnTargetZ + "]...");
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
	
	// ========================================================
	// World Access (used by BossArenaManager).
	// ========================================================
	
	/**
	 * Returns the currently active world.
	 */
	public World getWorld()
	{
		return _world;
	}
	
	/**
	 * Returns the shared texture atlas material.<br>
	 * Used by BossArenaManager to create the arena world with matching textures.
	 */
	public Material getAtlasMaterial()
	{
		return _atlasMaterial;
	}
	
	/**
	 * Swaps the active world reference.<br>
	 * Called by BossArenaManager when entering or exiting the boss arena.<br>
	 * Updates the world reference used by this state and by BlockInteraction.
	 * @param newWorld the new world to use
	 */
	public void swapWorld(World newWorld)
	{
		_world = newWorld;
		
		if (_blockInteraction != null)
		{
			_blockInteraction.setWorld(newWorld);
		}
	}
	
	/**
	 * Returns the BossArenaManager instance.
	 */
	public BossArenaManager getBossArenaManager()
	{
		return _bossArenaManager;
	}
	
	/**
	 * Returns the combat system instance.<br>
	 * Used by BossArenaManager to wire dragon combat.
	 */
	public CombatSystem getCombatSystem()
	{
		return _combatSystem;
	}
	
	/**
	 * Returns the main world drop manager (not the arena drop manager).<br>
	 * Used by BossArenaManager to restore the drop manager reference on arena exit.
	 */
	public DropManager getMainDropManager()
	{
		return _dropManager;
	}
	
	/**
	 * Begins the arena entry sequence: shows a loading screen, disables input,<br>
	 * consumes the Dragon Orb and sets a flag so the next frame performs the actual<br>
	 * arena generation and world swap.<br>
	 * <br>
	 * This split across frames ensures the loading screen is rendered before<br>
	 * the heavy arena generation work blocks the main thread.
	 */
	public void beginArenaEntry()
	{
		if (_pendingArenaEntry)
		{
			return;
		}
		
		// Consume the Dragon Orb immediately.
		if (_playerController != null)
		{
			_playerController.getInventory().consumeSelectedItem();
		}
		
		// Disable player input during loading.
		if (_playerController != null)
		{
			_playerController.unregisterInput();
		}
		
		if (_blockInteraction != null)
		{
			_blockInteraction.unregisterInput();
		}
		
		// Show loading screen (will be visible this frame's render pass).
		showLoadingScreen();
		
		_pendingArenaEntry = true;
		_arenaEntryWaitFrames = 0;
		
		System.out.println("PlayingState: Arena entry initiated - loading screen shown, generating next frame.");
	}
	
	/**
	 * Returns true if the player is currently in the boss arena.
	 */
	private boolean isInBossArena()
	{
		return _bossArenaManager != null && _bossArenaManager.isInArena();
	}
	
	/**
	 * Returns the enemy scene node, or null if the spawn system is not initialized.<br>
	 * Used by BossArenaManager to detach/reattach enemies during arena transitions.
	 */
	public Node getEnemyNode()
	{
		return _spawnSystem != null ? _spawnSystem.getEnemyNode() : null;
	}
	
	/**
	 * Returns the drop manager scene node, or null if the drop manager is not initialized.<br>
	 * Used by BossArenaManager to detach/reattach drops during arena transitions.
	 */
	public Node getDropNode()
	{
		return _dropManager != null ? _dropManager.getNode() : null;
	}
	
	@Override
	protected void onExitState()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		
		// Clean up respawn listener if active.
		cleanupRespawnListener();
		
		// Close inventory if open.
		if (_inventoryScreen != null && _inventoryScreen.isOpen())
		{
			_inventoryScreen.close();
		}
		
		// Close crafting screen if open.
		if (_craftingScreen != null && _craftingScreen.isOpen())
		{
			_craftingScreen.close();
		}
		
		// Close chest screen if open.
		if (_chestScreen != null && _chestScreen.isOpen())
		{
			_chestScreen.close();
		}
		
		// Close furnace screen if open.
		if (_furnaceScreen != null && _furnaceScreen.isOpen())
		{
			_furnaceScreen.close();
		}
		
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
		
		// If pausing, keep the world alive - only disable controls above.
		if (_paused)
		{
			// Clean up HUD during pause so it doesn't overlap the pause menu.
			cleanupHUD();
			return;
		}
		
		// Full exit (returning to main menu) - save and tear down everything.
		// If in boss arena, exit arena first to restore main world before saving.
		if (_bossArenaManager != null && _bossArenaManager.isInArena())
		{
			_bossArenaManager.exitArena(_playerController, this, false);
			
			// Restore the player's position to their respawn point before saving.
			// Without this, the arena coordinates (200, 2, 145) would be saved as the
			// main world position, causing the player to spawn underground on next load.
			if (_playerController != null)
			{
				final Vector3f respawnPoint = _playerController.getActiveRespawnPoint();
				_playerController.setPosition(respawnPoint.x, respawnPoint.y, respawnPoint.z);
				
				// Also restore health since the player might have been dead.
				if (_playerController.isDead())
				{
					_playerController.setHealth(_playerController.getMaxHealth());
				}
				
				System.out.println("PlayingState: Restored player to respawn point [" + respawnPoint.x + ", " + respawnPoint.y + ", " + respawnPoint.z + "] before saving.");
			}
		}
		
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
	 * Returns an (x, z) offset for step N of a clockwise spiral pattern centered on (0, 0). Step 0 = origin, then expands outward in rings. Used by the safe spawn surface search to check progressively farther columns.
	 * @param step the spiral step index (0-based)
	 * @return int array {offsetX, offsetZ}
	 */
	private static int[] spiralOffset(int step)
	{
		if (step == 0)
		{
			return new int[]
			{
				0,
				0
			};
		}
		
		int x = 0, z = 0;
		int dx = 1, dz = 0;
		int segmentLength = 1;
		int segmentPassed = 0;
		int segmentCount = 0;
		
		for (int i = 0; i < step; i++)
		{
			x += dx;
			z += dz;
			segmentPassed++;
			
			if (segmentPassed == segmentLength)
			{
				segmentPassed = 0;
				
				// Rotate direction: +X -> +Z -> -X -> -Z.
				final int temp = dx;
				dx = -dz;
				dz = temp;
				segmentCount++;
				if (segmentCount % 2 == 0)
				{
					segmentLength++;
				}
			}
		}
		
		return new int[]
		{
			x,
			z
		};
	}
	
	/**
	 * Returns true if regions are loaded around the given world position.
	 */
	private boolean areRegionsLoadedAround(int worldX, int worldZ, int radius)
	{
		final int centerRX = Math.floorDiv(worldX, Region.SIZE_XZ);
		final int centerRZ = Math.floorDiv(worldZ, Region.SIZE_XZ);
		
		for (int rx = centerRX - radius; rx <= centerRX + radius; rx++)
		{
			for (int rz = centerRZ - radius; rz <= centerRZ + radius; rz++)
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
	 * Scans loaded regions near the spawn origin for the nearest column above water level.<br>
	 * Used as a fallback when the strict 9×9 grassland search times out.<br>
	 * Searches in a spiral pattern from SPAWN_X/Z and returns the first column where<br>
	 * the surface is above water with head clearance.<br>
	 * The search covers the entire loaded area (render distance) and stops early if it<br>
	 * reaches the boundary of loaded regions (many consecutive unloaded columns).
	 * @return int array {worldX, worldZ, surfaceY} or null if no dry column found
	 */
	private int[] findNearestDryColumn()
	{
		// Search up to 100,000 spiral steps - covers a radius of ~158 blocks,
		// well beyond the loaded region boundary for typical render distances.
		// Early exit after 200 consecutive unloaded columns (reached the boundary).
		int consecutiveUnloaded = 0;
		
		for (int step = 0; step < 100000; step++)
		{
			final int[] offset = spiralOffset(step);
			final int checkX = SPAWN_X + offset[0];
			final int checkZ = SPAWN_Z + offset[1];
			
			// Skip if region not loaded.
			if (_world.getRegion(Math.floorDiv(checkX, Region.SIZE_XZ), Math.floorDiv(checkZ, Region.SIZE_XZ)) == null)
			{
				consecutiveUnloaded++;
				if (consecutiveUnloaded > 200)
				{
					// We've gone well past the loaded boundary - stop searching.
					break;
				}
				continue;
			}
			
			consecutiveUnloaded = 0;
			
			// Find surface block.
			for (int y = _spawnScanFromY; y >= 0; y--)
			{
				final Block b = _world.getBlock(checkX, y, checkZ);
				if (b != Block.AIR && !b.isLiquid())
				{
					// Must be above water level.
					if (y > TerrainGenerator.WATER_LEVEL)
					{
						// Head clearance check.
						final Block feet = _world.getBlock(checkX, y + 1, checkZ);
						final Block head = _world.getBlock(checkX, y + 2, checkZ);
						if ((feet == Block.AIR || feet.isDecoration()) && (head == Block.AIR || head.isDecoration()))
						{
							return new int[]
							{
								checkX,
								checkZ,
								y
							};
						}
					}
					break; // Surface found but underwater or no clearance - try next column.
				}
			}
		}
		
		System.out.println("findNearestDryColumn: No dry land found in loaded area.");
		return null;
	}
	
	/**
	 * Called once terrain is available at the spawn position and nearby regions are loaded.<br>
	 * On initial entry: creates the player controller, block interaction and HUD.<br>
	 * On respawn: places the player, re-registers input and recreates the HUD.<br>
	 * In both cases, sets {@code _waitingForGround = true} so the loading screen remains<br>
	 * visible until the player physically touches the ground (checked in the update loop).
	 * @param spawnY the Y coordinate to place the player (feet level)
	 */
	private void finalizeSpawn(int spawnY)
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		
		_pendingSpawn = false;
		
		// --- Respawn path: player controller already exists (death -> respawn). ---
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
			
			// Keep loading screen visible - it will be removed once the player touches the ground.
			_waitingForGround = true;
			
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
			// Use saved position (the spawnY from terrain scan is a fallback - saved position is authoritative).
			_playerController.setPosition(_playerSaveData.getPosX(), _playerSaveData.getPosY(), _playerSaveData.getPosZ());
			_playerController.setHealth(_playerSaveData.getHealth());
			
			// Restore initial spawn point.
			_playerController.setInitialSpawn(_playerSaveData.getInitialSpawnX(), _playerSaveData.getInitialSpawnY(), _playerSaveData.getInitialSpawnZ());
			
			// Restore campfire spawn point.
			if (_playerSaveData.hasCampfireSpawn())
			{
				_playerController.setCampfireSpawnDirect(new Vector3f(_playerSaveData.getCampfireSpawnX(), _playerSaveData.getCampfireSpawnY(), _playerSaveData.getCampfireSpawnZ()));
			}
			
			System.out.println("Restored player from save: pos=[" + _playerSaveData.getPosX() + ", " + _playerSaveData.getPosY() + ", " + _playerSaveData.getPosZ() + "], health=" + _playerSaveData.getHealth());
		}
		else
		{
			// New world - use the (possibly adjusted) spawn position from safe surface search.
			_playerController.setPosition(_spawnTargetX, spawnY, _spawnTargetZ);
			_playerController.setInitialSpawn(_spawnTargetX, spawnY, _spawnTargetZ);
		}
		
		// Restore inventory from save data (overrides the default starting inventory).
		// Must happen after player controller creation (which populates defaults)
		// but before any gameplay systems that read inventory state.
		if (_inventorySaveData != null)
		{
			_playerController.getInventory().deserialize(_inventorySaveData);
			
			// Strip any Recall Orb that may have been saved while in the arena
			// (e.g. game crashed or force-quit after killing the dragon but before using the orb).
			// The Recall Orb must never persist into the main world.
			final simplecraft.item.Inventory inv = _playerController.getInventory();
			for (int i = 0; i < 36; i++)
			{
				final simplecraft.item.ItemInstance item = inv.getSlot(i);
				if (item != null && "golden_orb".equals(item.getTemplate().getId()))
				{
					inv.setSlot(i, null);
					System.out.println("PlayingState: Stripped leftover Recall Orb from slot " + i + " on world load.");
				}
			}
		}
		
		_playerController.registerInput();
		
		// Create and initialize block interaction (raycasting, breaking, placing).
		_blockInteraction = new BlockInteraction(app.getCamera(), app.getInputManager(), _world, _playerController, app.getAssetManager(), app.getAudioManager());
		_blockInteraction.registerInput();
		app.getRootNode().attachChild(_blockInteraction.getOverlayNode());
		app.getRootNode().attachChild(_blockInteraction.getDestructionEffectsNode());
		
		// Create the viewmodel renderer (first-person held item sprite on GUI).
		_viewmodelRenderer = new ViewmodelRenderer(app.getAssetManager(), app.getRootNode());
		
		// Create the boss arena manager for Dragon's Lair teleportation.
		_bossArenaManager = new BossArenaManager();
		if (_blockInteraction != null)
		{
			_blockInteraction.setBossArenaManager(_bossArenaManager);
		}
		
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
		// Block light data is not persisted - only block types and tile entities are saved.
		// This restores the lighting state so torches and campfires illuminate their surroundings.
		_world.repropagateAllBlockLights();
		
		// Rebuild all loaded region meshes now that tile entities are populated.
		// FLAT_PANEL blocks (doors, windows) require TileEntityManager data for correct
		// geometry (facing direction, open/closed state), which was unavailable during
		// initial background mesh building when the TileEntityManager was still empty.
		_world.rebuildAllLoadedRegions();
		
		// Clear save data references - no longer needed.
		_playerSaveData = null;
		_tileEntitySaveData = null;
		_inventorySaveData = null;
		
		// Create the player HUD.
		createHUD();
		
		// Initialize the enemy spawn system.
		final Node enemyNode = new Node("Enemies");
		app.getRootNode().attachChild(enemyNode);
		_spawnSystem = new SpawnSystem(enemyNode, app.getAssetManager(), _world.getSeed());
		_spawnSystem.setPlayerSpawnZone(_spawnTargetX, _spawnTargetZ);
		_spawnSystem.setDayNightCycle(_dayNightCycle);
		_spawnSystem.setAudioManager(app.getAudioManager());
		
		// Initialize the combat system (screen flashes, enemy -> player damage, player -> enemy attacks).
		_combatSystem = new CombatSystem(app.getAudioManager());
		
		// Initialize the drop manager (enemy death drops, pickup, despawn).
		_dropManager = new DropManager(app.getAssetManager(), app.getAudioManager(), _atlasMaterial);
		app.getRootNode().attachChild(_dropManager.getNode());
		
		// Wire drop manager to combat system for enemy death drops.
		_combatSystem.setDropManager(_dropManager);
		
		// Wire drop manager to block interaction for block break drops.
		_blockInteraction.setDropManager(_dropManager);
		
		// Wire drop manager and world to inventory screen for item discards.
		if (_inventoryScreen != null)
		{
			_inventoryScreen.setDropManager(_dropManager);
			_inventoryScreen.setWorld(_world);
		}
		
		// Wire drop manager and world to chest screen for item discards.
		if (_chestScreen != null)
		{
			_chestScreen.setDropManager(_dropManager);
			_chestScreen.setWorld(_world);
		}
		
		// Wire drop manager and world to furnace screen for item discards.
		if (_furnaceScreen != null)
		{
			_furnaceScreen.setDropManager(_dropManager);
		}
		
		// Initialize the particle manager for block break and combat effects.
		_particleManager = new ParticleManager(app.getAssetManager());
		app.getRootNode().attachChild(_particleManager.getNode());
		
		// Wire particle manager to block interaction and combat system.
		_blockInteraction.setParticleManager(_particleManager);
		_combatSystem.setParticleManager(_particleManager);
		EnemyAI.setParticleManager(_particleManager);
		
		// Wire viewmodel renderer to block interaction and combat system for swing animation.
		_blockInteraction.setViewmodelRenderer(_viewmodelRenderer);
		_combatSystem.setViewmodelRenderer(_viewmodelRenderer);
		
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
		
		// Keep loading screen visible - it will be removed once the player touches the ground.
		_waitingForGround = true;
		
		// Ensure cursor is hidden for first-person control.
		app.getInputManager().setCursorVisible(false);
		
		final Vector3f pos = _playerController.getPosition();
		System.out.println("World loaded. Player at [" + pos.x + ", " + pos.y + ", " + pos.z + "]");
	}
	
	// ========================================================
	// HUD Management.
	// ========================================================
	
	/**
	 * Creates the player HUD, inventory screen, crafting screen and links them to the block interaction handler.
	 */
	private void createHUD()
	{
		_playerHUD = new PlayerHUD();
		_playerHUD.setBlockInteraction(_blockInteraction);
		_playerHUD.setInventory(_playerController.getInventory());
		
		// Create inventory screen (hidden initially).
		_inventoryScreen = new InventoryScreen(_playerController, _blockInteraction);
		if (_dropManager != null)
		{
			_inventoryScreen.setDropManager(_dropManager);
		}
		
		if (_world != null)
		{
			_inventoryScreen.setWorld(_world);
		}
		
		// Create crafting screen (hidden initially, opened by right-clicking a Crafting Table).
		final SimpleCraft app = SimpleCraft.getInstance();
		_craftingScreen = new CraftingScreen(_playerController, _blockInteraction, app.getAudioManager());
		if (_blockInteraction != null)
		{
			_blockInteraction.setCraftingScreen(_craftingScreen);
		}
		
		// Create chest screen (hidden initially, opened by right-clicking a Chest).
		_chestScreen = new ChestScreen(_playerController, _blockInteraction);
		if (_dropManager != null)
		{
			_chestScreen.setDropManager(_dropManager);
		}
		
		if (_world != null)
		{
			_chestScreen.setWorld(_world);
		}
		
		if (_blockInteraction != null)
		{
			_blockInteraction.setChestScreen(_chestScreen);
		}
		
		// Create furnace screen (hidden initially, opened by right-clicking a Furnace).
		_furnaceScreen = new FurnaceScreen(_playerController, _blockInteraction);
		if (_dropManager != null)
		{
			_furnaceScreen.setDropManager(_dropManager);
		}
		
		if (_blockInteraction != null)
		{
			_blockInteraction.setFurnaceScreen(_furnaceScreen);
		}
	}
	
	/**
	 * Removes the player HUD, inventory screen and crafting screen.
	 */
	private void cleanupHUD()
	{
		if (_craftingScreen != null)
		{
			_craftingScreen.cleanup();
			_craftingScreen = null;
		}
		
		if (_chestScreen != null)
		{
			_chestScreen.cleanup();
			_chestScreen = null;
		}
		
		if (_furnaceScreen != null)
		{
			_furnaceScreen.cleanup();
			_furnaceScreen = null;
		}
		
		if (_inventoryScreen != null)
		{
			_inventoryScreen.cleanup();
			_inventoryScreen = null;
		}
		
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
	 * No scene lights to remove - lighting is baked into vertex colors.
	 */
	public void destroyWorld()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		
		// Safety net: if still in the boss arena when the world is being destroyed
		// (e.g. quit-to-menu from pause while in arena), exit the arena first,
		// restore the player to their respawn point and save.
		if (_bossArenaManager != null && _bossArenaManager.isInArena())
		{
			_bossArenaManager.exitArena(_playerController, this, false);
			
			if (_playerController != null)
			{
				final Vector3f respawnPoint = _playerController.getActiveRespawnPoint();
				_playerController.setPosition(respawnPoint.x, respawnPoint.y, respawnPoint.z);
				
				if (_playerController.isDead())
				{
					_playerController.setHealth(_playerController.getMaxHealth());
				}
			}
			
			if (_world != null && _playerController != null && _dayNightCycle != null)
			{
				SaveManager.save(_world, _playerController, _dayNightCycle);
				System.out.println("PlayingState.destroyWorld: Saved after emergency arena exit.");
			}
		}
		
		// Reset paused flag.
		_paused = false;
		_pendingSpawn = false;
		_pendingArenaEntry = false;
		_playerDead = false;
		_dragonDeathProcessed = false;
		_newWorldSpawn = false;
		_waitingForGround = false;
		
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
		
		// Clean up boss arena manager.
		if (_bossArenaManager != null)
		{
			_bossArenaManager.cleanup();
			_bossArenaManager = null;
		}
		
		// Clean up drop manager.
		if (_dropManager != null)
		{
			_dropManager.cleanup();
			app.getRootNode().detachChild(_dropManager.getNode());
			_dropManager = null;
		}
		
		// Clean up particle manager.
		if (_particleManager != null)
		{
			_particleManager.cleanup();
			app.getRootNode().detachChild(_particleManager.getNode());
			EnemyAI.setParticleManager(null);
			_particleManager = null;
		}
		
		// Clean up viewmodel renderer.
		removeHeldTorchLight();
		if (_viewmodelRenderer != null)
		{
			_viewmodelRenderer.cleanup();
			_viewmodelRenderer = null;
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
		_atlasMaterial = null;
		
		// Clear cached item textures so they are reloaded for the next world.
		ItemTextureResolver.clearCache();
		_activeWorld = null;
		_playerSaveData = null;
		_tileEntitySaveData = null;
		_inventorySaveData = null;
		
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
