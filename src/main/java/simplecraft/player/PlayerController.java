package simplecraft.player;

import com.jme3.input.InputManager;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;

import simplecraft.input.GameInputManager;

/**
 * First-person player controller — fly mode (no physics).<br>
 * Handles mouse look and WASD movement relative to camera facing direction.<br>
 * Movement uses yaw-only forward/right vectors so the player always moves<br>
 * on the horizontal plane regardless of where the camera is looking.<br>
 * <br>
 * Registers as both {@link ActionListener} (movement key flags) and<br>
 * {@link AnalogListener} (mouse axes) on the jME3 {@link InputManager}.<br>
 * Call {@link #update(float)} each frame from the owning state.
 * @author Pantelis Andrianakis
 * @since February 27th 2026
 */
public class PlayerController implements ActionListener, AnalogListener
{
	private final Camera _camera;
	private final InputManager _inputManager;
	
	/** World position of the player (feet level). */
	private final Vector3f _position = new Vector3f();
	
	/** Horizontal rotation in radians. 0 = looking along -Z (jME3 default). */
	private float _yaw;
	
	/** Vertical rotation in radians. Positive = looking up. */
	private float _pitch;
	
	/** Movement speed in blocks per second. */
	private float _moveSpeed = 4.3f;
	
	/** Mouse sensitivity — degrees per pixel of mouse movement. */
	private float _mouseSensitivity = 0.2f;
	
	// Movement flags set by key press/release.
	private boolean _moveForward;
	private boolean _moveBack;
	private boolean _moveLeft;
	private boolean _moveRight;
	private boolean _moveUp;
	private boolean _moveDown;
	
	/** Eye height offset above foot position. */
	private static final float EYE_HEIGHT = 1.6f;
	
	/** Maximum pitch angle in radians (±89 degrees). */
	private static final float MAX_PITCH = 89f * FastMath.DEG_TO_RAD;
	
	// Reusable vectors to avoid per-frame allocation.
	private final Vector3f _forward = new Vector3f();
	private final Vector3f _right = new Vector3f();
	private final Vector3f _velocity = new Vector3f();
	private final Quaternion _rotation = new Quaternion();
	
	/** Player health. Reserved for future use. */
	private float _health = 20f;
	
	/** Maximum health. Reserved for future use. */
	private float _maxHealth = 20f;
	
	// Action names this controller listens to.
	private static final String[] ACTIONS =
	{
		GameInputManager.MOVE_FORWARD,
		GameInputManager.MOVE_BACK,
		GameInputManager.MOVE_LEFT,
		GameInputManager.MOVE_RIGHT,
		GameInputManager.JUMP,
		GameInputManager.SWIM_DOWN,
	};
	
	private static final String[] ANALOG_ACTIONS =
	{
		GameInputManager.LOOK_LEFT,
		GameInputManager.LOOK_RIGHT,
		GameInputManager.LOOK_UP,
		GameInputManager.LOOK_DOWN,
	};
	
	/**
	 * Create a new player controller.
	 * @param camera the jME3 camera to control
	 * @param inputManager the jME3 input manager to register listeners on
	 */
	public PlayerController(Camera camera, InputManager inputManager)
	{
		_camera = camera;
		_inputManager = inputManager;
	}
	
	/**
	 * Register input listeners for movement keys and mouse look.<br>
	 * Call once when the controller becomes active.
	 */
	public void registerInput()
	{
		_inputManager.addListener((ActionListener) this, ACTIONS);
		_inputManager.addListener((AnalogListener) this, ANALOG_ACTIONS);
	}
	
	/**
	 * Remove input listeners.<br>
	 * Call when the controller is no longer active (state cleanup).
	 */
	public void unregisterInput()
	{
		_inputManager.removeListener(this);
	}
	
	/**
	 * Set the initial player position (feet level).
	 * @param x world X coordinate
	 * @param y world Y coordinate (feet)
	 * @param z world Z coordinate
	 */
	public void setPosition(float x, float y, float z)
	{
		_position.set(x, y, z);
	}
	
	/**
	 * Get the current player position (feet level).<br>
	 * Used by the world to determine region loading center.
	 * @return the player position vector (do not modify)
	 */
	public Vector3f getPosition()
	{
		return _position;
	}
	
