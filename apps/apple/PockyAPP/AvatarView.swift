import PockyCore
import SwiftUI
import UIKit

@MainActor
final class AvatarCache {
    static let shared = AvatarCache()
    private let images = NSCache<NSString, UIImage>()

    func image(for url: String, size: Int, client: PockyClient) async -> UIImage? {
        let key = "\(url)|\(size)" as NSString
        if let cached = images.object(forKey: key) {
            return cached
        }
        guard let data = try? await client.loadMedia(source: url, width: UInt32(size), height: UInt32(size)),
              let image = UIImage(data: data)
        else { return nil }
        images.setObject(image, forKey: key)
        return image
    }
}

struct AvatarView: View {
    let id: String
    let name: String
    let url: String?
    let client: PockyClient
    var size: CGFloat = 48
    var square = false

    @State private var image: UIImage?

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: square ? size * 0.3 : size / 2, style: .continuous)
        ZStack {
            shape.fill(color)
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
            } else {
                Text(initials)
                    .font(.system(size: size * 0.36, weight: .heavy))
                    .foregroundStyle(.white)
            }
        }
        .frame(width: size, height: size)
        .clipShape(shape)
        .task(id: url) {
            image = nil
            guard let url else { return }
            image = await AvatarCache.shared.image(for: url, size: Int(size * 2), client: client)
        }
    }

    private var color: Color {
        var hash: Int32 = 0
        for unit in id.utf16 {
            hash = hash &* 31 &+ Int32(unit)
        }
        let count = Int32(Palette.avatars.count)
        return Palette.avatars[Int(((hash % count) + count) % count)]
    }

    private var initials: String {
        let words = name
            .trimmingCharacters(in: CharacterSet(charactersIn: "#@! "))
            .split(whereSeparator: { " ._-·".contains($0) })
        guard let first = words.first else { return "?" }
        if words.count == 1 { return String(first.prefix(2)).uppercased() }
        return (String(first.prefix(1)) + String(words[1].prefix(1))).uppercased()
    }
}
