package studybuddy.backend.group.model;

/**
 * Published inside the closing transaction so features that depend on groups can clean up their
 * own records without the group package depending on them.
 */
public record GroupClosedEvent(String groupId) {}
