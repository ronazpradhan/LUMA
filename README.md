<p align="center">
  <img src="assets/logo.svg" width="128" alt="LUMA logo: an ivory crescent with a lavender point">
</p>

<h1 align="center">LUMA</h1>

<p align="center">
  <b>Your Spotify + YouTube Music library, on your phone, your way.</b><br>
  Stream it, download it, play it offline. No account, no ads.
</p>

<p align="center">
  <a href="https://github.com/ronazpradhan/LUMA/releases/latest/download/LUMA.apk"><b>⬇ Download LUMA.apk</b></a>
  &nbsp;·&nbsp;
  <a href="https://github.com/ronazpradhan/LUMA/releases">All releases</a>
  &nbsp;·&nbsp;
  <a href="LICENSE">GPL-3.0</a>
</p>

---

## What it does

LUMA mirrors your Spotify and YouTube Music libraries to your Android phone. Connect a service, pick what you want, and LUMA streams it or saves it for offline playback.

- **Your YouTube Music home, in the app.** Quick picks, Listen again, Mixed for you and the rest of your YouTube Music feed sit right on Home.
- **The music never stops.** When your queue ends, LUMA keeps going with similar songs, the way YouTube Music fills its Up next. Tap any song in the feed to start a radio from it.
- **Suggestions from what you hear.** A "Suggested for you" row on Home and in Search, built from your recent plays. Pull down to refresh for a new set.
- **Bring your Spotify playlists.** In Library, tap + and paste a Spotify playlist link: LUMA finds every song and saves the playlist, ready to download in one tap.
- **Download what you choose.** One switch to auto-download your YouTube Music Liked songs, another for your playlists. Everything else streams.
- **Good sound.** High-quality AAC and Opus audio.
- **Live lyrics.** Scroll down in the player for synced, word-by-word lyrics.
- **A glass interface.** Frosted glass everywhere, a player that takes its colours from each album cover, and quick, fluid motion between screens.
- **Private by design.** No LUMA account and no analytics. Your sign-ins stay on your phone, encrypted, and are only ever sent back to the service they came from.

## Download

1. On your Android phone, tap **[Download LUMA.apk](https://github.com/ronazpradhan/LUMA/releases/latest/download/LUMA.apk)**.
2. Open the file. If Android asks, allow installs from your browser, then go back and tap **Install**.
3. Open LUMA, tap the gear at the top right of Home, and connect Spotify and/or YouTube Music under **Accounts**.
4. Go to **Sync**, choose your playlists, and tap **Sync now**.

> **Coming from LUMA 1.0 or 1.1?** Those were test builds. Uninstall the old LUMA once, then install this one. From 1.2.0 on, every update installs over the last, and LUMA tells you with a popup when a new version is out.

**Requirements:** Android 8.0 or later. A Spotify and/or YouTube Music account. For downloads, room for your music (a four-minute song is roughly 4 to 10 MB).

> LUMA is not on the Play Store. It signs in to Spotify and YouTube with your own login, which store policies don't allow, so it's installed straight from this page.

## Build from source

```bash
git clone https://github.com/ronazpradhan/LUMA.git
cd LUMA
./gradlew assembleDebug
# The APK is in app/build/outputs/apk/debug/
```

You need JDK 17 and the Android SDK 35 (Android Studio bundles both).

## What LUMA connects to

LUMA has no server of its own. Besides Spotify and YouTube / YouTube Music (sign-in, library sync, search, your home feed, song radio and the audio itself), it talks to:

- **Lyrics services:** LRCLIB, iTunes Search with paxsenix's lyrics API, KuGou and YouTube Music.
- **ListenBrainz and Discord**, only if you connect them.
- **Spotify's public embed page** (`open.spotify.com`), only when you import a playlist link, to read the playlist's name and songs.
- **Last.fm** isn't available in this build: it ships without a Last.fm API key, so there is nothing to connect and no Last.fm lookups.
- **MusicBrainz**, for artist details, and **JioSaavn**, as the AAC fallback.
- **GitHub**, to check for new LUMA versions each time the app opens, and for yt-dlp updates.
- **The Stash project's share service**, only when you open a shared mix or song link, or turn on Community.
- **Google Cast**, only when you open the speaker list.

For exactly what each one is sent, see the detailed list in the [Stash README](https://github.com/rawnaldclark/Stash#what-stash-talks-to). LUMA sends the same to the services above, minus everything Qobuz and lossless (LUMA never contacts Qobuz or the Stash lossless relay), the Stash update check, the supporters list and Last.fm, and plus your YouTube Music home feed, song radio and suggestions.

---

## Built on Stash ❤️

LUMA is built on **[Stash](https://github.com/rawnaldclark/Stash)** by **Rawnald Clark** and its contributors, a brilliant open-source music app. Almost everything that makes LUMA work is their code: the sync engine, the player, streaming and downloads, lyrics and much more. Huge thanks to them. If you like LUMA, go give Stash a ⭐.

Stash and LUMA are separate projects. LUMA isn't endorsed by or affiliated with Stash, and problems with LUMA belong here, not with them.

### What LUMA changes (2026)

- Its own name, logo and package (`com.luma.music`), so it installs alongside Stash.
- Your YouTube Music home feed on Home, in place of the Qobuz New Releases, Qobuz Playlists and Top Albums rows.
- Autoplay on by default, using YouTube Music's own song radio.
- Auto-download switches for YouTube Music Liked songs and playlists.
- Fix: a newly synced playlist no longer opens empty in Download mode.
- Fix: the Home tab can always be reached from the other tabs.
- A frosted-glass floating tab bar and mini player, Settings moved to Home, and faster, smoother screen transitions.
- A redesigned player: album-coloured glass, the heart on the cover, download beside the quality badge, an Up Next card, and live lyrics below.
- Spotify playlist import from a link, and listening-based suggestions on Home and Search.
- Faster song start (no waiting on a slow backup search), and Search no longer pops the keyboard.
- Lossless (Qobuz FLAC) removed entirely.
- Signed with LUMA's own release key, and an in-app popup when a new version is out.
- Stash's donation prompts, supporter ticker, Discord button, update checker and links to the Stash website removed.

## Acknowledgments

Through Stash, LUMA also builds on [yt-dlp](https://github.com/yt-dlp/yt-dlp), [youtubedl-android](https://github.com/JunkFood02/youtubedl-android), [QuickJS-NG](https://github.com/quickjs-ng/quickjs), [Media3 / ExoPlayer](https://github.com/androidx/media), [ytmusicapi](https://github.com/sigma67/ytmusicapi) (as a reference) and [Spicy Lyrics](https://github.com/Spikerko/spicy-lyrics) (as a reference), plus [Haze](https://github.com/chrisbanes/haze) for the glass blur.

## Legal

LUMA is an independent, unofficial project. It is **not affiliated with, endorsed by, or sponsored by Spotify, YouTube, Google, Qobuz, Apple, Discord, Last.fm, ListenBrainz, MusicBrainz, JioSaavn, KuGou, LRCLIB, or any other service it connects to.** Their names are used only to identify their services, and all trademarks belong to their owners.

LUMA is a tool for managing your own music library. You're responsible for following the terms of service of the music services you use with it. Downloading copyrighted content without a license may be illegal where you live.

## License

Copyright © 2026 Rawnald Clark (Stash)<br>
Copyright © 2026 Ronaz Pradhan (LUMA modifications)

LUMA is free software: you can redistribute it and/or modify it under the terms of the [GNU General Public License](LICENSE), either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but **WITHOUT ANY WARRANTY**; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the [LICENSE](LICENSE) file for the full text.
