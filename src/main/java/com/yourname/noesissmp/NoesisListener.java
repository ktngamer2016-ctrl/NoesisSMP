package com.yourname.noesissmp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.FoodComponent;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NoesisListener implements Listener {

    private static final int GOLDEN_CARROT_NUTRITION = 6;
    private static final float GOLDEN_CARROT_SATURATION = 14.4f;
    private static final float GOLDEN_APPLE_SATURATION = 9.6f;
    private final Map<UUID, Long> fullHungerGoldenCarrotUseUntil = new HashMap<>();
    private final NoesisSMP plugin;

    public NoesisListener(NoesisSMP plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (prepareGoldenCarrots(player)) player.updateInventory();
            }
        }, 1L, 5L);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        if (plugin.isAltarLocation(loc)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.PREFIX + ChatColor.RED + "You cannot break the Altar of Triumph!");
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (item != null && item.getType() == Material.NETHER_STAR && item.hasItemMeta()) {
                if (item.getItemMeta().getPersistentDataContainer().has(plugin.starTypeKey, PersistentDataType.STRING)) {
                    event.setCancelled(true);
                    if (event.getWhoClicked() instanceof Player player) {
                        player.sendMessage(plugin.PREFIX + ChatColor.RED + "These magical stars cannot be used in normal crafting!");
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    }
                    return;
                }
            }
        }

        if (event.getRecipe() != null && event.getRecipe().getResult() != null) {
            ItemStack result = event.getRecipe().getResult();
            if (result.getType() == Material.MACE && result.hasItemMeta()) {
                if (result.getItemMeta().getPersistentDataContainer().has(plugin.trueMaceKey, PersistentDataType.STRING)) {
                    Bukkit.broadcastMessage(plugin.PREFIX + ChatColor.DARK_PURPLE + ChatColor.BOLD + "⚔ เศษเสี้ยวของ Zacrozz ได้ถูกหลอมรวมเป็นอาวุธโดยบุคคลปริศนา...");
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 0.5f);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String uuid = player.getUniqueId().toString();

        // Always cache latest player username in config
        plugin.getData().set("players." + uuid + ".name", player.getName());
        plugin.saveData();

        if (plugin.getData().getBoolean("players." + uuid + ".pending_heart_loss", false)) {
            plugin.getData().set("players." + uuid + ".pending_heart_loss", false);
            plugin.saveData();

            double currentMax = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
            double newMax = currentMax - 2.0;

            if (newMax <= 0.0) {
                player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0); player.setHealth(20.0);
                plugin.getData().set("players." + uuid + ".kills", 0); plugin.getData().set("players." + uuid + ".overflow", 0);
                plugin.getData().set("players." + uuid + ".stored_triumph", 0); plugin.getData().set("players." + uuid + ".stored_soul", 0);
                plugin.saveData();
                Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), "advancement revoke " + player.getName() + " everything");
                player.sendTitle(ChatColor.DARK_RED + "☠ SOUL SHATTERED ☠", ChatColor.RED + "You lost all hearts. Stats wiped.", 10, 100, 20);
                Bukkit.broadcastMessage(plugin.PREFIX + ChatColor.DARK_RED + ChatColor.BOLD + player.getName() + " has lost all their hearts and was WIPED!");
            } else {
                player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(newMax);
            }
        }

        try {
            org.bukkit.attribute.AttributeInstance maxHealthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (maxHealthAttr != null && maxHealthAttr.getValue() < 20.0 && !plugin.getData().contains("players." + uuid + ".kills")) {
                maxHealthAttr.setBaseValue(20.0); player.setHealth(20.0);
            }
        } catch (Exception exception) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Could not normalize max health for " + player.getName(), exception);
        }

        int pendingRefund = plugin.getData().getInt("players." + uuid + ".pending_zone_refund", 0);
        if (pendingRefund > 0) {
            plugin.getData().set("players." + uuid + ".pending_zone_refund", 0);
            plugin.saveData();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage(plugin.PREFIX + ChatColor.GOLD + "The Zone Skill Tree was disabled while you were offline. Refunded " + ChatColor.YELLOW + pendingRefund + " Triumph Stars" + ChatColor.GOLD + " to your Cloud Storage!");
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                }
            }, 30L);
        }

        // Refresh attributes on join according to player's unlocked skills
        try {
            String t1 = plugin.getData().getString("players." + uuid + ".zone.tier1", "none");
            if (plugin.combatListener != null) {
                plugin.combatListener.clearBlackCritStun(player);
                plugin.combatListener.updateBaseAttackSpeed(player, t1, 0);
            }
        } catch (Exception exception) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Could not refresh attributes for " + player.getName(), exception);
        }

        if (!plugin.getData().getBoolean("players." + uuid + ".seen_noesis_reboot_v2", false)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage("");
                    player.sendMessage(ChatColor.DARK_GRAY + "=====================================================");
                    player.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + " 🩸 NOESIS SMP (Season 1) 🩸");
                    player.sendMessage(ChatColor.GRAY + " Survive. Steal Hearts. Unleash your Power.");
                    player.sendMessage("");
                    player.sendMessage(ChatColor.WHITE + "  • " + ChatColor.YELLOW + "📖 " + ChatColor.AQUA + "/guide" + ChatColor.WHITE + " - Read the server mechanics (Important!)");
                    player.sendMessage(ChatColor.WHITE + "  • " + ChatColor.GREEN + "📊 " + ChatColor.AQUA + "/status" + ChatColor.WHITE + " - Check your stats and Crit Chance");
                    player.sendMessage(ChatColor.WHITE + "  • " + ChatColor.GOLD + "⚙ " + ChatColor.AQUA + "/ui" + ChatColor.WHITE + " - Toggle the right-side scoreboard");
                    player.sendMessage(ChatColor.WHITE + "  • " + ChatColor.LIGHT_PURPLE + "🔕 " + ChatColor.AQUA + "/alerts" + ChatColor.WHITE + " - Toggle combat sound & text alerts");
                    player.sendMessage(ChatColor.DARK_GRAY + "=====================================================");
                    player.sendMessage("");
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1f);
                    plugin.getData().set("players." + uuid + ".seen_noesis_reboot_v2", true);
                    plugin.saveData();
                }
            }, 60L);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Location loc = event.getClickedBlock().getLocation();

            // Detect interaction with the configured Altar of Triumph block.
            if (plugin.isAltarLocation(loc)) {
                event.setCancelled(true);

                // ถ้าระบบแท่นเปิดอยู่
                if (plugin.altarOpen) {
                    CraftingEffectManager.playInteractEffect(loc, event.getPlayer());
                    plugin.altarGUI.openGUI(event.getPlayer());
                } else {
                    // ถ้าแท่นปิด แจ้งเวลาคูลดาวน์ให้ผู้เล่นรู้
                    long now = System.currentTimeMillis();
                    if (plugin.altarCooldownTime == Long.MAX_VALUE) {
                        event.getPlayer().sendMessage(plugin.PREFIX + ChatColor.RED + "The Altar of Triumph is currently inactive.");
                    } else {
                        long left = plugin.altarCooldownTime - now;
                        long h = left / 3600000;
                        long m = (left % 3600000) / 60000;
                        event.getPlayer().sendMessage(plugin.PREFIX + ChatColor.RED + "The Altar is dormant. Next alignment in: " + ChatColor.YELLOW + h + "h " + m + "m");
                    }
                    event.getPlayer().playSound(loc, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1f, 1f);
                }
                return;
            }
        }

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null) return;

        Player player = event.getPlayer();

        if (item.getType() == Material.GOLDEN_CARROT) {
            ItemStack heldItem = event.getHand() == EquipmentSlot.OFF_HAND
                    ? player.getInventory().getItemInOffHand()
                    : player.getInventory().getItemInMainHand();
            if (configureGoldenCarrotFood(heldItem, GOLDEN_CARROT_NUTRITION,
                    GOLDEN_CARROT_SATURATION)) {
                if (event.getHand() == EquipmentSlot.OFF_HAND) {
                    player.getInventory().setItemInOffHand(heldItem);
                } else {
                    player.getInventory().setItemInMainHand(heldItem);
                }
                player.updateInventory();
            }
            if (player.getFoodLevel() >= 20) {
                long useToken = System.currentTimeMillis() + 3000L;
                fullHungerGoldenCarrotUseUntil.put(player.getUniqueId(), useToken);
                EquipmentSlot hand = event.getHand();
                Bukkit.getScheduler().runTaskLater(plugin,
                        () -> completeGoldenCarrotFallback(player, hand, useToken), 34L);
            } else {
                fullHungerGoldenCarrotUseUntil.remove(player.getUniqueId());
            }
            event.setUseItemInHand(org.bukkit.event.Event.Result.ALLOW);
            return;
        }

        if (item.getType() == Material.ENCHANTED_GOLDEN_APPLE) {
            event.setCancelled(true);
            item.setAmount(item.getAmount() - 1);
            plugin.giveRewardSmart(player, "triumph", 1);
            player.sendMessage(plugin.PREFIX + ChatColor.GREEN + "Converted Enchanted Golden Apple into 1x Triumph Star!");
            player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1f);
            return;
        }

        if (item.getType() == Material.TOTEM_OF_UNDYING) {
            event.setCancelled(true);
            String uuidPath = "players." + player.getUniqueId() + ".claimed_totem_star";
            if (plugin.getData().getBoolean(uuidPath, false)) {
                player.sendMessage(plugin.PREFIX + ChatColor.RED + "You have already claimed your ONE-TIME Totem Star!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
            plugin.getData().set(uuidPath, true);
            plugin.saveData();

            item.setAmount(item.getAmount() - 1);
            plugin.giveRewardSmart(player, "triumph", 1);
            player.sendMessage(plugin.PREFIX + ChatColor.GREEN + "Converted Totem into your ONE-TIME Triumph Star!");
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            return;
        }

        if (item.getType() != Material.NETHER_STAR || !item.hasItemMeta()) return;

        String starType = null;
        if (item.getItemMeta().getPersistentDataContainer().has(plugin.starTypeKey, PersistentDataType.STRING)) {
            starType = item.getItemMeta().getPersistentDataContainer().get(plugin.starTypeKey, PersistentDataType.STRING);
        } else {
            String dName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
            if (dName != null) {
                if (dName.contains("Triumph Star")) starType = "triumph";
                else if (dName.contains("Soul Star")) starType = "soul";
            }
        }

        if (starType == null) return;
        event.setCancelled(true);

        org.bukkit.attribute.AttributeInstance maxHealthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        int hearts = (int) ((maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0) / 2.0);
        Inventory gui;

        if (starType.equals("triumph")) {
            gui = Bukkit.createInventory(null, 27, "Consume Triumph Star");
            int overflow = plugin.getData().getInt("players." + player.getUniqueId() + ".overflow", 0);
            gui.setItem(11, createIcon(Material.RED_DYE, "&c&l+1 Max Heart", "&7Current Hearts: " + hearts + "/20", "", "&e▶ Click to consume 1"));
            gui.setItem(15, createIcon(Material.GOLD_NUGGET, "&e&l+1 Overflow (+0.8% Crit)", "&7Current Overflow: " + overflow, "", "&e▶ Click to consume 1"));
            gui.setItem(20, createIcon(Material.HEART_OF_THE_SEA, "&c&l[✦] Claim ALL for Hearts", "&7Consumes Triumph Stars", "&7until &c20 Hearts&7 max.", "", "&e▶ Click to consume all"));
            gui.setItem(24, createIcon(Material.HOPPER, "&e&l[✦] Claim ALL for Overflow", "&7Consumes ALL Triumph Stars", "&7in your inventory for Overflow.", "", "&e▶ Click to claim all"));
        } else {
            gui = Bukkit.createInventory(null, 27, "Consume Soul Star");
            int kills = plugin.getData().getInt("players." + player.getUniqueId() + ".kills", 0);
            gui.setItem(11, createIcon(Material.RED_DYE, "&c&l+1 Max Heart", "&7Current Hearts: " + hearts + "/20", "", "&e▶ Click to consume 1"));
            gui.setItem(15, createIcon(Material.IRON_SWORD, "&c&l+1 Kill Stack (+0.8% Crit)", "&7Current Kills: " + kills, "", "&e▶ Click to consume 1"));
            gui.setItem(20, createIcon(Material.HEART_OF_THE_SEA, "&c&l[✦] Claim ALL for Hearts", "&7Consumes Soul Stars", "&7until &c20 Hearts&7 max.", "", "&e▶ Click to consume all"));
            gui.setItem(24, createIcon(Material.HOPPER, "&c&l[✦] Claim ALL for Kill Stacks", "&7Consumes ALL Soul Stars", "&7in your inventory for Kill Stacks.", "", "&e▶ Click to claim all"));
        }
        player.openInventory(gui);
    }

    @EventHandler
    public void onGUIClick(InventoryClickEvent event) {
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (title == null || (!title.equals("Consume Triumph Star") && !title.equals("Consume Soul Star"))) return;

        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        String starType = title.equals("Consume Triumph Star") ? "triumph" : "soul";

        if (slot == 11 || slot == 15) {
            if (!consumeStar(player, starType)) {
                player.sendMessage(plugin.PREFIX + ChatColor.RED + "Star not found in your inventory!");
                player.closeInventory(); return;
            }

            if (slot == 11) {
                org.bukkit.attribute.AttributeInstance maxHealthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                double currentHearts = maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0;
                if (currentHearts >= 40.0) {
                    player.sendMessage(plugin.PREFIX + ChatColor.RED + "Your hearts are already maxed out (20)! Pick Crit instead.");
                    giveStar(player, starType); player.closeInventory(); return;
                }
                if (maxHealthAttr != null) maxHealthAttr.setBaseValue(currentHearts + 2.0);
                player.sendMessage(plugin.PREFIX + ChatColor.GREEN + "Consumed star for " + ChatColor.RED + "+1 Max Heart" + ChatColor.GREEN + "!");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            } else {
                if (starType.equals("triumph")) {
                    int over = plugin.getData().getInt("players." + player.getUniqueId() + ".overflow", 0) + 1;
                    plugin.getData().set("players." + player.getUniqueId() + ".overflow", over);
                    player.sendMessage(plugin.PREFIX + ChatColor.GREEN + "Consumed star for " + ChatColor.YELLOW + "+1 Overflow (+0.8% Crit)" + ChatColor.GREEN + "!");
                } else {
                    int kills = plugin.getData().getInt("players." + player.getUniqueId() + ".kills", 0) + 1;
                    plugin.getData().set("players." + player.getUniqueId() + ".kills", kills);
                    player.sendMessage(plugin.PREFIX + ChatColor.GREEN + "Consumed soul for " + ChatColor.DARK_RED + "+1 Kill Stack (+0.8% Crit)" + ChatColor.GREEN + "!");
                }
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            }

            plugin.saveData();
            player.closeInventory();
        } else if (slot == 20) {
            // Claim ALL for Hearts
            org.bukkit.attribute.AttributeInstance maxHealthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            double currentHearts = maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0;
            int heartsNeeded = (int) Math.max(0, (40.0 - currentHearts) / 2.0);
            if (heartsNeeded <= 0) {
                player.sendMessage(plugin.PREFIX + ChatColor.RED + "Your hearts are already maxed out (20)!");
                player.closeInventory();
                return;
            }
            int totalInInv = countStars(player, starType);
            int toConsume = Math.min(totalInInv, heartsNeeded);
            if (toConsume <= 0) {
                player.sendMessage(plugin.PREFIX + ChatColor.RED + "No " + starType + " stars in your inventory!");
                player.closeInventory();
                return;
            }
            int consumed = consumeAllStars(player, starType, toConsume);
            if (maxHealthAttr != null) maxHealthAttr.setBaseValue(currentHearts + (consumed * 2.0));
            player.sendMessage(plugin.PREFIX + ChatColor.GREEN + "Consumed " + consumed + " stars for " + ChatColor.RED + "+" + consumed + " Max Hearts" + ChatColor.GREEN + "!");
            plugin.saveData();
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            player.closeInventory();
        } else if (slot == 24) {
            // Claim ALL for Overflow / Kill Stacks
            int totalInInv = countStars(player, starType);
            if (totalInInv <= 0) {
                player.sendMessage(plugin.PREFIX + ChatColor.RED + "No " + starType + " stars in your inventory!");
                player.closeInventory();
                return;
            }
            int consumed = consumeAllStars(player, starType, totalInInv);
            if (starType.equals("triumph")) {
                int over = plugin.getData().getInt("players." + player.getUniqueId() + ".overflow", 0) + consumed;
                plugin.getData().set("players." + player.getUniqueId() + ".overflow", over);
                player.sendMessage(plugin.PREFIX + ChatColor.GREEN + "Consumed " + consumed + " Triumph Stars for " + ChatColor.YELLOW + "+" + consumed + " Overflow" + ChatColor.GREEN + "!");
            } else {
                int kills = plugin.getData().getInt("players." + player.getUniqueId() + ".kills", 0) + consumed;
                plugin.getData().set("players." + player.getUniqueId() + ".kills", kills);
                player.sendMessage(plugin.PREFIX + ChatColor.GREEN + "Consumed " + consumed + " Soul Stars for " + ChatColor.DARK_RED + "+" + consumed + " Kill Stacks" + ChatColor.GREEN + "!");
            }
            plugin.saveData();
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            player.closeInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGoldenCarrotConsume(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() != Material.GOLDEN_CARROT) return;

        UUID playerId = event.getPlayer().getUniqueId();
        boolean startedAtFullHunger = fullHungerGoldenCarrotUseUntil
                .getOrDefault(playerId, 0L) >= System.currentTimeMillis();
        fullHungerGoldenCarrotUseUntil.remove(playerId);
        float saturationBefore = event.getPlayer().getSaturation();

        ItemStack consumed = event.getItem().clone();
        if (startedAtFullHunger) {
            configureGoldenCarrotFood(consumed, 0, GOLDEN_APPLE_SATURATION);
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = event.getPlayer();
                if (!player.isOnline()) return;
                player.setSaturation(Math.min(player.getFoodLevel(),
                        saturationBefore + GOLDEN_APPLE_SATURATION));
            });
        } else {
            configureGoldenCarrotFood(consumed, GOLDEN_CARROT_NUTRITION,
                    GOLDEN_CARROT_SATURATION);
        }
        event.setItem(consumed);
    }

    private boolean prepareGoldenCarrots(Player player) {
        boolean changed = false;
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item != null && item.getType() == Material.GOLDEN_CARROT) {
                if (configureGoldenCarrotFood(item, GOLDEN_CARROT_NUTRITION,
                        GOLDEN_CARROT_SATURATION)) {
                    player.getInventory().setItem(slot, item);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private void completeGoldenCarrotFallback(Player player, EquipmentSlot hand, long useToken) {
        UUID playerId = player.getUniqueId();
        if (!player.isOnline()
                || fullHungerGoldenCarrotUseUntil.getOrDefault(playerId, 0L) != useToken) return;

        fullHungerGoldenCarrotUseUntil.remove(playerId);
        ItemStack heldItem = hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        if (heldItem.getType() != Material.GOLDEN_CARROT || heldItem.getAmount() <= 0) return;

        heldItem.setAmount(heldItem.getAmount() - 1);
        player.setSaturation(Math.min(player.getFoodLevel(),
                player.getSaturation() + GOLDEN_APPLE_SATURATION));
        player.getWorld().spawnParticle(org.bukkit.Particle.ITEM,
                player.getEyeLocation().add(player.getLocation().getDirection().multiply(0.35)),
                8, 0.12, 0.12, 0.12, 0.04, new ItemStack(Material.GOLDEN_CARROT));
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.8f, 1.0f);
        player.updateInventory();
    }

    private boolean configureGoldenCarrotFood(ItemStack item, int nutrition, float saturation) {
        if (item == null || item.getType() != Material.GOLDEN_CARROT) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        FoodComponent food = meta.getFood();
        if (food.canAlwaysEat() && food.getNutrition() == nutrition
                && Math.abs(food.getSaturation() - saturation) < 0.001f) {
            return false;
        }
        food.setNutrition(nutrition);
        food.setSaturation(saturation);
        food.setCanAlwaysEat(true);
        meta.setFood(food);
        item.setItemMeta(meta);
        return true;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (title != null && (title.equals("Consume Triumph Star") || title.equals("Consume Soul Star"))) event.setCancelled(true);
    }

    private int countStars(Player player, String type) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.NETHER_STAR && item.hasItemMeta()) {
                String checkType = item.getItemMeta().getPersistentDataContainer().get(plugin.starTypeKey, PersistentDataType.STRING);
                if (checkType == null) {
                    String dName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
                    if (dName != null) checkType = dName.contains("Triumph Star") ? "triumph" : (dName.contains("Soul Star") ? "soul" : null);
                }
                if (type.equals(checkType)) {
                    count += item.getAmount();
                }
            }
        }
        return count;
    }

    private int consumeAllStars(Player player, String type, int maxToConsume) {
        int remaining = maxToConsume;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == Material.NETHER_STAR && item.hasItemMeta()) {
                String checkType = item.getItemMeta().getPersistentDataContainer().get(plugin.starTypeKey, PersistentDataType.STRING);
                if (checkType == null) {
                    String dName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
                    if (dName != null) checkType = dName.contains("Triumph Star") ? "triumph" : (dName.contains("Soul Star") ? "soul" : null);
                }
                if (type.equals(checkType)) {
                    if (item.getAmount() <= remaining) {
                        remaining -= item.getAmount();
                        player.getInventory().setItem(i, null);
                    } else {
                        item.setAmount(item.getAmount() - remaining);
                        remaining = 0;
                    }
                    if (remaining <= 0) break;
                }
            }
        }
        return maxToConsume - remaining;
    }

    private boolean consumeStar(Player player, String type) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.NETHER_STAR && item.hasItemMeta()) {
                String checkType = item.getItemMeta().getPersistentDataContainer().get(plugin.starTypeKey, PersistentDataType.STRING);
                if (checkType == null) {
                    String dName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
                    if (dName != null) checkType = dName.contains("Triumph Star") ? "triumph" : (dName.contains("Soul Star") ? "soul" : null);
                }
                if (type.equals(checkType)) { item.setAmount(item.getAmount() - 1); return true; }
            }
        }
        return false;
    }

    private void giveStar(Player player, String type) {
        ItemStack star = plugin.createStar(type);
        java.util.HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(star);
        for (ItemStack item : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
    }

    private ItemStack createIcon(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat); ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        meta.setLore(Arrays.asList(lore).stream().map(l -> ChatColor.translateAlternateColorCodes('&', l)).toList());
        item.setItemMeta(meta); return item;
    }
}
