package dev.bulletcarpet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;

public final class SidebarScoreOrder {
    public static Collection<Score> totalOnTop(ScoreObjective objective, Collection<Score> scores) {
        if (!objective.getName().startsWith("st.")) return scores;
        List<Score> ordered = new ArrayList<>(scores);
        ordered.sort(Comparator.comparing(score -> "Total".equals(score.getPlayerName())));
        return ordered;
    }
}
