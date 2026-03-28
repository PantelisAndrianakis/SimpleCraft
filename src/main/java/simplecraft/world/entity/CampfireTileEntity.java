package simplecraft.world.entity;

import com.jme3.effect.ParticleEmitter;
import com.jme3.effect.ParticleMesh;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Node;

import simplecraft.SimpleCraft;
import simplecraft.player.PlayerController;
import simplecraft.ui.MessageManager;
import simplecraft.settings.LanguageManager;
import simplecraft.ui.QuestionManager;
import simplecraft.util.Vector3i;
import simplecraft.world.Block;
import simplecraft.world.World;

/**
 * Campfire tile entity - serves as a respawn point when activated by the player.<br>
 * <br>
 * All placed campfires are always lit (fire particles start immediately on placement).<br>
 * The currently active respawn campfire burns brighter with larger particles, while<br>
 * inactive campfires have dimmer, smaller flames.<br>
 * <br>
 * <b>Interaction:</b><br>
 * Right-clicking an inactive campfire shows a "Set respawn point here?" dialog.<br>
 * Right-clicking the already-active campfire shows a brief status message.<br>
 * Only one campfire can be the active respawn point at a time - activating a new<br>
 * one automatically deactivates the previous one.<br>
 * <br>
 * <b>Block light:</b><br>
 * Inactive campfire emits block light level 12.<br>
 * Active campfire (respawn point) emits block light level 14 (brighter, wider glow).<br>
 * Light propagates through non-solid blocks via the region's block light system.
 * @author Pantelis Andrianakis
 * @since March 8th 2026
 */
public class CampfireTileEntity extends TileEntity
{
	/** Flame effect image path */
	private static final String FLAME_IMAGE_PATH = "assets/images/effects/flame.png";
	
	/** Whether this campfire is the player's current respawn point. */
	private boolean _activated;
	
	/** Block light level when inactive (dim). */
	private static final int LIGHT_LEVEL_INACTIVE = 12;
	
	/** Block light level when active (bright). */
	private static final int LIGHT_LEVEL_ACTIVE = 14;
	
	/** Fire particle emitter. */
	private ParticleEmitter _fireEmitter;
	
	/** Ember particle emitter. */
	private ParticleEmitter _emberEmitter;
	
	// Input handling for QuestionManager keyboard navigation.
	private static final String MAPPING_LEFT = "QuestionNavLeft";
	private static final String MAPPING_RIGHT = "QuestionNavRight";
	private static final String MAPPING_CONFIRM = "QuestionConfirm";
	private static final String MAPPING_BACK = "QuestionBack";
	
	private PlayerController _currentPlayer;
	private SimpleCraft _currentApp;
	
	/**
	 * Creates a new campfire tile entity at the given world position.
	 * @param position world block coordinates
	 */
	public CampfireTileEntity(Vector3i position)
	{
		super(position, Block.CAMPFIRE);
	}
	
	// ========================================================
	// Lifecycle.
	// ========================================================
	
	@Override
	public void onPlaced(World world)
	{
		// Create the visual node that holds all particle emitters.
		_visualNode = new Node("Campfire_" + _position.x + "_" + _position.y + "_" + _position.z);
		_visualNode.setLocalTranslation(_position.x + 0.5f, _position.y + 0.3f, _position.z + 0.5f);
		
		// Create fire particles.
		createFireEmitter();
		
		// Create ember particles.
		createEmberEmitter();
		
		// Apply initial brightness based on activation state.
		applyParticleBrightness();
		
		// Propagate block light.
		propagateLight(world);
	}
	
	@Override
	public void onRemoved(World world)
	{
		// Remove block light contribution.
		removeLight(world);
		
		// Kill particles (TileEntityManager handles removing _visualNode from scene).
		if (_fireEmitter != null)
		{
			_fireEmitter.killAllParticles();
		}
		
		if (_emberEmitter != null)
		{
			_emberEmitter.killAllParticles();
		}
	}
	
