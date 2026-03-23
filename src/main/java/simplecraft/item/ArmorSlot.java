package simplecraft.item;

/**
 * Defines the four armor equipment slots.<br>
 * Each slot can hold one armor piece that reduces incoming damage by its protection value.
 * @author Pantelis Andrianakis
 * @since March 23rd 2026
 */
public enum ArmorSlot
{
	HELMET(0),
	CHESTPLATE(1),
	PANTS(2),
	BOOTS(3);
	
	/** Total number of armor slots. */
	public static final int COUNT = 4;
	
	private final int _index;
	
	ArmorSlot(int index)
	{
		_index = index;
	}
	
	/**
	 * Returns the array index for this armor slot (0-3).
	 */
	public int getIndex()
	{
		return _index;
	}
	
	/**
	 * Returns the ArmorSlot for the given index, or null if invalid.
	 */
	public static ArmorSlot fromIndex(int index)
	{
		switch (index)
		{
			case 0:
			{
				return HELMET;
			}
			case 1:
			{
				return CHESTPLATE;
			}
			case 2:
			{
				return PANTS;
			}
			case 3:
			{
				return BOOTS;
			}
			default:
			{
				return null;
			}
		}
	}
}
