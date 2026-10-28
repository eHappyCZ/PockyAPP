using Microsoft.UI.Xaml;
using uniffi.pocky_ffi;

namespace PockyAPP;

public sealed partial class MainWindow : Window
{
    public MainWindow()
    {
        InitializeComponent();
        Title = "PockyAPP";
        VersionText.Text = $"{PockyFfiMethods.DefaultServer()} · core {PockyFfiMethods.CoreVersion()}";
    }
}
