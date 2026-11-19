package com.example.studyappandroid.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.studyappandroid.domain.model.AvatarExpression
import com.example.studyappandroid.domain.model.AvatarStyle
import com.example.studyappandroid.ui.theme.RoastAmber
import com.example.studyappandroid.ui.theme.RoastCoral
import com.example.studyappandroid.ui.theme.RoastCrimson
import com.example.studyappandroid.ui.theme.RoastIndigo
import com.example.studyappandroid.ui.theme.RoastMint
import kotlinx.coroutines.delay

@Composable
fun RoastAvatar(
    expression: AvatarExpression,
    style: AvatarStyle = AvatarStyle.SPARK,
    size: Dp = 84.dp,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "avatar_anim")
    val bob by transition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bob"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val mouthTalk by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(180, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mouth_talk"
    )

    var isBlinking by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3400L)
            isBlinking = true
            delay(130L)
            isBlinking = false
        }
    }

    val auraColor by animateColorAsState(
        targetValue = when (expression) {
            AvatarExpression.HAPPY, AvatarExpression.PROUD -> RoastMint
            AvatarExpression.EXCITED, AvatarExpression.LAUGHING -> RoastAmber
            AvatarExpression.DISAPPOINTED, AvatarExpression.FACEPALMING, AvatarExpression.SHOCKED -> RoastCrimson
            AvatarExpression.LISTENING, AvatarExpression.SPEAKING -> RoastIndigo
            else -> RoastCoral
        },
        label = "aura"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val cx = w / 2f
            val cy = h / 2f + (if (expression == AvatarExpression.EXCITED) bob * 1.6f else bob * 0.7f)
            val radius = w * 0.36f

            // Listening or Speaking animated rings
            if (expression == AvatarExpression.LISTENING || expression == AvatarExpression.SPEAKING) {
                drawCircle(
                    color = auraColor.copy(alpha = 0.25f),
                    radius = radius * 1.15f * pulse,
                    center = Offset(cx, cy),
                    style = Stroke(width = w * 0.03f)
                )
                drawCircle(
                    color = auraColor.copy(alpha = 0.14f),
                    radius = radius * 1.32f * pulse,
                    center = Offset(cx, cy)
                )
            } else {
                drawCircle(
                    color = auraColor.copy(alpha = 0.18f),
                    radius = radius * 1.16f,
                    center = Offset(cx, cy)
                )
            }

            // Style crest / accessory
            when (style) {
                AvatarStyle.SPARK -> {
                    // Antenna
                    drawLine(
                        color = auraColor,
                        start = Offset(cx, cy - radius * 0.95f),
                        end = Offset(cx, cy - radius * 1.24f),
                        strokeWidth = w * 0.035f,
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = auraColor,
                        radius = w * 0.05f,
                        center = Offset(cx, cy - radius * 1.26f)
                    )
                }
                AvatarStyle.FLAME -> {
                    val flame = Path().apply {
                        moveTo(cx - radius * 0.35f, cy - radius * 0.88f)
                        quadraticTo(cx - radius * 0.1f, cy - radius * 1.4f, cx, cy - radius * 1.35f)
                        quadraticTo(cx + radius * 0.25f, cy - radius * 1.3f, cx + radius * 0.35f, cy - radius * 0.88f)
                        close()
                    }
                    drawPath(flame, color = RoastCoral)
                }
                AvatarStyle.SCHOLAR -> {
                    // Mortarboard cap bar
                    drawRoundRect(
                        color = RoastIndigo,
                        topLeft = Offset(cx - radius * 0.7f, cy - radius * 1.14f),
                        size = Size(radius * 1.4f, radius * 0.18f),
                        cornerRadius = CornerRadius(8f, 8f)
                    )
                }
            }

            // Head body
            drawCircle(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF28314E), Color(0xFF171D30))
                ),
                radius = radius,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = auraColor.copy(alpha = 0.8f),
                radius = radius,
                center = Offset(cx, cy),
                style = Stroke(width = w * 0.032f)
            )

            // Faceplate
            drawRoundRect(
                color = Color(0xFF0E1220),
                topLeft = Offset(cx - radius * 0.72f, cy - radius * 0.48f),
                size = Size(radius * 1.44f, radius * 0.96f),
                cornerRadius = CornerRadius(radius * 0.42f, radius * 0.42f)
            )

            val eyeY = cy - radius * 0.12f
            val leftEyeX = cx - radius * 0.32f
            val rightEyeX = cx + radius * 0.32f
            val mouthY = cy + radius * 0.24f

            drawEyes(
                expression = expression,
                isBlinking = isBlinking,
                leftEye = Offset(leftEyeX, eyeY),
                rightEye = Offset(rightEyeX, eyeY),
                radius = radius,
                accent = auraColor
            )

            if (style == AvatarStyle.SCHOLAR) {
                // Glasses frames
                val glassR = radius * 0.23f
                drawCircle(
                    color = Color(0xFFCBD5F5),
                    radius = glassR,
                    center = Offset(leftEyeX, eyeY),
                    style = Stroke(width = w * 0.022f)
                )
                drawCircle(
                    color = Color(0xFFCBD5F5),
                    radius = glassR,
                    center = Offset(rightEyeX, eyeY),
                    style = Stroke(width = w * 0.022f)
                )
                drawLine(
                    color = Color(0xFFCBD5F5),
                    start = Offset(leftEyeX + glassR, eyeY),
                    end = Offset(rightEyeX - glassR, eyeY),
                    strokeWidth = w * 0.022f
                )
            }

            drawMouth(
                expression = expression,
                mouthCenter = Offset(cx, mouthY),
                radius = radius,
                accent = auraColor,
                talkFactor = mouthTalk
            )

            // Facepalm hand overlay
            if (expression == AvatarExpression.FACEPALMING) {
                drawRoundRect(
                    color = RoastCoral.copy(alpha = 0.9f),
                    topLeft = Offset(cx - radius * 0.42f, cy - radius * 0.34f),
                    size = Size(radius * 0.84f, radius * 0.48f),
                    cornerRadius = CornerRadius(14f, 14f)
                )
                for (i in -1..1) {
                    drawLine(
                        color = Color(0xFF1A0600),
                        start = Offset(cx + i * radius * 0.18f, cy - radius * 0.34f),
                        end = Offset(cx + i * radius * 0.18f, cy - radius * 0.06f),
                        strokeWidth = w * 0.02f,
                        cap = StrokeCap.Round
                    )
                }
            }

            // Thinking / Confused extra indicators
            if (expression == AvatarExpression.THINKING) {
                drawCircle(RoastAmber, radius = w * 0.03f, center = Offset(cx + radius * 0.95f, cy - radius * 0.7f))
                drawCircle(RoastAmber, radius = w * 0.045f, center = Offset(cx + radius * 1.12f, cy - radius * 0.95f))
            } else if (expression == AvatarExpression.CONFUSED) {
                drawArc(
                    color = RoastAmber,
                    startAngle = 180f,
                    sweepAngle = 210f,
                    useCenter = false,
                    topLeft = Offset(cx + radius * 0.82f, cy - radius * 1.05f),
                    size = Size(w * 0.14f, w * 0.14f),
                    style = Stroke(width = w * 0.03f, cap = StrokeCap.Round)
                )
            }
        }
    }
}

