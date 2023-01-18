package space.kepler_17c.selfupdater;

import java.io.File;

interface CreateDiff {
    File createDiff(File oldJar, File newJar, File outputDir);

    static File v1(File oldJar, File newJar, File outputDir) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }
}
