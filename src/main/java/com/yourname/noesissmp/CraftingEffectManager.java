package com.yourname.noesissmp;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Random;

public class CraftingEffectManager {

    private static final Random random = new Random();

    // Pure black color for lightning tendrils
    private static final Color COLOR_BLACK = Color.fromRGB(0, 0, 0);
    private static final Particle.DustOptions PURE_BLACK_DUST = new Particle.DustOptions(COLOR_BLACK, 1.4f);
    private static final Particle.DustOptions THICK_BLACK_DUST = new Particle.DustOptions(COLOR_BLACK, 2.6f);

    /**
     * Ambient particle effect when Crafting Table / Altar is READY / OPEN.
     * Displays sharp black lightning arcs around the crafting table and enchanting glyphs.
     */
    public static void playAmbientEffect(Location blockLoc) {
        if (blockLoc == null || blockLoc.getWorld() == null) return;

        // Positioned 1 block lower, directly at the crafting table base
        Location tableLoc = blockLoc.clone().add(0.5, 0.0, 0.5);

        // 1. Enchanting particles swirling around the crafting table
        tableLoc.getWorld().spawnParticle(Particle.ENCHANT, tableLoc.clone().add(0, 0.5, 0), 12, 0.7, 0.5, 0.7, 0.6);

        // 2. Sharp black electric lightning arcs crackling around the crafting table
        if (random.nextDouble() < 0.8) {
            for (int t = 0; t < 3; t++) {
                Vector dir = new Vector(
                        (random.nextDouble() - 0.5) * 1.5,
                        random.nextDouble() * 0.8 + 0.1,
                        (random.nextDouble() - 0.5) * 1.5
                ).normalize();

                Location curr = tableLoc.clone().add((random.nextDouble() - 0.5) * 0.8, random.nextDouble() * 0.6, (random.nextDouble() - 0.5) * 0.8);

                double arcLength = 2.5 + random.nextDouble() * 2.5;
                for (double s = 0; s < arcLength; s += 0.2) {
                    if (random.nextDouble() < 0.35) {
                        dir.add(new Vector(
                                (random.nextDouble() - 0.5) * 0.7,
                                (random.nextDouble() - 0.5) * 0.6,
                                (random.nextDouble() - 0.5) * 0.7
                        )).normalize();
                    }
                    curr.add(dir.clone().multiply(0.2));
                    tableLoc.getWorld().spawnParticle(Particle.DUST, curr, 1, 0, 0, 0, 0, PURE_BLACK_DUST);
                    if (random.nextDouble() < 0.25) {
                        tableLoc.getWorld().spawnParticle(Particle.SQUID_INK, curr, 1, 0, 0, 0, 0.01);
                    }
                }
            }
        }
    }

