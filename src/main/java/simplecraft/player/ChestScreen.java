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
import simplecraft.item.ItemTemplate;
import simplecraft.item.ItemTextureResolver;
import simplecraft.item.ItemType;
import simplecraft.settings.LanguageManager;
import simplecraft.ui.FontManager;
import simplecraft.world.World;
import simplecraft.world.entity.ChestTileEntity;

/**
 * Chest UI opened by right-clicking a placed Chest block.<br>
 * Displays 27 chest storage slots (3×9) above the player's 36 inventory slots.<br>
 * <br>
 * <b>Layout (top to bottom):</b><br>
 * Title "Chest" -> 3×9 chest grid -> 3×9 main inventory -> 1×9 hotbar.<br>
 * <br>
 * <b>Interaction:</b><br>
 * Left-click a slot to pick up the stack. Left-click another slot to place/swap/merge.<br>
 * Works across the chest ↔ inventory boundary.<br>
 * Right-click while holding a stack to place a single item.<br>
 * Shift+left‑click while holding nothing on a non‑empty slot -> transfer the entire stack<br>
 * to the opposite inventory (chest -> player or player -> chest), filling matching stacks<br>
 * first, then the first empty slot.<br>
 * Shift+right‑click -> split a stack (pick up half from a slot, or place half of the held<br>
 * stack into an empty slot).<br>
 * Click outside the grid while holding to discard the stack.<br>
 * <br>
 * Changes are immediate - closing the screen does not undo any transfers.<br>
 * Close with Escape or Tab. While open, the cursor is visible and player<br>
 * movement/look is disabled.
 * @author Pantelis Andrianakis
 * @since March 18th 2026
 */
public class ChestScreen implements ActionListener
{
	// ========================================================
	// Layout Constants.
	// ========================================================
	
	/** Number of columns in both grids. */
	private static final int GRID_COLS = 9;
	
	/** Number of rows in the chest grid. */
	private static final int CHEST_ROWS = 3;
	
	/** Number of rows in the player main inventory grid. */
	private static final int PLAYER_MAIN_ROWS = 3;
	
	/** Pixel gap between slot groups (chest-to-player, main-to-hotbar). */
	private static final float SECTION_GAP = 30f;
	
	/** Pixel gap between individual slots. */
	private static final float SLOT_SPACING = 3f;
	
	/** Background padding around the slot fill quad. */
	private static final float SLOT_PADDING = 2f;
	
	/** Total displayed slots: 27 chest + 36 player. */
	private static final int TOTAL_DISPLAY_SLOTS = ChestTileEntity.CHEST_SLOTS + Inventory.TOTAL_SLOTS;
	
	// Z-depths.
	private static final float Z_OVERLAY = 10f;
	private static final float Z_SLOT_BG = 11f;
	private static final float Z_SLOT_FILL = 11.5f;
	private static final float Z_TEXT = 12f;
	private static final float Z_DURABILITY = 12f;
	private static final float Z_HIGHLIGHT = 10.8f;
	private static final float Z_HELD = 15f;
	private static final float Z_TOOLTIP = 16f;
	
	// ========================================================
	// Colors.
	// ========================================================
	
	private static final ColorRGBA COLOR_OVERLAY = new ColorRGBA(0.0f, 0.0f, 0.0f, 0.65f);
	private static final ColorRGBA COLOR_SLOT_BG = new ColorRGBA(0.15f, 0.15f, 0.15f, 0.85f);
	private static final ColorRGBA COLOR_SLOT_EMPTY = new ColorRGBA(0.25f, 0.25f, 0.25f, 0.6f);
	
	/** Lighter background for chest container slots (visual distinction from player inventory). */
	private static final ColorRGBA COLOR_CHEST_SLOT_BG = new ColorRGBA(0.25f, 0.25f, 0.25f, 0.85f);
	private static final ColorRGBA COLOR_CHEST_SLOT_EMPTY = new ColorRGBA(0.35f, 0.35f, 0.35f, 0.6f);
	private static final ColorRGBA COLOR_HOTBAR_LABEL = new ColorRGBA(0.5f, 0.5f, 0.5f, 0.4f);
	private static final ColorRGBA COLOR_HOVER = new ColorRGBA(1.0f, 1.0f, 1.0f, 0.15f);
	private static final ColorRGBA COLOR_TEXT = new ColorRGBA(1.0f, 1.0f, 1.0f, 1.0f);
	private static final ColorRGBA COLOR_TEXT_SHADOW = new ColorRGBA(0.0f, 0.0f, 0.0f, 0.8f);
	private static final ColorRGBA COLOR_TOOLTIP_BG = new ColorRGBA(0.1f, 0.1f, 0.1f, 0.9f);
	private static final ColorRGBA COLOR_DURABILITY_GREEN = new ColorRGBA(0.2f, 0.85f, 0.2f, 1.0f);
	private static final ColorRGBA COLOR_DURABILITY_YELLOW = new ColorRGBA(0.9f, 0.85f, 0.2f, 1.0f);
	private static final ColorRGBA COLOR_DURABILITY_RED = new ColorRGBA(0.9f, 0.2f, 0.2f, 1.0f);
	private static final ColorRGBA COLOR_ARMOR_SLOT_EMPTY = new ColorRGBA(0.30f, 0.30f, 0.35f, 0.6f);
	
	// Player model colors (matching EnemyFactory.buildPlayer).
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
	
	/** Offscreen render texture dimensions. */
	private static final int MODEL_TEX_WIDTH = 256;
	private static final int MODEL_TEX_HEIGHT = 512;
	
	// ========================================================
	// Fields.
	// ========================================================
	
	private final Inventory _inventory;
	private final PlayerController _playerController;
	private final BlockInteraction _blockInteraction;
	
	/** Drop manager for spawning world drops when items are discarded. */
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
	
	/** Whether the chest screen is currently visible. */
	private boolean _open;
	
	/** The chest tile entity currently being viewed. Null when closed. */
	private ChestTileEntity _activeChest;
	
	// Per-slot geometry and text (indices 0-26 = chest, 27-62 = player).
	private final Geometry[] _slotBg = new Geometry[TOTAL_DISPLAY_SLOTS];
	private final Geometry[] _slotFill = new Geometry[TOTAL_DISPLAY_SLOTS];
	private final Material[] _slotFillMat = new Material[TOTAL_DISPLAY_SLOTS];
	private final BitmapText[] _slotLabel = new BitmapText[TOTAL_DISPLAY_SLOTS];
	private final BitmapText[] _slotCount = new BitmapText[TOTAL_DISPLAY_SLOTS];
	private final BitmapText[] _slotCountShadow = new BitmapText[TOTAL_DISPLAY_SLOTS];
	private final Geometry[] _slotDurBar = new Geometry[TOTAL_DISPLAY_SLOTS];
	private final Material[] _slotDurMat = new Material[TOTAL_DISPLAY_SLOTS];
	
	/** Hotbar numbers (1-9) shown faintly in the player hotbar slots. */
	private final BitmapText[] _hotbarNumbers = new BitmapText[Inventory.HOTBAR_SLOTS];
	
