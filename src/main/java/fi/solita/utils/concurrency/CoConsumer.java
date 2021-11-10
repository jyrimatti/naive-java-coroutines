package fi.solita.utils.concurrency;

import java.util.function.Consumer;

public abstract class CoConsumer<IN> extends Coroutine<IN,Void> implements Consumer<IN> {
    public CoConsumer() {
    }
    public CoConsumer(String name) {
        super(name);
    }
    
    public CoConsumer<IN> start(IN in) throws AlreadyStartedException {
        doStart(in);
        return this;
    }
    
    public IN yield_() {
        return doYield(null);
    }
    
    public void join() throws BlockingError, NotStartedException {
        doJoin();
    }
    
    public void resume(IN in) throws AlreadyFinishedException {
        doResume(in);
    }
    
    @Override
    Void apply(IN in) {
        accept(in);
        return null;
    }
}