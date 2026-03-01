package simplecraft.player;

import com.jme3.asset.AssetManager;
import com.jme3.input.InputManager;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.scene.debug.WireBox;
import com.jme3.util.BufferUtils;

import simplecraft.world.Block;
import simplecraft.world.Block.Face;
import simplecraft.world.World;

/**
 * Handles block interaction: raycasting, multi-hit breaking, placement, and selection.<br>
 * <br>
 * Each frame, steps along the camera ray in 0.05-block increments up to 5 blocks.<br>
 * The first non-AIR, non-liquid block hit becomes the target. The position one step<br>
 * before the hit is the placement position. Both are tracked for breaking and placing.<br>
 * <br>
 * <b>Breaking</b> uses a multi-hit system: each block requires {@code getHardness()} hits<br>
 * to destroy, delivered at 4 hits per second when holding left-click. Switching targets<br>
 * resets progress. A crack overlay darkens the targeted face proportional to progress.<br>
 * <br>
 * <b>Placing</b> puts the selected block at the placement position if it does not overlap<br>
 * the player's AABB. Tile entity blocks trigger interaction instead of placement.<br>
 * Special rules apply for torches (must attach to solid surface) and flowers/grass<br>
 * (must be placed on GRASS or DIRT).<br>
 * <br>
 * <b>Selection</b> cycles through placeable blocks via mouse wheel.
 * @author Pantelis Andrianakis
 * @since March 1st 2026
 */
public class BlockInteraction implements ActionListener, AnalogListener
{
	// ========================================================
	// Constants.
	// ========================================================
	
	/** Maximum raycast distance in blocks. */
	private static final float RAY_DISTANCE = 5.0f;
	
	/** Step increment along the ray (smaller = more precise but more expensive). */
	private static final float RAY_STEP = 0.05f;
	
	/** Seconds between hits when holding attack (4 hits per second). */
	private static final float HIT_COOLDOWN = 0.25f;
	
	/** Wireframe expansion beyond block edges to prevent z-fighting. */
	private static final float WIREFRAME_EXPAND = 0.002f;
	
	/** Crack overlay offset from block face to prevent z-fighting. */
	private static final float CRACK_OFFSET = 0.001f;
	
	/** Maximum crack overlay alpha (at 100% break progress). */
	private static final float CRACK_MAX_ALPHA = 0.5f;
	
	/** Camera shake magnitude in blocks. */
	private static final float SHAKE_MAGNITUDE = 0.02f;
	
	/** Camera shake duration in seconds. */
	private static final float SHAKE_DURATION = 0.05f;
	
	/** Player AABB half-width (X and Z). */
	private static final float PLAYER_HALF_WIDTH = 0.3f;
	
	/** Player AABB height from feet. */
	private static final float PLAYER_HEIGHT = 1.8f;
	
	// Input action names.
	private static final String ACTION_ATTACK = "ATTACK";
	private static final String ACTION_PLACE = "PLACE_BLOCK";
	private static final String ACTION_NEXT_BLOCK = "NEXT_BLOCK";
	private static final String ACTION_PREV_BLOCK = "PREV_BLOCK";
	
	/** Ordered list of blocks the player can place. */
	private static final Block[] PLACEABLE_BLOCKS =
	{
		Block.GRASS,
		Block.DIRT,
		Block.STONE,
		Block.SAND,
		Block.WOOD,
		Block.LEAVES,
		Block.CAMPFIRE,
		Block.CHEST,
		Block.CRAFTING_TABLE,
		Block.FURNACE,
		Block.TORCH,
		Block.TALL_GRASS,
		Block.RED_POPPY,
		Block.DANDELION,
		Block.BLUE_ORCHID,
		Block.WHITE_DAISY,
		Block.TALL_SEAWEED,
		Block.SHORT_SEAWEED
	};
	
	/** Flower and decoration blocks that can only be placed on GRASS or DIRT. */
	private static final Block[] SOIL_ONLY_BLOCKS =
	{
		Block.TALL_GRASS,
		Block.RED_POPPY,
		Block.DANDELION,
		Block.BLUE_ORCHID,
		Block.WHITE_DAISY
	};
	
	// ========================================================
	// Fields.
	// ========================================================
	
	private final Camera _camera;
	private final InputManager _inputManager;
	private final World _world;
	private final PlayerController _playerController;
	
	/** Scene node for highlight and crack overlay geometries. */
	private final Node _overlayNode = new Node("BlockInteractionOverlay");
	
	// Materials.
	private final Material _wireframeMaterial;
	private final Material _wireframeUnbreakableMaterial;
	private final Material _crackMaterial;
	
	// Wireframe highlight.
	private Geometry _wireframeGeometry;
	private boolean _wireframeVisible;
	
	// Crack overlay.
	private Geometry _crackGeometry;
	private boolean _crackVisible;
	
	// Raycast results (updated each frame).
	/** World coordinates of the targeted block, or null if no target. */
	private int _targetX;
	private int _targetY;
	private int _targetZ;
	private boolean _hasTarget;
	
	/** The face of the targeted block that the ray hit. */
	private Face _targetFace;
	
	/** World coordinates of the placement position (one step before hit). */
	private int _placeX;
	private int _placeY;
	private int _placeZ;
	private boolean _hasPlacePos;
	
	// Breaking state.
	/** The block position currently being broken (for reset detection). */
	private int _breakingX;
	private int _breakingY;
	private int _breakingZ;
	private boolean _isBreaking;
	
	/** Number of hits delivered to the current target. */
	private int _hitsDelivered;
	
