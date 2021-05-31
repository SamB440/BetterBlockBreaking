package com.github.cheesesoftware.betterblockbreaking.util;

import com.github.cheesesoftware.betterblockbreaking.block.DamageBlock;
import net.minecraft.server.v1_16_R3.BlockPosition;
import net.minecraft.server.v1_16_R3.PacketPlayOutBlockBreakAnimation;
import net.minecraft.server.v1_16_R3.WorldServer;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_16_R3.CraftServer;
import org.bukkit.craftbukkit.v1_16_R3.CraftWorld;

public class ReflectiveUtils {

    public static void sendDamagePacket(DamageBlock block, int damage) {
        WorldServer worldServer = ((CraftWorld) block.getWorld()).getHandle();
        BlockPosition blockPosition = new BlockPosition(block.getX(), block.getY(), block.getZ());
        CraftServer craftServer = (CraftServer) Bukkit.getServer();
        craftServer.getHandle().sendPacketNearby(null, block.getX(), block.getY(), block.getZ(), 120, worldServer.getDimensionKey(),
                new PacketPlayOutBlockBreakAnimation(block.getEntity().getId(), blockPosition, damage));
    }
}
