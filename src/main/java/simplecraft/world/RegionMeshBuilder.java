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
 * Static utility class that builds jME3 Mesh objects from region block data.<br>
 * Generates three separate meshes per region: opaque, transparent, and billboard.<br>
 * Includes per-vertex color data for sky-light-based lighting with directional face shading.<br>
 * Pure function: data in, Mesh out. No instance state.
 * @author Pantelis Andrianakis
 * @since February 21st 2026
 */
public class RegionMeshBuilder
{
	// ========================================================
	// Atlas Constants.
	// ========================================================
	
	/** Number of tiles per row/column in the texture atlas (from {@link TextureAtlas}). */
	private static final int ATLAS_GRID_SIZE = TextureAtlas.GRID_SIZE;
	
	/** UV size of one tile in the atlas. */
	private static final float TILE_UV = 1.0f / ATLAS_GRID_SIZE;
	
	/** UV inset to prevent atlas bleeding (half-pixel padding). */
	private static final float UV_PADDING = 0.001f;
	
	// ========================================================
	// Face Shade Factors (simulates directional sunlight).
	// ========================================================
	// Indexed by Face ordinal: TOP, BOTTOM, NORTH, SOUTH, EAST, WEST.
	// TOP = direct overhead sun (brightest), EAST = darker side, WEST = sun-facing side.
	
	// @formatter:off
	private static final float[] FACE_SHADE =
	{
		1.0f, // TOP    — direct overhead sunlight.
		0.5f, // BOTTOM — in shadow underneath.
		0.8f, // NORTH  — slight side shadow.
		0.8f, // SOUTH  — slight side shadow.
		0.6f, // EAST   — darker side (sun from southwest).
		0.9f  // WEST   — brighter side (facing sun).
	};
	// @formatter:on
	
	/** Billboard shade factor — uniform brightness, no directional bias on thin cross quads. */
	private static final float BILLBOARD_SHADE = 0.6f;
	
	/** Minimum vertex brightness. Zero allows total darkness underground. */
	private static final float MIN_BRIGHTNESS = 0.001f;
	
	// ========================================================
	// Block Light Warm Tint (torch/campfire glow).
	// ========================================================
	
	/** Red component of warm block light tint. */
	private static final float WARM_TINT_R = 1.0f;
	
	/** Green component of warm block light tint. */
	private static final float WARM_TINT_G = 0.85f;
	
	/** Blue component of warm block light tint. */
	private static final float WARM_TINT_B = 0.55f;
	
	// ========================================================
	// Day/Night Cycle Multipliers (global, updated by PlayingState).
	// ========================================================
	
	/** Sky brightness multiplier from the day/night cycle (0.1 at midnight, 1.0 at noon). */
	private static volatile float _cycleBrightness = 1.0f;
	
	/** Red component of the day/night sky tint. */
	private static volatile float _cycleTintR = 1.0f;
	
	/** Green component of the day/night sky tint. */
	private static volatile float _cycleTintG = 1.0f;
	
	/** Blue component of the day/night sky tint. */
	private static volatile float _cycleTintB = 1.0f;
	
	// ========================================================
	// Face Vertex Positions (relative to block origin 0,0,0).
	// ========================================================
	// Each face has 4 vertices forming a quad. jME3 is Y-up.
	// Winding order is counter-clockwise when viewed from outside.
	// Flattened to float arrays for better performance: [x,y,z] per vertex, 4 vertices per face.
	
	// Original arrays kept for reference but not used in hot paths.
	private static final Vector3f[][] FACE_POSITIONS =
	{
		// TOP (Y+ face, y=1).
		{
			new Vector3f(0, 1, 0),
			new Vector3f(0, 1, 1),
			new Vector3f(1, 1, 1),
			new Vector3f(1, 1, 0)
		},
		// BOTTOM (Y- face, y=0).
		{
			new Vector3f(0, 0, 1),
			new Vector3f(0, 0, 0),
			new Vector3f(1, 0, 0),
			new Vector3f(1, 0, 1)
		},
		// NORTH (Z+ face, z=1).
		{
			new Vector3f(1, 0, 1),
			new Vector3f(1, 1, 1),
			new Vector3f(0, 1, 1),
			new Vector3f(0, 0, 1)
		},
		// SOUTH (Z- face, z=0).
		{
			new Vector3f(0, 0, 0),
			new Vector3f(0, 1, 0),
			new Vector3f(1, 1, 0),
			new Vector3f(1, 0, 0)
		},
		// EAST (X+ face, x=1).
		{
			new Vector3f(1, 0, 0),
			new Vector3f(1, 1, 0),
			new Vector3f(1, 1, 1),
			new Vector3f(1, 0, 1)
		},
		// WEST (X- face, x=0).
		{
			new Vector3f(0, 0, 1),
			new Vector3f(0, 1, 1),
			new Vector3f(0, 1, 0),
			new Vector3f(0, 0, 0)
		}
	};
	
	// ========================================================
	// Face Normals.
	// ========================================================
	
	private static final Vector3f[] FACE_NORMALS =
	{
		new Vector3f(0, 1, 0), // TOP.
		new Vector3f(0, -1, 0), // BOTTOM.
		new Vector3f(0, 0, 1), // NORTH.
		new Vector3f(0, 0, -1), // SOUTH.
		new Vector3f(1, 0, 0), // EAST.
		new Vector3f(-1, 0, 0) // WEST.
	};
	
	private static final float[][] FACE_POSITIONS_FLAT = new float[6][12];
	private static final float[][] FACE_NORMALS_FLAT = new float[6][12]; // 4 vertices × 3 coords, all same normal
	
