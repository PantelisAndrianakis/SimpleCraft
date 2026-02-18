package simplecraft.state;

import java.awt.Font;

import com.jme3.app.Application;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.jme3.ui.Picture;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.Label;

import simplecraft.SimpleCraft;
import simplecraft.audio.AudioManager;
import simplecraft.state.GameStateManager.GameState;
import simplecraft.ui.ButtonManager;
import simplecraft.ui.FontManager;
import simplecraft.ui.QuestionManager;

/**
 * Main menu state with title and navigation buttons.<br>
 * Provides Start Game, Options, and Exit functionality with SFX feedback.
 * @author Pantelis Andrianakis
 * @since February 17th 2026
 */
public class MainMenuState extends FadeableAppState
{
	private static final String BACKGROUND_PATH = "assets/images/backgrounds/main_menu.png";
	private static final String TITLE_LOGO_PATH = "assets/images/app_icons/icon_128.png";
	
	private static final int LOGO_SIZE = 128;
	private static final int LOGO_TITLE_SPACING = 5;
	private static final int BUTTON_SPACING = 10;
	private static final int VERSION_MARGIN = 10;
	
	private Label _titleLabel;
	private Picture _background;
	private Picture _logo;
	private Container _buttonContainer;
	private Label _versionLabel;
	
	public MainMenuState()
	{
		setFadeIn(1.0f, new ColorRGBA(0, 0, 0, 1));
		setFadeOut(1.0f, new ColorRGBA(0, 0, 0, 1));
	}
	
	@Override
	protected void initialize(Application app)
	{
		// Initialization happens once when state is first attached.
	}
	
	@Override
	protected void cleanup(Application app)
	{
		// Cleanup happens once when state is permanently detached.
	}
	
	@Override
	protected void onEnterState()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		
		System.out.println("MainMenuState entered.");
		
		// Screen dimensions.
		final float screenWidth = app.getCamera().getWidth();
		final float screenHeight = app.getCamera().getHeight();
		final float screenCenterX = screenWidth / 2f;
		
		// --- Background Image (stretched to fill screen) ---
		_background = new Picture("Menu Background");
		_background.setImage(app.getAssetManager(), BACKGROUND_PATH, true);
		_background.setWidth(screenWidth);
		_background.setHeight(screenHeight);
		_background.setLocalTranslation(0, 0, -10); // Position at bottom-left with negative Z to ensure it's behind everything.
		_background.setCullHint(Spatial.CullHint.Never);
		
