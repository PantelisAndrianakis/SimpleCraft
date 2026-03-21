package simplecraft.player;

import java.awt.Font;
import java.util.Map;
import java.util.Map.Entry;

import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;

import simplecraft.SimpleCraft;
import simplecraft.item.DropManager;
import simplecraft.item.Inventory;
import simplecraft.item.ItemInstance;
import simplecraft.item.ItemTemplate;
import simplecraft.item.ItemTextureResolver;
import simplecraft.item.SmeltingRegistry;
import simplecraft.ui.FontManager;
import simplecraft.world.entity.FurnaceTileEntity;

/**
 * Furnace UI opened by right-clicking a Furnace block.<br>
 * Displays three furnace slots (input, fuel, output), a flame indicator for fuel burn,<br>
 * an arrow indicator for smelt progress and the player's inventory grid below.<br>
 * <br>
 * While open, player movement and block interaction input are unregistered<br>
 * (same pattern as {@link InventoryScreen} and {@link CraftingScreen}).<br>
 * Close with Escape or Tab.<br>
 * <br>
 * <b>Interaction:</b><br>
 * - Click input/fuel slots to place/swap items from held cursor.<br>
 * - Click output slot to take crafted items (can only take, not place).<br>
 * - Input slot only accepts items with a valid smelting recipe.<br>
 * - Fuel slot only accepts items with a positive burn time.<br>
 * <br>
 * The screen reads smelting progress and fuel state directly from the<br>
 * {@link FurnaceTileEntity} each frame via getters.
 * @author Pantelis Andrianakis
 * @since March 19th 2026
 */
public class FurnaceScreen implements ActionListener
{
	// ========================================================
	// Constants.
	// ========================================================
	
	/** Slot visual size in pixels. */
	private static final float SLOT_SIZE = 48;
	
	/** Icon size inside a slot (slightly smaller for padding). */
	private static final float ICON_SIZE = 40;
	
	/** Icon padding inside a slot. */
	private static final float ICON_PAD = (SLOT_SIZE - ICON_SIZE) / 2;
	
	/** Furnace slot visual size in pixels (doubled for prominence). */
	private static final float FURNACE_SLOT_SIZE = 96;
	
	/** Furnace icon size inside a furnace slot. */
	private static final float FURNACE_ICON_SIZE = 80;
	
	/** Furnace icon padding inside a furnace slot. */
	private static final float FURNACE_ICON_PAD = (FURNACE_SLOT_SIZE - FURNACE_ICON_SIZE) / 2;
	
	/** Recipe panel icon size. */
	private static final float RECIPE_ICON_SIZE = 32;
	
	/** Recipe row height. */
	private static final float RECIPE_ROW_HEIGHT = 40;
	
	/** Recipe panel left margin from screen edge. */
	private static final float RECIPE_PANEL_MARGIN = 20;
	
	/** Gap between slots. */
	private static final float SLOT_GAP = 8;
	
	/** Inventory grid columns. */
	private static final int INV_COLS = 9;
	
	/** Number of main inventory rows (excluding hotbar). */
	private static final int INV_ROWS = 3;
	
	/** Background overlay alpha. */
	private static final float OVERLAY_ALPHA = 0.6f;
	
	/** Slot background alpha (empty). */
	private static final float SLOT_BG_ALPHA = 0.4f;
	
	/** Font size for slot labels and count text. */
	private static final int FONT_SIZE = 16;
	
	/** Font size for the title. */
	private static final int TITLE_FONT_SIZE = 24;
	
	/** Input action for furnace screen click. */
	private static final String ACTION_FURNACE_CLICK = "FURNACE_CLICK";
	
	/** Z-depth layers for GUI elements. */
	private static final float Z_OVERLAY = 0;
	private static final float Z_SLOT_BG = 1;
	private static final float Z_ICON = 2;
	private static final float Z_LABEL = 3;
	private static final float Z_PROGRESS = 2;
	private static final float Z_PROGRESS_FILL = 3;
	private static final float Z_TITLE = 5;
	private static final float Z_CURSOR = 10;
	
	// ========================================================
	// Fields.
	// ========================================================
	
	private final PlayerController _playerController;
	private final BlockInteraction _blockInteraction;
	
	/** The furnace tile entity currently being viewed. */
	private FurnaceTileEntity _furnace;
	
	/** Whether the screen is currently open. */
	private boolean _open;
	
	/** Item held on the cursor (being moved between slots). */
	private ItemInstance _cursorItem;
	
	/** Root GUI node for all furnace UI elements. */
	private Node _rootNode;
	
	// Furnace slot icon geometries and materials.
	private Geometry _inputIcon;
	private Material _inputIconMat;
	private BitmapText _inputCountLabel;
	
	private Geometry _fuelIcon;
	private Material _fuelIconMat;
	private BitmapText _fuelCountLabel;
	
	private Geometry _outputIcon;
	private Material _outputIconMat;
	private BitmapText _outputCountLabel;
	
	// Progress indicators.
	private Geometry _arrowFill;
	private Geometry _flameFill;
	
	// Inventory slot icon geometries, materials and count labels.
	private final Geometry[] _invIcons = new Geometry[Inventory.TOTAL_SLOTS];
	private final Material[] _invIconMats = new Material[Inventory.TOTAL_SLOTS];
	private final BitmapText[] _invCountLabels = new BitmapText[Inventory.TOTAL_SLOTS];
	
	// Cursor item display.
	private Geometry _cursorIcon;
	private Material _cursorIconMat;
	private BitmapText _cursorCountLabel;
	
	// Slot positions (screen coordinates, bottom-left origin).
	private float _inputSlotX;
	private float _inputSlotY;
	private float _fuelSlotX;
	private float _fuelSlotY;
	private float _outputSlotX;
	private float _outputSlotY;
	private final float[] _invSlotX = new float[Inventory.TOTAL_SLOTS];
	private final float[] _invSlotY = new float[Inventory.TOTAL_SLOTS];
	
	/** Cached item templates to detect when slot contents change. */
	private ItemTemplate _lastInputTemplate;
	private ItemTemplate _lastFuelTemplate;
	private ItemTemplate _lastOutputTemplate;
	private final ItemTemplate[] _lastInvTemplates = new ItemTemplate[Inventory.TOTAL_SLOTS];
	private ItemTemplate _lastCursorTemplate;
	
	/** Drop manager for spawning world drops. */
	private DropManager _dropManager;
	
	/** Shift tracking. */
	private boolean _shiftDown;
	private final ActionListener _shiftListener = (name, isPressed, tpf) ->
	{
		if (name.equals("SHIFT_LEFT") || name.equals("SHIFT_RIGHT"))
		{
			_shiftDown = isPressed;
		}
	};
	
	// ========================================================
	// Constructor.
	// ========================================================
	
