package space.kepler_17c.selfupdater;

import java.nio.file.Path;

interface ApplyDiff {
    Path applyDiff(Path diff, Path jar);

    static Path v1(Path diff, Path jar) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }
}
