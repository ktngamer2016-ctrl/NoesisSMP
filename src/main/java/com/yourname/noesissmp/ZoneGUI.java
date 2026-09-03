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
        if (!plugin.getConfig().getBoolean("settings.zonetree_enabled", true)) {
            p.sendMessage(plugin.PREFIX + ChatColor.RED + "The Zone Skill Tree is currently disabled by an administrator.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 45, TITLE);

        String t1 = plugin.getData().getString("players." + p.getUniqueId() + ".zone.tier1", "none");
        String t2 = plugin.getData().getString("players." + p.getUniqueId() + ".zone.tier2", "none");
        String t3 = plugin.getData().getString("players." + p.getUniqueId() + ".zone.tier3", "none");

        // กระจกพื้นหลัง
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta gm = glass.getItemMeta(); gm.setDisplayName(" "); glass.setItemMeta(gm);
        for(int i = 0; i < 45; i++) inv.setItem(i, glass);

        // 🔴 TIER 3 (Top Row)
        inv.setItem(11, createNode(Material.GHAST_TEAR, t3.equals("light"), "&f&lLIGHT: Tier 3",
                "&7Cost: &615 Triumph Stars", "", "&f▶ Precision Stacks:", "&7Every 3 player hits grants 1 stack.", "&7Stacks boost damage and reduce", "&7the target's attack speed.", "&7Cap: &f15%", "&7Shield offhand: &c10%", "&7Diamond/Netherite offhand: &a25%", "", getStatusText("light", t3, !t2.equals("none"))));
        inv.setItem(15, createNode(Material.TNT, t3.equals("heavy"), "&c&lHEAVY: Tier 3",
                "&7Cost: &615 Triumph Stars", "", "&c▶ Shockwave (30s CD):", "&7At 5 stacks, your next hit slams", "&7nearby enemies and traps them", "&7inside a slowing 10-block domain.", "", getStatusText("heavy", t3, !t2.equals("none"))));

        // 🔴 TIER 2 (Middle Row)
        String handMode = plugin.getData().getString("players." + p.getUniqueId() + ".zone.hand_mode", "normal");
        boolean isInverted = handMode.equals("invert");
        inv.setItem(19, createNode(
                isInverted ? Material.AMETHYST_SHARD : Material.PRISMARINE_SHARD,
                false,
                isInverted ? "&e&lHand Mode: &bInvert Mode" : "&e&lHand Mode: &aNormal Mode",
                "&7Current: " + (isInverted ? "&bInvert (Left-Handed)" : "&aNormal (Right-Handed)"),
                "",
                "&7In Invert Mode, the offhand and",
                "&7mainhand on the afterimage clone",
                "&7are swapped to match left-handed players.",
                "",
                "&e▶ Click to Toggle"
        ));

        inv.setItem(20, createNode(Material.PHANTOM_MEMBRANE, t2.equals("light"), "&f&lLIGHT: Tier 2",
                "&7Cost: &610 Triumph Stars", "", "&f▶ Afterimage (20s CD):", "&7Press [F] with a Sword in the Zone.", "&7Shield offhand: Invisibility + Speed IV.", "&7Diamond/Netherite offhand: &a+3 range", "", getStatusText("light", t2, !t1.equals("none"))));
        inv.setItem(24, createNode(Material.IRON_AXE, t2.equals("heavy"), "&c&lHEAVY: Tier 2",
                "&7Cost: &610 Triumph Stars", "", "&c▶ Heavy Combo:", "&7Charged hits: &c+10% damage", "&7and &c-3% attack speed &7per stack.", "&7Maximum 5 stacks. Misses lose 1.", "", getStatusText("heavy", t2, !t1.equals("none"))));

        // 🔴 TIER 1 (Bottom Row - Core Passive)
        inv.setItem(29, createNode(Material.FEATHER, t1.equals("light"), "&f&lLIGHT: Tier 1 (Core)",
                "&7Cost: &65 Triumph Stars", "", "&f▶ Light Hit:", "&71.75x damage and +15% attack speed.", "&7Hits grant Invisibility + Speed II.", "&7Shield offhand: +5% attack speed, no passive.", "", "&f▶ Perfect Dodge (30s CD):", "&7Tap Sneak, then counter within 0.5s.", "&75 hearts true damage.", "&7Shield offhand: 3 hearts", "&7Best Sword offhand: &a7 hearts", "", getStatusText("light", t1, true)));
        inv.setItem(33, createNode(Material.ANVIL, t1.equals("heavy"), "&c&lHEAVY: Tier 1 (Core)",
                "&7Cost: &65 Triumph Stars", "", "&c▶ Heavy Hit:", "&72x damage with -10% attack speed", "&7and Slowness I.", "", "&c▶ Shield Parry:", "&7Blocking slows and weakens attackers.", "", getStatusText("heavy", t1, true)));

        // 🔴 ปุ่ม Reset
        inv.setItem(40, createNode(Material.BARRIER, false, "&4&lRESET SKILL TREE", "&7Reset all nodes and start over.", "&c(Triumph Stars will NOT be refunded!)", "", "&e▶ Click to Reset"));

        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f);
    }

    private String getStatusText(String type, String current, boolean isUnlocked) {
        if (current.equals(type)) return ChatColor.GREEN + "✔ UNLOCKED & SELECTED";
        if (!current.equals("none")) return ChatColor.DARK_GRAY + "❌ LOCKED (Other Path Selected)";
        if (!isUnlocked) return ChatColor.RED + "🔒 LOCKED (Require Previous Tier)";
        return ChatColor.YELLOW + "▶ Click to Unlock & Select";
    }

    private ItemStack createNode(Material mat, boolean isSelected, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        List<String> loreList = Arrays.asList(lore).stream().map(l -> ChatColor.translateAlternateColorCodes('&', l)).toList();
        meta.setLore(loreList);
        if (isSelected) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            try {
                meta.setEnchantmentGlintOverride(true);
            } catch (Throwable ignored) {}
        }
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!ChatColor.stripColor(e.getView().getTitle()).equals("✦ THE ZONE SKILL TREE ✦")) return;
        e.setCancelled(true);

        if (e.getClickedInventory() == e.getView().getTopInventory()) {
            Player p = (Player) e.getWhoClicked();
            String uuid = p.getUniqueId().toString();
            int slot = e.getRawSlot();

            String t1 = plugin.getData().getString("players." + uuid + ".zone.tier1", "none");
            String t2 = plugin.getData().getString("players." + uuid + ".zone.tier2", "none");
            String t3 = plugin.getData().getString("players." + uuid + ".zone.tier3", "none");

            // Hand Mode Toggle
            if (slot == 19) {
                String currentMode = plugin.getData().getString("players." + uuid + ".zone.hand_mode", "normal");
                String nextMode = currentMode.equals("invert") ? "normal" : "invert";
                plugin.getData().set("players." + uuid + ".zone.hand_mode", nextMode);
                plugin.saveData();
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
                openGUI(p);
                return;
            }

            // รีเซ็ตสกิล
            if (slot == 40) {
                plugin.getData().set("players." + uuid + ".zone.tier1", "none");
                plugin.getData().set("players." + uuid + ".zone.tier2", "none");
                plugin.getData().set("players." + uuid + ".zone.tier3", "none");
                plugin.saveData();
                if (plugin.combatListener != null) {
                    plugin.combatListener.endTheZone(p);
                }
                if (p.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_SPEED) != null) {
                    p.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_SPEED).setBaseValue(4.0);
                }
                p.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                openGUI(p);
                return;
            }

            // TIER 1
            if (slot == 29 || slot == 33) {
                if (!t1.equals("none")) { p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f); return; }
                if (!consumeStars(p, 5)) { p.sendMessage(plugin.PREFIX + ChatColor.RED + "You need 5 Triumph Stars in your inventory!"); p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f); return; }
                plugin.getData().set("players." + uuid + ".zone.tier1", slot == 29 ? "light" : "heavy");
                plugin.saveData();
                if (plugin.combatListener != null) {
                    plugin.combatListener.updateBaseAttackSpeed(p, slot == 29 ? "light" : "heavy", 0);
                }
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f); openGUI(p);
            }
            // TIER 2
            else if (slot == 20 || slot == 24) {
                if (t1.equals("none")) { p.sendMessage(plugin.PREFIX + ChatColor.RED + "You must unlock Tier 1 first!"); p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f); return; }
                if (!t2.equals("none")) { p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f); return; }
                if (!consumeStars(p, 10)) { p.sendMessage(plugin.PREFIX + ChatColor.RED + "You need 10 Triumph Stars in your inventory!"); p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f); return; }
                plugin.getData().set("players." + uuid + ".zone.tier2", slot == 20 ? "light" : "heavy");
                plugin.saveData();
                if (plugin.combatListener != null) {
                    plugin.combatListener.updateBaseAttackSpeed(p, t1, 0);
                }
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f); openGUI(p);
            }
            // TIER 3
            else if (slot == 11 || slot == 15) {
                if (t2.equals("none")) { p.sendMessage(plugin.PREFIX + ChatColor.RED + "You must unlock Tier 2 first!"); p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f); return; }
                if (!t3.equals("none")) { p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f); return; }
                if (!consumeStars(p, 15)) { p.sendMessage(plugin.PREFIX + ChatColor.RED + "You need 15 Triumph Stars in your inventory!"); p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f); return; }
                plugin.getData().set("players." + uuid + ".zone.tier3", slot == 11 ? "light" : "heavy");
                plugin.saveData(); p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f); openGUI(p);
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

    public void disableZoneTree() {
        plugin.getConfig().set("settings.zonetree_enabled", false);

        for (Player onlineP : Bukkit.getOnlinePlayers()) {
            if (plugin.combatListener != null) {
                plugin.combatListener.endTheZone(onlineP);
            }
            if (onlineP.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_SPEED) != null) {
                onlineP.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_SPEED).setBaseValue(4.0);
            }
            onlineP.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);
        }

        if (plugin.getData().contains("players")) {
            for (String uuidStr : plugin.getData().getConfigurationSection("players").getKeys(false)) {
                String t1 = plugin.getData().getString("players." + uuidStr + ".zone.tier1", "none");
                String t2 = plugin.getData().getString("players." + uuidStr + ".zone.tier2", "none");
                String t3 = plugin.getData().getString("players." + uuidStr + ".zone.tier3", "none");

                int refund = 0;
                if (!t1.equals("none")) refund += 5;
                if (!t2.equals("none")) refund += 10;
                if (!t3.equals("none")) refund += 15;

                if (refund > 0) {
                    int stored = plugin.getData().getInt("players." + uuidStr + ".stored_triumph", 0);
                    plugin.getData().set("players." + uuidStr + ".stored_triumph", stored + refund);
                    plugin.getData().set("players." + uuidStr + ".zone.tier1", "none");
                    plugin.getData().set("players." + uuidStr + ".zone.tier2", "none");
                    plugin.getData().set("players." + uuidStr + ".zone.tier3", "none");

                    boolean sentLive = false;
                    try {
                        java.util.UUID u = java.util.UUID.fromString(uuidStr);
                        Player p = Bukkit.getPlayer(u);
                        if (p != null && p.isOnline()) {
                            if (ChatColor.stripColor(p.getOpenInventory().getTitle()).contains("THE ZONE SKILL TREE")) {
                                p.closeInventory();
                            }
                            p.sendMessage(plugin.PREFIX + ChatColor.GOLD + "The Zone Skill Tree has been disabled. Refunded " + ChatColor.YELLOW + refund + " Triumph Stars" + ChatColor.GOLD + " to your Cloud Storage!");
                            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                            sentLive = true;
                        }
                    } catch (Exception ignored) {}

                    if (!sentLive) {
                        int prevPending = plugin.getData().getInt("players." + uuidStr + ".pending_zone_refund", 0);
                        plugin.getData().set("players." + uuidStr + ".pending_zone_refund", prevPending + refund);
                    }
                }
            }
        }

        plugin.saveData();
        plugin.saveConfig();
    }

    public void enableZoneTree() {
        plugin.getConfig().set("settings.zonetree_enabled", true);
        plugin.saveConfig();
    }
}
