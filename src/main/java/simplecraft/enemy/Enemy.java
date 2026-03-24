package simplecraft.enemy;

import java.util.ArrayList;
import java.util.List;

import com.jme3.asset.AssetManager;
import com.jme3.effect.ParticleEmitter;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

import simplecraft.audio.AudioManager;
import simplecraft.enemy.EnemyAI.AIState;
import simplecraft.util.Rnd;
import simplecraft.world.World;

/**
 * Base class for all enemies.<br>
 * Holds the visual model (a hierarchy of Nodes and Geometries built from box primitives),<br>
 * combat stats, positional data and AI state. The model is assembled by {@link EnemyFactory}.<br>
 * Each frame, {@link EnemyAI} drives behavior and {@link EnemyAnimator} drives procedural animations.<br>
 * <br>
 * <b>Player combat:</b> {@link #takeDamage(float)} reduces health and triggers a 0.1-second white hit flash<br>
 * across all geometry materials. When health reaches zero the enemy dies - {@code isAlive()} returns false,<br>
 * the state timer resets for death-linger tracking in {@link simplecraft.enemy.SpawnSystem} and the model<br>
 * scales down to zero over the linger duration via {@link EnemyAnimator}.
 * @author Pantelis Andrianakis
 * @since March 4th 2026
 */
public class Enemy
{
	/** All enemy types available in the game. */
	public enum EnemyType
	{
		ZOMBIE,
		SKELETON,
		WOLF,
		SPIDER,
		SLIME,
		PIRANHA,
		PLAYER,
		DRAGON,
		SHADOW
	}
	
	// ------------------------------------------------------------------
	// Hit flash constants.
	// ------------------------------------------------------------------
	
	/** Duration of the white hit flash in seconds. */
	private static final float HIT_FLASH_DURATION = 0.1f;
	
	/** Minimum interval between ambient sounds (seconds). */
	private static final float AMBIENT_SOUND_MIN_INTERVAL = 5.0f;
	
	/** Maximum interval between ambient sounds (seconds). */
	private static final float AMBIENT_SOUND_MAX_INTERVAL = 8.0f;
	
	/** Maximum distance from the player at which ambient sounds are played (blocks). */
	private static final float AMBIENT_SOUND_RANGE = 25.0f;
	
	// ------------------------------------------------------------------
	// Fields.
	// ------------------------------------------------------------------
	
	/** Root scene node that holds the entire enemy model. */
	private final Node _rootNode;
	
	/** World position of this enemy (feet / base). */
	private final Vector3f _position = new Vector3f();
	
	/** Current sky light level at enemy position (0-1) */
	private float _skyLight = 1.0f;
	
	/** Whether enemy lighting needs to be updated */
	private boolean _lightingDirty = true;
	
	/** The type of this enemy. */
	private final EnemyType _type;
	
	/** Current health. */
	private float _health;
	
	/** Maximum health. */
	private float _maxHealth;
	
	/** Movement speed in blocks per second. */
	private float _moveSpeed;
	
	/** Damage dealt per attack. */
	private float _attackDamage;
	
	/** Time between attacks in seconds. */
	private float _attackCooldown;
	
	/** Range at which the enemy detects the player (blocks). */
	private float _detectionRange;
	
	/** Range at which the enemy can hit the player (blocks). */
	private float _attackRange;
	
	/** Whether this enemy is alive. */
	private boolean _alive = true;
	
	/** Whether this enemy is currently playing its death animation. */
	private boolean _dying = false;
	
	/** Timer tracking progress of the death animation (seconds). */
	private float _deathTimer = 0;
	
	/** Whether this enemy lives in water (true for PIRANHA only). */
	private boolean _aquatic = false;
	
	/** Whether this enemy is currently moving. */
	private boolean _isMoving = false;
	
	/** Accumulated animation time in seconds (drives sine wave cycles). */
	private float _animTime = 0;
	
	/** Walk animation blend factor (0 = idle, 1 = full walk). Smoothly interpolated. */
	private float _walkBlend = 0;
	
	/** Whether this enemy is currently playing its spawn-in animation. */
	private boolean _spawning = false;
	
	/** Timer tracking progress of the spawn-in animation (seconds). */
	private float _spawnTimer = 0;
	
