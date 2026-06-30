package com.sft;

import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

/**
 * Smoke test: does the actual app come up? Boots the real JavaFX toolkit, runs
 * the whole App.start() (track, clones, CSS load, drawer wiring, stage.show()) -- will change this, this is overkill.
 * and passes only if nothing threw. This is the "app is created and running"
 * gate — not a logic test. For testing specific states, test the extracted
 * pieces (e.g. Carousel) directly instead of booting the app here.
 *
 * Uses whatever display is present — the same one javafx:run needs — so no
 * TestFX / Monocle, no headless version-matching. Needs a screen; briefly
 * flashes a window.
 */
class AppSmokeTest {

    @Test
    void appBootsWithoutThrowing() throws Exception {
        // 1. Start the FX toolkit. The callback fires once the FX thread is live.
        //    Platform.startup() skips the "JavaFX components missing" check that
        //    bites a bare `java` launch, so this works on the test classpath.
        CountDownLatch toolkitUp = new CountDownLatch(1); // (CountDownLatch ~ sleep -> launch)
        // toolkitUp object:
        Platform.startup(toolkitUp::countDown); // testing platform?
        toolkitUp.await(10, TimeUnit.SECONDS);

        // 2. Build + show the real app on the FX thread (scene work must run there).
        //    Anything start() throws — missing CSS, bad drawer cast, empty pages —
        //    is captured instead of dying silently on the FX thread.
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                new App().start(new Stage());
            } catch (Throwable t) {
                error.set(t);
            } finally {
                done.countDown();
            }
        });
        done.await(10, TimeUnit.SECONDS);
        Platform.exit(); // tear the toolkit down so the JVM exits clean

        // 3. The gate: start() finished with no exception → app created and running.
        assertNull(error.get(), "App.start() threw: " + error.get()); // check error log. cool java
    }
    // new tests here....
}
