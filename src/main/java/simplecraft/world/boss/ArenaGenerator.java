package simplecraft.world.boss;

import com.jme3.material.Material;

import simplecraft.util.Vector3i;
import simplecraft.world.Block;
import simplecraft.world.Block.Face;
import simplecraft.world.Region;
import simplecraft.world.World;
import simplecraft.world.entity.DoorTileEntity;
import simplecraft.world.entity.TileEntity.Facing;
import simplecraft.world.entity.TileEntityManager;
import simplecraft.world.entity.TorchTileEntity;

/**
 * Generates the Dragon's Lair - a massive sealed underground boss arena.<br>
 * <br>
 * Arena dimensions: 401×80×401 blocks. All solid blocks are BEDROCK (indestructible).<br>
 * <br>
 * Structure:<br>
 * - Sealed BEDROCK shell: 10-block thick walls, floor and ceiling. No entrance - player teleports in.<br>
 * - Clear central arena (120×120) for the boss fight with a raised border ring.<br>
 * - Four castle towers enclosing helicoid staircases. Steps extend wall-to-wall (no gaps).<br>
 * - Each tower has a door at the base and a door at the top leading to a balcony.<br>
 * - Block above each door is solid BEDROCK (no gap).<br>
 * - Torches on inner tower walls and around the arena border.
 * @author Pantelis Andrianakis
 * @since March 22nd 2026
 */
public class ArenaGenerator
{
	// ========================================================
	// Arena Constants.
	// ========================================================
	
	/** Arena width/depth in blocks. */
	public static final int ARENA_SIZE_X = 401;
	public static final int ARENA_SIZE_Y = 80;
	public static final int ARENA_SIZE_Z = 401;
	
	/** Thickness of the bedrock perimeter walls. */
	private static final int WALL_THICKNESS = 10;
	
	/** First/last interior coordinates (just inside the walls). */
	private static final int INTERIOR_MIN = WALL_THICKNESS;
	private static final int INTERIOR_MAX = ARENA_SIZE_X - 1 - WALL_THICKNESS;
	
	/** Ceiling Y. */
	private static final int CEILING_Y = ARENA_SIZE_Y - 1;
	
	/** Arena center. */
	private static final int CENTER_X = ARENA_SIZE_X / 2;
	private static final int CENTER_Z = ARENA_SIZE_Z / 2;
	
	// Central arena fight zone.
	private static final int ARENA_HALF = 60;
	private static final int ARENA_MIN_X = CENTER_X - ARENA_HALF;
	private static final int ARENA_MAX_X = CENTER_X + ARENA_HALF;
	private static final int ARENA_MIN_Z = CENTER_Z - ARENA_HALF;
	private static final int ARENA_MAX_Z = CENTER_Z + ARENA_HALF;
	
	/** Player spawn - SW corner of the arena, facing the center. */
	public static final Vector3i PLAYER_SPAWN = new Vector3i(ARENA_MIN_X - 5, 2, ARENA_MIN_Z - 5);
	
	// ========================================================
	// Tower Constants.
	// ========================================================
	
	/** Tower outer radius (circular wall). */
	private static final int TOWER_OUTER_RADIUS = 12;
	
	/** Tower wall thickness. */
	private static final int TOWER_WALL = 2;
	
	/** Tower interior radius (playable space). */
	private static final int TOWER_INNER_RADIUS = TOWER_OUTER_RADIUS - TOWER_WALL;
	
	/** Staircase fills the entire tower interior - no gap to fall through. */
	private static final int HELIX_OUTER_RADIUS = TOWER_INNER_RADIUS;
	
	/** Central column radius. */
	private static final int HELIX_COLUMN_RADIUS = 3;
	
	/** Number of 1-block-high steps from base to roof. */
	private static final int NUM_STAIRS = 50;
	
	/** Number of 1-block-high steps in each helix. */
	private static final int HELIX_HEIGHT = 45;
	
	/** Steps per full revolution. */
	private static final int HELIX_STEPS_PER_REV = 16;
	
	/**
	 * Angular half-width of each step - must be narrow enough to guarantee 2-block headroom. With 16 steps/rev (22.5° spacing),<br>
	 * a half-width of 20° means steps 3 apart (67.5°) never overlap -> 3 blocks free above.
	 */
	private static final double STEP_HALF_WIDTH = Math.toRadians(20);
	
