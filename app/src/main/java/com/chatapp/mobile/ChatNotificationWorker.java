package com.chatapp.mobile;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class ChatNotificationWorker extends Worker {
    private static final String CHANNEL="chat_messages";
    public ChatNotificationWorker(Context context, WorkerParameters params){super(context,params);}
    private String iso(long time){
        SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));return f.format(new Date(time));
    }
    @Override public Result doWork(){
        Context context=getApplicationContext();
        String uid=ChatSession.userId(context),token=ChatSession.validToken(context);
        if(uid.isEmpty()||token.isEmpty())return Result.success();
        try{
            SharedPreferences prefs=context.getSharedPreferences("chat_notifications",Context.MODE_PRIVATE);
            long last=prefs.getLong("last_check",0L),now=System.currentTimeMillis();
            if(last==0L){prefs.edit().putLong("last_check",now).apply();return Result.success();}
            JSONArray memberships=SupabaseClient.getArray("/rest/v1/conversation_members?select=conversation_id&user_id=eq."+SupabaseClient.enc(uid),token);
            StringBuilder ids=new StringBuilder();
            for(int i=0;i<memberships.length();i++){
                String id=memberships.getJSONObject(i).optString("conversation_id");
                if(id.isEmpty())continue;
                if(ids.length()>0)ids.append(",");
                ids.append(id);
            }
            if(ids.length()==0){prefs.edit().putLong("last_check",now).apply();return Result.success();}
            String path="/rest/v1/messages?select=id,body,sender_id,created_at,attachment_path&sender_id=neq."+SupabaseClient.enc(uid)
                +"&created_at=gt."+SupabaseClient.enc(iso(last))+"&conversation_id=in.("+ids+")&order=created_at.asc&limit=20";
            JSONArray messages=SupabaseClient.getArray(path,token);
            prefs.edit().putLong("last_check",now).apply();
            if(messages.length()==0)return Result.success();
            NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
            if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel(CHANNEL,"Chat messages",NotificationManager.IMPORTANCE_DEFAULT));
            Intent launch=new Intent(context,MainActivity.class);
            launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pending=PendingIntent.getActivity(context,0,launch,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            for(int i=0;i<messages.length();i++){
                JSONObject msg=messages.optJSONObject(i);
                if(msg==null)continue;
                String body=msg.optString("body","").trim();
                if(body.isEmpty())body=msg.optString("attachment_path","").isEmpty()?"New message":"Sent an attachment";
                if(body.length()>120)body=body.substring(0,117)+"...";
                NotificationCompat.Builder notification=new NotificationCompat.Builder(context,CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_dialog_email)
                    .setContentTitle("New chat message")
                    .setContentText(body)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                    .setContentIntent(pending).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_DEFAULT);
                try{NotificationManagerCompat.from(context).notify(msg.optString("id").hashCode(),notification.build());}catch(SecurityException ignored){}
            }
            return Result.success();
        }catch(Exception e){return Result.retry();}
    }
}
