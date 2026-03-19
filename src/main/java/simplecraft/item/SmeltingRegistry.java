package simplecraft.item;

import java.util.HashMap;
import java.util.Map;

/**
 * Central registry of smelting recipes and fuel burn times for the Furnace.<br>
 * Each recipe maps an input item ID to an output ItemTemplate and a smelt time in seconds.<br>
 * Each fuel item has a burn duration in seconds (how long a single unit sustains the fire).<br>
 * <br>
 * Call {@link #registerDefaults()} once at startup after {@link ItemRegistry#registerDefaults()}.
 * @author Pantelis Andrianakis
 * @since March 19th 2026
 */
public class SmeltingRegistry
{
	// ========================================================
	// Smelting Recipe Inner Class.
	// ========================================================
	
	/**
	 * Holds the result of a smelting recipe: the output item and the time required to smelt.
	 */
	public static class SmeltResult
	{
		private final ItemTemplate _output;
		private final float _smeltTime;
		
		public SmeltResult(ItemTemplate output, float smeltTime)
		{
			_output = output;
			_smeltTime = smeltTime;
		}
		
		/**
		 * Returns the item produced by this smelting recipe.
		 */
		public ItemTemplate getOutput()
		{
			return _output;
		}
		
		/**
		 * Returns the time in seconds required to smelt this recipe.
		 */
		public float getSmeltTime()
		{
			return _smeltTime;
		}
	}
	
	// ========================================================
	// Registries.
	// ========================================================
	
	/** Maps input item ID -> smelting result (output + time). */
	private static final Map<String, SmeltResult> SMELT_RECIPES = new HashMap<>();
	
	/** Maps fuel item ID -> burn duration in seconds. */
	private static final Map<String, Float> FUEL_BURN_TIMES = new HashMap<>();
	
	/**
	 * Registers all default smelting recipes and fuel burn times.<br>
	 * Must be called after {@link ItemRegistry#registerDefaults()} so that item lookups work.
	 */
	public static void registerDefaults()
	{
		// ========================================================
		// Smelting Recipes.
		// ========================================================
		registerRecipe("iron_ore", "iron_ingot", 10.0f);
		registerRecipe("sand", "glass", 8.0f);
		registerRecipe("wood", "charcoal", 5.0f);
		
		// ========================================================
		// Fuel Burn Times.
		// ========================================================
		registerFuel("wood", 10.0f);
		registerFuel("wood_plank", 8.0f);
		registerFuel("charcoal", 20.0f);
		registerFuel("leaves", 3.0f);
		
		System.out.println("SmeltingRegistry: Registered " + SMELT_RECIPES.size() + " recipes, " + FUEL_BURN_TIMES.size() + " fuels.");
	}
	
	// ========================================================
	// Registration Helpers.
	// ========================================================
	
	/**
	 * Registers a smelting recipe.
	 * @param inputItemId the item ID of the input (e.g. "iron_ore")
	 * @param outputItemId the item ID of the output (e.g. "iron_ingot")
	 * @param smeltTime time in seconds to smelt
	 */
	private static void registerRecipe(String inputItemId, String outputItemId, float smeltTime)
	{
		final ItemTemplate output = ItemRegistry.get(outputItemId);
		if (output == null)
		{
			System.err.println("SmeltingRegistry: Unknown output item '" + outputItemId + "' for recipe input '" + inputItemId + "'");
			return;
		}
		SMELT_RECIPES.put(inputItemId, new SmeltResult(output, smeltTime));
	}
	
	/**
	 * Registers a fuel item and its burn duration.
	 * @param fuelItemId the item ID of the fuel
	 * @param burnTime burn duration in seconds
	 */
	private static void registerFuel(String fuelItemId, float burnTime)
	{
		FUEL_BURN_TIMES.put(fuelItemId, burnTime);
	}
	
	// ========================================================
	// Lookup Methods.
	// ========================================================
	
	/**
	 * Returns the smelting result for the given input item, or null if not smeltable.
	 * @param inputItemId the item ID to check
	 * @return the SmeltResult (output + time), or null
	 */
	public static SmeltResult getSmeltResult(String inputItemId)
	{
		return SMELT_RECIPES.get(inputItemId);
	}
	
	/**
	 * Returns the burn time in seconds for the given fuel item, or 0 if not a fuel.
	 * @param fuelItemId the item ID to check
	 * @return burn duration in seconds, or 0
	 */
	public static float getBurnTime(String fuelItemId)
	{
		final Float burnTime = FUEL_BURN_TIMES.get(fuelItemId);
		return burnTime != null ? burnTime : 0.0f;
	}
	
	/**
	 * Returns true if the given item ID is a valid smelting input.
	 * @param itemId the item ID to check
	 * @return true if smeltable
	 */
	public static boolean isSmeltable(String itemId)
	{
		return SMELT_RECIPES.containsKey(itemId);
	}
	
	/**
	 * Returns true if the given item ID is a valid fuel source.
	 * @param itemId the item ID to check
	 * @return true if burnable
	 */
	public static boolean isFuel(String itemId)
	{
		return FUEL_BURN_TIMES.containsKey(itemId) && FUEL_BURN_TIMES.get(itemId) > 0;
	}
}
