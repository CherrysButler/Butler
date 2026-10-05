# JanitorAI API Reference (reverse-engineered)

> **Provenance:** derived from static analysis of the official Android app
> (`com.janitor.ai` v2.5.0 / build 136, Hermes bundle). This is *our own*
> analysis, kept here so Butler can be built from either machine.
>
> **How to read this doc.** Two kinds of fact live here and they are not equally
> trustworthy:
>
> - **✅ Verified (2026-09-23)** — observed against the live API with a real session.
>   Field names, types and envelopes are measured, not inferred. Trust these.
> - *Everything unmarked* — derived from static analysis of the Hermes bundle. Endpoint
>   paths are generally right; **request/response shapes are inference** and several have
>   already been proven wrong (see below). Treat as a lead, not a contract.
>
> Verification has corrected real errors: `GET /characters` took `mode`/`tag_id`, not
> `nsfw`/`tags[]`; `GET /personas` is actually `/personas/mine`; access tokens last 3 hours,
> not 1; passkeys are disabled at the project level. **Don't invent
> a field name** — verify it, then record it here with a ✅ and the date.

> **Sensitivity note:** contains only *public* client-side values (the Supabase
> anon key, Turnstile **site** key, RevenueCat public key, Sentry DSN) — the same
> values shipped in every official client. No private secrets. **Before making the
> repo public, decide whether to keep this reverse-engineered reference in it.**

---

# Janitor AI — Complete API Reference
**Source**: Static analysis of `com.janitor.ai-136.apk` (Hermes HBC v98, ~23MB bundle)
**Decompiled with**: `hbc-decompiler` (hermes-dec 0.1.7)
**Date**: 2026-09-17

---

## 1. Environment Constants

All values extracted from the compiled config module (module 1789 in the Hermes bundle).

| Constant | Value |
|---|---|
| `BACKEND_ENDPOINT` | `https://janitorai.com/mb` |
| `SUPABASE_ENDPOINT` | `https://auth.janitorai.com` |
| `SUPABASE_ANON_KEY` | `eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im1jbXp4dHpvbW1wbnhreW5kZGJvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3MjgzNzA3NDAsImV4cCI6MjA0Mzk0Njc0MH0.UfRPni4ga9Lmin8j0JjV5ouuK9bXp8tsqPJ8pMTDDAI` |
| `IMAGES_ENDPOINT` | `https://ella.janitorai.com` |
| `LLM_SERVICE_ENDPOINT` | `https://janitorai.com/mobile` |
| `NOTIFICATIONS_API_URL` | `https://janitorai.com/notifs/mobile` |
| `NOTIFICATIONS_WS_URL` | `ws://janitorai.com/notifs/mobile` |
| `WEBVIEW_URL` | `https://janitorai.com` |
| `WEB_DOMAIN` | `https://janitorai.com` |
| `APP_LANG_REMOTE_URL` | `https://janitorai.com/mobile-i18n` |
| `LLM_SERVICE_ENDPOINT` | `https://janitorai.com/mobile` |
| `STATSIG_CLIENT_KEY` | `client-Pqp6U4689ZDU2EmaGPIao5Dze9sHVzyN1E5an9HPsri` |
| `REVENUECAT_API_KEY` (Android) | `goog_siPqOtUspkByhMcdnLcUxdiehxu` |
| `SENTRY_DSN` | `https://dd89349bcf7aafdd4a7eeac3f5b26e99@o4510467634298880.ingest.us.sentry.io/4510473353363456` |
| `TURNSTILE_KEY_AUTH` | `0x4AAAAAAAMttfE31t8DPXZ8` |
| `TURNSTILE_ACCOUNT_DELETE_KEY` | `0x4AAAAAAB62g0eBfTKYTTKe` |
| `AGE_VERIFICATION_COUNTRY_CODES` | `BR,AU,GB` |
| CodePush Package | `@revopush/react-native-code-push` v2.5.0 |
| CodePush Server Base | `https://revopush.org/v0.1/public/codepush/` |

---

## 2. Auth Configuration

### Supabase Client
```
URL:     https://auth.janitorai.com
anon key: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im1jbXp4dHpvbW1wbnhreW5kZGJvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3MjgzNzA3NDAsImV4cCI6MjA0Mzk0Njc0MH0.UfRPni4ga9Lmin8j0JjV5ouuK9bXp8tsqPJ8pMTDDAI
Project ref: mcmzxtzommpnxkynddbo
```

The Supabase JS client is the auth provider. Auth state persists under AsyncStorage key **`supabase.auth.token`**.

---

## 3. Auth Flow

### 3.1 Request Headers (all authenticated calls)
```
Authorization: Bearer <supabase_access_token>
apikey: <SUPABASE_ANON_KEY>
Content-Type: application/json
```

Backend calls (axios client) inject the Bearer token via a request interceptor. The token is fetched from `supabase.auth.getSession()`.

### 3.2 Login Methods (via Supabase GoTrue)
The app supports these sign-in methods (from the Supabase auth-js method list in the bundle):
- `signInWithPassword` — email + password
- `signInWithOAuth` — OAuth redirect (Google, Discord, Apple)
- `signInWithOtp` — OTP via email
- `signInWithIdToken` — native social tokens
- `signInWithSSO`

#### Email/Password Login
```
POST https://auth.janitorai.com/auth/v1/token?grant_type=password
Content-Type: application/json
apikey: <anon_key>

{
  "email": "user@example.com",
  "password": "password"
}
```
Response:
```json
{
  "access_token": "<jwt>",
  "token_type": "bearer",
  "expires_in": 10800,
  "expires_at": 1234567890,
  // NOTE (verified 2026-09-23): expires_in is 10800 (3h), not 3600.
  "refresh_token": "<refresh_token>",
  "user": { ... }
}
```

#### Token Refresh
```
POST https://auth.janitorai.com/auth/v1/token?grant_type=refresh_token
Content-Type: application/json
apikey: <anon_key>

{
  "refresh_token": "<refresh_token>"
}
```

#### OTP Request (email code)
```
POST https://auth.janitorai.com/auth/v1/otp
```
#### OTP Verify
```
POST https://auth.janitorai.com/auth/v1/verify
```

#### Discord OAuth Redirect
Deep link redirect target: `://auth/discord`
OAuth callback: `https://auth.janitorai.com/auth/v1/callback`

#### Sign Up
```
POST https://auth.janitorai.com/auth/v1/signup
```

#### Logout
```
POST https://auth.janitorai.com/auth/v1/logout
```
The app calls `supabaseLogout` which calls `supabase.auth.signOut()`.

### 3.3 Session Persistence
- Storage key: `supabase.auth.token` (AsyncStorage)
- Also uses `session-storage` and `chat-settings-storage` keys
- Uses Navigator LockManager for token rotation safety
- Refresh happens automatically via the Supabase JS client

### 3.4 Backend Auth Account Endpoints
These are custom Janitor endpoints (not Supabase GoTrue), base: `https://janitorai.com/mb`:

| Method | Path | Description |
|---|---|---|
| `GET` | `/auth/account` | Get account info |
| `POST` | `/auth/email/lookup` | Check if email registered |
| `GET` | `/auth/sessions` | List active sessions |

### 3.5 Passkey Support

> ⚠️ **Disabled — verified 2026-09-23.** `GET /auth/v1/settings` reports
> `"passkeys_enabled": false` on Janitor's project, so these endpoints are not
> callable no matter what the client does. The roadmap item is blocked on Janitor
> enabling it, not on Butler's implementation.

The endpoints the SDK would use if it were enabled:
```
POST https://auth.janitorai.com/auth/v1/passkeys/authentication/options
POST https://auth.janitorai.com/auth/v1/passkeys/authentication/verify
POST https://auth.janitorai.com/auth/v1/passkeys/registration/options
POST https://auth.janitorai.com/auth/v1/passkeys/registration/verify
POST https://auth.janitorai.com/auth/v1/passkeys/authentication/verifyBeforeUpdateEmail
```

### 3.6 Cloudflare Turnstile
Auth screen loads a Turnstile widget for bot protection using key `0x4AAAAAAAMttfE31t8DPXZ8`. The token is submitted with login/register requests.

---

## 4. Backend API — `https://janitorai.com/mb`

All routes are relative to this base. Axios client with axios v1.19.0. Auth via `Authorization: Bearer <jwt>` + `apikey: <anon>`.

### 4.1 Characters

| Method | Path | Description | Auth |
|---|---|---|---|
| `GET` | `/characters` | List/search characters | Optional |
| `POST` | `/characters` | Create character | Required |
| `PATCH` | `/characters` | Update character | Required |
| `DELETE` | `/characters` | Delete character | Required |
| `GET` | `/characters/autocomplete` | Search autocomplete | Optional |
| `GET` | `/characters/v2/blocked` | Get blocked characters | Required |
| `GET` | `/characters/blockedById` | Characters that blocked you | Required |
| `GET` | `/characters/v2/mine` | My characters | Required |
| `GET` | `/character/{characterId}` | Get character by ID | Optional |
| `GET` | `/character/{characterId}/count` | Get character stats/counts | Optional |
| `GET` | `/character/{characterId}/chats` | Chats with this character | Required |
| `GET` | `/character/{characterId}/preview` | Character preview | Optional |
| `GET` | `/character/{characterId}/persona` | Get persona for character | Required |
| `PATCH` | `/character/{characterId}/order` | Reorder character | Required |
| `POST` | `/character/{characterId}/disable-trending` | Disable trending flag | Required |
| `POST` | `/character/{characterId}/enable-trending` | Enable trending flag | Required |
| `POST` | `/character/{characterId}/nsfw-avatar` | Set NSFW avatar | Required |
| `GET` | `/character-analytics` | Analytics dashboard | Required |
| `GET` | `/character-analytics/{characterId}` | Per-character analytics | Required |
| `POST` | `/mobile-sanitize` | Sanitize character content | Required |
| `GET` | `/settings/{characterId}` | Character AI settings | Required |
| `GET` | `/counts/{characterId}` | Counts for character | Optional |
| `GET` | `/exists` | Check if entity exists | Optional |
| `GET` | `/embed-preview` | Embed preview | Optional |

Query params for `GET /characters` — **verified live 2026-09-23** against the
unauthenticated endpoint (see *Verified response* below). Two of these differ from the
earlier static-analysis guess; the superseded values are named so nobody reintroduces them.

| Param | Type | Values / notes |
|---|---|---|
| `page` | int | **1-indexed.** `page=0` returns `400 {"message":"","statusCode":400}` |
| `sort` | enum | `latest`, `popular`, `trending`, `trending24`, `created`, `relevance`, `random`. An invalid value 400s with the full enum in `message[0]`. Default appears to be `popular`. |
| `search` | string | Text search. Narrows `total` (e.g. `search=vampire` gives `total: 2274`). |
| `mode` | enum | `sfw`, `all`, `nsfw`. **The official app's Popular is `sort=popular` with `mode=all`** (signed in, verified 2026-10-03: Medieval Fantasy World RP, Modern Life RPG, Eva, dante stone…); its filter sheet's View "Limited only" is `sfw`. Anonymous calls leave out `is_explicit_for_anon` characters. **This is the NSFW filter.** The previously documented `nsfw=true/false` is silently ignored. Verified: `mode=sfw` returns only `is_nsfw:false`, `mode=nsfw` only `is_nsfw:true`. |
| `tag_id[]` | int[] | Tag IDs, **bracket form** (`?tag_id[]=1&tag_id[]=3`). Works for one or many, and is what the web client sends. The bare form `tag_id=1` is a **400** on a single value (the validator wants an array); `tag_id=1&tag_id=3` happens to work only because ≥2 values parse as one. Use the brackets. Max 53 elements, server-enforced. |
| `size` | — | Accepted but ignored; page size is fixed at **34**. |
| `count_mode` | enum | `bounded` (default) or `exact`. Invalid values 400 with the enum. On broad queries both still return `total: 10000` / `total_relation: "gte"` — the cap is not lifted by `exact`. |
| `special_mode` | enum | `trending`, `newcomer`, `trending24`, `hidden_gems` (the 400 for `hidden_gem` lists them, 2026-10-03). **Separate from `sort`** and takes precedence over it. The official app's "Trending ⌄" is `trending24` (24h) / `trending` (Weekly); "Hidden Gems" is `hidden_gems`. |
| `favorites` | bool | `true` restricts to the caller's favourited characters. Requires auth. |
| *(min messages / tokens / proxy)* | — | **No server parameter found** (2026-10-03): `min_messages`, `min_total_messages`, `min_tokens`, `allow_proxy`, `proxy`, `limited` are all ignored signed in. The official app's bundle has `applyMessagesFilter` / `applyTokensFilter`, i.e. it filters fetched rows on the phone; Butler does the same. List rows carry no `allow_proxy`, so the Proxy switch is not reproducible. |
| `following` | bool | `true` restricts to characters by creators the caller follows. Requires auth. |
| `language` | string | e.g. `en`. Accepted; no observable effect on the sample tested. |
| `include_top_custom_tags` | bool | `true` adds a `top_custom_tags[]` array to the envelope. |

> **Reading `total` correctly.** `total_relation` tells you whether `total` is real:
> `"eq"` means an exact count, `"gte"` means a lower bound saturated at `pagination_limit`
> (10000). A filtered query often returns `"eq"` (e.g. `tag_id[]=1&tag_id[]=3` →
> `total: 6883, "eq"`), so an exact count *can* be shown — but only when `total_relation`
> says `eq`. Paging must still stop on a short page, never on `total`.
>
> This also invalidates a tempting test: a query returning `total: 10000, "gte"` is **not**
> evidence that a filter was ignored — it may simply have more than 10000 matches. Verify a
> filter by checking the returned rows, not the count. (An earlier revision of this document
> got the `tag_id` syntax wrong exactly this way.)

### Verified response — `GET /characters`

Envelope:

```json
{
  "data": ["Character[] — 34 per page"],
  "page": 1,
  "size": 34,
  "total": 10000,
  "pagination_limit": 10000,
  "total_relation": "gte",
  "filtered_total": 10000
}
```

`total` saturates at `pagination_limit` (10000) and is reported with `total_relation: "gte"`
— it is an Elasticsearch-style **lower bound, not an exact count**. Do not derive a page
count from it; page until `data` comes back short.

Character object (all 26 fields present on all 34 rows sampled):

