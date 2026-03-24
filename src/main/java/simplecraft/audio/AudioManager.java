package simplecraft.audio;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.jme3.asset.AssetManager;
import com.jme3.audio.AudioData.DataType;
import com.jme3.audio.AudioNode;
import com.jme3.math.Vector3f;

/**
 * Manages all audio playback including music with crossfading and sound effects.<br>
 * Gracefully handles missing audio files without crashing.<br>
 * <br>
 * Music is always played as streamed audio ({@link DataType#Stream}) for instant<br>
 * startup with no decoding delay.<br>
 * <br>
 * Supports suspending tracks into a map (fade to zero, then pause). When a<br>
 * suspended track is requested again it is unpaused at the exact position<br>
 * instead of being reloaded - no seeking required. This allows any number<br>
 * of tracks to be paused and resumed independently (e.g. surface music<br>
 * while underwater and water music while on the surface).
 * @author Pantelis Andrianakis
 * @since February 16th 2026
 */
public class AudioManager
{
	// Public SFX paths - UI.
	public static final String UI_CLICK_SFX_PATH = "sounds/ui/click.ogg";
	
	// Public SFX paths - player footsteps.
	public static final String SFX_STEP_GRASS = "sounds/player/step_grass.ogg";
	public static final String SFX_STEP_DIRT = "sounds/player/step_dirt.ogg";
	public static final String SFX_STEP_STONE = "sounds/player/step_stone.ogg";
	public static final String SFX_STEP_SAND = "sounds/player/step_sand.ogg";
	public static final String SFX_STEP_WOOD = "sounds/player/step_wood.ogg";
	
	// Public SFX paths - player combat.
	public static final String SFX_PLAYER_HURT = "sounds/player/player_hurt.ogg";
	public static final String SFX_PLAYER_DEATH = "sounds/player/player_death.ogg";
	
	// Public SFX paths - block interaction.
	public static final String SFX_BLOCK_HIT = "sounds/blocks/block_hit.ogg";
	public static final String SFX_BLOCK_BREAK = "sounds/blocks/block_break.ogg";
	public static final String SFX_BLOCK_PLACE = "sounds/blocks/block_place.ogg";
	
	// Public SFX paths - enemy ambient.
	public static final String SFX_ZOMBIE_GROAN = "sounds/enemies/zombie_groan.ogg";
	public static final String SFX_SKELETON_RATTLE = "sounds/enemies/skeleton_rattle.ogg";
	public static final String SFX_WOLF_GROWL = "sounds/enemies/wolf_growl.ogg";
	public static final String SFX_SPIDER_HISS = "sounds/enemies/spider_hiss.ogg";
	public static final String SFX_SLIME_SQUELCH = "sounds/enemies/slime_squelch.ogg";
	public static final String SFX_DRAGON = "sounds/enemies/dragon.ogg";
	public static final String SFX_SHADOW = "sounds/enemies/shadow.ogg";
	
	// Public SFX paths - enemy combat.
	public static final String SFX_ENEMY_HIT = "sounds/enemies/enemy_hit.ogg";
	public static final String SFX_ENEMY_DEATH = "sounds/enemies/enemy_death.ogg";
	
	// Asset manager used.
	private final AssetManager _assetManager;
	
	// Volume controls (0.0 - 1.0).
	private float _masterVolume = 0.7f;
	private float _musicVolume = 0.7f;
	private float _sfxVolume = 0.7f;
	
	// Current music track.
	private AudioNode _currentMusic;
	private String _currentMusicPath;
	
	// Crossfade state.
	private AudioNode _fadingOutMusic;
	private AudioNode _fadingInMusic;
	private float _fadeTimer = 0f;
	private float _fadeDuration = 0f;
	private float _fadeOutStartVolume = 0f;
	
	/**
	 * When true, the fading-out track will be paused instead of stopped when<br>
	 * the crossfade completes and stored in the suspended tracks map.
	 */
	private boolean _pauseOnFadeOut;
	
	/**
	 * The asset path of the track currently fading out, captured at the moment<br>
	 * the crossfade begins. Used to store the paused AudioNode under the correct<br>
	 * key when the fade completes (since {@code _currentMusicPath} has already<br>
	 * been overwritten by that point).
	 */
	private String _suspendingPath;
	
	// Fade-in state for simple fade-ins (not crossfades).
	private boolean _fadingIn = false;
	private float _fadeInTargetVolume = 0f;
	private float _fadeInDuration = 0f;
	private float _fadeInTimer = 0f;
	
