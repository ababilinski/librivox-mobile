import LibriVoxCore
import SwiftUI
import UIKit

struct MarqueeMetrics {
    static let initialPause: Double = 1
    static let cyclePause: Double = 1
    static let gap: CGFloat = 24

    static func shouldScroll(textWidth: CGFloat, availableWidth: CGFloat, active: Bool, reduceMotion: Bool, voiceOver: Bool) -> Bool {
        active && !reduceMotion && !voiceOver && availableWidth > 0 && textWidth > availableWidth + 1
    }

    static func duration(textWidth: CGFloat, gap: CGFloat = MarqueeMetrics.gap) -> Double {
        Double(textWidth + gap) / 40
    }

    static func contentSizeCategory(for size: DynamicTypeSize) -> UIContentSizeCategory {
        switch size {
        case .xSmall: .extraSmall
        case .small: .small
        case .medium: .medium
        case .large: .large
        case .xLarge: .extraLarge
        case .xxLarge: .extraExtraLarge
        case .xxxLarge: .extraExtraExtraLarge
        case .accessibility1: .accessibilityMedium
        case .accessibility2: .accessibilityLarge
        case .accessibility3: .accessibilityExtraLarge
        case .accessibility4: .accessibilityExtraExtraLarge
        case .accessibility5: .accessibilityExtraExtraExtraLarge
        @unknown default: .large
        }
    }
}

/// The player uses these semantic fonts in both SwiftUI's static text and UIKit's ticker.
struct MarqueeFont: Equatable, Sendable {
    private enum Style: Equatable, Sendable { case subheadline, headline, title2 }
    private var style: Style
    private var isBold = false

    static let subheadline = Self(style: .subheadline)
    static let headline = Self(style: .headline)
    static let title2 = Self(style: .title2)

    func bold() -> Self {
        var result = self
        result.isBold = true
        return result
    }

    var swiftUIFont: Font {
        let font: Font
        switch style {
        case .subheadline: font = .subheadline
        case .headline: font = .headline
        case .title2: font = .title2
        }
        return isBold ? font.bold() : font
    }

    func uiFont(compatibleWith traits: UITraitCollection) -> UIFont {
        let textStyle: UIFont.TextStyle
        switch style {
        case .subheadline: textStyle = .subheadline
        case .headline: textStyle = .headline
        case .title2: textStyle = .title2
        }
        let font = UIFont.preferredFont(forTextStyle: textStyle, compatibleWith: traits)
        guard isBold, let descriptor = font.fontDescriptor.withSymbolicTraits(.traitBold) else { return font }
        return UIFont(descriptor: descriptor, size: font.pointSize)
    }
}

/// Only player labels scroll; their duplicate is never exposed to assistive technology.
struct MarqueeText: View {
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @State private var voiceOver = UIAccessibility.isVoiceOverRunning
    @State private var visible = false
    var text: String
    var font: MarqueeFont
    var motion: AnimationSpeed
    var color: Color = Color(uiColor: .label)
    var staticLineLimit: Int? = 1
    var isActive = true

    private var permitsMotion: Bool { !reduceMotion && motion != .reduced && !voiceOver }

    var body: some View {
        Text(text)
            .font(font.swiftUIFont)
            .foregroundStyle(color)
            .lineLimit(permitsMotion ? 1 : staticLineLimit)
            .fixedSize(horizontal: false, vertical: true)
            .opacity(permitsMotion ? 0 : 1)
            .overlay {
                if permitsMotion {
                    MarqueeLabelRepresentable(
                        text: text,
                        font: font,
                        color: UIColor(color),
                        active: visible && isActive && scenePhase == .active,
                        dynamicTypeSize: dynamicTypeSize
                    )
                    .allowsHitTesting(false)
                }
            }
            .contentShape(Rectangle())
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(text)
            .accessibilityAddTraits(.isStaticText)
            .onAppear { visible = true }
            .onDisappear { visible = false }
            .onReceive(NotificationCenter.default.publisher(for: UIAccessibility.voiceOverStatusDidChangeNotification)) { _ in
                voiceOver = UIAccessibility.isVoiceOverRunning
            }
    }
}

private struct MarqueeLabelRepresentable: UIViewRepresentable {
    var text: String
    var font: MarqueeFont
    var color: UIColor
    var active: Bool
    var dynamicTypeSize: DynamicTypeSize

    func makeUIView(context: Context) -> MarqueeLabelView { MarqueeLabelView() }

    func updateUIView(_ view: MarqueeLabelView, context: Context) {
        view.configure(
            text: text, font: font, color: color, active: active,
            contentSizeCategory: MarqueeMetrics.contentSizeCategory(for: dynamicTypeSize)
        )
    }

    static func dismantleUIView(_ view: MarqueeLabelView, coordinator: ()) {
        view.stopAnimating()
    }
}

/// Core Animation moves already-laid-out labels without invalidating SwiftUI on each frame.
/// Repeated playback updates leave the current animation running; only its inputs restart it.
final class MarqueeLabelView: UIView {
    private let strip = UIView()
    private let firstLabel = UILabel()
    private let secondLabel = UILabel()
    private let edgeMask = CAGradientLayer()
    private var marqueeFont = MarqueeFont.headline
    private var contentSizeCategory = UIContentSizeCategory.large
    private var active = false
    private var animationIdentity: AnimationIdentity?

