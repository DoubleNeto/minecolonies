package com.minecolonies.coremod.client.gui;

import com.ldtteam.structures.helpers.Settings;
import com.ldtteam.structurize.client.gui.WindowBuildTool;
import com.ldtteam.structurize.management.StructureName;
import com.ldtteam.structurize.placement.handlers.placement.PlacementError;
import com.minecolonies.api.colony.buildings.registry.IBuildingRegistry;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.coremod.Network;
import com.minecolonies.coremod.event.HighlightManager;
import com.minecolonies.coremod.items.ItemSupplyCampDeployer;
import com.minecolonies.coremod.items.ItemSupplyChestDeployer;
import com.minecolonies.coremod.network.messages.server.BuildToolPasteMessage;
import com.minecolonies.coremod.network.messages.server.BuildToolPlaceMessage;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TranslationTextComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.minecolonies.api.util.constant.TranslationConstants.*;

/**
 * BuildTool window.
 */
public class WindowMinecoloniesBuildTool extends WindowBuildTool
{
    /**
     * The displayed boxes category
     */
    private static final String RENDER_BOX_CATEGORY = "placement";

    /**
     * Creates a window build tool for a specific structure.
     *
     * @param pos           the position.
     * @param structureName the structure name.
     * @param rotation      the rotation.
     * @param groundstyle   one of the GROUNDSTYLE_ values.
     */
    public WindowMinecoloniesBuildTool(@Nullable final BlockPos pos, final String structureName, final int rotation, final int groundstyle)
    {
        super(pos, structureName, rotation, groundstyle);
    }

    /**
     * Creates a window build tool. This requires X, Y and Z coordinates. If a structure is active, recalculates the X Y Z with offset. Otherwise the given parameters are used.
     *
     * @param pos coordinate.
     * @param groundstyle one of the GROUNDSTYLE_ values.
     */
    public WindowMinecoloniesBuildTool(@Nullable final BlockPos pos, final int groundstyle)
    {
        super(pos, groundstyle);
    }

    @Override
    public void place(final StructureName structureName)
    {
        final BlockPos offset = Settings.instance.getActiveStructure().getPrimaryBlockOffset();
        final BlockState state = Settings.instance.getActiveStructure().getBlockState(offset).getBlockState();

        BuildToolPlaceMessage msg = new BuildToolPlaceMessage(
          structureName.toString(),
          structureName.getLocalizedName(),
          Settings.instance.getPosition(),
          Settings.instance.getRotation(),
          structureName.isHut(),
          Settings.instance.getMirror(),
          state);

        if (structureName.isHut())
        {
            Network.getNetwork().sendToServer(msg);
        }
        else
        {
            Minecraft.getInstance().tell(new WindowBuildDecoration(msg, Settings.instance.getPosition(), structureName)::open);
        }
    }

    @Override
    public boolean hasPermission()
    {
        return true;
    }

    @Override
    public boolean pasteDirectly()
    {
        return false;
    }

    @Override
    public void paste(final StructureName name, final boolean complete)
    {
        final BlockPos offset = Settings.instance.getActiveStructure().getPrimaryBlockOffset();
        ;
        final BlockState state = Settings.instance.getActiveStructure().getBlockState(offset).getBlockState();

        Network.getNetwork().sendToServer(new BuildToolPasteMessage(
          name.toString(),
          name.toString(),
          Settings.instance.getPosition(),
          Settings.instance.getRotation(),
          name.isHut(),
          Settings.instance.getMirror(),
          complete,
          state));
    }

    @Override
    public void checkAndPlace()
    {
        final List<PlacementError> placementErrorList = new ArrayList<>();
        final String schemName = Settings.instance.getStaticSchematicName();

        if (schemName.contains("supplyship") || schemName.contains("nethership"))
        {
            if (ItemSupplyChestDeployer.canShipBePlaced(Minecraft.getInstance().level, Settings.instance.getPosition(),
              Settings.instance.getActiveStructure(),
              placementErrorList,
              Minecraft.getInstance().player))
            {
                super.pasteNice();
            }
            else
            {
                MessageUtils.format(WARNING_SUPPLY_SHIP_IN_WATER).sendTo(Minecraft.getInstance().player);
            }
        }
        else if (schemName.contains("supplycamp"))
        {
            if (ItemSupplyCampDeployer.canCampBePlaced(Minecraft.getInstance().level, Settings.instance.getPosition(),
              placementErrorList,
              Minecraft.getInstance().player))
            {
                super.pasteNice();
            }
        }

        HighlightManager.clearCategory(RENDER_BOX_CATEGORY);
        if (!placementErrorList.isEmpty())
        {
            MessageUtils.format(WARNING_SUPPLY_BUILDING_BAD_BLOCKS).sendTo(Minecraft.getInstance().player);

            for (final PlacementError error : placementErrorList)
            {
                HighlightManager.addRenderBox(RENDER_BOX_CATEGORY, new HighlightManager.TimedBoxRenderData()
                  .setPos(error.getPos())
                  .setRemovalTimePoint(Minecraft.getInstance().level.getGameTime() + 120 * 20 * 60)
                  .addText(new TranslationTextComponent(PARTIAL_WARNING_SUPPLY_BUILDING_ERROR + error.getType().toString().toLowerCase()).getString())
                  .setColor(0xFF0000));
            }
        }

        if (!Screen.hasShiftDown())
        {
            super.cancelClicked();
        }
    }

    @Override
    public void cancelClicked()
    {
        super.cancelClicked();
        HighlightManager.clearCategory(RENDER_BOX_CATEGORY);
    }

    @Override
    public boolean hasMatchingBlock(@NotNull final PlayerInventory inventory, final String hut)
    {
        //todo 1.19 remove this
        final String name = hut.equals("citizen") ? "home" : hut;
        return InventoryUtils.hasItemInProvider(inventory.player,
          item -> item.getItem() instanceof BlockItem
                    && StructureName.HUTS.contains(hut)
                    && ((BlockItem) item.getItem()).getBlock() == IBuildingRegistry.getInstance().getValue(new ResourceLocation(Constants.MOD_ID, name)).getBuildingBlock());
    }
}
