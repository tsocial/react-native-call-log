package com.wscodelabs.callLogs;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.database.Cursor;

import java.util.Date;
import java.lang.*;

import android.content.Context;

import org.json.*;
import org.w3c.dom.Text;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CallLogModule extends ReactContextBaseJavaModule {

    public static final int LINE1_NUMBER_ID = -1;
    private Context context;

    public CallLogModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.context = reactContext;
    }

    @Override
    public String getName() {
        return "CallLogs";
    }

    @ReactMethod
    public void fetchFromDate(String from, int size, Promise promise) {
        String selection = CallLog.Calls.DATE + " > ?";
        String[] selectionArgs = {from};
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
        String[] selectionArgs = {from};
        fetch(from, size, selection, selectionArgs, promise);
    }

    @ReactMethod
    public void fetchForNumberFrom(String number, int size, Promise promise) {
        long currentTime = System.currentTimeMillis();
        String from = String.valueOf(currentTime);
        String selection = CallLog.Calls.NUMBER + " = ?";
        String[] selectionArgs = {number};
        fetch(from, size, selection, selectionArgs, promise);
    }

    @ReactMethod
    public void fetchForNumberFrom(String number, String from, int size, Promise promise) {
        String selection = CallLog.Calls.NUMBER + " = ? AND " + CallLog.Calls.DATE + " < ?";
        String[] selectionArgs = {number, from};
        fetch(from, size, selection, selectionArgs, promise);
    }

    @ReactMethod
    public void getPhoneNumbers(Promise promise) {
        try {
            WritableArray writableArray = Arguments.createArray();
            Map<Integer, String> simPhones = getSimNumbers();
            for (Map.Entry<Integer, String> simPhone : simPhones.entrySet()) {
                WritableMap map = Arguments.createMap();
                map.putDouble("phone_account_id", simPhone.getKey());
                map.putString("phoneNumber", simPhone.getValue());
                writableArray.pushMap(map);
            }

            promise.resolve(writableArray);
        } catch (Exception ex) {
            promise.reject(ex);
        }

    }

    private Map<Integer, String> getSimNumbers() {
        Map<Integer, String> simPhones = new HashMap<>();
        try {
            if (context.checkCallingOrSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED ||
                    context.checkCallingOrSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED ||
                    context.checkCallingOrSelfPermission("android.permission.READ_PHONE_NUMBERS") == PackageManager.PERMISSION_GRANTED) {

                if (Build.VERSION.SDK_INT >= 22) {
                    List<SubscriptionInfo> subscriptionList = ((SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE))
                            .getActiveSubscriptionInfoList();
                    if (subscriptionList != null) {
                        for (SubscriptionInfo subscriptionInfo : subscriptionList) {
                            int subscriptionId = subscriptionInfo.getSubscriptionId();
                            simPhones.put(subscriptionId, subscriptionInfo.getNumber());
                            Log.e("CallLogItem", subscriptionId + "");
                        }
                    }
                }

                TelephonyManager telMgr = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                String number = telMgr.getLine1Number();
                simPhones.put(LINE1_NUMBER_ID, number);
            }
        } catch (Exception ex) {

        }
        return simPhones;
    }

    private String getValueAtColumn(Cursor cursor, int columnIndex, String columnName) {
        try {
            return cursor.getString(columnIndex);
        } catch (Exception e) {
            Log.d("CallLogModule", "could not fetch value at column" + columnName);
            return "";
        }
    }

    private void fetch(String from, int size, String selection, String[] selectionArgs, Promise promise) {

        Map<Integer, String> phoneNumbers = getSimNumbers();

        if (phoneNumbers.size() == 0) {
            promise.resolve("[]");
            Log.e("CallLogModule", "Can't get sim numbers");
            return;
        }

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
        int phoneAccountId = cursor.getColumnIndex(Calls.PHONE_ACCOUNT_ID);
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
            String phoneAccount = getValueAtColumn(cursor, phoneAccountId, Calls.PHONE_ACCOUNT_ID);
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
            try {
                callObj.put("phoneNumber", phNumber);

                String myPhone = getMyPhone(phoneNumbers, phoneAccount);
                callObj.put("phoneAccount", myPhone);
                callObj.put("callType", dir);
                callObj.put("callDate", callDate);
                callObj.put("callDuration", callDuration);
                callObj.put("callDayTime", callDayTime);
                callObj.put("name", callName);
                callObj.put("photoURI", callPhotoURI);
                callArray.put(callObj);
//                Log.e("CallLogItem", callObj.toString());
            } catch (JSONException e) {
                promise.reject(e);
            }

            count++;
        }

        cursor.close();
        promise.resolve(callArray.toString());
    }

    private String getMyPhone(Map<Integer, String> phoneNumbers, String phoneAccount) {
        String myPhone = "";
        try {
            myPhone = phoneNumbers.get(Integer.valueOf(phoneAccount));
        } catch (Exception ex) {
//            Log.e("CallLogModule", "Parse phoneAccountId error");
        }
        if (TextUtils.isEmpty(myPhone)) {
            myPhone = phoneNumbers.get(LINE1_NUMBER_ID);
        }
        return myPhone;
    }
}