	/** Tower base Y (first step). */
	private static final int TOWER_BASE_Y = 2;
	
	/** Landing platform Y at the top of the helix. */
	private static final int LANDING_Y = TOWER_BASE_Y + HELIX_HEIGHT;
	
	/** Tower top Y (ceiling). */
	private static final int TOWER_TOP_Y = LANDING_Y + 5;
	
	/**
	 * Tower definitions: {centerX, centerZ, doorDirX, doorDirZ}.<br>
	 * doorDir points from tower center toward the arena center.
	 */
	private static final int[][] TOWERS =
	{
		// @formatter:off
		{ 80, 80, 1, 1 },
		{ 80, 320, 1, -1 },
		{ 320, 80, -1, 1 },
		{ 320, 320, -1, -1 }
		// @formatter:on
	};
	
	/** Wall torch spacing along arena perimeter. */
	private static final int WALL_TORCH_SPACING = 30;
	
	/** Regions needed per axis. */
	private static final int REGIONS_PER_AXIS = (ARENA_SIZE_X + Region.SIZE_XZ - 1) / Region.SIZE_XZ;
	
	// ========================================================
	// Generation.
	// ========================================================
	
	/**
	 * Generates the boss arena.
	 * @param sharedMaterial the atlas material
	 * @return a fully built static World
	 */
	public static World generate(Material sharedMaterial)
	{
		final long startTime = System.currentTimeMillis();
		
		final World arenaWorld = new World(0, sharedMaterial);
		arenaWorld.setStaticWorld(true);
		
		for (int rx = 0; rx < REGIONS_PER_AXIS; rx++)
		{
			for (int rz = 0; rz < REGIONS_PER_AXIS; rz++)
			{
				arenaWorld.createEmptyRegion(rx, rz);
			}
		}
		
		// Build structure.
		fillShell(arenaWorld);
		fillCentralArena(arenaWorld);
		
		// Build towers.
		final TileEntityManager manager = arenaWorld.getTileEntityManager();
		for (int[] t : TOWERS)
		{
			generateTower(arenaWorld, manager, t[0], t[1], t[2], t[3]);
		}
		
		// Torches.
		placePerimeterTorches(arenaWorld, manager);
		placeArenaTorches(arenaWorld, manager);
		
		// Build meshes.
		arenaWorld.rebuildAllLoadedRegions();
		
		System.out.println("ArenaGenerator: Dragon's Lair generated (" + ARENA_SIZE_X + "x" + ARENA_SIZE_Y + "x" + ARENA_SIZE_Z + ") in " + (System.currentTimeMillis() - startTime) + "ms.");
		return arenaWorld;
	}
	
	// ========================================================
	// Shell.
	// ========================================================
	
	private static void fillShell(World world)
	{
		// Floor (y=0, y=1) and ceiling across entire footprint.
		for (int x = 0; x < ARENA_SIZE_X; x++)
		{
			for (int z = 0; z < ARENA_SIZE_Z; z++)
			{
				world.setBlockNoRebuild(x, 0, z, Block.BEDROCK);
				world.setBlockNoRebuild(x, 1, z, Block.BEDROCK);
				world.setBlockNoRebuild(x, CEILING_Y, z, Block.BEDROCK);
			}
		}
		
		// South/north walls.
		for (int x = 0; x < ARENA_SIZE_X; x++)
		{
			for (int y = 2; y < CEILING_Y; y++)
			{
				for (int w = 0; w < WALL_THICKNESS; w++)
				{
					world.setBlockNoRebuild(x, y, w, Block.BEDROCK);
					world.setBlockNoRebuild(x, y, ARENA_SIZE_Z - 1 - w, Block.BEDROCK);
				}
			}
		}
		
		// West/east walls.
		for (int z = WALL_THICKNESS; z < ARENA_SIZE_Z - WALL_THICKNESS; z++)
		{
			for (int y = 2; y < CEILING_Y; y++)
			{
				for (int w = 0; w < WALL_THICKNESS; w++)
				{
					world.setBlockNoRebuild(w, y, z, Block.BEDROCK);
					world.setBlockNoRebuild(ARENA_SIZE_X - 1 - w, y, z, Block.BEDROCK);
				}
			}
		}
	}
	
