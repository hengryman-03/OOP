package studybuddy.backend.group.model;

import studybuddy.backend.connection.model.RequestStatus;

import java.time.Instant;

public class MembershipRequest {
    private String id;
    private String groupId;
    private String studentId;
    private RequestStatus status = RequestStatus.PENDING;
    private Instant createdAt = Instant.now();

    public MembershipRequest() {}

    public MembershipRequest(String id, String groupId, String studentId) {
        this.id = id;
        this.groupId = groupId;
        this.studentId = studentId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public void setStatus(RequestStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
