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

    // Radiant Cosmic & Celestial Bright Color Palette (เอาความดำมืดออก สีสว่างไสว)
    private static final Color COLOR_BRIGHT_CYAN = Color.fromRGB(0, 240, 255);
    private static final Color COLOR_CELESTIAL_GOLD = Color.fromRGB(255, 215, 0);
    private static final Color COLOR_PURE_WHITE = Color.fromRGB(255, 255, 255);
    private static final Color COLOR_VIBRANT_MAGENTA = Color.fromRGB(255, 80, 240);
    private static final Color COLOR_RADIANT_PURPLE = Color.fromRGB(180, 100, 255);

    /**
     * Ambient particle effect for Crafting Table / Altar.
     * Absorbs bright celestial cosmic energy from a 10-block radius into the crafting table.
     */
    public static void playAmbientEffect(Location blockLoc) {
        if (blockLoc == null || blockLoc.getWorld() == null) return;

        // Core target center of crafting block
        Location coreLoc = blockLoc.clone().add(0.5, 1.0, 0.5);

        // 1. Inward Cosmic Energy Suction (ดูดซับพลังงานจากจักรวาลระยะ 10 บล็อก เข้าสู่โต๊ะคราฟท์)
        for (int i = 0; i < 30; i++) {
            // Spawn point on outer sphere (radius 2.0 to 10.0 blocks around table)
            double r = 2.0 + random.nextDouble() * 8.0; 
            double theta = random.nextDouble() * 2.0 * Math.PI;
            double phi = Math.acos(2.0 * random.nextDouble() - 1.0);

            double x = r * Math.sin(phi) * Math.cos(theta);
            double y = Math.max(0.2, r * Math.sin(phi) * Math.sin(theta)); // Keep above ground level
            double z = r * Math.cos(phi);

            Location spawnLoc = coreLoc.clone().add(x, y, z);
            Vector inwardDir = coreLoc.toVector().subtract(spawnLoc.toVector()).normalize();

            // Pick bright celestial colors
            Color color;
            double colorRoll = random.nextDouble();
            if (colorRoll < 0.25) color = COLOR_BRIGHT_CYAN;
            else if (colorRoll < 0.50) color = COLOR_CELESTIAL_GOLD;
            else if (colorRoll < 0.75) color = COLOR_VIBRANT_MAGENTA;
            else color = COLOR_PURE_WHITE;

            float scale = 0.9f + random.nextFloat() * 0.8f;

            // Spawn multi-step ray streaming inward towards center
            double rayLength = Math.min(r, 2.5);
            for (double step = 0; step < rayLength; step += 0.5) {
                Location rayLoc = spawnLoc.clone().add(inwardDir.clone().multiply(step));
                coreLoc.getWorld().spawnParticle(
                        Particle.DUST,
                        rayLoc,
                        1, 0, 0, 0, 0,
                        new Particle.DustOptions(color, scale)
                );
            }

            // Glowing end rod / glitter particles drifting inward from distant points
            if (random.nextDouble() < 0.3) {
                coreLoc.getWorld().spawnParticle(
                        Particle.END_ROD,
                        spawnLoc,
                        1,
                        inwardDir.getX() * 0.15, inwardDir.getY() * 0.15, inwardDir.getZ() * 0.15,
                        0.02
                );
            }
        }

        // 2. Enchantment glyphs & starlight drifting into table core
        coreLoc.getWorld().spawnParticle(Particle.ENCHANT, coreLoc, 10, 2.5, 1.5, 2.5, 0.5);

        // 3. Bright glowing core pulse on table surface
        coreLoc.getWorld().spawnParticle(
                Particle.DUST,
                coreLoc.clone().add(0, 0.1, 0),
                6, 0.2, 0.05, 0.2, 0,
                new Particle.DustOptions(COLOR_CELESTIAL_GOLD, 1.5f)
        );
        coreLoc.getWorld().spawnParticle(
                Particle.DUST,
                coreLoc.clone().add(0, 0.2, 0),
                4, 0.1, 0.05, 0.1, 0,
                new Particle.DustOptions(COLOR_BRIGHT_CYAN, 1.3f)
        );
    }

    /**
     * Massive epic OUTWARD EXPLOSION effect when Crafting Table is USED / CRAFTED.
     * Bright celestial energy detonates outward up to 7 blocks radius in a grand explosion!
     */
    public static void playActivationEffect(JavaPlugin plugin, Location blockLoc) {
        if (blockLoc == null || blockLoc.getWorld() == null) return;

        Location coreLoc = blockLoc.clone().add(0.5, 1.2, 0.5);

        // 1. Initial Grand Flash & Fireworks
        coreLoc.getWorld().spawnParticle(Particle.FLASH, coreLoc, 2, 0.1, 0.1, 0.1, 0);
        coreLoc.getWorld().spawnParticle(Particle.FIREWORK, coreLoc, 80, 1.0, 1.0, 1.0, 0.35);
        coreLoc.getWorld().spawnParticle(Particle.END_ROD, coreLoc, 100, 1.5, 1.5, 1.5, 0.3);

        // 2. Outward Expanding Radiant Shockwave (ขยายวงกว้างระเบิดออกไกล 7 บล็อก)
        for (int degree = 0; degree < 360; degree += 8) {
            double rad = Math.toRadians(degree);
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);

            for (double dist = 0.5; dist <= 7.0; dist += 0.5) { // 7 blocks radius
                Location ringLoc = coreLoc.clone().add(cos * dist, 0.3, sin * dist);

                Color c = (degree % 24 == 0) ? COLOR_CELESTIAL_GOLD :
                        (degree % 16 == 0 ? COLOR_BRIGHT_CYAN :
                        (degree % 8 == 0 ? COLOR_PURE_WHITE : COLOR_VIBRANT_MAGENTA));

                ringLoc.getWorld().spawnParticle(
                        Particle.DUST,
                        ringLoc,
                        2, 0.08, 0.08, 0.08, 0,
                        new Particle.DustOptions(c, 2.0f)
                );
            }
        }

        // 3. Animated Outward Cosmic Rays (กิ่งก้านสายฟ้าสว่างไสวพุ่งออกถึง 7 บล็อก)
        new BukkitRunnable() {
            int wave = 0;

            @Override
            public void run() {
                if (wave >= 4) {
                    this.cancel();
                    return;
                }

                for (int b = 0; b < 12; b++) {
                    double dx = (random.nextDouble() - 0.5) * 2.0;
                    double dy = random.nextDouble() * 1.5 + 0.2;
                    double dz = (random.nextDouble() - 0.5) * 2.0;
                    Vector direction = new Vector(dx, dy, dz).normalize();

                    Location current = coreLoc.clone();
                    Color rayColor = random.nextBoolean() ? COLOR_BRIGHT_CYAN :
                            (random.nextBoolean() ? COLOR_CELESTIAL_GOLD : COLOR_PURE_WHITE);

                    double maxReach = 7.0 + random.nextDouble() * 2.0; // ~7-9 blocks

                    for (double step = 0; step < maxReach; step += 0.35) {
                        Vector jitter = new Vector(
                                (random.nextDouble() - 0.5) * 0.3,
                                (random.nextDouble() - 0.5) * 0.3,
                                (random.nextDouble() - 0.5) * 0.3
                        );
                        direction.add(jitter).normalize();
                        current.add(direction.clone().multiply(0.35));

                        float currentScale = (float) Math.max(0.6, 2.6f * (1.0 - (step / maxReach)));
                        current.getWorld().spawnParticle(
                                Particle.DUST, current, 2, 0.04, 0.04, 0.04, 0,
                                new Particle.DustOptions(rayColor, currentScale)
                        );
                        if (random.nextDouble() < 0.15) {
                            current.getWorld().spawnParticle(Particle.END_ROD, current, 1, 0.02, 0.02, 0.02, 0.01);
                        }
                    }
                }
                wave++;
            }
        }.runTaskTimer(plugin, 0L, 2L);

        // 4. Grand Celestial Sound Effects (เสียงระเบิดก้องกังวานสว่างไสว)
        coreLoc.getWorld().playSound(coreLoc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.8f);
        coreLoc.getWorld().playSound(coreLoc, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 2.0f, 1.0f);
        coreLoc.getWorld().playSound(coreLoc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.8f, 1.2f);
        coreLoc.getWorld().playSound(coreLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 1.0f);
    }

    /**
     * Interaction effect when player opens/clicks Crafting Table.
     */
    public static void playInteractEffect(Location blockLoc, Player player) {
        if (blockLoc == null || blockLoc.getWorld() == null) return;

        Location topLoc = blockLoc.clone().add(0.5, 1.1, 0.5);

        for (int i = 0; i < 35; i++) {
            Vector v = new Vector(random.nextDouble() - 0.5, random.nextDouble() * 0.5 + 0.2, random.nextDouble() - 0.5).normalize().multiply(0.35);
            Color c = random.nextBoolean() ? COLOR_BRIGHT_CYAN : COLOR_CELESTIAL_GOLD;
            topLoc.getWorld().spawnParticle(Particle.DUST, topLoc, 1, v.getX(), v.getY(), v.getZ(), 0.1, new Particle.DustOptions(c, 1.5f));
        }

        topLoc.getWorld().spawnParticle(Particle.END_ROD, topLoc, 8, 0.2, 0.2, 0.2, 0.05);

        if (player != null) {
            player.playSound(blockLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
            player.playSound(blockLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.5f, 1.4f);
        }
    }
}
