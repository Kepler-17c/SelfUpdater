package space.kepler_17c.selfupdater;

import java.io.IOException;

/**
 * Library exception for all failures related to internal requirements.
 */
public class SelfUpdaterException extends IOException {
    SelfUpdaterException() {
        super();
    }

    SelfUpdaterException(String message) {
        super(message);
    }

    SelfUpdaterException(String message, Throwable cause) {
        super(message, cause);
    }

    SelfUpdaterException(Throwable cause) {
        super(cause);
    }
}
