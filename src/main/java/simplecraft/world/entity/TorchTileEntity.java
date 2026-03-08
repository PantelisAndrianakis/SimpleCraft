package simplecraft.world.entity;

import com.jme3.effect.ParticleEmitter;
import com.jme3.effect.ParticleMesh;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.material.RenderState.FaceCullMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.util.BufferUtils;

import simplecraft.SimpleCraft;
import simplecraft.player.PlayerController;
import simplecraft.util.Vector3i;
import simplecraft.world.Block;
import simplecraft.world.Block.Face;
import simplecraft.world.TextureAtlas;
import simplecraft.world.World;

/**
 * Torch tile entity — emits warm block light and a small flame particle effect.<br>
 * <br>
 * Renders its own cross-billboard mesh rather than relying on the region mesh builder,<br>
 * because wall-mounted torches need a ~25° tilt that the mesh builder cannot provide<br>
 * (it has no knowledge of attachment direction). The torch is excluded from<br>
 * {@code RegionMeshBuilder}'s billboard pass and rendered entirely through this entity.<br>
 * <br>
 * Torches can be placed on the floor (on top of a solid block) or on a wall<br>
 * (adjacent to a solid block face). The inherited {@code _attachedFace} field records<br>
 * which face of the neighboring solid block the torch is attached to — this is used<br>
 * by the block support system to remove the torch if the supporting block is broken.<br>
 * <br>
 * <b>Floor torch:</b> {@code attachedFace = BOTTOM} — resting on the block below.<br>
 * Flame particles are centered on top of the torch.<br>
 * <b>Wall torch:</b> {@code attachedFace = NORTH/SOUTH/EAST/WEST} — attached to<br>
 * the side of an adjacent solid block. The entire torch tilts ~25° toward the wall<br>
 * and the flame follows naturally as a child of the tilted node.<br>
 * <br>
 * <b>Block light:</b> emits level 10, decaying 1 per block distance (~10 block radius).<br>
 * Warm yellow tint applied by the mesh builder to blocks lit primarily by torch light.
 * @author Pantelis Andrianakis
 * @since March 8th 2026
 */
public class TorchTileEntity extends TileEntity
{
	/** Flame effect image path */
	private static final String FLAME_IMAGE_PATH = "assets/images/effects/flame.png";
	
	/** Block light level emitted by a placed torch. */
	private static final int TORCH_LIGHT_LEVEL = 10;
	
	/** Tilt angle in radians for wall-mounted torches (~25 degrees). */
	private static final float WALL_TILT_ANGLE = 25.0f * FastMath.DEG_TO_RAD;
	
	/** How far toward the wall the torch base shifts (in blocks). */
	private static final float WALL_SHIFT = 0.52f;
	
	// ========================================================
	// Billboard Mesh Constants.
	// ========================================================
	
	/** Number of tiles per row/column in the texture atlas. */
	private static final int ATLAS_GRID_SIZE = TextureAtlas.GRID_SIZE;
	
	/** UV size of one tile in the atlas. */
	private static final float TILE_UV = 1.0f / ATLAS_GRID_SIZE;
	
	/** UV inset to prevent atlas bleeding (matches RegionMeshBuilder). */
	private static final float UV_PADDING = 0.001f;
	
	/** Inset from block edges for cross-billboard quads (matches RegionMeshBuilder). */
	private static final float INSET = 0.15f;
	
	/** Self-illumination brightness for the torch billboard (always warm-lit). */
	private static final float SELF_LIGHT = 0.9f;
	
	// ========================================================
	// Shared Material (lazy-initialized, reused for all torches).
	// ========================================================
	
	/** Shared material for all torch billboard geometries. */
	private static Material _sharedMaterial;
	
	// ========================================================
	// Instance Fields.
	// ========================================================
	
	/** Flame particle emitter. */
	private ParticleEmitter _flameEmitter;
	
