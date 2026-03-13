package simplecraft.player;

import java.awt.Font;

import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Quad;

import simplecraft.SimpleCraft;
import simplecraft.item.Inventory;
import simplecraft.item.ItemInstance;
import simplecraft.item.ItemTemplate;
import simplecraft.state.PlayingState;
import simplecraft.ui.FontManager;
import simplecraft.world.Block;

/**
 * In-game heads-up display showing health, air, hotbar, crosshair,<br>
 * block-breaking progress, and the death/respawn screen.<br>
 * <br>
 * All elements are attached to the application's GUI node (screen-space overlay).<br>
 * Call {@link #update(float, float, float, float, boolean, int, int, boolean, boolean)} each frame<br>
 * with current player state, and {@link #cleanup()} when leaving the playing state.<br>
 * <br>
 * <b>Crosshair:</b> Small "+" at screen center, white with slight transparency.<br>
 * <b>Health bar:</b> Top-left, red fill proportional to health. Dark background.<br>
 * <b>Air meter:</b> Blue bar below health, visible only when submerged. Fades in/out<br>
 * over 0.5 seconds. Flashes red when air drops below 3 seconds.<br>
 * <b>Hotbar:</b> 9 rectangular slots horizontally centered at screen bottom.<br>
 * Each slot shows a colored square for the item type, optional label letter,<br>
 * stack count (if > 1), and durability bar for weapons/tools.<br>
 * Selected slot has a bright highlight border.<br>
 * <b>Break progress:</b> Small bar below crosshair showing "BlockName 3/8" while breaking.<br>
 * <b>Death screen:</b> Translucent dark overlay with "You Died" text, death cause,<br>
 * and "Click to Respawn" prompt. Shown when health reaches 0.
 * @author Pantelis Andrianakis
 * @since March 2nd 2026
 */
public class PlayerHUD
{
	// ========================================================
	// Layout Constants.
	// ========================================================
	
	/** Health bar width in pixels. */
	private static final float HEALTH_BAR_WIDTH = 200f;
	
	/** Health bar height in pixels. */
	private static final float HEALTH_BAR_HEIGHT = 16f;
	
	/** Air bar width in pixels. */
	private static final float AIR_BAR_WIDTH = 200f;
	
	/** Air bar height in pixels. */
	private static final float AIR_BAR_HEIGHT = 12f;
	
	/** Padding from screen edges in pixels. */
	private static final float EDGE_PADDING = 16f;
	
	/** Padding between health bar and air bar. */
	private static final float BAR_SPACING = 6f;
	
	/** Background padding around bars. */
	private static final float BG_PADDING = 3f;
	
	/** Break progress bar width in pixels. */
	private static final float BREAK_BAR_WIDTH = 140f;
	
	/** Break progress bar height in pixels. */
	private static final float BREAK_BAR_HEIGHT = 10f;
	
	/** Offset below crosshair for break progress bar. */
	private static final float BREAK_BAR_OFFSET_Y = 30f;
	
	/** Seconds after surfacing before air meter fades out. */
	private static final float AIR_FADE_DELAY = 2.0f;
	
	/** Duration of the air meter fade in/out transition. */
	private static final float AIR_FADE_DURATION = 0.5f;
	
	/** Air threshold in seconds below which the bar flashes red. */
	private static final float AIR_WARNING_THRESHOLD = 3.0f;
	
	/** Flash rate in cycles per second when air is critically low. */
	private static final float AIR_FLASH_RATE = 3.0f;
	
	/** Number of hotbar slots. */
	private static final int HOTBAR_SIZE = Inventory.HOTBAR_SLOTS;
	
	/** Pixel spacing between hotbar slots. */
	private static final float HOTBAR_SLOT_SPACING = 2f;
	
	/** Padding around the hotbar slot fill for the background. */
	private static final float HOTBAR_BG_PADDING = 2f;
	
	/** Height of the durability bar in pixels. */
	private static final float DURABILITY_BAR_HEIGHT = 3f;
	
	// ========================================================
	// Colors.
	// ========================================================
	
	private static final ColorRGBA COLOR_BG = new ColorRGBA(0.1f, 0.1f, 0.1f, 0.6f);
	private static final ColorRGBA COLOR_HEALTH = new ColorRGBA(0.85f, 0.15f, 0.15f, 1.0f);
	private static final ColorRGBA COLOR_HEALTH_LOW = new ColorRGBA(0.6f, 0.05f, 0.05f, 1.0f);
	private static final ColorRGBA COLOR_AIR = new ColorRGBA(0.2f, 0.5f, 0.9f, 1.0f);
	private static final ColorRGBA COLOR_AIR_WARNING = new ColorRGBA(0.9f, 0.15f, 0.15f, 1.0f);
	private static final ColorRGBA COLOR_BREAK_BG = new ColorRGBA(0.1f, 0.1f, 0.1f, 0.5f);
	private static final ColorRGBA COLOR_BREAK_FILL = new ColorRGBA(1.0f, 0.85f, 0.2f, 0.9f);
	private static final ColorRGBA COLOR_CROSSHAIR = new ColorRGBA(1.0f, 1.0f, 1.0f, 0.7f);
	private static final ColorRGBA COLOR_TEXT = new ColorRGBA(1.0f, 1.0f, 1.0f, 1.0f);
	private static final ColorRGBA COLOR_TEXT_SHADOW = new ColorRGBA(0.0f, 0.0f, 0.0f, 0.8f);
	
