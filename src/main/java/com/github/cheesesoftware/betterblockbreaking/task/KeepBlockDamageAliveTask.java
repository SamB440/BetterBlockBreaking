package com.github.cheesesoftware.betterblockbreaking.task;

import com.github.cheesesoftware.betterblockbreaking.BetterBlockBreaking;
import com.github.cheesesoftware.betterblockbreaking.block.DamageBlock;
import com.github.cheesesoftware.betterblockbreaking.util.ReflectiveUtils;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class KeepBlockDamageAliveTask extends BukkitRunnable {

    private final JavaPlugin plugin;
    private final DamageBlock block;

    public KeepBlockDamageAliveTask(JavaPlugin plugin, DamageBlock block) {
        this.plugin = plugin;
        this.block = block;
    }

    @Override
    public void run() {
        if (block.isDamaged() && block.getEntity() != null && ((BetterBlockBreaking)plugin).damageBlocks.containsKey(block.getLocation())) {
            float currentDamage = block.getDamage();
            ReflectiveUtils.sendDamagePacket(block, (int) currentDamage);
        } else this.cancel();
    }
}