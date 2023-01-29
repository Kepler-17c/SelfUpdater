package space.kepler_17c.selfupdater;

final class MiscUtils {
    private MiscUtils() {
        throw new UnsupportedOperationException("Static utility class");
    }

    record Tuple2<A, B>(A a, B b) {}
}
