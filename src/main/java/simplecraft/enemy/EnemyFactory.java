package simplecraft.enemy;

import java.nio.ByteBuffer;
import java.util.Random;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import com.jme3.texture.Image;
import com.jme3.texture.Image.Format;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;

import simplecraft.enemy.Enemy.EnemyType;

/**
 * Builds box-primitive enemy models and configures their combat stats.<br>
 * Each enemy type has a distinct silhouette built from Box geometries<br>
 * organized into a Node hierarchy for future skeletal animation (pivot-based rotation).<br>
 * <br>
 * Body parts are built as: {@code Box -> Geometry -> child of pivot Node}.<br>
 * The pivot Node sits at the joint (shoulder, hip) so rotating the Node<br>
 * swings the limb naturally. The Geometry is offset downward inside the Node.<br>
 * <br>
 * All enemies have red glowing eyes for visual consistency and menace.<br>
 * Per-material 32x32 noise textures are generated procedurally providing<br>
 * natural surface grain without darkening the model.
 * @author Pantelis Andrianakis
 * @since March 4th 2026
 */
public class EnemyFactory
{
	/** Shared red eye color used by all enemy types. */
	private static final ColorRGBA EYE_RED = new ColorRGBA(0.90f, 0.08f, 0.08f, 1.0f);
	
	/** Shared white tooth color. */
	private static final ColorRGBA TOOTH_WHITE = new ColorRGBA(0.92f, 0.90f, 0.85f, 1.0f);
	
	/** Size of generated noise textures in pixels. */
	private static final int NOISE_SIZE = 32;
	
	/** Random for noise generation. */
	private static final Random NOISE_RANDOM = new Random();
	
	/**
	 * Creates a fully configured enemy of the given type.
	 * @param type the enemy type to create
	 * @param assetManager the asset manager for creating materials
	 * @return a new Enemy with model and stats configured
	 */
	public static Enemy createEnemy(EnemyType type, AssetManager assetManager)
	{
		final Enemy enemy = new Enemy(type);
		
		switch (type)
		{
			case ZOMBIE:
			{
				buildZombie(enemy, assetManager);
				applyStats(enemy, 18, 2.5f, 16, 1.5f, 8.0f, 1.5f, false);
				break;
			}
			case SKELETON:
			{
				buildSkeleton(enemy, assetManager);
				applyStats(enemy, 15, 2.8f, 18, 1.5f, 8.0f, 1.5f, false);
				break;
			}
			case WOLF:
			{
				buildWolf(enemy, assetManager);
				applyStats(enemy, 12, 4.0f, 20, 1.2f, 7.0f, 1.0f, false);
				break;
			}
			case SPIDER:
			{
				buildSpider(enemy, assetManager);
				applyStats(enemy, 9, 3.0f, 12, 1.0f, 6.0f, 1.2f, false);
				break;
			}
			case SLIME:
			{
				buildSlime(enemy, assetManager);
				applyStats(enemy, 6, 1.5f, 10, 1.0f, 5.0f, 2.0f, false);
				break;
			}
			case PIRANHA:
			{
				buildPiranha(enemy, assetManager);
				applyStats(enemy, 3, 5.0f, 12, 0.8f, 4.0f, 0.8f, true);
				break;
			}
			case PLAYER:
			{
				buildPlayer(enemy, assetManager);
				applyStats(enemy, 20, 0, 0, 0, 0, 0, false);
				break;
			}
			case DRAGON:
			{
				buildDragon(enemy, assetManager);
				
				// Stats: 200 HP, speed 2.0 (Phase 1), detection 50, attackRange 3.0 (bite), damage 8 (bite P1), cooldown 2.0s.
				applyStats(enemy, 200, 2.0f, 50, 3.0f, 8.0f, 2.0f, false);
				break;
			}
		}
		
		return enemy;
	}
	
	// ========================================================
	// Stat Application.
	// ========================================================
	
	/**
	 * Applies combat and movement stats to an enemy.
	 */
	private static void applyStats(Enemy enemy, float health, float speed, float detection, float attackRange, float damage, float cooldown, boolean aquatic)
	{
		enemy.setHealth(health);
		enemy.setMaxHealth(health);
		enemy.setMoveSpeed(speed);
		enemy.setDetectionRange(detection);
		enemy.setAttackRange(attackRange);
		enemy.setAttackDamage(damage);
		enemy.setAttackCooldown(cooldown);
		enemy.setAquatic(aquatic);
	}
	
	// ========================================================
	// Noise Texture Generation.
	// ========================================================
	
	/**
	 * Generates a 32x32 noise texture based on the given color.<br>
	 * Each pixel varies the base color by ±12% brightness.
	 * @param baseColor the base color to build noise around
	 * @return a new Texture2D with the generated noise pattern
	 */
	private static Texture2D generateNoiseTexture(ColorRGBA baseColor)
	{
		final ByteBuffer buffer = BufferUtils.createByteBuffer(NOISE_SIZE * NOISE_SIZE * 4);
		
		for (int y = 0; y < NOISE_SIZE; y++)
		{
			for (int x = 0; x < NOISE_SIZE; x++)
			{
				final float variation = 0.88f + (NOISE_RANDOM.nextFloat() * 0.24f);
				
				final int r = Math.min(255, Math.max(0, (int) (baseColor.r * variation * 255)));
				final int g = Math.min(255, Math.max(0, (int) (baseColor.g * variation * 255)));
				final int b = Math.min(255, Math.max(0, (int) (baseColor.b * variation * 255)));
				final int a = (int) (baseColor.a * 255);
				
				buffer.put((byte) r);
				buffer.put((byte) g);
				buffer.put((byte) b);
				buffer.put((byte) a);
			}
		}
		
		buffer.flip();
		final Image image = new Image(Format.RGBA8, NOISE_SIZE, NOISE_SIZE, buffer, ColorSpace.sRGB);
		return new Texture2D(image);
	}
	
	// ========================================================
	// Material Helpers.
	// ========================================================
	
	/**
	 * Creates an opaque Unshaded material with a per-color noise texture.
	 */
	private static Material makeNoiseMat(AssetManager assetManager, ColorRGBA color)
	{
		final Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
		mat.setTexture("ColorMap", generateNoiseTexture(color));
		mat.setBoolean("VertexColor", true); // Enable vertex colors.
		return mat;
	}
	
	/**
	 * Creates an opaque Unshaded material with flat color only (no texture).<br>
	 * Used for eyes, teeth and other small detail parts that should stay crisp.
	 */
	private static Material makeFlatMat(AssetManager assetManager, ColorRGBA color)
	{
		final Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
		mat.setColor("Color", color);
		mat.setBoolean("VertexColor", true); // Enable vertex colors.
		return mat;
	}
	
	/**
	 * Creates a semi-transparent Unshaded material with a noise texture and alpha blending.
	 */
	private static Material makeTransparentNoiseMat(AssetManager assetManager, ColorRGBA color)
	{
		final Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
		mat.setTexture("ColorMap", generateNoiseTexture(color));
		mat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
		mat.setBoolean("VertexColor", true); // Enable vertex colors.
		return mat;
	}
	
	/**
	 * Creates a noise-textured material WITHOUT VertexColor for the dragon.<br>
	 * The boss arena has no sky light, so EnemyLighting would set vertex colors<br>
	 * to black. Disabling VertexColor makes the dragon render at full brightness.
	 */
	private static Material makeDragonMat(AssetManager assetManager, ColorRGBA color)
	{
		final Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
		mat.setTexture("ColorMap", generateNoiseTexture(color));
		
		// VertexColor intentionally NOT set - dragon is always fully lit.
		return mat;
	}
	
