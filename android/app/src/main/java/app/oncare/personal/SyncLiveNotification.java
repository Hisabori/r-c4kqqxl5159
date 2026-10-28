package app.oncare.personal;

import android.app.*;
import android.content.*;
import android.os.Build;

/** Android Live Updates feed compatible Galaxy Now Bar surfaces. */
final class SyncLiveNotification {
    static final int ID=3202;
    static final String CHANNEL="health-sync-live";
    static void show(Context context,String text,boolean active){
        if(!context.getSharedPreferences("health-sync",Context.MODE_PRIVATE).getBoolean("live-notification",false))return;
        NotificationManager manager=context.getSystemService(NotificationManager.class);
        NotificationChannel channel=new NotificationChannel(CHANNEL,"건강 연결 실시간 상태",NotificationManager.IMPORTANCE_DEFAULT);channel.setSound(null,null);channel.enableVibration(false);manager.createNotificationChannel(channel);
        if(!manager.areNotificationsEnabled())return;
        PendingIntent open=PendingIntent.getActivity(context,82,new Intent(context,HealthSyncActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=new Notification.Builder(context,CHANNEL).setSmallIcon(android.R.drawable.stat_notify_sync).setContentTitle("온케어 · iPhone 건강 연결").setContentText(text).setContentIntent(open).setOnlyAlertOnce(true).setOngoing(active).setCategory(Notification.CATEGORY_PROGRESS).setVisibility(Notification.VISIBILITY_PRIVATE);
        // Public Notification.EXTRA_REQUEST_PROMOTED_ONGOING key; literal keeps compilation compatible with SDK 36.0.
        if(Build.VERSION.SDK_INT>=36){android.os.Bundle extras=new android.os.Bundle();extras.putBoolean("android.requestPromotedOngoing",active);builder.addExtras(extras);}
        if(!active)builder.setTimeoutAfter(60000);
        manager.notify(ID,builder.build());
    }
    static void cancel(Context context){context.getSystemService(NotificationManager.class).cancel(ID);}
}
