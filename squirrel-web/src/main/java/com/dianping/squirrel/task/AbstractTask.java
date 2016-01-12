package com.dianping.squirrel.task;

import com.dianping.cache.entity.Task;

/**
 * Created by thunder on 16/1/5.
 */
public abstract class AbstractTask<T> implements Runnable{

    private Task deamonTask;

    protected void startTask() {

    }

    protected void endTask() {

    }

    public abstract void taskRun();

    public void run() {
        startTask();
        taskRun();
        endTask();
    }



}