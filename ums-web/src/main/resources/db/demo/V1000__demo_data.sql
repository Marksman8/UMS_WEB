-- Demo data (Flyway location db/demo, loaded unless ums.demo.enabled=false is paired
-- with a locations override). All demo accounts get password DemoPass@2026: the
-- placeholder hash '{demo}' below is replaced by a real BCrypt hash at first start.

INSERT INTO department (id, code, name) VALUES
    (1, 'CSA', 'Computer Applications'),
    (2, 'MTH', 'Mathematics');

INSERT INTO programme (id, dept_id, code, name, semesters) VALUES
    (1, 1, 'MCA', 'Master of Computer Applications', 4),
    (2, 1, 'BCA', 'Bachelor of Computer Applications', 6);

INSERT INTO app_user (id, username, password_hash, role, full_name, email) VALUES
    (1,  'admin',      '{demo}', 'ADMIN',   'Meera Krishnan',   'admin@ums.local'),
    (2,  'dev',        '{demo}', 'DEV',     'Ajsal Ashraf',     'dev@ums.local'),
    (3,  'fac.rao',    '{demo}', 'FACULTY', 'Dr. Suresh Rao',   'rao@ums.local'),
    (4,  'fac.menon',  '{demo}', 'FACULTY', 'Dr. Latha Menon',  'menon@ums.local'),
    (5,  'fac.iyer',   '{demo}', 'FACULTY', 'Prof. Karthik Iyer','iyer@ums.local'),
    (6,  'stu.anu',    '{demo}', 'STUDENT', 'Anu Nair',         'anu@ums.local'),
    (7,  'stu.bala',   '{demo}', 'STUDENT', 'Bala Subramanian', 'bala@ums.local'),
    (8,  'stu.chitra', '{demo}', 'STUDENT', 'Chitra Pillai',    'chitra@ums.local'),
    (9,  'stu.deepak', '{demo}', 'STUDENT', 'Deepak Varma',     'deepak@ums.local'),
    (10, 'stu.esha',   '{demo}', 'STUDENT', 'Esha Thomas',      'esha@ums.local'),
    (11, 'stu.farhan', '{demo}', 'STUDENT', 'Farhan Ali',       'farhan@ums.local'),
    (12, 'stu.gita',   '{demo}', 'STUDENT', 'Gita Menon',       'gita@ums.local'),
    (13, 'stu.hari',   '{demo}', 'STUDENT', 'Hari Krishnan',    'hari@ums.local');

INSERT INTO faculty (user_id, dept_id, designation) VALUES
    (3, 1, 'Professor'), (4, 1, 'Associate Professor'), (5, 2, 'Assistant Professor');

INSERT INTO student (user_id, roll_no, programme_id, admission_year, current_semester)
SELECT u.id, 'MCA25' || lpad((u.id - 5)::text, 3, '0'), 1, 2025, 2
FROM app_user u WHERE u.role = 'STUDENT';

INSERT INTO course (id, code, title, credits, dept_id, description) VALUES
    (1, 'CSA501', 'Data Structures',        4, 1, 'Arrays to graphs, with complexity analysis.'),
    (2, 'CSA502', 'Database Systems',       4, 1, 'Relational model, SQL, transactions.'),
    (3, 'CSA503', 'Operating Systems',      3, 1, 'Processes, memory, scheduling, files.'),
    (4, 'CSA504', 'Discrete Mathematics',   3, 2, 'Logic, sets, counting, graph theory.'),
    (5, 'CSA511', 'Advanced Algorithms',    4, 1, 'Dynamic programming, flows, approximation.'),
    (6, 'CSA512', 'Machine Learning',       4, 1, 'Supervised and unsupervised learning.'),
    (7, 'CSA513', 'Software Engineering',   3, 1, 'Process, design, testing, delivery.'),
    (8, 'CSA514', 'Cloud Computing',        3, 1, 'Virtualisation, containers, services.');

INSERT INTO course_prereq (course_id, prereq_id) VALUES (5, 1), (6, 4);

INSERT INTO term (id, name, state, starts_on, ends_on) VALUES
    (1, 'Spring 2026', 'RESULTS_PUBLISHED', DATE '2026-01-12', DATE '2026-05-08'),
    (2, 'Autumn 2026', 'REGISTRATION_OPEN', DATE '2026-10-26', DATE '2027-02-26');

INSERT INTO section (id, course_id, term_id, faculty_id, code, capacity, enrolled_count, day_of_week, start_min, end_min, room) VALUES
    (1, 1, 1, 3, 'A', 40, 8, 1,  540,  600, 'LH-101'),
    (2, 2, 1, 4, 'A', 40, 8, 2,  540,  630, 'LH-102'),
    (3, 3, 1, 5, 'A', 40, 8, 3,  600,  660, 'LH-103'),
    (4, 4, 1, 5, 'A', 40, 8, 4,  540,  600, 'LH-104'),
    (5, 5, 2, 3, 'A', 40, 0, 1,  540,  630, 'LH-201'),
    (6, 6, 2, 4, 'A', 30, 0, 2,  660,  750, 'LH-202'),
    (7, 7, 2, 5, 'A',  2, 2, 3,  540,  600, 'LH-203'),
    (8, 8, 2, 4, 'A', 30, 0, 3,  570,  630, 'LH-204');

