package app.oncare.personal;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import org.json.*;
import java.util.ArrayList;

/** Runs only in the separate test APK, never in the distributed application. */
public class BackupChecks extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try {
            progress("TLS connection");SyncChecks.run(getTargetContext());
            progress("Workout state");PracticeChecks.run(this);
            progress("Widget and shortcut screens");WidgetRouteChecks.run(this);
            progress("Medication schedule and notifications");MedicationChecks.run(this);
            progress("Medication transfer and preview");MedicationTransferChecks.run(this);
            progress("QR and personal sharing");PersonalShareChecks.run(this);
            progress("Health record catalog and original values");HealthCatalogChecks.run();HealthBrowserChecks.run(this);
            progress("Sleep export");JSONObject sleepFixture=SleepExportChecks.run();
            progress("Backup and reminders");EnhancedActivity a=(EnhancedActivity)startActivitySync(new Intent(getTargetContext(),EnhancedActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            final Throwable[] failure={null};
            runOnMainSync(()->{try {
                JSONObject original=a.backup();a.validateBackup(original);
                JSONObject sleepBackup=new JSONObject().put("version",3).put("entries",new JSONArray().put(sleepFixture));a.validateBackup(sleepBackup);a.restore(sleepBackup);
                JSONObject sleepRoundtrip=a.backup();JSONObject savedSleep=null;for(int i=0;i<sleepRoundtrip.getJSONArray("entries").length();i++){JSONObject candidate=sleepRoundtrip.getJSONArray("entries").getJSONObject(i);if(candidate.optString("id").equals(sleepFixture.getString("id")))savedSleep=candidate;}
                if(savedSleep==null||savedSleep.getDouble("date")!=sleepFixture.getDouble("date")||savedSleep.getDouble("wakeTime")!=savedSleep.getDouble("date")||!savedSleep.getJSONObject("sleep").toString().equals(sleepFixture.getJSONObject("sleep").toString()))throw new AssertionError("Sleep details lost in full backup");
                ArrayList<JSONObject> withoutSleep=new ArrayList<>();for(JSONObject record:a.entries)if(!record.optString("id").equals(sleepFixture.getString("id")))withoutSleep.add(record);if(!a.persist(withoutSleep))throw new AssertionError("Sleep fixture cleanup failed");
                JSONObject steps=new JSONObject().put("version",3).put("entries",new JSONArray().put(new JSONObject().put("id","steps-check").put("kind","걸음").put("value",5000).put("date",System.currentTimeMillis()).put("note","")));a.validateBackup(steps);steps.getJSONArray("entries").getJSONObject(0).put("value",1.5);reject(a,steps);
                JSONArray routine=new JSONArray().put(new JSONObject().put("id","roundtrip").put("title","Round trip"));
                JSONObject check=new JSONObject().put(a.todayKey(),new JSONObject().put("roundtrip",true));
                JSONObject fixture=new JSONObject(original.toString()).put("routines",routine).put("checks",check);
                fixture.getJSONObject("goals").put("weight",65).put("water",1800);
                a.validateBackup(fixture);a.restore(fixture);
                if(a.goal("weight",0)!=65||a.goal("water",0)!=1800||!a.checked("roundtrip",a.todayKey()))throw new AssertionError("Settings not restored");
                JSONObject roundtrip=new JSONObject(a.backup().toString());a.validateBackup(roundtrip);
                int before=a.entries.size();a.restore(roundtrip);if(a.entries.size()!=before)throw new AssertionError("Duplicate import");
                // Legacy V2 backups preserve goals and routines.
                JSONObject legacy=new JSONObject().put("version",2).put("entries",new JSONArray());a.validateBackup(legacy);a.restore(legacy);
                if(a.goal("weight",0)!=65||a.routines.length()!=1)throw new AssertionError("Legacy import lost settings");
                JSONObject invalid=new JSONObject(roundtrip.toString());invalid.getJSONObject("goals").put("water",-1);reject(a,invalid);
                invalid=new JSONObject(roundtrip.toString());invalid.getJSONObject("checks").put("2026-99-99",new JSONObject());reject(a,invalid);
                invalid=new JSONObject(roundtrip.toString());invalid.getJSONObject("checks").put(a.todayKey(),new JSONObject().put("missing",true));reject(a,invalid);
                invalid=new JSONObject(roundtrip.toString());invalid.put("version",99);reject(a,invalid);
                if(a.entries.size()!=before)throw new AssertionError("Invalid file changed records");
                a.restore(original);
                android.app.NotificationManager manager=(android.app.NotificationManager)a.getSystemService(Context.NOTIFICATION_SERVICE);
                manager.cancelAll();a.care.edit().putBoolean("reminder",true).commit();
                new ReminderReceiver().onReceive(a,new Intent("app.oncare.REMIND"));
                boolean allowed=manager.areNotificationsEnabled();
                waitNotifications(manager,allowed?1:0);
                manager.cancelAll();waitNotifications(manager,0);a.care.edit().putBoolean("reminder",false).commit();ReminderReceiver.schedule(a);
                new ReminderReceiver().onReceive(a,new Intent("app.oncare.REMIND"));
                if(manager.getActiveNotifications().length!=0)throw new AssertionError("Disabled reminder posted notification");
                a.finish();
            }catch(Throwable e){failure[0]=e;}});
            if(failure[0]!=null)throw new AssertionError(failure[0]);
            result.putString("stream","PASS: TLS pinning, token rejection and pairing reset; practice timer persistence, paused time, 24-hour cap, interrupted-save deduplication, weekly averages and routine streaks; widget privacy/date updates, immutable quick routes, native sleep stages/source and awake union, memo preservation; full backup round trip, goal and routine restore, duplicate merge, V2 compatibility, malformed backup rejection, reminder permission handling; medication validation/persistence/weekday rollover/taken checks/private notifications/permission handling; cross-platform medication backup/recovery/import preview and consent; QR encode/decode and invalid payload rejection; selected personal summary/date filtering/private field exclusion/share preview/version\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable e){result.putString("stream","FAIL: "+e+"\n");finish(Activity.RESULT_CANCELED,result);}
    }
    void progress(String name){Bundle status=new Bundle();status.putString("stream","CHECK: "+name+"\n");sendStatus(0,status);}
    void waitNotifications(android.app.NotificationManager manager,int expected)throws Exception { for(int i=0;i<40;i++){if(manager.getActiveNotifications().length==expected)return;Thread.sleep(50);}throw new AssertionError("Notification count "+manager.getActiveNotifications().length+", expected "+expected+", enabled "+manager.areNotificationsEnabled()); }
    void reject(EnhancedActivity a,JSONObject file)throws Exception{try{a.validateBackup(file);}catch(JSONException expected){return;}throw new AssertionError("Invalid backup accepted");}
}
