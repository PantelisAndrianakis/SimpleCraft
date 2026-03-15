package simplecraft.item;

import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.scene.control.BillboardControl;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;
import com.jme3.util.BufferUtils;

import simplecraft.world.Block;
import simplecraft.world.Block.Face;
import simplecraft.world.TextureAtlas;

/**
 * A world entity representing an item floating on the ground after a block break or enemy death.<br>
 * <br>
 * <b>Visual modes (checked in order):</b><br>
 * <ol>
 * <li><b>Mini textured cube</b> — if the item is a BLOCK type and an atlas material is available,<br>
 * renders as a small 0.3-block cube with the block's actual per-face atlas textures.<br>
 * This gives the same look as the full-size block in the world, shrunk down.</li>
 * <li><b>Billboard sprite</b> — if a texture exists at {@code assets/images/drops/<item_id>.png},<br>
 * renders as a camera-facing quad (0.4 blocks) with that sprite.</li>
 * <li><b>Colored cube</b> — fallback: a small solid-color cube (0.3 blocks) tinted per item type.</li>
 * </ol>
 * Mini-cube and colored-cube drops spin slowly around the Y axis. Billboard drops face the<br>
 * camera via {@link BillboardControl} and do not spin. All modes bob up and down via a sine wave.<br>
 * <br>
 * Pickup is handled externally by {@link DropManager}, which checks distance to<br>
 * the player each frame and attempts {@link Inventory#addItem(ItemInstance)}.<br>
 * <br>
 * Each drop has a 300-second (5-minute) lifetime. When expired, the drop is<br>
 * removed from the world by the manager.
 * @author Pantelis Andrianakis
 * @since March 15th 2026
 */
public class DroppedItem
{
	// ------------------------------------------------------------------
	// Cube visual constants.
	// ------------------------------------------------------------------
	
	/** Half-extent of the visual cube (0.15 = 0.3 blocks wide). */
	private static final float CUBE_HALF_SIZE = 0.15f;
	
	/** Spin speed for cube drops in radians per second. */
	private static final float SPIN_SPEED = 1.5f;
	
	// ------------------------------------------------------------------
	// Billboard visual constants.
	// ------------------------------------------------------------------
	
	/** Width and height of the billboard quad in blocks. */
	private static final float BILLBOARD_SIZE = 0.4f;
	
	/**
	 * Asset path prefix for drop textures.<br>
	 * Full path: {@code assets/images/drops/<item_id>.png}
	 */
	private static final String DROP_TEXTURE_PATH = "assets/images/drops/";
	
	// ------------------------------------------------------------------
	// Atlas UV constants (mirrors RegionMeshBuilder).
	// ------------------------------------------------------------------
	
	/** Atlas grid size (tiles per row/column). */
	private static final int ATLAS_GRID_SIZE = TextureAtlas.GRID_SIZE;
	
	/** UV size of one tile in the atlas. */
	private static final float TILE_UV = 1.0f / ATLAS_GRID_SIZE;
	
	/** UV inset to prevent texture bleeding at tile edges. */
	private static final float UV_PADDING = 0.001f;
	
	// ------------------------------------------------------------------
	// Shared constants.
	// ------------------------------------------------------------------
	
	/** Vertical bob amplitude in blocks. */
	private static final float BOB_AMPLITUDE = 0.15f;
	
	/** Bob frequency multiplier (radians per second). */
	private static final float BOB_SPEED = 2.5f;
	
	/** Base vertical offset above the ground position. */
	private static final float BASE_Y_OFFSET = 0.3f;
	
	/** Maximum lifetime before automatic despawn (seconds). */
	private static final float MAX_LIFETIME = 300.0f;
	
	/** Faces in the order used for mini cube mesh construction. */
	// @formatter:off
	private static final Face[] CUBE_FACES =
	{
		Face.TOP, Face.BOTTOM, Face.NORTH, Face.SOUTH, Face.EAST, Face.WEST
	};
	// @formatter:on
	
	// ------------------------------------------------------------------
	// Fields.
	// ------------------------------------------------------------------
	
	/** The item stack this drop represents. */
	private final ItemInstance _instance;
	
