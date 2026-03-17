package simplecraft.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jme3.math.ColorRGBA;

/**
 * Defines all block types with their rendering, gameplay and atlas properties.<br>
 * Texture filenames are defined per block. Atlas indices are resolved dynamically by {@link TextureAtlas} during atlas building.
 * @author Pantelis Andrianakis
 * @since February 21st 2026
 */
public enum Block
{
	// @formatter:off
	//               DisplayName                      RenderMode                   solid        transparent         liquid        tileEntity       decoration     faceSnap   hardness  tool             sideTexture                topTexture                    bottomTexture                    frontTexture
	AIR              ("",                 RenderMode.CUBE_SOLID,       false, true,  false, false,  false,          false,    0,        ToolType.NONE,    null,                      null,                         null,                            null),
	GRASS            ("Dirt",             RenderMode.CUBE_SOLID,       true,  false, false, false,  false,          false,    6,        ToolType.SHOVEL,  "grass_side.png",          "grass_top.png",              "dirt.png",                      null),
	DIRT             ("Dirt",             RenderMode.CUBE_SOLID,       true,  false, false, false,  false,          false,    6,        ToolType.SHOVEL,  "dirt.png",                null,                         null,                            null),
	STONE            ("Stone",            RenderMode.CUBE_SOLID,       true,  false, false, false,  false,          false,    12,       ToolType.PICKAXE, "stone.png",               null,                         null,                            null),
	SAND             ("Sand",             RenderMode.CUBE_SOLID,       true,  false, false, false,  false,          false,    3,        ToolType.SHOVEL,  "sand.png",                null,                         null,                            null),
	WOOD             ("Wood",             RenderMode.CUBE_SOLID,       true,  false, false, false,  false,          false,    9,        ToolType.AXE,     "wood_side.png",           "wood_top.png",               null,                            null),
	LEAVES           ("Leaves",           RenderMode.CUBE_TRANSPARENT, false, true,  false, false,  false,          false,    1,        ToolType.AXE,     "leaves.png",              null,                         null,                            null),
	WATER            ("Water",            RenderMode.CUBE_TRANSPARENT, false, true,  true,  false,  false,          false,    -1,       ToolType.NONE,    "water.png",               null,                         null,                            null),
	IRON_ORE         ("Iron Ore",         RenderMode.CUBE_SOLID,       true,  false, false, false,  false,          false,    15,       ToolType.PICKAXE, "iron_ore.png",            null,                         null,                            null),
	BEDROCK          ("Bedrock",          RenderMode.CUBE_SOLID,       true,  false, false, false,  false,          false,    -1,       ToolType.NONE,    "bedrock.png",             null,                         null,                            null),
	BERRY_BUSH       ("Berry Bush",       RenderMode.CROSS_BILLBOARD,  false, true,  false, false,  false,          false,    1,        ToolType.AXE,     "berry_bush.png",          null,                         null,                            null),
	CAMPFIRE         ("Campfire",         RenderMode.CROSS_BILLBOARD,  false, true,  false, true,   false,          false,    6,        ToolType.AXE,     "campfire.png",            null,                         null,                            null),
	CHEST            ("Chest",            RenderMode.CUBE_SOLID,       true,  false, false, true,   false,          false,    6,        ToolType.AXE,     "chest_side.png",          "chest_top.png",              null,                            "chest_front.png"),
	CRAFTING_TABLE   ("Crafting Table",   RenderMode.CUBE_SOLID,       true,  false, false, true,   false,          false,    6,        ToolType.PICKAXE, "crafting_table_side.png", "crafting_table_top.png",     "crafting_table_bottom.png",     null),
	FURNACE          ("Furnace",          RenderMode.CUBE_SOLID,       true,  false, false, true,   false,          false,    12,       ToolType.PICKAXE, "furnace_side.png",        "furnace_top.png",            null,                            "furnace_front.png"),
	TORCH            ("Torch",            RenderMode.CROSS_BILLBOARD,  false, true,  false, true,   false,          true,     1,        ToolType.NONE,    "torch.png",               null,                         null,                            null),
	TALL_GRASS       ("Tall Grass",       RenderMode.CROSS_BILLBOARD,  false, true,  false, false,  true,           false,    1,        ToolType.NONE,    "tall_grass.png",          null,                         null,                            null),
	RED_POPPY        ("Red Poppy",        RenderMode.CROSS_BILLBOARD,  false, true,  false, false,  true,           false,    1,        ToolType.NONE,    "red_poppy.png",           null,                         null,                            null),
	DANDELION        ("Dandelion",        RenderMode.CROSS_BILLBOARD,  false, true,  false, false,  true,           false,    1,        ToolType.NONE,    "dandelion.png",           null,                         null,                            null),
	BLUE_ORCHID      ("Blue Orchid",      RenderMode.CROSS_BILLBOARD,  false, true,  false, false,  true,           false,    1,        ToolType.NONE,    "blue_orchid.png",         null,                         null,                            null),
	WHITE_DAISY      ("White Daisy",      RenderMode.CROSS_BILLBOARD,  false, true,  false, false,  true,           false,    1,        ToolType.NONE,    "white_daisy.png",         null,                         null,                            null),
	TALL_SEAWEED     ("Tall Seaweed",     RenderMode.CUBE_TRANSPARENT, false, true,  false, false,  true,           false,    1,        ToolType.NONE,    "tall_seaweed.png",        "transparent.png",            "transparent.png",               null),
	SHORT_SEAWEED    ("Short Seaweed",    RenderMode.CUBE_TRANSPARENT, false, true,  false, false,  true,           false,    1,        ToolType.NONE,    "short_seaweed.png",       "transparent.png",            "transparent.png",               null),
	GLASS            ("Glass",            RenderMode.CUBE_TRANSPARENT, true,  true,  false, false,  false,          false,    3,        ToolType.NONE,    "glass.png",               null,                         null,                            null),
	WINDOW           ("Window",           RenderMode.FLAT_PANEL,       false, true,  false, true,   false,          true,     6,        ToolType.NONE,    "window.png",              null,                         null,                            null),
	DOOR_BOTTOM      ("Door",             RenderMode.FLAT_PANEL,       false, true,  false, true,   false,          true,     9,        ToolType.AXE,     "door_bottom.png",         null,                         null,                            null),
	DOOR_TOP         ("Door",             RenderMode.FLAT_PANEL,       false, true,  false, true,   false,          true,     9,        ToolType.AXE,     "door_top.png",            null,                         null,                            null);
	// @formatter:on
	
