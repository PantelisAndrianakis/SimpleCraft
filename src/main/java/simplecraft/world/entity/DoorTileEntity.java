package simplecraft.world.entity;

import simplecraft.player.PlayerController;
import simplecraft.util.Vector3i;
import simplecraft.world.Block;
import simplecraft.world.Block.Face;
import simplecraft.world.World;

/**
 * Tile entity for DOOR_BOTTOM and DOOR_TOP blocks.<br>
 * A door occupies two vertical block spaces linked by partner positions.<br>
 * Stores open/closed state, facing direction, and partner coordinates<br>
 * for synchronized open/close of both halves.<br>
 * <br>
 * When opened, the FLAT_PANEL quad slides from center to one edge.<br>
 * If an adjacent door is detected on the attachment side, the panel<br>
 * flips to the opposite edge (double-door effect).<br>
 * <br>
 * Interacting with either half toggles both halves simultaneously.<br>
 * Breaking either half destroys both halves and removes both tile entities.<br>
 * If the supporting wall is broken, both halves drop.<br>
 * <br>
 * Right-click toggles open/closed. Left-click breaks normally.
 * @author Pantelis Andrianakis
 * @since March 8th 2026
 */
public class DoorTileEntity extends TileEntity
{
	// ========================================================
	// Fields.
	// ========================================================
	
	/** Whether the door panel is currently open (slid to one side). */
	private boolean _open = false;
	
	/**
	 * Whether the panel opens to the opposite side of the attachment wall.<br>
	 * True when an adjacent DOOR or WINDOW block is detected behind this door<br>
	 * at the time of opening, creating a double-door effect.
	 */
	private boolean _flippedOpen = false;
	
	/** World position of the other half of this door (top if this is bottom, bottom if this is top). */
	private Vector3i _partnerPos;
	
	/** True if this is the bottom half (DOOR_BOTTOM), false if this is the top half (DOOR_TOP). */
	private boolean _isBottom;
	
	// ========================================================
	// Constructor.
	// ========================================================
	
	/**
	 * Creates a new door tile entity at the given position.
	 * @param position world block coordinates of this half
	 * @param blockType DOOR_BOTTOM or DOOR_TOP
	 * @param attachedFace the face of the solid block this door is attached to
	 * @param partnerPos the world position of the other half
	 * @param isBottom true if this is the bottom half
	 */
	public DoorTileEntity(Vector3i position, Block blockType, Face attachedFace, Vector3i partnerPos, boolean isBottom)
	{
		super(position, blockType, attachedFace);
		_partnerPos = partnerPos;
		_isBottom = isBottom;
	}
	
	// ========================================================
	// Lifecycle Hooks.
	// ========================================================
	
	@Override
	public void onPlaced(World world)
	{
		// Doors have no visual effects on placement (no particles or lights).
	}
	
	@Override
	public void onRemoved(World world)
	{
		// Doors have no visual effects to clean up.
		// Partner destruction is handled by the interaction/breaking system.
	}
	
	/**
	 * Toggles the door open or closed when the player right-clicks either half.<br>
	 * Both halves are toggled simultaneously via the partner reference.<br>
	 * On opening: checks for an adjacent DOOR or WINDOW block on the attachment<br>
	 * side. If found, both halves flip to the opposite edge.<br>
	 * On closing: resets flip state on both halves.<br>
	 * Triggers region mesh rebuilds for both block positions.
	 * @param player the player controller
	 * @param world the game world
	 */
	@Override
	public void onInteract(PlayerController player, World world)
	{
		_open = !_open;
		
		if (_open)
		{
			// Check for adjacent door/window on the swing side.
			// If found, flip direction so both panels swing away from each other.
			final Vector3i swingSide = getSwingSideAdjacentPosition();
			final Block swingBlock = world.getBlock(swingSide.x, swingSide.y, swingSide.z);
			
			_flippedOpen = swingBlock.isFlatPanel();
		}
		else
		{
			_flippedOpen = false;
		}
		
		// Synchronize the partner half.
		if (_partnerPos != null)
		{
			final TileEntityManager manager = world.getTileEntityManager();
			final TileEntity partnerEntity = manager.get(_partnerPos);
			if (partnerEntity instanceof DoorTileEntity)
			{
				final DoorTileEntity partner = (DoorTileEntity) partnerEntity;
				partner._open = _open;
				partner._flippedOpen = _flippedOpen;
			}
		}
		
		// Trigger mesh rebuild for both halves.
		// Cannot use setBlockNoRebuild — the block types haven't changed (still DOOR_BOTTOM/TOP),
		// so Region.setBlock skips the dirty flag. Force rebuild via markRegionDirtyAt.
		world.markRegionDirtyAt(_position.x, _position.y, _position.z);
		if (_partnerPos != null)
		{
			world.markRegionDirtyAt(_partnerPos.x, _partnerPos.y, _partnerPos.z);
		}
		world.rebuildDirtyRegionsImmediate();
		
		System.out.println("Door " + (_open ? "opened" : "closed") + " at " + _position + (_flippedOpen ? " (flipped)" : ""));
	}
	
