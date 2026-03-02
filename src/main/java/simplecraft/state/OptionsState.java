package simplecraft.state;

import java.awt.Font;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.jme3.app.Application;
import com.jme3.font.BitmapFont;
import com.jme3.input.KeyInput;
import com.jme3.input.RawInputListener;
import com.jme3.input.event.JoyAxisEvent;
import com.jme3.input.event.JoyButtonEvent;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.input.event.TouchEvent;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.jme3.scene.Spatial.CullHint;
import com.jme3.ui.Picture;

import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.HAlignment;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.Panel;
import com.simsilica.lemur.Slider;
import com.simsilica.lemur.VAlignment;
import com.simsilica.lemur.component.SpringGridLayout;
import com.simsilica.lemur.core.VersionedReference;
import com.simsilica.lemur.event.CursorButtonEvent;
import com.simsilica.lemur.event.CursorEventControl;
import com.simsilica.lemur.event.DefaultCursorListener;

import simplecraft.SimpleCraft;
import simplecraft.audio.AudioManager;
import simplecraft.input.GameInputManager;
import simplecraft.input.MenuNavigationManager;
import simplecraft.input.MenuNavigationManager.NavigationSlot;
import simplecraft.settings.SettingsManager;
import simplecraft.settings.WindowDisplayManager;
import simplecraft.ui.ButtonManager;
import simplecraft.ui.FontManager;
import simplecraft.ui.QuestionManager;

/**
 * Options state with three tabs: Display, Audio, and Keybindings.<br>
 * All volume changes are applied in real-time. Display settings apply immediately via context restart.<br>
 * Accessible from both Main Menu and Pause Menu; Back returns to the previous state.
 * @author Pantelis Andrianakis
 * @since February 19th 2026
 */
public class OptionsState extends FadeableAppState
{
	private static final String BACKGROUND_PATH = "assets/images/backgrounds/main_menu.png";
	
	// Font size ratios relative to screen height for resolution-independent scaling.
	private static final float TITLE_FONT_RATIO = 0.078f;
	private static final float LABEL_FONT_RATIO = 0.031f;
	private static final float VALUE_FONT_RATIO = 0.028f;
	private static final float TAB_FONT_RATIO = 0.028f;
	private static final float SLIDER_BUTTON_FONT_RATIO = 0.039f;
	
	// Layout constants (proportional to screen).
	private static final float SLIDER_WIDTH_PERCENT = 0.18f;
	private static final float LABEL_WIDTH_PERCENT = 0.13f;
	private static final float VALUE_LABEL_WIDTH_PERCENT = 0.10f;
	
	private static final float SFX_TEST_INTERVAL = 0.4f;
	
	private static final ColorRGBA TAB_ACTIVE_COLOR = ColorRGBA.White;
	private static final ColorRGBA TAB_INACTIVE_COLOR = new ColorRGBA(0.5f, 0.5f, 0.5f, 1f);
	private static final ColorRGBA TOGGLE_ON_COLOR = new ColorRGBA(0.3f, 1f, 0.3f, 1f);
	private static final ColorRGBA TOGGLE_OFF_COLOR = new ColorRGBA(0.6f, 0.6f, 0.6f, 1f);
	private static final ColorRGBA KEY_BUTTON_COLOR = new ColorRGBA(0.7f, 0.85f, 1f, 1f);
	private static final ColorRGBA KEY_LISTENING_COLOR = new ColorRGBA(1f, 1f, 0.3f, 1f);
	private static final ColorRGBA FIXED_KEY_COLOR = new ColorRGBA(0.5f, 0.5f, 0.5f, 1f);
	
	private static final int TAB_DISPLAY = 0;
	private static final int TAB_AUDIO = 1;
	private static final int TAB_KEYBINDINGS = 2;
	
	// Keybinding sub-tab indices.
	private static final int SUB_TAB_MOVEMENT = 0;
	private static final int SUB_TAB_ACTIONS = 1;
	private static final int SUB_TAB_MOUSE = 2;
	
	// Scaled font sizes (computed per build from screen height).
	private int _titleFontSize;
	private int _labelFontSize;
	private int _valueFontSize;
	private int _tabFontSize;
	private int _sliderButtonFontSize;
	private float _sliderHeight;
	private float _labelWidth;
	private float _valueLabelWidth;
	private float _rowSpacing;
	private float _keybindLabelWidth;
	private float _keybindGapWidth;
	
	// GUI root elements.
	private Picture _background;
	private Label _titleLabel;
	private Container _tabRow;
	private Container _displayContent;
	private Container _audioContent;
	private Container _keybindingsSubTabRow;
	private Container _keyMovementContent;
	private Container _keyActionsContent;
	private Container _keyMouseContent;
	private Container _buttonRow;
	
	// Tab buttons.
	private Label _tabDisplayButton;
	private Label _tabAudioButton;
	private Label _tabKeybindingsButton;
	private int _activeTab = TAB_DISPLAY;
	
	// Keybinding sub-tab buttons.
	private Label _subTabMovementButton;
	private Label _subTabActionsButton;
	private Label _subTabMouseButton;
	private int _activeSubTab = SUB_TAB_MOVEMENT;
	
	// Audio sliders.
	private Slider _masterSlider;
	private Slider _musicSlider;
	private Slider _sfxSlider;
	private Label _masterValueLabel;
	private Label _musicValueLabel;
	private Label _sfxValueLabel;
	private VersionedReference<Double> _masterVolumeRef;
	private VersionedReference<Double> _musicVolumeRef;
	private VersionedReference<Double> _sfxVolumeRef;
	
	// Display controls.
	private int _resolutionIndex;
	private Label _resolutionValueLabel;
	private Button _fullscreenToggle;
	private Slider _renderDistanceSlider;
	private Label _renderDistanceValueLabel;
	private VersionedReference<Double> _renderDistanceRef;
	private Button _showHighlightToggle;
	private Button _showCrosshairToggle;
	private Button _showStatsToggle;
	private Button _showFpsToggle;
	
	// Screen size tracking for rebuild after live display change.
	private int _lastScreenWidth;
	private int _lastScreenHeight;
	
	// SFX test click cooldown.
	private float _sfxTestCooldown = 0f;
	
	// Keybinding rebind state.
	private final Map<String, Button> _keybindButtons = new LinkedHashMap<>();
	private final Map<String, Button> _mouseBindButtons = new LinkedHashMap<>();
	private String _listeningAction;
	private Button _listeningButton;
	private boolean _listeningForMouse;
	private long _listenStartTime;
	private RawInputListener _rawInputListener;
	
	// --- Keyboard navigation (via MenuNavigationManager) ---
	
	// Per-tab content navigation slots.
	private final List<NavigationSlot> _displayContentSlots = new ArrayList<>();
	private final List<NavigationSlot> _audioContentSlots = new ArrayList<>();
	private final List<NavigationSlot> _keyMovementSlots = new ArrayList<>();
	private final List<NavigationSlot> _keyActionsSlots = new ArrayList<>();
	private final List<NavigationSlot> _keyMouseSlots = new ArrayList<>();
	
	// Special navigation slots.
	private NavigationSlot _tabRowSlot;
	private NavigationSlot _subTabRowSlot;
	private NavigationSlot _bottomRowSlot;
	
	private int _bottomFocusIndex = 1; // 0 = Defaults, 1 = Back.
	
	// Bottom button references for focus highlighting.
	private Panel _defaultsButton;
	private Panel _backButton;
	
	private MenuNavigationManager _navigation;
	
	public OptionsState()
	{
		setFadeIn(0.6f, new ColorRGBA(0, 0, 0, 1));
		setFadeOut(0.6f, new ColorRGBA(0, 0, 0, 1));
	}
	
	@Override
	protected void initialize(Application app)
	{
	}
	
	@Override
	protected void cleanup(Application app)
	{
	}
	
	@Override
	protected void onEnterState()
	{
		_navigation = new MenuNavigationManager();
		
		buildGui();
		
		_navigation.setBackAction(() ->
		{
			final SimpleCraft app = SimpleCraft.getInstance();
			app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
			app.getGameStateManager().returnToPrevious(true);
		});
		_navigation.register();
	}
	
	@Override
	protected void onExitState()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		
		if (_navigation != null)
		{
			_navigation.unregister();
			_navigation = null;
		}
		
		cancelListening();
		QuestionManager.dismiss();
		app.getSettingsManager().save();
		
