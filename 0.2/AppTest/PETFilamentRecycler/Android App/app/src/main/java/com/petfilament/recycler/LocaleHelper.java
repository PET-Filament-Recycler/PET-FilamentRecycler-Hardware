package com.petfilament.recycler;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.LocaleList;

import java.util.Locale;

public final class LocaleHelper {

    public static final String LANG_EN = "en";
    public static final String LANG_ZH = "zh";

    private static final String PREFS_NAME = "pet_app_prefs";
    private static final String KEY_LANGUAGE = "language";

    private LocaleHelper() {
    }

    public static Context wrap(Context context) {
        return updateResources(context, getLanguage(context));
    }

    public static String getLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LANGUAGE, LANG_EN);
    }

    public static void setLanguage(Context context, String language) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LANGUAGE, language)
                .apply();
    }

    public static String toggleLanguage(Context context) {
        String next = LANG_EN.equals(getLanguage(context)) ? LANG_ZH : LANG_EN;
        setLanguage(context, next);
        return next;
    }

    private static Context updateResources(Context context, String language) {
        Locale locale = LANG_ZH.equals(language)
                ? Locale.TRADITIONAL_CHINESE
                : Locale.ENGLISH;

        Locale.setDefault(locale);
        Resources resources = context.getResources();
        Configuration configuration = new Configuration(resources.getConfiguration());
        configuration.setLocales(new LocaleList(locale));
        return context.createConfigurationContext(configuration);
    }
}