	static
	{
		// Initialize flattened arrays for better cache locality.
		for (int face = 0; face < 6; face++)
		{
			final Vector3f[] positions = FACE_POSITIONS[face];
			final Vector3f normal = FACE_NORMALS[face];
			
			for (int v = 0; v < 4; v++)
			{
				// Positions.
				FACE_POSITIONS_FLAT[face][v * 3] = positions[v].x;
				FACE_POSITIONS_FLAT[face][v * 3 + 1] = positions[v].y;
				FACE_POSITIONS_FLAT[face][v * 3 + 2] = positions[v].z;
				
				// Normals (same for all 4 vertices).
				FACE_NORMALS_FLAT[face][v * 3] = normal.x;
				FACE_NORMALS_FLAT[face][v * 3 + 1] = normal.y;
				FACE_NORMALS_FLAT[face][v * 3 + 2] = normal.z;
			}
		}
	}
	
	// ========================================================
	// Face UV Coordinates (unit UV space).
	// ========================================================
	
	// @formatter:off
	private static final float[] FACE_UVS =
	{
		// Per vertex: u, v.
		// V is flipped so V=0 (top of PNG) maps to vertex at y=1 (top of block face).
		// Without this flip, flipY=false causes textures to render upside-down on side faces.
		0, 1,
		0, 0,
		1, 0,
		1, 1
	};
	// @formatter:on
	
	// ========================================================
	// Face Triangle Indices (two triangles per quad).
	// ========================================================
	// Relative to first vertex of the face (0-based per face).
	
	// @formatter:off
	private static final int[] FACE_INDICES = { 0, 1, 2, 0, 2, 3 };
	// @formatter:on
	
	// ========================================================
	// Neighbor Offsets (indexed by Face ordinal).
	// ========================================================
	
	// @formatter:off
	private static final int[][] NEIGHBOR_OFFSETS =
	{
		{ 0, 1, 0 },  // TOP.
		{ 0, -1, 0 }, // BOTTOM.
		{ 0, 0, 1 },  // NORTH.
		{ 0, 0, -1 }, // SOUTH.
		{ 1, 0, 0 },  // EAST.
		{ -1, 0, 0 }  // WEST.
	};
	// @formatter:on
	
	// ========================================================
	// Billboard Quad Positions (relative to block origin).
	// ========================================================
	// Two quads crossing at 45 degrees through the block center.
	// Inset slightly (0.15) from edges to look more natural.
	// Flattened for better performance.
	
	private static final float[][] BILLBOARD_QUAD_A_FLAT = new float[1][12];
	private static final float[][] BILLBOARD_QUAD_B_FLAT = new float[1][12];
	
	static
	{
		// Quad A.
		BILLBOARD_QUAD_A_FLAT[0][0] = 0.15f;
		BILLBOARD_QUAD_A_FLAT[0][1] = 0;
		BILLBOARD_QUAD_A_FLAT[0][2] = 0.15f;
		BILLBOARD_QUAD_A_FLAT[0][3] = 0.15f;
		BILLBOARD_QUAD_A_FLAT[0][4] = 1;
		BILLBOARD_QUAD_A_FLAT[0][5] = 0.15f;
		BILLBOARD_QUAD_A_FLAT[0][6] = 0.85f;
		BILLBOARD_QUAD_A_FLAT[0][7] = 1;
		BILLBOARD_QUAD_A_FLAT[0][8] = 0.85f;
		BILLBOARD_QUAD_A_FLAT[0][9] = 0.85f;
		BILLBOARD_QUAD_A_FLAT[0][10] = 0;
		BILLBOARD_QUAD_A_FLAT[0][11] = 0.85f;
		
		// Quad B.
		BILLBOARD_QUAD_B_FLAT[0][0] = 0.85f;
		BILLBOARD_QUAD_B_FLAT[0][1] = 0;
		BILLBOARD_QUAD_B_FLAT[0][2] = 0.15f;
		BILLBOARD_QUAD_B_FLAT[0][3] = 0.85f;
		BILLBOARD_QUAD_B_FLAT[0][4] = 1;
		BILLBOARD_QUAD_B_FLAT[0][5] = 0.15f;
		BILLBOARD_QUAD_B_FLAT[0][6] = 0.15f;
		BILLBOARD_QUAD_B_FLAT[0][7] = 1;
		BILLBOARD_QUAD_B_FLAT[0][8] = 0.85f;
		BILLBOARD_QUAD_B_FLAT[0][9] = 0.15f;
		BILLBOARD_QUAD_B_FLAT[0][10] = 0;
		BILLBOARD_QUAD_B_FLAT[0][11] = 0.85f;
	}
	
	// ========================================================
	// Region Mesh Result Container.
	// ========================================================
	
	/**
	 * Holds the three meshes generated from a region.<br>
	 * Any mesh may be null if the region contains no blocks of that render mode.
	 */
	public static class RegionMeshResult
	{
		private final Mesh _opaqueMesh;
		private final Mesh _transparentMesh;
		private final Mesh _billboardMesh;
		
		public RegionMeshResult(Mesh opaqueMesh, Mesh transparentMesh, Mesh billboardMesh)
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
	// Region Mesh Data Container (thread-safe raw arrays).
	// ========================================================
	
