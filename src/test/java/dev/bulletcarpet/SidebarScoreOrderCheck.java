package dev.bulletcarpet;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.scoreboard.IScoreCriteria;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;

public final class SidebarScoreOrderCheck {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        for (String playerName : new String[] {"Alice", "Zulu"}) {
            for (int totalPoints : new int[] {0, 10, 20}) {
                Scoreboard board = new Scoreboard();
                ScoreObjective objective = board.addScoreObjective("st.jump", IScoreCriteria.DUMMY);
                Score player = board.getOrCreateScore(playerName, objective);
                player.setScorePoints(10);
                Score total = board.getOrCreateScore("Total", objective);
                total.setScorePoints(totalPoints);
                List<Score> original = new ArrayList<>(board.getSortedScores(objective));
                List<Score> ordered = new ArrayList<>(SidebarScoreOrder.totalOnTop(objective, original));
                check(ordered.get(ordered.size() - 1) == total, "Total moved below " + playerName);
                check(original.equals(new ArrayList<>(board.getSortedScores(objective))), "Original scoreboard order changed");
                check(total.getScorePoints() == totalPoints && player.getScorePoints() == 10, "Score values changed");
                check(total.getPlayerName().equals("Total"), "Total renamed");
            }
        }
        Scoreboard board = new Scoreboard();
        ScoreObjective objective = board.addScoreObjective("st.jump", IScoreCriteria.DUMMY);
        check(SidebarScoreOrder.formatName(objective, null, "Total").equals("\u00a7lTotal\u00a7r"), "Total name is not bold");
        check(SidebarScoreOrder.formatName(objective, null, "Alice").equals("Alice"), "Player name formatting changed");
        ScorePlayerTeam team = board.createTeam("labels");
        team.setPrefix("\u00a7a[");
        team.setSuffix("]");
        check(SidebarScoreOrder.formatName(objective, team, "Total").equals("\u00a7a[\u00a7lTotal\u00a7r]"), "Team decoration lost");
        for (int i = 0; i < 20; i++) board.getOrCreateScore("Player" + i, objective).setScorePoints(i);
        Score total = board.getOrCreateScore("Total", objective);
        List<Score> original = new ArrayList<>(board.getSortedScores(objective));
        List<Score> ordered = new ArrayList<>(SidebarScoreOrder.totalOnTop(objective, original));
        check(ordered.subList(ordered.size() - 15, ordered.size()).get(14) == total, "Total lost at sidebar cutoff");
        original.remove(total); ordered.remove(total);
        check(original.equals(ordered), "Player ordering changed");
        ScoreObjective ordinary = board.addScoreObjective("ordinary", IScoreCriteria.DUMMY);
        check(SidebarScoreOrder.formatName(ordinary, null, "Total").equals("Total"), "Ordinary Total formatting changed");
        board.getOrCreateScore("Total", ordinary).setScorePoints(0);
        board.getOrCreateScore("Alice", ordinary).setScorePoints(10);
        java.util.Collection<Score> ordinaryScores = board.getSortedScores(ordinary);
        check(SidebarScoreOrder.totalOnTop(ordinary, ordinaryScores) == ordinaryScores, "Ordinary scoreboard changed");
        System.out.println("Sidebar Total priority, bold name, cutoff and unchanged score checks passed");
    }
}
