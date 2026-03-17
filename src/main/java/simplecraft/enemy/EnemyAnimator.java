package simplecraft.enemy;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

/**
 * Procedural animation system for all enemy types.<br>
 * Drives limb rotations, body scaling and positional offsets<br>
 * each frame based on the enemy type and movement state.<br>
 * <br>
 * Animation styles:<br>
 * - <b>Humanoids</b> (Zombie, Skeleton, Player): leg/arm swing via X-axis sine wave.<br>
 * - <b>Wolf</b>: four-leg trot with reduced amplitude, diagonal pair gait.<br>
 * - <b>Spider</b>: alternating leg pair scurry via Y-axis rotation on each leg node.<br>
 * - <b>Slime</b>: perpetual squash-and-stretch hop cycle (always active).<br>
 * - <b>Piranha</b>: body sway and tail wag for swimming motion.<br>
 * <br>
 * When an enemy stops moving, a blend factor smoothly fades the walk<br>
 * animation back to a neutral pose. An idle bobbing oscillation keeps<br>
 * all non-slime enemies from looking frozen when stationary.
 * @author Pantelis Andrianakis
 * @since March 4th 2026
 */
public class EnemyAnimator
{
	// ------------------------------------------------------------------
	// Walk animation constants.
	// ------------------------------------------------------------------
	
	/** Humanoid walk cycle speed (radians per second). */
	private static final float HUMANOID_WALK_SPEED = 8.0f;
	
	/** Humanoid limb swing amplitude (radians). */
	private static final float HUMANOID_SWING_AMPLITUDE = 0.6f;
	
	/** Wolf walk cycle speed (radians per second). */
	private static final float WOLF_WALK_SPEED = 8.0f;
	
	/** Wolf leg swing amplitude (radians). */
	private static final float WOLF_SWING_AMPLITUDE = 0.4f;
	
	/** Spider scurry cycle speed (radians per second). */
	private static final float SPIDER_WALK_SPEED = 10.0f;
	
	/** Spider leg forward/back swing amplitude around X axis (radians). */
	private static final float SPIDER_SWEEP_AMPLITUDE = 0.5f;
	
	/** Piranha swim cycle speed (radians per second). */
	private static final float PIRANHA_SWIM_SPEED = 6.0f;
	
	/** Piranha body sway amplitude (radians around Y). */
	private static final float PIRANHA_SWAY_AMPLITUDE = 0.08f;
	
	/** Piranha tail wag amplitude (radians around Y). */
	private static final float PIRANHA_TAIL_AMPLITUDE = 0.3f;
	
	// ------------------------------------------------------------------
	// Slime hop constants.
	// ------------------------------------------------------------------
	
	/** Slime hop cycle speed (radians per second). */
	private static final float SLIME_HOP_SPEED = 3.0f;
	
	/** Slime vertical scale oscillation amplitude. */
	private static final float SLIME_SCALE_Y_AMP = 0.3f;
	
	/** Slime horizontal scale oscillation amplitude. */
	private static final float SLIME_SCALE_XZ_AMP = 0.15f;
	
	/** Slime vertical translation amplitude (blocks). */
	private static final float SLIME_HOP_HEIGHT = 0.3f;
	
	// ------------------------------------------------------------------
	// Idle constants.
	// ------------------------------------------------------------------
	
	/** Idle bobbing cycle speed (radians per second). */
	private static final float IDLE_BOB_SPEED = 1.5f;
	
	/** Idle bobbing amplitude on Y axis (blocks). */
	private static final float IDLE_BOB_AMPLITUDE = 0.02f;
	
	// ------------------------------------------------------------------
	// Blend constants.
	// ------------------------------------------------------------------
	
	/** How fast the walk blend factor fades in/out (per second). */
	private static final float WALK_BLEND_RATE = 4.0f;
	
	// ------------------------------------------------------------------
	// Death animation constants.
	// ------------------------------------------------------------------
	
	/** Duration of the topple phase - enemy falls sideways with spin (seconds). */
	private static final float DEATH_TOPPLE_DURATION = 0.5f;
	
	/** Duration of the shrink phase - enemy scales down to nothing (seconds). */
	private static final float DEATH_SHRINK_DURATION = 0.45f;
	
	/** Total death animation duration (topple + shrink). */
	private static final float DEATH_TOTAL_DURATION = DEATH_TOPPLE_DURATION + DEATH_SHRINK_DURATION;
	
	/** How many full Y-axis spins during the topple (revolutions). */
	private static final float DEATH_SPIN_REVOLUTIONS = 0.75f;
	
	/** Maximum Z-axis tilt when fully toppled (radians). ~100° so it overshoots sideways slightly. */
	private static final float DEATH_TOPPLE_ANGLE = FastMath.DEG_TO_RAD * 100f;
	
