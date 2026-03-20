package simplecraft.player;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import com.jme3.asset.AssetManager;
import com.jme3.asset.TextureKey;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.effect.ParticleEmitter;
import com.jme3.effect.ParticleMesh;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial.CullHint;
import com.jme3.scene.VertexBuffer.Type;
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
import simplecraft.world.Block.RenderMode;

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
	
	/** Filesystem path for dedicated drop/icon override textures (highest priority). */
	private static final String DROP_TEX_DIR = "assets/images/drops/";
	
	/** Filesystem path for item textures. */
	private static final String ITEM_TEX_DIR = "assets/images/items/";
	
	/** Filesystem path for block textures. */
	private static final String BLOCK_TEX_DIR = "assets/images/blocks/";
	
	/** ID when hand is empty. */
	private static final String FIST_ID = "fist";
	
	/** Flame effect image path (same as TorchTileEntity). */
	private static final String FLAME_IMAGE_PATH = "assets/images/effects/flame.png";
	
	/**
	 * Flame position relative to the hand node.<br>
	 * The sprite quad extends from (-SPRITE_SIZE, 0, 0) to (0, SPRITE_SIZE, 0)
	 */
	private static final float FLAME_X = -SPRITE_SIZE * 0.475f;
	private static final float FLAME_Y = SPRITE_SIZE * 0.55f;
	private static final float FLAME_Z = 0.15f;
	
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
	
	/** Flame particle emitter for held torch. */
	private ParticleEmitter _flameEmitter;
	
	/** True when the flame is currently active (holding a torch). */
	private boolean _flameActive;
	
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
		_handNode.setCullHint(CullHint.Never);
		rootNode.attachChild(_handNode);
		
		// Block geometry - small cube, hidden initially.
		final Box box = new Box(BLOCK_HALF_SIZE, BLOCK_HALF_SIZE, BLOCK_HALF_SIZE);
		_blockGeo = new Geometry("ViewmodelBlock", box);
		_blockGeo.setCullHint(CullHint.Always); // hidden
		_blockGeo.setQueueBucket(Bucket.Translucent);
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
		quad.setBuffer(Type.TexCoord, 2, uvs);
		_spriteGeo = new Geometry("ViewmodelSprite", quad);
		// Offset so the node origin (rotation pivot) is at the lower-right corner (handle).
		// Quad extends left and up from the pivot point.
		_spriteGeo.setLocalTranslation(-SPRITE_SIZE, 0, 0);
		_spriteGeo.setCullHint(CullHint.Never);
		_spriteGeo.setQueueBucket(Bucket.Translucent);
		_handNode.attachChild(_spriteGeo);
		
		// Apply fallback material to both so they never have null material.
		final Material fallback = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
		fallback.setColor("Color", ColorRGBA.Magenta);
		fallback.getAdditionalRenderState().setDepthTest(false);
		fallback.getAdditionalRenderState().setDepthWrite(false);
		_blockGeo.setMaterial(fallback);
		_spriteGeo.setMaterial(fallback);
		
		// Flame particle emitter for held torch - hidden until a torch is equipped.
		createFlameEmitter();
		_flameEmitter.setCullHint(CullHint.Always);
		_handNode.attachChild(_flameEmitter);
		
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
		
		// --- Flame: hide during swing so the effect doesn't look odd mid-swing ---
		if (_flameActive)
		{
			if (_swinging)
			{
				_flameEmitter.setCullHint(CullHint.Always);
			}
			else
			{
				_flameEmitter.setCullHint(CullHint.Never);
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
		
		// CROSS_BILLBOARD and FLAT_PANEL blocks (torch, flowers, campfire, etc.) render as flat sprites, not 3D cubes.
		// They should look the same as when placed in-world.
		final boolean isBillboardBlock;
		if (isBlock)
		{
			final Block block = item.getTemplate().getPlacesBlock();
			isBillboardBlock = (block != null && (block.getRenderMode() == RenderMode.CROSS_BILLBOARD || block.getRenderMode() == RenderMode.FLAT_PANEL));
		}
		else
		{
			isBillboardBlock = false;
		}
		
		// Show cube only for non-billboard blocks. Billboard blocks use the sprite quad.
		final boolean showCube = isBlock && !isBillboardBlock;
		
		// Detect held torch for flame particle effect.
		final boolean isTorch;
		if (isBillboardBlock)
		{
			final Block block = item.getTemplate().getPlacesBlock();
			isTorch = (block == Block.TORCH);
		}
		else
		{
			isTorch = false;
		}
		
		// Show the right geometry, hide the other.
		if (showCube)
		{
			_blockGeo.setCullHint(CullHint.Never);
			_spriteGeo.setCullHint(CullHint.Always);
			_showingBlock = true;
		}
		else
		{
			_blockGeo.setCullHint(CullHint.Always);
			_spriteGeo.setCullHint(CullHint.Never);
			_showingBlock = false;
		}
		
		// Toggle flame particles - show only when holding a torch.
		if (isTorch && !_flameActive)
		{
			_flameEmitter.setCullHint(CullHint.Never);
			_flameEmitter.setParticlesPerSec(6);
			_flameActive = true;
		}
		else if (!isTorch && _flameActive)
		{
			_flameEmitter.setParticlesPerSec(0);
			_flameEmitter.killAllParticles();
			_flameEmitter.setCullHint(CullHint.Always);
			_flameActive = false;
		}
		
		// Check cache.
		final Material cached = _materialCache.get(itemId);
		if (cached != null)
		{
			if (showCube)
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
		// Priority 1: Check drops directory for dedicated icon override (matches ItemTextureResolver).
		// Only applies to sprite-rendered items (not 3D block cubes, which need tiling textures).
		Material mat = null;
		if (!showCube)
		{
			mat = loadMaterialFromFile(DROP_TEX_DIR, itemId + ".png", true);
		}
		
		// Priority 2+: Type-specific fallback.
		if (mat == null)
		{
			if (showCube)
			{
				mat = loadBlockItemMaterial(item.getTemplate());
			}
			else if (isBillboardBlock)
			{
				// Billboard block held as sprite - load from block textures directory.
				mat = loadBillboardBlockMaterial(item.getTemplate());
			}
			else
			{
				mat = loadItemMaterial(itemId);
			}
		}
		
		if (mat != null)
		{
			_materialCache.put(itemId, mat);
			if (showCube)
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
	 * Loads a CROSS_BILLBOARD block's texture as a flat sprite material.<br>
	 * Uses the side texture (the only texture billboard blocks define).
	 */
	private Material loadBillboardBlockMaterial(ItemTemplate template)
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
		
		return loadMaterialFromFile(BLOCK_TEX_DIR, texFile, true);
	}
	
	/**
	 * Loads a texture and creates an Unshaded material.<br>
	 * <br>
	 * <b>Important:</b> The TextureKey uses the full relative path ({@code directory + filename})<br>
	 * rather than just the filename. This prevents jME3's AssetManager from returning a<br>
	 * same-named file from a different previously-registered locator directory (since<br>
	 * {@code registerLocator} calls accumulate globally).
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
			// Register project root so full-path TextureKeys resolve unambiguously.
			_assetManager.registerLocator("", FileLocator.class);
			final String fullPath = directory + filename;
			final TextureKey key = new TextureKey(fullPath, false);
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
			
			System.out.println("ViewmodelRenderer: Loaded '" + fullPath + "'" + (isSprite ? " (sprite)" : " (block)") + ".");
			return mat;
		}
		catch (Exception e)
		{
			System.out.println("ViewmodelRenderer: Failed '" + filename + "': " + e.getMessage());
			return null;
		}
	}
	
	// ========================================================
	// Flame Particles.
	// ========================================================
	
	/**
	 * Creates a small flame particle emitter for the held torch tip.<br>
	 * Based on {@code TorchTileEntity}'s flame but scaled down for the viewmodel.<br>
	 * The emitter is a child of {@code _handNode} so it follows the hand position<br>
	 * and rotation naturally. Particles emit upward from the torch tip.<br>
	 * Depth test/write are disabled so the flame renders on top of world geometry,<br>
	 * matching the viewmodel rendering approach.
	 */
	private void createFlameEmitter()
	{
		_flameEmitter = new ParticleEmitter("ViewmodelFlame", ParticleMesh.Type.Triangle, 8);
		
		final Material mat = new Material(_assetManager, "Common/MatDefs/Misc/Particle.j3md");
		mat.setTexture("Texture", _assetManager.loadTexture(FLAME_IMAGE_PATH));
		mat.getAdditionalRenderState().setBlendMode(BlendMode.Additive);
		mat.getAdditionalRenderState().setDepthTest(false);
		mat.getAdditionalRenderState().setDepthWrite(false);
		_flameEmitter.setMaterial(mat);
		_flameEmitter.setQueueBucket(Bucket.Transparent);
		
		_flameEmitter.setImagesX(2);
		_flameEmitter.setImagesY(2);
		_flameEmitter.setSelectRandomImage(true);
		
		// Smaller than world torch - this is right in front of the camera.
		_flameEmitter.setStartSize(0.04f);
		_flameEmitter.setEndSize(0.01f);
		_flameEmitter.setStartColor(new ColorRGBA(1.0f, 0.8f, 0.2f, 0.8f));
		_flameEmitter.setEndColor(new ColorRGBA(1.0f, 0.3f, 0.0f, 0.0f));
		
		_flameEmitter.setLowLife(0.15f);
		_flameEmitter.setHighLife(0.3f);
		
		_flameEmitter.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 0.3f, 0));
		_flameEmitter.getParticleInfluencer().setVelocityVariation(0.2f);
		_flameEmitter.setGravity(0, -0.15f, 0);
		
		// Position at the torch tip.
		_flameEmitter.setLocalTranslation(FLAME_X, FLAME_Y, FLAME_Z);
		_flameEmitter.setParticlesPerSec(0); // Off until torch is equipped.
	}
	
	// ========================================================
	// Cleanup.
	// ========================================================
	
	public void cleanup()
	{
		if (_flameEmitter != null)
		{
			_flameEmitter.killAllParticles();
		}
		_rootNode.detachChild(_handNode);
		_materialCache.clear();
	}
}
