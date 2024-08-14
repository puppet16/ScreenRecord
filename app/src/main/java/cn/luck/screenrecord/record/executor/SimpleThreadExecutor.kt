package cn.luck.screenrecord.record.executor

import android.os.Handler
import android.os.Looper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * ============================================================
 *
 * @author 李桐桐
 * date    2023/4/14
 * desc    线程池单例，适用需要快速处理突发性强、耗时较短的任务场景，拒绝策略为抛弃最老任务策略 DiscardOldestPolicy
 * ============================================================
 **/
class SimpleThreadExecutor {
    companion object {
        @JvmStatic
        val instance: SimpleThreadExecutor by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            SimpleThreadExecutor()
        }
    }

    /**
     * 主线程 handler， 用于切换到主线程
     */
    private val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }


    private val executor: ExecutorService by lazy {
        createExecutor()
    }

    /**
     * 线程池 CachedThreadPool + 抛弃最老任务策略
     * CachedThreadPool 默认的拒绝策略会抛异常
     */
    private fun createExecutor(): ExecutorService {
        return ThreadPoolExecutor(
            0, Integer.MAX_VALUE,
            60L, TimeUnit.SECONDS,
            SynchronousQueue<Runnable>(), Executors.defaultThreadFactory(),
            ThreadPoolExecutor.DiscardOldestPolicy()
        )
    }


    /**
     * 提交任务
     * @param block Function0<R>
     * @param callback DVMCommonCallback<R>?
     */
    fun <R> submit(block: () -> R, callback: SimpleThreadExecutorCallback<R>? = null) {
        executor.submit {
            val result = block.invoke()
            mainHandler.post(CallbackRunnable(result, callback))
        }
    }

    /**
     * 直接关闭
     */
    fun shutdownNow() {
        executor.shutdownNow()
    }

    /**
     * 主线程回调使用，handler post之后 该 runnable 执行 run 方法，则执行 callback 回调
     * @param T
     * @property data T
     * @property callback ExecutorCallback<T>?
     * @constructor
     */
    private class CallbackRunnable<T>(private val data: T, private val callback: SimpleThreadExecutorCallback<T>?) : Runnable {
        override fun run() {
            callback?.onComplete(true, data)
        }
    }

}