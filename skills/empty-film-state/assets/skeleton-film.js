/**
 * Skeleton film for web empty states: a short looping demo drawn as skeleton UI on the top half of
 * a phone (or a browser window) that fades into the page. Framework-free ES module; wrap it in a
 * React/Vue/Svelte component by creating it on mount and calling destroy() on unmount.
 *
 *   import { SkeletonFilm, emptyFilmState } from './skeleton-film.js'
 *   const film = new SkeletonFilm(canvas, {
 *     loopSeconds: 10, restSeconds: 6.5, pace: 1.3, device: 'phone',
 *     label: 'A phone showing…',
 *     drawScreen(f, t, w) { … },     // content coordinates, contentWidth wide, scaled to fit
 *     drawOverScreen(f, t) { … },    // device coordinates over the screen (touches, sheets)
 *   })
 *
 * Colours come from CSS custom properties on the canvas (or any ancestor), so the film follows the
 * design system's tokens and its light and dark themes. Map them to your tokens in CSS:
 *   --film-page, --film-card, --film-raised, --film-ink, --film-ink-soft, --film-outline,
 *   --film-accent, --film-on-accent
 *
 * Playback: plays `loops` times while visible, pauses off screen and in hidden tabs, then rests on
 * the `restSeconds` frame. With prefers-reduced-motion it shows only that frame.
 */

const DEVICES = {
  // Sizes in CSS px.
  phone: { width: 288, top: 2, radius: 44, bezel: 8, status: 28, toolbar: 40, contentWidth: 328 },
  browser: { width: 520, top: 2, radius: 14, bezel: 6, status: 34, toolbar: 40, contentWidth: 640 },
}
const FADE_H = 56
const INSET = 16
// Shimmer: how much ink a skeleton carries over its surface, and the sweeping highlight.
const INK_DAY = 0.075
const INK_NIGHT = 0.1
const BAND = 120
const SLANT = 0.35
const PASS_S = 1.6
const CYCLE_S = 2.6

