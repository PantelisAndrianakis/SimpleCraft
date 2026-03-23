package simplecraft.item;

/**
 * Categorizes items by their primary function.
 * @author Pantelis Andrianakis
 * @since March 13th 2026
 */
public enum ItemType
{
	/** Placeable block items (dirt, stone, wood, etc). */
	BLOCK,
	
	/** Block-breaking tools (pickaxe, axe, shovel). Can be used as a weapon at lower damage. */
	TOOL,
	
	/** Combat weapons (swords). No tool affinity bonus for block breaking. */
	WEAPON,
	
	/** Armor equipment. */
	ARMOR,
	
	/** Consumable items that restore health (potions, food). */
	CONSUMABLE,
	
	/** Crafting materials that cannot be placed or consumed directly. */
	MATERIAL
}
