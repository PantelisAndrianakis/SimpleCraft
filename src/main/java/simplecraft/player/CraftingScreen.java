package simplecraft.player;

import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.InputManager;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;

import simplecraft.SimpleCraft;
import simplecraft.audio.AudioManager;
import simplecraft.item.CraftingRecipe;
import simplecraft.item.CraftingRegistry;
import simplecraft.item.Inventory;
import simplecraft.item.ItemRegistry;
import simplecraft.item.ItemTemplate;
import simplecraft.item.ItemTextureResolver;
import simplecraft.ui.FontManager;

/**
 * Crafting UI opened by right-clicking a Crafting Table.<br>
 * Displays all registered recipes in a scrollable list with item icons (same sprites<br>
 * as the inventory screen). Craftable recipes are highlighted; uncraftable recipes<br>
 * are greyed out. Clicking [Craft] consumes ingredients and adds the output.<br>
 * <br>
 * While open, player movement and block interaction input are unregistered<br>
 * (same pattern as {@link InventoryScreen}). Close with Escape or Tab.
 * @author Pantelis Andrianakis
 * @since March 17th 2026
 */
public class CraftingScreen implements ActionListener, AnalogListener
{
	// ========================================================
	// Constants.
	// ========================================================
	
	/** Font size for recipe text. */
	private static final int FONT_SIZE = 16;
	
	/** Font size for the title. */
	private static final int TITLE_FONT_SIZE = 24;
	
	/** Vertical space per recipe row (pixels). */
	private static final float ROW_HEIGHT = 48;
	
	/** Padding inside the panel edges (pixels). */
	private static final float PANEL_PADDING = 20;
	
	/** Panel width as a fraction of screen width. */
	private static final float PANEL_WIDTH_FRACTION = 0.65f;
	
	/** Panel height as a fraction of screen height. */
	private static final float PANEL_HEIGHT_FRACTION = 0.75f;
	
	/** Craft button width estimate for click detection (pixels). */
	private static final float BUTTON_WIDTH = 70;
	
	/** Pixels to scroll per mouse wheel tick. */
	private static final float SCROLL_SPEED = 40;
	
	/** Background overlay alpha. */
	private static final float OVERLAY_ALPHA = 0.55f;
	
	/** Panel background alpha. */
	private static final float PANEL_ALPHA = 0.8f;
	
	/** Size of the output item icon (pixels). */
	private static final float ICON_SIZE = 32;
	
	/** Gap between icon and text. */
	private static final float ICON_TEXT_GAP = 8;
	
	// Z-depths matching InventoryScreen conventions.
	private static final float Z_OVERLAY = 10f;
	private static final float Z_PANEL = 11f;
	private static final float Z_ICON = 12f;
	private static final float Z_TEXT = 12.5f;
	
	// Input action names (unique to avoid collisions with other systems).
	private static final String ACTION_CRAFT_CLICK = "CRAFTING_CLICK";
	private static final String ACTION_CRAFT_SCROLL_UP = "CRAFTING_SCROLL_UP";
	private static final String ACTION_CRAFT_SCROLL_DOWN = "CRAFTING_SCROLL_DOWN";
	
	// Colors.
	private static final ColorRGBA COLOR_CRAFTABLE_OUTPUT = new ColorRGBA(0.3f, 1.0f, 0.3f, 1.0f);
	private static final ColorRGBA COLOR_CRAFTABLE_INGREDIENTS = new ColorRGBA(0.85f, 0.85f, 0.85f, 1.0f);
	private static final ColorRGBA COLOR_CRAFTABLE_BUTTON = new ColorRGBA(0.2f, 0.9f, 0.2f, 1.0f);
	private static final ColorRGBA COLOR_UNCRAFTABLE_OUTPUT = new ColorRGBA(0.45f, 0.45f, 0.45f, 1.0f);
	private static final ColorRGBA COLOR_UNCRAFTABLE_INGREDIENTS = new ColorRGBA(0.35f, 0.35f, 0.35f, 1.0f);
	private static final ColorRGBA COLOR_UNCRAFTABLE_BUTTON = new ColorRGBA(0.35f, 0.35f, 0.35f, 1.0f);
	private static final ColorRGBA COLOR_TITLE = new ColorRGBA(1.0f, 0.9f, 0.5f, 1.0f);
	private static final ColorRGBA COLOR_HINT = new ColorRGBA(0.6f, 0.6f, 0.6f, 1.0f);
	private static final ColorRGBA COLOR_TEXT_SHADOW = new ColorRGBA(0.0f, 0.0f, 0.0f, 0.8f);
	
