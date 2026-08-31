package com.yourname.noesissmp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AdminStarGUI implements Listener {

    private final NoesisSMP plugin;
    public static final String TITLE_PREFIX = ChatColor.DARK_RED + "★ Star Manager: ";
    public static final String SELECTOR_TITLE = ChatColor.DARK_GRAY + "⚙ " + ChatColor.GOLD + "Select Player to Manage";

    private final Map<UUID, UUID> adminTarget = new HashMap<>();

    public AdminStarGUI(NoesisSMP plugin) {
        this.plugin = plugin;
    }

    /**
     * Open player selector GUI for admin to choose who to manage.
     */
    public void openPlayerSelector(Player admin) {
        Inventory inv = Bukkit.createInventory(null, 54, SELECTOR_TITLE);

        ItemStack glass = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, glass);

        int slot = 10;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (slot >= 44) break;
            if (slot % 9 == 8) slot += 2;

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) head.getItemMeta();
            sm.setOwningPlayer(online);
            sm.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + online.getName());

            int st = plugin.getConfig().getInt("players." + online.getUniqueId() + ".stored_triumph", 0);
            int ss = plugin.getConfig().getInt("players." + online.getUniqueId() + ".stored_soul", 0);
            int invT = plugin.countStarsInInventory(online.getInventory(), "triumph");
            int invS = plugin.countStarsInInventory(online.getInventory(), "soul");

            sm.setLore(Arrays.asList(
                    ChatColor.GRAY + "Cloud: " + ChatColor.GOLD + st + " Triumph" + ChatColor.GRAY + " | " + ChatColor.DARK_RED + ss + " Soul",
                    ChatColor.GRAY + "Inventory: " + ChatColor.GOLD + invT + " Triumph" + ChatColor.GRAY + " | " + ChatColor.DARK_RED + invS + " Soul",
                    "",
                    ChatColor.YELLOW + "▶ Click to Manage Stars"
            ));
            head.setItemMeta(sm);
            inv.setItem(slot++, head);
        }

        inv.setItem(49, createItem(Material.ARROW, "&c<- Back to Admin Panel", "&7Return to Admin GUI"));

        admin.openInventory(inv);
        admin.playSound(admin.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);
    }

    /**
     * Opens the star manager for a specific player (online or offline).
     */
    public void openManager(Player admin, OfflinePlayer target) {
        if (target == null) return;
        UUID targetId = target.getUniqueId();
        adminTarget.put(admin.getUniqueId(), targetId);

        String title = TITLE_PREFIX + ChatColor.DARK_AQUA + (target.getName() != null ? target.getName() : targetId.toString().substring(0, 8));
        if (title.length() > 32) title = title.substring(0, 32);

        Inventory inv = Bukkit.createInventory(null, 45, title);

        ItemStack glass = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 45; i++) inv.setItem(i, glass);

        Player onlineTarget = target.isOnline() ? target.getPlayer() : null;

        int st = plugin.getConfig().getInt("players." + targetId + ".stored_triumph", 0);
        int ss = plugin.getConfig().getInt("players." + targetId + ".stored_soul", 0);
        int invT = (onlineTarget != null) ? plugin.countStarsInInventory(onlineTarget.getInventory(), "triumph") : 0;
        int invS = (onlineTarget != null) ? plugin.countStarsInInventory(onlineTarget.getInventory(), "soul") : 0;
        int ecT = (onlineTarget != null) ? plugin.countStarsInInventory(onlineTarget.getEnderChest(), "triumph") : 0;
        int ecS = (onlineTarget != null) ? plugin.countStarsInInventory(onlineTarget.getEnderChest(), "soul") : 0;

        // Player Head Overview (Slot 4)
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        sm.setOwningPlayer(target);
        sm.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + (target.getName() != null ? target.getName() : "Player"));
        sm.setLore(Arrays.asList(
                ChatColor.GRAY + "Status: " + (target.isOnline() ? ChatColor.GREEN + "Online" : ChatColor.RED + "Offline"),
                "",
                ChatColor.YELLOW + "✦ Cloud Storage:",
                ChatColor.GRAY + "  - Triumph Stars: " + ChatColor.GOLD + st,
                ChatColor.GRAY + "  - Soul Stars: " + ChatColor.DARK_RED + ss,
                "",
                ChatColor.YELLOW + "✦ Live Inventory / EC:",
                ChatColor.GRAY + "  - Inventory: " + ChatColor.GOLD + invT + " T" + ChatColor.GRAY + " | " + ChatColor.DARK_RED + invS + " S",
                ChatColor.GRAY + "  - Ender Chest: " + ChatColor.GOLD + ecT + " T" + ChatColor.GRAY + " | " + ChatColor.DARK_RED + ecS + " S"
        ));
        head.setItemMeta(sm);
        inv.setItem(4, head);

        // 🟡 TRIUMPH STAR CONTROLS (Slots 10, 11, 12, 19, 20, 21)
        inv.setItem(10, createItem(Material.GOLD_NUGGET, "&6&l[+] Add 1 Triumph", "&7Add 1x Triumph Star to Cloud", "", "&e▶ Click to Add"));
        inv.setItem(11, createItem(Material.RAW_GOLD, "&6&l[+] Add 5 Triumph", "&7Add 5x Triumph Stars to Cloud", "", "&e▶ Click to Add"));
        inv.setItem(12, createItem(Material.GOLD_BLOCK, "&6&l[+] Add 10 Triumph", "&7Add 10x Triumph Stars to Cloud", "", "&e▶ Click to Add"));

        inv.setItem(19, createItem(Material.ORANGE_DYE, "&c&l[-] Pull 1 Triumph", "&7Remove 1x Triumph Star from Cloud", "", "&c▶ Click to Pull"));
        inv.setItem(20, createItem(Material.REDSTONE, "&c&l[-] Pull 5 Triumph", "&7Remove 5x Triumph Stars from Cloud", "", "&c▶ Click to Pull"));
        inv.setItem(21, createItem(Material.REDSTONE_BLOCK, "&c&l[-] Pull 10 Triumph", "&7Remove 10x Triumph Stars from Cloud", "", "&c▶ Click to Pull"));

        // 🔴 SOUL STAR CONTROLS (Slots 14, 15, 16, 23, 24, 25)
        inv.setItem(14, createItem(Material.NETHER_WART, "&4&l[+] Add 1 Soul", "&7Add 1x Soul Star to Cloud", "", "&e▶ Click to Add"));
        inv.setItem(15, createItem(Material.CRIMSON_FUNGUS, "&4&l[+] Add 5 Soul", "&7Add 5x Soul Stars to Cloud", "", "&e▶ Click to Add"));
        inv.setItem(16, createItem(Material.MAGMA_BLOCK, "&4&l[+] Add 10 Soul", "&7Add 10x Soul Stars to Cloud", "", "&e▶ Click to Add"));

        inv.setItem(23, createItem(Material.PURPLE_DYE, "&c&l[-] Pull 1 Soul", "&7Remove 1x Soul Star from Cloud", "", "&c▶ Click to Pull"));
        inv.setItem(24, createItem(Material.POPPY, "&c&l[-] Pull 5 Soul", "&7Remove 5x Soul Stars from Cloud", "", "&c▶ Click to Pull"));
        inv.setItem(25, createItem(Material.WITHER_ROSE, "&c&l[-] Pull 10 Soul", "&7Remove 10x Soul Stars from Cloud", "", "&c▶ Click to Pull"));

        // 🟢 DIRECT INVENTORY ACTIONS (Row 4)
        if (onlineTarget != null) {
            inv.setItem(28, createItem(Material.CHEST, "&a&l[+] Put 1 Triumph in Inv", "&7Gives 1 Triumph Star directly to inventory", "", "&e▶ Click to Give"));
            inv.setItem(29, createItem(Material.HOPPER, "&e&l[-] Take 1 Triumph from Inv", "&7Pulls 1 Triumph Star from inventory to admin", "", "&e▶ Click to Pull"));
            inv.setItem(33, createItem(Material.ENDER_CHEST, "&a&l[+] Put 1 Soul in Inv", "&7Gives 1 Soul Star directly to inventory", "", "&e▶ Click to Give"));
            inv.setItem(34, createItem(Material.DISPENSER, "&e&l[-] Take 1 Soul from Inv", "&7Pulls 1 Soul Star from inventory to admin", "", "&e▶ Click to Pull"));
        } else {
            inv.setItem(28, createItem(Material.BARRIER, "&8[Target Offline]", "&7Direct inventory actions disabled"));
            inv.setItem(29, createItem(Material.BARRIER, "&8[Target Offline]", "&7Direct inventory actions disabled"));
            inv.setItem(33, createItem(Material.BARRIER, "&8[Target Offline]", "&7Direct inventory actions disabled"));
            inv.setItem(34, createItem(Material.BARRIER, "&8[Target Offline]", "&7Direct inventory actions disabled"));
        }

        inv.setItem(36, createItem(Material.ARROW, "&c<- Back", "&7Return to Player Selector"));
        inv.setItem(40, createItem(Material.NETHER_STAR, "&e&l[✦] Take 1 Star from Cloud to Hand", "&7Withdraws 1 star from their Cloud into Admin hand", "", "&e▶ Click to Withdraw"));
        inv.setItem(44, createItem(Material.TNT, "&4&l[☠] Clear All Cloud Stars", "&cSets their Cloud storage to 0 stars", "", "&4▶ Click to Clear"));

        admin.openInventory(inv);
        admin.playSound(admin.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1.2f);
    }

    private ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        List<String> loreList = Arrays.asList(lore).stream().map(l -> ChatColor.translateAlternateColorCodes('&', l)).toList();
        meta.setLore(loreList);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (title == null) return;

        if (title.equals("⚙ Select Player to Manage")) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player admin)) return;
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.BLACK_STAINED_GLASS_PANE) return;

            if (clicked.getType() == Material.ARROW) {
                plugin.getAdminGUI().openGUI(admin);
                return;
            }

            if (clicked.getType() == Material.PLAYER_HEAD && clicked.getItemMeta() instanceof SkullMeta sm) {
                OfflinePlayer target = sm.getOwningPlayer();
                if (target != null) {
                    openManager(admin, target);
                }
            }
            return;
        }

        if (!title.startsWith("★ Star Manager:")) return;

        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (!(event.getWhoClicked() instanceof Player admin)) return;

        UUID targetId = adminTarget.get(admin.getUniqueId());
        if (targetId == null) return;
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetId);
        Player onlineTarget = target.isOnline() ? target.getPlayer() : null;

        int slot = event.getSlot();
        String targetName = target.getName() != null ? target.getName() : "Player";

        int currT = plugin.getConfig().getInt("players." + targetId + ".stored_triumph", 0);
        int currS = plugin.getConfig().getInt("players." + targetId + ".stored_soul", 0);

        switch (slot) {
            // Add Triumph to Cloud
            case 10: modifyCloud(targetId, "triumph", 1); admin.sendMessage(plugin.PREFIX + ChatColor.GREEN + "Added +1 Triumph Star to " + targetName + "'s Cloud."); break;
            case 11: modifyCloud(targetId, "triumph", 5); admin.sendMessage(plugin.PREFIX + ChatColor.GREEN + "Added +5 Triumph Stars to " + targetName + "'s Cloud."); break;
            case 12: modifyCloud(targetId, "triumph", 10); admin.sendMessage(plugin.PREFIX + ChatColor.GREEN + "Added +10 Triumph Stars to " + targetName + "'s Cloud."); break;

            // Pull Triumph from Cloud
            case 19: modifyCloud(targetId, "triumph", -1); admin.sendMessage(plugin.PREFIX + ChatColor.YELLOW + "Removed 1 Triumph Star from " + targetName + "'s Cloud."); break;
            case 20: modifyCloud(targetId, "triumph", -5); admin.sendMessage(plugin.PREFIX + ChatColor.YELLOW + "Removed 5 Triumph Stars from " + targetName + "'s Cloud."); break;
            case 21: modifyCloud(targetId, "triumph", -10); admin.sendMessage(plugin.PREFIX + ChatColor.YELLOW + "Removed 10 Triumph Stars from " + targetName + "'s Cloud."); break;

            // Add Soul to Cloud
            case 14: modifyCloud(targetId, "soul", 1); admin.sendMessage(plugin.PREFIX + ChatColor.GREEN + "Added +1 Soul Star to " + targetName + "'s Cloud."); break;
            case 15: modifyCloud(targetId, "soul", 5); admin.sendMessage(plugin.PREFIX + ChatColor.GREEN + "Added +5 Soul Stars to " + targetName + "'s Cloud."); break;
            case 16: modifyCloud(targetId, "soul", 10); admin.sendMessage(plugin.PREFIX + ChatColor.GREEN + "Added +10 Soul Stars to " + targetName + "'s Cloud."); break;

            // Pull Soul from Cloud
            case 23: modifyCloud(targetId, "soul", -1); admin.sendMessage(plugin.PREFIX + ChatColor.YELLOW + "Removed 1 Soul Star from " + targetName + "'s Cloud."); break;
            case 24: modifyCloud(targetId, "soul", -5); admin.sendMessage(plugin.PREFIX + ChatColor.YELLOW + "Removed 5 Soul Stars from " + targetName + "'s Cloud."); break;
            case 25: modifyCloud(targetId, "soul", -10); admin.sendMessage(plugin.PREFIX + ChatColor.YELLOW + "Removed 10 Soul Stars from " + targetName + "'s Cloud."); break;

            // Direct Inventory actions
            case 28:
                if (onlineTarget != null) {
                    onlineTarget.getInventory().addItem(plugin.createStar("triumph"));
                    onlineTarget.sendMessage(plugin.PREFIX + ChatColor.GOLD + "Admin put 1 Triumph Star in your inventory.");
                    admin.sendMessage(plugin.PREFIX + ChatColor.GREEN + "Put 1 Triumph Star in " + targetName + "'s inventory.");
                } break;
            case 29:
                if (onlineTarget != null) {
                    int removed = plugin.removeStarsFromInventory(onlineTarget.getInventory(), "triumph", 1);
                    if (removed > 0) {
                        admin.getInventory().addItem(plugin.createStar("triumph"));
                        onlineTarget.sendMessage(plugin.PREFIX + ChatColor.RED + "Admin pulled 1 Triumph Star from your inventory.");
                        admin.sendMessage(plugin.PREFIX + ChatColor.GREEN + "Pulled 1 Triumph Star from " + targetName + " into your inventory.");
                    } else {
                        admin.sendMessage(plugin.PREFIX + ChatColor.RED + targetName + " has no Triumph Stars in inventory!");
                    }
                } break;
            case 33:
                if (onlineTarget != null) {
                    onlineTarget.getInventory().addItem(plugin.createStar("soul"));
                    onlineTarget.sendMessage(plugin.PREFIX + ChatColor.DARK_RED + "Admin put 1 Soul Star in your inventory.");
                    admin.sendMessage(plugin.PREFIX + ChatColor.GREEN + "Put 1 Soul Star in " + targetName + "'s inventory.");
                } break;
            case 34:
                if (onlineTarget != null) {
                    int removed = plugin.removeStarsFromInventory(onlineTarget.getInventory(), "soul", 1);
                    if (removed > 0) {
                        admin.getInventory().addItem(plugin.createStar("soul"));
                        onlineTarget.sendMessage(plugin.PREFIX + ChatColor.RED + "Admin pulled 1 Soul Star from your inventory.");
                        admin.sendMessage(plugin.PREFIX + ChatColor.GREEN + "Pulled 1 Soul Star from " + targetName + " into your inventory.");
                    } else {
                        admin.sendMessage(plugin.PREFIX + ChatColor.RED + targetName + " has no Soul Stars in inventory!");
                    }
                } break;

            // Withdraw 1 from their Cloud into Admin hand
            case 40:
                if (currT > 0 || currS > 0) {
                    String type = currT > 0 ? "triumph" : "soul";
                    modifyCloud(targetId, type, -1);
                    admin.getInventory().addItem(plugin.createStar(type));
                    admin.sendMessage(plugin.PREFIX + ChatColor.GREEN + "Pulled 1 " + type + " star from " + targetName + "'s Cloud to your hand.");
                } else {
                    admin.sendMessage(plugin.PREFIX + ChatColor.RED + targetName + " has no stars in Cloud Storage!");
                }
                break;

            // Clear all Cloud Stars
            case 44:
                plugin.getConfig().set("players." + targetId + ".stored_triumph", 0);
                plugin.getConfig().set("players." + targetId + ".stored_soul", 0);
                plugin.saveConfig();
                admin.sendMessage(plugin.PREFIX + ChatColor.RED + "Cleared all Cloud Stars for " + targetName + ".");
                break;

            case 36:
                openPlayerSelector(admin);
                return;
        }

        admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
        // Refresh GUI to show live updated values
        openManager(admin, target);
    }

    private void modifyCloud(UUID targetId, String type, int delta) {
        String path = "players." + targetId + ".stored_" + type.toLowerCase();
        int current = plugin.getConfig().getInt(path, 0);
        int updated = Math.max(0, current + delta);
        plugin.getConfig().set(path, updated);
        plugin.saveConfig();
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (title != null && (title.startsWith("★ Star Manager:") || title.equals("⚙ Select Player to Manage"))) {
            event.setCancelled(true);
        }
    }
}
