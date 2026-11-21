# Local lorebooks

Open **Settings → Local Lorebooks** for the complete library. **Add Lorebook** below the list
opens the editor. Tap an existing book to edit it or tap its star to mark it as a favorite.
Books created or imported here stay in the library until you enable them in a chat or
attach them to a persona.

**Lore character limit** controls the total lore added to each request across all enabled
books. The default is **24,000 characters**; **0** disables this limit. It is saved on the
device and applies to the next generation. It does not change the
history scan depth.

Open a chat's menu → **Local Lorebooks** → **Create** to open the full-screen editor. Set a name
and default scan depth, then add entries. Tap an existing lorebook in the picker to edit it.

Each entry has an optional name, content, primary keywords (one per line), enabled and
always-active switches, secondary keyword conditions, case/whole-word matching, priority,
an optional scan-depth override and a probability. The entry menu also offers Duplicate
and Delete. **Done** applies entry edits to the book's working copy; **Save** saves the
entire lorebook. Back asks before discarding unsaved changes. Generation uses the saved
version until you save. Editing keeps the book's identity, favorite status and attachments.

In the library, **Import JSON** detects the format from a file or pasted JSON. It accepts
Janitor exports (an array at the root), SillyTavern World Info (`entries` object), and
Character Books (`entries` array), including those embedded in a character card under
`character_book` or `data.character_book`. Import opens a prefilled editor. The book is
added to the library when you press **Save**. The editor suggests the book's name from
JSON or the file name when available. Saving a new lorebook from a chat or persona picker
enables it in that selection.

Janitor entries use their `insertion_order` and per-entry `depth`. Keyword arrays take
precedence over the raw comma-separated fields. Butler imports scripted entries as
disabled text. Script code, minimum message counts, and inclusion groups are unsupported.

The book editor's **⋮** menu beside **Save** lets you export the saved book as a
SillyTavern World Info JSON file or delete it after confirmation.

Use the switches to enable several books. Stars mark favorites, sort them first, and make
them available through the Favorites filter; starring a book does not enable it. Deleting
a book asks for confirmation and removes its bindings everywhere on this device.

In the persona editor, open **Local Lorebooks** to attach books to that persona, including the
default profile. New personas retain their selection when saved. Existing persona bindings
are saved immediately when toggled. Attached books follow the active persona in every chat.
The chat sheet labels them “From persona”; its switches can disable them for this chat.
Switching a book back on removes that chat override. A book selected through both the chat
and persona is processed once.

The library opens from device storage without loading a Janitor profile. The same books,
favorites, and character limit are available across accounts on the device. Bindings stay
with the selected chats and personas, including each account's default persona. The library
and bindings survive app restarts. Activated content is sent to
the generation service as part of the request, so it consumes model context like other lore.
The original JSON and inactive entries are not sent.

## Matching

The implementation follows the basic activation rules in
[SillyTavern's World Info implementation](https://github.com/SillyTavern/SillyTavern/blob/release/public/scripts/world-info.js):

- `constant` entries are always candidates. `disable: true` or `enabled: false` skips an entry.
- Otherwise at least one `key` / `keys` must match recent messages (from either speaker)
  or the preceding round's lore content.
- `selective` uses `keysecondary` / `secondary_keys`: `selectiveLogic` 0 = AND ANY,
  1 = NOT ALL, 2 = NOT ANY, 3 = AND ALL. Constants bypass keyword conditions.
- Plain substring matching ignores case by default. `caseSensitive` / `case_sensitive` and
  `matchWholeWords` are supported, including their Character Book extension forms.
  Whole-word boundaries recognize Unicode letters and numbers.
- The default scan window is four messages. Book `scan_depth` and entry `scanDepth` /
  `extensions.scan_depth` override it, clamped to 0–100 messages.
- Activation proceeds in rounds across all enabled books. The first round scans history;
  each later round scans only the combined content of entries added in the preceding round,
  without applying history scan depth or rescanning history and older rounds. Constants can
  start a chain even with empty history. An entry is consumed once when its keyword
  conditions match, so self-references and cycles cannot add it again. Separate entries
  with identical content remain separate entries.
- `probability` (including the extension form) is a percentage rolled for each request.
  A matched entry gets one attempt; a failed roll is not retried in later rounds.
  `useProbability: false` disables the probability check.
- Within each round, constant entries get budget priority, then higher `order` / `insertion_order`.
  Included entries are presented in ascending order. The **Lore character limit** in
  Settings → Local Lorebooks caps the total content across all enabled books, including separators
  (24,000 characters by default, 0 for unlimited). Entries that do not fit are skipped whole.
  Skipped entries cannot trigger more entries; later rounds use the remaining budget without
  displacing earlier entries. This is a character budget, not an exact model token budget.
- `{{user}}` is filled with the active persona's name. Other macros remain for Janitor.

New replies, alternatives, regeneration, Continue, and Write for me / Enhance match against
the scene's chat history. Butler's instructions and the suggestion draft are excluded from
that scan. When entries activate, the outgoing request carries their text on a copy of the
latest user message and asks Janitor to refresh the chat prompt cache. With a bot-only
opening, lore gets its own temporary user line. The suggestion draft stays last in either
case. Injected lore is never saved to the transcript, outbox, or profile. Highlights,
Choices, and summaries do not request local lore.

This is a basic importer, not full SillyTavern emulation. Regex keys,
insertion positions/depth/roles, vector activation, inclusion groups, timed effects, scripts,
and other advanced extensions are not interpreted. Advanced fields are ignored; regex keys
are treated as literal strings. Import is limited to 2 million JSON characters and 5,000
entries per book; malformed imports report an error without changing the library.

Minimal example:

```json
{
  "name": "My world",
  "entries": {
    "0": { "key": ["castle"], "content": "The castle gates close at sunset." },
    "1": { "constant": true, "content": "This world has two moons." }
  }
}
```
