package simplecraft.enemy;

import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

import simplecraft.enemy.EnemyAI.AIState;
import simplecraft.world.World;

/**
 * Base class for all enemies.<br>
 * Holds the visual model (a hierarchy of Nodes and Geometries built from box primitives),<br>
 * combat stats, positional data, and AI state. The model is assembled by {@link EnemyFactory}.<br>
 * Each frame, {@link EnemyAI} drives behavior and {@link EnemyAnimator} drives procedural animations.
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
		PLAYER
	}
	
	/** Root scene node that holds the entire enemy model. */
	private final Node _rootNode;
	
	/** The type of this enemy. */
	private final EnemyType _type;
	
	/** World position of this enemy (feet / base). */
	private final Vector3f _position = new Vector3f();
	
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
	
	/** Whether this enemy lives in water (true for PIRANHA only). */
	private boolean _aquatic = false;
	
	/** Whether this enemy is currently moving. */
	private boolean _isMoving = false;
	
	/** Accumulated animation time in seconds (drives sine wave cycles). */
	private float _animTime = 0;
	
	/** Walk animation blend factor (0 = idle, 1 = full walk). Smoothly interpolated. */
	private float _walkBlend = 0;
	
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
	// Body part sub-nodes (set by EnemyFactory, may be null for types that lack them).
	// ------------------------------------------------------------------
	
	private Node _head;
	private Node _body;
	private Node _leftArm;
	private Node _rightArm;
	private Node _leftLeg;
	private Node _rightLeg;
	
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
	 * Per-frame update. Drives AI behavior via {@link EnemyAI} and then<br>
	 * procedural animation via {@link EnemyAnimator}.
	 * @param playerPos the player's current world position
	 * @param playerInWater true if the player's feet are in water
	 * @param world the game world for block queries
	 * @param tpf time per frame in seconds
	 */
	public void update(Vector3f playerPos, boolean playerInWater, World world, float tpf)
	{
		// AI drives state transitions, movement, and facing.
		EnemyAI.update(this, playerPos, playerInWater, world, tpf);
		
		// Animator drives limb rotations and visual effects.
		EnemyAnimator.update(this, tpf, _isMoving);
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
}
