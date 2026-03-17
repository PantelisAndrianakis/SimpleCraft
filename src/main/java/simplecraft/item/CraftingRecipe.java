package simplecraft.item;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A single crafting recipe: maps ingredient requirements to an output item and count.<br>
 * Use {@link #canCraft(Inventory)} to check whether the player has all required materials,<br>
 * and {@link #craft(Inventory)} to consume them and add the output to the inventory.
 * @author Pantelis Andrianakis
 * @since March 17th 2026
 */
public class CraftingRecipe
{
	/** Item ID of the crafted output (must exist in {@link ItemRegistry}). */
	private final String _outputItemId;
	
	/** How many items this recipe produces per craft. */
	private final int _outputCount;
	
	/** Required ingredients: item ID -> quantity needed. Unmodifiable. */
	private final Map<String, Integer> _ingredients;
	
	/**
	 * Creates a new crafting recipe.
	 * @param outputItemId the item ID this recipe produces
	 * @param outputCount how many items per craft
	 * @param ingredients item ID -> required count
	 */
	public CraftingRecipe(String outputItemId, int outputCount, Map<String, Integer> ingredients)
	{
		_outputItemId = outputItemId;
		_outputCount = outputCount;
		_ingredients = Collections.unmodifiableMap(new LinkedHashMap<>(ingredients));
	}
	
	/**
	 * Returns true if the player's inventory contains all required ingredients.
	 * @param inventory the player inventory to check
	 * @return true if all ingredients are present in sufficient quantity
	 */
	public boolean canCraft(Inventory inventory)
	{
		for (Map.Entry<String, Integer> entry : _ingredients.entrySet())
		{
			if (!inventory.hasItem(entry.getKey(), entry.getValue()))
			{
				return false;
			}
		}
		return true;
	}
	
	/**
	 * Consumes ingredients from the inventory and adds the crafted output.<br>
	 * Returns false without modifying the inventory if ingredients are insufficient<br>
	 * or the output item ID is invalid.
	 * @param inventory the player inventory to modify
	 * @return true if crafting succeeded
	 */
	public boolean craft(Inventory inventory)
	{
		if (!canCraft(inventory))
		{
			return false;
		}
		
		final ItemTemplate outputTemplate = ItemRegistry.get(_outputItemId);
		if (outputTemplate == null)
		{
			System.err.println("CraftingRecipe: Unknown output item ID: " + _outputItemId);
			return false;
		}
		
		// Consume all ingredients.
		for (Map.Entry<String, Integer> entry : _ingredients.entrySet())
		{
			inventory.removeItem(entry.getKey(), entry.getValue());
		}
		
		// Add crafted output.
		inventory.addItem(new ItemInstance(outputTemplate, _outputCount));
		return true;
	}
	
	// ========================================================
	// Getters.
	// ========================================================
	
	/**
	 * Returns the item ID of the crafted output.
	 */
	public String getOutputItemId()
	{
		return _outputItemId;
	}
	
	/**
	 * Returns how many items this recipe produces per craft.
	 */
	public int getOutputCount()
	{
		return _outputCount;
	}
	
	/**
	 * Returns the unmodifiable ingredient map (item ID -> required count).
	 */
	public Map<String, Integer> getIngredients()
	{
		return _ingredients;
	}
	
	/**
	 * Returns a human-readable description of this recipe's output.<br>
	 * e.g. "Wood Plank ×4" or "Stone Sword ×1".
	 */
	public String getOutputDisplayName()
	{
		final ItemTemplate template = ItemRegistry.get(_outputItemId);
		if (template == null)
		{
			return _outputItemId + " ×" + _outputCount;
		}
		
		if (_outputCount > 1)
		{
			return template.getDisplayName() + " ×" + _outputCount;
		}
		return template.getDisplayName();
	}
	
	/**
	 * Returns a human-readable ingredient list.<br>
	 * e.g. "2× Stone Shard, 1× Wood Plank".
	 */
	public String getIngredientsDisplayString()
	{
		final StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (Map.Entry<String, Integer> entry : _ingredients.entrySet())
		{
			if (!first)
			{
				sb.append(", ");
			}
			
			sb.append(entry.getValue()).append("× ");
			final ItemTemplate template = ItemRegistry.get(entry.getKey());
			if (template != null)
			{
				sb.append(template.getDisplayName());
			}
			else
			{
				sb.append(entry.getKey());
			}
			
			first = false;
		}
		return sb.toString();
	}
}
