package simplecraft.enemy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

import com.jme3.asset.AssetManager;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

import simplecraft.combat.CombatSystem;
import simplecraft.enemy.Enemy.EnemyType;
import simplecraft.player.PlayerController;
import simplecraft.world.Block;
import simplecraft.world.DayNightCycle;
import simplecraft.world.Region;
import simplecraft.world.World;

/**
 * Manages deterministic, region-based enemy spawning.<br>
 * <br>
 * Each region in the world has a fixed set of spawn points generated from the world seed.<br>
 * The XZ position and enemy type of each spawn point are determined purely by seed math,<br>
 * making them consistent across play sessions. The Y coordinate and validity are resolved<br>
 * lazily when the region first loads (requires terrain block queries).<br>
 * <br>
 * When the player walks within activation range of an unoccupied spawn point,<br>
 * an enemy materializes with a scale-up animation. When the player moves beyond<br>
 * {@link #DESPAWN_RANGE}, the enemy is silently removed and the point becomes available.<br>
 * After an enemy is killed, the spawn point enters a {@link #RESPAWN_COOLDOWN_DAY}<br>
 * (or shorter {@link #RESPAWN_COOLDOWN_NIGHT} at night) before it can produce a new enemy.<br>
 * <br>
 * At night, spawn rates increase: the activation range widens, the maximum active enemy<br>
 * cap is raised, and the spawn table shifts to favor skeletons and spiders.<br>
 * <br>
 * Spawn point data is cached per-region and discarded when the region unloads.
 * @author Pantelis Andrianakis
 * @since March 5th 2026
 */
public class SpawnSystem
{
	// ------------------------------------------------------------------
	// Spawn density constants.
	// ------------------------------------------------------------------
	
	/** Maximum spawn points generated per region. */
	private static final int MAX_SPAWNS_PER_REGION = 1;
	
	/**
	 * Global spawn rate multiplier. Scales the maximum spawn points per region.<br>
	 * 0.5 = 5% chance per region, 1.0 = default (10%), 5.0 = 50%, 10.0 = guaranteed 1, etc.<br>
	 * Changes take effect for newly tracked regions (already-loaded regions keep<br>
	 * their existing spawn points until they unload and reload).
	 */
	private static final float SPAWN_RATE = 1.0f;
	
	// ------------------------------------------------------------------
	// Distance constants.
	// ------------------------------------------------------------------
	
	/**
	 * Minimum distance before a spawn point can activate (blocks).<br>
	 * Prevents enemies from materializing in the player's immediate view.
	 */
	private static final float ACTIVATION_RANGE_MIN = 32.0f;
	
	/** Maximum distance at which an unoccupied spawn point activates during the day (blocks). */
	private static final float ACTIVATION_RANGE_MAX_DAY = 64.0f;
	
	/** Maximum distance at which an unoccupied spawn point activates at night (blocks). */
	private static final float ACTIVATION_RANGE_MAX_NIGHT = 80.0f;
	
	/** Distance at which an active enemy is despawned and its point freed (blocks). */
	private static final float DESPAWN_RANGE = 64.0f;
	
	/** Radius around the player's world spawn point where no enemies can appear (blocks). */
	private static final float SAFE_ZONE_RADIUS = 24.0f;
	
	/** Squared minimum activation range. */
	private static final float ACTIVATION_RANGE_MIN_SQ = ACTIVATION_RANGE_MIN * ACTIVATION_RANGE_MIN;
	
	/** Squared maximum activation range (day). */
	private static final float ACTIVATION_RANGE_MAX_DAY_SQ = ACTIVATION_RANGE_MAX_DAY * ACTIVATION_RANGE_MAX_DAY;
	
	/** Squared maximum activation range (night). */
	private static final float ACTIVATION_RANGE_MAX_NIGHT_SQ = ACTIVATION_RANGE_MAX_NIGHT * ACTIVATION_RANGE_MAX_NIGHT;
	
	/** Squared despawn range. */
	private static final float DESPAWN_RANGE_SQ = DESPAWN_RANGE * DESPAWN_RANGE;
	
	/** Squared safe zone radius. */
	private static final float SAFE_ZONE_RADIUS_SQ = SAFE_ZONE_RADIUS * SAFE_ZONE_RADIUS;
	
	// ------------------------------------------------------------------
	// Timing constants.
	// ------------------------------------------------------------------
	
	/** Cooldown before a spawn point can produce a new enemy after death during the day (seconds). */
	private static final float RESPAWN_COOLDOWN_DAY = 120.0f;
	
