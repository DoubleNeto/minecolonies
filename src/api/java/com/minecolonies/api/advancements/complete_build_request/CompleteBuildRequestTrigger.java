package com.minecolonies.api.advancements.complete_build_request;

import com.google.gson.JsonObject;
import com.ldtteam.structurize.management.StructureName;
import com.minecolonies.api.advancements.AbstractCriterionTrigger;
import com.minecolonies.api.util.constant.Constants;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.loot.ConditionArrayParser;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class CompleteBuildRequestTrigger extends AbstractCriterionTrigger<CompleteBuildRequestListeners, CompleteBuildRequestCriterionInstance>
{
    public CompleteBuildRequestTrigger()
    {
        super(new ResourceLocation(Constants.MOD_ID, Constants.CRITERION_COMPLETE_BUILD_REQUEST), CompleteBuildRequestListeners::new);
    }

    /**
     * Triggers the listener checks if there are any listening in
     * @param player the player the check regards
     * @param structureName the structure that was just completed
     * @param level the level the structure got upgraded to, or 0
     */
    public void trigger(final ServerPlayerEntity player, final StructureName structureName, final int level)
    {
        final CompleteBuildRequestListeners listeners = this.getListeners(player.getAdvancements());
        if (listeners != null)
        {
            listeners.trigger(structureName, level);
        }
    }

    @NotNull
    @Override
    public CompleteBuildRequestCriterionInstance createInstance(@NotNull final JsonObject jsonObject, @NotNull final ConditionArrayParser jsonDeserializationContext)
    {
        return CompleteBuildRequestCriterionInstance.deserializeFromJson(jsonObject, jsonDeserializationContext);
    }
}
