package com.minecolonies.coremod.colony.colonyEvents.raidEvents;

import com.minecolonies.api.colony.ColonyState;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.colonyEvents.EventStatus;
import com.minecolonies.api.colony.colonyEvents.IColonyCampFireRaidEvent;
import com.minecolonies.api.colony.colonyEvents.IColonyEvent;
import com.minecolonies.api.colony.colonyEvents.IColonyRaidEvent;
import com.minecolonies.api.entity.mobs.RaiderMobUtils;
import com.minecolonies.api.entity.pathfinding.PathResult;
import com.minecolonies.api.sounds.RaidSounds;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.api.util.constant.NbtTagConstants;
import com.minecolonies.coremod.colony.colonyEvents.raidEvents.barbarianEvent.Horde;
import com.minecolonies.coremod.colony.colonyEvents.raidEvents.pirateEvent.ShipBasedRaiderUtils;
import com.minecolonies.coremod.network.messages.client.PlayAudioMessage;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.pathfinding.Path;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.BossInfo;
import net.minecraft.world.server.ServerBossInfo;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.minecolonies.api.util.constant.ColonyConstants.SMALL_HORDE_SIZE;
import static com.minecolonies.api.util.constant.Constants.TAG_COMPOUND;
import static com.minecolonies.api.util.constant.NbtTagConstants.*;
import static com.minecolonies.api.util.constant.TranslationConstants.*;
import static com.minecolonies.coremod.colony.colonyEvents.raidEvents.pirateEvent.PirateRaidEvent.TAG_DAYS_LEFT;

/**
 * Horde raid event for the colony, triggers a horde that spawn and attack the colony.
 */
public abstract class HordeRaidEvent implements IColonyRaidEvent, IColonyCampFireRaidEvent
{
    /**
     * Spacing between waypoints
     */
    private static final int WAYPOINT_SPACING = 20;

    /**
     * The max distance a barbarian is allowed to spawn from the original spawn position
     */
    public static int MAX_SPAWN_DEVIATION = 300;

    /**
     * The max distance to search for a loaded blockpos on a respawn try
     */
    public static int MAX_RESPAWN_DEVIATION = 10 * 16;

    /**
     * The minimum distance to the colony center where mobs are allowed to spawn
     */
    public static int MIN_CENTER_DISTANCE = 100;

    /**
     * The amount of entities overall
     */
    protected Horde horde;

    /**
     * The raids visual raidbar
     */
    protected final ServerBossInfo raidBar = new ServerBossInfo(new StringTextComponent("Colony Raid"), BossInfo.Color.RED, BossInfo.Overlay.NOTCHED_10);

    /**
     * The references to living raiders left
     */
    protected Map<Entity, UUID> normal  = new WeakHashMap<>();
    protected Map<Entity, UUID> archers = new WeakHashMap<>();
    protected Map<Entity, UUID> boss    = new WeakHashMap<>();

    /**
     * List of respawns to do
     */
    private List<Tuple<EntityType<?>, BlockPos>> respawns = new ArrayList<>();

    /**
     * Currently active campfires
     */
    private List<BlockPos> campFires = new ArrayList<>();

    /**
     * The related colony
     */
    private IColony colony;

    /**
     * The events id
     */
    private int id;

    /**
     * The events starting spawnpoint
     */
    private BlockPos spawnPoint;

    /**
     * Status of the event
     */
    protected EventStatus status = EventStatus.STARTING;

    /**
     * Days the event can last, to make sure it eventually despawns.
     */
    private int daysToGo = 3;

    /**
     * Time the campfires are active
     */
    private int campFireTime = 0;

    /**
     * The path result towards the intended spawn point
     */
    private PathResult spawnPathResult;

    /**
     * Waypoints helping raiders travel
     */
    private List<BlockPos> wayPoints = new ArrayList<>();

    public HordeRaidEvent(IColony colony)
    {
        this.colony = colony;
        id = colony.getEventManager().getAndTakeNextEventID();
    }

    @Override
    public void setSpawnPoint(final BlockPos spawnPoint)
    {
        this.spawnPoint = spawnPoint;
    }

    @Override
    public BlockPos getSpawnPos()
    {
        return spawnPoint;
    }

