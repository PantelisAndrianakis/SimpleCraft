package simplecraft;

import com.jme3.app.SimpleApplication;
import com.jme3.math.ColorRGBA;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;
import com.simsilica.lemur.GuiGlobals;

import simplecraft.audio.AudioManager;
import simplecraft.input.GameInputManager;
import simplecraft.state.GameStateManager;

/**
 * SimpleCraft — A simple offline voxel game.<br>
 * Main entry point.
 */
public class SimpleCraft extends SimpleApplication
{
	// Core managers.
	private GameStateManager _gameStateManager;
	private AudioManager _audioManager;
	private GameInputManager _gameInputManager;
	
	public static void main(String[] args)
	{
		final SimpleCraft simpleCraft = getInstance();
		
		final AppSettings settings = new AppSettings(true);
		settings.setTitle("SimpleCraft");
		settings.setWidth(1280);
		settings.setHeight(720);
		settings.setVSync(true);
		simpleCraft.setSettings(settings);
		simpleCraft.setShowSettings(false);
		
		// Use JME context display mode for better window control.
		simpleCraft.start(JmeContext.Type.Display);
	}
	
	@Override
	public void simpleInitApp()
	{
		// Initialize Lemur UI system.
		GuiGlobals.initialize(this);
		
		// Set background to a dark color so we know it's running.
		final ColorRGBA backgroundColor = new ColorRGBA(0.1f, 0.1f, 0.2f, 1.0f);
		viewPort.setBackgroundColor(backgroundColor);
		
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
		
		// Switch to initial splash state (will log warning - expected).
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
