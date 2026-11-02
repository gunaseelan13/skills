// Change this package to your app's own.
package com.example.emptyfilm

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.AttrRes
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import com.google.android.material.R.attr as M3
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Skeleton film for Android empty states: a short looping demo drawn as skeleton UI on the top half
 * of a phone (or a browser window) that fades into the page. The Android twin of skeleton-film.js:
 * the same devices, timing, shimmer and parts, so a film reads the same on every platform.
 *
 * Subclass it and write the script:
 *
 *   class ExampleFilmView(context: Context, attrs: AttributeSet? = null) : SkeletonFilmView(context, attrs) {
 *       override val loopSeconds = 10f
 *       override val restSeconds = 6.5f
 *       override val pace = 1.3f
 *       override val label = "A phone showing…"            // the whole story, for TalkBack
 *       override fun drawScreen(t: Float, w: Float) { … }  // content coordinates, contentWidth wide
 *       override fun drawOverScreen(t: Float) { … }        // device coordinates (touches, sheets)
 *   }
 *
 * Every coordinate is in dp, and the numbers are the same as in a web script. Colours come from the
 * Material 3 theme (see [readColors]); override it to map the film to your own tokens.
 *
 * Playback: plays [loops] times while on screen, pauses when hidden, scrolled away or detached, then
 * rests on the [restSeconds] frame. With animations off (Settings → Accessibility → Remove
 * animations, or animator scale 0) it shows only that frame.
 */
