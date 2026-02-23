package simplecraft.state;

import java.awt.Font;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import com.jme3.app.Application;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.jme3.ui.Picture;

import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.HAlignment;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.Panel;
import com.simsilica.lemur.TextField;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.component.SpringGridLayout;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;

import simplecraft.SimpleCraft;
import simplecraft.audio.AudioManager;
import simplecraft.input.MenuNavigationManager;
import simplecraft.state.GameStateManager.GameState;
import simplecraft.ui.ButtonManager;
import simplecraft.ui.FontManager;
import simplecraft.ui.MessageManager;
import simplecraft.ui.QuestionManager;
import simplecraft.world.WorldInfo;

/**
 * World selection screen where players create, select, and delete worlds.<br>
 * Displays a paginated list of existing worlds (3 per page) sorted by last played date.<br>
 * Uses {@link FontManager#SYMBOL_ARROW_LEFT} and {@link FontManager#SYMBOL_ARROW_RIGHT}<br>
 * for page navigation when more than 3 worlds exist.<br>
 * Provides a "Create New World" dialog with name and seed fields.<br>
 * Supports keyboard navigation via WASD and arrow keys.<br>
 * Navigation is locked while the create dialog is open to prevent WASD<br>
 * keystrokes from triggering menu movement during text entry.
 * @author Pantelis Andrianakis
 * @since February 23rd 2026
 */
public class WorldSelectState extends FadeableAppState
{
	private static final String BACKGROUND_PATH = "assets/images/backgrounds/main_menu.png";
	private static final String TITLE_LOGO_PATH = "assets/images/app_icons/icon_128.png";
	
	private static final int LOGO_SIZE = 128;
	private static final int LOGO_TITLE_SPACING = 5;
	private static final int DIALOG_FIELD_SPACING = 10;
	private static final int WORLDS_PER_PAGE = 5;
	
	// All vertical spacing as screen-height percentages.
	private static final float BOTTOM_BUTTON_SPACING_PERCENT = 0.015f;
	private static final float ENTRY_SPACING_PERCENT = 0.004f;
	private static final float ENTRY_PAD_TOP_PERCENT = 0.006f;
	private static final float ENTRY_PAD_BOTTOM_PERCENT = 0.014f;
	private static final float INFO_BOTTOM_PERCENT = 0.004f;
	
	private static final float LIST_WIDTH_PERCENT = 0.30f;
	private static final float BOTTOM_BUTTON_WIDTH_PERCENT = 0.18f;
	private static final float BOTTOM_BUTTON_HEIGHT_PERCENT = 0.055f;
	private static final float PLAY_BUTTON_WIDTH_PERCENT = 0.12f;
	private static final float ENTRY_BUTTON_HEIGHT_PERCENT = 0.040f;
	
	private static final ColorRGBA ENTRY_BG_COLOR = new ColorRGBA(0.12f, 0.12f, 0.12f, 0.75f);
	private static final ColorRGBA FIELD_BG_COLOR = new ColorRGBA(0.25f, 0.25f, 0.25f, 0.95f);
	private static final ColorRGBA FIELD_TEXT_COLOR = ColorRGBA.White;
	private static final ColorRGBA INFO_TEXT_COLOR = new ColorRGBA(0.65f, 0.65f, 0.65f, 0.95f);
	private static final ColorRGBA DIALOG_BG_COLOR = new ColorRGBA(0.10f, 0.10f, 0.10f, 0.95f);
	private static final ColorRGBA PAGE_ARROW_COLOR = new ColorRGBA(0.9f, 0.9f, 0.9f, 0.9f);
	private static final ColorRGBA PAGE_ARROW_DISABLED = new ColorRGBA(0.35f, 0.35f, 0.35f, 0.5f);
	private static final ColorRGBA PAGE_TEXT_COLOR = new ColorRGBA(0.7f, 0.7f, 0.7f, 0.9f);
	private static final ColorRGBA DELETE_SYMBOL_COLOR = new ColorRGBA(0.55f, 0.55f, 0.55f, 0.9f);
	private static final ColorRGBA DELETE_SYMBOL_HOVER = new ColorRGBA(0.9f, 0.2f, 0.2f, 1f);
	
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");
	
	private Picture _background;
	private Picture _logo;
	private Label _titleLabel;
	private Container _listContainer;
	private Container _pageBar;
	private Container _bottomBar;
	private Label _emptyLabel;
	
