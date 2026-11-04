package studybuddy.backend.group.model;

/**
 * Published inside the leaving transaction so records a departing member owns can pass to the
 * leader without the group package depending on them.
 */
public record MemberLeftEvent(String groupId, String studentId, String leaderId) {}
