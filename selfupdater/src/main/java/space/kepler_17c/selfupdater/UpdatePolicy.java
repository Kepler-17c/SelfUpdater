package space.kepler_17c.selfupdater;

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
    WHEN_READY,
}
