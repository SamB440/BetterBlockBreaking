package com.github.cheesesoftware.betterblockbreaking;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.github.cheesesoftware.betterblockbreaking.block.DamageBlock;
import com.github.cheesesoftware.betterblockbreaking.task.KeepBlockDamageAliveTask;
import com.github.cheesesoftware.betterblockbreaking.task.RemoveOldDamagedBlocksTask;
import com.github.cheesesoftware.betterblockbreaking.task.ShowCurrentBlockDamageTask;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunPlugin;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.SlimefunItem;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import net.minecraft.server.v1_16_R3.BlockPosition;
import net.minecraft.server.v1_16_R3.Entity;
import net.minecraft.server.v1_16_R3.EntityChicken;
import net.minecraft.server.v1_16_R3.EntityLiving;
import net.minecraft.server.v1_16_R3.EntityTNTPrimed;
import net.minecraft.server.v1_16_R3.EntityTypes;
import net.minecraft.server.v1_16_R3.PacketPlayInBlockDig;
import net.minecraft.server.v1_16_R3.PacketPlayOutBlockBreakAnimation;
import net.minecraft.server.v1_16_R3.WorldServer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.v1_16_R3.CraftServer;
import org.bukkit.craftbukkit.v1_16_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class BetterBlockBreaking extends JavaPlugin implements Listener {

    public static int blockDamageUpdateDelay = 5 * 20; // seconds * ticks
    private ProtocolManager protocolManager;
    private int removeOldDamagedBlocksTaskId = -1;
    private boolean useCustomExplosions = true;

    private FileConfiguration customConfig = null;
    private File customConfigFile = null;

    public Plugin worldGuard = null;
    public Plugin slimefun = null;

    public HashMap<Location, DamageBlock> damageBlocks = new HashMap<>();

    public void onEnable() {
        this.worldGuard = this.getWorldGuard();
        this.slimefun = this.getSlimefun();

        this.saveDefaultConfig();
        this.reloadCustomConfig();

        Bukkit.getServer().getPluginManager().registerEvents(this, this);

        BukkitTask task = new RemoveOldDamagedBlocksTask(this).runTaskTimer(this, 0, 20);
        this.removeOldDamagedBlocksTaskId = task.getTaskId();
    }

    public void onLoad() {
        protocolManager = ProtocolLibrary.getProtocolManager();
        protocolManager.addPacketListener(new PacketAdapter(this, ListenerPriority.NORMAL, PacketType.Play.Client.BLOCK_DIG) {
            @Override
            public void onPacketReceiving(PacketEvent e) {
                if (e.getPacketType() == PacketType.Play.Client.BLOCK_DIG) {
                    final PacketContainer packet = e.getPacket();
                    final PacketEvent event = e;
                    final HashMap<Location, DamageBlock> damageBlocks = ((BetterBlockBreaking) plugin).damageBlocks;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        StructureModifier<PacketPlayInBlockDig.EnumPlayerDigType> data = packet.getSpecificModifier(PacketPlayInBlockDig.EnumPlayerDigType.class);
                        StructureModifier<BlockPosition> dataTemp = packet.getSpecificModifier(BlockPosition.class);

                        PacketPlayInBlockDig.EnumPlayerDigType type = data.getValues().get(0);
                        Player player = event.getPlayer();
                        BlockPosition pos = dataTemp.getValues().get(0);

                        Location posLocation = new Location(player.getWorld(), pos.getX(), pos.getY(), pos.getZ());

                        if (worldGuard != null && !canBuild(player, posLocation)) {
                            return;
                        }

                        if (player.getGameMode() == GameMode.SURVIVAL) {

                            DamageBlock damageBlock = damageBlocks.get(posLocation);
                            if (damageBlock == null) {
                                damageBlock = new DamageBlock(posLocation);

                                // Prevent block duplication in Slimefun
                                if (slimefun != null) {
                                    SlimefunItem sfItem = BlockStorage.check(damageBlock.getLocation().getBlock());
                                    if (sfItem != null)
                                        return;
                                }

                                damageBlocks.put(posLocation, damageBlock);
                            }

                            if (type == PacketPlayInBlockDig.EnumPlayerDigType.START_DESTROY_BLOCK) {
                                // Clean old task
                                player.setMetadata("BlockBeginDestroy", new FixedMetadataValue(plugin, new Date()));
                                if (player.hasMetadata("showCurrentDamageTaskId")) {
                                    Bukkit.getScheduler().cancelTask(player.getMetadata("showCurrentDamageTaskId").get(0).asInt());
                                    player.removeMetadata("showCurrentDamageTaskId", plugin);
                                }

                                damageBlock.resetFade();

                                // Prevent duplicate animations on the same block when player doesn't stop breaking
                                if (!damageBlock.isDamaged())
                                    damageBlock.isNoCancel = true;

                                // Start new task
                                BukkitTask task = new ShowCurrentBlockDamageTask(player, damageBlock).runTaskTimer(plugin, 0, 2);
                                player.setMetadata("showCurrentDamageTaskId", new FixedMetadataValue(plugin, task.getTaskId()));

                            } else if (type == PacketPlayInBlockDig.EnumPlayerDigType.ABORT_DESTROY_BLOCK || type == PacketPlayInBlockDig.EnumPlayerDigType.STOP_DESTROY_BLOCK) {

                                // Clean old tasks
                                if (player.hasMetadata("showCurrentDamageTaskId")) {
                                    Bukkit.getScheduler().cancelTask(player.getMetadata("showCurrentDamageTaskId").get(0).asInt());
                                    player.removeMetadata("showCurrentDamageTaskId", plugin);
                                }

                                // Player cancelled breaking
                                if (damageBlock.isNoCancel && type == PacketPlayInBlockDig.EnumPlayerDigType.ABORT_DESTROY_BLOCK) {

                                    // Load block "monster", used for displaying the damage on the block
                                    WorldServer world = ((CraftWorld) damageBlock.getWorld()).getHandle();
                                    EntityLiving entity = damageBlock.getEntity();
                                    if (entity == null) {
                                        entity = new EntityChicken(EntityTypes.CHICKEN, world);
                                        world.addEntity(entity, SpawnReason.CUSTOM);
                                        damageBlock.setEntity(entity);
                                    }

                                    // Send damage packet
                                    float currentDamage = damageBlock.getDamage();
                                    ((CraftServer) plugin.getServer()).getHandle().sendPacketNearby(null, posLocation.getX(), posLocation.getY(), posLocation.getZ(), 120, world.getDimensionKey(),
                                            new PacketPlayOutBlockBreakAnimation(damageBlock.getEntity().getId(), pos, (int) currentDamage));

                                    // Cancel old keep-damage-alive task
                                    if (damageBlock.keepBlockDamageAliveTaskId != -1) {
                                        Bukkit.getScheduler().cancelTask(damageBlock.keepBlockDamageAliveTaskId);
                                        damageBlock.keepBlockDamageAliveTaskId = -1;
                                    }

                                    // Start the task which prevents block damage from disappearing
                                    BukkitTask aliveTask = new KeepBlockDamageAliveTask((JavaPlugin) plugin, damageBlock).runTaskTimer(plugin, BetterBlockBreaking.blockDamageUpdateDelay,
                                            BetterBlockBreaking.blockDamageUpdateDelay);
                                    damageBlock.keepBlockDamageAliveTaskId = aliveTask.getTaskId();
                                }
                                damageBlock.isNoCancel = false;

                                // Clean metadata
                                player.removeMetadata("BlockBeginDestroy", plugin);
                            }
                        }
                    });
                }
            }
        });
    }

    public void onDisable() {
        Bukkit.getScheduler().cancelTask(this.removeOldDamagedBlocksTaskId);
    }

    public void reloadCustomConfig() {
        if (customConfigFile == null) {
            customConfigFile = new File(getDataFolder(), "config.yml");
        }
        customConfig = YamlConfiguration.loadConfiguration(customConfigFile);

        if (customConfig.contains("millisecondsBeforeBeginFade"))
            RemoveOldDamagedBlocksTask.millisecondsBeforeBeginFade = customConfig.getLong("millisecondsBeforeBeginFade");

        if (customConfig.contains("millisecondsBetweenFade"))
            RemoveOldDamagedBlocksTask.millisecondsBetweenFade = customConfig.getLong("millisecondsBetweenFade");

        if (customConfig.contains("damageDecreasePerFade"))
            RemoveOldDamagedBlocksTask.damageDecreasePerFade = customConfig.getInt("damageDecreasePerFade");

        if (customConfig.contains("useCustomExplosions"))
            this.useCustomExplosions = customConfig.getBoolean("useCustomExplosions");

        // Look for defaults in the jar
        Reader defConfigStream;
        defConfigStream = new InputStreamReader(this.getResource("config.yml"), StandardCharsets.UTF_8);
        YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(defConfigStream);
        customConfig.setDefaults(defConfig);
    }

    public FileConfiguration getCustomConfig() {
        if (customConfig == null) {
            reloadCustomConfig();
        }
        return customConfig;
    }

    public void saveCustomConfig() {
        if (customConfig == null || customConfigFile == null) {
            return;
        }
        try {
            getCustomConfig().save(customConfigFile);
        } catch (IOException ex) {
            getLogger().log(Level.SEVERE, "Could not save config to " + customConfigFile, ex);
        }
    }

    public void saveDefaultConfig() {
        if (customConfigFile == null) {
            customConfigFile = new File(getDataFolder(), "config.yml");
        }

        if (!customConfigFile.exists()) {
            this.saveResource("config.yml", false);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;
        Location location = clickedBlock.getLocation();
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            DamageBlock block = damageBlocks.get(location);
            if (block != null) {
                debug("Block has damage @ " + location + ": " + block.getDamage() + ".");
            } else {
                debug("No block damage at location: " + location + ".");
            }
        }
    }

    @EventHandler
    public void onPlayerDestroyBlock(BlockBreakEvent event) {
        Block block = event.getBlock();
        this.getDamageBlock(block.getLocation()).removeAllDamage();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (this.useCustomExplosions && !event.isCancelled() && event.blockList().size() > 0) {
            final List<Block> blocks = new ArrayList<>(event.blockList());
            event.blockList().clear();
            event.setYield(0);

            final EntityExplodeEvent e = event;
            final Location explosion = event.getLocation();
            final Map<Location, Material> materials = new HashMap<>();
            for (Block block : blocks)
                materials.put(block.getLocation(), block.getType());

            Bukkit.getScheduler().runTask(this, () -> {
                Random random = ThreadLocalRandom.current();
                for (Block block : blocks) {

                    WorldServer world = ((CraftWorld) block.getWorld()).getHandle();
                    BlockPosition pos = new BlockPosition(block.getX(), block.getY(), block.getZ());

                    if (block.getType() == Material.TNT) {
                        Entity entity = ((CraftEntity) e.getEntity()).getHandle();
                        EntityLiving shit = entity == null ? null
                                : (entity instanceof EntityTNTPrimed ? ((EntityTNTPrimed) entity).getSource() : (entity instanceof EntityLiving ? (EntityLiving) entity : null));
                        EntityTNTPrimed entitytntprimed = new EntityTNTPrimed(world, (float) pos.getX() + 0.5F, (float) pos.getY() + 0.5F,
                                (float) pos.getZ() + 0.5F, shit);

                        entitytntprimed.fireTicks = world.random.nextInt(Math.max(entitytntprimed.fireTicks / 4, 1)) + entitytntprimed.fireTicks / 8;
                        world.addEntity(entitytntprimed);
                    }

                    double distance = explosion.distance(block.getLocation());
                    DamageBlock damageBlock = getDamageBlock(block.getLocation());
                    damageBlock.setDamage((float) ((14 + (random.nextInt(5) - 2) - (2.0f * distance)) + damageBlock.getDamage()), null);
                }
            });
        }
    }

    public DamageBlock getDamageBlock(Location location) {
        DamageBlock damageBlock = this.damageBlocks.get(location);
        if (damageBlock == null) {
            damageBlock = new DamageBlock(location);
            damageBlocks.put(location, damageBlock);
        }
        return damageBlock;
    }

    /**
     * @deprecated Use discouraged
     * @return Instance of BetterBlockBreaking, via {@link JavaPlugin#getPlugin(Class)}
     */
    @Deprecated
    public static BetterBlockBreaking getPlugin() {
        return JavaPlugin.getPlugin(BetterBlockBreaking.class);
    }

    public boolean canBuild(Player player, Location location) {
        if (worldGuard != null) {
            RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
            com.sk89q.worldedit.util.Location loc = BukkitAdapter.adapt(location);
            if (!hasBypass(player, location)) {
                return query.testState(loc, WorldGuardPlugin.inst().wrapPlayer(player), Flags.BLOCK_BREAK);
            }
            return true;
        }
        return false;
    }

    public boolean hasBypass(Player player, Location location) {
        if (location.getWorld() == null) return true;
        LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
        World world = BukkitAdapter.adapt(location.getWorld());
        return WorldGuard.getInstance().getPlatform().getSessionManager().hasBypass(localPlayer, world);
    }

    private Plugin getWorldGuard() {
        Plugin plugin = getServer().getPluginManager().getPlugin("WorldGuard");

        // WorldGuard may not be loaded
        if (!(plugin instanceof WorldGuardPlugin)) {
            getServer().getLogger().log(Level.INFO, "[BetterBlockBreaking] WorldGuard could not be loaded. Disabling interaction.");
            return null; // Maybe you want throw an exception instead
        } else
            getServer().getLogger().log(Level.INFO, "[BetterBlockBreaking] Enabled WorldGuard interaction.");

        return plugin;
    }

    private Plugin getSlimefun() {
        Plugin plugin = getServer().getPluginManager().getPlugin("Slimefun");

        // Slimefun may not be loaded
        if (!(plugin instanceof SlimefunPlugin)) {
            getServer().getLogger().log(Level.INFO, "[BetterBlockBreaking] Slimefun could not be loaded. Disabling interaction.");
            return null; // Maybe you want throw an exception instead
        } else
            getServer().getLogger().log(Level.INFO, "[BetterBlockBreaking] Enabled Slimefun interaction.");

        return plugin;
    }

    public void debug(String debug) {
        if (customConfig.getBoolean("debug")) {
            this.getLogger().info("[Debug] " + debug);
        }
    }
}
