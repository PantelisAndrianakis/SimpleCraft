package simplecraft;

import java.io.File;

import com.jme3.app.SimpleApplication;
import com.jme3.math.ColorRGBA;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;
import com.simsilica.lemur.GuiGlobals;

import simplecraft.audio.AudioManager;
import simplecraft.input.GameInputManager;
import simplecraft.state.GameStateManager;

/**
 * SimpleCraft - A simple voxel game built in Java.<br>
 * Main entry point.
 * @author Pantelis Andrianakis
 * @since February 14th 2026
 */
public class SimpleCraft extends SimpleApplication
{
	// Core managers.
	private GameStateManager _gameStateManager;
	private AudioManager _audioManager;
	private GameInputManager _gameInputManager;
	
	public static void main(String[] args)
	{
		final SimpleCraft app = getInstance();
		
		final AppSettings settings = new AppSettings(true);
		settings.setTitle("SimpleCraft");
		settings.setWidth(1280);
		settings.setHeight(720);
		settings.setVSync(true);
		
		// Try to set initial framebuffer clear color (may help with white flash).
		settings.setFrameRate(60);
		settings.setGammaCorrection(false);
		settings.setSamples(0);
		
		app.setSettings(settings);
		app.setShowSettings(false);
		
		// Use JME context display mode for better window control.
		app.start(JmeContext.Type.Display);
	}
	
	@Override
	public void simpleInitApp()
	{
		// Set a dark gray background during initialization to minimize white flash.
		// This will be visible for a very short time before the splash screen takes over.
		viewPort.setBackgroundColor(new ColorRGBA(0.05f, 0.05f, 0.05f, 1.0f));
		
		// Initialize Lemur UI system.
		GuiGlobals.initialize(this);
		
		// Register the current working directory as an asset locator.
		final String workingDir = System.getProperty("user.dir");
		System.out.println("Working directory: " + workingDir);
		
		// Register both file and classpath locators for the current directory.
		assetManager.registerLocator(workingDir, com.jme3.asset.plugins.FileLocator.class);
		
		// Also register the assets folder directly.
		final String assetsPath = workingDir + File.separator + "assets";
		System.out.println("Registering assets path: " + assetsPath);
		assetManager.registerLocator(assetsPath, com.jme3.asset.plugins.FileLocator.class);
		
		// Disable default flyCam to avoid input conflicts.
		flyCam.setEnabled(false);
		
		// Initialize core managers in order.
		System.out.println("Initializing core managers...");
		
		// 1. Input Manager (sets up all input mappings).
		_gameInputManager = new GameInputManager(inputManager);
		
		// 2. Audio Manager (handles music and SFX).
		_audioManager = new AudioManager(assetManager);
		
		// 3. Game State Manager (manages state transitions).
		_gameStateManager = new GameStateManager(stateManager);
		
		// Register game states.
		_gameStateManager.registerState(GameStateManager.GameState.SPLASH, new simplecraft.state.SplashState());
		_gameStateManager.registerState(GameStateManager.GameState.MAIN_MENU, new simplecraft.state.MainMenuState());
		
		// Switch to initial splash state.
		_gameStateManager.switchTo(GameStateManager.GameState.SPLASH);
		
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
