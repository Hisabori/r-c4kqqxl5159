package app.oncare.personal;

import android.app.*;
import android.content.*;
import java.util.Calendar;

/** One inexact alarm at a time: calendar recalculation handles DST and time changes. */
public class ReminderReceiver extends BroadcastReceiver {
    static PendingIntent alarm(Context c) {
        return PendingIntent.getBroadcast(c, 31, new Intent(c, ReminderReceiver.class).setAction("app.oncare.REMIND"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    static void schedule(Context c) {
        SharedPreferences p=c.getSharedPreferences("care",Context.MODE_PRIVATE);
        AlarmManager a=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        a.cancel(alarm(c));
        if(!p.getBoolean("reminder",false))return;
        Calendar next=Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY,p.getInt("hour",21));next.set(Calendar.MINUTE,p.getInt("minute",0));next.set(Calendar.SECOND,0);next.set(Calendar.MILLISECOND,0);
        if(next.getTimeInMillis()<=System.currentTimeMillis())next.add(Calendar.DAY_OF_YEAR,1);
        a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.getTimeInMillis(),alarm(c));
    }
    static void channel(Context c) {
        NotificationManager n=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        n.createNotificationChannel(new NotificationChannel("daily","하루 기록 알림",NotificationManager.IMPORTANCE_DEFAULT));
    }
    @Override public void onReceive(Context c,Intent i) {
        if("app.oncare.REMIND".equals(i.getAction()) && c.getSharedPreferences("care",Context.MODE_PRIVATE).getBoolean("reminder",false)) {
            channel(c);
            NotificationManager n=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
            if(n.areNotificationsEnabled() && (android.os.Build.VERSION.SDK_INT<33 || c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")==android.content.pm.PackageManager.PERMISSION_GRANTED)) {
                PendingIntent open=PendingIntent.getActivity(c,0,new Intent(c,EnhancedActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                n.notify(31,new Notification.Builder(c,"daily").setSmallIcon(android.R.drawable.ic_menu_edit).setContentTitle("오늘의 나를 돌아볼 시간").setContentText("마음과 작은 실천을 온케어에 남겨보세요.").setContentIntent(open).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).build());
            }
        }
        schedule(c);
    }
}
