package space.kepler_17c.selfupdater;

final class DiffFormatConstantsV1 {
    static final String VERSION = "1";
    static final String META_DELETED = "deletedFiles";
    static final String META_MOVED = "movedFiles";
    static final String DATA_DIR = "tree";

    private DiffFormatConstantsV1() {
        throw new UnsupportedOperationException("Static utility class.");
    }
}