	/** Shared quaternion to avoid allocations in the update loop. */
	private static final Quaternion TEMP_QUAT = new Quaternion();
	
	/** Second shared quaternion for rotation composition. */
	private static final Quaternion TEMP_QUAT2 = new Quaternion();
	
	/**
	 * Private constructor - utility class with only static methods.
	 */
	private EnemyAnimator()
	{
	}
	
	/**
	 * Updates the procedural animation for one enemy.<br>
	 * Called once per frame from {@link Enemy#update(float)}.
	 * @param enemy the enemy to animate
	 * @param tpf time per frame in seconds
	 * @param isMoving whether the enemy is currently walking / swimming
	 */
	public static void update(Enemy enemy, float tpf, boolean isMoving)
	{
		// Update lighting first.
		EnemyLighting.updateLighting(enemy);
		
		// Death animation overrides everything else.
		if (enemy.isDying())
		{
			animateDeath(enemy, tpf);
			return;
		}
		
		// Advance animation clock.
		enemy.setAnimTime(enemy.getAnimTime() + tpf);
		
		// Smooth walk blend factor toward 1 (moving) or 0 (stopped).
		final float targetBlend = isMoving ? 1.0f : 0.0f;
		float blend = enemy.getWalkBlend();
		blend = approach(blend, targetBlend, WALK_BLEND_RATE * tpf);
		enemy.setWalkBlend(blend);
		
		final float time = enemy.getAnimTime();
		
		switch (enemy.getType())
		{
			case ZOMBIE:
			case SKELETON:
			case PLAYER:
			{
				animateHumanoid(enemy, time, blend);
				animateIdle(enemy, time, blend);
				break;
			}
			case WOLF:
			{
				animateWolf(enemy, time, blend);
				animateIdle(enemy, time, blend);
				break;
			}
			case SPIDER:
			{
				animateSpider(enemy, time, blend);
				animateIdle(enemy, time, blend);
				break;
			}
			case SLIME:
			{
				animateSlime(enemy, time);
				// Slime has no idle - the hop IS the idle.
				break;
			}
			case PIRANHA:
			{
				animatePiranha(enemy, time, blend);
				animateIdle(enemy, time, blend);
				break;
			}
		}
	}
	
	// ------------------------------------------------------------------
	// Humanoid walk (Zombie, Skeleton, Player).
	// ------------------------------------------------------------------
	
	/**
	 * Swings legs and arms in opposite sine waves around the X axis.<br>
	 * Left leg and right arm share one phase; right leg and left arm share the opposite.
	 */
	private static void animateHumanoid(Enemy enemy, float time, float blend)
	{
		final float swing = FastMath.sin(time * HUMANOID_WALK_SPEED) * HUMANOID_SWING_AMPLITUDE * blend;
		
		// Legs: left forward when swing > 0, right forward when swing < 0.
		setXRotation(enemy.getLeftLeg(), swing);
		setXRotation(enemy.getRightLeg(), -swing);
		
		// Arms swing opposite to legs.
		setXRotation(enemy.getLeftArm(), -swing);
		setXRotation(enemy.getRightArm(), swing);
	}
	
	// ------------------------------------------------------------------
	// Wolf trot (diagonal pair gait).
	// ------------------------------------------------------------------
	
	/**
	 * Animates four wolf legs in a diagonal trot: front-left + back-right share<br>
	 * one phase, front-right + back-left share the opposite phase.
	 */
	private static void animateWolf(Enemy enemy, float time, float blend)
	{
		final float swing = FastMath.sin(time * WOLF_WALK_SPEED) * WOLF_SWING_AMPLITUDE * blend;
		
		// LeftLeg = front-left, RightLeg = front-right.
		// LeftArm = back-left, RightArm = back-right.
		// Diagonal pairs: FL + BR, FR + BL.
		setXRotation(enemy.getLeftLeg(), swing); // Front-left forward.
		setXRotation(enemy.getRightArm(), swing); // Back-right forward (same phase).
		setXRotation(enemy.getRightLeg(), -swing); // Front-right back.
		setXRotation(enemy.getLeftArm(), -swing); // Back-left back (same phase).
	}
	
	// ------------------------------------------------------------------
	// Spider scurry (alternating pair sweep).
	// ------------------------------------------------------------------
	