		// --- Title Label ---
		_titleLabel = new Label("SimpleCraft");
		_titleLabel.setFont(FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_LINOCUT_PATH, Font.PLAIN, 72));
		_titleLabel.setFontSize(72);
		_titleLabel.setColor(ColorRGBA.White);
		
		final float titleWidth = _titleLabel.getPreferredSize().x;
		final float titleHeight = _titleLabel.getPreferredSize().y;
		
		// --- Logo ---
		_logo = new Picture("Menu Logo");
		_logo.setImage(app.getAssetManager(), TITLE_LOGO_PATH, true);
		_logo.setWidth(LOGO_SIZE);
		_logo.setHeight(LOGO_SIZE);
		
		// Calculate total title group width (title + spacing + logo) for centering.
		final float titleGroupWidth = titleWidth + LOGO_TITLE_SPACING + LOGO_SIZE;
		
		// --- Button Container ---
		_buttonContainer = new Container();
		_buttonContainer.setBackground(null);
		
		_buttonContainer.addChild(ButtonManager.createMenuButtonByScreenPercentage(app.getAssetManager(), "Start", 0.15f, 0.065f, () ->
		{
			app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
			app.getGameStateManager().switchTo(GameState.PLAYING, true);
		}));
		
		addButtonSpacer();
		
		_buttonContainer.addChild(ButtonManager.createMenuButtonByScreenPercentage(app.getAssetManager(), "Options", 0.15f, 0.065f, () ->
		{
			app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
			app.getGameStateManager().switchTo(GameState.OPTIONS, true);
		}));
		
		addButtonSpacer();
		
		_buttonContainer.addChild(ButtonManager.createMenuButtonByScreenPercentage(app.getAssetManager(), "Exit", 0.15f, 0.065f, () ->
		{
			app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
			QuestionManager.show("Exit game?", () -> app.stop(), null);
		}));
		
		final float buttonContainerWidth = _buttonContainer.getPreferredSize().x;
		final float buttonContainerHeight = _buttonContainer.getPreferredSize().y;
		
		// --- Layout ---
		// Title group: positioned in upper portion of screen.
		final float titleGroupX = screenCenterX - (titleGroupWidth / 2.3f);
		final float titleY = screenHeight * 0.85f;
		_titleLabel.setLocalTranslation(titleGroupX, titleY, 0);
		
		// Logo: to the right of the title, vertically centered with title.
		// Lemur title Y is top edge; font renders lower due to internal padding.
		// Offset logo down by ~41% of titleHeight to align with visual text center.
		final float logoX = titleGroupX + titleWidth + LOGO_TITLE_SPACING;
		final float logoY = titleY - (titleHeight * 0.41f) - (LOGO_SIZE / 2f);
		_logo.setLocalTranslation(logoX, logoY, 0);
		
		// Button container: centered on screen independently.
		final float buttonContainerX = screenCenterX - (buttonContainerWidth / 2f);
		final float buttonContainerY = (screenHeight + buttonContainerHeight) / 2f;
		_buttonContainer.setLocalTranslation(buttonContainerX, buttonContainerY, 0);
		
		// --- Version Label (bottom-right) ---
		_versionLabel = new Label("Dev Edition");
		_versionLabel.setFont(FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_REGULAR_PATH, Font.PLAIN, 14));
		_versionLabel.setFontSize(14);
		_versionLabel.setColor(new ColorRGBA(0.6f, 0.6f, 0.6f, 0.8f));
		
		final float versionWidth = _versionLabel.getPreferredSize().x;
		final float versionHeight = _versionLabel.getPreferredSize().y;
		_versionLabel.setLocalTranslation(screenWidth - versionWidth - VERSION_MARGIN, versionHeight + VERSION_MARGIN, 0);
		
		// Attach all elements to the GUI node (background first so it's behind everything).
		app.getGuiNode().attachChild(_background);
		app.getGuiNode().attachChild(_titleLabel);
		app.getGuiNode().attachChild(_logo);
		app.getGuiNode().attachChild(_buttonContainer);
		app.getGuiNode().attachChild(_versionLabel);
		
		// Start menu music.
		app.getAudioManager().playMusic(AudioManager.PERSPECTIVES_MUSIC_PATH);
	}
	
	@Override
	protected void onExitState()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		
		// Dismiss any active question dialog.
		QuestionManager.dismiss();
		
		// Remove all GUI elements.
		if (_background != null)
		{
			app.getGuiNode().detachChild(_background);
			_background = null;
		}
		
		if (_titleLabel != null)
		{
			app.getGuiNode().detachChild(_titleLabel);
			_titleLabel = null;
		}
		
		if (_logo != null)
		{
			app.getGuiNode().detachChild(_logo);
			_logo = null;
		}
		
		if (_buttonContainer != null)
		{
			app.getGuiNode().detachChild(_buttonContainer);
			_buttonContainer = null;
		}
		
		if (_versionLabel != null)
		{
			app.getGuiNode().detachChild(_versionLabel);
			_versionLabel = null;
		}
		
		// Don't stop music when leaving menu - let next state handle music.
	}
	
	/**
	 * Add a transparent spacer between buttons for visual separation.
	 */
	private void addButtonSpacer()
	{
		final Label spacer = new Label("");
		spacer.setPreferredSize(new Vector3f(1, BUTTON_SPACING, 0));
		_buttonContainer.addChild(spacer);
	}
}