	/** Shorter cooldown at night for faster respawning (seconds). */
	private static final float RESPAWN_COOLDOWN_NIGHT = 45.0f;
	
	/** Time dead enemies linger before removal (seconds). */
	private static final float DEATH_LINGER_TIME = 2.0f;
	
	/**
	 * Delay after a spawn point is resolved before it can activate (seconds).<br>
	 * Prevents enemies from popping in when a region loads while the player<br>
	 * is already within activation range (e.g. walking into a new area).
	 */
	private static final float ACTIVATION_DELAY = 3.0f;
	
	/** Maximum number of enemies active at once during the day (performance cap). */
	private static final int MAX_ACTIVE_ENEMIES_DAY = 100;
	
	/** Maximum number of enemies active at once at night (raised cap). */
	private static final int MAX_ACTIVE_ENEMIES_NIGHT = 140;
	
	// ------------------------------------------------------------------
	// Spawn animation constants.
	// ------------------------------------------------------------------
	
	/** Duration of the spawn-in scale animation (seconds). */
	private static final float SPAWN_ANIM_DURATION = 0.6f;
	
	/** Peak overshoot scale during the bounce phase. */
	private static final float SPAWN_OVERSHOOT = 1.15f;
	
	/** Fraction of animation used for scale-up (0 → overshoot). Rest is settle (overshoot → 1.0). */
	private static final float SPAWN_RISE_FRACTION = 0.7f;
	
	// ------------------------------------------------------------------
	// Terrain constants.
	// ------------------------------------------------------------------
	
	/** Small vertical offset to prevent ground clipping. */
	private static final float GROUND_OFFSET = 0.05f;
	
	/** Horizontal radius (blocks) around a spawn point to check for player activity. */
	private static final int PLAYER_ACTIVITY_CHECK_RADIUS = 2;
	
	/** Vertical range above spawn point to check for player activity. */
	private static final int PLAYER_ACTIVITY_CHECK_HEIGHT = 3;
	
	/**
	 * Maximum BFS radius for the enclosure check (blocks).<br>
	 * If a ground-level flood fill from the spawn point cannot escape this radius<br>
	 * due to player-placed walls (solid or flat panel), the point is considered<br>
	 * enclosed (e.g. inside a house) and enemies will not spawn there.
	 */
	private static final int ENCLOSURE_CHECK_RADIUS = 8;
	
	/** Cardinal direction offsets for the enclosure BFS. */
	// @formatter:off
	private static final int[][] CARDINAL_DIRS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
	// @formatter:on
	
	// ------------------------------------------------------------------
	// Spawn tables.
	// ------------------------------------------------------------------
	
	/**
	 * Weighted spawn table for land enemy types during the day.<br>
	 * Piranhas are assigned automatically when a spawn point lands on water.
	 */
	// @formatter:off
	private static final EnemyType[] LAND_SPAWN_TABLE_DAY =
	{
		EnemyType.WOLF,
		EnemyType.SPIDER,
		EnemyType.SPIDER,
		EnemyType.SLIME,
		EnemyType.SLIME,
		EnemyType.SLIME,
		EnemyType.ZOMBIE,
		EnemyType.SKELETON,
	};
	// @formatter:on
	
	/**
	 * Weighted spawn table for land enemy types at night.<br>
	 * Heavier weighting toward skeletons and spiders for a more threatening nighttime.
	 */
	// @formatter:off
	private static final EnemyType[] LAND_SPAWN_TABLE_NIGHT =
	{
		EnemyType.WOLF,
		EnemyType.SPIDER,
		EnemyType.SPIDER,
		EnemyType.SPIDER,
		EnemyType.SLIME,
		EnemyType.ZOMBIE,
		EnemyType.ZOMBIE,
		EnemyType.ZOMBIE,
		EnemyType.SKELETON,
		EnemyType.SKELETON,
		EnemyType.SKELETON,
	};
	// @formatter:on
	
	// ------------------------------------------------------------------
	// Seed multipliers for deterministic RNG (large primes).
	// ------------------------------------------------------------------
	
	private static final long SEED_MUL_A = 341873128712L;
	private static final long SEED_MUL_B = 132897987541L;
	
	// ------------------------------------------------------------------
	// Inner class: SpawnPoint.
	// ------------------------------------------------------------------
	
	/**
	 * A fixed spawn location within a region.<br>
	 * XZ and enemy type are determined by the world seed at generation time.<br>
	 * Y and validity are resolved lazily when the region first loads.
	 */
	private static class SpawnPoint
	{
		/** World X coordinate (seed-determined). */
		final int worldX;
		
