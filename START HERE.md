# Fitness Tracker

A native Android Kotlin application, PHP JSON web service, and MySQL database for the Mobile App Development assignment.

## Contents

- `app/` — Android screens, XML resources, object-oriented models, networking, offline storage, and unit tests.
- `backend/` — database schema, PHP API, example configuration, and repeatable API tests.
- `docs/` — requirements mapping, design specification, testing notes, and screenshot checklist.
- `docs/Appendix A Source Code.html` — a readable, printable source listing. Open it in a browser, or open/import it in Word to add it to your report. The Markdown version contains the same code. Your original written task answers have not been edited.
- `gradlew.bat`, `gradlew`, `gradle/` — the Gradle wrapper for reproducible builds.

## Start the backend on Windows

**Convenient local option on this PC:** MySQL Server 8.0 is already installed. Double-click **START BACKEND.cmd**, or open PowerShell in this folder and run `powershell -NoProfile -ExecutionPolicy Bypass -File .\start-local-backend.ps1`. The script downloads and verifies a portable PHP runtime, creates an isolated database under `.runtime` on port 3308, creates random local credentials, and starts the API on port 8088. It does not replace the existing MySQL service or its data. The execution-policy option applies only to that PowerShell process. Keep the terminal open while using the app. The first download needs an internet connection. Do not submit `.runtime` or `backend/config.local.php`.

Alternatively, configure your own PHP/MySQL installation:

1. Install PHP 8.2 or later with the `pdo_mysql` extension and MySQL 8.0.16 or later. The database requires MySQL, not SQLite. `phpMyAdmin` is optional.
2. Import `backend/fitness_db.sql` with MySQL Workbench or the MySQL command-line client. It creates `fitness_db` without dropping existing databases or tables.
3. Create a dedicated database account. Run the following as a MySQL administrator, replacing the example password with your own:

```sql
CREATE USER 'fitness_app'@'127.0.0.1' IDENTIFIED BY 'YOUR_STRONG_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE ON fitness_db.* TO 'fitness_app'@'127.0.0.1';
```

4. Copy `backend/config.example.php` to `backend/config.local.php` and enter your MySQL host, port, account and password. The normal MySQL port is 3306. Never put these credentials in the Android app.
5. Open a terminal in the project folder and run:

```powershell
php -S 127.0.0.1:8088 -t backend/public
```

6. Open `http://127.0.0.1:8088/index.php?action=health`. It should return `{"status":"ok","service":"Fitness Tracker"}`.

Only `backend/public` is the web root. The configuration and SQL remain outside the publicly served directory. PHP's built-in server is for local assessment/testing. For hosting, use HTTPS with a configured web server and add operational protections such as login rate limiting, monitoring, and backups.

## Open the Android project

1. In Android Studio select **Open** and select the project folder containing `settings.gradle.kts`.
2. Choose JDK 17 for Gradle under **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK**. Use the Download JDK option if Java 17 is not installed. Install Android SDK Platform 35 and Build Tools 36.0.0 through SDK Manager. Gradle 8.11.1, Android Gradle Plugin 8.9.2 and Kotlin 2.1.20 are pinned for this project. These are deliberate compatible versions, not a claim that they are the newest versions available.
3. Allow Gradle Sync to download dependencies.
4. Run the `app` configuration on an Android device or emulator (Android 7.0 / API 24 or newer).
5. On the sign-in screen, open **Server connection**. For the standard Android emulator, keep `http://10.0.2.2:8088`.
6. Select **New here? Create an account** and register. There is no hardcoded account or password in the application.

For a USB-connected phone, enable USB debugging, authorize the computer, then run:

```powershell
adb reverse tcp:8088 tcp:8088
```

Set the app's server to `http://127.0.0.1:8088`. Keep the backend running. For a remotely hosted API use its HTTPS base URL. HTTP is permitted only for the explicit loopback/emulator hosts in the debug build; release builds require HTTPS.

## Use the app

- Record a run, cycle, walk or weightlifting session using the **+** button. Activity-specific fields appear automatically.
- Enter duration and date. Enter distance for running/cycling/walking, or weight, sets and repetitions for weightlifting.
- **Attach current location** requests permission and saves coordinates with the workout. Approximate location is supported. Declining permission does not prevent recording. This feature attaches a location; it does not record a live route or infer distance.
- Tap an existing workout to inspect, edit or delete it. Deleting requires confirmation.
- Open **Goals** and use **+** to set an activity-minute target and end date. The start date is today. Progress is calculated from matching workouts.
- Open **Progress** for daily minutes, a comparison with the previous seven days, and activity-specific statistics.
- New workouts are saved locally before upload. Pending entries are visible and retried on returning to the dashboard, reconnecting while it is open, or tapping **Sync**. The server's unique client ID prevents duplicate inserts after a network timeout.
- Registration, sign-in, editing, deletion and goal changes require a server connection. Offline recording works after sign-in. Synchronization does not run when the app is closed.

## Build and test

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
python backend/tests/smoke.py http://127.0.0.1:8088
```

The backend smoke test creates disposable test accounts and workouts. Run it against a test database, not a production database. The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

See `docs/TESTING.md` for the checks actually performed and the remaining device checks. A source listing does not replace the complete runnable project. Screenshots should be taken from the running application using `docs/SCREENSHOTS.md`.

## Submission

Submit the Android source project, PHP backend, SQL schema, setup instructions and your written report. Keep private credentials and signing keys out of the submitted files. Explain the code and show the three inheritance examples in `docs/OOP.md`. Check your centre's appendix word-count policy; the supplied question paper does not explicitly give a blanket exemption for all code appendices.
