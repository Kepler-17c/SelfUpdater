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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;
import space.kepler_17c.selfupdater.FileUtils.WorkingDirectory;
import space.kepler_17c.selfupdater.MiscUtils.Tuple2;

interface CreateDiff {
    Path createDiff(Path oldJar, Path newJar, Path outputDir) throws IOException;

    static Path v1(Path oldJar, Path newJar, Path outputDir) throws IOException {
        WorkingDirectory workingDirectory;
        if (oldJar == null
                || newJar == null
                || outputDir == null
                || (workingDirectory = FileUtils.prepareWorkingDirectory(oldJar, newJar, null)) == null) {
            return null;
        }
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
        Files.walkFileTree(oldJar, hashingFileVisitor);
        List<Tuple2<String, String>> movedFiles = new ArrayList<>();
        FileVisitor<Path> comparingFileVisitor = new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                List<Path> matchingFiles = hashedFilesMap.get(attrs.size());
                if (matchingFiles != null) {
                    for (Path p : matchingFiles) {
                        if (FileUtils.equalFiles(file, p)) {
                            String relSrc =
                                    workingDirectory.oldJar.relativize(p).toString();
                            String relDest =
                                    workingDirectory.newJar.relativize(file).toString();
                            movedFiles.add(new Tuple2<>(relSrc, relDest));
                            break;
                        }
                    }
                }
                return super.visitFile(file, attrs);
            }
        };
        Files.walkFileTree(newJar, comparingFileVisitor);
        Set<String> movedDestFiles = new HashSet<>();
        for (Tuple2<String, String> filePair : movedFiles) {
            movedDestFiles.add(filePair.b());
        }
        // check for deleted/changed/new files
        Stack<Path> oldFilesStack = new Stack<>();
        Stack<Path> newFilesStack = new Stack<>();
        FileUtils.pushFilesReversed(oldFilesStack, workingDirectory.oldJar);
        FileUtils.pushFilesReversed(newFilesStack, workingDirectory.newJar);
        Path oldFile;
        Path newFile;
        Path oldFileRel;
        Path newFileRel;
        Path diffFile;
        List<String> deletedFiles = new ArrayList<>();
        while (true) {
            // ensure top path denotes a file
            while (!oldFilesStack.isEmpty() && Files.isDirectory(oldFilesStack.peek())) {
                FileUtils.pushFilesReversed(oldFilesStack, oldFilesStack.pop());
            }
            while (!newFilesStack.isEmpty() && Files.isDirectory(newFilesStack.peek())) {
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
                    diffFile = workingDirectory.newJar.relativize(newFile);
                    if (!movedDestFiles.contains(diffFile.toString())) {
                        diffFile = workingDirectory.diffData.resolve(diffFile);
                        Files.copy(newFile, diffFile);
                    }
                    newFilesStack.pop();
                } else {
                    // only [new] is empty => mark all remaining as deleted
                    diffFile = workingDirectory.oldJar.relativize(oldFile);
                    deletedFiles.add(diffFile.toString());
                    oldFilesStack.pop();
                }
                continue;
            }
            // both stacks contain elements => compare files
            oldFileRel = workingDirectory.oldJar.relativize(oldFile);
            newFileRel = workingDirectory.newJar.relativize(newFile);
            String oldRelString = oldFileRel.toString();
            String newRelString = newFileRel.toString();
            diffFile = workingDirectory.diff.resolve("tree/").resolve(newFileRel);
            if (oldRelString.compareTo(newRelString) < 0) {
                // [old] is before [new] alphabetically => [new] skipped a file => mark as deleted
                deletedFiles.add(oldRelString);
                oldFilesStack.pop();
            } else if (oldRelString.compareTo(newRelString) > 0) {
                // [old] is after [new] alphabetically => [old] skipped a file => add new file
                if (!movedDestFiles.contains(newFileRel.toString())) {
                    Files.copy(newFile, diffFile);
                }
                newFilesStack.pop();
            } else if (oldRelString.equals(newRelString)) {
                // [old] and [new] have equal paths => compare files and add new if changed
                if (!movedDestFiles.contains(newFileRel.toString()) && !FileUtils.equalFiles(newFile, oldFile)) {
                    Files.copy(newFile, diffFile);
                }
                oldFilesStack.pop();
                newFilesStack.pop();
            } else {
                throw new IOException("Working directory is being modified by another thread.");
            }
        }
        Path deletedFilesMeta = workingDirectory.diffData.resolve("deletedFiles");
        try (OutputStream os = Files.newOutputStream(deletedFilesMeta)) {
            for (String line : deletedFiles) {
                os.write(line.getBytes(StandardCharsets.UTF_8));
                os.write('\n');
            }
        }
        Path movedFilesMeta = workingDirectory.diffData.resolve("movedFiles");
        try (OutputStream os = Files.newOutputStream(movedFilesMeta)) {
            for (Tuple2<String, String> filePair : movedFiles) {
                os.write(filePair.a().getBytes(StandardCharsets.UTF_8));
                os.write('\n');
                os.write(filePair.b().getBytes(StandardCharsets.UTF_8));
                os.write('\n');
            }
        }
        if (!FileUtils.generateMandatoryMetaFiles(workingDirectory, "1")) {
            return null;
        }
        Path result = outputDir.resolve(FileUtils.getStrippedFileName(oldJar) + "_updated.jar");
        FileUtils.zipJar(workingDirectory.diff, result);
        if (!workingDirectory.clear()) {
            return null;
        }
        return result;
    }
}
