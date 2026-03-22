package simplecraft.world.boss;

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
import simplecraft.state.PlayingState;
import simplecraft.ui.MessageManager;
import simplecraft.world.RegionMeshBuilder;
import simplecraft.world.World;
import simplecraft.world.entity.TileEntityManager;

/**
 * Manages teleportation between the main overworld and the Dragon's Lair boss arena.<br>
 * <br>
 * The arena is generated fresh each time the player uses a Dragon Orb. On entry, the main<br>
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
 * The Dragon Orb is consumed on entry. If the player dies, they must craft another to retry.
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
	
	/** The dragon boss enemy spawned in the arena. */
	private Enemy _dragon;
	
	/** Enemy node for the arena dragon. */
	private Node _arenaEnemyNode;
	
	/** Drop manager for the arena (spawns Recall Orb on dragon death). */
	private DropManager _arenaDropManager;
	
	/** Duration in seconds for the arena entry message. */
	private static final float ARENA_ENTRY_MESSAGE_DURATION = 10.0f;
	
	/** Duration in seconds for the victory message. */
	private static final float VICTORY_MESSAGE_DURATION = 10.0f;
	
	// ========================================================
	// Arena Entry.
	// ========================================================
	
	/**
	 * Teleports the player into the Dragon's Lair boss arena.<br>
	 * Generates a fresh arena, swaps the world, detaches main world nodes<br>
	 * (including enemies and drops) and consumes the Dragon Orb.
	 * @param player the player controller
	 * @param state the playing state for scene management
	 */
	public void enterArena(PlayerController player, PlayingState state)
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
		
		// Point enemy lighting at the arena world so the dragon is lit correctly.
		EnemyLighting.setWorld(_arenaWorld);
		
		// --- Spawn the Dragon ---
		final SimpleCraft spawnApp = SimpleCraft.getInstance();
		_dragon = EnemyFactory.createEnemy(EnemyType.DRAGON, spawnApp.getAssetManager());
		_dragon.initCombat(spawnApp.getAssetManager());
		
		// Position at arena center, same floor height as the player spawn.
		final float dragonX = ArenaGenerator.ARENA_SIZE_X / 2f;
		final float dragonZ = ArenaGenerator.ARENA_SIZE_Z / 2f;
		final float dragonY = ArenaGenerator.PLAYER_SPAWN.y + 0.05f;
		_dragon.setPosition(new Vector3f(dragonX, dragonY, dragonZ));
		
		System.out.println("BossArenaManager: Dragon spawned at [" + dragonX + ", " + dragonY + ", " + dragonZ + "] (arena size: " + ArenaGenerator.ARENA_SIZE_X + "x" + ArenaGenerator.ARENA_SIZE_Z + ")");
		
		// Initialize charge interval for Phase 1 (no charges, but set a default).
		_dragon.setChargeInterval(999f);
		
		// Create arena enemy node and attach dragon.
		_arenaEnemyNode = new Node("ArenaEnemies");
		_arenaEnemyNode.attachChild(_dragon.getNode());
		spawnApp.getRootNode().attachChild(_arenaEnemyNode);
		
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
		MessageManager.show("You have entered the Dragon's Lair.", ARENA_ENTRY_MESSAGE_DURATION);
		
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
		
		// Detach and clean up arena enemy node (dragon).
		if (_arenaEnemyNode != null)
		{
			app.getRootNode().detachChild(_arenaEnemyNode);
			_arenaEnemyNode = null;
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
		
		_dragon = null;
		
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
			MessageManager.show("You return victorious.", VICTORY_MESSAGE_DURATION);
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
	 * Returns the dragon boss enemy, or null if not in arena or dragon is dead.
	 */
	public Enemy getDragon()
	{
		return _dragon;
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
	 * Drives dragon AI, animation, visual effects and drop pickup while in the arena.
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
		
		// Update dragon AI and animation (if dragon is still alive/dying).
		if (_dragon != null && (_dragon.isAlive() || _dragon.isDying()))
		{
			_dragon.update(player.getPosition(), false, world, SimpleCraft.getInstance().getAudioManager(), tpf);
		}
		
		// Always update arena drops - the Recall Orb needs pickup checks
		// even after the dragon is dead and removed from the scene.
		if (_arenaDropManager != null && !player.isDead())
		{
			_arenaDropManager.update(player.getPosition(), player.getInventory(), tpf);
		}
	}
	
	/**
	 * Called when the dragon's death animation completes.<br>
	 * Marks the dragon as fully dead for removal.
	 */
	public void onDragonDeathComplete()
	{
		// Dragon model is cleaned up; just null the reference.
		// The Recall Orb was already spawned when dying began.
		if (_arenaEnemyNode != null && _dragon != null)
		{
			_arenaEnemyNode.detachChild(_dragon.getNode());
		}
		
		_dragon = null;
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
		
		if (_arenaDropManager != null)
		{
			_arenaDropManager.cleanup();
			app.getRootNode().detachChild(_arenaDropManager.getNode());
			_arenaDropManager = null;
		}
		
		_dragon = null;
		_mainWorld = null;
		_mainWorldReturnPos = null;
		_mainWorldTileEntityNode = null;
		_arenaTileEntityNode = null;
		_mainWorldEnemyNode = null;
		_mainWorldDropNode = null;
		_inArena = false;
	}
}
