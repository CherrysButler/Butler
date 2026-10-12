package com.cherry.butler.feature.auth

import com.cherry.butler.core.config.JanitorConfig

/** Which check a person passes: its site key, the action it's for, and what the sheet says about it. */
data class TurnstileCheck(val siteKey: String, val action: String?, val body: String) {
    companion object {
        val SignIn = TurnstileCheck(
            JanitorConfig.TURNSTILE_SITE_KEY,
            null,
            "Janitor asks Cloudflare to check that you're a person before an e-mail sign-in. This usually passes on its own.",
        )
        val CharacterSave = TurnstileCheck(
            JanitorConfig.CHARACTER_TURNSTILE_SITE_KEY,
            JanitorConfig.CHARACTER_TURNSTILE_ACTION,
            "Janitor checks that a person is saving this character, as it does on the website. This usually passes on its own.",
        )
    }
}

/**
 * The page the Turnstile check runs in, shared by the phone's WebView and the desktop's. It
 * reports through a `Butler` object: onToken(token) and onError(code).
 */
internal fun turnstilePage(dark: Boolean, check: TurnstileCheck = TurnstileCheck.SignIn): String = """
<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
<style>html,body{margin:0;padding:0;background:transparent}#w{display:flex;justify-content:center}</style>
<script src="https://challenges.cloudflare.com/turnstile/v0/api.js?onload=butlerReady&render=explicit" async defer></script>
<script>
function butlerReady(){
  turnstile.render('#w',{
    sitekey:'${check.siteKey}',
    ${check.action?.let { "action:'$it'," }.orEmpty()}
    theme:'${if (dark) "dark" else "light"}',
    callback:function(t){Butler.onToken(t)},
    'error-callback':function(c){Butler.onError(String(c))},
    'expired-callback':function(){Butler.onError('expired')}
  });
}
</script></head><body><div id="w"></div></body></html>
""".trimIndent()
