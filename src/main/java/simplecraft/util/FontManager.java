package simplecraft.util;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapCharacter;
import com.jme3.font.BitmapCharacterSet;
import com.jme3.font.BitmapFont;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;

/**
 * Generates crisp BitmapFont objects from TTF/OTF font files or system fonts at exact pixel sizes.<br>
 * Uses Java2D to render glyphs into a texture atlas, eliminating blurriness caused by scaling up low-resolution bitmap fonts.<br>
 * <br>
 * Supports both TrueType (.ttf) and OpenType (.otf) font files loaded via the jME3 AssetManager.<br>
 * Fonts are cached by asset path (or system name), style, and size combination.
 * @author Pantelis Andrianakis
 * @since February 17th 2026
 */
public class FontManager
{
	private static final int CHARS_PER_ROW = 16;
	private static final int START_CHAR = 32; // Space.
	private static final int END_CHAR = 127; // DEL (exclusive).
	private static final int PADDING = 2;
	
	private static final HashMap<String, BitmapFont> _cache = new HashMap<>();
	
	/**
	 * Get (or generate) a BitmapFont from a TTF or OTF font file in the asset path.<br>
	 * The font is rendered at exactly the requested pixel size for maximum clarity.
	 * @param assetManager The jME3 AssetManager (used for material creation and file loading)
	 * @param assetPath Path to the .ttf or .otf file relative to assets (e.g., "fonts/myfont.otf")
	 * @param style AWT font style constant (e.g., Font.PLAIN, Font.BOLD)
	 * @param size Pixel size to render the font at
	 * @return A crisp BitmapFont ready for use with Lemur Labels or BitmapText
	 */
	public static BitmapFont getFont(AssetManager assetManager, String assetPath, int style, int size)
	{
		final String key = assetPath + "_" + style + "_" + size;
		
		if (_cache.containsKey(key))
		{
			return _cache.get(key);
		}
		
		Font awtFont = null;
		
		try
		{
			// Load the font file via jME3's AssetManager (supports registered locators).
			final InputStream stream = assetManager.locateAsset(new com.jme3.asset.AssetKey<>(assetPath)).openStream();
			
			// Font.TRUETYPE_FONT handles both TTF and OTF formats.
			awtFont = Font.createFont(Font.TRUETYPE_FONT, stream);
			awtFont = awtFont.deriveFont(style, (float) size);
			stream.close();
			
			System.out.println("FontManager: Loaded font from asset '" + assetPath + "' style=" + style + " size=" + size + "px.");
		}
		catch (Exception e)
		{
			System.err.println("WARNING: Could not load font asset '" + assetPath + "': " + e.getMessage());
			System.err.println("WARNING: Falling back to system SansSerif font.");
			awtFont = new Font("SansSerif", style, size);
		}
		
		final BitmapFont font = generateFont(assetManager, awtFont, size);
		_cache.put(key, font);
		
		return font;
	}
	
	/**
	 * Get (or generate) a BitmapFont from a system font.<br>
	 * Use this when no custom font file is available.
	 * @param assetManager The jME3 AssetManager (needed for material creation)
	 * @param fontName System font name (e.g., "SansSerif", "Serif", "Monospaced")
	 * @param style AWT font style constant (e.g., Font.PLAIN, Font.BOLD)
	 * @param size Pixel size to render the font at
	 * @return A crisp BitmapFont ready for use with Lemur Labels or BitmapText
	 */
	public static BitmapFont getSystemFont(AssetManager assetManager, String fontName, int style, int size)
	{
		final String key = "system:" + fontName + "_" + style + "_" + size;
		
		if (_cache.containsKey(key))
		{
			return _cache.get(key);
		}
		
		final Font awtFont = new Font(fontName, style, size);
		final BitmapFont font = generateFont(assetManager, awtFont, size);
		_cache.put(key, font);
		
		System.out.println("FontManager: Generated system font '" + fontName + "' style=" + style + " size=" + size + "px.");
		return font;
	}
	
	/**
	 * Clear all cached fonts. Call when the application shuts down or fonts are no longer needed.
	 */
	public static void clearCache()
	{
		_cache.clear();
	}
	
	// --- Internal generation ---
	