	@Override
	public void onInteract(PlayerController player, World world)
	{
		if (_activated)
		{
			// Already the active respawn point - just show a status message.
			MessageManager.show(LanguageManager.get("msg.already_respawn_point"));
			return;
		}
		
		// Don't open if another question dialog is already showing.
		if (QuestionManager.isActive())
		{
			return;
		}
		
		_currentApp = SimpleCraft.getInstance();
		_currentPlayer = player;
		
		// Disable player movement and show cursor while dialog is open.
		player.unregisterInput();
		_currentApp.getInputManager().setCursorVisible(true);
		
		// Register keyboard navigation for the dialog.
		registerQuestionNavigation();
		
		// Show confirmation dialog using the shared QuestionManager.
		QuestionManager.show(LanguageManager.get("msg.set_respawn_here"), () ->
		{
			// Yes - activate campfire and restore controls.
			activate(player, world);
			cleanupDialog();
		}, () ->
		{
			// No - just restore controls.
			cleanupDialog();
		});
	}
	
	// ========================================================
	// Dialog Input Handling.
	// ========================================================
	
	/**
	 * Registers input mappings for keyboard navigation of the question dialog.
	 */
	private void registerQuestionNavigation()
	{
		final InputManager inputManager = _currentApp.getInputManager();
		
		// Add mappings for navigation keys.
		inputManager.addMapping(MAPPING_LEFT, new KeyTrigger(KeyInput.KEY_LEFT), new KeyTrigger(KeyInput.KEY_A));
		inputManager.addMapping(MAPPING_RIGHT, new KeyTrigger(KeyInput.KEY_RIGHT), new KeyTrigger(KeyInput.KEY_D));
		inputManager.addMapping(MAPPING_CONFIRM, new KeyTrigger(KeyInput.KEY_RETURN), new KeyTrigger(KeyInput.KEY_SPACE));
		inputManager.addMapping(MAPPING_BACK, new KeyTrigger(KeyInput.KEY_ESCAPE));
		
		// Add the listener.
		inputManager.addListener(QUESTION_NAV_LISTENER, MAPPING_LEFT, MAPPING_RIGHT, MAPPING_CONFIRM, MAPPING_BACK);
	}
	
	/**
	 * Unregisters input mappings for the question dialog.
	 */
	private void unregisterQuestionNavigation()
	{
		if (_currentApp == null)
		{
			return;
		}
		
		final InputManager inputManager = _currentApp.getInputManager();
		
		inputManager.removeListener(QUESTION_NAV_LISTENER);
		inputManager.deleteMapping(MAPPING_LEFT);
		inputManager.deleteMapping(MAPPING_RIGHT);
		inputManager.deleteMapping(MAPPING_CONFIRM);
		inputManager.deleteMapping(MAPPING_BACK);
	}
	
	/**
	 * Action listener for question dialog navigation.
	 */
	private final ActionListener QUESTION_NAV_LISTENER = (name, isPressed, tpf) ->
	{
		if (!isPressed)
		{
			return;
		}
		
		switch (name)
		{
			case MAPPING_LEFT:
			{
				QuestionManager.navigateLeft();
				break;
			}
			case MAPPING_RIGHT:
			{
				QuestionManager.navigateRight();
				break;
			}
			case MAPPING_CONFIRM:
			{
				QuestionManager.confirmSelection();
				break;
			}
			case MAPPING_BACK:
			{
				// Back acts as No - dismiss dialog and cleanup.
				QuestionManager.dismiss();
				cleanupDialog();
				break;
			}
		}
	};
	
	/**
	 * Cleans up dialog state: unregisters navigation, restores player input, hides cursor.
	 */
	private void cleanupDialog()
	{
		unregisterQuestionNavigation();
		
		if (_currentPlayer != null)
		{
			_currentPlayer.registerInput();
			_currentPlayer = null;
		}
		
		if (_currentApp != null)
		{
			_currentApp.getInputManager().setCursorVisible(false);
			_currentApp = null;
		}
	}
	
	// ========================================================
	// Activation.
	// ========================================================
	
