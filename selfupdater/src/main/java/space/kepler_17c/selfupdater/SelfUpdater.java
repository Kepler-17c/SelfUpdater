package space.kepler_17c.selfupdater;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import space.kepler_17c.selfupdater.FileUtils.DiffMetaData;
import space.kepler_17c.selfupdater.FileUtils.WorkingDirectory;

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
     * Creates a diff from two jar files using the latest diff format and writes it to a file.
     *
     * @param oldJar    Location of the old version.
     * @param newJar    Location of the target version.
     * @param outputDir Directory where the diff will be written to.
     * @return The location of the diff file if all operations succeeded, {@code null} otherwise.
     * @see #createDiff(Path, Path, Path, DiffFormat)
     */
    public static Path createDiff(Path oldJar, Path newJar, Path outputDir) throws IOException {
        return createDiff(oldJar, newJar, outputDir, DiffFormat.LATEST);
    }

    /**
     * Creates a diff from two jar files using the chosen diff format and writes it to a file.
     *
     * @param oldJar     Location of the old version.
     * @param newJar     Location of the target version.
     * @param outputDir  Directory where the diff shall be written to.
     * @param diffFormat Diff format to be used.
     * @return The location of the diff file if all operations succeeded, {@code null} otherwise.
     */
    public static Path createDiff(Path oldJar, Path newJar, Path outputDir, DiffFormat diffFormat) throws IOException {
        return diffFormat.createFunction.createDiff(oldJar, newJar, outputDir);
    }

    /**
     * Applies a previously created diff to a jar file.
     *
     * @param diff Location of the diff to be applied.
     * @param jar  Location of the file to be updated.
     * @return The location of the updated jar file if all operations succeeded, {@code null} otherwise.
     */
    private static Path applyDiff(Path diff, Path jar) throws SelfUpdaterException {
        if (diff != null && Files.isRegularFile(diff)) {
            UpdaterEvent.triggerEvent(UpdaterEvent.RECEIVED_DIFF, true);
        } else {
            UpdaterEvent.triggerEvent(UpdaterEvent.RECEIVED_DIFF, false);
            throw new SelfUpdaterException("Invalid diff file path: " + diff);
        }
        WorkingDirectory workingDirectory = FileUtils.prepareWorkingDirectory(jar, null, diff);
        DiffMetaData metaData = FileUtils.getDiffMetaData(workingDirectory);
        if (metaData.version().matches("[0-9]+") && DiffFormat.hasVersion(Integer.parseInt(metaData.version()))) {
            UpdaterEvent.triggerEvent(UpdaterEvent.CHECKED_VERSION, true);
        } else {
            UpdaterEvent.triggerEvent(UpdaterEvent.CHECKED_VERSION, false);
            throw new SelfUpdaterException("Version string doesn't represent a known version: " + metaData.version());
        }
        if (metaData.diffHash().equals(FileUtils.hashDirectory(workingDirectory.diffDataFiles))
                && metaData.oldHash().equals(FileUtils.hashDirectory(workingDirectory.oldFiles))) {
            UpdaterEvent.triggerEvent(UpdaterEvent.VERIFIED_HASHES, true);
        } else {
            UpdaterEvent.triggerEvent(UpdaterEvent.VERIFIED_HASHES, false);
            throw new SelfUpdaterException("Hashes of source or diff files don't match.");
        }
        int version = Integer.parseInt(metaData.version());
        DiffFormat diffFormat = DiffFormat.getFormatByVersion(version);
        Path resultPath = diffFormat.applyFunction.applyDiff(workingDirectory);
        if (metaData.newHash().equals(FileUtils.hashDirectory(workingDirectory.newFiles))) {
            UpdaterEvent.triggerEvent(UpdaterEvent.VERIFIED_UPDATED_FILES, true);
            return resultPath;
        } else {
            UpdaterEvent.triggerEvent(UpdaterEvent.VERIFIED_UPDATED_FILES, false);
            throw new SelfUpdaterException("Updated files' hashes don't match.");
        }
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
