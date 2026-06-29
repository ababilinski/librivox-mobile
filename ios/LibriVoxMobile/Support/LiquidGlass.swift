import LibriVoxCore
import SwiftUI

struct GlassArtworkBackground: View {
    var book: AudioBook?

    private var coverURL: URL? {
        let sourceBook = book ?? .theJungle
        let urlString = sourceBook.fullCoverImageURL ?? sourceBook.coverImageURL
        return urlString.flatMap(URL.init(string:))
    }

    var body: some View {
        ZStack {
            Color(.systemBackground)

            if let coverURL {
                AsyncImage(url: coverURL, transaction: Transaction(animation: .easeInOut(duration: 0.35))) { phase in
                    switch phase {
                    case .empty:
                        Color(.secondarySystemBackground)
                    case .success(let image):
                        image
                            .resizable()
                            .scaledToFill()
                            .scaleEffect(1.14)
                            .blur(radius: 34)
                            .saturation(1.18)
                            .opacity(0.46)
                    case .failure:
                        Color(.secondarySystemBackground)
                    @unknown default:
                        Color(.secondarySystemBackground)
                    }
                }
            }

            Rectangle()
                .fill(.regularMaterial)
                .opacity(0.56)

            Color(.systemBackground)
                .opacity(0.22)
        }
        .ignoresSafeArea()
    }
}

extension View {
    @ViewBuilder
    func liquidGlassPanel(cornerRadius: CGFloat = 28, interactive: Bool = true) -> some View {
        if #available(iOS 26.0, *) {
            self
                .glassEffect(
                    interactive ? .regular.interactive() : .regular,
                    in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                )
                .glassPanelChrome(cornerRadius: cornerRadius)
        } else {
            self
                .background(
                    .ultraThinMaterial,
                    in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                )
                .glassPanelChrome(cornerRadius: cornerRadius)
        }
    }

    func glassPageBackground(book: AudioBook? = nil) -> some View {
        self
            .scrollContentBackground(.hidden)
            .background {
                GlassArtworkBackground(book: book)
            }
            .toolbarBackground(.hidden, for: .navigationBar)
    }

    private func glassPanelChrome(cornerRadius: CGFloat) -> some View {
        self
            .overlay {
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .stroke(.white.opacity(0.28), lineWidth: 0.8)
            }
            .shadow(color: .black.opacity(0.14), radius: 20, x: 0, y: 10)
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
