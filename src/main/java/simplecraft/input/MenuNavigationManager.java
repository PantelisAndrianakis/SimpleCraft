package simplecraft.input;

import java.util.ArrayList;
import java.util.List;

import com.jme3.input.InputManager;
import com.jme3.input.controls.ActionListener;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Spatial;

import com.simsilica.lemur.Label;
import com.simsilica.lemur.Panel;

import simplecraft.SimpleCraft;
import simplecraft.audio.AudioManager;
import simplecraft.ui.ButtonManager;
import simplecraft.ui.QuestionManager;

/**
 * Centralized keyboard/gamepad navigation for all menu states.<br>
 * Manages a flat list of {@link NavigationSlot} entries with focus tracking,<br>
 * QuestionManager delegation, and a configurable back action.<br>
 * <br>
 * Supports three slot types via static factories:<br>
 * - {@link #buttonSlot(Panel, Runnable)} for ButtonManager-styled menu buttons<br>
 * - {@link #labelSlot(Label, Runnable, Runnable, Runnable)} for label-highlighted content rows<br>
 * - {@link #customSlot(Runnable, Runnable, Runnable, Runnable, Runnable)} for tab rows and special zones
 * @author Pantelis Andrianakis
 * @since February 20th 2026
 */
public class MenuNavigationManager
{
	/** Focus highlight color for label-based slots. */
	public static final ColorRGBA FOCUS_HIGHLIGHT_COLOR = new ColorRGBA(1f, 0.85f, 0f, 1f);
	
	/**
	 * A focusable slot in the navigation list.<br>
	 * Each slot handles its own focus visuals and directional/confirm actions.
	 */
	public interface NavigationSlot
	{
		/** Called when this slot gains focus. */
		default void onFocus()
		{
		}
		
		/** Called when this slot loses focus. */
		default void onUnfocus()
		{
		}
		
		/** Called on left input (A key / left arrow). */
		default void onLeft()
		{
		}
		
		/** Called on right input (D key / right arrow). */
		default void onRight()
		{
		}
		
		/** Called on confirm input (Enter / Space). */
		default void onConfirm()
		{
		}
	}
	
	// ========== SLOT FACTORIES ==========
	
	/**
	 * Create a slot for a ButtonManager-styled menu button.<br>
	 * Focus/unfocus calls {@link ButtonManager#setFocused(Spatial, boolean)}.<br>
	 * Confirm runs the button action. Left/right do nothing.
	 * @param button The Panel created by ButtonManager
	 * @param action The action to run on confirm
	 * @return A NavigationSlot for this button
	 */
	public static NavigationSlot buttonSlot(Panel button, Runnable action)
	{
		return new NavigationSlot()
		{
			@Override
			public void onFocus()
			{
				ButtonManager.setFocused(button, true);
			}
			
			@Override
			public void onUnfocus()
			{
				ButtonManager.setFocused(button, false);
			}
			
			@Override
			public void onConfirm()
			{
				if (action != null)
				{
					action.run();
				}
			}
		};
	}
	
	/**
	 * Create a slot for a label-highlighted content row (e.g., sliders, toggles).<br>
	 * Focus sets the label to yellow; unfocus restores white.
	 * @param label The label to highlight
	 * @param onLeft Action for left input (e.g., decrease slider), or null
	 * @param onRight Action for right input (e.g., increase slider), or null
	 * @param onConfirm Action for confirm input (e.g., toggle), or null
	 * @return A NavigationSlot for this row
	 */
	public static NavigationSlot labelSlot(Label label, Runnable onLeft, Runnable onRight, Runnable onConfirm)
	{
		return new NavigationSlot()
		{
			@Override
			public void onFocus()
			{
				if (label != null)
				{
					label.setColor(FOCUS_HIGHLIGHT_COLOR);
				}
			}
			
			@Override
			public void onUnfocus()
			{
				if (label != null)
				{
					label.setColor(ColorRGBA.White);
				}
			}
			
			@Override
			public void onLeft()
			{
				if (onLeft != null)
				{
					onLeft.run();
				}
			}
			
			@Override
			public void onRight()
			{
				if (onRight != null)
				{
					onRight.run();
				}
			}
			
			@Override
			public void onConfirm()
			{
				if (onConfirm != null)
				{
					onConfirm.run();
				}
			}
		};
	}
	
