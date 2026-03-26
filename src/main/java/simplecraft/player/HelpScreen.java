package simplecraft.player;

import java.awt.Font;

import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;

import simplecraft.SimpleCraft;
import simplecraft.input.GameInputManager;
import simplecraft.ui.FontManager;

/**
 * Help UI opened by pressing F1.<br>
 * Displays a numbered list of gameplay progression steps in a scrollable panel.<br>
 * While open, player movement and block interaction input are unregistered<br>
 * (same pattern as {@link CraftingScreen}). Close with Escape or F1.
 * @author Pantelis Andrianakis
 * @since March 26th 2026
 */
public class HelpScreen
{
	// ========================================================
	// Constants.
	// ========================================================
	
	/** Font size for step text. */
	private static final int FONT_SIZE = 18;
	
	/** Font size for the title. */
	private static final int TITLE_FONT_SIZE = 28;
	
	/** Vertical space per step row (pixels). */
	private static final float ROW_HEIGHT = 30;
	
	/** Padding inside the panel edges (pixels). */
	private static final float PANEL_PADDING = 24;
	
	/** Panel width as a fraction of screen width. */
	private static final float PANEL_WIDTH_FRACTION = 0.55f;
	
	/** Panel height as a fraction of screen height. */
	private static final float PANEL_HEIGHT_FRACTION = 0.90f;
	
	/** Background overlay alpha. */
	private static final float OVERLAY_ALPHA = 0.55f;
	
	/** Panel background alpha. */
	private static final float PANEL_ALPHA = 0.8f;
	
	// Z-depths matching InventoryScreen conventions.
	private static final float Z_OVERLAY = 10f;
	private static final float Z_PANEL = 11f;
	private static final float Z_TEXT = 12.5f;
	
	// Colors.
	private static final ColorRGBA COLOR_TITLE = new ColorRGBA(1.0f, 0.85f, 0.3f, 1.0f);
	private static final ColorRGBA COLOR_STEP_NUMBER = new ColorRGBA(0.4f, 0.9f, 0.4f, 1.0f);
	private static final ColorRGBA COLOR_STEP_TEXT = new ColorRGBA(0.9f, 0.9f, 0.9f, 1.0f);
	private static final ColorRGBA COLOR_HINT = new ColorRGBA(0.6f, 0.6f, 0.6f, 1.0f);
	private static final ColorRGBA COLOR_TEXT_SHADOW = new ColorRGBA(0.0f, 0.0f, 0.0f, 0.8f);
	private static final ColorRGBA COLOR_KEY_LABEL = new ColorRGBA(0.5f, 0.8f, 1.0f, 1.0f);
	private static final ColorRGBA COLOR_KEY_VALUE = new ColorRGBA(0.85f, 0.85f, 0.85f, 1.0f);
	
	/** Extra vertical gap before the Controls section (pixels). */
	private static final float SECTION_GAP = 32;
	
	/** Gameplay progression steps displayed in the help screen. */
	private static final String[] HELP_STEPS =
	{
		"Hit a tree to gather wood.",
		"Place the crafting table and create a shovel and a pickaxe.",
		"Dig to find stone.",
		"Create stone tools and a campfire.",
		"Set your spawn to the campfire.",
		"Create a furnace and use it to create coal.",
		"Use coal to create torches.",
		"Dig deeper to find iron.",
		"Create iron armor and weapon.",
		"Create a Dragon Orb and defeat the Dragon boss to gain gold bars.",
		"Create gold armor and weapon.",
		"Create a Shadow Orb and defeat the Shadow boss (final boss).",
	};
	
	/** Gameplay TIPS displayed in the help screen. */
	private static final String[] TIPS =
	{
		"You can use the Shift key to split stackable items.",
		"You can right-click on 4 Wood blocks to create a Crafting Table.",
	};
	
	// ========================================================
	// Fields.
	// ========================================================
	
	private final PlayerController _playerController;
	private final BlockInteraction _blockInteraction;
	
	/** Whether the help screen is currently open. */
	private boolean _open;
	
	/** Root node holding all help UI elements. Attached to guiNode when open. */
	private Node _rootNode;
	
	// ========================================================
	// Constructor.
	// ========================================================
	
	/**
	 * Creates a new help screen (initially hidden).
	 * @param playerController the player controller for input management
	 * @param blockInteraction the block interaction handler for input management
	 */
	public HelpScreen(PlayerController playerController, BlockInteraction blockInteraction)
	{
		_playerController = playerController;
		_blockInteraction = blockInteraction;
	}
	
	// ========================================================
	// Open / Close.
	// ========================================================
	