	// ========================================================
	// Fields.
	// ========================================================
	
	private final PlayerController _playerController;
	private final BlockInteraction _blockInteraction;
	private final AudioManager _audioManager;
	
	/** Whether the crafting screen is currently open. */
	private boolean _open;
	
	/** Root node holding all crafting UI elements. Attached to guiNode when open. */
	private Node _rootNode;
	
	/** Font for recipe text. */
	private BitmapFont _recipeFont;
	
	/** Font for title text. */
	private BitmapFont _titleFont;
	
	/** Per-recipe row data for click detection and color updates. */
	private final List<RecipeRow> _rows = new ArrayList<>();
	
	/** Current vertical scroll offset (pixels). 0 = top. Positive = scrolled down. */
	private float _scrollOffset;
	
	/** Maximum scroll offset based on content height vs. visible area. */
	private float _maxScrollOffset;
	
	// Panel geometry (recalculated on open to handle resolution changes).
	private float _panelX;
	private float _panelY;
	private float _panelWidth;
	private float _panelHeight;
	
	/** Top Y of the first recipe row inside the panel (screen coords). */
	private float _recipeAreaTop;
	
	/** Bottom Y of the recipe area (above the hint text). */
	private float _recipeAreaBottom;
	
	// ========================================================
	// Recipe Row Data.
	// ========================================================
	
	/**
	 * Tracks the UI elements and bounds for a single recipe row.
	 */
	private static class RecipeRow
	{
		CraftingRecipe recipe;
		
		/** Output item icon (textured quad with sprite from ItemTextureResolver). */
		Geometry iconQuad;
		Material iconMat;
		
		/** Fallback: type label letter (W, P, A, +, etc.) shown when no sprite is available. */
		BitmapText iconLabel;
		
		BitmapText outputText;
		BitmapText outputTextShadow;
		BitmapText ingredientText;
		BitmapText buttonText;
		
		/** Y position of this row's top edge (index * ROW_HEIGHT from the top). */
		float localY;
		
		/** X position of the [Craft] button (right-aligned). */
		float buttonX;
		
		/** Whether a sprite texture was found for the icon. */
		boolean hasTexture;
	}
	
	// ========================================================
	// Constructor.
	// ========================================================
	
	/**
	 * Creates a new crafting screen (initially hidden).
	 * @param playerController the player controller for inventory access and input management
	 * @param blockInteraction the block interaction handler for input management
	 * @param audioManager the audio manager for sound effects
	 */
	public CraftingScreen(PlayerController playerController, BlockInteraction blockInteraction, AudioManager audioManager)
	{
		_playerController = playerController;
		_blockInteraction = blockInteraction;
		_audioManager = audioManager;
	}
	
	// ========================================================
	// Open / Close.
	// ========================================================
	
	/**
	 * Opens the crafting screen: builds the UI, disables player input, shows cursor.
	 */
	public void open()
	{
		if (_open)
		{
			return;
		}
		
		_open = true;
		_scrollOffset = 0;
		
		final SimpleCraft app = SimpleCraft.getInstance();
		
		// Disable player movement and block interaction input (same pattern as InventoryScreen).
		_playerController.unregisterInput();
		_blockInteraction.unregisterInput();
		
		// Build UI.
		buildUI(app);
		
		// Attach to guiNode.
		app.getGuiNode().attachChild(_rootNode);
		
		// Show cursor.
		app.getInputManager().setCursorVisible(true);
		
		// Register crafting screen input (click and scroll).
		registerInput(app.getInputManager());
		
		// Set initial recipe colors.
		refreshRecipeColors();
	}
	
	/**
	 * Closes the crafting screen: detaches UI, restores player input, hides cursor.
	 */
	public void close()
	{
		if (!_open)
		{
			return;
		}
		
		_open = false;
		
		final SimpleCraft app = SimpleCraft.getInstance();
		
		// Unregister crafting screen input.
		unregisterInput(app.getInputManager());
		
		// Detach UI.
		if (_rootNode != null)
		{
			app.getGuiNode().detachChild(_rootNode);
			_rootNode = null;
		}
		
		// Clear row references.
		_rows.clear();
		
		// Restore player movement and block interaction input.
		_playerController.registerInput();
		_blockInteraction.registerInput();
		
		// Hide cursor.
		app.getInputManager().setCursorVisible(false);
	}
	
