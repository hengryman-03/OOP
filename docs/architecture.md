# Architecture and design decisions

The supplied template's `student`, `matching`, `connection`, `group`, and `admin` packages remain the domain boundaries. Controllers translate HTTP input; services enforce business rules; models describe domain data; the persistence package owns SQL and serialization. Frontend feature folders mirror these capabilities.

```mermaid
flowchart LR
    UI[Next.js feature screens] --> Client[Typed API client]
    UI --> FirebaseAuth[Firebase email/password authentication]
    FirebaseAuth --> Client
    Client --> Proxy[Same-origin API rewrite]
    Proxy --> Session[Session and origin checks]
    Session --> Controllers[Domain controllers]
    Controllers --> Services[Domain services]
    Services --> Repository[StudyRepository]
    Repository --> H2[(File-backed H2)]
    Config[MatchingProperties defaults] --> Matching[MatchingService]
    Matching --> Repository
```

```mermaid
classDiagram
    StudentProfile *-- StudyPreference
    StudyPreference *-- AvailabilitySlot
    StudentProfile --> Course : current courses
    StudyPreference --> StudyMode
    StudyPreference --> StudyArrangement
    StudyPreference --> StudyGoal
    BuddyRequest --> StudentProfile : sender / recipient IDs
    BuddyRequest --> RequestStatus
    StudyGroup *-- AvailabilitySlot
    StudyGroup --> StudentProfile : leader / member IDs
    StudyGroup --> GroupStatus
    MembershipRequest --> StudyGroup
    MembershipRequest --> StudentProfile
    MatchingService --> MatchingProperties
    MatchingService --> AvailabilityCalculator
    MatchingService --> MatchResult
    MatchResult *-- MatchBreakdown
    MatchResult --> StudentProfile : public projection
    SessionService --> UserAccount
    SessionService --> Actor
    AdminService --> UserAccount
    StudentService --> StudyRepository
    ConnectionService --> StudyRepository
    StudyGroupService --> StudyRepository
    AdminService --> StudyRepository
```

## Key responsibilities

- `Actor` represents trusted session identity. It is not accepted from JSON. Group leadership is checked dynamically against each group, so a transfer changes permissions immediately.
- `FirebaseConfig` verifies student ID tokens before `SessionController` creates a server session. Administrator credentials use a separate login path. Students create their own application account after Firebase registration; a missing application account can be completed after a later sign-in. `SessionService` resolves active accounts on every API request, so suspension/deletion invalidates existing session access without waiting for cookie expiry. Login rotates the session ID. Deletion removes application records; suspension blocks access while retaining the account.
- `StudentService.publicProfile` is the only viewer-aware contact projection. General directories and ranked matches always hide the number. Administrators do not automatically receive contact details.
- `ConnectionService` treats an accepted buddy request as the active connection record. A separate redundant `Connection` entity is unnecessary for the specified lifecycle. Declined/ended requests remain history; new requests may then be sent.
- `AvailabilityCalculator` converts weekly slots to a 10,080-bit minute set. Union removes duplicate coverage; intersection counts shared minutes. This is deterministic and avoids nested-loop double counting.
- `MatchingService` filters candidates, evaluates five compatibility factors and constructs a public score breakdown. `MatchRanker` then applies one of four deterministic policies: weighted score, availability-first, course-first or non-dominated Pareto fronts. Configuration and candidate filtering stay separate from ranking.
- `StudyGroupService` owns membership capacity, decisions, closure, and transfer/exit transitions. Group update preserves membership/status/leader fields even if a client supplies forged values.
- `AdminService` handles account lifecycle across profiles, requests, and groups inside one transaction. Active leadership prevents deletion/suspension until transferred or closed.
- `StudyRepository` isolates JSON aggregate persistence. Its lock is acquired before all business writes. H2 transactions roll back the whole action on a business error. A concurrent capacity test verifies the shared lock protects the last available seat.
- `DemoDataInitializer` checks a persisted marker inside the same lock/transaction. It does not reseed based on current student count, which would incorrectly recreate deleted accounts.
- `ApiExceptionHandler` provides consistent errors without exposing SQL, stack traces, or internal exception details to the browser.
- Frontend feature components own local form state; the workspace shell loads current data, switches accounts, and clears prior-account screens. The typed client sends cookie-backed requests through Next.js and parses API errors.

## Deliberate limits

H2 JSON aggregates retain the starter's simple POJOs. They provide real restart persistence but no SQL-level foreign keys on JSON fields. Services enforce relationships. All writes serialize on a database row; this is appropriate for the 50-profile demonstration, with lower throughput than per-aggregate locking. There is no distributed cache, queue, or background notification service.

The frontend uses one App Router page with domain components and tab navigation, preserving the template's single dashboard model. Tabs are not bookmarkable routes. State refresh is explicit after mutations or through Refresh data; there is no live cross-browser push. The current session identity is shared between tabs of the same browser cookie context.

Spring Boot was updated to 3.5 for this branch's Java 25 target. Before any public deployment, configure Firebase credentials and administrator secrets outside source control, plan database migrations, review dependency security support, configure HTTPS, and perform a production security review. None of those deployment changes are claimed here.
