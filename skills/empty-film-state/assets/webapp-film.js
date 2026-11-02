import { ease, linear } from './skeleton-film.js'

/**
 * A desktop web app with no projects: the pointer clicks New project, types a name into the
 * dialog that opens, picks a colour, clicks Create, and the project lands at the top of the list
 * and in the sidebar.
 */

// Beats, in seconds into a loop, before the pace stretches them.
const CLICK_NEW = 1.3
const DIALOG = 1.45
const TYPE = 2.0
const PICK = 3.35
const CLICK_CREATE = 4.2
const DIALOG_OUT = 4.35
const LANDS = 4.6
const SIDEBAR = 4.9
const FADE_OUT = 8.8
const SIDE_W = 132
const ROW_H = 52

export const webappFilm = {
  loopSeconds: 9.6,
  restSeconds: 6.6,
  pace: 1.25,
  device: 'browser',
  label: 'A browser window showing an empty projects page. The pointer clicks New project, types ' +
      'a name into the dialog, picks a colour and clicks Create, and the new project appears at ' +
      'the top of the list and in the sidebar.',

  drawToolbar(f, t) {
    const s = f.screen
    const alpha = screenAlpha(t)
    // The app's own top bar: its mark, the page title, and New project on the right.
    f.shape('accent', s.left + 14, f.toolbarY - 9, 18, 18, 5, alpha)
    f.bar('page', s.left + 40, f.toolbarY - 5, 64, 10, alpha)
    f.bar('page', s.left + 130, f.toolbarY - 5, 80, 10, alpha)
    const press = 1 - 0.06 * Math.sin(linear(t, CLICK_NEW - 0.08, CLICK_NEW + 0.2) * Math.PI)
    const bw = 92 * press, bh = 24 * press
    f.shape('accent', s.right - 16 - 92 + (92 - bw) / 2, f.toolbarY - bh / 2, bw, bh, 12, alpha)
    f.shape('onAccent', s.right - 16 - 70, f.toolbarY - 3, 48, 6, 3, alpha * 0.85)
  },

  drawScreen(f, t, w) {
    const alpha = screenAlpha(t)
    const land = ease(t, LANDS, LANDS + 0.5)
    const side = ease(t, SIDEBAR, SIDEBAR + 0.45)

    // Sidebar: nav rows, the new project joining the bottom of them.
    f.shape('card', 0, 8, SIDE_W, 300, 14, alpha)
    for (let i = 0; i < 5; i++) {
      const y = 26 + i * 30
      const current = i === 2
      if (current) f.shape('page', 8, y - 8, SIDE_W - 16, 26, 8, alpha)
      f.circle('card', 24, y + 5, 7, alpha)
      f.bar('card', 38, y, 40 + (i * 13) % 34, 10, alpha)
    }
    f.bar('card', 16, 186, 52, 8, alpha * 0.8)
    f.shape('accent', 18, 210 - 6 + 6 * (1 - side), 12, 12, 4, alpha * side)
    f.bar('card', 38, 210 - 5 + 6 * (1 - side), 64, 10, alpha * side)

    // Main: an empty list until the project lands at its top.
    const x = SIDE_W + 16, mw = w - x
    f.bar('page', x, 16, 120, 12, alpha)
    f.ctx.save()
    f.ctx.translate(0, ROW_H * land - ROW_H)
    for (let i = 0; i < 4; i++) {
      const y = 44 + i * ROW_H
      const fresh = i === 0
      const a = alpha * (fresh ? land : 0.45)
      f.shape('card', x, y, mw, ROW_H - 8, 12, a)
      if (fresh) f.shape('accent', x + 12, y + 9, 26, 26, 7, a)
      else f.circle('card', x + 25, y + 22, 13, a)
      f.bar('card', x + 50, y + 11, fresh ? mw * 0.34 : mw * (0.22 + i * 0.06), 10, a)
      f.bar('card', x + 50, y + 27, mw * 0.18, 7, a)
      f.bar('card', x + mw - 70, y + 17, 54, 10, a)
    }
    f.ctx.restore()
  },

  drawOverScreen(f, t) {
    const s = f.screen
    const alpha = screenAlpha(t)

    // The dialog: a scrim, then a card that grows in from 96% and shrinks away after Create.
    const open = ease(t, DIALOG, DIALOG + 0.3) * (1 - ease(t, DIALOG_OUT, DIALOG_OUT + 0.3))
    const dw = 236, dh = 168
    const dx = s.left + (s.width - dw) / 2, dy = s.top + 72
    const create = { x: dx + dw - 16 - 34, y: dy + dh - 30 }
    const swatch = i => ({ x: dx + 28 + i * 24, y: dy + 102 })
    if (open > 0) {
      f.shape('ink', s.left, s.top, s.width, s.height, 0, 0.16 * open)
      const ctx = f.ctx
      ctx.save()
      const k = 0.96 + 0.04 * open
      ctx.translate(dx + dw / 2, dy + dh / 2)
      ctx.scale(k, k)
      ctx.translate(-(dx + dw / 2), -(dy + dh / 2))
      f.shape('card', dx, dy, dw, dh, 14, open)
      f.stroke('outline', dx, dy, dw, dh, 14, open)
      f.bar('card', dx + 16, dy + 18, 96, 10, open)
      // Name field, typed into.
      f.shape('page', dx + 16, dy + 40, dw - 32, 34, 9, open)
      const typed = linear(t, TYPE, TYPE + 1.0)
      f.bar('page', dx + 28, dy + 52, (dw - 90) * typed, 10, open * (typed > 0 ? 1 : 0))
      // Colour swatches: the accent one picked.
      const picked = ease(t, PICK, PICK + 0.2)
      for (let i = 0; i < 5; i++) {
        const p = swatch(i)
        f.circle('card', p.x, p.y, 9, open)
        if (i === 1 && picked > 0) {
          f.shape('accent', p.x - 9, p.y - 9, 18, 18, 9, open * picked)
          f.stroke('accent', p.x - 12, p.y - 12, 24, 24, 12, open * picked)
        }
      }
      // Cancel and Create.
      f.stroke('outline', dx + dw - 16 - 68 - 62, dy + dh - 42, 56, 24, 12, open)
      const press = 1 - 0.06 * Math.sin(linear(t, CLICK_CREATE - 0.08, CLICK_CREATE + 0.2) * Math.PI)
      f.shape('accent', create.x - 34 * press, create.y - 12 * press, 68 * press, 24 * press, 12, open)
      ctx.restore()
    }

    // The pointer: in from the page, to New project, to the swatch, to Create, then away.
    const newBtn = { x: s.right - 16 - 46, y: f.toolbarY }
    const p1 = swatch(1)
    f.pointer(t, [
      [0, s.left + s.width * 0.55, s.top + 220],
      [CLICK_NEW - 0.1, newBtn.x, newBtn.y + 2, 0.8],
      [PICK - 0.05, p1.x + 2, p1.y + 2, 0.7],
      [CLICK_CREATE - 0.1, create.x, create.y + 2, 0.55],
      [LANDS + 1.2, s.left + s.width * 0.62, s.top + 250, 0.9],
    ], [CLICK_NEW, PICK, CLICK_CREATE], alpha)
  },
}

function screenAlpha(t) {
  return ease(t, 0, 0.4) * (1 - ease(t, FADE_OUT, FADE_OUT + 0.6))
}
