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
            near(ProjectionMath.endpointOpacity(start+2.5f,false,start,false),.5);
            near(ProjectionMath.endpointOpacity(start+5,false,start,false),1);
            near(ProjectionMath.endpointOpacity(start,true,start,false),0);
            near(ProjectionMath.endpointOpacity(start+2.5f,true,start,false),.5);
            near(ProjectionMath.endpointOpacity(165,true,start,false),1);
            near(ProjectionMath.endpointOpacity(170,true,start,false),.5);
            near(ProjectionMath.endpointOpacity(175,true,start,false),0);
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
        near(ProjectionMath.endpointOpacity(3.5f,false,0,false),.5);
        near(ProjectionMath.endpointOpacity(32.5f,false,180,false),.5);
        for(boolean inner:new boolean[]{false,true}) {
            for(int i=0;i<=100;i++) {
                double x=(inner?-1:1)*ProjectionMath.LEAF_WIDTH*i/100;
                double[] q=ProjectionMath.project(x,.045,inner?180:0,inner);
                near(q[0],x);near(q[1],.045);near(q[2],0);
            }
            for(int angle=0;angle<=180;angle++) {
                double[] edge=ProjectionMath.project(0,.052,angle,inner);
                near(edge[0],0);near(edge[1],.052);near(edge[2],0);
                double previous=-1;
                for(int i=0;i<=100;i++) {
                    double[] q=ProjectionMath.project((inner?-1:1)*ProjectionMath.LEAF_WIDTH*i/100,.025,angle,inner);
                    if(!Double.isFinite(q[0]) || !Double.isFinite(q[1]) || q[2]+1e-8<previous) throw new AssertionError("invalid/nonmonotonic gap");
                    previous=q[2];
                }
                double[] right=ProjectionMath.project(.043,.071,angle,true);
                near(right[0],.043);near(right[1],.071);near(right[2],0);
            }
        }
        System.out.println("PASS: clear endpoints, anchored edges, fixed inner right, finite and monotonic distance at every angle");
        System.out.println("PASS: adjustable fade boundaries, unchanged flat endpoint, physical closure and pending sensor override every threshold");
    }
}
