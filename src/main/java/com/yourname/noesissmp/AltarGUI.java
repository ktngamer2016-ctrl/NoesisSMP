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
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class AltarGUI implements Listener {

    private final NoesisSMP plugin;
    public static final String TITLE = ChatColor.DARK_RED + "☠ " + ChatColor.GOLD + "Altar of Triumph" + ChatColor.DARK_RED + " ☠";
    public static final int MAX_STARS_PER_SESSION = 10;
    private final java.util.Map<java.util.UUID, Integer> sessionCrafted = new HashMap<>();

    private final int[] glassSlots = {0, 1, 2, 6, 7, 8, 9, 11, 15, 17, 18, 19, 20, 24, 25, 26};
    private final int[] gridSlots = {3, 4, 5, 12, 13, 14, 21, 22, 23};
    private final int CATALYST_SLOT = 10;
    private final int RESULT_SLOT = 16;

    public AltarGUI(NoesisSMP plugin) {
        this.plugin = plugin;
    }

    public void resetSessionLimits() {
        sessionCrafted.clear();
    }

    public void resetSession() {
        resetSessionLimits();
    }

    public void openGUI(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);

        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = border.getItemMeta();
        meta.setDisplayName(" ");
        border.setItemMeta(meta);

        for (int slot : glassSlots) inv.setItem(slot, border);

        int crafted = sessionCrafted.getOrDefault(p.getUniqueId(), 0);
        int remainingLimit = Math.max(0, MAX_STARS_PER_SESSION - crafted);

        ItemStack btn = new ItemStack(Material.NETHER_STAR);
        ItemMeta bm = btn.getItemMeta();
        bm.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "✦ FORGE TRIUMPH STAR ✦");
        bm.setLore(Arrays.asList(
                ChatColor.GRAY + "Fill the 3x3 grid to accumulate points.",
                "",
                ChatColor.RED + "【 Activation Cost 】",
                ChatColor.GRAY + "- 2x Nether Star (Catalyst Slot)",
                "",
                ChatColor.AQUA + "【 Exchange Rates 】",
                ChatColor.AQUA + "- 7x Diamond Block " + ChatColor.WHITE + "➔ 1 Star",
                ChatColor.DARK_GRAY + "- 2x Netherite Ingot " + ChatColor.WHITE + "➔ 1 Star",
                "",
                ChatColor.LIGHT_PURPLE + "【 Session Limit 】",
                ChatColor.GRAY + "- Traded This Open: " + (crafted >= MAX_STARS_PER_SESSION ? ChatColor.RED : ChatColor.YELLOW) + crafted + "/" + MAX_STARS_PER_SESSION + " Stars",
                ChatColor.GRAY + "- Remaining: " + (remainingLimit == 0 ? ChatColor.RED + "0 (LIMIT REACHED)" : ChatColor.GREEN + "" + remainingLimit + " Stars"),
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
        ItemStack catalyst = inv.getItem(CATALYST_SLOT);

        if (!isValidNetherStarCatalyst(catalyst)) {
            p.sendMessage(plugin.PREFIX + ChatColor.RED + "You must place 2 Nether Stars in the catalyst slot to activate the Altar!");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        int crafted = sessionCrafted.getOrDefault(p.getUniqueId(), 0);
        int remainingAllowance = MAX_STARS_PER_SESSION - crafted;

        int diamondBlocks = 0, netheriteIngots = 0;
        List<ItemStack> invalidItems = new ArrayList<>();

        for (int slot : gridSlots) {
            ItemStack item = inv.getItem(slot);
            if (item != null) {
                if (item.getType() == Material.DIAMOND_BLOCK) diamondBlocks += item.getAmount();
                else if (item.getType() == Material.NETHERITE_INGOT) netheriteIngots += item.getAmount();
                else invalidItems.add(item.clone());

                inv.setItem(slot, null);
            }
        }

        if (remainingAllowance <= 0) {
            p.sendMessage(plugin.PREFIX + ChatColor.RED + "You have reached the limit of " + MAX_STARS_PER_SESSION + " stars for this Altar session!");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);

            if (diamondBlocks > 0) inv.addItem(new ItemStack(Material.DIAMOND_BLOCK, diamondBlocks));
            if (netheriteIngots > 0) inv.addItem(new ItemStack(Material.NETHERITE_INGOT, netheriteIngots));
            for (ItemStack invalid : invalidItems) inv.addItem(invalid);
            return;
        }

        int possibleDiamondStars = diamondBlocks / 7;
        int possibleNetheriteStars = netheriteIngots / 2;
        int totalPossible = possibleDiamondStars + possibleNetheriteStars;

        if (totalPossible == 0) {
            p.sendMessage(plugin.PREFIX + ChatColor.RED + "Not enough valid materials in the 3x3 grid!");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);

            if (diamondBlocks > 0) inv.addItem(new ItemStack(Material.DIAMOND_BLOCK, diamondBlocks));
            if (netheriteIngots > 0) inv.addItem(new ItemStack(Material.NETHERITE_INGOT, netheriteIngots));
            for (ItemStack invalid : invalidItems) inv.addItem(invalid);
            return;
        }

        int actualDiamondStars = Math.min(possibleDiamondStars, remainingAllowance);
        int remAllowanceAfterDiamond = remainingAllowance - actualDiamondStars;
        int actualNetheriteStars = Math.min(possibleNetheriteStars, remAllowanceAfterDiamond);

        int totalStars = actualDiamondStars + actualNetheriteStars;
        int usedDiamondBlocks = actualDiamondStars * 7;
        int usedNetheriteIngots = actualNetheriteStars * 2;

        int refundDiamondBlocks = diamondBlocks - usedDiamondBlocks;
        int refundNetheriteIngots = netheriteIngots - usedNetheriteIngots;

        if (catalyst.getAmount() == 2) inv.setItem(CATALYST_SLOT, null);
        else catalyst.setAmount(catalyst.getAmount() - 2);

        sessionCrafted.put(p.getUniqueId(), crafted + totalStars);

        for (int i = 0; i < totalStars; i++) {
            HashMap<Integer, ItemStack> left = p.getInventory().addItem(plugin.createStar("triumph"));
            for (ItemStack l : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), l);
        }

        List<ItemStack> itemsToReturn = new ArrayList<>(invalidItems);
        if (refundDiamondBlocks > 0) itemsToReturn.add(new ItemStack(Material.DIAMOND_BLOCK, refundDiamondBlocks));
        if (refundNetheriteIngots > 0) itemsToReturn.add(new ItemStack(Material.NETHERITE_INGOT, refundNetheriteIngots));

        for (ItemStack returnItem : itemsToReturn) {
            HashMap<Integer, ItemStack> left = p.getInventory().addItem(returnItem);
            for (ItemStack l : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), l);
        }

        if (!itemsToReturn.isEmpty()) {
            p.sendMessage(plugin.PREFIX + ChatColor.YELLOW + "Excess/Unused materials were returned to your inventory.");
        }

        p.sendMessage(plugin.PREFIX + ChatColor.GREEN + "Altar activated! Forged " + ChatColor.GOLD + totalStars + "x Triumph Stars! " + ChatColor.GRAY + "(Session: " + ChatColor.YELLOW + (crafted + totalStars) + "/" + MAX_STARS_PER_SESSION + ChatColor.GRAY + ")");

        Location loc = plugin.getAltarLocation();
        playEpicAltarEffect(loc);
    }

    private boolean isValidNetherStarCatalyst(ItemStack catalyst) {
        if (catalyst == null || catalyst.getType() != Material.NETHER_STAR || catalyst.getAmount() < 2) return false;
        if (!catalyst.hasItemMeta()) return true;

        ItemMeta meta = catalyst.getItemMeta();
        if (meta.getPersistentDataContainer().has(plugin.starTypeKey, PersistentDataType.STRING)) return false;

        // Also protect legacy custom stars created before their persistent tag existed.
        String name = ChatColor.stripColor(meta.getDisplayName());
        return name == null || (!name.contains("Triumph Star") && !name.contains("Soul Star"));
    }

    // 🔴 เมธอดสำหรับสร้าง Particle สุดอลังการแบบในภาพ
    private void playEpicAltarEffect(Location centerLoc) {
        CraftingEffectManager.playActivationEffect(plugin, centerLoc);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!ChatColor.stripColor(e.getView().getTitle()).equals("☠ Altar of Triumph ☠")) return;
        Player p = (Player) e.getPlayer();

        int[] returnSlots = {CATALYST_SLOT, 3, 4, 5, 12, 13, 14, 21, 22, 23};

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