	// ------------------------------------------------------------------
	// Suspended tracks (paused in place for seamless resume).
	// ------------------------------------------------------------------
	
	/**
	 * Map of tracks that have been suspended (faded to zero then paused).<br>
	 * Keyed by asset path. When a track is requested via {@link #crossfadeWithSuspend},<br>
	 * the map is checked first - if the track exists here it is unpaused at the<br>
	 * exact position instead of being reloaded from disk.
	 */
	private final Map<String, AudioNode> _suspendedTracks = new HashMap<>();
	
	// Audio cache.
	private final ConcurrentHashMap<String, AudioNode> _sfxCache;
	
	public AudioManager(AssetManager assetManager)
	{
		_assetManager = assetManager;
		_sfxCache = new ConcurrentHashMap<>();
		
		System.out.println("AudioManager initialized. Master: " + _masterVolume + ", Music: " + _musicVolume + ", SFX: " + _sfxVolume);
	}
	
	// ------------------------------------------------------------------
	// Crossfade interruption handling.
	// ------------------------------------------------------------------
	
	/**
	 * Stops any in-progress crossfade so a new one can start cleanly.<br>
	 * <br>
	 * Without this, starting a new crossfade while one is still running would overwrite<br>
	 * the fade state fields ({@code _fadingOutMusic}, {@code _suspendingPath}, etc.),<br>
	 * causing the old fading-out track to be abandoned - never paused, never stopped,<br>
	 * just leaked. That track would then be missing from the suspended map on the next<br>
	 * resume attempt, forcing a fresh load (position reset).<br>
	 * <br>
	 * The outgoing track is paused (if suspending) or stopped immediately.<br>
	 * The incoming track is left at whatever volume it has reached - the new<br>
	 * crossfade will capture that as its starting volume for a smooth handoff.
	 */
	private void stopActiveCrossfade()
	{
		// Settle outgoing track.
		if (_fadingOutMusic != null)
		{
			if (_pauseOnFadeOut)
			{
				// Pause and store in the suspended map (same as what update() would do).
				_fadingOutMusic.setVolume(0f);
				_fadingOutMusic.pause();
				if (_suspendingPath != null)
				{
					_suspendedTracks.put(_suspendingPath, _fadingOutMusic);
					System.out.println("AudioManager: Stopped in-progress suspend: " + _suspendingPath);
				}
				
				_pauseOnFadeOut = false;
				_suspendingPath = null;
			}
			else
			{
				_fadingOutMusic.stop();
			}
			
			_fadingOutMusic = null;
		}
		
		// Leave the incoming track at its current volume - the next crossfade
		// will pick it up from there via _fadeOutStartVolume for a smooth transition.
		_fadingInMusic = null;
		
		// Clear simple fade-in if active.
		_fadingIn = false;
	}
	
	// ------------------------------------------------------------------
	// Music playback.
	// ------------------------------------------------------------------
	
	/**
	 * Play a music track on loop. Stops any currently playing music.
	 * @param assetPath Path to the .ogg file (e.g., "music/perspectives.ogg")
	 */
	public void playMusic(String assetPath)
	{
		// Skip if this track is already playing.
		if (assetPath.equals(_currentMusicPath) && _currentMusic != null)
		{
			return;
		}
		
		try
		{
			// Settle any in-progress crossfade before changing tracks.
			stopActiveCrossfade();
			
			// Clear all suspended tracks (fresh start).
			clearAllSuspended();
			
			// Stop current music if playing.
			if (_currentMusic != null)
			{
				_currentMusic.stop();
			}
			
			// Load and play new music (Stream = instant start, no decoding delay).
			final AudioNode music = new AudioNode(_assetManager, assetPath, DataType.Stream);
			music.setLooping(true);
			music.setPositional(false);
			music.setVolume(_masterVolume * _musicVolume);
			music.play();
			
			_currentMusic = music;
			_currentMusicPath = assetPath;
			System.out.println("AudioManager: Playing music: " + assetPath);
		}
		catch (Exception e)
		{
			System.err.println("WARNING: Could not load music: " + assetPath + " - " + e.getMessage());
		}
	}
	
