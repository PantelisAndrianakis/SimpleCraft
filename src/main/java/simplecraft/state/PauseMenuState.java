package simplecraft.state;

import java.awt.Font;

import com.jme3.app.Application;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Spatial;
import com.jme3.ui.Picture;

import com.simsilica.lemur.Label;
import com.simsilica.lemur.Panel;

import simplecraft.SimpleCraft;
import simplecraft.audio.AudioManager;
import simplecraft.input.MenuNavigationManager;
import simplecraft.state.GameStateManager.GameState;
import simplecraft.ui.ButtonManager;
import simplecraft.ui.FontManager;
import simplecraft.ui.QuestionManager;

/**
 * Pause menu overlay rendered on top of PlayingState.<br>
 * Background with centered title, logo, and buttons.<br>
 * Provides Resume, Options, and Quit to Menu with keyboard navigation.<br>
 * Pressing Escape (UI_BACK) resumes the game.
 * @author Pantelis Andrianakis
 * @since February 19th 2026
 */
public class PauseMenuState extends FadeableAppState
{
	// Title layout constants (matching main menu).
	private static final String BACKGROUND_PATH = "assets/images/backgrounds/main_menu.png";
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
		
		// Show cursor for menu interaction.
		app.getInputManager().setCursorVisible(true);
		
		buildGui();
	}
	
	@Override
	protected void onExitState()
	{
		System.out.println("PauseMenuState exited.");
		
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
	 * Build the pause menu background, title, logo, and buttons.
	 */
	private void buildGui()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		final int screenWidth = app.getCamera().getWidth();
		final int screenHeight = app.getCamera().getHeight();
		final float centerX = screenWidth / 2f;
		
		// --- Background Image (stretched to fill screen) ---
		_background = new Picture("Pause Background");
		_background.setImage(app.getAssetManager(), BACKGROUND_PATH, true);
		_background.setWidth(screenWidth);
		_background.setHeight(screenHeight);
		_background.setLocalTranslation(0, 0, -10); // Behind everything.
		_background.setCullHint(Spatial.CullHint.Never);
		app.getGuiNode().attachChild(_background);
		
		// --- Title label (same as main menu) ---
		final int titleFontSize = Math.max(24, Math.round(screenHeight * TITLE_FONT_RATIO));
		_titleLabel = new Label("SimpleCraft");
		_titleLabel.setFont(FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_LINOCUT_PATH, Font.PLAIN, titleFontSize));
		_titleLabel.setFontSize(titleFontSize);
		_titleLabel.setColor(ColorRGBA.White);
		_titleLabel.setBackground(null);
		
		final float titleWidth = _titleLabel.getPreferredSize().x;
		final float titleHeight = _titleLabel.getPreferredSize().y;
		
		// --- Logo (to the right of title, same as main menu) ---
		_logo = new Picture("Pause Logo");
		_logo.setImage(app.getAssetManager(), TITLE_LOGO_PATH, true);
		_logo.setWidth(LOGO_SIZE);
		_logo.setHeight(LOGO_SIZE);
		
		// Center the title+logo group horizontally.
		final float titleGroupWidth = titleWidth + LOGO_TITLE_SPACING + LOGO_SIZE;
		final float titleGroupX = centerX - (titleGroupWidth / 2.3f);
		final float titleY = screenHeight * 0.85f;
		_titleLabel.setLocalTranslation(titleGroupX, titleY, 1);
		app.getGuiNode().attachChild(_titleLabel);
		
		// Logo: to the right of title, vertically centered with text.
		final float logoX = titleGroupX + titleWidth + LOGO_TITLE_SPACING;
		final float logoY = titleY - (titleHeight * 0.41f) - (LOGO_SIZE / 2f);
		_logo.setLocalTranslation(logoX, logoY, 1);
		app.getGuiNode().attachChild(_logo);
		
		// --- Button actions ---
		final Runnable resumeAction = () ->
		{
			app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
			resumeGame();
		};
		final Runnable optionsAction = () ->
		{
			app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
			app.getGameStateManager().switchTo(GameState.OPTIONS, true);
		};
		final Runnable quitAction = () ->
		{
			app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
			QuestionManager.show("Quit to menu?", this::quitToMenu, null);
		};
		
		// --- Buttons ---
		_buttons = new Panel[BUTTON_COUNT];
		_buttons[0] = ButtonManager.createMenuButtonByScreenPercentage(app.getAssetManager(), "Resume", BUTTON_WIDTH_PERCENT, BUTTON_HEIGHT_PERCENT, resumeAction);
		_buttons[1] = ButtonManager.createMenuButtonByScreenPercentage(app.getAssetManager(), "Options", BUTTON_WIDTH_PERCENT, BUTTON_HEIGHT_PERCENT, optionsAction);
		_buttons[2] = ButtonManager.createMenuButtonByScreenPercentage(app.getAssetManager(), "Quit to Menu", BUTTON_WIDTH_PERCENT, BUTTON_HEIGHT_PERCENT, quitAction);
		
		// Position buttons centered below the title.
		final float buttonHeight = screenHeight * BUTTON_HEIGHT_PERCENT;
		final float spacing = screenHeight * BUTTON_SPACING_PERCENT;
		final float totalHeight = (BUTTON_COUNT * buttonHeight) + ((BUTTON_COUNT - 1) * spacing);
		final float startY = (screenHeight + totalHeight) / 2f + (screenHeight * 0.02f);
		
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
			final float buttonWidth = _buttons[i].getPreferredSize().x;
			final float x = centerX - (buttonWidth / 2f);
			final float y = startY - (i * (buttonHeight + spacing));
			_buttons[i].setLocalTranslation(x, y, 1);
			app.getGuiNode().attachChild(_buttons[i]);
			
			_navigation.addSlot(MenuNavigationManager.buttonSlot(_buttons[i], actions[i]));
		}
		
		_navigation.setBackAction(() ->
		{
			app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
			resumeGame();
		});
		_navigation.register();
	}
	
	/**
	 * Remove all GUI elements from the gui node.
	 */
	private void detachAllGui()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		
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
		
		if (_buttons != null)
		{
			for (int i = 0; i < _buttons.length; i++)
			{
				if (_buttons[i] != null)
				{
					app.getGuiNode().detachChild(_buttons[i]);
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
