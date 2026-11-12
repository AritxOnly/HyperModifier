package com.aritxonly.myhypermodifier

import android.util.Xml
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.xmlpull.v1.XmlPullParser
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.StringReader

/**
 * A safe, intentionally small visualizer for the media ConstraintSet editor.
 *
 * It reads the constraint fields that have a visible effect in the module's media layout and
 * renders them with representative content. Unsupported attributes remain the responsibility of
 * SystemUI's real ConstraintLayout; this preview never attempts to execute arbitrary XML.
 */
@Composable
fun MediaConstraintSetPreview(
    xml: String,
    defaultCardHeight: Float,
    modifier: Modifier = Modifier,
    notificationRadius: Float = 28f,
) {
    val spec = remember(xml, defaultCardHeight) {
        MediaPreviewConstraintSpec.fromXml(xml, defaultCardHeight)
    }
    val cardHeight = spec.cardHeight.coerceIn(80f, 240f)
    val artSize = spec.artSize.coerceIn(42f, 160f)
    val previewHeight = cardHeight.coerceIn(80f, 180f)
    val coverSize = artSize.coerceIn(48f, 62f)
    val white = Color.White
    val secondaryWhite = white.copy(alpha = 0.62f)
    val trackWhite = white.copy(alpha = 0.24f)

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(previewHeight.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF2B2B34), Color(0xFF121216)))),
        ) {
            if (spec.islandActionsBelow) {
                Column(Modifier.fillMaxWidth().height(previewHeight.dp).padding(12.dp)) {
                    Row(Modifier.fillMaxWidth().height(artSize.dp)) {
                        Box(Modifier.size(artSize.dp)
                            .clip(RoundedCornerShape(MediaLargeCoverStyle.coverRadius(notificationRadius,
                                12f, 12f, artSize, artSize).dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF7D62FF), Color(0xFF232348)))),
                            contentAlignment = Alignment.Center) {
                            Text("♫", color = white, fontSize = 32.sp)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f).height(artSize.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Column(Modifier.weight(1f)) {
                                    Text("Cause you know", color = white, fontSize = 17.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("G.E.M. 邓紫棋", color = secondaryWhite, fontSize = 11.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (spec.showSeamless) Text("⌁", color = secondaryWhite, fontSize = 22.sp)
                            }
                            Spacer(Modifier.weight(1f))
                            Box(Modifier.fillMaxWidth().height(if (spec.showTime) 6.dp else 38.dp), contentAlignment = Alignment.Center) {
                                Box(Modifier.fillMaxWidth().height(6.dp).background(trackWhite)) {
                                    Box(Modifier.fillMaxWidth(.48f).height(6.dp).background(secondaryWhite))
                                }
                            }
                            if (spec.showTime) Spacer(Modifier.height(4.dp))
                            if (spec.showTime) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("02:46", color = secondaryWhite, fontSize = 10.sp)
                                Text("05:54", color = secondaryWhite, fontSize = 10.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("♡", color = secondaryWhite, fontSize = 18.sp)
                        Text("◀", color = white, fontSize = 22.sp)
                        Text("▶", color = white, fontSize = 26.sp)
                        Text("▶", color = white, fontSize = 22.sp)
                        Text("⤨", color = secondaryWhite, fontSize = 18.sp)
                    }
                }
            } else if (spec.largeCover) {
                Row(Modifier.fillMaxWidth().height(previewHeight.dp).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(artSize.coerceAtMost(previewHeight - 22f).dp)
                        .clip(RoundedCornerShape(MediaLargeCoverStyle.coverRadius(notificationRadius, 12f,
                            (previewHeight - artSize) / 2f, artSize, artSize).dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF7D62FF), Color(0xFF232348)))),
                        contentAlignment = Alignment.Center) {
                        Text("♫", color = white, fontSize = 32.sp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f).height((previewHeight - 24f).dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text("Cause you know", color = white, fontSize = 17.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("G.E.M. 邓紫棋", color = secondaryWhite, fontSize = 11.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (spec.showSeamless) Text("⌁", color = secondaryWhite, fontSize = 22.sp)
                        }
                        Row(Modifier.fillMaxWidth().weight(1f),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically) {
                            if (spec.showExtraActions) Text("♡", color = secondaryWhite, fontSize = 20.sp)
                            Text("◀", color = white, fontSize = 22.sp)
                            Text("▶", color = white, fontSize = 26.sp)
                            Text("▶", color = white, fontSize = 22.sp)
                            if (spec.showExtraActions) Text("⤨", color = secondaryWhite, fontSize = 20.sp)
                        }
                        Box(Modifier.fillMaxWidth().height(if (spec.showTime) 6.dp else 38.dp), contentAlignment = Alignment.Center) {
                            Box(Modifier.fillMaxWidth().height(6.dp).background(trackWhite)) {
                                Box(Modifier.fillMaxWidth(.48f).height(6.dp).background(secondaryWhite))
                            }
                        }
                        if (spec.showTime) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("02:46", color = secondaryWhite, fontSize = 11.sp)
                            Text("05:54", color = secondaryWhite, fontSize = 11.sp)
                        }
                    }
                }
            } else Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(previewHeight.dp)
                    .padding(horizontal = if (spec.compact) 9.dp else 15.dp,
                        vertical = if (spec.compact) 9.dp else 12.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(coverSize.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF7D62FF), Color(0xFF232348)))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("♫", color = white, fontSize = 28.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Cause you know",
                            color = white,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "11-G.E.M. 邓紫棋",
                            color = secondaryWhite,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                    if (spec.showSeamless) {
                        Text("⌁", color = secondaryWhite, fontSize = 24.sp)
                    }
                    if (spec.compact) {
                        Text("◀  ▶  ▶", color = white, fontSize = 18.sp)
                    }
                }
                if (!spec.compact) {
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("♡", color = secondaryWhite, fontSize = 22.sp)
                    Text("◀", color = secondaryWhite, fontSize = 18.sp)
                    Text("▶", color = white, fontSize = 25.sp)
                    Text("▶", color = secondaryWhite, fontSize = 18.sp)
                    Text("≋", color = secondaryWhite, fontSize = 21.sp)
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("02:46", color = secondaryWhite, fontSize = 11.sp)
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(trackWhite),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.23f)
                                .height(3.dp)
                                .background(white.copy(alpha = 0.9f)),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("03:48", color = secondaryWhite, fontSize = 11.sp)
                }
                }
            }
        }
        Text(
            text = spec.status,
            color = if (spec.parseError == null) MiuixTheme.colorScheme.onSurfaceVariantSummary
            else MiuixTheme.colorScheme.error,
            style = MiuixTheme.textStyles.footnote1,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

private data class MediaPreviewConstraintSpec(
    val cardHeight: Float,
    val artSize: Float,
    val artStart: Float,
    val artTop: Float,
    val titleBesideArt: Boolean,
    val progressBelowArt: Boolean,
    val actionsBelowProgress: Boolean,
    val showSeamless: Boolean,
    val compact: Boolean,
    val largeCover: Boolean = false,
    val showExtraActions: Boolean = true,
    val showTime: Boolean = true,
    val islandActionsBelow: Boolean = false,
    val parseError: String? = null,
) {
    val status: String
        get() = parseError ?: "已映射卡片、封面、标题、进度条与操作区约束"

    companion object {
        fun fromXml(xml: String, defaultCardHeight: Float): MediaPreviewConstraintSpec {
            val fallback = MediaPreviewConstraintSpec(
                cardHeight = defaultCardHeight,
                artSize = 76f,
                artStart = 16f,
                artTop = 16f,
                titleBesideArt = true,
                progressBelowArt = true,
                actionsBelowProgress = true,
                showSeamless = true,
                compact = false,
            )
            if (xml.isBlank()) return fallback
            return try {
                val constraints = parseConstraints(xml)
                val art = constraints["album_art"].orEmpty()
                val title = constraints["header_title"].orEmpty()
                val progress = constraints["media_progress_bar"].orEmpty()
                val actions = constraints["actions"].orEmpty()
                val background = (constraints["media_bg_view"] ?: constraints["media_bg"]).orEmpty()
                val seamless = constraints["media_seamless"].orEmpty()
                val compact = progress["visibility"] == "gone"
                val islandActionsBelow = (constraints["action2"]?.get("layout_constraintBottom_toBottomOf")
                    ?.let { it.references("media_bg") || it.references("media_bg_view") } == true) &&
                    art["layout_constraintBottom_toTopOf"].references("action2")
                val artSize = if (islandActionsBelow) (defaultCardHeight - 72f).coerceAtLeast(1f)
                    else art["layout_width"].asDpOr(fallback.artSize)
                fallback.copy(
                    cardHeight = background["layout_height"].asDpOr(defaultCardHeight).takeIf { it > 0 } ?: defaultCardHeight,
                    artSize = artSize,
                    islandActionsBelow = islandActionsBelow,
                    artStart = art["layout_marginStart"].asDpOr(fallback.artStart),
                    artTop = art["layout_marginTop"].asDpOr(fallback.artTop),
                    titleBesideArt = title["layout_constraintStart_toEndOf"].references("album_art"),
                    progressBelowArt = progress["layout_constraintTop_toBottomOf"].references("album_art"),
                    actionsBelowProgress = actions["layout_constraintTop_toBottomOf"].references("media_progress_bar"),
                    showSeamless = seamless.isNotEmpty() && seamless["visibility"] != "gone",
                    compact = compact,
                    showTime = constraints["media_elapsed_time"]?.get("visibility") != "gone",
                    showExtraActions = constraints["action0"]?.get("visibility") != "gone" ||
                        constraints["action4"]?.get("visibility") != "gone",
                    largeCover = !compact && (islandActionsBelow || art["layout_width"].asDpOr(0f) >= 88f) &&
                        title["layout_constraintStart_toEndOf"].references("album_art"),
                )
            } catch (_: Throwable) {
                fallback.copy(parseError = "XML 尚未完整，当前显示安全回退布局")
            }
        }

        private fun parseConstraints(xml: String): Map<String, Map<String, String>> {
            val parser = Xml.newPullParser().apply { setInput(StringReader(xml)) }
            val constraints = mutableMapOf<String, Map<String, String>>()
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "Constraint") {
                    val attributes = buildMap {
                        repeat(parser.attributeCount) { index ->
                            put(parser.getAttributeName(index), parser.getAttributeValue(index))
                        }
                    }
                    attributes["id"]?.constraintId()?.let { constraints[it] = attributes }
                }
                parser.next()
            }
            return constraints
        }

        private fun String?.asDpOr(fallback: Float): Float {
            val value = this ?: return fallback
            return DP_VALUE.find(value)?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: fallback
        }

        private fun String?.references(id: String): Boolean = this?.endsWith("/$id") == true

        private fun String.constraintId(): String? = substringAfterLast('/').takeIf { it.isNotBlank() }

        private val DP_VALUE = Regex("(-?\\d+(?:\\.\\d+)?)dp")
    }
}
