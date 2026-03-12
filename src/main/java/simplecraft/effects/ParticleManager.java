package simplecraft.effects;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.jme3.asset.AssetManager;
import com.jme3.effect.ParticleEmitter;
import com.jme3.effect.ParticleMesh;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

import simplecraft.world.Block;

/**
 * Manages particle effects for block breaking and combat damage.<br>
 * <br>
 * Uses jME3 {@link ParticleEmitter} in burst mode: particles are emitted once<br>
 * via {@code emitAllParticles()}, then the emitter is auto-removed after all<br>
 * particles have died. A maximum of {@value #MAX_ACTIVE_EMITTERS} emitters can<br>
 * be active simultaneously — the oldest is removed if the limit is exceeded.<br>
 * <br>
 * <b>Block break particles:</b> 8–12 small colored quads fly outward with gravity.<br>
 * Color is derived from the block type via {@link Block#getParticleColor()}.<br>
 * <br>
 * <b>Damage particles:</b> 4–6 small red quads fly upward briefly at the hit point.
 * @author Pantelis Andrianakis
 * @since March 12th 2026
 */
public class ParticleManager
{
	// ========================================================
	// Constants.
	// ========================================================
	
	/** Maximum number of simultaneously active particle emitters. */
	private static final int MAX_ACTIVE_EMITTERS = 10;
	
	// -- Block break particle settings --
	
	/** Minimum number of particles for a block break burst. */
	private static final int BREAK_PARTICLES_MIN = 8;
	
	/** Maximum number of particles for a block break burst. */
	private static final int BREAK_PARTICLES_MAX = 12;
	
	/** Size of each block break particle quad. */
	private static final float BREAK_PARTICLE_SIZE = 0.1f;
	
	/** Minimum lifetime of a block break particle in seconds. */
	private static final float BREAK_LIFETIME_MIN = 0.3f;
	
	/** Maximum lifetime of a block break particle in seconds. */
	private static final float BREAK_LIFETIME_MAX = 0.8f;
	
	/** Initial velocity magnitude for block break particles. */
	private static final float BREAK_VELOCITY = 2.0f;
	
	/** Velocity variation for block break particles. */
	private static final float BREAK_VELOCITY_VARIATION = 1.5f;
	
	// -- Damage particle settings --
	
	/** Number of particles for a damage hit burst. */
	private static final int DAMAGE_PARTICLES_MIN = 4;
	
	/** Maximum number of particles for a damage hit burst. */
	private static final int DAMAGE_PARTICLES_MAX = 6;
	
	/** Size of each damage particle quad. */
	private static final float DAMAGE_PARTICLE_SIZE = 0.08f;
	
	/** Minimum lifetime of a damage particle in seconds. */
	private static final float DAMAGE_LIFETIME_MIN = 0.2f;
	
	/** Maximum lifetime of a damage particle in seconds. */
	private static final float DAMAGE_LIFETIME_MAX = 0.5f;
	
	/** Initial upward velocity for damage particles. */
	private static final float DAMAGE_VELOCITY = 1.5f;
	
	/** Velocity variation for damage particles. */
	private static final float DAMAGE_VELOCITY_VARIATION = 1.0f;
	
	/** Color for damage particles (red). */
	private static final ColorRGBA DAMAGE_COLOR = new ColorRGBA(0.9f, 0.1f, 0.1f, 1.0f);
	
	/** Gravity applied to all particles. */
	private static final Vector3f GRAVITY = new Vector3f(0, -10f, 0);
	
	/** Slight upward gravity for damage particles (fly up then fall). */
	private static final Vector3f DAMAGE_GRAVITY = new Vector3f(0, -6f, 0);
	
	// ========================================================
	// Fields.
	// ========================================================
	
	/** Scene node that holds all active particle emitters. */
	private final Node _particleNode = new Node("Particles");
	
	/** The asset manager for creating materials. */
	private final AssetManager _assetManager;
	
	/** Tracks active emitters with their remaining lifetime for auto-cleanup. */
	private final List<EmitterEntry> _activeEmitters = new ArrayList<>();
	
	/** Running counter for unique emitter names. */
	private int _emitterCounter;
	
	// ========================================================
	// Inner Class.
	// ========================================================
	
	/**
	 * Tracks an active emitter and its maximum lifetime for auto-removal.
	 */
	private static class EmitterEntry
	{
		final ParticleEmitter emitter;
		final float maxLifetime;
		float elapsed;
		
		EmitterEntry(ParticleEmitter emitter, float maxLifetime)
		{
			this.emitter = emitter;
			this.maxLifetime = maxLifetime;
			this.elapsed = 0;
		}
	}
	
	// ========================================================
	// Constructor.
	// ========================================================
	
	/**
	 * Creates the particle manager.
	 * @param assetManager the jME3 asset manager for material creation
	 */
	public ParticleManager(AssetManager assetManager)
	{
		_assetManager = assetManager;
	}
	
	// ========================================================
	// Public API.
	// ========================================================
	
	/**
	 * Returns the scene node containing all active particle emitters.<br>
	 * Attach this to rootNode in PlayingState.
	 * @return the particle node
	 */
	public Node getNode()
	{
		return _particleNode;
	}
	
