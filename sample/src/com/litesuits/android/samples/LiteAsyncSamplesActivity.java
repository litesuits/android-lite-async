package com.litesuits.android.samples;


import android.os.Bundle;
import android.os.SystemClock;
import android.view.Menu;
import com.litesuits.android.async.*;
import com.litesuits.android.log.Log;
import com.litesuits.async.R;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Timer;
import java.util.concurrent.TimeUnit;

public class LiteAsyncSamplesActivity extends BaseActivity {
    Timer timer;

    /**
     * 在{@link BaseActivity#onCreate(Bundle)}中设置视图
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setSubTitile(getString(R.string.sub_title));
	}

	public void onDestroy() {
		super.onDestroy();
		if (timer != null)
			timer.cancel();
	};

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public String getMainTitle() {
		return getString(R.string.title);
	}

	@Override
	public String[] getButtonTexts() {
		return getResources().getStringArray(R.array.async_test_list);
	}

	@Override
	public Runnable getButtonClickRunnable(final int id) {
		// Main UI Thread
		// makeAsyncTask(id);
		return new Runnable() {
			@Override
			public void run() {
				// Child Thread
				makeAsyncTask(id);
			}
		};
	}

	/**
	 * 
	 * 0<item>AsyncTask Execute</item> 1<item>AsyncTask Exe Runnable</item>
	 * 2<item>Simple Task</item> 3<item>Safe Task</item> 4<item>Task
	 * Cancel</item> 5<item>Ordered Task Executor</item> 6<item>CyclicBarrier
	 * Task Executor</item> 7<item>Delayed Task</item> 8<item>Timer Task</item>
	 * 9<item>Cached Task:Reduce Pressure of Server</item>
	 * 
	 * @param id
	 */
	private void makeAsyncTask(int id) {
		switch (id) {
		case 0:
			testAsyncTask();
			break;
		case 1:
			testExeRunnable();
			break;
		case 2:
			testSimpleTask();
			break;
		case 3:
			testSafeTask();
			break;
		case 4:
			test500msCancel();
			break;
		case 5:
			testOrderedTaskExecutor();
			break;
		case 6:
			testCyclicBarrierExecutor();
			break;
		case 7:
			SimpleTask<?> s1 = getTask(1);
			TaskExecutor.startDelayedTask(s1, 2, TimeUnit.SECONDS);
			break;
		case 8:
			if (timer != null)
				timer.cancel();
			timer = TaskExecutor.startTimerTask(new Runnable() {

				@Override
				public void run() {
					Log.i(TAG, "bong!");
				}
			}, 3000, 1000);
			break;
		case 9:
			// Cached Task 在不需要频繁刷新数据的场景，正确实用它，可极大减轻服务器压力
			testCachedAsyncTask();
		default:
			break;
		}
	}

	private void testCachedAsyncTask() {
		// 超时时间暂设置为10秒(实际可能时间比较长)：第一次无缓存，取自网络。
		new SimpleCachedTask<User>(LiteAsyncSamplesActivity.this,
				"getUserInfo", 10, TimeUnit.SECONDS) {
			@Override
			protected User doConnectNetwork() {
				// execute...!
				Log.i(TAG, " 1 connect to network now..");
				return mockhttpGetUserInfo();
			}
		}.execute();
		Log.i(TAG, "first call");

		SystemClock.sleep(6000);
		Log.i(TAG, "sleep 6000ms, second call");
		// sleep 6s , 未超时，数据将取自本地缓存。
		new SimpleCachedTask<User>(LiteAsyncSamplesActivity.this,
				"getUserInfo", 10, TimeUnit.SECONDS) {
			@Override
			protected User doConnectNetwork() {
				// noooo execute...!
				Log.i(TAG, " 2 connect to network now.. 你将看不到这行日志。因为未超时它不会被执行");
				return mockhttpGetUserInfo();
			}
		}.execute();
		SystemClock.sleep(6000);
		Log.i(TAG, "sleep 6000ms again, third call");
		// sleep 12s , 已超时，数据将取自本地网络。
		new SimpleCachedTask<User>(LiteAsyncSamplesActivity.this,
				"getUserInfo", 10, TimeUnit.SECONDS) {
			@Override
			protected User doConnectNetwork() {
				// execute...!
				Log.i(TAG, " 3 connect to network now..");
				Log.i(TAG, " 3  取自互联网（虚拟）");
				return mockhttpGetUserInfo();
			}
		}.execute();
	}