	/**
	 * Play a music track with a fade-in from silence.
	 * @param assetPath Path to the .ogg file
	 * @param fadeDuration Duration of the fade-in in seconds
	 */
	public void fadeInMusic(String assetPath, float fadeDuration)
	{
		// Skip if this track is already playing.
		if (assetPath.equals(_currentMusicPath) && _currentMusic != null)
		{
			return;
		}
		
		try
		{
			// Settle any in-progress crossfade before changing tracks.
			stopActiveCrossfade();
			
			// Clear all suspended tracks (fresh start).
			clearAllSuspended();
			
			// Stop current music if playing.
			if (_currentMusic != null)
			{
				_currentMusic.stop();
			}
			
			// Load new music and start at volume 0 (Stream = instant start).
			final AudioNode music = new AudioNode(_assetManager, assetPath, DataType.Stream);
			music.setLooping(true);
			music.setPositional(false);
			music.setVolume(0f);
			music.play();
			
			_currentMusic = music;
			_currentMusicPath = assetPath;
			
			// Setup fade-in state.
			_fadingIn = true;
			_fadeInTimer = 0f;
			_fadeInDuration = fadeDuration;
			_fadeInTargetVolume = _masterVolume * _musicVolume;
			
			System.out.println("AudioManager: Fading in music: " + assetPath + " over " + fadeDuration + "s");
		}
		catch (Exception e)
		{
			System.err.println("WARNING: Could not load music for fade-in: " + assetPath + " - " + e.getMessage());
		}
	}
	
	/**
	 * Crossfade from current music to a new track over a specified duration.<br>
	 * The new track starts from the beginning. The old track is stopped when the<br>
	 * crossfade completes. All suspended tracks are also stopped and discarded.
	 * @param assetPath Path to the new music file
	 * @param duration Fade duration in seconds
	 */
	public void crossfadeTo(String assetPath, float duration)
	{
		// Skip if this track is already playing.
		if (assetPath.equals(_currentMusicPath) && _currentMusic != null)
		{
			return;
		}
		
		try
		{
			// Settle any in-progress crossfade before changing tracks.
			stopActiveCrossfade();
			
			// Clear all suspended tracks (moving to a completely new track).
			clearAllSuspended();
			
			// Load new music (Stream = instant start, always from beginning).
			final AudioNode newMusic = new AudioNode(_assetManager, assetPath, DataType.Stream);
			newMusic.setLooping(true);
			newMusic.setPositional(false);
			newMusic.setVolume(0f);
			newMusic.play();
			
			// Setup crossfade state.
			_fadingOutMusic = _currentMusic;
			_fadingInMusic = newMusic;
			_fadeTimer = 0f;
			_fadeDuration = duration;
			_fadeOutStartVolume = (_currentMusic != null) ? _currentMusic.getVolume() : 0f;
			_pauseOnFadeOut = false;
			
			_currentMusic = newMusic;
			_currentMusicPath = assetPath;
			
			System.out.println("AudioManager: Crossfading to: " + assetPath + " over " + duration + "s");
		}
		catch (Exception e)
		{
			System.err.println("WARNING: Could not load music for crossfade: " + assetPath + " - " + e.getMessage());
		}
	}
	
	// ------------------------------------------------------------------
	// Suspend / resume.
	// ------------------------------------------------------------------
	
	/**
	 * Crossfade from the current track to a different track, suspending the<br>
	 * outgoing track (faded to zero then paused) so it can be resumed later.<br>
	 * <br>
	 * If the target track already exists in the suspended map it is unpaused<br>
	 * at its saved position instead of being loaded fresh from disk.<br>
	 * <br>
	 * Both directions of a music override use this method. For example, water<br>
	 * music: entering water suspends the surface track and starts (or resumes)<br>
	 * the water track; exiting water suspends the water track and resumes the<br>
	 * surface track.
	 * @param assetPath Path to the music file to play (or resume)
	 * @param duration Fade duration in seconds
	 */
	public void crossfadeWithSuspend(String assetPath, float duration)
	{
		// Skip if this track is already playing.
		if (assetPath.equals(_currentMusicPath) && _currentMusic != null)
		{
			return;
		}
		
		// Settle any in-progress crossfade before starting a new one.
		// This ensures the previous outgoing track is properly paused into the
		// suspended map rather than being abandoned (which would cause a position reset).
		stopActiveCrossfade();
		
		// Determine the incoming track: resume from map or load fresh.
		AudioNode incoming = _suspendedTracks.remove(assetPath);
		boolean resumed = false;
		
		if (incoming != null)
		{
			// Resume: unpause at volume 0, will be faded in by the crossfade.
			incoming.setVolume(0f);
			incoming.play();
			resumed = true;
		}
		else
		{
			// Load fresh.
			try
			{
				incoming = new AudioNode(_assetManager, assetPath, DataType.Stream);
				incoming.setLooping(true);
				incoming.setPositional(false);
				incoming.setVolume(0f);
				incoming.play();
			}
			catch (Exception e)
			{
				System.err.println("WARNING: Could not load music for suspend crossfade: " + assetPath + " - " + e.getMessage());
				return;
			}
		}
		
		// Capture the outgoing track's path before overwriting (needed when the
		// fade completes and the track is stored in the suspended map).
		_suspendingPath = _currentMusicPath;
		
		// Setup crossfade: old track fades to 0 then pauses, new track fades in.
		_fadingOutMusic = _currentMusic;
		_fadingInMusic = incoming;
		_fadeTimer = 0f;
		_fadeDuration = duration;
		_fadeOutStartVolume = (_currentMusic != null) ? _currentMusic.getVolume() : 0f;
		_pauseOnFadeOut = true; // Pause instead of stop when fade completes.
		
		_currentMusic = incoming;
		_currentMusicPath = assetPath;
		
		System.out.println("AudioManager: " + (resumed ? "Resuming" : "Starting") + " " + assetPath + ", suspending " + _suspendingPath + " (crossfade " + duration + "s)");
	}
	
