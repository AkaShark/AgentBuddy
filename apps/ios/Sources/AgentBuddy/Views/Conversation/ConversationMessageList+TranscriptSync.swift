import SwiftUI

extension ConversationMessageList {
    func syncTranscriptTurns(resetExpansion: Bool = false) {
        let nextBuildKey = makeTranscriptBuildKey()
        if transcriptBuildKey == nextBuildKey, !transcriptTurns.isEmpty {
            if resetExpansion { expandedTurnIDs.removeAll() }
            return
        }

        let nextTurns = TranscriptTurn.build(
            from: items,
            threadStatus: threadStatus,
            expandedRecentTurnCount: expandedRecentTurnCount
        )
        transcriptBuildKey = nextBuildKey
        if shouldAnimateNewTurnInsertion(from: transcriptTurns, to: nextTurns, resetExpansion: resetExpansion) {
            pendingAnimatedTurns = nextTurns
            guard !turnInsertionAnimationInFlight else { return }
            startNewTurnInsertionAnimation(from: transcriptTurns)
            return
        }

        if turnInsertionAnimationInFlight {
            pendingAnimatedTurns = nextTurns
            return
        }

        let lastTurnItemCountGrew = {
            guard let currentLast = transcriptTurns.last,
                  let nextLast = nextTurns.last,
                  currentLast.id == nextLast.id,
                  nextLast.items.count > currentLast.items.count else {
                return false
            }
            return true
        }()

        if lastTurnItemCountGrew {
            withAnimation(.spring(duration: 0.4, bounce: 0.08)) {
                applyTranscriptTurns(nextTurns, resetExpansion: resetExpansion)
            }
        } else {
            applyTranscriptTurns(nextTurns, resetExpansion: resetExpansion)
        }
    }

    private func makeTranscriptBuildKey() -> Int {
        var hasher = Hasher()
        hasher.combine(expandedRecentTurnCount)
        hasher.combine(transcriptRenderDigest)
        return hasher.finalize()
    }

    func makeRenderedTurnsBuildKey(for turns: [TranscriptTurn]) -> Int {
        var hasher = Hasher()
        hasher.combine(turns.count)
        for turn in turns {
            hasher.combine(turn.id)
            hasher.combine(turn.renderDigest)
            hasher.combine(turn.isLive)
            hasher.combine(turn.isCollapsedByDefault)
        }
        return hasher.finalize()
    }

    private func layoutSignature(for turn: TranscriptTurn) -> Int {
        var hasher = Hasher()
        hasher.combine(turn.id)
        hasher.combine(turn.renderDigest)
        hasher.combine(turn.isLive)
        hasher.combine(turn.isCollapsedByDefault)
        return hasher.finalize()
    }

    private func shouldAnimateNewTurnInsertion(
        from currentTurns: [TranscriptTurn],
        to nextTurns: [TranscriptTurn],
        resetExpansion: Bool
    ) -> Bool {
        guard collapseTurns,
              !resetExpansion,
              !currentTurns.isEmpty,
              nextTurns.count == currentTurns.count + 1,
              currentTurns.last?.id != nextTurns.last?.id,
              let lastTurn = nextTurns.last,
              lastTurn.items.first?.isUserItem == true,
              lastTurn.items.first?.isFromUserTurnBoundary == true else {
            return false
        }

        for (currentTurn, nextTurn) in zip(currentTurns, nextTurns) {
            guard currentTurn.id == nextTurn.id else { return false }
        }

        return true
    }

    private func startNewTurnInsertionAnimation(from currentTurns: [TranscriptTurn]) {
        guard let previousLastTurnID = currentTurns.last?.id else {
            if let pendingAnimatedTurns {
                applyTranscriptTurns(pendingAnimatedTurns)
                self.pendingAnimatedTurns = nil
            }
            return
        }

        turnInsertionAnimationInFlight = true
        let collapsedTurns = currentTurns.map { turn in
            turn.id == previousLastTurnID ? turn.withCollapsedByDefault(true) : turn
        }

        withAnimation(.snappy(duration: 0.16, extraBounce: 0)) {
            applyTranscriptTurns(
                collapsedTurns,
                removeExpandedTurnID: previousLastTurnID
            )
        } completion: {
            let turnsToInsert = pendingAnimatedTurns ?? collapsedTurns
            withAnimation(.smooth(duration: 0.2)) {
                applyTranscriptTurns(turnsToInsert)
            } completion: {
                turnInsertionAnimationInFlight = false
                let latestTurns = pendingAnimatedTurns ?? turnsToInsert
                pendingAnimatedTurns = nil
                if latestTurns.map(layoutSignature(for:)) != transcriptTurns.map(layoutSignature(for:)) {
                    applyTranscriptTurns(latestTurns)
                }
            }
        }
    }

    private func applyTranscriptTurns(
        _ nextTurns: [TranscriptTurn],
        resetExpansion: Bool = false,
        removeExpandedTurnID: String? = nil
    ) {
        let nextTurnIDs = Set(nextTurns.map(\.id))
        let nextRenderedTurns = TranscriptTurn.mergeConsecutiveExplorationTurnsForRendering(nextTurns)
        transcriptTurns = nextTurns
        renderedTurns = nextRenderedTurns
        renderedTurnsBuildKey = makeRenderedTurnsBuildKey(for: nextTurns)
        if resetExpansion {
            expandedTurnIDs.removeAll()
        } else {
            expandedTurnIDs.formIntersection(nextTurnIDs)
        }
        if let removeExpandedTurnID {
            expandedTurnIDs.remove(removeExpandedTurnID)
        }
    }
}
