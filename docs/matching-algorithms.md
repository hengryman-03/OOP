# Study buddy matching algorithms

The endpoint first applies explicit search filters, then evaluates each remaining peer against the searching student. It does not compare students who failed a filter. Five factors are between 0 and 1: preferred/current course, shared weekly availability, meeting mode, study goal and arrangement/group size. The formula and boundaries are in [README.md](../README.md#matching-formula).

The compatibility score is the sum of five rounded contributions. Administrator weights are normalised to 100 points. This score answers **how compatible?** The selected strategy answers **which trade-off should rank first?** Student ID settles any remaining tie, so repeated searches over the same data return the same order.

| Strategy | Algorithm | Useful when | Main trade-off |
| --- | --- | --- | --- |
| Balanced | Weighted additive score | Several moderate strengths should compensate for a weakness | A severe weakness can be hidden by strengths elsewhere |
| Availability first | Lexicographic sort by shared unique minutes, then score | Finding a time together is the hard constraint | One extra minute can outrank a much better overall fit |
| Course first | Lexicographic sort by preferred-course match, enrolled-course match, then score | Course alignment matters most | A preferred-course peer can outrank someone with more shared time |
| Pareto | Non-dominated sorting across enabled factors, then score within each front | Presenting varied strong trade-offs without collapsing them immediately into one number | Many candidates can share a front; pairwise comparison costs O(n² × 5) |

For Pareto, peer A **dominates** B when A is at least as good on every enabled factor and better on at least one. All peers dominated by nobody form front 1. Remove that front and repeat to form front 2 and later fronts. The displayed score orders peers within each front. A weight of zero removes a factor from this comparison; it also turns off the strict priority for the corresponding availability-first or course-first strategy.

Example: one peer has a 90-point compatibility score but 20 shared minutes, while another scores 70 with 120 shared minutes. Balanced puts the 90-point peer first; availability-first puts the 120-minute peer first. Neither order is universally correct. The administrator can select the policy for the cohort and the result explains its own position.

This is an interpretable multi-criteria recommender, not a learned model. It uses declared preferences only, avoids inferred personal traits and makes no claim that a high score guarantees a successful partnership. Matching is directional because availability is divided by the searching student's available minutes; the same pair can receive a different score when the other student searches. The current strategies rank candidates for one requester, not a globally optimal assignment of all students to groups.

The focused ranker tests cover strategy-specific order, Pareto fronts, disabled criteria, score ties and the result limit. The HTTP integration test checks that administrator strategy updates are validated and the ranking explanation reaches the student endpoint.
