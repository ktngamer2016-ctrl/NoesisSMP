package com.yourname.noesissmp;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import java.util.*;

public class EventManager implements Listener {

    private final NoesisSMP plugin;
    public enum EventState { INACTIVE, RECRUITING, COUNTDOWN, TEAM_SELECT, ACTIVE }
    private EventState currentState = EventState.INACTIVE;

    public enum EventCategory { PVP, PVE }
    private EventCategory currentCategory;

    public enum EventType { BOSS_FIGHT, TEAM_FIGHT }
    private EventType currentEventType;

    private final Set<UUID> joinedPlayers = new HashSet<>();
    private final Set<UUID> readyPlayers = new HashSet<>();

    private final Map<UUID, Integer> playerTeams = new HashMap<>();
    private final List<UUID> bosses = new ArrayList<>();

    private UUID pveBossUUID = null;
    private int pvePhase = 1;

    private boolean isTransitioning = false;
    private boolean isChargingSlash = false;
    private boolean isDomainSlash = false;

    private boolean hasExploded75 = false;
    private boolean hasExploded50 = false;
    private boolean hasExploded25 = false;

    private boolean hasSlash75 = false;
    private boolean hasSlash50 = false;
    private boolean hasSlash25 = false;

    private final Map<UUID, Double> originalMaxHealthMap = new HashMap<>();

    private BossBar recruitingBar;
    private BossBar activeEventBar;
    private final Map<UUID, BossBar> statusBarMap = new HashMap<>();

    private int recruitTimeLeft = 7200;
    private int countdownTimeLeft = 15;
    private int teamSelectTimeLeft = 120;
    private int activeTimeLeft = 2700;

    private int recruitingTaskId = -1;
    private int countdownTaskId = -1;
    private int teamSelectTaskId = -1;
    private int activeTaskId = -1;

