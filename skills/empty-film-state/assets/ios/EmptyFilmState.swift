import SwiftUI

/// An empty state or feature explainer: the film, a title, a one-line description, a primary
/// action and an optional quieter one. Swap the spacing below for your design system's tokens,
/// and the button styles for its own buttons if it has them.
public struct EmptyFilmState: View {
    private let script: any FilmScript
    private let title: String
    private let description: String
    private let primary: (label: String, action: () -> Void)?
    private let secondary: (label: String, action: () -> Void)?

    // The spacing scale: xs < sm < md < lg < xl.
    private enum Space {
        static let xs: CGFloat = 4
        static let md: CGFloat = 12
        static let lg: CGFloat = 16
        static let xl: CGFloat = 24
    }
    private static let filmHeight: CGFloat = 380
    private static let buttonHeight: CGFloat = 48
    // The vertical padding .bordered and .borderedProminent add round their label at the regular size.
    private static let buttonPadding: CGFloat = 14

    public init(script: any FilmScript, title: String, description: String,
                primary: String? = nil, onPrimary: @escaping () -> Void = {},
                secondary: String? = nil, onSecondary: @escaping () -> Void = {}) {
        self.script = script
        self.title = title
        self.description = description
        self.primary = primary.map { ($0, onPrimary) }
        self.secondary = secondary.map { ($0, onSecondary) }
    }

    public var body: some View {
        VStack(spacing: 0) {
            SkeletonFilmView(script)
                .frame(maxWidth: .infinity)
                .frame(height: Self.filmHeight)
            Text(title)
                .font(.title3.weight(.semibold))
                .multilineTextAlignment(.center)
                .padding(.top, Space.md)
            Text(description)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, Space.lg)
                .padding(.top, Space.xs)
            if let primary {
                Button(action: primary.action) {
                    Text(primary.label).frame(maxWidth: .infinity, minHeight: Self.buttonHeight - Self.buttonPadding)
                }
                .buttonStyle(.borderedProminent)
                .padding(.top, Space.xl)
            }
            if let secondary {
                Button(action: secondary.action) {
                    Text(secondary.label).frame(maxWidth: .infinity, minHeight: Self.buttonHeight - Self.buttonPadding)
                }
                .buttonStyle(.bordered)
                .padding(.top, Space.md)
            }
        }
        .padding(.top, Space.xl)
    }
}
