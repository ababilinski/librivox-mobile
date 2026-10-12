import LibriVoxCore
import SwiftUI

struct GlassArtworkBackground: View {
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    var book: AudioBook?

    private var coverURL: URL? {
        guard let book else { return nil }
        let urlString = book.fullCoverImageURL ?? book.coverImageURL
        return urlString.flatMap(URL.init(string:))
    }

    var body: some View {
        ZStack {
            LibriVoxBrand.surface(for: colorScheme)

            if let coverURL {
                AsyncImage(
                    url: coverURL,
                    transaction: Transaction(animation: reduceMotion ? nil : .easeInOut(duration: 0.35))
                ) { phase in
                    switch phase {
                    case .empty:
                        LibriVoxBrand.surfaceVariant(for: colorScheme)
                    case .success(let image):
                        image
                            .resizable()
                            .scaledToFill()
                            .scaleEffect(1.14)
                            .blur(radius: 34)
                            .saturation(1.18)
                            .opacity(0.46)
                    case .failure:
                        LibriVoxBrand.surfaceVariant(for: colorScheme)
                    @unknown default:
                        LibriVoxBrand.surfaceVariant(for: colorScheme)
                    }
                }
            }

            if book != nil {
                Rectangle()
                    .fill(.regularMaterial)
                    .opacity(0.56)

                LinearGradient(
                    colors: LibriVoxBrand.pageGradient(for: colorScheme),
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .opacity(colorScheme == .dark ? 0.58 : 0.42)
            }
        }
        .ignoresSafeArea()
    }
}

extension View {
    @ViewBuilder
    func liquidGlassPanel(cornerRadius: CGFloat = 28, interactive: Bool = true) -> some View {
        if interactive {
            self
                .glassEffect(
                    .regular.interactive(),
                    in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                )
        } else {
            self
                .background(
                    .thinMaterial,
                    in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                )
        }
    }

    func contentMaterialPanel(cornerRadius: CGFloat = 28) -> some View {
        background(
            .thinMaterial,
            in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
        )
    }

    func glassPageBackground(book: AudioBook? = nil) -> some View {
        self
            .scrollContentBackground(.hidden)
            .background {
                GlassArtworkBackground(book: book)
            }
    }

    func appMotionPolicy(reduceMotion: Bool, preference: AnimationSpeed) -> some View {
        transaction { transaction in
            if reduceMotion || preference == .reduced {
                transaction.animation = nil
                transaction.disablesAnimations = true
            }
        }
    }
}

enum LibriVoxMotion {
    static func presentation(reduceMotion: Bool, preference: AnimationSpeed) -> Animation? {
        guard !reduceMotion, preference != .reduced else { return nil }
        switch preference {
        case .reduced:
            return nil
        case .standard:
            return .spring(response: 0.34, dampingFraction: 0.9)
        case .expressive:
            return .spring(response: 0.42, dampingFraction: 0.78)
        }
    }

    static func brief(reduceMotion: Bool, preference: AnimationSpeed) -> Animation? {
        guard !reduceMotion, preference != .reduced else { return nil }
        switch preference {
        case .reduced:
            return nil
        case .standard:
            return .smooth(duration: 0.22)
        case .expressive:
            return .spring(response: 0.3, dampingFraction: 0.82)
        }
    }
}

struct LiquidGlassGroup<Content: View>: View {
    var spacing: CGFloat = 12
    @ViewBuilder var content: () -> Content

    var body: some View {
        if #available(iOS 26.0, *) {
            GlassEffectContainer(spacing: spacing) {
                content()
            }
        } else {
            content()
        }
    }
}

struct GlassCircleIcon: View {
    @Environment(\.isEnabled) private var isEnabled
    @Environment(\.colorScheme) private var colorScheme

    var systemImage: String
    var size: CGFloat
    var symbolSize: CGFloat
    var material: Glass = .regular
    var foreground: Color = .primary
    var isSelected = false
    var selectedTint: Color = LibriVoxBrand.accent
    var prominentForeground: Color? = nil
    var isProminent = false

    var body: some View {
        let activeForeground = isSelected || isProminent ? selectedTint : foreground
        let displayedForeground = isEnabled ? activeForeground : .primary.opacity(0.34)
        let prominentForeground = prominentForeground ?? LibriVoxBrand.surface(for: colorScheme)

        Image(systemName: systemImage)
            .font(.system(size: symbolSize, weight: .semibold))
            .foregroundStyle(isProminent ? prominentForeground : displayedForeground)
            .frame(width: size, height: size)
            .glassEffect(glass, in: Circle())
            .opacity(isEnabled ? 1 : 0.62)
            .contentShape(Circle())
    }

    private var glass: Glass {
        if isProminent {
            return material.tint(selectedTint).interactive()
        }
        if isSelected {
            return material.tint(selectedTint.opacity(0.28)).interactive()
        }
        return material.interactive()
    }
}
