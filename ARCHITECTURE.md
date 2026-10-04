# How Butler is put together

One Gradle module, Kotlin and Jetpack Compose, three layers:

```
core/      config, design system, network, auth, data (Room + remote), generation, background
feature/   one package per screen: browse, chats, chat, character, community, profile, settings, auth
ui/        the shell: AuthGate, ButlerRoot, the tab host, shared components
```

`core` never imports `feature`. Screens are Compose functions fed by Hilt view models; the
view models talk to repositories; repositories own the mirror and the remote sources.

## The data layer

Everything the screens show comes from Room. The network only ever updates what is already
on disk, which is why screens paint instantly and keep working offline.

- `ChatRepository` mirrors chats and messages. Lists are Paging 3 over the mirror with a
  RemoteMediator that refreshes behind.
- Message rows exist before the server knows about them: the primary key is local, the
  server id is nullable. A queued send and a reply still streaming are ordinary rows.
- `group_marks` is a projection of pin and archive state for the chat list queries. The
  truth is two folders on Janitor named `Pinned` and `Archive`; `FolderRepository.reconcile()`
  rebuilds the marks whenever a chat row's folders change.
- `ApiCall` is the one funnel every HTTP call goes through. Every outcome becomes a value or
  a typed `ApiError` (`Network`, `Unauthorized`, `RateLimited`, `Server`, `Api` with Janitor's
  code, `Serialization`, `Cancelled`), each carrying whether a retry is worth trying. Retries
  back off with jitter and only happen when the error says so.

## Auth

Janitor's auth is Supabase, so Butler uses supabase-kt against `auth.janitorai.com`. The
session is persisted through `SecureSessionStore` (EncryptedSharedPreferences, Android
Keystore). `SessionKeeper` keeps the last good session through a failed refresh, refreshes
when under ninety seconds remain, and renews on foreground, so an expired token on launch is
renewed instead of signing the user out.

OAuth runs in a Custom Tab and comes back through `janitor://auth/…`, the deep-link scheme
Janitor's own project allow-lists. E-mail sign-in needs a Cloudflare Turnstile token, which
`TurnstileSheet` gets from the widget running in a WebView whose document origin is
janitorai.com (the site key is bound to that hostname).

## Generation

Two transports behind one interface (`GenerationTransport`):

- **Proxy.** Butler posts to the website's `/generateAlpha`, which assembles the prompt and
  hands back the full OpenAI payload. Butler adds OpenRouter options (preset, provider order,
  sort) and streams the reply from the user's own proxy host. Janitor never sees the stream.
- **JLLM.** Janitor's own model over a WebSocket at `/mobile/generateAlpha`.

Reasoning tags (`<think>`, `<thinking>`) are lifted out of the stream by an incremental
parser and kept in their own column. Prompt assembly is entirely server-side; Butler never
needs Janitor's system prompt.

## The outbox, which is why this exists

The official app loses messages two ways: text that was never persisted when the network
died, and failures that just flash red. The send pipeline is designed against both.

- **Persist before the network.** Send writes the text to the outbox in the same transaction
  that clears the composer. There is no code path where the text exists only in memory.
- **Retry quietly, fail loudly.** Retryable failures retry with a visible countdown.
  Terminal ones (paywall, empty wallet, 403) stop and name the fix. A manual Retry appears
  only where retrying can change the outcome.
- **Never post twice.** The API has no idempotency key, so before re-posting a message
  whose outcome is unknown, the pipeline re-reads the chat tail and adopts the server's row
  if it's already there.
- **Continue, don't regenerate.** Chunks are persisted as they arrive. A stream that dies
  after partial output is resumed with `CONTINUE`, not restarted; only a generation that
  produced nothing is retried from scratch.
- **Survive process death.** Every state is on disk; `resumeAll()` at start picks up where
  the process stopped. On phones that freeze background apps, `ReplyService` holds a wake
  lock while a reply streams, and a home-screen widget earns the exemption those phones
  grant to apps with a widget.

## Rendering

Roleplay text goes through a purpose-built tokenizer into an `AnnotatedString`: `*action*`,
`**emphasis**`, `"speech"`, `` `thought` ``, lists and rules, with `**` resolved before `*`
and unbalanced markers rendered literally. The persona's name is marked where it fills
`{{user}}`. Creator HTML on character pages is rendered natively (`RichHtml`), colours as
written. There is no WebView anywhere except the Turnstile check.

Streaming grows the reply in place without the words above moving: growth is absorbed into
the scroll offset each frame, and a draw-time translation covers the gap until the layout
catches up.

## The shell

Five tabs stay composed under one navigation destination (`TabHost`); switching measures a
different child and nothing else, so a tab change is one frame. Pushed screens cut, they
don't animate. `AuthGate` routes between the door and the app on the session state; a
refresh failure for want of network keeps the app, not the door.

## Design

`Palette.kt` holds the only hex values in the app, three times, once per look. Everything
else reads Material roles or `ButlerTheme.colors`. Two typefaces: the phone's own sans for
the UI, Atkinson Hyperlegible for prose because some OEM faces ship without an italic and
that flattens every `*action*`.

## Toolchain

JDK 21 to run Gradle (the compile target is JVM 17), Gradle wrapper 8.11.1, AGP 8.7.3,
compile SDK 35, min SDK 26. The `perf` build type is release behaviour signed with the debug
key: measure there.
