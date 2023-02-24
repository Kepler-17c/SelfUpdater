package space.kepler_17c.selfupdater;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import space.kepler_17c.selfupdater.FileUtils.WorkingDirectory;
import space.kepler_17c.selfupdater.MiscUtils.Tuple2;

interface ApplyDiff {
    Path applyDiff(WorkingDirectory workingDirectory) throws SelfUpdaterException;

    static Path v1(WorkingDirectory workingDirectory) throws SelfUpdaterException {
        // copy source except for deleted files
        Path deletedFilesPath = workingDirectory.diffDataFiles.resolve(DiffFormatConstantsV1.META_DELETED);
        Set<String> deletedFiles = new HashSet<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(Files.newInputStream(deletedFilesPath)))) {
            String line;
            while ((line = br.readLine()) != null) {
                deletedFiles.add(line);
            }
        } catch (IOException e) {
            UpdaterEvent.triggerEvent(UpdaterEvent.APPLIED_DIFF, false);
            throw new SelfUpdaterException("Failed to read `deletedFiles`.", e);
        }
        FileVisitor<Path> filteredCopyVisitor = new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relDir = workingDirectory.oldFiles.relativize(dir);
                if (!deletedFiles.contains(FileUtils.normalisedPathString(relDir, true))) {
                    Files.createDirectories(workingDirectory.newFiles.resolve(relDir));
                }
                return super.preVisitDirectory(dir, attrs);
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relFile = workingDirectory.oldFiles.relativize(file);
                if (!deletedFiles.contains(FileUtils.normalisedPathString(relFile, false))) {
                    Files.copy(file, workingDirectory.newFiles.resolve(relFile));
                }
                return super.visitFile(file, attrs);
            }
        };
        try {
            Files.walkFileTree(workingDirectory.oldFiles, filteredCopyVisitor);
        } catch (IOException e) {
            UpdaterEvent.triggerEvent(UpdaterEvent.APPLIED_DIFF, false);
            throw new SelfUpdaterException("Failed to copy files from source.", e);
        }
        // copy new/changed files
        Path diffChangedFilesDir = workingDirectory.diffDataFiles.resolve(DiffFormatConstantsV1.DATA_DIR);
        FileVisitor<Path> diffCopyVisitor = new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path copyTo = workingDirectory.newFiles.resolve(diffChangedFilesDir.relativize(dir));
                Files.createDirectories(copyTo);
                return super.preVisitDirectory(dir, attrs);
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path copyTo = workingDirectory.newFiles.resolve(diffChangedFilesDir.relativize(file));
                Files.copy(file, copyTo, StandardCopyOption.REPLACE_EXISTING);
                return super.visitFile(file, attrs);
            }
        };
        try {
            Files.walkFileTree(diffChangedFilesDir, diffCopyVisitor);
        } catch (IOException e) {
            UpdaterEvent.triggerEvent(UpdaterEvent.APPLIED_DIFF, false);
            throw new SelfUpdaterException("Failed to copy added/changed files.", e);
        }
        // copy moved files
        Path movedFilesPath = workingDirectory.diffDataFiles.resolve(DiffFormatConstantsV1.META_MOVED);
        List<Tuple2<String, String>> movedFiles = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(Files.newInputStream(movedFilesPath)))) {
            String lineFrom, lineTo;
            while ((lineFrom = br.readLine()) != null && (lineTo = br.readLine()) != null) {
                movedFiles.add(new Tuple2<>(lineFrom, lineTo));
            }
        } catch (IOException e) {
            UpdaterEvent.triggerEvent(UpdaterEvent.APPLIED_DIFF, false);
            throw new SelfUpdaterException("Failed to read `movedFiles`.", e);
        }
        Path movedFrom, movedTo;
        for (Tuple2<String, String> move : movedFiles) {
            movedFrom = workingDirectory.oldFiles.resolve(move.a());
            movedTo = workingDirectory.newFiles.resolve(move.b());
            try {
                Files.createDirectories(movedTo.getParent());
                Files.copy(movedFrom, movedTo);
            } catch (IOException e) {
                UpdaterEvent.triggerEvent(UpdaterEvent.APPLIED_DIFF, false);
                throw new SelfUpdaterException("Failed to copy moved files from source.", e);
            }
        }
        UpdaterEvent.triggerEvent(UpdaterEvent.APPLIED_DIFF, true);
        Path resultPath = workingDirectory.rootDir.resolve(FileUtils.UPDATED_FILE_NAME);
        try {
            FileUtils.zipDir(workingDirectory.newFiles, resultPath);
            UpdaterEvent.triggerEvent(UpdaterEvent.PACKED_EXECUTABLE, true);
        } catch (IOException e) {
            UpdaterEvent.triggerEvent(UpdaterEvent.PACKED_EXECUTABLE, false);
            throw new SelfUpdaterException("Failed to re-zip updated files.", e);
        }
        return resultPath;
    }
}
