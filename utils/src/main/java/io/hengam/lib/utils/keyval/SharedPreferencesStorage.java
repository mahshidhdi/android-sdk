package io.hengam.lib.utils.keyval;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SharedPreferencesStorage implements KVStorage {
    private SharedPreferences sharedPreferences;

    public SharedPreferencesStorage(Context androidContext, String storeName) {
        this(androidContext
                .getSharedPreferences(storeName, Context.MODE_PRIVATE));
    }

    public SharedPreferencesStorage(SharedPreferences sharedPreferences) {
        this.sharedPreferences = sharedPreferences;
    }

    @Override
    public boolean contains(String key) {
        return sharedPreferences.contains(key);
    }

    @Override
    public String getString(String key, String defVal) {
        return sharedPreferences.getString(key, defVal);
    }

    @Override
    public int getInt(String key, int defVal) {
        return sharedPreferences.getInt(key, defVal);
    }

    @Override
    public float getFloat(String key, float defVal) {
        return sharedPreferences.getFloat(key, defVal);
    }

    @Override
    public boolean getBoolean(String key, boolean defVal) {
        return sharedPreferences.getBoolean(key, defVal);
    }

    @Override
    public String[] getStringArray(String key) {
        String valuesString = sharedPreferences.getString(key, "");
        String[] values = valuesString.split(",");
        List<String> valueList = new ArrayList<>();
        for (String value : values) {
            String cleanedValue = value.trim();
            if (!value.isEmpty()) {
                valueList.add(cleanedValue);
            }
        }
        return valueList.toArray(new String[0]);
    }

    @Override
    public void saveString(String key, String value) {
        edit().putString(key, value).apply();
    }

    @Override
    public void saveInt(String key, int value) {
        edit().putInt(key, value).apply();
    }

    @Override
    public void saveFloat(String key, float value) {
        edit().putFloat(key, value).apply();
    }

    @Override
    public void saveBoolean(String key, boolean value) {
        edit().putBoolean(key, value).apply();
    }

    @Override
    public void saveStringArray(String key, String[] value) {
        edit().putStringArray(key, value).apply();
    }

    @Override
    public void remove(String key) {
        edit().remove(key).apply();
    }

    @Override
    public Map<String, ?> getAll() {
        return sharedPreferences.getAll();
    }

    @Override
    public Editor edit() {
        return new Editor(sharedPreferences.edit());
    }

    public static class Editor implements KVStorageEditor {
        private SharedPreferences.Editor editor;
        private Editor(SharedPreferences.Editor editor) {
            this.editor = editor;
        }

        @Override
        public Editor putString(String key, String value) {
            editor.putString(key, value);
            return this;
        }

        @Override
        public Editor putInt(String key, int value) {
            editor.putInt(key, value);
            return this;
        }

        @Override
        public Editor putFloat(String key, float value) {
            editor.putFloat(key, value);
            return this;
        }

        @Override
        public Editor putBoolean(String key, boolean value) {
            editor.putBoolean(key, value);
            return this;
        }

        @Override
        public Editor putStringArray(String key, String[] array) {
            if (array.length == 0) {
                editor.putString(key, "");
                return this;
            }

            StringBuilder builder = new StringBuilder();
            builder.append(array[0]);
            for (int i = 1; i < array.length; i++) {
                builder.append(',').append(array[i]);
            }
            editor.putString(key, builder.toString());
            return this;
        }

        @Override
        public Editor remove(String key) {
            editor.remove(key);
            return this;
        }

        @Override
        public void apply() {
            editor.apply();
        }

        @Override
        public void commit() {
            editor.commit();
        }
    }
}
