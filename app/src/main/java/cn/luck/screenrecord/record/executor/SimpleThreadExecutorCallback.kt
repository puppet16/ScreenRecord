package cn.luck.screenrecord.record.executor
/**
 * ============================================================
 *
 * @author 李桐桐
 * date    2023/4/13
 * desc    线程任务执行完成后的结果回调
 * ============================================================
 **/
interface SimpleThreadExecutorCallback<T> {
    fun onComplete(success: Boolean, data: T, message: String = "")
}