package io.hengam.lib.utils.keyval;

import java.util.Map;

/**
 * A key-value storage that allows multiple levels of storage for managing data priority.
 *
 * When something is saved in the MultiLevelKVStorage using one of the put methods, a positive integer
 * value is stored along side the stored data which specifies the priority level of the stored data. The
 * stored data can only be overridden by another write which has an equal or higher priority value than
 * what is stored.
 *
 * Note, only positive integers are accepted as levels. Using negative values could produce unpredictable
 * results.
 *
 * Also note, the level priorities are only applied when writing values. Removing a value from the storage
 * will always remove the value regardless of what level it is stored with.
 *
 * <code>
 *     MultiLevelKVStorage storage; // ... initialization
 *
 *     KVStorageEditor editor = storage.edit(0);  // Create editor with write level=0
 *     editor.putString("key", "value");  // Key "key" did not previously exist so write is allowed
 *     editor.save();
 *
 *     editor = storage.edit(2);  // Create editor with write level=2
 *     editor.putString("key", "new value");  // Key "key" does exist but the editors write level is
 *                                            // higher than the level "key" was previously saved with
 *                                            // so it is allowed and the value for "key" is overridden.
 *     editor.save();
 *
 *
 *     editor = storage.edit(1);  // Create editor with write level=1
 *     editor.putString("key", "other value");  // The editors level (1) is lower than the level which
 *                                              // the key was previously saved with (2) so the new value
 *                                              // will not be saved.
 *     editor.save();
 *
 * </code>
 */
public class MultiLevelKVStorage implements KVStorage {

    private KVStorage mainStorage;
    private KVStorage levelStorage;
    private int defaultLevel;

    /**
     * The MultiLevelKVStorage itself uses two {@link KVStorage} instances for storing data. One instance
     * is used to store the actual key-value data and the other instance is used to store the levels with
     * which the keys are saved with.
     *
     * @param mainStorage The {@link KVStorage} used to store the actual data
     * @param levelStorage The {@link KVStorage} used to store the data levels
     * @param defaultLevel The level to store data with if no level was provided by the caller
     */
    public MultiLevelKVStorage(KVStorage mainStorage, KVStorage levelStorage, int defaultLevel) {
        this.mainStorage = mainStorage;
        this.levelStorage = levelStorage;
        this.defaultLevel = defaultLevel;
    }

    @Override
    public boolean contains(String key) {
        return mainStorage.contains(key);
    }

    @Override
    public String getString(String key, String defVal) {
        return mainStorage.getString(key, defVal);
    }

    @Override
    public int getInt(String key, int defVal) {
        return mainStorage.getInt(key, defVal);
    }

    @Override
    public float getFloat(String key, float defVal) {
        return mainStorage.getFloat(key, defVal);
    }

    @Override
    public boolean getBoolean(String key, boolean defVal) {
        return mainStorage.getBoolean(key, defVal);
    }

    @Override
    public String[] getStringArray(String key) {
        return mainStorage.getStringArray(key);
    }

    public void saveString(String key, String value, int level) {
        edit(level).putString(key, value).apply();
    }

    @Override
    public void saveString(String key, String value) {
        saveString(key, value, defaultLevel);
    }

    public void saveInt(String key, int value, int level) {
        edit(level).putInt(key, value);
    }

    @Override
    public void saveInt(String key, int value) {
        saveInt(key, value, defaultLevel);
    }


    public void saveFloat(String key, float value, int level) {
        edit(level).putFloat(key, value);
    }

    @Override
    public void saveFloat(String key, float value) {
        saveFloat(key, value, defaultLevel);
    }

    public void saveBoolean(String key, boolean value, int level) {
        edit(level).putBoolean(key, value);
    }

    @Override
    public void saveBoolean(String key, boolean value) {
        saveBoolean(key, value, defaultLevel);
    }

    public void saveStringArray(String key, String[] value, int level) {
        edit(level).putStringArray(key, value);
    }

    @Override
    public void saveStringArray(String key, String[] value) {
        saveStringArray(key, value, defaultLevel);
    }

    @Override
    public void remove(String key) {
        mainStorage.remove(key);
        levelStorage.remove(key);
    }

    @Override
    public Map<String, ?> getAll() {
        return mainStorage.getAll();
    }

    public KVStorageEditor edit(int level) {
        return new Editor(level);
    }

    @Override
    public KVStorageEditor edit() {
        return new Editor(defaultLevel);
    }

    public class Editor implements KVStorageEditor {
        private int level;
        private KVStorageEditor mainEditor;
        private KVStorageEditor levelEditor;

        public Editor(int level) {
            this.level = level;
        }

        private KVStorageEditor getMainEditor() {
            if (mainEditor == null) {
                mainEditor = mainStorage.edit();
            }
            return mainEditor;
        }

        private KVStorageEditor getLevelEditor() {
            if (levelEditor == null) {
                levelEditor = levelStorage.edit();
            }
            return levelEditor;
        }

        @Override
        public KVStorageEditor putString(String key, String value) {
            int storedLevel = levelStorage.getInt(key, 0);
            if (level >= storedLevel) {
                getMainEditor().putString(key, value);
                if (level != storedLevel) {
                    getLevelEditor().putInt(key, level);
                }
            }
            return this;
        }

        @Override
        public KVStorageEditor putInt(String key, int value) {
            int storedLevel = levelStorage.getInt(key, 0);
            if (level >= storedLevel) {
                getMainEditor().putInt(key, value);
                if (level != storedLevel) {
                    getLevelEditor().putInt(key, level);
                }
            }
            return this;
        }

        @Override
        public KVStorageEditor putFloat(String key, float value) {
            int storedLevel = levelStorage.getInt(key, 0);
            if (level >= storedLevel) {
                getMainEditor().putFloat(key, value);
                if (level != storedLevel) {
                    getLevelEditor().putInt(key, level);
                }
            }
            return this;
        }

        @Override
        public KVStorageEditor putBoolean(String key, boolean value) {
            int storedLevel = levelStorage.getInt(key, 0);
            if (level >= storedLevel) {
                getMainEditor().putBoolean(key, value);
                if (level != storedLevel) {
                    getLevelEditor().putInt(key, level);
                }
            }
            return this;
        }

        @Override
        public KVStorageEditor putStringArray(String key, String[] value) {
            int storedLevel = levelStorage.getInt(key, 0);
            if (level >= storedLevel) {
                getMainEditor().putStringArray(key, value);
                if (level != storedLevel) {
                    getLevelEditor().putInt(key, level);
                }
            }
            return this;
        }

        @Override
        public KVStorageEditor remove(String key) {
            getMainEditor().remove(key);
            getLevelEditor().remove(key);
            return this;
        }

        @Override
        public void apply() {
            if (mainEditor != null) {
                mainEditor.apply();
            }
            if (levelEditor != null) {
                levelEditor.apply();
            }
        }

        @Override
        public void commit() {
            if (mainEditor != null) {
                mainEditor.commit();
            }
            if (levelEditor != null) {
                levelEditor.commit();
            }
        }
    }
}
