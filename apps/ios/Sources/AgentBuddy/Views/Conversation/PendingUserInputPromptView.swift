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
        VStack(alignment: .leading, spacing: BuddySpacing.sm) {
            header

            ForEach(request.questions, id: \.id) { question in
                questionBlock(question)
            }

            if canSubmit {
                BuddyButton("Submit", systemImage: "paperplane.fill", kind: .primary) {
                    let answers = request.questions.reduce(into: [String: [String]]()) { result, question in
                        let answer = resolvedAnswer(for: question)
                        guard !answer.isEmpty else { return }
                        result[question.id] = [answer]
                    }
                    onSubmit(answers)
                }
            }
        }
        .padding(.leading, BuddySpacing.md)
        .padding(.trailing, BuddySpacing.xxs)
        .padding(.bottom, BuddySpacing.md)
        .buddyCard(.surface, radius: BuddyRadius.confirmCard, padding: nil)
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: BuddySpacing.xs) {
                Image(systemName: "questionmark.bubble.fill")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(AgentBuddyTheme.warning)
                    .accessibilityHidden(true)
                Text(LocalizedStringKey(promptTitle))
                    .buddyText(.heading)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                Spacer(minLength: 0)
                BuddyIconButton(
                    systemImage: "xmark",
                    accessibilityLabel: "Dismiss input request",
                    tone: .plain,
                    iconSize: 15,
                    action: onDismiss
                )
            }

            if let requesterLabel {
                Text(verbatim: requesterLabel)
                    .buddyText(.caption)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .padding(.top, -BuddySpacing.xs)
            }
        }
    }

    private func questionBlock(_ question: PendingUserInputQuestion) -> some View {
        VStack(alignment: .leading, spacing: BuddySpacing.xs) {
            if let header = question.header, !header.isEmpty {
                Text(verbatim: header)
                    .buddyText(.caption, weight: .semibold)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
            }

            Text(verbatim: question.question)
                .buddyText(.body)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
                .fixedSize(horizontal: false, vertical: true)

            if question.isSecret || (!question.isOtherAllowed && question.options.isEmpty) {
                BuddyBanner(
                    tone: .warning,
                    message: Text("This prompt type is not fully supported in the current iOS client.")
                )
            } else {
                answerControls(for: question)
            }
        }
        .padding(.trailing, BuddySpacing.sm)
    }

    @ViewBuilder
    private func answerControls(for question: PendingUserInputQuestion) -> some View {
        if !question.options.isEmpty {
            // ViewThatFits + VStack fallback so long option labels wrap to a new
            // row instead of squeezing a short option into a narrow column.
            let optionButtons = ForEach(question.options, id: \.label) { option in
                optionButton(option, question: question)
            }
            ViewThatFits(in: .horizontal) {
                HStack(spacing: BuddySpacing.xs) { optionButtons }
                VStack(alignment: .leading, spacing: BuddySpacing.xxs) { optionButtons }
            }
        }

        if question.isOtherAllowed {
            TextField(
                question.options.isEmpty ? "Enter response" : "Other response",
                text: otherAnswerBinding(for: question),
                axis: .vertical
            )
            .buddyText(.body)
            .foregroundStyle(AgentBuddyTheme.textPrimary)
            .tint(AgentBuddyTheme.focus)
            .padding(.horizontal, BuddySpacing.sm)
            .padding(.vertical, BuddySpacing.sm)
            .frame(minHeight: BuddySize.control)
            .background(AgentBuddyTheme.background, in: RoundedRectangle(cornerRadius: BuddyRadius.control, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: BuddyRadius.control, style: .continuous)
                    .strokeBorder(AgentBuddyTheme.borderControl, lineWidth: 1)
            }
        }
    }

    private func optionButton(_ option: PendingUserInputOption, question: PendingUserInputQuestion) -> some View {
        let isSelected =
            selectedAnswers[question.id] == option.label &&
            trimmedOtherAnswer(for: question).isEmpty
        return Button {
            selectedAnswers[question.id] = option.label
            otherAnswers[question.id] = ""
        } label: {
            HStack(spacing: BuddySpacing.xxs) {
                if isSelected {
                    Image(systemName: "checkmark")
                        .font(.system(size: 12, weight: .bold))
                        .accessibilityHidden(true)
                }
                Text(verbatim: option.label)
                    .buddyText(.label)
                    .multilineTextAlignment(.leading)
            }
            .foregroundStyle(isSelected ? AgentBuddyTheme.onAction : AgentBuddyTheme.textPrimary)
            .padding(.horizontal, BuddySpacing.sm)
            .padding(.vertical, 7)
            .frame(minHeight: BuddySize.compactPill)
            .background(isSelected ? AgentBuddyTheme.action : AgentBuddyTheme.surfaceSoft, in: Capsule())
            .frame(minHeight: BuddySize.minHitTarget)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
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
