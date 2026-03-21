package simplecraft.item;

/**
 * Player inventory with 36 slots: hotbar (0-8) and main inventory (9-35).<br>
 * Null slots are empty. Weapons and tools never stack (unique durability).<br>
 * Blocks, consumables and materials stack up to their max stack size.<br>
 * <br>
 * Serialization format (inventory.txt):<br>
 * Line 1: {@code hotbar:<index>} - selected hotbar slot index (0-8).<br>
 * Lines 2-37: {@code <itemId>:<count>:<durability>} per slot, or {@code empty:0:0} for empty slots.<br>
 * Durability is 0 for non-durability items (blocks, materials, consumables).
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
	 * Pass null to clear the slot. Used for crafting output, drops and UI manipulation.
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
			
			// Inventory is full - could not place all items.
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
	
	// ========================================================
	// Serialization.
	// ========================================================
	
	/**
	 * Serializes the inventory to a string for saving to disk.<br>
	 * Format: first line is {@code hotbar:<index>}, followed by 36 lines<br>
	 * of {@code <itemId>:<count>:<durability>}. Empty slots are written as {@code empty:0:0}.
	 * @return the serialized inventory string
	 */
	public String serialize()
	{
		final StringBuilder sb = new StringBuilder();
		
		// Line 1: hotbar selection.
		sb.append("hotbar:").append(_selectedHotbarIndex).append('\n');
		
		// Lines 2-37: slot data.
		for (int i = 0; i < TOTAL_SLOTS; i++)
		{
			final ItemInstance slot = _slots[i];
			if (slot == null)
			{
				sb.append("empty:0:0");
			}
			else
			{
				sb.append(slot.getTemplate().getId());
				sb.append(':');
				sb.append(slot.getCount());
				sb.append(':');
				sb.append(slot.getDurability());
			}
			
			if (i < TOTAL_SLOTS - 1)
			{
				sb.append('\n');
			}
		}
		
		return sb.toString();
	}
	
	/**
	 * Deserializes inventory data from a saved string and restores all slots.<br>
	 * Clears any existing inventory contents before restoring.<br>
	 * Unknown item IDs are skipped (slot left empty) with a warning logged.
	 * @param data the serialized inventory string from {@link #serialize()}
	 */
	public void deserialize(String data)
	{
		if (data == null || data.isEmpty())
		{
			System.err.println("Inventory: Cannot deserialize - data is null or empty.");
			return;
		}
		
		final String[] lines = data.split("\n");
		if (lines.length < 1 + TOTAL_SLOTS)
		{
			System.err.println("Inventory: Cannot deserialize - expected " + (1 + TOTAL_SLOTS) + " lines, got " + lines.length + ".");
			return;
		}
		
		// Line 1: hotbar selection.
		final String hotbarLine = lines[0].trim();
		if (hotbarLine.startsWith("hotbar:"))
		{
			try
			{
				final int index = Integer.parseInt(hotbarLine.substring("hotbar:".length()));
				_selectedHotbarIndex = Math.max(0, Math.min(index, HOTBAR_SLOTS - 1));
			}
			catch (NumberFormatException e)
			{
				System.err.println("Inventory: Bad hotbar index in save - defaulting to 0.");
				_selectedHotbarIndex = 0;
			}
		}
		
		// Lines 2-37: slot data.
		for (int i = 0; i < TOTAL_SLOTS; i++)
		{
			final String line = lines[1 + i].trim();
			
			// Empty slot marker.
			if (line.equals("empty:0:0"))
			{
				_slots[i] = null;
				continue;
			}
			
			// Parse id:count:durability.
			final String[] parts = line.split(":");
			if (parts.length < 3)
			{
				System.err.println("Inventory: Bad slot line [" + i + "]: '" + line + "' - clearing slot.");
				_slots[i] = null;
				continue;
			}
			
			final String itemId = parts[0];
			final ItemTemplate template = ItemRegistry.get(itemId);
			if (template == null)
			{
				System.err.println("Inventory: Unknown item ID '" + itemId + "' in slot " + i + " - clearing slot.");
				_slots[i] = null;
				continue;
			}
			
			try
			{
				final int count = Integer.parseInt(parts[1]);
				final int durability = Integer.parseInt(parts[2]);
				
				final ItemInstance instance = new ItemInstance(template, count);
				
				// Restore durability for weapons/tools. The constructor sets max durability,
				// so we override it with the saved value for items that have durability.
				if (instance.hasDurability())
				{
					instance.setDurability(durability);
				}
				
				_slots[i] = instance;
			}
			catch (NumberFormatException e)
			{
				System.err.println("Inventory: Bad count/durability in slot " + i + ": '" + line + "' - clearing slot.");
				_slots[i] = null;
			}
		}
		
		System.out.println("Inventory: Deserialized " + TOTAL_SLOTS + " slots, hotbar index " + _selectedHotbarIndex + ".");
	}
}
