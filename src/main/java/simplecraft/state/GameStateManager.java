package simplecraft.state;

import java.util.HashMap;
import java.util.Stack;

import com.jme3.app.state.AppStateManager;
import com.jme3.app.state.BaseAppState;

/**
 * Manages game state transitions and maintains the current game state.<br>
 * Handles special cases like overlay states (PAUSED) and return navigation (OPTIONS).<br>
 * Supports optional fade transitions via {@link #switchTo(GameState, boolean)}.
 * @author Pantelis Andrianakis
 * @since February 16th 2026
 */
public class GameStateManager
{
	public enum GameState
	{
		INTRO,
		MAIN_MENU,
		OPTIONS,
		PLAYING,
		PAUSED
	}
	
	private final AppStateManager _stateManager;
	private final HashMap<GameState, BaseAppState> _registeredStates;
	private final Stack<GameState> _stateHistory;
	
	private GameState _currentState;
	private BaseAppState _currentAppState;
	
	public GameStateManager(AppStateManager stateManager)
	{
		_stateManager = stateManager;
		_registeredStates = new HashMap<>();
		_stateHistory = new Stack<>();
		_currentState = null;
		_currentAppState = null;
		
		System.out.println("GameStateManager initialized.");
	}
	
	/**
	 * Register a state implementation for a given GameState enum.
	 * @param state The GameState enum value
	 * @param appState The BaseAppState implementation
	 */
	public void registerState(GameState state, BaseAppState appState)
	{
		_registeredStates.put(state, appState);
		System.out.println("Registered state: " + state);
	}
	
	/**
	 * Switch to a new game state instantly (no fade transition).
	 * @param newState The state to switch to
	 */
	public void switchTo(GameState newState)
	{
		switchTo(newState, false);
	}
	
	/**
	 * Switch to a new game state with an optional fade transition.<br>
	 * When fade is true and the current state is a {@link FadeableAppState} with a configured fade-out duration,<br>
	 * the current state fades out before the switch occurs.<br>
	 * The new state's fade-in (if configured) begins automatically when it is enabled.<br>
	 * <br>
	 * Handles special cases:<br>
	 * - PAUSED: Overlays on PLAYING (disables PLAYING instead of detaching)<br>
	 * - OPTIONS: Tracks previous state for return navigation
	 * @param newState The state to switch to
	 * @param fade Whether to use fade transitions
	 */
	public void switchTo(GameState newState, boolean fade)
	{
		if (!_registeredStates.containsKey(newState))
		{
			System.err.println("WARNING: State " + newState + " is not registered. Cannot switch.");
			return;
		}
		
		// If fading is requested and the current state supports it, fade out first.
		if (fade && _currentAppState instanceof FadeableAppState)
		{
			final FadeableAppState fadeable = (FadeableAppState) _currentAppState;
			if (fadeable.getFadeOutDuration() > 0f && !fadeable.isFading())
			{
				fadeable.startFadeOut(() -> executeSwitch(newState));
				return;
			}
		}
		
		executeSwitch(newState);
	}
	
	/**
	 * Return to the previous state (used when exiting OPTIONS).<br>
	 * Uses an instant transition (no fade).
	 */
	public void returnToPrevious()
	{
		returnToPrevious(false);
	}
	
	/**
	 * Return to the previous state with an optional fade transition.
	 * @param fade Whether to use fade transitions
	 */
	public void returnToPrevious(boolean fade)
	{
		if (_stateHistory.isEmpty())
		{
			System.err.println("WARNING: No previous state to return to.");
			return;
		}
		
		final GameState previousState = _stateHistory.pop();
		
		// If fading is requested and current state supports it, fade out first.
		if (fade && _currentAppState instanceof FadeableAppState)
		{
			final FadeableAppState fadeable = (FadeableAppState) _currentAppState;
			if (fadeable.getFadeOutDuration() > 0f && !fadeable.isFading())
			{
				fadeable.startFadeOut(() -> executeReturnTo(previousState));
				return;
			}
		}
		
		executeReturnTo(previousState);
	}
	
	/**
	 * Get the current game state.
	 * @return The current GameState enum value, or null if no state is active
	 */
	public GameState getCurrentState()
	{
		return _currentState;
	}
	
