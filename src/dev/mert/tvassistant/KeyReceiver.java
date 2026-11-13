package dev.mert.tvassistant;

import android.content.*;

/**
 * Provisions the Gemini API key over ADB so nobody has to type it on the TV. The manifest protects
 * this receiver with android.permission.DUMP, which the ADB shell holds and ordinary apps cannot get.
 * The key is never logged or echoed back.
 */
public final class KeyReceiver extends BroadcastReceiver {
  static final String SET = "dev.mert.tvassistant.SET_GEMINI_KEY";
  static final String CLEAR = "dev.mert.tvassistant.CLEAR_GEMINI_KEY";

  @Override
  public void onReceive(Context c, Intent intent) {
    try {
      GeminiKey key = new GeminiKey(c);
      SharedPreferences prefs = c.getSharedPreferences("assistant", Context.MODE_PRIVATE);
      if (CLEAR.equals(intent.getAction())) {
        key.clear();
        setResult(1, "Gemini key removed", null);
        return;
      }
      if (!SET.equals(intent.getAction())) return;
      key.set(intent.getStringExtra("key"));
      String model = intent.getStringExtra("model");
      if (model != null && model.matches("[a-z0-9.\\-]{3,80}")) prefs.edit().putString("gemini_model", model).apply();
      setResult(1, "Gemini key stored (" + key.get().length() + " chars, encrypted)", null);
    } catch (Exception e) {
      setResult(0, "Gemini key not stored: " + e.getClass().getSimpleName() + " " + ChatAuth.safe(e), null);
    }
  }
}
