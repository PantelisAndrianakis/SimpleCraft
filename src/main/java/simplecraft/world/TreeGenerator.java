package simplecraft.world;

import java.util.Random;

/**
 * Generates procedural trees on grass terrain after terrain generation.<br>
 * Produces five tree variants: small oak, tall oak, birch, shrub and spruce.<br>
 * Canopies use layered profiles with corner/edge randomization for a natural rounded look.<br>
 * Spruce trees provide a distinct conical silhouette that contrasts with rounded canopies.<br>
 * Trees are spaced to avoid trunk collisions while allowing natural canopy overlap.
 * @author Pantelis Andrianakis
 * @since February 24th 2026
 */
public class TreeGenerator
{
	// ========================================================
	// Constants
	// ========================================================
	
	/** Minimum distance from region edge to place a tree (avoids cross-region leaf overflow). */
	private static final int EDGE_PADDING = 3;
	
	/** Minimum horizontal distance between tree trunks. */
	private static final int TRUNK_SPACING = 2;
	
	/** Probability of attempting a tree per column (3%). */
	private static final double TREE_CHANCE = 0.03;
	
	/** Hashing prime for column-based seeded random (X component). */
	private static final long HASH_PRIME_X = 73856093L;
	
	/** Hashing prime for column-based seeded random (Z component). */
	private static final long HASH_PRIME_Z = 19349663L;
	
	/** Maximum iterations for tree post-processing to prevent infinite loops. */
	private static final int MAX_POST_PROCESS_ITERATIONS = 10;
	
	// ========================================================
	// Tree Types
	// ========================================================
	
	private static final int TREE_SMALL_OAK = 0;
	private static final int TREE_TALL_OAK = 1;
	private static final int TREE_BIRCH = 2;
	private static final int TREE_SHRUB = 3;
	private static final int TREE_SPRUCE = 4;
	
	// ========================================================
	// Public API
	// ========================================================
	
	/**
	 * Generates trees for the given region using the world seed.<br>
	 * Must be called after terrain generation but before mesh building.
	 * @param region the region to populate with trees
	 * @param seed the world seed from WorldInfo
	 */
	public static void generateTrees(Region region, long seed)
	{
		final int regionWorldX = region.getRegionX() * Region.SIZE_XZ;
		final int regionWorldZ = region.getRegionZ() * Region.SIZE_XZ;
		
		// Tree seed offset to avoid correlation with terrain decorations.
		final long treeSeed = seed + 300;
		
		// Track trunk positions for spacing checks.
		final boolean[][] trunkPlaced = new boolean[Region.SIZE_XZ][Region.SIZE_XZ];
		
		for (int x = EDGE_PADDING; x < Region.SIZE_XZ - EDGE_PADDING; x++)
		{
			for (int z = EDGE_PADDING; z < Region.SIZE_XZ - EDGE_PADDING; z++)
			{
				final int worldX = regionWorldX + x;
				final int worldZ = regionWorldZ + z;
				
				// Seeded random for this column.
				final Random columnRandom = new Random(treeSeed ^ (worldX * HASH_PRIME_X) ^ (worldZ * HASH_PRIME_Z));
				final double roll = columnRandom.nextDouble();
				
				// 3% chance to attempt a tree.
				if (roll >= TREE_CHANCE)
				{
					continue;
				}
				
				// Find highest GRASS block in this column.
				final int groundY = findGrassTop(region, x, z);
				if (groundY < 0)
				{
					continue;
				}
				
				// Skip if ground is at or below water level.
				if (groundY <= TerrainGenerator.WATER_LEVEL)
				{
					continue;
				}
				
				// Spacing check: no other trunk base within TRUNK_SPACING blocks.
				if (hasTrunkNearby(trunkPlaced, x, z, TRUNK_SPACING))
				{
					continue;
				}
				
				// Select tree type.
				final int treeType = selectTreeType(columnRandom);
				
				// Place the tree.
				placeTree(region, x, z, groundY, treeType, columnRandom);
				
				// Mark trunk position for spacing checks.
				trunkPlaced[x][z] = true;
			}
		}
		
		// Post-process the region repeatedly until no more changes are made.
		postProcessTreesRepeatedly(region);
	}
	
