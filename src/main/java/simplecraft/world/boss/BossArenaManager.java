package simplecraft.world.boss;

import com.jme3.effect.ParticleEmitter;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

import simplecraft.SimpleCraft;
import simplecraft.enemy.Enemy;
import simplecraft.enemy.Enemy.EnemyType;
import simplecraft.enemy.EnemyFactory;
import simplecraft.enemy.EnemyLighting;
import simplecraft.item.DropManager;
import simplecraft.item.Inventory;
import simplecraft.item.ItemInstance;
import simplecraft.player.PlayerController;
import simplecraft.settings.LanguageManager;
import simplecraft.state.PlayingState;
import simplecraft.ui.MessageManager;
import simplecraft.world.RegionMeshBuilder;
import simplecraft.world.World;
import simplecraft.world.entity.TileEntityManager;

/**
 * Manages teleportation between the main overworld and the boss arena.<br>
 * <br>
 * The arena is generated fresh each time the player uses a boss Orb. On entry, the main<br>
 * world's scene nodes are detached (world, tile entities, enemies, drops) and the arena<br>
 * world is attached in their place. The player keeps their full inventory.<br>
 * <br>
 * On exit (victory via Recall Orb, or death -> respawn), the arena is destroyed and the<br>
 * main world nodes are reattached. The player returns to their pre-teleport position<br>
 * (victory) or respawns at their campfire/initial spawn (death).<br>
 * <br>
 * <b>Death flow:</b> When the player dies in the arena, the death screen is shown with<br>
 * the arena still visible behind it. Only when the player clicks "Respawn" does the<br>
 * arena exit happen, followed by normal respawn in the main world. This avoids the<br>
 * jarring visual of the main world appearing behind the death screen.<br>
 * <br>
 * The boss Orb is consumed on entry. If the player dies, they must craft another to retry.
 * @author Pantelis Andrianakis
 * @since March 22nd 2026
 */
public class BossArenaManager
{
	/** Whether the player is currently in the boss arena. */
	private boolean _inArena;
	
	/** Reference to the main overworld (stored on arena entry, restored on exit). */
	private World _mainWorld;
	
	/** The generated boss arena world. */
	private World _arenaWorld;
	
	/** Player position in the main world before teleporting (for return). */
	private Vector3f _mainWorldReturnPos;
	
	/** The TileEntityManager node from the main world (detached/reattached during swap). */
	private Node _mainWorldTileEntityNode;
	
	/** The arena's TileEntityManager node. */
	private Node _arenaTileEntityNode;
	
	/** The enemy node from the main world (detached to hide enemies in arena). */
	private Node _mainWorldEnemyNode;
	
	/** The drop manager node from the main world (detached to hide drops in arena). */
	private Node _mainWorldDropNode;
	
	/** The boss enemy spawned in the arena. */
	private Enemy _boss;
	
	/** Enemy node for the arena boss. */
	private Node _arenaEnemyNode;
	
	/** Drop manager for the arena (spawns Recall Orb on boss death). */
	private DropManager _arenaDropManager;
	
	/** Fire breath particle emitter for the Shadow boss (attached outside enemy hierarchy to avoid EnemyLighting crash). */
	private ParticleEmitter _fireBreathEmitter;
	
	/** Ambient smoke emitter for the Shadow boss (attached outside enemy hierarchy). */
	private ParticleEmitter _smokeEmitter;
	
	/** Duration in seconds for the arena entry message. */
	private static final float ARENA_ENTRY_MESSAGE_DURATION = 10.0f;
	
	/** Duration in seconds for the victory message. */
	private static final float VICTORY_MESSAGE_DURATION = 10.0f;
	
	// ========================================================
	// Arena Entry.
	// ========================================================
	
