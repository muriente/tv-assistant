package dev.mert.tvassistant;

import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.*;

final class Vault {
  private final SharedPreferences prefs;

  Vault(Context c) {
    this(c, "account_vault");
  }

  /** A separate encrypted store that shares the same Android Keystore key. */
  Vault(Context c, String name) {
    prefs = c.getSharedPreferences(name, Context.MODE_PRIVATE);
  }

  synchronized void clear() {
    prefs.edit().remove("encrypted").commit();
  }

  private SecretKey key() throws Exception {
    KeyStore s = KeyStore.getInstance("AndroidKeyStore");
    s.load(null);
    String alias = "TVAssistantAccounts";
    if (s.containsAlias(alias)) return (SecretKey) s.getKey(alias, null);
    KeyGenerator g = KeyGenerator.getInstance("AES", "AndroidKeyStore");
    g.init(
        new KeyGenParameterSpec.Builder(
                alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build());
    return g.generateKey();
  }

  synchronized JSONObject load() throws Exception {
    String value = prefs.getString("encrypted", null);
    if (value == null) return Json.obj("profiles", new JSONArray());
    JSONObject saved = new JSONObject(value);
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(
        Cipher.DECRYPT_MODE,
        key(),
        new GCMParameterSpec(128, Base64.decode(saved.getString("iv"), Base64.NO_WRAP)));
    return new JSONObject(
        new String(
            cipher.doFinal(Base64.decode(saved.getString("data"), Base64.NO_WRAP)), "UTF-8"));
  }

  synchronized void save(JSONObject value) throws Exception {
    Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
    c.init(Cipher.ENCRYPT_MODE, key());
    JSONObject saved =
        Json.obj(
            "iv",
            Base64.encodeToString(c.getIV(), Base64.NO_WRAP),
            "data",
            Base64.encodeToString(c.doFinal(value.toString().getBytes("UTF-8")), Base64.NO_WRAP));
    if (!prefs.edit().putString("encrypted", saved.toString()).commit())
      throw new java.io.IOException("Could not save account");
  }
}
