package simplecraft.item;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.jme3.asset.AssetManager;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

import simplecraft.audio.AudioManager;

/**
 * Manages all dropped items in the world.<br>
 * <br>
 * Dropped items are small colored cubes that bob and spin at the position where<br>
 * an enemy died. Each frame, the manager updates animations, checks player proximity<br>
 * for automatic pickup, and removes expired drops.<br>
 * <br>
 * A maximum of {@link #MAX_ACTIVE_DROPS} drops can exist simultaneously. When the<br>
 * limit is reached, the oldest drop is removed to make room for a new one.<br>
 * <br>
 * Pickup range is {@link #PICKUP_RANGE} blocks — when the player walks within<br>
 * this distance, the drop is automatically added to the player's inventory.<br>
 * If the inventory is full, the drop remains on the ground.
 * @author Pantelis Andrianakis
 * @since March 15th 2026
 */
public class DropManager
{
	/** Maximum distance for automatic item pickup (blocks). */
	private static final float PICKUP_RANGE = 1.5f;
	
	/** Squared pickup range for distance comparison. */
	private static final float PICKUP_RANGE_SQ = PICKUP_RANGE * PICKUP_RANGE;
	
	/** Maximum number of active drops in the world. */
	private static final int MAX_ACTIVE_DROPS = 50;
	
	/**
	 * Sound effect path for item pickup.<br>
	 * Add this constant to {@link AudioManager} if a dedicated pickup sound is desired.<br>
	 * Falls back silently if the asset does not exist.
	 */
	private static final String SFX_ITEM_PICKUP = "Sounds/sfx/item_pickup.ogg";
	
	/** All currently active dropped items. */
	private final List<DroppedItem> _drops = new ArrayList<>();
	
	/** Scene node that holds all drop visuals. */
	private final Node _dropNode;
	
	/** Asset manager for creating drop visuals. */
	private final AssetManager _assetManager;
	
	/** Audio manager for pickup sound effect. */
	private final AudioManager _audioManager;
	
	/**
	 * Creates a new drop manager.
	 * @param assetManager the asset manager for creating materials
	 * @param audioManager the audio manager for pickup sounds
	 */
	public DropManager(AssetManager assetManager, AudioManager audioManager)
	{
		_assetManager = assetManager;
		_audioManager = audioManager;
		_dropNode = new Node("DroppedItems");
	}
	
	/**
	 * Spawns a dropped item at the given world position.<br>
	 * If the maximum drop count is reached, the oldest drop is removed first.
	 * @param position the world position to spawn at (typically enemy death position)
	 * @param instance the item stack to drop
	 */
	public void spawnDrop(Vector3f position, ItemInstance instance)
	{
		if (instance == null || instance.isEmpty())
		{
			return;
		}
		
		// Enforce maximum active drops — remove the oldest if at capacity.
		while (_drops.size() >= MAX_ACTIVE_DROPS)
		{
			final DroppedItem oldest = _drops.remove(0);
			_dropNode.detachChild(oldest.getNode());
		}
		
		final DroppedItem drop = new DroppedItem(instance, position, _assetManager);
		_drops.add(drop);
		_dropNode.attachChild(drop.getNode());
		
		System.out.println("DropManager: Spawned " + instance.getCount() + "x " + instance.getTemplate().getDisplayName() + " at [" + String.format("%.1f", position.x) + ", " + String.format("%.1f", position.y) + ", " + String.format("%.1f", position.z) + "]");
	}
	
	/**
	 * Per-frame update. Animates drops, checks pickup range, and removes expired drops.
	 * @param playerPos the player's current world position
	 * @param inventory the player's inventory for pickup attempts
	 * @param tpf time per frame in seconds
	 */
	public void update(Vector3f playerPos, Inventory inventory, float tpf)
	{
		final Iterator<DroppedItem> iterator = _drops.iterator();
		while (iterator.hasNext())
		{
			final DroppedItem drop = iterator.next();
			
			// Update animation.
			drop.update(tpf);
			
			// Check for expiration.
			if (drop.isExpired())
			{
				_dropNode.detachChild(drop.getNode());
				iterator.remove();
				continue;
			}
			
			// Check pickup range (3D distance).
			final float dx = playerPos.x - drop.getPosition().x;
			final float dy = playerPos.y - drop.getPosition().y;
			final float dz = playerPos.z - drop.getPosition().z;
			final float distSq = dx * dx + dy * dy + dz * dz;
			
			if (distSq <= PICKUP_RANGE_SQ)
			{
				// Attempt to add to inventory.
				final boolean added = inventory.addItem(drop.getInstance());
				if (added)
				{
					_dropNode.detachChild(drop.getNode());
					iterator.remove();
					
					// Play pickup sound.
					if (_audioManager != null)
					{
						_audioManager.playSfx(SFX_ITEM_PICKUP);
					}
					
					System.out.println("Picked up " + drop.getInstance().getCount() + "x " + drop.getInstance().getTemplate().getDisplayName());
				}
				// If inventory is full, leave the drop on the ground.
			}
		}
	}
	
	/**
	 * Returns the scene node holding all drop visuals.<br>
	 * Attach this to the root node during initialization.
	 * @return the drop node
	 */
	public Node getNode()
	{
		return _dropNode;
	}
	
	/**
	 * Removes all active drops from the world. Called during world teardown.
	 */
	public void cleanup()
	{
		for (DroppedItem drop : _drops)
		{
			_dropNode.detachChild(drop.getNode());
		}
		_drops.clear();
	}
	
	/**
	 * Returns the number of currently active drops.
	 * @return active drop count
	 */
	public int getActiveCount()
	{
		return _drops.size();
	}
}
