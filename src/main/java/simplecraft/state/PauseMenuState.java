package simplecraft.state;

import java.awt.Font;

import com.jme3.app.Application;
import com.jme3.asset.AssetManager;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.ui.Picture;

import com.simsilica.lemur.Label;
import com.simsilica.lemur.Panel;

import simplecraft.SimpleCraft;
import simplecraft.audio.AudioManager;
import simplecraft.input.MenuNavigationManager;
import simplecraft.settings.LanguageManager;
import simplecraft.settings.MouseSensitivityManager;
import simplecraft.state.GameStateManager.GameState;
import simplecraft.ui.ButtonManager;
import simplecraft.ui.FontManager;
import simplecraft.ui.QuestionManager;

/**
 * Pause menu overlay rendered on top of PlayingState.<br>
 * Background with centered title, logo and buttons.<br>
 * Provides Resume, Options and Quit to Menu with keyboard navigation.<br>
 * Pressing Escape (UI_BACK) resumes the game.
 * @author Pantelis Andrianakis
 * @since February 19th 2026
 */
public class PauseMenuState extends FadeableAppState
{
	// Title layout constants (matching main menu).
	private static final String BACKGROUND_PATH = "assets/images/backgrounds/pause_menu.png";
	private static final String TITLE_LOGO_PATH = "assets/images/app_icons/icon_128.png";
	private static final int LOGO_SIZE = 128;
	private static final int LOGO_TITLE_SPACING = 5;
	
	// Font size ratio relative to screen height (matching main menu proportions).
	private static final float TITLE_FONT_RATIO = 0.065f;
	
	// Button size as percentage of screen.
	private static final float BUTTON_WIDTH_PERCENT = 0.18f;
	private static final float BUTTON_HEIGHT_PERCENT = 0.065f;
	private static final float BUTTON_SPACING_PERCENT = 0.025f;
	
	private static final int BUTTON_COUNT = 3;
	
	// GUI elements.
	private Label _titleLabel;
	private Picture _background;
	private Picture _logo;
	private Panel[] _buttons;
	
	// Keyboard navigation.
	private MenuNavigationManager _navigation;
	
	public PauseMenuState()
	{
		// Set fade in/out with black color (matches MainMenuState style).
		setFadeIn(0.5f, new ColorRGBA(0, 0, 0, 1));
		setFadeOut(0.3f, new ColorRGBA(0, 0, 0, 1));
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
		
		System.out.println("PauseMenuState entered.");
		MouseSensitivityManager.setEnabled(true);
		
		// Show cursor for menu interaction.
		app.getInputManager().setCursorVisible(true);
		
		buildGui();
	}
	
	@Override
	protected void onExitState()
	{
		System.out.println("PauseMenuState exited.");
		MouseSensitivityManager.setEnabled(false);
		
		if (_navigation != null)
		{
			_navigation.unregister();
			_navigation = null;
		}
		
		QuestionManager.dismiss();
		detachAllGui();
	}
	
	// ========== GUI CONSTRUCTION ==========
	
	/**
	 * Build the pause menu background, title, logo and buttons.
	 */
	private void buildGui()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		final Camera camera = app.getCamera();
		final AssetManager assetManager = app.getAssetManager();
		final AudioManager audioManager = app.getAudioManager();
		final Node guiNode = app.getGuiNode();
		final int screenWidth = camera.getWidth();
		final int screenHeight = camera.getHeight();
		final float centerX = screenWidth / 2f;
		
		// --- Background Image (stretched to fill screen) ---
		_background = new Picture("Pause Background");
		_background.setImage(assetManager, BACKGROUND_PATH, true);
		_background.setWidth(screenWidth);
		_background.setHeight(screenHeight);
		_background.setLocalTranslation(0, 0, -10); // Behind everything.
		_background.setCullHint(Spatial.CullHint.Never);
		guiNode.attachChild(_background);
		
		// --- Title label (same as main menu) ---
		final int titleFontSize = Math.max(24, Math.round(screenHeight * TITLE_FONT_RATIO));
		_titleLabel = new Label("SimpleCraft");
		_titleLabel.setFont(FontManager.getFont(assetManager, FontManager.getTitlePath(), Font.PLAIN, titleFontSize));
		_titleLabel.setFontSize(titleFontSize);
		_titleLabel.setColor(ColorRGBA.White);
		_titleLabel.setBackground(null);
		
		final Vector3f titleSize = _titleLabel.getPreferredSize();
		final float titleWidth = titleSize.x;
		final float titleHeight = titleSize.y;
		
