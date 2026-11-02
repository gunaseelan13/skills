import SwiftUI

/*
 Skeleton film for SwiftUI empty states: a short looping demo drawn as skeleton UI on the top half
 of a phone (or a browser window) that fades into the page. The same model as the web engine
 (skeleton-film.js): a script draws each frame at a second of its story, and the view handles the
 device, the glass clip, the shimmer, the fade and playback.

   SkeletonFilmView(ProjectsFilm())
       .frame(height: 380)
       .environment(\.filmTheme, FilmTheme(page: .appPage, card: .appCard, ...))

 Playback: plays `loops` times while on screen and the app is active, pauses otherwise and resumes
 where it was, then rests on the `restSeconds` frame. With Reduce Motion it shows only that frame.
 */

// MARK: - Script

/// A film: its beats as seconds into a loop, and what to draw at a second `t` of the story.
public protocol FilmScript {
    var loopSeconds: Double { get }
    /// The frame that explains the feature on its own: shown after the last loop, and with Reduce Motion.
    var restSeconds: Double { get }
    /// Stretches every beat; 1.3 is common.
    var pace: Double { get }
    var loops: Int { get }
    var device: FilmDevice { get }
    /// The whole story, in order, for VoiceOver.
    var label: String { get }

    /// Device coordinates, in the toolbar band (`f.toolbarY` is its middle).
    func drawToolbar(_ f: FilmContext, _ t: Double)
    /// Content coordinates: `w` is the device's contentWidth, scaled to fit the screen for you.
    func drawScreen(_ f: FilmContext, _ t: Double, _ w: CGFloat)
    /// Device coordinates over the screen (`f.screen`): touches, sheets, scrims, pointers.
    func drawOverScreen(_ f: FilmContext, _ t: Double)
    /// An optional colour over the page colour, behind everything on the screen.
    func screenBackground(_ f: FilmContext, _ t: Double) -> FilmBackground?
}

public extension FilmScript {
    var pace: Double { 1 }
    var loops: Int { 3 }
    var device: FilmDevice { .phone }
    func drawToolbar(_ f: FilmContext, _ t: Double) {}
    func drawScreen(_ f: FilmContext, _ t: Double, _ w: CGFloat) {}
    func drawOverScreen(_ f: FilmContext, _ t: Double) {}
    func screenBackground(_ f: FilmContext, _ t: Double) -> FilmBackground? { nil }
}

public struct FilmBackground: Sendable {
    public var color: FilmColor
    public var alpha: Double
    public init(_ color: FilmColor, alpha: Double = 1) { self.color = color; self.alpha = alpha }
}

// MARK: - Device

public enum FilmDevice: Sendable {
    /// For features used on a phone, or any story that is a gesture.
    case phone
    /// For a desktop app: a window with three dots and an address bar.
    case browser

    var spec: DeviceSpec {
        switch self {
        // Sizes in points, as the web film draws them in CSS px.
        case .phone: DeviceSpec(width: 288, top: 2, radius: 44, bezel: 8, status: 28, toolbar: 40, contentWidth: 328)
        case .browser: DeviceSpec(width: 520, top: 2, radius: 14, bezel: 6, status: 34, toolbar: 40, contentWidth: 640)
        }
    }
}

struct DeviceSpec {
    let width, top, radius, bezel, status, toolbar, contentWidth: CGFloat
}

private let fadeHeight: CGFloat = 56
private let inset: CGFloat = 16
// Shimmer: how much ink a skeleton carries over its surface, and the sweeping highlight.
private let inkDay = 0.075
private let inkNight = 0.1
private let band: CGFloat = 120
private let slant: CGFloat = 0.35
private let passSeconds = 1.6
private let cycleSeconds = 2.6

// MARK: - Theme

/// A colour the film draws with. Scripts name these, never raw colours.
public enum FilmColor: Sendable {
    case page, card, raised, ink, inkSoft, outline, accent, onAccent
}

