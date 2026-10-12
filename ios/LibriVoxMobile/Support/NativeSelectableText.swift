import SwiftUI
import UIKit

/// A read-only UIKit text surface that keeps iPhone's native word handles and
/// edit menu (Copy, Look Up, Translate, Share, and Select All).
struct NativeSelectableText: UIViewRepresentable {
    var text: String
    var textStyle: UIFont.TextStyle = .body
    var color: UIColor = .label
    var alignment: NSTextAlignment = .natural
    var maximumNumberOfLines = 0
    var accessibilityIdentifier: String? = nil

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> UITextView {
        let textView = UITextView(usingTextLayoutManager: true)
        textView.backgroundColor = .clear
        textView.isEditable = false
        textView.isSelectable = true
        textView.isScrollEnabled = false
        textView.allowsEditingTextAttributes = false
        textView.textContainerInset = .zero
        textView.textContainer.lineFragmentPadding = 0
        textView.textContainer.widthTracksTextView = true
        textView.adjustsFontForContentSizeCategory = true
        textView.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        textView.setContentHuggingPriority(.defaultLow, for: .vertical)
        return textView
    }

    func updateUIView(_ textView: UITextView, context: Context) {
        let signature = "\(text.hashValue)|\(textStyle.rawValue)|\(alignment.rawValue)|\(maximumNumberOfLines)"
        if context.coordinator.signature != signature {
            let selectedRange = textView.selectedRange
            let textWasUnchanged = textView.text == text
            textView.text = text
            textView.font = UIFont.preferredFont(forTextStyle: textStyle)
            textView.textAlignment = alignment
            textView.textContainer.maximumNumberOfLines = maximumNumberOfLines
            textView.textContainer.lineBreakMode = maximumNumberOfLines == 0 ? .byWordWrapping : .byTruncatingTail
            if textWasUnchanged,
               selectedRange.location != NSNotFound,
               NSMaxRange(selectedRange) <= (text as NSString).length {
                textView.selectedRange = selectedRange
            }
            context.coordinator.signature = signature
        }
        textView.textColor = color
        textView.accessibilityIdentifier = accessibilityIdentifier
    }

    func sizeThatFits(_ proposal: ProposedViewSize, uiView: UITextView, context: Context) -> CGSize? {
        let width = proposal.width ?? uiView.bounds.width
        guard width > 0 else { return nil }
        let size = uiView.sizeThatFits(CGSize(width: width, height: .greatestFiniteMagnitude))
        return CGSize(width: width, height: ceil(size.height))
    }

    final class Coordinator {
        var signature = ""
    }
}