	// ========================================================
	// Central Arena.
	// ========================================================
	
	private static void fillCentralArena(World world)
	{
		for (int x = ARENA_MIN_X - 2; x <= ARENA_MAX_X + 2; x++)
		{
			for (int z = ARENA_MIN_Z - 2; z <= ARENA_MAX_Z + 2; z++)
			{
				if (x < ARENA_MIN_X || x > ARENA_MAX_X || z < ARENA_MIN_Z || z > ARENA_MAX_Z)
				{
					world.setBlockNoRebuild(x, 2, z, Block.BEDROCK);
				}
			}
		}
	}
	
	// ========================================================
	// Tower.
	// ========================================================
	
	// ========================================================
	// Tower generation (modified)
	// ========================================================
	
	/**
	 * Generates a castle tower: circular BEDROCK walls, helicoid staircase,<br>
	 * doors at base and top, balcony, interior torches and roof battlements.
	 */
	private static void generateTower(World world, TileEntityManager manager, int cx, int cz, int doorDirX, int doorDirZ)
	{
		// --- Circular walls and interior (now up to TOWER_TOP_Y) ---
		for (int dx = -TOWER_OUTER_RADIUS; dx <= TOWER_OUTER_RADIUS; dx++)
		{
			for (int dz = -TOWER_OUTER_RADIUS; dz <= TOWER_OUTER_RADIUS; dz++)
			{
				final int distSq = dx * dx + dz * dz;
				if (distSq > TOWER_OUTER_RADIUS * TOWER_OUTER_RADIUS)
				{
					continue;
				}
				
				final int wx = cx + dx;
				final int wz = cz + dz;
				final boolean isWall = distSq > TOWER_INNER_RADIUS * TOWER_INNER_RADIUS;
				
				for (int y = TOWER_BASE_Y; y <= TOWER_TOP_Y; y++)
				{
					world.setBlockNoRebuild(wx, y, wz, isWall ? Block.BEDROCK : Block.AIR);
				}
				
				// Solid roof floor (full disc) – replaces the old ceiling line
				world.setBlockNoRebuild(wx, TOWER_TOP_Y, wz, Block.BEDROCK);
			}
		}
		
		// --- Helicoid staircase (now reaches TOWER_TOP_Y-1) ---
		generateHelicoid(world, cx, cz);
		
		// --- Doors: pick the axis that points toward arena center ---
		final int bottomDoorX = cx + doorDirX * (TOWER_OUTER_RADIUS);
		final int bottomDoorZ = cz;
		final Facing bottomFacing = doorDirX > 0 ? Facing.EAST : Facing.WEST;
		
		// Bottom door.
		carveDoorway(world, bottomDoorX, TOWER_BASE_Y, bottomDoorZ, doorDirX, 0);
		placeDoor(world, manager, bottomDoorX, TOWER_BASE_Y, bottomDoorZ, bottomFacing);
		
		// Wall-mounted flanking torches: build door frame pillars, then mount torches.
		final int torchOutX = bottomDoorX + doorDirX; // one block outside the door
		final Face torchAttachFace = doorDirX > 0 ? Face.WEST : Face.EAST;
		
		// Door frame blocks (ensure solid attachment surface at outer wall edge).
		for (int dy = 0; dy <= 1; dy++)
		{
			world.setBlockNoRebuild(bottomDoorX, TOWER_BASE_Y + dy, bottomDoorZ - 1, Block.BEDROCK);
			world.setBlockNoRebuild(bottomDoorX, TOWER_BASE_Y + dy, bottomDoorZ + 1, Block.BEDROCK);
		}
		
		placeWallTorch(world, manager, torchOutX, TOWER_BASE_Y + 1, bottomDoorZ - 1, torchAttachFace);
		placeWallTorch(world, manager, torchOutX, TOWER_BASE_Y + 1, bottomDoorZ + 1, torchAttachFace);
		
		// --- Interior torches (outer wall only) ---
		placeTowerTorches(world, manager, cx, cz);
		
		// --- Rooftop torches for visibility ---
		placeRooftopTorches(world, manager, cx, cz);
		
		// --- Corbelling (outward flare) at top of tower walls ---
		// Adds a 1-block ring just outside the tower wall at roof level for a castle look.
		// Must be placed BEFORE battlements since battlements check for solid roof blocks.
		final int corbelR = TOWER_OUTER_RADIUS + 1;
		for (int dx = -corbelR; dx <= corbelR; dx++)
		{
			for (int dz = -corbelR; dz <= corbelR; dz++)
			{
				final int distSq = dx * dx + dz * dz;
				
				// Ring just outside the old wall.
				if (distSq > TOWER_OUTER_RADIUS * TOWER_OUTER_RADIUS && distSq <= corbelR * corbelR)
				{
					final int wx = cx + dx;
					final int wz = cz + dz;
					
					// Two rows of corbelling below the roof line.
					world.setBlockNoRebuild(wx, TOWER_TOP_Y - 1, wz, Block.BEDROCK);
					world.setBlockNoRebuild(wx, TOWER_TOP_Y, wz, Block.BEDROCK);
				}
			}
		}
		
		// --- Roof battlements with torches (sits on corbelled ring) ---
		addBattlements(world, manager, cx, cz);
	}
	
