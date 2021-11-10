package fi.solita.utils.concurrency;

public abstract class CoRunnable extends Coroutine<Void,Void> implements Runnable {
    public CoRunnable() {
    }
    public CoRunnable(String name) {
        super(name);
    }
    
    public CoRunnable start() throws AlreadyStartedException {
        doStart(null);
        return this;
    }
    
    public void yield_() {
        doYield(null);
    }
    
    public void join() throws BlockingError, NotStartedException {
        doJoin();
    }
    
    public void resume() throws AlreadyFinishedException {
        doResume(null);
    }
    
    @Override
    Void apply(Void in) {
        run();
        return null;
    };
}