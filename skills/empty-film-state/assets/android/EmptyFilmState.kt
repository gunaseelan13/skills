// Change this package to your app's own.
package com.example.emptyfilm

import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.core.widget.TextViewCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.R.attr as M3

/**
 * An empty state: [film] showing the feature at work, a [title], a one-line [description], a
 * primary action and, if given, a quieter secondary one under it. As an explainer for a feature
 * that needs no action, pass no primary and it shows only the film and its words.
 *
 * Built in code from the Material 3 theme (title and body text appearances, a filled and an
 * outlined MaterialButton), so it needs no layout file. To use your own components instead, build
 * the same structure (spacing in [Spacing]) and put the film in a 380dp slot.
 */
object EmptyFilmState {

    /** The spacing scale the layout uses, in dp: swap in your design system's values. */
    object Spacing {
        const val XS = 4
        const val MD = 12
        const val LG = 16
        const val XL = 24
        const val FILM_HEIGHT = 380
        const val BUTTON_HEIGHT = 48
    }

    class Views(val root: LinearLayout, val title: TextView, val description: TextView,
                val primary: MaterialButton?, val secondary: MaterialButton?)

    fun show(
            parent: ViewGroup,
            film: SkeletonFilmView,
            title: CharSequence,
            description: CharSequence,
            primary: CharSequence? = null,
            onPrimary: () -> Unit = {},
            secondary: CharSequence? = null,
            onSecondary: () -> Unit = {},
    ): Views {
        val context = parent.context
        fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
        fun text(@AttrRes appearance: Int, @AttrRes color: Int) = TextView(context).apply {
            TextViewCompat.setTextAppearance(this, resolve(this, appearance))
            setTextColor(MaterialColors.getColor(this, color))
            gravity = Gravity.CENTER
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(Spacing.XL), 0, 0)
        }
        val slot = FrameLayout(context)
        slot.addView(film, FrameLayout.LayoutParams(-1, -1))
        root.addView(slot, LinearLayout.LayoutParams(-1, dp(Spacing.FILM_HEIGHT)))

        val titleView = text(M3.textAppearanceTitleLarge, M3.colorOnSurface).apply { this.text = title }
        root.addView(titleView, wrap().apply { topMargin = dp(Spacing.MD) })
        val descView = text(M3.textAppearanceBodyLarge, M3.colorOnSurfaceVariant).apply {
            this.text = description
            setPadding(dp(Spacing.LG), 0, dp(Spacing.LG), 0)
        }
        root.addView(descView, wrap().apply { topMargin = dp(Spacing.XS) })

        val primaryButton = primary?.let { label ->
            MaterialButton(context).apply {
                text = label
                setOnClickListener { onPrimary() }
                root.addView(this, button(dp(Spacing.BUTTON_HEIGHT)).apply { topMargin = dp(Spacing.XL) })
            }
        }
        val secondaryButton = secondary?.let { label ->
            MaterialButton(context, null, M3.materialButtonOutlinedStyle).apply {
                text = label
                setOnClickListener { onSecondary() }
                root.addView(this, button(dp(Spacing.BUTTON_HEIGHT)).apply {
                    topMargin = dp(if (primaryButton != null) Spacing.MD else Spacing.XL)
                })
            }
        }
        parent.addView(root, ViewGroup.LayoutParams(-1, -2))
        return Views(root, titleView, descView, primaryButton, secondaryButton)
    }

    private fun wrap() = LinearLayout.LayoutParams(-2, -2)
    private fun button(height: Int) = LinearLayout.LayoutParams(-1, height)

    private fun resolve(view: View, @AttrRes attr: Int): Int {
        val value = TypedValue()
        view.context.theme.resolveAttribute(attr, value, true)
        return value.resourceId
    }
}