	// ========================================================
	// Post-Processing
	// ========================================================
	
	/**
	 * Repeatedly post-processes trees until no more changes are made or max iterations reached.
	 */
	private static void postProcessTreesRepeatedly(Region region)
	{
		boolean changed = false;
		int iterations = 0;
		
		do
		{
			changed = postProcessTrees(region);
			iterations++;
			
			// Safety check to prevent infinite loops.
			if (iterations >= MAX_POST_PROCESS_ITERATIONS)
			{
				break;
			}
		}
		while (changed);
	}
	
	/**
	 * Post-processes the region to clean up tree tops and add leaves where needed.<br>
	 * Rules:<br>
	 * 1. If top block is wood and has no leaves around it, remove it.<br>
	 * 2. If top block is wood, has leaves around it, add a leaf above it.<br>
	 * 3. If top block is wood, ensure it has leaves on all four cardinal directions.<br>
	 * 4. If the block below the top is wood and the top is leaf, convert the wood below to leaf.
	 * @return true if any changes were made, false if nothing changed
	 */
	private static boolean postProcessTrees(Region region)
	{
		boolean changesMade = false;
		
		// First pass: Convert wood below top leaf to leaf.
		for (int x = 0; x < Region.SIZE_XZ; x++)
		{
			for (int z = 0; z < Region.SIZE_XZ; z++)
			{
				final int highestY = findHighestBlock(region, x, z);
				if (highestY == -1)
				{
					continue;
				}
				
				// Rule 4: If top block is leaf and block below is wood, convert wood to leaf.
				if (region.getBlock(x, highestY, z) == Block.LEAVES)
				{
					final int belowY = highestY - 1;
					if (belowY >= 0 && region.getBlock(x, belowY, z) == Block.WOOD)
					{
						region.setBlock(x, belowY, z, Block.LEAVES);
						changesMade = true;
					}
				}
			}
		}
		
		// Second pass: Regular tree top processing.
		for (int x = 0; x < Region.SIZE_XZ; x++)
		{
			for (int z = 0; z < Region.SIZE_XZ; z++)
			{
				final int highestY = findHighestBlock(region, x, z);
				if (highestY == -1)
				{
					continue;
				}
				
				// Check if the top block is wood.
				if (region.getBlock(x, highestY, z) == Block.WOOD)
				{
					// First, ensure the wood has leaves on all four sides at the same level.
					boolean hasNorthLeaf = z > 0 && region.getBlock(x, highestY, z - 1) == Block.LEAVES;
					boolean hasSouthLeaf = z < Region.SIZE_XZ - 1 && region.getBlock(x, highestY, z + 1) == Block.LEAVES;
					boolean hasWestLeaf = x > 0 && region.getBlock(x - 1, highestY, z) == Block.LEAVES;
					boolean hasEastLeaf = x < Region.SIZE_XZ - 1 && region.getBlock(x + 1, highestY, z) == Block.LEAVES;
					
					// Add missing side leaves if there's support below.
					if (!hasNorthLeaf && addSideLeafIfSupported(region, x, highestY, z - 1))
					{
						changesMade = true;
						hasNorthLeaf = true;
					}
					
					if (!hasSouthLeaf && addSideLeafIfSupported(region, x, highestY, z + 1))
					{
						changesMade = true;
						hasSouthLeaf = true;
					}
					
					if (!hasWestLeaf && addSideLeafIfSupported(region, x - 1, highestY, z))
					{
						changesMade = true;
						hasWestLeaf = true;
					}
					
					if (!hasEastLeaf && addSideLeafIfSupported(region, x + 1, highestY, z))
					{
						changesMade = true;
						hasEastLeaf = true;
					}
					
					// Now check for leaves in adjacent positions (including diagonals) for the removal rule.
					boolean hasAdjacentLeaf = hasNorthLeaf || hasSouthLeaf || hasWestLeaf || hasEastLeaf;
					
					// Also check diagonals for the removal rule.
					for (int dx = -1; dx <= 1 && !hasAdjacentLeaf; dx++)
					{
						for (int dz = -1; dz <= 1 && !hasAdjacentLeaf; dz++)
						{
							if (dx == 0 && dz == 0)
							{
								continue; // Skip self.
							}
							
							final int nx = x + dx;
							final int nz = z + dz;
							
							if (nx >= 0 && nx < Region.SIZE_XZ && nz >= 0 && nz < Region.SIZE_XZ)
							{
								// Check same level.
								if (region.getBlock(nx, highestY, nz) == Block.LEAVES)
								{
									hasAdjacentLeaf = true;
									break;
								}
								
								// Check one level down (for leaves that might be slightly lower).
								if (highestY - 1 >= 0 && region.getBlock(nx, highestY - 1, nz) == Block.LEAVES)
								{
									hasAdjacentLeaf = true;
									break;
								}
							}
						}
					}
					
					if (!hasAdjacentLeaf)
					{
						// Rule 1: No leaves around - remove the wood block.
						region.setBlock(x, highestY, z, Block.AIR);
						changesMade = true;
					}
					else
					{
						// Rule 2: Has leaves around - add a leaf above if space allows.
						final int aboveY = highestY + 1;
						if (aboveY < Region.SIZE_Y && region.getBlock(x, aboveY, z) == Block.AIR)
						{
							// Check if the position above has support from the side leaves.
							boolean hasSupportAbove = false;
							for (int dx = -1; dx <= 1 && !hasSupportAbove; dx++)
							{
								for (int dz = -1; dz <= 1 && !hasSupportAbove; dz++)
								{
									if (dx == 0 && dz == 0)
									{
										continue;
									}
									
									final int nx = x + dx;
									final int nz = z + dz;
									if (nx >= 0 && nx < Region.SIZE_XZ && nz >= 0 && nz < Region.SIZE_XZ)
									{
										if (region.getBlock(nx, highestY, nz) == Block.LEAVES)
										{
											hasSupportAbove = true;
										}
									}
								}
							}
							
							if (hasSupportAbove)
							{
								region.setBlock(x, aboveY, z, Block.LEAVES);
								changesMade = true;
							}
						}
					}
				}
			}
		}
		
		return changesMade;
	}
	
