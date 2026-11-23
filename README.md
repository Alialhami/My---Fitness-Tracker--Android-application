# Fitness Tracker

A native Android fitness tracking app built with Kotlin, XML layouts, a PHP JSON API, and MySQL.

## Features

- Register and sign in to a personal account.
- Record running, cycling, walking, and weightlifting workouts.
- View workout history, activity statistics, goals, and progress.
- Optionally attach a location to a workout.
- Save new workouts offline and sync them when the app reconnects.

## Project contents

- `app/` — Android application source code.
- `backend/` — PHP API, MySQL schema, and backend checks.
- `docs/` — design, testing, and source-code documentation.
- `apk/` — debug APK for installation and testing.

## Setup

Read [`START HERE.md`](START%20HERE.md) for Android Studio setup, backend configuration, and run instructions.

The app requires a separately running PHP service and MySQL database. The GitHub repository stores the project files; it does not host the backend.

## Security

Do not commit `backend/config.local.php`, `.runtime/`, `local.properties`, passwords, or tokens. Use `backend/config.example.php` as the configuration template.
