package simplecraft;

import com.jme3.app.SimpleApplication;
import com.jme3.math.ColorRGBA;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;
import com.simsilica.lemur.GuiGlobals;

/**
 * SimpleCraft — A simple offline voxel game.<br>
 * Main entry point.
 */
public class SimpleCraft extends SimpleApplication
{
	public static SimpleCraft getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final SimpleCraft INSTANCE = new SimpleCraft();
	}
	
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
		
		// TODO: Initialize game state manager, audio manager, input manager.
		// TODO: Attach SplashState as first state.
		System.out.println("SimpleCraft started successfully!");
	}
	
	@Override
	public void simpleUpdate(float tpf)
	{
		// TODO: Game loop updates.
	}
}
