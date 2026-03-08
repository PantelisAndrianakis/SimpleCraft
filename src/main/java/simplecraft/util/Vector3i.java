package simplecraft.util;

/**
 * Lightweight immutable 3D integer coordinate.<br>
 * Used for block positions throughout the tile entity system.<br>
 * jME3 provides {@code Vector3f} but no integer equivalent.
 * @author Pantelis Andrianakis
 * @since March 8th 2026
 */
public class Vector3i
{
	public final int x;
	public final int y;
	public final int z;
	
	public Vector3i(int x, int y, int z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
	}
	
	@Override
	public int hashCode()
	{
		// Spread bits to reduce collisions for typical block coordinate ranges.
		return (x * 73856093) ^ (y * 19349663) ^ (z * 83492791);
	}
	
	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
		{
			return true;
		}
		
		if (!(obj instanceof Vector3i))
		{
			return false;
		}
		
		final Vector3i other = (Vector3i) obj;
		return x == other.x && y == other.y && z == other.z;
	}
	
	@Override
	public String toString()
	{
		return "[" + x + ", " + y + ", " + z + "]";
	}
}
