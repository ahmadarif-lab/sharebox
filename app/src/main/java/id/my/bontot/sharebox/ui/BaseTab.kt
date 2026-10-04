package id.my.bontot.sharebox.ui

import android.view.LayoutInflater
import android.view.View
import id.my.bontot.sharebox.MainActivity

abstract class BaseTab(protected val act: MainActivity) {

    val root: View = LayoutInflater.from(act).inflate(layoutId(), null)

    protected abstract fun layoutId(): Int

    open fun onShow() {}

    open fun onHide() {}

    open fun onBack(): Boolean = false

    open fun onPermissionsResult(requestCode: Int, grantResults: IntArray) {}
}
