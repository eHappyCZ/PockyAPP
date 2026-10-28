using System.Text.Json;
using Microsoft.UI.Xaml;

namespace PockyAPP;

internal enum ThemeChoice
{
    System,
    Light,
    Dark,
}

internal static class AppSettings
{
    private sealed record Stored(ThemeChoice Theme);

    private static readonly string FilePath = Path.Combine(SessionStore.Root, "settings.json");
    private static ThemeChoice theme = Load();

    public static event Action? Changed;

    public static ThemeChoice Theme
    {
        get => theme;
        set
        {
            if (theme == value)
            {
                return;
            }
            theme = value;
            Save();
            Changed?.Invoke();
        }
    }

    public static ElementTheme ElementTheme => theme switch
    {
        ThemeChoice.Light => Microsoft.UI.Xaml.ElementTheme.Light,
        ThemeChoice.Dark => Microsoft.UI.Xaml.ElementTheme.Dark,
        _ => Microsoft.UI.Xaml.ElementTheme.Default,
    };

    private static ThemeChoice Load()
    {
        try
        {
            if (File.Exists(FilePath))
            {
                return JsonSerializer.Deserialize<Stored>(File.ReadAllText(FilePath))?.Theme ?? ThemeChoice.System;
            }
        }
        catch (Exception ex) when (ex is IOException or JsonException)
        {
        }
        return ThemeChoice.System;
    }

    private static void Save()
    {
        try
        {
            Directory.CreateDirectory(SessionStore.Root);
            File.WriteAllText(FilePath, JsonSerializer.Serialize(new Stored(theme)));
        }
        catch (IOException)
        {
        }
    }
}
