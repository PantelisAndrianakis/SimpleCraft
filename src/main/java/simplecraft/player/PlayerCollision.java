package simplecraft.player;

import com.jme3.math.Vector3f;

import simplecraft.world.Block;
import simplecraft.world.World;

/**
 * Resolves player-world collision for gravity, ground detection, and water detection.<br>
 * The player bounding box is 0.6 wide (±0.3 from center) and 1.8 tall.<br>
 * Position represents the bottom-center (feet level) of the player.<br>
 * <br>
 * Ground detection checks the block directly below the player's feet.<br>
 * When the player is at or below the top surface of a solid block,<br>
 * position is snapped upward and vertical velocity is zeroed.<br>
 * <br>
 * Water detection checks blocks at feet and eye level for liquid blocks,<br>
 * preparing flags for swimming mechanics in future sessions.
 * @author Pantelis Andrianakis
 * @since February 27th 2026
 */
public class PlayerCollision
{
	/** Gravitational acceleration in blocks per second². */
	private static final float GRAVITY = 20f;
	
	/** Small offset below feet to sample the block under the player. */
	private static final float GROUND_CHECK_OFFSET = 0.01f;
	
	/** Height offset for water check at body center. */
	private static final float WATER_BODY_OFFSET = 0.5f;
	
	/** Height offset for water check at eye level. */
	private static final float WATER_HEAD_OFFSET = 1.6f;
	
	// ========================================================
	// Collision Result.
	// ========================================================
	
	/**
	 * Result of a single collision resolution step.<br>
	 * Contains ground state, water state, and fall distance on landing.
	 */
	public static class CollisionResult
	{
		private boolean _onGround;
		private boolean _inWater;
		private boolean _headSubmerged;
		private float _fallDistance;
		
		public boolean isOnGround()
		{
			return _onGround;
		}
		
		public boolean isInWater()
		{
			return _inWater;
		}
		
		public boolean isHeadSubmerged()
		{
			return _headSubmerged;
		}
		
		/**
		 * Returns the fall distance recorded on landing this frame.<br>
		 * Zero if the player did not land this frame.
		 */
		public float getFallDistance()
		{
			return _fallDistance;
		}
	}
	
	// ========================================================
	// Fields.
	// ========================================================
	
	/** Y position when the player last left the ground. Used to calculate fall distance. */
	private float _fallStartY = Float.NaN;
	
	/** Whether the player was on the ground last frame. Used to detect takeoff and landing. */
	private boolean _wasOnGround = true;
	
	/** Reusable result object to avoid per-frame allocation. */
	private final CollisionResult _result = new CollisionResult();
	
	// ========================================================
	// Collision Resolution.
	// ========================================================
	
	/**
	 * Applies gravity, resolves ground collision, and detects water.<br>
	 * Modifies position and velocity in place.
	 * @param position the player's current feet position (modified in place)
	 * @param velocity the player's current velocity (modified in place)
	 * @param world the world for block lookups
	 * @param tpf time per frame in seconds
	 * @return collision result with ground, water, and fall distance information
	 */
	public CollisionResult resolveCollision(Vector3f position, Vector3f velocity, World world, float tpf)
	{
		// Reset result.
		_result._onGround = false;
		_result._inWater = false;
		_result._headSubmerged = false;
		_result._fallDistance = 0;
		
		// --- Apply gravity to vertical velocity ---
		velocity.y -= GRAVITY * tpf;
		
		// --- Move Y ---
		position.y += velocity.y * tpf;
		
		// --- Ground detection ---
		// Sample block coordinates below the player's feet.
		final int blockX = (int) Math.floor(position.x);
		final int blockZ = (int) Math.floor(position.z);
		final int blockBelowY = (int) Math.floor(position.y - GROUND_CHECK_OFFSET);
		
		// Check if the block below feet is solid.
		final Block blockBelow = world.getBlock(blockX, blockBelowY, blockZ);
		if (blockBelow.isSolid())
		{
			// Top surface of the solid block is blockBelowY + 1.
			final float groundSurface = blockBelowY + 1.0f;
			
			// Snap feet to ground surface if at or below it.
			if (position.y <= groundSurface)
			{
				position.y = groundSurface;
				velocity.y = 0;
				_result._onGround = true;
			}
		}
		
		// --- Fall distance tracking ---
		if (_wasOnGround && !_result._onGround)
		{
			// Just left the ground — record takeoff Y.
			_fallStartY = position.y;
		}
		else if (!_wasOnGround && _result._onGround)
		{
			// Just landed — calculate fall distance.
			if (!Float.isNaN(_fallStartY))
			{
				final float distance = _fallStartY - position.y;
				_result._fallDistance = Math.max(0, distance);
			}
			_fallStartY = Float.NaN;
		}
		
		_wasOnGround = _result._onGround;
		
		// --- Water detection ---
		final int waterCheckX = (int) Math.floor(position.x);
		final int waterCheckZ = (int) Math.floor(position.z);
		
		// Check at body center (feet + 0.5).
		final int waterBodyY = (int) Math.floor(position.y + WATER_BODY_OFFSET);
		_result._inWater = world.getBlock(waterCheckX, waterBodyY, waterCheckZ).isLiquid();
		
		// Check at eye level (feet + 1.6).
		final int waterHeadY = (int) Math.floor(position.y + WATER_HEAD_OFFSET);
		_result._headSubmerged = world.getBlock(waterCheckX, waterHeadY, waterCheckZ).isLiquid();
		
		return _result;
	}
}