	// ------------------------------------------------------------------
	// AI state fields.
	// ------------------------------------------------------------------
	
	/** Current AI state. */
	private AIState _aiState = AIState.IDLE;
	
	/** Time spent in the current AI state (seconds). */
	private float _stateTimer = 0;
	
	/** Random idle duration for the current IDLE period (seconds). */
	private float _idleDuration = 3.0f;
	
	/** Current wander target position (null when not wandering). */
	private Vector3f _wanderTarget;
	
	/** Time since last attack (seconds). Used for attack cooldown tracking. */
	private float _attackTimer = 0;
	
	/** Piranha circle center for idle circling behavior. */
	private Vector3f _circleCenter;
	
	// ------------------------------------------------------------------
	// Hit flash / death animation fields.
	// ------------------------------------------------------------------
	
	/** Cached (Geometry, original Material) pairs for hit flash restoration. */
	private List<Object[]> _materialCache;
	
	/** Shared white material used for the hit flash effect. */
	private Material _whiteMaterial;
	
	/** Remaining time for the current hit flash (counts down to 0). */
	private float _hitFlashTimer;
	
	/** Whether the hit flash is currently active (materials swapped to white). */
	private boolean _hitFlashActive;
	
	/** Countdown timer for next ambient sound (seconds). */
	private float _ambientSoundTimer = AMBIENT_SOUND_MIN_INTERVAL + Rnd.nextFloat() * (AMBIENT_SOUND_MAX_INTERVAL - AMBIENT_SOUND_MIN_INTERVAL);
	
	// ------------------------------------------------------------------
	// Pathfinding fields.
	// ------------------------------------------------------------------
	
	/** Current A* path waypoints (world-space positions). Null when no path active. */
	private List<Vector3f> _path;
	
	/** Index of the next waypoint to walk toward in the current path. */
	private int _pathIndex = 0;
	
	/** Timer tracking seconds since the last path recalculation. */
	private float _pathTimer = 0;
	
	// ------------------------------------------------------------------
	// Body part sub-nodes (set by EnemyFactory, may be null for types that lack them).
	// ------------------------------------------------------------------
	
	private Node _head;
	private Node _body;
	private Node _leftArm;
	private Node _rightArm;
	private Node _leftLeg;
	private Node _rightLeg;
	
	// ------------------------------------------------------------------
	// Dragon-specific body parts (set by EnemyFactory, null for non-dragon).
	// ------------------------------------------------------------------
	
	private Node _jaw;
	private Node _tail1;
	private Node _tail2;
	private Node _tail3;
	
	// ------------------------------------------------------------------
	// Boss phase fields (DRAGON only).
	// ------------------------------------------------------------------
	
	/** Current boss phase (1, 2, or 3). */
	private int _bossPhase = 1;
	
	/** Whether the dragon is currently charging. */
	private boolean _chargeActive;
	
	/** Charge direction vector (normalized XZ). */
	private final Vector3f _chargeDirection = new Vector3f();
	
	/** Charge distance remaining (blocks). */
	private float _chargeDistanceRemaining;
	
	/** Timer tracking charge cooldown (seconds since last charge). */
	private float _chargeCooldown;
	
	/** Randomized interval before next charge (seconds). */
	private float _chargeInterval;
	
	/** Whether the dragon is in charge recovery (stunned after charge). */
	private boolean _chargeRecovery;
	
	/** Recovery timer (seconds remaining in recovery). */
	private float _chargeRecoveryTimer;
	
	/** Whether the dragon hit a pillar during the current charge. */
	private boolean _chargePillarStun;
	
	/** Pillar stun timer (seconds remaining - longer than normal recovery). */
	private float _pillarStunTimer;
	
	/** Whether the charge telegraph (head lowering) is playing. */
	private boolean _chargeTelegraph;
	
	/** Telegraph timer (counts up to 0.8s). */
	private float _telegraphTimer;
	
	/** Tail swipe cooldown timer (counts up). */
	private float _tailSwipeCooldown;
	
	/** Whether a tail swipe animation is currently playing. */
	private boolean _tailSwiping;
	
	/** Tail swipe animation timer. */
	private float _tailSwipeTimer;
	
	/** Whether the bite attack animation is currently playing. */
	private boolean _biteActive;
	
	/** Bite attack animation timer. */
	private float _biteTimer;
	
