import LibriVoxCore
import SwiftUI
import UIKit

/// A cover is an artwork affordance; the book title remains the details link.
struct CoverArtworkButton<Label: View>: View {
    @Environment(AppModel.self) private var app
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Namespace private var transition
    @State private var isPresented = false
    var book: AudioBook
    var accessibilityIdentifier: String
    var onPresentationChange: ((Bool) -> Void)? = nil
    @ViewBuilder var label: () -> Label

    var body: some View {
        Button {
            isPresented = true
        } label: {
            label()
                .contentShape(Rectangle())
                .matchedTransitionSource(id: book.id, in: transition)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("View full-screen cover for \(book.title)")
        .accessibilityHint("Opens artwork that you can zoom")
        .accessibilityIdentifier(accessibilityIdentifier)
        .fullScreenCover(isPresented: $isPresented) {
            if reduceMotion || app.settings.animationSpeed == .reduced {
                CoverArtworkViewer(book: book)
            } else {
                CoverArtworkViewer(book: book)
                    .navigationTransition(.zoom(sourceID: book.id, in: transition))
            }
        }
        .onChange(of: isPresented) { _, value in
            onPresentationChange?(value)
        }
    }
}

private struct CoverArtworkViewer: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(AppModel.self) private var app
    @StateObject private var imageLoader = CoverImageLoader(maxPixelSize: 2_560)
    @State private var didAttemptLoad = false
    var book: AudioBook

    private var coverURLs: [URL] {
        var seen = Set<URL>()
        return [book.fullCoverImageURL, book.coverImageURL].compactMap { value in
            guard let value,
                  var components = URLComponents(string: value.trimmingCharacters(in: .whitespacesAndNewlines))
            else { return nil }
            if components.host == "www.archive.org" { components.host = "archive.org" }
            guard let url = components.url, seen.insert(url).inserted else { return nil }
            return url
        }
    }

    var body: some View {
        NavigationStack {
            Group {
                if let image = imageLoader.image {
                    ZoomableCoverArtwork(
                        image: image,
                        title: book.title,
                        reduceMotion: reduceMotion || app.settings.animationSpeed == .reduced
                    )
                } else if imageLoader.isLoading || !didAttemptLoad {
                    ProgressView("Loading cover art")
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    ContentUnavailableView(
                        "Cover unavailable",
                        systemImage: "book.closed",
                        description: Text("Artwork for \(book.title) could not be loaded.")
                    )
                    .accessibilityIdentifier("cover-artwork-unavailable")
                }
            }
            .background(.black)
            .navigationTitle("Cover Art")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(.hidden, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Close cover art", systemImage: "xmark") { dismiss() }
                        .accessibilityIdentifier("cover-artwork-close")
                }
            }
            .task(id: coverURLs) {
                didAttemptLoad = false
                await imageLoader.load(urls: coverURLs)
                guard !Task.isCancelled else { return }
                didAttemptLoad = true
            }
            .onDisappear { imageLoader.cancel() }
        }
        .preferredColorScheme(.dark)
        .tint(.white)
        .accessibilityAction(.escape) { dismiss() }
    }
}

private struct ZoomableCoverArtwork: UIViewRepresentable {
    var image: UIImage
    var title: String
    var reduceMotion: Bool

    func makeUIView(context: Context) -> CoverArtworkScrollView {
        CoverArtworkScrollView()
    }

    func updateUIView(_ view: CoverArtworkScrollView, context: Context) {
        view.setImage(image)
        view.accessibilityLabel = "\(title) cover art"
        view.reduceMotion = reduceMotion
    }
}

/// UIScrollView handles pinch inertia, panning, and rotation without redrawing SwiftUI.
private final class CoverArtworkScrollView: UIScrollView, UIScrollViewDelegate {
    private let artwork = UIImageView()
    private var fittedViewport = CGSize.zero
    private var needsImageLayout = false
    private var isLayingOutImage = false
    var reduceMotion = false

    override init(frame: CGRect) {
        super.init(frame: frame)
        delegate = self
        minimumZoomScale = 1
        maximumZoomScale = 4
        backgroundColor = .black
        contentInsetAdjustmentBehavior = .never
        showsHorizontalScrollIndicator = false
        showsVerticalScrollIndicator = false
        alwaysBounceHorizontal = false
        alwaysBounceVertical = false
        artwork.contentMode = .scaleAspectFit
        addSubview(artwork)
        let doubleTap = UITapGestureRecognizer(target: self, action: #selector(toggleZoom(_:)))
        doubleTap.numberOfTapsRequired = 2
        addGestureRecognizer(doubleTap)
        isAccessibilityElement = true
        accessibilityIdentifier = "cover-artwork-image"
        accessibilityTraits = [.image, .adjustable]
        accessibilityHint = "Pinch or double-tap to zoom. Swipe up or down with one finger to adjust zoom with VoiceOver."
        updateZoomValue()
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    func setImage(_ image: UIImage) {
        guard artwork.image !== image else { return }
        artwork.image = image
        needsImageLayout = true
        setNeedsLayout()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        guard !isLayingOutImage, bounds.width > 0, bounds.height > 0,
              let image = artwork.image, image.size.width > 0, image.size.height > 0
        else { return }
        isLayingOutImage = true
        defer { isLayingOutImage = false }

        if fittedViewport != bounds.size || needsImageLayout {
            let preservedScale = needsImageLayout ? 1 : zoomScale
            fittedViewport = bounds.size
            needsImageLayout = false
            setZoomScale(1, animated: false)
            let fit = min(bounds.width / image.size.width, bounds.height / image.size.height)
            let size = CGSize(width: image.size.width * fit, height: image.size.height * fit)
            artwork.frame = CGRect(origin: .zero, size: size)
            contentSize = size
            setZoomScale(preservedScale, animated: false)
        }
        centerArtwork()
    }

    func viewForZooming(in scrollView: UIScrollView) -> UIView? { artwork }

    func scrollViewDidZoom(_ scrollView: UIScrollView) {
        centerArtwork()
        updateZoomValue()
    }

    private func centerArtwork() {
        artwork.center = CGPoint(
            x: max(bounds.width, contentSize.width) / 2,
            y: max(bounds.height, contentSize.height) / 2
        )
    }

    private func updateZoomValue() {
        accessibilityValue = "Zoom \(Int((zoomScale * 100).rounded())) percent"
    }

    @objc private func toggleZoom(_ gesture: UITapGestureRecognizer) {
        if zoomScale > 1.05 {
            setZoomScale(1, animated: !reduceMotion)
        } else {
            let point = gesture.location(in: artwork)
            let size = CGSize(width: bounds.width / 2, height: bounds.height / 2)
            zoom(to: CGRect(x: point.x - size.width / 2, y: point.y - size.height / 2, width: size.width, height: size.height), animated: !reduceMotion)
        }
    }

    override func accessibilityIncrement() {
        setZoomScale(min(maximumZoomScale, zoomScale + 0.5), animated: !reduceMotion)
    }

    override func accessibilityDecrement() {
        setZoomScale(max(minimumZoomScale, zoomScale - 0.5), animated: !reduceMotion)
    }
}
