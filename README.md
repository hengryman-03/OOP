# Study Buddy Matcher System

A coursework application built on the supplied Nx monorepo template: a Java 25 / Spring Boot 3.5 backend, a Next.js/React frontend, Firebase Authentication for students, and file-backed H2 persistence. Students can manage study preferences, find compatible buddies, exchange requests, and manage study groups. Administrators manage accounts and the matching engine.

## Run locally

Requirements: Node.js 22 LTS with npm and JDK 25. The Maven wrapper downloads Maven automatically. An internet connection is required for the first dependency install.

Student login needs a Firebase project with Email/Password sign-in enabled. Copy `apps/frontend/.env.example` to `apps/frontend/.env` and fill in its `NEXT_PUBLIC_FIREBASE_*` web-app values. Keep the Firebase Admin service-account JSON **outside the project directory** (including `apps/backend/src/main/resources`, which Maven packages into the app) and set `FIREBASE_SERVICE_ACCOUNT` to its plain absolute path before starting the backend. The public web config is not a service-account credential. The backend intentionally refuses to start when its private credential is missing; do not commit that JSON file. `FIREBASE_DATABASE_URL` has a project default and is used by the Firebase Admin initialisation, while application records remain in H2.

From this project directory:

```bash
npm ci
npm run dev
```

Open **http://localhost:4200**. The backend runs at **http://127.0.0.1:8080**. Stop both applications with Ctrl+C. If port 4200 is already occupied by an earlier template session, stop that session before starting another. For a temporary frontend port, use `npx nx serve frontend --port=4201` and include that origin in `CORS_ALLOWED_ORIGINS`.

To run the applications separately in two terminals:

```bash
# Terminal 1, from the project root
cd apps/backend
./mvnw spring-boot:run
```

```bash
# Terminal 2, from the project root
npm run dev --workspace=@study-buddy-matcher/frontend
```

The standalone frontend uses **http://localhost:3000**. Both development entry points explicitly use Webpack, matching the verified production build and supporting workspace checkouts with linked dependencies. On Windows, use `mvnw.cmd` in place of `./mvnw` and run the applications separately. If a Unix checkout loses the wrapper executable bit, run `chmod +x apps/backend/mvnw`.

### Accounts and sample data

Students register or sign in with Firebase email and password. The backend verifies the Firebase ID token, including revocation, and then creates an HttpOnly server session. A newly registered student completes **My profile** before matching. If Firebase registration succeeds while the backend is unavailable, sign in later and use **Finish registration** to create the missing application account. The administrator uses the separate administrator sign-in, configured by `ADMIN_USERNAME` and `ADMIN_PASSWORD`; override the demo defaults before sharing the app.

The database seeds 50 fictional student profiles and 10 courses for matching demonstrations. Those seeded `S001`–`S050` records are **not** Firebase users, so they cannot be selected as login accounts. To demonstrate two-way requests or group decisions in the browser, create two Firebase users and complete their profiles. Students may self-register. Administrators can also enter a student's name and email in **User accounts** to create a passwordless Firebase user and its application account, then request Firebase's password-reset email so the student can set a password. Use a team-controlled real inbox for a live demonstration. Firebase accepting the email request does not prove delivery; check inbox/spam and use **Resend invite** if needed. An invited student should use **Log in** after setting the password, not **Register**.

## Implemented features

- Profile creation/editing: name, school, programme, year, contact, current courses, preferred course, meeting mode, arrangement, primary study goal, preferred group size, and multiple weekly time slots.
- Ranked matches with explainable score contributions; filters for course, goal, meeting mode, arrangement, day/time window, and overlap with the student's own schedule.
- Public profiles hide contacts. An accepted buddy request reveals contacts to both participants; ending it revokes contact access. Directory and matching responses always omit contacts.
- Buddy requests with optional messages, inbox/sent/history views, accept/decline, active connections, and ending connections. Self-requests, duplicate/reverse pending requests, forged senders, and invalid status transitions are rejected.
- Group creation/editing/closure, course discovery, membership requests and decisions, capacity enforcement, removal, ordinary member exit, leadership transfer, and the team-added leader exit rule.
- Administrator student invitations, name/status updates and deletion with Firebase identity and related personal-record cleanup, profile-completion status, active-buddy counts, group counts, pending-request counts and last sign-in time.
- Administrator strategy/weight/result-limit configuration saved immediately to the database. A weight of zero disables that criterion.
- Seed data installed once: 10 courses, 50 fictional student profiles and student accounts, 1 administrator, and 3 study groups. Deleted seed accounts do not reappear at restart.
- Responsive interface with validation, request-in-progress states, errors, empty states, and confirmations for destructive actions.

