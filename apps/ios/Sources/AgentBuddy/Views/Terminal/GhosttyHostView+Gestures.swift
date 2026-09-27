import UIKit

extension GhosttyHostView {
    // MARK: - Gestures

    func installGestureRecognizers() {
        let scroll = UIPanGestureRecognizer(target: self, action: #selector(handleScrollPan(_:)))
        scroll.minimumNumberOfTouches = 1
        scroll.maximumNumberOfTouches = 2
        scroll.cancelsTouchesInView = false
        scroll.delegate = self
        addGestureRecognizer(scroll)
        scrollPan = scroll

        let drag = UIPanGestureRecognizer(target: self, action: #selector(handleDragPan(_:)))
        drag.minimumNumberOfTouches = 1
        drag.maximumNumberOfTouches = 1
        drag.cancelsTouchesInView = false
        drag.delegate = self
        addGestureRecognizer(drag)
        dragPan = drag

        let tap = UITapGestureRecognizer(target: self, action: #selector(handleTap(_:)))
        tap.cancelsTouchesInView = false
        tap.delegate = self
        addGestureRecognizer(tap)
        self.tap = tap

        let longPress = UILongPressGestureRecognizer(target: self, action: #selector(handleLongPress(_:)))
        longPress.minimumPressDuration = 0.32
        longPress.allowableMovement = 12
        longPress.cancelsTouchesInView = false
        longPress.delegate = self
        addGestureRecognizer(longPress)
        self.longPress = longPress

        let pinch = UIPinchGestureRecognizer(target: self, action: #selector(handlePinch(_:)))
        pinch.cancelsTouchesInView = false
        pinch.delegate = self
        addGestureRecognizer(pinch)
        self.pinch = pinch
    }

    @objc private func handleTap(_ gesture: UITapGestureRecognizer) {
        // If a selection exists, a tap dismisses it. Otherwise check for
        // a tappable hyperlink under the touch point; absent that, focus
        // the keyboard.
        if renderer?.currentSelectionRange() != nil {
            renderer?.selectionClear()
            return
        }
        let location = gesture.location(in: self)
        if let link = linkAtPoint(viewPoint: location), let url = URL(string: link.url) {
            UIApplication.shared.open(url, options: [:], completionHandler: nil)
            return
        }
        focusTerminalInput(forceRefresh: true)
    }

    private func linkAtPoint(viewPoint: CGPoint) -> TerminalLink? {
        renderer?.updateViewportLinks()
        let scale = contentScale
        return renderer?.linkAtPoint(x: viewPoint.x * scale, y: viewPoint.y * scale)
    }

    @objc private func handleScrollPan(_ gesture: UIPanGestureRecognizer) {
        guard let renderer else { return }
        // While selecting (long-press), the scroll pan stays disabled so
        // the user's drag extends the selection instead.
        if selectionDragInProgress { return }
        if renderer.mouseCaptured && gesture.numberOfTouches == 1 { return }
        switch gesture.state {
        case .began:
            lastScrollTranslation = .zero
        case .changed:
            let translation = gesture.translation(in: self)
            let dx = Double(translation.x - lastScrollTranslation.x)
            let dy = Double(translation.y - lastScrollTranslation.y)
            lastScrollTranslation = translation
            renderer.sendMouseScroll(x: dx, y: dy, precise: true)
        case .ended, .cancelled, .failed:
            lastScrollTranslation = .zero
        default:
            break
        }
    }

    @objc private func handleDragPan(_ gesture: UIPanGestureRecognizer) {
        guard let renderer, renderer.mouseCaptured else {
            if dragInProgress {
                renderer?.sendMouseButton(pressed: false, button: 1)
                dragInProgress = false
            }
            return
        }
        let scale = contentScale
        let location = gesture.location(in: self)
        let px = Double(location.x * scale)
        let py = Double(location.y * scale)
        switch gesture.state {
        case .began:
            renderer.sendMousePos(x: px, y: py)
            renderer.sendMouseButton(pressed: true, button: 1)
            dragInProgress = true
        case .changed:
            renderer.sendMousePos(x: px, y: py)
        case .ended, .cancelled, .failed:
            renderer.sendMouseButton(pressed: false, button: 1)
            dragInProgress = false
        default:
            break
        }
    }

    @objc private func handleLongPress(_ gesture: UILongPressGestureRecognizer) {
        guard let renderer else { return }
        let location = gesture.location(in: self)
        let scale = contentScale
        switch gesture.state {
        case .began:
            // Pick the cell under the finger; seed word selection.
            guard let pos = renderer.hitTest(x: location.x * scale, y: location.y * scale) else { return }
            selectionAnchorPos = pos
            selectionDragInProgress = true
            let initial = renderer.wordRange(at: pos)
                ?? TerminalCellRange(start: pos, end: pos, rectangle: false)
            renderer.selectionSet(initial)
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        case .changed:
            guard let anchor = selectionAnchorPos,
                  let focus = renderer.hitTest(x: location.x * scale, y: location.y * scale) else {
                return
            }
            renderer.selectionSet(
                TerminalCellRange(start: anchor, end: focus, rectangle: false)
            )
        case .ended, .cancelled, .failed:
            selectionDragInProgress = false
            // Present the edit menu over the final selection rect on a
            // run-loop tick so the long-press recognizer fully transitions
            // and we don't fight UIKit for the responder.
            DispatchQueue.main.async { [weak self] in
                self?.presentEditMenu()
            }
        default:
            break
        }
    }

    /// Handle a drag of an existing selection handle. Rotates the anchor
    /// to the *opposite* end so the handle the user is pulling stays
    /// glued to their finger.
    func handleSelectionHandleDrag(
        handle: TerminalSelectionOverlayView.Handle,
        location: CGPoint,
        state: UIGestureRecognizer.State
    ) {
        guard let renderer, let range = renderer.currentSelectionRange() else { return }
        let scale = contentScale
        let viewPx = CGPoint(x: location.x * scale, y: location.y * scale)
        guard let focus = renderer.hitTest(x: viewPx.x, y: viewPx.y) else { return }
        switch state {
        case .began:
            selectionDragInProgress = true
            selectionAnchorPos = handle == .start ? range.end : range.start
        case .changed:
            guard let anchor = selectionAnchorPos else { return }
            renderer.selectionSet(
                TerminalCellRange(start: anchor, end: focus, rectangle: false)
            )
        case .ended, .cancelled, .failed:
            selectionDragInProgress = false
            DispatchQueue.main.async { [weak self] in
                self?.presentEditMenu()
            }
        default:
            break
        }
    }

    @objc private func handlePinch(_ gesture: UIPinchGestureRecognizer) {
        switch gesture.state {
        case .began:
            pinchStartFontSize = currentFontSize
        case .changed:
            // Smooth: scale font size by the current pinch scale,
            // clamped to a humane range. We don't push it back to the
            // SwiftUI state until the gesture settles to avoid storming
            // applyConfig on every callback.
            let scaled = pinchStartFontSize * Double(gesture.scale)
            let clamped = min(max(scaled, 10.0), 24.0)
            // Live preview: bump local font size on the receiver so the
            // next layoutSubviews picks up the new grid; we still wait to
            // commit to AppStorage until .ended.
            currentFontSize = clamped
        case .ended:
            let final = currentFontSize
            onFontSizePinched?(final)
            gesture.scale = 1.0
        case .cancelled, .failed:
            currentFontSize = pinchStartFontSize
        default:
            break
        }
    }

    // MARK: - UIGestureRecognizerDelegate

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer
    ) -> Bool {
        // Long-press takes priority over scroll/drag pans; tap and pinch
        // coexist with everything else so the user can pinch while
        // long-pressing or tap between drags.
        if gestureRecognizer === longPress || other === longPress {
            return true
        }
        return true
    }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRequireFailureOf other: UIGestureRecognizer
    ) -> Bool {
        // Scroll pan must wait for long-press to fail; once long-press
        // starts, we want the user's drag to extend the selection rather
        // than scroll the viewport.
        if gestureRecognizer === scrollPan && other === longPress {
            return true
        }
        return false
    }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldReceive touch: UITouch
    ) -> Bool {
        if touch.view is UIControl { return false }
        return true
    }
}