	/**
	 * Teleports the player into a Boss arena.<br>
	 * Generates a fresh arena, swaps the world, detaches main world nodes<br>
	 * (including enemies and drops) and consumes the Orb.
	 * @param shadowArena whether the arena spawns a shadow
	 * @param player the player controller
	 * @param state the playing state for scene management
	 */
	public void enterArena(boolean shadowArena, PlayerController player, PlayingState state)
	{
		if (_inArena)
		{
			System.err.println("BossArenaManager: Already in arena - ignoring enter request.");
			return;
		}
		
		final SimpleCraft app = SimpleCraft.getInstance();
		
		// Store main world state.
		_mainWorld = state.getWorld();
		_mainWorldReturnPos = player.getPosition().clone();
		
		// Detach main world scene nodes.
		app.getRootNode().detachChild(_mainWorld.getWorldNode());
		
		final TileEntityManager mainTileEntityManager = _mainWorld.getTileEntityManager();
		if (mainTileEntityManager != null)
		{
			_mainWorldTileEntityNode = mainTileEntityManager.getNode();
			app.getRootNode().detachChild(_mainWorldTileEntityNode);
		}
		
		// Detach main world enemy node (prevents main world enemies from rendering in arena).
		_mainWorldEnemyNode = state.getEnemyNode();
		if (_mainWorldEnemyNode != null)
		{
			app.getRootNode().detachChild(_mainWorldEnemyNode);
		}
		
		// Detach main world drop node (prevents main world drops from rendering in arena).
		_mainWorldDropNode = state.getDropNode();
		if (_mainWorldDropNode != null)
		{
			app.getRootNode().detachChild(_mainWorldDropNode);
		}
		
		// Generate the arena world using the same atlas material.
		_arenaWorld = ArenaGenerator.generate(state.getAtlasMaterial());
		
		// Propagate block light for arena torches.
		_arenaWorld.repropagateAllBlockLights();
		
		// Rebuild meshes now that light is propagated.
		_arenaWorld.rebuildAllLoadedRegions();
		
		// Attach arena world scene nodes.
		app.getRootNode().attachChild(_arenaWorld.getWorldNode());
		
		final TileEntityManager arenaTileEntityManager = _arenaWorld.getTileEntityManager();
		if (arenaTileEntityManager != null)
		{
			_arenaTileEntityNode = arenaTileEntityManager.getNode();
			app.getRootNode().attachChild(_arenaTileEntityNode);
		}
		
		// Set the global tile entity manager for mesh building.
		RegionMeshBuilder.setGlobalTileEntityManager(arenaTileEntityManager);
		
		// Swap world references in player and block interaction.
		player.setWorld(_arenaWorld);
		player.setInBossArena(true);
		state.swapWorld(_arenaWorld);
		
		// Point enemy lighting at the arena world so the boss is lit correctly.
		EnemyLighting.setWorld(_arenaWorld);
		
		// --- Spawn the Boss ---
		final SimpleCraft spawnApp = SimpleCraft.getInstance();
		_boss = EnemyFactory.createEnemy(shadowArena ? EnemyType.SHADOW : EnemyType.DRAGON, spawnApp.getAssetManager());
		_boss.initCombat(spawnApp.getAssetManager());
		
		// Position at arena center, same floor height as the player spawn.
		final float bossX = ArenaGenerator.ARENA_SIZE_X / 2f;
		final float bossZ = ArenaGenerator.ARENA_SIZE_Z / 2f;
		final float bossY = ArenaGenerator.PLAYER_SPAWN.y + 0.05f;
		_boss.setPosition(new Vector3f(bossX, bossY, bossZ));
		
		System.out.println("BossArenaManager: Boss spawned at [" + bossX + ", " + bossY + ", " + bossZ + "] (arena size: " + ArenaGenerator.ARENA_SIZE_X + "x" + ArenaGenerator.ARENA_SIZE_Z + ")");
		
		// Initialize charge interval for Phase 1 (no charges, but set a default).
		_boss.setChargeInterval(999f);
		
		// Create arena enemy node and attach boss.
		_arenaEnemyNode = new Node("ArenaEnemies");
		_arenaEnemyNode.attachChild(_boss.getNode());
		spawnApp.getRootNode().attachChild(_arenaEnemyNode);
		
		// Attach fire breath emitter outside the enemy node hierarchy.
		// EnemyLighting recursively traverses enemy.getNode() and crashes on ParticleEmitter
		// meshes (incompatible vertex color buffer). The emitter's world transform is synced
		// to the boss head each frame in update().
		_fireBreathEmitter = _boss.getFireBreathEmitter();
		if (_fireBreathEmitter != null)
		{
			spawnApp.getRootNode().attachChild(_fireBreathEmitter);
		}
		
		// Attach ambient smoke emitter for Shadow boss (same reason as fire breath).
		_smokeEmitter = _boss.getSmokeEmitter();
		if (_smokeEmitter != null)
		{
			spawnApp.getRootNode().attachChild(_smokeEmitter);
		}
		
		// Create arena drop manager for Recall Orb drop.
		_arenaDropManager = new DropManager(spawnApp.getAssetManager(), spawnApp.getAudioManager(), state.getAtlasMaterial());
		spawnApp.getRootNode().attachChild(_arenaDropManager.getNode());
		
		// Wire arena drop manager to combat system.
		if (state.getCombatSystem() != null)
		{
			state.getCombatSystem().setDropManager(_arenaDropManager);
		}
		
		// Teleport player to entrance alcove.
		final float spawnX = ArenaGenerator.PLAYER_SPAWN.x + 0.5f;
		final float spawnY = ArenaGenerator.PLAYER_SPAWN.y;
		final float spawnZ = ArenaGenerator.PLAYER_SPAWN.z + 0.5f;
		player.setPosition(spawnX, spawnY, spawnZ);
		
		// Face toward arena center.
		final float dx = ArenaGenerator.ARENA_SIZE_X / 2f - spawnX;
		final float dz = ArenaGenerator.ARENA_SIZE_Z / 2f - spawnZ;
		final float yaw = (float) Math.atan2(dx, dz);
		app.getCamera().lookAtDirection(new Vector3f(-(float) Math.sin(yaw), 0, (float) Math.cos(yaw)), Vector3f.UNIT_Y);
		
		_inArena = true;
		
		// Show dramatic entry message.
		MessageManager.show(shadowArena ? "You have entered the Shadow Lair." : "You have entered the Dragon's Lair.", ARENA_ENTRY_MESSAGE_DURATION);
		
		System.out.println("BossArenaManager: Entered arena. Return pos: [" + _mainWorldReturnPos.x + ", " + _mainWorldReturnPos.y + ", " + _mainWorldReturnPos.z + "]");
	}
	
