package app.oncare.personal;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.*;

public class MedicationReceiver extends BroadcastReceiver {
    static final String ACTION="app.oncare.MEDICATION",CHANNEL="medications";
    static PendingIntent alarm(Context c,long due){return PendingIntent.getBroadcast(c,410,new Intent(c,MedicationReceiver.class).setAction(ACTION).putExtra("due",due),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static boolean allowed(Context c){NotificationManager manager=c.getSystemService(NotificationManager.class);NotificationChannel channel=manager.getNotificationChannel(CHANNEL);return manager.areNotificationsEnabled()&&(channel==null||channel.getImportance()!=NotificationManager.IMPORTANCE_NONE)&&(Build.VERSION.SDK_INT<33||c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")==PackageManager.PERMISSION_GRANTED);}
    static boolean exact(Context c){return Build.VERSION.SDK_INT<31||c.getSystemService(AlarmManager.class).canScheduleExactAlarms();}
    static long next(MedicationStore.Plan plan,long now,TimeZone zone){
        if(!plan.enabled)return Long.MAX_VALUE;long earliest=Long.MAX_VALUE;
        for(int offset=0;offset<=7;offset++){Calendar candidate=Calendar.getInstance(zone);candidate.setTimeInMillis(now);candidate.add(Calendar.DAY_OF_YEAR,offset);candidate.set(Calendar.HOUR_OF_DAY,plan.hour);candidate.set(Calendar.MINUTE,plan.minute);candidate.set(Calendar.SECOND,0);candidate.set(Calendar.MILLISECOND,0);if(plan.days.contains(candidate.get(Calendar.DAY_OF_WEEK))&&candidate.getTimeInMillis()>now)earliest=Math.min(earliest,candidate.getTimeInMillis());}return earliest;
    }
    static void channel(Context c){c.getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"투약 일정 알림",NotificationManager.IMPORTANCE_HIGH));}
    static void schedule(Context c){schedule(c,System.currentTimeMillis());}
    static void schedule(Context c,long now){
        AlarmManager manager=c.getSystemService(AlarmManager.class);manager.cancel(alarm(c,0));MedicationStore store=new MedicationStore(c);
        if(!store.readable||!allowed(c))return;long due=Long.MAX_VALUE;for(MedicationStore.Plan plan:store.plans)due=Math.min(due,next(plan,now,TimeZone.getDefault()));if(due==Long.MAX_VALUE){c.getSystemService(NotificationManager.class).cancel(41);return;}
        PendingIntent intent=alarm(c,due);
        try{if(exact(c))manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,due,intent);else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,due,intent);}catch(SecurityException denied){manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,due,intent);}
    }
    @android.annotation.SuppressLint("MissingPermission") // allowed() checks POST_NOTIFICATIONS and channel settings before notify().
    @Override public void onReceive(Context c,Intent intent){
        long now=System.currentTimeMillis(),due=intent.getLongExtra("due",0);
        if(ACTION.equals(intent.getAction())&&due>0&&due<=now&&now-due<12*60*60*1000L&&allowed(c)){
            MedicationStore store=new MedicationStore(c);Calendar date=Calendar.getInstance();date.setTimeInMillis(due);boolean valid=false;
            for(MedicationStore.Plan p:store.plans)if(p.enabled&&p.days.contains(date.get(Calendar.DAY_OF_WEEK))&&p.hour==date.get(Calendar.HOUR_OF_DAY)&&p.minute==date.get(Calendar.MINUTE))valid=true;
            android.content.SharedPreferences prefs=c.getSharedPreferences("medication-alarm",Context.MODE_PRIVATE);
            if(store.readable&&valid&&prefs.getLong("delivered",0)!=due){
                channel(c);PendingIntent open=PendingIntent.getActivity(c,41,new Intent(c,MedicationActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                c.getSystemService(NotificationManager.class).notify(41,new Notification.Builder(c,CHANNEL).setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(DeepMind.notificationTitle(c.getSharedPreferences("app-preferences",0).getBoolean("deep-mind",false),c.getSharedPreferences("app-preferences",0).getBoolean("deep-explicit",false))).setContentText(DeepMind.notificationBody(c.getSharedPreferences("app-preferences",0).getBoolean("deep-mind",false))).setContentIntent(open).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).build());prefs.edit().putLong("delivered",due).commit();
            }
        }
        schedule(c,Math.max(now,due>0&&due<=now?due:now)+1000);
    }
}