    public EventManager(NoesisSMP plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public boolean isEventActive() { return currentState == EventState.ACTIVE; }

    public boolean isEventBoss(UUID entityId) {
        return pveBossUUID != null && pveBossUUID.equals(entityId);
    }

    public boolean isPvpBoss(UUID uuid) {
        return currentState == EventState.ACTIVE && currentCategory == EventCategory.PVP && currentEventType == EventType.BOSS_FIGHT && bosses.contains(uuid);
    }

    public void startRecruiting(EventCategory category) {
        if (currentState != EventState.INACTIVE) return;
        currentState = EventState.RECRUITING;
        currentCategory = category;

        joinedPlayers.clear(); readyPlayers.clear(); playerTeams.clear(); bosses.clear();
        recruitTimeLeft = 7200;

        String title = category == EventCategory.PVP ? ChatColor.RED + "⚔ PVP Event Recruiting! /event" : ChatColor.AQUA + "☠ PVE Boss Raid Recruiting! /event";
        recruitingBar = Bukkit.createBossBar(title, category == EventCategory.PVP ? BarColor.RED : BarColor.BLUE, BarStyle.SOLID);

        for (Player p : Bukkit.getOnlinePlayers()) {
            recruitingBar.addPlayer(p);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1f);
            sendInviteLink(p);
        }

        recruitingTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (recruitTimeLeft <= 0) { cancelEvent("Lobby Time Expired. Not enough players."); return; }
            recruitTimeLeft--;

            if (currentState == EventState.RECRUITING) {
                recruitingBar.setProgress((double) recruitTimeLeft / 7200.0);
                int h = recruitTimeLeft / 3600; int m = (recruitTimeLeft % 3600) / 60;
                String baseTitle = currentCategory == EventCategory.PVP ? ChatColor.RED + "⚔ PVP Event" : ChatColor.AQUA + "☠ PVE Raid";
                recruitingBar.setTitle(baseTitle + ChatColor.WHITE + " - Ready: " + ChatColor.GREEN + readyPlayers.size() + "/" + joinedPlayers.size() + ChatColor.WHITE + " (" + h + "h " + m + "m)");
            }
        }, 0L, 20L);
    }

    private void sendInviteLink(Player p) {
        String typeName = currentCategory == EventCategory.PVP ? ChatColor.RED + "PVP TOURNAMENT" : ChatColor.AQUA + "PVE BOSS RAID";
        p.sendMessage(ChatColor.DARK_GRAY + "=========================================");
        p.sendMessage(ChatColor.GOLD + " " + ChatColor.BOLD + "A " + typeName + ChatColor.GOLD + ChatColor.BOLD + " HAS BEEN ANNOUNCED!");
        TextComponent joinBtn = new TextComponent(ChatColor.GREEN + " " + ChatColor.BOLD + "[CLICK HERE TO OPEN GUI] ");
        joinBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/event"));
        p.spigot().sendMessage(joinBtn);
        p.sendMessage(ChatColor.DARK_GRAY + "=========================================");
    }

    private ItemStack createGuiItem(Material mat, String name, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        meta.setLore(Collections.singletonList(ChatColor.translateAlternateColorCodes('&', lore)));
        item.setItemMeta(meta);
        return item;
    }

    public void openEventGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, ChatColor.DARK_AQUA + "🏆 Event Control Panel");

        if (!joinedPlayers.contains(player.getUniqueId())) {
            gui.setItem(11, createGuiItem(Material.EMERALD_BLOCK, "&a&lJoin Event", "&7Click to participate!"));
            gui.setItem(13, createGuiItem(Material.BARRIER, "&c&lNot Joined", "&7You must join first."));
        } else {
            gui.setItem(11, createGuiItem(Material.REDSTONE_BLOCK, "&c&lLeave Event", "&7Click to leave."));
            if (readyPlayers.contains(player.getUniqueId())) {
                gui.setItem(13, createGuiItem(Material.DIAMOND, "&b&lReady!", "&7Click to unready."));
            } else {
                gui.setItem(13, createGuiItem(Material.IRON_INGOT, "&7&lNot Ready", "&7Click to set Ready status."));
            }
        }

        if (currentState == EventState.TEAM_SELECT) {
            gui.setItem(15, createGuiItem(Material.SHIELD, "&6&lSelect Team", "&7Click to choose your team."));
        } else {
            gui.setItem(15, createGuiItem(Material.GRAY_DYE, "&8&lSelect Team", "&7Team selection is not active right now."));
        }

        player.openInventory(gui);
    }

    public void openTeamGUI(Player player) {
        if (currentState != EventState.TEAM_SELECT) {
            player.sendMessage(ChatColor.RED + "Team selection is not active right now!");
            return;
        }
        Inventory gui = Bukkit.createInventory(null, 27, ChatColor.GOLD + "⚔ Select Your Team");
        gui.setItem(10, createGuiItem(Material.RED_BANNER, "&c&lTeam 1", "&7Click to join Team 1"));
        gui.setItem(12, createGuiItem(Material.BLUE_BANNER, "&9&lTeam 2", "&7Click to join Team 2"));
        gui.setItem(14, createGuiItem(Material.GREEN_BANNER, "&a&lTeam 3", "&7Click to join Team 3"));
        gui.setItem(16, createGuiItem(Material.YELLOW_BANNER, "&e&lTeam 4", "&7Click to join Team 4"));
        gui.setItem(26, createGuiItem(Material.ARROW, "&cBack", "&7Return to Main Menu"));
        player.openInventory(gui);
    }

    @EventHandler
    public void onEventGuiClick(InventoryClickEvent event) {
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (title == null) return;

        if (title.equals("🏆 Event Control Panel")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
            Player player = (Player) event.getWhoClicked();
            Material type = event.getCurrentItem().getType();

            if (type == Material.EMERALD_BLOCK) { handleCommand(player, new String[]{"join"}); openEventGUI(player); }
            else if (type == Material.REDSTONE_BLOCK) { handleCommand(player, new String[]{"leave"}); openEventGUI(player); }
            else if (type == Material.IRON_INGOT) { handleCommand(player, new String[]{"ready"}); openEventGUI(player); }
            else if (type == Material.DIAMOND) { handleCommand(player, new String[]{"unready"}); openEventGUI(player); }
            else if (type == Material.SHIELD) { openTeamGUI(player); }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);

        } else if (title.equals("⚔ Select Your Team")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
            Player player = (Player) event.getWhoClicked();
            Material type = event.getCurrentItem().getType();

            if (type == Material.RED_BANNER) { handleCommand(player, new String[]{"team", "1"}); player.closeInventory(); }
            else if (type == Material.BLUE_BANNER) { handleCommand(player, new String[]{"team", "2"}); player.closeInventory(); }
            else if (type == Material.GREEN_BANNER) { handleCommand(player, new String[]{"team", "3"}); player.closeInventory(); }
            else if (type == Material.YELLOW_BANNER) { handleCommand(player, new String[]{"team", "4"}); player.closeInventory(); }
            else if (type == Material.ARROW) { openEventGUI(player); }
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        if (joinedPlayers.contains(p.getUniqueId())) {
            cancelForPlayer(p);
            checkAllReady();
        }
    }

    public void handleCommand(Player player, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
            openEventGUI(player);
            return;
        }

        String arg = args[0].toLowerCase();
        UUID uuid = player.getUniqueId();

        if (arg.equals("threshold") && player.hasPermission("noesis.admin")) {
            if (args.length < 2) { player.sendMessage(plugin.PREFIX + ChatColor.RED + "Usage: /event threshold <amount>"); return; }
            try {
                int amount = Integer.parseInt(args[1]);
                plugin.getConfig().set("settings.pve_phase2_threshold", amount);
                plugin.saveConfig();
                player.sendMessage(plugin.PREFIX + ChatColor.GREEN + "PVE Phase 2 threshold set to " + amount + " players.");
            } catch (NumberFormatException ex) {
                player.sendMessage(plugin.PREFIX + ChatColor.RED + "Invalid number.");
            }
            return;
        }

        if (arg.equals("tab") || arg.equals("list")) {
            player.sendMessage(ChatColor.GOLD + "=====[ Event Participants (" + joinedPlayers.size() + ") ]=====");
            if (joinedPlayers.isEmpty()) player.sendMessage(ChatColor.GRAY + "No one has joined yet.");
            else {
                for (UUID id : joinedPlayers) {
                    Player p = Bukkit.getPlayer(id);
                    String name = (p != null) ? p.getName() : "Unknown";
                    String status = readyPlayers.contains(id) ? ChatColor.GREEN + "✔ READY" : ChatColor.RED + "❌ NOT READY";
                    player.sendMessage(ChatColor.WHITE + "- " + name + " " + status);
                }
            }
            player.sendMessage(ChatColor.GOLD + "===================================");
            return;
        }

        if (arg.equals("join")) {
            if (!joinedPlayers.contains(uuid)) {
                joinedPlayers.add(uuid);
                if (recruitingBar != null) recruitingBar.removePlayer(player);
                updatePlayerStatusBossBar(player, false);
                player.sendMessage(ChatColor.GREEN + "Joined Event! Click Ready in the GUI or type /event ready");
                checkAllReady();
            }
        } else if (arg.equals("ready")) {
            if (!joinedPlayers.contains(uuid)) { player.sendMessage(ChatColor.RED + "You must join first!"); return; }
            if (!readyPlayers.contains(uuid)) {
                readyPlayers.add(uuid);
                updatePlayerStatusBossBar(player, true);
                checkAllReady();
            }
        } else if (arg.equals("unready")) {
            if (!joinedPlayers.contains(uuid)) { player.sendMessage(ChatColor.RED + "You must join first!"); return; }
            if (readyPlayers.contains(uuid)) {
                readyPlayers.remove(uuid);
                updatePlayerStatusBossBar(player, false);
                player.sendMessage(ChatColor.YELLOW + "You are no longer ready.");
                checkAllReady();
            }
        } else if (arg.equals("team")) {
            if (currentState != EventState.TEAM_SELECT) { player.sendMessage(ChatColor.RED + "Team selection is not active right now!"); return; }
            if (args.length < 2) { openTeamGUI(player); return; }
            try {
                int teamNum = Integer.parseInt(args[1]);
                playerTeams.put(uuid, teamNum);
                player.sendMessage(ChatColor.GREEN + "You selected Team " + teamNum + "!");
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid team number.");
            }
        } else if (arg.equals("decline") || arg.equals("leave")) {
            if (joinedPlayers.contains(uuid)) {
                cancelForPlayer(player);
                checkAllReady();
            }
        }
    }

    private void updatePlayerStatusBossBar(Player p, boolean isReady) {
        BossBar bar = statusBarMap.computeIfAbsent(p.getUniqueId(), k -> Bukkit.createBossBar("", BarColor.RED, BarStyle.SOLID));
        bar.addPlayer(p);
        if (isReady) { bar.setTitle(ChatColor.GREEN + "✔ READY (Waiting for others...)"); bar.setColor(BarColor.GREEN); }
        else { bar.setTitle(ChatColor.RED + "❌ NOT READY (Use /event)"); bar.setColor(BarColor.RED); }
    }

    private void checkAllReady() {
        if (joinedPlayers.isEmpty()) {
            if (currentState == EventState.COUNTDOWN) cancelCountdown();
            return;
        }

        if (readyPlayers.size() == joinedPlayers.size()) {
            if (currentState == EventState.RECRUITING) {
                startCountdown();
            }
        } else {
            if (currentState == EventState.COUNTDOWN) {
                cancelCountdown();
            }
        }
    }

    private void startCountdown() {
        currentState = EventState.COUNTDOWN;
        countdownTimeLeft = 15;

        for (UUID id : joinedPlayers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                p.sendMessage(plugin.PREFIX + ChatColor.GREEN + "All players ready! Event starts in 15 seconds...");
            }
        }

        countdownTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (countdownTimeLeft <= 0) {
                Bukkit.getScheduler().cancelTask(countdownTaskId);
                Bukkit.getScheduler().cancelTask(recruitingTaskId);

                if (currentCategory == EventCategory.PVP) {
                    currentEventType = new Random().nextBoolean() ? EventType.BOSS_FIGHT : EventType.TEAM_FIGHT;
                    if (currentEventType == EventType.TEAM_FIGHT) {
                        startTeamSelection();
                        return;
                    }
                }
                startEventGame();
                return;
            }

            recruitingBar.setProgress((double) countdownTimeLeft / 15.0);
            recruitingBar.setTitle(ChatColor.YELLOW + "Event Starting in " + ChatColor.RED + countdownTimeLeft + "s" + ChatColor.YELLOW + " [All Ready!]");

            if (countdownTimeLeft <= 5) {
                for (UUID id : joinedPlayers) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 2f);
                }
            }
            countdownTimeLeft--;
        }, 0L, 20L);
    }

    private void cancelCountdown() {
        currentState = EventState.RECRUITING;
        if (countdownTaskId != -1) {
            Bukkit.getScheduler().cancelTask(countdownTaskId);
            countdownTaskId = -1;

            for (UUID id : joinedPlayers) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    p.sendMessage(plugin.PREFIX + ChatColor.RED + "Countdown stopped! Waiting for all players to be ready.");
                    p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_PLACE, 1f, 1f);
                }
            }
        }
    }

    private void startTeamSelection() {
        currentState = EventState.TEAM_SELECT;
        teamSelectTimeLeft = 120;
        Bukkit.broadcastMessage(plugin.PREFIX + ChatColor.GOLD + ChatColor.BOLD + "TEAM FIGHT MODE SELECTED!");
        Bukkit.broadcastMessage(plugin.PREFIX + ChatColor.YELLOW + "You have 2 minutes to choose your team. Open the GUI with " + ChatColor.GREEN + "/event");

        teamSelectTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (teamSelectTimeLeft <= 0) {
                Bukkit.getScheduler().cancelTask(teamSelectTaskId);
                startEventGame();
                return;
            }
            for (UUID id : joinedPlayers) {
                if (statusBarMap.containsKey(id)) {
                    statusBarMap.get(id).setTitle(ChatColor.AQUA + "Choose Team (/event) - " + teamSelectTimeLeft + "s");
                    statusBarMap.get(id).setProgress((double) teamSelectTimeLeft / 120.0);
                }
            }
            teamSelectTimeLeft--;
        }, 0L, 20L);
    }

    private void setPlayerGlowingColor(Player p, String teamId, ChatColor color) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team t = board.getTeam("evt_" + teamId);
        if (t == null) { t = board.registerNewTeam("evt_" + teamId); t.setColor(color); }
        t.addEntry(p.getName());
        p.setGlowing(true);
    }

    private Location getRandomLocation(Location center, int radius) {
        Random rnd = new Random();
        int offsetX = rnd.nextInt(radius * 2) - radius;
        int offsetZ = rnd.nextInt(radius * 2) - radius;
        int targetX = center.getBlockX() + offsetX;
        int targetZ = center.getBlockZ() + offsetZ;
        int targetY = center.getWorld().getHighestBlockYAt(targetX, targetZ);
        return new Location(center.getWorld(), targetX + 0.5, targetY + 1.0, targetZ + 0.5);
    }

    private void startEventGame() {
        currentState = EventState.ACTIVE;
        originalMaxHealthMap.clear();

        Location baseSpawn = plugin.getEventSpawnLocation();

        Map<Integer, Location> teamSpawnLocations = new HashMap<>();
        if (currentCategory == EventCategory.PVP && currentEventType == EventType.TEAM_FIGHT) {
            for (int i = 1; i <= 4; i++) {
                teamSpawnLocations.put(i, getRandomLocation(baseSpawn, 50));
            }
        }

        if (currentCategory == EventCategory.PVP) {
            activeTimeLeft = 1800;

            if (currentEventType == EventType.BOSS_FIGHT) {
                List<UUID> list = new ArrayList<>(joinedPlayers);
                Collections.shuffle(list);
                int numBosses = Math.max(1, joinedPlayers.size() / 4);
                for (int i = 0; i < numBosses; i++) bosses.add(list.get(i));
                activeEventBar = Bukkit.createBossBar(ChatColor.RED + "⚔ EVENT: BOSS FIGHT ⚔", BarColor.RED, BarStyle.SEGMENTED_10);
            } else {
                List<UUID> noTeam = new ArrayList<>();
                for (UUID id : joinedPlayers) if (!playerTeams.containsKey(id)) noTeam.add(id);
                int assignTeam = 1;
                for (UUID id : noTeam) {
                    playerTeams.put(id, assignTeam);
                    assignTeam = (assignTeam % 4) + 1;
                }
                activeEventBar = Bukkit.createBossBar(ChatColor.RED + "⚔ EVENT: TEAM FIGHT ⚔", BarColor.RED, BarStyle.SEGMENTED_10);
            }

            for (UUID id : joinedPlayers) {
                Player p = Bukkit.getPlayer(id);
                if (p == null) continue;

                if (p.getOpenInventory().getTitle().contains("Event") || p.getOpenInventory().getTitle().contains("Team")) {
                    p.closeInventory();
                }

                Location tpLoc;
                if (currentEventType == EventType.TEAM_FIGHT) {
                    int teamNum = playerTeams.getOrDefault(id, 1);
                    tpLoc = teamSpawnLocations.get(teamNum);
                } else {
                    tpLoc = getRandomLocation(baseSpawn, 50);
                }
                p.teleport(tpLoc);

                activeEventBar.addPlayer(p);
                if (statusBarMap.containsKey(id)) {
                    statusBarMap.get(id).removeAll();
                    statusBarMap.get(id).setVisible(false);
                    statusBarMap.remove(id);
                }
                originalMaxHealthMap.put(id, p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue());

                if (currentEventType == EventType.BOSS_FIGHT) {
                    if (bosses.contains(id)) {
                        int hunters = joinedPlayers.size() - bosses.size();
                        double extraHp = hunters * 20.0;
                        p.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0 + extraHp); p.setHealth(20.0 + extraHp);

                        p.sendTitle(ChatColor.DARK_RED + "YOU ARE THE BOSS!", "Defeat the Hunters", 10, 100, 20);
                        p.sendMessage(plugin.PREFIX + ChatColor.RED + "Boss Buff: " + ChatColor.YELLOW + "+70% Critical Hit Chance!");
                        setPlayerGlowingColor(p, "boss", ChatColor.DARK_RED);
                    } else {
                        p.sendTitle(ChatColor.AQUA + "HUNTER", "Defeat the Boss(es)", 10, 100, 20);
                        setPlayerGlowingColor(p, "hunter", ChatColor.AQUA);
                    }
                } else {
                    int t = playerTeams.getOrDefault(id, 1);
                    ChatColor c = switch(t) { case 1 -> ChatColor.RED; case 2 -> ChatColor.BLUE; case 3 -> ChatColor.GREEN; default -> ChatColor.YELLOW; };
                    p.sendTitle(c + "TEAM " + t, "Defeat other teams!", 10, 100, 20);
                    setPlayerGlowingColor(p, "team" + t, c);
                }
            }
        } else {
            activeTimeLeft = 1800; pvePhase = 1;
            activeEventBar = Bukkit.createBossBar(ChatColor.DARK_RED + "☠ Zacrozz (Phase 1) ☠", BarColor.BLUE, BarStyle.SEGMENTED_10);

            Location bossLoc = getRandomLocation(baseSpawn, 50);
            WitherSkeleton boss = baseSpawn.getWorld().spawn(bossLoc, WitherSkeleton.class);
            boss.setCustomName(ChatColor.DARK_RED + "" + ChatColor.BOLD + "☠ Zacrozz, The Shadow of God ☠");
            boss.setCustomNameVisible(true);

            double calculatedHp = 200.0 + (joinedPlayers.size() * 50.0);
            double finalHp = Math.min(1024.0, calculatedHp);
            boss.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(finalHp);
            boss.setHealth(finalHp);

            boss.getEquipment().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
            boss.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
            boss.getEquipment().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
            boss.getEquipment().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
            boss.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));

            boss.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999, 1));
            boss.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 999999, 0));

            pveBossUUID = boss.getUniqueId();

            for (UUID id : joinedPlayers) {
                Player p = Bukkit.getPlayer(id);
                if (p == null) continue;

                if (p.getOpenInventory().getTitle().contains("Event")) p.closeInventory();

                p.teleport(getRandomLocation(baseSpawn, 50));
                activeEventBar.addPlayer(p);

                if (statusBarMap.containsKey(id)) {
                    statusBarMap.get(id).removeAll();
                    statusBarMap.get(id).setVisible(false);
                    statusBarMap.remove(id);
                }
                originalMaxHealthMap.put(id, p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue());
                p.sendTitle(ChatColor.DARK_RED + "BOSS RAID", ChatColor.WHITE + "Phase 1 Started!", 10, 100, 20);
            }
        }

        activeTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (activeTimeLeft <= 0) {
                for (UUID id : joinedPlayers) { Player p = Bukkit.getPlayer(id); if (p != null) giveReward(p, 1); }
                endEvent("Time Out! Event Finished.");
                return;
            }
            activeTimeLeft--;

            if (currentCategory == EventCategory.PVP) {
                int m = activeTimeLeft / 60; int s = activeTimeLeft % 60;
                activeEventBar.setTitle(ChatColor.RED + "⚔ " + ChatColor.BOLD + currentEventType.name().replace("_", " ") + ChatColor.WHITE + " - Time Left: " + ChatColor.YELLOW + m + ":" + String.format("%02d", s));
                activeEventBar.setProgress((double) activeTimeLeft / 1800.0);
            } else {
                Entity bossEntity = Bukkit.getEntity(pveBossUUID);
                if (bossEntity instanceof LivingEntity boss && !boss.isDead()) {
                    double pct = boss.getHealth() / boss.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
                    activeEventBar.setProgress(Math.max(0, Math.min(1, pct)));

                    int displayHp = (int) Math.max(1, Math.round(boss.getHealth()));
                    activeEventBar.setTitle(ChatColor.DARK_RED + "☠ " + (pvePhase==1 ? "Zacrozz (Phase 1)" : "Zacrozz (Phase 2)") + " ☠ " + ChatColor.WHITE + "- " + ChatColor.RED + displayHp + " HP");

                    int tick = 1800 - activeTimeLeft;

                    if (pvePhase == 1 && !isTransitioning && !isChargingSlash && !isDomainSlash) {
                        if (pct <= 0.75 && !hasSlash75) { hasSlash75 = true; triggerSlashAttack(boss, 3); }
                        else if (pct <= 0.50 && !hasSlash50) { hasSlash50 = true; triggerDomainSlash(boss); }
                        else if (pct <= 0.25 && !hasSlash25) { hasSlash25 = true; triggerSlashAttack(boss, 3); }

                        if (tick % 600 == 0) {
                            triggerSlashAttack(boss, 5);
                        }

                        Player target = getRandomAlivePlayer();
                        if (target != null && boss instanceof WitherSkeleton ws) {
                            ws.setTarget(target);
                        }
                    }
                    else if (pvePhase == 2) {
                        if (pct <= 0.75 && !hasExploded75) { hasExploded75 = true; triggerExplosion(boss); }
                        if (pct <= 0.50 && !hasExploded50) { hasExploded50 = true; triggerExplosion(boss); triggerStationaryPhase(boss, 8); }
                        if (pct <= 0.25 && !hasExploded25) { hasExploded25 = true; triggerExplosion(boss); }

                        if (tick % 200 == 0) {
                            for (Entity n : boss.getNearbyEntities(15, 15, 15)) {
                                if (n instanceof Player p && joinedPlayers.contains(p.getUniqueId())) {
                                    shootWitherSkull(boss, p.getLocation());
                                }
                            }
                        }

                        if (tick % 80 == 0 && !boss.isInvulnerable()) {
                            int randAtk = new Random().nextInt(3);
                            Player target = getRandomAlivePlayer();
                            if (target != null) {
                                if (randAtk == 0) {
                                    for(int i=0; i<5; i++) shootWitherSkullSpread(boss, target.getLocation());
                                    boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1f, 1f);
                                } else if (randAtk == 1) {
                                    for(int i=0; i<10; i++) {
                                        Location randTarget = boss.getLocation().add(Math.random()*20-10, Math.random()*10-5, Math.random()*20-10);
                                        shootWitherSkull(boss, randTarget);
                                    }
                                    boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1f, 0.5f);
                                } else {
                                    boss.teleport(target.getLocation().add(0, 3, 0));
                                    Vector dashDir = target.getLocation().toVector().subtract(boss.getLocation().toVector()).normalize();
                                    boss.setVelocity(dashDir.multiply(2.5));
                                    boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 0.5f);
                                }
                            }
                        }
                    }
                }
            }
        }, 0L, 20L);
    }

    private void triggerSlashAttack(LivingEntity boss, int chargeSec) {
        if (isChargingSlash || isDomainSlash || isTransitioning) return;
        isChargingSlash = true;
        boss.setAI(false);

        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 1f);
        boss.getWorld().spawnParticle(Particle.CRIT, boss.getLocation().add(0, 1, 0), 100, 2, 2, 2, 0.5);

        for (UUID id : joinedPlayers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendMessage(plugin.PREFIX + ChatColor.RED + "⚠ Zacrozz is charging a devastating Slash Attack! Raise your shields!");
        }

        new BukkitRunnable() {
            int timer = chargeSec;
            @Override
            public void run() {
                if (boss.isDead() || isTransitioning) { isChargingSlash = false; this.cancel(); return; }
                if (timer > 0) {
                    boss.getWorld().spawnParticle(Particle.SWEEP_ATTACK, boss.getLocation().add(0, 1, 0), 10);
                    timer--;
                } else {
                    boss.setAI(true);
                    isChargingSlash = false;
                    performSlashExecution(boss);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void performSlashExecution(LivingEntity boss) {
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 2f, 0.5f);
        for (Entity n : boss.getNearbyEntities(15, 15, 15)) {
            if (n instanceof Player p && joinedPlayers.contains(p.getUniqueId())) {
                if (p.isBlocking()) {
                    ItemStack shield = p.getInventory().getItemInMainHand();
                    if (shield.getType() != Material.SHIELD) shield = p.getInventory().getItemInOffHand();
                    if (shield.getType() == Material.SHIELD) shield.setAmount(0);
                    p.playSound(p.getLocation(), Sound.ITEM_SHIELD_BREAK, 1f, 1f);
                    p.sendMessage(plugin.PREFIX + ChatColor.YELLOW + "Your shield absorbed the Slash and broke!");
                } else {
                    p.damage(10.0, boss);
                    p.sendMessage(plugin.PREFIX + ChatColor.DARK_RED + "You were struck by Zacrozz's Slash!");
                }
            }
        }
    }

    private void triggerDomainSlash(LivingEntity boss) {
        if (isDomainSlash || isTransitioning) return;
        isDomainSlash = true;
        boss.setAI(false);

        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2f, 0.5f);
        for (UUID id : joinedPlayers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendTitle(ChatColor.DARK_RED + "DOMAIN SLASH", ChatColor.RED + "Prepare for the ultimate strike!", 10, 60, 10);
        }

        new BukkitRunnable() {
            int timer = 5;
            @Override
            public void run() {
                if (boss.isDead() || isTransitioning) { isDomainSlash = false; this.cancel(); return; }
                if (timer > 0) {
                    boss.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, boss.getLocation().add(0, 1, 0), 30, 2, 2, 2, 0.1);
                    timer--;
                } else {
                    boss.setAI(true);
                    isDomainSlash = false;

                    boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1f);
                    for (Entity n : boss.getNearbyEntities(20, 20, 20)) {
                        if (n instanceof Player p && joinedPlayers.contains(p.getUniqueId())) {
                            if (p.isBlocking()) {
                                ItemStack shield = p.getInventory().getItemInMainHand();
                                if (shield.getType() != Material.SHIELD) shield = p.getInventory().getItemInOffHand();
                                if (shield.getType() == Material.SHIELD) shield.setAmount(0);
                                p.playSound(p.getLocation(), Sound.ITEM_SHIELD_BREAK, 1f, 1f);
                            }
                            Vector dir = p.getLocation().toVector().subtract(boss.getLocation().toVector()).normalize();
                            p.setVelocity(dir.multiply(2.0).setY(0.5));
                            p.damage(14.0, boss);
                        }
                    }
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void triggerExplosion(LivingEntity boss) {
        boss.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, boss.getLocation(), 3);
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 3f, 0.5f);
        for (Entity n : boss.getNearbyEntities(6, 6, 6)) {
            if (n instanceof Player p && joinedPlayers.contains(p.getUniqueId())) {
                Vector dir = p.getLocation().toVector().subtract(boss.getLocation().toVector());
                if (dir.lengthSquared() > 0.001) p.setVelocity(dir.normalize().multiply(1.5));
                p.damage(10.0, boss);
            }
        }
    }

    private void triggerStationaryPhase(LivingEntity boss, int minionCount) {
        boss.setInvulnerable(true);
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1f, 1f);
        boss.getWorld().spawnParticle(Particle.SCULK_SOUL, boss.getLocation(), 50, 1, 1, 1, 0.1);

        for (int i = 0; i < minionCount; i++) {
            Location spawn = boss.getLocation().add(Math.random()*6-3, 0, Math.random()*6-3);
            WitherSkeleton minion = (WitherSkeleton) boss.getWorld().spawnEntity(spawn, EntityType.WITHER_SKELETON);
            minion.setCustomName(ChatColor.DARK_GRAY + "Servant of Chaos");
            minion.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(50.0);
            minion.setHealth(50.0);
            minion.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999, 1));
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (boss.isValid() && !boss.isDead()) {
                boss.setInvulnerable(false);
                boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 1f, 1f);
            }
        }, 200L);
    }

    private void shootWitherSkull(LivingEntity boss, Location targetLoc) {
        WitherSkull skull = (WitherSkull) boss.getWorld().spawnEntity(boss.getLocation().add(0, 1.5, 0), EntityType.WITHER_SKULL);
        Vector dir = targetLoc.toVector().subtract(skull.getLocation().toVector()).normalize();
        skull.setDirection(dir);
        skull.setShooter(boss);
    }

    private void shootWitherSkullSpread(LivingEntity boss, Location targetLoc) {
        WitherSkull skull = (WitherSkull) boss.getWorld().spawnEntity(boss.getLocation().add(0, 1.5, 0), EntityType.WITHER_SKULL);
        Vector dir = targetLoc.toVector().subtract(skull.getLocation().toVector()).normalize();
        dir.add(new Vector(Math.random()*0.2-0.1, Math.random()*0.2-0.1, Math.random()*0.2-0.1));
        skull.setDirection(dir);
        skull.setShooter(boss);
    }

    private Player getRandomAlivePlayer() {
        List<Player> alive = new ArrayList<>();
        for (UUID id : joinedPlayers) { Player p = Bukkit.getPlayer(id); if (p != null && !p.isDead()) alive.add(p); }
        if (alive.isEmpty()) return null;
        return alive.get(new Random().nextInt(alive.size()));
    }

    public void giveReward(Player p, int multiplier) {
        for (int i = 0; i < multiplier; i++) {
            int roll = new Random().nextInt(100);
            ItemStack reward;

            if (roll < 50) reward = plugin.createStar("triumph");
            else if (roll < 75) reward = new ItemStack(Material.NETHERITE_INGOT, 1);
            else if (roll < 95) reward = new ItemStack(Material.SHULKER_SHELL, 1);
            else reward = new ItemStack(Material.NETHERITE_BLOCK, 1);

            HashMap<Integer, ItemStack> leftover = p.getInventory().addItem(reward);
            for (ItemStack left : leftover.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), left);
            }
        }
        if (multiplier >= 3) {
            p.sendMessage(ChatColor.GREEN + "🎉 You WON the event! Received 3x Rewards!");
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        } else {
            p.sendMessage(ChatColor.AQUA + "🎁 Event Participation Reward received!");
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        }
    }

    private void distributeBossDrop(List<UUID> winners) {
        if (winners == null || winners.isEmpty()) return;

        UUID luckyWinner = winners.get(new Random().nextInt(winners.size()));
        Player p = Bukkit.getPlayer(luckyWinner);

        if (p != null) {
            ItemStack bossDrop = plugin.createBossDrop();
            HashMap<Integer, ItemStack> leftover = p.getInventory().addItem(bossDrop);
            for (ItemStack left : leftover.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), left);
            }
            p.sendMessage(plugin.PREFIX + ChatColor.DARK_PURPLE + ChatColor.BOLD + "You received the ultra-rare Zacrozz's Fragment!");
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1f, 1f);
        }
    }

    @EventHandler
    public void onBossDamage(EntityDamageEvent e) {
        if (!isEventActive() || currentCategory != EventCategory.PVE || pveBossUUID == null) return;
        if (!e.getEntity().getUniqueId().equals(pveBossUUID)) return;

        LivingEntity boss = (LivingEntity) e.getEntity();

        if (isTransitioning) {
            e.setCancelled(true);
            return;
        }

        if (pvePhase == 1) {
            // 🔴 ตั้งค่าขั้นต่ำในการเข้าเฟส 2 เหลือแค่ 4 คนเป็น Default
            int threshold = plugin.getConfig().getInt("settings.pve_phase2_threshold", 4);

            if (e.getFinalDamage() >= boss.getHealth()) {
                e.setCancelled(true);
                boss.setHealth(1.0);

                if (joinedPlayers.size() >= threshold) {
                    startPhaseTransition(boss);
                } else {
                    List<UUID> winners = new ArrayList<>();
                    for (UUID id : joinedPlayers) {
                        Player p = Bukkit.getPlayer(id);
                        if (p != null) { giveReward(p, 3); winners.add(id); }
                    }
                    distributeBossDrop(winners);
                    boss.remove();
                    endEvent(ChatColor.GREEN + "ZACROZZ HAS BEEN DEFEATED! RAID SUCCESSFUL!");
                }
            }
        } else if (pvePhase == 2) {
            if (e.getFinalDamage() >= boss.getHealth()) {
                e.setCancelled(true);

                List<UUID> winners = new ArrayList<>();
                for (UUID id : joinedPlayers) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null) { giveReward(p, 3); winners.add(id); }
                }
                distributeBossDrop(winners);

                boss.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, boss.getLocation(), 5);
                boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WITHER_DEATH, 1f, 1f);

                boss.remove();
                endEvent(ChatColor.GREEN + "ZACROZZ HAS BEEN DEFEATED! RAID SUCCESSFUL!");
            }
        }
    }

    private void startPhaseTransition(LivingEntity phase1Boss) {
        isTransitioning = true;
        phase1Boss.setAI(false);
        phase1Boss.setGravity(false);
        phase1Boss.setInvulnerable(true);

        Bukkit.broadcastMessage(plugin.PREFIX + ChatColor.DARK_RED + ChatColor.BOLD + "[Zacrozz] " + ChatColor.RED + "She cannot protect you from my will. This hatred is for your children, Aotoa.");

        for (UUID id : joinedPlayers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.5f);
        }

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks < 60) {
                    phase1Boss.teleport(phase1Boss.getLocation().add(0, 0.1, 0));
                    phase1Boss.getWorld().spawnParticle(Particle.PORTAL, phase1Boss.getLocation().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 1);
                    phase1Boss.getWorld().spawnParticle(Particle.LARGE_SMOKE, phase1Boss.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
                    ticks++;
                } else {
                    Location loc = phase1Boss.getLocation();
                    phase1Boss.remove();
                    spawnPhase2(loc);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void spawnPhase2(Location loc) {
        pvePhase = 2;
        isTransitioning = false;
        loc.getWorld().strikeLightningEffect(loc);

        LivingEntity witherBoss = (LivingEntity) loc.getWorld().spawnEntity(loc, EntityType.WITHER);
        witherBoss.setCustomName(ChatColor.DARK_RED + "" + ChatColor.BOLD + "☠ Zacrozz, The Embodiment of Destruction ☠");
        witherBoss.setCustomNameVisible(true);

        // 🔴 ปรับสมการเลือดให้ตกคนละ 200 HP (4 คนจะได้ 800 HP พอดี) และตันที่ 1024
        double calculatedHp = Math.max(800.0, joinedPlayers.size() * 200.0);
        double finalHp = Math.min(1024.0, calculatedHp);

        witherBoss.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(finalHp);
        witherBoss.setHealth(finalHp);

        // ถ้าเลือดเกิน 1024 ให้แถม Resistance
        if (calculatedHp > 1024.0) witherBoss.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 999999, 1));

        witherBoss.setGlowing(true);
        pveBossUUID = witherBoss.getUniqueId();

        Bukkit.broadcastMessage(plugin.PREFIX + ChatColor.DARK_RED + ChatColor.BOLD + "ZACROZZ HAS ENTERED PHASE 2 (THE EMBODIMENT OF DESTRUCTION)!");
        activeEventBar.setTitle(ChatColor.DARK_RED + "☠ Zacrozz (Phase 2) ☠");

        triggerStationaryPhase(witherBoss, 4);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        // Fallback safety
        if (isEventActive() && currentCategory == EventCategory.PVE && pveBossUUID != null && e.getEntity().getUniqueId().equals(pveBossUUID)) {
            e.getDrops().clear(); e.setDroppedExp(0);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (currentState != EventState.ACTIVE) return;
        Player victim = event.getEntity(); UUID id = victim.getUniqueId();

        if (joinedPlayers.contains(id)) {
            joinedPlayers.remove(id);
            readyPlayers.remove(id);
            bosses.remove(id);
            playerTeams.remove(id);

            if (activeEventBar != null) activeEventBar.removePlayer(victim);
            if (statusBarMap.containsKey(id)) {
                statusBarMap.get(id).removePlayer(victim);
                statusBarMap.get(id).setVisible(false);
                statusBarMap.remove(id);
            }
            if (originalMaxHealthMap.containsKey(id)) {
                victim.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(originalMaxHealthMap.get(id));
                originalMaxHealthMap.remove(id);
            }
            victim.setGlowing(false);
            victim.removePotionEffect(PotionEffectType.STRENGTH);

            giveReward(victim, 1);
            victim.sendMessage(plugin.PREFIX + ChatColor.YELLOW + "You have been eliminated from the event!");

            if (currentCategory == EventCategory.PVP) {
                if (currentEventType == EventType.BOSS_FIGHT) {
                    boolean bossAlive = false, hunterAlive = false;
                    for (UUID pId : joinedPlayers) { if (bosses.contains(pId)) bossAlive = true; else hunterAlive = true; }

                    if (!bossAlive) {
                        List<UUID> winners = new ArrayList<>();
                        for (UUID pId : joinedPlayers) {
                            Player p = Bukkit.getPlayer(pId);
                            if (p != null) { giveReward(p, 3); winners.add(pId); }
                        }
                        distributeBossDrop(winners);
                        endEvent(ChatColor.GREEN + "The Bosses are defeated! Hunters Win!");
                    } else if (!hunterAlive) {
                        List<UUID> winners = new ArrayList<>();
                        for (UUID pId : bosses) {
                            Player p = Bukkit.getPlayer(pId);
                            if (p != null && joinedPlayers.contains(pId)) { giveReward(p, 3); winners.add(pId); }
                        }
                        distributeBossDrop(winners);
                        endEvent(ChatColor.DARK_RED + "All Hunters are dead! Bosses Win!");
                    }
                } else if (currentEventType == EventType.TEAM_FIGHT) {
                    Set<Integer> aliveTeams = new HashSet<>();
                    for (UUID pId : joinedPlayers) aliveTeams.add(playerTeams.getOrDefault(pId, 0));

                    if (aliveTeams.size() <= 1) {
                        List<UUID> winners = new ArrayList<>();
                        for (UUID pId : joinedPlayers) {
                            Player p = Bukkit.getPlayer(pId);
                            if (p != null) { giveReward(p, 3); winners.add(pId); }
                        }
                        distributeBossDrop(winners);
                        endEvent(ChatColor.GOLD + "The Team Fight is over! " + (aliveTeams.isEmpty() ? "No one" : "Team " + aliveTeams.iterator().next()) + " Wins!");
                    }
                }
            } else {
                if (joinedPlayers.isEmpty()) endEvent(ChatColor.DARK_RED + "All players wiped out! Zacrozz wins!");
            }
        }
    }

    public void cancelEvent(String reason) {
        cancelManagedTasks();
        cleanup(); Bukkit.broadcastMessage(plugin.PREFIX + ChatColor.AQUA + "Event Finished/Cancelled: " + reason);
    }

    public void shutdown() {
        cancelManagedTasks();
        cleanup();
    }

    private void cancelManagedTasks() {
        if (recruitingTaskId >= 0) Bukkit.getScheduler().cancelTask(recruitingTaskId);
        if (countdownTaskId >= 0) Bukkit.getScheduler().cancelTask(countdownTaskId);
        if (teamSelectTaskId >= 0) Bukkit.getScheduler().cancelTask(teamSelectTaskId);
        if (activeTaskId >= 0) Bukkit.getScheduler().cancelTask(activeTaskId);
        recruitingTaskId = countdownTaskId = teamSelectTaskId = activeTaskId = -1;
    }

    private void endEvent(String msg) { cancelEvent(msg); }

    private void cleanup() {
        if (recruitingBar != null) {
            recruitingBar.removeAll();
            recruitingBar.setVisible(false);
            recruitingBar = null;
        }
        if (activeEventBar != null) {
            activeEventBar.removeAll();
            activeEventBar.setVisible(false);
            activeEventBar = null;
        }
        for (BossBar b : statusBarMap.values()) {
            b.removeAll();
            b.setVisible(false);
        }
        statusBarMap.clear();

        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team t : board.getTeams()) if (t.getName().startsWith("evt_")) t.unregister();

        for (UUID id : joinedPlayers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                if (originalMaxHealthMap.containsKey(id)) p.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(originalMaxHealthMap.get(id));
                p.removePotionEffect(PotionEffectType.STRENGTH); p.setGlowing(false);
            }
        }

        if (pveBossUUID != null) { Entity boss = Bukkit.getEntity(pveBossUUID); if (boss != null && !boss.isDead()) boss.remove(); }
        pveBossUUID = null; currentEventType = null; currentCategory = null; originalMaxHealthMap.clear(); currentState = EventState.INACTIVE;
        joinedPlayers.clear(); readyPlayers.clear(); playerTeams.clear(); bosses.clear();
        isTransitioning = false; isChargingSlash = false; isDomainSlash = false;
        hasExploded75 = false; hasExploded50 = false; hasExploded25 = false;
        hasSlash75 = false; hasSlash50 = false; hasSlash25 = false;
    }

    private void cancelForPlayer(Player p) {
        joinedPlayers.remove(p.getUniqueId()); readyPlayers.remove(p.getUniqueId()); playerTeams.remove(p.getUniqueId()); bosses.remove(p.getUniqueId());
        if (statusBarMap.containsKey(p.getUniqueId())) {
            statusBarMap.get(p.getUniqueId()).removePlayer(p);
            statusBarMap.get(p.getUniqueId()).setVisible(false);
            statusBarMap.remove(p.getUniqueId());
        }
        if (recruitingBar != null) recruitingBar.removePlayer(p);
        if (activeEventBar != null) activeEventBar.removePlayer(p);
        p.setGlowing(false); p.sendMessage(ChatColor.GRAY + "You left the event lobby.");
    }
}
