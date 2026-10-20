package edu.ums.web.notice;

import edu.ums.web.user.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notices")
public class NoticeController {

    private final NoticeService notices;
    private final CurrentUser currentUser;

    public NoticeController(NoticeService notices, CurrentUser currentUser) {
        this.notices = notices;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<NoticeService.NoticeView> list(@RequestParam(defaultValue = "50") int limit) {
        return notices.list(currentUser.require().role(), limit);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public NoticeService.NoticeView create(@Valid @RequestBody NoticeService.NewNotice body) {
        return notices.create(currentUser.id(), body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        notices.delete(id);
    }
}
