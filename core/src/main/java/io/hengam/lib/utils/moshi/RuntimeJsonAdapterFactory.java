/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.hengam.lib.utils.moshi;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonDataException;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import kotlin.jvm.functions.Function1;
//import javax.annotation.CheckReturnValue;

/**
 * This is a modified version of the `RuntimeJsonAdapterFactory` found in the Moshi repository.
 * It is modified to:
 *  - Include the label key in the JSON data when serializing the objects to JSON.
 *  - Use a custom adapter for the subtype if provided
 *
 * A JsonAdapter factory for polymorphic types. This is useful when the type is not known before
 * deserializing the JSON. This factory's adapters expect JSON in the format of a JSON object with a
 * key whose value is a label that determines the type to which to map the JSON object.
 */
public final class RuntimeJsonAdapterFactory<T> implements JsonAdapter.Factory {
    private final Class<T> baseType;
    private final String labelKey;
    private final Map<String, Type> labelToType = new LinkedHashMap<>();
    private final Map<Type, String> typeToLabel = new LinkedHashMap<>();
    private final Map<Type, Function1<Moshi, JsonAdapter<? extends T>>> typeToAdapterProvider = new LinkedHashMap<>();
    private  Function1<Moshi, JsonAdapter<? extends T>> defaultAdapterProvider;
    private T fallbackValue;

    /**
     * @param baseType The base type for which this factory will create adapters.
     * @param labelKey The key in the JSON object whose value determines the type to which to map the
     * JSON object.
     */
    public static <T> RuntimeJsonAdapterFactory<T> of(Class<T> baseType, String labelKey) {
        if (baseType == null) throw new NullPointerException("baseType == null");
        if (labelKey == null) throw new NullPointerException("labelKey == null");
        return new RuntimeJsonAdapterFactory<>(baseType, labelKey);
    }

    RuntimeJsonAdapterFactory(Class<T> baseType, String labelKey) {
        this.baseType = baseType;
        this.labelKey = labelKey;
    }

    /**
     * Register the subtype that can be created based on the label. When deserializing, if a label
     * that was not registered is found, a JsonDataException will be thrown. When serializing, if a
     * type that was not registered is used, an IllegalArgumentException will be thrown.
     */
    public RuntimeJsonAdapterFactory<T> registerSubtype(String label, Class<? extends T> subtype, Function1<Moshi, JsonAdapter<? extends T>> adapterProvider) {
        if (subtype == null) throw new NullPointerException("subtype == null");
        if (label == null) throw new NullPointerException("label == null");
        if (labelToType.containsKey(label) || labelToType.containsValue(subtype)) {
            throw new IllegalArgumentException("Subtypes and labels must be unique.");
        }
        labelToType.put(label, subtype);
        typeToLabel.put(subtype, label);
        typeToAdapterProvider.put(subtype, adapterProvider);
        return this;
    }

    public RuntimeJsonAdapterFactory<T> registerDefault(Function1<Moshi, JsonAdapter<? extends T>> adapterProvider) {
        this.defaultAdapterProvider = adapterProvider;
        return this;
    }

    public RuntimeJsonAdapterFactory<T> setFallbackValueOnError(T fallbackValue) {
        this.fallbackValue = fallbackValue;
        return this;
    }

    public RuntimeJsonAdapterFactory<T> registerSubtype(String label, Class<? extends T> subtype) {
        return registerSubtype(label, subtype, null);
    }

    @Override
    public JsonAdapter<?> create(Type type, Set<? extends Annotation> annotations, Moshi moshi) {
        if (Types.getRawType(type) != baseType || !annotations.isEmpty()) {
            return null;
        }
        int size = labelToType.size();
        Map<Type, JsonAdapter<Object>> typeToAdapter = new LinkedHashMap<>(size);
        Map<String, JsonAdapter<Object>> labelToAdapter = new LinkedHashMap<>(size);
        for (Map.Entry<String, Type> entry : labelToType.entrySet()) {
            String label = entry.getKey();
            Type typeValue = entry.getValue();

            Function1<Moshi, JsonAdapter<? extends T>> adapterProvider = typeToAdapterProvider.get(type);
            JsonAdapter<Object> adapter;
            if (adapterProvider == null) {
                adapter = moshi.adapter(typeValue);
            } else {
                adapter = (JsonAdapter<Object>)adapterProvider.invoke(moshi);
            }
            labelToAdapter.put(label, adapter);
            typeToAdapter.put(typeValue, adapter);
        }
        JsonAdapter<Object> anyAdapter = moshi.adapter(Object.class);
        JsonAdapter<Object> defaultAdapter = null;
        if (defaultAdapterProvider != null) {
            defaultAdapter = (JsonAdapter<Object>)defaultAdapterProvider.invoke(moshi);
        }
        return new RuntimeJsonAdapter(labelKey, labelToAdapter, typeToAdapter, typeToLabel, anyAdapter, defaultAdapter, fallbackValue).nullSafe();
    }