	/**
	 * Creates a new FurnaceScreen (hidden initially).
	 * @param playerController player controller for inventory access
	 * @param blockInteraction block interaction for input suppression
	 */
	public FurnaceScreen(PlayerController playerController, BlockInteraction blockInteraction)
	{
		_playerController = playerController;
		_blockInteraction = blockInteraction;
	}
	
	// ========================================================
	// Open / Close.
	// ========================================================
	
	/**
	 * Opens the furnace screen for the given furnace tile entity.
	 * @param furnace the furnace to interact with
	 */
	public void open(FurnaceTileEntity furnace)
	{
		if (_open)
		{
			return;
		}
		
		_furnace = furnace;
		_furnace.setActiveScreen(this);
		_open = true;
		_cursorItem = null;
		_shiftDown = false;
		
		final SimpleCraft app = SimpleCraft.getInstance();
		
		// Disable player movement and block interaction input.
		_playerController.unregisterInput();
		_blockInteraction.unregisterInput();
		
		// Build UI.
		buildUI(app);
		
		// Attach to guiNode.
		app.getGuiNode().attachChild(_rootNode);
		
		// Show cursor.
		app.getInputManager().setCursorVisible(true);
		
		// Register click listener.
		registerInput(app.getInputManager());
		
		// Register shift key listeners for split-stack detection.
		app.getInputManager().addMapping("SHIFT_LEFT", new KeyTrigger(KeyInput.KEY_LSHIFT));
		app.getInputManager().addMapping("SHIFT_RIGHT", new KeyTrigger(KeyInput.KEY_RSHIFT));
		app.getInputManager().addListener(_shiftListener, "SHIFT_LEFT", "SHIFT_RIGHT");
		
		System.out.println("FurnaceScreen: Opened.");
	}
	
	/**
	 * Closes the furnace screen.
	 */
	public void close()
	{
		if (!_open)
		{
			return;
		}
		
		_open = false;
		
		final SimpleCraft app = SimpleCraft.getInstance();
		final InputManager inputManager = app.getInputManager();
		
		// Unregister click listener.
		unregisterInput(inputManager);
		
		// Remove shift listeners.
		inputManager.removeListener(_shiftListener);
		inputManager.deleteMapping("SHIFT_LEFT");
		inputManager.deleteMapping("SHIFT_RIGHT");
		
		// Return cursor item to player inventory or drop it.
		if (_cursorItem != null && !_cursorItem.isEmpty())
		{
			final boolean added = _playerController.getInventory().addItem(_cursorItem);
			if (!added && _dropManager != null)
			{
				final Vector3f pos = _playerController.getPosition();
				_dropManager.spawnDrop(new Vector3f(pos.x, pos.y + 1.0f, pos.z), _cursorItem);
			}
			
			_cursorItem = null;
		}
		
		// Clear active screen reference.
		if (_furnace != null)
		{
			_furnace.setActiveScreen(null);
			_furnace = null;
		}
		
		// Detach UI.
		if (_rootNode != null)
		{
			app.getGuiNode().detachChild(_rootNode);
			_rootNode = null;
		}
		
		// Clear cached templates.
		_lastInputTemplate = null;
		_lastFuelTemplate = null;
		_lastOutputTemplate = null;
		_lastCursorTemplate = null;
		for (int i = 0; i < Inventory.TOTAL_SLOTS; i++)
		{
			_lastInvTemplates[i] = null;
		}
		
		// Restore player movement and block interaction input.
		_playerController.registerInput();
		_blockInteraction.registerInput();
		
		// Hide cursor.
		inputManager.setCursorVisible(false);
		
		System.out.println("FurnaceScreen: Closed.");
	}
	
	/**
	 * Returns true if the screen is currently open.
	 */
	public boolean isOpen()
	{
		return _open;
	}
	
	// ========================================================
	// Input Registration.
	// ========================================================
	
	/**
	 * Registers mouse input for the furnace screen.
	 */
	private void registerInput(InputManager inputManager)
	{
		if (!inputManager.hasMapping(ACTION_FURNACE_CLICK))
		{
			inputManager.addMapping(ACTION_FURNACE_CLICK, new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
		}
		
		inputManager.addListener(this, ACTION_FURNACE_CLICK);
	}
	
	/**
	 * Unregisters mouse input for the furnace screen.
	 */
	private void unregisterInput(InputManager inputManager)
	{
		inputManager.removeListener(this);
	}
	
	// ========================================================
	// Update.
	// ========================================================
	
	/**
	 * Updates the furnace screen each frame (slot icons, progress indicators, cursor).
	 * @param tpf time per frame
	 */
	public void update(float tpf)
	{
		if (!_open || _furnace == null)
		{
			return;
		}
		
		final SimpleCraft app = SimpleCraft.getInstance();
		
		// Update furnace slot icons.
		updateSlotIcon(_inputIcon, _inputIconMat, _inputCountLabel, _furnace.getInputSlot(), _lastInputTemplate, app);
		_lastInputTemplate = _furnace.getInputSlot() != null ? _furnace.getInputSlot().getTemplate() : null;
		
		updateSlotIcon(_fuelIcon, _fuelIconMat, _fuelCountLabel, _furnace.getFuelSlot(), _lastFuelTemplate, app);
		_lastFuelTemplate = _furnace.getFuelSlot() != null ? _furnace.getFuelSlot().getTemplate() : null;
		
		updateSlotIcon(_outputIcon, _outputIconMat, _outputCountLabel, _furnace.getOutputSlot(), _lastOutputTemplate, app);
		_lastOutputTemplate = _furnace.getOutputSlot() != null ? _furnace.getOutputSlot().getTemplate() : null;
		
		// Update player inventory slot icons.
		final Inventory inv = _playerController.getInventory();
		for (int i = 0; i < Inventory.TOTAL_SLOTS; i++)
		{
			final ItemInstance item = inv.getSlot(i);
			updateSlotIcon(_invIcons[i], _invIconMats[i], _invCountLabels[i], item, _lastInvTemplates[i], app);
			_lastInvTemplates[i] = item != null ? item.getTemplate() : null;
		}
		
		// Update cursor icon.
		updateSlotIcon(_cursorIcon, _cursorIconMat, _cursorCountLabel, _cursorItem, _lastCursorTemplate, app);
		_lastCursorTemplate = _cursorItem != null ? _cursorItem.getTemplate() : null;
		if (_cursorItem != null && !_cursorItem.isEmpty())
		{
			final Vector2f cursor = app.getInputManager().getCursorPosition();
			_cursorIcon.setLocalTranslation(cursor.x + 8, cursor.y - ICON_SIZE + 8, Z_CURSOR);
			_cursorCountLabel.setLocalTranslation(cursor.x + 8, cursor.y - ICON_SIZE + 20, Z_CURSOR + 1);
		}
		
		// Update arrow fill (smelt progress).
		if (_arrowFill != null)
		{
			final float progress = _furnace.getSmeltProgressFraction();
			_arrowFill.setLocalScale(progress, 1, 1);
		}
		
		// Update flame fill (fuel remaining).
		if (_flameFill != null)
		{
			final float fuel = _furnace.getFuelRemainingFraction();
			_flameFill.setLocalScale(1, fuel, 1);
		}
	}
	
	/**
	 * Updates a slot's icon geometry and count label based on the current item.
	 * @param icon the icon geometry
	 * @param iconMat the icon material
	 * @param countLabel the count text
	 * @param item the current item in the slot (or null)
	 * @param lastTemplate the previously cached template (for change detection)
	 * @param app the application instance
	 */
	private void updateSlotIcon(Geometry icon, Material iconMat, BitmapText countLabel, ItemInstance item, ItemTemplate lastTemplate, SimpleCraft app)
	{
		if (item == null || item.isEmpty())
		{
			icon.setCullHint(Geometry.CullHint.Always);
			countLabel.setText("");
			return;
		}
		
		// Update texture if the item type changed.
		final ItemTemplate template = item.getTemplate();
		if (template != lastTemplate)
		{
			final Texture texture = ItemTextureResolver.resolve(app.getAssetManager(), template);
			if (texture != null)
			{
				iconMat.setTexture("ColorMap", texture);
				iconMat.setColor("Color", ColorRGBA.White);
			}
			else
			{
				iconMat.clearParam("ColorMap");
				iconMat.setColor("Color", new ColorRGBA(0.5f, 0.5f, 0.5f, 0.8f));
			}
		}
		
		icon.setCullHint(Geometry.CullHint.Never);
		
		// Update count label (only show count if > 1).
		if (item.getCount() > 1)
		{
			countLabel.setText(String.valueOf(item.getCount()));
		}
		else
		{
			countLabel.setText("");
		}
	}
	
	// ========================================================
	// UI Building.
	// ========================================================
	
	/**
	 * Builds the complete furnace UI.
	 */
	private void buildUI(SimpleCraft app)
	{
		final int screenW = app.getCamera().getWidth();
		final int screenH = app.getCamera().getHeight();
		
		_rootNode = new Node("FurnaceScreen");
		
		final BitmapFont font = FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_LINOCUT_PATH, Font.PLAIN, FONT_SIZE);
		final BitmapFont titleFont = FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_LINOCUT_PATH, Font.PLAIN, TITLE_FONT_SIZE);
		
