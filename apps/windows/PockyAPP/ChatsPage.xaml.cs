using System.Collections.ObjectModel;
using Microsoft.UI.Dispatching;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Navigation;
using uniffi.pocky_ffi;

namespace PockyAPP;

public sealed partial class ChatsPage : Page
{
    private readonly AppModel model = App.Model;
    private readonly Dictionary<string, RoomRow> rows = new();
    private readonly ObservableCollection<RoomRow> visible = new();
    private readonly DispatcherQueue dispatcher = DispatcherQueue.GetForCurrentThread();
    private Room[] rooms = [];
    private TaskHandle? roomsTask;
    private TaskHandle? statusTask;
    private SyncStatus status = SyncStatus.Idle;

    public ChatsPage()
    {
        InitializeComponent();
        HeaderTitle.Text = Loc.T("Chaty", "Chats");
        Search.PlaceholderText = Loc.T("Hledat", "Search");
        SignOutItem.Text = Loc.T("Odhlásit se", "Sign out");
        Placeholder.Text = Loc.T("Vyber chat ze seznamu.", "Select a chat from the list.");
        EmptyText.Text = Loc.T("Zatím tu nic není.", "Nothing here yet.");
        RoomList.ItemsSource = visible;
    }

    protected override async void OnNavigatedTo(NavigationEventArgs e)
    {
        var client = model.Client;
        if (client == null)
        {
            return;
        }
        try
        {
            await client.StartSync();
            roomsTask = await client.ObserveRooms(new RoomsListener(this));
            statusTask = await client.ObserveSyncStatus(new StatusListener(this));
            model.SaveSession();
        }
        catch (PockyException error)
        {
            StatusText.Text = AppModel.ErrorText(error);
            StatusText.Visibility = Visibility.Visible;
        }
    }

    protected override void OnNavigatedFrom(NavigationEventArgs e)
    {
        roomsTask?.Dispose();
        statusTask?.Dispose();
        roomsTask = statusTask = null;
    }

    private void ApplyRooms(Room[] next)
    {
        rooms = next;
        Rebuild();
    }

    private void ApplyStatus(SyncStatus next)
    {
        status = next;
        StatusText.Text = status switch
        {
            SyncStatus.Offline => Loc.T("Offline, čekám na připojení…", "Offline, waiting for a connection…"),
            SyncStatus.Error => Loc.T("Synchronizace selhala, zkouším znovu…", "Sync failed, retrying…"),
            SyncStatus.Idle => Loc.T("Připojuji se…", "Connecting…"),
            _ => "",
        };
        StatusText.Visibility = StatusText.Text.Length > 0 ? Visibility.Visible : Visibility.Collapsed;
    }

    private void Rebuild()
    {
        var term = Search.Text.Trim();
        var wanted = new List<RoomRow>();
        foreach (var room in rooms.OrderByDescending(r => r.IsInvite))
        {
            if (term.Length > 0 && !room.Name.Contains(term, StringComparison.CurrentCultureIgnoreCase))
            {
                continue;
            }
            if (rows.TryGetValue(room.Id, out var row))
            {
                row.Update(room);
            }
            else
            {
                row = new RoomRow(room);
                rows[room.Id] = row;
            }
            wanted.Add(row);
        }

        var ids = rooms.Select(r => r.Id).ToHashSet();
        foreach (var gone in rows.Keys.Where(id => !ids.Contains(id)).ToList())
        {
            rows.Remove(gone);
        }

        var selected = (RoomList.SelectedItem as RoomRow)?.Id;
        if (!visible.SequenceEqual(wanted))
        {
            visible.Clear();
            foreach (var row in wanted)
            {
                visible.Add(row);
            }
        }
        if (selected != null && wanted.FirstOrDefault(r => r.Id == selected) is { } keep)
        {
            RoomList.SelectedItem = keep;
        }

        EmptyText.Visibility = wanted.Count == 0 ? Visibility.Visible : Visibility.Collapsed;

        if (model.Client is { } client)
        {
            foreach (var row in wanted)
            {
                _ = row.LoadAvatar(client);
            }
        }
    }

    private void OnSearch(object sender, TextChangedEventArgs e) => Rebuild();

    private void OnSelected(object sender, SelectionChangedEventArgs e)
    {
    }

    private async void OnAccept(object sender, RoutedEventArgs e)
    {
        if (((FrameworkElement)sender).Tag is RoomRow row && model.Client is { } client)
        {
            await Try(() => client.AcceptInvite(row.Id));
        }
    }

    private async void OnDecline(object sender, RoutedEventArgs e)
    {
        if (((FrameworkElement)sender).Tag is RoomRow row && model.Client is { } client)
        {
            await Try(() => client.DeclineInvite(row.Id));
        }
    }

    private async void OnSignOut(object sender, RoutedEventArgs e) => await model.SignOut();

    private static async Task Try(Func<Task> action)
    {
        try
        {
            await action();
        }
        catch (PockyException)
        {
        }
    }

    private sealed class RoomsListener(ChatsPage page) : RoomListListener
    {
        public void OnUpdate(Room[] rooms) => page.dispatcher.TryEnqueue(() => page.ApplyRooms(rooms));
    }

    private sealed class StatusListener(ChatsPage page) : SyncStatusListener
    {
        public void OnUpdate(SyncStatus status) => page.dispatcher.TryEnqueue(() => page.ApplyStatus(status));
    }
}
