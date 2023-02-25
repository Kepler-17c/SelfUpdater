package space.kepler_17c.selfupdater;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;
import java.util.stream.Stream;
import space.kepler_17c.selfupdater.FileUtils.WorkingDirectory;
import space.kepler_17c.selfupdater.MiscUtils.Tuple2;

interface CreateDiff {
    Path createDiff(Path oldJar, Path newJar, Path outputDir) throws IOException;

    static Path v1(Path oldJar, Path newJar, Path outputDir) throws IOException {
        if (oldJar == null
                || newJar == null
                || outputDir == null
                || !Files.isRegularFile(oldJar)
                || !Files.isRegularFile(newJar)
                || !Files.isDirectory(outputDir)) {
            throw new IOException(
                    "Arguments are required to be non-null and denote (in that order) a file, file, directory.");
        }
        WorkingDirectory workingDirectory = FileUtils.prepareWorkingDirectory(oldJar, newJar, null);
        // check for moved files
        Map<Long, List<Path>> hashedFilesMap = new TreeMap<>();
        FileVisitor<Path> hashingFileVisitor = new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                long size = attrs.size();
                if (!hashedFilesMap.containsKey(size)) {
                    hashedFilesMap.put(size, new ArrayList<>());
                }
                hashedFilesMap.get(size).add(file);
                return super.visitFile(file, attrs);
            }
        };
        Files.walkFileTree(workingDirectory.oldFiles, hashingFileVisitor);
        List<Tuple2<String, String>> movedFiles = new ArrayList<>();
        FileVisitor<Path> comparingFileVisitor = new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                List<Path> matchingFiles = hashedFilesMap.get(attrs.size());
                if (matchingFiles != null) {
                    for (Path p : matchingFiles) {
                        String relSrc = workingDirectory.oldFiles.relativize(p).toString();
                        String relDest =
                                workingDirectory.newFiles.relativize(file).toString();
                        if (!relSrc.equals(relDest) && FileUtils.equalFiles(file, p)) {
                            movedFiles.add(new Tuple2<>(relSrc, relDest));
                            break;
                        }
                    }
                }
                return super.visitFile(file, attrs);
            }
        };
        Files.walkFileTree(workingDirectory.newFiles, comparingFileVisitor);
        Set<String> movedDestFiles = new HashSet<>();
        for (Tuple2<String, String> filePair : movedFiles) {
            movedDestFiles.add(filePair.b());
        }
        // check for deleted/changed/new files
        Stack<Path> oldFilesStack = new Stack<>();
        Stack<Path> newFilesStack = new Stack<>();
        FileUtils.pushFilesReversed(oldFilesStack, workingDirectory.oldFiles);
        FileUtils.pushFilesReversed(newFilesStack, workingDirectory.newFiles);
        Path oldFile;
        Path newFile;
        Path oldFileRel;
        Path newFileRel;
        Path diffFile;
        List<String> deletedFiles = new ArrayList<>();
        Set<String> encounteredOldDirs = new HashSet<>();
        Set<String> encounteredNewDirs = new HashSet<>();
        Path diffTreeRoot = workingDirectory.diffDataFiles.resolve(DiffFormatConstantsV1.DATA_DIR);
        Files.createDirectories(diffTreeRoot);
        while (true) {
            // ensure top path denotes a file
            while (!oldFilesStack.isEmpty() && Files.isDirectory(oldFilesStack.peek())) {
                encounteredOldDirs.add(FileUtils.normalisedPathString(
                        workingDirectory.oldFiles.relativize(oldFilesStack.peek()), true));
                FileUtils.pushFilesReversed(oldFilesStack, oldFilesStack.pop());
            }
            while (!newFilesStack.isEmpty() && Files.isDirectory(newFilesStack.peek())) {
                encounteredNewDirs.add(FileUtils.normalisedPathString(
                        workingDirectory.newFiles.relativize(newFilesStack.peek()), true));
                FileUtils.pushFilesReversed(newFilesStack, newFilesStack.pop());
            }
            // get references for comparison
            oldFile = oldFilesStack.isEmpty() ? null : oldFilesStack.peek();
            newFile = newFilesStack.isEmpty() ? null : newFilesStack.peek();
            // both stacks empty => done
            if (oldFile == null && newFile == null) {
                break;
            }
            if (oldFile == null || newFile == null) {
                // one stack is empty => handle remaining elements
                if (oldFile == null) {
                    // only [old] is empty => add all new files
                    diffFile = workingDirectory.newFiles.relativize(newFile);
                    if (!movedDestFiles.contains(diffFile.toString())) {
                        diffFile = diffTreeRoot.resolve(diffFile);
                        Files.createDirectories(diffFile.getParent());
                        Files.copy(newFile, diffFile);
                    }
                    newFilesStack.pop();
                } else {
                    // only [new] is empty => mark all remaining as deleted
                    diffFile = workingDirectory.oldFiles.relativize(oldFile);
                    deletedFiles.add(FileUtils.normalisedPathString(diffFile, false));
                    oldFilesStack.pop();
                }
                continue;
            }
            // both stacks contain elements => compare files
            oldFileRel = workingDirectory.oldFiles.relativize(oldFile);
            newFileRel = workingDirectory.newFiles.relativize(newFile);
            String oldRelString = oldFileRel.toString();
            String newRelString = newFileRel.toString();
            diffFile = diffTreeRoot.resolve(newFileRel);
            if (oldRelString.compareTo(newRelString) < 0) {
                // [old] is before [new] alphabetically => [new] skipped a file => mark as deleted
                deletedFiles.add(FileUtils.normalisedPathString(oldRelString, false));
                oldFilesStack.pop();
            } else if (oldRelString.compareTo(newRelString) > 0) {
                // [old] is after [new] alphabetically => [old] skipped a file => add new file
                if (!movedDestFiles.contains(newFileRel.toString())) {
                    Files.createDirectories(diffFile.getParent());
                    Files.copy(newFile, diffFile);
                }
                newFilesStack.pop();
            } else if (oldRelString.equals(newRelString)) {
                // [old] and [new] have equal paths => compare files and add new if changed
                if (!movedDestFiles.contains(newFileRel.toString()) && !FileUtils.equalFiles(newFile, oldFile)) {
                    Files.createDirectories(diffFile.getParent());
                    Files.copy(newFile, diffFile);
                }
                oldFilesStack.pop();
                newFilesStack.pop();
            } else {
                throw new IOException("Working directory is being modified by another thread.");
            }
        }
        for (String dir : encounteredNewDirs.stream()
                .filter(d -> !encounteredOldDirs.contains(d))
                .toList()) {
            Files.createDirectories(workingDirectory
                    .diffDataFiles
                    .resolve(DiffFormatConstantsV1.DATA_DIR)
                    .resolve(dir));
        }
        encounteredOldDirs.removeAll(encounteredNewDirs);
        deletedFiles.addAll(encounteredOldDirs);
        // check for empty diff
        boolean emptyDiffTree;
        try (Stream<Path> diffTreeFiles = Files.list(diffTreeRoot)) {
            emptyDiffTree = diffTreeFiles.findAny().isEmpty();
        }
        if (emptyDiffTree && deletedFiles.isEmpty() && movedFiles.isEmpty()) {
            throw new SelfUpdaterException("Diff is empty, because the given files are equal.");
        }
        // write meta-data for deleted and moved files
        deletedFiles.sort(null);
        Path deletedFilesMeta = workingDirectory.diffDataFiles.resolve(DiffFormatConstantsV1.META_DELETED);
        try (OutputStream outputStream = Files.newOutputStream(deletedFilesMeta)) {
            for (String line : deletedFiles) {
                outputStream.write(line.getBytes(StandardCharsets.UTF_8));
                outputStream.write('\n');
            }
        }
        Path movedFilesMeta = workingDirectory.diffDataFiles.resolve(DiffFormatConstantsV1.META_MOVED);
        movedFiles.sort(
                Comparator.comparing((Tuple2<String, String> a) -> a.a()).thenComparing(Tuple2::b));
        try (OutputStream outputStream = Files.newOutputStream(movedFilesMeta)) {
            for (Tuple2<String, String> filePair : movedFiles) {
                outputStream.write(
                        FileUtils.normalisedPathString(filePair.a(), false).getBytes(StandardCharsets.UTF_8));
                outputStream.write('\n');
                outputStream.write(
                        FileUtils.normalisedPathString(filePair.b(), false).getBytes(StandardCharsets.UTF_8));
                outputStream.write('\n');
            }
        }
        FileUtils.generateMandatoryMetaFiles(workingDirectory, DiffFormatConstantsV1.VERSION);
        Path result = outputDir.resolve(FileUtils.getStrippedFileName(oldJar) + "." + FileUtils.DIFF_FILE_TYPE);
        FileUtils.zipDir(workingDirectory.diffRoot, result);
        return result;
    }
}
