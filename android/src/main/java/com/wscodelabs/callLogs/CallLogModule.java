package com.wscodelabs.callLogs;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.CallLog;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.provider.CallLog.Calls;
import android.database.Cursor;

import java.util.ArrayList;
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

    private static final int LINE1_NUMBER_ID = -1;
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
    public void fetchInRange(String from, String to, int size, Promise promise) {
        String selection = CallLog.Calls.DATE + " > ? AND " + CallLog.Calls.DATE + " < ?";
        String[] selectionArgs = {from, to};
        fetch(from, size, selection, selectionArgs, true, promise);
    }

    @ReactMethod
    public void fetchFromDate(String from, int size, Promise promise) {
        String selection = CallLog.Calls.DATE + " > ?";
        String[] selectionArgs = {from};
        fetch(from, size, selection, selectionArgs, true, promise);
    }

    @ReactMethod
    public void fetchFrom(int size, Promise promise) {
        long currentTime = System.currentTimeMillis();
        String from = String.valueOf(currentTime);
        fetch(from, size, null, null, false, promise);
    }

    @ReactMethod
    public void fetchFrom(String from, int size, Promise promise) {
        String selection = CallLog.Calls.DATE + " < ?";
        String[] selectionArgs = {from};
        fetch(from, size, selection, selectionArgs, false, promise);
    }

    @ReactMethod
    public void fetchForNumberFrom(String number, int size, Promise promise) {
        long currentTime = System.currentTimeMillis();
        String from = String.valueOf(currentTime);
        String selection = CallLog.Calls.NUMBER + " = ?";
        String[] selectionArgs = {number};
        fetch(from, size, selection, selectionArgs, false, promise);
    }

    @ReactMethod
    public void fetchForNumberFrom(String number, String from, int size, Promise promise) {
        String selection = CallLog.Calls.NUMBER + " = ? AND " + CallLog.Calls.DATE + " < ?";
        String[] selectionArgs = {number, from};
        fetch(from, size, selection, selectionArgs, false, promise);
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

    public List<AidoCallLog> fetchCallLogBatch(String fromDateTimestamp, int size) {
        String selection = CallLog.Calls.DATE + " > ?";
        String[] selectionArgs = {fromDateTimestamp};
        return fetch(size, selection, selectionArgs, true);
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
                            String subscriptionNumber = subscriptionInfo.getNumber();
                            if (!TextUtils.isEmpty(subscriptionNumber)) {
                                simPhones.put(subscriptionId, subscriptionInfo.getNumber());
                            }

                            Log.e("CallLogItem", subscriptionId + "");
                        }
                    }
                }

                TelephonyManager telMgr = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                String number = telMgr.getLine1Number();
                if (!TextUtils.isEmpty(number))
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

    private List<AidoCallLog> fetch(int size, String selection, String[] selectionArgs, Boolean isAscending) {
        List<AidoCallLog> callLogs = new ArrayList<>();
        Map<Integer, String> phoneNumbers = getSimNumbers();

        Cursor cursor = this.context.getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                null,
                selection,
                selectionArgs,
                CallLog.Calls.DATE + (isAscending ? " ASC" : " DESC"));

        if (cursor == null) {
            return callLogs;
        }

        int number = cursor.getColumnIndex(CallLog.Calls.NUMBER);
        int phoneAccountId = cursor.getColumnIndex(Calls.PHONE_ACCOUNT_ID);
        int type = cursor.getColumnIndex(CallLog.Calls.TYPE);
        int date = cursor.getColumnIndex(CallLog.Calls.DATE);
        int duration = cursor.getColumnIndex(CallLog.Calls.DURATION);
        int name = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME);
        int photo = cursor.getColumnIndex(CallLog.Calls.CACHED_PHOTO_URI);

        int count = 0;

        while (count < size && cursor.moveToNext()) {
            try {
                String callName = getValueAtColumn(cursor, name, "name");
                String callPhotoURI = getValueAtColumn(cursor, photo, "photo");
                String phNumber = getValueAtColumn(cursor, number, "number");
                String phoneAccount = getValueAtColumn(cursor, phoneAccountId, Calls.PHONE_ACCOUNT_ID);
                String callType = getValueAtColumn(cursor, type, "type");
                String callDate = getValueAtColumn(cursor, date, "date");
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

                AidoCallLog callLog = new AidoCallLog();
                callLog.setPhoneNumber(phNumber);
                String myPhone = getMyPhone(phoneNumbers, phoneAccount);
                callLog.setPhoneAccount(myPhone);
                callLog.setCallType(dir);
                callLog.setCallDate(Long.valueOf(callDate));
                callLog.setCallDuration(callDuration);
                callLog.setName(callName);
                callLog.setPhotoURI(callPhotoURI);

                callLogs.add(callLog);
            } catch (Exception ex) {
                Log.e("CallLogModule", ex.getMessage(), ex);
            }

            count++;
        }

        cursor.close();
        return callLogs;
    }

    private void fetch(String from, int size, String selection, String[] selectionArgs, Boolean isAscending, Promise promise) {

        Map<Integer, String> phoneNumbers = getSimNumbers();

        Cursor cursor = this.context.getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                null,
                selection,
                selectionArgs,
                CallLog.Calls.DATE + (isAscending ? " ASC" : " DESC"));

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

        if (TextUtils.isEmpty(myPhone)) {
            myPhone = "";
        }
        return myPhone;
    }

    public static class AidoCallLog {

        private String phoneNumber;
        private String phoneAccount;
        private String callType;
        private long callDate;
        private String callDuration;
        private String name;
        private String photoURI;

        public String getPhoneNumber() {
            return phoneNumber;
        }

        public void setPhoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
        }

        public String getPhoneAccount() {
            return phoneAccount;
        }

        public void setPhoneAccount(String phoneAccount) {
            this.phoneAccount = phoneAccount;
        }

        public String getCallType() {
            return callType;
        }

        public void setCallType(String callType) {
            this.callType = callType;
        }

        public long getCallDate() {
            return callDate;
        }

        public void setCallDate(long callDate) {
            this.callDate = callDate;
        }

        public String getCallDuration() {
            return callDuration;
        }

        public void setCallDuration(String callDuration) {
            this.callDuration = callDuration;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPhotoURI() {
            return photoURI;
        }

        public void setPhotoURI(String photoURI) {
            this.photoURI = photoURI;
        }
    }
}