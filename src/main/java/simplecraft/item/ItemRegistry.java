package simplecraft.item;

import java.util.HashMap;
import java.util.Map;

import simplecraft.world.Block;
import simplecraft.world.Block.ToolType;

/**
 * Central catalog of all items in the game.<br>
 * Call {@link #registerDefaults()} once at startup from SimpleCraft.simpleInitApp().
 * @author Pantelis Andrianakis
 * @since March 13th 2026
 */
public class ItemRegistry
{
	/** All registered items keyed by ID. */
	private static final Map<String, ItemTemplate> ITEMS = new HashMap<>();
	
	/** Maps each Block enum value to its corresponding item ID. */
	private static final Map<Block, String> BLOCK_TO_ITEM = new HashMap<>();
	
	/**
	 * Registers all default items. Called once at startup.
	 */
	public static void registerDefaults()
	{
		// ========================================================
		// Block Items.
		// ========================================================
		registerBlockItem(ItemTemplate.block("dirt", "Dirt", Block.DIRT), Block.DIRT);
		registerBlockItem(ItemTemplate.block("grass", "Grass Block", Block.GRASS), Block.GRASS);
		registerBlockItem(ItemTemplate.block("stone", "Stone", Block.STONE), Block.STONE);
		registerBlockItem(ItemTemplate.block("sand", "Sand", Block.SAND), Block.SAND);
		registerBlockItem(ItemTemplate.block("wood", "Wood", Block.WOOD), Block.WOOD);
		registerBlockItem(ItemTemplate.block("leaves", "Leaves", Block.LEAVES), Block.LEAVES);
		registerBlockItem(ItemTemplate.block("torch", "Torch", Block.TORCH), Block.TORCH);
		registerBlockItem(ItemTemplate.block("red_poppy", "Red Poppy", Block.RED_POPPY), Block.RED_POPPY);
		registerBlockItem(ItemTemplate.block("dandelion", "Dandelion", Block.DANDELION), Block.DANDELION);
		registerBlockItem(ItemTemplate.block("blue_orchid", "Blue Orchid", Block.BLUE_ORCHID), Block.BLUE_ORCHID);
		registerBlockItem(ItemTemplate.block("white_daisy", "White Daisy", Block.WHITE_DAISY), Block.WHITE_DAISY);
		registerBlockItem(ItemTemplate.block("campfire", "Campfire", Block.CAMPFIRE), Block.CAMPFIRE);
		registerBlockItem(ItemTemplate.block("chest", "Chest", Block.CHEST), Block.CHEST);
		registerBlockItem(ItemTemplate.block("crafting_table", "Crafting Table", Block.CRAFTING_TABLE), Block.CRAFTING_TABLE);
		registerBlockItem(ItemTemplate.block("furnace", "Furnace", Block.FURNACE), Block.FURNACE);
		registerBlockItem(ItemTemplate.block("iron_ore", "Iron Ore", Block.IRON_ORE), Block.IRON_ORE);
		registerBlockItem(ItemTemplate.block("glass", "Glass", Block.GLASS), Block.GLASS);
		registerBlockItem(ItemTemplate.block("window", "Window", Block.WINDOW), Block.WINDOW);
		registerBlockItem(ItemTemplate.block("door", "Door", Block.DOOR_BOTTOM), Block.DOOR_BOTTOM);
		
		// DOOR_TOP maps to same item as DOOR_BOTTOM (single "door" item).
		BLOCK_TO_ITEM.put(Block.DOOR_TOP, "door");
		
		// ========================================================
		// Weapons (Swords - no tool affinity, for combat).
		// ========================================================
		register(ItemTemplate.weapon("wood_sword", "Wood Sword", 4.0f, 0.5f, 60));
		register(ItemTemplate.weapon("stone_sword", "Stone Sword", 5.0f, 0.45f, 132));
		register(ItemTemplate.weapon("iron_sword", "Iron Sword", 7.0f, 0.4f, 250));
		register(ItemTemplate.weapon("gold_sword", "Gold Sword", 7.5f, 0.4f, 300));
		
		// ========================================================
		// Tools - Pickaxes (for STONE, IRON_ORE).
		// ========================================================
		register(ItemTemplate.tool("wood_pickaxe", "Wood Pickaxe", ToolType.PICKAXE, 2.0f, 0.5f, 60));
		register(ItemTemplate.tool("stone_pickaxe", "Stone Pickaxe", ToolType.PICKAXE, 2.5f, 0.45f, 132));
		register(ItemTemplate.tool("iron_pickaxe", "Iron Pickaxe", ToolType.PICKAXE, 3.0f, 0.4f, 250));
		register(ItemTemplate.tool("gold_pickaxe", "Gold Pickaxe", ToolType.PICKAXE, 3.5f, 0.4f, 300));
		
		// ========================================================
		// Tools - Axes (for WOOD, LEAVES, BERRY_BUSH).
		// ========================================================
		register(ItemTemplate.tool("wood_axe", "Wood Axe", ToolType.AXE, 3.0f, 0.6f, 60));
		register(ItemTemplate.tool("stone_axe", "Stone Axe", ToolType.AXE, 4.0f, 0.55f, 132));
		register(ItemTemplate.tool("iron_axe", "Iron Axe", ToolType.AXE, 5.0f, 0.5f, 250));
		register(ItemTemplate.tool("gold_axe", "Gold Axe", ToolType.AXE, 5.5f, 0.5f, 300));
		
		// ========================================================
		// Tools - Shovels (for DIRT, GRASS, SAND).
		// ========================================================
		register(ItemTemplate.tool("wood_shovel", "Wood Shovel", ToolType.SHOVEL, 1.5f, 0.5f, 60));
		register(ItemTemplate.tool("stone_shovel", "Stone Shovel", ToolType.SHOVEL, 2.0f, 0.45f, 132));
		register(ItemTemplate.tool("iron_shovel", "Iron Shovel", ToolType.SHOVEL, 2.5f, 0.4f, 250));
		register(ItemTemplate.tool("gold_shovel", "Gold Shovel", ToolType.SHOVEL, 3.0f, 0.4f, 300));
		
		// ========================================================
		// Armor - Iron Set (4 pieces, each reduces 1 damage, 250 durability).
		// ========================================================
		register(ItemTemplate.armor("iron_helmet", "Iron Helmet", ArmorSlot.HELMET, 1.0f, 250));
		register(ItemTemplate.armor("iron_chestplate", "Iron Chestplate", ArmorSlot.CHESTPLATE, 1.0f, 250));
		register(ItemTemplate.armor("iron_pants", "Iron Pants", ArmorSlot.PANTS, 1.0f, 250));
		register(ItemTemplate.armor("iron_boots", "Iron Boots", ArmorSlot.BOOTS, 1.0f, 250));
		
		// ========================================================
		// Armor - Gold Set (6 total reduction: 1+2+2+1, 300 durability).
		// ========================================================
		register(ItemTemplate.armor("gold_helmet", "Gold Helmet", ArmorSlot.HELMET, 1.0f, 300));
		register(ItemTemplate.armor("gold_chestplate", "Gold Chestplate", ArmorSlot.CHESTPLATE, 2.0f, 300));
		register(ItemTemplate.armor("gold_pants", "Gold Pants", ArmorSlot.PANTS, 2.0f, 300));
		register(ItemTemplate.armor("gold_boots", "Gold Boots", ArmorSlot.BOOTS, 1.0f, 300));
		
		// ========================================================
		// Consumables.
		// ========================================================
		register(ItemTemplate.consumable("health_potion", "Health Potion", 8.0f));
		register(ItemTemplate.consumable("meat", "Meat", 4.0f));
		register(ItemTemplate.consumable("berries", "Berries", 3.0f));
		
		// ========================================================
		// Materials.
		// ========================================================
		register(ItemTemplate.material("wood_plank", "Wood Plank"));
		register(ItemTemplate.material("stone_shard", "Stone Shard"));
		register(ItemTemplate.material("charcoal", "Charcoal"));
		register(ItemTemplate.material("iron_bar", "Iron Bar"));
		register(ItemTemplate.material("gold_bar", "Gold Bar"));
		
		// ========================================================
		// Special Orbs.
		// ========================================================
		register(ItemTemplate.consumable("recall_orb", "Recall Orb", 0.0f, 1));
		register(ItemTemplate.consumable("dragon_orb", "Dragon Orb", 0.0f, 1));
		register(ItemTemplate.consumable("shadow_orb", "Shadow Orb", 0.0f, 1));
		
		System.out.println("ItemRegistry: Registered " + ITEMS.size() + " items.");
	}
	
