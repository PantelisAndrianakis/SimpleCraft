package simplecraft.world;

import java.util.Random;

import simplecraft.util.OpenSimplex2;

/**
 * Generates terrain for chunks using 2D noise for heightmaps and 3D noise for ore veins.<br>
 * Produces rolling hills with grass, dirt, stone layers, water-filled valleys,<br>
 * iron ore clusters, berry bushes, tall grass, and flowers.
 * @author Pantelis Andrianakis
 * @since February 23rd 2026
 */
public class TerrainGenerator
{
	// ========================================================
	// Constants
	// ========================================================
	
	/** Sea level. Valleys below this height are filled with water. */
	public static final int WATER_LEVEL = 28;
	
	/** Minimum terrain height after clamping. */
	private static final int MIN_HEIGHT = 1;
	
	/** Maximum terrain height after clamping. */
	private static final int MAX_HEIGHT = 100;
	
	/** 3D noise threshold for iron ore generation. */
	private static final float ORE_THRESHOLD = 0.5f;
	
	/** Maximum Y level for iron ore spawning. */
	private static final int ORE_MAX_Y = 40;
	
	/** Hashing prime for column-based seeded random (X component). */
	private static final long HASH_PRIME_X = 73856093L;
	
	/** Hashing prime for column-based seeded random (Z component). */
	private static final long HASH_PRIME_Z = 19349669L;
	
	// ========================================================
	// Public API
	// ========================================================
	
	/**
	 * Generates terrain for the given chunk using the world seed.<br>
	 * Fills terrain layers, scatters iron ore, and places surface decorations.
	 * @param chunk the chunk to populate
	 * @param seed the world seed from WorldInfo
	 */
	public static void generateChunk(Chunk chunk, long seed)
	{
		final int chunkWorldX = chunk.getChunkX() * Chunk.SIZE_XZ;
		final int chunkWorldZ = chunk.getChunkZ() * Chunk.SIZE_XZ;
		
		// Precompute terrain heights for the entire chunk.
		final int[][] heights = new int[Chunk.SIZE_XZ][Chunk.SIZE_XZ];
		for (int x = 0; x < Chunk.SIZE_XZ; x++)
		{
			for (int z = 0; z < Chunk.SIZE_XZ; z++)
			{
				heights[x][z] = computeHeight(seed, chunkWorldX + x, chunkWorldZ + z);
			}
		}
		
		// Fill terrain layers.
		fillTerrain(chunk, heights);
		
		// Scatter iron ore veins in stone.
		generateOreVeins(chunk, seed, chunkWorldX, chunkWorldZ, heights);
		
		// Place surface decorations (berry bushes, tall grass, flowers).
		generateDecorations(chunk, seed, chunkWorldX, chunkWorldZ, heights);
		
		// Place seaweed on sand blocks underwater.
		generateSeaweed(chunk, seed, chunkWorldX, chunkWorldZ, heights);
	}
	
	// ========================================================
	// Height Computation
	// ========================================================
	
