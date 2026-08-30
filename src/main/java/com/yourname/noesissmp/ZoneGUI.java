package com.yourname.noesissmp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.List;

public class ZoneGUI implements Listener {

    private final NoesisSMP plugin;
    public static final String TITLE = ChatColor.DARK_PURPLE + "✦ THE ZONE SKILL TREE ✦";

    public ZoneGUI(NoesisSMP plugin) {
        this.plugin = plugin;
    }

    public void openGUI(Player p) {
        Inventory inv = Bukkit.createInventory(null, 45, TITLE);

        String t1 = plugin.getConfig().getString("players." + p.getUniqueId() + ".zone.tier1", "none");
        String t2 = plugin.getConfig().getString("players." + p.getUniqueId() + ".zone.tier2", "none");
        String t3 = plugin.getConfig().getString("players." + p.getUniqueId() + ".zone.tier3", "none");

        // กระจกพื้นหลัง
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta gm = glass.getItemMeta(); gm.setDisplayName(" "); glass.setItemMeta(gm);
        for(int i = 0; i < 45; i++) inv.setItem(i, glass);

        // 🔴 TIER 3 (Top Row)
        inv.setItem(11, createNode(Material.GHAST_TEAR, "&f&lLIGHT: Tier 3",
                "&7Cost: &615 Triumph Stars", "", "&f▶ Combo Debuff:", "&7Hit an enemy 3 times to reduce", "&7their movement and attack speed.", "&7(Stacks up to 50%)", "", getStatusText("light", t3, !t2.equals("none"))));
        inv.setItem(15, createNode(Material.TNT, "&c&lHEAVY: Tier 3",
                "&7Cost: &615 Triumph Stars", "", "&c▶ Shockwave Domain:", "&7Every 5 hits triggers an AoE shockwave", "&7that damages, slows, and traps enemies", "&7inside a 10-block domain.", "", getStatusText("heavy", t3, !t2.equals("none"))));

        // 🔴 TIER 2 (Middle Row)
        inv.setItem(20, createNode(Material.PHANTOM_MEMBRANE, "&f&lLIGHT: Tier 2",
                "&7Cost: &610 Triumph Stars", "", "&f▶ Afterimage:", "&7Crits have 35% chance to create", "&7a clone and grant Speed 4 for 1s.", "", getStatusText("light", t2, !t1.equals("none"))));
        inv.setItem(24, createNode(Material.IRON_AXE, "&c&lHEAVY: Tier 2",
                "&7Cost: &610 Triumph Stars", "", "&c▶ Combo Stack:", "&7Successive hits grant +10% DMG", "&7and -5% ATK SPD (Max 3 Stacks).", "&7Missing a swing resets combo.", "", getStatusText("heavy", t2, !t1.equals("none"))));

        // 🔴 TIER 1 (Bottom Row - Core Passive)
        inv.setItem(29, createNode(Material.FEATHER, "&f&lLIGHT: Tier 1 (Core)",
                "&7Cost: &65 Triumph Stars", "", "&f▶ Passive (Always Active):", "&7+15% ATK Speed. Crits grant", "&7Invisibility & Speed 2 (5s).", "", "&f▶ Perfect Dodge:", "&735% chance to teleport behind attacker", "&7and blind/slow them.", "", getStatusText("light", t1, true)));
        inv.setItem(33, createNode(Material.ANVIL, "&c&lHEAVY: Tier 1 (Core)",
                "&7Cost: &65 Triumph Stars", "", "&c▶ Passive (Always Active):", "&7+50% DMG, -25% ATK Speed.", "&7Permanent Slowness 1.", "", "&c▶ Perfect Parry:", "&7Taking a hit within 0.25s of", "&7raising a shield stuns attacker.", "", getStatusText("heavy", t1, true)));

        // 🔴 ปุ่ม Reset
        inv.setItem(40, createNode(Material.BARRIER, "&4&lRESET SKILL TREE", "&7Reset all nodes and start over.", "&c(Triumph Stars will NOT be refunded!)", "", "&e▶ Click to Reset"));

        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f);
    }

    private String getStatusText(String type, String current, boolean isUnlocked) {
        if (current.equals(type)) return ChatColor.GREEN + "✔ UNLOCKED & SELECTED";
        if (!current.equals("none")) return ChatColor.DARK_GRAY + "❌ LOCKED (Other Path Selected)";
        if (!isUnlocked) return ChatColor.RED + "🔒 LOCKED (Require Previous Tier)";
        return ChatColor.YELLOW + "▶ Click to Unlock & Select";
    }

    private ItemStack createNode(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat); ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        List<String> loreList = Arrays.asList(lore).stream().map(l -> ChatColor.translateAlternateColorCodes('&', l)).toList();
        meta.setLore(loreList); item.setItemMeta(meta); return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!ChatColor.stripColor(e.getView().getTitle()).equals("✦ THE ZONE SKILL TREE ✦")) return;
        e.setCancelled(true);

        if (e.getClickedInventory() == e.getView().getTopInventory()) {
            Player p = (Player) e.getWhoClicked();
            String uuid = p.getUniqueId().toString();
            int slot = e.getRawSlot();

            String t1 = plugin.getConfig().getString("players." + uuid + ".zone.tier1", "none");
            String t2 = plugin.getConfig().getString("players." + uuid + ".zone.tier2", "none");
            String t3 = plugin.getConfig().getString("players." + uuid + ".zone.tier3", "none");

            // รีเซ็ตสกิล
            if (slot == 40) {
                plugin.getConfig().set("players." + uuid + ".zone.tier1", "none");
                plugin.getConfig().set("players." + uuid + ".zone.tier2", "none");
                plugin.getConfig().set("players." + uuid + ".zone.tier3", "none");
                plugin.saveConfig();
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                openGUI(p);
                return;
            }

            // TIER 1
            if (slot == 29 || slot == 33) {
                if (!t1.equals("none")) { p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f); return; }
                if (!consumeStars(p, 5)) { p.sendMessage(plugin.PREFIX + ChatColor.RED + "You need 5 Triumph Stars in your inventory!"); p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f); return; }
                plugin.getConfig().set("players." + uuid + ".zone.tier1", slot == 29 ? "light" : "heavy");
                plugin.saveConfig(); p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f); openGUI(p);
            }
            // TIER 2
            else if (slot == 20 || slot == 24) {
                if (t1.equals("none")) { p.sendMessage(plugin.PREFIX + ChatColor.RED + "You must unlock Tier 1 first!"); p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f); return; }
                if (!t2.equals("none")) { p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f); return; }
                if (!consumeStars(p, 10)) { p.sendMessage(plugin.PREFIX + ChatColor.RED + "You need 10 Triumph Stars in your inventory!"); p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f); return; }
                plugin.getConfig().set("players." + uuid + ".zone.tier2", slot == 20 ? "light" : "heavy");
                plugin.saveConfig(); p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f); openGUI(p);
            }
            // TIER 3
            else if (slot == 11 || slot == 15) {
                if (t2.equals("none")) { p.sendMessage(plugin.PREFIX + ChatColor.RED + "You must unlock Tier 2 first!"); p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f); return; }
                if (!t3.equals("none")) { p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f); return; }
                if (!consumeStars(p, 15)) { p.sendMessage(plugin.PREFIX + ChatColor.RED + "You need 15 Triumph Stars in your inventory!"); p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f); return; }
                plugin.getConfig().set("players." + uuid + ".zone.tier3", slot == 11 ? "light" : "heavy");
                plugin.saveConfig(); p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f); openGUI(p);
            }
        }
    }

    private boolean consumeStars(Player p, int amount) {
        int count = 0;
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && item.getType() == Material.NETHER_STAR && item.hasItemMeta()) {
                String type = item.getItemMeta().getPersistentDataContainer().get(plugin.starTypeKey, PersistentDataType.STRING);
                if ("triumph".equals(type)) count += item.getAmount();
            }
        }
        if (count < amount) return false;

        int toRemove = amount;
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            ItemStack item = p.getInventory().getItem(i);
            if (item != null && item.getType() == Material.NETHER_STAR && item.hasItemMeta()) {
                String type = item.getItemMeta().getPersistentDataContainer().get(plugin.starTypeKey, PersistentDataType.STRING);
                if ("triumph".equals(type)) {
                    if (item.getAmount() <= toRemove) {
                        toRemove -= item.getAmount();
                        p.getInventory().setItem(i, null);
                    } else {
                        item.setAmount(item.getAmount() - toRemove);
                        toRemove = 0;
                    }
                    if (toRemove <= 0) break;
                }
            }
        }
        return true;
    }
}