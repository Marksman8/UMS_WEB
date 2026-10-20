package edu.ums.web.academic;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Departments, programmes and the course catalogue. */
public interface CatalogService {

    List<Dept> departments();

    Dept createDepartment(NewDept request);

    List<Programme> programmes();

    Programme createProgramme(NewProgramme request);

    List<Course> courses(String search);

    Course createCourse(NewCourse request);

    record Dept(long id, String code, String name) {
    }

    record Programme(long id, long deptId, String deptCode, String code, String name, int semesters) {
    }

    record Course(long id, String code, String title, int credits, String deptCode, String description, String prereqs) {
    }

    record NewDept(@NotBlank @Pattern(regexp = "[A-Za-z0-9]{2,10}", message = "2-10 letters or digits") String code,
                   @NotBlank @Size(max = 120) String name) {
    }

    record NewProgramme(@NotNull Long deptId,
                        @NotBlank @Pattern(regexp = "[A-Za-z0-9]{2,10}", message = "2-10 letters or digits") String code,
                        @NotBlank @Size(max = 120) String name,
                        @Min(1) @Max(12) int semesters) {
    }

    record NewCourse(@NotBlank @Pattern(regexp = "[A-Za-z0-9]{2,12}", message = "2-12 letters or digits") String code,
                     @NotBlank @Size(max = 160) String title,
                     @Min(1) @Max(6) int credits,
                     @NotNull Long deptId,
                     @Size(max = 400) String description,
                     List<Long> prereqIds) {
    }
}