	/**
	 * Generates the helicoid staircase. Steps fill the full interior radius<br>
	 * (HELIX_OUTER_RADIUS = TOWER_INNER_RADIUS) so there are no gaps to the wall.
	 */
	private static void generateHelicoid(World world, int cx, int cz)
	{
		// Central column: 5×5 BEDROCK (extends to roof level)
		for (int y = TOWER_BASE_Y; y <= TOWER_TOP_Y; y++)
		{
			for (int dx = -2; dx <= 2; dx++)
			{
				for (int dz = -2; dz <= 2; dz++)
				{
					world.setBlockNoRebuild(cx + dx, y, cz + dz, Block.BEDROCK);
				}
			}
		}
		
		// Spiral steps - solid wedge fill via polar coordinate test.
		for (int step = 0; step < NUM_STAIRS; step++) // was HELIX_HEIGHT
		{
			final int y = TOWER_BASE_Y + step;
			final double centerAngle = step * (2.0 * Math.PI / HELIX_STEPS_PER_REV);
			
			for (int dx = -HELIX_OUTER_RADIUS; dx <= HELIX_OUTER_RADIUS; dx++)
			{
				for (int dz = -HELIX_OUTER_RADIUS; dz <= HELIX_OUTER_RADIUS; dz++)
				{
					final int distSq = dx * dx + dz * dz;
					if (distSq < HELIX_COLUMN_RADIUS * HELIX_COLUMN_RADIUS || distSq > HELIX_OUTER_RADIUS * HELIX_OUTER_RADIUS)
					{
						continue;
					}
					
					double angleDiff = Math.atan2(dz, dx) - centerAngle;
					while (angleDiff > Math.PI)
					{
						angleDiff -= 2.0 * Math.PI;
					}
					while (angleDiff < -Math.PI)
					{
						angleDiff += 2.0 * Math.PI;
					}
					
					if (Math.abs(angleDiff) <= STEP_HALF_WIDTH)
					{
						world.setBlockNoRebuild(cx + dx, y, cz + dz, Block.BEDROCK);
					}
				}
			}
		}
		
		// Top landing platform (roof floor) is already set by the wall/roof loop.
		
		// --- Headroom clearance pass ---
		// Guarantee 2 blocks of air above every step so the player (2-tall) can walk.
		for (int step = 0; step < NUM_STAIRS; step++)
		{
			final int y = TOWER_BASE_Y + step;
			final double centerAngle = step * (2.0 * Math.PI / HELIX_STEPS_PER_REV);
			
			for (int dx = -HELIX_OUTER_RADIUS; dx <= HELIX_OUTER_RADIUS; dx++)
			{
				for (int dz = -HELIX_OUTER_RADIUS; dz <= HELIX_OUTER_RADIUS; dz++)
				{
					final int distSq = dx * dx + dz * dz;
					if (distSq < HELIX_COLUMN_RADIUS * HELIX_COLUMN_RADIUS || distSq > HELIX_OUTER_RADIUS * HELIX_OUTER_RADIUS)
					{
						continue;
					}
					
					double angleDiff = Math.atan2(dz, dx) - centerAngle;
					while (angleDiff > Math.PI)
					{
						angleDiff -= 2.0 * Math.PI;
					}
					while (angleDiff < -Math.PI)
					{
						angleDiff += 2.0 * Math.PI;
					}
					
					if (Math.abs(angleDiff) <= STEP_HALF_WIDTH)
					{
						final int wx = cx + dx;
						final int wz = cz + dz;
						
						// Clear 2 blocks above the step for headroom.
						if (world.getBlock(wx, y + 1, wz) == Block.BEDROCK)
						{
							world.setBlockNoRebuild(wx, y + 1, wz, Block.AIR);
						}
						
						if (world.getBlock(wx, y + 2, wz) == Block.BEDROCK)
						{
							world.setBlockNoRebuild(wx, y + 2, wz, Block.AIR);
						}
					}
				}
			}
		}
	}
	
