package simplecraft.combat;

import java.util.List;

import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;

import simplecraft.SimpleCraft;
import simplecraft.audio.AudioManager;
import simplecraft.effects.ParticleManager;
import simplecraft.enemy.Enemy;
import simplecraft.enemy.Enemy.EnemyType;
import simplecraft.enemy.EnemyAI.AIState;
import simplecraft.enemy.EnemyDropTable;
import simplecraft.item.DropManager;
import simplecraft.item.Inventory;
import simplecraft.item.ItemInstance;
import simplecraft.player.PlayerController;
import simplecraft.player.ViewmodelRenderer;
import simplecraft.world.World;

/**
 * Manages all combat interactions between the player and enemies.<br>
 * <br>
 * <b>Player -> Enemy damage:</b> {@link #tryPlayerAttack(Camera, List, World, PlayerController)} raycasts from the camera<br>
 * with a 3-block reach and a generous 0.5-block hit cylinder around each enemy's center mass.<br>
 * The closest hit enemy takes damage based on the held weapon/tool (bare hands: 3.0).<br>
 * Attack cooldown varies by weapon speed. Durability is consumed per hit.<br>
 * Hit and death feedback is handled by {@link Enemy#takeDamage(float)} (white flash, scale-down).<br>
 * <br>
 * <b>Enemy -> Player damage:</b> Scans all enemies in ATTACK state each frame. When an enemy's attack cooldown fires and the player is within attack range, damage is dealt via {@link PlayerController#takeDamage(float, String)}.<br>
 * <br>
 * <b>Screen flash:</b> Full-screen colored quad on the GUI node that fades out over 0.3 seconds. Red (alpha 0.3) for damage, green (alpha 0.2) for healing. Reused across all damage/healing sources - any new flash restarts the timer.<br>
 * <br>
 * <b>Enemy death drops:</b> When an enemy dies, its drop table is rolled via {@link EnemyDropTable}<br>
 * and resulting items are spawned as world drops via the {@link DropManager}.
 * @author Pantelis Andrianakis
 * @since March 6th 2026
 */
public class CombatSystem
{
	// ------------------------------------------------------------------
	// Flash constants.
	// ------------------------------------------------------------------
	
	/** Duration of the screen flash fade-out in seconds. */
	private static final float FLASH_DURATION = 0.3f;
	
	/** Starting alpha for the damage (red) flash. */
	private static final float DAMAGE_FLASH_ALPHA = 0.3f;
	
	/** Starting alpha for the healing (green) flash. */
	private static final float HEAL_FLASH_ALPHA = 0.2f;
	
	// ------------------------------------------------------------------
	// Player attack constants.
	// ------------------------------------------------------------------
	
	/** Maximum raycast reach in blocks. */
	private static final float PLAYER_ATTACK_RANGE = 3.0f;
	
	/**
	 * Generous hit cylinder radius around enemy center mass (blocks).<br>
	 * The ray must pass within this distance of the enemy's center to register a hit.
	 */
	private static final float PLAYER_HIT_RADIUS = 0.5f;
	
	/**
	 * Close-range sphere radius for point-blank hit detection (blocks).<br>
	 * When the 3D distance from the camera to an enemy's center mass is within this<br>
	 * threshold, the hit registers regardless of the cylinder projection. This prevents<br>
	 * enemies pressed against the player from being unhittable due to the vertical<br>
	 * gap between eye height and enemy center mass exceeding the cylinder hit radius.
	 */
	private static final float CLOSE_RANGE_RADIUS = 1.5f;
	
	/**
	 * Vertical offset from enemy feet to center mass for hit detection (blocks).<br>
	 * Approximates the torso center for all enemy types.
	 */
	private static final float ENEMY_CENTER_OFFSET = 0.9f;
	
	/**
	 * Step size for the line-of-sight solid block check (blocks).<br>
	 * Smaller values are more accurate but cost more iterations.<br>
	 * 0.5 is a good balance: at max range (3 blocks), only 6 steps.
	 */
	private static final float LOS_STEP_SIZE = 0.5f;
	
	// ------------------------------------------------------------------
	// Fields.
	// ------------------------------------------------------------------
	
	/** Full-screen flash quad geometry. */
	private final Geometry _flashGeometry;
	
	/** Material for the flash quad (color changed dynamically). */
	private final Material _flashMaterial;
	
	/** Current flash timer (counts down from FLASH_DURATION to 0). */
	private float _flashTimer;
	
	/** Starting alpha of the current flash. */
	private float _flashStartAlpha;
	
