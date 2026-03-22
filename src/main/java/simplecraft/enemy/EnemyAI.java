package simplecraft.enemy;

import java.util.List;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;

import simplecraft.effects.ParticleManager;
import simplecraft.enemy.Enemy.EnemyType;
import simplecraft.util.Rnd;
import simplecraft.world.Block;
import simplecraft.world.World;
import simplecraft.world.boss.ArenaGenerator;

/**
 * Finite-state AI for all enemy types.<br>
 * Land enemies (Zombie, Skeleton, Wolf, Spider, Slime) patrol terrain and chase the player on land.<br>
 * Aquatic enemies (Piranha) swim freely within water bodies and attack submerged players.<br>
 * <br>
 * States: {@code IDLE -> WANDER -> CHASE -> ATTACK}.<br>
 * Land enemies avoid water. Piranhas never leave water.<br>
 * Each enemy stores its own AI state, timers and target via fields on {@link Enemy}.
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
		ATTACK,
		DRAGON
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
	
	/** Wander timeout - if not arrived after this many seconds, return to IDLE (seconds). */
	private static final float WANDER_TIMEOUT = 8.0f;
	
	/** Arrival threshold - distance to target at which the enemy considers it "reached" (blocks). */
	private static final float ARRIVAL_THRESHOLD = 0.5f;
	
	/** Chase leash multiplier - lose interest when player exceeds detectionRange × this factor. */
	private static final float CHASE_LEASH_MULTIPLIER = 1.5f;
	
	/** Small vertical offset to keep enemy model above the ground surface. */
	private static final float GROUND_OFFSET = 0.05f;
	
	/** Maximum step-up height for land pathfinding (blocks). */
	private static final int MAX_STEP_UP = 1;
	
	/** Maximum downward search range for ground detection (blocks below current feet). */
	private static final int GROUND_SEARCH_DOWN = 16;
	
	/**
	 * Maximum vertical distance (blocks) at which a land enemy can attack the player.<br>
	 * Prevents enemies at ground level from hitting a player standing on tall pillars.
	 */
	private static final float MAX_VERTICAL_ATTACK_REACH = 2.0f;
	
	// ------------------------------------------------------------------
	// Pathfinding constants.
	// ------------------------------------------------------------------
	
	/** How often to recalculate the A* path during CHASE (seconds). */
	private static final float PATH_RECALC_INTERVAL = 0.5f;
	
	/** Distance to a waypoint at which the enemy advances to the next one (blocks). */
	private static final float WAYPOINT_ARRIVAL_THRESHOLD = 0.5f;
	
	/**
	 * Minimum horizontal distance to player at which pathfinding is used.<br>
	 * Below this distance, direct movement is used instead since pathfinding<br>
	 * overhead is unnecessary at close range.
	 */
	private static final float PATH_MIN_DISTANCE = 2.5f;
	
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
	
	/** Particle manager for dragon block destruction effects. */
	private static ParticleManager _particleManager;
	
	/**
	 * Sets the particle manager used for dragon block destruction effects.
	 * @param particleManager the particle manager instance
	 */
	public static void setParticleManager(ParticleManager particleManager)
	{
		_particleManager = particleManager;
	}
	
	/**
	 * Private constructor - utility class with only static methods.
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
		if (!enemy.isAlive() || enemy.isDying())
		{
			enemy.setMoving(false);
			return;
		}
		
		if (enemy.isAquatic())
		{
			updateAquatic(enemy, playerPos, playerInWater, world, tpf);
		}
		else if (enemy.getType() == EnemyType.DRAGON)
		{
			updateBoss(enemy, playerPos, world, tpf);
			snapDragonToGround(enemy, world);
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
	 * Runs the land enemy state machine: IDLE -> WANDER -> CHASE -> ATTACK.
	 */
	private static void updateLand(Enemy enemy, Vector3f playerPos, boolean playerInWater, World world, float tpf)
	{
		// Advance state timer.
		enemy.setStateTimer(enemy.getStateTimer() + tpf);
		
		final float distToPlayer = horizontalDistance(enemy.getPosition(), playerPos);
		final float verticalDist = Math.abs(enemy.getPosition().y - playerPos.y);
		final float detectionRange = enemy.getDetectionRange();
		final float attackRange = enemy.getAttackRange();
		
		switch (enemy.getAIState())
		{
			case IDLE:
			{
				enemy.setMoving(false);
				
				// Transition: player detected -> CHASE (only if player is NOT in water).
				if (!playerInWater && distToPlayer <= detectionRange)
				{
					enterChase(enemy);
					break;
				}
				
				// Transition: idle timer expired -> WANDER.
				if (enemy.getStateTimer() >= enemy.getIdleDuration())
				{
					enterWander(enemy, world);
				}
				break;
			}
			case WANDER:
			{
				// Transition: player detected -> CHASE (only if player is NOT in water).
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
				
				// Transition: arrived at target -> IDLE.
				if (horizontalDistance(enemy.getPosition(), target) < ARRIVAL_THRESHOLD)
				{
					enterIdle(enemy);
					break;
				}
				
				// Transition: timeout -> IDLE.
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
				
				// Transition: player out of leash range -> IDLE.
				if (distToPlayer > detectionRange * CHASE_LEASH_MULTIPLIER)
				{
					enterIdle(enemy);
					break;
				}
				
				// Transition: within attack range -> ATTACK.
				// Only if the player is also within vertical reach (not standing on a pillar).
				if (distToPlayer <= attackRange && verticalDist <= MAX_VERTICAL_ATTACK_REACH)
				{
					enterAttack(enemy);
					break;
				}
				
				// --- Pathfinding-driven movement ---
				
				// At very close range, use direct movement (no pathfinding overhead).
				if (distToPlayer <= PATH_MIN_DISTANCE)
				{
					final boolean moved = moveLandToward(enemy, playerPos, enemy.getMoveSpeed(), world, tpf);
					enemy.setMoving(moved);
					break;
				}
				
				// Advance path recalculation timer.
				enemy.setPathTimer(enemy.getPathTimer() + tpf);
				
				// Recalculate path periodically or when no path exists.
				if (enemy.getPath() == null || enemy.getPathTimer() >= PATH_RECALC_INTERVAL)
				{
					final int headroom = getHeadroom(enemy);
					final int searchRange = (int) Math.ceil(detectionRange);
					final List<Vector3f> path = Pathfinder.findPath(world, enemy.getPosition(), playerPos, headroom, searchRange);
					
					if (!path.isEmpty())
					{
						enemy.setPath(path); // Also resets pathIndex to 0.
					}
					else
					{
						// No path found - clear stale path so we fall back to direct.
						enemy.clearPath();
					}
					
					enemy.setPathTimer(0);
				}
				
				// Follow the current path if one exists.
				final List<Vector3f> path = enemy.getPath();
				if (path != null && enemy.getPathIndex() < path.size())
				{
					final Vector3f waypoint = path.get(enemy.getPathIndex());
					final boolean moved = moveLandToward(enemy, waypoint, enemy.getMoveSpeed(), world, tpf);
					enemy.setMoving(moved);
					
					// Check if we arrived at the current waypoint.
					if (horizontalDistance(enemy.getPosition(), waypoint) < WAYPOINT_ARRIVAL_THRESHOLD)
					{
						enemy.setPathIndex(enemy.getPathIndex() + 1);
						
						// If path is exhausted, clear it so next recalc generates fresh one.
						if (enemy.getPathIndex() >= path.size())
						{
							enemy.clearPath();
						}
					}
					else if (!moved)
					{
						// Blocked on a waypoint - force immediate recalculation next frame.
						enemy.setPathTimer(PATH_RECALC_INTERVAL);
					}
				}
				else
				{
					// No valid path - fall back to direct movement (best effort).
					final boolean moved = moveLandToward(enemy, playerPos, enemy.getMoveSpeed(), world, tpf);
					enemy.setMoving(moved);
				}
				break;
			}
			case ATTACK:
			{
				enemy.setMoving(false);
				
				// Face the player while attacking.
				faceTarget(enemy, playerPos);
				
				// If player moves out of attack range or vertical reach, resume chase.
				if (distToPlayer > attackRange * 1.2f || verticalDist > MAX_VERTICAL_ATTACK_REACH)
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
			case DRAGON:
			{
				// Dragon uses updateBoss() - never reaches updateLand. Safety no-op.
				break;
			}
		}
		
		// Per-frame ground snap - keeps enemies glued to terrain even when idle
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
	
	/**
	 * Snaps the dragon's Y position downward to the terrain surface below it.<br>
	 * Only corrects downward (falling off edges, terrain changes beneath the dragon).<br>
	 * Upward movement is handled by the walk and charge code which set Y from ground<br>
	 * detection with headroom checks - this prevents the snap from climbing walls.
	 */
	private static void snapDragonToGround(Enemy enemy, World world)
	{
		final Vector3f pos = enemy.getPosition();
		final int bx = (int) Math.floor(pos.x);
		final int bz = (int) Math.floor(pos.z);
		final int currentFootY = (int) Math.floor(pos.y);
		
		final int groundY = findGroundY(world, bx, bz, currentFootY);
		if (groundY >= 0)
		{
			final float expectedY = groundY + GROUND_OFFSET;
			
			// Only snap downward - never upward. Walk/charge handle stepping up.
			if (pos.y > expectedY + 0.1f)
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
		
		// Check if the destination is water - if so, don't move there.
		final int groundY = findGroundY(world, bx, bz, currentFootY);
		if (groundY < 0)
		{
			// No valid ground found - stay put.
			return false;
		}
		
		// Check if the block at foot level of the new ground is water.
		final Block footBlock = world.getBlock(bx, groundY, bz);
		if (footBlock.isLiquid())
		{
			// Water ahead - don't enter.
			return false;
		}
		
		// Check step-up: only allow climbing 1 block.
		final int heightDiff = groundY - currentFootY;
		if (heightDiff > MAX_STEP_UP)
		{
			// Wall too high - can't climb.
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
				
				// Transition: player in water and in range -> CHASE.
				if (playerInWater && distToPlayer <= detectionRange)
				{
					enterChase(enemy);
					break;
				}
				
				// Transition: after some time -> WANDER (explore the water body).
				if (enemy.getStateTimer() >= enemy.getIdleDuration())
				{
					enterAquaticWander(enemy, world);
				}
				break;
			}
			case WANDER:
			{
				enemy.setMoving(true);
				
				// Transition: player in water and in range -> CHASE.
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
				
				// Arrived -> IDLE.
				if (enemy.getPosition().distance(target) < ARRIVAL_THRESHOLD)
				{
					enterAquaticIdle(enemy);
					break;
				}
				
				// Timeout -> IDLE.
				if (enemy.getStateTimer() >= PIRANHA_WANDER_TIMEOUT)
				{
					enterAquaticIdle(enemy);
				}
				break;
			}
			case CHASE:
			{
				enemy.setMoving(true);
				
				// Player left water -> return to wander.
				if (!playerInWater)
				{
					enterAquaticWander(enemy, world);
					break;
				}
				
				// Player too far -> return to wander.
				if (distToPlayer > detectionRange * CHASE_LEASH_MULTIPLIER)
				{
					enterAquaticWander(enemy, world);
					break;
				}
				
				// Within attack range -> ATTACK.
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
				
				// Player left water -> stop.
				if (!playerInWater)
				{
					enterAquaticWander(enemy, world);
					break;
				}
				
				// Player moved away -> CHASE.
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
			case DRAGON:
			{
				// Dragon is not aquatic - safety no-op.
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
			// Out of water - reverse direction by staying at current pos.
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
		
		// Clamp to water - if the next position is not water, don't move there.
		final int bx = (int) Math.floor(newX);
		final int by = (int) Math.floor(newY);
		final int bz = (int) Math.floor(newZ);
		
		if (!world.getBlock(bx, by, bz).isLiquid())
		{
			// Can't go there - try just XZ movement at current Y.
			final int byFallback = (int) Math.floor(pos.y);
			if (!world.getBlock(bx, byFallback, bz).isLiquid())
			{
				// Still blocked - stay put.
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
	// Boss AI (Dragon).
	// ==================================================================
	
	/** Dragon Phase 2 HP threshold (50%). */
	private static final float DRAGON_PHASE2_HP_RATIO = 0.5f;
	
	/** Dragon Phase 3 HP threshold (25%). */
	private static final float DRAGON_PHASE3_HP_RATIO = 0.25f;
	
	/** Dragon bite attack range (blocks). */
	private static final float DRAGON_BITE_RANGE = 3.0f;
	
	/** Dragon tail swipe range (blocks). */
	private static final float DRAGON_TAIL_RANGE = 4.5f;
	
	/** Charge speed (blocks per second). */
	private static final float DRAGON_CHARGE_SPEED = 10.0f;
	
	/** Charge distance (blocks). */
	private static final float DRAGON_CHARGE_DISTANCE = 6.0f;
	
	/** Charge telegraph duration (seconds). */
	private static final float DRAGON_TELEGRAPH_DURATION = 0.8f;
	
	/** Charge recovery duration (seconds). */
	private static final float DRAGON_CHARGE_RECOVERY = 1.0f;
	
	/** Roar animation duration at phase transition (seconds). */
	private static final float DRAGON_ROAR_DURATION = 1.0f;
	
	/** Arena bounds margin - dragon cannot get closer than this to arena edges (blocks). */
	private static final float DRAGON_ARENA_MARGIN = 2.0f;
	
	/**
	 * Boss AI for the Dragon. Completely separate from the normal land AI.<br>
	 * Three phases based on HP percentage with escalating aggression.
	 */
	private static void updateBoss(Enemy enemy, Vector3f playerPos, World world, float tpf)
	{
		// Advance state timer.
		enemy.setStateTimer(enemy.getStateTimer() + tpf);
		
		// --- Phase transitions ---
		final float hpRatio = enemy.getHealth() / enemy.getMaxHealth();
		final int currentPhase = enemy.getBossPhase();
		
		if (currentPhase == 1 && hpRatio <= DRAGON_PHASE2_HP_RATIO)
		{
			enterDragonPhase(enemy, 2);
			return; // Skip one frame for roar.
		}
		else if (currentPhase == 2 && hpRatio <= DRAGON_PHASE3_HP_RATIO)
		{
			enterDragonPhase(enemy, 3);
			return;
		}
		
		// --- Roar animation (phase transition) ---
		if (enemy.isRoaring())
		{
			enemy.setRoarTimer(enemy.getRoarTimer() + tpf);
			enemy.setMoving(false);
			faceTarget(enemy, playerPos);
			
			if (enemy.getRoarTimer() >= DRAGON_ROAR_DURATION)
			{
				enemy.setRoaring(false);
				enemy.setRoarTimer(0);
			}
			return;
		}
		
		// --- Charge telegraph ---
		if (enemy.isChargeTelegraph())
		{
			enemy.setTelegraphTimer(enemy.getTelegraphTimer() + tpf);
			enemy.setMoving(false);
			faceTarget(enemy, playerPos);
			
			if (enemy.getTelegraphTimer() >= DRAGON_TELEGRAPH_DURATION)
			{
				// Launch charge.
				enemy.setChargeTelegraph(false);
				enemy.setTelegraphTimer(0);
				enemy.setChargeActive(true);
				enemy.setChargeDistanceRemaining(DRAGON_CHARGE_DISTANCE);
				
				// Set charge direction toward player at moment of launch.
				final Vector3f pos = enemy.getPosition();
				float dx = playerPos.x - pos.x;
				float dz = playerPos.z - pos.z;
				final float dist = FastMath.sqrt(dx * dx + dz * dz);
				if (dist > 0.01f)
				{
					enemy.getChargeDirection().set(dx / dist, 0, dz / dist);
				}
				else
				{
					enemy.getChargeDirection().set(0, 0, -1);
				}
			}
			return;
		}
		
		// --- Active charge ---
		if (enemy.isChargeActive())
		{
			updateDragonCharge(enemy, world, tpf);
			return;
		}
		
		// --- Charge recovery / pillar stun ---
		if (enemy.isChargeRecovery())
		{
			enemy.setChargeRecoveryTimer(enemy.getChargeRecoveryTimer() - tpf);
			enemy.setMoving(false);
			
			if (enemy.getChargeRecoveryTimer() <= 0)
			{
				enemy.setChargeRecovery(false);
				enemy.setChargePillarStun(false);
			}
			return;
		}
		
		// --- Pillar stun ---
		if (enemy.isChargePillarStun())
		{
			enemy.setPillarStunTimer(enemy.getPillarStunTimer() - tpf);
			enemy.setMoving(false);
			
			if (enemy.getPillarStunTimer() <= 0)
			{
				enemy.setChargePillarStun(false);
			}
			return;
		}
		
		// --- Normal boss behavior ---
		final float distToPlayer = horizontalDistance(enemy.getPosition(), playerPos);
		
		// Tick cooldowns.
		enemy.setBiteCooldown(enemy.getBiteCooldown() + tpf);
		enemy.setTailSwipeCooldown(enemy.getTailSwipeCooldown() + tpf);
		enemy.setChargeCooldown(enemy.getChargeCooldown() + tpf);
		
		// Get phase-specific stats.
		final float moveSpeed;
		final float biteDamage;
		final float biteCooldownMax;
		final float tailRearArc; // Degrees of rear arc for tail swipe.
		final float chargeMinInterval;
		final float chargeMaxInterval;
		final boolean canCharge;
		
		switch (enemy.getBossPhase())
		{
			case 2:
			{
				moveSpeed = 3.5f;
				biteDamage = 8.0f;
				biteCooldownMax = 1.5f;
				tailRearArc = 120.0f;
				chargeMinInterval = 8.0f;
				chargeMaxInterval = 12.0f;
				canCharge = true;
				break;
			}
			case 3:
			{
				moveSpeed = 5.0f;
				biteDamage = 10.0f;
				biteCooldownMax = 1.0f;
				tailRearArc = 180.0f;
				chargeMinInterval = 5.0f;
				chargeMaxInterval = 8.0f;
				canCharge = true;
				break;
			}
			default: // Phase 1.
			{
				moveSpeed = 2.0f;
				biteDamage = 6.0f;
				biteCooldownMax = 2.0f;
				tailRearArc = 90.0f;
				chargeMinInterval = 0;
				chargeMaxInterval = 0;
				canCharge = false;
				break;
			}
		}
		
		// Update phase-specific stats on the enemy.
		enemy.setMoveSpeed(moveSpeed);
		enemy.setAttackDamage(biteDamage);
		enemy.setAttackCooldown(biteCooldownMax);
		
		// --- Charge initiation (Phase 2+) ---
		if (canCharge && enemy.getChargeCooldown() >= enemy.getChargeInterval() && distToPlayer > DRAGON_BITE_RANGE)
		{
			// Start telegraph.
			enemy.setChargeTelegraph(true);
			enemy.setTelegraphTimer(0);
			enemy.setChargeCooldown(0);
			enemy.setChargeInterval(chargeMinInterval + Rnd.nextFloat() * (chargeMaxInterval - chargeMinInterval));
			return;
		}
		
		// --- Tail swipe check ---
		// If player is within tail range and behind the dragon.
		if (distToPlayer <= DRAGON_TAIL_RANGE && enemy.getTailSwipeCooldown() >= 3.0f)
		{
			if (isPlayerBehind(enemy, playerPos, tailRearArc))
			{
				// Start tail swipe.
				enemy.setTailSwiping(true);
				enemy.setTailSwipeTimer(0);
				enemy.setTailSwipeCooldown(0);
				// Tail swipe damage is applied by CombatSystem when animation fires.
			}
		}
		
		// --- Bite attack ---
		if (distToPlayer <= DRAGON_BITE_RANGE && enemy.getBiteCooldown() >= biteCooldownMax && !enemy.isBiteActive())
		{
			enemy.setBiteActive(true);
			enemy.setBiteTimer(0);
			enemy.setBiteCooldown(0);
			enemy.setMoving(false);
			faceTarget(enemy, playerPos);
			
			// Bite damage applied by CombatSystem when animation fires.
			return;
		}
		
		// --- Walk toward player ---
		if (distToPlayer > DRAGON_BITE_RANGE * 0.8f && !enemy.isBiteActive() && !enemy.isTailSwiping())
		{
			// Direct movement toward player (no A* - arena is open).
			final Vector3f pos = enemy.getPosition();
			float dx = playerPos.x - pos.x;
			float dz = playerPos.z - pos.z;
			final float dist = FastMath.sqrt(dx * dx + dz * dz);
			
			if (dist > 0.1f)
			{
				dx /= dist;
				dz /= dist;
				
				final float step = moveSpeed * tpf;
				float newX = pos.x + dx * step;
				float newZ = pos.z + dz * step;
				
				// Clamp to arena bounds.
				newX = Math.max(DRAGON_ARENA_MARGIN, Math.min(ArenaGenerator.ARENA_SIZE_X - DRAGON_ARENA_MARGIN, newX));
				newZ = Math.max(DRAGON_ARENA_MARGIN, Math.min(ArenaGenerator.ARENA_SIZE_Z - DRAGON_ARENA_MARGIN, newZ));
				
				// Find the ground level at the destination position.
				final int bx = (int) Math.floor(newX);
				final int bz = (int) Math.floor(newZ);
				final int currentFootY = (int) Math.floor(pos.y);
				final int newGroundY = findGroundY(world, bx, bz, currentFootY);
				
				// Use new ground if valid, otherwise current foot level.
				final int checkFootY = (newGroundY >= 0) ? newGroundY : currentFootY;
				
				// Check headroom above the destination ground level (not current foot level).
				boolean blocked = false;
				for (int h = 0; h < 3; h++)
				{
					final Block ahead = world.getBlock(bx, checkFootY + h, bz);
					if (ahead != Block.AIR && !ahead.isLiquid())
					{
						blocked = true;
						break;
					}
				}
				
				if (!blocked)
				{
					final float newY = (newGroundY >= 0) ? (newGroundY + GROUND_OFFSET) : pos.y;
					enemy.setPosition(new Vector3f(newX, newY, newZ));
					faceDirection(enemy, dx, dz);
					enemy.setMoving(true);
				}
				else
				{
					// Destroy any blocks above ground level that block the dragon's path.
					if (dragonDestroyBlocks(world, bx, checkFootY, bz, dx, dz))
					{
						// Blocks were destroyed - pause for one frame to sell the impact,
						// then the dragon will move through on the next frame.
						faceDirection(enemy, dx, dz);
						enemy.setMoving(false);
					}
					else
					{
						// Indestructible obstacle (shouldn't happen in arena) - try to slide around.
						final float slideX = pos.x + dz * step; // Perpendicular.
						final float slideZ = pos.z - dx * step;
						final float clampedSlideX = Math.max(DRAGON_ARENA_MARGIN, Math.min(ArenaGenerator.ARENA_SIZE_X - DRAGON_ARENA_MARGIN, slideX));
						final float clampedSlideZ = Math.max(DRAGON_ARENA_MARGIN, Math.min(ArenaGenerator.ARENA_SIZE_Z - DRAGON_ARENA_MARGIN, slideZ));
						
						final int sbx = (int) Math.floor(clampedSlideX);
						final int sbz = (int) Math.floor(clampedSlideZ);
						final int slideGroundY = findGroundY(world, sbx, sbz, currentFootY);
						final int slideCheckY = (slideGroundY >= 0) ? slideGroundY : currentFootY;
						boolean slideBlocked = false;
						for (int h = 0; h < 3; h++)
						{
							if (world.getBlock(sbx, slideCheckY + h, sbz).isSolid())
							{
								slideBlocked = true;
								break;
							}
						}
						
						if (!slideBlocked)
						{
							final float slideY = (slideGroundY >= 0) ? (slideGroundY + GROUND_OFFSET) : pos.y;
							enemy.setPosition(new Vector3f(clampedSlideX, slideY, clampedSlideZ));
							faceDirection(enemy, dz, -dx);
							enemy.setMoving(true);
						}
						else
						{
							enemy.setMoving(false);
						}
					}
				}
			}
			else
			{
				enemy.setMoving(false);
			}
		}
		else if (!enemy.isBiteActive() && !enemy.isTailSwiping())
		{
			// Close to player - face them and stop.
			faceTarget(enemy, playerPos);
			enemy.setMoving(false);
		}
	}
	
	/**
	 * Updates dragon charge movement. Moves in a straight line at high speed.<br>
	 * If a solid block (pillar) is hit, the dragon stops and is stunned.
	 */
	private static void updateDragonCharge(Enemy enemy, World world, float tpf)
	{
		final Vector3f pos = enemy.getPosition();
		final Vector3f dir = enemy.getChargeDirection();
		final float step = DRAGON_CHARGE_SPEED * tpf;
		
		float newX = pos.x + dir.x * step;
		float newZ = pos.z + dir.z * step;
		
		// Clamp to arena bounds.
		newX = Math.max(DRAGON_ARENA_MARGIN, Math.min(ArenaGenerator.ARENA_SIZE_X - DRAGON_ARENA_MARGIN, newX));
		newZ = Math.max(DRAGON_ARENA_MARGIN, Math.min(ArenaGenerator.ARENA_SIZE_Z - DRAGON_ARENA_MARGIN, newZ));
		
		// Find ground level at destination and check headroom above it.
		final int bx = (int) Math.floor(newX);
		final int bz = (int) Math.floor(newZ);
		final int currentFootY = (int) Math.floor(pos.y);
		final int newGroundY = findGroundY(world, bx, bz, currentFootY);
		final int checkFootY = (newGroundY >= 0) ? newGroundY : currentFootY;
		
		boolean hitBlock = false;
		for (int h = 0; h < 3; h++)
		{
			final Block ahead = world.getBlock(bx, checkFootY + h, bz);
			if (ahead != Block.AIR && !ahead.isLiquid())
			{
				hitBlock = true;
				break;
			}
		}
		
		// Check arena boundary hit.
		final boolean hitBoundary = (newX <= DRAGON_ARENA_MARGIN || newX >= ArenaGenerator.ARENA_SIZE_X - DRAGON_ARENA_MARGIN || newZ <= DRAGON_ARENA_MARGIN || newZ >= ArenaGenerator.ARENA_SIZE_Z - DRAGON_ARENA_MARGIN);
		
		if (hitBlock)
		{
			// Destroy blocks in the charge path and continue charging through.
			dragonDestroyBlocks(world, bx, checkFootY, bz, dir.x, dir.z);
			// Charge continues - dragon plows through player-placed blocks.
		}
		
		if (hitBoundary)
		{
			// Hit arena wall - normal recovery.
			enemy.setChargeActive(false);
			enemy.setChargeRecovery(true);
			enemy.setChargeRecoveryTimer(DRAGON_CHARGE_RECOVERY);
			enemy.setMoving(false);
			return;
		}
		
		// Move along charge line.
		final float chargeY = (newGroundY >= 0) ? (newGroundY + GROUND_OFFSET) : pos.y;
		enemy.setPosition(new Vector3f(newX, chargeY, newZ));
		faceDirection(enemy, dir.x, dir.z);
		enemy.setMoving(true);
		
		enemy.setChargeDistanceRemaining(enemy.getChargeDistanceRemaining() - step);
		
		if (enemy.getChargeDistanceRemaining() <= 0)
		{
			// Charge complete - normal recovery.
			enemy.setChargeActive(false);
			enemy.setChargeRecovery(true);
			enemy.setChargeRecoveryTimer(DRAGON_CHARGE_RECOVERY);
			enemy.setMoving(false);
		}
	}
	
	/**
	 * Destroys all non-AIR blocks (solid, torches, doors, etc.) in three columns:<br>
	 * center, left and right (perpendicular to movement direction).<br>
	 * Each column spans the dragon's 3-block headroom plus 1 block above (4 total).<br>
	 * Uses batch block removal with a single mesh rebuild for efficiency.
	 * @param world the arena world
	 * @param bx block X coordinate of the center column
	 * @param footY the dragon's foot-level Y (ground surface)
	 * @param bz block Z coordinate of the center column
	 * @param dirX normalized X movement direction (for computing perpendicular)
	 * @param dirZ normalized Z movement direction (for computing perpendicular)
	 * @return true if any blocks were destroyed
	 */
	private static boolean dragonDestroyBlocks(World world, int bx, int footY, int bz, float dirX, float dirZ)
	{
		// Perpendicular direction for left/right expansion.
		// Left = (-dirZ, dirX), Right = (dirZ, -dirX) - rounded to block offsets.
		int perpX = Math.round(-dirZ);
		int perpZ = Math.round(dirX);
		
		// Safety: if perpendicular collapses to zero (perfectly diagonal rounding), default to X-axis.
		if (perpX == 0 && perpZ == 0)
		{
			perpX = 1;
		}
		
		boolean destroyed = false;
		
		// Destroy center, left and right columns (3-block headroom + 1 above each).
		for (int h = 0; h < 4; h++)
		{
			final int by = footY + h;
			
			// Center.
			destroyed |= dragonDestroySingleBlock(world, bx, by, bz);
			
			// Left (perpendicular).
			destroyed |= dragonDestroySingleBlock(world, bx + perpX, by, bz + perpZ);
			
			// Right (negative perpendicular).
			destroyed |= dragonDestroySingleBlock(world, bx - perpX, by, bz - perpZ);
		}
		
		if (destroyed)
		{
			world.rebuildDirtyRegionsImmediate();
			System.out.println("Dragon destroyed blocks at [" + bx + ", " + footY + ", " + bz + "]");
		}
		
		return destroyed;
	}
	
	/**
	 * Destroys a single block if it is not AIR, replacing it with AIR.<br>
	 * Catches all block types: solid, torches, doors, decorations, panels, etc.
	 * @return true if a block was destroyed
	 */
	private static boolean dragonDestroySingleBlock(World world, int x, int y, int z)
	{
		final Block block = world.getBlock(x, y, z);
		if (block != Block.AIR && !block.isLiquid())
		{
			world.setBlockNoRebuild(x, y, z, Block.AIR);
			
			// Spawn break particles at the destroyed block position.
			if (_particleManager != null)
			{
				_particleManager.spawnBlockBreak(new Vector3f(x, y, z), block);
			}
			
			return true;
		}
		
		return false;
	}
	
	/**
	 * Transitions the dragon to a new combat phase with a roar animation.
	 */
	private static void enterDragonPhase(Enemy enemy, int phase)
	{
		enemy.setBossPhase(phase);
		enemy.setRoaring(true);
		enemy.setRoarTimer(0);
		enemy.setMoving(false);
		
		// Reset charge interval for new phase.
		if (phase == 2)
		{
			enemy.setChargeInterval(8.0f + Rnd.nextFloat() * 4.0f);
		}
		else if (phase == 3)
		{
			enemy.setChargeInterval(5.0f + Rnd.nextFloat() * 3.0f);
		}
		
		System.out.println("Dragon entered Phase " + phase + "! HP: " + String.format("%.1f", enemy.getHealth()) + "/" + String.format("%.0f", enemy.getMaxHealth()));
	}
	
	/**
	 * Returns true if the player is behind the dragon (within the given rear arc in degrees).
	 */
	private static boolean isPlayerBehind(Enemy enemy, Vector3f playerPos, float rearArcDegrees)
	{
		final Vector3f pos = enemy.getPosition();
		
		// Get dragon's facing direction from its root node rotation.
		// The dragon faces -Z at identity, so the forward vector is derived from the node's rotation.
		final float[] angles = new float[3];
		enemy.getNode().getLocalRotation().toAngles(angles);
		final float yaw = angles[1]; // Y-axis rotation.
		
		// Dragon's forward direction in world space.
		final float forwardX = -FastMath.sin(yaw);
		final float forwardZ = -FastMath.cos(yaw);
		
		// Direction from dragon to player.
		float toPlayerX = playerPos.x - pos.x;
		float toPlayerZ = playerPos.z - pos.z;
		final float dist = FastMath.sqrt(toPlayerX * toPlayerX + toPlayerZ * toPlayerZ);
		if (dist < 0.01f)
		{
			return false;
		}
		
		toPlayerX /= dist;
		toPlayerZ /= dist;
		
		// Dot product: positive = in front, negative = behind.
		final float dot = forwardX * toPlayerX + forwardZ * toPlayerZ;
		
		// Convert rear arc from degrees to a cosine threshold.
		// 90° rear = dot < 0 (behind); 120° rear = dot < cos(60°) = 0.5; 180° = any direction.
		final float halfFrontArc = (180.0f - rearArcDegrees) * FastMath.DEG_TO_RAD;
		return dot < FastMath.cos(halfFrontArc);
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
		enemy.setIdleDuration(IDLE_MIN_TIME + Rnd.nextFloat() * (IDLE_MAX_TIME - IDLE_MIN_TIME));
		enemy.setMoving(false);
		enemy.setWanderTarget(null);
		enemy.clearPath();
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
			final float angle = Rnd.nextFloat() * FastMath.TWO_PI;
			final float radius = 2.0f + Rnd.nextFloat() * (WANDER_RADIUS - 2.0f);
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
		
		// Failed to find valid target - just idle.
		enterIdle(enemy);
	}
	
	/**
	 * Enters the CHASE state.
	 */
	private static void enterChase(Enemy enemy)
	{
		enemy.setAIState(AIState.CHASE);
		enemy.setStateTimer(0);
		enemy.clearPath(); // Force fresh path calculation.
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
		enemy.clearPath();
	}
	
	/**
	 * Enters the aquatic IDLE state (circling).
	 */
	private static void enterAquaticIdle(Enemy enemy)
	{
		enemy.setAIState(AIState.IDLE);
		enemy.setStateTimer(0);
		enemy.setIdleDuration(IDLE_MIN_TIME + Rnd.nextFloat() * (IDLE_MAX_TIME - IDLE_MIN_TIME));
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
			final float angle = Rnd.nextFloat() * FastMath.TWO_PI;
			final float radius = 2.0f + Rnd.nextFloat() * (PIRANHA_WANDER_RADIUS - 2.0f);
			final float tx = pos.x + FastMath.cos(angle) * radius;
			final float tz = pos.z + FastMath.sin(angle) * radius;
			final float ty = pos.y + (Rnd.nextFloat() - 0.5f) * PIRANHA_DEPTH_VARIATION;
			
			final int bx = (int) Math.floor(tx);
			final int by = (int) Math.floor(ty);
			final int bz = (int) Math.floor(tz);
			
			if (world.getBlock(bx, by, bz).isLiquid())
			{
				enemy.setWanderTarget(new Vector3f(tx, ty, tz));
				return;
			}
		}
		
		// Failed - circle in place.
		enterAquaticIdle(enemy);
	}
	
	// ==================================================================
	// Pathfinding helpers.
	// ==================================================================
	
	/**
	 * Returns the headroom (number of air blocks needed above ground) for the given enemy type.<br>
	 * Humanoid enemies (Zombie, Skeleton, Player) need 2 blocks; small enemies need 1.
	 * @param enemy the enemy to check
	 * @return headroom in blocks
	 */
	private static int getHeadroom(Enemy enemy)
	{
		switch (enemy.getType())
		{
			case SPIDER:
			case SLIME:
			{
				return 1;
			}
			case DRAGON:
			{
				return 3;
			}
			default:
			{
				return 2;
			}
		}
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
