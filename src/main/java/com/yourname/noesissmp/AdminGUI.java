package com.yourname.noesissmp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class AdminGUI implements Listener {

    private final NoesisSMP plugin;
    public static final String GUI_NAME = ChatColor.DARK_GRAY + "⚙ " + ChatColor.DARK_AQUA + ChatColor.BOLD + "Noesis Admin Panel";

    public AdminGUI(NoesisSMP plugin) {
        this.plugin = plugin;
    }

    public void openGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 36, GUI_NAME);

        ItemStack glass = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 36; i++) gui.setItem(i, glass);

        gui.setItem(11, createItem(Material.GOLD_NUGGET, "&6&l[+] Triumph Star", "&7Spawn 1x Triumph Star", "", "&e▶ Click to receive"));
        gui.setItem(12, createItem(Material.REDSTONE, "&4&l[+] Soul Star", "&7Spawn 1x Soul Star", "", "&e▶ Click to receive"));
        gui.setItem(13, createItem(Material.ECHO_SHARD, "&5&l[+] Zacrozz's Fragment", "&7Spawn 1x Boss Drop", "&7(Used for Mace)", "", "&e▶ Click to receive"));
        gui.setItem(14, createItem(Material.BLAZE_POWDER, "&6&l[✦] Max Crit Stack", "&7Sets your Kill Stack to &e100&7.", "&7- 50% Max Crit Chance", "&7- Unlocks &8&lVOID / Black Crit &7Tier", "", "&e▶ Click to Max Crit"));

        gui.setItem(15, createItem(Material.IRON_SWORD, "&c&lStart PVP Event", "&7Starts the 4-Mode Tactical PvP Event.", "", "&e▶ Click to start recruitment"));
        gui.setItem(16, createItem(Material.WITHER_SKELETON_SKULL, "&9&lStart PVE Raid", "&7Starts the Boss Raid Event.", "&7(Zacrozz Boss Fight)", "", "&e▶ Click to start recruitment"));

        boolean treeEnabled = plugin.getConfig().getBoolean("settings.zonetree_enabled", true);
        gui.setItem(19, createItem(
                treeEnabled ? Material.OAK_SAPLING : Material.DEAD_BUSH,
                treeEnabled ? "&a&l[✦] Zone Tree: &2ENABLED" : "&c&l[✦] Zone Tree: &4DISABLED",
                "&7Status: " + (treeEnabled ? "&aActive" : "&cDisabled"),
                "",
                "&7When disabled, all player upgrades",
                "&7are reset and refunded into",
                "&7their Cloud Storage.",
                "",
                "&e▶ Click to Toggle"
        ));

        gui.setItem(20, createItem(Material.DRAGON_BREATH, "&d&l[✦] Enter The Zone", "&7Instantly trigger &d&lTHE ZONE&7 mode.", "", "&e▶ Click to activate"));
        gui.setItem(21, createItem(Material.GLASS_BOTTLE, "&7&l[✦] End The Zone", "&7Instantly end &d&lTHE ZONE&7 mode.", "", "&e▶ Click to deactivate"));
        gui.setItem(22, createItem(Material.COMMAND_BLOCK, "&d&lReload Config", "&7Reloads the config.yml file.", "", "&e▶ Click to reload"));
        gui.setItem(23, createItem(Material.HONEYCOMB, "&e&l[✦] Skip: Yellow Stacks", "&7Sets Yellow Stacks to &e7&7.", "", "&e▶ Click to set"));
        gui.setItem(24, createItem(Material.GLOWSTONE_DUST, "&6&l[✦] Skip: Orange Stacks", "&7Sets Orange Stacks to &67&7.", "", "&e▶ Click to set"));
        gui.setItem(25, createItem(Material.REDSTONE_BLOCK, "&c&l[✦] Skip: Red Stacks", "&7Sets Red Stacks to &c6&7.", "&7(Next hit triggers &8&lBlack Flash&7!)", "", "&e▶ Click to set"));
        gui.setItem(31, createItem(Material.TNT, "&c&l☠ EMERGENCY WIPE ☠", "&4&lWARNING:", "&cResets ALL player stats & hearts!", "", "&4▶ Click to WIPE SERVER"));

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 1f, 1.5f);
    }

    private ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat); ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        List<String> loreList = Arrays.asList(lore).stream().map(l -> ChatColor.translateAlternateColorCodes('&', l)).toList();
        meta.setLore(loreList); item.setItemMeta(meta); return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (title == null || !title.contains("Noesis Admin Panel")) return;

        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.BLACK_STAINED_GLASS_PANE) return;

        int slot = event.getSlot();

        switch (slot) {
            case 11:
                HashMap<Integer, ItemStack> left1 = player.getInventory().addItem(plugin.createStar("triumph"));
                for (ItemStack i : left1.values()) player.getWorld().dropItemNaturally(player.getLocation(), i);
                player.sendMessage(plugin.PREFIX + ChatColor.GREEN + "Spawned 1 Triumph Star.");
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f); break;
            case 12:
                HashMap<Integer, ItemStack> left2 = player.getInventory().addItem(plugin.createStar("soul"));
                for (ItemStack i : left2.values()) player.getWorld().dropItemNaturally(player.getLocation(), i);
                player.sendMessage(plugin.PREFIX + ChatColor.GREEN + "Spawned 1 Soul Star.");
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f); break;
            case 13:
                HashMap<Integer, ItemStack> left3 = player.getInventory().addItem(plugin.createBossDrop());
                for (ItemStack i : left3.values()) player.getWorld().dropItemNaturally(player.getLocation(), i);
                player.sendMessage(plugin.PREFIX + ChatColor.GREEN + "Spawned 1 Zacrozz's Fragment.");
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f); break;
            case 14:
                plugin.getConfig().set("players." + player.getUniqueId() + ".kills", 100);
                plugin.saveConfig();
                player.sendMessage(plugin.PREFIX + ChatColor.GOLD + "✨ Your Kill Stack is now MAXED to 100! (60% Crit Chance & VOID Tier Unlocked)");
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                break;
            case 15:
                player.closeInventory();
                if (plugin.eventManager != null) {
                    if (plugin.eventManager.isEventActive()) player.sendMessage(plugin.PREFIX + ChatColor.RED + "An event is already active!");
                    else { plugin.eventManager.startRecruiting(EventManager.EventCategory.PVP); player.sendMessage(plugin.PREFIX + ChatColor.GREEN + "Started PvP Event Recruitment!"); }
                } break;
            case 16:
                player.closeInventory();
                if (plugin.eventManager != null) {
                    if (plugin.eventManager.isEventActive()) player.sendMessage(plugin.PREFIX + ChatColor.RED + "An event is already active!");
                    else { plugin.eventManager.startRecruiting(EventManager.EventCategory.PVE); player.sendMessage(plugin.PREFIX + ChatColor.GREEN + "Started PvE Boss Raid Recruitment!"); }
                } break;
            case 19:
                boolean currentTree = plugin.getConfig().getBoolean("settings.zonetree_enabled", true);
                if (currentTree) {
                    if (plugin.zoneGUI != null) plugin.zoneGUI.disableZoneTree();
                    player.sendMessage(plugin.PREFIX + ChatColor.RED + "Zone Skill Tree is now DISABLED and all spent stars refunded to Cloud Storage!");
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                } else {
                    if (plugin.zoneGUI != null) plugin.zoneGUI.enableZoneTree();
                    player.sendMessage(plugin.PREFIX + ChatColor.GREEN + "Zone Skill Tree is now ENABLED!");
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                }
                openGUI(player);
                break;
            case 20:
                player.closeInventory();
                if (plugin.combatListener != null) {
                    plugin.combatListener.enterTheZone(player);
                } break;
            case 21:
                player.closeInventory();
                if (plugin.combatListener != null) {
                    plugin.combatListener.endTheZone(player);
                } break;
            case 22: plugin.reloadConfig(); player.sendMessage(plugin.PREFIX + ChatColor.LIGHT_PURPLE + "Config reloaded!"); player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f); break;
            case 23:
                if (plugin.combatListener != null) {
                    plugin.combatListener.skipToTier(player, "yellow");
                    player.sendMessage(plugin.PREFIX + ChatColor.YELLOW + "⚡ Yellow Stacks set to 7!");
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
                } break;
            case 24:
                if (plugin.combatListener != null) {
                    plugin.combatListener.skipToTier(player, "orange");
                    player.sendMessage(plugin.PREFIX + ChatColor.GOLD + "⚡ Orange Stacks set to 7!");
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
                } break;
            case 25:
                if (plugin.combatListener != null) {
                    plugin.combatListener.skipToTier(player, "red");
                    player.sendMessage(plugin.PREFIX + ChatColor.RED + "⚡ Red Stacks set to 6! Next crit will trigger BLACK FLASH!");
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.5f);
                } break;
            case 31:
                player.closeInventory(); plugin.getConfig().set("players", null); plugin.saveConfig();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0); p.setHealth(20.0);
                    p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 0.5f); p.sendTitle(ChatColor.DARK_RED + "☠ SERVER WIPE ☠", ChatColor.RED + "All stats have been reset.", 10, 70, 20);
                } Bukkit.broadcastMessage(plugin.PREFIX + ChatColor.DARK_RED + ChatColor.BOLD + "EMERGENCY SERVER WIPE COMPLETED BY ADMIN!"); break;
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (title != null && title.contains("Noesis Admin Panel")) {
            event.setCancelled(true);
        }
    }
}