	// ========================================================
	// Inner Enums.
	// ========================================================
	
	public enum RenderMode
	{
		CUBE_SOLID,
		CUBE_TRANSPARENT,
		CROSS_BILLBOARD,
		FLAT_PANEL
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
	
	private final String _displayName;
	private final RenderMode _renderMode;
	private final boolean _solid;
	private final boolean _transparent;
	private final boolean _liquid;
	private final boolean _tileEntity;
	private final boolean _decoration;
	private final boolean _faceSnap;
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
	
	Block(String displayName, RenderMode renderMode, boolean solid, boolean transparent, boolean liquid, boolean tileEntity, boolean decoration, boolean faceSnap, int hardness, ToolType bestTool, String sideTexture, String topTexture, String bottomTexture, String frontTexture)
	{
		_displayName = displayName;
		_renderMode = renderMode;
		_solid = solid;
		_transparent = transparent;
		_liquid = liquid;
		_tileEntity = tileEntity;
		_decoration = decoration;
		_faceSnap = faceSnap;
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
	
	public String getDisplayName()
	{
		return _displayName;
	}
	
	public boolean isTileEntity()
	{
		return _tileEntity;
	}
	
	public boolean isDecoration()
	{
		return _decoration;
	}
	
	public int getLightLevel()
	{
		if (this == CAMPFIRE)
		{
			return 14;
		}
		
		if (this == TORCH)
		{
			return 10;
		}
		
		return 0;
	}
	
	/**
	 * Returns true if this block snaps to the exact face the player is looking at,<br>
	 * bypassing the 20% edge redirect used for solid block stacking.<br>
	 * Applies to blocks that attach to surfaces: torches, doors, windows, buttons, levers, etc.
	 * @return true if this block uses face-snap placement
	 */
	public boolean isFaceSnap()
	{
		return _faceSnap;
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
	
	/**
	 * Returns true if this block is a door (DOOR_BOTTOM or DOOR_TOP).<br>
	 * Convenience check for placement and interaction systems.
	 * @return true if this block is part of a door
	 */
	public boolean isDoor()
	{
		return this == DOOR_BOTTOM || this == DOOR_TOP;
	}
	
	/**
	 * Returns true if this block is a FLAT_PANEL type (window or door).<br>
	 * Used by the open/flip logic to detect adjacent panels.
	 * @return true if this block uses the FLAT_PANEL render mode
	 */
	public boolean isFlatPanel()
	{
		return _renderMode == RenderMode.FLAT_PANEL;
	}
	
	/**
	 * Returns an approximate RGB color for this block, used by the particle system<br>
	 * to color break particles. Returns a neutral gray for blocks without a clear color.
	 * @return a ColorRGBA representing this block's dominant surface color
	 */
	public ColorRGBA getParticleColor()
	{
		switch (this)
		{
			case GRASS:
			{
				return new ColorRGBA(0.36f, 0.55f, 0.20f, 1.0f);
			}
			case DIRT:
			{
				return new ColorRGBA(0.55f, 0.38f, 0.22f, 1.0f);
			}
			case STONE:
			{
				return new ColorRGBA(0.50f, 0.50f, 0.50f, 1.0f);
			}
			case SAND:
			{
				return new ColorRGBA(0.85f, 0.80f, 0.55f, 1.0f);
			}
			case WOOD:
			{
				return new ColorRGBA(0.60f, 0.42f, 0.22f, 1.0f);
			}
			case LEAVES:
			{
				return new ColorRGBA(0.20f, 0.50f, 0.15f, 1.0f);
			}
			case IRON_ORE:
			{
				return new ColorRGBA(0.65f, 0.55f, 0.50f, 1.0f);
			}
			case BERRY_BUSH:
			{
				return new ColorRGBA(0.30f, 0.45f, 0.20f, 1.0f);
			}
			case CAMPFIRE:
			{
				return new ColorRGBA(0.60f, 0.35f, 0.15f, 1.0f);
			}
			case CHEST:
			case CRAFTING_TABLE:
			{
				return new ColorRGBA(0.55f, 0.40f, 0.20f, 1.0f);
			}
			case FURNACE:
			{
				return new ColorRGBA(0.55f, 0.55f, 0.55f, 1.0f);
			}
			case TORCH:
			{
				return new ColorRGBA(0.80f, 0.65f, 0.20f, 1.0f);
			}
			case GLASS:
			case WINDOW:
			{
				return new ColorRGBA(0.80f, 0.90f, 0.95f, 1.0f);
			}
			case DOOR_BOTTOM:
			case DOOR_TOP:
			{
				return new ColorRGBA(0.55f, 0.40f, 0.22f, 1.0f);
			}
			case TALL_GRASS:
			case TALL_SEAWEED:
			case SHORT_SEAWEED:
			{
				return new ColorRGBA(0.25f, 0.55f, 0.18f, 1.0f);
			}
			case RED_POPPY:
			{
				return new ColorRGBA(0.80f, 0.15f, 0.15f, 1.0f);
			}
			case DANDELION:
			{
				return new ColorRGBA(0.90f, 0.85f, 0.20f, 1.0f);
			}
			case BLUE_ORCHID:
			{
				return new ColorRGBA(0.20f, 0.45f, 0.85f, 1.0f);
			}
			case WHITE_DAISY:
			{
				return new ColorRGBA(0.90f, 0.90f, 0.85f, 1.0f);
			}
			default:
			{
				return new ColorRGBA(0.50f, 0.50f, 0.50f, 1.0f);
			}
		}
	}
	
	// ========================================================
	// Texture File Lookup.
	// ========================================================
	
	/**
	 * Returns the texture filename for a specific face.<br>
	 * Fallback chain: front -> side, top -> side, bottom -> top -> side.
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
	 * Resolves face -> texture filename -> atlas index via the map populated by TextureAtlas.
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
	 * For cross-billboard and flat-panel blocks, this is the only texture.
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
