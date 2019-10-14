package co.pushe.plus.analytics.event;

import com.squareup.moshi.Json;

public enum EventAction {
    @Json(name = "custom") CUSTOM,
    @Json(name = "sign_up") SIGN_UP,
    @Json(name = "login") LOGIN,
    @Json(name = "purchase") PURCHASE,
    @Json(name = "achievement") ACHIEVEMENT,
    @Json(name = "level") LEVEL;

    @Override
    public String toString() {
        return super.toString().toLowerCase();
    }
}
