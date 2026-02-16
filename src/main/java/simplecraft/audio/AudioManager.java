package simplecraft.audio;

import java.util.ArrayList;
import java.util.List;

import com.jme3.asset.AssetManager;
import com.jme3.audio.AudioData;
import com.jme3.audio.AudioNode;
import com.jme3.audio.AudioSource;
import com.jme3.math.Vector3f;

/**
 * Manages all audio playback including music with crossfading and sound effects.<br>
 * Gracefully handles missing audio files without crashing.
 * @author Pantelis Andrianakis
 * @since February 16th 2026
 */
public class AudioManager
{
	private final AssetManager _assetManager;
	
	// Volume controls (0.0 - 1.0).
	private float _masterVolume = 0.7f;
	private float _musicVolume = 0.7f;
	private float _sfxVolume = 0.7f;
	
	// Current music track.
	private AudioNode _currentMusic;
	
	// Crossfade state.
	private AudioNode _fadingOutMusic;
	private AudioNode _fadingInMusic;
	private float _fadeTimer = 0f;
	private float _fadeDuration = 0f;
	private float _fadeOutStartVolume = 0f;
	
	// Fade-in state for simple fade-ins (not crossfades).
	private boolean _fadingIn = false;
	private float _fadeInTargetVolume = 0f;
	private float _fadeInDuration = 0f;
	private float _fadeInTimer = 0f;
	
	// Active SFX for cleanup.
	private final List<AudioNode> _activeSfx;
	
	public AudioManager(AssetManager assetManager)
	{
		_assetManager = assetManager;
		_activeSfx = new ArrayList<>();
		
		System.out.println("AudioManager initialized. Master: " + _masterVolume + ", Music: " + _musicVolume + ", SFX: " + _sfxVolume);
	}
	
	/**
	 * Play a music track on loop. Stops any currently playing music.
	 * @param assetPath Path to the .ogg file (e.g., "music/perspectives.ogg")
	 */
	public void playMusic(String assetPath)
	{
		try
		{
			// Stop current music if playing.
			if (_currentMusic != null)
			{
				_currentMusic.stop();
			}
			
			// Load and play new music.
			final AudioNode music = new AudioNode(_assetManager, assetPath, AudioData.DataType.Stream);
			music.setLooping(true);
			music.setPositional(false);
			music.setVolume(_masterVolume * _musicVolume);
			music.play();
			
			_currentMusic = music;
			System.out.println("Playing music: " + assetPath);
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
		try
		{
			// Stop current music if playing.
			if (_currentMusic != null)
			{
				_currentMusic.stop();
			}
			
			// Load new music and start at volume 0.
			final AudioNode music = new AudioNode(_assetManager, assetPath, AudioData.DataType.Stream);
			music.setLooping(true);
			music.setPositional(false);
			music.setVolume(0f);
			music.play();
			
			_currentMusic = music;
			
			// Setup fade-in state.
			_fadingIn = true;
			_fadeInTimer = 0f;
			_fadeInDuration = fadeDuration;
			_fadeInTargetVolume = _masterVolume * _musicVolume;
			
			System.out.println("Fading in music: " + assetPath + " over " + fadeDuration + "s");
		}
		catch (Exception e)
		{
			System.err.println("WARNING: Could not load music for fade-in: " + assetPath + " - " + e.getMessage());
		}
	}
	
	/**
	 * Crossfade from current music to a new track over a specified duration.
	 * @param assetPath Path to the new music file
	 * @param duration Fade duration in seconds
	 */
	public void crossfadeTo(String assetPath, float duration)
	{
		try
		{
			// Load new music.
			final AudioNode newMusic = new AudioNode(_assetManager, assetPath, AudioData.DataType.Stream);
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
			
			_currentMusic = newMusic;
			
			System.out.println("Crossfading to music: " + assetPath + " over " + duration + "s");
		}
		catch (Exception e)
		{
			System.err.println("WARNING: Could not load music for crossfade: " + assetPath + " - " + e.getMessage());
		}
	}
	
	/**
	 * Stop the currently playing music with a fade out.
	 */
	public void stopMusic()
	{
		if (_currentMusic != null)
		{
			// Quick fade out.
			_fadingOutMusic = _currentMusic;
			_fadingInMusic = null;
			_fadeTimer = 0f;
			_fadeDuration = 1.0f;
			_fadeOutStartVolume = _currentMusic.getVolume();
			
			_currentMusic = null;
			System.out.println("Stopping music with fade out.");
		}
	}
	
	/**
	 * Play a one-shot sound effect.
	 * @param assetPath Path to the sound file
	 */
	public void playSfx(String assetPath)
	{
		try
		{
			final AudioNode sfx = new AudioNode(_assetManager, assetPath, AudioData.DataType.Buffer);
			sfx.setLooping(false);
			sfx.setVolume(_masterVolume * _sfxVolume);
			sfx.play();
			
			_activeSfx.add(sfx);
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
			final AudioNode sfx = new AudioNode(_assetManager, assetPath, AudioData.DataType.Buffer);
			sfx.setLooping(false);
			sfx.setVolume(_masterVolume * _sfxVolume);
			sfx.setPositional(true);
			sfx.setLocalTranslation(position);
			sfx.play();
			
			_activeSfx.add(sfx);
		}
		catch (Exception e)
		{
			System.err.println("WARNING: Could not load positional SFX: " + assetPath + " - " + e.getMessage());
		}
	}
	
	/**
	 * Set master volume and update all playing audio.
	 * @param volume Volume level (0.0 - 1.0)
	 */
	public void setMasterVolume(float volume)
	{
		_masterVolume = Math.max(0f, Math.min(1f, volume));
		updateAllVolumes();
		System.out.println("Master volume set to: " + _masterVolume);
	}
	
	/**
	 * Set music volume and update playing music.
	 * @param volume Volume level (0.0 - 1.0)
	 */
	public void setMusicVolume(float volume)
	{
		_musicVolume = Math.max(0f, Math.min(1f, volume));
		updateAllVolumes();
		System.out.println("Music volume set to: " + _musicVolume);
	}
	
	/**
	 * Set SFX volume and update playing sound effects.
	 * @param volume Volume level (0.0 - 1.0)
	 */
	public void setSfxVolume(float volume)
	{
		_sfxVolume = Math.max(0f, Math.min(1f, volume));
		updateAllVolumes();
		System.out.println("SFX volume set to: " + _sfxVolume);
	}
	
	/**
	 * Update audio manager state. Must be called every frame.<br>
	 * Handles crossfade interpolation, fade-ins, and cleanup of finished SFX.
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
				System.out.println("Music fade-in complete.");
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
					_fadingOutMusic.stop();
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
		
		// Clean up finished SFX.
		for (int i = _activeSfx.size() - 1; i >= 0; i--)
		{
			if (_activeSfx.get(i).getStatus() != AudioSource.Status.Playing)
			{
				_activeSfx.remove(i);
			}
		}
	}
	
	/**
	 * Update volumes of all currently playing audio.
	 */
	private void updateAllVolumes()
	{
		// Update current music (unless it's in the middle of a fade-in).
		if (_currentMusic != null && !_fadingIn && _fadingInMusic != _currentMusic)
		{
			_currentMusic.setVolume(_masterVolume * _musicVolume);
		}
		
		// Update active SFX.
		for (AudioNode sfx : _activeSfx)
		{
			if (sfx.getStatus() == AudioSource.Status.Playing)
			{
				sfx.setVolume(_masterVolume * _sfxVolume);
			}
		}
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
