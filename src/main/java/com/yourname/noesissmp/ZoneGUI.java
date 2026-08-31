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

        String t1 = plugin.getConfig().getString("players." + p.getUniqueId() + ".zone.tier1", "none");
        String t2 = plugin.getConfig().getString("players." + p.getUniqueId() + ".zone.tier2", "none");
        String t3 = plugin.getConfig().getString("players." + p.getUniqueId() + ".zone.tier3", "none");

        // กระจกพื้นหลัง
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta gm = glass.getItemMeta(); gm.setDisplayName(" "); glass.setItemMeta(gm);
        for(int i = 0; i < 45; i++) inv.setItem(i, glass);

        // 🔴 TIER 3 (Top Row)
        inv.setItem(11, createNode(Material.GHAST_TEAR, t3.equals("light"), "&f&lLIGHT: Tier 3",
                "&7Cost: &615 Triumph Stars", "", "&f▶ Combo Debuff & DMG Boost:", "&7Hit an enemy 3 times to reduce", "&7their SPD & increase your DMG by +3%", "&7(Max 5 Stacks / 15% Boost).", "", getStatusText("light", t3, !t2.equals("none"))));
        inv.setItem(15, createNode(Material.TNT, t3.equals("heavy"), "&c&lHEAVY: Tier 3",
                "&7Cost: &615 Triumph Stars", "", "&c▶ Shockwave Finisher (30s CD):", "&7At 5 Combo Stacks, your next hit", "&7slams nearby enemies down, applying", "&7Slowness 4 (1s) & Slowness 2 (5s),", "&7and seals them in a 10-block domain.", "", getStatusText("heavy", t3, !t2.equals("none"))));

        // 🔴 TIER 2 (Middle Row)
        String handMode = plugin.getConfig().getString("players." + p.getUniqueId() + ".zone.hand_mode", "normal");
        boolean isInverted = handMode.equals("invert");
        inv.setItem(19, createNode(
                isInverted ? Material.AMETHYST_SHARD : Material.PRISMARINE_SHARD,
                isInverted,
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
                "&7Cost: &610 Triumph Stars", "", "&f▶ Afterimage (20s CD):", "&7Press Offhand [F] with a Sword in Zone", "&7to summon a running afterimage storm,", "&7shredding enemy armor durability continuously (1.5s),", "&7and granting Invisibility (2s) & Speed (1.5s).", "", getStatusText("light", t2, !t1.equals("none"))));
        inv.setItem(24, createNode(Material.IRON_AXE, t2.equals("heavy"), "&c&lHEAVY: Tier 2",
                "&7Cost: &610 Triumph Stars", "", "&c▶ Combo Stack:", "&7Removes ANY attack speed buffs.", "&7Successive charged hits grant", "&7+10% DMG & -3% ATK Speed", "&7(Max 5 Stacks / +50% DMG, -15% SPD).", "&7Missing a swing removes 1 stack.", "", getStatusText("heavy", t2, !t1.equals("none"))));

        // 🔴 TIER 1 (Bottom Row - Core Passive)
        inv.setItem(29, createNode(Material.FEATHER, t1.equals("light"), "&f&lLIGHT: Tier 1 (Core)",
                "&7Cost: &65 Triumph Stars", "", "&f▶ Passive (Always Active):", "&7+15% ATK Speed. Crits grant", "&7Invisibility & Speed 2 (2s).", "", "&f▶ Perfect Dodge:", "&735% chance to teleport behind attacker", "&7and blind/slow them.", "", getStatusText("light", t1, true)));
        inv.setItem(33, createNode(Material.ANVIL, t1.equals("heavy"), "&c&lHEAVY: Tier 1 (Core)",
                "&7Cost: &65 Triumph Stars", "", "&c▶ Passive (Always Active):", "&7+50% DMG, -10% ATK Speed.", "&7Permanent Slowness 1.", "", "&c▶ Shield Parry:", "&7Blocking any attack with a shield", "&7stuns and weakens the attacker.", "", getStatusText("heavy", t1, true)));

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

            String t1 = plugin.getConfig().getString("players." + uuid + ".zone.tier1", "none");
            String t2 = plugin.getConfig().getString("players." + uuid + ".zone.tier2", "none");
            String t3 = plugin.getConfig().getString("players." + uuid + ".zone.tier3", "none");

            // Hand Mode Toggle
            if (slot == 19) {
                String currentMode = plugin.getConfig().getString("players." + uuid + ".zone.hand_mode", "normal");
                String nextMode = currentMode.equals("invert") ? "normal" : "invert";
                plugin.getConfig().set("players." + uuid + ".zone.hand_mode", nextMode);
                plugin.saveConfig();
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
                openGUI(p);
                return;
            }

            // รีเซ็ตสกิล
            if (slot == 40) {
                plugin.getConfig().set("players." + uuid + ".zone.tier1", "none");
                plugin.getConfig().set("players." + uuid + ".zone.tier2", "none");
                plugin.getConfig().set("players." + uuid + ".zone.tier3", "none");
                plugin.saveConfig();
                if (plugin.combatListener != null) {
                    plugin.combatListener.endTheZone(p);
                }
                if (p.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_SPEED) != null) {
                    p.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_SPEED).setBaseValue(4.0);
                }
                if (p.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED) != null) {
                    p.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.1);
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
                plugin.getConfig().set("players." + uuid + ".zone.tier1", slot == 29 ? "light" : "heavy");
                plugin.saveConfig();
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
                plugin.getConfig().set("players." + uuid + ".zone.tier2", slot == 20 ? "light" : "heavy");
                plugin.saveConfig();
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

    public void disableZoneTree() {
        plugin.getConfig().set("settings.zonetree_enabled", false);

        for (Player onlineP : Bukkit.getOnlinePlayers()) {
            if (plugin.combatListener != null) {
                plugin.combatListener.endTheZone(onlineP);
            }
            if (onlineP.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_SPEED) != null) {
                onlineP.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_SPEED).setBaseValue(4.0);
            }
            if (onlineP.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED) != null) {
                onlineP.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.1);
            }
            onlineP.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);
        }

        if (plugin.getConfig().contains("players")) {
            for (String uuidStr : plugin.getConfig().getConfigurationSection("players").getKeys(false)) {
                String t1 = plugin.getConfig().getString("players." + uuidStr + ".zone.tier1", "none");
                String t2 = plugin.getConfig().getString("players." + uuidStr + ".zone.tier2", "none");
                String t3 = plugin.getConfig().getString("players." + uuidStr + ".zone.tier3", "none");

                int refund = 0;
                if (!t1.equals("none")) refund += 5;
                if (!t2.equals("none")) refund += 10;
                if (!t3.equals("none")) refund += 15;

                if (refund > 0) {
                    int stored = plugin.getConfig().getInt("players." + uuidStr + ".stored_triumph", 0);
                    plugin.getConfig().set("players." + uuidStr + ".stored_triumph", stored + refund);
                    plugin.getConfig().set("players." + uuidStr + ".zone.tier1", "none");
                    plugin.getConfig().set("players." + uuidStr + ".zone.tier2", "none");
                    plugin.getConfig().set("players." + uuidStr + ".zone.tier3", "none");

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
                        int prevPending = plugin.getConfig().getInt("players." + uuidStr + ".pending_zone_refund", 0);
                        plugin.getConfig().set("players." + uuidStr + ".pending_zone_refund", prevPending + refund);
                    }
                }
            }
        }

        plugin.saveConfig();
    }

    public void enableZoneTree() {
        plugin.getConfig().set("settings.zonetree_enabled", true);
        plugin.saveConfig();
    }
}