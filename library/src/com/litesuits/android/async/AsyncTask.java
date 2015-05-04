package com.litesuits.android.async;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.widget.ListView;

import java.util.Stack;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * see {@link android.os.AsyncTask}
 * <p><b>在系统基础上</b>
 * <ul>
 * <li>1. 增强并发能力,根据处理器个数设置线程开销</li>
 * <li>2. 大量线程并发状况下优化线程并发控制及调度策略</li>
 * <li>3. 支持子线程建立并执行{@link AsyncTask}，{@link #onPostExecute(Object)}方法一定会在主线程执行</li>
 * </ul>
 * @author MaTianyu
 * 2014-1-30下午3:10:43
 */
public abstract class AsyncTask<Params, Progress, Result> {
	private static final String LOG_TAG = "AsyncTask";

	private static int CPU_COUNT = Runtime.getRuntime().availableProcessors();
	static {
		Log.i(LOG_TAG, "CPU ： " + CPU_COUNT);
	}
	/*********************************** 基本线程池（无容量限制） *******************************/
	/**
	 * 有N处理器，便长期保持N个活跃线程。
	 */
	private static final int CORE_POOL_SIZE = CPU_COUNT;
	private static final int MAXIMUM_POOL_SIZE = Integer.MAX_VALUE;
	private static final int KEEP_ALIVE = 10;
	private static final ThreadFactory sThreadFactory = new ThreadFactory() {
		private final AtomicInteger mCount = new AtomicInteger(1);

		public Thread newThread(Runnable r) {
			return new Thread(r, "AsyncTask #" + mCount.getAndIncrement());
		}
	};
	private static final BlockingQueue<Runnable> sPoolWorkQueue = new SynchronousQueue<Runnable>();
	/**
	 * An {@link Executor} that can be used to execute tasks in parallel.
	 * 核心线程数为{@link #CORE_POOL_SIZE}，不限制并发总线程数!
	 * 这就使得任务总能得到执行，且高效执行少量（<={@link #CORE_POOL_SIZE}）异步任务。
	 * 线程完成任务后保持{@link #KEEP_ALIVE}秒销毁，这段时间内可重用以应付短时间内较大量并发，提升性能。
	 * 它实际控制并执行线程任务。
	 */
	public static final ThreadPoolExecutor mCachedSerialExecutor = new ThreadPoolExecutor(CORE_POOL_SIZE,
			MAXIMUM_POOL_SIZE, KEEP_ALIVE, TimeUnit.SECONDS, sPoolWorkQueue, sThreadFactory);

	/*********************************** 线程并发控制器 *******************************/
	/**
	 * 并发量控制: 根据cpu能力控制一段时间内并发数量，并发过量大时采用Lru方式移除旧的异步任务，默认采用LIFO策略调度线程运作，开发者可选调度策略有LIFO、FIFO。
	 */
    public static final Executor mLruSerialExecutor = new SmartSerialExecutor();

	/**
	 * 它大大改善Android自带异步任务框架的处理能力和速度。
	 * 默认地，它使用LIFO（后进先出）策略来调度线程，可将最新的任务快速执行，当然你自己可以换为FIFO调度策略。
	 * 这有助于用户当前任务优先完成（比如加载图片时，很容易做到当前屏幕上的图片优先加载）。
	 *
	 * @author MaTianyu
	 * 2014-2-3上午12:46:53
	 */
	private static class SmartSerialExecutor implements Executor {
		/**
		 * 这里使用{@link ArrayDequeCompat}当栈比{@link Stack}性能高
		 */
		private ArrayDequeCompat<Runnable> mQueue = new ArrayDequeCompat<Runnable>(serialMaxCount);
		private ScheduleStrategy mStrategy = ScheduleStrategy.LIFO;

		private enum ScheduleStrategy {
			/**
			 * 队列中最后加入的任务最先执行
			 */
			LIFO,
			/**
			 * 队列中最先加入的任务最先执行
			 */
			FIFO;
		}
		/**
		 * 一次同时并发的数量，根据处理器数量调节
		 *
		 * <p>cpu count   :  1    2    3    4    8    16    32
		 * <p>once(base*2):  1    2    3    4    8    16    32
		 *
		 * <p>一个时间段内最多并发线程个数：
		 * 双核手机：2
		 * 四核手机：4
		 * ...
		 * 计算公式如下：
		 */
		private static int serialOneTime;
		/**
		 * 并发最大数量，当投入的任务过多大于此值时，根据Lru规则，将最老的任务移除（将得不到执行）
		 * <p>cpu count   :  1    2    3    4    8    16    32
		 * <p>base(cpu+3) :  4    5    6    7    11   19    35
		 * <p>max(base*16):  64   80   96   112  176  304   560
		 */
		private static int serialMaxCount;
		private int cpuCount = CPU_COUNT;

		private void reSettings(int cpuCount) {
			this.cpuCount = cpuCount;
			serialOneTime = cpuCount;
			serialMaxCount = (cpuCount + 3) * 16;
		}

		public SmartSerialExecutor() {
			reSettings(CPU_COUNT);
		}

		@Override
		public synchronized void execute(final Runnable command) {
			Runnable r = new Runnable() {
				@Override
				public void run() {
					command.run();
					next();
				}
			};
			if (mCachedSerialExecutor.getActiveCount() < serialOneTime) {
				// 小于单次并发量直接运行
				mCachedSerialExecutor.execute(r);
			} else {
				// 如果大于并发上限，那么移除最老的任务
				if (mQueue.size() >= serialMaxCount) {
					mQueue.pollFirst();
				}
				// 新任务放在队尾
				mQueue.offerLast(r);

				// 动态获取目前cpu处理器数目,并调整设置。
				// int proCount = Runtime.getRuntime().availableProcessors();
				// if (proCount != cpuCount) {
				// cpuCount = proCount;
				// reSettings(proCount);
				// }
			}

		}

		public synchronized void next() {
			Runnable mActive;
			switch (mStrategy) {
				case LIFO :
					mActive = mQueue.pollLast();
					break;
				case FIFO :
					mActive = mQueue.pollFirst();
					break;
				default :
					mActive = mQueue.pollLast();
					break;
			}
			if (mActive != null) mCachedSerialExecutor.execute(mActive);
		}
	}

	/*********************************** 其他 *******************************/

	private static final int MESSAGE_POST_RESULT = 0x1;
	private static final int MESSAGE_POST_PROGRESS = 0x2;

	protected static final InternalHandler sHandler;
	static {
		if (Looper.myLooper() != Looper.getMainLooper()) {
			sHandler = new InternalHandler(Looper.getMainLooper());
		} else {
			sHandler = new InternalHandler();
		}
	}

	private static volatile Executor sDefaultExecutor = mCachedSerialExecutor;
	private final WorkerRunnable<Params, Result> mWorker;
	private final FutureTask<Result> mFuture;

	private volatile Status mStatus = Status.PENDING;

	private final AtomicBoolean mCancelled = new AtomicBoolean();
	private final AtomicBoolean mTaskInvoked = new AtomicBoolean();
	private FinishedListener finishedListener;

	/**
	 * Indicates the current status of the task. Each status will be set only once
	 * during the lifetime of a task.
	 */
	public enum Status {
		/**
		 * Indicates that the task has not been executed yet.
		 */
		PENDING,
		/**
		 * Indicates that the task is running.
		 */
		RUNNING,
		/**
		 * Indicates that {@link AsyncTask#onPostExecute} has finished.
		 */
		FINISHED,
	}

	/** @hide Used to force static handler to be created. */
	public static void init() {
		sHandler.getLooper();
	}

	/** @hide */
	public static void setDefaultExecutor(Executor exec) {
		sDefaultExecutor = exec;
	}

	/**
	 * Creates a new asynchronous task. This constructor must be invoked on the UI thread.
	 */
	public AsyncTask() {
		mWorker = new WorkerRunnable<Params, Result>() {
			public Result call() throws Exception {
				mTaskInvoked.set(true);
				Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
				return postResult(doInBackground(mParams));
			}
		};

		mFuture = new FutureTask<Result>(mWorker) {
			@Override
			protected void done() {
				try {
					postResultIfNotInvoked(get());
				} catch (InterruptedException e) {
					android.util.Log.w(LOG_TAG, e);
				} catch (ExecutionException e) {
					throw new RuntimeException("An error occured while executing doInBackground()", e.getCause());
				} catch (CancellationException e) {
					postResultIfNotInvoked(null);
				}
			}
		};
	}

	private void postResultIfNotInvoked(Result result) {
		final boolean wasTaskInvoked = mTaskInvoked.get();
		if (!wasTaskInvoked) {
			postResult(result);
		}
	}

	private Result postResult(Result result) {
		@SuppressWarnings("unchecked")
		Message message = sHandler.obtainMessage(MESSAGE_POST_RESULT, new AsyncTaskResult<Result>(this, result));
		message.sendToTarget();
		return result;
	}

	/**
	 * Returns the current status of this task.
	 *
	 * @return The current status.
	 */
	public final Status getStatus() {
		return mStatus;
	}

	/**
	 * Override this method to perform a computation on a background thread. The
	 * specified parameters are the parameters passed to {@link #execute}
	 * by the caller of this task.
	 *
	 * This method can call {@link #publishProgress} to publish updates
	 * on the UI thread.
	 *
	 * @param params The parameters of the task.
	 *
	 * @return A result, defined by the subclass of this task.
	 *
	 * @see #onPreExecute()
	 * @see #onPostExecute
	 * @see #publishProgress
	 */
	protected abstract Result doInBackground(Params... params);

	/**
	 * Runs on the UI thread before {@link #doInBackground}.
	 *
	 * @see #onPostExecute
	 * @see #doInBackground
	 */
	protected void onPreExecute() {}

	/**
	 * <p>Runs on the UI thread after {@link #doInBackground}. The
	 * specified result is the value returned by {@link #doInBackground}.</p>
	 *
	 * <p>This method won't be invoked if the task was cancelled.</p>
	 *
	 * @param result The result of the operation computed by {@link #doInBackground}.
	 *
	 * @see #onPreExecute
	 * @see #doInBackground
	 * @see #onCancelled(Object)
	 */
	@SuppressWarnings({"UnusedDeclaration"})
	protected void onPostExecute(Result result) {}

	/**
	 * Runs on the UI thread after {@link #publishProgress} is invoked.
	 * The specified values are the values passed to {@link #publishProgress}.
	 *
	 * @param values The values indicating progress.
	 *
	 * @see #publishProgress
	 * @see #doInBackground
	 */
	@SuppressWarnings({"UnusedDeclaration"})
	protected void onProgressUpdate(Progress... values) {}

	/**
	 * <p>Runs on the UI thread after {@link #cancel(boolean)} is invoked and
	 * {@link #doInBackground(Object[])} has finished.</p>
	 *
	 * <p>The default implementation simply invokes {@link #onCancelled()} and
	 * ignores the result. If you write your own implementation, do not call
	 * <code>super.onCancelled(result)</code>.</p>
	 *
	 * @param result The result, if any, computed in
	 *               {@link #doInBackground(Object[])}, can be null
	 *
	 * @see #cancel(boolean)
	 * @see #isCancelled()
	 */
	@SuppressWarnings({"UnusedParameters"})
	protected void onCancelled(Result result) {
		onCancelled();
	}

	/**
	 * <p>Applications should preferably override {@link #onCancelled(Object)}.
	 * This method is invoked by the default implementation of
	 * {@link #onCancelled(Object)}.</p>
	 *
	 * <p>Runs on the UI thread after {@link #cancel(boolean)} is invoked and
	 * {@link #doInBackground(Object[])} has finished.</p>
	 *
	 * @see #onCancelled(Object)
	 * @see #cancel(boolean)
	 * @see #isCancelled()
	 */
	protected void onCancelled() {}

	/**
	 * Returns <tt>true</tt> if this task was cancelled before it completed
	 * normally. If you are calling {@link #cancel(boolean)} on the task,
	 * the value returned by this method should be checked periodically from
	 * {@link #doInBackground(Object[])} to end the task as soon as possible.
	 *
	 * @return <tt>true</tt> if task was cancelled before it completed
	 *
	 * @see #cancel(boolean)
	 */
	public final boolean isCancelled() {
		return mCancelled.get();
	}

	/**
	 * <p>Attempts to cancel execution of this task.  This attempt will
	 * fail if the task has already completed, already been cancelled,
	 * or could not be cancelled for some other reason. If successful,
	 * and this task has not started when <tt>cancel</tt> is called,
	 * this task should never run. If the task has already started,
	 * then the <tt>mayInterruptIfRunning</tt> parameter determines
	 * whether the thread executing this task should be interrupted in
	 * an attempt to stop the task.</p>
	 *
	 * <p>Calling this method will result in {@link #onCancelled(Object)} being
	 * invoked on the UI thread after {@link #doInBackground(Object[])}
	 * returns. Calling this method guarantees that {@link #onPostExecute(Object)}
	 * is never invoked. After invoking this method, you should check the
	 * value returned by {@link #isCancelled()} periodically from
	 * {@link #doInBackground(Object[])} to finish the task as early as
	 * possible.</p>
	 *
	 * @param mayInterruptIfRunning <tt>true</tt> if the thread executing this
	 *        task should be interrupted; otherwise, in-progress tasks are allowed
	 *        to complete.
	 *
	 * @return <tt>false</tt> if the task could not be cancelled,
	 *         typically because it has already completed normally;
	 *         <tt>true</tt> otherwise
	 *
	 * @see #isCancelled()
	 * @see #onCancelled(Object)
	 */
	public final boolean cancel(boolean mayInterruptIfRunning) {
		mCancelled.set(true);
		return mFuture.cancel(mayInterruptIfRunning);
	}

	/**
	 * Waits if necessary for the computation to complete, and then
	 * retrieves its result.
	 *
	 * @return The computed result.
	 *
	 * @throws CancellationException If the computation was cancelled.
	 * @throws ExecutionException If the computation threw an exception.
	 * @throws InterruptedException If the current thread was interrupted
	 *         while waiting.
	 */
	public final Result get() throws InterruptedException, ExecutionException {
		return mFuture.get();
	}

	/**
	 * Waits if necessary for at most the given time for the computation
	 * to complete, and then retrieves its result.
	 *
	 * @param timeout Time to wait before cancelling the operation.
	 * @param unit The time unit for the timeout.
	 *
	 * @return The computed result.
	 *
	 * @throws CancellationException If the computation was cancelled.
	 * @throws ExecutionException If the computation threw an exception.
	 * @throws InterruptedException If the current thread was interrupted
	 *         while waiting.
	 * @throws TimeoutException If the wait timed out.
	 */
	public final Result get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException,
			TimeoutException {
		return mFuture.get(timeout, unit);
	}

	/**
	 * Executes the task with the specified parameters. The task returns
	 * itself (this) so that the caller can keep a reference to it.
	 *
	 * <p> Execute a task immediately.
	 *
	 * <p> This method must be invoked on the UI thread.
	 *
	 * <p> 用于重要、紧急、单独的异步任务，该Task立即得到执行。
	 * <p> 加载类似瀑布流时产生的大量并发（一定程度允许任务被剔除队列）时请用{@link AsyncTask#executeAllowingLoss(Object...)}
	 * @param params The parameters of the task.
	 *
	 * @return This instance of AsyncTask.
	 *
	 * @throws IllegalStateException If {@link #getStatus()} returns either
	 *         {@link AsyncTask.Status#RUNNING} or {@link AsyncTask.Status#FINISHED}.
	 *
	 * @see #executeOnExecutor(java.util.concurrent.Executor, Object[])
	 * @see #execute(Runnable)
	 */
	public final AsyncTask<Params, Progress, Result> execute(final Params... params) {
		return executeOnExecutor(sDefaultExecutor, params);
	}

	/**
	 * <p> 用于瞬间大量并发的场景，比如，假设用户拖动{@link ListView}时如果需要加载大量图片，而拖动过去时间很久的用户已经看不到，允许任务丢失。
	 * <p> This method execute task wisely when a large number of task will be submitted.
	 * @param params
	 * @return
	 */
	public final AsyncTask<Params, Progress, Result> executeAllowingLoss(Params... params) {
		return executeOnExecutor(mLruSerialExecutor, params);
	}

	/**
	 * Executes the task with the specified parameters. The task returns
	 * itself (this) so that the caller can keep a reference to it.
	 *
	 * <p>This method is typically used with {@link #mCachedSerialExecutor} to
	 * allow multiple tasks to run in parallel on a pool of threads managed by
	 * AsyncTask, however you can also use your own {@link Executor} for custom
	 * behavior.
	 *
	 * <p>This method must be invoked on the UI thread.
	 *
	 * @param exec The executor to use.  {@link #mCachedSerialExecutor} is available as a
	 *              convenient process-wide thread pool for tasks that are loosely coupled.
	 * @param params The parameters of the task.
	 *
	 * @return This instance of AsyncTask.
	 *
	 * @throws IllegalStateException If {@link #getStatus()} returns either
	 *         {@link AsyncTask.Status#RUNNING} or {@link AsyncTask.Status#FINISHED}.
	 *
	 * @see #execute(Object[])
	 */
	public final AsyncTask<Params, Progress, Result> executeOnExecutor(Executor exec, Params... params) {
		if (mStatus != Status.PENDING) {
			switch (mStatus) {
				case RUNNING :
					throw new IllegalStateException("Cannot execute task:" + " the task is already running.");
				case FINISHED :
					throw new IllegalStateException("Cannot execute task:" + " the task has already been executed "
							+ "(a task can be executed only once)");
			}
		}

		mStatus = Status.RUNNING;

		onPreExecute();

		mWorker.mParams = params;
		exec.execute(mFuture);

		return this;
	}

	/**
	 * Convenience version of {@link #execute(Object...)} for use with
	 * a simple Runnable object. See {@link #execute(Object[])} for more
	 * information on the order of execution.
	 * <p> 用于重要、紧急、单独的异步任务，该Runnable立即得到执行。
	 * <p> 加载类似瀑布流时产生的大量并发（任务数超出限制允许任务被剔除队列）时请用{@link AsyncTask#executeAllowingLoss(Runnable)}
	 * @see #execute(Object[])
	 * @see #executeOnExecutor(java.util.concurrent.Executor, Object[])
	 */
	public static void execute(Runnable runnable) {
		sDefaultExecutor.execute(runnable);
	}

	/**
	 * <p> 用于瞬间大量并发的场景，比如，假设用户拖动{@link ListView}时如果需要启动大量异步线程，而拖动过去时间很久的用户已经看不到，允许任务丢失。
	 * <p> This method execute runnable wisely when a large number of task will be submitted.
	 * <p> 任务数限制情况见{@link SmartSerialExecutor}
	 * immediate execution for important or urgent task.
	 * @param runnable
	 */
	public static void executeAllowingLoss(Runnable runnable) {
		mLruSerialExecutor.execute(runnable);
	}

	/**
	 * This method can be invoked from {@link #doInBackground} to
	 * publish updates on the UI thread while the background computation is
	 * still running. Each call to this method will trigger the execution of
	 * {@link #onProgressUpdate} on the UI thread.
	 *
	 * {@link #onProgressUpdate} will note be called if the task has been
	 * canceled.
	 *
	 * @param values The progress values to update the UI with.
	 *
	 * @see #onProgressUpdate
	 * @see #doInBackground
	 */
	protected final void publishProgress(Progress... values) {
		if (!isCancelled()) {
			sHandler.obtainMessage(MESSAGE_POST_PROGRESS, new AsyncTaskResult<Progress>(this, values)).sendToTarget();
		}
	}

	private void finish(Result result) {
		if (isCancelled()) {
			onCancelled(result);
			if (finishedListener != null) finishedListener.onCancelled();
		} else {
			onPostExecute(result);
			if (finishedListener != null) finishedListener.onPostExecute();
		}
		mStatus = Status.FINISHED;
	}

	protected FinishedListener getFinishedListener() {
		return finishedListener;
	}

	protected void setFinishedListener(FinishedListener finishedListener) {
		this.finishedListener = finishedListener;
	}

	private static class InternalHandler extends Handler {
		public InternalHandler() {
			super();
		}

		public InternalHandler(Looper looper) {
			super(looper);
		}

		@SuppressWarnings({"unchecked", "RawUseOfParameterizedType"})
		@Override
		public void handleMessage(Message msg) {
			AsyncTaskResult result = (AsyncTaskResult) msg.obj;
			switch (msg.what) {
				case MESSAGE_POST_RESULT :
					// There is only one result
					result.mTask.finish(result.mData[0]);
					break;
				case MESSAGE_POST_PROGRESS :
					result.mTask.onProgressUpdate(result.mData);
					break;
			}
		}
	}

	private static abstract class WorkerRunnable<Params, Result> implements Callable<Result> {
		Params[] mParams;
	}

	@SuppressWarnings({"RawUseOfParameterizedType"})
	private static class AsyncTaskResult<Data> {
		final AsyncTask mTask;
		final Data[] mData;

		AsyncTaskResult(AsyncTask task, Data... data) {
			mTask = task;
			mData = data;
		}
	}

	public static interface FinishedListener {
		void onCancelled();

		void onPostExecute();
	}

}
