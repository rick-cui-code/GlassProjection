package io.github.sixzleo.tabfold.projection;

/** Independent analytic model of Ocisly14/iphone_duo's documented ray/plane method. */
public final class ProjectionMath {
    public static final float LEAF_WIDTH=.073f, HEIGHT=.16f, EYE_Z=.6f;
    public static int clampStartAngle(int angle){return Math.max(1,Math.min(30,angle));}
    public static float endpointOpacity(float angle,boolean inner,int startAngle,boolean physicallyBlocked){
        if(physicallyBlocked||!Float.isFinite(angle)||angle<=clampStartAngle(startAngle))return 0;
        float opening=Math.min(1,(angle-clampStartAngle(startAngle))/5);
        return inner?Math.min(opening,Math.max(0,(175-angle)/10)):opening;
    }
    public static float tilt(float angle,boolean inner) {
        // A real display can be active while its virtual face points away from
        // the reference camera. Limit that unobservable view to a grazing pose.
        return Math.min(85,Math.max(0,inner?180-angle:angle));
    }
    public static double[] project(double x,double y,double angle,boolean inner) {
        if(inner && x>=0) return new double[]{x,y,0};
        double theta=Math.toRadians(tilt((float)angle,inner));
        double px=x*Math.cos(theta),pz=Math.abs(x)*Math.sin(theta);
        double ex=inner?0:LEAF_WIDTH*.5;
        double k=EYE_Z/(EYE_Z-pz);
        double qx=ex+(px-ex)*k,qy=y*k;
        double distance=Math.sqrt((qx-px)*(qx-px)+(qy-y)*(qy-y)+pz*pz);
        return new double[]{qx,qy,distance};
    }
    public static int turn(boolean inner,int rotation) { return inner?(rotation+1)%4:rotation; }
}
