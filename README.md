android-lite-async
==================

An ameliorative, enhanced AsyncTask for Android. LiteAsync provides SimpleTask, SafeTask, CachedTask, etc, for rapid development. More convenient is, it has a TaskExecutor which can executes ordered, cyclicbarrier, delayed and timer Task.

同学们在日常开发中有没有遇到以下场景：

1. 两个原子任务，任务2需要等待任务1完成了才能进行。
2. 任务3需要等任务1和任务2都完成了才能进行，但是1和2可以并发以节省时间。看起来要写很多代码来调度任务。
3. 服务器接口压力过大，要被你的调用频度调戏到down机啦！
4. 系统的异步任务类AsyncTask要用的泛型太多太重啦，并且只能在主线程使用，不爽！
5. 要么大量并发使手机cpu吃紧卡到爆，要么不能真正（Android系统自带AsyncTask）并发执行。不爽！

  OK，如果你都遇到过，恭喜你，说明你的应用对开发者要求还是挺碉的。
那么是不是需要很多的代码才能完成这种和谐并发和任务调度呢？nooooo！有了Crossbow，我们只要一行代码。

  比方说场景2， Task3要等待Task1，Task2执行完才能执行，我们使用LiteAsync可以这样做：

```java
  TaskExecutor.newCyclicBarrierExecutor().put(task1).put(task2).start(task3);
```

这么一行代码，低调，内敛，而又充满能量，再多的任务可以执行，Task1，Task2并发执行，且随时可取消执行，结束（或取消）时会自动调度Task3执行。

#关于android并发
来谈谈并发，研究过Android系统源码的同学会发现：AsyncTask在android2.3的时候线程池是一个核心数为5线程，队列可容纳10线程，最大执行128个任务，这存在一个问题，当你真的有138个并发时，即使手机没被你撑爆，那么超出这个指标应用绝对crash掉。
后来升级到4.0，为了避免并发带来的一些列问题，AsyncTask竟然成为序列执行器了，也就是你即使你同时execute N个AsyncTask，它也是挨个排队执行的。
这一点请同学们一定注意，AsyncTask在4.0以后，是异步的没错，但不是并发的。

言归正传，我们来看看LiteAsync能做些什么吧：

#异步任务AsyncTask
1. Ameliorative AsyncTask：真正可并发，均衡手机能力与开销，针对短时间大量并发有调控策略，可在子线程执行。
2. SimpleTask：具备Ameliorative AsyncTask所有特性，简化了使用方法，仅设置一个泛型（结果类）即可。
3. SafeTask：具备Ameliorative AsyncTask所有特性，但是各个环节是安全的，能捕获任何异常，并传递给开发者。
4. CachedTask：具备Ameliorative AsyncTask所有特性，增加了对结果的缓存，可设置一个超时时间，只有在超时后才去异步执行，否则取缓存结果返回。
        
#任务调度器TaskExecutor
1. 顺序执行器，使一系列异步任务按序执行，非并发
2. 关卡执行器，使一系列异步任务并发执行，最后会调度执行一个终点任务
3. 延迟执行器，使一个异步任务延迟开发者指定的时间后执行
4. 心跳执行器，是一个异步任务按执行的间隔持续执行

恩，全部介绍完了，它很简单，却是最贴心的异步&并发爱心天使。
我在github工程里各自都谢了demo和案例，约10来个，足够你起步啦，现在就用起来吧骚年！
    
#个人开源站点：http://litesuits.com/

QQ交流群： 47357508    欢迎大牛入群交流，入群可以写明你的目的或研究方向~
