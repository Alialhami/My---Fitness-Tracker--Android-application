# Verification record

## Backend checks completed

PHP 8.4.27 with MySQL 8.0.43 was used for the local tests. The test script completed all 25 checks. The same checks also passed against a fresh database created by the supplied Windows launcher on separate test ports.

| Area | Verified result |
|---|---|
| Database connectivity | Health endpoint connects to MySQL |
| Registration | Account created, token returned, duplicate email rejected |
| Login | Valid password accepted, incorrect password rejected |
| Authentication | Missing token and revoked token rejected |
| Offline retries | Repeated UUID returns the same workout ID without duplicating the row |
| Account isolation | Other account cannot read, update or delete the first account's workout |
| Validation | Zero/fractional duration, invalid coordinates, date and activity rejected |
| Workout updates | Owner can edit and delete saved workouts |
| Strength data | Weight, sets and repetitions accepted for weightlifting |
| Goals | Progress reflects stored workout minutes; invalid date ranges rejected |
| Goal ownership | Another account cannot delete the goal |
| Logout | Session token is revoked |

The repeatable command is `python backend/tests/smoke.py http://127.0.0.1:8088`. Full test names are printed by the script. PHP syntax checks passed for the API and shared bootstrap.

## Android checks

The final build result is recorded in the delivered verification summary. The included JVM tests check running pace, cycling speed, lifting volume, zero-distance handling and polymorphic activity statistics.

## Device acceptance checks still required

The supplied emulator could not be started successfully in this environment. No device screenshots or completed device test results are claimed. Before submission, run the APK on an emulator or phone and record actual results for the following:

| Test | Expected result |
|---|---|
| Register, sign out, sign in | User reaches their own dashboard; wrong credentials show an error |
| Add all four activities | Correct fields are displayed and records persist after restarting |
| Edit and delete | Changes appear in history and goal progress; deletion asks for confirmation |
| Deny location | App remains usable; workout can be saved without coordinates |
| Grant approximate/precise location | Coordinates appear in details and are stored in MySQL |
| Location disabled/unavailable | Helpful message appears without a crash |
| Offline recording | New workout displays Pending sync and remains after app restart |
| Reconnect and retry | Exactly one database row appears for each queued workout |
| Change account | Previous account's cached and pending records are not displayed |
| Goal dates and progress | Only the matching activity inside the inclusive range counts |
| Rotate the workout form during input | Entered fields, selected activity/date and attached location are retained |
| Small screen and larger font | Inputs remain reachable, labels remain readable and keyboard does not cover actions |
| Backend unavailable | App gives feedback and retains cached history/pending workouts |
| Navigation | Back, tabs, dialogs and sign out behave consistently |

The XML layout and native screens require this final visual check on a real Android runtime. Local database/API tests do not establish GPS behaviour, touch usability or device compatibility.
