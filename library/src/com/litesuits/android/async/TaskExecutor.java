package com.litesuits.android.async;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * the {@link TaskExecutor} can execute task in many ways.
 * <ul>
 * <li>1. OrderedTask, 有序的执行一些列任务。
 * <li>2. CyclicBarrierTask, 并发的执行一系列任务，且会在所有任务执行完成时集中到一个关卡点（执行特定的函数）。
 * <li>3. Delayed Task, 延时任务。
 * <li>4. Timer Runnable, 定时任务。
 * </ul>
 *
 * @author MaTianyu
 *         2014-2-3下午6:30:14
 */
public class TaskExecutor {

    /**
     * 开子线程
     *
     * @param run
     */
    public static void start(Runnable run) {
        AsyncTask.execute(run);
    }

    /**
     * 开子线程，并发超出数量限制时允许丢失任务。
     *
     * @param run
     */
    public static void startAllowingLoss(Runnable run) {
        AsyncTask.executeAllowingLoss(run);
    }

    /**
     * 有序异步任务执行器
     *
     * @return
     */
    public static OrderedTaskExecutor newOrderedExecutor() {
        return new OrderedTaskExecutor();
    }

    /**
     * 关卡异步任务执行器
     *
     * @return
     */
    public static CyclicBarrierExecutor newCyclicBarrierExecutor() {
        return new CyclicBarrierExecutor();
    }

    /**
     * 延时异步任务
     *
     * @param task
     * @param time
     * @param unit
     */
    public static void startDelayedTask(final AsyncTask<?, ?, ?> task, long time, TimeUnit unit) {
        long delay = time;
        if (unit != null) delay = unit.toMillis(time);
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                task.execute();
            }
        }, delay);
    }

    /**
     * 启动定时任务
     *
     * @param run
     * @param delay  >0 延迟时间
     * @param period >0 心跳间隔时间
     * @return
     */
    public static Timer startTimerTask(final Runnable run, long delay, long period) {
        Timer timer = new Timer();
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                run.run();
            }
        };
        timer.scheduleAtFixedRate(timerTask, delay, period);
        return timer;
    }

    public static class OrderedTaskExecutor {
        LinkedList<AsyncTask<?, ?, ?>> taskList = new LinkedList<AsyncTask<?, ?, ?>>();
        private transient boolean isRunning = false;

        public OrderedTaskExecutor put(AsyncTask<?, ?, ?> task) {
            synchronized (taskList) {
                if (task != null) taskList.add(task);
            }
            return this;
        }

        public void start() {
            if (isRunning) return;
            isRunning = true;
            for (AsyncTask<?, ?, ?> each : taskList) {
                final AsyncTask<?, ?, ?> task = each;
                task.setFinishedListener(new AsyncTask.FinishedListener() {

                    @Override
                    public void onPostExecute() {
                        synchronized (taskList) {
                            executeNext();
                        }
                    }

                    @Override
                    public void onCancelled() {
                        synchronized (taskList) {
                            taskList.remove(task);
                            if (task.getStatus() == AsyncTask.Status.RUNNING) {
                                executeNext();
                            }
                        }
                    }
                });
            }
            executeNext();
        }

        @SuppressWarnings("unchecked")
        private void executeNext() {
            AsyncTask<?, ?, ?> next = null;
            if (taskList.size() > 0) {
                next = taskList.removeFirst();
            }
            if (next != null) {
                next.execute();
            } else {
                isRunning = false;
            }
        }
    }

    public static class CyclicBarrierExecutor {
        ArrayList<AsyncTask<?, ?, ?>> taskList = new ArrayList<AsyncTask<?, ?, ?>>();
        private transient boolean isRunning = false;

        public CyclicBarrierExecutor put(AsyncTask<?, ?, ?> task) {
            if (task != null) taskList.add(task);
            return this;
        }

        public void start(final AsyncTask<?, ?, ?> finishTask) {
            start(finishTask, 0, null);
        }

        @SuppressWarnings("unchecked")
        public void start(final AsyncTask<?, ?, ?> endOnUiTask, final long time, final TimeUnit unit) {
            if (isRunning) throw new RuntimeException("CyclicBarrierExecutor only can start once.");
            isRunning = true;
            final CountDownLatch latch = new CountDownLatch(taskList.size());
            new SimpleTask<Boolean>() {

                @Override
                protected Boolean doInBackground() {
                    try {
                        if (unit == null) latch.await();
                        else latch.await(time, unit);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    return true;
                }

                @Override
                protected void onPostExecute(Boolean aBoolean) {
                    endOnUiTask.execute();
                }
            }.execute();
            startInternal(latch);
        }

        public void start(Runnable endOnUiThread) {
            start(endOnUiThread, 0, null);
        }

        public void start(final Runnable endOnUiThread, final long time, final TimeUnit unit) {
            if (isRunning) throw new RuntimeException("CyclicBarrierExecutor only can start once.");
            isRunning = true;
            final CountDownLatch latch = new CountDownLatch(taskList.size());
            new SimpleTask<Boolean>() {

                @Override
                protected Boolean doInBackground() {
                    try {
                        if (unit == null) latch.await();
                        else latch.await(time, unit);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    return true;
                }

                @Override
                protected void onPostExecute(Boolean aBoolean) {
                    endOnUiThread.run();
                }
            }.execute();
            startInternal(latch);
        }

        private void startInternal(final CountDownLatch latch) {
            for (AsyncTask<?, ?, ?> each : taskList) {
                each.setFinishedListener(new AsyncTask.FinishedListener() {

                    @Override
                    public void onPostExecute() {
                        latch.countDown();
                    }

                    @Override
                    public void onCancelled() {
                        latch.countDown();
                    }
                });
                each.execute();
            }
        }

    }
}
