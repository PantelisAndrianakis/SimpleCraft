package simplecraft.player;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import com.jme3.asset.AssetManager;
import com.jme3.asset.TextureKey;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;

import simplecraft.item.Inventory;
import simplecraft.item.ItemInstance;
import simplecraft.item.ItemTemplate;
import simplecraft.item.ItemType;
import simplecraft.world.Block;
import simplecraft.world.Block.Face;

/**
 * Renders the held item in 3D space near the player's right hand.<br>
 * <br>
 * <b>Block items</b> render as a small 3D cube ({@link Box}), textured on all faces.<br>
 * <b>Tools, weapons and fist</b> render as a flat sprite ({@link Quad}), same as how<br>
 * billboard decorations (flowers, torches) are displayed in the world.<br>
 * <br>
 * Both geometries are children of a parent {@link Node} on {@code rootNode}.<br>
 * Each frame, the node is positioned using camera direction/left/up vectors.<br>
 * Only one geometry is visible at a time - the other is culled via CullHint.
 * @author Pantelis Andrianakis
 * @since March 14th 2026
 */
public class ViewmodelRenderer
{
	// ========================================================
	// Constants.
	// ========================================================
	
	/** How far in front of the eye the held item sits. */
	private static final float FORWARD = 0.6f;
	
	/** How far to the right of center (sprites). */
	private static final float RIGHT = 0.14f;
	
	/** How far to the right of center (blocks - further right than sprites). */
	private static final float BLOCK_RIGHT = 0.27f;
	
	/** How far below eye level. */
	private static final float DOWN = 0.3f;
	
	/** Half-size of the held block cube (world units). */
	private static final float BLOCK_HALF_SIZE = 0.1f;
	
	/** Size of the held item sprite quad (world units). */
	private static final float SPRITE_SIZE = 0.35f;
	
	/** Duration of a full swing in seconds. */
	private static final float SWING_DURATION = 0.25f;
	
	/** Fraction of swing duration spent swinging forward. */
	private static final float SWING_FORWARD_FRACTION = 0.4f;
	
	/** Maximum swing angle in degrees. */
	private static final float SWING_MAX_ANGLE = 60f;
	
	/** Walk bob speed multiplier. */
	private static final float BOB_SPEED = 5f;
	
	/** Horizontal bob amplitude. */
	private static final float BOB_X = 0.015f;
	
	/** Vertical bob amplitude. */
	private static final float BOB_Y = 0.012f;
	
	/** Base rotation angles in degrees (item tilt in hand). */
	private static final float BASE_PITCH = -10f;
	private static final float BASE_YAW = -35f;
	private static final float BASE_ROLL = 5f;
	
	/** Filesystem path for item textures. */
	private static final String ITEM_TEX_DIR = "assets/images/items/";
	
	/** Filesystem path for block textures. */
	private static final String BLOCK_TEX_DIR = "assets/images/blocks/";
	
	/** ID when hand is empty. */
	private static final String FIST_ID = "fist";
	
	// ========================================================
	// Fields.
	// ========================================================
	
	private final Node _rootNode;
	private final AssetManager _assetManager;
	
	/** Parent node positioned at the hand each frame. */
	private final Node _handNode;
	
	/** 3D cube geometry for block items. */
	private final Geometry _blockGeo;
	
	/** Flat quad geometry for tools, weapons and fist. */
	private final Geometry _spriteGeo;
	
	private String _currentItemId = "";
	private final Map<String, Material> _materialCache = new HashMap<>();
	private final Quaternion _baseRot = new Quaternion();
	
	/** Gentler base rotation for block cubes. */
	private final Quaternion _baseBlockRot = new Quaternion();
	
	/** True when showing the block cube. */
	private boolean _showingBlock;
	
	// Swing state.
	private boolean _swinging;
	private float _swingTimer;
	
	// Bob state.
	private float _bobTimer;
	private boolean _isMoving;
	
