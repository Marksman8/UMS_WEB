package edu.ums.web.admin;

import edu.ums.web.academic.CatalogService;
import edu.ums.web.academic.SectionService;
import edu.ums.web.academic.TermService;
import edu.ums.web.api.PageResult;
import edu.ums.web.audit.ActivityService;
import edu.ums.web.insight.InsightsService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Admin area: people, academic setup, terms, approvals, activity. Thin: every rule lives in a service. */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserAdminService users;
    private final CatalogService catalog;
    private final TermService terms;
    private final SectionService sections;
    private final ApprovalService approvals;
    private final InsightsService insights;
    private final ActivityService activity;

    public AdminController(UserAdminService users, CatalogService catalog, TermService terms, SectionService sections,
                           ApprovalService approvals, InsightsService insights, ActivityService activity) {
        this.users = users;
        this.catalog = catalog;
        this.terms = terms;
        this.sections = sections;
        this.approvals = approvals;
        this.insights = insights;
        this.activity = activity;
    }

    @GetMapping("/summary")
    public InsightsService.AdminSummary summary() {
        return insights.adminSummary();
    }

    // ----- people ---------------------------------------------------------------------------------

    @GetMapping("/users")
    public PageResult<UserAdminService.UserRow> users(@RequestParam(required = false) String q,
                                                      @RequestParam(required = false) String role,
                                                      @RequestParam(required = false) Long after,
                                                      @RequestParam(defaultValue = "50") int limit) {
        return users.list(q, role, after, limit);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserAdminService.CreatedUser createUser(@Valid @RequestBody UserAdminService.NewUser body) {
        return users.create(body);
    }

    @PostMapping("/users/{id}/active")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setActive(@PathVariable long id, @RequestBody ActiveRequest body) {
        users.setActive(id, body.active());
    }

    @PostMapping("/users/{id}/password")
    public PasswordResult resetPassword(@PathVariable long id, @RequestBody(required = false) PasswordRequest body) {
        return new PasswordResult(users.resetPassword(id, body == null ? null : body.password()));
    }

    @PostMapping(value = "/users/import", consumes = "text/plain")
    public UserAdminService.ImportReport importUsers(@RequestBody String csv) {
        return users.importCsv(csv);
    }

    // ----- academic setup ---------------------------------------------------------------------------

    @GetMapping("/departments")
    public List<CatalogService.Dept> departments() {
        return catalog.departments();
    }

    @PostMapping("/departments")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogService.Dept createDepartment(@Valid @RequestBody CatalogService.NewDept body) {
        return catalog.createDepartment(body);
    }

    @GetMapping("/programmes")
    public List<CatalogService.Programme> programmes() {
        return catalog.programmes();
    }

    @PostMapping("/programmes")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogService.Programme createProgramme(@Valid @RequestBody CatalogService.NewProgramme body) {
        return catalog.createProgramme(body);
    }

    @GetMapping("/courses")
    public List<CatalogService.Course> courses(@RequestParam(required = false) String q) {
        return catalog.courses(q);
    }

    @PostMapping("/courses")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogService.Course createCourse(@Valid @RequestBody CatalogService.NewCourse body) {
        return catalog.createCourse(body);
    }

    // ----- terms and sections -----------------------------------------------------------------------

    @GetMapping("/terms")
    public List<TermService.TermView> terms() {
        return terms.list();
    }

    @PostMapping("/terms")
    @ResponseStatus(HttpStatus.CREATED)
    public TermService.TermView createTerm(@Valid @RequestBody TermService.NewTerm body) {
        return terms.create(body);
    }

    @PostMapping("/terms/{id}/advance")
    public TermService.TermView advance(@PathVariable long id) {
        return terms.advance(id);
    }

    @GetMapping("/sections")
    public List<SectionService.SectionRow> sections(@RequestParam(required = false) Long termId) {
        return sections.list(termId);
    }

    @PostMapping("/sections")
    @ResponseStatus(HttpStatus.CREATED)
    public SectionService.SectionRow createSection(@Valid @RequestBody SectionService.NewSection body) {
        return sections.create(body);
    }

    @GetMapping("/faculty-options")
    public List<SectionService.FacultyOption> facultyOptions() {
        return sections.faculty();
    }

    // ----- approvals and activity -------------------------------------------------------------------

    @GetMapping("/approvals")
    public List<ApprovalService.PendingSheet> approvals() {
        return approvals.pending();
    }

    @PostMapping("/approvals/{sheetId}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(@PathVariable long sheetId) {
        approvals.approve(sheetId);
    }

    @PostMapping("/approvals/{sheetId}/return")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sendBack(@PathVariable long sheetId) {
        approvals.sendBack(sheetId);
    }

    @GetMapping("/activity")
    public List<ActivityService.ActivityRow> activity(@RequestParam(required = false) Long before,
                                                      @RequestParam(defaultValue = "50") int limit) {
        return activity.latest(before, limit);
    }

    public record ActiveRequest(boolean active) {
    }

    public record PasswordRequest(String password) {
    }

    public record PasswordResult(String password) {
    }
}
