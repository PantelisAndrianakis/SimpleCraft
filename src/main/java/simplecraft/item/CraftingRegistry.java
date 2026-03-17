package simplecraft.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static registry of all crafting recipes in the game.<br>
 * Call {@link #registerDefaults()} once at startup (after {@link ItemRegistry#registerDefaults()}).<br>
 * <br>
 * Recipes are stored in insertion order so the crafting UI displays them in a<br>
 * logical progression: basic materials -> wood tools -> stone tools -> iron tools<br>
 * -> consumables -> utility blocks.
 * @author Pantelis Andrianakis
 * @since March 17th 2026
 */
public class CraftingRegistry
{
	/** All registered recipes in display order. */
	private static final List<CraftingRecipe> RECIPES = new ArrayList<>();
	
	/**
	 * Registers all default crafting recipes. Called once at startup.
	 */
	public static void registerDefaults()
	{
		// ========================================================
		// Basic Materials.
		// ========================================================
		register("wood_plank", 4, ingredients("wood", 1));
		register("stone_shard", 4, ingredients("stone", 1));
		
		// ========================================================
		// Wood Tools & Weapons.
		// ========================================================
		register("wood_pickaxe", 1, ingredients("wood_plank", 4));
		register("wood_axe", 1, ingredients("wood_plank", 4));
		register("wood_shovel", 1, ingredients("wood_plank", 4));
		register("wood_sword", 1, ingredients("wood_plank", 4));
		
		// ========================================================
		// Stone Tools & Weapons.
		// ========================================================
		register("stone_pickaxe", 1, ingredients("stone_shard", 5, "wood_plank", 1));
		register("stone_axe", 1, ingredients("stone_shard", 5, "wood_plank", 1));
		register("stone_shovel", 1, ingredients("stone_shard", 5, "wood_plank", 1));
		register("stone_sword", 1, ingredients("stone_shard", 5, "wood_plank", 1));
		
		// ========================================================
		// Iron Tools & Weapons.
		// ========================================================
		register("iron_pickaxe", 1, ingredients("iron_nugget", 5, "wood_plank", 1));
		register("iron_axe", 1, ingredients("iron_nugget", 5, "wood_plank", 1));
		register("iron_shovel", 1, ingredients("iron_nugget", 5, "wood_plank", 1));
		register("iron_sword", 1, ingredients("iron_nugget", 5, "wood_plank", 1));
		
		// ========================================================
		// Consumables.
		// ========================================================
		register("health_potion", 1, ingredients("cooked_meat", 2, "glass", 1));
		
		// ========================================================
		// Utility / Furniture.
		// ========================================================
		register("campfire", 1, ingredients("wood", 2, "stone_shard", 4));
		register("chest", 1, ingredients("wood_plank", 10));
		register("crafting_table", 1, ingredients("wood", 4));
		register("furnace", 1, ingredients("stone_shard", 20));
		register("torch", 1, ingredients("wood_plank", 1, "coal", 1));
		register("door", 1, ingredients("wood_plank", 12, "glass", 1));
		register("window", 1, ingredients("wood_plank", 6, "glass", 4));
		
		System.out.println("CraftingRegistry: Registered " + RECIPES.size() + " recipes.");
	}
	
	// ========================================================
	// Internal Helpers.
	// ========================================================
	
	/**
	 * Registers a single recipe.
	 */
	private static void register(String outputItemId, int outputCount, Map<String, Integer> ingredients)
	{
		RECIPES.add(new CraftingRecipe(outputItemId, outputCount, ingredients));
	}
	
	/**
	 * Builds an ingredient map from alternating (itemId, count) pairs.<br>
	 * Uses a LinkedHashMap to preserve insertion order for display.
	 */
	private static Map<String, Integer> ingredients(Object... pairs)
	{
		final Map<String, Integer> map = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			map.put((String) pairs[i], (Integer) pairs[i + 1]);
		}
		return map;
	}
	
	// ========================================================
	// Queries.
	// ========================================================
	
	/**
	 * Returns the full list of all registered recipes in display order.<br>
	 * The returned list is unmodifiable.
	 */
	public static List<CraftingRecipe> getAllRecipes()
	{
		return Collections.unmodifiableList(RECIPES);
	}
	
	/**
	 * Returns only the recipes the player can currently craft<br>
	 * (i.e., the inventory contains all required ingredients).
	 * @param inventory the player inventory to check against
	 * @return list of craftable recipes (may be empty)
	 */
	public static List<CraftingRecipe> getAvailableRecipes(Inventory inventory)
	{
		final List<CraftingRecipe> available = new ArrayList<>();
		for (CraftingRecipe recipe : RECIPES)
		{
			if (recipe.canCraft(inventory))
			{
				available.add(recipe);
			}
		}
		return available;
	}
}
