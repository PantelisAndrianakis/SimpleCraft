package simplecraft.settings;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;

import com.jme3.system.AppSettings;

import simplecraft.SimpleCraft;

/**
 * Manages window display mode transitions using borderless windowed fullscreen.<br>
 * Avoids GLFW exclusive fullscreen entirely to prevent NVIDIA and gamma/HDR side effects.<br>
 * The borderless window is 1 pixel shorter than the monitor to prevent Windows<br>
 * from triggering "fullscreen optimizations" which alter the gamma/color pipeline.
 * @author Pantelis Andrianakis
 * @since February 20th 2026
 */
public class WindowDisplayManager
{
	/**
	 * Apply borderless windowed fullscreen.<br>
	 * Strips window decoration, sets always-on-top, and covers the monitor.<br>
	 * Enqueues the change on the render thread for safe execution.
	 */
	public static void applyBorderlessFullscreen()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		
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
			final int windowHeight = monitorHeight - 1;
			
			GLFW.glfwSetWindowAttrib(windowHandle, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
			GLFW.glfwSetWindowAttrib(windowHandle, GLFW.GLFW_FLOATING, GLFW.GLFW_TRUE);
			GLFW.glfwSetWindowPos(windowHandle, 0, 0);
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
	 * Restores window decoration, disables always-on-top, and centers the window.<br>
	 * Enqueues the change on the render thread for safe execution.
	 * @param width the desired window width
	 * @param height the desired window height
	 */
	public static void applyWindowed(int width, int height)
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		
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
			
			GLFW.glfwSetWindowAttrib(windowHandle, GLFW.GLFW_FLOATING, GLFW.GLFW_FALSE);
			GLFW.glfwSetWindowAttrib(windowHandle, GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);
			GLFW.glfwSetWindowSize(windowHandle, width, height);
			
			if (vidMode != null)
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
}