private fun DrawScope.drawEyes(
    expression: AvatarExpression,
    isBlinking: Boolean,
    leftEye: Offset,
    rightEye: Offset,
    radius: Float,
    accent: Color
) {
    val eyeR = radius * 0.13f
    val strokeW = radius * 0.08f
    if (isBlinking && expression != AvatarExpression.SHOCKED) {
        drawLine(accent, Offset(leftEye.x - eyeR, leftEye.y), Offset(leftEye.x + eyeR, leftEye.y), strokeW, StrokeCap.Round)
        drawLine(accent, Offset(rightEye.x - eyeR, rightEye.y), Offset(rightEye.x + eyeR, rightEye.y), strokeW, StrokeCap.Round)
        return
    }

    when (expression) {
        AvatarExpression.HAPPY, AvatarExpression.LAUGHING -> {
            drawArc(
                color = accent,
                startAngle = 190f,
                sweepAngle = 160f,
                useCenter = false,
                topLeft = Offset(leftEye.x - eyeR, leftEye.y - eyeR * 0.6f),
                size = Size(eyeR * 2, eyeR * 1.5f),
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )
            drawArc(
                color = accent,
                startAngle = 190f,
                sweepAngle = 160f,
                useCenter = false,
                topLeft = Offset(rightEye.x - eyeR, rightEye.y - eyeR * 0.6f),
                size = Size(eyeR * 2, eyeR * 1.5f),
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )
        }
        AvatarExpression.EXCITED -> {
            drawCircle(RoastAmber, radius = eyeR * 1.2f, center = leftEye)
            drawCircle(Color.White, radius = eyeR * 0.45f, center = leftEye + Offset(-2f, -2f))
            drawCircle(RoastAmber, radius = eyeR * 1.2f, center = rightEye)
            drawCircle(Color.White, radius = eyeR * 0.45f, center = rightEye + Offset(-2f, -2f))
        }
        AvatarExpression.SHOCKED -> {
            drawCircle(Color.White, radius = eyeR * 1.4f, center = leftEye)
            drawCircle(Color(0xFF0E1220), radius = eyeR * 0.55f, center = leftEye)
            drawCircle(Color.White, radius = eyeR * 1.4f, center = rightEye)
            drawCircle(Color(0xFF0E1220), radius = eyeR * 0.55f, center = rightEye)
        }
        AvatarExpression.DISAPPOINTED -> {
            drawLine(accent, Offset(leftEye.x - eyeR, leftEye.y - eyeR * 0.4f), Offset(leftEye.x + eyeR, leftEye.y), strokeW, StrokeCap.Round)
            drawLine(accent, Offset(rightEye.x - eyeR, rightEye.y), Offset(rightEye.x + eyeR, rightEye.y - eyeR * 0.4f), strokeW, StrokeCap.Round)
        }
        AvatarExpression.THINKING -> {
            drawCircle(accent, radius = eyeR, center = leftEye + Offset(eyeR * 0.35f, -eyeR * 0.3f))
            drawCircle(accent, radius = eyeR, center = rightEye + Offset(eyeR * 0.35f, -eyeR * 0.3f))
        }
        AvatarExpression.CONFUSED -> {
            drawCircle(accent, radius = eyeR * 1.15f, center = leftEye)
            drawCircle(accent, radius = eyeR * 0.8f, center = rightEye)
            drawLine(
                accent,
                Offset(leftEye.x - eyeR, leftEye.y - eyeR * 1.7f),
                Offset(leftEye.x + eyeR, leftEye.y - eyeR * 2.1f),
                strokeW * 0.8f,
                StrokeCap.Round
            )
        }
        AvatarExpression.PROUD -> {
            drawLine(accent, Offset(leftEye.x - eyeR, leftEye.y), Offset(leftEye.x + eyeR, leftEye.y - eyeR * 0.3f), strokeW, StrokeCap.Round)
            drawLine(accent, Offset(rightEye.x - eyeR, rightEye.y - eyeR * 0.3f), Offset(rightEye.x + eyeR, rightEye.y), strokeW, StrokeCap.Round)
        }
        else -> {
            drawCircle(accent, radius = eyeR, center = leftEye)
            drawCircle(Color.White, radius = eyeR * 0.35f, center = leftEye + Offset(-eyeR * 0.3f, -eyeR * 0.3f))
            drawCircle(accent, radius = eyeR, center = rightEye)
            drawCircle(Color.White, radius = eyeR * 0.35f, center = rightEye + Offset(-eyeR * 0.3f, -eyeR * 0.3f))
        }
    }
}

