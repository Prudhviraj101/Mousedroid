package com.example.mousedroid

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.hypot

class ModernTrackpadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface TrackpadListener {
        fun onMouseMove(dx: Float, dy: Float)
        fun onMouseLeftClick()
        fun onMouseRightClick()
        fun onScroll(deltaY: Int, deltaX: Int)
        fun onLeftDown()
        fun onLeftUp()
        fun onSpecialGesture(action: String)
        fun onZoom(scaleFactor: Float)
    }

    var listener: TrackpadListener? = null

    // Sensitivity & Configuration Settings
    var sensitivity: Float = 1.3f
    var scrollMultiplier: Float = 1.0f
    var invertScroll: Boolean = false
    var tapToClick: Boolean = true
    var smoothingEnabled: Boolean = true

    // Visual Paints
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1C281A")
        style = Paint.Style.FILL
    }

    private val touchRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }

    private val touchFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val scrollGuidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#55FBBF24")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        pathEffect = DashPathEffect(floatArrayOf(14f, 14f), 0f)
    }

    private val tapRipplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    // Touch tracking state
    private class ActiveTouch(
        val id: Int,
        var x: Float,
        var y: Float,
        var startX: Float,
        var startY: Float,
        val startTime: Long
    )

    private val activePointers = mutableMapOf<Int, ActiveTouch>()
    private var lastSentX = 0f
    private var lastSentY = 0f
    private var smoothedDx = 0f
    private var smoothedDy = 0f
    private var lastSentTime = 0L
    private val SEND_INTERVAL_MS = 12L

    // Tap & Drag Gestures
    private val TAP_MAX_DURATION = 240L
    private val TAP_MAX_DISTANCE = 35f
    private var isDragging = false
    private var lastTapTime = 0L
    private val DOUBLE_TAP_TIMEOUT = 280L

    // Scroll state & Kinetic Inertia
    private var isTwoFingerScroll = false
    private var lastScrollY = 0f
    private var lastScrollX = 0f
    private var scrollVelocityY = 0f
    private var scrollVelocityX = 0f

    // 3-finger gesture tracking
    private var threeFingerStartX = 0f
    private var threeFingerStartY = 0f
    private var isThreeFingerGesture = false

    // Scale / Pinch detector for Zoom
    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            if (abs(scaleFactor - 1.0f) > 0.03f) {
                listener?.onZoom(scaleFactor)
                HapticHelper.tick(this@ModernTrackpadView)
                return true
            }
            return false
        }
    })

    // Tap Ripple Animation Data
    private class RippleEffect(
        val x: Float,
        val y: Float,
        var radius: Float = 15f,
        var alpha: Int = 220,
        val color: Int = Color.parseColor("#6EE7B7")
    )
    private val activeRipples = mutableListOf<RippleEffect>()

    init {
        HapticHelper.init(context)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        // 1. Ambient geometric dot grid
        val step = 44f
        var gx = step
        while (gx < w) {
            var gy = step
            while (gy < h) {
                canvas.drawCircle(gx, gy, 1.8f, dotPaint)
                gy += step
            }
            gx += step
        }

        // 2. Center visual scroll track when 2 fingers active
        if (isTwoFingerScroll) {
            val midX = w / 2f
            canvas.drawLine(midX, 60f, midX, h - 60f, scrollGuidePaint)
        }

        // 3. Active tap ripple rings
        val iterator = activeRipples.iterator()
        while (iterator.hasNext()) {
            val ripple = iterator.next()
            tapRipplePaint.color = ripple.color
            tapRipplePaint.alpha = ripple.alpha
            canvas.drawCircle(ripple.x, ripple.y, ripple.radius, tapRipplePaint)
        }

        // 4. Luminous touch halos under active finger positions
        for ((_, touch) in activePointers) {
            val pointerCount = activePointers.size
            val ringColor = when {
                pointerCount >= 3 -> Color.parseColor("#A855F7") // Violet for 3-finger gesture
                pointerCount == 2 -> Color.parseColor("#FBBF24") // Amber for 2-finger scroll
                else -> Color.parseColor("#6EE7B7") // Neon Emerald for 1-finger move
            }

            // Outer soft glow aura
            touchFillPaint.color = ringColor
            touchFillPaint.alpha = 35
            canvas.drawCircle(touch.x, touch.y, 60f, touchFillPaint)

            // Inner touch halo
            touchFillPaint.alpha = 75
            canvas.drawCircle(touch.x, touch.y, 42f, touchFillPaint)

            // Outer crisp stroke
            touchRingPaint.color = ringColor
            touchRingPaint.alpha = 200
            canvas.drawCircle(touch.x, touch.y, 42f, touchRingPaint)

            // Center bright precision dot
            touchFillPaint.alpha = 240
            canvas.drawCircle(touch.x, touch.y, 7f, touchFillPaint)
        }
    }

    private fun spawnRipple(x: Float, y: Float, color: Int) {
        val ripple = RippleEffect(x, y, radius = 15f, alpha = 230, color = color)
        activeRipples.add(ripple)

        ValueAnimator.ofFloat(15f, 120f).apply {
            duration = 340
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val progress = animator.animatedFraction
                ripple.radius = animator.animatedValue as Float
                ripple.alpha = ((1f - progress) * 230).toInt()
                if (progress >= 1f) {
                    activeRipples.remove(ripple)
                }
                invalidate()
            }
            start()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Delegate to pinch zoom detector
        scaleGestureDetector.onTouchEvent(event)

        val actionIndex = event.actionIndex
        val pointerId = event.getPointerId(actionIndex)
        val now = System.currentTimeMillis()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val x = event.getX(actionIndex)
                val y = event.getY(actionIndex)
                activePointers[pointerId] = ActiveTouch(pointerId, x, y, x, y, now)

                if (activePointers.size == 1) {
                    lastSentX = x
                    lastSentY = y
                    smoothedDx = 0f
                    smoothedDy = 0f
                    // Double-tap & hold drag lock
                    if (now - lastTapTime < DOUBLE_TAP_TIMEOUT) {
                        isDragging = true
                        listener?.onLeftDown()
                        HapticHelper.click(this)
                    }
                } else if (activePointers.size == 2) {
                    isTwoFingerScroll = true
                    val p1 = activePointers.values.elementAt(0)
                    val p2 = activePointers.values.elementAt(1)
                    lastScrollX = (p1.x + p2.x) / 2f
                    lastScrollY = (p1.y + p2.y) / 2f
                    scrollVelocityY = 0f
                    scrollVelocityX = 0f
                    HapticHelper.tick(this)
                } else if (activePointers.size == 3) {
                    isThreeFingerGesture = true
                    threeFingerStartX = (event.getX(0) + event.getX(1) + event.getX(2)) / 3f
                    threeFingerStartY = (event.getY(0) + event.getY(1) + event.getY(2)) / 3f
                    HapticHelper.tick(this)
                }
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pId = event.getPointerId(i)
                    activePointers[pId]?.let {
                        it.x = event.getX(i)
                        it.y = event.getY(i)
                    }
                }

                if (now - lastSentTime >= SEND_INTERVAL_MS) {
                    if (event.pointerCount == 1 && !isTwoFingerScroll && !isThreeFingerGesture) {
                        val curX = event.getX(0)
                        val curY = event.getY(0)
                        val rawDx = (curX - lastSentX) * sensitivity
                        val rawDy = (curY - lastSentY) * sensitivity

                        val finalDx: Float
                        val finalDy: Float
                        if (smoothingEnabled) {
                            // Exponential smoothing alpha
                            val alpha = 0.75f
                            smoothedDx = alpha * rawDx + (1f - alpha) * smoothedDx
                            smoothedDy = alpha * rawDy + (1f - alpha) * smoothedDy
                            finalDx = smoothedDx
                            finalDy = smoothedDy
                        } else {
                            finalDx = rawDx
                            finalDy = rawDy
                        }

                        if (abs(finalDx) >= 0.4f || abs(finalDy) >= 0.4f) {
                            listener?.onMouseMove(finalDx, finalDy)
                            lastSentX = curX
                            lastSentY = curY
                            lastSentTime = now
                        }
                    } else if (event.pointerCount == 2 && !scaleGestureDetector.isInProgress) {
                        // 2-Finger Kinetic Scroll
                        val currentMidY = (event.getY(0) + event.getY(1)) / 2f
                        val currentMidX = (event.getX(0) + event.getX(1)) / 2f
                        val dy = currentMidY - lastScrollY
                        val dx = currentMidX - lastScrollX

                        val scrollY = (if (invertScroll) dy else -dy) * scrollMultiplier / 5.5f
                        val scrollX = (dx * scrollMultiplier / 9f).toInt()

                        if (abs(scrollY) >= 0.8f) {
                            scrollVelocityY = scrollY
                            scrollVelocityX = scrollX.toFloat()
                            listener?.onScroll(scrollY.toInt(), scrollX)
                            lastScrollY = currentMidY
                            lastScrollX = currentMidX
                            lastSentTime = now
                            HapticHelper.tick(this)
                        }
                    }
                }
                invalidate()
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val releasedTouch = activePointers.remove(pointerId)
                if (releasedTouch != null) {
                    val duration = now - releasedTouch.startTime
                    val dist = hypot(releasedTouch.x - releasedTouch.startX, releasedTouch.y - releasedTouch.startY)

                    // 2-Finger Tap detection (Right Click)
                    if (event.pointerCount == 2 && duration < TAP_MAX_DURATION && dist < TAP_MAX_DISTANCE) {
                        listener?.onMouseRightClick()
                        spawnRipple(releasedTouch.x, releasedTouch.y, Color.parseColor("#FBBF24"))
                        HapticHelper.heavyClick(this)
                    }
                }

                if (activePointers.size < 2) {
                    isTwoFingerScroll = false
                }
                if (activePointers.size < 3 && isThreeFingerGesture) {
                    // Check 3-finger swipe direction
                    val finalX = event.getX(0)
                    val finalY = event.getY(0)
                    val deltaX = finalX - threeFingerStartX
                    val deltaY = finalY - threeFingerStartY

                    if (abs(deltaY) > abs(deltaX)) {
                        if (deltaY < -120f) {
                            // 3-Finger Swipe Up -> Task View / Windows Overview
                            listener?.onSpecialGesture("HOTKEY:WIN_TAB")
                            HapticHelper.heavyClick(this)
                        } else if (deltaY > 120f) {
                            // 3-Finger Swipe Down -> Show Desktop
                            listener?.onSpecialGesture("HOTKEY:WIN+D")
                            HapticHelper.heavyClick(this)
                        }
                    } else {
                        if (deltaX < -120f) {
                            // 3-Finger Swipe Left -> Switch Desktop Left
                            listener?.onSpecialGesture("HOTKEY:CTRL+WIN+LEFT")
                            HapticHelper.heavyClick(this)
                        } else if (deltaX > 120f) {
                            // 3-Finger Swipe Right -> Switch Desktop Right
                            listener?.onSpecialGesture("HOTKEY:CTRL+WIN+RIGHT")
                            HapticHelper.heavyClick(this)
                        }
                    }
                    isThreeFingerGesture = false
                }

                if (activePointers.size == 1) {
                    val remaining = activePointers.values.first()
                    lastSentX = remaining.x
                    lastSentY = remaining.y
                }
                invalidate()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val releasedTouch = activePointers.remove(pointerId)
                activePointers.clear()
                isTwoFingerScroll = false

                if (isDragging) {
                    isDragging = false
                    listener?.onLeftUp()
                    HapticHelper.click(this)
                } else if (releasedTouch != null && tapToClick && !isThreeFingerGesture) {
                    val duration = now - releasedTouch.startTime
                    val dist = hypot(releasedTouch.x - releasedTouch.startX, releasedTouch.y - releasedTouch.startY)

                    if (duration < TAP_MAX_DURATION && dist < TAP_MAX_DISTANCE) {
                        listener?.onMouseLeftClick()
                        spawnRipple(releasedTouch.x, releasedTouch.y, Color.parseColor("#6EE7B7"))
                        HapticHelper.click(this)
                        lastTapTime = now
                    }
                }

                isThreeFingerGesture = false
                invalidate()
            }
        }
        return true
    }
}
