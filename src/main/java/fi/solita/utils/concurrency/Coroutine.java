package fi.solita.utils.concurrency;

import java.io.PrintStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import reactor.blockhound.BlockHound;
import reactor.blockhound.BlockHound.Builder;
import reactor.blockhound.BlockingOperationError;
import reactor.core.scheduler.ReactorBlockHoundIntegration;

public abstract class Coroutine<IN,OUT> {
    static {
        Builder builder = BlockHound.builder()
            .nonBlockingThreadPredicate(current -> current.or(it -> (it instanceof ForkJoinWorkerThread)))
            .allowBlockingCallsInside("java.util.concurrent.ForkJoinPool", "awaitWork")
            .allowBlockingCallsInside("java.util.concurrent.ForkJoinTask", "awaitDone");
        
        new ReactorBlockHoundIntegration().applyTo(builder);
        builder.install();
    }
    
    
    
    public static final PrintStream SystemOut = new PrintStream(new NaiveAsyncOutputStream(System.out));
    
    public static final NaiveAsyncTimer scheduler = new NaiveAsyncTimer();
    
    public static <OUT> CoSupplier<OUT> start(Supplier<OUT> supplier) {
        CoSupplier<OUT> c = new CoSupplier<>() {
            @Override
            public OUT get() {
                try {
                    assertPool();
                    return supplier.get();
                } catch (RuntimeException e) {
                    super.releaseLocks(e);
                    throw e;
                } finally {
                    super.resumeLock.complete(null);
                }
            }
        };
        c.doStart(null);
        return c;
    }
    
    public static CoRunnable start(Runnable runnable) {
        CoRunnable c = new CoRunnable() {
            @Override
            public void run() {
                try {
                    assertPool();
                    runnable.run();
                } catch (RuntimeException e) {
                    super.releaseLocks(e);
                    throw e;
                } finally {
                    super.resumeLock.complete(null);
                }
            }
        };
        c.doStart(null);
        return c;
    }
    
    public static <IN> CoConsumer<IN> start(IN in, Consumer<IN> function) {
        CoConsumer<IN> c = new CoConsumer<>() {
            @Override
            public void accept(IN in) {
                try {
                    assertPool();
                    function.accept(in);
                } catch (RuntimeException e) {
                    super.releaseLocks(e);
                    throw e;
                } finally {
                    super.resumeLock.complete(null);
                }
            }
        };
        c.doStart(in);
        return c;
    }
    
    public static <IN,OUT> CoFunction<IN,OUT> start(IN in, Function<IN,OUT> function) {
        CoFunction<IN,OUT> c = new CoFunction<>() {
            @Override
            public OUT apply(IN in) {
                try {
                    assertPool();
                    return function.apply(in);
                } catch (RuntimeException e) {
                    super.releaseLocks(e);
                    throw e;
                } finally {
                    super.resumeLock.complete(null);
                }
            }
        };
        c.doStart(in);
        return c;
    }
    
    private static final ForkJoinPool pool = new ForkJoinPool(1);
    
    private static final AtomicReference<Map.Entry<Coroutine<?,?>,String>> running = new AtomicReference<>();
    
    static void assertPool() {
        if (!ForkJoinTask.inForkJoinPool()) {
            throw new IllegalStateException("Not a ForkJoin Thread");
        }
        if (ForkJoinTask.getPool() != pool) {
            throw new IllegalStateException("Running in wrong pool");
        }
    }
    
    private static void assertRunning(Coroutine<?,?> c, boolean set) {
        if (!ForkJoinTask.inForkJoinPool()) {
            // skip if called outside coroutines
            return;
        }
        
        Map.Entry<Coroutine<?, ?>, String> previous = running.get();
        while (previous != null && set) {
            Thread.yield();
            previous = running.get();
        }
        previous = running.getAndSet(set ? new Map.Entry<>() {
            private String threadName = Thread.currentThread().getName();
            @Override
            public Coroutine<?, ?> getKey() {
                return c;
            }
            public String getValue() {
                return threadName;
            }
            @Override
            public String setValue(String value) {
                throw new UnsupportedOperationException();
            };
        } : null);
        if (previous != null && set) {
            throw new IllegalStateException("Cannot run: " + c + "(" + Thread.currentThread().getName() + ") since " + previous + " was already running! Running: " + pool.getRunningThreadCount() + ", Active: " + pool.getActiveThreadCount());
        }
    }
    
