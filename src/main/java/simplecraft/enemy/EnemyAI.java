package simplecraft.enemy;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;

import simplecraft.world.Block;
import simplecraft.world.World;

/**
 * Finite-state AI for all enemy types.<br>
 * Land enemies (Zombie, Skeleton, Wolf, Spider, Slime) patrol terrain and chase the player on land.<br>
 * Aquatic enemies (Piranha) swim freely within water bodies and attack submerged players.<br>
 * <br>
 * States: {@code IDLE → WANDER → CHASE → ATTACK}.<br>
 * Land enemies avoid water. Piranhas never leave water.<br>
 * Each enemy stores its own AI state, timers, and target via fields on {@link Enemy}.
 * @author Pantelis Andrianakis
 * @since March 5th 2026
 */
public class EnemyAI
{
	// ------------------------------------------------------------------
	// AI states.
	// ------------------------------------------------------------------
	
	/** Possible AI states for an enemy. */
	public enum AIState
	{
		IDLE,
		WANDER,
		CHASE,
		ATTACK
	}
	
	// ------------------------------------------------------------------
	// Land AI constants.
	// ------------------------------------------------------------------
	
	/** Minimum idle duration before switching to WANDER (seconds). */
	private static final float IDLE_MIN_TIME = 2.0f;
	
	/** Maximum idle duration before switching to WANDER (seconds). */
	private static final float IDLE_MAX_TIME = 5.0f;
	
	/** Maximum radius for wander target selection (blocks). */
	private static final float WANDER_RADIUS = 8.0f;
	
	/** Wander movement uses half of the enemy's configured move speed. */
	private static final float WANDER_SPEED_FACTOR = 0.5f;
	
	/** Wander timeout — if not arrived after this many seconds, return to IDLE (seconds). */
	private static final float WANDER_TIMEOUT = 8.0f;
	
	/** Arrival threshold — distance to target at which the enemy considers it "reached" (blocks). */
	private static final float ARRIVAL_THRESHOLD = 0.5f;
	
	/** Chase leash multiplier — lose interest when player exceeds detectionRange × this factor. */
	private static final float CHASE_LEASH_MULTIPLIER = 1.5f;
	
	/** Small vertical offset to keep enemy model above the ground surface. */
	private static final float GROUND_OFFSET = 0.05f;
	
	/** Maximum step-up height for land pathfinding (blocks). */
	private static final int MAX_STEP_UP = 1;
	
	/** Maximum downward search range for ground detection (blocks below current feet). */
	private static final int GROUND_SEARCH_DOWN = 16;
	
	// ------------------------------------------------------------------
	// Aquatic AI constants (Piranha).
	// ------------------------------------------------------------------
	
	/** Piranha idle circle radius (blocks). */
	private static final float PIRANHA_CIRCLE_RADIUS = 2.0f;
	
	/** Piranha idle circle speed (radians per second). */
	private static final float PIRANHA_CIRCLE_SPEED = 1.5f;
	
	/** Piranha wander radius within a water body (blocks). */
	private static final float PIRANHA_WANDER_RADIUS = 6.0f;
	
	/** Piranha depth variation during wander (blocks). */
	private static final float PIRANHA_DEPTH_VARIATION = 2.0f;
	
	/** Piranha wander timeout before picking a new destination (seconds). */
	private static final float PIRANHA_WANDER_TIMEOUT = 5.0f;
	
	/** Shared quaternion for facing rotation. */
	private static final Quaternion TEMP_QUAT = new Quaternion();
	
	/**
	 * Private constructor — utility class with only static methods.
	 */
	private EnemyAI()
	{
	}
	
	// ------------------------------------------------------------------
	// Main update entry point.
	// ------------------------------------------------------------------
	
	/**
	 * Updates the AI for a single enemy. Called once per frame from {@link Enemy#update(float)}.<br>
	 * Delegates to land or aquatic behavior based on the enemy's aquatic flag.
	 * @param enemy the enemy to update
	 * @param playerPos the player's current world position
	 * @param playerInWater true if the player's feet are in water
	 * @param world the game world (for block queries and ground height)
	 * @param tpf time per frame in seconds
	 */
	public static void update(Enemy enemy, Vector3f playerPos, boolean playerInWater, World world, float tpf)
	{
		if (!enemy.isAlive())
		{
			enemy.setMoving(false);
			return;
		}
		
		if (enemy.isAquatic())
		{
			updateAquatic(enemy, playerPos, playerInWater, world, tpf);
		}
		else
		{
			updateLand(enemy, playerPos, playerInWater, world, tpf);
		}
	}
	
