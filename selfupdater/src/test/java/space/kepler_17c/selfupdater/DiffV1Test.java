package space.kepler_17c.selfupdater;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static space.kepler_17c.selfupdater.TestUtils.DIFF_FILE;
import static space.kepler_17c.selfupdater.TestUtils.ORIGINAL_FILE;
import static space.kepler_17c.selfupdater.TestUtils.RESOURCES;
import static space.kepler_17c.selfupdater.TestUtils.UPDATED_FILE;
import static space.kepler_17c.selfupdater.TestUtils.invokePrivateMethod;

public class DiffV1Test {
    private static final String SINGLE_EDIT_TEST_DIR = "diff-v1-single-edit";
    private static final String SINGLE_MOVE_TEST_DIR = "diff-v1-single-move";
    private static final String DIRS_ONLY_TEST_DIR = "diff-v1-directories-only";

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
    public void noChangesDiff() {
        Path jar = RESOURCES.resolve(SINGLE_MOVE_TEST_DIR).resolve(ORIGINAL_FILE);
        Path diff = SelfUpdater.createDiff(jar, jar, tmpDir, DiffFormat.V1);
        Assertions.assertNull(diff);
    }
}