	// ========================================================
	// Roof Battlements
	// ========================================================
	
	/**
	 * Adds crenellations (alternating merlons and crenels) around the top of the tower<br>
	 * and places torches on merlon tops. Uses angular spacing for even distribution.
	 */
	private static void addBattlements(World world, TileEntityManager manager, int cx, int cz)
	{
		final int r = TOWER_OUTER_RADIUS + 1; // sits on the corbelled ring
		final int merlonBaseY = TOWER_TOP_Y + 1;
		final int merlonTopY = merlonBaseY + 1; // 2-high merlons
		final int torchY = merlonTopY + 1;
		
		// Number of merlons and angular width.
		final int NUM_MERLONS = 10;
		final double merlonAngularWidth = Math.toRadians(12); // each merlon spans ~12°
		
		for (int dx = -r; dx <= r; dx++)
		{
			for (int dz = -r; dz <= r; dz++)
			{
				double dist = Math.sqrt(dx * dx + dz * dz);
				
				// Select blocks approximately on the outer ring.
				if (Math.abs(dist - r) > 1.0)
				{
					continue;
				}
				
				int wx = cx + dx;
				int wz = cz + dz;
				
				// Only place on top of solid tower roof.
				if (world.getBlock(wx, TOWER_TOP_Y, wz) != Block.BEDROCK)
				{
					continue;
				}
				
				// Determine if this block falls within a merlon.
				double angle = Math.atan2(dz, dx);
				boolean isMerlon = false;
				for (int m = 0; m < NUM_MERLONS; m++)
				{
					double merlonCenter = m * (2.0 * Math.PI / NUM_MERLONS);
					double diff = angle - merlonCenter;
					while (diff > Math.PI)
					{
						diff -= 2.0 * Math.PI;
					}
					while (diff < -Math.PI)
					{
						diff += 2.0 * Math.PI;
					}
					
					if (Math.abs(diff) <= merlonAngularWidth)
					{
						isMerlon = true;
						break;
					}
				}
				
				if (isMerlon)
				{
					// 2-block tall merlon.
					world.setBlockNoRebuild(wx, merlonBaseY, wz, Block.BEDROCK);
					world.setBlockNoRebuild(wx, merlonTopY, wz, Block.BEDROCK);
				}
			}
		}
		
		// Place torches on top of every other merlon (use the center block of each merlon).
		for (int m = 0; m < NUM_MERLONS; m += 2)
		{
			double angle = m * (2.0 * Math.PI / NUM_MERLONS);
			int tx = cx + (int) Math.round(r * Math.cos(angle));
			int tz = cz + (int) Math.round(r * Math.sin(angle));
			if (world.getBlock(tx, merlonTopY, tz) == Block.BEDROCK)
			{
				placeFloorTorch(world, manager, tx, torchY, tz);
			}
		}
	}
	
	// ========================================================
	// Doors.
	// ========================================================
	