	// ========================================================
	// Arena Exit.
	// ========================================================
	
	/**
	 * Exits the boss arena and returns the player to the main world.<br>
	 * Reattaches all main world scene nodes (world, tile entities, enemies, drops).
	 * @param player the player controller
	 * @param state the playing state for scene management
	 * @param victory true if the dragon was slain, false if the player died
	 */
	public void exitArena(PlayerController player, PlayingState state, boolean victory)
	{
		if (!_inArena)
		{
			System.err.println("BossArenaManager: Not in arena - ignoring exit request.");
			return;
		}
		
		final SimpleCraft app = SimpleCraft.getInstance();
		
		// Detach arena world scene nodes.
		if (_arenaWorld != null)
		{
			app.getRootNode().detachChild(_arenaWorld.getWorldNode());
		}
		
		if (_arenaTileEntityNode != null)
		{
			app.getRootNode().detachChild(_arenaTileEntityNode);
			_arenaTileEntityNode = null;
		}
		
		// Reattach main world scene nodes.
		app.getRootNode().attachChild(_mainWorld.getWorldNode());
		
		if (_mainWorldTileEntityNode != null)
		{
			app.getRootNode().attachChild(_mainWorldTileEntityNode);
		}
		
		// Reattach enemy and drop nodes.
		if (_mainWorldEnemyNode != null)
		{
			app.getRootNode().attachChild(_mainWorldEnemyNode);
		}
		
		if (_mainWorldDropNode != null)
		{
			app.getRootNode().attachChild(_mainWorldDropNode);
		}
		
		// Detach and clean up arena enemy node (boss).
		if (_arenaEnemyNode != null)
		{
			app.getRootNode().detachChild(_arenaEnemyNode);
			_arenaEnemyNode = null;
		}
		
		// Detach fire breath emitter (attached to root, not enemy hierarchy).
		if (_fireBreathEmitter != null)
		{
			_fireBreathEmitter.killAllParticles();
			app.getRootNode().detachChild(_fireBreathEmitter);
			_fireBreathEmitter = null;
		}
		
		// Detach smoke emitter.
		if (_smokeEmitter != null)
		{
			_smokeEmitter.killAllParticles();
			app.getRootNode().detachChild(_smokeEmitter);
			_smokeEmitter = null;
		}
		
		// Detach and clean up arena drop manager.
		if (_arenaDropManager != null)
		{
			_arenaDropManager.cleanup();
			app.getRootNode().detachChild(_arenaDropManager.getNode());
			_arenaDropManager = null;
		}
		
		// Restore main world drop manager in combat system.
		if (state.getMainDropManager() != null && state.getCombatSystem() != null)
		{
			state.getCombatSystem().setDropManager(state.getMainDropManager());
		}
		
		_boss = null;
		
		// Restore the global tile entity manager.
		RegionMeshBuilder.setGlobalTileEntityManager(_mainWorld.getTileEntityManager());
		
		// Swap world references back.
		player.setWorld(_mainWorld);
		player.setInBossArena(false);
		state.swapWorld(_mainWorld);
		
		// Restore enemy lighting to the main world.
		EnemyLighting.setWorld(_mainWorld);
		
		// Teleport player back to their pre-arena position (victory only).
		if (victory)
		{
			player.setPosition(_mainWorldReturnPos.x, _mainWorldReturnPos.y, _mainWorldReturnPos.z);
			MessageManager.show(LanguageManager.get("msg.victory"), VICTORY_MESSAGE_DURATION);
			System.out.println("BossArenaManager: Exited arena (VICTORY). Player returned to main world.");
		}
		else
		{
			// Death exit - player will respawn via normal respawn chain in the main world.
			// Remove any Recall Orb from inventory - it must not persist outside the arena.
			final Inventory inventory = player.getInventory();
			for (int i = 0; i < 36; i++) // 36 total slots: 0-8 hotbar, 9-35 main.
			{
				final ItemInstance item = inventory.getSlot(i);
				if (item != null && "golden_orb".equals(item.getTemplate().getId()))
				{
					inventory.setSlot(i, null);
					System.out.println("BossArenaManager: Removed Recall Orb from slot " + i + " on death exit.");
				}
			}
			
			System.out.println("BossArenaManager: Exited arena (DEATH). Player will respawn in main world.");
		}
		
		// Dispose arena world.
		if (_arenaWorld != null)
		{
			_arenaWorld.shutdown();
			_arenaWorld = null;
		}
		
		_mainWorld = null;
		_mainWorldReturnPos = null;
		_mainWorldTileEntityNode = null;
		_mainWorldEnemyNode = null;
		_mainWorldDropNode = null;
		_inArena = false;
	}
	
