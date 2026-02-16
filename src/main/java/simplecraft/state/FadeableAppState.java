package simplecraft.state;

import com.jme3.app.state.BaseAppState;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Quad;

import simplecraft.SimpleCraft;

/**
 * Base state class that provides configurable fade-in and fade-out screen transitions.<br>
 * Subclasses set fade parameters via {@link #setFadeIn} and {@link #setFadeOut},<br>
 * then implement {@link #onEnterState}, {@link #onExitState},<br>
 * and optionally {@link #onUpdateState}, {@link #onFadeInComplete}, {@link #onFadeOutComplete}.<br>
 * <br>
 * The fade is rendered as a full-screen colored overlay in the GUI node.<br>
 * States can be switched with or without fading via {@link GameStateManager#switchTo(GameStateManager.GameState, boolean)}.
 * @author Pantelis Andrianakis
 * @since February 17th 2026
 */
public abstract class FadeableAppState extends BaseAppState
{
	private enum FadePhase
	{
		NONE,
		FADING_IN,
		IDLE,
		FADING_OUT
	}
	
	// Fade-in configuration.
	private float _fadeInDuration = 0f;
	private ColorRGBA _fadeInColor = new ColorRGBA(0, 0, 0, 1);
	
	// Fade-out configuration.
	private float _fadeOutDuration = 0f;
	private ColorRGBA _fadeOutColor = new ColorRGBA(0, 0, 0, 1);
	
	// Fade runtime state.
	private FadePhase _fadePhase = FadePhase.NONE;
	private float _fadeTimer = 0f;
	
	// Fade overlay.
	private Geometry _fadeOverlay;
	private Material _fadeMaterial;
	
	// Callback when fade-out completes.
	private Runnable _onFadeOutComplete;
	
	// --- Configuration methods ---
	
	/**
	 * Set the fade-in parameters. The overlay starts opaque and fades to transparent.
	 * @param duration Duration in seconds (0 = no fade-in)
	 * @param color The overlay color during fade-in
	 */
	public void setFadeIn(float duration, ColorRGBA color)
	{
		_fadeInDuration = duration;
		_fadeInColor = color.clone();
	}
	
	/**
	 * Set the fade-out parameters. The overlay starts transparent and fades to opaque.
	 * @param duration Duration in seconds (0 = no fade-out)
	 * @param color The overlay color during fade-out
	 */
	public void setFadeOut(float duration, ColorRGBA color)
	{
		_fadeOutDuration = duration;
		_fadeOutColor = color.clone();
	}
	
	public float getFadeInDuration()
	{
		return _fadeInDuration;
	}
	
	public ColorRGBA getFadeInColor()
	{
		return _fadeInColor;
	}
	
	public float getFadeOutDuration()
	{
		return _fadeOutDuration;
	}
	
	public ColorRGBA getFadeOutColor()
	{
		return _fadeOutColor;
	}
	
	// --- Subclass lifecycle hooks ---
	
	/**
	 * Called when the state becomes active. Set up GUI elements and scene content here.
	 */
	protected abstract void onEnterState();
	
	/**
	 * Called when the state is disabled. Clean up GUI elements and scene content here.
	 */
	protected abstract void onExitState();
	
	/**
	 * Called every frame while the state is enabled. Override for per-frame logic.
	 * @param tpf Time per frame in seconds
	 */
	protected void onUpdateState(float tpf)
	{
		// Optional override.
	}
	
	/**
	 * Called when the fade-in animation completes. Override to trigger post-reveal logic.
	 */
	protected void onFadeInComplete()
	{
		// Optional override.
	}
	
	/**
	 * Called when the fade-out animation completes, before the completion callback runs.
	 */
	protected void onFadeOutComplete()
	{
		// Optional override.
	}
	
	// --- BaseAppState overrides (final) ---
	
	@Override
	protected final void onEnable()
	{
		// Let the subclass set up its content first.
		onEnterState();
		
		// Start fade-in if configured.
		if (_fadeInDuration > 0f)
		{
			createFadeOverlay(_fadeInColor, 1f);
			_fadePhase = FadePhase.FADING_IN;
			_fadeTimer = 0f;
			System.out.println(getClass().getSimpleName() + ": Starting fade-in (" + _fadeInDuration + "s).");
		}
		else
		{
			_fadePhase = FadePhase.IDLE;
		}
	}
	
	@Override
	protected final void onDisable()
	{
		removeFadeOverlay();
		_fadePhase = FadePhase.NONE;
		_fadeTimer = 0f;
		_onFadeOutComplete = null;
		
		onExitState();
	}
	