		detachAllGui();
	}
	
	@Override
	protected void onUpdateState(float tpf)
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		
		// Detect screen size change after live display settings apply.
		final int currentWidth = app.getCamera().getWidth();
		final int currentHeight = app.getCamera().getHeight();
		if (currentWidth != _lastScreenWidth || currentHeight != _lastScreenHeight)
		{
			_navigation.unregister();
			detachAllGui();
			_navigation = new MenuNavigationManager();
			_navigation.setBackAction(() ->
			{
				app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
				app.getGameStateManager().returnToPrevious(true);
			});
			buildGui();
			_navigation.register();
			return;
		}
		
		// Decrease SFX test cooldown.
		if (_sfxTestCooldown > 0f)
		{
			_sfxTestCooldown -= tpf;
		}
		
		// Only poll sliders for the active tab.
		if (_activeTab == TAB_AUDIO)
		{
			pollAudioSliders(app);
		}
		else if (_activeTab == TAB_DISPLAY)
		{
			pollDisplaySliders(app);
		}
	}
	
	// ========== GUI BUILD / TEARDOWN ==========
	
	/**
	 * Build the entire Options GUI, scaled to current screen dimensions.
	 */
	private void buildGui()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		final SettingsManager settings = app.getSettingsManager();
		final float screenWidth = app.getCamera().getWidth();
		final float screenHeight = app.getCamera().getHeight();
		final float screenCenterX = screenWidth / 2f;
		
		// Compute scaled font sizes and layout values.
		_titleFontSize = Math.max(20, (int) (screenHeight * TITLE_FONT_RATIO));
		_labelFontSize = Math.max(12, (int) (screenHeight * LABEL_FONT_RATIO));
		_valueFontSize = Math.max(10, (int) (screenHeight * VALUE_FONT_RATIO));
		_tabFontSize = Math.max(12, (int) (screenHeight * TAB_FONT_RATIO));
		_sliderButtonFontSize = Math.max(14, (int) (screenHeight * SLIDER_BUTTON_FONT_RATIO));
		_sliderHeight = screenHeight * 0.031f;
		_labelWidth = screenWidth * LABEL_WIDTH_PERCENT;
		_valueLabelWidth = screenWidth * VALUE_LABEL_WIDTH_PERCENT;
		_rowSpacing = screenHeight * 0.011f;
		_keybindLabelWidth = screenWidth * 0.22f;
		_keybindGapWidth = screenWidth * 0.025f;
		
		final float sliderWidth = screenWidth * SLIDER_WIDTH_PERCENT;
		
		// --- Background ---
		_background = new Picture("Options Background");
		_background.setImage(app.getAssetManager(), BACKGROUND_PATH, true);
		_background.setWidth(screenWidth);
		_background.setHeight(screenHeight);
		_background.setLocalTranslation(0, 0, -10);
		_background.setCullHint(CullHint.Never);
		
		// --- Title ---
		_titleLabel = new Label("Options");
		_titleLabel.setFont(FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_LINOCUT_PATH, Font.PLAIN, _titleFontSize));
		_titleLabel.setFontSize(_titleFontSize);
		_titleLabel.setColor(ColorRGBA.White);
		final float titleWidth = _titleLabel.getPreferredSize().x;
		_titleLabel.setLocalTranslation(screenCenterX - (titleWidth / 2f), screenHeight * 0.94f, 0);
		
		// --- Tab Row ---
		_tabRow = new Container();
		_tabRow.setBackground(null);
		_tabRow.setLayout(new SpringGridLayout(Axis.X, Axis.Y, FillMode.None, FillMode.None));
		
		_tabDisplayButton = createTabButton(app, "Display", TAB_DISPLAY);
		_tabAudioButton = createTabButton(app, "Audio", TAB_AUDIO);
		_tabKeybindingsButton = createTabButton(app, "Keybindings", TAB_KEYBINDINGS);
		
		_tabRow.addChild(_tabDisplayButton);
		_tabRow.addChild(createTabSpacer());
		_tabRow.addChild(_tabAudioButton);
		_tabRow.addChild(createTabSpacer());
		_tabRow.addChild(_tabKeybindingsButton);
		
		final float tabRowWidth = _tabRow.getPreferredSize().x;
		_tabRow.setLocalTranslation(screenCenterX - (tabRowWidth / 2f), screenHeight * 0.82f, 0);
		
		updateTabStyles();
		
		// --- Display Content ---
		_displayContent = new Container();
		_displayContent.setBackground(null);
		buildDisplayContent(app, settings, sliderWidth);
		
		// --- Audio Content ---
		_audioContent = new Container();
		_audioContent.setBackground(null);
		buildAudioContent(app, settings, sliderWidth);
		
		// --- Keybindings Sub-Tab Row ---
		_keybindingsSubTabRow = new Container();
		_keybindingsSubTabRow.setBackground(null);
		_keybindingsSubTabRow.setLayout(new SpringGridLayout(Axis.X, Axis.Y, FillMode.None, FillMode.None));
		
		_subTabMovementButton = createSubTabButton(app, "Movement", SUB_TAB_MOVEMENT);
		_subTabActionsButton = createSubTabButton(app, "Actions", SUB_TAB_ACTIONS);
		_subTabMouseButton = createSubTabButton(app, "Mouse", SUB_TAB_MOUSE);
		
		_keybindingsSubTabRow.addChild(_subTabMovementButton);
		_keybindingsSubTabRow.addChild(createSubTabSpacer());
		_keybindingsSubTabRow.addChild(_subTabActionsButton);
		_keybindingsSubTabRow.addChild(createSubTabSpacer());
		_keybindingsSubTabRow.addChild(_subTabMouseButton);
		
		final float subTabRowWidth = _keybindingsSubTabRow.getPreferredSize().x;
		_keybindingsSubTabRow.setLocalTranslation(screenCenterX - (subTabRowWidth / 2f), screenHeight * 0.76f, 0);
		
		// --- Keybindings Sub-Tab Content ---
		_keyMovementContent = new Container();
		_keyMovementContent.setBackground(null);
		_keyActionsContent = new Container();
		_keyActionsContent.setBackground(null);
		_keyMouseContent = new Container();
		_keyMouseContent.setBackground(null);
		buildKeybindingsContent(app);
		
		// Position content containers (centered).
		positionContentContainer(_displayContent, screenCenterX, screenHeight / 0.83f);
		positionContentContainer(_audioContent, screenCenterX, screenHeight / 0.83f);
		positionContentContainer(_keyMovementContent, screenCenterX, screenHeight / 0.78f);
		positionContentContainer(_keyActionsContent, screenCenterX, screenHeight / 0.78f);
		positionContentContainer(_keyMouseContent, screenCenterX, screenHeight / 0.78f);
		
		// --- Button Row (Defaults + Back) ---
		_buttonRow = new Container();
		_buttonRow.setBackground(null);
		_buttonRow.setLayout(new SpringGridLayout(Axis.X, Axis.Y, FillMode.None, FillMode.None));
		
		final Panel defaultsButton = ButtonManager.createMenuButtonByScreenPercentage(app.getAssetManager(), "Defaults", 0.15f, 0.065f, () ->
		{
			app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
			QuestionManager.show("Reset to defaults?", this::applyDefaults, null);
		});
		_buttonRow.addChild(defaultsButton);
		_defaultsButton = defaultsButton;
		
		final Label buttonSpacer = new Label("");
		buttonSpacer.setPreferredSize(new Vector3f(screenWidth * 0.016f, 1, 0));
		_buttonRow.addChild(buttonSpacer);
		
		final Panel backButton = ButtonManager.createMenuButtonByScreenPercentage(app.getAssetManager(), "Back", 0.15f, 0.065f, () ->
		{
			app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
			app.getGameStateManager().returnToPrevious(true);
		});
		_buttonRow.addChild(backButton);
		_backButton = backButton;
		
		final float buttonRowWidth = _buttonRow.getPreferredSize().x;
		_buttonRow.setLocalTranslation(screenCenterX - (buttonRowWidth / 2f), screenHeight * 0.12f, 0);
		
		// Track screen size for rebuild detection after live display changes.
		_lastScreenWidth = (int) screenWidth;
		_lastScreenHeight = (int) screenHeight;
		
		// Reset state.
		_sfxTestCooldown = 0f;
		
		// Attach to GUI node.
		app.getGuiNode().attachChild(_background);
		app.getGuiNode().attachChild(_titleLabel);
		app.getGuiNode().attachChild(_tabRow);
		app.getGuiNode().attachChild(_displayContent);
		app.getGuiNode().attachChild(_audioContent);
		app.getGuiNode().attachChild(_keybindingsSubTabRow);
		app.getGuiNode().attachChild(_keyMovementContent);
		app.getGuiNode().attachChild(_keyActionsContent);
		app.getGuiNode().attachChild(_keyMouseContent);
		app.getGuiNode().attachChild(_buttonRow);
		
		// Show only the active tab content.
		showTab(_activeTab);
		
		// Build special navigation slots.
		buildSpecialSlots(app);
		
		// Assemble the flat focus list.
		rebuildFocusList();
	}
	
	/**
	 * Remove all GUI elements from the scene.
	 */
	private void detachAllGui()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		
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
		if (_tabRow != null)
		{
			app.getGuiNode().detachChild(_tabRow);
			_tabRow = null;
		}
		if (_displayContent != null)
		{
			app.getGuiNode().detachChild(_displayContent);
			_displayContent = null;
		}
		if (_audioContent != null)
		{
			app.getGuiNode().detachChild(_audioContent);
			_audioContent = null;
		}
		if (_keybindingsSubTabRow != null)
		{
			app.getGuiNode().detachChild(_keybindingsSubTabRow);
			_keybindingsSubTabRow = null;
		}
		if (_keyMovementContent != null)
		{
			app.getGuiNode().detachChild(_keyMovementContent);
			_keyMovementContent = null;
		}
		if (_keyActionsContent != null)
		{
			app.getGuiNode().detachChild(_keyActionsContent);
			_keyActionsContent = null;
		}
		if (_keyMouseContent != null)
		{
			app.getGuiNode().detachChild(_keyMouseContent);
			_keyMouseContent = null;
		}
		if (_buttonRow != null)
		{
			app.getGuiNode().detachChild(_buttonRow);
			_buttonRow = null;
		}
		
		_tabDisplayButton = null;
		_tabAudioButton = null;
		_tabKeybindingsButton = null;
		
		_subTabMovementButton = null;
		_subTabActionsButton = null;
		_subTabMouseButton = null;
		
		_masterSlider = null;
		_musicSlider = null;
		_sfxSlider = null;
		_masterValueLabel = null;
		_musicValueLabel = null;
		_sfxValueLabel = null;
		_masterVolumeRef = null;
		_musicVolumeRef = null;
		_sfxVolumeRef = null;
		
		_resolutionValueLabel = null;
		_fullscreenToggle = null;
		_renderDistanceSlider = null;
		_renderDistanceValueLabel = null;
		_renderDistanceRef = null;
		_showHighlightToggle = null;
		_showCrosshairToggle = null;
		_showStatsToggle = null;
		_showFpsToggle = null;
		
		_keybindButtons.clear();
		_mouseBindButtons.clear();
		_listeningAction = null;
		_listeningButton = null;
		_listeningForMouse = false;
		
		// Clear navigation state.
		_displayContentSlots.clear();
		_audioContentSlots.clear();
		_keyMovementSlots.clear();
		_keyActionsSlots.clear();
		_keyMouseSlots.clear();
		_tabRowSlot = null;
		_subTabRowSlot = null;
		_bottomRowSlot = null;
		_defaultsButton = null;
		_backButton = null;
	}
	
	// ========== TAB CONTENT BUILDERS ==========
	
	/**
	 * Build the Display tab content: resolution, fullscreen, render distance, stats, fps.
	 */
	private void buildDisplayContent(SimpleCraft app, SettingsManager settings, float sliderWidth)
	{
		_displayContentSlots.clear();
		
		// Resolution selector.
		_resolutionIndex = settings.getResolutionIndex();
		if (_resolutionIndex < 0)
		{
			// Find closest match or default to last available.
			_resolutionIndex = SettingsManager.getAvailableResolutions().length - 1;
		}
		_resolutionValueLabel = createValueLabel(formatResolution(_resolutionIndex));
		final Label resLabel = createNameLabel(app, "Resolution");
		_displayContent.addChild(createResolutionRow(app, resLabel));
		// @formatter:off
		_displayContentSlots.add(MenuNavigationManager.labelSlot(resLabel,
			() -> { app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH); cycleResolution(-1); },
			() -> { app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH); cycleResolution(1); },
			null));
		// @formatter:on
		addRowSpacer(_displayContent);
		
		// Fullscreen toggle.
		_fullscreenToggle = createToggleButton(settings.isFullscreen());
		_fullscreenToggle.addClickCommands(source ->
		{
			app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
			final boolean newValue = !settings.isFullscreen();
			settings.setFullscreen(newValue);
			updateToggleButton(_fullscreenToggle, newValue);
			WindowDisplayManager.applyCurrentSettings();
		});
		final Label fsLabel = createNameLabel(app, "Fullscreen");
		_displayContent.addChild(createToggleRow(app, fsLabel, _fullscreenToggle));
		final Runnable toggleFullscreen = () ->
		{
			app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
			final boolean newValue = !settings.isFullscreen();
			settings.setFullscreen(newValue);
			updateToggleButton(_fullscreenToggle, newValue);
			WindowDisplayManager.applyCurrentSettings();
		};
		_displayContentSlots.add(MenuNavigationManager.labelSlot(fsLabel, toggleFullscreen, toggleFullscreen, toggleFullscreen));
		addRowSpacer(_displayContent);
		
		// Render Distance slider.
		_renderDistanceSlider = new Slider(Axis.X);
		_renderDistanceSlider.setPreferredSize(new Vector3f(sliderWidth, _sliderHeight, 0));
		_renderDistanceSlider.getModel().setMinimum(SettingsManager.MIN_RENDER_DISTANCE);
		_renderDistanceSlider.getModel().setMaximum(SettingsManager.MAX_RENDER_DISTANCE);
		_renderDistanceSlider.getModel().setValue(settings.getDisplayRenderDistance());
		_renderDistanceValueLabel = createValueLabel(String.valueOf(settings.getDisplayRenderDistance()));
		final Label rdLabel = createNameLabel(app, "Render Distance");
		_displayContent.addChild(createSliderRow(app, rdLabel, _renderDistanceSlider, _renderDistanceValueLabel));
		// @formatter:off
		_displayContentSlots.add(MenuNavigationManager.labelSlot(rdLabel,
			() -> { app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH); adjustSlider(_renderDistanceSlider, -1); },
			() -> { app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH); adjustSlider(_renderDistanceSlider, 1); },
			null));
		// @formatter:on
		addRowSpacer(_displayContent);
		
		// Show Highlight toggle.
		_showHighlightToggle = createToggleButton(settings.isShowHighlight());
		_showHighlightToggle.addClickCommands(source ->
		{
			app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
			final boolean newValue = !settings.isShowHighlight();
			settings.setShowHighlight(newValue);
			updateToggleButton(_showHighlightToggle, newValue);
		});
		final Label wfLabel = createNameLabel(app, "Block Highlight");
		_displayContent.addChild(createToggleRow(app, wfLabel, _showHighlightToggle));
		final Runnable toggleHighlight = () ->
		{
			app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
			final boolean newValue = !settings.isShowHighlight();
			settings.setShowHighlight(newValue);
			updateToggleButton(_showHighlightToggle, newValue);
		};
		_displayContentSlots.add(MenuNavigationManager.labelSlot(wfLabel, toggleHighlight, toggleHighlight, toggleHighlight));
		addRowSpacer(_displayContent);
		
		// Show Crosshair toggle.
		_showCrosshairToggle = createToggleButton(settings.isShowCrosshair());
		_showCrosshairToggle.addClickCommands(source ->
		{
			app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
			final boolean newValue = !settings.isShowCrosshair();
			settings.setShowCrosshair(newValue);
			updateToggleButton(_showCrosshairToggle, newValue);
		});
		final Label chLabel = createNameLabel(app, "Show Crosshair");
		_displayContent.addChild(createToggleRow(app, chLabel, _showCrosshairToggle));
		final Runnable toggleCrosshair = () ->
		{
			app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
			final boolean newValue = !settings.isShowCrosshair();
			settings.setShowCrosshair(newValue);
			updateToggleButton(_showCrosshairToggle, newValue);
		};
		_displayContentSlots.add(MenuNavigationManager.labelSlot(chLabel, toggleCrosshair, toggleCrosshair, toggleCrosshair));
		addRowSpacer(_displayContent);
		
		// Show Stats toggle.
		_showStatsToggle = createToggleButton(settings.isShowStats());
		_showStatsToggle.addClickCommands(source ->
		{
			app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
			final boolean newValue = !settings.isShowStats();
			settings.setShowStats(newValue);
			updateToggleButton(_showStatsToggle, newValue);
			app.setDisplayStatView(newValue);
		});
		final Label ssLabel = createNameLabel(app, "Show Stats");
		_displayContent.addChild(createToggleRow(app, ssLabel, _showStatsToggle));
		final Runnable toggleStats = () ->
		{
			app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
			final boolean newValue = !settings.isShowStats();
			settings.setShowStats(newValue);
			updateToggleButton(_showStatsToggle, newValue);
			app.setDisplayStatView(newValue);
		};
		_displayContentSlots.add(MenuNavigationManager.labelSlot(ssLabel, toggleStats, toggleStats, toggleStats));
		addRowSpacer(_displayContent);
		
		// Show FPS toggle.
		_showFpsToggle = createToggleButton(settings.isShowFps());
		_showFpsToggle.addClickCommands(source ->
		{
			app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
			final boolean newValue = !settings.isShowFps();
			settings.setShowFps(newValue);
			updateToggleButton(_showFpsToggle, newValue);
			app.setDisplayFps(newValue);
		});
		final Label fpsLabel = createNameLabel(app, "Show FPS");
		_displayContent.addChild(createToggleRow(app, fpsLabel, _showFpsToggle));
		final Runnable toggleFps = () ->
		{
			app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
			final boolean newValue = !settings.isShowFps();
			settings.setShowFps(newValue);
			updateToggleButton(_showFpsToggle, newValue);
			app.setDisplayFps(newValue);
		};
		_displayContentSlots.add(MenuNavigationManager.labelSlot(fpsLabel, toggleFps, toggleFps, toggleFps));
		addRowSpacer(_displayContent);
		
		// Setup versioned references and style sliders.
		_renderDistanceRef = _renderDistanceSlider.getModel().createReference();
		styleSliderComponents(_renderDistanceSlider);
	}
	
	/**
	 * Build the Audio tab content: master, music, sfx volume sliders.
	 */
	private void buildAudioContent(SimpleCraft app, SettingsManager settings, float sliderWidth)
	{
		_audioContentSlots.clear();
		
		// Master Volume.
		_masterSlider = new Slider(Axis.X);
		_masterSlider.setPreferredSize(new Vector3f(sliderWidth, _sliderHeight, 0));
		_masterSlider.getModel().setMinimum(0);
		_masterSlider.getModel().setMaximum(100);
		_masterSlider.getModel().setValue(settings.getMasterVolume() * 100.0);
		_masterValueLabel = createValueLabel(String.format("%.2f", settings.getMasterVolume()));
		final Label masterLabel = createNameLabel(app, "Master Volume");
		_audioContent.addChild(createSliderRow(app, masterLabel, _masterSlider, _masterValueLabel));
		// @formatter:off
		_audioContentSlots.add(MenuNavigationManager.labelSlot(masterLabel,
			() -> { app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH); adjustSlider(_masterSlider, -1); },
			() -> { app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH); adjustSlider(_masterSlider, 1); },
			null));
		// @formatter:on
		addRowSpacer(_audioContent);
		
		// Music Volume.
		_musicSlider = new Slider(Axis.X);
		_musicSlider.setPreferredSize(new Vector3f(sliderWidth, _sliderHeight, 0));
		_musicSlider.getModel().setMinimum(0);
		_musicSlider.getModel().setMaximum(100);
		_musicSlider.getModel().setValue(settings.getMusicVolume() * 100.0);
		_musicValueLabel = createValueLabel(String.format("%.2f", settings.getMusicVolume()));
		final Label musicLabel = createNameLabel(app, "Music Volume");
		_audioContent.addChild(createSliderRow(app, musicLabel, _musicSlider, _musicValueLabel));
		// @formatter:off
		_audioContentSlots.add(MenuNavigationManager.labelSlot(musicLabel,
			() -> { app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH); adjustSlider(_musicSlider, -1); },
			() -> { app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH); adjustSlider(_musicSlider, 1); },
			null));
		// @formatter:on
		addRowSpacer(_audioContent);
		
		// SFX Volume.
		_sfxSlider = new Slider(Axis.X);
		_sfxSlider.setPreferredSize(new Vector3f(sliderWidth, _sliderHeight, 0));
		_sfxSlider.getModel().setMinimum(0);
		_sfxSlider.getModel().setMaximum(100);
		_sfxSlider.getModel().setValue(settings.getSfxVolume() * 100.0);
		_sfxValueLabel = createValueLabel(String.format("%.2f", settings.getSfxVolume()));
		final Label sfxLabel = createNameLabel(app, "SFX Volume");
		_audioContent.addChild(createSliderRow(app, sfxLabel, _sfxSlider, _sfxValueLabel));
		// @formatter:off
		_audioContentSlots.add(MenuNavigationManager.labelSlot(sfxLabel,
			() -> { app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH); adjustSlider(_sfxSlider, -1); },
			() -> { app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH); adjustSlider(_sfxSlider, 1); },
			null));
		// @formatter:on
		
		// Setup versioned references and style sliders.
		_masterVolumeRef = _masterSlider.getModel().createReference();
		_musicVolumeRef = _musicSlider.getModel().createReference();
		_sfxVolumeRef = _sfxSlider.getModel().createReference();
		styleSliderComponents(_masterSlider);
		styleSliderComponents(_musicSlider);
		styleSliderComponents(_sfxSlider);
	}
	
	/**
	 * Build the Keybindings sub-tab contents: Movement, Actions, and Mouse.<br>
	 * Rebindable rows show a clickable button that enters listening mode for key reassignment.<br>
	 * Fixed mouse rows are read-only and displayed in a muted color.
	 */
	private void buildKeybindingsContent(SimpleCraft app)
	{
		final GameInputManager input = app.getGameInputManager();
		
		_keybindButtons.clear();
		_mouseBindButtons.clear();
		_keyMovementSlots.clear();
		_keyActionsSlots.clear();
		_keyMouseSlots.clear();
		
		// --- Rebindable Keyboard Actions (routed to the correct sub-tab container) ---
		for (String[] entry : GameInputManager.BINDABLE_ACTIONS)
		{
			final String action = entry[0];
			final String displayName = entry[1];
			final String section = entry[2];
			
			// Determine which sub-tab container and slot list this action belongs to.
			final Container target;
			final List<NavigationSlot> targetSlots;
			if ("Movement".equals(section))
			{
				target = _keyMovementContent;
				targetSlots = _keyMovementSlots;
			}
			else
			{
				target = _keyActionsContent;
				targetSlots = _keyActionsSlots;
			}
			
			// Create the key button for this action.
			final String keyName = GameInputManager.getKeyName(input.getKeyCode(action));
			final Button keyButton = createKeyButton(app, keyName);
			_keybindButtons.put(action, keyButton);
			
			// Click handler: start listening for a new key.
			final String actionRef = action;
			keyButton.addClickCommands(source ->
			{
				app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
				startListening(actionRef, keyButton, false);
			});
			
			final Label nameLabel = createKeybindNameLabel(app, displayName);
			target.addChild(createKeybindingRow(app, nameLabel, keyButton));
			addRowSpacer(target);
			
			// Focus slot: Enter starts listening.
			targetSlots.add(MenuNavigationManager.labelSlot(nameLabel, null, null, () ->
			{
				app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
				startListening(actionRef, keyButton, false);
			}));
		}
		
		// --- Mouse-Rebindable Actions ---
		for (String[] entry : GameInputManager.MOUSE_BINDABLE_ACTIONS)
		{
			final String action = entry[0];
			final String displayName = entry[1];
			
			final String buttonName = GameInputManager.getMouseButtonName(input.getMouseCode(action));
			final Button mouseButton = createKeyButton(app, buttonName);
			_mouseBindButtons.put(action, mouseButton);
			
			final String actionRef = action;
			mouseButton.addClickCommands(source ->
			{
				app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
				startListening(actionRef, mouseButton, true);
			});
			
			final Label nameLabel = createKeybindNameLabel(app, displayName);
			_keyMouseContent.addChild(createKeybindingRow(app, nameLabel, mouseButton));
			addRowSpacer(_keyMouseContent);
			
			// Focus slot: Enter starts listening.
			_keyMouseSlots.add(MenuNavigationManager.labelSlot(nameLabel, null, null, () ->
			{
				app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
				startListening(actionRef, mouseButton, true);
			}));
		}
		
		// --- Fixed Mouse / System Bindings (styled but not clickable, no focus slots) ---
		for (String[] entry : GameInputManager.FIXED_BINDINGS)
		{
			final String displayName = entry[0];
			final String keyText = entry[1];
			_keyMouseContent.addChild(createFixedKeybindingRow(app, displayName, keyText));
			addRowSpacer(_keyMouseContent);
		}
		
		updateSubTabStyles();
	}
	
	// ========== KEYBINDING LISTENING ==========
	
	/**
	 * Enter listening mode for a keybinding or mouse binding. The button text changes to "..."<br>
	 * and a RawInputListener captures the next key press or mouse button press.
	 * @param action The action to rebind
	 * @param keyButton The UI button being edited
	 * @param mouseMode true to listen for mouse buttons, false for keyboard keys
	 */
	private void startListening(String action, Button keyButton, boolean mouseMode)
	{
		// Cancel any existing listen first.
		cancelListening();
		
		_listeningAction = action;
		_listeningButton = keyButton;
		_listeningForMouse = mouseMode;
		_listenStartTime = System.currentTimeMillis();
		_navigation.setLocked(true);
		_listeningButton.setText("...");
		_listeningButton.setColor(KEY_LISTENING_COLOR);
		
		final SimpleCraft app = SimpleCraft.getInstance();
		
		_rawInputListener = new RawInputListener()
		{
			@Override
			public void onKeyEvent(KeyInputEvent event)
			{
				if (!event.isPressed())
				{
					return;
				}
				
				final int keyCode = event.getKeyCode();
				
				// Escape cancels the rebind in both modes.
				if (keyCode == KeyInput.KEY_ESCAPE)
				{
					app.enqueue(() ->
					{
						cancelListening();
						return null;
					});
					return;
				}
				
				// Only accept keyboard keys in keyboard mode.
				if (_listeningForMouse)
				{
					return;
				}
				
				if (!GameInputManager.isValidBindingKey(keyCode))
				{
					return;
				}
				
				app.enqueue(() ->
				{
					applyKeyRebind(keyCode);
					return null;
				});
			}
			
			@Override
			public void onMouseButtonEvent(MouseButtonEvent event)
			{
				// Only accept mouse buttons in mouse mode.
				if (!_listeningForMouse || !event.isPressed())
				{
					return;
				}
				
				// Ignore clicks within 150ms of starting listen (prevents the triggering click).
				if ((System.currentTimeMillis() - _listenStartTime) < 150)
				{
					return;
				}
				
				final int buttonIndex = event.getButtonIndex();
				if (!GameInputManager.isValidMouseButton(buttonIndex))
				{
					return;
				}
				
				app.enqueue(() ->
				{
					applyMouseRebind(buttonIndex);
					return null;
				});
			}
			
			@Override
			public void beginInput()
			{
			}
			
			@Override
			public void endInput()
			{
			}
			
			@Override
			public void onMouseMotionEvent(MouseMotionEvent event)
			{
			}
			
			@Override
			public void onJoyAxisEvent(JoyAxisEvent event)
			{
			}
			
			@Override
			public void onJoyButtonEvent(JoyButtonEvent event)
			{
			}
			
			@Override
			public void onTouchEvent(TouchEvent event)
			{
			}
		};
		
		app.getInputManager().addRawInputListener(_rawInputListener);
	}
	
	/**
	 * Apply a keyboard rebind: update GameInputManager, refresh button text, handle swaps.
	 */
	private void applyKeyRebind(int newKeyCode)
	{
		if (_listeningAction == null)
		{
			return;
		}
		
		final SimpleCraft app = SimpleCraft.getInstance();
		final GameInputManager input = app.getGameInputManager();
		
		final String action = _listeningAction;
		final Button button = _listeningButton;
		
		// Perform the remap (returns the swapped action, if any).
		final String swappedAction = input.remapKey(action, newKeyCode);
		
		// Stop listening first so we can update UI cleanly.
		removeRawListener();
		_listeningAction = null;
		_listeningButton = null;
		_navigation.setLocked(false);
		
		// Update the button we were editing.
		button.setText(GameInputManager.getKeyName(newKeyCode));
		button.setColor(KEY_BUTTON_COLOR);
		
		// If a swap occurred, update the other button too.
		if (swappedAction != null)
		{
			final Button swappedButton = _keybindButtons.get(swappedAction);
			if (swappedButton != null)
			{
				swappedButton.setText(GameInputManager.getKeyName(input.getKeyCode(swappedAction)));
			}
		}
		
		app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
	}
	
	/**
	 * Apply a mouse button rebind: update GameInputManager, refresh button text, handle swaps.
	 */
	private void applyMouseRebind(int newButtonCode)
	{
		if (_listeningAction == null)
		{
			return;
		}
		
		final SimpleCraft app = SimpleCraft.getInstance();
		final GameInputManager input = app.getGameInputManager();
		
		final String action = _listeningAction;
		final Button button = _listeningButton;
		
		final String swappedAction = input.remapMouseButton(action, newButtonCode);
		
		removeRawListener();
		_listeningAction = null;
		_listeningButton = null;
		_navigation.setLocked(false);
		
		button.setText(GameInputManager.getMouseButtonName(newButtonCode));
		button.setColor(KEY_BUTTON_COLOR);
		
		if (swappedAction != null)
		{
			final Button swappedButton = _mouseBindButtons.get(swappedAction);
			if (swappedButton != null)
			{
				swappedButton.setText(GameInputManager.getMouseButtonName(input.getMouseCode(swappedAction)));
			}
		}
		
		app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
	}
	
	/**
	 * Cancel any active listening mode, restoring the button to its current binding.
	 */
	private void cancelListening()
	{
		if (_listeningAction == null)
		{
			return;
		}
		
		removeRawListener();
		
		final SimpleCraft app = SimpleCraft.getInstance();
		if (_listeningForMouse)
		{
			final int currentCode = app.getGameInputManager().getMouseCode(_listeningAction);
			_listeningButton.setText(GameInputManager.getMouseButtonName(currentCode));
		}
		else
		{
			final int currentCode = app.getGameInputManager().getKeyCode(_listeningAction);
			_listeningButton.setText(GameInputManager.getKeyName(currentCode));
		}
		_listeningButton.setColor(KEY_BUTTON_COLOR);
		
		_listeningAction = null;
		_listeningButton = null;
		_listeningForMouse = false;
		_navigation.setLocked(false);
	}
	
	/**
	 * Remove the raw input listener if active.
	 */
	private void removeRawListener()
	{
		if (_rawInputListener != null)
		{
			SimpleCraft.getInstance().getInputManager().removeRawInputListener(_rawInputListener);
			_rawInputListener = null;
		}
	}
	
	/**
	 * Refresh all keybinding and mouse binding button labels (used after defaults reset).
	 */
	private void refreshKeybindButtons()
	{
		final GameInputManager input = SimpleCraft.getInstance().getGameInputManager();
		for (Entry<String, Button> entry : _keybindButtons.entrySet())
		{
			final int keyCode = input.getKeyCode(entry.getKey());
			entry.getValue().setText(GameInputManager.getKeyName(keyCode));
			entry.getValue().setColor(KEY_BUTTON_COLOR);
		}
		for (Entry<String, Button> entry : _mouseBindButtons.entrySet())
		{
			final int buttonCode = input.getMouseCode(entry.getKey());
			entry.getValue().setText(GameInputManager.getMouseButtonName(buttonCode));
			entry.getValue().setColor(KEY_BUTTON_COLOR);
		}
	}
	
	// ========== KEYBINDING UI FACTORY ==========
	
	/**
	 * Create a clickable key button for rebindable actions.
	 */
	private Button createKeyButton(SimpleCraft app, String keyName)
	{
		final Button button = new Button(keyName);
		button.setFont(FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_REGULAR_PATH, Font.PLAIN, _valueFontSize));
		button.setFontSize(_valueFontSize);
		button.setColor(KEY_BUTTON_COLOR);
		button.setBackground(null);
		button.setTextHAlignment(HAlignment.Left);
		button.setTextVAlignment(VAlignment.Center);
		button.setPreferredSize(new Vector3f(_keybindLabelWidth, _sliderHeight, 0));
		return button;
	}
	
	/**
	 * Create a row with a right-aligned action name and a left-aligned rebindable key button,<br>
	 * centered on screen with a gap between them.
	 * @param app The application instance
	 * @param nameLabel Pre-created name label for the row
	 * @param keyButton The rebindable key Button
	 * @return A configured Container
	 */
	private Container createKeybindingRow(SimpleCraft app, Label nameLabel, Button keyButton)
	{
		final Container row = new Container();
		row.setBackground(null);
		row.setLayout(new SpringGridLayout(Axis.X, Axis.Y, FillMode.None, FillMode.None));
		
		row.addChild(nameLabel);
		
		final Label gap = new Label("");
		gap.setPreferredSize(new Vector3f(_keybindGapWidth, 1, 0));
		row.addChild(gap);
		
		row.addChild(keyButton);
		
		return row;
	}
	
	/**
	 * Create a row for a fixed (non-rebindable) binding, right-aligned name with muted key text.
	 */
	private Container createFixedKeybindingRow(SimpleCraft app, String actionName, String keyText)
	{
		final Container row = new Container();
		row.setBackground(null);
		row.setLayout(new SpringGridLayout(Axis.X, Axis.Y, FillMode.None, FillMode.None));
		
		final Label nameLabel = new Label(actionName);
		nameLabel.setFont(FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_REGULAR_PATH, Font.PLAIN, _labelFontSize));
		nameLabel.setFontSize(_labelFontSize);
		nameLabel.setColor(new ColorRGBA(0.7f, 0.7f, 0.7f, 1f));
		nameLabel.setTextHAlignment(HAlignment.Right);
		nameLabel.setTextVAlignment(VAlignment.Center);
		nameLabel.setPreferredSize(new Vector3f(_keybindLabelWidth, _sliderHeight, 0));
		row.addChild(nameLabel);
		
		final Label gap = new Label("");
		gap.setPreferredSize(new Vector3f(_keybindGapWidth, 1, 0));
		row.addChild(gap);
		
		final Label keyLabel = new Label(keyText);
		keyLabel.setFont(FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_REGULAR_PATH, Font.PLAIN, _valueFontSize));
		keyLabel.setFontSize(_valueFontSize);
		keyLabel.setColor(FIXED_KEY_COLOR);
		keyLabel.setTextHAlignment(HAlignment.Left);
		keyLabel.setTextVAlignment(VAlignment.Center);
		keyLabel.setPreferredSize(new Vector3f(_keybindLabelWidth, _sliderHeight, 0));
		row.addChild(keyLabel);
		
		return row;
	}
	
	// ========== TAB MANAGEMENT ==========
	
	/**
	 * Switch to a tab and show its content, hiding all others.
	 * @param tab The tab index to show
	 */
	private void showTab(int tab)
	{
		// Cancel any active keybinding listen when switching away.
		if (_activeTab == TAB_KEYBINDINGS && tab != TAB_KEYBINDINGS)
		{
			cancelListening();
		}
		
		_activeTab = tab;
		
		_displayContent.setCullHint(tab == TAB_DISPLAY ? CullHint.Inherit : CullHint.Always);
		_audioContent.setCullHint(tab == TAB_AUDIO ? CullHint.Inherit : CullHint.Always);
		
		// Keybindings: show sub-tab row and active sub-tab content.
		final boolean keybindings = tab == TAB_KEYBINDINGS;
		_keybindingsSubTabRow.setCullHint(keybindings ? CullHint.Inherit : CullHint.Always);
		if (keybindings)
		{
			showSubTab(_activeSubTab);
		}
		else
		{
			_keyMovementContent.setCullHint(CullHint.Always);
			_keyActionsContent.setCullHint(CullHint.Always);
			_keyMouseContent.setCullHint(CullHint.Always);
		}
		
		updateTabStyles();
		rebuildFocusList();
	}
	
	/**
	 * Update tab button colors to reflect the active tab.
	 */
	private void updateTabStyles()
	{
		if (_tabDisplayButton != null)
		{
			_tabDisplayButton.setColor(_activeTab == TAB_DISPLAY ? TAB_ACTIVE_COLOR : TAB_INACTIVE_COLOR);
		}
		if (_tabAudioButton != null)
		{
			_tabAudioButton.setColor(_activeTab == TAB_AUDIO ? TAB_ACTIVE_COLOR : TAB_INACTIVE_COLOR);
		}
		if (_tabKeybindingsButton != null)
		{
			_tabKeybindingsButton.setColor(_activeTab == TAB_KEYBINDINGS ? TAB_ACTIVE_COLOR : TAB_INACTIVE_COLOR);
		}
	}
	
	/**
	 * Switch to a keybinding sub-tab, showing its content and hiding the others.
	 * @param subTab The sub-tab index to show
	 */
	private void showSubTab(int subTab)
	{
		cancelListening();
		
		_activeSubTab = subTab;
		
		_keyMovementContent.setCullHint(subTab == SUB_TAB_MOVEMENT ? CullHint.Inherit : CullHint.Always);
		_keyActionsContent.setCullHint(subTab == SUB_TAB_ACTIONS ? CullHint.Inherit : CullHint.Always);
		_keyMouseContent.setCullHint(subTab == SUB_TAB_MOUSE ? CullHint.Inherit : CullHint.Always);
		
		updateSubTabStyles();
		rebuildFocusList();
	}
	
	/**
	 * Update keybinding sub-tab button colors to reflect the active sub-tab.
	 */
	private void updateSubTabStyles()
	{
		if (_subTabMovementButton != null)
		{
			_subTabMovementButton.setColor(_activeSubTab == SUB_TAB_MOVEMENT ? TAB_ACTIVE_COLOR : TAB_INACTIVE_COLOR);
		}
		if (_subTabActionsButton != null)
		{
			_subTabActionsButton.setColor(_activeSubTab == SUB_TAB_ACTIONS ? TAB_ACTIVE_COLOR : TAB_INACTIVE_COLOR);
		}
		if (_subTabMouseButton != null)
		{
			_subTabMouseButton.setColor(_activeSubTab == SUB_TAB_MOUSE ? TAB_ACTIVE_COLOR : TAB_INACTIVE_COLOR);
		}
	}
	
	// ========== SLIDER POLLING ==========
	
	/**
	 * Poll audio sliders for changes and apply in real-time.
	 */
	private void pollAudioSliders(SimpleCraft app)
	{
		final AudioManager audio = app.getAudioManager();
		final SettingsManager settings = app.getSettingsManager();
		
		if (_masterVolumeRef != null && _masterVolumeRef.update())
		{
			final float value = (float) (_masterVolumeRef.get() / 100.0);
			audio.setMasterVolume(value);
			settings.setMasterVolume(value);
			_masterValueLabel.setText(String.format("%.2f", value));
		}
		
		if (_musicVolumeRef != null && _musicVolumeRef.update())
		{
			final float value = (float) (_musicVolumeRef.get() / 100.0);
			audio.setMusicVolume(value);
			settings.setMusicVolume(value);
			_musicValueLabel.setText(String.format("%.2f", value));
		}
		
		if (_sfxVolumeRef != null && _sfxVolumeRef.update())
		{
			final float value = (float) (_sfxVolumeRef.get() / 100.0);
			audio.setSfxVolume(value);
			settings.setSfxVolume(value);
			_sfxValueLabel.setText(String.format("%.2f", value));
			
			if (_sfxTestCooldown <= 0f)
			{
				audio.playSfx(AudioManager.UI_CLICK_SFX_PATH);
				_sfxTestCooldown = SFX_TEST_INTERVAL;
			}
		}
	}
	
	/**
	 * Poll display sliders for changes.
	 */
	private void pollDisplaySliders(SimpleCraft app)
	{
		final SettingsManager settings = app.getSettingsManager();
		
		if (_renderDistanceRef != null && _renderDistanceRef.update())
		{
			final int value = (int) Math.round(_renderDistanceRef.get());
			settings.setRenderDistance(value);
			_renderDistanceValueLabel.setText(String.valueOf(value));
			
			final double currentModelValue = _renderDistanceSlider.getModel().getValue();
			if (Math.abs(currentModelValue - value) > 0.01)
			{
				_renderDistanceSlider.getModel().setValue(value);
			}
		}
	}
	
	// ========== UI FACTORY HELPERS ==========
	
	/**
	 * Create a tab button label with click handling.
	 * @param app The application instance
	 * @param text The tab label text
	 * @param tabIndex The tab index this button activates
	 * @return A Label configured as a clickable tab button
	 */
	private Label createTabButton(SimpleCraft app, String text, int tabIndex)
	{
		final Label tab = new Label(text);
		tab.setFont(FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_LINOCUT_PATH, Font.PLAIN, _tabFontSize));
		tab.setFontSize(_tabFontSize);
		tab.setColor(TAB_INACTIVE_COLOR);
		tab.setTextHAlignment(HAlignment.Center);
		tab.setPreferredSize(new Vector3f(app.getCamera().getWidth() * 0.12f, _sliderHeight * 1.5f, 0));
		
		CursorEventControl.addListenersToSpatial(tab, new DefaultCursorListener()
		{
			@Override
			public void cursorButtonEvent(CursorButtonEvent event, Spatial target, Spatial capture)
			{
				if (event.isPressed())
				{
					app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
					showTab(tabIndex);
				}
			}
		});
		
		return tab;
	}
	
	/**
	 * Create a spacer between tab buttons.
	 */
	private Label createTabSpacer()
	{
		final Label spacer = new Label("|");
		final SimpleCraft app = SimpleCraft.getInstance();
		spacer.setFont(FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_REGULAR_PATH, Font.PLAIN, _tabFontSize));
		spacer.setFontSize(_tabFontSize);
		spacer.setColor(new ColorRGBA(0.3f, 0.3f, 0.3f, 0.6f));
		spacer.setTextHAlignment(HAlignment.Center);
		spacer.setPreferredSize(new Vector3f(app.getCamera().getWidth() * 0.03f, _sliderHeight * 1.5f, 0));
		return spacer;
	}
	
	/**
	 * Create a keybinding sub-tab button label with click handling.
	 * @param app The application instance
	 * @param text The sub-tab label text
	 * @param subTabIndex The sub-tab index this button activates
	 * @return A Label configured as a clickable sub-tab button
	 */
	private Label createSubTabButton(SimpleCraft app, String text, int subTabIndex)
	{
		final int subTabFontSize = Math.max(10, (int) (_tabFontSize * 0.85f));
		final Label tab = new Label(text);
		tab.setFont(FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_LINOCUT_PATH, Font.PLAIN, subTabFontSize));
		tab.setFontSize(subTabFontSize);
		tab.setColor(TAB_INACTIVE_COLOR);
		tab.setTextHAlignment(HAlignment.Center);
		tab.setPreferredSize(new Vector3f(app.getCamera().getWidth() * 0.1f, _sliderHeight * 1.3f, 0));
		
		CursorEventControl.addListenersToSpatial(tab, new DefaultCursorListener()
		{
			@Override
			public void cursorButtonEvent(CursorButtonEvent event, Spatial target, Spatial capture)
			{
				if (event.isPressed())
				{
					app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
					showSubTab(subTabIndex);
				}
			}
		});
		
		return tab;
	}
	
	/**
	 * Create a spacer between keybinding sub-tab buttons.
	 */
	private Label createSubTabSpacer()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		final int subTabFontSize = Math.max(10, (int) (_tabFontSize * 0.85f));
		final Label spacer = new Label(FontManager.SYMBOL_DOT);
		spacer.setFont(FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_REGULAR_PATH, Font.PLAIN, subTabFontSize));
		spacer.setFontSize(subTabFontSize);
		spacer.setColor(new ColorRGBA(0.3f, 0.3f, 0.3f, 0.6f));
		spacer.setTextHAlignment(HAlignment.Center);
		spacer.setPreferredSize(new Vector3f(app.getCamera().getWidth() * 0.02f, _sliderHeight * 1.3f, 0));
		return spacer;
	}
	
	/**
	 * Style a slider's sub-components with custom font, symbols, and click SFX.
	 * @param slider The Lemur slider to style
	 */
	private void styleSliderComponents(Slider slider)
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		final int thumbFontSize = Math.max(10, (int) (_sliderButtonFontSize * 0.45f));
		final BitmapFont thumbFont = FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_REGULAR_PATH, Font.PLAIN, thumbFontSize);
		
		final Button thumbButton = slider.getThumbButton();
		thumbButton.setText(FontManager.SYMBOL_SQUARE);
		thumbButton.setFont(thumbFont);
		thumbButton.setFontSize(thumbFontSize);
		thumbButton.setColor(ColorRGBA.White);
		
		// Hide built-in arrow buttons completely.
		slider.getDecrementButton().setText("");
		slider.getDecrementButton().setPreferredSize(new Vector3f(0, 0, 0));
		slider.getDecrementButton().setBackground(null);
		slider.getIncrementButton().setText("");
		slider.getIncrementButton().setPreferredSize(new Vector3f(0, 0, 0));
		slider.getIncrementButton().setBackground(null);
	}
	
	/**
	 * Create a horizontal row containing a label, slider, and value display.
	 * @param app The application instance
	 * @param nameLabel Pre-created name label for the row
	 * @param slider The Lemur Slider control
	 * @param valueLabel The value display label
	 * @return A configured Container with all elements
	 */
	private Container createSliderRow(SimpleCraft app, Label nameLabel, Slider slider, Label valueLabel)
	{
		final Container row = new Container();
		row.setBackground(null);
		row.setLayout(new SpringGridLayout(Axis.X, Axis.Y, FillMode.None, FillMode.None));
		
		final BitmapFont arrowFont = FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_REGULAR_PATH, Font.PLAIN, _sliderButtonFontSize);
		
		row.addChild(nameLabel);
		
		// External left arrow with click-to-decrement.
		final Button leftArrow = new Button(FontManager.SYMBOL_ARROW_LEFT);
		leftArrow.setFont(arrowFont);
		leftArrow.setFontSize(_sliderButtonFontSize);
		leftArrow.setColor(ColorRGBA.White);
		leftArrow.setBackground(null);
		leftArrow.setTextHAlignment(HAlignment.Center);
		leftArrow.addClickCommands(source ->
		{
			app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
			final double newVal = Math.max(slider.getModel().getMinimum(), slider.getModel().getValue() - 1.0);
			slider.getModel().setValue(newVal);
		});
		row.addChild(leftArrow);
		
		row.addChild(slider);
		
		// SPACER BEFORE RIGHT ARROW - prevents thumb overlap.
		final Label arrowSpacer = new Label("");
		arrowSpacer.setPreferredSize(new Vector3f(13, 1, 0)); // 13px buffer.
		row.addChild(arrowSpacer);
		
		// External right arrow with click-to-increment.
		final Button rightArrow = new Button(FontManager.SYMBOL_ARROW_RIGHT);
		rightArrow.setFont(arrowFont);
		rightArrow.setFontSize(_sliderButtonFontSize);
		rightArrow.setColor(ColorRGBA.White);
		rightArrow.setBackground(null);
		rightArrow.setTextHAlignment(HAlignment.Center);
		rightArrow.addClickCommands(source ->
		{
			app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
			final double newVal = Math.min(slider.getModel().getMaximum(), slider.getModel().getValue() + 1.0);
			slider.getModel().setValue(newVal);
		});
		row.addChild(rightArrow);
		
		final Label spacer = new Label("");
		spacer.setPreferredSize(new Vector3f(15, 1, 0));
		row.addChild(spacer);
		
		row.addChild(valueLabel);
		
		return row;
	}
	
	/**
	 * Create the resolution selector row with left/right arrow buttons.
	 * @param app The application instance
	 * @param nameLabel Pre-created name label for the row
	 * @return A configured Container with label, arrows, and value display
	 */
	private Container createResolutionRow(SimpleCraft app, Label nameLabel)
	{
		final Container row = new Container();
		row.setBackground(null);
		row.setLayout(new SpringGridLayout(Axis.X, Axis.Y, FillMode.None, FillMode.None));
		
		final BitmapFont arrowFont = FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_REGULAR_PATH, Font.PLAIN, _sliderButtonFontSize);
		
		row.addChild(nameLabel);
		
		final Button leftArrow = new Button(FontManager.SYMBOL_ARROW_LEFT);
		leftArrow.setFont(arrowFont);
		leftArrow.setFontSize(_sliderButtonFontSize);
		leftArrow.setColor(ColorRGBA.White);
		leftArrow.setBackground(null);
		leftArrow.addClickCommands(source ->
		{
			app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
			cycleResolution(-1);
		});
		row.addChild(leftArrow);
		
		final Label spacerL = new Label("");
		spacerL.setPreferredSize(new Vector3f(10, 1, 0));
		row.addChild(spacerL);
		
		_resolutionValueLabel.setTextHAlignment(HAlignment.Center);
		_resolutionValueLabel.setTextVAlignment(VAlignment.Center);
		row.addChild(_resolutionValueLabel);
		
		final Label spacerR = new Label("");
		spacerR.setPreferredSize(new Vector3f(10, 1, 0));
		row.addChild(spacerR);
		
		final Button rightArrow = new Button(FontManager.SYMBOL_ARROW_RIGHT);
		rightArrow.setFont(arrowFont);
		rightArrow.setFontSize(_sliderButtonFontSize);
		rightArrow.setColor(ColorRGBA.White);
		rightArrow.setBackground(null);
		rightArrow.addClickCommands(source ->
		{
			app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
			cycleResolution(1);
		});
		row.addChild(rightArrow);
		
		return row;
	}
	
	/**
	 * Create a row with a label and a toggle button.
	 * @param app The application instance
	 * @param nameLabel Pre-created name label for the row
	 * @param toggleButton The toggle Button control
	 * @return A configured Container with label and toggle
	 */
	private Container createToggleRow(SimpleCraft app, Label nameLabel, Button toggleButton)
	{
		final Container row = new Container();
		row.setBackground(null);
		row.setLayout(new SpringGridLayout(Axis.X, Axis.Y, FillMode.None, FillMode.None));
		
		row.addChild(nameLabel);
		row.addChild(toggleButton);
		
		return row;
	}
	
	/**
	 * Create a styled name label for use in content rows.
	 * @param app The application instance
	 * @param text The label text
	 * @return A configured Label
	 */
	private Label createNameLabel(SimpleCraft app, String text)
	{
		final Label label = new Label(text);
		label.setFont(FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_REGULAR_PATH, Font.PLAIN, _labelFontSize));
		label.setFontSize(_labelFontSize);
		label.setColor(ColorRGBA.White);
		label.setTextHAlignment(HAlignment.Left);
		label.setTextVAlignment(VAlignment.Center);
		label.setPreferredSize(new Vector3f(_labelWidth, _sliderHeight, 0));
		return label;
	}
	
	/**
	 * Create a styled name label for keybinding rows (right-aligned, wider).
	 * @param app The application instance
	 * @param text The label text
	 * @return A configured Label
	 */
	private Label createKeybindNameLabel(SimpleCraft app, String text)
	{
		final Label label = new Label(text);
		label.setFont(FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_REGULAR_PATH, Font.PLAIN, _labelFontSize));
		label.setFontSize(_labelFontSize);
		label.setColor(ColorRGBA.White);
		label.setTextHAlignment(HAlignment.Right);
		label.setTextVAlignment(VAlignment.Center);
		label.setPreferredSize(new Vector3f(_keybindLabelWidth, _sliderHeight, 0));
		return label;
	}
	
	/**
	 * Create a styled toggle button showing "On" or "Off".
	 * @param initialValue The initial toggle state
	 * @return A Button configured as a toggle
	 */
	private Button createToggleButton(boolean initialValue)
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		final BitmapFont font = FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_REGULAR_PATH, Font.PLAIN, _labelFontSize);
		
		final Button button = new Button(initialValue ? "On" : "Off");
		button.setFont(font);
		button.setFontSize(_labelFontSize);
		button.setColor(initialValue ? TOGGLE_ON_COLOR : TOGGLE_OFF_COLOR);
		button.setBackground(null);
		button.setPreferredSize(new Vector3f(_valueLabelWidth, _sliderHeight, 0));
		button.setTextHAlignment(HAlignment.Center);
		button.setTextVAlignment(VAlignment.Center);
		return button;
	}
	
	/**
	 * Update a toggle button's text and color.
	 */
	private void updateToggleButton(Button button, boolean value)
	{
		button.setText(value ? "On" : "Off");
		button.setColor(value ? TOGGLE_ON_COLOR : TOGGLE_OFF_COLOR);
	}
	
	/**
	 * Create a styled value label for displaying slider or selector values.
	 */
	private Label createValueLabel(String initialText)
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		final Label label = new Label(initialText);
		label.setFont(FontManager.getFont(app.getAssetManager(), FontManager.BLUE_HIGHWAY_REGULAR_PATH, Font.PLAIN, _valueFontSize));
		label.setFontSize(_valueFontSize);
		label.setColor(new ColorRGBA(0.9f, 0.9f, 0.9f, 1f));
		label.setTextHAlignment(HAlignment.Right);
		label.setTextVAlignment(VAlignment.Center);
		label.setPreferredSize(new Vector3f(_valueLabelWidth, _sliderHeight, 0));
		return label;
	}
	
	/**
	 * Add a transparent spacer between rows.
	 */
	private void addRowSpacer(Container container)
	{
		final Label spacer = new Label("");
		spacer.setPreferredSize(new Vector3f(1, _rowSpacing, 0));
		container.addChild(spacer);
	}
	
	/**
	 * Position a content container centered on screen.
	 */
	private void positionContentContainer(Container container, float screenCenterX, float screenHeight)
	{
		final float width = container.getPreferredSize().x;
		final float height = container.getPreferredSize().y;
		container.setLocalTranslation(screenCenterX - (width / 2f), (screenHeight + height) / 2f - screenHeight * 0.06f, 0);
	}
	
	// ========== KEYBOARD NAVIGATION ==========
	
	/**
	 * Build the three special navigation slots (tab row, sub-tab row, bottom buttons).
	 */
	private void buildSpecialSlots(SimpleCraft app)
	{
		// Tab row slot: A/D cycle tabs.
		// @formatter:off
		_tabRowSlot = MenuNavigationManager.customSlot(
			() -> highlightActiveTab(true),
			() -> highlightActiveTab(false),
			() -> { app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH); cycleTabFromSlot(-1); },
			() -> { app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH); cycleTabFromSlot(1); },
			null
		);
		// @formatter:on
		
		// Sub-tab row slot: A/D cycle sub-tabs.
		// @formatter:off
		_subTabRowSlot = MenuNavigationManager.customSlot(
			() -> highlightActiveSubTab(true),
			() -> highlightActiveSubTab(false),
			() -> { app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH); cycleSubTabFromSlot(-1); },
			() -> { app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH); cycleSubTabFromSlot(1); },
			null
		);
		// @formatter:on
		
		// Bottom button slot: A/D switch between Defaults/Back, Enter activates.
		// @formatter:off
		_bottomRowSlot = MenuNavigationManager.customSlot(
			this::highlightBottomButtons,
			this::unhighlightBottomButtons,
			() -> { _bottomFocusIndex = 0; highlightBottomButtons(); },
			() -> { _bottomFocusIndex = 1; highlightBottomButtons(); },
			this::activateBottomButton
		);
		// @formatter:on
	}
	
	/**
	 * Assemble the flat focus list from the tab slot, active content slots, and bottom slot.
	 */
	private void rebuildFocusList()
	{
		_navigation.clearSlots();
		
		// Tab row always first.
		_navigation.addSlot(_tabRowSlot);
		
		// Content based on active tab.
		switch (_activeTab)
		{
			case TAB_DISPLAY:
			{
				for (NavigationSlot slot : _displayContentSlots)
				{
					_navigation.addSlot(slot);
				}
				break;
			}
			case TAB_AUDIO:
			{
				for (NavigationSlot slot : _audioContentSlots)
				{
					_navigation.addSlot(slot);
				}
				break;
			}
			case TAB_KEYBINDINGS:
			{
				_navigation.addSlot(_subTabRowSlot);
				switch (_activeSubTab)
				{
					case SUB_TAB_MOVEMENT:
					{
						for (NavigationSlot slot : _keyMovementSlots)
						{
							_navigation.addSlot(slot);
						}
						break;
					}
					case SUB_TAB_ACTIONS:
					{
						for (NavigationSlot slot : _keyActionsSlots)
						{
							_navigation.addSlot(slot);
						}
						break;
					}
					case SUB_TAB_MOUSE:
					{
						for (NavigationSlot slot : _keyMouseSlots)
						{
							_navigation.addSlot(slot);
						}
						break;
					}
				}
				break;
			}
		}
		
		// Bottom buttons always last.
		_navigation.addSlot(_bottomRowSlot);
		
		// Re-apply focus if active.
		_navigation.clampAndRefocus();
	}
	
	/**
	 * Cycle main tabs from the tab row slot and rebuild the focus list.
	 */
	private void cycleTabFromSlot(int direction)
	{
		final int tabCount = 3;
		final int newTab = ((_activeTab + direction) % tabCount + tabCount) % tabCount;
		showTab(newTab);
		// showTab calls rebuildFocusList; re-apply focus on the tab row.
		_navigation.setFocusIndex(0);
	}
	
	/**
	 * Cycle keybinding sub-tabs from the sub-tab row slot and rebuild the focus list.
	 */
	private void cycleSubTabFromSlot(int direction)
	{
		final int subTabCount = 3;
		final int newSubTab = ((_activeSubTab + direction) % subTabCount + subTabCount) % subTabCount;
		showSubTab(newSubTab);
		// showSubTab calls rebuildFocusList; re-apply focus on the sub-tab row (index 1).
		_navigation.setFocusIndex(1);
	}
	
	/**
	 * Highlight the active tab label with focus color or standard active color.
	 */
	private void highlightActiveTab(boolean focused)
	{
		final ColorRGBA activeColor = focused ? MenuNavigationManager.FOCUS_HIGHLIGHT_COLOR : TAB_ACTIVE_COLOR;
		if (_tabDisplayButton != null)
		{
			_tabDisplayButton.setColor(_activeTab == TAB_DISPLAY ? activeColor : TAB_INACTIVE_COLOR);
		}
		if (_tabAudioButton != null)
		{
			_tabAudioButton.setColor(_activeTab == TAB_AUDIO ? activeColor : TAB_INACTIVE_COLOR);
		}
		if (_tabKeybindingsButton != null)
		{
			_tabKeybindingsButton.setColor(_activeTab == TAB_KEYBINDINGS ? activeColor : TAB_INACTIVE_COLOR);
		}
	}
	
	/**
	 * Highlight the active sub-tab label with focus color or standard active color.
	 */
	private void highlightActiveSubTab(boolean focused)
	{
		final ColorRGBA activeColor = focused ? MenuNavigationManager.FOCUS_HIGHLIGHT_COLOR : TAB_ACTIVE_COLOR;
		if (_subTabMovementButton != null)
		{
			_subTabMovementButton.setColor(_activeSubTab == SUB_TAB_MOVEMENT ? activeColor : TAB_INACTIVE_COLOR);
		}
		if (_subTabActionsButton != null)
		{
			_subTabActionsButton.setColor(_activeSubTab == SUB_TAB_ACTIONS ? activeColor : TAB_INACTIVE_COLOR);
		}
		if (_subTabMouseButton != null)
		{
			_subTabMouseButton.setColor(_activeSubTab == SUB_TAB_MOUSE ? activeColor : TAB_INACTIVE_COLOR);
		}
	}
	
	/**
	 * Highlight the focused bottom button.
	 */
	private void highlightBottomButtons()
	{
		if (_defaultsButton != null)
		{
			ButtonManager.setFocused(_defaultsButton, _bottomFocusIndex == 0);
		}
		if (_backButton != null)
		{
			ButtonManager.setFocused(_backButton, _bottomFocusIndex == 1);
		}
	}
	
	/**
	 * Remove focus highlight from both bottom buttons.
	 */
	private void unhighlightBottomButtons()
	{
		if (_defaultsButton != null)
		{
			ButtonManager.setFocused(_defaultsButton, false);
		}
		if (_backButton != null)
		{
			ButtonManager.setFocused(_backButton, false);
		}
	}
	
	/**
	 * Activate the currently focused bottom button.
	 */
	private void activateBottomButton()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		if (_bottomFocusIndex == 0)
		{
			app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
			QuestionManager.show("Reset to defaults?", this::applyDefaults, null);
		}
		else
		{
			app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
			app.getGameStateManager().returnToPrevious(true);
		}
	}
	
	/**
	 * Adjust a slider by the given step, clamping to min/max.
	 * @param slider The slider to adjust
	 * @param step The amount to change (positive or negative)
	 */
	private void adjustSlider(Slider slider, double step)
	{
		final double newVal = Math.max(slider.getModel().getMinimum(), Math.min(slider.getModel().getMaximum(), slider.getModel().getValue() + step));
		slider.getModel().setValue(newVal);
	}
	
	// ========== RESOLUTION / DEFAULTS ==========
	
	/**
	 * Cycle the resolution index and apply the change immediately.
	 */
	private void cycleResolution(int direction)
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		final SettingsManager settings = app.getSettingsManager();
		final int presetCount = SettingsManager.getAvailableResolutions().length;
		
		if (presetCount == 0)
		{
			return;
		}
		
		_resolutionIndex = (_resolutionIndex + direction + presetCount) % presetCount;
		settings.setResolutionByIndex(_resolutionIndex);
		_resolutionValueLabel.setText(formatResolution(_resolutionIndex));
		
		WindowDisplayManager.applyCurrentSettings();
	}
	
	/**
	 * Format a resolution preset as "WIDTHxHEIGHT".
	 */
	private String formatResolution(int index)
	{
		final int[][] resolutions = SettingsManager.getAvailableResolutions();
		if (index < 0 || index >= resolutions.length)
		{
			return "Unknown";
		}
		
		return resolutions[index][0] + "x" + resolutions[index][1];
	}
	
	/**
	 * Reset all settings to defaults and update all UI controls.
	 */
	private void applyDefaults()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		final SettingsManager settings = app.getSettingsManager();
		final AudioManager audio = app.getAudioManager();
		
		settings.resetToDefaults();
		
		// Apply audio immediately.
		audio.setMasterVolume(settings.getMasterVolume());
		audio.setMusicVolume(settings.getMusicVolume());
		audio.setSfxVolume(settings.getSfxVolume());
		
		// Apply debug display immediately.
		app.setDisplayStatView(settings.isShowStats());
		app.setDisplayFps(settings.isShowFps());
		
		// Reset keybindings.
		cancelListening();
		app.getGameInputManager().resetKeybindings();
		refreshKeybindButtons();
		
		// Apply display settings (UI will rebuild via screen size detection).
		WindowDisplayManager.applyCurrentSettings();
	}
}
