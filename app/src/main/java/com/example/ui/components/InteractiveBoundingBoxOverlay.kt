package com.example.ui.components

import android.graphics.RectF
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DetectedAmount
import com.example.ui.theme.EmeraldSuccess
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun InteractiveBoundingBoxOverlay(
    amounts: List<DetectedAmount>,
    isTargetFrameEnabled: Boolean,
    targetFrameRect: RectF,
    onToggleAmount: (String) -> Unit,
    onSelectAmountsInRect: (RectF) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }

    val gestureModifier = modifier
        .fillMaxSize()
        .pointerInput(amounts) {
            detectDragGestures(
                onDragStart = { offset ->
                    dragStart = offset
                    dragCurrent = offset
                },
                onDrag = { change, _ ->
                    dragCurrent = change.position
                },
                onDragEnd = {
                    val start = dragStart
                    val current = dragCurrent
                    if (start != null && current != null) {
                        val left = min(start.x, current.x)
                        val right = max(start.x, current.x)
                        val top = min(start.y, current.y)
                        val bottom = max(start.y, current.y)

                        // Trigger area selection if dragged more than 20px
                        if ((right - left) > 20f && (bottom - top) > 20f) {
                            val normRect = RectF(
                                left / size.width.toFloat(),
                                top / size.height.toFloat(),
                                right / size.width.toFloat(),
                                bottom / size.height.toFloat()
                            )
                            onSelectAmountsInRect(normRect)
                        }
                    }
                    dragStart = null
                    dragCurrent = null
                },
                onDragCancel = {
                    dragStart = null
                    dragCurrent = null
                }
            )
        }

    BoxWithConstraints(
        modifier = gestureModifier
    ) {
        val overlayWidth = constraints.maxWidth.toFloat()
        val overlayHeight = constraints.maxHeight.toFloat()
        val density = LocalDensity.current

        // Target Scan Frame (guides user to align the target column)
        if (isTargetFrameEnabled && overlayWidth > 0 && overlayHeight > 0) {
            val frameLeft = targetFrameRect.left * overlayWidth
            val frameTop = targetFrameRect.top * overlayHeight
            val frameWidth = targetFrameRect.width() * overlayWidth
            val frameHeight = targetFrameRect.height() * overlayHeight

            val fWidthDp = with(density) { frameWidth.toDp() }
            val fHeightDp = with(density) { frameHeight.toDp() }

            Box(
                modifier = Modifier
                    .offset { IntOffset(frameLeft.roundToInt(), frameTop.roundToInt()) }
                    .size(fWidthDp, fHeightDp)
                    .border(2.dp, Color(0xAA38BDF8), RoundedCornerShape(12.dp))
                    .background(Color(0x0A38BDF8))
            ) {
                Text(
                    text = "🎯 CELUJ W KOLUMNĘ SKŁADEK",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF38BDF8),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .background(Color(0xCC0F172A), RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        // Render detected numbers as clean, non-intrusive bounding boxes that NEVER cover the text
        amounts.forEachIndexed { index, item ->
            key(item.id) {
                val rect = item.normalizedRect
                if (rect != null && overlayWidth > 0 && overlayHeight > 0) {
                    val boxLeft = rect.left * overlayWidth
                    val boxTop = rect.top * overlayHeight
                    val boxWidth = max(rect.width() * overlayWidth, 60f)
                    val boxHeight = max(rect.height() * overlayHeight, 28f)

                    val bWidthDp = with(density) { boxWidth.toDp() }
                    val bHeightDp = with(density) { boxHeight.toDp() }

                    val isSelected = item.isSelected
                    val borderColor = if (isSelected) Color(0xFF34D399) else Color(0x5594A3B8)
                    val boxBgColor = if (isSelected) Color(0x1A34D399) else Color(0x05000000)

                    // Clickable transparent highlight box directly around the printed text
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(boxLeft.roundToInt(), boxTop.roundToInt()) }
                            .size(bWidthDp, bHeightDp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(boxBgColor)
                            .border(1.5.dp, borderColor, RoundedCornerShape(6.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onToggleAmount(item.id)
                            }
                            .testTag("amount_box_${item.id}")
                    ) {
                        // Small circular badge pinned to the right edge so it NEVER covers the printed digits
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .offset(x = 10.dp)
                                .size(22.dp)
                                .shadow(2.dp, CircleShape)
                                .clip(CircleShape)
                                .background(if (isSelected) Color(0xFF047857) else Color(0xCC1E293B))
                                .border(1.dp, if (isSelected) Color(0xFF6EE7B7) else Color(0x8894A3B8), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Add,
                                contentDescription = if (isSelected) "Zaznaczona" else "Niezaznaczona",
                                tint = if (isSelected) Color.White else Color(0xFF94A3B8),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }

        // Render finger drag selection rectangle when the user draws on screen
        val start = dragStart
        val current = dragCurrent
        if (start != null && current != null) {
            val left = min(start.x, current.x)
            val top = min(start.y, current.y)
            val width = max(start.x, current.x) - left
            val height = max(start.y, current.y) - top

            val widthDp = with(density) { width.toDp() }
            val heightDp = with(density) { height.toDp() }

            Box(
                modifier = Modifier
                    .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                    .size(widthDp, heightDp)
                    .background(Color(0x2538BDF8), RoundedCornerShape(8.dp))
                    .border(2.dp, Color(0xFF38BDF8), RoundedCornerShape(8.dp))
            ) {
                Text(
                    text = "Zaznacz kolumnę",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .background(Color(0xDD0284C7), RoundedCornerShape(bottomEnd = 6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
