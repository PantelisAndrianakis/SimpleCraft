package simplecraft.player;

import java.awt.Font;

import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.FrameBuffer;
import com.jme3.texture.FrameBuffer.FrameBufferTarget;
import com.jme3.texture.Image.Format;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture.MagFilter;
import com.jme3.texture.Texture.MinFilter;
import com.jme3.texture.Texture2D;

import simplecraft.SimpleCraft;
import simplecraft.input.GameInputManager;
import simplecraft.item.ArmorSlot;
import simplecraft.item.DropManager;
import simplecraft.item.Inventory;
import simplecraft.item.ItemInstance;
import simplecraft.item.ItemRegistry;
import simplecraft.item.ItemTemplate;
import simplecraft.item.ItemTextureResolver;
import simplecraft.item.ItemType;
import simplecraft.ui.FontManager;
import simplecraft.world.Block;
import simplecraft.world.World;

/**
 * Full inventory screen opened with Tab.<br>
 * Displays all 36 inventory slots in a 4-row × 9-column grid on the right,<br>
 * with a rotating 3D player model and 4 armor slots on the left.<br>
 * Top row = hotbar (slots 0-8), visually separated from bottom 3 rows (slots 9-35).<br>
 * <br>
 * <b>Armor slots:</b><br>
 * Four slots (Helmet, Chestplate, Pants, Boots) are shown to the left of a<br>
 * 3D player model rendered via an offscreen viewport. Left-click with a matching<br>
 * armor piece to equip. Left-click an occupied armor slot to unequip to cursor.<br>
 * Equipped armor is visible on the 3D model in real time.<br>
 * <br>
 * <b>Interaction:</b><br>
 * Left-click a slot to pick up the stack. Left-click another slot to place/swap/merge.<br>
 * Right-click while holding a stack to place a single item.<br>
 * Click outside the grid while holding to discard the stack.<br>
 * <br>
 * When open the cursor is visible and player movement/look is disabled.<br>
 * Tab or Escape closes the screen and restores first-person controls.
 * @author Pantelis Andrianakis
 * @since March 13th 2026
 */
public class InventoryScreen implements ActionListener
{
	// ========================================================
	// Layout Constants.
	// ========================================================
	
	/** Number of columns in the grid. */
	private static final int GRID_COLS = 9;
	
	/** Number of rows in the grid. */
	private static final int GRID_ROWS = 4;
	
	/** Pixel gap between individual slots. */
	private static final float SLOT_SPACING = 3f;
	
	/** Pixel gap between hotbar and main inventory (for Action Bar label). */
	private static final float SECTION_GAP = 30f;
	
	/** Background padding around the slot fill quad. */
	private static final float SLOT_PADDING = 2f;
	
	/** Z-depth for the full-screen overlay. */
	private static final float Z_OVERLAY = 10f;
	
	/** Z-depth for slot backgrounds. */
	private static final float Z_SLOT_BG = 11f;
	
	/** Z-depth for slot fill quads. */
	private static final float Z_SLOT_FILL = 11.5f;
	
	/** Z-depth for slot labels and counts. */
	private static final float Z_TEXT = 12f;
	
	/** Z-depth for durability bars. */
	private static final float Z_DURABILITY = 12f;
	
	/** Z-depth for the highlight border. */
	private static final float Z_HIGHLIGHT = 10.8f;
	
	/** Z-depth for the held item quad (always on top). */
	private static final float Z_HELD = 15f;
	
	/** Z-depth for tooltip text. */
	private static final float Z_TOOLTIP = 16f;
	
	// ========================================================
	// Player Model Colors (matching EnemyFactory.buildPlayer).
	// ========================================================
	
	private static final ColorRGBA COLOR_SKIN = new ColorRGBA(0.67f, 0.5f, 0.36f, 1.0f);
	private static final ColorRGBA COLOR_HAIR = new ColorRGBA(0.20f, 0.12f, 0.06f, 1.0f);
	private static final ColorRGBA COLOR_IRON = new ColorRGBA(0.62f, 0.62f, 0.65f, 1.0f);
	private static final ColorRGBA COLOR_IRON_DARK = new ColorRGBA(0.48f, 0.48f, 0.52f, 1.0f);
	private static final ColorRGBA COLOR_IRON_LIGHT = new ColorRGBA(0.72f, 0.72f, 0.76f, 1.0f);
	private static final ColorRGBA COLOR_GOLD = new ColorRGBA(0.85f, 0.70f, 0.25f, 1.0f);
	private static final ColorRGBA COLOR_GOLD_DARK = new ColorRGBA(0.70f, 0.55f, 0.15f, 1.0f);
	private static final ColorRGBA COLOR_GOLD_LIGHT = new ColorRGBA(0.95f, 0.82f, 0.35f, 1.0f);
	private static final ColorRGBA COLOR_EYE_GREEN = new ColorRGBA(0.04f, 0.30f, 0.10f, 1.0f);
	private static final ColorRGBA COLOR_EYE_WHITE = new ColorRGBA(0.92f, 0.92f, 0.92f, 1.0f);
	private static final ColorRGBA COLOR_PANTS_BROWN = new ColorRGBA(0.35f, 0.25f, 0.15f, 1.0f);
	
	/** Armor slot label letters shown in empty armor slots. */
	// @formatter:off
	private static final String[] ARMOR_SLOT_LABELS = { "Head", "Chest", "Pants", "Boots" };
	// @formatter:on
	
	// ========================================================
	// Colors.
	// ========================================================
	
	private static final ColorRGBA COLOR_OVERLAY = new ColorRGBA(0.0f, 0.0f, 0.0f, 0.65f);
	private static final ColorRGBA COLOR_SLOT_BG = new ColorRGBA(0.15f, 0.15f, 0.15f, 0.85f);
	private static final ColorRGBA COLOR_SLOT_EMPTY = new ColorRGBA(0.25f, 0.25f, 0.25f, 0.6f);
	private static final ColorRGBA COLOR_HOTBAR_LABEL = new ColorRGBA(0.5f, 0.5f, 0.5f, 0.4f);
	private static final ColorRGBA COLOR_HOVER = new ColorRGBA(1.0f, 1.0f, 1.0f, 0.15f);
	private static final ColorRGBA COLOR_TEXT = new ColorRGBA(1.0f, 1.0f, 1.0f, 1.0f);
	private static final ColorRGBA COLOR_TEXT_SHADOW = new ColorRGBA(0.0f, 0.0f, 0.0f, 0.8f);
	private static final ColorRGBA COLOR_TOOLTIP_BG = new ColorRGBA(0.1f, 0.1f, 0.1f, 0.9f);
	private static final ColorRGBA COLOR_DURABILITY_GREEN = new ColorRGBA(0.2f, 0.85f, 0.2f, 1.0f);
	private static final ColorRGBA COLOR_DURABILITY_YELLOW = new ColorRGBA(0.9f, 0.85f, 0.2f, 1.0f);
	private static final ColorRGBA COLOR_DURABILITY_RED = new ColorRGBA(0.9f, 0.2f, 0.2f, 1.0f);
	private static final ColorRGBA COLOR_ARMOR_SLOT_EMPTY = new ColorRGBA(0.30f, 0.30f, 0.35f, 0.6f);
	
	// ========================================================
	// Fields.
	// ========================================================
	
	private final Inventory _inventory;
	private final PlayerController _playerController;
	private final BlockInteraction _blockInteraction;
	
	/** Drop manager for spawning world drops when items are discarded from the inventory. */
	private DropManager _dropManager;
	
	/** World reference for ground-level lookups when dropping items. */
	private World _world;
	
	private final Node _guiNode;
	private final Node _screenNode;
	private final InputManager _inputManager;
	private final BitmapFont _font;
	private final BitmapFont _tooltipFont;
	private final int _screenWidth;
	private final int _screenHeight;
	
	/** Computed slot size in pixels (proportional to screen height). */
	private final float _slotSize;
	
	/** Whether the inventory screen is currently visible. */
	private boolean _open;
	
	// Per-slot geometry and text.
	private final Geometry[] _slotBg = new Geometry[Inventory.TOTAL_SLOTS];
	private final Geometry[] _slotFill = new Geometry[Inventory.TOTAL_SLOTS];
	private final Material[] _slotFillMat = new Material[Inventory.TOTAL_SLOTS];
	private final BitmapText[] _slotLabel = new BitmapText[Inventory.TOTAL_SLOTS];
	private final BitmapText[] _slotCount = new BitmapText[Inventory.TOTAL_SLOTS];
	private final BitmapText[] _slotCountShadow = new BitmapText[Inventory.TOTAL_SLOTS];
	private final Geometry[] _slotDurBar = new Geometry[Inventory.TOTAL_SLOTS];
	private final Material[] _slotDurMat = new Material[Inventory.TOTAL_SLOTS];
	
	/** Hotbar row label numbers (1-9) shown faintly in each hotbar slot. */
	private final BitmapText[] _hotbarNumbers = new BitmapText[Inventory.HOTBAR_SLOTS];
	
	/** Screen-space X origin of each slot. */
	private final float[] _slotX = new float[Inventory.TOTAL_SLOTS];
	
	/** Screen-space Y origin of each slot. */
	private final float[] _slotY = new float[Inventory.TOTAL_SLOTS];
	
	// Armor slot geometry and text (4 slots: HELMET, CHESTPLATE, PANTS, BOOTS).
	private final Geometry[] _armorSlotBg = new Geometry[ArmorSlot.COUNT];
	private final Geometry[] _armorSlotFill = new Geometry[ArmorSlot.COUNT];
	private final Material[] _armorSlotFillMat = new Material[ArmorSlot.COUNT];
	private final BitmapText[] _armorSlotLabel = new BitmapText[ArmorSlot.COUNT];
	private final Geometry[] _armorSlotDurBar = new Geometry[ArmorSlot.COUNT];
	private final Material[] _armorSlotDurMat = new Material[ArmorSlot.COUNT];
	private final float[] _armorSlotX = new float[ArmorSlot.COUNT];
	private final float[] _armorSlotY = new float[ArmorSlot.COUNT];
	
	// 3D player model rendered via offscreen viewport.
	private ViewPort _modelViewPort;
	private Camera _modelCamera;
	private Node _modelScene;
	private Node _modelRootNode;
	private Geometry _modelDisplayQuad;
	private Material _modelDisplayMat;
	private Texture2D _modelRenderTexture;
	
	/** Offscreen render texture dimensions. */
	private static final int MODEL_TEX_WIDTH = 256;
	private static final int MODEL_TEX_HEIGHT = 512;
	
