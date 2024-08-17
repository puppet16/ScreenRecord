package cn.luck.screenrecord.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ============================================================
 *
 * @author 李桐桐
 * date    2023/9/13
 * desc    使用协程进行线程切换，在IO线程做耗时操作
 * ============================================================
 **/
internal class WorkManager {

    // 协程作用域用于统一管理协程，
    // 其中 SupervisorJob 的子协程发生异常被取消时不会同时取消SupervisorJob的其它子协程，且错误让协程自行处理
    private val scope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.IO) }


    /**
     * 添加耗时操作放到 IO 线程里执行，之后切到主线程回调执行结果
     * @param block Function0<R>
     * @param callback NetCallback<R>
     */
    fun <R> addWork(block: () -> R, callback: WorkCallback<R>) {
        scope.launch {
            // Result 会将 block 执行的所有异常情况放到 failure 中，将正常执行的结果放到 success 中
            Result.runCatching {
                block.invoke()
            }.run {
                // 切换到主线程
                withContext(Dispatchers.Main) {
                    // 根据 Result 的回调返回用户注册的回调结果
                    onSuccess { result -> callback.onSuccess(result) }
                    onFailure { error -> callback.onError(error) }
                }
            }
        }
    }

    /**
     * 用户注册的回调协议
     * @param R
     */
    interface WorkCallback<R> {
        fun onSuccess(data: R)
        fun onError(exception: Throwable)
    }

    /**
     * 取消正在活跃的协程
     */
    fun release() {
        // 使用此方式取消协程后，再添加协程不会出现无法执行的情况
        scope.coroutineContext.cancelChildren()
    }
}