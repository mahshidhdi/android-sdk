package co.pushe.plus.internal;

public class PusheException extends Exception {
    public PusheException(String message) {
        super(message);
    }

    public PusheException(String message, Throwable cause) {
        super(message, cause);
    }
}
