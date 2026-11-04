package studybuddy.backend.group.model;

/**
 * Published before an accepted member is added, so a listener can veto the join by throwing.
 * The group package then needs no knowledge of what limits a group's size.
 */
public record MemberJoiningEvent(String groupId, int newMemberCount) {}
