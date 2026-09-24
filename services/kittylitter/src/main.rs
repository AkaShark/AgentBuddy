fn main() -> anyhow::Result<()> {
    alleycat::App {
        binary_name: "agentbuddy",
        qualifier: "com",
        organization: "akashark",
        application: "agentbuddycli",
        label: "com.akashark.agentbuddycli",
        version: env!("CARGO_PKG_VERSION"),
        // Default Worker for host-reported turn notifications; host.toml [push]
        // worker_url overrides it and [push] enabled = false turns it off.
        push_worker_url: Some("https://agentbuddy-push-proxy.aaksharker.workers.dev"),
    }
    .run()
}
