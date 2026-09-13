package io.github.sixzleo.tabfold.probe;

/** Time-based return of projection geometry to the undeformed screen plane. */
final class FoldReturnMotion {
    private boolean initialized,held;
    private long started;
    private float from=1,target=1;
    float update(long now,boolean suspended,boolean physicallyBlocked){
        if(physicallyBlocked){
            // A physical endpoint clears this frame, including an interrupted hold return.
            // The next real opening starts with fresh motion instead of resuming the old pose.
            initialized=false;
            return 0;
        }
        return update(now,suspended);
    }
    float update(long now,boolean suspended){
        if(!initialized){initialized=true;held=suspended;from=target=suspended?0:1;started=now;}
        if(held!=suspended){
            from=value(now);held=suspended;target=suspended?0:1;started=now;
        }
        return value(now);
    }
    private float value(long now){
        float t=Math.max(0,Math.min(1,(now-started)/(held?220f:160f)));
        float ease=t*t*(3-2*t);
        return from+(target-from)*ease;
    }
    static float coverage(float amount){
        float t=Math.max(0,Math.min(1,amount/.15f));
        return t*t*(3-2*t);
    }
}
