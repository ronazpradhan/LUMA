<p align="center">
  <img src="assets/logo.svg" width="128" alt="LUMA logo: an ivory crescent with a lavender point">
</p>

<h1 align="center">LUMA</h1>

<p align="center">
  Spotify + YouTube Music on Android. Stream or download. No account, no ads.
</p>

<p align="center">
  <a href="https://github.com/ronazpradhan/LUMA/releases"><img src="https://img.shields.io/github/downloads/ronazpradhan/LUMA/total?label=downloads&color=b4a7f5" alt="Total downloads"></a>
  <a href="https://github.com/ronazpradhan/LUMA/releases/latest"><img src="https://img.shields.io/github/v/release/ronazpradhan/LUMA?label=latest&color=b4a7f5" alt="Latest release"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3ddc84?logo=android&logoColor=white" alt="Android 8.0+">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-blue" alt="GPL-3.0"></a>
</p>

<p align="center">
  <a href="https://github.com/ronazpradhan/LUMA/releases/latest/download/LUMA.apk"><b>⬇ Download LUMA.apk</b></a>
</p>

---

## Supported Android versions

| | |
|---|---|
| **Minimum** | Android 8.0 Oreo (API 26) |
| **Target** | Android 15 (API 35) |
| **Works on** | Android 8, 9, 10, 11, 12, 13, 14, 15 and newer |

Below Android 8.0 it won't install. Phones only; no Android TV or Wear OS build.

## Install

1. Download **[LUMA.apk](https://github.com/ronazpradhan/LUMA/releases/latest/download/LUMA.apk)** on your phone.
2. Open it. Allow installs from your browser if asked, then tap **Install**.
3. In LUMA: gear icon (top right of Home) → **Accounts** → connect Spotify and/or YouTube Music.
4. **Sync** → pick playlists → **Sync now**.

On 1.0 or 1.1? Uninstall it first. From 1.2.0 on, updates install over the top and the app tells you when one is out.

Not on the Play Store, and won't be: it logs in to Spotify and YouTube with your own account, which store rules don't allow.

## What you get

- Your YouTube Music home feed (Quick picks, Listen again, Mixed for you).
- Autoplay: the queue keeps going with similar songs when it runs out.
- Suggestions based on what you've been playing.
- Import any Spotify playlist by pasting its link.
- Auto-download Liked songs and/or playlists. Everything else streams.
- AAC and Opus audio.
- Synced, word-by-word lyrics.
- Glass UI with a player that picks up colours from the album art.

## Privacy

No LUMA account, no LUMA server, no analytics. Your logins are stored encrypted on your phone and only sent to the service they belong to.

Besides Spotify and YouTube, LUMA talks to:

- **Lyrics:** LRCLIB, iTunes Search + paxsenix's lyrics API, KuGou, YouTube Music.
- **MusicBrainz** (artist info) and **JioSaavn** (AAC fallback).
- **GitHub** (update checks for LUMA and yt-dlp).
- **Spotify's public embed page**, only when you import a playlist link.
- **ListenBrainz / Discord**, only if you connect them.
- **Stash share service**, only for shared links or Community.
- **Google Cast**, only when you open the speaker list.

Last.fm, Qobuz and the Stash lossless relay are never contacted. Full details of what each service receives: [Stash README](https://github.com/rawnaldclark/Stash#what-stash-talks-to).

## Build

```bash
git clone https://github.com/ronazpradhan/LUMA.git
cd LUMA
./gradlew assembleDebug   # APK lands in app/build/outputs/apk/debug/
```

Needs JDK 17 and Android SDK 35.

## Credits

LUMA is a fork of **[Stash](https://github.com/rawnaldclark/Stash)** by Rawnald Clark and contributors. The sync engine, player, downloads, lyrics and most of the rest is their work. Give Stash a ⭐. LUMA is not affiliated with Stash; report LUMA bugs here, not there.

**Changed from Stash:** new name, logo and package (`com.luma.music`, installs alongside Stash); YouTube Music home feed instead of Qobuz rows; autoplay on by default; auto-download switches; Spotify link import; listening-based suggestions; redesigned glass UI and player; faster song start; Qobuz/lossless removed; own release key and update popup; Stash donation prompts, supporter ticker and links removed; fixes for empty playlists in Download mode and Home tab navigation.

Also built on [yt-dlp](https://github.com/yt-dlp/yt-dlp), [youtubedl-android](https://github.com/JunkFood02/youtubedl-android), [QuickJS-NG](https://github.com/quickjs-ng/quickjs), [Media3 / ExoPlayer](https://github.com/androidx/media), [Haze](https://github.com/chrisbanes/haze), with [ytmusicapi](https://github.com/sigma67/ytmusicapi) and [Spicy Lyrics](https://github.com/Spikerko/spicy-lyrics) as references.

## Legal

Unofficial. **Not affiliated with Spotify, YouTube, Google, Qobuz, Apple, Discord, Last.fm, ListenBrainz, MusicBrainz, JioSaavn, KuGou, LRCLIB or any other service it connects to.** Trademarks belong to their owners.

You're responsible for following those services' terms. Downloading copyrighted music without a license may be illegal where you live.

## License

Copyright © 2026 Rawnald Clark (Stash)<br>
Copyright © 2026 Ronaz Pradhan (LUMA modifications)

[GPL-3.0](LICENSE), or any later version. No warranty.