	/** Bite attack cooldown timer (counts up). */
	private float _biteCooldown;
	
	/** Whether the roar animation is currently playing (phase transition). */
	private boolean _roaring;
	
	/** Roar animation timer. */
	private float _roarTimer;
	
	/** Whether the dragon's death animation is the special boss death (collapse, not topple). */
	private boolean _bossDeath;
	
	/** Boss death animation timer. */
	private float _bossDeathTimer;
	
	// ------------------------------------------------------------------
	// Shadow-specific fields (fire breath replaces tail swipe).
	// ------------------------------------------------------------------
	
	/** Whether the shadow's fire breath is currently active. */
	private boolean _fireBreathActive;
	
	/** Fire breath animation timer. */
	private float _fireBreathTimer;
	
	/** Fire breath cooldown timer (counts up). */
	private float _fireBreathCooldown;
	
	/** Fire breath particle emitter (created by EnemyFactory, attached to head). */
	private ParticleEmitter _fireBreathEmitter;
	
	/** Ambient smoke emitter (created by EnemyFactory, attached outside enemy hierarchy). */
	private ParticleEmitter _smokeEmitter;
	
	/**
	 * Creates a new enemy with the given type.
	 * @param type the enemy type
	 */
	public Enemy(EnemyType type)
	{
		_type = type;
		_rootNode = new Node("Enemy_" + type.name());
	}
	
	/**
	 * Initializes combat-related visuals: caches all geometry materials and creates<br>
	 * the shared white material used for hit flashes. Must be called after the model<br>
	 * is fully assembled by {@link EnemyFactory}.
	 * @param assetManager the asset manager for material creation
	 */
	public void initCombat(AssetManager assetManager)
	{
		_whiteMaterial = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
		_whiteMaterial.setColor("Color", ColorRGBA.White);
		
		_materialCache = new ArrayList<>();
		cacheMaterials(_rootNode);
	}
	
	/**
	 * Recursively traverses the node hierarchy and stores every Geometry's<br>
	 * current material for later restoration after a hit flash.
	 */
	private void cacheMaterials(Node node)
	{
		for (Spatial child : node.getChildren())
		{
			if (child instanceof Geometry)
			{
				final Geometry geom = (Geometry) child;
				_materialCache.add(new Object[]
				{
					geom,
					geom.getMaterial()
				});
			}
			else if (child instanceof Node)
			{
				cacheMaterials((Node) child);
			}
		}
	}
	
	// ------------------------------------------------------------------
	// Player combat.
	// ------------------------------------------------------------------
	
	/**
	 * Deals damage to this enemy. Triggers a white hit flash on the model<br>
	 * and plays the appropriate hit or death sound effect.<br>
	 * If health drops to zero, the enemy dies: {@code isAlive()} returns false<br>
	 * and the state timer resets so the spawn system can track the death linger period.
	 * @param amount the amount of damage to deal (positive)
	 * @param audioManager the audio manager for sound effect playback
	 */
	public void takeDamage(float amount, AudioManager audioManager)
	{
		if (!_alive || _dying || amount <= 0)
		{
			return;
		}
		
		_health -= amount;
		
		if (_health <= 0)
		{
			_health = 0;
			_dying = true;
			
			// Stop movement so the death pose plays cleanly.
			_isMoving = false;
			
			audioManager.playSfx(AudioManager.SFX_ENEMY_DEATH);
		}
		else
		{
			audioManager.playSfx(AudioManager.SFX_ENEMY_HIT);
		}
		
		// Trigger hit flash (white materials for HIT_FLASH_DURATION).
		applyWhiteMaterial();
		_hitFlashTimer = HIT_FLASH_DURATION;
		_hitFlashActive = true;
	}
	
	/**
	 * Updates visual effects each frame: hit flash fade-out and death scale-down.<br>
	 * Called by the spawn system for both alive and dead enemies.
	 * @param tpf time per frame in seconds
	 */
	public void updateVisuals(float tpf)
	{
		// Hit flash fade-out.
		if (_hitFlashActive)
		{
			_hitFlashTimer -= tpf;
			if (_hitFlashTimer <= 0)
			{
				_hitFlashActive = false;
				restoreOriginalMaterials();
			}
		}
	}
	
