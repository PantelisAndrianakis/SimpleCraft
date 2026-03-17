package simplecraft.world.entity;

import simplecraft.player.PlayerController;
import simplecraft.ui.MessageManager;
import simplecraft.util.Vector3i;
import simplecraft.world.Block;
import simplecraft.world.World;

/**
 * Placeholder tile entity for blocks that are tile entities but not yet functional.<br>
 * Used for CHEST, CRAFTING_TABLE and FURNACE until their full behavior is wired.<br>
 * Right-clicking shows a "Not yet functional" message.
 * @author Pantelis Andrianakis
 * @since March 8th 2026
 */
public class PlaceholderTileEntity extends TileEntity
{
	/**
	 * Creates a placeholder tile entity at the given position.
	 * @param position world block coordinates
	 * @param blockType the block type (CHEST, CRAFTING_TABLE, FURNACE)
	 */
	public PlaceholderTileEntity(Vector3i position, Block blockType)
	{
		super(position, blockType);
	}
	
	@Override
	public void onInteract(PlayerController player, World world)
	{
		final String rawName = _blockType.name().replace('_', ' ').toLowerCase();
		final String displayName = Character.toUpperCase(rawName.charAt(0)) + rawName.substring(1);
		MessageManager.show(displayName + " is not yet functional");
		System.out.println("Interacted with " + _blockType.name() + " at [" + _position.x + ", " + _position.y + ", " + _position.z + "] - not yet functional.");
	}
	
	@Override
	public String serialize()
	{
		final StringBuilder sb = new StringBuilder();
		sb.append("type=").append(_blockType.name()).append('\n');
		sb.append("x=").append(_position.x).append('\n');
		sb.append("y=").append(_position.y).append('\n');
		sb.append("z=").append(_position.z);
		return sb.toString();
	}
}
