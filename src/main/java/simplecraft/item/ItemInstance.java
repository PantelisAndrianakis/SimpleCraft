package simplecraft.item;

/**
 * An instance of an item with quantity and per-instance durability.<br>
 * Weapons and tools each have unique durability and never stack.<br>
 * Blocks, consumables and materials stack up to their max stack size.
 * @author Pantelis Andrianakis
 * @since March 13th 2026
 */
public class ItemInstance
{
	private final ItemTemplate _template;
	private int _count;
	private int _durability;
	
	/**
	 * Creates a new ItemInstance with the given count.<br>
	 * Durability is initialized to the item's max durability (0 for non-durability items).
	 * @param template the item template
	 * @param count initial quantity (clamped to 1..maxStackSize)
	 */
	public ItemInstance(ItemTemplate template, int count)
	{
		_template = template;
		_count = Math.max(1, Math.min(count, template.getMaxStackSize()));
		_durability = template.getMaxDurability();
	}
	
	/**
	 * Creates a single-count ItemInstance.
	 * @param template the item template
	 */
	public ItemInstance(ItemTemplate template)
	{
		this(template, 1);
	}
	
	// ========================================================
	// Getters.
	// ========================================================
	
	public ItemTemplate getTemplate()
	{
		return _template;
	}
	
	public int getCount()
	{
		return _count;
	}
	
	public int getDurability()
	{
		return _durability;
	}
	
	// ========================================================
	// Stack Operations.
	// ========================================================
	
	/**
	 * Returns true if this stack can merge with another.<br>
	 * Requirements: same item, neither is full and both are non-durability items.<br>
	 * Weapons and tools with durability NEVER stack because each has unique wear.
	 * @param other the other stack to check
	 * @return true if the stacks can be combined
	 */
	public boolean canStackWith(ItemInstance other)
	{
		if (other == null)
		{
			return false;
		}
		
		if (_template != other._template)
		{
			return false;
		}
		
		if (isFull() || other.isFull())
		{
			return false;
		}
		
		// Durability items (weapons/tools) never stack - each has unique wear.
		if (hasDurability() || other.hasDurability())
		{
			return false;
		}
		
		return true;
	}
	
	/**
	 * Adds the given amount to this stack.
	 * @param amount the quantity to add
	 * @return the overflow amount that could not fit (0 if everything fit)
	 */
	public int add(int amount)
	{
		final int maxStackSize = _template.getMaxStackSize();
		final int space = maxStackSize - _count;
		
		if (amount <= space)
		{
			_count += amount;
			return 0;
		}
		
		_count = maxStackSize;
		return amount - space;
	}
	
	/**
	 * Removes the given amount from this stack.
	 * @param amount the quantity to remove
	 * @return true if the removal succeeded (enough items were present)
	 */
	public boolean remove(int amount)
	{
		if (amount > _count)
		{
			return false;
		}
		
		_count -= amount;
		return true;
	}
	
	/**
	 * Directly sets the stack count.<br>
	 * <b>Use with caution:</b> intended for split/merge operations where the count is already validated to be within bounds.<br>
	 * The count is automatically clamped between 0 and the item's max stack size.
	 * @param count new stack size
	 */
	public void setCount(int count)
	{
		if (count < 0)
		{
			count = 0;
		}
		final int max = _template.getMaxStackSize();
		if (count > max)
		{
			count = max;
		}
		_count = count;
	}
	
	/**
	 * Returns true if this stack is empty (count <= 0).
	 */
	public boolean isEmpty()
	{
		return _count <= 0;
	}
	
	/**
	 * Returns true if this stack is full (count >= maxStackSize).
	 */
	public boolean isFull()
	{
		return _count >= _template.getMaxStackSize();
	}
	
	/**
	 * Creates a deep copy of this stack with the same item, count and durability.
	 * @return a new ItemInstance with identical state
	 */
	public ItemInstance copy()
	{
		final ItemInstance clone = new ItemInstance(_template, _count);
		clone._durability = _durability;
		return clone;
	}
	
	// ========================================================
	// Durability Operations.
	// ========================================================
	
	/**
	 * Returns true if this item has durability (weapons and tools).
	 */
	public boolean hasDurability()
	{
		return _template.getMaxDurability() > 0;
	}
	
	/**
	 * Reduces durability by the given amount.
	 * @param amount durability to lose
	 * @return true if durability hit 0 or below (item is broken)
	 */
	public boolean loseDurability(int amount)
	{
		if (!hasDurability())
		{
			return false;
		}
		
		_durability -= amount;
		if (_durability < 0)
		{
			_durability = 0;
		}
		
		return _durability <= 0;
	}
	
	/**
	 * Returns the current durability as a percentage (0.0 to 1.0).<br>
	 * Used for rendering durability bars in the UI.
	 * @return durability fraction, or 1.0 if item has no durability
	 */
	public float getDurabilityPercent()
	{
		final int maxDurability = _template.getMaxDurability();
		if (maxDurability <= 0)
		{
			return 1.0f;
		}
		
		return _durability / (float) maxDurability;
	}
	
	@Override
	public String toString()
	{
		if (hasDurability())
		{
			return _template.getDisplayName() + " x" + _count + " [" + _durability + "/" + _template.getMaxDurability() + "]";
		}
		
		return _template.getDisplayName() + " x" + _count;
	}
}