	// Pagination.
	private int _currentPage;
	private Label _pageLeftArrow;
	private Label _pageLabel;
	private Label _pageRightArrow;
	
	// Create dialog.
	private Container _dialogOverlay;
	private Container _dialogContainer;
	private TextField _nameField;
	private TextField _seedField;
	private boolean _dialogOpen;
	
	// Keyboard navigation.
	private MenuNavigationManager _navigation;
	
	// Loaded worlds.
	private List<WorldInfo> _worlds;
	
	public WorldSelectState()
	{
		setFadeIn(1.0f, new ColorRGBA(0, 0, 0, 1));
		setFadeOut(1.0f, new ColorRGBA(0, 0, 0, 1));
	}
	
	@Override
	protected void initialize(Application app)
	{
		// Initialization happens once when state is first attached.
	}
	
	@Override
	protected void cleanup(Application app)
	{
		// Cleanup happens once when state is permanently detached.
	}
	
	@Override
	protected void onEnterState()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		
		System.out.println("WorldSelectState entered.");
		
		_dialogOpen = false;
		_currentPage = 0;
		
		// Screen dimensions.
		final float screenWidth = app.getCamera().getWidth();
		final float screenHeight = app.getCamera().getHeight();
		final float screenCenterX = screenWidth / 2f;
		
		// --- Background Image ---
		_background = new Picture("WorldSelect Background");
		_background.setImage(app.getAssetManager(), BACKGROUND_PATH, true);
		_background.setWidth(screenWidth);
		_background.setHeight(screenHeight);
		_background.setLocalTranslation(0, 0, -10);
		_background.setCullHint(Spatial.CullHint.Never);
		