	private static BitmapFont generateFont(AssetManager assetManager, Font awtFont, int size)
	{
		final int numChars = END_CHAR - START_CHAR;
		
		// First pass: measure all characters using a temporary Graphics2D.
		final BufferedImage tempImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D tempGraphics = tempImage.createGraphics();
		tempGraphics.setFont(awtFont);
		
		final FontMetrics metrics = tempGraphics.getFontMetrics();
		final int charHeight = metrics.getHeight();
		final int ascent = metrics.getAscent();
		
		final int[] charWidths = new int[numChars];
		int maxCharWidth = 0;
		
		for (int i = 0; i < numChars; i++)
		{
			charWidths[i] = metrics.charWidth((char) (START_CHAR + i));
			
			if (charWidths[i] > maxCharWidth)
			{
				maxCharWidth = charWidths[i];
			}
		}
		
		tempGraphics.dispose();
		
		// Calculate atlas dimensions (power-of-two for GPU compatibility).
		final int cellWidth = maxCharWidth + PADDING * 2;
		final int cellHeight = charHeight + PADDING * 2;
		final int rows = (numChars + CHARS_PER_ROW - 1) / CHARS_PER_ROW;
		
		final int atlasWidth = nextPowerOfTwo(CHARS_PER_ROW * cellWidth);
		final int atlasHeight = nextPowerOfTwo(rows * cellHeight);
		
		// Second pass: render all glyphs into the atlas.
		final BufferedImage atlas = new BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D graphics = atlas.createGraphics();
		
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		graphics.setFont(awtFont);
		graphics.setColor(Color.WHITE);
		
		// Build the BitmapCharacterSet alongside rendering.
		final BitmapCharacterSet charSet = new BitmapCharacterSet();
		charSet.setRenderedSize(size);
		charSet.setLineHeight(charHeight);
		charSet.setBase(ascent);
		charSet.setWidth(atlasWidth);
		charSet.setHeight(atlasHeight);
		
		for (int i = 0; i < numChars; i++)
		{
			final int col = i % CHARS_PER_ROW;
			final int row = i / CHARS_PER_ROW;
			
			final int x = col * cellWidth + PADDING;
			final int y = row * cellHeight + PADDING;
			
			// Draw the glyph. drawString baseline is at y + ascent.
			graphics.drawString(String.valueOf((char) (START_CHAR + i)), x, y + ascent);
			
			// Register the character in the set.
			final BitmapCharacter bc = new BitmapCharacter();
			bc.setX(x);
			bc.setY(y);
			bc.setWidth(charWidths[i]);
			bc.setHeight(charHeight);
			bc.setXOffset(0);
			bc.setYOffset(0);
			bc.setXAdvance(charWidths[i]);
			bc.setPage(0);
			
			charSet.addCharacter(START_CHAR + i, bc);
		}
		
		graphics.dispose();
		
		// Convert the BufferedImage to a jME3 Texture2D.
		final Texture2D texture = createTexture(atlas, atlasWidth, atlasHeight);
		
		// Build the font material.
		final Material fontMaterial = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
		fontMaterial.setTexture("ColorMap", texture);
		fontMaterial.setBoolean("VertexColor", true);
		fontMaterial.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
		
		// Assemble the BitmapFont.
		final BitmapFont bitmapFont = new BitmapFont();
		bitmapFont.setCharSet(charSet);
		bitmapFont.setPages(new Material[]
		{
			fontMaterial
		});
		
		return bitmapFont;
	}
	
	/**
	 * Convert a BufferedImage to a jME3 Texture2D (RGBA8, vertically flipped for OpenGL).
	 */
	private static Texture2D createTexture(BufferedImage image, int width, int height)
	{
		final ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
		
		// OpenGL expects the image bottom-to-top, so flip vertically.
		for (int y = height - 1; y >= 0; y--)
		{
			for (int x = 0; x < width; x++)
			{
				final int argb = image.getRGB(x, y);
				final byte a = (byte) ((argb >> 24) & 0xFF);
				final byte r = (byte) ((argb >> 16) & 0xFF);
				final byte g = (byte) ((argb >> 8) & 0xFF);
				final byte b = (byte) (argb & 0xFF);
				
				buffer.put(r).put(g).put(b).put(a);
			}
		}
		
		buffer.flip();
		
		final Image jmeImage = new Image(Image.Format.RGBA8, width, height, buffer, ColorSpace.sRGB);
		final Texture2D texture = new Texture2D(jmeImage);
		texture.setMagFilter(com.jme3.texture.Texture.MagFilter.Bilinear);
		texture.setMinFilter(com.jme3.texture.Texture.MinFilter.BilinearNoMipMaps);
		
		return texture;
	}
	
	/**
	 * Round up to the next power of two.
	 */
	private static int nextPowerOfTwo(int value)
	{
		int result = 1;
		
		while (result < value)
		{
			result <<= 1;
		}
		
		return result;
	}
}