	// Toggleable 3D armor nodes on the player model.
	private Node _armor3dHelmet;
	private Node _armor3dChestplate;
	private Node _armor3dPants;
	private Node _armor3dBoots;
	
	// Per-armor-piece materials (recolored dynamically for iron vs gold).
	private final Material[] _armorMatsMain = new Material[ArmorSlot.COUNT];
	private final Material[] _armorMatsDark = new Material[ArmorSlot.COUNT];
	private final Material[] _armorMatsLight = new Material[ArmorSlot.COUNT];
	
	// Base clothing nodes (hidden when corresponding armor is equipped).
	private Geometry _basePantsLeft;
	private Geometry _basePantsRight;
	private Geometry _baseWaistband;
	private Node _baseHair;
	
	/** Model Y-axis rotation angle in radians (mouse-drag controlled). */
	private float _modelRotation;
	
	/** Whether the left mouse button is currently held. */
	private boolean _mouseDown;
	
	/** Previous cursor X position for computing drag delta. */
	private float _prevCursorX;
	
	/** Screen-space bounds of the 3D model display quad. */
	private float _modelDisplayX;
	private float _modelDisplayY;
	private float _modelDisplayWidth;
	private float _modelDisplayHeight;
	
	// Hover highlight.
	private Geometry _hoverHighlight;
	private Material _hoverHighlightMat;
	private int _hoveredSlot = -1;
	private int _hoveredArmorIndex = -1;
	
	// Tooltip.
	private Geometry _tooltipBg;
	private Material _tooltipBgMat;
	private BitmapText _tooltipText;
	private BitmapText _tooltipTextShadow;
	
	// Held item (being moved by cursor).
	private ItemInstance _heldStack;
	private Geometry _heldQuad;
	private Material _heldQuadMat;
	private BitmapText _heldLabel;
	private BitmapText _heldCount;
	private BitmapText _heldCountShadow;
	
	/** Title text at top of inventory grid. */
	private BitmapText _titleText;
	private BitmapText _titleTextShadow;
	
	/** Action Bar label between hotbar and inventory. */
	private BitmapText _actionBarText;
	private BitmapText _actionBarTextShadow;
	
	/** Armor label above the armor slots. */
	private BitmapText _armorTitleText;
	private BitmapText _armorTitleTextShadow;
	
	/** Shift tracking. */
	private boolean _shiftDown;
	private final ActionListener _shiftListener = (name, isPressed, tpf) ->
	{
		if (name.equals("SHIFT_LEFT") || name.equals("SHIFT_RIGHT"))
		{
			_shiftDown = isPressed;
		}
	};
	
	/** X position of the armor panel area. */
	private float _armorPanelX;
	
	/** Y position of the armor panel area (bottom). */
	private float _armorPanelY;
	
	// ========================================================
	// Constructor.
	// ========================================================
	
	/**
	 * Creates the inventory screen (hidden initially).
	 * @param playerController the player controller (for input register/unregister)
	 * @param blockInteraction the block interaction handler (for input register/unregister)
	 */
	public InventoryScreen(PlayerController playerController, BlockInteraction blockInteraction)
	{
		_playerController = playerController;
		_blockInteraction = blockInteraction;
		_inventory = playerController.getInventory();
		
		final SimpleCraft app = SimpleCraft.getInstance();
		_guiNode = app.getGuiNode();
		_inputManager = app.getInputManager();
		_screenWidth = app.getCamera().getWidth();
		_screenHeight = app.getCamera().getHeight();
		
		// Slot size proportional to screen height.
		_slotSize = Math.max(36f, _screenHeight * 0.05f);
		
		// Fonts.
		final int fontSize = Math.max(10, (int) (_screenHeight * 0.016f));
		_font = FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_REGULAR_PATH, Font.PLAIN, fontSize);
		
