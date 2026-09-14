package io.github.sixzleo.tabfold.projection;

public final class ProjectionAngleMotionTest {
    static void near(float value,float expected){
        if(!Float.isFinite(value)||Math.abs(value-expected)>.0001f)throw new AssertionError(value+" != "+expected);
    }
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static float flat(ProjectionAngleMotion motion,long now,float raw){return motion.update(now,raw,true,true,true,false);}
    public static void main(String[] args){
        ProjectionAngleMotion motion=new ProjectionAngleMotion();
        ProjectionEntrance entry=new ProjectionEntrance();
        near(motion.update(0,160,true,false,false,false),160);
        entry.update(0,true,false);
        near(entry.update(200,true,false),1);
        near(flat(motion,200,180),160); // Start from the visible pose, not normalized telemetry.
        float beforeCrop=Float.POSITIVE_INFINITY;
        for(int t=200;t<=300;t+=10){
            float angle=flat(motion,t,t%20==0?166:179); // Flat jitter must not restart the return.
            float endpoint=ProjectionMath.endpointOpacity(angle,true,6,false);
            float entrance=entry.update(t,endpoint>0,false);
            float crop=ProjectionMath.cropFraction(angle,true)*ProjectionMath.onsetMotion(endpoint,entrance);
            check(crop<=beforeCrop,"flat return must approach neutral monotonically");
            if(endpoint>0){near(entrance,1);near(ProjectionMath.onsetOpacity(endpoint),1);}
            beforeCrop=crop;
        }
        near(beforeCrop,0);near(flat(motion,300,166),180);
        for(int t=320;t<=2000;t+=20)near(flat(motion,t,145+t%35),180);

        ProjectionAngleMotion fine=new ProjectionAngleMotion(),coarse=new ProjectionAngleMotion();
        fine.update(0,160,true,false,false,false);coarse.update(0,160,true,false,false,false);
        flat(fine,100,180);flat(coarse,100,180);
        for(int t=110;t<150;t+=10)flat(fine,t,180);
        near(flat(fine,150,180),flat(coarse,150,180));near(flat(fine,150,180),170);
        float middle=flat(fine,150,180);
        near(fine.update(160,150,true,false,false,false),middle);
        float reverse=fine.update(170,150,true,false,false,false);
        check(reverse<middle&&reverse>150,"reversal must resume hinge following");
        near(flat(fine,180,180),reverse);
        near(fine.update(190,0,true,true,false,false),0); // Closure always wins immediately.
        check(ProjectionAngleMotion.hardBlocked(true,false,true),"closure must remain blocked");
        check(ProjectionAngleMotion.hardBlocked(true,true,false),"outer flat pose must remain blocked");
        check(!ProjectionAngleMotion.hardBlocked(true,true,true),"inner flat can finish its visual tail");
        near(new ProjectionAngleMotion().update(0,167,true,true,true,false),180);
        near(fine.update(200,166,true,true,true,true),180); // New scene must not inherit a tail.

        for(boolean inner:new boolean[]{false,true}){
            ProjectionAngleMotion regular=new ProjectionAngleMotion();
            near(regular.update(0,120,inner,false,false,false),120);
            near(regular.update(16,130,inner,false,false,false),ProjectionMath.followAngle(120,130,16,inner));
            near(regular.update(20,0,inner,true,false,false),0);
        }
        // Replay the complete pose-to-render pipeline: vendor OPENED arrives
        // at 160, but the original angle follower remains in charge until 175.
        float[] opened={0,160,1,2,3,3,0,0,0,681,1630};
        float[] folding={0,150,1,2,2,3,0,0,0,681,1630};
        FoldPose pose=new FoldPose(true,true).withAngle(150).withFoldEvent(folding);
        ProjectionAngleMotion integrated=new ProjectionAngleMotion();
        float expected=integrated.update(0,pose.angle(),true,pose.blocksProjection(),pose.fullyOpened(),false);
        long now=0;
        for(float raw:new float[]{155,160,160,160,160,165,170,174}){
            now+=100;
            pose=pose.withAngle(raw).withFoldEvent(opened);
            check(!pose.fullyOpened(),"early OPENED must not start a timed finish");
            float followed=integrated.update(now,pose.angle(),true,pose.blocksProjection(),pose.fullyOpened(),false);
            check(followed>=expected&&followed<=raw,"adaptive follower must approach the real hinge without an early flat return");
            expected=followed;
        }
        pose=pose.withAngle(175);
        check(pose.fullyOpened(),"both signals must arm protection");
        now+=16;
        near(integrated.update(now,pose.angle(),true,pose.blocksProjection(),pose.fullyOpened(),false),expected);
        check(expected<175,"test must exercise a residual visible effect at lock");
        for(int t=10;t<=100;t+=10){
            pose=pose.withAngle(160);
            float current=integrated.update(now+t,pose.angle(),true,pose.blocksProjection(),pose.fullyOpened(),false);
            check(current>=expected&&current<=180,"residual must finish monotonically despite tilt");
            expected=current;
        }
        near(expected,180);
        System.out.println("PASS: original hinge following until dual-signal lock, 100 ms residual finish and tilt latch");
        System.out.println("PASS: 100 ms flat return, continuous geometry, jitter rejection, reversal, closure and scene reset");
    }
}
