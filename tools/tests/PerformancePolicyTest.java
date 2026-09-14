package io.github.sixzleo.tabfold.projection;

public final class PerformancePolicyTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void near(float a,float b){check(Float.isFinite(a)&&Math.abs(a-b)<.0001f,a+" != "+b);}
    public static void main(String[] args){
        check(ProjectionCadence.delay(false,false,false,0,false,false)==40,"moving retains active timers");
        check(ProjectionCadence.delay(false,true,false,0,false,false)==200,"clear endpoints reduce timers");
        check(ProjectionCadence.delay(true,true,false,0,false,false)==500,"standby reduces timers further");
        check(ProjectionCadence.delay(false,true,true,0,false,false)==40,"fade remains active");
        check(ProjectionCadence.delay(false,true,false,1,false,false)==40,"handoff remains active");
        check(ProjectionCadence.delay(false,true,false,0,true,false)==40,"pending capture remains active");
        check(ProjectionCadence.delay(false,true,false,0,false,true)==40,"uncertain home gets a quick retry");
        FrameUpdateOrder order=new FrameUpdateOrder();
        check(order.needsFull()&&!order.accept(2,1,false),"delta cannot initialize a cache");
        check(order.accept(2,0,true)&&!order.needsFull(),"full initializes");
        check(order.accept(3,2,false),"ordered delta merges");
        check(!order.accept(2,1,false)&&!order.needsFull(),"old delta is harmless");
        check(!order.accept(5,4,false)&&order.needsFull(),"missing base requires repair");
        check(!order.accept(4,3,false),"do not merge partial history while repairing");
        check(order.accept(5,0,true)&&!order.needsFull(),"full snapshot repairs dropped update");
        check(!order.accept(4,0,true),"late polling reply cannot replace a newer push");
        check(order.accept(5,0,true)&&order.accept(6,5,false),"equal-version heartbeat then delta works");

        for(boolean inner:new boolean[]{false,true}){
            AdaptiveAngleFollow adaptive=new AdaptiveAngleFollow();
            float current=inner?175:5,regular=current;adaptive.reset(0,current);
            for(int t=8;t<=2000;t+=8){
                float outer=5+(t/400)%2,target=inner?180-outer:outer;
                current=adaptive.update(t,current,target,inner);regular=ProjectionMath.followAngle(regular,target,8,inner);
                near(current,regular); // Slow movement must preserve dense smoothing.
            }
            current=regular=inner?175:5;adaptive.reset(0,current);
            for(int t=8;t<=800;t+=8){
                float outer=5+(t/24)%2,target=inner?180-outer:outer;
                current=adaptive.update(t,current,target,inner);regular=ProjectionMath.followAngle(regular,target,8,inner);
                near(current,regular); // Fast alternating quantization is not opening speed.
            }
            current=regular=inner?178:2;adaptive.reset(0,current);float adaptiveError=0,regularError=0;
            for(int t=8;t<=96;t+=8){
                float outer=2+t/16,target=inner?180-outer:outer;
                current=adaptive.update(t,current,target,inner);regular=ProjectionMath.followAngle(regular,target,8,inner);
                adaptiveError+=Math.abs(target-current);regularError+=Math.abs(target-regular);
                check(inner?current>=target:current<=target,"fast following cannot overshoot");
            }
            check(adaptiveError<regularError*.9f,"directional fast motion must reduce tracking error: "+adaptiveError+" / "+regularError);
            float before=current,target=inner?178:2;
            current=adaptive.update(104,current,target,inner);
            check(inner?current>before&&current<=target:current<before&&current>=target,"fast reversal has no queued forward tail");
            System.out.println("TRACKING inner="+inner+" adaptiveError="+adaptiveError+" baselineError="+regularError);
        }
        ProjectionAngleMotion motion=new ProjectionAngleMotion();
        motion.update(0,0,false,true,false,false);motion.update(8,8,false,false,false,false);
        near(motion.update(16,0,false,true,false,false),0);
        near(motion.update(20,180,true,true,true,true),180);
        System.out.println("PASS: adaptive cadence, ordered frame deltas/repair, slow/jitter equivalence, faster directional tracking, reversals, closure and flat protection");
    }
}
