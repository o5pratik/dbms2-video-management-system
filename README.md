# Video Management System

For a public cloud deployment that works while this computer is off, see [DEPLOYMENT.md](DEPLOYMENT.md). The cloud version uses Render and Neon PostgreSQL; the local version continues to use Oracle XE.

A DBMS laboratory mini-project built with Java 17, Spring Boot, HTML, CSS, JavaScript, and a relational database. A fresh checkout uses SQLite through JDBC. On the current computer, `.env` selects the installed **Oracle Database 11g Express Edition** through Oracle JDBC. Oracle XE is free to use; this connection does not need the CData bridge or a CData product key.

## What it does

- Sign-up, session login with BCrypt password hashes, logout, and protection for all video, category, dashboard, and report APIs.
- Create, view, search, update, and delete videos. Changes are shown only after the API confirms success.
- Dashboard totals and recently added videos.
- Reports for all videos, videos grouped by category, category counts, most viewed, and a selected upload date range.
- Live database connection indicator in the sidebar. It shows the actual configured mode and reachability.
- One Spring Boot server serves both the frontend and REST API on port 8080 by default.

## Requirements

1. Windows with **JDK 17 or newer**. Verify `java -version` in a new terminal.
2. **Apache Maven 3.8+**. Verify `mvn -version`.
3. Internet access for Maven's first dependency download. After dependencies are cached, Maven can run offline.
4. VS Code and a modern browser are optional conveniences. The Oracle setup on this computer also has SQL*Plus.

## Run in one step

From the project folder, double-click `start.bat`, or run:

```bat
start.bat
```