	/**
	 * Opens the help screen: builds the UI, disables player input, shows cursor.
	 */
	public void open()
	{
		if (_open)
		{
			return;
		}
		
		_open = true;
		
		final SimpleCraft app = SimpleCraft.getInstance();
		
		// Disable player movement and block interaction input (same pattern as CraftingScreen).
		_playerController.unregisterInput();
		_blockInteraction.unregisterInput();
		
		// Build UI.
		buildUI(app);
		
		// Attach to guiNode.
		app.getGuiNode().attachChild(_rootNode);
		
		// Show cursor.
		app.getInputManager().setCursorVisible(true);
	}
	
	/**
	 * Closes the help screen: detaches UI, restores player input, hides cursor.
	 */
	public void close()
	{
		if (!_open)
		{
			return;
		}
		
		_open = false;
		
		final SimpleCraft app = SimpleCraft.getInstance();
		
		// Detach UI.
		if (_rootNode != null)
		{
			app.getGuiNode().detachChild(_rootNode);
			_rootNode = null;
		}
		
		// Restore player movement and block interaction input.
		_playerController.registerInput();
		_blockInteraction.registerInput();
		
		// Hide cursor.
		app.getInputManager().setCursorVisible(false);
	}
	
	/**
	 * Returns true if the help screen is currently open.
	 */
	public boolean isOpen()
	{
		return _open;
	}
	
	// ========================================================
	// UI Construction.
	// ========================================================
	
