<p align="center">  <img src="docs/img/mark.png" width="110" alt="Butler"></p>

<h1 align="center">Butler!</h1>

<p align="center">  A third party JanitorAI client for Android that's all about smoothness, quality-of-life stuff, and not falling over.</p>

<p align="center">  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android 8.0+">  <img src="https://img.shields.io/badge/Kotlin-2.1-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin 2.1">  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose">  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-blue?style=flat-square" alt="Apache 2.0"></a>  <img src="https://img.shields.io/badge/trackers-0-EA5A4F?style=flat-square" alt="Trackers: 0">  <img src="https://img.shields.io/badge/status-beta-F2B35E?style=flat-square" alt="Status: beta">  <img src="https://img.shields.io/badge/JanitorAI-unofficial%20client-2B2E33?style=flat-square" alt="Unofficial JanitorAI client"></p>

* * *

## Download!

<a href="https://github.com/CherrysButler/Butler/releases/latest"><img src="https://img.shields.io/github/v/release/CherrysButler/Butler?style=for-the-badge&label=Download&color=EA5A4F" alt="Download the latest release"></a> <a href="https://discord.gg/Kh5z9Cb5F"><img src="https://img.shields.io/badge/Discord-Join-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Join the Discord"></a>

Grab the APK from the [latest release](https://github.com/CherrysButler/Butler/releases/latest) and install it. Your phone will ask you to allow installs from your browser or file manager, that's normal. Needs Android 8.0 or newer.

* **Butler-x.x.x.apk** is the one you want.
* **SHA256SUMS.txt** if you wanna check the files weren't messed with.
* Every release is scanned on [VirusTotal](https://www.virustotal.com/) by the release workflow itself; the scan link and its result are on each release page, under **Checked**.

**F-Droid is coming soon!** It's submitted and waiting on review. Heads up: the F-Droid version and the GitHub version are signed with different keys, so one can't update the other. Pick one and stick with it (switching means uninstalling first).

## Features!

### 🧈 Butter smooth? Butler smooth! (Not an Ai joke LoL)

* **Native everything.** Kotlin and Jetpack Compose. Tabs switch in a single frame, typing stays under 10 ms a keystroke, and scrolling a 100+ message chat doesn't stutter.
* **Way fewer bugs and rendering weirdness.** Smooth streams! No Character description collapsing, and actually a better error handling.
* **Light on your phone.** Around 200 MB of RAM in my testing, and it does nothing at all while it's sitting there. Numbers are in [docs/PERFORMANCE.md](docs/PERFORMANCE.md).

### 🧈 Butler specials (the surprise!)

* **Butter mode.** The model marks the parts of a reply that actually hold the juicy stuff that's required to progress the scene. Tap the butter and the reply folds down to just those, perfect for catching up on a long scene.
* **Highlights.** After a reply, Butler asks your model which lines are romantic, dangerous, sad, funny or spicy, and gives them a soft highlight. The reply itself never changes. The prompt it asks with is yours to edit, if your model needs steering.
* **Rich typing.** " * B keys right on the message box, and the formatting shows as you type, like Discord. *Actions* have never been this easy to write.
* **Description writer.** Write a few lines about your persona, tap ✦ Enhance, pick one of your proxy presets, and it fleshes them out in your own style, keeping everything you wrote. Undo if you liked yours better.
* **Pictures in your message** (beta). Tap the picture key, pick one picture, and a model of yours that can see writes it into your line in your own voice, right where you put it. You read it, change what you like, and send it yourself. Nothing of the picture goes to Janitor, only to your proxy. Switch it off, or make it wait for your go, in Settings › Chat.
* **Agent mode** (beta, proxies with a reasoning model). The reply is drafted, checked against goals strictly, fixed where it fails (exact edits, or a rewrite) and only then delivered. Pick the effort, add your own goals, and watch every step in the thought panel.
  
  
  
  > ⚠️ Butter mode, Highlights, Pictures and Agent mode run on your own model, so they cost tokens (Agent mode two to four calls a reply), and how well they work depends on how well your model follows the rule.

### 💬 Chatting

* **A chat that's nice to read.** Three layouts (Story, Bubbles, or Janitor-style with avatars), a reading font with real italics so `*actions*` actually look like actions.
* **Readable thinking!** A proper animation with Claude-style spinner verbs while the model thinks, and the whole thought process opens in its own sheet when it's done instead of getting dumped into the chat in a small square. The words are yours to change: edit Claude's list or add lists of your own.
* **Guided swipes!!** What's that? You don't like a reply? Tell it what to change before it rerolls: "shorter", "more dialogue", "slow down", or type your own. They stack, too. Inspired by ChatGPT's.
* **Choices button.** Lazy to actually type something? short choices can be provided by your model/JLLM to choose.
* **Better error handling!** Your message is saved before it's even sent. If the network shits itself, Butler retries by itself with a countdown. If a reply dies halfway through, you keep what came in and hit Continue.
* **The little thing.** Edit anything, including the first message as this is not included by JanitorAi main app.
* **Share a bot.** A share key on any character page hands its link to your phone's share sheet.
* **Edit your persona mid-chat.** A pencil beside each persona in Play as opens its editor, then you're back in the chat.
* **The keyboard goes down when you send**, unless you'd rather it stayed up (Settings › Chat).

### 🎨 Make it yours

* **Themes!** Janitor Classic, Lights out, Daylight, and **Custom**: pick an accent and a background and Butler builds the rest. Go Advanced and set every single colour, even how round the corners are.
* **Markdown your way.** Pick your own colors for speech, thoughts, actions and the rest, any colour on a real colour wheel, so the chat looks how you want it to. a bit more flexible than Janitor's
* **Fonts!** Pick one font for your chats and one for the rest of the app: a handful built in, or add your own .ttf / .otf. The app's text size is adjustable too.
* **Backgrounds.** Keep a library of pictures, set one for every chat or a different one per chat.
* **Home your way.** Endless scroll or pages, and it keeps your place when you come back from a character.
* **Swap your default persona.** Make any persona your default, swapped properly on Janitor's side, with backups kept in case something goes sideways.
* **Pin and archive chats**, Telegram style. Pull down on your chats to find the archive. Both are saved to your Janitor account as folders, so they follow you to any phone. Folders work too.

### 🔒 Just yours

* **Lock the app** with your fingerprint, face, or screen lock. The recents screen shows a blank card and notifications hide what was said.
* **Replies finish in the background.** Lock your phone mid-reply and it's waiting for you when you come back. Works even on phones that love killing background apps (Fuck you, Infinix).

### 🔌 Proxy nerd stuff

* **Clone proxy presets.** One tap copies a proxy, key and all. The key never shows up on screen. works if u wanna use multiple models and don't wanna make configs by hand.
* **Better OpenRouter handling.** Use OpenRouter presets, pick and order your providers, set fallbacks, and sort them by price, speed, or latency.
* **Context length from inside the chat.** see how full your context is.
* **Thinking levels.** Low, medium, high (and more) for OpenRouter models that think, set per proxy.
* **Write for me, on another model.** Write for me doesn't have to use your default model. Kimi for the bot, DeepSeek for your lines, whatever you like.

### 📦 And the rest

* Sign in with Google, Discord, or e-mail.
* Export chats (Butler's own format or SillyTavern's) and import them back in.
* Block characters, creators, tags and keywords.
* Notifications, plus every notification setting Janitor has.
* Published chats, with reactions and comments.
* **Update check, and it's optional.** Tap Settings › Updates › Check for updates, or turn on checking on every open. It only asks GitHub for the latest version and gives you the release page. Nothing downloads or installs by itself, and it stays silent when you're up to date.
* **Continued support!** I use this every single day, it's not going anywhere.

## Coming soon!

* **CSS rendering in-chat**, for a more immersive (and honestly cooler) experience. (Creator profiles already render theirs.)
* **Lorebooks that actually get used.**
* (Possibly) Multiple characters together in one chat!

## Why was this made?

merely because i really love JanitorAI. but the mobile app is a disaster for a bunch of reasons, so this is the client i wished janitor had.

i got sick enough of it to just build my own. i hope you like it!

## Does it lack something?

For **ordinary** users? No. but if you make bots, you're probably still on the website for that. so: coming soon, with cool features! i don't make bots myself, but i'll ask creators in the community what they actually want. open for feedback.

## Found a bug? Got an idea?

Bugs go in [Issues](https://github.com/CherrysButler/Butler/issues). If you can, paste the report from Settings › App › Report a problem, it helps a ton. Ideas, questions and everything else go in [Discussions](https://github.com/CherrysButler/Butler/discussions).

Or just come hang out on the [Discord](https://discord.gg/Kh5z9Cb5F): updates, previews of what's coming, and the fastest way to reach me.

## Performance?

check out [docs/PERFORMANCE.md](docs/PERFORMANCE.md).

## How do i know this is safe and not malicious?

The code is fully open source, and it'll probably be on F-Droid, which builds the app themselves from this exact code. So it can be audited, and **it will be**.

On top of that, it's easy to show that the app doesn't "call home". It doesn't talk to anything that isn't Janitor (or your own proxy, if you set one), so no command and control servers or anything like that. The one exception is checking GitHub for a new version, and that's optional: tap the button in Settings, or turn on checking on every open. Nothing is downloaded, you just get a link to the release. It doesn't even send diagnostics on its own. If something breaks, you make a report in Settings and send it by hand, only if you want to.

The full list of what it talks to and what it keeps on your phone is in [TRUST.md](TRUST.md).

## API reference?

check [docs/JANITOR_API.md](docs/JANITOR_API.md). everything in there was poked at against the real thing, with dates.

<details><summary>Building it yourself</summary>

You need JDK 21 and the Android SDK with platform 35. Put `sdk.dir=…` in `local.properties`, then:

```bash
./gradlew :app:installPerf
```

`perf` is the real deal (release build, signed with the debug key so it installs over itself). Measure on that, not on `debug`.

</details>

## The usual

Butler is independent. It isn't affiliated with, endorsed by (yet?), or connected to JanitorAI. The name and marks are theirs. Butler uses the same public client keys every official client ships, plus your own login, so it can only reach what your account already can.

Licensed under [Apache 2.0](LICENSE).
