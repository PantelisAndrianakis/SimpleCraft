package simplecraft.enemy;

import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

/**
 * Base class for all enemies.<br>
 * Holds the visual model (a hierarchy of Nodes and Geometries built from box primitives),<br>
 * combat stats, and positional data. The model is assembled by {@link EnemyFactory}.
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
	
	// Named sub-nodes for body parts (set by EnemyFactory, may be null for types that lack them).
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
	 * Per-frame update. Placeholder for future AI / animation.
	 * @param tpf time per frame in seconds
	 */
	public void update(float tpf)
	{
		// Future: AI, animation, combat logic.
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
