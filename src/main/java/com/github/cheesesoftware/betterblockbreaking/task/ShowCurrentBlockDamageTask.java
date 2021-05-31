package com.github.cheesesoftware.betterblockbreaking.task;

import java.util.Date;

import com.github.cheesesoftware.betterblockbreaking.BetterBlockBreaking;
import com.github.cheesesoftware.betterblockbreaking.block.DamageBlock;
import net.minecraft.server.v1_16_R3.BlockPosition;
import net.minecraft.server.v1_16_R3.EntityPlayer;
import net.minecraft.server.v1_16_R3.IBlockData;
import net.minecraft.server.v1_16_R3.WorldServer;

import org.bukkit.craftbukkit.v1_16_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

public class ShowCurrentBlockDamageTask extends BukkitRunnable {

    private final Player player;
    private final DamageBlock damageBlock;

    public ShowCurrentBlockDamageTask(Player player, DamageBlock damageBlock) {
        this.player = player;
        this.damageBlock = damageBlock;
    }

    @Override
    public void run() {
        if (player.hasMetadata("BlockBeginDestroy")) {
            Date old = (Date) player.getMetadata("BlockBeginDestroy").get(0).value();
            Date now = new Date();
            long differenceMilliseconds = now.getTime() - old.getTime();

            WorldServer world = ((CraftWorld) player.getWorld()).getHandle();
            EntityPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
            BlockPosition pos = new BlockPosition(damageBlock.getX(), damageBlock.getY(), damageBlock.getZ());
            IBlockData blockData = world.getType(pos);
            net.minecraft.server.v1_16_R3.Block block = blockData.getBlock();

            float i = differenceMilliseconds / 20.0f;
            float f = 1000 * ((block.getDamage(blockData, nmsPlayer, world, pos) * i) / 240);
            f += damageBlock.getDamage();
            damageBlock.setDamage(f, player);
            player.setMetadata("BlockBeginDestroy", new FixedMetadataValue(BetterBlockBreaking.getPlugin(), new Date()));
        } else this.cancel();
    }
}