		// Dark overlay.
		final Geometry overlay = createQuad("FurnaceOverlay", screenW, screenH, new ColorRGBA(0, 0, 0, OVERLAY_ALPHA));
		overlay.setLocalTranslation(0, 0, Z_OVERLAY);
		_rootNode.attachChild(overlay);
		
		// Layout calculations - furnace slots use doubled sizes.
		final float furnacePanelW = FURNACE_SLOT_SIZE * 3 + SLOT_GAP * 2;
		final float furnacePanelX = (screenW - furnacePanelW) / 2;
		final float furnaceY = screenH * 0.6f;
		
		// Title.
		final BitmapText titleText = new BitmapText(titleFont);
		titleText.setSize(titleFont.getCharSet().getRenderedSize());
		titleText.setText("Furnace");
		titleText.setColor(ColorRGBA.White);
		titleText.setLocalTranslation(furnacePanelX + furnacePanelW / 2 - titleText.getLineWidth() / 2, furnaceY + FURNACE_SLOT_SIZE + 40, Z_TITLE);
		_rootNode.attachChild(titleText);
		
		// Input slot (top-left of furnace area, doubled size).
		_inputSlotX = furnacePanelX;
		_inputSlotY = furnaceY;
		createFurnaceSlotBg("InputSlotBg", _inputSlotX, _inputSlotY);
		_inputIconMat = createIconMaterial(app);
		_inputIcon = createFurnaceIconGeometry("InputIcon", _inputIconMat, _inputSlotX + FURNACE_ICON_PAD, _inputSlotY + FURNACE_ICON_PAD);
		_inputCountLabel = createFurnaceCountLabel(font, _inputSlotX, _inputSlotY);
		
		// Fuel slot (below input, doubled size).
		_fuelSlotX = furnacePanelX;
		_fuelSlotY = furnaceY - FURNACE_SLOT_SIZE - SLOT_GAP * 2;
		createFurnaceSlotBg("FuelSlotBg", _fuelSlotX, _fuelSlotY);
		_fuelIconMat = createIconMaterial(app);
		_fuelIcon = createFurnaceIconGeometry("FuelIcon", _fuelIconMat, _fuelSlotX + FURNACE_ICON_PAD, _fuelSlotY + FURNACE_ICON_PAD);
		_fuelCountLabel = createFurnaceCountLabel(font, _fuelSlotX, _fuelSlotY);
		
		// Output slot (right side, vertically centered between input and fuel, doubled size).
		_outputSlotX = furnacePanelX + FURNACE_SLOT_SIZE * 2 + SLOT_GAP * 2;
		_outputSlotY = furnaceY - (FURNACE_SLOT_SIZE + SLOT_GAP * 2) / 2;
		createFurnaceSlotBg("OutputSlotBg", _outputSlotX, _outputSlotY);
		_outputIconMat = createIconMaterial(app);
		_outputIcon = createFurnaceIconGeometry("OutputIcon", _outputIconMat, _outputSlotX + FURNACE_ICON_PAD, _outputSlotY + FURNACE_ICON_PAD);
		_outputCountLabel = createFurnaceCountLabel(font, _outputSlotX, _outputSlotY);
		
		// Arrow indicator (between input/output, scaled for doubled slots).
		final float arrowX = furnacePanelX + FURNACE_SLOT_SIZE + SLOT_GAP;
		final float arrowY = _outputSlotY + FURNACE_SLOT_SIZE / 2 - 12;
		final Geometry arrowBg = createQuad("ArrowBg", FURNACE_SLOT_SIZE, 24, new ColorRGBA(0.3f, 0.3f, 0.3f, 0.6f));
		arrowBg.setLocalTranslation(arrowX, arrowY, Z_PROGRESS);
		_rootNode.attachChild(arrowBg);
		_arrowFill = createQuad("ArrowFill", FURNACE_SLOT_SIZE, 24, new ColorRGBA(0.2f, 0.8f, 0.2f, 0.8f));
		_arrowFill.setLocalTranslation(arrowX, arrowY, Z_PROGRESS_FILL);
		_rootNode.attachChild(_arrowFill);
		
