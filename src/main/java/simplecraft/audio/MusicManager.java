package simplecraft.audio;

import simplecraft.world.DayNightCycle;

/**
 * Orchestrates music transitions based on game state: day/night phase, player<br>
 * submersion, and underground depth. Delegates all playback to {@link AudioManager}.<br>
 * <br>
 * Priority (highest first): water > underground > night > day.<br>
 * <br>
 * Water music overrides everything. Underground plays night music during daytime<br>
 * when the player is at least 2 blocks below world-generated dirt/grass.<br>
 * Both water and underground transitions use suspend/resume so tracks continue<br>
 * from where they left off on re-entry.<br>
 * <br>
 * All transitions use time-based delays to prevent rapid toggling from brief<br>
 * dips (water), cave entrance flickering (underground), or phase boundaries<br>
 * (day/night).
 * @author Pantelis Andrianakis
 * @since March 7th 2026
 */
public class MusicManager
{
	// ------------------------------------------------------------------
	// Music paths.
	// ------------------------------------------------------------------
	
	/** Music track played during daytime on the surface. */
	public static final String DAY_MUSIC_PATH = "music/perspectives.ogg";
	
	/** Music track played during nighttime and while underground. */
	public static final String NIGHT_MUSIC_PATH = "music/impressionist.ogg";
	
	/** Music track played while the player is submerged. Overrides all other music. */
	public static final String WATER_MUSIC_PATH = "music/memaloric.ogg";
	
	// ------------------------------------------------------------------
	// Transition timing.
	// ------------------------------------------------------------------
	
	/** Crossfade duration for day/night music transitions (seconds). */
	private static final float MUSIC_CROSSFADE_DURATION = 3.0f;
	
	/** Crossfade duration for water transitions (entering/exiting water). */
	private static final float WATER_CROSSFADE_DURATION = 3.0f;
	
	/** Crossfade duration for underground transitions. */
	private static final float UNDERGROUND_CROSSFADE_DURATION = 3.0f;
	
	/** Time the player must be continuously submerged before water music starts (seconds). */
	private static final float WATER_ENTER_DELAY = 2.0f;
	
	/** Time the player must be continuously out of water before music switches back (seconds). */
	private static final float WATER_EXIT_DELAY = 2.0f;
	
	/** Time the player must be continuously underground before underground music starts (seconds). */
	private static final float UNDERGROUND_ENTER_DELAY = 5.0f;
	
	/** Time the player must be continuously on the surface before underground music stops (seconds). */
	private static final float UNDERGROUND_EXIT_DELAY = 5.0f;
	
	// ------------------------------------------------------------------
	// Dependencies.
	// ------------------------------------------------------------------
	
	/** Audio manager for actual playback and crossfading. */
	private final AudioManager _audioManager;
	
	/** Day/night cycle for querying the current phase. */
	private final DayNightCycle _dayNightCycle;
	
	// ------------------------------------------------------------------
	// Water state.
	// ------------------------------------------------------------------
	
	/** Whether the water music override is currently active. */
	private boolean _wasSubmerged;
	
	/** Seconds the player has been continuously submerged (reset to 0 when surfacing). */
	private float _timeInWater;
	
	/** Seconds since the player was last in water (reset to 0 each frame in water). */
	private float _timeSinceWater = Float.MAX_VALUE;
	
	// ------------------------------------------------------------------
	// Underground state.
	// ------------------------------------------------------------------
	
	/**
	 * Whether the underground music override is currently active (delayed).<br>
	 * When true during daytime, night music plays instead of day music.
	 */
	private boolean _undergroundActive;
	
	/** Seconds the player has been continuously underground (reset to 0 on surface). */
	private float _timeUnderground;
	
	/** Seconds since the player was last underground (reset to 0 each frame underground). */
	private float _timeSinceUnderground = Float.MAX_VALUE;
	
	// ------------------------------------------------------------------
	// Day/night state.
	// ------------------------------------------------------------------
	
	/** Whether it was night on the previous frame. */
	private boolean _wasNight;
	
