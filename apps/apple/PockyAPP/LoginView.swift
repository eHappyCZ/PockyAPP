import SwiftUI

struct LoginView: View {
    @Environment(AppModel.self) private var model

    @State private var username = ""
    @State private var password = ""
    @State private var server = ""
    @State private var editingServer = false
    @State private var busy = false
    @State private var error: String?
    @FocusState private var focus: Field?

    private enum Field {
        case username, password, server
    }

    private var ready: Bool {
        !busy && !username.trimmingCharacters(in: .whitespaces).isEmpty && !password.isEmpty
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Logo()
                    .frame(width: 64, height: 80)
                    .padding(.bottom, 12)

                Text("Vítej v PockyAPP")
                    .font(.system(size: 30, weight: .bold, design: .rounded))
                    .foregroundStyle(Palette.ink)
                Text("Soukromý chat s šifrováním pro komunitu Pokecio.")
                    .foregroundStyle(Palette.inkMuted)
                    .padding(.bottom, 18)

                serverRow

                TextField("", text: $username, prompt: prompt("Uživatelské jméno"))
                    .textContentType(.username)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .focused($focus, equals: .username)
                    .submitLabel(.next)
                    .onSubmit { focus = .password }
                    .field()

                SecureField("", text: $password, prompt: prompt("Heslo"))
                    .textContentType(.password)
                    .focused($focus, equals: .password)
                    .submitLabel(.go)
                    .onSubmit(submit)
                    .field()

                if let error {
                    Text(error)
                        .font(.subheadline)
                        .foregroundStyle(Palette.danger)
                }

                Button(action: submit) {
                    if busy {
                        ProgressView().tint(.white)
                    } else {
                        Text("Přihlásit se")
                    }
                }
                .buttonStyle(PrimaryButton(enabled: ready))
                .disabled(!ready)
                .padding(.top, 6)
            }
            .padding(24)
            .frame(maxWidth: 440)
            .frame(maxWidth: .infinity)
        }
        .scrollDismissesKeyboard(.interactively)
        .onAppear {
            if server.isEmpty { server = model.defaultServer }
        }
    }

    private var serverRow: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Server")
                    .foregroundStyle(Palette.inkMuted)
                Text(server)
                    .foregroundStyle(Palette.ink)
                    .lineLimit(1)
                Spacer()
                Button(editingServer ? "Hotovo" : "Změnit") {
                    editingServer.toggle()
                    focus = editingServer ? .server : nil
                }
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Palette.accentSoft)
            }
            .font(.subheadline)

            if editingServer {
                TextField("", text: $server, prompt: prompt("třeba matrix.org"))
                    .keyboardType(.URL)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .focused($focus, equals: .server)
                    .field()
            }
        }
        .padding(.bottom, 4)
    }

    private func prompt(_ text: String) -> Text {
        Text(text).foregroundStyle(Palette.inkMuted.opacity(0.7))
    }

    private func submit() {
        guard ready else { return }
        busy = true
        error = nil
        let server = server.isEmpty ? model.defaultServer : server
        Task {
            do {
                try await model.signIn(username: username, password: password, server: server)
            } catch {
                self.error = errorText(error)
            }
            busy = false
        }
    }
}

struct Logo: View {
    var body: some View {
        Canvas { context, size in
            let scale = min(size.width / 255, size.height / 320)
            context.scaleBy(x: scale, y: scale)

            var glyph = Path()
            glyph.move(to: CGPoint(x: 0, y: 55))
            glyph.addArc(center: CGPoint(x: 55, y: 55), radius: 55, startAngle: .degrees(180), endAngle: .degrees(270), clockwise: false)
            glyph.addLine(to: CGPoint(x: 147.5, y: 0))
            glyph.addArc(center: CGPoint(x: 147.5, y: 107.5), radius: 107.5, startAngle: .degrees(270), endAngle: .degrees(90), clockwise: false)
            glyph.addLine(to: CGPoint(x: 107, y: 215))
            glyph.addQuadCurve(to: CGPoint(x: 85, y: 237), control: CGPoint(x: 85, y: 215))
            glyph.addLine(to: CGPoint(x: 85, y: 277.5))
            glyph.addArc(center: CGPoint(x: 42.5, y: 277.5), radius: 42.5, startAngle: .degrees(0), endAngle: .degrees(180), clockwise: false)
            glyph.closeSubpath()

            context.fill(
                glyph,
                with: .linearGradient(
                    Gradient(colors: [Color(hex: 0xDCCFF7), Color(hex: 0xA466DE)]),
                    startPoint: .zero,
                    endPoint: CGPoint(x: 0, y: 320)
                )
            )
            for x in [74.0, 130.0, 186.0] {
                context.fill(Path(ellipseIn: CGRect(x: x - 20, y: 87.5, width: 40, height: 40)), with: .color(Palette.ink))
            }
        }
    }
}
