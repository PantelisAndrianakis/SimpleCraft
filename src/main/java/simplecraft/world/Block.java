package simplecraft.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Defines all block types with their rendering, gameplay, and atlas properties.<br>
 * Texture filenames are defined per block. Atlas indices are resolved dynamically by {@link TextureAtlas} during atlas building.
 * @author Pantelis Andrianakis
 * @since February 21st 2026
 */
public enum Block
{
	// @formatter:off
	//                        RenderMode                   solid        transparent         liquid        tileEntity          decoration     hardness   tool              sideTexture                topTexture                    bottomTexture                    frontTexture
	AIR                      (RenderMode.CUBE_SOLID,       false, true,  false, false,  false, 0,        ToolType.NONE,    null,                      null,                         null,                            null),
	GRASS                    (RenderMode.CUBE_SOLID,       true,  false, false, false,  false, 3,        ToolType.SHOVEL,  "grass_side.png",          "grass_top.png",              "dirt.png",                      null),
	DIRT                     (RenderMode.CUBE_SOLID,       true,  false, false, false,  false, 3,        ToolType.SHOVEL,  "dirt.png",                null,                         null,                            null),
	STONE                    (RenderMode.CUBE_SOLID,       true,  false, false, false,  false, 8,        ToolType.PICKAXE, "stone.png",               null,                         null,                            null),
	SAND                     (RenderMode.CUBE_SOLID,       true,  false, false, false,  false, 2,        ToolType.SHOVEL,  "sand.png",                null,                         null,                            null),
	WOOD                     (RenderMode.CUBE_SOLID,       true,  false, false, false,  false, 5,        ToolType.AXE,     "wood_side.png",           "wood_top.png",               null,                            null),
	LEAVES                   (RenderMode.CUBE_TRANSPARENT, false, true,  false, false,  false, 1,        ToolType.AXE,     "leaves.png",              null,                         null,                            null),
	WATER                    (RenderMode.CUBE_TRANSPARENT, false, true,  true,  false,  false, -1,       ToolType.NONE,    "water.png",               null,                         null,                            null),
	IRON_ORE                 (RenderMode.CUBE_SOLID,       true,  false, false, false,  false, 10,       ToolType.PICKAXE, "iron_ore.png",            null,                         null,                            null),
	BEDROCK                  (RenderMode.CUBE_SOLID,       true,  false, false, false,  false, -1,       ToolType.NONE,    "bedrock.png",             null,                         null,                            null),
	BERRY_BUSH               (RenderMode.CROSS_BILLBOARD,  false, true,  false, false,  false, 1,        ToolType.AXE,     "berry_bush.png",          null,                         null,                            null),
	CAMPFIRE                 (RenderMode.CROSS_BILLBOARD,  false, true,  false, true,   false, 3,        ToolType.AXE,     "campfire.png",            null,                         null,                            null),
	CHEST                    (RenderMode.CUBE_SOLID,       true,  false, false, true,   false, 4,        ToolType.AXE,     "chest_side.png",          "chest_top.png",              null,                            "chest_front.png"),
	CRAFTING_TABLE           (RenderMode.CUBE_SOLID,       true,  false, false, true,   false, 4,        ToolType.AXE,     "crafting_table_side.png", "crafting_table_top.png",     "crafting_table_bottom.png",     null),
	FURNACE                  (RenderMode.CUBE_SOLID,       true,  false, false, true,   false, 6,        ToolType.PICKAXE, "furnace_side.png",        "furnace_top.png",            null,                            "furnace_front.png"),
	TORCH                    (RenderMode.CROSS_BILLBOARD,  false, true,  false, true,   false, 1,        ToolType.NONE,    "torch.png",               null,                         null,                            null),
	TALL_GRASS               (RenderMode.CROSS_BILLBOARD,  false, true,  false, false,  true,  1,        ToolType.NONE,    "tall_grass.png",          null,                         null,                            null),
	RED_POPPY                (RenderMode.CROSS_BILLBOARD,  false, true,  false, false,  true,  1,        ToolType.NONE,    "red_poppy.png",           null,                         null,                            null),
	DANDELION                (RenderMode.CROSS_BILLBOARD,  false, true,  false, false,  true,  1,        ToolType.NONE,    "dandelion.png",           null,                         null,                            null),
	BLUE_ORCHID              (RenderMode.CROSS_BILLBOARD,  false, true,  false, false,  true,  1,        ToolType.NONE,    "blue_orchid.png",         null,                         null,                            null),
	WHITE_DAISY              (RenderMode.CROSS_BILLBOARD,  false, true,  false, false,  true,  1,        ToolType.NONE,    "white_daisy.png",         null,                         null,                            null),
	TALL_SEAWEED             (RenderMode.CUBE_TRANSPARENT, false, true,  false, false,  true,  1,        ToolType.NONE,    "tall_seaweed.png",        "transparent.png",            "transparent.png",               null),
	SHORT_SEAWEED            (RenderMode.CUBE_TRANSPARENT, false, true,  false, false,  true,  1,        ToolType.NONE,    "short_seaweed.png",       "transparent.png",            "transparent.png",               null);
	// @formatter:on
	
