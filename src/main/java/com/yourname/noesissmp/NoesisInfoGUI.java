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
import java.util.List;

public class NoesisInfoGUI implements Listener {

    private final NoesisSMP plugin;
    public static final String LANG_MENU = ChatColor.DARK_GRAY + "Select Language / เลือกภาษา";
    public static final String INFO_MENU_EN = ChatColor.DARK_AQUA + "Noesis SMP Guide";
    public static final String INFO_MENU_TH = ChatColor.DARK_AQUA + "คู่มือ Noesis SMP";

    public NoesisInfoGUI(NoesisSMP plugin) {
        this.plugin = plugin;
    }

    public void openLanguageMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, LANG_MENU);
        gui.setItem(11, createItem(Material.WRITTEN_BOOK, "&e&lEnglish", "&7Click to read the guide."));
        gui.setItem(15, createItem(Material.BOOK, "&6&lภาษาไทย", "&7คลิกเพื่ออ่านคู่มือ"));
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
    }

    public void openInfoMenu(Player player, boolean isThai) {
        Inventory gui = Bukkit.createInventory(null, 36, isThai ? INFO_MENU_TH : INFO_MENU_EN);
        if (!isThai) {
            gui.setItem(10, createItem(Material.NETHER_STAR, "&e&l1. Core System",
                    "&c▶ Health & Death:",
                    "&7- Death: -1 Heart, Kill Stack resets to 0.",
                    "&7- 0 Hearts = Stats Wiped.",
                    "",
                    "&6▶ Star Manager (/noesis):",
                    "&7- Convert Overflow into Triumph Stars.",
                    "&7- Change reward modes (Auto/Inv/EC/Cloud)."));

            gui.setItem(11, createItem(Material.IRON_SWORD, "&c&l2. Combat & PVP Rules",
                    "&4▶ PVP Rules (Consent Required!):",
                    "&7- Official Events, Scheduled, or Mutual Agreement.",
                    "",
                    "&e▶ Weapon & Combat Tag:",
                    "&7- Dying within 15s of combat counts as a PVP kill.",
                    "&7- Stars will DROP AT THE CORPSE on kill.",
                    "",
                    "&6▶ Dynamic Bounty (40+ Point Gap):",
                    "&7- &aGiant Slayer:&7 Shatter up to 60% of victim's",
                    "&7  Overflow into Triumph Stars & get Bonus Souls!"));

            gui.setItem(12, createItem(Material.MACE, "&5&l3. Items & Restrictions",
                    "&d▶ Abyssal Mace (Custom Recipe!):",
                    "&7- Normal Mace is banned (Shatters on hit).",
                    "&7- Craft: Heavy Core(Top), Zacrozz Fragment(Mid),",
                    "&7  Breeze Rod(Bottom).",
                    "",
                    "&b▶ Ender Pearls (Ammo System):",
                    "&7- 16 max. Recharges 1 every 10s.",
                    "&7- Using all 16 triggers 60s cooldown.",
                    "",
                    "&c▶ Banned:",
                    "&7- Strength Potions, End Crystals, Minecart TNT."));

            gui.setItem(14, createItem(Material.BLAZE_POWDER, "&6&l4. Critical & The Zone",
                    "&e▶ Crit Stack System:",
                    "&7- Base Chance = (Kills+Overflow) * 0.8%",
                    "&7- Hit players/bosses to build stacks.",
                    "&7- Decays after 15s out of combat.",
                    "",
                    "&c▶ Crit Tiers:",
                    "&7- &eYellow (1.5x)&7 -> &6Orange (2x)&7 -> &cRed (3x)",
                    "&7- Needs 7 successful crits to unlock next tier.",
                    "",
                    "&5▶ THE ZONE (Black Crit):",
                    "&7- &l4.0x DMG&7. Enters THE ZONE (15s).",
                    "&7- 100% Crit chance. Select paths via &b/noesis zone"));

            gui.setItem(15, createItem(Material.CRAFTING_TABLE, "&d&l5. Altar of Triumph",
                    "&e▶ Location & Cycle:",
                    "&7- Fixed at &bX:0, Y:80, Z:0 &7(Unbreakable).",
                    "&7- Opens 1 Hour, Cooldown 48 Hours.",
                    "&7- Check status: &b/noesis altar status",
                    "",
                    "&c▶ How to Forge Stars:",
                    "&7- Requires &81x Netherite Block&7 (Catalyst Slot).",
                    "&7- Put ores in 3x3 grid (Leftovers are returned).",
                    "",
                    "&a▶ Exchange Rates (per 1 Star):",
                    "&7- &b7x Diamond Block &7➔ &61 Star",
                    "&7- &82x Netherite Ingot &7➔ &61 Star",
                    "&e- Limit: Max 10 Stars per Altar Open."));

            gui.setItem(16, createItem(Material.WITHER_SKELETON_SKULL, "&9&l6. Event System (/event)",
                    "&c▶ PVP Modes (Random TP 50 blocks):",
                    "&7- Boss Fight: 1 Boss per 4 Hunters.",
                    "&7- Team Fight: 120s to choose team.",
                    "",
                    "&a▶ PVE Raid (Zacrozz):",
                    "&7- Phase 1: Wither Skeleton | Phase 2: Wither.",
                    "",
                    "&e▶ Rewards:",
                    "&7- Die/Lose = 1x Reward.",
                    "&7- Win/Survive = 3x Rewards."));

            gui.setItem(31, createItem(Material.ARROW, "&c<- Back", "&7Return to language selection"));
        } else {
            gui.setItem(10, createItem(Material.NETHER_STAR, "&e&l1. ระบบหลัก (Core)",
                    "&c▶ ระบบเลือดและการตาย:",
                    "&7- ตายทุกกรณี: หัก 1 หัวใจ และ Kill Stack เป็น 0",
                    "&7- หัวใจเหลือ 0 = ล้างสเตตัสทั้งหมด",
                    "",
                    "&6▶ เมนูจัดการดาว (/noesis):",
                    "&7- ใช้ 1 Overflow สกัดเป็น 1 Triumph Star",
                    "&7- ปรับโหมดรับดาว (เข้าตัว/กล่อง/Cloud)"));

            gui.setItem(11, createItem(Material.IRON_SWORD, "&c&l2. กฎการต่อสู้ (Combat)",
                    "&4▶ กฎ PVP (ต้องยินยอมเท่านั้น!):",
                    "&7- นัดหมาย, ตกลงหน้างาน, หรือใน Event PVP เท่านั้น",
                    "",
                    "&e▶ การต่อสู้ และ Combat Tag:",
                    "&7- ตายภายใน 15วิ หลังโดนตี = ถูกฆ่าตาย",
                    "&7- ดาวจากการฆ่าจะ ดรอปตกที่ศพ",
                    "",
                    "&6▶ ระบบสมดุล (แต้มห่าง 40+):",
                    "&7- &aล้มยักษ์:&7 สกัดแต้ม Overflow ทิ้งสูงสุด 60%",
                    "&7  เปลี่ยนเป็นดาวให้คนฆ่า พร้อมแจก Soul โบนัส!"));

            gui.setItem(12, createItem(Material.MACE, "&5&l3. ไอเทมและข้อจำกัด",
                    "&d▶ กระบอง Abyssal Mace:",
                    "&7- Mace ธรรมดาถูกแบน (ตีสิ่งมีชีวิตจะแตกสลาย)",
                    "&7- คราฟต์: Heavy Core(บน), Zacrozz Fragment(กลาง),",
                    "&7  Breeze Rod(ล่าง)",
                    "",
                    "&b▶ Ender Pearl (ระบบ Ammo):",
                    "&7- ความจุ 16 ลูก (รีชาร์จ 1 ลูก/10 วิ)",
                    "&7- ปาครบ 16 ลูกรวดเดียว ติดคูลดาวน์ 60 วิ",
                    "",
                    "&c▶ ระบบที่แบน:",
                    "&7- ยา Strength, คริสตัล (CPvP), ระเบิด TNT รถราง"));

            gui.setItem(14, createItem(Material.BLAZE_POWDER, "&6&l4. คริติคอล & THE ZONE",
                    "&e▶ ระบบสะสมคริติคอล:",
                    "&7- โอกาสพื้นฐาน = (Kills+Overflow) * 0.8%",
                    "&7- ตีผู้เล่น/บอสเพื่อเก็บ Stack (รีเซ็ตถ้าไม่สู้ 15วิ)",
                    "",
                    "&c▶ ระดับคริติคอล:",
                    "&7- &eเหลือง (1.5x)&7 -> &6ส้ม (2x)&7 -> &cแดง (3x)",
                    "&7- ต้องตีคริให้ติด 7 ครั้งเพื่อปลดล็อกขั้นถัดไป",
                    "",
                    "&5▶ THE ZONE (คริสีดำ):",
                    "&7- ดาเมจ &l4.0x&7 และเข้าสู่โหมด THE ZONE (15วิ)",
                    "&7- &aคริ 100%&7 เลือกสายอัปสกิลได้ด้วย &b/noesis zone"));

            gui.setItem(15, createItem(Material.CRAFTING_TABLE, "&d&l5. แท่น Altar of Triumph",
                    "&e▶ พิกัดและเวลาทำงาน:",
                    "&7- ตั้งอยู่ที่ &bX:0, Y:80, Z:0 &7(พังไม่ได้)",
                    "&7- เปิดรอบละ 1 ชั่วโมง สลับคูลดาวน์ 48 ชั่วโมง",
                    "&7- เช็คเวลาเปิดได้ด้วย &b/noesis altar status",
                    "",
                    "&c▶ วิธีคราฟต์ดาว (Triumph Star):",
                    "&7- บังคับใส่ &8Netherite Block 1 บล็อก&7 (ช่องซ้าย) เพื่อเดินเครื่อง",
                    "&7- โยนแร่ผสมกันใน 3x3 ตรงกลาง (แร่เศษๆจะเด้งคืนให้)",
                    "",
                    "&a▶ อัตราแลกเปลี่ยน (ต่อ 1 ดาว):",
                    "&7- &bบล็อกเพชร 7 บล็อก &7➔ &61 ดาว",
                    "&7- &8เนเธอร์ไรต์ 2 แท่ง &7➔ &61 ดาว",
                    "&e- จำกัด: สูงสุด 10 ดาว ต่อรอบแท่นเปิด"));

            gui.setItem(16, createItem(Material.WITHER_SKELETON_SKULL, "&9&l6. ระบบกิจกรรม (/event)",
                    "&c▶ โหมด PVP (สุ่มเกิด 50 บล็อก):",
                    "&7- Boss Fight: บอส 1 ต่อผู้เล่น 4 คน",
                    "&7- Team Fight: เลือกทีมใน 120 วิ",
                    "",
                    "&a▶ โหมด PVE Raid (Zacrozz):",
                    "&7- เฟส 1: Wither Skeleton | เฟส 2: Wither",
                    "",
                    "&e▶ ของรางวัล:",
                    "&7- แพ้/ตาย = รับรางวัลปลอบใจ 1 ชิ้น",
                    "&7- ชนะ/รอด = รับรางวัล 3 ชิ้น"));

            gui.setItem(31, createItem(Material.ARROW, "&c<- กลับ", "&7กลับไปหน้าเลือกภาษา"));
        }
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PUT, 1f, 1f);
    }

    private ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        List<String> loreList = Arrays.asList(lore).stream().map(l -> ChatColor.translateAlternateColorCodes('&', l))
                .toList();
        meta.setLore(loreList);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (title == null)
            return;
        if (!title.contains("Select Language") && !title.contains("Noesis SMP") && !title.contains("เลือกภาษา")
                && !title.contains("คู่มือ"))
            return;
        event.setCancelled(true);
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR)
            return;
        Player player = (Player) event.getWhoClicked();
        Material clicked = event.getCurrentItem().getType();
        if (title.contains("Select Language") || title.contains("เลือกภาษา")) {
            if (clicked == Material.WRITTEN_BOOK)
                openInfoMenu(player, false);
            else if (clicked == Material.BOOK)
                openInfoMenu(player, true);
        } else {
            if (clicked == Material.ARROW)
                openLanguageMenu(player);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (title != null && (title.contains("Select Language") || title.contains("Noesis SMP")
                || title.contains("เลือกภาษา") || title.contains("คู่มือ"))) {
            event.setCancelled(true);
        }
    }
}