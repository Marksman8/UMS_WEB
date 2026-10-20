package edu.ums.web.faculty;

import edu.ums.web.insight.InsightsService;
import edu.ums.web.user.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Faculty area. The faculty member is always the signed-in account; section ownership is checked in the services. */
@RestController
@RequestMapping("/api/faculty")
@PreAuthorize("hasRole('FACULTY')")
public class FacultyController {

    private final FacultySectionService sections;
    private final AttendanceService attendance;
    private final MarksService marks;
    private final InsightsService insights;
    private final CurrentUser currentUser;

    public FacultyController(FacultySectionService sections, AttendanceService attendance, MarksService marks,
                             InsightsService insights, CurrentUser currentUser) {
        this.sections = sections;
        this.attendance = attendance;
        this.marks = marks;
        this.insights = insights;
        this.currentUser = currentUser;
    }

    @GetMapping("/summary")
    public InsightsService.FacultySummary summary() {
        return insights.facultySummary(currentUser.id());
    }

    @GetMapping("/sections")
    public List<FacultySectionService.SectionBrief> sections() {
        return sections.mySections(currentUser.id());
    }

    @GetMapping("/sections/{id}/roster")
    public List<FacultySectionService.RosterRow> roster(@PathVariable long id) {
        return sections.roster(id, currentUser.id());
    }

    @GetMapping("/sections/{id}/sessions")
    public List<AttendanceService.SessionRow> sessions(@PathVariable long id) {
        return attendance.sessions(id, currentUser.id());
    }

    @PostMapping("/sections/{id}/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public AttendanceService.SessionRow openSession(@PathVariable long id, @Valid @RequestBody AttendanceService.NewSession body) {
        return attendance.openSession(id, currentUser.id(), body);
    }

    @GetMapping("/sessions/{sessionId}/attendance")
    public List<AttendanceService.AttendanceEntry> attendanceSheet(@PathVariable long sessionId) {
        return attendance.sheet(sessionId, currentUser.id());
    }

    @PutMapping("/sessions/{sessionId}/attendance")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAttendance(@PathVariable long sessionId, @Valid @RequestBody List<AttendanceService.@Valid AttendanceMark> body) {
        attendance.mark(sessionId, currentUser.id(), body);
    }

    @GetMapping("/sections/{id}/marks")
    public MarksService.MarkGrid marks(@PathVariable long id) {
        return marks.grid(id, currentUser.id());
    }

    @PutMapping("/sections/{id}/marks")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void saveMarks(@PathVariable long id, @Valid @RequestBody List<MarksService.@Valid MarkEntry> body) {
        marks.save(id, currentUser.id(), body);
    }

    @PostMapping("/sections/{id}/submit")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void submit(@PathVariable long id) {
        marks.submit(id, currentUser.id());
    }
}