		/** World Z coordinate (seed-determined). */
		final int worldZ;
		
		/** Pre-selected enemy type for land (may become PIRANHA if water is found). */
		final EnemyType landType;
		
		/** Resolved world Y coordinate (feet level). Set during resolution. */
		int worldY;
		
		/** Actual enemy type after terrain check (may differ from landType for water). */
		EnemyType resolvedType;
		
		/** Whether this spawn point has been resolved against terrain. */
		boolean resolved;
		
		/** Whether this spawn point is valid (has walkable ground or water). */
		boolean valid;
		
		/** Whether the resolved position is in water (piranha). */
		boolean aquatic;
		
		/** Currently active enemy at this point, or null. */
		Enemy activeEnemy;
		
		/** Respawn cooldown timer (counts down to 0 after death). */
		float respawnTimer;
		
		/** Whether the death drop has been processed for the current enemy. */
		boolean deathProcessed;
		
		/**
		 * Activation delay timer (counts down to 0 after resolution).<br>
		 * Prevents enemies from popping in when a region loads while the player is nearby.
		 */
		float activationDelay;
		
		SpawnPoint(int worldX, int worldZ, EnemyType landType)
		{
			this.worldX = worldX;
			this.worldZ = worldZ;
			this.landType = landType;
		}
	}
	
	// ------------------------------------------------------------------
	// Fields.
	// ------------------------------------------------------------------
	
	/** World seed for deterministic spawn point generation. */
	private final long _worldSeed;
	
	/** Scene node that holds all enemy models. */
	private final Node _enemyNode;
	
	/** Asset manager for creating enemy materials. */
	private final AssetManager _assetManager;
	
	/** Spawn points keyed by packed region coordinate. */
	private final Map<Long, List<SpawnPoint>> _regionSpawnPoints = new HashMap<>();
	
	/** Set of region keys currently being tracked (have had spawn points generated). */
	private final Set<Long> _trackedRegions = new HashSet<>();
	
	/** Total number of active (spawned) enemies across all spawn points. */
	private int _activeCount = 0;
	
	/** Center of the player-spawn safe zone (world XZ). Null until set. */
	private float _safeZoneX;
	private float _safeZoneZ;
	private boolean _safeZoneSet = false;
	
	/** Reference to the combat system for enemy death healing drops. */
	private CombatSystem _combatSystem;
	
	/** Reference to the player controller for enemy death healing drops. */
	private PlayerController _playerController;
	
	/** Reference to the day/night cycle for night-aware spawn rates. */
	private DayNightCycle _dayNightCycle;
	
	/**
	 * Creates a new region-based spawn system.
	 * @param enemyNode the scene node to attach enemy models to
	 * @param assetManager the asset manager for creating enemy materials
	 * @param worldSeed the world seed for deterministic spawn point generation
	 */
	public SpawnSystem(Node enemyNode, AssetManager assetManager, long worldSeed)
	{
		_enemyNode = enemyNode;
		_assetManager = assetManager;
		_worldSeed = worldSeed;
	}
	
	/**
	 * Sets the safe zone center (player's world spawn point).<br>
	 * No enemies will spawn within {@link #SAFE_ZONE_RADIUS} of this position.
	 * @param x world X of the spawn point
	 * @param z world Z of the spawn point
	 */
	public void setPlayerSpawnZone(float x, float z)
	{
		_safeZoneX = x;
		_safeZoneZ = z;
		_safeZoneSet = true;
	}
	
	/**
	 * Sets the combat system reference for enemy death healing drops.
	 * @param combatSystem the combat system
	 * @param playerController the player controller
	 */
	public void setCombatReferences(CombatSystem combatSystem, PlayerController playerController)
	{
		_combatSystem = combatSystem;
		_playerController = playerController;
	}
	
	/**
	 * Sets the day/night cycle reference for night-aware spawn behavior.
	 * @param dayNightCycle the day/night cycle instance
	 */
	public void setDayNightCycle(DayNightCycle dayNightCycle)
	{
		_dayNightCycle = dayNightCycle;
	}
	
	// ------------------------------------------------------------------
	// Night-aware helper methods.
	// ------------------------------------------------------------------
	
	/**
	 * Returns whether it is currently night according to the day/night cycle.<br>
	 * Defaults to false if no cycle is set.
	 */
	private boolean isNight()
	{
		return _dayNightCycle != null && _dayNightCycle.isNight();
	}
	
