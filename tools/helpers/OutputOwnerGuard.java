package io.github.sixzleo.tabfold.probe;

import android.os.IBinder;
import android.os.RemoteException;
import android.view.SurfaceControl;

/** Keep a remote handle so an app-process death cannot strand its last overlay buffer. */
final class OutputOwnerGuard implements IBinder.DeathRecipient,AutoCloseable {
    private final IBinder owner;
    private SurfaceControl root;
    private final Runnable onDeath;
    private boolean closed;
    OutputOwnerGuard(IBinder owner,SurfaceControl root,Runnable onDeath)throws RemoteException {
        this.owner=owner;this.root=root;this.onDeath=onDeath;
        try{owner.linkToDeath(this,0);}
        catch(RemoteException e){detach();root.release();this.root=null;throw e;}
    }
    @Override public void binderDied(){
        detach();
        System.out.println("OUTPUT_OWNER_DIED detached display overlay");
        onDeath.run();
    }
    synchronized void detach(){
        if(root!=null&&root.isValid())try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){
            t.setVisibility(root,false).reparent(root,null).apply();
        }catch(RuntimeException e){System.err.println("Output detach: "+e);}
    }
    @Override public synchronized void close(){
        if(closed)return;closed=true;
        owner.unlinkToDeath(this,0);
        if(root!=null){root.release();root=null;}
    }
}