	/**
	 * Clears a specific suspended track without resuming it.<br>
	 * Used when a day/night transition occurs while submerged, making the<br>
	 * suspended surface track irrelevant (the correct track has changed).
	 * @param assetPath the track to clear from the suspended map
	 */
	public void clearSuspended(String assetPath)
	{
		final AudioNode removed = _suspendedTracks.remove(assetPath);
		if (removed != null)
		{
			removed.stop();
			System.out.println("AudioManager: Cleared suspended track: " + assetPath);
		}
	}
	
	/**
	 * Clears all suspended tracks. Used by {@link #crossfadeTo} and other<br>
	 * methods that represent a fresh start where no track should be resumable.
	 */
	public void clearAllSuspended()
	{
		if (!_suspendedTracks.isEmpty())
		{
			for (AudioNode node : _suspendedTracks.values())
			{
				node.stop();
			}
			
			System.out.println("AudioManager: Cleared all suspended tracks (" + _suspendedTracks.size() + ")");
			_suspendedTracks.clear();
		}
	}
	
	/**
	 * Stop the currently playing music with a fade out.
	 */
	public void stopMusic()
	{
		if (_currentMusic != null)
		{
			// Settle any in-progress crossfade.
			stopActiveCrossfade();
			
			clearAllSuspended();
			
			// Quick fade out.
			_fadingOutMusic = _currentMusic;
			_fadingInMusic = null;
			_fadeTimer = 0f;
			_fadeDuration = 1.0f;
			_fadeOutStartVolume = _currentMusic.getVolume();
			_pauseOnFadeOut = false;
			
			_currentMusic = null;
			_currentMusicPath = null;
			System.out.println("AudioManager: Stopping music with fade out.");
		}
	}
	
	// ------------------------------------------------------------------
	// Sound effects.
	// ------------------------------------------------------------------
	
	/**
	 * Play a one-shot sound effect.
	 * @param assetPath Path to the sound file
	 */
	public void playSfx(String assetPath)
	{
		try
		{
			final AudioNode sfx = getOrCreateSfx(assetPath).clone();
			sfx.setVolume(_masterVolume * _sfxVolume);
			sfx.play();
		}
		catch (Exception e)
		{
			System.err.println("WARNING: Could not load SFX: " + assetPath + " - " + e.getMessage());
		}
	}
	
	/**
	 * Play a positional 3D sound effect.
	 * @param assetPath Path to the sound file
	 * @param position 3D position in world space
	 */
	public void playSfxAtPosition(String assetPath, Vector3f position)
	{
		try
		{
			final AudioNode sfx = getOrCreateSfx(assetPath).clone();
			sfx.setVolume(_masterVolume * _sfxVolume);
			sfx.setPositional(true);
			sfx.setLocalTranslation(position);
			sfx.play();
		}
		catch (Exception e)
		{
			System.err.println("WARNING: Could not load positional SFX: " + assetPath + " - " + e.getMessage());
		}
	}
	
	// ------------------------------------------------------------------
	// Volume controls.
	// ------------------------------------------------------------------
	
	/**
	 * Set master volume and update all playing audio.
	 * @param volume Volume level (0.0 - 1.0)
	 */
	public void setMasterVolume(float volume)
	{
		_masterVolume = Math.max(0f, Math.min(1f, volume));
		updateMusicVolume();
		System.out.println("Master volume set to: " + _masterVolume);
	}
	
