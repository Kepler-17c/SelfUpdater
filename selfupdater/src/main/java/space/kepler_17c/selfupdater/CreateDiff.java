package space.kepler_17c.selfupdater;

import java.nio.file.Path;

interface CreateDiff {
    Path createDiff(Path oldJar, Path newJar, Path outputDir);

    static Path v1(Path oldJar, Path newJar, Path outputDir) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }
}