/// The film's colours. Map them to your design system's tokens once, at the app root or on the
/// empty state: `.environment(\.filmTheme, FilmTheme(page: .surfacePage, card: .surfaceCard, ...))`.
public struct FilmTheme: Sendable {
    public var page, card, raised, ink, inkSoft, outline, accent, onAccent: Color

    public init(page: Color, card: Color, raised: Color? = nil, ink: Color, inkSoft: Color,
                outline: Color, accent: Color, onAccent: Color) {
        self.page = page; self.card = card; self.raised = raised ?? card
        self.ink = ink; self.inkSoft = inkSoft; self.outline = outline
        self.accent = accent; self.onAccent = onAccent
    }

    /// System semantic colours, so the film follows light and dark until you map your own.
    public static let system = FilmTheme(
        page: Color(uiColor: .systemGroupedBackground),
        card: Color(uiColor: .secondarySystemGroupedBackground),
        raised: Color(uiColor: .tertiarySystemGroupedBackground),
        ink: Color(uiColor: .label),
        inkSoft: Color(uiColor: .secondaryLabel),
        outline: Color(uiColor: .separator),
        accent: .accentColor,
        onAccent: .white)

    func color(_ c: FilmColor) -> Color {
        switch c {
        case .page: page
        case .card: card
        case .raised: raised
        case .ink: ink
        case .inkSoft: inkSoft
        case .outline: outline
        case .accent: accent
        case .onAccent: onAccent
        }
    }
}

private struct FilmThemeKey: EnvironmentKey {
    static let defaultValue = FilmTheme.system
}

public extension EnvironmentValues {
    var filmTheme: FilmTheme {
        get { self[FilmThemeKey.self] }
        set { self[FilmThemeKey.self] = newValue }
    }
}

// MARK: - View

/// Plays a [FilmScript]. Give it the film slot's height (380 in an empty state); it fills the width.
public struct SkeletonFilmView: View {
    private let script: any FilmScript
    private let frozenAt: Double?

    @Environment(\.filmTheme) private var theme
    @Environment(\.self) private var environment
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var onScreen = false
    @State private var banked: Double = 0        // seconds played before the current run
    @State private var runStart: Date?           // when the current run started, while moving
    @State private var finished = false

    /// - Parameter frozenAt: draws only that second of the script, shimmer included, and holds it:
    ///   for captures, tests and previews.
    public init(_ script: any FilmScript, frozenAt: Double? = nil) {
        self.script = script
        self.frozenAt = frozenAt
    }

    private var loopDuration: Double { script.loopSeconds * script.pace }
    private var moving: Bool {
        frozenAt == nil && onScreen && scenePhase == .active && !reduceMotion && !finished
    }

    public var body: some View {
        TimelineView(.animation(paused: !moving)) { timeline in
            let elapsed = banked + (runStart.map { timeline.date.timeIntervalSince($0) } ?? 0)
            Canvas { ctx, size in
                let palette = FilmPalette(theme: theme, environment: environment)
                let f = FilmContext(ctx: ctx, size: size, device: script.device.spec, palette: palette)
                if let frozenAt {
                    f.draw(script, t: frozenAt, seconds: frozenAt * script.pace, shimmer: true)
                } else if moving {
                    let playing = min(elapsed, loopDuration * Double(script.loops) - 0.001)
                    let t = playing.truncatingRemainder(dividingBy: loopDuration) / script.pace
                    f.draw(script, t: t, seconds: playing, shimmer: true)
                } else {
                    f.draw(script, t: script.restSeconds, seconds: 0, shimmer: false)
                }
            }
        }
        .mask {
            // The bottom fades out, so the page shows through: the device has no bottom edge.
            VStack(spacing: 0) {
                Rectangle()
                LinearGradient(colors: [.black, .clear], startPoint: .top, endPoint: .bottom)
                    .frame(height: fadeHeight)
            }
        }
        .onAppear { onScreen = true }
        .onDisappear { onScreen = false }
        .modifier(ScrollVisibility(onScreen: $onScreen))
        .onChange(of: moving, initial: true) { _, isMoving in
            if isMoving {
                runStart = .now
            } else if let start = runStart {
                banked += Date.now.timeIntervalSince(start)
                runStart = nil
            }
        }
        .task(id: moving) {
            // Stop after the last loop, so the timeline rests instead of ticking.
            guard moving else { return }
            let left = loopDuration * Double(script.loops) - banked
            try? await Task.sleep(for: .seconds(max(left, 0)))
            if !Task.isCancelled { finished = true }
        }
        .accessibilityElement()
        .accessibilityLabel(script.label)
        .accessibilityAddTraits(.isImage)
    }

}

