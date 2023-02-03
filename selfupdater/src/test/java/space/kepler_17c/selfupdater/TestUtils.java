package space.kepler_17c.selfupdater;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
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

    static <T> Object invokePrivateMethod(Class<T> typeClass, String methodName, Class<?>[] types, Object[] args)
            throws SelfUpdaterException {
        try {
            Method declaredMethod = typeClass.getDeclaredMethod(methodName, types);
            declaredMethod.setAccessible(true);
            return declaredMethod.invoke(typeClass, args);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new SelfUpdaterException(e);
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

    static boolean equalZipFiles(Path a, Path b) throws IOException {
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
                    throw new SelfUpdaterException("Jar Comparison Failed: Mismatching names");
                }
                while ((receivedA = zisA.read(bufferA)) > 0 | (receivedB = zisB.read(bufferB)) > 0) {
                    if (receivedA != receivedB || Arrays.compare(bufferA, bufferB) != 0) {
                        byte[] subA = new byte[Math.max(receivedA, 0)];
                        byte[] subB = new byte[Math.max(receivedB, 0)];
                        System.arraycopy(bufferA, 0, subA, 0, Math.max(receivedA, 0));
                        System.arraycopy(bufferB, 0, subB, 0, Math.max(receivedB, 0));
                        throw new SelfUpdaterException("Jar Comparison Failed: Mismatching data\n\t" + new String(subA)
                                + "\n\t" + new String(subB));
                    }
                }
            }
            if (zisA.getNextEntry() != null || zisB.getNextEntry() != null) {
                throw new SelfUpdaterException("Jar Comparison Failed: Differing entry count");
            }
        }
        return true;
    }

    static void printJar(Path jar) throws IOException {
        printJar(jar, System.out);
    }

    static void printJar(Path jar, PrintStream printStream) throws IOException {
        try (InputStream inputStream = Files.newInputStream(jar);
                ZipInputStream zis = new ZipInputStream(inputStream)) {
            ZipEntry ze;
            int maxPreviewLength = 1 << 10;
            byte[] buffer = new byte[maxPreviewLength];
            int bytesRead;
            byte[] croppedBuffer;
            String header = "### Printing Jar  ### " + jar.getFileName() + " ###";
            String footer = "### Done Printing ### " + jar.getFileName() + " ###";
            String border = pad("", "#", header.length());
            printStream.println(border);
            printStream.println(header);
            printStream.println(border);
            while ((ze = zis.getNextEntry()) != null) {
                printStream.println("### " + ze + " ###");
                bytesRead = zis.read(buffer);
                if (bytesRead == -1) {
                    continue;
                }
                croppedBuffer = new byte[Math.min(bytesRead, buffer.length)];
                System.arraycopy(buffer, 0, croppedBuffer, 0, croppedBuffer.length);
                printStream.println(new String(croppedBuffer));
            }
            printStream.println(border);
            printStream.println(footer);
            printStream.println(border);
        }
    }

    static void printDirectory(Path dir) throws IOException {
        printDirectory(dir, System.out);
    }

    static void printDirectory(Path dir, PrintStream printStream) throws IOException {
        String header = "### Printing Directory ### " + dir.getFileName() + " ###";
        String footer = "### Done Printing      ### " + dir.getFileName() + " ###";
        String border = pad("", "#", header.length());
        printStream.println(border);
        printStream.println(header);
        printStream.println(border);
        FileVisitor<Path> printVisitor = new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                printStream.println("### " + dir + " ###");
                return super.preVisitDirectory(dir, attrs);
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                printStream.println("### " + file + " ###");
                return super.visitFile(file, attrs);
            }
        };
        Files.walkFileTree(dir, printVisitor);
        printStream.println(border);
        printStream.println(footer);
        printStream.println(border);
    }

    static String pad(String base, String padSequence, int size) {
        boolean padLeft = size < 0;
        size = Math.abs(size);
        String padding = "";
        for (int i = 0; i < (size - base.length()) / padSequence.length(); i++) {
            padding += padSequence;
        }
        int missingLength = size - base.length() - padding.length();
        if (padLeft) {
            return padSequence.substring(padSequence.length() - missingLength) + padding + base;
        } else {
            return base + padding + padSequence.substring(0, missingLength);
        }
    }
}
