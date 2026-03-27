package simplecraft.settings;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

/**
 * Manages multilanguage support by discovering available language files and loading translations.<br>
 * Language files live at {@code assets/lang/{code}.txt} as key=value pairs.<br>
 * Corresponding flag images are expected at {@code assets/images/flags/{code}.png}.<br>
 * Only languages that have both a flag and a lang file are considered available.
 * @author Pantelis Andrianakis
 * @since March 27th 2026
 */
public class LanguageManager
{
	private static final String FLAGS_DIR = "assets/images/flags/";
	private static final String LANG_DIR = "assets/lang/";
	private static final String LANG_NAME_KEY = "lang.name";
	
	public static final String DEFAULT_LANGUAGE = "en";
	 
	// Currently active language code.
	private static String _currentCode = DEFAULT_LANGUAGE;
	
	// Loaded translations for the active language.
	private static final Map<String, String> _translations = new LinkedHashMap<>();
	
	/**
	 * Discover all languages that have both a flag PNG and a lang file.<br>
	 * Rescans the filesystem each call so newly added languages are picked up.
	 * @return Ordered map of language code to display name (e.g. "en" -> "English")
	 */
	public static Map<String, String> discoverLanguages()
	{
		final Map<String, String> languages = new LinkedHashMap<>();
		
		final File flagsDir = new File(FLAGS_DIR);
		if (!flagsDir.exists() || !flagsDir.isDirectory())
		{
			System.err.println("LanguageManager: Flags directory not found: " + flagsDir.getAbsolutePath());
			return languages;
		}
		
		final File[] files = flagsDir.listFiles();
		if (files == null)
		{
			return languages;
		}
		
		for (File f : files)
		{
			final String fileName = f.getName();
			if (!fileName.endsWith(".png"))
			{
				continue;
			}
			
			final String code = fileName.substring(0, fileName.length() - 4);
			final File langFile = new File(LANG_DIR + code + ".txt");
			if (langFile.exists())
			{
				languages.put(code, readLanguageName(langFile, code));
			}
		}
		
		System.out.println("LanguageManager: Discovered " + languages.size() + " language(s): " + languages.keySet());
		return languages;
	}
	
	/**
	 * Load translations for the given language code from {@code assets/lang/{code}.txt}.<br>
	 * Clears the previous translations before loading. Falls back gracefully on error.
	 * @param code Language code (e.g. "en", "de")
	 */
	public static void loadLanguage(String code)
	{
		_translations.clear();
		_currentCode = code;
		
		final File file = new File(LANG_DIR + code + ".txt");
		if (!file.exists())
		{
			System.err.println("LanguageManager: Language file not found: " + file.getAbsolutePath());
			return;
		}
		
		try (BufferedReader reader = new BufferedReader(new FileReader(file)))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#"))
				{
					continue;
				}
				
				final int sep = line.indexOf('=');
				if (sep < 0)
				{
					continue;
				}
				
				_translations.put(line.substring(0, sep).trim(), line.substring(sep + 1).replace("\\n", "\n"));
			}
			
			System.out.println("LanguageManager: Loaded " + _translations.size() + " entries for '" + code + "'.");
		}
		catch (Exception e)
		{
			System.err.println("LanguageManager: Failed to load '" + code + "': " + e.getMessage());
		}
	}
	
	/**
	 * Get a translated string by key.<br>
	 * Returns the key itself as a fallback if no translation is found.
	 * @param key Translation key (e.g. "menu.start")
	 * @return Translated string, or the key if not found
	 */
	public static String get(String key)
	{
		return _translations.getOrDefault(key, key);
	}
	
	/**
	 * Get the currently loaded language code.
	 * @return Current language code (e.g. "en")
	 */
	public static String getCurrentCode()
	{
		return _currentCode;
	}
	
	/**
	 * Collect all unique characters from the current language's translations that fall outside
	 * the standard ASCII range (32–126). Used by FontManager to extend the glyph atlas so
	 * non-Latin scripts (Cyrillic, Greek, CJK, Hangul, etc.) render correctly.
	 * @return Sorted array of unique non-ASCII characters used in the active language
	 */
	public static char[] getUniqueChars()
	{
		final TreeSet<Character> set = new TreeSet<>();

		for (String value : _translations.values())
		{
			for (int i = 0; i < value.length(); i++)
			{
				final char c = value.charAt(i);
				if (c < 32 || c > 126)
				{
					set.add(c);
				}
			}
		}

		final char[] result = new char[set.size()];
		int i = 0;
		for (char c : set)
		{
			result[i++] = c;
		}

		return result;
	}

	/**
	 * Get the asset path to the flag image for a given language code.
	 * @param code Language code (e.g. "en")
	 * @return Asset path to the flag PNG
	 */
	public static String getFlagPath(String code)
	{
		return FLAGS_DIR + code + ".png";
	}
	
	/**
	 * Read only the {@code lang.name} entry from a lang file without loading all translations.
	 * @param file Lang file to read
	 * @param fallback Value returned if the key is not found or file cannot be read
	 * @return The language display name
	 */
	private static String readLanguageName(File file, String fallback)
	{
		try (BufferedReader reader = new BufferedReader(new FileReader(file)))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				line = line.trim();
				if (line.startsWith(LANG_NAME_KEY + "="))
				{
					return line.substring(LANG_NAME_KEY.length() + 1);
				}
			}
		}
		catch (Exception e)
		{
			// Ignore.
		}
		
		return fallback;
	}
}
