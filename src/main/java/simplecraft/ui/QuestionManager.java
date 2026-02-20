package simplecraft.ui;

import java.awt.Font;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Quad;
import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.HAlignment;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.Panel;
import com.simsilica.lemur.component.SpringGridLayout;

import simplecraft.SimpleCraft;
import simplecraft.audio.AudioManager;

/**
 * Utility class for displaying modal yes/no question dialogs.<br>
 * Shows a centered question with two buttons over a semi-transparent backdrop.<br>
 * Only one question dialog can be active at a time; showing a new one dismisses the previous.
 * @author Pantelis Andrianakis
 * @since February 18th 2026
 */
public class QuestionManager
{
	private static final int QUESTION_FONT_SIZE = 32;
	private static final int BUTTON_SPACING = 20;
	private static final float BACKDROP_ALPHA = 0.85f;
	private static final float BACKDROP_Z = 5f;
	private static final float DIALOG_Z = 6f;
	private static final float BUTTON_WIDTH_PERCENT = 0.10f;
	private static final float BUTTON_HEIGHT_PERCENT = 0.065f;
	
	// Active dialog elements.
	private static Geometry _backdrop;
	private static Container _dialogContainer;
	
	// Keyboard navigation state.
	private static Panel _yesButton;
	private static Panel _noButton;
	private static Runnable _yesAction;
	private static Runnable _noAction;
	private static int _selectedIndex = -1; // -1 = no keyboard focus, 0 = Yes, 1 = No
	
	/**
	 * Show a modal question dialog with Yes and No buttons.<br>
	 * The dialog is centered on screen with a semi-transparent dark backdrop.<br>
	 * Any previously active dialog is dismissed before showing the new one.
	 * @param question the question text to display
	 * @param yesAction action to execute when Yes is clicked (dialog is dismissed before execution)
	 * @param noAction action to execute when No is clicked (dialog is dismissed before execution)
	 */
	public static void show(String question, Runnable yesAction, Runnable noAction)
	{
		// Dismiss any existing dialog first.
		dismiss();
		
		final SimpleCraft app = SimpleCraft.getInstance();
		final AssetManager assetManager = app.getAssetManager();
		final float screenWidth = app.getCamera().getWidth();
		final float screenHeight = app.getCamera().getHeight();
		
		// Store action references for keyboard confirmation.
		_yesAction = yesAction;
		_noAction = noAction;
		_selectedIndex = -1; // No keyboard focus until first keyboard input.
		
		// --- Semi-transparent backdrop ---
		final Quad backdropQuad = new Quad(screenWidth, screenHeight);
		_backdrop = new Geometry("QuestionBackdrop", backdropQuad);
		
		final Material backdropMaterial = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
		backdropMaterial.setColor("Color", new ColorRGBA(0, 0, 0, BACKDROP_ALPHA));
		backdropMaterial.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
		
		_backdrop.setMaterial(backdropMaterial);
		_backdrop.setLocalTranslation(0, 0, BACKDROP_Z);
		
		// --- Dialog container (holds question label and button row) ---
		_dialogContainer = new Container();
		_dialogContainer.setBackground(null);
		
		// Question label.
		final Label questionLabel = new Label(question);
		questionLabel.setFont(FontManager.getFont(assetManager, FontManager.BLUE_HIGHWAY_LINOCUT_PATH, Font.PLAIN, QUESTION_FONT_SIZE));
		questionLabel.setFontSize(QUESTION_FONT_SIZE);
		questionLabel.setColor(ColorRGBA.White);
		questionLabel.setTextHAlignment(HAlignment.Center);
		_dialogContainer.addChild(questionLabel);
		
		// Spacer between question and buttons.
		final Label spacer = new Label("");
		spacer.setPreferredSize(new Vector3f(1, BUTTON_SPACING, 0));
		_dialogContainer.addChild(spacer);
		
		// Button row container (horizontal layout).
		final Container buttonRow = new Container();
		buttonRow.setBackground(null);
		buttonRow.setLayout(new SpringGridLayout(Axis.X, Axis.Y, FillMode.None, FillMode.None));
		
		// Yes button.
		_yesButton = ButtonManager.createMenuButtonByScreenPercentage(assetManager, "Yes", BUTTON_WIDTH_PERCENT, BUTTON_HEIGHT_PERCENT, () ->
		{
			app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
			final Runnable action = _yesAction;
			dismiss();
			
			if (action != null)
			{
				action.run();
			}
		});
		buttonRow.addChild(_yesButton);
		
		// Horizontal spacer between buttons.
		final Label buttonSpacer = new Label("");
		buttonSpacer.setPreferredSize(new Vector3f(BUTTON_SPACING, 1, 0));
		buttonRow.addChild(buttonSpacer);
		
		// No button.
		_noButton = ButtonManager.createMenuButtonByScreenPercentage(assetManager, "No", BUTTON_WIDTH_PERCENT, BUTTON_HEIGHT_PERCENT, () ->
		{
			app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
			final Runnable action = _noAction;
			dismiss();
			
			if (action != null)
			{
				action.run();
			}
		});
		buttonRow.addChild(_noButton);
		
		_dialogContainer.addChild(buttonRow);
		
		// Center the dialog on screen.
		final float dialogWidth = _dialogContainer.getPreferredSize().x;
		final float dialogHeight = _dialogContainer.getPreferredSize().y;
		final float dialogX = (screenWidth - dialogWidth) / 2f;
		final float dialogY = (screenHeight + dialogHeight) / 2f;
		_dialogContainer.setLocalTranslation(dialogX, dialogY, DIALOG_Z);
		
		// Attach to GUI node (backdrop first, then dialog on top).
		app.getGuiNode().attachChild(_backdrop);
		app.getGuiNode().attachChild(_dialogContainer);
	}
	
