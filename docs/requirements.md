# Requirement coverage

This map interprets the supplied PDF's functional requirements and the Word checklist as application scope. Submission and peer-evaluation instructions are not application features. The Word document's leader-exit rule is included as the team-added requirement.

A live Firebase browser walkthrough was completed on 17 October 2026 using two disposable student identities and an isolated H2 database. It covered sign-in, profile setup, matching, buddy and group workflows and the administrator strategy change. See `verification.md` for exact limits. Earlier browser observations there predate Firebase Authentication.

| Requirement | Implementation | Verification |
| --- | --- | --- |
| Create/update basic profile and courses | `StudentController`, `StudentService`, `ProfileEditor` | API validation/ownership test; browser onboarding |
| Course, mode, arrangement, goal, size, weekly availability | Validated `StudyPreference`, shared availability editor | Nested validation and saved-profile tests |
| Search by course or study goal | Match explorer filters and `MatchingService` | Combined filter test |
| Ranked compatible students | Normalized scoring with deterministic balanced, priority and Pareto ranking | Ranking tests, API strategy test and live Firebase browser match explanations |
| Filter course, goal, mode, availability | Filters plus arrangement and overlap-only | Positive/empty/invalid filter tests |
| Public profile with private contact | Server-side public projection | Privacy test across multiple sessions |
| Request with optional message | `ConnectionService.sendRequest`, selected profile form | Duplicate/self/forgery tests; browser send |
| Accept/decline; reveal contact after acceptance | Recipient-only transition checks | API lifecycle and browser acceptance/contact check |
| Active buddies and end connection | Accepted request records and Connections tabs | Both-direction privacy revocation test |
| Course-specific group creation and details | `GroupEditor`, `StudyGroupService` | Creation validation/ownership test; browser create |
| Edit or close group | Manager-only service operations | Closed-state and preservation tests |
| View membership requests | Own/managed request projection and group detail UI | Browser leader review |
| Accept/reject membership | Transactional decision with capacity recheck | Ownership/capacity/concurrency tests; browser approval |
| Remove members | Manager or self-removal operation | Membership lifecycle test |
| Leader transfers or closes before quitting | Transfer-and-stay, transfer-and-leave, close-and-leave | Blocked exit/self-transfer/outsider tests; browser transfer |
| Lone leader exits | Automatic closure and leader membership removal | Leader exit test |
| Admin override | Admin group controls | Override/leadership protection test |
| Create/update/delete user accounts | Students self-register or admins create Firebase-backed students by email; admins update existing accounts and delete their Firebase identity and application data. Administrators cannot create another administrator account. | Mocked Firebase API lifecycle tests; live invitation email and deletion remain to be checked with a team-controlled inbox |
| Account status/basic usage | Status, profile completeness, connections/groups/pending counts, last sign-in | Admin account response tests; live browser table showed sign-in timestamps |
| Configure matching criteria and importance | Nonnegative weights, zero disables, nonzero total | Validation and persistence tests |
| Configure strategy | Balanced, availability-first, course-first and Pareto fronts | Ranking, API strategy and saved-config tests |
| At least 10 courses and 50 profiles | Atomic one-time fictional seeds | Seed-count test; optional real-HTTP smoke requires Firebase Admin credentials |
| Modular OOP and layered design | Existing package/model structure retained; focused services/helpers | Architecture and class diagrams |
| Externalized parameters | Properties, environment variables, saved matching config | Configuration documentation and restart smoke |
| README/setup/assumptions/authentication | Project README and demo guide | Build and test commands exercised; live Firebase login and recovery checked on 17 October 2026 |

Scope choices preserve the template: one primary study goal and one matching preference per profile/group; multiple weekly time slots; group size includes the leader. Students now authenticate with Firebase, while the system administrator uses a separate configured password. Seeded fictional student records have no Firebase identities and cannot sign in. Historical browser evidence in `verification.md` predates this change. The application does not invent personal contribution records or claim the team has rehearsed a presentation.
