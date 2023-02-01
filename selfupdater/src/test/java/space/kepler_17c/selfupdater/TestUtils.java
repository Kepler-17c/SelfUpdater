package space.kepler_17c.selfupdater;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import static space.kepler_17c.selfupdater.FileUtils.equalFiles;

public class TestUtils {
    private static final int READ_BUFFER_SIZE = 1 << 20; // 2^20 = 1 MiB
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

    static boolean equalDirectories(Path a, Path b) throws IOException {
        if (Files.isDirectory(a) && Files.isDirectory(b)) {
            List<Path> listA;
            List<Path> listB;
            try (Stream<Path> streamA = Files.list(a);
                    Stream<Path> streamB = Files.list(b)) {
                listA = streamA.sorted().toList();
                listB = streamB.sorted().toList();
            }
            if (listA.size() != listB.size()) {
                return false;
            }
            for (int i = 0; i < listA.size(); i++) {
                if (!listA.get(i)
                        .getFileName()
                        .toString()
                        .equals(listB.get(i).getFileName().toString())) {
                    return false;
                }
                if (!equalDirectories(listA.get(i), listB.get(i))) {
                    return false;
                }
            }
            return true;
        } else if (Files.isRegularFile(a) && Files.isRegularFile(b)) {
            return equalFiles(a, b);
        } else {
            return false;
        }
    }

    static boolean equalZipFiles(Path a, Path b) {
        try (ZipInputStream zisA = new ZipInputStream(Files.newInputStream(a));
                ZipInputStream zisB = new ZipInputStream(Files.newInputStream(b))) {
            ZipEntry zeA;
            ZipEntry zeB;
            byte[] bufferA = new byte[READ_BUFFER_SIZE];
            byte[] bufferB = new byte[READ_BUFFER_SIZE];
            int receivedA;
            int receivedB;
            while ((zeA = zisA.getNextEntry()) != null && (zeB = zisB.getNextEntry()) != null) {
                if (!zeA.getName().equals(zeB.getName())) {
                    return false;
                }
                while ((receivedA = zisA.read(bufferA)) > 0 | (receivedB = zisB.read(bufferB)) > 0) {
                    if (receivedA != receivedB || Arrays.compare(bufferA, bufferB) != 0) {
                        return false;
                    }
                }
            }
            if (zisA.getNextEntry() != null || zisB.getNextEntry() != null) {
                return false;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
}