	// Reusable (no per-frame allocation).
	private final Vector3f _pos = new Vector3f();
	private final Vector3f _dir = new Vector3f();
	private final Vector3f _left = new Vector3f();
	private final Vector3f _up = new Vector3f();
	private final Quaternion _rot = new Quaternion();
	private final Quaternion _worldRot = new Quaternion();
	
	// ========================================================
	// Constructor.
	// ========================================================
	
	public ViewmodelRenderer(AssetManager assetManager, Node rootNode)
	{
		_assetManager = assetManager;
		_rootNode = rootNode;
		
		_baseRot.fromAngles(FastMath.DEG_TO_RAD * BASE_PITCH, FastMath.DEG_TO_RAD * BASE_YAW, FastMath.DEG_TO_RAD * BASE_ROLL);
		_baseBlockRot.fromAngles(FastMath.DEG_TO_RAD * -15f, FastMath.DEG_TO_RAD * -10f, 0);
		
		// Parent node - positioned and rotated each frame.
		_handNode = new Node("ViewmodelHand");
		_handNode.setCullHint(Spatial.CullHint.Never);
		rootNode.attachChild(_handNode);
		
		// Block geometry - small cube, hidden initially.
		final Box box = new Box(BLOCK_HALF_SIZE, BLOCK_HALF_SIZE, BLOCK_HALF_SIZE);
		_blockGeo = new Geometry("ViewmodelBlock", box);
		_blockGeo.setCullHint(Spatial.CullHint.Always); // hidden
		_blockGeo.setQueueBucket(com.jme3.renderer.queue.RenderQueue.Bucket.Translucent);
		_handNode.attachChild(_blockGeo);
		
		// Sprite geometry - flat quad for tools/weapons/fist, visible initially.
		// Quad is created in XY plane. FaceCullMode.Off renders both sides.
		// We flip the UVs horizontally to correct mirroring (cheaper than rotating).
		final Quad quad = new Quad(SPRITE_SIZE, SPRITE_SIZE);
		// Flip UVs both horizontally and vertically.
		// The quad faces +Z but camera looks at -Z, so we see the back face.
		// FaceCullMode.Off renders it, but the image is mirrored on both axes.
		// Fix by flipping both U and V coordinates.
		// jME3 Quad vertex order: BL(0), BR(1), TR(2), TL(3).
		// @formatter:off
		final float[] uvs = new float[]
		{
			1, 1, // BL vertex
			0, 1, // BR vertex
			0, 0, // TR vertex
			1, 0 // TL vertex
		};
		// @formatter:on
		quad.setBuffer(com.jme3.scene.VertexBuffer.Type.TexCoord, 2, uvs);
		_spriteGeo = new Geometry("ViewmodelSprite", quad);
		// Offset so the node origin (rotation pivot) is at the lower-right corner (handle).
		// Quad extends left and up from the pivot point.
		_spriteGeo.setLocalTranslation(-SPRITE_SIZE, 0, 0);
		_spriteGeo.setCullHint(Spatial.CullHint.Never);
		_spriteGeo.setQueueBucket(com.jme3.renderer.queue.RenderQueue.Bucket.Translucent);
		_handNode.attachChild(_spriteGeo);
		
		// Apply fallback material to both so they never have null material.
		final Material fallback = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
		fallback.setColor("Color", ColorRGBA.Magenta);
		fallback.getAdditionalRenderState().setDepthTest(false);
		fallback.getAdditionalRenderState().setDepthWrite(false);
		_blockGeo.setMaterial(fallback);
		_spriteGeo.setMaterial(fallback);
		
		// Start with fist.
		updateSprite(FIST_ID);
		
		System.out.println("ViewmodelRenderer: Initialized.");
	}
	
	// ========================================================
	// Update.
	// ========================================================
	
