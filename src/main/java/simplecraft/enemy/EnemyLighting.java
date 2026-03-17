package simplecraft.enemy;

import java.nio.FloatBuffer;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.util.BufferUtils;

import simplecraft.world.World;

/**
 * Handles dynamic lighting for enemy models using vertex colors.<br>
 * Samples both sky light and block light (torches, campfires) at each enemy's<br>
 * position and applies a blended brightness with warm tint to all vertex colors.<br>
 * Uses a dirty flag on {@link Enemy} so vertex buffers are only rebuilt when<br>
 * the light level changes, not every frame.
 * @author Pantelis Andrianakis
 * @since March 5th 2026
 */
public class EnemyLighting
{
	/** Minimum vertex brightness to prevent fully invisible enemies in total darkness. */
	private static final float MIN_BRIGHTNESS = 0.001f;
	
	/** Red component of warm block light tint (matches RegionMeshBuilder). */
	private static final float WARM_TINT_R = 1.0f;
	
	/** Green component of warm block light tint. */
	private static final float WARM_TINT_G = 0.85f;
	
	/** Blue component of warm block light tint. */
	private static final float WARM_TINT_B = 0.55f;
	
	// ------------------------------------------------------------------
	// Day/night cycle tint (global, updated each frame by PlayingState).
	// ------------------------------------------------------------------
	
	/** Red component of the current day/night tint. */
	private static float _tintR = 1.0f;
	
	/** Green component of the current day/night tint. */
	private static float _tintG = 1.0f;
	
	/** Blue component of the current day/night tint. */
	private static float _tintB = 1.0f;
	
	/** World reference for sampling block light at enemy positions. */
	private static World _world;
	
	/**
	 * Private constructor - utility class with only static methods.
	 */
	private EnemyLighting()
	{
	}
	
	/**
	 * Sets the world reference used to sample block light at enemy positions.<br>
	 * Called once by PlayingState during initialization.
	 * @param world the active game world
	 */
	public static void setWorld(World world)
	{
		_world = world;
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
	 * Updates the vertex colors on an enemy's model to match the current light level.<br>
	 * Blends sky light and block light, applying warm tint for torch/campfire glow.<br>
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
		
		final float skyLight = Math.max(enemy.getSkyLight(), MIN_BRIGHTNESS);
		
		// Sample block light from the world at the enemy's foot position.
		float blockLight = 0;
		if (_world != null)
		{
			final Vector3f pos = enemy.getNode().getLocalTranslation();
			blockLight = _world.getBlockLight((int) pos.x, (int) pos.y, (int) pos.z) / 15.0f;
		}
		
		// Blend: take the brighter of sky light and block light.
		final float finalB = Math.max(Math.max(skyLight, blockLight), MIN_BRIGHTNESS);
		
		// Warm tint ratio: how much light comes from block light.
		final float blkRatio = (finalB > MIN_BRIGHTNESS) ? (blockLight / finalB) : 0;
		final float r = finalB * lerp(_tintR, WARM_TINT_R, blkRatio);
		final float g = finalB * lerp(_tintG, WARM_TINT_G, blkRatio);
		final float b = finalB * lerp(_tintB, WARM_TINT_B, blkRatio);
		
		applyColorToNode(enemy.getNode(), r, g, b);
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
		final float light = Math.max(enemy.getSkyLight(), MIN_BRIGHTNESS);
		applyColorToNode(enemy.getNode(), light * _tintR, light * _tintG, light * _tintB);
	}
	
	/**
	 * Recursively walks a scene graph node, applying uniform RGB color<br>
	 * to all child Geometries and descending into child Nodes.
	 * @param node the node to walk
	 * @param r red component (0.0 – 1.0)
	 * @param g green component (0.0 – 1.0)
	 * @param b blue component (0.0 – 1.0)
	 */
	private static void applyColorToNode(Node node, float r, float g, float b)
	{
		for (Spatial child : node.getChildren())
		{
			if (child instanceof Geometry)
			{
				applyColorToGeometry((Geometry) child, r, g, b);
			}
			else if (child instanceof Node)
			{
				applyColorToNode((Node) child, r, g, b);
			}
		}
	}
	
	/**
	 * Replaces the vertex color buffer on a single Geometry with the given RGB values.<br>
	 * Creates a new RGBA float buffer with the given color for all vertices.
	 * @param geom the geometry to update
	 * @param r red component (0.0 – 1.0)
	 * @param g green component (0.0 – 1.0)
	 * @param b blue component (0.0 – 1.0)
	 */
	private static void applyColorToGeometry(Geometry geom, float r, float g, float b)
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
			colorBuffer.put(r); // R
			colorBuffer.put(g); // G
			colorBuffer.put(b); // B
			colorBuffer.put(1.0f); // A
		}
		colorBuffer.flip();
		
		mesh.setBuffer(Type.Color, 4, colorBuffer);
	}
	
	/**
	 * Linear interpolation between two values.
	 */
	private static float lerp(float a, float b, float t)
	{
		return a + (b - a) * t;
	}
}
