package space.kepler_17c.selfupdater;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static space.kepler_17c.selfupdater.TestUtils.RESOURCES;
import static space.kepler_17c.selfupdater.TestUtils.equalDirectories;
import static space.kepler_17c.selfupdater.TestUtils.generateRandomFileTree;
import static space.kepler_17c.selfupdater.TestUtils.invokePrivateMethod;

public class DiffV1Test {
    private static final long REPEATABLE_RANDOMNESS_SEED =
            ((long) "Self".hashCode() << (Long.SIZE / 2)) | "Updater".hashCode();
    private static final String SINGLE_EDIT_TEST_DIR = "diff-single-edit";
    private static final String SINGLE_MOVE_TEST_DIR = "diff-single-move";
    private static final String DIRS_ONLY_TEST_DIR = "diff-directories-only";
    private static final String ORIGINAL_FILE = "base.jar";
    private static final String DIFF_FILE = "diff.jar";
    private static final String UPDATED_FILE = "updated.jar";
    private static final String ORIGINAL_DIR = "baseDir";
    private static final String UPDATED_DIR = "updatedDir";
    private static final String EXTRACTED_DIR = "extracted";

    private Path tmpDir;

    @BeforeEach
    public void setup() throws IOException {
        tmpDir = FileUtils.createTmpDir();
    }

    @AfterEach
    public void cleanup() throws IOException {
        FileUtils.clearWorkingDirectory(tmpDir);
    }

    @Test
    public void createSingleEdit() throws IOException {
        regularCreateTest(SINGLE_EDIT_TEST_DIR);
    }

    @Test
    public void applySingleEdit() throws IOException {
        regularApplyTest(SINGLE_EDIT_TEST_DIR);
    }

    @Test
    public void createSingleMove() throws IOException {
        regularCreateTest(SINGLE_MOVE_TEST_DIR);
    }

    @Test
    public void applySingleMove() throws IOException {
        regularApplyTest(SINGLE_MOVE_TEST_DIR);
    }

    @Test
    public void createOnlyDirs() throws IOException {
        regularCreateTest(DIRS_ONLY_TEST_DIR);
    }

    @Test
    public void applyOnlyDirs() throws IOException {
        regularApplyTest(DIRS_ONLY_TEST_DIR);
    }

    private void regularCreateTest(String testDir) throws IOException {
        System.out.println("Running create test " + testDir + " in " + tmpDir.getFileName());
        Path generatedDiff = SelfUpdater.createDiff(
                RESOURCES.resolve(testDir).resolve(ORIGINAL_FILE),
                RESOURCES.resolve(testDir).resolve(UPDATED_FILE),
                tmpDir,
                DiffFormat.V1);
        Assertions.assertTrue(TestUtils.equalZipFiles(RESOURCES.resolve(testDir).resolve(DIFF_FILE), generatedDiff));
    }

    private void regularApplyTest(String testDir) throws IOException {
        System.out.println("Running apply test " + testDir + " in " + tmpDir.getFileName());
        Path generatedUpdate = (Path) invokePrivateMethod(
                SelfUpdater.class, "applyDiff", new Class<?>[] {Path.class, Path.class}, new Object[] {
                    RESOURCES.resolve(testDir).resolve(DIFF_FILE),
                    RESOURCES.resolve(testDir).resolve(ORIGINAL_FILE)
                });
        Assertions.assertTrue(
                TestUtils.equalZipFiles(RESOURCES.resolve(testDir).resolve(UPDATED_FILE), generatedUpdate));
    }

    @Test
    public void pseudoRandomBroadTest() throws IOException {
        Random random = new Random(REPEATABLE_RANDOMNESS_SEED);
        int treeGenerationSteps = 1 << 8;
        int maxFileSize = 1 << 4;
        int fileNameVariability = 1 << 7;
        int fileWeight = 5;
        int dirWeight = 3;
        int stepOutWeight = 2;
        // prepare directories
        Path tmpDir = FileUtils.createTmpDir();
        Files.createDirectories(tmpDir.resolve(ORIGINAL_DIR));
        Files.createDirectories(tmpDir.resolve(UPDATED_DIR));
        Files.createDirectories(tmpDir.resolve(EXTRACTED_DIR));
        // generate two separate file trees
        generateRandomFileTree(
                tmpDir.resolve(ORIGINAL_DIR),
                random,
                treeGenerationSteps,
                maxFileSize,
                fileNameVariability,
                fileWeight,
                dirWeight,
                stepOutWeight);
        generateRandomFileTree(
                tmpDir.resolve(UPDATED_DIR),
                random,
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
        Path generatedDiff = SelfUpdater.createDiff(
                tmpDir.resolve(ORIGINAL_FILE), tmpDir.resolve(UPDATED_FILE), tmpDir, DiffFormat.V1);
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