	/**
	 * Holds pre-built raw vertex arrays for a region's three meshes.<br>
	 * Produced on a background thread by {@link #buildRegionMeshData}.<br>
	 * Converted to jME3 Mesh objects on the main thread by {@link #createMeshes}.<br>
	 * This split allows the expensive iteration and vertex building to run off-thread<br>
	 * while only the lightweight DirectBuffer allocation happens on the render thread.<br>
	 * Includes per-vertex color arrays for sky-light-based lighting.
	 */
	public static class RegionMeshData
	{
		private final float[] _opaquePositions;
		private final float[] _opaqueNormals;
		private final float[] _opaqueTexCoords;
		private final float[] _opaqueColors;
		private final int[] _opaqueIndices;
		
		private final float[] _transparentPositions;
		private final float[] _transparentNormals;
		private final float[] _transparentTexCoords;
		private final float[] _transparentColors;
		private final int[] _transparentIndices;
		
		private final float[] _billboardPositions;
		private final float[] _billboardNormals;
		private final float[] _billboardTexCoords;
		private final float[] _billboardColors;
		private final int[] _billboardIndices;
		
		public RegionMeshData(float[] opaquePositions, float[] opaqueNormals, float[] opaqueTexCoords, float[] opaqueColors, int[] opaqueIndices, float[] transparentPositions, float[] transparentNormals, float[] transparentTexCoords, float[] transparentColors, int[] transparentIndices, float[] billboardPositions, float[] billboardNormals, float[] billboardTexCoords, float[] billboardColors, int[] billboardIndices)
		{
			_opaquePositions = opaquePositions;
			_opaqueNormals = opaqueNormals;
			_opaqueTexCoords = opaqueTexCoords;
			_opaqueColors = opaqueColors;
			_opaqueIndices = opaqueIndices;
			_transparentPositions = transparentPositions;
			_transparentNormals = transparentNormals;
			_transparentTexCoords = transparentTexCoords;
			_transparentColors = transparentColors;
			_transparentIndices = transparentIndices;
			_billboardPositions = billboardPositions;
			_billboardNormals = billboardNormals;
			_billboardTexCoords = billboardTexCoords;
			_billboardColors = billboardColors;
			_billboardIndices = billboardIndices;
		}
		
		public boolean hasOpaque()
		{
			return _opaqueIndices != null;
		}
		
		public boolean hasTransparent()
		{
			return _transparentIndices != null;
		}
		
		public boolean hasBillboard()
		{
			return _billboardIndices != null;
		}
	}
	
	// ========================================================
	// Cross-Region Block Access.
	// ========================================================
	
	/**
	 * Functional interface for looking up blocks at world coordinates.<br>
	 * Used by the mesh builder to resolve neighbors across region boundaries.
	 */
	@FunctionalInterface
	public interface WorldBlockAccess
	{
		Block getBlock(int worldX, int worldY, int worldZ);
	}
	
	// ========================================================
	// Private Constructor (static utility class).
	// ========================================================
	
	private RegionMeshBuilder()
	{
		// Static utility class — do not instantiate.
	}
	
	// ========================================================
	// Day/Night Cycle API.
	// ========================================================
	
	/**
	 * Sets the day/night cycle parameters used when building vertex colors.<br>
	 * Must be called before triggering mesh rebuilds so background threads pick up<br>
	 * the current values. Fields are volatile for safe cross-thread reads.
	 * @param brightness the sky brightness multiplier (0.1 at midnight, 1.0 at noon)
	 * @param tintR red component of the sky tint (0.0 – 1.0)
	 * @param tintG green component of the sky tint (0.0 – 1.0)
	 * @param tintB blue component of the sky tint (0.0 – 1.0)
	 */
	public static void setDayNightParams(float brightness, float tintR, float tintG, float tintB)
	{
		_cycleBrightness = brightness;
		_cycleTintR = tintR;
		_cycleTintG = tintG;
		_cycleTintB = tintB;
	}
	
	// ========================================================
	// Full Region Mesh Building (legacy — kept for compatibility).
	// ========================================================
	
	/**
	 * Builds three separate meshes from the region's block data.<br>
	 * Legacy method - prefer using buildRegionMeshData + createMeshes for better performance.
	 */
	public static RegionMeshResult buildRegionMesh(Region region, WorldBlockAccess worldAccess)
	{
		// Use the optimized data builder then convert to meshes.
		final RegionMeshData data = buildRegionMeshData(region, worldAccess);
		return createMeshes(data);
	}
	
	// ========================================================
	// Background-Safe Mesh Data Building.
	// ========================================================
	