	// ========================================================
	// Accessors.
	// ========================================================
	
	/**
	 * Returns true if the player is currently in the boss arena.
	 */
	public boolean isInArena()
	{
		return _inArena;
	}
	
	/**
	 * Returns the arena world, or null if not in arena.
	 */
	public World getArenaWorld()
	{
		return _arenaWorld;
	}
	
	/**
	 * Returns the boss enemy, or null if not in arena or boss is dead.
	 */
	public Enemy getBoss()
	{
		return _boss;
	}
	
	/**
	 * Returns the arena drop manager, or null if not in arena.
	 */
	public DropManager getArenaDropManager()
	{
		return _arenaDropManager;
	}
	
	/**
	 * Per-frame update for the boss arena.<br>
	 * Drives boss AI, animation, visual effects and drop pickup while in the arena.
	 * @param player the player controller
	 * @param world the arena world
	 * @param tpf time per frame
	 */
	public void update(PlayerController player, World world, float tpf)
	{
		if (!_inArena)
		{
			return;
		}
		
		// Update boss AI and animation (if boss is still alive/dying).
		if (_boss != null && (_boss.isAlive() || _boss.isDying()))
		{
			_boss.update(player.getPosition(), false, world, SimpleCraft.getInstance().getAudioManager(), tpf);
			
			// Sync fire breath emitter world transform to the boss.
			// The emitter lives outside the enemy node hierarchy (to avoid EnemyLighting crash)
			// so we manually position it each frame.
			if (_fireBreathEmitter != null && _boss.getHead() != null)
			{
				// Force world transform update so getWorldTranslation() reflects the current frame's AI rotation (not the previous frame's cached value).
				_boss.getNode().updateGeometricState();
				
				final Node head = _boss.getHead();
				final Quaternion headWorldRot = head.getWorldRotation();
				
				if (_boss.getType() == EnemyType.DRAGON)
				{
					// Dragon's head is 2.1 blocks forward from feet. At bite range (3.0), the snout tip is at/past the player — fire spawning there is invisible.
					// Anchor fire at the body/neck area instead: use the body world position with a small forward offset so the fire stream runs from the neck,
					// through the open jaw, toward the player (3+ blocks of visible stream).
					final Node body = _boss.getBody();
					final Vector3f bodyWorldPos = (body != null) ? body.getWorldTranslation() : _boss.getNode().getWorldTranslation();
					final Vector3f neckOffset = headWorldRot.mult(new Vector3f(0, 0.3f, -3f));
					_fireBreathEmitter.setLocalTranslation(bodyWorldPos.add(neckOffset));
				}
				else
				{
					// Shadow: head is close to the body (z=-0.05), so anchoring at the
					// head with a mouth offset works perfectly.
					final Vector3f headWorldPos = head.getWorldTranslation();
					final Vector3f mouthOffset = headWorldRot.mult(new Vector3f(0, 0.1f, -0.45f));
					_fireBreathEmitter.setLocalTranslation(headWorldPos.add(mouthOffset));
				}
				
				_fireBreathEmitter.setLocalRotation(headWorldRot);
				
				// JME3 particle velocities are in world space and NOT rotated by the emitter's local rotation.
				// Update initial velocity direction each frame so fire shoots forward from the mouth in the direction the boss is facing.
				final Vector3f fireVelocity = headWorldRot.mult(new Vector3f(0, 0.5f, -8.0f));
				_fireBreathEmitter.getParticleInfluencer().setInitialVelocity(fireVelocity);
			}
			
			// Sync smoke emitter to the boss body (torso area, slightly above center).
			// Stop emitting when the boss starts dying - particles fade out naturally.
			if (_smokeEmitter != null && _boss.getBody() != null)
			{
				if (_boss.isDying())
				{
					_smokeEmitter.setParticlesPerSec(0);
				}
				
				final Vector3f bodyWorldPos = _boss.getBody().getWorldTranslation();
				_smokeEmitter.setLocalTranslation(bodyWorldPos.x, bodyWorldPos.y + 0.5f, bodyWorldPos.z);
			}
		}
		
		// Always update arena drops - the Recall Orb needs pickup checks
		// even after the boss is dead and removed from the scene.
		if (_arenaDropManager != null && !player.isDead())
		{
			_arenaDropManager.update(player.getPosition(), player.getInventory(), tpf);
		}
	}
	
