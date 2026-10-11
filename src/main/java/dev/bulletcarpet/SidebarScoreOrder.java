package dev.bulletcarpet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.text.TextFormatting;

public final class SidebarScoreOrder {
    public static String formatName(ScoreObjective objective, Team team, String name) {
        if (objective.getName().startsWith("st.") && "Total".equals(name)) {
            name = TextFormatting.BOLD + name + TextFormatting.RESET;
        }
        return ScorePlayerTeam.formatPlayerName(team, name);
    }

    public static Collection<Score> totalOnTop(ScoreObjective objective, Collection<Score> scores) {
        if (!objective.getName().startsWith("st.")) return scores;
        List<Score> ordered = new ArrayList<>(scores);
        ordered.sort(Comparator.comparing(score -> "Total".equals(score.getPlayerName())));
        return ordered;
    }
}