	/**
	 * Adds a leaf at (x, y, z) if the position is in bounds and has WOOD or LEAVES support one block below.<br>
	 * Used by post-processing to fill in missing side leaves around exposed wood tops.
	 * @return true if a leaf was placed, false otherwise
	 */
	private static boolean addSideLeafIfSupported(Region region, int x, int y, int z)
	{
		if (!region.isInBounds(x, y, z) || y - 1 < 0)
		{
			return false;
		}
		
		final Block below = region.getBlock(x, y - 1, z);
		if (below == Block.WOOD || below == Block.LEAVES)
		{
			placeLeaf(region, x, y, z);
			return true;
		}
		
		return false;
	}
	
	// ========================================================
	// Ground Detection
	// ========================================================
	
	/**
	 * Finds the Y coordinate of the highest GRASS block in the given column.<br>
	 * Returns -1 if no GRASS block exists.
	 */
	private static int findGrassTop(Region region, int x, int z)
	{
		for (int y = Region.SIZE_Y - 1; y >= 0; y--)
		{
			if (region.getBlock(x, y, z) == Block.GRASS)
			{
				return y;
			}
		}
		
		return -1;
	}
	
	/**
	 * Finds the Y coordinate of the highest non-AIR block in the given column.<br>
	 * Returns -1 if the column is entirely air.
	 */
	private static int findHighestBlock(Region region, int x, int z)
	{
		for (int y = Region.SIZE_Y - 1; y >= 0; y--)
		{
			if (region.getBlock(x, y, z) != Block.AIR)
			{
				return y;
			}
		}
		
		return -1;
	}
	