	/**
	 * Registers an item in the catalog.
	 * @param template the item to register
	 */
	private static void register(ItemTemplate template)
	{
		ITEMS.put(template.getId(), template);
	}
	
	/**
	 * Registers a block item and its block-to-item mapping.
	 * @param template the block item to register
	 * @param block the Block enum value it corresponds to
	 */
	private static void registerBlockItem(ItemTemplate template, Block block)
	{
		ITEMS.put(template.getId(), template);
		BLOCK_TO_ITEM.put(block, template.getId());
	}
	
	/**
	 * Retrieves an item by its ID.
	 * @param id the item ID (e.g. "dirt", "stone_sword")
	 * @return the Item, or null if not found
	 */
	public static ItemTemplate get(String id)
	{
		return ITEMS.get(id);
	}
	
	/**
	 * Returns the item that corresponds to a given block type.<br>
	 * This is the item form of the block itself (e.g. Block.DIRT -> "dirt" item).
	 * @param block the Block enum value
	 * @return the corresponding Item, or null if the block has no item form
	 */
	public static ItemTemplate getItemForBlock(Block block)
	{
		final String itemId = BLOCK_TO_ITEM.get(block);
		if (itemId == null)
		{
			return null;
		}
		
		return ITEMS.get(itemId);
	}
	
