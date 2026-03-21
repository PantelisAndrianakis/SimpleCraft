package simplecraft.world;

import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;

/**
 * Manages the day/night cycle including time progression, sky brightness,<br>
 * sky color tinting and viewport background color.<br>
 * <br>
 * Time is represented as a float from 0.0 (midnight) to 1.0:<br>
 * 0.0 = midnight, 0.25 = sunrise, 0.5 = noon, 0.75 = sunset.<br>
 * <br>
 * Sky brightness follows a sinusoidal curve clamped to a minimum of 0.1<br>
 * so the surface is never pitch black. This value is used by RegionMeshBuilder<br>
 * to modulate vertex colors: {@code finalSkyLight = region.getSkyLight(x,y,z) * getSkyBrightness()}.<br>
 * <br>
 * Sky tint provides a color multiplier applied to vertex colors for warm/cool<br>
 * atmospheric effects (slight blue tint at night, neutral white at noon).<br>
 * <br>
 * The viewport background color smoothly transitions through day blue,<br>
 * sunset orange-pink, night dark blue-black and sunrise warm tones.<br>
 * <br>
 * A full cycle takes {@link #DAY_LENGTH_SECONDS} real-time seconds (default 1200 = 20 minutes).
 * @author Pantelis Andrianakis
 * @since March 7th 2026
 */
public class DayNightCycle
{
	// ------------------------------------------------------------------
	// Time constants.
	// ------------------------------------------------------------------
	
	/** Real-time seconds for one full day/night cycle. */
	private static final float DAY_LENGTH_SECONDS = 1200f;
	
	/** Two PI constant for sinusoidal calculations. */
	private static final float TWO_PI = FastMath.TWO_PI;
	
	// ------------------------------------------------------------------
	// Night boundary constants.
	// ------------------------------------------------------------------
	
	/** Time of day at which night begins (just after sunset). */
	private static final float NIGHT_START = 0.8f;
	
	/** Time of day at which night ends (just before sunrise). */
	private static final float NIGHT_END = 0.2f;
	
	// ------------------------------------------------------------------
	// Sky brightness constants.
	// ------------------------------------------------------------------
	
	/** Minimum sky brightness (never pitch black on surface). */
	private static final float MIN_BRIGHTNESS = 0.1f;
	
	// ------------------------------------------------------------------
	// Sky tint colors.
	// ------------------------------------------------------------------
	
	/** Neutral daytime tint (no color shift). */
	private static final ColorRGBA TINT_DAY = new ColorRGBA(1.0f, 1.0f, 1.0f, 1.0f);
	
	/** Cool blue nighttime tint. */
	private static final ColorRGBA TINT_NIGHT = new ColorRGBA(0.3f, 0.3f, 0.5f, 1.0f);
	
	// ------------------------------------------------------------------
	// Viewport sky color keyframes (time -> color).
	// ------------------------------------------------------------------
	
	/** Midnight: deep dark blue-black. */
	private static final ColorRGBA SKY_MIDNIGHT = new ColorRGBA(0.02f, 0.02f, 0.06f, 1.0f);
	
	/** Pre-dawn: very dark blue with hint of purple. */
	private static final ColorRGBA SKY_PRE_DAWN = new ColorRGBA(0.08f, 0.06f, 0.15f, 1.0f);
	
	/** Sunrise: warm orange-pink horizon. */
	private static final ColorRGBA SKY_SUNRISE = new ColorRGBA(0.85f, 0.55f, 0.35f, 1.0f);
	
	/** Morning: transitioning to clear blue. */
	private static final ColorRGBA SKY_MORNING = new ColorRGBA(0.53f, 0.72f, 0.88f, 1.0f);
	
	/** Noon: bright clear blue. */
	private static final ColorRGBA SKY_NOON = new ColorRGBA(0.53f, 0.81f, 0.92f, 1.0f);
	
	/** Afternoon: slightly warmer blue. */
	private static final ColorRGBA SKY_AFTERNOON = new ColorRGBA(0.55f, 0.75f, 0.88f, 1.0f);
	
	/** Sunset: deep orange-pink. */
	private static final ColorRGBA SKY_SUNSET = new ColorRGBA(0.90f, 0.45f, 0.25f, 1.0f);
	
	/** Dusk: fading purple-blue. */
	private static final ColorRGBA SKY_DUSK = new ColorRGBA(0.15f, 0.10f, 0.25f, 1.0f);
	