The requirement-by-requirement implementation map is in [docs/requirements.md](docs/requirements.md). The demonstration walkthrough is in [docs/demo-guide.md](docs/demo-guide.md).

Deleting a student account removes its application records, requests, profile, Firebase Authentication identity if present and server-session access. Seeded fictional accounts have no Firebase identity. Firebase deletion and H2 cleanup are separate operations: Firebase is deleted first and a missing identity is safe on retry. A failed Firebase call stops application deletion; if H2 cleanup fails after Firebase deletion, retry the administrator delete. Deletion does not ban an email from registering a new UID; suspend an account when the goal is to block its current access. The administrator cannot create another administrator through the account form.

## Architecture and template conventions

```text
apps/
  backend/
    src/main/java/studybuddy/backend/
      student/      controller, service, model: profiles and course catalogue
      matching/     service, model, config: scoring and saved configuration
      connection/   controller, service, model: buddy-request lifecycle
      group/        controller, service, model: membership and leadership rules
      admin/        controller, service, model: account lifecycle and usage
      auth/         Firebase token verification, sessions, trusted Actor, API interception
      persistence/  H2 repository and one-time deterministic seed data
      common/       validation helpers and consistent API errors
      config/       origins and request-interceptor configuration
    src/main/resources/
      application.properties  external settings and default matching weights
      schema.sql              database tables
    src/test/                 real-H2 API integration tests with a fake token verifier
  frontend/
    src/app/                  layout, navigation, and global styling
    src/features/             profile, matching, connections, groups, admin screens
    src/components/           reusable form controls and feedback
    src/lib/                  TypeScript contracts and HTTP client
scripts/smoke_test.py          optional real-HTTP admin and restart check
```

The original package names, model names, controller/service/model separation, frontend API/types boundary, Nx workspace, and configurable matching properties are retained. Backend Nx commands are explicitly defined in `apps/backend/project.json`; redundant Spring plugin target inference was removed because it misidentified Maven projects when Next.js constructed the graph from the frontend directory. Domain logic stays in Java services; frontend components only collect input and render responses. See [docs/architecture.md](docs/architecture.md) for diagrams and design decisions.

### Persistence trade-off

`StudyRepository` uses Spring JDBC and H2 to store each template POJO aggregate as JSON in `aggregates(kind, id, payload)`. This preserves the starter's domain models without adding ORM lifecycle behavior or flattening every preference into entities. A separate schema file owns database initialization.

Every business mutation runs in a database transaction and takes a shared database row lock before reading business state. This makes request duplicate checks, group capacity, and cross-record deletion atomic. Reads deserialize fresh objects, so a failed operation cannot leak partially modified in-memory objects. The trade-off is serialized writes and application-enforced relationships; this suits a small, single-process coursework system. It is not a normalized relational schema or a design for large-scale search. A production version would use purpose-specific tables, constraints, migrations, and narrower locks.

The default database is `apps/backend/data/studybuddy.mv.db` when launched with the documented commands. Do not run two backend processes against that file. To make a fresh demonstration database, stop the backend and **move** the `apps/backend/data` directory to a backup location before restarting. Restarting alone preserves all changes. HTTP sessions expire after 60 minutes and are intentionally not persisted.

## Matching formula

The template's five criteria remain separately configurable:

| Criterion | Compatibility factor, from 0 to 1 |
| --- | --- |
| Course | 1 if candidate's preferred course equals the search course; 0.5 if merely enrolled; otherwise 0 |
| Availability | Shared unique weekly minutes divided by the searching student's unique available minutes |
| Meeting mode | 1 if equal or either student allows `EITHER`; otherwise 0 |
| Study goal | 1 for the same primary goal; otherwise 0 |
| Group size and arrangement | 0 for incompatible arrangements; otherwise `1 / (1 + abs(sizeA - sizeB))` |

The selected search course drives course scoring; with **All courses**, the student's saved preferred course is used. Filters restrict candidates before scoring. Availability windows are same-day, minute-granular Singapore times. Adjacent windows do not overlap. Duplicate or overlapping windows are counted only once.

Default weights are **40 / 25 / 15 / 15 / 5**. Strategies are explicit design choices:

- `BALANCED`: use the administrator's relative weights.
- `AVAILABILITY_FIRST`: rank by shared unique weekly minutes, then score and student ID.
- `COURSE_FIRST`: rank by exact preferred-course match, then current-course enrolment, then score and student ID.
- `PARETO`: compare all enabled compatibility factors. Front 1 has no candidate that is at least as good on every enabled factor and better on one; remove it and repeat to find fronts 2, 3, etc. Rank by front, then score and student ID. Cards show the front separately from their weighted score so a lower score above a higher one is explainable.

