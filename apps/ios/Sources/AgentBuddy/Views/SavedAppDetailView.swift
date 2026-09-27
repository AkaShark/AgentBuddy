import SwiftUI

/// Fullscreen host for a single saved app. Loads the widget HTML + persisted
/// state from Rust on appear, renders through `WidgetWebView` in app-mode so
/// the `loadAppState` / `saveAppState` JS bridge is wired. State saves flow
/// through the onMessage handler into `SavedAppsStore.saveState`, which
/// debounces the write.
struct SavedAppDetailView: View {
    let appId: String

    @Environment(\.dismiss) private var dismiss
    @State private var store = SavedAppsStore.shared
    @State private var payload: SavedAppWithPayload?
    @State private var loadAttempted = false
    @State private var renameText: String = ""
    @State private var showRenameSheet = false
    @State private var showUpdateOverlay = false
    @State private var isUpdating = false
    @State private var updateError: String?
    @State private var updateSuccessMessage: String?
    @State private var reloadTick = 0
    @State private var pollingTask: Task<Void, Never>?
    @State private var showDeleteConfirm = false
    /// In-memory ephemeral thread id for this saved-app view. Rust owns
    /// the thread; we just cache the id so consecutive `structuredResponse`
    /// calls in this view reuse the same hidden thread. Dies with the
    /// view — that's the whole point (Option B in the plan).
    @State private var cachedStructuredThreadId: String?

