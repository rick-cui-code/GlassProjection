package io.github.sixzleo.tabfold.projection;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Grants the system installer read access to one verified APK, never arbitrary app files. */
public final class UpdateApkProvider extends ContentProvider {
    public boolean onCreate(){return true;}
    private File file(Uri uri)throws FileNotFoundException {
        if(!"/verified.apk".equals(uri.getPath()))throw new FileNotFoundException();
        return new File(getContext().getFilesDir(),"updates/verified.apk");
    }
    public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException {
        if(!"r".equals(mode))throw new FileNotFoundException("Read only");return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    public String getType(Uri uri){return "application/vnd.android.package-archive";}
    public Cursor query(Uri uri,String[] columns,String selection,String[] args,String order){
        if(columns==null)columns=new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE};
        MatrixCursor result=new MatrixCursor(columns);
        try{File file=file(uri);Object[] row=new Object[columns.length];for(int i=0;i<columns.length;i++){
            if(OpenableColumns.DISPLAY_NAME.equals(columns[i]))row[i]="GlassProjection.apk";
            else if(OpenableColumns.SIZE.equals(columns[i]))row[i]=file.length();
        }result.addRow(row);}catch(FileNotFoundException ignored){}return result;
    }
    public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    public int update(Uri uri,ContentValues values,String where,String[] args){throw new UnsupportedOperationException();}
    public int delete(Uri uri,String where,String[] args){throw new UnsupportedOperationException();}
}