		// --- Logo (to the right of title, same as main menu) ---
		_logo = new Picture("Pause Logo");
		_logo.setImage(assetManager, TITLE_LOGO_PATH, true);
		_logo.setWidth(LOGO_SIZE);
		_logo.setHeight(LOGO_SIZE);
		
		// Center the title+logo group horizontally.
		final float titleGroupX = centerX - ((titleWidth + LOGO_TITLE_SPACING + LOGO_SIZE) / 2.3f);
		final float titleY = screenHeight * 0.85f;
		_titleLabel.setLocalTranslation(titleGroupX, titleY, 1);
		guiNode.attachChild(_titleLabel);
		
		// Logo: to the right of title, vertically centered with text.
		_logo.setLocalTranslation((titleGroupX + titleWidth + LOGO_TITLE_SPACING), (titleY - (titleHeight * 0.52f) - (LOGO_SIZE / 2f)), 1);
		guiNode.attachChild(_logo);
		
		// --- Button actions ---
		final Runnable resumeAction = () ->
		{
			audioManager.playSfx(AudioManager.UI_CLICK_SFX_PATH);
			resumeGame();
		};
		final Runnable optionsAction = () ->
		{
			audioManager.playSfx(AudioManager.UI_CLICK_SFX_PATH);
			app.getGameStateManager().switchTo(GameState.OPTIONS, true);
		};
		final Runnable quitAction = () ->
		{
			audioManager.playSfx(AudioManager.UI_CLICK_SFX_PATH);
			QuestionManager.show(LanguageManager.get("menu.quit_confirm"), this::quitToMenu, null);
		};
		
		// --- Buttons ---
		_buttons = new Panel[BUTTON_COUNT];
		_buttons[0] = ButtonManager.createMenuButtonByScreenPercentage(assetManager, LanguageManager.get("menu.resume"), BUTTON_WIDTH_PERCENT, BUTTON_HEIGHT_PERCENT, resumeAction);
		_buttons[1] = ButtonManager.createMenuButtonByScreenPercentage(assetManager, LanguageManager.get("menu.options"), BUTTON_WIDTH_PERCENT, BUTTON_HEIGHT_PERCENT, optionsAction);
		_buttons[2] = ButtonManager.createMenuButtonByScreenPercentage(assetManager, LanguageManager.get("menu.quit_to_menu"), BUTTON_WIDTH_PERCENT, BUTTON_HEIGHT_PERCENT, quitAction);
		
		// Position buttons centered below the title.
		final float buttonHeight = screenHeight * BUTTON_HEIGHT_PERCENT;
		final float spacing = screenHeight * BUTTON_SPACING_PERCENT;
		final float startY = (screenHeight + ((BUTTON_COUNT * buttonHeight) + ((BUTTON_COUNT - 1) * spacing))) / 2f + (screenHeight * 0.02f);
		
		// --- Navigation ---
		_navigation = new MenuNavigationManager();
		final Runnable[] actions =
		{
			resumeAction,
			optionsAction,
			quitAction
		};
		
		for (int i = 0; i < BUTTON_COUNT; i++)
		{
			_buttons[i].setLocalTranslation((centerX - (_buttons[i].getPreferredSize().x / 2f)), (startY - (i * (buttonHeight + spacing))), 1);
			guiNode.attachChild(_buttons[i]);
			
			_navigation.addSlot(MenuNavigationManager.buttonSlot(_buttons[i], actions[i]));
		}
		
		_navigation.setBackAction(() ->
		{
			audioManager.playSfx(AudioManager.UI_CLICK_SFX_PATH);
			resumeGame();
		});
		_navigation.register();
	}
	
	/**
	 * Remove all GUI elements from the gui node.
	 */
	private void detachAllGui()
	{
		final Node guiNode = SimpleCraft.getInstance().getGuiNode();
		
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
		
		if (_buttons != null)
		{
			for (int i = 0; i < _buttons.length; i++)
			{
				if (_buttons[i] != null)
				{
					guiNode.detachChild(_buttons[i]);
					_buttons[i] = null;
				}
			}
			
			_buttons = null;
		}
	}
	
	// ========== ACTIONS ==========
	
	/**
	 * Resume the game by switching back to PLAYING state.
	 */
	private void resumeGame()
	{
		SimpleCraft.getInstance().getGameStateManager().switchTo(GameState.PLAYING);
	}
	
	/**
	 * Quit to the main menu, cleaning up both pause and playing states.
	 */
	private void quitToMenu()
	{
		SimpleCraft.getInstance().getGameStateManager().switchTo(GameState.MAIN_MENU, true);
	}
}
