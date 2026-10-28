using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;

namespace PockyAPP;

public sealed partial class LoginPage : Page
{
    private readonly AppModel model = App.Model;
    private bool busy;

    public LoginPage()
    {
        InitializeComponent();
        Subtitle.Text = Loc.T("Přihlas se do komunity Pokecio.", "Sign in to the Pokecio community.");
        UsernameLabel.Text = Loc.T("Uživatelské jméno", "Username");
        PasswordLabel.Text = Loc.T("Heslo", "Password");
        Username.PlaceholderText = Loc.T("jmeno", "username");
        ServerExpander.Header = Loc.T("Server", "Server");
        Server.Text = model.DefaultServer;
        SignInButton.Content = Loc.T("Přihlásit se", "Sign in");
        RegisterButton.Content = Loc.T("Nemáš účet? Vytvořit účet", "No account? Create one");
    }

    private bool CanSubmit => !busy
        && Username.Text.Trim().Length > 0
        && Password.Password.Length > 0
        && Server.Text.Trim().Length > 0;

    private void Refresh()
    {
        SignInButton.IsEnabled = CanSubmit;
        Username.IsEnabled = Password.IsEnabled = Server.IsEnabled = RegisterButton.IsEnabled = !busy;
        Busy.IsActive = busy;
        Busy.Visibility = busy ? Visibility.Visible : Visibility.Collapsed;
    }

    private void OnFieldChanged(object sender, object e) => Refresh();

    private void OnKeyDown(object sender, KeyRoutedEventArgs e)
    {
        if (e.Key == Windows.System.VirtualKey.Enter && CanSubmit)
        {
            OnSignIn(sender, e);
        }
    }

    private async void OnSignIn(object sender, RoutedEventArgs e)
    {
        if (!CanSubmit)
        {
            return;
        }
        busy = true;
        ErrorText.Visibility = Visibility.Collapsed;
        Refresh();
        try
        {
            await model.SignIn(Username.Text, Password.Password, Server.Text);
        }
        catch (Exception error)
        {
            ErrorText.Text = AppModel.ErrorText(error);
            ErrorText.Visibility = Visibility.Visible;
            busy = false;
            Refresh();
        }
    }

    private void OnRegister(object sender, RoutedEventArgs e) => Frame.Navigate(typeof(RegisterPage));
}
