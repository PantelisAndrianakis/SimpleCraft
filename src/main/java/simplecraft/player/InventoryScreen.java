package simplecraft.player;

import java.awt.Font;

import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.InputManager;
import com.jme3.input.controls.ActionListener;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;

import simplecraft.SimpleCraft;
import simplecraft.input.GameInputManager;
import simplecraft.item.DropManager;
import simplecraft.item.Inventory;
import simplecraft.item.ItemInstance;
import simplecraft.item.ItemRegistry;
import simplecraft.item.ItemTemplate;
import simplecraft.item.ItemTextureResolver;
import simplecraft.ui.FontManager;
import simplecraft.world.Block;
import simplecraft.world.World;

/**
 * Full inventory screen opened with Tab.<br>
 * Displays all 36 inventory slots in a 4-row × 9-column grid.<br>
 * Top row = hotbar (slots 0-8), visually separated from bottom 3 rows (slots 9-35).<br>
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
	
	/** Pixel gap between hotbar row and main inventory rows. */
	private static final float HOTBAR_GAP = 8f;
	
	/** Pixel gap between individual slots. */
	private static final float SLOT_SPACING = 3f;
	
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
	
	// Hover highlight.
	private Geometry _hoverHighlight;
	private Material _hoverHighlightMat;
	private int _hoveredSlot = -1;
	
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
	
	/** Reusable color to avoid allocation each frame. */
	// private final ColorRGBA _tempColor = new ColorRGBA();
	
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
	 * Computes screen-space positions for all 36 slots.<br>
	 * Layout: hotbar row at the top of the grid, 3 main rows below with a gap.
	 */
	private void computeSlotPositions()
	{
		final float totalGridWidth = GRID_COLS * _slotSize + (GRID_COLS - 1) * SLOT_SPACING;
		final float totalGridHeight = GRID_ROWS * _slotSize + (GRID_ROWS - 1) * SLOT_SPACING + HOTBAR_GAP;
		
		final float gridStartX = (_screenWidth - totalGridWidth) / 2f;
		final float gridStartY = (_screenHeight - totalGridHeight) / 2f;
		
		// Main inventory rows (slots 9-35): bottom 3 rows.
		for (int row = 0; row < 3; row++)
		{
			for (int col = 0; col < GRID_COLS; col++)
			{
				final int slotIndex = 9 + row * GRID_COLS + col;
				_slotX[slotIndex] = gridStartX + col * (_slotSize + SLOT_SPACING);
				// Row 0 of main = bottom row (slots 27-35), row 2 = top of main (slots 9-17).
				_slotY[slotIndex] = gridStartY + (2 - row) * (_slotSize + SLOT_SPACING);
			}
		}
		
		// Hotbar row (slots 0-8): top row, above main inventory with gap.
		final float hotbarY = gridStartY + 3 * (_slotSize + SLOT_SPACING) + HOTBAR_GAP;
		for (int col = 0; col < GRID_COLS; col++)
		{
			_slotX[col] = gridStartX + col * (_slotSize + SLOT_SPACING);
			_slotY[col] = hotbarY;
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
		
		// Position above the hotbar row.
		final float titleWidth = _titleText.getLineWidth();
		final float titleX = (_screenWidth - titleWidth) / 2f;
		final float titleY = _slotY[0] + _slotSize + SLOT_PADDING + _titleText.getLineHeight() + 8;
		_titleText.setLocalTranslation(titleX, titleY, Z_TEXT);
		_titleTextShadow.setLocalTranslation(titleX + 1, titleY - 1, Z_TEXT - 0.1f);
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
		
		// Force full slot refresh.
		refreshAllSlots();
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
		
		// Hide held item visuals.
		_heldQuad.setCullHint(Geometry.CullHint.Always);
		_heldLabel.setCullHint(BitmapText.CullHint.Always);
		_heldCount.setCullHint(BitmapText.CullHint.Always);
		_heldCountShadow.setCullHint(BitmapText.CullHint.Always);
		
		// Remove click listeners.
		_inputManager.removeListener(this);
		
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
		
		// Determine hovered slot.
		_hoveredSlot = getSlotAtPosition(cx, cy);
		
		// Update hover highlight.
		if (_hoveredSlot >= 0)
		{
			_hoverHighlight.setLocalTranslation(_slotX[_hoveredSlot], _slotY[_hoveredSlot], Z_HIGHLIGHT);
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
	 * Refreshes the visual state of all 36 slots from inventory data.
	 */
	private void refreshAllSlots()
	{
		for (int i = 0; i < Inventory.TOTAL_SLOTS; i++)
		{
			final ItemInstance stack = _inventory.getSlot(i);
			updateSlotVisual(i, stack);
		}
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
		final com.jme3.texture.Texture slotTexture = ItemTextureResolver.resolve(SimpleCraft.getInstance().getAssetManager(), template);
		
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
			// final float barWidth = Math.max(1f, percent * _slotSize);
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
	
	private void updateTooltip(float cx, float cy)
	{
		if (_hoveredSlot < 0)
		{
			_tooltipBg.setCullHint(Geometry.CullHint.Always);
			_tooltipText.setCullHint(BitmapText.CullHint.Always);
			_tooltipTextShadow.setCullHint(BitmapText.CullHint.Always);
			return;
		}
		
		final ItemInstance stack = _inventory.getSlot(_hoveredSlot);
		if (stack == null || stack.isEmpty())
		{
			_tooltipBg.setCullHint(Geometry.CullHint.Always);
			_tooltipText.setCullHint(BitmapText.CullHint.Always);
			_tooltipTextShadow.setCullHint(BitmapText.CullHint.Always);
			return;
		}
		
		// Build tooltip text.
		String text = stack.getTemplate().getDisplayName();
		if (stack.hasDurability())
		{
			text += " [" + stack.getDurability() + "/" + stack.getTemplate().getMaxDurability() + "]";
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
		final com.jme3.texture.Texture heldTexture = ItemTextureResolver.resolve(SimpleCraft.getInstance().getAssetManager(), _heldStack.getTemplate());
		
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
		if (!_open || !isPressed)
		{
			return;
		}
		
		final Vector2f cursor = _inputManager.getCursorPosition();
		final int slot = getSlotAtPosition(cursor.x, cursor.y);
		
		if (GameInputManager.ATTACK.equals(name))
		{
			handleLeftClick(slot);
		}
		else if (GameInputManager.PLACE_BLOCK.equals(name))
		{
			handleRightClick(slot);
		}
	}
	
	/**
	 * Left-click: pick up, place, swap, or merge stacks.
	 */
	private void handleLeftClick(int slot)
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
	 * Returns the slot index at the given screen position, or -1 if outside all slots.
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
}