	// ------------------------------------------------------------------
	// Sky color keyframe times.
	// ------------------------------------------------------------------
	
	/** Ordered keyframe times matching the color array. */
	private static final float[] SKY_TIMES =
	{
		0.00f, // midnight
		0.18f, // pre-dawn
		0.25f, // sunrise
		0.33f, // morning
		0.50f, // noon
		0.65f, // afternoon
		0.75f, // sunset
		0.85f, // dusk
	};
	
	/** Ordered keyframe colors matching the times array. */
	private static final ColorRGBA[] SKY_COLORS =
	{
		SKY_MIDNIGHT,
		SKY_PRE_DAWN,
		SKY_SUNRISE,
		SKY_MORNING,
		SKY_NOON,
		SKY_AFTERNOON,
		SKY_SUNSET,
		SKY_DUSK,
	};
	
	// ------------------------------------------------------------------
	// Terrain brightness (gradual day/night transition).
	// ------------------------------------------------------------------
	
	/** Fixed terrain brightness during daytime. */
	private static final float TERRAIN_BRIGHTNESS_DAY = 1.0f;
	
	/** Fixed terrain brightness during nighttime. */
	private static final float TERRAIN_BRIGHTNESS_NIGHT = 0.2f;
	
	/** Terrain tint during daytime (neutral white). */
	private static final ColorRGBA TERRAIN_TINT_DAY = new ColorRGBA(1.0f, 1.0f, 1.0f, 1.0f);
	
	/** Terrain tint during nighttime (cool blue). */
	private static final ColorRGBA TERRAIN_TINT_NIGHT = new ColorRGBA(0.3f, 0.3f, 0.5f, 1.0f);
	
	/** Duration of the terrain brightness/tint transition in seconds. */
	private static final float TERRAIN_TRANSITION_DURATION = 30.0f;
	
	/** Minimum terrain brightness change between rebuilds during a transition. */
	private static final float TERRAIN_REBUILD_THRESHOLD = 0.02f;
	
	// ------------------------------------------------------------------
	// Fields.
	// ------------------------------------------------------------------
	
	/** Current time of day (0.0 = midnight, 0.5 = noon, 1.0 wraps to 0.0). */
	private float _timeOfDay;
	
	/** Current sky brightness multiplier (0.0-1.0). */
	private float _skyBrightness;
	
	/** Current sky tint color applied to vertex colors. */
	private final ColorRGBA _skyTint = new ColorRGBA(1.0f, 1.0f, 1.0f, 1.0f);
	
	/** Current viewport background sky color. */
	private final ColorRGBA _skyColor = new ColorRGBA(0.53f, 0.81f, 0.92f, 1.0f);
	
	/** Current terrain brightness (lerped during transitions). */
	private float _terrainBrightness;
	
	/** Current terrain tint (lerped during transitions). */
	private final ColorRGBA _terrainTint = new ColorRGBA(1.0f, 1.0f, 1.0f, 1.0f);
	
	/** Target terrain brightness for the current phase. */
	private float _terrainBrightnessTarget;
	
	/** Target terrain tint for the current phase. */
	private final ColorRGBA _terrainTintTarget = new ColorRGBA(1.0f, 1.0f, 1.0f, 1.0f);
	
	/** Terrain brightness at the start of the transition. */
	private float _terrainBrightnessStart;
	
	/** Terrain tint at the start of the transition. */
	private final ColorRGBA _terrainTintStart = new ColorRGBA(1.0f, 1.0f, 1.0f, 1.0f);
	
	/** Elapsed time within the current terrain transition (0 to TERRAIN_TRANSITION_DURATION). */
	private float _terrainTransitionElapsed;
	
	/** Whether a terrain transition is currently in progress. */
	private boolean _terrainTransitioning;
	
	/** Whether a terrain mesh rebuild is needed this frame. */
	private boolean _terrainRebuildNeeded;
	
	/** Last terrain brightness value when a rebuild was triggered (prevents redundant rebuilds). */
	private float _lastTerrainRebuildBrightness;
	
	/** Whether a day/night phase change occurred this frame. */
	private boolean _phaseChanged;
	
	/** Whether it was night on the previous frame (for terrain transition detection). */
	private boolean _wasNight;
	
