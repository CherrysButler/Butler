<p align="center">  <img src="docs/img/mark.png" width="110" alt="Butler"></p>

<h1 align="center">Butler!</h1>

<p align="center">  A third party JanitorAI client for Android that's all about smoothness, quality-of-life stuff, and not falling over.</p>

<p align="center">  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android 8.0+">  <img src="https://img.shields.io/badge/Kotlin-2.1-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin 2.1">  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose">  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-blue?style=flat-square" alt="Apache 2.0"></a>  <img src="https://img.shields.io/badge/trackers-0-EA5A4F?style=flat-square" alt="Trackers: 0">  <img src="https://img.shields.io/badge/status-beta-F2B35E?style=flat-square" alt="Status: beta">  <img src="https://img.shields.io/badge/JanitorAI-unofficial%20client-2B2E33?style=flat-square" alt="Unofficial JanitorAI client"></p>

* * *

## Features!

### 🧈 Butter smooth? Butler smooth! (Not an Ai joke LoL)

* **Native everything.** Kotlin and Jetpack Compose. Tabs switch in a single frame, typing stays under 10 ms a keystroke, and scrolling a 100+ message chat doesn't stutter.
* **Way fewer bugs and rendering weirdness.** Smooth streams! No Character description collapsing, and actually a better error handling.
* **Light on your phone.** Around 200 MB of RAM in my testing, and it does nothing at all while it's sitting there. Numbers are in [docs/PERFORMANCE.md](docs/PERFORMANCE.md).

### 💬 Chatting

* **A chat that's nice to read.** Three layouts (Story, Bubbles, or Janitor-style with avatars), a reading font with real italics so `*actions*` actually look like actions.
* **Readable thinking!** A proper animation with Claude-style spinner verbs while the model thinks, and the whole thought process opens in its own sheet when it's done instead of getting dumped into the chat in a small square.
* **Guided swipes!!** What's that? You don't like a reply? Tell it what to change before it rerolls: "shorter", "more dialogue", "slow down", or type your own. Inspired by ChatGPT's.
* **Choices button.** Lazy to actually type something? short choices can be provided by your model/JLLM to choose.
* **Better error handling!** Your message is saved before it's even sent. If the network shits itself, Butler retries by itself with a countdown. If a reply dies halfway through, you keep what came in and hit Continue.
* **The little thing.** Edit anything, including the first message as this is not included by JanitorAi main app.

### 🎨 Make it yours

* **Themes!** Janitor Classic, Lights out (i wanna see it on OLED's, send ss lol), and Daylight.
* **Markdown your way.** Pick your own colors for speech, thoughts, actions and the rest, so the chat looks how you want it to. a bit more flexible than Janitor's
* **Swap your default persona.** Make any persona your default, swapped properly on Janitor's side, with backups kept in case something goes sideways.
* **Pin and archive chats**, Telegram style. Pull down on your chats to find the archive. Both are saved to your Janitor account as folders, so they follow you to any phone. Folders work too.

### 🔒 Just yours

* **Lock the app** with your fingerprint, face, or screen lock. The recents screen shows a blank card and notifications hide what was said.
* **Replies finish in the background.** Lock your phone mid-reply and it's waiting for you when you come back. Works even on phones that love killing background apps (Fuck you, Infinix).

### 🔌 Proxy nerd stuff

* **Clone proxy presets.** One tap copies a proxy, key and all. The key never shows up on screen. works if u wanna use multiple models and don't wanna make configs by hand.
* **Better OpenRouter handling.** Use OpenRouter presets, pick and order your providers, set fallbacks, and sort them by price, speed, or latency.
* **Context length from inside the chat.** see how full your context is.

### 📦 And the rest

* Sign in with Google, Discord, or e-mail.
* Export chats (Butler's own format or SillyTavern's) and import them back in.
* Block characters, creators, tags and keywords.
* Notifications, plus every notification setting Janitor has.
* Published chats, with reactions and comments.
* **Continued support!** I use this every single day, it's not going anywhere.

## Coming soon!

* **CSS rendering in-chat**, for a more immersive (and honestly cooler) experience.
* **Highlights and "butter mode".** It's a surprise 🧈

## Why was this made?

merely because i really love JanitorAI. but the mobile app is a disaster for a bunch of reasons, so this is the client i wished janitor had.

i got sick enough of it to just build my own. i hope you like it!

## Does it lack something?

For **ordinary** users? No. but if you make bots, you're probably still on the website for that. so: coming soon, with cool features! i don't make bots myself, but i'll ask creators in the community what they actually want. open for feedback.

## Performance?

check out [docs/PERFORMANCE.md](docs/PERFORMANCE.md).

## How do i know this is safe and not malicious?

The code is fully open source, and it'll probably be on F-Droid, which builds the app themselves from this exact code. So it can be audited, and **it will be**.

On top of that, it's easy to show that the app doesn't "call home". It doesn't talk to anything that isn't Janitor (or your own proxy, if you set one), so no command and control servers or anything like that. It doesn't even send diagnostics on its own. If something breaks, you make a report in Settings and send it by hand, only if you want to.

The full list of what it talks to and what it keeps on your phone is in [TRUST.md](TRUST.md).

## API reference?

check [docs/JANITOR_API.md](docs/JANITOR_API.md). everything in there was poked at against the real thing, with dates.

<details><summary>Building it yourself</summary>

You need JDK 21 and the Android SDK with platform 35. Put `sdk.dir=…` in `local.properties`, then:

    ./gradlew :app:installPerf

`perf` is the real deal (release build, signed with the debug key so it installs over itself). Measure on that, not on `debug`.

</details>

## The usual

Butler is independent. It isn't affiliated with, endorsed by (yet?), or connected to JanitorAI. The name and marks are theirs. Butler uses the same public client keys every official client ships, plus your own login, so it can only reach what your account already can.

Licensed under [Apache 2.0](LICENSE).
