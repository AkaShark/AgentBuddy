import SwiftUI

struct PendingUserInputPromptView: View {
    let request: PendingUserInputRequest
    let onSubmit: ([String: [String]]) -> Void
    let onDismiss: () -> Void

    @State private var selectedAnswers: [String: String] = [:]
    @State private var otherAnswers: [String: String] = [:]

    private var promptTitle: String {
        let firstQuestion = request.questions.first?.question.lowercased() ?? ""
        if firstQuestion.contains("implement") && firstQuestion.contains("plan") {
            return "Implement Plan"
        }
        return "Input Required"
    }

    private var requesterLabel: String? {
        AgentLabelFormatter.format(
            nickname: request.requesterAgentNickname,
            role: request.requesterAgentRole
        )
    }

    private var unsupportedQuestions: [PendingUserInputQuestion] {
        request.questions.filter { question in
            question.isSecret || (!question.isOtherAllowed && question.options.isEmpty)
        }
    }

    private var canSubmit: Bool {
        unsupportedQuestions.isEmpty &&
        request.questions.allSatisfy { !resolvedAnswer(for: $0).isEmpty }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: "questionmark.bubble.fill")
                    .foregroundColor(AgentBuddyTheme.warning)
                Text(promptTitle)
                    .agentBuddyFont(.caption, weight: .semibold)
                    .foregroundColor(AgentBuddyTheme.textPrimary)
                Spacer()
                Button(action: onDismiss) {
                    Image(systemName: "xmark.circle.fill")
                        .agentBuddyFont(.body)
                        .foregroundColor(AgentBuddyTheme.textMuted)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Dismiss input request")
            }

            if let requesterLabel {
                Text(requesterLabel)
                    .agentBuddyFont(.caption2)
                    .foregroundColor(AgentBuddyTheme.textMuted)
            }

            ForEach(request.questions, id: \.id) { question in
                VStack(alignment: .leading, spacing: 6) {
                    if let header = question.header, !header.isEmpty {
                        Text(header.uppercased())
                            .agentBuddyFont(.caption2, weight: .bold)
                            .foregroundColor(AgentBuddyTheme.textMuted)
                    }

                    Text(question.question)
                        .agentBuddyFont(.caption)
                        .foregroundColor(AgentBuddyTheme.textPrimary)

                    if question.isSecret || (!question.isOtherAllowed && question.options.isEmpty) {
                        Text("This prompt type is not fully supported in the current iOS client.")
                            .agentBuddyFont(.caption2)
                            .foregroundColor(AgentBuddyTheme.textSecondary)
                    } else {
                        VStack(alignment: .leading, spacing: 8) {
                            if !question.options.isEmpty {
                                // ViewThatFits + VStack fallback so long
                                // option labels wrap to a new row instead
                                // of squeezing a short option into a narrow
                                // column with character-by-character wrapping.
                                let optionButtons = ForEach(question.options, id: \.label) { option in
                                    let isSelected =
                                        selectedAnswers[question.id] == option.label &&
                                        trimmedOtherAnswer(for: question).isEmpty
                                    Button {
                                        selectedAnswers[question.id] = option.label
                                        otherAnswers[question.id] = ""
                                    } label: {
                                        Text(option.label)
                                            .agentBuddyFont(.caption2, weight: .semibold)
                                            .foregroundColor(isSelected ? Color.black : AgentBuddyTheme.textPrimary)
                                            .padding(.horizontal, 10)
                                            .padding(.vertical, 6)
                                            .background(isSelected ? AgentBuddyTheme.accent : AgentBuddyTheme.surface.opacity(0.8))
                                            .clipShape(Capsule())
                                    }
                                    .buttonStyle(.plain)
                                }
                                ViewThatFits(in: .horizontal) {
                                    HStack(spacing: 8) { optionButtons }
                                    VStack(alignment: .leading, spacing: 8) { optionButtons }
                                }
                            }

                            if question.isOtherAllowed {
                                TextField(
                                    question.options.isEmpty ? "Enter response" : "Other response",
                                    text: otherAnswerBinding(for: question)
                                )
                                .agentBuddyFont(.caption2)
                                .foregroundColor(AgentBuddyTheme.textPrimary)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 8)
                                .background(AgentBuddyTheme.surface.opacity(0.8))
                                .clipShape(RoundedRectangle(cornerRadius: 8))
                            }
                        }
                    }
                }
            }

            if canSubmit {
                Button("Submit") {
                    let answers = request.questions.reduce(into: [String: [String]]()) { result, question in
                        let answer = resolvedAnswer(for: question)
                        guard !answer.isEmpty else { return }
                        result[question.id] = [answer]
                    }
                    onSubmit(answers)
                }
                .agentBuddyFont(.caption, weight: .semibold)
                .foregroundColor(Color.black)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(AgentBuddyTheme.accent)
                .clipShape(Capsule())
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .modifier(GlassRectModifier(cornerRadius: 14))
    }

    private func otherAnswerBinding(for question: PendingUserInputQuestion) -> Binding<String> {
        Binding(
            get: { otherAnswers[question.id, default: ""] },
            set: { newValue in
                otherAnswers[question.id] = newValue
            }
        )
    }

    private func trimmedOtherAnswer(for question: PendingUserInputQuestion) -> String {
        otherAnswers[question.id, default: ""]
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func resolvedAnswer(for question: PendingUserInputQuestion) -> String {
        let other = trimmedOtherAnswer(for: question)
        if !other.isEmpty {
            return other
        }
        return selectedAnswers[question.id, default: ""]
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