	/**
	 * Creates a flat color material WITHOUT VertexColor for dragon detail parts.
	 */
	private static Material makeDragonFlatMat(AssetManager assetManager, ColorRGBA color)
	{
		final Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
		mat.setColor("Color", color);
		
		// VertexColor intentionally NOT set - dragon is always fully lit.
		return mat;
	}
	
	/**
	 * Creates a box Geometry with the given half-extents, material and local translation.
	 */
	private static Geometry makeBox(String name, float halfX, float halfY, float halfZ, Material mat, float tx, float ty, float tz)
	{
		final Box box = new Box(halfX, halfY, halfZ);
		final Geometry geom = new Geometry(name, box);
		geom.setMaterial(mat);
		geom.setLocalTranslation(tx, ty, tz);
		return geom;
	}
	
	/**
	 * Creates a pivot Node at the given position and attaches a Geometry to it.
	 */
	private static Node makePivotPart(String name, Geometry geom, float pivotX, float pivotY, float pivotZ)
	{
		final Node pivot = new Node(name);
		pivot.setLocalTranslation(pivotX, pivotY, pivotZ);
		pivot.attachChild(geom);
		return pivot;
	}
	
	/**
	 * Returns a color that is slightly darker than the input.
	 */
	private static ColorRGBA darken(ColorRGBA color, float factor)
	{
		return new ColorRGBA(color.r * factor, color.g * factor, color.b * factor, color.a);
	}
	
	/**
	 * Returns a color that is slightly lighter than the input.
	 */
	private static ColorRGBA lighten(ColorRGBA color, float factor)
	{
		return new ColorRGBA(Math.min(1, color.r * factor), Math.min(1, color.g * factor), Math.min(1, color.b * factor), color.a);
	}
	
	// ========================================================
	// Zombie (~2.3 blocks tall with neck).
	// ========================================================
	
	/**
	 * Builds a zombie model with visible neck, bare green skin torso,<br>
	 * brown pants, red eyes and white teeth.
	 */
	private static void buildZombie(Enemy enemy, AssetManager assetManager)
	{
		final Node root = enemy.getNode();
		
		// Zombie palette: dark green skin, brown pants.
		final ColorRGBA skin = new ColorRGBA(0.16f, 0.36f, 0.12f, 1.0f);
		final ColorRGBA pants = new ColorRGBA(0.35f, 0.25f, 0.15f, 1.0f);
		
		final Material skinMat = makeNoiseMat(assetManager, skin);
		final Material pantsMat = makeNoiseMat(assetManager, pants);
		final Material eyeMat = makeFlatMat(assetManager, EYE_RED);
		final Material toothMat = makeFlatMat(assetManager, TOOTH_WHITE);
		final Material mouthMat = makeFlatMat(assetManager, new ColorRGBA(0.08f, 0.15f, 0.06f, 1.0f)); // Dark green/brown.
		
		// Body.
		final Node bodyNode = makePivotPart("Body", makeBox("BodyBox", 0.3f, 0.33f, 0.15f, skinMat, 0, 0, 0), 0, 1.27f, 0);
		root.attachChild(bodyNode);
		enemy.setBody(bodyNode);
		
		// Waistband.
		root.attachChild(makeBox("Waistband", 0.31f, 0.12f, 0.16f, pantsMat, 0, 0.88f, 0));
		
		// Neck.
		root.attachChild(makeBox("Neck", 0.15f, 0.04f, 0.12f, skinMat, 0, 1.64f, 0));
		
		// Head.
		final Node headNode = makePivotPart("Head", makeBox("HeadBox", 0.25f, 0.25f, 0.25f, skinMat, 0, 0.25f, 0), 0, 1.65f, 0);
		root.attachChild(headNode);
		enemy.setHead(headNode);
		
		// Red eyes.
		headNode.attachChild(makeBox("LeftEye", 0.06f, 0.05f, 0.02f, eyeMat, -0.09f, 0.3f, -0.27f));
		headNode.attachChild(makeBox("RightEye", 0.06f, 0.05f, 0.02f, eyeMat, 0.09f, 0.3f, -0.27f));
		
		// Nose.
		headNode.attachChild(makeBox("Nose", 0.04f, 0.04f, 0.03f, skinMat, 0, 0.22f, -0.28f));
		
		// Mouth dark recess.
		headNode.attachChild(makeBox("MouthBg", 0.14f, 0.04f, 0.02f, mouthMat, 0, 0.14f, -0.27f));
		
		// Teeth.
		final float ty = 0.155f;
		final float tz = -0.29f;
		headNode.attachChild(makeBox("Tooth1", 0.025f, 0.02f, 0.01f, toothMat, -0.08f, ty, tz));
		headNode.attachChild(makeBox("Tooth2", 0.025f, 0.02f, 0.01f, toothMat, -0.025f, ty, tz));
		headNode.attachChild(makeBox("Tooth3", 0.025f, 0.02f, 0.01f, toothMat, 0.025f, ty, tz));
		headNode.attachChild(makeBox("Tooth4", 0.025f, 0.02f, 0.01f, toothMat, 0.08f, ty, tz));
		
		// Legs.
		final Node leftLeg = makePivotPart("LeftLeg", makeBox("LeftLegBox", 0.125f, 0.39f, 0.125f, skinMat, 0, -0.39f, 0), -0.175f, 0.8f, 0);
		root.attachChild(leftLeg);
		enemy.setLeftLeg(leftLeg);
		
		// Pants - slightly thicker than legs (like human model).
		leftLeg.attachChild(makeBox("LPants", 0.135f, 0.25f, 0.135f, pantsMat, 0, -0.15f, 0));
		
		final Node rightLeg = makePivotPart("RightLeg", makeBox("RightLegBox", 0.125f, 0.39f, 0.125f, skinMat, 0, -0.39f, 0), 0.175f, 0.8f, 0);
		root.attachChild(rightLeg);
		enemy.setRightLeg(rightLeg);
		
		rightLeg.attachChild(makeBox("RPants", 0.135f, 0.25f, 0.135f, pantsMat, 0, -0.15f, 0));
		
		// Arms.
		final Node leftArm = makePivotPart("LeftArm", makeBox("LeftArmBox", 0.125f, 0.4f, 0.125f, skinMat, 0, -0.4f, 0), -0.425f, 1.55f, 0);
		root.attachChild(leftArm);
		enemy.setLeftArm(leftArm);
		
		final Node rightArm = makePivotPart("RightArm", makeBox("RightArmBox", 0.125f, 0.4f, 0.125f, skinMat, 0, -0.4f, 0), 0.425f, 1.55f, 0);
		root.attachChild(rightArm);
		enemy.setRightArm(rightArm);
	}
	
	// ========================================================
	// Skeleton (~2.2 blocks tall with neck, short brown pants).
	// ========================================================
	