	// ------------------------------------------------------------------
	// Constructor.
	// ------------------------------------------------------------------
	
	/**
	 * Creates a new day/night cycle starting at the specified time.
	 * @param startTime initial time of day (0.0-1.0)
	 */
	public DayNightCycle(float startTime)
	{
		_timeOfDay = Math.max(0f, Math.min(1f, startTime));
		_wasNight = isNight();
		
		// Compute initial values.
		updateSkyBrightness();
		updateSkyTint();
		updateSkyColor();
		
		// Set initial terrain values instantly (no transition on first load).
		if (isNight())
		{
			_terrainBrightness = TERRAIN_BRIGHTNESS_NIGHT;
			_terrainTint.set(TERRAIN_TINT_NIGHT);
		}
		else
		{
			_terrainBrightness = TERRAIN_BRIGHTNESS_DAY;
			_terrainTint.set(TERRAIN_TINT_DAY);
		}
		
		_terrainBrightnessTarget = _terrainBrightness;
		_terrainTintTarget.set(_terrainTint);
		_lastTerrainRebuildBrightness = _terrainBrightness;
		
		System.out.println("DayNightCycle initialized. Time: " + _timeOfDay + ", Brightness: " + _skyBrightness + ", Night: " + isNight() + ", TerrainBrightness: " + _terrainBrightness);
	}
	
	// ------------------------------------------------------------------
	// Update.
	// ------------------------------------------------------------------
	
	/**
	 * Advances the cycle and updates all derived values.<br>
	 * Must be called every frame.
	 * @param tpf time per frame in seconds
	 */
	public void update(float tpf)
	{
		// Advance time.
		_timeOfDay += tpf / DAY_LENGTH_SECONDS;
		if (_timeOfDay >= 1.0f)
		{
			_timeOfDay -= 1.0f;
		}
		
		// Update derived values.
		updateSkyBrightness();
		updateSkyTint();
		updateSkyColor();
		
		// Detect day/night phase change - start a gradual terrain transition.
		_phaseChanged = false;
		if (isNight() != _wasNight)
		{
			startTerrainTransition();
			_phaseChanged = true;
			System.out.println("DayNightCycle: Phase transition started to " + (isNight() ? "NIGHT" : "DAY") + ". Target brightness: " + _terrainBrightnessTarget);
		}
		
		_wasNight = isNight();
		
		// Advance terrain transition (lerp brightness and tint toward target).
		updateTerrainTransition(tpf);
	}
	
	// ------------------------------------------------------------------
	// Sky brightness.
	// ------------------------------------------------------------------
	
	/**
	 * Computes sky brightness using a sinusoidal curve.<br>
	 * Noon (0.5) = 1.0, Midnight (0.0) = 0.1, Sunrise/Sunset (0.25/0.75) = 0.55.
	 */
	private void updateSkyBrightness()
	{
		// Sinusoidal curve: peaks at noon (0.5), troughs at midnight (0.0).
		// sin((0.5 - 0.25) * TWO_PI) = sin(PI/2) = 1.0 -> 0.55 + 0.45 = 1.0
		// sin((0.0 - 0.25) * TWO_PI) = sin(-PI/2) = -1.0 -> 0.55 - 0.45 = 0.1
		final float raw = 0.55f + 0.45f * FastMath.sin((_timeOfDay - 0.25f) * TWO_PI);
		_skyBrightness = Math.max(MIN_BRIGHTNESS, Math.min(1.0f, raw));
	}
	
	// ------------------------------------------------------------------
	// Sky tint.
	// ------------------------------------------------------------------
	
	/**
	 * Computes the sky tint color by interpolating between day (white) and night (blue).<br>
	 * Uses the sky brightness as the interpolation factor for smooth transitions.
	 */
	private void updateSkyTint()
	{
		// Use brightness as a natural interpolation factor:
		// brightness 1.0 (noon) -> full day tint, brightness 0.1 (midnight) -> full night tint.
		// Remap brightness [0.1, 1.0] -> [0.0, 1.0] for smoother tint transitions.
		final float t = Math.max(0f, Math.min(1f, (_skyBrightness - MIN_BRIGHTNESS) / (1.0f - MIN_BRIGHTNESS)));
		
		_skyTint.r = TINT_NIGHT.r + (TINT_DAY.r - TINT_NIGHT.r) * t;
		_skyTint.g = TINT_NIGHT.g + (TINT_DAY.g - TINT_NIGHT.g) * t;
		_skyTint.b = TINT_NIGHT.b + (TINT_DAY.b - TINT_NIGHT.b) * t;
	}
	