	// ========================================================
	// Spacing Check
	// ========================================================
	
	/**
	 * Returns true if any column within the given radius already has a trunk placed.
	 */
	private static boolean hasTrunkNearby(boolean[][] trunkPlaced, int x, int z, int radius)
	{
		final int minX = Math.max(0, x - radius);
		final int maxX = Math.min(Region.SIZE_XZ - 1, x + radius);
		final int minZ = Math.max(0, z - radius);
		final int maxZ = Math.min(Region.SIZE_XZ - 1, z + radius);
		
		for (int nx = minX; nx <= maxX; nx++)
		{
			for (int nz = minZ; nz <= maxZ; nz++)
			{
				if (trunkPlaced[nx][nz])
				{
					return true;
				}
			}
		}
		
		return false;
	}
	
	// ========================================================
	// Tree Type Selection
	// ========================================================
	
	/**
	 * Selects a tree type based on weighted probabilities.<br>
	 * SMALL_OAK: 30%, TALL_OAK: 20%, BIRCH: 15%, SHRUB: 5%, SPRUCE: 30%.
	 */
	private static int selectTreeType(Random random)
	{
		final double roll = random.nextDouble();
		
		if (roll < 0.30)
		{
			return TREE_SMALL_OAK;
		}
		else if (roll < 0.50)
		{
			return TREE_TALL_OAK;
		}
		else if (roll < 0.65)
		{
			return TREE_BIRCH;
		}
		else if (roll < 0.70)
		{
			return TREE_SHRUB;
		}
		else
		{
			return TREE_SPRUCE;
		}
	}
	
	// ========================================================
	// Tree Placement
	// ========================================================
	
	/**
	 * Places a tree of the given type at the specified position.
	 * @param region the region to modify
	 * @param x local X of trunk base
	 * @param z local Z of trunk base
	 * @param groundY Y coordinate of the GRASS block (trunk starts at groundY+1)
	 * @param treeType the tree type constant
	 * @param random seeded random for variation
	 */
	private static void placeTree(Region region, int x, int z, int groundY, int treeType, Random random)
	{
		switch (treeType)
		{
			case TREE_SMALL_OAK:
			{
				placeSmallOak(region, x, z, groundY, random);
				break;
			}
			case TREE_TALL_OAK:
			{
				placeTallOak(region, x, z, groundY, random);
				break;
			}
			case TREE_BIRCH:
			{
				placeBirch(region, x, z, groundY, random);
				break;
			}
			case TREE_SHRUB:
			{
				placeShrub(region, x, z, groundY, random);
				break;
			}
			case TREE_SPRUCE:
			{
				placeSpruce(region, x, z, groundY, random);
				break;
			}
		}
	}
	
	// ========================================================
	// SMALL_OAK (40%) - Compact rounded canopy
	// ========================================================
	