	/**
	 * Create a fully custom slot with explicit callbacks for all actions.<br>
	 * Used for tab rows, bottom button rows, and other special navigation zones.
	 * @param onFocus Called when slot gains focus, or null
	 * @param onUnfocus Called when slot loses focus, or null
	 * @param onLeft Called on left input, or null
	 * @param onRight Called on right input, or null
	 * @param onConfirm Called on confirm input, or null
	 * @return A NavigationSlot with the given callbacks
	 */
	public static NavigationSlot customSlot(Runnable onFocus, Runnable onUnfocus, Runnable onLeft, Runnable onRight, Runnable onConfirm)
	{
		return new NavigationSlot()
		{
			@Override
			public void onFocus()
			{
				if (onFocus != null)
				{
					onFocus.run();
				}
			}
			
			@Override
			public void onUnfocus()
			{
				if (onUnfocus != null)
				{
					onUnfocus.run();
				}
			}
			
			@Override
			public void onLeft()
			{
				if (onLeft != null)
				{
					onLeft.run();
				}
			}
			
			@Override
			public void onRight()
			{
				if (onRight != null)
				{
					onRight.run();
				}
			}
			
			@Override
			public void onConfirm()
			{
				if (onConfirm != null)
				{
					onConfirm.run();
				}
			}
		};
	}
	
	// ========== INSTANCE FIELDS ==========
	
	private final List<NavigationSlot> _slots = new ArrayList<>();
	private int _focusIndex = -1;
	private Runnable _backAction;
	private boolean _locked;
	private ActionListener _listener;
	
	// ========== SLOT MANAGEMENT ==========
	
	/**
	 * Add a slot to the end of the navigation list.
	 * @param slot The NavigationSlot to add
	 */
	public void addSlot(NavigationSlot slot)
	{
		_slots.add(slot);
	}
	
	/**
	 * Remove all slots and reset focus to mouse-only mode.
	 */
	public void clearSlots()
	{
		// Unfocus current slot before clearing.
		if (_focusIndex >= 0 && _focusIndex < _slots.size())
		{
			_slots.get(_focusIndex).onUnfocus();
		}
		_slots.clear();
	}
	
	/**
	 * Get the current slot list size.
	 * @return The number of registered slots
	 */
	public int getSlotCount()
	{
		return _slots.size();
	}
	
	// ========== CONFIGURATION ==========
	
	/**
	 * Set the action to execute when UI_BACK is pressed.<br>
	 * This action fires regardless of focus mode (mouse or keyboard).
	 * @param backAction The Runnable to execute, or null to disable
	 */
	public void setBackAction(Runnable backAction)
	{
		_backAction = backAction;
	}
	
	/**
	 * Set whether navigation input is locked (ignored).<br>
	 * Used by OptionsState during keybinding listening mode.
	 * @param locked true to ignore all navigation input
	 */
	public void setLocked(boolean locked)
	{
		_locked = locked;
	}
	
	/**
	 * Check if navigation is currently locked.
	 * @return true if locked
	 */
	public boolean isLocked()
	{
		return _locked;
	}
	
	// ========== FOCUS CONTROL ==========
	
	/**
	 * Reset focus to mouse-only mode (no slot highlighted).
	 */
	public void resetFocus()
	{
		if (_focusIndex >= 0 && _focusIndex < _slots.size())
		{
			_slots.get(_focusIndex).onUnfocus();
		}
		_focusIndex = -1;
	}
	
	/**
	 * Get the current focus index.
	 * @return The focused slot index, or -1 for mouse-only mode
	 */
	public int getFocusIndex()
	{
		return _focusIndex;
	}
	
	/**
	 * Set the focus index directly and apply focus visuals.<br>
	 * Used after tab changes to restore focus position.
	 * @param index The slot index to focus, or -1 for mouse-only mode
	 */
	public void setFocusIndex(int index)
	{
		// Unfocus previous.
		if (_focusIndex >= 0 && _focusIndex < _slots.size())
		{
			_slots.get(_focusIndex).onUnfocus();
		}
		
		_focusIndex = index;
		
		// Focus new.
		if (_focusIndex >= 0 && _focusIndex < _slots.size())
		{
			_slots.get(_focusIndex).onFocus();
		}
	}
	
