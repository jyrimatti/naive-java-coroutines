package fi.solita.utils.concurrency;

import java.util.function.Supplier;

public class Trampoline<T> {
    private final T t;
    private final Supplier<Trampoline<T>> f;
    
    public Trampoline(T t) {
        this.t = t;
        this.f = null;
    }
    
    public Trampoline(Supplier<Trampoline<T>> f) {
        this.t = null;
        this.f = f;
    }
    
    public T get() {
        Trampoline<T> next = this;
        while (true) {
            if (next.t != null) {
                return next.t;
            }
            next = next.f.get();
        }
    }
    
    public static <T> Trampoline<T> step(Supplier<Trampoline<T>> f) {
        return new Trampoline<>(f);
    }
    
    public static <T> Trampoline<T> finished(T t) {
        return new Trampoline<>(t);
    }
}