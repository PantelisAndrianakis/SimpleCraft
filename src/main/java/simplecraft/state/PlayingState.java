package simplecraft.state;

import com.jme3.app.Application;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;

import simplecraft.SimpleCraft;

/**
 * Playing state - the main game scene.<br>
 * Currently an empty scene with sky background and basic lighting.
 * @author Pantelis Andrianakis
 * @since February 18th 2026
 */
public class PlayingState extends FadeableAppState
{
	private DirectionalLight _sun;
	private AmbientLight _ambient;
	
	@Override
	protected void initialize(Application app)
	{
		// Initialization happens once when state is first attached.
	}
	
	@Override
	protected void cleanup(Application app)
	{
		// Cleanup happens once when state is permanently detached.
	}
	
	@Override
	protected void onEnterState()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		
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
		
		// Disable cursor for first-person control.
		app.getInputManager().setCursorVisible(false);
	}
	
	@Override
	protected void onExitState()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		
		// Re-enable cursor when leaving game.
		app.getInputManager().setCursorVisible(true);
		
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
	}
	
	@Override
	public void update(float tpf)
	{
		// Game logic updates will go here in future sessions.
	}
}
