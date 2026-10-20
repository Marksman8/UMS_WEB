package edu.ums.web.student;

import edu.ums.web.ai.AiContextContributor;
import edu.ums.web.common.Rs;
import edu.ums.web.notice.NoticeService;
import edu.ums.web.user.Role;
import edu.ums.web.user.UmsPrincipal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

// Complexity: time O(C + R + A) per call — the student's own courses, results and attendance rows, each already LIMITed; space O(same).

/** What the assistant may tell a student: only that student's own data plus public notices. */
@Component
public class StudentAiContext implements AiContextContributor {

    private final StudentPortalService portal;
    private final RegistrationService registration;
    private final TranscriptService transcripts;
    private final NoticeService notices;

    public StudentAiContext(StudentPortalService portal, RegistrationService registration,
                            TranscriptService transcripts, NoticeService notices) {
        this.portal = portal;
        this.registration = registration;
        this.transcripts = transcripts;
        this.notices = notices;
    }

    @Override
    public Role role() {
        return Role.STUDENT;
    }

    @Override
    public List<Fact> facts(UmsPrincipal who) {
        StudentPortalService.StudentSummary s = portal.summary(who.id());
        List<Fact> facts = new ArrayList<>();

        facts.add(new Fact("profile", List.of("who am i", "my name", "roll", "programme", "semester", "credit", "profile"),
                s.fullName() + " (roll " + s.rollNo() + "), " + s.programme() + ", semester " + s.semester()
                        + ". Credits registered this term: " + s.currentCredits() + " of " + s.creditLimit() + "."));

        TranscriptService.Transcript t = transcripts.transcript(who.id());
        StringBuilder results = new StringBuilder();
        results.append(t.cgpa() == null ? "No published results yet." : "CGPA " + t.cgpa() + " over " + t.credits() + " credits.");
        for (TranscriptService.TermBlock block : t.terms()) {
            results.append("\n").append(block.term()).append(": SGPA ").append(block.sgpa()).append(" - ");
            results.append(String.join(", ", block.courses().stream()
                    .map(c -> c.code() + " " + c.grade() + " (" + c.total() + ")").toList()));
        }
        facts.add(new Fact("results", List.of("cgpa", "gpa", "sgpa", "grade", "result", "marks", "transcript", "score"), results.toString()));

        List<StudentPortalService.AttendanceLine> att = portal.attendance(who.id());
        StringBuilder attendance = new StringBuilder();
        if (att.isEmpty()) {
            attendance.append("No attendance has been recorded yet.");
        } else {
            attendance.append("Overall attendance ").append(s.attendancePercent()).append("%. The minimum is ")
                    .append(s.attendanceThreshold()).append("%.");
            for (StudentPortalService.AttendanceLine a : att) {
                attendance.append("\n").append(a.term()).append(" ").append(a.code()).append(" ").append(a.title()).append(": ")
                        .append(a.present()).append("/").append(a.held()).append(" = ").append(a.percent()).append("%")
                        .append(a.belowThreshold() ? " (BELOW the minimum)" : "");
            }
        }
        facts.add(new Fact("attendance", List.of("attendance", "present", "absent", "shortage", "75", "detain", "bunk"), attendance.toString()));

        List<RegistrationService.MyCourse> mine = registration.myCourses(who.id());
        StringBuilder courses = new StringBuilder();
        if (mine.isEmpty()) {
            courses.append("You are not registered for any course in the current term.");
        }
        for (RegistrationService.MyCourse c : mine) {
            courses.append(courses.length() > 0 ? "\n" : "").append(c.courseCode()).append(" ").append(c.title()).append(" (")
                    .append(c.credits()).append(" cr), ").append(Rs.dayName(c.dayOfWeek())).append(" ").append(c.start())
                    .append("-").append(c.end()).append(", ").append(c.room()).append(", ").append(c.faculty());
        }
        facts.add(new Fact("courses", List.of("course", "class", "timetable", "schedule", "registered", "today", "subject",
                "lecture", "enrolled", "room", "teacher"), courses.toString()));

        List<RegistrationService.SectionOffer> open = registration.openSections(who.id());
        StringBuilder offers = new StringBuilder();
        if (open.isEmpty()) {
            offers.append("Registration is not open for any section right now.");
        }
        for (RegistrationService.SectionOffer o : open) {
            offers.append(offers.length() > 0 ? "\n" : "").append(o.courseCode()).append(" ").append(o.title()).append(" (")
                    .append(o.credits()).append(" cr), ").append(Rs.dayName(o.dayOfWeek())).append(" ").append(o.start())
                    .append("-").append(o.end()).append(", seats left ").append(o.seatsLeft())
                    .append(o.registered() ? ", you are registered" : "")
                    .append(o.missingPrereqs() != null ? ", missing prerequisite " + o.missingPrereqs() : "");
        }
        facts.add(new Fact("open sections", List.of("register", "open", "seat", "enrol", "available", "offer", "prereq",
                "elective", "can i take", "which course"), offers.toString()));

        StringBuilder news = new StringBuilder();
        for (NoticeService.NoticeView n : notices.list(Role.STUDENT, 5)) {
            news.append(news.length() > 0 ? "\n" : "").append(n.title()).append(": ").append(n.body());
        }
        facts.add(new Fact("notices", List.of("notice", "announce", "news", "circular"),
                news.length() == 0 ? "There are no notices." : news.toString()));
        return List.copyOf(facts);
    }
}