	/**
	 * Returns the current maximum active enemy cap based on time of day.
	 */
	private int getMaxActiveEnemies()
	{
		return isNight() ? MAX_ACTIVE_ENEMIES_NIGHT : MAX_ACTIVE_ENEMIES_DAY;
	}
	
	/**
	 * Returns the squared maximum activation range based on time of day.
	 */
	private float getActivationRangeMaxSq()
	{
		return isNight() ? ACTIVATION_RANGE_MAX_NIGHT_SQ : ACTIVATION_RANGE_MAX_DAY_SQ;
	}
	
	/**
	 * Returns the respawn cooldown based on time of day.
	 */
	private float getRespawnCooldown()
	{
		return isNight() ? RESPAWN_COOLDOWN_NIGHT : RESPAWN_COOLDOWN_DAY;
	}
	
	// ------------------------------------------------------------------
	// Main update.
	// ------------------------------------------------------------------
	
	/**
	 * Per-frame update. Manages region tracking, spawn point activation/deactivation,<br>
	 * death handling, respawn timers, and enemy AI updates.
	 * @param playerPos the player's current world position
	 * @param playerInWater true if the player's feet are in water
	 * @param world the game world for block queries and region checks
	 * @param tpf time per frame in seconds
	 */
	public void update(Vector3f playerPos, boolean playerInWater, World world, float tpf)
	{
		// 1. Determine which regions should be tracked and update the set.
		updateTrackedRegions(playerPos, world);
		
		// 2. Process all spawn points: activate, deactivate, handle death, update timers.
		processSpawnPoints(playerPos, playerInWater, world, tpf);
	}
	
	// ------------------------------------------------------------------
	// Region tracking.
	// ------------------------------------------------------------------
	
	/**
	 * Tracks regions near the player. Generates spawn points for newly tracked regions<br>
	 * and cleans up spawn points for regions that are no longer nearby.
	 */
	private void updateTrackedRegions(Vector3f playerPos, World world)
	{
		// Calculate scan radius in regions based on despawn range.
		final int scanRadius = (int) Math.ceil(DESPAWN_RANGE / Region.SIZE_XZ) + 1;
		final int playerRegionX = Math.floorDiv((int) Math.floor(playerPos.x), Region.SIZE_XZ);
		final int playerRegionZ = Math.floorDiv((int) Math.floor(playerPos.z), Region.SIZE_XZ);
		
		// Build the set of desired region keys.
		final Set<Long> desiredRegions = new HashSet<>();
		for (int rx = playerRegionX - scanRadius; rx <= playerRegionX + scanRadius; rx++)
		{
			for (int rz = playerRegionZ - scanRadius; rz <= playerRegionZ + scanRadius; rz++)
			{
				desiredRegions.add(regionKey(rx, rz));
			}
		}
		
		// Track new regions that are loaded.
		for (long key : desiredRegions)
		{
			if (!_trackedRegions.contains(key))
			{
				final int rx = regionKeyX(key);
				final int rz = regionKeyZ(key);
				
				// Only generate spawn points if the region is actually loaded.
				if (world.getRegion(rx, rz) != null)
				{
					final List<SpawnPoint> points = generateSpawnPoints(rx, rz);
					_regionSpawnPoints.put(key, points);
					_trackedRegions.add(key);
				}
			}
		}
		
		// Untrack regions that are no longer desired.
		final Iterator<Long> trackIterator = _trackedRegions.iterator();
		while (trackIterator.hasNext())
		{
			final long key = trackIterator.next();
			if (!desiredRegions.contains(key))
			{
				// Despawn any active enemies in this region.
				final List<SpawnPoint> points = _regionSpawnPoints.remove(key);
				if (points != null)
				{
					for (SpawnPoint point : points)
					{
						despawnEnemy(point);
					}
				}
				trackIterator.remove();
			}
		}
	}
	
	// ------------------------------------------------------------------
	// Spawn point generation (deterministic from seed).
	// ------------------------------------------------------------------
	
