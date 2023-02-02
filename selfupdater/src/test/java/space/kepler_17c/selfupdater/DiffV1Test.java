package space.kepler_17c.selfupdater;

import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static space.kepler_17c.selfupdater.TestUtils.RESOURCES;

public class DiffV1Test {
    private static final String SINGLE_EDIT_TEST_DIR = "diff-single-edit";
    private static final String SINGLE_MOVE_TEST_DIR = "diff-single-move";
    private static final String ORIGINAL_FILE = "base.jar";
    private static final String DIFF_FILE = "diff.jar";
    private static final String UPDATED_FILE = "updated.jar";

    private Path tmpDir;

    @BeforeEach
    public void setup() {
        tmpDir = FileUtils.createTmpDir();
    }

    @AfterEach
    public void cleanup() {
        FileUtils.clearWorkingDirectory(tmpDir);
    }

    @Test
    public void createSingleEdit() {
        Path generatedDiff = SelfUpdater.createDiff(
                RESOURCES.resolve(SINGLE_EDIT_TEST_DIR).resolve(ORIGINAL_FILE),
                RESOURCES.resolve(SINGLE_EDIT_TEST_DIR).resolve(UPDATED_FILE),
                tmpDir,
                DiffFormat.V1);
        Assertions.assertTrue(
                TestUtils.equalZipFiles(RESOURCES.resolve(SINGLE_EDIT_TEST_DIR).resolve(DIFF_FILE), generatedDiff));
    }

    @Test
    public void applySingleEdit() {}

    @Test
    public void createSingleMove() {
        Path generatedDiff = SelfUpdater.createDiff(
                RESOURCES.resolve(SINGLE_MOVE_TEST_DIR).resolve(ORIGINAL_FILE),
                RESOURCES.resolve(SINGLE_MOVE_TEST_DIR).resolve(UPDATED_FILE),
                tmpDir,
                DiffFormat.V1);
        Assertions.assertTrue(
                TestUtils.equalZipFiles(RESOURCES.resolve(SINGLE_MOVE_TEST_DIR).resolve(DIFF_FILE), generatedDiff));
    }

    @Test
    public void applySingleMove() {}
}
