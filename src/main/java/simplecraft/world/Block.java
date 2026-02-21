package simplecraft.world;

/**
 * Defines all block types with their rendering, gameplay, and atlas properties.
 * @author Pantelis Andrianakis
 * @since February 21st 2026
 */
public enum Block
{
	// @formatter:off
	//               solid         transparent          liquid          tileEntity          hardness         bestTool             atlasIdx
	AIR                      (RenderMode.CUBE_SOLID,     false, true,  false, false,  false, 0,        ToolType.NONE,   -1),
	GRASS                    (RenderMode.CUBE_SOLID,     true,  false, false, false,  false, 3,        ToolType.SHOVEL, -1), // multi-face
	DIRT                     (RenderMode.CUBE_SOLID,     true,  false, false, false,  false, 3,        ToolType.SHOVEL, 2),
	STONE                    (RenderMode.CUBE_SOLID,     true,  false, false, false,  false, 8,        ToolType.PICKAXE, 3),
	SAND                     (RenderMode.CUBE_SOLID,     true,  false, false, false,  false, 2,        ToolType.SHOVEL, 4),
	WOOD                     (RenderMode.CUBE_SOLID,     true,  false, false, false,  false, 5,        ToolType.AXE,    -1), // multi-face
	LEAVES                   (RenderMode.CUBE_TRANSPARENT, false, true, false, false, false, 1,        ToolType.AXE,    7),
	WATER                    (RenderMode.CUBE_TRANSPARENT, false, true, true,  false, false, -1,       ToolType.NONE,   8),
	IRON_ORE                 (RenderMode.CUBE_SOLID,     true,  false, false, false,  false, 10,       ToolType.PICKAXE, 9),
	BEDROCK                  (RenderMode.CUBE_SOLID,     true,  false, false, false,  false, -1,       ToolType.NONE,   10),
	BERRY_BUSH               (RenderMode.CROSS_BILLBOARD, false, true, false, false,  false, 1,        ToolType.AXE,    11),
	CAMPFIRE                 (RenderMode.CROSS_BILLBOARD, false, true, false, true,   false, 3,        ToolType.AXE,    12),
	CHEST                    (RenderMode.CUBE_SOLID,     true,  false, false, true,   false, 4,        ToolType.AXE,    -1), // multi-face
	CRAFTING_TABLE            (RenderMode.CUBE_SOLID,     true,  false, false, true,   false, 4,        ToolType.AXE,    -1), // multi-face
	FURNACE                  (RenderMode.CUBE_SOLID,     true,  false, false, true,   false, 6,        ToolType.PICKAXE, -1), // multi-face
	TORCH                    (RenderMode.CROSS_BILLBOARD, false, true, false, true,   false, 1,        ToolType.NONE,   23),
	RED_POPPY                (RenderMode.CROSS_BILLBOARD, false, true, false, false,  true,  1,        ToolType.NONE,   24),
	DANDELION                (RenderMode.CROSS_BILLBOARD, false, true, false, false,  true,  1,        ToolType.NONE,   25),
	BLUE_ORCHID              (RenderMode.CROSS_BILLBOARD, false, true, false, false,  true,  1,        ToolType.NONE,   26),
	WHITE_DAISY              (RenderMode.CROSS_BILLBOARD, false, true, false, false,  true,  1,        ToolType.NONE,   27);
    // @formatter:on
	
	// ========================================================
	// Inner Enums
	// ========================================================
	
	public enum RenderMode
	{
		CUBE_SOLID,
		CUBE_TRANSPARENT,
		CROSS_BILLBOARD
	}
	
	public enum ToolType
	{
		NONE,
		PICKAXE,
		AXE,
		SHOVEL
	}
	
	public enum Face
	{
		TOP,
		BOTTOM,
		NORTH,
		SOUTH,
		EAST,
		WEST
	}
	
	// ========================================================
	// Fields
	// ========================================================
	