    @Override
    public List<Entity> getEntities()
    {
        List<Entity> entities = new ArrayList<>();
        entities.addAll(archers.keySet());
        entities.addAll(boss.keySet());
        entities.addAll(normal.keySet());
        return entities;
    }

    @Override
    public EventStatus getStatus()
    {
        return status;
    }

    @Override
    public void setStatus(final EventStatus status)
    {
        this.status = status;
    }

    @Override
    public int getID()
    {
        return id;
    }

    @Override
    public void setColony(@NotNull final IColony colony)
    {
        this.colony = colony;
    }

    /**
     * Called when an entity is removed
     *
     * @param entity the entity to unregister.
     */
    @Override
    public void unregisterEntity(final Entity entity)
    {
        if (!(archers.containsKey(entity) || boss.containsKey(entity) || normal.containsKey(entity)) || status != EventStatus.PROGRESSING
              || colony.getState() != ColonyState.ACTIVE)
        {
            return;
        }

        archers.remove(entity);
        boss.remove(entity);
        normal.remove(entity);

        // Respawn as a new entity in a loaded chunk, if not too close.
        respawns.add(new Tuple<>(entity.getType(), new BlockPos(entity.position())));
    }

    /**
     * Spawn a specific horde.
     *
     * @param spawnPos        the pos to spawn them at.
     * @param colony          the colony to spawn them for.
     * @param id              the raid event id.
     * @param numberOfArchers the archers.
     * @param numberOfBosses  the bosses.
     * @param numberOfRaiders the normal raiders.
     */
    protected void spawnHorde(final BlockPos spawnPos, final IColony colony, final int id, final int numberOfBosses, final int numberOfArchers, final int numberOfRaiders)
    {
        RaiderMobUtils.spawn(getNormalRaiderType(), numberOfRaiders, spawnPos, colony.getWorld(), colony, id);
        RaiderMobUtils.spawn(getBossRaiderType(), numberOfBosses, spawnPos, colony.getWorld(), colony, id);
        RaiderMobUtils.spawn(getArcherRaiderType(), numberOfArchers, spawnPos, colony.getWorld(), colony, id);
    }

    /**
     * Prepares the horde event, makes them wait at campfires for a while,deciding on their plans.
     */
    private void prepareEvent()
    {
        if (--campFireTime <= 0)
        {
            // Start raiding
            status = EventStatus.PROGRESSING;
        }
    }

    /**
     * Spawns a few campfires around the pos, depending on raid size
     *
     * @param pos the pos to spawn it at.
     */
    private void spawnCampFires(final BlockPos pos)
    {
        final int fireCount = Math.max(1, horde.hordeSize / 5);
        for (int i = 0; i < fireCount; i++)
        {
            for (int tries = 0; tries < 3; tries++)
            {
                BlockPos spawn = BlockPosUtil.getRandomPosition(colony.getWorld(), pos, BlockPos.ZERO, 3, 7);
                if (spawn != BlockPos.ZERO)
                {
                    colony.getWorld().setBlockAndUpdate(spawn, Blocks.CAMPFIRE.defaultBlockState());
                    campFires.add(spawn);
                    break;
                }
            }
        }
    }

    @Override
    public void onFinish()
    {
        for (final Entity entity : getEntities())
        {
            entity.remove();
        }

        for (final BlockPos pos : campFires)
        {
            colony.getWorld().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }

        raidBar.setVisible(false);
        raidBar.removeAllPlayers();

        if (horde.hordeSize > 0)
        {
            MessageUtils.format(ALL_BARBARIANS_KILLED_MESSAGE, colony.getName()).sendTo(colony).forManagers();
        }
    }

    @Override
    public void onNightFall()
    {
        daysToGo--;
        if (daysToGo < 0)
        {
            status = EventStatus.DONE;
        }
    }

    public void setHorde(final Horde horde)
    {
        this.horde = horde;
    }

    /**
     * Returns a random campfire
     *
     * @return a random campfire.
     */
    public BlockPos getRandomCampfire()
    {
        if (campFires.isEmpty())
        {
            return null;
        }
        return campFires.get(colony.getWorld().random.nextInt(campFires.size()));
    }

