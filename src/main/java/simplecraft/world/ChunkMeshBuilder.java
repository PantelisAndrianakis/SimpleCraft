package simplecraft.world;

import java.util.ArrayList;
import java.util.List;

import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.util.BufferUtils;

import simplecraft.world.Block.Face;
import simplecraft.world.Block.RenderMode;

/**
 * Static utility class that builds jME3 Mesh objects from chunk block data.<br>
 * Generates three separate meshes per chunk: opaque, transparent, and billboard.<br>
 * Pure function: data in, Mesh out. No instance state.
 * @author Pantelis Andrianakis
 * @since February 21st 2026
 */
public class ChunkMeshBuilder
{
	// ========================================================
	// Atlas Constants
	// ========================================================
	
	/** Number of tiles per row/column in the texture atlas (from {@link TextureAtlas}). */
	private static final int ATLAS_GRID_SIZE = TextureAtlas.GRID_SIZE;
	
	/** UV size of one tile in the atlas. */
	private static final float TILE_UV = 1.0f / ATLAS_GRID_SIZE;
	
	/** UV inset to prevent atlas bleeding (half-pixel padding). */
	private static final float UV_PADDING = 0.001f;
	
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
		// V is flipped so V=0 (top of PNG) maps to vertex at y=1 (top of block face).
		// Without this flip, flipY=false causes textures to render upside-down on side faces.
		0, 1,
		0, 0,
		1, 0,
		1, 1
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
	// Billboard Quad Positions (relative to block origin)
	// ========================================================
	// Two quads crossing at 45 degrees through the block center.
	// Inset slightly (0.15) from edges to look more natural.
	
	private static final Vector3f[][] BILLBOARD_QUAD_A =
	{
		{
			new Vector3f(0.15f, 0, 0.15f),
			new Vector3f(0.15f, 1, 0.15f),
			new Vector3f(0.85f, 1, 0.85f),
			new Vector3f(0.85f, 0, 0.85f)
		}
	};
	
	private static final Vector3f[][] BILLBOARD_QUAD_B =
	{
		{
			new Vector3f(0.85f, 0, 0.15f),
			new Vector3f(0.85f, 1, 0.15f),
			new Vector3f(0.15f, 1, 0.85f),
			new Vector3f(0.15f, 0, 0.85f)
		}
	};
	
	// ========================================================
	// Chunk Mesh Result Container
	// ========================================================
	
	/**
	 * Holds the three meshes generated from a chunk.<br>
	 * Any mesh may be null if the chunk contains no blocks of that render mode.
	 */
	public static class ChunkMeshResult
	{
		private final Mesh _opaqueMesh;
		private final Mesh _transparentMesh;
		private final Mesh _billboardMesh;
		
		public ChunkMeshResult(Mesh opaqueMesh, Mesh transparentMesh, Mesh billboardMesh)
		{
			_opaqueMesh = opaqueMesh;
			_transparentMesh = transparentMesh;
			_billboardMesh = billboardMesh;
		}
		
		public Mesh getOpaqueMesh()
		{
			return _opaqueMesh;
		}
		
		public Mesh getTransparentMesh()
		{
			return _transparentMesh;
		}
		
		public Mesh getBillboardMesh()
		{
			return _billboardMesh;
		}
	}
	
	// ========================================================
	// Private Constructor (static utility class)
	// ========================================================
	
	private ChunkMeshBuilder()
	{
		// Static utility class — do not instantiate.
	}
	
	// ========================================================
	// Full Chunk Mesh Building
	// ========================================================
	
