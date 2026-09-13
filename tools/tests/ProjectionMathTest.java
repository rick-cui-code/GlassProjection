package io.github.sixzleo.tabfold.projection;

public final class ProjectionMathTest {
    private static void near(double actual,double expected) {
        if(Math.abs(actual-expected)>1e-8) throw new AssertionError(actual+" != "+expected);
    }
    public static void main(String[] args) {
        for(int start:new int[]{1,3,10,30}){
            near(ProjectionMath.endpointOpacity(start-.01f,false,start,false),0);
            near(ProjectionMath.endpointOpacity(start,false,start,false),0);
            if(ProjectionMath.endpointOpacity(start+.01f,false,start,false)<=0)throw new AssertionError("must start fading immediately above threshold");
            near(ProjectionMath.endpointOpacity(start+4f,false,start,false),.5);
            near(ProjectionMath.endpointOpacity(start+8,false,start,false),1);
            near(ProjectionMath.endpointOpacity(start,true,start,false),0);
            near(ProjectionMath.endpointOpacity(start+4f,true,start,false),.5);
            near(ProjectionMath.endpointOpacity(165,true,start,false),1);
            near(ProjectionMath.endpointOpacity(170,true,start,false),.5);
            near(ProjectionMath.endpointOpacity(175,true,start,false),0);
            for(boolean inner:new boolean[]{false,true}){
                float previous=0;
                for(int step=0;step<=800;step++){
                    float angle=start+step/100f;
                    float opacity=ProjectionMath.endpointOpacity(angle,inner,start,false);
                    if(opacity<previous||opacity-previous>.002)throw new AssertionError("onset must be continuous and monotonic");
                    near(ProjectionMath.effectTilt(angle,inner,start,true),0);
                    previous=opacity;
                }
                near(ProjectionMath.effectTilt(start,inner,start,false),0);
                if(ProjectionMath.endpointOpacity(start+1,inner,start,false)>.05)throw new AssertionError("first degree must not pop in");
                float initialSlope=ProjectionMath.endpointOpacity(start+.01f,inner,start,false)/.01f;
                float finalSlope=(1-ProjectionMath.endpointOpacity(start+7.99f,inner,start,false))/.01f;
                if(initialSlope>.001||finalSlope>.001)throw new AssertionError("onset must have soft endpoints");
            }
            // Replay tilt-induced hinge readings while physically closed, on either panel.
            FoldPose closed=new FoldPose(true).withFoldStatus(1);
            for(int raw=0;raw<=30;raw++)for(boolean inner:new boolean[]{false,true}){
                FoldPose pose=closed.withAngle(raw);
                near(ProjectionMath.endpointOpacity(pose.rawAngle,inner,start,pose.blocksProjection()),0);
            }
            near(ProjectionMath.endpointOpacity(90,false,start,new FoldPose(true).blocksProjection()),0);
        }
        near(ProjectionMath.endpointOpacity(Float.NaN,false,1,false),0);
        near(ProjectionMath.endpointOpacity(Float.POSITIVE_INFINITY,false,1,false),0);
        near(ProjectionMath.clampStartAngle(-1),1);
        near(ProjectionMath.clampStartAngle(180),30);
        near(ProjectionMath.endpointOpacity(5f,false,0,false),.5);
        near(ProjectionMath.endpointOpacity(34f,false,180,false),.5);
        for(boolean inner:new boolean[]{false,true}) {
            for(int i=0;i<=100;i++) {
                double x=(inner?-1:1)*ProjectionMath.LEAF_WIDTH*i/100;
                double[] q=ProjectionMath.project(x,.045,inner?180:0,inner);
                near(q[0],x);near(q[1],.045);near(q[2],0);
            }
            for(int angle=0;angle<=180;angle++) {
                double[] edge=ProjectionMath.project(0,.052,angle,inner);
                near(edge[0],0);near(edge[1],.052);near(edge[2],0);
                double farEdge=(inner?-1:1)*ProjectionMath.LEAF_WIDTH;
                double visible=Math.abs(ProjectionMath.project(farEdge,.025,angle,inner)[0]);
                if(visible<ProjectionMath.LEAF_WIDTH*.6599||visible>ProjectionMath.LEAF_WIDTH+1e-8)throw new AssertionError("far crop must remain bounded");
                double previous=-1,previousU=-1;
                for(int i=0;i<=100;i++) {
                    double[] q=ProjectionMath.project((inner?-1:1)*ProjectionMath.LEAF_WIDTH*i/100,.025,angle,inner);
                    if(!Double.isFinite(q[0]) || !Double.isFinite(q[1]) || q[2]+1e-8<previous) throw new AssertionError("invalid/nonmonotonic gap");
                    near(q[1],.025);
                    double mapped=Math.abs(q[0]);
                    if(mapped<previousU || mapped>ProjectionMath.LEAF_WIDTH+1e-8)throw new AssertionError("content must fill the viewport without sampling outside the source or folding over");
                    previousU=mapped;
                    previous=q[2];
                }
                // Vertical size stays exact; perspective grows toward the far edge without collapsing.
                for(int i=1;i<90;i+=7){
                    double x=(inner?-1:1)*ProjectionMath.LEAF_WIDTH*i/100;
                    double dx=(inner?-1:1)*.003,dy=.006;
                    double[] a=ProjectionMath.project(x,-.02,angle,inner);
                    double[] b=ProjectionMath.project(x+dx,-.02,angle,inner);
                    double[] c=ProjectionMath.project(x,-.02+dy,angle,inner);
                    double scale=(b[0]-a[0])/dx;
                    if(scale<.3199||scale>1+1e-8)throw new AssertionError("excessive horizontal strain: "+scale);
                    near(b[1]-a[1],0);
                    near(c[0]-a[0],0);near(c[1]-a[1],dy);
                    if(Math.abs(a[0]-x)>ProjectionMath.LEAF_WIDTH*.3401+1e-8)throw new AssertionError("unbounded parallax");
                }
                double[] right=ProjectionMath.project(.043,.071,angle,true);
                near(right[0],.043);near(right[1],.071);near(right[2],0);
            }
            double far=(inner?-1:1)*ProjectionMath.LEAF_WIDTH;
            double folded=inner?120:60;
            double[] paper=ProjectionMath.paperPoint(far,.08,folded,inner);
            if(paper[1]<=.08)throw new AssertionError("paper contour must expose a black far corner");
            double[] content=ProjectionMath.project(far,.08,folded,inner);
            near(content[1],.08);
            near(content[2],paper[2]);
            double[] paperEdge=ProjectionMath.paperPoint(0,.08,folded,inner);
            near(paperEdge[0],0);near(paperEdge[1],.08);
            double previousCrop=-1;
            for(int tilt=0;tilt<=85;tilt++){
                double angle=inner?180-tilt:tilt;
                double crop=1-Math.abs(ProjectionMath.project(far,0,angle,inner)[0]/far);
                if(crop<previousCrop||crop>.3401)throw new AssertionError("far crop must increase smoothly with tilt");
                if(tilt==0)near(crop,0);
                if(Math.abs(crop-tilt*.004)>.000001)throw new AssertionError("far crop must stay linear throughout its angle range");
                // Equal angle steps must move interior samples by equal distances too.
                if(tilt>0 && tilt<85)for(double position:new double[]{.1,.5,.9}){
                    double x=far*position;
                    double before=ProjectionMath.project(x,0,inner?181-tilt:tilt-1,inner)[0];
                    double now=ProjectionMath.project(x,0,angle,inner)[0];
                    double after=ProjectionMath.project(x,0,inner?179-tilt:tilt+1,inner)[0];
                    near(now-before,after-now);
                }
                if(tilt==30 && (Math.abs(crop-.12)>.00001))throw new AssertionError("mid-angle crop must remain moderate");
                double nearHinge=far*.01;
                double nearScale=ProjectionMath.project(nearHinge,0,angle,inner)[0]/nearHinge;
                if(nearScale<.995||nearScale>1)throw new AssertionError("hinge-side content must remain stable");
                previousCrop=crop;
            }
        }
        near(ProjectionMath.DEFAULT_STRETCH_PERCENT,100);
        near(ProjectionMath.clampStretchPercent(-100),0);
        near(ProjectionMath.clampStretchPercent(999),125);
        near(ProjectionMath.cropFraction(Float.NaN,false,100),0);
        for(boolean inner:new boolean[]{false,true})for(int percent:new int[]{0,25,50,100,125}){
            double expected=.12*percent/100;
            if(Math.abs(ProjectionMath.cropFraction(inner?150:30,inner,percent)-expected)>.000001)throw new AssertionError("configured crop did not scale from default");
            for(int tilt=0;tilt<=85;tilt++){
                float angle=inner?180-tilt:tilt;
                double previous=-1;
                for(int point=0;point<=100;point++){
                    double x=(inner?-1:1)*ProjectionMath.LEAF_WIDTH*point/100;
                    double[] q=ProjectionMath.project(x,.02,angle,inner,percent);
                    double distance=Math.abs(q[0]);
                    if(distance<previous||distance>ProjectionMath.LEAF_WIDTH+1e-8)throw new AssertionError("configured perspective folds over or leaves source bounds");
                    if(point>0 && distance-previous<ProjectionMath.LEAF_WIDTH/100*.149)throw new AssertionError("configured perspective collapses near far edge");
                    near(q[1],.02);
                    if(percent==0)near(q[0],x);
                    if(percent==100)near(q[0],ProjectionMath.project(x,.02,angle,inner)[0]);
                    previous=distance;
                }
                near(ProjectionMath.project(0,0,angle,inner,percent)[0],0);
                near(ProjectionMath.project(.04,0,angle,true,percent)[0],.04);
            }
        }
        System.out.println("PASS: current default preserved, zero/half/full/maximum strength, hinge and inner-right anchors, no foldover at configured extremes");
        // The outer hinge middle must defocus with opening, without a local clear pocket.
        double previousHingeGap=0;
        for(int angle=0;angle<=85;angle++){
            double previousGap=-1;
            for(int point=0;point<=100;point++){
                double x=ProjectionMath.LEAF_WIDTH*point/100;
                double d=ProjectionMath.blurDistance(x,angle,false);
                if(d+1e-8<x)throw new AssertionError("outer blur must not weaken existing blur");
                for(double y:new double[]{-.08,-.04,0,.04,.08}){
                    double gap=ProjectionMath.paperPoint(d,y,angle,false)[2];
                    if(angle==0)near(gap,0);
                    else if(gap<=0)throw new AssertionError("opened outer hinge retains a focused pocket");
                    if(point==100)near(gap,ProjectionMath.paperPoint(x,y,angle,false)[2]);
                }
                double gap=ProjectionMath.paperPoint(d,0,angle,false)[2];
                if(gap<previousGap)throw new AssertionError("outer blur must vary continuously toward far edge");
                previousGap=gap;
            }
            double hingeGap=ProjectionMath.paperPoint(ProjectionMath.blurDistance(0,angle,false),0,angle,false)[2];
            if(hingeGap<previousHingeGap||hingeGap-previousHingeGap>.0003)throw new AssertionError("hinge blur must follow opening smoothly");
            previousHingeGap=hingeGap;
        }
        System.out.println("PASS: outer hinge defocuses across its full height, smooth angle onset, neutral closed state, unchanged far-edge blur");
        double previousWidth=0;
        for(int tilt=0;tilt<=85;tilt++){
            float angle=180-tilt;
            double width=ProjectionMath.spillWidth(angle,true);
            if(width<previousWidth||width>ProjectionMath.LEAF_WIDTH*.22001)throw new AssertionError("blur spill must grow with closing angle, within the adjacent strip");
            previousWidth=width;
            near(ProjectionMath.spillWidth(angle,false),0);
            near(ProjectionMath.spillCoverage(-.02,angle,true),1);
            near(ProjectionMath.spillCoverage(0,angle,true),1);
            near(ProjectionMath.blurDistance(-ProjectionMath.LEAF_WIDTH,angle,true),ProjectionMath.LEAF_WIDTH);
            if(tilt==0){near(width,0);near(ProjectionMath.spillCoverage(.001,angle,true),0);continue;}
            if(ProjectionMath.blurDistance(0,angle,true)<=0)throw new AssertionError("hinge must retain blur instead of being cleared");
            near(ProjectionMath.spillCoverage(width,angle,true),0);
            near(ProjectionMath.blurDistance(width,angle,true),0);
            near(ProjectionMath.spillCoverage(width*.5,angle,true),.5);
            double previous=1;
            for(int i=0;i<=100;i++){
                double x=width*i/100;
                double coverage=ProjectionMath.spillCoverage(x,angle,true);
                if(coverage>previous||previous-coverage>.016)throw new AssertionError("blur tail must fade continuously on stationary half");
                for(int strength:new int[]{0,100,125})near(ProjectionMath.project(x,.02,angle,true,strength)[0],x);
                previous=coverage;
            }
            // Both sides of the physical hinge must share the same blur field.
            double epsilon=1e-9;
            if(Math.abs(ProjectionMath.blurDistance(-epsilon,angle,true)-ProjectionMath.blurDistance(epsilon,angle,true))>2.1e-9)throw new AssertionError("blur field broke across hinge");
        }
        System.out.println("PASS: angle-dependent cross-hinge tail, full blur-side coverage, unchanged far edge, stationary receiving half, clear spill endpoint");
        near(ProjectionMath.followAngle(Float.NaN,30,8),30);
        near(ProjectionMath.followAngle(20,Float.NaN,8),20);
        near(ProjectionMath.followAngle(20,30,-1),20);
        float response=ProjectionMath.followAngle(0,60,36);
        if(response<57||response>60)throw new AssertionError("follow must reach 95% within 36 ms without overshoot");
        float stepped=0;
        for(int frame=0;frame<6;frame++)stepped=ProjectionMath.followAngle(stepped,60,6);
        if(Math.abs(stepped-response)>.0001)throw new AssertionError("follow must be independent of frame rate");
        float reversed=ProjectionMath.followAngle(response,10,8);
        if(reversed>=response||reversed<10)throw new AssertionError("reversal must follow immediately without overshoot");
        System.out.println("PASS: clear endpoints, hinge anchored, linear far-side and interior motion, bounded perspective, unchanged vertical scale, fixed inner right, monotonic depth blur at every angle");
        System.out.println("PASS: adjustable fade boundaries, unchanged flat endpoint, physical closure and pending sensor override every threshold");
    }
}
