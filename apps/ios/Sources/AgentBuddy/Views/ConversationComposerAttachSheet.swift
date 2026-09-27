import SwiftUI

/// Attachment source chooser (photo library, file, camera).
struct ConversationComposerAttachSheet: View {
    let onPickPhotoLibrary: () -> Void
    let onChooseFile: (() -> Void)?
    let onTakePhoto: (() -> Void)?

    var body: some View {
        VStack(alignment: .leading, spacing: BuddySpacing.sm) {
            Text("Attach")
                .buddyText(.title)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
                .padding(.bottom, BuddySpacing.xxs)

            VStack(spacing: 0) {
                sheetButton("Photo Library", systemImage: "photo.on.rectangle", action: onPickPhotoLibrary)

                if let onChooseFile {
                    BuddyDivider()
                    sheetButton("Choose File", systemImage: "folder", action: onChooseFile)
                }

                if let onTakePhoto {
                    BuddyDivider()
                    sheetButton("Take Photo", systemImage: "camera", action: onTakePhoto)
                }
            }
            .buddyCard(.surface, radius: BuddyRadius.card, padding: nil)

            Spacer(minLength: 0)
        }
        .padding(.horizontal, BuddySpacing.xl)
        .padding(.top, BuddySpacing.xl)
        .padding(.bottom, BuddySpacing.lg)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .buddyPageBackground()
    }

    private func sheetButton(_ title: LocalizedStringKey, systemImage: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: BuddySpacing.sm) {
                Image(systemName: systemImage)
                    .font(.system(size: 20, weight: .medium))
                    .foregroundStyle(AgentBuddyTheme.link)
                    .frame(width: 28)
                    .accessibilityHidden(true)

                Text(title)
                    .buddyText(.body, weight: .medium)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)

                Spacer()

                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .accessibilityHidden(true)
            }
            .padding(.horizontal, BuddySpacing.md)
            .frame(minHeight: 56)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
