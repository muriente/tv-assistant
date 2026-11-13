package dev.mert.tvassistant;

import android.content.Context;
import org.json.JSONObject;

/** The Google AI Studio API key, encrypted with the same Android Keystore key as the OAuth tokens. */
final class GeminiKey {
  private final Vault vault;
  private volatile String cached;

  GeminiKey(Context c) {
    vault = new Vault(c.getApplicationContext(), "gemini_vault");
  }

  static boolean valid(String key) {
    return key != null && key.matches("[A-Za-z0-9_\\-]{20,200}");
  }

  synchronized String get() {
    if (cached != null) return cached;
    try {
      cached = vault.load().optString("api_key", "");
    } catch (Exception e) {
      cached = "";
    }
    return cached;
  }

  boolean present() {
    return !get().isEmpty();
  }

  synchronized void set(String key) throws Exception {
    if (!valid(key)) throw new IllegalArgumentException("That does not look like a Gemini API key");
    vault.save(new JSONObject().put("api_key", key));
    cached = key;
  }

  synchronized void clear() {
    vault.clear();
    cached = "";
  }
}
