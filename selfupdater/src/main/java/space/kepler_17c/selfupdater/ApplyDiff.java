package space.kepler_17c.selfupdater;

import java.io.File;

interface ApplyDiff {
    File applyDiff(File diff, File jar);

    static File v1(File diff, File jar) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }
}