	public void update(Camera camera, Inventory inventory, boolean isMoving, float tpf)
	{
		_isMoving = isMoving;
		
		// --- Item change detection ---
		final ItemInstance held = inventory.getSelectedItem();
		final String newId = (held != null) ? held.getTemplate().getId() : FIST_ID;
		if (!newId.equals(_currentItemId))
		{
			_currentItemId = newId;
			updateSprite(newId, held);
		}
		
		// --- Swing: local Z rotation on the geometry itself ---
		// The geometry pivot is at its lower-right corner (handle).
		// Rotating around local Z swings the top of the weapon left/right.
		// Negative angle = counterclockwise = swing left. That's what we want.
		float swingAngle = 0;
		if (_swinging)
		{
			_swingTimer += tpf;
			final float p = _swingTimer / SWING_DURATION;
			if (p >= 1.0f)
			{
				_swinging = false;
			}
			else
			{
				if (p < SWING_FORWARD_FRACTION)
				{
					swingAngle = (p / SWING_FORWARD_FRACTION) * SWING_MAX_ANGLE;
				}
				else
				{
					swingAngle = SWING_MAX_ANGLE * (1f - (p - SWING_FORWARD_FRACTION) / (1f - SWING_FORWARD_FRACTION));
				}
			}
		}
		
		// Apply swing as local Z rotation on whichever geometry is active.
		if (swingAngle != 0)
		{
			if (_showingBlock)
			{
				// Block swing: mostly pitch down (vertical chop), minimal left sweep.
				_rot.fromAngles(FastMath.DEG_TO_RAD * swingAngle * 0.8f, 0.2f, FastMath.DEG_TO_RAD * -swingAngle * 0.3f);
				_blockGeo.setLocalRotation(_rot);
			}
			else
			{
				// Sprite swing: sweep left around handle pivot.
				_rot.fromAngles(0, 0, FastMath.DEG_TO_RAD * -swingAngle);
				_spriteGeo.setLocalRotation(_rot);
			}
		}
		else
		{
			// Reset to identity when not swinging.
			if (_showingBlock)
			{
				_blockGeo.setLocalRotation(Quaternion.IDENTITY);
			}
			else
			{
				_spriteGeo.setLocalRotation(Quaternion.IDENTITY);
			}
		}
		
		// --- Bob ---
		float bobR = 0;
		float bobU = 0;
		if (_isMoving && !_swinging)
		{
			_bobTimer += tpf * BOB_SPEED;
			bobR = FastMath.sin(_bobTimer) * BOB_X;
			bobU = FastMath.sin(_bobTimer * 2f) * BOB_Y;
		}
		
		// --- Position: camera + offset ---
		camera.getDirection(_dir);
		camera.getLeft(_left);
		camera.getUp(_up);
		
		final float rightOffset = _showingBlock ? BLOCK_RIGHT : RIGHT;
		final float r = -(rightOffset + bobR);
		final float d = -(DOWN - bobU);
		
		_pos.set(camera.getLocation());
		_pos.x += _dir.x * FORWARD + _left.x * r + _up.x * d;
		_pos.y += _dir.y * FORWARD + _left.y * r + _up.y * d;
		_pos.z += _dir.z * FORWARD + _left.z * r + _up.z * d;
		
		_handNode.setLocalTranslation(_pos);
		
		// --- Rotation: camera * base tilt (no swing here - swing is on geometry) ---
		final Quaternion baseRot = _showingBlock ? _baseBlockRot : _baseRot;
		_worldRot.set(camera.getRotation());
		_worldRot.multLocal(baseRot);
		_handNode.setLocalRotation(_worldRot);
	}
	
	// ========================================================
	// Swing.
	// ========================================================
	
	public void triggerSwing()
	{
		if (!_swinging)
		{
			_swinging = true;
			_swingTimer = 0f;
		}
	}
	
	// ========================================================
	// Sprite Swap.
	// ========================================================
	
	private void updateSprite(String itemId)
	{
		updateSprite(itemId, null);
	}
	
