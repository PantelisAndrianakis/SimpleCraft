package simplecraft.world;

import java.util.ArrayList;
import java.util.List;

import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.util.BufferUtils;

import simplecraft.world.Block.Face;

/**
 * Static utility class that builds jME3 Mesh objects from chunk block data.<br>
 * Pure function: data in, Mesh out. No instance state.
 * @author Pantelis Andrianakis
 * @since February 21st 2026
 */
public class ChunkMeshBuilder
{
	// ========================================================
	// Face Vertex Positions (relative to block origin 0,0,0)
	// ========================================================
	// Each face has 4 vertices forming a quad. jME3 is Y-up.
	// Winding order is counter-clockwise when viewed from outside.
	
	private static final Vector3f[][] FACE_POSITIONS =
	{
		// TOP (Y+ face, y=1)
		{
			new Vector3f(0, 1, 0),
			new Vector3f(0, 1, 1),
			new Vector3f(1, 1, 1),
			new Vector3f(1, 1, 0)
		},
		// BOTTOM (Y- face, y=0)
		{
			new Vector3f(0, 0, 1),
			new Vector3f(0, 0, 0),
			new Vector3f(1, 0, 0),
			new Vector3f(1, 0, 1)
		},
		// NORTH (Z+ face, z=1)
		{
			new Vector3f(1, 0, 1),
			new Vector3f(1, 1, 1),
			new Vector3f(0, 1, 1),
			new Vector3f(0, 0, 1)
		},
		// SOUTH (Z- face, z=0)
		{
			new Vector3f(0, 0, 0),
			new Vector3f(0, 1, 0),
			new Vector3f(1, 1, 0),
			new Vector3f(1, 0, 0)
		},
		// EAST (X+ face, x=1)
		{
			new Vector3f(1, 0, 0),
			new Vector3f(1, 1, 0),
			new Vector3f(1, 1, 1),
			new Vector3f(1, 0, 1)
		},
		// WEST (X- face, x=0)
		{
			new Vector3f(0, 0, 1),
			new Vector3f(0, 1, 1),
			new Vector3f(0, 1, 0),
			new Vector3f(0, 0, 0)
		}
	};
	
	// ========================================================
	// Face Normals
	// ========================================================
	
	private static final Vector3f[] FACE_NORMALS =
	{
		new Vector3f(0, 1, 0), // TOP
		new Vector3f(0, -1, 0), // BOTTOM
		new Vector3f(0, 0, 1), // NORTH
		new Vector3f(0, 0, -1), // SOUTH
		new Vector3f(1, 0, 0), // EAST
		new Vector3f(-1, 0, 0) // WEST
	};
	
	// ========================================================
	// Face UV Coordinates (unit UV space)
	// ========================================================
	
	// @formatter:off
	private static final float[] FACE_UVS =
	{
		// Per vertex: u, v
		0, 0,
		0, 1,
		1, 1,
		1, 0
	};
	// @formatter:on
	
	// ========================================================
	// Face Triangle Indices (two triangles per quad)
	// ========================================================
	// Relative to first vertex of the face (0-based per face).
	
	// @formatter:off
	private static final int[] FACE_INDICES = { 0, 1, 2, 0, 2, 3 };
	// @formatter:on
	
	// ========================================================
	// Neighbor Offsets (indexed by Face ordinal)
	// ========================================================
	
	// @formatter:off
	private static final int[][] NEIGHBOR_OFFSETS =
	{
		{ 0, 1, 0 },  // TOP
		{ 0, -1, 0 }, // BOTTOM
		{ 0, 0, 1 },  // NORTH
		{ 0, 0, -1 }, // SOUTH
		{ 1, 0, 0 },  // EAST
		{ -1, 0, 0 }  // WEST
	};
	// @formatter:on
	
	// ========================================================
	// Private Constructor (static utility class)
	// ========================================================
	
	private ChunkMeshBuilder()
	{
		// Static utility class — do not instantiate.
	}
	
	// ========================================================
	// Face Visibility
	// ========================================================
	
	/**
	 * Returns true if the given face of the block at (x, y, z) should be rendered.<br>
	 * A face is visible if the neighbor block in that direction is AIR, transparent, or out-of-bounds.
	 */
	public static boolean isFaceVisible(Chunk chunk, int x, int y, int z, Face face)
	{
		final int[] offset = NEIGHBOR_OFFSETS[face.ordinal()];
		final int nx = x + offset[0];
		final int ny = y + offset[1];
		final int nz = z + offset[2];
		
		// Out-of-bounds neighbors are treated as air (face is visible).
		if (!chunk.isInBounds(nx, ny, nz))
		{
			return true;
		}
		
		final Block neighbor = chunk.getBlock(nx, ny, nz);
		return neighbor == Block.AIR || neighbor.isTransparent();
	}
	
