# Web

`assets/skeleton-film.js` is a framework-free ES module that plays a film on a `<canvas>` with the
rules in SKILL.md: the device frame, the rounded-glass clip, the bottom fade, the
shimmer, 3 loops then a rest frame, pausing off screen and in hidden tabs, and a still rest frame
under `prefers-reduced-motion`. Copy it into the project (with `example-film.js` as a starting
script) rather than re-implementing it, so films look the same on every product.

Two examples come with it. Serve the folder (`python3 -m http.server`) and open either page;
add `?t=6.4` to hold one second of the script, and `&theme=dark` for dark mode.

| Example | Device | Story | Page |
|---|---|---|---|
| `webapp-film.js` | browser | A desktop app with no projects: the pointer clicks New project, types a name, picks a colour, clicks Create, and the project lands in the list and the sidebar | `webapp-demo.html`: a full app page (sidebar, top bar, search) with the state in its main area |
| `example-film.js` | phone | A tap on New, a name typed into a bottom sheet, Save, the project landing at the top | `demo.html`: the state on its own |

Start from `webapp-film.js` for a desktop web app and `example-film.js` for a mobile web page.
`webapp-film.js` shows the pointer pattern: one `f.pointer` path through every control, a click
on each, and the dialog drawn in `drawOverScreen` so it sits over the whole window.

## Pick the device

- `device: 'phone'` for features used on mobile, or any time the story is a gesture.
- `device: 'browser'` for a desktop web app: a window with a bar of three dots and an address
  bar, 520 px wide, laid out at 640 px. Same rules otherwise.

## Wire the theme

The film reads colours from CSS custom properties, so map them to your design system's tokens
once, on the empty state or the app root:

```css
.empty-film {
  --film-page: var(--color-surface-page);
  --film-card: var(--color-surface-card);
  --film-raised: var(--color-surface-raised);  /* optional, defaults to card */
  --film-ink: var(--color-text-primary);
  --film-ink-soft: var(--color-text-secondary);
  --film-outline: var(--color-border);
  --film-accent: var(--color-accent);
  --film-on-accent: var(--color-on-accent);
}
```

Use your real token names. Never put hex in a script; scripts name tokens (`'card'`, `'accent'`).
The film re-reads the tokens when the colour scheme changes or `<html>`'s `class`, `data-theme` or
`style` changes. If your theme switches some other way, call `film.readTheme()` and then
`film.draw()`.

## Write a script

A script is a plain object. Beats are constants in seconds; `pace` stretches all of them.

```js
import { ease, linear } from './skeleton-film.js'

const TAP = 1.2, RESULT = 2.0, FADE_OUT = 8.6

export const exampleFilm = {
  loopSeconds: 9.4,
  restSeconds: 6.4,      // the frame that explains the feature on its own
  pace: 1.3,
  device: 'phone',
  label: 'A phone showing … then …',   // the whole story, for screen readers

  drawToolbar(f, t) { /* device coordinates, in the toolbar band (f.toolbarY) */ },
  drawScreen(f, t, w) {
    // Content coordinates: w = contentWidth (328 phone, 640 browser), scaled to fit for you.
    const alpha = ease(t, 0, 0.4) * (1 - ease(t, FADE_OUT, FADE_OUT + 0.6))
    f.shape('card', 0, 16, w, 48, 16, alpha)
    f.circle('card', 28, 40, 14, alpha)
    f.bar('card', 54, 34, w * 0.4, 10, alpha)
  },
  drawOverScreen(f, t) {
    // Device coordinates over the screen (f.screen.left/top/right/width): touches, sheets, scrims.
    f.touch(t, f.screen.left + 60, f.screen.top + 120, TAP, TAP + 0.5)
  },
  screenBackground(f, t) { return null },   // optional: { color: 'card', alpha } over the page colour
}
```

What `f` gives a script:

