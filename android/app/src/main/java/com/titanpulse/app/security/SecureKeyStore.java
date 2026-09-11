package com.titanpulse.app.security;

import android.content.Context;
import android.util.Base64;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Iterator;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Encrypted, account-scoped API key store backed by Android Keystore. */
public final class SecureKeyStore {
    private static final String KS = "AndroidKeyStore";
    private static final String ALIAS = "titan_pulse_provider_keys_v2";
    private static final String PREFS = "titan_secure_v2";
    private static final String DATA = "encrypted_provider_keys";
    private static final String ACCOUNTS = "accounts";
    private final Context context;

    private static final String DEVICE_OWNER = "device_owner";
    public SecureKeyStore(Context context) { this.context = context.getApplicationContext(); }

    public synchronized void bindDeviceOwner(String ownerId) { if (ownerId != null && !ownerId.isEmpty()) context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(DEVICE_OWNER, ownerId).apply(); }

    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance(KS);
        ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KS);
            kg.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(false)
                    .build());
            kg.generateKey();
        }
        return ((KeyStore.SecretKeyEntry) ks.getEntry(ALIAS, null)).getSecretKey();
    }

    public synchronized void put(String ownerId, String providerId, String value) throws Exception {
        if (ownerId == null || ownerId.isEmpty() || providerId == null || providerId.isEmpty()) return;
        JSONObject accounts = getAccounts();
        JSONObject account = accounts.optJSONObject(ownerId);
        if (account == null) account = new JSONObject();
        account.put(providerId, value == null ? "" : value);
        accounts.put(ownerId, account);
        saveAccounts(accounts);
    }

    public synchronized String get(String ownerId, String providerId) {
        try {
            JSONObject account = getAccountForOwner(getAccounts(), ownerId);
            return account == null ? "" : account.optString(providerId, "");
        } catch (Exception e) { return ""; }
    }

    public synchronized String getAllJsonMasked(String ownerId) {
        try {
            JSONObject account = getAccountForOwner(getAccounts(), ownerId);
            JSONObject out = new JSONObject();
            if (account == null) return out.toString();
            Iterator<String> it = account.keys();
            while (it.hasNext()) {
                String id = it.next();
                JSONArray keys = new JSONArray(account.optString(id, "[]"));
                JSONArray masks = new JSONArray();
                for (int i = 0; i < keys.length(); i++) {
                    String k = keys.optString(i, "");
                    masks.put(k.length() > 8 ? k.substring(0, 4) + "••••" + k.substring(k.length() - 4) : "••••••");
                }
                out.put(id, new JSONObject().put("count", keys.length()).put("masks", masks));
            }
            return out.toString();
        } catch (Exception e) { return "{}"; }
    }

    public synchronized void remove(String ownerId, String providerId) throws Exception {
        JSONObject root = readRoot();
        JSONObject accounts = getAccounts();
        JSONObject account = accounts.optJSONObject(ownerId);
        if (account != null) { account.remove(providerId); if (account.length() == 0) accounts.remove(ownerId); }
        saveAccounts(accounts);
    }

    public synchronized void removeOwner(String ownerId) throws Exception {
        JSONObject root = readRoot();
        JSONObject accounts = getAccounts();
        accounts.remove(ownerId);
        saveAccounts(accounts);
    }

    /** Move the legacy device-scoped secret bucket to an authenticated owner once. */
    public synchronized void adoptLegacyOwner(String newOwnerId) throws Exception {
        if (newOwnerId == null || newOwnerId.isEmpty()) return;
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(DATA, "");
        if (!raw.isEmpty()) return;
        JSONObject legacy = readLegacyV1();
        if (legacy == null || legacy.length() == 0) return;
        saveAccounts(new JSONObject().put(newOwnerId, legacy));
        context.getSharedPreferences("titan_secure_v1", Context.MODE_PRIVATE).edit().clear().apply();
    }

    /** Move secrets from one owner to another without exposing their plaintext outside the encrypted store. */
    public synchronized void migrateOwner(String fromOwnerId, String toOwnerId) throws Exception {
        if (fromOwnerId == null || fromOwnerId.isEmpty() || toOwnerId == null || toOwnerId.isEmpty() || fromOwnerId.equals(toOwnerId)) return;
        JSONObject accounts = getAccounts();
        JSONObject from = accounts.optJSONObject(fromOwnerId);
        if (from == null) return;
        JSONObject to = accounts.optJSONObject(toOwnerId);
        if (to == null) to = new JSONObject();
        Iterator<String> it = from.keys();
        while (it.hasNext()) {
            String provider = it.next();
            to.put(provider, from.opt(provider));
        }
        accounts.put(toOwnerId, to);
        accounts.remove(fromOwnerId);
        saveAccounts(accounts);
    }

    private JSONObject getAccounts() throws Exception {
        JSONObject root = readRoot();
        if (root.has(ACCOUNTS)) {
            JSONObject a = root.optJSONObject(ACCOUNTS);
            return a == null ? new JSONObject() : a;
        }
        JSONObject accounts = new JSONObject();
        String deviceOwner = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(DEVICE_OWNER, "");
        if (!deviceOwner.isEmpty() && root.length() > 0) accounts.put(deviceOwner, root);
        return accounts;
    }

    private JSONObject getAccountForOwner(JSONObject accounts, String ownerId) {
        if (accounts == null || ownerId == null || ownerId.isEmpty()) return null;
        return accounts.optJSONObject(ownerId);
    }

    private JSONObject readLegacyV1() {
        try {
            String raw = context.getSharedPreferences("titan_secure_v1", Context.MODE_PRIVATE).getString("encrypted_provider_keys", "");
            if (raw.isEmpty()) return null;
            String[] parts = raw.split("\\.", 3);
            if (parts.length != 2) return null;
            KeyStore ks = KeyStore.getInstance(KS);
            ks.load(null);
            if (!ks.containsAlias("titan_pulse_provider_keys_v1")) return null;
            SecretKey legacyKey = ((KeyStore.SecretKeyEntry) ks.getEntry("titan_pulse_provider_keys_v1", null)).getSecretKey();
            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] cipherText = Base64.decode(parts[1], Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, legacyKey, new GCMParameterSpec(128, iv));
            return new JSONObject(new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    private JSONObject readRoot() throws Exception {
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(DATA, "");
        if (raw.isEmpty()) {
            JSONObject legacy = readLegacyV1();
            return legacy == null ? new JSONObject() : legacy;
        }
        String[] parts = raw.split("\\.", 3);
        if (parts.length != 2) throw new IllegalStateException("Invalid secure store payload");
        byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
        byte[] cipherText = Base64.decode(parts[1], Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        return new JSONObject(new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8));
    }

    private void saveAccounts(JSONObject accounts) throws Exception { saveRoot(new JSONObject().put(ACCOUNTS, accounts)); }

    private void saveRoot(JSONObject value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] iv = cipher.getIV();
        byte[] enc = cipher.doFinal(value.toString().getBytes(StandardCharsets.UTF_8));
        String payload = Base64.encodeToString(iv, Base64.NO_WRAP) + "." + Base64.encodeToString(enc, Base64.NO_WRAP);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(DATA, payload).apply();
    }
}
