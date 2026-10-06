package com.cherry.butler.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Pill
import com.cherry.butler.core.markdown.RichHtml
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Gutter and gap shared by every two-up grid, so tiles line up across tabs. */
object TileGrid {
    val gutter = 12.dp
    val gap = 10.dp

    /** The width one tile gets in a two-column grid on this screen. */
    @Composable
    fun tileWidth(): Dp {
        val screen = LocalConfiguration.current.screenWidthDp.dp
        return (screen - gutter * 2 - gap) / 2
    }
}

/** Portrait height for a tile of [width]: a touch taller than square, as Janitor's are. */
private fun portraitHeight(width: Dp): Dp = width * 1.08f

/**
 * The two-up character card, laid out the way Janitor lays it: the name across the top,
 * the picture with the chat count pinned to its corner, then the creator, a few lines of
 * description and the first tags. Every part has a fixed line count so the two cards in
 * a row always end level. The picture is requested at exactly its pixel box.
 */
@Composable
fun CharacterTile(
    name: String,
    avatarUrl: String?,
    width: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    obscured: Boolean = false,
    nsfw: Boolean = false,
    chatCount: String? = null,
    footer: @Composable () -> Unit,
) {
    val height = portraitHeight(width)
    Column(
        modifier = modifier
            .width(width)
            .card(clip = false)
            .clickable(onClick = onClick),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        )
        Box(modifier = Modifier.width(width).height(height).background(ButlerTheme.colors.surfaceHigh)) {
            TilePortrait(url = avatarUrl, name = name, width = width, height = height, obscured = obscured)
            if (chatCount != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                            RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp),
                        )
                        .padding(start = 7.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.ChatBubbleOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = chatCount,
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            if (nsfw) {
                Text(
                    text = "18+",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(ButlerTheme.colors.chrome.copy(alpha = 0.78f), MaterialTheme.shapes.extraSmall)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Column(
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) { footer() }
    }
}

@Composable
private fun TilePortrait(url: String?, name: String, width: Dp, height: Dp, obscured: Boolean) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val wPx = with(density) { width.roundToPx() }
    val hPx = with(density) { height.roundToPx() }
    val request = remember(url, wPx, hPx, obscured) { tilePortraitRequest(context, url, wPx, hPx, obscured) }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = name.firstOrNull()?.uppercase().orEmpty(),
            style = MaterialTheme.typography.headlineMedium,
            color = ButlerTheme.colors.textLow,
        )
        if (url != null) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Low,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private const val OBSCURED_PX = 10

/**
 * The one request a tile's picture is loaded with. [PrefetchTilePortraits] builds the same,
 * so a picture fetched ahead sits in memory under exactly the key the tile will ask for.
 */
fun tilePortraitRequest(context: android.content.Context, url: String?, wPx: Int, hPx: Int, obscured: Boolean): ImageRequest =
    ImageRequest.Builder(context)
        .data(url)
        .size(if (obscured) Size(OBSCURED_PX, OBSCURED_PX) else Size(wPx, hPx))
        .memoryCacheKey(if (obscured) "$url@obscured" else "$url@${wPx}x$hPx")
        .allowHardware(true)
        .build()

/**
 * Fetches and decodes the pictures of the next [AHEAD] tiles below the last one on screen,
 * so cards arrive with their picture instead of filling in a beat later. Each one is asked
 * for once; a fling far ahead simply starts from wherever it lands.
 */
@Composable
fun PrefetchTilePortraits(
    state: androidx.compose.foundation.lazy.grid.LazyGridState,
    count: Int,
    width: Dp,
    portraitAt: (Int) -> Pair<String?, Boolean>?,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val wPx = with(density) { width.roundToPx() }
    val hPx = with(density) { portraitHeight(width).roundToPx() }
    // Bounded: the catalogue pages on without end. A key dropped here at worst asks the
    // loader again, which answers from its cache.
    val asked = remember {
        object : LinkedHashMap<String, Unit>() {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?) = size > ASKED_MAX
        }
    }
    androidx.compose.runtime.LaunchedEffect(state, count, wPx, hPx) {
        androidx.compose.runtime.snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last ->
                if (last < 0) return@collect
                val loader = coil.Coil.imageLoader(context)
                for (i in (last + 1)..minOf(last + AHEAD, count - 1)) {
                    val (url, obscured) = portraitAt(i) ?: continue
                    if (url == null || asked.put("$url@$obscured", Unit) != null) continue
                    loader.enqueue(tilePortraitRequest(context, url, wPx, hPx, obscured))
                }
            }
    }
}