export class SkeletonFilm {
  constructor(canvas, script) {
    this.canvas = canvas
    this.ctx = canvas.getContext('2d')
    this.script = script
    this.loopSeconds = script.loopSeconds
    this.restSeconds = script.restSeconds
    this.pace = script.pace ?? 1
    this.loops = script.loops ?? 3
    this.device = DEVICES[script.device ?? 'phone']
    this.screen = { left: 0, top: 0, right: 0, bottom: 0, width: 0, height: 0 }
    this.contentScale = 1
    this.elapsedMs = 0
    this.visible = false
    this.raf = 0

    canvas.setAttribute('role', 'img')
    if (script.label) canvas.setAttribute('aria-label', script.label)

    this.reducedMotion = matchMedia('(prefers-reduced-motion: reduce)')
    this.darkScheme = matchMedia('(prefers-color-scheme: dark)')
    this.onSchemeChange = () => { this.readTheme(); this.draw() }
    this.onMotionChange = () => this.update()
    this.onTabVisibility = () => this.update()
    this.darkScheme.addEventListener('change', this.onSchemeChange)
    this.reducedMotion.addEventListener('change', this.onMotionChange)
    document.addEventListener('visibilitychange', this.onTabVisibility)
    // Themes switched by a class or attribute on <html> (data-theme, .dark).
    this.themeObserver = new MutationObserver(this.onSchemeChange)
    this.themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['class', 'data-theme', 'style'] })
    this.resizeObserver = new ResizeObserver(() => { this.resize(); this.draw() })
    this.resizeObserver.observe(canvas)
    this.intersection = new IntersectionObserver(([entry]) => { this.visible = entry.isIntersecting; this.update() })
    this.intersection.observe(canvas)

    this.readTheme()
    this.resize()
    this.draw()
  }

  destroy() {
    cancelAnimationFrame(this.raf)
    this.darkScheme.removeEventListener('change', this.onSchemeChange)
    this.reducedMotion.removeEventListener('change', this.onMotionChange)
    document.removeEventListener('visibilitychange', this.onTabVisibility)
    this.themeObserver.disconnect()
    this.resizeObserver.disconnect()
    this.intersection.disconnect()
  }

  /** Starts over from the first beat, for when the page shows the film again. */
  restart() {
    this.elapsedMs = 0
    this.update()
  }

  // ── Playback ────────────────────────────────────────────────────────────────────────────────

  get loopMs() { return this.loopSeconds * this.pace * 1000 }
  get done() { return this.elapsedMs >= this.loopMs * this.loops }
  get moving() { return this.visible && !document.hidden && !this.reducedMotion.matches && !this.done }

  update() {
    cancelAnimationFrame(this.raf)
    this.raf = 0
    this.last = undefined
    if (this.moving) this.raf = requestAnimationFrame(now => this.tick(now))
    else this.draw()
  }

  tick(now) {
    if (this.last !== undefined) this.elapsedMs += now - this.last
    this.last = now
    this.draw()
    if (this.moving) this.raf = requestAnimationFrame(n => this.tick(n))
    else this.update()
  }

  /** The script's time for this frame: within the loop while playing, else the rest frame. */
  get t() {
    return this.moving ? (this.elapsedMs % this.loopMs) / 1000 / this.pace : this.restSeconds
  }

  /** Draws the film at script time [t]; for captures and tests. */
  frame(t) { this.draw(t) }

  // ── Theme ───────────────────────────────────────────────────────────────────────────────────

  readTheme() {
    const css = getComputedStyle(this.canvas)
    const v = (name, fallback) => css.getPropertyValue(name).trim() || fallback
    this.colors = {
      page: parseColor(v('--film-page', '#eef0f3')),
      card: parseColor(v('--film-card', '#ffffff')),
      raised: parseColor(v('--film-raised', v('--film-card', '#ffffff'))),
      ink: parseColor(v('--film-ink', '#1b1d21')),
      inkSoft: parseColor(v('--film-ink-soft', '#5c6370')),
      outline: parseColor(v('--film-outline', '#d6d9de')),
      accent: parseColor(v('--film-accent', '#0a9b4b')),
      onAccent: parseColor(v('--film-on-accent', '#ffffff')),
    }
    this.painters = {}
  }

  /** A colour token as a CSS colour, faded by [alpha]. */
  color(name, alpha = 1) {
    const c = typeof name === 'string' ? this.colors[name] : name
    return `rgba(${c[0]},${c[1]},${c[2]},${(c[3] ?? 1) * clamp(alpha)})`
  }

  /** The skeleton fill for shapes sitting on [surface] (a colour token name). */
  painter(surface) {
    if (this.painters[surface]) return this.painters[surface]
    const s = this.colors[surface]
    const ink = this.colors.ink
    const lighter = luminance(s) > luminance(ink)
    const base = mix(s, ink, lighter ? INK_DAY : INK_NIGHT)
    const highlight = lighter ? mix(base, s, 0.75) : mix(base, ink, 0.06)
    return (this.painters[surface] = { base, highlight })
  }

  // ── Drawing ─────────────────────────────────────────────────────────────────────────────────

  resize() {
    const r = this.canvas.getBoundingClientRect()
    this.dpr = window.devicePixelRatio || 1
    this.width = r.width
    this.height = r.height
    this.canvas.width = Math.round(r.width * this.dpr)
    this.canvas.height = Math.round(r.height * this.dpr)
  }

  draw(t = this.t) {
    const { ctx, device: d } = this
    if (!this.width || !this.height) return
    const seconds = this.elapsedMs / 1000
    this.shimmerOn = this.moving
    ctx.setTransform(this.dpr, 0, 0, this.dpr, 0, 0)
    ctx.clearRect(0, 0, this.width, this.height)

    const phoneW = Math.min(this.width, d.width)
    const phoneX = (this.width - phoneW) / 2
    const s = this.screen
    s.left = phoneX + d.bezel
    s.top = d.top + d.bezel
    s.right = phoneX + phoneW - d.bezel
    s.bottom = this.height + d.radius
    s.width = s.right - s.left
    s.height = s.bottom - s.top
    this.toolbarY = s.top + d.status + d.toolbar / 2
    this.contentScale = (s.width - INSET * 1.5) / d.contentWidth
    const glass = Math.max(d.radius - d.bezel, 0)

    // The device, still: body, screen, status bar or window bar, toolbar.
    this.round(phoneX, d.top, phoneW, this.height - d.top + d.radius, d.radius)
    ctx.fillStyle = this.color('card')
    ctx.fill()
    ctx.lineWidth = 1
    ctx.strokeStyle = this.color('outline')
    this.round(phoneX + 0.5, d.top + 0.5, phoneW - 1, this.height - d.top + d.radius - 1, d.radius)
    ctx.stroke()

    ctx.save()
    this.clipToGlass(glass)
    this.shimmer(seconds, this.width)
    ctx.fillStyle = this.color('page')
    ctx.fillRect(s.left, s.top, s.width, s.height)
    const bg = this.script.screenBackground?.(this, t)
    if (bg) { ctx.fillStyle = this.color(bg.color, bg.alpha); ctx.fillRect(s.left, s.top, s.width, s.height) }
    if (this.script.device === 'browser') this.windowBar()
    else this.circle('page', s.left + s.width / 2, s.top + d.status / 2 + 2, 5)
    this.script.drawToolbar?.(this, t)

    // The screen's content, laid out at contentWidth and scaled to the device.
    ctx.save()
    ctx.translate(s.left + INSET * 0.75, s.top + d.status + d.toolbar)
    ctx.scale(this.contentScale, this.contentScale)
    this.shimmer(seconds, d.contentWidth)
    this.script.drawScreen?.(this, t, d.contentWidth)
    ctx.restore()

    // What floats over the screen, at device size.
    this.shimmer(seconds, this.width)
    this.script.drawOverScreen?.(this, t)
    ctx.restore()

    // The bottom fades out, so the page shows through whatever is behind the canvas.
    ctx.save()
    ctx.globalCompositeOperation = 'destination-out'
    const fade = ctx.createLinearGradient(0, this.height - FADE_H, 0, this.height)
    fade.addColorStop(0, 'rgba(0,0,0,0)')
    fade.addColorStop(1, 'rgba(0,0,0,1)')
    ctx.fillStyle = fade
    ctx.fillRect(0, this.height - FADE_H, this.width, FADE_H)
    ctx.restore()
  }

  /** Clips everything after it to the screen's rounded glass: nothing spills past the frame. */
  clipToGlass(radius) {
    const s = this.screen
    this.round(s.left, s.top, s.width, s.height, radius)
    this.ctx.clip()
  }

  windowBar() {
    const s = this.screen
    const cy = s.top + this.device.status / 2
    for (let i = 0; i < 3; i++) this.circle('page', s.left + 14 + i * 14, cy, 4)
    this.bar('page', s.left + 64, cy - 8, s.width - 128, 16)
  }

  round(x, y, w, h, r) {
    const ctx = this.ctx
    ctx.beginPath()
    if (ctx.roundRect) { ctx.roundRect(x, y, w, h, r); return }
    r = Math.min(r, w / 2, h / 2)
    ctx.moveTo(x + r, y)
    ctx.arcTo(x + w, y, x + w, y + h, r)
    ctx.arcTo(x + w, y + h, x, y + h, r)
    ctx.arcTo(x, y + h, x, y, r)
    ctx.arcTo(x, y, x + w, y, r)
    ctx.closePath()
  }

  // The highlight's sweep for this frame, across a space [width] wide.
  shimmer(seconds, width) {
    const pass = (seconds % CYCLE_S) / PASS_S
    this.sweepX = this.shimmerOn && pass < 1 ? -BAND + (width + BAND * 2) * pass : -BAND * 4
  }

  skeletonFill(surface, alpha) {
    const { base, highlight } = this.painter(surface)
    const g = this.ctx.createLinearGradient(this.sweepX - BAND, 0, this.sweepX + BAND, BAND * SLANT)
    g.addColorStop(0, this.color(base, alpha))
    g.addColorStop(0.5, this.color(highlight, alpha))
    g.addColorStop(1, this.color(base, alpha))
    return g
  }

  // ── Parts for scripts ───────────────────────────────────────────────────────────────────────

  /** A skeleton bar, rounded at its ends, on [surface] ('page', 'card', 'raised'). */
  bar(surface, x, y, w, h, alpha = 1) {
    if (w <= 0 || alpha <= 0) return
    this.round(x, y, w, h, h / 2)
    this.ctx.fillStyle = this.skeletonFill(surface, alpha)
    this.ctx.fill()
  }

  /** A skeleton block with corners of [r]. */
  block(surface, x, y, w, h, r, alpha = 1) {
    if (w <= 0 || alpha <= 0) return
    this.round(x, y, w, h, r)
    this.ctx.fillStyle = this.skeletonFill(surface, alpha)
    this.ctx.fill()
  }

  /** A skeleton circle, such as an icon well or avatar. */
  circle(surface, cx, cy, r, alpha = 1) {
    if (alpha <= 0) return
    this.ctx.beginPath()
    this.ctx.arc(cx, cy, r, 0, Math.PI * 2)
    this.ctx.fillStyle = this.skeletonFill(surface, alpha)
    this.ctx.fill()
  }

  /** A filled shape in a colour token (a card, a sheet, the accent), faded by [alpha]. */
  shape(color, x, y, w, h, r, alpha = 1) {
    if (alpha <= 0) return
    this.round(x, y, w, h, r)
    this.ctx.fillStyle = this.color(color, alpha)
    this.ctx.fill()
  }

  /** A hairline edge round the shape [shape] would fill. */
  stroke(color, x, y, w, h, r, alpha = 1) {
    if (alpha <= 0) return
    this.round(x + 0.5, y + 0.5, w - 1, h - 1, r)
    this.ctx.lineWidth = 1
    this.ctx.strokeStyle = this.color(color, alpha)
    this.ctx.stroke()
  }

  /** A switch: outline track when off, accent when [on] (0 to 1), a light thumb sliding across. */
  toggle(x, cy, on, alpha = 1) {
    const w = 36, h = 20
    this.round(x, cy - h / 2, w, h, h / 2)
    this.ctx.fillStyle = this.color(mix(this.colors.outline, this.colors.accent, clamp(on)), alpha)
    this.ctx.fill()
    this.ctx.beginPath()
    this.ctx.arc(x + h / 2 + (w - h) * clamp(on), cy, h / 2 - 3, 0, Math.PI * 2)
    this.ctx.fillStyle = this.color('onAccent', alpha)
    this.ctx.fill()
  }

  /** A touch at [x], [y]: a dot where the finger lands at [at], its ring spreading until [until]. */
  touch(t, x, y, at, until, alpha = 1) {
    const spread = linear(t, at - 0.1, until)
    if (spread <= 0 || spread >= 1) return
    const ctx = this.ctx
    ctx.beginPath()
    ctx.arc(x, y, 14, 0, Math.PI * 2)
    ctx.fillStyle = this.color('ink', 0.16 * pulse(t, at - 0.1, at + 0.35) * alpha)
    ctx.fill()
    ctx.beginPath()
    ctx.arc(x, y, 14 + 22 * spread, 0, Math.PI * 2)
    ctx.fillStyle = this.color('ink', 0.12 * (1 - spread) * alpha)
    ctx.fill()
  }

  /**
   * Where a mouse pointer is at [t] along [path], a list of [second, x, y] stops in device
   * coordinates: it arrives at each stop at that second, gliding (eased) over the 0.45 s before,
   * or over a 4th element's seconds, and holds there until it leaves for the next.
   */
  pointerAt(t, path) {
    let [, x, y] = path[0]
    for (let i = 1; i < path.length; i++) {
      const [at, nx, ny, glide = 0.45] = path[i]
      const p = ease(t, at - glide, at)
      x += (nx - x) * p
      y += (ny - y) * p
    }
    return { x, y }
  }

  /**
   * A mouse pointer for desktop films, the web's stand-in for [touch]: an arrow following [path]
   * (see [pointerAt]), pressing in a little and ringing at each second in [clicks].
   */
  pointer(t, path, clicks = [], alpha = 1) {
    if (alpha <= 0) return
    const { x, y } = this.pointerAt(t, path)
    const ctx = this.ctx
    let press = 0
    for (const at of clicks) {
      press = Math.max(press, pulse(t, at - 0.08, at + 0.18))
      const spread = linear(t, at, at + 0.45)
      if (spread > 0 && spread < 1) {
        ctx.beginPath()
        ctx.arc(x, y, 6 + 14 * spread, 0, Math.PI * 2)
        ctx.fillStyle = this.color('accent', 0.22 * (1 - spread) * alpha)
        ctx.fill()
      }
    }
    ctx.save()
    ctx.translate(x, y)
    ctx.scale(1 - 0.12 * press, 1 - 0.12 * press)
    ctx.beginPath()
    ctx.moveTo(0, 0)
    ctx.lineTo(0, 16)
    ctx.lineTo(4.2, 12.2)
    ctx.lineTo(7, 18.6)
    ctx.lineTo(9.6, 17.5)
    ctx.lineTo(6.9, 11.3)
    ctx.lineTo(12, 11.3)
    ctx.closePath()
    ctx.lineJoin = 'round'
    ctx.lineWidth = 1.2
    ctx.fillStyle = this.color('ink', alpha)
    ctx.strokeStyle = this.color('card', alpha)
    ctx.fill()
    ctx.stroke()
    ctx.restore()
  }

  /** Content coordinates (as drawScreen uses them) to device coordinates, to aim a pointer or touch. */
  toDevice(x, y) {
    const s = this.screen, d = this.device
    return { x: s.left + INSET * 0.75 + x * this.contentScale, y: s.top + d.status + d.toolbar + y * this.contentScale }
  }

  /** "That did nothing": a quick side-to-side offset, starting at [at], to translate the screen by. */
  shake(t, at, seconds = 0.5, swings = 3, size = 7) {
    const p = linear(t, at, at + seconds)
    if (p <= 0 || p >= 1) return 0
    return Math.sin(p * Math.PI * 2 * swings) * (1 - p) * size
  }

  /** The screen going off, as when it locks: black fading in over [seconds] from [at], out at [until]. */
  screenOff(t, at, until, seconds = 0.7) {
    const off = ease(t, at, at + seconds) * (1 - ease(t, until, until + 0.6))
    if (off <= 0) return
    const s = this.screen
    this.ctx.fillStyle = `rgba(0,0,0,${off})`
    this.ctx.fillRect(s.left, s.top, s.width, s.height)
  }
}

