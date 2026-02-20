package simplecraft.ui;

import java.awt.Font;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.jme3.asset.AssetManager;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.HAlignment;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.Panel;
import com.simsilica.lemur.VAlignment;
import com.simsilica.lemur.component.TbtQuadBackgroundComponent;
import com.simsilica.lemur.event.CursorButtonEvent;
import com.simsilica.lemur.event.CursorEventControl;
import com.simsilica.lemur.event.CursorMotionEvent;
import com.simsilica.lemur.event.DefaultCursorListener;

import simplecraft.SimpleCraft;

/**
 * Utility class for creating styled UI buttons with 9-slice texture backgrounds.<br>
 * Supports normal, hover, and pressed visual states using TbtQuadBackgroundComponent.<br>
 * Provides three sizing modes: texture-based, fixed pixel, and screen percentage.<br>
 * Font size automatically scales with button height for consistent visuals at any resolution.
 * @author Pantelis Andrianakis
 * @since February 18th 2026
 */
public class ButtonManager
{
	private static final String BUTTON_NORMAL_PATH = "assets/images/ui/buttons/button_normal.png";
	private static final String BUTTON_HOVER_PATH = "assets/images/ui/buttons/button_hover.png";
	private static final String BUTTON_PRESS_PATH = "assets/images/ui/buttons/button_press.png";
	
	// 9-slice insets (left, top, right, bottom) matching the button texture border size.
	private static final int INSET_LEFT = 4;
	private static final int INSET_TOP = 4;
	private static final int INSET_RIGHT = 4;
	private static final int INSET_BOTTOM = 4;
	
	// Font size as a proportion of button height. 52% fills the button well.
	private static final float FONT_HEIGHT_RATIO = 0.52f;
	private static final int FALLBACK_FONT_SIZE = 28;
	
	private static final float BG_Z_OFFSET = 0.01f;
	private static final long CLICK_COOLDOWN_MS = 300;
	
	/**
	 * Create a menu button sized by the background texture dimensions.
	 * @param assetManager the application asset manager
	 * @param text button label text
	 * @param action action to execute on click
	 * @return configured Panel instance styled as a button
	 */
	public static Panel createMenuButton(AssetManager assetManager, String text, Runnable action)
	{
		return createMenuButtonInternal(assetManager, text, FALLBACK_FONT_SIZE, -1, -1, action);
	}
	
	/**
	 * Create a menu button stretched to a fixed pixel size.
	 * @param assetManager the application asset manager
	 * @param text button label text
	 * @param xSize button width in pixels
	 * @param ySize button height in pixels
	 * @param action action to execute on click
	 * @return configured Panel instance styled as a button
	 */
	public static Panel createMenuButton(AssetManager assetManager, String text, float xSize, float ySize, Runnable action)
	{
		final int fontSize = Math.max(10, (int) (ySize * FONT_HEIGHT_RATIO));
		return createMenuButtonInternal(assetManager, text, fontSize, xSize, ySize, action);
	}
	
