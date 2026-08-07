package simplecraft.state;

import java.awt.Font;

import com.jme3.asset.AssetManager;
import com.jme3.app.Application;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.ui.Picture;

import com.simsilica.lemur.Container;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.Panel;

import simplecraft.SimpleCraft;
import simplecraft.audio.AudioManager;
import simplecraft.audio.MusicManager;
import simplecraft.input.MenuNavigationManager;
import simplecraft.settings.LanguageManager;
import simplecraft.settings.MouseSensitivityManager;
import simplecraft.state.GameStateManager.GameState;
import simplecraft.ui.ButtonManager;
import simplecraft.ui.FontManager;
import simplecraft.ui.QuestionManager;

/**
 * Main menu state with title and navigation buttons.<br>
 * Provides Start Game, Options and Exit functionality with SFX feedback.<br>
 * Supports keyboard navigation via WASD and arrow keys.
 * @author Pantelis Andrianakis
 * @since February 17th 2026
 */
public class MainMenuState extends FadeableAppState
{
	private static final int LOGO_SIZE = 128;
	private static final int LOGO_TITLE_SPACING = 5;
	private static final int BUTTON_SPACING = 22;
	private static final int VERSION_MARGIN = 10;
	
	private Label _titleLabel;
	private Picture _background;
	private Picture _logo;
	private Container _buttonContainer;
	private Label _versionLabel;
	
	// Keyboard navigation.
	private MenuNavigationManager _navigation;
	
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
		final AssetManager assetManager = app.getAssetManager();
		final AudioManager audioManager = app.getAudioManager();
		final GameStateManager gameStateManager = app.getGameStateManager();
		final Node guiNode = app.getGuiNode();
		
		System.out.println("MainMenuState entered.");
		MouseSensitivityManager.setEnabled(true);
		
		// Screen dimensions.
		final Camera camera = app.getCamera();
		final float screenWidth = camera.getWidth();
		final float screenHeight = camera.getHeight();
		final float screenCenterX = screenWidth / 2f;
		
		// --- Background Image (stretched to fill screen) ---
		_background = new Picture("Menu Background");
		_background.setImage(assetManager, "assets/images/backgrounds/main_menu.png", true);
		_background.setWidth(screenWidth);
		_background.setHeight(screenHeight);
		_background.setLocalTranslation(0, 0, -10);
		_background.setCullHint(Spatial.CullHint.Never);
		
		// --- Title Label ---
		_titleLabel = new Label("SimpleCraft");
		_titleLabel.setFont(FontManager.getFont(assetManager, FontManager.BLUE_HIGHWAY_LINOCUT_PATH, Font.PLAIN, 72));
		_titleLabel.setFontSize(72);
		_titleLabel.setColor(ColorRGBA.White);
		
		final Vector3f titleSize = _titleLabel.getPreferredSize();
		final float titleWidth = titleSize.x;
		final float titleHeight = titleSize.y;
		
		// --- Logo ---
		_logo = new Picture("Menu Logo");
		_logo.setImage(assetManager, "assets/images/app_icons/icon_128.png", true);
		_logo.setWidth(LOGO_SIZE);
		_logo.setHeight(LOGO_SIZE);
		
		// Calculate total title group width (title + spacing + logo) for centering.
		final float titleGroupWidth = titleWidth + LOGO_TITLE_SPACING + LOGO_SIZE;
		
		// --- Button Container ---
		_buttonContainer = new Container();
		_buttonContainer.setBackground(null);
		
		// --- Navigation ---
		_navigation = new MenuNavigationManager();
		
		// Start Game button - transitions to World Select.
		final Runnable startAction = () ->
		{
			audioManager.playSfx(AudioManager.UI_CLICK_SFX_PATH);
			gameStateManager.switchTo(GameState.WORLD_SELECT, true);
		};
		final Panel startButton = ButtonManager.createMenuButtonByScreenPercentage(assetManager, LanguageManager.get("menu.start"), 0.18f, 0.065f, startAction);
		_buttonContainer.addChild(startButton);
		_navigation.addSlot(MenuNavigationManager.buttonSlot(startButton, startAction));
		
		addButtonSpacer();
		