	/**
	 * Builds raw vertex arrays from the region's block data (background-thread safe).<br>
	 * Optimized version with zero garbage collection during vertex building.<br>
	 * Includes per-vertex color data computed from the region's sky light map, face shade factors,<br>
	 * and the current day/night cycle brightness and tint (set via {@link #setDayNightParams}).
	 * @param region the region to build mesh data from
	 * @param worldAccess optional world-level block access for cross-region neighbor lookups (may be null)
	 * @return a RegionMeshData containing raw vertex arrays including color data
	 */
	public static RegionMeshData buildRegionMeshData(Region region, WorldBlockAccess worldAccess)
	{
		// Ensure sky light data is up to date before building vertex colors.
		region.ensureSkyLightComputed();
		
		// Pass 1: Count vertices needed for each mesh type.
		final int[] counts = countVertices(region, worldAccess);
		final int opaqueVerts = counts[0];
		final int transparentVerts = counts[1];
		final int billboardVerts = counts[2];
		
		// Pre-allocate exact-size arrays (null if no vertices).
		// Color arrays have 4 components per vertex (RGBA).
		final float[] opaquePos = opaqueVerts > 0 ? new float[opaqueVerts * 3] : null;
		final float[] opaqueNorm = opaqueVerts > 0 ? new float[opaqueVerts * 3] : null;
		final float[] opaqueUV = opaqueVerts > 0 ? new float[opaqueVerts * 2] : null;
		final float[] opaqueCol = opaqueVerts > 0 ? new float[opaqueVerts * 4] : null;
		final int[] opaqueIdx = opaqueVerts > 0 ? new int[opaqueVerts / 4 * 6] : null;
		
		final float[] transPos = transparentVerts > 0 ? new float[transparentVerts * 3] : null;
		final float[] transNorm = transparentVerts > 0 ? new float[transparentVerts * 3] : null;
		final float[] transUV = transparentVerts > 0 ? new float[transparentVerts * 2] : null;
		final float[] transCol = transparentVerts > 0 ? new float[transparentVerts * 4] : null;
		final int[] transIdx = transparentVerts > 0 ? new int[transparentVerts / 4 * 6] : null;
		
		final float[] billPos = billboardVerts > 0 ? new float[billboardVerts * 3] : null;
		final float[] billNorm = billboardVerts > 0 ? new float[billboardVerts * 3] : null;
		final float[] billUV = billboardVerts > 0 ? new float[billboardVerts * 2] : null;
		final float[] billCol = billboardVerts > 0 ? new float[billboardVerts * 4] : null;
		final int[] billIdx = billboardVerts > 0 ? new int[billboardVerts / 4 * 6] : null;
		
		// Pass 2: Fill arrays with direct writes.
		int opaqueVPtr = 0;
		int opaqueUPtr = 0;
		int opaqueCPtr = 0;
		int opaqueIPtr = 0;
		
		int transVPtr = 0;
		int transUPtr = 0;
		int transCPtr = 0;
		int transIPtr = 0;
		
		int billVPtr = 0;
		int billUPtr = 0;
		int billCPtr = 0;
		int billIPtr = 0;
		
		for (int x = 0; x < Region.SIZE_XZ; x++)
		{
			for (int y = 0; y < Region.SIZE_Y; y++)
			{
				for (int z = 0; z < Region.SIZE_XZ; z++)
				{
					final Block block = region.getBlock(x, y, z);
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
								if (isFaceVisible(region, x, y, z, face, worldAccess))
								{
									// Blend sky light and block light for this face.
									final float skyLight = getNeighborSkyLight(region, x, y, z, face) * _cycleBrightness;
									final float blockLight = getNeighborBlockLight(region, x, y, z, face);
									final float shade = FACE_SHADE[face.ordinal()];
									final float skyB = skyLight * shade;
									final float blkB = blockLight * shade;
									final float finalB = Math.max(Math.max(skyB, blkB), MIN_BRIGHTNESS);
									
									// Warm tint ratio: how much of the light comes from block light.
									final float blkRatio = (finalB > MIN_BRIGHTNESS) ? (blkB / finalB) : 0;
									final float r = finalB * lerp(_cycleTintR, WARM_TINT_R, blkRatio);
									final float g = finalB * lerp(_cycleTintG, WARM_TINT_G, blkRatio);
									final float b = finalB * lerp(_cycleTintB, WARM_TINT_B, blkRatio);
									
									writeFace(opaquePos, opaqueNorm, opaqueUV, opaqueCol, opaqueIdx, x, y, z, face, block, r, g, b, opaqueVPtr, opaqueUPtr, opaqueCPtr, opaqueIPtr);
									opaqueVPtr += 4 * 3; // 4 verts × 3 coords
									opaqueUPtr += 4 * 2; // 4 verts × 2 UVs
									opaqueCPtr += 4 * 4; // 4 verts × 4 RGBA
									opaqueIPtr += 6; // 6 indices
								}
							}
							break;
						}
						case CUBE_TRANSPARENT:
						{
							for (Face face : Face.values())
							{
								if (isTransparentFaceVisible(region, x, y, z, face, block, worldAccess))
								{
									final float skyLight = getNeighborSkyLight(region, x, y, z, face) * _cycleBrightness;
									final float blockLight = getNeighborBlockLight(region, x, y, z, face);
									final float shade = FACE_SHADE[face.ordinal()];
									final float skyB = skyLight * shade;
									final float blkB = blockLight * shade;
									final float finalB = Math.max(Math.max(skyB, blkB), MIN_BRIGHTNESS);
									
									final float blkRatio = (finalB > MIN_BRIGHTNESS) ? (blkB / finalB) : 0;
									final float r = finalB * lerp(_cycleTintR, WARM_TINT_R, blkRatio);
									final float g = finalB * lerp(_cycleTintG, WARM_TINT_G, blkRatio);
									final float b = finalB * lerp(_cycleTintB, WARM_TINT_B, blkRatio);
									
									writeFace(transPos, transNorm, transUV, transCol, transIdx, x, y, z, face, block, r, g, b, transVPtr, transUPtr, transCPtr, transIPtr);
									transVPtr += 4 * 3;
									transUPtr += 4 * 2;
									transCPtr += 4 * 4;
									transIPtr += 6;
								}
							}
							break;
						}
						case CROSS_BILLBOARD:
						{
							// Torches are rendered by TorchTileEntity with wall tilt support.
							if (block == Block.TORCH)
							{
								break;
							}
							
							// Billboards sit in air/transparent space — use their own position's light.
							final float skyLight = region.getSkyLight(x, y, z) * _cycleBrightness;
							final float blockLight = region.getBlockLight(x, y, z) / 15.0f;
							final float skyB = skyLight * BILLBOARD_SHADE;
							final float blkB = blockLight * BILLBOARD_SHADE;
							final float finalB = Math.max(Math.max(skyB, blkB), MIN_BRIGHTNESS);
							
							final float blkRatio = (finalB > MIN_BRIGHTNESS) ? (blkB / finalB) : 0;
							final float r = finalB * lerp(_cycleTintR, WARM_TINT_R, blkRatio);
							final float g = finalB * lerp(_cycleTintG, WARM_TINT_G, blkRatio);
							final float b = finalB * lerp(_cycleTintB, WARM_TINT_B, blkRatio);
							
							writeBillboard(billPos, billNorm, billUV, billCol, billIdx, x, y, z, block, r, g, b, billVPtr, billUPtr, billCPtr, billIPtr);
							billVPtr += 8 * 3; // 8 verts × 3 coords
							billUPtr += 8 * 2; // 8 verts × 2 UVs
							billCPtr += 8 * 4; // 8 verts × 4 RGBA
							billIPtr += 12; // 12 indices
							break;
						}
					}
				}
			}
		}
		
		return new RegionMeshData(opaquePos, opaqueNorm, opaqueUV, opaqueCol, opaqueIdx, transPos, transNorm, transUV, transCol, transIdx, billPos, billNorm, billUV, billCol, billIdx);
	}
	
	/**
	 * Returns the sky light at the neighbor position for a given face.<br>
	 * A face should be lit by how much sky light reaches the air space it faces,<br>
	 * not by the block's own buried position. This prevents light bleeding through solids.<br>
	 * <br>
	 * If the neighbor is above the region (ny >= SIZE_Y), returns 1.0 (open sky).<br>
	 * If the neighbor is out of horizontal bounds (cross-region), falls back to the block's own sky light.
	 */
	private static float getNeighborSkyLight(Region region, int x, int y, int z, Face face)
	{
		final int[] offset = NEIGHBOR_OFFSETS[face.ordinal()];
		final int nx = x + offset[0];
		final int ny = y + offset[1];
		final int nz = z + offset[2];
		
		// Above the region — open sky.
		if (ny >= Region.SIZE_Y)
		{
			return 1.0f;
		}
		
		// Below the region — fully underground.
		if (ny < 0)
		{
			return 0.05f;
		}
		
		// Within region bounds — use the neighbor's column sky light.
		if (nx >= 0 && nx < Region.SIZE_XZ && nz >= 0 && nz < Region.SIZE_XZ)
		{
			return region.getSkyLight(nx, ny, nz);
		}
		
		// Cross-region boundary — fall back to block's own sky light.
		// Minor seam at region edges, but avoids needing cross-region sky light access.
		return region.getSkyLight(x, y, z);
	}
	
	/**
	 * Returns the block light (artificial light) at the neighbor position for a given face.<br>
	 * Parallel to {@link #getNeighborSkyLight} — samples the air space the face looks at.<br>
	 * Returns the block's own block light for cross-region boundaries (minor seam).
	 * @return block light level normalized to [0.0, 1.0]
	 */
	private static float getNeighborBlockLight(Region region, int x, int y, int z, Face face)
	{
		final int[] offset = NEIGHBOR_OFFSETS[face.ordinal()];
		final int nx = x + offset[0];
		final int ny = y + offset[1];
		final int nz = z + offset[2];
		
		if (ny < 0 || ny >= Region.SIZE_Y)
		{
			return 0.0f;
		}
		
		if (nx >= 0 && nx < Region.SIZE_XZ && nz >= 0 && nz < Region.SIZE_XZ)
		{
			return region.getBlockLight(nx, ny, nz) / 15.0f;
		}
		
		// Cross-region boundary — fall back to block's own block light.
		return region.getBlockLight(x, y, z) / 15.0f;
	}
	
	/**
	 * Linear interpolation between two values.
	 * @param a start value (when t = 0)
	 * @param b end value (when t = 1)
	 * @param t interpolation factor [0, 1]
	 * @return interpolated value
	 */
	private static float lerp(float a, float b, float t)
	{
		return a + (b - a) * t;
	}
	
	/**
	 * Counts vertices needed for each mesh type (first pass).
	 */
	private static int[] countVertices(Region region, WorldBlockAccess worldAccess)
	{
		int opaque = 0;
		int transparent = 0;
		int billboard = 0;
		
		for (int x = 0; x < Region.SIZE_XZ; x++)
		{
			for (int y = 0; y < Region.SIZE_Y; y++)
			{
				for (int z = 0; z < Region.SIZE_XZ; z++)
				{
					final Block block = region.getBlock(x, y, z);
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
								if (isFaceVisible(region, x, y, z, face, worldAccess))
								{
									opaque += 4;
								}
							}
							break;
						}
						case CUBE_TRANSPARENT:
						{
							for (Face face : Face.values())
							{
								if (isTransparentFaceVisible(region, x, y, z, face, block, worldAccess))
								{
									transparent += 4;
								}
							}
							break;
						}
						case CROSS_BILLBOARD:
						{
							// Torches are rendered by TorchTileEntity with wall tilt support.
							if (block == Block.TORCH)
							{
								break;
							}
							
							billboard += 8;
							break;
						}
					}
				}
			}
		}
		
		return new int[]
		{
			opaque,
			transparent,
			billboard
		};
	}
	
	/**
	 * Writes one face directly to pre-allocated arrays, including vertex color data.
	 * @param r pre-computed red component for all 4 vertices (light × tint)
	 * @param g pre-computed green component for all 4 vertices
	 * @param b pre-computed blue component for all 4 vertices
	 */
	private static void writeFace(float[] positions, float[] normals, float[] texCoords, float[] colors, int[] indices, int bx, int by, int bz, Face face, Block block, float r, float g, float b, int vPtr, int uvPtr, int cPtr, int iPtr)
	{
		final int faceOrdinal = face.ordinal();
		final int baseVertex = vPtr / 3;
		
		// Get atlas UV bounds.
		final float[] uvBounds = getAtlasUVs(block, face);
		
		// Use flattened arrays instead of Vector3f arrays.
		final float[] facePos = FACE_POSITIONS_FLAT[faceOrdinal];
		final float[] faceNorm = FACE_NORMALS_FLAT[faceOrdinal];
		
		// Write 4 vertices.
		for (int v = 0; v < 4; v++)
		{
			// Position — use flattened array.
			positions[vPtr + v * 3] = facePos[v * 3] + bx;
			positions[vPtr + v * 3 + 1] = facePos[v * 3 + 1] + by;
			positions[vPtr + v * 3 + 2] = facePos[v * 3 + 2] + bz;
			
			// Normal — use flattened array.
			normals[vPtr + v * 3] = faceNorm[v * 3];
			normals[vPtr + v * 3 + 1] = faceNorm[v * 3 + 1];
			normals[vPtr + v * 3 + 2] = faceNorm[v * 3 + 2];
			
			// UV coordinates.
			final float unitU = FACE_UVS[v * 2];
			final float unitV = FACE_UVS[v * 2 + 1];
			texCoords[uvPtr + v * 2] = uvBounds[0] + unitU * (uvBounds[2] - uvBounds[0]);
			texCoords[uvPtr + v * 2 + 1] = uvBounds[1] + unitV * (uvBounds[3] - uvBounds[1]);
			
			// Vertex color (RGBA) — pre-blended sky/block light with warm tint.
			colors[cPtr + v * 4] = r; // R
			colors[cPtr + v * 4 + 1] = g; // G
			colors[cPtr + v * 4 + 2] = b; // B
			colors[cPtr + v * 4 + 3] = 1.0f; // A
		}
		
		// Write indices.
		indices[iPtr] = baseVertex;
		indices[iPtr + 1] = baseVertex + 1;
		indices[iPtr + 2] = baseVertex + 2;
		indices[iPtr + 3] = baseVertex;
		indices[iPtr + 4] = baseVertex + 2;
		indices[iPtr + 5] = baseVertex + 3;
	}
	
	/**
	 * Writes billboard directly to pre-allocated arrays, including vertex color data.
	 * @param r pre-computed red component for all 8 vertices
	 * @param g pre-computed green component for all 8 vertices
	 * @param b pre-computed blue component for all 8 vertices
	 */
	private static void writeBillboard(float[] positions, float[] normals, float[] texCoords, float[] colors, int[] indices, int bx, int by, int bz, Block block, float r, float g, float b, int vPtr, int uvPtr, int cPtr, int iPtr)
	{
		final float[] uvBounds = getAtlasUVs(block, Face.TOP);
		
		// Write Quad A.
		writeBillboardQuad(positions, normals, texCoords, colors, indices, bx, by, bz, BILLBOARD_QUAD_A_FLAT[0], uvBounds, r, g, b, vPtr, uvPtr, cPtr, iPtr);
		
		// Write Quad B.
		writeBillboardQuad(positions, normals, texCoords, colors, indices, bx, by, bz, BILLBOARD_QUAD_B_FLAT[0], uvBounds, r, g, b, vPtr + 4 * 3, uvPtr + 4 * 2, cPtr + 4 * 4, iPtr + 6);
	}
	
	/**
	 * Writes a single billboard quad, including vertex color data.
	 */
	private static void writeBillboardQuad(float[] positions, float[] normals, float[] texCoords, float[] colors, int[] indices, int bx, int by, int bz, float[] quadVerts, float[] uvBounds, float r, float g, float b, int vPtr, int uvPtr, int cPtr, int iPtr)
	{
		final int baseVertex = vPtr / 3;
		
		// Calculate normal.
		final float edgeAx = quadVerts[9] - quadVerts[0];
		final float edgeAz = quadVerts[11] - quadVerts[2];
		final float length = (float) Math.sqrt(edgeAx * edgeAx + edgeAz * edgeAz);
		final float nx = -edgeAz / length;
		final float nz = edgeAx / length;
		
		// Write vertices.
		for (int v = 0; v < 4; v++)
		{
			positions[vPtr + v * 3] = quadVerts[v * 3] + bx;
			positions[vPtr + v * 3 + 1] = quadVerts[v * 3 + 1] + by;
			positions[vPtr + v * 3 + 2] = quadVerts[v * 3 + 2] + bz;
			
			normals[vPtr + v * 3] = nx;
			normals[vPtr + v * 3 + 1] = 0;
			normals[vPtr + v * 3 + 2] = nz;
			
			// Vertex color — pre-blended sky/block light with warm tint.
			colors[cPtr + v * 4] = r; // R
			colors[cPtr + v * 4 + 1] = g; // G
			colors[cPtr + v * 4 + 2] = b; // B
			colors[cPtr + v * 4 + 3] = 1.0f; // A
		}
		
		// Write UVs.
		texCoords[uvPtr] = uvBounds[0];
		texCoords[uvPtr + 1] = uvBounds[3];
		texCoords[uvPtr + 2] = uvBounds[0];
		texCoords[uvPtr + 3] = uvBounds[1];
		texCoords[uvPtr + 4] = uvBounds[2];
		texCoords[uvPtr + 5] = uvBounds[1];
		texCoords[uvPtr + 6] = uvBounds[2];
		texCoords[uvPtr + 7] = uvBounds[3];
		
		// Write indices.
		indices[iPtr] = baseVertex;
		indices[iPtr + 1] = baseVertex + 1;
		indices[iPtr + 2] = baseVertex + 2;
		indices[iPtr + 3] = baseVertex;
		indices[iPtr + 4] = baseVertex + 2;
		indices[iPtr + 5] = baseVertex + 3;
	}
	
	/**
	 * Gets UV bounds for a block face.
	 */
	private static float[] getAtlasUVs(Block block, Face face)
	{
		final int atlasIndex = block.getAtlasIndex(face);
		if (atlasIndex < 0)
		{
			return new float[]
			{
				UV_PADDING,
				UV_PADDING,
				1.0f - UV_PADDING,
				1.0f - UV_PADDING
			};
		}
		
		final float col = (atlasIndex % ATLAS_GRID_SIZE) * TILE_UV;
		final float row = (atlasIndex / ATLAS_GRID_SIZE) * TILE_UV;
		return new float[]
		{
			col + UV_PADDING,
			row + UV_PADDING,
			col + TILE_UV - UV_PADDING,
			row + TILE_UV - UV_PADDING
		};
	}
	
	/**
	 * Creates jME3 Mesh objects from pre-built raw vertex arrays (main-thread only).<br>
	 * This is the lightweight step: only DirectBuffer allocation and GPU upload.<br>
	 * Typically completes in under 1ms per region.<br>
	 * Now includes Color buffer for vertex-color-based lighting.
	 * @param data the pre-built mesh data from {@link #buildRegionMeshData}
	 * @return a RegionMeshResult containing the three meshes (any may be null)
	 */
	public static RegionMeshResult createMeshes(RegionMeshData data)
	{
		final Mesh opaqueMesh = data.hasOpaque() ? assembleFromArrays(data._opaquePositions, data._opaqueNormals, data._opaqueTexCoords, data._opaqueColors, data._opaqueIndices) : null;
		final Mesh transparentMesh = data.hasTransparent() ? assembleFromArrays(data._transparentPositions, data._transparentNormals, data._transparentTexCoords, data._transparentColors, data._transparentIndices) : null;
		final Mesh billboardMesh = data.hasBillboard() ? assembleFromArrays(data._billboardPositions, data._billboardNormals, data._billboardTexCoords, data._billboardColors, data._billboardIndices) : null;
		return new RegionMeshResult(opaqueMesh, transparentMesh, billboardMesh);
	}
	
	/**
	 * Creates a jME3 Mesh from pre-built primitive arrays, including vertex colors.
	 */
	private static Mesh assembleFromArrays(float[] positions, float[] normals, float[] texCoords, float[] colors, int[] indices)
	{
		final Mesh mesh = new Mesh();
		mesh.setBuffer(Type.Position, 3, BufferUtils.createFloatBuffer(positions));
		mesh.setBuffer(Type.Normal, 3, BufferUtils.createFloatBuffer(normals));
		mesh.setBuffer(Type.TexCoord, 2, BufferUtils.createFloatBuffer(texCoords));
		mesh.setBuffer(Type.Color, 4, BufferUtils.createFloatBuffer(colors));
		mesh.setBuffer(Type.Index, 3, BufferUtils.createIntBuffer(indices));
		mesh.updateBound();
		return mesh;
	}
	
	// ========================================================
	// Face Visibility — CUBE_SOLID.
	// ========================================================
	
	/**
	 * Returns true if the given face of a CUBE_SOLID block at (x, y, z) should be rendered.<br>
	 * A face is visible if the neighbor block in that direction is AIR, transparent, or out-of-bounds.<br>
	 * When worldAccess is provided, out-of-bounds neighbors are resolved from adjacent regions.
	 */
	public static boolean isFaceVisible(Region region, int x, int y, int z, Face face, WorldBlockAccess worldAccess)
	{
		final int[] offset = NEIGHBOR_OFFSETS[face.ordinal()];
		final int nx = x + offset[0];
		final int ny = y + offset[1];
		final int nz = z + offset[2];
		
		final Block neighbor;
		if (!region.isInBounds(nx, ny, nz))
		{
			if (worldAccess != null && ny >= 0 && ny < Region.SIZE_Y)
			{
				// Convert to world coordinates and query across region boundaries.
				final int worldX = region.getWorldX() + nx;
				final int worldZ = region.getWorldZ() + nz;
				neighbor = worldAccess.getBlock(worldX, ny, worldZ);
			}
			else
			{
				// No world access or out of vertical bounds — treat as air.
				return true;
			}
		}
		else
		{
			neighbor = region.getBlock(nx, ny, nz);
		}
		
		// Face visible if neighbor is not a solid cube.
		return neighbor == Block.AIR || neighbor.getRenderMode() != RenderMode.CUBE_SOLID;
	}
	
	// ========================================================
	// Face Visibility — CUBE_TRANSPARENT.
	// ========================================================
	
	/**
	 * Positive-direction faces used for leaves single-face rendering.<br>
	 * When two leaves blocks share a boundary, only the positive-direction face (TOP, NORTH, EAST) is rendered.<br>
	 * This avoids z-fighting from two overlapping transparent faces while keeping interiors visible.
	 */
	private static final boolean[] POSITIVE_FACE =
	{
		true, // TOP (Y+).
		false, // BOTTOM (Y-).
		true, // NORTH (Z+).
		false, // SOUTH (Z-).
		true, // EAST (X+).
		false // WEST (X-).
	};
	
	/**
	 * Returns true if the given face of a CUBE_TRANSPARENT block should be rendered.<br>
	 * <br>
	 * <b>Water (liquid):</b> Only renders faces adjacent to AIR or out-of-bounds.<br>
	 * No water faces against pool floor/walls or between water blocks.<br>
	 * This creates a clean surface you can see through.<br>
	 * <br>
	 * <b>Leaves (non-liquid):</b> Renders faces against AIR/non-solid normally.<br>
	 * When two leaves blocks share a boundary, renders exactly ONE face<br>
	 * (the positive-direction side: TOP/NORTH/EAST) to avoid z-fighting.<br>
	 * <br>
	 * When worldAccess is provided, out-of-bounds neighbors are resolved from adjacent regions.
	 */
	public static boolean isTransparentFaceVisible(Region region, int x, int y, int z, Face face, Block block, WorldBlockAccess worldAccess)
	{
		final int[] offset = NEIGHBOR_OFFSETS[face.ordinal()];
		final int nx = x + offset[0];
		final int ny = y + offset[1];
		final int nz = z + offset[2];
		
		final Block neighbor;
		if (!region.isInBounds(nx, ny, nz))
		{
			if (worldAccess != null && ny >= 0 && ny < Region.SIZE_Y)
			{
				// Convert to world coordinates and query across region boundaries.
				final int worldX = region.getWorldX() + nx;
				final int worldZ = region.getWorldZ() + nz;
				neighbor = worldAccess.getBlock(worldX, ny, worldZ);
			}
			else
			{
				// No world access or out of vertical bounds — treat as air.
				return true;
			}
		}
		else
		{
			neighbor = region.getBlock(nx, ny, nz);
		}
		
		if (block.isLiquid())
		{
			// Water only renders faces adjacent to air — clean pool surface.
			return neighbor == Block.AIR;
		}
		
		// Leaves: cull against solid blocks.
		if (neighbor.isSolid())
		{
			return false;
		}
		
		// Leaves-to-leaves: render only one face per shared boundary.
		// Positive-direction face (TOP/NORTH/EAST) renders, negative is culled.
		if (neighbor == block)
		{
			return POSITIVE_FACE[face.ordinal()];
		}
		
		// Adjacent to air or other non-solid — always render.
		return true;
	}
	
	// ========================================================
	// Single Cube Test Mesh.
	// ========================================================
	
	/**
	 * Generates a Mesh for a single block at (0,0,0) with all 6 faces visible.<br>
	 * Used for testing that the mesh pipeline works before building full regions.<br>
	 * All vertex colors are set to white (full brightness).
	 */
	public static Mesh buildSingleCube()
	{
		final List<Float> positions = new ArrayList<>();
		final List<Float> normals = new ArrayList<>();
		final List<Float> texCoords = new ArrayList<>();
		final List<Float> colors = new ArrayList<>();
		final List<Integer> indices = new ArrayList<>();
		
		for (Face face : Face.values())
		{
			addFaceUnitUV(positions, normals, texCoords, colors, indices, 0, 0, 0, face);
		}
		
		return assembleMesh(positions, normals, texCoords, colors, indices);
	}
	
	// ========================================================
	// Legacy Face Building (unit UVs for single cube test).
	// ========================================================
	
	/**
	 * Appends one face (4 vertices, 6 indices) to the buffer lists.
	 * @param positions vertex position list (x, y, z per vertex)
	 * @param normals vertex normal list (x, y, z per vertex)
	 * @param texCoords vertex UV list (u, v per vertex)
	 * @param colors vertex color list (r, g, b, a per vertex)
	 * @param indices triangle index list
	 * @param bx block X position (local)
	 * @param by block Y position (local)
	 * @param bz block Z position (local)
	 * @param face which face to add
	 */
	private static void addFaceUnitUV(List<Float> positions, List<Float> normals, List<Float> texCoords, List<Float> colors, List<Integer> indices, int bx, int by, int bz, Face face)
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
			
			// Vertex color — full white for test cube.
			colors.add(1.0f); // R
			colors.add(1.0f); // G
			colors.add(1.0f); // B
			colors.add(1.0f); // A
		}
		
		// Add 6 indices (two triangles).
		for (int i = 0; i < 6; i++)
		{
			indices.add(baseIndex + FACE_INDICES[i]);
		}
	}
	
	// ========================================================
	// Mesh Assembly (Legacy).
	// ========================================================
	
	/**
	 * Converts the buffer lists into a jME3 Mesh, including vertex colors.
	 */
	private static Mesh assembleMesh(List<Float> positions, List<Float> normals, List<Float> texCoords, List<Float> colors, List<Integer> indices)
	{
		final Mesh mesh = new Mesh();
		
		// Convert Lists to primitive arrays.
		final float[] posArray = toFloatArray(positions);
		final float[] normArray = toFloatArray(normals);
		final float[] uvArray = toFloatArray(texCoords);
		final float[] colArray = toFloatArray(colors);
		final int[] idxArray = toIntArray(indices);
		
		mesh.setBuffer(Type.Position, 3, BufferUtils.createFloatBuffer(posArray));
		mesh.setBuffer(Type.Normal, 3, BufferUtils.createFloatBuffer(normArray));
		mesh.setBuffer(Type.TexCoord, 2, BufferUtils.createFloatBuffer(uvArray));
		mesh.setBuffer(Type.Color, 4, BufferUtils.createFloatBuffer(colArray));
		mesh.setBuffer(Type.Index, 3, BufferUtils.createIntBuffer(idxArray));
		mesh.updateBound();
		
		return mesh;
	}
	
	// ========================================================
	// Array Conversion Utilities.
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
