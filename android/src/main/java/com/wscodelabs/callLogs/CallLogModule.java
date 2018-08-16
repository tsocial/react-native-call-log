package com.wscodelabs.callLogs;

import android.util.Log;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.database.Cursor;
import java.util.Date;
import android.content.Context;
import org.json.*;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

import java.util.Map;

public class CallLogModule extends ReactContextBaseJavaModule {

    private Context context;
    private Cursor cursor;

    public CallLogModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.context= reactContext;
    }

    @Override
    public String getName() {
        return "CallLogs";
    }

    @ReactMethod
    public void reset() {
        cursor = null;
    }

    private String getValueAtColumn(int columnIndex, String columnName) {
        try {
            return cursor.getString(columnIndex);
        } 
        catch (Exception e) {
            Log.d("CallLogModule", "could not fetch value at column" + columnName);
            return "";
        }
    }

    @ReactMethod
    public void show(int size, Promise promise) {
        if (cursor == null) {
            cursor = this.context.getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                null,
                null,
                null,
                CallLog.Calls.DATE + " DESC");
        }

        if (cursor.isClosed()) {
            promise.resolve("[]");
            return;
        }

        int number = cursor.getColumnIndex(CallLog.Calls.NUMBER);
        int type = cursor.getColumnIndex(CallLog.Calls.TYPE);
        int date = cursor.getColumnIndex(CallLog.Calls.DATE);
        int duration = cursor.getColumnIndex(CallLog.Calls.DURATION);  
        int name = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME);
        int photo = cursor.getColumnIndex(CallLog.Calls.CACHED_PHOTO_URI);

        JSONArray callArray = new JSONArray();
        int count = 0;

        while (count < size && cursor.moveToNext()) {
            String callName = getValueAtColumn(name, "name");
            String callPhotoURI = getValueAtColumn(photo, "photo");
            String phNumber = getValueAtColumn(number, "number");
            String callType = getValueAtColumn(type, "type");
            String callDate = getValueAtColumn(date, "date");
            Date callDayTime = new Date(Long.valueOf(callDate));
            String callDuration = getValueAtColumn(duration, "duration");
            String dir = null;
            int dircode = Integer.parseInt(callType);
            switch (dircode) {
            case CallLog.Calls.OUTGOING_TYPE:
                dir = "OUTGOING";
                break;
            case CallLog.Calls.INCOMING_TYPE:
                dir = "INCOMING";
                break;

            case CallLog.Calls.MISSED_TYPE:
                dir = "MISSED";
                break;
            }

            JSONObject callObj = new JSONObject();
            try{
                callObj.put("phoneNumber",phNumber);
                callObj.put("callType", dir);
                callObj.put("callDate", callDate);
                callObj.put("callDuration", callDuration);
                callObj.put("callDayTime", callDayTime);
                callObj.put("name", callName);
                callObj.put("photoURI", callPhotoURI);
                callArray.put(callObj); 
            }
            catch(JSONException e){
                promise.reject(e);
            }

            count++;
        }

        if (count < size) {
            cursor.close();
        }
        promise.resolve(callArray.toString());
    }
}