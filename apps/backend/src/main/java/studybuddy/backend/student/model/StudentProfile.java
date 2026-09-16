package studybuddy.backend.student.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.ArrayList;
import java.util.List;

public class StudentProfile {
    private String id;

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotBlank
    @Size(max = 100)
    private String school;

    @NotBlank
    @Size(max = 120)
    private String programme;

    @Min(1)
    @Max(8)
    private int yearOfStudy;

    @NotBlank
    @Pattern(regexp = "[+0-9 ()-]{7,20}")
    private String contactNumber;

    @NotEmpty
    @Size(max = 20)
    private List<String> currentCourses = new ArrayList<>();

    @NotNull @Valid private StudyPreference preference;

    public StudentProfile() {}

    public StudentProfile(
            String id,
            String name,
            String school,
            String programme,
            int yearOfStudy,
            String contactNumber,
            List<String> currentCourses,
            StudyPreference preference) {
        this.id = id;
        this.name = name;
        this.school = school;
        this.programme = programme;
        this.yearOfStudy = yearOfStudy;
        this.contactNumber = contactNumber;
        this.currentCourses =
                currentCourses == null ? new ArrayList<>() : new ArrayList<>(currentCourses);
        this.preference = preference;
    }

    public StudentProfile toPublicProfile(boolean revealContact) {
        return new StudentProfile(
                id,
                name,
                school,
                programme,
                yearOfStudy,
                revealContact ? contactNumber : null,
                currentCourses,
                preference);
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

    public String getSchool() {
        return school;
    }

    public void setSchool(String school) {
        this.school = school;
    }

    public String getProgramme() {
        return programme;
    }

    public void setProgramme(String programme) {
        this.programme = programme;
    }

    public int getYearOfStudy() {
        return yearOfStudy;
    }

    public void setYearOfStudy(int yearOfStudy) {
        this.yearOfStudy = yearOfStudy;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public void setContactNumber(String contactNumber) {
        this.contactNumber = contactNumber;
    }

    public List<String> getCurrentCourses() {
        return currentCourses;
    }

    public void setCurrentCourses(List<String> currentCourses) {
        this.currentCourses =
                currentCourses == null ? new ArrayList<>() : new ArrayList<>(currentCourses);
    }

    public StudyPreference getPreference() {
        return preference;
    }

    public void setPreference(StudyPreference preference) {
        this.preference = preference;
    }
}
