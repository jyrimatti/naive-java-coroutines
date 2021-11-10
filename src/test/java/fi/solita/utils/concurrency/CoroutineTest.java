package fi.solita.utils.concurrency;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.Assert;
import org.junit.Test;

import reactor.blockhound.BlockingOperationError;
import reactor.core.scheduler.Schedulers;

public class CoroutineTest {
    @Test
    public void blockHoundWorks() throws InterruptedException, TimeoutException {
        try {
            FutureTask<?> task = new FutureTask<>(() -> {
                Thread.sleep(0);
                return "";
            });
            Schedulers.parallel().schedule(task);

            task.get(10, TimeUnit.SECONDS);
            Assert.fail("should fail");
        } catch (ExecutionException e) {
            Assert.assertTrue("detected", e.getCause() instanceof BlockingOperationError);
        }
    }
    
    @Test
    public void yieldsAndResumesHappenInOrder() throws InterruptedException {
        List<Integer> steps = new ArrayList<>();
        Coroutine.start( () -> {
            steps.add(1);
            CoRunnable c = new CoRunnable() {
                @Override
                public void run() {
                    steps.add(3);
                    yield_();
                    steps.add(5);
                    yield_();
                    steps.add(7); // never gets here
                }
            };
            steps.add(2);
            c.resume();
            steps.add(4);
            c.resume();
            steps.add(6);
        }).join();
        
        assertEquals(List.of(1,2,3,4,5,6), steps);
    }
    
    @Test(expected = BlockingError.class)
    public void usingSystemOutShouldFail() {
        Coroutine.start( () -> {
            System.out.println("test");
        }).join();
    }
    
    @Test(expected = BlockingError.class)
    public void usingThreadSleepShouldFail() {
        Coroutine.start( () -> {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }).join();
    }
    
    @Test
    public void differentCoroutinesExecuteInOrderEvenWithSleeps() {
        List<Integer> steps = new ArrayList<>();
        Coroutine.start(() -> {
            CoRunnable c1 = new CoRunnable("looper1") {
                @Override
                public void run() {
                    for (int i = 0; i < 2; ++i) {
                        steps.add(1);
                        scheduler.sleep(10, TimeUnit.MILLISECONDS);
                        yield_();
                    }
                    steps.add(4);
                }
            };
            CoRunnable c2 = new CoRunnable("looper2") {
                @Override
                public void run() {
                    for (int i = 0; i < 2; ++i) {
                        steps.add(2);
                        scheduler.sleep(20, TimeUnit.MILLISECONDS);
                        yield_();
                    }
                    steps.add(5);
                }
            };
            CoRunnable c3 = new CoRunnable("looper3") {
                @Override
                public void run() {
                    for (int i = 0; i < 2; ++i) {
                        steps.add(3);
                        scheduler.sleep(30, TimeUnit.MILLISECONDS);
                        yield_();
                    }
                    steps.add(6);
                }
            };
            
            c1.resume();
            c2.resume();
            c3.resume();
            c1.resume();
            c2.resume();
            c3.resume();
            c1.resume();
            c2.resume();
            c3.resume();
                
            c1.join();
            c2.join();
            c3.join();
        }).join();
        
        assertEquals(List.of(1,2,3,1,2,3,4,5,6), steps);
    }
    
    @Test
    public void joinKeepsReturingReturnValue() {
        Coroutine.start(() -> {
            Generator<Integer> generator = new Generator<>() {
                @Override
                public Integer get() {
                    return 1;
                }
            };
            
            assertEquals(1, (int)generator.resume());
            
            assertEquals(1, (int)generator.join());
            assertEquals(1, (int)generator.join());
        }).join();
    }
    
    @Test
    public void doesntRunUntilFirstResume() {
        List<Integer> steps = new ArrayList<>();
        Coroutine.start(() -> {
            Generator<Integer> generator = new Generator<>() {
                @Override
                public Integer get() {
                    steps.add(1);
                    yield_(1);
                    steps.add(2);
                    return 2;
                }
            };
            assertEquals(List.of(), steps);
            generator.resume();
            assertEquals(List.of(1), steps);
            generator.resume();
            assertEquals(List.of(1,2), steps);
        }).join();
    }
    
