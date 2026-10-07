import SwiftUI

/// Actions available on a task. Shared by the context menu (long press) and the
/// visible "…" button, so long press is never the only way in.
struct TaskActionHandlers {
    var reply: (HomeTaskItem) -> Void
    var stop: (HomeTaskItem) -> Void
    var fork: (HomeTaskItem) -> Void
    var togglePin: (HomeTaskItem) -> Void
    var hide: (HomeTaskItem) -> Void
    var delete: (HomeTaskItem) -> Void
}

struct TaskActionsMenuContent: View {
    let item: HomeTaskItem
    let handlers: TaskActionHandlers

    var body: some View {
        Button {
            handlers.reply(item)
        } label: {
            Label("Reply", systemImage: "arrowshape.turn.up.left")
        }
        if item.session.hasTurnActive {
            Button {
                handlers.stop(item)
            } label: {
                Label("Stop task", systemImage: "stop.circle")
            }
            .disabled(item.state == .stopping)
        }
        Button {
            handlers.fork(item)
        } label: {
            Label("Fork", systemImage: "arrow.triangle.branch")
        }
        .disabled(item.session.hasTurnActive)
        Button {
            handlers.togglePin(item)
        } label: {
            if item.isPinned {
                Label("Unpin", systemImage: "pin.slash")
            } else {
                Label("Pin", systemImage: "pin")
            }
        }
        Button {
            handlers.hide(item)
        } label: {
            Label("Hide from Home", systemImage: "eye.slash")
        }
        Divider()
        Button(role: .destructive) {
            handlers.delete(item)
        } label: {
            Label("Delete", systemImage: "trash")
        }
    }
}

/// Visible "…" trigger with a 44pt hit target.
struct TaskActionsMenuButton: View {
    let item: HomeTaskItem
    let handlers: TaskActionHandlers
    var tint: Color = AgentBuddyTheme.textSecondary

    var body: some View {
        Menu {
            TaskActionsMenuContent(item: item, handlers: handlers)
        } label: {
            Image(systemName: "ellipsis")
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(tint)
                .frame(width: BuddySize.minHitTarget, height: BuddySize.minHitTarget)
                .contentShape(Rectangle())
        }
        .accessibilityLabel(Text("More actions"))
    }
}