    @Override
    public void onStart()
    {
        if (spawnPathResult != null && spawnPathResult.isDone())
        {
            final Path path = spawnPathResult.getPath();
            if (path != null && path.canReach())
            {
                spawnPoint = path.getEndNode().asBlockPos();
            }
            this.wayPoints = ShipBasedRaiderUtils.createWaypoints(colony.getWorld(), path, WAYPOINT_SPACING);
        }

        final BlockPos spawnPos = ShipBasedRaiderUtils.getLoadedPositionTowardsCenter(spawnPoint, colony, MAX_SPAWN_DEVIATION, spawnPoint, MIN_CENTER_DISTANCE, 10);
        if (spawnPos == null)
        {
            status = EventStatus.CANCELED;
            return;
        }

        status = EventStatus.PREPARING;
        spawnCampFires(spawnPos);
        final double dist = colony.getCenter().distSqr(spawnPos);
        if (dist < MIN_CENTER_DISTANCE * MIN_CENTER_DISTANCE)
        {
            campFireTime = 6;
        }
        else
        {
            campFireTime = 3;
        }

        spawnHorde(spawnPos, colony, id, horde.numberOfBosses, horde.numberOfArchers, horde.numberOfRaiders);

        updateRaidBar();

        MessageUtils.format(RAID_EVENT_MESSAGE + horde.getMessageID(), BlockPosUtil.calcDirection(colony.getCenter(), spawnPoint), colony.getName()).sendTo(colony).forManagers();

        PlayAudioMessage audio = new PlayAudioMessage(horde.initialSize <= SMALL_HORDE_SIZE ? RaidSounds.WARNING_EARLY : RaidSounds.WARNING, SoundCategory.RECORDS);
        PlayAudioMessage.sendToAll(getColony(), false, false, audio);
    }

    /**
     * Get the assigned colony.
     *
     * @return the colony.
     */
    public IColony getColony()
    {
        return colony;
    }

    /**
     * Updates the raid bar
     */
    protected void updateRaidBar()
    {
        final ITextComponent directionName = BlockPosUtil.calcDirection(colony.getCenter(), spawnPoint);
        raidBar.setName(getDisplayName().append(" - ").append(directionName));
        for (final PlayerEntity player : colony.getPackageManager().getCloseSubscribers())
        {
            raidBar.addPlayer((ServerPlayerEntity) player);
        }
        raidBar.setVisible(true);
    }

    /**
     * Gets the raids display name
     *
     * @return
     */
    protected abstract IFormattableTextComponent getDisplayName();

    @Override
    public void onUpdate()
    {
        if (status == EventStatus.PREPARING)
        {
            prepareEvent();
        }

        updateRaidBar();

        colony.getRaiderManager().setNightsSinceLastRaid(0);

        if (horde.hordeSize <= 0)
        {
            status = EventStatus.DONE;
        }

        if (!respawns.isEmpty())
        {
            for (final Tuple<EntityType<?>, BlockPos> entry : respawns)
            {
                final BlockPos spawnPos = ShipBasedRaiderUtils.getLoadedPositionTowardsCenter(entry.getB(), colony, MAX_RESPAWN_DEVIATION, spawnPoint, MIN_CENTER_DISTANCE, 10);
                if (spawnPos != null)
                {
                    RaiderMobUtils.spawn(entry.getA(), 1, spawnPos, colony.getWorld(), colony, id);
                }
            }
            respawns.clear();
            return;
        }

        if (boss.size() + archers.size() + normal.size() < horde.numberOfBosses + horde.numberOfRaiders + horde.numberOfArchers)
        {
            final BlockPos spawnPos = ShipBasedRaiderUtils.getLoadedPositionTowardsCenter(spawnPoint, colony, MAX_RESPAWN_DEVIATION, spawnPoint, MIN_CENTER_DISTANCE, 10);
            if (spawnPos != null)
            {
                spawnHorde(spawnPos, colony, id, horde.numberOfBosses - boss.size(), horde.numberOfArchers - archers.size(), horde.numberOfRaiders - normal.size());
            }
        }

        if (horde.numberOfBosses + horde.numberOfRaiders + horde.numberOfArchers < Math.round(horde.initialSize * 0.05))
        {
            status = EventStatus.DONE;
        }

        for (final Entity entity : getEntities())
        {
            if (!entity.isAlive() || !WorldUtil.isEntityBlockLoaded(colony.getWorld(), new BlockPos(entity.position())))
            {
                entity.remove();
                respawns.add(new Tuple<>(entity.getType(), new BlockPos(entity.position())));
                continue;
            }

            if (colony.getRaiderManager().areSpiesEnabled())
            {
                ((LivingEntity) entity).addEffect(new EffectInstance(Effects.GLOWING, 550));
            }
        }
    }