	/**
	 * Generates spawn points for a region using a deterministic RNG seeded from<br>
	 * the world seed and region coordinates. XZ positions and enemy types are<br>
	 * fixed for any given world; Y is resolved later against actual terrain.
	 */
	private List<SpawnPoint> generateSpawnPoints(int regionX, int regionZ)
	{
		final long regionSeed = _worldSeed * SEED_MUL_A + regionKey(regionX, regionZ) * SEED_MUL_B;
		final Random rng = new Random(regionSeed);
		
		// Calculate spawn count using rate as probability for fractional part.
		// e.g. rate 1.0 = 10% chance of 1 point, rate 5.0 = 50%, rate 10.0 = guaranteed 1.
		final float scaled = MAX_SPAWNS_PER_REGION * (SPAWN_RATE / 10f);
		final int guaranteed = (int) scaled;
		final float fractional = scaled - guaranteed;
		final int count = guaranteed + (rng.nextFloat() < fractional ? 1 : 0);
		
		final int originX = regionX * Region.SIZE_XZ;
		final int originZ = regionZ * Region.SIZE_XZ;
		
		// Select from the night table if it is currently night for more threatening spawns.
		final EnemyType[] spawnTable = isNight() ? LAND_SPAWN_TABLE_NIGHT : LAND_SPAWN_TABLE_DAY;
		
		final List<SpawnPoint> points = new ArrayList<>(count);
		for (int i = 0; i < count; i++)
		{
			final int wx = originX + rng.nextInt(Region.SIZE_XZ);
			final int wz = originZ + rng.nextInt(Region.SIZE_XZ);
			final EnemyType landType = spawnTable[rng.nextInt(spawnTable.length)];
			
			points.add(new SpawnPoint(wx, wz, landType));
		}
		
		return points;
	}
	
	// ------------------------------------------------------------------
	// Spawn point resolution (terrain queries).
	// ------------------------------------------------------------------
	
	/**
	 * Resolves a spawn point's Y coordinate and validity against the actual terrain.<br>
	 * If the position has water, converts to a piranha spawn. If no valid ground<br>
	 * or headroom exists, marks the point as invalid.
	 */
	private void resolveSpawnPoint(SpawnPoint point, World world)
	{
		point.resolved = true;
		
		final int bx = point.worldX;
		final int bz = point.worldZ;
		
		// Reject spawn points inside the player's safe zone.
		if (_safeZoneSet)
		{
			final float distSq = horizontalDistanceSq(_safeZoneX, _safeZoneZ, bx, bz);
			if (distSq < SAFE_ZONE_RADIUS_SQ)
			{
				point.valid = false;
				return;
			}
		}
		
		// Check for water — if found, this becomes a piranha spawn.
		final int waterY = findWaterY(world, bx, bz);
		if (waterY >= 0)
		{
			point.worldY = waterY;
			point.resolvedType = EnemyType.PIRANHA;
			point.aquatic = true;
			point.valid = true;
			point.activationDelay = ACTIVATION_DELAY;
			return;
		}
		
		// Find ground height for land enemy.
		final int groundY = findGroundY(world, bx, bz);
		if (groundY < 0)
		{
			point.valid = false;
			return;
		}
		
		// Check that feet position is not water or solid.
		final Block footBlock = world.getBlock(bx, groundY, bz);
		if (footBlock.isLiquid() || footBlock.isSolid())
		{
			point.valid = false;
			return;
		}
		
		// Check headroom (2 blocks above ground).
		final Block headBlock = world.getBlock(bx, groundY + 1, bz);
		if (headBlock.isSolid())
		{
			point.valid = false;
			return;
		}
		
		point.worldY = groundY;
		point.resolvedType = point.landType;
		point.aquatic = false;
		point.valid = !hasPlayerActivity(world, bx, groundY, bz) && !isEnclosedByPlayer(world, bx, groundY, bz);
		if (point.valid)
		{
			point.activationDelay = ACTIVATION_DELAY;
		}
	}
	
	// ------------------------------------------------------------------
	// Spawn point processing.
	// ------------------------------------------------------------------
	