Effective weights are normalized to exactly 100 using the largest-remainder method, with stable criterion-order ties. Each contribution is `round(effectiveWeight × compatibilityFactor)`. The displayed 0–100 compatibility score is the sum. Balanced results sort by score; the other strategies use the priorities above, so a higher ranked result can have a lower score. Each result states its ranking reason. A zero weight removes a criterion from Pareto comparisons and disables its strict priority in the course-first or availability-first strategy. The result count is limited to the configured 1–50. Pareto sorting makes pairwise comparisons across all eligible peers (O(n² × 5)); it is suitable for this coursework data size, not a large matching marketplace.

Example: with default balanced weights, exact preferred-course alignment, 60 shared minutes out of 120, compatible mode, the same goal, and equal compatible group sizes gives `40 + 13 + 15 + 15 + 5 = 88`.

For the algorithm concepts, trade-offs and presentation example, see [matching algorithms](docs/matching-algorithms.md).

## Group and account rules

- Only the leader or an administrator can edit/close a group, decide requests, remove another member, or transfer leadership.
- Membership and leader IDs in create/update payloads cannot overwrite server-managed membership. The maximum size includes the leader and cannot be reduced below the current member count.
- Acceptance rechecks capacity inside the transaction. A full group cannot receive new requests. Duplicate pending join requests are rejected.
- A leader may transfer and stay, transfer and leave, or close and leave. Transfer requires another active existing member. If the leader is alone, leaving closes the group. Closed groups keep history and cannot reopen; pending requests are declined on closure.
- Closed groups may retain a historical leader ID even after that person exits or is deleted; the UI labels unavailable historical accounts. Active groups always have a valid leader/member.
- An administrator must transfer leadership or close a student's active groups before suspending/deleting their account. Administrators can perform that override in **Study groups**.
- Suspension removes students from matching and blocks their existing sessions; reactivation restores access. Existing relationships remain historical/active records until explicitly ended or the account is deleted.
- Account deletion removes the profile, buddy requests, membership requests, and membership references transactionally. Administrators cannot delete or suspend themselves. Existing account roles are fixed.

## Configuration

Backend environment variables:

| Variable | Default | Purpose |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | Backend port |
| `SERVER_ADDRESS` | `127.0.0.1` | Bind address |
| `DATABASE_URL` | `jdbc:h2:file:./data/studybuddy;DB_CLOSE_ON_EXIT=FALSE` | Database URL, relative to backend working directory |
| `DATABASE_USERNAME` | `sa` | H2 username |
| `DATABASE_PASSWORD` | empty | Local H2 password |
| `FIREBASE_SERVICE_ACCOUNT` | none supplied | Plain absolute path to the private Firebase Admin JSON key outside the project directory; required to start the backend |
| `FIREBASE_DATABASE_URL` | Project Realtime Database URL | Firebase Admin initialisation; study data still uses H2 |
| `ADMIN_USERNAME`, `ADMIN_PASSWORD` | Demo values in `application.properties` | Separate administrator sign-in; override for shared use |
| `CORS_ALLOWED_ORIGINS` | localhost and 127.0.0.1 on ports 3000 and 4200 | Comma-separated browser origins |

Frontend: the required public Firebase web-app values are listed in `apps/frontend/.env.example`. `BACKEND_URL` defaults to `http://127.0.0.1:8080`; set it in `apps/frontend/.env` if needed. The frontend calls same-origin `/api` URLs, forwarded using Next.js rewrites. Restart Next.js after changing these values; production rewrites and `NEXT_PUBLIC_*` values are captured at build time. For a custom frontend port, add its exact origin to `CORS_ALLOWED_ORIGINS` on the backend as well.

Default matching values live under `app.matching.*` in `application.properties`. Once an administrator saves a configuration, the database value takes precedence over file defaults. Backend environment variables must be exported or passed to Java; Spring Boot does not automatically read the frontend `.env.local` file.

## API reference

