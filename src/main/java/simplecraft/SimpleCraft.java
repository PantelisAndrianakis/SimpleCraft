package simplecraft;

import java.io.File;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext.Type;
import com.simsilica.lemur.GuiGlobals;

import simplecraft.audio.AudioManager;
import simplecraft.input.GameInputManager;
import simplecraft.settings.SettingsManager;
import simplecraft.settings.WindowDisplayManager;
import simplecraft.settings.WindowIconManager;
import simplecraft.state.GameStateManager;
import simplecraft.state.GameStateManager.GameState;
import simplecraft.state.IntroState;
import simplecraft.state.MainMenuState;
import simplecraft.state.OptionsState;
import simplecraft.state.PlayingState;
import simplecraft.ui.CursorManager;

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
		
		// Initialize core managers in order.
		System.out.println("Initializing core managers...");
		
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
		_gameStateManager.registerState(GameState.OPTIONS, new OptionsState());
		_gameStateManager.registerState(GameState.PLAYING, new PlayingState());
		
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
	
	public static SimpleCraft getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final SimpleCraft INSTANCE = new SimpleCraft();
	}
}
