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

    // Color Palette matching the picture
    private static final Color COLOR_BLACK = Color.fromRGB(5, 5, 5);
    private static final Color COLOR_DEEP_PURPLE = Color.fromRGB(120, 0, 200);
    private static final Color COLOR_VIBRANT_PURPLE = Color.fromRGB(190, 40, 255);
    private static final Color COLOR_NEON_MAGENTA = Color.fromRGB(240, 80, 255);

    /**
     * Ambient particle effect when Crafting Table / Altar is READY / OPEN.
     * Displays a floating dark-purple void orb with small electric tendrils wrapping around it.
     */
    public static void playAmbientEffect(Location blockLoc) {
        if (blockLoc == null || blockLoc.getWorld() == null) return;

        Location coreLoc = blockLoc.clone().add(0.5, 1.8, 0.5);

        // 1. Ambient Core Sphere (Small pulsing orb)
        for (int i = 0; i < 35; i++) {
            double u = random.nextDouble();
            double v = random.nextDouble();
            double theta = u * 2.0 * Math.PI;
            double phi = Math.acos(2.0 * v - 1.0);
            double r = 0.6 + random.nextDouble() * 0.3;

            double x = r * Math.sin(phi) * Math.cos(theta);
            double y = r * Math.sin(phi) * Math.sin(theta);
            double z = r * Math.cos(phi);

            Color color = random.nextBoolean() ? COLOR_BLACK : (random.nextBoolean() ? COLOR_DEEP_PURPLE : COLOR_VIBRANT_PURPLE);
            float scale = 1.2f + random.nextFloat() * 0.8f;
            coreLoc.getWorld().spawnParticle(
                    Particle.DUST,
                    coreLoc.clone().add(x, y, z),
                    1, 0, 0, 0, 0,
                    new Particle.DustOptions(color, scale)
            );
        }

        // 2. Micro Electric Tendrils flaring outward from ambient core
        if (random.nextDouble() < 0.6) {
            for (int t = 0; t < 2; t++) {
                Vector dir = new Vector(random.nextDouble() - 0.5, random.nextDouble() * 0.8 + 0.1, random.nextDouble() - 0.5).normalize();
                Location curr = coreLoc.clone();
                Color c = random.nextBoolean() ? COLOR_BLACK : COLOR_NEON_MAGENTA;
                Particle.DustOptions dust = new Particle.DustOptions(c, 1.2f);

                for (double s = 0; s < 3.0; s += 0.3) {
                    dir.add(new Vector((random.nextDouble() - 0.5) * 0.3, (random.nextDouble() - 0.5) * 0.3, (random.nextDouble() - 0.5) * 0.3)).normalize();
                    curr.add(dir.clone().multiply(0.3));
                    coreLoc.getWorld().spawnParticle(Particle.DUST, curr, 1, 0, 0, 0, 0, dust);
                }
            }
        }

        // 3. Subtle ambient portal dust
        coreLoc.getWorld().spawnParticle(Particle.PORTAL, coreLoc, 6, 0.2, 0.2, 0.2, 0.02);
    }

    /**
     * Massive epic burst effect when Crafting Table / Altar is USED or ACTIVATED.
     * Matches the exact picture with a dense dark purple-black sphere and dozens of branching jagged tendrils.
     */
    public static void playActivationEffect(JavaPlugin plugin, Location blockLoc) {
        if (blockLoc == null || blockLoc.getWorld() == null) return;

        Location coreLoc = blockLoc.clone().add(0.5, 2.2, 0.5);

        // 1. Dense Core Void Sphere (ก้อนพลังงานตรงกลางสีม่วง-ดำอัดแน่น)
        for (int i = 0; i < 700; i++) {
            Vector offset = new Vector(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5).multiply(3.0);
            if (offset.lengthSquared() <= 2.25) { // Radius ~1.5 blocks
                Color c;
                double randVal = random.nextDouble();
                if (randVal < 0.45) c = COLOR_BLACK;
                else if (randVal < 0.80) c = COLOR_DEEP_PURPLE;
                else if (randVal < 0.95) c = COLOR_VIBRANT_PURPLE;
                else c = COLOR_NEON_MAGENTA;

                float size = 1.8f + random.nextFloat() * 1.2f;
                coreLoc.getWorld().spawnParticle(Particle.DUST, coreLoc.clone().add(offset), 1, 0, 0, 0, 0, new Particle.DustOptions(c, size));
            }
        }

        // Add witch and portal aura to the core
        coreLoc.getWorld().spawnParticle(Particle.WITCH, coreLoc, 40, 1.0, 1.0, 1.0, 0.1);
        coreLoc.getWorld().spawnParticle(Particle.PORTAL, coreLoc, 50, 0.8, 0.8, 0.8, 0.05);

        // 2. Animated Branching Tendrils (กิ่งก้านสายฟ้าพุ่งกระจายเต็มท้องฟ้าเหมือนในภาพ)
        new BukkitRunnable() {
            int wave = 0;

            @Override
            public void run() {
                if (wave >= 5) {
                    this.cancel();
                    return;
                }

                // 8 main branches per wave = 40 huge branches total
                for (int b = 0; b < 8; b++) {
                    // Strong upward and outward bias for branch growth
                    double dx = (random.nextDouble() - 0.5) * 1.8;
                    double dy = random.nextDouble() * 1.4 + 0.3; // Strong upward growth
                    double dz = (random.nextDouble() - 0.5) * 1.8;
                    Vector direction = new Vector(dx, dy, dz).normalize();

                    Location current = coreLoc.clone().add(direction.clone().multiply(1.2)); // start from sphere edge

                    boolean isBlackBranch = random.nextDouble() < 0.5;
                    Color branchColor = isBlackBranch ? COLOR_BLACK : (random.nextBoolean() ? COLOR_VIBRANT_PURPLE : COLOR_DEEP_PURPLE);
                    float initialScale = isBlackBranch ? 2.8f : 2.4f;
                    Particle.DustOptions dust = new Particle.DustOptions(branchColor, initialScale);

                    double maxReach = 14.0 + (random.nextDouble() * 12.0); // 14 to 26 blocks reach

                    for (double step = 0; step < maxReach; step += 0.25) {
                        // Sharp zig-zag perturbations for realistic lightning/tendril look
                        Vector jitter = new Vector(
                                (random.nextDouble() - 0.5) * 0.4,
                                (random.nextDouble() - 0.5) * 0.4,
                                (random.nextDouble() - 0.5) * 0.4
                        );
                        direction.add(jitter).normalize();
                        current.add(direction.clone().multiply(0.25));

                        // Taper scale towards the ends
                        float currentScale = (float) Math.max(0.6, initialScale * (1.0 - (step / maxReach)));
                        Particle.DustOptions stepDust = new Particle.DustOptions(branchColor, currentScale);

                        current.getWorld().spawnParticle(Particle.DUST, current, 2, 0.04, 0.04, 0.04, 0, stepDust);

                        // Occasional glowing embers along the tendril
                        if (random.nextDouble() < 0.05) {
                            current.getWorld().spawnParticle(Particle.WITCH, current, 1, 0.02, 0.02, 0.02, 0.01);
                        }

                        // Fractal Sub-branching (แขนงย่อยแตกกิ่งก้าน)
                        if (random.nextDouble() < 0.07 && step < maxReach * 0.8) {
                            Vector subDir = direction.clone().add(new Vector(
                                    (random.nextDouble() - 0.5) * 0.9,
                                    (random.nextDouble() - 0.5) * 0.9,
                                    (random.nextDouble() - 0.5) * 0.9
                            )).normalize();

                            Location subCurrent = current.clone();
                            Color subColor = random.nextBoolean() ? COLOR_NEON_MAGENTA : branchColor;

                            double subLength = 4.0 + random.nextDouble() * 6.0;
                            for (double subStep = 0; subStep < subLength; subStep += 0.25) {
                                subDir.add(new Vector(
                                        (random.nextDouble() - 0.5) * 0.45,
                                        (random.nextDouble() - 0.5) * 0.45,
                                        (random.nextDouble() - 0.5) * 0.45
                                )).normalize();
                                subCurrent.add(subDir.clone().multiply(0.25));

                                float subScale = (float) Math.max(0.5, 1.4 * (1.0 - (subStep / subLength)));
                                subCurrent.getWorld().spawnParticle(
                                        Particle.DUST, subCurrent, 1, 0.02, 0.02, 0.02, 0,
                                        new Particle.DustOptions(subColor, subScale)
                                );
                            }
                        }
                    }
                }
                wave++;
            }
        }.runTaskTimer(plugin, 0L, 2L);

        // 3. Majestic Epic Sound Effects
        coreLoc.getWorld().playSound(coreLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.8f, 0.5f);
        coreLoc.getWorld().playSound(coreLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 0.6f);
        coreLoc.getWorld().playSound(coreLoc, Sound.BLOCK_PORTAL_TRAVEL, 1.2f, 1.4f);
        coreLoc.getWorld().playSound(coreLoc, Sound.ENTITY_WITHER_SPAWN, 0.8f, 0.8f);
    }

    /**
     * Interaction burst when player opens or clicks the Crafting Table.
     */
    public static void playInteractEffect(Location blockLoc, Player player) {
        if (blockLoc == null || blockLoc.getWorld() == null) return;

        Location topLoc = blockLoc.clone().add(0.5, 1.1, 0.5);

        // Small void flare
        for (int i = 0; i < 40; i++) {
            Vector v = new Vector(random.nextDouble() - 0.5, random.nextDouble() * 0.6 + 0.2, random.nextDouble() - 0.5).normalize().multiply(0.4);
            Color c = random.nextBoolean() ? COLOR_BLACK : COLOR_VIBRANT_PURPLE;
            topLoc.getWorld().spawnParticle(Particle.DUST, topLoc, 1, v.getX(), v.getY(), v.getZ(), 0.15, new Particle.DustOptions(c, 1.5f));
        }

        topLoc.getWorld().spawnParticle(Particle.WITCH, topLoc, 10, 0.3, 0.3, 0.3, 0.05);

        if (player != null) {
            player.playSound(blockLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 0.8f);
            player.playSound(blockLoc, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.7f, 1.2f);
        }
    }
}