/// On iOS 18 and later, also pauses when scrolled out of view inside a ScrollView.
private struct ScrollVisibility: ViewModifier {
    @Binding var onScreen: Bool
    func body(content: Content) -> some View {
        if #available(iOS 18.0, *) {
            content.onScrollVisibilityChange(threshold: 0.2) { onScreen = $0 }
        } else {
            content
        }
    }
}

// MARK: - Drawing

/// The theme resolved for this frame's environment (light or dark), with the skeleton fills.
struct FilmPalette {
    let colors: [FilmColor: Color.Resolved]

    init(theme: FilmTheme, environment: EnvironmentValues) {
        var colors: [FilmColor: Color.Resolved] = [:]
        for c in [FilmColor.page, .card, .raised, .ink, .inkSoft, .outline, .accent, .onAccent] {
            colors[c] = theme.color(c).resolve(in: environment)
        }
        self.colors = colors
    }

    subscript(_ c: FilmColor) -> Color.Resolved { colors[c]! }

    /// The skeleton's base and highlight on `surface`: a hint of ink over it.
    func skeleton(on surface: FilmColor) -> (base: Color.Resolved, highlight: Color.Resolved) {
        let s = self[surface], ink = self[.ink]
        let lighter = luminance(s) > luminance(ink)
        let base = mix(s, ink, lighter ? inkDay : inkNight)
        let highlight = lighter ? mix(base, s, 0.75) : mix(base, ink, 0.06)
        return (base, highlight)
    }
}

/// What a script draws with: the canvas, where the screen is, and the parts.
public final class FilmContext {
    /// The graphics context, for anything else. Wrap your own transforms in `layer { }`.
    public var ctx: GraphicsContext
    /// Where the screen is, in device coordinates.
    public private(set) var screen = CGRect.zero
    /// The middle of the toolbar band.
    public private(set) var toolbarY: CGFloat = 0
    /// The film's height.
    public let height: CGFloat
    public let width: CGFloat

    let device: DeviceSpec
    let palette: FilmPalette
    private var contentScale: CGFloat = 1
    private var sweepX: CGFloat = 0

    init(ctx: GraphicsContext, size: CGSize, device: DeviceSpec, palette: FilmPalette) {
        self.ctx = ctx
        self.width = size.width
        self.height = size.height
        self.device = device
        self.palette = palette
    }

    /// Runs `body`, then puts the context's transform, clip and opacity back.
    public func layer(_ body: () -> Void) {
        let saved = ctx
        body()
        ctx = saved
    }