	// ==================================================================
	// Land Enemy AI (Zombie, Skeleton, Wolf, Spider, Slime, Player).
	// ==================================================================
	
	/**
	 * Runs the land enemy state machine: IDLE → WANDER → CHASE → ATTACK.
	 */
	private static void updateLand(Enemy enemy, Vector3f playerPos, boolean playerInWater, World world, float tpf)
	{
		// Advance state timer.
		enemy.setStateTimer(enemy.getStateTimer() + tpf);
		
		final float distToPlayer = horizontalDistance(enemy.getPosition(), playerPos);
		final float detectionRange = enemy.getDetectionRange();
		final float attackRange = enemy.getAttackRange();
		
		switch (enemy.getAIState())
		{
			case IDLE:
			{
				enemy.setMoving(false);
				
				// Transition: player detected → CHASE (only if player is NOT in water).
				if (!playerInWater && distToPlayer <= detectionRange)
				{
					enterChase(enemy);
					break;
				}
				
				// Transition: idle timer expired → WANDER.
				if (enemy.getStateTimer() >= enemy.getIdleDuration())
				{
					enterWander(enemy, world);
				}
				break;
			}
			case WANDER:
			{
				// Transition: player detected → CHASE (only if player is NOT in water).
				if (!playerInWater && distToPlayer <= detectionRange)
				{
					enterChase(enemy);
					break;
				}
				
				// Move toward wander target.
				final Vector3f target = enemy.getWanderTarget();
				if (target == null)
				{
					enterIdle(enemy);
					break;
				}
				
				final boolean moved = moveLandToward(enemy, target, enemy.getMoveSpeed() * WANDER_SPEED_FACTOR, world, tpf);
				enemy.setMoving(moved);
				
				// Transition: arrived at target → IDLE.
				if (horizontalDistance(enemy.getPosition(), target) < ARRIVAL_THRESHOLD)
				{
					enterIdle(enemy);
					break;
				}
				
				// Transition: timeout → IDLE.
				if (enemy.getStateTimer() >= WANDER_TIMEOUT)
				{
					enterIdle(enemy);
				}
				break;
			}
			case CHASE:
			{
				// If player is in water, stop at the edge and wait.
				if (playerInWater)
				{
					enemy.setMoving(false);
					// If player went too far, give up entirely.
					if (distToPlayer > detectionRange * CHASE_LEASH_MULTIPLIER)
					{
						enterIdle(enemy);
					}
					break;
				}
				
				// Transition: player out of leash range → IDLE.
				if (distToPlayer > detectionRange * CHASE_LEASH_MULTIPLIER)
				{
					enterIdle(enemy);
					break;
				}
				
				// Transition: within attack range → ATTACK.
				if (distToPlayer <= attackRange)
				{
					enterAttack(enemy);
					break;
				}
				
				// Move toward player at full speed.
				final boolean moved = moveLandToward(enemy, playerPos, enemy.getMoveSpeed(), world, tpf);
				enemy.setMoving(moved);
				break;
			}
			case ATTACK:
			{
				enemy.setMoving(false);
				
				// Face the player while attacking.
				faceTarget(enemy, playerPos);
				
				// If player moves out of attack range, resume chase.
				if (distToPlayer > attackRange * 1.2f)
				{
					enterChase(enemy);
					break;
				}
				
				// If player entered water, stop attacking.
				if (playerInWater)
				{
					enterIdle(enemy);
					break;
				}
				
				// Deal damage on cooldown.
				enemy.setAttackTimer(enemy.getAttackTimer() + tpf);
				if (enemy.getAttackTimer() >= enemy.getAttackCooldown())
				{
					enemy.setAttackTimer(0);
					// Damage is applied by the caller / damage system.
					// For now, log the attack.
				}
				break;
			}
		}
		
		// Per-frame ground snap — keeps enemies glued to terrain even when idle
		// or when terrain changes beneath them.
		snapToGround(enemy, world);
	}
	
