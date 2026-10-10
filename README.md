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
- **Download what you choose.** One switch to auto-download your YouTube Music Liked songs, another for your playlists. Everything else streams.
- **Good sound.** High-quality AAC and Opus audio. FLAC is optional: it only works if you connect your own Qobuz account (or your own lossless relay) in Settings › Audio & Quality.
- **Lyrics.** Synced, word-by-word where available.
- **A glass interface.** A floating frosted tab bar and mini player over your music, with quick, fluid motion between screens.
- **Private by design.** No LUMA account and no analytics. Your sign-ins stay on your phone, encrypted, and are only ever sent back to the service they came from.

## Download

1. On your Android phone, tap **[Download LUMA.apk](https://github.com/ronazpradhan/LUMA/releases/latest/download/LUMA.apk)**.
2. Open the file. If Android asks, allow installs from your browser, then go back and tap **Install**.
3. Open LUMA, tap the gear at the top right of Home, and connect Spotify and/or YouTube Music under **Accounts**.
4. Go to **Sync**, choose your playlists, and tap **Sync now**.

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
- **Qobuz's public catalog** (`www.qobuz.com`), in one place only: opening an artist page sends Qobuz the artist's name, to fill in albums YouTube is missing. No account, and nothing else about you. Qobuz audio (FLAC) is only used if you connect your own Qobuz account or set your own lossless relay. LUMA never contacts the Stash lossless relay: this build ships without its config.
- **Last.fm** isn't available in this build: it ships without a Last.fm API key, so there is nothing to connect and no Last.fm lookups.
- **MusicBrainz**, for artist details, and **JioSaavn**, as the AAC fallback.
- **GitHub**, for yt-dlp updates.
- **The Stash project's share service**, only when you share or open a mix or song link, use Listen Together, or turn on Community.
- **Google Cast**, only when you open the speaker list.

For exactly what each one is sent, see the detailed list in the [Stash README](https://github.com/rawnaldclark/Stash#what-stash-talks-to). LUMA sends the same to the services above, minus the Stash update check, the supporters list, the Qobuz Home rows, the Stash lossless relay and Last.fm, and plus your YouTube Music home feed and song radio.

---

## Built on Stash ❤️

LUMA is built on **[Stash](https://github.com/rawnaldclark/Stash)** by **Rawnald Clark** and its contributors, a brilliant open-source music app. Almost everything that makes LUMA work is their code: the sync engine, the player, lossless audio, lyrics and much more. Huge thanks to them. If you like LUMA, go give Stash a ⭐.

Stash and LUMA are separate projects. LUMA isn't endorsed by or affiliated with Stash, and problems with LUMA belong here, not with them.

### What LUMA changes (2026)

- Its own name, logo and package (`com.luma.music`), so it installs alongside Stash.
- Your YouTube Music home feed on Home, in place of the Qobuz New Releases, Qobuz Playlists and Top Albums rows.
- Autoplay on by default, using YouTube Music's own song radio.
- Auto-download switches for YouTube Music Liked songs and playlists.
- Fix: a newly synced playlist no longer opens empty in Download mode.
- Fix: the Home tab can always be reached from the other tabs.
- A frosted-glass floating tab bar and mini player, Settings moved to Home, and faster, smoother screen transitions.
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
