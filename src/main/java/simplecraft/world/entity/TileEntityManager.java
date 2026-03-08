package simplecraft.world.entity;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.jme3.scene.Node;

import simplecraft.util.Vector3i;
import simplecraft.world.World;
import simplecraft.world.entity.TileEntity.Facing;

/**
 * Central registry for all active tile entities in the world.<br>
 * Maintains a map from packed world position to {@link TileEntity} instances<br>
 * and a scene node for visual attachments (particle effects, etc.).<br>
 * <br>
 * Tile entities are registered when their block is placed and removed when<br>
 * their block is broken. The manager updates all entities each frame and<br>
 * provides serialization/deserialization for save/load.
 * @author Pantelis Andrianakis
 * @since March 8th 2026
 */
public class TileEntityManager
{
	/** Map of packed position → tile entity. */
	private final Map<Long, TileEntity> _entities = new ConcurrentHashMap<>();
	
	/** Scene node that holds all tile entity visual nodes (particles, etc.). */
	private final Node _node = new Node("TileEntities");
	
	// ========================================================
	// Position Packing.
	// ========================================================
	
	/**
	 * Packs world coordinates into a single long key.<br>
	 * Supports X/Z in range [-8388608, 8388607] and Y in range [0, 65535].
	 */
	public static long packPosition(int x, int y, int z)
	{
		return ((long) (x & 0xFFFFFF) << 40) | ((long) (y & 0xFFFF) << 24) | ((long) (z & 0xFFFFFF));
	}
	
	/**
	 * Packs a Vector3i position into a single long key.
	 */
	public static long packPosition(Vector3i pos)
	{
		return packPosition(pos.x, pos.y, pos.z);
	}
	
	// ========================================================
	// Registration.
	// ========================================================
	
	/**
	 * Registers a tile entity and attaches its visual node to the scene.<br>
	 * If a tile entity already exists at this position, it is removed first.
	 * @param entity the tile entity to register
	 */
	public void register(TileEntity entity)
	{
		final long key = packPosition(entity.getPosition());
		
		// Remove existing entity at this position if any.
		final TileEntity existing = _entities.remove(key);
		if (existing != null && existing.getVisualNode() != null)
		{
			_node.detachChild(existing.getVisualNode());
		}
		
		_entities.put(key, entity);
		
		// Attach visual node if present.
		if (entity.getVisualNode() != null)
		{
			_node.attachChild(entity.getVisualNode());
		}
	}
	
	/**
	 * Removes the tile entity at the given position and detaches its visual node.
	 * @param pos the world block coordinates
	 * @return the removed tile entity, or null if none existed
	 */
	public TileEntity remove(Vector3i pos)
	{
		final long key = packPosition(pos);
		final TileEntity entity = _entities.remove(key);
		if (entity != null && entity.getVisualNode() != null)
		{
			_node.detachChild(entity.getVisualNode());
		}
		return entity;
	}
	
	/**
	 * Removes the tile entity at the given world coordinates.
	 * @return the removed tile entity, or null if none existed
	 */
	public TileEntity remove(int x, int y, int z)
	{
		return remove(new Vector3i(x, y, z));
	}
	
	/**
	 * Returns the tile entity at the given position, or null if none exists.
	 */
	public TileEntity get(Vector3i pos)
	{
		return _entities.get(packPosition(pos));
	}
	
	/**
	 * Returns the tile entity at the given world coordinates, or null if none exists.
	 */
	public TileEntity get(int x, int y, int z)
	{
		return _entities.get(packPosition(x, y, z));
	}
	
	/**
	 * Returns the facing direction of the tile entity at the given world coordinates.<br>
	 * Convenience method for the mesh builder to quickly look up orientation.<br>
	 * Returns {@link Facing#NORTH} (default) if no tile entity exists at the position.
	 * @param x world X coordinate
	 * @param y world Y coordinate
	 * @param z world Z coordinate
	 * @return the facing direction, or NORTH if no entity found
	 */
	public Facing getFacing(int x, int y, int z)
	{
		final TileEntity entity = _entities.get(packPosition(x, y, z));
		if (entity != null)
		{
			return entity.getFacing();
		}
		
		return Facing.NORTH;
	}
	
	/**
	 * Returns all registered tile entities.
	 */
	public Collection<TileEntity> getAll()
	{
		return _entities.values();
	}
	
	/**
	 * Returns the scene node that holds all tile entity visual nodes.<br>
	 * Attach this to rootNode during world setup.
	 */
	public Node getNode()
	{
		return _node;
	}
	
	// ========================================================
	// Update.
	// ========================================================
	
	/**
	 * Updates all tile entities. Called each frame from PlayingState.
	 * @param tpf time per frame in seconds
	 */
	public void update(float tpf)
	{
		for (TileEntity entity : _entities.values())
		{
			entity.update(tpf);
		}
	}
	
	// ========================================================
	// Serialization.
	// ========================================================
	
	/** Separator between individual tile entity records in serialized data. */
	private static final String RECORD_SEPARATOR = "\n---\n";
	
	/**
	 * Serializes all tile entities to a single string for saving.
	 * @return serialized data containing all tile entities
	 */
	public String serializeAll()
	{
		final StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (TileEntity entity : _entities.values())
		{
			if (!first)
			{
				sb.append(RECORD_SEPARATOR);
			}
			sb.append(entity.serialize());
			first = false;
		}
		return sb.toString();
	}
	
	/**
	 * Reconstructs tile entities from serialized save data.<br>
	 * Clears existing entities and replaces them with deserialized ones.
	 * @param data the serialized string from {@link #serializeAll()}
	 * @param world the game world (passed to onPlaced for visual/light setup)
	 */
	public void deserializeAll(String data, World world)
	{
		// Clear existing.
		_node.detachAllChildren();
		_entities.clear();
		
		if (data == null || data.isEmpty())
		{
			return;
		}
		
		final String[] records = data.split(RECORD_SEPARATOR);
		for (String record : records)
		{
			final String trimmed = record.trim();
			if (trimmed.isEmpty())
			{
				continue;
			}
			
			final TileEntity entity = TileEntity.deserialize(trimmed);
			if (entity != null)
			{
				entity.onPlaced(world);
				register(entity);
			}
		}
	}
	
	// ========================================================
	// Cleanup.
	// ========================================================
	
	/**
	 * Removes all tile entities and detaches all visual nodes.<br>
	 * Called during world teardown.
	 */
	public void cleanup()
	{
		_node.detachAllChildren();
		_entities.clear();
	}
}
