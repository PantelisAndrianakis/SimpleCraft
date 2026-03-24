package simplecraft.item;

import simplecraft.world.Block;
import simplecraft.world.Block.ToolType;

/**
 * Defines what an item IS (template/prototype, not an instance).<br>
 * Created via static factory methods: {@link #block}, {@link #weapon}, {@link #tool}, {@link #consumable}, {@link #material}, {@link #armor}.
 * @author Pantelis Andrianakis
 * @since March 13th 2026
 */
public class ItemTemplate
{
	private final String _id;
	private final String _displayName;
	private final ItemType _type;
	private final int _maxStackSize;
	private final Block _placesBlock;
	private final float _weaponDamage;
	private final float _weaponSpeed;
	private final float _healAmount;
	private final ToolType _toolType;
	private final int _maxDurability;
	private final ArmorSlot _armorSlot;
	private final float _damageReduction;
	private final float _viewmodelScale;
	
	/**
	 * Private constructor. Use static factory methods to create items.
	 */
	private ItemTemplate(String id, String displayName, ItemType type, int maxStackSize, Block placesBlock, float weaponDamage, float weaponSpeed, float healAmount, ToolType toolType, int maxDurability, ArmorSlot armorSlot, float damageReduction, float viewmodelScale)
	{
		_id = id;
		_displayName = displayName;
		_type = type;
		_maxStackSize = maxStackSize;
		_placesBlock = placesBlock;
		_weaponDamage = weaponDamage;
		_weaponSpeed = weaponSpeed;
		_healAmount = healAmount;
		_toolType = toolType;
		_maxDurability = maxDurability;
		_armorSlot = armorSlot;
		_damageReduction = damageReduction;
		_viewmodelScale = viewmodelScale;
	}
	
	// ========================================================
	// Static Factory Methods.
	// ========================================================
	
	/**
	 * Creates a placeable block item. Stack size 64, no durability.
	 * @param id unique key (e.g. "dirt", "stone")
	 * @param displayName human-readable name
	 * @param placesBlock the Block enum value this item places
	 * @param viewmodelScale first-person sprite scale (1.0 = full size)
	 * @return new block ItemTemplate
	 */
	public static ItemTemplate block(String id, String displayName, Block placesBlock, float viewmodelScale)
	{
		return new ItemTemplate(id, displayName, ItemType.BLOCK, 64, placesBlock, 0.0f, 0.0f, 0.0f, ToolType.NONE, 0, null, 0.0f, viewmodelScale);
	}
	
	/**
	 * Creates a combat weapon (sword). Stack size 1, has durability. No tool affinity.
	 * @param id unique key (e.g. "stone_sword")
	 * @param displayName human-readable name
	 * @param damage attack damage
	 * @param speed attack cooldown in seconds
	 * @param durability maximum durability
	 * @param viewmodelScale first-person sprite scale (1.0 = full size)
	 * @return new weapon ItemTemplate
	 */
	public static ItemTemplate weapon(String id, String displayName, float damage, float speed, int durability, float viewmodelScale)
	{
		return new ItemTemplate(id, displayName, ItemType.WEAPON, 1, null, damage, speed, 0.0f, ToolType.NONE, durability, null, 0.0f, viewmodelScale);
	}
	
	/**
	 * Creates a block-breaking tool (pickaxe, axe, shovel). Stack size 1, has durability.<br>
	 * Tools have a tool affinity that speeds up breaking matching blocks. Can also be used as a weapon at lower damage.
	 * @param id unique key (e.g. "wood_pickaxe")
	 * @param displayName human-readable name
	 * @param toolType the ToolType affinity (PICKAXE, AXE, SHOVEL)
	 * @param damage weapon damage when used in combat
	 * @param speed attack cooldown in seconds
	 * @param durability maximum durability
	 * @param viewmodelScale first-person sprite scale (1.0 = full size)
	 * @return new tool ItemTemplate
	 */
	public static ItemTemplate tool(String id, String displayName, ToolType toolType, float damage, float speed, int durability, float viewmodelScale)
	{
		return new ItemTemplate(id, displayName, ItemType.TOOL, 1, null, damage, speed, 0.0f, toolType, durability, null, 0.0f, viewmodelScale);
	}
	
	/**
	 * Creates a consumable item that restores health. Stack size 64, no durability.
	 * @param id unique key (e.g. "health_potion")
	 * @param displayName human-readable name
	 * @param healAmount health restored on use
	 * @param viewmodelScale first-person sprite scale (1.0 = full size)
	 * @return new consumable ItemTemplate
	 */
	public static ItemTemplate consumable(String id, String displayName, float healAmount, float viewmodelScale)
	{
		return new ItemTemplate(id, displayName, ItemType.CONSUMABLE, 64, null, 0.0f, 0.0f, healAmount, ToolType.NONE, 0, null, 0.0f, viewmodelScale);
	}
	