	/**
	 * Builds a skeleton model - thin bone-colored humanoid with a subtle neck,<br>
	 * brown pants from waist down, red eye sockets, ribs and prominent teeth.
	 */
	private static void buildSkeleton(Enemy enemy, AssetManager assetManager)
	{
		final Node root = enemy.getNode();
		final ColorRGBA bone = new ColorRGBA(0.82f, 0.78f, 0.72f, 1.0f);
		final ColorRGBA greyBone = new ColorRGBA(0.65f, 0.62f, 0.58f, 1.0f);
		final ColorRGBA pants = new ColorRGBA(0.35f, 0.25f, 0.15f, 1.0f);
		
		final Material boneMat = makeNoiseMat(assetManager, bone);
		final Material greyBoneMat = makeNoiseMat(assetManager, greyBone);
		final Material pantsMat = makeNoiseMat(assetManager, pants);
		final Material eyeMat = makeFlatMat(assetManager, EYE_RED);
		final Material toothMat = makeFlatMat(assetManager, TOOTH_WHITE);
		final Material ribMat = makeNoiseMat(assetManager, darken(greyBone, 0.7f));
		final Material jointMat = makeNoiseMat(assetManager, darken(bone, 0.8f));
		
		// Body: shorter so pants visible at waist.
		// HalfY=0.3, center at Y=1.2 -> bottom at Y=0.9, top at Y=1.5.
		final Node bodyNode = makePivotPart("Body", makeBox("BodyBox", 0.2f, 0.3f, 0.125f, greyBoneMat, 0, 0, 0), 0, 1.2f, 0);
		root.attachChild(bodyNode);
		enemy.setBody(bodyNode);
		
		// Rib detail.
		bodyNode.attachChild(makeBox("Rib1", 0.16f, 0.015f, 0.005f, ribMat, 0, 0.2f, -0.145f));
		bodyNode.attachChild(makeBox("Rib2", 0.16f, 0.015f, 0.005f, ribMat, 0, 0.1f, -0.145f));
		bodyNode.attachChild(makeBox("Rib3", 0.16f, 0.015f, 0.005f, ribMat, 0, 0.0f, -0.145f));
		bodyNode.attachChild(makeBox("Rib4", 0.14f, 0.015f, 0.005f, ribMat, 0, -0.1f, -0.145f));
		
		// Neck: doubled thickness, visible connector.
		root.attachChild(makeBox("Neck", 0.1f, 0.03f, 0.08f, boneMat, 0, 1.53f, 0));
		
		// Head.
		final Node headNode = makePivotPart("Head", makeBox("HeadBox", 0.225f, 0.225f, 0.225f, boneMat, 0, 0.225f, 0), 0, 1.54f, 0);
		root.attachChild(headNode);
		enemy.setHead(headNode);
		
		// Red eye sockets.
		headNode.attachChild(makeBox("LeftEye", 0.055f, 0.05f, 0.02f, eyeMat, -0.08f, 0.28f, -0.245f));
		headNode.attachChild(makeBox("RightEye", 0.055f, 0.05f, 0.02f, eyeMat, 0.08f, 0.28f, -0.245f));
		
		// Nose hole.
		headNode.attachChild(makeBox("Nose", 0.03f, 0.03f, 0.02f, ribMat, 0, 0.2f, -0.245f));
		
		// Mouth dark recess.
		headNode.attachChild(makeBox("MouthBg", 0.14f, 0.035f, 0.02f, makeFlatMat(assetManager, new ColorRGBA(0.25f, 0.22f, 0.20f, 1.0f)), 0, 0.1f, -0.245f));
		
		// 5 individual teeth.
		final float ty = 0.115f;
		final float tz = -0.265f;
		headNode.attachChild(makeBox("Tooth1", 0.02f, 0.025f, 0.01f, toothMat, -0.08f, ty, tz));
		headNode.attachChild(makeBox("Tooth2", 0.02f, 0.025f, 0.01f, toothMat, -0.035f, ty, tz));
		headNode.attachChild(makeBox("Tooth3", 0.02f, 0.025f, 0.01f, toothMat, 0.0f, ty, tz));
		headNode.attachChild(makeBox("Tooth4", 0.02f, 0.025f, 0.01f, toothMat, 0.035f, ty, tz));
		headNode.attachChild(makeBox("Tooth5", 0.02f, 0.025f, 0.01f, toothMat, 0.08f, ty, tz));
		
		// Brown waistband - fills gap between torso bottom (Y=0.9) and leg top (Y=0.75).
		// HalfY=0.12 centered at Y=0.83 -> from Y=0.71 to Y=0.95.
		root.attachChild(makeBox("Waistband", 0.21f, 0.12f, 0.13f, pantsMat, 0, 0.83f, 0));
		
		// Legs: bone-colored with short brown pants covering belly and upper thigh.
		final Node leftLeg = makePivotPart("LeftLeg", makeBox("LeftLegBox", 0.075f, 0.375f, 0.075f, boneMat, 0, -0.375f, 0), -0.125f, 0.75f, 0);
		root.attachChild(leftLeg);
		enemy.setLeftLeg(leftLeg);
		
		// Short pants - covers from above hip (into belly) to mid-thigh.
		// Centered at Y=-0.05 with halfY=0.15 -> covers +0.10 (belly overlap) to -0.20.
		leftLeg.attachChild(makeBox("LPants", 0.085f, 0.15f, 0.085f, pantsMat, 0, -0.05f, 0));
		
		final Node rightLeg = makePivotPart("RightLeg", makeBox("RightLegBox", 0.075f, 0.375f, 0.075f, boneMat, 0, -0.375f, 0), 0.125f, 0.75f, 0);
		root.attachChild(rightLeg);
		enemy.setRightLeg(rightLeg);
		
		rightLeg.attachChild(makeBox("RPants", 0.085f, 0.15f, 0.085f, pantsMat, 0, -0.05f, 0));
		
		// Knee joint marks on exposed bone below pants.
		leftLeg.attachChild(makeBox("LKnee", 0.05f, 0.025f, 0.005f, jointMat, 0, -0.375f, -0.095f));
		rightLeg.attachChild(makeBox("RKnee", 0.05f, 0.025f, 0.005f, jointMat, 0, -0.375f, -0.095f));
		
		// Arms.
		final Node leftArm = makePivotPart("LeftArm", makeBox("LeftArmBox", 0.075f, 0.375f, 0.075f, greyBoneMat, 0, -0.375f, 0), -0.275f, 1.45f, 0);
		root.attachChild(leftArm);
		enemy.setLeftArm(leftArm);
		
		leftArm.attachChild(makeBox("LElbow", 0.05f, 0.025f, 0.005f, jointMat, 0, -0.375f, -0.095f));
		
		final Node rightArm = makePivotPart("RightArm", makeBox("RightArmBox", 0.075f, 0.375f, 0.075f, greyBoneMat, 0, -0.375f, 0), 0.275f, 1.45f, 0);
		root.attachChild(rightArm);
		enemy.setRightArm(rightArm);
		
		rightArm.attachChild(makeBox("RElbow", 0.05f, 0.025f, 0.005f, jointMat, 0, -0.375f, -0.095f));
	}
	
	// ========================================================
	// Wolf (~0.85 blocks tall, grey).
	// ========================================================
	
