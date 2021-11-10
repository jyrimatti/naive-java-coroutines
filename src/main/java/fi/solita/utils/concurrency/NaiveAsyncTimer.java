package fi.solita.utils.concurrency;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class NaiveAsyncTimer {
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    public <T> T block() {
        sleep(Long.MAX_VALUE, TimeUnit.DAYS);
        return null;
    }
    
    public void sleep(long duration, TimeUnit unit) {
        CoRunnable sleeper = new CoRunnable("Sleeper") {
            @Override
            public void run() {
                yield_();
            }
        };
        sleeper.start();
        scheduler.schedule(() -> Coroutine.start(() -> sleeper.resume()), duration, unit);
        sleeper.join();
    }
}