package simplecraft.player;

import com.jme3.input.InputManager;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;

import simplecraft.input.GameInputManager;
import simplecraft.player.PlayerCollision.CollisionResult;
import simplecraft.world.Block;
import simplecraft.world.World;

/**
 * First-person player controller with gravity, AABB collision, and step-up.<br>
 * Handles mouse look and WASD movement relative to camera facing direction.<br>
 * Horizontal movement uses yaw-only forward/right vectors so the player always<br>
 * moves on the horizontal plane regardless of where the camera is looking.<br>
 * Vertical movement is governed by gravity and ground detection via {@link PlayerCollision}.<br>
 * <br>
 * Horizontal movement deltas are passed to the collision system which resolves<br>
 * axis-separated AABB sweeps with step-up support for 1-block ledges.<br>
 * <br>
 * Fall damage is applied on landing when fall distance exceeds 3 blocks.<br>
 * Landing in water cancels all fall damage.<br>
 * <br>
 * When in water, movement speed is reduced to 60% and Space/Shift control<br>
 * vertical swimming instead of jumping. An air meter drains while the player's<br>
 * head is submerged; when air reaches zero, drowning damage is applied<br>
 * continuously until the player surfaces.<br>
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
	private final World _world;
	private final PlayerCollision _collision;
	
	/** World position of the player (feet level). */
	private final Vector3f _position = new Vector3f();
	
	/** Player velocity. Horizontal is set from input each frame; vertical accumulates from gravity. */
	private final Vector3f _velocity = new Vector3f();
	
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
	
	// Collision state flags.
	private boolean _onGround;
	private boolean _inWater;
	private boolean _headSubmerged;
	
	/** True when the player is in water and actively moving (any directional input). */
	private boolean _isSwimming;
	
	/** Eye height offset above foot position. */
	private static final float EYE_HEIGHT = 1.6f;
	
	/** Maximum pitch angle in radians (±89 degrees). */
	private static final float MAX_PITCH = 89f * FastMath.DEG_TO_RAD;
	
	/** Fall distance threshold in blocks before damage is dealt. */
	private static final float FALL_DAMAGE_THRESHOLD = 3.0f;
	
	/** Damage multiplier per block fallen beyond the threshold. */
	private static final float FALL_DAMAGE_MULTIPLIER = 2.0f;
	
	/** Horizontal speed multiplier when in water (60% of normal). */
	private static final float WATER_SPEED_MULTIPLIER = 0.6f;
	
	/** Drowning damage per second when air is depleted and head is submerged. */
	private static final float DROWNING_DAMAGE_PER_SECOND = 2.0f;
	
	/** Rate at which air restores when head is above water (multiplier of drain rate). */
	private static final float AIR_RESTORE_MULTIPLIER = 3.0f;
	
	// Reusable vectors to avoid per-frame allocation.
	private final Vector3f _forward = new Vector3f();
	private final Vector3f _right = new Vector3f();
	private final Vector3f _moveDir = new Vector3f();
	private final Vector3f _eyePos = new Vector3f();
	private final Quaternion _rotation = new Quaternion();
	
	/** Player health. */
	private float _health = 20f;
	
	/** Maximum health. */
	private float _maxHealth = 20f;
	
	/** Remaining air in seconds. Drains while head is submerged. */
	private float _air = 10f;
	
	/** Maximum air supply in seconds. */
	private float _maxAir = 10f;
	
	/** Whether the drowning log has been printed this submersion (avoids log spam). */
	private boolean _drowningLogged;
	
	/** Spawn protection — ignores fall damage until the player touches ground for the first time. */
	private boolean _spawnProtection = true;
	
	/** Currently selected block for placement. Managed by {@link BlockInteraction}. */
	private Block _selectedBlock = Block.DIRT;
	
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
	 * @param world the game world for block collision lookups
	 */
	public PlayerController(Camera camera, InputManager inputManager, World world)
	{
		_camera = camera;
		_inputManager = inputManager;
		_world = world;
		_collision = new PlayerCollision();
		
		// Reduce near clip so geometry at the player's collision boundary (0.3 blocks
		// from walls) is never clipped. setFrustumPerspective rebuilds the full
		// projection matrix cleanly — earlier attempts using setFrustumNear alone
		// caused distortion because they only modified one frustum parameter.
		final float aspect = (float) _camera.getWidth() / _camera.getHeight();
		_camera.setFrustumPerspective(45f, aspect, 0.1f, 1000f);
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
		_velocity.set(0, 0, 0);
		_spawnProtection = true;
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
	 * Update player movement, collision, and camera each frame.
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
		
		// --- Horizontal movement (instant from input, not accumulated) ---
		_moveDir.set(0, 0, 0);
		
		if (_moveForward)
		{
			_moveDir.addLocal(_forward);
		}
		
		if (_moveBack)
		{
			_moveDir.subtractLocal(_forward);
		}
		
		if (_moveLeft)
		{
			_moveDir.subtractLocal(_right);
		}
		
		if (_moveRight)
		{
			_moveDir.addLocal(_right);
		}
		
		// Normalize so diagonal movement isn't faster, then apply speed.
		// Reduce speed to 60% when in water.
		float deltaX = 0;
		float deltaZ = 0;
		if (_moveDir.lengthSquared() > 0)
		{
			_moveDir.normalizeLocal();
			float effectiveSpeed = _moveSpeed;
			if (_inWater)
			{
				effectiveSpeed *= WATER_SPEED_MULTIPLIER;
			}
			_moveDir.multLocal(effectiveSpeed * tpf);
			deltaX = _moveDir.x;
			deltaZ = _moveDir.z;
		}
		
		// --- Vertical: jump input ---
		boolean waterSurfaceJump = false;
		if (_moveUp)
		{
			if (_inWater && !_headSubmerged)
			{
				// At water surface — jump out of water.
				_velocity.y = 8f;
				waterSurfaceJump = true;
			}
			else if (_onGround && !_inWater)
			{
				// Normal land jump.
				_velocity.y = 8f;
				_onGround = false;
			}
		}
		
		// --- Swimming state ---
		final boolean hasMovementInput = _moveForward || _moveBack || _moveLeft || _moveRight || _moveUp || _moveDown;
		_isSwimming = _inWater && hasMovementInput;
		
		// Resolve collision. Pass swim input flags so the collision system can
		// handle swim-up (Space) and swim-down (Shift) when in water.
		// Don't pass swimUp when doing a surface jump — let the jump impulse work.
		final boolean swimUp = _inWater && _moveUp && !waterSurfaceJump;
		final boolean swimDown = _inWater && _moveDown;
		final CollisionResult result = _collision.resolveCollision(_position, _velocity, deltaX, deltaZ, _world, tpf, swimUp, swimDown);
		_onGround = result.isOnGround();
		_inWater = result.isInWater();
		_headSubmerged = result.isHeadSubmerged();
		
		// --- Fall damage ---
		final float fallDistance = result.getFallDistance();
		if (_spawnProtection)
		{
			// Clear spawn protection once the player lands for the first time.
			if (_onGround)
			{
				_spawnProtection = false;
				if (fallDistance > FALL_DAMAGE_THRESHOLD)
				{
					System.out.println("Spawn protection absorbed " + String.format("%.1f", fallDistance) + " blocks of fall distance.");
				}
			}
		}
		else if (fallDistance > FALL_DAMAGE_THRESHOLD)
		{
			if (_inWater)
			{
				// Water breaks the fall — no damage.
				System.out.println("Water saved you! Fall distance: " + String.format("%.1f", fallDistance) + " blocks.");
			}
			else
			{
				final float damage = (fallDistance - FALL_DAMAGE_THRESHOLD) * FALL_DAMAGE_MULTIPLIER;
				_health -= damage;
				_health = Math.max(0, _health);
				System.out.println("Fall damage! Distance: " + String.format("%.1f", fallDistance) + " blocks, Damage: " + String.format("%.1f", damage) + ", Health: " + String.format("%.1f", _health) + "/" + String.format("%.0f", _maxHealth));
			}
		}
		
		// --- Air and drowning ---
		if (_headSubmerged)
		{
			_air -= tpf;
			if (_air <= 0)
			{
				_air = 0;
				
				// Drowning damage — continuous while suffocating.
				_health -= DROWNING_DAMAGE_PER_SECOND * tpf;
				_health = Math.max(0, _health);
				
				if (!_drowningLogged)
				{
					System.out.println("Drowning!");
					_drowningLogged = true;
				}
			}
		}
		else
		{
			// Head above water — restore air 3× faster than it drains.
			if (_air < _maxAir)
			{
				_air += tpf * AIR_RESTORE_MULTIPLIER;
				_air = Math.min(_air, _maxAir);
			}
			_drowningLogged = false;
		}
		
		// Update camera position (eye height above feet).
		_eyePos.set(_position.x, _position.y + EYE_HEIGHT, _position.z);
		_camera.setLocation(_eyePos);
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
				_moveUp = isPressed;
				break;
			}
			case GameInputManager.SWIM_DOWN:
			{
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
	
	public float getAir()
	{
		return _air;
	}
	
	public float getMaxAir()
	{
		return _maxAir;
	}
	
	public boolean isOnGround()
	{
		return _onGround;
	}
	
	public boolean isInWater()
	{
		return _inWater;
	}
	
	public boolean isHeadSubmerged()
	{
		return _headSubmerged;
	}
	
	public boolean isSwimming()
	{
		return _isSwimming;
	}
	
	public Block getSelectedBlock()
	{
		return _selectedBlock;
	}
	
	public void setSelectedBlock(Block selectedBlock)
	{
		_selectedBlock = selectedBlock;
	}
}