		// Flame indicator (between input and fuel, scaled for doubled slots).
		final float flameX = _inputSlotX + FURNACE_SLOT_SIZE / 2 - 12;
		final float flameY = furnaceY - SLOT_GAP - 2;
		final float flameHeight = SLOT_GAP * 2;
		final Geometry flameBg = createQuad("FlameBg", 24, flameHeight, new ColorRGBA(0.3f, 0.2f, 0.1f, 0.6f));
		flameBg.setLocalTranslation(flameX, flameY - flameHeight, Z_PROGRESS);
		_rootNode.attachChild(flameBg);
		_flameFill = createQuad("FlameFill", 24, flameHeight, new ColorRGBA(1.0f, 0.5f, 0.0f, 0.9f));
		_flameFill.setLocalTranslation(flameX, flameY - flameHeight, Z_PROGRESS_FILL);
		_rootNode.attachChild(_flameFill);
		
		// Slot indicator labels (positioned above each furnace slot).
		createFieldLabel(font, "Input", _inputSlotX, _inputSlotY + FURNACE_SLOT_SIZE + 16);
		createFieldLabel(font, "Fuel", _fuelSlotX, _fuelSlotY + FURNACE_SLOT_SIZE + 16);
		createFieldLabel(font, "Output", _outputSlotX, _outputSlotY + FURNACE_SLOT_SIZE + 16);
		
		// ========================================================
		// Recipe Info Panel (left edge of screen).
		// ========================================================
		buildRecipePanel(app, font, furnaceY);
		
		// Player inventory grid.
		final float invStartX = (screenW - (INV_COLS * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP)) / 2;
		
		// Compute the full width of the inventory grid.
		final float gridWidth = INV_COLS * SLOT_SIZE + (INV_COLS - 1) * SLOT_GAP;
		final float gridCenterX = invStartX + gridWidth / 2;
		
		// Layout: Hotbar (action bar) ABOVE main inventory.
		final float mainInvY = screenH * 0.08f; // Main inventory at bottom (slots 9-35).
		final float hotbarY = mainInvY + (SLOT_SIZE + SLOT_GAP) * 3 + SLOT_GAP * 2; // Hotbar above main inventory.
		
		// Hotbar (slots 0-8) - now at the top of the inventory grid.
		for (int i = 0; i < Inventory.HOTBAR_SLOTS; i++)
		{
			_invSlotX[i] = invStartX + i * (SLOT_SIZE + SLOT_GAP);
			_invSlotY[i] = hotbarY;
			createSlotBg("InvSlot" + i, _invSlotX[i], _invSlotY[i]);
			_invIconMats[i] = createIconMaterial(app);
			_invIcons[i] = createIconGeometry("InvIcon" + i, _invIconMats[i], _invSlotX[i] + ICON_PAD, _invSlotY[i] + ICON_PAD);
			_invCountLabels[i] = createCountLabel(font, _invSlotX[i], _invSlotY[i]);
		}
		
		// Main inventory (slots 9-35) - now below the hotbar.
		for (int i = Inventory.HOTBAR_SLOTS; i < Inventory.TOTAL_SLOTS; i++)
		{
			final int row = (i - Inventory.HOTBAR_SLOTS) / INV_COLS;
			final int col = (i - Inventory.HOTBAR_SLOTS) % INV_COLS;
			_invSlotX[i] = invStartX + col * (SLOT_SIZE + SLOT_GAP);
			_invSlotY[i] = mainInvY + (INV_ROWS - 1 - row) * (SLOT_SIZE + SLOT_GAP);
			createSlotBg("InvSlot" + i, _invSlotX[i], _invSlotY[i]);
			_invIconMats[i] = createIconMaterial(app);
			_invIcons[i] = createIconGeometry("InvIcon" + i, _invIconMats[i], _invSlotX[i] + ICON_PAD, _invSlotY[i] + ICON_PAD);
			_invCountLabels[i] = createCountLabel(font, _invSlotX[i], _invSlotY[i]);
		}
		
		// ========================================================
		// Action Bar and Inventory titles (like InventoryScreen)
		// ========================================================
		
		// ----- "Action Bar" title (centered above hotbar) -----
		final float actionBarY = _invSlotY[0] + SLOT_SIZE + 30; // Padding above hotbar
		
		BitmapText actionBarText = new BitmapText(titleFont);
		actionBarText.setText("Action Bar");
		actionBarText.setColor(ColorRGBA.White);
		float actionBarWidth = actionBarText.getLineWidth();
		float actionBarX = gridCenterX - actionBarWidth / 2f;
		actionBarText.setLocalTranslation(actionBarX, actionBarY, Z_TITLE);
		_rootNode.attachChild(actionBarText);
		
		// Shadow for Action Bar.
		BitmapText actionBarShadow = new BitmapText(titleFont);
		actionBarShadow.setText("Action Bar");
		actionBarShadow.setColor(new ColorRGBA(0, 0, 0, 0.8f));
		actionBarShadow.setLocalTranslation(actionBarX + 1, actionBarY - 1, Z_TITLE - 0.1f);
		_rootNode.attachChild(actionBarShadow);
		
		// ----- "Inventory" title (centered above main inventory) -----
		final float invLabelY = _invSlotY[9] + SLOT_SIZE + 30; // Above top main inventory row
		
		BitmapText invText = new BitmapText(titleFont);
		invText.setText("Inventory");
		invText.setColor(ColorRGBA.White);
		float invWidth = invText.getLineWidth();
		float invX = gridCenterX - invWidth / 2f;
		invText.setLocalTranslation(invX, invLabelY, Z_TITLE);
		_rootNode.attachChild(invText);
		
		// Shadow for Inventory.
		BitmapText invShadow = new BitmapText(titleFont);
		invShadow.setText("Inventory");
		invShadow.setColor(new ColorRGBA(0, 0, 0, 0.8f));
		invShadow.setLocalTranslation(invX + 1, invLabelY - 1, Z_TITLE - 0.1f);
		_rootNode.attachChild(invShadow);
		
