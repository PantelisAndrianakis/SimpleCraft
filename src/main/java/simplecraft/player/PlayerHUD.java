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
import simplecraft.ui.FontManager;
import simplecraft.world.Block;

/**
 * In-game heads-up display showing health, air, selected block, crosshair,<br>
 * and block-breaking progress.<br>
 * <br>
 * All elements are attached to the application's GUI node (screen-space overlay).<br>
 * Call {@link #update(float, float, float, float, boolean, Block, int, int, boolean)} each frame<br>
 * with current player state, and {@link #cleanup()} when leaving the playing state.<br>
 * <br>
 * <b>Crosshair:</b> Small "+" at screen center, white with slight transparency.<br>
 * <b>Health bar:</b> Bottom-left, red fill proportional to health. Dark background.<br>
 * <b>Air meter:</b> Blue bar above health, visible only when submerged. Fades in/out<br>
 * over 0.5 seconds. Flashes red when air drops below 3 seconds.<br>
 * <b>Selected block:</b> Bottom-center, colored square with block name text.<br>
 * <b>Break progress:</b> Small bar below crosshair showing "BlockName 3/8" while breaking.
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
	
	/** Selected block indicator square size in pixels. */
	private static final float BLOCK_INDICATOR_SIZE = 32f;
	
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
	// private Material _airTextMat; // Not used directly — BitmapText uses setColor.
	
	// Air fade state.
	private float _airAlpha;
	private float _airFadeTimer;
	private boolean _wasSubmerged;
	private float _airFlashTimer;
	
	// Selected block display.
	private Geometry _blockIndicatorBg;
	private Geometry _blockIndicator;
	private BitmapText _blockNameText;
	private BitmapText _blockNameTextShadow;
	private Material _blockIndicatorMat;
	
	// Break progress.
	private Geometry _breakBg;
	private Geometry _breakFill;
	private BitmapText _breakText;
	private BitmapText _breakTextShadow;
	private Material _breakFillMat;
	private Node _breakNode;
	
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
		
		// Container node for easy cleanup.
		_hudNode = new Node("PlayerHUD");
		
		// Build all elements.
		buildCrosshair();
		buildHealthBar();
		buildAirMeter();
		buildSelectedBlock();
		buildBreakProgress();
		
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
	
	private void buildSelectedBlock()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		
		// Bottom-center of screen.
		final float centerX = _screenWidth / 2f;
		final float indicatorX = centerX - BLOCK_INDICATOR_SIZE / 2f;
		final float indicatorY = EDGE_PADDING;
		
		// Background (slightly larger).
		_blockIndicatorBg = createQuad("BlockIndicatorBg", BLOCK_INDICATOR_SIZE + BG_PADDING * 2, BLOCK_INDICATOR_SIZE + BG_PADDING * 2, COLOR_BG, app);
		_blockIndicatorBg.setLocalTranslation(indicatorX - BG_PADDING, indicatorY - BG_PADDING, 0);
		_hudNode.attachChild(_blockIndicatorBg);
		
		// Colored square.
		_blockIndicatorMat = createColorMaterial(new ColorRGBA(0.6f, 0.4f, 0.2f, 1.0f), app);
		_blockIndicator = createQuadWithMaterial("BlockIndicator", BLOCK_INDICATOR_SIZE, BLOCK_INDICATOR_SIZE, _blockIndicatorMat);
		_blockIndicator.setLocalTranslation(indicatorX, indicatorY, 0.1f);
		_hudNode.attachChild(_blockIndicator);
		
		// Block name text (positioned to the right of the indicator).
		_blockNameTextShadow = createText("Dirt", COLOR_TEXT_SHADOW);
		_blockNameText = createText("Dirt", COLOR_TEXT);
		
		final float textX = indicatorX + BLOCK_INDICATOR_SIZE + BG_PADDING + 6;
		final float textY = indicatorY + BLOCK_INDICATOR_SIZE / 2f + _blockNameText.getLineHeight() / 4f;
		_blockNameTextShadow.setLocalTranslation(textX + 1, textY - 1, 0.1f);
		_blockNameText.setLocalTranslation(textX, textY, 0.2f);
		
		_hudNode.attachChild(_blockNameTextShadow);
		_hudNode.attachChild(_blockNameText);
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
	 * @param selectedBlock the currently selected block for placement
	 * @param hitsDelivered number of hits dealt to the current target
	 * @param hitsRequired total hits needed to break the current target
	 * @param isBreaking true if the player is actively breaking a block
	 */
	public void update(float health, float maxHealth, float air, float maxAir, boolean headSubmerged, Block selectedBlock, int hitsDelivered, int hitsRequired, boolean isBreaking, boolean showCrosshair)
	{
		final float tpf = SimpleCraft.getInstance().getTimer().getTimePerFrame();
		
		updateHealthBar(health, maxHealth);
		updateAirMeter(air, maxAir, headSubmerged, tpf);
		updateSelectedBlock(selectedBlock);
		updateBreakProgress(hitsDelivered, hitsRequired, isBreaking);
		
		// Toggle crosshair visibility based on settings.
		_crosshairText.setCullHint(showCrosshair ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
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
	
	// ---- Selected block ----
	
	/** Tracks the last displayed block to avoid redundant updates. */
	private Block _lastSelectedBlock;
	
	private void updateSelectedBlock(Block selectedBlock)
	{
		if (selectedBlock == _lastSelectedBlock)
		{
			return;
		}
		
		_lastSelectedBlock = selectedBlock;
		
		// Update block indicator color based on block type.
		final ColorRGBA blockColor = getBlockColor(selectedBlock);
		_blockIndicatorMat.setColor("Color", blockColor);
		
		// Update name text with title-cased name.
		final String displayName = formatBlockName(selectedBlock);
		_blockNameText.setText(displayName);
		_blockNameTextShadow.setText(displayName);
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
		final Block targetBlock = SimpleCraft.getInstance().getStateManager().getState(simplecraft.state.PlayingState.class) != null ? getBreakingBlockFromInteraction() : Block.AIR;
		final String blockName = targetBlock != null && targetBlock != Block.AIR ? formatBlockName(targetBlock) : "Block";
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
	
	/**
	 * Returns a representative color for a block type (used for the indicator square).
	 */
	private static ColorRGBA getBlockColor(Block block)
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
			default:
			{
				return new ColorRGBA(0.5f, 0.5f, 0.5f, 1.0f);
			}
		}
	}
	
	/**
	 * Formats a block enum name into title case (e.g. CRAFTING_TABLE → "Crafting Table").
	 */
	private static String formatBlockName(Block block)
	{
		if (block == null)
		{
			return "None";
		}
		
		// GRASS is mentioned as DIRT, all other blocks keep their names.
		final String[] words = block == Block.GRASS ? Block.DIRT.name().toLowerCase().split("_") : block.name().toLowerCase().split("_");
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < words.length; i++)
		{
			if (i > 0)
			{
				sb.append(' ');
			}
			sb.append(Character.toUpperCase(words[i].charAt(0)));
			sb.append(words[i].substring(1));
		}
		
		return sb.toString();
	}
}
