# Verification record

## Live Firebase and browser check — 17 October 2026

The frontend used the supplied Firebase web configuration and the backend used the supplied Firebase Admin key, stored outside the project directory. The backend used an isolated in-memory H2 database for this check; its application data was deliberately discarded at restart. Two disposable Firebase users were created with unique test addresses. The browser check verified student registration and login, profile completion, 50 seeded profiles and 10 courses, a 100% peer match with five score contributions, contact privacy before and after buddy acceptance and again after ending the connection, group creation and joining, leader transfer, administrator login rejection and success, saving Pareto strategy and seeing the changed ranking explanation.

After a backend restart, the same Firebase identity reached **Finish registration**, created a fresh application account and entered the app. The backend started with a plain absolute `FIREBASE_SERVICE_ACCOUNT` path. The admin table displayed sign-in timestamps for the recovered student and administrator. Backend integration tests passed after the sign-in timestamp change: 19 HTTP integration tests and 3 focused ranking tests, including a venue recommendation/booking/release test. Frontend typecheck, lint and production build passed. After the disposable check, the local backend was restarted on the existing file-backed H2 database; the frontend and backend returned HTTP 200 and an unauthenticated venue API request returned HTTP 401. The service-account JSON was removed from backend resources because Maven had copied it into `target/classes`; it now lives outside the repository and was not included in the build.

Limits: the browser walkthrough did not exercise every filter, every failure path, venue/session booking or a production deployment. The two test identities remain in Firebase Authentication. The disposable H2 restart erased their profiles, requests and groups, so this restart is **not** evidence of file-backed H2 persistence. The backend tests mock Firebase token verification; only the browser walkthrough verifies real Firebase token exchange. The section below is an older pre-Firebase record and should not be presented as current authentication evidence.

## Historical pre-Firebase check — 8 September 2026

Historical record from 8 September 2026, before Firebase Authentication was merged. All browser mutations used fictional demo accounts in an isolated test database. This record does not verify the current login flow; use the current README test commands and a separate live Firebase check.

| Check | Result |
| --- | --- |
| TypeScript, `npm run typecheck` | Passed |
| ESLint over frontend source with the project's Nx/React configuration | Passed, after building the Nx project graph |
| Next.js production build using Webpack | Passed |
| Maven compile/package, Java source release 17 | Passed on the available Temurin JDK 25.0.4 |
| Backend integration tests | 16 passed; 0 failures; 0 errors; 0 skipped |
| Simultaneous acceptance of two requests for one remaining seat | One 200 response, one 409 response; capacity retained |
| Real HTTP session and restart smoke test | Passed |
| Student profile update and new-account onboarding | Verified in browser |
| Request send, recipient acceptance, contact reveal | Verified in browser |
| Contact hidden before acceptance and after ending, both directions | Verified by integration and real-HTTP smoke tests |
| Group creation, membership request/approval, leadership transfer | Verified in browser |
| Admin account creation and strategy update | Verified in browser |
| Desktop and 390px mobile viewport | Visually inspected; no horizontal page overflow |
| Nx project discovery | Both frontend and backend recognized |
| Nx backend serve target | Started successfully on temporary port 8082 |
| Nx frontend serve target with Webpack | Started on temporary port 4201 |

The restart smoke test confirms saved profile fields, accepted buddy connections/contact access, group memberships, matching strategy, and account deletion survive a real backend process restart. Deleted seeded accounts do not reappear. The smoke script shuts down its temporary servers and deletes its temporary database.

The original template's frontend was already running on port 4200. The combined `npm run dev` attempt correctly launched the backend but could not bind a second frontend to that occupied port. The frontend's same Nx serve executor was subsequently checked on port 4201. Do not run two frontend instances against the same `.next/dev` directory.

Two starter integration issues were corrected: redundant Spring-plugin graph inference interpreted the Maven project as an invalid Gradle path when called from the frontend directory; and Nx's implicit Turbopack choice rejected a linked node_modules checkout. Explicit backend targets and a consistent Webpack development/build configuration resolved them.

An initial browser login on a custom test port was rejected by the backend origin allowlist. Custom origins are now configurable, and default localhost/127.0.0.1 origins are tested. The browser test used an explicit test-port origin.

Build tools still print non-failing notices about old browser compatibility data and Next.js TypeScript project-reference support. JDK 25 prints a native-access notice for the retained Tomcat version. Application dependencies were not broadly upgraded as part of this template-preserving implementation.

Not verified: public deployment, password authentication, email delivery, distributed/multi-instance operation, production security hardening, Windows execution, or every browser engine. The API tests cover failure/permission cases that were not repeated through every UI button. These checks establish coursework functionality, not production readiness or a guarantee against all defects.
