package space.kepler_17c.selfupdater;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static space.kepler_17c.selfupdater.TestUtils.EXTRACTED_DIR;
import static space.kepler_17c.selfupdater.TestUtils.ORIGINAL_DIR;
import static space.kepler_17c.selfupdater.TestUtils.ORIGINAL_FILE;
import static space.kepler_17c.selfupdater.TestUtils.UPDATED_DIR;
import static space.kepler_17c.selfupdater.TestUtils.UPDATED_FILE;
import static space.kepler_17c.selfupdater.TestUtils.equalDirectories;
import static space.kepler_17c.selfupdater.TestUtils.generateRandomFileTree;
import static space.kepler_17c.selfupdater.TestUtils.invokePrivateMethod;

public class FullAutoBroadTest {
    private static final long REPEATABLE_RANDOMNESS_SEED =
            ((long) "Self".hashCode() << (Long.SIZE / 2)) | "Updater".hashCode();
    private static final Random RANDOM = new Random(REPEATABLE_RANDOMNESS_SEED);

    @Test
    public void pseudoRandomBroadTest() throws IOException {
        // dir gen params
        int treeGenerationSteps = 1 << 8;
        int maxFileSize = 1 << 4;
        int fileNameVariability = 1 << 7;
        int fileWeight = 5;
        int dirWeight = 3;
        int stepOutWeight = 2;
        // test params
        int runsPerDiffFormat = 8;
        // run tests
        for (DiffFormat diffFormat : DiffFormat.values()) {
            for (int i = 0; i < runsPerDiffFormat; i++) {
                randomisedDirTest(
                        treeGenerationSteps,
                        maxFileSize,
                        fileNameVariability,
                        fileWeight,
                        dirWeight,
                        stepOutWeight,
                        diffFormat);
            }
        }
    }

    public void randomisedDirTest(
            int treeGenerationSteps,
            int maxFileSize,
            int fileNameVariability,
            int fileWeight,
            int dirWeight,
            int stepOutWeight,
            DiffFormat diffFormat)
            throws IOException {
        // prepare directories
        Path tmpDir = FileUtils.createTmpDir();
        Files.createDirectories(tmpDir.resolve(ORIGINAL_DIR));
        Files.createDirectories(tmpDir.resolve(UPDATED_DIR));
        Files.createDirectories(tmpDir.resolve(EXTRACTED_DIR));
        // generate two separate file trees
        generateRandomFileTree(
                tmpDir.resolve(ORIGINAL_DIR),
                RANDOM,
                treeGenerationSteps,
                maxFileSize,
                fileNameVariability,
                fileWeight,
                dirWeight,
                stepOutWeight);
        generateRandomFileTree(
                tmpDir.resolve(UPDATED_DIR),
                RANDOM,
                treeGenerationSteps,
                maxFileSize,
                fileNameVariability,
                fileWeight,
                dirWeight,
                stepOutWeight);
        // zip both to run diff on them
        FileUtils.zipDir(tmpDir.resolve(ORIGINAL_DIR), tmpDir.resolve(ORIGINAL_FILE));
        FileUtils.zipDir(tmpDir.resolve(UPDATED_DIR), tmpDir.resolve(UPDATED_FILE));
        // create and apply diff
        Path generatedDiff =
                SelfUpdater.createDiff(tmpDir.resolve(ORIGINAL_FILE), tmpDir.resolve(UPDATED_FILE), tmpDir, diffFormat);
        Path generatedUpdate = (Path) invokePrivateMethod(
                SelfUpdater.class, "applyDiff", new Class<?>[] {Path.class, Path.class}, new Object[] {
                    generatedDiff, tmpDir.resolve(ORIGINAL_FILE)
                });
        // extract update and compare with original
        invokePrivateMethod(FileUtils.class, "extractJar", new Class<?>[] {Path.class, Path.class}, new Object[] {
            generatedUpdate, tmpDir.resolve(EXTRACTED_DIR)
        });
        equalDirectories(tmpDir.resolve(UPDATED_DIR), tmpDir.resolve(EXTRACTED_DIR));
    }
}
