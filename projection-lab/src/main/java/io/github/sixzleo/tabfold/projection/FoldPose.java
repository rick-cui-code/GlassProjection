package io.github.sixzleo.tabfold.projection;

/** Immutable snapshot: validated closure signals take priority over noisy hinge estimates. */
final class FoldPose {
    // -2: sensor unavailable; -1: waiting for its initial event; 0: open; 1: closed.
    final int foldStatus;
    // lhasa fold_status values[2]: observed 0 at contact, 1 after separation.
    // -2: unvalidated device; -1: no valid contact event yet (use coarse state).
    final int contactStatus;
    final float rawAngle;
    FoldPose(boolean physicalSensor){this(physicalSensor,false);}
    FoldPose(boolean physicalSensor,boolean contactSupported){this(Float.NaN,physicalSensor?-1:-2,physicalSensor&&contactSupported?-1:-2);}
    private FoldPose(float rawAngle,int foldStatus,int contactStatus){this.rawAngle=rawAngle;this.foldStatus=foldStatus;this.contactStatus=contactStatus;}
    FoldPose withAngle(float angle){
        return Float.isFinite(angle)?new FoldPose(angle,foldStatus,contactStatus):this;
    }
    FoldPose withFoldStatus(float status){
        // This vendor flag is binary. Never interpret an unknown/invalid value as open.
        if(foldStatus==-2||(status!=0&&status!=1))return this;
        return new FoldPose(rawAngle,(int)status,contactStatus);
    }
    FoldPose withFoldEvent(float[] values){
        if(values==null||values.length==0)return this;
        FoldPose next=withFoldStatus(values[0]);
        if(contactStatus==-2||values.length<11||(values[2]!=0&&values[2]!=1))return next;
        return new FoldPose(rawAngle,next.foldStatus,(int)values[2]);
    }
    boolean blocksProjection(){return contactStatus>=0?contactStatus==0:foldStatus==-1||foldStatus==1;}
    float angle(){return blocksProjection()?0:rawAngle;}
}
