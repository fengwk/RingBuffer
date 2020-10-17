# ConcurrentQueue

该库提供了一些线程安全的无锁的高性能队列的Java实现以便学习和参考。

# 介绍

## WaitingQueue

在并发且无锁场景下使用类似`Object.wait()`和`Object.notify()`这样的同步机制可能是不安全的。
例如下边的场景中的执行顺序可能是这样的：step1->step3->step4->step2。
这样一来Thread1将错过Thread2的唤醒从而陷入永久的等待。

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

`WaitingQueue`是为了避免上述情况而设计的，它将wait分为两个步骤`WaitingQueue.prepareWait`和`WaitingNode.await`。
在condition检查前先使用prepareWait入队这个可能因检查失败而被挂起的线程Thread1，这样一来Thread1永远不会错过被Thread2唤醒的机会，当然实现中还需要依赖另一个安全挂起和唤醒的组件，例如`LockSupport`。

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
- x表示`WaitingNode.await`调用次数。
- y表示`WaitingQueue.signal`调用次数。
- `WaitingNode.await`调用，当前线程将挂起，`x++`，`w++`。
- `WaitingQueue.signal`调用，若有已被挂起或已prepare将来会被挂起的线程，则保证必然能唤醒其中已被挂起或将来被挂起的最先者，已被唤醒过的线程除非重新调用`WaitingNode.await`否则不会被再次唤醒（在当前版本的实现中这个语义是逻辑上正确的），`y++`，`s++`。
- 若某一时刻x==y，则w==s。

## RingBufferBlockingQueue

通过`RingBufferBlockingQueue.enqueue(Object)`入队元素，通过`RingBufferBlockingQueue.dequeue()`出队元素。
 当队列已满时调用`RingBufferBlockingQueue.enqueue(Object)`的线程将被阻塞，当队列为空时调用`RingBufferBlockingQueue.dequeue()`的线程将被阻塞。  

 该缓冲队列可以用于生产消费者模式的实现中，可使用命令模式解耦生产消费者，且使用缓冲区能够有效消除生产快消费慢的速度差异。 

Reference：https://tech.meituan.com/2016/11/18/disruptor.html

# 使用

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
