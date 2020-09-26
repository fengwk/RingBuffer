package fun.fengwk.ringbuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

import org.junit.jupiter.api.Test;

/**
 * 
 * @author fengwk
 * @since 2020-09-26 13:11
 */
public class ArrayBlockingQueueTest {
    
    @Test
    public void test() throws InterruptedException {
        int epochCount = 1000;
        int enqueueThreadCount = 100;
        int enqueueCount = 100;
        int dequeueThreadCount = enqueueThreadCount;
        int dequeueCount = enqueueCount / 2;
        int queueCapacity = enqueueThreadCount * enqueueCount;
        
        long begin = System.currentTimeMillis();
        for (int epoch = 0; epoch < epochCount; epoch++) {
            ArrayBlockingQueue<Integer> queue = new ArrayBlockingQueue<>(queueCapacity);
            List<Thread> threads = new ArrayList<>();
            for (int j = 0; j < enqueueThreadCount; j++) {
                threads.add(new Thread(() -> enqueueRunner(queue, enqueueCount), "EnqueueThread"));
            }
            for (int j = 0; j < enqueueThreadCount; j++) {
                threads.add(new Thread(() -> dequeueRunner(queue, dequeueCount), "DequeueThread"));
            }
            for (Thread thread : threads) {
                thread.start();
            }
            for (Thread thread : threads) {
                thread.join();
            }
            if (queue.size() != enqueueThreadCount * enqueueCount - dequeueThreadCount * dequeueCount) {
                throw new AssertionError();
            }
            System.out.println("epoch " + epoch);
        }
        System.out.println(System.currentTimeMillis() - begin);
    }
    
    @Test
    public void testSmallCapacity() throws InterruptedException {
        int epochCount = 1000;
        int enqueueThreadCount = 100;
        int enqueueCount = 100;
        int dequeueThreadCount = enqueueThreadCount;
        int dequeueCount = enqueueCount;
        int queueCapacity = 1;
        
        long begin = System.currentTimeMillis();
        for (int epoch = 0; epoch < epochCount; epoch++) {
            ArrayBlockingQueue<Integer> queue = new ArrayBlockingQueue<>(queueCapacity);
            List<Thread> threads = new ArrayList<>();
            for (int j = 0; j < enqueueThreadCount; j++) {
                threads.add(new Thread(() -> enqueueRunner(queue, enqueueCount), "EnqueueThread"));
            }
            for (int j = 0; j < enqueueThreadCount; j++) {
                threads.add(new Thread(() -> dequeueRunner(queue, dequeueCount), "DequeueThread"));
            }
            for (Thread thread : threads) {
                thread.start();
            }
            for (Thread thread : threads) {
                thread.join();
            }
            if (queue.size() != enqueueThreadCount * enqueueCount - dequeueThreadCount * dequeueCount) {
                throw new AssertionError();
            }
            System.out.println("Concurrent " + epoch);
        }
        System.out.println(System.currentTimeMillis() - begin);
    }
    
    private void enqueueRunner(ArrayBlockingQueue<Integer> queue, int enqueueCount) {
        for (int i = 0; i < enqueueCount; i++) {
            try {
                queue.put(i);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
    private void dequeueRunner(ArrayBlockingQueue<Integer> queue, int dequeueCount) {
        for (int i = 0; i < dequeueCount; i++) {
            try {
                queue.take();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

}