	/** World position (ground level where the drop was spawned). */
	private final Vector3f _position;
	
	/** Scene node holding the visual geometry. */
	private final Node _node;
	
	/** True if this drop uses a billboard quad (no spin). */
	private final boolean _isBillboard;
	
	/** Remaining lifetime in seconds. */
	private float _lifetime;
	
	/** Accumulated bob/spin animation timer. */
	private float _animTimer;
	
	/**
	 * Creates a new dropped item at the given world position.<br>
	 * Chooses the best visual mode in order: mini textured cube → billboard sprite → colored cube.
	 * @param instance the item stack to drop
	 * @param position the world position (ground level at the drop origin)
	 * @param assetManager the asset manager for creating materials and loading textures
	 * @param atlasMaterial the shared texture atlas material for block rendering, or null if unavailable
	 */
	public DroppedItem(ItemInstance instance, Vector3f position, AssetManager assetManager, Material atlasMaterial)
	{
		_instance = instance;
		_position = new Vector3f(position);
		_lifetime = MAX_LIFETIME;
		_animTimer = 0;
		
		final String itemId = instance.getTemplate().getId();
		_node = new Node("DroppedItem_" + itemId);
		
		// ------------------------------------------------------------------
		// Mode 1: Mini textured cube for block items with atlas textures.
		// ------------------------------------------------------------------
		final Block placedBlock = instance.getTemplate().getType() == ItemType.BLOCK ? instance.getTemplate().getPlacesBlock() : null;
		if (placedBlock != null && atlasMaterial != null && placedBlock.getAtlasIndex() >= 0)
		{
			_isBillboard = false;
			
			final Mesh miniCubeMesh = buildMiniCubeMesh(placedBlock);
			final Geometry geom = new Geometry("DropMiniCube", miniCubeMesh);
			geom.setMaterial(atlasMaterial);
			
			_node.attachChild(geom);
			_node.setLocalTranslation(position.x, position.y + BASE_Y_OFFSET, position.z);
			return;
		}
		
		// ------------------------------------------------------------------
		// Mode 2: Billboard sprite if assets/images/drops/<item_id>.png exists.
		// ------------------------------------------------------------------
		Texture dropTexture = null;
		try
		{
			dropTexture = assetManager.loadTexture(DROP_TEXTURE_PATH + itemId + ".png");
		}
		catch (AssetNotFoundException ignored)
		{
			// No texture — will fall back to colored cube.
		}
		catch (Exception ignored)
		{
			// Any other loading error — fall back silently.
		}
		
		if (dropTexture != null)
		{
			_isBillboard = true;
			
			final Quad quad = new Quad(BILLBOARD_SIZE, BILLBOARD_SIZE);
			final Geometry geom = new Geometry("DropBillboard", quad);
			
			final Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
			mat.setTexture("ColorMap", dropTexture);
			mat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
			geom.setMaterial(mat);
			geom.setQueueBucket(Bucket.Transparent);
			
			// Center the quad around the node origin (Quad starts at 0,0 by default).
			geom.setLocalTranslation(-BILLBOARD_SIZE * 0.5f, -BILLBOARD_SIZE * 0.5f, 0);
			
			_node.attachChild(geom);
			
			// BillboardControl makes the node always face the camera.
			final BillboardControl billboard = new BillboardControl();
			_node.addControl(billboard);
			
			_node.setLocalTranslation(position.x, position.y + BASE_Y_OFFSET, position.z);
			return;
		}
		
		// ------------------------------------------------------------------
		// Mode 3: Colored cube fallback.
		// ------------------------------------------------------------------
		_isBillboard = false;
		
		final Box box = new Box(CUBE_HALF_SIZE, CUBE_HALF_SIZE, CUBE_HALF_SIZE);
		final Geometry geom = new Geometry("DropCube", box);
		
		final Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
		mat.setColor("Color", getColorForItem(itemId));
		geom.setMaterial(mat);
		
		_node.attachChild(geom);
		_node.setLocalTranslation(position.x, position.y + BASE_Y_OFFSET, position.z);
	}
	
	// ------------------------------------------------------------------
	// Mini textured cube mesh builder.
	// ------------------------------------------------------------------
	