	/**
	 * Sets all cached geometries to the white flash material.
	 */
	private void applyWhiteMaterial()
	{
		if (_materialCache == null)
		{
			return;
		}
		
		for (Object[] pair : _materialCache)
		{
			((Geometry) pair[0]).setMaterial(_whiteMaterial);
		}
	}
	
	/**
	 * Restores all cached geometries to their original materials.
	 */
	private void restoreOriginalMaterials()
	{
		if (_materialCache == null)
		{
			return;
		}
		
		for (Object[] pair : _materialCache)
		{
			((Geometry) pair[0]).setMaterial((Material) pair[1]);
		}
		
		// Mark lighting dirty so EnemyLighting re-applies the correct light level.
		_lightingDirty = true;
	}
	
	// ------------------------------------------------------------------
	// Per-frame update.
	// ------------------------------------------------------------------
	
	/**
	 * Per-frame update. Drives AI behavior via {@link EnemyAI} and then<br>
	 * procedural animation via {@link EnemyAnimator}.<br>
	 * Also plays positional ambient sounds when the enemy is within range of the player.
	 * @param playerPos the player's current world position
	 * @param playerInWater true if the player's feet are in water
	 * @param world the game world for block queries
	 * @param audioManager the audio manager for positional sound effects
	 * @param tpf time per frame in seconds
	 */
	public void update(Vector3f playerPos, boolean playerInWater, World world, AudioManager audioManager, float tpf)
	{
		// Skip AI and animation while the spawn-in effect is playing.
		if (_spawning)
		{
			return;
		}
		
		// Update visual effects (hit flash fade-out).
		updateVisuals(tpf);
		
		// AI drives state transitions, movement and facing.
		EnemyAI.update(this, playerPos, playerInWater, world, tpf);
		
		// Animator drives limb rotations and visual effects.
		EnemyAnimator.update(this, tpf, _isMoving);
		
		// Ambient sounds - only while alive and within range of the player.
		if (_alive && !_dying)
		{
			updateAmbientSound(playerPos, audioManager, tpf);
		}
	}
	
	/**
	 * Ticks the ambient sound timer and plays a positional type-specific sound<br>
	 * when the timer expires and the player is within {@link #AMBIENT_SOUND_RANGE}.
	 */
	private void updateAmbientSound(Vector3f playerPos, AudioManager audioManager, float tpf)
	{
		_ambientSoundTimer -= tpf;
		if (_ambientSoundTimer > 0)
		{
			return;
		}
		
		// Reset timer for next ambient sound.
		_ambientSoundTimer = AMBIENT_SOUND_MIN_INTERVAL + Rnd.nextFloat() * (AMBIENT_SOUND_MAX_INTERVAL - AMBIENT_SOUND_MIN_INTERVAL);
		
		// Only play if the player is within audible range.
		final float distSq = _position.distanceSquared(playerPos);
		if (distSq > AMBIENT_SOUND_RANGE * AMBIENT_SOUND_RANGE)
		{
			return;
		}
		
		final String sfxPath = getAmbientSoundPath();
		if (sfxPath != null)
		{
			audioManager.playSfx(sfxPath);
		}
	}
	
	/**
	 * Returns the ambient sound asset path for this enemy's type, or null if none.
	 * @return the SFX asset path, or null for types with no ambient sound
	 */
	public String getAmbientSoundPath()
	{
		switch (_type)
		{
			case ZOMBIE:
			{
				return AudioManager.SFX_ZOMBIE_GROAN;
			}
			case SKELETON:
			{
				return AudioManager.SFX_SKELETON_RATTLE;
			}
			case WOLF:
			{
				return AudioManager.SFX_WOLF_GROWL;
			}
			case SPIDER:
			{
				return AudioManager.SFX_SPIDER_HISS;
			}
			case SLIME:
			{
				return AudioManager.SFX_SLIME_SQUELCH;
			}
			case DRAGON:
			{
				return AudioManager.SFX_DRAGON;
			}
			case SHADOW:
			{
				return AudioManager.SFX_SHADOW;
			}
			default:
			{
				return null;
			}
		}
	}
	
	/**
	 * Returns the root scene node for this enemy.
	 * @return the root node
	 */
	public Node getNode()
	{
		return _rootNode;
	}
	
