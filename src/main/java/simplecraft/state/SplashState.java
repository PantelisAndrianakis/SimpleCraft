package simplecraft.state;

import java.awt.Font;

import com.jme3.app.Application;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Quad;
import com.simsilica.lemur.Label;

import simplecraft.SimpleCraft;
import simplecraft.util.FontManager;

/**
 * Splash screen state that shows the game title with a fade-in, hold, and fade-out sequence.<br>
 * Uses the {@link FadeableAppState} overlay for screen transitions:<br>
 * 1. Fade-in from black reveals the title.<br>
 * 2. Title holds on screen for {@link #HOLD_DURATION} seconds.<br>
 * 3. Fade-out to black, then transitions to MAIN_MENU.
 * @author Pantelis Andrianakis
 * @since February 17th 2026
 */
public class SplashState extends FadeableAppState
{
	private static final String FONT_PATH = "fonts/blue_highway_linocut.otf";
	private static final float HOLD_DURATION = 2.0f;
	
	private Geometry _background;
	private Label _titleLabel;
	
	private boolean _holdPhase;
	private float _holdTimer;
	private boolean _complete;
	
	public SplashState()
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
		
		// Create black background quad to ensure pure black behind the fade overlay.
		final Quad quad = new Quad(app.getCamera().getWidth(), app.getCamera().getHeight());
		_background = new Geometry("SplashBackground", quad);
		
		final Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
		mat.setColor("Color", ColorRGBA.Black);
		_background.setMaterial(mat);
		_background.setLocalTranslation(0, 0, -1); // Behind text.
		
		app.getGuiNode().attachChild(_background);
		
		// Create title text with the custom font at exact pixel size.
		_titleLabel = new Label("SimpleCraft");
		_titleLabel.setFont(FontManager.getFont(app.getAssetManager(), FONT_PATH, Font.PLAIN, 72));
		_titleLabel.setFontSize(72);
		_titleLabel.setColor(ColorRGBA.White);
		
		// Center the label.
		final float labelWidth = _titleLabel.getPreferredSize().x;
		final float labelHeight = _titleLabel.getPreferredSize().y;
		final float x = (app.getCamera().getWidth() - labelWidth) / 2f;
		final float y = (app.getCamera().getHeight() + labelHeight) / 2f;
		_titleLabel.setLocalTranslation(x, y, 0);
		
		app.getGuiNode().attachChild(_titleLabel);
		
		_holdPhase = false;
		_holdTimer = 0f;
		_complete = false;
		
		System.out.println("SplashState entered.");
	}
	
	@Override
	protected void onExitState()
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
		
		System.out.println("SplashState exited.");
	}
	
	@Override
	protected void onFadeInComplete()
	{
		// Fade-in finished; start the hold timer.
		_holdPhase = true;
		_holdTimer = 0f;
		System.out.println("Splash: Fade-in complete, holding for " + HOLD_DURATION + "s.");
	}
	
	@Override
	protected void onUpdateState(float tpf)
	{
		if (_complete)
		{
			return;
		}
		
		if (_holdPhase)
		{
			_holdTimer += tpf;
			
			if (_holdTimer >= HOLD_DURATION)
			{
				_complete = true;
				System.out.println("Splash: Hold complete, starting fade-out.");
				
				// Fade out, then transition to main menu.
				startFadeOut(() ->
				{
					SimpleCraft.getInstance().getGameStateManager().switchTo(GameStateManager.GameState.MAIN_MENU);
				});
			}
		}
	}
}
