package com.minecolonies.coremod.colony.buildings.workerbuildings;

import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.items.ModTags;
import com.minecolonies.coremod.client.gui.huts.WindowHutSifterModule;
import com.minecolonies.coremod.colony.buildings.AbstractBuilding;
import com.minecolonies.coremod.colony.buildings.modules.AbstractCraftingBuildingModule;
import com.minecolonies.coremod.colony.buildings.views.AbstractBuildingView;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;

import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_CURRENT_DAILY;

/**
 * Class of the sifter building.
 */
public class BuildingSifter extends AbstractBuilding
{
    /**
     * The multiplier to define the max craft per day.
     */
    private static final double BUILDING_LEVEL_MULTIPLIER = 64;

    /**
     * The sifter string.
     */
    private static final String SIFTER_DESC = "sifter";

    /**
     * Max building level of the sifter.
     */
    private static final int MAX_BUILDING_LEVEL = 5;

    /**
     * The current daily quantity.
     */
    private int currentDailyQuantity = 0;

    /**
     * Instantiates a new sifter building.
     *
     * @param c the colony.
     * @param l the location
     */
    public BuildingSifter(final IColony c, final BlockPos l)
    {
        super(c, l);

        keepX.put(stack -> stack.is(ModTags.meshes), new net.minecraft.util.Tuple<>(4, false));
    }

    @NotNull
    @Override
    public String getSchematicName()
    {
        return SIFTER_DESC;
    }

    @Override
    public int getMaxBuildingLevel()
    {
        return MAX_BUILDING_LEVEL;
    }

    /**
     * Set the current daily quantity.
     *
     * @param currentDailyQuantity the current quantity.
     */
    public void setCurrentDailyQuantity(final int currentDailyQuantity)
    {
        this.currentDailyQuantity = currentDailyQuantity;
    }

    /**
     * Calculate the max quantity to be crafted per day.
     *
     * @return the max.
     */
    public int getMaxDailyQuantity()
    {
        if (getBuildingLevel() >= MAX_BUILDING_LEVEL)
        {
            return Integer.MAX_VALUE;
        }

        return (int) (Math.pow(getBuildingLevel(), 2) * BUILDING_LEVEL_MULTIPLIER);
    }

    @Override
    public void onWakeUp()
    {
        super.onWakeUp();
        this.currentDailyQuantity = 0;
    }

    /**
     * Get the current daily quantity.
     *
     * @return the quantity.
     */
    public int getCurrentDailyQuantity()
    {
        return currentDailyQuantity;
    }

    @Override
    public void deserializeNBT(final CompoundTag compound)
    {
        super.deserializeNBT(compound);

        this.currentDailyQuantity = compound.getInt(TAG_CURRENT_DAILY);
    }

    @Override
    public CompoundTag serializeNBT()
    {
        final CompoundTag compound = super.serializeNBT();

        compound.putInt(TAG_CURRENT_DAILY, currentDailyQuantity);

        return compound;
    }

    @Override
    public void serializeToView(@NotNull final FriendlyByteBuf buf)
    {
        super.serializeToView(buf);
        buf.writeInt(getMaxDailyQuantity());
        buf.writeInt(getCurrentDailyQuantity());
    }

    /**
     * BuildingSifter View.
     */
    public static class View extends AbstractBuildingView
    {
        /**
         * Maximum possible daily quantity
         */
        private int maxDailyQuantity = 0;

        /**
         * Current daily quantity
         */
        private int currentDailyQuantity = 0;

        /**
         * Instantiate the sifter view.
         *
         * @param c the colonyview to put it in
         * @param l the positon
         */
        public View(final IColonyView c, final BlockPos l)
        {
            super(c, l);
        }

        @Override
        public void deserialize(@NotNull final FriendlyByteBuf buf)
        {
            super.deserialize(buf);
            maxDailyQuantity = buf.readInt();
            currentDailyQuantity = buf.readInt();
        }

        /**
         * Getter for the current maximum settable daily quantity
         * @return the maximum
         */
        public int getMaxDailyQuantity()
        {
            return maxDailyQuantity;
        }

        /**
         * Getter for the current daily quantity
         * @return the current quantity
         */
        public int getCurrentDailyQuantity()
        {
            return currentDailyQuantity;
        }

        @NotNull
        @Override
        public BOWindow getWindow()
        {
            return new WindowHutSifterModule(this);
        }
    }

    public static class CraftingModule extends AbstractCraftingBuildingModule.Custom
    {
        /**
         * Create a new module.
         *
         * @param jobEntry the entry of the job.
         */
        public CraftingModule(final JobEntry jobEntry)
        {
            super(jobEntry);
        }
    }
}