    func draw(_ script: any FilmScript, t: Double, seconds: Double, shimmer on: Bool) {
        guard width > 0, height > 0 else { return }
        let d = device
        let phoneW = min(width, d.width)
        let phoneX = (width - phoneW) / 2
        screen = CGRect(x: phoneX + d.bezel, y: d.top + d.bezel,
                        width: phoneW - d.bezel * 2, height: height + d.radius - d.top - d.bezel)
        toolbarY = screen.minY + d.status + d.toolbar / 2
        contentScale = (screen.width - inset * 1.5) / d.contentWidth
        let glass = max(d.radius - d.bezel, 0)
        let shimmerOn = on

        // The device, still: body, screen, status bar or window bar, toolbar.
        let body = CGRect(x: phoneX, y: d.top, width: phoneW, height: height - d.top + d.radius)
        ctx.fill(round(body, d.radius), with: .color(Color(palette[.card])))
        ctx.stroke(round(body.insetBy(dx: 0.5, dy: 0.5), d.radius), with: .color(Color(palette[.outline])), lineWidth: 1)

        layer {
            // Everything on the screen is clipped to its rounded glass: nothing spills past the frame.
            ctx.clip(to: round(screen, glass))
            shimmer(seconds, width, shimmerOn)
            ctx.fill(Path(screen), with: .color(Color(palette[.page])))
            if let bg = script.screenBackground(self, t) {
                ctx.fill(Path(screen), with: .color(color(bg.color, bg.alpha)))
            }
            if script.device == .browser { windowBar() }
            else { circle(.page, screen.midX, screen.minY + d.status / 2 + 2, 5) }
            script.drawToolbar(self, t)

            // The screen's content, laid out at contentWidth and scaled to the device.
            layer {
                ctx.translateBy(x: screen.minX + inset * 0.75, y: screen.minY + d.status + d.toolbar)
                ctx.scaleBy(x: contentScale, y: contentScale)
                shimmer(seconds, d.contentWidth, shimmerOn)
                script.drawScreen(self, t, d.contentWidth)
            }

            // What floats over the screen, at device size.
            shimmer(seconds, width, shimmerOn)
            script.drawOverScreen(self, t)
        }
    }

    private func windowBar() {
        let cy = screen.minY + device.status / 2
        for i in 0..<3 { circle(.page, screen.minX + 14 + CGFloat(i) * 14, cy, 4) }
        bar(.page, screen.minX + 64, cy - 8, screen.width - 128, 16)
    }

    // The highlight's sweep for this frame, across a space `space` wide.
    private func shimmer(_ seconds: Double, _ space: CGFloat, _ on: Bool) {
        let pass = seconds.truncatingRemainder(dividingBy: cycleSeconds) / passSeconds
        sweepX = on && pass < 1 ? -band + (space + band * 2) * CGFloat(pass) : -band * 4
    }

    private func skeletonFill(_ surface: FilmColor, _ alpha: Double) -> GraphicsContext.Shading {
        let (base, highlight) = palette.skeleton(on: surface)
        let a = clamp(alpha)
        return .linearGradient(
            Gradient(colors: [Color(base).opacity(a), Color(highlight).opacity(a), Color(base).opacity(a)]),
            startPoint: CGPoint(x: sweepX - band, y: 0),
            endPoint: CGPoint(x: sweepX + band, y: band * slant))
    }

    private func round(_ r: CGRect, _ radius: CGFloat) -> Path {
        Path(roundedRect: r, cornerRadius: min(radius, r.width / 2, r.height / 2), style: .circular)
    }

    /// A colour token, faded by `alpha`.
    public func color(_ c: FilmColor, _ alpha: Double = 1) -> Color {
        Color(palette[c]).opacity(clamp(alpha))
    }

    // MARK: Parts for scripts

    /// A skeleton bar, rounded at its ends, on `surface` (.page, .card, .raised), shimmering.
    public func bar(_ surface: FilmColor, _ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat, _ alpha: Double = 1) {
        guard w > 0, alpha > 0 else { return }
        ctx.fill(round(CGRect(x: x, y: y, width: w, height: h), h / 2), with: skeletonFill(surface, alpha))
    }

    /// A skeleton block with corners of `r`.
    public func block(_ surface: FilmColor, _ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat, _ r: CGFloat, _ alpha: Double = 1) {
        guard w > 0, alpha > 0 else { return }
        ctx.fill(round(CGRect(x: x, y: y, width: w, height: h), r), with: skeletonFill(surface, alpha))
    }

