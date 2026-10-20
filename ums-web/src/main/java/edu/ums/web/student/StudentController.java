package edu.ums.web.student;

import edu.ums.web.user.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Student area. No endpoint takes a user id: the student is always the signed-in account. */
@RestController
@RequestMapping("/api/student")
@PreAuthorize("hasRole('STUDENT')")
public class StudentController {

    private final RegistrationService registration;
    private final TranscriptService transcripts;
    private final StudentPortalService portal;
    private final CurrentUser currentUser;

    public StudentController(RegistrationService registration, TranscriptService transcripts,
                             StudentPortalService portal, CurrentUser currentUser) {
        this.registration = registration;
        this.transcripts = transcripts;
        this.portal = portal;
        this.currentUser = currentUser;
    }

    @GetMapping("/summary")
    public StudentPortalService.StudentSummary summary() {
        return portal.summary(currentUser.id());
    }

    @GetMapping("/sections/open")
    public List<RegistrationService.SectionOffer> openSections() {
        return registration.openSections(currentUser.id());
    }

    @PostMapping("/registrations")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationService.Registered register(@Valid @RequestBody RegisterRequest body) {
        return registration.register(currentUser.id(), body.sectionId());
    }

    @DeleteMapping("/registrations/{enrolmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void drop(@PathVariable long enrolmentId) {
        registration.drop(currentUser.id(), enrolmentId);
    }

    @GetMapping("/courses")
    public List<RegistrationService.MyCourse> courses() {
        return registration.myCourses(currentUser.id());
    }

    @GetMapping("/attendance")
    public List<StudentPortalService.AttendanceLine> attendance() {
        return portal.attendance(currentUser.id());
    }

    @GetMapping("/transcript")
    public TranscriptService.Transcript transcript() {
        return transcripts.transcript(currentUser.id());
    }

    public record RegisterRequest(@NotNull Long sectionId) {
    }
}