	// Hotbar colors.
	private static final ColorRGBA COLOR_HOTBAR_BG = new ColorRGBA(0.1f, 0.1f, 0.1f, 0.7f);
	private static final ColorRGBA COLOR_HOTBAR_EMPTY = new ColorRGBA(0.2f, 0.2f, 0.2f, 0.5f);
	private static final ColorRGBA COLOR_HOTBAR_HIGHLIGHT = new ColorRGBA(1.0f, 1.0f, 1.0f, 0.5f);
	private static final ColorRGBA COLOR_DURABILITY_GREEN = new ColorRGBA(0.2f, 0.85f, 0.2f, 1.0f);
	private static final ColorRGBA COLOR_DURABILITY_YELLOW = new ColorRGBA(0.9f, 0.85f, 0.2f, 1.0f);
	private static final ColorRGBA COLOR_DURABILITY_RED = new ColorRGBA(0.9f, 0.2f, 0.2f, 1.0f);
	
	// Death screen colors.
	private static final ColorRGBA COLOR_DEATH_OVERLAY = new ColorRGBA(0.1f, 0.0f, 0.0f, 0.7f);
	private static final ColorRGBA COLOR_DEATH_TITLE = new ColorRGBA(0.9f, 0.15f, 0.15f, 1.0f);
	private static final ColorRGBA COLOR_DEATH_CAUSE = new ColorRGBA(0.85f, 0.85f, 0.85f, 1.0f);
	private static final ColorRGBA COLOR_DEATH_PROMPT = new ColorRGBA(0.7f, 0.7f, 0.7f, 1.0f);
	
	// ========================================================
	// Fields.
	// ========================================================
	
	private final Node _guiNode;
	private final Node _hudNode;
	private final BitmapFont _font;
	private final int _screenWidth;
	private final int _screenHeight;
	
	// Crosshair.
	private BitmapText _crosshairText;
	
	// Health bar.
	private Geometry _healthBg;
	private Geometry _healthFill;
	private BitmapText _healthText;
	private BitmapText _healthTextShadow;
	private Material _healthFillMat;
	
	// Air meter.
	private Geometry _airBg;
	private Geometry _airFill;
	private BitmapText _airText;
	private BitmapText _airTextShadow;
	private Material _airBgMat;
	private Material _airFillMat;
	
	// Air fade state.
	private float _airAlpha;
	private float _airFadeTimer;
	private boolean _wasSubmerged;
	private float _airFlashTimer;
	
	// Hotbar.
	private final Geometry[] _hotbarBg = new Geometry[HOTBAR_SIZE];
	private final Geometry[] _hotbarFill = new Geometry[HOTBAR_SIZE];
	private final Material[] _hotbarFillMat = new Material[HOTBAR_SIZE];
	private final BitmapText[] _hotbarLabel = new BitmapText[HOTBAR_SIZE];
	private final BitmapText[] _hotbarCount = new BitmapText[HOTBAR_SIZE];
	private final BitmapText[] _hotbarCountShadow = new BitmapText[HOTBAR_SIZE];
	private final Geometry[] _hotbarDurBar = new Geometry[HOTBAR_SIZE];
	private final Material[] _hotbarDurMat = new Material[HOTBAR_SIZE];
	private Geometry _hotbarHighlight;
	private Material _hotbarHighlightMat;
	
	/** Computed hotbar slot size in pixels (proportional to screen height). */
	private final float _hotbarSlotSize;
	
	/** Screen X origin of each hotbar slot. */
	private final float[] _hotbarSlotX = new float[HOTBAR_SIZE];
	
	/** Screen Y origin of hotbar slots (all same). */
	private float _hotbarSlotY;
	
	/** Inventory reference for reading hotbar data. */
	private Inventory _inventory;
	
	/** Tracks the last selected hotbar index to avoid redundant highlight repositioning. */
	private int _lastSelectedIndex = -1;
	
	// Break progress.
	private Geometry _breakBg;
	private Geometry _breakFill;
	private BitmapText _breakText;
	private BitmapText _breakTextShadow;
	private Material _breakFillMat;
	private Node _breakNode;
	
	// Death screen.
	private Node _deathScreenNode;
	private BitmapText _deathTitle;
	private BitmapText _deathTitleShadow;
	private BitmapText _deathCauseText;
	private BitmapText _deathCauseShadow;
	private BitmapText _deathPrompt;
	private BitmapText _deathPromptShadow;
	private boolean _deathScreenVisible;
	
	/** Pulse timer for the "Click to Respawn" text animation. */
	private float _deathPromptPulse;
	
	/** Reusable color to avoid allocation each frame. */
	private final ColorRGBA _tempColor = new ColorRGBA();
	
	// ========================================================
	// Constructor.
	// ========================================================
	
	/**
	 * Creates and attaches all HUD elements to the GUI node.
	 */
	public PlayerHUD()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		_guiNode = app.getGuiNode();
		_screenWidth = app.getCamera().getWidth();
		_screenHeight = app.getCamera().getHeight();
		
