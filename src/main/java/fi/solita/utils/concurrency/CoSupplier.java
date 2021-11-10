package fi.solita.utils.concurrency;

import java.util.function.Supplier;

public abstract class CoSupplier<OUT> extends Coroutine<Void,OUT> implements Supplier<OUT> {
    public CoSupplier() {
    }
    public CoSupplier(String name) {
        super(name);
    }
    
    public CoSupplier<OUT> start() throws AlreadyStartedException {
        doStart(null);
        return this;
    }
    
    public void yield_(OUT out) {
        doYield(out);
    }
    
    public OUT join() throws BlockingError, NotStartedException {
        return doJoin();
    }
    
    public OUT resume() throws AlreadyFinishedException {
        return doResume(null);
    }
    
    @Override
    OUT apply(Void in) {
        return get();
    }
}