package studybuddy.backend.connection.model;

import jakarta.validation.constraints.*;

import java.time.Instant;

public class BuddyRequest {
    private String id;
    private String senderId;
    @NotBlank private String receiverId;

    @Size(max = 500)
    private String message;

    private RequestStatus status = RequestStatus.PENDING;
    private Instant createdAt = Instant.now();

    public BuddyRequest() {}

    public BuddyRequest(String id, String senderId, String receiverId, String message) {
        this.id = id;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.message = message;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(String receiverId) {
        this.receiverId = receiverId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
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
