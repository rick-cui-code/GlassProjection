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
        // Captured with the hinge held flat while the whole phone was tilted:
        // Android hinge angles crossed the animation boundary but posture stayed 3.
        float[] flat={0,162,1,2,3,3,-1.1733302f,-.27828494f,-215.85f,681,1630};
        float[] folding={0,158,1,2,2,3,.0019744812f,.03203462f,-215.25002f,681,1630};
        pose=new FoldPose(true,true).withAngle(172).withFoldEvent(flat);
        check(pose.postureStatus==3&&!pose.fullyOpened()&&pose.angle()==172,"startup with OPENED below 175 still waits for both conditions");
        pose=pose.withAngle(174.99f);
        check(!pose.blocksProjection()&&pose.angle()==174.99f,"below threshold must continue raw hinge tracking");
        pose=pose.withAngle(175);
        check(pose.fullyOpened()&&pose.angle()==180,"OPENED first, then exact 175 latches protection");
        FoldPose angleFirst=new FoldPose(true,true).withAngle(179).withFoldEvent(folding);
        check(!angleFirst.fullyOpened()&&angleFirst.angle()==179,"angle alone never latches protection");
        angleFirst=angleFirst.withFoldEvent(flat);
        check(angleFirst.fullyOpened(),"angle first, then OPENED also latches");
        angleFirst=angleFirst.withFoldEvent(folding).withAngle(170).withFoldEvent(flat);
        check(!angleFirst.fullyOpened(),"a previous cycle reaching 175 must not arm the next cycle");
        hold=new FoldHoldGate();display=new EarlyDisplayModel();
        ProjectionEntrance entry=new ProjectionEntrance();
        ScreenFade fade=new ScreenFade();fade.configure(60,170);
        now=0;
        for(int repeat=0;repeat<30;repeat++)for(float raw:new float[]{179,175,171,168,166,172,176,174,170,175}){
            now+=20;pose=pose.withAngle(raw);
            check(pose.rawAngle==raw&&pose.angle()==180&&pose.fullyOpened()&&pose.blocksProjection(),"flat tilt must keep the flat endpoint");
            for(boolean inner:new boolean[]{false,true}){
                check(ProjectionMath.endpointOpacity(pose.rawAngle,inner,6,pose.blocksProjection())==0,"flat protection suppresses either panel even with a stale angle");
                check(ProjectionMath.effectTilt(raw,inner,6,pose.blocksProjection())==0,"flat protection removes blur/geometry");
            }
            check(!hold.update(now,pose.angle(),true),"flat tilt cannot arm hover restore");
            check(display.update(pose.angle(),true,true)==-1,"flat tilt cannot request a screen switch");
            fade.update(now,pose.angle(),true,true,false);
            check(!fade.active()&&!fade.backing(),"flat tilt cannot trigger pre-switch darkness even at 170 degrees");
            check(entry.update(now,false,false)==0,"flat tilt cannot retain an old entrance");
        }
        // Real folding changed posture 3 -> 2. Even small inner movements must
        // resume, without requiring another arbitrary hinge-angle threshold.
        pose=pose.withAngle(174).withFoldEvent(folding);
        check(!pose.blocksProjection()&&!pose.fullyOpened()&&pose.angle()==174,"real folding releases flat protection immediately");
        boolean visible=ProjectionMath.endpointOpacity(pose.angle(),true,6,pose.blocksProjection())>0;
        check(visible&&entry.update(++now,visible,false)==0,"folding starts a fresh neutral entrance");
        check(entry.update(now+180,visible,false)==1,"real folding animation completes its entrance");
        pose=pose.withAngle(150);
        check(pose.angle()==150&&ProjectionMath.endpointOpacity(pose.angle(),true,6,false)==1,"normal inner folding remains fully animated");
        // Early OPENED no longer interrupts the linear angle mapping.
        pose=pose.withAngle(145).withFoldEvent(flat);
        check(pose.angle()==145&&!pose.blocksProjection(),"early OPENED preserves the current hinge angle");
        for(float raw:new float[]{150,160,170,174}){
            pose=pose.withAngle(raw).withFoldEvent(flat);
            check(pose.angle()==raw&&!pose.blocksProjection(),"repeated OPENED below 175 never starts an automatic finish");
        }
        pose=pose.withAngle(175).withAngle(160);
        check(pose.angle()==180&&pose.blocksProjection(),"once latched, angle-only regression must not release protection");
        float[] invalid=flat.clone();invalid[4]=Float.NaN;
        pose=pose.withFoldEvent(invalid).withFoldEvent(new float[]{0,160});
        check(pose.fullyOpened(),"short and invalid events preserve known flat posture");
        for(float unknown:new float[]{-1,4,2.5f,Float.POSITIVE_INFINITY}){
            invalid[4]=unknown;pose=pose.withFoldEvent(invalid);
            check(pose.fullyOpened(),"unknown posture cannot release flat protection");
        }
        invalid[4]=2;invalid[2]=Float.NaN;
        pose=pose.withFoldEvent(invalid);
        check(!pose.blocksProjection()&&pose.contactStatus==1,"valid posture updates independently of invalid contact");
        invalid=flat.clone();invalid[2]=0;
        pose=pose.withFoldEvent(invalid);
        check(pose.angle()==0&&!pose.fullyOpened()&&pose.blocksProjection(),"physical contact wins over a stale flat posture");
        pose=pose.withFoldEvent(new float[]{1,11,1,2,0,3,0,0,-477,681,1630}).withAngle(7);
        check(!pose.blocksProjection()&&pose.angle()==7,"outer small opening still works after an inner flat cycle");
        legacy=new FoldPose(true).withAngle(170).withFoldEvent(flat);
        check(legacy.postureStatus==-2&&!legacy.blocksProjection()&&legacy.angle()==170,"unvalidated devices must not decode the posture field");
        check(new FoldPose(false,true).withAngle(170).withFoldEvent(flat).angle()==170,"no vendor sensor preserves angle fallback");
        System.out.println("PASS: captured flat tilt blocked, true folding resumes, no false screen fade/hold/switch, stale/invalid events, closure precedence and device fallback");
        System.out.println("PASS: captured small-opening/contact-closure replay, low-angle fade, invalid contact and device fallback");
        System.out.println("PASS: closed tilt replay, startup, immediate opening, handoff, delayed hinge, invalid events and fallback");
    }
}
