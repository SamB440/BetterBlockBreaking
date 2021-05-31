package com.github.cheesesoftware.betterblockbreaking.block;

import com.github.cheesesoftware.betterblockbreaking.BetterBlockBreaking;
import com.github.cheesesoftware.betterblockbreaking.task.KeepBlockDamageAliveTask;
import net.minecraft.server.v1_16_R3.BlockPosition;
import net.minecraft.server.v1_16_R3.EntityChicken;
import net.minecraft.server.v1_16_R3.EntityLiving;
import net.minecraft.server.v1_16_R3.EntityTypes;
import net.minecraft.server.v1_16_R3.IBlockData;
import net.minecraft.server.v1_16_R3.PacketPlayOutBlockBreakAnimation;
import net.minecraft.server.v1_16_R3.PacketPlayOutBlockChange;
import net.minecraft.server.v1_16_R3.TileEntity;
import net.minecraft.server.v1_16_R3.WorldServer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_16_R3.CraftServer;
import org.bukkit.craftbukkit.v1_16_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

public class DamageBlock {

    private final Location location;
    private Date dateDamaged = null;
    private Date lastFade = null;
    private float damage = 0;
    private EntityLiving entity = null;

    public boolean isNoCancel = false;
    public int keepBlockDamageAliveTaskId = -1;

    // public int showCurrentDamageTaskId = -1;

    public DamageBlock(Location location) {
        this.location = location;
    }

    public Location getLocation() {
        return this.location;
    }

    public Date getDamageDate() {
        return this.dateDamaged;
    }

    public Date getLastFadeDate() {
        return this.lastFade;
    }

    public void resetFade() {
        this.dateDamaged = null;
        this.lastFade = null;
    }

    public EntityLiving getEntity() {
        return this.entity;
    }

    public float getDamage() {
        return this.damage;
    }

    public boolean isDamaged() {
        return this.damage > 0;
    }

    public World getWorld() {
        return this.location.getWorld();
    }

    public int getX() {
        return this.location.getBlockX();
    }

    public int getY() {
        return this.location.getBlockY();
    }

    public int getZ() {
        return this.location.getBlockZ();
    }

    private void setDamageDate() {
        this.dateDamaged = new Date();
    }

    public void setLastFadeDate() {
        this.lastFade = new Date();
    }

    public void setDamage(float damage, @Nullable Player breaker) {
        this.damage = damage;
        this.setDamageDate();

        WorldServer world = ((CraftWorld) this.location.getWorld()).getHandle();
        BlockPosition pos = new BlockPosition(getX(), getY(), getZ());

        // || it is a block with no strength, break immediately
        IBlockData blockData = world.getType(pos);
        if (damage >= 10 || (damage > 0 && blockData.getBlock().getDurability() <= 0)) {
            this.breakBlock(breaker);
        } else {
            // Load block "monster", used for displaying the damage on the block
            if (this.entity == null) {
                this.entity = new EntityChicken(EntityTypes.CHICKEN, world);
                world.addEntity(entity, SpawnReason.CUSTOM);
            }

            // Send damage packet
            if (!this.isNoCancel) {
                ((CraftServer) Bukkit.getServer()).getHandle().sendPacketNearby(null, getX(), getY(), getZ(), 120, world.getDimensionKey(),
                        new PacketPlayOutBlockBreakAnimation(entity.getId(), pos, (int) this.damage));
                BetterBlockBreaking.getPlugin().debug("Sent damage packet (" + this.damage + ").");
            }

            // Cancel old task
            if (this.keepBlockDamageAliveTaskId != -1) {
                Bukkit.getScheduler().cancelTask(this.keepBlockDamageAliveTaskId);
            }

            // Start the task which prevents block damage from disappearing
            BukkitTask aliveTask = new KeepBlockDamageAliveTask(BetterBlockBreaking.getPlugin(), this).runTaskTimer(BetterBlockBreaking.getPlugin(), BetterBlockBreaking.blockDamageUpdateDelay,
                    BetterBlockBreaking.blockDamageUpdateDelay);
            this.keepBlockDamageAliveTaskId = aliveTask.getTaskId();
        }
    }

    public void breakBlock(Player breaker) {
        Block block = this.location.getBlock();
        if (breaker != null) {
            WorldServer world = ((CraftWorld) this.location.getWorld()).getHandle();
            BlockPosition pos = new BlockPosition(getX(), getY(), getZ());

            if (block.getType() != org.bukkit.Material.AIR) {

                // Call an additional BlockBreakEvent to make sure other plugins can cancel it
                BlockBreakEvent event = new BlockBreakEvent(block, breaker);
                Bukkit.getServer().getPluginManager().callEvent(event);

                if (event.isCancelled()) {
                    // Let the client know the block still exists
                    ((CraftPlayer) breaker).getHandle().playerConnection.sendPacket(new PacketPlayOutBlockChange(world, pos));
                    // Update any tile entity data for this block
                    TileEntity tileentity = world.getTileEntity(pos);
                    if (tileentity != null) {
                        ((CraftPlayer) breaker).getHandle().playerConnection.sendPacket(tileentity.getUpdatePacket());
                    }

                    this.removeAllDamage();
                } else {
                    this.removeAllDamage();

                    // Play block break sound
                    Sound breakSound = block.getBlockData().getSoundGroup().getBreakSound();
                    breaker.getWorld().playSound(block.getLocation(), breakSound, 2.0f, 1.0f);

                    // Use the proper function to break block, this also applies any effects the item the player is holding has on the block
                    ((CraftPlayer) breaker).getHandle().playerInteractManager.breakBlock(pos);
                }
            }
        } else {
            block.setType(Material.AIR);
            this.removeAllDamage();
        }
    }

    public void removeAllDamage() {

        WorldServer world = ((CraftWorld) this.getWorld()).getHandle();
        BlockPosition pos = new BlockPosition(this.getX(), this.getY(), this.getZ());

        // Clean tasks
        if (keepBlockDamageAliveTaskId != -1)
            Bukkit.getScheduler().cancelTask(keepBlockDamageAliveTaskId);
        // if (this.showCurrentDamageTaskId != -1)
        // Bukkit.getScheduler().cancelTask(showCurrentDamageTaskId);

        if (this.entity == null) {
            this.entity = new EntityChicken(EntityTypes.CHICKEN, world);
            world.addEntity(entity, SpawnReason.CUSTOM);
        }

        // Send a damage packet to remove the damage of the block
        ((CraftServer) Bukkit.getServer()).getHandle().sendPacketNearby(null, this.getX(), this.getY(), this.getZ(), 120, world.getDimensionKey(),
                new PacketPlayOutBlockBreakAnimation(this.getEntity().getId(), pos, -1));

        this.getEntity().die();
        BetterBlockBreaking.getPlugin().damageBlocks.remove(getLocation());
    }

    public void setEntity(EntityLiving entity) {
        this.entity = entity;
    }
}
