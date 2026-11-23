# Real app evidence to capture

Device screenshots have not been supplied as completed evidence. Capture them after running the application on an emulator or an Android phone. Do not use design mockups as execution evidence.

1. Sign-in screen and registration screen.
2. History dashboard containing running, cycling, weightlifting and walking entries, with the RecyclerView and floating + button visible.
3. Add-workout form with distance fields, then the weightlifting form showing weight, sets and repetitions.
4. Location permission prompt and a saved workout displaying its attached coordinates. Test permission denial as well.
5. Goals tab with a target and progress derived from matching workouts.
6. Progress tab with daily totals and activity statistics.
7. Offline pending workout and the same workout marked saved after reconnecting. Verify there is only one corresponding MySQL row.
8. MySQL tables and a SELECT query showing the saved workout and foreign-key relationship. Hide passwords, tokens and configuration secrets.

Suggested captions identify the screen, action, expected result and observed result. Save the PNG files in an `evidence` folder and insert selected images into your report under Task 4 or an evidence appendix, following your centre's presentation requirements.

For an emulator use its screenshot button. For a phone use its hardware screenshot shortcut or ADB. An ADB capture can be saved with:

```powershell
adb shell screencap -p /sdcard/fitness-evidence.png
adb pull /sdcard/fitness-evidence.png evidence/fitness-evidence.png
```

Before capture, create your own demonstration account and realistic sample workouts. Never include real passwords or bearer tokens in evidence.
