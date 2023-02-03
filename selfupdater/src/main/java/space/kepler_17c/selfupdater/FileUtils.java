package space.kepler_17c.selfupdater;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import space.kepler_17c.selfupdater.MiscUtils.Tuple2;

final class FileUtils {
    private static final int READ_BUFFER_SIZE = 1 << 20; // 2^20 = 1 MiB
    private static final String WORKING_DIR_PREFIX = "SelfUpdater-";
    private static final String UUID_PATTERN =
            "\\p{XDigit}{8}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{12}";
    private static final String WORKING_DIR_PATTERN = WORKING_DIR_PREFIX + UUID_PATTERN;
    private static final String PATH_OLD = "old/";
    private static final String PATH_NEW = "new/";
    private static final String PATH_DIFF = "diff/";
    private static final String PATH_DIFF_DATA = PATH_DIFF + "data/";
    private static final String PATH_DIFF_META = PATH_DIFF + "meta/";
    static final String DIFF_FILE_TYPE = "jardiff";
    static final String UPDATED_FILE_NAME = "updated.jar";

    static {
        // clear working from the previous run on launch
        List<Path> staleTmpDirs = Collections.emptyList();
        try (Stream<Path> pathStream = Files.list(getSystemTmpDir())) {
            staleTmpDirs = pathStream
                    .filter(p -> p.getFileName().toString().matches(WORKING_DIR_PATTERN))
                    .toList();
        } catch (IOException e) {
            e.printStackTrace();
        }
        for (Path dir : staleTmpDirs) {
            try {
                clearWorkingDirectory(dir);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private FileUtils() {}

    static Path getRunningJarFile() {
        URL jarUrl = FileUtils.class.getProtectionDomain().getCodeSource().getLocation();
        URI jarUri = URI.create(jarUrl.toString());
        return Path.of(jarUri);
    }

    public static Path getSystemTmpDir() {
        return Path.of(System.getProperty("java.io.tmpdir"));
    }

    public static Path createTmpDir() throws IOException {
        Path tmpDir = getSystemTmpDir().resolve(WORKING_DIR_PREFIX + UUID.randomUUID());
        Files.createDirectory(tmpDir);
        return tmpDir;
    }

    static WorkingDirectory prepareWorkingDirectory(Path oldJar, Path newJar, Path diff) throws IOException {
        if (oldJar != null && !Files.isRegularFile(oldJar)
                || newJar != null && !Files.isRegularFile(newJar)
                || diff != null && !Files.isRegularFile(diff)) {
            throw new IOException("Paths contain non-regular files.");
        }
        WorkingDirectory wd = WorkingDirectory.fromPath(createTmpDir());
        extractJar(oldJar, wd.oldFiles);
        extractJar(newJar, wd.newFiles);
        extractJar(diff, wd.diffRoot);
        return wd;
    }

    public static void clearWorkingDirectory(Path rootDir) throws IOException {
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
        Files.walkFileTree(rootDir, fileDeletionVisitor);
    }

    private static void extractJar(Path jar, Path targetDirectory) throws IOException {
        if (jar == null) {
            return;
        }
        if (!Files.isDirectory(targetDirectory)) {
            Files.createDirectories(targetDirectory);
        }
        ZipInputStream zis = new ZipInputStream(Files.newInputStream(jar));
        ZipEntry ze;
        while ((ze = zis.getNextEntry()) != null) {
            String entryName = ze.getName();
            Path entryFile = targetDirectory.resolve(entryName);
            if (entryName.endsWith("/")) {
                Files.createDirectories(entryFile);
            } else {
                OutputStream outputStream = Files.newOutputStream(entryFile);
                outputStream.write(zis.readAllBytes());
                outputStream.close();
            }
        }
        zis.close();
    }

    public static void zipDir(Path sourceDirectory, Path jar) throws IOException {
        if (!Files.isDirectory(sourceDirectory)) {
            return;
        }
        ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(jar));
        FileVisitor<Path> zipWritingFileVisitor = new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                String pathString = sourceDirectory.relativize(dir).toString();
                if (pathString.isEmpty()) {
                    return FileVisitResult.CONTINUE;
                }
                pathString = normalisedDirString(pathString);
                ZipEntry ze = new ZipEntry(pathString);
                zos.putNextEntry(ze);
                zos.closeEntry();
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String pathString = normalisedPathString(sourceDirectory.relativize(file));
                ZipEntry ze = new ZipEntry(pathString);
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

    static void generateMandatoryMetaFiles(WorkingDirectory workingDirectory, String version) throws IOException {
        String diffHash = FileUtils.hashDirectory(workingDirectory.diffDataFiles);
        String newHash = FileUtils.hashDirectory(workingDirectory.newFiles);
        String oldHash = FileUtils.hashDirectory(workingDirectory.oldFiles);
        List<Tuple2<String, String>> entries = new ArrayList<>();
        entries.add(new Tuple2<>("diffHash", diffHash));
        entries.add(new Tuple2<>("newHash", newHash));
        entries.add(new Tuple2<>("oldHash", oldHash));
        entries.add(new Tuple2<>("version", version));
        for (Tuple2<String, String> e : entries) {
            try (OutputStream outputStream = Files.newOutputStream(workingDirectory.diffMetaFiles.resolve(e.a()))) {
                outputStream.write(e.b().getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    static DiffMetaData getDiffMetaData(WorkingDirectory workingDirectory) throws IOException {
        Map<String, String> presentMetaData = new HashMap<>();
        List<Path> metaFiles;
        try (Stream<Path> pathStream = Files.list(workingDirectory.diffMetaFiles)) {
            metaFiles = pathStream.toList();
        }
        for (Path file : metaFiles) {
            try (InputStream inputStream = Files.newInputStream(file)) {
                byte[] dataBytes = inputStream.readAllBytes();
                String dataString = dataBytes == null ? null : new String(dataBytes);
                presentMetaData.put(file.getFileName().toString(), dataString);
            }
        }
        return new DiffMetaData(
                presentMetaData.get("diffHash"),
                presentMetaData.get("oldHash"),
                presentMetaData.get("newHash"),
                presentMetaData.get("version"));
    }

    static String hashFile(Path file) throws IOException {
        MessageDigest sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required to be present on all implementations
            throw new RuntimeException(e);
        }
        byte[] hashBytes;
        byte[] readBuffer = new byte[READ_BUFFER_SIZE];
        int bytesCount;
        try (InputStream inputStream = Files.newInputStream(file)) {
            while ((bytesCount = inputStream.read(readBuffer)) > 0) {
                sha256.update(readBuffer, 0, bytesCount);
            }
        }
        hashBytes = sha256.digest();
        return IntStream.range(0, hashBytes.length)
                .map(i -> 0xFF & hashBytes[i])
                .mapToObj(b -> String.format("%02x", b))
                .collect(Collectors.joining());
    }

    static String hashDirectory(Path dir) throws IOException {
        MessageDigest sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required to be present on all implementations
            throw new RuntimeException(e);
        }
        byte[] readBuffer = new byte[READ_BUFFER_SIZE];
        byte[] hashBytes;
        int bytesCount;
        Stack<Path> fileStack = new Stack<>();
        Path absPath, relPath;
        String pathString;
        pushFilesReversed(fileStack, dir);
        while (!fileStack.isEmpty()) {
            absPath = fileStack.pop();
            relPath = dir.relativize(absPath);
            pathString = Files.isDirectory(absPath) ? normalisedDirString(relPath) : normalisedPathString(relPath);
            sha256.update(pathString.getBytes(StandardCharsets.UTF_8));
            if (Files.isDirectory(absPath)) {
                pushFilesReversed(fileStack, absPath);
            } else if (Files.isRegularFile(absPath)) {
                try (InputStream inputStream = Files.newInputStream(absPath)) {
                    while ((bytesCount = inputStream.read(readBuffer)) > 0) {
                        sha256.update(readBuffer, 0, bytesCount);
                    }
                }
            }
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
            while ((receivedA = inA.read(bufferA)) > 0 | (receivedB = inB.read(bufferB)) > 0) {
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

    static String normalisedPathString(Path path) {
        return normalisedPathString(path.toString());
    }

    static String normalisedPathString(String path) {
        return path.replace("\\", "/");
    }

    static String normalisedDirString(Path dir) {
        return normalisedDirString(dir.toString());
    }

    static String normalisedDirString(String dir) {
        String normalised = normalisedPathString(dir);
        if (!normalised.endsWith("/")) {
            normalised += "/";
        }
        return normalised;
    }

    static final class WorkingDirectory {
        final Path rootDir;
        final Path oldFiles;
        final Path newFiles;
        final Path diffRoot;
        final Path diffDataFiles;
        final Path diffMetaFiles;

        private WorkingDirectory(Path rootDir) {
            this.rootDir = rootDir;
            oldFiles = rootDir.resolve(PATH_OLD);
            newFiles = rootDir.resolve(PATH_NEW);
            diffRoot = rootDir.resolve(PATH_DIFF);
            diffDataFiles = rootDir.resolve(PATH_DIFF_DATA);
            diffMetaFiles = rootDir.resolve(PATH_DIFF_META);
        }

        static WorkingDirectory fromPath(Path rootDir) throws IOException {
            WorkingDirectory directories = new WorkingDirectory(rootDir);
            directories.createDirectories();
            return directories;
        }

        private void createDirectories() throws IOException {
            Files.createDirectories(oldFiles);
            Files.createDirectories(newFiles);
            Files.createDirectories(diffDataFiles);
            Files.createDirectories(diffMetaFiles);
        }
    }

    record DiffMetaData(String diffHash, String oldHash, String newHash, String version) {}
}
