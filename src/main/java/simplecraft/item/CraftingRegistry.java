package simplecraft.item;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Central registry of crafting recipes for the Crafting Table.<br>
 * Each recipe maps a set of ingredient requirements to an output item and quantity.<br>
 * <br>
 * Call {@link #registerDefaults()} once at startup after {@link ItemRegistry#registerDefaults()}.
 * @author Pantelis Andrianakis
 * @since March 17th 2026
 */
public class CraftingRegistry
{
	// ========================================================
	// Recipe Inner Class.
	// ========================================================
	
	/**
	 * A crafting recipe with ingredients and output.
	 */
	public static class CraftingRecipe
	{
		private final String[] _ingredientIds;
		private final int[] _ingredientCounts;
		private final ItemTemplate _output;
		private final int _outputCount;
		
		public CraftingRecipe(String[] ingredientIds, int[] ingredientCounts, ItemTemplate output, int outputCount)
		{
			_ingredientIds = ingredientIds;
			_ingredientCounts = ingredientCounts;
			_output = output;
			_outputCount = outputCount;
		}
		
		public String[] getIngredientIds()
		{
			return _ingredientIds;
		}
		
		public int[] getIngredientCounts()
		{
			return _ingredientCounts;
		}
		
		public ItemTemplate getOutput()
		{
			return _output;
		}
		
		public int getOutputCount()
		{
			return _outputCount;
		}
		
		/**
		 * Returns the output item ID.
		 */
		public String getOutputItemId()
		{
			return _output.getId();
		}
		
		/**
		 * Returns a display string for the output (e.g. "4× Wood Plank").
		 */
		public String getOutputDisplayName()
		{
			if (_outputCount > 1)
			{
				return _outputCount + "× " + _output.getDisplayName();
			}
			
			return _output.getDisplayName();
		}
		
		/**
		 * Returns a human-readable ingredient list (e.g. "3× Stone Shard, 1× Wood Plank").
		 */
		public String getIngredientsDisplayString()
		{
			final StringBuilder sb = new StringBuilder();
			for (int i = 0; i < _ingredientIds.length; i++)
			{
				if (i > 0)
				{
					sb.append(", ");
				}
				sb.append(_ingredientCounts[i]).append("× ");
				final ItemTemplate ingredient = ItemRegistry.get(_ingredientIds[i]);
				sb.append(ingredient != null ? ingredient.getDisplayName() : _ingredientIds[i]);
			}
			
			return sb.toString();
		}
		
		/**
		 * Returns true if the given inventory has all required ingredients.
		 */
		public boolean canCraft(Inventory inventory)
		{
			for (int i = 0; i < _ingredientIds.length; i++)
			{
				if (!inventory.hasItem(_ingredientIds[i], _ingredientCounts[i]))
				{
					return false;
				}
			}
			
			return true;
		}
		
		/**
		 * Consumes ingredients from the inventory and adds the output.<br>
		 * Returns true if crafting succeeded, false if ingredients were insufficient.
		 * @param inventory the player inventory
		 * @return true if the item was crafted and added
		 */
		public boolean craft(Inventory inventory)
		{
			if (!canCraft(inventory))
			{
				return false;
			}
			
			for (int i = 0; i < _ingredientIds.length; i++)
			{
				inventory.removeItem(_ingredientIds[i], _ingredientCounts[i]);
			}
			
			inventory.addItem(new ItemInstance(_output, _outputCount));
			return true;
		}
	}
	
	// ========================================================
	// Registry.
	// ========================================================
	
	/** All registered crafting recipes. */
	private static final List<CraftingRecipe> RECIPES = new ArrayList<>();
	
	/**
	 * Registers all default crafting recipes.<br>
	 * Must be called after {@link ItemRegistry#registerDefaults()} so that item lookups work.
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
		// Iron Tools & Weapons (use iron_ingot - smelted from iron ore or crafted from nuggets).
		// ========================================================
		register("iron_pickaxe", 1, ingredients("iron_ingot", 3, "wood_plank", 1));
		register("iron_axe", 1, ingredients("iron_ingot", 3, "wood_plank", 1));
		register("iron_shovel", 1, ingredients("iron_ingot", 3, "wood_plank", 1));
		register("iron_sword", 1, ingredients("iron_ingot", 3, "wood_plank", 1));
		
		// ========================================================
		// Consumables.
		// ========================================================
		register("health_potion", 1, ingredients("cooked_meat", 2, "glass", 1));
		
		// ========================================================
		// Special Orbs.
		// ========================================================
		register("dragon_orb", 1, ingredients("iron_ingot", 20, "stone_shard", 50, "glass", 10));
		
		// ========================================================
		// Utility / Furniture.
		// ========================================================
		register("campfire", 1, ingredients("wood", 2, "stone_shard", 4));
		register("chest", 1, ingredients("wood_plank", 10));
		register("crafting_table", 1, ingredients("wood", 4));
		register("furnace", 1, ingredients("stone_shard", 20));
		register("torch", 1, ingredients("wood_plank", 1, "charcoal", 1));
		register("door", 1, ingredients("wood_plank", 12, "glass", 1));
		register("window", 1, ingredients("wood_plank", 6, "glass", 4));
		
		System.out.println("CraftingRegistry: Registered " + RECIPES.size() + " recipes.");
	}
	
	// ========================================================
	// Registration Helpers.
	// ========================================================
	
	/**
	 * Builds a paired ingredient map from alternating (itemId, count) varargs.<br>
	 * Usage: {@code ingredients("wood_plank", 4, "charcoal", 1)}
	 * @param args alternating item IDs (String) and counts (int)
	 * @return a map of item ID -> required count
	 */
	private static Map<String, Integer> ingredients(Object... args)
	{
		final Map<String, Integer> map = new LinkedHashMap<>();
		for (int i = 0; i < args.length; i += 2)
		{
			map.put((String) args[i], (int) args[i + 1]);
		}
		
		return map;
	}
	
	/**
	 * Registers a crafting recipe.
	 * @param outputId the output item ID
	 * @param outputCount number of items produced
	 * @param ingredientMap map of ingredient item ID -> required count
	 */
	private static void register(String outputId, int outputCount, Map<String, Integer> ingredientMap)
	{
		final ItemTemplate output = ItemRegistry.get(outputId);
		if (output == null)
		{
			System.err.println("CraftingRegistry: Unknown output item '" + outputId + "'");
			return;
		}
		
		final String[] ids = new String[ingredientMap.size()];
		final int[] counts = new int[ingredientMap.size()];
		int idx = 0;
		for (Entry<String, Integer> entry : ingredientMap.entrySet())
		{
			ids[idx] = entry.getKey();
			counts[idx] = entry.getValue();
			idx++;
		}
		
		RECIPES.add(new CraftingRecipe(ids, counts, output, outputCount));
	}
	
	// ========================================================
	// Lookup Methods.
	// ========================================================
	
	/**
	 * Returns all registered crafting recipes.
	 */
	public static List<CraftingRecipe> getAllRecipes()
	{
		return RECIPES;
	}
	
	/**
	 * Returns all recipes that can currently be crafted with the given inventory.
	 * @param inventory the player inventory to check against
	 * @return list of craftable recipes
	 */
	public static List<CraftingRecipe> getCraftableRecipes(Inventory inventory)
	{
		final List<CraftingRecipe> craftable = new ArrayList<>();
		for (CraftingRecipe recipe : RECIPES)
		{
			if (recipe.canCraft(inventory))
			{
				craftable.add(recipe);
			}
		}
		
		return craftable;
	}
}
