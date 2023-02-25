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

    /**
     * Register a function to be called after an update has been applied successfully.
     *
     * @param callback The function to be executed.
     */
    public static void registerUpdateCallback(Runnable callback) {
        UPDATE_CALLBACKS.add(callback);
    }

    /**
     * Remove a previously registered callback function.
     *
     * @param callback The function to be removed.
     *
     * @return Whether removing the function succeeded.
     */
    public static boolean removeUpdateCallback(Runnable callback) {
        return UPDATE_CALLBACKS.remove(callback);
    }

    static void runUpdateCallbacks() {
        UPDATE_CALLBACKS.forEach(Runnable::run);
    }
}