	/**
	 * Carves a 1-wide, 2-tall opening through the tower wall for a door.
	 */
	private static void carveDoorway(World world, int x, int y, int z, int dirX, int dirZ)
	{
		// Carve through the full wall thickness + extra clearance on both sides.
		for (int w = -TOWER_WALL - 2; w <= TOWER_WALL + 2; w++)
		{
			final int cx = x + dirX * w;
			final int cz = z + dirZ * w;
			
			// Clear 2 blocks high (exactly door height).
			for (int dy = 0; dy <= 1; dy++)
			{
				if (world.getBlock(cx, y + dy, cz) == Block.BEDROCK)
				{
					world.setBlockNoRebuild(cx, y + dy, cz, Block.AIR);
				}
			}
		}
	}
	
	/**
	 * Places a door (DOOR_BOTTOM + DOOR_TOP) with linked tile entities.
	 */
	private static void placeDoor(World world, TileEntityManager manager, int x, int y, int z, Facing facing)
	{
		final Vector3i bottomPos = new Vector3i(x, y, z);
		final Vector3i topPos = new Vector3i(x, y + 1, z);
		
		world.setBlockNoRebuild(x, y, z, Block.DOOR_BOTTOM);
		world.setBlockNoRebuild(x, y + 1, z, Block.DOOR_TOP);
		
		final Face attachFace = facingToAttachFace(facing);
		
		final DoorTileEntity bottom = new DoorTileEntity(bottomPos, Block.DOOR_BOTTOM, attachFace, topPos, true);
		bottom.setFacing(facing);
		bottom.onPlaced(world);
		manager.register(bottom);
		
		final DoorTileEntity top = new DoorTileEntity(topPos, Block.DOOR_TOP, attachFace, bottomPos, false);
		top.setFacing(facing);
		top.onPlaced(world);
		manager.register(top);
	}
	
	private static Face facingToAttachFace(Facing facing)
	{
		switch (facing)
		{
			case NORTH:
			{
				return Face.SOUTH;
			}
			case SOUTH:
			{
				return Face.NORTH;
			}
			case EAST:
			{
				return Face.WEST;
			}
			case WEST:
			{
				return Face.EAST;
			}
			default:
			{
				return Face.SOUTH;
			}
		}
	}
	
	// ========================================================
	// Tower Interior Torches.
	// ========================================================
	
	/**
	 * Places torches inside a tower on the outer wall going upward.<br>
	 * Uses 8 cardinal/diagonal positions around the inner wall face, spaced every 4 Y levels.
	 */
	private static void placeTowerTorches(World world, TileEntityManager manager, int cx, int cz)
	{
		// Torch positions on the inner face of the outer wall.
		// TOWER_INNER_RADIUS = 10, so torches sit at radius 10 (just inside the wall).
		final int r = TOWER_INNER_RADIUS;
		final int diagR = (int) Math.round(r * 0.707); // r * cos(45°) for diagonals
		
		// 8 positions: 4 cardinal + 4 diagonal, with their attach faces.
		// @formatter:off
		final int[][] torchOffsets =
		{
			// {dx, dz, attachFace index} - 0=EAST, 1=WEST, 2=SOUTH, 3=NORTH
			{ r, 0, 0 },     // +X wall, attach EAST (torch on inner side)
			{ -r, 0, 1 },    // -X wall, attach WEST
			{ 0, r, 2 },     // +Z wall, attach SOUTH
			{ 0, -r, 3 },    // -Z wall, attach NORTH
			{ diagR, diagR, 0 },
			{ diagR, -diagR, 0 },
			{ -diagR, diagR, 1 },
			{ -diagR, -diagR, 1 }
		};
		final Face[] faces = { Face.EAST, Face.WEST, Face.SOUTH, Face.NORTH };
		// @formatter:on
		
		for (int y = TOWER_BASE_Y + 3; y < LANDING_Y - 1; y += 4)
		{
			for (int[] offset : torchOffsets)
			{
				final int tx = cx + offset[0];
				final int tz = cz + offset[1];
				final Face attachFace = faces[offset[2]];
				
				// Only place if the torch position is air and the wall behind is solid.
				carveAndPlaceWallTorch(world, manager, tx, y, tz, attachFace);
			}
		}
	}
	
	/**
	 * Carves an alcove on the inner tower wall and places a wall torch.<br>
	 * The torch attaches to the wall block behind it (the attachedFace side).
	 */
	private static void carveAndPlaceWallTorch(World world, TileEntityManager manager, int x, int y, int z, Face attachedFace)
	{
		// Clear the torch position and block above for visibility.
		world.setBlockNoRebuild(x, y, z, Block.AIR);
		world.setBlockNoRebuild(x, y + 1, z, Block.AIR);
		placeWallTorch(world, manager, x, y, z, attachedFace);
	}
	
