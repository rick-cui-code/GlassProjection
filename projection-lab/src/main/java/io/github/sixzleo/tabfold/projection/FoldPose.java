package io.github.sixzleo.tabfold.projection;

/** Immutable snapshot: validated endpoint signals take priority over noisy hinge estimates. */
final class FoldPose {
    // -2: sensor unavailable; -1: waiting for its initial event; 0: open; 1: closed.
    final int foldStatus;
    // lhasa fold_status values[2]: observed 0 at contact, 1 after separation.
    // -2: unvalidated device; -1: no valid contact event yet (use coarse state).
    final int contactStatus;
    // lhasa fold_status values[4]: 3 is OPENED, 2 is HALF_OPENED; 0/1 are
    // folded postures, not proof of contact. Validated against real flat tilt
    // and folding on this device; never decode this field on other devices.
    final int postureStatus;
    final float rawAngle;
    private final boolean flatLatched;
    FoldPose(boolean physicalSensor){this(physicalSensor,false);}
    FoldPose(boolean physicalSensor,boolean contactSupported){this(Float.NaN,physicalSensor?-1:-2,physicalSensor&&contactSupported?-1:-2,physicalSensor&&contactSupported?-1:-2,false);}
    private FoldPose(float rawAngle,int foldStatus,int contactStatus,int postureStatus,boolean wasFlat){
        this.rawAngle=rawAngle;this.foldStatus=foldStatus;this.contactStatus=contactStatus;this.postureStatus=postureStatus;
        // OPENED can arrive before the hinge has reached the clear endpoint.
        // Follow the hinge until BOTH signals agree, then ignore angle-only tilt.
        flatLatched=!closed()&&postureStatus==3&&(wasFlat||Float.isFinite(rawAngle)&&rawAngle>=175);
    }
    FoldPose withAngle(float angle){
        return Float.isFinite(angle)?new FoldPose(angle,foldStatus,contactStatus,postureStatus,flatLatched):this;
    }
    FoldPose withFoldStatus(float status){
        // This vendor flag is binary. Never interpret an unknown/invalid value as open.
        if(foldStatus==-2||(status!=0&&status!=1))return this;
        return new FoldPose(rawAngle,(int)status,contactStatus,postureStatus,flatLatched);
    }
    FoldPose withFoldEvent(float[] values){
        if(values==null||values.length==0)return this;
        FoldPose next=withFoldStatus(values[0]);
        if(contactStatus==-2||values.length<11)return next;
        int contact=values[2]==0||values[2]==1?(int)values[2]:contactStatus;
        float posture=values[4];
        int state=posture==0||posture==1||posture==2||posture==3?(int)posture:postureStatus;
        return new FoldPose(rawAngle,next.foldStatus,contact,state,flatLatched);
    }
    private boolean closed(){return contactStatus>=0?contactStatus==0:foldStatus==-1||foldStatus==1;}
    // Confirmed flat protection, not merely the raw vendor OPENED report.
    boolean fullyOpened(){return flatLatched;}
    boolean blocksProjection(){return closed()||fullyOpened();}
    // Preserve which endpoint was reached: 0 would make the screen controller
    // treat a flat phone as closed and release/choose the wrong panel.
    float angle(){return closed()?0:fullyOpened()?180:rawAngle;}
}