    var body: some View {
        ZStack {
            AgentBuddyTheme.background.ignoresSafeArea()
            if let payload {
                ZStack {
                    WidgetWebView(
                        widgetHTML: payload.widgetHtml,
                        isFinalized: true,
                        allowsScrollAndZoom: true,
                        onMessage: handleMessage,
                        onStructuredRequest: handleStructuredRequest,
                        appMode: true,
                        initialAppState: payload.stateJson,
                        schemaVersion: Int(payload.app.schemaVersion)
                    )
                    .id("\(appId)-\(reloadTick)")
                    // Keep the initial content below the floating header
                    // buttons so they don't overlap on load. Users can
                    // still scroll content up under them — only the
                    // initial anchor respects the bar.
                    .safeAreaPadding(.top, 54)
                    .ignoresSafeArea(edges: [.bottom, .horizontal])

                    if isUpdating {
                        shimmerOverlay
                    }
                }

                topBar(for: payload.app)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            } else if loadAttempted {
                brokenAppPlaceholder
            } else {
                ProgressView()
                    .tint(AgentBuddyTheme.textSecondary)
            }

            if showUpdateOverlay {
                SavedAppUpdateOverlay(
                    isUpdating: $isUpdating,
                    errorMessage: $updateError,
                    onSubmit: { prompt in
                        Task { await runUpdate(prompt: prompt) }
                    },
                    onDismiss: {
                        showUpdateOverlay = false
                        updateError = nil
                    }
                )
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }

            if let message = updateSuccessMessage {
                VStack {
                    Spacer()
                    Label {
                        Text(message)
                    } icon: {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundStyle(AgentBuddyTheme.success)
                            .accessibilityHidden(true)
                    }
                    .buddyText(.label, weight: .semibold)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                    .padding(.horizontal, BuddySpacing.md)
                    .frame(minHeight: BuddySize.compactPill + 6)
                    .background(AgentBuddyTheme.surface, in: Capsule())
                    .overlay { Capsule().strokeBorder(AgentBuddyTheme.border, lineWidth: 1) }
                    .shadow(color: AgentBuddyTheme.floatingShadow, radius: 12, y: 8)
                    .padding(.bottom, BuddySpacing.xxl)
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .navigationBarBackButtonHidden(true)
        .onAppear(perform: reloadPayload)
        .onDisappear {
            pollingTask?.cancel()
            pollingTask = nil
        }
        .onChange(of: isUpdating) { _, updating in
            if updating {
                startPollingForUpdate()
            } else {
                pollingTask?.cancel()
                pollingTask = nil
            }
        }
        .sheet(isPresented: $showRenameSheet) {
            SavedAppRenameSheet(
                text: $renameText,
                onCancel: { showRenameSheet = false },
                onSave: {
                    let trimmed = renameText.trimmingCharacters(in: .whitespacesAndNewlines)
                    guard !trimmed.isEmpty else { showRenameSheet = false; return }
                    _ = try? store.rename(id: appId, title: trimmed)
                    showRenameSheet = false
                    reloadPayload()
                }
            )
            .presentationDetents([.medium])
            .buddySheetStyle()
        }
        .alert(
            "Delete \"\(payload?.app.title ?? "")\"?",
            isPresented: $showDeleteConfirm
        ) {
            Button("Cancel", role: .cancel) {}
            Button("Delete", role: .destructive) {
                try? store.delete(id: appId)
                dismiss()
            }
        } message: {
            Text("This removes the app, its saved HTML, and its persisted state.")
        }
    }

    /// While a saved-app update is in flight, poll the on-disk HTML
    /// every 500ms. When the model-driven `apply_patch` lands a new
    /// version, reassign `payload` so `WidgetWebView` picks up the
    /// change through its existing morphdom debouncer. The final
    /// reassign after the RPC completes is handled by `reloadPayload`
    /// in `runUpdate`.
    private func startPollingForUpdate() {
        pollingTask?.cancel()
        let id = appId
        pollingTask = Task { @MainActor in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 500_000_000)
                if Task.isCancelled { break }
                guard let fresh = store.getWithPayload(id: id) else { continue }
                if fresh.widgetHtml != payload?.widgetHtml {
                    payload = fresh
                }
            }
        }
    }

    /// Floating header over the widget: surface circles and a title capsule,
    /// with Update as the one emphasised action (brand fill).
    private func topBar(for app: SavedApp) -> some View {
        HStack(spacing: BuddySpacing.xs) {
            BuddyIconButton(
                systemImage: "chevron.left",
                accessibilityLabel: "Back",
                tone: .surface,
                diameter: 38,
                iconSize: 17
            ) {
                dismiss()
            }
            .shadow(color: AgentBuddyTheme.floatingShadow, radius: 12, y: 8)

            Button {
                renameText = app.title
                showRenameSheet = true
            } label: {
                Text(verbatim: app.title)
                    .buddyText(.label, weight: .semibold)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                    .lineLimit(1)
                    .padding(.horizontal, BuddySpacing.md)
                    .frame(minHeight: 38)
                    .background(AgentBuddyTheme.surface, in: Capsule())
                    .overlay { Capsule().strokeBorder(AgentBuddyTheme.border, lineWidth: 1) }
                    .shadow(color: AgentBuddyTheme.floatingShadow, radius: 12, y: 8)
                    .frame(minHeight: BuddySize.minHitTarget)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityHint(Text("Rename"))

            Spacer(minLength: 0)

            Menu {
                Button {
                    renameText = app.title
                    showRenameSheet = true
                } label: {
                    Label("Rename", systemImage: "pencil")
                }
                Button(role: .destructive) {
                    showDeleteConfirm = true
                } label: {
                    Label("Delete", systemImage: "trash")
                }
            } label: {
                Image(systemName: "ellipsis")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                    .frame(width: 38, height: 38)
                    .background(AgentBuddyTheme.surface, in: Circle())
                    .overlay { Circle().strokeBorder(AgentBuddyTheme.border, lineWidth: 1) }
                    .shadow(color: AgentBuddyTheme.floatingShadow, radius: 12, y: 8)
                    .frame(width: BuddySize.minHitTarget, height: BuddySize.minHitTarget)
                    .contentShape(Rectangle())
            }
            .accessibilityLabel("App options")

            if let threadId = app.originThreadId, threadExists(threadId) {
                BuddyIconButton(
                    systemImage: "text.bubble",
                    accessibilityLabel: "View Conversation",
                    tone: .surface,
                    diameter: 38,
                    iconSize: 17
                ) {
                    SavedAppsNavigation.shared.requestConversation(threadId: threadId)
                    dismiss()
                }
                .shadow(color: AgentBuddyTheme.floatingShadow, radius: 12, y: 8)
            }

            Button {
                showUpdateOverlay = true
                updateError = nil
            } label: {
                HStack(spacing: BuddySpacing.xxs) {
                    Image(systemName: "arrow.triangle.2.circlepath")
                        .font(.system(size: 13, weight: .semibold))
                        .accessibilityHidden(true)
                    Text("Update")
                        .buddyText(.label, weight: .semibold)
                }
                .foregroundStyle(isUpdating ? AgentBuddyTheme.onDisabled : AgentBuddyTheme.onBrand)
                .padding(.horizontal, BuddySpacing.md)
                .frame(minHeight: 38)
                .background(isUpdating ? AgentBuddyTheme.disabled : AgentBuddyTheme.brand, in: Capsule())
                .shadow(color: AgentBuddyTheme.floatingShadow, radius: 12, y: 8)
                .frame(minHeight: BuddySize.minHitTarget)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(isUpdating)
        }
        .padding(.horizontal, BuddySpacing.sm)
        .padding(.top, BuddySpacing.xxs)
    }

    private func threadExists(_ threadId: String) -> Bool {
        guard let threads = AppModel.shared.snapshot?.threads else { return false }
        return threads.contains(where: { $0.key.threadId == threadId })
    }

    private var shimmerOverlay: some View {
        // Subtle veil + progress strip over the running widget while the
        // update is in flight. The widget itself stays interactive; the strip
        // is purely a visual hint that a regeneration is happening.
        ZStack {
            AgentBuddyTheme.background.opacity(0.35)
                .ignoresSafeArea()
            ShimmerStrip()
                .frame(maxWidth: .infinity)
                .frame(height: 2)
                .padding(.top, 0)
                .frame(maxHeight: .infinity, alignment: .top)
        }
        .allowsHitTesting(false)
    }

    private var brokenAppPlaceholder: some View {
        BuddyEmptyState(
            systemImage: "exclamationmark.triangle",
            title: "This app's files are missing",
            message: "Delete it to clear the entry.",
            actionTitle: "Delete App",
            actionSystemImage: "trash",
            actionKind: .destructive
        ) {
            try? store.delete(id: appId)
            dismiss()
        }
        .padding(.horizontal, BuddySpacing.xl)
    }

    // MARK: - Actions

    private func reloadPayload() {
        payload = store.getWithPayload(id: appId)
        loadAttempted = true
        reloadTick &+= 1
    }

    private func handleMessage(_ body: Any) {
        guard let dict = body as? [String: Any],
              let type = dict["_type"] as? String else { return }
        switch type {
        case "saveAppState":
            guard let value = dict["value"] as? String else { return }
            let schema = (dict["schema"] as? Int).map(UInt32.init) ?? (payload?.app.schemaVersion ?? 1)
            store.saveState(id: appId, stateJson: value, schemaVersion: schema)
        default:
            break
        }
    }

    private func handleStructuredRequest(
        requestId: String,
        prompt: String,
        responseFormatJSON: String,
        respond: @escaping (String, String?, String?) -> Void
    ) {
        guard let serverId = SavedAppDetailView.resolveActiveServerId() else {
            respond(requestId, nil, "No connected server available")
            return
        }
        let cached = cachedStructuredThreadId
        Task.detached {
            let result = await AppModel.shared.client.structuredResponse(
                serverId: serverId,
                cachedThreadId: cached,
                prompt: prompt,
                outputSchemaJson: responseFormatJSON
            )
            switch result {
            case .success(let threadId, let responseJson):
                await MainActor.run {
                    cachedStructuredThreadId = threadId
                }
                respond(requestId, responseJson, nil)
            case .error(let message):
                respond(requestId, nil, message)
            }
        }
    }

    private func runUpdate(prompt: String) async {
        guard let serverId = SavedAppDetailView.resolveActiveServerId() else {
            updateError = "No connected server available"
            return
        }
        isUpdating = true
        defer { isUpdating = false }
        do {
            _ = try await store.requestUpdate(id: appId, serverId: serverId, prompt: prompt)
            showUpdateOverlay = false
            updateError = nil
            reloadPayload()
            withAnimation { updateSuccessMessage = "Updated" }
            Task {
                try? await Task.sleep(nanoseconds: 2_500_000_000)
                withAnimation { updateSuccessMessage = nil }
            }
        } catch {
            updateError = error.localizedDescription
        }
    }

    @MainActor
    private static func resolveActiveServerId() -> String? {
        let snapshot = AppModel.shared.snapshot
        // Prefer the active thread's server; fall back to any known server
        // id. If nothing is connected, the update will fail loudly in the
        // overlay.
        if let serverId = snapshot?.activeThread?.serverId { return serverId }
        return snapshot?.servers.first?.serverId
    }
}

/// Thin animated shimmer strip used at the top of the widget during an
/// in-flight update. Repeats a gradient wipe indefinitely; the parent
/// decides when to show/hide it. With Reduce Motion it stays a static bar.
private struct ShimmerStrip: View {
    @State private var phase: CGFloat = -1
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        GeometryReader { geo in
            if reduceMotion {
                AgentBuddyTheme.link
            } else {
                LinearGradient(
                    colors: [
                        AgentBuddyTheme.link.opacity(0.0),
                        AgentBuddyTheme.link.opacity(0.9),
                        AgentBuddyTheme.link.opacity(0.0),
                    ],
                    startPoint: .leading,
                    endPoint: .trailing
                )
                .frame(width: geo.size.width)
                .offset(x: phase * geo.size.width)
                .onAppear {
                    withAnimation(
                        .linear(duration: 1.2).repeatForever(autoreverses: false)
                    ) {
                        phase = 1
                    }
                }
            }
        }
        .accessibilityHidden(true)
    }
}
