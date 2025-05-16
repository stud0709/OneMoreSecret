package com.onemoresecret.msg_fragment_plugins

import androidx.fragment.app.Fragment

abstract class FragmentWithNotificationBeforePause : Fragment() {
    @JvmField
    protected var beforePause: Runnable? = null

    /**
     * A fragment is paused when the confirmation dialog is raised ("send to" or "BT discovery").
     * This is to notify the parent, that this is about to happen.
     */
    open fun setBeforePause(r: Runnable?) {
        this.beforePause = r
    }
}
