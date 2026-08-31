package com.yourname.noesissmp;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
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
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
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
    private final Map<UUID, Long> afterimageCD = new HashMap<>();
    public final Set<UUID> afterimageHidden = new HashSet<>();
    public final Set<UUID> afterimageDamageLock = new HashSet<>();

    public final NamespacedKey ZONE_ATK_SPEED_KEY;
    public final NamespacedKey LIGHT_DEBUFF_ATK_KEY;
    public final NamespacedKey LIGHT_DEBUFF_SPD_KEY;

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
        this.ZONE_ATK_SPEED_KEY = new NamespacedKey(plugin, "zone_attack_speed");
        this.LIGHT_DEBUFF_ATK_KEY = new NamespacedKey(plugin, "light_debuff_atk");
        this.LIGHT_DEBUFF_SPD_KEY = new NamespacedKey(plugin, "light_debuff_spd");
        startCritDecayTask();
    }

    public void revealPlayer(Player p) {
        if (p == null) return;
        if (afterimageHidden.remove(p.getUniqueId())) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other != p && other.isOnline()) other.showPlayer(plugin, p);
            }
            p.setWalkSpeed(0.2f);
        }
    }

    public void removeAttributeModifier(org.bukkit.attribute.AttributeInstance attr, NamespacedKey key) {
        if (attr == null || key == null) return;
        for (AttributeModifier mod : new java.util.ArrayList<>(attr.getModifiers())) {
            if (key.equals(mod.getKey())) {
                attr.removeModifier(mod);
            }
        }
    }

    public void setAttributeModifier(org.bukkit.attribute.AttributeInstance attr, NamespacedKey key, double amount, AttributeModifier.Operation op) {
        if (attr == null || key == null) return;
        removeAttributeModifier(attr, key);
        if (Math.abs(amount) > 0.0001) {
            attr.addModifier(new AttributeModifier(key, amount, op, org.bukkit.inventory.EquipmentSlotGroup.ANY));
        }
    }

    public void updateBaseAttackSpeed(Player p, String t1, int stack) {
        updateZoneAttackSpeed(p);
    }

    public void updateZoneAttackSpeed(Player p) {
        if (p == null) return;
        UUID uuid = p.getUniqueId();
        org.bukkit.attribute.AttributeInstance attr = p.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
        if (attr == null) return;

        if (attr.getBaseValue() != 4.0) {
            attr.setBaseValue(4.0);
        }

        removeAttributeModifier(attr, ZONE_ATK_SPEED_KEY);

        long now = System.currentTimeMillis();
        boolean inZone = zoneEndTime.containsKey(uuid) && zoneEndTime.get(uuid) > now;
        if (!inZone) {
            return;
        }

        String t1 = plugin.getConfig().getString("players." + uuid + ".zone.tier1", "none");
        String t2 = plugin.getConfig().getString("players." + uuid + ".zone.tier2", "none");
        int stack = heavyStacks.getOrDefault(uuid, 0);

        double scalarMultiplier = 1.0;
        if (t1.equals("heavy")) {
            scalarMultiplier *= 0.90; // -10% Attack Speed
        } else if (t1.equals("light")) {
            if (!t2.equals("heavy")) {
                scalarMultiplier *= 1.15; // +15% Attack Speed
            }
        }

        scalarMultiplier *= (1.0 - (stack * 0.03));
        if (t2.equals("heavy") && scalarMultiplier > 1.0) {
            scalarMultiplier = 1.0;
        }

        double modAmount = scalarMultiplier - 1.0;
        setAttributeModifier(attr, ZONE_ATK_SPEED_KEY, modAmount, AttributeModifier.Operation.ADD_SCALAR);
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
        playBlackFlashVFX(center, null);
    }

    public void playBlackFlashVFX(Location targetLoc, Location attackerLoc) {
        if (targetLoc == null || targetLoc.getWorld() == null) return;
        org.bukkit.World world = targetLoc.getWorld();

        // 1. Audio Impact (Crisp thunder & explosive crackle)
        try {
            world.playSound(targetLoc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.4f, 0.5f);
            world.playSound(targetLoc, Sound.ITEM_TRIDENT_THUNDER, 1.5f, 0.6f);
            world.playSound(targetLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
            world.playSound(targetLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.2f, 0.6f);
            world.playSound(targetLoc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 0.5f);
        } catch (Throwable ignored) {}

        // 2. Core Flash & Impact Spark
        try {
            world.spawnParticle(Particle.FLASH, targetLoc, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.ENCHANT, targetLoc, 20, 0.3, 0.3, 0.3, 0.5);
            world.spawnParticle(Particle.SQUID_INK, targetLoc, 4, 0.1, 0.1, 0.1, 0.02);
        } catch (Throwable ignored) {}

        // 3. Direction Vector: surges from targetLoc towards & past attackerLoc
        Vector baseDir;
        double distToAttacker = 2.5;

        if (attackerLoc != null && attackerLoc.getWorld() != null && attackerLoc.getWorld().equals(world)) {
            baseDir = attackerLoc.toVector().subtract(targetLoc.toVector());
            double len = baseDir.length();
            if (len > 0.1) {
                distToAttacker = len;
                baseDir.normalize();
            } else {
                baseDir = attackerLoc.getDirection().multiply(-1).normalize();
            }
        } else {
            baseDir = new Vector(0, 0.6, 0).normalize();
        }

        final Vector mainDir = baseDir;
        final double targetDist = distToAttacker;
        final Color COLOR_BLACK = Color.fromRGB(0, 0, 0);
        final java.util.Random rand = new java.util.Random();

        // 4. Multi-wave razor-thin branching black lightning (Crisp, defined, sharp lines)
        new BukkitRunnable() {
            int wave = 0;
            final int maxWaves = 4;

            @Override
            public void run() {
                if (wave >= maxWaves) {
                    this.cancel();
                    return;
                }

                // 8 razor-thin branching lightning bolts per wave
                for (int b = 0; b < 8; b++) {
                    Vector direction = mainDir.clone().add(new Vector(
                            (rand.nextDouble() - 0.5) * 0.75,
                            (rand.nextDouble() - 0.5) * 0.65,
                            (rand.nextDouble() - 0.5) * 0.75
                    )).normalize();

                    Location current = targetLoc.clone().add(
                            (rand.nextDouble() - 0.5) * 0.2,
                            (rand.nextDouble() - 0.5) * 0.2,
                            (rand.nextDouble() - 0.5) * 0.2
                    );

                    float initialScale = 0.85f; // Thin, sharp scale
                    double maxReach = Math.max(3.2, targetDist + 1.2 + (rand.nextDouble() * 2.8));

                    for (double step = 0; step < maxReach; step += 0.15) {
                        // Sharp angular lightning zig-zags
                        if (rand.nextDouble() < 0.38) {
                            direction.add(new Vector(
                                    (rand.nextDouble() - 0.5) * 0.75,
                                    (rand.nextDouble() - 0.5) * 0.65,
                                    (rand.nextDouble() - 0.5) * 0.75
                            )).normalize();
                        }
                        current.add(direction.clone().multiply(0.15));

                        float currentScale = (float) Math.max(0.35, initialScale * (1.0 - (step / maxReach)));
                        Particle.DustOptions stepDust = new Particle.DustOptions(COLOR_BLACK, currentScale);

                        world.spawnParticle(Particle.DUST, current, 1, 0, 0, 0, 0, stepDust);

                        // Primary Branch Forks (razor-thin branch tendrils)
                        if (rand.nextDouble() < 0.08 && step < maxReach * 0.85) {
                            Vector branchDir = direction.clone().add(new Vector(
                                    (rand.nextDouble() - 0.5) * 1.1,
                                    (rand.nextDouble() - 0.5) * 0.9,
                                    (rand.nextDouble() - 0.5) * 1.1
                            )).normalize();

                            Location branchCurrent = current.clone();
                            double branchLength = 1.2 + rand.nextDouble() * 2.2;

                            for (double branchStep = 0; branchStep < branchLength; branchStep += 0.15) {
                                if (rand.nextDouble() < 0.35) {
                                    branchDir.add(new Vector(
                                            (rand.nextDouble() - 0.5) * 0.7,
                                            (rand.nextDouble() - 0.5) * 0.6,
                                            (rand.nextDouble() - 0.5) * 0.7
                                    )).normalize();
                                }
                                branchCurrent.add(branchDir.clone().multiply(0.15));

                                float branchScale = (float) Math.max(0.30, 0.65 * (1.0 - (branchStep / branchLength)));
                                world.spawnParticle(
                                        Particle.DUST, branchCurrent, 1, 0, 0, 0, 0,
                                        new Particle.DustOptions(COLOR_BLACK, branchScale)
                                );

                                // Secondary Sub-branch Fork
                                if (rand.nextDouble() < 0.06 && branchStep < branchLength * 0.7) {
                                    Vector subDir = branchDir.clone().add(new Vector(
                                            (rand.nextDouble() - 0.5) * 1.2,
                                            (rand.nextDouble() - 0.5) * 1.0,
                                            (rand.nextDouble() - 0.5) * 1.2
                                    )).normalize();

                                    Location subCurrent = branchCurrent.clone();
                                    double subLength = 0.8 + rand.nextDouble() * 1.2;

                                    for (double subStep = 0; subStep < subLength; subStep += 0.16) {
                                        subCurrent.add(subDir.clone().multiply(0.16));
                                        world.spawnParticle(
                                                Particle.DUST, subCurrent, 1, 0, 0, 0, 0,
                                                new Particle.DustOptions(COLOR_BLACK, 0.35f)
                                        );
                                    }
                                }
                            }
                        }
                    }
                }
                wave++;
            }
        }.runTaskTimer(plugin, 0L, 2L);

        // 5. Scattered Crit Aura Embers (All 4 colors: Purple, Red, Orange, Yellow appear simultaneously)
        final Color COLOR_PURPLE = Color.fromRGB(185, 50, 255); // The Zone Purple
        final Color COLOR_RED = Color.fromRGB(255, 40, 40);      // Tier 3 Red
        final Color COLOR_ORANGE = Color.fromRGB(255, 140, 0);   // Tier 2 Orange
        final Color COLOR_YELLOW = Color.fromRGB(255, 230, 30);  // Tier 1 Yellow
        final Color[] ALL_CRIT_COLORS = new Color[]{COLOR_PURPLE, COLOR_RED, COLOR_ORANGE, COLOR_YELLOW};

        class FloatingEmber {
            final Location loc;
            final Vector vel;
            final Color color;

            FloatingEmber(Location loc, Vector vel, Color color) {
                this.loc = loc;
                this.vel = vel;
                this.color = color;
            }
        }

        final java.util.List<FloatingEmber> embers = new java.util.ArrayList<>();
        // For each of the 4 colors, spawn 5 distinct embers scattered in the air
        for (Color color : ALL_CRIT_COLORS) {
            for (int k = 0; k < 5; k++) {
                double progress = rand.nextDouble() * Math.min(targetDist + 1.5, 3.8);
                Location spawnLoc = targetLoc.clone().add(mainDir.clone().multiply(progress)).add(
                        (rand.nextDouble() - 0.5) * 1.8,
                        (rand.nextDouble() - 0.5) * 1.2 + 0.2,
                        (rand.nextDouble() - 0.5) * 1.8
                );
                Vector vel = new Vector(
                        (rand.nextDouble() - 0.5) * 0.035,
                        rand.nextDouble() * 0.03 + 0.015,
                        (rand.nextDouble() - 0.5) * 0.035
                );
                embers.add(new FloatingEmber(spawnLoc, vel, color));
            }
        }

        new BukkitRunnable() {
            int tick = 0;
            final int maxTicks = 30; // ~1.5s slow fade

            @Override
            public void run() {
                if (tick >= maxTicks) {
                    this.cancel();
                    return;
                }

                float scale = (float) Math.max(0.35, 1.15 * (1.0 - ((double) tick / maxTicks)));

                for (FloatingEmber ember : embers) {
                    ember.loc.add(ember.vel);
                    ember.vel.multiply(0.96); // Soft drag
                    ember.vel.setY(ember.vel.getY() + 0.001); // Gentle upward float

                    world.spawnParticle(
                            Particle.DUST,
                            ember.loc,
                            1, 0, 0, 0, 0,
                            new Particle.DustOptions(ember.color, scale)
                    );
                }

                tick += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
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
                            } else if (t2.equals("light")) {
                                long lastAfterimage = afterimageCD.getOrDefault(uuid, 0L);
                                if (now < lastAfterimage + 20000L) {
                                    long cdLeft = (lastAfterimage + 20000L - now + 999) / 1000;
                                    extraText = ChatColor.AQUA + " [Afterimage: " + cdLeft + "s]";
                                } else {
                                    extraText = ChatColor.GREEN + " [Afterimage: READY]";
                                }
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
            if (t != null) {
                if (t.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) removeAttributeModifier(t.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED), LIGHT_DEBUFF_SPD_KEY);
                if (t.getAttribute(Attribute.GENERIC_ATTACK_SPEED) != null) removeAttributeModifier(t.getAttribute(Attribute.GENERIC_ATTACK_SPEED), LIGHT_DEBUFF_ATK_KEY);
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

        if (p != null && !afterimageDamageLock.contains(p.getUniqueId())) revealPlayer(p);
        if (event.getEntity() instanceof Player damagedPlayer) revealPlayer(damagedPlayer);

        if (p != null && (shockwaveDamageLock.contains(p.getUniqueId()) || afterimageDamageLock.contains(p.getUniqueId()))) return;

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



            if (t3.equals("light") && targetEnt != null) {
                int hits = lightHits.getOrDefault(uuid, 0) + 1;
                UUID tId = targetEnt.getUniqueId();
                Map<UUID, Integer> dMap = lightDebuffs.computeIfAbsent(uuid, k -> new HashMap<>());

                if (hits >= 3) {
                    hits = 0;
                    int stacks = dMap.getOrDefault(tId, 0) + 1;
                    if (stacks > 5) stacks = 5;
                    dMap.put(tId, stacks);

                    double moveRed = - (stacks * 0.03);
                    double atkSpeedRed = - (stacks * 0.03);
                    if (targetEnt.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) {
                        setAttributeModifier(targetEnt.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED), LIGHT_DEBUFF_SPD_KEY, moveRed, AttributeModifier.Operation.ADD_SCALAR);
                    }
                    if (targetEnt.getAttribute(Attribute.GENERIC_ATTACK_SPEED) != null) {
                        setAttributeModifier(targetEnt.getAttribute(Attribute.GENERIC_ATTACK_SPEED), LIGHT_DEBUFF_ATK_KEY, atkSpeedRed, AttributeModifier.Operation.ADD_SCALAR);
                    }

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

                    playBlackFlashVFX(targetLoc, p.getLocation().add(0, 1.1, 0));
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
                if (event.getEntity() instanceof Player) {
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

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player p = event.getPlayer();
        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();

        String t2 = plugin.getConfig().getString("players." + uuid + ".zone.tier2", "none");
        if (!t2.equals("light")) return;

        ItemStack mainHand = p.getInventory().getItemInMainHand();
        if (mainHand == null || !mainHand.getType().name().endsWith("_SWORD")) return;

        boolean inZone = zoneEndTime.containsKey(uuid) && zoneEndTime.get(uuid) > now;
        if (!inZone) return;

        event.setCancelled(true);

        long lastUse = afterimageCD.getOrDefault(uuid, 0L);
        if (now < lastUse + 20000L) {
            long remaining = (lastUse + 20000L - now + 999) / 1000;
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ChatColor.RED + "§l⚠ Afterimage Cooldown: " + remaining + "s"));
            p.sendMessage(plugin.PREFIX + ChatColor.RED + "Afterimage is on cooldown for " + remaining + "s!");
            return;
        }

        afterimageCD.put(uuid, now);
        triggerAfterimage(p);
    }

    private void triggerAfterimage(Player p) {
        UUID uuid = p.getUniqueId();
        p.setWalkSpeed(0.44f); // Speed VI equivalent walk speed (+120% speed) without potion effect/icon
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 40, 0, false, false, true));

        afterimageHidden.add(p.getUniqueId());
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other != p && other.isOnline()) other.hidePlayer(plugin, p);
        }

        boolean alertsEnabled = plugin.getConfig().getBoolean("players." + uuid + ".alerts", true);
        if (alertsEnabled) {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ChatColor.AQUA + "§l⚡ AFTERIMAGE (1.5s Speed Boost + 2s Invisibility) ⚡"));
            p.sendMessage(plugin.PREFIX + ChatColor.AQUA + "Afterimage Activated! (1.5s Speed & Illusions, 2s Invisibility)");
        }

        final Location centerLoc = p.getLocation().clone();
        final double maxRadius = 4.8;

        // Global activation audio (Sharp Wind Slice & Whoosh Burst) & initial sweep attack ring
        centerLoc.getWorld().playSound(centerLoc, Sound.ITEM_TRIDENT_RIPTIDE_2, 1.1f, 1.6f);
        centerLoc.getWorld().playSound(centerLoc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 1.4f);
        centerLoc.getWorld().playSound(centerLoc, Sound.ENTITY_BREEZE_WIND_BURST, 0.9f, 1.5f);
        for (double a = 0; a < 360; a += 30) {
            double rad = Math.toRadians(a);
            double rx = Math.cos(rad) * maxRadius;
            double rz = Math.sin(rad) * maxRadius;
            centerLoc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, centerLoc.clone().add(rx, 1.0, rz), 1);
            centerLoc.getWorld().spawnParticle(Particle.CLOUD, centerLoc.clone().add(rx, 0.5, rz), 3, 0.2, 0.2, 0.2, 0.05);
        }

        final java.util.List<ArmorStand> activeClones = new java.util.ArrayList<>();

        // Staggered Spawning & Timed Decay Runnable with Randomized Locations and Facing Angles (30 ticks = 1.5s)
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || ticks >= 30) {
                    for (ArmorStand clone : activeClones) {
                        if (clone != null && clone.isValid()) {
                            clone.getWorld().spawnParticle(Particle.DUST, clone.getLocation().add(0, 1, 0), 4, 0.2, 0.3, 0.2, 0.0, new Particle.DustOptions(org.bukkit.Color.AQUA, 1.0f));
                            clone.remove();
                        }
                    }
                    activeClones.clear();
                    revealPlayer(p);
                    this.cancel();
                    return;
                }

                // Continuous true damage & armor durability drain (every 5 ticks = 6 hits over 1.5s, totaling 3.6 hearts of true damage)
                if (ticks % 5 == 0) {
                    afterimageDamageLock.add(p.getUniqueId());
                    for (org.bukkit.entity.Entity ent : centerLoc.getWorld().getNearbyEntities(centerLoc, maxRadius, 3.0, maxRadius)) {
                        if (ent instanceof LivingEntity victim && ent != p && !(ent instanceof ArmorStand)) {
                            Vector prevVel = victim.getVelocity();
                            victim.setNoDamageTicks(0);

                            // True Damage: 1.2 HP per hit (6 hits = 7.2 HP / 3.6 Hearts total, bypassing all armor/protection)
                            double trueDmg = 1.2;
                            double curHp = victim.getHealth();
                            if (curHp - trueDmg <= 0.001) {
                                victim.damage(99999.0, p);
                            } else {
                                victim.setHealth(Math.max(0.0, curHp - trueDmg));
                                victim.playHurtAnimation(0.0f);
                            }

                            victim.setVelocity(prevVel); // Cancel immediate knockback
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    if (victim.isValid() && !victim.isDead()) {
                                        victim.setVelocity(prevVel);
                                    }
                                }
                            }.runTaskLater(plugin, 1L);

                            // Armor Durability Shredding with Unbreaking Calculation
                            if (victim.getEquipment() != null) {
                                ItemStack[] armor = victim.getEquipment().getArmorContents();
                                boolean brokenAny = false;
                                for (int i = 0; i < armor.length; i++) {
                                    ItemStack piece = armor[i];
                                    if (piece != null && piece.getType() != Material.AIR && piece.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable meta) {
                                        int unbreakingLevel = piece.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.UNBREAKING);
                                        int pointsToDamage = 0;
                                        for (int d = 0; d < 4; d++) {
                                            if (unbreakingLevel > 0) {
                                                double chance = (60.0 + (40.0 / (unbreakingLevel + 1.0))) / 100.0;
                                                if (Math.random() < chance) {
                                                    pointsToDamage++;
                                                }
                                            } else {
                                                pointsToDamage++;
                                            }
                                        }

                                        if (pointsToDamage > 0) {
                                            int currentDmg = meta.getDamage();
                                            int maxDmg = piece.getType().getMaxDurability();
                                            int newDmg = currentDmg + pointsToDamage;
                                            if (maxDmg > 0 && newDmg >= maxDmg) {
                                                armor[i] = null;
                                                brokenAny = true;
                                            } else {
                                                meta.setDamage(newDmg);
                                                piece.setItemMeta(meta);
                                            }
                                        }
                                    }
                                }
                                victim.getEquipment().setArmorContents(armor);
                                if (brokenAny) {
                                    victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.9f);
                                }
                            }

                            victim.getWorld().spawnParticle(Particle.SWEEP_ATTACK, victim.getLocation().add(0, 1.0, 0), 1);
                            victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1.0, 0), 4, 0.2, 0.2, 0.2, 0.05);
                            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, 0.7f, 1.5f);
                        }
                    }
                    afterimageDamageLock.remove(p.getUniqueId());
                }

                // Every single tick, spawn a clone spread widely in a 1.2 to 4.8 block area
                double angle = Math.random() * 2 * Math.PI;
                double dist = 1.2 + Math.random() * 3.6;
                double x = Math.cos(angle) * dist;
                double z = Math.sin(angle) * dist;
                Location spot = centerLoc.clone().add(x, 0, z);

                float randomYaw = (float) (Math.random() * 360.0);
                float randomPitch = (float) ((Math.random() - 0.5) * 15.0);

                ArmorStand clone = spawnStaggeredAfterimage(p, spot, randomYaw, randomPitch);
                if (clone != null) {
                    p.hideEntity(plugin, clone); // Hide clone from the user's view so it never blocks their vision
                    activeClones.add(clone);

                    double yawRad = Math.toRadians(randomYaw);
                    final Vector forward = new Vector(-Math.sin(yawRad), 0, Math.cos(yawRad)).normalize().multiply(0.40);
                    String handMode = plugin.getConfig().getString("players." + p.getUniqueId() + ".zone.hand_mode", "normal");
                    final boolean isInverted = handMode.equals("invert");

                    // Natural running forward: clone runs forward for 9 ticks (~3.6 blocks) with smooth stride and athletic sprint lean
                    new BukkitRunnable() {
                        int life = 0;
                        final int maxLife = 9;

                        @Override
                        public void run() {
                            life += 1;
                            if (!clone.isValid() || clone.isDead() || life >= maxLife) {
                                if (clone.isValid()) {
                                    clone.getWorld().spawnParticle(Particle.DUST, clone.getLocation().add(0, 1, 0), 2, 0.15, 0.25, 0.15, 0.0, new Particle.DustOptions(org.bukkit.Color.AQUA, 0.9f));
                                    if (Math.random() < 0.35) {
                                        clone.getWorld().playSound(clone.getLocation(), Sound.ENTITY_BREEZE_IDLE_AIR, 0.35f, 1.8f);
                                    }
                                    clone.remove();
                                    activeClones.remove(clone);
                                }
                                this.cancel();
                                return;
                            }

                            // Move clone forward across distance
                            Location cur = clone.getLocation();
                            Location next = cur.clone().add(forward);
                            if (!next.getBlock().getType().isSolid()) {
                                clone.teleport(next);
                            }

                            // Smooth natural running stride animation (calm, athletic pacing)
                            double progress = (double) life / (double) maxLife;
                            double legAngle = Math.sin(progress * Math.PI) * 28.0; // 0 -> 28 -> 0 deg smooth single stride
                            double armAngle = Math.sin(progress * Math.PI) * 22.0;

                            clone.setBodyPose(new org.bukkit.util.EulerAngle(Math.toRadians(12), 0, 0));

                            if (!isInverted) {
                                clone.setRightLegPose(new org.bukkit.util.EulerAngle(Math.toRadians(-legAngle), 0, 0));
                                clone.setLeftLegPose(new org.bukkit.util.EulerAngle(Math.toRadians(legAngle), 0, 0));
                                clone.setRightArmPose(new org.bukkit.util.EulerAngle(Math.toRadians(armAngle - 20), Math.toRadians(10), 0));
                                clone.setLeftArmPose(new org.bukkit.util.EulerAngle(Math.toRadians(-armAngle - 20), Math.toRadians(-10), 0));
                            } else {
                                clone.setRightLegPose(new org.bukkit.util.EulerAngle(Math.toRadians(legAngle), 0, 0));
                                clone.setLeftLegPose(new org.bukkit.util.EulerAngle(Math.toRadians(-legAngle), 0, 0));
                                clone.setRightArmPose(new org.bukkit.util.EulerAngle(Math.toRadians(-armAngle - 20), Math.toRadians(10), 0));
                                clone.setLeftArmPose(new org.bukkit.util.EulerAngle(Math.toRadians(armAngle - 20), Math.toRadians(-10), 0));
                            }

                            if (life % 2 == 0) {
                                clone.getWorld().spawnParticle(Particle.DUST, clone.getLocation().add(0, 0.1, 0), 1, 0.05, 0.02, 0.05, 0.0, new Particle.DustOptions(org.bukkit.Color.AQUA, 0.6f));
                            }
                        }
                    }.runTaskTimer(plugin, 1L, 1L);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private ArmorStand spawnStaggeredAfterimage(Player p, Location loc, float yaw, float pitch) {
        if (loc == null || loc.getWorld() == null) return null;
        UUID uuid = p.getUniqueId();
        Location spawnLoc = loc.clone();
        spawnLoc.setYaw(yaw);
        spawnLoc.setPitch(pitch);

        ArmorStand clone = loc.getWorld().spawn(spawnLoc, ArmorStand.class);
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

        ItemStack[] armor = p.getInventory().getArmorContents();
        if (armor.length > 3 && armor[3] != null && armor[3].getType() != Material.AIR) clone.getEquipment().setHelmet(armor[3].clone());
        if (armor.length > 2 && armor[2] != null) clone.getEquipment().setChestplate(armor[2].clone());
        if (armor.length > 1 && armor[1] != null) clone.getEquipment().setLeggings(armor[1].clone());
        if (armor.length > 0 && armor[0] != null) clone.getEquipment().setBoots(armor[0].clone());

        String handMode = plugin.getConfig().getString("players." + uuid + ".zone.hand_mode", "normal");
        boolean isInverted = handMode.equals("invert");

        clone.setHeadPose(new org.bukkit.util.EulerAngle(Math.toRadians(pitch), 0, 0));

        if (!isInverted) {
            clone.getEquipment().setItemInMainHand(p.getInventory().getItemInMainHand());
            clone.getEquipment().setItemInOffHand(p.getInventory().getItemInOffHand());

            clone.setRightArmPose(new org.bukkit.util.EulerAngle(Math.toRadians(-45), Math.toRadians(25), 0));
            clone.setLeftArmPose(new org.bukkit.util.EulerAngle(Math.toRadians(20), 0, 0));
            clone.setRightLegPose(new org.bukkit.util.EulerAngle(Math.toRadians(-25), 0, 0));
            clone.setLeftLegPose(new org.bukkit.util.EulerAngle(Math.toRadians(25), 0, 0));
        } else {
            clone.getEquipment().setItemInMainHand(p.getInventory().getItemInOffHand());
            clone.getEquipment().setItemInOffHand(p.getInventory().getItemInMainHand());

            clone.setRightArmPose(new org.bukkit.util.EulerAngle(Math.toRadians(20), 0, 0));
            clone.setLeftArmPose(new org.bukkit.util.EulerAngle(Math.toRadians(-45), Math.toRadians(-25), 0));
            clone.setRightLegPose(new org.bukkit.util.EulerAngle(Math.toRadians(25), 0, 0));
            clone.setLeftLegPose(new org.bukkit.util.EulerAngle(Math.toRadians(-25), 0, 0));
        }

        // Spawn visual sweep and sharp wind slice / air cut SFX
        loc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc.clone().add(0, 1.0, 0), 1);
        loc.getWorld().spawnParticle(Particle.CLOUD, loc.clone().add(0, 0.5, 0), 2, 0.1, 0.1, 0.1, 0.02);

        float sweepPitch = 1.4f + (float)(Math.random() * 0.5);
        loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.85f, sweepPitch);
        if (Math.random() < 0.5) {
            loc.getWorld().playSound(loc, Sound.ENTITY_BREEZE_SHOOT, 0.6f, 1.5f + (float)(Math.random() * 0.4));
        }

        return clone;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onArmorStandManipulate(org.bukkit.event.player.PlayerArmorStandManipulateEvent event) {
        ArmorStand stand = event.getRightClicked();
        if (stand.isInvulnerable() || stand.getPersistentDataContainer().has(new org.bukkit.NamespacedKey(plugin, "afterimage"), PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }
}