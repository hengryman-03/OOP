package studybuddy.backend.matching.service;

import studybuddy.backend.matching.config.MatchingProperties;
import studybuddy.backend.matching.model.MatchResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/** Orders scored candidates without changing the five explainable score contributions. */
final class MatchRanker {
    private MatchRanker() {}

    record Candidate(MatchResult result, double[] factors, int sharedMinutes) {}

    static List<MatchResult> rank(List<Candidate> candidates, MatchingProperties config) {
        int[] fronts =
                "PARETO".equals(config.getStrategy()) ? paretoFronts(candidates, enabled(config)) : null;
        Comparator<Integer> scoreOrder =
                Comparator.<Integer>comparingInt(i -> candidates.get(i).result().getScore())
                        .reversed()
                        .thenComparing(i -> candidates.get(i).result().getStudent().getId());
        Comparator<Integer> order =
                switch (config.getStrategy()) {
                    case "BALANCED" -> scoreOrder;
                    case "AVAILABILITY_FIRST" ->
                            config.getAvailabilityWeight() == 0
                                    ? scoreOrder
                                    : Comparator.<Integer>comparingInt(
                                                    i -> candidates.get(i).sharedMinutes())
                                            .reversed()
                                            .thenComparing(scoreOrder);
                    case "COURSE_FIRST" ->
                            config.getCourseWeight() == 0
                                    ? scoreOrder
                                    : Comparator.<Integer>comparingDouble(
                                                    i -> candidates.get(i).factors()[0])
                                            .reversed()
                                            .thenComparing(scoreOrder);
                    case "PARETO" ->
                            Comparator.<Integer>comparingInt(i -> fronts[i]).thenComparing(scoreOrder);
                    default -> throw new IllegalArgumentException("Unknown matching strategy.");
                };
        return IntStream.range(0, candidates.size())
                .boxed()
                .sorted(order)
                .limit(config.getMaxResults())
                .map(
                        i -> {
                            Candidate candidate = candidates.get(i);
                            candidate.result().setParetoFront(fronts == null ? null : fronts[i]);
                            candidate.result().setRankingReason(reason(config, candidate, fronts, i));
                            return candidate.result();
                        })
                .toList();
    }

    private static String reason(
            MatchingProperties config, Candidate candidate, int[] fronts, int index) {
        return switch (config.getStrategy()) {
            case "BALANCED" -> "Higher weighted compatibility score ranks first.";
            case "AVAILABILITY_FIRST" ->
                    config.getAvailabilityWeight() == 0
                            ? "Availability is disabled; higher compatibility score ranks first."
                            : candidate.sharedMinutes()
                                    + " shared weekly minutes rank first; compatibility score breaks ties.";
            case "COURSE_FIRST" ->
                    config.getCourseWeight() == 0
                            ? "Course is disabled; higher compatibility score ranks first."
                            : (candidate.factors()[0] == 1
                                            ? "Preferred course matches"
                                            : candidate.factors()[0] == 0.5
                                                    ? "Enrolled in the search course"
                                                    : "No search-course match")
                                    + "; compatibility score breaks ties.";
            case "PARETO" ->
                    "Pareto front "
                            + fronts[index]
                            + " (non-dominated layer); compatibility score breaks ties within the front.";
            default -> throw new IllegalArgumentException("Unknown matching strategy.");
        };
    }

    private static boolean[] enabled(MatchingProperties config) {
        return new boolean[] {
            config.getCourseWeight() > 0,
            config.getAvailabilityWeight() > 0,
            config.getStudyModeWeight() > 0,
            config.getStudyGoalWeight() > 0,
            config.getGroupSizeWeight() > 0
        };
    }

    /** Non-dominated sorting: a candidate dominates another only if it is no worse on all
     * enabled factors and strictly better on at least one. This takes O(n² × 5) comparisons. */
    private static int[] paretoFronts(List<Candidate> candidates, boolean[] enabled) {
        int count = candidates.size();
        int[] dominatedBy = new int[count], fronts = new int[count];
        List<List<Integer>> dominates = new ArrayList<>();
        for (int i = 0; i < count; i++) dominates.add(new ArrayList<>());
        for (int i = 0; i < count; i++) {
            for (int j = i + 1; j < count; j++) {
                if (dominates(candidates.get(i), candidates.get(j), enabled)) {
                    dominates.get(i).add(j);
                    dominatedBy[j]++;
                } else if (dominates(candidates.get(j), candidates.get(i), enabled)) {
                    dominates.get(j).add(i);
                    dominatedBy[i]++;
                }
            }
        }
        List<Integer> current = new ArrayList<>();
        for (int i = 0; i < count; i++) if (dominatedBy[i] == 0) current.add(i);
        for (int front = 1; !current.isEmpty(); front++) {
            List<Integer> next = new ArrayList<>();
            for (int i : current) {
                fronts[i] = front;
                for (int j : dominates.get(i)) if (--dominatedBy[j] == 0) next.add(j);
            }
            current = next;
        }
        return fronts;
    }

    private static boolean dominates(Candidate a, Candidate b, boolean[] enabled) {
        boolean better = false;
        for (int i = 0; i < enabled.length; i++) {
            if (!enabled[i]) continue;
            if (a.factors()[i] < b.factors()[i]) return false;
            if (a.factors()[i] > b.factors()[i]) better = true;
        }
        return better;
    }
}
