package co.pushe.plus.utils.keyval;

import java.util.Map;

public interface KVStorage {

    boolean contains(String key);

    String getString(String key, String defVal);
    int getInt(String key, int defVal);
    float getFloat(String key, float defVal);
    boolean getBoolean(String key, boolean defVal);
    String[] getStringArray(String key);

    void saveString(String key, String value);
    void saveInt(String key, int value);
    void saveFloat(String key, float value);
    void saveBoolean(String key, boolean value);
    void saveStringArray(String key, String[] value);

    void remove(String key);

    Map<String, ?> getAll();

    KVStorageEditor edit();
}