private fun DrawScope.drawMouth(
    expression: AvatarExpression,
    mouthCenter: Offset,
    radius: Float,
    accent: Color,
    talkFactor: Float
) {
    val mw = radius * 0.45f
    val strokeW = radius * 0.08f
    when (expression) {
        AvatarExpression.SPEAKING -> {
            drawOval(
                color = accent,
                topLeft = Offset(mouthCenter.x - mw * 0.5f, mouthCenter.y - radius * 0.08f * talkFactor),
                size = Size(mw, radius * 0.22f * talkFactor)
            )
        }
        AvatarExpression.SHOCKED -> {
            drawCircle(
                color = accent,
                radius = radius * 0.14f,
                center = mouthCenter,
                style = Stroke(width = strokeW)
            )
        }
        AvatarExpression.LAUGHING, AvatarExpression.EXCITED -> {
            drawArc(
                color = accent,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(mouthCenter.x - mw * 0.65f, mouthCenter.y - radius * 0.12f),
                size = Size(mw * 1.3f, radius * 0.28f)
            )
        }
        AvatarExpression.DISAPPOINTED, AvatarExpression.FACEPALMING -> {
            drawArc(
                color = accent,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(mouthCenter.x - mw * 0.55f, mouthCenter.y - radius * 0.02f),
                size = Size(mw * 1.1f, radius * 0.2f),
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )
        }
        AvatarExpression.CONFUSED, AvatarExpression.THINKING -> {
            drawLine(
                color = accent,
                start = Offset(mouthCenter.x - mw * 0.4f, mouthCenter.y + 2f),
                end = Offset(mouthCenter.x + mw * 0.4f, mouthCenter.y - 4f),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
        }
        else -> {
            drawArc(
                color = accent,
                startAngle = 15f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(mouthCenter.x - mw * 0.6f, mouthCenter.y - radius * 0.1f),
                size = Size(mw * 1.2f, radius * 0.22f),
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )
        }
    }
}