// ── Timing ──────────────────────────────────────────────────────────────────────────────────────

export const clamp = (v, lo = 0, hi = 1) => Math.min(hi, Math.max(lo, v))
/** 0 before [a], 1 after [b], straight between. */
export const linear = (t, a, b) => clamp((t - a) / (b - a))
/** 0 before [a], 1 after [b], smoothstep between: for fades and moves. */
export const ease = (t, a, b) => { const p = linear(t, a, b); return p * p * (3 - 2 * p) }
/** Up and back down between [a] and [b]. */
export const pulse = (t, a, b) => Math.max(0, Math.sin(linear(t, a, b) * Math.PI))

// ── Colour ──────────────────────────────────────────────────────────────────────────────────────

let probe
/** Any CSS colour as [r, g, b, a]. */
export function parseColor(css) {
  probe ??= document.createElement('canvas').getContext('2d', { willReadFrequently: true })
  probe.clearRect(0, 0, 1, 1)
  probe.fillStyle = '#000'
  probe.fillStyle = css
  probe.fillRect(0, 0, 1, 1)
  const [r, g, b, a] = probe.getImageData(0, 0, 1, 1).data
  return [r, g, b, a / 255]
}
const mix = (a, b, p) => a.map((v, i) => (i < 3 ? Math.round(v + ((b[i] ?? 1) - v) * p) : v))
const luminance = ([r, g, b]) => {
  const ch = v => { v /= 255; return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4 }
  return 0.2126 * ch(r) + 0.7152 * ch(g) + 0.0722 * ch(b)
}

