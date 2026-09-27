import SwiftUI

struct HomeCatFooterView: View {
    let playEntrance: Bool

    @State private var showingLoop: Bool

    private let entranceURL = Bundle.main.url(forResource: "home_cat_entrance", withExtension: "png")
    private let loopURL = Bundle.main.url(forResource: "home_cat", withExtension: "png")

    init(playEntrance: Bool) {
        self.playEntrance = playEntrance
        self._showingLoop = State(initialValue: !playEntrance)
    }

    var body: some View {
        GeometryReader { proxy in
            if let imageURL = showingLoop ? loopURL : (entranceURL ?? loopURL) {
                let width = min(max(0, proxy.size.width - 48), 340)
                VStack {
                    AlphaAnimatedImageView(
                        fileURL: imageURL,
                        repeatCount: showingLoop ? 0 : 1,
                        onFinished: showingLoop ? nil : {
                            showingLoop = true
                        }
                    )
                    .frame(width: width, height: width * 9.0 / 16.0)
                    .accessibilityHidden(true)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                .padding(.top, 12)
                .padding(.bottom, 20)
            }
        }
    }
}