	/**
	 * Builds a grey wolf model with pointed ears, snout, short Doberman-style tail,<br>
	 * lighter belly and darker back stripe. Red glowing eyes.
	 */
	private static void buildWolf(Enemy enemy, AssetManager assetManager)
	{
		final Node root = enemy.getNode();
		final ColorRGBA grey = new ColorRGBA(0.52f, 0.52f, 0.50f, 1.0f);
		final ColorRGBA darkGrey = new ColorRGBA(0.38f, 0.38f, 0.36f, 1.0f);
		final Material greyMat = makeNoiseMat(assetManager, grey);
		final Material darkGreyMat = makeNoiseMat(assetManager, darkGrey);
		final Material eyeMat = makeFlatMat(assetManager, EYE_RED);
		
		final Material bellyMat = makeNoiseMat(assetManager, lighten(grey, 1.3f));
		final Material backMat = makeNoiseMat(assetManager, darken(grey, 0.7f));
		final Material noseMat = makeFlatMat(assetManager, new ColorRGBA(0.12f, 0.10f, 0.10f, 1.0f));
		final Material earInnerMat = makeNoiseMat(assetManager, new ColorRGBA(0.58f, 0.48f, 0.45f, 1.0f));
		
		// Body.
		final Node bodyNode = makePivotPart("Body", makeBox("BodyBox", 0.25f, 0.2f, 0.45f, greyMat, 0, 0, 0), 0, 0.6f, 0);
		root.attachChild(bodyNode);
		enemy.setBody(bodyNode);
		
		bodyNode.attachChild(makeBox("Belly", 0.18f, 0.005f, 0.35f, bellyMat, 0, -0.22f, -0.02f));
		bodyNode.attachChild(makeBox("BackStripe", 0.1f, 0.005f, 0.4f, backMat, 0, 0.22f, 0));
		
		// Head.
		final Node headNode = makePivotPart("Head", makeBox("HeadBox", 0.2f, 0.175f, 0.175f, darkGreyMat, 0, 0, 0), 0, 0.7f, -0.6f);
		root.attachChild(headNode);
		enemy.setHead(headNode);
		
		headNode.attachChild(makeBox("Snout", 0.1f, 0.1f, 0.15f, greyMat, 0, -0.05f, -0.3f));
		headNode.attachChild(makeBox("NoseTip", 0.06f, 0.05f, 0.02f, noseMat, 0, 0.0f, -0.47f));
		headNode.attachChild(makeBox("LeftEye", 0.04f, 0.04f, 0.02f, eyeMat, -0.1f, 0.06f, -0.20f));
		headNode.attachChild(makeBox("RightEye", 0.04f, 0.04f, 0.02f, eyeMat, 0.1f, 0.06f, -0.20f));
		
		// Ears.
		headNode.attachChild(makeBox("LeftEar", 0.05f, 0.12f, 0.04f, darkGreyMat, -0.12f, 0.28f, 0.0f));
		headNode.attachChild(makeBox("LeftEarInner", 0.03f, 0.08f, 0.005f, earInnerMat, -0.12f, 0.28f, -0.055f));
		headNode.attachChild(makeBox("RightEar", 0.05f, 0.12f, 0.04f, darkGreyMat, 0.12f, 0.28f, 0.0f));
		headNode.attachChild(makeBox("RightEarInner", 0.03f, 0.08f, 0.005f, earInnerMat, 0.12f, 0.28f, -0.055f));
		
		headNode.attachChild(makeBox("Mouth", 0.07f, 0.015f, 0.005f, noseMat, 0, -0.11f, -0.43f));
		
		// Canine teeth - two white fangs hanging below the snout.
		final Material toothMat = makeFlatMat(assetManager, TOOTH_WHITE);
		headNode.attachChild(makeBox("LeftCanine", 0.015f, 0.03f, 0.015f, toothMat, -0.05f, -0.14f, -0.38f));
		headNode.attachChild(makeBox("RightCanine", 0.015f, 0.03f, 0.015f, toothMat, 0.05f, -0.14f, -0.38f));
		
		// Tail.
		final Node tailNode = new Node("Tail");
		tailNode.setLocalTranslation(0, 0.15f, 0.45f);
		tailNode.setLocalRotation(new Quaternion().fromAngleAxis(-35f * FastMath.DEG_TO_RAD, Vector3f.UNIT_X));
		tailNode.attachChild(makeBox("TailBox", 0.05f, 0.15f, 0.05f, darkGreyMat, 0, 0.15f, 0));
		bodyNode.attachChild(tailNode);
		
		// Four legs.
		final float legOffsetX = 0.175f;
		final float legOffsetZ = 0.3f;
		
		final Node frontLeft = makePivotPart("LeftLeg", makeBox("FLLegBox", 0.075f, 0.2f, 0.075f, darkGreyMat, 0, -0.2f, 0), -legOffsetX, 0.4f, -legOffsetZ);
		root.attachChild(frontLeft);
		enemy.setLeftLeg(frontLeft);
		
		final Node frontRight = makePivotPart("RightLeg", makeBox("FRLegBox", 0.075f, 0.2f, 0.075f, darkGreyMat, 0, -0.2f, 0), legOffsetX, 0.4f, -legOffsetZ);
		root.attachChild(frontRight);
		enemy.setRightLeg(frontRight);
		
		final Node backLeft = makePivotPart("LeftArm", makeBox("BLLegBox", 0.075f, 0.2f, 0.075f, darkGreyMat, 0, -0.2f, 0), -legOffsetX, 0.4f, legOffsetZ);
		root.attachChild(backLeft);
		enemy.setLeftArm(backLeft);
		
		final Node backRight = makePivotPart("RightArm", makeBox("BRLegBox", 0.075f, 0.2f, 0.075f, darkGreyMat, 0, -0.2f, 0), legOffsetX, 0.4f, legOffsetZ);
		root.attachChild(backRight);
		enemy.setRightArm(backRight);
		
		frontLeft.attachChild(makeBox("FLPaw", 0.06f, 0.03f, 0.06f, bellyMat, 0, -0.4f, 0));
		frontRight.attachChild(makeBox("FRPaw", 0.06f, 0.03f, 0.06f, bellyMat, 0, -0.4f, 0));
	}
	
	// ========================================================
	// Spider (dark blue-black, big abdomen, smaller legs).
	// ========================================================
	
	/**
	 * Builds a spider model - dark blue-black body with a visibly large abdomen,<br>
	 * 8 thinner legs spread wide, red eyes and fangs.
	 */
	private static void buildSpider(Enemy enemy, AssetManager assetManager)
	{
		final Node root = enemy.getNode();
		
		// Dark blue-black spider palette.
		final ColorRGBA spiderBlue = new ColorRGBA(0.08f, 0.08f, 0.18f, 1.0f);
		final Material bodyMat = makeNoiseMat(assetManager, spiderBlue);
		final Material eyeMat = makeFlatMat(assetManager, EYE_RED);
		
		final Material stripeMat = makeNoiseMat(assetManager, darken(spiderBlue, 0.6f));
		final Material legJointMat = makeNoiseMat(assetManager, lighten(spiderBlue, 2.0f));
		final Material fangMat = makeFlatMat(assetManager, new ColorRGBA(0.82f, 0.82f, 0.78f, 1.0f));
		
		// Front body (cephalothorax): smaller front section.
		final Node bodyNode = makePivotPart("Body", makeBox("BodyBox", 0.25f, 0.13f, 0.2f, bodyMat, 0, 0, 0), 0, 0.5f, 0);
		root.attachChild(bodyNode);
		enemy.setBody(bodyNode);
		
		// Abdomen (rear): separate block behind cephalothorax, smaller and rounder.
		root.attachChild(makeBox("Abdomen", 0.3f, 0.22f, 0.3f, bodyMat, 0, 0.48f, 0.5f));
		
		// Abdomen dark stripe markings on top.
		root.attachChild(makeBox("AbdStripe1", 0.16f, 0.006f, 0.07f, stripeMat, 0, 0.71f, 0.40f));
		root.attachChild(makeBox("AbdStripe2", 0.12f, 0.006f, 0.07f, stripeMat, 0, 0.71f, 0.52f));
		root.attachChild(makeBox("AbdStripe3", 0.08f, 0.006f, 0.05f, stripeMat, 0, 0.71f, 0.62f));
		
		// Head plate - darker front.
		bodyNode.attachChild(makeBox("HeadPlate", 0.16f, 0.1f, 0.06f, stripeMat, 0, 0.02f, -0.2f));
		
		// Eyes - well forward.
		bodyNode.attachChild(makeBox("LeftEyeL", 0.04f, 0.035f, 0.03f, eyeMat, -0.08f, 0.09f, -0.28f));
		bodyNode.attachChild(makeBox("RightEyeL", 0.04f, 0.035f, 0.03f, eyeMat, 0.08f, 0.09f, -0.28f));
		bodyNode.attachChild(makeBox("LeftEyeS", 0.02f, 0.02f, 0.025f, eyeMat, -0.15f, 0.07f, -0.26f));
		bodyNode.attachChild(makeBox("RightEyeS", 0.02f, 0.02f, 0.025f, eyeMat, 0.15f, 0.07f, -0.26f));
		
		// Fangs.
		bodyNode.attachChild(makeBox("LeftFang", 0.02f, 0.06f, 0.02f, fangMat, -0.05f, -0.1f, -0.28f));
		bodyNode.attachChild(makeBox("RightFang", 0.02f, 0.06f, 0.02f, fangMat, 0.05f, -0.1f, -0.28f));
		
		// 8 legs - thinner (0.03 half-extent), shorter (0.25 half-extent), 65° spread.
		// Legs attach to the cephalothorax, not the abdomen.
		final float[] zOffsets =
		{
			-0.1f,
			0.0f,
			0.1f,
			0.18f
		};
		final float legAngle = 65f * FastMath.DEG_TO_RAD;
		final float legHalfY = 0.25f;
		
		for (int i = 0; i < 4; i++)
		{
			// Left leg.
			final Node leftLegNode = new Node("LeftSpiderLeg" + i);
			leftLegNode.setLocalTranslation(-0.25f, 0.5f, zOffsets[i]);
			leftLegNode.attachChild(makeBox("LLeg" + i, 0.03f, legHalfY, 0.03f, bodyMat, 0, -legHalfY, 0));
			leftLegNode.attachChild(makeBox("LLegJoint" + i, 0.04f, 0.02f, 0.04f, legJointMat, 0, -legHalfY, 0));
			leftLegNode.setLocalRotation(new Quaternion().fromAngleAxis(-legAngle, Vector3f.UNIT_Z));
			root.attachChild(leftLegNode);
			
			// Right leg.
			final Node rightLegNode = new Node("RightSpiderLeg" + i);
			rightLegNode.setLocalTranslation(0.25f, 0.5f, zOffsets[i]);
			rightLegNode.attachChild(makeBox("RLeg" + i, 0.03f, legHalfY, 0.03f, bodyMat, 0, -legHalfY, 0));
			rightLegNode.attachChild(makeBox("RLegJoint" + i, 0.04f, 0.02f, 0.04f, legJointMat, 0, -legHalfY, 0));
			rightLegNode.setLocalRotation(new Quaternion().fromAngleAxis(legAngle, Vector3f.UNIT_Z));
			root.attachChild(rightLegNode);
		}
	}
	