	/**
	 * Returns the item ID that a block drops when broken.<br>
	 * Some blocks drop different items than their block form:<br>
	 * BERRY_BUSH -> "berries", GRASS -> "dirt".<br>
	 * LEAVES have a 25% chance to drop "leaves", 75% nothing (caller handles randomness).<br>
	 * Returns null if the block drops nothing (AIR, WATER, BEDROCK, TALL_GRASS, SEAWEED, etc).
	 * @param block the block that was broken
	 * @return the item ID of the drop, or null for no drop
	 */
	public static String getDropItemId(Block block)
	{
		switch (block)
		{
			// Special drops - different from the block itself.
			case BERRY_BUSH:
			{
				return "berries";
			}
			case GRASS:
			{
				return "dirt";
			}
			
			// Leaves: caller must handle 25% chance. Return "leaves" here; caller rolls the dice.
			case LEAVES:
			{
				return "leaves";
			}
			
			// Blocks that drop themselves (recoverable).
			case DIRT:
			case SAND:
			case WOOD:
			case STONE:
			case IRON_ORE:
			case TORCH:
			case CAMPFIRE:
			case CHEST:
			case CRAFTING_TABLE:
			case FURNACE:
			case GLASS:
			case WINDOW:
			case DOOR_BOTTOM:
			{
				final String itemId = BLOCK_TO_ITEM.get(block);
				return itemId;
			}
			
			// Blocks that drop nothing.
			case AIR:
			case WATER:
			case BEDROCK:
			case TALL_GRASS:
			case TALL_SEAWEED:
			case SHORT_SEAWEED:
			case RED_POPPY:
			case DANDELION:
			case BLUE_ORCHID:
			case WHITE_DAISY:
			case DOOR_TOP:
			default:
			{
				return null;
			}
		}
	}
}
