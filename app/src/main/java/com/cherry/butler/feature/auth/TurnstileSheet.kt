package com.cherry.butler.feature.auth

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.cherry.butler.core.config.JanitorConfig
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.SheetShape
import com.cherry.butler.ui.components.SheetHandle
import com.cherry.butler.ui.components.SheetScrim

/**
 * Cloudflare's Turnstile check, which Janitor's sign-in requires. The widget is a web
 * thing, so it runs in a WebView; the page is given janitorai.com as its origin because
 * the site key is bound to that hostname. Usually it passes by itself in a second or two;
 * when Cloudflare wants a tap, the box shows it. The token comes back through [onToken]
 * and is good for one request within five minutes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TurnstileSheet(onToken: (String) -> Unit, onDismiss: () -> Unit) {
    val dark = !MaterialTheme.colorScheme.background.let { it.red + it.green + it.blue > 1.5f }
    var failure by remember { mutableStateOf<String?>(null) }
    val deliver by rememberUpdatedState(onToken)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = SheetScrim,
        dragHandle = { SheetHandle() },
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            Text("One moment", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = "Janitor asks Cloudflare to check that you're a person before an e-mail sign-in. This usually passes on its own.",
                style = MaterialTheme.typography.bodySmall,
                color = ButlerTheme.colors.textMed,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
            )
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp), contentAlignment = Alignment.Center) {
                TurnstileView(dark = dark, onToken = { deliver(it) }, onError = { failure = it })
            }
            failure?.let {
                Text(
                    text = "The check didn't go through ($it). Close this and try again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ButlerTheme.colors.danger,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TurnstileView(dark: Boolean, onToken: (String) -> Unit, onError: (String) -> Unit) {
    AndroidView(
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                setBackgroundColor(Color.TRANSPARENT)
                webViewClient = WebViewClient()
                addJavascriptInterface(object {
                    @JavascriptInterface fun onToken(token: String) = post { onToken(token) }
                    @JavascriptInterface fun onError(code: String) = post { onError(code) }
                }, "Butler")
                // The origin is what the site key is bound to; the page itself is Butler's.
                loadDataWithBaseURL("https://janitorai.com/", page(dark), "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() },
    )
}

private fun page(dark: Boolean): String = """
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