	/**
	 * Clamp the focus index to the current slot list size and re-apply focus visuals.<br>
	 * Call after {@link #clearSlots()} / {@link #addSlot(NavigationSlot)} to maintain valid focus.
	 */
	public void clampAndRefocus()
	{
		if (_focusIndex >= 0)
		{
			_focusIndex = Math.min(_focusIndex, _slots.size() - 1);
			if (_focusIndex >= 0 && _focusIndex < _slots.size())
			{
				_slots.get(_focusIndex).onFocus();
			}
		}
	}
	
	// ========== LISTENER LIFECYCLE ==========
	
	/**
	 * Register the navigation ActionListener on the given InputManager.<br>
	 * Listens for UI_UP, UI_DOWN, UI_LEFT, UI_RIGHT, UI_CONFIRM, UI_BACK.
	 */
	public void register()
	{
		final InputManager inputManager = SimpleCraft.getInstance().getInputManager();
		
		_listener = (String name, boolean isPressed, float tpf) ->
		{
			if (!isPressed)
			{
				return;
			}
			
			// Ignore all input when locked (e.g., keybinding listening mode).
			if (_locked)
			{
				return;
			}
			
			final SimpleCraft app = SimpleCraft.getInstance();
			
			// When a question dialog is active, delegate navigation to it.
			if (QuestionManager.isActive())
			{
				handleQuestionNavigation(app, name);
				return;
			}
			
			// Back action always fires regardless of focus mode.
			if (GameInputManager.UI_BACK.equals(name))
			{
				if (_backAction != null)
				{
					_backAction.run();
				}
				return;
			}
			
			// First keyboard input activates focus on the first slot.
			if (_focusIndex < 0)
			{
				if (!_slots.isEmpty())
				{
					_focusIndex = 0;
					_slots.get(_focusIndex).onFocus();
				}
				return;
			}
			
			switch (name)
			{
				case GameInputManager.UI_UP:
				{
					app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
					moveFocus(-1);
					break;
				}
				case GameInputManager.UI_DOWN:
				{
					app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
					moveFocus(1);
					break;
				}
				case GameInputManager.UI_LEFT:
				{
					if (_focusIndex >= 0 && _focusIndex < _slots.size())
					{
						_slots.get(_focusIndex).onLeft();
					}
					break;
				}
				case GameInputManager.UI_RIGHT:
				{
					if (_focusIndex >= 0 && _focusIndex < _slots.size())
					{
						_slots.get(_focusIndex).onRight();
					}
					break;
				}
				case GameInputManager.UI_CONFIRM:
				{
					if (_focusIndex >= 0 && _focusIndex < _slots.size())
					{
						_slots.get(_focusIndex).onConfirm();
					}
					break;
				}
			}
		};
		
		inputManager.addListener(_listener, GameInputManager.UI_UP, GameInputManager.UI_DOWN, GameInputManager.UI_LEFT, GameInputManager.UI_RIGHT, GameInputManager.UI_CONFIRM, GameInputManager.UI_BACK);
	}
	
	/**
	 * Unregister the navigation ActionListener and reset state.
	 */
	public void unregister()
	{
		if (_listener != null)
		{
			SimpleCraft.getInstance().getInputManager().removeListener(_listener);
			_listener = null;
		}
	}
	
	// ========== INTERNAL ==========
	
	/**
	 * Move focus up or down with wrapping.
	 * @param offset -1 for up, +1 for down
	 */
	private void moveFocus(int offset)
	{
		if (_slots.isEmpty())
		{
			return;
		}
		
		// Unfocus current.
		if (_focusIndex >= 0 && _focusIndex < _slots.size())
		{
			_slots.get(_focusIndex).onUnfocus();
		}
		
		final int count = _slots.size();
		_focusIndex = ((_focusIndex + offset) % count + count) % count;
		
		// Focus new.
		_slots.get(_focusIndex).onFocus();
	}
	
	/**
	 * Handle keyboard navigation when a QuestionManager dialog is active.
	 */
	private void handleQuestionNavigation(SimpleCraft app, String name)
	{
		switch (name)
		{
			case GameInputManager.UI_LEFT:
			{
				app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
				QuestionManager.navigateLeft();
				break;
			}
			case GameInputManager.UI_RIGHT:
			{
				app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
				QuestionManager.navigateRight();
				break;
			}
			case GameInputManager.UI_CONFIRM:
			{
				QuestionManager.confirmSelection();
				break;
			}
			case GameInputManager.UI_BACK:
			{
				app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
				QuestionManager.dismiss();
				break;
			}
		}
	}
}
