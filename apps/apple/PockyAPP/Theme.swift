import SwiftUI

enum Palette {
    static let bg = Color(hex: 0x08070C)
    static let surface = Color(hex: 0x121019)
    static let accent = Color(hex: 0x8B5CF6)
    static let accentSoft = Color(hex: 0xB8A1FB)
    static let bubbleOut = Color(hex: 0x7C3AED)
    static let ink = Color(hex: 0xF4F2F9)
    static let inkMuted = Color(hex: 0xA4A0B3)
    static let success = Color(hex: 0x34D399)
    static let gold = Color(hex: 0xF3CD66)
    static let danger = Color(hex: 0xF87171)
    static let line = Color.white.opacity(0.08)

    static let avatars: [Color] = [
        Color(hex: 0x7C3AED), Color(hex: 0x8B5CF6), Color(hex: 0x6366F1), Color(hex: 0xDB2777),
        Color(hex: 0x0EA5E9), Color(hex: 0x059669), Color(hex: 0xD97706),
    ]
}

extension Color {
    init(hex: UInt32) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}

struct FieldStyle: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(.horizontal, 14)
            .frame(height: 50)
            .background(Palette.surface, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Palette.line))
            .foregroundStyle(Palette.ink)
    }
}

struct PrimaryButton: ButtonStyle {
    var enabled = true

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .frame(maxWidth: .infinity)
            .frame(height: 52)
            .background(Palette.accent.opacity(enabled ? 1 : 0.45), in: RoundedRectangle(cornerRadius: 14))
            .foregroundStyle(.white)
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
    }
}

extension View {
    func field() -> some View {
        modifier(FieldStyle())
    }
}
