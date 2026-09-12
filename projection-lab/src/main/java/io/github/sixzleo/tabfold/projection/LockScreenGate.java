package io.github.sixzleo.tabfold.projection;

/** Retain a confirmed lock scene through short window-tree handoffs only. */
final class LockScreenGate {
    private boolean confirmed,wasInteractive;
    private long lastSeen,wakeAt;
    boolean visible(boolean locked,boolean interactive,int observed,long now) {
        if(!locked || observed<0){confirmed=false;wasInteractive=interactive;return false;}
        if(!interactive){wasInteractive=false;return false;}
        if(!wasInteractive)wakeAt=now;
        wasInteractive=true;
        if(observed>0){confirmed=true;lastSeen=now;}
        return confirmed&&(now-lastSeen<1000 || now-wakeAt<250);
    }
}
