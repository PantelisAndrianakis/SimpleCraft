package simplecraft.state;

import java.awt.Font;

import com.jme3.app.Application;
import com.jme3.math.ColorRGBA;
import com.simsilica.lemur.Label;

import simplecraft.SimpleCraft;
import simplecraft.util.FontManager;

/**
 * Placeholder main menu state with fade-in transition from black.
 * @author Pantelis Andrianakis
 * @since February 17th 2026
 */
public class MainMenuState extends FadeableAppState
{
	private static final String FONT_PATH = "fonts/blue_highway_rg.otf";
	private static final String MUSIC_PATH = "music/perspectives.ogg";
	
	private Label _placeholderLabel;
	
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
		
		// Restore the original menu background color (dark blue).
		app.getViewPort().setBackgroundColor(new ColorRGBA(0.1f, 0.1f, 0.2f, 1.0f));
		
		// Create placeholder text with the custom font at exact pixel size.
		_placeholderLabel = new Label("Main Menu \u2014 Coming Soon");
		_placeholderLabel.setFont(FontManager.getFont(app.getAssetManager(), FONT_PATH, Font.PLAIN, 32));
		_placeholderLabel.setFontSize(32);
		_placeholderLabel.setColor(ColorRGBA.White);
		
		// Center the label.
		final float labelWidth = _placeholderLabel.getPreferredSize().x;
		final float labelHeight = _placeholderLabel.getPreferredSize().y;
		final float x = (app.getCamera().getWidth() - labelWidth) / 2f;
		final float y = (app.getCamera().getHeight() + labelHeight) / 2f;
		_placeholderLabel.setLocalTranslation(x, y, 0);
		
		app.getGuiNode().attachChild(_placeholderLabel);
		
		// Start music with fade-in.
		app.getAudioManager().fadeInMusic(MUSIC_PATH, 5.0f);
	}
	
	@Override
	protected void onExitState()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		
		// Remove placeholder text.
		if (_placeholderLabel != null)
		{
			app.getGuiNode().detachChild(_placeholderLabel);
			_placeholderLabel = null;
		}
		
		// Stop music when leaving menu.
		app.getAudioManager().stopMusic();
	}
}
