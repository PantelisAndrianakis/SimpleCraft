package simplecraft.audio;

import simplecraft.world.DayNightCycle;

/**
 * Orchestrates music transitions based on game state: day/night phase and<br>
 * player submersion. Delegates all actual playback to {@link AudioManager}.<br>
 * <br>
 * Priority: submerged > night > day. Water music overrides day/night tracks.<br>
 * Both the surface track and the water track are suspended (paused in place)<br>
 * during transitions, so re-entering water resumes water music from where it<br>
 * left off, and surfacing resumes the surface track from where it left off.<br>
 * <br>
 * Uses time-in-water and time-since-water trackers so the player must be<br>
 * continuously submerged for {@link #WATER_ENTER_DELAY} seconds before water<br>
 * music starts, and continuously on dry land for {@link #WATER_EXIT_DELAY}<br>
 * seconds before it switches back, preventing rapid toggling from brief dips<br>
 * or wave splashes. Day/night transitions are suppressed while submerged.
 * @author Pantelis Andrianakis
 * @since March 7th 2026
 */
public class MusicManager
{
	// ------------------------------------------------------------------
	// Music paths.
	// ------------------------------------------------------------------
	
	/** Music track played during daytime. */
	public static final String DAY_MUSIC_PATH = "music/perspectives.ogg";
	
	/** Music track played during nighttime. */
	public static final String NIGHT_MUSIC_PATH = "music/impressionist.ogg";
	
	/** Music track played while the player is submerged. Overrides day/night music. */
	public static final String WATER_MUSIC_PATH = "music/memaloric.ogg";
	
	// ------------------------------------------------------------------
	// Transition timing.
	// ------------------------------------------------------------------
	
	/** Crossfade duration for day/night music transitions (seconds). */
	private static final float MUSIC_CROSSFADE_DURATION = 3.0f;
	
	/** Crossfade duration for water transitions (entering/exiting water). */
	private static final float WATER_CROSSFADE_DURATION = 3.0f;
	
	/** Time the player must be continuously submerged before water music starts (seconds). */
	private static final float WATER_ENTER_DELAY = 2.0f;
	
	/** Time the player must be continuously out of water before music switches back (seconds). */
	private static final float WATER_EXIT_DELAY = 2.0f;
	
	// ------------------------------------------------------------------
	// Dependencies.
	// ------------------------------------------------------------------
	
	/** Audio manager for actual playback and crossfading. */
	private final AudioManager _audioManager;
	
	/** Day/night cycle for querying the current phase. */
	private final DayNightCycle _dayNightCycle;
	
	// ------------------------------------------------------------------
	// State.
	// ------------------------------------------------------------------
	
	/** Whether it was night on the previous frame (for day/night transition detection). */
	private boolean _wasNight;
	
	/** Whether a music transition has been triggered for the current day/night phase. */
	private boolean _musicTransitioned;
	
	/** Whether the water music override is currently active. */
	private boolean _wasSubmerged;
	
	/** Seconds the player has been continuously submerged (reset to 0 when surfacing). */
	private float _timeInWater;
	
	/** Seconds since the player was last in water (reset to 0 each frame in water). */
	private float _timeSinceWater = Float.MAX_VALUE;
	
	// ------------------------------------------------------------------
	// Constructor.
	// ------------------------------------------------------------------
	
	/**
	 * Creates a new music manager.
	 * @param audioManager the audio manager for playback
	 * @param dayNightCycle the day/night cycle for phase queries
	 */
	public MusicManager(AudioManager audioManager, DayNightCycle dayNightCycle)
	{
		_audioManager = audioManager;
		_dayNightCycle = dayNightCycle;
		_wasNight = dayNightCycle.isNight();
		_musicTransitioned = true; // Prevent triggering on first frame.
		
		System.out.println("MusicManager initialized. Night: " + _wasNight);
	}
	
	// ------------------------------------------------------------------
	// Update.
	// ------------------------------------------------------------------
	
	/**
	 * Updates music state based on submersion and day/night phase.<br>
	 * Must be called every frame.
	 * @param tpf time per frame in seconds
	 * @param submerged whether the player's is currently underwater
	 */
	public void update(float tpf, boolean submerged)
	{
		// Track time in/out of water.
		if (submerged)
		{
			_timeInWater += tpf;
			_timeSinceWater = 0f;
		}
		else
		{
			_timeInWater = 0f;
			_timeSinceWater += tpf;
		}
		
		final boolean nightNow = _dayNightCycle.isNight();
		
		// --- Water transitions (highest priority) ---
		
		if (_timeInWater >= WATER_ENTER_DELAY && !_wasSubmerged)
		{
			// Entering water — suspend the surface track, start or resume water music.
			_audioManager.crossfadeWithSuspend(WATER_MUSIC_PATH, WATER_CROSSFADE_DURATION);
			_wasSubmerged = true;
			_wasNight = nightNow;
			_musicTransitioned = true;
			System.out.println("MusicManager: Transitioning to underwater music.");
			return;
		}
		
		if (_timeSinceWater >= WATER_EXIT_DELAY && _wasSubmerged)
		{
			// Out of water long enough — suspend water music, resume surface track.
			final String surfaceTrack = nightNow ? NIGHT_MUSIC_PATH : DAY_MUSIC_PATH;
			_audioManager.crossfadeWithSuspend(surfaceTrack, WATER_CROSSFADE_DURATION);
			_wasSubmerged = false;
			_wasNight = nightNow;
			_musicTransitioned = true;
			System.out.println("MusicManager: Surfacing, resuming " + (nightNow ? "night" : "day") + " music.");
			return;
		}
		
		// --- Day/night transitions (only when not in water) ---
		if (_wasSubmerged)
		{
			// While water music active, just track night state silently.
			// If day/night changed, clear the old surface track — it is no longer the correct track.
			if (nightNow != _wasNight)
			{
				final String oldTrack = nightNow ? DAY_MUSIC_PATH : NIGHT_MUSIC_PATH;
				_audioManager.clearSuspended(oldTrack);
			}
			_wasNight = nightNow;
			return;
		}
		
		if (nightNow != _wasNight)
		{
			if (!_musicTransitioned)
			{
				if (nightNow)
				{
					_audioManager.crossfadeTo(NIGHT_MUSIC_PATH, MUSIC_CROSSFADE_DURATION);
					System.out.println("MusicManager: Transitioning to night music.");
				}
				else
				{
					_audioManager.crossfadeTo(DAY_MUSIC_PATH, MUSIC_CROSSFADE_DURATION);
					System.out.println("MusicManager: Transitioning to day music.");
				}
				
				_musicTransitioned = true;
			}
			
			_wasNight = nightNow;
		}
		else
		{
			// Reset transition flag once we are firmly in the current phase.
			_musicTransitioned = false;
		}
	}
}
