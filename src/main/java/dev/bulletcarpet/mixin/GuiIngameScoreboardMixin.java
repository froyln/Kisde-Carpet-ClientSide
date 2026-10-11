package dev.bulletcarpet.mixin;

import dev.bulletcarpet.SidebarScoreOrder;
import java.util.Collection;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.scoreboard.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GuiIngame.class)
public abstract class GuiIngameScoreboardMixin {
    @Redirect(method = "renderScoreboard", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/scoreboard/ScorePlayerTeam;formatPlayerName(Lnet/minecraft/scoreboard/Team;Ljava/lang/String;)Ljava/lang/String;"), require = 2)
    private String kisde$boldTotal(Team team, String name, ScoreObjective objective, ScaledResolution resolution) {
        return SidebarScoreOrder.formatName(objective, team, name);
    }

    @Redirect(method = "renderScoreboard", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/scoreboard/Scoreboard;getSortedScores(Lnet/minecraft/scoreboard/ScoreObjective;)Ljava/util/Collection;"))
    private Collection<Score> kisde$totalOnTop(Scoreboard scoreboard, ScoreObjective objective) {
        return SidebarScoreOrder.totalOnTop(objective, scoreboard.getSortedScores(objective));
    }
}
