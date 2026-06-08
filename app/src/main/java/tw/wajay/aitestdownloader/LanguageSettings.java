package tw.wajay.aitestdownloader;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import java.util.Locale;

final class LanguageSettings {
    private static final String PREFS = "language_settings";
    private static final String KEY_LANGUAGE = "language";
    static final String SYSTEM = "system";

    static final class Option {
        final String code;
        final int labelResId;

        Option(String code, int labelResId) {
            this.code = code;
            this.labelResId = labelResId;
        }
    }

    private static final Option[] OPTIONS = new Option[]{
            new Option(SYSTEM, R.string.language_system),
            new Option("en", R.string.language_english),
            new Option("zh-TW", R.string.language_traditional_chinese),
            new Option("zh-CN", R.string.language_simplified_chinese),
            new Option("ja", R.string.language_japanese)
    };

    private LanguageSettings() {
    }

    static Option[] options() {
        return OPTIONS;
    }

    static String current(Context context) {
        return prefs(context).getString(KEY_LANGUAGE, SYSTEM);
    }

    static void set(Context context, String languageCode) {
        String normalized = normalize(languageCode);
        prefs(context).edit().putString(KEY_LANGUAGE, normalized).apply();
    }

    static int selectedIndex(Context context) {
        String current = current(context);
        for (int i = 0; i < OPTIONS.length; i++) {
            if (OPTIONS[i].code.equals(current)) {
                return i;
            }
        }
        return 0;
    }

    static Context wrap(Context context) {
        String languageCode = current(context);
        if (SYSTEM.equals(languageCode)) {
            return context;
        }
        Locale locale = localeFor(languageCode);
        Locale.setDefault(locale);
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(locale);
        return context.createConfigurationContext(config);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String normalize(String languageCode) {
        if (languageCode == null) {
            return SYSTEM;
        }
        String value = languageCode.trim();
        for (Option option : OPTIONS) {
            if (option.code.equals(value)) {
                return value;
            }
        }
        return SYSTEM;
    }

    private static Locale localeFor(String languageCode) {
        if ("zh-TW".equals(languageCode)) {
            return Locale.TAIWAN;
        }
        if ("zh-CN".equals(languageCode)) {
            return Locale.SIMPLIFIED_CHINESE;
        }
        if ("ja".equals(languageCode)) {
            return Locale.JAPAN;
        }
        return Locale.US;
    }
}