	/**
	 * Places a small oak tree with a layered rounded canopy.<br>
	 * Trunk: 4-5 blocks. Canopy profile widens then narrows for a dome shape.<br>
	 * Total height: ~8-9 blocks.
	 *
	 * <pre>
	 *     L           ← top: cross or single block
	 *    LLL          ← upper: 3×3 minus corners
	 *   LLLLL         ← middle: 5×5 minus corners, edges randomized
	 *   LLWLL         ← lower: 5×5 minus corners, edges randomized
	 *    LLL          ← bottom fringe: 3×3 minus corners
	 *     W
	 *     W
	 *     W
	 *     D
	 * </pre>
	 */
	private static void placeSmallOak(Region region, int x, int z, int groundY, Random random)
	{
		final int trunkHeight = 4 + random.nextInt(2); // 4-5.
		
		// Replace grass with dirt under trunk.
		region.setBlock(x, groundY, z, Block.DIRT);
		
		// Place trunk.
		final int trunkTop = groundY + trunkHeight;
		for (int y = groundY + 1; y <= trunkTop; y++)
		{
			placeTrunk(region, x, y, z);
		}
		
		// Canopy layers (bottom to top) - dome profile.
		// Layer 0: bottom fringe - 3×3 minus corners.
		placeRoundedLayer(region, x, trunkTop - 1, z, 1, random);
		
		// Layer 1: lower wide - 5×5 rounded.
		placeRoundedLayer(region, x, trunkTop, z, 2, random);
		
		// Layer 2: upper wide - 5×5 rounded.
		placeRoundedLayer(region, x, trunkTop + 1, z, 2, random);
		
		// Layer 3: upper - 3×3 minus corners.
		placeRoundedLayer(region, x, trunkTop + 2, z, 1, random);
		
		// Layer 4: top - single block or cross (but keep it attached to the layer below)
		placeTopLayer(region, x, trunkTop + 3, z, random);
	}
	
	// ========================================================
	// TALL_OAK (25%) - Large rounded canopy
	// ========================================================
	
	/**
	 * Places a tall oak tree with a wide layered canopy.<br>
	 * Trunk: 6-8 blocks. Canopy has a broad dome with 5×5 core layers.<br>
	 * Total height: ~12-15 blocks.
	 *
	 * <pre>
	 *     L           ← top cross
	 *    LLL          ← upper: 3×3 rounded
	 *   LLLLL         ← wide 5×5 rounded
	 *   LLWLL         ← wide 5×5 rounded (trunk through center)
	 *   LLWLL         ← wide 5×5 rounded
	 *   LLWLL         ← wide 5×5 rounded
	 *    LLL          ← bottom fringe: 3×3 rounded
	 *     W
	 *     W
	 *     W
	 *     W
	 *     W
	 *     D
	 * </pre>
	 */
	private static void placeTallOak(Region region, int x, int z, int groundY, Random random)
	{
		final int trunkHeight = 6 + random.nextInt(3); // 6-8.
		
		// Replace grass with dirt under trunk.
		region.setBlock(x, groundY, z, Block.DIRT);
		
		// Place trunk.
		final int trunkTop = groundY + trunkHeight;
		for (int y = groundY + 1; y <= trunkTop; y++)
		{
			placeTrunk(region, x, y, z);
		}
		
		// Canopy layers - tall dome profile.
		// Bottom fringe.
		placeRoundedLayer(region, x, trunkTop - 3, z, 1, random);
		
		// Wide layers (4 layers of 5×5 rounded).
		placeRoundedLayer(region, x, trunkTop - 2, z, 2, random);
		placeRoundedLayer(region, x, trunkTop - 1, z, 2, random);
		placeRoundedLayer(region, x, trunkTop, z, 2, random);
		placeRoundedLayer(region, x, trunkTop + 1, z, 2, random);
		
		// Upper taper.
		placeRoundedLayer(region, x, trunkTop + 2, z, 1, random);
		
		// Top layer (attached).
		placeTopLayer(region, x, trunkTop + 3, z, random);
	}
	
	// ========================================================
	// BIRCH (20%) - Narrow columnar canopy
	// ========================================================
	