	/** Whether a music transition has been triggered for the current phase (prevents double-fire). */
	private boolean _musicTransitioned;
	
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
	 * Updates music state based on submersion, underground depth, and day/night phase.<br>
	 * Must be called every frame during gameplay.<br>
	 * <br>
	 * Music priority: water > underground > night > day.<br>
	 * Underground plays night music during daytime. All transitions except pure<br>
	 * day/night boundary crossings use suspend/resume for seamless track continuity.
	 * @param tpf time per frame in seconds
	 * @param submerged whether the player's head is currently underwater
	 * @param underground whether the player is at least 2 blocks below natural dirt/grass
	 */
	public void update(float tpf, boolean submerged, boolean underground)
	{
		// --- Track water and underground timers ---
		
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
		
		if (underground)
		{
			_timeUnderground += tpf;
			_timeSinceUnderground = 0f;
		}
		else
		{
			_timeUnderground = 0f;
			_timeSinceUnderground += tpf;
		}
		
		// --- Compute delayed underground state ---
		
		boolean newUnderground = _undergroundActive;
		if (_timeUnderground >= UNDERGROUND_ENTER_DELAY)
		{
			newUnderground = true;
		}
		if (_timeSinceUnderground >= UNDERGROUND_EXIT_DELAY)
		{
			newUnderground = false;
		}
		
		final boolean nightNow = _dayNightCycle.isNight();
		
		// The "effective night" combines actual night with underground.
		// When either is true, night music should play.
		// This simplifies all transition logic to a single boolean comparison.
		final boolean wasEffectiveNight = _wasNight || _undergroundActive;
		final boolean isEffectiveNight = nightNow || newUnderground;
		
		// ===============================================================
		// Priority 1: Water transitions.
		// ===============================================================
		
		if (_timeInWater >= WATER_ENTER_DELAY && !_wasSubmerged)
		{
			// Entering water — suspend whatever surface track is playing.
			_audioManager.crossfadeWithSuspend(WATER_MUSIC_PATH, WATER_CROSSFADE_DURATION);
			_wasSubmerged = true;
			_wasNight = nightNow;
			_undergroundActive = newUnderground;
			_musicTransitioned = true;
			System.out.println("MusicManager: Transitioning to underwater music.");
			return;
		}
		
		if (_timeSinceWater >= WATER_EXIT_DELAY && _wasSubmerged)
		{
			// Exiting water — resume the correct surface track.
			final String surfaceTrack = isEffectiveNight ? NIGHT_MUSIC_PATH : DAY_MUSIC_PATH;
			_audioManager.crossfadeWithSuspend(surfaceTrack, WATER_CROSSFADE_DURATION);
			_wasSubmerged = false;
			_wasNight = nightNow;
			_undergroundActive = newUnderground;
			_musicTransitioned = true;
			System.out.println("MusicManager: Surfacing, resuming " + (isEffectiveNight ? "night" : "day") + " music.");
			return;
		}
		
		// ===============================================================
		// While submerged: track state changes silently.
		// ===============================================================
		
		if (_wasSubmerged)
		{
			// If the effective surface music changed while underwater,
			// the suspended track is now stale. Clear it so the correct track
			// plays on water exit (loaded fresh if not in the map).
			if (wasEffectiveNight != isEffectiveNight)
			{
				final String staleTrack = wasEffectiveNight ? NIGHT_MUSIC_PATH : DAY_MUSIC_PATH;
				_audioManager.clearSuspended(staleTrack);
			}
			_wasNight = nightNow;
			_undergroundActive = newUnderground;
			return;
		}
		
		// ===============================================================
		// Priority 2 & 3: Underground and day/night transitions.
		// ===============================================================
		
		if (wasEffectiveNight != isEffectiveNight)
		{
			// The effective music phase changed — need a transition.
			final boolean undergroundChanged = newUnderground != _undergroundActive;
			
			if (undergroundChanged)
			{
				// Underground enter/exit caused the change — use suspend/resume
				// so the surface track continues from where it left off.
				final String track = isEffectiveNight ? NIGHT_MUSIC_PATH : DAY_MUSIC_PATH;
				_audioManager.crossfadeWithSuspend(track, UNDERGROUND_CROSSFADE_DURATION);
				System.out.println("MusicManager: " + (newUnderground ? "Entering" : "Leaving") + " underground, " + (isEffectiveNight ? "night" : "day") + " music.");
			}
			else if (!_musicTransitioned)
			{
				// Pure day/night boundary on the surface — fresh start.
				final String track = isEffectiveNight ? NIGHT_MUSIC_PATH : DAY_MUSIC_PATH;
				_audioManager.crossfadeTo(track, MUSIC_CROSSFADE_DURATION);
				System.out.println("MusicManager: Transitioning to " + (isEffectiveNight ? "night" : "day") + " music.");
			}
			
			_wasNight = nightNow;
			_undergroundActive = newUnderground;
			_musicTransitioned = true;
			return;
		}
		
		// ===============================================================
		// No effective change — clean up stale state if needed.
		// ===============================================================
		
		// Individual states may have changed without affecting the effective phase.
		// For example, night arrives while underground (effectiveNight stays true,
		// but the suspended day track from underground entry is now stale since day/night moved on).
		if (_undergroundActive && !_wasNight && nightNow)
		{
			// Was underground-day (day track suspended), night arrived.
			// The suspended day track is stale — clear it.
			_audioManager.clearSuspended(DAY_MUSIC_PATH);
			System.out.println("MusicManager: Night arrived while underground, cleared stale day track.");
		}
		
		_wasNight = nightNow;
		_undergroundActive = newUnderground;
		_musicTransitioned = false;
	}
}
