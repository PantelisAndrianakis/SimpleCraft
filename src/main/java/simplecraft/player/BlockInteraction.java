package simplecraft.player;

import java.util.List;

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
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.scene.debug.WireBox;
import com.jme3.util.BufferUtils;

import simplecraft.audio.AudioManager;
import simplecraft.effects.ParticleManager;
import simplecraft.item.DropManager;
import simplecraft.item.Inventory;
import simplecraft.item.ItemInstance;
import simplecraft.item.ItemRegistry;
import simplecraft.item.ItemTemplate;
import simplecraft.item.ItemType;
import simplecraft.ui.MessageManager;
import simplecraft.util.Rnd;
import simplecraft.util.Vector3i;
import simplecraft.world.Block;
import simplecraft.world.Block.Face;
import simplecraft.world.BlockDestructionQueue;
import simplecraft.world.BlockSupport;
import simplecraft.world.TreeFeller;
import simplecraft.world.World;
import simplecraft.world.entity.CampfireTileEntity;
import simplecraft.world.entity.DoorTileEntity;
import simplecraft.world.entity.PlaceholderTileEntity;
import simplecraft.world.entity.TileEntity;
import simplecraft.world.entity.TileEntity.Facing;
import simplecraft.world.entity.TileEntityManager;
import simplecraft.world.entity.TorchTileEntity;
import simplecraft.world.entity.WindowTileEntity;

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
	
	/** Highlight expansion beyond block edges to prevent z-fighting. */
	private static final float HIGHLIGHT_EXPAND = 0.002f;
	
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
	private final AudioManager _audioManager;
	
	/** Scene node for highlight and crack overlay geometries. */
	private final Node _overlayNode = new Node("BlockInteractionOverlay");
	
	// Materials.
	private final Material _highlightMaterial;
	private final Material _highlightUnbreakableMaterial;
	private final Material _crackMaterial;
	
	// Block highlight.
	private Geometry _highlightGeometry;
	private boolean _highlightVisible;
	
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
	
	/** The raw entry face from the ray-AABB slab test, before any edge redirection. */
	private Face _entryFace;
	
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
	
	/** The item held when breaking started (for reset on tool switch). */
	private ItemInstance _breakingWithItem;
	
	/** Number of hits delivered to the current target. */
	private int _hitsDelivered;
	
	/** Required hits for the current target block. */
	private int _hitsRequired;
	
	/** Time remaining before the next hit can be delivered. */
	private float _hitCooldownTimer;
	
	// Input flags.
	private boolean _attackHeld;
	private boolean _placePressed;
	
	/**
	 * When true, attack processing is suppressed for this frame.<br>
	 * Set by PlayingState when the combat system detects an enemy in the crosshair,<br>
	 * preventing block breaking behind enemies during melee combat.
	 */
	private boolean _attackSuppressed;
	
	/** Whether the block highlight is enabled (from display settings). */
	private boolean _showHighlight = true;
	
	/** Manages staggered visual destruction of auto-destroyed blocks. */
	private final BlockDestructionQueue _destructionQueue;
	
	/** Particle effect manager for block break effects. */
	private ParticleManager _particleManager;
	
	/** Viewmodel renderer for triggering swing animation on block hits. */
	private ViewmodelRenderer _viewmodelRenderer;
	
	/** Drop manager for spawning world drops when blocks are broken. */
	private DropManager _dropManager;
	
	// Camera shake.
	private float _shakeTimer;
	private final Vector3f _shakeOffset = new Vector3f();
	
	/**
	 * True when the raycast detected a framed air block for direct window placement.<br>
	 * Set during performRaycast when WINDOW is selected and the ray passes through<br>
	 * an air block surrounded by solid blocks on most frame edges.
	 */
	private boolean _windowDirectPlace;
	
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
	 * @param audioManager the audio manager for sound effect playback
	 */
	public BlockInteraction(Camera camera, InputManager inputManager, World world, PlayerController playerController, AssetManager assetManager, AudioManager audioManager)
	{
		_camera = camera;
		_inputManager = inputManager;
		_world = world;
		_playerController = playerController;
		_audioManager = audioManager;
		
		// Create highlight material (bright white, unshaded).
		_highlightMaterial = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
		_highlightMaterial.setColor("Color", new ColorRGBA(1.0f, 1.0f, 1.0f, 0.9f));
		_highlightMaterial.getAdditionalRenderState().setWireframe(true);
		_highlightMaterial.getAdditionalRenderState().setLineWidth(2.0f);
		_highlightMaterial.getAdditionalRenderState().setDepthTest(false);
		
		// Create highlight material for unbreakable blocks (vivid red).
		_highlightUnbreakableMaterial = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
		_highlightUnbreakableMaterial.setColor("Color", new ColorRGBA(1.0f, 0.2f, 0.2f, 0.9f));
		_highlightUnbreakableMaterial.getAdditionalRenderState().setWireframe(true);
		_highlightUnbreakableMaterial.getAdditionalRenderState().setLineWidth(2.0f);
		_highlightUnbreakableMaterial.getAdditionalRenderState().setDepthTest(false);
		
		// Create crack overlay material (semi-transparent black).
		_crackMaterial = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
		_crackMaterial.setColor("Color", new ColorRGBA(0, 0, 0, 0));
		_crackMaterial.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
		
		// Build highlight geometry.
		final WireBox wireBox = new WireBox(0.5f + HIGHLIGHT_EXPAND, 0.5f + HIGHLIGHT_EXPAND, 0.5f + HIGHLIGHT_EXPAND);
		_highlightGeometry = new Geometry("BlockHighlight", wireBox);
		_highlightGeometry.setMaterial(_highlightMaterial);
		_highlightGeometry.setQueueBucket(Bucket.Translucent);
		_highlightGeometry.setCullHint(Geometry.CullHint.Always); // Hidden initially.
		_overlayNode.attachChild(_highlightGeometry);
		
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
		
		// Create destruction queue for staggered visual effects.
		_destructionQueue = new BlockDestructionQueue(_world, assetManager);
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
		
		// Update box highlight.
		updateHighlight();
		
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
		
		// Update destruction queue (staggered block removal + poof effects).
		_destructionQueue.update(tpf);
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
		_windowDirectPlace = false;
		
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
			
			// Window direct placement: detect air blocks with a valid 4-edge solid frame.
			// When the player aims through a hole in a wall with empty space behind,
			// the ray would normally pass through without stopping. This catches it.
			if (block == Block.AIR && _playerController.getSelectedBlock() == Block.WINDOW)
			{
				if (hasWindowFrame(bx, by, bz))
				{
					_targetX = bx;
					_targetY = by;
					_targetZ = bz;
					_hasTarget = true;
					_targetSelf = true;
					_windowDirectPlace = true;
					_hasPlacePos = false;
					break;
				}
			}
			
			prevBlockX = bx;
			prevBlockY = by;
			prevBlockZ = bz;
		}
		
		// If target changed or held item changed, reset breaking progress.
		if (_hasTarget)
		{
			if (!_isBreaking || _targetX != _breakingX || _targetY != _breakingY || _targetZ != _breakingZ)
			{
				resetBreaking();
			}
			else if (_isBreaking)
			{
				// Same block, but check if the player switched tools — recalculate hits required.
				final ItemInstance currentItem = _playerController.getInventory().getSelectedItem();
				if (currentItem != _breakingWithItem)
				{
					resetBreaking();
				}
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
			_entryFace = Face.TOP;
			return Face.TOP;
		}
		
		// Compute hit point in local block coordinates [0, 1].
		final float t = tMin >= 0 ? tMin : 0;
		final float lx = Math.max(0, Math.min(1, origin.x + dir.x * t - bx));
		final float ly = Math.max(0, Math.min(1, origin.y + dir.y * t - by));
		final float lz = Math.max(0, Math.min(1, origin.z + dir.z * t - bz));
		
		// Check edges of the hit face for redirection.
		_entryFace = entryFace;
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
		
		// Suppressed by combat system (enemy in crosshair).
		if (_attackSuppressed)
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
			// Still costs durability (wrong target = 2) — don't waste your tools on bedrock.
			final Inventory inventory = _playerController.getInventory();
			final ItemInstance held = inventory.getSelectedItem();
			if (held != null && held.hasDurability())
			{
				final boolean broken = held.loseDurability(DURABILITY_COST_WRONG);
				if (broken)
				{
					final String toolName = held.getTemplate().getDisplayName();
					inventory.setSlot(inventory.getSelectedHotbarIndex(), null);
					MessageManager.show("Your " + toolName + " broke!");
					System.out.println("Tool broke: " + toolName);
				}
			}
			_hitCooldownTimer = HIT_COOLDOWN;
			System.out.println("Cannot break " + block.name() + " — unbreakable.");
			return;
		}
		
		final Inventory inventory = _playerController.getInventory();
		final ItemInstance heldItem = inventory.getSelectedItem();
		
		// Start tracking this block if not already.
		if (!_isBreaking)
		{
			_breakingX = _targetX;
			_breakingY = _targetY;
			_breakingZ = _targetZ;
			_hitsDelivered = 0;
			_hitsRequired = getEffectiveHits(block, heldItem);
			_breakingWithItem = heldItem;
			_isBreaking = true;
		}
		
		// Deliver hit.
		_hitsDelivered++;
		_hitCooldownTimer = HIT_COOLDOWN;
		_audioManager.playSfx(block.isDecoration() ? AudioManager.SFX_STEP_GRASS : AudioManager.SFX_BLOCK_HIT);
		
		// Trigger viewmodel swing animation.
		if (_viewmodelRenderer != null)
		{
			_viewmodelRenderer.triggerSwing();
		}
		
		// Durability loss — correct tool costs 1, wrong tool/weapon costs 2.
		final int durabilityCost = getDurabilityCostPerHit(block, heldItem);
		if (durabilityCost > 0 && heldItem != null && heldItem.hasDurability())
		{
			final boolean broken = heldItem.loseDurability(durabilityCost);
			if (broken)
			{
				final String toolName = heldItem.getTemplate().getDisplayName();
				inventory.setSlot(inventory.getSelectedHotbarIndex(), null);
				MessageManager.show("Your " + toolName + " broke!");
				System.out.println("Tool broke: " + toolName);
				
				// Tool broke mid-block-break — recalculate effective hits with bare hands.
				final ItemInstance newHeld = inventory.getSelectedItem();
				final int newRequired = getEffectiveHits(block, newHeld);
				// Scale progress: keep the same ratio of completion.
				if (newRequired > _hitsRequired)
				{
					final float progress = (float) _hitsDelivered / _hitsRequired;
					_hitsRequired = newRequired;
					_hitsDelivered = (int) (progress * newRequired);
				}
				// Update tracked item so the tool-switch check doesn't reset on next frame.
				_breakingWithItem = newHeld;
			}
		}
		
		System.out.println("Hit " + block.name() + " (" + _hitsDelivered + "/" + _hitsRequired + ")");
		
		// Apply camera shake.
		applyShake();
		
		// Check if broken.
		if (_hitsDelivered >= _hitsRequired)
		{
			System.out.println("Broke " + block.name() + " at [" + _targetX + ", " + _targetY + ", " + _targetZ + "]");
			_audioManager.playSfx(block.isDecoration() ? AudioManager.SFX_STEP_DIRT : AudioManager.SFX_BLOCK_BREAK);
			
			// Spawn block break particles at the broken block position.
			if (_particleManager != null)
			{
				_particleManager.spawnBlockBreak(new Vector3f(_targetX, _targetY, _targetZ), block);
			}
			
			// Clear player-placed flag before removal.
			_world.clearPlayerPlaced(_targetX, _targetY, _targetZ);
			
			// Mark as player-removed (prevents enemy spawning in cleared areas).
			_world.markPlayerRemoved(_targetX, _targetY, _targetZ);
			
			// Remove tile entity if present.
			if (block.isTileEntity())
			{
				final TileEntityManager manager = _world.getTileEntityManager();
				if (manager != null)
				{
					final TileEntity entity = manager.get(_targetX, _targetY, _targetZ);
					if (entity != null)
					{
						// Door: breaking either half destroys both halves.
						if (entity instanceof DoorTileEntity)
						{
							final DoorTileEntity door = (DoorTileEntity) entity;
							final Vector3i partnerPos = door.getPartnerPos();
							
							// Clear player-placed and mark player-removed for both positions.
							_world.clearPlayerPlaced(_targetX, _targetY, _targetZ);
							_world.markPlayerRemoved(_targetX, _targetY, _targetZ);
							if (partnerPos != null)
							{
								_world.clearPlayerPlaced(partnerPos.x, partnerPos.y, partnerPos.z);
								_world.markPlayerRemoved(partnerPos.x, partnerPos.y, partnerPos.z);
							}
							
							// Destroy both halves (sets to AIR, removes tile entities, rebuilds mesh).
							door.destroyBothHalves(_world);
							
							// Drop the door item on the ground.
							dropBlockItem(Block.DOOR_BOTTOM, _targetX, _targetY, _targetZ);
							
							System.out.println("Broke DOOR at [" + _targetX + ", " + _targetY + ", " + _targetZ + "] — both halves destroyed.");
							_audioManager.playSfx(AudioManager.SFX_BLOCK_BREAK);
							resetBreaking();
							return;
						}
						
						// Non-door tile entities: standard removal.
						manager.remove(_targetX, _targetY, _targetZ);
						entity.onRemoved(_world);
						
						// If the broken campfire was the active respawn point, clear it.
						if (entity instanceof CampfireTileEntity)
						{
							final CampfireTileEntity campfire = (CampfireTileEntity) entity;
							if (campfire.isActivated())
							{
								_playerController.clearRespawnCampfire();
								MessageManager.show("Respawn point lost! You will respawn at world origin.");
							}
						}
					}
				}
			}
			
			// If any adjacent block is liquid, replace with WATER instead of AIR.
			final Block replacement = (_targetY <= World.WATER_LEVEL && hasAdjacentLiquid(_targetX, _targetY, _targetZ)) ? Block.WATER : Block.AIR;
			_world.setBlockImmediate(_targetX, _targetY, _targetZ, replacement);
			
			// Drop block item on the ground at the broken block position.
			dropBlockItem(block, _targetX, _targetY, _targetZ);
			
			// If WOOD was broken, trigger tree felling (block is already AIR).
			if (block == Block.WOOD)
			{
				final TreeFeller.FellingResult fellingResult = TreeFeller.fellTree(_world, _targetX, _targetY, _targetZ);
				_destructionQueue.queueTreeFelling(fellingResult);
				
				// Drop items for each block destroyed by the felling cascade.
				// The broken block itself was already handled by dropBlockItem above.
				// Each felled block drops at its own world position.
				final List<Block> felledTypes = fellingResult.getBlockTypes();
				final List<int[]> felledPositions = fellingResult.getBlocks();
				for (int i = 0; i < felledTypes.size(); i++)
				{
					final int[] pos = felledPositions.get(i);
					dropBlockItem(felledTypes.get(i), pos[0], pos[1], pos[2]);
				}
			}
			
			// Check block support for nearby player-placed blocks.
			final BlockSupport.CollapseResult collapseResult = BlockSupport.checkSupport(_world, _targetX, _targetY, _targetZ);
			_destructionQueue.queueCollapseResult(collapseResult);
			
			// Check if breaking this block removes support from neighboring tile entities
			// (torches, campfires, windows, doors). These aren't caught by BlockSupport
			// because they aren't solid player-placed blocks.
			removeSupportedTileEntities(_targetX, _targetY, _targetZ);
			
			resetBreaking();
		}
	}
	
	/** Block damage per hit with the correct tool. */
	private static final float BLOCK_DAMAGE_CORRECT_TOOL = 3.0f;
	
	/** Block damage per hit with a wrong tool or weapon. */
	private static final float BLOCK_DAMAGE_WRONG_TOOL = 2.0f;
	
	/** Block damage per hit with bare hands, blocks, or non-combat items. */
	private static final float BLOCK_DAMAGE_BARE_HANDS = 1.0f;
	
	/** Weapon durability cost per hit on the correct block type. */
	private static final int DURABILITY_COST_CORRECT = 1;
	
	/** Weapon durability cost per hit on the wrong block type. */
	private static final int DURABILITY_COST_WRONG = 2;
	
	/**
	 * Calculates the effective number of hits required to break a block.<br>
	 * <ul>
	 * <li>Correct tool (matching bestTool): 3 damage per hit</li>
	 * <li>Wrong tool or weapon: 2 damage per hit</li>
	 * <li>Bare hands / blocks / consumables / materials: 1 damage per hit</li>
	 * </ul>
	 * Hits = ceil(blockHP / damagePerHit).
	 * @param block the block being broken
	 * @param heldItem the currently held ItemInstance, or null for bare hands
	 * @return effective hits required, or -1 if unbreakable
	 */
	private int getEffectiveHits(Block block, ItemInstance heldItem)
	{
		final int hardness = block.getHardness();
		if (hardness <= 0)
		{
			return -1; // Unbreakable.
		}
		
		final float damagePerHit = getBlockDamagePerHit(block, heldItem);
		return (int) Math.ceil(hardness / damagePerHit);
	}
	
	/**
	 * Returns the block damage per hit for the given held item against the given block.
	 * @param block the target block
	 * @param heldItem the held item, or null for bare hands
	 * @return 3.0 for correct tool, 2.0 for wrong tool/weapon, 1.0 for everything else
	 */
	private float getBlockDamagePerHit(Block block, ItemInstance heldItem)
	{
		if (heldItem == null)
		{
			return BLOCK_DAMAGE_BARE_HANDS;
		}
		
		final ItemTemplate template = heldItem.getTemplate();
		final ItemType type = template.getType();
		
		if (type == ItemType.TOOL)
		{
			final Block.ToolType bestTool = block.getBestTool();
			if (bestTool != Block.ToolType.NONE && template.getToolType() == bestTool)
			{
				return BLOCK_DAMAGE_CORRECT_TOOL;
			}
			// Tool but wrong type.
			return BLOCK_DAMAGE_WRONG_TOOL;
		}
		
		if (type == ItemType.WEAPON)
		{
			return BLOCK_DAMAGE_WRONG_TOOL;
		}
		
		// Blocks, consumables, materials — same as bare hands.
		return BLOCK_DAMAGE_BARE_HANDS;
	}
	
	/**
	 * Returns the weapon durability cost per hit for the given held item against the given block.
	 * @param block the target block
	 * @param heldItem the held item, or null for bare hands
	 * @return 1 for correct tool, 2 for wrong tool/weapon, 0 for non-durability items
	 */
	private int getDurabilityCostPerHit(Block block, ItemInstance heldItem)
	{
		if (heldItem == null || !heldItem.hasDurability())
		{
			return 0;
		}
		
		final ItemTemplate template = heldItem.getTemplate();
		
		// Correct tool: 1 durability per hit.
		if (template.hasTool())
		{
			final Block.ToolType bestTool = block.getBestTool();
			if (bestTool != Block.ToolType.NONE && template.getToolType() == bestTool)
			{
				return DURABILITY_COST_CORRECT;
			}
		}
		
		// Wrong tool or weapon: 2 durability per hit.
		return DURABILITY_COST_WRONG;
	}
	
	/**
	 * Spawns a dropped item on the ground at the given block position.<br>
	 * Handles special drops (STONE → stone_shard, IRON_ORE → iron_nugget, etc.),<br>
	 * LEAVES 25% drop chance, and standard block-to-item drops.<br>
	 * If the DropManager is not set, falls back to adding directly to the inventory.
	 * @param block the block that was broken
	 * @param bx the world X of the broken block
	 * @param by the world Y of the broken block
	 * @param bz the world Z of the broken block
	 */
	private void dropBlockItem(Block block, int bx, int by, int bz)
	{
		// Leaves have a 25% chance to drop; 75% nothing.
		if (block == Block.LEAVES)
		{
			if (Rnd.nextFloat() >= 0.25f)
			{
				return; // No drop.
			}
		}
		
		final String dropId = ItemRegistry.getDropItemId(block);
		if (dropId == null)
		{
			return; // Block drops nothing.
		}
		
		final ItemTemplate dropItem = ItemRegistry.get(dropId);
		if (dropItem == null)
		{
			System.out.println("WARNING: No item registered for drop ID '" + dropId + "' from block " + block.name());
			return;
		}
		
		final ItemInstance stack = new ItemInstance(dropItem, 1);
		
		// Spawn as a world drop at the broken block position.
		if (_dropManager != null)
		{
			// Find ground level below the block position so drops don't float in the air.
			// Scans downward from the block's Y to find the first solid block surface.
			int groundY = by;
			for (int y = by - 1; y >= 0; y--)
			{
				if (_world.getBlock(bx, y, bz).isSolid())
				{
					groundY = y + 1; // Stand on top of the solid block.
					break;
				}
			}
			
			// Center of the block + slight random offset for visual spread.
			final float dropX = bx + 0.5f + (Rnd.nextFloat() - 0.5f) * 0.3f;
			final float dropZ = bz + 0.5f + (Rnd.nextFloat() - 0.5f) * 0.3f;
			_dropManager.spawnDrop(new Vector3f(dropX, groundY, dropZ), stack);
		}
		else
		{
			// Fallback: add directly to inventory if DropManager is not wired.
			final Inventory inventory = _playerController.getInventory();
			final boolean added = inventory.addItem(stack);
			if (added)
			{
				System.out.println("Picked up: " + dropItem.getDisplayName());
			}
			else
			{
				System.out.println("WARNING: Inventory full — could not pick up " + dropItem.getDisplayName());
			}
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
		_breakingWithItem = null;
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
	
	/** Neighbor offsets for tile entity support check (all 6 directions). */
	// @formatter:off
	private static final int[][] TE_NEIGHBOR_OFFSETS =
	{
		{ 0, 1, 0 },
		{ 0, -1, 0 },
		{ 0, 0, 1 },
		{ 0, 0, -1 },
		{ 1, 0, 0 },
		{ -1, 0, 0 }
	};
	// @formatter:on
	
	/**
	 * Checks all 6 neighbors of the broken block for tile entities whose support block<br>
	 * was at the broken position. Removes any that lost their support.<br>
	 * Handles special cases: campfire respawn point clearing, door dual-half destruction,<br>
	 * and block light removal for torches and campfires.
	 * @param brokenX the world X of the broken block
	 * @param brokenY the world Y of the broken block
	 * @param brokenZ the world Z of the broken block
	 */
	private void removeSupportedTileEntities(int brokenX, int brokenY, int brokenZ)
	{
		final TileEntityManager manager = _world.getTileEntityManager();
		if (manager == null)
		{
			return;
		}
		
		boolean needsRebuild = false;
		
		for (int[] offset : TE_NEIGHBOR_OFFSETS)
		{
			final int nx = brokenX + offset[0];
			final int ny = brokenY + offset[1];
			final int nz = brokenZ + offset[2];
			
			final TileEntity te = manager.get(nx, ny, nz);
			if (te == null)
			{
				continue;
			}
			
			// Check if the broken block was the support for this tile entity.
			final Vector3i support = te.getSupportBlockPosition();
			if (support.x != brokenX || support.y != brokenY || support.z != brokenZ)
			{
				continue;
			}
			
			// Door: destroy both halves.
			if (te instanceof DoorTileEntity)
			{
				final DoorTileEntity door = (DoorTileEntity) te;
				final Vector3i partnerPos = door.getPartnerPos();
				
				_world.clearPlayerPlaced(nx, ny, nz);
				_world.markPlayerRemoved(nx, ny, nz);
				if (partnerPos != null)
				{
					_world.clearPlayerPlaced(partnerPos.x, partnerPos.y, partnerPos.z);
					_world.markPlayerRemoved(partnerPos.x, partnerPos.y, partnerPos.z);
				}
				
				door.destroyBothHalves(_world);
				System.out.println("Door lost support at [" + nx + ", " + ny + ", " + nz + "] — both halves destroyed.");
				// destroyBothHalves already rebuilds, but mark for rebuild in case of batching.
				needsRebuild = true;
				continue;
			}
			
			// Campfire: clear respawn point if active.
			if (te instanceof CampfireTileEntity)
			{
				final CampfireTileEntity campfire = (CampfireTileEntity) te;
				if (campfire.isActivated())
				{
					_playerController.clearRespawnCampfire();
					MessageManager.show("Respawn point lost! You will respawn at world origin.");
				}
			}
			
			// Standard tile entity removal: call onRemoved, remove from manager, set block to AIR.
			te.onRemoved(_world);
			manager.remove(nx, ny, nz);
			_world.setBlockNoRebuild(nx, ny, nz, Block.AIR);
			_world.clearPlayerPlaced(nx, ny, nz);
			_world.markPlayerRemoved(nx, ny, nz);
			needsRebuild = true;
			
			System.out.println("Tile entity lost support at [" + nx + ", " + ny + ", " + nz + "] — removed.");
		}
		
		if (needsRebuild)
		{
			_world.rebuildDirtyRegionsImmediate();
		}
	}
	
	/**
	 * Applies a brief camera shake for hit feedback.
	 */
	private void applyShake()
	{
		_shakeTimer = SHAKE_DURATION;
		// @formatter:off
		_shakeOffset.set(
			(Rnd.nextFloat() - 0.5f) * 2.0f * SHAKE_MAGNITUDE,
			(Rnd.nextFloat() - 0.5f) * 2.0f * SHAKE_MAGNITUDE,
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
			final TileEntityManager manager = _world.getTileEntityManager();
			if (manager != null)
			{
				final TileEntity entity = manager.get(_targetX, _targetY, _targetZ);
				if (entity != null)
				{
					entity.onInteract(_playerController, _world);
					
					// Play a thud on door open/close and window flip.
					if (entity instanceof DoorTileEntity || entity instanceof WindowTileEntity)
					{
						_audioManager.playSfx(AudioManager.SFX_BLOCK_HIT);
					}
				}
				else
				{
					System.out.println("Interacted with " + targetBlock.name() + " at [" + _targetX + ", " + _targetY + ", " + _targetZ + "] — no tile entity registered.");
				}
			}
			return;
		}
		
		final Block selectedBlock = _playerController.getSelectedBlock();
		
		// Consumable use — right-click with a consumable item heals the player.
		final Inventory inventory = _playerController.getInventory();
		final ItemInstance selectedItem = inventory.getSelectedItem();
		if (selectedItem != null && selectedItem.getTemplate().getType() == ItemType.CONSUMABLE)
		{
			if (_playerController.getHealth() < _playerController.getMaxHealth())
			{
				final float healAmount = selectedItem.getTemplate().getHealAmount();
				_playerController.heal(healAmount);
				inventory.consumeSelectedItem();
				System.out.println("Used " + selectedItem.getTemplate().getDisplayName() + "! +" + String.format("%.1f", healAmount) + " HP (Health: " + String.format("%.1f", _playerController.getHealth()) + "/" + String.format("%.0f", _playerController.getMaxHealth()) + ")");
			}
			else
			{
				System.out.println("Health is already full.");
			}
			return;
		}
		
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
		
		// Face-snap placement — blocks that attach to surfaces (torches, doors, etc.)
		// bypass the 20% edge redirect and always use the raw entry face.
		if (selectedBlock.isFaceSnap() && _hasTarget && targetBlock.isSolid())
		{
			faceToOffset(_entryFace);
			placeX = _targetX + _faceOffsetX;
			placeY = _targetY + _faceOffsetY;
			placeZ = _targetZ + _faceOffsetZ;
			hasPlace = true;
		}
		
		// Window direct placement — aiming through a hole with empty space behind.
		// The raycast detected a valid framed air block. Place the window directly there.
		if (selectedBlock == Block.WINDOW && _windowDirectPlace)
		{
			placeX = _targetX;
			placeY = _targetY;
			placeZ = _targetZ;
			hasPlace = true;
			// Frame validation was already done in performRaycast via hasWindowFrame().
			// Facing and attachment will be determined from frame geometry during tile entity creation.
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
		
		// Campfire placement — must be on top of a solid block (floor only).
		if (selectedBlock == Block.CAMPFIRE)
		{
			if (!_world.getBlock(placeX, placeY - 1, placeZ).isSolid())
			{
				System.out.println("Cannot place CAMPFIRE here — needs a solid block below.");
				return;
			}
		}
		
		// Window placement — normal path (targeting a solid wall block).
		// Must target a solid block's side face (not top/bottom).
		// All frame edges (top, bottom, and left or right of the panel) must be solid
		// so the window sits in a proper wall opening, not dangling in open air.
		if (selectedBlock == Block.WINDOW && !_windowDirectPlace)
		{
			if (_entryFace == Face.TOP || _entryFace == Face.BOTTOM)
			{
				System.out.println("Cannot place WINDOW on top/bottom faces — windows are vertical only.");
				return;
			}
			
			if (!targetBlock.isSolid())
			{
				System.out.println("Cannot place WINDOW here — needs a solid wall to attach to.");
				return;
			}
			
			// Check that all frame edges around the panel are solid or window blocks.
			// NORTH/SOUTH facing (perpendicular to Z): frame = EAST, WEST, TOP, BOTTOM.
			// EAST/WEST facing (perpendicular to X): frame = NORTH, SOUTH, TOP, BOTTOM.
			final boolean topOk = isWindowFrameBlock(placeX, placeY + 1, placeZ);
			final boolean bottomOk = isWindowFrameBlock(placeX, placeY - 1, placeZ);
			final boolean leftOk;
			final boolean rightOk;
			
			if (_entryFace == Face.NORTH || _entryFace == Face.SOUTH)
			{
				// Panel spans X axis — check EAST and WEST.
				leftOk = isWindowFrameBlock(placeX - 1, placeY, placeZ);
				rightOk = isWindowFrameBlock(placeX + 1, placeY, placeZ);
			}
			else
			{
				// Panel spans Z axis — check NORTH and SOUTH.
				leftOk = isWindowFrameBlock(placeX, placeY, placeZ - 1);
				rightOk = isWindowFrameBlock(placeX, placeY, placeZ + 1);
			}
			
			// Fix: Require BOTH top and bottom to be solid, and at least ONE of left/right to be solid.
			// This allows windows to be placed in corners or next to other windows.
			if (!topOk || !bottomOk || (!leftOk && !rightOk))
			{
				System.out.println("Cannot place WINDOW here — needs solid blocks on frame edges.");
				return;
			}
		}
		
		// Door placement — must target a solid block face where two vertical AIR blocks exist.
		// Places DOOR_BOTTOM at placeY and DOOR_TOP at placeY + 1.
		// The block below the door must be solid for support.
		if (selectedBlock == Block.DOOR_BOTTOM)
		{
			// Determine the lower position for the door.
			int doorBottomY = placeY;
			
			// If targeting a top face, the door goes at y+1 and y+2 above the targeted block.
			if (_entryFace == Face.TOP)
			{
				doorBottomY = _targetY + 1;
			}
			
			// Check that the block below the door is solid (for support).
			final Block belowBlock = _world.getBlock(placeX, doorBottomY - 1, placeZ);
			if (!belowBlock.isSolid())
			{
				System.out.println("Cannot place DOOR here — needs a solid block below.");
				return;
			}
			
			// Check that both door positions are AIR (or replaceable decorations).
			final Block lowerBlock = _world.getBlock(placeX, doorBottomY, placeZ);
			final Block upperBlock = _world.getBlock(placeX, doorBottomY + 1, placeZ);
			if ((lowerBlock != Block.AIR && !lowerBlock.isDecoration()) || (upperBlock != Block.AIR && !upperBlock.isDecoration()))
			{
				System.out.println("Cannot place DOOR here — needs two vertical air blocks.");
				return;
			}
			
			// Check that both positions don't overlap the player.
			if (overlapsPlayer(placeX, doorBottomY, placeZ) || overlapsPlayer(placeX, doorBottomY + 1, placeZ))
			{
				System.out.println("Cannot place DOOR — overlaps player.");
				return;
			}
			
			// Override placeY to the computed bottom position.
			placeY = doorBottomY;
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
		
		// Place the block and create tile entity.
		// For tile entity blocks, we use a two-step approach:
		// 1. Set block data without rebuilding the mesh.
		// 2. Create and register the tile entity (with facing, light, etc.).
		// 3. Rebuild the mesh so the tile entity's facing is available during vertex building.
		// Without this order, the mesh builder would query the TileEntityManager before the
		// entity is registered and default to NORTH facing.
		if (selectedBlock.isTileEntity())
		{
			final TileEntityManager manager = _world.getTileEntityManager();
			if (manager != null)
			{
				// Step 1: Set block data without mesh rebuild.
				_world.setBlockNoRebuild(placeX, placeY, placeZ, selectedBlock);
				_world.markPlayerPlaced(placeX, placeY, placeZ);
				
				// Step 2: Create and register the tile entity.
				final Vector3i pos = new Vector3i(placeX, placeY, placeZ);
				TileEntity entity = null;
				
				switch (selectedBlock)
				{
					case CAMPFIRE:
					{
						entity = new CampfireTileEntity(pos);
						break;
					}
					case TORCH:
					{
						// Determine attachment face based on which block face the player clicked.
						final Face attachedFace = determineTorchAttachment(placeX, placeY, placeZ, _entryFace);
						entity = new TorchTileEntity(pos, attachedFace);
						break;
					}
					case CHEST:
					case CRAFTING_TABLE:
					case FURNACE:
					{
						final PlaceholderTileEntity placeholder = new PlaceholderTileEntity(pos, selectedBlock);
						placeholder.setFacing(getPlayerFacing());
						entity = placeholder;
						break;
					}
					case WINDOW:
					{
						// Determine facing and attachment from entry face (normal) or frame geometry (direct).
						final Facing windowFacing;
						final Face windowAttach;
						if (_windowDirectPlace)
						{
							windowFacing = getWindowFrameFacing(placeX, placeY, placeZ);
							windowAttach = facingToAttachFace(windowFacing);
						}
						else
						{
							windowFacing = entryFaceToFacing(_entryFace);
							windowAttach = oppositeFace(_entryFace);
						}
						final WindowTileEntity window = new WindowTileEntity(pos, windowAttach);
						window.setFacing(windowFacing);
						entity = window;
						break;
					}
					case DOOR_BOTTOM:
					{
						// Place both halves and create two linked tile entities.
						final int doorTopY = placeY + 1;
						final Vector3i bottomPos = pos;
						final Vector3i topPos = new Vector3i(placeX, doorTopY, placeZ);
						
						// Set the top block data.
						_world.setBlockNoRebuild(placeX, doorTopY, placeZ, Block.DOOR_TOP);
						_world.markPlayerPlaced(placeX, doorTopY, placeZ);
						
						// Attachment face and facing derived from player look direction.
						// If an adjacent door exists on the hinge side, mirror the facing
						// so both door knobs face each other (double-door effect).
						Facing doorFacing = getPlayerFacing();
						if (hasAdjacentDoor(placeX, placeY, placeZ, doorFacing))
						{
							doorFacing = mirrorFacing(doorFacing);
						}
						final Face doorAttach = facingToAttachFace(doorFacing);
						
						// Create linked tile entities.
						final DoorTileEntity bottomEntity = new DoorTileEntity(bottomPos, Block.DOOR_BOTTOM, doorAttach, topPos, true);
						bottomEntity.setFacing(doorFacing);
						bottomEntity.onPlaced(_world);
						manager.register(bottomEntity);
						
						final DoorTileEntity topEntity = new DoorTileEntity(topPos, Block.DOOR_TOP, doorAttach, bottomPos, false);
						topEntity.setFacing(doorFacing);
						topEntity.onPlaced(_world);
						manager.register(topEntity);
						
						// entity stays null — we already registered both halves directly.
						break;
					}
					default:
					{
						break;
					}
				}
				
				if (entity != null)
				{
					entity.onPlaced(_world);
					manager.register(entity);
				}
				
				// Step 3: Rebuild the mesh now that the tile entity is registered.
				_world.rebuildDirtyRegionsImmediate();
			}
			else
			{
				// Fallback if no manager (shouldn't happen in practice).
				_world.setBlockImmediate(placeX, placeY, placeZ, selectedBlock);
				_world.markPlayerPlaced(placeX, placeY, placeZ);
			}
		}
		else
		{
			// Non-tile-entity blocks: immediate placement with single-step rebuild.
			_world.setBlockImmediate(placeX, placeY, placeZ, selectedBlock);
			_world.markPlayerPlaced(placeX, placeY, placeZ);
		}
		
		// Consume one item from inventory for the placed block.
		inventory.consumeSelectedItem();
		
		System.out.println("Placed " + selectedBlock.name() + " at [" + placeX + ", " + placeY + ", " + placeZ + "]");
		_audioManager.playSfx(AudioManager.SFX_BLOCK_PLACE);
	}
	
	/**
	 * Calculates which cardinal direction the block's front face should point.<br>
	 * The front face points TOWARD the player so the player sees the front texture<br>
	 * (e.g. chest latch, furnace opening) when looking at the block they just placed.<br>
	 * <br>
	 * If the player looks east (+X), they see the WEST face of blocks in front of them,<br>
	 * so the block's front should face WEST (toward the player).
	 * @return the facing direction for the placed block's front face
	 */
	private Facing getPlayerFacing()
	{
		final Vector3f dir = _camera.getDirection();
		final float absX = Math.abs(dir.x);
		final float absZ = Math.abs(dir.z);
		
		if (absX > absZ)
		{
			// Player looks more along X axis.
			// Looking east (+X) → player sees WEST face → front faces WEST.
			return dir.x > 0 ? Facing.WEST : Facing.EAST;
		}
		else
		{
			// Player looks more along Z axis.
			// Looking north (+Z) → player sees SOUTH face → front faces SOUTH.
			return dir.z > 0 ? Facing.SOUTH : Facing.NORTH;
		}
	}
	
	/**
	 * Converts an entry face (the face of the target block the ray hit) to a Facing direction<br>
	 * for the placed block. The placed block faces the same direction as the entry face<br>
	 * so that its panel is perpendicular to the wall it is attached to.<br>
	 * Only handles horizontal faces (NORTH/SOUTH/EAST/WEST). TOP/BOTTOM default to NORTH.
	 * @param entryFace the entry face from the ray-AABB test
	 * @return the facing direction for the placed block
	 */
	private static Facing entryFaceToFacing(Face entryFace)
	{
		switch (entryFace)
		{
			case NORTH:
			{
				return Facing.NORTH;
			}
			case SOUTH:
			{
				return Facing.SOUTH;
			}
			case EAST:
			{
				return Facing.EAST;
			}
			case WEST:
			{
				return Facing.WEST;
			}
			default:
			{
				return Facing.NORTH;
			}
		}
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
	 * Determines the attachment face for a torch placed at the given position.<br>
	 * Prefers the face opposite to the entry face (the block the player actually clicked),<br>
	 * then falls back to floor, then to the first solid side neighbor.
	 * @param clickedFace the entry face from the ray-AABB test (face of the target block that was hit)
	 * @return the face of the adjacent solid block the torch attaches to
	 */
	private Face determineTorchAttachment(int x, int y, int z, Face clickedFace)
	{
		// Prefer the face the player actually clicked — the support block is opposite
		// to the entry face direction. E.g. clicking the EAST face of a block places
		// the torch one block to the east; the support block is to the WEST of the torch.
		final Face preferred = oppositeFace(clickedFace);
		if (isAttachableFace(x, y, z, preferred))
		{
			return preferred;
		}
		
		// Fall back to floor placement.
		if (_world.getBlock(x, y - 1, z).isSolid())
		{
			return Face.BOTTOM;
		}
		
		// Wall attachment — check sides.
		if (_world.getBlock(x + 1, y, z).isSolid())
		{
			return Face.EAST;
		}
		
		if (_world.getBlock(x - 1, y, z).isSolid())
		{
			return Face.WEST;
		}
		
		if (_world.getBlock(x, y, z + 1).isSolid())
		{
			return Face.NORTH;
		}
		
		if (_world.getBlock(x, y, z - 1).isSolid())
		{
			return Face.SOUTH;
		}
		
		// Fallback (shouldn't happen if canPlaceTorch passed).
		return Face.BOTTOM;
	}
	
	/**
	 * Returns true if the block in the given face direction from (x, y, z) is solid.<br>
	 * Used to validate a preferred attachment face.
	 */
	private boolean isAttachableFace(int x, int y, int z, Face face)
	{
		faceToOffset(face);
		return _world.getBlock(x + _faceOffsetX, y + _faceOffsetY, z + _faceOffsetZ).isSolid();
	}
	
	/**
	 * Returns the opposite face.
	 */
	private static Face oppositeFace(Face face)
	{
		switch (face)
		{
			case TOP:
			{
				return Face.BOTTOM;
			}
			case BOTTOM:
			{
				return Face.TOP;
			}
			case NORTH:
			{
				return Face.SOUTH;
			}
			case SOUTH:
			{
				return Face.NORTH;
			}
			case EAST:
			{
				return Face.WEST;
			}
			case WEST:
			{
				return Face.EAST;
			}
			default:
			{
				return Face.BOTTOM;
			}
		}
	}
	
	/**
	 * Returns true if the air block at the given position has solid blocks on all<br>
	 * frame edges (top, bottom, and one of the horizontal sides) for at least one<br>
	 * facing axis. Used by the raycast to detect valid window placement positions<br>
	 * when the player aims through a hole in a wall with empty space behind it.
	 */
	private boolean hasWindowFrame(int x, int y, int z)
	{
		// Top and bottom must always be solid.
		if (!isWindowFrameBlock(x, y + 1, z) || !isWindowFrameBlock(x, y - 1, z))
		{
			return false;
		}
		
		// Check NORTH/SOUTH facing (panel spans X axis): need EAST or WEST solid.
		if (isWindowFrameBlock(x + 1, y, z) || isWindowFrameBlock(x - 1, y, z))
		{
			return true;
		}
		
		// Check EAST/WEST facing (panel spans Z axis): need NORTH or SOUTH solid.
		if (isWindowFrameBlock(x, y, z + 1) || isWindowFrameBlock(x, y, z - 1))
		{
			return true;
		}
		
		return false;
	}
	
	/**
	 * Returns true if the block at the given position can serve as a window frame edge.<br>
	 * Solid blocks (dirt, stone, wood, glass) and existing WINDOW blocks qualify,<br>
	 * allowing windows to be placed side by side in a row.
	 */
	private boolean isWindowFrameBlock(int x, int y, int z)
	{
		final Block block = _world.getBlock(x, y, z);
		return block.isSolid() || block == Block.WINDOW;
	}
	
	/**
	 * Determines the window facing direction from the frame geometry at the given position.<br>
	 * If EAST + WEST neighbors are solid, the panel spans X → faces NORTH or SOUTH.<br>
	 * If NORTH + SOUTH neighbors are solid, the panel spans Z → faces EAST or WEST.<br>
	 * Uses the camera direction to pick which way the front faces (toward the player).
	 */
	private Facing getWindowFrameFacing(int x, int y, int z)
	{
		final boolean eastOk = isWindowFrameBlock(x + 1, y, z);
		final boolean westOk = isWindowFrameBlock(x - 1, y, z);
		
		if (eastOk && westOk)
		{
			// Panel perpendicular to Z axis.
			return _camera.getDirection().z > 0 ? Facing.SOUTH : Facing.NORTH;
		}
		
		// Panel perpendicular to X axis.
		return _camera.getDirection().x > 0 ? Facing.WEST : Facing.EAST;
	}
	
	/**
	 * Converts a window frame facing direction to the corresponding attachment face.<br>
	 * The attachment face is opposite to the facing direction.
	 */
	private static Face facingToAttachFace(Facing facing)
	{
		switch (facing)
		{
			case NORTH:
			{
				return Face.SOUTH;
			}
			case SOUTH:
			{
				return Face.NORTH;
			}
			case EAST:
			{
				return Face.WEST;
			}
			case WEST:
			{
				return Face.EAST;
			}
			default:
			{
				return Face.SOUTH;
			}
		}
	}
	
	/**
	 * Returns true if an existing DOOR_BOTTOM block exists on the hinge side<br>
	 * of the given position for the given facing. Used to detect double-door<br>
	 * placement so the new door's knob can be mirrored to face the existing door.
	 * @param x door bottom X
	 * @param y door bottom Y
	 * @param z door bottom Z
	 * @param facing the proposed facing for the new door
	 * @return true if an adjacent door exists on the hinge side
	 */
	private boolean hasAdjacentDoor(int x, int y, int z, Facing facing)
	{
		// Hinge side (right from front view) for each facing:
		// NORTH: +X, SOUTH: -X, EAST: -Z, WEST: +Z
		switch (facing)
		{
			case NORTH:
			{
				return _world.getBlock(x + 1, y, z).isDoor();
			}
			case SOUTH:
			{
				return _world.getBlock(x - 1, y, z).isDoor();
			}
			case EAST:
			{
				return _world.getBlock(x, y, z - 1).isDoor();
			}
			case WEST:
			{
				return _world.getBlock(x, y, z + 1).isDoor();
			}
			default:
			{
				return false;
			}
		}
	}
	
	/**
	 * Returns the opposite facing along the same axis.<br>
	 * Used to mirror a door's facing so the knob appears on the opposite side.
	 */
	private static Facing mirrorFacing(Facing facing)
	{
		switch (facing)
		{
			case NORTH:
			{
				return Facing.SOUTH;
			}
			case SOUTH:
			{
				return Facing.NORTH;
			}
			case EAST:
			{
				return Facing.WEST;
			}
			case WEST:
			{
				return Facing.EAST;
			}
			default:
			{
				return facing;
			}
		}
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
	 * Cycles to the next hotbar slot.
	 */
	private void nextBlock()
	{
		_playerController.getInventory().nextHotbar();
	}
	
	/**
	 * Cycles to the previous hotbar slot.
	 */
	private void previousBlock()
	{
		_playerController.getInventory().prevHotbar();
	}
	
	// ========================================================
	// Block Highlight.
	// ========================================================
	
	/**
	 * Updates the block highlight position and visibility.
	 */
	private void updateHighlight()
	{
		// Hide highlight if disabled in display settings.
		if (!_showHighlight)
		{
			if (_highlightVisible)
			{
				_highlightGeometry.setCullHint(Geometry.CullHint.Always);
				_highlightVisible = false;
			}
			return;
		}
		
		// Only show highlight when a placeable block is equipped.
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
				// Window direct placement: highlight the framed air block.
				if (_windowDirectPlace)
				{
					showX = _targetX;
					showY = _targetY;
					showZ = _targetZ;
				}
				else if (targetBlock.isSolid())
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
					if (_highlightVisible)
					{
						_highlightGeometry.setCullHint(Geometry.CullHint.Always);
						_highlightVisible = false;
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
					if (_highlightVisible)
					{
						_highlightGeometry.setCullHint(Geometry.CullHint.Always);
						_highlightVisible = false;
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
			
			// Position highlight.
			_highlightGeometry.setLocalTranslation(showX + 0.5f, showY + 0.5f, showZ + 0.5f);
			
			// Swap material based on breakability.
			// Window direct placement targets AIR (not breakable) but should use normal highlight.
			if (targetBlock.isBreakable() || _windowDirectPlace)
			{
				_highlightGeometry.setMaterial(_highlightMaterial);
			}
			else
			{
				_highlightGeometry.setMaterial(_highlightUnbreakableMaterial);
			}
			
			if (!_highlightVisible)
			{
				_highlightGeometry.setCullHint(Geometry.CullHint.Never);
				_highlightVisible = true;
			}
		}
		else
		{
			if (_highlightVisible)
			{
				_highlightGeometry.setCullHint(Geometry.CullHint.Always);
				_highlightVisible = false;
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
	 * Returns the overlay node containing highlight and crack geometries.<br>
	 * Must be attached to the scene by the owning state.
	 */
	public Node getOverlayNode()
	{
		return _overlayNode;
	}
	
	/**
	 * Returns the destruction effects node containing poof particles.<br>
	 * Must be attached to the scene by the owning state.
	 */
	public Node getDestructionEffectsNode()
	{
		return _destructionQueue.getEffectsNode();
	}
	
	/**
	 * Cleans up the destruction queue. Called during state teardown.
	 */
	public void cleanupDestructionQueue()
	{
		_destructionQueue.cleanup();
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
	 * Returns true if the player is actively breaking a block (hits > 0).
	 */
	public boolean isBreaking()
	{
		return _isBreaking && _hitsDelivered > 0;
	}
	
	/**
	 * Returns the number of hits delivered to the current target.
	 */
	public int getHitsDelivered()
	{
		return _hitsDelivered;
	}
	
	/**
	 * Returns the number of hits required to break the current target.
	 */
	public int getHitsRequired()
	{
		return _hitsRequired;
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
	 * Returns the currently selected placeable block, or null if no block is selected.
	 */
	public Block getSelectedBlock()
	{
		return _playerController.getSelectedBlock();
	}
	
	/**
	 * Returns true if the attack (left-click) is currently held down.
	 */
	public boolean isAttackHeld()
	{
		return _attackHeld;
	}
	
	/**
	 * Sets whether attack processing should be suppressed this frame.<br>
	 * Used by PlayingState to prevent block breaking when the combat system<br>
	 * detects an enemy in the player's crosshair.
	 * @param suppressed true to suppress attack, false to allow normal processing
	 */
	public void setAttackSuppressed(boolean suppressed)
	{
		_attackSuppressed = suppressed;
	}
	
	/**
	 * Sets whether the block highlight is shown (from display settings).
	 */
	public void setShowHighlight(boolean show)
	{
		_showHighlight = show;
	}
	
	/**
	 * Sets the particle manager for block break visual effects.
	 * @param particleManager the particle manager instance
	 */
	public void setParticleManager(ParticleManager particleManager)
	{
		_particleManager = particleManager;
	}
	
	/**
	 * Sets the viewmodel renderer for triggering swing animation on block hits.
	 * @param viewmodelRenderer the viewmodel renderer instance
	 */
	public void setViewmodelRenderer(ViewmodelRenderer viewmodelRenderer)
	{
		_viewmodelRenderer = viewmodelRenderer;
	}
	
	/**
	 * Sets the drop manager for spawning world drops when blocks are broken.
	 * @param dropManager the drop manager instance
	 */
	public void setDropManager(DropManager dropManager)
	{
		_dropManager = dropManager;
	}
}