	// ========================================================
	// Inner Enums.
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
	// Fields.
	// ========================================================
	
	private final RenderMode _renderMode;
	private final boolean _solid;
	private final boolean _transparent;
	private final boolean _liquid;
	private final boolean _tileEntity;
	private final boolean _decoration;
	private final int _hardness;
	private final ToolType _bestTool;
	
	/** Side (SOUTH, EAST, WEST) texture. Also default for all faces and cross-billboards. */
	private final String _sideTexture;
	
	/** Top face texture. Null = use side texture. */
	private final String _topTexture;
	
	/** Bottom face texture. Null = use top texture (which falls back to side). */
	private final String _bottomTexture;
	
	/** Front/NORTH face texture. Null = use side texture. */
	private final String _frontTexture;
	
	// ========================================================
	// Atlas Index Map (populated by TextureAtlas at startup).
	// ========================================================
	
	/** Maps texture filename to atlas index. Populated by {@link TextureAtlas#buildAtlas}. */
	private static final Map<String, Integer> ATLAS_INDEX_MAP = new HashMap<>();
	
	// ========================================================
	// Constructor.
	// ========================================================
	
	Block(RenderMode renderMode, boolean solid, boolean transparent, boolean liquid, boolean tileEntity, boolean decoration, int hardness, ToolType bestTool, String sideTexture, String topTexture, String bottomTexture, String frontTexture)
	{
		_renderMode = renderMode;
		_solid = solid;
		_transparent = transparent;
		_liquid = liquid;
		_tileEntity = tileEntity;
		_decoration = decoration;
		_hardness = hardness;
		_bestTool = bestTool;
		_sideTexture = sideTexture;
		_topTexture = topTexture;
		_bottomTexture = bottomTexture;
		_frontTexture = frontTexture;
	}
	
	// ========================================================
	// Rendering Properties.
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
	// Gameplay Properties.
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
	// Texture File Lookup.
	// ========================================================
	
	/**
	 * Returns the texture filename for a specific face.<br>
	 * Fallback chain: front → side, top → side, bottom → top → side.
	 */
	public String getTextureFile(Face face)
	{
		switch (face)
		{
			case TOP:
			{
				return _topTexture != null ? _topTexture : _sideTexture;
			}
			case BOTTOM:
			{
				// Bottom falls back to top, then side.
				if (_bottomTexture != null)
				{
					return _bottomTexture;
				}
				
				return _topTexture != null ? _topTexture : _sideTexture;
			}
			case NORTH:
			{
				return _frontTexture != null ? _frontTexture : _sideTexture;
			}
			default:
			{
				// SOUTH, EAST, WEST.
				return _sideTexture;
			}
		}
	}
	
	// ========================================================
	// Atlas Index Lookup.
	// ========================================================
	
	/**
	 * Returns the atlas index for a specific face.<br>
	 * Resolves face → texture filename → atlas index via the map populated by TextureAtlas.
	 */
	public int getAtlasIndex(Face face)
	{
		final String filename = getTextureFile(face);
		if (filename == null)
		{
			return -1;
		}
		
		return ATLAS_INDEX_MAP.getOrDefault(filename, -1);
	}
	
	/**
	 * Returns the default atlas index (using side texture).<br>
	 * For cross-billboard blocks, this is the only texture.
	 */
	public int getAtlasIndex()
	{
		if (_sideTexture == null)
		{
			return -1;
		}
		
		return ATLAS_INDEX_MAP.getOrDefault(_sideTexture, -1);
	}
	
	// ========================================================
	// Static Atlas Registration.
	// ========================================================
	
	/**
	 * Registers an atlas index for a texture filename.<br>
	 * Called by {@link TextureAtlas} during atlas building.
	 */
	public static void registerAtlasIndex(String filename, int index)
	{
		ATLAS_INDEX_MAP.put(filename, index);
	}
	
	/**
	 * Collects all unique texture filenames from all block types in a deterministic order.<br>
	 * Used by {@link TextureAtlas} to build the atlas grid.
	 * @return ordered list of unique texture filenames
	 */
	public static List<String> collectTextureFiles()
	{
		// LinkedHashSet preserves insertion order and removes duplicates.
		final Set<String> uniqueFiles = new LinkedHashSet<>();
		
		for (Block block : VALUES)
		{
			if (block._sideTexture != null)
			{
				uniqueFiles.add(block._sideTexture);
			}
			
			if (block._topTexture != null)
			{
				uniqueFiles.add(block._topTexture);
			}
			
			if (block._bottomTexture != null)
			{
				uniqueFiles.add(block._bottomTexture);
			}
			
			if (block._frontTexture != null)
			{
				uniqueFiles.add(block._frontTexture);
			}
		}
		
		return new ArrayList<>(uniqueFiles);
	}
	
	// ========================================================
	// Lookup by Ordinal.
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