```json
{
  "id": "62650d46-bcda-4eac-90a5-1162cb3d5d80",
  "name": "Mafia Boss",
  "description": "string",
  "mobileDescription": "string",
  "avatar": "85f5b296-6c8b-4076-a8d1-4f7065657f1f.jpeg",
  "creator_id": "872df56d-38f0-4383-b5d9-bce89f2d32d5",
  "creator_name": "KLOOMSY",
  "creator_verified": true,
  "creator_plusbadge": false,
  "creator_display_prefs": {
    "show_plus_badge": false,
    "show_member_since": true,
    "username_color": "#b3ff8f",
    "show_shiny_username": false
  },
  "stats": { "chat": 861540, "message": 38935054 },
  "tags": [
    {
      "id": 1,
      "name": "<emoji> Male",
      "slug": "male",
      "description": "Male characters",
      "created_at": "2023-04-04T09:18:30.194451+00:00"
    }
  ],
  "custom_tags": ["isekai"],
  "total_tokens": 462,
  "public_chat_count": 147,
  "is_nsfw": true,
  "is_image_nsfw": false,
  "is_public": true,
  "is_deleted": false,
  "is_force_remove": false,
  "is_proxy_enabled": true,
  "showdefinition": false,
  "scheduled_publish_at": null,
  "created_at": "2023-06-04T00:55:17.988502+00:00",
  "updated_at": "2024-10-24T07:27:58.572600+00:00",
  "first_published_at": "2023-06-04T00:55:17.988502"
}
```

Field notes (these are the traps, not a restatement of the JSON):

- **`avatar` is a bare filename, not a URL.** Build it as
  `https://ella.janitorai.com/bot-avatars/{avatar}`.
- **`creator_display_prefs` is nullable** — null on 32 of the 34 rows sampled. Every other
  field was non-null on every row, but that is what was *observed*, not proven; treat
  unseen nullability as unknown.
- **`mobileDescription` is not always equal to `description`.** Use `mobileDescription`
  on mobile surfaces.
- **`tags` and `custom_tags` are different types.** `tags[]` holds full objects
  (id/name/slug/description/created_at); `custom_tags` is a plain `string[]` of free-text
  creator tags. Don't merge them.
