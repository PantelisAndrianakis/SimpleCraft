package simplecraft;

import java.io.File;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext.Type;

import com.simsilica.lemur.GuiGlobals;

import simplecraft.audio.AudioManager;
import simplecraft.input.GameInputManager;
import simplecraft.item.CraftingRegistry;
import simplecraft.item.ItemRegistry;
import simplecraft.item.SmeltingRegistry;
import simplecraft.settings.DiscordRichPresenceManager;
import simplecraft.settings.LanguageManager;
import simplecraft.settings.MouseSensitivityManager;
import simplecraft.settings.SettingsManager;
import simplecraft.settings.WindowDisplayManager;
import simplecraft.settings.WindowIconManager;
import simplecraft.state.GameStateManager;
import simplecraft.state.GameStateManager.GameState;
import simplecraft.state.IntroState;
import simplecraft.state.MainMenuState;
import simplecraft.state.OptionsState;
import simplecraft.state.PauseMenuState;
import simplecraft.state.PlayingState;
import simplecraft.state.WorldSelectState;
import simplecraft.ui.CursorManager;
import simplecraft.ui.FontManager;
import simplecraft.ui.MessageManager;
import simplecraft.world.WorldInfo;

/**
 * SimpleCraft - A simple voxel game built in Java.<br>
 * Main entry point.
 * @author Pantelis Andrianakis
 * @since February 14th 2026
 */
public class SimpleCraft extends SimpleApplication
{
	// Core managers.
	private SettingsManager _settingsManager;
	private GameStateManager _gameStateManager;
	private AudioManager _audioManager;
	private GameInputManager _gameInputManager;
	private DiscordRichPresenceManager _discordPresence;
	
	// Active world (set when entering a world from WorldSelectState).
	private WorldInfo _activeWorld;
	
	public static void main(String[] args)
	{
		final SimpleCraft app = getInstance();
		
		// Load settings early for display configuration.
		app._settingsManager = new SettingsManager();
		app._settingsManager.load();
		
		final AppSettings settings = new AppSettings(true);
		settings.setTitle("SimpleCraft");
		settings.setWidth(app._settingsManager.getScreenWidth());
		settings.setHeight(app._settingsManager.getScreenHeight());
		settings.setFullscreen(false); // Always start windowed; borderless fullscreen is applied post-init.
		settings.setVSync(true);
		
		// Try to set initial framebuffer clear color (may help with white flash).
		settings.setFrameRate(SettingsManager.getNativeRefreshRate());
		settings.setGammaCorrection(false);
		settings.setSamples(0);
		
		// Set window icons (taskbar + title bar).
		WindowIconManager.applyIcons(settings);
		
		app.setSettings(settings);
		app.setShowSettings(false);
		
		// Use JME context display mode for better window control.
		app.start(Type.Display);
	}
	
