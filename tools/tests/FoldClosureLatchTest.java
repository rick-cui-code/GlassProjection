package io.github.sixzleo.tabfold.projection;

import io.github.sixzleo.tabfold.probe.EarlyDisplayModel;

public final class FoldClosureLatchTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static float[] event(float angle,int contact){return new float[]{1,angle,contact,2,0,3,.73f,-.79f,-334.5f,681,1630};}
    private static FoldPose staleClosed(){
        // 20:54:32: last vendor event is CLOSED / separated / 9 degrees.
        // The hinge reaches 1, but no contact update arrives for >50 seconds.
        return new FoldPose(true,true).withAngle(9,100).withFoldEvent(event(9,1),101).withAngle(1,102);
    }
    private static void blocked(FoldPose pose){
        check(pose.closedLatched()&&pose.blocksProjection()&&pose.angle()==0,"closed endpoint must stay locked");
        for(boolean inner:new boolean[]{false,true}){
            check(ProjectionMath.endpointOpacity(pose.rawAngle,inner,1,pose.blocksProjection())==0,"no projected coverage on either panel");
            check(ProjectionMath.effectTilt(pose.rawAngle,inner,1,pose.blocksProjection())==0,"no geometry or blur");
        }
    }
    public static void main(String[] args){
        FoldPose pose=staleClosed();blocked(pose);
        FoldHoldGate hold=new FoldHoldGate();EarlyDisplayModel display=new EarlyDisplayModel();
        ScreenFade fade=new ScreenFade();fade.configure(10,170);
        long at=102;
        for(int repeat=0;repeat<100;repeat++)for(float raw:new float[]{0,1,2,5,7,12,38,90,179,3}){
            pose=pose.withAngle(raw,++at);blocked(pose);
            check(!hold.update(at,pose.angle(),true),"tilt cannot rearm hover");
            check(display.update(pose.angle(),true,false)==-1,"tilt cannot override screen");
            fade.update(at,pose.angle(),true,false,false);
            check(!fade.active()&&!fade.backing(),"tilt cannot darken cover");
        }
        // Invalid, short, coarse-only, duplicate and out-of-order events cannot unlock.
        pose=staleClosed().withFoldEvent(event(9,1),101);blocked(pose);
        pose=pose.withFoldEvent(event(9,1),102);blocked(pose);
        pose=pose.withFoldEvent(new float[]{0},103);blocked(pose);
        for(float bad:new float[]{Float.NaN,-1,0,1,181,Float.POSITIVE_INFINITY}){
            pose=pose.withFoldEvent(event(bad,1),++at);blocked(pose);
        }
        float[] invalid=event(2,1);invalid[2]=Float.NaN;
        pose=pose.withFoldEvent(invalid,++at);blocked(pose);
        invalid=event(2,1);invalid[0]=Float.NaN;
        pose=pose.withFoldEvent(invalid,++at);blocked(pose);
        invalid=event(2,1);invalid[4]=Float.NaN;
        pose=pose.withFoldEvent(invalid,++at);blocked(pose);
        // A fresh separated event can restart at 2 degrees while coarse CLOSED
        // remains set. No 10/30-degree hard threshold or timeout is introduced.
        pose=pose.withFoldEvent(event(2,1),++at).withAngle(2,++at);
        check(!pose.blocksProjection()&&pose.foldStatus==1&&pose.angle()==2,"fresh small opening unlocks");
        check(ProjectionMath.endpointOpacity(pose.angle(),false,1,false)>0,"2-degree onset remains visible");
        // Reverse before reaching the endpoint: never latch a real small opening.
        for(float raw:new float[]{19,7,2,3,7,19}){
            pose=pose.withAngle(raw,++at);
            check(!pose.blocksProjection(),"small-angle reversal stays animated");
        }
        pose=pose.withAngle(1,++at);blocked(pose);
        pose=pose.withFoldEvent(event(11,0),++at).withAngle(38,++at);blocked(pose);
        pose=pose.withFoldEvent(event(2,1),++at).withAngle(2,++at);
        check(!pose.blocksProjection(),"repeated cycle unlocks");
        // Hinge endpoint delivered first, then an older queued coarse CLOSED.
        pose=new FoldPose(true,true).withAngle(1,200).withFoldEvent(event(9,1),190);blocked(pose);
        pose=pose.withAngle(5,210).withFoldEvent(event(9,1),195);blocked(pose);
        pose=pose.withFoldEvent(event(2,1),220);
        check(!pose.blocksProjection(),"measured-after-endpoint separation unlocks");
        // Delayed endpoint measured before reopening cannot relock it.
        pose=pose.withAngle(0,215);
        check(!pose.blocksProjection(),"queued old hinge endpoint cannot relock fresh opening");
        pose=pose.withAngle(2,225).withAngle(0,214);
        check(pose.rawAngle==2&&!pose.blocksProjection(),"out-of-order hinge event ignored");
        // Equal-time opening/hinge readings give the same result in either order.
        FoldPose firstAngle=staleClosed().withAngle(1,300).withFoldEvent(event(2,1),301).withAngle(1,301);
        FoldPose firstFold=staleClosed().withAngle(1,300).withAngle(1,301).withFoldEvent(event(2,1),301);
        // A closure at the exact separation timestamp remains conservative;
        // it requires a strictly later separation sample to release.
        blocked(firstAngle);blocked(firstFold);
        check(!firstAngle.withFoldEvent(event(2,1),302).blocksProjection(),"strictly newer separation resolves equal-time observations");
        // Fallbacks keep their existing behavior and never decode vendor fields.
        pose=new FoldPose(true).withAngle(1,100).withFoldEvent(event(9,1),101);
        check(!pose.closedLatched()&&pose.blocksProjection(),"legacy coarse fallback");
        pose=pose.withFoldStatus(0).withAngle(2);
        check(!pose.blocksProjection(),"legacy reopening unaffected");
        pose=new FoldPose(false,true).withAngle(1).withAngle(5);
        check(!pose.closedLatched()&&pose.angle()==5,"unsupported sensor unchanged");
        // A stale coarse CLOSED is not enough on its own at 2+ degrees.
        pose=new FoldPose(true,true).withAngle(2,100).withFoldEvent(event(11,1),90);
        check(!pose.blocksProjection(),"startup at a real small opening remains available");
        check(staleClosed().withAngle(Float.NaN,500).withAngle(-1,501).withAngle(181,502).rawAngle==1,"invalid angles cannot affect endpoint");
        System.out.println("PASS: stale-contact tilt replay, fresh low-angle reopening, closure precedence, event ordering, invalid events and device fallback");
    }
}
