package com.example.communitymember20;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;

public class LocaleHelper {

    // In-memory cache: key = "langCode|originalText", value = translatedText
    // Prevents repeated API calls for the same string across the app session.
    private static final HashMap<String, String> translationCache = new HashMap<>();

    public static String getLanguageCode(String displayLanguage) {
        if (displayLanguage == null) return "en";
        switch (displayLanguage) {
            case "Setswana":  return "tn";
            case "isiZulu":   return "zu";
            case "Afrikaans": return "af";
            default:          return "en";
        }
    }

    // ---------------------------------------------------------------
    // NETWORK CHECK
    // ---------------------------------------------------------------
    private static boolean isNetworkAvailable(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return caps != null && (
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------------------------------------------------------
    // VIEW TRANSLATION
    // ---------------------------------------------------------------
    public static void translateAllViews(Context context, View root) {
        SharedPreferences prefs = context.getSharedPreferences("AquacommPrefs", Context.MODE_PRIVATE);
        String selectedLang = prefs.getString("preferred_lang", "English");

        if ("English".equals(selectedLang)) return;

        if (root instanceof Spinner) {
            translateSpinnerAdapter(context, (Spinner) root);
        } else if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                translateAllViews(context, group.getChildAt(i));
            }
        } else if (root instanceof EditText) {
            EditText editText = (EditText) root;
            CharSequence currentHint = editText.getHint();
            if (currentHint != null && currentHint.length() > 0) {
                String originalHint = editText.getTag() != null
                        ? editText.getTag().toString() : currentHint.toString();
                if (editText.getTag() == null) editText.setTag(originalHint);
                translateHintApi(context, editText, originalHint);
            }
        } else if (root instanceof TextView) {
            TextView tv = (TextView) root;
            CharSequence currentText = tv.getText();
            if (currentText != null && currentText.length() > 0) {
                String originalText = tv.getTag() != null
                        ? tv.getTag().toString() : currentText.toString();
                if (tv.getTag() == null) tv.setTag(originalText);
                translateViewApi(context, tv, originalText);
            }
        }
    }

    private static void translateSpinnerAdapter(Context context, Spinner spinner) {
        if (spinner.getAdapter() == null || spinner.getAdapter().getCount() == 0) {
            spinner.getViewTreeObserver().addOnGlobalLayoutListener(
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            if (spinner.getAdapter() != null && spinner.getAdapter().getCount() > 0) {
                                spinner.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                                translateSpinnerAdapter(context, spinner);
                            }
                        }
                    });
            return;
        }

        if (spinner.getAdapter() instanceof ArrayAdapter) {
            @SuppressWarnings("unchecked")
            ArrayAdapter<String> adapter = (ArrayAdapter<String>) spinner.getAdapter();
            int count = adapter.getCount();
            if (count == 0) return;

            String[] originalItems;
            if (spinner.getTag() instanceof String[]) {
                originalItems = (String[]) spinner.getTag();
            } else {
                originalItems = new String[count];
                for (int i = 0; i < count; i++) originalItems[i] = adapter.getItem(i);
                spinner.setTag(originalItems);
            }

            int currentSelection = spinner.getSelectedItemPosition();
            String[] translatedItems = new String[count];
            translateNextSpinnerItem(context, originalItems, translatedItems, 0, currentSelection, spinner);
        }
    }

    private static void translateNextSpinnerItem(Context context, String[] originalItems,
                                                 String[] translatedItems, int index,
                                                 int currentSelection, Spinner spinner) {
        if (index >= originalItems.length) {
            new Handler(Looper.getMainLooper()).post(() -> {
                ArrayAdapter<String> newAdapter = new ArrayAdapter<>(context,
                        android.R.layout.simple_spinner_dropdown_item, translatedItems);
                newAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinner.setAdapter(newAdapter);
                spinner.setSelection(Math.min(currentSelection, translatedItems.length - 1));
            });
            return;
        }

        translateStringApi(context, originalItems[index], translated -> {
            translatedItems[index] = (translated != null && !translated.isEmpty())
                    ? translated : originalItems[index];
            translateNextSpinnerItem(context, originalItems, translatedItems,
                    index + 1, currentSelection, spinner);
        });
    }

    public static void translateViewApi(Context context, TextView view, String textToTranslate) {
        translateStringApi(context, textToTranslate, view::setText);
    }

    public static void translateHintApi(Context context, EditText editText, String textToTranslate) {
        translateStringApi(context, textToTranslate, editText::setHint);
    }

    // ---------------------------------------------------------------
    // TOASTS
    // ---------------------------------------------------------------
    public static void showTranslatedToast(Context context, String message, int duration) {
        if (context == null || message == null) return;
        final Context appContext = context.getApplicationContext();
        translateStringApi(context, message, translatedText ->
                Toast.makeText(appContext, translatedText, duration).show());
    }

    public interface TranslationCallback {
        void onTranslated(String translatedText);
    }

    // ---------------------------------------------------------------
    // CORE TRANSLATION (with cache + timeout + offline fallback)
    // ---------------------------------------------------------------
    public static void translateStringApi(Context context, final String textToTranslate,
                                          TranslationCallback callback) {
        SharedPreferences prefs = context.getSharedPreferences("AquacommPrefs", Context.MODE_PRIVATE);
        String selectedLang = prefs.getString("preferred_lang", "English");

        // English or empty — return as-is
        if ("English".equals(selectedLang) || textToTranslate == null || textToTranslate.isEmpty()) {
            callback.onTranslated(textToTranslate);
            return;
        }

        // Cache hit — instant, no network call
        String cacheKey = selectedLang + "|" + textToTranslate;
        if (translationCache.containsKey(cacheKey)) {
            callback.onTranslated(translationCache.get(cacheKey));
            return;
        }

        // Offline — return English (no crash, no hang)
        if (!isNetworkAvailable(context)) {
            callback.onTranslated(textToTranslate);
            return;
        }

        final String targetCode = getLanguageCode(selectedLang);

        new Thread(() -> {
            try {
                String encodedText = URLEncoder.encode(textToTranslate, "UTF-8");
                String urlStr = "https://api.mymemory.translated.net/get?q="
                        + encodedText + "&langpair=en|" + targetCode;

                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                // Timeouts prevent the app from hanging on a slow API
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();

                JSONObject jsonResponse = new JSONObject(response.toString());
                String translatedText = jsonResponse.getJSONObject("responseData")
                        .getString("translatedText");

                final String result = (translatedText != null && !translatedText.isEmpty()
                        && !translatedText.startsWith("MYMEMORY WARNING"))
                        ? translatedText : textToTranslate;

                // Store in cache for future calls
                synchronized (translationCache) {
                    translationCache.put(cacheKey, result);
                }

                new Handler(Looper.getMainLooper()).post(() -> callback.onTranslated(result));

            } catch (Exception e) {
                // On any failure (timeout, JSON error, etc.), return original English
                new Handler(Looper.getMainLooper()).post(() -> callback.onTranslated(textToTranslate));
            }
        }).start();
    }
}