	/**
	 * Set music volume and update playing music.
	 * @param volume Volume level (0.0 - 1.0)
	 */
	public void setMusicVolume(float volume)
	{
		_musicVolume = Math.max(0f, Math.min(1f, volume));
		updateMusicVolume();
		System.out.println("Music volume set to: " + _musicVolume);
	}
	
	/**
	 * Set SFX volume. Applies to future sound effect playback.
	 * @param volume Volume level (0.0 - 1.0)
	 */
	public void setSfxVolume(float volume)
	{
		_sfxVolume = Math.max(0f, Math.min(1f, volume));
		System.out.println("SFX volume set to: " + _sfxVolume);
	}
	
	// ------------------------------------------------------------------
	// Frame update.
	// ------------------------------------------------------------------
	
	/**
	 * Update audio manager state. Must be called every frame.<br>
	 * Handles crossfade interpolation and fade-ins.
	 * @param tpf Time per frame in seconds
	 */
	public void update(float tpf)
	{
		// Handle simple fade-in (not part of crossfade).
		if (_fadingIn && _currentMusic != null)
		{
			_fadeInTimer += tpf;
			final float progress = Math.min(1f, _fadeInTimer / _fadeInDuration);
			
			final float volume = _fadeInTargetVolume * progress;
			_currentMusic.setVolume(volume);
			
			if (progress >= 1f)
			{
				_fadingIn = false;
				System.out.println("AudioManager: Music fade-in complete.");
			}
		}
		
		// Handle crossfade.
		if (_fadingOutMusic != null || _fadingInMusic != null)
		{
			_fadeTimer += tpf;
			final float progress = Math.min(1f, _fadeTimer / _fadeDuration);
			
			// Fade out.
			if (_fadingOutMusic != null)
			{
				final float volume = _fadeOutStartVolume * (1f - progress);
				_fadingOutMusic.setVolume(volume);
				
				if (progress >= 1f)
				{
					if (_pauseOnFadeOut)
					{
						// Pause instead of stop - store in suspended map for later resume.
						_fadingOutMusic.pause();
						if (_suspendingPath != null)
						{
							_suspendedTracks.put(_suspendingPath, _fadingOutMusic);
							System.out.println("AudioManager: Track paused (suspended): " + _suspendingPath);
							_suspendingPath = null;
						}
						
						_pauseOnFadeOut = false;
					}
					else
					{
						_fadingOutMusic.stop();
					}
					
					_fadingOutMusic = null;
				}
			}
			
			// Fade in.
			if (_fadingInMusic != null)
			{
				final float targetVolume = _masterVolume * _musicVolume;
				final float volume = targetVolume * progress;
				_fadingInMusic.setVolume(volume);
				
				if (progress >= 1f)
				{
					_fadingInMusic = null;
				}
			}
		}
	}
	
	/**
	 * Update volume of currently playing music.
	 */
	private void updateMusicVolume()
	{
		// Update current music (unless it's in the middle of a fade-in).
		if (_currentMusic != null && !_fadingIn && _fadingInMusic != _currentMusic)
		{
			_currentMusic.setVolume(_masterVolume * _musicVolume);
		}
	}
	
	// --- Cache management ---
	
	/**
	 * Get a cached SFX template AudioNode or create and cache a new one.<br>
	 * The returned node is a template; callers should clone() it before playback.
	 * @param assetPath Path to the sound effect file
	 * @return Cached or newly created AudioNode template configured for SFX playback
	 */
	private AudioNode getOrCreateSfx(String assetPath)
	{
		return _sfxCache.computeIfAbsent(assetPath, path ->
		{
			final AudioNode sfx = new AudioNode(_assetManager, path, DataType.Buffer);
			sfx.setLooping(false);
			sfx.setPositional(false);
			System.out.println("AudioManager: Cached SFX: " + path);
			return sfx;
		});
	}
	
	/**
	 * Clear all cached SFX template nodes. Call when the application shuts down or audio is no longer needed.
	 */
	public void clearCache()
	{
		_sfxCache.clear();
		System.out.println("AudioManager: SFX cache cleared.");
	}
	
	// Getters.
	
	public float getMasterVolume()
	{
		return _masterVolume;
	}
	
	public float getMusicVolume()
	{
		return _musicVolume;
	}
	
	public float getSfxVolume()
	{
		return _sfxVolume;
	}
}
