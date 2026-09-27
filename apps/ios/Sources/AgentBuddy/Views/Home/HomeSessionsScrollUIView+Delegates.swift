import UIKit

extension HomeSessionsScrollUIView: UIGestureRecognizerDelegate {
    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        true
    }
}

// MARK: - UIScrollViewDelegate (page snap at zoom 4)
//
// Stock `UIScrollView.isPagingEnabled` pages at `bounds.height`, which
// ignores `contentInset.top`/`bottom`. Our scroll view ducks below the
// dynamic island via a top inset, so paging on raw bounds would drop
// each card half-under the island. Instead we retarget the deceleration
// destination ourselves: round to the nearest integer "page" (each one
// `pageFitHeight()` tall), measured in `contentInset.top`-anchored
// coordinates, then translate back to the raw `contentOffset.y` the
// scroll view will animate to.
extension HomeSessionsScrollUIView: UIScrollViewDelegate {
    func scrollViewDidScroll(_ scrollView: UIScrollView) {
        updatePageBackgroundVisibility()
    }

    func scrollViewWillEndDragging(
        _ scrollView: UIScrollView,
        withVelocity velocity: CGPoint,
        targetContentOffset: UnsafeMutablePointer<CGPoint>
    ) {
        guard zoomLevel == 4, !isPinching else { return }
        let page = pageFitHeight()
        guard page > 0 else { return }

        let insetTop = scrollView.adjustedContentInset.top
        let pageOriginInScroll = -insetTop  // contentOffset.y when first card is on screen

        // TikTok-style snap: velocity *direction* picks the next page,
        // not the proposed target distance. With `.fast` deceleration, a
        // hard flick covers a short distance — the system's
        // `targetContentOffset` can land in the first half of the next
        // page, which a nearest-rounding snap then pulls back to the
        // current page. The user reads that as "the flick didn't take".
        //
        // Instead: figure out which page the user is leaving (the one
        // they were resting on at drag start, ≈ floor of current offset
        // relative to page), then advance ±1 page based on velocity sign.
        // A truly slow lift (no flick) falls through to nearest-rounding
        // so it still snaps to whichever page is closer.
        let currentRelative = scrollView.contentOffset.y - pageOriginInScroll
        let lowerPage = floor(currentRelative / page)
        let upperPage = lowerPage + 1

        let velocityThreshold: CGFloat = 0.1
        let targetPage: CGFloat
        if velocity.y > velocityThreshold {
            targetPage = upperPage
        } else if velocity.y < -velocityThreshold {
            targetPage = lowerPage
        } else {
            // No real flick — snap to whichever side they're closer to.
            targetPage = (currentRelative / page).rounded()
        }

        let snapped = pageOriginInScroll + targetPage * page
        let maxY = max(pageOriginInScroll, scrollView.contentSize.height - scrollView.bounds.height + scrollView.adjustedContentInset.bottom)
        let minY = pageOriginInScroll
        targetContentOffset.pointee.y = min(maxY, max(minY, snapped))
    }

    /// Cover the case where the user drags slowly and lifts without
    /// triggering deceleration — `willEndDragging` doesn't redirect that
    /// path. Without this, a careful drag rests between cards.
    func scrollViewDidEndDragging(_ scrollView: UIScrollView, willDecelerate decelerate: Bool) {
        guard !decelerate else { return }
        guard zoomLevel == 4, !isPinching else { return }
        snapToNearestPage(animated: true)
    }

    /// Belt-and-braces: if any path leaves us at a non-page offset
    /// after deceleration finishes, snap once more.
    func scrollViewDidEndDecelerating(_ scrollView: UIScrollView) {
        guard zoomLevel == 4, !isPinching else { return }
        let page = pageFitHeight()
        guard page > 0 else { return }
        let insetTop = scrollView.adjustedContentInset.top
        let relative = scrollView.contentOffset.y - (-insetTop)
        let drift = abs(relative.truncatingRemainder(dividingBy: page))
        // Within 0.5pt of an exact page boundary → already aligned.
        if drift > 0.5 && drift < (page - 0.5) {
            snapToNearestPage(animated: true)
        }
    }
}