	/**
	 * Creates a torch tile entity at the given position with the specified attachment.
	 * @param position world block coordinates of the torch
	 * @param attachedFace which face of the supporting block the torch is attached to
	 */
	public TorchTileEntity(Vector3i position, Face attachedFace)
	{
		super(position, Block.TORCH, attachedFace);
	}
	
	// ========================================================
	// Lifecycle.
	// ========================================================
	
	@Override
	public void onPlaced(World world)
	{
		// Root node at block origin.
		_visualNode = new Node("Torch_" + _position.x + "_" + _position.y + "_" + _position.z);
		_visualNode.setLocalTranslation(_position.x, _position.y, _position.z);
		
		// Pivot node at the center-base of the block — rotation happens here.
		// This ensures the torch tilts around its base, not around the block corner.
		final Node pivotNode = new Node("TorchPivot");
		pivotNode.setLocalTranslation(0.5f, 0, 0.5f);
		_visualNode.attachChild(pivotNode);
		
		// Create the cross-billboard mesh for the torch (centered on pivot origin).
		final Geometry torchGeometry = createTorchGeometry();
		pivotNode.attachChild(torchGeometry);
		
		// Create flame particles at the torch tip.
		createFlameEmitter();
		_flameEmitter.setLocalTranslation(0, 0.65f, 0);
		pivotNode.attachChild(_flameEmitter);
		
		// Apply wall tilt and offset for wall-mounted torches.
		if (_attachedFace != Face.BOTTOM)
		{
			applyWallTilt(pivotNode);
		}
		
		// Propagate block light from the torch position.
		world.propagateBlockLight(_position.x, _position.y, _position.z, TORCH_LIGHT_LEVEL);
		
		System.out.println("Torch placed at [" + _position.x + ", " + _position.y + ", " + _position.z + "] attached to " + _attachedFace);
	}
	
	@Override
	public void onRemoved(World world)
	{
		// Kill particles (TileEntityManager handles removing _visualNode from scene).
		if (_flameEmitter != null)
		{
			_flameEmitter.killAllParticles();
		}
		
		// Remove block light contribution.
		world.removeBlockLight(_position.x, _position.y, _position.z);
		
		System.out.println("Torch removed at [" + _position.x + ", " + _position.y + ", " + _position.z + "]");
	}
	
	@Override
	public void onInteract(PlayerController player, World world)
	{
		// Torches have no interaction — do nothing.
	}
	
	// ========================================================
	// Wall Tilt.
	// ========================================================
	
	/**
	 * Applies rotation and position offset to the pivot node for wall-mounted torches.<br>
	 * The torch tilts ~25° away from the wall (top leans into open space), and shifts<br>
	 * slightly toward the wall so the base appears to rest against the block surface.
	 * @param pivotNode the pivot node to rotate and offset
	 */
	private void applyWallTilt(Node pivotNode)
	{
		final Quaternion tilt;
		float offsetX = 0;
		float offsetZ = 0;
		
		switch (_attachedFace)
		{
			case NORTH: // Wall at +Z — tilt top toward -Z (lean away from wall).
			{
				tilt = new Quaternion().fromAngleAxis(-WALL_TILT_ANGLE, Vector3f.UNIT_X);
				offsetZ = WALL_SHIFT;
				break;
			}
			case SOUTH: // Wall at -Z — tilt top toward +Z.
			{
				tilt = new Quaternion().fromAngleAxis(WALL_TILT_ANGLE, Vector3f.UNIT_X);
				offsetZ = -WALL_SHIFT;
				break;
			}
			case EAST: // Wall at +X — tilt top toward -X.
			{
				tilt = new Quaternion().fromAngleAxis(WALL_TILT_ANGLE, Vector3f.UNIT_Z);
				offsetX = WALL_SHIFT;
				break;
			}
			case WEST: // Wall at -X — tilt top toward +X.
			{
				tilt = new Quaternion().fromAngleAxis(-WALL_TILT_ANGLE, Vector3f.UNIT_Z);
				offsetX = -WALL_SHIFT;
				break;
			}
			default:
			{
				return;
			}
		}
		
		pivotNode.setLocalRotation(tilt);
		
		// Shift pivot toward the wall so the base looks attached.
		final Vector3f pos = pivotNode.getLocalTranslation();
		pivotNode.setLocalTranslation(pos.x + offsetX, pos.y, pos.z + offsetZ);
	}
	
