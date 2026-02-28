package simplecraft.player;

import com.jme3.math.Vector3f;

import simplecraft.world.World;

/**
 * Resolves player-world collision using axis-separated AABB sweeps.<br>
 * The player bounding box is 0.6 wide (±0.3 from center) and 1.8 tall.<br>
 * Position represents the bottom-center (feet level) of the player.<br>
 * <br>
 * Movement is resolved one axis at a time (X → Z → Y) to prevent corner clipping.<br>
 * Each axis move checks the resulting AABB against solid blocks and pushes back<br>
 * or triggers a step-up when a 1-block ledge is detected.<br>
 * <br>
 * Step-up occurs when the player walks into a 1-block-high wall while on the ground.<br>
 * The player is raised by one block instead of being stopped, provided there is<br>
 * enough headroom (two non-solid blocks above the obstacle).<br>
 * <br>
 * Vertical collision handles both ground snapping (landing) and ceiling bonks (jumping).<br>
 * Water detection checks blocks at feet and eye level for liquid blocks.<br>
 * <br>
 * When in water, normal gravity is replaced with buoyancy physics:<br>
 * reduced gravity, surface bobbing, swim-up/swim-down input, and velocity<br>
 * dampening on water entry to break falls.
 * @author Pantelis Andrianakis
 * @since February 27th 2026
 */
public class PlayerCollision
{
	/** Gravitational acceleration in blocks per second². */
	private static final float GRAVITY = 20f;
	
	/** Reduced gravity when submerged in water. Player sinks slowly. */
	private static final float WATER_GRAVITY = 5f;
	
	/** Maximum terminal velocity (negative = downward). */
	private static final float TERMINAL_VELOCITY = -50f;
	
	/** Maximum terminal velocity in water (much slower sinking). */
	private static final float WATER_TERMINAL_VELOCITY = -4f;
	
	/** Maximum time step to prevent tunneling on frame spikes. */
	private static final float MAX_TPF = 0.05f;
	
	/** Half-width of the player bounding box. Full width is 0.6 blocks. */
	private static final float HALF_WIDTH = 0.3f;
	
	/** Total height of the player bounding box in blocks. */
	private static final float PLAYER_HEIGHT = 1.8f;
	
	/**
	 * Small inset used when computing the upper bound of block ranges.<br>
	 * Prevents the AABB from claiming to occupy a block when the edge is<br>
	 * exactly on that block's boundary (e.g. edge at 6.0 should NOT include block 6).
	 */
	private static final float SKIN = 0.001f;
	
	/** Height offset for water check at body center. */
	private static final float WATER_BODY_OFFSET = 0.5f;
	
	/** Height offset for water check at eye level. */
	private static final float WATER_HEAD_OFFSET = 1.6f;
	
	/** Vertical swim speed when pressing swim-up or swim-down in water. */
	private static final float SWIM_VERTICAL_SPEED = 3.5f;
	
	/** Velocity multiplier applied on water entry to break falls. */
	private static final float WATER_ENTRY_DAMPEN = 0.3f;
	
	/** Distance from the water surface within which bobbing force is applied. */
	private static final float SURFACE_BOB_RANGE = 0.5f;
	
	/** Upward force applied when near the water surface to keep the player floating. */
	private static final float SURFACE_BOB_FORCE = 6f;
	
	/** Upward impulse when pressing swim-up at the water surface near a solid edge. */
	private static final float HOP_OUT_IMPULSE = 5.5f;
	
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
	
	/** Whether the player was in water last frame. Used to detect water entry. */
	private boolean _wasInWater;
	
	/** Reusable result object to avoid per-frame allocation. */
	private final CollisionResult _result = new CollisionResult();
	
	// ========================================================
	// Collision Resolution — Main Entry Point.
	// ========================================================
	
