package space.kepler_17c.selfupdater;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import static space.kepler_17c.selfupdater.TestUtils.RESOURCES;
import static space.kepler_17c.selfupdater.TestUtils.invokePrivateMethod;

public class ZipTest {
    private static final String TEST_DIR = "zip-extract";

    @Test
    public void zipExtractEquality() throws IOException {
        Path dir = RESOURCES.resolve(TEST_DIR);
        Path tmpDir = FileUtils.createTmpDir();
        FileUtils.zipDir(dir, tmpDir.resolve("test.jar"));
        invokePrivateMethod(FileUtils.class, "extractJar", new Class<?>[] {Path.class, Path.class}, new Object[] {
            tmpDir.resolve("test.jar"), tmpDir.resolve("extracted")
        });
        Assertions.assertTrue(FileUtils.equalDirectories(dir, tmpDir.resolve("extracted")));
        FileUtils.clearWorkingDirectory(tmpDir);
    }
}
