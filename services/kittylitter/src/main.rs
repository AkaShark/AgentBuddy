fn main() -> anyhow::Result<()> {
    alleycat::App {
        binary_name: "agentbuddy",
        qualifier: "com",
        organization: "akashark",
        application: "agentbuddycli",
        label: "com.akashark.agentbuddycli",
        version: env!("CARGO_PKG_VERSION"),
    }
    .run()
}