	/**
	 * Computes terrain height at a world column using 2-octave noise.
	 */
	private static int computeHeight(long seed, int worldX, int worldZ)
	{
		// Octave 1: broad rolling hills.
		double height = 32.0 + OpenSimplex2.noise2(seed, worldX * 0.01, worldZ * 0.01) * 16.0;
		
		// Octave 2: finer detail.
		height += OpenSimplex2.noise2(seed + 1, worldX * 0.02, worldZ * 0.02) * 8.0;
		
		// Clamp to valid range.
		return Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, (int) height));
	}
	
	// ========================================================
	// Terrain Filling
	// ========================================================
	
	/**
	 * Fills each column with bedrock, stone, dirt, grass/sand, water, and air layers.
	 */
	private static void fillTerrain(Chunk chunk, int[][] heights)
	{
		for (int x = 0; x < Chunk.SIZE_XZ; x++)
		{
			for (int z = 0; z < Chunk.SIZE_XZ; z++)
			{
				final int terrainHeight = heights[x][z];
				
				for (int y = 0; y < Chunk.SIZE_Y; y++)
				{
					final Block block;
					if (y == 0)
					{
						// Bedrock floor.
						block = Block.BEDROCK;
					}
					else if (y < terrainHeight - 4)
					{
						// Deep stone layer.
						block = Block.STONE;
					}
					else if (y < terrainHeight)
					{
						// Dirt layer (4 blocks thick).
						block = Block.DIRT;
					}
					else if (y == terrainHeight)
					{
						if (terrainHeight > WATER_LEVEL)
						{
							// Above water: grass surface.
							block = Block.GRASS;
						}
						else
						{
							// At or below water: sand surface.
							block = Block.SAND;
						}
					}
					else if (y > terrainHeight && y <= WATER_LEVEL)
					{
						// Water fills valleys up to water level.
						block = Block.WATER;
					}
					else
					{
						// Air above terrain (and above water level).
						block = Block.AIR;
					}
					
					chunk.setBlock(x, y, z, block);
				}
			}
		}
	}
	
	// ========================================================
	// Ore Generation
	// ========================================================
	
	/**
	 * Scatters iron ore veins in stone using 3D noise.<br>
	 * Only replaces STONE blocks below y=40 where noise exceeds the threshold.
	 */
	private static void generateOreVeins(Chunk chunk, long seed, int chunkWorldX, int chunkWorldZ, int[][] heights)
	{
		final long oreSeed = seed + 100;
		
		for (int x = 0; x < Chunk.SIZE_XZ; x++)
		{
			for (int z = 0; z < Chunk.SIZE_XZ; z++)
			{
				final int worldX = chunkWorldX + x;
				final int worldZ = chunkWorldZ + z;
				
				// Only check below ore ceiling and below dirt layer.
				final int maxY = Math.min(ORE_MAX_Y, heights[x][z] - 4);
				
				for (int y = 1; y <= maxY; y++)
				{
					if (chunk.getBlock(x, y, z) != Block.STONE)
					{
						continue;
					}
					
					final float oreNoise = OpenSimplex2.noise3(oreSeed, worldX * 0.08, y * 0.08, worldZ * 0.08);
					if (oreNoise > ORE_THRESHOLD)
					{
						chunk.setBlock(x, y, z, Block.IRON_ORE);
					}
				}
			}
		}
	}
	
	// ========================================================
	// Surface Decorations
	// ========================================================
	
	/**
	 * Places berry bushes, tall grass, and flowers on grass surface blocks.<br>
	 * Uses seeded random per column for deterministic placement.<br>
	 * Priority: berry bush (0.05%) > tall grass (10%) > flowers (1%).
	 */
	private static void generateDecorations(Chunk chunk, long seed, int chunkWorldX, int chunkWorldZ, int[][] heights)
	{
		for (int x = 0; x < Chunk.SIZE_XZ; x++)
		{
			for (int z = 0; z < Chunk.SIZE_XZ; z++)
			{
				final int terrainHeight = heights[x][z];
				
				// Only decorate grass blocks above water level.
				if (terrainHeight <= WATER_LEVEL)
				{
					continue;
				}
				
				if (chunk.getBlock(x, terrainHeight, z) != Block.GRASS)
				{
					continue;
				}
				
				// Ensure decoration Y is valid.
				final int decoY = terrainHeight + 1;
				if (decoY >= Chunk.SIZE_Y)
				{
					continue;
				}
				
				final int worldX = chunkWorldX + x;
				final int worldZ = chunkWorldZ + z;
				
				// Skip if adjacent to water or on a steep slope.
				if (isAdjacentToWater(heights, x, z) || isSteepSlope(heights, x, z, terrainHeight))
				{
					continue;
				}
				
				// Seeded random for this column.
				final Random columnRandom = new Random(seed ^ (worldX * HASH_PRIME_X) ^ (worldZ * HASH_PRIME_Z));
				final double roll = columnRandom.nextDouble();
				
				if (roll < 0.0005)
				{
					// 0.05% chance: berry bush.
					chunk.setBlock(x, decoY, z, Block.BERRY_BUSH);
				}
				else if (roll < 0.1005)
				{
					// 10% chance: tall grass.
					chunk.setBlock(x, decoY, z, Block.TALL_GRASS);
				}
				else if (roll < 0.1105)
				{
					// 1% chance: flower.
					final Block flower = pickFlower(columnRandom, heights, x, z);
					chunk.setBlock(x, decoY, z, flower);
				}
			}
		}
	}
	
	/**
	 * Picks a flower type based on seeded random.<br>
	 * BLUE_ORCHID only spawns within 5 blocks of water; otherwise rerolled.
	 */
	private static Block pickFlower(Random random, int[][] heights, int localX, int localZ)
	{
		final double flowerRoll = random.nextDouble();
		
		Block flower;
		if (flowerRoll < 0.30)
		{
			flower = Block.RED_POPPY;
		}
		else if (flowerRoll < 0.60)
		{
			flower = Block.DANDELION;
		}
		else if (flowerRoll < 0.80)
		{
			flower = Block.WHITE_DAISY;
		}
		else
		{
			flower = Block.BLUE_ORCHID;
		}
		
		// Blue orchid special rule: must be within 5 blocks of water.
		if (flower == Block.BLUE_ORCHID && !isNearWater(heights, localX, localZ, 5))
		{
			// Reroll to a non-restricted flower.
			final double reroll = random.nextDouble();
			if (reroll < 0.375)
			{
				flower = Block.RED_POPPY;
			}
			else if (reroll < 0.75)
			{
				flower = Block.DANDELION;
			}
			else
			{
				flower = Block.WHITE_DAISY;
			}
		}
		
		return flower;
	}
	
	// ========================================================
	// Underwater Seaweed
	// ========================================================
	
	/**
	 * Places tall and short seaweed on sand blocks that are underwater.<br>
	 * Uses seeded random per column for deterministic placement.<br>
	 * Tall seaweed stacks 2-4 blocks high; short seaweed is always 1 block.
	 */
	private static void generateSeaweed(Chunk chunk, long seed, int chunkWorldX, int chunkWorldZ, int[][] heights)
	{
		final long seaweedSeed = seed + 200;
		
		for (int x = 0; x < Chunk.SIZE_XZ; x++)
		{
			for (int z = 0; z < Chunk.SIZE_XZ; z++)
			{
				final int terrainHeight = heights[x][z];
				
				// Only place seaweed on sand blocks that are underwater.
				if (terrainHeight >= WATER_LEVEL)
				{
					continue;
				}
				
				if (chunk.getBlock(x, terrainHeight, z) != Block.SAND)
				{
					continue;
				}
				
				// Need at least 1 block of water above sand for seaweed.
				final int baseY = terrainHeight + 1;
				if (baseY >= WATER_LEVEL)
				{
					continue;
				}
				
				final int worldX = chunkWorldX + x;
				final int worldZ = chunkWorldZ + z;
				
				// Seeded random for this column.
				final Random columnRandom = new Random(seaweedSeed ^ (worldX * HASH_PRIME_X) ^ (worldZ * HASH_PRIME_Z));
				final double roll = columnRandom.nextDouble();
				
				if (roll < 0.01)
				{
					// 1% chance: tall seaweed (2-4 blocks high).
					final int waterDepth = WATER_LEVEL - terrainHeight;
					final int maxHeight = Math.min(waterDepth, 4);
					final int seaweedHeight = maxHeight <= 2 ? maxHeight : (2 + columnRandom.nextInt(3)); // 2 to 4
					
					for (int dy = 0; dy < seaweedHeight; dy++)
					{
						final int seaY = baseY + dy;
						if (seaY < WATER_LEVEL && chunk.getBlock(x, seaY, z) == Block.WATER)
						{
							chunk.setBlock(x, seaY, z, Block.TALL_SEAWEED);
						}
					}
				}
				else if (roll < 0.03)
				{
					// 2% chance: short seaweed (1 block).
					if (chunk.getBlock(x, baseY, z) == Block.WATER)
					{
						chunk.setBlock(x, baseY, z, Block.SHORT_SEAWEED);
					}
				}
			}
		}
	}
	
	// ========================================================
	// Adjacency Checks
	// ========================================================
	
	/**
	 * Returns true if any of the 4 cardinal neighbors has terrain at or below water level.
	 */
	private static boolean isAdjacentToWater(int[][] heights, int x, int z)
	{
		if (x > 0 && heights[x - 1][z] <= WATER_LEVEL)
		{
			return true;
		}
		
		if (x < Chunk.SIZE_XZ - 1 && heights[x + 1][z] <= WATER_LEVEL)
		{
			return true;
		}
		
		if (z > 0 && heights[x][z - 1] <= WATER_LEVEL)
		{
			return true;
		}
		
		if (z < Chunk.SIZE_XZ - 1 && heights[x][z + 1] <= WATER_LEVEL)
		{
			return true;
		}
		
		return false;
	}
	
	/**
	 * Returns true if any cardinal neighbor differs in height by more than 2 blocks.
	 */
	private static boolean isSteepSlope(int[][] heights, int x, int z, int centerHeight)
	{
		if (x > 0 && Math.abs(heights[x - 1][z] - centerHeight) > 2)
		{
			return true;
		}
		
		if (x < Chunk.SIZE_XZ - 1 && Math.abs(heights[x + 1][z] - centerHeight) > 2)
		{
			return true;
		}
		
		if (z > 0 && Math.abs(heights[x][z - 1] - centerHeight) > 2)
		{
			return true;
		}
		
		if (z < Chunk.SIZE_XZ - 1 && Math.abs(heights[x][z + 1] - centerHeight) > 2)
		{
			return true;
		}
		
		return false;
	}
	
	/**
	 * Returns true if any column within the given radius has terrain at or below water level.<br>
	 * Used for BLUE_ORCHID proximity check.
	 */
	private static boolean isNearWater(int[][] heights, int x, int z, int radius)
	{
		final int minX = Math.max(0, x - radius);
		final int maxX = Math.min(Chunk.SIZE_XZ - 1, x + radius);
		final int minZ = Math.max(0, z - radius);
		final int maxZ = Math.min(Chunk.SIZE_XZ - 1, z + radius);
		
		for (int nx = minX; nx <= maxX; nx++)
		{
			for (int nz = minZ; nz <= maxZ; nz++)
			{
				if (heights[nx][nz] <= WATER_LEVEL)
				{
					return true;
				}
			}
		}
		
		return false;
	}
}
