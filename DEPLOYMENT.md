# Free cloud deployment

This project can run on a Render Free web service with a Neon Free PostgreSQL database. The public `onrender.com` URL works while the owner's computer is off. Render spins down an idle Free service, so the next visitor may wait about a minute for it to start. PostgreSQL data is stored separately and survives web service restarts.

## 1. Create a Neon database

Sign in to [Neon](https://console.neon.tech/) and create a Free project named `video-management-system`. Keep its connection details private. In Neon **Connection Details**, use the direct connection (or a pooled connection if Neon recommends it for your region). Record the host, database name, user, and password.

The JDBC connection URL has this form:

```text
jdbc:postgresql://HOST/DATABASE?sslmode=require
```

Do not put the connection password into GitHub or a URL shared with others.

## 2. Copy the local Oracle records (optional)

The cloud database can start with the ten sample videos. To copy the current Oracle categories and videos, create `.env.neon.local` from `.env.neon.example` and fill in the Neon values. Keep it on this computer; it is ignored by Git. Run `scripts/MigrateOracleToPostgres.java` from the project root with the locally installed Oracle and PostgreSQL JDBC JARs on the classpath. The program creates the cloud schema, refuses to overwrite a database that already contains rows, copies categories and videos, and checks their counts. It does not copy website accounts or passwords.

Example PowerShell command after filling `.env.neon.local`:

```powershell
$jdkLine = Get-Content .env | Where-Object { $_ -match '^JAVA_HOME=' } | Select-Object -First 1
$jdkPath = $jdkLine.Substring(10)
$oracleJar = (Get-ChildItem '.tools/m2/local/vendor/oracle-jdbc/1.0' -Filter '*.jar' | Select-Object -First 1).FullName
$postgresJar = (Get-ChildItem '.tools/m2/org/postgresql/postgresql' -Recurse -Filter 'postgresql-*.jar' | Select-Object -First 1).FullName
& "$jdkPath/bin/java.exe" -cp "$oracleJar;$postgresJar" scripts/MigrateOracleToPostgres.java
```

## 3. Deploy the web service

Push this project to a GitHub repository. In [Render](https://dashboard.render.com/), create a new **Blueprint** from that repository. Render reads `render.yaml`, builds `Dockerfile`, and prompts for the three secret environment variables below. Enter the same Neon values you used in step 1:

| Variable | Value |
|---|---|
| `DB_URL` | `jdbc:postgresql://HOST/DATABASE?sslmode=require` |
| `DB_USER` | Neon database user |
| `DB_PASSWORD` | Neon database password |

Select Render's **Free** plan. The Blueprint sets `APP_HOST=0.0.0.0`, `DB_MODE=POSTGRES`, HTTPS-only session cookies, and disables the known `admin/admin123` demo account. After deployment reports **Live**, open the service's `https://...onrender.com/` URL. Check `/api/database/status` for `"reachable":true` and `"mode":"PostgreSQL JDBC"`.

Use **Create an account** to register the first cloud website login. Accounts in the local Oracle database do not transfer. Every registered website user can add, edit, and delete videos in the shared cloud library, so share the URL with people you trust. An idle Render Free instance may require users to log in again after it wakes.

## Local mode

`start.bat` still uses the local `.env` and Oracle XE. The cloud database is separate; edits to one database do not automatically appear in the other.