	private final RenderMode _renderMode;
	private final boolean _solid;
	private final boolean _transparent;
	private final boolean _liquid;
	private final boolean _tileEntity;
	private final boolean _decoration;
	private final int _hardness;
	private final ToolType _bestTool;
	private final int _atlasIndex; // -1 for multi-face or AIR
	
	// ========================================================
	// Constructor
	// ========================================================
	
	Block(RenderMode renderMode, boolean solid, boolean transparent, boolean liquid, boolean tileEntity, boolean decoration, int hardness, ToolType bestTool, int atlasIndex)
	{
		_renderMode = renderMode;
		_solid = solid;
		_transparent = transparent;
		_liquid = liquid;
		_tileEntity = tileEntity;
		_decoration = decoration;
		_hardness = hardness;
		_bestTool = bestTool;
		_atlasIndex = atlasIndex;
	}
	
	// ========================================================
	// Rendering Properties
	// ========================================================
	
	public RenderMode getRenderMode()
	{
		return _renderMode;
	}
	
	public boolean isSolid()
	{
		return _solid;
	}
	
	public boolean isTransparent()
	{
		return _transparent;
	}
	
	public boolean isLiquid()
	{
		return _liquid;
	}
	
	// ========================================================
	// Gameplay Properties
	// ========================================================
	
	public boolean isTileEntity()
	{
		return _tileEntity;
	}
	
	public boolean isDecoration()
	{
		return _decoration;
	}
	
	public int getHardness()
	{
		return _hardness;
	}
	
	public boolean isBreakable()
	{
		return _hardness > 0;
	}
	
	public ToolType getBestTool()
	{
		return _bestTool;
	}
	
	// ========================================================
	// Atlas Lookup
	// ========================================================
	
	/**
	 * Returns the default atlas index for this block. For uniform blocks this is the single texture index. For multi-face blocks this returns -1; use {@link #getAtlasIndex(Face)} instead.
	 */
	public int getAtlasIndex()
	{
		return _atlasIndex;
	}
	
	/**
	 * Returns the atlas index for a specific face. Multi-face blocks (GRASS, WOOD, CHEST, CRAFTING_TABLE, FURNACE) return different indices per face. Cross-billboard blocks ignore the face parameter.
	 */
	public int getAtlasIndex(Face face)
	{
		switch (this)
		{
			case GRASS:
			{
				switch (face)
				{
					case TOP:
					{
						return 0; // grass_top
					}
					case BOTTOM:
					{
						return 2; // dirt
					}
					default:
					{
						return 1; // grass_side
					}
				}
			}
			case WOOD:
			{
				switch (face)
				{
					case TOP:
					case BOTTOM:
					{
						return 6; // wood_top
					}
					default:
					{
						return 5; // wood_side
					}
				}
			}
			case CHEST:
			{
				switch (face)
				{
					case NORTH:
					{
						return 14; // chest_front (latch face)
					}
					case TOP:
					case BOTTOM:
					{
						return 16; // chest_top
					}
					default:
					{
						return 15; // chest_side
					}
				}
			}
			case CRAFTING_TABLE:
			{
				switch (face)
				{
					case TOP:
					{
						return 17; // crafting_table_top (grid)
					}
					case BOTTOM:
					{
						return 19; // crafting_table_bottom
					}
					default:
					{
						return 18; // crafting_table_side
					}
				}
			}
			case FURNACE:
			{
				switch (face)
				{
					case NORTH:
					{
						return 20; // furnace_front (dark opening)
					}
					case TOP:
					case BOTTOM:
					{
						return 22; // furnace_top
					}
					default:
					{
						return 21; // furnace_side
					}
				}
			}
			default:
			{
				return _atlasIndex;
			}
		}
	}
	
	// ========================================================
	// Lookup by Ordinal
	// ========================================================
	
	private static final Block[] VALUES = values();
	
	/**
	 * Fast ordinal-to-Block lookup. Returns AIR for invalid ordinals.
	 */
	public static Block fromOrdinal(int ordinal)
	{
		if (ordinal < 0 || ordinal >= VALUES.length)
		{
			return AIR;
		}
		
		return VALUES[ordinal];
	}
}
