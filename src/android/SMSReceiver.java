package com.rbcorrea.cordova.receiver;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class SMSReceiver extends CordovaPlugin {    
       
    private ContentObserver mObserver = null;
    private BroadcastReceiver mReceiver = null;
    private boolean mIntercept = false;
    private String lastFrom = "";
    private String lastContent = "";

    public boolean execute(String action, JSONArray inputs, CallbackContext callbackContext) throws JSONException {
        PluginResult result = null;
        if ("startWatch".equals(action)) {
            result = this.startWatch(callbackContext);
        } else if ("stopWatch".equals(action)) {
            result = this.stopWatch(callbackContext);
        } else if ("enableIntercept".equals(action)) {
            boolean on_off = inputs.optBoolean(0);
            result = this.enableIntercept(on_off, callbackContext);
        } else {
            Log.d("SMSReceiver", String.format("Not valid action: %s", action));
            result = new PluginResult(PluginResult.Status.INVALID_ACTION);
        }
        if (result != null) {
            callbackContext.sendPluginResult(result);
        }
        return true;
    }

    public void onDestroy() {
        this.stopWatch(null);
    }
    private PluginResult startWatch(CallbackContext callbackContext) {
        Log.d("SMSReceiver", "startWatch");
        if (this.mObserver == null) {
            this.createContentObserver();
        }
        if (this.mReceiver == null) {
            this.createIncomingSMSReceiver();
        }
        if (callbackContext != null) {
            callbackContext.success();
        }
        return null;
    }

    private PluginResult stopWatch(CallbackContext callbackContext) {
        Log.d("SMSReceiver", "stopWatch");
        Activity ctx = this.cordova.getActivity();
        if (this.mReceiver != null) {
            ctx.unregisterReceiver(this.mReceiver);
            this.mReceiver = null;
        }
        if (this.mObserver != null) {
            ctx.getContentResolver().unregisterContentObserver(this.mObserver);
            this.mObserver = null;
        }
        if (callbackContext != null) {
            callbackContext.success();
        }
        return null;
    }

    private PluginResult enableIntercept(boolean on_off, CallbackContext callbackContext) {
        Log.d("SMSReceiver", "enableIntercept");
        this.mIntercept = on_off;
        if (callbackContext != null) {
            callbackContext.success();
        }
        return null;
    }

    private void fireEvent(final String event, JSONObject json) {
    	final String str = json.toString();
    	Log.d("SMSReceiver", "Event: " + event + ", " + str);
    	
        cordova.getActivity().runOnUiThread(new Runnable(){
            @Override
            public void run() {
            	String js = String.format("javascript:cordova.fireDocumentEvent(\"%s\", {\"data\":%s});", event, str);
            	webView.loadUrl( js );
            }
        });
    }
    private void onSMSReceived(JSONObject json) {
        String from = json.optString("address");
        String content = json.optString("body");
        if (from.equals(this.lastFrom) && content.equals(this.lastContent)) {
            return;
        }
        this.lastFrom = json.optString("adress");
        this.lastContent = json.optString("body");
        this.fireEvent("onSMSReceived", json);
    }

    private JSONObject getJsonFromCursor(Cursor cur) {
		JSONObject json = new JSONObject();
		
		int nCol = cur.getColumnCount();
		String keys[] = cur.getColumnNames();

		try {
			for(int j=0; j<nCol; j++) {
				switch(cur.getType(j)) {
				case Cursor.FIELD_TYPE_NULL:
					json.put(keys[j], null);
					break;
				case Cursor.FIELD_TYPE_INTEGER:
					json.put(keys[j], cur.getLong(j));
					break;
				case Cursor.FIELD_TYPE_FLOAT:
					json.put(keys[j], cur.getFloat(j));
					break;
				case Cursor.FIELD_TYPE_STRING:
					json.put(keys[j], cur.getString(j));
					break;
				case Cursor.FIELD_TYPE_BLOB:
					json.put(keys[j], cur.getBlob(j));
					break;
				}
			}
		} catch (Exception e) {
			return null;
		}

		return json;
    }

    protected void createIncomingSMSReceiver() {
        Activity ctx = this.cordova.getActivity();
        this.mReceiver = new BroadcastReceiver(){

            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                Log.d("SMSReceiver", ("onRecieve: " + action));
                if ("android.provider.Telephony.SMS_RECEIVED".equals(action)) {
                    Bundle bundle;
                    if (SMSReceiver.this.mIntercept) {
                        this.abortBroadcast();
                    }
                    if ((bundle = intent.getExtras()) != null) {
                        Object[] pdus;
                        if ((pdus = (Object[])bundle.get("pdus")).length != 0) {
                            for (int i = 0; i < pdus.length; ++i) {
                                SmsMessage sms = SmsMessage.createFromPdu((byte[])((byte[])pdus[i]));
                                JSONObject json = SMSReceiver.this.getJsonFromSmsMessage(sms);
                                SMSReceiver.this.onSMSReceived(json);
                            }
                        }
                    }
                }
            }
        };
        String[] filterstr = new String[]{"android.provider.Telephony.SMS_RECEIVED"};
        for (int i = 0; i < filterstr.length; ++i) {
            IntentFilter filter = new IntentFilter(filterstr[i]);
            filter.setPriority(100);
            ctx.registerReceiver(this.mReceiver, filter);
        }
    }

    protected void createContentObserver() {
        Activity ctx = this.cordova.getActivity();
        this.mObserver = new ContentObserver(new Handler()){

            public void onChange(boolean selfChange) {
                this.onChange(selfChange, null);
            }

            public void onChange(boolean selfChange, Uri uri) {
                ContentResolver resolver = cordova.getActivity().getContentResolver(); 
                Log.d("SMSReceiver", ("onChange, selfChange: " + selfChange + ", uri: " + (Object)uri));
                int id = -1;
                String str;
                if (uri != null && (str = uri.toString()).startsWith("content://sms/")) {
                    try {
                        id = Integer.parseInt(str.substring("content://sms/".length()));
                        Log.d("SMSReceiver", ("sms id: " + id));
                    }
                    catch (NumberFormatException nfe) {
                        // empty catch block
                    }
                }
                if (id == -1) {
                    uri = Uri.parse("content://sms/inbox");
                }
                Cursor cur = resolver.query(uri, null, null, null, "_id desc");
                if (cur != null) {
                    int n = cur.getCount();
                    Log.d("SMSReceiver", ("n = " + n));
                    if (n > 0 && cur.moveToFirst()) {
                        JSONObject json;
                        if ((json = SMSReceiver.this.getJsonFromCursor(cur)) != null) {
                            onSMSReceived(json);
                        } else {
                            Log.d("SMSReceiver", "fetch record return null");
                        }
                    }
                    cur.close();
                }
            }
        };
        ctx.getContentResolver().registerContentObserver(Uri.parse("content://sms/inbox"), true, this.mObserver);
        Log.d("SMSReceiver", "REGISTERED OBSERVER!");
    }

    private JSONObject getJsonFromSmsMessage(SmsMessage sms) {
    	JSONObject json = new JSONObject();
    	
        try {
        	json.put( "address", sms.getOriginatingAddress() );
        	json.put( "body", sms.getMessageBody() ); // May need sms.getMessageBody.toString()
        	json.put( "date_sent", sms.getTimestampMillis() );
        	json.put( "date", System.currentTimeMillis() );
        	json.put( "read", 0 );
        	json.put( "seen", 0 );
        	json.put( "status", sms.getStatus() );
        	json.put( "type", 1 );
        	json.put( "service_center", sms.getServiceCenterAddress());
        	
        } catch ( Exception e ) { 
            e.printStackTrace(); 
        }

    	return json;
    }

    private ContentValues getContentValuesFromJson(JSONObject json) {
    	ContentValues values = new ContentValues();
    	values.put( "address", json.optString("address") );
    	values.put( "body", json.optString("body"));
    	values.put( "date_sent",  json.optLong("date_sent"));
    	values.put( "read", json.optInt("read"));
    	values.put( "seen", json.optInt("seen"));
    	values.put( "type", json.optInt("type") );
    	values.put( "service_center", json.optString("service_center"));
    	return values;
    }

    private PluginResult restoreSMS(JSONArray array, CallbackContext callbackContext) {
        ContentResolver resolver = this.cordova.getActivity().getContentResolver();
        Uri uri = Uri.parse("content://sms/inbox");
        int n = array.length();
        int m = 0;
        for (int i = 0; i < n; ++i) {
            JSONObject json;
            if ((json = array.optJSONObject(i)) == null) continue;
            String str = json.toString();
            Log.d("SMSReceiver", str);
            Uri newuri = resolver.insert(uri, this.getContentValuesFromJson(json));
            Log.d("SMSReceiver", ("inserted: " + newuri.toString()));
            ++m;
        }
        if (callbackContext != null) {
            callbackContext.success(m);
        }
        return null;
    }

    
}