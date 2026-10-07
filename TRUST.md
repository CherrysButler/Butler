# What Butler does with your data

Short version: it talks to Janitor, stores what it needs on your phone, and sends nothing
anywhere else. The one exception is asking GitHub whether a newer Butler is out, and only
when you ask it to. Here is the long version, and how to check it.

## Where your traffic goes

Butler contacts exactly these hosts:

- `janitorai.com` and its subdomains: the API, the chat generation route, notifications,
  and `ella.janitorai.com` for pictures.
- `auth.janitorai.com`: Janitor's Supabase project, which is where your login lives.
- `challenges.cloudflare.com`, only during an e-mail sign-in, because Janitor requires
  Cloudflare's check for that.
- Your own reverse proxy, if you set one (OpenRouter or wherever you point it). Butler
  sends your messages there because you told it to; the key you entered goes with them.
- `api.github.com`, and only if you want it to. It's optional: tap Settings › Updates ›
  Check for updates, or turn on "Check on every open" (off until you do). Either way it asks
  which Butler release is the newest, nothing about you, and it stays silent when you're up to
  date. Butler never downloads or installs anything; a newer version gets you a pop-up that
  links to its release page.

Nothing else. There is no Butler server. There is no analytics SDK, no crash reporter, no
ad network. The official app ships Sentry, Statsig and Firebase; Butler ships none of them,
so it sends strictly less of your data than the app you'd otherwise use.

## What's on the phone

- Your session (the tokens that prove you're you), encrypted with a key in the Android
  Keystore. It never touches plain storage.
- A mirror of your chats and characters, so screens paint instantly and work offline.
- Messages you've written and not yet managed to send. These are written before any
  network call, which is the whole point.
- Your settings: the look, the chat layout, fonts, which proxy is selected, routing and
  thinking options for OpenRouter.
- Cloudflare's cookies for janitorai.com: the pass Cloudflare gives you when you clear the
  check at sign-in, and its usual bot and queue cookies. Butler sends them back to Janitor
  the way a browser would, so it's treated like the browser that passed the check. They go
  nowhere else, and they're cleared when you sign out.
- Chat backgrounds and font files you added, copied in so they stay when the originals move.
- With Butter mode or Highlights set to keep their tags on the phone, the tagged copy of
  each reply (Janitor gets the clean one). The proxy key itself is kept by Janitor, not by Butler; Butler only knows
  whether one is saved.

Signing out deletes all of it from the phone. Pins, archive and folders live on your Janitor
account, so they come back when you sign in again.

A debug build can also write the session to a plain file so the API can be probed from a
dev machine, but only when the developer turns it on for their own machine
(`butler.devMirror=true` in `local.properties`). The release build doesn't contain that code
at all, and the debug APKs published on GitHub are built with it off.

## Permissions

Everything Butler asks Android for, and why:

- **Internet** and **network state**: to talk to Janitor (and your proxy), and to know when
  the connection is back so a failed send can go out.
- **Notifications**: a reply keeps writing when you leave Butler, and a notification tells
  you when it lands. Android asks you first; say no and replies still finish, quietly.
- **Foreground service** and **wake lock**: what lets that reply finish in the background.
  Both are held only while a reply is being written, never otherwise.
- **Ignore battery optimisation**: only asked for (once, from Settings or after a reply was
  cut off) on phones that kill background apps anyway. It's your call, and Butler works
  without it.

No contacts, location, storage, camera, microphone, or accessibility. Pictures (a persona's,
a chat background) and font files come through the system picker, which hands Butler the
one file you chose and nothing else.

## When something goes wrong

Butler doesn't phone home about it. Settings › App › Report a problem builds a page of text
on the phone: the app version, the device, the last crash if there was one, the last
failures (status codes, paths, Janitor's error codes, never message bodies), and Butler's
own recent log lines with anything shaped like a token, key or e-mail address blanked out.
You read it, then you decide whether to copy it or send it to someone.

## Check it yourself

Put the phone behind mitmproxy (or any proxy that shows you hostnames) and use Butler for
a while. The host list above is the whole list. If you ever see another one, that's a bug
and I want to know.

## The honest part

Any client, Butler or the official one, holds your session while you use it. That can't be
otherwise. The only real question is whether the code that holds it is honest, and open
source with no server and no telemetry is about as good an answer as software can give.
It is not "100% safe"; nothing is. It is as little trust as the job allows.

The rules that keep this true, and that pull requests will be held to: no Butler-owned
backend, ever; no telemetry, ever; no permission beyond the network; a host list that's
written down and enforced.