	/**
	 * Activates this campfire as the player's respawn point.<br>
	 * Called by the confirmation dialog when the player clicks Yes.<br>
	 * Deactivates any previously active campfire.
	 * @param player the player controller
	 * @param world the game world
	 */
	public void activate(PlayerController player, World world)
	{
		// Deactivate the previously active campfire if any.
		final TileEntityManager manager = world.getTileEntityManager();
		if (manager != null)
		{
			for (TileEntity entity : manager.getAll())
			{
				if (entity instanceof CampfireTileEntity)
				{
					final CampfireTileEntity other = (CampfireTileEntity) entity;
					if (other._activated && other != this)
					{
						other.deactivate(world);
					}
				}
			}
		}
		
		_activated = true;
		
		// Set the player's campfire respawn point.
		player.setRespawnCampfire();
		
		// Update visuals and light.
		applyParticleBrightness();
		propagateLight(world);
		
		MessageManager.show(LanguageManager.get("msg.respawn_point_set"));
		System.out.println("Campfire activated at [" + _position.x + ", " + _position.y + ", " + _position.z + "]");
	}
	
	/**
	 * Deactivates this campfire (another campfire became the respawn point).<br>
	 * Dims the particles but keeps the fire lit.
	 * @param world the game world
	 */
	public void deactivate(World world)
	{
		_activated = false;
		
		// Update visuals and light.
		applyParticleBrightness();
		propagateLight(world);
		
		System.out.println("Campfire deactivated at [" + _position.x + ", " + _position.y + ", " + _position.z + "]");
	}
	
	/**
	 * Returns true if this campfire is the active respawn point.
	 */
	public boolean isActivated()
	{
		return _activated;
	}
	
	/**
	 * Sets the activation state (used during deserialization).
	 */
	public void setActivated(boolean activated)
	{
		_activated = activated;
	}
	
	// ========================================================
	// Particles.
	// ========================================================
	
