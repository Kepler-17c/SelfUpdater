package space.kepler_17c.selfupdater;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static space.kepler_17c.selfupdater.TestUtils.RESOURCES;
import static space.kepler_17c.selfupdater.TestUtils.invokePrivateMethod;

public class DiffV1Test {
    private static final String SINGLE_EDIT_TEST_DIR = "diff-single-edit";
    private static final String SINGLE_MOVE_TEST_DIR = "diff-single-move";
    private static final String ORIGINAL_FILE = "base.jar";
    private static final String DIFF_FILE = "diff.jar";
    private static final String UPDATED_FILE = "updated.jar";

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
        Path generatedDiff = SelfUpdater.createDiff(
                RESOURCES.resolve(SINGLE_EDIT_TEST_DIR).resolve(ORIGINAL_FILE),
                RESOURCES.resolve(SINGLE_EDIT_TEST_DIR).resolve(UPDATED_FILE),
                tmpDir,
                DiffFormat.V1);
        Assertions.assertTrue(
                TestUtils.equalZipFiles(RESOURCES.resolve(SINGLE_EDIT_TEST_DIR).resolve(DIFF_FILE), generatedDiff));
    }

    @Test
    public void applySingleEdit() throws IOException {
        Path generatedUpdate = (Path) invokePrivateMethod(
                SelfUpdater.class, "applyDiff", new Class<?>[] {Path.class, Path.class}, new Object[] {
                    RESOURCES.resolve(SINGLE_EDIT_TEST_DIR).resolve(DIFF_FILE),
                    RESOURCES.resolve(SINGLE_EDIT_TEST_DIR).resolve(ORIGINAL_FILE)
                });
        Assertions.assertTrue(TestUtils.equalZipFiles(
                RESOURCES.resolve(SINGLE_EDIT_TEST_DIR).resolve(UPDATED_FILE), generatedUpdate));
    }

    @Test
    public void createSingleMove() throws IOException {
        Path generatedDiff = SelfUpdater.createDiff(
                RESOURCES.resolve(SINGLE_MOVE_TEST_DIR).resolve(ORIGINAL_FILE),
                RESOURCES.resolve(SINGLE_MOVE_TEST_DIR).resolve(UPDATED_FILE),
                tmpDir,
                DiffFormat.V1);
        Assertions.assertTrue(
                TestUtils.equalZipFiles(RESOURCES.resolve(SINGLE_MOVE_TEST_DIR).resolve(DIFF_FILE), generatedDiff));
    }

    @Test
    public void applySingleMove() throws IOException {
        Path generatedUpdate = (Path) invokePrivateMethod(
                SelfUpdater.class, "applyDiff", new Class<?>[] {Path.class, Path.class}, new Object[] {
                    RESOURCES.resolve(SINGLE_MOVE_TEST_DIR).resolve(DIFF_FILE),
                    RESOURCES.resolve(SINGLE_MOVE_TEST_DIR).resolve(ORIGINAL_FILE)
                });
        Assertions.assertTrue(TestUtils.equalZipFiles(
                RESOURCES.resolve(SINGLE_MOVE_TEST_DIR).resolve(UPDATED_FILE), generatedUpdate));
    }
}