/** About two screens of cards. */
private const val AHEAD = 12

/** Prefetched keys remembered, so a scroll back doesn't ask again. */
private const val ASKED_MAX = 600

/** What a Browse card says under its picture: creator, a few lines about them, tags. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrowseTileFooter(
    creatorName: String,
    creatorVerified: Boolean,
    blurb: String,
    tags: List<String>,
    creatorColor: String? = null,
) {
    // The creator's own username colour, as Janitor shows it, when it reads on this ground.
    val red = MaterialTheme.colorScheme.primary
    val ground = MaterialTheme.colorScheme.surfaceContainer
    val handle = remember(creatorColor, red, ground) {
        creatorColor?.let { RichHtml.parseColor(it) }?.let { Color(it) }
            ?.takeIf { kotlin.math.abs(it.luminance() - ground.luminance()) > 0.3f } ?: red
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "@$creatorName",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = handle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (creatorVerified) {
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Rounded.Verified,
                contentDescription = "Verified creator",
                tint = ButlerTheme.colors.speech,
                modifier = Modifier.size(14.dp),
            )
        }
    }
    Text(
        text = blurb,
        style = MaterialTheme.typography.bodySmall,
        color = ButlerTheme.colors.textMed,
        minLines = 3,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth().height(24.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        maxLines = 1,
    ) {
        tags.take(4).forEach { TagPill(it) }
    }
}

/** A tag as Janitor shows it: an outlined pill. */
@Composable
fun TagPill(text: String, modifier: Modifier = Modifier) {
    val tint = tagTint(text)
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = tint,
        maxLines = 1,
        modifier = modifier
            .border(1.dp, tint.copy(alpha = 0.45f), Pill)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/**
 * A tag's colour: one of the theme's own accents, always the same one for the same tag, so a
 * tag is recognisable at a glance on cards, the character page and the browse tag row alike (the
 * website colours tags too, from a palette of its own). Butler red is left out, as it marks what's
 * chosen. Each theme sets these accents for its own background, so tags read on Daylight as well
 * as on the dark looks. `#` and case don't change a tag's colour.
 */
@Composable
fun tagTint(tag: String): androidx.compose.ui.graphics.Color {
    val c = ButlerTheme.colors
    val accents = listOf(c.speech, c.thought, c.success, c.warn, c.romance, c.desire)
    return accents[Math.floorMod(tag.removePrefix("#").trim().lowercase().hashCode(), accents.size)]
}

/** A card-shaped placeholder: the grid's own silhouette, breathing. */
@Composable
fun SkeletonTile(width: Dp) {
    val alpha = rememberSkeletonAlpha()
    Column(modifier = Modifier.width(width).card()) {
        Column(modifier = Modifier.padding(10.dp)) {
            SkeletonBlock(width = width * 0.6f, height = 12.dp, alpha = alpha)
        }
        SkeletonBlock(width = width, height = portraitHeight(width), radius = 0.dp, alpha = alpha)
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SkeletonBlock(width = width * 0.5f, height = 10.dp, alpha = alpha)
            SkeletonBlock(width = width * 0.85f, height = 10.dp, alpha = alpha)
            SkeletonBlock(width = width * 0.7f, height = 10.dp, alpha = alpha)
        }
    }
}
