package simplecraft;

import com.jme3.app.SimpleApplication;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;

/**
 * SimpleCraft — A simple offline voxel game.
 * Main entry point.
 */
public class SimpleCraft extends SimpleApplication
{
	public static void main(String[] args)
	{
		final SimpleCraft app = new SimpleCraft();

		final AppSettings settings = new AppSettings(true);
		settings.setTitle("SimpleCraft");
		settings.setWidth(1280);
		settings.setHeight(720);
		settings.setVSync(true);

		app.setSettings(settings);
		app.setShowSettings(false);
		
		// Use JME context display mode for better window control.
		app.start(JmeContext.Type.Display);
	}

	@Override
	public void simpleInitApp()
	{
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