    private struct AnimationIdentity: Equatable {
        var text: String
        var fontName: String
        var fontSize: CGFloat
        var width: CGFloat
        var height: CGFloat
        var scrolls: Bool
    }

    override init(frame: CGRect) {
        super.init(frame: frame)
        clipsToBounds = true
        isUserInteractionEnabled = false
        isAccessibilityElement = false
        accessibilityElementsHidden = true
        addSubview(strip)
        for label in [firstLabel, secondLabel] {
            label.numberOfLines = 1
            label.lineBreakMode = .byTruncatingTail
            strip.addSubview(label)
        }
        edgeMask.startPoint = CGPoint(x: 0, y: 0.5)
        edgeMask.endPoint = CGPoint(x: 1, y: 0.5)
        edgeMask.colors = [UIColor.black.cgColor, UIColor.black.cgColor, UIColor.clear.cgColor]
        registerForTraitChanges([UITraitPreferredContentSizeCategory.self, UITraitLegibilityWeight.self]) { (view: MarqueeLabelView, _: UITraitCollection) in
            view.updateFont()
        }
        updateFont()
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    func configure(text: String, font: MarqueeFont, color: UIColor, active: Bool, contentSizeCategory: UIContentSizeCategory = .large) {
        if firstLabel.text != text {
            firstLabel.text = text
            secondLabel.text = text
            setNeedsLayout()
        }
        if marqueeFont != font || self.contentSizeCategory != contentSizeCategory {
            marqueeFont = font
            self.contentSizeCategory = contentSizeCategory
            updateFont()
        }
        if self.active != active {
            self.active = active
            setNeedsLayout()
        }
        firstLabel.textColor = color
        secondLabel.textColor = color
    }

    private func updateFont() {
        // SwiftUI may cap Dynamic Type for the fixed-height mini player while
        // its UIKit window still carries the user's larger accessibility size.
        let fontTraits = traitCollection.modifyingTraits {
            $0.preferredContentSizeCategory = contentSizeCategory
        }
        let font = marqueeFont.uiFont(compatibleWith: fontTraits)
        firstLabel.font = font
        secondLabel.font = font
        setNeedsLayout()
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window == nil { stopAnimating() }
        setNeedsLayout()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let scale = traitCollection.displayScale > 0 ? traitCollection.displayScale : 1
        let width = (bounds.width * scale).rounded() / scale
        let textSize = firstLabel.sizeThatFits(CGSize(width: CGFloat.greatestFiniteMagnitude, height: CGFloat.greatestFiniteMagnitude))
        let textWidth = ceil(textSize.width * scale) / scale
        let scrolls = MarqueeMetrics.shouldScroll(
            textWidth: textWidth, availableWidth: width,
            active: active && window != nil, reduceMotion: false, voiceOver: false
        )
        let identity = AnimationIdentity(
            text: firstLabel.text ?? "", fontName: firstLabel.font.fontName,
            fontSize: firstLabel.font.pointSize, width: width, height: bounds.height, scrolls: scrolls
        )
        guard identity != animationIdentity else { return }
        stopAnimating()
        animationIdentity = identity

        CATransaction.begin()
        CATransaction.setDisableActions(true)
        strip.frame = bounds
        let labelWidth = scrolls ? textWidth : width
        firstLabel.frame = CGRect(x: 0, y: (bounds.height - textSize.height) / 2, width: labelWidth, height: textSize.height)
        secondLabel.frame = firstLabel.frame.offsetBy(dx: textWidth + MarqueeMetrics.gap, dy: 0)
        secondLabel.isHidden = !scrolls
        edgeMask.frame = bounds
        edgeMask.locations = [0, NSNumber(value: max(0, 1 - 8 / max(width, 1))), 1]
        layer.mask = scrolls ? edgeMask : nil
        CATransaction.commit()
        guard scrolls else { return }

        let travel = MarqueeMetrics.duration(textWidth: textWidth)
        let total = MarqueeMetrics.initialPause + travel + MarqueeMetrics.cyclePause
        let animation = CAKeyframeAnimation(keyPath: "transform.translation.x")
        animation.values = [0, 0, -(textWidth + MarqueeMetrics.gap), -(textWidth + MarqueeMetrics.gap)]
        animation.keyTimes = [0, NSNumber(value: MarqueeMetrics.initialPause / total), NSNumber(value: (MarqueeMetrics.initialPause + travel) / total), 1]
        animation.duration = total
        animation.calculationMode = .linear
        animation.repeatCount = .infinity
        // The duplicate exactly replaces the first label at the end of a cycle,
        // so resetting the layer never exposes an empty strip or a jump in the title.
        animation.beginTime = strip.layer.convertTime(CACurrentMediaTime(), from: nil)
        strip.layer.add(animation, forKey: "title-marquee")
    }

    func stopAnimating() {
        strip.layer.removeAnimation(forKey: "title-marquee")
        animationIdentity = nil
    }
}
