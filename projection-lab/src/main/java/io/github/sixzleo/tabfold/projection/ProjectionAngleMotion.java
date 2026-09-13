package io.github.sixzleo.tabfold.projection;

/** Visual-only endpoint return; control/pose telemetry stays physically gated. */
public final class ProjectionAngleMotion {
    public static final long FLAT_RETURN_MS=100;
    private float angle=Float.NaN,flatFrom;
    private long lastAt,flatAt;
    private boolean flat;

    public static boolean hardBlocked(boolean blocked,boolean fullyOpened,boolean inner){
        // Only the inner flat endpoint may finish an existing animation.
        // Closure, pending closure detection and outer-panel endpoints clear now.
        return blocked&&!(fullyOpened&&inner);
    }
    public float update(long now,float target,boolean inner,boolean blocked,boolean fullyOpened,boolean sceneChanged){
        boolean nextFlat=inner&&fullyOpened;
        if(!Float.isFinite(target)){
            angle=Float.NaN;flat=false;lastAt=now;return angle;
        }
        if(sceneChanged||!Float.isFinite(angle)||hardBlocked(blocked,fullyOpened,inner)){
            angle=nextFlat?180:target;flatFrom=angle;flatAt=lastAt=now;flat=nextFlat;return angle;
        }
        if(nextFlat){
            if(!flat){flatFrom=angle;flatAt=now;}
            float t=Math.max(0,Math.min(1,(now-flatAt)/(float)FLAT_RETURN_MS));
            angle=flatFrom+(180-flatFrom)*t*t*(3-2*t);
        }else if(!flat){
            angle=ProjectionMath.followAngle(angle,target,now-lastAt,inner);
        }
        // A reversal keeps the last visible pose on its first frame, then
        // follows the hinge. There is no queued finish or reset to raw angle.
        flat=nextFlat;lastAt=now;return angle;
    }
}
