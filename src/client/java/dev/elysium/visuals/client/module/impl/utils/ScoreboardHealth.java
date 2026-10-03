package dev.elysium.visuals.client.module.impl.utils;

import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;

/**
 * Many servers hide other players' real health from the client (it arrives as
 * a placeholder), but show it in the scoreboard under the name tag. This reads
 * it from there for our HUD (target, etc.).
 */
public class ScoreboardHealth extends Module {
	public ScoreboardHealth() {
		super("scoreboard_health", "ScoreboardHealth", "Реальное здоровье игроков из скорборда под ником", Category.UTILS);
	}

	/** Health to show for {@code e}: the below-name score for players when available, else the entity's own. */
	public static float health(LivingEntity e) {
		Float score = score(e);
		return score != null ? score : e.getHealth();
	}

	/** Max health to scale bars with (at least the score, in case the server counts differently). */
	public static float maxHealth(LivingEntity e) {
		Float score = score(e);
		return score != null ? Math.max(e.getMaxHealth(), score) : e.getMaxHealth();
	}

	private static Float score(LivingEntity e) {
		ScoreboardHealth m = ModuleManager.get().find(ScoreboardHealth.class);
		Minecraft mc = Minecraft.getInstance();
		if (m == null || !m.isEnabled() || !(e instanceof Player player) || mc.level == null) {
			return null;
		}
		Scoreboard scoreboard = mc.level.getScoreboard();
		Objective objective = scoreboard.getDisplayObjective(DisplaySlot.BELOW_NAME);
		if (objective == null) {
			return null;
		}
		ReadOnlyScoreInfo info = scoreboard.getPlayerScoreInfo(player, objective);
		return info == null || info.value() <= 0 ? null : (float) info.value();
	}
}
