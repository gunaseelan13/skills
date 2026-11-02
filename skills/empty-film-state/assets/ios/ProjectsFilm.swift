import SwiftUI

/// An example script: a project list with nothing in it, a tap on New, a sheet with a name typed
/// in, Save, and the new project landing at the top of the list in the accent.
struct ProjectsFilm: FilmScript {
    // Beats, in seconds into a loop, before the pace stretches them.
    private static let tapNew = 1.2
    private static let sheet = 1.6
    private static let type = 2.4
    private static let save = 4.2
    private static let sheetOut = 4.6
    private static let lands = 4.9
    private static let fadeOut = 8.6
    private static let rowH: CGFloat = 56

    let loopSeconds = 9.4
    let restSeconds = 6.4   // the frame that explains the feature on its own
    let pace = 1.3
    let device = FilmDevice.phone
    let label = "A phone showing an empty project list, a tap on New, a name typed into the sheet " +
        "that opens, then Save, and the new project appearing at the top of the list."

    func drawToolbar(_ f: FilmContext, _ t: Double) {
        let s = f.screen
        let alpha = screenAlpha(t)
        f.circle(.page, s.minX + 24, f.toolbarY, 9, alpha)
        f.bar(.page, s.minX + 44, f.toolbarY - 6, 88, 12, alpha)
        // New, tapped at tapNew.
        f.shape(.accent, s.maxX - 52, f.toolbarY - 13, 36, 26, 13, alpha)
    }

    func drawScreen(_ f: FilmContext, _ t: Double, _ w: CGFloat) {
        let alpha = screenAlpha(t)
        let land = ease(t, Self.lands, Self.lands + 0.5)
        // The list: the new row slides in at the top and pushes the rest down.
        f.layer {
            f.ctx.translateBy(x: 0, y: Self.rowH * CGFloat(land) - Self.rowH)
            for i in 0..<4 {
                let y = 16 + CGFloat(i) * Self.rowH
                let fresh = i == 0
                let a = alpha * (fresh ? land : 0.55)
                f.shape(.card, 0, y, w, Self.rowH - 8, 16, a)
                if fresh { f.shape(.accent, 14, y + 10, 28, 28, 8, a) }
                else { f.circle(.card, 28, y + 24, 14, a) }
                f.bar(.card, 54, y + 12, fresh ? w * 0.42 : w * (0.3 + CGFloat(i) * 0.07), 10, a)
                f.bar(.card, 54, y + 28, w * 0.24, 8, a)
            }
        }
    }

    func drawOverScreen(_ f: FilmContext, _ t: Double) {
        let s = f.screen
        f.touch(t, s.maxX - 34, f.toolbarY, Self.tapNew, Self.tapNew + 0.5)

        // The sheet rises from the bottom with its field, then sinks away after Save.
        let up = ease(t, Self.sheet, Self.sheet + 0.45) * (1 - ease(t, Self.sheetOut, Self.sheetOut + 0.4))
        guard up > 0 else { return }
        f.shape(.ink, s.minX, s.minY, s.width, s.height, 0, 0.18 * up)
        let sheetH: CGFloat = 230
        let top = f.height - sheetH * CGFloat(up)
        f.shape(.card, s.minX, top, s.width, sheetH + 60, 24)
        f.bar(.card, s.midX - 16, top + 8, 32, 4)
        let fieldY = top + 34
        f.shape(.page, s.minX + 16, fieldY, s.width - 32, 44, 12)
        let typed = linear(t, Self.type, Self.type + 1.2)
        f.bar(.page, s.minX + 30, fieldY + 17, (s.width - 120) * CGFloat(typed), 10, typed > 0 ? 1 : 0)
        let press = 1 - 0.04 * sin(linear(t, Self.save - 0.1, Self.save + 0.25) * .pi)
        let bw = (s.width - 32) * CGFloat(press)
        f.shape(.accent, s.midX - bw / 2, fieldY + 60, bw, 40, 20)
        f.touch(t, s.midX, fieldY + 80, Self.save, Self.save + 0.5)
    }

    private func screenAlpha(_ t: Double) -> Double {
        ease(t, 0, 0.4) * (1 - ease(t, Self.fadeOut, Self.fadeOut + 0.6))
    }
}

#Preview("Projects, empty") {
    ScrollView {
        EmptyFilmState(
            script: ProjectsFilm(),
            title: "Keep work in projects",
            description: "Group related tasks so the whole team sees what is next.",
            primary: "New Project")
            .padding(.horizontal, 16)
    }
    .background(Color(uiColor: .systemGroupedBackground))
}

#Preview("Projects, rest frame") {
    SkeletonFilmView(ProjectsFilm(), frozenAt: ProjectsFilm().restSeconds)
        .frame(height: 380)
        .padding(16)
        .background(Color(uiColor: .systemGroupedBackground))
}