- `tags[].name` carries a leading emoji; `slug` is the clean key. **`tags[].id` is an int**
  (that's what `tag_id` filters on), while character and creator ids are UUID strings.
- `stats` is `{chat, message}` — lifetime totals, not per-user.
- **`first_published_at` has no timezone suffix**, while `created_at` / `updated_at` end in
  `+00:00`. Normalize on ingest or parsing will differ per field.
- Timestamps are ISO-8601 with microseconds.

Error body (NestJS shape), useful for `ApiError` classification:

```json
{
  "message": ["sort must be one of the following values: latest, popular, ..."],
  "error": "Bad Request",
  "statusCode": 400
}
```

`message` is a **string array** on validation errors but a plain **string** on others
(`page=0` returns `{"message":"","statusCode":400}`, with no `error` key at all). Decode it
leniently or it will throw on the second shape.

Response headers of note: `x-backend-pool: search`, and
`Cache-Control: public, max-age=0, s-maxage=1800, stale-while-revalidate=86400`.

Character body shape (POST/PATCH):
```json
{
  "name": "string",
  "title": "string",
  "description": "string",
  "personality": "string",
  "scenario": "string",
  "example_dialog": "string",
  "first_messages": ["string"],
  "tags": ["string"],
  "is_nsfw": false,
  "allow_published_chats": true,
  "avatar_id": "file_uuid"
}
```

### 4.2 Chats

| Method | Path | Description | Auth |
|---|---|---|---|
| `POST` | `/chats` | Create new chat | Required |
| `GET` | `/chats/{chatId}` | Get chat | Required |
| `PATCH` | `/chats/{chatId}` | Update chat name/settings | Required |
| `DELETE` | `/chats/{chatId}` | Delete chat | Required |
| `GET` | `/chats/list` | List my chats | Required |
| `GET` | `/chats/character/{characterId}` | Get chat for character | Required |
| `GET` | `/chats/character/{characterId}/chats` | All chats with character | Required |
| `GET` | `/chats/character-chats` | All character chats | Required |
| `GET` | `/chats/character-chats/mine` | My character chats | Required |
| `GET` | `/chats/homepage` | Homepage chat feed | Optional |
| `POST` | `/chats/{chatId}/fork` | Fork chat from message | Required |
| `GET` | `/chats/{chatId}/emoji-definitions` | Emoji reactions | Optional |
| `GET` | `/chats/character/{characterId}/persona` | Persona for chat | Required |
| `GET` | `/chats/character/{characterId}/preview` | Character preview | Optional |
| `GET` | `/chats/public/discover/trending` | Trending public chats | Optional |
| `GET` | `/chats/public/history` | Recent public chat history | Optional |
| `POST` | `/chats/public/history/{chatId}/dismiss` | Dismiss from history | Required |
| `GET` | `/chats/public/{chatId}` | View public chat | Optional |
| `GET` | `/chats/public/character/{characterId}` | Public chats for character | Optional |
| `GET` | `/chats/public/character/{characterId}/hidden` | Hidden public chats | Required |
| `GET` | `/chats/public/user/{userId}` | User's public chats | Optional |
| `POST` | `/chats/{chatId}/publish` | Publish chat | Required |
| `GET` | `/chats/{chatId}/publish` | Get publish data | Required |
| `GET` | `/chats/publish/mine` | My published chats | Required |
| `DELETE` | `/chats/{chatId}/publish` | Unpublish chat | Required |
| `POST` | `/chats/public/{chatId}/favorite` | Favorite public chat | Required |
| `DELETE` | `/chats/public/{chatId}/favorite` | Unfavorite public chat | Required |
| `GET` | `/chats/public/{chatId}/favorite` | Check favorite status | Required |
| `GET` | `/chats/public/{chatId}/favorite/count` | Favorite count | Optional |
| `POST` | `/chats/public/{chatId}/react` | React to public chat | Required |
| `DELETE` | `/chats/public/{chatId}/react` | Remove reaction | Required |
| `POST` | `/chats/public/{chatId}/hide` | Hide public chat | Required |
| `DELETE` | `/chats/public/{chatId}/hide` | Unhide public chat | Required |
| `POST` | `/chats/public/{chatId}/confetti` | Send confetti reaction | Required |

Create chat body:
```json
{
  "character_id": "uuid",
  "persona_id": "uuid|null"
}
```

### 4.3 Chat Messages

| Method | Path | Description | Auth |
|---|---|---|---|
| `POST` | `/chats/{chatId}/messages` | Create message(s) | Required |
| `PATCH` | `/chats/{chatId}/messages/{messageId}` | Edit message | Required |
| `DELETE` | `/chats/{chatId}/messages` | Delete messages | Required |
| `PATCH` | `/ratings/{chatId}/messages/{messageId}` | Rate message (thumbs) | Required |

Create messages body:
```json
{
  "messages": [
    {
      "content": "string",
      "is_human": true
    }
  ]
}
```

### 4.4 Chat Folders

| Method | Path | Description | Auth |
|---|---|---|---|
| `POST` | `/chats/folders` | Create folder | Required |
| `GET` | `/chats/folders` | List folders | Required |
| `GET` | `/chats/folders/{folderId}` | Get folder | Required |
| `GET` | `/chats/folders/{folderId}/list` | List chats in folder | Required |
| `PATCH` | `/chats/folders/{folderId}` | Rename folder | Required |
| `DELETE` | `/chats/folders/{folderId}` | Delete folder | Required |
| `POST` | `/chats/folders/{folderId}/chats` | Add chat to folder | Required |
| `DELETE` | `/chats/folders/{folderId}/chats/{chatId}` | Remove chat from folder | Required |

### 4.5 Chat Comments (Public Chats)

| Method | Path | Description | Auth |
|---|---|---|---|
| `GET` | `/chats/{chatId}/comments` | Get comments | Optional |
| `POST` | `/chats/{chatId}/comments` | Post comment | Required |
| `DELETE` | `/chats/comments/{commentId}` | Delete comment | Required |
| `POST` | `/chats/comments/{commentId}/like` | Like comment | Required |
| `DELETE` | `/chats/comments/{commentId}/like` | Unlike comment | Required |
| `POST` | `/chats/comments/{commentId}/react` | React to comment | Required |
| `DELETE` | `/chats/comments/{commentId}/react` | Remove react | Required |
| `GET` | `/chats/comments/{commentId}/likes` | Likes on comment | Optional |

### 4.6 Personas — ✅ Verified 2026-09-23

The earlier static-analysis list flattened this module's routes and got the list
endpoints wrong. These paths are confirmed against the live API; the bundle's route
registry builds them as `base + suffix`, which is why `/reorder` and `/my_personas`
appeared to be top-level and are not.

| Method | Path | Description | Status |
|---|---|---|---|
| `GET` | `/personas/mine` | **List my personas** | ✅ 200 |
| `POST` | `/personas` | Create persona | from registry |
| `DELETE` | `/personas/{id}` | Delete persona | from registry |
| `PATCH` | `/personas/{id}/group` | Move persona to group: `{groupId}`, `null` ungroups | ✅ 200 `true` (2026-10-05) |
| `PATCH` | `/personas/reorder` | Reorder personas: `{personas: [{id, order}]}` | ✅ 200 `true` (2026-10-05) |
| `GET` | `/persona-groups/mine` | List persona groups: `[{id, userId, name, description, color, order, created_at, updated_at}]`, by `order` | ✅ 200 |
| `POST` | `/persona-groups` | Create group: `{name, description, color}`; `""` description stored as null | ✅ 201, the group (2026-10-05) |
| `PATCH` | `/persona-groups/{id}` | Rename / recolour: `{name?, color?, description?}` | ✅ 200, the group (2026-10-05) |
| `DELETE` | `/persona-groups/{id}` | Delete group; its personas stay, with `groupId: null` | ✅ 200 `true` (2026-10-05) |
| `PATCH` | `/persona-groups/reorder` | Reorder groups: `{groups: [{id, order}]}` (max 50) | ✅ 200 `true` (2026-10-05) |

**Superseded — do not use:** ~~`GET /personas`~~ (404), ~~`GET /my_personas`~~ (404),
~~`PATCH /reorder`~~, ~~`POST /persona-groups` as a bare path for listing~~.

`GET /personas/mine` returns a **bare array**, not an envelope:

```json
[
  {
    "id": "uuid",
    "name": "string",
    "avatar": "vV0OlBvdgi_qlQYQaNpFI.webp",
    "appearance": "string",
    "pronouns": "string",
    "order": 0
  }
]
```

Matches the persona shape recovered independently from the bundle
(`{id, name, appearance, avatar, pronouns, order}`) — the two sources agree.
Note the field is `appearance`, **not** `description`, and `avatar` is a bare filename
like everywhere else.

### 4.7 Profiles

| Method | Path | Description | Auth |
|---|---|---|---|
| `GET` | `/profiles/{userId}` | Get user profile | Optional |
| `GET` | `/profiles/mine` | Get my profile | Required |
| `PATCH` | `/profiles/mine` | Update my profile | Required |
| `DELETE` | `/profiles/mine` | Initiate account deletion | Required |
| `GET` | `/profiles/search` | Search profiles | Optional |
| `PATCH` | `/display-prefs` | Update display preferences | Required |
| `PATCH` | `/me/allow-published-chats` | Toggle published chats | Required |
| `POST` | `/me/manage` | Manage account | Required |
| `POST` | `/me/receipt-conflict` | Report receipt conflict | Required |
| `GET` | `/user/{userId}` | Get user | Optional |

Update profile body:
```json
{
  "username": "string",
  "description": "string",
  "avatar_id": "file_uuid|null"
}
```

### 4.8 Following / Social

| Method | Path | Description | Auth |
|---|---|---|---|
| `POST` | `/follow` | Follow user | Required |
| `POST` | `/unfollow` | Unfollow user | Required |
| `GET` | `/following/{userId}` | Get following list | Optional |
| `GET` | `/v2/myfollowing` | My following | Required |
| `GET` | `/myfavorites/{characterId}` | Favorite status for character | Required |
| `GET` | `/myfollowing/{userId}` | Following status | Required |
| `GET` | `/topics/{topic}` | Topic feed | Optional |
| `GET` | `/v2/mine` | My characters | Required |
| `POST` | `/feed` | Activity feed | Optional |
| `POST` | `/beacon` | Activity beacon | Required |
| `POST` | `/collect` | Data collection event | Optional |

### 4.9 Reviews

| Method | Path | Description | Auth |
|---|---|---|---|
| `POST` | `/reviews` | Create review | Required |
| `GET` | `/review/{reviewId}` | Get review | Optional |
| `POST` | `/like/review/{reviewId}` | Like review | Required |
| `POST` | `/react/review/{reviewId}` | React to review | Required |

### 4.10 Reports & Moderation

| Method | Path | Description | Auth |
|---|---|---|---|
| `POST` | `/v3/item-reports` | Submit item report | Required |
| `GET` | `/v3/item-reports/count` | Report count | Required |
| `GET` | `/v3/reports` | Report history | Required |
| `GET` | `/v3/reports/count` | Report count | Required |
| `POST` | `/report` | Submit general report | Required |
| `GET` | `/moderation` | Moderation queue | Required |
| `GET` | `/audits` | Audit log | Required |
| `POST` | `/character/{characterId}` (report) | Report character | Required |
| `POST` | `/comment/{commentId}` (report) | Report comment | Required |
| `POST` | `/profile/{profileId}` | Report profile | Required |
| `POST` | `/review/{reviewId}` (report) | Report review | Required |
| `DELETE` | `/comment/{commentId}` | Delete comment | Required |
| `GET` | `/verificationReport/{profileId}` | Verification report | Required |

### 4.11 Subscriptions & Billing

| Method | Path | Description | Auth |
|---|---|---|---|
| `GET` | `/plans` | Get subscription plans | Optional |
| `POST` | `/subscriptions` | Create/update subscription | Required |
| `GET` | `/subscriptions` | Get subscription status | Required |
| `POST` | `/checkout` | Initiate checkout | Required |
| `POST` | `/me/receipt-conflict` | Report receipt conflict | Required |

### 4.12 Janitor Router (AI Model Config)

Base prefix: `/janitor-router`, all on `https://janitorai.com/mb`

| Method | Path | Description | Auth |
|---|---|---|---|
| `GET` | `/janitor-router/catalog` | Get available AI models | Required |
| `GET` | `/janitor-router/cockpit` | Get router cockpit config | Required |
| `GET` | `/janitor-router/config` | Get user AI config | Required |
| `PUT` | `/janitor-router/config` | Update AI config | Required |
| `POST` | `/janitor-router/config/migrate-legacy` | Migrate legacy config | Required |
| `GET` | `/janitor-router/wallet` | Get router wallet balance | Required |
| `POST` | `/janitor-router/favorites` | Toggle model favorite | Required |
| `POST` | `/janitor-router/signup-bonus/claim` | Claim signup bonus | Required |
| `GET` | `/prompt-library` | List prompt library | Required |
| `POST` | `/prompt-library` | Create prompt | Required |
| `PATCH` | `/prompt-library/{id}` | Update prompt | Required |
| `DELETE` | `/prompt-library/{id}` | Delete prompt | Required |
| `GET` | `/proxy-configs` | List proxy configs | Required |
| `POST` | `/proxy-configs` | Create proxy config | Required |
| `DELETE` | `/proxy-configs/{id}` | Delete proxy config | Required |
| `GET` | `/api-settings` | Get API settings | Required |
| `POST` | `/api-settings` | Create API settings | Required |
| `PATCH` | `/api-settings` | Update API settings | Required |
| `POST` | `/prompts/import` | Import prompts | Required |

Router wallet error codes:
- `JANITOR_ROUTER_INSUFFICIENT_BALANCE`
- `JANITOR_ROUTER_DISABLED`
- `JANITOR_ROUTER_MODEL_UNAVAILABLE`
- `JANITOR_ROUTER_CONFIG_UNAVAILABLE`

### 4.13 Wallet

| Method | Path | Description | Auth |
|---|---|---|---|
| `GET` | `/wallet` | Get wallet balance | Required |

Wallet response shape:
```json
{
  "wallet_available": number,
  "wallet_available_stack": [...],
  "wallet_unavailable_stack": [...],
  "wallet_unknown_stack": [...],
  "walletRevision": number
}
```

### 4.14 Media / File Upload

Base prefix: `/media`, all on `https://janitorai.com/mb`

| Method | Path | Description | Auth |
|---|---|---|---|
| `POST` | `/media/files` | Create upload record | Required |
| `GET` | `/media/files` | List files | Required |
| `PATCH` | `/media/files/{id}` | Update file metadata | Required |
| `DELETE` | `/media/files` | Delete files | Required |
| `POST` | `/media/files/{id}/upload-complete` | Confirm upload done | Required |
| `GET` | `/media/files/{id}/moderation-status` | Check moderation | Required |
| `POST` | `/media/folders` | Create folder | Required |
| `GET` | `/media/folders` | List folders | Required |
| `PATCH` | `/media/folders/{id}` | Update folder | Required |
| `DELETE` | `/media/folders` | Delete folders | Required |

Upload flow:
1. `POST /media/files` → get presigned URL + file UUID
2. PUT to the presigned URL (direct to storage)
3. `POST /media/files/{id}/upload-complete` → confirm

Separate upload endpoint (legacy):
- `POST /upload/uploadFile` — multipart/form-data direct upload

### 4.15 Posts / Community Feed

| Method | Path | Description | Auth |
|---|---|---|---|
| `POST` | `/posts` | Create post | Required |
| `GET` | `/posts/{postId}` | Get post | Optional |
| `DELETE` | `/posts/{postId}` | Delete post | Required |
| `POST` | `/posts/{postId}/like` | Like post | Required |
| `POST` | `/posts/{postId}/bookmark` | Bookmark post | Required |
| `POST` | `/posts/{postId}/comments` | Comment on post | Required |
| `GET` | `/users/{userId}/posts` | User's posts | Optional |

### 4.16 Scripts & Stories (Character Scripting)

| Method | Path | Description | Auth |
|---|---|---|---|
| `POST` | `/script` | Create/save script | Required |
| `GET` | `/{scriptId}/versions/{versionNumber}` | Get script version | Required |
| `POST` | `/{scriptId}/versions/{versionNumber}/restore` | Restore version | Required |
| `GET` | `/{scriptId}/characters` | Characters using script | Required |
| `POST` | `/{scriptId}/character/{characterId}` | Link script to character | Required |
| `POST` | `/story` | Create story | Required |
| `GET` | `/{storyId}/draft` | Get story draft | Required |
| `PUT` | `/{storyId}/draft` | Save story draft | Required |
| `POST` | `/{storyId}/comments/{commentId}` | Comment on story | Required |
| `POST` | `/{storyId}/comments/{commentId}/rate` | Rate story comment | Required |

### 4.17 Tags

| Method | Path | Description | Auth |
|---|---|---|---|
| `GET` | `/tags` | List tags | Optional |
| `GET` | `/tags/suggest` | Tag suggestions | Optional |
| `PATCH` | `/tag-preferences` | Update tag preferences | Required |

### 4.18 Miscellaneous Backend

| Method | Path | Description | Auth |
|---|---|---|---|
| `GET` | `/global` | Global settings/config | Optional |
| `GET` | `/home` | Home screen data | Optional |
| `GET` | `/homepage` | Homepage feed | Optional |
| `POST` | `/recsys` | Recommendation system event | Optional |
| `POST` | `/events` | Track events | Optional |
| `POST` | `/polls` | Create poll | Required |
| `POST` | `/gifs/share` | Share GIF | Required |
| `GET` | `/gifs/item/{contentType}/{slug}` | Get GIF item | Optional |
| `POST` | `/hmac` | Get HMAC hash | Required |
| `GET` | `/search` | Global search | Optional |
| `GET` | `/autocomplete` | Autocomplete | Optional |
| `POST` | `/comment` | Post comment | Required |
| `DELETE` | `/comment/{commentId}` | Delete comment | Required |
| `POST` | `/like/comment/{commentId}` | Like comment | Required |
| `POST` | `/react/comment/{commentId}` | React to comment | Required |
| `GET` | `/emoji-definitions` | Get emoji definitions | Optional |
| `POST` | `/flower-hunt` | Flower hunt event | Optional |
| `POST` | `/progress` | Progress tracking | Required |
| `GET` | `/app-beta/status` | Beta status | Required |
| `POST` | `/app-beta/signup` | Sign up for beta | Required |
| `DELETE` | `/app-beta/cancel` | Cancel beta | Required |
| `GET` | `/app-update/policy` | App update policy | Optional |
| `GET` | `/creator-verification` | Creator verification | Required |
| `POST` | `/deletion` | Initiate deletion | Required |
| `POST` | `/deletion/cancel` | Cancel deletion | Required |
| `GET` | `/customization/draft` | Get customization draft | Required |
| `PUT` | `/customization/draft` | Save customization draft | Required |
| `GET` | `/customization/live` | Get live customization | Optional |
| `POST` | `/customization/publish` | Publish customization | Required |
| `GET` | `/customization/history` | Customization history | Required |
| `GET` | `/customization/editor-state` | Editor state | Required |
| `GET` | `/customization/{customizationId}` | Get specific customization | Optional |

---

## 5. Age Verification

Active for country codes: `BR`, `AU`, `GB`.

| Method | Path | Description |
|---|---|---|
| `GET` | `/age-verification/status` | Check age verification status |
| `PUT` | `/age-verification/verify` | Submit age verification (document) |
| `POST` | `/age-verification/verify` | Verify age (OTP) |

---

## 6. Notifications API — `https://janitorai.com/notifs/mobile`

All routes relative to `https://janitorai.com/notifs/mobile`.

| Method | Path | Description | Auth |
|---|---|---|---|
| `POST` | `/api/device-token` | Register device push token | Required |
| `GET` | `/api/notifications/{userId}` | List notifications | Required |
| `GET` | `/api/notifications/count/{userId}` | Unread count | Required |
| `POST` | `/api/notifications/read-all/{userId}` | Mark all read | Required |
| `POST` | `/api/notifications/archive-all/{userId}` | Archive all | Required |
| `POST` | `/api/notifications/{notificationId}/read` | Mark single read | Required |
| `POST` | `/api/notifications/{notificationId}/archive` | Archive single | Required |
| `GET` | `/api/preferences/{userId}` | Get notification prefs | Required |
| `PUT` | `/api/preferences/{userId}/{workflow}` | Update notification pref | Required |

Device token body:
```json
{
  "token": "fcm_token_string",
  "platform": "android"
}
```

---

## 7. LLM Generation API — `https://janitorai.com/mobile`

This is the AI chat generation service. All calls use `Authorization: Bearer <jwt>`.

### 7.1 Generate

> ✅ **Superseded in part by §18 (captured live 2026-09-23).** There are *two*
> generation paths, and only one of them streams from Janitor. On the reverse-proxy
> path `/generateAlpha` returns an **assembled OpenAI payload** that the client then
> sends to the user's own proxy — it does not generate. On the JLLM / router path it
> upgrades to a **WebSocket**. The request envelope, the `generateMode` values and the
> message write sequence are all in §18. The description below is the original
> static-analysis reading of the mobile endpoint; the paths still look right, the
> "SSE" framing does not.


```
POST https://janitorai.com/mobile/generateAlpha
Authorization: Bearer <access_token>
Content-Type: application/json
Accept: text/event-stream
```

Request body:
```json
{
  "generation_settings": { ... },
  "source": "mobile",
  "bad_words": ["..."],
  "openai_model": "string|null",
  "claude_model": "string|null",
  "selected_proxy_config_id": "uuid|null",
  "stream": true
}
```

The `generation_settings` object contains the full prompt context assembled from character data, conversation history, lorebooks, and persona.

#### `generation_settings` — ✅ Verified 2026-09-23

**This closes the long-standing gap.** The keys were previously assumed to be
OpenAI-standard sampler params; they are not. Recovered live from
`GET /mb/profiles/mine` → `config.generation_settings`, which is the same object the
client sends back up. Exact keys and observed defaults:

```json
{
  "top_k": 2,
  "top_p": 0,
  "temperature": 0.9,
  "prefill_text": "",
  "max_new_token": 5000,
  "context_length": 60160,
  "enable_thinking": true,
  "prefill_enabled": false,
  "enable_reasoning": true,
  "frequency_penalty": 0,
  "repetition_penalty": 0,
  "enable_reasoning_chat": false,
  "enable_short_responses": false,
  "enable_router_temperature": false
}
```

Traps — the assumed names were wrong in ways that would have failed silently:

- **`max_new_token` is singular.** Not `max_tokens`, not `max_new_tokens`.
- **`repetition_penalty` and `frequency_penalty` are both present** and distinct.
- `context_length`, not `max_context` or `context_size`.
- `top_p: 0` and `top_k: 2` are real observed values, not placeholders — don't
  "correct" them to 1.0/40.
- The `enable_*` booleans are Janitor-specific features (thinking, reasoning, short
  responses, router temperature) with no OpenAI equivalent.
- Values are read off **one** account. Ranges and server-side validation are still
  unknown; treat the defaults as illustrative, the **keys** as confirmed.

Because this object is stored on the user's profile and echoed to the generation
endpoint, Butler reads it from `/profiles/mine` rather than constructing one.

Response: Server-Sent Events stream.  
SSE `data:` lines contain delta text chunks.  
When `content-type: application/json` is returned instead of `text/event-stream`, the full response is in `response.json()`.

Response headers of interest:
- `x-model-version` — `v1` or `v2`
- `x-script-applied` — `true` if script modified output
- `x-script-ids` — comma-separated script IDs
- `x-generation-request-id` — request UUID for correlation
- `x-error-code` — error code on failure
- `x-error-retryable` — `true` if client should retry
- `x-upstream-status` — upstream HTTP status

Error codes returned in response body or `x-error-code`:
- `ENHANCEMENT_CREDITS_EXHAUSTED` — 402 status
- `JANITOR_ROUTER_INSUFFICIENT_BALANCE` — wallet empty
- `JANITOR_ROUTER_DISABLED`
- `JANITOR_ROUTER_MODEL_UNAVAILABLE`
- `JANITOR_ROUTER_CONFIG_UNAVAILABLE`

### 7.2 Rate Generation

```
POST https://janitorai.com/mobile/generateAlpha/rating
Authorization: Bearer <access_token>
Content-Type: application/json

{
  "request_id": "uuid",
  "rating": "positive|negative"
}
```

### 7.3 Usage Budget

```
GET https://janitorai.com/mobile/generateAlpha/budget
Authorization: Bearer <access_token>
```

Response:
```json
{
  "enhancement_credits": {
    "remaining": 300,
    "total": 300,
    "reset_at": "ISO8601"
  }
}
```

### 7.4 Cancel Generation

```
POST https://janitorai.com/mobile/generateAlpha/cancel
Authorization: Bearer <access_token>

{
  "request_id": "uuid"
}
```

### 7.5 OpenAI Reverse Proxy (User-Configured)

When `source = "openai"` and user has set a custom proxy:
```
POST <user_configured_open_ai_reverse_proxy_url>
Authorization: Bearer <reverseProxyKey>
Content-Type: application/json
HTTP-Referer: https://janitorai.com
X-Title: janitor
Accept: text/event-stream
```
Body follows OpenAI chat completions format.

### 7.6 Model Availability
AI models referenced in the bundle:
- `gpt-4o-mini-realtime-preview`
- `gpt-4o-mini-realtime-preview-2024-12-17`
- `gpt-4o-realtime`
- `gpt-4o-realtime-preview-2024-10-01`
- `gpt-4o-realtime-preview-2024-12-17`

The catalog endpoint (`GET /janitor-router/catalog`) returns the full list of available models.

---

## 8. Supabase REST API — `https://auth.janitorai.com/rest/v1`

Standard PostgREST interface. Tables accessed via `apikey` header + Bearer token.

Headers for all REST calls:
```
apikey: <SUPABASE_ANON_KEY>
Authorization: Bearer <access_token>
Content-Type: application/json
```

The app primarily uses the Supabase JS client methods (`supabase.from('table').select(...)`) rather than raw HTTP calls. The following tables are referenced in the bundle:

| Table | Operations | Notes |
|---|---|---|
| `chats` | SELECT, INSERT, UPDATE, DELETE | Main chat data |
| `messages` | SELECT, INSERT, UPDATE, DELETE | Chat messages |
| `characters` | SELECT, INSERT, UPDATE, DELETE | Character data |
| `personas` | SELECT, INSERT, UPDATE, DELETE | Persona data |
| `profiles` | SELECT, UPDATE | User profiles |
| `published_chats` | SELECT | Public chat listing |
| `notifications` | SELECT, UPDATE | In-app notifications |

---

## 9. Supabase Realtime Subscriptions

Realtime URL: `https://auth.janitorai.com/realtime/v1` (WebSocket: `wss://auth.janitorai.com/realtime/v1`)

The app defines these channel builders (extracted from module 5294):

### 9.1 Notifications Channel
**Topic**: `notifications:{userId}`  
**Kind**: `notifications`  
**Dedupe cap**: 256  
Subscribes to user-specific notification events. Events trigger notification badge refresh.

### 9.2 Published Chats (Live) Channel
**Topic**: `chat:{chatId}/live`  
**Kind**: `published-chats`  
**Dedupe cap**: 1024  
Subscribes to live reactions and comments on a public chat.  
Events: confetti, reactions, new comments.

### 9.3 Reviews Channel
**Topic**: `character:{characterId}/reviews`  
**Kind**: `reviews`  
**Dedupe cap**: 256  
Subscribes to new reviews on a character.

### 9.4 Private Chats Channel
**Topic**: `private-chats:{chatId}`  
**Kind**: `private-chats`  
**Dedupe cap**: 0 (no dedup)  
Subscribes to real-time updates for an active private chat.  
Events: `new_messages` — new AI-generated messages arrive.

### 9.5 WebSocket Telemetry Counters
The app tracks these WebSocket events for diagnostics:
- `ws.authz.denied`
- `ws.catchup.fetch`
- `ws.connect`
- `ws.dedupe.drop`
- `ws.disconnect`
- `ws.frame.unknown_v`
- `ws.hub.epoch_reset`
- `ws.reconnect.attempt`

---

## 10. Supabase Storage — `https://auth.janitorai.com/storage/v1`

Storage endpoints (via Supabase storage-js):
```
GET    /storage/v1/object/<bucket>/<path>    — download
POST   /storage/v1/object/<bucket>/<path>    — upload
DELETE /storage/v1/object/<bucket>           — delete
GET    /storage/v1/bucket                    — list buckets
POST   /storage/v1/object/upload/sign/<path> — signed upload URL
```

Media served via `https://ella.janitorai.com/media-approved/` and `https://ella.janitorai.com/bot-avatars/`.

---

## 11. CodePush / OTA Updates

The app uses `@revopush/react-native-code-push` v2.5.0 (fork of Microsoft CodePush). The replacement APK should **skip this entirely** — do not implement.

CodePush server: `https://revopush.org/v0.1/public/codepush/`

For documentation purposes:
```
GET  https://revopush.org/v0.1/public/codepush/update_check?deployment_key=<key>&app_version=<ver>&...
POST https://revopush.org/v0.1/public/codepush/report_status/deploy
POST https://revopush.org/v0.1/public/codepush/report_status/download
```

Update check params:
```
deployment_key    string  — from CODEPUSH_KEY_PROD
app_version       string  — semver
build_number      string
package_hash      string  — current bundle hash
asset_hash        string
is_companion      bool
label             string
client_unique_id  string  — device UUID
```

CodePush custom headers (used only for OTA, not for replacement APK):
- `X-CodePush-Plugin-Name`
- `X-CodePush-Plugin-Version`
- `X-CodePush-SDK-Version`

---

## 12. Screen Map & API Calls on Mount

| Screen | Mount Calls |
|---|---|
| Home/Characters List | `GET /characters` (list), `GET /global`, `GET /homepage` |
| Character Detail | `GET /character/{id}`, `GET /character/{id}/count`, `GET /character/{id}/chats` |
| Chat View | `GET /chats/{chatId}`, Realtime subscribe `private-chats:{chatId}` |
| My Chats | `GET /chats/list`, `GET /chats/folders` |
| Personas | `GET /personas` |
| Notifications | `GET /api/notifications/count/{userId}`, `GET /api/notifications/{userId}` |
| Billing | `GET /plans`, `GET /janitor-router/wallet`, RevenueCat `GET /v1/subscribers/{appUserId}` |
| Profile | `GET /profiles/mine`, `GET /me` (Supabase auth) |
| Router Settings | `GET /janitor-router/catalog`, `GET /janitor-router/config`, `GET /api-settings` |
| Published Chats | `GET /chats/public/discover/trending`, `GET /chats/public/character/{id}` |

---

## 13. Business Logic

### 13.1 Character Pagination
- `GET /characters?page=<n>` — integer page, likely 1-indexed
- List screen uses infinite scroll; next page fetched when near bottom

### 13.2 Chat Message Send Flow
1. User types message → optimistic insert to local store
2. `POST /chats/{chatId}/messages` with `{ messages: [{content, is_human: true}] }`
3. `POST https://janitorai.com/mobile/generateAlpha` (streaming)
4. SSE stream consumed; AI text appended chunk-by-chunk
5. On stream close, `POST /chats/{chatId}/messages` with AI response
6. `PATCH /ratings/{chatId}/messages/{messageId}` if user rates response
7. Realtime channel `private-chats:{chatId}` receives `new_messages` event for other clients

### 13.3 Message Reroll / Enhancement (Swipe)
- Calls `/generateAlpha` again with same context but signals a swipe/regen
- Uses `enhancement_credits` from budget. 402 response → show paywall
- `x-script-applied: true` → script modified output, show script indicator

### 13.4 NSFW Gate Logic
Field `allow_mobile_nsfw` on the user preferences controls NSFW visibility.  
`blur_nsfw_images` — boolean from preferences, blurs NSFW thumbnails.  
`is_nsfw` — field on character and chat objects.  
`is_character_nsfw` / `is_image_nsfw` — granular flags.  
NSFW content returns `togglensfw` endpoint path: `/character/{characterId}/togglensfw`.

### 13.5 Wallet / Token Balance
- `GET /janitor-router/wallet` returns available credits
- `walletRevision` field used for optimistic concurrency
- Low balance shows `walletLowBalance` UI state
- Zero balance shows `wallet_unavailable_stack`
- Credits are router-specific (separate from subscription)

### 13.6 Subscription Tier Logic
`has_premium` boolean on user object from backend. Premium features:
- 5× context window (`5x more context`)
- Enhanced swipes (300/month — `enhancement_credits`)
- Priority routing
- Golden badge

`subscription_platform` field: `android` | `ios` | `web`

### 13.7 Retry / Debounce
- Axios cache headers: `x-axios-cache-etag`, `x-axios-cache-last-modified`, `x-axios-cache-stale-if-error`
- Network errors trigger `walletRetry` logic
- `subscription_cap_reached` triggers paywall on generation

### 13.8 Memory System
- `messagesSinceLastSave` tracks messages since last memory save
- `messagesToSummarize` is the count used for summarization call
- `memoryReplaces` — when enabled, old summarized messages are removed from prompt
- `first_messages` / `first_messages_tokens` — initial chat messages with token counts

### 13.9 Lorebook
- `min_messages_sent` — minimum messages before lorebook activates
- `min_current_session_messages` — session minimum
- `min_total_messages_sent` — total minimum
- `messagesAgo` — how many messages back to check
- `example_dialog_tokens` / `scenario_tokens` / `personality_tokens` / `first_message_tokens` / `special_tokens` — token budgets per section

---

## 14. RevenueCat Integration

SDK: RevenueCat (Android API key: `goog_siPqOtUspkByhMcdnLcUxdiehxu`)

Key API calls made by SDK:
```
GET  https://api.revenuecat.com/v1/subscribers/{appUserId}
POST https://api.revenuecat.com/v1/receipts
```

The app calls `syncRevenueCatIdentity` and `syncRevenueCatCustomer` on auth state change.  
`generateRevenueCatAnonymousAppUserId` — creates anonymous ID before login.  
`applyRevenueCatPremiumAccess` — applies entitlement to local state.

---

## 15. Analytics & Telemetry

### Statsig
- Client key: `client-Pqp6U4689ZDU2EmaGPIao5Dze9sHVzyN1E5an9HPsri`
- API: `https://api.statsigcdn.com/v1` and `https://statsigapi.net/v1`
- Exception reporting: `https://statsigapi.net/v1/sdk_exception`
- Events include: `statsig::gate_exposure`, `statsig::config_exposure`, `statsig::layer_exposure`, `statsig::log_event_dropped_event_count`

### Sentry
- DSN: `https://dd89349bcf7aafdd4a7eeac3f5b26e99@o4510467634298880.ingest.us.sentry.io/4510473353363456`
- SDK: `sentry.javascript.browser/1.33.7`
- Endpoint: `https://o447951.ingest.sentry.io/api/4509632503087104/envelope/?sentry_version=7&sentry_key=c1dfb07d783ad5325c245c1fd3725390`

### Firebase
- Firebase Analytics (`logEvent`, `logLogin`, `logSignUp`, etc.)
- Firebase Cloud Messaging (push notifications) via `@fcm.googleapis.com`

### OpenTelemetry (gen_ai)
The app uses OpenTelemetry semantic conventions for AI:
- `gen_ai.usage.input_tokens`
- `gen_ai.usage.output_tokens`
- `gen_ai.usage.total_tokens`
- `gen_ai.usage.input_tokens.cached`
- `gen_ai.usage.cache_creation_input_tokens`
- `gen_ai.usage.cache_read_input_tokens`
- `gen_ai.request.max_tokens`
- `gen_ai.output.messages`

---

## 16. Implementation Notes for Replacement APK

### Must Implement
1. Supabase client: `https://auth.janitorai.com` with the anon key above
2. Axios client: base URL `https://janitorai.com/mb`, Bearer auth interceptor
3. Notifications client: base URL `https://janitorai.com/notifs/mobile`
4. LLM fetch: `https://janitorai.com/mobile/generateAlpha` (SSE streaming)
5. Supabase Realtime: `private-chats:{chatId}` and `notifications:{userId}` channels

### Skip (not needed for basic function)
- CodePush / OTA (bundle is self-contained after patch)
- Play Integrity attestation
- PairIP anti-tamper
- Firebase Analytics / Sentry (no functional dependency)
- Statsig feature flags (safe to default all experiments to off)
- RevenueCat (skip if not implementing payments)

### Auth Storage Key
AsyncStorage key for session: `supabase.auth.token`

### Image URLs
Character avatars: `https://ella.janitorai.com/bot-avatars/<filename>`  
User/media images: `https://ella.janitorai.com/media-approved/<path>`

### Axios Version
App ships `axios@1.19.0`. Uses `x-axios-cache-*` headers for response caching.

---

## 17. Verified response schemas — ✅ 2026-09-23

Observed against the live API with a real authenticated session. Field names and types
are measured. Values are illustrative only — no account data is reproduced here.

Coverage note: every path below returned `200`. Paths from the bundle that returned
`404` on a live call are listed in §17.7 as **corrections**, because the endpoint tables
above still name several routes that do not exist.

### 17.1 `GET /profiles/mine`

```json
{
  "id": "uuid", "avatar": "filename.webp", "name": "string",
  "user_name": "string", "about_me": "string", "is_verified": false,
  "config": {}
}
```

`config` is where the entire AI-routing surface lives — **not** behind `/janitor-router/*`
as §4.12 implies. Its keys:

| Group | Keys |
|---|---|
| Provider | `api`, `open_ai_mode`, `openAiModel`, `claudeModel`, `open_ai_reverse_proxy` |
| Prompts | `llm_prompt`, `open_ai_jailbreak_prompt`, `claude_jailbreak_prompt`, `proxy_global_prompt` |
| Sampling | `generation_settings` (see §7.1), `bad_words` |
| Proxies | `proxyConfigurations[]`, `selectedProxyConfigId` |
| NSFW | `allow_mobile_nsfw`, `blur_nsfw_images`, `bio_preview_images` |
| Chat theming | `chat_custom_background_image`, `..._background_blur`, `..._background_opacity`, `..._font_family`, `..._font_size`, `..._foreground_color`, `..._dialogue_color`, `..._bold_color`, `..._italic_color`, `..._code_color` |
| Cosmetic | `show_pride`, `show_clouds`, `show_swords`, `language` |

`proxyConfigurations[]` = `{id, name, apiUrl, apiKey, model, jailbreakPrompt}`.

> ⚠️ **`apiKey` is the user's real third-party API key, in plaintext.** It arrives on the
> profile response, so it is in memory and in any cached mirror of that response. Never log
> `config`, never write `proxyConfigurations` into the Room mirror, and redact it before any
> diagnostic output.

The chat theming keys are what `ARCHITECTURE.md` §5 anticipates rendering through the
sandboxed WebView path.

### 17.2 `GET /chats/list`

**A different envelope from `/characters`** — offset paging with an explicit `hasMore`:

```json
{ "hasMore": true, "page": 1, "pageSize": 20, "items": [] }
```

`items[]`:

```json
{
  "id": 2987398548,
  "character": { "id": "uuid", "name": "string", "avatar": "filename.webp",
                 "is_deleted": false, "is_force_removed": false, "is_public": true },
  "folder_ids": [],
  "is_public": false,
  "last_message_at": "2026-09-22T07:07:55.675Z",
  "last_message_preview": "string",
  "message_count": 0
}
```

- **Chat `id` is an `int`**, while character / persona / profile ids are UUID strings. Room
  entities must not share a key type.
- `last_message_at` ends in `Z`; character timestamps end in `+00:00`. Normalize on ingest.
- Paginate on `hasMore` — there is no total here.
- The embedded `character` is a **reduced** object (6 fields), not the full character from
  §4.1. Don't reuse the same DTO for both.

### 17.3 Pagination — three different shapes

The main structural surprise, and the reason Paging 3 needs a per-endpoint key rather than
one shared `PagingSource`.

| Endpoint | Envelope | How to page |
|---|---|---|
| `/characters`, `/characters/v2/mine`, `/characters/v2/blocked` | `{data, page, size, total, pagination_limit, total_relation, filtered_total}` | `page++` until short page; `total` is a **lower bound** capped at 10000 |
| `/chats/list` | `{items, page, pageSize, hasMore}` | `page++` while `hasMore` |
| `/chats/character-chats/mine` | `{characters, page, hasMore, totalCharacters, totalChats}` | `page++` while `hasMore` |
| `/personas/mine`, `/tags` | **bare array**, no envelope | not paginated |
| `/chats/publish/mine`, `/chats/public/discover/trending` | `{chats, total}` | `total` is exact here |
| `/notifs/api/notifications/{userId}` | `{notifications, hasMore}` | page while `hasMore` (see §21.3) |
| `/chats/{id}/comments` | `{comments, total, page, page_size, has_more}` | exact `total` **and** `has_more` (see §22.3) |

`/characters/v2/mine` adds `top_custom_tags[]` to the standard character envelope.

### 17.4 Janitor Router

`GET /janitor-router/catalog`:

```json
{
  "enabled": true,
  "default_model_id": "string",
  "groups": [ { "label": "string", "model_ids": ["string"] } ],
  "models": [ {
    "model_id": "string", "display_name": "string", "model_creator": "string",
    "provider": "string", "tier": "string", "price_tier": 0, "price_version": 0,
    "input_price_usd_micros_per_1m": 0, "output_price_usd_micros_per_1m": 0,
    "context_length": 0, "max_completion_tokens": null,
    "is_recommended": false, "logo_key": "string",
    "param_specs": [], "supported_params": [],
    "supports_function_calling": false, "supports_prompt_caching": false,
    "supports_reasoning": false, "supports_temperature": false,
    "supports_vision": false, "supports_web_search": false
  } ]
}
```

Prices are **integer micro-USD per 1M tokens** — divide by 1e6 for USD; don't parse as float.
`max_completion_tokens` is nullable. The per-model `supports_*` flags decide which sampler
controls the settings UI may show for the selected model.

`GET /janitor-router/config`:

```json
{
  "enabled": false, "configured": false, "model_id": null,
  "active_preset_id": null, "favorite_model_ids": [], "forbidden_words": [],
  "model_params": {}, "params": {}, "prefill_prompt": null, "system_prompt": null,
  "show_thinking": false, "web_search_enabled": false
}
```

`GET /janitor-router/cockpit` → `{config, favorite_model_ids, presets[], prompts[]}`, where
`config` is the object above plus `active_preset_modified`. **One call replaces
config + prompt-library + favorites**, so prefer it on the settings screen.

`GET /janitor-router/wallet`:

```json
{
  "wallet_available": false, "can_generate": true, "is_low_balance": false,
  "total_balance_usd_micros": 0, "included_balance_usd_micros": 0,
  "purchased_balance_usd_micros": 0, "pending_topup_total_usd_micros": 0,
  "pending_topups": [], "topup_packs": [], "topups_enabled": false,
  "current_period_ends_at": null,
  "signup_bonus": { "amount_usd_micros": 0, "claimable": false, "claimed": false }
}
```

Balances are micro-USD ints. **§4.13's `GET /wallet` does not exist (404)**, and neither do
its `wallet_available_stack` / `walletRevision` fields — that shape belongs to an older
client. Use this endpoint instead.

**Writes, probed 2026-10-04 (free account):** every write answers
`403 {"error_code":"JANITOR_ROUTER_DISABLED","message":"JanitorRouter requires an active Janitor
Plus subscription."}`, so the shapes below come from the API's own validation messages, not a
successful round trip. `PUT /janitor-router/config` wants the whole config (`enabled must be a
boolean value` when it is missing); `POST /janitor-router/favorites` takes
`{model_id, favorited}` (`favorited must be a boolean value`). Reads (catalog: 85 models in 9
groups, config, wallet with a $5.00 signup bonus marked not claimable) work on any account.

### 17.5 Prompts, API settings, budget

- `GET /prompt-library` → `{prompts: [{id, name, kind, content, created_at, updated_at}]}`
- `GET /api-settings` → `{legacy_config: {...}, materialized: ...}`, where `legacy_config`
  mirrors `profiles/mine.config` (same `generation_settings`, `proxyConfigurations`,
  jailbreak prompts). The same plaintext-`apiKey` warning applies.
- `GET /mobile/generateAlpha/budget` →
  `{has_premium, bypassed, enhancement_credits, rolling, weekly, soft_warning_pct}`.
  `enhancement_credits`, `rolling` and `weekly` were all **null** on a free account, so
  §7.3's `{remaining, total, reset_at}` is not what every account returns — treat all three
  as nullable or the paywall logic will NPE on a free user.

### 17.6 Account, sessions, misc

| Endpoint | Response |
|---|---|
| `GET /auth/account` | `{hasPassword: bool, oauthProviders: [string]}` |
| `GET /auth/sessions` | `{sessions: [{id, ip, device, location, isCurrent, createdAt, lastActiveAt, expiresAt}]}` |
| `GET /profiles/mine/counts` | `{character_count, persona_count, script_count}` |
| `GET /profiles/mine/blocked-content` | `{bots, creators, keywords, tags}` — all arrays |
| `GET /age-verification/status` | `{status, countryCode, isVerified, requiresVerification}` |
| `GET /app-update/policy` | `{severity: string}` |
| `GET /app-beta/status` | `{availability: {android, ios}, signup: {isInvited, platform}}` |
| `GET /chats/folders` | `{folders: []}` |
| `GET /tags` | bare array of `{id, name, slug, description, created_at}` |
| `GET /subscriptions/plans` | `{plans: [{key, entitlements[], packages{}}]}` |
| `GET /profiles/search?search=` | `{data: [...]}` |

Janitor calls characters **"bots"** internally (`blocked-content.bots`) — useful context when
guessing an unverified field name.

### 17.6a Blocking — ✅ verified 2026-10-04

`PATCH /profiles/mine {block_list: {bots, creators, keywords, tags}}` replaces the whole list
(send all four arrays). What goes in, and what `GET /profiles/mine/blocked-content` gives back:

| Key | Sent | Read back |
|---|---|---|
| `bots` | character id strings | the same ids; `GET /characters/v2/blocked?page=1` lists them as full characters |
| `creators` | creator **user id** strings | objects `{id, name, user_name, avatar}`; `avatar` is a bare file under `/avatars/` |
| `keywords` | plain strings | the same strings |
| `tags` | tag ids (ints, as in `/tags`) | the same ids |

Tested by blocking one character, one creator, one tag and one keyword, reading back, and
restoring the empty list (byte-identical afterwards).

### 17.7 Corrections — documented paths that 404 on the live API

These appear in the tables above and **do not exist**. They came from route-registry
fragments read as absolute paths.

| Documented | Reality |
|---|---|
| `GET /personas`, `GET /my_personas` | → `GET /personas/mine` |
| `PATCH /reorder` | → `/personas/reorder`, `/persona-groups/reorder` |
| `GET /wallet` | → `GET /janitor-router/wallet` |
| `GET /global`, `/home`, `/homepage` | 404 — not present |
| `GET /plans`, `GET /subscriptions` | → `GET /subscriptions/plans` |
| `GET /proxy-configs` | 404 at top level — the real CRUD is `/api-settings/proxy-configs/{id}`, see §20.3 |
| `GET /v2/myfollowing` | 404 |
| `GET /emoji-definitions` | 404 |
| `GET /tags/suggest` | 404 |
| `GET /characters/autocomplete?search=` | 400 `"uuid is expected"` — not a text search |
| `GET /characters/v2/mine`, `/characters/v2/blocked` | require `?page=`, else 400 |

**§4.18 Miscellaneous Backend is the least reliable table in this document.** Most of its
entries are suffixes of a module base (e.g. `/counts` is really `/profiles/mine/counts`),
not top-level routes. Verify each before use.

Notifications (`/notifs/mobile/api/...`) returned **403** with a valid backend bearer token,
so that service takes a different credential. Unresolved — verify before building §6.

---

## 18. Chat & generation — ✅ captured from the live web client 2026-09-23

Captured with mitmproxy against the web client. **The web client is not the mobile
client** — see §18.7 — but the request bodies and the chat state machine are the same
service underneath, and this is the only source we have for the write side.

### 18.1 There are two generation paths, not one

Which one runs is decided by `userConfig.api` / `open_ai_mode`, switched by
`PATCH /api-settings {"source": "janitor"}`. They behave *completely differently* and
Butler has to implement both.

| | **Reverse-proxy path** (`open_ai_mode: "proxy"`) | **JLLM / Janitor Router** (`source: "janitor"`) |
|---|---|---|
| Transport | `POST /generateAlpha` → JSON | `GET /generateAlpha` → **HTTP 101, WebSocket** |
| What Janitor returns | An **assembled OpenAI request payload** | The generated tokens, streamed |
| Who calls the model | **The client**, against the user's own proxy | Janitor's servers |
| Where the user's key goes | Client → user's proxy | Not applicable |

> **The single most important finding:** on the proxy path, `/generateAlpha` **does not
> generate anything**. It is a *prompt assembly service*. It returns a ready-to-send
> OpenAI payload and the client posts that to the user's reverse proxy itself.

This is why `ARCHITECTURE.md` §2's conclusion holds — Butler never needs to reverse
engineer Janitor's system prompt, because the server hands the fully assembled prompt
over at runtime.

Assembled payload returned on the proxy path:

```json
{
  "messages": [ { "role": "system|user|assistant", "content": "..." } ],
  "model": "deepseek/deepseek-v4-pro:nitro",
  "max_tokens": 5000,
  "temperature": 0.9,
  "top_k": 2,
  "stream": true,
  "transforms": ["middle-out"]
}
```

Stock OpenAI chat-completions, which is forced: reverse proxies accept nothing else.
`messages[0]` is the assembled system prompt (~5.7 KB) and the rest alternate
`user` / `assistant` over the chat history. `transforms: ["middle-out"]` is an
OpenRouter extension for context overflow.

**Not yet captured:** the WebSocket frames on the JLLM path (the handshake was captured,
the frames were not — the addon lacked a `websocket_message` hook at the time; it has one
now), and the proxy leg itself, which goes to the user's own host and was deliberately
outside the capture filter.

### 18.2 `POST /generateAlpha` request envelope

```json
{
  "chat": { "id": 2990752259, "character_id": "uuid", "user_id": "uuid", "summary": "" },
  "chatMessages": [ /* see 18.3 */ ],
  "profile":  { "id": "uuid", "name": "...", "user_name": "...", "user_appearance": "..." },
  "profiles": [ { "id", "name", "user_name", "appearance", "type" } ],
  "userConfig": { /* profiles/mine.config, plus the keys below */ },
  "generateMode": "NEW | ALTERNATIVE | CONTINUE",
  "generateType": "CHAT",
  "clientPlatform": "web",
  "forcedPromptGenerationCacheRefetch": {
    "character": false, "chat": false, "profile": false, "script": false
  }
}
```

Notes:

- `chat.summary` is the **memory summary** — see §18.5. Empty until summarization runs.
- `userConfig` is `profiles/mine.config` with credentials added: `reverseProxyKey`,
  `openAIKey`, `claudeApiKey`, plus `text_streaming` and `janitor_router_enabled`.
- `forcedPromptGenerationCacheRefetch` implies the **server caches prompt fragments**
  per character/chat/profile/script. Set a flag to force a re-read after an edit.
- `profiles[]` (plural, with a `type`) exists alongside `profile` (singular).

> ⚠️ **`reverseProxyKey` is the user's real third-party API key, in plaintext, on every
> generation request.** It must travel, but must never be logged, never written to the
> Room mirror, and never included in diagnostics. Same for `openAIKey` / `claudeApiKey`.

### 18.3 Message objects

```json
{
  "id": 105117778501,
  "chat_id": 2990752259,
  "character_id": "uuid",
  "message": "...",
  "is_bot": false,
  "is_main": true,
  "created_at": "2026-09-23T01:40:11.265Z",
  "rating": null,
  "metadata": { "persona_id": null, "generation_request_ids": ["http-...-..."] },
  "_localThinkingContent": "...",
  "_localThinkingKey": "tmp:1790127611274"
}
```

- **Message and chat ids are `int`**, not UUIDs. Character/persona/profile ids are UUIDs.
- **`is_main` is the swipe selector**, not "is this the primary message". Selecting a
  variant is `PATCH /chats/{chatId}/messages/{id} {"is_main": true}` with no other field.
- **`_localThinkingContent` / `_localThinkingKey` are client-local** (underscore-prefixed).
  Reasoning is *persisted* in its **own field**, separate from `message`.
  This refines `ARCHITECTURE.md` §5 rather than replacing it: the client still has to
  **extract** the reasoning from the model output — the request sets `enable_reasoning` /
  `enable_thinking`, so the reasoning arrives inside the stream — but once extracted it is
  stored as a sibling field, not left inline. So Butler needs both halves: a parser on the
  way in, and a separate column on the way out. Exactly *how* the reasoning is delimited in
  the stream (a `reasoning` field vs `<think>` tags) is **not yet captured** — that lives on
  the proxy leg and the JLLM WebSocket, neither of which we have frames for.
- `metadata.generation_request_ids` accumulates one id per generation that contributed to
  the message, so a continued message ends up with several.

### 18.4 The three generate modes and how each is persisted

| Mode | UI | Assembled prompt | Persistence |
|---|---|---|---|
| `NEW` | Send | history as-is | `POST` user message, then `POST` bot message |
| `ALTERNATIVE` | Swipe / reroll | identical to `NEW` | `POST` a new bot message, then `PATCH {is_main:true}` to select it |
| `CONTINUE` | "Proceed" | history **plus a trailing `user` instruction** appended after the last assistant turn | **`PATCH` the existing message** with the longer text |

> ✅ **A fourth mode exists: `SUMMARY_FULL`** — see §19.2. Summarization runs through
> this same endpoint, not a separate one.

`CONTINUE` is the one that would have been easy to get wrong: it is **not** a new message.
Observed: an 8-message prompt (vs 6 for `NEW`) whose final element is a ~300-char `user`
instruction, and the existing message's text grew 1260 → 1420 chars via `PATCH`, with a
second entry appended to `generation_request_ids`.

Full send sequence:

```
POST /chats                          {character_id}                     -> 201 (chat id, int)
POST /chats/{id}/messages            user msg,  is_bot:false            -> 201
POST /generateAlpha                  envelope, generateMode:NEW         -> 200
   (proxy path: client posts the returned payload to its own proxy and streams)
POST /chats/{id}/messages            bot msg,   is_bot:true             -> 201
PATCH /chats/{id}/messages/{msgId}   {is_main:true}                     -> 200
```

Generation persists nothing — **the client writes both messages**. Butler's outbox must
own this whole sequence, because a crash between steps leaves the chat inconsistent
server-side.

### 18.5 Memory / summarization

```
PATCH /chats/{chatId}  { "summary": "...", "summary_chat_id": 105118886536 }
```

The client computes the summary and stores it with **the message id it summarized up to**.
That value then rides on `chat.summary` in the next generation request. This is the
concrete mechanism behind the memory-state finding — the client tracks
memory state, the server folds it into the prompt.

✅ **Resolved in §19.2–19.3.** The summary is produced by `/generateAlpha` itself with
`generateMode: "SUMMARY_FULL"`, and "replace old messages" is the per-request flag
`memoryReplacesHistory` — **not** `summary_chat_id`, which only records how far the
summary reaches. What *triggers* summarization is still unknown.

### 18.6 Other write bodies

| Call | Body |
|---|---|
| `POST /personas` | `{name, appearance, avatar, pronouns, groupId}` — **`appearance`**, not `description` |
| `PATCH /personas/{id}/group` | `{groupId}` |
| `POST /persona-groups` | `{name, description, color}` (color is a hex string) |
| `POST /reviews` | `{character_id, content, is_like}` — full review surface in §23.1 |
| `PATCH /profiles/mine` | `{block_list: {bots, creators, keywords, tags}}` — partial update |
| `PATCH /api-settings` | `{source: "janitor"}` — switches to JLLM |

### 18.7 Web vs mobile — read this before porting anything above

| | Web | Mobile |
|---|---|---|
| Backend base | `janitorai.com/hampter` | `janitorai.com/mb` |
| Generation | `/generateAlpha` (root) | `/mobile/generateAlpha` |
| `clientPlatform` | `"web"` | presumably `"mobile"` |
| Realtime | WebSocket `/notifs/ws/hub`, `/notifs/chat-live/{chatId}` | Supabase Realtime per §9 |
| Notifications | `/notifs/api/...` | `/notifs/mobile/api/...` |

The paths differ; the **shapes** are the strong part of this capture. Treat §18 as a
high-confidence reference for bodies and the state machine, and verify the mobile path
before relying on a URL.

The web's realtime is its own WebSocket hub, not Supabase Realtime — so §9's channel list
describes the mobile client only.

### 18.8 `GET /characters/{id}` — full character (✅ verified)

Richer than the list object in §4.1; this is what a character page needs.

```json
{
  "id": "uuid", "name": "...", "avatar": "filename", "raw_avatar": null,
  "chat_name": null,
  "description": "...", "personality": "...", "scenario": "...",
  "example_dialogs": "...", "first_message": "...", "first_messages": ["..."],
  "token_counts": {
    "personality_tokens": 0, "scenario_tokens": 0, "example_dialog_tokens": 0,
    "first_message_tokens": 0, "total_tokens": 0
  },
  "tags": [ { "id", "name", "slug", "description", "created_at" } ],
  "custom_tags": null,
  "stats": { "chat": 0, "message": 0 },
  "creator_id": "uuid", "creator_name": "...", "creator_verified": false,
  "creator_plusbadge": false, "creator_display_prefs": null,
  "is_nsfw": false, "is_public": true, "is_deleted": false, "is_force_remove": false,
  "is_explicit_for_anon": false,
  "allow_proxy": true, "allow_published_chats": true,
  "showdefinition": false, "showDefinitionOverride": false,
  "obscenity_score": 0, "text_obscenity_score": 0,
  "scripts": [], "soundcloud_track_id": null, "silent_publish": null,
  "scheduled_publish_at": null,
  "created_at": "...", "updated_at": "...", "first_published_at": "..."
}
```

Traps:

- **`example_dialogs` is plural** here, while §4.1's create/update body documents
  `example_dialog` (singular). Don't assume one name works for both.
- **`first_message` (singular) and `first_messages` (array) both exist.**
- **`custom_tags` is `null` here but `[]` in the list response** — decode as nullable or
  it will throw on the detail endpoint.
- `token_counts` is precomputed server-side; no need to count tokens client-side.
- `showdefinition` and `showDefinitionOverride` differ only in casing. Both real.

### 18.9 Endpoints seen on web that are absent from this document

`/favorites/character/{id}/count`, `/favorites/myfavorites/{id}`,
`/reviews/settings/{id}`, `/reviews/counts/{id}`, `/reviews/emoji-definitions`,
`/characters/{id}/similar?limit=&offset=`, `/tags/exists?tagSlug=`,
`/chats/public/{slug}/content`, `/chats/public/{id}/viewer|activity|favorite`,
`/chats/homepage?page=`, `/chats/public/history?page=`,
`/profiles/search?mode=foryou&page=`, `/following/myfollowing/{userId}`.

Paths are web (`/hampter`); the mobile equivalents are unverified.

---

## 19. The generation protocol, end to end — ✅ captured 2026-09-23

Section 18 described the request envelope. This section describes what actually carries
it, captured on both paths with WebSocket frames included. **This closes slice 5.**

### 19.1 One request shape, two transports

The single most useful conclusion: the two paths differ **only in transport**. Same
envelope, same `generateMode` values, same persistence. Butler needs one generation
module with a pluggable transport, not two implementations.

```
                        ┌─ JLLM  ── WS frame  ──→ janitorai.com ──→ chunks back on the socket
  build envelope ───────┤
                        └─ proxy ── POST      ──→ janitorai.com ──→ assembled OpenAI payload
                                                        │
                                                        └──→ client POSTs it to the user's
                                                             proxy (verified: openrouter.ai),
                                                             streams SSE from there
```

Both paths finish the same way: the client writes the result back with
`POST /chats/{id}/messages` (or `PATCH` for `CONTINUE` / a summary).

### 19.2 `generateMode` — four values, not three

| Mode | Meaning |
|---|---|
| `NEW` | ordinary send |
| `ALTERNATIVE` | swipe / reroll |
| `CONTINUE` | extend the last message in place |
| **`SUMMARY_FULL`** | **produce a memory summary** |

Summarization is **not a separate endpoint** — it is `/generateAlpha` with a different
mode, on whichever transport is active. The summary streams back like any other
generation and the client then persists it with
`PATCH /chats/{id} {summary, summary_chat_id}`.

Observed summary output is structured plain text, not prose — it began
`CHARACTERS\n  <name>: <facts>…` and ran ~520 chars.

### 19.3 `memoryReplacesHistory` — the toggle the mobile app doesn't have

```json
{ "generateMode": "SUMMARY_FULL", "memoryReplacesHistory": true, ... }
```

This field appears **only on `SUMMARY_FULL` requests** and is absent from `NEW`. It is the
web UI's "summary replaces old messages" switch, and it is **sent per request rather than
stored server-side** — toggling it in the web UI fires no network call at all, confirming
it lives in client state.

> **Correction.** An earlier draft of §18.5 guessed that `summary_chat_id` was what
> implemented "replace old messages". It isn't — `summary_chat_id` only records which
> message the summary covers up to. The replace behaviour is this request flag.

Consequence for Butler: this is a **client-owned feature**. Nothing needs to be enabled
server-side, and the official Android app simply never sends the flag. Butler can offer
both auto-summarization and replace-history on mobile using APIs that already exist.

### 19.4 JLLM transport — WebSocket at `/generateAlpha`

`GET /generateAlpha` → `101 Switching Protocols`, `permessage-deflate`, no subprotocol.
One socket is **multiplexed across requests**, correlated by `requestId`.

**Client → server** — one frame per generation, the §18.2 envelope plus:

| Field | Note |
|---|---|
| `Authorization` | `Bearer <jwt>` **inside the JSON frame**. Browsers can't set headers on a WebSocket, so it moves into the body. |
| `requestId` | **Client-generated** (`req_<uuid>`), and how replies are matched. |
| `memoryReplacesHistory` | only on `SUMMARY_FULL` |

**Server → client** — three frame types, every one carrying `requestId`:

```json
{"type":"meta",  "modelVersion":"janitor-llm", "requestId":"req_…"}
{"type":"meta",  "contextUsagePercent":16, "contextWindowFull":false, "requestId":"req_…"}
{"type":"chunk", "data":"{\"choices\":[{\"delta\":{\"content\":\"Mia\"}}]}", "event":null, "requestId":"req_…"}
{"type":"done",  "requestId":"req_…"}
```

Traps:

- **`data` is double-encoded.** It is a *JSON string* containing a JSON object, so it needs
  a second parse. Its inner shape is an OpenAI streaming chunk —
  `choices[0].delta.content` — so the same delta reader serves both transports.
- The **first `chunk` carries an empty `content`**; don't treat empty as end-of-stream.
- **`type: "done"` is the terminator**, not a sentinel inside `data`. There is no
  `[DONE]` string as in raw OpenAI SSE.
- `meta` arrives **twice** and the second one carries the context gauge
  (`contextUsagePercent`, `contextWindowFull`) — that's the live context meter.
- Because the socket is shared, **frames for two generations interleave**. Always route
  on `requestId`; never assume the next chunk belongs to the request you just sent.

Observed: 245 server frames over one socket — 240 `chunk`, 3 `meta`, 2 `done` — covering a
chat generation and a summarization that overlapped on the same connection.

### 19.5 Proxy transport — `POST /generateAlpha`, then the user's own host

`POST /generateAlpha` returns the assembled OpenAI payload (§18.1). The client then calls
the user's configured proxy itself. Verified end to end:

```
POST /generateAlpha                       -> 200, assembled payload
OPTIONS openrouter.ai/api/v1/chat/completions   -> 204   (CORS preflight, browser only)
POST    openrouter.ai/api/v1/chat/completions   -> 200   text/event-stream, ~150 KB
PATCH  /hampter/chats/{id}                -> 200, summary persisted
```

- The proxy leg is **ordinary OpenAI SSE**, streamed by the client. Butler is a native app,
  so the `OPTIONS` preflight does not apply to us.
- **`SUMMARY_FULL` goes through this same path** — assembled payload, streamed from the
  proxy, then persisted. That is why one implementation covers both transports.
- Janitor never sees the proxy response; only the client does.

> ⚠️ On this path the user's own API key is used against a **third-party host**. The key
> arrives in `userConfig.reverseProxyKey` and must reach only that host — never logged,
> never mirrored, never sent anywhere else. Butler's host allow-list (TRUST.md) has to
> account for the fact that this path contacts a host the *user* nominates.

### 19.6 What Butler needs to build

1. A transport interface with two implementations — WebSocket (JLLM) and
   POST-then-stream-elsewhere (proxy) — behind one generation API.
2. `requestId` routing, generated client-side, so a shared socket stays coherent.
3. A delta reader over `choices[0].delta.content`, reused by both transports; the only
   difference is unwrapping the double-encoded `data` on the WS path.
4. `SUMMARY_FULL` plus `memoryReplacesHistory` — a feature the official mobile app lacks.
5. A context meter from the `meta` frame.
6. Reconnect handling: the socket is long-lived and shared, so a drop mid-generation must
   resume or fail loudly, never hang. This is exactly the failure Butler exists to fix.

### 19.7 Still unverified

- ~~**Everything here is the web client.** Whether the mobile JLLM path also upgrades to a
  WebSocket at the same route is not confirmed.~~ ✅ **Verified 2026-10-04 on the phone:**
  Butler's JLLM transport (the WebSocket at `/mobile/generateAlpha`, `GenerationTransport`)
  streams replies end to end.
- Where reasoning (`enable_thinking` / `enable_reasoning`) surfaces in the stream. The
  captured run produced no reasoning deltas, so how `_localThinkingContent` gets populated
  is still unknown.
- What triggers summarization, and the threshold behind it.
- Error frames. No failure occurred during capture, so the shape of a mid-stream error on
  the WS path is unknown — and that is precisely the case Butler's retry layer must handle.

---

## 20. Settings & prompt library — ✅ captured 2026-09-23

The write side of the AI-config surface. All paths are web (`/hampter`); shapes should
port to `/mb`, paths need verifying.

### 20.1 Switching provider

```
PATCH /api-settings   { "source": "janitor" }    -> JLLM / Janitor Router
PATCH /api-settings   { "source": "proxy"   }    -> user's reverse proxy
```

One field, and it decides which transport §19 uses. `GET /api-settings` returns
`settings.source` alongside `router_enabled` and `selected_proxy_config_id`.

### 20.2 Sampler settings are a partial update

```
PATCH /api-settings   { "generation_settings": { "context_length": 60160, "top_k": 2 } }
```

Only the changed keys are sent, not the whole 14-key object. Butler should do the same —
sending a full object risks clobbering a key added server-side later.

### 20.3 Proxy configurations — corrected path

```
PATCH /api-settings/proxy-configs/{id}
{ "name": "...", "api_url": "...", "api_key": "...", "model": "...", "prompt_id": "uuid" }
```

> **Correction to §17.7.** That section records `GET /proxy-configs` as 404 and concludes
> proxy configs live only inside `profiles/mine.config`. The 404 was right, the conclusion
> was wrong: they have their own CRUD under **`/api-settings/proxy-configs/`**. The
> top-level path simply isn't where they live.

`prompt_id` links a proxy config to a prompt-library entry, so each proxy can carry its own
system prompt.

> ⚠️ `api_key` is the user's real provider key, sent in the clear in this body and echoed
> back by `GET /api-settings`. Never log this request or that response.

### 20.4 Prompt library

```
POST  /prompt-library        { "name": "...", "kind": "system", "content": "..." }   -> 201
PATCH /prompt-library/{id}   { "name": "...", "content": "..." }                     -> 200
```

`kind` is set on create only (`"system"` observed) and omitted on update. `GET` returns
`{prompts: [{id, name, kind, content, created_at, updated_at}]}`.

This is the "pre-prompt" the UI exposes, and it is what `prompt_id` on a proxy config
points at.

---

## 21. Profile, notifications & realtime — ✅ captured 2026-09-23

### 21.1 `PATCH /profiles/mine` is a one-key-at-a-time partial update

The web client sends a single changed field per request, and for anything under `config`
it sends a `config` object containing **only that one key**:

```json
PATCH /profiles/mine   { "about_me": "...", "avatar": "<filename>" }
PATCH /profiles/mine   { "config": { "allow_mobile_nsfw": true } }
PATCH /profiles/mine   { "config": { "disable_custom_css": false } }
PATCH /profiles/mine   { "config": { "bio_preview_images": true } }
PATCH /profiles/mine   { "config": { "is_review_cooldown": true } }
```

**Butler must do the same.** `config` is a large object containing the user's proxy keys
and prompts (§17.1); reading it, mutating one field and sending the whole thing back
risks clobbering keys added server-side later — and means shipping the user's API keys
back over the wire on every toggle. Send one key.

Two `config` keys not present in the §17.1 listing:

| Key | Meaning |
|---|---|
| `disable_custom_css` | Turns off author-supplied CSS on profile/character cards. Directly relevant to the sandboxed-WebView plan in `ARCHITECTURE.md` §5 — **this is a user preference we must honour**, not just a rendering choice. |
| `is_review_cooldown` | Review rate-limit preference. |

### 21.2 Notification preferences — 20 workflows

```
GET /notifs/api/preferences/{userId}              -> { "preferences": [] }
PUT /notifs/api/preferences/{userId}/{workflow}   { "active": true|false }
```

The body is just `{active}`. An empty `preferences` array means "all defaults" — the
server only stores explicit overrides, so the UI must render the full workflow list from
a client-side constant and treat absence as the default.

Complete workflow list (verified — every one returned 200):

| Group | Workflows |
|---|---|
| Characters | `new-character-created`, `character-updated`, `character-favorited`, `character-scheduled-countdown` |
| Chats | `new-public-chat`, `chat-favorited`, `chat-commented`, `chat-comment-replied`, `chat-comment-liked` |
| Scripts | `script-commented`, `script-comment-replied`, `script-comment-liked`, `script-reply-liked` |
| Reviews | `new-review-to-creator`, `review-liked`, `review-pinned` |
| Social | `new-follower`, `new-comment-to-creator`, `comment-liked`, `community-poll-created` |

**Mobile path — ✅ verified 2026-10-04:** `GET /notifs/mobile/api/preferences/{userId}` answers
`{preferences: [{workflow, active, updatedAt, userId}]}` (this account has an explicit row for
all 20), and `PUT /notifs/mobile/api/preferences/{userId}/{workflow} {active}` → `{success: true}`;
toggling `community-poll-created` off and on round-tripped. `POST .../notifications/read-all/{userId}`
and `POST .../notifications/{id}/read` both answer `{success: true}`.

### 21.3 Notifications list

```
GET /notifs/api/notifications/{userId}        -> { "notifications": [], "hasMore": false }
GET /notifs/api/notifications/count/{userId}  -> { "count": 0 }
```

A fourth pagination shape: `{notifications, hasMore}`. Item schema is **not captured** —
the account had no notifications.

### 21.4 Realtime hub — `wss://janitorai.com/notifs/ws/hub`

The web client's realtime is **its own WebSocket hub**, not Supabase Realtime. The URL
carries a client session id: `?client-session=cs_<random>`.

Client frames:

```json
{"type":"subscribe",   "channel":"notifications:{userId}"}
{"type":"subscribe",   "channel":"character:{characterId}/reviews"}
{"type":"unsubscribe", "channel":"character:{characterId}/reviews"}
{"type":"resume",      "channels":[{"channel":"notifications:{userId}","lastSeenSeq":0}]}
```

Two things worth building on:

- **Channel names match §9 exactly** (`notifications:{userId}`,
  `character:{characterId}/reviews`). The naming is shared with the mobile Supabase
  Realtime topics, so §9's channel list is still good even though the transport differs.
- **`resume` with `lastSeenSeq` is a gap-free catch-up mechanism.** On reconnect the
  client replays per-channel sequence numbers and the server sends what was missed. That
  explains the `ws.catchup.fetch` and `ws.hub.epoch_reset` telemetry counters in §9.5.

That second point matters for Butler more than it looks. A reconnect that silently drops
events is exactly the class of bug Butler exists to avoid, and the protocol already hands
us the fix: persist `lastSeenSeq` per channel and always reconnect with `resume`, never a
bare `subscribe`.

**Not captured:** server→client frame shapes. The account received no live events during
the capture, so how a delivered notification or a sequence acknowledgement looks on the
wire is unknown. Subscribe/resume are client-side only in this data.

### 21.5 Mobile caveat

All of §21 is the **web** client. Mobile notification paths are `/notifs/mobile/api/...`
(verified working in §17) and `docs/JANITOR_API.md` §9 says mobile realtime is Supabase Realtime
rather than this hub. The preference workflow names and the `{active}` body should port;
the transport almost certainly does not.

---

## 22. Published chats, reactions & comments — ✅ captured 2026-09-23 (partial)

Web paths (`/hampter`). Read and react are verified; the write side of comments is not.

### 22.1 Reading a published chat

```
GET /chats/public/{slug}/content
```

Note it is keyed by **slug** (`hi-medieval-fantasy-world-rp-2`), not id. Response:

```json
{
  "chat":        { "id", "character_id", "slug", "description", "custom_tags",
                   "is_public", "published_at", "created_at" },
  "character":   { "id", "name", "avatar", "chat_name", "is_nsfw", "is_public",
                   "soundcloud_track_id" },
  "chatMessages": [ /* as §18.3 */ ],
  "creator":     { "user_id", "username", "is_verified", "plusbadge", "display_prefs" },
  "publisher":   { "user_id", "username", "avatar", "is_verified", "plusbadge", "display_prefs" },
  "persona": null, "personas": [], "customization": null, "share_persona": false
}
```

`creator` (who made the character) and `publisher` (who published the chat) are **separate
people** — don't collapse them.

Supporting calls: `GET /chats/public/{id}/viewer` → `{is_favorited, user_reactions[]}`,
plus `/activity`, `/favorite`, and `POST /chats/public/{id}/view` to register a view.

### 22.2 Reactions are per-message, not per-chat

```
POST /chats/public/{id}/react   { "emoji": "5683-soopmoji-3", "message_id": 55808067807 }
-> 201 { "success": true }
```

Despite the chat-scoped URL, the body carries **`message_id`** — reactions attach to an
individual message inside the published chat. `emoji` is an **id from
`GET /chats/emoji-definitions`**, not a unicode character; custom emoji exist
(`soopmoji`), so the picker must be driven by that endpoint rather than a system keyboard.

The viewer's own reactions come back on `GET .../viewer` as `user_reactions[]`.

### 22.3 Comments

```
GET /chats/{chatId}/comments?limit=20&page=1
-> { "comments": [...], "total": 152, "page": 1, "page_size": 20, "has_more": true }
```

A **fifth pagination envelope** — `{comments, total, page, page_size, has_more}`. It
carries both an exact `total` and `has_more`.

Comment shape:

```json
{
  "id", "chat_id", "content", "created_at", "deleted_at",
  "message_id", "parent_id",
  "like_count", "is_liked_by_user", "reply_count",
  "reactions", "user_reactions",
  "user_id", "user_name", "user_avatar", "user_verified", "user_plusbadge",
  "user_display_prefs"
}
```

Two structural points:

- **`message_id`** — comments anchor to a *message*, like reactions do. A published chat's
  comment thread is therefore per-message, not one flat list.
- **`parent_id`** — comments are **threaded**; replies nest under a parent.
- `deleted_at` is present rather than the row being removed, so deleted comments still
  arrive and must be filtered or tombstoned client-side.

### 22.3a Verified on `/mb` — 2026-10-04

- **Writes need an app-like User-Agent.** With `curl/…` or a Chrome UA, every social write
  (`react`, comments, likes, even the documented review like) answers `401 Unauthorized`
  while the same token reads fine; with `okhttp/4.12.0` (what Butler sends) they all pass.
- `GET /chats/public/{id}/activity` → `reactions: [{count, emoji, message_id}]` (the whole
  chat's tallies, one row per emoji per message) plus `stats`. `GET …/viewer` →
  `user_reactions: [{emoji, message_id}]`. Both routes also answer anonymously.
- `POST /chats/public/{id}/react {emoji, message_id}` → 201 `{success:true}`; it is **not a
  toggle** (a second POST leaves the reaction). `DELETE` the same path with the same body →
  200 `{success:true}` removes it.
- `POST /chats/{chatId}/comments {content}` → 201 with the full comment row (`user_avatar` is a
  full URL here). `DELETE /chats/comments/{commentId}` → 200 `{success:true}` on your own.
  `DELETE /chats/{chatId}/comments/{id}` is 404.
- `POST /chats/comments/{id}/like` → 201 `{like_count, message:"Comment liked"}`, idempotent;
  `DELETE` the same path → 200 `{like_count, message:"Comment unliked"}`.

### 22.4 Not captured

- ~~`POST /chats/{chatId}/comments`~~ — ✅ **captured, see §23.4.** The earlier guess that it
  takes `content` + `message_id` + `parent_id` was wrong: a top-level comment sends
  **`content` alone**.
- Liking a comment, reacting to a comment, deleting one, replying.
- Publishing your own chat (`POST /chats/{id}/publish`) and `/confetti`.
- **`/notifs/chat-live/{chatId}` frames.** The 101 handshake was captured but not the
  frames — it happened before the addon had a WebSocket hook. Given §21.4, expect the same
  hub protocol (subscribe / resume with `lastSeenSeq`), but that is unverified.

These are all the *social* surface, which `README.md` defers out of v1 — so the gap is not
blocking. Capture them if the social feed is ever brought forward.

---

## 23. Reviews, uploads & token refresh — ✅ captured 2026-09-23

### 23.1 Reviews — full write surface

```
POST   /reviews                        { "character_id", "content", "is_like" }   -> 201
POST   /reviews/like/review/{reviewId} { "data": "" }                             -> 201
POST   /reviews/comment                { "review_id", "content" }                 -> 201
DELETE /reviews/{reviewId}                                                        -> 200
```

Reads: `GET /reviews/{characterId}?page=1&size=20&sortBy=likes|latest|oldest`,
`GET /reviews/counts/{characterId}`, `GET /reviews/settings/{characterId}`,
`GET /reviews/emoji-definitions`.

- `is_like` is a **boolean**, so a review is thumbs-up/down plus text, not a star rating.
- **`POST /reviews/like/review/{id}` sends `{"data": ""}`** — a placeholder body, not a
  meaningful field. Sending `{}` may or may not work; this is what the client sends.
- **Review comments are their own thing.** `POST /reviews/comment` takes `review_id`,
  while chat comments (§22.3) take a `chat_id` in the path and a `message_id` in the body.
  Two separate comment systems — don't unify them.

### 23.2 File upload — three steps, presigned

```
1. POST /upload/uploadFile   { "extension": "webp", "type": "profile-avatar" }
   -> 201 { "url": "<presigned Cloudflare R2 URL>",
            "filename": "profile-avatar-pending/{userId}/{id}.webp" }

2. PUT <url>                 (raw bytes, direct to R2 — never touches janitorai.com)

3. PATCH /profiles/mine      { "avatar": "<filename from step 1>" }
```

- The client **never uploads through Janitor**; step 2 goes straight to object storage.
- **`profile-avatar-pending/`** in the returned path implies moderation: the file is
  pending until approved, which lines up with the `moderation-status` endpoint in §4.14.
  Butler should expect an avatar not to be visible immediately after upload.
- `type` selects the bucket/prefix; `profile-avatar` is the only value observed. Character
  avatars presumably use another, unverified.
- This supersedes the older `POST /media/files` flow described in §4.14 for this surface —
  both exist in the docs, only this one was observed in use.

### 23.3 Token refresh

```
POST /auth/v1/token?grant_type=refresh_token   { "refresh_token": "y2q2vk6eu4km" }
```

Worth noting: the refresh token is a **short opaque string (~12 chars)**, not a JWT. The
access token is a 1453-char JWT; the refresh token is not. Don't size storage or validation
on the assumption that both are JWTs.

Don't make this call from a dev machine while debugging — GoTrue
rotates refresh tokens, so refreshing off-device invalidates the app's copy and silently
signs the device out.

### 23.4 Comment systems — there are two, and only two

Captured in full. Characters have **reviews**, not comments; a character page loads
`/reviews/{characterId}` and there is no character-comment endpoint anywhere in the
capture. So the surface is:

**Published-chat comments**

```
GET  /chats/{chatId}/comments?limit=20&page=1
POST /chats/{chatId}/comments              { "content": "lol" }            -> 201
POST /chats/comments/{commentId}/like      (no body)                       -> 201 { like_count, message }
```

A top-level comment sends **only `content`**. `message_id` and `parent_id` come back on
the response as nulls — they exist for message-anchored comments and threaded replies,
but are omitted when posting at the top level.

**Review comments** — a separate system

```
POST /reviews/comment   { "review_id": "uuid", "content": "mhm" }          -> 201
```

Response: `{id, review_id, user_id, content, created_at, like_count, dislike_count,
is_liked_by_user, is_deleted}`.

Differences that matter if you try to share a model between them:

| | Chat comment | Review comment |
|---|---|---|
| Anchor | `chat_id` in path, optional `message_id` | `review_id` in body |
| Threading | `parent_id` | none observed |
| Reactions | `like_count`, `reactions[]` | `like_count` **and `dislike_count`** |
| Soft delete | `deleted_at` | `is_deleted` |

Different field names for the same concepts on both rows — keep the DTOs separate.

---

## 24. Mobile-path corrections — ✅ verified against `/mb` and `/mobile` 2026-09-23

Checked with the device token, because the web capture proved shapes but not mobile paths.

- **`GET /mb/character/{id}` is a 404. The mobile detail route is `GET /mb/characters/{id}`**
  (plural, 34 fields, same shape as §18.8). §4.1's singular `/character/{characterId}` rows
  are wrong for mobile.
- **`GET /mb/chats/list`, `GET /mb/chats/{id}`, `GET /mb/personas/mine`** return the same
  shapes as their `/hampter` twins (§17.2, §18, §4.6). `GET /mb/chats/{id}` returns
  `{chat, character, chatMessages, personas, fork_source_chat_id}`; `chat` carries
  `summary` and `summary_chat_id`; messages carry `{id, chat_id, created_at, is_bot,
  is_main, message, metadata, rating}` — **no thinking field comes back from the server**,
  so `_localThinkingContent` really is client-local and must live in the client's own store.
- **Persona `pronouns` is an object, not a string enum:**
  `{subjective, objective, possessive, possessivePronoun, reflexive}`. §4.6's
  `"he/him|she/her|they/them|custom"` is wrong. Personas also carry `groupId`, `order`,
  `created_at`, `updated_at`.
- **The mobile proxy path is server-proxied.** `POST /mobile/generateAlpha` with
  `clientPlatform: "mobile"` and a proxy-mode `userConfig` does **not** return an
  assembled payload the way the web does (§18.1). It responds with
  `x-proxy-mode: server` and **streams the completion itself** as `text/event-stream`
  in OpenAI chunk format (`data: {"object":"chat.completion.chunk", "model":
  "deepseek/deepseek-v4-pro", "provider":"BaseTen", "choices":[{"delta":{"content":…}}]}`)
  — Janitor calls the user's proxy with the user's key on the server side. So on mobile
  the client never contacts the proxy host; both paths are POST → SSE from Janitor. This
  is what §7.1's original static-analysis reading described, and it was right for mobile.
  Response headers: `x-context-usage-percent`, `x-jllm-context-window-full`,
  `x-generation-request-id`, `x-proxy-mode`.
  ⚠️ **A proxy-mode POST on mobile is a real generation and costs the user.** Never probe
  it casually; the web's free assemble-only behaviour does not carry over.
- The bare `POST /generateAlpha` (no `/mobile`) is **403 Access Restricted** from a
  non-browser client. Mobile must use the `/mobile` prefix.
- Still unverified: the SSE terminator, and where reasoning deltas appear. (The mobile
  **JLLM** transport is verified working, 2026-10-04.)

## 25. Editing, deleting and choosing replies — ✅ verified on `/mb` 2026-10-03

- **Edit** is `PATCH /chats/{chatId}/messages/{id} {"message": "..."}` with only that
  field. Response `{"success": true}`; the chat detail returns the new text.
- **Delete** is `DELETE /chats/{chatId}/messages` with a JSON body
  `{"message_ids": [int, ...]}`: 1 to 256 positive integers. There is **no**
  per-message route (`DELETE /chats/{id}/messages/{msgId}` is a 404). The official
  client's copy says a delete "will also delete any messages below it"; the server does
  not do that by itself, the client sends every id.
- **`is_main` is never unset by the server.** Selecting a variant with
  `PATCH {is_main: true}` leaves earlier variants `true` as well; two variants of one
  turn were both `is_main: true` after two swipes. No captured official request writes
  `is_main: false`. Butler reads the newest main variant of a turn as the chosen one,
  and writes `false` on the others when the user picks, which the server accepts.
- **Rating** is `PATCH /ratings/{chatId}/messages/{id}` and validates
  `rating` as an integer 1–5 (`{}` returns that validation error). How the official
  client maps its thumbs onto 1–5 is not captured.
  ✅ 2026-10-04: `{"rating": 4}` → **200, empty body**, but the message's own `rating` field in
  `GET /chats/{id}` stays `null` afterwards: the rating is kept somewhere the chat doesn't show
  (feedback). Butler sends 1–5 stars from a reply's sheet and remembers its own copy. The official
  app registers it as `updateMessageRating` (`PATCH /ratings/{{ chatId }}/messages/{{ messageId }}`).

- **`DELETE /chats/{chatId}`** deletes the whole chat — ✅ verified 2026-10-04: 200, after
  which `GET /chats/{chatId}` answers `404 {"message":"Chat not found"}` and the character's
  chat count drops by one. No body.

## 26. Personas on chats — ✅ verified 2026-10-03

- **Starting a chat as a persona:** `POST /chats {"character_id": "...", "persona_id": "..."}`.
  The website sends `persona_id` only when a persona was picked; without it the chat
  plays as the profile (captured 2026-09-23).
- **The profile is a persona.** A chat with `persona_id: null` lists the profile in its
  detail's `personas[]` with `id` = the user id and `name` = the profile name, and that
  name is what fills `{{user}}`. A chat with a persona lists both.
- **Persona avatars** are bare file names served from `https://ella.janitorai.com/avatars/{file}`.
  Profile avatars are full URLs.
- **Switching persona mid-chat is per message** (verified 2026-10-03). Each user line carries
  `metadata.persona_id` (null = the profile); there is no chat-level switch.
  `PATCH /chats/{id} {"persona_id": ...}` answers 200 and changes nothing (re-read showed
  `persona_id: null` and the same `personas[]`). The official app's composer chip just sends
  later lines with a different `persona_id`; Butler does the same and builds the envelope's
  `user_appearance` from the persona of the latest user line.
- `GET /chats/character/{characterId}/persona` returns a bare chat id, not a persona;
  `GET /character/{id}/persona` is a 404 on `/mb`. Neither stores a per-character persona.

## 27. AI settings, prompts and JLLM on `/mb` — ✅ verified 2026-10-03

### 27.1 Two copies, one source of truth

`GET /mb/api-settings` returns `{settings, proxy_configs, prompts, legacy_config, materialized}`.
`settings` + `proxy_configs` are the current model; `legacy_config` is the profile's old
`config` blob (what `GET /profiles/mine` returns and what the generation envelope's
`userConfig` is built from). **Every write to `/api-settings` is mirrored into the legacy
copy in the same response**: `source: janitor` → legacy `api: "janitor"`,
`open_ai_mode: "api_key"`; `source: proxy` → `api: "openai"`, `open_ai_mode: "proxy"`;
selecting a proxy updates `selectedProxyConfigId` and `openAiModel`; sampler values follow.
So: write via `/api-settings`, re-read the profile, keep building envelopes from it.

`PATCH` responses carry everything **except `prompts`** — a missing list is not an empty one.

### 27.2 Routes

| Call | Body | Notes |
|---|---|---|
| `PATCH /api-settings` | `{source}` · `{selected_proxy_config_id}` · `{generation_settings: {key: value}}` | Partial. Unknown keys are silently accepted, so "200" proves nothing on its own. Selection verified by switching and switching back. |
| `POST /api-settings/proxy-configs` | `{name, api_url, api_key, model, prompt_id?}` | name ≤ 255 non-empty; api_url ≤ 2048; api_key ≤ 4096; model ≤ 255; prompt_id a UUID. Returns the full settings. |
| `PATCH /api-settings/proxy-configs/{id}` | any of the above | Captured on the web (§20.3). |
| `DELETE /api-settings/proxy-configs/{id}` | none | Unknown id → `404 Proxy config not found`. Do not send a JSON content type without a body. |
| `GET /api-settings/proxy-configs` | — | **404**: list via `GET /api-settings`. |
| `GET /prompt-library` | — | `{prompts: [{id, name, kind, content, created_at, updated_at}]}` |
| `POST /prompt-library` | `{name, kind, content}` | kind is `system` or `prefill`; content ≤ 500 000. |
| `PATCH /prompt-library/{id}` | `{name, content}` | Unknown id → `404 Prompt not found`. |
| `DELETE /prompt-library/{id}` | none | `{deleted, cleared_live_slot, cleared_preset_ids}`; unknown id → `deleted: false`. |

`proxy_configs[]` = `{id, client_id, name, api_url, api_key, model, position, prompt{…}, created_at, updated_at}`;
`client_id` equals the legacy entry's `id`. `api_key` is returned in clear: never log it.

### 27.3 JLLM over the mobile route

`wss://janitorai.com/mobile/generateAlpha` answers the upgrade with **101**, and a real
generation over it works with the §19.4 frame (envelope + `Authorization` + `requestId`).
The provider is readable from the envelope itself: `userConfig.api == "janitor"`.

### 27.4 Correction to §19.3

`memoryReplacesHistory: true` is sent on **every** generation while the switch is on and
the chat has a summary — `NEW` included (captured: two `NEW` requests carried it), not only
on `SUMMARY_FULL`. History is still sent complete; the server does the replacing using
`chat.summary_chat_id`. A `SUMMARY_FULL` run on JLLM produced a 1 190-char structured
summary, saved with `PATCH /chats/{id} {summary, summary_chat_id: <last message id>}`.


## 28. Community on `/mb` — ✅ verified 2026-10-03 (reads only)

| Call | Returns | Notes |
|---|---|---|
| `GET /reviews/{characterId}?page=N` | bare array, 20 a page | Janitor's "Comments" on a character. `{id, content, created_at, like_count, comment_count, is_pinned, is_liked_by_user, user_profiles{user_name, avatar, is_verified}, …}`. A short page is the last (Dr. Evelyn Reed: 8×20 + 15 = 175, matching the official count). |
| `GET /reviews/comments/{reviewId}` | bare array | Replies: `{id, review_id, content, created_at, like_count, user_profiles}`. |
| `POST /reviews/comment` | 201 | `{review_id, content}` (captured on the web, §23.4). Butler sends it once, never retried. |
| `GET /characters/{id}/similar` | bare array of list-shaped characters | Six on the sample. |
| `GET /chats/public/character/{characterId}` | `{chats, total}` | Published chats: `{id, slug, title, description, message_count, published_at, publisher{username, avatar}, stats{view_count, …}}`. `GET /chats/public?character_id=` is a 400 (wants a number). |
| `GET /chats/public/{slug}/content` | §22.1 shape | Works on `/mb`. |
| `GET {notifs}/api/notifications/{userId}` | `{notifications, hasMore}` | `{id, subject, body, createdAt, isRead, isArchived, workflow, data{chatSlug, characterName, characterAvatar, …}, redirect{url}}`. Another user's id → `{"error":"forbidden"}`. |
| `GET {notifs}/api/notifications/count/{userId}` | `{count}` | Unread. |
| character detail `scripts[]` | lorebooks | `{type: "lorebook", id, title, user_name, is_public, updated_at, message_count}`. |
| list rows `is_proxy_enabled`, `creator_display_prefs.username_color` | | The Proxy filter and the creator's colour come from these. |

Reads at the web paths work on `/mb` too: `GET /favorites/myfavorites/{characterId}` → `false`,
`GET /favorites/character/{id}/count` → `{characterId, favoritesCount}` (3877 = the official "3.88K"),
`GET /following/myfollowing/{userId}` → `false`, `GET /reviews/counts/{id}` → `{likes, dislikes, total}`.
(`/myfavorites/{id}`, `/favorites`, `/v2/myfollowing` are 404: those §4.8 rows are wrong.)

Writes: posting a comment (`POST /reviews {character_id, content, is_like}`) and liking one
(`POST /reviews/like/review/{id} {"data": ""}`) are captured (§23.1). **Favouriting a character
and following a creator are not:** only their read routes and the bundle's `/follow`, `/unfollow`
names are known; the request bodies have never been seen.

## 29. Personas: editing and "default" — ✅ verified 2026-10-03

- **The default persona is the profile.** A chat with no `persona_id` lists the profile as
  its persona with `is_default: true`: name = `profiles/mine.name`, avatar = the profile
  avatar, appearance = **`profiles/mine.profile`** (a top-level text field). There is no
  "set default" request: not in the API, not in the mobile bundle (only a UI name,
  `selectDefaultPersona`). Butler's "Make default" swaps contents instead (see `PersonaSwap`).
- **`PATCH /personas/{id}`** is a full update: the body must carry `id` (a UUID, the persona's
  own) and `appearance`, plus `name`, `avatar`, `pronouns`. A no-op with the current values
  returns the full row `{id, user_id, name, avatar, appearance, pronouns, groupId, order,
  created_at, updated_at, deleted_at}`. `PUT` is 404.
- **`POST /upload/uploadFile` types:** `avatar, bot, image, background-image, profile-pic,
  profile-avatar` (from the 400 for an unknown type). `avatar` returns a bare filename under
  `/avatars/` (persona pictures); `profile-avatar` returns `profile-avatar-pending/{userId}/{id}.webp`.
- **Profile pictures pass a stricter, AI-judged content check** than persona pictures, at the
  `PATCH /profiles/mine {avatar}` step: the same picture was refused once (4xx whose message
  is the judge's reasoning) and accepted on the next try. A refusal changes nothing. Butler
  keeps the swap and shows the persona's picture on the default itself.
- **The profile can't hold pronouns or a persona-folder file,** so Butler keeps them locally
  (`DefaultOrigin`) and hands them back on the next swap. Verified 2026-10-03: Rain→Mona→Rain
  leaves every persona byte-identical to the backup, including Mona's pronouns and original file.
- **The profile holds Janitor's Customize settings** in `config`: `chat_custom_font_size`,
  `chat_custom_bold_color`, `chat_custom_italic_color`, `chat_custom_dialogue_color`,
  `chat_custom_code_color`, `chat_custom_foreground_color`, `chat_custom_font_family`,
  `chat_custom_background_image/blur/opacity`. Butler could sync its Customize page with them.

- **Creating and deleting — ✅ verified 2026-10-04.** `POST /personas {name, appearance, avatar,
  pronouns, groupId}` → **201** and the full row (`avatar: ""` is accepted; `groupId: null` is
  fine). `DELETE /personas/{id}` → **200** with body `true`; the persona leaves `/personas/mine`.
- **Pronouns are lowercase** in the object: she `{she, her, her, hers, herself}`, he
  `{he, him, his, his, himself}`, they `{they, them, their, theirs, themselves}`; `null` is "none".
- **`PATCH /profiles/mine {about_me}`** is accepted on its own (✅ 2026-10-04), like `name`,
  `profile` and `avatar`.

## 30. Generation route: web vs mobile — ✅ verified 2026-10-04

- **`POST https://janitorai.com/generateAlpha`** (the website's route) always answers with the
  assembled OpenAI payload `{max_tokens, messages, model, stream, temperature, top_k?,
  transforms}` as `application/json`, plus `x-context-usage-percent`,
  `x-generation-request-id` and `x-jllm-context-window-full` headers. The client then sends
  it to the user's proxy. Six of six captured website calls (2026-09-23), and Butler's own
  call on 2026-10-04 (`head={"max_tokens":5000,"mess…`).
- **`POST https://janitorai.com/mobile/generateAlpha`** (what Butler used until now) relays
  the proxy's stream instead: its body opened with OpenRouter's own SSE (`data: {"id":"gen-…`,
  `: OPENROUTER PROCESSING`), i.e. Janitor's server made the upstream call.
- Butler uses the web route for proxies (`JanitorConfig.WEB_LLM_BASE`), so it can add
  OpenRouter's `provider` or a preset model before sending. JLLM stays on the mobile socket.
- The direct call must not go through Janitor's OkHttp interceptors: they overwrite
  `Authorization` with Janitor's session (the proxy then answers 401) and add Janitor's `apikey`.
- **One proxy, two ids.** `/api-settings` `proxy_configs[].id` is a UUID; the profile's legacy
  `config.proxyConfigurations[].id` (what generation reads, and `selectedProxyConfigId`) is a
  different client id, linked by `proxy_configs[].client_id`. `/api-settings`
  `selected_proxy_config_id` can be null while the legacy selection is set.
- **Model-only change** (`PATCH /api-settings/proxy-configs/{id} {name, api_url, model, prompt_id}`,
  no key): both stores and the legacy `openAiModel` follow (read back 2026-10-04).
- **OpenRouter accepts Butler's additions:** `provider: {"sort":"throughput"}` streamed a full
  reply. `model: "@preset/roleplay"` reached OpenRouter, which refused with 404
  `No allowed providers are specified` (`failed_routing_step: Filter by Allowed Providers`, 34
  endpoints before it): the preset's own provider settings exclude every endpoint of its model.
- **Customize values** (`config.chat_custom_*`, read 2026-10-04): `font_size`, `background_blur`,
  `background_opacity` are ints (16, 0, 10); colours are hex strings (`#8B9DF2`);
  `font_family` is a CSS font list (`"Lexend", -apple-system, sans-serif`); `background_image` a string.

## 31. Write bodies from the website's own code — read 2026-10-04 (not yet sent by Butler)

Read from janitorai.com's public script chunks (`chat-*.js`, `favorite-*.js`, main bundle).
Requests go through the endpoint registry as `{data: <body>, urlParams: <path params>}`;
`data` is the JSON body.

| Action | Request | Body | Notes |
|---|---|---|---|
| Favourite a character | `POST /favorites/favorite` | `{characterId}` | ✅ 2026-10-04: `myfavorites` false→true, count +1 |
| Unfavourite | `POST /favorites/unfavorite` | `{characterId}` | ✅ back to false, count −1 |
| Follow a creator | `POST /following/follow` | `{userId}` | ✅ `myfollowing/{userId}` false→true |
| Unfollow | `POST /following/unfollow` | `{userId}` | ✅ back to false |
| Fork (branch) a chat | `POST /chats/{chatId}/fork` | `{from_message_id, persona_id?}` | ✅ 2026-10-04 on `/mb`: returns the new chat row (`id`, …, its `summary` copied); copies up to and including that message. `fork_source_chat_id` stays null on the copy. `/hampter` answers "Access Restricted" to scripts |
| Create a folder | `POST /chats/folders` | `{name}` | (mobile app code) |
| Add chats to a folder | `POST /chats/folders/{folderId}/chats` | `{chatIds: [..]}` | |
| Rename a folder | `PATCH /chats/folders/{folderId}` | `{name}` | |
| Remove a chat from a folder | `DELETE /chats/folders/{folderId}/chats/{chatId}` | — | |
| Like / unlike a comment | `POST /reviews/like/review/{id}` | `{"data": ""}` | one call toggles; ✅ `is_liked_by_user` false→true→false, `like_count` ±1 |
| Favourite a published chat | `POST` / `DELETE /chats/public/{chatId}/favorite` | — | |

Verified live 2026-10-04: folder = `{id (UUID), name, chat_count, created_at, updated_at}`;
create answers 201 with it; add answers `{added: n}`; `GET /chats/folders/{id}/list?page&pageSize`
answers the same `{items, page, pageSize, hasMore}` rows as `/chats/list`; every chat row's
`folder_ids` is a list of folder UUIDs (strings, not numbers).

There is no pin or archive route. Butler keeps both as ordinary folders on Janitor, named
`Pinned` and `Archive` (made on first use, hidden from its own folder tabs), so they follow
the account across phones and sign-outs; the phone's `group_marks` rows are rebuilt from
`folder_ids` after each folder refresh. The 2026-09-23 captures hold
none of these writes (their writes: messages, chats, generateAlpha, reviews, api-settings,
prompt library, profile, uploads, notification preferences).