	/**
	 * Creates the main fire particle emitter.<br>
	 * Orange/red particles rising from the campfire block top surface.
	 */
	private void createFireEmitter()
	{
		_fireEmitter = new ParticleEmitter("CampfireFire", ParticleMesh.Type.Triangle, 20);
		
		final Material mat = new Material(SimpleCraft.getInstance().getAssetManager(), "Common/MatDefs/Misc/Particle.j3md");
		mat.setTexture("Texture", SimpleCraft.getInstance().getAssetManager().loadTexture(FLAME_IMAGE_PATH));
		mat.getAdditionalRenderState().setBlendMode(BlendMode.Additive);
		_fireEmitter.setMaterial(mat);
		_fireEmitter.setQueueBucket(Bucket.Transparent);
		
		_fireEmitter.setImagesX(2);
		_fireEmitter.setImagesY(2);
		_fireEmitter.setSelectRandomImage(true);
		
		_fireEmitter.setStartSize(0.15f);
		_fireEmitter.setEndSize(0.05f);
		_fireEmitter.setStartColor(new ColorRGBA(1.0f, 0.6f, 0.1f, 0.9f)); // Orange.
		_fireEmitter.setEndColor(new ColorRGBA(1.0f, 0.15f, 0.0f, 0.0f)); // Red, fade out.
		
		_fireEmitter.setLowLife(0.3f);
		_fireEmitter.setHighLife(0.6f);
		
		_fireEmitter.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 1.2f, 0));
		_fireEmitter.getParticleInfluencer().setVelocityVariation(0.3f);
		_fireEmitter.setGravity(0, -0.5f, 0); // Slight upward drift (negative gravity = up).
		
		_fireEmitter.setLocalTranslation(0, 0, 0);
		_fireEmitter.setParticlesPerSec(15);
		
		_visualNode.attachChild(_fireEmitter);
	}
	
	/**
	 * Creates the ember particle emitter.<br>
	 * Tiny yellow dots that drift upward slowly for atmosphere.
	 */
	private void createEmberEmitter()
	{
		_emberEmitter = new ParticleEmitter("CampfireEmbers", ParticleMesh.Type.Triangle, 8);
		
		final Material mat = new Material(SimpleCraft.getInstance().getAssetManager(), "Common/MatDefs/Misc/Particle.j3md");
		mat.setTexture("Texture", SimpleCraft.getInstance().getAssetManager().loadTexture(FLAME_IMAGE_PATH));
		mat.getAdditionalRenderState().setBlendMode(BlendMode.Additive);
		_emberEmitter.setMaterial(mat);
		_emberEmitter.setQueueBucket(Bucket.Transparent);
		
		_emberEmitter.setImagesX(2);
		_emberEmitter.setImagesY(2);
		_emberEmitter.setSelectRandomImage(true);
		
		_emberEmitter.setStartSize(0.04f);
		_emberEmitter.setEndSize(0.01f);
		_emberEmitter.setStartColor(new ColorRGBA(1.0f, 0.9f, 0.3f, 0.8f)); // Yellow.
		_emberEmitter.setEndColor(new ColorRGBA(1.0f, 0.5f, 0.0f, 0.0f)); // Orange, fade.
		
		_emberEmitter.setLowLife(1.0f);
		_emberEmitter.setHighLife(1.8f);
		
		_emberEmitter.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 0.6f, 0));
		_emberEmitter.getParticleInfluencer().setVelocityVariation(0.5f);
		_emberEmitter.setGravity(0, -0.1f, 0);
		
		_emberEmitter.setLocalTranslation(0, 0.1f, 0);
		_emberEmitter.setParticlesPerSec(4);
		
		_visualNode.attachChild(_emberEmitter);
	}
	
	/**
	 * Adjusts particle brightness based on activation state.<br>
	 * Active campfire: brighter, larger particles.<br>
	 * Inactive campfire: dimmer, smaller particles.
	 */
	private void applyParticleBrightness()
	{
		if (_fireEmitter == null)
		{
			return;
		}
		
		if (_activated)
		{
			// Bright active fire.
			_fireEmitter.setStartSize(0.14f);
			_fireEmitter.setEndSize(0.04f);
			_fireEmitter.setStartColor(new ColorRGBA(1.0f, 0.7f, 0.15f, 1.0f));
			_fireEmitter.setEndColor(new ColorRGBA(1.0f, 0.2f, 0.0f, 0.0f));
			_fireEmitter.setParticlesPerSec(12);
			
			if (_emberEmitter != null)
			{
				_emberEmitter.setParticlesPerSec(6);
				_emberEmitter.setStartSize(0.05f);
			}
		}
		else
		{
			// Dimmer inactive fire.
			_fireEmitter.setStartSize(0.12f);
			_fireEmitter.setEndSize(0.04f);
			_fireEmitter.setStartColor(new ColorRGBA(0.8f, 0.45f, 0.08f, 0.7f));
			_fireEmitter.setEndColor(new ColorRGBA(0.8f, 0.1f, 0.0f, 0.0f));
			_fireEmitter.setParticlesPerSec(10);
			
			if (_emberEmitter != null)
			{
				_emberEmitter.setParticlesPerSec(3);
				_emberEmitter.setStartSize(0.03f);
			}
		}
	}
	
	// ========================================================
	// Block Light.
	// ========================================================
	
	/**
	 * Propagates block light from this campfire into the surrounding area.<br>
	 * Active campfire uses a higher light level for a brighter, wider glow.
	 */
	private void propagateLight(World world)
	{
		final int level = _activated ? LIGHT_LEVEL_ACTIVE : LIGHT_LEVEL_INACTIVE;
		world.propagateBlockLight(_position.x, _position.y, _position.z, level);
	}
	
	/**
	 * Removes block light contribution from this campfire.
	 */
	private void removeLight(World world)
	{
		world.removeBlockLight(_position.x, _position.y, _position.z);
	}
	
	public int getLightLevel()
	{
		return _activated ? LIGHT_LEVEL_ACTIVE : LIGHT_LEVEL_INACTIVE;
	}
	
	// ========================================================
	// Serialization.
	// ========================================================
	
	@Override
	public String serialize()
	{
		return super.serialize() + "\nactivated=" + _activated;
	}
}
