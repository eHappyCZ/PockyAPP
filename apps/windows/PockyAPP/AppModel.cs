using uniffi.pocky_ffi;

namespace PockyAPP;

internal enum AppState
{
    Loading,
    SignedOut,
    SignedIn,
}

internal sealed class AppModel
{
    private static readonly string DeviceName = $"PockyAPP Windows · {Environment.MachineName}";

    public AppState State { get; private set; } = AppState.Loading;

    public PockyClient? Client { get; private set; }

    public string DefaultServer { get; } = PockyFfiMethods.DefaultServer();

    public event Action? Changed;

    public async Task Resume()
    {
        var session = SessionStore.LoadSession();
        if (session == null)
        {
            Move(AppState.SignedOut);
            return;
        }
        try
        {
            Client = await PockyFfiMethods.RestoreClient(session, SessionStore.StorePath, SessionStore.DatabasePassphrase());
            Move(AppState.SignedIn);
        }
        catch (PockyException)
        {
            SessionStore.Clear();
            Move(AppState.SignedOut);
        }
    }

    public async Task SignIn(string username, string password, string server)
    {
        server = server.Trim();
        if (server != DefaultServer)
        {
            await PockyFfiMethods.CheckServer(server);
        }
        SessionStore.Clear();
        var client = await PockyFfiMethods.CreateClient(SessionStore.StorePath, SessionStore.DatabasePassphrase(), server);
        var session = await client.Login(username.Trim(), password, DeviceName);
        SessionStore.SaveSession(session);
        Client = client;
        Move(AppState.SignedIn);
    }

    public async Task Register(string username, string password, string inviteCode)
    {
        SessionStore.Clear();
        var client = await PockyFfiMethods.CreateClient(SessionStore.StorePath, SessionStore.DatabasePassphrase(), DefaultServer);
        var session = await client.Register(username.Trim(), password, inviteCode.Trim(), DeviceName);
        SessionStore.SaveSession(session);
        Client = client;
        Move(AppState.SignedIn);
    }

    public async Task SignOut()
    {
        if (Client != null)
        {
            try
            {
                await Client.Logout();
            }
            catch (PockyException)
            {
            }
        }
        SessionStore.Clear();
        Client = null;
        Move(AppState.SignedOut);
    }

    public void SaveSession()
    {
        var session = Client?.ExportSession();
        if (session != null)
        {
            SessionStore.SaveSession(session);
        }
    }

    private void Move(AppState state)
    {
        State = state;
        Changed?.Invoke();
    }

    public static string ErrorText(Exception error) => error switch
    {
        PockyException.InvalidCredentials => Loc.T("Nesprávné jméno nebo heslo.", "Wrong username or password."),
        PockyException.UserDeactivated => Loc.T("Tento účet byl deaktivován.", "This account has been deactivated."),
        PockyException.RateLimited => Loc.T("Příliš mnoho pokusů. Zkus to za chvíli.", "Too many attempts. Try again in a moment."),
        PockyException.ServerNotFound => Loc.T("Takový Matrix server se nepodařilo najít.", "That Matrix server could not be found."),
        PockyException.PasswordLoginUnsupported => Loc.T("Tenhle server nepovoluje přihlášení heslem.", "This server does not allow signing in with a password."),
        PockyException.Network => Loc.T("Server není dostupný. Zkontroluj adresu serveru a připojení.", "The server is unreachable. Check the server address and your connection."),
        PockyException.NotLoggedIn or PockyException.InvalidSession => Loc.T("Přihlášení vypršelo. Přihlas se znovu.", "Your session expired. Sign in again."),
        PockyException.UsernameTaken => Loc.T("Toto uživatelské jméno je už obsazené.", "That username is already taken."),
        PockyException.InvalidInviteCode => Loc.T("Pozvánkový kód není platný.", "The invitation code is not valid."),
        PockyException.RegistrationUnavailable => Loc.T("Na tomto serveru se nedá registrovat.", "Registration is not available on this server."),
        PockyException.WeakPassword => Loc.T("Heslo je příliš slabé.", "The password is too weak."),
        _ => Loc.T("Něco se pokazilo. Zkus to znovu.", "Something went wrong. Try again."),
    };
}
