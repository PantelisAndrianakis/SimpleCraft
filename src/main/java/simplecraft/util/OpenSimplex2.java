package simplecraft.util;

/**
 * Seeded 2D and 3D gradient noise for terrain generation.<br>
 * Hash-based (no permutation table) so it accepts any long seed directly.<br>
 * Deterministic: same (seed, x, y) always returns the same value.<br>
 * Output range is approximately [-1, 1].
 * @author Pantelis Andrianakis
 * @since February 23rd 2026
 */
public class OpenSimplex2
{
	// ========================================================
	// Constants
	// ========================================================
	
	/** Large primes for hashing grid coordinates. */
	private static final long PRIME_X = 0x5205402B9270C86FL;
	private static final long PRIME_Y = 0x598CD327003817B5L;
	private static final long PRIME_Z = 0x5BCC226E9FA0BACBL;
	private static final long HASH_MUL = 0x53A3F72DEEC546F5L;
	
	/** 2D gradient vectors (8 directions). */
	private static final double[][] GRAD_2D = // @formatter:off
	{
		{1, 0}, {-1, 0}, {0, 1}, {0, -1},
		{1, 1}, {-1, 1}, {1, -1}, {-1, -1}
	};
	
	/** 3D gradient vectors (12 directions). */
	private static final double[][] GRAD_3D =
	{
		{1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0},
		{1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
		{0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1}
	}; // @formatter:on
	
	// ========================================================
	// 2D Noise
	// ========================================================
	
	/**
	 * 2D seeded gradient noise.<br>
	 * Returns a value in approximately [-1, 1].
	 * @param seed the noise seed
	 * @param x the x coordinate
	 * @param y the y coordinate
	 * @return noise value
	 */
	public static float noise2(long seed, double x, double y)
	{
		// Grid cell coordinates.
		final int x0 = fastFloor(x);
		final int y0 = fastFloor(y);
		final int x1 = x0 + 1;
		final int y1 = y0 + 1;
		
		// Fractional position within cell.
		final double fx = x - x0;
		final double fy = y - y0;
		
		// Smoothstep interpolation weights.
		final double u = fade(fx);
		final double v = fade(fy);
		
		// Gradient dot products at each corner.
		final double n00 = gradDot2(hash2(seed, x0, y0), fx, fy);
		final double n10 = gradDot2(hash2(seed, x1, y0), fx - 1, fy);
		final double n01 = gradDot2(hash2(seed, x0, y1), fx, fy - 1);
		final double n11 = gradDot2(hash2(seed, x1, y1), fx - 1, fy - 1);
		
		// Bilinear interpolation.
		final double nx0 = lerp(n00, n10, u);
		final double nx1 = lerp(n01, n11, u);
		return (float) lerp(nx0, nx1, v);
	}
	
	// ========================================================
	// 3D Noise
	// ========================================================
	
	/**
	 * 3D seeded gradient noise.<br>
	 * Returns a value in approximately [-1, 1].
	 * @param seed the noise seed
	 * @param x the x coordinate
	 * @param y the y coordinate
	 * @param z the z coordinate
	 * @return noise value
	 */
	public static float noise3(long seed, double x, double y, double z)
	{
		// Grid cell coordinates.
		final int x0 = fastFloor(x);
		final int y0 = fastFloor(y);
		final int z0 = fastFloor(z);
		final int x1 = x0 + 1;
		final int y1 = y0 + 1;
		final int z1 = z0 + 1;
		
		// Fractional position within cell.
		final double fx = x - x0;
		final double fy = y - y0;
		final double fz = z - z0;
		
		// Smoothstep interpolation weights.
		final double u = fade(fx);
		final double v = fade(fy);
		final double w = fade(fz);
		
		// Gradient dot products at each of the 8 corners.
		final double n000 = gradDot3(hash3(seed, x0, y0, z0), fx, fy, fz);
		final double n100 = gradDot3(hash3(seed, x1, y0, z0), fx - 1, fy, fz);
		final double n010 = gradDot3(hash3(seed, x0, y1, z0), fx, fy - 1, fz);
		final double n110 = gradDot3(hash3(seed, x1, y1, z0), fx - 1, fy - 1, fz);
		final double n001 = gradDot3(hash3(seed, x0, y0, z1), fx, fy, fz - 1);
		final double n101 = gradDot3(hash3(seed, x1, y0, z1), fx - 1, fy, fz - 1);
		final double n011 = gradDot3(hash3(seed, x0, y1, z1), fx, fy - 1, fz - 1);
		final double n111 = gradDot3(hash3(seed, x1, y1, z1), fx - 1, fy - 1, fz - 1);
		
		// Trilinear interpolation.
		final double nx00 = lerp(n000, n100, u);
		final double nx10 = lerp(n010, n110, u);
		final double nx01 = lerp(n001, n101, u);
		final double nx11 = lerp(n011, n111, u);
		final double nxy0 = lerp(nx00, nx10, v);
		final double nxy1 = lerp(nx01, nx11, v);
		return (float) lerp(nxy0, nxy1, w);
	}
	
	// ========================================================
	// Hash Functions
	// ========================================================
	
	/**
	 * Hashes seed and 2D grid coordinates to a deterministic integer.
	 */
	private static int hash2(long seed, int x, int y)
	{
		long h = seed;
		h ^= x * PRIME_X;
		h ^= y * PRIME_Y;
		h *= HASH_MUL;
		h ^= h >>> 32;
		h *= HASH_MUL;
		h ^= h >>> 29;
		return (int) h;
	}
	
	/**
	 * Hashes seed and 3D grid coordinates to a deterministic integer.
	 */
	private static int hash3(long seed, int x, int y, int z)
	{
		long h = seed;
		h ^= x * PRIME_X;
		h ^= y * PRIME_Y;
		h ^= z * PRIME_Z;
		h *= HASH_MUL;
		h ^= h >>> 32;
		h *= HASH_MUL;
		h ^= h >>> 29;
		return (int) h;
	}
	
	// ========================================================
	// Gradient Dot Products
	// ========================================================
	
	/**
	 * Selects a 2D gradient from the hash and computes the dot product with (dx, dy).
	 */
	private static double gradDot2(int hash, double dx, double dy)
	{
		final double[] g = GRAD_2D[(hash & 0x7FFFFFFF) % GRAD_2D.length];
		return g[0] * dx + g[1] * dy;
	}
	
	/**
	 * Selects a 3D gradient from the hash and computes the dot product with (dx, dy, dz).
	 */
	private static double gradDot3(int hash, double dx, double dy, double dz)
	{
		final double[] g = GRAD_3D[(hash & 0x7FFFFFFF) % GRAD_3D.length];
		return g[0] * dx + g[1] * dy + g[2] * dz;
	}
	
	// ========================================================
	// Interpolation
	// ========================================================
	
	/**
	 * Quintic smoothstep curve: 6t^5 - 15t^4 + 10t^3.<br>
	 * Produces smooth interpolation with zero first and second derivatives at 0 and 1.
	 */
	private static double fade(double t)
	{
		return t * t * t * (t * (t * 6 - 15) + 10);
	}
	
	/**
	 * Linear interpolation between a and b by factor t.
	 */
	private static double lerp(double a, double b, double t)
	{
		return a + t * (b - a);
	}
	
	// ========================================================
	// Utility
	// ========================================================
	
	private static int fastFloor(double x)
	{
		final int xi = (int) x;
		return x < xi ? xi - 1 : xi;
	}
}
