package translation.translators;

import com.squareup.okhttp.*;
import extension.Language;
import org.json.JSONArray;
import org.json.JSONObject;
import translation.TranslationException;
import translation.Translator;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class DeepLTranslator extends Translator {

	// https://developers.deepl.com/docs/getting-started/intro
    private final String apiKey;

    public DeepLTranslator(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    @Override
    protected String translate(String text, Language source, Language target) throws TranslationException {
        return translate(singletonListSafe(text), source, target).get(0);
    }

    @Override
    protected List<String> translate(List<String> texts, Language source, Language target) throws TranslationException {
        if (apiKey.isEmpty()) {
            throw new TranslationException("no deepl api key set");
        }

        List<String> nonNullTexts = new ArrayList<>();
        for (String t : texts) {
            nonNullTexts.add(t == null ? "" : t);
        }

        String sourceCode = mapLanguage(source);
        String targetCode = mapLanguage(target);

    if (targetCode == null) throw new TranslationException("target lang not supported by deepl");
        boolean includeSource = sourceCode != null;

        String[] hosts = new String[] {"api-free.deepl.com", "api.deepl.com"};
        TranslationException lastEx = null;
        for (String host : hosts) {
            try {
                return doRequest(host, nonNullTexts, includeSource ? sourceCode : null, targetCode);
            } catch (TranslationException e) {
                lastEx = e;
                String msg = e.getReason().toLowerCase();
                if (!(msg.contains("auth") || msg.contains("quota") || msg.contains("403") || msg.contains("456"))) {
                    break;
                }
            }
        }
    throw lastEx != null ? lastEx : new TranslationException("deepl died for some mystery reason");
    }

    private List<String> doRequest(String host, List<String> texts, String source, String target) throws TranslationException {
        OkHttpClient client = new OkHttpClient();

        StringBuilder form = new StringBuilder();
        append(form, "auth_key", apiKey);
        for (String t : texts) {
            append(form, "text", t);
        }
        append(form, "target_lang", target);
        if (source != null) append(form, "source_lang", source);
        append(form, "preserve_formatting", "1");

        Request request = new Request.Builder()
                .url("https://" + host + "/v2/translate")
                .post(RequestBody.create(MediaType.parse("application/x-www-form-urlencoded"), form.toString()))
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build();

        Response response;
        try {
            response = client.newCall(request).execute();
        } catch (IOException e) {
            throw new TranslationException("net went weird");
        }

        if (response.code() != 200) {
            try {
                String errBody = response.body() != null ? response.body().string() : "";
                throw new TranslationException("deepl said nope: " + response.code() + " - " + errBody);
            } catch (IOException ignore) {
                throw new TranslationException("deepl said nope: " + response.code());
            }
        }

        try {
            String bodyStr = response.body().string();
            JSONObject json = new JSONObject(bodyStr);
            JSONArray arr = json.getJSONArray("translations");
            if (arr.length() != texts.size()) {
                List<String> fallback = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    fallback.add(arr.getJSONObject(i).getString("text"));
                }
                return fallback;
            }
            List<String> result = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                result.add(arr.getJSONObject(i).getString("text"));
            }
            return result;
        } catch (Exception e) {
            throw new TranslationException("couldn't make sense of deepl reply");
        }
    }

    private void append(StringBuilder sb, String key, String value) throws TranslationException {
        if (sb.length() > 0) sb.append('&');
        try {
            sb.append(URLEncoder.encode(key, "UTF-8"))
              .append('=')
              .append(URLEncoder.encode(value, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new TranslationException("encoding went kinda wrong");
        }
    }

    private List<String> singletonListSafe(String s) {
        List<String> list = new ArrayList<>();
        list.add(s == null ? "" : s);
        return list;
    }

    private String mapLanguage(Language lang) {
        if (lang == null) return null;
        switch (lang) {
            case ENGLISH: return "EN";
            case FRENCH: return "FR";
            case GERMAN: return "DE";
            case ITALIAN: return "IT";
            case PORTUGUESE: return "PT";
            case SPANISH: return "ES";
            case TURKISH: return "TR";
            case FINNISH: return "FI";
            case DUTCH: return "NL";
            default: return null;
        }
    }

    @Override
    public boolean allowMultiLines() {
        return true;
    }
}
