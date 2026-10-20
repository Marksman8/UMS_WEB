# UMS — University Management System (web)

Java 21 · Spring Boot 3.5 · PostgreSQL · plain JavaScript front end. Cream and mid-green theme.
Four roles: **Student**, **Faculty**, **Admin**, **Developer** (SQL console + live feed).

## Run it

1. Create an empty database (pgAdmin or `createdb ums_web`).
2. Set the connection (defaults shown):
   ```
   UMS_DB_URL=jdbc:postgresql://localhost:5432/ums_web?currentSchema=ums
   UMS_DB_USER=postgres
   UMS_DB_PASSWORD=postgres
   ```
3. `mvn spring-boot:run`, then open http://localhost:8080
4. Flyway creates schema `ums` and loads demo data. Demo logins (password `DemoPass@2026`):
   `admin`, `dev`, `fac.rao`, `fac.menon`, `fac.iyer`, `stu.anu` … `stu.hari`.

Clean database without demo data: set `UMS_FLYWAY_EXTRA=` (empty) before the first start, then create your own
admin directly in SQL. **Change or remove demo passwords before any real use.**

## See the data in pgAdmin
Register server → localhost:5432 → database `ums_web` → Schemas → `ums` → Tables. Everything you add in the app appears there.

## AI assistant
Order of use: Claude if `ANTHROPIC_API_KEY` is set, else your local **Ollama** (first installed `qwen*` model, or set
`UMS_OLLAMA_MODEL`; URL via `UMS_OLLAMA_URL`, default http://localhost:11434), else a built-in assistant. The assistant only receives facts the signed-in user may see. The key is never sent to the browser.

## SQL console (Developer role)
Read-only by design: `ConsoleGuard` allows one `SELECT/WITH/VALUES/TABLE/SHOW/EXPLAIN`, blocks writes, DDL, locks,
file/sleep functions, and `app_user`/`password_hash` (use the `app_user_public` view). It also runs in a read-only transaction
with a 5 s timeout and a 200-row cap. For defence in depth, point it at a read-only database role.
"Suggest" turns plain English into SQL (Claude if configured, otherwise templates); you always review before running.

## Structure
`common` errors + generic `StateMachine` · `security` session login, CSRF, lockout · `academic` terms, sections, result publishing ·
`student` / `faculty` / `admin` / `dev` feature packages · `ai` assistants · `audit` activity log · `static/` front end.
Seats use one atomic conditional UPDATE; term and mark-sheet lifecycles are explicit transition tables with compare-and-set persistence.

## Tests
`mvn test`. Unit tests cover the state machines and the console guard. Concurrency and database tests need a throwaway DB
(`UMS_TEST_DB_URL`) and are not included yet.
