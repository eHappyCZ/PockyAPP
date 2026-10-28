using Microsoft.UI;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Media.Animation;
using Windows.UI;

namespace PockyAPP;

public sealed partial class MainWindow : Window
{
    private readonly AppModel model = App.Model;

    public MainWindow()
    {
        InitializeComponent();
        Title = "PockyAPP";
        ExtendsContentIntoTitleBar = true;
        SetTitleBar(TitleBar);

        AppSettings.Changed += ApplyTheme;
        Root.ActualThemeChanged += (_, _) => PaintCaption();
        ApplyTheme();

        model.Changed += () => DispatcherQueue.TryEnqueue(Navigate);
        Closed += (_, _) => model.SaveSession();
        Navigate();
        _ = model.Resume();
    }

    private void ApplyTheme()
    {
        Root.RequestedTheme = AppSettings.ElementTheme;
        PaintCaption();
    }

    private void PaintCaption()
    {
        var dark = Root.ActualTheme == ElementTheme.Dark;
        var buttons = AppWindow.TitleBar;
        buttons.ButtonBackgroundColor = Colors.Transparent;
        buttons.ButtonInactiveBackgroundColor = Colors.Transparent;
        buttons.ButtonForegroundColor = dark ? Color.FromArgb(255, 244, 242, 249) : Color.FromArgb(255, 23, 20, 31);
        buttons.ButtonInactiveForegroundColor = Color.FromArgb(255, 164, 160, 179);
        buttons.ButtonHoverBackgroundColor = dark ? Color.FromArgb(40, 255, 255, 255) : Color.FromArgb(30, 0, 0, 0);
        buttons.ButtonHoverForegroundColor = buttons.ButtonForegroundColor;
    }

    private void Navigate()
    {
        Spinner.Visibility = model.State == AppState.Loading ? Visibility.Visible : Visibility.Collapsed;
        var current = Host.CurrentSourcePageType;
        switch (model.State)
        {
            case AppState.SignedOut when current != typeof(LoginPage) && current != typeof(RegisterPage):
                Host.Navigate(typeof(LoginPage), null, new SuppressNavigationTransitionInfo());
                Host.BackStack.Clear();
                break;
            case AppState.SignedIn when current != typeof(ChatsPage):
                Host.Navigate(typeof(ChatsPage), null, new SuppressNavigationTransitionInfo());
                Host.BackStack.Clear();
                break;
        }
    }
}
