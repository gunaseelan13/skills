# iOS (SwiftUI)

`assets/ios/SkeletonFilm.swift` plays a film in SwiftUI with the same rules as the web engine: the
device frame, the rounded-glass clip, the bottom fade, the shimmer, 3 loops then a rest frame,
pausing off screen and while the app isn't active (resuming where it was), and a still rest frame
under Reduce Motion. iOS 17 or later, no dependencies. Copy these into the app rather than
re-implementing them, so films look the same on every platform:

| File | What it is |
|---|---|
| `assets/ios/SkeletonFilm.swift` | The engine: `FilmScript`, `SkeletonFilmView`, `FilmTheme`, the parts a script draws with |
| `assets/ios/EmptyFilmState.swift` | The state around the film: film slot, title, description, buttons |
| `assets/ios/ProjectsFilm.swift` | An example script with previews; a starting point for a new film |

## Pick the device

- `.phone` for features used on a phone, or any time the story is a gesture.
- `.browser` for an iPad or desktop-class app: a window with three dots and an address bar,
  520 pt wide, laid out at 640 pt.

## Wire the theme

The film draws from a `FilmTheme` in the environment. Without one it uses the system's semantic
colours (grouped backgrounds, label, separator, the accent colour), so it already follows light and
dark. Map it to your design system's colours once, at the app root or on the empty state:

```swift
ContentView()
    .environment(\.filmTheme, FilmTheme(
        page: .surfacePage,        // your design system's colours, not literals
        card: .surfaceCard,
        raised: .surfaceRaised,    // optional, defaults to card
        ink: .textPrimary,
        inkSoft: .textSecondary,
        outline: .border,
        accent: .accent,
        onAccent: .onAccent))
```

Use colours that adapt to light and dark (asset catalog colours or dynamic `UIColor`s). The film
resolves them for the current appearance every frame. Never put a hex value in a script: scripts
name tokens (`.card`, `.accent`).

## Write a script

A script is a struct conforming to `FilmScript`. Beats are constants in seconds; `pace` stretches
all of them.

```swift
struct ExampleFilm: FilmScript {
    private static let tap = 1.2, result = 2.0, fadeOut = 8.6

    let loopSeconds = 9.4
    let restSeconds = 6.4      // the frame that explains the feature on its own
    let pace = 1.3
    let device = FilmDevice.phone
    let label = "A phone showing … then …"   // the whole story, for VoiceOver

    func drawToolbar(_ f: FilmContext, _ t: Double) { /* device coordinates, f.toolbarY */ }

    func drawScreen(_ f: FilmContext, _ t: Double, _ w: CGFloat) {
        // Content coordinates: w = contentWidth (328 phone, 640 browser), scaled to fit for you.
        let alpha = ease(t, 0, 0.4) * (1 - ease(t, Self.fadeOut, Self.fadeOut + 0.6))
        f.shape(.card, 0, 16, w, 48, 16, alpha)
        f.circle(.card, 28, 40, 14, alpha)
        f.bar(.card, 54, 34, w * 0.4, 10, alpha)
    }

    func drawOverScreen(_ f: FilmContext, _ t: Double) {
        // Device coordinates over the screen (f.screen): touches, sheets, scrims.
        f.touch(t, f.screen.minX + 60, f.screen.minY + 120, Self.tap, Self.tap + 0.5)
    }
}
```

What `f` gives a script:

| Call | Draws |
|---|---|
| `f.bar(surface, x, y, w, h, alpha)` | A skeleton bar on `surface` (`.page`, `.card`, `.raised`), shimmering |
| `f.block(surface, x, y, w, h, r, alpha)` | A skeleton block with corners `r` |
| `f.circle(surface, cx, cy, r, alpha)` | A skeleton circle (icon well, avatar) |
| `f.shape(token, x, y, w, h, r, alpha)` | A solid shape in a colour token: a card, a sheet, the accent |
| `f.stroke(token, x, y, w, h, r, alpha)` | A hairline edge |
| `f.toggle(x, cy, on, alpha)` | A switch, `on` from 0 to 1 |
| `f.touch(t, x, y, at, until, alpha)` | A finger: a dot landing at `at`, its ring spreading until `until` |
| `f.pointer(t, path, clicks:, alpha)` | A pointer for desktop-style films: follows `path` (`[PointerStop(second, x, y)]`), pressing and ringing at each second in `clicks` |
| `f.toDevice(x, y)` | Content coordinates to device ones, to aim a touch or pointer at something `drawScreen` drew |
| `f.shake(t, at)` | An x offset for "that did nothing": `f.ctx.translateBy(x: f.shake(t, Self.tryAt), y: 0)` |
| `f.screenOff(t, at, until)` | The screen fading to black, as when it locks, and back |
| `f.layer { }` | Saves and restores the context round your own transforms |
| `f.ctx` | The `GraphicsContext`, for anything else |
| `f.screen`, `f.toolbarY`, `f.height` | Where the screen is, the toolbar's middle, the film's height |

Timing helpers: `ease(t, a, b)` (smoothstep, for fades and moves), `linear(t, a, b)` (for spreads
and progress), `pulse(t, a, b)` (up and back down).

Pass the surface a skeleton sits on, so it carries the right amount of ink: a bar on a card uses
`.card`, a bar straight on the screen uses `.page`.

Everything drawn in the three hooks is clipped to the screen's rounded glass. Don't draw
on-screen content outside them.

## Show it

```swift
if projects.isEmpty {
    ScrollView {
        EmptyFilmState(
            script: ProjectsFilm(),
            title: "Keep work in projects",
            description: "Group related tasks so the whole team sees what is next.",
            primary: "New Project", onPrimary: { showNewProject = true })
            // secondary: "Import", onSecondary: { showImport = true }
            .padding(.horizontal, 16)
    }
}
```

`EmptyFilmState` uses a 380 pt film slot, the title in `.title3` semibold, the description in
secondary text, a full-width `.borderedProminent` primary button 48 pt tall, and an optional
`.bordered` secondary 12 pt below it. If your design system has its own buttons or spacing scale,
swap them in that file. On a wide iPad layout, cap the state's width (around 420 pt) and centre it.

To show only the film, use `SkeletonFilmView(script)` with a height. To restart it from the first
beat (when the screen is shown again), give it a new `.id(...)`.

## Checking it

- Hold frames with `SkeletonFilmView(script, frozenAt: 4.2)` in a `#Preview` or a test host, and
  look at each beat in light and dark.
- On a simulator, `xcrun simctl ui booted appearance dark` switches appearance; `xcrun simctl io
  booted screenshot` and `recordVideo` capture it. Crop the top corners at full size: nothing may
  spill past the rounded glass, including scrims and sheets.
- Turn on Reduce Motion (Settings › Accessibility › Motion): the film must show the rest frame and
  stay still.
- Send the app to the background and back: the film pauses and resumes where it was, and stops
  after 3 loops. In a `ScrollView` on iOS 18 or later it also pauses when scrolled away; on iOS 17
  it pauses when the view leaves the hierarchy (`onDisappear`).
- VoiceOver reads the film as one image with the script's `label`.
