using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;

namespace PockyAPP;

public sealed partial class ThemeButton : UserControl
{
    public ThemeButton()
    {
        InitializeComponent();
        SystemItem.Text = Loc.T("Podle systému", "Use system setting");
        LightItem.Text = Loc.T("Světlý vzhled", "Light");
        DarkItem.Text = Loc.T("Tmavý vzhled", "Dark");
        ToolTipService.SetToolTip(Trigger, Loc.T("Vzhled", "Appearance"));
    }

    private void OnOpening(object sender, object e)
    {
        SystemItem.IsChecked = AppSettings.Theme == ThemeChoice.System;
        LightItem.IsChecked = AppSettings.Theme == ThemeChoice.Light;
        DarkItem.IsChecked = AppSettings.Theme == ThemeChoice.Dark;
    }

    private void OnSystem(object sender, RoutedEventArgs e) => AppSettings.Theme = ThemeChoice.System;

    private void OnLight(object sender, RoutedEventArgs e) => AppSettings.Theme = ThemeChoice.Light;

    private void OnDark(object sender, RoutedEventArgs e) => AppSettings.Theme = ThemeChoice.Dark;
}