    /// A skeleton circle, such as an icon well or avatar.
    public func circle(_ surface: FilmColor, _ cx: CGFloat, _ cy: CGFloat, _ r: CGFloat, _ alpha: Double = 1) {
        guard alpha > 0 else { return }
        ctx.fill(Path(ellipseIn: CGRect(x: cx - r, y: cy - r, width: r * 2, height: r * 2)), with: skeletonFill(surface, alpha))
    }

    /// A filled shape in a colour token (a card, a sheet, the accent), faded by `alpha`.
    public func shape(_ c: FilmColor, _ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat, _ r: CGFloat, _ alpha: Double = 1) {
        guard alpha > 0 else { return }
        ctx.fill(round(CGRect(x: x, y: y, width: w, height: h), r), with: .color(color(c, alpha)))
    }

    /// A hairline edge round the shape `shape` would fill.
    public func stroke(_ c: FilmColor, _ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat, _ r: CGFloat, _ alpha: Double = 1) {
        guard alpha > 0 else { return }
        ctx.stroke(round(CGRect(x: x + 0.5, y: y + 0.5, width: w - 1, height: h - 1), r), with: .color(color(c, alpha)), lineWidth: 1)
    }

    /// A switch: outline track when off, accent when `on` (0 to 1), a light thumb sliding across.
    public func toggle(_ x: CGFloat, _ cy: CGFloat, _ on: Double, _ alpha: Double = 1) {
        let w: CGFloat = 36, h: CGFloat = 20, p = clamp(on)
        let track = mix(palette[.outline], palette[.accent], p)
        ctx.fill(round(CGRect(x: x, y: cy - h / 2, width: w, height: h), h / 2), with: .color(Color(track).opacity(clamp(alpha))))
        let cx = x + h / 2 + (w - h) * CGFloat(p), r = h / 2 - 3
        ctx.fill(Path(ellipseIn: CGRect(x: cx - r, y: cy - r, width: r * 2, height: r * 2)), with: .color(color(.onAccent, alpha)))
    }

