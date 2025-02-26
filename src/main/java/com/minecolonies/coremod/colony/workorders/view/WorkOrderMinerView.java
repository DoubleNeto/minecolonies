package com.minecolonies.coremod.colony.workorders.view;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;

/**
 * The client side representation for a work order that the builder can take to build mineshafts.
 */
public class WorkOrderMinerView extends AbstractWorkOrderView
{
    @Override
    public ITextComponent getDisplayName()
    {
        return new TranslationTextComponent(getWorkOrderName());
    }

    @Override
    public boolean shouldShowIn(IBuildingView view)
    {
        return false;
    }
}