| Call | Draws |
|---|---|
| `f.bar(surface, x, y, w, h, alpha)` | A skeleton bar on `surface` (`'page'`, `'card'`, `'raised'`), shimmering |
| `f.block(surface, x, y, w, h, r, alpha)` | A skeleton block with corners `r` |
| `f.circle(surface, cx, cy, r, alpha)` | A skeleton circle (icon well, avatar) |
| `f.shape(token, x, y, w, h, r, alpha)` | A solid shape in a colour token: a card, a sheet, the accent |
| `f.stroke(token, x, y, w, h, r, alpha)` | A hairline edge |
| `f.toggle(x, cy, on, alpha)` | A switch, `on` from 0 to 1 |
| `f.touch(t, x, y, at, until, alpha)` | A finger: a dot landing at `at`, its ring spreading until `until` |
| `f.pointer(t, path, clicks, alpha)` | A mouse pointer for desktop films: follows `path` (`[[second, x, y], …]`, arriving at each stop at that second), pressing and ringing at each second in `clicks` |
| `f.toDevice(x, y)` | Content coordinates to device ones, to aim a pointer or touch at something `drawScreen` drew |
| `f.shake(t, at)` | An x offset for "that did nothing": `f.ctx.translate(f.shake(t, TRY), 0)` |
| `f.screenOff(t, at, until)` | The screen fading to black, as when it locks, and back |
| `f.ctx` | The 2D context, for anything else (save/restore around your own transforms) |
| `f.screen`, `f.toolbarY`, `f.height` | Where the screen is, the toolbar's middle, the canvas height |

Timing helpers: `ease(t, a, b)` (smoothstep, for fades and moves), `linear(t, a, b)` (for spreads
and progress), `pulse(t, a, b)` (up and back down).

Pass the surface a skeleton sits on, so it carries the right amount of ink: a bar on a card uses
`'card'`, a bar straight on the screen uses `'page'`.

Everything drawn in the three hooks is clipped to the screen's rounded glass. Don't draw
on-screen content outside them.

## Show it

Plain DOM:

```js
import { emptyFilmState } from './skeleton-film.js'
import { exampleFilm } from './example-film.js'

const state = emptyFilmState(container, {
  script: exampleFilm,
  title: 'Keep work in projects',
  description: 'Group related tasks so the whole team sees what is next.',
  primary: 'New Project', onPrimary: openNewProject,
  // secondary: 'Import', onSecondary: openImport,
})
// later: state.destroy()
```

In a framework, render the same structure with the design system's own heading, text and
`Button` components, and create the film on mount:

```jsx
function EmptyFilmState({ script, title, description, primary, onPrimary, secondary, onSecondary }) {
  const canvas = useRef(null)
  useEffect(() => {
    const film = new SkeletonFilm(canvas.current, script)
    return () => film.destroy()
  }, [script])
  return (
    <section className="empty-film">
      <canvas ref={canvas} className="empty-film-canvas" />
      <h2 className="empty-film-title">{title}</h2>
      <p className="empty-film-desc">{description}</p>
      {primary && <Button className="empty-film-primary" onClick={onPrimary}>{primary}</Button>}
      {secondary && <Button variant="secondary" className="empty-film-secondary" onClick={onSecondary}>{secondary}</Button>}
    </section>
  )
}
```

Layout CSS, with the design system's spacing tokens in place of these names:

```css
.empty-film { display: flex; flex-direction: column; align-items: center; padding-top: var(--space-xl); }
.empty-film-canvas { display: block; width: 100%; height: 380px; }
.empty-film-title { margin: var(--space-md) 0 0; text-align: center; }
.empty-film-desc { margin: var(--space-xs) 0 0; padding: 0 var(--space-lg); text-align: center; color: var(--color-text-secondary); }
.empty-film-primary, .empty-film-secondary { width: 100%; }
.empty-film-primary { margin-top: var(--space-xl); }
.empty-film-secondary { margin-top: var(--space-md); }
```

On a wide desktop page, cap the state's width (around 420 px) and centre it, rather than stretching
the buttons across the page.

## Checking it

- Hold frames with `?t=` (or `film.frame(t)`) and look at each beat in light and dark.
- Headless Chrome screenshots work for held frames (`--headless=new --screenshot
  --window-size=500,760`; it lays out at 500 px wide at least). Its `--virtual-time-budget`
  barely runs animation frames, so check live playback in a real browser.
- Turn on reduced motion in the OS: the film must show the rest frame and stay still.
- Scroll it off screen and back: it pauses and resumes, and stops after 3 loops.