	/**
	 * Swings spider legs forward and back around the X axis in alternating pairs,<br>
	 * composing the swing with the factory's base 65° Z-axis splay.<br>
	 * Pairs (0, 2) swing one direction while (1, 3) swing the opposite,<br>
	 * creating a natural arthropod scurrying gait.
	 */
	private static void animateSpider(Enemy enemy, float time, float blend)
	{
		final float sweep = FastMath.sin(time * SPIDER_WALK_SPEED) * SPIDER_SWEEP_AMPLITUDE * blend;
		final Node root = enemy.getNode();
		final float baseSplay = 65f * FastMath.DEG_TO_RAD;
		
		for (int i = 0; i < 4; i++)
		{
			// Diagonal gait: L0+R1+L2+R3 move together, R0+L1+R2+L3 move opposite.
			// Same-index left and right legs swing in opposite directions.
			final float phase = ((i % 2) == 0) ? sweep : -sweep;
			
			final Spatial leftLeg = root.getChild("LeftSpiderLeg" + i);
			final Spatial rightLeg = root.getChild("RightSpiderLeg" + i);
			
			if (leftLeg != null)
			{
				// Base splay (negative Z) composed with forward/back swing (X).
				TEMP_QUAT.fromAngleAxis(-baseSplay, Vector3f.UNIT_Z);
				TEMP_QUAT2.fromAngleAxis(phase, Vector3f.UNIT_X);
				TEMP_QUAT.multLocal(TEMP_QUAT2);
				leftLeg.setLocalRotation(TEMP_QUAT);
			}
			if (rightLeg != null)
			{
				// Base splay (positive Z) composed with opposite swing (X).
				TEMP_QUAT.fromAngleAxis(baseSplay, Vector3f.UNIT_Z);
				TEMP_QUAT2.fromAngleAxis(-phase, Vector3f.UNIT_X);
				TEMP_QUAT.multLocal(TEMP_QUAT2);
				rightLeg.setLocalRotation(TEMP_QUAT);
			}
		}
	}
	
	// ------------------------------------------------------------------
	// Slime hop (always active).
	// ------------------------------------------------------------------
	
	/**
	 * Squash-and-stretch hop cycle on the slime's body node.<br>
	 * The body scales vertically while compressing horizontally,<br>
	 * and translates upward during the stretch phase. Always active.
	 */
	private static void animateSlime(Enemy enemy, float time)
	{
		final Node body = enemy.getBody();
		if (body == null)
		{
			return;
		}
		
		final float cycle = FastMath.sin(time * SLIME_HOP_SPEED);
		
		// Scale: stretch Y while compressing XZ.
		final float scaleY = 1.0f + (cycle * SLIME_SCALE_Y_AMP);
		final float scaleXZ = 1.0f - (cycle * SLIME_SCALE_XZ_AMP);
		body.setLocalScale(scaleXZ, scaleY, scaleXZ);
		
		// Translate upward during stretch (positive cycle).
		final float hopY = Math.max(0, cycle * SLIME_HOP_HEIGHT);
		// Body pivot is at Y=0.4 (from factory). Add hop offset.
		body.setLocalTranslation(0, 0.4f + hopY, 0);
	}
	
	// ------------------------------------------------------------------
	// Piranha swim (body sway + tail wag).
	// ------------------------------------------------------------------
	
	/**
	 * Sways the piranha body side to side and wags the tail fin<br>
	 * for a natural swimming motion.
	 */
	private static void animatePiranha(Enemy enemy, float time, float blend)
	{
		final Node body = enemy.getBody();
		if (body == null)
		{
			return;
		}
		
		final float swimBlend = Math.max(blend, 0.4f); // Piranha always swims a little, even "idle".
		final float sway = FastMath.sin(time * PIRANHA_SWIM_SPEED) * PIRANHA_SWAY_AMPLITUDE * swimBlend;
		
		// Sway the whole body around Y axis.
		setYRotation(body, sway);
		
		// Wag the tail fin faster and wider.
		final Spatial tailFin = body.getChild("TailFin");
		if (tailFin != null)
		{
			final float tailWag = FastMath.sin(time * PIRANHA_SWIM_SPEED * 1.5f) * PIRANHA_TAIL_AMPLITUDE * swimBlend;
			setYRotation(tailFin, tailWag);
		}
	}
	
	// ------------------------------------------------------------------
	// Idle bob (all types except slime).
	// ------------------------------------------------------------------
	
	/**
	 * Applies a subtle vertical oscillation to the root node<br>
	 * to prevent a frozen look when the enemy is stationary.<br>
	 * Fades out as the walk blend increases so both don't compete.
	 */
	private static void animateIdle(Enemy enemy, float time, float walkBlend)
	{
		final float idleStrength = 1.0f - walkBlend;
		final float bobY = FastMath.sin(time * IDLE_BOB_SPEED) * IDLE_BOB_AMPLITUDE * idleStrength;
		
		// Offset root node Y relative to the enemy's base position.
		final Node root = enemy.getNode();
		final Vector3f pos = enemy.getPosition();
		root.setLocalTranslation(pos.x, pos.y + bobY, pos.z);
	}
	
