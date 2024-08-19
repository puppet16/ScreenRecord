package cn.luck.screenrecord.record3.splicechecker

/**
 * ============================================================
 *
 * @author 李桐桐
 * date    2024/8/19
 * desc    描述
 * ============================================================
 **/
interface ISpliceChecker {
    fun checkSplice(callback: (Boolean) -> Unit)

    fun stopCheck()
}