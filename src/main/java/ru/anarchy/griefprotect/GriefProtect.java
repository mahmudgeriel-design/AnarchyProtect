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
import org.bukkit.entity.EntityType;
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
    private final Map<Location, List<UUID>> claimMembers = new HashMap<>();
    private final Map<UUID, BossBar> playerBars = new HashMap<>();
    private final String PREFIX = "§b§lFrostWorld §8» §7";
    private final int HEIGHT_LIMIT = 10; 

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("gprotect") != null) {
            getCommand("gprotect").setExecutor(this);
        }

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                Location loc = player.getLocation();
                Location activeCenter = null;

                boolean isSpawn = loc.getWorld().getName().equalsIgnoreCase("world") 
                        && loc.getX() >= -150 && loc.getX() <= 150 
                        && loc.getZ() >= -150 && loc.getZ() <= 150;

                for (Location blockLoc : claimBlocks.keySet()) {
                    int radius = getRadius(blockLoc.getBlock().getType());
                    if (Math.abs(blockLoc.getBlockX() - loc.getBlockX()) <= radius && 
                        Math.abs(blockLoc.getBlockZ() - loc.getBlockZ()) <= radius) {
                        if (loc.getBlockY() >= (blockLoc.getBlockY() - HEIGHT_LIMIT) && 
                            loc.getBlockY() <= (blockLoc.getBlockY() + HEIGHT_LIMIT)) {
                            activeCenter = blockLoc;
                            break;
                        }
                    }
                }

                BossBar bossBar = playerBars.computeIfAbsent(player.getUniqueId(), id -> {
                    BossBar bar = Bukkit.createBossBar("", BarColor.WHITE, BarStyle.SOLID);
                    bar.addPlayer(player);
                    return bar;
                });

                bossBar.setProgress(0.0);

                if (isSpawn) {
                    bossBar.setTitle("§f[ §e§lСпавн §f]");
                } else if (activeCenter != null) {
                    UUID ownerUUID = claimBlocks.get(activeCenter);
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
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Команда только для игроков.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§cИспользуйте: /gprotect [add/remove/give]");
            return true;
        }

        if (args[0].equalsIgnoreCase("add")) {
            if (!player.hasPermission("griefprotect.player")) {
                player.sendMessage(PREFIX + "§cУ вас нет прав.");
                return true;
            }
            if (args.length < 2) {
                player.sendMessage(PREFIX + "§cУкажите ник игрока.");
                return true;
            }
            Location blockLoc = findOwnedBlock(player.getUniqueId());
            if (blockLoc == null) {
                player.sendMessage(PREFIX + "§cВы должны стоять рядом со своим кристаллом привата.");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                player.sendMessage(PREFIX + "§cИгрок не найден.");
                return true;
            }
            claimMembers.computeIfAbsent(blockLoc, k -> new ArrayList<>()).add(target.getUniqueId());
            player.sendMessage(PREFIX + "§aИгрок §e" + target.getName() + " §aуспешно добавлен в ваш приват!");
            return true;
        }

        if (args[0].equalsIgnoreCase("remove")) {
            if (!player.hasPermission("griefprotect.player")) {
                player.sendMessage(PREFIX + "§cУ вас нет прав.");
                return true;
            }
            if (args.length < 2) {
                player.sendMessage(PREFIX + "§cУкажите ник игрока.");
                return true;
            }
            Location blockLoc = findOwnedBlock(player.getUniqueId());
            if (blockLoc == null) {
                player.sendMessage(PREFIX + "§cВы должны стоять рядом со своим кристаллом привата.");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            UUID targetUUID = target != null ? target.getUniqueId() : Bukkit.getOfflinePlayer(args[1]).getUniqueId();
            
            if (claimMembers.containsKey(blockLoc) && claimMembers.get(blockLoc).remove(targetUUID)) {
                player.sendMessage(PREFIX + "§aИгрок успешно удален из вашего привата.");
            } else {
                player.sendMessage(PREFIX + "§cЭтот игрок не записан в вашем привате.");
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("give")) {
            if (!player.hasPermission("griefprotect.admin")) {
                player.sendMessage(PREFIX + "§cУ вас нет прав.");
                return true;
            }
            if (args.length < 4) {
                player.sendMessage("§cИспользуйте: /gprotect give [ник] [iron/gold/diamond/emerald] [кол-во]");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                player.sendMessage(PREFIX + "§cИгрок оффлайн.");
                return true;
            }
            int amount;
            try { 
                amount = Integer.parseInt(args[3]); 
            } catch (Exception e) { 
                player.sendMessage(PREFIX + "§cНеверное количество.");
                return true; 
            }
            
            Material mat = Material.matchMaterial(args[2].toUpperCase() + "_BLOCK");
            if (mat == null || !isProtectBlock(mat)) {
                player.sendMessage(PREFIX + "§cНеверный тип блока.");
                return true;
            }

            ItemStack item = new ItemStack(mat, amount);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§b§l⚡ КРИСТАЛЛ ПРИВАТА ⚡");
                item.setItemMeta(meta);
            }
            target.getInventory().addItem(item);
            player.sendMessage(PREFIX + "§aВыдано!");
            return true;
        }
        return true;
    }

    private Location findOwnedBlock(UUID ownerUUID) {
        for (Map.Entry<Location, UUID> entry : claimBlocks.entrySet()) {
            if (entry.getValue().equals(ownerUUID)) return entry.getKey();
        }
        return null;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        if (!isProtectBlock(block.getType())) {
            for (Location blockLoc : claimBlocks.keySet()) {
                int radius = getRadius(blockLoc.getBlock().getType());
                if (Math.abs(blockLoc.getBlockX() - block.getX()) <= radius && 
                    Math.abs(blockLoc.getBlockZ() - block.getZ()) <= radius) {
                    if (block.getY() >= (blockLoc.getBlockY() - HEIGHT_LIMIT) && 
                        block.getY() <= (blockLoc.getBlockY() + HEIGHT_LIMIT)) {
                        
                        boolean isOwner = claimBlocks.get(blockLoc).equals(player.getUniqueId());
                        boolean isMember = claimMembers.containsKey(blockLoc) && claimMembers.get(blockLoc).contains(player.getUniqueId());
                        
                        if (!isOwner && !isMember) {
                            event.setCancelled(true);
                            player.sendMessage(PREFIX + "§cВы не можете строить на чужой территории!");
                            return;
                        }
                    }
                }
            }
            return;
        }
        claimBlocks.put(block.getLocation(), player.getUniqueId());
        player.sendMessage(PREFIX + "Блок установлен! Радиус: §b" + getRadius(block.getType()));
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        if (isProtectBlock(block.getType()) && claimBlocks.containsKey(block.getLocation())) {
            if (claimBlocks.get(block.getLocation()).equals(player.getUniqueId()) || player.hasPermission("gprotect.admin")) {
                claimBlocks.remove(block.getLocation());
                claimMembers.remove(block.getLocation());
                player.sendMessage(PREFIX + "Приват снят.");
            } else {
                event.setCancelled(true);
                player.sendMessage(PREFIX + "§cВы не можете сломать чужой кристалл привата!");
            }
            return;
        }
        for (Location blockLoc : claimBlocks.keySet()) {
            int radius = getRadius(blockLoc.getBlock().getType());
            if (Math.abs(blockLoc.getBlockX() - block.getX()) <= radius && 
                Math.abs(blockLoc.getBlockZ() - block.getZ()) <= radius) {
                if (block.getY() >= (blockLoc.getBlockY() - HEIGHT_LIMIT) && 
                    block.getY() <= (blockLoc.getBlockY() + HEIGHT_LIMIT)) {
                    
                    boolean isOwner = claimBlocks.get(blockLoc).equals(player.getUniqueId());
                    boolean isMember = claimMembers.containsKey(blockLoc) && claimMembers.get(blockLoc).contains(player.getUniqueId());
                    
                    if (!isOwner && !isMember) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.getEntityType() != EntityType.PRIMED_TNT) {
            for (Location blockLoc : claimBlocks.keySet()) {
                int radius = getRadius(blockLoc.getBlock().getType());
                event.blockList().removeIf(b -> 
                    Math.abs(blockLoc.getBlockX() - b.getX()) <= radius && 
                    Math.abs(blockLoc.getBlockZ() - b.getZ()) <= radius &&
                    b.getY() >= (blockLoc.getBlockY() - HEIGHT_LIMIT) && 
                    b.getY() <= (blockLoc.getBlockY() + HEIGHT_LIMIT)
                );
            }
            return;
        }

        for (Block b : event.blockList()) {
            if (isProtectBlock(b.getType()) && claimBlocks.containsKey(b.getLocation())) {
                claimBlocks.remove(b.getLocation());
                claimMembers.remove(b.getLocation());
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
