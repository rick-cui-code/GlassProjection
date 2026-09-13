package io.github.sixzleo.tabfold.projection;

/** Hinge-anchored perspective crops the far source edge while filling the screen. */
public final class ProjectionMath {
    public static final float LEAF_WIDTH=.073f, HEIGHT=.16f;
    public static final float CROP_PER_DEGREE=.004f;
    public static final float INNER_SPILL_FRACTION=.22f;
    public static final float OUTER_HINGE_DISTANCE_FRACTION=.18f;
    public static double spillWidth(float angle,boolean inner){
        return inner&&Float.isFinite(angle)?LEAF_WIDTH*INNER_SPILL_FRACTION*Math.sin(Math.toRadians(tilt(angle,true))):0;
    }
    public static float spillCoverage(double x,float angle,boolean inner){
        if(!inner||x<=0)return 1;
        double width=spillWidth(angle,true);
        return width<=0?0:1-smoothUnit((float)(x/width));
    }
    public static double blurDistance(double x,float angle,boolean inner){
        // Lift the entire hinge edge out of the focal plane; keep the far endpoint.
        if(!inner)return LEAF_WIDTH*OUTER_HINGE_DISTANCE_FRACTION+Math.abs(x)*(1-OUTER_HINGE_DISTANCE_FRACTION);
        double width=spillWidth(angle,true);
        // Move the clear endpoint across the hinge, preserving far-edge distance.
        return Math.max(0,width-x)/(1+width/LEAF_WIDTH);
    }
    public static final int DEFAULT_STRETCH_PERCENT=100, MAX_STRETCH_PERCENT=125;
    public static int clampStretchPercent(int percent){return Math.max(0,Math.min(MAX_STRETCH_PERCENT,percent));}
    public static float followAngle(float current,float target,long elapsedMs){
        return followAngle(current,target,elapsedMs,false);
    }
    public static float followAngle(float current,float target,long elapsedMs,boolean inner){
        if(!Float.isFinite(target))return current;
        if(!Float.isFinite(current))return target;
        // Measure onset from the active screen's clear endpoint. Inner folding
        // starts at 180 degrees; outer unfolding starts at zero.
        float onsetAngle=inner?180-target:target;
        // Integer hinge readings are most visible before the blur develops.
        // The visible 4-9 degree onset needs more subframes than the near-clear
        // endpoint: at 120 Hz the first frame advances about 11%, with 75%
        // covered over 12 frames (100 ms). Use elapsed time, not queued frames,
        // so reversal takes effect immediately and other refresh rates agree.
        // Ease into this extra smoothing over 2-4 degrees, then out over 9-14.
        float visibleOnset=smoothUnit((onsetAngle-2)/2)*(1-smoothUnit((onsetAngle-9)/5));
        double timeConstantMs=12+12*(1-smoothUnit((onsetAngle-10)/4))+48*visibleOnset;
        float step=(float)(1-Math.exp(-Math.max(0,elapsedMs)/timeConstantMs));
        return current+(target-current)*step;
    }
    public static int clampStartAngle(int angle){return Math.max(1,Math.min(30,angle));}
    private static float smoothUnit(float value){
        float t=Math.max(0,Math.min(1,value));
        return t*t*(3-2*t);
    }
    public static float openingAmount(float angle,int startAngle,boolean physicallyBlocked){
        if(physicallyBlocked||!Float.isFinite(angle))return 0;
        return smoothUnit((angle-clampStartAngle(startAngle))/8);
    }
    public static float endpointOpacity(float angle,boolean inner,int startAngle,boolean physicallyBlocked){
        float opening=openingAmount(angle,startAngle,physicallyBlocked);
        if(opening==0)return 0;
        return inner?Math.min(opening,smoothUnit((175-angle)/10)):opening;
    }
    /** Morph one opaque image from neutral instead of crossfading shifted copies. */
    public static float onsetMotion(float endpoint,float entry){
        if(!Float.isFinite(endpoint)||!Float.isFinite(entry))return 0;
        return Math.max(0,Math.min(1,endpoint))*Math.max(0,Math.min(1,entry));
    }
    public static float onsetOpacity(float endpoint){
        return Float.isFinite(endpoint)&&endpoint>0?1:0;
    }
    public static float effectTilt(float angle,boolean inner,int startAngle,boolean physicallyBlocked){
        float opening=openingAmount(angle,startAngle,physicallyBlocked);
        return opening==0?0:tilt(angle,inner)*opening;
    }
    public static float tilt(float angle,boolean inner) {
        // A real display can be active while its virtual face points away from
        // the reference camera. Limit that unobservable view to a grazing pose.
        return Math.min(85,Math.max(0,inner?180-angle:angle));
    }
    public static float cropFraction(float angle,boolean inner){
        return cropFraction(angle,inner,DEFAULT_STRETCH_PERCENT);
    }
    public static float cropFraction(float angle,boolean inner,int stretchPercent){
        return Float.isFinite(angle)?tilt(angle,inner)*CROP_PER_DEGREE*(clampStretchPercent(stretchPercent)/100f):0;
    }
    public static double[] project(double x,double y,double angle,boolean inner) {
        return project(x,y,angle,inner,DEFAULT_STRETCH_PERCENT);
    }
    public static double[] project(double x,double y,double angle,boolean inner,int stretchPercent) {
        if(inner && x>=0) return new double[]{x,y,0};
        double depth=paperPoint(x,y,angle,inner)[2];
        double a=Math.max(0,Math.min(1,Math.abs(x)/LEAF_WIDTH));
        // Each sample moves proportionally to angle, with stronger displacement
        // toward the far side and a stationary hinge. Output still fills the leaf.
        double crop=cropFraction((float)angle,inner,stretchPercent);
        return new double[]{x*(1-crop*a),y,depth};
    }
    public static double[] paperPoint(double x,double y,double angle,boolean inner){
        if(inner&&x>=0)return new double[]{x,y,0};
        double theta=Math.toRadians(tilt((float)angle,inner));
        double px=x*Math.cos(theta),pz=Math.abs(x)*Math.sin(theta);
        double ex=inner?0:LEAF_WIDTH*.5,k=.6/(.6-pz);
        double qx=ex+(px-ex)*k,qy=y*k;
        double distance=Math.sqrt((qx-px)*(qx-px)+(qy-y)*(qy-y)+pz*pz);
        return new double[]{qx,qy,distance};
    }
    public static int turn(boolean inner,int rotation) { return inner?(rotation+1)%4:rotation; }
}