		// Scale font size relative to screen height for resolution independence.
		final int fontSize = Math.max(12, (int) (_screenHeight * 0.018f));
		_font = FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_REGULAR_PATH, Font.PLAIN, fontSize);
		
		// Compute hotbar slot size proportional to screen height.
		_hotbarSlotSize = Math.max(32f, _screenHeight * 0.037f);
		
		// Container node for easy cleanup.
		_hudNode = new Node("PlayerHUD");
		
		// Build all elements.
		buildCrosshair();
		buildHealthBar();
		buildAirMeter();
		buildHotbar();
		buildBreakProgress();
		buildDeathScreen();
		
		_guiNode.attachChild(_hudNode);
	}
	
	// ========================================================
	// Build Methods.
	// ========================================================
	
	private void buildCrosshair()
	{
		_crosshairText = new BitmapText(_font);
		_crosshairText.setText("+");
		_crosshairText.setSize(_font.getCharSet().getRenderedSize() * 1.5f);
		_crosshairText.setColor(COLOR_CROSSHAIR);
		
		// Center on screen. BitmapText origin is bottom-left of text.
		final float textWidth = _crosshairText.getLineWidth();
		final float textHeight = _crosshairText.getLineHeight();
		_crosshairText.setLocalTranslation((_screenWidth - textWidth) / 2f, (_screenHeight + textHeight) / 2f, 1);
		
		_hudNode.attachChild(_crosshairText);
	}
	
	private void buildHealthBar()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		final float barX = EDGE_PADDING;
		final float barY = _screenHeight - EDGE_PADDING - HEALTH_BAR_HEIGHT;
		
		// Background.
		_healthBg = createQuad("HealthBg", HEALTH_BAR_WIDTH + BG_PADDING * 2, HEALTH_BAR_HEIGHT + BG_PADDING * 2, COLOR_BG, app);
		_healthBg.setLocalTranslation(barX - BG_PADDING, barY - BG_PADDING, 0);
		_hudNode.attachChild(_healthBg);
		
		// Fill (scaled dynamically).
		_healthFillMat = createColorMaterial(COLOR_HEALTH, app);
		_healthFill = createQuadWithMaterial("HealthFill", HEALTH_BAR_WIDTH, HEALTH_BAR_HEIGHT, _healthFillMat);
		_healthFill.setLocalTranslation(barX, barY, 0.1f);
		_hudNode.attachChild(_healthFill);
		
		// Text (shadow + foreground).
		_healthTextShadow = createText("HP: 20/20", COLOR_TEXT_SHADOW);
		_healthText = createText("HP: 20/20", COLOR_TEXT);
		
		positionBarText(_healthText, _healthTextShadow, barX, barY, HEALTH_BAR_WIDTH, HEALTH_BAR_HEIGHT);
		
		_hudNode.attachChild(_healthTextShadow);
		_hudNode.attachChild(_healthText);
	}
	
	private void buildAirMeter()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		final float barX = EDGE_PADDING;
		final float barY = _screenHeight - EDGE_PADDING - HEALTH_BAR_HEIGHT - BAR_SPACING - BG_PADDING * 2 - AIR_BAR_HEIGHT;
		
		// Background.
		_airBgMat = createColorMaterial(COLOR_BG, app);
		_airBg = createQuadWithMaterial("AirBg", AIR_BAR_WIDTH + BG_PADDING * 2, AIR_BAR_HEIGHT + BG_PADDING * 2, _airBgMat);
		_airBg.setLocalTranslation(barX - BG_PADDING, barY - BG_PADDING, 0);
		_hudNode.attachChild(_airBg);
		
		// Fill.
		_airFillMat = createColorMaterial(COLOR_AIR, app);
		_airFill = createQuadWithMaterial("AirFill", AIR_BAR_WIDTH, AIR_BAR_HEIGHT, _airFillMat);
		_airFill.setLocalTranslation(barX, barY, 0.1f);
		_hudNode.attachChild(_airFill);
		
		// Text.
		_airTextShadow = createText("Air: 10/10", COLOR_TEXT_SHADOW);
		_airText = createText("Air: 10/10", COLOR_TEXT);
		
		positionBarText(_airText, _airTextShadow, barX, barY, AIR_BAR_WIDTH, AIR_BAR_HEIGHT);
		
		_hudNode.attachChild(_airTextShadow);
		_hudNode.attachChild(_airText);
		
		// Start invisible.
		_airAlpha = 0;
		setAirAlpha(0);
	}
	
	/**
	 * Builds the 9-slot hotbar at the bottom center of the screen.
	 */
	private void buildHotbar()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		
		final float totalWidth = HOTBAR_SIZE * _hotbarSlotSize + (HOTBAR_SIZE - 1) * HOTBAR_SLOT_SPACING;
		final float startX = (_screenWidth - totalWidth) / 2f;
		_hotbarSlotY = EDGE_PADDING;
		
		for (int i = 0; i < HOTBAR_SIZE; i++)
		{
			_hotbarSlotX[i] = startX + i * (_hotbarSlotSize + HOTBAR_SLOT_SPACING);
			
			// Background quad (slightly larger for border effect).
			final float bgSize = _hotbarSlotSize + HOTBAR_BG_PADDING * 2;
			_hotbarBg[i] = createQuad("HotbarBg" + i, bgSize, bgSize, COLOR_HOTBAR_BG, app);
			_hotbarBg[i].setLocalTranslation(_hotbarSlotX[i] - HOTBAR_BG_PADDING, _hotbarSlotY - HOTBAR_BG_PADDING, 0);
			_hudNode.attachChild(_hotbarBg[i]);
			
			// Fill quad (colored by item type).
			_hotbarFillMat[i] = createColorMaterial(COLOR_HOTBAR_EMPTY, app);
			_hotbarFill[i] = createQuadWithMaterial("HotbarFill" + i, _hotbarSlotSize, _hotbarSlotSize, _hotbarFillMat[i]);
			_hotbarFill[i].setLocalTranslation(_hotbarSlotX[i], _hotbarSlotY, 0.1f);
			_hudNode.attachChild(_hotbarFill[i]);
			
			// Label text (type indicator: W, P, A, S, +).
			_hotbarLabel[i] = new BitmapText(_font);
			_hotbarLabel[i].setText("");
			_hotbarLabel[i].setSize(_font.getCharSet().getRenderedSize() * 1.2f);
			_hotbarLabel[i].setColor(COLOR_TEXT.clone());
			_hotbarLabel[i].setCullHint(BitmapText.CullHint.Always);
			_hudNode.attachChild(_hotbarLabel[i]);
			
			// Count text (bottom-right of slot).
			_hotbarCountShadow[i] = new BitmapText(_font);
			_hotbarCountShadow[i].setText("");
			_hotbarCountShadow[i].setSize(_font.getCharSet().getRenderedSize());
			_hotbarCountShadow[i].setColor(COLOR_TEXT_SHADOW.clone());
			_hotbarCountShadow[i].setCullHint(BitmapText.CullHint.Always);
			_hudNode.attachChild(_hotbarCountShadow[i]);
			
			_hotbarCount[i] = new BitmapText(_font);
			_hotbarCount[i].setText("");
			_hotbarCount[i].setSize(_font.getCharSet().getRenderedSize());
			_hotbarCount[i].setColor(COLOR_TEXT.clone());
			_hotbarCount[i].setCullHint(BitmapText.CullHint.Always);
			_hudNode.attachChild(_hotbarCount[i]);
			
			// Durability bar (bottom of slot, hidden by default).
			_hotbarDurMat[i] = createColorMaterial(COLOR_DURABILITY_GREEN, app);
			_hotbarDurBar[i] = createQuadWithMaterial("HotbarDur" + i, _hotbarSlotSize, DURABILITY_BAR_HEIGHT, _hotbarDurMat[i]);
			_hotbarDurBar[i].setLocalTranslation(_hotbarSlotX[i], _hotbarSlotY, 0.2f);
			_hotbarDurBar[i].setCullHint(Geometry.CullHint.Always);
			_hudNode.attachChild(_hotbarDurBar[i]);
		}
		
		// Selection highlight — bright border quad drawn behind the selected slot.
		final float hlSize = _hotbarSlotSize + HOTBAR_BG_PADDING * 2 + 2;
		_hotbarHighlightMat = createColorMaterial(COLOR_HOTBAR_HIGHLIGHT, app);
		_hotbarHighlight = createQuadWithMaterial("HotbarHighlight", hlSize, hlSize, _hotbarHighlightMat);
		_hotbarHighlight.setLocalTranslation(0, 0, -0.1f);
		_hudNode.attachChild(_hotbarHighlight);
	}
	
	private void buildBreakProgress()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		
		// Centered below crosshair.
		final float barX = (_screenWidth - BREAK_BAR_WIDTH) / 2f;
		final float barY = _screenHeight / 2f - BREAK_BAR_OFFSET_Y - BREAK_BAR_HEIGHT;
		
		_breakNode = new Node("BreakProgress");
		
		// Background.
		_breakBg = createQuad("BreakBg", BREAK_BAR_WIDTH + BG_PADDING * 2, BREAK_BAR_HEIGHT + BG_PADDING * 2, COLOR_BREAK_BG, app);
		_breakBg.setLocalTranslation(barX - BG_PADDING, barY - BG_PADDING, 0);
		_breakNode.attachChild(_breakBg);
		
		// Fill.
		_breakFillMat = createColorMaterial(COLOR_BREAK_FILL, app);
		_breakFill = createQuadWithMaterial("BreakFill", BREAK_BAR_WIDTH, BREAK_BAR_HEIGHT, _breakFillMat);
		_breakFill.setLocalTranslation(barX, barY, 0.1f);
		_breakNode.attachChild(_breakFill);
		
		// Text (above the bar).
		_breakTextShadow = createText("", COLOR_TEXT_SHADOW);
		_breakText = createText("", COLOR_TEXT);
		_breakNode.attachChild(_breakTextShadow);
		_breakNode.attachChild(_breakText);
		
		// Hidden initially.
		_breakNode.setCullHint(Node.CullHint.Always);
		_hudNode.attachChild(_breakNode);
	}
	
	/**
	 * Builds the death screen overlay (hidden initially).
	 */
	private void buildDeathScreen()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		
		_deathScreenNode = new Node("DeathScreen");
		
		// Full-screen dark red overlay.
		final Quad overlayQuad = new Quad(_screenWidth, _screenHeight);
		final Geometry overlay = new Geometry("DeathOverlay", overlayQuad);
		final Material overlayMat = createColorMaterial(COLOR_DEATH_OVERLAY, app);
		overlay.setMaterial(overlayMat);
		overlay.setQueueBucket(Bucket.Gui);
		overlay.setLocalTranslation(0, 0, 20);
		_deathScreenNode.attachChild(overlay);
		
		// "You Died" title — large font.
		final int titleSize = Math.max(24, (int) (_screenHeight * 0.06f));
		final BitmapFont titleFont = FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_LINOCUT_PATH, Font.PLAIN, titleSize);
		
		_deathTitleShadow = new BitmapText(titleFont);
		_deathTitleShadow.setText("You Died");
		_deathTitleShadow.setSize(titleSize);
		_deathTitleShadow.setColor(new ColorRGBA(0, 0, 0, 0.9f));
		_deathScreenNode.attachChild(_deathTitleShadow);
		
		_deathTitle = new BitmapText(titleFont);
		_deathTitle.setText("You Died");
		_deathTitle.setSize(titleSize);
		_deathTitle.setColor(COLOR_DEATH_TITLE);
		_deathScreenNode.attachChild(_deathTitle);
		
		// Center title.
		float titleWidth = _deathTitle.getLineWidth();
		float titleHeight = _deathTitle.getLineHeight();
		float titleX = (_screenWidth - titleWidth) / 2f;
		float titleY = _screenHeight * 0.6f + titleHeight / 2f;
		_deathTitle.setLocalTranslation(titleX, titleY, 21);
		_deathTitleShadow.setLocalTranslation(titleX + 2, titleY - 2, 20.5f);
		
		// Death cause text — smaller font below title.
		final int causeSize = Math.max(14, (int) (_screenHeight * 0.025f));
		final BitmapFont causeFont = FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_REGULAR_PATH, Font.PLAIN, causeSize);
		
		_deathCauseShadow = new BitmapText(causeFont);
		_deathCauseShadow.setText("");
		_deathCauseShadow.setSize(causeSize);
		_deathCauseShadow.setColor(new ColorRGBA(0, 0, 0, 0.8f));
		_deathScreenNode.attachChild(_deathCauseShadow);
		
		_deathCauseText = new BitmapText(causeFont);
		_deathCauseText.setText("");
		_deathCauseText.setSize(causeSize);
		_deathCauseText.setColor(COLOR_DEATH_CAUSE);
		_deathScreenNode.attachChild(_deathCauseText);
		
		// "Click to Respawn" prompt — below cause text.
		_deathPromptShadow = new BitmapText(causeFont);
		_deathPromptShadow.setText("Click to Respawn");
		_deathPromptShadow.setSize(causeSize);
		_deathPromptShadow.setColor(new ColorRGBA(0, 0, 0, 0.8f));
		_deathScreenNode.attachChild(_deathPromptShadow);
		
		_deathPrompt = new BitmapText(causeFont);
		_deathPrompt.setText("Click to Respawn");
		_deathPrompt.setSize(causeSize);
		_deathPrompt.setColor(COLOR_DEATH_PROMPT);
		_deathScreenNode.attachChild(_deathPrompt);
		
		float promptWidth = _deathPrompt.getLineWidth();
		float promptY = titleY - titleHeight - _screenHeight * 0.12f;
		float promptX = (_screenWidth - promptWidth) / 2f;
		_deathPrompt.setLocalTranslation(promptX, promptY, 21);
		_deathPromptShadow.setLocalTranslation(promptX + 1, promptY - 1, 20.5f);
		
		// Hidden initially.
		_deathScreenNode.setCullHint(Node.CullHint.Always);
		_hudNode.attachChild(_deathScreenNode);
	}
	
	// ========================================================
	// Inventory Setter.
	// ========================================================
	
	/**
	 * Sets the inventory reference used to populate hotbar slots.
	 * @param inventory the player inventory
	 */
	public void setInventory(Inventory inventory)
	{
		_inventory = inventory;
	}
	
	// ========================================================
	// Update.
	// ========================================================
	
	/**
	 * Updates all HUD elements with current player state. Call each frame.
	 * @param health current player health
	 * @param maxHealth maximum player health
	 * @param air current air supply in seconds
	 * @param maxAir maximum air supply in seconds
	 * @param headSubmerged true if the player's head is underwater
	 * @param hitsDelivered number of hits dealt to the current target
	 * @param hitsRequired total hits needed to break the current target
	 * @param isBreaking true if the player is actively breaking a block
	 * @param showCrosshair true if the crosshair should be visible
	 */
	public void update(float health, float maxHealth, float air, float maxAir, boolean headSubmerged, int hitsDelivered, int hitsRequired, boolean isBreaking, boolean showCrosshair)
	{
		final float tpf = SimpleCraft.getInstance().getTimer().getTimePerFrame();
		
		updateHealthBar(health, maxHealth);
		updateAirMeter(air, maxAir, headSubmerged, tpf);
		updateHotbar();
		updateBreakProgress(hitsDelivered, hitsRequired, isBreaking);
		
		// Toggle crosshair visibility based on settings (hidden when dead).
		final boolean showCross = showCrosshair && !_deathScreenVisible;
		_crosshairText.setCullHint(showCross ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
		
		// Animate death screen prompt pulse.
		if (_deathScreenVisible)
		{
			_deathPromptPulse += tpf;
			final float pulse = (float) (Math.sin(_deathPromptPulse * 2.0) * 0.3 + 0.7);
			_tempColor.set(COLOR_DEATH_PROMPT.r, COLOR_DEATH_PROMPT.g, COLOR_DEATH_PROMPT.b, pulse);
			_deathPrompt.setColor(_tempColor.clone());
		}
	}
	
	// ---- Health bar ----
	
	private void updateHealthBar(float health, float maxHealth)
	{
		final float ratio = maxHealth > 0 ? Math.max(0, Math.min(1, health / maxHealth)) : 0;
		
		// Scale fill width.
		_healthFill.setLocalScale(ratio, 1, 1);
		
		// Color shifts to darker red when health is low (below 30%).
		if (ratio < 0.3f)
		{
			_healthFillMat.setColor("Color", COLOR_HEALTH_LOW);
		}
		else
		{
			_healthFillMat.setColor("Color", COLOR_HEALTH);
		}
		
		// Update text.
		final String text = "HP: " + (int) Math.ceil(health) + "/" + (int) maxHealth;
		_healthText.setText(text);
		_healthTextShadow.setText(text);
	}
	
	// ---- Air meter ----
	
	private void updateAirMeter(float air, float maxAir, boolean headSubmerged, float tpf)
	{
		// Fade logic: instant fade-in when submerging, delayed fade-out after surfacing.
		if (headSubmerged)
		{
			_airAlpha = Math.min(1.0f, _airAlpha + tpf / AIR_FADE_DURATION);
			_airFadeTimer = AIR_FADE_DELAY;
			_wasSubmerged = true;
		}
		else if (_wasSubmerged)
		{
			// Count down delay before fading out.
			_airFadeTimer -= tpf;
			if (_airFadeTimer <= 0)
			{
				_airAlpha = Math.max(0, _airAlpha - tpf / AIR_FADE_DURATION);
				if (_airAlpha <= 0)
				{
					_wasSubmerged = false;
				}
			}
		}
		
		setAirAlpha(_airAlpha);
		
		if (_airAlpha <= 0)
		{
			return; // Skip fill/text updates when invisible.
		}
		
		// Fill ratio.
		final float ratio = maxAir > 0 ? Math.max(0, Math.min(1, air / maxAir)) : 0;
		_airFill.setLocalScale(ratio, 1, 1);
		
		// Flash red when air is critically low.
		if (air < AIR_WARNING_THRESHOLD && air > 0)
		{
			_airFlashTimer += tpf;
			final float flash = (float) (Math.sin(_airFlashTimer * AIR_FLASH_RATE * Math.PI * 2) * 0.5 + 0.5);
			_tempColor.interpolateLocal(COLOR_AIR, COLOR_AIR_WARNING, flash);
			_tempColor.a = _airAlpha;
			_airFillMat.setColor("Color", _tempColor);
		}
		else if (air <= 0)
		{
			// Solid red when out of air.
			_tempColor.set(COLOR_AIR_WARNING);
			_tempColor.a = _airAlpha;
			_airFillMat.setColor("Color", _tempColor);
		}
		else
		{
			_airFlashTimer = 0;
			_tempColor.set(COLOR_AIR);
			_tempColor.a = _airAlpha;
			_airFillMat.setColor("Color", _tempColor);
		}
		
		// Update text.
		final String text = "Air: " + (int) Math.ceil(air) + "/" + (int) maxAir;
		_airText.setText(text);
		_airTextShadow.setText(text);
	}
	
	private void setAirAlpha(float alpha)
	{
		if (alpha <= 0.001f)
		{
			_airBg.setCullHint(Geometry.CullHint.Always);
			_airFill.setCullHint(Geometry.CullHint.Always);
			_airText.setCullHint(BitmapText.CullHint.Always);
			_airTextShadow.setCullHint(BitmapText.CullHint.Always);
		}
		else
		{
			_airBg.setCullHint(Geometry.CullHint.Never);
			_airFill.setCullHint(Geometry.CullHint.Never);
			_airText.setCullHint(BitmapText.CullHint.Never);
			_airTextShadow.setCullHint(BitmapText.CullHint.Never);
			
			// Apply alpha to background.
			_tempColor.set(COLOR_BG);
			_tempColor.a = COLOR_BG.a * alpha;
			_airBgMat.setColor("Color", _tempColor);
			
			// Text alpha.
			_tempColor.set(COLOR_TEXT);
			_tempColor.a = alpha;
			_airText.setColor(_tempColor.clone());
			
			_tempColor.set(COLOR_TEXT_SHADOW);
			_tempColor.a = COLOR_TEXT_SHADOW.a * alpha;
			_airTextShadow.setColor(_tempColor.clone());
		}
	}
	
	// ---- Hotbar ----
	
	/**
	 * Updates all 9 hotbar slots and the selection highlight from the inventory.
	 */
	private void updateHotbar()
	{
		if (_inventory == null)
		{
			return;
		}
		
		for (int i = 0; i < HOTBAR_SIZE; i++)
		{
			final ItemInstance stack = _inventory.getSlot(i);
			updateHotbarSlot(i, stack);
		}
		
		// Update selection highlight position.
		final int selectedIndex = _inventory.getSelectedHotbarIndex();
		if (selectedIndex != _lastSelectedIndex)
		{
			_lastSelectedIndex = selectedIndex;
			// final float hlSize = _hotbarSlotSize + HOTBAR_BG_PADDING * 2 + 2;
			final float hlX = _hotbarSlotX[selectedIndex] - HOTBAR_BG_PADDING - 1;
			final float hlY = _hotbarSlotY - HOTBAR_BG_PADDING - 1;
			_hotbarHighlight.setLocalTranslation(hlX, hlY, -0.1f);
		}
	}
	
	/**
	 * Updates the visual representation of a single hotbar slot.
	 */
	private void updateHotbarSlot(int index, ItemInstance stack)
	{
		if (stack == null || stack.isEmpty())
		{
			_hotbarFillMat[index].setColor("Color", COLOR_HOTBAR_EMPTY);
			_hotbarLabel[index].setCullHint(BitmapText.CullHint.Always);
			_hotbarCount[index].setCullHint(BitmapText.CullHint.Always);
			_hotbarCountShadow[index].setCullHint(BitmapText.CullHint.Always);
			_hotbarDurBar[index].setCullHint(Geometry.CullHint.Always);
			return;
		}
		
		final ItemTemplate item = stack.getTemplate();
		
		// Fill color based on item type.
		final ColorRGBA fillColor = InventoryScreen.getItemColor(item);
		_hotbarFillMat[index].setColor("Color", fillColor);
		
		// Label (type indicator letter).
		final String label = InventoryScreen.getItemLabel(item);
		if (label != null && !label.isEmpty())
		{
			_hotbarLabel[index].setText(label);
			final float labelWidth = _hotbarLabel[index].getLineWidth();
			final float labelHeight = _hotbarLabel[index].getLineHeight();
			final float labelX = _hotbarSlotX[index] + (_hotbarSlotSize - labelWidth) / 2f;
			final float labelY = _hotbarSlotY + (_hotbarSlotSize + labelHeight) / 2f;
			_hotbarLabel[index].setLocalTranslation(labelX, labelY, 0.3f);
			_hotbarLabel[index].setCullHint(BitmapText.CullHint.Never);
		}
		else
		{
			_hotbarLabel[index].setCullHint(BitmapText.CullHint.Always);
		}
		
		// Count (shown if count > 1).
		if (stack.getCount() > 1)
		{
			final String countStr = String.valueOf(stack.getCount());
			_hotbarCount[index].setText(countStr);
			_hotbarCountShadow[index].setText(countStr);
			
			final float countWidth = _hotbarCount[index].getLineWidth();
			final float countX = _hotbarSlotX[index] + _hotbarSlotSize - countWidth - 2;
			final float countY = _hotbarSlotY + _hotbarCount[index].getLineHeight() + 1;
			_hotbarCount[index].setLocalTranslation(countX, countY, 0.3f);
			_hotbarCountShadow[index].setLocalTranslation(countX + 1, countY - 1, 0.2f);
			_hotbarCount[index].setCullHint(BitmapText.CullHint.Never);
			_hotbarCountShadow[index].setCullHint(BitmapText.CullHint.Never);
		}
		else
		{
			_hotbarCount[index].setCullHint(BitmapText.CullHint.Always);
			_hotbarCountShadow[index].setCullHint(BitmapText.CullHint.Always);
		}
		
		// Durability bar.
		if (stack.hasDurability())
		{
			final float percent = stack.getDurabilityPercent();
			_hotbarDurBar[index].setLocalScale(percent, 1, 1);
			
			if (percent > 0.6f)
			{
				_hotbarDurMat[index].setColor("Color", COLOR_DURABILITY_GREEN);
			}
			else if (percent > 0.3f)
			{
				_hotbarDurMat[index].setColor("Color", COLOR_DURABILITY_YELLOW);
			}
			else
			{
				_hotbarDurMat[index].setColor("Color", COLOR_DURABILITY_RED);
			}
			
			_hotbarDurBar[index].setCullHint(Geometry.CullHint.Never);
		}
		else
		{
			_hotbarDurBar[index].setCullHint(Geometry.CullHint.Always);
		}
	}
	
	// ---- Break progress ----
	
	private void updateBreakProgress(int hitsDelivered, int hitsRequired, boolean isBreaking)
	{
		if (!isBreaking || hitsDelivered <= 0 || hitsRequired <= 0)
		{
			// Hide progress bar.
			if (_breakNode.getCullHint() != Node.CullHint.Always)
			{
				_breakNode.setCullHint(Node.CullHint.Always);
			}
			return;
		}
		
		// Show progress bar.
		if (_breakNode.getCullHint() != Node.CullHint.Never)
		{
			_breakNode.setCullHint(Node.CullHint.Never);
		}
		
		// Scale fill (inverted — full bar = full durability, shrinks as hits land).
		final float ratio = Math.max(0, 1.0f - (float) hitsDelivered / hitsRequired);
		_breakFill.setLocalScale(ratio, 1, 1);
		
		// Update text.
		final Block targetBlock = SimpleCraft.getInstance().getStateManager().getState(PlayingState.class) != null ? getBreakingBlockFromInteraction() : Block.AIR;
		final String blockName = targetBlock != null ? targetBlock.getDisplayName() : "Block";
		final int remaining = hitsRequired - hitsDelivered;
		final String text = blockName + " " + remaining + "/" + hitsRequired;
		_breakText.setText(text);
		_breakTextShadow.setText(text);
		
		// Position text centered above the bar.
		final float barX = (_screenWidth - BREAK_BAR_WIDTH) / 2f;
		final float barY = _screenHeight / 2f - BREAK_BAR_OFFSET_Y - BREAK_BAR_HEIGHT;
		final float textX = barX + (BREAK_BAR_WIDTH - _breakText.getLineWidth()) / 2f;
		final float textY = barY + BREAK_BAR_HEIGHT + BG_PADDING + _breakText.getLineHeight() + 2;
		
		_breakText.setLocalTranslation(textX, textY, 0.2f);
		_breakTextShadow.setLocalTranslation(textX + 1, textY - 1, 0.1f);
	}
	
	/** Cached reference to BlockInteraction for target block lookup. */
	private BlockInteraction _blockInteraction;
	
	/**
	 * Sets the BlockInteraction reference for target block name lookup.
	 * @param blockInteraction the block interaction handler
	 */
	public void setBlockInteraction(BlockInteraction blockInteraction)
	{
		_blockInteraction = blockInteraction;
	}
	
	private Block getBreakingBlockFromInteraction()
	{
		if (_blockInteraction != null)
		{
			return _blockInteraction.getTargetBlock();
		}
		return Block.AIR;
	}
	
	// ========================================================
	// Death Screen.
	// ========================================================
	
	/**
	 * Shows the death screen overlay with the given cause of death.
	 * @param deathCause description of how the player died (e.g. "Killed by Zombie")
	 */
	public void showDeathScreen(String deathCause)
	{
		if (_deathScreenVisible)
		{
			return;
		}
		
		_deathScreenVisible = true;
		_deathPromptPulse = 0;
		
		// Set cause text and center it.
		final String cause = deathCause != null && !deathCause.isEmpty() ? deathCause : "You died";
		_deathCauseText.setText(cause);
		_deathCauseShadow.setText(cause);
		
		float causeWidth = _deathCauseText.getLineWidth();
		float causeX = (_screenWidth - causeWidth) / 2f;
		
		// Position below the title.
		float titleY = _screenHeight * 0.6f;
		float causeY = titleY - _deathTitle.getLineHeight() - _screenHeight * 0.04f;
		_deathCauseText.setLocalTranslation(causeX, causeY, 21);
		_deathCauseShadow.setLocalTranslation(causeX + 1, causeY - 1, 20.5f);
		
		_deathScreenNode.setCullHint(Node.CullHint.Never);
	}
	
	/**
	 * Hides the death screen overlay.
	 */
	public void hideDeathScreen()
	{
		if (!_deathScreenVisible)
		{
			return;
		}
		
		_deathScreenVisible = false;
		_deathScreenNode.setCullHint(Node.CullHint.Always);
	}
	
	/**
	 * Returns true if the death screen is currently visible.
	 */
	public boolean isDeathScreenVisible()
	{
		return _deathScreenVisible;
	}
	
	// ========================================================
	// Cleanup.
	// ========================================================
	
	/**
	 * Removes all HUD elements from the GUI node.
	 */
	public void cleanup()
	{
		_guiNode.detachChild(_hudNode);
	}
	
	// ========================================================
	// Helper Methods.
	// ========================================================
	
	/**
	 * Creates a colored quad geometry with alpha blending.
	 */
	private Geometry createQuad(String name, float width, float height, ColorRGBA color, SimpleCraft app)
	{
		final Material mat = createColorMaterial(color, app);
		return createQuadWithMaterial(name, width, height, mat);
	}
	
	/**
	 * Creates a quad geometry with the given material.
	 */
	private Geometry createQuadWithMaterial(String name, float width, float height, Material mat)
	{
		final Quad quad = new Quad(width, height);
		final Geometry geom = new Geometry(name, quad);
		geom.setMaterial(mat);
		geom.setQueueBucket(Bucket.Gui);
		return geom;
	}
	
	/**
	 * Creates an unshaded material with the given color and alpha blending enabled.
	 */
	private Material createColorMaterial(ColorRGBA color, SimpleCraft app)
	{
		final Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
		mat.setColor("Color", color.clone());
		mat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
		return mat;
	}
	
	/**
	 * Creates a BitmapText with the given initial text and color.
	 */
	private BitmapText createText(String text, ColorRGBA color)
	{
		final BitmapText bt = new BitmapText(_font);
		bt.setText(text);
		bt.setSize(_font.getCharSet().getRenderedSize());
		bt.setColor(color.clone());
		return bt;
	}
	
	/**
	 * Positions a text and its shadow centered within a bar area.
	 */
	private void positionBarText(BitmapText text, BitmapText shadow, float barX, float barY, float barWidth, float barHeight)
	{
		final float textX = barX + (barWidth - text.getLineWidth()) / 2f;
		final float textY = barY + barHeight / 2f + text.getLineHeight() / 2f;
		
		text.setLocalTranslation(textX, textY, 0.2f);
		shadow.setLocalTranslation(textX + 1, textY - 1, 0.1f);
	}
}
