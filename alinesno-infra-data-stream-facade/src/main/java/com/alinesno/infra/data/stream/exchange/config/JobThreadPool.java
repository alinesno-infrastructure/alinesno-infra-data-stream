package com.alinesno.infra.data.stream.exchange.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 核心线程10个 最大线程100 队列200 公平策略
 *
 * @author zhp
 * @Description:
 * @date 2019-08-18
 * @time 00:16
 */

public final class JobThreadPool {

  // 日志记录
  private static final Logger log = LoggerFactory.getLogger(JobThreadPool.class);

  private static int corePoolSize = 10;

  private static int maximumPoolSize = 100;

  private static long keepAliveTime = 10;


  private static ThreadPoolExecutor threadPoolExecutor;

  private static JobThreadPool asyncThreadPool;

  private JobThreadPool() {
    BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(200, true);
    threadPoolExecutor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime,
        TimeUnit.MINUTES, workQueue);
  }

  public static synchronized JobThreadPool getInstance() {
    if (null == asyncThreadPool) {
      synchronized (JobThreadPool.class) {
        if (null == asyncThreadPool) {
          asyncThreadPool = new JobThreadPool();
        }
      }
    }
    log.info("JobThreadPool threadPoolExecutor={}", threadPoolExecutor);
    return asyncThreadPool;
  }

  public synchronized ThreadPoolExecutor getThreadPoolExecutor() {
    return threadPoolExecutor;
  }


}
