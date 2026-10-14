package studybuddy.backend.admin.model;

import jakarta.validation.constraints.*;

public class UserAccount {
    private String id;
    private java.time.Instant createdAt = java.time.Instant.now();
    private java.time.Instant lastActiveAt;

    public java.time.Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(java.time.Instant value) {
        createdAt = value;
    }

    public java.time.Instant getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(java.time.Instant value) {
        lastActiveAt = value;
    }

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotNull
    @Pattern(regexp = "STUDENT|SYSTEM_ADMINISTRATOR")
    private String role;

    @NotNull
    @Pattern(regexp = "ACTIVE|SUSPENDED")
    private String status = "ACTIVE";

    public UserAccount() {}

    public UserAccount(String id, String name, String role, String status) {
        this.id = id;
        this.name = name;
        this.role = role;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