Every `/api` route except student login/sign-up and administrator login requires an active server session. Mutations require `X-StudyBuddy-Request: 1`. Browser requests additionally pass the configured origin policy. IDs used for ownership are derived from the session. JSON errors use `{ "error": "Readable message" }`; validation is 400, missing session 401, permission failures 403, missing records 404, and conflicting lifecycle operations 409.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/` | Backend health |
| POST | `/api/session/signup` | Register a student with `{idToken, name}` after Firebase registration |
| POST | `/api/session/login` | Exchange a verified Firebase `{idToken}` for a server session |
| POST | `/api/session/admin-login` | Administrator username/password login |
| GET / DELETE | `/api/session` | Current account / sign out |
| GET | `/api/students` | Active public profile directory |
| GET | `/api/students/courses` | Course catalogue |
| GET | `/api/students/{id}` | Viewer-aware profile/contact visibility |
| POST | `/api/students` | Create/update own profile |
| GET | `/api/students/{id}/matches` | Own matches; `courseCode`, `studyGoal`, `studyMode`, `arrangement`, `day`, `startTime`, `endTime`, `overlapOnly` filters |
| POST | `/api/connections/requests` | Send `{receiverId, message}` |
| POST | `/api/connections/requests/{id}/status?status=...` | Accept, decline, or end |
| GET | `/api/connections/students/{id}` | Own request history |
| GET | `/api/connections/students/{id}/active` | Own accepted connections |
| GET / POST | `/api/groups` | List/create groups |
| PUT | `/api/groups/{id}` | Edit group fields |
| GET | `/api/groups/join-requests` | Own and manageable membership requests |
| POST | `/api/groups/{id}/join-requests` | Request membership as current student |
| POST | `/api/groups/join-requests/{id}/decision?decision=...` | Accept/decline membership |
| POST | `/api/groups/{id}/remove-member?studentId=...` | Remove member or leave as ordinary member |
| POST | `/api/groups/{id}/transfer-leadership?newLeaderId=...` | Transfer and stay |
| POST | `/api/groups/{id}/leader-quits?replacementLeaderId=...` | Transfer and leave; alone means close |
| POST | `/api/groups/{id}/leader-quits?closeGroup=true` | Close and leave |
| POST | `/api/groups/{id}/close` | Close; members remain in historical record |
| GET / POST | `/api/admin/accounts` | Usage summaries / update an existing account |
| POST | `/api/admin/accounts/invitations` | Create a Firebase-backed student with `{name, email}`; browser requests the invite email |
| DELETE | `/api/admin/accounts/{id}` | Delete the Firebase identity if present, then the account and related records |
| GET / PUT | `/api/admin/matching-config` | View/update weights, strategy, result limit |

## Verification

```bash
npm run typecheck
npm run lint
npm run test:backend
npm run build
cd apps/backend
./mvnw package
cd ../..
```

The backend suite contains 22 tests: 19 HTTP integration tests using real H2 and separate sessions, plus 3 focused ranking tests. Only the external Firebase token-verification boundary is mocked; these tests do not need a private service-account key. Coverage includes session and role checks, privacy before/after acceptance/ending, actor spoofing, nested input validation, request transitions, filters, matching strategies, duplicate-slot overlap, group capacity, simultaneous acceptance, ownership preservation, leader exit, closed groups, origin restrictions, account suspension/deletion, admin override, and demo venue booking conflicts and release.

With a local Firebase service-account file configured and the backend JAR built, run `python3 scripts/smoke_test.py` for a real-HTTP administrator session and H2 restart check against a disposable database. It does not exercise live student sign-in. The live Firebase walkthrough is recorded in `docs/verification.md`; it used a disposable in-memory H2 database. Run it again with your own Firebase configuration before a class demonstration.

The test resources select Mockito's subclass mock maker. On the verified JDK 25 run, Mockito still prints a non-fatal dynamic-agent warning; all tests complete successfully. The application itself does not use Mockito.

See [docs/verification.md](docs/verification.md) for the live Firebase browser check, its limits and the separate historical pre-Firebase results.

## Libraries and assumptions

Spring Boot 3.5.16 supports the branch's Java 25 target. Next.js 16, React 19, TypeScript, Tailwind/PostCSS, and Nx remain in the template structure. Spring Validation handles input constraints; Spring JDBC and H2 provide local persistence; JUnit/Spring Test verify service/database behaviour. Firebase Authentication is required for live student login; matching itself uses no external API or AI service.

The template represents one current matching preference and one primary study goal per student/group. Weekly availability can contain multiple slots. There is no chat, notification delivery, attendance tracking, or automatic scheduling in the required scope; request inboxes and group status provide in-app feedback. Personal team assignments and a final presentation deck need the team's own authorship. This implementation includes a demo guide and architecture/UML material, not invented contributions or a completed presentation.

AI assistance was used for implementation, tests, documentation, and verification. Team members should review the code, understand and explain the design, verify the rubric mapping, and accurately disclose AI use in their submission.

Reference documentation: [Spring Boot 3.5 system requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html) and [Next.js rewrites](https://nextjs.org/docs/app/api-reference/config/next-config-js/rewrites).