	/**
	 * Builds a small cube mesh with per-face atlas UVs matching the given block type.<br>
	 * Uses 24 vertices (4 per face), each with correct positions, normals, atlas UV<br>
	 * coordinates, and white vertex colors (full brightness, required by the atlas material).
	 * @param block the block to use for atlas UV lookups
	 * @return the custom mini cube mesh
	 */
	private static Mesh buildMiniCubeMesh(Block block)
	{
		final int vertexCount = 24; // 4 vertices × 6 faces.
		final int indexCount = 36; // 6 indices × 6 faces.
		
		final float[] positions = new float[vertexCount * 3];
		final float[] normals = new float[vertexCount * 3];
		final float[] texCoords = new float[vertexCount * 2];
		final float[] colors = new float[vertexCount * 4];
		final int[] indices = new int[indexCount];
		
		final float h = CUBE_HALF_SIZE;
		
		for (int f = 0; f < 6; f++)
		{
			final Face face = CUBE_FACES[f];
			final int vBase = f * 4;
			final int pBase = vBase * 3;
			final int nBase = vBase * 3;
			final int tBase = vBase * 2;
			final int cBase = vBase * 4;
			final int iBase = f * 6;
			
			// Write positions and normals for this face.
			writeFacePositions(positions, pBase, normals, nBase, face, h);
			
			// Write atlas UVs.
			writeFaceUVs(texCoords, tBase, block, face);
			
			// Write vertex colors (white = full brightness).
			for (int v = 0; v < 4; v++)
			{
				colors[cBase + v * 4] = 1.0f;
				colors[cBase + v * 4 + 1] = 1.0f;
				colors[cBase + v * 4 + 2] = 1.0f;
				colors[cBase + v * 4 + 3] = 1.0f;
			}
			
			// Write indices (two triangles per face).
			indices[iBase] = vBase;
			indices[iBase + 1] = vBase + 1;
			indices[iBase + 2] = vBase + 2;
			indices[iBase + 3] = vBase;
			indices[iBase + 4] = vBase + 2;
			indices[iBase + 5] = vBase + 3;
		}
		
		final Mesh mesh = new Mesh();
		mesh.setBuffer(Type.Position, 3, BufferUtils.createFloatBuffer(positions));
		mesh.setBuffer(Type.Normal, 3, BufferUtils.createFloatBuffer(normals));
		mesh.setBuffer(Type.TexCoord, 2, BufferUtils.createFloatBuffer(texCoords));
		mesh.setBuffer(Type.Color, 4, BufferUtils.createFloatBuffer(colors));
		mesh.setBuffer(Type.Index, 3, BufferUtils.createIntBuffer(indices));
		mesh.updateBound();
		return mesh;
	}
	