	/**
	 * Creates a consumable item with a custom stack size. No durability.
	 * @param id unique key (e.g. "dragon_orb")
	 * @param displayName human-readable name
	 * @param healAmount health restored on use (0 for non-healing consumables)
	 * @param maxStackSize maximum stack size
	 * @param viewmodelScale first-person sprite scale (1.0 = full size)
	 * @return new consumable ItemTemplate
	 */
	public static ItemTemplate consumable(String id, String displayName, float healAmount, int maxStackSize, float viewmodelScale)
	{
		return new ItemTemplate(id, displayName, ItemType.CONSUMABLE, maxStackSize, null, 0.0f, 0.0f, healAmount, ToolType.NONE, 0, null, 0.0f, viewmodelScale);
	}
	
	/**
	 * Creates a crafting material. Stack size 64, no durability, not placeable.
	 * @param id unique key (e.g. "wood_plank")
	 * @param displayName human-readable name
	 * @param viewmodelScale first-person sprite scale (1.0 = full size)
	 * @return new material ItemTemplate
	 */
	public static ItemTemplate material(String id, String displayName, float viewmodelScale)
	{
		return new ItemTemplate(id, displayName, ItemType.MATERIAL, 64, null, 0.0f, 0.0f, 0.0f, ToolType.NONE, 0, null, 0.0f, viewmodelScale);
	}
	
	/**
	 * Creates an armor item. Stack size 1, has durability, reduces incoming damage.<br>
	 * Each armor piece occupies a specific slot (helmet, chestplate, pants, boots).
	 * @param id unique key (e.g. "iron_helmet")
	 * @param displayName human-readable name (e.g. "Iron Helmet")
	 * @param armorSlot which equipment slot this armor occupies
	 * @param damageReduction damage reduced per incoming hit (e.g. 1.0f)
	 * @param durability maximum durability (e.g. 300)
	 * @param viewmodelScale first-person sprite scale (1.0 = full size)
	 * @return new armor ItemTemplate
	 */
	public static ItemTemplate armor(String id, String displayName, ArmorSlot armorSlot, float damageReduction, int durability, float viewmodelScale)
	{
		return new ItemTemplate(id, displayName, ItemType.ARMOR, 1, null, 0.0f, 0.0f, 0.0f, ToolType.NONE, durability, armorSlot, damageReduction, viewmodelScale);
	}
	
	// ========================================================
	// Getters.
	// ========================================================
	
	/**
	 * Unique key for this item (e.g. "dirt", "stone_sword", "wood_pickaxe").
	 */
	public String getId()
	{
		return _id;
	}
	
	/**
	 * Human-readable display name (e.g. "Dirt", "Stone Sword").
	 */
	public String getDisplayName()
	{
		return _displayName;
	}
	
	public ItemType getType()
	{
		return _type;
	}
	
	/**
	 * Maximum stack size. 64 for blocks/materials/consumables, 1 for weapons/tools/armor.
	 */
	public int getMaxStackSize()
	{
		return _maxStackSize;
	}
	
	/**
	 * The block this item places. Only non-null for BLOCK type items.
	 */
	public Block getPlacesBlock()
	{
		return _placesBlock;
	}
	
	/**
	 * Attack damage for weapons and tools. 0 for non-combat items.
	 */
	public float getWeaponDamage()
	{
		return _weaponDamage;
	}
	
	/**
	 * Attack cooldown in seconds. 0 for non-combat items.
	 */
	public float getWeaponSpeed()
	{
		return _weaponSpeed;
	}
	
	/**
	 * Health restored when consumed. 0 for non-consumable items.
	 */
	public float getHealAmount()
	{
		return _healAmount;
	}
	
	/**
	 * Returns true if this item is a TOOL type (pickaxe, axe, shovel).<br>
	 * Tools have a tool affinity that speeds up breaking matching blocks.
	 * @return true for TOOL items, false for everything else
	 */
	public boolean hasTool()
	{
		return _type == ItemType.TOOL;
	}
	
	/**
	 * Tool affinity type for TOOL items (PICKAXE, AXE, SHOVEL). NONE for everything else.
	 */
	public ToolType getToolType()
	{
		return _toolType;
	}
	
	/**
	 * Maximum durability for weapons, tools and armor. 0 for non-durability items.
	 */
	public int getMaxDurability()
	{
		return _maxDurability;
	}
	
	/**
	 * The armor equipment slot this item occupies (HELMET, CHESTPLATE, PANTS, BOOTS).<br>
	 * Null for non-armor items.
	 */
	public ArmorSlot getArmorSlot()
	{
		return _armorSlot;
	}
	
	/**
	 * Damage reduction per incoming hit when this armor piece is worn (e.g. 1.0f).<br>
	 * 0 for non-armor items.
	 */
	public float getDamageReduction()
	{
		return _damageReduction;
	}
	
	/**
	 * Scale multiplier for the first-person viewmodel sprite.<br>
	 * 1.0 = full size, 0.7 = 30% smaller, etc. Configured per item in {@code ItemRegistry}.
	 */
	public float getViewmodelScale()
	{
		return _viewmodelScale;
	}
	
	@Override
	public String toString()
	{
		return "ItemTemplate[" + _id + " (" + _displayName + ") type=" + _type + "]";
	}
}