	/**
	 * Builds three separate meshes from the chunk's block data:<br>
	 * opaque (CUBE_SOLID), transparent (CUBE_TRANSPARENT), and billboard (CROSS_BILLBOARD).<br>
	 * Each mesh contains only the visible faces after neighbor culling.
	 * @param chunk the chunk to build meshes from
	 * @return a ChunkMeshResult containing the three meshes (any may be null)
	 */
	public static ChunkMeshResult buildChunkMesh(Chunk chunk)
	{
		// Separate buffer lists for each render mode.
		final List<Float> opaquePositions = new ArrayList<>();
		final List<Float> opaqueNormals = new ArrayList<>();
		final List<Float> opaqueTexCoords = new ArrayList<>();
		final List<Integer> opaqueIndices = new ArrayList<>();
		
		final List<Float> transparentPositions = new ArrayList<>();
		final List<Float> transparentNormals = new ArrayList<>();
		final List<Float> transparentTexCoords = new ArrayList<>();
		final List<Integer> transparentIndices = new ArrayList<>();
		
		final List<Float> billboardPositions = new ArrayList<>();
		final List<Float> billboardNormals = new ArrayList<>();
		final List<Float> billboardTexCoords = new ArrayList<>();
		final List<Integer> billboardIndices = new ArrayList<>();
		
		// Iterate every block in the chunk.
		for (int x = 0; x < Chunk.SIZE_XZ; x++)
		{
			for (int y = 0; y < Chunk.SIZE_Y; y++)
			{
				for (int z = 0; z < Chunk.SIZE_XZ; z++)
				{
					final Block block = chunk.getBlock(x, y, z);
					if (block == Block.AIR)
					{
						continue;
					}
					
					switch (block.getRenderMode())
					{
						case CUBE_SOLID:
						{
							for (Face face : Face.values())
							{
								if (isFaceVisible(chunk, x, y, z, face))
								{
									addFace(opaquePositions, opaqueNormals, opaqueTexCoords, opaqueIndices, x, y, z, face, block);
								}
							}
							break;
						}
						case CUBE_TRANSPARENT:
						{
							for (Face face : Face.values())
							{
								if (isTransparentFaceVisible(chunk, x, y, z, face, block))
								{
									addFace(transparentPositions, transparentNormals, transparentTexCoords, transparentIndices, x, y, z, face, block);
								}
							}
							break;
						}
						case CROSS_BILLBOARD:
						{
							addBillboard(billboardPositions, billboardNormals, billboardTexCoords, billboardIndices, x, y, z, block);
							break;
						}
					}
				}
			}
		}
		
		// Build meshes from buffer lists (null if empty).
		final Mesh opaqueMesh = opaqueIndices.isEmpty() ? null : assembleMesh(opaquePositions, opaqueNormals, opaqueTexCoords, opaqueIndices);
		final Mesh transparentMesh = transparentIndices.isEmpty() ? null : assembleMesh(transparentPositions, transparentNormals, transparentTexCoords, transparentIndices);
		final Mesh billboardMesh = billboardIndices.isEmpty() ? null : assembleMesh(billboardPositions, billboardNormals, billboardTexCoords, billboardIndices);
		
		return new ChunkMeshResult(opaqueMesh, transparentMesh, billboardMesh);
	}
	
	// ========================================================
	// Face Visibility — CUBE_SOLID
	// ========================================================
	
	/**
	 * Returns true if the given face of a CUBE_SOLID block at (x, y, z) should be rendered.<br>
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
		
		// Face visible if neighbor is not a solid cube.
		return neighbor == Block.AIR || neighbor.getRenderMode() != RenderMode.CUBE_SOLID;
	}
	
	// ========================================================
	// Face Visibility — CUBE_TRANSPARENT
	// ========================================================
	
	/**
	 * Returns true if the given face of a CUBE_TRANSPARENT block should be rendered.<br>
	 * Faces between two blocks of the same type are culled (water-water, leaves-leaves).<br>
	 * Faces adjacent to AIR or a different block type are rendered.
	 */
	public static boolean isTransparentFaceVisible(Chunk chunk, int x, int y, int z, Face face, Block block)
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
		