	// ========================================================
	// Rooftop Torches.
	// ========================================================
	
	/**
	 * Places floor torches on the tower rooftop for visibility from above and below.<br>
	 * Torches go around the roof perimeter and on the central column top.
	 */
	private static void placeRooftopTorches(World world, TileEntityManager manager, int cx, int cz)
	{
		final int y = TOWER_TOP_Y + 1; // on top of the roof
		
		// Ring of torches just inside the wall on the roof surface.
		final int r = TOWER_INNER_RADIUS - 1;
		final int numTorches = 8;
		for (int i = 0; i < numTorches; i++)
		{
			final double angle = i * (2.0 * Math.PI / numTorches);
			final int tx = cx + (int) Math.round(r * Math.cos(angle));
			final int tz = cz + (int) Math.round(r * Math.sin(angle));
			if (world.getBlock(tx, TOWER_TOP_Y, tz) == Block.BEDROCK)
			{
				placeFloorTorch(world, manager, tx, y, tz);
			}
		}
		
		// Central column top torch.
		placeFloorTorch(world, manager, cx, y, cz);
	}
	
	// ========================================================
	// Perimeter and Arena Torches.
	// ========================================================
	
	private static void placePerimeterTorches(World world, TileEntityManager manager)
	{
		final int y = 6;
		for (int x = INTERIOR_MIN + WALL_TORCH_SPACING; x < INTERIOR_MAX; x += WALL_TORCH_SPACING)
		{
			placeWallTorch(world, manager, x, y, INTERIOR_MIN, Face.SOUTH);
			placeWallTorch(world, manager, x, y, INTERIOR_MAX, Face.NORTH);
		}
		
		for (int z = INTERIOR_MIN + WALL_TORCH_SPACING; z < INTERIOR_MAX; z += WALL_TORCH_SPACING)
		{
			placeWallTorch(world, manager, INTERIOR_MIN, y, z, Face.WEST);
			placeWallTorch(world, manager, INTERIOR_MAX, y, z, Face.EAST);
		}
	}
	
	private static void placeArenaTorches(World world, TileEntityManager manager)
	{
		final int y = 3;
		placeFloorTorch(world, manager, ARENA_MIN_X - 1, y, ARENA_MIN_Z - 1);
		placeFloorTorch(world, manager, ARENA_MAX_X + 1, y, ARENA_MIN_Z - 1);
		placeFloorTorch(world, manager, ARENA_MIN_X - 1, y, ARENA_MAX_Z + 1);
		placeFloorTorch(world, manager, ARENA_MAX_X + 1, y, ARENA_MAX_Z + 1);
		
		for (int x = ARENA_MIN_X; x <= ARENA_MAX_X; x += 20)
		{
			placeFloorTorch(world, manager, x, y, ARENA_MIN_Z - 1);
			placeFloorTorch(world, manager, x, y, ARENA_MAX_Z + 1);
		}
		
		for (int z = ARENA_MIN_Z; z <= ARENA_MAX_Z; z += 20)
		{
			placeFloorTorch(world, manager, ARENA_MIN_X - 1, y, z);
			placeFloorTorch(world, manager, ARENA_MAX_X + 1, y, z);
		}
	}
	
	// ========================================================
	// Torch Helpers.
	// ========================================================
	
	private static void placeWallTorch(World world, TileEntityManager manager, int x, int y, int z, Face attachedFace)
	{
		world.setBlockNoRebuild(x, y, z, Block.TORCH);
		final TorchTileEntity torch = new TorchTileEntity(new Vector3i(x, y, z), attachedFace);
		torch.onPlaced(world);
		manager.register(torch);
	}
	
	private static void placeFloorTorch(World world, TileEntityManager manager, int x, int y, int z)
	{
		world.setBlockNoRebuild(x, y, z, Block.TORCH);
		final TorchTileEntity torch = new TorchTileEntity(new Vector3i(x, y, z), Face.TOP);
		torch.onPlaced(world);
		manager.register(torch);
	}
}
