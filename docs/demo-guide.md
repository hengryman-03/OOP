# Demonstration walkthrough

Configure Firebase as described in the README and register two student accounts before the demonstration. Complete both profiles. Seeded profiles provide realistic matching candidates, but their `S001`–`S050` IDs are not Firebase logins. Earlier actions persist in H2 across backend restarts.

1. Sign in as the first registered student. Point out the 50 fictional seed profiles and 10 courses.
2. Open **My profile**. Change the study goal or availability and save. Show the contact privacy hint and current-course selection. Keep the preferred course within the selected current courses.
3. Open **Find buddies**. Select IS442; try a study goal, meeting mode and availability filter. Expand **Why this match?** on a result and explain how the five contributions add to the displayed score and why the selected algorithm ranked it there.
4. Open a result's study profile and confirm the contact number is hidden. To demonstrate two-way actions, send a buddy request to the second registered Firebase student.
5. Sign out and sign in as the second student. Open **Connections → Inbox**, accept the request and show that both students can now see each other's contact number. End the connection and demonstrate that privacy is restored.
6. As the first student, create an IS442 group with a name, description, goal, meeting mode, capacity and one or more slots. Sign in as the second student and request to join. Return to the first account to accept the membership request.
7. In **Leadership handover**, pick the accepted member. Demonstrate **Transfer & stay** or **Transfer & leave**, then sign in as the replacement to show their management access.
8. Use **System administrator sign-in** to show account usage, group oversight and matching configuration. Use the configured administrator credentials, not a seeded-student login. Invite a student with a team-controlled inbox, follow the Firebase password email and log in as that student. If the email request fails, use **Resend invite**. Delete only a disposable demo account, then confirm its old Firebase login no longer works.
9. Change the matching strategy or criterion weights and save. Return to the first student and run the same search. Compare balanced scoring with strict availability/course priority and Pareto fronts; a higher ranked result may have a lower score. A zero weight disables that criterion and at least one criterion must remain positive.
10. Restart the backend, sign in again and show that saved data/configuration remain. Explain that server sessions themselves are intentionally temporary.

## Suggested 12-minute presentation allocation

- 2 minutes: problem, actors, architecture and OOP responsibilities.
- 2 minutes: matching formula, availability calculation, strategy choices and trade-offs.
- 6 minutes: student, connections, groups, leader exit, and admin demonstration.
- 2 minutes: verification, limitations, actual team contributions, libraries, and AI-use disclosure.

Use the class diagram and component responsibilities in `architecture.md`. Fill in the team's real division of work and individual understanding; do not present generated implementation as evidence of each person's contribution. The final PPT and rehearsal remain team submission work, not completed application behavior.
