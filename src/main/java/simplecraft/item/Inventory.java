package simplecraft.item;

/**
 * Player inventory with 36 slots: hotbar (0-8) and main inventory (9-35).<br>
 * Null slots are empty. Weapons and tools never stack (unique durability).<br>
 * Blocks, consumables, and materials stack up to their max stack size.
 * @author Pantelis Andrianakis
 * @since March 13th 2026
 */
public class Inventory
{
	/** Total number of inventory slots. */
	public static final int TOTAL_SLOTS = 36;
	
	/** Number of hotbar slots (indices 0-8). */
	public static final int HOTBAR_SLOTS = 9;
	
	/** All inventory slots. Null means empty. */
	private final ItemInstance[] _slots = new ItemInstance[TOTAL_SLOTS];
	
	/** Currently selected hotbar index (0-8). */
	private int _selectedHotbarIndex;
	
	// ========================================================
	// Slot Access.
	// ========================================================
	
	/**
	 * Returns the ItemInstance at the given slot index, or null if empty.
	 * @param index slot index (0-35)
	 * @return the ItemInstance, or null
	 */
	public ItemInstance getSlot(int index)
	{
		if (index < 0 || index >= TOTAL_SLOTS)
		{
			return null;
		}
		
		return _slots[index];
	}
	
	/**
	 * Sets the ItemInstance at the given slot index directly.<br>
	 * Pass null to clear the slot. Used for crafting output, drops, and UI manipulation.
	 * @param index slot index (0-35)
	 * @param stack the ItemInstance to place, or null to clear
	 */
	public void setSlot(int index, ItemInstance stack)
	{
		if (index < 0 || index >= TOTAL_SLOTS)
		{
			return;
		}
		
		_slots[index] = stack;
	}
	
	// ========================================================
	// Hotbar Selection.
	// ========================================================
	
	/**
	 * Returns the ItemInstance in the currently selected hotbar slot, or null if empty.
	 * @return the selected ItemInstance, or null
	 */
	public ItemInstance getSelectedItem()
	{
		return _slots[_selectedHotbarIndex];
	}
	
	/**
	 * Returns the currently selected hotbar index (0-8).
	 * @return the selected hotbar index
	 */
	public int getSelectedHotbarIndex()
	{
		return _selectedHotbarIndex;
	}
	
	/**
	 * Selects a hotbar slot by index (clamped to 0-8).
	 * @param index the hotbar index to select
	 */
	public void selectHotbar(int index)
	{
		_selectedHotbarIndex = Math.max(0, Math.min(index, HOTBAR_SLOTS - 1));
	}
	
	/**
	 * Cycles the hotbar selection forward (wraps from 8 to 0).
	 */
	public void nextHotbar()
	{
		_selectedHotbarIndex++;
		if (_selectedHotbarIndex >= HOTBAR_SLOTS)
		{
			_selectedHotbarIndex = 0;
		}
	}
	
	/**
	 * Cycles the hotbar selection backward (wraps from 0 to 8).
	 */
	public void prevHotbar()
	{
		_selectedHotbarIndex--;
		if (_selectedHotbarIndex < 0)
		{
			_selectedHotbarIndex = HOTBAR_SLOTS - 1;
		}
	}
	
	// ========================================================
	// Item Operations.
	// ========================================================
	
	/**
	 * Attempts to add an ItemInstance to the inventory.<br>
	 * First tries to merge with existing matching stacks, then fills the first empty slot.<br>
	 * Handles partial merges across multiple stacks before falling back to empty slots.
	 * @param stack the ItemInstance to add
	 * @return true if all items were placed, false if inventory is full and items remain
	 */
	public boolean addItem(ItemInstance stack)
	{
		if (stack == null || stack.isEmpty())
		{
			return true;
		}
		
		int remaining = stack.getCount();
		
		// Phase 1: Try to merge with existing matching stacks.
		for (int i = 0; i < TOTAL_SLOTS; i++)
		{
			if (remaining <= 0)
			{
				return true;
			}
			
			if (_slots[i] != null && _slots[i].canStackWith(stack))
			{
				remaining = _slots[i].add(remaining);
			}
		}
		
		// Phase 2: Fill first empty slot with whatever remains.
		if (remaining > 0)
		{
			for (int i = 0; i < TOTAL_SLOTS; i++)
			{
				if (_slots[i] == null)
				{
					_slots[i] = new ItemInstance(stack.getTemplate(), remaining);
					return true;
				}
			}
			
			// Inventory is full — could not place all items.
			return false;
		}
		
		return true;
	}
	
	/**
	 * Removes a total of {@code count} items matching the given item ID from the inventory.<br>
	 * Scans all slots and removes from each until the total is satisfied.<br>
	 * If not enough items exist, nothing is removed and false is returned.
	 * @param itemId the item ID to remove (e.g. "dirt", "wood_plank")
	 * @param count the total quantity to remove
	 * @return true if enough items were found and removed, false otherwise
	 */
	public boolean removeItem(String itemId, int count)
	{
		if (!hasItem(itemId, count))
		{
			return false;
		}
		
		int remaining = count;
		for (int i = 0; i < TOTAL_SLOTS; i++)
		{
			if (remaining <= 0)
			{
				break;
			}
			
			if (_slots[i] != null && _slots[i].getTemplate().getId().equals(itemId))
			{
				final int available = _slots[i].getCount();
				if (available <= remaining)
				{
					remaining -= available;
					_slots[i] = null;
				}
				else
				{
					_slots[i].remove(remaining);
					remaining = 0;
				}
			}
		}
		
		return true;
	}
	
	/**
	 * Checks whether the inventory contains at least {@code count} items of the given type.
	 * @param itemId the item ID to check
	 * @param count the minimum quantity required
	 * @return true if the inventory has enough
	 */
	public boolean hasItem(String itemId, int count)
	{
		int total = 0;
		for (int i = 0; i < TOTAL_SLOTS; i++)
		{
			if (_slots[i] != null && _slots[i].getTemplate().getId().equals(itemId))
			{
				total += _slots[i].getCount();
				if (total >= count)
				{
					return true;
				}
			}
		}
		
		return false;
	}
	
	/**
	 * Removes one item from the currently selected hotbar stack.<br>
	 * If the stack reaches zero, the slot is cleared to null.
	 */
	public void consumeSelectedItem()
	{
		final ItemInstance selected = _slots[_selectedHotbarIndex];
		if (selected == null)
		{
			return;
		}
		
		selected.remove(1);
		if (selected.isEmpty())
		{
			_slots[_selectedHotbarIndex] = null;
		}
	}
	
	/**
	 * Swaps the contents of two inventory slots.<br>
	 * Used for UI drag-and-drop operations.
	 * @param a first slot index (0-35)
	 * @param b second slot index (0-35)
	 */
	public void swapSlots(int a, int b)
	{
		if (a < 0 || a >= TOTAL_SLOTS || b < 0 || b >= TOTAL_SLOTS)
		{
			return;
		}
		
		final ItemInstance temp = _slots[a];
		_slots[a] = _slots[b];
		_slots[b] = temp;
	}
}
