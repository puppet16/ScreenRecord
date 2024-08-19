package cn.luck.screenrecord.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
/**
 * ============================================================
 *
 * @author 李桐桐
 * date    2024/8/19
 * desc    描述
 * ============================================================
 **/
class OrderWorkManager {
    private val scope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    private val workChannel = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        // 启动一个协程从通道中读取任务并按顺序执行
        scope.launch {
            for (work in workChannel) {
                work()
            }
        }
    }

    fun <R> addWork(block: suspend () -> R, callback: WorkCallback<R>? = null) {
        scope.launch {
            workChannel.send {
                Result.runCatching {
                    withContext(Dispatchers.IO) {
                        block.invoke()
                    }
                }.run {
                    withContext(Dispatchers.Main) {
                        onSuccess { result -> callback?.onSuccess(result) }
                        onFailure { error -> callback?.onError(error) }
                    }
                }
            }
        }
    }

    interface WorkCallback<R> {
        fun onSuccess(data: R)
        fun onError(exception: Throwable)
    }

    fun release() {
        scope.coroutineContext.cancelChildren()
    }
}