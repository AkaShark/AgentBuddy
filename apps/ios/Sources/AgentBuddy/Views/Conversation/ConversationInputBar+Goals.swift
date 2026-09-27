import SwiftUI

extension ConversationInputBar {
    func handleGoalCommand(_ args: String?) async {
        let raw = args?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let lower = raw.lowercased()
        do {
            switch lower {
            case "":
                let goal = try await appModel.client.getThreadGoal(
                    serverId: snapshot.threadKey.serverId,
                    params: AppThreadGoalGetRequest(threadId: snapshot.threadKey.threadId)
                )
                guard let goal else {
                    slashErrorMessage = "No goal is set for this thread."
                    return
                }
                slashErrorMessage = goalSummary(goal)
            case "pause":
                _ = try await appModel.client.setThreadGoal(
                    serverId: snapshot.threadKey.serverId,
                    params: AppThreadGoalSetRequest(
                        threadId: snapshot.threadKey.threadId,
                        objective: nil,
                        status: .paused,
                        tokenBudget: nil
                    )
                )
            case "resume":
                _ = try await appModel.client.setThreadGoal(
                    serverId: snapshot.threadKey.serverId,
                    params: AppThreadGoalSetRequest(
                        threadId: snapshot.threadKey.threadId,
                        objective: nil,
                        status: .active,
                        tokenBudget: nil
                    )
                )
            case "clear":
                _ = try await appModel.client.clearThreadGoal(
                    serverId: snapshot.threadKey.serverId,
                    params: AppThreadGoalClearRequest(threadId: snapshot.threadKey.threadId)
                )
            default:
                _ = try await appModel.client.setThreadGoal(
                    serverId: snapshot.threadKey.serverId,
                    params: AppThreadGoalSetRequest(
                        threadId: snapshot.threadKey.threadId,
                        objective: raw,
                        status: .active,
                        tokenBudget: nil
                    )
                )
            }
        } catch {
            slashErrorMessage = error.localizedDescription
        }
    }

    private func goalSummary(_ goal: AppThreadGoal) -> String {
        var lines = [
            "Goal: \(goal.objective)",
            "Status: \(goalStatusLabel(goal.status))",
            "Tokens used: \(goal.tokensUsed)"
        ]
        if let tokenBudget = goal.tokenBudget {
            lines.append("Token budget: \(tokenBudget)")
        }
        return lines.joined(separator: "\n")
    }

    private func goalStatusLabel(_ status: AppThreadGoalStatus) -> String {
        switch status {
        case .active: return "active"
        case .paused: return "paused"
        case .blocked: return "blocked"
        case .usageLimited: return "limited by usage"
        case .budgetLimited: return "limited by budget"
        case .complete: return "complete"
        }
    }

    func makeGoalCardActions() -> GoalCardActions {
        GoalCardActions(
            togglePause: {
                guard let current = snapshot.goal?.status else { return }
                let next: AppThreadGoalStatus
                switch current {
                case .active: next = .paused
                case .paused, .blocked, .usageLimited, .budgetLimited: next = .active
                case .complete: return
                }
                Task { await applyGoalUpdate(status: next) }
            },
            markComplete: {
                Task { await applyGoalUpdate(status: .complete) }
            },
            setObjective: { objective in
                Task { await applyGoalUpdate(objective: objective) }
            },
            setBudget: { value in
                let goal = snapshot.goal
                let resumeFromLimit = goal?.status == .budgetLimited
                    && (value ?? 0) > (goal?.tokensUsed ?? 0)
                Task {
                    await applyGoalUpdate(
                        status: resumeFromLimit ? .active : nil,
                        tokenBudget: value
                    )
                }
            },
            clear: {
                Task { await clearGoal() }
            }
        )
    }

    private func applyGoalUpdate(
        objective: String? = nil,
        status: AppThreadGoalStatus? = nil,
        tokenBudget: Int64? = nil
    ) async {
        do {
            _ = try await appModel.client.setThreadGoal(
                serverId: snapshot.threadKey.serverId,
                params: AppThreadGoalSetRequest(
                    threadId: snapshot.threadKey.threadId,
                    objective: objective,
                    status: status,
                    tokenBudget: tokenBudget
                )
            )
        } catch {
            slashErrorMessage = error.localizedDescription
        }
    }

    private func clearGoal() async {
        do {
            _ = try await appModel.client.clearThreadGoal(
                serverId: snapshot.threadKey.serverId,
                params: AppThreadGoalClearRequest(threadId: snapshot.threadKey.threadId)
            )
        } catch {
            slashErrorMessage = error.localizedDescription
        }
    }
}
