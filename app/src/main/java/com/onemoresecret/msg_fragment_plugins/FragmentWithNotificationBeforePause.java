package com.onemoresecret.msg_fragment_plugins;

import androidx.fragment.app.Fragment;

public abstract class FragmentWithNotificationBeforePause extends Fragment {
    protected Runnable beforePause = null;
    /**
     * A fragment is paused when the confirmation dialog is raised ("send to" or "BT discovery").
     * This is to notify the parent, that this is about to happen.
     */

    public void setBeforePause(Runnable r) {
        this.beforePause = r;
    }
}