	/**
	 * Snaps a land enemy's Y position to the terrain surface below them.<br>
	 * Prevents floating or sinking through mesh when standing still or after terrain changes.
	 */
	private static void snapToGround(Enemy enemy, World world)
	{
		final Vector3f pos = enemy.getPosition();
		final int bx = (int) Math.floor(pos.x);
		final int bz = (int) Math.floor(pos.z);
		final int currentFootY = (int) Math.floor(pos.y);
		
		final int groundY = findGroundY(world, bx, bz, currentFootY);
		if (groundY >= 0)
		{
			final float expectedY = groundY + GROUND_OFFSET;
			// Only snap if the difference is significant (avoids jitter from float precision).
			if (Math.abs(pos.y - expectedY) > 0.1f)
			{
				enemy.setPosition(new Vector3f(pos.x, expectedY, pos.z));
			}
		}
	}
	
	// ------------------------------------------------------------------
	// Land movement helper.
	// ------------------------------------------------------------------
	
	/**
	 * Moves a land enemy toward a target position on the XZ plane.<br>
	 * Snaps Y to terrain surface each frame and supports stepping up 1-block walls.<br>
	 * Avoids moving into water blocks.
	 * @return true if the enemy actually moved, false if blocked
	 */
	private static boolean moveLandToward(Enemy enemy, Vector3f target, float speed, World world, float tpf)
	{
		final Vector3f pos = enemy.getPosition();
		
		// Horizontal direction only.
		float dx = target.x - pos.x;
		float dz = target.z - pos.z;
		final float dist = FastMath.sqrt(dx * dx + dz * dz);
		
		if (dist < 0.01f)
		{
			return false;
		}
		
		// Normalize and scale by speed.
		dx /= dist;
		dz /= dist;
		final float step = speed * tpf;
		float newX = pos.x + dx * step;
		float newZ = pos.z + dz * step;
		
		// Find ground height at new position.
		final int bx = (int) Math.floor(newX);
		final int bz = (int) Math.floor(newZ);
		final int currentFootY = (int) Math.floor(pos.y);
		
		// Check if the destination is water — if so, don't move there.
		final int groundY = findGroundY(world, bx, bz, currentFootY);
		if (groundY < 0)
		{
			// No valid ground found — stay put.
			return false;
		}
		
		// Check if the block at foot level of the new ground is water.
		final Block footBlock = world.getBlock(bx, groundY, bz);
		if (footBlock.isLiquid())
		{
			// Water ahead — don't enter.
			return false;
		}
		
		// Check step-up: only allow climbing 1 block.
		final int heightDiff = groundY - currentFootY;
		if (heightDiff > MAX_STEP_UP)
		{
			// Wall too high — can't climb.
			return false;
		}
		
		// Check headroom (2 blocks above ground for humanoids, 1 for small enemies).
		final int headroom = (enemy.getType() == Enemy.EnemyType.SPIDER || enemy.getType() == Enemy.EnemyType.SLIME) ? 1 : 2;
		for (int h = 0; h < headroom; h++)
		{
			final Block above = world.getBlock(bx, groundY + h, bz);
			if (above.isSolid())
			{
				return false; // Blocked.
			}
		}
		
		// Apply movement.
		final float newY = groundY + GROUND_OFFSET;
		enemy.setPosition(new Vector3f(newX, newY, newZ));
		
		// Face movement direction.
		faceDirection(enemy, dx, dz);
		
		return true;
	}
	
	/**
	 * Finds the Y coordinate of the ground (top of highest solid block) at the given XZ,<br>
	 * searching from above the enemy's current foot level down to well below it.
	 * @return the Y of the ground surface (feet level), or -1 if no valid ground found
	 */
	private static int findGroundY(World world, int bx, int bz, int currentFootY)
	{
		// Search upward for step-up, then downward for drops and slopes.
		for (int y = currentFootY + MAX_STEP_UP; y >= currentFootY - GROUND_SEARCH_DOWN; y--)
		{
			if (y < 0)
			{
				break;
			}
			
			final Block block = world.getBlock(bx, y, bz);
			final Block above = world.getBlock(bx, y + 1, bz);
			
			// Ground = solid block with non-solid above (or liquid counts as blocking for land enemies).
			if (block.isSolid() && !above.isSolid())
			{
				return y + 1; // Feet stand on top of the solid block.
			}
		}
		
		return -1;
	}
	
