package simplecraft.ui;

import java.awt.Font;

import com.jme3.math.ColorRGBA;

import com.simsilica.lemur.Container;
import com.simsilica.lemur.HAlignment;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.component.QuadBackgroundComponent;

import simplecraft.SimpleCraft;

/**
 * Displays temporary toast-style messages on screen.<br>
 * Used for validation feedback, warnings, and informational notifications.<br>
 * Messages auto-dismiss after a configurable duration.<br>
 * Only one message is shown at a time - showing a new message replaces the current one.<br>
 * Supports optional initial delay before the message appears.<br>
 * <br>
 * Usage:<br>
 * {@code MessageManager.show("World already exists!");}<br>
 * {@code MessageManager.show("Saved successfully!", 2f);}<br>
 * {@code MessageManager.show("Screenshot captured!", 2f, 0.5f);} // 2s duration, 0.5s initial delay.
 * @author Pantelis Andrianakis
 * @since February 22nd 2026
 */
public class MessageManager
{
	private static final float DEFAULT_DURATION = 3.0f;
	private static final float PADDING_HORIZONTAL = 25f;
	private static final float PADDING_VERTICAL = 12f;
	private static final float VERTICAL_POSITION = 0.30f; // Fraction of screen height from bottom.
	
	private static final ColorRGBA BG_COLOR = new ColorRGBA(0.12f, 0.12f, 0.12f, 0.92f);
	private static final ColorRGBA TEXT_COLOR = new ColorRGBA(1f, 0.85f, 0.4f, 1f);
	
	private static Container _container;
	private static Label _label;
	private static float _remainingTime;
	private static boolean _active;
	private static float _initialDelayRemaining;
	private static boolean _delayedMessagePending;
	private static String _pendingMessage;
	private static float _pendingDuration;
	
	/**
	 * Show a message with the default duration (3 seconds) and no initial delay.
	 * @param message The message text to display
	 */
	public static void show(String message)
	{
		show(message, DEFAULT_DURATION, 0f);
	}
	
	/**
	 * Show a message for the specified duration with no initial delay. Replaces any currently displayed message.
	 * @param message The message text to display
	 * @param duration Time in seconds before auto-dismiss
	 */
	public static void show(String message, float duration)
	{
		show(message, duration, 0f);
	}
	
	/**
	 * Show a message with an initial delay before appearing.<br>
	 * Replaces any currently displayed or pending message.
	 * @param message The message text to display
	 * @param duration Time in seconds before auto-dismiss after appearing
	 * @param initialDelay Time in seconds to wait before showing the message
	 */
	public static void show(String message, float duration, float initialDelay)
	{
		// Dismiss any existing message immediately.
		dismiss();
		
		// Cancel any pending delayed message.
		_delayedMessagePending = false;
		
		if (initialDelay <= 0f)
		{
			// Show immediately.
			showImmediately(message, duration);
		}
		else
		{
			// Schedule for later.
			_delayedMessagePending = true;
			_pendingMessage = message;
			_pendingDuration = duration;
			_initialDelayRemaining = initialDelay;
			
			System.out.println("Message scheduled with " + initialDelay + "s delay: " + message);
		}
	}
	
	/**
	 * Internal method to show message immediately without delay.
	 */
	private static void showImmediately(String message, float duration)
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		final float screenWidth = app.getCamera().getWidth();
		final float screenHeight = app.getCamera().getHeight();
		final float screenCenterX = screenWidth / 2f;
		
		// Build container.
		_container = new Container();
		_container.setBackground(new QuadBackgroundComponent(BG_COLOR));
		_container.setInsets(new Insets3f(PADDING_VERTICAL, PADDING_HORIZONTAL, PADDING_VERTICAL, PADDING_HORIZONTAL));
		
		// Message label.
		_label = new Label(message);
		_label.setFont(FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_REGULAR_PATH, Font.PLAIN, 18));
		_label.setFontSize(18);
		_label.setColor(TEXT_COLOR);
		_label.setTextHAlignment(HAlignment.Center);
		_container.addChild(_label);
		
		// Position centered horizontally, lower-center of screen.
		final float containerWidth = _container.getPreferredSize().x;
		final float containerHeight = _container.getPreferredSize().y;
		final float posX = screenCenterX - (containerWidth / 2f);
		final float posY = (screenHeight * VERTICAL_POSITION) + (containerHeight / 2f);
		_container.setLocalTranslation(posX, posY, 100); // z=100 to render above everything.
		
		app.getGuiNode().attachChild(_container);
		
		_remainingTime = duration;
		_active = true;
		
		System.out.println("Message shown: " + message);
	}
	
	/**
	 * Dismiss the current message immediately and cancel any pending delayed message.
	 */
	public static void dismiss()
	{
		// Cancel pending delayed message.
		_delayedMessagePending = false;
		_pendingMessage = null;
		
		if (!_active)
		{
			return;
		}
		
		_active = false;
		_remainingTime = 0;
		
		if (_container != null)
		{
			SimpleCraft.getInstance().getGuiNode().detachChild(_container);
			_container = null;
		}
		
		_label = null;
	}
	
	/**
	 * Check if a message is currently being displayed.
	 * @return true if a message is active
	 */
	public static boolean isActive()
	{
		return _active;
	}
	
	/**
	 * Check if a message is pending (waiting for initial delay).
	 * @return true if a delayed message is scheduled
	 */
	public static boolean isPending()
	{
		return _delayedMessagePending;
	}
	
	/**
	 * Update the message timer. Call from the application update loop.<br>
	 * Handles initial delay countdown and auto-dismissal.
	 * @param tpf Time per frame in seconds
	 */
	public static void update(float tpf)
	{
		// Handle pending delayed message.
		if (_delayedMessagePending && !_active)
		{
			_initialDelayRemaining -= tpf;
			if (_initialDelayRemaining <= 0)
			{
				// Time to show the message.
				showImmediately(_pendingMessage, _pendingDuration);
				_delayedMessagePending = false;
				_pendingMessage = null;
			}
			return;
		}
		
		// Handle active message timer.
		if (!_active)
		{
			return;
		}
		
		_remainingTime -= tpf;
		if (_remainingTime <= 0)
		{
			dismiss();
		}
	}
}
