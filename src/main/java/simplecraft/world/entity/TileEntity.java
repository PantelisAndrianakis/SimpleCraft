package simplecraft.world.entity;

import com.jme3.scene.Node;

import simplecraft.player.PlayerController;
import simplecraft.util.Vector3i;
import simplecraft.world.Block;
import simplecraft.world.Block.Face;
import simplecraft.world.World;

/**
 * Base class for blocks that carry extra data and behavior beyond the block grid.<br>
 * Subclasses (campfire, torch, furnace, chest, etc.) override hooks for interaction,<br>
 * placement, removal, and per-frame updates.<br>
 * <br>
 * Each tile entity stores its world block position, block type, attachment face,<br>
 * and an optional visual node for particle effects or other scene attachments.<br>
 * The visual node is managed by {@link TileEntityManager} — it is attached to<br>
 * the scene on registration and detached on removal.<br>
 * <br>
 * The {@code attachedFace} field records which face of the neighboring solid block<br>
 * this tile entity is attached to. This is relevant for directional blocks such as<br>
 * torches, doors, windows, and trapdoors. Blocks without directional placement<br>
 * (e.g. campfire, chest) default to {@link Face#BOTTOM}.<br>
 * <br>
 * The {@code facing} field records which cardinal direction the block's front face<br>
 * points toward. Used by CHEST and FURNACE so the front texture (latch, opening)<br>
 * renders on the correct world face based on where the player stood when placing.<br>
 * Defaults to {@link Facing#NORTH} for backward compatibility with older saves.
 * @author Pantelis Andrianakis
 * @since March 8th 2026
 */
public abstract class TileEntity
{
	// ========================================================
	// Facing Enum.
	// ========================================================
	
	/**
	 * Cardinal direction a block's front face points toward.<br>
	 * NORTH = front texture on the Z+ face (default).<br>
	 * Used by CHEST, FURNACE, and CRAFTING_TABLE for directional placement.
	 */
	public enum Facing
	{
		NORTH,
		SOUTH,
		EAST,
		WEST
	}
	
	// ========================================================
	// Fields.
	// ========================================================
	
	/** World block coordinates of this tile entity. */
	protected final Vector3i _position;
	
	/** The block type this tile entity represents. */
	protected final Block _blockType;
	
	/**
	 * The face of the adjacent solid block this entity is attached to.<br>
	 * BOTTOM = resting on the block below (default for non-directional blocks).<br>
	 * NORTH/SOUTH/EAST/WEST = attached to the side of an adjacent block.<br>
	 * TOP = hanging from the block above (e.g. ceiling-mounted lantern).<br>
	 * <br>
	 * Used by directional blocks (torch, door, window) for rendering orientation<br>
	 * and by the block support system to remove the entity if the supporting block is broken.
	 */
	protected final Face _attachedFace;
	
	/**
	 * Which cardinal direction the block's front face points toward.<br>
	 * Defaults to NORTH for backward compatibility with saves that lack this field.<br>
	 * Only visually meaningful for blocks with a front texture (CHEST, FURNACE).<br>
	 * Stored in the base class for uniform serialization.
	 */
	protected Facing _facing = Facing.NORTH;
	
	/** Optional scene node for particles/visuals. Null if no visuals needed. */
	protected Node _visualNode;
	
	// ========================================================
	// Constructors.
	// ========================================================
	
	/**
	 * Create a new tile entity at the given world position with a specific attachment face.
	 * @param position world block coordinates
	 * @param blockType the block type this entity represents
	 * @param attachedFace the face of the neighboring block this entity is attached to
	 */
	public TileEntity(Vector3i position, Block blockType, Face attachedFace)
	{
		_position = position;
		_blockType = blockType;
		_attachedFace = attachedFace;
	}
	
	/**
	 * Create a new tile entity at the given world position with the default attachment (BOTTOM).<br>
	 * Convenience constructor for non-directional blocks like campfires and chests.
	 * @param position world block coordinates
	 * @param blockType the block type this entity represents
	 */
	public TileEntity(Vector3i position, Block blockType)
	{
		this(position, blockType, Face.BOTTOM);
	}
	
	// ========================================================
	// Lifecycle Hooks.
	// ========================================================
	
	/**
	 * Called when this tile entity's block is placed in the world.<br>
	 * Override to create particles, register light sources, etc.
	 * @param world the game world
	 */
	public void onPlaced(World world)
	{
		// Default: no-op.
	}
	
	/**
	 * Called when this tile entity's block is broken/removed from the world.<br>
	 * Override to clean up particles, remove light sources, etc.
	 * @param world the game world
	 */
	public void onRemoved(World world)
	{
		// Default: no-op.
	}
	
	/**
	 * Called when the player right-clicks this tile entity's block.<br>
	 * Override to open GUIs, set respawn points, etc.
	 * @param player the player controller
	 * @param world the game world (for light/entity operations)
	 */
	public void onInteract(PlayerController player, World world)
	{
		// Default: no-op.
	}
	
	/**
	 * Per-frame update. Override for behavior that ticks over time<br>
	 * (e.g., furnace smelting timer, animated effects).
	 * @param tpf time per frame in seconds
	 */
	public void update(float tpf)
	{
		// Default: no-op.
	}
	
	// ========================================================
	// Serialization.
	// ========================================================
	