	@Override
	public void update(float tpf)
	{
		updateFade(tpf);
		onUpdateState(tpf);
	}
	
	// --- Fade control ---
	
	/**
	 * Begin fading out. The overlay fades from transparent to opaque over the configured duration.
	 * @param onComplete Callback to run when the fade-out finishes (may be null)
	 */
	public void startFadeOut(Runnable onComplete)
	{
		_onFadeOutComplete = onComplete;
		
		if (_fadeOutDuration > 0f)
		{
			createFadeOverlay(_fadeOutColor, 0f);
			_fadePhase = FadePhase.FADING_OUT;
			_fadeTimer = 0f;
			System.out.println(getClass().getSimpleName() + ": Starting fade-out (" + _fadeOutDuration + "s).");
		}
		else
		{
			// No fade-out configured; complete immediately.
			_fadePhase = FadePhase.IDLE;
			onFadeOutComplete();
			
			if (_onFadeOutComplete != null)
			{
				final Runnable callback = _onFadeOutComplete;
				_onFadeOutComplete = null;
				callback.run();
			}
		}
	}
	
	/**
	 * Check whether a fade animation is currently in progress.
	 * @return true if fading in or out
	 */
	public boolean isFading()
	{
		return _fadePhase == FadePhase.FADING_IN || _fadePhase == FadePhase.FADING_OUT;
	}
	
	// --- Fade internals ---
	
	private void updateFade(float tpf)
	{
		if (_fadePhase == FadePhase.FADING_IN)
		{
			_fadeTimer += tpf;
			final float progress = Math.min(1f, _fadeTimer / _fadeInDuration);
			final float alpha = 1f - progress; // Opaque -> transparent.
			updateFadeOverlayAlpha(alpha);
			
			if (progress >= 1f)
			{
				removeFadeOverlay();
				_fadePhase = FadePhase.IDLE;
				System.out.println(getClass().getSimpleName() + ": Fade-in complete.");
				onFadeInComplete();
			}
		}
		else if (_fadePhase == FadePhase.FADING_OUT)
		{
			_fadeTimer += tpf;
			final float progress = Math.min(1f, _fadeTimer / _fadeOutDuration);
			final float alpha = progress; // Transparent -> opaque.
			updateFadeOverlayAlpha(alpha);
			
			if (progress >= 1f)
			{
				_fadePhase = FadePhase.IDLE;
				System.out.println(getClass().getSimpleName() + ": Fade-out complete.");
				onFadeOutComplete();
				
				if (_onFadeOutComplete != null)
				{
					final Runnable callback = _onFadeOutComplete;
					_onFadeOutComplete = null;
					callback.run();
				}
			}
		}
	}
	
	/**
	 * Create the full-screen fade overlay quad.
	 * @param color The overlay RGB color
	 * @param initialAlpha Starting alpha (1 = fully opaque, 0 = fully transparent)
	 */
	private void createFadeOverlay(ColorRGBA color, float initialAlpha)
	{
		removeFadeOverlay();
		
		final SimpleCraft app = SimpleCraft.getInstance();
		final float width = app.getCamera().getWidth();
		final float height = app.getCamera().getHeight();
		
		final Quad quad = new Quad(width, height);
		_fadeOverlay = new Geometry("FadeOverlay", quad);
		
		_fadeMaterial = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
		_fadeMaterial.setColor("Color", new ColorRGBA(color.r, color.g, color.b, initialAlpha));
		_fadeMaterial.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
		
		_fadeOverlay.setMaterial(_fadeMaterial);
		_fadeOverlay.setLocalTranslation(0, 0, 10); // In front of all GUI content.
		
		app.getGuiNode().attachChild(_fadeOverlay);
	}
	
	/**
	 * Update only the alpha channel of the existing fade overlay material.
	 * @param alpha New alpha value (0-1)
	 */
	private void updateFadeOverlayAlpha(float alpha)
	{
		if (_fadeMaterial != null)
		{
			final ColorRGBA current = (ColorRGBA) _fadeMaterial.getParam("Color").getValue();
			_fadeMaterial.setColor("Color", new ColorRGBA(current.r, current.g, current.b, alpha));
		}
	}
	
	/**
	 * Remove the fade overlay from the GUI node and release references.
	 */
	private void removeFadeOverlay()
	{
		if (_fadeOverlay != null)
		{
			SimpleCraft.getInstance().getGuiNode().detachChild(_fadeOverlay);
			_fadeOverlay = null;
			_fadeMaterial = null;
		}
	}
}
