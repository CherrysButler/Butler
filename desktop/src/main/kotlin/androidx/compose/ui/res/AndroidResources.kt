package androidx.compose.ui.res

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import org.xml.sax.InputSource

/** painterResource(R.drawable.x): the Android vector drawable, read by Compose's own loader. */
@Composable
fun painterResource(id: Int): Painter {
    val density = LocalDensity.current
    val vector = remember(id, density) { Vectors.of(id, density) }
    return rememberVectorPainter(vector)
}

/** Each drawable read once per density for the whole app: an ImageVector is immutable. */
private object Vectors {
    private val loaded = java.util.concurrent.ConcurrentHashMap<Pair<Int, Float>, androidx.compose.ui.graphics.vector.ImageVector>()

    fun of(id: Int, density: androidx.compose.ui.unit.Density) = loaded.getOrPut(id to density.density) {
        val path = com.cherry.butler.R.path(id)
        val stream = requireNotNull(Thread.currentThread().contextClassLoader.getResourceAsStream(path)) { "no resource $path" }
        stream.use { loadXmlImageVector(InputSource(it), density) }
    }
}

@Composable
fun stringResource(id: Int): String = com.cherry.butler.R.text(id)

@Composable
fun stringResource(id: Int, vararg formatArgs: Any): String = com.cherry.butler.R.text(id).format(*formatArgs)