	/**
	 * Iterates all tracked spawn points and handles activation, deactivation,<br>
	 * death cleanup, respawn timers, and per-frame enemy updates.
	 */
	private void processSpawnPoints(Vector3f playerPos, boolean playerInWater, World world, float tpf)
	{
		final int maxEnemies = getMaxActiveEnemies();
		final float activationRangeMaxSq = getActivationRangeMaxSq();
		
		for (List<SpawnPoint> points : _regionSpawnPoints.values())
		{
			for (SpawnPoint point : points)
			{
				// Lazy resolution on first encounter.
				if (!point.resolved)
				{
					resolveSpawnPoint(point, world);
				}
				
				if (!point.valid)
				{
					continue;
				}
				
				final float distSq = horizontalDistanceSq(playerPos.x, playerPos.z, point.worldX, point.worldZ);
				
				if (point.activeEnemy != null)
				{
					// --- Active enemy exists at this point ---
					final Enemy enemy = point.activeEnemy;
					
					// Handle death.
					if (!enemy.isAlive())
					{
						// Process death drop (once per enemy death).
						if (!point.deathProcessed)
						{
							point.deathProcessed = true;
							if (_combatSystem != null && _playerController != null)
							{
								_combatSystem.onEnemyDeath(_playerController, enemy);
							}
						}
						
						enemy.setStateTimer(enemy.getStateTimer() + tpf);
						
						// Update hit flash fade-out and death scale-down animation.
						enemy.updateVisuals(tpf);
						
						if (enemy.getStateTimer() >= DEATH_LINGER_TIME)
						{
							_enemyNode.detachChild(enemy.getNode());
							point.activeEnemy = null;
							point.respawnTimer = getRespawnCooldown();
							point.deathProcessed = false;
							_activeCount--;
						}
						continue;
					}
					
					// Despawn if player moved too far.
					if (distSq > DESPAWN_RANGE_SQ)
					{
						despawnEnemy(point);
						continue;
					}
					
					// Drive spawn-in animation.
					if (enemy.isSpawning())
					{
						updateSpawnAnimation(enemy, tpf);
						continue;
					}
					
					// Normal update: sky light modulated by day/night brightness + AI + animation + combat visuals.
					final Vector3f pos = enemy.getPosition();
					float skyLight = world.getSkyLight((int) Math.floor(pos.x), (int) Math.floor(pos.y), (int) Math.floor(pos.z));
					if (_dayNightCycle != null)
					{
						skyLight *= _dayNightCycle.getSkyBrightness();
					}
					enemy.setSkyLight(skyLight);
					enemy.update(playerPos, playerInWater, world, tpf);
					
					// Update hit flash for alive enemies (fades white material back to original).
					enemy.updateVisuals(tpf);
				}
				else
				{
					// --- No active enemy — check if we should spawn one ---
					
					// Tick respawn cooldown.
					if (point.respawnTimer > 0)
					{
						point.respawnTimer -= tpf;
						continue;
					}
					
					// Tick activation delay (prevents pop-in when region loads near player).
					if (point.activationDelay > 0)
					{
						point.activationDelay -= tpf;
						continue;
					}
					
					// Activate if player is within the activation band (not too close, not too far).
					if (distSq >= ACTIVATION_RANGE_MIN_SQ && distSq <= activationRangeMaxSq && _activeCount < maxEnemies)
					{
						// Re-check for player buildings (may have built since resolution).
						if (hasPlayerActivity(world, point.worldX, point.worldY, point.worldZ) || isEnclosedByPlayer(world, point.worldX, point.worldY, point.worldZ))
						{
							continue;
						}
						
						activateSpawnPoint(point, world);
					}
				}
			}
		}
	}
	
	// ------------------------------------------------------------------
	// Activation / deactivation.
	// ------------------------------------------------------------------
	
	/**
	 * Spawns an enemy at the given spawn point with the scale-up animation.
	 * @param point the spawn point to activate
	 * @param world the world for sky light lookups
	 */
	private void activateSpawnPoint(SpawnPoint point, World world)
	{
		final float spawnY = point.aquatic ? point.worldY : (point.worldY + GROUND_OFFSET);
		
		final Enemy enemy = EnemyFactory.createEnemy(point.resolvedType, _assetManager);
		enemy.setPosition(new Vector3f(point.worldX + 0.5f, spawnY, point.worldZ + 0.5f));
		
		// Set initial sky light so the enemy spawns at the correct brightness (not full white at night).
		float skyLight = world.getSkyLight(point.worldX, (int) Math.floor(spawnY), point.worldZ);
		if (_dayNightCycle != null)
		{
			skyLight *= _dayNightCycle.getSkyBrightness();
		}
		enemy.setSkyLight(skyLight);
		
		EnemyLighting.initializeLighting(enemy);
		
		// Initialize combat visuals (material cache for hit flash).
		enemy.initCombat(_assetManager);
		
		// Start spawn-in animation.
		enemy.setSpawning(true);
		enemy.setSpawnTimer(0);
		enemy.getNode().setLocalScale(0);
		
		_enemyNode.attachChild(enemy.getNode());
		point.activeEnemy = enemy;
		point.deathProcessed = false;
		_activeCount++;
	}
	
