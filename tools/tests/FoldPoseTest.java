package io.github.sixzleo.tabfold.projection;

import io.github.sixzleo.tabfold.probe.EarlyDisplayModel;

public final class FoldPoseTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        FoldPose pose=new FoldPose(true).withAngle(30);
        check(pose.blocksProjection()&&pose.angle()==0,"wait for initial physical state before showing effects");
        pose=pose.withFoldStatus(1);
        FoldHoldGate hold=new FoldHoldGate();EarlyDisplayModel display=new EarlyDisplayModel();
        int now=0;
        for(int repeat=0;repeat<40;repeat++)for(float raw:new float[]{0,1,3,4,8,23,30,27,2,0}){
            pose=pose.withAngle(raw);now+=20;
            check(pose.rawAngle==raw&&pose.angle()==0&&pose.blocksProjection(),"closed tilt must not reanimate");
            check(!hold.update(now,pose.angle(),true),"closed tilt must not start hold/resume");
            check(display.update(pose.angle(),true,false)==-1,"closed tilt must release screen override");
        }
        pose=pose.withAngle(4).withFoldStatus(0);
        check(!pose.blocksProjection()&&pose.angle()==4,"physical opening resumes immediately without a larger angle threshold");
        check(display.update(pose.angle(),true,false)==0,"opening cover animation remains available");
        pose=pose.withAngle(60);
        check(display.update(pose.angle(),true,false)==2,"configured inner handoff remains available");
        pose=pose.withAngle(179).withFoldStatus(1);
        check(pose.angle()==0&&pose.blocksProjection(),"physical closure wins even before hinge catches up");
        pose=pose.withFoldStatus(Float.NaN).withFoldStatus(2).withFoldStatus(-1).withAngle(Float.POSITIVE_INFINITY);
        check(pose.foldStatus==1&&pose.rawAngle==179,"invalid events preserve last valid snapshot");
        pose=pose.withFoldStatus(0).withAngle(90);
        check(pose.angle()==90,"reopening restores raw hinge tracking");
        FoldPose unsupported=new FoldPose(false).withAngle(45).withFoldStatus(1);
        check(!unsupported.blocksProjection()&&unsupported.angle()==45,"unsupported sensor preserves existing behavior");
        check(new FoldPose(true).blocksProjection(),"service restart waits for a fresh physical event");
        System.out.println("PASS: closed tilt replay, startup, immediate opening, handoff, delayed hinge, invalid events and fallback");
    }
}