		// Options button.
		final Runnable optionsAction = () ->
		{
			audioManager.playSfx(AudioManager.UI_CLICK_SFX_PATH);
			gameStateManager.switchTo(GameState.OPTIONS, true);
		};
		final Panel optionsButton = ButtonManager.createMenuButtonByScreenPercentage(assetManager, LanguageManager.get("menu.options"), 0.18f, 0.065f, optionsAction);
		_buttonContainer.addChild(optionsButton);
		_navigation.addSlot(MenuNavigationManager.buttonSlot(optionsButton, optionsAction));
		
		addButtonSpacer();
		
		// Exit button.
		final Runnable exitAction = () ->
		{
			audioManager.playSfx(AudioManager.UI_CLICK_SFX_PATH);
			QuestionManager.show(LanguageManager.get("menu.exit_confirm"), () -> app.stop(), null);
		};
		final Panel exitButton = ButtonManager.createMenuButtonByScreenPercentage(assetManager, LanguageManager.get("menu.exit"), 0.18f, 0.065f, exitAction);
		_buttonContainer.addChild(exitButton);
		_navigation.addSlot(MenuNavigationManager.buttonSlot(exitButton, exitAction));
		
		final Vector3f buttonContainerSize = _buttonContainer.getPreferredSize();
		final float buttonContainerWidth = buttonContainerSize.x;
		final float buttonContainerHeight = buttonContainerSize.y;
		
		// --- Layout ---
		// Title group: positioned in upper portion of screen.
		final float titleGroupX = screenCenterX - (titleGroupWidth / 2.3f);
		final float titleY = screenHeight * 0.85f;
		_titleLabel.setLocalTranslation(titleGroupX, titleY, 0);
		
		// Logo: to the right of the title, vertically centered with title.
		_logo.setLocalTranslation((titleGroupX + titleWidth + LOGO_TITLE_SPACING), (titleY - (titleHeight * 0.52f) - (LOGO_SIZE / 2f)), 0);
		
		// Button container: centered on screen independently.
		_buttonContainer.setLocalTranslation((screenCenterX - (buttonContainerWidth / 2f)), ((screenHeight + buttonContainerHeight) / 2f), 0);
		
		// --- Version Label (bottom-right) ---
		_versionLabel = new Label("Dev Edition");
		_versionLabel.setFont(FontManager.getFont(assetManager, FontManager.BLUE_HIGHWAY_REGULAR_PATH, Font.PLAIN, 14));
		_versionLabel.setFontSize(14);
		_versionLabel.setColor(new ColorRGBA(0.6f, 0.6f, 0.6f, 0.8f));
		
		final Vector3f versionSize = _versionLabel.getPreferredSize();
		_versionLabel.setLocalTranslation(screenWidth - versionSize.x - VERSION_MARGIN, versionSize.y + VERSION_MARGIN, 0);
		
		// Attach all elements to the GUI node (background first so it's behind everything).
		guiNode.attachChild(_background);
		guiNode.attachChild(_titleLabel);
		guiNode.attachChild(_logo);
		guiNode.attachChild(_buttonContainer);
		guiNode.attachChild(_versionLabel);
		
		// Register navigation (Escape shows exit confirmation).
		_navigation.setBackAction(() ->
		{
			audioManager.playSfx(AudioManager.UI_CLICK_SFX_PATH);
			QuestionManager.show(LanguageManager.get("menu.exit_confirm"), () -> app.stop(), null);
		});
		_navigation.register();
		
		// Start menu music.
		audioManager.playMusic(MusicManager.DAY_MUSIC_PATH);
	}
	
	@Override
	protected void onExitState()
	{
		final Node guiNode = SimpleCraft.getInstance().getGuiNode();
		MouseSensitivityManager.setEnabled(false);
		
		if (_navigation != null)
		{
			_navigation.unregister();
			_navigation = null;
		}
		
		// Dismiss any active question dialog.
		QuestionManager.dismiss();
		
		// Remove all GUI elements.
		if (_background != null)
		{
			guiNode.detachChild(_background);
			_background = null;
		}
		
		if (_titleLabel != null)
		{
			guiNode.detachChild(_titleLabel);
			_titleLabel = null;
		}
		
		if (_logo != null)
		{
			guiNode.detachChild(_logo);
			_logo = null;
		}
		
		if (_buttonContainer != null)
		{
			guiNode.detachChild(_buttonContainer);
			_buttonContainer = null;
		}
		
		if (_versionLabel != null)
		{
			guiNode.detachChild(_versionLabel);
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