	/**
	 * Silently removes the active enemy from a spawn point (despawn, not death).
	 */
	private void despawnEnemy(SpawnPoint point)
	{
		if (point.activeEnemy != null)
		{
			_enemyNode.detachChild(point.activeEnemy.getNode());
			point.activeEnemy = null;
			_activeCount--;
			// No cooldown on despawn — point is immediately available when player returns.
		}
	}
	
	// ------------------------------------------------------------------
	// Spawn animation.
	// ------------------------------------------------------------------
	
	/**
	 * Two-phase smooth-step scale animation: fast rise (0 → overshoot),<br>
	 * then settle (overshoot → 1.0) for an elastic, organic feel.
	 */
	private static void updateSpawnAnimation(Enemy enemy, float tpf)
	{
		float timer = enemy.getSpawnTimer() + tpf;
		enemy.setSpawnTimer(timer);
		
		float scale;
		
		if (timer >= SPAWN_ANIM_DURATION)
		{
			// Animation complete — finalize at full scale and activate AI.
			scale = 1.0f;
			enemy.setSpawning(false);
		}
		else
		{
			final float progress = timer / SPAWN_ANIM_DURATION;
			
			if (progress < SPAWN_RISE_FRACTION)
			{
				// Phase 1: Rise from 0 to overshoot.
				final float t = progress / SPAWN_RISE_FRACTION;
				final float smooth = t * t * (3.0f - 2.0f * t);
				scale = smooth * SPAWN_OVERSHOOT;
			}
			else
			{
				// Phase 2: Settle from overshoot to 1.0.
				final float t = (progress - SPAWN_RISE_FRACTION) / (1.0f - SPAWN_RISE_FRACTION);
				final float smooth = t * t * (3.0f - 2.0f * t);
				scale = SPAWN_OVERSHOOT + (1.0f - SPAWN_OVERSHOOT) * smooth;
			}
		}
		
		enemy.getNode().setLocalScale(scale);
	}
	
	// ------------------------------------------------------------------
	// Player building detection.
	// ------------------------------------------------------------------
	
	/**
	 * Checks whether any player-placed or player-removed blocks exist near a spawn point.<br>
	 * Scans a small box around the position to prevent enemies from spawning<br>
	 * inside or adjacent to player-built structures or excavated areas.
	 * @return true if player activity is found nearby
	 */
	private static boolean hasPlayerActivity(World world, int bx, int by, int bz)
	{
		for (int dx = -PLAYER_ACTIVITY_CHECK_RADIUS; dx <= PLAYER_ACTIVITY_CHECK_RADIUS; dx++)
		{
			for (int dz = -PLAYER_ACTIVITY_CHECK_RADIUS; dz <= PLAYER_ACTIVITY_CHECK_RADIUS; dz++)
			{
				for (int dy = 0; dy < PLAYER_ACTIVITY_CHECK_HEIGHT; dy++)
				{
					final int cx = bx + dx;
					final int cy = by + dy;
					final int cz = bz + dz;
					if (world.isPlayerPlaced(cx, cy, cz) || world.isPlayerRemoved(cx, cy, cz))
					{
						return true;
					}
				}
			}
		}
		
		return false;
	}
	