	/**
	 * Sets the world position and updates the root node translation.
	 * @param pos the new position (feet / base level)
	 */
	public void setPosition(Vector3f pos)
	{
		_position.set(pos);
		_rootNode.setLocalTranslation(pos);
	}
	
	/**
	 * Returns the current world position.
	 * @return position vector
	 */
	public Vector3f getPosition()
	{
		return _position;
	}
	
	public float getSkyLight()
	{
		return _skyLight;
	}
	
	public void setSkyLight(float skyLight)
	{
		if (Math.abs(_skyLight - skyLight) > 0.01f)
		{
			_skyLight = skyLight;
			_lightingDirty = true;
		}
	}
	
	public boolean isLightingDirty()
	{
		return _lightingDirty;
	}
	
	public void setLightingDirty(boolean lightingDirty)
	{
		_lightingDirty = lightingDirty;
	}
	
	public EnemyType getType()
	{
		return _type;
	}
	
	public float getHealth()
	{
		return _health;
	}
	
	public void setHealth(float health)
	{
		_health = health;
	}
	
	public float getMaxHealth()
	{
		return _maxHealth;
	}
	
	public void setMaxHealth(float maxHealth)
	{
		_maxHealth = maxHealth;
	}
	
	public float getMoveSpeed()
	{
		return _moveSpeed;
	}
	
	public void setMoveSpeed(float moveSpeed)
	{
		_moveSpeed = moveSpeed;
	}
	
	public float getAttackDamage()
	{
		return _attackDamage;
	}
	
	public void setAttackDamage(float attackDamage)
	{
		_attackDamage = attackDamage;
	}
	
	public float getAttackCooldown()
	{
		return _attackCooldown;
	}
	
	public void setAttackCooldown(float attackCooldown)
	{
		_attackCooldown = attackCooldown;
	}
	
	public float getDetectionRange()
	{
		return _detectionRange;
	}
	
	public void setDetectionRange(float detectionRange)
	{
		_detectionRange = detectionRange;
	}
	
	public float getAttackRange()
	{
		return _attackRange;
	}
	
	public void setAttackRange(float attackRange)
	{
		_attackRange = attackRange;
	}
	
	public boolean isAlive()
	{
		return _alive;
	}
	
	public void setAlive(boolean alive)
	{
		_alive = alive;
	}
	
	public boolean isDying()
	{
		return _dying;
	}
	
	public void setDying(boolean dying)
	{
		_dying = dying;
	}
	
	public float getDeathTimer()
	{
		return _deathTimer;
	}
	
	public void setDeathTimer(float deathTimer)
	{
		_deathTimer = deathTimer;
	}
	
	public boolean isAquatic()
	{
		return _aquatic;
	}
	
	public void setAquatic(boolean aquatic)
	{
		_aquatic = aquatic;
	}
	
	public boolean isMoving()
	{
		return _isMoving;
	}
	
	public void setMoving(boolean moving)
	{
		_isMoving = moving;
	}
	
	public float getAnimTime()
	{
		return _animTime;
	}
	
	public void setAnimTime(float animTime)
	{
		_animTime = animTime;
	}
	
	public float getWalkBlend()
	{
		return _walkBlend;
	}
	
	public void setWalkBlend(float walkBlend)
	{
		_walkBlend = walkBlend;
	}
	
	public boolean isSpawning()
	{
		return _spawning;
	}
	
	public void setSpawning(boolean spawning)
	{
		_spawning = spawning;
	}
	
	public float getSpawnTimer()
	{
		return _spawnTimer;
	}
	
	public void setSpawnTimer(float spawnTimer)
	{
		_spawnTimer = spawnTimer;
	}
	
	// ------------------------------------------------------------------
	// AI state accessors.
	// ------------------------------------------------------------------
	
	public AIState getAIState()
	{
		return _aiState;
	}
	
	public void setAIState(AIState aiState)
	{
		_aiState = aiState;
	}
	
	public float getStateTimer()
	{
		return _stateTimer;
	}
	
	public void setStateTimer(float stateTimer)
	{
		_stateTimer = stateTimer;
	}
	
	public float getIdleDuration()
	{
		return _idleDuration;
	}
	
	public void setIdleDuration(float idleDuration)
	{
		_idleDuration = idleDuration;
	}
	
