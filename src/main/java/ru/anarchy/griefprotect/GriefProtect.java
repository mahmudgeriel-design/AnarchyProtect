package ru.anarchy.griefprotect;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class GriefProtect extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<Location, UUID> claimBlocks = new HashMap<>();
    private final Map<UUID, BossBar> playerBars = new HashMap<>();
    private final String PREFIX = "§b§lFrostWorld §8» §7";

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("gprotect") != null) {
            getCommand("gprotect").setExecutor(this);
        }

        // БЕСКОНЕЧНЫЙ ТАЙМЕР ДЛЯ ИНДИКАТОРА СВЕРХУ ЭКРАНА
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                Location loc = player.getLocation();
                Location activeCenter = null;

                // Проверка зоны спавна
                boolean isSpawn = loc.getWorld().getName().equalsIgnoreCase("world") 
                        && loc.getX() >= -150 && loc.getX() <= 150 
                        && loc.getZ() >= -150 && loc.getZ() <= 150;

                // Проверка регионов привата
                for (Location blockLoc : claimBlocks.keySet()) {
                    int radius = getRadius(blockLoc.getBlock().getType());
                    if (Math.abs(blockLoc.getBlockX() - loc.getBlockX()) <= radius && 
                        Math.abs(blockLoc.getBlockZ() - loc.getBlockZ()) <= radius) {
                        activeCenter = blockLoc;
                        break;
                    }
                }

                BossBar bossBar = playerBars.computeIfAbsent(player.getUniqueId(), id -> {
                    BossBar bar = Bukkit.createBossBar("", BarColor.WHITE, BarStyle.SOLID);
                    bar.addPlayer(player);
                    return bar;
                });

                bossBar.setProgress(0.0); // Прячем полоску, оставляем только текст

                if (isSpawn) {
                    bossBar.setTitle("§f[ §e§lСпавн §f]");
                } else if (activeCenter != null) { // Исправил опечатку тут
                    UUID ownerUUID = claimBlocks.get(activeCenter); // И тут
                    String owner = Bukkit.getOfflinePlayer(ownerUUID).getName();
                    bossBar.setTitle("§f[ §c§lРегион: §f" + owner + " §f]");
                } else {
                    bossBar.setTitle("§f[ §a§lСвободная зона §f]");
                }
            }
        }, 0L, 5L); 
    }

    @Override
    public void onDisable() {
        for (BossBar bar : playerBars.values()) {
            bar.removeAll();
        }
        playerBars.clear();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (playerBars.containsKey(uuid)) {
            playerBars.get(uuid).removeAll();
            playerBars.remove(uuid);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("griefprotect.admin")) {
            sender.sendMessage(PREFIX + "§cУ вас нет прав.");
            return true;
        }
        if (args.length >= 4 && args[0].equalsIgnoreCase("give")) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(PREFIX + "§cИгрок оффлайн.");
                return true;
            }
            int amount;
            try { 
                amount = Integer.parseInt(args[3]); 
            } catch (Exception e) { 
                sender.sendMessage(PREFIX + "§cНеверное количество.");
                return true; 
            }
            
            Material mat = Material.matchMaterial(args[2].toUpperCase() + "_BLOCK");
            if (mat == null || !isProtectBlock(mat)) {
                sender.sendMessage(PREFIX + "§cНеверный тип блока.");
                return true;
            }

            ItemStack item = new ItemStack(mat, amount);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§b§l⚡ КРИСТАЛЛ ПРИВАТА ⚡");
                item.setItemMeta(meta);
            }
            target.getInventory().addItem(item);
            sender.sendMessage(PREFIX + "§aВыдано!");
            return true;
        }
        sender.sendMessage("§c/gprotect give [ник] [iron/gold/diamond/emerald] [кол-во]");
        return true;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (!isProtectBlock(block.getType())) {
            for (Location blockLoc : claimBlocks.keySet()) {
                int radius = getRadius(blockLoc.getBlock().getType());
                if (Math.abs(blockLoc.getBlockX() - block.getX()) <= radius && Math.abs(blockLoc.getBlockZ() - block.getZ()) <= radius) {
                    if (!claimBlocks.get(blockLoc).equals(event.getPlayer().getUniqueId())) {
                        event.setCancelled(true);
                        event.getPlayer().sendMessage(PREFIX + "§cВы не можете строить на чужой территории!");
                        return;
                    }
                }
            }
            return;
        }
        claimBlocks.put(block.getLocation(), event.getPlayer().getUniqueId());
        event.getPlayer().sendMessage(PREFIX + "Блок установлен! Радиус: §b" + getRadius(block.getType()));
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (isProtectBlock(block.getType()) && claimBlocks.containsKey(block.getLocation())) {
            claimBlocks.remove(block.getLocation());
            event.getPlayer().sendMessage(PREFIX + "Приват снят.");
            return;
        }
        for (Location blockLoc : claimBlocks.keySet()) {
            int radius = getRadius(blockLoc.getBlock().getType());
            if (Math.abs(blockLoc.getBlockX() - block.getX()) <= radius && Math.abs(blockLoc.getBlockZ() - block.getZ()) <= radius) {
                if (!claimBlocks.get(blockLoc).equals(event.getPlayer().getUniqueId())) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block b : event.blockList()) {
            if (isProtectBlock(b.getType()) && claimBlocks.containsKey(b.getLocation())) {
                claimBlocks.remove(b.getLocation());
            }
        }
    }

    private boolean isProtectBlock(Material m) {
        return m == Material.IRON_BLOCK || m == Material.GOLD_BLOCK || m == Material.DIAMOND_BLOCK || m == Material.EMERALD_BLOCK;
    }

    private int getRadius(Material m) {
        return m == Material.IRON_BLOCK ? 5 : m == Material.GOLD_BLOCK ? 8 : m == Material.DIAMOND_BLOCK ? 12 : 16;
    }
}
