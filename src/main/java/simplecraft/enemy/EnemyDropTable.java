package simplecraft.enemy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import simplecraft.enemy.Enemy.EnemyType;
import simplecraft.item.ItemInstance;
import simplecraft.item.ItemRegistry;
import simplecraft.item.ItemTemplate;

/**
 * Defines drop tables for each enemy type and rolls loot on death.<br>
 * <br>
 * Each enemy type has a set of independent drop entries, each with its own<br>
 * item ID, quantity range and probability. Rolls are made independently<br>
 * for each entry, so an enemy can drop multiple different items in a single death.<br>
 * <br>
 * The Random is seeded per kill from a combination of enemy position and a<br>
 * monotonically increasing kill counter, ensuring varied but reproducible drops<br>
 * during the same session.
 * @author Pantelis Andrianakis
 * @since March 15th 2026
 */
public class EnemyDropTable
{
	/** Monotonically increasing counter to vary the seed across kills at the same position. */
	private static long _killCounter = 0;
	
	/**
	 * A single entry in a drop table: item ID, min/max count and drop chance.
	 */
	private static class DropEntry
	{
		final String itemId;
		final int minCount;
		final int maxCount;
		final float chance;
		
		DropEntry(String itemId, int minCount, int maxCount, float chance)
		{
			this.itemId = itemId;
			this.minCount = minCount;
			this.maxCount = maxCount;
			this.chance = chance;
		}
	}
	
	// ------------------------------------------------------------------
	// Drop tables per enemy type.
	// ------------------------------------------------------------------
	
	/** Zombie: 1-2 Cooked Meat (60%), 1 Iron Nugget (20%). */
	private static final DropEntry[] ZOMBIE_DROPS =
	{
		new DropEntry("cooked_meat", 1, 2, 0.60f),
		new DropEntry("iron_nugget", 1, 1, 0.20f),
	};
	
	/** Skeleton: 1-3 Stone Shard (70%), 1 Wood Plank (40%). */
	private static final DropEntry[] SKELETON_DROPS =
	{
		new DropEntry("stone_shard", 1, 3, 0.70f),
		new DropEntry("wood_plank", 1, 1, 0.40f),
	};
	
	/** Wolf: 1-2 Cooked Meat (80%). */
	private static final DropEntry[] WOLF_DROPS =
	{
		new DropEntry("cooked_meat", 1, 2, 0.80f),
	};
	
	/** Spider: 1 Iron Nugget (30%). */
	private static final DropEntry[] SPIDER_DROPS =
	{
		new DropEntry("iron_nugget", 1, 1, 0.30f),
	};
	
	/** Slime: 1 Health Potion (25%). */
	private static final DropEntry[] SLIME_DROPS =
	{
		new DropEntry("health_potion", 1, 1, 0.25f),
	};
	
	/** Piranha: 1 Cooked Meat (40%). */
	private static final DropEntry[] PIRANHA_DROPS =
	{
		new DropEntry("cooked_meat", 1, 1, 0.40f),
	};
	
	/** Dragon: 1 Recall Orb (100%), 5 Iron Nuggets (100%). */
	private static final DropEntry[] DRAGON_DROPS =
	{
		new DropEntry("recall_orb", 1, 1, 1.00f),
		new DropEntry("iron_ingot", 5, 5, 1.00f),
	};
	
	/**
	 * Rolls the drop table for the given enemy type and returns a list of item instances<br>
	 * that should be spawned as world drops. Each entry is rolled independently.<br>
	 * Returns an empty list if no drops are rolled.
	 * @param type the enemy type that was killed
	 * @param enemyX the enemy's world X position (used for seed variation)
	 * @param enemyZ the enemy's world Z position (used for seed variation)
	 * @return list of item instances to drop (may be empty, never null)
	 */
	public static List<ItemInstance> rollDrops(EnemyType type, float enemyX, float enemyZ)
	{
		final DropEntry[] table = getTable(type);
		final List<ItemInstance> results = new ArrayList<>();
		
		if (table == null || table.length == 0)
		{
			return results;
		}
		
		// Seed from position and kill counter for varied but deterministic drops.
		final long seed = Float.floatToIntBits(enemyX) * 341873128712L + Float.floatToIntBits(enemyZ) * 132897987541L + _killCounter;
		_killCounter++;
		final Random rng = new Random(seed);
		
		for (DropEntry entry : table)
		{
			if (rng.nextFloat() < entry.chance)
			{
				final ItemTemplate template = ItemRegistry.get(entry.itemId);
				if (template == null)
				{
					System.err.println("EnemyDropTable: Unknown item ID '" + entry.itemId + "' in drop table for " + type.name());
					continue;
				}
				
				// Roll quantity within the min-max range (inclusive).
				final int count;
				if (entry.minCount >= entry.maxCount)
				{
					count = entry.minCount;
				}
				else
				{
					count = entry.minCount + rng.nextInt(entry.maxCount - entry.minCount + 1);
				}
				
				results.add(new ItemInstance(template, count));
			}
		}
		
		return results;
	}
	
	/**
	 * Returns the drop table for the given enemy type, or null if none defined.
	 * @param type the enemy type
	 * @return the drop entry array, or null
	 */
	private static DropEntry[] getTable(EnemyType type)
	{
		switch (type)
		{
			case ZOMBIE:
			{
				return ZOMBIE_DROPS;
			}
			case SKELETON:
			{
				return SKELETON_DROPS;
			}
			case WOLF:
			{
				return WOLF_DROPS;
			}
			case SPIDER:
			{
				return SPIDER_DROPS;
			}
			case SLIME:
			{
				return SLIME_DROPS;
			}
			case PIRANHA:
			{
				return PIRANHA_DROPS;
			}
			case DRAGON:
			{
				return DRAGON_DROPS;
			}
			default:
			{
				return null;
			}
		}
	}
}
