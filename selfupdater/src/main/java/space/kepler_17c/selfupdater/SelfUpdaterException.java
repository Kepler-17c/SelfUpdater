package space.kepler_17c.selfupdater;

import java.io.IOException;

/**
 * Library exception for all failures related to internal requirements.
 */
public class SelfUpdaterException extends IOException {
    SelfUpdaterException(String message) {
        super(message);
    }

    SelfUpdaterException(Throwable cause) {
        super(cause);
    }
}