	/**
	 * Builds all UI elements for the help screen.
	 */
	private void buildUI(SimpleCraft app)
	{
		_rootNode = new Node("HelpScreen");
		
		final Camera cam = app.getCamera();
		final float screenW = cam.getWidth();
		final float screenH = cam.getHeight();
		
		// Load fonts.
		final BitmapFont stepFont = FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_LINOCUT_PATH, Font.PLAIN, FONT_SIZE);
		final BitmapFont titleFont = FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_LINOCUT_PATH, Font.PLAIN, TITLE_FONT_SIZE);
		
		// ---- Full-screen dark overlay ----
		final Quad overlayQuad = new Quad(screenW, screenH);
		final Geometry overlay = new Geometry("HelpOverlay", overlayQuad);
		final Material overlayMat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
		overlayMat.setColor("Color", new ColorRGBA(0, 0, 0, OVERLAY_ALPHA));
		overlayMat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
		overlay.setMaterial(overlayMat);
		overlay.setQueueBucket(Bucket.Gui);
		overlay.setLocalTranslation(0, 0, Z_OVERLAY);
		_rootNode.attachChild(overlay);
		
		// ---- Panel background ----
		final float panelWidth = screenW * PANEL_WIDTH_FRACTION;
		final float panelHeight = screenH * PANEL_HEIGHT_FRACTION;
		final float panelX = (screenW - panelWidth) / 2;
		final float panelY = (screenH - panelHeight) / 2;
		
		final Quad panelQuad = new Quad(panelWidth, panelHeight);
		final Geometry panelBg = new Geometry("HelpPanel", panelQuad);
		final Material panelMat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
		panelMat.setColor("Color", new ColorRGBA(0.1f, 0.1f, 0.12f, PANEL_ALPHA));
		panelMat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
		panelBg.setMaterial(panelMat);
		panelBg.setQueueBucket(Bucket.Gui);
		panelBg.setLocalTranslation(panelX, panelY, Z_PANEL);
		_rootNode.attachChild(panelBg);
		
		// ---- Title ----
		final BitmapText titleShadow = new BitmapText(titleFont);
		titleShadow.setText("How to Play");
		titleShadow.setColor(COLOR_TEXT_SHADOW);
		titleShadow.setSize(titleFont.getCharSet().getRenderedSize());
		
		final BitmapText titleText = new BitmapText(titleFont);
		titleText.setText("How to Play");
		titleText.setColor(COLOR_TITLE);
		titleText.setSize(titleFont.getCharSet().getRenderedSize());
		
		final float titleX = panelX + (panelWidth - titleText.getLineWidth()) / 2;
		final float titleY = panelY + panelHeight - PANEL_PADDING;
		titleText.setLocalTranslation(titleX, titleY, Z_TEXT);
		titleShadow.setLocalTranslation(titleX + 1, titleY - 1, Z_TEXT - 0.1f);
		_rootNode.attachChild(titleShadow);
		_rootNode.attachChild(titleText);
		
		// ---- Close hint at bottom ----
		final BitmapText hintText = new BitmapText(stepFont);
		hintText.setText("Press Escape or F1 to close");
		hintText.setColor(COLOR_HINT);
		hintText.setSize(stepFont.getCharSet().getRenderedSize());
		final float hintX = panelX + (panelWidth - hintText.getLineWidth()) / 2;
		final float hintY = panelY + PANEL_PADDING + hintText.getLineHeight();
		hintText.setLocalTranslation(hintX, hintY, Z_TEXT);
		_rootNode.attachChild(hintText);
		
		// ---- Flowing Y cursor (top-down from title) ----
		float cursorY = titleY - titleText.getLineHeight() - PANEL_PADDING * 0.5f;
		
		// ---- Step rows ----
		final float leftX = panelX + PANEL_PADDING;
		
		// Measure the widest number string to right-align all numbers.
		final BitmapText widestNumber = new BitmapText(stepFont);
		widestNumber.setText(HELP_STEPS.length + ". ");
		widestNumber.setSize(stepFont.getCharSet().getRenderedSize());
		final float numberColumnWidth = widestNumber.getLineWidth();
		
		// Fixed X where all step descriptions start (after the number column).
		final float descriptionX = leftX + numberColumnWidth;
		
		for (int i = 0; i < HELP_STEPS.length; i++)
		{
			final float rowY = cursorY - i * ROW_HEIGHT;
			final String numberStr = (i + 1) + ". ";
			
			// Step number (green) - right-aligned within the number column.
			final BitmapText numberText = new BitmapText(stepFont);
			numberText.setText(numberStr);
			numberText.setColor(COLOR_STEP_NUMBER);
			numberText.setSize(stepFont.getCharSet().getRenderedSize());
			final float numberX = leftX + numberColumnWidth - numberText.getLineWidth();
			numberText.setLocalTranslation(numberX, rowY, Z_TEXT);
			_rootNode.attachChild(numberText);
			
			// Shadow.
			final BitmapText stepShadow = new BitmapText(stepFont);
			stepShadow.setText(HELP_STEPS[i]);
			stepShadow.setColor(COLOR_TEXT_SHADOW);
			stepShadow.setSize(stepFont.getCharSet().getRenderedSize());
			stepShadow.setLocalTranslation(descriptionX + 1, rowY - 1, Z_TEXT - 0.1f);
			_rootNode.attachChild(stepShadow);
			
			// Main text.
			final BitmapText stepText = new BitmapText(stepFont);
			stepText.setText(HELP_STEPS[i]);
			stepText.setColor(COLOR_STEP_TEXT);
			stepText.setSize(stepFont.getCharSet().getRenderedSize());
			stepText.setLocalTranslation(descriptionX, rowY, Z_TEXT);
			_rootNode.attachChild(stepText);
		}
		
		// Advance cursor past all step rows.
		cursorY -= HELP_STEPS.length * ROW_HEIGHT + SECTION_GAP;
		
		// ---- Controls section ----
		final GameInputManager gim = app.getGameInputManager();
		
		// Build control labels from current bindings.
		final String moveKeys = GameInputManager.getKeyName(gim.getKeyCode(GameInputManager.MOVE_FORWARD)) + " " + GameInputManager.getKeyName(gim.getKeyCode(GameInputManager.MOVE_LEFT)) + " " + GameInputManager.getKeyName(gim.getKeyCode(GameInputManager.MOVE_BACK)) + " " + GameInputManager.getKeyName(gim.getKeyCode(GameInputManager.MOVE_RIGHT));
		
		final String[][] controlEntries =
		{
			{
				"Move",
				moveKeys
			},
			{
				"Jump",
				GameInputManager.getKeyName(gim.getKeyCode(GameInputManager.JUMP))
			},
			{
				"Inventory",
				GameInputManager.getKeyName(gim.getKeyCode(GameInputManager.INVENTORY))
			},
			{
				"Attack",
				GameInputManager.getMouseButtonName(gim.getMouseCode(GameInputManager.ATTACK))
			},
			{
				"Interact",
				GameInputManager.getMouseButtonName(gim.getMouseCode(GameInputManager.PLACE_BLOCK))
			},
		};
		
		// "Controls" header - same title font as "How to Play", centered.
		final BitmapText controlsTitleShadow = new BitmapText(titleFont);
		controlsTitleShadow.setText("Controls");
		controlsTitleShadow.setColor(COLOR_TEXT_SHADOW);
		controlsTitleShadow.setSize(titleFont.getCharSet().getRenderedSize());
		
		final BitmapText controlsTitleText = new BitmapText(titleFont);
		controlsTitleText.setText("Controls");
		controlsTitleText.setColor(COLOR_TITLE);
		controlsTitleText.setSize(titleFont.getCharSet().getRenderedSize());
		
		final float controlsTitleX = panelX + (panelWidth - controlsTitleText.getLineWidth()) / 2;
		controlsTitleText.setLocalTranslation(controlsTitleX, cursorY, Z_TEXT);
		controlsTitleShadow.setLocalTranslation(controlsTitleX + 1, cursorY - 1, Z_TEXT - 0.1f);
		_rootNode.attachChild(controlsTitleShadow);
		_rootNode.attachChild(controlsTitleText);
		
		cursorY -= controlsTitleText.getLineHeight() + PANEL_PADDING * 0.3f;
		
		// Find the widest "Label: Value" combination to center the block.
		float maxEntryWidth = 0;
		for (String[] entry : controlEntries)
		{
			final BitmapText measure = new BitmapText(stepFont);
			measure.setText(entry[0] + ":  " + entry[1]);
			measure.setSize(stepFont.getCharSet().getRenderedSize());
			if (measure.getLineWidth() > maxEntryWidth)
			{
				maxEntryWidth = measure.getLineWidth();
			}
		}
		
		// Center the control entries block within the panel.
		final float controlBlockX = panelX + (panelWidth - maxEntryWidth) / 2;
		final float controlRowHeight = ROW_HEIGHT * 0.75f;
		
		// Render each control entry as "Label: Value".
		for (String[] entry : controlEntries)
		{
			// Label (blue).
			final BitmapText labelText = new BitmapText(stepFont);
			labelText.setText(entry[0] + ":  ");
			labelText.setColor(COLOR_KEY_LABEL);
			labelText.setSize(stepFont.getCharSet().getRenderedSize());
			labelText.setLocalTranslation(controlBlockX, cursorY, Z_TEXT);
			_rootNode.attachChild(labelText);
			
			// Value (light grey).
			final float valueX = controlBlockX + labelText.getLineWidth();
			
			final BitmapText valueShadow = new BitmapText(stepFont);
			valueShadow.setText(entry[1]);
			valueShadow.setColor(COLOR_TEXT_SHADOW);
			valueShadow.setSize(stepFont.getCharSet().getRenderedSize());
			valueShadow.setLocalTranslation(valueX + 1, cursorY - 1, Z_TEXT - 0.1f);
			_rootNode.attachChild(valueShadow);
			
			final BitmapText valueText = new BitmapText(stepFont);
			valueText.setText(entry[1]);
			valueText.setColor(COLOR_KEY_VALUE);
			valueText.setSize(stepFont.getCharSet().getRenderedSize());
			valueText.setLocalTranslation(valueX, cursorY, Z_TEXT);
			_rootNode.attachChild(valueText);
			
			cursorY -= controlRowHeight;
		}
		
		// ---- Tips section ----
		cursorY -= SECTION_GAP;
		
		// "Game Tips" header - same title font as "How to Play", centered.
		final BitmapText tipsTitleShadow = new BitmapText(titleFont);
		tipsTitleShadow.setText("Game Tips");
		tipsTitleShadow.setColor(COLOR_TEXT_SHADOW);
		tipsTitleShadow.setSize(titleFont.getCharSet().getRenderedSize());
		
		final BitmapText tipsTitleText = new BitmapText(titleFont);
		tipsTitleText.setText("Game Tips");
		tipsTitleText.setColor(COLOR_TITLE);
		tipsTitleText.setSize(titleFont.getCharSet().getRenderedSize());
		
		final float tipsTitleX = panelX + (panelWidth - tipsTitleText.getLineWidth()) / 2;
		tipsTitleText.setLocalTranslation(tipsTitleX, cursorY, Z_TEXT);
		tipsTitleShadow.setLocalTranslation(tipsTitleX + 1, cursorY - 1, Z_TEXT - 0.1f);
		_rootNode.attachChild(tipsTitleShadow);
		_rootNode.attachChild(tipsTitleText);
		
		cursorY -= tipsTitleText.getLineHeight() + PANEL_PADDING * 0.3f;
		
		// Tip lines - white text (same color as step text), centered, no numbers.
		for (String tip : TIPS)
		{
			final BitmapText tipShadow = new BitmapText(stepFont);
			tipShadow.setText(tip);
			tipShadow.setColor(COLOR_TEXT_SHADOW);
			tipShadow.setSize(stepFont.getCharSet().getRenderedSize());
			
			final BitmapText tipText = new BitmapText(stepFont);
			tipText.setText(tip);
			tipText.setColor(COLOR_STEP_TEXT);
			tipText.setSize(stepFont.getCharSet().getRenderedSize());
			
			final float tipX = panelX + (panelWidth - tipText.getLineWidth()) / 2;
			tipText.setLocalTranslation(tipX, cursorY, Z_TEXT);
			tipShadow.setLocalTranslation(tipX + 1, cursorY - 1, Z_TEXT - 0.1f);
			_rootNode.attachChild(tipShadow);
			_rootNode.attachChild(tipText);
			
			cursorY -= controlRowHeight + stepFont.getCharSet().getRenderedSize() * 0.5f;
		}
	}
	
	// ========================================================
	// Update.
	// ========================================================
	
	/**
	 * Per-frame update. Currently a no-op.
	 * @param tpf time per frame in seconds
	 */
	public void update(float tpf)
	{
		// No per-frame work needed.
	}
	
	// ========================================================
	// Cleanup.
	// ========================================================
	
	/**
	 * Full teardown: closes the screen and releases resources.
	 */
	public void cleanup()
	{
		close();
	}
}
