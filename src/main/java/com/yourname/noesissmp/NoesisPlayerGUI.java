package com.yourname.noesissmp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class NoesisPlayerGUI implements Listener {

    private final NoesisSMP plugin;
    public static final String GUI_TITLE = ChatColor.DARK_PURPLE + "✨ Noesis Star Manager";

    public NoesisPlayerGUI(NoesisSMP plugin) {
        this.plugin = plugin;
    }

    public void openGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, GUI_TITLE);

        int overflow = plugin.getData().getInt("players." + player.getUniqueId() + ".overflow", 0);
        int storedTriumph = plugin.getData().getInt("players." + player.getUniqueId() + ".stored_triumph", 0);
        int storedSoul = plugin.getData().getInt("players." + player.getUniqueId() + ".stored_soul", 0);
        int kills = plugin.getData().getInt("players." + player.getUniqueId() + ".kills", 0);
        String mode = plugin.getData().getString("players." + player.getUniqueId() + ".reward_mode", "auto").toUpperCase();

        int totalPts = kills + overflow;
        double crit = Math.min(50.0, totalPts * 0.8);
        int hearts = (int) (player.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null
                ? player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue() / 2.0
                : 20.0 / 2.0);

        // Player Profile Overview (Slot 4)
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        sm.setOwningPlayer(player);
        sm.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + player.getName() + "'s Profile");
        sm.setLore(Arrays.asList(
                ChatColor.RED + "❤ Max Hearts: " + ChatColor.WHITE + hearts + "/20",
                ChatColor.DARK_RED + "⚔ Kill Stack: " + ChatColor.WHITE + kills,
                ChatColor.GOLD + "⭐ Overflow Points: " + ChatColor.WHITE + overflow + " pts",
                ChatColor.AQUA + "🎯 Base Crit Chance: " + ChatColor.WHITE + String.format(java.util.Locale.US, "%.1f", crit) + "%",
                "",
                ChatColor.YELLOW + "✦ Cloud Storage: " + ChatColor.GOLD + storedTriumph + "x Triumph" + ChatColor.GRAY + " | " + ChatColor.DARK_RED + storedSoul + "x Soul"
        ));
        head.setItemMeta(sm);
        gui.setItem(4, head);

        // 1. Convert Overflow
        gui.setItem(10, createItem(Material.GOLD_NUGGET, "&6&lConvert Overflow",
                "&7Current Overflow: &e" + overflow,
                "",
                "&eLeft-Click &7to convert 1 Overflow",
                "&7into &61x Triumph Star"
        ));

        // 2. Reward Mode
        gui.setItem(12, createItem(Material.COMPASS, "&b&lReward Mode",
                "&7Current Reward Mode: &a" + mode,
                "",
                "&eLeft-Click &7to cycle modes",
                "&8(AUTO -> INV -> EC -> SYS)"
        ));

        // 3. Deposit Stars
        gui.setItem(14, createItem(Material.ENDER_CHEST, "&d&lDeposit Stars",
                "&7Deposit all stars from your inventory",
                "&7into the Cloud Storage.",
                "",
                "&eLeft-Click &7to deposit all"
        ));

        // 4. Claim Stars
        gui.setItem(16, createItem(Material.NETHER_STAR, "&a&lClaim Stars",
                "&7Stars currently in Cloud:",
                "&6Triumph: &e" + storedTriumph,
                "&cSoul: &e" + storedSoul,
                "",
                "&eLeft-Click &7to claim 1 &6Triumph",
                "&eRight-Click &7to claim 1 &cSoul"
        ));

        // 5. Convert ALL Overflow (Below Slot 10)
        gui.setItem(19, createItem(Material.RAW_GOLD_BLOCK, "&6&l[✦] Convert ALL Overflow",
                "&7Converts all &e" + overflow + " Overflow",
                "&7into Triumph Stars.",
                "",
                "&e▶ Click to convert all"
        ));

        // 6. Claim ALL Stars (Below Slot 16)
        gui.setItem(25, createItem(Material.BEACON, "&a&l[✦] Claim ALL Cloud Stars",
                "&7Withdraw all stars from Cloud",
                "&7directly into your inventory.",
                "",
                "&e▶ Click to claim all"
        ));

        // Border Decoration
        ItemStack glass = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            if (gui.getItem(i) == null) gui.setItem(i, glass);
        }

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f);
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
        if (!ChatColor.stripColor(event.getView().getTitle()).equals("✨ Noesis Star Manager")) return;
        event.setCancelled(true);
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR || event.getCurrentItem().getType() == Material.BLACK_STAINED_GLASS_PANE) return;

        Player player = (Player) event.getWhoClicked();
        Material clicked = event.getCurrentItem().getType();
        int slot = event.getSlot();
        String uuid = player.getUniqueId().toString();

        // 1. Convert Overflow
        if (clicked == Material.GOLD_NUGGET) {
            int overflow = plugin.getData().getInt("players." + uuid + ".overflow", 0);
            if (overflow > 0) {
                plugin.getData().set("players." + uuid + ".overflow", overflow - 1);
                plugin.saveData();
                plugin.giveRewardSmart(player, "triumph", 1);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                openGUI(player);
            } else {
                player.sendMessage(plugin.PREFIX + ChatColor.RED + "You don't have any Overflow to convert!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            }
        }
        // 2. Change Mode
        else if (clicked == Material.COMPASS) {
            String mode = plugin.getData().getString("players." + uuid + ".reward_mode", "auto");
            String nextMode = switch (mode) { case "auto" -> "inv"; case "inv" -> "ec"; case "ec" -> "sys"; default -> "auto"; };
            plugin.getData().set("players." + uuid + ".reward_mode", nextMode);
            plugin.saveData();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
            openGUI(player);
        }
        // 3. Deposit
        else if (clicked == Material.ENDER_CHEST) {
            player.closeInventory();
            player.performCommand("noesis deposit");
        }
        // 4. Claim 1 Star
        else if (clicked == Material.NETHER_STAR) {
            if (event.isLeftClick()) { // Claim Triumph
                int st = plugin.getData().getInt("players." + uuid + ".stored_triumph", 0);
                if (st > 0) {
                    HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(plugin.createStar("triumph"));
                    if (!leftover.isEmpty()) {
                        player.getInventory().removeItem(plugin.createStar("triumph"));
                        player.sendMessage(plugin.PREFIX + ChatColor.RED + "Inventory full! Please make some space before claiming.");
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                        return;
                    }
                    plugin.getData().set("players." + uuid + ".stored_triumph", st - 1);
                    plugin.saveData();
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
                    openGUI(player);
                } else {
                    player.sendMessage(plugin.PREFIX + ChatColor.RED + "No Triumph Stars in Cloud Storage!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                }
            } else if (event.isRightClick()) { // Claim Soul
                int ss = plugin.getData().getInt("players." + uuid + ".stored_soul", 0);
                if (ss > 0) {
                    HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(plugin.createStar("soul"));
                    if (!leftover.isEmpty()) {
                        player.getInventory().removeItem(plugin.createStar("soul"));
                        player.sendMessage(plugin.PREFIX + ChatColor.RED + "Inventory full! Please make some space before claiming.");
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                        return;
                    }
                    plugin.getData().set("players." + uuid + ".stored_soul", ss - 1);
                    plugin.saveData();
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
                    openGUI(player);
                } else {
                    player.sendMessage(plugin.PREFIX + ChatColor.RED + "No Soul Stars in Cloud Storage!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                }
            }
        }
        // 5. Convert ALL Overflow (Slot 19)
        else if (slot == 19) {
            int overflow = plugin.getData().getInt("players." + uuid + ".overflow", 0);
            if (overflow > 0) {
                plugin.getData().set("players." + uuid + ".overflow", 0);
                plugin.saveData();
                plugin.giveRewardSmart(player, "triumph", overflow);
                player.sendMessage(plugin.PREFIX + ChatColor.GREEN + "Converted all " + overflow + " Overflow into Triumph Stars!");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                openGUI(player);
            } else {
                player.sendMessage(plugin.PREFIX + ChatColor.RED + "You don't have any Overflow to convert!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            }
        }
        // 6. Claim ALL Stars from Cloud (Slot 25)
        else if (slot == 25) {
            int st = plugin.getData().getInt("players." + uuid + ".stored_triumph", 0);
            int ss = plugin.getData().getInt("players." + uuid + ".stored_soul", 0);
            if (st <= 0 && ss <= 0) {
                player.sendMessage(plugin.PREFIX + ChatColor.RED + "No stars in Cloud Storage!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
            int claimedT = 0, claimedS = 0;
            while (st > 0) {
                HashMap<Integer, ItemStack> left = player.getInventory().addItem(plugin.createStar("triumph"));
                if (!left.isEmpty()) {
                    player.getInventory().removeItem(plugin.createStar("triumph"));
                    break;
                }
                st--;
                claimedT++;
            }
            while (ss > 0) {
                HashMap<Integer, ItemStack> left = player.getInventory().addItem(plugin.createStar("soul"));
                if (!left.isEmpty()) {
                    player.getInventory().removeItem(plugin.createStar("soul"));
                    break;
                }
                ss--;
                claimedS++;
            }
            plugin.getData().set("players." + uuid + ".stored_triumph", st);
            plugin.getData().set("players." + uuid + ".stored_soul", ss);
            plugin.saveData();
            if (claimedT > 0 || claimedS > 0) {
                player.sendMessage(plugin.PREFIX + ChatColor.GREEN + "Claimed " + (claimedT + claimedS) + " Stars (" + claimedT + " Triumph, " + claimedS + " Soul) from Cloud!");
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
            }
            if (st > 0 || ss > 0) {
                player.sendMessage(plugin.PREFIX + ChatColor.RED + "Inventory full! Some stars remain in Cloud Storage.");
            }
            openGUI(player);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (ChatColor.stripColor(event.getView().getTitle()).equals("✨ Noesis Star Manager")) {
            event.setCancelled(true);
        }
    }
}