	/** Current flash color (red or green). */
	private final ColorRGBA _flashColor = new ColorRGBA();
	
	/** Reusable color to avoid allocation. */
	private final ColorRGBA _tempColor = new ColorRGBA();
	
	/** The GUI node this system's overlays are attached to. */
	private final Node _guiNode;
	
	/** Audio manager for combat sound effects. */
	private final AudioManager _audioManager;
	
	/** Player attack cooldown timer (counts down to 0). */
	private float _playerAttackTimer;
	
	/** Particle effect manager for damage hit particles. */
	private ParticleManager _particleManager;
	
	/** Viewmodel renderer for triggering swing animation on player attacks. */
	private ViewmodelRenderer _viewmodelRenderer;
	
	/** Drop manager for spawning item drops on enemy death. */
	private DropManager _dropManager;
	
	// Reusable vectors for raycast math (avoid per-frame allocation).
	private final Vector3f _rayOrigin = new Vector3f();
	private final Vector3f _rayDir = new Vector3f();
	private final Vector3f _toEnemy = new Vector3f();
	private final Vector3f _closestPoint = new Vector3f();
	
	/**
	 * Creates the combat system and attaches its screen flash overlay to the GUI.
	 * @param audioManager the audio manager for combat sound effects
	 */
	public CombatSystem(AudioManager audioManager)
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		_guiNode = app.getGuiNode();
		_audioManager = audioManager;
		
		final int screenWidth = app.getCamera().getWidth();
		final int screenHeight = app.getCamera().getHeight();
		
		// Build full-screen flash quad (hidden initially).
		final Quad flashQuad = new Quad(screenWidth, screenHeight);
		_flashGeometry = new Geometry("DamageFlash", flashQuad);
		_flashMaterial = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
		_flashMaterial.setColor("Color", new ColorRGBA(0, 0, 0, 0));
		_flashMaterial.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
		_flashGeometry.setMaterial(_flashMaterial);
		_flashGeometry.setQueueBucket(Bucket.Gui);
		_flashGeometry.setLocalTranslation(0, 0, 10); // Above most GUI elements.
		_flashGeometry.setCullHint(Geometry.CullHint.Always);
		