	/**
	 * Writes the 4 vertex positions and normals for one face of the mini cube.<br>
	 * Vertices are wound counter-clockwise when viewed from outside the cube.
	 * @param pos position array
	 * @param p offset into position array
	 * @param nrm normal array
	 * @param n offset into normal array
	 * @param face the cube face
	 * @param h half-extent of the cube
	 */
	private static void writeFacePositions(float[] pos, int p, float[] nrm, int n, Face face, float h)
	{
		float nx = 0;
		float ny = 0;
		float nz = 0;
		
		switch (face)
		{
			case TOP:
			{
				ny = 1;
				// v0(-h,h,-h) v1(-h,h,h) v2(h,h,h) v3(h,h,-h)
				pos[p] = -h;
				pos[p + 1] = h;
				pos[p + 2] = -h;
				pos[p + 3] = -h;
				pos[p + 4] = h;
				pos[p + 5] = h;
				pos[p + 6] = h;
				pos[p + 7] = h;
				pos[p + 8] = h;
				pos[p + 9] = h;
				pos[p + 10] = h;
				pos[p + 11] = -h;
				break;
			}
			case BOTTOM:
			{
				ny = -1;
				// v0(-h,-h,h) v1(-h,-h,-h) v2(h,-h,-h) v3(h,-h,h)
				pos[p] = -h;
				pos[p + 1] = -h;
				pos[p + 2] = h;
				pos[p + 3] = -h;
				pos[p + 4] = -h;
				pos[p + 5] = -h;
				pos[p + 6] = h;
				pos[p + 7] = -h;
				pos[p + 8] = -h;
				pos[p + 9] = h;
				pos[p + 10] = -h;
				pos[p + 11] = h;
				break;
			}
			case NORTH:
			{
				nz = 1;
				// v0(h,-h,h) v1(h,h,h) v2(-h,h,h) v3(-h,-h,h)
				pos[p] = h;
				pos[p + 1] = -h;
				pos[p + 2] = h;
				pos[p + 3] = h;
				pos[p + 4] = h;
				pos[p + 5] = h;
				pos[p + 6] = -h;
				pos[p + 7] = h;
				pos[p + 8] = h;
				pos[p + 9] = -h;
				pos[p + 10] = -h;
				pos[p + 11] = h;
				break;
			}
			case SOUTH:
			{
				nz = -1;
				// v0(-h,-h,-h) v1(-h,h,-h) v2(h,h,-h) v3(h,-h,-h)
				pos[p] = -h;
				pos[p + 1] = -h;
				pos[p + 2] = -h;
				pos[p + 3] = -h;
				pos[p + 4] = h;
				pos[p + 5] = -h;
				pos[p + 6] = h;
				pos[p + 7] = h;
				pos[p + 8] = -h;
				pos[p + 9] = h;
				pos[p + 10] = -h;
				pos[p + 11] = -h;
				break;
			}
			case EAST:
			{
				nx = 1;
				// v0(h,-h,-h) v1(h,h,-h) v2(h,h,h) v3(h,-h,h)
				pos[p] = h;
				pos[p + 1] = -h;
				pos[p + 2] = -h;
				pos[p + 3] = h;
				pos[p + 4] = h;
				pos[p + 5] = -h;
				pos[p + 6] = h;
				pos[p + 7] = h;
				pos[p + 8] = h;
				pos[p + 9] = h;
				pos[p + 10] = -h;
				pos[p + 11] = h;
				break;
			}
			case WEST:
			{
				nx = -1;
				// v0(-h,-h,h) v1(-h,h,h) v2(-h,h,-h) v3(-h,-h,-h)
				pos[p] = -h;
				pos[p + 1] = -h;
				pos[p + 2] = h;
				pos[p + 3] = -h;
				pos[p + 4] = h;
				pos[p + 5] = h;
				pos[p + 6] = -h;
				pos[p + 7] = h;
				pos[p + 8] = -h;
				pos[p + 9] = -h;
				pos[p + 10] = -h;
				pos[p + 11] = -h;
				break;
			}
		}
		
		// Write normal for all 4 vertices.
		for (int v = 0; v < 4; v++)
		{
			nrm[n + v * 3] = nx;
			nrm[n + v * 3 + 1] = ny;
			nrm[n + v * 3 + 2] = nz;
		}
	}
	
	/**
	 * Writes atlas UV coordinates for one face of the mini cube.<br>
	 * Looks up the block's atlas index for the given face and computes the<br>
	 * tile UV rectangle with padding to prevent texture bleeding.
	 * @param texCoords the UV array
	 * @param t offset into the UV array
	 * @param block the block for atlas lookup
	 * @param face the cube face
	 */
	private static void writeFaceUVs(float[] texCoords, int t, Block block, Face face)
	{
		final int atlasIndex = block.getAtlasIndex(face);
		
		final float u0;
		final float v0;
		final float u1;
		final float v1;
		
		if (atlasIndex >= 0)
		{
			final float col = (atlasIndex % ATLAS_GRID_SIZE) * TILE_UV;
			final float row = (atlasIndex / ATLAS_GRID_SIZE) * TILE_UV;
			u0 = col + UV_PADDING;
			v0 = row + UV_PADDING;
			u1 = col + TILE_UV - UV_PADDING;
			v1 = row + TILE_UV - UV_PADDING;
		}
		else
		{
			// Fallback — full atlas (shouldn't happen for valid blocks).
			u0 = UV_PADDING;
			v0 = UV_PADDING;
			u1 = 1.0f - UV_PADDING;
			v1 = 1.0f - UV_PADDING;
		}
		
		// UV winding: v0(u0,v1) v1(u0,v0) v2(u1,v0) v3(u1,v1)
		texCoords[t] = u0;
		texCoords[t + 1] = v1;
		texCoords[t + 2] = u0;
		texCoords[t + 3] = v0;
		texCoords[t + 4] = u1;
		texCoords[t + 5] = v0;
		texCoords[t + 6] = u1;
		texCoords[t + 7] = v1;
	}
	