	/**
	 * Places a birch tree with a narrow, tall canopy.<br>
	 * Trunk: 5-7 blocks. Canopy is a vertical stack of 3×3 rounded layers.<br>
	 * Distinct narrow silhouette compared to oaks. Total height: ~10-12 blocks.
	 *
	 * <pre>
	 *    L            ← top cross
	 *   LLL           ← 3×3 rounded
	 *   LWL           ← 3×3 rounded (trunk)
	 *   LWL           ← 3×3 rounded (trunk)
	 *   LLL           ← 3×3 rounded
	 *    W
	 *    W
	 *    W
	 *    W
	 *    D
	 * </pre>
	 */
	private static void placeBirch(Region region, int x, int z, int groundY, Random random)
	{
		final int trunkHeight = 5 + random.nextInt(3); // 5-7.
		
		// Replace grass with dirt under trunk.
		region.setBlock(x, groundY, z, Block.DIRT);
		
		// Place trunk.
		final int trunkTop = groundY + trunkHeight;
		for (int y = groundY + 1; y <= trunkTop; y++)
		{
			placeTrunk(region, x, y, z);
		}
		
		// Canopy: vertical stack of 3×3 rounded layers.
		// Bottom.
		placeRoundedLayer(region, x, trunkTop - 2, z, 1, random);
		
		// Middle layers (trunk visible).
		placeRoundedLayer(region, x, trunkTop - 1, z, 1, random);
		placeRoundedLayer(region, x, trunkTop, z, 1, random);
		
		// Upper.
		placeRoundedLayer(region, x, trunkTop + 1, z, 1, random);
		
		// Top.
		placeTopLayer(region, x, trunkTop + 2, z, random);
	}
	
	// ========================================================
	// SHRUB (15%) - Short but elevated canopy
	// ========================================================
	
	/**
	 * Places a shrub tree with a short trunk and compact canopy.<br>
	 * Trunk: 2-3 blocks. Canopy is a small rounded dome.<br>
	 * Total height: ~5-6 blocks.
	 *
	 * <pre>
	 *    L            ← top cross
	 *   LLL           ← 3×3 rounded
	 *   LWL           ← 3×3 at trunk top
	 *    W
	 *    W
	 *    D
	 * </pre>
	 */
	private static void placeShrub(Region region, int x, int z, int groundY, Random random)
	{
		final int trunkHeight = 2 + random.nextInt(2); // 2-3.
		
		// Replace grass with dirt under trunk.
		region.setBlock(x, groundY, z, Block.DIRT);
		
		// Place trunk.
		final int trunkTop = groundY + trunkHeight;
		for (int y = groundY + 1; y <= trunkTop; y++)
		{
			placeTrunk(region, x, y, z);
		}
		
		// Compact canopy.
		placeRoundedLayer(region, x, trunkTop, z, 1, random);
		placeRoundedLayer(region, x, trunkTop + 1, z, 1, random);
		
		// Top.
		placeTopLayer(region, x, trunkTop + 2, z, random);
	}
	
	// ========================================================
	// SPRUCE (20%) - Conical layered canopy
	// ========================================================
	