		// --- Title Label ---
		_titleLabel = new Label("Select World");
		_titleLabel.setFont(FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_LINOCUT_PATH, Font.PLAIN, 52));
		_titleLabel.setFontSize(52);
		_titleLabel.setColor(ColorRGBA.White);
		
		final float titleWidth = _titleLabel.getPreferredSize().x;
		final float titleHeight = _titleLabel.getPreferredSize().y;
		
		// --- Logo ---
		_logo = new Picture("WorldSelect Logo");
		_logo.setImage(app.getAssetManager(), TITLE_LOGO_PATH, true);
		_logo.setWidth(LOGO_SIZE);
		_logo.setHeight(LOGO_SIZE);
		_logo.setCullHint(Spatial.CullHint.Never);
		
		// Calculate total title group width (title + spacing + logo) for centering.
		final float titleGroupWidth = titleWidth + LOGO_TITLE_SPACING + LOGO_SIZE;
		
		// Title group: positioned in upper portion of screen.
		final float titleGroupX = screenCenterX - (titleGroupWidth / 2.3f);
		final float titleY = screenHeight * 0.90f;
		_titleLabel.setLocalTranslation(titleGroupX, titleY, 1);
		
		// Logo: to the right of the title, vertically centered with title.
		final float logoX = titleGroupX + titleWidth + LOGO_TITLE_SPACING;
		final float logoY = titleY - (titleHeight * 0.41f) - (LOGO_SIZE / 2f);
		_logo.setLocalTranslation(logoX, logoY, 1);
		
		// --- Navigation ---
		_navigation = new MenuNavigationManager();
		
		// --- World List Container ---
		_listContainer = new Container();
		_listContainer.setBackground(null);
		
		// Load and display worlds.
		refreshWorldList();
		
		// --- Bottom Buttons ---
		buildBottomBar();
		
		// Attach all elements to GUI (background first so it's behind everything).
		app.getGuiNode().attachChild(_background);
		app.getGuiNode().attachChild(_titleLabel);
		app.getGuiNode().attachChild(_logo);
		app.getGuiNode().attachChild(_listContainer);
		app.getGuiNode().attachChild(_bottomBar);
		
		// Register navigation (Escape goes back to main menu).
		final Runnable backAction = () ->
		{
			app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
			app.getGameStateManager().switchTo(GameState.MAIN_MENU, true);
		};
		_navigation.setBackAction(backAction);
		_navigation.register();
	}
	
	@Override
	protected void onExitState()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		
		// Close dialog if open.
		dismissCreateDialog();
		
		if (_navigation != null)
		{
			_navigation.unregister();
			_navigation = null;
		}
		
		// Dismiss any active question dialog or message.
		QuestionManager.dismiss();
		MessageManager.dismiss();
		
		// Remove all GUI elements.
		if (_background != null)
		{
			app.getGuiNode().detachChild(_background);
			_background = null;
		}
		
		if (_titleLabel != null)
		{
			app.getGuiNode().detachChild(_titleLabel);
			_titleLabel = null;
		}
		
		if (_logo != null)
		{
			app.getGuiNode().detachChild(_logo);
			_logo = null;
		}
		
		if (_listContainer != null)
		{
			app.getGuiNode().detachChild(_listContainer);
			_listContainer = null;
		}
		
		if (_pageBar != null)
		{
			app.getGuiNode().detachChild(_pageBar);
			_pageBar = null;
		}
		
		if (_bottomBar != null)
		{
			app.getGuiNode().detachChild(_bottomBar);
			_bottomBar = null;
		}
		
		if (_emptyLabel != null)
		{
			app.getGuiNode().detachChild(_emptyLabel);
			_emptyLabel = null;
		}
		
		_worlds = null;
	}
	
	// --- World List ---
	
	/**
	 * Reload the world list from disk and rebuild the list UI for the current page.
	 */
	private void refreshWorldList()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		final float screenWidth = app.getCamera().getWidth();
		final float screenHeight = app.getCamera().getHeight();
		final float screenCenterX = screenWidth / 2f;
		
		// Clear existing list.
		_listContainer.detachAllChildren();
		
		// Remove empty label if present.
		if (_emptyLabel != null)
		{
			app.getGuiNode().detachChild(_emptyLabel);
			_emptyLabel = null;
		}
		
		// Remove page bar if present.
		if (_pageBar != null)
		{
			app.getGuiNode().detachChild(_pageBar);
			_pageBar = null;
		}
		
		// Load worlds.
		_worlds = WorldInfo.loadAllWorlds();
		
		if (_worlds.isEmpty())
		{
			// Show empty message.
			_emptyLabel = new Label("No worlds yet. Create one!");
			_emptyLabel.setFont(FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_REGULAR_PATH, Font.PLAIN, 20));
			_emptyLabel.setFontSize(20);
			_emptyLabel.setColor(new ColorRGBA(0.8f, 0.8f, 0.8f, 0.9f));
			
			final float emptyWidth = _emptyLabel.getPreferredSize().x;
			final float emptyY = screenHeight * 0.55f;
			_emptyLabel.setLocalTranslation(screenCenterX - (emptyWidth / 2f), emptyY, 0);
			app.getGuiNode().attachChild(_emptyLabel);
		}
		else
		{
			// Clamp current page.
			final int totalPages = getTotalPages();
			if (_currentPage >= totalPages)
			{
				_currentPage = totalPages - 1;
			}
			if (_currentPage < 0)
			{
				_currentPage = 0;
			}
			
			// Calculate page range.
			final int startIndex = _currentPage * WORLDS_PER_PAGE;
			final int endIndex = Math.min(startIndex + WORLDS_PER_PAGE, _worlds.size());
			
			// Build world entries for current page.
			for (int i = startIndex; i < endIndex; i++)
			{
				final WorldInfo world = _worlds.get(i);
				final Container entry = createWorldEntry(world);
				_listContainer.addChild(entry);
				
				// Add spacer between entries (not after last).
				if (i < endIndex - 1)
				{
					addListSpacer();
				}
			}
			
			// Build page indicator if more than one page.
			if (totalPages > 1)
			{
				buildPageBar(totalPages);
			}
		}
		
		// Position list container centered horizontally, below title.
		final float listContainerWidth = _listContainer.getPreferredSize().x;
		final float listY = screenHeight * 0.785f;
		_listContainer.setLocalTranslation(screenCenterX - (listContainerWidth / 2f), listY, 0);
	}
	
	/**
	 * Create a single world entry panel with name, seed/date info, and Play + X buttons.
	 * @param world The world info
	 * @return The entry container
	 */
	private Container createWorldEntry(WorldInfo world)
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		final float screenWidth = app.getCamera().getWidth();
		final float screenHeight = app.getCamera().getHeight();
		final float entryInnerWidth = screenWidth * LIST_WIDTH_PERCENT - 20;
		
		// Outer entry container with dark background and percentage-based padding.
		final float padTop = screenHeight * ENTRY_PAD_TOP_PERCENT;
		final float padBottom = screenHeight * ENTRY_PAD_BOTTOM_PERCENT;
		final Container entry = new Container();
		entry.setBackground(new QuadBackgroundComponent(ENTRY_BG_COLOR));
		entry.setInsets(new Insets3f(padTop, 10, padBottom, 10));
		
		// --- Row 1: [World Name] ... [■] ---
		final Container nameRow = new Container(new SpringGridLayout(Axis.Y, Axis.X, FillMode.None, FillMode.None));
		nameRow.setBackground(null);
		
		// World name label (column 0).
		final Label nameLabel = new Label(" " + world.getName());
		nameLabel.setFont(FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_REGULAR_PATH, Font.PLAIN, 20));
		nameLabel.setFontSize(20);
		nameLabel.setColor(ColorRGBA.White);
		nameRow.addChild(nameLabel, 0, 0);
		
		// Flex spacer to push ■ right (column 1).
		final float nameWidth = nameLabel.getPreferredSize().x;
		final float symbolWidth = 20; // Approximate width of ■ label.
		final float nameSpacer = entryInnerWidth - nameWidth - symbolWidth;
		final Label nameFlexSpacer = new Label("");
		nameFlexSpacer.setPreferredSize(new Vector3f(Math.max(nameSpacer, 10), 1, 0));
		nameFlexSpacer.setBackground(null);
		nameRow.addChild(nameFlexSpacer, 0, 1);
		
		// Delete symbol "■" (column 2) — gray, turns red on hover.
		final Label deleteSymbol = new Label(FontManager.SYMBOL_SQUARE + " ");
		deleteSymbol.setFont(FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_REGULAR_PATH, Font.PLAIN, 12));
		deleteSymbol.setFontSize(12);
		deleteSymbol.setColor(DELETE_SYMBOL_COLOR);
		deleteSymbol.setBackground(null);
		deleteSymbol.setInsets(new Insets3f(0, 0, 0, 0));
		nameRow.addChild(deleteSymbol, 0, 2);
		
		// Click + hover listener for delete symbol.
		final Runnable deleteAction = () ->
		{
			app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
			QuestionManager.show("Delete \"" + world.getName() + "\"?\nThis cannot be undone.", () ->
			{
				WorldInfo.delete(world);
				rebuildNavigation();
			}, null);
		};
		MouseEventControl.addListenersToSpatial(deleteSymbol, new DefaultMouseListener()
		{
			@Override
			public void mouseButtonEvent(MouseButtonEvent event, Spatial target, Spatial capture)
			{
				if (event.isPressed())
				{
					deleteAction.run();
				}
			}
			
			@Override
			public void mouseEntered(MouseMotionEvent event, Spatial target, Spatial capture)
			{
				deleteSymbol.setColor(DELETE_SYMBOL_HOVER);
			}
			
			@Override
			public void mouseExited(MouseMotionEvent event, Spatial target, Spatial capture)
			{
				deleteSymbol.setColor(DELETE_SYMBOL_COLOR);
			}
		});
		// @formatter:off
		_navigation.addSlot(MenuNavigationManager.customSlot(
			() -> deleteSymbol.setColor(DELETE_SYMBOL_HOVER), // onFocus.
			() -> deleteSymbol.setColor(DELETE_SYMBOL_COLOR), // onUnfocus.
			null, // onLeft.
			null, // onRight.
			deleteAction // onConfirm.
		));
		// @formatter:on
		
		entry.addChild(nameRow);
		
		// --- Row 2: [Seed | Last played] ---
		final String lastPlayed = DATE_FORMAT.format(new Date(world.getLastPlayedAt()));
		final Label infoLabel = new Label(" Seed: " + world.getSeed() + "  |  " + lastPlayed);
		infoLabel.setFont(FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_REGULAR_PATH, Font.PLAIN, 12));
		infoLabel.setFontSize(12);
		infoLabel.setColor(INFO_TEXT_COLOR);
		infoLabel.setInsets(new Insets3f(0, 0, screenHeight * INFO_BOTTOM_PERCENT, 0));
		entry.addChild(infoLabel);
		
		// --- Row 3: [Play] centered ---
		final Container playRow = new Container(new SpringGridLayout(Axis.Y, Axis.X, FillMode.None, FillMode.None));
		playRow.setBackground(null);
		playRow.setInsets(new Insets3f(0, 0, screenHeight * 0.01f, 0));
		
		// Left spacer for centering.
		final float playWidth = screenWidth * PLAY_BUTTON_WIDTH_PERCENT;
		final float sideSpace = (entryInnerWidth - playWidth) / 2f;
		
		final Label leftSpacer = new Label("");
		leftSpacer.setPreferredSize(new Vector3f(Math.max(sideSpace, 1), 1, 0));
		leftSpacer.setBackground(null);
		playRow.addChild(leftSpacer, 0, 0);
		
		// Play button (centered).
		final Runnable playAction = () ->
		{
			app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
			enterWorld(world);
		};
		final Panel playButton = ButtonManager.createMenuButtonByScreenPercentage(app.getAssetManager(), "Play", PLAY_BUTTON_WIDTH_PERCENT, ENTRY_BUTTON_HEIGHT_PERCENT, playAction);
		playRow.addChild(playButton, 0, 1);
		_navigation.addSlot(MenuNavigationManager.buttonSlot(playButton, playAction));
		
		entry.addChild(playRow);
		
		return entry;
	}
	
	// --- Pagination ---
	
	/**
	 * Get the total number of pages.
	 * @return Total pages (at least 1)
	 */
	private int getTotalPages()
	{
		if (_worlds == null || _worlds.isEmpty())
		{
			return 1;
		}
		
		return (int) Math.ceil((double) _worlds.size() / WORLDS_PER_PAGE);
	}
	
	/**
	 * Build the page navigation bar: ◀ Page X / Y ▶<br>
	 * Positioned between the world list and the bottom buttons.<br>
	 * Arrow colors dim when at the first or last page.<br>
	 * Registered as a navigation slot with left/right for page changes.
	 * @param totalPages The total number of pages
	 */
	private void buildPageBar(int totalPages)
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		final float screenWidth = app.getCamera().getWidth();
		final float screenHeight = app.getCamera().getHeight();
		final float screenCenterX = screenWidth / 2f;
		
		_pageBar = new Container(new SpringGridLayout(Axis.Y, Axis.X, FillMode.None, FillMode.None));
		_pageBar.setBackground(null);
		
		// Left arrow.
		_pageLeftArrow = new Label(FontManager.SYMBOL_ARROW_LEFT);
		_pageLeftArrow.setFont(FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_REGULAR_PATH, Font.PLAIN, 20));
		_pageLeftArrow.setFontSize(20);
		_pageLeftArrow.setColor(_currentPage > 0 ? PAGE_ARROW_COLOR : PAGE_ARROW_DISABLED);
		_pageLeftArrow.setInsets(new Insets3f(0, 10, 0, 10));
		_pageBar.addChild(_pageLeftArrow, 0, 0);
		
		// Click listener for left arrow.
		MouseEventControl.addListenersToSpatial(_pageLeftArrow, new DefaultMouseListener()
		{
			@Override
			public void mouseButtonEvent(MouseButtonEvent event, Spatial target, Spatial capture)
			{
				if (event.isPressed() && _currentPage > 0)
				{
					app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
					_currentPage--;
					rebuildNavigation();
				}
			}
		});
		
		// Page text.
		_pageLabel = new Label("Page " + (_currentPage + 1) + " / " + totalPages);
		_pageLabel.setFont(FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_REGULAR_PATH, Font.PLAIN, 16));
		_pageLabel.setFontSize(16);
		_pageLabel.setColor(PAGE_TEXT_COLOR);
		_pageLabel.setTextHAlignment(HAlignment.Center);
		_pageBar.addChild(_pageLabel, 0, 1);
		
		// Right arrow.
		_pageRightArrow = new Label(FontManager.SYMBOL_ARROW_RIGHT);
		_pageRightArrow.setFont(FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_REGULAR_PATH, Font.PLAIN, 20));
		_pageRightArrow.setFontSize(20);
		_pageRightArrow.setColor(_currentPage < totalPages - 1 ? PAGE_ARROW_COLOR : PAGE_ARROW_DISABLED);
		_pageRightArrow.setInsets(new Insets3f(0, 10, 0, 10));
		_pageBar.addChild(_pageRightArrow, 0, 2);
		
		// Click listener for right arrow.
		MouseEventControl.addListenersToSpatial(_pageRightArrow, new DefaultMouseListener()
		{
			@Override
			public void mouseButtonEvent(MouseButtonEvent event, Spatial target, Spatial capture)
			{
				if (event.isPressed() && _currentPage < getTotalPages() - 1)
				{
					app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
					_currentPage++;
					rebuildNavigation();
				}
			}
		});
		
		// Register page navigation slot (left/right arrows change page, confirm does nothing).
		// @formatter:off
		_navigation.addSlot(MenuNavigationManager.customSlot(
			() -> _pageLabel.setColor(MenuNavigationManager.FOCUS_HIGHLIGHT_COLOR), // onFocus.
			() -> _pageLabel.setColor(PAGE_TEXT_COLOR), // onUnfocus.
			() -> // onLeft.
			{
				if (_currentPage > 0)
				{
					app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
					_currentPage--;
					rebuildNavigation();
				}
			},
			() -> // onRight.
			{
				if (_currentPage < getTotalPages() - 1)
				{
					app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
					_currentPage++;
					rebuildNavigation();
				}
			},
			null // onConfirm.
		));
		// @formatter:on
		
		// Position centered, between list and bottom bar.
		final float pageBarWidth = _pageBar.getPreferredSize().x;
		final float pageBarY = screenHeight * 0.18f;
		_pageBar.setLocalTranslation(screenCenterX - (pageBarWidth / 2f), pageBarY, 0);
		
		app.getGuiNode().attachChild(_pageBar);
	}
	
	// --- Bottom Bar ---
	
	/**
	 * Build or rebuild the bottom button bar (Create New World + Back).
	 */
	private void buildBottomBar()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		final float screenWidth = app.getCamera().getWidth();
		final float screenHeight = app.getCamera().getHeight();
		final float screenCenterX = screenWidth / 2f;
		
		_bottomBar = new Container();
		_bottomBar.setBackground(null);
		
		// Create New World button.
		final Runnable createAction = () ->
		{
			app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
			showCreateDialog();
		};
		final Panel createButton = ButtonManager.createMenuButtonByScreenPercentage(app.getAssetManager(), "Create New World", BOTTOM_BUTTON_WIDTH_PERCENT, BOTTOM_BUTTON_HEIGHT_PERCENT, createAction);
		_bottomBar.addChild(createButton);
		_navigation.addSlot(MenuNavigationManager.buttonSlot(createButton, createAction));
		
		addBottomSpacer();
		
		// Back button.
		final Runnable backAction = () ->
		{
			app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
			app.getGameStateManager().switchTo(GameState.MAIN_MENU, true);
		};
		final Panel backButton = ButtonManager.createMenuButtonByScreenPercentage(app.getAssetManager(), "Back", BOTTOM_BUTTON_WIDTH_PERCENT, BOTTOM_BUTTON_HEIGHT_PERCENT, backAction);
		_bottomBar.addChild(backButton);
		_navigation.addSlot(MenuNavigationManager.buttonSlot(backButton, backAction));
		
		// Position bottom bar centered.
		final float bottomBarWidth = _bottomBar.getPreferredSize().x;
		final float bottomBarY = screenHeight * 0.15f;
		_bottomBar.setLocalTranslation(screenCenterX - (bottomBarWidth / 2f), bottomBarY, 0);
	}
	
	/**
	 * Rebuild navigation slots and refresh the world list.<br>
	 * Called after a world is deleted or a page changes.
	 */
	private void rebuildNavigation()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		
		if (_navigation != null)
		{
			_navigation.unregister();
		}
		
		_navigation = new MenuNavigationManager();
		
		// Refresh the world list (re-creates entry slots).
		refreshWorldList();
		
		// Rebuild bottom bar.
		app.getGuiNode().detachChild(_bottomBar);
		buildBottomBar();
		app.getGuiNode().attachChild(_bottomBar);
		
		// Register navigation.
		final Runnable backAction = () ->
		{
			app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
			app.getGameStateManager().switchTo(GameState.MAIN_MENU, true);
		};
		_navigation.setBackAction(backAction);
		_navigation.register();
	}
	
	/**
	 * Enter a world: update last played time, set active world, transition to PlayingState.
	 * @param world The world to enter
	 */
	private void enterWorld(WorldInfo world)
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		
		// Update last played timestamp and save.
		world.setLastPlayedAt(System.currentTimeMillis());
		WorldInfo.save(world, world.getWorldDirectory());
		
		// Set as active world.
		app.setActiveWorld(world);
		
		// Transition to playing.
		app.getGameStateManager().switchTo(GameState.PLAYING, true);
	}
	
	// --- Create World Dialog ---
	
	/**
	 * Show the "Create New World" dialog overlay.<br>
	 * Locks navigation to prevent WASD keystrokes from triggering<br>
	 * menu movement while typing in text fields.
	 */
	private void showCreateDialog()
	{
		if (_dialogOpen)
		{
			return;
		}
		
		_dialogOpen = true;
		
		// Lock navigation so WASD goes to text fields, not menu navigation.
		if (_navigation != null)
		{
			_navigation.setLocked(true);
		}
		
		final SimpleCraft app = SimpleCraft.getInstance();
		final float screenWidth = app.getCamera().getWidth();
		final float screenHeight = app.getCamera().getHeight();
		final float screenCenterX = screenWidth / 2f;
		final float screenCenterY = screenHeight / 2f;
		
		// Semi-transparent overlay to dim background.
		_dialogOverlay = new Container();
		_dialogOverlay.setPreferredSize(new Vector3f(screenWidth, screenHeight, 0));
		_dialogOverlay.setBackground(new QuadBackgroundComponent(new ColorRGBA(0, 0, 0, 0.6f)));
		_dialogOverlay.setLocalTranslation(0, screenHeight, 1);
		
		// Dialog box.
		final float dialogWidth = screenWidth * 0.30f;
		_dialogContainer = new Container();
		_dialogContainer.setBackground(new QuadBackgroundComponent(DIALOG_BG_COLOR));
		_dialogContainer.setInsets(new Insets3f(15, 20, 15, 20));
		
		// Dialog title.
		final Label dialogTitle = new Label("Create New World");
		dialogTitle.setFont(FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_REGULAR_PATH, Font.PLAIN, 28));
		dialogTitle.setFontSize(28);
		dialogTitle.setColor(ColorRGBA.White);
		dialogTitle.setTextHAlignment(HAlignment.Center);
		dialogTitle.setPreferredSize(new Vector3f(dialogWidth - 40, 35, 0));
		_dialogContainer.addChild(dialogTitle);
		
		addDialogSpacer(DIALOG_FIELD_SPACING);
		
		// World Name label.
		final Label nameLabel = new Label(" World Name:");
		nameLabel.setFont(FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_REGULAR_PATH, Font.PLAIN, 16));
		nameLabel.setFontSize(16);
		nameLabel.setColor(new ColorRGBA(0.85f, 0.85f, 0.85f, 1f));
		_dialogContainer.addChild(nameLabel);
		
		// World Name text field — visible background so it reads as an input box.
		_nameField = new TextField("New World");
		_nameField.setPreferredSize(new Vector3f(dialogWidth - 40, 28, 0));
		_nameField.setFont(FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_REGULAR_PATH, Font.PLAIN, 16));
		_nameField.setFontSize(16);
		_nameField.setColor(FIELD_TEXT_COLOR);
		_nameField.setBackground(new QuadBackgroundComponent(FIELD_BG_COLOR));
		_nameField.setInsets(new Insets3f(4, 6, 4, 6));
		_dialogContainer.addChild(_nameField);
		
		addDialogSpacer(DIALOG_FIELD_SPACING);
		
		// Seed label.
		final Label seedLabel = new Label(" Seed (leave empty for random):");
		seedLabel.setFont(FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_REGULAR_PATH, Font.PLAIN, 16));
		seedLabel.setFontSize(16);
		seedLabel.setColor(new ColorRGBA(0.85f, 0.85f, 0.85f, 1f));
		_dialogContainer.addChild(seedLabel);
		
		// Seed text field — visible background.
		_seedField = new TextField("");
		_seedField.setPreferredSize(new Vector3f(dialogWidth - 40, 28, 0));
		_seedField.setFont(FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_REGULAR_PATH, Font.PLAIN, 16));
		_seedField.setFontSize(16);
		_seedField.setColor(FIELD_TEXT_COLOR);
		_seedField.setBackground(new QuadBackgroundComponent(FIELD_BG_COLOR));
		_seedField.setInsets(new Insets3f(4, 6, 4, 6));
		_dialogContainer.addChild(_seedField);
		
		addDialogSpacer(DIALOG_FIELD_SPACING * 2);
		
		// Dialog buttons.
		final Container dialogButtons = new Container();
		dialogButtons.setBackground(null);
		
		// Create button.
		final Runnable confirmCreateAction = () ->
		{
			app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
			createWorld();
		};
		final Panel createBtn = ButtonManager.createMenuButtonByScreenPercentage(app.getAssetManager(), "Create", 0.12f, 0.055f, confirmCreateAction);
		dialogButtons.addChild(createBtn);
		
		// Small spacer between dialog buttons.
		final Label dialogBtnSpacer = new Label("");
		dialogBtnSpacer.setPreferredSize(new Vector3f(1, 8, 0));
		dialogButtons.addChild(dialogBtnSpacer);
		
		// Cancel button.
		final Runnable cancelAction = () ->
		{
			app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
			dismissCreateDialog();
		};
		final Panel cancelBtn = ButtonManager.createMenuButtonByScreenPercentage(app.getAssetManager(), "Cancel", 0.12f, 0.055f, cancelAction);
		dialogButtons.addChild(cancelBtn);
		
		_dialogContainer.addChild(dialogButtons);
		
		// Position dialog centered on screen.
		final float dialogTotalWidth = _dialogContainer.getPreferredSize().x;
		final float dialogHeight = _dialogContainer.getPreferredSize().y;
		_dialogContainer.setLocalTranslation(screenCenterX - (dialogTotalWidth / 2f), screenCenterY + (dialogHeight / 2f), 2);
		
		// Attach overlay and dialog.
		app.getGuiNode().attachChild(_dialogOverlay);
		app.getGuiNode().attachChild(_dialogContainer);
	}
	
	/**
	 * Dismiss the create world dialog and unlock navigation.
	 */
	private void dismissCreateDialog()
	{
		if (!_dialogOpen)
		{
			return;
		}
		
		_dialogOpen = false;
		
		final SimpleCraft app = SimpleCraft.getInstance();
		
		if (_dialogOverlay != null)
		{
			app.getGuiNode().detachChild(_dialogOverlay);
			_dialogOverlay = null;
		}
		
		if (_dialogContainer != null)
		{
			app.getGuiNode().detachChild(_dialogContainer);
			_dialogContainer = null;
		}
		
		_nameField = null;
		_seedField = null;
		
		// Unlock navigation now that text fields are gone.
		if (_navigation != null)
		{
			_navigation.setLocked(false);
		}
	}
	
	/**
	 * Validate inputs and create a new world.
	 */
	private void createWorld()
	{
		final String name = _nameField.getText().trim();
		
		// Validate name.
		if (name.isEmpty())
		{
			System.err.println("WARNING: World name cannot be empty.");
			MessageManager.show("World name cannot be empty.");
			return;
		}
		
		// Check for duplicate name.
		if (WorldInfo.worldExists(name))
		{
			System.err.println("WARNING: A world with the name \"" + name + "\" already exists.");
			MessageManager.show("A world named \"" + name + "\" already exists.");
			return;
		}
		
		// Determine seed.
		final String seedInput = _seedField.getText().trim();
		final String seed = seedInput.isEmpty() ? WorldInfo.randomSeed() : seedInput;
		
		// Create world info.
		final WorldInfo world = new WorldInfo(name, seed);
		
		// Save to disk.
		WorldInfo.save(world, world.getWorldDirectory());
		
		System.out.println("Created world: " + name + " with seed: " + seed);
		
		// Close dialog (unlocks navigation).
		dismissCreateDialog();
		
		// Enter the new world.
		enterWorld(world);
	}
	
	// --- Spacer Helpers ---
	
	/**
	 * Add a transparent spacer between world entries in the list.
	 */
	private void addListSpacer()
	{
		final float screenHeight = SimpleCraft.getInstance().getCamera().getHeight();
		final Label spacer = new Label("");
		spacer.setPreferredSize(new Vector3f(1, screenHeight * ENTRY_SPACING_PERCENT, 0));
		spacer.setBackground(null);
		_listContainer.addChild(spacer);
	}
	
	/**
	 * Add a transparent spacer between bottom bar buttons.
	 */
	private void addBottomSpacer()
	{
		final float screenHeight = SimpleCraft.getInstance().getCamera().getHeight();
		final Label spacer = new Label("");
		spacer.setPreferredSize(new Vector3f(1, screenHeight * BOTTOM_BUTTON_SPACING_PERCENT, 0));
		spacer.setBackground(null);
		_bottomBar.addChild(spacer);
	}
	
	/**
	 * Add a transparent spacer inside the dialog container.
	 * @param height The spacer height in pixels
	 */
	private void addDialogSpacer(int height)
	{
		final Label spacer = new Label("");
		spacer.setPreferredSize(new Vector3f(1, height, 0));
		_dialogContainer.addChild(spacer);
	}
}
