package simplecraft.settings;

import java.awt.DisplayMode;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeSet;

/**
 * Manages persistent game settings stored as key=value pairs in a plain text file.<br>
 * Settings are saved to {@code ~/.simplecraft/settings.txt}. Use %USERPROFILE% on Windows.<br>
 * Missing or corrupted files gracefully fall back to defaults without crashing.<br>
 * Available screen resolutions are auto-detected from the system's graphics device.
 * @author Pantelis Andrianakis
 * @since February 19th 2026
 */
public class SettingsManager
{
	// File paths.
	private static final String SETTINGS_DIR = System.getProperty("user.home") + File.separator + ".simplecraft";
	private static final String SETTINGS_FILE = SETTINGS_DIR + File.separator + "settings.txt";
	
	// Setting keys.
	private static final String KEY_MASTER_VOLUME = "masterVolume";
	private static final String KEY_MUSIC_VOLUME = "musicVolume";
	private static final String KEY_SFX_VOLUME = "sfxVolume";
	private static final String KEY_RENDER_DISTANCE = "renderDistance";
	private static final String KEY_SCREEN_WIDTH = "screenWidth";
	private static final String KEY_SCREEN_HEIGHT = "screenHeight";
	private static final String KEY_FULLSCREEN = "fullscreen";
	private static final String KEY_SHOW_STATS = "showStats";
	private static final String KEY_SHOW_FPS = "showFps";
	
	// Prefix for keybinding entries (e.g., "key.move_forward=17").
	private static final String KEYBINDING_PREFIX = "key.";
	
	// Prefix for mouse binding entries (e.g., "mouse.attack=0").
	private static final String MOUSEBINDING_PREFIX = "mouse.";
	
	// Default audio values.
	private static final float DEFAULT_MASTER_VOLUME = 0.7f;
	private static final float DEFAULT_MUSIC_VOLUME = 0.7f;
	private static final float DEFAULT_SFX_VOLUME = 0.7f;
	private static final int DEFAULT_RENDER_DISTANCE = 15;
	
	/** Player-facing render distance range (1–15), internally offset by +5 to become 6–20 regions. */
	public static final int MIN_RENDER_DISTANCE = 1;
	public static final int MAX_RENDER_DISTANCE = 15;
	private static final int RENDER_DISTANCE_OFFSET = 5;
	
	// Default display values (native resolution, fullscreen).
	private static final int DEFAULT_SCREEN_WIDTH;
	private static final int DEFAULT_SCREEN_HEIGHT;
	private static final boolean DEFAULT_FULLSCREEN = true;
	private static final boolean DEFAULT_SHOW_STATS = false;
	private static final boolean DEFAULT_SHOW_FPS = false;
	
	// Native monitor refresh rate (used as frame rate cap fallback).
	private static final int NATIVE_REFRESH_RATE;
	
	// Minimum resolution filter (exclusive - must be larger than 1024x768).
	private static final int MIN_RESOLUTION_WIDTH = 1025;
	private static final int MIN_RESOLUTION_HEIGHT = 769;
	
	// Maximum default resolution cap (4K is overkill for default).
	private static final int MAX_DEFAULT_WIDTH = 1920;
	private static final int MAX_DEFAULT_HEIGHT = 1080;
	
	// Auto-detected available resolutions (width x height), sorted ascending.
	private static final int[][] AVAILABLE_RESOLUTIONS;
	static
	{
		// Detect native resolution and available modes from the system.
		final GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
		final DisplayMode nativeMode = device.getDisplayMode();
		final int nativeWidth = nativeMode.getWidth();
		final int nativeHeight = nativeMode.getHeight();
		DEFAULT_SCREEN_WIDTH = Math.min(nativeWidth, MAX_DEFAULT_WIDTH);
		DEFAULT_SCREEN_HEIGHT = Math.min(nativeHeight, MAX_DEFAULT_HEIGHT);
		
		// Detect native refresh rate (fallback to 60 if unknown).
		final int detectedRate = nativeMode.getRefreshRate();
		NATIVE_REFRESH_RATE = (detectedRate == DisplayMode.REFRESH_RATE_UNKNOWN) ? 60 : detectedRate;
		
		// Collect unique resolutions from all display modes.
		final TreeSet<String> seen = new TreeSet<>();
		final List<int[]> resolutions = new ArrayList<>();
		
		for (DisplayMode mode : device.getDisplayModes())
		{
			final int w = mode.getWidth();
			final int h = mode.getHeight();
			
			// Filter out resolutions that are too small or exceed the monitor's native size.
			if (w < MIN_RESOLUTION_WIDTH || h < MIN_RESOLUTION_HEIGHT)
			{
				continue;
			}
			
			if (w > nativeWidth || h > nativeHeight)
			{
				continue;
			}
			
			final String key = w + "x" + h;
			if (seen.add(key))
			{
				resolutions.add(new int[]
				{
					w,
					h
				});
			}
		}
		
		// Sort by width, then height.
		resolutions.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
		
		AVAILABLE_RESOLUTIONS = resolutions.toArray(new int[0][]);
		
		System.out.println("SettingsManager: Detected " + AVAILABLE_RESOLUTIONS.length + " resolutions. Native: " + DEFAULT_SCREEN_WIDTH + "x" + DEFAULT_SCREEN_HEIGHT + " @ " + NATIVE_REFRESH_RATE + "Hz");
	}
	
