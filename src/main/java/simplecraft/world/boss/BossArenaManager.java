package simplecraft.world.boss;

import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

import simplecraft.SimpleCraft;
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
		
		// Teleport player to entrance alcove.
		final float spawnX = ArenaGenerator.PLAYER_SPAWN.x + 0.5f;
		final float spawnY = ArenaGenerator.PLAYER_SPAWN.y;
		final float spawnZ = ArenaGenerator.PLAYER_SPAWN.z + 0.5f;
		player.setPosition(spawnX, spawnY, spawnZ);
		
		// Face toward arena center.
		final float dx = ArenaGenerator.ARENA_SIZE_X / 2f - spawnX;
		final float dz = ArenaGenerator.ARENA_SIZE_Z / 2f - spawnZ;
		final float yaw = (float) Math.atan2(dx, dz);
		app.getCamera().lookAtDirection(new Vector3f((float) Math.sin(yaw), 0, (float) Math.cos(yaw)), Vector3f.UNIT_Y);
		
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
		
		// Restore the global tile entity manager.
		RegionMeshBuilder.setGlobalTileEntityManager(_mainWorld.getTileEntityManager());
		
		// Swap world references back.
		player.setWorld(_mainWorld);
		player.setInBossArena(false);
		state.swapWorld(_mainWorld);
		
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
	 * Cleans up any arena resources.<br>
	 * Called when leaving the game session entirely (quit to menu).
	 */
	public void cleanup()
	{
		if (_arenaWorld != null)
		{
			_arenaWorld.shutdown();
			_arenaWorld = null;
		}
		
		_mainWorld = null;
		_mainWorldReturnPos = null;
		_mainWorldTileEntityNode = null;
		_arenaTileEntityNode = null;
		_mainWorldEnemyNode = null;
		_mainWorldDropNode = null;
		_inArena = false;
	}
}