	/**
	 * Create a menu button stretched to a percentage of the current screen size.
	 * @param assetManager the application asset manager
	 * @param text button label text
	 * @param xPercentage button width as percentage of screen width (0.0 to 1.0)
	 * @param yPercentage button height as percentage of screen height (0.0 to 1.0)
	 * @param action action to execute on click
	 * @return configured Panel instance styled as a button
	 */
	public static Panel createMenuButtonByScreenPercentage(AssetManager assetManager, String text, float xPercentage, float yPercentage, Runnable action)
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		final float xSize = app.getCamera().getWidth() * xPercentage;
		final float ySize = app.getCamera().getHeight() * yPercentage;
		final int fontSize = Math.max(10, (int) (ySize * FONT_HEIGHT_RATIO));
		return createMenuButtonInternal(assetManager, text, fontSize, xSize, ySize, action);
	}
	
	/**
	 * Internal method that creates a styled menu button using a Label with no internal click handling.<br>
	 * All interaction (hover, press, click) is managed through CursorEventControl.
	 * @param assetManager the application asset manager
	 * @param text button label text
	 * @param fontSize font size for the button label
	 * @param xSize button width in pixels, or -1 to use texture size
	 * @param ySize button height in pixels, or -1 to use texture size
	 * @param action action to execute on click
	 * @return configured Panel instance styled as a button
	 */
	private static Panel createMenuButtonInternal(AssetManager assetManager, String text, int fontSize, float xSize, float ySize, Runnable action)
	{
		final Label button = new Label(text);
		button.setFont(FontManager.getFont(assetManager, FontManager.BLUE_HIGHWAY_LINOCUT_PATH, Font.PLAIN, fontSize));
		button.setFontSize(fontSize);
		button.setTextHAlignment(HAlignment.Center);
		button.setTextVAlignment(VAlignment.Center);
		
		// Set explicit size if provided, otherwise let the texture define the size.
		if (xSize > 0 && ySize > 0)
		{
			button.setPreferredSize(new Vector3f(xSize, ySize, 0));
		}
		
		// Set default state.
		button.setBackground(createBackground(BUTTON_NORMAL_PATH));
		
		// Track pressed state so hover/exit events do not override press visuals.
		final AtomicBoolean pressed = new AtomicBoolean(false);
		
		// Track whether cursor is currently over the button.
		final AtomicBoolean hovering = new AtomicBoolean(false);
		
		// Track last click time to prevent rapid repeated clicks.
		final AtomicLong lastClickTime = new AtomicLong(0);
		
		// Add hover, press, and click handling through cursor listener.
		// Fresh TbtQuadBackgroundComponent instances are created each time because
		// Lemur components cannot be reattached after being detached from a spatial.
		CursorEventControl.addListenersToSpatial(button, new DefaultCursorListener()
		{
			@Override
			public void cursorEntered(CursorMotionEvent event, Spatial target, Spatial capture)
			{
				hovering.set(true);
				if (!pressed.get())
				{
					button.setBackground(createBackground(BUTTON_HOVER_PATH));
				}
			}
			
			@Override
			public void cursorExited(CursorMotionEvent event, Spatial target, Spatial capture)
			{
				hovering.set(false);
				if (!pressed.get())
				{
					button.setBackground(createBackground(BUTTON_NORMAL_PATH));
				}
			}
			
			@Override
			public void cursorButtonEvent(CursorButtonEvent event, Spatial target, Spatial capture)
			{
				event.setConsumed();
				
				if (event.isPressed())
				{
					pressed.set(true);
					button.setBackground(createBackground(BUTTON_PRESS_PATH));
				}
				else
				{
					pressed.set(false);
					
					if (hovering.get())
					{
						// Cursor still over button: reset to hover and fire action.
						button.setBackground(createBackground(BUTTON_HOVER_PATH));
						
						final long now = System.currentTimeMillis();
						if ((now - lastClickTime.get()) >= CLICK_COOLDOWN_MS)
						{
							lastClickTime.set(now);
							action.run();
						}
					}
					else
					{
						// Cursor moved outside button during press: reset to normal, no action.
						button.setBackground(createBackground(BUTTON_NORMAL_PATH));
					}
				}
			}
		});
		
		return button;
	}
	
	/**
	 * Create a TbtQuadBackgroundComponent (9-slice) from a texture path.
	 * @param texturePath path to the button texture
	 * @return configured 9-slice background component
	 */
	private static TbtQuadBackgroundComponent createBackground(String texturePath)
	{
		return TbtQuadBackgroundComponent.create(texturePath, 1f, INSET_LEFT, INSET_TOP, INSET_RIGHT, INSET_BOTTOM, BG_Z_OFFSET, false);
	}
	
	/**
	 * Set a button's visual focus state. Focused buttons show the hover texture and yellow text,<br>
	 * unfocused buttons show the normal texture and white text.<br>
	 * Used by keyboard navigation systems.
	 * @param button the button Panel to update (must have been created by ButtonManager)
	 * @param focused true to show hover/focus visuals, false for normal state
	 */
	public static void setFocused(Panel button, boolean focused)
	{
		if (button instanceof Label)
		{
			final Label label = (Label) button;
			label.setBackground(createBackground(focused ? BUTTON_HOVER_PATH : BUTTON_NORMAL_PATH));
			label.setColor(focused ? new ColorRGBA(1f, 0.85f, 0f, 1f) : ColorRGBA.White);
		}
	}
}