abstract class SkeletonFilmView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
) : View(context, attrs) {

    // ── The script ─────────────────────────────────────────────────────────────────────────────

    /** One pass of the script, in seconds, before [pace]. */
    protected abstract val loopSeconds: Float
    /** The frame that explains the feature on its own: shown after the loops, and with motion off. */
    protected abstract val restSeconds: Float
    /** Stretches every beat: 1.3 plays the same script 30% slower. */
    protected open val pace = 1f
    /** How many times it plays while on screen. */
    protected open val loops = 3
    protected open val device = FilmDevice.PHONE
    /** The whole story in order, read out by TalkBack. */
    protected open val label: CharSequence? = null

    /** The toolbar band, in device coordinates (centred on [toolbarY]). */
    protected open fun drawToolbar(t: Float) {}
    /** The screen's content at [t] seconds into the script: [w] (contentWidth) wide, scaled to fit. */
    protected abstract fun drawScreen(t: Float, w: Float)
    /** What floats over the screen at [t], in device coordinates: touches, sheets, scrims. */
    protected open fun drawOverScreen(t: Float) {}
    /** A colour over the screen's page colour at [t], with its alpha, or null for none. */
    protected open fun screenBackground(t: Float): Pair<FilmTone, Float>? = null

    // ── What the script can read ───────────────────────────────────────────────────────────────

    /** The canvas being drawn, for anything the parts don't cover (save and restore around it). */
    protected val canvas: Canvas get() = frameCanvas ?: error("Only valid while drawing")
    /** This frame's screen, in device coordinates; it runs on past the bottom of the view. */
    protected val screen = RectF()
    /** The middle of the toolbar band. */
    protected var toolbarY = 0f
        private set
    /** The view's height in dp. */
    protected var filmHeight = 0f
        private set
    /** How much the screen's content is scaled down to fit the device. */
    protected var contentScale = 1f
        private set

    // ── State ──────────────────────────────────────────────────────────────────────────────────

    private val density = resources.displayMetrics.density
    private var colors: FilmColors? = null
    private val painters = HashMap<FilmTone, SkeletonPaint>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val glass = Path()
    private val fadePaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
    private var frameCanvas: Canvas? = null
    private var sweepX = 0f
    private var shimmerOn = false

    private var elapsedMs = 0L
    private var lastFrameNanos = -1L
    private var ticking = false
    private var shown = false
    private var onScreen = true
    private val visibleRect = Rect()

    private val frame = Choreographer.FrameCallback { now -> tick(now) }
    private val scrollWatch = ViewTreeObserver.OnScrollChangedListener { checkOnScreen() }
    private val layoutWatch = ViewTreeObserver.OnGlobalLayoutListener { checkOnScreen() }
    private val motionWatch: Any? =
            if (Build.VERSION.SDK_INT >= 33) ValueAnimator.DurationScaleChangeListener { post { update() } } else null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    // ── Playback ───────────────────────────────────────────────────────────────────────────────

    private val loopMs get() = (loopSeconds * pace * 1000).toLong()
    private val done get() = elapsedMs >= loopMs * loops
    private val moving get() = shown && onScreen && ValueAnimator.areAnimatorsEnabled() && !done

    /** The script's time for this frame: within the loop while playing, else the rest frame. */
    private val t get() = if (moving) (elapsedMs % loopMs) / 1000f / pace else restSeconds

    /** Starts over from the first beat, for when the screen shows the film again. */
    fun restart() {
        elapsedMs = 0
        update()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        label?.let { contentDescription = it }
        viewTreeObserver.addOnScrollChangedListener(scrollWatch)
        viewTreeObserver.addOnGlobalLayoutListener(layoutWatch)
        if (Build.VERSION.SDK_INT >= 33) ValueAnimator.registerDurationScaleChangeListener(motionWatch as ValueAnimator.DurationScaleChangeListener)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnScrollChangedListener(scrollWatch)
        viewTreeObserver.removeOnGlobalLayoutListener(layoutWatch)
        if (Build.VERSION.SDK_INT >= 33) ValueAnimator.unregisterDurationScaleChangeListener(motionWatch as ValueAnimator.DurationScaleChangeListener)
        shown = false
        update()
        super.onDetachedFromWindow()
    }

    // Covers the view, its parents and the window being shown or hidden.
    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        shown = isVisible
        update()
    }

    private fun checkOnScreen() {
        val now = isAttachedToWindow && getLocalVisibleRect(visibleRect)
        if (now != onScreen) { onScreen = now; update() }
    }

    private fun update() {
        if (moving && !ticking) {
            ticking = true
            lastFrameNanos = -1L
            Choreographer.getInstance().postFrameCallback(frame)
        } else if (!moving && ticking) {
            ticking = false
            Choreographer.getInstance().removeFrameCallback(frame)
        }
        invalidate()
    }

    private fun tick(now: Long) {
        if (lastFrameNanos >= 0) elapsedMs += (now - lastFrameNanos) / 1_000_000
        lastFrameNanos = now
        invalidate()
        if (moving) Choreographer.getInstance().postFrameCallback(frame) else { ticking = false; invalidate() }
    }

    // ── Theme ──────────────────────────────────────────────────────────────────────────────────

    /**
     * The film's colours. By default, from the Material 3 theme:
     * page = colorSurfaceContainerLow, card = colorSurfaceContainerLowest by day and
     * colorSurfaceContainerHigh by night (cards sit lighter than the page in both), raised =
     * colorSurfaceContainerLowest by day and colorSurfaceContainerHighest by night, ink = colorOnSurface, ink soft =
     * colorOnSurfaceVariant, outline = colorOutlineVariant, accent = colorPrimary, on accent =
     * colorOnPrimary. Override it to use your design system's tokens instead.
     */
    protected open fun readColors(): FilmColors {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return FilmColors(
                page = themeColor(M3.colorSurfaceContainerLow),
                card = themeColor(if (night) M3.colorSurfaceContainerHigh else M3.colorSurfaceContainerLowest),
                raised = themeColor(if (night) M3.colorSurfaceContainerHighest else M3.colorSurfaceContainerLowest),
                ink = themeColor(M3.colorOnSurface),
                inkSoft = themeColor(M3.colorOnSurfaceVariant),
                outline = themeColor(M3.colorOutlineVariant),
                accent = themeColor(M3.colorPrimary),
                onAccent = themeColor(M3.colorOnPrimary),
        )
    }

    /** A colour from the theme, for [readColors]. */
    protected fun themeColor(@AttrRes attr: Int) = MaterialColors.getColor(this, attr)

    /** Reads the colours again, as after a theme change made some other way than a configuration change. */
    fun readTheme() {
        colors = null
        painters.clear()
        invalidate()
    }

    private val palette get() = colors ?: readColors().also { colors = it }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        readTheme()
    }

    /** A colour token's colour. */
    protected fun color(tone: FilmTone) = when (tone) {
        FilmTone.PAGE -> palette.page
        FilmTone.CARD -> palette.card
        FilmTone.RAISED -> palette.raised
        FilmTone.INK -> palette.ink
        FilmTone.INK_SOFT -> palette.inkSoft
        FilmTone.OUTLINE -> palette.outline
        FilmTone.ACCENT -> palette.accent
        FilmTone.ON_ACCENT -> palette.onAccent
    }

    private fun painter(surface: FilmTone) = painters.getOrPut(surface) {
        val s = color(surface)
        val ink = palette.ink
        val lighter = ColorUtils.calculateLuminance(s) > ColorUtils.calculateLuminance(ink)
        val base = ColorUtils.blendARGB(s, ink, if (lighter) INK_DAY else INK_NIGHT)
        val highlight = if (lighter) ColorUtils.blendARGB(base, s, .75f) else ColorUtils.blendARGB(base, ink, .06f)
        SkeletonPaint(base, highlight)
    }

    // ── Drawing ────────────────────────────────────────────────────────────────────────────────

    override fun onDraw(cv: Canvas) {
        if (width == 0 || height == 0) return
        val d = device
        val t = t
        val seconds = elapsedMs / 1000f
        shimmerOn = moving
        frameCanvas = cv
        // Everything is drawn in dp, as the web film draws in CSS px.
        val w = width / density
        val h = height / density
        filmHeight = h
        val layer = cv.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        cv.scale(density, density)

        val phoneW = min(w, d.width)
        val phoneX = (w - phoneW) / 2
        screen.set(phoneX + d.bezel, d.top + d.bezel, phoneX + phoneW - d.bezel, h + d.radius)
        toolbarY = screen.top + d.status + d.toolbar / 2
        contentScale = (screen.width() - INSET * 1.5f) / d.contentWidth
        val glassR = max(d.radius - d.bezel, 0f)

        // The device, still: body, screen, status bar or window bar, toolbar.
        shape(FilmTone.CARD, phoneX, d.top, phoneW, h - d.top + d.radius, d.radius)
        stroke(FilmTone.OUTLINE, phoneX, d.top, phoneW, h - d.top + d.radius, d.radius)

        cv.save()
        glass.rewind()
        glass.addRoundRect(screen, glassR, glassR, Path.Direction.CW)
        cv.clipPath(glass)
        shimmer(seconds, w)
        shape(FilmTone.PAGE, screen.left, screen.top, screen.width(), screen.height(), 0f)
        screenBackground(t)?.let { (tone, alpha) -> shape(tone, screen.left, screen.top, screen.width(), screen.height(), 0f, alpha) }
        if (d == FilmDevice.BROWSER) windowBar()
        else circle(FilmTone.PAGE, screen.centerX(), screen.top + d.status / 2 + 2, 5f)
        drawToolbar(t)

        // The screen's content, laid out at contentWidth and scaled to the device.
        cv.save()
        cv.translate(screen.left + INSET * .75f, screen.top + d.status + d.toolbar)
        cv.scale(contentScale, contentScale)
        shimmer(seconds, d.contentWidth)
        drawScreen(t, d.contentWidth)
        cv.restore()

        // What floats over the screen, at device size.
        shimmer(seconds, w)
        drawOverScreen(t)
        cv.restore()

        // The bottom fades out, so the page shows through whatever is behind the view.
        cv.drawRect(0f, h - FADE_H, w, h, fadePaint)
        cv.restoreToCount(layer)
        frameCanvas = null
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val hDp = h / density
        fadePaint.shader = LinearGradient(0f, hDp - FADE_H, 0f, hDp, 0x00000000, 0xFF000000.toInt(), Shader.TileMode.CLAMP)
    }

    private fun windowBar() {
        val cy = screen.top + device.status / 2
        for (i in 0 until 3) circle(FilmTone.PAGE, screen.left + 14 + i * 14, cy, 4f)
        bar(FilmTone.PAGE, screen.left + 64, cy - 8, screen.width() - 128, 16f)
    }

    // The highlight's sweep for this frame, across a space [width] wide.
    private fun shimmer(seconds: Float, width: Float) {
        val pass = (seconds % CYCLE_S) / PASS_S
        sweepX = if (shimmerOn && pass < 1) -BAND + (width + BAND * 2) * pass else -BAND * 4
    }

    private fun skeletonPaint(surface: FilmTone, alpha: Float): Paint {
        val p = painter(surface)
        p.matrix.setTranslate(sweepX, 0f)
        p.shader.setLocalMatrix(p.matrix)
        p.paint.alpha = (255 * alpha.coerceIn(0f, 1f)).toInt()
        return p.paint
    }

    // ── Parts for scripts ──────────────────────────────────────────────────────────────────────

    /** A skeleton bar, rounded at its ends, on [surface] (PAGE, CARD, RAISED), shimmering. */
    protected fun bar(surface: FilmTone, x: Float, y: Float, w: Float, h: Float, alpha: Float = 1f) {
        if (w <= 0 || alpha <= 0) return
        rect.set(x, y, x + w, y + h)
        canvas.drawRoundRect(rect, h / 2, h / 2, skeletonPaint(surface, alpha))
    }

    /** A skeleton block with corners of [r]. */
    protected fun block(surface: FilmTone, x: Float, y: Float, w: Float, h: Float, r: Float, alpha: Float = 1f) {
        if (w <= 0 || alpha <= 0) return
        rect.set(x, y, x + w, y + h)
        canvas.drawRoundRect(rect, r, r, skeletonPaint(surface, alpha))
    }

    /** A skeleton circle, such as an icon well or avatar. */
    protected fun circle(surface: FilmTone, cx: Float, cy: Float, r: Float, alpha: Float = 1f) {
        if (alpha <= 0) return
        canvas.drawCircle(cx, cy, r, skeletonPaint(surface, alpha))
    }

    /** A solid shape in a colour token (a card, a sheet, the accent), faded by [alpha]. */
    protected fun shape(tone: FilmTone, x: Float, y: Float, w: Float, h: Float, r: Float, alpha: Float = 1f) =
            shapeColor(color(tone), x, y, w, h, r, alpha)

    /** [shape] in any colour, such as a mix of two tokens. */
    protected fun shapeColor(color: Int, x: Float, y: Float, w: Float, h: Float, r: Float, alpha: Float = 1f) {
        if (alpha <= 0) return
        paint.style = Paint.Style.FILL
        paint.color = color.faded(alpha)
        rect.set(x, y, x + w, y + h)
        canvas.drawRoundRect(rect, r, r, paint)
    }

    /** A hairline edge round the shape [shape] would fill. */
    protected fun stroke(tone: FilmTone, x: Float, y: Float, w: Float, h: Float, r: Float, alpha: Float = 1f) {
        if (alpha <= 0) return
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = color(tone).faded(alpha)
        rect.set(x + .5f, y + .5f, x + w - .5f, y + h - .5f)
        canvas.drawRoundRect(rect, r, r, paint)
        paint.style = Paint.Style.FILL
    }

    /** A switch: outline track when off, accent when [on] (0 to 1), a light thumb sliding across. */
    protected fun toggle(x: Float, cy: Float, on: Float, alpha: Float = 1f) {
        val w = 36f
        val h = 20f
        shapeColor(ColorUtils.blendARGB(palette.outline, palette.accent, on.coerceIn(0f, 1f)), x, cy - h / 2, w, h, h / 2, alpha)
        paint.color = palette.onAccent.faded(alpha)
        canvas.drawCircle(x + h / 2 + (w - h) * on.coerceIn(0f, 1f), cy, h / 2 - 3, paint)
    }

    /** A touch at [x], [y]: a dot where the finger lands at [at], its ring spreading until [until]. */
    protected fun touch(t: Float, x: Float, y: Float, at: Float, until: Float, alpha: Float = 1f) {
        val spread = linear(t, at - .1f, until)
        if (spread <= 0 || spread >= 1) return
        paint.style = Paint.Style.FILL
        paint.color = palette.ink.faded(.16f * pulse(t, at - .1f, at + .35f) * alpha)
        canvas.drawCircle(x, y, 14f, paint)
        paint.color = palette.ink.faded(.12f * (1 - spread) * alpha)
        canvas.drawCircle(x, y, 14 + 22 * spread, paint)
    }

    /**
     * Where a mouse pointer is at [t] along [path], stops in device coordinates: it arrives at each
     * stop at its second, gliding (eased) over the stop's [PointerStop.glide] seconds before, and
     * holds there until it leaves for the next.
     */
    protected fun pointerAt(t: Float, path: List<PointerStop>): PointF {
        var x = path[0].x
        var y = path[0].y
        for (i in 1 until path.size) {
            val s = path[i]
            val p = ease(t, s.at - s.glide, s.at)
            x += (s.x - x) * p
            y += (s.y - y) * p
        }
        return PointF(x, y)
    }

    /**
     * A mouse pointer for desktop films, the stand-in for [touch]: an arrow following [path] (see
     * [pointerAt]), pressing in a little and ringing at each second in [clicks].
     */
    protected fun pointer(t: Float, path: List<PointerStop>, clicks: List<Float> = emptyList(), alpha: Float = 1f) {
        if (alpha <= 0) return
        val (x, y) = pointerAt(t, path).let { it.x to it.y }
        var press = 0f
        paint.style = Paint.Style.FILL
        for (at in clicks) {
            press = max(press, pulse(t, at - .08f, at + .18f))
            val spread = linear(t, at, at + .45f)
            if (spread > 0 && spread < 1) {
                paint.color = palette.accent.faded(.22f * (1 - spread) * alpha)
                canvas.drawCircle(x, y, 6 + 14 * spread, paint)
            }
        }
        val cv = canvas
        cv.save()
        cv.translate(x, y)
        cv.scale(1 - .12f * press, 1 - .12f * press)
        val arrow = Path().apply {
            moveTo(0f, 0f); lineTo(0f, 16f); lineTo(4.2f, 12.2f); lineTo(7f, 18.6f)
            lineTo(9.6f, 17.5f); lineTo(6.9f, 11.3f); lineTo(12f, 11.3f); close()
        }
        paint.color = palette.ink.faded(alpha)
        cv.drawPath(arrow, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = 1.2f
        paint.color = palette.card.faded(alpha)
        cv.drawPath(arrow, paint)
        paint.style = Paint.Style.FILL
        cv.restore()
    }

    /** Content coordinates (as [drawScreen] uses them) to device ones, to aim a pointer or touch. */
    protected fun toDevice(x: Float, y: Float) = PointF(
            screen.left + INSET * .75f + x * contentScale,
            screen.top + device.status + device.toolbar + y * contentScale,
    )

    /** "That did nothing": a quick side-to-side offset from [at], to translate the screen by. */
    protected fun shake(t: Float, at: Float, seconds: Float = .5f, swings: Int = 3, size: Float = 7f): Float {
        val p = linear(t, at, at + seconds)
        if (p <= 0 || p >= 1) return 0f
        return sin(p * PI * 2 * swings).toFloat() * (1 - p) * size
    }

    /** The screen going off, as when it locks: black fading in over [seconds] from [at], out at [until]. */
    protected fun screenOff(t: Float, at: Float, until: Float, seconds: Float = .7f) {
        val off = ease(t, at, at + seconds) * (1 - ease(t, until, until + .6f))
        if (off <= 0) return
        paint.style = Paint.Style.FILL
        paint.color = 0xFF000000.toInt().faded(off)
        canvas.drawRect(screen, paint)
    }

    // ── Timing ─────────────────────────────────────────────────────────────────────────────────

    /** 0 before [a], 1 after [b], straight between: for spreads and progress. */
    protected fun linear(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
    /** 0 before [a], 1 after [b], smoothstep between: for fades and moves. */
    protected fun ease(t: Float, a: Float, b: Float): Float { val p = linear(t, a, b); return p * p * (3 - 2 * p) }
    /** Up and back down between [a] and [b]. */
    protected fun pulse(t: Float, a: Float, b: Float) = sin(linear(t, a, b) * PI).toFloat().coerceAtLeast(0f)

    private fun Int.faded(alpha: Float) =
            ColorUtils.setAlphaComponent(this, ((this ushr 24) * alpha.coerceIn(0f, 1f)).toInt())

    private class SkeletonPaint(base: Int, highlight: Int) {
        val shader = LinearGradient(-BAND, 0f, BAND, BAND * SLANT,
                intArrayOf(base, highlight, base), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
        val matrix = Matrix()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = this@SkeletonPaint.shader }
    }

    private companion object {
        const val FADE_H = 56f
        const val INSET = 16f
        // Shimmer: how much ink a skeleton carries over its surface, and the sweeping highlight.
        const val INK_DAY = .075f
        const val INK_NIGHT = .1f
        const val BAND = 120f
        const val SLANT = .35f
        const val PASS_S = 1.6f
        const val CYCLE_S = 2.6f
    }
}

/** The device a film plays on, sizes in dp. */
enum class FilmDevice(
        val width: Float, val top: Float, val radius: Float, val bezel: Float,
        val status: Float, val toolbar: Float, val contentWidth: Float,
) {
    /** For features used on a phone, or any story told with a gesture. */
    PHONE(288f, 2f, 44f, 8f, 28f, 40f, 328f),
    /** For desktop features: a window with three dots and an address bar. */
    BROWSER(520f, 2f, 14f, 6f, 34f, 40f, 640f),
}

/** The film's colour tokens. Skeletons take a surface (PAGE, CARD, RAISED); shapes take any. */
enum class FilmTone { PAGE, CARD, RAISED, INK, INK_SOFT, OUTLINE, ACCENT, ON_ACCENT }

/** The colours behind [FilmTone], as ARGB ints. */
data class FilmColors(
        val page: Int, val card: Int, val raised: Int, val ink: Int,
        val inkSoft: Int, val outline: Int, val accent: Int, val onAccent: Int,
)

/** A stop on a pointer's path: it arrives at [x], [y] at second [at], gliding over [glide] seconds. */
data class PointerStop(val at: Float, val x: Float, val y: Float, val glide: Float = .45f)