	// --- Internal switch logic ---
	
	/**
	 * Execute the actual state transition (called directly or after a fade-out completes).
	 * @param newState The state to switch to
	 */
	private void executeSwitch(GameState newState)
	{
		// Special case: PAUSED is an overlay on PLAYING.
		if (newState == GameState.PAUSED)
		{
			if (_currentState != GameState.PLAYING)
			{
				System.err.println("WARNING: Can only pause from PLAYING state. Current: " + _currentState);
				return;
			}
			
			// Disable PLAYING but keep it attached.
			if (_currentAppState != null)
			{
				_currentAppState.setEnabled(false);
			}
			
			// Push current state to history and attach PAUSED.
			_stateHistory.push(_currentState);
			final BaseAppState pausedState = _registeredStates.get(GameState.PAUSED);
			_stateManager.attach(pausedState);
			_currentState = GameState.PAUSED;
			_currentAppState = pausedState;
			
			System.out.println("Switched to PAUSED (overlay mode).");
			return;
		}
		
		// Special case: Resuming from PAUSED.
		if (_currentState == GameState.PAUSED && newState == GameState.PLAYING)
		{
			// Detach PAUSED overlay.
			final BaseAppState pausedState = _registeredStates.get(GameState.PAUSED);
			_stateManager.detach(pausedState);
			
			// Re-enable PLAYING.
			final BaseAppState playingState = _registeredStates.get(GameState.PLAYING);
			if (playingState != null)
			{
				playingState.setEnabled(true);
			}
			
			// Pop from history.
			if (!_stateHistory.isEmpty())
			{
				_stateHistory.pop();
			}
			
			_currentState = GameState.PLAYING;
			_currentAppState = playingState;
			
			System.out.println("Resumed PLAYING from PAUSED.");
			return;
		}
		
		// Special case: Leaving PAUSED for a state other than PLAYING or OPTIONS (e.g., Quit to Menu).
		// Must detach both the PAUSED overlay and the underlying PLAYING state.
		if (_currentState == GameState.PAUSED && newState != GameState.PLAYING && newState != GameState.OPTIONS)
		{
			// Detach PAUSED overlay.
			final BaseAppState pausedState = _registeredStates.get(GameState.PAUSED);
			if (pausedState != null)
			{
				_stateManager.detach(pausedState);
			}
			
			// Detach the underlying PLAYING state that was kept attached but disabled.
			final BaseAppState playingState = _registeredStates.get(GameState.PLAYING);
			if (playingState != null && _stateManager.hasState(playingState))
			{
				_stateManager.detach(playingState);
			}
			
			// Clear history since we are fully leaving the play session.
			_stateHistory.clear();
			
			// Attach the new state.
			final BaseAppState newAppState = _registeredStates.get(newState);
			_stateManager.attach(newAppState);
			_currentState = newState;
			_currentAppState = newAppState;
			
			System.out.println("Left PAUSED, switched to state: " + newState);
			return;
		}
		
		// Special case: OPTIONS tracks previous state.
		if (newState == GameState.OPTIONS)
		{
			if (_currentState != null)
			{
				_stateHistory.push(_currentState);
			}
		}
		
		// Standard state transition: detach current, attach new.
		if (_currentAppState != null)
		{
			_stateManager.detach(_currentAppState);
		}
		
		final BaseAppState newAppState = _registeredStates.get(newState);
		_stateManager.attach(newAppState);
		
		_currentState = newState;
		_currentAppState = newAppState;
		
		System.out.println("Switched to state: " + newState);
	}
	
	/**
	 * Execute a return-to-previous transition (called directly or after a fade-out completes).
	 * @param previousState The state to return to
	 */
	private void executeReturnTo(GameState previousState)
	{
		// Detach current.
		if (_currentAppState != null)
		{
			_stateManager.detach(_currentAppState);
		}
		
		// Attach previous.
		final BaseAppState previousAppState = _registeredStates.get(previousState);
		if (previousAppState != null)
		{
			_stateManager.attach(previousAppState);
			_currentState = previousState;
			_currentAppState = previousAppState;
			System.out.println("Returned to state: " + previousState);
		}
	}
}