	// ========================================================
	// Torch Billboard Mesh.
	// ========================================================
	
	/**
	 * Creates a Geometry containing the cross-billboard mesh for the torch.<br>
	 * Two quads crossing at 45 degrees, textured from the atlas, with warm<br>
	 * vertex colors for self-illumination. The mesh is centered at the origin<br>
	 * horizontally (for correct pivot rotation) and extends from y=0 to y=1.
	 * @return the torch billboard geometry, ready to attach to a scene node
	 */
	private Geometry createTorchGeometry()
	{
		// Billboard quad vertices, centered at origin (pivot point).
		// Same dimensions as RegionMeshBuilder's billboard quads but shifted
		// so the center is at (0, 0, 0) instead of (0.5, 0, 0.5).
		final float lo = INSET - 0.5f; // -0.35
		final float hi = (1.0f - INSET) - 0.5f; // +0.35
		
		// 8 vertices: 4 per quad, two quads crossing at 45 degrees.
		// @formatter:off
		final float[] positions =
		{
			// Quad A: diagonal from (lo, 0, lo) to (hi, 1, hi).
			lo, 0, lo,
			lo, 1, lo,
			hi, 1, hi,
			hi, 0, hi,
			// Quad B: diagonal from (hi, 0, lo) to (lo, 1, hi).
			hi, 0, lo,
			hi, 1, lo,
			lo, 1, hi,
			lo, 0, hi
		};
		// @formatter:on
		
		// Normals — perpendicular to each quad face.
		final float n = 0.7071f; // 1/sqrt(2)
		// @formatter:off
		final float[] normals =
		{
			// Quad A normal: perpendicular to diagonal (lo,lo)→(hi,hi) = (-n, 0, n).
			-n, 0, n,
			-n, 0, n,
			-n, 0, n,
			-n, 0, n,
			// Quad B normal: perpendicular to diagonal (hi,lo)→(lo,hi) = (n, 0, n).
			n, 0, n,
			n, 0, n,
			n, 0, n,
			n, 0, n
		};
		// @formatter:on
		
		// UV coordinates from the texture atlas.
		final int atlasIndex = Block.TORCH.getAtlasIndex(Face.TOP);
		final float col = (atlasIndex % ATLAS_GRID_SIZE) * TILE_UV;
		final float row = (atlasIndex / ATLAS_GRID_SIZE) * TILE_UV;
		final float u0 = col + UV_PADDING;
		final float v0 = row + UV_PADDING;
		final float u1 = col + TILE_UV - UV_PADDING;
		final float v1 = row + TILE_UV - UV_PADDING;
		
		// @formatter:off
		final float[] texCoords =
		{
			// Quad A.
			u0, v1,
			u0, v0,
			u1, v0,
			u1, v1,
			// Quad B.
			u0, v1,
			u0, v0,
			u1, v0,
			u1, v1
		};
		// @formatter:on
		
		// Vertex colors — warm self-illumination (torch is always lit).
		final float[] colors = new float[8 * 4]; // 8 verts × RGBA
		for (int i = 0; i < 8; i++)
		{
			colors[i * 4] = SELF_LIGHT * 1.0f; // R — warm
			colors[i * 4 + 1] = SELF_LIGHT * 0.85f; // G — warm
			colors[i * 4 + 2] = SELF_LIGHT * 0.55f; // B — warm
			colors[i * 4 + 3] = 1.0f; // A
		}
		
		// Indices: two triangles per quad, two quads.
		// @formatter:off
		final int[] indices =
		{
			// Quad A.
			0, 1, 2,
			0, 2, 3,
			// Quad B.
			4, 5, 6,
			4, 6, 7
		};
		// @formatter:on
		
		// Assemble jME3 mesh.
		final Mesh mesh = new Mesh();
		mesh.setBuffer(Type.Position, 3, BufferUtils.createFloatBuffer(positions));
		mesh.setBuffer(Type.Normal, 3, BufferUtils.createFloatBuffer(normals));
		mesh.setBuffer(Type.TexCoord, 2, BufferUtils.createFloatBuffer(texCoords));
		mesh.setBuffer(Type.Color, 4, BufferUtils.createFloatBuffer(colors));
		mesh.setBuffer(Type.Index, 3, BufferUtils.createIntBuffer(indices));
		mesh.updateBound();
		
		// Create geometry with shared atlas material.
		final Geometry geom = new Geometry("TorchBillboard", mesh);
		geom.setMaterial(getSharedMaterial());
		geom.setQueueBucket(Bucket.Opaque);
		return geom;
	}
	
