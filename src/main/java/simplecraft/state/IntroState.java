package simplecraft.state;

import java.awt.Font;

import com.jme3.app.Application;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Quad;
import com.jme3.ui.Picture;

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
	private static final float HOLD_DURATION = 3.0f;
	
	// Reference resolution for font scaling (1080p baseline).
	private static final float REFERENCE_WIDTH = 1920f;
	private static final float REFERENCE_HEIGHT = 1080f;
	
	// Base font size at reference resolution.
	private static final float BASE_FONT_SIZE = 64f;
	
	private Label _titleLabel;
	private Geometry _blackBackground;
	private Picture _backgroundImage;
	private float _holdTimer;
	private boolean _firstFadeComplete;
	
	public IntroState()
	{
		// First fade: light gray to transparent (1.33 seconds).
		setFadeIn(1.33f, ColorRGBA.LightGray);
		
		// Second fade: transparent to black (1.0 seconds).
		setFadeOut(1.0f, ColorRGBA.Black);
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
		final Camera camera = app.getCamera();
		final float screenWidth = camera.getWidth();
		final float screenHeight = camera.getHeight();
		
		// Calculate resolution scale factor (using average of width/height ratios for balanced scaling).
		
		// Use the smaller scale to ensure text fits on screen, or average for balanced look.
		final float scaleFactor = Math.min((screenWidth / REFERENCE_WIDTH), (screenHeight / REFERENCE_HEIGHT)); // Conservative: text always fits.
		
		// Calculate adaptive font size.
		final float adaptiveFontSize = BASE_FONT_SIZE * scaleFactor;
		
		// Hide the mouse cursor.
		app.getInputManager().setCursorVisible(false);
		
		// Set initial background to white.
		app.getViewPort().setBackgroundColor(ColorRGBA.White);
		
		// Create black background quad (will be visible during fades).
		_blackBackground = new Geometry("BlackBackground", new Quad(screenWidth, screenHeight));
		
		final Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
		mat.setColor("Color", ColorRGBA.Black);
		_blackBackground.setMaterial(mat);
		_blackBackground.setLocalTranslation(0, 0, -20); // Behind everything.
		
		final Node guiNode = app.getGuiNode();
		guiNode.attachChild(_blackBackground);
		
		// --- Background Image (stretched to fill screen) ---
		_backgroundImage = new Picture("Intro Background");
		_backgroundImage.setImage(app.getAssetManager(), "assets/images/backgrounds/epic_dragon_games.png", true);
		_backgroundImage.setWidth(screenWidth);
		_backgroundImage.setHeight(screenHeight);
		_backgroundImage.setLocalTranslation(0, 0, -10);
		_backgroundImage.setCullHint(Spatial.CullHint.Never);
		
		guiNode.attachChild(_backgroundImage);
		
		// Create title text "Epic Dragon Games" below the logo.
		_titleLabel = new Label("Epic Dragon Games");
		_titleLabel.setFont(FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_LINOCUT_PATH, Font.PLAIN, (int) adaptiveFontSize));
		_titleLabel.setFontSize(adaptiveFontSize);
		_titleLabel.setColor(ColorRGBA.LightGray);
		
		// Center the label.
		final Vector3f labelSize = _titleLabel.getPreferredSize();
		_titleLabel.setLocalTranslation(((screenWidth - labelSize.x) / 2f), ((screenHeight + labelSize.y) / 3.5f), 0);
		
		guiNode.attachChild(_titleLabel);
		
		_holdTimer = 0f;
		_firstFadeComplete = false;
		
		System.out.println("IntroState: Starting - fade in from light gray...");
	}
	
	@Override
	protected void onExitState()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		final Node guiNode = app.getGuiNode();
		
		// Clean up black background.
		if (_blackBackground != null)
		{
			guiNode.detachChild(_blackBackground);
			_blackBackground = null;
		}
		
		// Clean up background image.
		if (_backgroundImage != null)
		{
			guiNode.detachChild(_backgroundImage);
			_backgroundImage = null;
		}
		
		// Clean up title.
		if (_titleLabel != null)
		{
			guiNode.detachChild(_titleLabel);
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
		if (!_firstFadeComplete || isFading() || (_holdTimer >= HOLD_DURATION))
		{
			return;
		}
		
		_holdTimer += tpf;
		if (_holdTimer >= HOLD_DURATION)
		{
			System.out.println("IntroState: Hold complete, starting fade-out to black...");
			
			// Start fade-out and transition to main menu when complete.
			startFadeOut(() -> SimpleCraft.getInstance().getGameStateManager().switchTo(GameStateManager.GameState.MAIN_MENU));
		}
	}
	
	@Override
	protected void onFadeInComplete()
	{
		_firstFadeComplete = true;
		System.out.println("IntroState: Fade in complete - now showing background image with text, holding for " + HOLD_DURATION + "s");
	}
}
