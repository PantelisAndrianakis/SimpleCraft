package simplecraft.world;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import com.jme3.asset.AssetManager;
import com.jme3.asset.TextureKey;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.material.Material;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;

/**
 * Builds a texture atlas by stitching individual 32×32 block PNGs into an 8×8 grid.<br>
 * Missing textures are filled with magenta as a visible indicator.<br>
 * Provides a shared {@link Material} for all chunk geometries.
 * @author Pantelis Andrianakis
 * @since February 22nd 2026
 */
public class TextureAtlas
{
	// ========================================================
	// Constants
	// ========================================================
	
	/** Pixels per individual block texture. */
	public static final int TILE_SIZE = 32;
	
	/** Number of tiles per row/column in the atlas grid. */
	public static final int GRID_SIZE = 8;
	
	/** Total atlas dimensions in pixels (TILE_SIZE × GRID_SIZE). */
	public static final int ATLAS_SIZE = TILE_SIZE * GRID_SIZE; // 256×256
	
	/** Relative path to block texture PNGs within the assets directory. */
	private static final String TEXTURE_PATH = "images/blocks/";
	
	/**
	 * Ordered list of texture filenames matching atlas indices 0–27.<br>
	 * Empty strings represent reserved/unused slots.
	 */
	// @formatter:off
	private static final String[] TEXTURE_FILES =
	{
		"grass_top.png",             // 0
		"grass_side.png",            // 1
		"dirt.png",                  // 2
		"stone.png",                 // 3
		"sand.png",                  // 4
		"wood_side.png",             // 5
		"wood_top.png",              // 6
		"leaves.png",                // 7
		"water.png",                 // 8
		"iron_ore.png",              // 9
		"bedrock.png",               // 10
		"berry_bush.png",            // 11
		"campfire.png",              // 12
		"",                          // 13 (reserved)
		"chest_front.png",           // 14
		"chest_side.png",            // 15
		"chest_top.png",             // 16
		"crafting_table_top.png",    // 17
		"crafting_table_side.png",   // 18
		"crafting_table_bottom.png", // 19
		"furnace_front.png",         // 20
		"furnace_side.png",          // 21
		"furnace_top.png",           // 22
		"torch.png",                 // 23
		"red_poppy.png",             // 24
		"dandelion.png",             // 25
		"blue_orchid.png",           // 26
		"white_daisy.png"            // 27
	};
	// @formatter:on
	
	// ========================================================
	// Fields
	// ========================================================
	
	private BufferedImage _atlasImage;
	private Material _sharedMaterial;
	
	// ========================================================
	// Atlas Building
	// ========================================================
	
	/**
	 * Builds the texture atlas by loading individual PNGs and stitching them into an 8×8 grid.<br>
	 * Missing textures are filled with magenta (255, 0, 255) as a visible indicator.<br>
	 * Must be called before {@link #createMaterial(AssetManager)}.
	 * @param assetManager the jME3 AssetManager (used for texture conversion)
	 */
	public void buildAtlas(AssetManager assetManager)
	{
		// Create the atlas image filled with magenta (missing texture indicator).
		_atlasImage = new BufferedImage(ATLAS_SIZE, ATLAS_SIZE, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = _atlasImage.createGraphics();
		g.setColor(new Color(255, 0, 255, 255));
		g.fillRect(0, 0, ATLAS_SIZE, ATLAS_SIZE);
		g.dispose();
		
		// Stitch each texture into its atlas slot.
		int loadedCount = 0;
		for (int i = 0; i < TEXTURE_FILES.length; i++)
		{
			final String filename = TEXTURE_FILES[i];
			if (filename.isEmpty())
			{
				continue; // Reserved slot.
			}
			
			final int px = (i % GRID_SIZE) * TILE_SIZE;
			final int py = (i / GRID_SIZE) * TILE_SIZE;
			
			// Load directly from the file system relative to project root.
			final File file = new File("assets/" + TEXTURE_PATH + filename);
			if (file.exists())
			{
				try
				{
					final BufferedImage tile = ImageIO.read(file);
					final Graphics2D tileGraphics = _atlasImage.createGraphics();
					tileGraphics.drawImage(tile, px, py, TILE_SIZE, TILE_SIZE, null);
					tileGraphics.dispose();
					loadedCount++;
				}
				catch (IOException e)
				{
					System.out.println("WARNING: Failed to read block texture: " + filename + " — " + e.getMessage());
				}
			}
			else
			{
				System.out.println("WARNING: Missing block texture: " + filename);
			}
		}
		
		System.out.println("TextureAtlas built: " + ATLAS_SIZE + "x" + ATLAS_SIZE + " (" + loadedCount + "/" + TEXTURE_FILES.length + " textures loaded)");
	}
	
	// ========================================================
	// Material Creation
	// ========================================================
	
	/**
	 * Creates a shared {@link Material} using the built atlas texture.<br>
	 * Uses Lighting.j3md with Nearest filtering for pixel-crispy rendering.<br>
	 * Call {@link #buildAtlas(AssetManager)} first.
	 * @param assetManager the jME3 AssetManager
	 * @return a Material with the atlas as its DiffuseMap
	 */
	public Material createMaterial(AssetManager assetManager)
	{
		// Save atlas to a temp PNG, then load it back through jME3's standard texture pipeline.
		// This avoids all AWTLoader / ByteBuffer conversion issues.
		try
		{
			final File tempAtlas = File.createTempFile("simplecraft_atlas", ".png");
			tempAtlas.deleteOnExit();
			ImageIO.write(_atlasImage, "PNG", tempAtlas);
			
			// Register the temp directory as a locator so jME3 can find the file.
			assetManager.registerLocator(tempAtlas.getParent(), FileLocator.class);
			
			// Load via jME3's proven PNG loader.
			final TextureKey key = new TextureKey(tempAtlas.getName(), false);
			key.setGenerateMips(false);
			final Texture2D texture = (Texture2D) assetManager.loadTexture(key);
			texture.setMagFilter(Texture.MagFilter.Nearest);
			texture.setMinFilter(Texture.MinFilter.NearestNoMipMaps);
			texture.setWrap(Texture.WrapMode.EdgeClamp);
			
			// Build material with the atlas texture.
			_sharedMaterial = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
			_sharedMaterial.setTexture("DiffuseMap", texture);
			
			System.out.println("TextureAtlas: Material created from " + tempAtlas.getAbsolutePath());
		}
		catch (IOException e)
		{
			System.out.println("ERROR: Failed to create atlas material: " + e.getMessage());
		}
		
		return _sharedMaterial;
	}
	
	/**
	 * Returns the shared material created by {@link #createMaterial(AssetManager)}.<br>
	 * Returns null if not yet created.
	 */
	public Material getSharedMaterial()
	{
		return _sharedMaterial;
	}
	
	// ========================================================
	// Debug Helper
	// ========================================================
	
	/**
	 * Saves the atlas image to disk for visual inspection.<br>
	 * Useful for verifying texture placement and diagnosing atlas issues.
	 * @param path file path to write (e.g. "debug_atlas.png")
	 */
	public void saveDebugAtlas(String path)
	{
		if (_atlasImage == null)
		{
			System.out.println("WARNING: Cannot save debug atlas — atlas not built yet.");
			return;
		}
		
		try
		{
			ImageIO.write(_atlasImage, "PNG", new File(path));
			System.out.println("Debug atlas saved to: " + path);
		}
		catch (IOException e)
		{
			System.out.println("WARNING: Failed to save debug atlas: " + e.getMessage());
		}
	}
}