	// ========================================================
	// Slime (0.8 cubed, semi-transparent).
	// ========================================================
	
	/**
	 * Builds a slime model - semi-transparent green cube with red eyes,<br>
	 * darker core nucleus and a mouth slit.
	 */
	private static void buildSlime(Enemy enemy, AssetManager assetManager)
	{
		final Node root = enemy.getNode();
		final ColorRGBA slimeGreen = new ColorRGBA(0.20f, 0.70f, 0.20f, 0.6f);
		final Material slimeMat = makeTransparentNoiseMat(assetManager, slimeGreen);
		final Material eyeMat = makeFlatMat(assetManager, EYE_RED);
		final Material coreMat = makeNoiseMat(assetManager, new ColorRGBA(0.10f, 0.35f, 0.10f, 0.9f));
		final Material mouthMat = makeFlatMat(assetManager, new ColorRGBA(0.04f, 0.15f, 0.04f, 1.0f));
		
		final Node bodyNode = makePivotPart("Body", makeBox("SlimeBox", 0.4f, 0.4f, 0.4f, slimeMat, 0, 0, 0), 0, 0.4f, 0);
		bodyNode.getChild("SlimeBox").setQueueBucket(Bucket.Transparent);
		root.attachChild(bodyNode);
		enemy.setBody(bodyNode);
		
		final Geometry core = makeBox("Core", 0.2f, 0.2f, 0.2f, coreMat, 0, -0.05f, 0);
		core.setQueueBucket(Bucket.Transparent);
		bodyNode.attachChild(core);
		
		bodyNode.attachChild(makeBox("LeftEye", 0.07f, 0.06f, 0.07f, eyeMat, -0.12f, 0.05f, -0.2f));
		bodyNode.attachChild(makeBox("RightEye", 0.07f, 0.06f, 0.07f, eyeMat, 0.12f, 0.05f, -0.2f));
		bodyNode.attachChild(makeBox("Mouth", 0.1f, 0.025f, 0.07f, mouthMat, 0, -0.08f, -0.2f));
	}
	
	// ========================================================
	// Piranha (~0.5 long, ~0.2 tall, aquatic).
	// ========================================================
	
	/**
	 * Builds a piranha model - small fish with separate individual teeth,<br>
	 * red eyes, fins and belly coloring. The only aquatic enemy.
	 */
	private static void buildPiranha(Enemy enemy, AssetManager assetManager)
	{
		final Node root = enemy.getNode();
		final ColorRGBA silverGrey = new ColorRGBA(0.58f, 0.58f, 0.62f, 1.0f);
		final ColorRGBA darkGrey = new ColorRGBA(0.45f, 0.45f, 0.48f, 1.0f);
		final ColorRGBA darkTeal = new ColorRGBA(0.10f, 0.35f, 0.35f, 1.0f);
		
		final Material bodyMat = makeNoiseMat(assetManager, silverGrey);
		final Material headMat = makeNoiseMat(assetManager, darkGrey);
		final Material finMat = makeFlatMat(assetManager, darkTeal);
		final Material eyeMat = makeFlatMat(assetManager, EYE_RED);
		final Material toothMat = makeFlatMat(assetManager, TOOTH_WHITE);
		
		final Material bellyMat = makeNoiseMat(assetManager, lighten(silverGrey, 1.2f));
		final Material backMat = makeNoiseMat(assetManager, darken(silverGrey, 0.7f));
		
		// Body.
		final Node bodyNode = makePivotPart("Body", makeBox("BodyBox", 0.15f, 0.1f, 0.25f, bodyMat, 0, 0, 0), 0, 0.1f, 0);
		root.attachChild(bodyNode);
		enemy.setBody(bodyNode);
		
		// Belly patch - overlaps into body bottom, thicker to be visible.
		bodyNode.attachChild(makeBox("Belly", 0.12f, 0.015f, 0.2f, bellyMat, 0, -0.1f, 0));
		
		// Back patch - overlaps into body top, thicker to be visible.
		bodyNode.attachChild(makeBox("Back", 0.1f, 0.015f, 0.2f, backMat, 0, 0.1f, 0));
		
		// Dorsal fin - overlaps into body top (body top at Y=0.1 local).
		bodyNode.attachChild(makeBox("DorsalFin", 0.01f, 0.06f, 0.08f, finMat, 0, 0.13f, -0.02f));
		
		// Side fins - overlap into body sides (body side at X=±0.15 local).
		bodyNode.attachChild(makeBox("LeftFin", 0.005f, 0.03f, 0.06f, finMat, -0.14f, -0.04f, -0.05f));
		bodyNode.attachChild(makeBox("RightFin", 0.005f, 0.03f, 0.06f, finMat, 0.14f, -0.04f, -0.05f));
		
		// Head - back face touches body front (body front at Z=-0.25, head halfZ=0.075, so center at -0.325).
		final Node headNode = makePivotPart("Head", makeBox("HeadBox", 0.125f, 0.09f, 0.075f, headMat, 0, 0, 0), 0, 0.1f, -0.325f);
		root.attachChild(headNode);
		enemy.setHead(headNode);
		
		headNode.attachChild(makeBox("LeftEye", 0.025f, 0.03f, 0.025f, eyeMat, -0.135f, 0.02f, 0));
		headNode.attachChild(makeBox("RightEye", 0.025f, 0.03f, 0.025f, eyeMat, 0.135f, 0.02f, 0));
		
		// Jaw - longer, offset down clearly from head to avoid z-fighting.
		final Material jawMat = makeNoiseMat(assetManager, new ColorRGBA(0.25f, 0.25f, 0.28f, 1.0f));
		headNode.attachChild(makeBox("LowerJaw", 0.11f, 0.025f, 0.1f, jawMat, 0, -0.115f, -0.03f));
		
		// Two teeth on lower jaw - left and right fangs pointing up.
		final float ttz = -0.13f;
		headNode.attachChild(makeBox("LeftFang", 0.015f, 0.03f, 0.01f, toothMat, -0.06f, -0.09f, ttz));
		headNode.attachChild(makeBox("RightFang", 0.015f, 0.03f, 0.01f, toothMat, 0.06f, -0.09f, ttz));
		
		// Tail fin.
		// Tail fin - overlaps into body back (body back at Z=0.25 local).
		bodyNode.attachChild(makeBox("TailFin", 0.01f, 0.075f, 0.1f, finMat, 0, 0, 0.30f));
	}
	
