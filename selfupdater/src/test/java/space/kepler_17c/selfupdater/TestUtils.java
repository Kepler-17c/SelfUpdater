package space.kepler_17c.selfupdater;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TestUtils {
    public static final Path RESOURCES = Paths.get("src", "test", "resources");

    private TestUtils() {
        throw new UnsupportedOperationException("static utility class");
    }

    static <T> Object invokePrivateMethod(Class<T> typeClass, String methodName, Class<?>[] types, Object[] args) {
        try {
            Method declaredMethod = typeClass.getDeclaredMethod(methodName, types);
            declaredMethod.setAccessible(true);
            return declaredMethod.invoke(typeClass, args);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            return null;
        }
    }
}