	/**
	 * Update player movement and camera each frame.
	 * @param tpf time per frame in seconds
	 */
	public void update(float tpf)
	{
		// Apply camera rotation from accumulated yaw and pitch first,
		// then derive movement vectors from the actual camera orientation.
		// This guarantees movement always matches the visual look direction.
		_rotation.fromAngles(_pitch, _yaw, 0);
		_camera.setRotation(_rotation);
		
		// Derive horizontal forward vector from camera direction (flatten to XZ plane).
		_camera.getDirection(_forward);
		_forward.y = 0;
		_forward.normalizeLocal();
		
		// Derive horizontal right vector from camera left (negated).
		_camera.getLeft(_right);
		_right.negateLocal();
		_right.y = 0;
		_right.normalizeLocal();
		
		// Accumulate movement from flags.
		_velocity.set(0, 0, 0);
		
		if (_moveForward)
		{
			_velocity.addLocal(_forward);
		}
		
		if (_moveBack)
		{
			_velocity.subtractLocal(_forward);
		}
		
		if (_moveLeft)
		{
			_velocity.subtractLocal(_right);
		}
		
		if (_moveRight)
		{
			_velocity.addLocal(_right);
		}
		
		if (_moveUp)
		{
			_velocity.y += 1f;
		}
		
		if (_moveDown)
		{
			_velocity.y -= 1f;
		}
		
		// Normalize so diagonal movement isn't faster, then apply speed.
		if (_velocity.lengthSquared() > 0)
		{
			_velocity.normalizeLocal();
			_velocity.multLocal(_moveSpeed * tpf);
			_position.addLocal(_velocity);
		}
		
		// Update camera position (eye height above feet).
		_camera.setLocation(_position.add(0, EYE_HEIGHT, 0));
	}
	
	// ========== ActionListener — movement key flags ==========
	
	@Override
	public void onAction(String name, boolean isPressed, float tpf)
	{
		switch (name)
		{
			case GameInputManager.MOVE_FORWARD:
			{
				_moveForward = isPressed;
				break;
			}
			case GameInputManager.MOVE_BACK:
			{
				_moveBack = isPressed;
				break;
			}
			case GameInputManager.MOVE_LEFT:
			{
				_moveLeft = isPressed;
				break;
			}
			case GameInputManager.MOVE_RIGHT:
			{
				_moveRight = isPressed;
				break;
			}
			case GameInputManager.JUMP:
			{
				// In fly mode, JUMP moves up.
				_moveUp = isPressed;
				break;
			}
			case GameInputManager.SWIM_DOWN:
			{
				// In fly mode, SWIM_DOWN moves down.
				_moveDown = isPressed;
				break;
			}
		}
	}
	
	// ========== AnalogListener — mouse look ==========
	
	@Override
	public void onAnalog(String name, float value, float tpf)
	{
		// Convert mouse delta to rotation. Value is already in "pixels" of movement.
		final float delta = value * _mouseSensitivity;
		
		switch (name)
		{
			case GameInputManager.LOOK_LEFT:
			{
				_yaw += delta;
				break;
			}
			case GameInputManager.LOOK_RIGHT:
			{
				_yaw -= delta;
				break;
			}
			case GameInputManager.LOOK_UP:
			{
				_pitch += delta;
				break;
			}
			case GameInputManager.LOOK_DOWN:
			{
				_pitch -= delta;
				break;
			}
		}
		
		// Clamp pitch to prevent flipping.
		_pitch = FastMath.clamp(_pitch, -MAX_PITCH, MAX_PITCH);
		
		// Keep yaw in [0, TWO_PI) range to avoid float drift.
		if (_yaw < 0)
		{
			_yaw += FastMath.TWO_PI;
		}
		else if (_yaw >= FastMath.TWO_PI)
		{
			_yaw -= FastMath.TWO_PI;
		}
	}
	
	// ========== Getters / Setters ==========
	
	public float getMoveSpeed()
	{
		return _moveSpeed;
	}
	
	public void setMoveSpeed(float moveSpeed)
	{
		_moveSpeed = moveSpeed;
	}
	
	public float getMouseSensitivity()
	{
		return _mouseSensitivity;
	}
	
	public void setMouseSensitivity(float mouseSensitivity)
	{
		_mouseSensitivity = mouseSensitivity;
	}
	
	public float getHealth()
	{
		return _health;
	}
	
	public void setHealth(float health)
	{
		_health = Math.max(0, Math.min(health, _maxHealth));
	}
	
	public float getMaxHealth()
	{
		return _maxHealth;
	}
	
	public void setMaxHealth(float maxHealth)
	{
		_maxHealth = maxHealth;
	}
}