		// Cull faces between identical transparent block types.
		return neighbor != block;
	}
	
	// ========================================================
	// Face Building (with atlas UV mapping)
	// ========================================================
	
	/**
	 * Appends one face (4 vertices, 6 indices) to the buffer lists with atlas-mapped UVs.
	 * @param positions vertex position list (x, y, z per vertex)
	 * @param normals vertex normal list (x, y, z per vertex)
	 * @param texCoords vertex UV list (u, v per vertex)
	 * @param indices triangle index list
	 * @param bx block X position (local)
	 * @param by block Y position (local)
	 * @param bz block Z position (local)
	 * @param face which face to add
	 * @param block the block type (for atlas index lookup)
	 */
	private static void addFace(List<Float> positions, List<Float> normals, List<Float> texCoords, List<Integer> indices, int bx, int by, int bz, Face face, Block block)
	{
		final int faceOrdinal = face.ordinal();
		final int baseIndex = positions.size() / 3;
		
		// Atlas UV region for this face.
		final int atlasIndex = block.getAtlasIndex(face);
		final float uMin;
		final float vMin;
		final float uMax;
		final float vMax;
		
		if (atlasIndex >= 0)
		{
			final float col = (atlasIndex % ATLAS_GRID_SIZE) * TILE_UV;
			final float row = (atlasIndex / ATLAS_GRID_SIZE) * TILE_UV;
			uMin = col + UV_PADDING;
			vMin = row + UV_PADDING;
			uMax = col + TILE_UV - UV_PADDING;
			vMax = row + TILE_UV - UV_PADDING;
		}
		else
		{
			// Fallback for blocks without atlas index (shouldn't happen in practice).
			uMin = UV_PADDING;
			vMin = UV_PADDING;
			uMax = 1.0f - UV_PADDING;
			vMax = 1.0f - UV_PADDING;
		}
		
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
			
			// UV coordinates mapped to atlas region.
			final float unitU = FACE_UVS[v * 2];
			final float unitV = FACE_UVS[v * 2 + 1];
			texCoords.add(uMin + unitU * (uMax - uMin));
			texCoords.add(vMin + unitV * (vMax - vMin));
		}
		
		// Add 6 indices (two triangles).
		for (int i = 0; i < 6; i++)
		{
			indices.add(baseIndex + FACE_INDICES[i]);
		}
	}
	
	// ========================================================
	// Billboard Building
	// ========================================================
	
	/**
	 * Appends two crossed billboard quads (8 vertices, 12 indices) to the buffer lists.<br>
	 * No neighbor checking — billboards are always rendered.
	 */
	private static void addBillboard(List<Float> positions, List<Float> normals, List<Float> texCoords, List<Integer> indices, int bx, int by, int bz, Block block)
	{
		// Atlas UV region (same for all faces on billboard blocks).
		final int atlasIndex = block.getAtlasIndex(Face.TOP); // Face ignored for billboards.
		final float uMin;
		final float vMin;
		final float uMax;
		final float vMax;
		
		if (atlasIndex >= 0)
		{
			final float col = (atlasIndex % ATLAS_GRID_SIZE) * TILE_UV;
			final float row = (atlasIndex / ATLAS_GRID_SIZE) * TILE_UV;
			uMin = col + UV_PADDING;
			vMin = row + UV_PADDING;
			uMax = col + TILE_UV - UV_PADDING;
			vMax = row + TILE_UV - UV_PADDING;
		}
		else
		{
			uMin = UV_PADDING;
			vMin = UV_PADDING;
			uMax = 1.0f - UV_PADDING;
			vMax = 1.0f - UV_PADDING;
		}
		
		// Quad A: diagonal from (0.15, 0, 0.15) to (0.85, 1, 0.85)
		addBillboardQuad(positions, normals, texCoords, indices, bx, by, bz, BILLBOARD_QUAD_A[0], uMin, vMin, uMax, vMax);
		
		// Quad B: diagonal from (0.85, 0, 0.15) to (0.15, 1, 0.85)
		addBillboardQuad(positions, normals, texCoords, indices, bx, by, bz, BILLBOARD_QUAD_B[0], uMin, vMin, uMax, vMax);
	}
	
	/**
	 * Appends a single billboard quad (4 vertices, 6 indices) to the buffer lists.
	 */
	private static void addBillboardQuad(List<Float> positions, List<Float> normals, List<Float> texCoords, List<Integer> indices, int bx, int by, int bz, Vector3f[] quadVerts, float uMin, float vMin, float uMax, float vMax)
	{
		final int baseIndex = positions.size() / 3;
		
		// Calculate normal from quad geometry (cross product of two edges).
		final float edgeAx = quadVerts[3].x - quadVerts[0].x;
		final float edgeAz = quadVerts[3].z - quadVerts[0].z;
		// final float normalX = 0;
		final float normalY = 0;
		// For a vertical quad, the normal is perpendicular in the XZ plane.
		final float length = (float) Math.sqrt(edgeAx * edgeAx + edgeAz * edgeAz);
		final float nx = -edgeAz / length;
		final float nz = edgeAx / length;
		
		// Billboard UV layout: bottom-left, top-left, top-right, bottom-right.
		final float[] quadUVs =
		{
			uMin, //
			vMax, // bottom-left
			uMin, //
			vMin, // top-left
			uMax, //
			vMin, // top-right
			uMax, //
			vMax, // bottom-right
		};
		
		for (int v = 0; v < 4; v++)
		{
			positions.add(quadVerts[v].x + bx);
			positions.add(quadVerts[v].y + by);
			positions.add(quadVerts[v].z + bz);
			
			normals.add(nx);
			normals.add(normalY);
			normals.add(nz);
			
			texCoords.add(quadUVs[v * 2]);
			texCoords.add(quadUVs[v * 2 + 1]);
		}
		
		for (int i = 0; i < 6; i++)
		{
			indices.add(baseIndex + FACE_INDICES[i]);
		}
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
			addFaceUnitUV(positions, normals, texCoords, indices, 0, 0, 0, face);
		}
		
		return assembleMesh(positions, normals, texCoords, indices);
	}
	
	// ========================================================
	// Legacy Face Building (unit UVs for single cube test)
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
	private static void addFaceUnitUV(List<Float> positions, List<Float> normals, List<Float> texCoords, List<Integer> indices, int bx, int by, int bz, Face face)
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
	private static Mesh assembleMesh(List<Float> positions, List<Float> normals, List<Float> texCoords, List<Integer> indices)
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
