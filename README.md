# RingBuffer

这是一个用于学习如何实现环形缓冲区的Java类库，提供了无锁的环形缓冲阻塞队列和无锁模式下安全的等待队列实现。

*This is a Java class library for learning how to implement ring buffers. It provides a lock-free ring buffer blocking queue and a safe waiting queue in lock-free mode.*

# Introduction

## WaitingQueue

在并发且无锁场景下使用类似Object.wait()和Object.notify()这样的同步机制可能是不安全的。
例如下边的场景中的执行顺序可能是这样的：step1->step3->step4->step2。
这样一来Thread1将错过Thread2的唤醒从而陷入永久的等待。

*It may be unsafe to use synchronization mechanisms like Object.wait() and Object.notify() in a concurrent and lock-free scenario.*
*For example, the execution order in the following scenario may be like this: step1->step3->step4->step2.*
*In this way, Thread1 will miss the wake-up of Thread2 and fall into a permanent wait.*

```
 init:
     condition = false
 
 Thread1:
     while (!condition) {  // step1
         wait              // step2
     }
     
 Thread2:
     condition = true    // step3
     notify              // step4
```

WaitingQueue是为了避免上述情况而设计的，它将wait分为两个步骤WaitingQueue.prepareWait和WaitingNode.await。
在condition检查前先使用prepareWait入队这个可能因检查失败而被挂起的线程Thread1，这样一来Thread1永远不会错过被Thread2唤醒的机会，当然实现中还需要依赖另一个安全挂起和唤醒的组件，例如LockSupport。

*WaitingQueue is designed to avoid the above situation. It divides wait into two steps, WaitingQueue.prepareWait and WaitingNode.await.*
*Before the condition check, use prepareWait to enqueue the thread Thread1 that may be suspended due to the check failure, so that Thread1 will never miss the opportunity to be awakened by Thread2. Of course, the implementation also needs to rely on another safe suspension and awakening Components, such as LockSupport.*

```
 init:
     boolean condition = false;
     WaitingQueue wq = new WaitingQueue();
 
 Thread1:
     for (;;) {
         WaitingNode wn = wq.prepareWait();
         if (condition) {
             break;
         }
         wn.await();
     }
 
 Thread2:
     condition = true;
     wq.signal();
```

语义保证：

- w表示调用挂起的线程数量。
- s表示唤醒的线程数量。
- x表示WaitingNode.await调用次数。
- y表示WaitingQueue.signal调用次数。
- WaitingNode.await调用，当前线程将挂起，x++，w++。
- WaitingQueue.signal调用，若有已被挂起或已prepare将来会被挂起的线程，则保证必然能唤醒其中已被挂起或将来被挂起的最先者，已被唤醒过的线程除非重新调用WaitingNode.await否则不会被再次唤醒（在当前版本的实现中这个语义是逻辑上正确的），y++，s++。
- 若某一时刻x==y，则w==s。

*Semantic guarantee:*

- *w indicates the number of threads that the call is suspended.*
- *s indicates the number of threads to wake up.*
- *x represents the number of calls to WaitingNode.await.*
- *y represents the number of calls to WaitingQueue.signal.*
- *WaitingNode.await call, the current thread will be suspended, x++, w++.*
- *WaitingQueue.signal call, if there is a thread that has been suspended or that has been prepared and will be suspended in the future, it is guaranteed to be able to wake up the first thread that has been suspended or will be suspended in the future, unless the thread has been awakened Call WaitingNode.await again, otherwise it will not be awakened again (this semantics is logically correct in the current version of the implementation), y++, s++.*
- *If at a certain moment x==y, then w==s.*

## RingBufferBlockingQueue

通过RingBufferBlockingQueue.enqueue(Object)入队元素，通过RingBufferBlockingQueue.dequeue()出队元素。
 当队列已满时调用RingBufferBlockingQueue.enqueue(Object)的线程将被阻塞，当队列为空时调用RingBufferBlockingQueue.dequeue()的线程将被阻塞。  

 该缓冲队列可以用于生产消费者模式的实现中，可使用命令模式解耦生产消费者，且使用缓冲区能够有效消除生产快消费慢的速度差异。 

*Enqueue elements through RingBufferBlockingQueue.enqueue(Object) and dequeue elements through RingBufferBlockingQueue.dequeue().*
  *The thread calling RingBufferBlockingQueue.enqueue(Object) will be blocked when the queue is full, and the thread calling RingBufferBlockingQueue.dequeue() will be blocked when the queue is empty.*

  *The buffer queue can be used in the realization of the production-consumer mode, the command mode can be used to decouple the production consumers, and the use of the buffer can effectively eliminate the difference in the speed of production and consumption.*

Reference：https://tech.meituan.com/2016/11/18/disruptor.html

# usage

## WaitingQueue

```
public class WaitingQueue {

    /**
     * 准备一个等待节点。
     * 
     * @return
     */
    public WaitingNode prepareWait()

    /**
     * 若有已被挂起或已prepare将来会被挂起的线程，则保证必然能唤醒其中已被挂起或将来被挂起的最先者，已被唤醒过的线程除非重新调用{@link WaitingNode#await}否则不会被再次唤醒。
     */
    public void signal()

}

public class WaitingNode {
    
   /**
    * 挂起当前线程直至收到一个当前节点prepare完成后的{@link WaitingQueue#signal()}通知。
    */
    public void await() throws InterruptedException
    
}

init:
    boolean condition = false;
    WaitingQueue wq = new WaitingQueue();

Thread1:
    for (;;) {
        WaitingNode wn = wq.prepareWait();
        if (condition) {
            break;
        }
        wn.await();
    }

Thread2:
    condition = true;
    wq.signal();
```

## RingBufferBlockingQueue

```
public class RingBufferBlockingQueue<E> {

    /**
     * 入队元素，当队列已满时调用线程将被阻塞。
     *
     * @param element
     * @throws InterruptedException
     */
    public void enqueue(E element) throws InterruptedException
    
    /**
     * 出队元素，当队列为空时调用线程将被阻塞。
     *
     * @return
     * @throws InterruptedException
     */
    public E dequeue() throws InterruptedException

}
```