	private void updateSprite(String itemId, ItemInstance item)
	{
		final boolean isBlock = (item != null && item.getTemplate().getType() == ItemType.BLOCK);
		
		// Show the right geometry, hide the other.
		if (isBlock)
		{
			_blockGeo.setCullHint(Spatial.CullHint.Never);
			_spriteGeo.setCullHint(Spatial.CullHint.Always);
			_showingBlock = true;
		}
		else
		{
			_blockGeo.setCullHint(Spatial.CullHint.Always);
			_spriteGeo.setCullHint(Spatial.CullHint.Never);
			_showingBlock = false;
		}
		
		// Check cache.
		final Material cached = _materialCache.get(itemId);
		if (cached != null)
		{
			if (isBlock)
			{
				_blockGeo.setMaterial(cached);
			}
			else
			{
				_spriteGeo.setMaterial(cached);
			}
			return;
		}
		
		// Load new material.
		Material mat = null;
		if (isBlock)
		{
			mat = loadBlockItemMaterial(item.getTemplate());
		}
		else
		{
			mat = loadItemMaterial(itemId);
		}
		
		if (mat != null)
		{
			_materialCache.put(itemId, mat);
			if (isBlock)
			{
				_blockGeo.setMaterial(mat);
			}
			else
			{
				_spriteGeo.setMaterial(mat);
			}
		}
	}
	
	private Material loadItemMaterial(String itemId)
	{
		return loadMaterialFromFile(ITEM_TEX_DIR, itemId + ".png", true);
	}
	
	private Material loadBlockItemMaterial(ItemTemplate template)
	{
		final Block block = template.getPlacesBlock();
		if (block == null)
		{
			return null;
		}
		final String texFile = block.getTextureFile(Face.NORTH);
		if (texFile == null || texFile.isEmpty())
		{
			return null;
		}
		return loadMaterialFromFile(BLOCK_TEX_DIR, texFile, false);
	}
	
	/**
	 * Loads a texture and creates an Unshaded material.
	 * @param directory filesystem directory
	 * @param filename texture filename
	 * @param isSprite true for flat sprites (alpha cutout + no face cull + depth off), false for block cubes (normal depth)
	 * @return the material, or null if loading fails
	 */
	private Material loadMaterialFromFile(String directory, String filename, boolean isSprite)
	{
		final File file = new File(directory + filename);
		if (!file.exists())
		{
			System.out.println("ViewmodelRenderer: Missing: " + file.getAbsolutePath());
			return null;
		}
		try
		{
			_assetManager.registerLocator(file.getParent(), FileLocator.class);
			final TextureKey key = new TextureKey(filename, false);
			key.setGenerateMips(false);
			final Texture2D tex = (Texture2D) _assetManager.loadTexture(key);
			tex.setMagFilter(Texture.MagFilter.Nearest);
			tex.setMinFilter(Texture.MinFilter.NearestNoMipMaps);
			
			final Material mat = new Material(_assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
			mat.setTexture("ColorMap", tex);
			
			// Always render on top of world geometry.
			mat.getAdditionalRenderState().setDepthTest(false);
			mat.getAdditionalRenderState().setDepthWrite(false);
			
			// Alpha cutout for all items - blocks like leaves/glass have transparent pixels too.
			mat.setFloat("AlphaDiscardThreshold", 0.5f);
			
			if (isSprite)
			{
				// Flat sprite: no face culling (visible from both sides).
				mat.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
			}
			// Block cube: default depth - renders like any world block.
			
			System.out.println("ViewmodelRenderer: Loaded '" + filename + "'" + (isSprite ? " (sprite)" : " (block)") + ".");
			return mat;
		}
		catch (Exception e)
		{
			System.out.println("ViewmodelRenderer: Failed '" + filename + "': " + e.getMessage());
			return null;
		}
	}
	
	// ========================================================
	// Cleanup.
	// ========================================================
	
	public void cleanup()
	{
		_rootNode.detachChild(_handNode);
		_materialCache.clear();
	}
}
