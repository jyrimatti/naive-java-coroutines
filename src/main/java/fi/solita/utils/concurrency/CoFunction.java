package fi.solita.utils.concurrency;

import java.util.function.Function;

public abstract class CoFunction<IN,OUT> extends Coroutine<IN,OUT> implements Function<IN,OUT> {
    public CoFunction() {
    }
    public CoFunction(String name) {
        super(name);
    }
    
    public void start(IN in) throws AlreadyStartedException {
        doStart(in);
    }
    
    public IN yield_(OUT out) {
        return doYield(out);
    }
    
    public OUT join() throws BlockingError, NotStartedException {
        return doJoin();
    }
    
    public OUT resume(IN in) throws AlreadyFinishedException {
        return doResume(in);
    }
}