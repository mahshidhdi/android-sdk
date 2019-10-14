package co.pushe.plus.analytics.event;

import java.util.Map;

public class Event {

    private String name;
    private EventAction action;
    private Object data;

    private Event(String name, Object data, EventAction action) {
        this.name = name;
        this.action = action;
        this.data = data;
    }

    public String getName() {
        return name;
    }

    public EventAction getAction() {
        return action;
    }

    public Object getData() {
        return data;
    }

    public static class Builder {
        private String name;
        private EventAction action = EventAction.CUSTOM;
        private Object data = null;

        public Builder(String name) {
            this.name = name;
        }

        public Builder setAction(EventAction action) {
            this.action = action;
            return this;
        }

        public Builder setData(Integer data) {
            this.data = data;
            return this;
        }

        public Builder setData(String data) {
            this.data = data;
            return this;
        }

        public Builder setData(Double data) {
            this.data = data;
            return this;
        }

        public Builder setData(Boolean data) {
            this.data = data;
            return this;
        }

        public Builder setData(Map<String, Object> data) {
            this.data = data;
            return this;
        }

        public Event build() {
            return new Event(name, data, action);
        }
    }
}