	/** Required hits for the current target block. */
	private int _hitsRequired;
	
	/** Time remaining before the next hit can be delivered. */
	private float _hitCooldownTimer;
	
	// Input flags.
	private boolean _attackHeld;
	private boolean _placePressed;
	
	// Block selection.
	private int _selectedBlockIndex;
	
	// Camera shake.
	private float _shakeTimer;
	private final Vector3f _shakeOffset = new Vector3f();
	
	// Reusable face offset (avoids allocation in raycast loop).
	private int _faceOffsetX;
	private int _faceOffsetY;
	private int _faceOffsetZ;
	
	// Reusable vectors to avoid per-frame allocation in performRaycast.
	private final Vector3f _rayOrigin = new Vector3f();
	private final Vector3f _rayDirection = new Vector3f();
	
	// ========================================================
	// Constructor.
	// ========================================================
	
	/**
	 * Creates a new block interaction handler.
	 * @param camera the jME3 camera for raycast direction
	 * @param inputManager the jME3 input manager for mouse input
	 * @param world the game world for block queries and modifications
	 * @param playerController the player controller for position and selected block
	 * @param assetManager the jME3 asset manager for material creation
	 */
	public BlockInteraction(Camera camera, InputManager inputManager, World world, PlayerController playerController, AssetManager assetManager)
	{
		_camera = camera;
		_inputManager = inputManager;
		_world = world;
		_playerController = playerController;
		
		// Create wireframe material (bright white, unshaded).
		_wireframeMaterial = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
		_wireframeMaterial.setColor("Color", new ColorRGBA(1.0f, 1.0f, 1.0f, 0.9f));
		_wireframeMaterial.getAdditionalRenderState().setWireframe(true);
		_wireframeMaterial.getAdditionalRenderState().setLineWidth(2.0f);
		_wireframeMaterial.getAdditionalRenderState().setDepthTest(false);
		
		// Create wireframe material for unbreakable blocks (vivid red).
		_wireframeUnbreakableMaterial = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
		_wireframeUnbreakableMaterial.setColor("Color", new ColorRGBA(1.0f, 0.2f, 0.2f, 0.9f));
		_wireframeUnbreakableMaterial.getAdditionalRenderState().setWireframe(true);
		_wireframeUnbreakableMaterial.getAdditionalRenderState().setLineWidth(2.0f);
		_wireframeUnbreakableMaterial.getAdditionalRenderState().setDepthTest(false);
		
		// Create crack overlay material (semi-transparent black).
		_crackMaterial = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
		_crackMaterial.setColor("Color", new ColorRGBA(0, 0, 0, 0));
		_crackMaterial.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
		
		// Build wireframe geometry.
		final WireBox wireBox = new WireBox(0.5f + WIREFRAME_EXPAND, 0.5f + WIREFRAME_EXPAND, 0.5f + WIREFRAME_EXPAND);
		_wireframeGeometry = new Geometry("BlockHighlight", wireBox);
		_wireframeGeometry.setMaterial(_wireframeMaterial);
		_wireframeGeometry.setQueueBucket(Bucket.Translucent);
		_wireframeGeometry.setCullHint(Geometry.CullHint.Always); // Hidden initially.
		_overlayNode.attachChild(_wireframeGeometry);
		
		// Build crack overlay geometry (single quad, mesh updated dynamically).
		// Initialize with a minimal valid mesh (1 degenerate triangle) so Lemur's
		// pick system never encounters a Mesh without a Position buffer.
		final Mesh placeholderMesh = new Mesh();
		// @formatter:off
		placeholderMesh.setBuffer(Type.Position, 3, BufferUtils.createFloatBuffer(new float[]{0,0,0, 0,0,0, 0,0,0}));
		placeholderMesh.setBuffer(Type.Index, 3, BufferUtils.createIntBuffer(new int[]{0, 1, 2}));
		// @formatter:on
		placeholderMesh.updateBound();
		_crackGeometry = new Geometry("CrackOverlay", placeholderMesh);
		_crackGeometry.setMaterial(_crackMaterial);
		_crackGeometry.setQueueBucket(Bucket.Transparent);
		_crackGeometry.setCullHint(Geometry.CullHint.Always); // Hidden initially.
		_overlayNode.attachChild(_crackGeometry);
		
		// Set initial selected block.
		_selectedBlockIndex = 1; // DIRT
		_playerController.setSelectedBlock(PLACEABLE_BLOCKS[_selectedBlockIndex]);
	}
	
	// ========================================================
	// Input Registration.
	// ========================================================
	
