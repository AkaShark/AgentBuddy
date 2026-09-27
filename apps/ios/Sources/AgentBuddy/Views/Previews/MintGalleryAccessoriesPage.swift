import SwiftUI

#if DEBUG
/// Composer accessories (goal, plan, queued messages, input prompts, the
/// suggestion popup and usage badges) rendered from fixtures.
struct MintGalleryAccessoriesPage: View {
    /// `false` shows goal/plan/queue; `true` shows prompts, popup and badges.
    var showsPrompts = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: BuddySpacing.md) {
                Text(verbatim: showsPrompts ? "Composer prompts" : "Composer accessories")
                    .buddyText(.title)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)

                if showsPrompts {
                    prompts
                } else {
                    accessories
                }
            }
            .padding(.horizontal, BuddySpacing.md)
            .padding(.vertical, BuddySpacing.xl)
            .padding(.top, 48)
        }
    }

    @ViewBuilder
    private var accessories: some View {
        ConversationComposerGoalRowView(goal: Self.goal, actions: .noop)
        ConversationComposerPlanProgressView(progress: Self.plan)
        ConversationComposerActiveTaskRowView(
            summary: ConversationActiveTaskSummary(
                progressLabel: "2/5",
                title: "Fix the login flow",
                detail: "Checking the session refresh path"
            )
        )
        QueuedFollowUpsPreviewView(previews: Self.queued, onSteer: { _ in }, onDelete: { _ in })
    }

    @ViewBuilder
    private var prompts: some View {
        PendingUserInputPromptView(request: Self.inputRequest, onSubmit: { _ in }, onDismiss: {})
        PlanImplementationPromptView(onImplement: {}, onDismiss: {})
        ConversationComposerPopupOverlayView(
            state: .slash([.plan, .model, .goal]),
            onApplySlashSuggestion: { _ in },
            onApplyFileSuggestion: { _ in },
            onApplySkillSuggestion: { _ in },
            onApplyPluginSuggestion: { _ in }
        )
        .padding(.horizontal, -BuddySpacing.sm)
        .padding(.bottom, -56)
        ConversationComposerContextBarView(rateLimits: Self.rateLimits, contextPercent: 28)
    }

    private static let goal = AppThreadGoal(
        threadId: "t",
        objective: "Ship the login flow fix with tests on both platforms",
        status: .active,
        tokenBudget: 200_000,
        tokensUsed: 174_000,
        timeUsedSeconds: 1_520,
        createdAt: 0,
        updatedAt: 0
    )

    private static let plan = AppPlanProgressSnapshot(
        turnId: "turn",
        explanation: "Reproduce first, then fix the refresh path.",
        plan: [
            AppPlanStep(step: "Reproduce the logout loop", status: .completed),
            AppPlanStep(step: "Fix the session refresh path", status: .inProgress),
            AppPlanStep(step: "Add a regression test", status: .pending)
        ]
    )

    private static let queued = [
        AppQueuedFollowUpPreview(id: "q1", kind: .message, text: "Also check the logout path on iPad"),
        AppQueuedFollowUpPreview(id: "q2", kind: .pendingSteer, text: "Use the existing token store"),
        AppQueuedFollowUpPreview(id: "q3", kind: .retryingSteer, text: "Keep the old API for now")
    ]

    private static let inputRequest = PendingUserInputRequest(
        id: "input",
        serverId: "s",
        threadId: "t",
        turnId: "turn",
        itemId: "item",
        questions: [
            PendingUserInputQuestion(
                id: "q",
                header: "Scope",
                question: "Should the fix also cover the watch app?",
                isOtherAllowed: true,
                isSecret: false,
                options: [
                    PendingUserInputOption(label: "Phone only", description: nil),
                    PendingUserInputOption(label: "Phone and watch", description: nil)
                ]
            )
        ],
        requesterAgentNickname: nil,
        requesterAgentRole: nil
    )

    private static let rateLimits = RateLimitSnapshot(
        limitId: nil,
        limitName: nil,
        primary: RateLimitWindow(usedPercent: 35, windowDurationMins: 300, resetsAt: nil),
        secondary: RateLimitWindow(usedPercent: 82, windowDurationMins: 10_080, resetsAt: nil),
        credits: nil,
        planType: nil
    )
}
#endif