    static final class RuntimeJsonAdapter extends JsonAdapter<Object> {
        final String labelKey;
        final Map<String, JsonAdapter<Object>> labelToAdapter;
        final Map<Type, JsonAdapter<Object>> typeToAdapter;
        final Map<Type, String> typeToLabel;
        final JsonAdapter<Object> anyAdapter;
        final JsonAdapter<Object> defaultAdapter;
        final Object fallbackValue;

        RuntimeJsonAdapter(
                String labelKey,
                Map<String, JsonAdapter<Object>> labelToAdapter,
                Map<Type, JsonAdapter<Object>> typeToAdapter,
                Map<Type, String> typeToLabel,
                JsonAdapter<Object> anyAdapter,
                JsonAdapter<Object> defaultAdapter,
                Object fallbackValue
        ) {
            this.labelKey = labelKey;
            this.labelToAdapter = labelToAdapter;
            this.typeToAdapter = typeToAdapter;
            this.typeToLabel = typeToLabel;
            this.anyAdapter = anyAdapter;
            this.defaultAdapter = defaultAdapter;
            this.fallbackValue = fallbackValue;
        }

        @Override public Object fromJson(JsonReader reader) throws IOException {
            JsonReader.Token peekedToken = reader.peek();

            try {
                if (peekedToken != JsonReader.Token.BEGIN_OBJECT) {
                    throw new JsonDataException("Expected BEGIN_OBJECT but was " + peekedToken
                            + " at path " + reader.getPath());
                }
                Object jsonValue = reader.readJsonValue();
                Map<String, Object> jsonObject = (Map<String, Object>) jsonValue;
                Object label = jsonObject.get(labelKey);
                if (label == null) {
                    throw new JsonDataException("Missing label for " + labelKey);
                }
                if (!(label instanceof String)) {
                    throw new JsonDataException("Label for '"
                            + labelKey
                            + "' must be a string but was "
                            + label
                            + ", a "
                            + label.getClass());
                }
                JsonAdapter<Object> adapter = labelToAdapter.get(label);
                if (adapter == null) {
                    adapter = defaultAdapter;
                }
                if (adapter == null) {
                    throw new JsonDataException("Expected one of "
                            + labelToAdapter.keySet()
                            + " for key '"
                            + labelKey
                            + "' but found '"
                            + label
                            + "'. Register a subtype for this label.");
                }
                return adapter.fromJsonValue(jsonValue);
            } catch (JsonDataException e) {
                if (fallbackValue != null) {
                    return fallbackValue;
                } else {
                    throw e;
                }
            }
        }

        @Override public void toJson(JsonWriter writer, Object value) throws IOException {
            Class<?> type = value.getClass();
            JsonAdapter<Object> adapter = typeToAdapter.get(type);
            if (adapter == null && defaultAdapter != null) {
                adapter = defaultAdapter;
            }
            if (adapter == null) {
                throw new IllegalArgumentException("Expected one of "
                        + typeToAdapter.keySet()
                        + " but found "
                        + value
                        + ", a "
                        + value.getClass()
                        + ". Register this subtype.");
            }

            writer.beginObject();
            writer.name(labelKey).value(typeToLabel.get(type));
            Map<String, Object> jsonValue = (Map<String, Object>) adapter.toJsonValue(value);
            for (String key : jsonValue.keySet()) {
                if (key.equals(labelKey)) {
                    continue;
                }
                writer.name(key);
                anyAdapter.toJson(writer, jsonValue.get(key));
            }
            writer.endObject();
        }

        @Override public String toString() {
            return "RuntimeJsonAdapter(" + labelKey + ")";
        }
    }
}