import SwiftUI

struct BrandLogo: View {
    var size: CGFloat

    var body: some View {
        BuddyBrandMark(size: size)
    }
}

#if DEBUG
#Preview("Brand Logo") {
    ZStack {
        AgentBuddyTheme.background.ignoresSafeArea()
        BrandLogo(size: BuddySize.splashMark)
    }
}
#endif
