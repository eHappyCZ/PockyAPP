using System.Security.Cryptography;
using System.Text;

namespace PockyAPP;

internal static class SessionStore
{
    private const string SessionFile = "session.bin";
    private const string DatabaseFile = "database.bin";

    public static string Root { get; } = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "PockyAPP");

    public static string StorePath => Path.Combine(Root, "store");

    public static string? LoadSession() => Read(SessionFile);

    public static void SaveSession(string session) => Write(SessionFile, session);

    public static string DatabasePassphrase()
    {
        var existing = Read(DatabaseFile);
        if (existing != null)
        {
            return existing;
        }
        var passphrase = Convert.ToBase64String(RandomNumberGenerator.GetBytes(32));
        Write(DatabaseFile, passphrase);
        return passphrase;
    }

    public static void Clear()
    {
        Delete(SessionFile);
        Delete(DatabaseFile);
    }

    private static string? Read(string name)
    {
        var path = Path.Combine(Root, name);
        if (!File.Exists(path))
        {
            return null;
        }
        try
        {
            var bytes = ProtectedData.Unprotect(File.ReadAllBytes(path), null, DataProtectionScope.CurrentUser);
            return Encoding.UTF8.GetString(bytes);
        }
        catch (CryptographicException)
        {
            return null;
        }
    }

    private static void Write(string name, string value)
    {
        Directory.CreateDirectory(Root);
        var bytes = ProtectedData.Protect(Encoding.UTF8.GetBytes(value), null, DataProtectionScope.CurrentUser);
        File.WriteAllBytes(Path.Combine(Root, name), bytes);
    }

    private static void Delete(string name)
    {
        var path = Path.Combine(Root, name);
        if (File.Exists(path))
        {
            File.Delete(path);
        }
    }
}