	/** Screen-space X origin of each display slot. */
	private final float[] _slotX = new float[TOTAL_DISPLAY_SLOTS];
	
	/** Screen-space Y origin of each display slot. */
	private final float[] _slotY = new float[TOTAL_DISPLAY_SLOTS];
	
	// Hover highlight.
	private Geometry _hoverHighlight;
	private int _hoveredSlot = -1;
	
	// Tooltip.
	private Geometry _tooltipBg;
	private BitmapText _tooltipText;
	private BitmapText _tooltipTextShadow;
	
	// Held item (being moved by cursor).
	private ItemInstance _heldStack;
	private Geometry _heldQuad;
	private Material _heldQuadMat;
	private BitmapText _heldLabel;
	private BitmapText _heldCount;
	private BitmapText _heldCountShadow;
	
	/** Title text. */
	private BitmapText _titleText;
	private BitmapText _titleTextShadow;
	
	/** "Inventory" label above the player main inventory section. */
	private BitmapText _inventoryLabel;
	private BitmapText _inventoryLabelShadow;
	
	/** "Action Bar" label above the player hotbar section. */
	private BitmapText _actionBarLabel;
	private BitmapText _actionBarLabelShadow;
	
	/** Shift tracking. */
	private boolean _shiftDown;
	private final ActionListener _shiftListener = (name, isPressed, tpf) ->
	{
		if (name.equals("SHIFT_LEFT") || name.equals("SHIFT_RIGHT"))
		{
			_shiftDown = isPressed;
		}
	};
	
	// Armor slot geometry and text (4 slots).
	private final Geometry[] _armorSlotBg = new Geometry[ArmorSlot.COUNT];
	private final Geometry[] _armorSlotFill = new Geometry[ArmorSlot.COUNT];
	private final Material[] _armorSlotFillMat = new Material[ArmorSlot.COUNT];
	private final BitmapText[] _armorSlotLabel = new BitmapText[ArmorSlot.COUNT];
	private final Geometry[] _armorSlotDurBar = new Geometry[ArmorSlot.COUNT];
	private final Material[] _armorSlotDurMat = new Material[ArmorSlot.COUNT];
	private final float[] _armorSlotX = new float[ArmorSlot.COUNT];
	private final float[] _armorSlotY = new float[ArmorSlot.COUNT];
	private int _hoveredArmorIndex = -1;
	
	// 3D player model rendered via offscreen viewport.
	private ViewPort _modelViewPort;
	private Node _modelScene;
	private Node _modelRootNode;
	private Geometry _modelDisplayQuad;
	private Material _modelDisplayMat;
	private Texture2D _modelRenderTexture;
	
	// Toggleable 3D armor nodes.
	private Node _armor3dHelmet;
	private Node _armor3dChestplate;
	private Node _armor3dPants;
	private Node _armor3dBoots;
	
	// Per-armor-piece materials (recolored dynamically for iron vs gold).
	private final Material[] _armorMatsMain = new Material[ArmorSlot.COUNT];
	private final Material[] _armorMatsDark = new Material[ArmorSlot.COUNT];
	private final Material[] _armorMatsLight = new Material[ArmorSlot.COUNT];
	
	// Base clothing nodes (hidden when armor equipped).
	private Geometry _basePantsLeft;
	private Geometry _basePantsRight;
	private Geometry _baseWaistband;
	private Node _baseHair;
	
	/** Model rotation and mouse drag tracking. */
	private float _modelRotation;
	private boolean _mouseDown;
	private float _prevCursorX;
	private float _modelDisplayX;
	private float _modelDisplayY;
	private float _modelDisplayWidth;
	private float _modelDisplayHeight;
	
	/** Armor panel X and Y position. */
	private float _armorPanelX;
	private float _armorPanelY;
	
	/** Armor title label. */
	private BitmapText _armorTitleText;
	private BitmapText _armorTitleTextShadow;
	
	// ========================================================
	// Constructor.
	// ========================================================
	
	/**
	 * Creates the chest screen (hidden initially).
	 * @param playerController the player controller
	 * @param blockInteraction the block interaction handler
	 */
	public ChestScreen(PlayerController playerController, BlockInteraction blockInteraction)
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
		_font = FontManager.getFont(app.getAssetManager(), FontManager.getRegularPath(), Font.PLAIN, fontSize);
		
		final int tooltipSize = Math.max(12, (int) (_screenHeight * 0.018f));
		_tooltipFont = FontManager.getFont(app.getAssetManager(), FontManager.getRegularPath(), Font.PLAIN, tooltipSize);
		
