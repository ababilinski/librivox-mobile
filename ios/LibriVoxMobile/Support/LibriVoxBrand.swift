import SwiftUI
import UIKit
import LibriVoxCore

enum LibriVoxBrand {
    static let accentUIColor = UIColor.label

    static let accent = Color.primary
    static let switchTint = Color(uiColor: .systemGreen)

    static func surface(for _: ColorScheme) -> Color {
        Color(uiColor: .systemBackground)
    }

    static func surfaceVariant(for _: ColorScheme) -> Color {
        Color(uiColor: .secondarySystemBackground)
    }

    static func pageGradient(for colorScheme: ColorScheme) -> [Color] {
        if colorScheme == .dark {
            return [
                Color.black.opacity(0.48),
                surface(for: colorScheme),
                Color.black.opacity(0.96),
            ]
        }
        return [
            Color.white.opacity(0.72),
            surfaceVariant(for: colorScheme).opacity(0.64),
            surface(for: colorScheme),
        ]
    }
}

private struct LibriVoxAccentColorKey: EnvironmentKey {
    static let defaultValue = LibriVoxBrand.accent
}

extension EnvironmentValues {
    var libriVoxAccent: Color {
        get { self[LibriVoxAccentColorKey.self] }
        set { self[LibriVoxAccentColorKey.self] = newValue }
    }
}
