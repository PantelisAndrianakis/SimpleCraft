package simplecraft.world;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.control.BillboardControl;
import com.jme3.scene.shape.Quad;

/**
 * Manages staggered block destruction with visual "poof" effects.<br>
 * <br>
 * When trees are felled or player-placed blocks lose support, the block data<br>
 * is set to AIR immediately (for game logic), but the visual mesh updates are<br>
 * staggered through this queue. Each block is revealed (mesh rebuilt) with a<br>
 * small delay, and a brief expanding/fading particle is spawned at its position.<br>
 * <br>
 * This creates a satisfying cascade effect instead of all blocks vanishing at once.
 * @author Pantelis Andrianakis
 * @since March 2nd 2026
 */
public class BlockDestructionQueue
{
	// ========================================================
	// Constants.
	// ========================================================
	
	/** Delay in seconds between each block in a tree felling cascade. */
	private static final float TREE_DELAY_PER_BLOCK = 0.04f;
	
	/** Delay in seconds between each block in a support collapse cascade. */
	private static final float SUPPORT_DELAY_PER_BLOCK = 0.06f;
	
	/** Duration of the poof effect in seconds. */
	private static final float POOF_DURATION = 0.35f;
	
	/** Starting scale of the poof quad. */
	private static final float POOF_START_SCALE = 0.3f;
	
	/** Ending scale of the poof quad. */
	private static final float POOF_END_SCALE = 1.2f;
	
	/** Starting alpha of the poof effect. */
	private static final float POOF_START_ALPHA = 0.55f;
	
	/** Poof quad size (half-extent). */
	private static final float POOF_SIZE = 0.5f;
	
	/** Color for wood block poofs. */
	private static final ColorRGBA POOF_COLOR_WOOD = new ColorRGBA(0.65f, 0.50f, 0.30f, 1.0f);
	
	/** Color for leaf block poofs. */
	private static final ColorRGBA POOF_COLOR_LEAVES = new ColorRGBA(0.30f, 0.55f, 0.20f, 1.0f);
	
	/** Default poof color (dirt, stone, etc.). */
	private static final ColorRGBA POOF_COLOR_DEFAULT = new ColorRGBA(0.70f, 0.70f, 0.70f, 1.0f);
	
	// ========================================================
	// Inner Classes.
	// ========================================================
	
	/**
	 * A single block pending visual destruction.
	 */
	private static class PendingBlock
	{
		final int _x;
		final int _y;
		final int _z;
		final Block _blockType;
		float _delay;
		
		PendingBlock(int x, int y, int z, Block blockType, float delay)
		{
			_x = x;
			_y = y;
			_z = z;
			_blockType = blockType;
			_delay = delay;
		}
	}
	
	/**
	 * An active poof effect being animated.
	 */
	private static class PoofEffect
	{
		final Geometry _geometry;
		final Material _material;
		final ColorRGBA _baseColor;
		float _elapsed;
		
		PoofEffect(Geometry geometry, Material material, ColorRGBA baseColor)
		{
			_geometry = geometry;
			_material = material;
			_baseColor = baseColor;
			_elapsed = 0;
		}
	}
	
	// ========================================================
	// Fields.
	// ========================================================
	
	private final World _world;
	private final Node _effectsNode = new Node("DestructionEffects");
	private final AssetManager _assetManager;
	
	/** Pending blocks waiting for their delay to elapse. */
	private final List<PendingBlock> _pendingBlocks = new ArrayList<>();
	
	/** Active poof effects being animated. */
	private final List<PoofEffect> _activeEffects = new ArrayList<>();
	
	/** Shared billboard quad mesh for poof effects. */
	private final Quad _poofQuad;
	
	/** Counter for unique geometry names. */
	private int _poofCounter;
	
	// ========================================================
	// Constructor.
	// ========================================================
	
	/**
	 * Creates a new destruction queue.
	 * @param world the game world for mesh rebuilds
	 * @param assetManager the jME3 asset manager for material creation
	 */
	public BlockDestructionQueue(World world, AssetManager assetManager)
	{
		_world = world;
		_assetManager = assetManager;
		_poofQuad = new Quad(POOF_SIZE * 2, POOF_SIZE * 2);
	}
	
	// ========================================================
	// Queuing.
	// ========================================================
	
	/**
	 * Queues a tree felling result for staggered visual destruction.
	 * @param result the felling result from {@link TreeFeller#fellTree}
	 */
	public void queueTreeFelling(TreeFeller.FellingResult result)
	{
		if (result.isEmpty())
		{
			return;
		}
		
		final List<int[]> blocks = result.getBlocks();
		final List<Block> types = result.getBlockTypes();
		
		for (int i = 0; i < blocks.size(); i++)
		{
			final int[] pos = blocks.get(i);
			_pendingBlocks.add(new PendingBlock(pos[0], pos[1], pos[2], types.get(i), i * TREE_DELAY_PER_BLOCK));
		}
	}
	