    @Test
    public void lastResumeReturnsReturnValue() {
        Coroutine.start(() -> {
            Generator<Integer> generator = new Generator<>() {
                @Override
                public Integer get() {
                    yield_(1);
                    return 2;
                }
            };
            
            assertEquals(1, (int)generator.resume());
            assertEquals(2, (int)generator.resume());
        }).join();
    }
    
    @Test
    public void joinFailsOfNotStarted() {
        Coroutine.start(() -> {
            Generator<Integer> generator = new Generator<>() {
                @Override
                public Integer get() {
                    return 1;
                }
            };
            
            try {
                generator.join();
                fail("Should have failed");
            } catch (NotStartedException e) {
                // ok
            }
        }).join();
    }
    
    @Test
    public void testGenerator() {
        Coroutine.start(() -> {
            Generator<Integer> generator = new Generator<>() {
                @Override
                public Integer get() {
                    yield_(42);
                    for (int i = 1; i <= 3; i++) {
                        yield_(42+i);
                    }
                    return -1;
                }
            };
            
            assertEquals(42, (int)generator.resume());
            assertEquals(43, (int)generator.resume());
            assertEquals(44, (int)generator.resume());
            assertEquals(45, (int)generator.resume());
            assertEquals(-1, (int)generator.resume());
            assertEquals(-1, (int)generator.join());
        }).join();
    }

    @Test
    public void testCoConsumer() {
        List<Integer> steps = new ArrayList<>();
        Coroutine.start(() -> {
            CoConsumer<Integer> consumer = new CoConsumer<>() {
                @Override
                public void accept(Integer t) {
                    steps.add(t);
                    steps.add(yield_());
                    steps.add(yield_());
                }
            };
            
            consumer.resume(1);
            consumer.resume(2);
            consumer.resume(3);
            assertEquals(List.of(1,2,3), steps);
        }).join();
    }
    
    @Test
    public void testCoRunnable() {
        List<Integer> steps = new ArrayList<>();
        Coroutine.start(() -> {
            CoRunnable runnable = new CoRunnable() {
                @Override
                public void run() {
                    steps.add(1);
                    yield_();
                    steps.add(2);
                }
            };
            
            assertEquals(List.of(), steps);
            runnable.resume();
            assertEquals(List.of(1), steps);
            runnable.resume();
            assertEquals(List.of(1,2), steps);
            runnable.join();
        }).join();
    }
    
    @Test
    public void testCoFunction() {
        Coroutine.start(() -> {
            CoFunction<Character,String> function = new CoFunction<>() {
                @Override
                public String apply(Character t) {
                    Character res1 = yield_(t.toString().toUpperCase());
                    Character res2 = yield_(res1.toString().toUpperCase());
                    return res2.toString().toUpperCase();
                }
            };
            
            assertEquals("A", function.resume('a'));
            
            assertEquals("B", function.resume('b'));
            
            assertEquals("C", function.resume('c'));
            assertEquals("C", function.join());
        }).join();
    }
    
    Generator<String> fetch() {
        return new Generator<String>() {
            public String get() {
                Generator<String> self = this;
                Executors.newSingleThreadExecutor().execute(() -> Coroutine.start(() -> self.yield_("hello")));
                return scheduler.block();
            }
        }.start();
    }
    
    @Test
    public void testAsyncFetch() {
        List<String> data = new ArrayList<>();
        Coroutine.start(() -> {
            Generator<String> content1 = fetch();
            Generator<String> content2 = fetch();
            CoSupplier<Boolean> a = Coroutine.start(() -> data.add(content1.resume()));
            CoSupplier<Boolean> b = Coroutine.start(() -> data.add(content2.resume()));
            a.join();
            b.join();
        }).join();
        
        assertEquals(List.of("hello","hello"), data);
    }
}
