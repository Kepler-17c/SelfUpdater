package space.kepler_17c.selfupdater;

import java.io.File;

/**
 * This is the interface of the library providing access to all functionality.
 */
public final class SelfUpdater {
    /**
     * Holds the current update policy.
     * <p>Default value is {@link UpdatePolicy#ON_SHUTDOWN}.</p>
     */
    private static UpdatePolicy updatePolicy = UpdatePolicy.ON_SHUTDOWN;

    private SelfUpdater() {
        throw new UnsupportedOperationException("This is a static class.");
    }

    /**
     * Creates a diff from two jar files and writes it to the file system.
     *
     * @param oldJar          Location of the old version.
     * @param newJar          Location of the target version.
     * @param outputDirectory Directory where the diff shall be written to.
     * @return The location of the diff file if all operations succeeded, {@code null} otherwise.
     */
    public static File createDiff(File oldJar, File newJar, File outputDirectory) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    /**
     * Applies a previously created diff to a jar file.
     *
     * @param diff Location of the diff to be applied.
     * @param jar  Location of the file to be updated.
     * @return The location of the updated jar file if all operations succeeded, {@code null} otherwise.
     */
    public static File applyDiff(File diff, File jar) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    /**
     * Changes the update policy.
     *
     * @param newPolicy The new policy.
     * @return The old policy.
     */
    public static UpdatePolicy setUpdatePolicy(UpdatePolicy newPolicy) {
        UpdatePolicy oldPolicy = updatePolicy;
        updatePolicy = newPolicy;
        return oldPolicy;
    }
}
