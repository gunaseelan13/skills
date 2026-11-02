import { ease, linear } from './skeleton-film.js'

/**
 * An example script: a project list with nothing in it, a tap on New, a sheet with a name typed
 * in, Save, and the new project landing at the top of the list in the accent.
 */

// Beats, in seconds into a loop, before the pace stretches them.
const TAP_NEW = 1.2
const SHEET = 1.6
const TYPE = 2.4
const SAVE = 4.2
const SHEET_OUT = 4.6
const LANDS = 4.9
const FADE_OUT = 8.6
const ROW_H = 56

export const projectsFilm = {
  loopSeconds: 9.4,
  restSeconds: 6.4,
  pace: 1.3,
  device: 'phone',
  label: 'A phone showing an empty project list, a tap on New, a name typed into the sheet that ' +
      'opens, then Save, and the new project appearing at the top of the list.',

  drawToolbar(f, t) {
    const s = f.screen
    const alpha = screenAlpha(t)
    f.circle('page', s.left + 24, f.toolbarY, 9, alpha)
    f.bar('page', s.left + 44, f.toolbarY - 6, 88, 12, alpha)
    // New, pressed at TAP_NEW.
    f.shape('accent', s.right - 52, f.toolbarY - 13, 36, 26, 13, alpha)
  },

  drawScreen(f, t, w) {
    const alpha = screenAlpha(t)
    const land = ease(t, LANDS, LANDS + 0.5)
    // The list: the new row slides in at the top and pushes the rest down.
    f.ctx.save()
    f.ctx.translate(0, ROW_H * land - ROW_H)
    for (let i = 0; i < 4; i++) {
      const y = 16 + i * ROW_H
      const fresh = i === 0
      const a = alpha * (fresh ? land : 0.55)
      f.shape('card', 0, y, w, ROW_H - 8, 16, a)
      if (fresh) f.shape('accent', 14, y + 10, 28, 28, 8, a)
      else f.circle('card', 28, y + 24, 14, a)
      f.bar('card', 54, y + 12, fresh ? w * 0.42 : w * (0.3 + i * 0.07), 10, a)
      f.bar('card', 54, y + 28, w * 0.24, 8, a)
    }
    f.ctx.restore()
  },

  drawOverScreen(f, t) {
    const s = f.screen
    f.touch(t, s.right - 34, f.toolbarY, TAP_NEW, TAP_NEW + 0.5)

    // The sheet rises from the bottom with its field, then sinks away after Save.
    const up = ease(t, SHEET, SHEET + 0.45) * (1 - ease(t, SHEET_OUT, SHEET_OUT + 0.4))
    if (up <= 0) return
    f.shape('ink', s.left, s.top, s.width, s.height, 0, 0.18 * up)
    const sheetH = 230
    const top = f.height - sheetH * up
    f.shape('card', s.left, top, s.width, sheetH + 60, 24, 1)
    f.bar('card', s.left + s.width / 2 - 16, top + 8, 32, 4)
    const fieldY = top + 34
    f.shape('page', s.left + 16, fieldY, s.width - 32, 44, 12, 1)
    const typed = linear(t, TYPE, TYPE + 1.2)
    f.bar('page', s.left + 30, fieldY + 17, (s.width - 120) * typed, 10, typed > 0 ? 1 : 0)
    const press = 1 - 0.04 * Math.sin(linear(t, SAVE - 0.1, SAVE + 0.25) * Math.PI)
    const bw = (s.width - 32) * press
    f.shape('accent', s.left + s.width / 2 - bw / 2, fieldY + 60, bw, 40, 20, 1)
    f.touch(t, s.left + s.width / 2, fieldY + 80, SAVE, SAVE + 0.5)
  },
}

function screenAlpha(t) {
  return ease(t, 0, 0.4) * (1 - ease(t, FADE_OUT, FADE_OUT + 0.6))
}
