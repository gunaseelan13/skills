// Change this package to your app's own.
package com.example.emptyfilm

import android.content.Context
import android.util.AttributeSet
import com.example.emptyfilm.FilmTone.ACCENT
import com.example.emptyfilm.FilmTone.CARD
import com.example.emptyfilm.FilmTone.INK
import com.example.emptyfilm.FilmTone.PAGE

/**
 * An example film, the same script as example-film.js: a project list with nothing in it, a tap on
 * New, a sheet with a name typed in, Save, and the new project landing at the top of the list in
 * the accent.
 */
class ProjectsFilmView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null)
    : SkeletonFilmView(context, attrs) {

    override val loopSeconds = 9.4f
    override val restSeconds = 6.4f
    override val pace = 1.3f
    override val label = "A phone showing an empty project list, a tap on New, a name typed into " +
            "the sheet that opens, then Save, and the new project appearing at the top of the list."

    override fun drawToolbar(t: Float) {
        val alpha = screenAlpha(t)
        circle(PAGE, screen.left + 24, toolbarY, 9f, alpha)
        bar(PAGE, screen.left + 44, toolbarY - 6, 88f, 12f, alpha)
        // New, pressed at TAP_NEW.
        shape(ACCENT, screen.right - 52, toolbarY - 13, 36f, 26f, 13f, alpha)
    }

    override fun drawScreen(t: Float, w: Float) {
        val alpha = screenAlpha(t)
        val land = ease(t, LANDS, LANDS + .5f)
        // The list: the new row slides in at the top and pushes the rest down.
        canvas.save()
        canvas.translate(0f, ROW_H * land - ROW_H)
        for (i in 0 until 4) {
            val y = 16f + i * ROW_H
            val fresh = i == 0
            val a = alpha * if (fresh) land else .55f
            shape(CARD, 0f, y, w, ROW_H - 8, 16f, a)
            if (fresh) shape(ACCENT, 14f, y + 10, 28f, 28f, 8f, a)
            else circle(CARD, 28f, y + 24, 14f, a)
            bar(CARD, 54f, y + 12, if (fresh) w * .42f else w * (.3f + i * .07f), 10f, a)
            bar(CARD, 54f, y + 28, w * .24f, 8f, a)
        }
        canvas.restore()
    }

    override fun drawOverScreen(t: Float) {
        touch(t, screen.right - 34, toolbarY, TAP_NEW, TAP_NEW + .5f)

        // The sheet rises from the bottom with its field, then sinks away after Save.
        val up = ease(t, SHEET, SHEET + .45f) * (1 - ease(t, SHEET_OUT, SHEET_OUT + .4f))
        if (up <= 0) return
        shape(INK, screen.left, screen.top, screen.width(), screen.height(), 0f, .18f * up)
        val sheetH = 230f
        val top = filmHeight - sheetH * up
        shape(CARD, screen.left, top, screen.width(), sheetH + 60, 24f)
        bar(CARD, screen.centerX() - 16, top + 8, 32f, 4f)
        val fieldY = top + 34
        shape(PAGE, screen.left + 16, fieldY, screen.width() - 32, 44f, 12f)
        val typed = linear(t, TYPE, TYPE + 1.2f)
        bar(PAGE, screen.left + 30, fieldY + 17, (screen.width() - 120) * typed, 10f, if (typed > 0) 1f else 0f)
        val press = 1 - .04f * pulse(t, SAVE - .1f, SAVE + .25f)
        val bw = (screen.width() - 32) * press
        shape(ACCENT, screen.centerX() - bw / 2, fieldY + 60, bw, 40f, 20f)
        touch(t, screen.centerX(), fieldY + 80, SAVE, SAVE + .5f)
    }

    private fun screenAlpha(t: Float) = ease(t, 0f, .4f) * (1 - ease(t, FADE_OUT, FADE_OUT + .6f))

    private companion object {
        // Beats, in seconds into a loop, before the pace stretches them.
        const val TAP_NEW = 1.2f
        const val SHEET = 1.6f
        const val TYPE = 2.4f
        const val SAVE = 4.2f
        const val SHEET_OUT = 4.6f
        const val LANDS = 4.9f
        const val FADE_OUT = 8.6f
        const val ROW_H = 56f
    }
}
