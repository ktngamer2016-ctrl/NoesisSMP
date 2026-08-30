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
    private final Set<UUID> pendingSwing = new HashSet<>();
    private final Map<UUID, Integer> heavyStacks = new HashMap<>();
    private final Map<UUID, Integer> heavyHitCount = new HashMap<>();
    private final Map<UUID, DomainData> activeDomains = new HashMap<>();
    private final Set<UUID> shockwaveDamageLock = new HashSet<>();

    private final Map<UUID, Long> lightDodgeCD = new HashMap<>();
    private final Map<UUID, Double> lightDmgTaken = new HashMap<>();
    private final Map<UUID, Integer> lightHits = new HashMap<>();
    private final Map<UUID, Map<UUID, Integer>> lightDebuffs = new HashMap<>();
    private final Map<UUID, Double> origSpeed = new HashMap<>();
    private final Map<UUID, Double> origAtkSpeed = new HashMap<>();

    class DomainData {
        Location center; long endTime;
        public DomainData(Location c, long t) { center = c; endTime = t; }
    }

    public CombatListener(NoesisSMP plugin) {
        this.plugin = plugin;
        startCritDecayTask();
    }

    private void updateBaseAttackSpeed(Player p, String t1, int stack) {
        double base = 4.0;
        if (t1.equals("heavy")) base *= 0.75;
        else if (t1.equals("light")) base *= 1.15;

        base = base * (1.0 - (stack * 0.05)); // ลบ 5% จาก Heavy U2
        if (p.getAttribute(Attribute.GENERIC_ATTACK_SPEED) != null) {
            p.getAttribute(Attribute.GENERIC_ATTACK_SPEED).setBaseValue(base);
        }
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

                    for (int degree = 0; degree < 360; degree += 15) {
                        double rad = Math.toRadians(degree);
                        double x = center.getX() + (10 * Math.cos(rad));
                        double z = center.getZ() + (10 * Math.sin(rad));
                        center.getWorld().spawnParticle(Particle.DUST, x, center.getY() + 1, z, 1, 0, 0, 0, new Particle.DustOptions(org.bukkit.Color.MAROON, 1.5f));
                    }

                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.getUniqueId().equals(entry.getKey())) continue;
                        if (p.getWorld().equals(center.getWorld())) {
                            double dist = p.getLocation().distance(center);
                            if (dist > 9.5 && dist < 12.0) {
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
                        p.getAttribute(Attribute.GENERIC_ATTACK_SPEED).setBaseValue(4.0);
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
                            String extraText = "";
                            String t2 = plugin.getConfig().getString("players." + uuid + ".zone.tier2", "none");
                            if (t2.equals("heavy")) {
                                int s = heavyStacks.getOrDefault(uuid, 0);
                                extraText = ChatColor.RED + " [Combo: " + s + "/3]";
                            }
                            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ChatColor.DARK_GRAY + "【 " + ChatColor.DARK_PURPLE + ChatColor.BOLD + "THE ZONE" + ChatColor.DARK_GRAY + " 】" + extraText));
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
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction().name().contains("RIGHT_CLICK")) {
            Player p = e.getPlayer();
            if (p.getInventory().getItemInMainHand().getType() == Material.SHIELD || p.getInventory().getItemInOffHand().getType() == Material.SHIELD) {
                if (!p.hasCooldown(Material.SHIELD)) shieldRaiseTime.put(p.getUniqueId(), System.currentTimeMillis());
            }
        }
    }

    @EventHandler
    public void onArmSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player p = e.getPlayer(); UUID uuid = p.getUniqueId();

        if (zoneEndTime.containsKey(uuid) && zoneEndTime.get(uuid) > System.currentTimeMillis()) {
            String t2 = plugin.getConfig().getString("players." + uuid + ".zone.tier2", "none");
            if (t2.equals("heavy")) {
                pendingSwing.add(uuid);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (pendingSwing.contains(uuid)) {
                        pendingSwing.remove(uuid);
                        if (heavyStacks.getOrDefault(uuid, 0) > 0) {
                            heavyStacks.put(uuid, 0);
                            String t1 = plugin.getConfig().getString("players." + uuid + ".zone.tier1", "none");
                            updateBaseAttackSpeed(p, t1, 0);
                            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.5f, 1.5f);
                        }
                    }
                }, 3L);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        Player p = null; boolean isRanged = false;

        if (event.getDamager() instanceof Player) p = (Player) event.getDamager();
        else if (event.getDamager() instanceof org.bukkit.entity.AbstractArrow arrow && arrow.getShooter() instanceof Player shooter) { p = shooter; isRanged = true; }

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

                // HEAVY U1: Parry
                if (vt1.equals("heavy") && p != null && victim.isBlocking()) {
                    long raiseTime = shieldRaiseTime.getOrDefault(vId, 0L);
                    if (now - raiseTime <= 350) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 20, 1));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 1));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20, 0));
                        victim.playSound(victim.getLocation(), Sound.BLOCK_ANVIL_PLACE, 1f, 2f);
                        victim.getWorld().spawnParticle(Particle.FLASH, p.getLocation().add(0, 1, 0), 10);
                        if (vAlerts) victim.sendMessage(plugin.PREFIX + ChatColor.RED + "Perfect Parry! Attacker Stunned!");
                    }
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
        boolean isValidTarget = (event.getEntity() instanceof Player) || (plugin.eventManager != null && plugin.eventManager.isEventBoss(event.getEntity().getUniqueId()));

        boolean inZone = zoneEndTime.containsKey(uuid) && zoneEndTime.get(uuid) > now;
        String t1 = plugin.getConfig().getString("players." + uuid + ".zone.tier1", "none");
        String t2 = plugin.getConfig().getString("players." + uuid + ".zone.tier2", "none");
        String t3 = plugin.getConfig().getString("players." + uuid + ".zone.tier3", "none");

        pendingSwing.remove(uuid);

        boolean isVanillaCrit = false;
        if (!isRanged) { isVanillaCrit = (p.getFallDistance() > 0.0F && !p.isOnGround() && !p.hasPotionEffect(PotionEffectType.BLINDNESS) && p.getVehicle() == null && !p.isSprinting()); }
        else { if (event.getDamager() instanceof org.bukkit.entity.AbstractArrow arrow) isVanillaCrit = arrow.isCritical(); }

        if (inZone) {
            boolean isOrangeCrit = Math.random() < 0.5;
            double mult = isOrangeCrit ? 2.0 : 1.5;

            // Passive Mix
            if (t1.equals("heavy")) mult *= 1.5;
            if (t1.equals("light")) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 100, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1));
            }

            // TIER 2 Mix
            if (t2.equals("heavy") && isValidTarget) {
                int stack = heavyStacks.getOrDefault(uuid, 0);
                if (stack < 3) {
                    stack++; heavyStacks.put(uuid, stack);
                    updateBaseAttackSpeed(p, t1, stack);
                }
                mult *= (1.0 + (stack * 0.10));
            } else if (t2.equals("light") && Math.random() <= 0.35) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20, 3));
                ArmorStand clone = p.getWorld().spawn(p.getLocation(), ArmorStand.class);
                clone.setVisible(false); clone.setArms(true); clone.setBasePlate(false);
                clone.getEquipment().setArmorContents(p.getInventory().getArmorContents());
                clone.getEquipment().setItemInMainHand(p.getInventory().getItemInMainHand());
                clone.getEquipment().setHelmet(new ItemStack(Material.PLAYER_HEAD));
                Bukkit.getScheduler().runTaskLater(plugin, clone::remove, 6L);
            }

            // TIER 3 Mix
            if (t3.equals("heavy") && isValidTarget) {
                int hits = heavyHitCount.getOrDefault(uuid, 0) + 1;
                if (hits >= 5) {
                    hits = 0;
                    Location loc = p.getLocation();
                    p.getWorld().playSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 0.5f);
                    p.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 2);

                    shockwaveDamageLock.add(uuid);
                    for (org.bukkit.entity.Entity ent : p.getNearbyEntities(10, 10, 10)) {
                        if (ent instanceof LivingEntity t && ent != p) {
                            t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                            t.setVelocity(new Vector(0, 0.8, 0));
                            t.damage(5.0, p);
                        }
                    }
                    shockwaveDamageLock.remove(uuid);

                    activeDomains.put(p.getUniqueId(), new DomainData(loc.clone(), now + 10000));
                    p.sendMessage(plugin.PREFIX + ChatColor.DARK_RED + ChatColor.BOLD + "Domain Expansion: Shockwave Activated!");
                }
                heavyHitCount.put(uuid, hits);
            } else if (t3.equals("light") && targetEnt != null) {
                int hits = lightHits.getOrDefault(uuid, 0) + 1;
                if (hits >= 3) {
                    hits = 0;
                    UUID tId = targetEnt.getUniqueId();
                    Map<UUID, Integer> dMap = lightDebuffs.computeIfAbsent(uuid, k -> new HashMap<>());
                    int stacks = dMap.getOrDefault(tId, 0) + 1;
                    if (stacks > 10) stacks = 10;
                    dMap.put(tId, stacks);

                    if (!origSpeed.containsKey(tId)) {
                        if (targetEnt.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) origSpeed.put(tId, targetEnt.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).getBaseValue());
                        if (targetEnt.getAttribute(Attribute.GENERIC_ATTACK_SPEED) != null) origAtkSpeed.put(tId, targetEnt.getAttribute(Attribute.GENERIC_ATTACK_SPEED).getBaseValue());
                    }

                    double red = stacks * 0.05;
                    if (targetEnt.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) targetEnt.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(origSpeed.get(tId) * (1.0 - red));
                    if (targetEnt.getAttribute(Attribute.GENERIC_ATTACK_SPEED) != null) targetEnt.getAttribute(Attribute.GENERIC_ATTACK_SPEED).setBaseValue(origAtkSpeed.get(tId) * (1.0 - red));

                    targetEnt.getWorld().spawnParticle(Particle.WITCH, targetEnt.getLocation().add(0,2,0), 15);
                }
                lightHits.put(uuid, hits);
            }

            if (isVanillaCrit) mult /= 1.5;
            event.setDamage(event.getDamage() * mult);

            if (isOrangeCrit) {
                p.getWorld().spawnParticle(Particle.DUST, targetLoc, 25, 0.5, 0.5, 0.5, new Particle.DustOptions(org.bukkit.Color.ORANGE, 2.0f));
                if (alertsEnabled) p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.6f, 0.8f);
            } else {
                p.getWorld().spawnParticle(Particle.DUST, targetLoc, 15, 0.5, 0.5, 0.5, new Particle.DustOptions(org.bukkit.Color.YELLOW, 1.5f));
                if (alertsEnabled) p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.5f, 1.2f);
            }
            if (isValidTarget) lastValidHitTime.put(uuid, now);
            return;
        }

        int totalPoints = plugin.getConfig().getInt("players." + uuid + ".kills", 0) + plugin.getConfig().getInt("players." + uuid + ".overflow", 0);
        double critChance = Math.min(60.0, totalPoints * 0.8);
        if (plugin.eventManager != null && plugin.eventManager.isPvpBoss(uuid)) critChance += 70.0;
        if (isRanged && event.getDamager() instanceof org.bukkit.entity.AbstractArrow arrow && arrow.isShotFromCrossbow()) critChance -= 10.0;
        if (critChance < 0) critChance = 0.0;

        if (critChance > 0 && Math.random() * 100 < critChance) {
            if (isValidTarget) {
                lastValidHitTime.put(uuid, now);

                int y = yellowStacks.getOrDefault(uuid, 0); int o = orangeStacks.getOrDefault(uuid, 0); int r = redStacks.getOrDefault(uuid, 0);
                boolean isYellow = true, isOrange = false, isRed = false, isBlack = false;

                if (totalPoints >= 30 && y >= 7 && Math.random() * 100 < 25.0) { isYellow = false; isOrange = true; }
                if (isOrange && totalPoints >= 60 && o >= 7 && Math.random() * 100 < 15.0) { isOrange = false; isRed = true; }

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
                    zoneEndTime.put(uuid, now + 60000);
                    yellowStacks.put(uuid, 0); orangeStacks.put(uuid, 0); redStacks.put(uuid, 0);

                    if (t1.equals("heavy")) p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 1200, 0));
                    updateBaseAttackSpeed(p, t1, 0);

                    heavyStacks.put(uuid, 0); heavyHitCount.put(uuid, 0);
                    lightDmgTaken.put(uuid, 0.0); lightHits.put(uuid, 0);

                    double mult = 4.0;
                    if (isVanillaCrit) mult /= 1.5;
                    event.setDamage(event.getDamage() * mult);

                    p.getWorld().spawnParticle(Particle.SONIC_BOOM, targetLoc, 1);
                    p.getWorld().spawnParticle(Particle.SCULK_SOUL, targetLoc, 30, 0.5, 0.5, 0.5, 0.1);
                    if (alertsEnabled) {
                        p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 0.8f);
                        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "☠ BLACK CRIT - THE ZONE ☠"));
                    }
                }
                else if (isRed) {
                    redStacks.put(uuid, r + 1);
                    double mult = 3.0; if (isVanillaCrit) mult /= 1.5; event.setDamage(event.getDamage() * mult);
                    p.getWorld().spawnParticle(Particle.DUST, targetLoc, 40, 0.5, 0.5, 0.5, new Particle.DustOptions(org.bukkit.Color.RED, 2.5f));
                    p.getWorld().spawnParticle(Particle.EXPLOSION, targetLoc, 1);
                    if (alertsEnabled) p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.4f, 2f);
                }
                else if (isOrange) {
                    orangeStacks.put(uuid, o + 1);
                    double mult = 2.0; if (isVanillaCrit) mult /= 1.5; event.setDamage(event.getDamage() * mult);
                    p.getWorld().spawnParticle(Particle.DUST, targetLoc, 25, 0.5, 0.5, 0.5, new Particle.DustOptions(org.bukkit.Color.ORANGE, 2.0f));
                    if (alertsEnabled) p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.6f, 0.8f);
                }
                else if (isYellow) {
                    yellowStacks.put(uuid, y + 1);
                    double mult = 1.5; if (isVanillaCrit) mult /= 1.5; event.setDamage(event.getDamage() * mult);
                    p.getWorld().spawnParticle(Particle.DUST, targetLoc, 15, 0.5, 0.5, 0.5, new Particle.DustOptions(org.bukkit.Color.YELLOW, 1.5f));
                    if (alertsEnabled) p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.5f, 1.2f);
                }
            } else {
                double mult = 1.5; if (isVanillaCrit) mult /= 1.5; event.setDamage(event.getDamage() * mult);
                p.getWorld().spawnParticle(Particle.DUST, targetLoc, 15, 0.5, 0.5, 0.5, new Particle.DustOptions(org.bukkit.Color.YELLOW, 1.5f));
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

        yellowStacks.remove(vId); orangeStacks.remove(vId); redStacks.remove(vId);
        zoneEndTime.remove(vId); lastValidHitTime.remove(vId);
        heavyStacks.remove(vId); heavyHitCount.remove(vId);
        lightDmgTaken.remove(vId); lightHits.remove(vId); clearLightDebuffs(vId);

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
}