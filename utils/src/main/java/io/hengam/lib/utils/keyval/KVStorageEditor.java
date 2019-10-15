package io.hengam.lib.utils.keyval;

public interface KVStorageEditor {
    KVStorageEditor putString(String key, String value);
    KVStorageEditor putInt(String key, int value);
    KVStorageEditor putFloat(String key, float value);
    KVStorageEditor putBoolean(String key, boolean value);
    KVStorageEditor putStringArray(String key, String[] value);

    KVStorageEditor remove(String key);

    void apply();
    void commit();
}