    /// A touch at `x`, `y`: a dot where the finger lands at `at`, its ring spreading until `until`.
    public func touch(_ t: Double, _ x: CGFloat, _ y: CGFloat, _ at: Double, _ until: Double, _ alpha: Double = 1) {
        let spread = linear(t, at - 0.1, until)
        guard spread > 0, spread < 1 else { return }
        ctx.fill(Path(ellipseIn: CGRect(x: x - 14, y: y - 14, width: 28, height: 28)),
                 with: .color(color(.ink, 0.16 * pulse(t, at - 0.1, at + 0.35) * alpha)))
        let r = 14 + 22 * CGFloat(spread)
        ctx.fill(Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)),
                 with: .color(color(.ink, 0.12 * (1 - spread) * alpha)))
    }

    /// Where a mouse pointer is at `t` along `path`, a list of stops in device coordinates: it
    /// arrives at each stop at that second, gliding (eased) over the `glide` seconds before, and
    /// holds there until it leaves for the next.
    public func pointerAt(_ t: Double, _ path: [PointerStop]) -> CGPoint {
        guard var p = path.first?.point else { return .zero }
        for stop in path.dropFirst() {
            let k = CGFloat(ease(t, stop.at - stop.glide, stop.at))
            p.x += (stop.point.x - p.x) * k
            p.y += (stop.point.y - p.y) * k
        }
        return p
    }

    /// A mouse pointer for desktop films, the stand-in for `touch`: an arrow following `path` (see
    /// `pointerAt`), pressing in a little and ringing at each second in `clicks`.
    public func pointer(_ t: Double, _ path: [PointerStop], clicks: [Double] = [], _ alpha: Double = 1) {
        guard alpha > 0 else { return }
        let p = pointerAt(t, path)
        var press = 0.0
        for at in clicks {
            press = max(press, pulse(t, at - 0.08, at + 0.18))
            let spread = linear(t, at, at + 0.45)
            if spread > 0, spread < 1 {
                let r = 6 + 14 * CGFloat(spread)
                ctx.fill(Path(ellipseIn: CGRect(x: p.x - r, y: p.y - r, width: r * 2, height: r * 2)),
                         with: .color(color(.accent, 0.22 * (1 - spread) * alpha)))
            }
        }
        var arrow = Path()
        arrow.move(to: .zero)
        for (x, y) in [(0, 16), (4.2, 12.2), (7, 18.6), (9.6, 17.5), (6.9, 11.3), (12, 11.3)] as [(CGFloat, CGFloat)] {
            arrow.addLine(to: CGPoint(x: x, y: y))
        }
        arrow.closeSubpath()
        let k = 1 - 0.12 * CGFloat(press)
        let placed = arrow.applying(CGAffineTransform(translationX: p.x, y: p.y).scaledBy(x: k, y: k))
        ctx.fill(placed, with: .color(color(.ink, alpha)))
        ctx.stroke(placed, with: .color(color(.card, alpha)), style: StrokeStyle(lineWidth: 1.2, lineJoin: .round))
    }

    /// Content coordinates (as drawScreen uses them) to device coordinates, to aim a pointer or touch.
    public func toDevice(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
        CGPoint(x: screen.minX + inset * 0.75 + x * contentScale,
                y: screen.minY + device.status + device.toolbar + y * contentScale)
    }

    /// "That did nothing": a quick side-to-side offset, starting at `at`, to translate the screen by.
    public func shake(_ t: Double, _ at: Double, seconds: Double = 0.5, swings: Double = 3, size: CGFloat = 7) -> CGFloat {
        let p = linear(t, at, at + seconds)
        guard p > 0, p < 1 else { return 0 }
        return CGFloat(sin(p * .pi * 2 * swings) * (1 - p)) * size
    }

    /// The screen going off, as when it locks: black fading in over `seconds` from `at`, out at `until`.
    public func screenOff(_ t: Double, _ at: Double, _ until: Double, seconds: Double = 0.7) {
        let off = ease(t, at, at + seconds) * (1 - ease(t, until, until + 0.6))
        guard off > 0 else { return }
        ctx.fill(Path(screen), with: .color(.black.opacity(off)))
    }
}

/// A stop on a pointer's path: it arrives at `point` at second `at`, gliding over `glide` seconds.
public struct PointerStop: Sendable {
    public var at: Double
    public var point: CGPoint
    public var glide: Double
    public init(_ at: Double, _ x: CGFloat, _ y: CGFloat, glide: Double = 0.45) {
        self.at = at; self.point = CGPoint(x: x, y: y); self.glide = glide
    }
}

// MARK: - Timing

public func clamp(_ v: Double, _ lo: Double = 0, _ hi: Double = 1) -> Double { min(hi, max(lo, v)) }
/// 0 before `a`, 1 after `b`, straight between: for spreads and progress.
public func linear(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a)) }
/// 0 before `a`, 1 after `b`, smoothstep between: for fades and moves.
public func ease(_ t: Double, _ a: Double, _ b: Double) -> Double { let p = linear(t, a, b); return p * p * (3 - 2 * p) }
/// Up and back down between `a` and `b`.
public func pulse(_ t: Double, _ a: Double, _ b: Double) -> Double { max(0, sin(linear(t, a, b) * .pi)) }

// MARK: - Colour

private func mix(_ a: Color.Resolved, _ b: Color.Resolved, _ p: Double) -> Color.Resolved {
    let p = Float(p)
    return Color.Resolved(colorSpace: .sRGB,
                          red: a.red + (b.red - a.red) * p,
                          green: a.green + (b.green - a.green) * p,
                          blue: a.blue + (b.blue - a.blue) * p,
                          opacity: a.opacity + (b.opacity - a.opacity) * p)
}

private func luminance(_ c: Color.Resolved) -> Double {
    Double(0.2126 * c.linearRed + 0.7152 * c.linearGreen + 0.0722 * c.linearBlue)
}