	// ------------------------------------------------------------------
	// Per-frame update.
	// ------------------------------------------------------------------
	
	/**
	 * Updates the bob animation (and spin for non-billboard drops) and decrements the lifetime timer.
	 * @param tpf time per frame in seconds
	 */
	public void update(float tpf)
	{
		_animTimer += tpf;
		_lifetime -= tpf;
		
		// Sine wave bob (all modes).
		final float bobOffset = FastMath.sin(_animTimer * BOB_SPEED) * BOB_AMPLITUDE;
		_node.setLocalTranslation(_position.x, _position.y + BASE_Y_OFFSET + bobOffset, _position.z);
		
		// Non-billboard drops spin slowly around the Y axis.
		// Billboard drops skip this — BillboardControl handles their orientation.
		if (!_isBillboard)
		{
			final float angle = _animTimer * SPIN_SPEED;
			_node.setLocalRotation(new Quaternion().fromAngleAxis(angle, Vector3f.UNIT_Y));
		}
	}
	
	// ------------------------------------------------------------------
	// Accessors.
	// ------------------------------------------------------------------
	
	/**
	 * Returns true if this drop has expired (lifetime <= 0).
	 * @return true if expired
	 */
	public boolean isExpired()
	{
		return _lifetime <= 0;
	}
	
	/**
	 * Returns the item stack this drop represents.
	 * @return the item instance
	 */
	public ItemInstance getInstance()
	{
		return _instance;
	}
	
	/**
	 * Returns the world position where this drop was spawned.
	 * @return the ground-level position
	 */
	public Vector3f getPosition()
	{
		return _position;
	}
	
	/**
	 * Returns the scene node holding the visual geometry.
	 * @return the node
	 */
	public Node getNode()
	{
		return _node;
	}
	
	/**
	 * Returns the remaining lifetime in seconds.
	 * @return seconds until despawn
	 */
	public float getLifetime()
	{
		return _lifetime;
	}
	
	/**
	 * Returns true if this drop uses a billboard quad, false if it uses a cube.
	 * @return true for billboard mode
	 */
	public boolean isBillboard()
	{
		return _isBillboard;
	}
	
	// ------------------------------------------------------------------
	// Color fallback for non-block, non-billboard items.
	// ------------------------------------------------------------------
	
	/**
	 * Returns a representative color for the given item ID.<br>
	 * Used as the fallback visual when no atlas block or billboard texture exists.
	 * @param itemId the item ID
	 * @return the display color
	 */
	private static ColorRGBA getColorForItem(String itemId)
	{
		switch (itemId)
		{
			case "cooked_meat":
			{
				return new ColorRGBA(0.6f, 0.3f, 0.15f, 1.0f); // Brown.
			}
			case "iron_nugget":
			{
				return new ColorRGBA(0.75f, 0.75f, 0.75f, 1.0f); // Silver.
			}
			case "stone_shard":
			{
				return new ColorRGBA(0.5f, 0.5f, 0.5f, 1.0f); // Gray.
			}
			case "wood_plank":
			{
				return new ColorRGBA(0.7f, 0.5f, 0.25f, 1.0f); // Tan.
			}
			case "health_potion":
			{
				return new ColorRGBA(1.0f, 0.2f, 0.3f, 1.0f); // Red-pink.
			}
			case "berries":
			{
				return new ColorRGBA(0.8f, 0.1f, 0.3f, 1.0f); // Berry red.
			}
			default:
			{
				return new ColorRGBA(0.9f, 0.9f, 0.2f, 1.0f); // Yellow fallback.
			}
		}
	}
}
