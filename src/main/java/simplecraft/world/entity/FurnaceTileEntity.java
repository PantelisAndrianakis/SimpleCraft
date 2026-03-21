package simplecraft.world.entity;

import com.jme3.effect.ParticleEmitter;
import com.jme3.effect.ParticleMesh;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

import simplecraft.SimpleCraft;
import simplecraft.item.ItemInstance;
import simplecraft.item.ItemRegistry;
import simplecraft.item.ItemTemplate;
import simplecraft.item.SmeltingRegistry;
import simplecraft.item.SmeltingRegistry.SmeltResult;
import simplecraft.player.FurnaceScreen;
import simplecraft.player.PlayerController;
import simplecraft.util.Vector3i;
import simplecraft.world.Block;
import simplecraft.world.World;

/**
 * Tile entity for the Furnace block.<br>
 * Contains three item slots (input, fuel, output) and runs a smelting simulation<br>
 * every frame via {@link #update(float)}, called by {@link TileEntityManager}.<br>
 * <br>
 * <b>Key behavior:</b> Smelting continues even when the UI is closed. The player<br>
 * can load ore and fuel, walk away and return to find finished ingots.<br>
 * <br>
 * Fuel burns continuously once lit - if the input runs out or the output is full,<br>
 * remaining fuel is wasted (matches Minecraft behavior).<br>
 * <br>
 * The UI (FurnaceScreen) reads slot states, progress and fuel remaining directly<br>
 * from this tile entity via getters.
 * @author Pantelis Andrianakis
 * @since March 19th 2026
 */
public class FurnaceTileEntity extends TileEntity
{
	// ========================================================
	// Slots.
	// ========================================================
	
	/** The ore or material being smelted (top slot). */
	private ItemInstance _inputSlot;
	
	/** The fuel source being burned (bottom slot). */
	private ItemInstance _fuelSlot;
	
	/** The smelted result (right slot). */
	private ItemInstance _outputSlot;
	
	// ========================================================
	// Smelting State.
	// ========================================================
	
	/** Current smelt progress in seconds (0 to _smeltTimeRequired). */
	private float _smeltProgress;
	
	/** Total time required to smelt the current recipe (from SmeltingRegistry). */
	private float _smeltTimeRequired;
	
	/** Seconds of burn remaining from the current fuel item. */
	private float _fuelRemaining;
	
	/** Total burn time of the current fuel item (for UI progress bar calculation). */
	private float _fuelTotalBurnTime;
	
	/** Reference to the currently open FurnaceScreen, or null if not viewing. */
	private FurnaceScreen _activeScreen;
	
	/** Smoke particle emitter shown above the furnace while burning. */
	private ParticleEmitter _smokeEmitter;
	
	/** Tracks previous burning state to toggle smoke only on transitions. */
	private boolean _wasBurning;
	
	// ========================================================
	// Constructor.
	// ========================================================
	
	/**
	 * Creates a new furnace tile entity at the given world position.
	 * @param position world block coordinates
	 */
	public FurnaceTileEntity(Vector3i position)
	{
		super(position, Block.FURNACE);
	}
	
	// ========================================================
	// Lifecycle Hooks.
	// ========================================================
	