	public Vector3f getWanderTarget()
	{
		return _wanderTarget;
	}
	
	public void setWanderTarget(Vector3f wanderTarget)
	{
		_wanderTarget = wanderTarget;
	}
	
	public float getAttackTimer()
	{
		return _attackTimer;
	}
	
	public void setAttackTimer(float attackTimer)
	{
		_attackTimer = attackTimer;
	}
	
	public Vector3f getCircleCenter()
	{
		return _circleCenter;
	}
	
	public void setCircleCenter(Vector3f circleCenter)
	{
		_circleCenter = circleCenter;
	}
	
	// ------------------------------------------------------------------
	// Pathfinding accessors.
	// ------------------------------------------------------------------
	
	public List<Vector3f> getPath()
	{
		return _path;
	}
	
	public void setPath(List<Vector3f> path)
	{
		_path = path;
		_pathIndex = 0;
	}
	
	public int getPathIndex()
	{
		return _pathIndex;
	}
	
	public void setPathIndex(int pathIndex)
	{
		_pathIndex = pathIndex;
	}
	
	public float getPathTimer()
	{
		return _pathTimer;
	}
	
	public void setPathTimer(float pathTimer)
	{
		_pathTimer = pathTimer;
	}
	
	/**
	 * Clears the current path and resets the path timer.
	 */
	public void clearPath()
	{
		_path = null;
		_pathIndex = 0;
		_pathTimer = 0;
	}
	
	// ------------------------------------------------------------------
	// Body part accessors (set by EnemyFactory).
	// ------------------------------------------------------------------
	
	public Node getHead()
	{
		return _head;
	}
	
	public void setHead(Node head)
	{
		_head = head;
	}
	
	public Node getBody()
	{
		return _body;
	}
	
	public void setBody(Node body)
	{
		_body = body;
	}
	
	public Node getLeftArm()
	{
		return _leftArm;
	}
	
	public void setLeftArm(Node leftArm)
	{
		_leftArm = leftArm;
	}
	
	public Node getRightArm()
	{
		return _rightArm;
	}
	
	public void setRightArm(Node rightArm)
	{
		_rightArm = rightArm;
	}
	
	public Node getLeftLeg()
	{
		return _leftLeg;
	}
	
	public void setLeftLeg(Node leftLeg)
	{
		_leftLeg = leftLeg;
	}
	
	public Node getRightLeg()
	{
		return _rightLeg;
	}
	
	public void setRightLeg(Node rightLeg)
	{
		_rightLeg = rightLeg;
	}
	
	// ------------------------------------------------------------------
	// Dragon body part accessors.
	// ------------------------------------------------------------------
	
	public Node getJaw()
	{
		return _jaw;
	}
	
	public void setJaw(Node jaw)
	{
		_jaw = jaw;
	}
	
	public Node getTail1()
	{
		return _tail1;
	}
	
	public void setTail1(Node tail1)
	{
		_tail1 = tail1;
	}
	
	public Node getTail2()
	{
		return _tail2;
	}
	
	public void setTail2(Node tail2)
	{
		_tail2 = tail2;
	}
	
	public Node getTail3()
	{
		return _tail3;
	}
	
	public void setTail3(Node tail3)
	{
		_tail3 = tail3;
	}
	
	// ------------------------------------------------------------------
	// Boss phase accessors (DRAGON only).
	// ------------------------------------------------------------------
	
	public int getBossPhase()
	{
		return _bossPhase;
	}
	
	public void setBossPhase(int phase)
	{
		_bossPhase = phase;
	}
	
	public boolean isChargeActive()
	{
		return _chargeActive;
	}
	
	public void setChargeActive(boolean active)
	{
		_chargeActive = active;
	}
	
	public Vector3f getChargeDirection()
	{
		return _chargeDirection;
	}
	
	public float getChargeDistanceRemaining()
	{
		return _chargeDistanceRemaining;
	}
	
	public void setChargeDistanceRemaining(float dist)
	{
		_chargeDistanceRemaining = dist;
	}
	
	public float getChargeCooldown()
	{
		return _chargeCooldown;
	}
	
	public void setChargeCooldown(float cd)
	{
		_chargeCooldown = cd;
	}
	
	public float getChargeInterval()
	{
		return _chargeInterval;
	}
	
