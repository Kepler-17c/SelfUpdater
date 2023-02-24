package space.kepler_17c.selfupdater;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import space.kepler_17c.selfupdater.MiscUtils.Tuple2;
import static space.kepler_17c.selfupdater.TestUtils.DIFF_FILE;
import static space.kepler_17c.selfupdater.TestUtils.ORIGINAL_FILE;
import static space.kepler_17c.selfupdater.TestUtils.RESOURCES;
import static space.kepler_17c.selfupdater.TestUtils.UPDATED_FILE;
import static space.kepler_17c.selfupdater.TestUtils.invokePrivateMethod;

public class UpdateEventTest {
    @Test
    public void successfulUpdateTest() throws IOException {
        Path tmpDir = FileUtils.createTmpDir();
        String testDir = "diff-v1-single-move";
        System.out.println("Running update event test using " + testDir + " in " + tmpDir.getFileName());
        List<Tuple2<UpdaterEvent, Boolean>> receivedEvents = new ArrayList<>();
        for (final UpdaterEvent event : UpdaterEvent.values()) {
            UpdaterEvent.subscribeEvent(event, (success) -> receivedEvents.add(new Tuple2<>(event, success)));
        }
        Path generatedUpdate = (Path) invokePrivateMethod(
                SelfUpdater.class, "applyDiff", new Class<?>[] {Path.class, Path.class}, new Object[] {
                    RESOURCES.resolve(testDir).resolve(DIFF_FILE),
                    RESOURCES.resolve(testDir).resolve(ORIGINAL_FILE)
                });
        Assertions.assertEquals(UpdaterEvent.values().length, receivedEvents.size());
        for (int i = 0; i < UpdaterEvent.values().length; i++) {
            Assertions.assertEquals(
                    UpdaterEvent.values()[i], receivedEvents.get(i).a());
            Assertions.assertTrue(receivedEvents.get(i).b());
        }
        for (Tuple2<UpdaterEvent, Boolean> pair : receivedEvents) {
            System.out.println(pair.a() + " -> " + pair.b());
        }
        Assertions.assertTrue(
                TestUtils.equalZipFiles(RESOURCES.resolve(testDir).resolve(UPDATED_FILE), generatedUpdate));
        assert true;
    }
}