	// ==================================================================
	// Aquatic Enemy AI (Piranha).
	// ==================================================================
	
	/**
	 * Runs the piranha state machine: IDLE (circle), WANDER (swim), CHASE, ATTACK.
	 */
	private static void updateAquatic(Enemy enemy, Vector3f playerPos, boolean playerInWater, World world, float tpf)
	{
		// Advance state timer.
		enemy.setStateTimer(enemy.getStateTimer() + tpf);
		
		final float distToPlayer = enemy.getPosition().distance(playerPos);
		final float detectionRange = enemy.getDetectionRange();
		final float attackRange = enemy.getAttackRange();
		
		switch (enemy.getAIState())
		{
			case IDLE:
			{
				enemy.setMoving(true); // Piranha always swims.
				
				// Swim in small circles at current depth.
				swimCircle(enemy, world, tpf);
				
				// Transition: player in water and in range → CHASE.
				if (playerInWater && distToPlayer <= detectionRange)
				{
					enterChase(enemy);
					break;
				}
				
				// Transition: after some time → WANDER (explore the water body).
				if (enemy.getStateTimer() >= enemy.getIdleDuration())
				{
					enterAquaticWander(enemy, world);
				}
				break;
			}
			case WANDER:
			{
				enemy.setMoving(true);
				
				// Transition: player in water and in range → CHASE.
				if (playerInWater && distToPlayer <= detectionRange)
				{
					enterChase(enemy);
					break;
				}
				
				// Swim toward wander target.
				final Vector3f target = enemy.getWanderTarget();
				if (target == null)
				{
					enterAquaticIdle(enemy);
					break;
				}
				
				moveAquaticToward(enemy, target, enemy.getMoveSpeed() * WANDER_SPEED_FACTOR, world, tpf);
				
				// Arrived → IDLE.
				if (enemy.getPosition().distance(target) < ARRIVAL_THRESHOLD)
				{
					enterAquaticIdle(enemy);
					break;
				}
				
				// Timeout → IDLE.
				if (enemy.getStateTimer() >= PIRANHA_WANDER_TIMEOUT)
				{
					enterAquaticIdle(enemy);
				}
				break;
			}
			case CHASE:
			{
				enemy.setMoving(true);
				
				// Player left water → return to wander.
				if (!playerInWater)
				{
					enterAquaticWander(enemy, world);
					break;
				}
				
				// Player too far → return to wander.
				if (distToPlayer > detectionRange * CHASE_LEASH_MULTIPLIER)
				{
					enterAquaticWander(enemy, world);
					break;
				}
				
				// Within attack range → ATTACK.
				if (distToPlayer <= attackRange)
				{
					enterAttack(enemy);
					break;
				}
				
				// Swim toward player at full speed.
				moveAquaticToward(enemy, playerPos, enemy.getMoveSpeed(), world, tpf);
				break;
			}
			case ATTACK:
			{
				enemy.setMoving(true); // Still swimming during attack.
				
				// Circle around the player while attacking.
				faceTarget(enemy, playerPos);
				
				// Player left water → stop.
				if (!playerInWater)
				{
					enterAquaticWander(enemy, world);
					break;
				}
				
				// Player moved away → CHASE.
				if (distToPlayer > attackRange * 1.5f)
				{
					enterChase(enemy);
					break;
				}
				
				// Deal damage on cooldown.
				enemy.setAttackTimer(enemy.getAttackTimer() + tpf);
				if (enemy.getAttackTimer() >= enemy.getAttackCooldown())
				{
					enemy.setAttackTimer(0);
					// Damage applied by caller / damage system.
				}
				break;
			}
		}
	}
	
	// ------------------------------------------------------------------
	// Aquatic movement helpers.
	// ------------------------------------------------------------------
	
