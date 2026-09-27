import SwiftUI

extension ConversationMessageList {
    func isTurnExpanded(_ turn: TranscriptTurn) -> Bool {
        !turn.isCollapsedByDefault || expandedTurnIDs.contains(turn.id)
    }

    func toggleTurnExpansion(_ turn: TranscriptTurn) {
        guard turn.isCollapsedByDefault else { return }
        withAnimation(.spring(response: 0.28, dampingFraction: 0.88)) {
            if expandedTurnIDs.contains(turn.id) {
                expandedTurnIDs.remove(turn.id)
            } else {
                expandedTurnIDs.insert(turn.id)
            }
        }
    }

    func requestFollowScrollAfterLayout(_ proxy: ScrollViewProxy) {
        guard !followLayoutScrollScheduled else { return }
        followLayoutScrollScheduled = true
        DispatchQueue.main.async {
            followLayoutScrollScheduled = false
            guard isStreaming, autoFollowStreaming, !userIsDraggingScroll else { return }
            scrollToBottom(proxy)
        }
    }

    func requestInitialBottomScrollIfNeeded(_ proxy: ScrollViewProxy) {
        guard !items.isEmpty else { return }
        let threadScopeID = activeThreadScopeID
        guard initialBottomScrollThreadScopeID != threadScopeID else { return }
        initialBottomScrollThreadScopeID = threadScopeID
        DispatchQueue.main.async {
            guard activeThreadScopeID == threadScopeID else { return }
            scrollToBottom(proxy)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
                guard activeThreadScopeID == threadScopeID else { return }
                scrollToBottom(proxy)
            }
        }
    }

    func updateDistanceFromBottom(_ distance: CGFloat) {
        let clampedDistance = max(0, distance)
        if programmaticBottomScrollSettling {
            if clampedDistance <= Self.nearBottomRestoreDistance {
                programmaticBottomScrollSettling = false
            } else {
                return
            }
        }

        distanceFromBottom = clampedDistance
        let nextIsNearBottom = clampedDistance <= Self.nearBottomRestoreDistance
        if nextIsNearBottom != isNearBottom { isNearBottom = nextIsNearBottom }
        if nextIsNearBottom {
            autoFollowStreaming = true
        } else if isStreaming && userIsDraggingScroll {
            autoFollowStreaming = false
        }
    }

    func scrollToBottom(_ proxy: ScrollViewProxy) {
        isNearBottom = true
        distanceFromBottom = 0
        programmaticBottomScrollGeneration &+= 1
        let generation = programmaticBottomScrollGeneration
        programmaticBottomScrollSettling = true
        proxy.scrollTo(Self.bottomAnchorID, anchor: .bottom)
        DispatchQueue.main.asyncAfter(deadline: .now() + Self.bottomScrollSettleDuration) {
            guard programmaticBottomScrollGeneration == generation else { return }
            programmaticBottomScrollSettling = false
        }
    }

    func handlePinchChanged(scale: CGFloat) {
        if pinchBaseStep == nil {
            pinchBaseStep = textSizeStep
            pinchAppliedDelta = 0
        }

        let candidateDelta: Int
        if scale >= 1.18 { candidateDelta = 2 }
        else if scale >= 1.03 { candidateDelta = 1 }
        else if scale <= 0.86 { candidateDelta = -2 }
        else if scale <= 0.97 { candidateDelta = -1 }
        else { candidateDelta = 0 }
        guard candidateDelta != 0 else { return }

        if pinchAppliedDelta == 0 {
            pinchAppliedDelta = candidateDelta
            return
        }

        let sameDirection = (pinchAppliedDelta > 0 && candidateDelta > 0) || (pinchAppliedDelta < 0 && candidateDelta < 0)
        if sameDirection {
            if abs(candidateDelta) > abs(pinchAppliedDelta) {
                pinchAppliedDelta = candidateDelta
            }
        } else {
            pinchAppliedDelta = candidateDelta
        }
    }

    func finishPinch(scale: CGFloat) {
        handlePinchChanged(scale: scale)
        let baseline = pinchBaseStep ?? textSizeStep
        let next = ConversationTextSize.clamped(rawValue: baseline + pinchAppliedDelta).rawValue
        if next != textSizeStep {
            withAnimation(.spring(response: 0.22, dampingFraction: 0.9)) {
                textSizeStep = next
            }
        }
        pinchBaseStep = nil
        pinchAppliedDelta = 0
    }
}
