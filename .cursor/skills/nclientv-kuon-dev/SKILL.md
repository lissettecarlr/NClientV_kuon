---
name: nclientv-kuon-dev
description: Build, install, debug on the phone, commit and push the NClientV_kuon Android app (fork of NClientV3). Use when building or testing the app, installing the APK on the connected device, reading logs or the UI via adb, editing the app database on the device, creating feature branches, merging into develop, pushing develop/main, or updating the Desktop APK.
---

# NClientV_kuon: dev workflow

Windows + PowerShell, repo root `f:\code\NClientV_kuon`. Java Android app, flavor `post28`, debug package `com.maxwai.nclientv3.debug` (package name is kept upstream's on purpose, do not rename).
Reply to the user in Chinese, terse.

## Build

```powershell
powershell -File .cursor/skills/nclientv-kuon-dev/scripts/build.ps1
```

Runs `./gradlew.bat assemblePost28Debug --console=plain --rerun-tasks -x lint` (~15 s warm, ~3 min with a cold Gradle daemon; use a long `block_until_ms`) and checks the APK is ~20 MB.
There is no system JDK: the script sets `JAVA_HOME=E:\Android studio\jbr` and `ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk` itself. For other gradle commands set them the same way first. The root `keystore.properties` (dummy) and `local.properties` must exist (both untracked).
Output: `app/build/outputs/apk/post28/debug/NClientV_kuon_<version>_debug.apk`.
Never trust a quiet/incremental build run in the sandbox: it once produced a 3 MB APK without `classes.dex` that fails with `INSTALL_FAILED_INVALID_APK ... code is missing`.

## Install and test on the phone (Huawei M6, Android 10)

`adb` is not on PATH: `$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"`.

```powershell
powershell -File .cursor/skills/nclientv-kuon-dev/scripts/install.ps1   # install + launch
powershell -File .cursor/skills/nclientv-kuon-dev/scripts/ui.ps1        # list texts + bounds on screen
```

- Huawei shows a "继续安装" screen: `install.ps1` taps (800,2428) **once** when it sees it. A second blind tap hits cancel (`User rejected permissions`).
- Screenshots are black (FLAG_SECURE). Use `ui.ps1`, then `adb shell input tap X Y` (center of bounds). Long press: `adb shell input swipe X Y X Y 800`.
- Known coordinates (1600x2560): drawer button `(84,156)`, toolbar overflow `(1540,156)`, "管理书签" in the drawer `(420,1416)`, previous page `(177,2490)`.
- The IME is Baidu: `adb shell input text` types pinyin, not Chinese.
- Logs: tag `NCLIENTLOG`. `adb logcat -c` then reproduce, then `adb logcat -d | Select-String 'MinPagesFilter|BookmarkSeeker'`. Add `LogUtility.d(...)` where needed.
- The server rate limits bursts (HTTP 429): many quick requests make later page fills come back short.
- Be careful with the user's real data on the phone (bookmarks, favorites, status): do not delete them while testing, cancel dialogs.
- `adb` commands may be blocked by auto-review; retry the same call with `request_smart_mode_approval` (the user authorized device testing).

### Testing foldable / odd screen sizes (Xiaomi 18 Fold)

Real screen data of the Xiaomi 18 Fold (measured on a real unit that is no longer available): `devices/xiaomi-18-fold.md` (outer 425x623 dp `nrml`, inner 608x860 dp `lrg`, density 440). Check the file before asking for the device again.
`fold.ps1 <profile>` emulates those screens with `wm size` + `wm density` (same dp; px scaled to fit the tablet 1600x2560). Switching profiles while the app runs = fold/unfold event (activity must not restart; check `Main started with mode` in logcat).
Profiles: `phone`, `mi-outer`, `mi-inner-port`, `mi-inner-land` (tablet), `real-outer` (only on the real Xiaomi). iPhone Duo is iOS: not supported. **Always finish with `fold.ps1 reset`** and check `wm size` shows only `Physical`.
Screenshots need FLAG_SECURE off: pref `hide_multitasking` = false in `Settings.xml` (default is true). Save the original first (`cmd /c "adb exec-out run-as ... cat shared_prefs/Settings.xml > %TEMP%\Settings.orig.xml"`) and restore it when done. Then `scripts/shot.ps1 -Out %TEMP%\a.png` (picks the display that is ON, scales down; plain `screencap` is corrupt on two-display phones and 2.5 MB PNGs can not be read) and read the PNG.
Window-size dependent code: `WindowColumns.adjust` (columns follow the window width), `Global.initScreenSize` (refreshed in `GeneralActivity.onConfigurationChanged`). GalleryActivity has no `configChanges` on purpose (restarts on fold).

### Editing the app database or prefs on the device

`run-as com.maxwai.nclientv3.debug` works (debug build). Binary files must not go through PowerShell `>`:

```powershell
cmd /c "`"$adb`" exec-out run-as com.maxwai.nclientv3.debug cat databases/Entries.db > %TEMP%\Entries.db"   # pull
# edit with python sqlite3, then:
& $adb push $env:TEMP\Entries.db /data/local/tmp/Entries.db
& $adb shell "run-as com.maxwai.nclientv3.debug cp /data/local/tmp/Entries.db databases/Entries.db; run-as com.maxwai.nclientv3.debug rm -f databases/Entries.db-wal databases/Entries.db-shm databases/Entries.db-journal"
```

Force-stop the app before pushing. Prefs live in `shared_prefs/Settings.xml` (write a clean XML with the Write tool and `adb push`, do not use sed/python quoting tricks). Lock-related pref: `has_credentials`.

## Code conventions (fork isolation)

Goal: keep merging upstream `maxwai/NClientV3` easy.
- New logic goes in new files (e.g. `api/MinPagesFilter.java`, `api/BookmarkSeeker.java`, `components/activities/AppLock.java`).
- Upstream files only get small hook lines marked `// [FORK]`.
- Fork strings: `res/values/strings_custom.xml` and `res/values-zh-rCN/strings_custom.xml`.
- Upstream files use CRLF: edit with the StrReplace tool, not multi-line PowerShell `.Replace`.
- Do not change: package name `com.maxwai.nclientv3`, `LICENSE`.
- DB schema changes need a `DATABASE_VERSION` bump in `DatabaseHelper` plus a guarded migration.

## Git workflow

Branches: `develop` (working base, does not need to compile on CI) and `main` (pushing it triggers GitHub Actions `android.yml`, which compiles; so only push `main` when it builds). Remote `origin` = `git@github.com:lissettecarlr/NClientV_kuon.git`. There is no `upstream` remote yet (`https://github.com/maxwai/NClientV3.git`).

1. `git checkout develop; git checkout -b feature/xxx` (or `fix/xxx`).
2. Implement, build, test on the phone.
3. Commit. Write the message to a file with the Write tool, then `git commit -F "$env:TEMP\msg.txt"` (PowerShell mangles `-F -` here-strings).
4. `git checkout develop; git merge --no-ff feature/xxx -m "Merge feature/xxx into develop"; git branch -d feature/xxx`.
5. Push only when the user asks: `git push origin develop`. Publishing: develop -> `main` (confirm first, it triggers CI).

Untracked `build.log` in the root is scratch, never commit it. Stage with `git add app` (or explicit paths).

## Update the Desktop APK

After building from `develop` content:

```powershell
Remove-Item "$env:USERPROFILE\Desktop\NClientV3_kuon_debug.apk" -ErrorAction SilentlyContinue
Copy-Item app\build\outputs\apk\post28\debug\*.apk "$env:USERPROFILE\Desktop\NClientV_kuon_debug.apk" -Force
```

## Feature map (where things live)

- Min page / blocked-tag (yaoi, males only) filtering, paging cursors: `api/MinPagesFilter.java`, `api/BlockedTagFilter.java`, hook in `InspectorV3.doSearchV2`.
- Tag search suggestions + Chinese translation: `utility/TagSuggest.java`, `TagTranslator`, `SearchActivity`.
- Completed status auto-marking and list color refresh: `components/status/CompletedSync.java`.
- PIN/biometric lock via deep links: `components/activities/AppLock.java`, hooks in `GeneralActivity` and `PINActivity`.
- Bookmark anchor (jump to the page where the first gallery of the bookmarked page is now): `api/BookmarkSeeker.java`, `Bookmark.anchor`, DB column `anchor` (DB v14), `BookmarkAdapter` (long press = delete menu).
- Update check points to the fork's GitHub releases: `async/VersionChecker.java`.
