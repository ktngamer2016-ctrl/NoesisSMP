package com.yourname.noesissmp;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;

public class CraftingEffectManager {

    private static final Random random = new Random();

    // Clean, Celestial Color Palette (Soft Cyan, Warm Gold, Pure White)
    private static final Color COLOR_CYAN = Color.fromRGB(0, 220, 255);
    private static final Color COLOR_GOLD = Color.fromRGB(255, 215, 100);
    private static final Color COLOR_WHITE = Color.fromRGB(255, 255, 255);

    /**
     * Clean & Minimal Ambient Particle Effect for Altar / Crafting Table.
     * Gentle starlight and soft particles drifting gracefully within 1.5 blocks
     * around the table.
     */
    public static void playAmbientEffect(Location blockLoc) {
        if (blockLoc == null || blockLoc.getWorld() == null)
            return;

        Location coreLoc = blockLoc.clone().add(0.5, 1.1, 0.5);

        // 1. Subtle, gentle floating starlight particles around top of table (radius
        // ~1.2m)
        for (int i = 0; i < 3; i++) {
            double angle = random.nextDouble() * 2.0 * Math.PI;
            double dist = 0.3 + random.nextDouble() * 0.9;
            double offsetX = Math.cos(angle) * dist;
            double offsetZ = Math.sin(angle) * dist;
            double offsetY = random.nextDouble() * 0.6;

            Location pLoc = coreLoc.clone().add(offsetX, offsetY, offsetZ);

            Color color = random.nextBoolean() ? COLOR_CYAN : (random.nextBoolean() ? COLOR_GOLD : COLOR_WHITE);
            coreLoc.getWorld().spawnParticle(
                    Particle.DUST,
                    pLoc,
                    1, 0, 0.02, 0, 0,
                    new Particle.DustOptions(color, 0.9f));
        }

        // 2. A few subtle enchantment glyphs floating gracefully toward center
        if (random.nextDouble() < 0.4) {
            coreLoc.getWorld().spawnParticle(
                    Particle.ENCHANT,
                    coreLoc.clone().add((random.nextDouble() - 0.5) * 0.8, 0.2 + random.nextDouble() * 0.4,
                            (random.nextDouble() - 0.5) * 0.8),
                    2, 0.1, 0.1, 0.1, 0.2);
        }

        // 3. Subtle center soft glow dot
        coreLoc.getWorld().spawnParticle(
                Particle.DUST,
                coreLoc.clone().add(0, 0.05, 0),
                1, 0.02, 0.02, 0.02, 0,
                new Particle.DustOptions(COLOR_GOLD, 1.1f));
    }

    /**
     * Sleek, Elegant & Clean Activation Burst when crafting/using the Altar.
     * Creates a crisp, beautiful ring, vertical light beam, and satisfying chime
     * without screen clutter.
     */
    public static void playActivationEffect(JavaPlugin plugin, Location blockLoc) {
        if (blockLoc == null || blockLoc.getWorld() == null)
            return;

        Location coreLoc = blockLoc.clone().add(0.5, 1.0, 0.5);

        // 1. Clean expanding ring around table (radius 1.3 blocks max)
        for (int degree = 0; degree < 360; degree += 15) {
            double rad = Math.toRadians(degree);
            double x = Math.cos(rad) * 1.3;
            double z = Math.sin(rad) * 1.3;
            Location ringLoc = coreLoc.clone().add(x, 0.2, z);

            Color ringColor = (degree % 30 == 0) ? COLOR_GOLD : COLOR_CYAN;
            ringLoc.getWorld().spawnParticle(
                    Particle.DUST,
                    ringLoc,
                    1, 0.02, 0.02, 0.02, 0,
                    new Particle.DustOptions(ringColor, 1.2f));
        }

        // 2. Clean vertical beam of light rising up gracefully (2.5 blocks height)
        for (double y = 0.2; y <= 2.5; y += 0.3) {
            Location beamLoc = coreLoc.clone().add(0, y, 0);
            coreLoc.getWorld().spawnParticle(
                    Particle.DUST,
                    beamLoc,
                    1, 0.05, 0.02, 0.05, 0,
                    new Particle.DustOptions(COLOR_WHITE, 1.4f));
        }

        // 3. Soft sparkle burst at core
        coreLoc.getWorld().spawnParticle(Particle.END_ROD, coreLoc.clone().add(0, 0.5, 0), 12, 0.3, 0.5, 0.3, 0.08);

        // 4. Elegant & Satisfying Soundscape
        coreLoc.getWorld().playSound(coreLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.5f, 1.2f);
        coreLoc.getWorld().playSound(coreLoc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.4f);
        coreLoc.getWorld().playSound(coreLoc, Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 1.5f);
    }

    /**
     * Subtle & Clean Interaction Effect when opening/clicking the Altar.
     */
    public static void playInteractEffect(Location blockLoc, Player player) {
        if (blockLoc == null || blockLoc.getWorld() == null)
            return;

        Location tableLoc = blockLoc.clone().add(0.5, 0.0, 0.5);

        // Clean subtle sparkles
        topLoc.getWorld().spawnParticle(Particle.END_ROD, topLoc, 4, 0.15, 0.15, 0.15, 0.03);
        topLoc.getWorld().spawnParticle(
                Particle.DUST,
                topLoc,
                4, 0.1, 0.1, 0.1, 0,
                new Particle.DustOptions(COLOR_CYAN, 1.1f));

        if (player != null) {
            player.playSound(blockLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.5f);
            player.playSound(blockLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.3f);
        }
    }
}