	/**
	 * Dismiss the currently active question dialog, removing all elements from the GUI node.<br>
	 * Safe to call when no dialog is active.
	 */
	public static void dismiss()
	{
		final SimpleCraft app = SimpleCraft.getInstance();
		
		if (_backdrop != null)
		{
			app.getGuiNode().detachChild(_backdrop);
			_backdrop = null;
		}
		
		if (_dialogContainer != null)
		{
			app.getGuiNode().detachChild(_dialogContainer);
			_dialogContainer = null;
		}
		
		_yesButton = null;
		_noButton = null;
		_yesAction = null;
		_noAction = null;
		_selectedIndex = -1;
	}
	
	/**
	 * Check whether a question dialog is currently visible.
	 * @return true if a dialog is active
	 */
	public static boolean isActive()
	{
		return _dialogContainer != null;
	}
	
	// ========== KEYBOARD NAVIGATION ==========
	
	/**
	 * Move keyboard focus to the left button (Yes).
	 */
	public static void navigateLeft()
	{
		if (!isActive())
		{
			return;
		}
		
		_selectedIndex = 0;
		updateSelectionVisuals();
	}
	
	/**
	 * Move keyboard focus to the right button (No).
	 */
	public static void navigateRight()
	{
		if (!isActive())
		{
			return;
		}
		
		_selectedIndex = 1;
		updateSelectionVisuals();
	}
	
	/**
	 * Activate the currently focused button (Yes or No).<br>
	 * Only fires if keyboard focus is active. On first press without prior navigation,<br>
	 * activates focus on Yes without firing the action.
	 */
	public static void confirmSelection()
	{
		if (!isActive())
		{
			return;
		}
		
		// First confirm press activates focus on Yes without firing.
		if (_selectedIndex < 0)
		{
			_selectedIndex = 0;
			updateSelectionVisuals();
			SimpleCraft.getInstance().getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
			return;
		}
		
		final SimpleCraft app = SimpleCraft.getInstance();
		app.getAudioManager().playSfx(AudioManager.UI_CLICK_SFX_PATH);
		
		final Runnable action = (_selectedIndex == 0) ? _yesAction : _noAction;
		dismiss();
		
		if (action != null)
		{
			action.run();
		}
	}
	
	/**
	 * Update button visuals to reflect the current keyboard selection.<br>
	 * The selected button shows the hover texture and yellow text; the other shows normal and white.
	 */
	private static void updateSelectionVisuals()
	{
		if (_yesButton != null)
		{
			ButtonManager.setFocused(_yesButton, _selectedIndex == 0);
		}
		if (_noButton != null)
		{
			ButtonManager.setFocused(_noButton, _selectedIndex == 1);
		}
	}
}
