package space.kepler_17c.selfupdater;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import static space.kepler_17c.selfupdater.TestUtils.RESOURCES;

public class DiffV1Test {
    private static final String TEST_DIR = "diff-file-edit";
    private static final String ORIGINAL_FILE = "base.jar";
    private static final String DIFF_FILE = "diff.jar";
    private static final String UPDATED_FILE = "updated.jar";

    @Test
    public void singleFileEditTest() throws IOException {
        Path tmpDir = FileUtils.createTmpDir();
        Assertions.assertNotNull(tmpDir);
        Path generatedDiff = SelfUpdater.createDiff(
                RESOURCES.resolve(TEST_DIR).resolve(ORIGINAL_FILE),
                RESOURCES.resolve(TEST_DIR).resolve(UPDATED_FILE),
                tmpDir,
                DiffFormat.V1);
        Assertions.assertTrue(
                TestUtils.equalZipFiles(RESOURCES.resolve(TEST_DIR).resolve(DIFF_FILE), generatedDiff));
        FileUtils.clearWorkingDirectory(tmpDir);
    }
}