INSERT INTO assessment_component (id, section_id, name, max_marks, weight)
SELECT (s.id - 1) * 3 + k.n, s.id, k.name, k.max_marks, k.weight
FROM section s
CROSS JOIN (VALUES (1, 'Quiz', 20, 20), (2, 'Midterm', 30, 30), (3, 'Final exam', 50, 50)) AS k(n, name, max_marks, weight);

INSERT INTO mark_sheet (id, section_id, state)
SELECT s.id, s.id, CASE WHEN s.term_id = 1 THEN 'PUBLISHED' ELSE 'DRAFT' END FROM section s;

-- History: every student took the four Spring 2026 sections.
INSERT INTO enrolment (id, student_id, section_id)
SELECT (st.user_id - 6) * 4 + s.id, st.user_id, s.id
FROM student st CROSS JOIN section s WHERE s.term_id = 1;

-- Autumn 2026: two students already hold the two seats of CSA513.
INSERT INTO enrolment (id, student_id, section_id) VALUES (33, 6, 7), (34, 7, 7);

INSERT INTO mark (component_id, enrolment_id, value)
SELECT c.id, e.id,
       round(c.max_marks * (0.40 + (abs(hashtext(e.id::text || ':' || c.id::text)) % 55)::numeric / 100), 1)
FROM enrolment e JOIN assessment_component c ON c.section_id = e.section_id
WHERE e.section_id <= 4;

INSERT INTO result (enrolment_id, total, grade, points)
SELECT e.id, t.total, g.grade, g.points
FROM enrolment e
CROSS JOIN LATERAL (
    SELECT round(COALESCE(sum(m.value / c.max_marks * c.weight), 0), 2) AS total
    FROM assessment_component c
    LEFT JOIN mark m ON m.component_id = c.id AND m.enrolment_id = e.id
    WHERE c.section_id = e.section_id) t
CROSS JOIN LATERAL (
    SELECT grade, points FROM grade_scale WHERE min_total <= t.total ORDER BY min_total DESC LIMIT 1) g
WHERE e.section_id <= 4;

INSERT INTO term_result (student_id, term_id, credits, sgpa)
SELECT e.student_id, s.term_id, sum(c.credits),
       round(sum(r.points * c.credits)::numeric / sum(c.credits), 2)
FROM result r
JOIN enrolment e ON e.id = r.enrolment_id
JOIN section s ON s.id = e.section_id
JOIN course c ON c.id = s.course_id
WHERE s.term_id = 1
GROUP BY e.student_id, s.term_id;

-- Eight weekly class sessions per history section, with per-student attendance habits.
INSERT INTO class_session (id, section_id, held_on, topic)
SELECT (s.id - 1) * 8 + n, s.id, DATE '2026-01-12' + (n - 1) * 7 + (s.day_of_week - 1), 'Lecture ' || n
FROM section s CROSS JOIN generate_series(1, 8) AS n WHERE s.term_id = 1;

INSERT INTO attendance (session_id, enrolment_id, present)
SELECT cs.id, e.id,
       (abs(hashtext(e.id::text || ':' || cs.id::text)) % 100) < (55 + abs(hashtext(e.student_id::text)) % 40)
FROM class_session cs JOIN enrolment e ON e.section_id = cs.section_id AND e.state = 'ENROLLED';

INSERT INTO notice (id, title, body, audience, author_id) VALUES
    (1, 'Autumn 2026 registration is open', 'Register for your Autumn courses from the Register page. Seats are limited and given in order of registration; the credit limit is 26.', 'ALL', 1),
    (2, 'Marks submission deadline', 'Faculty: submit mark sheets for approval within 48 hours of the final exam. Returned sheets can be edited and resubmitted.', 'FACULTY', 1),
    (3, 'Attendance reminder', 'Attendance below 75% in any course may bar you from the end-term exam. Check the Attendance page regularly.', 'STUDENT', 1);

-- Keep identity sequences ahead of the explicit ids used above.
SELECT setval(pg_get_serial_sequence('department', 'id'),          (SELECT max(id) FROM department));
SELECT setval(pg_get_serial_sequence('programme', 'id'),           (SELECT max(id) FROM programme));
SELECT setval(pg_get_serial_sequence('app_user', 'id'),            (SELECT max(id) FROM app_user));
SELECT setval(pg_get_serial_sequence('course', 'id'),              (SELECT max(id) FROM course));
SELECT setval(pg_get_serial_sequence('term', 'id'),                (SELECT max(id) FROM term));
SELECT setval(pg_get_serial_sequence('section', 'id'),             (SELECT max(id) FROM section));
SELECT setval(pg_get_serial_sequence('assessment_component', 'id'),(SELECT max(id) FROM assessment_component));
SELECT setval(pg_get_serial_sequence('enrolment', 'id'),           (SELECT max(id) FROM enrolment));
SELECT setval(pg_get_serial_sequence('class_session', 'id'),       (SELECT max(id) FROM class_session));
SELECT setval(pg_get_serial_sequence('mark_sheet', 'id'),          (SELECT max(id) FROM mark_sheet));
SELECT setval(pg_get_serial_sequence('notice', 'id'),              (SELECT max(id) FROM notice));
