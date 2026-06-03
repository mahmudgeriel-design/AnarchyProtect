package ru.anarchy.griefprotect;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GriefProtect extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<UUID, String> lastRegion = new HashMap<>();
    private final String PREFIX = "§b§lFrostWorld §8» §7";

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("gprotect") != null) getCommand("gprotect").setExecutor(this);
    }

    @Override
    public void onDisable() {
        lastRegion.clear();
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
            } catch (NumberFormatException e) {
                sender.sendMessage(PREFIX + "§cНеверное количество.");
                return true;
            }
            Material mat = Material.matchMaterial(args[2].toUpperCase() + "_BLOCK");
            if (mat == null || !isProtectBlock(mat)) {
                sender.sendMessage(PREFIX + "§cБлоки: iron, gold, diamond, emerald");
                return true;
            }
            ItemStack item = new ItemStack(mat, amount);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§b§l⚡ КРИСТАЛЛ ПРИВАТА ⚡");
                meta.setLore(Arrays.asList("§7Поставьте блок для защиты территории."));
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
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        Player player = event.getPlayer();
        Location loc = event.getTo();
        if (loc == null) return;
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager regions = container.get(BukkitAdapter.adapt(loc.getWorld()));
        if (regions == null) return;
        ApplicableRegionSet set = regions.getApplicableRegions(BlockVector3.at(loc.getX(), loc.getY(), loc.getZ()));
        String currentRgId = "none";
        ProtectedRegion activeRegion = null;
        for (ProtectedRegion rg : set.getRegions()) {
            if (rg.getId().startsWith("ps_")) {
                activeRegion = rg;
                currentRgId = rg.getId();
                break;
            }
        }
        if (!lastRegion.getOrDefault(player.getUniqueId(), "none").equals(currentRgId)) {
            lastRegion.put(player.getUniqueId(), currentRgId);
            if (activeRegion != null) {
                String owner = activeRegion.getOwners().getPlayers().iterator().hasNext() ? Bukkit.getOfflinePlayer(activeRegion.getOwners().getPlayers().iterator().next()).getName() : "Неизвестен";
                player.sendTitle("§c§lЧУЖАЯ ТЕРРИТОРИЯ", "§7Владелец §8» §f" + owner, 10, 35, 10);
            } else {
                player.sendTitle("§e§lСВОБОДНАЯ ТЕРРИТОРИЯ", "§7Здесь можно строить базу", 10, 35, 10);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastRegion.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        if (!isProtectBlock(type)) return;
        int radius = type == Material.IRON_BLOCK ? 5 : type == Material.GOLD_BLOCK ? 8 : type == Material.DIAMOND_BLOCK ? 12 : 16;
        String id = block.getX() + "_" + block.getY() + "_" + block.getZ();
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager regions = container.get(BukkitAdapter.adapt(block.getWorld()));
        if (regions == null) return;
        ProtectedRegion region = new ProtectedCuboidRegion("ps_" + id, BlockVector3.at(block.getX() - radius, 0, block.getZ() - radius), BlockVector3.at(block.getX() + radius, 256, block.getZ() + radius));
        region.getOwners().addPlayer(event.getPlayer().getUniqueId());
        region.setFlag(Flags.TNT, StateFlag.State.ALLOW);
        region.setFlag(Flags.CREEPER_EXPLOSION, StateFlag.State.ALLOW);
        region.setFlag(Flags.OTHER_EXPLOSION, StateFlag.State.ALLOW);
        regions.addRegion(region);
        DHAPI.createHologram("holo_" + id, block.getLocation().add(0.5, 1.4, 0.5), Arrays.asList("§b§l⚡ КРИСТАЛЛ ПРИВАТА ⚡", "§7Владелец §8» §f" + event.getPlayer().getName(), "§7Радиус §8» §e" + radius));
        event.getPlayer().sendMessage(PREFIX + "Блок установлен! Радиус: §b" + radius);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (isProtectBlock(event.getBlock().getType())) removeProtection(event.getBlock());
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block b : event.blockList()) {
            if (isProtectBlock(b.getType())) removeProtection(b);
        }
    }

    private boolean isProtectBlock(Material m) {
        return m == Material.IRON_BLOCK || m == Material.GOLD_BLOCK || m == Material.DIAMOND_BLOCK || m == Material.EMERALD_BLOCK;
    }

    private void removeProtection(Block block) {
        String id = block.getX() + "_" + block.getY() + "_" + block.getZ();
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager regions = container.get(BukkitAdapter.adapt(block.getWorld()));
        if (regions != null && regions.hasRegion("ps_" + id)) regions.removeRegion("ps_" + id);
        Hologram holo = DHAPI.getHologram("holo_" + id);
        if (holo != null) holo.delete();
    }
}