package fi.solita.utils.concurrency;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;

class NaiveAsyncOutputStream extends OutputStream implements Runnable {
    private final OutputStream out;
    private final BlockingQueue<byte[]> pending = new LinkedTransferQueue<>();
    
    public NaiveAsyncOutputStream(OutputStream out) {
        this.out = out;
        Executors.newSingleThreadExecutor().execute(this);
    }
        
    @Override
    public void run() {
        Thread.currentThread().setName("Thread-NaiveAsyncOutputStream");
        while (true) {
            try {
                byte[] elem = pending.poll(1, TimeUnit.MINUTES);
                if (elem != null) {
                    out.write(elem);
                }
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
    
    @Override
    public void write(int b) throws IOException {
        this.write(BigInteger.valueOf(b).toByteArray());
    }
    
    @Override
    public void write(byte[] b) throws IOException {
        pending.add(b);
    }
    
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        this.write(Arrays.copyOfRange(b, off, len));
    }
}