// ── The state around the film ───────────────────────────────────────────────────────────────────

/**
 * Fills [parent] with the empty state: the film, a title, a one-line description, a primary action
 * and an optional secondary one. Returns { root, film, destroy }. Style it with the .empty-film-*
 * classes (see web.md) using your design system's tokens, or render the same structure in your
 * framework with its own Button component.
 */
export function emptyFilmState(parent, { script, title, description, primary, onPrimary, secondary, onSecondary }) {
  const root = document.createElement('section')
  root.className = 'empty-film'
  const canvas = document.createElement('canvas')
  canvas.className = 'empty-film-canvas'
  const h = document.createElement('h2')
  h.className = 'empty-film-title'
  h.textContent = title
  const p = document.createElement('p')
  p.className = 'empty-film-desc'
  p.textContent = description
  root.append(canvas, h, p)
  for (const [label, onClick, variant] of [[primary, onPrimary, 'primary'], [secondary, onSecondary, 'secondary']]) {
    if (!label) continue
    const b = document.createElement('button')
    b.type = 'button'
    b.className = `empty-film-button empty-film-${variant}`
    b.textContent = label
    if (onClick) b.addEventListener('click', onClick)
    root.append(b)
  }
  parent.append(root)
  const film = new SkeletonFilm(canvas, script)
  return { root, film, destroy() { film.destroy(); root.remove() } }
}
