package com.yourname.noesissmp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AdminStarGUI implements Listener {

    private final NoesisSMP plugin;
    public static final String TITLE_PREFIX = ChatColor.DARK_RED + "★ Star Manager: ";
    public static final String SELECTOR_TITLE_PREFIX = ChatColor.DARK_GRAY + "⚙ " + ChatColor.GOLD + "Select Player";

    private final Map<UUID, UUID> adminTarget = new HashMap<>();
    private final Map<UUID, Integer> adminSelectorPage = new HashMap<>();
    private final Map<UUID, PlayerSortMode> adminSortMode = new HashMap<>();
    private final NamespacedKey targetUuidKey;

    public enum PlayerSortMode {
        DEFAULT("Default (Online First)"),
        OVERFLOW_DESC("Highest Overflow (⭐ ▼)"),
        OVERFLOW_ASC("Lowest Overflow (⭐ ▲)"),
        KILLS_DESC("Highest Kills (⚔ ▼)"),
        KILLS_ASC("Lowest Kills (⚔ ▲)"),
        TOTAL_POINTS_DESC("Highest Total Points (🔮 ▼)"),
        TRIUMPH_DESC("Highest Triumph Stars (★ ▼)"),
        SOUL_DESC("Highest Soul Stars (✦ ▼)"),
        NAME_ASC("Name (A-Z)");

        private final String displayName;

        PlayerSortMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public PlayerSortMode next() {
            PlayerSortMode[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }

        public PlayerSortMode prev() {
            PlayerSortMode[] values = values();
            return values[(this.ordinal() - 1 + values.length) % values.length];
        }
    }

    private static final int[] HEAD_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    public AdminStarGUI(NoesisSMP plugin) {
        this.plugin = plugin;
        this.targetUuidKey = new NamespacedKey(plugin, "target_player_uuid");
    }

    /**
     * Open player selector GUI for admin to choose who to manage (supports Online & Offline players + Sorting + Pagination).
     */
    public void openPlayerSelector(Player admin) {
        openPlayerSelector(admin, 0);
    }

    public void openPlayerSelector(Player admin, int page) {
        // Collect all players: Online first, then offline
        List<OfflinePlayer> allPlayers = new ArrayList<>();
        Set<UUID> added = new HashSet<>();

        for (Player online : Bukkit.getOnlinePlayers()) {
            allPlayers.add(online);
            added.add(online.getUniqueId());
        }

        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (offline != null && offline.getUniqueId() != null && !added.contains(offline.getUniqueId())) {
                allPlayers.add(offline);
                added.add(offline.getUniqueId());
            }
        }

        // Also check data.yml's "players" section to catch registered offline players.
        if (plugin.getData().isConfigurationSection("players")) {
            for (String key : plugin.getData().getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID u = UUID.fromString(key);
                    if (!added.contains(u)) {
                        OfflinePlayer op = Bukkit.getOfflinePlayer(u);
                        allPlayers.add(op);
                        added.add(u);
                    }
                } catch (Exception ignored) {}
            }
        }

        // Apply Sorting
        PlayerSortMode sortMode = adminSortMode.getOrDefault(admin.getUniqueId(), PlayerSortMode.DEFAULT);
        allPlayers.sort((a, b) -> {
            UUID uA = a.getUniqueId();
            UUID uB = b.getUniqueId();
            String nameA = plugin.getPlayerName(a);
            String nameB = plugin.getPlayerName(b);

            int ofA = plugin.getData().getInt("players." + uA + ".overflow", 0);
            int ofB = plugin.getData().getInt("players." + uB + ".overflow", 0);
            int kA = plugin.getData().getInt("players." + uA + ".kills", 0);
            int kB = plugin.getData().getInt("players." + uB + ".kills", 0);
            int stA = plugin.getData().getInt("players." + uA + ".stored_triumph", 0);
            int stB = plugin.getData().getInt("players." + uB + ".stored_triumph", 0);
            int ssA = plugin.getData().getInt("players." + uA + ".stored_soul", 0);
            int ssB = plugin.getData().getInt("players." + uB + ".stored_soul", 0);

            switch (sortMode) {
                case OVERFLOW_DESC:
                    int cOfD = Integer.compare(ofB, ofA);
                    return cOfD != 0 ? cOfD : nameA.compareToIgnoreCase(nameB);
                case OVERFLOW_ASC:
                    int cOfA = Integer.compare(ofA, ofB);
                    return cOfA != 0 ? cOfA : nameA.compareToIgnoreCase(nameB);
                case KILLS_DESC:
                    int cKD = Integer.compare(kB, kA);
                    return cKD != 0 ? cKD : nameA.compareToIgnoreCase(nameB);
                case KILLS_ASC:
                    int cKA = Integer.compare(kA, kB);
                    return cKA != 0 ? cKA : nameA.compareToIgnoreCase(nameB);
                case TOTAL_POINTS_DESC:
                    int cTot = Integer.compare(kB + ofB, kA + ofA);
                    return cTot != 0 ? cTot : nameA.compareToIgnoreCase(nameB);
                case TRIUMPH_DESC:
                    int cSt = Integer.compare(stB, stA);
                    return cSt != 0 ? cSt : nameA.compareToIgnoreCase(nameB);
                case SOUL_DESC:
                    int cSs = Integer.compare(ssB, ssA);
                    return cSs != 0 ? cSs : nameA.compareToIgnoreCase(nameB);
                case NAME_ASC:
                    return nameA.compareToIgnoreCase(nameB);
                case DEFAULT:
                default:
                    if (a.isOnline() && !b.isOnline()) return -1;
                    if (!a.isOnline() && b.isOnline()) return 1;
                    return nameA.compareToIgnoreCase(nameB);
            }
        });

        int maxPages = Math.max(1, (int) Math.ceil((double) allPlayers.size() / HEAD_SLOTS.length));
        page = Math.max(0, Math.min(page, maxPages - 1));
        adminSelectorPage.put(admin.getUniqueId(), page);

        String title = SELECTOR_TITLE_PREFIX + " " + ChatColor.GRAY + "(" + (page + 1) + "/" + maxPages + ")";
        Inventory inv = Bukkit.createInventory(null, 54, title);

        ItemStack glass = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, glass);

        int startIndex = page * HEAD_SLOTS.length;
        int endIndex = Math.min(startIndex + HEAD_SLOTS.length, allPlayers.size());

        for (int i = startIndex; i < endIndex; i++) {
            OfflinePlayer p = allPlayers.get(i);
            int slot = HEAD_SLOTS[i - startIndex];

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) head.getItemMeta();

            // Safe skin attachment without triggering Mojang rate-limit HTTP 429
            if (p.isOnline() && p.getPlayer() != null) {
                sm.setOwningPlayer(p.getPlayer());
            } else if (p.hasPlayedBefore()) {
                try {
                    sm.setOwningPlayer(p);
                } catch (Throwable ignored) {}
            }

            UUID u = p.getUniqueId();
            sm.getPersistentDataContainer().set(targetUuidKey, PersistentDataType.STRING, u.toString());

            String pName = plugin.getPlayerName(p);
            sm.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + pName);

            int st = plugin.getData().getInt("players." + u + ".stored_triumph", 0);
            int ss = plugin.getData().getInt("players." + u + ".stored_soul", 0);
            int kills = plugin.getData().getInt("players." + u + ".kills", 0);
            int overflow = plugin.getData().getInt("players." + u + ".overflow", 0);
            int totalPts = kills + overflow;
            double crit = Math.min(50.0, totalPts * 0.8);

            Player online = p.isOnline() ? p.getPlayer() : null;
            int hearts = (int) ((online != null && online.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null)
                    ? online.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue() / 2.0
                    : plugin.getData().getDouble("players." + u + ".max_health", 20.0) / 2.0);

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Status: " + (p.isOnline() ? ChatColor.GREEN + "Online" : ChatColor.RED + "Offline"));
            lore.add(ChatColor.RED + "❤ Hearts: " + ChatColor.WHITE + hearts + "/20");
            lore.add(ChatColor.DARK_RED + "⚔ Kill Stack: " + ChatColor.WHITE + kills + ChatColor.GRAY + " | " + ChatColor.GOLD + "⭐ Overflow: " + ChatColor.WHITE + overflow);
            lore.add(ChatColor.AQUA + "🎯 Base Crit: " + ChatColor.WHITE + String.format(java.util.Locale.US, "%.1f", crit) + "%");
            lore.add(ChatColor.YELLOW + "✦ Cloud Storage: " + ChatColor.GOLD + st + "x Triumph" + ChatColor.GRAY + " | " + ChatColor.DARK_RED + ss + "x Soul");
            lore.add("");
            lore.add(ChatColor.YELLOW + "▶ Click to Manage Stars & Profile");

            sm.setLore(lore);
            head.setItemMeta(sm);
            inv.setItem(slot, head);
        }

        // Navigation & Sorting Controls (Bottom Row)
        if (page > 0) {
            inv.setItem(45, createItem(Material.ARROW, "&e◀ Previous Page", "&7Page " + page + "/" + maxPages));
        } else {
            inv.setItem(45, createItem(Material.GRAY_DYE, "&8◀ Previous Page", "&7You are on the first page"));
        }

        // Sorting Button (Slot 47)
        inv.setItem(47, createItem(Material.HOPPER, "&e&lSort Mode: &b" + sortMode.getDisplayName(),
                "&7Current Order: &f" + sortMode.getDisplayName(),
                "",
                "&7Available Modes:",
                "&8- Default (Online First)",
                "&8- Highest / Lowest Overflow",
                "&8- Highest / Lowest Kill Stack",
                "&8- Highest Total Points",
                "&8- Highest Triumph / Soul Stars",
                "&8- Alphabetical (A-Z)",
                "",
                "&e▶ Left-Click: &7Next Sort Mode",
                "&b▶ Right-Click: &7Previous Sort Mode"
        ));

        inv.setItem(49, createItem(Material.BARRIER, "&c<- Back to Admin Panel", "&7Return to Admin GUI"));

        // Total Players Info (Slot 51)
        inv.setItem(51, createItem(Material.BOOK, "&6&lServer Player Stats",
                "&7Total Registered: &e" + allPlayers.size() + " players",
                "&7Online Now: &a" + Bukkit.getOnlinePlayers().size() + " players"
        ));

        if (page < maxPages - 1) {
            inv.setItem(53, createItem(Material.ARROW, "&eNext Page ▶", "&7Page " + (page + 2) + "/" + maxPages));
        } else {
            inv.setItem(53, createItem(Material.GRAY_DYE, "&8Next Page ▶", "&7You are on the last page"));
        }

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

        String targetName = plugin.getPlayerName(target);
        String title = TITLE_PREFIX + ChatColor.DARK_AQUA + targetName;
        if (title.length() > 32) title = title.substring(0, 32);

        Inventory inv = Bukkit.createInventory(null, 45, title);

        ItemStack glass = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 45; i++) inv.setItem(i, glass);

        Player onlineTarget = target.isOnline() ? target.getPlayer() : null;

        int st = plugin.getData().getInt("players." + targetId + ".stored_triumph", 0);
        int ss = plugin.getData().getInt("players." + targetId + ".stored_soul", 0);
        int kills = plugin.getData().getInt("players." + targetId + ".kills", 0);
        int overflow = plugin.getData().getInt("players." + targetId + ".overflow", 0);
        int totalPts = kills + overflow;
        double crit = Math.min(50.0, totalPts * 0.8);

        int hearts = (int) ((onlineTarget != null && onlineTarget.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null)
                ? onlineTarget.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue() / 2.0
                : plugin.getData().getDouble("players." + targetId + ".max_health", 20.0) / 2.0);

        int invT = (onlineTarget != null) ? plugin.countStarsInInventory(onlineTarget.getInventory(), "triumph") : 0;
        int invS = (onlineTarget != null) ? plugin.countStarsInInventory(onlineTarget.getInventory(), "soul") : 0;
        int ecT = (onlineTarget != null) ? plugin.countStarsInInventory(onlineTarget.getEnderChest(), "triumph") : 0;
        int ecS = (onlineTarget != null) ? plugin.countStarsInInventory(onlineTarget.getEnderChest(), "soul") : 0;

        // Player Head Overview with Kill Stack & Overflow (Slot 4)
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        if (target.isOnline() && target.getPlayer() != null) {
            sm.setOwningPlayer(target.getPlayer());
        } else if (target.hasPlayedBefore()) {
            try {
                sm.setOwningPlayer(target);
            } catch (Throwable ignored) {}
        }

        sm.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + targetName);
        sm.setLore(Arrays.asList(
                ChatColor.GRAY + "Status: " + (target.isOnline() ? ChatColor.GREEN + "Online" : ChatColor.RED + "Offline"),
                "",
                ChatColor.LIGHT_PURPLE + "✦ Combat & Profile Stats:",
                ChatColor.RED + "  - Max Hearts: " + ChatColor.WHITE + hearts + "/20",
                ChatColor.DARK_RED + "  - Kill Stack: " + ChatColor.WHITE + kills,
                ChatColor.GOLD + "  - Overflow Points: " + ChatColor.WHITE + overflow + " pts",
                ChatColor.AQUA + "  - Base Crit Chance: " + ChatColor.WHITE + String.format(java.util.Locale.US, "%.1f", crit) + "%",
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

        if (title.startsWith("⚙ Select Player")) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player admin)) return;
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.BLACK_STAINED_GLASS_PANE || clicked.getType() == Material.GRAY_DYE) return;

            int page = adminSelectorPage.getOrDefault(admin.getUniqueId(), 0);
            PlayerSortMode currentMode = adminSortMode.getOrDefault(admin.getUniqueId(), PlayerSortMode.DEFAULT);

            // Back to Admin GUI
            if (event.getSlot() == 49) {
                plugin.getAdminGUI().openGUI(admin);
                return;
            }

            // Previous Page
            if (event.getSlot() == 45 && clicked.getType() == Material.ARROW) {
                openPlayerSelector(admin, page - 1);
                return;
            }

            // Next Page
            if (event.getSlot() == 53 && clicked.getType() == Material.ARROW) {
                openPlayerSelector(admin, page + 1);
                return;
            }

            // Sort Mode Button (Slot 47)
            if (event.getSlot() == 47) {
                PlayerSortMode nextMode = event.isRightClick() ? currentMode.prev() : currentMode.next();
                adminSortMode.put(admin.getUniqueId(), nextMode);
                admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.4f);
                openPlayerSelector(admin, 0); // Jump back to page 1 with updated order
                return;
            }

            // Clicked Player Head via PersistentDataContainer
            if (clicked.hasItemMeta()) {
                String uuidStr = clicked.getItemMeta().getPersistentDataContainer().get(targetUuidKey, PersistentDataType.STRING);
                if (uuidStr != null) {
                    try {
                        OfflinePlayer target = Bukkit.getOfflinePlayer(UUID.fromString(uuidStr));
                        openManager(admin, target);
                        return;
                    } catch (Exception ignored) {}
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
        String targetName = plugin.getPlayerName(target);

        int currT = plugin.getData().getInt("players." + targetId + ".stored_triumph", 0);
        int currS = plugin.getData().getInt("players." + targetId + ".stored_soul", 0);

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
                plugin.getData().set("players." + targetId + ".stored_triumph", 0);
                plugin.getData().set("players." + targetId + ".stored_soul", 0);
                plugin.saveData();
                admin.sendMessage(plugin.PREFIX + ChatColor.RED + "Cleared all Cloud Stars for " + targetName + ".");
                break;

            case 36:
                int lastPage = adminSelectorPage.getOrDefault(admin.getUniqueId(), 0);
                openPlayerSelector(admin, lastPage);
                return;
        }

        admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
        // Refresh GUI to show live updated values
        openManager(admin, target);
    }

    private void modifyCloud(UUID targetId, String type, int delta) {
        String path = "players." + targetId + ".stored_" + type.toLowerCase();
        int current = plugin.getData().getInt(path, 0);
        int updated = Math.max(0, current + delta);
        plugin.getData().set(path, updated);
        plugin.saveData();
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (title != null && (title.startsWith("★ Star Manager:") || title.startsWith("⚙ Select Player"))) {
            event.setCancelled(true);
        }
    }
}