    /**
     * Massive black lightning burst effect when Crafting Table / Altar is USED, ACTIVATED or APPEARS.
     * Pure black branching lightning arcing out from the crafting table with enchanting glyphs.
     */
    public static void playActivationEffect(JavaPlugin plugin, Location blockLoc) {
        if (blockLoc == null || blockLoc.getWorld() == null) return;

        // Positioned 1 block lower, directly at the crafting table base
        Location tableLoc = blockLoc.clone().add(0.5, 0.0, 0.5);

        // 1. Enchanting particle burst around the table
        tableLoc.getWorld().spawnParticle(Particle.ENCHANT, tableLoc.clone().add(0, 0.5, 0), 100, 1.5, 1.0, 1.5, 1.2);

        // 2. Animated Far-Reaching Branching Black Lightning
        new BukkitRunnable() {
            int wave = 0;

            @Override
            public void run() {
                if (wave >= 6) {
                    this.cancel();
                    return;
                }

                // 10 main black lightning bolts per wave = 60 bolts total
                for (int b = 0; b < 10; b++) {
                    double dx = (random.nextDouble() - 0.5) * 2.0;
                    double dy = random.nextDouble() * 1.5 + 0.2; // Upward & outward surge
                    double dz = (random.nextDouble() - 0.5) * 2.0;
                    Vector direction = new Vector(dx, dy, dz).normalize();

                    Location current = tableLoc.clone().add(0, 0.2, 0);
                    float initialScale = 3.0f;

                    // Farther reach: 20 to 38 blocks!
                    double maxReach = 20.0 + (random.nextDouble() * 18.0);

                    for (double step = 0; step < maxReach; step += 0.22) {
                        // Sharp angular lightning zig-zags
                        if (random.nextDouble() < 0.38) {
                            direction.add(new Vector(
                                    (random.nextDouble() - 0.5) * 0.75,
                                    (random.nextDouble() - 0.5) * 0.65,
                                    (random.nextDouble() - 0.5) * 0.75
                            )).normalize();
                        }
                        current.add(direction.clone().multiply(0.22));

                        float currentScale = (float) Math.max(0.7, initialScale * (1.0 - (step / maxReach)));
                        Particle.DustOptions stepDust = new Particle.DustOptions(COLOR_BLACK, currentScale);

                        current.getWorld().spawnParticle(Particle.DUST, current, 1, 0, 0, 0, 0, stepDust);

                        if (random.nextDouble() < 0.3) {
                            current.getWorld().spawnParticle(Particle.SQUID_INK, current, 1, 0, 0, 0, 0.01);
                        }

                        // Primary Branch Forks (แตกกิ่งสายฟ้าแยกออกไปไกล)
                        if (random.nextDouble() < 0.07 && step < maxReach * 0.85) {
                            Vector branchDir = direction.clone().add(new Vector(
                                    (random.nextDouble() - 0.5) * 1.1,
                                    (random.nextDouble() - 0.5) * 0.9,
                                    (random.nextDouble() - 0.5) * 1.1
                            )).normalize();

                            Location branchCurrent = current.clone();
                            double branchLength = 6.0 + random.nextDouble() * 10.0; // 6 to 16 blocks branch reach

                            for (double branchStep = 0; branchStep < branchLength; branchStep += 0.22) {
                                if (random.nextDouble() < 0.35) {
                                    branchDir.add(new Vector(
                                            (random.nextDouble() - 0.5) * 0.7,
                                            (random.nextDouble() - 0.5) * 0.6,
                                            (random.nextDouble() - 0.5) * 0.7
                                    )).normalize();
                                }
                                branchCurrent.add(branchDir.clone().multiply(0.22));

                                float branchScale = (float) Math.max(0.5, 1.8 * (1.0 - (branchStep / branchLength)));
                                branchCurrent.getWorld().spawnParticle(
                                        Particle.DUST, branchCurrent, 1, 0, 0, 0, 0,
                                        new Particle.DustOptions(COLOR_BLACK, branchScale)
                                );

                                if (random.nextDouble() < 0.2) {
                                    branchCurrent.getWorld().spawnParticle(Particle.SQUID_INK, branchCurrent, 1, 0, 0, 0, 0.01);
                                }

                                // Secondary Sub-branch Fork
                                if (random.nextDouble() < 0.05 && branchStep < branchLength * 0.7) {
                                    Vector subDir = branchDir.clone().add(new Vector(
                                            (random.nextDouble() - 0.5) * 1.2,
                                            (random.nextDouble() - 0.5) * 1.0,
                                            (random.nextDouble() - 0.5) * 1.2
                                    )).normalize();

                                    Location subCurrent = branchCurrent.clone();
                                    double subLength = 3.0 + random.nextDouble() * 5.0;

                                    for (double subStep = 0; subStep < subLength; subStep += 0.25) {
                                        subCurrent.add(subDir.clone().multiply(0.25));
                                        subCurrent.getWorld().spawnParticle(
                                                Particle.DUST, subCurrent, 1, 0, 0, 0, 0,
                                                new Particle.DustOptions(COLOR_BLACK, 0.9f)
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

        // 3. Sound Effects
        tableLoc.getWorld().playSound(tableLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 0.5f);
        tableLoc.getWorld().playSound(tableLoc, Sound.ITEM_TRIDENT_THUNDER, 1.6f, 0.7f);
        tableLoc.getWorld().playSound(tableLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.5f, 0.7f);
        tableLoc.getWorld().playSound(tableLoc, Sound.BLOCK_PORTAL_TRAVEL, 0.8f, 1.4f);
    }

    /**
     * Interaction burst when player opens or clicks the Crafting Table.
     */
    public static void playInteractEffect(Location blockLoc, Player player) {
        if (blockLoc == null || blockLoc.getWorld() == null) return;

        Location tableLoc = blockLoc.clone().add(0.5, 0.0, 0.5);

        // Black electric burst & enchanting particles right at crafting table
        tableLoc.getWorld().spawnParticle(Particle.ENCHANT, tableLoc.clone().add(0, 0.5, 0), 30, 0.6, 0.4, 0.6, 0.8);
        for (int i = 0; i < 25; i++) {
            Vector v = new Vector(random.nextDouble() - 0.5, random.nextDouble() * 0.6 + 0.1, random.nextDouble() - 0.5).normalize().multiply(0.35);
            tableLoc.getWorld().spawnParticle(Particle.DUST, tableLoc.clone().add(0, 0.5, 0), 1, v.getX(), v.getY(), v.getZ(), 0.1, THICK_BLACK_DUST);
        }

        if (player != null) {
            player.playSound(blockLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 0.8f);
        }
    }
}
