package studybuddy.backend;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import studybuddy.backend.admin.model.UserAccount;
import studybuddy.backend.admin.service.AdminService;
import studybuddy.backend.auth.Actor;
import studybuddy.backend.auth.FirebaseConfig;
import studybuddy.backend.persistence.StudyRepository;

class AdminServiceTest {
    @Test
    void failedApplicationSaveRemovesNewFirebaseIdentity() {
        StudyRepository repository = mock(StudyRepository.class);
        FirebaseConfig firebase = mock(FirebaseConfig.class);
        when(firebase.createStudent("Invited Student", "student@example.com")).thenReturn("new-uid");
        when(repository.save(eq("new-uid"), any(UserAccount.class)))
                .thenThrow(new IllegalStateException("Database unavailable"));

        assertThrows(
                IllegalStateException.class,
                () -> new AdminService(repository, firebase)
                        .inviteStudent(
                                "Invited Student",
                                "student@example.com",
                                new Actor("ADMIN001", "SYSTEM_ADMINISTRATOR")));

        verify(firebase).deleteUser("new-uid");
    }
}
