package simplecraft.input;

import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.input.controls.MouseButtonTrigger;

/**
 * Manages all game input mappings.<br>
 * Centralizes action names and input bindings for keyboard and mouse.<br>
 * Controller support will be added later.
 * @author Pantelis Andrianakis
 * @since February 16th 2026
 */
public class GameInputManager
{
	// Movement actions.
	public static final String MOVE_FORWARD = "move_forward";
	public static final String MOVE_BACK = "move_back";
	public static final String MOVE_LEFT = "move_left";
	public static final String MOVE_RIGHT = "move_right";
	public static final String JUMP = "jump";
	
	// Look actions (analog mouse axes).
	public static final String LOOK_LEFT = "look_left";
	public static final String LOOK_RIGHT = "look_right";
	public static final String LOOK_UP = "look_up";
	public static final String LOOK_DOWN = "look_down";
	
	// Game actions.
	public static final String ATTACK = "attack";
	public static final String PLACE_BLOCK = "place_block";
	public static final String NEXT_BLOCK = "next_block";
	public static final String PREV_BLOCK = "prev_block";
	
	// UI actions.
	public static final String PAUSE = "pause";
	public static final String UI_UP = "ui_up";
	public static final String UI_DOWN = "ui_down";
	public static final String UI_CONFIRM = "ui_confirm";
	public static final String UI_BACK = "ui_back";
	
	private final InputManager _inputManager;
	
	public GameInputManager(InputManager inputManager)
	{
		_inputManager = inputManager;
		
		// Clear any default jME3 mappings to avoid conflicts.
		clearDefaultMappings();
		
		// Setup our custom mappings.
		setupKeyboardMappings();
		setupMouseMappings();
		
		System.out.println("GameInputManager initialized with keyboard and mouse mappings.");
	}
	
	/**
	 * Clear jME3's default input mappings to prevent conflicts.
	 */
	private void clearDefaultMappings()
	{
		// Remove common default mappings that jME3 might have set.
		final String[] defaultMappings =
		{
			"FLYCAM_Left",
			"FLYCAM_Right",
			"FLYCAM_Up",
			"FLYCAM_Down",
			"FLYCAM_StrafeLeft",
			"FLYCAM_StrafeRight",
			"FLYCAM_Forward",
			"FLYCAM_Backward",
			"FLYCAM_ZoomIn",
			"FLYCAM_ZoomOut",
			"FLYCAM_RotateDrag",
			"FLYCAM_Rise",
			"FLYCAM_Lower",
			"SIMPLEAPP_Exit",
			"SIMPLEAPP_CameraPos",
			"SIMPLEAPP_Memory",
			"SIMPLEAPP_HideStats",
			"SIMPLEAPP_Profiler"
		};
		
		for (String mapping : defaultMappings)
		{
			if (_inputManager.hasMapping(mapping))
			{
				_inputManager.deleteMapping(mapping);
			}
		}
	}
	
	/**
	 * Setup keyboard input mappings.
	 */
	private void setupKeyboardMappings()
	{
		// Movement keys (WASD).
		_inputManager.addMapping(MOVE_FORWARD, new KeyTrigger(KeyInput.KEY_W));
		_inputManager.addMapping(MOVE_BACK, new KeyTrigger(KeyInput.KEY_S));
		_inputManager.addMapping(MOVE_LEFT, new KeyTrigger(KeyInput.KEY_A));
		_inputManager.addMapping(MOVE_RIGHT, new KeyTrigger(KeyInput.KEY_D));
		_inputManager.addMapping(JUMP, new KeyTrigger(KeyInput.KEY_SPACE));
		
		// UI navigation keys.
		_inputManager.addMapping(UI_UP, new KeyTrigger(KeyInput.KEY_UP));
		_inputManager.addMapping(UI_DOWN, new KeyTrigger(KeyInput.KEY_DOWN));
		_inputManager.addMapping(UI_CONFIRM, new KeyTrigger(KeyInput.KEY_RETURN));
		_inputManager.addMapping(UI_BACK, new KeyTrigger(KeyInput.KEY_ESCAPE));
		
		// Pause.
		_inputManager.addMapping(PAUSE, new KeyTrigger(KeyInput.KEY_ESCAPE));
		
		// Block selection (number keys for direct selection could be added later).
		_inputManager.addMapping(PREV_BLOCK, new KeyTrigger(KeyInput.KEY_Q));
		_inputManager.addMapping(NEXT_BLOCK, new KeyTrigger(KeyInput.KEY_E));
	}
	
	/**
	 * Setup mouse input mappings.
	 */
	private void setupMouseMappings()
	{
		// Mouse buttons.
		_inputManager.addMapping(ATTACK, new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
		_inputManager.addMapping(PLACE_BLOCK, new MouseButtonTrigger(MouseInput.BUTTON_RIGHT));
		
		// Mouse wheel for block selection.
		_inputManager.addMapping(NEXT_BLOCK, new MouseAxisTrigger(MouseInput.AXIS_WHEEL, false));
		_inputManager.addMapping(PREV_BLOCK, new MouseAxisTrigger(MouseInput.AXIS_WHEEL, true));
		
		// Mouse axes for looking (analog input).
		_inputManager.addMapping(LOOK_LEFT, new MouseAxisTrigger(MouseInput.AXIS_X, true));
		_inputManager.addMapping(LOOK_RIGHT, new MouseAxisTrigger(MouseInput.AXIS_X, false));
		_inputManager.addMapping(LOOK_UP, new MouseAxisTrigger(MouseInput.AXIS_Y, true));
		_inputManager.addMapping(LOOK_DOWN, new MouseAxisTrigger(MouseInput.AXIS_Y, false));
	}
	
	/**
	 * Get the underlying jME3 InputManager for advanced usage.
	 * @return The jME3 InputManager instance
	 */
	public InputManager getInputManager()
	{
		return _inputManager;
	}
}
