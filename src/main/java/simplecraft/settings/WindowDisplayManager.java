package simplecraft.settings;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;

import com.jme3.system.AppSettings;

import simplecraft.SimpleCraft;

/**
 * Manages window display mode transitions using borderless windowed fullscreen.<br>
 * Avoids GLFW exclusive fullscreen entirely to prevent NVIDIA and gamma/HDR side effects.<br>
 * The borderless window is 1 pixel larger than the monitor to prevent Windows<br>
 * from triggering "fullscreen optimizations" which alter the gamma/color pipeline.
 * @author Pantelis Andrianakis
 * @since February 20th 2026
 */
public class WindowDisplayManager
{
	private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");
	private static final boolean IS_WAYLAND = isWaylandSession();
	private static volatile boolean waylandNoticeLogged;
	
	private static boolean isWaylandSession()
	{
		final String sessionType = System.getenv("XDG_SESSION_TYPE");
		final String waylandDisplay = System.getenv("WAYLAND_DISPLAY");
		return "wayland".equalsIgnoreCase(sessionType) || (waylandDisplay != null && !waylandDisplay.isBlank());
	}
	
	/**
	 * Apply borderless windowed fullscreen.<br>
	 * Strips window decoration, sets always-on-top and covers the monitor.<br>
	 * Enqueues the change on the render thread for safe execution.
	 */
	public static void applyBorderlessFullscreen()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		logWaylandNoticeIfNeeded();
		
		app.enqueue(() ->
		{
			final long windowHandle = GLFW.glfwGetCurrentContext();
			if (windowHandle == 0)
			{
				System.err.println("WARNING: WindowDisplayManager: Could not get GLFW window handle.");
				return null;
			}
			
			final long monitor = GLFW.glfwGetPrimaryMonitor();
			final GLFWVidMode vidMode = GLFW.glfwGetVideoMode(monitor);
			final int monitorWidth = (vidMode != null) ? vidMode.width() : app.getSettingsManager().getScreenWidth();
			final int monitorHeight = (vidMode != null) ? vidMode.height() : app.getSettingsManager().getScreenHeight();
			final int windowHeight = IS_WINDOWS ? monitorHeight + 1 : monitorHeight;
			
			GLFW.glfwSetWindowAttrib(windowHandle, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
			if (!IS_WAYLAND)
			{
				GLFW.glfwSetWindowAttrib(windowHandle, GLFW.GLFW_FLOATING, GLFW.GLFW_TRUE);
				GLFW.glfwSetWindowPos(windowHandle, 0, 0);
			}
			
			GLFW.glfwSetWindowSize(windowHandle, monitorWidth, windowHeight);
			
			final AppSettings appSettings = app.getContext().getSettings();
			appSettings.setWidth(monitorWidth);
			appSettings.setHeight(windowHeight);
			appSettings.setFullscreen(false);
			
			app.reshape(monitorWidth, windowHeight);
			
			System.out.println("WindowDisplayManager: Applied borderless fullscreen " + monitorWidth + "x" + windowHeight);
			return null;
		});
	}
	
	/**
	 * Apply windowed mode at the specified resolution.<br>
	 * Restores window decoration, disables always-on-top and centers the window.<br>
	 * Enqueues the change on the render thread for safe execution.
	 * @param width the desired window width
	 * @param height the desired window height
	 */
	public static void applyWindowed(int width, int height)
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		logWaylandNoticeIfNeeded();
		
		app.enqueue(() ->
		{
			final long windowHandle = GLFW.glfwGetCurrentContext();
			if (windowHandle == 0)
			{
				System.err.println("WARNING: WindowDisplayManager: Could not get GLFW window handle.");
				return null;
			}
			
			final long monitor = GLFW.glfwGetPrimaryMonitor();
			final GLFWVidMode vidMode = GLFW.glfwGetVideoMode(monitor);
			
			if (!IS_WAYLAND)
			{
				GLFW.glfwSetWindowAttrib(windowHandle, GLFW.GLFW_FLOATING, GLFW.GLFW_FALSE);
			}
			
			GLFW.glfwSetWindowAttrib(windowHandle, GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);
			GLFW.glfwSetWindowSize(windowHandle, width, height);
			
			if (vidMode != null && !IS_WAYLAND)
			{
				final int posX = (vidMode.width() - width) / 2;
				final int posY = (vidMode.height() - height) / 2;
				GLFW.glfwSetWindowPos(windowHandle, posX, posY);
			}
			
			final AppSettings appSettings = app.getContext().getSettings();
			appSettings.setWidth(width);
			appSettings.setHeight(height);
			appSettings.setFullscreen(false);
			
			app.reshape(width, height);
			
			System.out.println("WindowDisplayManager: Applied windowed " + width + "x" + height);
			return null;
		});
	}
	
	/**
	 * Apply the current display settings from SettingsManager.<br>
	 * Routes to borderless fullscreen or windowed mode based on the saved preference.
	 */
	public static void applyCurrentSettings()
	{
		final SettingsManager settingsManager = SimpleCraft.getInstance().getSettingsManager();
		
		if (settingsManager.isFullscreen())
		{
			applyBorderlessFullscreen();
		}
		else
		{
			applyWindowed(settingsManager.getScreenWidth(), settingsManager.getScreenHeight());
		}
	}
	
	private static void logWaylandNoticeIfNeeded()
	{
		if (IS_WAYLAND && !waylandNoticeLogged)
		{
			waylandNoticeLogged = true;
			System.out.println("WindowDisplayManager: Wayland detected; floating/position hints are disabled by platform design.");
		}
	}
}
