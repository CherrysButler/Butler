package com.cherry.butler.feature.auth

import com.cherry.butler.core.config.JanitorConfig

/**
 * The page the Turnstile check runs in, shared by the phone's WebView and the desktop's. It
 * reports through a `Butler` object: onToken(token) and onError(code).
 */
internal fun turnstilePage(dark: Boolean): String = """
<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
<style>html,body{margin:0;padding:0;background:transparent}#w{display:flex;justify-content:center}</style>
<script src="https://challenges.cloudflare.com/turnstile/v0/api.js?onload=butlerReady&render=explicit" async defer></script>
<script>
function butlerReady(){
  turnstile.render('#w',{
    sitekey:'${JanitorConfig.TURNSTILE_SITE_KEY}',
    theme:'${if (dark) "dark" else "light"}',
    callback:function(t){Butler.onToken(t)},
    'error-callback':function(c){Butler.onError(String(c))},
    'expired-callback':function(){Butler.onError('expired')}
  });
}
</script></head><body><div id="w"></div></body></html>
""".trimIndent()
