package simplecraft.state;

import java.awt.Font;

import com.jme3.app.Application;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Quad;

import com.simsilica.lemur.Label;

import simplecraft.SimpleCraft;
import simplecraft.ui.FontManager;

/**
 * Simple intro that fades in and out using parent class functionality.
 * @author Pantelis Andrianakis
 * @since February 17th 2026
 */
public class IntroState extends FadeableAppState
{
	private static final float HOLD_DURATION = 2.5f;
	// Reference resolution for font scaling (1080p baseline).
	private static final float REFERENCE_WIDTH = 1920f;
	private static final float REFERENCE_HEIGHT = 1080f;
	// Base font size at reference resolution.
	private static final float BASE_FONT_SIZE = 48f;
	
	private Label _titleLabel;
	private Geometry _background;
	private float _holdTimer;
	private boolean _firstFadeComplete;
	
	public IntroState()
	{
		// First fade: white to black (1.33 seconds).
		setFadeIn(1.33f, new ColorRGBA(1, 1, 1, 1)); // Start white, fade to transparent.
		
		// Second fade: black fade-out (1 second).
		setFadeOut(1.0f, new ColorRGBA(0, 0, 0, 1)); // Fade to black.
	}
	
	@Override
	protected void initialize(Application app)
	{
		// Called once when state is first created.
	}
	
	@Override
	protected void cleanup(Application app)
	{
		// Called once when state is destroyed.
	}
	
	@Override
	protected void onEnterState()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		final float screenWidth = app.getCamera().getWidth();
		final float screenHeight = app.getCamera().getHeight();
		
		// Calculate resolution scale factor (using average of width/height ratios for balanced scaling).
		final float widthScale = screenWidth / REFERENCE_WIDTH;
		final float heightScale = screenHeight / REFERENCE_HEIGHT;
		// Use the smaller scale to ensure text fits on screen, or average for balanced look.
		final float scaleFactor = Math.min(widthScale, heightScale); // Conservative: text always fits.
		// Alternative: final float scaleFactor = (widthScale + heightScale) / 2f; // Balanced scaling.
		
		// Calculate adaptive font size.
		final float adaptiveFontSize = BASE_FONT_SIZE * scaleFactor;
		
		// Hide the mouse cursor.
		app.getInputManager().setCursorVisible(false);
		
		// Set initial background to white.
		app.getViewPort().setBackgroundColor(ColorRGBA.White);
		
		// Create black background quad (will be visible after fade-in).
		final Quad quad = new Quad(screenWidth, screenHeight);
		_background = new Geometry("BlackBackground", quad);
		
		final Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
		mat.setColor("Color", ColorRGBA.Black);
		_background.setMaterial(mat);
		_background.setLocalTranslation(0, 0, -1); // Behind text.
		
		app.getGuiNode().attachChild(_background);
		
		// Create title text in white (so it's visible on black background).
		_titleLabel = new Label("Mobius Development");
		_titleLabel.setFont(FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_LINOCUT_PATH, Font.PLAIN, (int) adaptiveFontSize));
		_titleLabel.setFontSize(adaptiveFontSize);
		_titleLabel.setColor(ColorRGBA.White); // White text on black background.
		
		// Center the label.
		final float labelWidth = _titleLabel.getPreferredSize().x;
		final float labelHeight = _titleLabel.getPreferredSize().y;
		final float x = (screenWidth - labelWidth) / 2f;
		final float y = (screenHeight + labelHeight) / 1.9f;
		_titleLabel.setLocalTranslation(x, y, 0);
		
		app.getGuiNode().attachChild(_titleLabel);
		
		_holdTimer = 0f;
		_firstFadeComplete = false;
		
		System.out.println("IntroState: Starting - white screen with fade-in to black...");
	}
	
	@Override
	protected void onExitState()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		
		// Clean up background.
		if (_background != null)
		{
			app.getGuiNode().detachChild(_background);
			_background = null;
		}
		
		// Clean up title.
		if (_titleLabel != null)
		{
			app.getGuiNode().detachChild(_titleLabel);
			_titleLabel = null;
		}
		
		// Restore mouse cursor visibility.
		app.getInputManager().setCursorVisible(true);
		
		System.out.println("IntroState exited.");
	}
	
	@Override
	protected void onUpdateState(float tpf)
	{
		// After first fade completes, wait for hold duration.
		if (_firstFadeComplete && !isFading() && _holdTimer < HOLD_DURATION)
		{
			_holdTimer += tpf;
			
			if (_holdTimer >= HOLD_DURATION)
			{
				System.out.println("IntroState: Hold complete, starting fade-out to black...");
				
				// Start fade-out and transition to main menu when complete.
				startFadeOut(() -> SimpleCraft.getInstance().getGameStateManager().switchTo(GameStateManager.GameState.MAIN_MENU));
			}
		}
	}
	
	@Override
	protected void onFadeInComplete()
	{
		_firstFadeComplete = true;
		System.out.println("IntroState: First fade complete - now on black background with text, holding for " + HOLD_DURATION + "s");
	}
}
