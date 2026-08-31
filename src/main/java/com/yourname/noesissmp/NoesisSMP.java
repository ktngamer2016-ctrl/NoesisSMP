package com.yourname.noesissmp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public final class NoesisSMP extends JavaPlugin implements TabCompleter {

    public final String PREFIX = ChatColor.DARK_GRAY + "[" + ChatColor.AQUA + "Noesis" + ChatColor.DARK_AQUA + "SMP" + ChatColor.DARK_GRAY + "] " + ChatColor.RESET;
    public NamespacedKey starTypeKey;
    public NamespacedKey trueMaceKey;
    public NamespacedKey bossDropKey;

    private AdminGUI adminGUI;
    public EventManager eventManager;
    public NoesisPlayerGUI playerGUI;
    public AltarGUI altarGUI;
    public ZoneGUI zoneGUI;
    public CombatListener combatListener;

    public boolean altarOpen = false;
    public long altarCloseTime = 0;
    public long altarCooldownTime = Long.MAX_VALUE;
    public final java.util.Set<String> altarUsedPlayers = new java.util.HashSet<>();

    // 🔴 ตัวแปรบาร์บอสสำหรับ Altar
    public BossBar altarBossBar;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        starTypeKey = new NamespacedKey(this, "star_type");
        trueMaceKey = new NamespacedKey(this, "true_mace");
        bossDropKey = new NamespacedKey(this, "boss_drop");

        altarOpen = getConfig().getBoolean("altar.is_open", false);
        altarCloseTime = getConfig().getLong("altar.close_time", 0);
        altarCooldownTime = getConfig().getLong("altar.cooldown_time", Long.MAX_VALUE);

        List<String> loadedUsed = getConfig().getStringList("altar.used_players");
        if (loadedUsed != null) {
            altarUsedPlayers.addAll(loadedUsed);
        }

        adminGUI = new AdminGUI(this);
        this.eventManager = new EventManager(this);
        this.playerGUI = new NoesisPlayerGUI(this);
        this.altarGUI = new AltarGUI(this);
        this.zoneGUI = new ZoneGUI(this);
        this.combatListener = new CombatListener(this);

        getServer().getPluginManager().registerEvents(new NoesisListener(this), this);
        getServer().getPluginManager().registerEvents(combatListener, this);
        getServer().getPluginManager().registerEvents(adminGUI, this);
        getServer().getPluginManager().registerEvents(new NoesisInfoGUI(this), this);
        getServer().getPluginManager().registerEvents(playerGUI, this);
        getServer().getPluginManager().registerEvents(altarGUI, this);
        getServer().getPluginManager().registerEvents(zoneGUI, this);

        new HUDManager(this).runTaskTimer(this, 20L, 40L);

        if (getCommand("noesis") != null) getCommand("noesis").setTabCompleter(this);
        if (getCommand("event") != null) getCommand("event").setTabCompleter(this);
        if (getCommand("zone") != null) getCommand("zone").setTabCompleter(this);

        try { Bukkit.removeRecipe(NamespacedKey.minecraft("mace")); } catch (Exception ignored) {}

        try {
            NamespacedKey customMaceKey = new NamespacedKey(this, "abyssal_mace");
            org.bukkit.inventory.ShapedRecipe maceRecipe = new org.bukkit.inventory.ShapedRecipe(customMaceKey, createTrueMace());
            maceRecipe.shape(" H ", " Z ", " B ");
            maceRecipe.setIngredient('H', Material.HEAVY_CORE);
            maceRecipe.setIngredient('Z', new org.bukkit.inventory.RecipeChoice.ExactChoice(createBossDrop()));
            maceRecipe.setIngredient('B', Material.BREEZE_ROD);
            Bukkit.addRecipe(maceRecipe);
        } catch (Exception ignored) {}

        // 🔴 สร้าง BossBar สำหรับ Altar
        altarBossBar = Bukkit.createBossBar(ChatColor.DARK_RED + "☠ Altar of Triumph ☠", BarColor.RED, BarStyle.SOLID);

        Location altarLoc = new Location(Bukkit.getWorlds().get(0), 0, 80, 0);

        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();

                // 🔴 เช็คหมดเวลาเปิด (เริ่มติดคูลดาวน์ 48 ชม.)
                if (altarOpen && now >= altarCloseTime) {
                    altarOpen = false;
                    altarCooldownTime = now + (48L * 60 * 60 * 1000);
                    getConfig().set("altar.is_open", false);
                    getConfig().set("altar.cooldown_time", altarCooldownTime);
                    saveConfig();

                    Bukkit.broadcastMessage(PREFIX + ChatColor.RED + "☠ The Altar of Triumph has closed and entered a 48-Hour cooldown! ☠");
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.5f, 0.5f);
                        if (ChatColor.stripColor(p.getOpenInventory().getTitle()).equals("☠ Altar of Triumph ☠")) {
                            p.closeInventory();
                            p.sendMessage(PREFIX + ChatColor.RED + "The Altar has closed while you were using it!");
                        }
                    }
                }
                // 🔴 เช็คหมดคูลดาวน์ (เปิดอัตโนมัติ 1 ชั่วโมง & รีเซ็ตโควต้า)
                else if (!altarOpen && now >= altarCooldownTime) {
                    altarOpen = true;
                    altarCloseTime = now + (1L * 60 * 60 * 1000);
                    altarUsedPlayers.clear();
                    getConfig().set("altar.is_open", true);
                    getConfig().set("altar.close_time", altarCloseTime);
                    getConfig().set("altar.used_players", new ArrayList<>());
                    saveConfig();

                    Bukkit.broadcastMessage(PREFIX + ChatColor.GREEN + "✨ The Altar of Triumph is now OPEN at 0, 80, 0 for 1 Hour! ✨");
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                    }
                }

                // 🔴 อัปเดตข้อความและสีของ BossBar
                if (altarOpen) {
                    long left = altarCloseTime - now;
                    long m = left / 60000;
                    long s = (left % 60000) / 1000;
                    altarBossBar.setTitle(ChatColor.LIGHT_PURPLE + "✨ Altar of Triumph is OPEN ✨ " + ChatColor.WHITE + m + "m " + s + "s");
                    altarBossBar.setColor(BarColor.PURPLE);
                    altarBossBar.setProgress(Math.max(0.0, Math.min(1.0, (double) left / (1L * 60 * 60 * 1000))));
                } else {
                    if (altarCooldownTime == Long.MAX_VALUE) {
                        altarBossBar.setTitle(ChatColor.DARK_RED + "☠ Altar of Triumph is DISABLED ☠");
                        altarBossBar.setColor(BarColor.RED);
                        altarBossBar.setProgress(0.0);
                    } else {
                        long left = altarCooldownTime - now;
                        long h = left / 3600000;
                        long m = (left % 3600000) / 60000;
                        long s = (left % 60000) / 1000;
                        altarBossBar.setTitle(ChatColor.RED + "☠ Altar of Triumph on Cooldown ☠ " + ChatColor.WHITE + h + "h " + m + "m " + s + "s");
                        altarBossBar.setColor(BarColor.RED);
                        altarBossBar.setProgress(Math.max(0.0, Math.min(1.0, (double) left / (48L * 60 * 60 * 1000))));
                    }
                }

                // 🔴 เช็คระยะผู้เล่น (100 บล็อก) เพื่อแสดง/ซ่อน BossBar
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getWorld().getName().equals(altarLoc.getWorld().getName()) && p.getLocation().distanceSquared(altarLoc) <= 10000) { // 100 * 100 = 10,000
                        if (!altarBossBar.getPlayers().contains(p)) {
                            altarBossBar.addPlayer(p);
                        }
                    } else {
                        if (altarBossBar.getPlayers().contains(p)) {
                            altarBossBar.removePlayer(p);
                        }
                    }
                }

                if (altarLoc.getBlock().getType() != Material.CRAFTING_TABLE) {
                    altarLoc.getBlock().setType(Material.CRAFTING_TABLE);
                }

                if (altarOpen) {
                    CraftingEffectManager.playAmbientEffect(altarLoc);
                } else {
                    altarLoc.getWorld().spawnParticle(Particle.ASH, altarLoc.clone().add(0.5, 1.2, 0.5), 3, 0.3, 0.3, 0.3, 0.01);
                }
            }
        }.runTaskTimer(this, 0L, 5L);

        getLogger().info("Noesis SMP Enabled (Core, Combat, Altar BossBar & The Zone v1.0.1 Ready)!");
    }

    @Override
    public void onDisable() {
        if (altarBossBar != null) {
            altarBossBar.removeAll();
        }
        if (combatListener != null) {
            for (java.util.UUID id : new java.util.HashSet<>(combatListener.afterimageHidden)) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) combatListener.revealPlayer(p);
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                combatListener.endTheZone(p);
                if (p.getAttribute(Attribute.GENERIC_ATTACK_SPEED) != null) {
                    p.getAttribute(Attribute.GENERIC_ATTACK_SPEED).setBaseValue(4.0);
                }
                if (p.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) {
                    p.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.1);
                }
                p.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);
            }
        }
    }

    public ItemStack createBossDrop() {
        ItemStack item = new ItemStack(Material.ECHO_SHARD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Zacrozz's Fragment");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "A crystal pulsing with the boss's power.",
                ChatColor.YELLOW + "Used to forge the True Mace."
        ));
        meta.getPersistentDataContainer().set(bossDropKey, PersistentDataType.STRING, "zacrozz_fragment");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createTrueMace() {
        ItemStack item = new ItemStack(Material.MACE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_RED + "" + ChatColor.BOLD + "Abyssal Mace");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Forged with Zacrozz's Fragment.",
                ChatColor.RED + "Unleashes devastating crits!"
        ));
        meta.getPersistentDataContainer().set(trueMaceKey, PersistentDataType.STRING, "true");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createStar(String type) {
        ItemStack star = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = star.getItemMeta();
        List<String> lore = new ArrayList<>();
        if (meta != null) {
            if (type.equalsIgnoreCase("triumph")) {
                meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Triumph Star");
                lore.add(ChatColor.GRAY + "Obtained from Advancements & Events");
                lore.add(ChatColor.YELLOW + "Right-click to consume!");
                meta.getPersistentDataContainer().set(starTypeKey, PersistentDataType.STRING, "triumph");
            } else if (type.equalsIgnoreCase("soul")) {
                meta.setDisplayName(ChatColor.DARK_RED + "" + ChatColor.BOLD + "Soul Star");
                lore.add(ChatColor.GRAY + "Harvested from fallen Players");
                lore.add(ChatColor.YELLOW + "Right-click to consume!");
                meta.getPersistentDataContainer().set(starTypeKey, PersistentDataType.STRING, "soul");
            }
            meta.setLore(lore); star.setItemMeta(meta);
        }
        return star;
    }

    private void sendToCloud(Player p, String type, int amount, String alertMsg) {
        String path = "players." + p.getUniqueId() + ".stored_" + type.toLowerCase();
        getConfig().set(path, getConfig().getInt(path, 0) + amount);
        saveConfig();
        if (getConfig().getBoolean("players." + p.getUniqueId() + ".alerts", true)) {
            String starName = (type.equals("triumph") ? ChatColor.GOLD + "Triumph" : ChatColor.DARK_RED + "Soul") + ChatColor.GRAY;
            p.sendMessage(PREFIX + ChatColor.AQUA + alertMsg + " (" + amount + "x " + starName + " Stars)");
        }
    }

    public void giveRewardSmart(Player p, String type, int amount) {
        String mode = getConfig().getString("players." + p.getUniqueId() + ".reward_mode", "auto");

        if (mode.equals("sys")) { sendToCloud(p, type, amount, "Directly saved to Cloud via settings."); return; }

        ItemStack item = createStar(type); item.setAmount(amount);
        String starName = (type.equals("triumph") ? ChatColor.GOLD + "Triumph" : ChatColor.DARK_RED + "Soul") + ChatColor.GRAY;
        boolean alertsEnabled = getConfig().getBoolean("players." + p.getUniqueId() + ".alerts", true);

        if (mode.equals("ec")) {
            HashMap<Integer, ItemStack> leftOverEc = p.getEnderChest().addItem(item);
            if (leftOverEc.isEmpty()) { if (alertsEnabled) p.sendMessage(PREFIX + ChatColor.YELLOW + "Received " + amount + "x " + starName + " Star(s) in Ender Chest."); }
            else {
                int remainder = leftOverEc.values().iterator().next().getAmount();
                sendToCloud(p, type, remainder, "EC full! Saved remaining to Cloud.");
            }
            return;
        }

        if (mode.equals("inv")) {
            HashMap<Integer, ItemStack> leftOverInv = p.getInventory().addItem(item);
            if (leftOverInv.isEmpty()) { if (alertsEnabled) p.sendMessage(PREFIX + ChatColor.GREEN + "Received " + amount + "x " + starName + " Star(s) in Inventory."); }
            else {
                int remainder = leftOverInv.values().iterator().next().getAmount();
                sendToCloud(p, type, remainder, "Inventory full! Saved remaining to Cloud.");
            }
            return;
        }

        HashMap<Integer, ItemStack> leftOverInv = p.getInventory().addItem(item);
        if (leftOverInv.isEmpty()) { if (alertsEnabled) p.sendMessage(PREFIX + ChatColor.GREEN + "Received " + amount + "x " + starName + " Star(s) in Inventory."); return; }

        ItemStack forEnder = leftOverInv.values().iterator().next();
        HashMap<Integer, ItemStack> leftOverEc = p.getEnderChest().addItem(forEnder);
        if (leftOverEc.isEmpty()) { if (alertsEnabled) p.sendMessage(PREFIX + ChatColor.YELLOW + "Inventory full! Sent " + forEnder.getAmount() + "x " + starName + " Star(s) to your Ender Chest."); return; }

        int remainder = leftOverEc.values().iterator().next().getAmount();
        sendToCloud(p, type, remainder, "Inv & EC full! Saved remaining to Cloud.");
    }

    public void giveRewardDirect(Player p, String type, int amount, String target) {
        ItemStack item = createStar(type); item.setAmount(amount);
        String path = "players." + p.getUniqueId() + ".stored_" + type.toLowerCase();
        String starName = (type.equals("triumph") ? ChatColor.GOLD + "Triumph" : ChatColor.DARK_RED + "Soul");

        switch (target.toLowerCase()) {
            case "inv":
                HashMap<Integer, ItemStack> left1 = p.getInventory().addItem(item);
                for(ItemStack leftover : left1.values()) p.getWorld().dropItemNaturally(p.getLocation(), leftover);
                p.sendMessage(PREFIX + ChatColor.GREEN + "Admin sent " + amount + "x " + starName + ChatColor.GREEN + " to your Inventory.");
                break;
            case "ec":
                HashMap<Integer, ItemStack> left2 = p.getEnderChest().addItem(item);
                for(ItemStack leftover : left2.values()) p.getWorld().dropItemNaturally(p.getLocation(), leftover);
                p.sendMessage(PREFIX + ChatColor.YELLOW + "Admin sent " + amount + "x " + starName + ChatColor.YELLOW + " to your Ender Chest.");
                break;
            case "sys":
                getConfig().set(path, getConfig().getInt(path, 0) + amount); saveConfig();
                p.sendMessage(PREFIX + ChatColor.AQUA + "Admin sent " + amount + "x " + starName + ChatColor.AQUA + " to your Cloud Storage.");
                break;
            default: giveRewardSmart(p, type, amount); break;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("noesis")) {
            if (args.length == 1) {
                completions.addAll(Arrays.asList("gui", "zone", "claim", "deposit", "mode"));
                if (sender.hasPermission("noesis.admin")) completions.addAll(Arrays.asList("admin", "wipe", "give", "gift", "dropmode", "altar", "maxcrit", "setcrit", "zoneenter", "zoneend", "blackflash", "skipstack", "setstack", "zonetree"));
            }
            else if (args.length == 2 && (args[0].equalsIgnoreCase("maxcrit") || args[0].equalsIgnoreCase("zoneenter") || args[0].equalsIgnoreCase("zoneend") || args[0].equalsIgnoreCase("blackflash")) && sender.hasPermission("noesis.admin")) {
                for (Player p : Bukkit.getOnlinePlayers()) completions.add(p.getName());
            }
            else if (args.length == 2 && args[0].equalsIgnoreCase("zonetree") && sender.hasPermission("noesis.admin")) {
                completions.addAll(Arrays.asList("enable", "disable", "toggle"));
            }
            else if (args.length == 2 && args[0].equalsIgnoreCase("skipstack") && sender.hasPermission("noesis.admin")) {
                completions.addAll(Arrays.asList("yellow", "orange", "red", "black"));
            }
            else if (args.length == 3 && args[0].equalsIgnoreCase("skipstack") && sender.hasPermission("noesis.admin")) {
                for (Player p : Bukkit.getOnlinePlayers()) completions.add(p.getName());
            }
            else if (args.length == 2 && args[0].equalsIgnoreCase("setstack") && sender.hasPermission("noesis.admin")) {
                completions.addAll(Arrays.asList("yellow", "orange", "red"));
            }
            else if (args.length == 3 && args[0].equalsIgnoreCase("setstack") && sender.hasPermission("noesis.admin")) {
                completions.addAll(Arrays.asList("1", "3", "5", "7"));
            }
            else if (args.length == 4 && args[0].equalsIgnoreCase("setstack") && sender.hasPermission("noesis.admin")) {
                for (Player p : Bukkit.getOnlinePlayers()) completions.add(p.getName());
            }
            else if (args.length == 2 && args[0].equalsIgnoreCase("zone") && sender.hasPermission("noesis.admin")) {
                completions.addAll(Arrays.asList("heavy", "light"));
            }
            else if (args.length == 3 && args[0].equalsIgnoreCase("zone") && (args[1].equalsIgnoreCase("heavy") || args[1].equalsIgnoreCase("light")) && sender.hasPermission("noesis.admin")) {
                completions.addAll(Arrays.asList("0", "1", "2", "3"));
            }
            else if (args.length == 2 && args[0].equalsIgnoreCase("altar") && sender.hasPermission("noesis.admin")) {
                completions.addAll(Arrays.asList("start", "stop", "status"));
            }
            else if (args.length == 2 && args[0].equalsIgnoreCase("mode")) {
                completions.addAll(Arrays.asList("auto", "inv", "ec", "sys"));
            }
            else if (args.length == 2 && (args[0].equalsIgnoreCase("maxcrit") || args[0].equalsIgnoreCase("zoneenter")) && sender.hasPermission("noesis.admin")) {
                for (Player p : Bukkit.getOnlinePlayers()) completions.add(p.getName());
            }
            else if (args.length == 2 && args[0].equalsIgnoreCase("setcrit") && sender.hasPermission("noesis.admin")) {
                for (Player p : Bukkit.getOnlinePlayers()) completions.add(p.getName());
            }
            else if (args.length == 3 && args[0].equalsIgnoreCase("setcrit") && sender.hasPermission("noesis.admin")) {
                completions.addAll(Arrays.asList("10", "30", "60", "100"));
            }
            else if (args.length == 2 && args[0].equalsIgnoreCase("give") && sender.hasPermission("noesis.admin")) {
                for (Player p : Bukkit.getOnlinePlayers()) completions.add(p.getName());
            }
            else if (args.length == 3 && args[0].equalsIgnoreCase("give") && sender.hasPermission("noesis.admin")) {
                completions.addAll(Arrays.asList("triumph", "soul"));
            }
            else if (args.length == 4 && args[0].equalsIgnoreCase("give") && sender.hasPermission("noesis.admin")) {
                completions.addAll(Arrays.asList("1", "5", "10", "64"));
            }
            else if (args.length == 5 && args[0].equalsIgnoreCase("give") && sender.hasPermission("noesis.admin")) {
                completions.addAll(Arrays.asList("auto", "inv", "ec", "sys"));
            }
            else if (args.length == 2 && args[0].equalsIgnoreCase("dropmode") && sender.hasPermission("noesis.admin")) {
                completions.addAll(Arrays.asList("true", "false"));
            }
            else if (args.length == 2 && args[0].equalsIgnoreCase("gift") && sender.hasPermission("noesis.admin")) {
                completions.add("all");
                for (Player p : Bukkit.getOnlinePlayers()) completions.add(p.getName());
            }
            else if (args.length == 2 && args[0].equalsIgnoreCase("wipe") && sender.hasPermission("noesis.admin")) {
                for (Player p : Bukkit.getOnlinePlayers()) completions.add(p.getName());
            }
        }
        else if (command.getName().equalsIgnoreCase("event")) {
            if (args.length == 1) {
                completions.addAll(Arrays.asList("join", "ready", "leave", "tab", "team"));
                if (sender.hasPermission("noesis.admin")) completions.addAll(Arrays.asList("pvp", "pve", "stop", "threshold"));
            } else if (args.length == 2 && args[0].equalsIgnoreCase("team")) {
                completions.addAll(Arrays.asList("1", "2", "3", "4"));
            }
        }
        else if (command.getName().equalsIgnoreCase("zone")) {
            if (args.length == 1 && sender.hasPermission("noesis.admin")) {
                completions.addAll(Arrays.asList("heavy", "light"));
            } else if (args.length == 2 && (args[0].equalsIgnoreCase("heavy") || args[0].equalsIgnoreCase("light")) && sender.hasPermission("noesis.admin")) {
                completions.addAll(Arrays.asList("0", "1", "2", "3"));
            }
        }

        List<String> result = new ArrayList<>();
        for (String c : completions) if (c.toLowerCase().startsWith(args[args.length - 1].toLowerCase())) result.add(c);
        return result;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return false;
        String uuid = player.getUniqueId().toString();

        if (command.getName().equalsIgnoreCase("zone")) {
            if (args.length == 0) {
                zoneGUI.openGUI(player);
                return true;
            }

            if (player.hasPermission("noesis.admin") && args.length >= 2) {
                String path = args[0].toLowerCase();
                try {
                    int lvl = Integer.parseInt(args[1]);
                    getConfig().set("players." + uuid + ".zone_path", path);
                    getConfig().set("players." + uuid + ".zone_level", lvl);
                    saveConfig();
                    player.sendMessage(PREFIX + ChatColor.GREEN + "Admin forced your path to " + path.toUpperCase() + " Level " + lvl);
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                } catch (Exception e) {
                    player.sendMessage(PREFIX + ChatColor.RED + "Invalid level.");
                }
                return true;
            }

            zoneGUI.openGUI(player);
            return true;
        }

        if (command.getName().equalsIgnoreCase("noesis")) {
            if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
                playerGUI.openGUI(player); return true;
            }
            String action = args[0].toLowerCase();

            if (action.equals("zone")) {
                if (args.length == 1) {
                    zoneGUI.openGUI(player);
                    return true;
                }

                if (player.hasPermission("noesis.admin") && args.length >= 3) {
                    String path = args[1].toLowerCase();
                    try {
                        int lvl = Integer.parseInt(args[2]);
                        getConfig().set("players." + uuid + ".zone_path", path);
                        getConfig().set("players." + uuid + ".zone_level", lvl);
                        saveConfig();
                        player.sendMessage(PREFIX + ChatColor.GREEN + "Admin forced your path to " + path.toUpperCase() + " Level " + lvl);
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                    } catch (Exception e) {
                        player.sendMessage(PREFIX + ChatColor.RED + "Invalid level.");
                    }
                }
                return true;
            }

            if (action.equals("altar")) {
                if (!player.hasPermission("noesis.admin")) {
                    player.sendMessage(PREFIX + ChatColor.RED + "Unknown command or insufficient permissions.");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(PREFIX + ChatColor.RED + "Usage: /noesis altar <start|stop|status>");
                    return true;
                }

                String sub = args[1].toLowerCase();
                long now = System.currentTimeMillis();

                if (sub.equals("start")) {
                    altarOpen = true;
                    altarCloseTime = now + (1L * 60 * 60 * 1000);
                    altarCooldownTime = altarCloseTime + (48L * 60 * 60 * 1000);
                    altarUsedPlayers.clear();
                    getConfig().set("altar.is_open", true);
                    getConfig().set("altar.close_time", altarCloseTime);
                    getConfig().set("altar.cooldown_time", altarCooldownTime);
                    getConfig().set("altar.used_players", new ArrayList<>());
                    saveConfig();

                    Bukkit.broadcastMessage(PREFIX + ChatColor.GREEN + "✨ An Admin has FORCE OPENED the Altar of Triumph for 1 Hour! Quotas reset! ✨");
                    for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                }
                else if (sub.equals("stop")) {
                    altarOpen = false;
                    altarCooldownTime = Long.MAX_VALUE;
                    getConfig().set("altar.is_open", false);
                    getConfig().set("altar.cooldown_time", altarCooldownTime);
                    saveConfig();

                    Bukkit.broadcastMessage(PREFIX + ChatColor.RED + "☠ An Admin has FORCE CLOSED the Altar of Triumph! ☠");
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.5f, 0.5f);
                        if (ChatColor.stripColor(p.getOpenInventory().getTitle()).equals("☠ Altar of Triumph ☠")) {
                            p.closeInventory();
                        }
                    }
                }
                else if (sub.equals("status")) {
                    if (altarOpen) {
                        long left = altarCloseTime - now;
                        long m = left / 60000;
                        long s = (left % 60000) / 1000;
                        player.sendMessage(PREFIX + ChatColor.GREEN + "Altar is OPEN! Closes in: " + m + "m " + s + "s");
                    } else {
                        if (altarCooldownTime == Long.MAX_VALUE) {
                            player.sendMessage(PREFIX + ChatColor.RED + "Altar is DISABLED (No scheduled opening).");
                        } else {
                            long left = altarCooldownTime - now;
                            long h = left / 3600000;
                            long m = (left % 3600000) / 60000;
                            player.sendMessage(PREFIX + ChatColor.RED + "Altar is CLOSED. Opens in: " + h + "h " + m + "m");
                        }
                    }
                }
                return true;
            }

            if (action.equals("mode")) {
                if (args.length < 2) { player.sendMessage(PREFIX + ChatColor.YELLOW + "Usage: /noesis mode <auto|inv|ec|sys>"); return true; }
                String mode = args[1].toLowerCase();
                if (!Arrays.asList("auto", "inv", "ec", "sys").contains(mode)) { player.sendMessage(PREFIX + ChatColor.RED + "Invalid mode. Use: auto, inv, ec, or sys."); return true; }
                getConfig().set("players." + uuid + ".reward_mode", mode); saveConfig();
                player.sendMessage(PREFIX + ChatColor.GREEN + "Reward preference set to: " + ChatColor.YELLOW + mode.toUpperCase());
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f); return true;
            }

            if (action.equals("deposit")) {
                int tCount = 0; int sCount = 0;
                for (int i = 0; i < player.getInventory().getSize(); i++) {
                    ItemStack item = player.getInventory().getItem(i);
                    if (item != null && item.getType() == Material.NETHER_STAR && item.hasItemMeta()) {
                        String type = item.getItemMeta().getPersistentDataContainer().get(starTypeKey, PersistentDataType.STRING);
                        if (type == null) {
                            String dName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
                            if (dName != null && dName.contains("Triumph Star")) type = "triumph";
                            else if (dName != null && dName.contains("Soul Star")) type = "soul";
                        }
                        if ("triumph".equals(type)) { tCount += item.getAmount(); player.getInventory().setItem(i, null); }
                        else if ("soul".equals(type)) { sCount += item.getAmount(); player.getInventory().setItem(i, null); }
                    }
                }
                if (tCount == 0 && sCount == 0) { player.sendMessage(PREFIX + ChatColor.RED + "No stars found in your inventory to deposit."); return true; }
                if (tCount > 0) getConfig().set("players." + uuid + ".stored_triumph", getConfig().getInt("players." + uuid + ".stored_triumph", 0) + tCount);
                if (sCount > 0) getConfig().set("players." + uuid + ".stored_soul", getConfig().getInt("players." + uuid + ".stored_soul", 0) + sCount);
                saveConfig();
                player.sendMessage(PREFIX + ChatColor.GREEN + "Deposited " + ChatColor.GOLD + tCount + " Triumph" + ChatColor.GREEN + " & " + ChatColor.DARK_RED + sCount + " Soul " + ChatColor.GREEN + "stars to Cloud.");
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_EYE_DEATH, 1f, 1f); return true;
            }

            if (action.equals("claim")) {
                int st = getConfig().getInt("players." + uuid + ".stored_triumph", 0);
                int ss = getConfig().getInt("players." + uuid + ".stored_soul", 0);

                if (st == 0 && ss == 0) { player.sendMessage(PREFIX + ChatColor.RED + "No stars stored in Cloud."); return true; }

                String typeToClaim = st > 0 ? "triumph" : "soul";

                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(createStar(typeToClaim));
                if (!leftover.isEmpty()) {
                    player.getInventory().removeItem(createStar(typeToClaim));
                    player.sendMessage(PREFIX + ChatColor.RED + "Inventory full! Please make some space before claiming.");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return true;
                }

                if (st > 0) getConfig().set("players." + uuid + ".stored_triumph", st - 1);
                else getConfig().set("players." + uuid + ".stored_soul", ss - 1);
                saveConfig();

                player.sendMessage(PREFIX + ChatColor.GREEN + "Claimed 1 star from cloud!");
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
                return true;
            }

            if (!player.hasPermission("noesis.admin")) {
                player.sendMessage(PREFIX + ChatColor.RED + "Unknown command or insufficient permissions.");
                return true;
            }

            if (action.equals("admin")) { adminGUI.openGUI(player); return true; }

            if (action.equals("dropmode")) {
                if (args.length < 2) { player.sendMessage(PREFIX + ChatColor.RED + "Usage: /noesis dropmode <true|false>"); return true; }
                boolean mode = Boolean.parseBoolean(args[1]);
                getConfig().set("settings.drop_at_corpse", mode);
                saveConfig();
                player.sendMessage(PREFIX + ChatColor.GREEN + "Corpse Drop Mode set to: " + mode);
                return true;
            }

            if (action.equals("gift")) {
                if (args.length < 3) { player.sendMessage(PREFIX + ChatColor.RED + "Usage: /noesis gift <all|player> <amount>"); return true; }
                int amount;
                try { amount = Integer.parseInt(args[2]); } catch (NumberFormatException e) { player.sendMessage(PREFIX + ChatColor.RED + "Invalid amount."); return true; }

                if (args[1].equalsIgnoreCase("all")) {
                    for (Player target : Bukkit.getOnlinePlayers()) {
                        eventManager.giveReward(target, amount);
                        target.sendMessage(PREFIX + ChatColor.LIGHT_PURPLE + "You received an Admin Gift!");
                    }
                    player.sendMessage(PREFIX + ChatColor.GREEN + "Gifted everyone " + amount + "x rewards!");
                } else {
                    Player target = Bukkit.getPlayer(args[1]);
                    if (target == null) { player.sendMessage(PREFIX + ChatColor.RED + "Player not found."); return true; }
                    eventManager.giveReward(target, amount);
                    target.sendMessage(PREFIX + ChatColor.LIGHT_PURPLE + "You received an Admin Gift!");
                    player.sendMessage(PREFIX + ChatColor.GREEN + "Gifted " + target.getName() + " " + amount + "x rewards!");
                }
                return true;
            }

            if (action.equals("wipe")) {
                if (args.length < 2) { player.sendMessage(PREFIX + ChatColor.RED + "Usage: /noesis wipe <player>"); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { player.sendMessage(PREFIX + ChatColor.RED + "Player not found."); return true; }
                target.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0); getConfig().set("players." + target.getUniqueId() + ".kills", 0);
                getConfig().set("players." + target.getUniqueId() + ".overflow", 0); saveConfig(); player.sendMessage(PREFIX + ChatColor.GREEN + "Wiped stats for " + target.getName());
                return true;
            }

            if (action.equals("give")) {
                if (args.length < 4) { player.sendMessage(PREFIX + ChatColor.RED + "Usage: /noesis give <player> <triumph|soul> <amount> [inv|ec|sys]"); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { player.sendMessage(PREFIX + ChatColor.RED + "Player not found."); return true; }
                try {
                    int amt = Integer.parseInt(args[3]);
                    String placement = (args.length == 5) ? args[4] : "auto";
                    giveRewardDirect(target, args[2], amt, placement);
                } catch (NumberFormatException e) {
                    player.sendMessage(PREFIX + ChatColor.RED + "Invalid amount.");
                }
                return true;
            }

            if (action.equals("maxcrit")) {
                Player target = (args.length >= 2) ? Bukkit.getPlayer(args[1]) : player;
                if (target == null) { player.sendMessage(PREFIX + ChatColor.RED + "Player not found."); return true; }
                String tUuid = target.getUniqueId().toString();
                getConfig().set("players." + tUuid + ".kills", 100);
                saveConfig();
                target.sendMessage(PREFIX + ChatColor.GOLD + "✨ Your Kill Stack has been MAXED to 100 by an Admin! (60% Crit Chance & VOID Tier Unlocked)");
                target.playSound(target.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                if (target != player) player.sendMessage(PREFIX + ChatColor.GREEN + "Maxed Kill Stack for " + target.getName() + " (100 Kills).");
                return true;
            }

            if (action.equals("setcrit")) {
                if (args.length < 3) { player.sendMessage(PREFIX + ChatColor.RED + "Usage: /noesis setcrit <player> <amount>"); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { player.sendMessage(PREFIX + ChatColor.RED + "Player not found."); return true; }
                try {
                    int amount = Integer.parseInt(args[2]);
                    String tUuid = target.getUniqueId().toString();
                    getConfig().set("players." + tUuid + ".kills", amount);
                    saveConfig();
                    target.sendMessage(PREFIX + ChatColor.GOLD + "✨ Your Kill Stack was set to " + amount + " by an Admin!");
                    target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                    player.sendMessage(PREFIX + ChatColor.GREEN + "Set Kill Stack for " + target.getName() + " to " + amount + ".");
                } catch (NumberFormatException e) {
                    player.sendMessage(PREFIX + ChatColor.RED + "Invalid number.");
                }
                return true;
            }

            if (action.equals("zoneenter")) {
                Player target = (args.length >= 2) ? Bukkit.getPlayer(args[1]) : player;
                if (target == null) { player.sendMessage(PREFIX + ChatColor.RED + "Player not found."); return true; }
                if (combatListener != null) {
                    combatListener.enterTheZone(target);
                    if (target != player) player.sendMessage(PREFIX + ChatColor.GREEN + "Forced THE ZONE on " + target.getName() + ".");
                }
                return true;
            }

            if (action.equals("zoneend")) {
                Player target = (args.length >= 2) ? Bukkit.getPlayer(args[1]) : player;
                if (target == null) { player.sendMessage(PREFIX + ChatColor.RED + "Player not found."); return true; }
                if (combatListener != null) {
                    combatListener.endTheZone(target);
                    if (target != player) player.sendMessage(PREFIX + ChatColor.GREEN + "Ended THE ZONE for " + target.getName() + ".");
                }
                return true;
            }

            if (action.equals("blackflash")) {
                Player target = (args.length >= 2) ? Bukkit.getPlayer(args[1]) : player;
                if (target == null) { player.sendMessage(PREFIX + ChatColor.RED + "Player not found."); return true; }
                if (combatListener != null) {
                    combatListener.playBlackFlashVFX(target.getLocation().add(0, 1, 0));
                    player.sendMessage(PREFIX + ChatColor.GREEN + "Played Black Flash VFX on " + target.getName() + ".");
                }
                return true;
            }

            if (action.equals("skipstack")) {
                if (args.length < 2) { player.sendMessage(PREFIX + ChatColor.RED + "Usage: /noesis skipstack <yellow|orange|red|black> [player]"); return true; }
                String tier = args[1].toLowerCase();
                Player target = (args.length >= 3) ? Bukkit.getPlayer(args[2]) : player;
                if (target == null) { player.sendMessage(PREFIX + ChatColor.RED + "Player not found."); return true; }
                if (combatListener != null) {
                    combatListener.skipToTier(target, tier);
                    target.sendMessage(PREFIX + ChatColor.GOLD + "⚡ Crit Stack skipped directly to: " + ChatColor.YELLOW + tier.toUpperCase() + " tier!");
                    target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
                    if (target != player) player.sendMessage(PREFIX + ChatColor.GREEN + "Skipped crit stack for " + target.getName() + " to " + tier + " tier.");
                }
                return true;
            }

            if (action.equals("setstack")) {
                if (args.length < 3) { player.sendMessage(PREFIX + ChatColor.RED + "Usage: /noesis setstack <yellow|orange|red> <amount> [player]"); return true; }
                String tier = args[1].toLowerCase();
                Player target = (args.length >= 4) ? Bukkit.getPlayer(args[3]) : player;
                if (target == null) { player.sendMessage(PREFIX + ChatColor.RED + "Player not found."); return true; }
                try {
                    int amount = Integer.parseInt(args[2]);
                    if (combatListener != null) {
                        combatListener.setStacks(target, tier, amount);
                        target.sendMessage(PREFIX + ChatColor.GOLD + "⚡ Set " + tier.toUpperCase() + " Stacks to " + amount + "!");
                        target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
                        if (target != player) player.sendMessage(PREFIX + ChatColor.GREEN + "Set " + tier + " stacks for " + target.getName() + " to " + amount + ".");
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage(PREFIX + ChatColor.RED + "Invalid amount number.");
                }
                return true;
            }

            if (action.equals("zonetree")) {
                if (args.length < 2) {
                    boolean enabled = getConfig().getBoolean("settings.zonetree_enabled", true);
                    player.sendMessage(PREFIX + ChatColor.GRAY + "Zone Skill Tree status: " + (enabled ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED") + ChatColor.GRAY + ". Use: /noesis zonetree <enable|disable|toggle>");
                    return true;
                }
                String sub = args[1].toLowerCase();
                if (sub.equals("enable")) {
                    if (zoneGUI != null) zoneGUI.enableZoneTree();
                    player.sendMessage(PREFIX + ChatColor.GREEN + "Zone Skill Tree is now ENABLED!");
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                } else if (sub.equals("disable")) {
                    if (zoneGUI != null) zoneGUI.disableZoneTree();
                    player.sendMessage(PREFIX + ChatColor.RED + "Zone Skill Tree is now DISABLED and all spent stars refunded to Cloud Storage!");
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                } else if (sub.equals("toggle")) {
                    boolean enabled = getConfig().getBoolean("settings.zonetree_enabled", true);
                    if (enabled) {
                        if (zoneGUI != null) zoneGUI.disableZoneTree();
                        player.sendMessage(PREFIX + ChatColor.RED + "Zone Skill Tree is now DISABLED and all spent stars refunded to Cloud Storage!");
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                    } else {
                        if (zoneGUI != null) zoneGUI.enableZoneTree();
                        player.sendMessage(PREFIX + ChatColor.GREEN + "Zone Skill Tree is now ENABLED!");
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                    }
                }
                return true;
            }

            player.sendMessage(PREFIX + ChatColor.RED + "Unknown admin command. Use: admin, wipe, give, gift, dropmode, altar, zone, maxcrit, setcrit, zoneenter, zoneend, blackflash, skipstack, setstack, zonetree.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("event")) {
            if (args.length == 0) {
                eventManager.handleCommand(player, new String[]{"gui"});
                return true;
            }
            if (args[0].equalsIgnoreCase("pvp") && player.hasPermission("noesis.admin")) { eventManager.startRecruiting(EventManager.EventCategory.PVP); return true; }
            if (args[0].equalsIgnoreCase("pve") && player.hasPermission("noesis.admin")) { eventManager.startRecruiting(EventManager.EventCategory.PVE); return true; }
            if ((args[0].equalsIgnoreCase("stop") || args[0].equalsIgnoreCase("cancel")) && player.hasPermission("noesis.admin")) { eventManager.cancelEvent("Cancelled by Admin."); return true; }

            eventManager.handleCommand(player, args);
            return true;
        }

        if (command.getName().equalsIgnoreCase("guide") || command.getName().equalsIgnoreCase("info")) { new NoesisInfoGUI(this).openLanguageMenu(player); return true; }
        if (command.getName().equalsIgnoreCase("ui")) {
            boolean current = getConfig().getBoolean("players." + uuid + ".hud", true); getConfig().set("players." + uuid + ".hud", !current); saveConfig();
            player.sendMessage(PREFIX + ChatColor.GRAY + "HUD " + (!current ? "Enabled." : "Disabled.")); return true;
        }
        if (command.getName().equalsIgnoreCase("alerts")) {
            boolean current = getConfig().getBoolean("players." + uuid + ".alerts", true); getConfig().set("players." + uuid + ".alerts", !current); saveConfig();
            player.sendMessage(PREFIX + ChatColor.GRAY + "Combat & System Alerts " + (!current ? "Enabled." : "Disabled.")); return true;
        }
        if (command.getName().equalsIgnoreCase("status")) {
            int h = (int) (player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue() / 2.0);
            int k = getConfig().getInt("players." + uuid + ".kills", 0); int o = getConfig().getInt("players." + uuid + ".overflow", 0);
            int total = k + o; double crit = Math.min(50.0, total * 0.8);

            player.sendMessage(""); player.sendMessage(ChatColor.DARK_GRAY + "==========[ " + ChatColor.AQUA + ChatColor.BOLD + "NOESIS STATUS " + ChatColor.DARK_GRAY + "]==========");
            player.sendMessage(ChatColor.RED + " ❤ Max Hearts: " + ChatColor.WHITE + h + ChatColor.GRAY + " / 20");
            player.sendMessage(ChatColor.DARK_RED + " ⚔ Kill Stack: " + ChatColor.WHITE + k);
            player.sendMessage(ChatColor.GOLD + " ⭐ Overflow: " + ChatColor.WHITE + o);
            player.sendMessage(ChatColor.LIGHT_PURPLE + " 🔮 Total Points: " + ChatColor.WHITE + total);
            player.sendMessage(ChatColor.AQUA + " 🎯 Base Crit Chance: " + ChatColor.WHITE + String.format(java.util.Locale.US, "%.1f", crit) + "%");
            player.sendMessage(ChatColor.DARK_GRAY + "=====================================");
            return true;
        }

        return true;
    }

    public boolean hasUsedAltarQuota(Player p) {
        if (p == null) return false;
        return altarUsedPlayers.contains(p.getUniqueId().toString());
    }

    public void recordAltarUsage(Player p) {
        if (p == null) return;
        altarUsedPlayers.add(p.getUniqueId().toString());
        getConfig().set("altar.used_players", new ArrayList<>(altarUsedPlayers));
        saveConfig();
    }
}