	/**
	 * Places a spruce tree with a distinctive conical silhouette.<br>
	 * Trunk: 7-10 blocks. Canopy is a tapering cone of layered leaf sections separated by 1-block trunk gaps,<br>
	 * giving the classic tiered spruce look.<br>
	 * Total height: ~13-17 blocks.
	 *
	 * <pre>
	 *      L          ← spike top: single leaf (currently commented)
	 *     LLL         ← tip: 3×3 rounded
	 *      W          ← trunk gap
	 *    LLLLL        ← mid section: 5×5 rounded
	 *    LLWLL        ← mid section: 5×5 rounded
	 *      W          ← trunk gap
	 *    LLLLL        ← lower section: 5×5 rounded
	 *    LLWLL        ← lower section: 5×5 rounded
	 *    LLLLL        ← base section: 5×5 rounded
	 *      W
	 *      W
	 *      W
	 *      W
	 *      D
	 * </pre>
	 */
	private static void placeSpruce(Region region, int x, int z, int groundY, Random random)
	{
		final int trunkHeight = 7 + random.nextInt(4); // 7-10.
		
		// Replace grass with dirt under trunk.
		region.setBlock(x, groundY, z, Block.DIRT);
		
		// Place full trunk (runs through the entire canopy).
		final int trunkTop = groundY + trunkHeight;
		for (int y = groundY + 1; y <= trunkTop; y++)
		{
			placeTrunk(region, x, y, z);
		}
		
		// Calculate where the canopy begins (leave 2-3 bare trunk blocks at the bottom).
		final int bareTrunk = 2 + random.nextInt(2); // 2-3 bare trunk blocks.
		final int canopyBase = groundY + 1 + bareTrunk;
		
		// Build tiered sections: wide at bottom, narrow at top.
		int currentY = canopyBase;
		int tier = 0;
		
		while (currentY <= trunkTop)
		{
			// Radius decreases as we go up: 2 for first tiers, then 1.
			final int radius = (tier < 2) ? 2 : 1;
			
			// Place 2 layers for this tier (or 1 for the top tier).
			int layersInTier = (tier < 3) ? 2 : 1;
			
			for (int dy = 0; dy < layersInTier; dy++)
			{
				if (currentY + dy <= trunkTop + 1) // Allow one layer above trunk.
				{
					placeRoundedLayer(region, x, currentY + dy, z, radius, random);
				}
			}
			
			// Move up past this tier, leaving a 1-block gap.
			currentY += layersInTier + 1;
			tier++;
			
			// Safety check to prevent infinite loop.
			if (tier > 5)
			{
				break;
			}
		}
		
		// Add a top spike (single block) above the highest layer.
		// final int spikeY = trunkTop + 2;
		// if (spikeY < Region.SIZE_Y)
		// {
		// Check if there's leaf support below.
		// boolean hasSupport = false;
		// for (int dy = 1; dy <= 2 && !hasSupport; dy++)
		// {
		// if (spikeY - dy >= 0 && region.getBlock(x, spikeY - dy, z) == Block.LEAVES)
		// {
		// hasSupport = true;
		// }
		// }
		//
		// if (hasSupport)
		// {
		// placeLeaf(region, x, spikeY, z);
		// }
		// }
		
		// Re-place trunk through canopy (trunk takes priority over leaves).
		for (int y = canopyBase; y <= trunkTop; y++)
		{
			placeTrunk(region, x, y, z);
		}
	}
	
	// ========================================================
	// Block Placement Helpers
	// ========================================================
	
	/**
	 * Places a WOOD block at the given position.<br>
	 * Trunk takes priority: overwrites leaves and decorations.
	 */
	private static void placeTrunk(Region region, int x, int y, int z)
	{
		if (!region.isInBounds(x, y, z))
		{
			return;
		}
		
		region.setBlock(x, y, z, Block.WOOD);
	}
	
	/**
	 * Places a LEAVES block at the given position.<br>
	 * Only overwrites AIR and other LEAVES. Does not overwrite WOOD or solid blocks.
	 */
	private static void placeLeaf(Region region, int x, int y, int z)
	{
		if (!region.isInBounds(x, y, z))
		{
			return;
		}
		
		final Block existing = region.getBlock(x, y, z);
		if (existing == Block.AIR || existing == Block.LEAVES)
		{
			region.setBlock(x, y, z, Block.LEAVES);
		}
	}
	
