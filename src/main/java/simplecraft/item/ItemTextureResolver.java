package simplecraft.item;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.jme3.asset.AssetManager;
import com.jme3.asset.TextureKey;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;

import simplecraft.player.ViewmodelRenderer;
import simplecraft.world.Block;
import simplecraft.world.Block.Face;

/**
 * Resolves the best available texture for an item by checking multiple filesystem paths<br>
 * in priority order. Uses the same {@link FileLocator} approach as {@link ViewmodelRenderer}<br>
 * to load textures from the {@code assets/images/} directory tree.<br>
 * <br>
 * Used by {@link DroppedItem} for world drop visuals, by the inventory screen for slot icons,<br>
 * and by the hotbar HUD for equipped item icons.<br>
 * <br>
 * <b>Lookup order:</b>
 * <ol>
 * <li>{@code assets/images/drops/<item_id>.png} - dedicated drop/icon override</li>
 * <li>{@code assets/images/items/<item_id>.png} - general item sprite (weapons, tools, consumables)</li>
 * <li>{@code assets/images/blocks/<block_texture>.png} - block's own texture (for BLOCK type items)</li>
 * </ol>
 * If none exist, the caller falls back to colored quads/cubes as a final fallback.<br>
 * <br>
 * Textures are cached per item ID so each asset is loaded at most once.
 * @author Pantelis Andrianakis
 * @since March 15th 2026
 */
public class ItemTextureResolver
{
	/** First priority: dedicated drop/icon textures. */
	private static final String PATH_DROPS = "assets/images/drops/";
	
	/** Second priority: general item sprites. */
	private static final String PATH_ITEMS = "assets/images/items/";
	
	/** Third priority: block textures (used for BLOCK type items). */
	private static final String PATH_BLOCKS = "assets/images/blocks/";
	
	/** Cache of resolved textures keyed by item ID. */
	private static final Map<String, Texture> _cache = new HashMap<>();
	
	/** Set of item IDs that have been checked and confirmed to have no texture. */
	private static final Set<String> _missingCache = new HashSet<>();
	
	/**
	 * Resolves the best available texture for the given item template.<br>
	 * Checks {@code assets/images/drops/}, then {@code assets/images/items/}, then<br>
	 * {@code assets/images/blocks/} (for BLOCK type items using the block's front/side texture).<br>
	 * Returns null if no texture exists at any path.
	 * @param assetManager the jME3 asset manager
	 * @param template the item template
	 * @return the loaded Texture, or null if none found
	 */
	public static Texture resolve(AssetManager assetManager, ItemTemplate template)
	{
		if (template == null)
		{
			return null;
		}
		
		final String itemId = template.getId();
		
		// Check positive cache first.
		if (_cache.containsKey(itemId))
		{
			return _cache.get(itemId);
		}
		
		// Check negative cache (already confirmed missing).
		if (_missingCache.contains(itemId))
		{
			return null;
		}
		
		// Priority 1: assets/images/drops/<item_id>.png
		Texture texture = tryLoadFromFile(assetManager, PATH_DROPS, itemId + ".png");
		
		// Priority 2: assets/images/items/<item_id>.png
		if (texture == null)
		{
			texture = tryLoadFromFile(assetManager, PATH_ITEMS, itemId + ".png");
		}
		
		// Priority 3: assets/images/blocks/<block_texture>.png (for BLOCK type items only).
		if (texture == null && template.getType() == ItemType.BLOCK)
		{
			final Block block = template.getPlacesBlock();
			if (block != null)
			{
				// Use the front/side texture as the representative icon.
				// Face.NORTH returns frontTexture if available, otherwise sideTexture.
				final String blockTextureFile = block.getTextureFile(Face.NORTH);
				if (blockTextureFile != null)
				{
					texture = tryLoadFromFile(assetManager, PATH_BLOCKS, blockTextureFile);
				}
			}
		}
		
		// Cache the result (positive or negative).
		if (texture != null)
		{
			_cache.put(itemId, texture);
		}
		else
		{
			_missingCache.add(itemId);
		}
		
		return texture;
	}
	
	/**
	 * Convenience overload that resolves by item ID.<br>
	 * Looks up the ItemTemplate from the registry, then delegates to {@link #resolve(AssetManager, ItemTemplate)}.
	 * @param assetManager the jME3 asset manager
	 * @param itemId the item ID (e.g. "wood_sword", "cooked_meat", "dirt")
	 * @return the loaded Texture, or null if none found
	 */
	public static Texture resolve(AssetManager assetManager, String itemId)
	{
		return resolve(assetManager, ItemRegistry.get(itemId));
	}
	
	/**
	 * Attempts to load a texture from the filesystem using {@link FileLocator}.<br>
	 * Checks {@link File#exists()} first, then registers the directory and loads<br>
	 * with a {@link TextureKey}. Uses nearest-neighbor filtering for pixel art.<br>
	 * This matches the proven loading approach used by {@link ViewmodelRenderer}.
	 * @param assetManager the jME3 asset manager
	 * @param directory the filesystem directory (e.g. "assets/images/items/")
	 * @param filename the texture filename (e.g. "stone_pickaxe.png")
	 * @return the loaded Texture, or null if the file does not exist
	 */
	private static Texture tryLoadFromFile(AssetManager assetManager, String directory, String filename)
	{
		final File file = new File(directory + filename);
		if (!file.exists())
		{
			return null;
		}
		
		try
		{
			assetManager.registerLocator(file.getParent(), FileLocator.class);
			final TextureKey key = new TextureKey(filename, false);
			key.setGenerateMips(false);
			final Texture2D tex = (Texture2D) assetManager.loadTexture(key);
			tex.setMagFilter(Texture.MagFilter.Nearest);
			tex.setMinFilter(Texture.MinFilter.NearestNoMipMaps);
			return tex;
		}
		catch (Exception e)
		{
			System.out.println("ItemTextureResolver: Failed to load '" + directory + filename + "': " + e.getMessage());
			return null;
		}
	}
	
	/**
	 * Clears all cached textures. Call during world teardown to free GPU resources.
	 */
	public static void clearCache()
	{
		_cache.clear();
		_missingCache.clear();
	}
}