    /**
     * Sends the right horde message.
     */
    protected void sendHordeMessage()
    {
        int total = 0;

        for (IColonyEvent event : colony.getEventManager().getEvents().values())
        {
            if (event instanceof HordeRaidEvent)
            {
                total += ((HordeRaidEvent) event).horde.hordeSize;
            }
        }
        raidBar.setPercent((float) horde.hordeSize / horde.initialSize);

        if (total == 0)
        {
            MessageUtils.format(ALL_BARBARIANS_KILLED_MESSAGE, colony.getName()).sendTo(colony).forManagers();

            PlayAudioMessage audio = new PlayAudioMessage(horde.initialSize <= SMALL_HORDE_SIZE ? RaidSounds.VICTORY_EARLY : RaidSounds.VICTORY, SoundCategory.RECORDS);
            PlayAudioMessage.sendToAll(getColony(), false, true, audio);

            if (colony.getRaiderManager().getLostCitizen() == 0)
            {
                colony.getCitizenManager().updateModifier("raidwithoutdeath");
            }
        }
        else if (total > 0 && total <= SMALL_HORDE_SIZE)
        {
            MessageUtils.format(ONLY_X_BARBARIANS_LEFT_MESSAGE, total).sendTo(colony).forManagers();
        }
    }

    @Override
    public CompoundNBT serializeNBT()
    {
        CompoundNBT compound = new CompoundNBT();
        compound.putInt(TAG_EVENT_ID, id);
        BlockPosUtil.write(compound, TAG_SPAWN_POS, spawnPoint);
        ListNBT campFiresNBT = new ListNBT();

        for (final BlockPos pos : campFires)
        {
            campFiresNBT.add(BlockPosUtil.write(new CompoundNBT(), NbtTagConstants.TAG_POS, pos));
        }

        compound.put(TAG_CAMPFIRE_LIST, campFiresNBT);
        compound.putInt(TAG_EVENT_STATUS, status.ordinal());
        compound.putInt(TAG_DAYS_LEFT, daysToGo);
        horde.writeToNbt(compound);

        BlockPosUtil.writePosListToNBT(compound, TAG_WAYPOINT, wayPoints);
        return compound;
    }

    @Override
    public void deserializeNBT(final CompoundNBT compound)
    {
        id = compound.getInt(TAG_EVENT_ID);
        setHorde(Horde.loadFromNbt(compound));
        spawnPoint = BlockPosUtil.read(compound, TAG_SPAWN_POS);

        for (final INBT posCompound : compound.getList(TAG_CAMPFIRE_LIST, TAG_COMPOUND))
        {
            campFires.add(BlockPosUtil.read((CompoundNBT) posCompound, NbtTagConstants.TAG_POS));
        }

        status = EventStatus.values()[compound.getInt(TAG_EVENT_STATUS)];
        daysToGo = compound.getInt(TAG_DAYS_LEFT);
        wayPoints = BlockPosUtil.readPosListFromNBT(compound, TAG_WAYPOINT);
    }

    @Override
    public void setCampFireTime(final int time)
    {
        campFireTime = time;
    }

    @Override
    public void addSpawner(final BlockPos pos)
    {
        // do noting
    }

    @Override
    public List<BlockPos> getWayPoints()
    {
        return wayPoints;
    }

    /**
     * Set the pathing for this raids spawnpoint
     *
     * @param result pathing result to wait for
     */
    public void setSpawnPath(final PathResult result)
    {
        this.spawnPathResult = result;
    }
}
