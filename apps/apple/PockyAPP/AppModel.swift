import Foundation
import Observation
import PockyCore
import UIKit

@MainActor
@Observable
final class AppModel {
    enum State {
        case loading
        case signedOut
        case signedIn(PockyClient)

        var id: Int {
            switch self {
            case .loading: 0
            case .signedOut: 1
            case .signedIn: 2
            }
        }
    }

    private(set) var state: State = .loading
    let defaultServer = PockyCore.defaultServer()

    func resume() async {
        guard case .loading = state else { return }
        guard let session = SessionStore.loadSession() else {
            state = .signedOut
            return
        }
        do {
            let client = try await restoreClient(
                session: session,
                dataDir: SessionStore.storePath,
                passphrase: SessionStore.databasePassphrase()
            )
            state = .signedIn(client)
        } catch {
            SessionStore.clear()
            state = .signedOut
        }
    }

    func signIn(username: String, password: String, server: String) async throws {
        let server = server.trimmingCharacters(in: .whitespaces)
        if server != defaultServer {
            _ = try await checkServer(server: server)
        }
        SessionStore.clear()
        let client = try await createClient(
            dataDir: SessionStore.storePath,
            passphrase: SessionStore.databasePassphrase(),
            server: server
        )
        let session = try await client.login(username: username, password: password, deviceName: deviceName)
        SessionStore.saveSession(session)
        state = .signedIn(client)
    }

    func signOut() async {
        guard case .signedIn(let client) = state else { return }
        try? await client.logout()
        SessionStore.clear()
        state = .signedOut
    }

    func saveSession() {
        guard case .signedIn(let client) = state, let session = client.exportSession() else { return }
        SessionStore.saveSession(session)
    }

    private var deviceName: String {
        "PockyAPP iOS · \(UIDevice.current.model)"
    }
}

func errorText(_ error: Error) -> String {
    switch error as? PockyError {
    case .InvalidCredentials: "Nesprávné jméno nebo heslo."
    case .UserDeactivated: "Tento účet byl deaktivován."
    case .RateLimited: "Příliš mnoho pokusů. Zkus to za chvíli."
    case .ServerNotFound: "Takový Matrix server se nepodařilo najít."
    case .PasswordLoginUnsupported: "Tenhle server nepovoluje přihlášení heslem."
    case .Network: "Server není dostupný. Zkontroluj adresu serveru a připojení."
    case .NotLoggedIn, .InvalidSession: "Přihlášení vypršelo. Přihlas se znovu."
    default: "Něco se pokazilo. Zkus to znovu."
    }
}