	/**
	 * Destroys both halves of the door when one half is broken.<br>
	 * Sets both positions to AIR, removes both tile entities, and triggers rebuild.<br>
	 * Called by the breaking system when either half reaches zero durability.
	 * @param world the game world
	 */
	public void destroyBothHalves(World world)
	{
		final TileEntityManager manager = world.getTileEntityManager();
		
		// Remove this half.
		world.setBlockNoRebuild(_position.x, _position.y, _position.z, Block.AIR);
		manager.remove(_position);
		
		// Remove partner half.
		if (_partnerPos != null)
		{
			world.setBlockNoRebuild(_partnerPos.x, _partnerPos.y, _partnerPos.z, Block.AIR);
			manager.remove(_partnerPos);
		}
		
		world.rebuildDirtyRegionsImmediate();
	}
	
	// ========================================================
	// State Accessors.
	// ========================================================
	
	/**
	 * Returns whether the door is currently open.
	 */
	public boolean isOpen()
	{
		return _open;
	}
	
	/**
	 * Sets the open state. Used during deserialization.
	 * @param open true if open
	 */
	public void setOpen(boolean open)
	{
		_open = open;
	}
	
	/**
	 * Returns whether the panel is flipped to the opposite side when open.
	 */
	public boolean isFlippedOpen()
	{
		return _flippedOpen;
	}
	
	/**
	 * Sets the flipped-open state. Used during deserialization.
	 * @param flippedOpen true if panel flips to the opposite wall
	 */
	public void setFlippedOpen(boolean flippedOpen)
	{
		_flippedOpen = flippedOpen;
	}
	
	/**
	 * Returns the world position of the other half of this door.
	 */
	public Vector3i getPartnerPos()
	{
		return _partnerPos;
	}
	
	/**
	 * Sets the partner position. Used during deserialization.
	 * @param partnerPos world coordinates of the other half
	 */
	public void setPartnerPos(Vector3i partnerPos)
	{
		_partnerPos = partnerPos;
	}
	
	/**
	 * Returns true if this is the bottom half of the door.
	 */
	public boolean isBottom()
	{
		return _isBottom;
	}
	
	/**
	 * Sets whether this is the bottom half. Used during deserialization.
	 * @param isBottom true if bottom half
	 */
	public void setIsBottom(boolean isBottom)
	{
		_isBottom = isBottom;
	}
	
	// ========================================================
	// Helpers.
	// ========================================================
	
	/**
	 * Returns the world position of the block adjacent to this door on the<br>
	 * swing-open side (the left side from the player's perspective looking at the front).<br>
	 * If another panel exists here, the door flips to the opposite side.
	 * @return world coordinates of the block on the swing side
	 */
	private Vector3i getSwingSideAdjacentPosition()
	{
		// Left side from the perspective of someone looking at the panel's front face:
		// NORTH facing (+Z front): left = -X
		// SOUTH facing (-Z front): left = +X
		// EAST facing (+X front): left = +Z
		// WEST facing (-X front): left = -Z
		switch (_facing)
		{
			case NORTH:
			{
				return new Vector3i(_position.x - 1, _position.y, _position.z);
			}
			case SOUTH:
			{
				return new Vector3i(_position.x + 1, _position.y, _position.z);
			}
			case EAST:
			{
				return new Vector3i(_position.x, _position.y, _position.z + 1);
			}
			case WEST:
			{
				return new Vector3i(_position.x, _position.y, _position.z - 1);
			}
			default:
			{
				return new Vector3i(_position.x, _position.y, _position.z);
			}
		}
	}
	
	// ========================================================
	// Serialization.
	// ========================================================
	
	/**
	 * Serializes this door tile entity to a key=value string.<br>
	 * Appends open, flippedOpen, isBottom, and partner position to the base serialization.
	 * @return serialized string
	 */
	@Override
	public String serialize()
	{
		final StringBuilder sb = new StringBuilder();
		sb.append(super.serialize());
		sb.append('\n').append("open=").append(_open);
		sb.append('\n').append("flippedOpen=").append(_flippedOpen);
		sb.append('\n').append("isBottom=").append(_isBottom);
		if (_partnerPos != null)
		{
			sb.append('\n').append("partnerX=").append(_partnerPos.x);
			sb.append('\n').append("partnerY=").append(_partnerPos.y);
			sb.append('\n').append("partnerZ=").append(_partnerPos.z);
		}
		return sb.toString();
	}
}
