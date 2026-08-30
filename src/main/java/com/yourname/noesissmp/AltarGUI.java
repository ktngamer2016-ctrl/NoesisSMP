package com.yourname.noesissmp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class AltarGUI implements Listener {

    private final NoesisSMP plugin;
    public static final String TITLE = ChatColor.DARK_RED + "☠ " + ChatColor.GOLD + "Altar of Triumph" + ChatColor.DARK_RED + " ☠";

    private final int[] glassSlots = {0, 2, 6, 7, 8, 9, 10, 11, 15, 17, 18, 20, 24, 25, 26};
    private final int[] gridSlots = {3, 4, 5, 12, 13, 14, 21, 22, 23};
    private final int TOP_CATALYST = 1;
    private final int BOT_CATALYST = 19;
    private final int RESULT_SLOT = 16;

    public AltarGUI(NoesisSMP plugin) {
        this.plugin = plugin;
    }

    public void openGUI(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);

        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta gm = glass.getItemMeta();
        gm.setDisplayName(" ");
        glass.setItemMeta(gm);
        for (int slot : glassSlots) {
            inv.setItem(slot, glass);
        }

        ItemStack btn = new ItemStack(Material.NETHER_STAR);
        ItemMeta bm = btn.getItemMeta();
        bm.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "✦ FORGE TRIUMPH STAR ✦");
        bm.setLore(Arrays.asList(
                ChatColor.GRAY + "Fill the 3x3 grid to accumulate points.",
                "",
                ChatColor.RED + "【 Activation Cost 】",
                ChatColor.GRAY + "- 1x Netherite Ingot (Top Slot)",
                ChatColor.GRAY + "- 1x Netherite Ingot (Bottom Slot)",
                "",
                ChatColor.AQUA + "【 Exchange Rates 】",
                ChatColor.GOLD + "- 9x Copper Ingot " + ChatColor.WHITE + "➔ 1 Star",
                ChatColor.YELLOW + "- 4x Gold Ingot " + ChatColor.WHITE + "➔ 1 Star",
                ChatColor.WHITE + "- 2x Iron Ingot " + ChatColor.WHITE + "➔ 1 Star",
                ChatColor.GREEN + "- 2x Emerald " + ChatColor.WHITE + "➔ 1 Star",
                ChatColor.AQUA + "- 1x Diamond " + ChatColor.WHITE + "➔ 1 Star",
                ChatColor.DARK_GRAY + "- 1x Netherite Ingot " + ChatColor.WHITE + "➔ 5 Stars",
                "",
                ChatColor.GREEN + "Click to forge all valid materials!"
        ));
        btn.setItemMeta(bm);
        inv.setItem(RESULT_SLOT, btn);

        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1f);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!ChatColor.stripColor(e.getView().getTitle()).equals("☠ Altar of Triumph ☠")) return;

        if (e.getAction().name().contains("MOVE_TO_OTHER_INVENTORY") || e.getAction().name().contains("HOTBAR")) {
            e.setCancelled(true);
            return;
        }

        if (e.getClickedInventory() == e.getView().getTopInventory()) {
            int slot = e.getRawSlot();

            if (slot == RESULT_SLOT) {
                e.setCancelled(true);
                processCrafting((Player) e.getWhoClicked(), e.getInventory());
            } else {
                for (int g : glassSlots) {
                    if (slot == g) {
                        e.setCancelled(true);
                        break;
                    }
                }
            }
        }
    }

    private void processCrafting(Player p, Inventory inv) {
        ItemStack top = inv.getItem(TOP_CATALYST);
        ItemStack bot = inv.getItem(BOT_CATALYST);

        if (top == null || top.getType() != Material.NETHERITE_INGOT ||
                bot == null || bot.getType() != Material.NETHERITE_INGOT) {
            p.sendMessage(plugin.PREFIX + ChatColor.RED + "You must place Netherite Ingots in the top and bottom slots to activate the Altar!");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        int copper = 0, iron = 0, gold = 0, emerald = 0, diamond = 0, netheriteGrid = 0;
        List<ItemStack> invalidItems = new ArrayList<>();

        for (int slot : gridSlots) {
            ItemStack item = inv.getItem(slot);
            if (item != null) {
                if (item.getType() == Material.COPPER_INGOT) copper += item.getAmount();
                else if (item.getType() == Material.IRON_INGOT) iron += item.getAmount();
                else if (item.getType() == Material.GOLD_INGOT) gold += item.getAmount();
                else if (item.getType() == Material.EMERALD) emerald += item.getAmount();
                else if (item.getType() == Material.DIAMOND) diamond += item.getAmount();
                else if (item.getType() == Material.NETHERITE_INGOT) netheriteGrid += item.getAmount();
                else invalidItems.add(item.clone());

                inv.setItem(slot, null);
            }
        }

        int copperStars = copper / 9;
        int ironStars = iron / 2;
        int goldStars = gold / 4;
        int emeraldStars = emerald / 2;
        int diamondStars = diamond;
        int netheriteStars = netheriteGrid * 5;

        int totalStars = copperStars + ironStars + goldStars + emeraldStars + diamondStars + netheriteStars;

        int copperRem = copper % 9;
        int ironRem = iron % 2;
        int goldRem = gold % 4;
        int emeraldRem = emerald % 2;

        if (totalStars == 0) {
            p.sendMessage(plugin.PREFIX + ChatColor.RED + "Not enough valid materials in the 3x3 grid!");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);

            if (copper > 0) inv.addItem(new ItemStack(Material.COPPER_INGOT, copper));
            if (iron > 0) inv.addItem(new ItemStack(Material.IRON_INGOT, iron));
            if (gold > 0) inv.addItem(new ItemStack(Material.GOLD_INGOT, gold));
            if (emerald > 0) inv.addItem(new ItemStack(Material.EMERALD, emerald));
            if (diamond > 0) inv.addItem(new ItemStack(Material.DIAMOND, diamond));
            if (netheriteGrid > 0) inv.addItem(new ItemStack(Material.NETHERITE_INGOT, netheriteGrid));
            for (ItemStack invalid : invalidItems) inv.addItem(invalid);
            return;
        }

        top.setAmount(top.getAmount() - 1);
        bot.setAmount(bot.getAmount() - 1);

        for (int i = 0; i < totalStars; i++) {
            HashMap<Integer, ItemStack> left = p.getInventory().addItem(plugin.createStar("triumph"));
            for (ItemStack l : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), l);
        }

        List<ItemStack> itemsToReturn = new ArrayList<>(invalidItems);
        if (copperRem > 0) itemsToReturn.add(new ItemStack(Material.COPPER_INGOT, copperRem));
        if (ironRem > 0) itemsToReturn.add(new ItemStack(Material.IRON_INGOT, ironRem));
        if (goldRem > 0) itemsToReturn.add(new ItemStack(Material.GOLD_INGOT, goldRem));
        if (emeraldRem > 0) itemsToReturn.add(new ItemStack(Material.EMERALD, emeraldRem));

        for (ItemStack returnItem : itemsToReturn) {
            HashMap<Integer, ItemStack> left = p.getInventory().addItem(returnItem);
            for (ItemStack l : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), l);
        }

        if (!itemsToReturn.isEmpty()) {
            p.sendMessage(plugin.PREFIX + ChatColor.YELLOW + "Excess materials were returned to your inventory.");
        }

        p.sendMessage(plugin.PREFIX + ChatColor.GREEN + "Altar activated! Forged " + ChatColor.GOLD + totalStars + "x Triumph Stars!");

        Location loc = new Location(Bukkit.getWorlds().get(0), 0, 80, 0);
        playEpicAltarEffect(loc);
    }

    // 🔴 เมธอดสำหรับสร้าง Particle สุดอลังการแบบในภาพ
    private void playEpicAltarEffect(Location centerLoc) {
        CraftingEffectManager.playActivationEffect(plugin, centerLoc);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!ChatColor.stripColor(e.getView().getTitle()).equals("☠ Altar of Triumph ☠")) return;
        Player p = (Player) e.getPlayer();

        int[] returnSlots = {TOP_CATALYST, BOT_CATALYST, 3, 4, 5, 12, 13, 14, 21, 22, 23};

        for (int slot : returnSlots) {
            ItemStack item = e.getInventory().getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                HashMap<Integer, ItemStack> leftover = p.getInventory().addItem(item);
                for (ItemStack left : leftover.values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), left);
                }
                e.getInventory().setItem(slot, null);
            }
        }
    }
}