	private void testAsyncTask() {
		// execute
		AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... params) {
				Log.i("One AsyncTask execute ");
				return null;
			}
		};
		task.execute();
		SystemClock.sleep(300);

		// 较大量并发 execute allowing loss
		for (int i = 0; i < 66; i++) {
			final int j = i;
			task = new AsyncTask<Void, Void, Void>() {
				@Override
				protected Void doInBackground(Void... params) {
					Log.i("AsyncTask executeAllowingLoss " + j);
					SystemClock.sleep(500);
					return null;
				}
			};
			task.executeAllowingLoss();
		}
	}

	private void testExeRunnable() {
		// execute
		AsyncTask.execute(new Runnable() {
			@Override
			public void run() {
				Log.i("AsyncTask Runnable execute ");
			}
		});
		SystemClock.sleep(300);
		// 较大量并发 execute allowing loss
		for (int i = 0; i < 66; i++) {
			final int j = i;
			AsyncTask.executeAllowingLoss(new Runnable() {
                @Override
                public void run() {
                    Log.i("AsyncTask Runnable executeAllowingLoss " + j);
                    SystemClock.sleep(500);
                }
            });
		}
	}

	private void testSimpleTask() {
		SimpleTask<Integer> simple = new SimpleTask<Integer>() {

			@Override
			protected Integer doInBackground() {
				return 12345678;
			}

			@Override
			protected void onPostExecute(Integer result) {
				Log.i("SimpleTask result: " + result);
			}
		};
		simple.execute();
		// simple safe success
		SimpleSafeTask<String> sst = new SimpleSafeTask<String>() {

            @Override
            protected String doInBackgroundSafely() throws Exception {
                return "hello";
            }

			@Override
			protected void onPostExecuteSafely(String result, Exception e)
					throws Exception {
				Log.i("SimpleSafeTask onPostExecuteSafely " + result
						+ " , thread id  : " + Thread.currentThread().getId());
			}
        };
		sst.execute();
		Log.i("~~~~~You Will See A Lot of Exception Info ~~~~~~:");
		// simple safe, error in every step
		SimpleSafeTask<String> sse = new SimpleSafeTask<String>() {

			@Override
			protected String doInBackgroundSafely() throws Exception {
				publishProgress(1, 2, 3);
				Log.i("SimpleSafeTask : doInBackground");
				String s = null;
				s.toCharArray();
				return null;
			}

			@Override
			protected void onPreExecuteSafely() throws Exception {
				Log.i("SimpleSafeTask : onPreExecuteSafely");
				String s = null;
				s.toCharArray();
			}

			@Override
			protected void onPostExecuteSafely(String result, Exception e)
					throws Exception {
				Log.i("SimpleSafeTask : onPostExecuteSafely, exception: " + e);
				String s = null;
				s.toCharArray();
			}

			@Override
			protected void onProgressUpdateSafely(Object... values)
					throws Exception {
				Log.i("SimpleSafeTask : onProgressUpdateSafely");
				String s = null;
				s.toCharArray();
			}

		};
		sse.execute();
	}

	private void testSafeTask() {
		Log.i("~~~~~You Will See A Lot of Exception Info ~~~~~~:");
		// safe task, but make error in every step
		SafeTask<Integer, Integer, String> se = new SafeTask<Integer, Integer, String>() {

			@Override
			protected String doInBackgroundSafely(Integer... params) {
				Log.i("SafeTask error doInBackgroundSafely, thread id  : "
						+ Thread.currentThread().getId());
				publishProgress(1, 2, 3);
				String s = null;
				s.toCharArray();
				return " Result String...";
			}

			@Override
			protected void onPreExecuteSafely() {
				Log.i("SafeTask error onPreExecuteSafely, thread id  : "
						+ Thread.currentThread().getId());
				String s = null;
				s.toCharArray();
			}

			@Override
			protected void onPostExecuteSafely(String result, Exception e)
					throws Exception {
				Log.i("SafeTask error onPostExecuteSafely :" + result
						+ ",  thread id  : " + Thread.currentThread().getId());
				Log.i("SafeTask error onPostExecuteSafely Exception :" + e
						+ ",  thread id  : " + Thread.currentThread().getId());
				String s = null;
				s.toCharArray();
			}

			@Override
			protected void onProgressUpdateSafely(Integer... values) {
				Log.i("SafeTask error onProgressUpdateSafely :"
						+ Arrays.toString(values) + ",  thread id  : "
						+ Thread.currentThread().getId());
				String s = null;
				s.toCharArray();
			}
		};
		se.execute();

	}

	private void test500msCancel() {
		SimpleTask<Integer> st = new SimpleTask<Integer>() {

			@Override
			protected Integer doInBackground() {
				try {
					Thread.sleep(1500);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				return 0;
			}

			@Override
			protected void onCancelled() {
				Log.i("SimpleTask onCancelled  ");
			}

			@Override
			protected void onPostExecute(Integer result) {
				Log.i("SimpleTask execute : " + result);
			}
		};
		st.execute();
		SystemClock.sleep(500);
		st.cancel(true);
	}

	private void testOrderedTaskExecutor() {
		// ordered task
		SimpleTask<?> s1 = getTask(1);
		SimpleTask<?> s2 = getTask(2);
		final long startTime = System.currentTimeMillis();
		SimpleTask<?> lastTask = new SimpleTask<Void>() {
			@Override
			protected Void doInBackground() {
				return null;
			}

			@Override
			protected void onPostExecute(Void result) {
				Log.i("OrderedTaskExecutor use time: "
						+ (System.currentTimeMillis() - startTime));
			}
		};
		// order: 2-1-last ，按task2-task1-lastTask的顺序执行
		TaskExecutor.newOrderedExecutor().put(s2).put(s1).put(lastTask).start();
	}

	private void testCyclicBarrierExecutor() {
		SimpleTask<?> task1 = getTask(1);
		SimpleTask<?> task2 = getTask(2);
		SimpleTask<?> task3 = getTask(3);
		final long startTime = System.currentTimeMillis();
		SimpleTask<String> destTask = new SimpleTask<String>() {
			@Override
			protected String doInBackground() {
				return "This is the destination. You can do anything you want.";
			}

			@Override
			protected void onPostExecute(String result) {
				Log.i("CyclicBarrierExecutor use time: "
						+ (System.currentTimeMillis() - startTime)
						+ " , info: " + result);
			}
		};
		// task 1,2,3 execute concurrently. destTask is the destination task.
		// 123并发执行，全部完成后，执行lastTask。
		TaskExecutor.newCyclicBarrierExecutor().put(task1).put(task2)
				.put(task3).start(destTask);
		task2.cancel(true);
	}

	private SimpleTask<Integer> getTask(final int id) {
		SimpleTask<Integer> simple = new SimpleTask<Integer>() {

			@Override
			protected Integer doInBackground() {
				try {
					Thread.sleep(1000 * id);
				} catch (InterruptedException e) {
				}
				return id;
			}

			@Override
			protected void onCancelled() {
				Log.i("SimpleTask onCancelled : " + id);
			}

			@Override
			protected void onPostExecute(Integer result) {
				Log.i("SimpleTask execute : " + result);
			}
		};
		return simple;
	}

	/************************************* UserInfo *****************************************************/
	private User mockhttpGetUserInfo() {
		User user = new User();
		user.api = "com.litesuits.get.user";
		user.result = new BaseResponse.Result(200, "OK");
		user.data = new User.UserInfo("Lucy", 28);
		return user;
	}

	public static class User extends BaseResponse {
		// private static final long serialVersionUID = -7759004942178651121L;
		private UserInfo data;

		public static class UserInfo implements Serializable {
            private String            name;
            private int               age;
            public  ArrayList<String> girl_friends;

            public UserInfo(String name, int age) {
                this.name = name;
                this.age = age;
            }

            @Override
            public String toString() {
                return "UserInfo [name=" + name + ", age=" + age
                        + ", girl_friends=" + girl_friends + "]";
            }

        }

        @Override
        public String toString() {
            return super.toString() + " User [data=" + data + "]";
        }

    }

    public static abstract class BaseResponse implements Serializable {
        // private static final long serialVersionUID = 448947882360115789L;
        public  String api;
        private String v;
        public  Result result;

        public static class Result implements Serializable {
            public int    code;
            public String message;

            public Result(int code, String message) {
                this.code = code;
                this.message = message;
            }

            @Override
            public String toString() {
                return "Result [code=" + code + ", message=" + message + "]";
            }

        }

        @Override
        public String toString() {
            return "BaseResponse [api=" + api + ", v=" + v + ", result="
                    + result + "]";
        }
    }

}
