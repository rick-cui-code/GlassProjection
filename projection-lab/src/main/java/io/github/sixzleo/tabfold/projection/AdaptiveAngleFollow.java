package io.github.sixzleo.tabfold.projection;

/** Preserve slow integer-step smoothing; shorten lag only for deliberate movement. */
public final class AdaptiveAngleFollow {
    private long frameAt,changeAt,runAt,boostAt;
    private float target=Float.NaN,runDistance,boost;
    private int direction;
    public void reset(long now,float value){frameAt=changeAt=runAt=boostAt=now;target=value;runDistance=boost=0;direction=0;}
    public float update(long now,float current,float next,boolean inner){
        if(!Float.isFinite(next))return current;
        if(!Float.isFinite(target)){reset(now,next);return next;}
        long elapsed=Math.max(0,now-frameAt);
        float delta=next-target;
        float strength=boost*Math.max(0,1-(now-boostAt)/120f);
        if(delta!=0){
            int sign=delta>0?1:-1;
            if(sign!=direction||now-changeAt>120||now-runAt>120){runAt=changeAt;runDistance=0;}
            runDistance+=Math.abs(delta);direction=sign;
            float speed=runDistance*1000/Math.max(16,now-runAt);
            // Alternating one-degree jitter never accumulates a directional run.
            float measured=runDistance>=3?Math.max(0,Math.min(1,(speed-30)/90)):0;
            if(Math.abs(delta)>=4)measured=1;
            strength=Math.max(strength,measured);boost=strength;boostAt=now;
            target=next;changeAt=now;
        }
        frameAt=now;
        return ProjectionMath.followAngle(current,next,elapsed,inner,strength);
    }
}