		final int tooltipSize = Math.max(12, (int) (_screenHeight * 0.018f));
		_tooltipFont = FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_REGULAR_PATH, Font.PLAIN, tooltipSize);
		
		// Build all UI elements.
		_screenNode = new Node("InventoryScreen");
		
		buildOverlay(app);
		computeSlotPositions();
		buildSlots(app);
		buildArmorSlots(app);
		buildPlayerModel(app);
		buildHoverHighlight(app);
		buildTooltip(app);
		buildHeldItem(app);
		buildTitle(app);
		
		// Hidden initially.
		_screenNode.setCullHint(Node.CullHint.Always);
		_guiNode.attachChild(_screenNode);
	}
	
	// ========================================================
	// Build Methods.
	// ========================================================
	
	private void buildOverlay(SimpleCraft app)
	{
		final Quad overlayQuad = new Quad(_screenWidth, _screenHeight);
		final Geometry overlay = new Geometry("InvOverlay", overlayQuad);
		final Material mat = createColorMaterial(COLOR_OVERLAY, app);
		overlay.setMaterial(mat);
		overlay.setQueueBucket(Bucket.Gui);
		overlay.setLocalTranslation(0, 0, Z_OVERLAY);
		_screenNode.attachChild(overlay);
	}
	
	/**
	 * Computes screen-space positions for all 36 inventory slots and 4 armor slots.<br>
	 * The main grid is shifted right to make room for the armor panel on the left.<br>
	 * Layout: armor panel + player model on left, hotbar row at top of grid,<br>
	 * 3 main rows below with a gap.
	 */
	private void computeSlotPositions()
	{
		final float totalGridWidth = GRID_COLS * _slotSize + (GRID_COLS - 1) * SLOT_SPACING;
		
		// 4 rows (1 hotbar + 3 main) with gap between hotbar and main for Action Bar label.
		final float totalGridHeight = GRID_ROWS * _slotSize + (GRID_ROWS - 1) * SLOT_SPACING + SECTION_GAP;
		
		// Armor panel dimensions.
		final float armorPanelWidth = _slotSize;
		final float playerModelWidth = _slotSize * 1.6f;
		final float armorGap = _slotSize * 0.5f;
		final float panelGridGap = _slotSize * 0.5f;
		final float armorAreaWidth = armorPanelWidth + armorGap + playerModelWidth + panelGridGap;
		
		// Combined width of armor area + grid, centered on screen.
		final float combinedWidth = armorAreaWidth + totalGridWidth;
		final float combinedStartX = (_screenWidth - combinedWidth) / 2f;
		
		// Grid starts after the armor area.
		final float gridStartX = combinedStartX + armorAreaWidth;
		final float gridStartY = (_screenHeight - totalGridHeight) / 2f;
		
		// Hotbar row (slots 0-8): bottom row.
		final float hotbarY = gridStartY;
		for (int col = 0; col < GRID_COLS; col++)
		{
			_slotX[col] = gridStartX + col * (_slotSize + SLOT_SPACING);
			_slotY[col] = hotbarY;
		}
		
		// Main inventory rows (slots 9-35): 3 rows above hotbar with gap for Action Bar label.
		final float mainStartY = hotbarY + _slotSize + SLOT_SPACING + SECTION_GAP;
		for (int row = 0; row < 3; row++)
		{
			for (int col = 0; col < GRID_COLS; col++)
			{
				final int slotIndex = 9 + row * GRID_COLS + col;
				_slotX[slotIndex] = gridStartX + col * (_slotSize + SLOT_SPACING);
				
				// Row 0 = bottom of main (slots 27-35), row 2 = top (slots 9-17).
				_slotY[slotIndex] = mainStartY + (2 - row) * (_slotSize + SLOT_SPACING);
			}
		}
		
		// Armor panel: 4 slots, helmet aligned with the top inventory row.
		final float topInvRowY = mainStartY + 2 * (_slotSize + SLOT_SPACING);
		
		// Helmet (i=0) is at armorStartY + 3*(slotSize+spacing), so solve for armorStartY.
		final float armorStartY = topInvRowY - 3 * (_slotSize + SLOT_SPACING);
		
		_armorPanelX = combinedStartX;
		_armorPanelY = armorStartY;
		
		for (int i = 0; i < ArmorSlot.COUNT; i++)
		{
			_armorSlotX[i] = combinedStartX;
			_armorSlotY[i] = armorStartY + (ArmorSlot.COUNT - 1 - i) * (_slotSize + SLOT_SPACING);
		}
	}
	
	private void buildSlots(SimpleCraft app)
	{
		for (int i = 0; i < Inventory.TOTAL_SLOTS; i++)
		{
			// Background quad (slightly larger than fill for border effect).
			final float bgSize = _slotSize + SLOT_PADDING * 2;
			_slotBg[i] = createQuad("InvSlotBg" + i, bgSize, bgSize, COLOR_SLOT_BG, app);
			_slotBg[i].setLocalTranslation(_slotX[i] - SLOT_PADDING, _slotY[i] - SLOT_PADDING, Z_SLOT_BG);
			_screenNode.attachChild(_slotBg[i]);
			
			// Fill quad (colored by item type).
			_slotFillMat[i] = createColorMaterial(COLOR_SLOT_EMPTY, app);
			_slotFill[i] = createQuadWithMaterial("InvSlotFill" + i, _slotSize, _slotSize, _slotFillMat[i]);
			_slotFill[i].setLocalTranslation(_slotX[i], _slotY[i], Z_SLOT_FILL);
			_screenNode.attachChild(_slotFill[i]);
			
			// Label text (W, P, A, S, +, etc.) - centered in slot.
			_slotLabel[i] = new BitmapText(_font);
			_slotLabel[i].setText("");
			_slotLabel[i].setSize(_font.getCharSet().getRenderedSize() * 1.2f);
			_slotLabel[i].setColor(COLOR_TEXT.clone());
			_slotLabel[i].setLocalTranslation(_slotX[i], _slotY[i] + _slotSize, Z_TEXT);
			_slotLabel[i].setCullHint(BitmapText.CullHint.Always);
			_screenNode.attachChild(_slotLabel[i]);
			
			// Count text (bottom-right corner of slot).
			_slotCountShadow[i] = new BitmapText(_font);
			_slotCountShadow[i].setText("");
			_slotCountShadow[i].setSize(_font.getCharSet().getRenderedSize());
			_slotCountShadow[i].setColor(COLOR_TEXT_SHADOW.clone());
			_slotCountShadow[i].setCullHint(BitmapText.CullHint.Always);
			_screenNode.attachChild(_slotCountShadow[i]);
			
			_slotCount[i] = new BitmapText(_font);
			_slotCount[i].setText("");
			_slotCount[i].setSize(_font.getCharSet().getRenderedSize());
			_slotCount[i].setColor(COLOR_TEXT.clone());
			_slotCount[i].setCullHint(BitmapText.CullHint.Always);
			_screenNode.attachChild(_slotCount[i]);
			
			// Durability bar (bottom of slot, hidden by default).
			_slotDurMat[i] = createColorMaterial(COLOR_DURABILITY_GREEN, app);
			_slotDurBar[i] = createQuadWithMaterial("InvDur" + i, _slotSize, 3f, _slotDurMat[i]);
			_slotDurBar[i].setLocalTranslation(_slotX[i], _slotY[i], Z_DURABILITY);
			_slotDurBar[i].setCullHint(Geometry.CullHint.Always);
			_screenNode.attachChild(_slotDurBar[i]);
			
			// Hotbar slot numbers (1-9) shown faintly.
			if (i < Inventory.HOTBAR_SLOTS)
			{
				_hotbarNumbers[i] = new BitmapText(_font);
				_hotbarNumbers[i].setText(String.valueOf(i + 1));
				_hotbarNumbers[i].setSize(_font.getCharSet().getRenderedSize() * 0.8f);
				_hotbarNumbers[i].setColor(COLOR_HOTBAR_LABEL.clone());
				final float numX = _slotX[i] + 2;
				final float numY = _slotY[i] + _slotSize - 2;
				_hotbarNumbers[i].setLocalTranslation(numX, numY, Z_TEXT);
				_screenNode.attachChild(_hotbarNumbers[i]);
			}
		}
	}
	
	/**
	 * Builds the 4 armor equipment slots to the left of the player model.
	 */
	private void buildArmorSlots(SimpleCraft app)
	{
		for (int i = 0; i < ArmorSlot.COUNT; i++)
		{
			// Background.
			final float bgSize = _slotSize + SLOT_PADDING * 2;
			_armorSlotBg[i] = createQuad("ArmorSlotBg" + i, bgSize, bgSize, COLOR_SLOT_BG, app);
			_armorSlotBg[i].setLocalTranslation(_armorSlotX[i] - SLOT_PADDING, _armorSlotY[i] - SLOT_PADDING, Z_SLOT_BG);
			_screenNode.attachChild(_armorSlotBg[i]);
			
			// Fill.
			_armorSlotFillMat[i] = createColorMaterial(COLOR_ARMOR_SLOT_EMPTY, app);
			_armorSlotFill[i] = createQuadWithMaterial("ArmorSlotFill" + i, _slotSize, _slotSize, _armorSlotFillMat[i]);
			_armorSlotFill[i].setLocalTranslation(_armorSlotX[i], _armorSlotY[i], Z_SLOT_FILL);
			_screenNode.attachChild(_armorSlotFill[i]);
			
			// Label (H, C, P, B shown when empty).
			_armorSlotLabel[i] = new BitmapText(_font);
			_armorSlotLabel[i].setText(ARMOR_SLOT_LABELS[i]);
			_armorSlotLabel[i].setSize(_font.getCharSet().getRenderedSize() * 1.2f);
			_armorSlotLabel[i].setColor(new ColorRGBA(0.5f, 0.5f, 0.55f, 0.5f));
			final float labelWidth = _armorSlotLabel[i].getLineWidth();
			final float labelHeight = _armorSlotLabel[i].getLineHeight();
			final float labelX = _armorSlotX[i] + (_slotSize - labelWidth) / 2f;
			final float labelY = _armorSlotY[i] + (_slotSize + labelHeight) / 2f;
			_armorSlotLabel[i].setLocalTranslation(labelX, labelY, Z_TEXT);
			_screenNode.attachChild(_armorSlotLabel[i]);
			
			// Durability bar.
			_armorSlotDurMat[i] = createColorMaterial(COLOR_DURABILITY_GREEN, app);
			_armorSlotDurBar[i] = createQuadWithMaterial("ArmorDur" + i, _slotSize, 3f, _armorSlotDurMat[i]);
			_armorSlotDurBar[i].setLocalTranslation(_armorSlotX[i], _armorSlotY[i], Z_DURABILITY);
			_armorSlotDurBar[i].setCullHint(Geometry.CullHint.Always);
			_screenNode.attachChild(_armorSlotDurBar[i]);
		}
	}
	
	/**
	 * Builds a 3D player model rendered via an offscreen viewport.<br>
	 * The model matches the proportions from {@code EnemyFactory.buildPlayer()} and<br>
	 * includes toggleable armor nodes that show/hide based on equipped items.<br>
	 * The result is rendered to a texture and displayed on a GUI quad.
	 */
	private void buildPlayerModel(SimpleCraft app)
	{
		// ---- 3D scene setup ----
		_modelScene = new Node("ModelScene");
		_modelRootNode = new Node("ModelRoot");
		_modelScene.attachChild(_modelRootNode);
		
		// Materials (Unshaded - no lighting needed in the offscreen scene).
		final Material skinMat = makeModelMaterial(COLOR_SKIN, app);
		final Material skinDarkMat = makeModelMaterial(new ColorRGBA(0.60f, 0.45f, 0.32f, 1.0f), app);
		final Material hairMat = makeModelMaterial(COLOR_HAIR, app);
		final Material eyeWhiteMat = makeModelMaterial(COLOR_EYE_WHITE, app);
		final Material eyeGreenMat = makeModelMaterial(COLOR_EYE_GREEN, app);
		final Material mouthMat = makeModelMaterial(new ColorRGBA(0.45f, 0.30f, 0.25f, 1.0f), app);
		final Material pantsMat = makeModelMaterial(COLOR_PANTS_BROWN, app);
		
		// Per-piece armor materials (start as iron, recolored dynamically in updatePlayerModelOverlays).
		for (int i = 0; i < ArmorSlot.COUNT; i++)
		{
			_armorMatsMain[i] = makeModelMaterial(COLOR_IRON, app);
			_armorMatsDark[i] = makeModelMaterial(COLOR_IRON_DARK, app);
			_armorMatsLight[i] = makeModelMaterial(COLOR_IRON_LIGHT, app);
		}
		
		// ========================================================
		// Base player model (always visible - skin with brown pants).
		// Proportions match EnemyFactory.buildPlayer() / buildZombie().
		// ========================================================
		
		// Body (skin - armor will overlay this when chestplate is equipped).
		final Node bodyNode = makeModelPivot("Body", makeModelBox("BodyBox", 0.3f, 0.33f, 0.15f, skinMat, 0, 0, 0), 0, 1.27f, 0);
		_modelRootNode.attachChild(bodyNode);
		
		// Brown waistband.
		_baseWaistband = makeModelBox("Waistband", 0.31f, 0.12f, 0.16f, pantsMat, 0, 0.88f, 0);
		_modelRootNode.attachChild(_baseWaistband);
		
		// Neck.
		_modelRootNode.attachChild(makeModelBox("Neck", 0.15f, 0.04f, 0.12f, skinMat, 0, 1.64f, 0));
		
		// Head.
		final Node headNode = makeModelPivot("Head", makeModelBox("HeadBox", 0.2f, 0.2f, 0.2f, skinMat, 0, 0.2f, 0), 0, 1.65f, 0);
		_modelRootNode.attachChild(headNode);
		
		// Hair - split into top (hidden by helmet) and sides/back (always visible).
		_baseHair = new Node("BaseHairTop");
		headNode.attachChild(_baseHair);
		_baseHair.attachChild(makeModelBox("HairCap", 0.21f, 0.08f, 0.19f, hairMat, 0, 0.37f, 0.02f));
		_baseHair.attachChild(makeModelBox("HairFringe", 0.21f, 0.03f, 0.02f, hairMat, 0, 0.37f, -0.19f));
		
		// Sides and back - always visible (poke out under the helmet).
		headNode.attachChild(makeModelBox("HairBack", 0.21f, 0.16f, 0.02f, hairMat, 0, 0.26f, 0.21f));
		headNode.attachChild(makeModelBox("HairLeft", 0.02f, 0.10f, 0.19f, hairMat, -0.21f, 0.30f, 0.04f));
		headNode.attachChild(makeModelBox("HairRight", 0.02f, 0.10f, 0.19f, hairMat, 0.21f, 0.30f, 0.04f));
		
		// Eyes.
		headNode.attachChild(makeModelBox("LeftEyeWhite", 0.035f, 0.03f, 0.015f, eyeWhiteMat, -0.07f, 0.24f, -0.215f));
		headNode.attachChild(makeModelBox("RightEyeWhite", 0.035f, 0.03f, 0.015f, eyeWhiteMat, 0.07f, 0.24f, -0.215f));
		headNode.attachChild(makeModelBox("LeftIris", 0.018f, 0.018f, 0.005f, eyeGreenMat, -0.07f, 0.24f, -0.235f));
		headNode.attachChild(makeModelBox("RightIris", 0.018f, 0.018f, 0.005f, eyeGreenMat, 0.07f, 0.24f, -0.235f));
		
		// Nose.
		headNode.attachChild(makeModelBox("Nose", 0.02f, 0.025f, 0.02f, skinDarkMat, 0, 0.17f, -0.22f));
		
		// Mouth.
		headNode.attachChild(makeModelBox("Mouth", 0.05f, 0.01f, 0.015f, mouthMat, 0, 0.1f, -0.215f));
		
		// Legs (skin with brown pants overlay).
		final Node leftLeg = makeModelPivot("LeftLeg", makeModelBox("LeftLegBox", 0.125f, 0.39f, 0.125f, skinMat, 0, -0.39f, 0), -0.175f, 0.8f, 0);
		_modelRootNode.attachChild(leftLeg);
		_basePantsLeft = makeModelBox("LPants", 0.135f, 0.25f, 0.135f, pantsMat, 0, -0.15f, 0);
		leftLeg.attachChild(_basePantsLeft);
		
		final Node rightLeg = makeModelPivot("RightLeg", makeModelBox("RightLegBox", 0.125f, 0.39f, 0.125f, skinMat, 0, -0.39f, 0), 0.175f, 0.8f, 0);
		_modelRootNode.attachChild(rightLeg);
		_basePantsRight = makeModelBox("RPants", 0.135f, 0.25f, 0.135f, pantsMat, 0, -0.15f, 0);
		rightLeg.attachChild(_basePantsRight);
		
		// Arms (skin only - shoulder plates are part of chestplate armor).
		final Node leftArm = makeModelPivot("LeftArm", makeModelBox("LeftArmBox", 0.125f, 0.4f, 0.125f, skinMat, 0, -0.4f, 0), -0.425f, 1.55f, 0);
		_modelRootNode.attachChild(leftArm);
		
		final Node rightArm = makeModelPivot("RightArm", makeModelBox("RightArmBox", 0.125f, 0.4f, 0.125f, skinMat, 0, -0.4f, 0), 0.425f, 1.55f, 0);
		_modelRootNode.attachChild(rightArm);
		
		// ========================================================
		// Toggleable 3D armor overlay nodes (hidden by default).
		// Positions are absolute since the model is static (no limb animation).
		// ========================================================
		
		// Helmet (attaches to head pivot node so it rotates with head).
		_armor3dHelmet = new Node("ArmorHelmet");
		headNode.attachChild(_armor3dHelmet);
		_armor3dHelmet.attachChild(makeModelBox("HelmetCap", 0.23f, 0.08f, 0.23f, _armorMatsMain[0], 0, 0.44f, 0));
		_armor3dHelmet.attachChild(makeModelBox("HelmetBand", 0.24f, 0.06f, 0.24f, _armorMatsLight[0], 0, 0.34f, 0));
		_armor3dHelmet.attachChild(makeModelBox("NoseGuard", 0.02f, 0.10f, 0.025f, _armorMatsDark[0], 0, 0.27f, -0.24f));
		_armor3dHelmet.setCullHint(Node.CullHint.Always);
		
		// Chestplate (iron body + chest detail + waistband + shoulder plates).
		_armor3dChestplate = new Node("ArmorChestplate");
		_modelRootNode.attachChild(_armor3dChestplate);
		_armor3dChestplate.attachChild(makeModelBox("IronBody", 0.31f, 0.34f, 0.18f, _armorMatsMain[1], 0, 1.27f, 0));
		_armor3dChestplate.attachChild(makeModelBox("ChestPlate", 0.22f, 0.2f, 0.005f, _armorMatsDark[1], 0, 1.32f, -0.20f));
		_armor3dChestplate.attachChild(makeModelBox("IronWaist", 0.32f, 0.13f, 0.17f, _armorMatsDark[1], 0, 0.88f, 0));
		_armor3dChestplate.attachChild(makeModelBox("LShoulderPlate", 0.15f, 0.18f, 0.15f, _armorMatsLight[1], -0.425f, 1.43f, 0));
		_armor3dChestplate.attachChild(makeModelBox("RShoulderPlate", 0.15f, 0.18f, 0.15f, _armorMatsLight[1], 0.425f, 1.43f, 0));
		_armor3dChestplate.setCullHint(Node.CullHint.Always);
		
		// Iron pants (overlays the brown pants on both legs).
		_armor3dPants = new Node("ArmorPants");
		_modelRootNode.attachChild(_armor3dPants);
		_armor3dPants.attachChild(makeModelBox("LIronPants", 0.135f, 0.25f, 0.135f, _armorMatsDark[2], -0.175f, 0.65f, 0));
		_armor3dPants.attachChild(makeModelBox("RIronPants", 0.135f, 0.25f, 0.135f, _armorMatsDark[2], 0.175f, 0.65f, 0));
		_armor3dPants.attachChild(makeModelBox("IronBelt", 0.31f, 0.12f, 0.16f, _armorMatsMain[2], 0, 0.88f, 0));
		_armor3dPants.setCullHint(Node.CullHint.Always);
		
		// Iron boots (covers lower portion of each leg).
		_armor3dBoots = new Node("ArmorBoots");
		_modelRootNode.attachChild(_armor3dBoots);
		_armor3dBoots.attachChild(makeModelBox("LBoots", 0.145f, 0.21f, 0.145f, _armorMatsMain[3], -0.175f, 0.2f, 0));
		_armor3dBoots.attachChild(makeModelBox("RBoots", 0.145f, 0.21f, 0.145f, _armorMatsMain[3], 0.175f, 0.2f, 0));
		_armor3dBoots.setCullHint(Node.CullHint.Always);
		
		// ========================================================
		// Offscreen viewport: renders the 3D model to a texture.
		// ========================================================
		
		// Camera framing the full model (feet to head).
		_modelCamera = new Camera(MODEL_TEX_WIDTH, MODEL_TEX_HEIGHT);
		_modelCamera.setFrustumPerspective(45f, (float) MODEL_TEX_WIDTH / MODEL_TEX_HEIGHT, 0.1f, 10f);
		_modelCamera.setLocation(new Vector3f(0, 1.05f, -3.2f));
		_modelCamera.lookAt(new Vector3f(0, 1.05f, 0), Vector3f.UNIT_Y);
		
		// Render target texture.
		_modelRenderTexture = new Texture2D(MODEL_TEX_WIDTH, MODEL_TEX_HEIGHT, Format.RGBA8);
		_modelRenderTexture.setMinFilter(MinFilter.BilinearNoMipMaps);
		_modelRenderTexture.setMagFilter(MagFilter.Bilinear);
		
		// Framebuffer.
		final FrameBuffer fb = new FrameBuffer(MODEL_TEX_WIDTH, MODEL_TEX_HEIGHT, 1);
		fb.setDepthTarget(FrameBufferTarget.newTarget(Format.Depth));
		fb.addColorTarget(FrameBufferTarget.newTarget(_modelRenderTexture));
		
		// Pre-render viewport (renders before the main scene each frame).
		_modelViewPort = app.getRenderManager().createPreView("InventoryPlayerModel", _modelCamera);
		_modelViewPort.setClearFlags(true, true, true);
		_modelViewPort.setBackgroundColor(new ColorRGBA(0, 0, 0, 0));
		_modelViewPort.setOutputFrameBuffer(fb);
		_modelViewPort.attachScene(_modelScene);
		_modelViewPort.setEnabled(false); // Only active when inventory is open.
		
		// Initialize scene graph state.
		_modelScene.updateGeometricState();
		
		// ========================================================
		// GUI display quad: shows the rendered model texture.
		// ========================================================
		
		final float armorGap = _slotSize * 0.5f;
		_modelDisplayX = _armorPanelX + _slotSize + armorGap;
		_modelDisplayWidth = _slotSize * 1.6f;
		final float armorTotalHeight = 4 * _slotSize + 3 * SLOT_SPACING;
		_modelDisplayY = _armorPanelY;
		_modelDisplayHeight = armorTotalHeight;
		
		_modelDisplayMat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
		_modelDisplayMat.setTexture("ColorMap", _modelRenderTexture);
		_modelDisplayMat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
		
		final Quad displayQuad = new Quad(_modelDisplayWidth, _modelDisplayHeight);
		_modelDisplayQuad = new Geometry("ModelDisplay", displayQuad);
		_modelDisplayQuad.setMaterial(_modelDisplayMat);
		_modelDisplayQuad.setQueueBucket(Bucket.Gui);
		_modelDisplayQuad.setLocalTranslation(_modelDisplayX, _modelDisplayY, Z_SLOT_FILL);
		_screenNode.attachChild(_modelDisplayQuad);
	}
	
	private void buildHoverHighlight(SimpleCraft app)
	{
		_hoverHighlightMat = createColorMaterial(COLOR_HOVER, app);
		_hoverHighlight = createQuadWithMaterial("InvHover", _slotSize, _slotSize, _hoverHighlightMat);
		_hoverHighlight.setLocalTranslation(0, 0, Z_HIGHLIGHT);
		_hoverHighlight.setCullHint(Geometry.CullHint.Always);
		_screenNode.attachChild(_hoverHighlight);
	}
	
	private void buildTooltip(SimpleCraft app)
	{
		_tooltipBgMat = createColorMaterial(COLOR_TOOLTIP_BG, app);
		_tooltipBg = createQuadWithMaterial("InvTooltipBg", 100, 24, _tooltipBgMat);
		_tooltipBg.setLocalTranslation(0, 0, Z_TOOLTIP - 0.1f);
		_tooltipBg.setCullHint(Geometry.CullHint.Always);
		_screenNode.attachChild(_tooltipBg);
		
		_tooltipTextShadow = new BitmapText(_tooltipFont);
		_tooltipTextShadow.setText("");
		_tooltipTextShadow.setSize(_tooltipFont.getCharSet().getRenderedSize());
		_tooltipTextShadow.setColor(COLOR_TEXT_SHADOW.clone());
		_tooltipTextShadow.setCullHint(BitmapText.CullHint.Always);
		_screenNode.attachChild(_tooltipTextShadow);
		
		_tooltipText = new BitmapText(_tooltipFont);
		_tooltipText.setText("");
		_tooltipText.setSize(_tooltipFont.getCharSet().getRenderedSize());
		_tooltipText.setColor(COLOR_TEXT.clone());
		_tooltipText.setCullHint(BitmapText.CullHint.Always);
		_screenNode.attachChild(_tooltipText);
	}
	
	private void buildHeldItem(SimpleCraft app)
	{
		_heldQuadMat = createColorMaterial(COLOR_SLOT_EMPTY, app);
		_heldQuad = createQuadWithMaterial("InvHeld", _slotSize * 0.8f, _slotSize * 0.8f, _heldQuadMat);
		_heldQuad.setLocalTranslation(0, 0, Z_HELD);
		_heldQuad.setCullHint(Geometry.CullHint.Always);
		_screenNode.attachChild(_heldQuad);
		
		_heldLabel = new BitmapText(_font);
		_heldLabel.setText("");
		_heldLabel.setSize(_font.getCharSet().getRenderedSize() * 1.2f);
		_heldLabel.setColor(COLOR_TEXT.clone());
		_heldLabel.setCullHint(BitmapText.CullHint.Always);
		_screenNode.attachChild(_heldLabel);
		
		_heldCountShadow = new BitmapText(_font);
		_heldCountShadow.setText("");
		_heldCountShadow.setSize(_font.getCharSet().getRenderedSize());
		_heldCountShadow.setColor(COLOR_TEXT_SHADOW.clone());
		_heldCountShadow.setCullHint(BitmapText.CullHint.Always);
		_screenNode.attachChild(_heldCountShadow);
		
		_heldCount = new BitmapText(_font);
		_heldCount.setText("");
		_heldCount.setSize(_font.getCharSet().getRenderedSize());
		_heldCount.setColor(COLOR_TEXT.clone());
		_heldCount.setCullHint(BitmapText.CullHint.Always);
		_screenNode.attachChild(_heldCount);
	}
	
	private void buildTitle(SimpleCraft app)
	{
		final int titleSize = Math.max(14, (int) (_screenHeight * 0.022f));
		final BitmapFont titleFont = FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_REGULAR_PATH, Font.PLAIN, titleSize);
		
		// Grid center X for label centering.
		final float gridCenterX = (_slotX[0] + _slotX[8] + _slotSize) / 2f;
		
		// "Inventory" label above the top main inventory row.
		_titleTextShadow = new BitmapText(titleFont);
		_titleTextShadow.setText("Inventory");
		_titleTextShadow.setSize(titleSize);
		_titleTextShadow.setColor(COLOR_TEXT_SHADOW.clone());
		_screenNode.attachChild(_titleTextShadow);
		
		_titleText = new BitmapText(titleFont);
		_titleText.setText("Inventory");
		_titleText.setSize(titleSize);
		_titleText.setColor(COLOR_TEXT.clone());
		_screenNode.attachChild(_titleText);
		
		// _slotY[9] is the top row of main inventory (slots 9-17).
		final float titleWidth = _titleText.getLineWidth();
		final float titleX = gridCenterX - titleWidth / 2f;
		final float titleY = _slotY[9] + _slotSize + SLOT_PADDING + _titleText.getLineHeight() + 2;
		_titleText.setLocalTranslation(titleX, titleY, Z_TEXT);
		_titleTextShadow.setLocalTranslation(titleX + 1, titleY - 1, Z_TEXT - 0.1f);
		
		// "Action Bar" label between hotbar and inventory.
		_actionBarTextShadow = new BitmapText(titleFont);
		_actionBarTextShadow.setText("Action Bar");
		_actionBarTextShadow.setSize(titleSize);
		_actionBarTextShadow.setColor(COLOR_TEXT_SHADOW.clone());
		_screenNode.attachChild(_actionBarTextShadow);
		
		_actionBarText = new BitmapText(titleFont);
		_actionBarText.setText("Action Bar");
		_actionBarText.setSize(titleSize);
		_actionBarText.setColor(COLOR_TEXT.clone());
		_screenNode.attachChild(_actionBarText);
		
		// Position above the hotbar row, below the inventory.
		final float abWidth = _actionBarText.getLineWidth();
		final float abX = gridCenterX - abWidth / 2f;
		final float abY = _slotY[0] + _slotSize + SLOT_PADDING + _actionBarText.getLineHeight() + 2;
		_actionBarText.setLocalTranslation(abX, abY, Z_TEXT);
		_actionBarTextShadow.setLocalTranslation(abX + 1, abY - 1, Z_TEXT - 0.1f);
		
		// "Armor" label above the armor slots.
		_armorTitleTextShadow = new BitmapText(titleFont);
		_armorTitleTextShadow.setText("Armor");
		_armorTitleTextShadow.setSize(titleSize);
		_armorTitleTextShadow.setColor(COLOR_TEXT_SHADOW.clone());
		_screenNode.attachChild(_armorTitleTextShadow);
		
		_armorTitleText = new BitmapText(titleFont);
		_armorTitleText.setText("Armor");
		_armorTitleText.setSize(titleSize);
		_armorTitleText.setColor(COLOR_TEXT.clone());
		_screenNode.attachChild(_armorTitleText);
		
		// Position above the top armor slot.
		final float armorTitleWidth = _armorTitleText.getLineWidth();
		final float armorTitleX = _armorSlotX[0] + (_slotSize - armorTitleWidth) / 2f;
		final float armorTitleY = _armorSlotY[0] + _slotSize + SLOT_PADDING + _armorTitleText.getLineHeight() + 2;
		_armorTitleText.setLocalTranslation(armorTitleX, armorTitleY, Z_TEXT);
		_armorTitleTextShadow.setLocalTranslation(armorTitleX + 1, armorTitleY - 1, Z_TEXT - 0.1f);
	}
	
	// ========================================================
	// Open / Close.
	// ========================================================
	
	/**
	 * Opens the inventory screen, showing the cursor and disabling player controls.
	 */
	public void open()
	{
		if (_open)
		{
			return;
		}
		
		_open = true;
		_heldStack = null;
		_hoveredSlot = -1;
		_hoveredArmorIndex = -1;
		_shiftDown = false;
		_mouseDown = false;
		
		// Show screen.
		_screenNode.setCullHint(Node.CullHint.Never);
		
		// Disable player movement and look.
		_playerController.unregisterInput();
		if (_blockInteraction != null)
		{
			_blockInteraction.unregisterInput();
		}
		
		// Show cursor.
		_inputManager.setCursorVisible(true);
		
		// Register click listeners.
		_inputManager.addListener(this, GameInputManager.ATTACK, GameInputManager.PLACE_BLOCK);
		
		// Register shift key listeners for split-stack detection.
		_inputManager.addMapping("SHIFT_LEFT", new KeyTrigger(KeyInput.KEY_LSHIFT));
		_inputManager.addMapping("SHIFT_RIGHT", new KeyTrigger(KeyInput.KEY_RSHIFT));
		_inputManager.addListener(_shiftListener, "SHIFT_LEFT", "SHIFT_RIGHT");
		
		// Force full slot refresh.
		refreshAllSlots();
		
		// Enable 3D player model viewport.
		_modelViewPort.setEnabled(true);
		_modelRotation = 0.35f; // ~20 degrees, slightly turned right.
	}
	
	/**
	 * Closes the inventory screen, hiding the cursor and restoring player controls.
	 */
	public void close()
	{
		if (!_open)
		{
			return;
		}
		
		_open = false;
		
		// If holding a stack, return it to inventory (or drop into world if full).
		if (_heldStack != null)
		{
			if (!_inventory.addItem(_heldStack))
			{
				dropItemIntoWorld(_heldStack);
			}
			
			_heldStack = null;
		}
		
		// Hide screen.
		_screenNode.setCullHint(Node.CullHint.Always);
		
		// Disable 3D player model viewport.
		_modelViewPort.setEnabled(false);
		
		// Hide held item visuals.
		_heldQuad.setCullHint(Geometry.CullHint.Always);
		_heldLabel.setCullHint(BitmapText.CullHint.Always);
		_heldCount.setCullHint(BitmapText.CullHint.Always);
		_heldCountShadow.setCullHint(BitmapText.CullHint.Always);
		
		// Remove click listeners and shift listeners.
		_inputManager.removeListener(this);
		_inputManager.removeListener(_shiftListener);
		_inputManager.deleteMapping("SHIFT_LEFT");
		_inputManager.deleteMapping("SHIFT_RIGHT");
		
		// Re-enable player controls.
		_playerController.registerInput();
		if (_blockInteraction != null)
		{
			_blockInteraction.registerInput();
		}
		
		// Hide cursor.
		_inputManager.setCursorVisible(false);
	}
	
	/**
	 * Returns true if the inventory screen is currently open.
	 */
	public boolean isOpen()
	{
		return _open;
	}
	
	// ========================================================
	// Update.
	// ========================================================
	
	/**
	 * Updates hover detection, tooltip and held item position each frame.
	 * @param tpf time per frame
	 */
	public void update(float tpf)
	{
		if (!_open)
		{
			return;
		}
		
		// Refresh slot visuals.
		refreshAllSlots();
		
		// Get cursor position.
		final Vector2f cursor = _inputManager.getCursorPosition();
		final float cx = cursor.x;
		final float cy = cursor.y;
		
		// Rotate the 3D player model by mouse drag on the model display area.
		if (_mouseDown)
		{
			final float deltaX = cx - _prevCursorX;
			
			// Only rotate if cursor is over the model display area (or drag started there).
			if (cx >= _modelDisplayX && cx < _modelDisplayX + _modelDisplayWidth && cy >= _modelDisplayY && cy < _modelDisplayY + _modelDisplayHeight)
			{
				_modelRotation += deltaX * 0.01f;
			}
		}
		
		_prevCursorX = cx;
		
		_modelRootNode.setLocalRotation(new Quaternion().fromAngleAxis(_modelRotation, Vector3f.UNIT_Y));
		
		// Update offscreen scene graph state for rendering.
		_modelScene.updateLogicalState(tpf);
		_modelScene.updateGeometricState();
		
		// Determine hovered slot (inventory grid or armor slot).
		_hoveredSlot = getSlotAtPosition(cx, cy);
		_hoveredArmorIndex = getArmorSlotAtPosition(cx, cy);
		
		// Update hover highlight.
		if (_hoveredSlot >= 0)
		{
			_hoverHighlight.setLocalTranslation(_slotX[_hoveredSlot], _slotY[_hoveredSlot], Z_HIGHLIGHT);
			_hoverHighlight.setCullHint(Geometry.CullHint.Never);
		}
		else if (_hoveredArmorIndex >= 0)
		{
			_hoverHighlight.setLocalTranslation(_armorSlotX[_hoveredArmorIndex], _armorSlotY[_hoveredArmorIndex], Z_HIGHLIGHT);
			_hoverHighlight.setCullHint(Geometry.CullHint.Never);
		}
		else
		{
			_hoverHighlight.setCullHint(Geometry.CullHint.Always);
		}
		
		// Update tooltip.
		updateTooltip(cx, cy);
		
		// Update held item position at cursor.
		updateHeldItemVisual(cx, cy);
	}
	
	/**
	 * Refreshes the visual state of all 36 inventory slots and 4 armor slots.
	 */
	private void refreshAllSlots()
	{
		for (int i = 0; i < Inventory.TOTAL_SLOTS; i++)
		{
			final ItemInstance stack = _inventory.getSlot(i);
			updateSlotVisual(i, stack);
		}
		
		// Refresh armor slots and player model overlays.
		for (int i = 0; i < ArmorSlot.COUNT; i++)
		{
			final ArmorSlot slot = ArmorSlot.fromIndex(i);
			final ItemInstance armorItem = _inventory.getArmorSlot(slot);
			updateArmorSlotVisual(i, armorItem);
		}
		
		updatePlayerModelOverlays();
	}
	
	/**
	 * Updates the visual representation of a single inventory slot.
	 */
	private void updateSlotVisual(int index, ItemInstance stack)
	{
		if (stack == null || stack.isEmpty())
		{
			// Empty slot - clear any texture and show empty color.
			_slotFillMat[index].clearParam("ColorMap");
			_slotFillMat[index].setColor("Color", COLOR_SLOT_EMPTY);
			_slotLabel[index].setCullHint(BitmapText.CullHint.Always);
			_slotCount[index].setCullHint(BitmapText.CullHint.Always);
			_slotCountShadow[index].setCullHint(BitmapText.CullHint.Always);
			_slotDurBar[index].setCullHint(Geometry.CullHint.Always);
			return;
		}
		
		final ItemTemplate template = stack.getTemplate();
		
		// Try to resolve a sprite texture (drops -> items -> blocks paths).
		final Texture slotTexture = ItemTextureResolver.resolve(SimpleCraft.getInstance().getAssetManager(), template);
		
		if (slotTexture != null)
		{
			// Textured slot - show the sprite, hide the type label letter.
			_slotFillMat[index].setTexture("ColorMap", slotTexture);
			_slotFillMat[index].setColor("Color", ColorRGBA.White);
			_slotLabel[index].setCullHint(BitmapText.CullHint.Always);
		}
		else
		{
			// No sprite - use colored quad with type label.
			_slotFillMat[index].clearParam("ColorMap");
			final ColorRGBA fillColor = getItemColor(template);
			_slotFillMat[index].setColor("Color", fillColor);
			
			// Label (type indicator).
			final String label = getItemLabel(template);
			if (label != null && !label.isEmpty())
			{
				_slotLabel[index].setText(label);
				final float labelWidth = _slotLabel[index].getLineWidth();
				final float labelHeight = _slotLabel[index].getLineHeight();
				final float labelX = _slotX[index] + (_slotSize - labelWidth) / 2f;
				final float labelY = _slotY[index] + (_slotSize + labelHeight) / 2f;
				_slotLabel[index].setLocalTranslation(labelX, labelY, Z_TEXT);
				_slotLabel[index].setCullHint(BitmapText.CullHint.Never);
			}
			else
			{
				_slotLabel[index].setCullHint(BitmapText.CullHint.Always);
			}
		}
		
		// Count (shown if count > 1).
		if (stack.getCount() > 1)
		{
			final String countStr = String.valueOf(stack.getCount());
			_slotCount[index].setText(countStr);
			_slotCountShadow[index].setText(countStr);
			
			// Position at bottom-right corner of slot.
			final float countWidth = _slotCount[index].getLineWidth();
			final float countX = _slotX[index] + _slotSize - countWidth - 2;
			final float countY = _slotY[index] + _slotCount[index].getLineHeight() + 1;
			_slotCount[index].setLocalTranslation(countX, countY, Z_TEXT);
			_slotCountShadow[index].setLocalTranslation(countX + 1, countY - 1, Z_TEXT - 0.1f);
			_slotCount[index].setCullHint(BitmapText.CullHint.Never);
			_slotCountShadow[index].setCullHint(BitmapText.CullHint.Never);
		}
		else
		{
			_slotCount[index].setCullHint(BitmapText.CullHint.Always);
			_slotCountShadow[index].setCullHint(BitmapText.CullHint.Always);
		}
		
		// Durability bar.
		if (stack.hasDurability())
		{
			final float percent = stack.getDurabilityPercent();
			_slotDurBar[index].setLocalScale(percent, 1, 1);
			
			// Color: green > 60%, yellow 30-60%, red < 30%.
			if (percent > 0.6f)
			{
				_slotDurMat[index].setColor("Color", COLOR_DURABILITY_GREEN);
			}
			else if (percent > 0.3f)
			{
				_slotDurMat[index].setColor("Color", COLOR_DURABILITY_YELLOW);
			}
			else
			{
				_slotDurMat[index].setColor("Color", COLOR_DURABILITY_RED);
			}
			
			_slotDurBar[index].setCullHint(Geometry.CullHint.Never);
		}
		else
		{
			_slotDurBar[index].setCullHint(Geometry.CullHint.Always);
		}
	}
	
	/**
	 * Updates the visual representation of a single armor slot.
	 */
	private void updateArmorSlotVisual(int index, ItemInstance armorItem)
	{
		if (armorItem == null || armorItem.isEmpty())
		{
			// Empty armor slot - show slot label letter and empty color.
			_armorSlotFillMat[index].clearParam("ColorMap");
			_armorSlotFillMat[index].setColor("Color", COLOR_ARMOR_SLOT_EMPTY);
			_armorSlotLabel[index].setCullHint(BitmapText.CullHint.Never);
			_armorSlotDurBar[index].setCullHint(Geometry.CullHint.Always);
			return;
		}
		
		final ItemTemplate template = armorItem.getTemplate();
		
		// Try to resolve a sprite texture.
		final Texture slotTexture = ItemTextureResolver.resolve(SimpleCraft.getInstance().getAssetManager(), template);
		
		if (slotTexture != null)
		{
			_armorSlotFillMat[index].setTexture("ColorMap", slotTexture);
			_armorSlotFillMat[index].setColor("Color", ColorRGBA.White);
		}
		else
		{
			_armorSlotFillMat[index].clearParam("ColorMap");
			_armorSlotFillMat[index].setColor("Color", getItemColor(template));
		}
		
		// Hide the letter label when an item is equipped.
		_armorSlotLabel[index].setCullHint(BitmapText.CullHint.Always);
		
		// Durability bar.
		if (armorItem.hasDurability())
		{
			final float percent = armorItem.getDurabilityPercent();
			_armorSlotDurBar[index].setLocalScale(percent, 1, 1);
			
			if (percent > 0.6f)
			{
				_armorSlotDurMat[index].setColor("Color", COLOR_DURABILITY_GREEN);
			}
			else if (percent > 0.3f)
			{
				_armorSlotDurMat[index].setColor("Color", COLOR_DURABILITY_YELLOW);
			}
			else
			{
				_armorSlotDurMat[index].setColor("Color", COLOR_DURABILITY_RED);
			}
			
			_armorSlotDurBar[index].setCullHint(Geometry.CullHint.Never);
		}
		else
		{
			_armorSlotDurBar[index].setCullHint(Geometry.CullHint.Always);
		}
	}
	
	/**
	 * Updates the 3D player model armor node visibility based on equipped armor.
	 */
	private void updatePlayerModelOverlays()
	{
		final ItemInstance helmet = _inventory.getArmorSlot(ArmorSlot.HELMET);
		final boolean hasHelmet = helmet != null && !helmet.isEmpty();
		_armor3dHelmet.setCullHint(hasHelmet ? Node.CullHint.Inherit : Node.CullHint.Always);
		_baseHair.setCullHint(hasHelmet ? Node.CullHint.Always : Node.CullHint.Inherit);
		if (hasHelmet)
		{
			recolorArmorMaterials(0, helmet.getTemplate().getId());
		}
		
		final ItemInstance chest = _inventory.getArmorSlot(ArmorSlot.CHESTPLATE);
		final boolean hasChest = chest != null && !chest.isEmpty();
		_armor3dChestplate.setCullHint(hasChest ? Node.CullHint.Inherit : Node.CullHint.Always);
		if (hasChest)
		{
			recolorArmorMaterials(1, chest.getTemplate().getId());
		}
		
		final ItemInstance pants = _inventory.getArmorSlot(ArmorSlot.PANTS);
		final boolean hasPants = pants != null && !pants.isEmpty();
		_armor3dPants.setCullHint(hasPants ? Node.CullHint.Inherit : Node.CullHint.Always);
		_basePantsLeft.setCullHint(hasPants ? Geometry.CullHint.Always : Geometry.CullHint.Inherit);
		_basePantsRight.setCullHint(hasPants ? Geometry.CullHint.Always : Geometry.CullHint.Inherit);
		_baseWaistband.setCullHint((hasChest || hasPants) ? Geometry.CullHint.Always : Geometry.CullHint.Inherit);
		if (hasPants)
		{
			recolorArmorMaterials(2, pants.getTemplate().getId());
		}
		
		final ItemInstance boots = _inventory.getArmorSlot(ArmorSlot.BOOTS);
		final boolean hasBoots = boots != null && !boots.isEmpty();
		_armor3dBoots.setCullHint(hasBoots ? Node.CullHint.Inherit : Node.CullHint.Always);
		if (hasBoots)
		{
			recolorArmorMaterials(3, boots.getTemplate().getId());
		}
	}
	
	/**
	 * Recolors the 3D armor materials for the given piece index based on the item ID.<br>
	 * Gold items get gold colors, everything else gets iron colors.
	 */
	private void recolorArmorMaterials(int index, String itemId)
	{
		final boolean isGold = itemId.startsWith("gold_");
		_armorMatsMain[index].setColor("Color", isGold ? COLOR_GOLD.clone() : COLOR_IRON.clone());
		_armorMatsDark[index].setColor("Color", isGold ? COLOR_GOLD_DARK.clone() : COLOR_IRON_DARK.clone());
		_armorMatsLight[index].setColor("Color", isGold ? COLOR_GOLD_LIGHT.clone() : COLOR_IRON_LIGHT.clone());
	}
	
	private void updateTooltip(float cx, float cy)
	{
		// Determine which item to show tooltip for.
		ItemInstance tooltipStack = null;
		
		if (_hoveredSlot >= 0)
		{
			tooltipStack = _inventory.getSlot(_hoveredSlot);
		}
		else if (_hoveredArmorIndex >= 0)
		{
			tooltipStack = _inventory.getArmorSlot(ArmorSlot.fromIndex(_hoveredArmorIndex));
		}
		
		if (tooltipStack == null || tooltipStack.isEmpty())
		{
			_tooltipBg.setCullHint(Geometry.CullHint.Always);
			_tooltipText.setCullHint(BitmapText.CullHint.Always);
			_tooltipTextShadow.setCullHint(BitmapText.CullHint.Always);
			return;
		}
		
		// Build tooltip text.
		String text = tooltipStack.getTemplate().getDisplayName();
		if (tooltipStack.hasDurability())
		{
			text += " [" + tooltipStack.getDurability() + "/" + tooltipStack.getTemplate().getMaxDurability() + "]";
		}
		
		_tooltipText.setText(text);
		_tooltipTextShadow.setText(text);
		
		// Position near cursor.
		final float tipWidth = _tooltipText.getLineWidth();
		final float tipHeight = _tooltipText.getLineHeight();
		final float padding = 6f;
		
		float tipX = cx + 14;
		float tipY = cy + tipHeight + padding + 4;
		
		// Keep on screen.
		if (tipX + tipWidth + padding * 2 > _screenWidth)
		{
			tipX = cx - tipWidth - padding * 2 - 4;
		}
		
		if (tipY > _screenHeight)
		{
			tipY = _screenHeight - 4;
		}
		
		// Background.
		_tooltipBg.getMesh().updateBound();
		
		// Recreate tooltip bg at the right size.
		final float bgW = tipWidth + padding * 2;
		final float bgH = tipHeight + padding;
		_tooltipBg.setMesh(new Quad(bgW, bgH));
		_tooltipBg.setLocalTranslation(tipX - padding, tipY - tipHeight - padding / 2f, Z_TOOLTIP - 0.1f);
		_tooltipBg.setCullHint(Geometry.CullHint.Never);
		
		_tooltipText.setLocalTranslation(tipX, tipY, Z_TOOLTIP);
		_tooltipTextShadow.setLocalTranslation(tipX + 1, tipY - 1, Z_TOOLTIP - 0.05f);
		_tooltipText.setCullHint(BitmapText.CullHint.Never);
		_tooltipTextShadow.setCullHint(BitmapText.CullHint.Never);
	}
	
	private void updateHeldItemVisual(float cx, float cy)
	{
		if (_heldStack == null || _heldStack.isEmpty())
		{
			_heldQuad.setCullHint(Geometry.CullHint.Always);
			_heldLabel.setCullHint(BitmapText.CullHint.Always);
			_heldCount.setCullHint(BitmapText.CullHint.Always);
			_heldCountShadow.setCullHint(BitmapText.CullHint.Always);
			return;
		}
		
		final float heldSize = _slotSize * 0.8f;
		final float heldX = cx - heldSize / 2f;
		final float heldY = cy - heldSize / 2f;
		
		// Try to resolve a sprite texture for the held item.
		final Texture heldTexture = ItemTextureResolver.resolve(SimpleCraft.getInstance().getAssetManager(), _heldStack.getTemplate());
		
		if (heldTexture != null)
		{
			_heldQuadMat.setTexture("ColorMap", heldTexture);
			_heldQuadMat.setColor("Color", ColorRGBA.White);
			_heldLabel.setCullHint(BitmapText.CullHint.Always);
		}
		else
		{
			_heldQuadMat.clearParam("ColorMap");
			final ColorRGBA color = getItemColor(_heldStack.getTemplate());
			_heldQuadMat.setColor("Color", color);
			
			// Label.
			final String label = getItemLabel(_heldStack.getTemplate());
			if (label != null && !label.isEmpty())
			{
				_heldLabel.setText(label);
				final float labelWidth = _heldLabel.getLineWidth();
				final float labelHeight = _heldLabel.getLineHeight();
				_heldLabel.setLocalTranslation(heldX + (heldSize - labelWidth) / 2f, heldY + (heldSize + labelHeight) / 2f, Z_HELD + 0.1f);
				_heldLabel.setCullHint(BitmapText.CullHint.Never);
			}
			else
			{
				_heldLabel.setCullHint(BitmapText.CullHint.Always);
			}
		}
		
		_heldQuad.setLocalTranslation(heldX, heldY, Z_HELD);
		_heldQuad.setCullHint(Geometry.CullHint.Never);
		
		// Count.
		if (_heldStack.getCount() > 1)
		{
			final String countStr = String.valueOf(_heldStack.getCount());
			_heldCount.setText(countStr);
			_heldCountShadow.setText(countStr);
			
			final float countWidth = _heldCount.getLineWidth();
			_heldCount.setLocalTranslation(heldX + heldSize - countWidth - 1, heldY + _heldCount.getLineHeight() + 1, Z_HELD + 0.1f);
			_heldCountShadow.setLocalTranslation(heldX + heldSize - countWidth, heldY + _heldCount.getLineHeight(), Z_HELD + 0.05f);
			_heldCount.setCullHint(BitmapText.CullHint.Never);
			_heldCountShadow.setCullHint(BitmapText.CullHint.Never);
		}
		else
		{
			_heldCount.setCullHint(BitmapText.CullHint.Always);
			_heldCountShadow.setCullHint(BitmapText.CullHint.Always);
		}
	}
	
	// ========================================================
	// Input Handling.
	// ========================================================
	
	@Override
	public void onAction(String name, boolean isPressed, float tpf)
	{
		if (!_open)
		{
			return;
		}
		
		// Track left mouse button state for model drag rotation.
		if (GameInputManager.ATTACK.equals(name))
		{
			_mouseDown = isPressed;
			
			if (!isPressed)
			{
				return;
			}
		}
		
		if (!isPressed)
		{
			return;
		}
		
		final Vector2f cursor = _inputManager.getCursorPosition();
		final int slot = getSlotAtPosition(cursor.x, cursor.y);
		final int armorIndex = getArmorSlotAtPosition(cursor.x, cursor.y);
		
		if (GameInputManager.ATTACK.equals(name))
		{
			if (armorIndex >= 0)
			{
				handleArmorSlotClick(armorIndex);
			}
			else if (isOverModelDisplay(cursor.x, cursor.y))
			{
				// Clicking on the 3D model area - used for drag rotation, don't drop items.
			}
			else
			{
				handleLeftClick(slot, _shiftDown);
			}
		}
		else if (GameInputManager.PLACE_BLOCK.equals(name))
		{
			if (armorIndex < 0)
			{
				handleRightClick(slot);
			}
		}
	}
	
	/**
	 * Handles left-click on an armor slot.<br>
	 * If holding an armor item matching this slot, equip it (swap if occupied).<br>
	 * If not holding anything, unequip the armor to cursor.
	 */
	private void handleArmorSlotClick(int armorIndex)
	{
		final ArmorSlot slot = ArmorSlot.fromIndex(armorIndex);
		if (slot == null)
		{
			return;
		}
		
		final ItemInstance equipped = _inventory.getArmorSlot(slot);
		
		if (_heldStack == null)
		{
			// Unequip: pick up the armor piece.
			if (equipped != null && !equipped.isEmpty())
			{
				_heldStack = equipped;
				_inventory.setArmorSlot(slot, null);
			}
		}
		else
		{
			// Try to equip the held item.
			final ItemTemplate template = _heldStack.getTemplate();
			if (template.getType() == ItemType.ARMOR && template.getArmorSlot() == slot)
			{
				// Swap or place.
				_inventory.setArmorSlot(slot, _heldStack);
				_heldStack = (equipped != null && !equipped.isEmpty()) ? equipped : null;
			}
		}
	}
	
	/**
	 * Left-click: pick up, place, swap, or merge stacks.<br>
	 * If shift is held, performs splitting instead.
	 */
	private void handleLeftClick(int slot, boolean shiftDown)
	{
		if (slot < 0)
		{
			// Clicked outside grid - drop held stack into the world.
			if (_heldStack != null)
			{
				dropItemIntoWorld(_heldStack);
				_heldStack = null;
			}
			return;
		}
		
		if (shiftDown)
		{
			// Shift-click: splitting behavior.
			if (_heldStack == null)
			{
				// Pick up half from slot.
				final ItemInstance target = _inventory.getSlot(slot);
				if (target != null && !target.isEmpty())
				{
					final int total = target.getCount();
					final int take = (total + 1) / 2; // ceil half
					final int leave = total - take;
					if (leave > 0)
					{
						target.setCount(leave);
						_heldStack = new ItemInstance(target.getTemplate(), take);
					}
					else
					{
						// Take whole stack (when total == 1).
						_heldStack = target;
						_inventory.setSlot(slot, null);
					}
				}
			}
			else
			{
				// Place half into empty slot.
				final ItemInstance target = _inventory.getSlot(slot);
				if (target == null || target.isEmpty())
				{
					final int total = _heldStack.getCount();
					final int put = (total + 1) / 2;
					final int keep = total - put;
					if (keep > 0)
					{
						_heldStack.setCount(keep);
						_inventory.setSlot(slot, new ItemInstance(_heldStack.getTemplate(), put));
					}
					else
					{
						// Put whole stack (when total == 1).
						_inventory.setSlot(slot, _heldStack);
						_heldStack = null;
					}
				}
				
				// If slot is not empty, do nothing for shift-click (could be extended later).
			}
			return;
		}
		
		// Original non-shift logic.
		if (_heldStack == null)
		{
			// Pick up stack from slot.
			final ItemInstance target = _inventory.getSlot(slot);
			if (target != null && !target.isEmpty())
			{
				_heldStack = target;
				_inventory.setSlot(slot, null);
			}
		}
		else
		{
			final ItemInstance target = _inventory.getSlot(slot);
			
			if (target == null || target.isEmpty())
			{
				// Place held stack in empty slot.
				_inventory.setSlot(slot, _heldStack);
				_heldStack = null;
			}
			else if (target.canStackWith(_heldStack))
			{
				// Merge: add held count to target.
				final int overflow = target.add(_heldStack.getCount());
				if (overflow <= 0)
				{
					_heldStack = null;
				}
				else
				{
					// Create new held stack with overflow.
					_heldStack = new ItemInstance(_heldStack.getTemplate(), overflow);
				}
			}
			else
			{
				// Swap: exchange held and slot contents.
				_inventory.setSlot(slot, _heldStack);
				_heldStack = target;
			}
		}
	}
	
	/**
	 * Right-click: place a single item from held stack, or quick-craft when not holding anything.<br>
	 * Quick-craft: right-clicking a Wood block item with 4+ wood in inventory consumes 4 wood<br>
	 * and creates a Crafting Table (early-game convenience before you have a Crafting Table).
	 */
	private void handleRightClick(int slot)
	{
		if (slot < 0)
		{
			return;
		}
		
		// Quick-craft: right-click with empty hand on a wood item.
		if (_heldStack == null)
		{
			final ItemInstance target = _inventory.getSlot(slot);
			if (target != null && !target.isEmpty() && "wood".equals(target.getTemplate().getId()))
			{
				if (_inventory.hasItem("wood", 4))
				{
					_inventory.removeItem("wood", 4);
					final ItemTemplate craftingTable = ItemRegistry.get("crafting_table");
					if (craftingTable != null)
					{
						_inventory.addItem(new ItemInstance(craftingTable, 1));
						System.out.println("Quick-crafted: Crafting Table (4× Wood)");
					}
				}
			}
			return;
		}
		
		final ItemInstance target = _inventory.getSlot(slot);
		
		if (target == null || target.isEmpty())
		{
			// Place one item in empty slot.
			_inventory.setSlot(slot, new ItemInstance(_heldStack.getTemplate(), 1));
			_heldStack.remove(1);
			if (_heldStack.isEmpty())
			{
				_heldStack = null;
			}
		}
		else if (target.getTemplate() == _heldStack.getTemplate() && !target.isFull() && !target.hasDurability())
		{
			// Add one item to matching stack.
			target.add(1);
			_heldStack.remove(1);
			if (_heldStack.isEmpty())
			{
				_heldStack = null;
			}
		}
	}
	
	// ========================================================
	// Slot Hit Detection.
	// ========================================================
	
	/**
	 * Returns the inventory slot index at the given screen position, or -1 if outside all slots.
	 */
	private int getSlotAtPosition(float x, float y)
	{
		for (int i = 0; i < Inventory.TOTAL_SLOTS; i++)
		{
			if (x >= _slotX[i] && x < _slotX[i] + _slotSize && y >= _slotY[i] && y < _slotY[i] + _slotSize)
			{
				return i;
			}
		}
		
		return -1;
	}
	
	/**
	 * Returns the armor slot index (0-3) at the given screen position, or -1 if outside.
	 */
	private int getArmorSlotAtPosition(float x, float y)
	{
		for (int i = 0; i < ArmorSlot.COUNT; i++)
		{
			if (x >= _armorSlotX[i] && x < _armorSlotX[i] + _slotSize && y >= _armorSlotY[i] && y < _armorSlotY[i] + _slotSize)
			{
				return i;
			}
		}
		
		return -1;
	}
	
	/**
	 * Returns true if the given screen position is over the 3D model display quad.
	 */
	private boolean isOverModelDisplay(float x, float y)
	{
		return x >= _modelDisplayX && x < _modelDisplayX + _modelDisplayWidth && y >= _modelDisplayY && y < _modelDisplayY + _modelDisplayHeight;
	}
	
	// ========================================================
	// World Drop Support.
	// ========================================================
	
	/**
	 * Sets the drop manager for spawning world drops when items are discarded.
	 * @param dropManager the drop manager instance
	 */
	public void setDropManager(DropManager dropManager)
	{
		_dropManager = dropManager;
	}
	
	/**
	 * Sets the world reference for ground-level lookups when dropping items.
	 * @param world the game world
	 */
	public void setWorld(World world)
	{
		_world = world;
	}
	
	/**
	 * Drops an item stack into the world slightly in front of the player.<br>
	 * The drop is placed 1.5 blocks forward from the player's position in the<br>
	 * camera look direction (horizontal only), then scanned downward to find<br>
	 * the ground surface. Falls back to silent discard if DropManager is not set.
	 * @param stack the item stack to drop
	 */
	private void dropItemIntoWorld(ItemInstance stack)
	{
		if (stack == null || stack.isEmpty())
		{
			return;
		}
		
		if (_dropManager == null)
		{
			System.out.println("Discarded (no DropManager): " + stack);
			return;
		}
		
		// Calculate drop position: 2.5 blocks in front of the player on the horizontal plane.
		final Vector3f playerPos = _playerController.getPosition();
		final Vector3f camDir = SimpleCraft.getInstance().getCamera().getDirection();
		
		// Horizontal-only forward direction (ignore vertical look angle).
		final float hLength = (float) Math.sqrt(camDir.x * camDir.x + camDir.z * camDir.z);
		final float forwardDist = 2.5f;
		float dropX;
		float dropZ;
		if (hLength > 0.001f)
		{
			dropX = playerPos.x + (camDir.x / hLength) * forwardDist;
			dropZ = playerPos.z + (camDir.z / hLength) * forwardDist;
		}
		else
		{
			// Looking straight up/down - drop at player position.
			dropX = playerPos.x;
			dropZ = playerPos.z;
		}
		
		// Find ground level below the drop position.
		// Start one block above feet level - handles 1-block step-ups in terrain
		// ahead while staying below ceilings and roofs.
		final int bx = (int) Math.floor(dropX);
		final int bz = (int) Math.floor(dropZ);
		int groundY = (int) Math.ceil(playerPos.y);
		if (_world != null)
		{
			final int startY = (int) Math.ceil(playerPos.y) + 1;
			for (int y = startY; y >= 0; y--)
			{
				if (_world.getBlock(bx, y, bz).isSolid())
				{
					groundY = y + 1;
					break;
				}
			}
		}
		
		_dropManager.spawnDrop(new Vector3f(dropX, groundY, dropZ), stack);
		System.out.println("Dropped into world: " + stack);
	}
	
	// ========================================================
	// Cleanup.
	// ========================================================
	
	/**
	 * Removes all inventory screen elements from the GUI node.
	 */
	public void cleanup()
	{
		if (_open)
		{
			close();
		}
		
		// Remove offscreen viewport.
		SimpleCraft.getInstance().getRenderManager().removePreView(_modelViewPort);
		
		_guiNode.detachChild(_screenNode);
	}
	
	// ========================================================
	// Item Visual Helpers.
	// ========================================================
	
	/**
	 * Returns a display color for the given item based on its type.
	 */
	static ColorRGBA getItemColor(ItemTemplate template)
	{
		if (template == null)
		{
			return new ColorRGBA(0.3f, 0.3f, 0.3f, 0.6f);
		}
		
		switch (template.getType())
		{
			case BLOCK:
			{
				final Block block = template.getPlacesBlock();
				if (block != null)
				{
					return getBlockColor(block);
				}
				
				return new ColorRGBA(0.5f, 0.5f, 0.5f, 1.0f);
			}
			case WEAPON:
			{
				return new ColorRGBA(0.7f, 0.7f, 0.75f, 1.0f);
			}
			case TOOL:
			{
				return new ColorRGBA(0.55f, 0.45f, 0.3f, 1.0f);
			}
			case CONSUMABLE:
			{
				return new ColorRGBA(0.25f, 0.7f, 0.25f, 1.0f);
			}
			case MATERIAL:
			{
				return new ColorRGBA(0.85f, 0.6f, 0.2f, 1.0f);
			}
			case ARMOR:
			{
				return new ColorRGBA(0.62f, 0.62f, 0.68f, 1.0f);
			}
			default:
			{
				return new ColorRGBA(0.5f, 0.5f, 0.5f, 1.0f);
			}
		}
	}
	
	/**
	 * Returns a short label for the given item (type indicator shown in the slot center).
	 */
	static String getItemLabel(ItemTemplate template)
	{
		if (template == null)
		{
			return null;
		}
		
		switch (template.getType())
		{
			case WEAPON:
			{
				return "W";
			}
			case TOOL:
			{
				switch (template.getToolType())
				{
					case PICKAXE:
					{
						return "P";
					}
					case AXE:
					{
						return "A";
					}
					case SHOVEL:
					{
						return "S";
					}
					default:
					{
						return "T";
					}
				}
			}
			case CONSUMABLE:
			{
				return "+";
			}
			case ARMOR:
			{
				// Use the armor slot initial as the label.
				final ArmorSlot armorSlot = template.getArmorSlot();
				if (armorSlot != null)
				{
					return ARMOR_SLOT_LABELS[armorSlot.getIndex()];
				}
				
				return "A";
			}
			default:
			{
				return null;
			}
		}
	}
	
	/**
	 * Returns a representative color for a block type.
	 */
	static ColorRGBA getBlockColor(Block block)
	{
		if (block == null)
		{
			return new ColorRGBA(0.5f, 0.5f, 0.5f, 1.0f);
		}
		
		switch (block)
		{
			case GRASS:
			{
				return new ColorRGBA(0.3f, 0.7f, 0.2f, 1.0f);
			}
			case DIRT:
			{
				return new ColorRGBA(0.55f, 0.35f, 0.18f, 1.0f);
			}
			case STONE:
			{
				return new ColorRGBA(0.5f, 0.5f, 0.5f, 1.0f);
			}
			case SAND:
			{
				return new ColorRGBA(0.9f, 0.85f, 0.6f, 1.0f);
			}
			case WOOD:
			{
				return new ColorRGBA(0.45f, 0.3f, 0.15f, 1.0f);
			}
			case LEAVES:
			{
				return new ColorRGBA(0.15f, 0.5f, 0.1f, 1.0f);
			}
			case CAMPFIRE:
			{
				return new ColorRGBA(0.9f, 0.4f, 0.1f, 1.0f);
			}
			case CHEST:
			{
				return new ColorRGBA(0.6f, 0.45f, 0.2f, 1.0f);
			}
			case CRAFTING_TABLE:
			{
				return new ColorRGBA(0.5f, 0.35f, 0.2f, 1.0f);
			}
			case FURNACE:
			{
				return new ColorRGBA(0.45f, 0.45f, 0.45f, 1.0f);
			}
			case TORCH:
			{
				return new ColorRGBA(1.0f, 0.85f, 0.3f, 1.0f);
			}
			case TALL_GRASS:
			{
				return new ColorRGBA(0.25f, 0.6f, 0.2f, 1.0f);
			}
			case RED_POPPY:
			{
				return new ColorRGBA(0.9f, 0.2f, 0.15f, 1.0f);
			}
			case DANDELION:
			{
				return new ColorRGBA(1.0f, 0.9f, 0.1f, 1.0f);
			}
			case BLUE_ORCHID:
			{
				return new ColorRGBA(0.2f, 0.4f, 0.9f, 1.0f);
			}
			case WHITE_DAISY:
			{
				return new ColorRGBA(0.95f, 0.95f, 0.95f, 1.0f);
			}
			case TALL_SEAWEED:
			case SHORT_SEAWEED:
			{
				return new ColorRGBA(0.1f, 0.55f, 0.3f, 1.0f);
			}
			case GLASS:
			{
				return new ColorRGBA(0.7f, 0.82f, 0.9f, 0.7f);
			}
			case WINDOW:
			{
				return new ColorRGBA(0.47f, 0.31f, 0.16f, 1.0f);
			}
			case DOOR_BOTTOM:
			case DOOR_TOP:
			{
				return new ColorRGBA(0.55f, 0.35f, 0.18f, 1.0f);
			}
			default:
			{
				return new ColorRGBA(0.5f, 0.5f, 0.5f, 1.0f);
			}
		}
	}
	
	// ========================================================
	// Geometry Helpers.
	// ========================================================
	
	private Geometry createQuad(String name, float width, float height, ColorRGBA color, SimpleCraft app)
	{
		final Material mat = createColorMaterial(color, app);
		return createQuadWithMaterial(name, width, height, mat);
	}
	
	private Geometry createQuadWithMaterial(String name, float width, float height, Material mat)
	{
		final Quad quad = new Quad(width, height);
		final Geometry geom = new Geometry(name, quad);
		geom.setMaterial(mat);
		geom.setQueueBucket(Bucket.Gui);
		return geom;
	}
	
	private Material createColorMaterial(ColorRGBA color, SimpleCraft app)
	{
		final Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
		mat.setColor("Color", color.clone());
		mat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
		return mat;
	}
	
	// ========================================================
	// 3D Model Helpers.
	// ========================================================
	
	/**
	 * Creates an opaque Unshaded material for the offscreen 3D player model.
	 */
	private Material makeModelMaterial(ColorRGBA color, SimpleCraft app)
	{
		final Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
		mat.setColor("Color", color.clone());
		return mat;
	}
	
	/**
	 * Creates a Box geometry with the given half-extents, material and local translation.
	 */
	private Geometry makeModelBox(String name, float halfX, float halfY, float halfZ, Material mat, float tx, float ty, float tz)
	{
		final Box box = new Box(halfX, halfY, halfZ);
		final Geometry geom = new Geometry(name, box);
		geom.setMaterial(mat);
		geom.setLocalTranslation(tx, ty, tz);
		return geom;
	}
	
	/**
	 * Creates a pivot Node at the given position and attaches a Geometry child to it.<br>
	 * Matches the pattern used by EnemyFactory for joint-based limb hierarchies.
	 */
	private Node makeModelPivot(String name, Geometry geom, float pivotX, float pivotY, float pivotZ)
	{
		final Node pivot = new Node(name);
		pivot.setLocalTranslation(pivotX, pivotY, pivotZ);
		pivot.attachChild(geom);
		return pivot;
	}
}
