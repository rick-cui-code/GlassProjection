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
        pose=new FoldPose(true,true).withAngle(19);
        check(pose.blocksProjection(),"contact profile starts blocked");
        // Captured while already visibly open: coarse CLOSED, contact released.
        pose=pose.withFoldEvent(new float[]{1,11,1,2,0,3,-.0125f,-.0171f,-477.45f,681,1630});
        check(pose.foldStatus==1&&pose.contactStatus==1&&!pose.blocksProjection()&&pose.angle()==19,"small opening overrides coarse closed range");
        check(ProjectionMath.endpointOpacity(pose.angle(),false,1,pose.blocksProjection())==1,"reported 19 degree opening renders");
        pose=pose.withAngle(2);
        check(ProjectionMath.endpointOpacity(pose.angle(),false,1,pose.blocksProjection())>0,"contact release allows low-angle fade");
        // Captured full closure plus tilt: noisy hinge, contact remains 0.
        pose=pose.withFoldEvent(new float[]{1,10,0,2,0,1,.1463f,-.9529f,-1808.7f,681,1630});
        for(float raw:new float[]{0,7,10,11,19,30,36}){
            pose=pose.withAngle(raw);
            check(pose.blocksProjection()&&pose.angle()==0,"contact closure suppresses entire tilt range");
        }
        pose=pose.withFoldEvent(new float[]{0,90,0,2,2,3,0,0,-1800,681,1630});
        check(pose.blocksProjection(),"contact closure wins over stale coarse OPEN");
        pose=pose.withFoldEvent(new float[]{0,90,Float.NaN,2,2,3,0,0,-1800,681,1630});
        check(pose.blocksProjection(),"invalid contact keeps last known closure");
        pose=pose.withFoldEvent(new float[]{0,90});
        check(pose.blocksProjection(),"short event does not discard valid contact state");
        FoldPose legacy=new FoldPose(true).withAngle(19).withFoldEvent(new float[]{1,11,1,2,0,3,0,0,-328,681,1630});
        check(legacy.contactStatus==-2&&legacy.blocksProjection(),"unvalidated devices ignore vendor contact field");
        check(!new FoldPose(true,true).withFoldEvent(new float[]{0}).blocksProjection(),"missing contact field falls back to coarse state");
        System.out.println("PASS: captured small-opening/contact-closure replay, low-angle fade, invalid contact and device fallback");
        System.out.println("PASS: closed tilt replay, startup, immediate opening, handoff, delayed hinge, invalid events and fallback");
    }
}