		// Cursor icon (follows mouse, hidden initially).
		_cursorIconMat = createIconMaterial(app);
		_cursorIcon = createIconGeometry("CursorIcon", _cursorIconMat, 0, 0);
		_cursorCountLabel = new BitmapText(font);
		_cursorCountLabel.setSize(font.getCharSet().getRenderedSize());
		_cursorCountLabel.setColor(ColorRGBA.White);
		_cursorCountLabel.setLocalTranslation(0, 0, Z_CURSOR + 1);
		_rootNode.attachChild(_cursorCountLabel);
	}
	
	// ========================================================
	// UI Helpers.
	// ========================================================
	
	/**
	 * Creates a slot background quad and attaches it to the root node.
	 */
	private void createSlotBg(String name, float x, float y)
	{
		final Geometry bg = createQuad(name, SLOT_SIZE, SLOT_SIZE, new ColorRGBA(0.2f, 0.2f, 0.2f, SLOT_BG_ALPHA));
		bg.setLocalTranslation(x, y, Z_SLOT_BG);
		_rootNode.attachChild(bg);
	}
	
	/**
	 * Creates a material for an item icon (Unshaded, alpha-blended).
	 */
	private Material createIconMaterial(SimpleCraft app)
	{
		final Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
		mat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
		mat.setColor("Color", ColorRGBA.White);
		return mat;
	}
	
	/**
	 * Creates an icon geometry (textured quad), initially hidden.
	 */
	private Geometry createIconGeometry(String name, Material mat, float x, float y)
	{
		final Quad quad = new Quad(ICON_SIZE, ICON_SIZE);
		final Geometry geom = new Geometry(name, quad);
		geom.setMaterial(mat);
		geom.setQueueBucket(Bucket.Gui);
		geom.setLocalTranslation(x, y, Z_ICON);
		geom.setCullHint(Geometry.CullHint.Always); // Hidden initially.
		_rootNode.attachChild(geom);
		return geom;
	}
	
	/**
	 * Creates a count label positioned at the bottom-right of a slot.
	 */
	private BitmapText createCountLabel(BitmapFont font, float slotX, float slotY)
	{
		final BitmapText label = new BitmapText(font);
		label.setSize(font.getCharSet().getRenderedSize());
		label.setColor(ColorRGBA.White);
		label.setLocalTranslation(slotX + SLOT_SIZE - 20, slotY + 16, Z_LABEL);
		_rootNode.attachChild(label);
		return label;
	}
	
	/**
	 * Creates a small field label (e.g. "Input", "Fuel", "Output").
	 */
	private void createFieldLabel(BitmapFont font, String text, float x, float y)
	{
		final BitmapText label = new BitmapText(font);
		label.setSize(font.getCharSet().getRenderedSize());
		label.setText(text);
		label.setColor(new ColorRGBA(0.7f, 0.7f, 0.7f, 1.0f));
		label.setLocalTranslation(x, y, Z_TITLE);
		_rootNode.attachChild(label);
	}
	
	/**
	 * Creates a colored quad geometry.
	 */
	private Geometry createQuad(String name, float w, float h, ColorRGBA color)
	{
		final Quad quad = new Quad(w, h);
		final Geometry geom = new Geometry(name, quad);
		final Material mat = new Material(SimpleCraft.getInstance().getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
		mat.setColor("Color", color);
		mat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
		geom.setMaterial(mat);
		geom.setQueueBucket(Bucket.Gui);
		return geom;
	}
	
	/**
	 * Creates a furnace-sized slot background quad (doubled size) and attaches it to the root node.
	 */
	private void createFurnaceSlotBg(String name, float x, float y)
	{
		final Geometry bg = createQuad(name, FURNACE_SLOT_SIZE, FURNACE_SLOT_SIZE, new ColorRGBA(0.2f, 0.2f, 0.2f, SLOT_BG_ALPHA));
		bg.setLocalTranslation(x, y, Z_SLOT_BG);
		_rootNode.attachChild(bg);
	}
	
	/**
	 * Creates a furnace-sized icon geometry (doubled size), initially hidden.
	 */
	private Geometry createFurnaceIconGeometry(String name, Material mat, float x, float y)
	{
		final Quad quad = new Quad(FURNACE_ICON_SIZE, FURNACE_ICON_SIZE);
		final Geometry geom = new Geometry(name, quad);
		geom.setMaterial(mat);
		geom.setQueueBucket(Bucket.Gui);
		geom.setLocalTranslation(x, y, Z_ICON);
		geom.setCullHint(Geometry.CullHint.Always);
		_rootNode.attachChild(geom);
		return geom;
	}
	
	/**
	 * Creates a count label positioned at the bottom-right of a furnace-sized slot.
	 */
	private BitmapText createFurnaceCountLabel(BitmapFont font, float slotX, float slotY)
	{
		final BitmapText label = new BitmapText(font);
		label.setSize(font.getCharSet().getRenderedSize());
		label.setColor(ColorRGBA.White);
		label.setLocalTranslation(slotX + FURNACE_SLOT_SIZE - 24, slotY + 18, Z_LABEL);
		_rootNode.attachChild(label);
		return label;
	}
	
	/**
	 * Returns true if the mouse position is inside a furnace-sized slot.
	 */
	private boolean isInsideFurnaceSlot(float mx, float my, float slotX, float slotY)
	{
		return mx >= slotX && mx <= slotX + FURNACE_SLOT_SIZE && my >= slotY && my <= slotY + FURNACE_SLOT_SIZE;
	}
	
	/**
	 * Builds the smelting recipe and fuel info panel pinned to the left edge of the screen.<br>
	 * Each recipe row shows: [input icon] input name -> [output icon] output name (Xs).<br>
	 * Below the recipes, a fuel section shows: [fuel icon] fuel name (Xs burn).<br>
	 * Text labels ensure readability even when item textures are missing.
	 */
	private void buildRecipePanel(SimpleCraft app, BitmapFont font, float furnaceY)
	{
		// Panel pinned to left edge of screen.
		final float panelX = RECIPE_PANEL_MARGIN;
		final float iconSlotSize = RECIPE_ICON_SIZE + 4;
		float currentY = furnaceY + FURNACE_SLOT_SIZE;
		
		// ========================================================
		// Recipes Section.
		// ========================================================
		final Map<ItemTemplate, SmeltingRegistry.SmeltResult> recipes = SmeltingRegistry.getRecipeMap();
		if (recipes != null && !recipes.isEmpty())
		{
			// "Recipes" title.
			final BitmapText recipeTitleText = new BitmapText(font);
			recipeTitleText.setSize(font.getCharSet().getRenderedSize());
			recipeTitleText.setText("Recipes");
			recipeTitleText.setColor(new ColorRGBA(0.9f, 0.7f, 0.3f, 1.0f));
			recipeTitleText.setLocalTranslation(panelX, currentY + 12, Z_TITLE);
			_rootNode.attachChild(recipeTitleText);
			currentY -= 8; // Gap below title before first row.
			
			// Recipe rows.
			int rowIndex = 0;
			for (Entry<ItemTemplate, SmeltingRegistry.SmeltResult> entry : recipes.entrySet())
			{
				final ItemTemplate input = entry.getKey();
				final ItemTemplate output = entry.getValue().getOutput();
				final float smeltTime = entry.getValue().getSmeltTime();
				currentY -= RECIPE_ROW_HEIGHT;
				float cursorX = panelX;
				
				// Input icon background.
				final Geometry inputBg = createQuad("RecipeInputBg" + rowIndex, iconSlotSize, iconSlotSize, new ColorRGBA(0.4f, 0.4f, 0.4f, 1.0f));
				inputBg.setLocalTranslation(cursorX, currentY, Z_SLOT_BG);
				_rootNode.attachChild(inputBg);
				
				// Input icon.
				final Texture inputTex = ItemTextureResolver.resolve(app.getAssetManager(), input);
				if (inputTex != null)
				{
					final Material inputMat = createIconMaterial(app);
					inputMat.setTexture("ColorMap", inputTex);
					final Quad inputQuad = new Quad(RECIPE_ICON_SIZE, RECIPE_ICON_SIZE);
					final Geometry inputGeom = new Geometry("RecipeInputIcon" + rowIndex, inputQuad);
					inputGeom.setMaterial(inputMat);
					inputGeom.setQueueBucket(Bucket.Gui);
					inputGeom.setLocalTranslation(cursorX + 2, currentY + 2, Z_ICON);
					_rootNode.attachChild(inputGeom);
				}
				
				cursorX += iconSlotSize + 4;
				
				// Input name text.
				final BitmapText inputLabel = new BitmapText(font);
				inputLabel.setSize(font.getCharSet().getRenderedSize());
				inputLabel.setText(input.getDisplayName());
				inputLabel.setColor(new ColorRGBA(0.8f, 0.8f, 0.8f, 1.0f));
				inputLabel.setLocalTranslation(cursorX, currentY + iconSlotSize / 2 + 6, Z_LABEL);
				_rootNode.attachChild(inputLabel);
				cursorX += inputLabel.getLineWidth() + 8;
				
				// Arrow ">".
				final BitmapText arrowLabel = new BitmapText(font);
				arrowLabel.setSize(font.getCharSet().getRenderedSize());
				arrowLabel.setText(">");
				arrowLabel.setColor(new ColorRGBA(0.6f, 0.6f, 0.6f, 1.0f));
				arrowLabel.setLocalTranslation(cursorX, currentY + iconSlotSize / 2 + 6, Z_LABEL);
				_rootNode.attachChild(arrowLabel);
				cursorX += arrowLabel.getLineWidth() + 8;
				
				// Output icon background.
				final Geometry outputBg = createQuad("RecipeOutputBg" + rowIndex, iconSlotSize, iconSlotSize, new ColorRGBA(0.4f, 0.4f, 0.4f, 1.0f));
				outputBg.setLocalTranslation(cursorX, currentY, Z_SLOT_BG);
				_rootNode.attachChild(outputBg);
				
				// Output icon.
				final Texture outputTex = ItemTextureResolver.resolve(app.getAssetManager(), output);
				if (outputTex != null)
				{
					final Material outputMat = createIconMaterial(app);
					outputMat.setTexture("ColorMap", outputTex);
					final Quad outputQuad = new Quad(RECIPE_ICON_SIZE, RECIPE_ICON_SIZE);
					final Geometry outputGeom = new Geometry("RecipeOutputIcon" + rowIndex, outputQuad);
					outputGeom.setMaterial(outputMat);
					outputGeom.setQueueBucket(Bucket.Gui);
					outputGeom.setLocalTranslation(cursorX + 2, currentY + 2, Z_ICON);
					_rootNode.attachChild(outputGeom);
				}
				
				cursorX += iconSlotSize + 4;
				
				// Output name text.
				final BitmapText outputLabel = new BitmapText(font);
				outputLabel.setSize(font.getCharSet().getRenderedSize());
				outputLabel.setText(output.getDisplayName());
				outputLabel.setColor(new ColorRGBA(0.7f, 1.0f, 0.7f, 1.0f));
				outputLabel.setLocalTranslation(cursorX, currentY + iconSlotSize / 2 + 6, Z_LABEL);
				_rootNode.attachChild(outputLabel);
				cursorX += outputLabel.getLineWidth() + 8;
				
				// Smelt duration text.
				final String timeStr = "(" + formatSeconds(smeltTime) + ")";
				final BitmapText timeLabel = new BitmapText(font);
				timeLabel.setSize(font.getCharSet().getRenderedSize());
				timeLabel.setText(timeStr);
				timeLabel.setColor(new ColorRGBA(0.5f, 0.5f, 0.5f, 1.0f));
				timeLabel.setLocalTranslation(cursorX, currentY + iconSlotSize / 2 + 6, Z_LABEL);
				_rootNode.attachChild(timeLabel);
				
				rowIndex++;
			}
		}
		
		// ========================================================
		// Fuels Section.
		// ========================================================
		final Map<ItemTemplate, Float> fuels = SmeltingRegistry.getFuelMap();
		if (fuels != null && !fuels.isEmpty())
		{
			// Gap between sections.
			currentY -= RECIPE_ROW_HEIGHT / 2;
			
			// "Fuels" title.
			final BitmapText fuelTitleText = new BitmapText(font);
			fuelTitleText.setSize(font.getCharSet().getRenderedSize());
			fuelTitleText.setText("Fuels");
			fuelTitleText.setColor(new ColorRGBA(0.9f, 0.5f, 0.2f, 1.0f));
			fuelTitleText.setLocalTranslation(panelX, currentY, Z_TITLE);
			_rootNode.attachChild(fuelTitleText);
			currentY -= RECIPE_ROW_HEIGHT / 2;
			
			// Fuel rows.
			int fuelIndex = 0;
			for (Entry<ItemTemplate, Float> entry : fuels.entrySet())
			{
				final ItemTemplate fuel = entry.getKey();
				final float burnTime = entry.getValue();
				currentY -= RECIPE_ROW_HEIGHT;
				float cursorX = panelX;
				
				// Fuel icon background.
				final Geometry fuelBg = createQuad("FuelInfoBg" + fuelIndex, iconSlotSize, iconSlotSize, new ColorRGBA(0.4f, 0.4f, 0.4f, 1.0f));
				fuelBg.setLocalTranslation(cursorX, currentY, Z_SLOT_BG);
				_rootNode.attachChild(fuelBg);
				
				// Fuel icon.
				final Texture fuelTex = ItemTextureResolver.resolve(app.getAssetManager(), fuel);
				if (fuelTex != null)
				{
					final Material fuelMat = createIconMaterial(app);
					fuelMat.setTexture("ColorMap", fuelTex);
					final Quad fuelQuad = new Quad(RECIPE_ICON_SIZE, RECIPE_ICON_SIZE);
					final Geometry fuelGeom = new Geometry("FuelInfoIcon" + fuelIndex, fuelQuad);
					fuelGeom.setMaterial(fuelMat);
					fuelGeom.setQueueBucket(Bucket.Gui);
					fuelGeom.setLocalTranslation(cursorX + 2, currentY + 2, Z_ICON);
					_rootNode.attachChild(fuelGeom);
				}
				
				cursorX += iconSlotSize + 4;
				
				// Fuel name text.
				final BitmapText fuelLabel = new BitmapText(font);
				fuelLabel.setSize(font.getCharSet().getRenderedSize());
				fuelLabel.setText(fuel.getDisplayName());
				fuelLabel.setColor(new ColorRGBA(0.8f, 0.8f, 0.8f, 1.0f));
				fuelLabel.setLocalTranslation(cursorX, currentY + iconSlotSize / 2 + 6, Z_LABEL);
				_rootNode.attachChild(fuelLabel);
				cursorX += fuelLabel.getLineWidth() + 8;
				
				// Burn duration text.
				final String burnStr = "(" + formatSeconds(burnTime) + " burn)";
				final BitmapText burnLabel = new BitmapText(font);
				burnLabel.setSize(font.getCharSet().getRenderedSize());
				burnLabel.setText(burnStr);
				burnLabel.setColor(new ColorRGBA(1.0f, 0.6f, 0.2f, 1.0f));
				burnLabel.setLocalTranslation(cursorX, currentY + iconSlotSize / 2 + 6, Z_LABEL);
				_rootNode.attachChild(burnLabel);
				
				fuelIndex++;
			}
		}
	}
	
	/**
	 * Formats a time in seconds to a human-readable string.<br>
	 * Shows whole seconds when exact (e.g. "10s"), one decimal otherwise (e.g. "5.5s").
	 */
	private String formatSeconds(float seconds)
	{
		if (seconds == (int) seconds)
		{
			return (int) seconds + "s";
		}
		
		return String.format("%.1fs", seconds);
	}
	
	// ========================================================
	// Click Handling.
	// ========================================================
	
	@Override
	public void onAction(String name, boolean isPressed, float tpf)
	{
		if (!isPressed || !_open || _furnace == null)
		{
			return;
		}
		
		if (!ACTION_FURNACE_CLICK.equals(name))
		{
			return;
		}
		
		final SimpleCraft app = SimpleCraft.getInstance();
		final Vector2f cursor = app.getInputManager().getCursorPosition();
		final float mx = cursor.x;
		final float my = cursor.y;
		
		// Check furnace input slot.
		if (isInsideFurnaceSlot(mx, my, _inputSlotX, _inputSlotY))
		{
			handleInputSlotClick(_shiftDown);
			return;
		}
		
		// Check furnace fuel slot.
		if (isInsideFurnaceSlot(mx, my, _fuelSlotX, _fuelSlotY))
		{
			handleFuelSlotClick(_shiftDown);
			return;
		}
		
		// Check furnace output slot.
		if (isInsideFurnaceSlot(mx, my, _outputSlotX, _outputSlotY))
		{
			handleOutputSlotClick(_shiftDown);
			return;
		}
		
		// Check inventory slots.
		for (int i = 0; i < Inventory.TOTAL_SLOTS; i++)
		{
			if (isInsideSlot(mx, my, _invSlotX[i], _invSlotY[i]))
			{
				handleInventorySlotClick(i, _shiftDown);
				return;
			}
		}
	}
	
	/**
	 * Returns true if the mouse position is inside the slot at the given position.
	 */
	private boolean isInsideSlot(float mx, float my, float slotX, float slotY)
	{
		return mx >= slotX && mx <= slotX + SLOT_SIZE && my >= slotY && my <= slotY + SLOT_SIZE;
	}
	
	/**
	 * Handles click on the input slot.<br>
	 * Only accepts items that have a valid smelting recipe.
	 */
	private void handleInputSlotClick(boolean shiftDown)
	{
		final ItemInstance slotItem = _furnace.getInputSlot();
		
		if (shiftDown)
		{
			// Shift-click splitting
			if (_cursorItem == null || _cursorItem.isEmpty())
			{
				// Pick up half from input slot
				if (slotItem != null && !slotItem.isEmpty())
				{
					int total = slotItem.getCount();
					int take = (total + 1) / 2;
					int leave = total - take;
					if (leave > 0)
					{
						slotItem.setCount(leave);
						_cursorItem = new ItemInstance(slotItem.getTemplate(), take);
					}
					else
					{
						_cursorItem = slotItem;
						_furnace.setInputSlot(null);
					}
				}
			}
			else
			{
				// Place half into empty input slot
				if (slotItem == null || slotItem.isEmpty())
				{
					// Check if cursor item is smeltable
					if (!SmeltingRegistry.isSmeltable(_cursorItem.getTemplate().getId()))
					{
						System.out.println("FurnaceScreen: Item '" + _cursorItem.getTemplate().getDisplayName() + "' is not smeltable.");
						return;
					}
					
					int total = _cursorItem.getCount();
					int put = (total + 1) / 2;
					int keep = total - put;
					if (keep > 0)
					{
						_cursorItem.setCount(keep);
						_furnace.setInputSlot(new ItemInstance(_cursorItem.getTemplate(), put));
					}
					else
					{
						_furnace.setInputSlot(_cursorItem);
						_cursorItem = null;
					}
				}
				
				// If slot is not empty, do nothing for shift
			}
			return;
		}
		
		// Original non-shift logic
		if (_cursorItem == null || _cursorItem.isEmpty())
		{
			// Pick up from slot.
			if (slotItem != null && !slotItem.isEmpty())
			{
				_cursorItem = slotItem;
				_furnace.setInputSlot(null);
			}
		}
		else
		{
			// Validate: only smeltable items can go in the input slot.
			if (!SmeltingRegistry.isSmeltable(_cursorItem.getTemplate().getId()))
			{
				System.out.println("FurnaceScreen: Item '" + _cursorItem.getTemplate().getDisplayName() + "' is not smeltable.");
				return;
			}
			
			if (slotItem == null || slotItem.isEmpty())
			{
				// Place cursor item into empty slot.
				_furnace.setInputSlot(_cursorItem);
				_cursorItem = null;
			}
			else if (slotItem.canStackWith(_cursorItem))
			{
				// Merge stacks.
				final int overflow = slotItem.add(_cursorItem.getCount());
				if (overflow <= 0)
				{
					_cursorItem = null;
				}
				else
				{
					_cursorItem.remove(_cursorItem.getCount() - overflow);
				}
			}
			else
			{
				// Swap.
				_furnace.setInputSlot(_cursorItem);
				_cursorItem = slotItem;
			}
		}
	}
	
	/**
	 * Handles click on the fuel slot.<br>
	 * Only accepts items that have a positive burn time.
	 */
	private void handleFuelSlotClick(boolean shiftDown)
	{
		final ItemInstance slotItem = _furnace.getFuelSlot();
		
		if (shiftDown)
		{
			// Shift-click splitting
			if (_cursorItem == null || _cursorItem.isEmpty())
			{
				// Pick up half from fuel slot
				if (slotItem != null && !slotItem.isEmpty())
				{
					int total = slotItem.getCount();
					int take = (total + 1) / 2;
					int leave = total - take;
					if (leave > 0)
					{
						slotItem.setCount(leave);
						_cursorItem = new ItemInstance(slotItem.getTemplate(), take);
					}
					else
					{
						_cursorItem = slotItem;
						_furnace.setFuelSlot(null);
					}
				}
			}
			else
			{
				// Place half into empty fuel slot
				if (slotItem == null || slotItem.isEmpty())
				{
					// Check if cursor item is fuel
					if (!SmeltingRegistry.isFuel(_cursorItem.getTemplate().getId()))
					{
						System.out.println("FurnaceScreen: Item '" + _cursorItem.getTemplate().getDisplayName() + "' is not a valid fuel.");
						return;
					}
					
					int total = _cursorItem.getCount();
					int put = (total + 1) / 2;
					int keep = total - put;
					if (keep > 0)
					{
						_cursorItem.setCount(keep);
						_furnace.setFuelSlot(new ItemInstance(_cursorItem.getTemplate(), put));
					}
					else
					{
						_furnace.setFuelSlot(_cursorItem);
						_cursorItem = null;
					}
				}
			}
			return;
		}
		
		// Original non-shift logic
		if (_cursorItem == null || _cursorItem.isEmpty())
		{
			// Pick up from slot.
			if (slotItem != null && !slotItem.isEmpty())
			{
				_cursorItem = slotItem;
				_furnace.setFuelSlot(null);
			}
		}
		else
		{
			// Validate: only fuel items can go in the fuel slot.
			if (!SmeltingRegistry.isFuel(_cursorItem.getTemplate().getId()))
			{
				System.out.println("FurnaceScreen: Item '" + _cursorItem.getTemplate().getDisplayName() + "' is not a valid fuel.");
				return;
			}
			
			if (slotItem == null || slotItem.isEmpty())
			{
				// Place cursor item into empty slot.
				_furnace.setFuelSlot(_cursorItem);
				_cursorItem = null;
			}
			else if (slotItem.canStackWith(_cursorItem))
			{
				// Merge stacks.
				final int overflow = slotItem.add(_cursorItem.getCount());
				if (overflow <= 0)
				{
					_cursorItem = null;
				}
				else
				{
					_cursorItem.remove(_cursorItem.getCount() - overflow);
				}
			}
			else
			{
				// Swap.
				_furnace.setFuelSlot(_cursorItem);
				_cursorItem = slotItem;
			}
		}
	}
	
	/**
	 * Handles click on the output slot.<br>
	 * Can only take items, not place them.
	 */
	private void handleOutputSlotClick(boolean shiftDown)
	{
		final ItemInstance slotItem = _furnace.getOutputSlot();
		
		if (slotItem == null || slotItem.isEmpty())
		{
			return;
		}
		
		if (shiftDown)
		{
			// Shift-click: take half from output
			if (_cursorItem == null || _cursorItem.isEmpty())
			{
				int total = slotItem.getCount();
				int take = (total + 1) / 2;
				int leave = total - take;
				if (leave > 0)
				{
					slotItem.setCount(leave);
					_cursorItem = new ItemInstance(slotItem.getTemplate(), take);
				}
				else
				{
					_cursorItem = slotItem;
					_furnace.setOutputSlot(null);
				}
			}
			
			// If cursor has item, do nothing (can't place into output)
			return;
		}
		
		// Original non-shift logic
		if (_cursorItem == null || _cursorItem.isEmpty())
		{
			// Take entire output stack.
			_cursorItem = slotItem;
			_furnace.setOutputSlot(null);
		}
		else if (_cursorItem.canStackWith(slotItem))
		{
			// Merge output into cursor.
			final int overflow = _cursorItem.add(slotItem.getCount());
			if (overflow <= 0)
			{
				_furnace.setOutputSlot(null);
			}
			else
			{
				slotItem.remove(slotItem.getCount() - overflow);
			}
		}
		
		// If cursor holds a different item, do nothing (can't place into output).
	}
	
	/**
	 * Handles click on a player inventory slot.<br>
	 * Picks up, places, or swaps items between cursor and inventory.
	 */
	private void handleInventorySlotClick(int slotIndex, boolean shiftDown)
	{
		final Inventory inv = _playerController.getInventory();
		final ItemInstance slotItem = inv.getSlot(slotIndex);
		
		if (shiftDown)
		{
			// Shift-click splitting
			if (_cursorItem == null || _cursorItem.isEmpty())
			{
				// Pick up half from inventory slot
				if (slotItem != null && !slotItem.isEmpty())
				{
					int total = slotItem.getCount();
					int take = (total + 1) / 2;
					int leave = total - take;
					if (leave > 0)
					{
						slotItem.setCount(leave);
						_cursorItem = new ItemInstance(slotItem.getTemplate(), take);
					}
					else
					{
						_cursorItem = slotItem;
						inv.setSlot(slotIndex, null);
					}
				}
			}
			else
			{
				// Place half into empty inventory slot
				if (slotItem == null || slotItem.isEmpty())
				{
					int total = _cursorItem.getCount();
					int put = (total + 1) / 2;
					int keep = total - put;
					if (keep > 0)
					{
						_cursorItem.setCount(keep);
						inv.setSlot(slotIndex, new ItemInstance(_cursorItem.getTemplate(), put));
					}
					else
					{
						inv.setSlot(slotIndex, _cursorItem);
						_cursorItem = null;
					}
				}
				
				// If slot is not empty, do nothing for shift
			}
			return;
		}
		
		// Original non-shift logic
		if (_cursorItem == null || _cursorItem.isEmpty())
		{
			// Pick up from inventory.
			if (slotItem != null && !slotItem.isEmpty())
			{
				_cursorItem = slotItem;
				inv.setSlot(slotIndex, null);
			}
		}
		else
		{
			if (slotItem == null || slotItem.isEmpty())
			{
				// Place cursor item into empty slot.
				inv.setSlot(slotIndex, _cursorItem);
				_cursorItem = null;
			}
			else if (slotItem.canStackWith(_cursorItem))
			{
				// Merge stacks.
				final int overflow = slotItem.add(_cursorItem.getCount());
				if (overflow <= 0)
				{
					_cursorItem = null;
				}
				else
				{
					_cursorItem.remove(_cursorItem.getCount() - overflow);
				}
			}
			else
			{
				// Swap.
				inv.setSlot(slotIndex, _cursorItem);
				_cursorItem = slotItem;
			}
		}
	}
	
	// ========================================================
	// Accessors.
	// ========================================================
	
	/**
	 * Sets the drop manager for spawning world drops.
	 */
	public void setDropManager(DropManager dropManager)
	{
		_dropManager = dropManager;
	}
	
	// ========================================================
	// Cleanup.
	// ========================================================
	
	/**
	 * Cleans up the furnace screen. Closes if open and removes all UI elements.
	 */
	public void cleanup()
	{
		if (_open)
		{
			close();
		}
		
		_rootNode = null;
	}
}
