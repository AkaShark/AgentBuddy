import Foundation

#if DEBUG
import SwiftUI

enum AgentBuddyPreviewData {
    static let sampleCwd = "/Users/sigkitten/dev/codex-ios"

    static let sampleServer = DiscoveredServer(
        id: "preview-remote",
        name: "Newspaper Solver",
        hostname: "192.168.1.228",
        port: 8390,
        codexPorts: [8390, 9234],
        source: .manual,
        hasCodexServer: true,
        wakeMAC: "12:18:c7:14:74:e3",
        sshPortForwardingEnabled: true,
        preferredConnectionMode: .directCodex,
        preferredCodexPort: 8390
    )

    static let sampleSSHServer = DiscoveredServer(
        id: "preview-ssh",
        name: "Build Mac mini",
        hostname: "mac-mini.local",
        port: nil,
        sshPort: 22,
        source: .ssh,
        hasCodexServer: false,
        wakeMAC: "aa:bb:cc:dd:ee:ff",
        sshPortForwardingEnabled: true,
        preferredConnectionMode: .ssh
    )

    static let sampleBonjourServer = DiscoveredServer(
        id: "preview-bonjour",
        name: "Kitchen iMac",
        hostname: "imac.local",
        port: 8390,
        codexPorts: [8390],
        source: .bonjour,
        hasCodexServer: true
    )

    static let sampleModels: [ModelInfo] = [
        ModelInfo(
            id: "gpt-5.4",
            model: "gpt-5.4",
            upgrade: nil, upgradeModel: nil, upgradeCopy: nil, modelLink: nil, migrationMarkdown: nil, availabilityNuxMessage: nil,
            displayName: "gpt-5.4",
            description: "Balanced flagship model",
            hidden: false,
            supportedReasoningEfforts: [
                ReasoningEffortOption(reasoningEffort: .medium, description: "Balanced"),
                ReasoningEffortOption(reasoningEffort: .high, description: "Deeper reasoning"),
                ReasoningEffortOption(reasoningEffort: .xHigh, description: "Maximum reasoning")
            ],
            defaultReasoningEffort: .high,
            inputModalities: [.text, .image],
            supportsPersonality: true,
            isDefault: true,
            agentRuntimeKind: "codex"
        ),
        ModelInfo(
            id: "gpt-5.4-mini",
            model: "gpt-5.4-mini",
            upgrade: nil, upgradeModel: nil, upgradeCopy: nil, modelLink: nil, migrationMarkdown: nil, availabilityNuxMessage: nil,
            displayName: "gpt-5.4-mini",
            description: "Faster lower-cost model",
            hidden: false,
            supportedReasoningEfforts: [
                ReasoningEffortOption(reasoningEffort: .low, description: "Fast"),
                ReasoningEffortOption(reasoningEffort: .medium, description: "Balanced")
            ],
            defaultReasoningEffort: .medium,
            inputModalities: [.text],
            supportsPersonality: true,
            isDefault: false,
            agentRuntimeKind: "codex"
        )
    ]

    static let sampleMessages: [ChatMessage] = [
        ChatMessage(
            role: .user,
            text: "why is repo_q1 pinned while patch repair is maxed out?",
            sourceTurnId: "turn-1",
            sourceTurnIndex: 0,
            isFromUserTurnBoundary: true
        ),
        ChatMessage(
            role: .assistant,
            text: """
            I found the relevant scheduler gate. `repo_jobs_q1` is being held behind the repo-fetch branch, so the patch lane keeps draining while clone/fetch never gets enqueued.

            Next step is to trace the worker split against `patch_repair` and `repo_jobs` thresholds.
            """,
            agentNickname: "Latest",
            agentRole: "explorer"
        ),
        ChatMessage(
            role: .system,
            text: """
            ### Command Execution
            status: completed
            duration: 1.2s

            Command
            ```bash
            rg -n "repo_jobs|patch_repair" scheduler.py
            ```

            Output
            ```text
            42: if repo_jobs_q1 < 100000 { ... }
            ```
            """
        )
    ]

