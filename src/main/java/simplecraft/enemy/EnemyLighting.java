package simplecraft.enemy;

import java.nio.FloatBuffer;

import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.util.BufferUtils;

/**
 * Handles dynamic lighting for enemy models using vertex colors.<br>
 * Samples the world's sky light at each enemy's position and applies it<br>
 * as a uniform brightness to all vertex colors in the enemy's scene graph.<br>
 * Uses a dirty flag on {@link Enemy} so vertex buffers are only rebuilt when<br>
 * the light level changes, not every frame.
 * @author Pantelis Andrianakis
 * @since March 5th 2026
 */
public class EnemyLighting
{
	/** Minimum vertex brightness to prevent fully invisible enemies in total darkness. */
	private static final float MIN_BRIGHTNESS = 0.001f;
	
	// ------------------------------------------------------------------
	// Day/night cycle tint (global, updated each frame by PlayingState).
	// ------------------------------------------------------------------
	
	/** Red component of the current day/night tint. */
	private static float _tintR = 1.0f;
	
	/** Green component of the current day/night tint. */
	private static float _tintG = 1.0f;
	
	/** Blue component of the current day/night tint. */
	private static float _tintB = 1.0f;
	
	/**
	 * Private constructor — utility class with only static methods.
	 */
	private EnemyLighting()
	{
	}
	
	/**
	 * Sets the global day/night tint applied to all enemy vertex colors.<br>
	 * Called each frame by PlayingState with the tint from DayNightCycle.<br>
	 * This provides the cool blue tint at night and neutral white during the day.
	 * @param tint the sky tint color from DayNightCycle
	 */
	public static void setDayNightTint(ColorRGBA tint)
	{
		_tintR = tint.r;
		_tintG = tint.g;
		_tintB = tint.b;
	}
	
	/**
	 * Updates the vertex colors on an enemy's model to match the current sky light level.<br>
	 * Only rebuilds vertex buffers when the enemy's lighting dirty flag is set.<br>
	 * Called once per frame from {@link EnemyAnimator#update}.
	 * @param enemy the enemy whose lighting to update
	 */
	public static void updateLighting(Enemy enemy)
	{
		if (!enemy.isLightingDirty())
		{
			return;
		}
		
		applyLightToNode(enemy.getNode(), Math.max(enemy.getSkyLight(), MIN_BRIGHTNESS));
		enemy.setLightingDirty(false);
	}
	
	/**
	 * Initializes vertex color buffers on all geometries in the enemy's scene graph.<br>
	 * Uses the enemy's current sky light value (set by SpawnSystem before this call)<br>
	 * so enemies spawn at the correct brightness for the time of day.<br>
	 * Must be called after the model is assembled by {@link EnemyFactory} and before the first render frame.
	 * @param enemy the enemy whose model to initialize
	 */
	public static void initializeLighting(Enemy enemy)
	{
		applyLightToNode(enemy.getNode(), Math.max(enemy.getSkyLight(), MIN_BRIGHTNESS));
	}
	
	/**
	 * Recursively walks a scene graph node, applying uniform brightness<br>
	 * to all child Geometries and descending into child Nodes.
	 * @param node the node to walk
	 * @param light the brightness value to apply (0.0 – 1.0)
	 */
	private static void applyLightToNode(Node node, float light)
	{
		for (Spatial child : node.getChildren())
		{
			if (child instanceof Geometry)
			{
				applyLightToGeometry((Geometry) child, light);
			}
			else if (child instanceof Node)
			{
				applyLightToNode((Node) child, light);
			}
		}
	}
	
	/**
	 * Replaces the vertex color buffer on a single Geometry with brightness and day/night tint.<br>
	 * Creates a new RGBA float buffer with the given brightness multiplied by the global tint<br>
	 * for RGB and 1.0 for alpha. The tint provides a cool blue shift at night.
	 * @param geom the geometry to update
	 * @param light the brightness value to apply (0.0 – 1.0)
	 */
	private static void applyLightToGeometry(Geometry geom, float light)
	{
		final Mesh mesh = geom.getMesh();
		if (mesh == null)
		{
			return;
		}
		
		final int vertexCount = mesh.getVertexCount();
		if (vertexCount == 0)
		{
			return;
		}
		
		final FloatBuffer colorBuffer = BufferUtils.createFloatBuffer(vertexCount * 4);
		for (int i = 0; i < vertexCount; i++)
		{
			colorBuffer.put(light * _tintR); // R
			colorBuffer.put(light * _tintG); // G
			colorBuffer.put(light * _tintB); // B
			colorBuffer.put(1.0f); // A
		}
		colorBuffer.flip();
		
		mesh.setBuffer(Type.Color, 4, colorBuffer);
	}
}
