package dev.mert.tvassistant;

import java.io.*;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.json.*;

/** Minimal Gemini API (Google AI Studio key) client: generateContent with function calling. */
final class Gemini {
  static final String DEFAULT_MODEL = "gemini-3.8-flash";
  static final String FALLBACK_MODEL = "gemini-3.5-flash-lite";
  private static final String BASE = "https://generativelanguage.googleapis.com/v1beta/";
  private static volatile HttpURLConnection active;

  static final class HttpError extends IOException {
    final int status;

    HttpError(int status, String message) {
      super(message);
      this.status = status;
    }
  }

  static void cancel() {
    HttpURLConnection c = active;
    if (c != null) c.disconnect();
  }

  private static String call(String key, String path, JSONObject body) throws Exception {
    HttpURLConnection c = Net.open(BASE + path, null);
    active = c;
    try {
      c.setReadTimeout(90000);
      c.setRequestProperty("x-goog-api-key", key);
      if (body != null) {
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        try (OutputStream out = c.getOutputStream()) {
          out.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
      }
      int status = c.getResponseCode();
      String text = Net.read(status < 400 ? c.getInputStream() : c.getErrorStream(), 4_000_000);
      if (status != 200) {
        String message = text;
        try {
          message = new JSONObject(text).getJSONObject("error").optString("message", text);
        } catch (Exception ignored) {
        }
        throw new HttpError(status, "Gemini HTTP " + status + " · " + Json.clip(message, 600));
      }
      return text;
    } finally {
      active = null;
      c.disconnect();
    }
  }

  static JSONObject generate(String key, String model, JSONObject body) throws Exception {
    if (!model.matches("[a-z0-9.\\-]{3,80}")) throw new IllegalArgumentException("Invalid Gemini model");
    return new JSONObject(call(key, "models/" + model + ":generateContent", body));
  }

  /** Should a failed request be retried once on the fallback model? */
  static boolean fallbackWorthy(Exception e) {
    if (!(e instanceof HttpError)) return e instanceof java.net.SocketTimeoutException;
    int s = ((HttpError) e).status;
    return s == 404 || s == 429 || s == 500 || s == 503 || s == 504;
  }

  /** Converts the app's Responses-style function definitions to Gemini function declarations. */
  static JSONArray declarations(JSONArray definitions) throws Exception {
    JSONArray out = new JSONArray();
    for (int i = 0; i < definitions.length(); i++) {
      JSONObject d = definitions.getJSONObject(i);
      out.put(
          Json.obj(
              "name", d.getString("name"),
              "description", d.optString("description"),
              "parametersJsonSchema", d.getJSONObject("parameters")));
    }
    return out;
  }

  /** Text-capable Gemini models available to this key, newest names first as the API lists them. */
  static JSONArray models(String key) throws Exception {
    JSONObject result = new JSONObject(call(key, "models?pageSize=200", null));
    JSONArray models = result.optJSONArray("models"), out = new JSONArray();
    if (models == null) return out;
    for (int i = 0; i < models.length(); i++) {
      JSONObject m = models.getJSONObject(i);
      String slug = m.optString("name").replaceFirst("^models/", "");
      String lower = slug.toLowerCase(Locale.ROOT);
      JSONArray methods = m.optJSONArray("supportedGenerationMethods");
      if (!lower.startsWith("gemini") || methods == null || !methods.toString().contains("\"generateContent\"")) continue;
      if (lower.contains("tts") || lower.contains("image") || lower.contains("live") || lower.contains("embedding")) continue;
      out.put(Json.obj("slug", slug, "display_name", m.optString("displayName", slug)));
    }
    return out;
  }
}