	/**
	 * Registers mouse input mappings for attack, place, and block selection.
	 */
	public void registerInput()
	{
		// Register mappings if not already present.
		if (!_inputManager.hasMapping(ACTION_ATTACK))
		{
			_inputManager.addMapping(ACTION_ATTACK, new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
		}
		
		if (!_inputManager.hasMapping(ACTION_PLACE))
		{
			_inputManager.addMapping(ACTION_PLACE, new MouseButtonTrigger(MouseInput.BUTTON_RIGHT));
		}
		
		if (!_inputManager.hasMapping(ACTION_NEXT_BLOCK))
		{
			_inputManager.addMapping(ACTION_NEXT_BLOCK, new MouseAxisTrigger(MouseInput.AXIS_WHEEL, false));
		}
		
		if (!_inputManager.hasMapping(ACTION_PREV_BLOCK))
		{
			_inputManager.addMapping(ACTION_PREV_BLOCK, new MouseAxisTrigger(MouseInput.AXIS_WHEEL, true));
		}
		
		_inputManager.addListener((ActionListener) this, ACTION_ATTACK, ACTION_PLACE);
		_inputManager.addListener((AnalogListener) this, ACTION_NEXT_BLOCK, ACTION_PREV_BLOCK);
	}
	
	/**
	 * Removes input listeners.
	 */
	public void unregisterInput()
	{
		_inputManager.removeListener(this);
	}
	
	// ========================================================
	// Update.
	// ========================================================
	
	/**
	 * Updates block interaction each frame: raycast, breaking, placement, overlays.
	 * @param tpf time per frame in seconds
	 */
	public void update(float tpf)
	{
		// Tick cooldown.
		if (_hitCooldownTimer > 0)
		{
			_hitCooldownTimer -= tpf;
		}
		
		// Tick camera shake.
		if (_shakeTimer > 0)
		{
			_shakeTimer -= tpf;
			if (_shakeTimer <= 0)
			{
				_shakeOffset.set(0, 0, 0);
			}
		}
		
		// Perform raycast.
		performRaycast();
		
		// Update wireframe highlight.
		updateWireframe();
		
		// Handle attack (breaking).
		if (_attackHeld && _hasTarget)
		{
			handleAttack();
		}
		
		// Handle place (single press).
		if (_placePressed)
		{
			_placePressed = false;
			handlePlace();
		}
		
		// Update crack overlay.
		updateCrackOverlay();
	}
	
	// ========================================================
	// Raycasting.
	// ========================================================
	
	/**
	 * Steps along the camera ray to find the first non-AIR, non-liquid block.
	 */
	private void performRaycast()
	{
		_rayOrigin.set(_camera.getLocation());
		_rayOrigin.subtractLocal(_shakeOffset);
		_rayDirection.set(_camera.getDirection());
		_rayDirection.normalizeLocal();
		
		_hasTarget = false;
		_hasPlacePos = false;
		
		int prevBlockX = Integer.MIN_VALUE;
		int prevBlockY = Integer.MIN_VALUE;
		int prevBlockZ = Integer.MIN_VALUE;
		
		final int steps = (int) (RAY_DISTANCE / RAY_STEP);
		for (int i = 0; i <= steps; i++)
		{
			final float distance = i * RAY_STEP;
			final float px = _rayOrigin.x + _rayDirection.x * distance;
			final float py = _rayOrigin.y + _rayDirection.y * distance;
			final float pz = _rayOrigin.z + _rayDirection.z * distance;
			
			final int bx = (int) Math.floor(px);
			final int by = (int) Math.floor(py);
			final int bz = (int) Math.floor(pz);
			
			// Skip duplicate block positions (multiple steps in same block).
			if (bx == prevBlockX && by == prevBlockY && bz == prevBlockZ)
			{
				continue;
			}
			
			final Block block = _world.getBlock(bx, by, bz);
			
			if (block != Block.AIR && !block.isLiquid())
			{
				// Hit a targetable block.
				_targetX = bx;
				_targetY = by;
				_targetZ = bz;
				_hasTarget = true;
				
				// Determine which face was hit and whether to target self or adjacent.
				_targetFace = determineFace(bx, by, bz);
				
				// Only set placement position when not targeting self (edge region).
				if (!_targetSelf)
				{
					faceToOffset(_targetFace);
					_placeX = bx + _faceOffsetX;
					_placeY = by + _faceOffsetY;
					_placeZ = bz + _faceOffsetZ;
					_hasPlacePos = true;
				}
				
				break;
			}
			
			prevBlockX = bx;
			prevBlockY = by;
			prevBlockZ = bz;
		}
		
		// If target changed, reset breaking progress.
		if (_hasTarget)
		{
			if (!_isBreaking || _targetX != _breakingX || _targetY != _breakingY || _targetZ != _breakingZ)
			{
				resetBreaking();
			}
		}
		else
		{
			resetBreaking();
		}
	}
	
	/** Edge threshold — outer 20% of a face redirects to the adjacent block. */
	private static final float EDGE_THRESHOLD = 0.2f;
	
	/** When true, the crosshair is in the center of the face — target the block itself. */
	private boolean _targetSelf;
	
	/**
	 * Determines the interaction face using ray-AABB intersection and edge detection.<br>
	 * Finds the exact hit point on the block face, then checks the 2D position:<br>
	 * - Outer 20% on any edge → redirects to the adjacent face (placement mode)<br>
	 * - Center 60% → targets the block itself (breaking mode, sets {@code _targetSelf})<br>
	 * If the edge redirect leads to a solid block, falls back to targeting self.
	 */
	private Face determineFace(int bx, int by, int bz)
	{
		_targetSelf = false;
		
		final Vector3f origin = _camera.getLocation();
		final Vector3f dir = _camera.getDirection();
		
		// Ray-AABB slab test to find entry face and t.
		float tMin = Float.NEGATIVE_INFINITY;
		float tMax = Float.POSITIVE_INFINITY;
		Face entryFace = Face.TOP;
		
		// X axis.
		if (Math.abs(dir.x) > 1e-8f)
		{
			float t1 = (bx - origin.x) / dir.x;
			float t2 = (bx + 1 - origin.x) / dir.x;
			final Face near = dir.x > 0 ? Face.WEST : Face.EAST;
			if (t1 > t2)
			{
				final float tmp = t1;
				t1 = t2;
				t2 = tmp;
			}
			if (t1 > tMin)
			{
				tMin = t1;
				entryFace = near;
			}
			tMax = Math.min(tMax, t2);
		}
		
		// Y axis.
		if (Math.abs(dir.y) > 1e-8f)
		{
			float t1 = (by - origin.y) / dir.y;
			float t2 = (by + 1 - origin.y) / dir.y;
			final Face near = dir.y > 0 ? Face.BOTTOM : Face.TOP;
			if (t1 > t2)
			{
				final float tmp = t1;
				t1 = t2;
				t2 = tmp;
			}
			if (t1 > tMin)
			{
				tMin = t1;
				entryFace = near;
			}
			tMax = Math.min(tMax, t2);
		}
		
		// Z axis.
		if (Math.abs(dir.z) > 1e-8f)
		{
			float t1 = (bz - origin.z) / dir.z;
			float t2 = (bz + 1 - origin.z) / dir.z;
			final Face near = dir.z > 0 ? Face.SOUTH : Face.NORTH;
			if (t1 > t2)
			{
				final float tmp = t1;
				t1 = t2;
				t2 = tmp;
			}
			if (t1 > tMin)
			{
				tMin = t1;
				entryFace = near;
			}
			tMax = Math.min(tMax, t2);
		}
		
		if (tMin > tMax || tMax < 0)
		{
			_targetSelf = true;
			return Face.TOP;
		}
		
		// Compute hit point in local block coordinates [0, 1].
		final float t = tMin >= 0 ? tMin : 0;
		final float lx = Math.max(0, Math.min(1, origin.x + dir.x * t - bx));
		final float ly = Math.max(0, Math.min(1, origin.y + dir.y * t - by));
		final float lz = Math.max(0, Math.min(1, origin.z + dir.z * t - bz));
		
		// Check edges of the hit face for redirection.
		final Face edgeFace = checkFaceEdges(entryFace, lx, ly, lz);
		
		if (edgeFace != null)
		{
			// Check if the adjacent block in that direction is empty (placeable).
			faceToOffset(edgeFace);
			final Block adjacent = _world.getBlock(bx + _faceOffsetX, by + _faceOffsetY, bz + _faceOffsetZ);
			if (!adjacent.isSolid())
			{
				return edgeFace;
			}
		}
		
		// Center of face or edge redirect blocked → target the block itself.
		_targetSelf = true;
		return entryFace;
	}
	
	/**
	 * Checks the 2D position on a block face for edge proximity.<br>
	 * Returns the adjacent face if the hit point is within the outer 20%,<br>
	 * or null if the hit point is in the center 60%.
	 */
	private Face checkFaceEdges(Face entryFace, float lx, float ly, float lz)
	{
		// Select the two local axes that span this face.
		final float a;
		final float b;
		final Face lowA;
		final Face highA;
		final Face lowB;
		final Face highB;
		
		switch (entryFace)
		{
			case TOP:
			case BOTTOM:
			{
				// Face spans X and Z.
				a = lx;
				b = lz;
				lowA = Face.WEST;
				highA = Face.EAST;
				lowB = Face.SOUTH;
				highB = Face.NORTH;
				break;
			}
			case NORTH:
			case SOUTH:
			{
				// Face spans X and Y.
				a = lx;
				b = ly;
				lowA = Face.WEST;
				highA = Face.EAST;
				lowB = Face.BOTTOM;
				highB = Face.TOP;
				break;
			}
			case EAST:
			case WEST:
			{
				// Face spans Z and Y.
				a = lz;
				b = ly;
				lowA = Face.SOUTH;
				highA = Face.NORTH;
				lowB = Face.BOTTOM;
				highB = Face.TOP;
				break;
			}
			default:
			{
				return null;
			}
		}
		
		// Check which edges (if any) the hit point is near.
		final boolean aLow = a < EDGE_THRESHOLD;
		final boolean aHigh = a > 1 - EDGE_THRESHOLD;
		final boolean bLow = b < EDGE_THRESHOLD;
		final boolean bHigh = b > 1 - EDGE_THRESHOLD;
		
		// Center — no edge redirect.
		if (!aLow && !aHigh && !bLow && !bHigh)
		{
			return null;
		}
		
		final Face faceA = aLow ? lowA : aHigh ? highA : null;
		final Face faceB = bLow ? lowB : bHigh ? highB : null;
		
		// Corner — pick the axis closest to the edge.
		if (faceA != null && faceB != null)
		{
			final float distA = aLow ? a : 1 - a;
			final float distB = bLow ? b : 1 - b;
			return distA < distB ? faceA : faceB;
		}
		
		return faceA != null ? faceA : faceB;
	}
	
	/**
	 * Sets the _faceOffset fields to the outward normal of the given face.<br>
	 * Used to calculate the placement position from target + face offset.
	 */
	private void faceToOffset(Face face)
	{
		_faceOffsetX = 0;
		_faceOffsetY = 0;
		_faceOffsetZ = 0;
		
		switch (face)
		{
			case TOP:
			{
				_faceOffsetY = 1;
				break;
			}
			case BOTTOM:
			{
				_faceOffsetY = -1;
				break;
			}
			case NORTH:
			{
				_faceOffsetZ = 1;
				break;
			}
			case SOUTH:
			{
				_faceOffsetZ = -1;
				break;
			}
			case EAST:
			{
				_faceOffsetX = 1;
				break;
			}
			case WEST:
			{
				_faceOffsetX = -1;
				break;
			}
		}
	}
	
	// ========================================================
	// Breaking.
	// ========================================================
	
	/**
	 * Handles attack input: delivers hits to the target block.
	 */
	private void handleAttack()
	{
		if (!_hasTarget)
		{
			return;
		}
		
		// Check cooldown.
		if (_hitCooldownTimer > 0)
		{
			return;
		}
		
		final Block block = _world.getBlock(_targetX, _targetY, _targetZ);
		if (block == Block.AIR)
		{
			return;
		}
		
		// Unbreakable block (WATER, BEDROCK).
		if (!block.isBreakable())
		{
			_hitCooldownTimer = HIT_COOLDOWN;
			System.out.println("Cannot break " + block.name() + " — unbreakable.");
			return;
		}
		
		// Start tracking this block if not already.
		if (!_isBreaking)
		{
			_breakingX = _targetX;
			_breakingY = _targetY;
			_breakingZ = _targetZ;
			_hitsDelivered = 0;
			_hitsRequired = block.getHardness();
			_isBreaking = true;
		}
		
		// Deliver hit.
		_hitsDelivered++;
		_hitCooldownTimer = HIT_COOLDOWN;
		
		System.out.println("Hit " + block.name() + " (" + _hitsDelivered + "/" + _hitsRequired + ")");
		
		// Apply camera shake.
		applyShake();
		
		// Check if broken.
		if (_hitsDelivered >= _hitsRequired)
		{
			System.out.println("Broke " + block.name() + " at [" + _targetX + ", " + _targetY + ", " + _targetZ + "]");
			
			// If any adjacent block is liquid, replace with WATER instead of AIR.
			final Block replacement = (_targetY <= World.WATER_LEVEL && hasAdjacentLiquid(_targetX, _targetY, _targetZ)) ? Block.WATER : Block.AIR;
			_world.setBlockImmediate(_targetX, _targetY, _targetZ, replacement);
			resetBreaking();
		}
	}
	
	/**
	 * Resets the breaking state (progress, target tracking).
	 */
	private void resetBreaking()
	{
		_isBreaking = false;
		_hitsDelivered = 0;
		_hitsRequired = 0;
	}
	
	/**
	 * Returns true if any of the six adjacent blocks at the given position is a liquid.
	 */
	private boolean hasAdjacentLiquid(int x, int y, int z)
	{
		return _world.getBlock(x, y + 1, z).isLiquid() || _world.getBlock(x, y - 1, z).isLiquid() || _world.getBlock(x + 1, y, z).isLiquid() || _world.getBlock(x - 1, y, z).isLiquid() || _world.getBlock(x, y, z + 1).isLiquid() || _world.getBlock(x, y, z - 1).isLiquid();
	}
	
	/**
	 * Returns true if any of the six adjacent blocks at the given position is solid.
	 */
	private boolean hasAdjacentSolid(int x, int y, int z)
	{
		return _world.getBlock(x, y - 1, z).isSolid() || _world.getBlock(x, y + 1, z).isSolid() || _world.getBlock(x + 1, y, z).isSolid() || _world.getBlock(x - 1, y, z).isSolid() || _world.getBlock(x, y, z + 1).isSolid() || _world.getBlock(x, y, z - 1).isSolid();
	}
	
	/**
	 * Applies a brief camera shake for hit feedback.
	 */
	private void applyShake()
	{
		_shakeTimer = SHAKE_DURATION;
		// @formatter:off
		_shakeOffset.set(
			(FastMath.nextRandomFloat() - 0.5f) * 2.0f * SHAKE_MAGNITUDE,
			(FastMath.nextRandomFloat() - 0.5f) * 2.0f * SHAKE_MAGNITUDE,
			0
		);
		// @formatter:on
		
		// Apply shake to camera (reuses _rayOrigin to avoid allocation).
		_rayOrigin.set(_camera.getLocation());
		_rayOrigin.addLocal(_shakeOffset);
		_camera.setLocation(_rayOrigin);
	}
	
	// ========================================================
	// Placing.
	// ========================================================
	
	/**
	 * Handles block placement at the placement position.
	 */
	private void handlePlace()
	{
		if (!_hasTarget)
		{
			return;
		}
		
		// Check if targeting a tile entity block — interact instead of placing.
		final Block targetBlock = _world.getBlock(_targetX, _targetY, _targetZ);
		if (targetBlock.isTileEntity())
		{
			System.out.println("Interacted with " + targetBlock.name() + " at [" + _targetX + ", " + _targetY + ", " + _targetZ + "]");
			return;
		}
		
		final Block selectedBlock = _playerController.getSelectedBlock();
		if (selectedBlock == null || selectedBlock == Block.AIR)
		{
			return;
		}
		
		// Cannot place WATER or BEDROCK.
		if (selectedBlock == Block.WATER || selectedBlock == Block.BEDROCK)
		{
			System.out.println("Cannot place " + selectedBlock.name() + ".");
			return;
		}
		
		// Determine actual placement position and validate.
		int placeX = _placeX;
		int placeY = _placeY;
		int placeZ = _placeZ;
		boolean hasPlace = _hasPlacePos;
		
		// If targeting a decoration block (tall grass, flowers), replace it directly.
		if (targetBlock.isDecoration())
		{
			placeX = _targetX;
			placeY = _targetY;
			placeZ = _targetZ;
			hasPlace = true;
		}
		// When _targetSelf, place at adjacent face position if target is solid.
		else if (_targetSelf)
		{
			if (targetBlock.isSolid())
			{
				faceToOffset(_targetFace);
				final int adjX = _targetX + _faceOffsetX;
				final int adjY = _targetY + _faceOffsetY;
				final int adjZ = _targetZ + _faceOffsetZ;
				final Block adjacent = _world.getBlock(adjX, adjY, adjZ);
				if (!adjacent.isSolid())
				{
					placeX = adjX;
					placeY = adjY;
					placeZ = adjZ;
					hasPlace = true;
				}
			}
		}
		
		if (!hasPlace)
		{
			return;
		}
		
		// Torch placement — special rules.
		if (selectedBlock == Block.TORCH)
		{
			if (!canPlaceTorch(placeX, placeY, placeZ))
			{
				System.out.println("Cannot place TORCH here — needs a solid surface.");
				return;
			}
		}
		
		// Flower/grass placement — must be on top of GRASS or DIRT.
		if (isSoilOnlyBlock(selectedBlock))
		{
			if (!canPlaceOnSoil(placeX, placeY, placeZ))
			{
				System.out.println("Cannot place " + selectedBlock.name() + " here — needs GRASS or DIRT below.");
				return;
			}
		}
		
		// Cannot place into a solid block.
		final Block existing = _world.getBlock(placeX, placeY, placeZ);
		if (existing.isSolid())
		{
			return;
		}
		
		// Cannot place non-solid blocks (decorations, billboards) into liquid.
		if (existing.isLiquid() && !selectedBlock.isSolid())
		{
			System.out.println("Cannot place " + selectedBlock.name() + " in water.");
			return;
		}
		
		// Cannot place into a seaweed block.
		if (existing == Block.TALL_SEAWEED || existing == Block.SHORT_SEAWEED)
		{
			return;
		}
		
		// Require at least one adjacent solid block for support.
		// Skip this check for decorations replaced in-place (they already sit on solid ground).
		if (!targetBlock.isDecoration() && !hasAdjacentSolid(placeX, placeY, placeZ))
		{
			System.out.println("Cannot place block here — no solid support.");
			return;
		}
		
		// Check that the block doesn't overlap the player AABB.
		if (overlapsPlayer(placeX, placeY, placeZ))
		{
			System.out.println("Cannot place block — overlaps player.");
			return;
		}
		
		// Place the block.
		_world.setBlockImmediate(placeX, placeY, placeZ, selectedBlock);
		System.out.println("Placed " + selectedBlock.name() + " at [" + placeX + ", " + placeY + ", " + placeZ + "]");
	}
	
	/**
	 * Checks if a torch can be placed at the given position.<br>
	 * Torch requires a solid block on at least one adjacent face (below or sides).
	 */
	private boolean canPlaceTorch(int x, int y, int z)
	{
		// Check block below.
		final Block below = _world.getBlock(x, y - 1, z);
		if (below.isSolid())
		{
			return true;
		}
		
		// Check side blocks.
		if (_world.getBlock(x + 1, y, z).isSolid())
		{
			return true;
		}
		
		if (_world.getBlock(x - 1, y, z).isSolid())
		{
			return true;
		}
		
		if (_world.getBlock(x, y, z + 1).isSolid())
		{
			return true;
		}
		
		if (_world.getBlock(x, y, z - 1).isSolid())
		{
			return true;
		}
		
		return false;
	}
	
	/**
	 * Checks if a soil-only block (flowers, tall grass) can be placed at the given position.<br>
	 * Requires GRASS or DIRT directly below.
	 */
	private boolean canPlaceOnSoil(int x, int y, int z)
	{
		final Block below = _world.getBlock(x, y - 1, z);
		return below == Block.GRASS || below == Block.DIRT;
	}
	
	/**
	 * Returns true if the given block is a soil-only decoration (flower or tall grass).
	 */
	private static boolean isSoilOnlyBlock(Block block)
	{
		for (Block soilBlock : SOIL_ONLY_BLOCKS)
		{
			if (block == soilBlock)
			{
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Checks if placing a block at the given position would overlap the player's AABB.
	 */
	private boolean overlapsPlayer(int bx, int by, int bz)
	{
		final Vector3f pos = _playerController.getPosition();
		
		// Player AABB.
		final float pMinX = pos.x - PLAYER_HALF_WIDTH;
		final float pMaxX = pos.x + PLAYER_HALF_WIDTH;
		final float pMinY = pos.y;
		final float pMaxY = pos.y + PLAYER_HEIGHT;
		final float pMinZ = pos.z - PLAYER_HALF_WIDTH;
		final float pMaxZ = pos.z + PLAYER_HALF_WIDTH;
		
		// Block AABB (integer position to +1).
		final float blockMinX = bx;
		final float blockMaxX = bx + 1;
		final float blockMinY = by;
		final float blockMaxY = by + 1;
		final float blockMinZ = bz;
		final float blockMaxZ = bz + 1;
		
		// AABB overlap test.
		return pMinX < blockMaxX && pMaxX > blockMinX && pMinY < blockMaxY && pMaxY > blockMinY && pMinZ < blockMaxZ && pMaxZ > blockMinZ;
	}
	
	// ========================================================
	// Block Selection (Mouse Wheel).
	// ========================================================
	
	/**
	 * Cycles to the next placeable block.
	 */
	private void nextBlock()
	{
		_selectedBlockIndex = (_selectedBlockIndex + 1) % PLACEABLE_BLOCKS.length;
		_playerController.setSelectedBlock(PLACEABLE_BLOCKS[_selectedBlockIndex]);
		System.out.println("Selected: " + PLACEABLE_BLOCKS[_selectedBlockIndex].name());
	}
	
	/**
	 * Cycles to the previous placeable block.
	 */
	private void previousBlock()
	{
		_selectedBlockIndex = (_selectedBlockIndex - 1 + PLACEABLE_BLOCKS.length) % PLACEABLE_BLOCKS.length;
		_playerController.setSelectedBlock(PLACEABLE_BLOCKS[_selectedBlockIndex]);
		System.out.println("Selected: " + PLACEABLE_BLOCKS[_selectedBlockIndex].name());
	}
	
	// ========================================================
	// Wireframe Highlight.
	// ========================================================
	
	/**
	 * Updates the wireframe highlight position and visibility.
	 */
	private void updateWireframe()
	{
		// Only show wireframe when a placeable block is equipped.
		final Block selectedBlock = _playerController.getSelectedBlock();
		if (_hasTarget && selectedBlock != null && selectedBlock != Block.AIR)
		{
			final Block targetBlock = _world.getBlock(_targetX, _targetY, _targetZ);
			
			int showX;
			int showY;
			int showZ;
			
			if (targetBlock.isDecoration())
			{
				// Decoration: always replace in-place.
				showX = _targetX;
				showY = _targetY;
				showZ = _targetZ;
			}
			else if (_targetSelf)
			{
				if (targetBlock.isSolid())
				{
					// TOP/BOTTOM face center: always show placement above/below if empty.
					if (_targetFace == Face.TOP || _targetFace == Face.BOTTOM)
					{
						faceToOffset(_targetFace);
						final Block adjacent = _world.getBlock(_targetX + _faceOffsetX, _targetY + _faceOffsetY, _targetZ + _faceOffsetZ);
						if (!adjacent.isSolid())
						{
							showX = _targetX + _faceOffsetX;
							showY = _targetY + _faceOffsetY;
							showZ = _targetZ + _faceOffsetZ;
						}
						else
						{
							// Blocked above/below — show on target for breaking.
							showX = _targetX;
							showY = _targetY;
							showZ = _targetZ;
						}
					}
					else
					{
						// Side face center — show on target for breaking.
						showX = _targetX;
						showY = _targetY;
						showZ = _targetZ;
					}
				}
				else if (targetBlock.isBreakable())
				{
					// Non-solid but breakable (leaves) — show on target for breaking.
					showX = _targetX;
					showY = _targetY;
					showZ = _targetZ;
				}
				else
				{
					// Non-solid, non-breakable — hide.
					if (_wireframeVisible)
					{
						_wireframeGeometry.setCullHint(Geometry.CullHint.Always);
						_wireframeVisible = false;
					}
					return;
				}
			}
			else if (!targetBlock.isSolid())
			{
				// Non-solid, non-decoration: show for breaking if breakable, hide otherwise.
				if (targetBlock.isBreakable())
				{
					showX = _targetX;
					showY = _targetY;
					showZ = _targetZ;
				}
				else
				{
					if (_wireframeVisible)
					{
						_wireframeGeometry.setCullHint(Geometry.CullHint.Always);
						_wireframeVisible = false;
					}
					return;
				}
			}
			else if (_hasPlacePos)
			{
				// Solid target, edge region: show at placement position.
				showX = _placeX;
				showY = _placeY;
				showZ = _placeZ;
				
				// If placement spot is solid, fall back to target.
				final Block placeBlock = _world.getBlock(showX, showY, showZ);
				if (placeBlock.isSolid())
				{
					showX = _targetX;
					showY = _targetY;
					showZ = _targetZ;
				}
			}
			else
			{
				// No valid placement — fall back to target.
				showX = _targetX;
				showY = _targetY;
				showZ = _targetZ;
			}
			
			// Position wireframe.
			_wireframeGeometry.setLocalTranslation(showX + 0.5f, showY + 0.5f, showZ + 0.5f);
			
			// Swap material based on breakability.
			if (targetBlock.isBreakable())
			{
				_wireframeGeometry.setMaterial(_wireframeMaterial);
			}
			else
			{
				_wireframeGeometry.setMaterial(_wireframeUnbreakableMaterial);
			}
			
			if (!_wireframeVisible)
			{
				_wireframeGeometry.setCullHint(Geometry.CullHint.Never);
				_wireframeVisible = true;
			}
		}
		else
		{
			if (_wireframeVisible)
			{
				_wireframeGeometry.setCullHint(Geometry.CullHint.Always);
				_wireframeVisible = false;
			}
		}
	}
	
	// ========================================================
	// Crack Overlay.
	// ========================================================
	
	/**
	 * Updates the crack overlay based on breaking progress.
	 */
	private void updateCrackOverlay()
	{
		if (_isBreaking && _hitsDelivered > 0 && _hitsRequired > 0)
		{
			final float progress = (float) _hitsDelivered / _hitsRequired;
			final float alpha = progress * CRACK_MAX_ALPHA;
			
			// Update crack material color.
			_crackMaterial.setColor("Color", new ColorRGBA(0, 0, 0, alpha));
			
			// Build quad mesh on the targeted face.
			buildCrackQuad(_breakingX, _breakingY, _breakingZ, _targetFace);
			
			if (!_crackVisible)
			{
				_crackGeometry.setCullHint(Geometry.CullHint.Never);
				_crackVisible = true;
			}
		}
		else
		{
			if (_crackVisible)
			{
				_crackGeometry.setCullHint(Geometry.CullHint.Always);
				_crackVisible = false;
			}
		}
	}
	
	/**
	 * Builds a quad mesh on the specified face of the given block position.<br>
	 * The quad is offset slightly outward to prevent z-fighting.
	 */
	private void buildCrackQuad(int bx, int by, int bz, Face face)
	{
		final float[] positions = new float[12]; // 4 vertices × 3 coords.
		final float[] normals = new float[12];
		// @formatter:off
		final int[] indices =
		{
			0, 1, 2, 0, 2, 3
		};
		// @formatter:on
		
		float nx = 0;
		float ny = 0;
		float nz = 0;
		
		switch (face)
		{
			case TOP:
			{
				ny = 1;
				final float y = by + 1 + CRACK_OFFSET;
				positions[0] = bx;
				positions[1] = y;
				positions[2] = bz;
				positions[3] = bx;
				positions[4] = y;
				positions[5] = bz + 1;
				positions[6] = bx + 1;
				positions[7] = y;
				positions[8] = bz + 1;
				positions[9] = bx + 1;
				positions[10] = y;
				positions[11] = bz;
				break;
			}
			case BOTTOM:
			{
				ny = -1;
				final float y = by - CRACK_OFFSET;
				positions[0] = bx;
				positions[1] = y;
				positions[2] = bz + 1;
				positions[3] = bx;
				positions[4] = y;
				positions[5] = bz;
				positions[6] = bx + 1;
				positions[7] = y;
				positions[8] = bz;
				positions[9] = bx + 1;
				positions[10] = y;
				positions[11] = bz + 1;
				break;
			}
			case NORTH:
			{
				nz = 1;
				final float z = bz + 1 + CRACK_OFFSET;
				positions[0] = bx + 1;
				positions[1] = by;
				positions[2] = z;
				positions[3] = bx + 1;
				positions[4] = by + 1;
				positions[5] = z;
				positions[6] = bx;
				positions[7] = by + 1;
				positions[8] = z;
				positions[9] = bx;
				positions[10] = by;
				positions[11] = z;
				break;
			}
			case SOUTH:
			{
				nz = -1;
				final float z = bz - CRACK_OFFSET;
				positions[0] = bx;
				positions[1] = by;
				positions[2] = z;
				positions[3] = bx;
				positions[4] = by + 1;
				positions[5] = z;
				positions[6] = bx + 1;
				positions[7] = by + 1;
				positions[8] = z;
				positions[9] = bx + 1;
				positions[10] = by;
				positions[11] = z;
				break;
			}
			case EAST:
			{
				nx = 1;
				final float x = bx + 1 + CRACK_OFFSET;
				positions[0] = x;
				positions[1] = by;
				positions[2] = bz;
				positions[3] = x;
				positions[4] = by + 1;
				positions[5] = bz;
				positions[6] = x;
				positions[7] = by + 1;
				positions[8] = bz + 1;
				positions[9] = x;
				positions[10] = by;
				positions[11] = bz + 1;
				break;
			}
			case WEST:
			{
				nx = -1;
				final float x = bx - CRACK_OFFSET;
				positions[0] = x;
				positions[1] = by;
				positions[2] = bz + 1;
				positions[3] = x;
				positions[4] = by + 1;
				positions[5] = bz + 1;
				positions[6] = x;
				positions[7] = by + 1;
				positions[8] = bz;
				positions[9] = x;
				positions[10] = by;
				positions[11] = bz;
				break;
			}
		}
		
		// Fill normals (same for all 4 vertices).
		for (int v = 0; v < 4; v++)
		{
			normals[v * 3] = nx;
			normals[v * 3 + 1] = ny;
			normals[v * 3 + 2] = nz;
		}
		
		// Update mesh buffers.
		final Mesh mesh = _crackGeometry.getMesh();
		mesh.setBuffer(Type.Position, 3, BufferUtils.createFloatBuffer(positions));
		mesh.setBuffer(Type.Normal, 3, BufferUtils.createFloatBuffer(normals));
		mesh.setBuffer(Type.Index, 3, BufferUtils.createIntBuffer(indices));
		mesh.updateBound();
	}
	
	// ========================================================
	// Input Callbacks.
	// ========================================================
	
	@Override
	public void onAction(String name, boolean isPressed, float tpf)
	{
		switch (name)
		{
			case ACTION_ATTACK:
			{
				_attackHeld = isPressed;
				// Reset cooldown on fresh press so first click is instant.
				if (isPressed)
				{
					_hitCooldownTimer = 0;
				}
				break;
			}
			case ACTION_PLACE:
			{
				// Single press (not hold).
				if (isPressed)
				{
					_placePressed = true;
				}
				break;
			}
		}
	}
	
	@Override
	public void onAnalog(String name, float value, float tpf)
	{
		switch (name)
		{
			case ACTION_NEXT_BLOCK:
			{
				nextBlock();
				break;
			}
			case ACTION_PREV_BLOCK:
			{
				previousBlock();
				break;
			}
		}
	}
	
	// ========================================================
	// Accessors.
	// ========================================================
	
	/**
	 * Returns the overlay node containing wireframe and crack geometries.<br>
	 * Must be attached to the scene by the owning state.
	 */
	public Node getOverlayNode()
	{
		return _overlayNode;
	}
	
	/**
	 * Returns true if a block is currently targeted by the raycast.
	 */
	public boolean hasTarget()
	{
		return _hasTarget;
	}
	
	/**
	 * Returns the targeted block type, or AIR if no target.
	 */
	public Block getTargetBlock()
	{
		if (!_hasTarget)
		{
			return Block.AIR;
		}
		
		return _world.getBlock(_targetX, _targetY, _targetZ);
	}
	
	/**
	 * Returns the current break progress (0.0 to 1.0), or 0 if not breaking.
	 */
	public float getBreakProgress()
	{
		if (!_isBreaking || _hitsRequired <= 0)
		{
			return 0;
		}
		
		return (float) _hitsDelivered / _hitsRequired;
	}
	
	/**
	 * Returns the currently selected placeable block.
	 */
	public Block getSelectedBlock()
	{
		return PLACEABLE_BLOCKS[_selectedBlockIndex];
	}
}