	@Override
	public void simpleInitApp()
	{
		// Initialize Lemur UI system.
		GuiGlobals.initialize(this);
		
		// Apply debug display settings from persisted settings.
		setDisplayStatView(_settingsManager.isShowStats());
		setDisplayFps(_settingsManager.isShowFps());
		
		// Register the current working directory as an asset locator.
		final String workingDir = System.getProperty("user.dir");
		System.out.println("Working directory: " + workingDir);
		
		// Register both file and classpath locators for the current directory.
		assetManager.registerLocator(workingDir, FileLocator.class);
		
		// Also register the assets folder directly.
		final String assetsPath = workingDir + File.separator + "assets";
		System.out.println("Registering assets path: " + assetsPath);
		assetManager.registerLocator(assetsPath, FileLocator.class);
		
		// Disable default flyCam to avoid input conflicts.
		flyCam.setEnabled(false);
		
		// Initialize cursor manager to set custom cursor.
		CursorManager.initialize(inputManager);
		MouseSensitivityManager.initialize(inputManager);
		
		// Initialize core managers in order.
		System.out.println("Initializing core managers...");
		
		// Language Manager (discovers and loads the saved language).
		LanguageManager.discoverLanguages();
		LanguageManager.loadLanguage(_settingsManager.getLanguage());
		
		// Pre-warm dialog fonts so first-use does not stall the render thread.
		FontManager.warmup(assetManager, cam.getHeight());
		
		// Input Manager (sets up all input mappings).
		_gameInputManager = new GameInputManager(inputManager);
		
		// Audio Manager (handles music and SFX).
		_audioManager = new AudioManager(assetManager);
		
		// Apply saved volume settings to Audio Manager.
		_audioManager.setMasterVolume(_settingsManager.getMasterVolume());
		_audioManager.setMusicVolume(_settingsManager.getMusicVolume());
		_audioManager.setSfxVolume(_settingsManager.getSfxVolume());
		
		// Game State Manager (manages state transitions).
		_gameStateManager = new GameStateManager(stateManager);
		
		// Register game states.
		_gameStateManager.registerState(GameState.INTRO, new IntroState());
		_gameStateManager.registerState(GameState.MAIN_MENU, new MainMenuState());
		_gameStateManager.registerState(GameState.WORLD_SELECT, new WorldSelectState());
		_gameStateManager.registerState(GameState.OPTIONS, new OptionsState());
		_gameStateManager.registerState(GameState.PLAYING, new PlayingState());
		_gameStateManager.registerState(GameState.PAUSED, new PauseMenuState());
		
		// Register all items in the item catalog.
		ItemRegistry.registerDefaults();
		
		// Register all crafting recipes.
		CraftingRegistry.registerDefaults();
		
		// Register all smelting recipes.
		SmeltingRegistry.registerDefaults();
		
		// Discord Rich Presence (connects on background thread, fails silently if Discord is not running).
		_discordPresence = new DiscordRichPresenceManager();
		_discordPresence.connect();
		
		// Switch to initial splash state.
		_gameStateManager.switchTo(GameState.INTRO);
		
		// If fullscreen is enabled, apply borderless windowed fullscreen now that the window exists.
		if (_settingsManager.isFullscreen())
		{
			WindowDisplayManager.applyBorderlessFullscreen();
		}
		
		System.out.println("SimpleCraft started successfully!");
	}
	
	@Override
	public void simpleUpdate(float tpf)
	{
		// Update audio manager for crossfade interpolation.
		if (_audioManager != null)
		{
			_audioManager.update(tpf);
		}
		
		// Update message toast timer for auto-dismiss.
		MessageManager.update(tpf);
	}
	
	@Override
	public void destroy()
	{
		// Disconnect Discord Rich Presence cleanly.
		if (_discordPresence != null)
		{
			_discordPresence.disconnect();
		}
		
		super.destroy();
	}
	
	@Override
	public void loseFocus()
	{
		// Override to prevent not rendering when app is out of focus.
		// Don't call super.loseFocus().
	}
	
	@Override
	public void gainFocus()
	{
		// Override to prevent not rendering when app is out of focus.
		// Don't call super.gainFocus().
	}
	
	public SettingsManager getSettingsManager()
	{
		return _settingsManager;
	}
	
	public GameStateManager getGameStateManager()
	{
		return _gameStateManager;
	}
	
	public AudioManager getAudioManager()
	{
		return _audioManager;
	}
	
	public GameInputManager getGameInputManager()
	{
		return _gameInputManager;
	}
	
	public DiscordRichPresenceManager getDiscordPresence()
	{
		return _discordPresence;
	}
	
	/**
	 * Get the currently active world (set when entering a world from WorldSelectState).
	 * @return The active WorldInfo, or null if no world is active
	 */
	public WorldInfo getActiveWorld()
	{
		return _activeWorld;
	}
	
	/**
	 * Set the active world. Called by WorldSelectState when entering a world and cleared by PlayingState when exiting the game session.
	 * @param activeWorld The world to set as active, or null to clear
	 */
	public void setActiveWorld(WorldInfo activeWorld)
	{
		_activeWorld = activeWorld;
	}
	
	public static SimpleCraft getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final SimpleCraft INSTANCE = new SimpleCraft();
	}
}
