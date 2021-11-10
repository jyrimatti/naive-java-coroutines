package fi.solita.utils.concurrency;

public abstract class Generator<OUT> extends CoSupplier<OUT> {
    public Generator() {
    }
    public Generator(String name) {
        super(name);
    }
    @Override
    public Generator<OUT> start() throws AlreadyStartedException {
        super.start();
        return this;
    }
}