package simplecraft.item;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;

/**
 * A world entity representing an item floating on the ground after an enemy death.<br>
 * Renders as a small colored cube (0.15 block half-extent) that bobs up and down<br>
 * via a sine wave and spins slowly around the Y axis. The color is determined by<br>
 * the item type for quick visual identification.<br>
 * <br>
 * Pickup is handled externally by {@link DropManager}, which checks distance to<br>
 * the player each frame and attempts {@link Inventory#addItem(ItemInstance)}.<br>
 * <br>
 * Each drop has a 300-second (5-minute) lifetime. When expired, the drop is<br>
 * removed from the world by the manager.
 * @author Pantelis Andrianakis
 * @since March 15th 2026
 */
public class DroppedItem
{
	/** Half-extent of the visual cube (0.15 = 0.3 blocks wide). */
	private static final float CUBE_HALF_SIZE = 0.15f;
	
	/** Vertical bob amplitude in blocks. */
	private static final float BOB_AMPLITUDE = 0.15f;
	
	/** Bob frequency multiplier (radians per second). */
	private static final float BOB_SPEED = 2.5f;
	
	/** Spin speed in radians per second. */
	private static final float SPIN_SPEED = 1.5f;
	
	/** Base vertical offset above the ground position. */
	private static final float BASE_Y_OFFSET = 0.3f;
	
	/** Maximum lifetime before automatic despawn (seconds). */
	private static final float MAX_LIFETIME = 300.0f;
	
	/** The item stack this drop represents. */
	private final ItemInstance _instance;
	
	/** World position (ground level where the drop was spawned). */
	private final Vector3f _position;
	
	/** Scene node holding the visual geometry. */
	private final Node _node;
	
	/** Remaining lifetime in seconds. */
	private float _lifetime;
	
	/** Accumulated bob/spin animation timer. */
	private float _animTimer;
	
	/**
	 * Creates a new dropped item at the given world position.
	 * @param instance the item stack to drop
	 * @param position the world position (ground level at enemy death)
	 * @param assetManager the asset manager for creating materials
	 */
	public DroppedItem(ItemInstance instance, Vector3f position, AssetManager assetManager)
	{
		_instance = instance;
		_position = new Vector3f(position);
		_lifetime = MAX_LIFETIME;
		_animTimer = 0;
		
		// Build the visual node.
		_node = new Node("DroppedItem_" + instance.getTemplate().getId());
		
		final Box box = new Box(CUBE_HALF_SIZE, CUBE_HALF_SIZE, CUBE_HALF_SIZE);
		final Geometry geom = new Geometry("DropCube", box);
		
		final Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
		mat.setColor("Color", getColorForItem(instance.getTemplate().getId()));
		geom.setMaterial(mat);
		
		_node.attachChild(geom);
		_node.setLocalTranslation(position.x, position.y + BASE_Y_OFFSET, position.z);
	}
	
	/**
	 * Updates the bob and spin animation and decrements the lifetime timer.
	 * @param tpf time per frame in seconds
	 */
	public void update(float tpf)
	{
		_animTimer += tpf;
		_lifetime -= tpf;
		
		// Sine wave bob.
		final float bobOffset = FastMath.sin(_animTimer * BOB_SPEED) * BOB_AMPLITUDE;
		_node.setLocalTranslation(_position.x, _position.y + BASE_Y_OFFSET + bobOffset, _position.z);
		
		// Slow Y-axis spin.
		final float angle = _animTimer * SPIN_SPEED;
		_node.setLocalRotation(new com.jme3.math.Quaternion().fromAngleAxis(angle, Vector3f.UNIT_Y));
	}
	
	/**
	 * Returns true if this drop has expired (lifetime <= 0).
	 * @return true if expired
	 */
	public boolean isExpired()
	{
		return _lifetime <= 0;
	}
	
	/**
	 * Returns the item stack this drop represents.
	 * @return the item instance
	 */
	public ItemInstance getInstance()
	{
		return _instance;
	}
	
	/**
	 * Returns the world position where this drop was spawned.
	 * @return the ground-level position
	 */
	public Vector3f getPosition()
	{
		return _position;
	}
	
	/**
	 * Returns the scene node holding the visual geometry.
	 * @return the node
	 */
	public Node getNode()
	{
		return _node;
	}
	
	/**
	 * Returns the remaining lifetime in seconds.
	 * @return seconds until despawn
	 */
	public float getLifetime()
	{
		return _lifetime;
	}
	
	/**
	 * Returns a representative color for the given item ID.<br>
	 * Makes dropped items visually distinguishable at a glance.
	 * @param itemId the item ID
	 * @return the display color
	 */
	private static ColorRGBA getColorForItem(String itemId)
	{
		switch (itemId)
		{
			case "cooked_meat":
			{
				return new ColorRGBA(0.6f, 0.3f, 0.15f, 1.0f); // Brown.
			}
			case "iron_nugget":
			{
				return new ColorRGBA(0.75f, 0.75f, 0.75f, 1.0f); // Silver.
			}
			case "stone_shard":
			{
				return new ColorRGBA(0.5f, 0.5f, 0.5f, 1.0f); // Gray.
			}
			case "wood_plank":
			{
				return new ColorRGBA(0.7f, 0.5f, 0.25f, 1.0f); // Tan.
			}
			case "health_potion":
			{
				return new ColorRGBA(1.0f, 0.2f, 0.3f, 1.0f); // Red-pink.
			}
			case "berries":
			{
				return new ColorRGBA(0.8f, 0.1f, 0.3f, 1.0f); // Berry red.
			}
			default:
			{
				return new ColorRGBA(0.9f, 0.9f, 0.2f, 1.0f); // Yellow fallback.
			}
		}
	}
}
