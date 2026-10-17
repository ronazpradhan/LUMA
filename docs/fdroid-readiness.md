# F-Droid / IzzyOnDroid readiness

Status notes for getting LUMA into IzzyOnDroid (first) and main F-Droid (later).
Rules checked on 2026-10-18; they change, so re-read the linked pages before submitting.

## What the build now has

- Two flavors:
  - `full`: the normal build, with Google Cast. Same as before.
  - `foss`: no Google Play Services. The Cast code is a stub (`app/src/foss`), so the cast button never shows.
- Per-ABI release APKs plus a universal one:

  | File | What it is |
  |---|---|
  | `LUMA.apk` | full, all ABIs (same name as before, used by download links, Obtainium and the in-app updater) |
  | `LUMA-arm64-v8a.apk`, `LUMA-armeabi-v7a.apk`, `LUMA-x86_64.apk` | full, one ABI each |
  | `LUMA-foss.apk` | foss, all ABIs |
  | `LUMA-foss-arm64-v8a.apk`, `LUMA-foss-armeabi-v7a.apk`, `LUMA-foss-x86_64.apk` | foss, one ABI each |

  Sizes (1.3.3, measured 2026-10-18):

  | ABI | full | foss |
  |---|---|---|
  | universal | 158.5 MB | 156.7 MB |
  | arm64-v8a | 60.7 MB | 59.0 MB |
  | armeabi-v7a | 57.7 MB | 55.9 MB |
  | x86_64 | 62.1 MB | 60.3 MB |

  The x86 (32-bit) libraries are only in the universal APKs. Most of each APK is the Python and ffmpeg runtime from youtubedl-android.

  Build with `./gradlew assembleFullRelease assembleFossRelease`. Output: `app/build/outputs/apk/<flavor>/release/`.
  All APKs share one versionCode. The in-app updater looks for `LUMA.apk` (full) or `LUMA-foss.apk` (foss) in the latest release.

## Blockers for IzzyOnDroid

IzzyOnDroid takes the APKs you attach to GitHub releases and doesn't build from source. So prebuilt `.so` files are fine there, but these points still matter:

1. **Size.** The rule of thumb is about 30 MB per app, and each APK must be under it. Exceptions need a good reason. For a fat build that is too big, they pick one ABI, usually arm64. LUMA's universal APK (~158 MB) is far over, and even the arm64 foss APK is over (see sizes below). Ask for an exception in the request and explain that the size is the Python + ffmpeg runtime yt-dlp needs.
2. **Downloaded executables.** The app must not download executable code without explicit opt-in consent, and the consent must say it bypasses the repo's checks. LUMA updates yt-dlp from its nightly channel automatically (`YtDlpManager.doNightlyUpdate`). This needs to become opt-in, at least in the foss flavor, before submitting. The in-app update prompt only opens the APK link in the browser after a tap, which should be fine.
3. **AI-generated code.** The current inclusion policy says apps that are fully or partly generative-AI-created are strongly opposed, vibe-coded apps are rejected, and code must be free of LLM output. LUMA's changes were written with an AI assistant, so this may be the biggest blocker. Read that section yourself and decide before you file. Being open about it is better than having it found later.
4. **Metadata.** The repo needs Fastlane metadata: `fastlane/metadata/android/en-US/` with `short_description.txt`, `full_description.txt`, `images/icon.png` and `images/phoneScreenshots/`. LUMA has none yet.
5. **Fork rules.** Forks must credit the original (Stash by Rawnald Clark), look different (name + icon), and have their own screenshots and description. LUMA already does the first two.
6. **Non-free parts.** Proprietary libraries are a show-stopper unless essential. Submit the **foss** APK, not the full one.

## Submitting to IzzyOnDroid, step by step

1. Fix blockers 2 and 4 above. Decide about 3.
2. Make a release on GitHub with the foss APKs attached: `LUMA-foss-arm64-v8a.apk` (the one Izzy would pick) and the others. Sign them with the normal release key (never a debug key; `debuggable` APKs are refused).
3. Check LUMA isn't already listed: https://apt.izzysoft.de/fdroid/
4. Open an issue at https://codeberg.org/IzzyOnDroid/repodata/issues (needs a Codeberg account). Use the template it offers. Include:
   - repo URL (https://github.com/ronazpradhan/LUMA) and license (GPL-3.0-or-later; check the LICENSE file),
   - package name `com.luma.music`,
   - which APK to use (`LUMA-foss-arm64-v8a.apk`, plus maybe armeabi-v7a), and the size, with the reason for a size exception,
   - that it is a fork of Stash, and the anti-features below.
5. Answer the reviewer's questions on the issue. After it's added, new releases are picked up by a daily check, usually within 24 hours of attaching the APK.

Sources: [App Inclusion Policy](https://izzyondroid.org/docs/general/AppInclusionPolicy/), [FAQ](https://izzyondroid.org/faq/).

## Anti-features LUMA would carry

- **NonFreeNet**: relies on Spotify and YouTube / YouTube Music, which are non-free network services.
- **NonFreeDep** (full flavor only): Google Play Services for Cast. The foss flavor doesn't have it.
- Possibly **NonFreeAdd** / **Tracking**: no. LUMA has no ads or analytics. Say so in the request.

## Blockers for main F-Droid (bigger job)

F-Droid builds every APK itself from source, on its own servers, and refuses anything prebuilt or non-free.

1. **Committed prebuilt `.so` files.** F-Droid's scanner refuses them:
   - `app/src/main/jniLibs/*/libc++_shared.so`: the C++ runtime from the Android NDK. Don't commit it. Let the NDK supply it with `ANDROID_STL=c++_shared` in a native build (CMake), or let a native dependency that F-Droid builds pull it in.
   - `data/download/src/main/jniLibs/arm64-v8a/libqjs.so` (QuickJS): build it from source. Add QuickJS as a git submodule and an `externalNativeBuild { cmake { ... } }` block with a small `CMakeLists.txt`, so Gradle compiles it for every ABI. Only arm64 exists now, so other ABIs are missing it anyway.
2. **youtubedl-android prebuilts.** `io.github.junkfood02.youtubedl-android` (library, ffmpeg, aria2c) ships ready-made Python, ffmpeg and aria2c binaries zipped as `.so` files. F-Droid can't use those from Maven. Other apps (Seal, YTDLnis) got in by building these from source in F-Droid's build recipe (`srclibs` + build scripts), and that takes a lot of work. Ask on the F-Droid forum or GitLab how those apps do it today.
3. **yt-dlp auto-update.** Same issue as for IzzyOnDroid: downloading code at runtime. It must be off or opt-in in the F-Droid build.
4. **Google Play Services.** Solved: use the `foss` flavor (`gradle: [foss]` in the F-Droid build metadata).
5. **Dependencies must come from trusted repos** (Maven Central, Google's Maven): check `settings.gradle.kts` stays that way.
6. **In-app updater.** F-Droid builds usually turn off self-update prompts (F-Droid handles updates). Easy to do with a foss-only BuildConfig flag when needed.
7. **Submission.** Merge request to https://gitlab.com/fdroid/fdroiddata with a `metadata/com.luma.music.yml` build recipe. Do this after 1-3 are solved.