    @Override
    public String toString() {
        return name.map(x -> "Coroutine(" + state + "):" + x).orElse(super.toString());
    }
    
    private enum State {
        UNINITIALIZED,
        IN_RESUME,
        IN_JOIN,
        IN_START,
        IN_YIELD,
        OTHER
    }
    
    private ForkJoinTask<OUT> task;

    ForkJoinTask<IN> yieldLock;
    ForkJoinTask<OUT> resumeLock;
    
    private final AtomicReference<State> state = new AtomicReference<>(State.UNINITIALIZED);
    private final AtomicBoolean finished = new AtomicBoolean(false);
    private final Optional<String> name;
    
    public Coroutine() {
        this.name = Optional.empty();
    }
    public Coroutine(String name) {
        this.name = Optional.of(name);
    }
    
    public final boolean isStarted() {
        return task != null;
    }
    
    public final boolean isFinished() {
        return finished.get();
    }
    
    void releaseLocks(RuntimeException e) {
        Coroutine.SystemOut.println("release locks");
        e.printStackTrace();
        if (resumeLock != null) {
            resumeLock.complete(null);
        }
        if (yieldLock != null) {
            yieldLock.complete(null);
        }
    }
    
    IN doYield(OUT out) {
        state.set(State.IN_YIELD);
        try {
            assertRunning(this, false);
            assertPool();
            yieldLock = ForkJoinTask.adapt(() -> { throw new IllegalStateException("Shouldn't be here"); });
            resumeLock.complete(out);
            IN ret = yieldLock.join();
            assertRunning(this, true);
            return ret;
        } catch (RuntimeException e) {
            releaseLocks(e);
            throw e;
        } finally {
            state.set(State.OTHER);
        }
    }
    
    void doStart(IN in) throws AlreadyStartedException {
        state.set(State.IN_START);
        try {
            if (isStarted()) {
                throw new AlreadyStartedException();
            }
            finished.set(false);
            resumeLock = ForkJoinTask.adapt(() -> { throw new IllegalStateException("Shouldn't be here"); });
            task = pool.submit(() -> {
                assertRunning(Coroutine.this, true);
                try {
                    return Coroutine.this.apply(in);
                } finally {
                    assertRunning(Coroutine.this, false);
                    finished.set(true);
                }
            });
        } finally {
            state.set(State.OTHER);
        }
    }
    
    OUT doJoin() throws NotStartedException {
        if (!isStarted()) {
            throw new NotStartedException();
        }
        
        state.set(State.IN_JOIN);
        try {
            assertRunning(this, false);
            try {
                return task.join();
            } catch (BlockingOperationError e) {
                // catch and throw to get the whole stacktrace
                throw new BlockingError(e);
            } catch (BlockingError e) {
                // catch and throw to get the whole stacktrace
                throw new BlockingError(e);
            } finally {
                assertRunning(this, true);
            }
        } finally {
            state.set(State.OTHER);
        }
    }
    
    OUT doResume(IN in) throws AlreadyFinishedException {
        state.set(State.IN_RESUME);
        try {
            if (isFinished()) {
                throw new AlreadyFinishedException();
            }
            assertRunning(this, false);
            resumeLock = ForkJoinTask.adapt(() -> { throw new IllegalStateException("Shouldn't be here"); });
            boolean started = isStarted();
            if (!started) {
                task = pool.submit(() -> {
                    assertRunning(Coroutine.this, true);
                    OUT result = null;
                    try {
                        result = Coroutine.this.apply(in);
                        return result;
                    } finally {
                        assertRunning(Coroutine.this, false);
                        finished.set(true);
                        resumeLock.complete(result);
                    }
                });
            }
            if (yieldLock != null) {
                yieldLock.complete(in);
            }
            
            OUT ret = resumeLock.join();
            if (started) {
                assertRunning(this, true);
            }
            return ret;
        } finally {
            state.set(State.OTHER);
        }
    }
    
    abstract OUT apply(IN in);
}
