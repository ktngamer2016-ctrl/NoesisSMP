package com.yourname.noesissmp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class AltarGUI implements Listener {

    private final NoesisSMP plugin;
    public static final String TITLE = ChatColor.DARK_RED + "☠ " + ChatColor.GOLD + "Altar of Triumph" + ChatColor.DARK_RED + " ☠";

    private final int[] glassSlots = {0, 1, 2, 6, 7, 8, 9, 11, 15, 17, 18, 19, 20, 24, 25, 26};
    private final int[] gridSlots = {3, 4, 5, 12, 13, 14, 21, 22, 23};
    private final int LEFT_CATALYST = 10;
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
                ChatColor.GRAY + "Fill the 3x3 grid with resources to forge Stars.",
                "",
                ChatColor.RED + "【 Activation Requirement 】",
                ChatColor.GRAY + "- 1x Netherite Block (Left Slot)",
                "",
                ChatColor.YELLOW + "【 Usage Quota 】",
                ChatColor.GRAY + "- 1 Craft per Player per Altar Cycle (1 Hour)",
                "",
                ChatColor.AQUA + "【 Exchange Rates (per Star) 】",
                ChatColor.GOLD + "- 9x Copper Ingot " + ChatColor.WHITE + "➔ 1 Star",
                ChatColor.YELLOW + "- 4x Gold Ingot " + ChatColor.WHITE + "➔ 1 Star",
                ChatColor.WHITE + "- 2x Iron Ingot " + ChatColor.WHITE + "➔ 1 Star",
                ChatColor.GREEN + "- 2x Emerald " + ChatColor.WHITE + "➔ 1 Star",
                ChatColor.AQUA + "- 1x Diamond " + ChatColor.WHITE + "➔ 1 Star",
                ChatColor.DARK_GRAY + "- 1x Netherite Ingot " + ChatColor.WHITE + "➔ 5 Stars",
                ChatColor.DARK_PURPLE + "- 1x Netherite Block " + ChatColor.WHITE + "➔ 45 Stars",
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
        // 1. Quota check (Max 1 per person per active cycle)
        if (plugin.hasUsedAltarQuota(p)) {
            p.sendMessage(plugin.PREFIX + ChatColor.RED + "You have already used your 1-craft quota for this Altar alignment cycle!");
            p.sendMessage(plugin.PREFIX + ChatColor.GRAY + "Each player can only craft 1 time per active Altar cycle (1 Hour).");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        // 2. Catalyst check (Left Slot 10 must contain 1x Netherite Block)
        ItemStack catalyst = inv.getItem(LEFT_CATALYST);
        if (catalyst == null || catalyst.getType() != Material.NETHERITE_BLOCK || catalyst.getAmount() < 1) {
            p.sendMessage(plugin.PREFIX + ChatColor.RED + "You must place 1x Netherite Block in the left slot to activate the Altar!");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        // 3. Exchange rates calculation for materials in the 3x3 middle grid
        int copper = 0, iron = 0, gold = 0, emerald = 0, diamond = 0, netheriteIngot = 0, netheriteBlock = 0;
        List<ItemStack> invalidItems = new ArrayList<>();

        for (int slot : gridSlots) {
            ItemStack item = inv.getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                if (item.getType() == Material.COPPER_INGOT) copper += item.getAmount();
                else if (item.getType() == Material.IRON_INGOT) iron += item.getAmount();
                else if (item.getType() == Material.GOLD_INGOT) gold += item.getAmount();
                else if (item.getType() == Material.EMERALD) emerald += item.getAmount();
                else if (item.getType() == Material.DIAMOND) diamond += item.getAmount();
                else if (item.getType() == Material.NETHERITE_INGOT) netheriteIngot += item.getAmount();
                else if (item.getType() == Material.NETHERITE_BLOCK) netheriteBlock += item.getAmount();
                else invalidItems.add(item.clone());

                inv.setItem(slot, null);
            }
        }

        int copperStars = copper / 9;
        int ironStars = iron / 2;
        int goldStars = gold / 4;
        int emeraldStars = emerald / 2;
        int diamondStars = diamond;
        int netheriteIngotStars = netheriteIngot * 5;
        int netheriteBlockStars = netheriteBlock * 45;

        int totalStars = copperStars + ironStars + goldStars + emeraldStars + diamondStars + netheriteIngotStars + netheriteBlockStars;

        int copperRem = copper % 9;
        int ironRem = iron % 2;
        int goldRem = gold % 4;
        int emeraldRem = emerald % 2;

        if (totalStars == 0) {
            p.sendMessage(plugin.PREFIX + ChatColor.RED + "Not enough valid materials in the 3x3 grid to forge stars!");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);

            // Restore items back to grid
            if (copper > 0) inv.addItem(new ItemStack(Material.COPPER_INGOT, copper));
            if (iron > 0) inv.addItem(new ItemStack(Material.IRON_INGOT, iron));
            if (gold > 0) inv.addItem(new ItemStack(Material.GOLD_INGOT, gold));
            if (emerald > 0) inv.addItem(new ItemStack(Material.EMERALD, emerald));
            if (diamond > 0) inv.addItem(new ItemStack(Material.DIAMOND, diamond));
            if (netheriteIngot > 0) inv.addItem(new ItemStack(Material.NETHERITE_INGOT, netheriteIngot));
            if (netheriteBlock > 0) inv.addItem(new ItemStack(Material.NETHERITE_BLOCK, netheriteBlock));
            for (ItemStack invalid : invalidItems) inv.addItem(invalid);
            return;
        }

        // 4. Deduct 1x Netherite Block catalyst from Left Slot 10
        catalyst.setAmount(catalyst.getAmount() - 1);
        if (catalyst.getAmount() <= 0) {
            inv.setItem(LEFT_CATALYST, null);
        }

        // 5. Award Triumph Stars to player smart storage/inventory
        plugin.giveRewardSmart(p, "triumph", totalStars);

        // 6. Return excess materials & invalid items
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

        // 7. Record Quota Usage (1 per cycle)
        plugin.recordAltarUsage(p);

        p.sendMessage(plugin.PREFIX + ChatColor.GREEN + "Altar activated! Forged " + ChatColor.GOLD + totalStars + "x Triumph Stars! " + ChatColor.YELLOW + "(Quota: 1/1 used)");

        // 8. Trigger epic cosmic explosion effect
        Location loc = new Location(Bukkit.getWorlds().get(0), 0, 80, 0);
        playEpicAltarEffect(loc);
    }

    private void playEpicAltarEffect(Location centerLoc) {
        CraftingEffectManager.playActivationEffect(plugin, centerLoc);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!ChatColor.stripColor(e.getView().getTitle()).equals("☠ Altar of Triumph ☠")) return;
        Player p = (Player) e.getPlayer();

        // Return catalyst left slot and 3x3 grid slots
        int[] returnSlots = {LEFT_CATALYST, 3, 4, 5, 12, 13, 14, 21, 22, 23};

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