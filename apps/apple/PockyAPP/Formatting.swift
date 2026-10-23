import Foundation
import PockyCore

private let czech = Locale(identifier: "cs_CZ")

func shortName(_ userId: String) -> String {
    let local = userId.split(separator: ":").first.map(String.init) ?? userId
    return local.hasPrefix("@") ? String(local.dropFirst()) : local
}

func unreadText(_ count: UInt64) -> String {
    switch count {
    case 1: "1 nepřečtená zpráva"
    case 2...4: "\(count) nepřečtené zprávy"
    default: "\(count) nepřečtených zpráv"
    }
}

func formatTimestamp(_ timestampMs: UInt64) -> String {
    let date = Date(timeIntervalSince1970: TimeInterval(timestampMs) / 1000)
    let calendar = Calendar.current
    if calendar.isDateInToday(date) {
        return date.formatted(.dateTime.hour().minute().locale(czech))
    }
    if calendar.isDateInYesterday(date) {
        return "Včera"
    }
    if let days = calendar.dateComponents([.day], from: date, to: .now).day, days < 7 {
        return date.formatted(.dateTime.weekday(.abbreviated).locale(czech)).capitalized(with: czech)
    }
    if calendar.isDate(date, equalTo: .now, toGranularity: .year) {
        return date.formatted(.dateTime.day().month(.defaultDigits).locale(czech))
    }
    return date.formatted(.dateTime.day().month(.defaultDigits).year().locale(czech))
}

func previewText(_ content: MessageContent) -> String {
    switch content {
    case .text(let body): body
    case .emote(let body): "* \(body)"
    case .image: "📷 Obrázek"
    case .video: "🎬 Video"
    case .audio: "🎤 Zvuková zpráva"
    case .file: "📎 Soubor"
    case .location: "📍 Poloha"
    case .sticker: "Nálepka"
    case .poll: "📊 Anketa"
    case .encrypted: "🔒 Šifrovaná zpráva"
    case .invite: "Pozvánka do konverzace"
    case .other: "Zpráva"
    }
}