	public void setChargeInterval(float interval)
	{
		_chargeInterval = interval;
	}
	
	public boolean isChargeRecovery()
	{
		return _chargeRecovery;
	}
	
	public void setChargeRecovery(boolean recovery)
	{
		_chargeRecovery = recovery;
	}
	
	public float getChargeRecoveryTimer()
	{
		return _chargeRecoveryTimer;
	}
	
	public void setChargeRecoveryTimer(float timer)
	{
		_chargeRecoveryTimer = timer;
	}
	
	public boolean isChargePillarStun()
	{
		return _chargePillarStun;
	}
	
	public void setChargePillarStun(boolean stun)
	{
		_chargePillarStun = stun;
	}
	
	public float getPillarStunTimer()
	{
		return _pillarStunTimer;
	}
	
	public void setPillarStunTimer(float timer)
	{
		_pillarStunTimer = timer;
	}
	
	public boolean isChargeTelegraph()
	{
		return _chargeTelegraph;
	}
	
	public void setChargeTelegraph(boolean telegraph)
	{
		_chargeTelegraph = telegraph;
	}
	
	public float getTelegraphTimer()
	{
		return _telegraphTimer;
	}
	
	public void setTelegraphTimer(float timer)
	{
		_telegraphTimer = timer;
	}
	
	public float getTailSwipeCooldown()
	{
		return _tailSwipeCooldown;
	}
	
	public void setTailSwipeCooldown(float cd)
	{
		_tailSwipeCooldown = cd;
	}
	
	public boolean isTailSwiping()
	{
		return _tailSwiping;
	}
	
	public void setTailSwiping(boolean swiping)
	{
		_tailSwiping = swiping;
	}
	
	public float getTailSwipeTimer()
	{
		return _tailSwipeTimer;
	}
	
	public void setTailSwipeTimer(float timer)
	{
		_tailSwipeTimer = timer;
	}
	
	public boolean isBiteActive()
	{
		return _biteActive;
	}
	
	public void setBiteActive(boolean active)
	{
		_biteActive = active;
	}
	
	public float getBiteTimer()
	{
		return _biteTimer;
	}
	
	public void setBiteTimer(float timer)
	{
		_biteTimer = timer;
	}
	
	public float getBiteCooldown()
	{
		return _biteCooldown;
	}
	
	public void setBiteCooldown(float cd)
	{
		_biteCooldown = cd;
	}
	
	public boolean isRoaring()
	{
		return _roaring;
	}
	
	public void setRoaring(boolean roaring)
	{
		_roaring = roaring;
	}
	
	public float getRoarTimer()
	{
		return _roarTimer;
	}
	
	public void setRoarTimer(float timer)
	{
		_roarTimer = timer;
	}
	
	public boolean isBossDeath()
	{
		return _bossDeath;
	}
	
	public void setBossDeath(boolean bossDeath)
	{
		_bossDeath = bossDeath;
	}
	
	public float getBossDeathTimer()
	{
		return _bossDeathTimer;
	}
	
	public void setBossDeathTimer(float timer)
	{
		_bossDeathTimer = timer;
	}
	
	// ------------------------------------------------------------------
	// Shadow fire breath accessors.
	// ------------------------------------------------------------------
	
	public boolean isFireBreathActive()
	{
		return _fireBreathActive;
	}
	
	public void setFireBreathActive(boolean active)
	{
		_fireBreathActive = active;
	}
	
	public float getFireBreathTimer()
	{
		return _fireBreathTimer;
	}
	
	public void setFireBreathTimer(float timer)
	{
		_fireBreathTimer = timer;
	}
	
	public float getFireBreathCooldown()
	{
		return _fireBreathCooldown;
	}
	
	public void setFireBreathCooldown(float cd)
	{
		_fireBreathCooldown = cd;
	}
	
	public ParticleEmitter getFireBreathEmitter()
	{
		return _fireBreathEmitter;
	}
	
	public void setFireBreathEmitter(ParticleEmitter emitter)
	{
		_fireBreathEmitter = emitter;
	}
	
	public ParticleEmitter getSmokeEmitter()
	{
		return _smokeEmitter;
	}
	
	public void setSmokeEmitter(ParticleEmitter emitter)
	{
		_smokeEmitter = emitter;
	}
}
