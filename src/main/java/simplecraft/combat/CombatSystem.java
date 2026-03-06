package simplecraft.combat;

import java.util.List;

import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;

import simplecraft.SimpleCraft;
import simplecraft.enemy.Enemy;
import simplecraft.enemy.EnemyAI.AIState;
import simplecraft.player.PlayerController;
import simplecraft.util.Rnd;

/**
 * Manages all combat interactions between enemies and the player.<br>
 * <br>
 * <b>Enemy → Player damage:</b> Scans all enemies in ATTACK state each frame. When an enemy's attack cooldown fires and the player is within attack range, damage is dealt via {@link PlayerController#takeDamage(float, String)}.<br>
 * <br>
 * <b>Screen flash:</b> Full-screen colored quad on the GUI node that fades out over 0.3 seconds. Red (alpha 0.3) for damage, green (alpha 0.2) for healing. Reused across all damage/healing sources — any new flash restarts the timer.<br>
 * <br>
 * <b>Enemy death healing:</b> When an enemy dies, a 30% chance to heal the player by 2.0 HP (temporary scrap-finding mechanic until proper item drops exist).
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
	// Enemy death healing constants.
	// ------------------------------------------------------------------
	
	/** Chance (0-1) that killing an enemy yields a healing drop. */
	private static final float ENEMY_DROP_CHANCE = 0.3f;
	
	/** HP healed when an enemy drops food scraps. */
	private static final float ENEMY_DROP_HEAL = 2.0f;
	
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
	
	/**
	 * Creates the combat system and attaches its screen flash overlay to the GUI.
	 */
	public CombatSystem()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		_guiNode = app.getGuiNode();
		
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
	 * @param tpf time per frame in seconds
	 */
	public void update(PlayerController player, List<Enemy> enemies, float tpf)
	{
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
					final String source = "Killed by " + formatEnemyName(enemy.getType());
					player.takeDamage(enemy.getAttackDamage(), source);
					triggerDamageFlash();
					
					System.out.println(formatEnemyName(enemy.getType()) + " hit player for " + String.format("%.1f", enemy.getAttackDamage()) + " damage! HP: " + String.format("%.1f", player.getHealth()) + "/" + String.format("%.0f", player.getMaxHealth()));
				}
			}
		}
		
		// Update flash.
		updateFlash(tpf);
	}
	
	/**
	 * Called when an enemy dies. Rolls for a healing drop.
	 * @param player the player controller
	 * @param enemy the enemy that just died
	 */
	public void onEnemyDeath(PlayerController player, Enemy enemy)
	{
		if (player.isDead())
		{
			return;
		}
		
		if (Rnd.nextFloat() < ENEMY_DROP_CHANCE)
		{
			player.heal(ENEMY_DROP_HEAL);
			triggerHealFlash();
			System.out.println("Found food! +2 HP (Health: " + String.format("%.1f", player.getHealth()) + "/" + String.format("%.0f", player.getMaxHealth()) + ")");
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
	
	// ------------------------------------------------------------------
	// Helpers.
	// ------------------------------------------------------------------
	
	/**
	 * Formats an enemy type enum into a display name (e.g. ZOMBIE → "Zombie").
	 */
	private static String formatEnemyName(Enemy.EnemyType type)
	{
		final String name = type.name();
		return name.charAt(0) + name.substring(1).toLowerCase();
	}
}