    static let sampleToolCallModel = ToolCallCardModel(
        kind: .commandExecution,
        title: "Command Execution",
        summary: "rg scheduler gate completed",
        status: .completed,
        duration: "1.2s",
        sections: [
            .kv(label: "Metadata", entries: [
                ToolCallKeyValue(key: "Status", value: "completed"),
                ToolCallKeyValue(key: "Directory", value: sampleCwd)
            ]),
            .code(
                label: "Command",
                language: "bash",
                content: #"rg -n "repo_jobs|patch_repair" scheduler.py"#
            ),
            .text(
                label: "Result",
                content: "Found the repo gate that prevents clone/fetch work from being scheduled."
            )
        ]
    )

    static var longConversation: [ChatMessage] {
        var msgs: [ChatMessage] = []
        let questions = [
            "How does the scheduler handle repo_jobs_q1 when patch repair is saturated?",
            "Can you trace the worker split against patch_repair thresholds?",
            "What happens when clone/fetch never gets enqueued?",
            "Is repo-first mode actually enabled in the current config?",
            "Show me the fairness weights for the repo lane.",
            "Why does the gate hold at 100k instead of scaling dynamically?",
            "Can we add a priority override for time-sensitive repo fetches?",
            "What are the queue metrics from the last hour?",
            "How do we test the scheduler changes without affecting production?",
            "Summarize the full patch repair bottleneck analysis.",
            "What is the expected throughput after the fairness fix?",
            "Can you write a migration plan for the scheduler changes?",
        ]
        let answers = [
            "The scheduler gate at line 42 checks `repo_jobs_q1` against a threshold of 100,000. When patch repair is saturated, the gate holds all repo-fetch work behind the branch queue.\n\n```python\nif repo_jobs_q1 < 100000:\n    enqueue(patch_repair_lane)\nelse:\n    defer_to_next_cycle()\n```\n\nThis means clone/fetch never gets scheduled while the patch lane is draining.",
            "The worker split is 70/30 in favor of patch_repair. The `repo_jobs` threshold is checked before any repo work is enqueued, so the split only applies after the gate opens.",
            "When clone/fetch is starved, the repo queue grows unbounded. Eventually the OOM killer steps in, which is how we first noticed the issue in production.",
            "Not from the current scheduler state. The gate is configured but the repo-first flag was never flipped in the deploy config. It's still set to `false`.",
            "Current fairness weights:\n\n```yaml\npatch_repair: 0.7\nrepo_fetch: 0.2\nclone: 0.1\n```\n\nThese haven't been updated since the initial rollout.",
            "The 100k threshold was chosen based on early benchmarks when repo sizes were smaller. With current repo sizes averaging 2.3GB, the threshold should be closer to 500k to avoid premature gating.",
            "Yes, we can add a `priority_override` field to the job struct. When set, it bypasses the gate check and goes directly to the front of the queue.\n\n```python\nif job.priority_override:\n    fast_enqueue(job)\n    return\n```",
            "Queue metrics for the last hour show patch_repair at 94% utilization, repo_fetch at 12%, and clone at 3%. The imbalance is clear.",
            "Best approach is a shadow deployment: run the new scheduler in read-only mode alongside production, compare decisions without actually routing traffic. We did this for the last major scheduler change.",
            "The bottleneck stems from a hard-coded gate threshold that hasn't scaled with repo sizes. The fix involves dynamic thresholds based on queue depth, fairness weights, and a priority override system.",
            "After the fairness fix, we expect repo_fetch utilization to rise from 12% to around 45%, with patch_repair dropping to 60%. Overall throughput should increase by roughly 30%.",
            "Migration plan:\n1. Deploy dynamic threshold config (no behavior change)\n2. Enable shadow mode for new scheduler\n3. Compare metrics for 24h\n4. Gradual rollout: 10% -> 50% -> 100%\n5. Monitor for 48h before removing old code path",
        ]
        for i in 0..<questions.count {
            msgs.append(ChatMessage(
                role: .user,
                text: questions[i],
                sourceTurnId: "turn-\(i * 2)",
                sourceTurnIndex: 0,
                isFromUserTurnBoundary: true
            ))
            msgs.append(ChatMessage(
                role: .assistant,
                text: answers[i]
            ))
        }
        return msgs
    }

    static var sampleDiscoveryServers: [DiscoveredServer] {
        [sampleBonjourServer, sampleSSHServer, sampleServer]
    }
}
#endif
