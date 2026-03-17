package simplecraft.world.entity;

import simplecraft.player.PlayerController;
import simplecraft.util.Vector3i;
import simplecraft.world.Block;
import simplecraft.world.World;

/**
 * Tile entity for the Crafting Table block.<br>
 * The crafting table itself has no persistent state — it simply triggers the<br>
 * crafting UI when the player right-clicks it. Screen opening is handled by<br>
 * {@link simplecraft.player.BlockInteraction}, which detects this entity type<br>
 * and opens the {@link simplecraft.player.CraftingScreen}.<br>
 * <br>
 * Replaces the former {@link PlaceholderTileEntity} usage for CRAFTING_TABLE blocks.
 * @author Pantelis Andrianakis
 * @since March 17th 2026
 */
public class CraftingTableTileEntity extends TileEntity
{
	/**
	 * Creates a new crafting table tile entity at the given position.
	 * @param position world block coordinates
	 */
	public CraftingTableTileEntity(Vector3i position)
	{
		super(position, Block.CRAFTING_TABLE);
	}
	
	@Override
	public void onInteract(PlayerController player, World world)
	{
		// Crafting UI opening is handled externally by BlockInteraction,
		// which checks for CraftingTableTileEntity and opens CraftingScreen.
		// No internal state to toggle or persist.
	}
	
	@Override
	public String serialize()
	{
		// Reuse base serialization — no extra fields to save.
		return super.serialize();
	}
}
