package simplecraft.world.entity;

import simplecraft.player.PlayerController;
import simplecraft.util.Vector3i;
import simplecraft.world.Block;
import simplecraft.world.Block.Face;
import simplecraft.world.World;

/**
 * Tile entity for WINDOW blocks.<br>
 * Stores open/closed state and facing direction for FLAT_PANEL rendering.<br>
 * When opened, the panel slides from the center of the block to one edge.<br>
 * If an adjacent window or door exists on the attachment side, the panel<br>
 * flips to the opposite edge to create a natural double-window effect.<br>
 * <br>
 * Windows must be attached to a solid block's side face and are removed<br>
 * if that supporting block is broken (via the base class support system).<br>
 * <br>
 * Right-click toggles open/closed. Left-click breaks normally.
 * @author Pantelis Andrianakis
 * @since March 8th 2026
 */
public class WindowTileEntity extends TileEntity
{
	// ========================================================
	// Fields.
	// ========================================================
	
	/** Whether the window panel is currently open (slid to one side). */
	private boolean _open = false;
	
	/**
	 * Whether the panel opens to the opposite side of the attachment wall.<br>
	 * True when an adjacent WINDOW or DOOR block is detected behind this window<br>
	 * at the time of opening, creating a double-window effect where both panels<br>
	 * swing to opposite walls.
	 */
	private boolean _flippedOpen = false;
	
	// ========================================================
	// Constructor.
	// ========================================================
	
	/**
	 * Creates a new window tile entity at the given position attached to the specified face.
	 * @param position world block coordinates
	 * @param attachedFace the face of the solid block this window is attached to
	 */
	public WindowTileEntity(Vector3i position, Face attachedFace)
	{
		super(position, Block.WINDOW, attachedFace);
	}
	
	// ========================================================
	// Lifecycle Hooks.
	// ========================================================
	
	@Override
	public void onPlaced(World world)
	{
		// Windows have no visual effects on placement (no particles or lights).
	}
	
	@Override
	public void onRemoved(World world)
	{
		// Windows have no visual effects to clean up.
	}
	
	/**
	 * Toggles the window open or closed when the player right-clicks.<br>
	 * On opening: checks for an adjacent WINDOW or DOOR block on the attachment<br>
	 * side (behind the panel). If found, flips to the opposite edge.<br>
	 * On closing: resets flip state and returns the panel to center.<br>
	 * Triggers a region mesh rebuild for the visual change.
	 * @param player the player controller
	 * @param world the game world
	 */
	@Override
	public void onInteract(PlayerController player, World world)
	{
		_open = !_open;
		
		if (_open)
		{
			// Check for adjacent window/door on the swing side.
			// If found, flip direction so both panels swing away from each other.
			final Vector3i swingSide = getSwingSideAdjacentPosition();
			final Block swingBlock = world.getBlock(swingSide.x, swingSide.y, swingSide.z);
			
			_flippedOpen = swingBlock.isFlatPanel();
		}
		else
		{
			// Closing resets the flip — panel returns to the center of the block.
			_flippedOpen = false;
		}
		
		// Trigger mesh rebuild so the FLAT_PANEL quad updates its position.
		// Cannot use setBlockImmediate — the block type hasn't changed (still WINDOW),
		// so Region.setBlock skips the dirty flag. Force rebuild via markRegionDirtyAt.
		world.markRegionDirtyAt(_position.x, _position.y, _position.z);
		world.rebuildDirtyRegionsImmediate();
		
		System.out.println("Window " + (_open ? "opened" : "closed") + " at " + _position + (_flippedOpen ? " (flipped)" : ""));
	}
	
	// ========================================================
	// State Accessors.
	// ========================================================
	
	/**
	 * Returns whether the window is currently open.
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
	 * Returns whether the panel is flipped to the opposite side when open.<br>
	 * True when an adjacent panel was detected on the attachment side at open time.
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
	
	// ========================================================
	// Helpers.
	// ========================================================
	
	/**
	 * Returns the world position of the block adjacent to this window on the<br>
	 * swing-open side (the right side from the player's perspective looking at the front).<br>
	 * If another panel exists here, the window flips to the opposite side.
	 * @return world coordinates of the block on the swing side
	 */
	private Vector3i getSwingSideAdjacentPosition()
	{
		// Hinge side from the perspective of someone looking at the panel's front face:
		// NORTH facing (+Z front): hinge = +X
		// SOUTH facing (-Z front): hinge = -X
		// EAST facing (+X front): hinge = +Z
		// WEST facing (-X front): hinge = -Z
		switch (_facing)
		{
			case NORTH:
			{
				return new Vector3i(_position.x + 1, _position.y, _position.z);
			}
			case SOUTH:
			{
				return new Vector3i(_position.x - 1, _position.y, _position.z);
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
	 * Serializes this window tile entity to a key=value string.<br>
	 * Appends open and flippedOpen fields to the base serialization.
	 * @return serialized string
	 */
	@Override
	public String serialize()
	{
		final StringBuilder sb = new StringBuilder();
		sb.append(super.serialize());
		sb.append('\n').append("open=").append(_open);
		sb.append('\n').append("flippedOpen=").append(_flippedOpen);
		return sb.toString();
	}
}