	// ========================================================
	// Single Cube Test Mesh
	// ========================================================
	
	/**
	 * Generates a Mesh for a single block at (0,0,0) with all 6 faces visible.<br>
	 * Used for testing that the mesh pipeline works before building full chunks.
	 */
	public static Mesh buildSingleCube()
	{
		final List<Float> positions = new ArrayList<>();
		final List<Float> normals = new ArrayList<>();
		final List<Float> texCoords = new ArrayList<>();
		final List<Integer> indices = new ArrayList<>();
		
		for (Face face : Face.values())
		{
			addFace(positions, normals, texCoords, indices, 0, 0, 0, face);
		}
		
		return buildMesh(positions, normals, texCoords, indices);
	}
	
	// ========================================================
	// Face Building
	// ========================================================
	
	/**
	 * Appends one face (4 vertices, 6 indices) to the buffer lists.
	 * @param positions vertex position list (x, y, z per vertex)
	 * @param normals vertex normal list (x, y, z per vertex)
	 * @param texCoords vertex UV list (u, v per vertex)
	 * @param indices triangle index list
	 * @param bx block X position (local)
	 * @param by block Y position (local)
	 * @param bz block Z position (local)
	 * @param face which face to add
	 */
	public static void addFace(List<Float> positions, List<Float> normals, List<Float> texCoords, List<Integer> indices, int bx, int by, int bz, Face face)
	{
		final int faceOrdinal = face.ordinal();
		final int baseIndex = positions.size() / 3;
		
		// Add 4 vertices.
		final Vector3f[] facePositions = FACE_POSITIONS[faceOrdinal];
		final Vector3f normal = FACE_NORMALS[faceOrdinal];
		
		for (int v = 0; v < 4; v++)
		{
			// Position (offset by block coordinates).
			positions.add(facePositions[v].x + bx);
			positions.add(facePositions[v].y + by);
			positions.add(facePositions[v].z + bz);
			
			// Normal (same for all 4 vertices of a face).
			normals.add(normal.x);
			normals.add(normal.y);
			normals.add(normal.z);
			
			// UV coordinates.
			texCoords.add(FACE_UVS[v * 2]);
			texCoords.add(FACE_UVS[v * 2 + 1]);
		}
		
		// Add 6 indices (two triangles).
		for (int i = 0; i < 6; i++)
		{
			indices.add(baseIndex + FACE_INDICES[i]);
		}
	}
	
	// ========================================================
	// Mesh Assembly
	// ========================================================
	
	/**
	 * Converts the buffer lists into a jME3 Mesh.
	 */
	public static Mesh buildMesh(List<Float> positions, List<Float> normals, List<Float> texCoords, List<Integer> indices)
	{
		final Mesh mesh = new Mesh();
		
		// Convert Lists to primitive arrays.
		final float[] posArray = toFloatArray(positions);
		final float[] normArray = toFloatArray(normals);
		final float[] uvArray = toFloatArray(texCoords);
		final int[] idxArray = toIntArray(indices);
		
		mesh.setBuffer(Type.Position, 3, BufferUtils.createFloatBuffer(posArray));
		mesh.setBuffer(Type.Normal, 3, BufferUtils.createFloatBuffer(normArray));
		mesh.setBuffer(Type.TexCoord, 2, BufferUtils.createFloatBuffer(uvArray));
		mesh.setBuffer(Type.Index, 3, BufferUtils.createIntBuffer(idxArray));
		mesh.updateBound();
		
		return mesh;
	}
	
	// ========================================================
	// Array Conversion Utilities
	// ========================================================
	
	private static float[] toFloatArray(List<Float> list)
	{
		final float[] array = new float[list.size()];
		for (int i = 0; i < list.size(); i++)
		{
			array[i] = list.get(i);
		}
		
		return array;
	}
	
	private static int[] toIntArray(List<Integer> list)
	{
		final int[] array = new int[list.size()];
		for (int i = 0; i < list.size(); i++)
		{
			array[i] = list.get(i);
		}
		
		return array;
	}
}
