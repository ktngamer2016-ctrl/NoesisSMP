package com.yourname.noesissmp;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CombatListener implements Listener {

    private final NoesisSMP plugin;
    private final Map<String, Long> killCooldowns = new HashMap<>();
    private final Map<UUID, Long> deathCooldowns = new HashMap<>();
    private final Map<UUID, Integer> pearlCharges = new HashMap<>();
    private final Map<UUID, Integer> pearlTasks = new HashMap<>();
    private final Map<UUID, UUID> combatTagAttacker = new HashMap<>();
    private final Map<UUID, Long> combatTagTimer = new HashMap<>();

    private final Map<UUID, Integer> yellowStacks = new HashMap<>();
    private final Map<UUID, Integer> orangeStacks = new HashMap<>();
    private final Map<UUID, Integer> redStacks = new HashMap<>();
    private final Map<UUID, Long> lastValidHitTime = new HashMap<>();
    private final Map<UUID, Long> zoneEndTime = new HashMap<>();

    private final Map<UUID, Long> shieldRaiseTime = new HashMap<>();
    private final Map<UUID, Long> lastAttackTime = new HashMap<>();
    private final Map<UUID, Long> lastLeftClickTime = new HashMap<>();
    private final Map<UUID, Boolean> lastSwingWasSpam = new HashMap<>();
    private final Map<UUID, Integer> heavyStacks = new HashMap<>();
    private final Map<UUID, Integer> heavyHitCount = new HashMap<>();
    private final Map<UUID, DomainData> activeDomains = new HashMap<>();
    private final Set<UUID> shockwaveDamageLock = new HashSet<>();
    private final Map<UUID, Long> shockwaveCooldown = new HashMap<>();

    private final Map<UUID, Long> lightDodgeCD = new HashMap<>();
    private final Map<UUID, Double> lightDmgTaken = new HashMap<>();
    private final Map<UUID, Integer> lightHits = new HashMap<>();
    private final Map<UUID, Map<UUID, Integer>> lightDebuffs = new HashMap<>();
    private final Map<UUID, Double> origSpeed = new HashMap<>();
    private final Map<UUID, Double> origAtkSpeed = new HashMap<>();
    public final Set<UUID> afterimageHidden = new HashSet<>();

    class DomainData {
        Location center;
        long endTime;
        UUID owner;

        public DomainData(Location c, long t, UUID o) {
            center = c;
            endTime = t;
            owner = o;
        }

        public boolean isInside(Location loc) {
            if (loc == null || loc.getWorld() == null || center == null || center.getWorld() == null) return false;
            if (!loc.getWorld().equals(center.getWorld())) return false;
            double dx = loc.getX() - center.getX();
            double dz = loc.getZ() - center.getZ();
            return (dx * dx + dz * dz) <= (10.2 * 10.2);
        }

        public boolean isEscaping(Location from, Location to) {
            if (from == null || to == null) return false;
            if (from.getWorld() == null || !from.getWorld().equals(center.getWorld())) return false;
            double fromDistSq = (from.getX() - center.getX()) * (from.getX() - center.getX()) + (from.getZ() - center.getZ()) * (from.getZ() - center.getZ());
            double toDistSq = (to.getX() - center.getX()) * (to.getX() - center.getX()) + (to.getZ() - center.getZ()) * (to.getZ() - center.getZ());
            return fromDistSq <= (10.2 * 10.2) && toDistSq > (9.8 * 9.8);
        }
    }

    public CombatListener(NoesisSMP plugin) {
        this.plugin = plugin;
        startCritDecayTask();
    }

    public void revealPlayer(Player p) {
        if (p == null) return;
        if (afterimageHidden.remove(p.getUniqueId())) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other != p && other.isOnline()) other.showPlayer(plugin, p);
            }
        }
    }

    public void updateBaseAttackSpeed(Player p, String t1, int stack) {
        if (p == null) return;
        String t2 = plugin.getConfig().getString("players." + p.getUniqueId() + ".zone.tier2", "none");
        double base = 4.0;
        if (t1.equals("heavy")) {
            base *= 0.90; // -10% Attack speed (3.60)
        } else if (t1.equals("light")) {
            // Heavy Tier 2 completely removes any attack speed buff (e.g. from Light Tier 1)
            if (!t2.equals("heavy")) {
                base *= 1.15; // +15% Attack speed (4.60)
            }
        }

        base = base * (1.0 - (stack * 0.03));
        if (t2.equals("heavy") && base > 4.0) {
            base = 4.0;
        }

        if (p.getAttribute(Attribute.GENERIC_ATTACK_SPEED) != null) {
            p.getAttribute(Attribute.GENERIC_ATTACK_SPEED).setBaseValue(base);
        }
    }

    public long getZoneDurationMs(UUID uuid) {
        String t1 = plugin.getConfig().getString("players." + uuid + ".zone.tier1", "none");
        String t2 = plugin.getConfig().getString("players." + uuid + ".zone.tier2", "none");
        String t3 = plugin.getConfig().getString("players." + uuid + ".zone.tier3", "none");

        int upgrades = 0;
        if (!t1.equals("none")) upgrades++;
        if (!t2.equals("none")) upgrades++;
        if (!t3.equals("none")) upgrades++;

        return switch (upgrades) {
            case 1 -> 90000L;   // 1m 30s
            case 2 -> 120000L;  // 2m
            case 3 -> 150000L;  // 2m 30s
            default -> 60000L;  // No upgrade: 1m
        };
    }

    public void setStacks(Player p, String tier, int amount) {
        UUID uuid = p.getUniqueId();
        lastValidHitTime.put(uuid, System.currentTimeMillis());
        switch (tier.toLowerCase()) {
            case "yellow" -> yellowStacks.put(uuid, amount);
            case "orange" -> orangeStacks.put(uuid, amount);
            case "red" -> redStacks.put(uuid, amount);
        }
    }

    public void skipToTier(Player p, String tier) {
        UUID uuid = p.getUniqueId();
        lastValidHitTime.put(uuid, System.currentTimeMillis());
        switch (tier.toLowerCase()) {
            case "yellow" -> {
                yellowStacks.put(uuid, 7);
                if (plugin.getConfig().getInt("players." + uuid + ".kills", 0) < 30) {
                    plugin.getConfig().set("players." + uuid + ".kills", 30);
                    plugin.saveConfig();
                }
            }
            case "orange" -> {
                yellowStacks.put(uuid, 7);
                orangeStacks.put(uuid, 7);
                if (plugin.getConfig().getInt("players." + uuid + ".kills", 0) < 60) {
                    plugin.getConfig().set("players." + uuid + ".kills", 60);
                    plugin.saveConfig();
                }
            }
            case "red" -> {
                yellowStacks.put(uuid, 7);
                orangeStacks.put(uuid, 7);
                redStacks.put(uuid, 6);
                if (plugin.getConfig().getInt("players." + uuid + ".kills", 0) < 100) {
                    plugin.getConfig().set("players." + uuid + ".kills", 100);
                    plugin.saveConfig();
                }
            }
            case "black", "zone" -> enterTheZone(p);
        }
    }

    public void playBlackFlashVFX(Location center) {
        if (center == null) return;
        org.bukkit.World world = center.getWorld();
        if (world == null) return;

        // 1. Crisp, balanced impact audio
        try {
            world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.7f, 1.2f);
            world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 0.8f);
            world.playSound(center, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.8f, 0.5f);
        } catch (Throwable ignored) {}

        Particle.DustOptions pureBlack = new Particle.DustOptions(org.bukkit.Color.fromRGB(10, 10, 10), 1.8f);
        Particle.DustOptions darkVoid = new Particle.DustOptions(org.bukkit.Color.fromRGB(30, 30, 30), 1.4f);

        // 2. Compact center spark & dark blast
        try {
            world.spawnParticle(Particle.FLASH, center, 1, 0.0, 0.0, 0.0, 0.0);
            world.spawnParticle(Particle.EXPLOSION, center, 1, 0.0, 0.0, 0.0, 0.0);
            world.spawnParticle(Particle.SQUID_INK, center, 12, 0.15, 0.15, 0.15, 0.03);
            world.spawnParticle(Particle.DUST, center, 15, 0.2, 0.2, 0.2, 0.0, pureBlack);
        } catch (Throwable ignored) {}

        // 3. JJK Black Flash: 8 sleek jagged black lightning arcs (~2 blocks reach)
        try {
            java.util.Random rand = new java.util.Random();
            int numTendrils = 8;

            for (int i = 0; i < numTendrils; i++) {
                double theta = rand.nextDouble() * 2 * Math.PI;
                double phi = (rand.nextDouble() - 0.5) * Math.PI;
                Vector dir = new Vector(Math.cos(theta) * Math.cos(phi), Math.sin(phi), Math.sin(theta) * Math.cos(phi)).normalize();

                Location current = center.clone();
                int steps = 7 + rand.nextInt(4); // ~1.8 to 2.5 blocks length
                for (int s = 0; s < steps; s++) {
                    Vector jitter = new Vector(
                            (rand.nextDouble() - 0.5) * 0.3,
                            (rand.nextDouble() - 0.5) * 0.3,
                            (rand.nextDouble() - 0.5) * 0.3
                    );
                    current.add(dir.clone().multiply(0.25).add(jitter));

                    // Sharp, clean black lightning points
                    try {
                        world.spawnParticle(Particle.DUST, current, 1, 0.0, 0.0, 0.0, 0.0, pureBlack);
                        if (rand.nextDouble() < 0.3) {
                            world.spawnParticle(Particle.SQUID_INK, current, 1, 0.0, 0.0, 0.0, 0.01);
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
    }

    public void enterTheZone(Player p) {
        long now = System.currentTimeMillis();
        UUID uuid = p.getUniqueId();
        long durationMs = getZoneDurationMs(uuid);
        zoneEndTime.put(uuid, now + durationMs);
        yellowStacks.put(uuid, 0);
        orangeStacks.put(uuid, 0);
        redStacks.put(uuid, 0);

        String t1 = plugin.getConfig().getString("players." + uuid + ".zone.tier1", "none");
        if (t1.equals("heavy")) p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int)(durationMs / 50L), 0));
        updateBaseAttackSpeed(p, t1, 0);

        heavyStacks.put(uuid, 0);
        heavyHitCount.put(uuid, 0);
        lightDmgTaken.put(uuid, 0.0);
        lightHits.put(uuid, 0);
        clearLightDebuffs(uuid);

        playBlackFlashVFX(p.getLocation().add(0, 1, 0));
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "☠ BLACK CRIT - THE ZONE ☠"));
        p.sendMessage(plugin.PREFIX + ChatColor.DARK_PURPLE + ChatColor.BOLD + "☠ THE ZONE ACTIVATED (" + (durationMs / 1000) + "s) ☠");
    }

    public void endTheZone(Player p) {
        if (p == null) return;
        UUID uuid = p.getUniqueId();
        zoneEndTime.remove(uuid);
        String t1 = plugin.getConfig().getString("players." + uuid + ".zone.tier1", "none");
        updateBaseAttackSpeed(p, t1, 0);
        if (p.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) {
            p.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.1);
        }
        p.removePotionEffect(PotionEffectType.SLOWNESS);
        heavyStacks.remove(uuid);
        heavyHitCount.remove(uuid);
        lightDmgTaken.remove(uuid);
        lightHits.remove(uuid);
        clearLightDebuffs(uuid);
        revealPlayer(p);
        p.sendMessage(plugin.PREFIX + ChatColor.GRAY + "The Zone has ended.");
    }

    private void startCritDecayTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();

                Iterator<Map.Entry<UUID, DomainData>> it = activeDomains.entrySet().iterator();
                while(it.hasNext()) {
                    Map.Entry<UUID, DomainData> entry = it.next();
                    if (now > entry.getValue().endTime) { it.remove(); continue; }
                    Location center = entry.getValue().center;
                    if (center == null || center.getWorld() == null) { it.remove(); continue; }

                    // Render striking domain barrier cylinder wall (10 blocks radius)
                    for (int degree = 0; degree < 360; degree += 12) {
                        double rad = Math.toRadians(degree);
                        double x = center.getX() + (10.0 * Math.cos(rad));
                        double z = center.getZ() + (10.0 * Math.sin(rad));
                        for (double yOffset = 0.0; yOffset <= 3.0; yOffset += 1.0) {
                            center.getWorld().spawnParticle(Particle.DUST, x, center.getY() + yOffset, z, 1, 0.0, 0.0, 0.0, 0.0, new Particle.DustOptions(org.bukkit.Color.RED, 1.2f));
                        }
                    }

                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.getUniqueId().equals(entry.getValue().owner)) continue;
                        if (p.getWorld().equals(center.getWorld())) {
                            double dist = p.getLocation().distance(center);
                            if (dist > 9.5 && dist < 12.0) {
                                if (p.isInsideVehicle()) p.leaveVehicle();
                                Vector push = center.toVector().subtract(p.getLocation().toVector()).normalize().multiply(0.8).setY(0.2);
                                p.setVelocity(push);
                                p.playSound(p.getLocation(), Sound.BLOCK_GLASS_HIT, 0.5f, 1f);
                            }
                        }
                    }
                }

                for (Player p : Bukkit.getOnlinePlayers()) {
                    UUID uuid = p.getUniqueId();
                    boolean inZone = zoneEndTime.containsKey(uuid) && zoneEndTime.get(uuid) > now;
                    boolean inCombat = lastValidHitTime.containsKey(uuid) && (now - lastValidHitTime.get(uuid) <= 15000);

                    if (zoneEndTime.containsKey(uuid) && !inZone) {
                        zoneEndTime.remove(uuid);
                        String t1 = plugin.getConfig().getString("players." + uuid + ".zone.tier1", "none");
                        updateBaseAttackSpeed(p, t1, 0);
                        p.removePotionEffect(PotionEffectType.SLOWNESS);
                        heavyStacks.remove(uuid); heavyHitCount.remove(uuid);
                        lightDmgTaken.remove(uuid); lightHits.remove(uuid);
                        clearLightDebuffs(uuid);

                        if (plugin.getConfig().getBoolean("players." + uuid + ".alerts", true)) {
                            p.sendMessage(plugin.PREFIX + ChatColor.GRAY + "The Zone has faded...");
                        }
                    }

                    if (!inCombat && !inZone) {
                        yellowStacks.remove(uuid); orangeStacks.remove(uuid); redStacks.remove(uuid);
                        lastValidHitTime.remove(uuid);
                        continue;
                    }

                    if (plugin.getConfig().getBoolean("players." + uuid + ".alerts", true)) {
                        if (inZone) {
                            long remainingSec = Math.max(0, (zoneEndTime.get(uuid) - now) / 1000);
                            String extraText = "";
                            String t2 = plugin.getConfig().getString("players." + uuid + ".zone.tier2", "none");
                            if (t2.equals("heavy")) {
                                int s = heavyStacks.getOrDefault(uuid, 0);
                                extraText = ChatColor.RED + " [Combo: " + s + "/5]";
                            }
                            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ChatColor.DARK_GRAY + "【 " + ChatColor.DARK_PURPLE + ChatColor.BOLD + "THE ZONE" + ChatColor.DARK_GRAY + " 】 " + ChatColor.LIGHT_PURPLE + remainingSec + "s" + extraText));
                        } else if (inCombat) {
                            int r = redStacks.getOrDefault(uuid, 0); int o = orangeStacks.getOrDefault(uuid, 0); int y = yellowStacks.getOrDefault(uuid, 0);
                            String text;
                            if (r > 0) text = ChatColor.RED + "【 < " + r + " > 】";
                            else if (o > 0) text = ChatColor.GOLD + "【 < " + o + " > 】";
                            else text = ChatColor.YELLOW + "【 < " + y + " > 】";
                            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(text));
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    private void clearLightDebuffs(UUID attackerId) {
        if (!lightDebuffs.containsKey(attackerId)) return;
        for (Map.Entry<UUID, Integer> entry : lightDebuffs.get(attackerId).entrySet()) {
            LivingEntity t = (LivingEntity) Bukkit.getEntity(entry.getKey());
            if (t != null && origSpeed.containsKey(t.getUniqueId())) {
                if (t.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) t.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(origSpeed.get(t.getUniqueId()));
                if (t.getAttribute(Attribute.GENERIC_ATTACK_SPEED) != null) t.getAttribute(Attribute.GENERIC_ATTACK_SPEED).setBaseValue(origAtkSpeed.get(t.getUniqueId()));
            }
            origSpeed.remove(entry.getKey()); origAtkSpeed.remove(entry.getKey());
        }
        lightDebuffs.remove(attackerId);
    }

    @EventHandler
    public void onArmSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();
        long timeSinceLastClick = now - lastLeftClickTime.getOrDefault(uuid, 0L);
        lastLeftClickTime.put(uuid, now);

        // If clicking faster than 250ms (>= 4 CPS), mark as spam
        lastSwingWasSpam.put(uuid, timeSinceLastClick < 250);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        if (e.getAction().name().contains("RIGHT_CLICK")) {
            if (p.getInventory().getItemInMainHand().getType() == Material.SHIELD || p.getInventory().getItemInOffHand().getType() == Material.SHIELD) {
                if (!p.hasCooldown(Material.SHIELD)) shieldRaiseTime.put(uuid, System.currentTimeMillis());
            }
        } else if (e.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_AIR || e.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) {
            long now = System.currentTimeMillis();
            if (zoneEndTime.containsKey(uuid) && zoneEndTime.get(uuid) > now) {
                String t2 = plugin.getConfig().getString("players." + uuid + ".zone.tier2", "none");
                if (t2.equals("heavy")) {
                    long lastHit = lastAttackTime.getOrDefault(uuid, 0L);
                    if (now - lastHit > 250) {
                        int cur = heavyStacks.getOrDefault(uuid, 0);
                        if (cur > 0) {
                            int newStack = cur - 1;
                            heavyStacks.put(uuid, newStack);
                            String t1 = plugin.getConfig().getString("players." + uuid + ".zone.tier1", "none");
                            updateBaseAttackSpeed(p, t1, newStack);
                            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.4f, 1.5f);
                            lastAttackTime.put(uuid, now);
                        }
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        Player p = null; boolean isRanged = false;

        if (event.getDamager() instanceof Player) p = (Player) event.getDamager();
        else if (event.getDamager() instanceof org.bukkit.entity.AbstractArrow arrow && arrow.getShooter() instanceof Player shooter) { p = shooter; isRanged = true; }

        if (p != null) revealPlayer(p);
        if (event.getEntity() instanceof Player damagedPlayer) revealPlayer(damagedPlayer);

        if (p != null && shockwaveDamageLock.contains(p.getUniqueId())) return;

        long now = System.currentTimeMillis();

        if (event.getEntity() instanceof Player victim) {
            UUID vId = victim.getUniqueId();
            if (p != null) {
                combatTagAttacker.put(vId, p.getUniqueId()); combatTagTimer.put(vId, now + 15000);
                combatTagAttacker.put(p.getUniqueId(), vId); combatTagTimer.put(p.getUniqueId(), now + 15000);
            }

            boolean vInZone = zoneEndTime.containsKey(vId) && zoneEndTime.get(vId) > now;
            String vt1 = plugin.getConfig().getString("players." + vId + ".zone.tier1", "none");
            String vt3 = plugin.getConfig().getString("players." + vId + ".zone.tier3", "none");
            boolean vAlerts = plugin.getConfig().getBoolean("players." + vId + ".alerts", true);

            if (vInZone) {
                // LIGHT U1: Dodge
                if (vt1.equals("light") && p != null && now > lightDodgeCD.getOrDefault(vId, 0L)) {
                    if (Math.random() <= 0.35) {
                        event.setCancelled(true);
                        Location attLoc = p.getLocation();
                        Vector dir = attLoc.getDirection().setY(0).normalize();
                        Location behind = attLoc.clone().subtract(dir.multiply(2));
                        behind.setDirection(attLoc.toVector().subtract(behind.toVector()));
                        victim.teleport(behind);

                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 3));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20, 0));

                        lightDodgeCD.put(vId, now + 10000);
                        victim.getWorld().spawnParticle(Particle.PORTAL, victim.getLocation(), 30);
                        victim.playSound(victim.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.5f);
                        if (vAlerts) victim.sendMessage(plugin.PREFIX + ChatColor.AQUA + "Perfect Dodge Activated!");
                        return;
                    }
                }

                // HEAVY U1: Parry (on any blocked attack with shield)
                if (vt1.equals("heavy") && p != null && victim.isBlocking()) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 1));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));
                    victim.playSound(victim.getLocation(), Sound.BLOCK_ANVIL_PLACE, 1f, 2f);
                    victim.getWorld().spawnParticle(Particle.FLASH, p.getLocation().add(0, 1, 0), 10);
                    if (vAlerts) victim.sendMessage(plugin.PREFIX + ChatColor.RED + "Parry! Attacker Stunned!");
                }

                // LIGHT U3: Break Combo
                if (vt3.equals("light")) {
                    double taken = lightDmgTaken.getOrDefault(vId, 0.0) + event.getFinalDamage();
                    if (taken > 20.0) {
                        lightDmgTaken.put(vId, 0.0);
                        clearLightDebuffs(vId);
                        if (vAlerts) victim.sendMessage(plugin.PREFIX + ChatColor.RED + "You took too much damage! Light Combo broken.");
                    } else {
                        lightDmgTaken.put(vId, taken);
                    }
                }
            }
        }

        if (p == null) return;
        UUID uuid = p.getUniqueId();
        boolean alertsEnabled = plugin.getConfig().getBoolean("players." + uuid + ".alerts", true);

        if (!isRanged) {
            ItemStack weapon = p.getInventory().getItemInMainHand();
            if (weapon.getType() == Material.MACE) {
                if (weapon.getItemMeta() == null || !weapon.getItemMeta().getPersistentDataContainer().has(plugin.trueMaceKey, PersistentDataType.STRING)) {
                    event.setCancelled(true);
                    p.getInventory().setItemInMainHand(new ItemStack(Material.HEAVY_CORE));
                    p.playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 0.5f);
                    p.sendMessage(plugin.PREFIX + ChatColor.DARK_RED + ChatColor.BOLD + "SHATTERED! " + ChatColor.RED + "Your Mace was too weak!");
                    return;
                }
                if (p.hasCooldown(Material.MACE)) { event.setCancelled(true); return; }
                if (p.getFallDistance() > 1.5) { p.setCooldown(Material.MACE, 900); }
            }
        }

        LivingEntity targetEnt = (event.getEntity() instanceof LivingEntity) ? (LivingEntity) event.getEntity() : null;
        Location targetLoc = event.getEntity().getLocation().add(0, 1, 0);
        boolean isValidTarget = (event.getEntity() instanceof LivingEntity) && !(event.getEntity() instanceof org.bukkit.entity.ArmorStand);

        boolean inZone = zoneEndTime.containsKey(uuid) && zoneEndTime.get(uuid) > now;
        String t1 = plugin.getConfig().getString("players." + uuid + ".zone.tier1", "none");
        String t2 = plugin.getConfig().getString("players." + uuid + ".zone.tier2", "none");
        String t3 = plugin.getConfig().getString("players." + uuid + ".zone.tier3", "none");

        boolean isSpam = lastSwingWasSpam.getOrDefault(uuid, false);
        long timeSinceLastHit = now - lastAttackTime.getOrDefault(uuid, 0L);
        boolean isChargedHit = isRanged || (!isSpam && (timeSinceLastHit >= 240 || !lastAttackTime.containsKey(uuid)));
        lastAttackTime.put(uuid, now);
        lastValidHitTime.put(uuid, now);

        boolean isVanillaCrit = false;
        if (!isRanged) { isVanillaCrit = (p.getFallDistance() > 0.0F && !p.isOnGround() && !p.hasPotionEffect(PotionEffectType.BLINDNESS) && p.getVehicle() == null && !p.isSprinting()); }
        else { if (event.getDamager() instanceof org.bukkit.entity.AbstractArrow arrow) isVanillaCrit = arrow.isCritical(); }

        if (inZone) {
            boolean isOrangeCrit = Math.random() < 0.5;
            double mult = isOrangeCrit ? 2.0 : 1.5;

            // Passive Mix
            if (t1.equals("heavy")) mult *= 1.5;
            if (t1.equals("light")) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 40, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1));
            }

            // TIER 2 & TIER 3 Mix
            if (isValidTarget && isChargedHit) {
                int stack = heavyStacks.getOrDefault(uuid, 0);

                long lastShockwave = shockwaveCooldown.getOrDefault(uuid, 0L);
                if (t3.equals("heavy") && stack >= 5 && now >= lastShockwave + 30000L) {
                    // Shockwave Finisher! (Triggered on next hit when at 5 stacks & not on 30s cooldown)
                    shockwaveCooldown.put(uuid, now);
                    heavyStacks.put(uuid, 0);
                    updateBaseAttackSpeed(p, t1, 0);

                    Location loc = p.getLocation();
                    p.getWorld().playSound(loc, Sound.BLOCK_ANVIL_LAND, 1.2f, 0.6f);
                    p.getWorld().playSound(loc, Sound.ITEM_MACE_SMASH_GROUND, 1.2f, 0.8f);

                    // Ground slam shockwave dust ring particles
                    for (double r = 1.0; r <= 6.0; r += 1.0) {
                        for (double theta = 0; theta < 2 * Math.PI; theta += Math.PI / 8) {
                            double x = Math.cos(theta) * r;
                            double z = Math.sin(theta) * r;
                            p.getWorld().spawnParticle(Particle.DUST, loc.clone().add(x, 0.1, z), 1, 0, 0, 0, 0, new Particle.DustOptions(org.bukkit.Color.GRAY, 1.2f));
                        }
                    }

                    shockwaveDamageLock.add(uuid);
                    for (org.bukkit.entity.Entity ent : p.getNearbyEntities(10, 10, 10)) {
                        if (ent instanceof LivingEntity t && ent != p && !(ent instanceof ArmorStand)) {
                            // Brings players down
                            t.setVelocity(new Vector(0, -1.2, 0));
                            // Apply Slowness 4 for 1 second, then Slowness 2 for 5 seconds
                            t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 3, false, true, true));
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    if (t.isValid() && !t.isDead()) {
                                        t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1, false, true, true));
                                    }
                                }
                            }.runTaskLater(plugin, 20L);

                            t.damage(5.0, p);
                        }
                    }
                    shockwaveDamageLock.remove(uuid);

                    activeDomains.put(p.getUniqueId(), new DomainData(loc.clone(), now + 20000L, p.getUniqueId()));
                    p.sendMessage(plugin.PREFIX + ChatColor.DARK_RED + ChatColor.BOLD + "Shockwave Activated!");
                } else if (t2.equals("heavy")) {
                    if (stack < 5) {
                        stack++;
                        heavyStacks.put(uuid, stack);
                        updateBaseAttackSpeed(p, t1, stack);
                    }
                    mult *= (1.0 + (stack * 0.10));
                }
            }

            if (t2.equals("light") && Math.random() <= 0.10) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20, 3));
                final Player attacker = p;
                final Location cloneLoc = attacker.getLocation();

                ArmorStand clone = attacker.getWorld().spawn(cloneLoc, ArmorStand.class);
                clone.setVisible(false);
                clone.setArms(true);
                clone.setBasePlate(false);
                clone.setGravity(false);
                clone.setInvulnerable(true);
                clone.setCollidable(false);
                clone.setCustomNameVisible(false);
                clone.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "afterimage"), PersistentDataType.BYTE, (byte) 1);

                for (org.bukkit.inventory.EquipmentSlot slot : org.bukkit.inventory.EquipmentSlot.values()) {
                    clone.addEquipmentLock(slot, ArmorStand.LockType.ADDING_OR_CHANGING);
                    clone.addEquipmentLock(slot, ArmorStand.LockType.REMOVING_OR_CHANGING);
                }

                ItemStack[] armor = attacker.getInventory().getArmorContents();
                ItemStack helmet = armor[3];
                if (helmet != null && helmet.getType() != Material.AIR) {
                    clone.getEquipment().setHelmet(helmet.clone());
                }

                if (armor[2] != null) clone.getEquipment().setChestplate(armor[2].clone());
                if (armor[1] != null) clone.getEquipment().setLeggings(armor[1].clone());
                if (armor[0] != null) clone.getEquipment().setBoots(armor[0].clone());

                String handMode = plugin.getConfig().getString("players." + uuid + ".zone.hand_mode", "normal");
                boolean isInverted = handMode.equals("invert");

                if (!isInverted) {
                    clone.getEquipment().setItemInMainHand(attacker.getInventory().getItemInMainHand());
                    clone.getEquipment().setItemInOffHand(attacker.getInventory().getItemInOffHand());

                    clone.setHeadPose(new org.bukkit.util.EulerAngle(Math.toRadians(cloneLoc.getPitch()), 0, 0));
                    clone.setRightArmPose(new org.bukkit.util.EulerAngle(Math.toRadians(-45), Math.toRadians(25), 0));
                    clone.setLeftArmPose(new org.bukkit.util.EulerAngle(Math.toRadians(20), 0, 0));
                    clone.setRightLegPose(new org.bukkit.util.EulerAngle(Math.toRadians(-25), 0, 0));
                    clone.setLeftLegPose(new org.bukkit.util.EulerAngle(Math.toRadians(25), 0, 0));
                } else {
                    clone.getEquipment().setItemInMainHand(attacker.getInventory().getItemInOffHand());
                    clone.getEquipment().setItemInOffHand(attacker.getInventory().getItemInMainHand());

                    clone.setHeadPose(new org.bukkit.util.EulerAngle(Math.toRadians(cloneLoc.getPitch()), 0, 0));
                    clone.setRightArmPose(new org.bukkit.util.EulerAngle(Math.toRadians(20), 0, 0));
                    clone.setLeftArmPose(new org.bukkit.util.EulerAngle(Math.toRadians(-45), Math.toRadians(-25), 0));
                    clone.setRightLegPose(new org.bukkit.util.EulerAngle(Math.toRadians(25), 0, 0));
                    clone.setLeftLegPose(new org.bukkit.util.EulerAngle(Math.toRadians(-25), 0, 0));
                }

                afterimageHidden.add(attacker.getUniqueId());
                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (other != attacker && other.isOnline()) other.hidePlayer(plugin, attacker);
                }

                // Directly multiply this critical hit's damage by 1.5x
                mult *= 1.5;

                // Circular 360° Sweep Attack particles & Sound around afterimage
                for (double angle = 0; angle < 360; angle += 45) {
                    double rad = Math.toRadians(angle);
                    double sx = Math.cos(rad) * 1.3;
                    double sz = Math.sin(rad) * 1.3;
                    cloneLoc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, cloneLoc.clone().add(sx, 1.0, sz), 1);
                }
                cloneLoc.getWorld().spawnParticle(Particle.PORTAL, cloneLoc.clone().add(0, 1, 0), 25, 0.4, 0.5, 0.4, 0.05);
                attacker.playSound(cloneLoc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 1.2f);

                new BukkitRunnable() {
                    int ticks = 0;
                    @Override
                    public void run() {
                        if (!clone.isValid() || clone.isDead()) {
                            revealPlayer(attacker);
                            this.cancel();
                            return;
                        }
                        if (ticks >= 10) {
                            clone.remove();
                            revealPlayer(attacker);
                            this.cancel();
                            return;
                        }
                        if (ticks % 4 == 0) {
                            clone.getWorld().spawnParticle(Particle.DUST, clone.getLocation().add(0, 1, 0), 2, 0.2, 0.3, 0.2, 0.0, new Particle.DustOptions(org.bukkit.Color.AQUA, 1.0f));
                        }
                        ticks += 2;
                    }
                }.runTaskTimer(plugin, 0L, 2L);
            }

            if (t3.equals("light") && targetEnt != null) {
                int hits = lightHits.getOrDefault(uuid, 0) + 1;
                UUID tId = targetEnt.getUniqueId();
                Map<UUID, Integer> dMap = lightDebuffs.computeIfAbsent(uuid, k -> new HashMap<>());

                if (hits >= 3) {
                    hits = 0;
                    int stacks = dMap.getOrDefault(tId, 0) + 1;
                    if (stacks > 5) stacks = 5;
                    dMap.put(tId, stacks);

                    if (!origSpeed.containsKey(tId)) {
                        if (targetEnt.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) origSpeed.put(tId, targetEnt.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).getBaseValue());
                        if (targetEnt.getAttribute(Attribute.GENERIC_ATTACK_SPEED) != null) origAtkSpeed.put(tId, targetEnt.getAttribute(Attribute.GENERIC_ATTACK_SPEED).getBaseValue());
                    }

                    double moveRed = stacks * 0.03;
                    double atkSpeedRed = stacks * 0.03;
                    if (targetEnt.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) targetEnt.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(origSpeed.get(tId) * (1.0 - moveRed));
                    if (targetEnt.getAttribute(Attribute.GENERIC_ATTACK_SPEED) != null) targetEnt.getAttribute(Attribute.GENERIC_ATTACK_SPEED).setBaseValue(origAtkSpeed.get(tId) * (1.0 - atkSpeedRed));

                    targetEnt.getWorld().spawnParticle(Particle.WITCH, targetEnt.getLocation().add(0,2,0), 15);
                }
                lightHits.put(uuid, hits);

                // Tier 3 Light: Increase user damage by +3% per stack up to a maximum of 15%
                int currentStacks = dMap.getOrDefault(tId, 0);
                if (currentStacks > 0) {
                    mult *= (1.0 + (currentStacks * 0.03));
                }
            }

            if (isVanillaCrit) mult /= 1.5;
            event.setDamage(event.getDamage() * mult);

            Location critLoc = event.getEntity().getLocation().add(0, 0.35, 0);
            if (isOrangeCrit) {
                p.getWorld().spawnParticle(Particle.DUST, critLoc, 8, 0.25, 0.25, 0.25, 0.0, new Particle.DustOptions(org.bukkit.Color.ORANGE, 1.2f));
                if (alertsEnabled) p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.6f, 0.8f);
            } else {
                p.getWorld().spawnParticle(Particle.DUST, critLoc, 5, 0.2, 0.2, 0.2, 0.0, new Particle.DustOptions(org.bukkit.Color.YELLOW, 1.0f));
                if (alertsEnabled) p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.5f, 1.2f);
            }
            if (isValidTarget) lastValidHitTime.put(uuid, now);
            return;
        }

        int totalPoints = plugin.getConfig().getInt("players." + uuid + ".kills", 0) + plugin.getConfig().getInt("players." + uuid + ".overflow", 0);
        double critChance = Math.min(50.0, totalPoints * 0.8);
        if (plugin.eventManager != null && plugin.eventManager.isPvpBoss(uuid)) critChance += 70.0;
        if (isRanged && event.getDamager() instanceof org.bukkit.entity.AbstractArrow arrow && arrow.isShotFromCrossbow()) critChance -= 10.0;
        if (critChance < 0) critChance = 0.0;

        if (critChance > 0 && Math.random() * 100 < critChance) {
            Location critLoc = event.getEntity().getLocation().add(0, 0.35, 0);
            if (isValidTarget) {
                lastValidHitTime.put(uuid, now);

                int y = yellowStacks.getOrDefault(uuid, 0); int o = orangeStacks.getOrDefault(uuid, 0); int r = redStacks.getOrDefault(uuid, 0);
                boolean isYellow = true, isOrange = false, isRed = false, isBlack = false;

                if (totalPoints >= 30 && y >= 7 && Math.random() * 100 < 35.0) { isYellow = false; isOrange = true; }
                if (isOrange && totalPoints >= 60 && o >= 7) {
                    double redChance = Math.min(50.0, 15.0 + (r * 5.0));
                    if (Math.random() * 100 < redChance) { isOrange = false; isRed = true; }
                }

                if (isRed && totalPoints >= 100) {
                    int nextRStack = r + 1;
                    double blackChance = switch(nextRStack) {
                        case 1 -> 0.0; case 2 -> 5.0; case 3 -> 15.0;
                        case 4 -> 35.0; case 5 -> 50.0; case 6 -> 75.0;
                        default -> 100.0;
                    };
                    if (Math.random() * 100 < blackChance) { isRed = false; isBlack = true; }
                }

                if (isBlack) {
                    long durationMs = getZoneDurationMs(uuid);
                    zoneEndTime.put(uuid, now + durationMs);
                    yellowStacks.put(uuid, 0); orangeStacks.put(uuid, 0); redStacks.put(uuid, 0);

                    if (t1.equals("heavy")) p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int)(durationMs / 50L), 0));
                    updateBaseAttackSpeed(p, t1, 0);

                    heavyStacks.put(uuid, 0); heavyHitCount.put(uuid, 0);
                    lightDmgTaken.put(uuid, 0.0); lightHits.put(uuid, 0);

                    double mult = 4.0;
                    if (isVanillaCrit) mult /= 1.5;
                    event.setDamage(event.getDamage() * mult);

                    playBlackFlashVFX(targetLoc);
                    if (alertsEnabled) {
                        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "☠ BLACK CRIT - THE ZONE (" + (durationMs / 1000) + "s) ☠"));
                    }
                }
                else if (isRed) {
                    redStacks.put(uuid, r + 1);
                    double mult = 3.0; if (isVanillaCrit) mult /= 1.5; event.setDamage(event.getDamage() * mult);
                    p.getWorld().spawnParticle(Particle.DUST, critLoc, 12, 0.3, 0.3, 0.3, 0.0, new Particle.DustOptions(org.bukkit.Color.RED, 1.4f));
                    if (alertsEnabled) p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.4f, 2f);
                }
                else if (isOrange) {
                    orangeStacks.put(uuid, o + 1);
                    double mult = 2.0; if (isVanillaCrit) mult /= 1.5; event.setDamage(event.getDamage() * mult);
                    p.getWorld().spawnParticle(Particle.DUST, critLoc, 8, 0.25, 0.25, 0.25, 0.0, new Particle.DustOptions(org.bukkit.Color.ORANGE, 1.2f));
                    if (alertsEnabled) p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.6f, 0.8f);
                }
                else if (isYellow) {
                    yellowStacks.put(uuid, y + 1);
                    double mult = 1.5; if (isVanillaCrit) mult /= 1.5; event.setDamage(event.getDamage() * mult);
                    p.getWorld().spawnParticle(Particle.DUST, critLoc, 5, 0.2, 0.2, 0.2, 0.0, new Particle.DustOptions(org.bukkit.Color.YELLOW, 1.0f));
                    if (alertsEnabled) p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.5f, 1.2f);
                }
            } else {
                double mult = 1.5; if (isVanillaCrit) mult /= 1.5; event.setDamage(event.getDamage() * mult);
                p.getWorld().spawnParticle(Particle.DUST, critLoc, 5, 0.2, 0.2, 0.2, 0.0, new Particle.DustOptions(org.bukkit.Color.YELLOW, 1.0f));
                if (alertsEnabled) p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.5f, 1.2f);
            }
        }
    }

    // ... ส่วนที่เหลือคือ Event ตาย และ โยนพุก (ใส่เหมือนเดิมเลยครับ)
    private void dropStars(Location loc, String type, int amount) {
        while (amount > 0) {
            int drop = Math.min(amount, 64);
            ItemStack star = plugin.createStar(type);
            star.setAmount(drop);
            loc.getWorld().dropItemNaturally(loc, star);
            amount -= drop;
        }
    }

    @EventHandler
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (event.getEntity() instanceof Player) {
            if (event.getNewEffect() != null && event.getNewEffect().getType().equals(PotionEffectType.STRENGTH)) {
                if (event.getCause() != EntityPotionEffectEvent.Cause.PLUGIN) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBossWitherDamage(EntityDamageByEntityEvent event) {
        if (plugin.eventManager == null) return;
        if (event.getDamager() instanceof org.bukkit.entity.WitherSkull skull) {
            if (skull.getShooter() instanceof LivingEntity shooter && plugin.eventManager.isEventBoss(shooter.getUniqueId())) {
                if (event.getEntity() instanceof Player target) {
                    event.setDamage(18.0);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 1));
                }
            }
        }
        else if (event.getDamager() instanceof org.bukkit.entity.Wither wither) {
            if (plugin.eventManager.isEventBoss(wither.getUniqueId())) {
                if (event.getEntity() instanceof Player target) {
                    event.setDamage(20.0);
                }
            }
        }
    }

    @EventHandler
    public void onPearlThrow(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof EnderPearl pearl && event.getEntity().getShooter() instanceof Player p) {
            UUID uuid = p.getUniqueId();
            if (p.hasCooldown(Material.ENDER_PEARL)) { event.setCancelled(true); return; }
            int charges = pearlCharges.getOrDefault(uuid, 16);
            if (charges <= 1) {
                pearlCharges.put(uuid, 16);
                Bukkit.getScheduler().runTask(plugin, () -> p.setCooldown(Material.ENDER_PEARL, 1200));
                p.sendMessage(plugin.PREFIX + ChatColor.RED + "Ender Pearl overheated! 60s cooldown.");
                if (pearlTasks.containsKey(uuid)) { Bukkit.getScheduler().cancelTask(pearlTasks.get(uuid)); pearlTasks.remove(uuid); }
            } else {
                charges--; pearlCharges.put(uuid, charges);
                if (!pearlTasks.containsKey(uuid)) {
                    int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                        if (!p.isOnline() || p.hasCooldown(Material.ENDER_PEARL)) return;
                        int current = pearlCharges.getOrDefault(uuid, 16);
                        if (current < 16) pearlCharges.put(uuid, current + 1);
                        if (current >= 16) { Bukkit.getScheduler().cancelTask(pearlTasks.get(uuid)); pearlTasks.remove(uuid); }
                    }, 200L, 200L);
                    pearlTasks.put(uuid, taskId);
                }
            }
            new BukkitRunnable() {
                @Override public void run() {
                    if (pearl.isValid() && !pearl.isDead()) {
                        pearl.remove();
                        if (plugin.getConfig().getBoolean("players." + p.getUniqueId() + ".alerts", true) && p.isOnline())
                            p.sendMessage(plugin.PREFIX + ChatColor.YELLOW + "Your Ender Pearl despawned.");
                    }
                }
            }.runTaskLater(plugin, 300L);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity(); UUID vId = victim.getUniqueId();
        revealPlayer(victim);

        yellowStacks.remove(vId); orangeStacks.remove(vId); redStacks.remove(vId);
        zoneEndTime.remove(vId); lastValidHitTime.remove(vId);
        heavyStacks.remove(vId); heavyHitCount.remove(vId);
        lightDmgTaken.remove(vId); lightHits.remove(vId); clearLightDebuffs(vId);

        victim.removePotionEffect(PotionEffectType.SLOWNESS);
        if (victim.getAttribute(Attribute.GENERIC_ATTACK_SPEED) != null) {
            victim.getAttribute(Attribute.GENERIC_ATTACK_SPEED).setBaseValue(4.0);
        }
        if (victim.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) {
            victim.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.1);
        }

        if (deathCooldowns.containsKey(vId) && System.currentTimeMillis() - deathCooldowns.get(vId) < 3000) return;
        deathCooldowns.put(vId, System.currentTimeMillis());

        Player killer = victim.getKiller();
        if (killer == null) {
            if (combatTagTimer.containsKey(vId) && combatTagTimer.get(vId) > System.currentTimeMillis()) {
                UUID attackerId = combatTagAttacker.get(vId);
                if (attackerId != null) killer = Bukkit.getPlayer(attackerId);
            }
        }

        int vKills = plugin.getConfig().getInt("players." + vId + ".kills", 0);
        int vOverflow = plugin.getConfig().getInt("players." + vId + ".overflow", 0);
        int vTotal = vKills + vOverflow;
        plugin.getConfig().set("players." + vId + ".pending_heart_loss", true);
        plugin.getConfig().set("players." + vId + ".kills", 0);

        if (killer != null && killer != victim) {
            UUID kId = killer.getUniqueId();
            String ipPair = killer.getAddress().getAddress().getHostAddress() + "_" + victim.getAddress().getAddress().getHostAddress();

            if (killCooldowns.containsKey(ipPair) && System.currentTimeMillis() - killCooldowns.get(ipPair) < 300000) {
                plugin.saveConfig(); return;
            }
            killCooldowns.put(ipPair, System.currentTimeMillis());

            int kKills = plugin.getConfig().getInt("players." + kId + ".kills", 0);
            int kOverflow = plugin.getConfig().getInt("players." + kId + ".overflow", 0);
            int kTotal = kKills + kOverflow;
            int diff = vTotal - kTotal;

            boolean dropAtCorpse = plugin.getConfig().getBoolean("settings.drop_at_corpse", true);

            if (diff >= 40 || (vTotal >= kTotal * 2 && diff >= 25)) {
                double stealPercent = Math.min(60.0, diff * 0.5) / 100.0;
                int overflowSteal = (int) Math.ceil(vOverflow * stealPercent);
                int totalSouls = 1 + (diff / 30);

                plugin.getConfig().set("players." + vId + ".overflow", Math.max(0, vOverflow - overflowSteal));
                plugin.getConfig().set("players." + kId + ".kills", kKills + 1);

                if (dropAtCorpse) {
                    dropStars(victim.getLocation(), "soul", totalSouls);
                    if (overflowSteal > 0) dropStars(victim.getLocation(), "triumph", overflowSteal);
                } else {
                    plugin.giveRewardSmart(killer, "soul", totalSouls);
                    if (overflowSteal > 0) plugin.giveRewardSmart(killer, "triumph", overflowSteal);
                }
            } else if (diff > -40) {
                plugin.getConfig().set("players." + kId + ".kills", kKills + 1);
                if (dropAtCorpse) dropStars(victim.getLocation(), "soul", 1);
                else plugin.giveRewardSmart(killer, "soul", 1);
            }
        }
        plugin.saveConfig();
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer(); UUID uuid = player.getUniqueId();
        String t1 = plugin.getConfig().getString("players." + uuid + ".zone.tier1", "none");
        updateBaseAttackSpeed(player, t1, 0);
        if (player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) {
            player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.1);
        }
        player.removePotionEffect(PotionEffectType.SLOWNESS);

        if (plugin.getConfig().getBoolean("players." + uuid + ".pending_heart_loss", false)) {
            plugin.getConfig().set("players." + uuid + ".pending_heart_loss", false); plugin.saveConfig();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                double newMax = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue() - 2.0;
                if (newMax <= 0.0) {
                    player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0); player.setHealth(20.0);
                    plugin.getConfig().set("players." + uuid + ".kills", 0); plugin.getConfig().set("players." + uuid + ".overflow", 0); plugin.saveConfig();
                    Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), "advancement revoke " + player.getName() + " everything");
                    player.sendTitle(ChatColor.DARK_RED + "☠ SOUL SHATTERED ☠", ChatColor.RED + "You lost all hearts.", 10, 100, 20);
                } else { player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(newMax); }
            }, 10L);
        }
    }

    @EventHandler public void onAdvancement(PlayerAdvancementDoneEvent event) { if (event.getAdvancement().getDisplay() != null && event.getAdvancement().getDisplay().shouldAnnounceChat()) { int p = switch (event.getAdvancement().getDisplay().getType().name()) { case "CHALLENGE" -> 3; case "GOAL" -> 2; default -> 1; }; plugin.giveRewardSmart(event.getPlayer(), "triumph", p); } }
    @EventHandler public void onResurrect(EntityResurrectEvent event) { if (event.getEntity() instanceof Player) event.setCancelled(true); }
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        revealPlayer(p);
        if (zoneEndTime.containsKey(p.getUniqueId())) {
            endTheZone(p);
        } else {
            String t1 = plugin.getConfig().getString("players." + p.getUniqueId() + ".zone.tier1", "none");
            updateBaseAttackSpeed(p, t1, 0);
            if (p.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) {
                p.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.1);
            }
            p.removePotionEffect(PotionEffectType.SLOWNESS);
        }
        clearLightDebuffs(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) return;

        Player p = event.getPlayer();
        long now = System.currentTimeMillis();

        for (DomainData domain : activeDomains.values()) {
            if (now > domain.endTime) continue;

            // If the mover is the caster/owner of the domain
            if (p.getUniqueId().equals(domain.owner)) {
                if (to.getWorld().equals(domain.center.getWorld())) {
                    double dist = to.distance(domain.center);
                    // When reaching/hitting near the border (>= 7.0 blocks from center), shift center to player
                    if (dist >= 7.0) {
                        domain.center = to.clone();
                    }
                }
                continue;
            }

            if (domain.isEscaping(from, to)) {
                if (p.isInsideVehicle()) p.leaveVehicle();
                event.setTo(from.clone());
                Vector push = domain.center.toVector().subtract(to.toVector()).normalize().multiply(0.5).setY(0.1);
                p.setVelocity(push);
                p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.3f, 1.8f);
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ChatColor.RED + "§l⚠ SHOCKWAVE BARRIER PREVENTS ESCAPE! ⚠"));
                break;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerTeleport(org.bukkit.event.player.PlayerTeleportEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        Player p = event.getPlayer();
        long now = System.currentTimeMillis();

        for (DomainData domain : activeDomains.values()) {
            if (now > domain.endTime) continue;

            if (p.getUniqueId().equals(domain.owner)) {
                if (to.getWorld().equals(domain.center.getWorld())) {
                    domain.center = to.clone();
                }
                continue;
            }

            if (domain.isInside(from) && !domain.isInside(to)) {
                event.setCancelled(true);
                p.playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.8f, 0.6f);
                p.sendMessage(plugin.PREFIX + ChatColor.RED + "You cannot teleport out of the Shockwave Domain!");
                break;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVehicleMove(org.bukkit.event.vehicle.VehicleMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        long now = System.currentTimeMillis();
        for (DomainData domain : activeDomains.values()) {
            if (now > domain.endTime) continue;
            if (domain.isEscaping(from, to)) {
                event.getVehicle().eject();
                event.getVehicle().setVelocity(domain.center.toVector().subtract(to.toVector()).normalize().multiply(0.5).setY(0.1));
                break;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onArmorStandManipulate(org.bukkit.event.player.PlayerArmorStandManipulateEvent event) {
        ArmorStand stand = event.getRightClicked();
        if (stand.isInvulnerable() || stand.getPersistentDataContainer().has(new org.bukkit.NamespacedKey(plugin, "afterimage"), PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }
}