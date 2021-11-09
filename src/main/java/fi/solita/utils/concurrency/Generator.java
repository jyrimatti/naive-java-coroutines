package fi.solita.utils.concurrency;

public abstract class Generator<OUT> extends CoSupplier<OUT> {
    public Generator() {
    }
    public Generator(String name) {
        super(name);
    }
}