	/**
	 * Called when the boss's death animation completes.<br>
	 * Marks the boss as fully dead for removal.
	 */
	public void onBossDeathComplete()
	{
		// Boss model is cleaned up; just null the reference.
		// The Recall Orb was already spawned when dying began.
		if (_arenaEnemyNode != null && _boss != null)
		{
			_arenaEnemyNode.detachChild(_boss.getNode());
		}
		
		// Stop and detach particle emitters (smoke lingers visually after death otherwise).
		final SimpleCraft app = SimpleCraft.getInstance();
		
		if (_smokeEmitter != null)
		{
			_smokeEmitter.setParticlesPerSec(0);
			_smokeEmitter.killAllParticles();
			app.getRootNode().detachChild(_smokeEmitter);
			_smokeEmitter = null;
		}
		
		if (_fireBreathEmitter != null)
		{
			_fireBreathEmitter.setParticlesPerSec(0);
			_fireBreathEmitter.killAllParticles();
			app.getRootNode().detachChild(_fireBreathEmitter);
			_fireBreathEmitter = null;
		}
		
		_boss = null;
	}
	
	/**
	 * Cleans up any arena resources.<br>
	 * Called when leaving the game session entirely (quit to menu).
	 */
	public void cleanup()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		
		if (_arenaWorld != null)
		{
			_arenaWorld.shutdown();
			_arenaWorld = null;
		}
		
		if (_arenaEnemyNode != null)
		{
			app.getRootNode().detachChild(_arenaEnemyNode);
			_arenaEnemyNode = null;
		}
		
		if (_fireBreathEmitter != null)
		{
			_fireBreathEmitter.killAllParticles();
			app.getRootNode().detachChild(_fireBreathEmitter);
			_fireBreathEmitter = null;
		}
		
		if (_smokeEmitter != null)
		{
			_smokeEmitter.killAllParticles();
			app.getRootNode().detachChild(_smokeEmitter);
			_smokeEmitter = null;
		}
		
		if (_arenaDropManager != null)
		{
			_arenaDropManager.cleanup();
			app.getRootNode().detachChild(_arenaDropManager.getNode());
			_arenaDropManager = null;
		}
		
		_boss = null;
		_mainWorld = null;
		_mainWorldReturnPos = null;
		_mainWorldTileEntityNode = null;
		_arenaTileEntityNode = null;
		_mainWorldEnemyNode = null;
		_mainWorldDropNode = null;
		_inArena = false;
	}
}