	/**
	 * Serializes this tile entity to a key=value string for saving.<br>
	 * Base implementation writes type, position, attached face, and facing direction.<br>
	 * Subclasses append extra fields.
	 * @return serialized string representation
	 */
	public String serialize()
	{
		final StringBuilder sb = new StringBuilder();
		sb.append("type=").append(_blockType.name()).append('\n');
		sb.append("x=").append(_position.x).append('\n');
		sb.append("y=").append(_position.y).append('\n');
		sb.append("z=").append(_position.z).append('\n');
		sb.append("attachedFace=").append(_attachedFace.name()).append('\n');
		sb.append("facing=").append(_facing.name());
		return sb.toString();
	}
	
	/**
	 * Factory method to reconstruct a tile entity from serialized data.<br>
	 * Parses the type field and delegates to the appropriate subclass deserializer.
	 * @param data the serialized key=value string
	 * @return the reconstructed tile entity, or null if the data is invalid
	 */
	public static TileEntity deserialize(String data)
	{
		if (data == null || data.isEmpty())
		{
			return null;
		}
		
		// Parse key=value pairs.
		String type = null;
		int x = 0;
		int y = 0;
		int z = 0;
		boolean activated = false;
		String attachedFace = "BOTTOM";
		String facing = "NORTH";
		
		for (String line : data.split("\n"))
		{
			final int eq = line.indexOf('=');
			if (eq < 0)
			{
				continue;
			}
			
			final String key = line.substring(0, eq).trim();
			final String value = line.substring(eq + 1).trim();
			
			switch (key)
			{
				case "type":
				{
					type = value;
					break;
				}
				case "x":
				{
					x = Integer.parseInt(value);
					break;
				}
				case "y":
				{
					y = Integer.parseInt(value);
					break;
				}
				case "z":
				{
					z = Integer.parseInt(value);
					break;
				}
				case "activated":
				{
					activated = Boolean.parseBoolean(value);
					break;
				}
				case "attachedFace":
				{
					attachedFace = value;
					break;
				}
				case "facing":
				{
					facing = value;
					break;
				}
			}
		}
		
		if (type == null)
		{
			return null;
		}
		
		final Vector3i pos = new Vector3i(x, y, z);
		final Face face = Face.valueOf(attachedFace);
		final Facing facingDir = Facing.valueOf(facing);
		
		switch (type)
		{
			case "CAMPFIRE":
			{
				final CampfireTileEntity campfire = new CampfireTileEntity(pos);
				campfire.setActivated(activated);
				campfire.setFacing(facingDir);
				return campfire;
			}
			case "TORCH":
			{
				final TorchTileEntity torch = new TorchTileEntity(pos, face);
				torch.setFacing(facingDir);
				return torch;
			}
			case "CHEST":
			case "CRAFTING_TABLE":
			case "FURNACE":
			{
				final PlaceholderTileEntity placeholder = new PlaceholderTileEntity(pos, Block.valueOf(type));
				placeholder.setFacing(facingDir);
				return placeholder;
			}
			default:
			{
				System.err.println("Unknown tile entity type: " + type);
				return null;
			}
		}
	}
	
	// ========================================================
	// Accessors.
	// ========================================================
	
	/**
	 * Returns the world block coordinates of this tile entity.
	 */
	public Vector3i getPosition()
	{
		return _position;
	}
	
	/**
	 * Returns the block type this tile entity represents.
	 */
	public Block getBlockType()
	{
		return _blockType;
	}
	
	/**
	 * Returns the face of the adjacent solid block this entity is attached to.<br>
	 * BOTTOM for floor-placed, NORTH/SOUTH/EAST/WEST for wall-mounted,<br>
	 * TOP for ceiling-mounted blocks.
	 */
	public Face getAttachedFace()
	{
		return _attachedFace;
	}
	
	/**
	 * Returns which cardinal direction the block's front face points toward.<br>
	 * NORTH means the front texture renders on the NORTH (Z+) face.
	 */
	public Facing getFacing()
	{
		return _facing;
	}
	
	/**
	 * Sets which cardinal direction the block's front face points toward.
	 * @param facing the facing direction
	 */
	public void setFacing(Facing facing)
	{
		_facing = facing;
	}
	
	/**
	 * Returns the world coordinates of the solid block this entity is attached to.<br>
	 * Used by the block support system to detect when the supporting block is broken.
	 */
	public Vector3i getSupportBlockPosition()
	{
		switch (_attachedFace)
		{
			case BOTTOM: // Floor: support is the block below.
			{
				return new Vector3i(_position.x, _position.y - 1, _position.z);
			}
			case TOP: // Ceiling: support is the block above.
			{
				return new Vector3i(_position.x, _position.y + 1, _position.z);
			}
			case NORTH: // North face: support block is to the north (+Z).
			{
				return new Vector3i(_position.x, _position.y, _position.z + 1);
			}
			case SOUTH: // South face: support block is to the south (-Z).
			{
				return new Vector3i(_position.x, _position.y, _position.z - 1);
			}
			case EAST: // East face: support block is to the east (+X).
			{
				return new Vector3i(_position.x + 1, _position.y, _position.z);
			}
			case WEST: // West face: support block is to the west (-X).
			{
				return new Vector3i(_position.x - 1, _position.y, _position.z);
			}
			default:
			{
				return new Vector3i(_position.x, _position.y - 1, _position.z);
			}
		}
	}
	
	/**
	 * Returns the visual node for particle effects, or null if none.
	 */
	public Node getVisualNode()
	{
		return _visualNode;
	}
}