	/**
	 * Swims the piranha in a small circle at its current position.<br>
	 * Uses a sine-wave path on XZ with the circle center as the anchor.
	 */
	private static void swimCircle(Enemy enemy, World world, float tpf)
	{
		final float time = enemy.getStateTimer();
		final Vector3f center = enemy.getCircleCenter();
		
		if (center == null)
		{
			// Initialize circle center to current position.
			enemy.setCircleCenter(new Vector3f(enemy.getPosition()));
			return;
		}
		
		final float angle = time * PIRANHA_CIRCLE_SPEED;
		float targetX = center.x + FastMath.cos(angle) * PIRANHA_CIRCLE_RADIUS;
		float targetZ = center.z + FastMath.sin(angle) * PIRANHA_CIRCLE_RADIUS;
		float targetY = center.y + FastMath.sin(time * 0.8f) * 0.3f; // Gentle depth bob.
		
		// Clamp to water.
		final int bx = (int) Math.floor(targetX);
		final int by = (int) Math.floor(targetY);
		final int bz = (int) Math.floor(targetZ);
		
		if (!world.getBlock(bx, by, bz).isLiquid())
		{
			// Out of water — reverse direction by staying at current pos.
			return;
		}
		
		final Vector3f pos = enemy.getPosition();
		final float dx = targetX - pos.x;
		final float dz = targetZ - pos.z;
		
		enemy.setPosition(new Vector3f(targetX, targetY, targetZ));
		
		if (dx * dx + dz * dz > 0.001f)
		{
			faceDirection(enemy, dx, dz);
		}
	}
	
	/**
	 * Moves an aquatic enemy toward a 3D target position, clamping to water blocks.
	 */
	private static void moveAquaticToward(Enemy enemy, Vector3f target, float speed, World world, float tpf)
	{
		final Vector3f pos = enemy.getPosition();
		float dx = target.x - pos.x;
		float dy = target.y - pos.y;
		float dz = target.z - pos.z;
		final float dist = FastMath.sqrt(dx * dx + dy * dy + dz * dz);
		
		if (dist < 0.01f)
		{
			return;
		}
		
		dx /= dist;
		dy /= dist;
		dz /= dist;
		
		final float step = Math.min(speed * tpf, dist);
		float newX = pos.x + dx * step;
		float newY = pos.y + dy * step;
		float newZ = pos.z + dz * step;
		
		// Clamp to water — if the next position is not water, don't move there.
		final int bx = (int) Math.floor(newX);
		final int by = (int) Math.floor(newY);
		final int bz = (int) Math.floor(newZ);
		
		if (!world.getBlock(bx, by, bz).isLiquid())
		{
			// Can't go there — try just XZ movement at current Y.
			final int byFallback = (int) Math.floor(pos.y);
			if (!world.getBlock(bx, byFallback, bz).isLiquid())
			{
				// Still blocked — stay put.
				return;
			}
			newY = pos.y;
		}
		
		enemy.setPosition(new Vector3f(newX, newY, newZ));
		
		// Face movement direction (XZ only).
		if (dx * dx + dz * dz > 0.001f)
		{
			faceDirection(enemy, dx, dz);
		}
	}
	
	// ==================================================================
	// State transition helpers.
	// ==================================================================
	
	/**
	 * Enters the IDLE state with a random idle duration.
	 */
	private static void enterIdle(Enemy enemy)
	{
		enemy.setAIState(AIState.IDLE);
		enemy.setStateTimer(0);
		enemy.setIdleDuration(IDLE_MIN_TIME + FastMath.nextRandomFloat() * (IDLE_MAX_TIME - IDLE_MIN_TIME));
		enemy.setMoving(false);
		enemy.setWanderTarget(null);
	}
	
	/**
	 * Enters the WANDER state, picking a random land target within range of the enemy's position.
	 */
	private static void enterWander(Enemy enemy, World world)
	{
		enemy.setAIState(AIState.WANDER);
		enemy.setStateTimer(0);
		
		final Vector3f pos = enemy.getPosition();
		
		// Try up to 10 times to find a valid land target.
		for (int attempt = 0; attempt < 10; attempt++)
		{
			final float angle = FastMath.nextRandomFloat() * FastMath.TWO_PI;
			final float radius = 2.0f + FastMath.nextRandomFloat() * (WANDER_RADIUS - 2.0f);
			final float tx = pos.x + FastMath.cos(angle) * radius;
			final float tz = pos.z + FastMath.sin(angle) * radius;
			final int bx = (int) Math.floor(tx);
			final int bz = (int) Math.floor(tz);
			final int currentFootY = (int) Math.floor(pos.y);
			
			final int groundY = findGroundY(world, bx, bz, currentFootY);
			if (groundY < 0)
			{
				continue;
			}
			
			// Make sure the target isn't in water.
			final Block footBlock = world.getBlock(bx, groundY, bz);
			if (footBlock.isLiquid())
			{
				continue;
			}
			
			enemy.setWanderTarget(new Vector3f(tx, groundY + GROUND_OFFSET, tz));
			return;
		}
		
		// Failed to find valid target — just idle.
		enterIdle(enemy);
	}
	
