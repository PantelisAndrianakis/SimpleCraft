package simplecraft.settings;

import com.jme3.input.InputManager;
import com.jme3.input.RawInputListener;
import com.jme3.input.event.JoyAxisEvent;
import com.jme3.input.event.JoyButtonEvent;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.input.event.TouchEvent;
import com.jme3.renderer.Camera;

import simplecraft.SimpleCraft;

/**
 * Scales menu cursor speed by replaying mouse motion events with a sensitivity multiplier.<br>
 * Enabled only in menu states so gameplay mouse look remains unchanged.
 * @author Pantelis Andrianakis
 * @since March 27th 2026
 */
public final class MouseSensitivityManager
{
	/** Sensitivity value that maps to unmodified system cursor speed. */
	private static final float BASE_SENSITIVITY = 1.0f;
	
	private static InputManager _inputManager;
	private static RawInputListener _listener;
	private static boolean _enabled;
	private static boolean _replaying;
	
	private MouseSensitivityManager()
	{
	}
	
	/**
	 * Initialize and register the raw input listener once.
	 */
	public static void initialize(InputManager inputManager)
	{
		if (_listener != null)
		{
			return;
		}
		
		_inputManager = inputManager;
		_listener = new RawInputListener()
		{
			@Override
			public void onMouseMotionEvent(MouseMotionEvent event)
			{
				if (!_enabled || _replaying || _inputManager == null)
				{
					return;
				}
				
				final float sensitivity = SimpleCraft.getInstance().getSettingsManager().getMouseSensitivity();
				final float multiplier = Math.max(0.05f, sensitivity / BASE_SENSITIVITY);
				if (Math.abs(multiplier - 1f) < 0.0001f)
				{
					return;
				}
				
				final int dx = event.getDX();
				final int dy = event.getDY();
				final int scaledDx = Math.round(dx * multiplier);
				final int scaledDy = Math.round(dy * multiplier);
				if (scaledDx == dx && scaledDy == dy)
				{
					return;
				}
				
				final SimpleCraft app = SimpleCraft.getInstance();
				final Camera camera = app.getCamera();
				final int maxX = Math.max(1, camera.getWidth() - 1);
				final int maxY = Math.max(1, camera.getHeight() - 1);
				
				// Scale from the event's previous position, not the current cursor state, to avoid double-applying motion.
				final int prevX = event.getX() - dx;
				final int prevY = event.getY() - dy;
				final int scaledX = Math.clamp(prevX + scaledDx, 0, maxX);
				final int scaledY = Math.clamp(prevY + scaledDy, 0, maxY);
				
				event.setConsumed();
				
				_replaying = true;
				try
				{
					final MouseMotionEvent scaled = new MouseMotionEvent(scaledX, scaledY, scaledDx, scaledDy, event.getWheel(), event.getDeltaWheel());
					_inputManager.onMouseMotionEvent(scaled);
				}
				finally
				{
					_replaying = false;
				}
			}
			
			@Override
			public void beginInput()
			{
			}
			
			@Override
			public void endInput()
			{
			}
			
			@Override
			public void onJoyAxisEvent(JoyAxisEvent evt)
			{
			}
			
			@Override
			public void onJoyButtonEvent(JoyButtonEvent evt)
			{
			}
			
			@Override
			public void onMouseButtonEvent(MouseButtonEvent evt)
			{
			}
			
			@Override
			public void onKeyEvent(KeyInputEvent evt)
			{
			}
			
			@Override
			public void onTouchEvent(TouchEvent evt)
			{
			}
		};
		
		_inputManager.addRawInputListener(_listener);
	}
	
	/**
	 * Enable or disable menu cursor scaling.
	 */
	public static void setEnabled(boolean enabled)
	{
		_enabled = enabled;
	}
}
