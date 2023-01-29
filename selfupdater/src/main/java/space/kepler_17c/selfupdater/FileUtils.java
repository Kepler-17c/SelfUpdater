package space.kepler_17c.selfupdater;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

final class FileUtils {
    private static final int READ_BUFFER_SIZE = 1 << 20; // 2^20 = 1 MiB
    private static final Map<UUID, WorkingDirectory> workingDirectoryMap = new HashMap<>();
    private static final String WORKING_DIR_PREFIX = "SelfUpdate-tmp-";
    private static final String PATH_OLD = "old/";
    private static final String PATH_NEW = "new/";
    private static final String PATH_DIFF = "diff/";
    private static final String PATH_DIFF_DATA = PATH_DIFF + "data/";
    private static final String PATH_DIFF_META = PATH_DIFF + "meta/";

    private FileUtils() {}

    static Path getRunningJarFile() {
        return Path.of(URI.create(FileUtils.class
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toString())
                .getPath());
    }

    private static UUID createWorkingDirectory() {
        UUID dirId = UUID.randomUUID();
        Path dir = getRunningJarFile().getParent().resolve(WORKING_DIR_PREFIX + dirId + "/");
        WorkingDirectory directories;
        try {
            directories = WorkingDirectory.fromPath(dir);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        workingDirectoryMap.put(dirId, directories);
        return dirId;
    }

    private static boolean clearWorkingDirectory(UUID dirId) {
        if (!workingDirectoryMap.containsKey(dirId)) {
            return true;
        }
        FileVisitor<Path> fileDeletionVisitor = new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        };
        Path rootDir = workingDirectoryMap.get(dirId).rootDir;
        try {
            Files.walkFileTree(rootDir, fileDeletionVisitor);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (Files.isDirectory(rootDir)) {
            return false;
        } else {
            workingDirectoryMap.remove(dirId);
            return true;
        }
    }

    static WorkingDirectory prepareWorkingDirectory(Path oldJar, Path newJar, Path diff) {
        if (oldJar != null && !Files.isRegularFile(oldJar)
                || newJar != null && !Files.isRegularFile(newJar)
                || diff != null && !Files.isRegularFile(diff)) {
            return null;
        }
        UUID dirId = createWorkingDirectory();
        if (dirId == null) {
            return null;
        }
        WorkingDirectory wd = workingDirectoryMap.get(dirId);
        try {
            extractJar(oldJar, wd.oldJar);
            extractJar(newJar, wd.newJar);
            extractJar(diff, wd.diff);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return wd;
    }

    private static void extractJar(Path jar, Path targetDirectory) throws IOException {
        if (jar == null) {
            return;
        }
        Files.createDirectory(targetDirectory);
        ZipInputStream zis = new ZipInputStream(Files.newInputStream(jar));
        ZipEntry ze;
        while ((ze = zis.getNextEntry()) != null) {
            Path entryFile = targetDirectory.resolve(ze.getName());
            if (Files.isDirectory(entryFile)) {
                Files.createDirectories(entryFile);
            } else if (Files.isRegularFile(entryFile)) {
                OutputStream outputStream = Files.newOutputStream(entryFile);
                outputStream.write(zis.readAllBytes());
                outputStream.close();
            }
        }
        zis.close();
    }

    public static void zipJar(Path sourceDirectory, Path jar) throws IOException {
        ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(jar));
        FileVisitor<Path> zipWritingFileVisitor = new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                ZipEntry ze = new ZipEntry(sourceDirectory.relativize(dir).toString());
                zos.putNextEntry(ze);
                zos.closeEntry();
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                ZipEntry ze = new ZipEntry(sourceDirectory.relativize(file).toString());
                zos.putNextEntry(ze);
                InputStream inputStream = Files.newInputStream(file);
                zos.write(inputStream.readAllBytes());
                inputStream.close();
                zos.closeEntry();
                return FileVisitResult.CONTINUE;
            }
        };
        Files.walkFileTree(sourceDirectory, zipWritingFileVisitor);
        zos.close();
    }

    static Map<String, String> getDiffMetaData(WorkingDirectory workingDirectory) throws IOException {
        Map<String, String> result = new HashMap<>();
        List<Path> metaFiles;
        try (Stream<Path> pathStream = Files.list(workingDirectory.diffMeta)) {
            metaFiles = pathStream.toList();
        }
        for (Path file : metaFiles) {
            try (InputStream inputStream = Files.newInputStream(file)) {
                byte[] dataBytes = inputStream.readAllBytes();
                String dataString = dataBytes == null ? null : new String(dataBytes);
                result.put(file.getFileName().toString(), dataString);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
        return result;
    }

    static String hashFile(Path file) {
        MessageDigest sha256 = null;
        try {
            sha256 = MessageDigest.getInstance("SHA256");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
        byte[] hashBytes;
        byte[] readBuffer = new byte[READ_BUFFER_SIZE];
        int bytesCount;
        try (InputStream inputStream = Files.newInputStream(file)) {
            while ((bytesCount = inputStream.read(readBuffer)) != 0) {
                sha256.update(readBuffer, 0, bytesCount);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        hashBytes = sha256.digest();
        return IntStream.range(0, hashBytes.length)
                .map(i -> 0xFF & hashBytes[i])
                .mapToObj(b -> String.format("%02x", b))
                .collect(Collectors.joining());
    }

    static String hashDirectory(Path dir) {
        MessageDigest sha256 = null;
        try {
            sha256 = MessageDigest.getInstance("SHA256");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
        byte[] readBuffer = new byte[READ_BUFFER_SIZE];
        byte[] hashBytes;
        int bytesCount;
        Stack<Path> fileStack = new Stack<>();
        Path tmp;
        try {
            pushFilesReversed(fileStack, dir);
            while (!fileStack.isEmpty()) {
                tmp = fileStack.pop();
                sha256.update(dir.relativize(tmp).toString().getBytes(StandardCharsets.UTF_8));
                if (Files.isDirectory(tmp)) {
                    pushFilesReversed(fileStack, tmp);
                } else if (Files.isRegularFile(tmp)) {
                    try (InputStream inputStream = Files.newInputStream(tmp)) {
                        while ((bytesCount = inputStream.read(readBuffer)) != 0) {
                            sha256.update(readBuffer, 0, bytesCount);
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        hashBytes = sha256.digest();
        return IntStream.range(0, hashBytes.length)
                .map(i -> 0xFF & hashBytes[i])
                .mapToObj(b -> String.format("%02x", b))
                .collect(Collectors.joining());
    }

    static void pushFilesReversed(Stack<Path> stack, Path dir) throws IOException {
        if (dir == null || Files.isRegularFile(dir)) {
            return;
        }
        try (Stream<Path> pathStream = Files.list(dir)) {
            pathStream.sorted(Comparator.reverseOrder()).forEach(stack::push);
        }
    }

    static boolean equalFiles(Path a, Path b) throws IOException {
        try (InputStream inA = Files.newInputStream(a);
                InputStream inB = Files.newInputStream(b)) {
            byte[] bufferA = new byte[READ_BUFFER_SIZE];
            byte[] bufferB = new byte[READ_BUFFER_SIZE];
            int receivedA;
            int receivedB;
            while ((receivedA = inA.read(bufferA)) != 0 | (receivedB = inB.read(bufferB)) != 0) {
                if (receivedA != receivedB || Arrays.compare(bufferA, bufferB) != 0) {
                    return false;
                }
            }
            return true;
        }
    }

    static String getStrippedFileName(Path file) {
        String rawName = file.getFileName().toString();
        return rawName.substring(0, rawName.lastIndexOf("."));
    }

    static final class WorkingDirectory {
        final Path rootDir;
        final Path oldJar;
        final Path newJar;
        final Path diff;
        final Path diffData;
        final Path diffMeta;

        private WorkingDirectory(Path rootDir) {
            this.rootDir = rootDir;
            oldJar = rootDir.resolve(PATH_OLD);
            newJar = rootDir.resolve(PATH_NEW);
            diff = rootDir.resolve(PATH_DIFF);
            diffData = rootDir.resolve(PATH_DIFF_DATA);
            diffMeta = rootDir.resolve(PATH_DIFF_META);
        }

        static WorkingDirectory fromPath(Path rootDir) throws IOException {
            WorkingDirectory directories = new WorkingDirectory(rootDir);
            directories.createDirectories();
            return directories;
        }

        private void createDirectories() throws IOException {
            Files.createDirectories(oldJar);
            Files.createDirectories(newJar);
            Files.createDirectories(diffData);
            Files.createDirectories(diffMeta);
        }
    }
}
