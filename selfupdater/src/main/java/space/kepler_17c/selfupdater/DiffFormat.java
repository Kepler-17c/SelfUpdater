package space.kepler_17c.selfupdater;

/**
 * List of all
 * <a href="https://github.com/Kepler-17c/SelfUpdater/blob/main/selfupdater/src/main/resources/diff-format.md">
 * diff formats
 * </a>
 * implemented in this library version.
 */
public enum DiffFormat {
    V1(CreateDiff::v1, ApplyDiff::v1);

    static final DiffFormat LATEST = V1;

    final CreateDiff createFunction;
    final ApplyDiff applyFunction;

    DiffFormat(CreateDiff createFunction, ApplyDiff applyFunction) {
        this.createFunction = createFunction;
        this.applyFunction = applyFunction;
    }

    static DiffFormat getFormatByVersion(int version) throws SelfUpdaterException {
        switch (version) {
            case 1:
                return V1;
            default:
                throw new SelfUpdaterException("Not a valid version number: " + version);
        }
    }
}