	// ------------------------------------------------------------------
	// Death animation (topple sideways with spin -> shrink).
	// ------------------------------------------------------------------
	
	/**
	 * Two-phase death animation:<br>
	 * <b>Phase 1 - Topple + Spin:</b> The enemy falls sideways (Z-axis rotation)<br>
	 * while spinning on the Y axis, using an ease-in curve for a natural gravity feel.<br>
	 * <b>Phase 2 - Shrink:</b> The enemy smoothly scales down to nothing and disappears.<br>
	 * When the animation completes, the enemy is marked as no longer alive<br>
	 * so the spawn system can remove it from the scene.
	 * @param enemy the dying enemy
	 * @param tpf time per frame in seconds
	 */
	private static void animateDeath(Enemy enemy, float tpf)
	{
		final float timer = enemy.getDeathTimer() + tpf;
		enemy.setDeathTimer(timer);
		
		final Node root = enemy.getNode();
		final Vector3f pos = enemy.getPosition();
		
		if (timer <= DEATH_TOPPLE_DURATION)
		{
			// Phase 1: Topple sideways with spin.
			// Ease-in (quadratic) for a gravity-like acceleration.
			final float t = timer / DEATH_TOPPLE_DURATION;
			final float eased = t * t;
			
			// Z-axis: fall sideways.
			final float toppleAngle = eased * DEATH_TOPPLE_ANGLE;
			// Y-axis: spin while falling.
			final float spinAngle = eased * DEATH_SPIN_REVOLUTIONS * FastMath.TWO_PI;
			
			TEMP_QUAT.fromAngleAxis(toppleAngle, Vector3f.UNIT_Z);
			TEMP_QUAT2.fromAngleAxis(spinAngle, Vector3f.UNIT_Y);
			TEMP_QUAT.multLocal(TEMP_QUAT2);
			root.setLocalRotation(TEMP_QUAT);
			root.setLocalTranslation(pos);
			root.setLocalScale(1.0f);
		}
		else if (timer <= DEATH_TOTAL_DURATION)
		{
			// Phase 2: Shrink to nothing.
			final float t = (timer - DEATH_TOPPLE_DURATION) / DEATH_SHRINK_DURATION;
			// Ease-out (inverse quadratic) for a smooth vanish.
			final float eased = 1.0f - t;
			final float scale = eased * eased;
			
			// Keep the toppled rotation.
			TEMP_QUAT.fromAngleAxis(DEATH_TOPPLE_ANGLE, Vector3f.UNIT_Z);
			TEMP_QUAT2.fromAngleAxis(DEATH_SPIN_REVOLUTIONS * FastMath.TWO_PI, Vector3f.UNIT_Y);
			TEMP_QUAT.multLocal(TEMP_QUAT2);
			root.setLocalRotation(TEMP_QUAT);
			root.setLocalTranslation(pos);
			root.setLocalScale(Math.max(scale, 0.01f)); // Clamp to avoid zero-scale artifacts.
		}
		else
		{
			// Animation complete - mark for removal.
			// Set stateTimer high so SpawnSystem's death-linger check passes immediately
			// (the visual death has already played; no need to linger further).
			root.setLocalScale(0.01f);
			enemy.setAlive(false);
			enemy.setStateTimer(999f);
		}
	}
	
	// ------------------------------------------------------------------
	// Rotation helpers.
	// ------------------------------------------------------------------
	
	/**
	 * Sets a spatial's local rotation to a single X-axis angle (radians).<br>
	 * Used for limb swing (legs, arms).
	 */
	private static void setXRotation(Node node, float angleRad)
	{
		if (node == null)
		{
			return;
		}
		
		TEMP_QUAT.fromAngleAxis(angleRad, Vector3f.UNIT_X);
		node.setLocalRotation(TEMP_QUAT);
	}
	
	/**
	 * Sets a spatial's local rotation to a single Y-axis angle (radians).<br>
	 * Used for spider leg sweep and piranha sway.
	 */
	private static void setYRotation(Spatial spatial, float angleRad)
	{
		if (spatial == null)
		{
			return;
		}
		
		TEMP_QUAT.fromAngleAxis(angleRad, Vector3f.UNIT_Y);
		spatial.setLocalRotation(TEMP_QUAT);
	}
	
	// ------------------------------------------------------------------
	// Math helpers.
	// ------------------------------------------------------------------
	
	/**
	 * Linearly moves {@code current} toward {@code target} by at most {@code maxDelta}.
	 * @param current the current value
	 * @param target the target value
	 * @param maxDelta the maximum change per call
	 * @return the new value, clamped to not overshoot target
	 */
	private static float approach(float current, float target, float maxDelta)
	{
		if (current < target)
		{
			return Math.min(current + maxDelta, target);
		}
		
		return Math.max(current - maxDelta, target);
	}
}
