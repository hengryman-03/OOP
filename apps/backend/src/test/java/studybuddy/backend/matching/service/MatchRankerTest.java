package studybuddy.backend.matching.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import studybuddy.backend.matching.config.MatchingProperties;
import studybuddy.backend.matching.model.MatchBreakdown;
import studybuddy.backend.matching.model.MatchResult;
import studybuddy.backend.student.model.StudentProfile;

import java.util.List;

class MatchRankerTest {
    private MatchRanker.Candidate candidate(
            String id, int score, double course, double availability, int sharedMinutes) {
        StudentProfile profile = new StudentProfile();
        profile.setId(id);
        return new MatchRanker.Candidate(
                new MatchResult(profile, new MatchBreakdown(score, 0, 0, 0, 0)),
                new double[] {course, availability, 1, 1, 1},
                sharedMinutes);
    }

    private List<String> ids(List<MatchResult> results) {
        return results.stream().map(result -> result.getStudent().getId()).toList();
    }

    @Test
    void algorithmsRankDifferentTradeoffsAndExplainTheOrder() {
        List<MatchRanker.Candidate> peers =
                List.of(
                        candidate("A", 90, 0.5, 0.5, 60),
                        candidate("B", 70, 1, 0.2, 20),
                        candidate("C", 60, 0, 1, 120));
        MatchingProperties config = new MatchingProperties();
        assertEquals(List.of("A", "B", "C"), ids(MatchRanker.rank(peers, config)));
        config.setStrategy("AVAILABILITY_FIRST");
        assertEquals(List.of("C", "A", "B"), ids(MatchRanker.rank(peers, config)));
        assertTrue(MatchRanker.rank(peers, config).get(0).getRankingReason().contains("120"));
        config.setStrategy("COURSE_FIRST");
        assertEquals(List.of("B", "A", "C"), ids(MatchRanker.rank(peers, config)));
    }

    @Test
    void paretoFrontBeatsHigherScoreWhenThatCandidateIsDominated() {
        List<MatchRanker.Candidate> peers =
                List.of(
                        candidate("A", 70, 1, 0.5, 60),
                        candidate("B", 90, 1, 0.4, 50),
                        candidate("C", 80, 0.5, 1, 120));
        MatchingProperties config = new MatchingProperties();
        config.setStrategy("PARETO");
        List<MatchResult> ranked = MatchRanker.rank(peers, config);
        assertEquals(List.of("C", "A", "B"), ids(ranked));
        assertTrue(ranked.get(0).getRankingReason().contains("front 1"));
        assertTrue(ranked.get(2).getRankingReason().contains("front 2"));
        assertEquals(1, ranked.get(0).getParetoFront());
        assertEquals(2, ranked.get(2).getParetoFront());
        config.setAvailabilityWeight(0);
        assertEquals(List.of("B", "A", "C"), ids(MatchRanker.rank(peers, config)));
    }

    @Test
    void disabledPriorityFallsBackToScoreAndTiesUseStudentId() {
        List<MatchRanker.Candidate> peers =
                List.of(
                        candidate("Z", 80, 1, 1, 120),
                        candidate("A", 80, 0, 0, 0),
                        candidate("B", 90, 0, 0, 0));
        MatchingProperties config = new MatchingProperties();
        config.setStrategy("COURSE_FIRST");
        config.setCourseWeight(0);
        assertEquals(List.of("B", "A", "Z"), ids(MatchRanker.rank(peers, config)));
        config.setStrategy("AVAILABILITY_FIRST");
        config.setAvailabilityWeight(0);
        assertEquals(List.of("B", "A", "Z"), ids(MatchRanker.rank(peers, config)));
        config.setMaxResults(2);
        assertEquals(2, MatchRanker.rank(peers, config).size());
    }
}
