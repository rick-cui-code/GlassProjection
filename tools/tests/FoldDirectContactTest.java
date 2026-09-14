package io.github.sixzleo.tabfold.projection;

public final class FoldDirectContactTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    // Captured directly from lhasa dighall, including closure and small separation.
    private static final float[] CLOSED={0,708,-744.45f,-1684.3501f,-1684.3501f,681,1630,0,3136.8f};
    private static final float[] OPEN={1,436.35f,97.05f,-679.65f,-679.65f,681,1630,0,1213.05f};
    private static final float[] STALE_OPEN={1,9,1,2,0,3,0,0,-493.35f,681,1630};
    private static final float[] STALE_CLOSED={1,13,0,2,0,3,0,0,-1940,681,1630};
    private static float[] field(float magnitude){float[] sample=CLOSED.clone();sample[3]=sample[4]=-magnitude;return sample;}
    public static void main(String[] args){
        FoldPose pose=new FoldPose(true,true,true).withAngle(5,10).withFoldEvent(STALE_OPEN,11);
        check(pose.blocksProjection(),"wait for the initial direct sample");
        pose=pose.withDirectContactEvent(CLOSED,12);
        for(int i=0;i<100;i++){
            pose=pose.withAngle(new float[]{0,1,2,5,17,38}[i%6],20+i);
            check(pose.angle()==0&&pose.blocksProjection(),"continuous closed signal blocks tilt regardless of stale vendor separation");
        }
        // The exact 0.4.17 regression: endpoint latched, actual separation, but
        // fold_status has not emitted another event. Unlock from direct Hall.
        pose=pose.withAngle(1,130).withDirectContactEvent(OPEN,131);
        check(!pose.blocksProjection()&&!pose.closedLatched(),"direct separation releases before a new fold event");
        for(float raw:new float[]{0,1,2,3,7,3,2}){
            pose=pose.withAngle(raw);
            check(!pose.blocksProjection(),"genuine separated low angles cannot relatch");
        }
        check(ProjectionMath.endpointOpacity(pose.angle(),false,1,pose.blocksProjection())>0,"fresh 2-degree angle starts animation");
        pose=pose.withFoldEvent(STALE_CLOSED);
        check(!pose.blocksProjection(),"stale coarse contact cannot override direct separation");
        // Direct closure immediately wins even with a stale high hinge angle.
        pose=pose.withAngle(35).withDirectContactEvent(CLOSED,1000);
        check(pose.angle()==0&&pose.blocksProjection(),"physical closure clears before the angle catches up");
        pose=pose.withDirectContactEvent(OPEN,999).withDirectContactEvent(OPEN,1000);
        check(pose.blocksProjection(),"old or duplicate Hall samples cannot unlock");
        pose=pose.withDirectContactEvent(OPEN,1001);
        check(!pose.blocksProjection(),"new separation unlocks immediately");
        for(float value:new float[]{Float.NaN,-1,2,Float.POSITIVE_INFINITY}){
            float[] bad=OPEN.clone();bad[0]=value;
            pose=pose.withDirectContactEvent(bad,1100);
            check(!pose.blocksProjection()&&pose.directContactStatus==1,"invalid direct bit preserves state");
        }
        pose=pose.withDirectContactEvent(new float[]{0},1100).withDirectContactEvent(null,1101);
        check(!pose.blocksProjection(),"short/null direct event ignored");
        // Flat protection is still independent of closure protection.
        float[] flat={0,162,1,2,3,3,0,0,-280,681,1630};
        pose=pose.withFoldEvent(flat).withAngle(175).withAngle(160);
        check(pose.fullyOpened()&&pose.angle()==180,"inner flat latch preserved with direct sensor");
        pose=pose.withDirectContactEvent(CLOSED,2000);
        check(!pose.fullyOpened()&&pose.angle()==0,"direct closure wins over flat posture");
        // No sensor/profile means the tested 0.4.17 endpoint fallback remains.
        FoldPose fallback=new FoldPose(true,true).withFoldEvent(STALE_OPEN).withAngle(1);
        check(fallback.withDirectContactEvent(OPEN,3000).blocksProjection(),"unavailable Hall ignored");
        check(new FoldPose(true,false,true).withDirectContactEvent(OPEN,3000).directContactStatus==-2,"unvalidated profile cannot enable direct field decoding");
        // The user's stationary 3-degree gap: the digital bit remains closed
        // until 681, but the field has already fallen to about 1200.
        pose=new FoldPose(true,true,true).withAngle(3,1).withDirectContactEvent(CLOSED,2);
        pose=pose.withDirectContactEvent(field(1200),3);
        check(pose.directContactStatus==1&&pose.angle()==3&&!pose.blocksProjection(),"held gap releases even while digital bit stays closed");
        check(ProjectionMath.endpointOpacity(pose.angle(),false,1,pose.blocksProjection())>0,"held gap reaches animation");
        for(int i=0;i<50;i++){
            pose=pose.withDirectContactEvent(field(new float[]{1468,1500,1629}[i%3]),4+i);
            check(!pose.blocksProjection(),"hysteresis band preserves separated state without chatter");
        }
        pose=pose.withDirectContactEvent(field(1630),60);
        check(pose.blocksProjection(),"vendor contact threshold closes immediately");
        for(int i=0;i<50;i++){
            pose=pose.withDirectContactEvent(field(new float[]{1468,1500,1629,1880,1905,3110}[i%6]),61+i).withAngle(i%39);
            check(pose.blocksProjection()&&pose.angle()==0,"closed band and captured closed tilt remain clear");
        }
        pose=pose.withDirectContactEvent(field(1467),200);
        check(!pose.blocksProjection(),"release threshold edge opens");
        for(float bad:new float[]{Float.NaN,Float.POSITIVE_INFINITY}){
            pose=pose.withDirectContactEvent(field(bad),201);
            check(pose.directContactAgeMs(1000200)==1,"invalid field does not refresh sample age");
        }
        for(int index:new int[]{5,6}){
            float[] bad=field(1900);bad[index]=Float.NaN;
            check(pose.withDirectContactEvent(bad,201)==pose,"invalid calibration ignored");
        }
        float[] bad=field(1900);bad[6]=bad[5];
        check(pose.withDirectContactEvent(bad,201)==pose,"inverted or empty calibration interval ignored");
        pose=new FoldPose(true,true,true).withAngle(3,1).withDirectContactEvent(field(1200),1000000000L);
        check(!pose.expireDirectContact(1500000000L).blocksProjection(),"freshness boundary allows sample");
        pose=pose.expireDirectContact(1501000000L);
        check(pose.blocksProjection()&&pose.directContactStatus==-1,"dead sampler cannot leave stale projection open");
        pose=pose.withDirectContactEvent(field(1200),1000000000L);
        check(pose.blocksProjection(),"replayed cached sample cannot revive expired contact");
        pose=pose.withDirectContactEvent(field(1200),1502000000L);
        check(!pose.blocksProjection(),"fresh sample recovers without service restart");
        pose=pose.withDirectContactAvailable(false);
        check(pose.directContactStatus==-2,"unsupported sampler uses vendor fallback");
        pose=pose.withDirectContactAvailable(true);
        check(pose.directContactStatus==-1&&pose.blocksProjection(),"sampler recovery waits for its first sample");
        System.out.println("PASS: direct Hall, held 3-degree gap with closed digital bit, captured closed tilt, hysteresis, sample expiry/recovery, ordering, invalid events and flat/fallback protection");
    }
}