With no `.env`, the first launch downloads dependencies, creates `database/videos.db`, creates the tables, inserts five categories and ten sample videos, and creates the demo user. On this computer, `.env` selects Oracle XE and port 8081. Open **[http://127.0.0.1:8081/](http://127.0.0.1:8081/)**.

| Login | Value |
|---|---|
| Username | `admin` |
| Password | `admin123` |

The password is hashed with BCrypt in `users.password_hash` when the database is first initialized. Change the demo password before using the project with real data.

Select **Create an account** on the sign-in page to choose your own website username and password. Usernames must be 3–32 letters, numbers, dots, underscores, or hyphens; passwords must be 8–72 characters. Registration signs you in immediately. All website accounts share the same video library. Website logins are stored in the `users` table and are separate from the Oracle `VMS_APP` database credentials in `.env`.

To use another port, create `.env` next to `start.bat`:

```ini
APP_PORT=8090
APP_HOST=127.0.0.1
```

Then open `http://127.0.0.1:8090/`. To allow another device to connect on a permitted local network, set `APP_HOST=0.0.0.0`, allow the port through Windows Firewall, and open `http://YOUR_COMPUTER_IP:8080/` from that device. Keep the demo credentials off public networks.

### Run from VS Code terminal

Start the terminal in the project root:

```powershell
New-Item -ItemType Directory -Force database | Out-Null
cd backend
mvn spring-boot:run
```

The SQLite path is relative to the **backend** working directory: `../database/videos.db`. `start.bat` sets the same working directory. If you run the packaged JAR from another directory, set an absolute `DB_URL`, for example `jdbc:sqlite:C:/lab/VideoManagement/database/videos.db`.

To build and test:

```powershell
cd backend
mvn clean test
mvn package
```

The JAR is `backend/target/video-management-system-1.0.0.jar`. Run it from `backend` with `java -jar target/video-management-system-1.0.0.jar`.

## Database and connectivity

```text
Browser (HTML/CSS/JavaScript)
  │ same-origin fetch, session cookie + CSRF token
  ▼
Spring Boot REST controllers
  ▼
VideoRepository / JdbcTemplate (prepared statements)
  ▼
HikariCP DataSource → JDBC driver → Oracle XE or SQLite
```

**Actual default mode:** `JDBC_FALLBACK`, using Xerial SQLite JDBC (`org.sqlite.JDBC`). The URL, driver, username, password, mode, and DSN are configured with environment variables; see `.env.example` and `backend/src/main/resources/application.properties`. Connections are pooled and closed by Spring/JdbcTemplate. Explicit connection usage in initialization and the status endpoint uses try-with-resources. SQLite foreign keys are enabled for every pooled connection.

### Oracle XE and Run SQL Command Line

The current machine runs Oracle XE at `127.0.0.1:1522/XE`. The app uses a dedicated `VMS_APP` account; `.env` holds its generated password and is ignored by Git. The existing SQLite data was copied into Oracle: 1 user, 5 categories, and 11 videos. The original SQLite file remains available as a backup. The Oracle schema is in `database/schema-oracle.sql`, and the one-time copy utility is `scripts/OracleMigration.java`.

Double-click `oracle-sqlplus.bat` to open the same SQL*Plus program as **Run SQL Command Line**, configured for this project. Enter the `DB_PASSWORD` value from `.env` at its hidden password prompt. Then try:

```sql
SELECT COUNT(*) FROM videos;
SELECT video_id, title FROM videos ORDER BY video_id;
EXIT
```

The application uses direct Oracle JDBC; SQL*Plus is a separate command-line client for inspecting and running SQL against the same tables. The launcher keeps its Oracle Net setting local to that command window. To restart the website, run `start.bat`; check `/api/database/status` for `Oracle JDBC` and `reachable: true`.

The SQLite schema is in `database/schema-sqlite.sql`; sample rows are in `database/seed-sqlite.sql`. On first launch, Spring executes equivalent copies packaged in `backend/src/main/resources/database/`. The database file is ignored by Git so personal changes stay local. Deleting `database/videos.db` while the server is stopped resets the demo data on the next launch.

### Optional ODBC mode for Microsoft Access

Java 17 has **no built-in JDBC–ODBC bridge**. The project uses a separate vendor bridge JAR for ODBC mode. A direct Access JDBC driver such as UCanAccess does not use ODBC. The Windows machine used for the setup has the 64-bit Microsoft Access ODBC driver. It was tested with a DSN-less connection to `database/videos-odbc.accdb`, and `scripts/migrate_to_access.py` copied the existing SQLite rows through ODBC. The **Java application's ODBC mode is not active** because CData requires a product license. Oracle XE is the working free database connection.

1. Create an Access `.accdb` file and run the statements in `database/schema-access.sql`, one at a time. The current machine's `database/videos-odbc.accdb` already has this schema and a snapshot of the existing data. That binary file is local and ignored by Git.
2. To copy an empty Access database from SQLite, install `pyodbc` locally with `py -3 -m pip install --target .tools/python pyodbc`, then run `py -3 scripts/migrate_to_access.py`. The script refuses to overwrite an Access database containing records. Run migration immediately before switching databases so recent SQLite edits are included.
3. Obtain a supported JDBC–ODBC bridge from its vendor. [CData's bridge](https://www.cdata.com/drivers/bridge/download/jdbc/) supports a DSN-less Access connection, but its trial download asks for an email address and acceptance of its [EULA](https://www.cdata.com/company/legal/eula/). Its [driver documentation](https://cdn.cdata.com/help/EJM/jdbc/pg_connectionj.htm) gives the class `cdata.jdbc.jdbcodbc.JDBCODBCDriver` and URL syntax. Other vendors may require a DSN or a local bridge server.
4. Put the licensed bridge JAR at `drivers/jdbc-odbc-bridge.jar`. Install it into the local Maven repository from `backend` with `mvn -Dmaven.repo.local=../.tools/m2 install:install-file -Dfile=../drivers/jdbc-odbc-bridge.jar -DgroupId=local.vendor -DartifactId=jdbc-odbc-bridge -Dversion=1.0 -Dpackaging=jar`. The `external-odbc-bridge` Maven profile then loads it. Follow the vendor's license file and native library instructions.
5. For CData with the Microsoft Access ODBC driver, set the following in `.env`, replacing the path with the absolute path to the Access file:

   ```ini
   DB_MODE=ODBC
   DB_DRIVER=cdata.jdbc.jdbcodbc.JDBCODBCDriver
   DB_URL=jdbc:cdata:jdbcodbc:Driver={Microsoft Access Driver (*.mdb, *.accdb)};DBQ=C:\absolute\path\to\database\videos-odbc.accdb
   DB_DSN=
   ```

   A vendor that uses a named DSN can instead put `{dsn}` in `DB_URL` and set `DB_DSN` to its name. The application substitutes `{dsn}` in the URL.
6. Restart with `start.bat`. Check `/api/database/status`, login, CRUD, and reports before treating the ODBC setup as complete. `ODBC Bridge` in the UI identifies the selected mode; the status endpoint checks reachability separately.

## REST endpoints

| Method | Path | Purpose |
|---|---|
| POST | `/api/auth/login` | Create a session and return CSRF token |
| POST | `/api/auth/register` | Create a website account and session |
| GET | `/api/auth/me` | Restore the current session |
| POST | `/api/auth/logout` | End the session |
| GET | `/api/videos` | All video records |
| GET | `/api/videos/{id}` | One video |
| POST | `/api/videos` | Create a video |
| PUT | `/api/videos/{id}` | Update a video |
| DELETE | `/api/videos/{id}` | Delete a video |
| GET | `/api/videos/search?field=title&q=sql` | Search by `id`, `title`, `category`, or `creator` |
| GET | `/api/categories` | Categories |
| GET | `/api/dashboard/stats` | Dashboard totals and recent videos |
| GET | `/api/reports?from=YYYY-MM-DD&to=YYYY-MM-DD` | Report data |
| GET | `/api/database/status` | Actual database mode and reachability |

All endpoints except login and database status require a session. Mutating requests also require the `X-CSRF-Token` header returned by login. The browser UI supplies it automatically. Errors return JSON, for example `{"error":"Video not found."}`. HTTP status codes include 201 for creation, 400 for validation, 401 for no session or bad login, 403 for bad CSRF token, 404 for a missing video, and 503 for database failures.

## SQL lab reference

See `database/lab-queries.sql` for SELECT, INSERT, UPDATE, DELETE, WHERE, ORDER BY, GROUP BY, COUNT, and INNER JOIN examples. The application executes equivalent prepared SQL statements in its repository and report controller. User search fields are selected from a fixed allowlist before SQL is assembled.

## Project layout

```text
frontend/                         Responsive website, served by Spring Boot
backend/pom.xml                   Maven dependencies and frontend packaging
backend/src/main/java/           API, session auth, database config and queries
backend/src/main/resources/       App settings and packaged SQL initialization
backend/src/test/                 End-to-end API integration test
database/                         Schema, seed data, SQL lab queries, SQLite file
start.bat                         Windows launcher
oracle-sqlplus.bat                Opens the Oracle app schema in SQL*Plus
.env.example                      Configuration template
```

## Troubleshooting

- **`java` or `mvn` is not recognized:** Install JDK 17+ and Maven, add their `bin` directories to PATH, then open a new terminal.
- **Port 8080 occupied:** Put `APP_PORT=8090` in `.env` and restart.
- **Database unreachable:** Check the sidebar status and `GET /api/database/status`. For SQLite, ensure `database/` exists and `DB_URL` points to a writable location. For ODBC, verify the DSN, driver JAR, URL, bitness, and vendor DLL setup.
- **Login fails after changing database:** The demo user is seeded when the new database has an empty `users` table. Ensure the schema was created and initialization completed, or insert a BCrypt user hash manually.
- **Network access fails:** Use `127.0.0.1` on the same computer. For another device, bind to `0.0.0.0` and check firewall and lab network rules.

## Verification

Run `mvn clean test` in `backend`. The integration test checks initial sample count, database mode, valid/invalid login, blocked unauthenticated access, CSRF protection, video creation, retrieval, search, update, reports, deletion, and logout. JavaScript syntax can be checked with `node --check frontend/app.js`. A test is only considered passed after it has actually run on the target machine.
