package simplecraft.state;

import java.util.HashMap;
import java.util.Stack;

import com.jme3.app.state.AppStateManager;
import com.jme3.app.state.BaseAppState;

/**
 * Manages game state transitions and maintains the current game state.<br>
 * Handles special cases like overlay states (PAUSED) and return navigation (OPTIONS).
 */
public class GameStateManager
{
	public enum GameState
	{
		SPLASH,
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
	 * Switch to a new game state.<br>
	 * Handles special cases:<br>
	 * - PAUSED: Overlays on PLAYING (disables PLAYING instead of detaching)<br>
	 * - OPTIONS: Tracks previous state for return navigation
	 * @param newState The state to switch to
	 */
	public void switchTo(GameState newState)
	{
		if (!_registeredStates.containsKey(newState))
		{
			System.err.println("WARNING: State " + newState + " is not registered. Cannot switch.");
			return;
		}
		
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
	 * Return to the previous state (used when exiting OPTIONS).
	 */
	public void returnToPrevious()
	{
		if (_stateHistory.isEmpty())
		{
			System.err.println("WARNING: No previous state to return to.");
			return;
		}
		
		final GameState previousState = _stateHistory.pop();
		
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
	
	/**
	 * Get the current game state.
	 * @return The current GameState enum value, or null if no state is active
	 */
	public GameState getCurrentState()
	{
		return _currentState;
	}
}
