package simplecraft.util;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import com.jme3.system.AppSettings;

/**
 * Utility class for loading and applying window icons.
 * @author Pantelis Andrianakis
 * @since February 18th 2026
 */
public class WindowIconLoader
{
	private static final String[] ICON_PATHS =
	{
		"assets/images/app_icons/icon_16.png",
		"assets/images/app_icons/icon_32.png",
		"assets/images/app_icons/icon_128.png"
	};
	
	/**
	 * Loads icons from the assets/images/app_icons/ directory and applies them to the given {@link AppSettings}.<br>
	 * Multiple sizes are provided so the OS can pick the best fit<br>
	 * (16x16 for title bar, 32x32 for taskbar, 128x128 for alt-tab / dock).
	 * @param settings the {@link AppSettings} to apply icons to
	 */
	public static void applyIcons(AppSettings settings)
	{
		final List<BufferedImage> loadedIcons = new ArrayList<>();
		for (String path : ICON_PATHS)
		{
			final File iconFile = new File(path);
			if (!iconFile.exists())
			{
				System.err.println("Icon file not found: " + iconFile.getAbsolutePath());
				continue;
			}
			
			try
			{
				final BufferedImage image = ImageIO.read(iconFile);
				if (image != null)
				{
					loadedIcons.add(image);
					System.out.println("Loaded icon: " + path + " (" + image.getWidth() + "x" + image.getHeight() + ")");
				}
			}
			catch (IOException e)
			{
				System.err.println("Failed to load icon: " + path + " - " + e.getMessage());
			}
		}
		
		if (!loadedIcons.isEmpty())
		{
			settings.setIcons(loadedIcons.toArray(new BufferedImage[0]));
			System.out.println("Window icons set successfully (" + loadedIcons.size() + " sizes).");
		}
		else
		{
			System.err.println("No window icons found. Using default.");
		}
	}
}
