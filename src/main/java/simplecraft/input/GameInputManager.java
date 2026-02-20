package simplecraft.input;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.input.controls.MouseButtonTrigger;

import simplecraft.SimpleCraft;

/**
 * Centralizes action names and input bindings for keyboard and mouse.<br>
 * Keyboard bindings are rebindable at runtime and persisted via SettingsManager.<br>
 * Mouse bindings and UI navigation keys are fixed.<br>
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
	public static final String SWIM_DOWN = "swim_down";
	
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
	public static final String INVENTORY = "inventory";
	
	// UI actions.
	public static final String PAUSE = "pause";
	public static final String UI_UP = "ui_up";
	public static final String UI_DOWN = "ui_down";
	public static final String UI_LEFT = "ui_left";
	public static final String UI_RIGHT = "ui_right";
	public static final String UI_CONFIRM = "ui_confirm";
	public static final String UI_BACK = "ui_back";
	
	/**
	 * Ordered list of rebindable keyboard actions for the options UI.<br>
	 * Each entry: {action constant, display name, section name}.
	 */
	public static final String[][] BINDABLE_ACTIONS =
	{
		// @formatter:off
		{MOVE_FORWARD, "Move Forward", "Movement"},
		{MOVE_BACK, "Move Back", "Movement"},
		{MOVE_LEFT, "Move Left", "Movement"},
		{MOVE_RIGHT, "Move Right", "Movement"},
		{JUMP, "Jump / Swim Up", "Movement"},
		{SWIM_DOWN, "Swim Down / Crouch", "Movement"},
		{NEXT_BLOCK, "Next Block", "Actions"},
		{PREV_BLOCK, "Prev Block", "Actions"},
		{INVENTORY, "Inventory", "Actions"},
		// @formatter:on
	};
	
	/**
	 * Read-only entries shown in the keybindings UI but not rebindable.<br>
	 * Each entry: {display name, key display text}.
	 */
	public static final String[][] FIXED_BINDINGS =
	{
		// @formatter:off
		{"Block Scroll", "Scroll Wheel"},
		{"Look Around", "Mouse Move"},
		{"Pause / Menu", "Escape"},
		// @formatter:on
	};
	
	/**
	 * Mouse button actions that can be reassigned between Left/Right/Middle click.<br>
	 * Each entry: {action constant, display name}.
	 */
	public static final String[][] MOUSE_BINDABLE_ACTIONS =
	{
		// @formatter:off
		{ATTACK, "Attack"},
		{PLACE_BLOCK, "Place / Interact"},
		// @formatter:on
	};
	
	// Default key codes for all rebindable actions.
	private static final Map<String, Integer> DEFAULT_KEY_CODES;
	static
	{
		final LinkedHashMap<String, Integer> defaults = new LinkedHashMap<>();
		defaults.put(MOVE_FORWARD, KeyInput.KEY_W);
		defaults.put(MOVE_BACK, KeyInput.KEY_S);
		defaults.put(MOVE_LEFT, KeyInput.KEY_A);
		defaults.put(MOVE_RIGHT, KeyInput.KEY_D);
		defaults.put(JUMP, KeyInput.KEY_SPACE);
		defaults.put(SWIM_DOWN, KeyInput.KEY_LSHIFT);
		defaults.put(NEXT_BLOCK, KeyInput.KEY_E);
		defaults.put(PREV_BLOCK, KeyInput.KEY_Q);
		defaults.put(INVENTORY, KeyInput.KEY_TAB);
		DEFAULT_KEY_CODES = Collections.unmodifiableMap(defaults);
	}
	
	// Default mouse button codes for mouse-rebindable actions.
	private static final Map<String, Integer> DEFAULT_MOUSE_CODES;
	static
	{
		final LinkedHashMap<String, Integer> defaults = new LinkedHashMap<>();
		defaults.put(ATTACK, MouseInput.BUTTON_LEFT);
		defaults.put(PLACE_BLOCK, MouseInput.BUTTON_RIGHT);
		DEFAULT_MOUSE_CODES = Collections.unmodifiableMap(defaults);
	}
	
	// Mouse button display names.
	private static final Map<Integer, String> MOUSE_BUTTON_NAMES;
	static
	{
		final Map<Integer, String> names = new LinkedHashMap<>();
		names.put(MouseInput.BUTTON_LEFT, "Left Click");
		names.put(MouseInput.BUTTON_RIGHT, "Right Click");
		names.put(MouseInput.BUTTON_MIDDLE, "Middle Click");
		MOUSE_BUTTON_NAMES = Collections.unmodifiableMap(names);
	}
	
	// Key name display lookup.
	private static final Map<Integer, String> KEY_NAMES;
	static
	{
		final Map<Integer, String> names = new LinkedHashMap<>();
		// Letters.
		names.put(KeyInput.KEY_A, "A");
		names.put(KeyInput.KEY_B, "B");
		names.put(KeyInput.KEY_C, "C");
		names.put(KeyInput.KEY_D, "D");
		names.put(KeyInput.KEY_E, "E");
		names.put(KeyInput.KEY_F, "F");
		names.put(KeyInput.KEY_G, "G");
		names.put(KeyInput.KEY_H, "H");
		names.put(KeyInput.KEY_I, "I");
		names.put(KeyInput.KEY_J, "J");
		names.put(KeyInput.KEY_K, "K");
		names.put(KeyInput.KEY_L, "L");
		names.put(KeyInput.KEY_M, "M");
		names.put(KeyInput.KEY_N, "N");
		names.put(KeyInput.KEY_O, "O");
		names.put(KeyInput.KEY_P, "P");
		names.put(KeyInput.KEY_Q, "Q");
		names.put(KeyInput.KEY_R, "R");
		names.put(KeyInput.KEY_S, "S");
		names.put(KeyInput.KEY_T, "T");
		names.put(KeyInput.KEY_U, "U");
		names.put(KeyInput.KEY_V, "V");
		names.put(KeyInput.KEY_W, "W");
		names.put(KeyInput.KEY_X, "X");
		names.put(KeyInput.KEY_Y, "Y");
		names.put(KeyInput.KEY_Z, "Z");
		// Numbers.
		names.put(KeyInput.KEY_0, "0");
		names.put(KeyInput.KEY_1, "1");
		names.put(KeyInput.KEY_2, "2");
		names.put(KeyInput.KEY_3, "3");
		names.put(KeyInput.KEY_4, "4");
		names.put(KeyInput.KEY_5, "5");
		names.put(KeyInput.KEY_6, "6");
		names.put(KeyInput.KEY_7, "7");
		names.put(KeyInput.KEY_8, "8");
		names.put(KeyInput.KEY_9, "9");
		// Function keys.
		names.put(KeyInput.KEY_F1, "F1");
		names.put(KeyInput.KEY_F2, "F2");
		names.put(KeyInput.KEY_F3, "F3");
		names.put(KeyInput.KEY_F4, "F4");
		names.put(KeyInput.KEY_F5, "F5");
		names.put(KeyInput.KEY_F6, "F6");
		names.put(KeyInput.KEY_F7, "F7");
		names.put(KeyInput.KEY_F8, "F8");
		names.put(KeyInput.KEY_F9, "F9");
		names.put(KeyInput.KEY_F10, "F10");
		names.put(KeyInput.KEY_F11, "F11");
		names.put(KeyInput.KEY_F12, "F12");
		// Modifiers and special keys.
		names.put(KeyInput.KEY_SPACE, "Space");
		names.put(KeyInput.KEY_LSHIFT, "L.Shift");
		names.put(KeyInput.KEY_RSHIFT, "R.Shift");
		names.put(KeyInput.KEY_LCONTROL, "L.Ctrl");
		names.put(KeyInput.KEY_RCONTROL, "R.Ctrl");
		names.put(KeyInput.KEY_LMENU, "L.Alt");
		names.put(KeyInput.KEY_RMENU, "R.Alt");
		names.put(KeyInput.KEY_TAB, "Tab");
		names.put(KeyInput.KEY_RETURN, "Enter");
		names.put(KeyInput.KEY_BACK, "Backspace");
		names.put(KeyInput.KEY_DELETE, "Delete");
		names.put(KeyInput.KEY_INSERT, "Insert");
		names.put(KeyInput.KEY_HOME, "Home");
		names.put(KeyInput.KEY_END, "End");
		names.put(KeyInput.KEY_PGUP, "Page Up");
		names.put(KeyInput.KEY_PGDN, "Page Down");
		names.put(KeyInput.KEY_UP, "Up");
		names.put(KeyInput.KEY_DOWN, "Down");
		names.put(KeyInput.KEY_LEFT, "Left");
		names.put(KeyInput.KEY_RIGHT, "Right");
		// Punctuation and misc.
		names.put(KeyInput.KEY_GRAVE, "`");
		names.put(KeyInput.KEY_MINUS, "-");
		names.put(KeyInput.KEY_EQUALS, "=");
		names.put(KeyInput.KEY_LBRACKET, "[");
		names.put(KeyInput.KEY_RBRACKET, "]");
		names.put(KeyInput.KEY_BACKSLASH, "\\");
		names.put(KeyInput.KEY_SEMICOLON, ";");
		names.put(KeyInput.KEY_APOSTROPHE, "'");
		names.put(KeyInput.KEY_COMMA, ",");
		names.put(KeyInput.KEY_PERIOD, ".");
		names.put(KeyInput.KEY_SLASH, "/");
		// Numpad.
		names.put(KeyInput.KEY_NUMPAD0, "Num 0");
		names.put(KeyInput.KEY_NUMPAD1, "Num 1");
		names.put(KeyInput.KEY_NUMPAD2, "Num 2");
		names.put(KeyInput.KEY_NUMPAD3, "Num 3");
		names.put(KeyInput.KEY_NUMPAD4, "Num 4");
		names.put(KeyInput.KEY_NUMPAD5, "Num 5");
		names.put(KeyInput.KEY_NUMPAD6, "Num 6");
		names.put(KeyInput.KEY_NUMPAD7, "Num 7");
		names.put(KeyInput.KEY_NUMPAD8, "Num 8");
		names.put(KeyInput.KEY_NUMPAD9, "Num 9");
		KEY_NAMES = Collections.unmodifiableMap(names);
	}
	
	// Current key codes (mutable, starts from defaults then overridden by saved settings).
	private final Map<String, Integer> _currentKeyCodes = new LinkedHashMap<>();
	
	// Current mouse button codes (mutable, starts from defaults then overridden by saved settings).
	private final Map<String, Integer> _currentMouseCodes = new LinkedHashMap<>();
	
	private final InputManager _inputManager;
	
	public GameInputManager(InputManager inputManager)
	{
		_inputManager = inputManager;
		
		// Initialize current bindings from defaults.
		_currentKeyCodes.putAll(DEFAULT_KEY_CODES);
		_currentMouseCodes.putAll(DEFAULT_MOUSE_CODES);
		
		// Apply any saved overrides from SettingsManager.
		final Map<String, Integer> savedKeys = SimpleCraft.getInstance().getSettingsManager().getKeybindings();
		for (Entry<String, Integer> entry : savedKeys.entrySet())
		{
			if (DEFAULT_KEY_CODES.containsKey(entry.getKey()))
			{
				_currentKeyCodes.put(entry.getKey(), entry.getValue());
			}
		}
		
		final Map<String, Integer> savedMouse = SimpleCraft.getInstance().getSettingsManager().getMouseBindings();
		for (Entry<String, Integer> entry : savedMouse.entrySet())
		{
			if (DEFAULT_MOUSE_CODES.containsKey(entry.getKey()))
			{
				_currentMouseCodes.put(entry.getKey(), entry.getValue());
			}
		}
		
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
		final String[] defaultMappings =
		{
			// @formatter:off
			"FLYCAM_Left", "FLYCAM_Right", "FLYCAM_Up", "FLYCAM_Down",
			"FLYCAM_StrafeLeft", "FLYCAM_StrafeRight", "FLYCAM_Forward", "FLYCAM_Backward",
			"FLYCAM_ZoomIn", "FLYCAM_ZoomOut", "FLYCAM_RotateDrag",
			"FLYCAM_Rise", "FLYCAM_Lower",
			"SIMPLEAPP_Exit", "SIMPLEAPP_CameraPos", "SIMPLEAPP_Memory",
			"SIMPLEAPP_HideStats", "SIMPLEAPP_Profiler"
			// @formatter:on
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
	 * Setup keyboard input mappings using current (possibly remapped) key codes.
	 */
	private void setupKeyboardMappings()
	{
		// Rebindable actions from current key codes.
		for (Entry<String, Integer> entry : _currentKeyCodes.entrySet())
		{
			_inputManager.addMapping(entry.getKey(), new KeyTrigger(entry.getValue()));
		}
		
		// Fixed UI navigation keys (not rebindable).
		// Arrow keys.
		_inputManager.addMapping(UI_UP, new KeyTrigger(KeyInput.KEY_UP));
		_inputManager.addMapping(UI_DOWN, new KeyTrigger(KeyInput.KEY_DOWN));
		_inputManager.addMapping(UI_LEFT, new KeyTrigger(KeyInput.KEY_LEFT));
		_inputManager.addMapping(UI_RIGHT, new KeyTrigger(KeyInput.KEY_RIGHT));
		// WASD as secondary UI navigation (unaffected by key rebinding).
		_inputManager.addMapping(UI_UP, new KeyTrigger(KeyInput.KEY_W));
		_inputManager.addMapping(UI_DOWN, new KeyTrigger(KeyInput.KEY_S));
		_inputManager.addMapping(UI_LEFT, new KeyTrigger(KeyInput.KEY_A));
		_inputManager.addMapping(UI_RIGHT, new KeyTrigger(KeyInput.KEY_D));
		_inputManager.addMapping(UI_CONFIRM, new KeyTrigger(KeyInput.KEY_RETURN));
		_inputManager.addMapping(UI_CONFIRM, new KeyTrigger(KeyInput.KEY_SPACE));
		_inputManager.addMapping(UI_BACK, new KeyTrigger(KeyInput.KEY_ESCAPE));
		_inputManager.addMapping(PAUSE, new KeyTrigger(KeyInput.KEY_ESCAPE));
	}
	
	/**
	 * Setup mouse input mappings using current (possibly remapped) mouse button codes.
	 */
	private void setupMouseMappings()
	{
		// Rebindable mouse button actions.
		for (Entry<String, Integer> entry : _currentMouseCodes.entrySet())
		{
			_inputManager.addMapping(entry.getKey(), new MouseButtonTrigger(entry.getValue()));
		}
		
		// Mouse wheel for block selection (in addition to keyboard keys).
		_inputManager.addMapping(NEXT_BLOCK, new MouseAxisTrigger(MouseInput.AXIS_WHEEL, false));
		_inputManager.addMapping(PREV_BLOCK, new MouseAxisTrigger(MouseInput.AXIS_WHEEL, true));
		
		// Mouse axes for looking (analog input).
		_inputManager.addMapping(LOOK_LEFT, new MouseAxisTrigger(MouseInput.AXIS_X, true));
		_inputManager.addMapping(LOOK_RIGHT, new MouseAxisTrigger(MouseInput.AXIS_X, false));
		_inputManager.addMapping(LOOK_UP, new MouseAxisTrigger(MouseInput.AXIS_Y, true));
		_inputManager.addMapping(LOOK_DOWN, new MouseAxisTrigger(MouseInput.AXIS_Y, false));
	}
	
	// ========== REBINDING API ==========
	
	/**
	 * Get the current key code for a rebindable action.
	 * @param action The action constant (e.g., MOVE_FORWARD)
	 * @return The current key code, or -1 if the action is not rebindable
	 */
	public int getKeyCode(String action)
	{
		final Integer code = _currentKeyCodes.get(action);
		return code != null ? code : -1;
	}
	
	/**
	 * Remap a keyboard binding to a new key. If the new key is already bound to another<br>
	 * action, the two bindings are swapped. Updates the jME3 InputManager immediately.
	 * @param action The action to rebind
	 * @param newKeyCode The new key code (from KeyInput constants)
	 * @return The action that was swapped, or null if no swap occurred
	 */
	public String remapKey(String action, int newKeyCode)
	{
		if (!_currentKeyCodes.containsKey(action))
		{
			return null;
		}
		
		final int oldKeyCode = _currentKeyCodes.get(action);
		if (oldKeyCode == newKeyCode)
		{
			return null;
		}
		
		// Check for conflict (another action already using this key).
		String swappedAction = null;
		for (Entry<String, Integer> entry : _currentKeyCodes.entrySet())
		{
			if (entry.getValue() == newKeyCode && !entry.getKey().equals(action))
			{
				swappedAction = entry.getKey();
				break;
			}
		}
		
		// Swap: give the conflicting action our old key.
		if (swappedAction != null)
		{
			_currentKeyCodes.put(swappedAction, oldKeyCode);
			reregisterMapping(swappedAction);
		}
		
		// Apply the new binding.
		_currentKeyCodes.put(action, newKeyCode);
		reregisterMapping(action);
		
		// Persist to settings.
		final simplecraft.settings.SettingsManager settings = SimpleCraft.getInstance().getSettingsManager();
		settings.setKeybindings(_currentKeyCodes);
		
		return swappedAction;
	}
	
	/**
	 * Reset all keyboard bindings to their defaults. Updates InputManager and SettingsManager.
	 */
	public void resetKeybindings()
	{
		_currentKeyCodes.clear();
		_currentKeyCodes.putAll(DEFAULT_KEY_CODES);
		
		// Re-register all rebindable mappings.
		for (String action : DEFAULT_KEY_CODES.keySet())
		{
			reregisterMapping(action);
		}
		
		// Reset mouse bindings.
		resetMouseBindings();
		
		// Clear persisted overrides.
		final simplecraft.settings.SettingsManager settings = SimpleCraft.getInstance().getSettingsManager();
		settings.clearKeybindings();
		settings.clearMouseBindings();
		
		System.out.println("GameInputManager: Keybindings reset to defaults.");
	}
	
	// ========== MOUSE REBINDING API ==========
	
	/**
	 * Get the current mouse button code for a mouse-rebindable action.
	 * @param action The action constant (e.g., ATTACK, PLACE_BLOCK)
	 * @return The current mouse button code, or -1 if not a mouse-rebindable action
	 */
	public int getMouseCode(String action)
	{
		final Integer code = _currentMouseCodes.get(action);
		return code != null ? code : -1;
	}
	
	/**
	 * Remap a mouse button binding. If the new button is already bound to another<br>
	 * action, the two bindings are swapped. Updates the jME3 InputManager immediately.
	 * @param action The action to rebind
	 * @param newButtonCode The new mouse button code (from MouseInput constants)
	 * @return The action that was swapped, or null if no swap occurred
	 */
	public String remapMouseButton(String action, int newButtonCode)
	{
		if (!_currentMouseCodes.containsKey(action))
		{
			return null;
		}
		
		final int oldButtonCode = _currentMouseCodes.get(action);
		if (oldButtonCode == newButtonCode)
		{
			return null;
		}
		
		// Check for conflict.
		String swappedAction = null;
		for (Entry<String, Integer> entry : _currentMouseCodes.entrySet())
		{
			if (entry.getValue() == newButtonCode && !entry.getKey().equals(action))
			{
				swappedAction = entry.getKey();
				break;
			}
		}
		
		// Swap: give the conflicting action our old button.
		if (swappedAction != null)
		{
			_currentMouseCodes.put(swappedAction, oldButtonCode);
			reregisterMouseMapping(swappedAction);
		}
		
		// Apply the new binding.
		_currentMouseCodes.put(action, newButtonCode);
		reregisterMouseMapping(action);
		
		// Persist to settings.
		SimpleCraft.getInstance().getSettingsManager().setMouseBindings(_currentMouseCodes);
		
		return swappedAction;
	}
	
	/**
	 * Reset all mouse button bindings to defaults.
	 */
	public void resetMouseBindings()
	{
		_currentMouseCodes.clear();
		_currentMouseCodes.putAll(DEFAULT_MOUSE_CODES);
		
		for (String action : DEFAULT_MOUSE_CODES.keySet())
		{
			reregisterMouseMapping(action);
		}
	}
	
	/**
	 * Delete and re-add the InputManager mouse button mapping for a single action.
	 */
	private void reregisterMouseMapping(String action)
	{
		if (_inputManager.hasMapping(action))
		{
			_inputManager.deleteMapping(action);
		}
		
		final Integer buttonCode = _currentMouseCodes.get(action);
		if (buttonCode != null)
		{
			_inputManager.addMapping(action, new MouseButtonTrigger(buttonCode));
		}
		
		// Re-add keyboard trigger if this action also has one (e.g., future expansion).
		final Integer keyCode = _currentKeyCodes.get(action);
		if (keyCode != null)
		{
			_inputManager.addMapping(action, new KeyTrigger(keyCode));
		}
	}
	
	/**
	 * Get the default mouse button code for a mouse-rebindable action.
	 * @param action The action constant
	 * @return The default mouse button code, or -1 if not found
	 */
	public static int getDefaultMouseCode(String action)
	{
		final Integer code = DEFAULT_MOUSE_CODES.get(action);
		return code != null ? code : -1;
	}
	
	/**
	 * Get the human-readable display name for a mouse button code.
	 * @param buttonCode The MouseInput button constant
	 * @return A display string (e.g., "Left Click"), or "Button XXX" for unknown buttons
	 */
	public static String getMouseButtonName(int buttonCode)
	{
		final String name = MOUSE_BUTTON_NAMES.get(buttonCode);
		return name != null ? name : ("Button " + buttonCode);
	}
	
	/**
	 * Check if a mouse button code is valid for rebinding.
	 * @param buttonCode The mouse button code to validate
	 * @return true if the button can be used as a binding
	 */
	public static boolean isValidMouseButton(int buttonCode)
	{
		return MOUSE_BUTTON_NAMES.containsKey(buttonCode);
	}
	
	/**
	 * Delete and re-add the InputManager mapping for a single action,<br>
	 * preserving any mouse triggers that share the same action name.
	 */
	private void reregisterMapping(String action)
	{
		// Remove the old mapping entirely (keyboard + mouse triggers).
		if (_inputManager.hasMapping(action))
		{
			_inputManager.deleteMapping(action);
		}
		
		// Re-add the keyboard trigger with the current key code.
		final Integer keyCode = _currentKeyCodes.get(action);
		if (keyCode != null)
		{
			_inputManager.addMapping(action, new KeyTrigger(keyCode));
		}
		
		// Re-add mouse triggers for actions that also have mouse input.
		if (NEXT_BLOCK.equals(action))
		{
			_inputManager.addMapping(action, new MouseAxisTrigger(MouseInput.AXIS_WHEEL, false));
		}
		else if (PREV_BLOCK.equals(action))
		{
			_inputManager.addMapping(action, new MouseAxisTrigger(MouseInput.AXIS_WHEEL, true));
		}
	}
	
	/**
	 * Get the default key code for a rebindable action.
	 * @param action The action constant
	 * @return The default key code, or -1 if not found
	 */
	public static int getDefaultKeyCode(String action)
	{
		final Integer code = DEFAULT_KEY_CODES.get(action);
		return code != null ? code : -1;
	}
	
	/**
	 * Get the human-readable display name for a key code.
	 * @param keyCode The jME3 KeyInput constant
	 * @return A short display string (e.g., "W", "Space", "L.Shift"), or "Key XXX" for unknown keys
	 */
	public static String getKeyName(int keyCode)
	{
		final String name = KEY_NAMES.get(keyCode);
		return name != null ? name : ("Key " + keyCode);
	}
	
	/**
	 * Check if a key code is valid for rebinding (not Escape, not unknown/zero).
	 * @param keyCode The key code to validate
	 * @return true if the key can be used as a binding
	 */
	public static boolean isValidBindingKey(int keyCode)
	{
		// Reject Escape (reserved for pause/back) and invalid codes.
		return keyCode > 0 && keyCode != KeyInput.KEY_ESCAPE;
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
