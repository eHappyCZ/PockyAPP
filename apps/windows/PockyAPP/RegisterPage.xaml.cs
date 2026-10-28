using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;

namespace PockyAPP;

public sealed partial class RegisterPage : Page
{
    private const int MinPassword = 8;

    private readonly AppModel model = App.Model;
    private bool busy;

    public RegisterPage()
    {
        InitializeComponent();
        Title.Text = Loc.T("Vytvořit účet", "Create account");
        Subtitle.Text = Loc.T(
            $"Registrace na serveru {model.DefaultServer} vyžaduje pozvánkový kód.",
            $"Signing up on {model.DefaultServer} needs an invitation code.");
        UsernameLabel.Text = Loc.T("Uživatelské jméno", "Username");
        PasswordLabel.Text = Loc.T("Heslo", "Password");
        PasswordHint.Text = Loc.T($"Alespoň {MinPassword} znaků.", $"At least {MinPassword} characters.");
        InviteLabel.Text = Loc.T("Pozvánkový kód", "Invitation code");
        SubmitButton.Content = Loc.T("Vytvořit účet", "Create account");
    }

    private bool CanSubmit => !busy
        && Username.Text.Trim().Length > 0
        && Password.Password.Length >= MinPassword
        && Invite.Text.Trim().Length > 0;

    private void Refresh()
    {
        SubmitButton.IsEnabled = CanSubmit;
        Username.IsEnabled = Password.IsEnabled = Invite.IsEnabled = BackButton.IsEnabled = !busy;
        Busy.IsActive = busy;
        Busy.Visibility = busy ? Visibility.Visible : Visibility.Collapsed;
    }

    private void OnFieldChanged(object sender, object e)
    {
        if (ReferenceEquals(sender, Username))
        {
            var cleaned = string.Concat(Username.Text.Where(c => !char.IsWhiteSpace(c))).TrimStart('@');
            var colon = cleaned.IndexOf(':');
            if (colon >= 0)
            {
                cleaned = cleaned[..colon];
            }
            if (cleaned != Username.Text)
            {
                Username.Text = cleaned;
                Username.SelectionStart = cleaned.Length;
            }
        }
        Refresh();
    }

    private void OnKeyDown(object sender, KeyRoutedEventArgs e)
    {
        if (e.Key == Windows.System.VirtualKey.Enter && CanSubmit)
        {
            OnSubmit(sender, e);
        }
    }

    private void OnBack(object sender, RoutedEventArgs e) => Frame.GoBack();

    private async void OnSubmit(object sender, RoutedEventArgs e)
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
            await model.Register(Username.Text, Password.Password, Invite.Text);
        }
        catch (Exception error)
        {
            ErrorText.Text = AppModel.ErrorText(error);
            ErrorText.Visibility = Visibility.Visible;
            busy = false;
            Refresh();
        }
    }
}