	/**
	 * Places a rounded leaf layer centered on (centerX, y, centerZ) with the given radius.<br>
	 * Radius 1 = 3×3 shape, radius 2 = 5×5 shape.<br>
	 * Corners are always removed for a rounded look.<br>
	 * Edge blocks on the perimeter have a 30% chance to be removed, creating organic variation.
	 * @param region the region to modify
	 * @param centerX local X center
	 * @param y the Y level for this layer
	 * @param centerZ local Z center
	 * @param radius leaf spread (1 or 2)
	 * @param random seeded random for variation
	 */
	private static void placeRoundedLayer(Region region, int centerX, int y, int centerZ, int radius, Random random)
	{
		if (y < 0 || y >= Region.SIZE_Y)
		{
			return;
		}
		
		// Check if this layer has support from below (prevents floating leaves).
		boolean hasSupport = false;
		for (int dx = -radius; dx <= radius && !hasSupport; dx++)
		{
			for (int dz = -radius; dz <= radius && !hasSupport; dz++)
			{
				final int checkX = centerX + dx;
				final int checkZ = centerZ + dz;
				if (region.isInBounds(checkX, y - 1, checkZ))
				{
					final Block below = region.getBlock(checkX, y - 1, checkZ);
					if (below == Block.WOOD || below == Block.LEAVES)
					{
						hasSupport = true;
					}
				}
			}
		}
		
		// Don't place floating leaves.
		if (!hasSupport && y > 0)
		{
			// Check if this is the top layer - allow it if it's directly above the trunk.
			boolean hasTrunkBelow = false;
			for (int dy = 1; dy <= 2 && !hasTrunkBelow; dy++)
			{
				if (y - dy >= 0 && region.getBlock(centerX, y - dy, centerZ) == Block.WOOD)
				{
					hasTrunkBelow = true;
				}
			}
			
			if (!hasTrunkBelow)
			{
				return; // Skip this layer if it would float.
			}
		}
		
		for (int dx = -radius; dx <= radius; dx++)
		{
			for (int dz = -radius; dz <= radius; dz++)
			{
				final int absDx = Math.abs(dx);
				final int absDz = Math.abs(dz);
				
				// Always skip corners (both offsets at max radius).
				if (radius >= 2 && absDx == radius && absDz == radius)
				{
					continue;
				}
				
				// Edge randomization: blocks on the perimeter have a chance to be skipped.
				if (radius >= 2 && (absDx == radius || absDz == radius))
				{
					// 30% chance to skip outer edge blocks for organic variation.
					if (random.nextDouble() < 0.30)
					{
						continue;
					}
				}
				
				final int lx = centerX + dx;
				final int lz = centerZ + dz;
				
				placeLeaf(region, lx, y, lz);
			}
		}
	}
	
	/**
	 * Places a top layer that's guaranteed to be supported.<br>
	 * Either a single block or a plus-shaped cross, but ensures connection to the layer below.
	 */
	private static void placeTopLayer(Region region, int centerX, int y, int centerZ, Random random)
	{
		if (y < 0 || y >= Region.SIZE_Y)
		{
			return;
		}
		
		// Check if there's support below.
		boolean hasSupport = false;
		for (int dx = -1; dx <= 1 && !hasSupport; dx++)
		{
			for (int dz = -1; dz <= 1 && !hasSupport; dz++)
			{
				final int checkX = centerX + dx;
				final int checkZ = centerZ + dz;
				if (region.isInBounds(checkX, y - 1, checkZ))
				{
					final Block below = region.getBlock(checkX, y - 1, checkZ);
					if (below == Block.WOOD || below == Block.LEAVES)
					{
						hasSupport = true;
					}
				}
			}
		}
		
		if (!hasSupport)
		{
			return; // Don't place floating top.
		}
		
		// Always place center block.
		placeLeaf(region, centerX, y, centerZ);
		
		// 60% chance for a plus-shaped cross, but only if those positions have support.
		if (random.nextDouble() < 0.60)
		{
			// Check each direction for support before placing.
			if (y - 1 >= 0 && region.getBlock(centerX - 1, y - 1, centerZ) != Block.AIR)
			{
				placeLeaf(region, centerX - 1, y, centerZ);
			}
			
			if (y - 1 >= 0 && region.getBlock(centerX + 1, y - 1, centerZ) != Block.AIR)
			{
				placeLeaf(region, centerX + 1, y, centerZ);
			}
			
			if (y - 1 >= 0 && region.getBlock(centerX, y - 1, centerZ - 1) != Block.AIR)
			{
				placeLeaf(region, centerX, y, centerZ - 1);
			}
			
			if (y - 1 >= 0 && region.getBlock(centerX, y - 1, centerZ + 1) != Block.AIR)
			{
				placeLeaf(region, centerX, y, centerZ + 1);
			}
		}
	}
}