	/**
	 * Ground-level flood fill to detect if a spawn point is inside a player-built enclosure.<br>
	 * <br>
	 * Starting from the spawn position, BFS expands outward on the XZ plane at foot and head<br>
	 * level. A cell is passable if neither foot nor head height contains a solid or flat panel<br>
	 * block (so walls, doors, and windows all count as barriers).<br>
	 * <br>
	 * If the fill reaches {@link #ENCLOSURE_CHECK_RADIUS} blocks from the start, the area is<br>
	 * open and the method returns false immediately. If the fill terminates without escaping<br>
	 * (all paths blocked) and at least one boundary block was player-placed, the area is<br>
	 * enclosed and the method returns true. Natural caves (no player blocks on the boundary)<br>
	 * are not considered enclosed.<br>
	 * <br>
	 * Typical cost: 20–50 block lookups for a house interior; early exit for open terrain.
	 * @param world the game world
	 * @param startX spawn world X
	 * @param startY spawn world Y (feet level)
	 * @param startZ spawn world Z
	 * @return true if the spawn point is inside a player-built enclosure
	 */
	private static boolean isEnclosedByPlayer(World world, int startX, int startY, int startZ)
	{
		final int radiusSq = ENCLOSURE_CHECK_RADIUS * ENCLOSURE_CHECK_RADIUS;
		final Set<Long> visited = new HashSet<>();
		final Queue<int[]> queue = new LinkedList<>();
		boolean hitPlayerBlock = false;
		
		final long startKey = ((long) startX << 32) | (startZ & 0xFFFFFFFFL);
		visited.add(startKey);
		queue.add(new int[]
		{
			startX,
			startZ
		});
		
		while (!queue.isEmpty())
		{
			final int[] current = queue.poll();
			final int cx = current[0];
			final int cz = current[1];
			
			for (int[] dir : CARDINAL_DIRS)
			{
				final int nx = cx + dir[0];
				final int nz = cz + dir[1];
				final long key = ((long) nx << 32) | (nz & 0xFFFFFFFFL);
				
				if (visited.contains(key))
				{
					continue;
				}
				visited.add(key);
				
				// Check if this cell has reached the escape radius.
				final int dx = nx - startX;
				final int dz = nz - startZ;
				if ((dx * dx + dz * dz) >= radiusSq)
				{
					return false; // Open area — flood fill escaped.
				}
				
				// Check if passable at foot and head level.
				// Solid blocks and flat panels (doors, windows) count as walls.
				final Block foot = world.getBlock(nx, startY, nz);
				final Block head = world.getBlock(nx, startY + 1, nz);
				final boolean footBlocked = foot.isSolid() || foot.isFlatPanel();
				final boolean headBlocked = head.isSolid() || head.isFlatPanel();
				
				if (!footBlocked && !headBlocked)
				{
					// Passable — continue flood fill.
					queue.add(new int[]
					{
						nx,
						nz
					});
				}
				else
				{
					// Blocked — check if the wall is player-placed.
					if (world.isPlayerPlaced(nx, startY, nz) || world.isPlayerPlaced(nx, startY + 1, nz))
					{
						hitPlayerBlock = true;
					}
				}
			}
		}
		
		// BFS terminated without escaping — enclosed only if player blocks formed the barrier.
		// Natural caves (no player blocks on boundary) are not considered enclosed.
		return hitPlayerBlock;
	}
	
	// ------------------------------------------------------------------
	// Terrain query helpers.
	// ------------------------------------------------------------------
	
	/**
	 * Finds the Y of the ground surface (top of highest solid block + 1).<br>
	 * Scans the full column from top to bottom. Only called once per spawn point.
	 */
	private static int findGroundY(World world, int bx, int bz)
	{
		for (int y = 255; y >= 0; y--)
		{
			final Block block = world.getBlock(bx, y, bz);
			final Block above = world.getBlock(bx, y + 1, bz);
			
			if (block.isSolid() && !above.isSolid())
			{
				return y + 1;
			}
		}
		
		return -1;
	}
	
	/**
	 * Finds a water block Y position suitable for piranha placement.<br>
	 * Scans the full column from top to bottom. Only called once per spawn point.
	 */
	private static int findWaterY(World world, int bx, int bz)
	{
		for (int y = 255; y >= 0; y--)
		{
			if (world.getBlock(bx, y, bz) == Block.WATER)
			{
				return y;
			}
		}
		
		return -1;
	}
	
	// ------------------------------------------------------------------
	// Math helpers.
	// ------------------------------------------------------------------
	
	/**
	 * Returns the squared horizontal distance between two XZ positions.
	 */
	private static float horizontalDistanceSq(float x1, float z1, float x2, float z2)
	{
		final float dx = x1 - x2;
		final float dz = z1 - z2;
		return dx * dx + dz * dz;
	}
	
	// ------------------------------------------------------------------
	// Region key encoding (matches World convention).
	// ------------------------------------------------------------------
	
	private static long regionKey(int regionX, int regionZ)
	{
		return ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
	}
	
	private static int regionKeyX(long key)
	{
		return (int) (key >> 32);
	}
	
	private static int regionKeyZ(long key)
	{
		return (int) key;
	}
	
	// ------------------------------------------------------------------
	// Accessors.
	// ------------------------------------------------------------------
	
	/**
	 * Returns a snapshot list of all currently active (spawned) enemies.<br>
	 * Used by the combat system to check for player-enemy interactions.
	 */
	public List<Enemy> getEnemies()
	{
		final List<Enemy> enemies = new ArrayList<>(_activeCount);
		
		for (List<SpawnPoint> points : _regionSpawnPoints.values())
		{
			for (SpawnPoint point : points)
			{
				if (point.activeEnemy != null)
				{
					enemies.add(point.activeEnemy);
				}
			}
		}
		
		return enemies;
	}
	
	/**
	 * Returns the scene node holding all enemy models.
	 */
	public Node getEnemyNode()
	{
		return _enemyNode;
	}
}