		// Build all UI elements.
		_screenNode = new Node("ChestScreen");
		
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
		final Geometry overlay = new Geometry("ChestOverlay", overlayQuad);
		final Material mat = createColorMaterial(COLOR_OVERLAY, app);
		overlay.setMaterial(mat);
		overlay.setQueueBucket(Bucket.Gui);
		overlay.setLocalTranslation(0, 0, Z_OVERLAY);
		_screenNode.attachChild(overlay);
	}
	
	/**
	 * Computes screen-space positions for all 63 display slots.<br>
	 * Layout (bottom to top): hotbar -> gap -> main inventory (3 rows) -> gap -> chest (3 rows).<br>
	 * Display slot indices: 0-26 = chest, 27-35 = player hotbar, 36-62 = player main.
	 */
	private void computeSlotPositions()
	{
		final float totalGridWidth = GRID_COLS * _slotSize + (GRID_COLS - 1) * SLOT_SPACING;
		
		// Total height: chest(3 rows) + gap + player main(3 rows) + gap + hotbar(1 row).
		final float totalGridHeight = (CHEST_ROWS + PLAYER_MAIN_ROWS + 1) * _slotSize + (CHEST_ROWS + PLAYER_MAIN_ROWS) * SLOT_SPACING + SECTION_GAP * 2;
		
		// Armor panel dimensions.
		final float armorPanelWidth = _slotSize;
		final float playerModelWidth = _slotSize * 1.6f;
		final float armorGap = _slotSize * 0.5f;
		final float panelGridGap = _slotSize * 0.5f;
		final float armorAreaWidth = armorPanelWidth + armorGap + playerModelWidth + panelGridGap;
		
		// Combined width centered on screen, grid shifted right.
		final float combinedWidth = armorAreaWidth + totalGridWidth;
		final float combinedStartX = (_screenWidth - combinedWidth) / 2f;
		final float gridStartX = combinedStartX + armorAreaWidth;
		final float gridStartY = (_screenHeight - totalGridHeight) / 2f;
		
		// Player hotbar (display slots 27-35): bottom row.
		final float hotbarY = gridStartY;
		for (int col = 0; col < GRID_COLS; col++)
		{
			final int di = ChestTileEntity.CHEST_SLOTS + col;
			_slotX[di] = gridStartX + col * (_slotSize + SLOT_SPACING);
			_slotY[di] = hotbarY;
		}
		
		// Player main inventory (display slots 36-62): 3 rows above hotbar with gap for Action Bar label.
		final float playerMainStartY = hotbarY + _slotSize + SLOT_SPACING + SECTION_GAP;
		for (int row = 0; row < PLAYER_MAIN_ROWS; row++)
		{
			for (int col = 0; col < GRID_COLS; col++)
			{
				final int playerSlotIndex = 9 + row * GRID_COLS + col;
				final int di = ChestTileEntity.CHEST_SLOTS + playerSlotIndex;
				_slotX[di] = gridStartX + col * (_slotSize + SLOT_SPACING);
				_slotY[di] = playerMainStartY + (2 - row) * (_slotSize + SLOT_SPACING);
			}
		}
		
		// Chest slots (display slots 0-26): 3 rows above player main with gap.
		final float chestStartY = playerMainStartY + PLAYER_MAIN_ROWS * (_slotSize + SLOT_SPACING) + SECTION_GAP;
		for (int row = 0; row < CHEST_ROWS; row++)
		{
			for (int col = 0; col < GRID_COLS; col++)
			{
				final int di = row * GRID_COLS + col;
				_slotX[di] = gridStartX + col * (_slotSize + SLOT_SPACING);
				_slotY[di] = chestStartY + (2 - row) * (_slotSize + SLOT_SPACING);
			}
		}
		
		// Armor panel: 4 slots, helmet aligned with top inventory row.
		final float topInvRowY = playerMainStartY + 2 * (_slotSize + SLOT_SPACING);
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
		for (int i = 0; i < TOTAL_DISPLAY_SLOTS; i++)
		{
			// Chest slots (0-26) use lighter colors; player inventory/action bar (27-62) use darker colors matching InventoryScreen.
			final boolean isChestSlot = i < ChestTileEntity.CHEST_SLOTS;
			final ColorRGBA bgColor = isChestSlot ? COLOR_CHEST_SLOT_BG : COLOR_SLOT_BG;
			final ColorRGBA fillColor = isChestSlot ? COLOR_CHEST_SLOT_EMPTY : COLOR_SLOT_EMPTY;
			
			// Background quad.
			final float bgSize = _slotSize + SLOT_PADDING * 2;
			_slotBg[i] = createQuad("ChSlotBg" + i, bgSize, bgSize, bgColor, app);
			_slotBg[i].setLocalTranslation(_slotX[i] - SLOT_PADDING, _slotY[i] - SLOT_PADDING, Z_SLOT_BG);
			_screenNode.attachChild(_slotBg[i]);
			
			// Fill quad.
			_slotFillMat[i] = createColorMaterial(fillColor, app);
			_slotFill[i] = createQuadWithMaterial("ChSlotFill" + i, _slotSize, _slotSize, _slotFillMat[i]);
			_slotFill[i].setLocalTranslation(_slotX[i], _slotY[i], Z_SLOT_FILL);
			_screenNode.attachChild(_slotFill[i]);
			
			// Label text.
			_slotLabel[i] = new BitmapText(_font);
			_slotLabel[i].setText("");
			_slotLabel[i].setSize(_font.getCharSet().getRenderedSize() * 1.2f);
			_slotLabel[i].setColor(COLOR_TEXT.clone());
			_slotLabel[i].setLocalTranslation(_slotX[i], _slotY[i] + _slotSize, Z_TEXT);
			_slotLabel[i].setCullHint(BitmapText.CullHint.Always);
			_screenNode.attachChild(_slotLabel[i]);
			
			// Count text + shadow.
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
			
			// Durability bar.
			_slotDurMat[i] = createColorMaterial(COLOR_DURABILITY_GREEN, app);
			_slotDurBar[i] = createQuadWithMaterial("ChDur" + i, _slotSize, 3f, _slotDurMat[i]);
			_slotDurBar[i].setLocalTranslation(_slotX[i], _slotY[i], Z_DURABILITY);
			_slotDurBar[i].setCullHint(Geometry.CullHint.Always);
			_screenNode.attachChild(_slotDurBar[i]);
			
			// Hotbar slot key labels for player hotbar slots (display indices 27-35).
			if (i >= ChestTileEntity.CHEST_SLOTS && i < ChestTileEntity.CHEST_SLOTS + Inventory.HOTBAR_SLOTS)
			{
				final int hotbarIndex = i - ChestTileEntity.CHEST_SLOTS;
				final GameInputManager gim = SimpleCraft.getInstance().getGameInputManager();
				final String keyName = GameInputManager.getKeyName(gim.getKeyCode(GameInputManager.HOTBAR_ACTIONS[hotbarIndex]));
				_hotbarNumbers[hotbarIndex] = new BitmapText(_font);
				_hotbarNumbers[hotbarIndex].setText(keyName);
				_hotbarNumbers[hotbarIndex].setSize(_font.getCharSet().getRenderedSize() * 0.8f);
				_hotbarNumbers[hotbarIndex].setColor(COLOR_HOTBAR_LABEL.clone());
				final float numX = _slotX[i] + 2;
				final float numY = _slotY[i] + _slotSize - 2;
				_hotbarNumbers[hotbarIndex].setLocalTranslation(numX, numY, Z_TEXT);
				_screenNode.attachChild(_hotbarNumbers[hotbarIndex]);
			}
		}
	}
	
	private void buildHoverHighlight(SimpleCraft app)
	{
		final Material mat = createColorMaterial(COLOR_HOVER, app);
		_hoverHighlight = createQuadWithMaterial("ChHover", _slotSize, _slotSize, mat);
		_hoverHighlight.setLocalTranslation(0, 0, Z_HIGHLIGHT);
		_hoverHighlight.setCullHint(Geometry.CullHint.Always);
		_screenNode.attachChild(_hoverHighlight);
	}
	
	/**
	 * Builds the 4 armor equipment slots.
	 */
	private void buildArmorSlots(SimpleCraft app)
	{
		for (int i = 0; i < ArmorSlot.COUNT; i++)
		{
			final float bgSize = _slotSize + SLOT_PADDING * 2;
			_armorSlotBg[i] = createQuad("ChArmorBg" + i, bgSize, bgSize, COLOR_SLOT_BG, app);
			_armorSlotBg[i].setLocalTranslation(_armorSlotX[i] - SLOT_PADDING, _armorSlotY[i] - SLOT_PADDING, Z_SLOT_BG);
			_screenNode.attachChild(_armorSlotBg[i]);
			
			_armorSlotFillMat[i] = createColorMaterial(COLOR_ARMOR_SLOT_EMPTY, app);
			_armorSlotFill[i] = createQuadWithMaterial("ChArmorFill" + i, _slotSize, _slotSize, _armorSlotFillMat[i]);
			_armorSlotFill[i].setLocalTranslation(_armorSlotX[i], _armorSlotY[i], Z_SLOT_FILL);
			_screenNode.attachChild(_armorSlotFill[i]);
			
			_armorSlotLabel[i] = new BitmapText(_font);
			_armorSlotLabel[i].setText(getArmorSlotLabel(i));
			_armorSlotLabel[i].setSize(_font.getCharSet().getRenderedSize() * 1.2f);
			_armorSlotLabel[i].setColor(new ColorRGBA(0.5f, 0.5f, 0.55f, 0.5f));
			final float labelWidth = _armorSlotLabel[i].getLineWidth();
			final float labelHeight = _armorSlotLabel[i].getLineHeight();
			_armorSlotLabel[i].setLocalTranslation(_armorSlotX[i] + (_slotSize - labelWidth) / 2f, _armorSlotY[i] + (_slotSize + labelHeight) / 2f, Z_TEXT);
			_screenNode.attachChild(_armorSlotLabel[i]);
			
			_armorSlotDurMat[i] = createColorMaterial(COLOR_DURABILITY_GREEN, app);
			_armorSlotDurBar[i] = createQuadWithMaterial("ChArmorDur" + i, _slotSize, 3f, _armorSlotDurMat[i]);
			_armorSlotDurBar[i].setLocalTranslation(_armorSlotX[i], _armorSlotY[i], Z_DURABILITY);
			_armorSlotDurBar[i].setCullHint(Geometry.CullHint.Always);
			_screenNode.attachChild(_armorSlotDurBar[i]);
		}
	}
	
	private static String getArmorSlotLabel(int index)
	{
		final String[] keys =
		{
			"screen.armor_head",
			"screen.armor_chest",
			"screen.armor_pants",
			"screen.armor_boots"
		};
		
		return LanguageManager.get(keys[index]);
	}
	
	/**
	 * Builds the 3D player model via offscreen viewport (same as InventoryScreen).
	 */
	private void buildPlayerModel(SimpleCraft app)
	{
		_modelScene = new Node("ChestModelScene");
		_modelRootNode = new Node("ChestModelRoot");
		_modelScene.attachChild(_modelRootNode);
		
		final Material skinMat = makeModelMat(COLOR_SKIN, app);
		final Material skinDarkMat = makeModelMat(new ColorRGBA(0.60f, 0.45f, 0.32f, 1.0f), app);
		final Material hairMat = makeModelMat(COLOR_HAIR, app);
		final Material eyeWhiteMat = makeModelMat(COLOR_EYE_WHITE, app);
		final Material eyeGreenMat = makeModelMat(COLOR_EYE_GREEN, app);
		final Material mouthMat = makeModelMat(new ColorRGBA(0.45f, 0.30f, 0.25f, 1.0f), app);
		final Material pantsMat = makeModelMat(COLOR_PANTS_BROWN, app);
		
		// Per-piece armor materials (recolored dynamically in updatePlayerModelOverlays).
		for (int i = 0; i < ArmorSlot.COUNT; i++)
		{
			_armorMatsMain[i] = makeModelMat(COLOR_IRON, app);
			_armorMatsDark[i] = makeModelMat(COLOR_IRON_DARK, app);
			_armorMatsLight[i] = makeModelMat(COLOR_IRON_LIGHT, app);
		}
		
		// Body.
		final Node bodyNode = makeModelPivot("Body", makeModelBox("BodyBox", 0.3f, 0.33f, 0.15f, skinMat, 0, 0, 0), 0, 1.27f, 0);
		_modelRootNode.attachChild(bodyNode);
		_baseWaistband = makeModelBox("Waistband", 0.31f, 0.12f, 0.16f, pantsMat, 0, 0.88f, 0);
		_modelRootNode.attachChild(_baseWaistband);
		_modelRootNode.attachChild(makeModelBox("Neck", 0.15f, 0.04f, 0.12f, skinMat, 0, 1.64f, 0));
		
		// Head.
		final Node headNode = makeModelPivot("Head", makeModelBox("HeadBox", 0.2f, 0.2f, 0.2f, skinMat, 0, 0.2f, 0), 0, 1.65f, 0);
		_modelRootNode.attachChild(headNode);
		
		// Hair.
		_baseHair = new Node("BaseHairTop");
		headNode.attachChild(_baseHair);
		_baseHair.attachChild(makeModelBox("HairCap", 0.21f, 0.08f, 0.19f, hairMat, 0, 0.37f, 0.02f));
		_baseHair.attachChild(makeModelBox("HairFringe", 0.21f, 0.03f, 0.02f, hairMat, 0, 0.37f, -0.19f));
		headNode.attachChild(makeModelBox("HairBack", 0.21f, 0.16f, 0.02f, hairMat, 0, 0.26f, 0.21f));
		headNode.attachChild(makeModelBox("HairLeft", 0.02f, 0.10f, 0.19f, hairMat, -0.21f, 0.30f, 0.04f));
		headNode.attachChild(makeModelBox("HairRight", 0.02f, 0.10f, 0.19f, hairMat, 0.21f, 0.30f, 0.04f));
		
		// Eyes, nose, mouth.
		headNode.attachChild(makeModelBox("LeftEyeWhite", 0.035f, 0.03f, 0.015f, eyeWhiteMat, -0.07f, 0.24f, -0.215f));
		headNode.attachChild(makeModelBox("RightEyeWhite", 0.035f, 0.03f, 0.015f, eyeWhiteMat, 0.07f, 0.24f, -0.215f));
		headNode.attachChild(makeModelBox("LeftIris", 0.018f, 0.018f, 0.005f, eyeGreenMat, -0.07f, 0.24f, -0.235f));
		headNode.attachChild(makeModelBox("RightIris", 0.018f, 0.018f, 0.005f, eyeGreenMat, 0.07f, 0.24f, -0.235f));
		headNode.attachChild(makeModelBox("Nose", 0.02f, 0.025f, 0.02f, skinDarkMat, 0, 0.17f, -0.22f));
		headNode.attachChild(makeModelBox("Mouth", 0.05f, 0.01f, 0.015f, mouthMat, 0, 0.1f, -0.215f));
		
		// Legs with pants.
		final Node leftLeg = makeModelPivot("LeftLeg", makeModelBox("LeftLegBox", 0.125f, 0.39f, 0.125f, skinMat, 0, -0.39f, 0), -0.175f, 0.8f, 0);
		_modelRootNode.attachChild(leftLeg);
		_basePantsLeft = makeModelBox("LPants", 0.135f, 0.25f, 0.135f, pantsMat, 0, -0.15f, 0);
		leftLeg.attachChild(_basePantsLeft);
		
		final Node rightLeg = makeModelPivot("RightLeg", makeModelBox("RightLegBox", 0.125f, 0.39f, 0.125f, skinMat, 0, -0.39f, 0), 0.175f, 0.8f, 0);
		_modelRootNode.attachChild(rightLeg);
		_basePantsRight = makeModelBox("RPants", 0.135f, 0.25f, 0.135f, pantsMat, 0, -0.15f, 0);
		rightLeg.attachChild(_basePantsRight);
		
		// Arms.
		_modelRootNode.attachChild(makeModelPivot("LeftArm", makeModelBox("LeftArmBox", 0.125f, 0.4f, 0.125f, skinMat, 0, -0.4f, 0), -0.425f, 1.55f, 0));
		_modelRootNode.attachChild(makeModelPivot("RightArm", makeModelBox("RightArmBox", 0.125f, 0.4f, 0.125f, skinMat, 0, -0.4f, 0), 0.425f, 1.55f, 0));
		
		// Armor nodes.
		_armor3dHelmet = new Node("ArmorHelmet");
		headNode.attachChild(_armor3dHelmet);
		_armor3dHelmet.attachChild(makeModelBox("HelmetCap", 0.23f, 0.08f, 0.23f, _armorMatsMain[0], 0, 0.44f, 0));
		_armor3dHelmet.attachChild(makeModelBox("HelmetBand", 0.24f, 0.06f, 0.24f, _armorMatsLight[0], 0, 0.34f, 0));
		_armor3dHelmet.attachChild(makeModelBox("NoseGuard", 0.02f, 0.10f, 0.025f, _armorMatsDark[0], 0, 0.27f, -0.24f));
		_armor3dHelmet.setCullHint(Node.CullHint.Always);
		
		_armor3dChestplate = new Node("ArmorChestplate");
		_modelRootNode.attachChild(_armor3dChestplate);
		_armor3dChestplate.attachChild(makeModelBox("IronBody", 0.31f, 0.34f, 0.18f, _armorMatsMain[1], 0, 1.27f, 0));
		_armor3dChestplate.attachChild(makeModelBox("ChestPlate", 0.22f, 0.2f, 0.005f, _armorMatsDark[1], 0, 1.32f, -0.20f));
		_armor3dChestplate.attachChild(makeModelBox("IronWaist", 0.32f, 0.13f, 0.17f, _armorMatsDark[1], 0, 0.88f, 0));
		_armor3dChestplate.attachChild(makeModelBox("LShoulderPlate", 0.15f, 0.18f, 0.15f, _armorMatsLight[1], -0.425f, 1.43f, 0));
		_armor3dChestplate.attachChild(makeModelBox("RShoulderPlate", 0.15f, 0.18f, 0.15f, _armorMatsLight[1], 0.425f, 1.43f, 0));
		_armor3dChestplate.setCullHint(Node.CullHint.Always);
		
		_armor3dPants = new Node("ArmorPants");
		_modelRootNode.attachChild(_armor3dPants);
		_armor3dPants.attachChild(makeModelBox("LIronPants", 0.135f, 0.25f, 0.135f, _armorMatsDark[2], -0.175f, 0.65f, 0));
		_armor3dPants.attachChild(makeModelBox("RIronPants", 0.135f, 0.25f, 0.135f, _armorMatsDark[2], 0.175f, 0.65f, 0));
		_armor3dPants.attachChild(makeModelBox("IronBelt", 0.31f, 0.12f, 0.16f, _armorMatsMain[2], 0, 0.88f, 0));
		_armor3dPants.setCullHint(Node.CullHint.Always);
		
		_armor3dBoots = new Node("ArmorBoots");
		_modelRootNode.attachChild(_armor3dBoots);
		_armor3dBoots.attachChild(makeModelBox("LBoots", 0.145f, 0.21f, 0.145f, _armorMatsMain[3], -0.175f, 0.2f, 0));
		_armor3dBoots.attachChild(makeModelBox("RBoots", 0.145f, 0.21f, 0.145f, _armorMatsMain[3], 0.175f, 0.2f, 0));
		_armor3dBoots.setCullHint(Node.CullHint.Always);
		
		// Offscreen viewport.
		final Camera modelCam = new Camera(MODEL_TEX_WIDTH, MODEL_TEX_HEIGHT);
		modelCam.setFrustumPerspective(45f, (float) MODEL_TEX_WIDTH / MODEL_TEX_HEIGHT, 0.1f, 10f);
		modelCam.setLocation(new Vector3f(0, 1.05f, -3.2f));
		modelCam.lookAt(new Vector3f(0, 1.05f, 0), Vector3f.UNIT_Y);
		
		_modelRenderTexture = new Texture2D(MODEL_TEX_WIDTH, MODEL_TEX_HEIGHT, Format.RGBA8);
		_modelRenderTexture.setMinFilter(MinFilter.BilinearNoMipMaps);
		_modelRenderTexture.setMagFilter(MagFilter.Bilinear);
		
		final FrameBuffer fb = new FrameBuffer(MODEL_TEX_WIDTH, MODEL_TEX_HEIGHT, 1);
		fb.setDepthTarget(FrameBufferTarget.newTarget(Format.Depth));
		fb.addColorTarget(FrameBufferTarget.newTarget(_modelRenderTexture));
		
		_modelViewPort = app.getRenderManager().createPreView("ChestPlayerModel", modelCam);
		_modelViewPort.setClearFlags(true, true, true);
		_modelViewPort.setBackgroundColor(new ColorRGBA(0, 0, 0, 0));
		_modelViewPort.setOutputFrameBuffer(fb);
		_modelViewPort.attachScene(_modelScene);
		_modelViewPort.setEnabled(false);
		
		_modelScene.updateGeometricState();
		
		// Display quad.
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
		_modelDisplayQuad = new Geometry("ChModelDisplay", displayQuad);
		_modelDisplayQuad.setMaterial(_modelDisplayMat);
		_modelDisplayQuad.setQueueBucket(Bucket.Gui);
		_modelDisplayQuad.setLocalTranslation(_modelDisplayX, _modelDisplayY, Z_SLOT_FILL);
		_screenNode.attachChild(_modelDisplayQuad);
	}
	
	private void buildTooltip(SimpleCraft app)
	{
		final Material bgMat = createColorMaterial(COLOR_TOOLTIP_BG, app);
		_tooltipBg = createQuadWithMaterial("ChTooltipBg", 100, 24, bgMat);
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
		_heldQuad = createQuadWithMaterial("ChHeld", _slotSize * 0.8f, _slotSize * 0.8f, _heldQuadMat);
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
		final BitmapFont titleFont = FontManager.getFont(app.getAssetManager(), FontManager.getRegularPath(), Font.PLAIN, titleSize);
		
		// "Chest" title above the chest slot section.
		_titleTextShadow = new BitmapText(titleFont);
		_titleTextShadow.setText(LanguageManager.get("screen.chest"));
		_titleTextShadow.setSize(titleSize);
		_titleTextShadow.setColor(COLOR_TEXT_SHADOW.clone());
		_screenNode.attachChild(_titleTextShadow);
		
		_titleText = new BitmapText(titleFont);
		_titleText.setText(LanguageManager.get("screen.chest"));
		_titleText.setSize(titleSize);
		_titleText.setColor(COLOR_TEXT.clone());
		_screenNode.attachChild(_titleText);
		
		// Grid center X for label centering (first hotbar slot to last hotbar slot).
		final float gridCenterX = (_slotX[ChestTileEntity.CHEST_SLOTS] + _slotX[ChestTileEntity.CHEST_SLOTS + 8] + _slotSize) / 2f;
		
		// Position above the top chest row.
		final float titleWidth = _titleText.getLineWidth();
		final float titleX = gridCenterX - titleWidth / 2f;
		
		// Top chest row is row 0 (display slots 0-8), which has the highest Y.
		final float topChestSlotY = _slotY[0];
		final float titleY = topChestSlotY + _slotSize + SLOT_PADDING + _titleText.getLineHeight() + 2;
		_titleText.setLocalTranslation(titleX, titleY, Z_TEXT);
		_titleTextShadow.setLocalTranslation(titleX + 1, titleY - 1, Z_TEXT - 0.1f);
		
		// "Inventory" label above the player main inventory section.
		_inventoryLabelShadow = new BitmapText(titleFont);
		_inventoryLabelShadow.setText(LanguageManager.get("screen.inventory"));
		_inventoryLabelShadow.setSize(titleSize);
		_inventoryLabelShadow.setColor(COLOR_TEXT_SHADOW.clone());
		_screenNode.attachChild(_inventoryLabelShadow);
		
		_inventoryLabel = new BitmapText(titleFont);
		_inventoryLabel.setText(LanguageManager.get("screen.inventory"));
		_inventoryLabel.setSize(titleSize);
		_inventoryLabel.setColor(COLOR_TEXT.clone());
		_screenNode.attachChild(_inventoryLabel);
		
		// Position above the top player main inventory row (display slot for player slot 9).
		final float invWidth = _inventoryLabel.getLineWidth();
		final float invX = gridCenterX - invWidth / 2f;
		final float topPlayerMainY = _slotY[ChestTileEntity.CHEST_SLOTS + 9];
		final float invY = topPlayerMainY + _slotSize + SLOT_PADDING + _inventoryLabel.getLineHeight() + 2;
		_inventoryLabel.setLocalTranslation(invX, invY, Z_TEXT);
		_inventoryLabelShadow.setLocalTranslation(invX + 1, invY - 1, Z_TEXT - 0.1f);
		
		// "Action Bar" label above the hotbar row, below the inventory.
		_actionBarLabelShadow = new BitmapText(titleFont);
		_actionBarLabelShadow.setText(LanguageManager.get("screen.action_bar"));
		_actionBarLabelShadow.setSize(titleSize);
		_actionBarLabelShadow.setColor(COLOR_TEXT_SHADOW.clone());
		_screenNode.attachChild(_actionBarLabelShadow);
		
		_actionBarLabel = new BitmapText(titleFont);
		_actionBarLabel.setText(LanguageManager.get("screen.action_bar"));
		_actionBarLabel.setSize(titleSize);
		_actionBarLabel.setColor(COLOR_TEXT.clone());
		_screenNode.attachChild(_actionBarLabel);
		
		final float abWidth = _actionBarLabel.getLineWidth();
		final float abX = gridCenterX - abWidth / 2f;
		final float hotbarTopY = _slotY[ChestTileEntity.CHEST_SLOTS];
		final float abY = hotbarTopY + _slotSize + SLOT_PADDING + _actionBarLabel.getLineHeight() + 2;
		_actionBarLabel.setLocalTranslation(abX, abY, Z_TEXT);
		_actionBarLabelShadow.setLocalTranslation(abX + 1, abY - 1, Z_TEXT - 0.1f);
		
		// "Armor" label above the armor slots.
		_armorTitleTextShadow = new BitmapText(titleFont);
		_armorTitleTextShadow.setText(LanguageManager.get("screen.armor"));
		_armorTitleTextShadow.setSize(titleSize);
		_armorTitleTextShadow.setColor(COLOR_TEXT_SHADOW.clone());
		_screenNode.attachChild(_armorTitleTextShadow);
		
		_armorTitleText = new BitmapText(titleFont);
		_armorTitleText.setText(LanguageManager.get("screen.armor"));
		_armorTitleText.setSize(titleSize);
		_armorTitleText.setColor(COLOR_TEXT.clone());
		_screenNode.attachChild(_armorTitleText);
		
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
	 * Opens the chest screen for the given chest tile entity.
	 * @param chest the chest to display and edit
	 */
	public void open(ChestTileEntity chest)
	{
		if (_open)
		{
			return;
		}
		
		_open = true;
		_activeChest = chest;
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
		
		// Register shift key listeners for shift-click transfer and splitting.
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
	 * Closes the chest screen, hiding the cursor and restoring player controls.
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
		
		_activeChest = null;
		
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
		
		System.out.println("ChestScreen: Closed.");
	}
	
	/**
	 * Returns true if the chest screen is currently open.
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
		if (!_open || _activeChest == null)
		{
			return;
		}
		
		// Refresh slot visuals (chest + inventory + armor).
		refreshAllSlots();
		
		// Get cursor position.
		final Vector2f cursor = _inputManager.getCursorPosition();
		final float cx = cursor.x;
		final float cy = cursor.y;
		
		// Mouse-drag rotation of 3D model.
		if (_mouseDown)
		{
			final float deltaX = cx - _prevCursorX;
			final float sensitivityScale = Math.max(0.05f, SimpleCraft.getInstance().getSettingsManager().getMouseSensitivity());
			if (cx >= _modelDisplayX && cx < _modelDisplayX + _modelDisplayWidth && cy >= _modelDisplayY && cy < _modelDisplayY + _modelDisplayHeight)
			{
				_modelRotation += deltaX * 0.01f * sensitivityScale;
			}
		}
		
		_prevCursorX = cx;
		
		_modelRootNode.setLocalRotation(new Quaternion().fromAngleAxis(_modelRotation, Vector3f.UNIT_Y));
		_modelScene.updateLogicalState(tpf);
		_modelScene.updateGeometricState();
		
		// Determine hovered slot.
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
	 * Refreshes the visual state of all display slots.
	 */
	private void refreshAllSlots()
	{
		// Chest slots (display 0-26).
		for (int i = 0; i < ChestTileEntity.CHEST_SLOTS; i++)
		{
			final ItemInstance stack = _activeChest.getSlot(i);
			updateSlotVisual(i, stack);
		}
		
		// Player slots (display 27-62 maps to inventory 0-35).
		for (int i = 0; i < Inventory.TOTAL_SLOTS; i++)
		{
			final ItemInstance stack = _inventory.getSlot(i);
			updateSlotVisual(ChestTileEntity.CHEST_SLOTS + i, stack);
		}
		
		// Armor slots and 3D model overlays.
		for (int i = 0; i < ArmorSlot.COUNT; i++)
		{
			final ArmorSlot slot = ArmorSlot.fromIndex(i);
			final ItemInstance armorItem = _inventory.getArmorSlot(slot);
			updateArmorSlotVisual(i, armorItem);
		}
		
		updatePlayerModelOverlays();
	}
	
	/**
	 * Updates the visual representation of a single display slot.
	 */
	private void updateSlotVisual(int index, ItemInstance stack)
	{
		if (stack == null || stack.isEmpty())
		{
			_slotFillMat[index].clearParam("ColorMap");
			_slotFillMat[index].setColor("Color", index < ChestTileEntity.CHEST_SLOTS ? COLOR_CHEST_SLOT_EMPTY : COLOR_SLOT_EMPTY);
			_slotLabel[index].setCullHint(BitmapText.CullHint.Always);
			_slotCount[index].setCullHint(BitmapText.CullHint.Always);
			_slotCountShadow[index].setCullHint(BitmapText.CullHint.Always);
			_slotDurBar[index].setCullHint(Geometry.CullHint.Always);
			return;
		}
		
		final ItemTemplate template = stack.getTemplate();
		
		// Try to resolve a sprite texture.
		final Texture slotTexture = ItemTextureResolver.resolve(SimpleCraft.getInstance().getAssetManager(), template);
		
		if (slotTexture != null)
		{
			_slotFillMat[index].setTexture("ColorMap", slotTexture);
			_slotFillMat[index].setColor("Color", ColorRGBA.White);
			_slotLabel[index].setCullHint(BitmapText.CullHint.Always);
		}
		else
		{
			_slotFillMat[index].clearParam("ColorMap");
			final ColorRGBA fillColor = InventoryScreen.getItemColor(template);
			_slotFillMat[index].setColor("Color", fillColor);
			
			final String label = InventoryScreen.getItemLabel(template);
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
		
		// Count.
		if (stack.getCount() > 1)
		{
			final String countStr = String.valueOf(stack.getCount());
			_slotCount[index].setText(countStr);
			_slotCountShadow[index].setText(countStr);
			
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
	
	private void updateTooltip(float cx, float cy)
	{
		// Determine which item to tooltip.
		ItemInstance stack = null;
		if (_hoveredSlot >= 0)
		{
			stack = getItemAtDisplaySlot(_hoveredSlot);
		}
		else if (_hoveredArmorIndex >= 0)
		{
			stack = _inventory.getArmorSlot(ArmorSlot.fromIndex(_hoveredArmorIndex));
		}
		
		if (stack == null || stack.isEmpty())
		{
			_tooltipBg.setCullHint(Geometry.CullHint.Always);
			_tooltipText.setCullHint(BitmapText.CullHint.Always);
			_tooltipTextShadow.setCullHint(BitmapText.CullHint.Always);
			return;
		}
		
		String text = stack.getTemplate().getDisplayName();
		if (stack.hasDurability())
		{
			text += " [" + stack.getDurability() + "/" + stack.getTemplate().getMaxDurability() + "]";
		}
		
		_tooltipText.setText(text);
		_tooltipTextShadow.setText(text);
		
		final float tipWidth = _tooltipText.getLineWidth();
		final float tipHeight = _tooltipText.getLineHeight();
		final float padding = 6f;
		
		float tipX = cx + 14;
		float tipY = cy + tipHeight + padding + 4;
		
		if (tipX + tipWidth + padding * 2 > _screenWidth)
		{
			tipX = cx - tipWidth - padding * 2 - 4;
		}
		
		if (tipY > _screenHeight)
		{
			tipY = _screenHeight - 4;
		}
		
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
			final ColorRGBA color = InventoryScreen.getItemColor(_heldStack.getTemplate());
			_heldQuadMat.setColor("Color", color);
			
			final String label = InventoryScreen.getItemLabel(_heldStack.getTemplate());
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
				handleRightClick(slot, _shiftDown);
			}
		}
	}
	
	/**
	 * Handles left-click on an armor slot.
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
			if (equipped != null && !equipped.isEmpty())
			{
				_heldStack = equipped;
				_inventory.setArmorSlot(slot, null);
			}
		}
		else
		{
			final ItemTemplate template = _heldStack.getTemplate();
			if (template.getType() == ItemType.ARMOR && template.getArmorSlot() == slot)
			{
				_inventory.setArmorSlot(slot, _heldStack);
				_heldStack = (equipped != null && !equipped.isEmpty()) ? equipped : null;
			}
		}
	}
	
	/**
	 * Left-click: pick up, place, swap, or merge stacks.<br>
	 * If shift is held and no stack is being carried, transfers the entire stack to the opposite inventory (chest ↔ player).
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
		
		if (shiftDown && _heldStack == null)
		{
			// Shift-click transfer (only when not holding anything).
			transferStack(slot);
			return;
		}
		
		// Normal left-click behavior.
		if (_heldStack == null)
		{
			// Pick up stack from slot.
			final ItemInstance target = getItemAtDisplaySlot(slot);
			if (target != null && !target.isEmpty())
			{
				_heldStack = target;
				setItemAtDisplaySlot(slot, null);
			}
		}
		else
		{
			final ItemInstance target = getItemAtDisplaySlot(slot);
			
			if (target == null || target.isEmpty())
			{
				// Place held stack in empty slot.
				setItemAtDisplaySlot(slot, _heldStack);
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
					_heldStack = new ItemInstance(_heldStack.getTemplate(), overflow);
				}
			}
			else
			{
				// Swap: exchange held and slot contents.
				setItemAtDisplaySlot(slot, _heldStack);
				_heldStack = target;
			}
		}
	}
	
	/**
	 * Right-click: place a single item from held stack.<br>
	 * If shift is held, performs splitting (pick up half or place half into empty slot).
	 */
	private void handleRightClick(int slot, boolean shiftDown)
	{
		if (slot < 0)
		{
			return;
		}
		
		if (shiftDown)
		{
			// Shift+right-click: splitting behavior.
			if (_heldStack == null)
			{
				// Pick up half from slot
				final ItemInstance target = getItemAtDisplaySlot(slot);
				if (target != null && !target.isEmpty())
				{
					final int total = target.getCount();
					final int take = (total + 1) / 2; // ceil half.
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
						setItemAtDisplaySlot(slot, null);
					}
				}
			}
			else
			{
				// Place half into empty slot.
				final ItemInstance target = getItemAtDisplaySlot(slot);
				if (target == null || target.isEmpty())
				{
					final int total = _heldStack.getCount();
					final int put = (total + 1) / 2;
					final int keep = total - put;
					if (keep > 0)
					{
						_heldStack.setCount(keep);
						setItemAtDisplaySlot(slot, new ItemInstance(_heldStack.getTemplate(), put));
					}
					else
					{
						// Put whole stack (when total == 1).
						setItemAtDisplaySlot(slot, _heldStack);
						_heldStack = null;
					}
				}
				
				// If slot is not empty, do nothing for shift-click (could be extended later).
			}
			return;
		}
		
		// Original non-shift logic: place one item from held stack.
		if (_heldStack == null)
		{
			return;
		}
		
		final ItemInstance target = getItemAtDisplaySlot(slot);
		
		if (target == null || target.isEmpty())
		{
			// Place one item in empty slot.
			setItemAtDisplaySlot(slot, new ItemInstance(_heldStack.getTemplate(), 1));
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
	
	/**
	 * Transfers the entire stack from the given display slot to the opposite inventory.<br>
	 * Chest slot -> player inventory, player slot -> chest.
	 */
	private void transferStack(int displaySlot)
	{
		final ItemInstance stack = getItemAtDisplaySlot(displaySlot);
		if (stack == null || stack.isEmpty())
		{
			return;
		}
		
		final boolean isChestSlot = displaySlot < ChestTileEntity.CHEST_SLOTS;
		
		if (isChestSlot)
		{
			// Transfer from chest to player inventory.
			if (_inventory.addItem(stack))
			{
				_activeChest.setSlot(displaySlot, null);
			}
		}
		else
		{
			// Transfer from player inventory to chest.
			if (addToChest(stack))
			{
				final int playerSlot = displaySlot - ChestTileEntity.CHEST_SLOTS;
				_inventory.setSlot(playerSlot, null);
			}
		}
	}
	
	/**
	 * Attempts to add an ItemInstance to the chest.<br>
	 * First tries to merge with existing matching stacks, then fills the first empty slot.
	 * @param stack the ItemInstance to add
	 * @return true if all items were placed, false if chest is full
	 */
	private boolean addToChest(ItemInstance stack)
	{
		if (stack == null || stack.isEmpty())
		{
			return true;
		}
		
		int remaining = stack.getCount();
		
		// Phase 1: Merge with existing matching stacks.
		for (int i = 0; i < ChestTileEntity.CHEST_SLOTS; i++)
		{
			if (remaining <= 0)
			{
				return true;
			}
			
			final ItemInstance existing = _activeChest.getSlot(i);
			if (existing != null && existing.canStackWith(stack))
			{
				remaining = existing.add(remaining);
			}
		}
		
		// Phase 2: Fill first empty slot.
		if (remaining > 0)
		{
			for (int i = 0; i < ChestTileEntity.CHEST_SLOTS; i++)
			{
				if (_activeChest.getSlot(i) == null)
				{
					_activeChest.setSlot(i, new ItemInstance(stack.getTemplate(), remaining));
					return true;
				}
			}
			
			// Chest is full.
			return false;
		}
		
		return true;
	}
	
	// ========================================================
	// Slot Mapping.
	// ========================================================
	
	/**
	 * Returns the item at the given display slot index.<br>
	 * Display slots 0-26 -> chest contents, 27-62 -> player inventory 0-35.
	 */
	private ItemInstance getItemAtDisplaySlot(int displaySlot)
	{
		if (displaySlot < 0)
		{
			return null;
		}
		
		if (displaySlot < ChestTileEntity.CHEST_SLOTS)
		{
			return _activeChest.getSlot(displaySlot);
		}
		
		final int playerSlot = displaySlot - ChestTileEntity.CHEST_SLOTS;
		if (playerSlot >= 0 && playerSlot < Inventory.TOTAL_SLOTS)
		{
			return _inventory.getSlot(playerSlot);
		}
		
		return null;
	}
	
	/**
	 * Sets the item at the given display slot index.
	 */
	private void setItemAtDisplaySlot(int displaySlot, ItemInstance stack)
	{
		if (displaySlot < 0)
		{
			return;
		}
		
		if (displaySlot < ChestTileEntity.CHEST_SLOTS)
		{
			_activeChest.setSlot(displaySlot, stack);
			return;
		}
		
		final int playerSlot = displaySlot - ChestTileEntity.CHEST_SLOTS;
		if (playerSlot >= 0 && playerSlot < Inventory.TOTAL_SLOTS)
		{
			_inventory.setSlot(playerSlot, stack);
		}
	}
	
	// ========================================================
	// Slot Hit Detection.
	// ========================================================
	
	/**
	 * Returns the display slot index at the given screen position, or -1 if outside.
	 */
	private int getSlotAtPosition(float x, float y)
	{
		for (int i = 0; i < TOTAL_DISPLAY_SLOTS; i++)
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
	
	/**
	 * Updates the visual representation of a single armor slot.
	 */
	private void updateArmorSlotVisual(int index, ItemInstance armorItem)
	{
		if (armorItem == null || armorItem.isEmpty())
		{
			_armorSlotFillMat[index].clearParam("ColorMap");
			_armorSlotFillMat[index].setColor("Color", COLOR_ARMOR_SLOT_EMPTY);
			_armorSlotLabel[index].setCullHint(BitmapText.CullHint.Never);
			_armorSlotDurBar[index].setCullHint(Geometry.CullHint.Always);
			return;
		}
		
		final ItemTemplate template = armorItem.getTemplate();
		final Texture slotTexture = ItemTextureResolver.resolve(SimpleCraft.getInstance().getAssetManager(), template);
		
		if (slotTexture != null)
		{
			_armorSlotFillMat[index].setTexture("ColorMap", slotTexture);
			_armorSlotFillMat[index].setColor("Color", ColorRGBA.White);
		}
		else
		{
			_armorSlotFillMat[index].clearParam("ColorMap");
			_armorSlotFillMat[index].setColor("Color", InventoryScreen.getItemColor(template));
		}
		
		_armorSlotLabel[index].setCullHint(BitmapText.CullHint.Always);
		
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
	 * Recolors the 3D armor materials for the given piece index based on the item ID.
	 */
	private void recolorArmorMaterials(int index, String itemId)
	{
		final boolean isGold = itemId.startsWith("gold_");
		_armorMatsMain[index].setColor("Color", isGold ? COLOR_GOLD.clone() : COLOR_IRON.clone());
		_armorMatsDark[index].setColor("Color", isGold ? COLOR_GOLD_DARK.clone() : COLOR_IRON_DARK.clone());
		_armorMatsLight[index].setColor("Color", isGold ? COLOR_GOLD_LIGHT.clone() : COLOR_IRON_LIGHT.clone());
	}
	
	// ========================================================
	// World Drop Support.
	// ========================================================
	
	/**
	 * Sets the drop manager for spawning world drops when items are discarded.
	 */
	public void setDropManager(DropManager dropManager)
	{
		_dropManager = dropManager;
	}
	
	/**
	 * Sets the world reference for ground-level lookups when dropping items.
	 */
	public void setWorld(World world)
	{
		_world = world;
	}
	
	/**
	 * Drops an item stack into the world slightly in front of the player.
	 */
	private void dropItemIntoWorld(ItemInstance stack)
	{
		if (stack == null || stack.isEmpty())
		{
			return;
		}
		
		if (_dropManager == null)
		{
			System.out.println("ChestScreen: Discarded (no DropManager): " + stack);
			return;
		}
		
		final Vector3f playerPos = _playerController.getPosition();
		final Vector3f camDir = SimpleCraft.getInstance().getCamera().getDirection();
		
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
			dropX = playerPos.x;
			dropZ = playerPos.z;
		}
		
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
	}
	
	// ========================================================
	// Cleanup.
	// ========================================================
	
	/**
	 * Removes all chest screen elements from the GUI node.
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
	
	private Material makeModelMat(ColorRGBA color, SimpleCraft app)
	{
		final Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
		mat.setColor("Color", color.clone());
		return mat;
	}
	
	private Geometry makeModelBox(String name, float halfX, float halfY, float halfZ, Material mat, float tx, float ty, float tz)
	{
		final Box box = new Box(halfX, halfY, halfZ);
		final Geometry geom = new Geometry(name, box);
		geom.setMaterial(mat);
		geom.setLocalTranslation(tx, ty, tz);
		return geom;
	}
	
	private Node makeModelPivot(String name, Geometry geom, float pivotX, float pivotY, float pivotZ)
	{
		final Node pivot = new Node(name);
		pivot.setLocalTranslation(pivotX, pivotY, pivotZ);
		pivot.attachChild(geom);
		return pivot;
	}
}
