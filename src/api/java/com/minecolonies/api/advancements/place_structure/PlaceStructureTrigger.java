package com.minecolonies.api.advancements.place_structure;

import com.google.gson.JsonObject;
import com.ldtteam.structurize.management.StructureName;
import com.minecolonies.api.advancements.AbstractCriterionTrigger;
import com.minecolonies.api.util.constant.Constants;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.loot.ConditionArrayParser;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Triggers whenever the build tool is used to position a new structure
 */
public class PlaceStructureTrigger extends AbstractCriterionTrigger<PlaceStructureListeners, PlaceStructureCriterionInstance>
{
    public PlaceStructureTrigger()
    {
        super(new ResourceLocation(Constants.MOD_ID, Constants.CRITERION_STRUCTURE_PLACED), PlaceStructureListeners::new);
    }

    /**
     * Triggers the listener checks if there are any listening in
     * @param player the player the check regards
     * @param structureName the structure id of what was just placed
     */
    public void trigger(final ServerPlayerEntity player, final StructureName structureName)
    {
        final PlaceStructureListeners listeners = this.getListeners(player.getAdvancements());
        if (listeners != null)
        {
            listeners.trigger(structureName);
        }
    }

    @NotNull
    @Override
    public PlaceStructureCriterionInstance createInstance(@NotNull final JsonObject jsonObject, @NotNull final ConditionArrayParser conditionArrayParser)
    {
        return PlaceStructureCriterionInstance.deserializeFromJson(jsonObject, conditionArrayParser);
    }
}