	/**
	 * Enters the CHASE state.
	 */
	private static void enterChase(Enemy enemy)
	{
		enemy.setAIState(AIState.CHASE);
		enemy.setStateTimer(0);
	}
	
	/**
	 * Enters the ATTACK state, resetting the attack cooldown timer.
	 */
	private static void enterAttack(Enemy enemy)
	{
		enemy.setAIState(AIState.ATTACK);
		enemy.setStateTimer(0);
		enemy.setAttackTimer(0);
		enemy.setMoving(false);
	}
	
	/**
	 * Enters the aquatic IDLE state (circling).
	 */
	private static void enterAquaticIdle(Enemy enemy)
	{
		enemy.setAIState(AIState.IDLE);
		enemy.setStateTimer(0);
		enemy.setIdleDuration(IDLE_MIN_TIME + FastMath.nextRandomFloat() * (IDLE_MAX_TIME - IDLE_MIN_TIME));
		enemy.setCircleCenter(new Vector3f(enemy.getPosition()));
		enemy.setMoving(true);
	}
	
	/**
	 * Enters the aquatic WANDER state, picking a random water target.
	 */
	private static void enterAquaticWander(Enemy enemy, World world)
	{
		enemy.setAIState(AIState.WANDER);
		enemy.setStateTimer(0);
		
		final Vector3f pos = enemy.getPosition();
		
		// Try to find a valid water position.
		for (int attempt = 0; attempt < 10; attempt++)
		{
			final float angle = FastMath.nextRandomFloat() * FastMath.TWO_PI;
			final float radius = 2.0f + FastMath.nextRandomFloat() * (PIRANHA_WANDER_RADIUS - 2.0f);
			final float tx = pos.x + FastMath.cos(angle) * radius;
			final float tz = pos.z + FastMath.sin(angle) * radius;
			final float ty = pos.y + (FastMath.nextRandomFloat() - 0.5f) * PIRANHA_DEPTH_VARIATION;
			
			final int bx = (int) Math.floor(tx);
			final int by = (int) Math.floor(ty);
			final int bz = (int) Math.floor(tz);
			
			if (world.getBlock(bx, by, bz).isLiquid())
			{
				enemy.setWanderTarget(new Vector3f(tx, ty, tz));
				return;
			}
		}
		
		// Failed — circle in place.
		enterAquaticIdle(enemy);
	}
	
	// ==================================================================
	// Facing helpers.
	// ==================================================================
	
	/**
	 * Rotates the enemy's root node to face a direction on the XZ plane.<br>
	 * jME3 models face -Z at identity rotation, so we negate the direction components to produce the correct yaw angle.
	 * @param enemy the enemy to rotate
	 * @param dx X component of the movement direction
	 * @param dz Z component of the movement direction
	 */
	private static void faceDirection(Enemy enemy, float dx, float dz)
	{
		final float angle = FastMath.atan2(-dx, -dz);
		TEMP_QUAT.fromAngleAxis(angle, Vector3f.UNIT_Y);
		enemy.getNode().setLocalRotation(TEMP_QUAT);
	}
	
	/**
	 * Rotates the enemy's root node to face a target position.
	 */
	private static void faceTarget(Enemy enemy, Vector3f target)
	{
		final Vector3f pos = enemy.getPosition();
		final float dx = target.x - pos.x;
		final float dz = target.z - pos.z;
		
		if (dx * dx + dz * dz > 0.001f)
		{
			faceDirection(enemy, dx, dz);
		}
	}
	
	// ==================================================================
	// Math helpers.
	// ==================================================================
	
	/**
	 * Returns the horizontal (XZ) distance between two positions, ignoring Y.
	 */
	private static float horizontalDistance(Vector3f a, Vector3f b)
	{
		final float dx = a.x - b.x;
		final float dz = a.z - b.z;
		return FastMath.sqrt(dx * dx + dz * dz);
	}
}
