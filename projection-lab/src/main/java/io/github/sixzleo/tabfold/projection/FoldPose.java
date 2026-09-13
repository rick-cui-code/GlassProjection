package io.github.sixzleo.tabfold.projection;

/** Immutable snapshot: Xiaomi's physical fold flag takes priority over noisy hinge estimates. */
final class FoldPose {
    // -2: sensor unavailable; -1: waiting for its initial event; 0: open; 1: closed.
    final int foldStatus;
    final float rawAngle;
    FoldPose(boolean physicalSensor){this(Float.NaN,physicalSensor?-1:-2);}
    private FoldPose(float rawAngle,int foldStatus){this.rawAngle=rawAngle;this.foldStatus=foldStatus;}
    FoldPose withAngle(float angle){
        return Float.isFinite(angle)?new FoldPose(angle,foldStatus):this;
    }
    FoldPose withFoldStatus(float status){
        // This vendor flag is binary. Never interpret an unknown/invalid value as open.
        if(foldStatus==-2||(status!=0&&status!=1))return this;
        return new FoldPose(rawAngle,(int)status);
    }
    boolean blocksProjection(){return foldStatus==-1||foldStatus==1;}
    float angle(){return blocksProjection()?0:rawAngle;}
}
