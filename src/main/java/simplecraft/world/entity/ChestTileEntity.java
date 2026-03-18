package simplecraft.world.entity;

import com.jme3.math.Vector3f;

import simplecraft.item.DropManager;
import simplecraft.item.ItemInstance;
import simplecraft.item.ItemRegistry;
import simplecraft.item.ItemTemplate;
import simplecraft.player.PlayerController;
import simplecraft.util.Rnd;
import simplecraft.util.Vector3i;
import simplecraft.world.Block;
import simplecraft.world.World;

/**
 * Tile entity for placed Chest blocks.<br>
 * Stores up to 27 item stacks (3 rows × 9 columns) that persist across save/load.<br>
 * Right-clicking opens a ChestScreen UI where items can be transferred between<br>
 * the chest and the player's inventory. Breaking the chest drops all contents<br>
 * as world items at a slight random scatter around the chest position.<br>
 * <br>
 * Serialization writes each slot as {@code slot_N=itemId:count} (or {@code slot_N=empty})<br>
 * appended to the base TileEntity key=value format. Deserialization reconstructs<br>
 * ItemInstances from saved item IDs via {@link ItemRegistry}.
 * @author Pantelis Andrianakis
 * @since March 18th 2026
 */
public class ChestTileEntity extends TileEntity
{
	/** Number of storage slots in a chest. */
	public static final int CHEST_SLOTS = 27;
	
	/** Random scatter offset (±blocks) when dropping chest contents. */
	private static final float DROP_SCATTER = 0.5f;
	
	/** Stored items. Null elements are empty slots. */
	private final ItemInstance[] _contents = new ItemInstance[CHEST_SLOTS];
	
	// ========================================================
	// Constructors.
	// ========================================================
	
	/**
	 * Creates a new chest tile entity at the given world position.
	 * @param position world block coordinates
	 */
	public ChestTileEntity(Vector3i position)
	{
		super(position, Block.CHEST);
	}
	
	// ========================================================
	// Lifecycle Hooks.
	// ========================================================
	
	@Override
	public void onInteract(PlayerController player, World world)
	{
		// Interaction is handled by BlockInteraction which opens ChestScreen.
		// This hook is intentionally a no-op; the instanceof check in
		// BlockInteraction.handlePlace() triggers the UI.
	}
	
	/**
	 * Drops all non-null chest contents as world items at the chest's position.<br>
	 * Items scatter slightly (random ±0.5 blocks on X and Z) so they don't<br>
	 * all stack in a single pile. Clears the contents array after dropping.
	 * @param dropManager the drop manager for spawning world items
	 */
	public void dropContents(DropManager dropManager)
	{
		if (dropManager == null)
		{
			return;
		}
		
		final float centerX = _position.x + 0.5f;
		final float centerY = _position.y;
		final float centerZ = _position.z + 0.5f;
		
		for (int i = 0; i < CHEST_SLOTS; i++)
		{
			if (_contents[i] != null && !_contents[i].isEmpty())
			{
				// Slight random offset so items scatter.
				final float dropX = centerX + (Rnd.nextFloat() - 0.5f) * 2.0f * DROP_SCATTER;
				final float dropZ = centerZ + (Rnd.nextFloat() - 0.5f) * 2.0f * DROP_SCATTER;
				dropManager.spawnDrop(new Vector3f(dropX, centerY, dropZ), _contents[i]);
				_contents[i] = null;
			}
		}
	}
	
	// ========================================================
	// Contents Access.
	// ========================================================
	
	/**
	 * Returns the chest's contents array (direct reference).<br>
	 * Modifications to this array are immediately reflected in the chest.<br>
	 * Used by ChestScreen for live editing of chest contents.
	 * @return the 27-element contents array
	 */
	public ItemInstance[] getContents()
	{
		return _contents;
	}
	
	/**
	 * Returns the item at the given chest slot index, or null if empty.
	 * @param index slot index (0-26)
	 * @return the ItemInstance, or null
	 */
	public ItemInstance getSlot(int index)
	{
		if (index < 0 || index >= CHEST_SLOTS)
		{
			return null;
		}
		return _contents[index];
	}
	
	/**
	 * Sets the item at the given chest slot index.
	 * @param index slot index (0-26)
	 * @param stack the ItemInstance to place, or null to clear
	 */
	public void setSlot(int index, ItemInstance stack)
	{
		if (index < 0 || index >= CHEST_SLOTS)
		{
			return;
		}
		_contents[index] = stack;
	}
	
	// ========================================================
	// Serialization.
	// ========================================================
	
	@Override
	public String serialize()
	{
		final StringBuilder sb = new StringBuilder();
		sb.append(super.serialize());
		
		// Write each slot.
		for (int i = 0; i < CHEST_SLOTS; i++)
		{
			sb.append('\n');
			if (_contents[i] != null && !_contents[i].isEmpty())
			{
				sb.append("slot_").append(i).append('=');
				sb.append(_contents[i].getTemplate().getId());
				sb.append(':').append(_contents[i].getCount());
			}
			else
			{
				sb.append("slot_").append(i).append("=empty");
			}
		}
		
		return sb.toString();
	}
	
	/**
	 * Reconstructs chest contents from serialized slot data.<br>
	 * Called during deserialization after the base entity fields are parsed.
	 * @param data the full serialized key=value string
	 */
	public void deserializeContents(String data)
	{
		if (data == null || data.isEmpty())
		{
			return;
		}
		
		for (String line : data.split("\n"))
		{
			final int eq = line.indexOf('=');
			if (eq < 0)
			{
				continue;
			}
			
			final String key = line.substring(0, eq).trim();
			final String value = line.substring(eq + 1).trim();
			
			if (!key.startsWith("slot_"))
			{
				continue;
			}
			
			// Parse slot index.
			final int slotIndex;
			try
			{
				slotIndex = Integer.parseInt(key.substring(5));
			}
			catch (NumberFormatException e)
			{
				continue;
			}
			
			if (slotIndex < 0 || slotIndex >= CHEST_SLOTS)
			{
				continue;
			}
			
			if ("empty".equals(value))
			{
				_contents[slotIndex] = null;
				continue;
			}
			
			// Parse "itemId:count" or "itemId:count:durability".
			final String[] parts = value.split(":");
			if (parts.length < 2)
			{
				continue;
			}
			
			final String itemId = parts[0];
			final int count;
			try
			{
				count = Integer.parseInt(parts[1]);
			}
			catch (NumberFormatException e)
			{
				continue;
			}
			
			final ItemTemplate template = ItemRegistry.get(itemId);
			if (template == null)
			{
				System.err.println("ChestTileEntity: Unknown item ID '" + itemId + "' in slot " + slotIndex);
				continue;
			}
			
			final ItemInstance instance = new ItemInstance(template, count);
			_contents[slotIndex] = instance;
		}
	}
}
