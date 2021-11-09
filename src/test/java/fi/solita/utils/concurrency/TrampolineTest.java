package fi.solita.utils.concurrency;

import java.math.BigInteger;

import org.junit.Test;

public class TrampolineTest {
    static BigInteger factorialWithoutTrampolining(long x) {
        return x <= 1 ? BigInteger.ONE :
                        BigInteger.valueOf(x).multiply(factorialWithoutTrampolining(x - 1));
    }
    
    @Test(expected = StackOverflowError.class)
    public void testWithoutTrampolining() {
        System.out.println(factorialWithoutTrampolining(50000));
    }
    
    static Trampoline<BigInteger> factorialWithTrampolining(long x, BigInteger result) {
        return x <= 1 ? Trampoline.finished(result) :
                        Trampoline.step(() -> factorialWithTrampolining(x-1, BigInteger.valueOf(x).multiply(result)));
    }
    
    @Test
    public void testWithTrampolining() {
        System.out.println(factorialWithTrampolining(50000, BigInteger.ONE).get());
    }
}
