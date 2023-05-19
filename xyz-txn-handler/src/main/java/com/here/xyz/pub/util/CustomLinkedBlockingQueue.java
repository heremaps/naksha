package com.here.xyz.pub.util;

import java.util.concurrent.LinkedBlockingQueue;

// Custom class with overridden behaviour to block the caller when task is submitted to this queue
public class CustomLinkedBlockingQueue<E> extends LinkedBlockingQueue<E> {
    public CustomLinkedBlockingQueue(int maxSize) {
        super(maxSize);
    }

    @Override
    public boolean offer(E e)
    {
        // turn offer() and add() into a blocking calls (unless interrupted)
        try {
            put(e);
            return true;
        } catch(InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        return false;
    }

}
