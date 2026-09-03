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
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
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
import org.bukkit.event.player.PlayerToggleSneakEvent;
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

    private record TendrilParticle(Location location, float size) {}
    private record PendingGuaranteedDamage(LivingEntity target, double healthBefore) {}

    private static final double LIGHT_ZONE_HIT_MULTIPLIER = 1.75;
    private static final double HEAVY_ZONE_HIT_MULTIPLIER = 2.0;
    private static final double ZONE_BLACK_CRIT_MULTIPLIER = 4.0;
    private static final double CINEMATIC_BLACK_CRIT_MULTIPLIER = 2.5;
    private static final double CINEMATIC_BLACK_CRIT_CHANCE = 0.30;
    private static final long CINEMATIC_BLACK_CRIT_WINDOW_MS = 2000L;
    private static final long SKILL_BLACK_CRIT_EXCLUSION_MS = 5000L;
    private static final long COLORED_CRIT_DECAY_INTERVAL_MS = 60000L;
    private static final int DEFAULT_COLORED_CRIT_GUARANTEE_STACKS = 12;
    private static final double CINEMATIC_BLACK_CRIT_KNOCKBACK = 3.2;
    private static final long CINEMATIC_BLACK_CRIT_SLOWNESS_MS = 850L;
    private static final long CINEMATIC_BLACK_CRIT_LAUNCH_TICKS = 17L;
    private static final double ZONE_BLACK_CHARGE_MAX = 100.0;
    private static final double DEFAULT_ZONE_BLACK_DAMAGE_FOR_FULL_CHARGE = 300.0;
    private static final long ZONE_BLACK_CRIT_EXTENSION_MS = 60000L;
    private static final long ZONE_ENTRY_BLACK_CRIT_COOLDOWN_MS = 30000L;
    private static final long ZONE_BLACK_CRIT_COOLDOWN_MS = 60000L;
    private static final double BLACK_CRIT_INSTANT_HEAL = 4.0;
    private static final int BLACK_CRIT_REGEN_TICKS = 100;
    private static final int BLACK_CRIT_STUN_TICKS = 40;
    private static final int BLACK_CRIT_STUN_AMPLIFIER = 255;
    private static final int COMBAT_SLOWNESS_AMPLIFIER = 9;

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
    private final Map<UUID, Long> nextColoredCritDecayAt = new HashMap<>();
    private final Map<UUID, Integer> coloredCritDecayStage = new HashMap<>();
    private final Map<UUID, Long> zoneEndTime = new HashMap<>();
    private final Map<UUID, Long> zoneBlackCritCooldown = new HashMap<>();
    private final Map<UUID, Double> zoneBlackCharge = new HashMap<>();
    private final Set<UUID> cinematicBlackCritFollowUps = new HashSet<>();
    private final Map<UUID, Long> cinematicBlackCritFollowUpEnd = new HashMap<>();
    private final Map<UUID, Long> skillUseLockedUntil = new HashMap<>();
    private final Map<UUID, Long> blackCritBlockedUntil = new HashMap<>();
    private final Set<UUID> forcedBlackCritTests = new HashSet<>();
    private final Set<UUID> forcedDoubleBlackCritTests = new HashSet<>();
    private final Map<EntityDamageByEntityEvent, PendingGuaranteedDamage> guaranteedDamageEvents = new HashMap<>();
    private boolean essentialsInteropWarningLogged;

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
    private final Map<UUID, Long> lightDodgeAttemptCD = new HashMap<>();
    private final Map<UUID, Long> lightDodgeWindowEnd = new HashMap<>();
    private final Map<UUID, UUID> perfectDodgeCounterTargets = new HashMap<>();
    private final Map<UUID, Long> perfectDodgeCounterEnd = new HashMap<>();
    private final Map<UUID, Long> perfectDodgeDamageSafetyUntil = new HashMap<>();
    private final Set<UUID> activeReversals = new HashSet<>();
    private final Map<UUID, Double> lightDmgTaken = new HashMap<>();
    private final Map<UUID, Map<UUID, Integer>> lightHits = new HashMap<>();
    private final Map<UUID, Map<UUID, Integer>> lightDebuffs = new HashMap<>();
    private final Map<UUID, Map<UUID, TextDisplay>> lightStackDisplays = new HashMap<>();
    private final Map<UUID, Double> origAtkSpeed = new HashMap<>();
    private final Map<UUID, Long> afterimageCD = new HashMap<>();
    private final Map<UUID, Float> afterimageOriginalWalkSpeed = new HashMap<>();
    public final Set<UUID> afterimageHidden = new HashSet<>();
    public final Set<UUID> afterimageDamageLock = new HashSet<>();

    public final NamespacedKey ZONE_ATK_SPEED_KEY;
    public final NamespacedKey LIGHT_DEBUFF_ATK_KEY;
    public final NamespacedKey LIGHT_DEBUFF_SPD_KEY;
    private final NamespacedKey BLACK_CRIT_STUN_MOVE_KEY;
    private final NamespacedKey BLACK_CRIT_STUN_JUMP_KEY;
    private final NamespacedKey BLACK_CRIT_STUN_KNOCKBACK_KEY;

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
        this.BLACK_CRIT_STUN_MOVE_KEY = new NamespacedKey(plugin, "black_crit_stun_move");
        this.BLACK_CRIT_STUN_JUMP_KEY = new NamespacedKey(plugin, "black_crit_stun_jump");
        this.BLACK_CRIT_STUN_KNOCKBACK_KEY = new NamespacedKey(plugin, "black_crit_stun_knockback");
        startCritDecayTask();
        startLightStackDisplayTask();
    }

    public void revealPlayer(Player p) {
        if (p == null) return;
        if (afterimageHidden.remove(p.getUniqueId())) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other != p && other.isOnline()) other.showPlayer(plugin, p);
            }
        }
        Float originalWalkSpeed = afterimageOriginalWalkSpeed.remove(p.getUniqueId());
        if (originalWalkSpeed != null) p.setWalkSpeed(originalWalkSpeed);
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

        removeAttributeModifier(attr, ZONE_ATK_SPEED_KEY);

        long now = System.currentTimeMillis();
        boolean inZone = zoneEndTime.containsKey(uuid) && zoneEndTime.get(uuid) > now;
        if (!inZone) {
            return;
        }

        String t1 = plugin.getData().getString("players." + uuid + ".zone.tier1", "none");
        String t2 = plugin.getData().getString("players." + uuid + ".zone.tier2", "none");
        int stack = heavyStacks.getOrDefault(uuid, 0);

        double scalarMultiplier = 1.0;
        if (t1.equals("heavy")) {
            scalarMultiplier *= 0.90; // -10% Attack Speed
        } else if (t1.equals("light")) {
            if (!t2.equals("heavy")) {
                double lightBonus = isOffhandShield(p)
                        ? plugin.getConfig().getDouble("combat.light-offhand.shield-attack-speed-bonus", 0.05)
                        : 0.15;
                scalarMultiplier *= (1.0 + lightBonus);
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
        String t1 = plugin.getData().getString("players." + uuid + ".zone.tier1", "none");
        String t2 = plugin.getData().getString("players." + uuid + ".zone.tier2", "none");
        String t3 = plugin.getData().getString("players." + uuid + ".zone.tier3", "none");

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
        recordPlayerCritActivity(uuid, System.currentTimeMillis());
        switch (tier.toLowerCase()) {
            case "yellow" -> yellowStacks.put(uuid, amount);
            case "orange" -> orangeStacks.put(uuid, amount);
            case "red" -> redStacks.put(uuid, amount);
        }
    }

    public void skipToTier(Player p, String tier) {
        UUID uuid = p.getUniqueId();
        recordPlayerCritActivity(uuid, System.currentTimeMillis());
        switch (tier.toLowerCase()) {
            case "yellow" -> {
                yellowStacks.put(uuid, 7);
                if (plugin.getData().getInt("players." + uuid + ".kills", 0) < 30) {
                    plugin.getData().set("players." + uuid + ".kills", 30);
                    plugin.saveData();
                }
            }
            case "orange" -> {
                yellowStacks.put(uuid, 7);
                orangeStacks.put(uuid, 7);
                if (plugin.getData().getInt("players." + uuid + ".kills", 0) < 60) {
                    plugin.getData().set("players." + uuid + ".kills", 60);
                    plugin.saveData();
                }
            }
            case "red" -> {
                yellowStacks.put(uuid, 7);
                orangeStacks.put(uuid, 7);
                redStacks.put(uuid, 6);
                if (plugin.getData().getInt("players." + uuid + ".kills", 0) < 100) {
                    plugin.getData().set("players." + uuid + ".kills", 100);
                    plugin.saveData();
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
            world.spawnParticle(Particle.DUST, targetLoc, 8, 0.12, 0.12, 0.12, 0.0,
                    new Particle.DustOptions(Color.WHITE, 3.0f));
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
        zoneBlackCharge.put(uuid, 0.0);
        zoneBlackCritCooldown.put(uuid, now + ZONE_ENTRY_BLACK_CRIT_COOLDOWN_MS);
        yellowStacks.put(uuid, 0);
        orangeStacks.put(uuid, 0);
        redStacks.put(uuid, 0);
        clearColoredCritDecay(uuid);

        String t1 = plugin.getData().getString("players." + uuid + ".zone.tier1", "none");
        if (t1.equals("heavy")) p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int)(durationMs / 50L), 0));
        updateBaseAttackSpeed(p, t1, 0);

        heavyStacks.put(uuid, 0);
        heavyHitCount.put(uuid, 0);
        lightDmgTaken.put(uuid, 0.0);
        lightHits.remove(uuid);
        clearLightDebuffs(uuid);

        playBlackFlashVFX(p.getLocation().add(0, 1, 0));
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "☠ BLACK CRIT - THE ZONE ☠"));
    }

    public void endTheZone(Player p) {
        if (p == null) return;
        UUID uuid = p.getUniqueId();
        zoneEndTime.remove(uuid);
        zoneBlackCharge.remove(uuid);
        clearSecondBlackCrit(uuid);
        String t1 = plugin.getData().getString("players." + uuid + ".zone.tier1", "none");
        updateBaseAttackSpeed(p, t1, 0);
        p.removePotionEffect(PotionEffectType.SLOWNESS);
        heavyStacks.remove(uuid);
        heavyHitCount.remove(uuid);
        lightDmgTaken.remove(uuid);
        lightHits.remove(uuid);
        clearPerfectDodgeState(uuid);
        clearLightDebuffs(uuid);
        revealPlayer(p);
        p.sendMessage(plugin.PREFIX + ChatColor.GRAY + "The Zone has ended.");
    }

    public void armBlackCritTest(Player player, boolean doubleCrit) {
        UUID playerId = player.getUniqueId();
        clearSecondBlackCrit(playerId);
        forcedBlackCritTests.add(playerId);
        if (doubleCrit) {
            forcedDoubleBlackCritTests.add(playerId);
        } else {
            forcedDoubleBlackCritTests.remove(playerId);
        }
    }

    private String getZoneBlackChargeDisplay(UUID playerId) {
        double charge = Math.max(0.0, Math.min(ZONE_BLACK_CHARGE_MAX,
                zoneBlackCharge.getOrDefault(playerId, 0.0)));
        int filledSegments = Math.min(10, (int) Math.floor(charge / 10.0));
        String liveCharge = String.format(java.util.Locale.US, "%.1f%%", charge);
        long cooldownLeft = Math.max(0L,
                (zoneBlackCritCooldown.getOrDefault(playerId, 0L) - System.currentTimeMillis() + 999L) / 1000L);
        String chanceOrCooldown = cooldownLeft > 0
                ? ChatColor.RED + "CD " + cooldownLeft + "s"
                : ChatColor.LIGHT_PURPLE + liveCharge;
        return ChatColor.DARK_GRAY + " [BC "
                + ChatColor.DARK_PURPLE + "|".repeat(filledSegments)
                + ChatColor.GRAY + "|".repeat(10 - filledSegments) + " "
                + chanceOrCooldown + ChatColor.DARK_GRAY + "]";
    }

    private void recordPlayerCritActivity(UUID playerId, long now) {
        lastValidHitTime.put(playerId, now);
        nextColoredCritDecayAt.put(playerId, now + COLORED_CRIT_DECAY_INTERVAL_MS);
        coloredCritDecayStage.put(playerId, 1);
    }

    private int getColoredCritStackTotal(UUID playerId) {
        return Math.max(0, yellowStacks.getOrDefault(playerId, 0))
                + Math.max(0, orangeStacks.getOrDefault(playerId, 0))
                + Math.max(0, redStacks.getOrDefault(playerId, 0));
    }

    private void subtractColoredCritStacks(Map<UUID, Integer> stacks, UUID playerId,
                                           int[] remainingLoss) {
        if (remainingLoss[0] <= 0) return;
        int current = Math.max(0, stacks.getOrDefault(playerId, 0));
        int removed = Math.min(current, remainingLoss[0]);
        int updated = current - removed;
        remainingLoss[0] -= removed;
        if (updated > 0) stacks.put(playerId, updated);
        else stacks.remove(playerId);
    }

    private void decayColoredCritStacks(UUID playerId, int amount) {
        int[] remainingLoss = {Math.max(0, amount)};
        subtractColoredCritStacks(redStacks, playerId, remainingLoss);
        subtractColoredCritStacks(orangeStacks, playerId, remainingLoss);
        subtractColoredCritStacks(yellowStacks, playerId, remainingLoss);
    }

    private void clearColoredCritDecay(UUID playerId) {
        lastValidHitTime.remove(playerId);
        nextColoredCritDecayAt.remove(playerId);
        coloredCritDecayStage.remove(playerId);
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

                    if (zoneEndTime.containsKey(uuid) && !inZone) {
                        zoneEndTime.remove(uuid);
                        zoneBlackCharge.remove(uuid);
                        clearSecondBlackCrit(uuid);
                        String t1 = plugin.getData().getString("players." + uuid + ".zone.tier1", "none");
                        updateBaseAttackSpeed(p, t1, 0);
                        p.removePotionEffect(PotionEffectType.SLOWNESS);
                        heavyStacks.remove(uuid); heavyHitCount.remove(uuid);
                        lightDmgTaken.remove(uuid); lightHits.remove(uuid);
                        clearLightDebuffs(uuid);

                        if (plugin.getData().getBoolean("players." + uuid + ".alerts", true)) {
                            p.sendMessage(plugin.PREFIX + ChatColor.GRAY + "The Zone has faded...");
                        }
                    }

                    if (!inZone && getColoredCritStackTotal(uuid) > 0) {
                        long nextDecay = nextColoredCritDecayAt.computeIfAbsent(uuid, key ->
                                lastValidHitTime.getOrDefault(uuid, now) + COLORED_CRIT_DECAY_INTERVAL_MS);
                        int decayStage = Math.max(1, coloredCritDecayStage.getOrDefault(uuid, 1));
                        while (now >= nextDecay && getColoredCritStackTotal(uuid) > 0) {
                            decayColoredCritStacks(uuid, decayStage);
                            decayStage++;
                            nextDecay += COLORED_CRIT_DECAY_INTERVAL_MS;
                        }
                        if (getColoredCritStackTotal(uuid) > 0) {
                            nextColoredCritDecayAt.put(uuid, nextDecay);
                            coloredCritDecayStage.put(uuid, decayStage);
                        } else {
                            clearColoredCritDecay(uuid);
                        }
                    }

                    if (inZone) updateZoneAttackSpeed(p);
                    if (plugin.getData().getBoolean("players." + uuid + ".alerts", true)) {
                        if (inZone) {
                            long remainingSec = Math.max(0, (zoneEndTime.get(uuid) - now) / 1000);
                            String extraText = "";
                            String t1 = plugin.getData().getString("players." + uuid + ".zone.tier1", "none");
                            String t2 = plugin.getData().getString("players." + uuid + ".zone.tier2", "none");
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
                            if (t1.equals("light")) {
                                long dodgeReadyAt = lightDodgeCD.getOrDefault(uuid, 0L);
                                if (now < dodgeReadyAt) {
                                    long dodgeLeft = (dodgeReadyAt - now + 999L) / 1000L;
                                    extraText += ChatColor.AQUA + " [PD: " + dodgeLeft + "s]";
                                } else {
                                    extraText += ChatColor.GREEN + " [PD: READY]";
                                }
                            }
                            extraText += getZoneBlackChargeDisplay(uuid);
                            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ChatColor.DARK_GRAY + "【 " + ChatColor.DARK_PURPLE + ChatColor.BOLD + "THE ZONE" + ChatColor.DARK_GRAY + " 】 " + ChatColor.LIGHT_PURPLE + remainingSec + "s" + extraText));
                        } else if (getColoredCritStackTotal(uuid) > 0) {
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
        Map<UUID, Integer> targetDebuffs = lightDebuffs.remove(attackerId);
        if (targetDebuffs != null) {
            for (Map.Entry<UUID, Integer> entry : targetDebuffs.entrySet()) {
                org.bukkit.entity.Entity entity = Bukkit.getEntity(entry.getKey());
                if (entity instanceof LivingEntity target) {
                    if (target.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) {
                        removeAttributeModifier(target.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED),
                                getLightDebuffSpeedKey(attackerId));
                    }
                    if (target.getAttribute(Attribute.GENERIC_ATTACK_SPEED) != null) {
                        removeAttributeModifier(target.getAttribute(Attribute.GENERIC_ATTACK_SPEED),
                                getLightDebuffAttackKey(attackerId));
                    }
                }
                origAtkSpeed.remove(entry.getKey());
            }
        }
        removeLightStackDisplays(attackerId);
    }

    private void clearLightEffectsOnTarget(Player target) {
        UUID targetId = target.getUniqueId();
        for (Map.Entry<UUID, Map<UUID, Integer>> attackerEntry : lightDebuffs.entrySet()) {
            if (attackerEntry.getValue().remove(targetId) == null) continue;
            UUID attackerId = attackerEntry.getKey();
            if (target.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) {
                removeAttributeModifier(target.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED),
                        getLightDebuffSpeedKey(attackerId));
            }
            if (target.getAttribute(Attribute.GENERIC_ATTACK_SPEED) != null) {
                removeAttributeModifier(target.getAttribute(Attribute.GENERIC_ATTACK_SPEED),
                        getLightDebuffAttackKey(attackerId));
            }
        }
        // Remove modifiers created by older builds as well.
        removeAttributeModifier(target.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED), LIGHT_DEBUFF_SPD_KEY);
        removeAttributeModifier(target.getAttribute(Attribute.GENERIC_ATTACK_SPEED), LIGHT_DEBUFF_ATK_KEY);
        for (Map<UUID, Integer> targetHits : lightHits.values()) targetHits.remove(targetId);
        for (Map<UUID, TextDisplay> displays : lightStackDisplays.values()) {
            TextDisplay display = displays.remove(targetId);
            if (display != null && display.isValid()) display.remove();
        }
        origAtkSpeed.remove(targetId);
    }

    private NamespacedKey getLightDebuffAttackKey(UUID attackerId) {
        return new NamespacedKey(plugin, "light_debuff_atk_" + attackerId.toString().replace("-", ""));
    }

    private NamespacedKey getLightDebuffSpeedKey(UUID attackerId) {
        return new NamespacedKey(plugin, "light_debuff_spd_" + attackerId.toString().replace("-", ""));
    }

    private void applyLightTier3Debuff(Player attacker, Player target, int stacks) {
        if (stacks <= 0) return;
        double maxEffect = getLightTier3MaxEffect(attacker);
        double currentEffect = Math.min(maxEffect, stacks * (maxEffect / 5.0));
        setAttributeModifier(target.getAttribute(Attribute.GENERIC_ATTACK_SPEED),
                getLightDebuffAttackKey(attacker.getUniqueId()), -currentEffect,
                AttributeModifier.Operation.ADD_SCALAR);
    }

    private boolean shouldShowLightStackDisplay(Player viewer, Player target) {
        if (viewer == null || target == null || !viewer.isOnline() || !target.isOnline()
                || target.isDead() || !viewer.getWorld().equals(target.getWorld())) return false;
        if (target.isSneaking() || target.getGameMode() == org.bukkit.GameMode.SPECTATOR
                || target.isInvisible() || target.hasPotionEffect(PotionEffectType.INVISIBILITY)
                || !viewer.canSee(target)) return false;
        if (viewer.getLocation().distanceSquared(target.getLocation()) > 64.0 * 64.0) return false;

        org.bukkit.scoreboard.Scoreboard scoreboard = viewer.getScoreboard();
        org.bukkit.scoreboard.Team targetTeam = scoreboard.getEntryTeam(target.getName());
        if (targetTeam == null) return true;

        org.bukkit.scoreboard.Team.OptionStatus visibility = targetTeam.getOption(
                org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY);
        org.bukkit.scoreboard.Team viewerTeam = scoreboard.getEntryTeam(viewer.getName());
        boolean sameTeam = targetTeam.equals(viewerTeam);
        return switch (visibility) {
            case ALWAYS -> true;
            case NEVER -> false;
            case FOR_OWN_TEAM -> sameTeam;
            case FOR_OTHER_TEAMS -> !sameTeam;
        };
    }

    private void updateLightStackDisplayVisibility(Player viewer, Player target,
                                                   TextDisplay display) {
        if (shouldShowLightStackDisplay(viewer, target)) {
            viewer.showEntity(plugin, display);
        } else {
            viewer.hideEntity(plugin, display);
        }
    }

    private void updateLightStackDisplay(Player viewer, Player target, int stacks) {
        if (!plugin.getConfig().getBoolean("combat.light-stack-display.enabled", true) || stacks <= 0) return;

        Map<UUID, TextDisplay> viewerDisplays = lightStackDisplays.computeIfAbsent(
                viewer.getUniqueId(), key -> new HashMap<>());
        TextDisplay display = viewerDisplays.get(target.getUniqueId());
        if (display == null || !display.isValid()) {
            double offset = plugin.getConfig().getDouble("combat.light-stack-display.height-offset", 0.65);
            Location location = target.getLocation().add(0, target.getHeight() + offset, 0);
            display = target.getWorld().spawn(location, TextDisplay.class, spawned -> {
                spawned.setVisibleByDefault(false);
                spawned.setPersistent(false);
                spawned.setInvulnerable(true);
                spawned.setGravity(false);
                spawned.setBillboard(Display.Billboard.CENTER);
                spawned.setAlignment(TextDisplay.TextAlignment.CENTER);
                spawned.setSeeThrough(false);
                spawned.setShadowed(true);
                spawned.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                spawned.setTeleportDuration(2);
            });
            viewerDisplays.put(target.getUniqueId(), display);
        }
        display.setText(ChatColor.AQUA + "" + ChatColor.BOLD + stacks);
        updateLightStackDisplayVisibility(viewer, target, display);
    }

    private void removeLightStackDisplays(UUID viewerId) {
        Map<UUID, TextDisplay> displays = lightStackDisplays.remove(viewerId);
        if (displays == null) return;
        for (TextDisplay display : displays.values()) {
            if (display != null && display.isValid()) display.remove();
        }
    }

    private void startLightStackDisplayTask() {
        long updateTicks = Math.max(1L, plugin.getConfig().getLong(
                "combat.light-stack-display.update-ticks", 2L));
        new BukkitRunnable() {
            @Override
            public void run() {
                Iterator<Map.Entry<UUID, Map<UUID, TextDisplay>>> viewerIterator =
                        lightStackDisplays.entrySet().iterator();
                while (viewerIterator.hasNext()) {
                    Map.Entry<UUID, Map<UUID, TextDisplay>> viewerEntry = viewerIterator.next();
                    UUID viewerId = viewerEntry.getKey();
                    Player viewer = Bukkit.getPlayer(viewerId);
                    if (viewer == null || !viewer.isOnline()
                            || zoneEndTime.getOrDefault(viewerId, 0L) <= System.currentTimeMillis()) {
                        for (TextDisplay display : viewerEntry.getValue().values()) {
                            if (display != null && display.isValid()) display.remove();
                        }
                        viewerIterator.remove();
                        continue;
                    }

                    Iterator<Map.Entry<UUID, TextDisplay>> targetIterator =
                            viewerEntry.getValue().entrySet().iterator();
                    while (targetIterator.hasNext()) {
                        Map.Entry<UUID, TextDisplay> targetEntry = targetIterator.next();
                        Player target = Bukkit.getPlayer(targetEntry.getKey());
                        TextDisplay display = targetEntry.getValue();
                        if (target == null || !target.isOnline() || target.isDead()
                                || display == null || !display.isValid()
                                || !target.getWorld().equals(viewer.getWorld())) {
                            if (display != null && display.isValid()) display.remove();
                            Map<UUID, Integer> stacks = lightDebuffs.get(viewerId);
                            if (stacks != null) stacks.remove(targetEntry.getKey());
                            Map<UUID, Integer> hits = lightHits.get(viewerId);
                            if (hits != null) hits.remove(targetEntry.getKey());
                            targetIterator.remove();
                            continue;
                        }

                        double offset = plugin.getConfig().getDouble(
                                "combat.light-stack-display.height-offset", 0.65);
                        display.teleport(target.getLocation().add(0, target.getHeight() + offset, 0));
                        updateLightStackDisplayVisibility(viewer, target, display);
                    }
                    if (viewerEntry.getValue().isEmpty()) viewerIterator.remove();
                }
            }
        }.runTaskTimer(plugin, updateTicks, updateTicks);
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

    private boolean isOffhandShield(Player player) {
        return player.getInventory().getItemInOffHand().getType() == Material.SHIELD;
    }

    private boolean isOffhandSword(Player player) {
        return player.getInventory().getItemInOffHand().getType().name().endsWith("_SWORD");
    }

    private double getOffhandSwordScale(Player player) {
        String material = player.getInventory().getItemInOffHand().getType().name();
        String configMaterial = switch (material) {
            case "WOODEN_SWORD" -> "wooden";
            case "GOLDEN_SWORD" -> "golden";
            case "STONE_SWORD" -> "stone";
            case "IRON_SWORD" -> "iron";
            case "NETHERITE_SWORD" -> "netherite";
            case "DIAMOND_SWORD" -> "diamond";
            default -> null;
        };
        if (configMaterial == null) return 0.0;

        double fallback = switch (configMaterial) {
            case "wooden" -> 0.25;
            case "golden" -> 0.35;
            case "stone" -> 0.50;
            case "iron" -> 0.75;
            case "netherite" -> 1.00;
            default -> 1.00;
        };
        return Math.max(0.0, Math.min(1.0, plugin.getConfig().getDouble(
                "combat.light-offhand.sword-material-scale." + configMaterial, fallback)));
    }

    private double getReversalTrueDamage(Player player) {
        if (isOffhandShield(player)) {
            return Math.max(0.0, plugin.getConfig().getDouble(
                    "combat.light-offhand.shield-reversal-true-damage", 6.0));
        }
        if (isOffhandSword(player)) {
            double normalDamage = Math.max(0.0, plugin.getConfig().getDouble(
                    "combat.perfect-dodge.total-true-damage", 10.0));
            double maximumDamage = Math.max(normalDamage, plugin.getConfig().getDouble(
                    "combat.light-offhand.sword-max-reversal-true-damage", 14.0));
            return normalDamage + (maximumDamage - normalDamage) * getOffhandSwordScale(player);
        }
        return Math.max(0.0, plugin.getConfig().getDouble(
                "combat.perfect-dodge.total-true-damage", 10.0));
    }

    private double getLightTier3MaxEffect(Player player) {
        if (isOffhandShield(player)) {
            return Math.max(0.0, plugin.getConfig().getDouble(
                    "combat.light-offhand.shield-tier3-max-effect", 0.10));
        }
        if (isOffhandSword(player)) {
            double maximumEffect = Math.max(0.15, plugin.getConfig().getDouble(
                    "combat.light-offhand.sword-max-tier3-effect", 0.25));
            return 0.15 + (maximumEffect - 0.15) * getOffhandSwordScale(player);
        }
        return 0.15;
    }

    @EventHandler(ignoreCancelled = true)
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        boolean inZone = zoneEndTime.getOrDefault(uuid, 0L) > now;
        String tier1 = plugin.getData().getString("players." + uuid + ".zone.tier1", "none");
        if (!inZone || !"light".equals(tier1)) return;
        if (isSkillUseLocked(player, now)) return;
        long dodgeReadyAt = lightDodgeCD.getOrDefault(uuid, 0L);
        if (now < dodgeReadyAt) {
            if (plugin.getData().getBoolean("players." + uuid + ".alerts", true)) {
                long remaining = (dodgeReadyAt - now + 999L) / 1000L;
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText(ChatColor.RED + "§l⚠ Perfect Dodge: " + remaining + "s"));
            }
            return;
        }
        if (now < lightDodgeAttemptCD.getOrDefault(uuid, 0L) || activeReversals.contains(uuid)) return;

        long windowMs = Math.max(100L, plugin.getConfig().getLong(
                "combat.perfect-dodge.timing-window-ms", 500L));
        long missedCooldownMs = Math.max(windowMs, plugin.getConfig().getLong(
                "combat.perfect-dodge.missed-attempt-cooldown-ms", 3000L));
        lightDodgeWindowEnd.put(uuid, now + windowMs);
        lightDodgeAttemptCD.put(uuid, now + missedCooldownMs);
        recordSkillUse(player, now);

        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0, 0.15, 0),
                5, 0.2, 0.05, 0.2, 0.02);
        player.playSound(player.getLocation(), Sound.ENTITY_BREEZE_SLIDE, 0.35f, 1.8f);
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
                String t2 = plugin.getData().getString("players." + uuid + ".zone.tier2", "none");
                if (t2.equals("heavy")) {
                    long lastHit = lastAttackTime.getOrDefault(uuid, 0L);
                    if (now - lastHit > 250) {
                        int cur = heavyStacks.getOrDefault(uuid, 0);
                        if (cur > 0) {
                            int newStack = cur - 1;
                            heavyStacks.put(uuid, newStack);
                            String t1 = plugin.getData().getString("players." + uuid + ".zone.tier1", "none");
                            updateBaseAttackSpeed(p, t1, newStack);
                            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.4f, 1.5f);
                            lastAttackTime.put(uuid, now);
                        }
                    }
                }
            }
        }
    }

    private void triggerPerfectDodge(EntityDamageByEntityEvent event, Player player,
                                     LivingEntity source, long now, boolean alertsEnabled) {
        UUID playerId = player.getUniqueId();
        clearPerfectDodgeDamageState(player, source);
        event.setCancelled(true);
        lightDodgeWindowEnd.remove(playerId);

        long cooldownSeconds = Math.max(1L, plugin.getConfig().getLong(
                "combat.perfect-dodge.cooldown-seconds", 30L));
        long counterWindowMs = Math.max(100L, plugin.getConfig().getLong(
                "combat.perfect-dodge.counter-window-ms", 500L));
        lightDodgeCD.put(playerId, now + cooldownSeconds * 1000L);
        markPerfectDodgeDamageSafety(player, source, now + 6000L);

        if (event.getDamager() instanceof Projectile projectile) {
            projectile.remove();
        }

        // Never teleport while Minecraft is still resolving the attack that was
        // dodged. Doing so can leave the server's damage transaction in a bad state.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.isDead() || !source.isValid() || source.isDead()
                    || !player.getWorld().equals(source.getWorld())) return;

            Location behind = findSafeLocationBehind(source, player);
            if (behind != null) {
                player.teleport(behind,
                        org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                clearEssentialsTeleportInvulnerability(player);
            }
            clearPerfectDodgeDamageState(player, source);
            Bukkit.getScheduler().runTask(plugin, () -> {
                clearEssentialsTeleportInvulnerability(player);
                clearPerfectDodgeDamageState(player, source);
            });

            long counterStart = System.currentTimeMillis();
            perfectDodgeCounterTargets.put(playerId, source.getUniqueId());
            perfectDodgeCounterEnd.put(playerId, counterStart + counterWindowMs);

            player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0, 0.8, 0),
                    30, 0.35, 0.6, 0.35, 0.15);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.5f);
            if (alertsEnabled) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText(ChatColor.AQUA + "§lPERFECT DODGE! §fCounter now!"));
            }

            long counterTicks = Math.max(2L, (counterWindowMs + 49L) / 50L);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!source.getUniqueId().equals(perfectDodgeCounterTargets.get(playerId))) return;
                if (System.currentTimeMillis() < perfectDodgeCounterEnd.getOrDefault(playerId, 0L)) return;

                perfectDodgeCounterTargets.remove(playerId);
                perfectDodgeCounterEnd.remove(playerId);
                if (!source.isValid() || source.isDead()) return;
                source.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 3));
                source.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20, 0));
            }, counterTicks + 1L);
        });
    }

    private void markPerfectDodgeDamageSafety(Player player, LivingEntity source, long until) {
        perfectDodgeDamageSafetyUntil.put(player.getUniqueId(), until);
        perfectDodgeDamageSafetyUntil.put(source.getUniqueId(), until);
    }

    private boolean hasPerfectDodgeDamageSafety(UUID entityId, long now) {
        long until = perfectDodgeDamageSafetyUntil.getOrDefault(entityId, 0L);
        if (until > now) return true;
        perfectDodgeDamageSafetyUntil.remove(entityId);
        return false;
    }

    private void clearPerfectDodgeDamageState(Player player, LivingEntity source) {
        clearBlackCritStun(player);
        clearBlackCritStun(source);
        clearDamageImmunity(player);
        clearDamageImmunity(source);
    }

    private void clearEssentialsTeleportInvulnerability(Player player) {
        if (player == null || !player.isOnline()) return;
        org.bukkit.plugin.Plugin essentials = Bukkit.getPluginManager().getPlugin("Essentials");
        if (essentials == null || !essentials.isEnabled()) return;

        try {
            java.lang.reflect.Method getUser = essentials.getClass().getMethod("getUser", Object.class);
            Object essentialsUser = getUser.invoke(essentials, player);
            if (essentialsUser == null) return;
            essentialsUser.getClass().getMethod("resetInvulnerabilityAfterTeleport")
                    .invoke(essentialsUser);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (!essentialsInteropWarningLogged) {
                essentialsInteropWarningLogged = true;
                plugin.getLogger().warning("Could not clear EssentialsX teleport invulnerability after "
                        + "Perfect Dodge: " + exception.getMessage());
            }
        }
    }

    private Location findSafeLocationBehind(LivingEntity source, Player player) {
        if (!source.getWorld().equals(player.getWorld())) return null;

        Vector direction = player.getLocation().toVector().subtract(source.getLocation().toVector()).setY(0);
        if (direction.lengthSquared() < 0.0001) direction = source.getLocation().getDirection().setY(0);
        if (direction.lengthSquared() < 0.0001) direction = new Vector(0, 0, 1);
        direction.normalize();

        for (double distance : new double[]{1.8, 1.3, 0.9}) {
            Location behind = source.getLocation().clone().subtract(direction.clone().multiply(distance));
            behind.setYaw(source.getLocation().getYaw());
            behind.setPitch(0f);
            behind.setDirection(source.getLocation().toVector().subtract(behind.toVector()));
            if (behind.getBlock().isPassable() && behind.clone().add(0, 1, 0).getBlock().isPassable()) {
                return behind;
            }
        }
        return null;
    }

    private boolean tryStartReversal(Player player, LivingEntity target,
                                     EntityDamageByEntityEvent event, long hitTime) {
        UUID playerId = player.getUniqueId();
        if (!target.getUniqueId().equals(perfectDodgeCounterTargets.get(playerId))) return false;
        if (hitTime > perfectDodgeCounterEnd.getOrDefault(playerId, 0L)) return false;
        if (activeReversals.contains(playerId)) return false;

        // Reversal replaces the triggering counter's normal damage so the complete
        // sequence always has one predictable true-damage budget.
        event.setCancelled(true);
        perfectDodgeCounterTargets.remove(playerId);
        perfectDodgeCounterEnd.remove(playerId);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !target.isValid() || target.isDead()) {
                if (target.isValid() && !target.isDead()) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 3));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20, 0));
                }
                return;
            }
            startReversal(player, target);
        });
        return true;
    }

    private void startReversal(Player player, LivingEntity target) {
        UUID playerId = player.getUniqueId();
        if (!activeReversals.add(playerId)) return;

        // A reversal uses direct health damage and must never leave vanilla hurt
        // immunity behind on either participant.
        clearDamageImmunity(player);
        clearDamageImmunity(target);

        int risingTicks = Math.max(4, plugin.getConfig().getInt(
                "combat.perfect-dodge.rising-duration-ticks", 8));
        double uppercutHorizontal = plugin.getConfig().getDouble(
                "combat.perfect-dodge.uppercut-horizontal-speed", 0.18);
        double uppercutVertical = plugin.getConfig().getDouble(
                "combat.perfect-dodge.uppercut-vertical-speed", 0.85);
        double slamHorizontal = plugin.getConfig().getDouble(
                "combat.perfect-dodge.slam-horizontal-speed", 0.9);
        double slamVertical = plugin.getConfig().getDouble(
                "combat.perfect-dodge.slam-vertical-speed", -1.35);
        double totalTrueDamage = getReversalTrueDamage(player);
        double risingShare = Math.max(0.0, Math.min(1.0, plugin.getConfig().getDouble(
                "combat.perfect-dodge.rising-damage-share", 0.6)));
        int risingPulses = (risingTicks + 1) / 2;
        double risingDamage = risingPulses == 0 ? 0.0 : totalTrueDamage * risingShare / risingPulses;
        double slamDamage = totalTrueDamage * (1.0 - risingShare);

        long reversalSlowDurationMs = Math.max(2500L, (risingTicks + 30L) * 50L);
        applyTemporarySlowness(player, reversalSlowDurationMs);
        applyTemporarySlowness(target, reversalSlowDurationMs);

        Vector forward = target.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
        if (forward.lengthSquared() < 0.0001) forward = player.getLocation().getDirection().setY(0);
        if (forward.lengthSquared() < 0.0001) forward = new Vector(0, 0, 1);
        forward.normalize();
        final Vector slamDirection = forward.clone();

        target.setVelocity(forward.clone().multiply(uppercutHorizontal).setY(uppercutVertical));
        player.setVelocity(forward.clone().multiply(uppercutHorizontal * 0.8).setY(uppercutVertical * 0.9));
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.7f);

        new BukkitRunnable() {
            int tick;
            boolean slammed;

            @Override
            public void run() {
                if (!canContinueReversal(player, target)) {
                    finishReversal(playerId, player, target);
                    cancel();
                    return;
                }

                clearDamageImmunity(player);
                clearDamageImmunity(target);

                if (tick < risingTicks) {
                    Vector chase = target.getLocation().toVector().subtract(player.getLocation().toVector());
                    if (chase.lengthSquared() > 1.44) {
                        double chaseSpeed = Math.min(0.48, 0.18 + Math.sqrt(chase.lengthSquared()) * 0.05);
                        Vector chaseVelocity = chase.normalize().multiply(chaseSpeed);
                        chaseVelocity.setY(Math.max(player.getVelocity().getY(), chaseVelocity.getY()));
                        player.setVelocity(chaseVelocity);
                    }

                    Location targetCenter = target.getLocation().add(0, target.getHeight() * 0.55, 0);
                    Location playerCenter = player.getLocation().add(0, 0.9, 0);
                    Location sweepPoint = targetCenter.clone().add(playerCenter.toVector()
                            .subtract(targetCenter.toVector()).multiply(0.25));
                    target.getWorld().spawnParticle(Particle.SWEEP_ATTACK, sweepPoint, 1);
                    target.getWorld().spawnParticle(Particle.CLOUD, sweepPoint, 3,
                            0.15, 0.15, 0.15, 0.02);

                    if (tick % 2 == 0 && risingDamage > 0.0) {
                        dealReversalDamage(player, target, risingDamage);
                        Vector continuedRise = target.getVelocity();
                        continuedRise.setY(Math.max(continuedRise.getY(), 0.25));
                        target.setVelocity(continuedRise);
                        target.getWorld().playSound(target.getLocation(),
                                Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.55f, 1.2f + tick * 0.04f);
                    }
                } else {
                    if (!slammed) {
                        slammed = true;
                        target.setVelocity(slamDirection.clone().multiply(slamHorizontal).setY(slamVertical));
                        target.getWorld().spawnParticle(Particle.SWEEP_ATTACK,
                                target.getLocation().add(0, 1, 0), 3);
                        target.getWorld().playSound(target.getLocation(), Sound.ITEM_MACE_SMASH_AIR, 1f, 0.7f);
                    }

                    player.setVelocity(new Vector(0, 0.055, 0));
                    player.setFallDistance(0f);

                    boolean impacted = tick >= risingTicks + 2 && isNearGround(target, 0.55);
                    boolean timedOut = tick >= risingTicks + 24;
                    if (impacted || timedOut) {
                        if (slamDamage > 0.0) dealReversalDamage(player, target, slamDamage);
                        playReversalImpact(target.getLocation());
                        finishReversal(playerId, player, target);
                        cancel();
                        return;
                    }
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private boolean canContinueReversal(Player player, LivingEntity target) {
        return player.isOnline() && !player.isDead() && target.isValid() && !target.isDead()
                && player.getWorld().equals(target.getWorld())
                && player.getLocation().distanceSquared(target.getLocation()) <= 225.0;
    }

    private void clearPerfectDodgeState(UUID playerId) {
        lightDodgeCD.remove(playerId);
        lightDodgeAttemptCD.remove(playerId);
        lightDodgeWindowEnd.remove(playerId);
        perfectDodgeCounterTargets.remove(playerId);
        perfectDodgeCounterEnd.remove(playerId);
    }

    private void dealReversalDamage(Player player, LivingEntity target, double damage) {
        if (damage <= 0.0 || !target.isValid() || target.isDead()) return;

        clearDamageImmunity(target);

        double remainingHealth = target.getHealth() - damage;
        if (remainingHealth <= 0.001) {
            // Direct health damage cannot be swallowed by vanilla iframes, shields,
            // invulnerability flags, or a nested EntityDamageByEntityEvent.
            target.setHealth(0.0);
            return;
        }

        target.setHealth(remainingHealth);
        clearDamageImmunity(target);
        target.playHurtAnimation(0.0f);
    }

    private void clearDamageImmunity(LivingEntity entity) {
        if (entity == null || !entity.isValid() || entity.isDead()) return;
        entity.setNoDamageTicks(0);
    }

    private void playReversalImpact(Location location) {
        if (location.getWorld() == null) return;
        location.getWorld().spawnParticle(Particle.EXPLOSION, location.clone().add(0, 0.25, 0), 1);
        location.getWorld().spawnParticle(Particle.CLOUD, location.clone().add(0, 0.2, 0),
                20, 0.8, 0.15, 0.8, 0.12);
        location.getWorld().spawnParticle(Particle.SWEEP_ATTACK, location.clone().add(0, 0.5, 0),
                4, 0.6, 0.2, 0.6, 0.0);
        location.getWorld().playSound(location, Sound.ITEM_MACE_SMASH_GROUND, 1.2f, 0.75f);
        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 0.65f, 1.25f);
    }

    private void finishReversal(UUID playerId, Player player, LivingEntity target) {
        activeReversals.remove(playerId);
        clearBlackCritStun(player);
        clearBlackCritStun(target);
        clearDamageImmunity(player);
        clearDamageImmunity(target);
        Bukkit.getScheduler().runTask(plugin, () -> {
            clearDamageImmunity(player);
            clearDamageImmunity(target);
        });
        if (player != null && player.isOnline() && !player.isDead()) startGracefulLanding(player);
    }

    private void startGracefulLanding(Player player) {
        boolean hadSlowFalling = player.hasPotionEffect(PotionEffectType.SLOW_FALLING);
        double triggerDistance = Math.max(0.5, plugin.getConfig().getDouble(
                "combat.perfect-dodge.landing-slow-fall-distance", 2.25));

        new BukkitRunnable() {
            int ticks;
            boolean appliedByReversal;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    cancel();
                    return;
                }

                player.setFallDistance(0f);
                if (!hadSlowFalling && !appliedByReversal && player.getVelocity().getY() <= 0.0
                        && (isNearGround(player, triggerDistance) || ticks >= 30)) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,
                            200, 0, false, false, true));
                    appliedByReversal = true;
                }

                if (ticks > 2 && isNearGround(player, 0.35)) {
                    if (appliedByReversal) player.removePotionEffect(PotionEffectType.SLOW_FALLING);
                    player.setFallDistance(0f);
                    cancel();
                    return;
                }

                if (ticks++ >= 200) {
                    if (appliedByReversal) player.removePotionEffect(PotionEffectType.SLOW_FALLING);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private boolean isNearGround(LivingEntity entity, double distance) {
        if (entity.getWorld() == null) return false;
        org.bukkit.util.RayTraceResult result = entity.getWorld().rayTraceBlocks(
                entity.getLocation().add(0, 0.1, 0), new Vector(0, -1, 0), distance + 0.1,
                org.bukkit.FluidCollisionMode.NEVER, true);
        return result != null;
    }

    private void playLightZoneHitVfx(Player attacker, Location targetLocation) {
        if (targetLocation.getWorld() == null) return;
        Vector forward = attacker.getLocation().getDirection().setY(0);
        if (forward.lengthSquared() > 0.0001) forward.normalize();
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        Location center = targetLocation.clone().add(0, 0.55, 0);
        targetLocation.getWorld().spawnParticle(Particle.SWEEP_ATTACK,
                center.clone().add(right.clone().multiply(0.28)), 1, 0, 0, 0, 0);
        targetLocation.getWorld().spawnParticle(Particle.SWEEP_ATTACK,
                center.clone().subtract(right.clone().multiply(0.28)).add(0, 0.18, 0), 1, 0, 0, 0, 0);
    }

    private void playHeavyZoneHitVfx(Location targetLocation) {
        if (targetLocation.getWorld() == null) return;
        org.bukkit.block.data.BlockData debris = targetLocation.clone().subtract(0, 1.2, 0)
                .getBlock().getBlockData();
        if (debris.getMaterial().isAir()) debris = Material.DEEPSLATE.createBlockData();
        targetLocation.getWorld().spawnParticle(Particle.FALLING_DUST,
                targetLocation.clone().add(0, 1.45, 0), 14, 0.4, 0.25, 0.4, 0.04, debris);
        targetLocation.getWorld().spawnParticle(Particle.BLOCK,
                targetLocation.clone().add(0, 0.25, 0), 10, 0.35, 0.18, 0.35, 0.08, debris);
    }

    private void applyBlackCritRecovery(Player player) {
        org.bukkit.attribute.AttributeInstance maxHealthAttribute =
                player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double maxHealth = maxHealthAttribute == null ? player.getHealth() : maxHealthAttribute.getValue();
        player.setHealth(Math.min(maxHealth, player.getHealth() + BLACK_CRIT_INSTANT_HEAL));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
                BLACK_CRIT_REGEN_TICKS, 1, false, true, true));
        player.setFireTicks(0);
        player.setFoodLevel(Math.min(20, player.getFoodLevel() + 2));
        player.setSaturation(Math.min(player.getFoodLevel(), player.getSaturation() + 2.0f));
        player.getWorld().spawnParticle(Particle.HEART,
                player.getLocation().add(0, 1.1, 0), 7, 0.45, 0.55, 0.45, 0.08);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.65f, 1.35f);
    }

    private void applySecondBlackCritRecovery(Player player) {
        org.bukkit.attribute.AttributeInstance maxHealthAttribute =
                player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double maxHealth = maxHealthAttribute == null ? player.getHealth() : maxHealthAttribute.getValue();
        player.setHealth(Math.min(maxHealth, player.getHealth() + 6.0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
                160, 2, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,
                160, 0, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                80, 0, false, true, true));
        player.setFireTicks(0);
        player.setFoodLevel(Math.min(20, player.getFoodLevel() + 4));
        player.setSaturation(Math.min(player.getFoodLevel(), player.getSaturation() + 4.0f));
        player.getWorld().spawnParticle(Particle.HEART,
                player.getLocation().add(0, 1.1, 0), 12, 0.55, 0.65, 0.55, 0.1);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                player.getLocation().add(0, 1.0, 0), 18, 0.5, 0.75, 0.5, 0.2);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.85f, 1.25f);
    }

    private void signalSecondBlackCritReady(Player player) {
        if (player == null || !player.isOnline()) return;
        if (!plugin.getData().getBoolean(
                "players." + player.getUniqueId() + ".alerts", true)) return;

        player.sendTitle(
                ChatColor.DARK_RED + "" + ChatColor.BOLD + "[ "
                        + ChatColor.RED + "!"
                        + ChatColor.DARK_RED + " ]",
                "",
                2, 14, 6);
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.65f);
        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.9f, 1.55f);
    }

    private boolean armSecondBlackCrit(Player player, boolean forced) {
        if (player == null || !player.isOnline()) return false;
        if (!forced && Math.random() >= CINEMATIC_BLACK_CRIT_CHANCE) return false;

        UUID playerId = player.getUniqueId();
        long expiresAt = System.currentTimeMillis() + CINEMATIC_BLACK_CRIT_WINDOW_MS;
        boolean newlyAvailable = cinematicBlackCritFollowUps.add(playerId);
        cinematicBlackCritFollowUpEnd.put(playerId, expiresAt);
        if (newlyAvailable) signalSecondBlackCritReady(player);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (cinematicBlackCritFollowUpEnd.getOrDefault(playerId, 0L) == expiresAt) {
                clearSecondBlackCrit(playerId);
            }
        }, Math.max(1L, (CINEMATIC_BLACK_CRIT_WINDOW_MS + 49L) / 50L));
        return true;
    }

    private void clearSecondBlackCrit(UUID playerId) {
        cinematicBlackCritFollowUps.remove(playerId);
        cinematicBlackCritFollowUpEnd.remove(playerId);
    }

    private boolean isSkillUseLocked(Player player, long now) {
        long lockedUntil = skillUseLockedUntil.getOrDefault(player.getUniqueId(), 0L);
        if (now >= lockedUntil) {
            skillUseLockedUntil.remove(player.getUniqueId());
            return false;
        }
        if (plugin.getData().getBoolean(
                "players." + player.getUniqueId() + ".alerts", true)) {
            long tenths = Math.max(1L, (lockedUntil - now + 99L) / 100L);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(
                    ChatColor.DARK_RED + "§lSKILLS SEALED §7— §c"
                            + (tenths / 10.0) + "s"));
        }
        return true;
    }

    private void recordSkillUse(Player player, long now) {
        blackCritBlockedUntil.put(player.getUniqueId(), now + SKILL_BLACK_CRIT_EXCLUSION_MS);
    }

    private void halveExpiryCooldown(Map<UUID, Long> cooldowns, UUID playerId, long now) {
        long readyAt = cooldowns.getOrDefault(playerId, 0L);
        if (readyAt > now) cooldowns.put(playerId, now + (readyAt - now) / 2L);
    }

    private void halveLastUseCooldown(Map<UUID, Long> cooldowns, UUID playerId,
                                      long now, long durationMs) {
        long lastUse = cooldowns.getOrDefault(playerId, 0L);
        long readyAt = lastUse + durationMs;
        if (lastUse > 0L && readyAt > now) {
            long shortenedReadyAt = now + (readyAt - now) / 2L;
            cooldowns.put(playerId, shortenedReadyAt - durationMs);
        }
    }

    private void applySecondBlackCritCooldownReward(Player player, long now) {
        UUID playerId = player.getUniqueId();
        halveExpiryCooldown(lightDodgeCD, playerId, now);
        halveExpiryCooldown(lightDodgeAttemptCD, playerId, now);
        halveLastUseCooldown(afterimageCD, playerId, now, 20000L);
        halveLastUseCooldown(shockwaveCooldown, playerId, now, 30000L);
        skillUseLockedUntil.put(playerId, now + SKILL_BLACK_CRIT_EXCLUSION_MS);
    }

    private void applyBlackCritPotionStun(LivingEntity target) {
        if (target == null || !target.isValid() || target.isDead()) return;
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                BLACK_CRIT_STUN_TICKS, BLACK_CRIT_STUN_AMPLIFIER, false, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,
                BLACK_CRIT_STUN_TICKS, BLACK_CRIT_STUN_AMPLIFIER, false, true, true));
    }

    private void applyTemporarySlowness(LivingEntity target, long durationMs) {
        if (target == null || !target.isValid() || target.isDead()) return;
        clearBlackCritStun(target);
        int durationTicks = (int) Math.min(Integer.MAX_VALUE,
                Math.max(1L, (Math.max(1L, durationMs) + 49L) / 50L));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, durationTicks,
                COMBAT_SLOWNESS_AMPLIFIER, false, true, true));
    }

    // Cleanup only: these modifiers may remain on entities from an older build.
    // Current combat control never creates attribute modifiers.
    public void clearBlackCritStun(LivingEntity target) {
        if (target == null) return;
        org.bukkit.attribute.AttributeInstance movement = target.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        removeAttributeModifier(movement, BLACK_CRIT_STUN_MOVE_KEY);
        removeAttributeModifier(target.getAttribute(Attribute.GENERIC_JUMP_STRENGTH),
                BLACK_CRIT_STUN_JUMP_KEY);
        removeAttributeModifier(target.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE),
                BLACK_CRIT_STUN_KNOCKBACK_KEY);
        if (movement != null) {
            for (AttributeModifier modifier : new HashSet<>(movement.getModifiers())) {
                NamespacedKey modifierKey = modifier.getKey();
                String key = modifierKey.getKey();
                if (modifierKey.getNamespace().equals(LIGHT_DEBUFF_SPD_KEY.getNamespace())
                        && (key.equals("light_debuff_spd") || key.startsWith("light_debuff_spd_"))) {
                    movement.removeModifier(modifier);
                }
            }
        }
    }

    private void playCinematicBlackCritVfx(Location impactLocation, Location attackerLocation) {
        if (impactLocation.getWorld() == null) return;
        playBlackFlashVFX(impactLocation, attackerLocation);
        org.bukkit.World world = impactLocation.getWorld();
        Location center = impactLocation.clone();

        world.spawnParticle(Particle.DUST, center, 12, 0.2, 0.2, 0.2, 0.0,
                new Particle.DustOptions(Color.WHITE, 4.0f));
        world.spawnParticle(Particle.END_ROD, center, 10, 0.25, 0.25, 0.25, 0.08);
        world.spawnParticle(Particle.REVERSE_PORTAL, center, 80, 1.2, 1.2, 1.2, 0.45);
        world.spawnParticle(Particle.ELECTRIC_SPARK, center, 55, 1.1, 1.0, 1.1, 0.5);
        world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 0.55f);
        world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 0.7f);

        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (tick >= CINEMATIC_BLACK_CRIT_LAUNCH_TICKS) {
                    world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1);
                    world.spawnParticle(Particle.ELECTRIC_SPARK, center, 90,
                            1.8, 1.3, 1.8, 0.8);
                    world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.3f, 0.65f);
                    cancel();
                    return;
                }

                double progress = tick / (double) CINEMATIC_BLACK_CRIT_LAUNCH_TICKS;
                double radius = 0.45 + Math.sin(progress * Math.PI) * 4.0;
                for (int tendril = 0; tendril < 10; tendril++) {
                    double angle = tendril * (Math.PI * 2.0 / 10.0)
                            + tick * 0.72 + Math.sin((tick + tendril) * 1.35) * 0.55;
                    double y = Math.sin(tick * 1.15 + tendril * 0.9) * 0.85
                            + (tick % 3) * 0.12;
                    Location point = center.clone().add(
                            Math.cos(angle) * radius, y, Math.sin(angle) * radius);
                    float size = 0.8f + (tick % 4) * 0.12f;
                    world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.BLACK, size));
                    if ((tick + tendril) % 2 == 0) {
                        world.spawnParticle(Particle.ELECTRIC_SPARK, point,
                                2, 0.08, 0.08, 0.08, 0.12);
                    }
                }

                if (tick % 3 == 0) {
                    for (int ringPoint = 0; ringPoint < 24; ringPoint++) {
                        double angle = ringPoint * (Math.PI * 2.0 / 24.0) + tick * 0.3;
                        Location point = center.clone().add(
                                Math.cos(angle) * radius, 0.1, Math.sin(angle) * radius);
                        world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0,
                                new Particle.DustOptions(Color.fromRGB(90, 0, 130), 1.1f));
                    }
                }

                // A suspended impact trail between the punch and its target.
                if (attackerLocation != null && attackerLocation.getWorld() == world && tick % 2 == 0) {
                    Vector impactLine = center.toVector().subtract(attackerLocation.toVector());
                    for (int linePoint = 1; linePoint <= 8; linePoint++) {
                        double lineProgress = linePoint / 9.0;
                        Location point = attackerLocation.clone().add(
                                impactLine.clone().multiply(lineProgress));
                        Color color = linePoint % 2 == 0 ? Color.BLACK : Color.fromRGB(110, 0, 160);
                        world.spawnParticle(Particle.DUST, point, 1, 0.03, 0.03, 0.03, 0,
                                new Particle.DustOptions(color, 0.9f));
                    }
                }

                if (tick > 0 && tick % 10 == 0) {
                    world.spawnParticle(Particle.DUST, center, 5, 0.1, 0.1, 0.1, 0.0,
                            new Particle.DustOptions(Color.WHITE, 3.0f));
                    world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_IMPACT,
                            0.8f, 0.65f + tick * 0.012f);
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void playCinematicReleaseTendrils(Location origin, Vector launchDirection) {
        if (origin == null || origin.getWorld() == null) return;
        org.bukkit.World world = origin.getWorld();
        Vector forward = launchDirection.clone().setY(0);
        if (forward.lengthSquared() < 0.0001) forward = new Vector(0, 0, 1);
        forward.normalize();
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        java.util.Random random = new java.util.Random();
        java.util.List<java.util.List<Location>> tendrils = new java.util.ArrayList<>();

        // Pre-build fixed, branching-looking paths. They grow rapidly, then the exact
        // same points are redrawn so the whole formation appears suspended in the air.
        for (int tendrilIndex = 0; tendrilIndex < 12; tendrilIndex++) {
            double angle = Math.PI * 2.0 * tendrilIndex / 18.0 + random.nextDouble() * 0.3;
            Vector direction = forward.clone().multiply(0.75 + random.nextDouble() * 0.65)
                    .add(right.clone().multiply(Math.cos(angle) * (0.45 + random.nextDouble() * 0.85)))
                    .add(new Vector(0, Math.sin(angle) * 0.8 + (random.nextDouble() - 0.5) * 0.55, 0))
                    .normalize();
            Location current = origin.clone().add(
                    (random.nextDouble() - 0.5) * 0.35,
                    (random.nextDouble() - 0.5) * 0.35,
                    (random.nextDouble() - 0.5) * 0.35);
            double length = 6.0 + random.nextDouble() * 4.5;
            java.util.List<Location> points = new java.util.ArrayList<>();

            for (double distance = 0; distance < length; distance += 0.42) {
                if (random.nextDouble() < 0.48) {
                    direction.add(right.clone().multiply((random.nextDouble() - 0.5) * 0.55))
                            .add(new Vector(0, (random.nextDouble() - 0.5) * 0.42, 0))
                            .add(forward.clone().multiply((random.nextDouble() - 0.5) * 0.18))
                            .normalize();
                }
                current.add(direction.clone().multiply(0.42));
                points.add(current.clone());
            }
            tendrils.add(points);
        }

        new BukkitRunnable() {
            int tick;
            static final int GROWTH_TICKS = 5;
            static final int HOLD_TICKS = 50;

            @Override
            public void run() {
                if (tick >= GROWTH_TICKS + HOLD_TICKS) {
                    cancel();
                    return;
                }

                boolean growing = tick < GROWTH_TICKS;
                // During the hold, three-tick redraws keep the dust stationary while
                // avoiding an excessive packet burst every server tick.
                if (growing || (tick - GROWTH_TICKS) % 3 == 0) {
                    double visibleFraction = growing ? (tick + 1.0) / GROWTH_TICKS : 1.0;
                    float size = tick > GROWTH_TICKS + HOLD_TICKS - 10 ? 0.75f : 1.25f;
                    for (java.util.List<Location> tendril : tendrils) {
                        int visiblePoints = Math.max(1,
                                Math.min(tendril.size(), (int) Math.ceil(tendril.size() * visibleFraction)));
                        for (int pointIndex = 0; pointIndex < visiblePoints; pointIndex++) {
                            Location point = tendril.get(pointIndex);
                            Color color = pointIndex % 7 == 0
                                    ? Color.fromRGB(45, 0, 65) : Color.BLACK;
                            world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0,
                                    new Particle.DustOptions(color, size));
                        }
                        if (growing) {
                            Location tip = tendril.get(visiblePoints - 1);
                            world.spawnParticle(Particle.SQUID_INK, tip, 3,
                                    0.08, 0.08, 0.08, 0.02);
                        }
                    }
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void playAltarStyleCinematicTendrils(Location targetOrigin, Location attackerLocation) {
        if (targetOrigin == null || attackerLocation == null
                || targetOrigin.getWorld() == null
                || !attackerLocation.getWorld().equals(targetOrigin.getWorld())) return;

        org.bukkit.World world = targetOrigin.getWorld();
        Vector towardAttacker = attackerLocation.toVector().subtract(targetOrigin.toVector());
        double attackerDistance = towardAttacker.length();
        if (attackerDistance < 0.1) towardAttacker = attackerLocation.getDirection().multiply(-1);
        if (towardAttacker.lengthSquared() < 0.0001) towardAttacker = new Vector(0, 0, 1);
        towardAttacker.normalize();
        Vector sideways = new Vector(-towardAttacker.getZ(), 0, towardAttacker.getX());
        java.util.Random random = new java.util.Random();
        java.util.List<java.util.List<Location>> tendrils = new java.util.ArrayList<>();

        // These paths all leave the struck target and aggressively steer toward the
        // attacker, then pass slightly beyond them like the altar's long lightning arcs.
        for (int tendrilIndex = 0; tendrilIndex < 18; tendrilIndex++) {
            Vector direction = towardAttacker.clone()
                    .add(sideways.clone().multiply((random.nextDouble() - 0.5) * 0.75))
                    .add(new Vector(0, (random.nextDouble() - 0.5) * 0.55, 0))
                    .normalize();
            Location current = targetOrigin.clone().add(
                    (random.nextDouble() - 0.5) * 0.3,
                    (random.nextDouble() - 0.5) * 0.3,
                    (random.nextDouble() - 0.5) * 0.3);
            double maxReach = Math.max(7.0, attackerDistance + 2.0 + random.nextDouble() * 3.0);
            java.util.List<Location> points = new java.util.ArrayList<>();

            for (double traveled = 0; traveled < maxReach; traveled += 0.3) {
                if (traveled < attackerDistance) {
                    Vector steering = attackerLocation.toVector().subtract(current.toVector());
                    if (steering.lengthSquared() > 0.001) {
                        direction.multiply(0.78).add(steering.normalize().multiply(0.22));
                    }
                }
                if (random.nextDouble() < 0.42) {
                    direction.add(sideways.clone().multiply((random.nextDouble() - 0.5) * 0.34))
                            .add(new Vector(0, (random.nextDouble() - 0.5) * 0.28, 0));
                }
                if (direction.lengthSquared() > 0.0001) direction.normalize();
                current.add(direction.clone().multiply(0.3));
                points.add(current.clone());
            }
            tendrils.add(points);
        }

        final Vector motionForward = towardAttacker.clone();
        final Vector motionSideways = sideways.clone();
        java.util.List<java.util.List<Location>> frozenTendrils = new java.util.ArrayList<>();

        new BukkitRunnable() {
            int tick;
            static final int WRITHE_TICKS = 40;
            static final int HOLD_TICKS = 45;

            @Override
            public void run() {
                if (tick >= WRITHE_TICKS + HOLD_TICKS) {
                    cancel();
                    return;
                }

                boolean writhing = tick < WRITHE_TICKS;
                if (writhing) {
                    for (int tendrilIndex = 0; tendrilIndex < tendrils.size(); tendrilIndex++) {
                        java.util.List<Location> tendril = tendrils.get(tendrilIndex);
                        java.util.List<Location> currentShape = new java.util.ArrayList<>();
                        double phase = tick * 2.15 + tendrilIndex * 0.73;
                        for (int pointIndex = 0; pointIndex < tendril.size(); pointIndex++) {
                            double pointProgress = pointIndex / (double) Math.max(1, tendril.size() - 1);
                            double envelope = 0.08 + Math.sin(pointProgress * Math.PI) * 0.92;
                            double lateralMotion = (Math.sin(phase + pointIndex * 0.95)
                                    + Math.sin(phase * 1.7 - pointIndex * 0.43) * 0.45)
                                    * 1.15 * envelope;
                            double verticalMotion = (Math.sin(phase * 1.35 + pointIndex * 1.18)
                                    + Math.cos(phase * 2.05 - pointIndex * 0.37) * 0.4)
                                    * 1.35 * envelope;
                            double forwardMotion = Math.sin(phase * 1.6 + pointIndex * 0.61)
                                    * 0.3 * envelope;
                            Location movingPoint = tendril.get(pointIndex).clone()
                                    .add(motionSideways.clone().multiply(lateralMotion))
                                    .add(motionForward.clone().multiply(forwardMotion))
                                    .add(0, verticalMotion, 0);
                            currentShape.add(movingPoint);
                            float size = (float) Math.max(0.7, 2.6 * (1.0 - pointProgress));
                            world.spawnParticle(Particle.DUST, movingPoint,
                                    1, 0, 0, 0, 0,
                                    new Particle.DustOptions(Color.BLACK, size));
                            if (pointIndex % 9 == 0) {
                                world.spawnParticle(Particle.SQUID_INK, movingPoint,
                                        1, 0.02, 0.02, 0.02, 0.005);
                            }
                        }
                        world.spawnParticle(Particle.SQUID_INK,
                                currentShape.get(currentShape.size() - 1),
                                4, 0.07, 0.07, 0.07, 0.015);
                        if (tick == WRITHE_TICKS - 1) frozenTendrils.add(currentShape);
                    }
                } else if ((tick - WRITHE_TICKS) % 3 == 0) {
                    for (java.util.List<Location> tendril : frozenTendrils) {
                        for (int pointIndex = 0; pointIndex < tendril.size(); pointIndex++) {
                            double pointProgress = pointIndex / (double) Math.max(1, tendril.size() - 1);
                            float size = (float) Math.max(0.7, 2.6 * (1.0 - pointProgress));
                            world.spawnParticle(Particle.DUST, tendril.get(pointIndex),
                                    1, 0, 0, 0, 0,
                                    new Particle.DustOptions(Color.BLACK, size));
                        }
                    }
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void playCinematicBlackLightningTendril(Location targetOrigin, Location attackerLocation) {
        if (targetOrigin == null || attackerLocation == null
                || targetOrigin.getWorld() == null
                || !targetOrigin.getWorld().equals(attackerLocation.getWorld())) return;

        org.bukkit.World world = targetOrigin.getWorld();
        Vector towardAttacker = attackerLocation.toVector().subtract(targetOrigin.toVector());
        double attackerDistance = towardAttacker.length();
        if (attackerDistance < 0.1) towardAttacker = attackerLocation.getDirection().multiply(-1);
        if (towardAttacker.lengthSquared() < 0.0001) towardAttacker = new Vector(0, 0, 1);
        towardAttacker.normalize();
        final Vector forward = towardAttacker.clone();
        Vector horizontalRight = new Vector(-forward.getZ(), 0, forward.getX());
        if (horizontalRight.lengthSquared() < 0.0001) horizontalRight = new Vector(1, 0, 0);
        final Vector right = horizontalRight.normalize();
        final double reach = Math.min(12.0, Math.max(3.0, attackerDistance)) + 2.5;
        final java.util.List<TendrilParticle> frozenShape = new java.util.ArrayList<>();

        new BukkitRunnable() {
            int frame;
            static final int WRITHE_FRAMES = 20;
            static final int HOLD_FRAMES = 20;

            private java.util.List<TendrilParticle> createFrame(int animationFrame) {
                java.util.Random frameRandom = new java.util.Random(0xB1ACFL + animationFrame * 7919L);
                java.util.List<TendrilParticle> shape = new java.util.ArrayList<>();
                for (int boltIndex = 0; boltIndex < 4; boltIndex++) {
                    double originOffset = (boltIndex - 1.5) * 0.13;
                    Location current = targetOrigin.clone()
                            .add(right.clone().multiply(originOffset))
                            .add(0, (boltIndex % 2 == 0 ? -0.08 : 0.08), 0);
                    Vector direction = forward.clone().add(new Vector(
                            (frameRandom.nextDouble() - 0.5) * 0.32,
                            (frameRandom.nextDouble() - 0.5) * 0.28,
                            (frameRandom.nextDouble() - 0.5) * 0.32)).normalize();
                    double boltReach = reach + (frameRandom.nextDouble() - 0.5) * 1.2;

                    for (double distance = 0.0; distance <= boltReach; distance += 0.15) {
                        double progress = distance / boltReach;
                        if (frameRandom.nextDouble() < 0.38) {
                            direction.add(new Vector(
                                    (frameRandom.nextDouble() - 0.5) * 0.75,
                                    (frameRandom.nextDouble() - 0.5) * 0.65,
                                    (frameRandom.nextDouble() - 0.5) * 0.75));
                        }
                        // A light forward correction keeps the normal random-walk bolt
                        // aimed from the victim toward the attacker instead of drifting away.
                        direction.multiply(0.88).add(forward.clone().multiply(0.12)).normalize();
                        current.add(direction.clone().multiply(0.15));
                        float size = (float) Math.max(0.35, 0.85 * (1.0 - progress));
                        shape.add(new TendrilParticle(current.clone(), size));

                        if (frameRandom.nextDouble() < 0.045 && progress < 0.82) {
                            Location branchCurrent = current.clone();
                            Vector branchDirection = direction.clone().add(new Vector(
                                    (frameRandom.nextDouble() - 0.5) * 1.1,
                                    (frameRandom.nextDouble() - 0.5) * 0.9,
                                    (frameRandom.nextDouble() - 0.5) * 1.1)).normalize();
                            double branchLength = 1.2 + frameRandom.nextDouble() * 2.2;
                            for (double branchDistance = 0; branchDistance < branchLength; branchDistance += 0.15) {
                                if (frameRandom.nextDouble() < 0.35) {
                                    branchDirection.add(new Vector(
                                            (frameRandom.nextDouble() - 0.5) * 0.7,
                                            (frameRandom.nextDouble() - 0.5) * 0.6,
                                            (frameRandom.nextDouble() - 0.5) * 0.7)).normalize();
                                }
                                branchCurrent.add(branchDirection.clone().multiply(0.15));
                                float branchSize = (float) Math.max(0.30,
                                        0.65 * (1.0 - branchDistance / branchLength));
                                shape.add(new TendrilParticle(branchCurrent.clone(), branchSize));
                            }
                        }
                    }
                }
                return shape;
            }

            private void render(java.util.List<TendrilParticle> shape) {
                for (TendrilParticle particle : shape) {
                    world.spawnParticle(Particle.DUST, particle.location(), 1,
                            0, 0, 0, 0,
                            new Particle.DustOptions(Color.BLACK, particle.size()));
                }
            }

            @Override
            public void run() {
                if (frame >= WRITHE_FRAMES + HOLD_FRAMES) {
                    cancel();
                    return;
                }

                if (frame < WRITHE_FRAMES) {
                    java.util.List<TendrilParticle> currentShape = createFrame(frame);
                    render(currentShape);
                    world.spawnParticle(Particle.SQUID_INK,
                            currentShape.get(currentShape.size() - 1).location(),
                            2, 0.04, 0.04, 0.04, 0.01);
                    if (frame == WRITHE_FRAMES - 1) frozenShape.addAll(currentShape);
                } else if ((frame - WRITHE_FRAMES) % 2 == 0) {
                    render(frozenShape);
                }
                frame++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /**
     * Draws several sharp dust tendrils that lash from the victim toward the attacker.
     * Only a short moving section is emitted at a time, preventing old frames from piling
     * into a cloud while still making each path read as a fast, crooked lightning ribbon.
     */
    private void playReferenceCinematicTendrils(Location targetOrigin, Location attackerLocation) {
        if (targetOrigin == null || attackerLocation == null
                || targetOrigin.getWorld() == null
                || !targetOrigin.getWorld().equals(attackerLocation.getWorld())) return;

        org.bukkit.World world = targetOrigin.getWorld();
        Vector towardAttacker = attackerLocation.toVector().subtract(targetOrigin.toVector());
        double attackerDistance = towardAttacker.length();
        if (attackerDistance < 0.1) towardAttacker = attackerLocation.getDirection().multiply(-1.0);
        if (towardAttacker.lengthSquared() < 0.0001) towardAttacker = new Vector(0, 0, 1);
        final Vector forward = towardAttacker.normalize();
        Vector horizontalRight = new Vector(-forward.getZ(), 0, forward.getX());
        if (horizontalRight.lengthSquared() < 0.0001) horizontalRight = new Vector(1, 0, 0);
        final Vector right = horizontalRight.normalize();
        final Vector up = right.clone().crossProduct(forward).normalize();
        final double reach = Math.min(11.0, Math.max(4.0, attackerDistance + 2.0));
        final Particle.DustOptions blackDust =
                new Particle.DustOptions(Color.fromRGB(2, 2, 3), 0.85f);

        final Particle.DustOptions[] accentColors = {
                new Particle.DustOptions(Color.fromRGB(255, 222, 35), 1.05f),
                new Particle.DustOptions(Color.fromRGB(255, 126, 20), 1.10f),
                new Particle.DustOptions(Color.fromRGB(235, 28, 45), 1.15f),
                new Particle.DustOptions(Color.fromRGB(157, 62, 255), 1.10f)
        };

        new BukkitRunnable() {
            int tick;
            static final int EMISSION_TICKS = 18;
            static final int TENDRIL_COUNT = 4;

            private Location point(int tendril, double progress) {
                double envelope = Math.sin(Math.PI * progress);
                double phase = tendril * (Math.PI * 0.5);
                double angularProgress = Math.floor(progress * 7.0) / 7.0;
                double side = Math.sin(angularProgress * Math.PI * (2.1 + tendril * 0.17)
                        + phase + tick * 1.48) * (1.25 + tendril * 0.12) * envelope;
                double height = Math.cos(angularProgress * Math.PI * (2.45 + tendril * 0.13)
                        + phase - tick * 1.63) * (0.9 + tendril * 0.10) * envelope;
                double snap = Math.sin(angularProgress * 18.0 + phase + tick * 1.9)
                        * 0.32 * envelope;
                double rootSpread = (tendril - 1.5) * 0.18 * (1.0 - progress);
                return targetOrigin.clone()
                        .add(forward.clone().multiply(reach * progress + snap))
                        .add(right.clone().multiply(rootSpread + side))
                        .add(up.clone().multiply(height));
            }

            private void renderMovingTrail(int tendril) {
                // Each tendril races across the whole gap in roughly six ticks. Staggered
                // phases create multiple successive lashes without filling the gap at once.
                double head = ((tick + tendril * 0.7) % 7.0) / 6.0;
                double tail = Math.max(0.0, head - 0.34);
                head = Math.min(1.0, head);
                Location previous = point(tendril, tail);
                for (double progress = tail + 0.035; progress <= head + 0.001; progress += 0.035) {
                    Location current = point(tendril, Math.min(progress, head));
                    Vector drift = current.toVector().subtract(previous.toVector());
                    if (drift.lengthSquared() > 0.0001) drift.normalize().multiply(0.055);
                    world.spawnParticle(Particle.DUST, current, 0,
                            drift.getX(), drift.getY(), drift.getZ(), 1.0, blackDust);
                    previous = current;
                }

                Location tip = point(tendril, head);
                Particle.DustOptions color = accentColors[(tendril + tick / 3) % accentColors.length];
                world.spawnParticle(Particle.DUST, tip, 2,
                        0.035, 0.035, 0.035, 0.0, color);
            }

            @Override
            public void run() {
                if (tick >= EMISSION_TICKS) {
                    cancel();
                    return;
                }
                for (int tendril = 0; tendril < TENDRIL_COUNT; tendril++) {
                    renderMovingTrail(tendril);
                }
                if (tick % 3 == 0) {
                    Particle.DustOptions color = accentColors[(tick / 3) % accentColors.length];
                    world.spawnParticle(Particle.DUST, targetOrigin, 4,
                            0.16, 0.22, 0.16, 0.0, color);
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        Player p = null; boolean isRanged = false;
        LivingEntity dodgeSource = null;

        if (event.getDamager() instanceof Player playerDamager) {
            p = playerDamager;
            dodgeSource = playerDamager;
        } else if (event.getDamager() instanceof Projectile projectile) {
            isRanged = true;
            if (projectile.getShooter() instanceof LivingEntity livingShooter) {
                dodgeSource = livingShooter;
                if (livingShooter instanceof Player shooter) p = shooter;
            }
        } else if (event.getDamager() instanceof LivingEntity livingDamager) {
            dodgeSource = livingDamager;
        }

        if (p != null && !afterimageDamageLock.contains(p.getUniqueId())) revealPlayer(p);
        if (event.getEntity() instanceof Player damagedPlayer) revealPlayer(damagedPlayer);

        if (p != null && (shockwaveDamageLock.contains(p.getUniqueId())
                || afterimageDamageLock.contains(p.getUniqueId()))) return;

        long now = System.currentTimeMillis();

        if (event.getEntity() instanceof Player victim) {
            UUID vId = victim.getUniqueId();
            if (p != null) {
                combatTagAttacker.put(vId, p.getUniqueId()); combatTagTimer.put(vId, now + 15000);
                combatTagAttacker.put(p.getUniqueId(), vId); combatTagTimer.put(p.getUniqueId(), now + 15000);
            }

            boolean vInZone = zoneEndTime.containsKey(vId) && zoneEndTime.get(vId) > now;
            String vt1 = plugin.getData().getString("players." + vId + ".zone.tier1", "none");
            String vt3 = plugin.getData().getString("players." + vId + ".zone.tier3", "none");
            boolean vAlerts = plugin.getData().getBoolean("players." + vId + ".alerts", true);

            if (vInZone) {
                // LIGHT U1: timing-based Perfect Dodge against melee and projectiles.
                if (!event.isCancelled() && vt1.equals("light") && dodgeSource != null && dodgeSource != victim
                        && now <= lightDodgeWindowEnd.getOrDefault(vId, 0L)
                        && now >= lightDodgeCD.getOrDefault(vId, 0L)) {
                    triggerPerfectDodge(event, victim, dodgeSource, now, vAlerts);
                    return;
                }

                // HEAVY U1: Parry (on any blocked attack with shield)
                if (vt1.equals("heavy") && p != null && victim.isBlocking()) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 1));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));
                    victim.playSound(victim.getLocation(), Sound.BLOCK_ANVIL_PLACE, 1f, 2f);
                    victim.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0),
                            10, 0.25, 0.25, 0.25, 0.0,
                            new Particle.DustOptions(Color.WHITE, 2.5f));
                    if (vAlerts) victim.sendMessage(plugin.PREFIX + ChatColor.RED + "Parry! Attacker Slowed!");
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

        if (p != null && !isRanged && event.getEntity() instanceof LivingEntity counterTarget) {
            if (tryStartReversal(p, counterTarget, event, now)) return;
        }

        if (p == null) return;
        UUID uuid = p.getUniqueId();
        boolean alertsEnabled = plugin.getData().getBoolean("players." + uuid + ".alerts", true);

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
        String t1 = plugin.getData().getString("players." + uuid + ".zone.tier1", "none");
        String t2 = plugin.getData().getString("players." + uuid + ".zone.tier2", "none");
        String t3 = plugin.getData().getString("players." + uuid + ".zone.tier3", "none");

        boolean isSpam = lastSwingWasSpam.getOrDefault(uuid, false);
        long timeSinceLastHit = now - lastAttackTime.getOrDefault(uuid, 0L);
        boolean isChargedHit = isRanged || (!isSpam && (timeSinceLastHit >= 240 || !lastAttackTime.containsKey(uuid)));
        lastAttackTime.put(uuid, now);
        if (!event.isCancelled() && event.getEntity() instanceof Player) {
            recordPlayerCritActivity(uuid, now);
        }

        boolean isVanillaCrit = false;
        if (!isRanged) { isVanillaCrit = (p.getFallDistance() > 0.0F && !p.isOnGround() && !p.hasPotionEffect(PotionEffectType.BLINDNESS) && p.getVehicle() == null && !p.isSprinting()); }
        else { if (event.getDamager() instanceof org.bukkit.entity.AbstractArrow arrow) isVanillaCrit = arrow.isCritical(); }

        if (inZone) {
            boolean isLightHit = t1.equals("light");
            boolean isHeavyHit = t1.equals("heavy");
            double mult = isHeavyHit ? HEAVY_ZONE_HIT_MULTIPLIER
                    : (isLightHit ? LIGHT_ZONE_HIT_MULTIPLIER : 1.5);

            // Passive Mix
            if (isLightHit && !isOffhandShield(p)) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 40, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1));
            }

            // TIER 2 & TIER 3 Mix
            if (isValidTarget && isChargedHit) {
                int stack = heavyStacks.getOrDefault(uuid, 0);

                long lastShockwave = shockwaveCooldown.getOrDefault(uuid, 0L);
                if (t3.equals("heavy") && stack >= 5 && now >= lastShockwave + 30000L
                        && !isSkillUseLocked(p, now)) {
                    // Shockwave Finisher! (Triggered on next hit when at 5 stacks & not on 30s cooldown)
                    shockwaveCooldown.put(uuid, now);
                    recordSkillUse(p, now);
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
                } else if (t2.equals("heavy")) {
                    if (stack < 5) {
                        stack++;
                        heavyStacks.put(uuid, stack);
                        updateBaseAttackSpeed(p, t1, stack);
                    }
                    mult *= (1.0 + (stack * 0.10));
                }
            }



            if (t3.equals("light") && targetEnt instanceof Player targetPlayer) {
                UUID tId = targetPlayer.getUniqueId();
                Map<UUID, Integer> dMap = lightDebuffs.computeIfAbsent(uuid, k -> new HashMap<>());
                double maxEffect = getLightTier3MaxEffect(p);
                double effectPerStack = maxEffect / 5.0;

                // Tier 3 Light scales to a different cap depending on the current offhand item.
                int currentStacks = dMap.getOrDefault(tId, 0);
                if (currentStacks > 0) {
                    double currentEffect = Math.min(maxEffect, currentStacks * effectPerStack);
                    applyLightTier3Debuff(p, targetPlayer, currentStacks);
                    mult *= (1.0 + currentEffect);
                }
            }

            if (isVanillaCrit) mult /= 1.5;
            event.setDamage(event.getDamage() * mult);

            Location critLoc = event.getEntity().getLocation().add(0, 0.35, 0);
            if (isLightHit) {
                playLightZoneHitVfx(p, critLoc);
                if (alertsEnabled) p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.5f, 1.45f);
            } else if (isHeavyHit) {
                playHeavyZoneHitVfx(critLoc);
                if (alertsEnabled) p.playSound(p.getLocation(), Sound.BLOCK_DEEPSLATE_BREAK, 0.75f, 0.65f);
            } else {
                p.getWorld().spawnParticle(Particle.DUST, critLoc, 5, 0.2, 0.2, 0.2, 0.0, new Particle.DustOptions(org.bukkit.Color.YELLOW, 1.0f));
                if (alertsEnabled) p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.5f, 1.2f);
            }
            return;
        }

        int totalPoints = plugin.getData().getInt("players." + uuid + ".kills", 0) + plugin.getData().getInt("players." + uuid + ".overflow", 0);
        double critChance = Math.min(50.0, totalPoints * 0.8);
        if (plugin.eventManager != null && plugin.eventManager.isPvpBoss(uuid)) critChance += 70.0;
        if (isRanged && event.getDamager() instanceof org.bukkit.entity.AbstractArrow arrow && arrow.isShotFromCrossbow()) critChance -= 10.0;
        if (critChance < 0) critChance = 0.0;
        boolean forcedBlackCrit = isValidTarget && forcedBlackCritTests.remove(uuid);
        boolean forcedDoubleBlackCrit = forcedBlackCrit && forcedDoubleBlackCritTests.remove(uuid);
        int guaranteeStacks = Math.max(1, plugin.getConfig().getInt(
                "combat.colored-crit.guarantee-next-tier-at-stacks",
                DEFAULT_COLORED_CRIT_GUARANTEE_STACKS));
        int currentYellow = yellowStacks.getOrDefault(uuid, 0);
        int currentOrange = orangeStacks.getOrDefault(uuid, 0);
        boolean guaranteeRed = isValidTarget && totalPoints >= 60 && currentOrange >= guaranteeStacks;
        boolean guaranteeOrange = isValidTarget && !guaranteeRed
                && totalPoints >= 30 && currentYellow >= guaranteeStacks;
        boolean guaranteedColoredCrit = guaranteeOrange || guaranteeRed;

        if (forcedBlackCrit || guaranteedColoredCrit
                || (critChance > 0 && Math.random() * 100 < critChance)) {
            Location critLoc = event.getEntity().getLocation().add(0, 0.35, 0);
            if (isValidTarget) {
                int y = yellowStacks.getOrDefault(uuid, 0); int o = orangeStacks.getOrDefault(uuid, 0); int r = redStacks.getOrDefault(uuid, 0);
                boolean isYellow = !forcedBlackCrit, isOrange = false, isRed = false, isBlack = forcedBlackCrit;

                if (guaranteeRed) {
                    isYellow = false;
                    isRed = true;
                } else if (guaranteeOrange) {
                    isYellow = false;
                    isOrange = true;
                } else if (!forcedBlackCrit && totalPoints >= 30 && y >= 7 && Math.random() * 100 < 35.0) {
                    isYellow = false;
                    isOrange = true;
                }
                if (!guaranteedColoredCrit && !forcedBlackCrit && isOrange && totalPoints >= 60 && o >= 7) {
                    double redChance = Math.min(50.0, 15.0 + (r * 5.0));
                    if (Math.random() * 100 < redChance) { isOrange = false; isRed = true; }
                }

                if (!guaranteedColoredCrit && !forcedBlackCrit && isRed && totalPoints >= 100
                        && now >= blackCritBlockedUntil.getOrDefault(uuid, 0L)) {
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
                    zoneBlackCharge.put(uuid, 0.0);
                    zoneBlackCritCooldown.put(uuid, now + ZONE_ENTRY_BLACK_CRIT_COOLDOWN_MS);
                    yellowStacks.put(uuid, 0); orangeStacks.put(uuid, 0); redStacks.put(uuid, 0);

                    if (t1.equals("heavy")) p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int)(durationMs / 50L), 0));
                    updateBaseAttackSpeed(p, t1, 0);

                    heavyStacks.put(uuid, 0); heavyHitCount.put(uuid, 0);
                    lightDmgTaken.put(uuid, 0.0); lightHits.remove(uuid);

                    double mult = 4.0;
                    if (isVanillaCrit) mult /= 1.5;
                    event.setDamage(event.getDamage() * mult);
                    guaranteeAcceptedDamage(event, targetEnt);
                    applyBlackCritRecovery(p);
                    applyBlackCritPotionStun(targetEnt);

                    playBlackFlashVFX(targetLoc, p.getLocation().add(0, 1.1, 0));
                    if (forcedDoubleBlackCrit) {
                        Bukkit.getScheduler().runTask(plugin,
                                () -> armSecondBlackCrit(Bukkit.getPlayer(uuid), true));
                    }
                    if (alertsEnabled) {
                        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "☠ BLACK CRIT - THE ZONE (" + (durationMs / 1000) + "s) ☠"));
                    }
                }
                else if (isRed) {
                    if (guaranteeRed) orangeStacks.put(uuid, 0);
                    redStacks.put(uuid, r + 1);
                    double mult = 3.0; if (isVanillaCrit) mult /= 1.5; event.setDamage(event.getDamage() * mult);
                    p.getWorld().spawnParticle(Particle.DUST, critLoc, 12, 0.3, 0.3, 0.3, 0.0, new Particle.DustOptions(org.bukkit.Color.RED, 1.4f));
                    if (alertsEnabled) p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.4f, 2f);
                }
                else if (isOrange) {
                    if (guaranteeOrange) yellowStacks.put(uuid, 0);
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
                        if (plugin.getData().getBoolean("players." + p.getUniqueId() + ".alerts", true) && p.isOnline())
                            p.sendMessage(plugin.PREFIX + ChatColor.YELLOW + "Your Ender Pearl despawned.");
                    }
                }
            }.runTaskLater(plugin, 300L);
        }
    }

    private void triggerInZoneBlackCrit(Player attacker, LivingEntity target,
                                        EntityDamageByEntityEvent event, long now) {
        UUID attackerId = attacker.getUniqueId();
        String tier1 = plugin.getData().getString(
                "players." + attackerId + ".zone.tier1", "none");
        double ordinaryMultiplier = tier1.equals("heavy")
                ? HEAVY_ZONE_HIT_MULTIPLIER
                : (tier1.equals("light") ? LIGHT_ZONE_HIT_MULTIPLIER : 1.5);
        event.setDamage(event.getDamage() * (ZONE_BLACK_CRIT_MULTIPLIER / ordinaryMultiplier));
        guaranteeAcceptedDamage(event, target);

        long extendedEndTime = zoneEndTime.get(attackerId) + ZONE_BLACK_CRIT_EXTENSION_MS;
        zoneEndTime.put(attackerId, extendedEndTime);
        zoneBlackCharge.put(attackerId, 0.0);
        zoneBlackCritCooldown.put(attackerId, now + ZONE_BLACK_CRIT_COOLDOWN_MS);
        applyBlackCritRecovery(attacker);
        applyBlackCritPotionStun(target);

        if (tier1.equals("heavy")) {
            long remainingTicks = Math.max(1L, (extendedEndTime - now + 49L) / 50L);
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                    (int) Math.min(Integer.MAX_VALUE, remainingTicks), 0));
        }

        playBlackFlashVFX(target.getLocation().add(0, 1, 0),
                attacker.getLocation().add(0, 1.1, 0));
        if (plugin.getData().getBoolean("players." + attackerId + ".alerts", true)) {
            attacker.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(
                    ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "☠ BLACK CRIT  +60s ZONE ☠"));
        }
        armSecondBlackCrit(attacker, false);
    }

    private void triggerCinematicBlackCrit(Player attacker, LivingEntity target,
                                           EntityDamageByEntityEvent event) {
        UUID attackerId = attacker.getUniqueId();
        String tier1 = plugin.getData().getString(
                "players." + attackerId + ".zone.tier1", "none");
        double ordinaryMultiplier = tier1.equals("heavy")
                ? HEAVY_ZONE_HIT_MULTIPLIER
                : (tier1.equals("light") ? LIGHT_ZONE_HIT_MULTIPLIER : 1.5);
        event.setDamage(event.getDamage() * (CINEMATIC_BLACK_CRIT_MULTIPLIER / ordinaryMultiplier));
        guaranteeAcceptedDamage(event, target);
        applySecondBlackCritRecovery(attacker);
        applySecondBlackCritCooldownReward(attacker, System.currentTimeMillis());

        Location impactLocation = target.getLocation().add(0, 1, 0);
        playCinematicBlackCritVfx(impactLocation, attacker.getLocation().add(0, 1.1, 0));
        applyTemporarySlowness(attacker, CINEMATIC_BLACK_CRIT_SLOWNESS_MS);
        applyBlackCritPotionStun(target);

        Vector launchDirection = target.getLocation().toVector()
                .subtract(attacker.getLocation().toVector()).setY(0);
        if (launchDirection.lengthSquared() < 0.0001) {
            launchDirection = attacker.getLocation().getDirection().setY(0);
        }
        if (launchDirection.lengthSquared() > 0.0001) launchDirection.normalize();
        Vector finalLaunch = launchDirection.multiply(CINEMATIC_BLACK_CRIT_KNOCKBACK).setY(0.8);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            clearBlackCritStun(attacker);
            clearBlackCritStun(target);
            if (!target.isValid() || target.isDead()
                    || !target.getWorld().equals(attacker.getWorld())) return;
            target.setVelocity(finalLaunch);
            playReferenceCinematicTendrils(target.getLocation().add(0, 1.0, 0),
                    attacker.getLocation().add(0, 1.0, 0));
            target.getWorld().spawnParticle(Particle.EXPLOSION,
                    target.getLocation().add(0, 0.8, 0), 3, 0.35, 0.35, 0.35, 0.0);
            target.getWorld().spawnParticle(Particle.CLOUD,
                    target.getLocation().add(0, 0.5, 0), 30, 0.7, 0.45, 0.7, 0.18);
        }, CINEMATIC_BLACK_CRIT_LAUNCH_TICKS);

        if (plugin.getData().getBoolean("players." + attackerId + ".alerts", true)) {
            attacker.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(
                    ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "☠ CINEMATIC BLACK CRIT ☠"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onZoneDamageCharge(EntityDamageByEntityEvent event) {
        if (event.getFinalDamage() <= 0.0
                || !(event.getEntity() instanceof LivingEntity target)
                || target instanceof ArmorStand) return;

        LivingEntity damageSource = null;
        if (event.getDamager() instanceof LivingEntity livingDamager) {
            damageSource = livingDamager;
        } else if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof LivingEntity livingShooter) {
            damageSource = livingShooter;
        }

        long now = System.currentTimeMillis();
        if (hasPerfectDodgeDamageSafety(target.getUniqueId(), now)
                || (damageSource != null
                && hasPerfectDodgeDamageSafety(damageSource.getUniqueId(), now))) {
            guaranteeAcceptedDamage(event, target);
        }

        Player attacker = null;
        if (damageSource instanceof Player player) attacker = player;
        if (attacker == null || attacker == target) return;

        UUID attackerId = attacker.getUniqueId();
        if (shockwaveDamageLock.contains(attackerId)
                || afterimageDamageLock.contains(attackerId)) return;
        if (zoneEndTime.getOrDefault(attackerId, 0L) <= now) return;

        if (forcedBlackCritTests.remove(attackerId)) {
            boolean forceDouble = forcedDoubleBlackCritTests.remove(attackerId);
            triggerInZoneBlackCrit(attacker, target, event, now);
            if (forceDouble) armSecondBlackCrit(attacker, true);
            return;
        }

        boolean blackCritBlocked = now < blackCritBlockedUntil.getOrDefault(attackerId, 0L);

        if (cinematicBlackCritFollowUps.contains(attackerId)) {
            long followUpEnd = cinematicBlackCritFollowUpEnd.getOrDefault(attackerId, 0L);
            if (now >= followUpEnd) {
                clearSecondBlackCrit(attackerId);
            } else if (!blackCritBlocked) {
                clearSecondBlackCrit(attackerId);
                triggerCinematicBlackCrit(attacker, target, event);
                return;
            } else {
                return;
            }
        }

        if (zoneBlackCritCooldown.getOrDefault(attackerId, 0L) > now) return;

        double actualDamage = Math.min(event.getFinalDamage(), target.getHealth());
        double damageForFullCharge = Math.max(1.0, plugin.getConfig().getDouble(
                "combat.black-crit.zone-damage-for-full-charge",
                DEFAULT_ZONE_BLACK_DAMAGE_FOR_FULL_CHARGE));
        double gainedCharge = actualDamage * ZONE_BLACK_CHARGE_MAX / damageForFullCharge;
        double previousCharge = zoneBlackCharge.getOrDefault(attackerId, 0.0);
        double updatedCharge = Math.min(ZONE_BLACK_CHARGE_MAX, previousCharge + gainedCharge);

        for (int milestoneIndex = 1; milestoneIndex <= 4; milestoneIndex++) {
            double milestone = milestoneIndex * 25.0;
            if (previousCharge < milestone && updatedCharge >= milestone) {
                double milestoneChance = milestoneIndex * 0.10;
                if (!blackCritBlocked && Math.random() < milestoneChance) {
                    triggerInZoneBlackCrit(attacker, target, event, now);
                    return;
                }
            }
        }

        // A failed 100% milestone must be earned again instead of rolling on every full-bar hit.
        zoneBlackCharge.put(attackerId, updatedCharge >= ZONE_BLACK_CHARGE_MAX ? 75.0 : updatedCharge);
    }

    private void guaranteeAcceptedDamage(EntityDamageByEntityEvent event, LivingEntity target) {
        if (target == null || target.isDead() || !target.isValid()) return;
        guaranteedDamageEvents.putIfAbsent(event,
                new PendingGuaranteedDamage(target, target.getHealth()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onGuaranteedDamageMonitor(EntityDamageByEntityEvent event) {
        PendingGuaranteedDamage pending = guaranteedDamageEvents.remove(event);
        if (pending == null || event.isCancelled()) return;

        double finalDamage = event.getFinalDamage();
        if (finalDamage <= 0.0) return;
        double expectedHealth = Math.max(0.0, pending.healthBefore() - finalDamage);

        Bukkit.getScheduler().runTask(plugin, () -> {
            LivingEntity target = pending.target();
            if (!target.isValid() || target.isDead() || target.getHealth() <= expectedHealth + 0.001) return;

            // Vanilla occasionally accepts the event but drops its health change due
            // to invulnerability-frame state. Never heal or double-hit: only fill the
            // exact amount missing from the already-calculated final damage.
            target.setHealth(expectedHealth);
            if (expectedHealth > 0.0) target.playHurtAnimation(0.0f);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLightTier3SuccessfulHit(EntityDamageByEntityEvent event) {
        if (event.getFinalDamage() <= 0.0 || !(event.getEntity() instanceof Player target)) return;

        Player attacker = null;
        if (event.getDamager() instanceof Player player) {
            attacker = player;
        } else if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            attacker = shooter;
        }
        if (attacker == null || attacker == target) return;

        UUID attackerId = attacker.getUniqueId();
        if (shockwaveDamageLock.contains(attackerId)
                || afterimageDamageLock.contains(attackerId)) return;
        if (zoneEndTime.getOrDefault(attackerId, 0L) <= System.currentTimeMillis()) return;
        if (!plugin.getData().getString(
                "players." + attackerId + ".zone.tier3", "none").equals("light")) return;

        UUID targetId = target.getUniqueId();
        Map<UUID, Integer> hitMap = lightHits.computeIfAbsent(attackerId, key -> new HashMap<>());
        int hits = hitMap.getOrDefault(targetId, 0) + 1;
        if (hits < 3) {
            hitMap.put(targetId, hits);
            return;
        }

        hitMap.put(targetId, 0);
        Map<UUID, Integer> stackMap = lightDebuffs.computeIfAbsent(attackerId, key -> new HashMap<>());
        int stacks = Math.min(5, stackMap.getOrDefault(targetId, 0) + 1);
        stackMap.put(targetId, stacks);
        applyLightTier3Debuff(attacker, target, stacks);
        updateLightStackDisplay(attacker, target, stacks);
        target.getWorld().spawnParticle(Particle.WITCH, target.getLocation().add(0, 2, 0), 15);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity(); UUID vId = victim.getUniqueId();
        clearBlackCritStun(victim);
        clearLightEffectsOnTarget(victim);
        revealPlayer(victim);

        yellowStacks.remove(vId); orangeStacks.remove(vId); redStacks.remove(vId);
        zoneEndTime.remove(vId); zoneBlackCharge.remove(vId); clearSecondBlackCrit(vId); clearColoredCritDecay(vId);
        perfectDodgeDamageSafetyUntil.remove(vId);
        skillUseLockedUntil.remove(vId); blackCritBlockedUntil.remove(vId);
        heavyStacks.remove(vId); heavyHitCount.remove(vId);
        lightDmgTaken.remove(vId); lightHits.remove(vId); clearPerfectDodgeState(vId); clearLightDebuffs(vId);

        victim.removePotionEffect(PotionEffectType.SLOWNESS);
        if (victim.getAttribute(Attribute.GENERIC_ATTACK_SPEED) != null) {
            victim.getAttribute(Attribute.GENERIC_ATTACK_SPEED).setBaseValue(4.0);
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

        int vKills = plugin.getData().getInt("players." + vId + ".kills", 0);
        int vOverflow = plugin.getData().getInt("players." + vId + ".overflow", 0);
        int vTotal = vKills + vOverflow;
        plugin.getData().set("players." + vId + ".pending_heart_loss", true);
        plugin.getData().set("players." + vId + ".kills", 0);

        if (killer != null && killer != victim) {
            UUID kId = killer.getUniqueId();
            String ipPair = getConnectionIdentity(killer) + "_" + getConnectionIdentity(victim);

            if (killCooldowns.containsKey(ipPair) && System.currentTimeMillis() - killCooldowns.get(ipPair) < 300000) {
                plugin.saveData(); return;
            }
            killCooldowns.put(ipPair, System.currentTimeMillis());

            int kKills = plugin.getData().getInt("players." + kId + ".kills", 0);
            int kOverflow = plugin.getData().getInt("players." + kId + ".overflow", 0);
            int kTotal = kKills + kOverflow;
            int diff = vTotal - kTotal;

            boolean dropAtCorpse = plugin.getConfig().getBoolean("settings.drop_at_corpse", true);

            if (diff >= 40 || (vTotal >= kTotal * 2 && diff >= 25)) {
                double stealPercent = Math.min(60.0, diff * 0.5) / 100.0;
                int overflowSteal = (int) Math.ceil(vOverflow * stealPercent);
                int totalSouls = 1 + (diff / 30);

                plugin.getData().set("players." + vId + ".overflow", Math.max(0, vOverflow - overflowSteal));
                plugin.getData().set("players." + kId + ".kills", kKills + 1);

                if (dropAtCorpse) {
                    dropStars(victim.getLocation(), "soul", totalSouls);
                    if (overflowSteal > 0) dropStars(victim.getLocation(), "triumph", overflowSteal);
                } else {
                    plugin.giveRewardSmart(killer, "soul", totalSouls);
                    if (overflowSteal > 0) plugin.giveRewardSmart(killer, "triumph", overflowSteal);
                }
            } else if (diff > -40) {
                plugin.getData().set("players." + kId + ".kills", kKills + 1);
                if (dropAtCorpse) dropStars(victim.getLocation(), "soul", 1);
                else plugin.giveRewardSmart(killer, "soul", 1);
            }
        }
        plugin.saveData();
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer(); UUID uuid = player.getUniqueId();
        clearBlackCritStun(player);
        skillUseLockedUntil.remove(uuid); blackCritBlockedUntil.remove(uuid);
        String t1 = plugin.getData().getString("players." + uuid + ".zone.tier1", "none");
        updateBaseAttackSpeed(player, t1, 0);
        player.removePotionEffect(PotionEffectType.SLOWNESS);

        if (plugin.getData().getBoolean("players." + uuid + ".pending_heart_loss", false)) {
            plugin.getData().set("players." + uuid + ".pending_heart_loss", false); plugin.saveData();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                double newMax = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue() - 2.0;
                if (newMax <= 0.0) {
                    player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0); player.setHealth(20.0);
                    plugin.getData().set("players." + uuid + ".kills", 0); plugin.getData().set("players." + uuid + ".overflow", 0); plugin.saveData();
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
        forcedBlackCritTests.remove(p.getUniqueId());
        forcedDoubleBlackCritTests.remove(p.getUniqueId());
        perfectDodgeDamageSafetyUntil.remove(p.getUniqueId());
        skillUseLockedUntil.remove(p.getUniqueId());
        blackCritBlockedUntil.remove(p.getUniqueId());
        clearBlackCritStun(p);
        clearLightEffectsOnTarget(p);
        clearPerfectDodgeState(p.getUniqueId());
        revealPlayer(p);
        if (zoneEndTime.containsKey(p.getUniqueId())) {
            endTheZone(p);
        } else {
            String t1 = plugin.getData().getString("players." + p.getUniqueId() + ".zone.tier1", "none");
            updateBaseAttackSpeed(p, t1, 0);
            p.removePotionEffect(PotionEffectType.SLOWNESS);
        }
        clearLightDebuffs(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent event) {
        Player p = event.getPlayer();
        long now = System.currentTimeMillis();
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) return;

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
                Location blockedPosition = from.clone();
                blockedPosition.setYaw(to.getYaw());
                blockedPosition.setPitch(to.getPitch());
                event.setTo(blockedPosition);
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

        String t2 = plugin.getData().getString("players." + uuid + ".zone.tier2", "none");
        if (!t2.equals("light")) return;

        ItemStack mainHand = p.getInventory().getItemInMainHand();
        if (mainHand == null || !mainHand.getType().name().endsWith("_SWORD")) return;

        boolean inZone = zoneEndTime.containsKey(uuid) && zoneEndTime.get(uuid) > now;
        if (!inZone) return;

        event.setCancelled(true);

        if (isSkillUseLocked(p, now)) return;

        long lastUse = afterimageCD.getOrDefault(uuid, 0L);
        if (now < lastUse + 20000L) {
            long remaining = (lastUse + 20000L - now + 999) / 1000;
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ChatColor.RED + "§l⚠ Afterimage Cooldown: " + remaining + "s"));
            p.sendMessage(plugin.PREFIX + ChatColor.RED + "Afterimage is on cooldown for " + remaining + "s!");
            return;
        }

        afterimageCD.put(uuid, now);
        recordSkillUse(p, now);
        triggerAfterimage(p);
    }

    private void triggerAfterimage(Player p) {
        UUID uuid = p.getUniqueId();
        boolean shieldOffhand = isOffhandShield(p);
        boolean swordOffhand = isOffhandSword(p);
        afterimageOriginalWalkSpeed.putIfAbsent(uuid, p.getWalkSpeed());
        float afterimageWalkSpeed = shieldOffhand
                ? (float) plugin.getConfig().getDouble("combat.light-offhand.shield-afterimage-walk-speed", 0.36)
                : 0.44f;
        p.setWalkSpeed(Math.max(0.0f, Math.min(1.0f, afterimageWalkSpeed)));
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 40, 0, false, false, true));

        if (!shieldOffhand) {
            afterimageHidden.add(p.getUniqueId());
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other != p && other.isOnline()) other.hidePlayer(plugin, p);
            }
        }

        boolean alertsEnabled = plugin.getData().getBoolean("players." + uuid + ".alerts", true);
        if (alertsEnabled) {
            String modeText = shieldOffhand
                    ? "Speed IV + potion Invisibility; true hiding disabled"
                    : "Speed VI + Invisibility + hidden player";
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(
                    ChatColor.AQUA + "§l⚡ AFTERIMAGE: " + modeText + " ⚡"));
        }

        final Location centerLoc = p.getLocation().clone();
        final double maxRadius = 4.8 + (swordOffhand
                ? Math.max(0.0, plugin.getConfig().getDouble(
                        "combat.light-offhand.sword-max-afterimage-range-bonus", 3.0))
                        * getOffhandSwordScale(p)
                : 0.0);

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
                    String handMode = plugin.getData().getString("players." + p.getUniqueId() + ".zone.hand_mode", "normal");
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

    private String getConnectionIdentity(Player player) {
        if (player.getAddress() != null && player.getAddress().getAddress() != null) {
            return player.getAddress().getAddress().getHostAddress();
        }
        return player.getUniqueId().toString();
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

        String handMode = plugin.getData().getString("players." + uuid + ".zone.hand_mode", "normal");
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