	// ------------------------------------------------------------------
	// Viewport sky color.
	// ------------------------------------------------------------------
	
	/**
	 * Interpolates the viewport background color between keyframe colors<br>
	 * based on the current time of day. Wraps from the last keyframe back<br>
	 * to the first for seamless midnight transitions.
	 */
	private void updateSkyColor()
	{
		final float t = _timeOfDay;
		final int count = SKY_TIMES.length;
		
		// Find the two keyframes surrounding the current time.
		int idx0 = count - 1;
		int idx1 = 0;
		
		for (int i = 0; i < count - 1; i++)
		{
			if (t >= SKY_TIMES[i] && t < SKY_TIMES[i + 1])
			{
				idx0 = i;
				idx1 = i + 1;
				break;
			}
		}
		
		// Handle wrap-around (time between last keyframe and midnight/first keyframe).
		final float t0 = SKY_TIMES[idx0];
		final float t1;
		if (idx1 == 0 && idx0 == count - 1)
		{
			// Wrapping: last keyframe -> first keyframe (across midnight).
			t1 = 1.0f + SKY_TIMES[0]; // Treat first keyframe as being at 1.0+.
		}
		else
		{
			t1 = SKY_TIMES[idx1];
		}
		
		// Calculate interpolation factor.
		final float span = t1 - t0;
		final float factor = (span > 0f) ? ((t - t0) / span) : 0f;
		
		// Smooth-step for more natural transitions.
		final float smooth = factor * factor * (3.0f - 2.0f * factor);
		
		final ColorRGBA c0 = SKY_COLORS[idx0];
		final ColorRGBA c1 = SKY_COLORS[idx1];
		
		_skyColor.r = c0.r + (c1.r - c0.r) * smooth;
		_skyColor.g = c0.g + (c1.g - c0.g) * smooth;
		_skyColor.b = c0.b + (c1.b - c0.b) * smooth;
	}
	
	// ------------------------------------------------------------------
	// Terrain transition (gradual day/night brightness lerp).
	// ------------------------------------------------------------------
	
	/**
	 * Starts a gradual terrain transition toward the new phase's brightness and tint.<br>
	 * Captures the current values as the start point and sets the target based on<br>
	 * whether it is now night or day. The transition lerps over {@link #TERRAIN_TRANSITION_DURATION}.
	 */
	private void startTerrainTransition()
	{
		// Capture current values as the starting point.
		_terrainBrightnessStart = _terrainBrightness;
		_terrainTintStart.set(_terrainTint);
		
		// Set target based on new phase.
		if (isNight())
		{
			_terrainBrightnessTarget = TERRAIN_BRIGHTNESS_NIGHT;
			_terrainTintTarget.set(TERRAIN_TINT_NIGHT);
		}
		else
		{
			_terrainBrightnessTarget = TERRAIN_BRIGHTNESS_DAY;
			_terrainTintTarget.set(TERRAIN_TINT_DAY);
		}
		
		_terrainTransitionElapsed = 0f;
		_terrainTransitioning = true;
	}
	
	/**
	 * Advances the terrain transition each frame, lerping brightness and tint toward the target.<br>
	 * Triggers a terrain rebuild when the brightness has changed enough since the last rebuild.<br>
	 * When the transition completes, snaps to the exact target values and triggers a final rebuild.
	 * @param tpf time per frame in seconds
	 */
	private void updateTerrainTransition(float tpf)
	{
		_terrainRebuildNeeded = false;
		
		if (!_terrainTransitioning)
		{
			return;
		}
		
		_terrainTransitionElapsed += tpf;
		
		if (_terrainTransitionElapsed >= TERRAIN_TRANSITION_DURATION)
		{
			// Transition complete - snap to exact target.
			_terrainBrightness = _terrainBrightnessTarget;
			_terrainTint.set(_terrainTintTarget);
			_terrainTransitioning = false;
			_terrainRebuildNeeded = true;
			_lastTerrainRebuildBrightness = _terrainBrightness;
			return;
		}
		
		// Smooth-step interpolation for natural easing.
		final float t = _terrainTransitionElapsed / TERRAIN_TRANSITION_DURATION;
		final float smooth = t * t * (3.0f - 2.0f * t);
		
		// Lerp brightness.
		_terrainBrightness = _terrainBrightnessStart + (_terrainBrightnessTarget - _terrainBrightnessStart) * smooth;
		
		// Lerp tint.
		_terrainTint.r = _terrainTintStart.r + (_terrainTintTarget.r - _terrainTintStart.r) * smooth;
		_terrainTint.g = _terrainTintStart.g + (_terrainTintTarget.g - _terrainTintStart.g) * smooth;
		_terrainTint.b = _terrainTintStart.b + (_terrainTintTarget.b - _terrainTintStart.b) * smooth;
		
		// Trigger rebuild when brightness has changed enough since last rebuild.
		if (Math.abs(_terrainBrightness - _lastTerrainRebuildBrightness) >= TERRAIN_REBUILD_THRESHOLD)
		{
			_terrainRebuildNeeded = true;
			_lastTerrainRebuildBrightness = _terrainBrightness;
		}
	}
	
