import SwiftUI

@main
struct PockyApp: App {
    @State private var model = AppModel()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(model)
                .preferredColorScheme(.dark)
                .tint(Palette.accent)
                .task { await model.resume() }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase != .active { model.saveSession() }
        }
    }
}

struct RootView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        ZStack {
            Palette.bg.ignoresSafeArea()
            switch model.state {
            case .loading:
                ProgressView().tint(Palette.accentSoft)
            case .signedOut:
                LoginView()
                    .transition(.opacity)
            case .signedIn(let client):
                ChatListView(client: client)
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.25), value: model.state.id)
    }
}