	/**
	 * Resolves full player collision for one frame using axis-separated sweeps.<br>
	 * Processes horizontal movement (X then Z) with step-up support, then applies<br>
	 * gravity (or water buoyancy) and resolves vertical collision.<br>
	 * Finally detects water at body and head level.<br>
	 * Modifies position and velocity in place.
	 * @param position the player's current feet position (modified in place)
	 * @param velocity the player's current velocity (modified in place)
	 * @param deltaX horizontal movement on the X axis for this frame
	 * @param deltaZ horizontal movement on the Z axis for this frame
	 * @param world the world for block lookups
	 * @param tpf time per frame in seconds
	 * @param swimUp true when the swim-up key (Space) is held
	 * @param swimDown true when the swim-down key (Shift) is held
	 * @return collision result with ground, water, and fall distance information
	 */
	public CollisionResult resolveCollision(Vector3f position, Vector3f velocity, float deltaX, float deltaZ, World world, float tpf, boolean swimUp, boolean swimDown)
	{
		// Reset result.
		_result._onGround = false;
		_result._inWater = false;
		_result._headSubmerged = false;
		_result._fallDistance = 0;
		
		// Clamp time step to prevent tunneling on frame spikes.
		tpf = Math.min(tpf, MAX_TPF);
		
		// --- Early water check at current position (before movement) ---
		// This determines whether water physics apply this frame.
		final boolean currentlyInWater = isPositionInWater(position, world);
		
		// --- Water entry: dampen velocity on first contact ---
		if (currentlyInWater && !_wasInWater)
		{
			velocity.y *= WATER_ENTRY_DAMPEN;
			System.out.println("Entered water");
		}
		
		// --- Horizontal X axis ---
		if (deltaX != 0)
		{
			position.x += deltaX;
			if (hasSolidCollision(position.x, position.y, position.z, world))
			{
				// Try step-up: only when the player was on ground last frame.
				boolean steppedUp = false;
				if (_wasOnGround)
				{
					final float steppedY = (float) Math.floor(position.y) + 1.0f;
					if (!hasSolidCollision(position.x, steppedY, position.z, world))
					{
						position.y = steppedY;
						steppedUp = true;
					}
				}
				
				if (!steppedUp)
				{
					pushBackX(position, deltaX, world);
				}
			}
		}
		
		// --- Horizontal Z axis ---
		if (deltaZ != 0)
		{
			position.z += deltaZ;
			if (hasSolidCollision(position.x, position.y, position.z, world))
			{
				// Try step-up: only when the player was on ground last frame.
				boolean steppedUp = false;
				if (_wasOnGround)
				{
					final float steppedY = (float) Math.floor(position.y) + 1.0f;
					if (!hasSolidCollision(position.x, steppedY, position.z, world))
					{
						position.y = steppedY;
						steppedUp = true;
					}
				}
				
				if (!steppedUp)
				{
					pushBackZ(position, deltaZ, world);
				}
			}
		}
		
		// --- Vertical: gravity / water physics and collision ---
		if (currentlyInWater)
		{
			resolveWaterVertical(position, velocity, world, tpf, swimUp, swimDown);
		}
		else
		{
			// Normal gravity.
			velocity.y -= GRAVITY * tpf;
			velocity.y = Math.max(velocity.y, TERMINAL_VELOCITY);
			position.y += velocity.y * tpf;
			resolveVertical(position, velocity, world);
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
		
		_wasInWater = _result._inWater;
		
		return _result;
	}
	
	// ========================================================
	// Water Vertical Resolution.
	// ========================================================
	
	/**
	 * Handles vertical movement while the player is in water.<br>
	 * Replaces normal gravity with buoyancy, handles swim-up/swim-down input,<br>
	 * and applies surface bobbing when the player is near the water-air boundary.
	 */
	private void resolveWaterVertical(Vector3f position, Vector3f velocity, World world, float tpf, boolean swimUp, boolean swimDown)
	{
		// Determine the water surface Y level.
		// Walk upward from feet to find the first non-liquid block — that block's
		// bottom face is the water surface.
		final int checkX = (int) Math.floor(position.x);
		final int checkZ = (int) Math.floor(position.z);
		final int feetBlockY = (int) Math.floor(position.y);
		float waterSurfaceY = feetBlockY + 1.0f; // Default: top of the block at feet.
		for (int y = feetBlockY; y < feetBlockY + 10; y++)
		{
			if (!world.getBlock(checkX, y, checkZ).isLiquid())
			{
				waterSurfaceY = y; // Bottom of the first non-water block = surface.
				break;
			}
		}
		
		// Distance from player feet to water surface (positive = below surface).
		final float depthBelowSurface = waterSurfaceY - position.y;
		
		if (swimUp)
		{
			// Check for hop-out: at the surface with a solid block adjacent at foot level.
			if (depthBelowSurface >= 0 && depthBelowSurface < SURFACE_BOB_RANGE && hasSolidNeighborAtFeet(position, world))
			{
				velocity.y = HOP_OUT_IMPULSE;
			}
			else
			{
				velocity.y = SWIM_VERTICAL_SPEED;
			}
		}
		else if (swimDown)
		{
			velocity.y = -SWIM_VERTICAL_SPEED;
		}
		else
		{
			// No swim input — apply water gravity (slow sinking) with surface bobbing.
			velocity.y -= WATER_GRAVITY * tpf;
			velocity.y = Math.max(velocity.y, WATER_TERMINAL_VELOCITY);
			
			// Surface bobbing: when near the surface, apply upward force to float.
			if (depthBelowSurface >= 0 && depthBelowSurface < SURFACE_BOB_RANGE)
			{
				// Strength increases the deeper below the surface the player is (within range).
				final float bobStrength = (SURFACE_BOB_RANGE - depthBelowSurface) / SURFACE_BOB_RANGE;
				velocity.y += SURFACE_BOB_FORCE * (1.0f - bobStrength) * tpf;
				
				// Dampen vertical velocity near the surface for gentle bobbing.
				velocity.y *= 0.85f;
			}
		}
		
		position.y += velocity.y * tpf;
		
		// Still need to resolve solid collisions (ground under water, ceilings in caves).
		resolveVertical(position, velocity, world);
	}
	
	// ========================================================
	// Water Position Check.
	// ========================================================
	
	/**
	 * Returns true if the player body center is in a liquid block at the given position.<br>
	 * Used for early water detection before movement is applied.
	 */
	private boolean isPositionInWater(Vector3f position, World world)
	{
		final int bx = (int) Math.floor(position.x);
		final int by = (int) Math.floor(position.y + WATER_BODY_OFFSET);
		final int bz = (int) Math.floor(position.z);
		return world.getBlock(bx, by, bz).isLiquid();
	}
	
	/**
	 * Returns true if there is a solid block adjacent to the player at foot level.<br>
	 * Checks all four cardinal neighbors at the block the player's feet occupy.<br>
	 * Used to detect whether the player can hop out of water onto a ledge.
	 */
	private boolean hasSolidNeighborAtFeet(Vector3f position, World world)
	{
		final int bx = (int) Math.floor(position.x);
		final int by = (int) Math.floor(position.y);
		final int bz = (int) Math.floor(position.z);
		
		return world.getBlock(bx + 1, by, bz).isSolid() || world.getBlock(bx - 1, by, bz).isSolid() || world.getBlock(bx, by, bz + 1).isSolid() || world.getBlock(bx, by, bz - 1).isSolid();
	}
	
	// ========================================================
	// AABB Overlap Test.
	// ========================================================
	
	/**
	 * Returns true if the player AABB at the given position overlaps any solid block.
	 */
	private boolean hasSolidCollision(float posX, float posY, float posZ, World world)
	{
		final int minBX = (int) Math.floor(posX - HALF_WIDTH);
		final int maxBX = (int) Math.floor(posX + HALF_WIDTH - SKIN);
		final int minBY = (int) Math.floor(posY);
		final int maxBY = (int) Math.floor(posY + PLAYER_HEIGHT - SKIN);
		final int minBZ = (int) Math.floor(posZ - HALF_WIDTH);
		final int maxBZ = (int) Math.floor(posZ + HALF_WIDTH - SKIN);
		
		for (int bx = minBX; bx <= maxBX; bx++)
		{
			for (int by = minBY; by <= maxBY; by++)
			{
				for (int bz = minBZ; bz <= maxBZ; bz++)
				{
					if (world.getBlock(bx, by, bz).isSolid())
					{
						return true;
					}
				}
			}
		}
		
		return false;
	}
	
	// ========================================================
	// Horizontal Push-Back.
	// ========================================================
	
	/**
	 * Pushes the player out of solid blocks along the X axis.<br>
	 * Scans for the first solid block in the direction of movement and snaps the<br>
	 * player edge to that block's face.
	 */
	private void pushBackX(Vector3f position, float deltaX, World world)
	{
		final int minBX = (int) Math.floor(position.x - HALF_WIDTH);
		final int maxBX = (int) Math.floor(position.x + HALF_WIDTH - SKIN);
		final int minBY = (int) Math.floor(position.y);
		final int maxBY = (int) Math.floor(position.y + PLAYER_HEIGHT - SKIN);
		final int minBZ = (int) Math.floor(position.z - HALF_WIDTH);
		final int maxBZ = (int) Math.floor(position.z + HALF_WIDTH - SKIN);
		
		if (deltaX > 0)
		{
			// Moving +X: scan from left to right, push player's right edge to block's left face.
			for (int bx = minBX; bx <= maxBX; bx++)
			{
				for (int by = minBY; by <= maxBY; by++)
				{
					for (int bz = minBZ; bz <= maxBZ; bz++)
					{
						if (world.getBlock(bx, by, bz).isSolid())
						{
							position.x = bx - HALF_WIDTH;
							return;
						}
					}
				}
			}
		}
		else
		{
			// Moving -X: scan from right to left, push player's left edge to block's right face.
			for (int bx = maxBX; bx >= minBX; bx--)
			{
				for (int by = minBY; by <= maxBY; by++)
				{
					for (int bz = minBZ; bz <= maxBZ; bz++)
					{
						if (world.getBlock(bx, by, bz).isSolid())
						{
							position.x = bx + 1 + HALF_WIDTH;
							return;
						}
					}
				}
			}
		}
	}
	
	/**
	 * Pushes the player out of solid blocks along the Z axis.<br>
	 * Scans for the first solid block in the direction of movement and snaps the<br>
	 * player edge to that block's face.
	 */
	private void pushBackZ(Vector3f position, float deltaZ, World world)
	{
		final int minBX = (int) Math.floor(position.x - HALF_WIDTH);
		final int maxBX = (int) Math.floor(position.x + HALF_WIDTH - SKIN);
		final int minBY = (int) Math.floor(position.y);
		final int maxBY = (int) Math.floor(position.y + PLAYER_HEIGHT - SKIN);
		final int minBZ = (int) Math.floor(position.z - HALF_WIDTH);
		final int maxBZ = (int) Math.floor(position.z + HALF_WIDTH - SKIN);
		
		if (deltaZ > 0)
		{
			// Moving +Z: push player's far edge to block's near face.
			for (int bz = minBZ; bz <= maxBZ; bz++)
			{
				for (int by = minBY; by <= maxBY; by++)
				{
					for (int bx = minBX; bx <= maxBX; bx++)
					{
						if (world.getBlock(bx, by, bz).isSolid())
						{
							position.z = bz - HALF_WIDTH;
							return;
						}
					}
				}
			}
		}
		else
		{
			// Moving -Z: push player's near edge to block's far face.
			for (int bz = maxBZ; bz >= minBZ; bz--)
			{
				for (int by = minBY; by <= maxBY; by++)
				{
					for (int bx = minBX; bx <= maxBX; bx++)
					{
						if (world.getBlock(bx, by, bz).isSolid())
						{
							position.z = bz + 1 + HALF_WIDTH;
							return;
						}
					}
				}
			}
		}
	}
	
	// ========================================================
	// Vertical Collision (Ground Snap + Ceiling Bonk).
	// ========================================================
	
	/**
	 * Resolves vertical collision after gravity has been applied.<br>
	 * When falling, finds the ground surface and snaps the player upward.<br>
	 * When rising, finds the ceiling and stops upward velocity.
	 */
	private void resolveVertical(Vector3f position, Vector3f velocity, World world)
	{
		final int minBX = (int) Math.floor(position.x - HALF_WIDTH);
		final int maxBX = (int) Math.floor(position.x + HALF_WIDTH - SKIN);
		final int minBY = (int) Math.floor(position.y);
		final int maxBY = (int) Math.floor(position.y + PLAYER_HEIGHT - SKIN);
		final int minBZ = (int) Math.floor(position.z - HALF_WIDTH);
		final int maxBZ = (int) Math.floor(position.z + HALF_WIDTH - SKIN);
		
		if (velocity.y <= 0)
		{
			// Falling or stationary — check for ground from bottom up.
			for (int by = minBY; by <= maxBY; by++)
			{
				for (int bx = minBX; bx <= maxBX; bx++)
				{
					for (int bz = minBZ; bz <= maxBZ; bz++)
					{
						if (world.getBlock(bx, by, bz).isSolid())
						{
							final float groundSurface = by + 1.0f;
							if (position.y < groundSurface)
							{
								position.y = groundSurface;
								velocity.y = 0;
							}
							_result._onGround = true;
							return;
						}
					}
				}
			}
		}
		else
		{
			// Rising — check for ceiling from top down.
			for (int by = maxBY; by >= minBY; by--)
			{
				for (int bx = minBX; bx <= maxBX; bx++)
				{
					for (int bz = minBZ; bz <= maxBZ; bz++)
					{
						if (world.getBlock(bx, by, bz).isSolid())
						{
							// Snap feet so the player's top is below this block.
							position.y = by - PLAYER_HEIGHT;
							velocity.y = 0;
							return;
						}
					}
				}
			}
		}
	}
}
