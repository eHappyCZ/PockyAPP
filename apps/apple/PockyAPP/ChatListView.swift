import PockyCore
import SwiftUI

@MainActor
@Observable
final class ChatListModel {
    private(set) var rooms: [Room]?
    private(set) var status: SyncStatus = .idle
    private(set) var name = ""

    let client: PockyClient
    private var handles: [TaskHandle] = []

    init(client: PockyClient) {
        self.client = client
        name = client.userId().map(shortName) ?? ""
    }

    func start() async {
        guard handles.isEmpty else { return }
        do {
            handles.append(try await client.observeSyncStatus(listener: SyncFeed { [weak self] value in
                Task { @MainActor in self?.status = value }
            }))
            handles.append(try await client.observeRooms(listener: RoomFeed { [weak self] value in
                Task { @MainActor in self?.rooms = value }
            }))
            try await client.startSync()
        } catch {
            status = .error
        }
        if let display = try? await client.displayName() {
            name = display
        }
    }

    func stop() {
        handles.forEach { $0.cancel() }
        handles.removeAll()
    }
}

private final class RoomFeed: RoomListListener, @unchecked Sendable {
    let onRooms: ([Room]) -> Void
    init(_ onRooms: @escaping ([Room]) -> Void) { self.onRooms = onRooms }
    func onUpdate(rooms: [Room]) { onRooms(rooms) }
}

private final class SyncFeed: SyncStatusListener, @unchecked Sendable {
    let onStatus: (SyncStatus) -> Void
    init(_ onStatus: @escaping (SyncStatus) -> Void) { self.onStatus = onStatus }
    func onUpdate(status: SyncStatus) { onStatus(status) }
}

struct ChatListView: View {
    @Environment(AppModel.self) private var app
    @State private var model: ChatListModel
    @State private var search = ""
    @State private var filter = Filter.all
    @State private var confirmingLogout = false

    enum Filter: String, CaseIterable, Identifiable {
        case all = "Vše"
        case unread = "Nepřečtené"
        case people = "Lidé"
        case groups = "Skupiny"

        var id: String { rawValue }
    }

    init(client: PockyClient) {
        _model = State(initialValue: ChatListModel(client: client))
    }

    private var visible: [Room] {
        let term = search.trimmingCharacters(in: .whitespaces).lowercased()
        return (model.rooms ?? []).filter { room in
            if !term.isEmpty && !room.name.lowercased().contains(term) { return false }
            switch filter {
            case .all: return true
            case .unread: return room.unreadMessages > 0 || room.isInvite
            case .people: return room.isDirect
            case .groups: return !room.isDirect
            }
        }
    }

    private var unread: UInt64 {
        (model.rooms ?? []).reduce(0) { $0 + ($1.isMuted ? 0 : $1.unreadMessages) }
    }

    private var statusText: String {
        switch model.status {
        case .offline, .error: return "Offline"
        default:
            if model.rooms == nil { return "Načítám chaty…" }
            return unread > 0 ? unreadText(unread) : "Vše přečteno ✓"
        }
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    filters
                        .listRowInsets(EdgeInsets(top: 0, leading: 16, bottom: 6, trailing: 16))
                }
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)

                ForEach(visible, id: \.id) { room in
                    RoomRow(room: room, client: model.client)
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                        .listRowInsets(EdgeInsets(top: 4, leading: 12, bottom: 4, trailing: 12))
                }

                if model.rooms != nil && visible.isEmpty {
                    Text(search.isEmpty ? "Zatím tu nic není. Až ti někdo napíše, objeví se tady." : "Nic nenalezeno.")
                        .foregroundStyle(Palette.inkMuted)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 30)
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .background(Palette.bg)
            .searchable(text: $search, prompt: "Hledat v chatech")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    VStack(alignment: .leading, spacing: 0) {
                        Text("Ahoj, \(model.name) 👋")
                            .font(.system(.title3, design: .rounded, weight: .bold))
                            .foregroundStyle(Palette.ink)
                        Text(statusText)
                            .font(.caption)
                            .foregroundStyle(model.status == .running && unread == 0 ? Palette.success : Palette.inkMuted)
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        confirmingLogout = true
                    } label: {
                        Image(systemName: "rectangle.portrait.and.arrow.right")
                    }
                    .foregroundStyle(Palette.inkMuted)
                }
            }
            .toolbarBackground(Palette.bg, for: .navigationBar)
            .confirmationDialog("Odhlásit se?", isPresented: $confirmingLogout, titleVisibility: .visible) {
                Button("Odhlásit", role: .destructive) {
                    model.stop()
                    Task { await app.signOut() }
                }
            } message: {
                Text("Bez zálohy klíčů nebo jiného zařízení nepůjde po odhlášení číst starší šifrované zprávy.")
            }
        }
        .task { await model.start() }
    }

    private var filters: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach(Filter.allCases) { option in
                    Button(option.rawValue) { filter = option }
                        .font(.subheadline)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(
                            Capsule().fill(filter == option ? Palette.accent.opacity(0.22) : .clear)
                        )
                        .overlay(Capsule().stroke(filter == option ? .clear : Palette.line))
                        .foregroundStyle(filter == option ? Palette.ink : Palette.inkMuted)
                }
            }
        }
    }
}

struct RoomRow: View {
    let room: Room
    let client: PockyClient

    var body: some View {
        HStack(spacing: 12) {
            AvatarView(id: room.id, name: room.name, url: room.avatarUrl, client: client, size: 50, square: !room.isDirect)
            VStack(alignment: .leading, spacing: 3) {
                Text(room.name)
                    .font(.body.weight(.semibold))
                    .foregroundStyle(Palette.ink)
                    .lineLimit(1)
                Text(preview)
                    .font(.subheadline)
                    .foregroundStyle(Palette.inkMuted)
                    .lineLimit(1)
            }
            Spacer(minLength: 8)
            VStack(alignment: .trailing, spacing: 6) {
                HStack(spacing: 4) {
                    if room.isFavourite { Image(systemName: "pin.fill").font(.caption2) }
                    if room.isMuted { Image(systemName: "bell.slash.fill").font(.caption2) }
                    if let time = room.timestampMs { Text(formatTimestamp(time)) }
                }
                .font(.caption)
                .foregroundStyle(Palette.inkMuted)

                if room.unreadMessages > 0 {
                    Text("\(room.unreadMessages)")
                        .font(.caption.bold())
                        .padding(.horizontal, 7)
                        .frame(minWidth: 22, minHeight: 22)
                        .background(Capsule().fill(room.isMuted ? Palette.inkMuted.opacity(0.4) : Palette.accent))
                        .foregroundStyle(.white)
                }
            }
        }
        .padding(10)
        .contentShape(Rectangle())
    }

    private var preview: String {
        if room.isInvite { return "Pozvánka do konverzace" }
        guard let message = room.lastMessage else { return "" }
        let text = previewText(message.content)
        if message.isOwn {
            let ticks = message.delivery == .read ? "✓✓ " : ""
            return "\(ticks)Ty: \(text)"
        }
        if !room.isDirect, let sender = message.senderName {
            return "\(sender): \(text)"
        }
        return text
    }
}