	// Current values.
	private float _masterVolume = DEFAULT_MASTER_VOLUME;
	private float _musicVolume = DEFAULT_MUSIC_VOLUME;
	private float _sfxVolume = DEFAULT_SFX_VOLUME;
	private int _renderDistance = DEFAULT_RENDER_DISTANCE;
	private int _screenWidth = DEFAULT_SCREEN_WIDTH;
	private int _screenHeight = DEFAULT_SCREEN_HEIGHT;
	private boolean _fullscreen = DEFAULT_FULLSCREEN;
	private boolean _showStats = DEFAULT_SHOW_STATS;
	private boolean _showFps = DEFAULT_SHOW_FPS;
	
	// Custom keybinding overrides (action name -> key code). Empty means all defaults.
	private final Map<String, Integer> _keybindings = new LinkedHashMap<>();
	
	// Custom mouse button overrides (action name -> mouse button code). Empty means all defaults.
	private final Map<String, Integer> _mouseBindings = new LinkedHashMap<>();
	
	public SettingsManager()
	{
		System.out.println("SettingsManager initialized. File: " + SETTINGS_FILE);
	}
	
	/**
	 * Load settings from disk. Missing file or corrupted entries fall back to defaults.
	 */
	public void load()
	{
		final File file = new File(SETTINGS_FILE);
		if (!file.exists())
		{
			System.out.println("SettingsManager: No settings file found, using defaults.");
			return;
		}
		
		try (BufferedReader reader = new BufferedReader(new FileReader(file)))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				line = line.trim();
				
				// Skip empty lines and comments.
				if (line.isEmpty() || line.startsWith("#"))
				{
					continue;
				}
				
				final int separatorIndex = line.indexOf('=');
				if (separatorIndex < 0)
				{
					continue;
				}
				
				final String key = line.substring(0, separatorIndex).trim();
				final String value = line.substring(separatorIndex + 1).trim();
				
				try
				{
					switch (key)
					{
						case KEY_MASTER_VOLUME:
						{
							_masterVolume = Math.clamp(Float.parseFloat(value), 0f, 1f);
							break;
						}
						case KEY_MUSIC_VOLUME:
						{
							_musicVolume = Math.clamp(Float.parseFloat(value), 0f, 1f);
							break;
						}
						case KEY_SFX_VOLUME:
						{
							_sfxVolume = Math.clamp(Float.parseFloat(value), 0f, 1f);
							break;
						}
						case KEY_RENDER_DISTANCE:
						{
							_renderDistance = Math.clamp(Integer.parseInt(value), MIN_RENDER_DISTANCE, MAX_RENDER_DISTANCE);
							break;
						}
						case KEY_SCREEN_WIDTH:
						{
							_screenWidth = Integer.parseInt(value);
							break;
						}
						case KEY_SCREEN_HEIGHT:
						{
							_screenHeight = Integer.parseInt(value);
							break;
						}
						case KEY_FULLSCREEN:
						{
							_fullscreen = Boolean.parseBoolean(value);
							break;
						}
						case KEY_SHOW_STATS:
						{
							_showStats = Boolean.parseBoolean(value);
							break;
						}
						case KEY_SHOW_FPS:
						{
							_showFps = Boolean.parseBoolean(value);
							break;
						}
						default:
						{
							// Check for keybinding entries (key.actionName=keyCode).
							if (key.startsWith(KEYBINDING_PREFIX))
							{
								final String action = key.substring(KEYBINDING_PREFIX.length());
								_keybindings.put(action, Integer.parseInt(value));
							}
							// Check for mouse binding entries (mouse.actionName=buttonCode).
							else if (key.startsWith(MOUSEBINDING_PREFIX))
							{
								final String action = key.substring(MOUSEBINDING_PREFIX.length());
								_mouseBindings.put(action, Integer.parseInt(value));
							}
							else
							{
								System.out.println("SettingsManager: Unknown key ignored: " + key);
							}
							break;
						}
					}
				}
				catch (NumberFormatException e)
				{
					System.err.println("WARNING: SettingsManager: Invalid value for " + key + ": " + value + " - using default.");
				}
			}
			
