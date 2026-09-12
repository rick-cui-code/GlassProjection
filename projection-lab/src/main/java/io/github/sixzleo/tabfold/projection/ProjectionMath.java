package io.github.sixzleo.tabfold.projection;

/** Independent analytic model of Ocisly14/iphone_duo's documented ray/plane method. */
public final class ProjectionMath {
    public static final float LEAF_WIDTH=.073f, HEIGHT=.16f, EYE_Z=.6f;
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
