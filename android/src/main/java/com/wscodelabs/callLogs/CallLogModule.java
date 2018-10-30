package com.wscodelabs.callLogs;

import android.util.Log;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.database.Cursor;
import java.util.Date;
import java.lang.*;
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

    public CallLogModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.context= reactContext;
    }

    @Override
    public String getName() {
        return "CallLogs";
    }

    @ReactMethod
    public void fetchFromDate(String from, int size, Promise promise) {
        String selection = CallLog.Calls.DATE + " > ?";
        String[] selectionArgs = { from };
        fetch(from, size, selection, selectionArgs, promise);
    }

    @ReactMethod
    public void fetchFrom(int size, Promise promise) {
        long currentTime = System.currentTimeMillis();
        String from = String.valueOf(currentTime);
        fetch(from, size, null, null, promise);
    }

    @ReactMethod
    public void fetchFrom(String from, int size, Promise promise) {
        String selection = CallLog.Calls.DATE + " < ?";
        String[] selectionArgs = { from };
        fetch(from, size, selection, selectionArgs, promise);
    }

    @ReactMethod
    public void fetchForNumberFrom(String number, int size, Promise promise) {
        long currentTime = System.currentTimeMillis();
        String from = String.valueOf(currentTime);
        String selection = CallLog.Calls.NUMBER + " = ?";
        String[] selectionArgs = { number };
        fetch(from, size, selection, selectionArgs, promise);
    }

    @ReactMethod
    public void fetchForNumberFrom(String number, String from, int size, Promise promise) {
        String selection = CallLog.Calls.NUMBER + " = ? AND " + CallLog.Calls.DATE + " < ?";
        String[] selectionArgs = { number, from };
        fetch(from, size, selection, selectionArgs, promise);
    }

    private String getValueAtColumn(Cursor cursor, int columnIndex, String columnName) {
        try {
            return cursor.getString(columnIndex);
        } 
        catch (Exception e) {
            Log.d("CallLogModule", "could not fetch value at column" + columnName);
            return "";
        }
    }

    private void fetch(String from, int size, String selection, String[] selectionArgs, Promise promise) {
        Cursor cursor = this.context.getContentResolver().query(
            CallLog.Calls.CONTENT_URI,
            null,
            selection,
            selectionArgs,
            CallLog.Calls.DATE + " DESC");

        if (cursor == null) {
            promise.resolve("[]");
            return;
        }

        int number = cursor.getColumnIndex(CallLog.Calls.NUMBER);
        int viaNumber = cursor.getColumnIndex(Calls.VIA_NUMBER);
        int type = cursor.getColumnIndex(CallLog.Calls.TYPE);
        int date = cursor.getColumnIndex(CallLog.Calls.DATE);
        int duration = cursor.getColumnIndex(CallLog.Calls.DURATION);  
        int name = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME);
        int photo = cursor.getColumnIndex(CallLog.Calls.CACHED_PHOTO_URI);

        JSONArray callArray = new JSONArray();
        int count = 0;

        while (count < size && cursor.moveToNext()) {
            String callName = getValueAtColumn(cursor, name, "name");
            String callPhotoURI = getValueAtColumn(cursor, photo, "photo");
            String phNumber = getValueAtColumn(cursor, number, "number");
            String viaPhNumber = getValueAtColumn(cursor, viaNumber, "viaNumber");
            String callType = getValueAtColumn(cursor, type, "type");
            String callDate = getValueAtColumn(cursor, date, "date");
            Date callDayTime = new Date(Long.valueOf(callDate));
            String callDuration = getValueAtColumn(cursor, duration, "duration");
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
                callObj.put("viaPhoneNumber",viaPhNumber);
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

        cursor.close();
        promise.resolve(callArray.toString());
    }
}