			System.out.println("SettingsManager: Settings loaded. Master=" + _masterVolume + ", Music=" + _musicVolume + ", SFX=" + _sfxVolume + ", RenderDist=" + _renderDistance + ", Resolution=" + _screenWidth + "x" + _screenHeight + ", Fullscreen=" + _fullscreen + ", Stats=" + _showStats + ", FPS=" + _showFps);
		}
		catch (Exception e)
		{
			System.err.println("WARNING: SettingsManager: Could not read settings file - " + e.getMessage() + ". Using defaults.");
			resetToDefaults();
		}
	}
	
	/**
	 * Save current settings to disk. Creates the settings directory if it does not exist.
	 */
	public void save()
	{
		try
		{
			// Ensure directory exists.
			final File dir = new File(SETTINGS_DIR);
			if (!dir.exists())
			{
				dir.mkdirs();
			}
			
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(SETTINGS_FILE)))
			{
				writer.write("# SimpleCraft Settings");
				writer.newLine();
				writer.write(KEY_MASTER_VOLUME + "=" + _masterVolume);
				writer.newLine();
				writer.write(KEY_MUSIC_VOLUME + "=" + _musicVolume);
				writer.newLine();
				writer.write(KEY_SFX_VOLUME + "=" + _sfxVolume);
				writer.newLine();
				writer.write(KEY_RENDER_DISTANCE + "=" + _renderDistance);
				writer.newLine();
				writer.write(KEY_SCREEN_WIDTH + "=" + _screenWidth);
				writer.newLine();
				writer.write(KEY_SCREEN_HEIGHT + "=" + _screenHeight);
				writer.newLine();
				writer.write(KEY_FULLSCREEN + "=" + _fullscreen);
				writer.newLine();
				writer.write(KEY_SHOW_STATS + "=" + _showStats);
				writer.newLine();
				writer.write(KEY_SHOW_FPS + "=" + _showFps);
				writer.newLine();
				
				// Write keybinding overrides.
				for (Entry<String, Integer> entry : _keybindings.entrySet())
				{
					writer.write(KEYBINDING_PREFIX + entry.getKey() + "=" + entry.getValue());
					writer.newLine();
				}
				
				// Write mouse binding overrides.
				for (Entry<String, Integer> entry : _mouseBindings.entrySet())
				{
					writer.write(MOUSEBINDING_PREFIX + entry.getKey() + "=" + entry.getValue());
					writer.newLine();
				}
			}
			
			System.out.println("SettingsManager: Settings saved.");
		}
		catch (Exception e)
		{
			System.err.println("WARNING: SettingsManager: Could not save settings - " + e.getMessage());
		}
	}
	
	/**
	 * Reset all settings to their default values.
	 */
	public void resetToDefaults()
	{
		_masterVolume = DEFAULT_MASTER_VOLUME;
		_musicVolume = DEFAULT_MUSIC_VOLUME;
		_sfxVolume = DEFAULT_SFX_VOLUME;
		_renderDistance = DEFAULT_RENDER_DISTANCE;
		_screenWidth = DEFAULT_SCREEN_WIDTH;
		_screenHeight = DEFAULT_SCREEN_HEIGHT;
		_fullscreen = DEFAULT_FULLSCREEN;
		_showStats = DEFAULT_SHOW_STATS;
		_showFps = DEFAULT_SHOW_FPS;
		_keybindings.clear();
		_mouseBindings.clear();
	}
	
	/**
	 * Get the array of auto-detected available screen resolutions.
	 * @return Array of int[2] pairs (width, height), sorted ascending
	 */
	public static int[][] getAvailableResolutions()
	{
		return AVAILABLE_RESOLUTIONS;
	}
	
	/**
	 * Get the native monitor refresh rate in Hz.
	 * @return Refresh rate (e.g. 60, 144, 240), or 60 if unknown
	 */
	public static int getNativeRefreshRate()
	{
		return NATIVE_REFRESH_RATE;
	}
	
	/**
	 * Find the index of the current resolution in the available resolutions array.
	 * @return The matching index, or -1 if not found
	 */
	public int getResolutionIndex()
	{
		for (int i = 0; i < AVAILABLE_RESOLUTIONS.length; i++)
		{
			if (AVAILABLE_RESOLUTIONS[i][0] == _screenWidth && AVAILABLE_RESOLUTIONS[i][1] == _screenHeight)
			{
				return i;
			}
		}
		return -1;
	}
	
	/**
	 * Set the resolution from an available resolutions array index.
	 * @param index Index into AVAILABLE_RESOLUTIONS
	 */
	public void setResolutionByIndex(int index)
	{
		if (index >= 0 && index < AVAILABLE_RESOLUTIONS.length)
		{
			_screenWidth = AVAILABLE_RESOLUTIONS[index][0];
			_screenHeight = AVAILABLE_RESOLUTIONS[index][1];
		}
	}
	
	// --- Getters and Setters ---
	
	public float getMasterVolume()
	{
		return _masterVolume;
	}
	
	public void setMasterVolume(float volume)
	{
		_masterVolume = Math.clamp(volume, 0f, 1f);
	}
	
	public float getMusicVolume()
	{
		return _musicVolume;
	}
	
	public void setMusicVolume(float volume)
	{
		_musicVolume = Math.clamp(volume, 0f, 1f);
	}
	
	public float getSfxVolume()
	{
		return _sfxVolume;
	}
	
	public void setSfxVolume(float volume)
	{
		_sfxVolume = Math.clamp(volume, 0f, 1f);
	}
	
	public int getRenderDistance()
	{
		return _renderDistance + RENDER_DISTANCE_OFFSET;
	}
	
	/**
	 * Returns the player-facing render distance (1–15) for UI display.
	 */
	public int getDisplayRenderDistance()
	{
		return _renderDistance;
	}
	
	public void setRenderDistance(int distance)
	{
		_renderDistance = Math.clamp(distance, MIN_RENDER_DISTANCE, MAX_RENDER_DISTANCE);
	}
	
	public int getScreenWidth()
	{
		return _screenWidth;
	}
	
	public int getScreenHeight()
	{
		return _screenHeight;
	}
	
	public boolean isFullscreen()
	{
		return _fullscreen;
	}
	
	public void setFullscreen(boolean fullscreen)
	{
		_fullscreen = fullscreen;
	}
	
	public boolean isShowStats()
	{
		return _showStats;
	}
	
	public void setShowStats(boolean showStats)
	{
		_showStats = showStats;
	}
	
	public boolean isShowFps()
	{
		return _showFps;
	}
	
	public void setShowFps(boolean showFps)
	{
		_showFps = showFps;
	}
	
	// --- Keybinding Accessors ---
	
	/**
	 * Get all saved keybinding overrides.
	 * @return Unmodifiable map of action name to key code
	 */
	public Map<String, Integer> getKeybindings()
	{
		return Collections.unmodifiableMap(_keybindings);
	}
	
	/**
	 * Replace all keybinding overrides with the given map.
	 * @param bindings Map of action name to key code
	 */
	public void setKeybindings(Map<String, Integer> bindings)
	{
		_keybindings.clear();
		_keybindings.putAll(bindings);
	}
	
	/**
	 * Clear all keybinding overrides (revert to defaults on next load).
	 */
	public void clearKeybindings()
	{
		_keybindings.clear();
	}
	
	// --- Mouse Binding Accessors ---
	
	/**
	 * Get all saved mouse button binding overrides.
	 * @return Unmodifiable map of action name to mouse button code
	 */
	public Map<String, Integer> getMouseBindings()
	{
		return Collections.unmodifiableMap(_mouseBindings);
	}
	
	/**
	 * Replace all mouse button binding overrides with the given map.
	 * @param bindings Map of action name to mouse button code
	 */
	public void setMouseBindings(Map<String, Integer> bindings)
	{
		_mouseBindings.clear();
		_mouseBindings.putAll(bindings);
	}
	
	/**
	 * Clear all mouse button binding overrides (revert to defaults on next load).
	 */
	public void clearMouseBindings()
	{
		_mouseBindings.clear();
	}
}