	/**
	 * Spawns a burst of block-colored particles at the given position.<br>
	 * Particles fly outward randomly and fall with gravity.
	 * @param position the world position (center of the broken block)
	 * @param blockType the block type (used for particle color)
	 */
	public void spawnBlockBreak(Vector3f position, Block blockType)
	{
		final int count = BREAK_PARTICLES_MIN + (int) (Math.random() * (BREAK_PARTICLES_MAX - BREAK_PARTICLES_MIN + 1));
		final ColorRGBA color = blockType.getParticleColor();
		
		final ParticleEmitter emitter = createEmitter("BlockBreak_" + _emitterCounter++, count, BREAK_PARTICLE_SIZE, BREAK_LIFETIME_MIN, BREAK_LIFETIME_MAX, color, color, GRAVITY);
		
		// Particles fly outward in all directions.
		emitter.getParticleInfluencer().setInitialVelocity(new Vector3f(0, BREAK_VELOCITY, 0));
		emitter.getParticleInfluencer().setVelocityVariation(BREAK_VELOCITY_VARIATION);
		
		emitter.setLocalTranslation(position.x + 0.5f, position.y + 0.5f, position.z + 0.5f);
		
		addEmitter(emitter, BREAK_LIFETIME_MAX);
	}
	
	/**
	 * Spawns a burst of red particles at the given position.<br>
	 * Particles fly upward slightly and fade quickly.
	 * @param position the world position of the hit
	 */
	public void spawnDamage(Vector3f position)
	{
		final int count = DAMAGE_PARTICLES_MIN + (int) (Math.random() * (DAMAGE_PARTICLES_MAX - DAMAGE_PARTICLES_MIN + 1));
		
		final ParticleEmitter emitter = createEmitter("Damage_" + _emitterCounter++, count, DAMAGE_PARTICLE_SIZE, DAMAGE_LIFETIME_MIN, DAMAGE_LIFETIME_MAX, DAMAGE_COLOR, DAMAGE_COLOR, DAMAGE_GRAVITY);
		
		// Particles fly upward and slightly outward.
		emitter.getParticleInfluencer().setInitialVelocity(new Vector3f(0, DAMAGE_VELOCITY, 0));
		emitter.getParticleInfluencer().setVelocityVariation(DAMAGE_VELOCITY_VARIATION);
		
		emitter.setLocalTranslation(position);
		
		addEmitter(emitter, DAMAGE_LIFETIME_MAX);
	}
	
	/**
	 * Per-frame update. Removes emitters whose particles have fully died.
	 * @param tpf time per frame in seconds
	 */
	public void update(float tpf)
	{
		final Iterator<EmitterEntry> it = _activeEmitters.iterator();
		while (it.hasNext())
		{
			final EmitterEntry entry = it.next();
			entry.elapsed += tpf;
			
			if (entry.elapsed >= entry.maxLifetime)
			{
				_particleNode.detachChild(entry.emitter);
				it.remove();
			}
		}
	}
	
	/**
	 * Removes all active particle emitters and clears the list.
	 */
	public void cleanup()
	{
		for (int i = 0; i < _activeEmitters.size(); i++)
		{
			_particleNode.detachChild(_activeEmitters.get(i).emitter);
		}
		_activeEmitters.clear();
	}
	
	// ========================================================
	// Internal Helpers.
	// ========================================================
	
	/**
	 * Creates a burst-mode particle emitter with the given parameters.
	 */
	private ParticleEmitter createEmitter(String name, int numParticles, float particleSize, float lifetimeMin, float lifetimeMax, ColorRGBA startColor, ColorRGBA endColor, Vector3f gravity)
	{
		final ParticleEmitter emitter = new ParticleEmitter(name, ParticleMesh.Type.Triangle, numParticles);
		
		// Solid color material (no texture).
		final Material mat = new Material(_assetManager, "Common/MatDefs/Misc/Particle.j3md");
		mat.setBoolean("PointSprite", false);
		mat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
		emitter.setMaterial(mat);
		
		// Particle properties.
		emitter.setImagesX(1);
		emitter.setImagesY(1);
		emitter.setStartColor(startColor);
		emitter.setEndColor(new ColorRGBA(endColor.r, endColor.g, endColor.b, 0.0f));
		emitter.setStartSize(particleSize);
		emitter.setEndSize(particleSize * 0.5f);
		emitter.setLowLife(lifetimeMin);
		emitter.setHighLife(lifetimeMax);
		emitter.setGravity(gravity);
		
		// Burst mode: emit all at once, then stop.
		emitter.setParticlesPerSec(0);
		
		return emitter;
	}
	
	/**
	 * Adds an emitter to the scene and tracking list.<br>
	 * Enforces the maximum active emitter limit by removing the oldest if needed.
	 */
	private void addEmitter(ParticleEmitter emitter, float maxLifetime)
	{
		// Enforce limit — remove oldest emitter if at capacity.
		while (_activeEmitters.size() >= MAX_ACTIVE_EMITTERS)
		{
			final EmitterEntry oldest = _activeEmitters.remove(0);
			_particleNode.detachChild(oldest.emitter);
		}
		
		_particleNode.attachChild(emitter);
		emitter.emitAllParticles();
		_activeEmitters.add(new EmitterEntry(emitter, maxLifetime));
	}
}