	// ========================================================
	// Player (~2.3 blocks tall, iron armor).
	// ========================================================
	
	/**
	 * Builds a player model - light brown skin, brown hair, green eyes,<br>
	 * iron helmet, iron armor on torso, iron pants, iron boots.<br>
	 * Same proportions as zombie (neck, longer arms).
	 */
	private static void buildPlayer(Enemy enemy, AssetManager assetManager)
	{
		final Node root = enemy.getNode();
		
		// Player palette.
		final ColorRGBA skin = new ColorRGBA(0.67f, 0.5f, 0.36f, 1.0f);
		final ColorRGBA skinDark = new ColorRGBA(0.60f, 0.45f, 0.32f, 1.0f);
		final ColorRGBA hair = new ColorRGBA(0.20f, 0.12f, 0.06f, 1.0f);
		final ColorRGBA eye = new ColorRGBA(0.04f, 0.30f, 0.10f, 1.0f);
		final ColorRGBA iron = new ColorRGBA(0.62f, 0.62f, 0.65f, 1.0f);
		final ColorRGBA ironDark = new ColorRGBA(0.48f, 0.48f, 0.52f, 1.0f);
		final ColorRGBA ironLight = new ColorRGBA(0.72f, 0.72f, 0.76f, 1.0f);
		
		// Create both noisy and flat materials for skin.
		final Material skinFlatMat = makeFlatMat(assetManager, skin); // For face and body.
		final Material skinDarkFlatMat = makeFlatMat(assetManager, skinDark); // For nose.
		final Material hairMat = makeNoiseMat(assetManager, hair);
		final Material ironMat = makeNoiseMat(assetManager, iron);
		final Material ironDarkMat = makeNoiseMat(assetManager, ironDark);
		final Material ironLightMat = makeNoiseMat(assetManager, ironLight);
		final Material eyeMat = makeFlatMat(assetManager, eye);
		
		// Iron armor torso - same size as zombie torso, shorter to show waistband.
		final Node bodyNode = makePivotPart("Body", makeBox("BodyBox", 0.3f, 0.33f, 0.18f, ironMat, 0, 0, 0), 0, 1.27f, 0);
		root.attachChild(bodyNode);
		enemy.setBody(bodyNode);
		
		// Armor chest plate detail on front.
		bodyNode.attachChild(makeBox("ChestPlate", 0.22f, 0.2f, 0.005f, ironDarkMat, 0, 0.05f, -0.20f));
		
		// Iron waistband - fills gap between torso and legs.
		root.attachChild(makeBox("Waistband", 0.29f, 0.1f, 0.16f, ironDarkMat, 0, 0.88f, 0));
		
		// Neck: use flat material.
		root.attachChild(makeBox("Neck", 0.15f, 0.04f, 0.12f, skinFlatMat, 0, 1.64f, 0));
		
		// Head: use flat material.
		final Node headNode = makePivotPart("Head", makeBox("HeadBox", 0.2f, 0.2f, 0.2f, skinFlatMat, 0, 0.2f, 0), 0, 1.65f, 0);
		root.attachChild(headNode);
		enemy.setHead(headNode);
		
		// Brown hair - thin strips. (keep hair noisy).
		headNode.attachChild(makeBox("HairBack", 0.19f, 0.10f, 0.02f, hairMat, 0, 0.25f, 0.21f));
		
		// Side strips: center Z=0.05, halfZ=0.18 -> Z=-0.13 to Z=0.23 (overlaps back at Z=0.21).
		headNode.attachChild(makeBox("HairLeft", 0.02f, 0.06f, 0.18f, hairMat, -0.21f, 0.28f, 0.05f));
		headNode.attachChild(makeBox("HairRight", 0.02f, 0.06f, 0.18f, hairMat, 0.21f, 0.28f, 0.05f));
		
		// Iron helmet - open-face cap sitting on top half of head.
		// Only covers forehead and above, leaving eyes/nose/mouth visible.
		// Top cap: center Y=0.44, halfY=0.08 -> Y=0.36 to Y=0.52.
		headNode.attachChild(makeBox("HelmetCap", 0.23f, 0.08f, 0.23f, ironMat, 0, 0.44f, 0));
		
		// Forehead band: thicker. Center Y=0.34, halfY=0.06 -> Y=0.28 to Y=0.40.
		headNode.attachChild(makeBox("HelmetBand", 0.24f, 0.06f, 0.24f, ironLightMat, 0, 0.34f, 0));
		
		// Nose guard - hangs down from the band center.
		headNode.attachChild(makeBox("NoseGuard", 0.02f, 0.10f, 0.025f, ironDarkMat, 0, 0.27f, -0.24f));
		
		// Eyes - white sclera with green iris dot.
		final Material whiteEyeMat = makeFlatMat(assetManager, new ColorRGBA(0.92f, 0.92f, 0.92f, 1.0f));
		headNode.attachChild(makeBox("LeftEyeWhite", 0.035f, 0.03f, 0.015f, whiteEyeMat, -0.07f, 0.24f, -0.215f));
		headNode.attachChild(makeBox("RightEyeWhite", 0.035f, 0.03f, 0.015f, whiteEyeMat, 0.07f, 0.24f, -0.215f));
		headNode.attachChild(makeBox("LeftIris", 0.018f, 0.018f, 0.005f, eyeMat, -0.07f, 0.24f, -0.235f));
		headNode.attachChild(makeBox("RightIris", 0.018f, 0.018f, 0.005f, eyeMat, 0.07f, 0.24f, -0.235f));
		
		// Nose - use flat dark skin.
		headNode.attachChild(makeBox("Nose", 0.02f, 0.025f, 0.02f, skinDarkFlatMat, 0, 0.17f, -0.22f));
		
		// Mouth.
		headNode.attachChild(makeBox("Mouth", 0.05f, 0.01f, 0.015f, makeFlatMat(assetManager, new ColorRGBA(0.45f, 0.30f, 0.25f, 1.0f)), 0, 0.1f, -0.215f));
		
		// Legs (skin with iron pants overlay).
		final Node leftLeg = makePivotPart("LeftLeg", makeBox("LeftLegBox", 0.125f, 0.39f, 0.125f, skinFlatMat, 0, -0.39f, 0), -0.175f, 0.8f, 0);
		root.attachChild(leftLeg);
		enemy.setLeftLeg(leftLeg);
		
		// Iron pants.
		leftLeg.attachChild(makeBox("LPants", 0.135f, 0.25f, 0.135f, ironDarkMat, 0, -0.15f, 0));
		
		// Iron boots.
		leftLeg.attachChild(makeBox("LBoots", 0.14f, 0.2f, 0.14f, ironMat, 0, -0.6f, 0));
		
		final Node rightLeg = makePivotPart("RightLeg", makeBox("RightLegBox", 0.125f, 0.39f, 0.125f, skinFlatMat, 0, -0.39f, 0), 0.175f, 0.8f, 0);
		root.attachChild(rightLeg);
		enemy.setRightLeg(rightLeg);
		
		rightLeg.attachChild(makeBox("RPants", 0.135f, 0.25f, 0.135f, ironDarkMat, 0, -0.15f, 0));
		rightLeg.attachChild(makeBox("RBoots", 0.14f, 0.2f, 0.14f, ironMat, 0, -0.6f, 0));
		
		// Arms (skin with iron shoulder plates).
		final Node leftArm = makePivotPart("LeftArm", makeBox("LeftArmBox", 0.125f, 0.4f, 0.125f, skinFlatMat, 0, -0.4f, 0), -0.425f, 1.55f, 0);
		root.attachChild(leftArm);
		enemy.setLeftArm(leftArm);
		
		leftArm.attachChild(makeBox("LShoulderPlate", 0.15f, 0.18f, 0.15f, ironLightMat, 0, -0.12f, 0));
		
		final Node rightArm = makePivotPart("RightArm", makeBox("RightArmBox", 0.125f, 0.4f, 0.125f, skinFlatMat, 0, -0.4f, 0), 0.425f, 1.55f, 0);
		root.attachChild(rightArm);
		enemy.setRightArm(rightArm);
		
		rightArm.attachChild(makeBox("RShoulderPlate", 0.15f, 0.18f, 0.15f, ironLightMat, 0, -0.12f, 0));
	}
	
