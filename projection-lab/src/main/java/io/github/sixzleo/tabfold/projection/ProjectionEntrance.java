package io.github.sixzleo.tabfold.projection;

/** Join the untransformed desktop before following an already nonzero sensor pose. */
public final class ProjectionEntrance {
    private boolean active;
    private long started;
    private int duration=180;
    public float update(long now,boolean visible,boolean sceneChanged){
        return update(now,visible,sceneChanged,now);
    }
    public float update(long now,boolean visible,boolean sceneChanged,long sceneStartedAt){
        return update(now,visible,sceneChanged,sceneStartedAt,180);
    }
    public float update(long now,boolean visible,boolean sceneChanged,long sceneStartedAt,int handoffDuration){
        if(!visible){active=false;return 0;}
        // A panel transition already underway must not restart when its first frame arrives.
        // Ordinary closed/flat-to-moving starts still join from neutral at this frame.
        if(!active||sceneChanged){
            active=true;
            duration=sceneChanged?Math.max(1,handoffDuration):180;
            started=sceneChanged?Math.max(now-duration,Math.min(now,sceneStartedAt)):now;
        }
        float t=Math.max(0,Math.min(1,(now-started)/(float)duration));
        return t*t*(3-2*t);
    }
}