	@Override
	public void onPlaced(World world)
	{
		// Create visual node with smoke particle emitter.
		_visualNode = new Node("FurnaceSmoke");
		_visualNode.setLocalTranslation(_position.x + 0.5f, _position.y + 1.0f, _position.z + 0.5f);
		
		final Material smokeMat = new Material(SimpleCraft.getInstance().getAssetManager(), "Common/MatDefs/Misc/Particle.j3md");
		smokeMat.setBoolean("PointSprite", true);
		
		_smokeEmitter = new ParticleEmitter("FurnaceSmokeEmitter", ParticleMesh.Type.Point, 12);
		_smokeEmitter.setMaterial(smokeMat);
		_smokeEmitter.setImagesX(1);
		_smokeEmitter.setImagesY(1);
		_smokeEmitter.setStartColor(new ColorRGBA(0.4f, 0.4f, 0.4f, 0.6f));
		_smokeEmitter.setEndColor(new ColorRGBA(0.3f, 0.3f, 0.3f, 0.0f));
		_smokeEmitter.setStartSize(0.15f);
		_smokeEmitter.setEndSize(0.4f);
		_smokeEmitter.setGravity(0, -0.3f, 0); // Drifts upward (negative gravity = up).
		_smokeEmitter.setLowLife(1.0f);
		_smokeEmitter.setHighLife(2.0f);
		_smokeEmitter.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 0.5f, 0));
		_smokeEmitter.getParticleInfluencer().setVelocityVariation(0.3f);
		_smokeEmitter.setParticlesPerSec(0); // Off by default - toggled in update().
		
		_visualNode.attachChild(_smokeEmitter);
	}
	
	@Override
	public void onInteract(PlayerController player, World world)
	{
		// FurnaceScreen opening is handled by BlockInteraction,
		// which calls setActiveScreen() and then opens the UI.
	}
	
	@Override
	public void onRemoved(World world)
	{
		// Stop smoke.
		if (_smokeEmitter != null)
		{
			_smokeEmitter.setParticlesPerSec(0);
			_smokeEmitter.killAllParticles();
		}
		
		// Close the furnace screen if this furnace is currently being viewed.
		if (_activeScreen != null && _activeScreen.isOpen())
		{
			_activeScreen.close();
		}
	}
	
	// ========================================================
	// Update (called every frame by TileEntityManager).
	// ========================================================
	
	@Override
	public void update(float tpf)
	{
		// Step 1: Check if we can smelt.
		final boolean canSmelt = canSmelt();
		
		// Step 2: Consume fuel if needed.
		if (!isBurning() && canSmelt)
		{
			consumeFuel();
		}
		
		// Step 3: Burn fuel.
		if (isBurning())
		{
			_fuelRemaining -= tpf;
			if (_fuelRemaining < 0)
			{
				_fuelRemaining = 0;
			}
		}
		
		// Step 4: Advance smelt progress.
		if (isBurning() && canSmelt)
		{
			_smeltProgress += tpf;
			
			// Check if smelting is complete.
			if (_smeltProgress >= _smeltTimeRequired)
			{
				produceSmelted();
				_smeltProgress = 0;
				
				// Recalculate smelt time for next item (if any).
				updateSmeltTime();
			}
		}
		else if (!canSmelt)
		{
			// Step 5: Idle - reset smelt progress when we can't smelt.
			// Fuel continues burning (wasted, like Minecraft).
			_smeltProgress = 0;
		}
		
		// Step 6: Toggle smoke particles on burning state transitions.
		final boolean burning = isBurning();
		if (burning != _wasBurning)
		{
			_wasBurning = burning;
			if (_smokeEmitter != null)
			{
				_smokeEmitter.setParticlesPerSec(burning ? 4 : 0);
			}
		}
	}
	
	// ========================================================
	// Smelting Logic.
	// ========================================================
	
	/**
	 * Returns true if smelting can proceed:<br>
	 * - Input slot has a smeltable item.<br>
	 * - Output slot is empty OR matches the expected output and has room.
	 */
	private boolean canSmelt()
	{
		if (_inputSlot == null || _inputSlot.isEmpty())
		{
			return false;
		}
		
		final SmeltResult result = SmeltingRegistry.getSmeltResult(_inputSlot.getTemplate().getId());
		if (result == null)
		{
			return false;
		}
		
		if (_outputSlot == null || _outputSlot.isEmpty())
		{
			return true;
		}
		
		// Output slot has items - check if they match and have room.
		if (_outputSlot.getTemplate() != result.getOutput())
		{
			return false;
		}
		
		return !_outputSlot.isFull();
	}
	
	/**
	 * Attempts to consume one fuel item from the fuel slot.<br>
	 * Sets _fuelRemaining and _fuelTotalBurnTime based on the fuel's burn time.
	 */
	private void consumeFuel()
	{
		if (_fuelSlot == null || _fuelSlot.isEmpty())
		{
			return;
		}
		
		final float burnTime = SmeltingRegistry.getBurnTime(_fuelSlot.getTemplate().getId());
		if (burnTime <= 0)
		{
			return;
		}
		
		// Consume one fuel item.
		_fuelSlot.remove(1);
		if (_fuelSlot.isEmpty())
		{
			_fuelSlot = null;
		}
		
		_fuelRemaining = burnTime;
		_fuelTotalBurnTime = burnTime;
		
		// Update smelt time for the current input.
		updateSmeltTime();
	}
	
	/**
	 * Produces one smelted output item, consuming one input item.
	 */
	private void produceSmelted()
	{
		if (_inputSlot == null || _inputSlot.isEmpty())
		{
			return;
		}
		
		final SmeltResult result = SmeltingRegistry.getSmeltResult(_inputSlot.getTemplate().getId());
		if (result == null)
		{
			return;
		}
		
		// Consume one input item.
		_inputSlot.remove(1);
		if (_inputSlot.isEmpty())
		{
			_inputSlot = null;
		}
		
		// Add one output item.
		if (_outputSlot == null || _outputSlot.isEmpty())
		{
			_outputSlot = new ItemInstance(result.getOutput(), 1);
		}
		else
		{
			_outputSlot.add(1);
		}
	}
	
	/**
	 * Updates the smelt time required based on the current input slot's recipe.
	 */
	private void updateSmeltTime()
	{
		if (_inputSlot == null || _inputSlot.isEmpty())
		{
			_smeltTimeRequired = 0;
			return;
		}
		
		final SmeltResult result = SmeltingRegistry.getSmeltResult(_inputSlot.getTemplate().getId());
		if (result != null)
		{
			_smeltTimeRequired = result.getSmeltTime();
		}
		else
		{
			_smeltTimeRequired = 0;
		}
	}
	
	// ========================================================
	// State Queries.
	// ========================================================
	
	/**
	 * Returns true if the furnace is currently burning fuel.
	 */
	public boolean isBurning()
	{
		return _fuelRemaining > 0;
	}
	
	/**
	 * Returns the smelt progress as a fraction (0.0 to 1.0).<br>
	 * Used by the UI to fill the arrow indicator.
	 */
	public float getSmeltProgressFraction()
	{
		if (_smeltTimeRequired <= 0)
		{
			return 0;
		}
		
		return Math.min(1.0f, _smeltProgress / _smeltTimeRequired);
	}
	
	/**
	 * Returns the fuel remaining as a fraction (0.0 to 1.0).<br>
	 * Used by the UI to fill the flame indicator.
	 */
	public float getFuelRemainingFraction()
	{
		if (_fuelTotalBurnTime <= 0)
		{
			return 0;
		}
		
		return Math.min(1.0f, _fuelRemaining / _fuelTotalBurnTime);
	}
	
	// ========================================================
	// Slot Access.
	// ========================================================
	
	public ItemInstance getInputSlot()
	{
		return _inputSlot;
	}
	
	public void setInputSlot(ItemInstance item)
	{
		_inputSlot = item;
		updateSmeltTime();
	}
	
	public ItemInstance getFuelSlot()
	{
		return _fuelSlot;
	}
	
	public void setFuelSlot(ItemInstance item)
	{
		_fuelSlot = item;
	}
	
	public ItemInstance getOutputSlot()
	{
		return _outputSlot;
	}
	
	public void setOutputSlot(ItemInstance item)
	{
		_outputSlot = item;
	}
	
	/**
	 * Sets the active FurnaceScreen reference.<br>
	 * Used to close the screen when the furnace block is broken.
	 */
	public void setActiveScreen(FurnaceScreen screen)
	{
		_activeScreen = screen;
	}
	
	/**
	 * Returns the active FurnaceScreen, or null if not viewing.
	 */
	public FurnaceScreen getActiveScreen()
	{
		return _activeScreen;
	}
	
	// ========================================================
	// Item Drop (on block break).
	// ========================================================
	
	/**
	 * Drops all furnace slot contents as world items via the DropManager.<br>
	 * Called when the furnace block is broken.
	 * @param dropManager the drop manager for spawning world drops
	 */
	public void dropContents(simplecraft.item.DropManager dropManager)
	{
		if (dropManager == null)
		{
			return;
		}
		
		final float cx = _position.x + 0.5f;
		final float cy = _position.y + 0.5f;
		final float cz = _position.z + 0.5f;
		
		if (_inputSlot != null && !_inputSlot.isEmpty())
		{
			dropManager.spawnDrop(new Vector3f(cx, cy, cz), _inputSlot);
			_inputSlot = null;
		}
		
		if (_fuelSlot != null && !_fuelSlot.isEmpty())
		{
			dropManager.spawnDrop(new Vector3f(cx, cy, cz), _fuelSlot);
			_fuelSlot = null;
		}
		
		if (_outputSlot != null && !_outputSlot.isEmpty())
		{
			dropManager.spawnDrop(new Vector3f(cx, cy, cz), _outputSlot);
			_outputSlot = null;
		}
	}
	
	// ========================================================
	// Serialization.
	// ========================================================
	
	@Override
	public String serialize()
	{
		final StringBuilder sb = new StringBuilder();
		sb.append(super.serialize());
		
		// Input slot.
		if (_inputSlot != null && !_inputSlot.isEmpty())
		{
			sb.append('\n').append("inputItem=").append(_inputSlot.getTemplate().getId());
			sb.append('\n').append("inputCount=").append(_inputSlot.getCount());
		}
		
		// Fuel slot.
		if (_fuelSlot != null && !_fuelSlot.isEmpty())
		{
			sb.append('\n').append("fuelItem=").append(_fuelSlot.getTemplate().getId());
			sb.append('\n').append("fuelCount=").append(_fuelSlot.getCount());
		}
		
		// Output slot.
		if (_outputSlot != null && !_outputSlot.isEmpty())
		{
			sb.append('\n').append("outputItem=").append(_outputSlot.getTemplate().getId());
			sb.append('\n').append("outputCount=").append(_outputSlot.getCount());
		}
		
		// Smelting state.
		sb.append('\n').append("smeltProgress=").append(_smeltProgress);
		sb.append('\n').append("fuelRemaining=").append(_fuelRemaining);
		sb.append('\n').append("fuelTotalBurnTime=").append(_fuelTotalBurnTime);
		
		return sb.toString();
	}
	
	/**
	 * Restores furnace contents and smelting state from serialized data.
	 * @param data the serialized key=value string
	 */
	public void deserializeContents(String data)
	{
		if (data == null || data.isEmpty())
		{
			return;
		}
		
		String inputItem = null;
		int inputCount = 0;
		String fuelItem = null;
		int fuelCount = 0;
		String outputItem = null;
		int outputCount = 0;
		float smeltProgress = 0;
		float fuelRemaining = 0;
		float fuelTotalBurnTime = 0;
		
		for (String line : data.split("\n"))
		{
			final int eq = line.indexOf('=');
			if (eq < 0)
			{
				continue;
			}
			
			final String key = line.substring(0, eq).trim();
			final String value = line.substring(eq + 1).trim();
			
			switch (key)
			{
				case "inputItem":
				{
					inputItem = value;
					break;
				}
				case "inputCount":
				{
					inputCount = Integer.parseInt(value);
					break;
				}
				case "fuelItem":
				{
					fuelItem = value;
					break;
				}
				case "fuelCount":
				{
					fuelCount = Integer.parseInt(value);
					break;
				}
				case "outputItem":
				{
					outputItem = value;
					break;
				}
				case "outputCount":
				{
					outputCount = Integer.parseInt(value);
					break;
				}
				case "smeltProgress":
				{
					smeltProgress = Float.parseFloat(value);
					break;
				}
				case "fuelRemaining":
				{
					fuelRemaining = Float.parseFloat(value);
					break;
				}
				case "fuelTotalBurnTime":
				{
					fuelTotalBurnTime = Float.parseFloat(value);
					break;
				}
			}
		}
		
		// Restore slots.
		if (inputItem != null && inputCount > 0)
		{
			final ItemTemplate template = ItemRegistry.get(inputItem);
			if (template != null)
			{
				_inputSlot = new ItemInstance(template, inputCount);
			}
		}
		
		if (fuelItem != null && fuelCount > 0)
		{
			final ItemTemplate template = ItemRegistry.get(fuelItem);
			if (template != null)
			{
				_fuelSlot = new ItemInstance(template, fuelCount);
			}
		}
		
		if (outputItem != null && outputCount > 0)
		{
			final ItemTemplate template = ItemRegistry.get(outputItem);
			if (template != null)
			{
				_outputSlot = new ItemInstance(template, outputCount);
			}
		}
		
		// Restore smelting state.
		_smeltProgress = smeltProgress;
		_fuelRemaining = fuelRemaining;
		_fuelTotalBurnTime = fuelTotalBurnTime;
		
		// Update smelt time based on current input.
		updateSmeltTime();
	}
}
