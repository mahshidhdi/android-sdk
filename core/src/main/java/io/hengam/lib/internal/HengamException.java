package io.hengam.lib.internal;

public class HengamException extends Exception {
    public HengamException(String message) {
        super(message);
    }

    public HengamException(String message, Throwable cause) {
        super(message, cause);
    }
}
