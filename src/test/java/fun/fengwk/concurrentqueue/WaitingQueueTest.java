package fun.fengwk.concurrentqueue;

import org.junit.jupiter.api.Test;

import fun.fengwk.concurrentqueue.WaitingQueue.WaitingNode;

/**
 * 
 * @author fengwk
 * @since 2020-09-26 13:09
 */
public class WaitingQueueTest {
    
    volatile boolean condition = false;
    
    /**
     * for testing
     */
    final WaitingQueue wq = new WaitingQueue();

    @Test
    public void test() throws InterruptedException {
        
        Thread thread1 = new Thread(() -> thread1Runner());
        Thread thread2 = new Thread(() -> thread2Runner());
        
        thread1.start();
        thread2.start();
        
        thread1.join();
        thread2.join();
    }
    
    private void thread1Runner() {
        for (;;) {
            WaitingNode wn = wq.prepareWait();
            if (condition) {
                break;
            }
            try {
                Thread.sleep(2000);
                wn.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.out.println("thread1 end");
    }
    
    private void thread2Runner() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        condition = true;
        wq.signal();
        System.out.println("thread2 end");
    }
    
}