	/**
	 * Queues a block support collapse result for staggered visual destruction.
	 * @param result the collapse result from {@link BlockSupport#checkSupport}
	 */
	public void queueCollapseResult(BlockSupport.CollapseResult result)
	{
		if (result.isEmpty())
		{
			return;
		}
		
		final List<int[]> blocks = result.getBlocks();
		final List<Block> types = result.getBlockTypes();
		
		for (int i = 0; i < blocks.size(); i++)
		{
			final int[] pos = blocks.get(i);
			_pendingBlocks.add(new PendingBlock(pos[0], pos[1], pos[2], types.get(i), i * SUPPORT_DELAY_PER_BLOCK));
		}
	}
	
	// ========================================================
	// Update.
	// ========================================================
	
	/**
	 * Updates the destruction queue each frame.<br>
	 * Processes pending blocks whose delay has elapsed (marks regions dirty, rebuilds,<br>
	 * spawns poof effects). Updates and cleans up active poof effects.
	 * @param tpf time per frame in seconds
	 */
	public void update(float tpf)
	{
		// Process pending blocks.
		boolean anyProcessed = false;
		
		final Iterator<PendingBlock> pendingIter = _pendingBlocks.iterator();
		while (pendingIter.hasNext())
		{
			final PendingBlock pending = pendingIter.next();
			pending._delay -= tpf;
			
			if (pending._delay <= 0)
			{
				pendingIter.remove();
				
				// Mark the region dirty and track it for rebuild.
				_world.markRegionDirtyAt(pending._x, pending._y, pending._z);
				anyProcessed = true;
				
				// Spawn poof effect.
				spawnPoof(pending._x, pending._y, pending._z, pending._blockType);
			}
		}
		
		// Batch rebuild all regions that had blocks revealed this frame.
		if (anyProcessed)
		{
			_world.rebuildDirtyRegionsImmediate();
		}
		
		// Update active poof effects.
		final Iterator<PoofEffect> effectIter = _activeEffects.iterator();
		while (effectIter.hasNext())
		{
			final PoofEffect effect = effectIter.next();
			effect._elapsed += tpf;
			
			final float progress = effect._elapsed / POOF_DURATION;
			
			if (progress >= 1.0f)
			{
				// Effect finished — remove.
				_effectsNode.detachChild(effect._geometry);
				effectIter.remove();
				continue;
			}
			
			// Interpolate scale.
			final float scale = POOF_START_SCALE + (POOF_END_SCALE - POOF_START_SCALE) * progress;
			effect._geometry.setLocalScale(scale);
			
			// Interpolate alpha (fade out).
			final float alpha = POOF_START_ALPHA * (1.0f - progress);
			effect._material.setColor("Color", new ColorRGBA(effect._baseColor.r, effect._baseColor.g, effect._baseColor.b, alpha));
		}
	}
	
	// ========================================================
	// Poof Effect.
	// ========================================================
	
	/**
	 * Spawns a poof effect at the center of the given block position.
	 */
	private void spawnPoof(int x, int y, int z, Block blockType)
	{
		// Create material.
		final Material material = new Material(_assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
		final ColorRGBA baseColor = getPoofColor(blockType);
		material.setColor("Color", new ColorRGBA(baseColor.r, baseColor.g, baseColor.b, POOF_START_ALPHA));
		material.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
		material.getAdditionalRenderState().setDepthWrite(false);
		
		// Create geometry.
		final Geometry geometry = new Geometry("Poof_" + (_poofCounter++), _poofQuad);
		geometry.setMaterial(material);
		geometry.setQueueBucket(Bucket.Transparent);
		geometry.setLocalScale(POOF_START_SCALE);
		
		// Center the quad on the block position (quad origin is bottom-left, so offset by -POOF_SIZE).
		geometry.setLocalTranslation(x + 0.5f - POOF_SIZE, y + 0.5f - POOF_SIZE, z + 0.5f);
		
		// Make it always face the camera.
		geometry.addControl(new BillboardControl());
		
		_effectsNode.attachChild(geometry);
		_activeEffects.add(new PoofEffect(geometry, material, baseColor));
	}
	
	/**
	 * Returns the poof color based on block type.
	 */
	private static ColorRGBA getPoofColor(Block blockType)
	{
		if (blockType == Block.WOOD)
		{
			return POOF_COLOR_WOOD;
		}
		if (blockType == Block.LEAVES)
		{
			return POOF_COLOR_LEAVES;
		}
		return POOF_COLOR_DEFAULT;
	}
	
	// ========================================================
	// Accessors.
	// ========================================================
	
	/**
	 * Returns the effects node. Must be attached to the scene by the owning state.
	 */
	public Node getEffectsNode()
	{
		return _effectsNode;
	}
	
	/**
	 * Returns true if there are pending blocks or active effects.
	 */
	public boolean isActive()
	{
		return !_pendingBlocks.isEmpty() || !_activeEffects.isEmpty();
	}
	
	/**
	 * Clears all pending blocks and active effects. Called during cleanup.
	 */
	public void cleanup()
	{
		_pendingBlocks.clear();
		_activeEffects.clear();
		_effectsNode.detachAllChildren();
	}
}
