package space.kepler_17c.selfupdater;

import java.util.HashSet;
import java.util.Set;

/**
 * Available policies for when to apply the update.
 */
public enum UpdatePolicy {
    /**
     * Wait till shutdown of the program to run the update.
     */
    ON_SHUTDOWN,
    /**
     * Run the update when the diff has been applied and the updated file has been created.
     */
    WHEN_READY;

    private static final Set<Runnable> UPDATE_CALLBACKS = new HashSet<>();

    public static void registerUpdateCallback(Runnable callback) {
        UPDATE_CALLBACKS.add(callback);
    }

    public static void removeUpdateCallback(Runnable callback) {
        UPDATE_CALLBACKS.remove(callback);
    }

    static void runUpdateCallbacks() {
        UPDATE_CALLBACKS.forEach(Runnable::run);
    }
}
