package space.kepler_17c.selfupdater;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public enum UpdaterEvent {
    RECEIVED_DIFF,
    EXTRACTED_DATA,
    CHECKED_VERSION,
    VERIFIED_HASHES,
    APPLIED_DIFF,
    PACKED_EXECUTABLE,
    VERIFIED_UPDATED_FILES;

    private static final Map<UpdaterEvent, Set<Consumer<Boolean>>> REGISTERED_CALLBACKS = new HashMap<>();

    static {
        for (UpdaterEvent event : UpdaterEvent.values()) {
            REGISTERED_CALLBACKS.put(event, new HashSet<>());
        }
    }

    static void triggerEvent(UpdaterEvent updaterEvent, boolean value) {
        if (value) {
            REGISTERED_CALLBACKS.get(updaterEvent).forEach(e -> e.accept(true));
        } else {
            UpdaterEvent[] events = UpdaterEvent.values();
            int start = Arrays.asList(events).indexOf(updaterEvent);
            for (int i = start; i < events.length; i++) {
                REGISTERED_CALLBACKS.get(events[i]).forEach(e -> e.accept(false));
            }
        }
    }

    public static void subscribeEvent(UpdaterEvent updaterEvent, Consumer<Boolean> callback) {
        REGISTERED_CALLBACKS.get(updaterEvent).add(callback);
    }

    public static void unsubscribeFrom(UpdaterEvent updaterEvent, Consumer<Boolean> callback) {
        REGISTERED_CALLBACKS.get(updaterEvent).remove(callback);
    }
}