	// ========================================================
	// Dragon (~2.2 blocks tall, ~7 blocks long, ground drake).
	// ========================================================
	
	/**
	 * Builds a dragon model - large ground lizard with dark green-grey body,<br>
	 * four thick legs, articulated jaw, yellow-orange eyes, white teeth,<br>
	 * and a three-segment tail. No wings - this is a ground drake.<br>
	 * <br>
	 * Uses leftLeg/rightLeg for front legs and leftArm/rightArm for back legs<br>
	 * (same convention as the wolf). Jaw and tail segments are stored in<br>
	 * dedicated dragon-specific fields on {@link Enemy}.
	 */
	private static void buildDragon(Enemy enemy, AssetManager assetManager)
	{
		final Node root = enemy.getNode();
		
		// Dragon palette - brown/earth tones.
		// Dragon uses unlit materials (no VertexColor) because the arena has no sky light -
		// EnemyLighting would read sky=0 and darken the dragon to invisible.
		final ColorRGBA bodyColor = new ColorRGBA(0.40f, 0.28f, 0.18f, 1.0f); // Warm brown.
		final ColorRGBA headColor = new ColorRGBA(0.35f, 0.24f, 0.15f, 1.0f); // Darker brown.
		final ColorRGBA eyeColor = EYE_RED; // Red glowing eyes like all enemies.
		final ColorRGBA bellyColor = new ColorRGBA(0.50f, 0.40f, 0.28f, 1.0f); // Lighter tan.
		final ColorRGBA hornColor = new ColorRGBA(0.25f, 0.20f, 0.12f, 1.0f); // Dark bone.
		final ColorRGBA wingColor = new ColorRGBA(0.45f, 0.32f, 0.20f, 1.0f); // Leathery brown.
		final ColorRGBA wingMembraneColor = new ColorRGBA(0.55f, 0.38f, 0.22f, 0.85f); // Lighter, slightly transparent.
		final ColorRGBA backColor = darken(bodyColor, 0.75f);
		
		final Material bodyMat = makeDragonMat(assetManager, bodyColor);
		final Material headMat = makeDragonMat(assetManager, headColor);
		final Material eyeMat = makeDragonFlatMat(assetManager, eyeColor);
		final Material toothMat = makeDragonFlatMat(assetManager, TOOTH_WHITE);
		final Material bellyMat = makeDragonMat(assetManager, bellyColor);
		final Material backMat = makeDragonMat(assetManager, backColor);
		final Material jawMat = makeDragonMat(assetManager, darken(headColor, 0.85f));
		final Material hornMat = makeDragonMat(assetManager, hornColor);
		final Material wingMat = makeDragonMat(assetManager, wingColor);
		
		// Wing membrane - semi-transparent.
		final Material membraneMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
		membraneMat.setTexture("ColorMap", generateNoiseTexture(wingMembraneColor));
		membraneMat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
		
		// ---- Body ----
		// Pivot at hip level (Y=1.0). Body geometry center at (0, 0.6, 0).
		// Body: 1.8 wide, 1.2 tall, 3.0 long.
		final Node bodyNode = makePivotPart("Body", makeBox("BodyBox", 0.9f, 0.6f, 1.5f, bodyMat, 0, 0.6f, 0), 0, 1.0f, 0);
		root.attachChild(bodyNode);
		enemy.setBody(bodyNode);
		
		// Belly underside.
		bodyNode.attachChild(makeBox("Belly", 0.7f, 0.03f, 1.3f, bellyMat, 0, 0.01f, 0));
		
		// Back ridge - spiny ridge along the spine.
		bodyNode.attachChild(makeBox("BackRidge", 0.08f, 0.12f, 1.2f, backColor.equals(backColor) ? hornMat : backMat, 0, 1.24f, 0));
		
		// ---- Head ----
		// Two-part head: wider back cranium + narrower forward snout.
		// Head pivot at (0, 1.2, -2.1). Dragon faces -Z.
		final Node headNode = new Node("Head");
		headNode.setLocalTranslation(0, 1.2f, -2.1f);
		root.attachChild(headNode);
		enemy.setHead(headNode);
		
		// Back cranium - wider, houses the brain.
		headNode.attachChild(makeBox("Cranium", 0.5f, 0.35f, 0.35f, headMat, 0, 0.25f, 0.1f));
		
		// Forward snout - narrower, elongated.
		headNode.attachChild(makeBox("Snout", 0.35f, 0.28f, 0.4f, headMat, 0, 0.18f, -0.55f));
		
		// Eyes on the FRONT of the cranium - facing forward, slightly to each side.
		// Positioned where cranium meets snout so they're visible head-on.
		headNode.attachChild(makeBox("LeftEye", 0.08f, 0.07f, 0.04f, eyeMat, -0.28f, 0.38f, -0.25f));
		headNode.attachChild(makeBox("RightEye", 0.08f, 0.07f, 0.04f, eyeMat, 0.28f, 0.38f, -0.25f));
		
		// Nostrils on snout tip.
		headNode.attachChild(makeBox("LeftNostril", 0.04f, 0.03f, 0.02f, backMat, -0.12f, 0.12f, -0.97f));
		headNode.attachChild(makeBox("RightNostril", 0.04f, 0.03f, 0.02f, backMat, 0.12f, 0.12f, -0.97f));
		
		// Two large white fangs - protruding DOWN and FORWARD past the snout.
		// Visible from the front, hanging below the jaw line.
		headNode.attachChild(makeBox("LeftFang", 0.035f, 0.12f, 0.035f, toothMat, -0.18f, -0.08f, -0.75f));
		headNode.attachChild(makeBox("RightFang", 0.035f, 0.12f, 0.035f, toothMat, 0.18f, -0.08f, -0.75f));
		
		// Brow ridge - armored ridge above the eyes.
		headNode.attachChild(makeBox("BrowRidge", 0.45f, 0.07f, 0.12f, backMat, 0, 0.5f, -0.2f));
		
		// ---- Horns (two curved-up horns on top of head) ----
		// Each horn is 2 segments: base angled back, tip angled further back and up.
		// Left horn base.
		final Node leftHornBase = new Node("LeftHornBase");
		leftHornBase.setLocalTranslation(-0.25f, 0.55f, 0.1f);
		TEMP_QUAT.fromAngleAxis(FastMath.DEG_TO_RAD * -25f, Vector3f.UNIT_X); // Tilt backward.
		leftHornBase.setLocalRotation(TEMP_QUAT);
		leftHornBase.attachChild(makeBox("LHornBase", 0.06f, 0.2f, 0.06f, hornMat, 0, 0.2f, 0));
		headNode.attachChild(leftHornBase);
		
		// Left horn tip.
		final Node leftHornTip = new Node("LeftHornTip");
		leftHornTip.setLocalTranslation(0, 0.4f, 0);
		TEMP_QUAT.fromAngleAxis(FastMath.DEG_TO_RAD * -15f, Vector3f.UNIT_X);
		leftHornTip.setLocalRotation(TEMP_QUAT);
		leftHornTip.attachChild(makeBox("LHornTip", 0.04f, 0.15f, 0.04f, hornMat, 0, 0.15f, 0));
		leftHornBase.attachChild(leftHornTip);
		
		// Right horn base.
		final Node rightHornBase = new Node("RightHornBase");
		rightHornBase.setLocalTranslation(0.25f, 0.55f, 0.1f);
		TEMP_QUAT.fromAngleAxis(FastMath.DEG_TO_RAD * -25f, Vector3f.UNIT_X);
		rightHornBase.setLocalRotation(TEMP_QUAT);
		rightHornBase.attachChild(makeBox("RHornBase", 0.06f, 0.2f, 0.06f, hornMat, 0, 0.2f, 0));
		headNode.attachChild(rightHornBase);
		
		// Right horn tip.
		final Node rightHornTip = new Node("RightHornTip");
		rightHornTip.setLocalTranslation(0, 0.4f, 0);
		TEMP_QUAT.fromAngleAxis(FastMath.DEG_TO_RAD * -15f, Vector3f.UNIT_X);
		rightHornTip.setLocalRotation(TEMP_QUAT);
		rightHornTip.attachChild(makeBox("RHornTip", 0.04f, 0.15f, 0.04f, hornMat, 0, 0.15f, 0));
		rightHornBase.attachChild(rightHornTip);
		
		// ---- Lower Jaw (separate node for bite animation) ----
		// Narrower jaw matching the snout, hinged at the back.
		final Node jawNode = new Node("Jaw");
		jawNode.setLocalTranslation(0, -0.1f, -0.2f);
		jawNode.attachChild(makeBox("JawBox", 0.3f, 0.08f, 0.4f, jawMat, 0, -0.08f, -0.2f));
		headNode.attachChild(jawNode);
		enemy.setJaw(jawNode);
		
		// Small lower teeth on the jaw - visible when mouth opens.
		jawNode.attachChild(makeBox("JawTeethL", 0.025f, 0.035f, 0.025f, toothMat, -0.15f, 0.02f, -0.5f));
		jawNode.attachChild(makeBox("JawTeethR", 0.025f, 0.035f, 0.025f, toothMat, 0.15f, 0.02f, -0.5f));
		
		// ---- Front Legs ----
		final Node frontLeft = makePivotPart("LeftLeg", makeBox("FrontLeftLegBox", 0.2f, 0.5f, 0.2f, bodyMat, 0, -0.5f, 0), -0.75f, 1.0f, -1.0f);
		root.attachChild(frontLeft);
		enemy.setLeftLeg(frontLeft);
		
		final Node frontRight = makePivotPart("RightLeg", makeBox("FrontRightLegBox", 0.2f, 0.5f, 0.2f, bodyMat, 0, -0.5f, 0), 0.75f, 1.0f, -1.0f);
		root.attachChild(frontRight);
		enemy.setRightLeg(frontRight);
		
		// ---- Back Legs ----
		final Node backLeft = makePivotPart("LeftArm", makeBox("BackLeftLegBox", 0.2f, 0.5f, 0.2f, bodyMat, 0, -0.5f, 0), -0.75f, 1.0f, 1.0f);
		root.attachChild(backLeft);
		enemy.setLeftArm(backLeft);
		
		final Node backRight = makePivotPart("RightArm", makeBox("BackRightLegBox", 0.2f, 0.5f, 0.2f, bodyMat, 0, -0.5f, 0), 0.75f, 1.0f, 1.0f);
		root.attachChild(backRight);
		enemy.setRightArm(backRight);
		
		// ---- Wings ----
		// Each wing: bone spar + membrane panel. Attached to upper body sides.
		// Wings are folded against the body at rest; EnemyAnimator can unfold them.
		// Left wing.
		final Node leftWing = new Node("LeftWing");
		leftWing.setLocalTranslation(-0.9f, 1.6f, 0); // Upper-left of body.
		TEMP_QUAT.fromAngleAxis(FastMath.DEG_TO_RAD * 15f, Vector3f.UNIT_Z); // Slightly raised.
		leftWing.setLocalRotation(TEMP_QUAT);
		
		// Wing bone (spar).
		leftWing.attachChild(makeBox("LWingBone", 0.04f, 0.04f, 0.8f, wingMat, -0.8f, 0, 0));
		
		// Wing membrane - large flat panel.
		final Geometry lMembrane = makeBox("LWingMembrane", 0.8f, 0.01f, 0.7f, membraneMat, -0.8f, -0.05f, 0);
		lMembrane.setQueueBucket(Bucket.Transparent);
		leftWing.attachChild(lMembrane);
		
		// Wing tip bone.
		leftWing.attachChild(makeBox("LWingTip", 0.03f, 0.03f, 0.5f, wingMat, -1.5f, 0.02f, 0.2f));
		
		bodyNode.attachChild(leftWing);
		
		// Right wing (mirrored).
		final Node rightWing = new Node("RightWing");
		rightWing.setLocalTranslation(0.9f, 1.6f, 0);
		TEMP_QUAT.fromAngleAxis(FastMath.DEG_TO_RAD * -15f, Vector3f.UNIT_Z);
		rightWing.setLocalRotation(TEMP_QUAT);
		
		rightWing.attachChild(makeBox("RWingBone", 0.04f, 0.04f, 0.8f, wingMat, 0.8f, 0, 0));
		
		final Geometry rMembrane = makeBox("RWingMembrane", 0.8f, 0.01f, 0.7f, membraneMat, 0.8f, -0.05f, 0);
		rMembrane.setQueueBucket(Bucket.Transparent);
		rightWing.attachChild(rMembrane);
		
		rightWing.attachChild(makeBox("RWingTip", 0.03f, 0.03f, 0.5f, wingMat, 1.5f, 0.02f, 0.2f));
		
		bodyNode.attachChild(rightWing);
		
		// ---- Tail (3 chained segments) ----
		final Node tail1 = new Node("Tail1");
		tail1.setLocalTranslation(0, 1.2f, 1.5f);
		tail1.attachChild(makeBox("Tail1Box", 0.25f, 0.2f, 0.6f, bodyMat, 0, 0, 0.6f));
		root.attachChild(tail1);
		enemy.setTail1(tail1);
		
		final Node tail2 = new Node("Tail2");
		tail2.setLocalTranslation(0, -0.05f, 1.2f);
		tail2.attachChild(makeBox("Tail2Box", 0.175f, 0.15f, 0.5f, bodyMat, 0, 0, 0.5f));
		tail1.attachChild(tail2);
		enemy.setTail2(tail2);
		
		final Node tail3 = new Node("Tail3");
		tail3.setLocalTranslation(0, -0.05f, 1.0f);
		tail3.attachChild(makeBox("Tail3Box", 0.1f, 0.1f, 0.4f, backMat, 0, 0, 0.4f));
		tail2.attachChild(tail3);
		enemy.setTail3(tail3);
	}
	
	/** Shared temp quaternion for dragon horn/wing rotation setup. */
	private static final Quaternion TEMP_QUAT = new Quaternion();
}