	/**
	 * Returns true if the crafting screen is currently open.
	 */
	public boolean isOpen()
	{
		return _open;
	}
	
	// ========================================================
	// UI Construction.
	// ========================================================
	
	/**
	 * Builds all UI elements for the crafting screen.
	 */
	private void buildUI(SimpleCraft app)
	{
		_rootNode = new Node("CraftingScreen");
		
		final Camera cam = app.getCamera();
		final float screenW = cam.getWidth();
		final float screenH = cam.getHeight();
		
		// Load fonts.
		_recipeFont = FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_LINOCUT_PATH, Font.PLAIN, FONT_SIZE);
		_titleFont = FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_LINOCUT_PATH, Font.PLAIN, TITLE_FONT_SIZE);
		
		// ---- Full-screen dark overlay ----
		final Quad overlayQuad = new Quad(screenW, screenH);
		final Geometry overlay = new Geometry("CraftingOverlay", overlayQuad);
		final Material overlayMat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
		overlayMat.setColor("Color", new ColorRGBA(0, 0, 0, OVERLAY_ALPHA));
		overlayMat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
		overlay.setMaterial(overlayMat);
		overlay.setQueueBucket(Bucket.Gui);
		overlay.setLocalTranslation(0, 0, Z_OVERLAY);
		_rootNode.attachChild(overlay);
		
		// ---- Panel background ----
		_panelWidth = screenW * PANEL_WIDTH_FRACTION;
		_panelHeight = screenH * PANEL_HEIGHT_FRACTION;
		_panelX = (screenW - _panelWidth) / 2;
		_panelY = (screenH - _panelHeight) / 2;
		
		final Quad panelQuad = new Quad(_panelWidth, _panelHeight);
		final Geometry panelBg = new Geometry("CraftingPanel", panelQuad);
		final Material panelMat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
		panelMat.setColor("Color", new ColorRGBA(0.1f, 0.1f, 0.12f, PANEL_ALPHA));
		panelMat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
		panelBg.setMaterial(panelMat);
		panelBg.setQueueBucket(Bucket.Gui);
		panelBg.setLocalTranslation(_panelX, _panelY, Z_PANEL);
		_rootNode.attachChild(panelBg);
		
		// ---- Title ----
		final BitmapText titleShadow = new BitmapText(_titleFont);
		titleShadow.setText("Crafting Table");
		titleShadow.setColor(COLOR_TEXT_SHADOW);
		titleShadow.setSize(_titleFont.getCharSet().getRenderedSize());
		
		final BitmapText titleText = new BitmapText(_titleFont);
		titleText.setText("Crafting Table");
		titleText.setColor(COLOR_TITLE);
		titleText.setSize(_titleFont.getCharSet().getRenderedSize());
		
		final float titleX = _panelX + (_panelWidth - titleText.getLineWidth()) / 2;
		final float titleY = _panelY + _panelHeight - PANEL_PADDING;
		titleText.setLocalTranslation(titleX, titleY, Z_TEXT);
		titleShadow.setLocalTranslation(titleX + 1, titleY - 1, Z_TEXT - 0.1f);
		_rootNode.attachChild(titleShadow);
		_rootNode.attachChild(titleText);
		
		// ---- Close hint at bottom ----
		final BitmapText hintText = new BitmapText(_recipeFont);
		hintText.setText("Press Escape or Tab to close");
		hintText.setColor(COLOR_HINT);
		hintText.setSize(_recipeFont.getCharSet().getRenderedSize());
		final float hintX = _panelX + (_panelWidth - hintText.getLineWidth()) / 2;
		final float hintY = _panelY + PANEL_PADDING + hintText.getLineHeight();
		hintText.setLocalTranslation(hintX, hintY, Z_TEXT);
		_rootNode.attachChild(hintText);
		
		// ---- Recipe area bounds ----
		_recipeAreaTop = titleY - titleText.getLineHeight() - PANEL_PADDING * 0.5f;
		_recipeAreaBottom = hintY + PANEL_PADDING * 0.5f;
		
		// ---- Build recipe rows ----
		buildRecipeRows(app);
		
		// Calculate max scroll offset.
		final float contentHeight = _rows.size() * ROW_HEIGHT;
		final float visibleHeight = _recipeAreaTop - _recipeAreaBottom;
		_maxScrollOffset = Math.max(0, contentHeight - visibleHeight);
		
		// Position rows.
		layoutRecipeRows();
	}
	
	/**
	 * Creates icon quads and text elements for every recipe.
	 */
	private void buildRecipeRows(SimpleCraft app)
	{
		_rows.clear();
		
		final List<CraftingRecipe> allRecipes = CraftingRegistry.getAllRecipes();
		// final float leftX = _panelX + PANEL_PADDING;
		final float rightX = _panelX + _panelWidth - PANEL_PADDING;
		
		for (int i = 0; i < allRecipes.size(); i++)
		{
			final CraftingRecipe recipe = allRecipes.get(i);
			final RecipeRow row = new RecipeRow();
			row.recipe = recipe;
			row.localY = i * ROW_HEIGHT;
			
			// ---- Output item icon ----
			final ItemTemplate outputTemplate = ItemRegistry.get(recipe.getOutputItemId());
			
			// Create icon material.
			row.iconMat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
			row.iconMat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
			
			// Try to resolve a sprite texture (same as InventoryScreen).
			final Texture iconTexture = outputTemplate != null ? ItemTextureResolver.resolve(app.getAssetManager(), outputTemplate) : null;
			
			if (iconTexture != null)
			{
				row.iconMat.setTexture("ColorMap", iconTexture);
				row.iconMat.setColor("Color", ColorRGBA.White);
				row.hasTexture = true;
			}
			else
			{
				// Fallback: colored quad with type label (same helpers as InventoryScreen).
				final ColorRGBA itemColor = InventoryScreen.getItemColor(outputTemplate);
				row.iconMat.setColor("Color", itemColor);
				row.hasTexture = false;
			}
			
			final Quad iconQuad = new Quad(ICON_SIZE, ICON_SIZE);
			row.iconQuad = new Geometry("CraftIcon" + i, iconQuad);
			row.iconQuad.setMaterial(row.iconMat);
			row.iconQuad.setQueueBucket(Bucket.Gui);
			_rootNode.attachChild(row.iconQuad);
			
			// Fallback label (only visible when no texture).
			if (!row.hasTexture)
			{
				final String label = InventoryScreen.getItemLabel(outputTemplate);
				if (label != null)
				{
					row.iconLabel = new BitmapText(_recipeFont);
					row.iconLabel.setText(label);
					row.iconLabel.setSize(_recipeFont.getCharSet().getRenderedSize() * 1.2f);
					row.iconLabel.setColor(ColorRGBA.White);
					_rootNode.attachChild(row.iconLabel);
				}
			}
			
			// ---- Text to the right of the icon ----
			// final float textX = leftX + ICON_SIZE + ICON_TEXT_GAP;
			
			// Output name + count (line 1) with shadow.
			row.outputTextShadow = new BitmapText(_recipeFont);
			row.outputTextShadow.setText(recipe.getOutputDisplayName());
			row.outputTextShadow.setSize(_recipeFont.getCharSet().getRenderedSize());
			row.outputTextShadow.setColor(COLOR_TEXT_SHADOW);
			_rootNode.attachChild(row.outputTextShadow);
			
			row.outputText = new BitmapText(_recipeFont);
			row.outputText.setText(recipe.getOutputDisplayName());
			row.outputText.setSize(_recipeFont.getCharSet().getRenderedSize());
			_rootNode.attachChild(row.outputText);
			
			// Ingredient list (line 2).
			row.ingredientText = new BitmapText(_recipeFont);
			row.ingredientText.setText("  Needs: " + recipe.getIngredientsDisplayString());
			row.ingredientText.setSize(_recipeFont.getCharSet().getRenderedSize());
			_rootNode.attachChild(row.ingredientText);
			
			// [Craft] button text (right-aligned).
			row.buttonText = new BitmapText(_recipeFont);
			row.buttonText.setText("[Craft]");
			row.buttonText.setSize(_recipeFont.getCharSet().getRenderedSize());
			row.buttonX = rightX - row.buttonText.getLineWidth();
			_rootNode.attachChild(row.buttonText);
			
			_rows.add(row);
		}
	}
	
	/**
	 * Positions all recipe rows based on the current scroll offset.<br>
	 * Hides rows that are outside the visible recipe area.
	 */
	private void layoutRecipeRows()
	{
		final float lineHeight = _recipeFont.getCharSet().getRenderedSize();
		final float leftX = _panelX + PANEL_PADDING;
		final float textX = leftX + ICON_SIZE + ICON_TEXT_GAP;
		
		for (RecipeRow row : _rows)
		{
			// Top of this row in screen coordinates.
			final float rowScreenY = _recipeAreaTop - row.localY + _scrollOffset;
			
			// Visibility check.
			final float rowTop = rowScreenY;
			final float rowBottom = rowScreenY - ROW_HEIGHT;
			final boolean visible = rowTop > _recipeAreaBottom && rowBottom < _recipeAreaTop;
			
			final Node.CullHint hint = visible ? Node.CullHint.Inherit : Node.CullHint.Always;
			
			// Icon position: vertically centered in the row.
			final float iconY = rowScreenY - (ROW_HEIGHT + ICON_SIZE) / 2;
			row.iconQuad.setLocalTranslation(leftX, iconY, Z_ICON);
			row.iconQuad.setCullHint(hint);
			
			// Fallback label centered in icon quad.
			if (row.iconLabel != null)
			{
				final float labelWidth = row.iconLabel.getLineWidth();
				final float labelHeight = row.iconLabel.getLineHeight();
				final float labelX = leftX + (ICON_SIZE - labelWidth) / 2;
				final float labelY = iconY + (ICON_SIZE + labelHeight) / 2;
				row.iconLabel.setLocalTranslation(labelX, labelY, Z_TEXT);
				row.iconLabel.setCullHint(hint);
			}
			
			// Line 1 (output name): vertically aligned near top of row.
			final float line1Y = rowScreenY - lineHeight * 0.3f;
			row.outputText.setLocalTranslation(textX, line1Y, Z_TEXT);
			row.outputTextShadow.setLocalTranslation(textX + 1, line1Y - 1, Z_TEXT - 0.1f);
			row.outputText.setCullHint(hint);
			row.outputTextShadow.setCullHint(hint);
			
			// Line 2 (ingredients): below line 1.
			final float line2Y = line1Y - lineHeight - 2;
			row.ingredientText.setLocalTranslation(textX, line2Y, Z_TEXT);
			row.ingredientText.setCullHint(hint);
			
			// Button: vertically centered in the row.
			final float buttonY = rowScreenY - (ROW_HEIGHT - lineHeight) / 2;
			row.buttonText.setLocalTranslation(row.buttonX, buttonY, Z_TEXT);
			row.buttonText.setCullHint(hint);
		}
	}
	
	// ========================================================
	// Recipe State Refresh.
	// ========================================================
	
	/**
	 * Updates recipe row colors based on current inventory contents.
	 */
	private void refreshRecipeColors()
	{
		final Inventory inventory = _playerController.getInventory();
		
		for (RecipeRow row : _rows)
		{
			final boolean craftable = row.recipe.canCraft(inventory);
			
			if (craftable)
			{
				row.outputText.setColor(COLOR_CRAFTABLE_OUTPUT);
				row.ingredientText.setColor(COLOR_CRAFTABLE_INGREDIENTS);
				row.buttonText.setColor(COLOR_CRAFTABLE_BUTTON);
				row.buttonText.setText("[Craft]");
				
				// Icon at full brightness.
				if (row.hasTexture)
				{
					row.iconMat.setColor("Color", ColorRGBA.White);
				}
			}
			else
			{
				row.outputText.setColor(COLOR_UNCRAFTABLE_OUTPUT);
				row.ingredientText.setColor(COLOR_UNCRAFTABLE_INGREDIENTS);
				row.buttonText.setColor(COLOR_UNCRAFTABLE_BUTTON);
				row.buttonText.setText("[----]");
				
				// Icon dimmed.
				if (row.hasTexture)
				{
					row.iconMat.setColor("Color", new ColorRGBA(0.4f, 0.4f, 0.4f, 0.6f));
				}
			}
		}
	}
	
	// ========================================================
	// Update.
	// ========================================================
	
	/**
	 * Per-frame update. Currently a no-op — recipe colors refresh on craft events.
	 * @param tpf time per frame in seconds
	 */
	public void update(float tpf)
	{
		// No per-frame work needed.
	}
	
	// ========================================================
	// Input.
	// ========================================================
	
	/**
	 * Registers mouse click and scroll input for the crafting screen.
	 */
	private void registerInput(InputManager inputManager)
	{
		if (!inputManager.hasMapping(ACTION_CRAFT_CLICK))
		{
			inputManager.addMapping(ACTION_CRAFT_CLICK, new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
		}
		if (!inputManager.hasMapping(ACTION_CRAFT_SCROLL_UP))
		{
			inputManager.addMapping(ACTION_CRAFT_SCROLL_UP, new MouseAxisTrigger(MouseInput.AXIS_WHEEL, false));
		}
		if (!inputManager.hasMapping(ACTION_CRAFT_SCROLL_DOWN))
		{
			inputManager.addMapping(ACTION_CRAFT_SCROLL_DOWN, new MouseAxisTrigger(MouseInput.AXIS_WHEEL, true));
		}
		
		inputManager.addListener((ActionListener) this, ACTION_CRAFT_CLICK);
		inputManager.addListener((AnalogListener) this, ACTION_CRAFT_SCROLL_UP, ACTION_CRAFT_SCROLL_DOWN);
	}
	
	/**
	 * Removes input listeners.
	 */
	private void unregisterInput(InputManager inputManager)
	{
		inputManager.removeListener(this);
	}
	
	@Override
	public void onAction(String name, boolean isPressed, float tpf)
	{
		if (!_open)
		{
			return;
		}
		
		if (ACTION_CRAFT_CLICK.equals(name) && isPressed)
		{
			handleClick();
		}
	}
	
	@Override
	public void onAnalog(String name, float value, float tpf)
	{
		if (!_open)
		{
			return;
		}
		
		switch (name)
		{
			case ACTION_CRAFT_SCROLL_UP:
			{
				_scrollOffset = Math.max(0, _scrollOffset - SCROLL_SPEED);
				layoutRecipeRows();
				break;
			}
			case ACTION_CRAFT_SCROLL_DOWN:
			{
				_scrollOffset = Math.min(_maxScrollOffset, _scrollOffset + SCROLL_SPEED);
				layoutRecipeRows();
				break;
			}
		}
	}
	
	/**
	 * Handles a mouse click: checks if it landed on a craftable recipe's [Craft] button.
	 */
	private void handleClick()
	{
		final Vector2f cursor = SimpleCraft.getInstance().getInputManager().getCursorPosition();
		final float mx = cursor.x;
		final float my = cursor.y;
		
		// Check if click is inside the panel.
		if (mx < _panelX || mx > _panelX + _panelWidth || my < _panelY || my > _panelY + _panelHeight)
		{
			return;
		}
		
		final float lineHeight = _recipeFont.getCharSet().getRenderedSize();
		final Inventory inventory = _playerController.getInventory();
		
		for (RecipeRow row : _rows)
		{
			// Calculate the button's screen position.
			final float rowScreenY = _recipeAreaTop - row.localY + _scrollOffset;
			final float buttonY = rowScreenY - (ROW_HEIGHT - lineHeight) / 2;
			final float buttonTop = buttonY;
			final float buttonBottom = buttonY - lineHeight;
			
			// Skip if outside visible area.
			if (buttonTop < _recipeAreaBottom || buttonBottom > _recipeAreaTop)
			{
				continue;
			}
			
			// Check vertical hit.
			if (my < buttonBottom || my > buttonTop)
			{
				continue;
			}
			
			// Check horizontal hit (button is right-aligned).
			final float buttonRight = _panelX + _panelWidth - PANEL_PADDING;
			final float buttonLeft = buttonRight - BUTTON_WIDTH;
			if (mx < buttonLeft || mx > buttonRight)
			{
				continue;
			}
			
			// Hit a button — try to craft.
			if (row.recipe.canCraft(inventory))
			{
				if (row.recipe.craft(inventory))
				{
					// Play a confirmation sound.
					if (_audioManager != null)
					{
						_audioManager.playSfx(AudioManager.SFX_BLOCK_PLACE);
					}
					
					System.out.println("Crafted: " + row.recipe.getOutputDisplayName());
					
					// Refresh all recipe colors (ingredient counts changed).
					refreshRecipeColors();
				}
			}
			
			// Only process one button per click.
			break;
		}
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