	/**
	 * Returns the shared material for torch billboard rendering.<br>
	 * Uses Unshaded.j3md with vertex colors and the atlas texture, matching<br>
	 * the region billboard material. Lazy-initialized on first torch placement.
	 * @return the shared torch material
	 */
	private static Material getSharedMaterial()
	{
		if (_sharedMaterial == null)
		{
			_sharedMaterial = new Material(SimpleCraft.getInstance().getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
			_sharedMaterial.setTexture("ColorMap", TextureAtlas.getAtlasTexture());
			_sharedMaterial.setBoolean("VertexColor", true);
			_sharedMaterial.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
			_sharedMaterial.setFloat("AlphaDiscardThreshold", 0.5f);
		}
		return _sharedMaterial;
	}
	
	// ========================================================
	// Particles.
	// ========================================================
	
	/**
	 * Creates a small flame particle emitter for the torch tip.<br>
	 * Similar to campfire but much smaller — a flicker rather than a fire.
	 */
	private void createFlameEmitter()
	{
		_flameEmitter = new ParticleEmitter("TorchFlame", ParticleMesh.Type.Triangle, 8);
		
		final Material mat = new Material(SimpleCraft.getInstance().getAssetManager(), "Common/MatDefs/Misc/Particle.j3md");
		mat.setTexture("Texture", SimpleCraft.getInstance().getAssetManager().loadTexture(FLAME_IMAGE_PATH));
		mat.getAdditionalRenderState().setBlendMode(BlendMode.Additive);
		_flameEmitter.setMaterial(mat);
		_flameEmitter.setQueueBucket(Bucket.Transparent);
		
		_flameEmitter.setImagesX(2);
		_flameEmitter.setImagesY(2);
		_flameEmitter.setSelectRandomImage(true);
		
		_flameEmitter.setStartSize(0.08f);
		_flameEmitter.setEndSize(0.02f);
		_flameEmitter.setStartColor(new ColorRGBA(1.0f, 0.8f, 0.2f, 0.8f)); // Warm yellow.
		_flameEmitter.setEndColor(new ColorRGBA(1.0f, 0.3f, 0.0f, 0.0f)); // Orange, fade out.
		
		_flameEmitter.setLowLife(0.2f);
		_flameEmitter.setHighLife(0.4f);
		
		_flameEmitter.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 0.6f, 0));
		_flameEmitter.getParticleInfluencer().setVelocityVariation(0.2f);
		_flameEmitter.setGravity(0, -0.3f, 0);
		
		_flameEmitter.setLocalTranslation(0, 0, 0);
		_flameEmitter.setParticlesPerSec(6);
	}
	
	// ========================================================
	// Accessors.
	// ========================================================
	
	/**
	 * Returns the block light level emitted by a placed torch.
	 */
	public static int getLightLevel()
	{
		return TORCH_LIGHT_LEVEL;
	}
	
	// ========================================================
	// Serialization.
	// ========================================================
	
	/**
	 * Torch serialization uses the base class only — attachedFace is already<br>
	 * included by {@link TileEntity#serialize()}.
	 */
	@Override
	public String serialize()
	{
		return super.serialize();
	}
}