		_guiNode.attachChild(_flashGeometry);
	}
	
	/**
	 * Per-frame update. Checks enemy attacks, processes flash fade-out.
	 * @param player the player controller
	 * @param enemies the list of currently active enemies
	 * @param world the game world for line-of-sight checks
	 * @param tpf time per frame in seconds
	 */
	public void update(PlayerController player, List<Enemy> enemies, World world, float tpf)
	{
		// Tick player attack cooldown.
		if (_playerAttackTimer > 0)
		{
			_playerAttackTimer -= tpf;
		}
		
		if (player.isDead())
		{
			// No combat processing while dead.
			updateFlash(tpf);
			return;
		}
		
		// Check each enemy for attack-state damage.
		for (int i = 0; i < enemies.size(); i++)
		{
			final Enemy enemy = enemies.get(i);
			
			if (!enemy.isAlive() || enemy.isSpawning())
			{
				continue;
			}
			
			if (enemy.getAIState() != AIState.ATTACK)
			{
				continue;
			}
			
			// Detect attack cooldown fire: EnemyAI resets attackTimer to exactly 0
			// when cooldown expires. The stateTimer check avoids false positives on
			// the first frame of entering ATTACK state (both timers are 0 then).
			if (enemy.getAttackTimer() == 0f && enemy.getStateTimer() > 0.1f)
			{
				// Verify the player is actually within attack range
				// (3D distance so enemies can't hit through floors or tall pillars).
				final float dist = enemy.getPosition().distance(player.getPosition());
				if (dist <= enemy.getAttackRange() * 1.2f)
				{
					// Line-of-sight check: enemies cannot hit through walls.
					if (isLineOfSightBlocked(enemy.getPosition(), ENEMY_CENTER_OFFSET, player.getPosition(), ENEMY_CENTER_OFFSET, world))
					{
						continue;
					}
					
					final String source = "Killed by " + formatEnemyName(enemy.getType());
					player.takeDamage(enemy.getAttackDamage(), source);
					triggerDamageFlash();
					_audioManager.playSfx(AudioManager.SFX_PLAYER_HURT);
					
					if (player.isDead())
					{
						_audioManager.playSfx(AudioManager.SFX_PLAYER_DEATH);
					}
					
					System.out.println(formatEnemyName(enemy.getType()) + " hit player for " + String.format("%.1f", enemy.getAttackDamage()) + " damage! HP: " + String.format("%.1f", player.getHealth()) + "/" + String.format("%.0f", player.getMaxHealth()));
				}
			}
		}
		
		// Update flash.
		updateFlash(tpf);
	}
	
	// ------------------------------------------------------------------
	// Player -> Enemy attack.
	// ------------------------------------------------------------------
	
	/**
	 * Attempts a player attack via camera raycast. Finds the closest enemy whose<br>
	 * center mass is within {@link #PLAYER_HIT_RADIUS} of the camera ray, up to<br>
	 * {@link #PLAYER_ATTACK_RANGE} blocks away. Before confirming a hit, checks<br>
	 * line-of-sight by stepping along the ray and verifying no solid block is between<br>
	 * the camera and the enemy. If a hit is found, deals damage based on the held<br>
	 * weapon/tool and starts the attack cooldown. Durability is consumed per hit.<br>
	 * <br>
	 * Called by {@link simplecraft.state.PlayingState} on left-click, before block<br>
	 * interaction processes. Returns true if an enemy was hit (suppresses block breaking).
	 * @param camera the player's camera (ray origin and direction)
	 * @param enemies the list of currently active enemies
	 * @param world the game world for solid block line-of-sight checks
	 * @param playerController the player controller for attack damage, speed and inventory
	 * @return true if an enemy was hit, false if the attack missed or is on cooldown
	 */
	public boolean tryPlayerAttack(Camera camera, List<Enemy> enemies, World world, PlayerController playerController)
	{
		_rayOrigin.set(camera.getLocation());
		_rayDir.set(camera.getDirection()).normalizeLocal();
		
		Enemy closestEnemy = null;
		float closestDist = PLAYER_ATTACK_RANGE;
		
		for (int i = 0; i < enemies.size(); i++)
		{
			final Enemy enemy = enemies.get(i);
			
			if (!enemy.isAlive() || enemy.isSpawning())
			{
				continue;
			}
			
			// Calculate enemy center mass position (feet + vertical offset).
			final Vector3f enemyPos = enemy.getPosition();
			final float centerX = enemyPos.x;
			final float centerY = enemyPos.y + ENEMY_CENTER_OFFSET;
			final float centerZ = enemyPos.z;
			
			// Vector from ray origin to enemy center.
			_toEnemy.set(centerX - _rayOrigin.x, centerY - _rayOrigin.y, centerZ - _rayOrigin.z);
			
			// Check close-range sphere first: if the enemy center is within arm's
			// reach of the camera AND roughly in front of the player, the hit registers
			// regardless of cylinder projection. This handles enemies pressed against
			// the player where the vertical gap between eye height and enemy center
			// mass exceeds the cylinder hit radius.
			final float distSq = _toEnemy.lengthSquared();
			if (distSq <= CLOSE_RANGE_RADIUS * CLOSE_RANGE_RADIUS)
			{
				// Facing check: dot product between camera direction and direction
				// to enemy must be positive (enemy is in front of the player).
				// At close range dot can be small due to the vertical angle down,
				// so a simple > 0 (forward hemisphere) is sufficient.
				final float facingDot = _toEnemy.dot(_rayDir);
				if (facingDot > 0)
				{
					// Use straight-line distance for sorting (closer = better).
					final float dist = (float) Math.sqrt(distSq);
					if (dist < closestDist)
					{
						closestEnemy = enemy;
						closestDist = dist;
					}
				}
				continue;
			}
			
			// Project onto ray direction to find closest point on ray.
			final float dot = _toEnemy.dot(_rayDir);
			
			// Behind the camera or beyond attack range - skip.
			if (dot < 0 || dot > PLAYER_ATTACK_RANGE)
			{
				continue;
			}
			
			// Closest point on the ray to the enemy center.
			_closestPoint.set(_rayDir).multLocal(dot).addLocal(_rayOrigin);
			
			// Perpendicular distance from enemy center to ray.
			final float dx = _closestPoint.x - centerX;
			final float dy = _closestPoint.y - centerY;
			final float dz = _closestPoint.z - centerZ;
			final float perpDistSq = dx * dx + dy * dy + dz * dz;
			
			// Check if within the generous hit radius and closer than current best.
			if (perpDistSq <= PLAYER_HIT_RADIUS * PLAYER_HIT_RADIUS && dot < closestDist)
			{
				closestEnemy = enemy;
				closestDist = dot;
			}
		}
		
		if (closestEnemy != null)
		{
			// Line-of-sight check: step along the ray and verify no solid block
			// is between the camera and the enemy. This prevents hitting enemies
			// through walls, floors and other solid geometry.
			if (isBlockedByTerrain(closestDist, world))
			{
				return false;
			}
			
			// Enemy is in the crosshair with clear line of sight - always suppress block breaking.
			// Only deal damage when the cooldown has expired.
			if (_playerAttackTimer <= 0)
			{
				final float attackDamage = playerController.getAttackDamage();
				final float attackSpeed = playerController.getAttackSpeed();
				
				closestEnemy.takeDamage(attackDamage, _audioManager);
				_playerAttackTimer = attackSpeed;
				
				// Trigger viewmodel swing animation.
				if (_viewmodelRenderer != null)
				{
					_viewmodelRenderer.triggerSwing();
				}
				
				// Durability loss - every successful attack costs 1 durability.
				final Inventory inventory = playerController.getInventory();
				final ItemInstance held = inventory.getSelectedItem();
				if (held != null && held.hasDurability())
				{
					final boolean broken = held.loseDurability(1);
					if (broken)
					{
						final String toolName = held.getTemplate().getDisplayName();
						inventory.setSlot(inventory.getSelectedHotbarIndex(), null);
						System.out.println("Combat: " + toolName + " broke!");
					}
				}
				
				// Spawn red damage particles at the enemy's center mass.
				if (_particleManager != null)
				{
					final Vector3f hitPos = new Vector3f(closestEnemy.getPosition());
					hitPos.y += ENEMY_CENTER_OFFSET;
					_particleManager.spawnDamage(hitPos);
				}
				
				final String name = formatEnemyName(closestEnemy.getType());
				if (!closestEnemy.isDying())
				{
					System.out.println("Hit " + name + " for " + String.format("%.1f", attackDamage) + " damage! HP: " + String.format("%.1f", closestEnemy.getHealth()) + "/" + String.format("%.0f", closestEnemy.getMaxHealth()));
				}
				else
				{
					System.out.println("Killed " + name + "! Final hit dealt " + String.format("%.1f", attackDamage) + " damage.");
				}
			}
			
			return true;
		}
		
		return false;
	}
	
	/**
	 * Steps along the stored ray ({@link #_rayOrigin}, {@link #_rayDir}) checking<br>
	 * for solid blocks. Returns true if any solid block is found before the given distance.
	 * @param maxDist the distance along the ray to check (enemy distance)
	 * @param world the game world
	 * @return true if a solid block blocks line-of-sight
	 */
	private boolean isBlockedByTerrain(float maxDist, World world)
	{
		// Start one step in (avoid checking the block the player is standing in).
		for (float t = LOS_STEP_SIZE; t < maxDist; t += LOS_STEP_SIZE)
		{
			final int bx = (int) Math.floor(_rayOrigin.x + _rayDir.x * t);
			final int by = (int) Math.floor(_rayOrigin.y + _rayDir.y * t);
			final int bz = (int) Math.floor(_rayOrigin.z + _rayDir.z * t);
			
			if (world.getBlock(bx, by, bz).isSolid())
			{
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * Returns true if the player's attack is currently on cooldown.
	 */
	public boolean isPlayerAttackOnCooldown()
	{
		return _playerAttackTimer > 0;
	}
	
	/**
	 * Checks line-of-sight between two positions by stepping along the line and<br>
	 * testing for solid blocks. Used to prevent enemy attacks through walls.
	 * @param fromPos the source position (feet level)
	 * @param fromYOffset vertical offset from source feet to check point (center mass)
	 * @param toPos the target position (feet level)
	 * @param toYOffset vertical offset from target feet to check point (center mass)
	 * @param world the game world
	 * @return true if a solid block blocks the line between the two points
	 */
	private boolean isLineOfSightBlocked(Vector3f fromPos, float fromYOffset, Vector3f toPos, float toYOffset, World world)
	{
		final float startX = fromPos.x;
		final float startY = fromPos.y + fromYOffset;
		final float startZ = fromPos.z;
		final float endX = toPos.x;
		final float endY = toPos.y + toYOffset;
		final float endZ = toPos.z;
		
		final float dx = endX - startX;
		final float dy = endY - startY;
		final float dz = endZ - startZ;
		final float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		
		if (dist < LOS_STEP_SIZE)
		{
			return false;
		}
		
		final float invDist = 1.0f / dist;
		final float dirX = dx * invDist;
		final float dirY = dy * invDist;
		final float dirZ = dz * invDist;
		
		for (float t = LOS_STEP_SIZE; t < dist; t += LOS_STEP_SIZE)
		{
			final int bx = (int) Math.floor(startX + dirX * t);
			final int by = (int) Math.floor(startY + dirY * t);
			final int bz = (int) Math.floor(startZ + dirZ * t);
			
			if (world.getBlock(bx, by, bz).isSolid())
			{
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * Called when an enemy dies. Rolls the drop table for the enemy type<br>
	 * and spawns each resulting item as a world drop via the {@link DropManager}.
	 * @param player the player controller
	 * @param enemy the enemy that just died
	 */
	public void onEnemyDeath(PlayerController player, Enemy enemy)
	{
		if (_dropManager == null)
		{
			return;
		}
		
		final Vector3f deathPos = enemy.getPosition();
		final List<ItemInstance> drops = EnemyDropTable.rollDrops(enemy.getType(), deathPos.x, deathPos.z);
		
		for (int i = 0; i < drops.size(); i++)
		{
			// Offset each drop slightly so they don't overlap.
			final float offsetX = (i % 2 == 0) ? (i * 0.2f) : -(i * 0.2f);
			final float offsetZ = (i % 2 == 0) ? -(i * 0.15f) : (i * 0.15f);
			final Vector3f dropPos = new Vector3f(deathPos.x + offsetX, deathPos.y, deathPos.z + offsetZ);
			
			_dropManager.spawnDrop(dropPos, drops.get(i));
		}
		
		if (!drops.isEmpty())
		{
			System.out.println(formatEnemyName(enemy.getType()) + " dropped " + drops.size() + " item(s).");
		}
	}
	
	/**
	 * Triggers a red damage flash on screen.
	 */
	public void triggerDamageFlash()
	{
		_flashColor.set(1.0f, 0.0f, 0.0f, 1.0f);
		_flashStartAlpha = DAMAGE_FLASH_ALPHA;
		_flashTimer = FLASH_DURATION;
		_flashGeometry.setCullHint(Geometry.CullHint.Never);
	}
	
	/**
	 * Triggers a green healing flash on screen.
	 */
	public void triggerHealFlash()
	{
		_flashColor.set(0.0f, 1.0f, 0.0f, 1.0f);
		_flashStartAlpha = HEAL_FLASH_ALPHA;
		_flashTimer = FLASH_DURATION;
		_flashGeometry.setCullHint(Geometry.CullHint.Never);
	}
	
	/**
	 * Updates the flash fade-out animation.
	 */
	private void updateFlash(float tpf)
	{
		if (_flashTimer <= 0)
		{
			return;
		}
		
		_flashTimer -= tpf;
		
		if (_flashTimer <= 0)
		{
			_flashTimer = 0;
			_flashGeometry.setCullHint(Geometry.CullHint.Always);
		}
		else
		{
			final float alpha = _flashStartAlpha * (_flashTimer / FLASH_DURATION);
			_tempColor.set(_flashColor.r, _flashColor.g, _flashColor.b, alpha);
			_flashMaterial.setColor("Color", _tempColor);
		}
	}
	
	/**
	 * Removes all GUI elements. Call during state teardown.
	 */
	public void cleanup()
	{
		_guiNode.detachChild(_flashGeometry);
	}
	
	/**
	 * Sets the particle manager for combat hit visual effects.
	 * @param particleManager the particle manager instance
	 */
	public void setParticleManager(ParticleManager particleManager)
	{
		_particleManager = particleManager;
	}
	
	/**
	 * Sets the viewmodel renderer for triggering swing animation on player attacks.
	 * @param viewmodelRenderer the viewmodel renderer instance
	 */
	public void setViewmodelRenderer(ViewmodelRenderer viewmodelRenderer)
	{
		_viewmodelRenderer = viewmodelRenderer;
	}
	
	/**
	 * Sets the drop manager for spawning item drops on enemy death.
	 * @param dropManager the drop manager instance
	 */
	public void setDropManager(DropManager dropManager)
	{
		_dropManager = dropManager;
	}
	
	// ------------------------------------------------------------------
	// Helpers.
	// ------------------------------------------------------------------
	
	/**
	 * Formats an enemy type enum into a display name (e.g. ZOMBIE -> "Zombie").
	 */
	private static String formatEnemyName(EnemyType type)
	{
		final String name = type.name();
		return name.charAt(0) + name.substring(1).toLowerCase();
	}
}
