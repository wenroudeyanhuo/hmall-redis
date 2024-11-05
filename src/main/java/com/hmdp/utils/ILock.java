package com.hmdp.utils;

/**
 * @author 温柔的烟火
 * @date 2024/11/4-19:35
 */
public interface ILock {
    /**
     * 尝试获取锁
     *
     */
    boolean tryLock(long timeoutSec);

    void unlock();
}
