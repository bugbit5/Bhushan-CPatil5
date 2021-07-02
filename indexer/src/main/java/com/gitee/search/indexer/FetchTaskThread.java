package com.gitee.search.indexer;

import com.gitee.search.core.GiteeSearchConfig;
import com.gitee.search.queue.QueueFactory;
import com.gitee.search.queue.QueueProvider;
import com.gitee.search.queue.QueueTask;
import org.apache.commons.lang.math.NumberUtils;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 用于从队列中获取待办任务的线程
 * @author Winter Lau<javayou@gmail.com>
 */
public class FetchTaskThread extends Thread {

    private QueueProvider provider;
    private int no_task_interval    = 1000; //从队列中获取不到任务时的休眠时间
    private int batch_fetch_count   = 10;   //一次从队列中获取任务的数量

    private Map<String, ExecutorService> executors = new HashMap<>(); //每一个类型的任务使用一个独立的线程来处理

    public FetchTaskThread() {
        this.provider = QueueFactory.getProvider();
        Properties props = GiteeSearchConfig.getIndexerProperties();
        no_task_interval = NumberUtils.toInt(props.getProperty("no_task_interval"), 1000);
        batch_fetch_count = NumberUtils.toInt(props.getProperty("batch_fetch_count"), 10);

        QueueTask.types.forEach( t -> {
            executors.put(t, Executors.newSingleThreadExecutor());
        });
    }

    @Override
    public void run() {
        while(!this.isInterrupted() ) {
            List<String> taskNames = new ArrayList<>();
            List<QueueTask> tasks = provider.pop(batch_fetch_count);
            tasks.forEach( task -> {
                ExecutorService executor = executors.get(task.getType());
                executor.submit(()-> {
                    //TODO: task write to lucene index
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                });
                taskNames.add(task.getType());
            });

            //waiting all threads finished
            taskNames.forEach(type -> executors.get(type).shutdown());

            if(tasks.size() < batch_fetch_count) {
                try {
                    Thread.sleep(no_task_interval);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

}