	// ------------------------------------------------------------------
	// Public API.
	// ------------------------------------------------------------------
	
	/**
	 * Returns whether it is currently night.<br>
	 * Night spans from {@link #NIGHT_START} (0.8) through midnight to {@link #NIGHT_END} (0.2).
	 * @return true if the current time is within the night range
	 */
	public boolean isNight()
	{
		return _timeOfDay >= NIGHT_START || _timeOfDay < NIGHT_END;
	}
	
	/**
	 * Returns the current time of day.
	 * @return float from 0.0 (midnight) to 1.0
	 */
	public float getTimeOfDay()
	{
		return _timeOfDay;
	}
	
	/**
	 * Returns the current sky brightness multiplier.<br>
	 * Used by RegionMeshBuilder to modulate vertex sky light values.
	 * @return float from 0.1 (midnight minimum) to 1.0 (noon)
	 */
	public float getSkyBrightness()
	{
		return _skyBrightness;
	}
	
	/**
	 * Returns the current sky tint color.<br>
	 * Applied to vertex colors as a multiplier for atmospheric color shifts.<br>
	 * Ranges from white (1,1,1) at noon to cool blue (0.3, 0.3, 0.5) at midnight.
	 * @return the current sky tint color (do not modify)
	 */
	public ColorRGBA getSkyTint()
	{
		return _skyTint;
	}
	
	/**
	 * Returns the current viewport background sky color.<br>
	 * Smoothly interpolated between keyframe colors throughout the day.
	 * @return the current sky color for viewport background (do not modify)
	 */
	public ColorRGBA getSkyColor()
	{
		return _skyColor;
	}
	
	/**
	 * Returns whether a day/night phase change occurred this frame.<br>
	 * True for exactly one frame when the day/night boundary is crossed.
	 * @return true if the day/night boundary was just crossed
	 */
	public boolean isPhaseChanged()
	{
		return _phaseChanged;
	}
	
	/**
	 * Returns whether visible region meshes should be rebuilt this frame.<br>
	 * True periodically during the 30-second terrain transition as brightness changes,<br>
	 * and once at the end when the transition completes. All regions read the same<br>
	 * brightness value from RegionMeshBuilder's volatile fields, preventing patchwork.
	 * @return true if terrain mesh rebuilds should be triggered
	 */
	public boolean isTerrainRebuildNeeded()
	{
		return _terrainRebuildNeeded;
	}
	
	/**
	 * Returns the current terrain brightness (lerped during transitions).<br>
	 * Gradually moves from the previous phase's value to the target over<br>
	 * {@link #TERRAIN_TRANSITION_DURATION} seconds using smooth-step interpolation.
	 * @return terrain brightness multiplier for vertex colors
	 */
	public float getTerrainBrightness()
	{
		return _terrainBrightness;
	}
	
	/**
	 * Returns the current terrain tint (lerped during transitions).<br>
	 * Gradually moves from the previous phase's tint to the target.
	 * @return terrain tint color for vertex colors (do not modify)
	 */
	public ColorRGBA getTerrainTint()
	{
		return _terrainTint;
	}
	
	/**
	 * Returns the full cycle duration in real-time seconds.
	 * @return day length in seconds
	 */
	public float getDayLengthSeconds()
	{
		return DAY_LENGTH_SECONDS;
	}
}
