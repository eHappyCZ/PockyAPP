using System.ComponentModel;
using System.Runtime.InteropServices.WindowsRuntime;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Imaging;
using uniffi.pocky_ffi;
using Windows.Storage.Streams;

namespace PockyAPP;

public sealed class RoomRow : INotifyPropertyChanged
{
    private static readonly Dictionary<string, ImageSource> AvatarCache = new();

    private Room room;
    private ImageSource? avatar;
    private string? avatarSource;

    internal RoomRow(Room room)
    {
        this.room = room;
    }

    public event PropertyChangedEventHandler? PropertyChanged;

    internal string Id => room.Id;

    internal bool IsInvite => room.IsInvite;

    internal Room Room => room;

    public string Name => room.Name;

    public string Preview => room.IsInvite
        ? Loc.T("Pozvánka do chatu", "Invitation to chat")
        : PreviewText(room.LastMessage);

    public string Time => room.TimestampMs is { } ms ? FormatTime(ms) : "";

    public string UnreadText => room.UnreadMessages > 99 ? "99+" : room.UnreadMessages.ToString();

    public bool HasUnread => !room.IsInvite && room.UnreadMessages > 0;

    public bool IsMuted => room.IsMuted;

    public bool IsFavourite => room.IsFavourite;

    public bool ShowInviteActions => room.IsInvite;

    public bool ShowMeta => !room.IsInvite;

    public string AcceptText => Loc.T("Přijmout", "Accept");

    public string DeclineText => Loc.T("Odmítnout", "Decline");

    public ImageSource? Avatar => avatar;

    internal void Update(Room next)
    {
        room = next;
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(string.Empty));
    }

    internal async Task LoadAvatar(PockyClient client)
    {
        var source = room.AvatarUrl;
        if (source == null || source == avatarSource)
        {
            return;
        }
        avatarSource = source;
        if (AvatarCache.TryGetValue(source, out var cached))
        {
            avatar = cached;
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(Avatar)));
            return;
        }
        try
        {
            var bytes = await client.LoadMedia(source, 96, 96);
            using var stream = new InMemoryRandomAccessStream();
            await stream.WriteAsync(bytes.AsBuffer());
            stream.Seek(0);
            var image = new BitmapImage();
            await image.SetSourceAsync(stream);
            AvatarCache[source] = image;
            avatar = image;
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(Avatar)));
        }
        catch (PockyException)
        {
            avatarSource = null;
        }
    }

    private static string PreviewText(LastMessage? message)
    {
        if (message == null)
        {
            return "";
        }
        var text = message.Content switch
        {
            MessageContent.Text t => t.Body,
            MessageContent.Emote e => e.Body,
            MessageContent.Image => Loc.T("Fotka", "Photo"),
            MessageContent.Video => Loc.T("Video", "Video"),
            MessageContent.Audio => Loc.T("Hlasová zpráva", "Voice message"),
            MessageContent.File => Loc.T("Soubor", "File"),
            MessageContent.Location => Loc.T("Poloha", "Location"),
            MessageContent.Sticker => Loc.T("Nálepka", "Sticker"),
            MessageContent.Poll => Loc.T("Anketa", "Poll"),
            MessageContent.Encrypted => Loc.T("Zašifrovaná zpráva", "Encrypted message"),
            _ => "",
        };
        text = text.ReplaceLineEndings(" ");
        if (message.IsOwn)
        {
            return $"{Loc.T("Ty", "You")}: {text}";
        }
        return message.SenderName is { Length: > 0 } sender ? $"{sender}: {text}" : text;
    }

    private static string FormatTime(ulong milliseconds)
    {
        var moment = DateTimeOffset.FromUnixTimeMilliseconds((long)milliseconds).ToLocalTime();
        var now = DateTimeOffset.Now;
        if (moment.Date == now.Date)
        {
            return moment.ToString("t");
        }
        if ((now.Date - moment.Date).TotalDays < 7)
        {
            return moment.ToString("ddd");
        }
        return moment.ToString("d");
    }
}
