package com.yourname.noesissmp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.Locale;
import java.util.logging.Level;

public class HUDManager extends BukkitRunnable {

    private final NoesisSMP plugin;
    private long lastErrorLogTime;

    public HUDManager(NoesisSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                boolean hudEnabled = plugin.getData().getBoolean("players." + player.getUniqueId() + ".hud", true);

                if (!hudEnabled) {
                    if (player.getScoreboard() != Bukkit.getScoreboardManager().getMainScoreboard()) {
                        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                    }
                    continue;
                }

                org.bukkit.attribute.AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                if (maxHealth == null) continue;
                int maxHearts = (int) (maxHealth.getBaseValue() / 2.0);
                int kills = plugin.getData().getInt("players." + player.getUniqueId() + ".kills", 0);
                int overflow = plugin.getData().getInt("players." + player.getUniqueId() + ".overflow", 0);

                int totalPoints = kills + overflow;
                double critChance = totalPoints * 0.8;
                if (critChance > 50.0) critChance = 50.0;

                String tierText = ChatColor.YELLOW + "(Yellow)";
                if (totalPoints >= 100) tierText = ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "(VOID)";
                else if (totalPoints >= 60) tierText = ChatColor.RED + "(Red)";
                else if (totalPoints >= 30) tierText = ChatColor.GOLD + "(Orange)";

                Scoreboard board = player.getScoreboard();
                if (board == Bukkit.getScoreboardManager().getMainScoreboard()) {
                    board = Bukkit.getScoreboardManager().getNewScoreboard();
                }

                Objective obj = board.getObjective("noesishud");
                if (obj != null) obj.unregister();

                try {
                    obj = board.registerNewObjective("noesishud", Criteria.DUMMY, ChatColor.DARK_GRAY + "[" + ChatColor.AQUA + ChatColor.BOLD + "Noesis SMP" + ChatColor.DARK_GRAY + "]");
                } catch (Throwable e) {
                    obj = board.registerNewObjective("noesishud", "dummy", ChatColor.DARK_GRAY + "[" + ChatColor.AQUA + ChatColor.BOLD + "Noesis SMP" + ChatColor.DARK_GRAY + "]");
                }
                obj.setDisplaySlot(DisplaySlot.SIDEBAR);

                obj.getScore(ChatColor.GRAY + "-----------------").setScore(6);
                obj.getScore(ChatColor.RED + "❤ Hearts: " + ChatColor.WHITE + maxHearts + ChatColor.GRAY + "/20").setScore(5);
                obj.getScore(ChatColor.DARK_RED + "⚔ Kill Stack: " + ChatColor.WHITE + kills).setScore(4);
                obj.getScore(ChatColor.GOLD + "⭐ Overflow: " + ChatColor.WHITE + overflow).setScore(3);
                obj.getScore(ChatColor.AQUA + "🎯 Crit: " + ChatColor.WHITE + String.format(Locale.US, "%.1f", critChance) + "% " + tierText).setScore(2);
                obj.getScore(ChatColor.GRAY + "----------------- ").setScore(1);

                if (player.getScoreboard() != board) player.setScoreboard(board);

            } catch (Exception exception) {
                long now = System.currentTimeMillis();
                if (now - lastErrorLogTime >= 60_000L) {
                    lastErrorLogTime = now;
                    plugin.getLogger().log(Level.WARNING, "Could not update the Noesis HUD", exception);
                }
            }
        }
    }
}
