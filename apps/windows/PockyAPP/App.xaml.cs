using Microsoft.UI.Xaml;

namespace PockyAPP;

public partial class App : Application
{
    private Window? window;

    internal static AppModel Model { get; } = new();

    public App()
    {
        InitializeComponent();
    }

    protected override void OnLaunched(LaunchActivatedEventArgs args)
    {
        window = new MainWindow();
        window.Activate();
    }
}
