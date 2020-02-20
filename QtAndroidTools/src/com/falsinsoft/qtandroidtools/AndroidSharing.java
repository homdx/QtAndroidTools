/*
 *	MIT License
 *
 *	Copyright (c) 2018 Fabio Falsini <falsinsoft@gmail.com>
 *
 *	Permission is hereby granted, free of charge, to any person obtaining a copy
 *	of this software and associated documentation files (the "Software"), to deal
 *	in the Software without restriction, including without limitation the rights
 *	to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *	copies of the Software, and to permit persons to whom the Software is
 *	furnished to do so, subject to the following conditions:
 *
 *	The above copyright notice and this permission notice shall be included in all
 *	copies or substantial portions of the Software.
 *
 *	THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *	IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *	FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *	AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *	LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *	OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *	SOFTWARE.
 */

package com.falsinsoft.qtandroidtools;

import android.content.Context;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.os.Bundle;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.content.ComponentName;
import android.support.v4.content.FileProvider;
import android.content.ContentResolver;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.FileDescriptor;

public class AndroidSharing
{
    private static final String TAG = "AndroidSharing";
    private final Activity mActivityInstance;
    private final Intent mActivityIntent;
    private ParcelFileDescriptor mInputSharedFile = null;

    public AndroidSharing(Activity ActivityInstance)
    {
        mActivityInstance = ActivityInstance;
        mActivityIntent = ActivityInstance.getIntent();
    }

    public int getAction()
    {
        final String ActionValue = mActivityIntent.getAction();
        int ActionId = ACTION_NONE;

        if(ActionValue != null)
        {
            switch(ActionValue)
            {
                case Intent.ACTION_SEND:
                    ActionId = ACTION_SEND;
                    break;
                case Intent.ACTION_SEND_MULTIPLE:
                    ActionId = ACTION_SEND_MULTIPLE;
                    break;
                case Intent.ACTION_PICK:
                    ActionId = ACTION_PICK;
                    break;
            }
        }

        return ActionId;
    }

    public String getMimeType()
    {
        return mActivityIntent.getType();
    }

    public String getSharedText()
    {
        return mActivityIntent.getStringExtra(Intent.EXTRA_TEXT);
    }

    public byte[] getSharedData()
    {
        final Uri DataUri = (Uri)mActivityIntent.getParcelableExtra(Intent.EXTRA_STREAM);
        byte[] ByteArray = null;
        InputStream DataStream;

        try
        {
            DataStream = mActivityInstance.getContentResolver().openInputStream(DataUri);
            ByteArray = new byte[DataStream.available()];
            DataStream.read(ByteArray);
        }
        catch(FileNotFoundException e)
        {
            return null;
        }
        catch(IOException e)
        {
            return null;
        }

        return ByteArray;
    }

    public boolean shareText(String Text)
    {
        Intent SendIntent = new Intent();

        SendIntent.setAction(Intent.ACTION_SEND);
        SendIntent.putExtra(Intent.EXTRA_TEXT, Text);
        SendIntent.setType("text/plain");

        mActivityInstance.startActivity(Intent.createChooser(SendIntent, null));       
        return true;
    }

    public boolean shareData(String MimeType, String DataFilePath)
    {
        final String PackageName = mActivityInstance.getApplicationContext().getPackageName();
        Intent SendIntent = new Intent();
        Uri FileUri;

        try
        {
            FileUri = FileProvider.getUriForFile(mActivityInstance,
                                                 PackageName + ".qtandroidtoolsfileprovider",
                                                 new File(DataFilePath)
                                                 );
        }
        catch(IllegalArgumentException e)
        {
            Log.e(TAG, "The selected file can't be shared: " + DataFilePath);
            return false;
        }

        SendIntent.setAction(Intent.ACTION_SEND);
        SendIntent.putExtra(Intent.EXTRA_STREAM, FileUri);
        SendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        SendIntent.setType(MimeType);

        mActivityInstance.startActivity(Intent.createChooser(SendIntent, null));
        return true;
    }

    public boolean returnSharedFile(boolean FileAvailable, String MimeType, String FilePath)
    {
        final String PackageName = mActivityInstance.getApplicationContext().getPackageName();
        Intent ReturnIntent = new Intent(PackageName + ".ACTION_RETURN_FILE");

        if(FileAvailable == true)
        {
            Uri FileUri;

            try
            {
                FileUri = FileProvider.getUriForFile(mActivityInstance,
                                                     PackageName + ".qtandroidtoolsfileprovider",
                                                     new File(FilePath)
                                                     );
            }
            catch(IllegalArgumentException e)
            {
                Log.e(TAG, "The selected file can't be shared: " + FilePath);
                return false;
            }

            ReturnIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            ReturnIntent.setDataAndType(FileUri, MimeType);
            mActivityInstance.setResult(Activity.RESULT_OK, ReturnIntent);
        }
        else
        {
            ReturnIntent.setDataAndType(null, "");
            mActivityInstance.setResult(Activity.RESULT_CANCELED, ReturnIntent);
        }

        return true;
    }

    public byte[] getRequestedSharedFile()
    {
        byte[] ByteArray = null;

        if(mInputSharedFile != null)
        {
            final FileInputStream DataStream = new FileInputStream(mInputSharedFile.getFileDescriptor());

            try
            {
                ByteArray = new byte[DataStream.available()];
                DataStream.read(ByteArray);
            }
            catch(IOException e)
            {
                return null;
            }

            closeSharedFile();
        }

        return ByteArray;
    }

    public Intent getRequestSharedFileIntent(String MimeType)
    {
        Intent RequestFileIntent = new Intent(Intent.ACTION_PICK);
        RequestFileIntent.setType(MimeType);
        return RequestFileIntent;
    }

    public boolean requestSharedFileIntentDataResult(Intent Data)
    {
        final ContentResolver Resolver = mActivityInstance.getContentResolver();
        final Uri SharedFileUri = Data.getData();
        String FileName, MimeType;
        Cursor DataCursor;
        long FileSize;

        closeSharedFile();

        try
        {
            mInputSharedFile = Resolver.openFileDescriptor(SharedFileUri, "r");
        }
        catch(FileNotFoundException e)
        {
            Log.e(TAG, "Shared file not found");
            return false;
        }

        DataCursor = Resolver.query(SharedFileUri, null, null, null, null);
        DataCursor.moveToFirst();
        FileName = DataCursor.getString(DataCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
        FileSize = DataCursor.getLong(DataCursor.getColumnIndex(OpenableColumns.SIZE));
        MimeType = Resolver.getType(SharedFileUri);

        requestedSharedFileInfo(MimeType, FileName, FileSize);
        return true;
    }

    public void closeSharedFile()
    {
        if(mInputSharedFile != null)
        {
            try
            {
                mInputSharedFile.close();
            }
            catch(IOException e)
            {
            }
            mInputSharedFile = null;
        }
    }

    private int ACTION_NONE = 0;
    private int ACTION_SEND = 1;
    private int ACTION_SEND_MULTIPLE = 2;
    private int ACTION_PICK = 3;

    private static native void requestedSharedFileInfo(String mimeType, String name, long size);
}
