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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.CRC32;
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

    /**
     * A list of registered magic bytes to identify jar files.
     * <p>
     *     The list index stands for the offset at which the magic bytes start.
     *     Each index carries a (potentially empty) collection of byte sequences for that offset.
     * </p>
     */
    static final List<Collection<List<Integer>>> JAR_FILE_MAGIC_BYTES = new ArrayList<>();

    static {
        // for now this only registers jar/zip files for uncompressed nesting
        // tuples of offset and magic bytes
        List<Tuple2<Integer, String>> knownMagicBytes = new ArrayList<>();
        // zip and related
        knownMagicBytes.add(new Tuple2<>(0, "50 4B 03 04"));
        knownMagicBytes.add(new Tuple2<>(0, "50 4B 05 06"));
        knownMagicBytes.add(new Tuple2<>(0, "50 4B 07 08"));
        for (Tuple2<Integer, String> pair : knownMagicBytes) {
            while (JAR_FILE_MAGIC_BYTES.size() <= pair.a()) {
                JAR_FILE_MAGIC_BYTES.add(new HashSet<>());
            }
            JAR_FILE_MAGIC_BYTES
                    .get(pair.a())
                    .add(Arrays.stream(pair.b().split(" "))
                            .map(s -> Integer.parseInt(s, 16))
                            .toList());
        }
    }

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
        String jarUriString = URI.create(jarUrl.toString()).toString();
        int cutStart = Math.max(jarUriString.lastIndexOf(':') + 1, 0);
        int cutEnd = jarUriString.indexOf('!');
        return Path.of(cutEnd < 0 ? jarUriString.substring(cutStart) : jarUriString.substring(cutStart, cutEnd));
    }

    private static Path getSystemTmpDir() {
        return Path.of(System.getProperty("java.io.tmpdir"));
    }

    public static Path createTmpDir() throws IOException {
        Path tmpDir = getSystemTmpDir().resolve(WORKING_DIR_PREFIX + UUID.randomUUID());
        Files.createDirectory(tmpDir);
        return tmpDir;
    }

    static WorkingDirectory prepareWorkingDirectory(Path oldJar, Path newJar, Path diff) throws SelfUpdaterException {
        WorkingDirectory wd;
        try {
            wd = WorkingDirectory.fromPath(createTmpDir());
            extractJar(oldJar, wd.oldFiles);
            extractJar(newJar, wd.newFiles);
            extractJar(diff, wd.diffRoot);
            UpdaterEvent.triggerEvent(UpdaterEvent.EXTRACTED_DATA, true);
        } catch (IOException e) {
            UpdaterEvent.triggerEvent(UpdaterEvent.EXTRACTED_DATA, false);
            throw new SelfUpdaterException("Failed to extract source files to working directory.", e);
        }
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
        if (!Files.isRegularFile(jar)) {
            throw new SelfUpdaterException("Jar path must denote a regular file, but is " + jar);
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
                pathString = normalisedPathString(pathString, true);
                ZipEntry ze = new ZipEntry(pathString);
                zos.putNextEntry(ze);
                zos.closeEntry();
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String pathString = normalisedPathString(sourceDirectory.relativize(file), false);
                byte[] data;
                try (InputStream inputStream = Files.newInputStream(file)) {
                    data = inputStream.readAllBytes();
                }
                ZipEntry ze = new ZipEntry(pathString);
                if (isCompressedFile(file)) {
                    ze.setMethod(ZipEntry.STORED);
                    ze.setSize(data.length);
                    ze.setCompressedSize(data.length);
                    CRC32 crc = new CRC32();
                    crc.update(data);
                    ze.setCrc(crc.getValue());
                }
                zos.putNextEntry(ze);
                zos.write(data);
                zos.closeEntry();
                return FileVisitResult.CONTINUE;
            }
        };
        Files.walkFileTree(sourceDirectory, zipWritingFileVisitor);
        zos.close();
    }

    private static boolean isCompressedFile(Path file) throws SelfUpdaterException {
        List<Integer> byteBuffer = new ArrayList<>();
        try (InputStream input = Files.newInputStream(file)) {
            for (Collection<List<Integer>> offsetGroup : JAR_FILE_MAGIC_BYTES) {
                for (List<Integer> byteSequence : offsetGroup) {
                    while (byteBuffer.size() < Math.max(byteSequence.size(), 1)) {
                        byteBuffer.add(input.read());
                    }
                    boolean foundMatch = IntStream.range(0, byteSequence.size())
                            .mapToObj(i -> byteSequence.get(i).equals(byteBuffer.get(i)))
                            .reduce(true, Boolean::logicalAnd);
                    if (foundMatch) {
                        return true;
                    }
                }
                byteBuffer.remove(0);
            }
        } catch (IOException e) {
            throw new SelfUpdaterException(e);
        }
        return false;
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

    static DiffMetaData getDiffMetaData(WorkingDirectory workingDirectory) throws SelfUpdaterException {
        Map<String, String> presentMetaData = new HashMap<>();
        List<Path> metaFiles;
        try (Stream<Path> pathStream = Files.list(workingDirectory.diffMetaFiles)) {
            metaFiles = pathStream.toList();
        } catch (IOException e) {
            throw new SelfUpdaterException("Failed to get list of meta file's paths.", e);
        }
        for (Path file : metaFiles) {
            try (InputStream inputStream = Files.newInputStream(file)) {
                byte[] dataBytes = inputStream.readAllBytes();
                String dataString = dataBytes == null ? null : new String(dataBytes);
                presentMetaData.put(file.getFileName().toString(), dataString);
            } catch (IOException e) {
                throw new SelfUpdaterException("Failed to read meta file.", e);
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

    static String hashDirectory(Path dir) throws SelfUpdaterException {
        MessageDigest sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required to be present on all implementations
            throw new SelfUpdaterException("Invalid JRE implementation.", e);
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
            pathString = normalisedPathString(relPath, Files.isDirectory(absPath));
            sha256.update(pathString.getBytes(StandardCharsets.UTF_8));
            if (Files.isDirectory(absPath)) {
                pushFilesReversed(fileStack, absPath);
            } else if (Files.isRegularFile(absPath)) {
                try (InputStream inputStream = Files.newInputStream(absPath)) {
                    while ((bytesCount = inputStream.read(readBuffer)) > 0) {
                        sha256.update(readBuffer, 0, bytesCount);
                    }
                } catch (IOException e) {
                    throw new SelfUpdaterException("Failed to file for hash.", e);
                }
            }
        }
        hashBytes = sha256.digest();
        return IntStream.range(0, hashBytes.length)
                .map(i -> 0xFF & hashBytes[i])
                .mapToObj(b -> String.format("%02x", b))
                .collect(Collectors.joining());
    }

    static void pushFilesReversed(Stack<Path> stack, Path dir) throws SelfUpdaterException {
        if (dir == null || Files.isRegularFile(dir)) {
            return;
        }
        // this special comparator is necessary due to system dependent behaviour in path comparisons
        Comparator<Path> reverseComparator = (a, b) -> normalisedPathString(b).compareTo(normalisedPathString(a));
        try (Stream<Path> pathStream = Files.list(dir)) {
            pathStream.sorted(reverseComparator).forEach(stack::push);
        } catch (IOException e) {
            throw new SelfUpdaterException("Failed to get file list.", e);
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
        return rawName.substring(0, Math.max(rawName.lastIndexOf("."), 0));
    }

    static String normalisedPathString(Path path) {
        return normalisedPathString(path, Files.isDirectory(path));
    }

    static String normalisedPathString(Path path, boolean isDirectory) {
        return normalisedPathString(path.toString(), isDirectory);
    }

    static String normalisedPathString(String path, boolean isDirectory) {
        String normalisedString = path.replace("\\", "/");
        if (!isDirectory && normalisedString.endsWith("/")) {
            return normalisedString.substring(0, normalisedString.length() - 1);
        } else if (isDirectory && !normalisedString.endsWith("/")) {
            return normalisedString + "/";
        } else {
            return normalisedString;
        }
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
