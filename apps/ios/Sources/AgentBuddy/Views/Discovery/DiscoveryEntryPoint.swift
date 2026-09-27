/// Where the add-host flow starts. `.pairWithQRCode` skips the chooser and
/// opens the QR pairing sheet straight away (the home "Scan to connect"
/// action); `.chooser` shows the three connection options first.
enum DiscoveryEntryPoint {
    case chooser
    case pairWithQRCode
}
