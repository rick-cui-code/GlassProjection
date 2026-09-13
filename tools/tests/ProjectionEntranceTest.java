package io.github.sixzleo.tabfold.projection;

public final class ProjectionEntranceTest {
    private static void near(float actual,float expected){
        if(Math.abs(actual-expected)>.000001)throw new AssertionError(actual+" != "+expected);
    }
    public static void main(String[] args){
        for(float firstAngle:new float[]{2,16,30}){
            ProjectionEntrance entry=new ProjectionEntrance();
            near(entry.update(0,false,false),0);
            // Replay an opening whose first delivered angle skips the threshold.
            float target=ProjectionMath.cropFraction(firstAngle,false);
            near(target*entry.update(100,true,false),0);
            float previous=0;
            for(int time=108;time<=276;time+=8){
                float amount=entry.update(time,true,false);
                if(amount<previous||amount-previous>.067)throw new AssertionError("onset jumped");
                previous=amount;
            }
            near(entry.update(280,true,false),1);
            // Steady-state linear motion must not restart its entrance every frame.
            near(target*entry.update(300,true,false),target);
            near(entry.update(301,false,false),0);
            near(entry.update(302,true,false),0);
            near(entry.update(392,true,false),.5f);
            near(entry.update(393,false,false),0);
            near(entry.update(394,true,false),0);
        }
        // Inner display: closing from flat can first report 174 or skip to 165.
        // A panel handoff can instead make it visible at a much larger tilt.
        for(float angle:new float[]{174,165,117}){
            ProjectionEntrance inner=new ProjectionEntrance();
            near(inner.update(0,ProjectionMath.endpointOpacity(180,true,1,false)>0,false),0);
            float entry=inner.update(100,ProjectionMath.endpointOpacity(angle,true,1,false)>0,false);
            near(entry,0);
            near(ProjectionMath.cropFraction(angle,true)*entry,0);
            near(ProjectionMath.effectTilt(angle,true,1,false)*entry,0);
            near(inner.update(190,true,false),.5f);
            near(inner.update(280,true,false),1);
            // Returning flat resets the next closing gesture, without a stale offset.
            near(inner.update(281,ProjectionMath.endpointOpacity(175,true,1,false)>0,false),0);
            near(inner.update(282,true,false),0);
        }
        ProjectionEntrance handoff=new ProjectionEntrance();
        near(handoff.update(0,true,false),0);
        near(handoff.update(180,true,false),1);
        // The inner panel must not inherit a fully entered outer-panel transform.
        near(handoff.update(181,true,true),0);
        near(handoff.update(361,true,false),1);
        near(handoff.update(362,ProjectionMath.endpointOpacity(117,true,1,true)>0,false),0);
        near(handoff.update(363,true,false),0);
        // Replay the observed 370 ms compositor stall: the new panel is already entering.
        ProjectionEntrance delayed=new ProjectionEntrance();
        near(delayed.update(100,true,true,100),0);
        near(delayed.update(470,true,false,100),1); // Same panel OFF -> ON: no scene reset.
        near(delayed.update(1000,true,true,630),1); // First new-panel frame arrived late.
        near(delayed.update(1090,true,true,1000),.5f);
        near(delayed.update(1180,true,false,1000),1);
        near(delayed.update(1200,false,false,1000),0); // Physical closure clears immediately.
        near(delayed.update(1201,true,false,1000),0); // Reopening must not reuse stale scene age.
        near(delayed.update(1291,true,false,1000),.5f);
        near(delayed.update(1400,true,true,2000),0); // Never advance from a future timestamp.
        System.out.println("PASS: delayed panel frame catches up, OFF/ON preserves entry, normal reopening remains smooth");
        ProjectionSceneTiming clock=new ProjectionSceneTiming();
        ProjectionEntrance outer=new ProjectionEntrance();
        clock.observe(2364,1672,3,true,0);
        long since=clock.observe(1672,2364,0,false,100); // Type switches before logical size.
        near(outer.update(100,true,true,since,80),0);
        since=clock.observe(1168,1712,0,false,151);
        near((float)since,100);
        float firstOn=outer.update(180,true,true,since,80);
        near(firstOn,1); // Former 35 ms / 180 ms restart exposed the native screen.
        since=clock.observe(1712,1168,3,false,250);
        near(outer.update(260,true,true,since,80),1); // Late rotation cannot reveal neutral.
        near(outer.update(300,false,false,since,80),0);
        near(outer.update(301,true,false,since,80),0); // Normal opening still lasts 180 ms.
        near(outer.update(391,true,false,since,80),.5f);
        near(outer.update(481,true,false,since,80),1);
        near((float)clock.observe(1168,1712,0,false,1000),1000); // Independent rotation.
        near((float)clock.observe(1168,1712,0,true,1100),1100);
        near((float)clock.observe(2364,1672,3,true,1150),1150); // Inner behavior unchanged.
        near((float)clock.observe(1168,1712,0,false,1200),1200); // New handoff gets fresh clock.
        System.out.println("PASS: outer handoff shares early panel clock, late geometry/rotation, normal gesture duration, unchanged inner timing");
        ProjectionEntrance coarse=new ProjectionEntrance(),fine=new ProjectionEntrance();
        near(coarse.update(0,true,false),0);near(fine.update(0,true,false),0);
        for(int time=1;time<=90;time++)fine.update(time,true,false);
        near(coarse.update(90,true,false),fine.update(90,true,false));
        near(coarse.update(180,true,false),1);
        near(coarse.update(181,true,true),0);
        near(coarse.update(361,true,false),1);
        near(coarse.update(10000,false,false),0);
        near(coarse.update(20000,true,false),0);
        System.out.println("PASS: inner flat-to-fold, skipped first angle, panel handoff and physical closure");
        System.out.println("PASS: neutral first crop, skipped-angle onset, frame-independent join, closure/reopen and scene reset");
        // Replay a single sharp icon during onset: any motion must fully cover
        // the original icon, while the first frame must match its old position.
        for(boolean isInner:new boolean[]{false,true}){
            for(float angle:isInner?new float[]{174,171,165}:new float[]{2,4,9}){
                ProjectionEntrance join=new ProjectionEntrance();
                float endpoint=ProjectionMath.endpointOpacity(angle,isInner,1,false);
                float previousMotion=0;
                for(int time=0;time<=180;time++){
                    float progress=join.update(time,true,false);
                    float motion=ProjectionMath.onsetMotion(endpoint,progress);
                    float opacity=ProjectionMath.onsetOpacity(endpoint);
                    float crop=ProjectionMath.cropFraction(angle,isInner)*motion;
                    if(time==0)near(crop,0);
                    if(crop>0&&1-opacity!=0)throw new AssertionError("moving icon must not expose a second unshifted icon");
                    near(opacity,1);
                    if(motion<previousMotion||motion-previousMotion>.009f)throw new AssertionError("opaque onset must morph continuously");
                    previousMotion=motion;
                }
                near(previousMotion,endpoint);
                float boundary=isInner?175:1;
                float almost=ProjectionMath.endpointOpacity(boundary+(isInner?-.001f:.001f),isInner,1,false);
                if(ProjectionMath.onsetMotion(almost,1)>.000001f)throw new AssertionError("handoff at endpoint must be visually neutral");
                near(ProjectionMath.onsetOpacity(ProjectionMath.endpointOpacity(boundary,isInner,1,false)),0);
                near(ProjectionMath.onsetOpacity(ProjectionMath.endpointOpacity(angle,isInner,1,true)),0);
            }
        }
        System.out.println("PASS: opaque inner/outer onset, no unshifted icon contribution, neutral first frame and endpoint handback");
    }
}
