using System.Globalization;

namespace PockyAPP;

internal static class Loc
{
    private static readonly bool Czech =
        CultureInfo.CurrentUICulture.TwoLetterISOLanguageName is "cs" or "sk";

    public static string T(string czech, string english) => Czech ? czech : english;
}
