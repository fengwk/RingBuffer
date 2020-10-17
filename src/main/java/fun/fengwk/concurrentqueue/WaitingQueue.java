package fun.fengwk.concurrentqueue;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.LockSupport;

/**
 * 等待队列。
 * 
 * <p>
 * 在并发且无锁场景下使用类似{@link Object#wait()}和{@link Object#notify()}这样的同步机制可能是不安全的。<br/>
 * 例如下边的场景中的执行顺序可能是这样的：step1->step3->step4->step2。<br/>
 * 这样一来Thread1将错过Thread2的唤醒从而陷入永久的等待。
 * </p>
 * 
 * <pre>
 * init:
 *     condition = false
 * 
 * Thread1:
 *     while (!condition) {  // step1
 *         wait              // step2
 *     }
 *     
 * Thread2:
 *     condition = true    // step3
 *     notify              // step4
 * </pre>
 * 
 * <p>
 * {@link WaitingQueue}是为了避免上述情况而设计的，它将wait分为两个步骤{@link WaitingQueue#prepareWait}和{@link WaitingNode#await}。<br/>
 * 在condition检查前先使用prepareWait入队这个可能因检查失败而被挂起的线程Thread1，这样一来Thread1永远不会错过被Thread2唤醒的机会，当然实现中还需要依赖另一个安全挂起和唤醒的组件，例如{@link LockSupport}。
 * </p>
 * 
 * <pre>
 * init:
 *     boolean condition = false;
 *     WaitingQueue wq = new WaitingQueue();
 * 
 * Thread1:
 *     for (;;) {
 *         WaitingNode wn = wq.prepareWait();
 *         if (condition) {
 *             break;
 *         }
 *         wn.await();
 *     }
 * 
 * Thread2:
 *     condition = true;
 *     wq.signal();
 * </pre>
 * 
 * <p>
 * 语义保证：
 * <li>w表示调用挂起的线程数量。</li>
 * <li>s表示唤醒的线程数量。</li>
 * <li>x表示{@link WaitingNode#await}调用次数。</li>
 * <li>y表示{@link WaitingQueue#signal}调用次数。</li>
 * <li>{@link WaitingNode#await}调用，当前线程将挂起，x++，w++。</li>
 * <li>{@link WaitingQueue#signal}调用，若有已被挂起或已prepare将来会被挂起的线程，则保证必然能唤醒其中已被挂起或将来被挂起的最先者，已被唤醒过的线程除非重新调用{@link WaitingNode#await}否则不会被再次唤醒（在当前版本的实现中这个语义是逻辑上正确的），y++，s++。</li>
 * <li>若某一时刻x==y，则w==s。</li>
 * </p>
 * 
 * @author fengwk
 * @since 2020-09-25 12:37
 */
public class WaitingQueue {

    /**
     * 等待节点。
     * 
     * @author fengwk
     * @since 2020-09-25 12:38
     */
    public class WaitingNode {
        
        /**
         * 当前等待节点所属线程。
         */
        final Thread thread = Thread.currentThread();
        
        /**
         * parked值为true表明当前线程真的被（或即将被）挂起了。
         */
        volatile boolean parked;
        
        /**
         * 挂起当前线程直至收到一个当前节点prepare完成后的{@link WaitingQueue#signal()}通知。
         */
        public void await() throws InterruptedException {
            // 将parked设置为true表示当前节点真的要被挂起了
            parked = true;
            LockSupport.park(WaitingQueue.this);
            // 这里必须将parked重置为false
            // 因为signal的实现中可能会在之前出队并unpark无需await的当前线程的其它WaitingNode实例
            // 这样一来本次的park是没有效果的，这意味着当前WaitingNode实例可能还在队列中且parked为true
            // 一旦这样的情况重复发生（例如当前线程马上再次进行prepareWait和await）
            // 队列queue中就会出现多个的同一线程的WaitingNode实例且parked为true，队列queue的语义就会被破坏
            // 下面再分析一下parked重置操作执行顺序的影响
            // 1.若当前wn已被出队parked重置是无意义的同时也是无任何影响的
            // 2.一旦wn还在队列中且代码执行到此处一定说明当前线程之前接受过unpark且未park
            // 2.1.若当前wn还在队列中且parked重置先于出队发生，那么signal线程不会终止于当前wn
            // 2.2.若当前wn还在队列中且parked重置后于出队发生，且signal线程将终止于当前wn，但语义是正确的，因为当前线程确实await且被唤醒了，只不过是通过前一次唤醒实现的
            parked = false;
            
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
        }
        
    }
    
    /**
     * 使用无锁队列维护等待节点。
     * 
     * <p>
     * 该队列需要保证这样的语义：队列中可能会有同一线程的多个{@link WaitingNode}实例，但最多只有一个实例的parked为true。<br/>
     * 这个语义保证是重要的，一旦违背就可能出现不安全的情况。<br/>
     * 试想同一线程A的两个WaitingNode（下边简称wn）实例同时出现在队列中且它们的parked都为true。
     * 此刻有两个线程B和C同时执行了signal方法，它们分别在A的其中一个wn处停止，这样在B和C看来各自唤醒了一个等待中的线程，总计唤醒了两个线程，
     * 而事实上只有A被唤醒了，若还有一个D线程在等待唤醒，那么D就错过了唤醒机会继而陷入永久的等待。
     * </p>
     */
    private final ConcurrentLinkedQueue<WaitingNode> queue = new ConcurrentLinkedQueue<>();
    
    /**
     * 准备一个等待节点。
     * 
     * @return
     */
    public WaitingNode prepareWait() {
        WaitingNode wn = new WaitingNode();
        queue.offer(wn);
        return wn;
    }
    
    /**
     * 若有已被挂起或已prepare将来会被挂起的线程，则保证必然能唤醒其中已被挂起或将来被挂起的最先者，已被唤醒过的线程除非重新调用{@link WaitingNode#await}否则不会被再次唤醒。
     */
    public void signal() {
        WaitingNode wn;
        while ((wn = queue.poll()) != null) {
            LockSupport.unpark(wn.thread);
            // 唤醒首个确认被挂起的节点后结束循环
            if (wn.parked) {
                break;
            }
        }
    }

    /**
     * 获取当前队列元素数量。
     *
     * @return
     */
    public int size() {
        return queue.size();
    }

    /**
     * 检查当前队列是否包含元素。
     *
     * @return
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }
    
}
