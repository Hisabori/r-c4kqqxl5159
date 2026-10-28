package app.oncare.personal;

import android.app.Instrumentation;
import android.content.*;
import org.json.*;
import java.util.*;

/** Only in the test APK: clock arithmetic, interrupted saves and real atomic journal writes. */
final class PracticeChecks {
    static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static void bad(JSONObject json)throws Exception {try{PracticeState.Session.decode(json);}catch(JSONException expected){return;}throw new AssertionError("Damaged session accepted");}
    static JSONObject entry(String id,String kind,double value,long date)throws JSONException {return new JSONObject().put("id",id).put("kind",kind).put("value",value).put("date",date).put("note","");}
    static void math()throws Exception {
        long start=1_000_000;
        PracticeState.Session s=PracticeState.Session.start("걷기",20,start);
        require(s.duration(start+125_000)==125_000,"Running time");s.pause(start+125_000);
        require(s.duration(start+185_000)==125_000,"Paused time included");s.resume(start+185_000);
        require(s.duration(start+219_000)==159_000,"Resume elapsed time");s.checkpoint(start+219_000);
        PracticeState.Session recovered=PracticeState.Session.decode(new JSONObject(s.json().toString()));
        require(recovered.duration(start+279_000)==219_000,"Restart timestamp recovery");
        require(recovered.id.equals(s.id)&&recovered.type.equals("걷기")&&recovered.goal==20,"Session fields lost");
        recovered.observe(start+279_000);try{recovered.duration(start+278_000);throw new AssertionError("Backwards clock accepted");}catch(IllegalStateException expected){}
        PracticeState.Session capped=PracticeState.Session.start("근력",60,start);capped.pause(start+PracticeState.MAX_MILLIS+5000);
        require(capped.accumulated==PracticeState.MAX_MILLIS&&!capped.running&&capped.limited,"24-hour cap");
        require(capped.record(start+PracticeState.MAX_MILLIS+5000).getDouble("value")==1440,"Record cap");
        try{capped.resume(start+PracticeState.MAX_MILLIS+6000);throw new AssertionError("Capped session resumed");}catch(IllegalStateException expected){}
        PracticeState.Session shortSession=PracticeState.Session.start("스트레칭",10,start);
        try{shortSession.record(start+59_999);throw new AssertionError("Short session recorded");}catch(IllegalStateException expected){}
        require(shortSession.record(start+60_000).getDouble("value")==1,"One-minute threshold");
        bad(new JSONObject(s.json().toString()).put("accumulated",-1));bad(new JSONObject(s.json().toString()).put("accumulated",PracticeState.MAX_MILLIS+1));
        bad(new JSONObject(s.json().toString()).put("seenAt",s.anchorAt-1));bad(new JSONObject(s.json().toString()).put("anchorAt",s.startedAt-1));
        bad(new JSONObject(s.json().toString()).put("goal",15));bad(new JSONObject(s.json().toString()).put("goal",10.5));
        bad(new JSONObject(s.json().toString()).put("running","true"));bad(new JSONObject(s.json().toString()).put("id","not-a-uuid"));bad(new JSONObject(s.json().toString()).put("type","unknown"));
        PracticeState.Session future=PracticeState.Session.decode(PracticeState.Session.start("러닝",30,start+1000).json());
        try{future.duration(start);throw new AssertionError("Future timestamp accepted");}catch(IllegalStateException expected){}
        long today=PracticeState.day(System.currentTimeMillis());JSONObject marks=new JSONObject();
        for(int offset:new int[]{-6,-4,-2,-1})marks.put(PracticeState.dayKey(PracticeState.addDays(today,offset)),new JSONObject().put("r",true));
        PracticeState.RoutineProgress progress=new PracticeState.RoutineProgress(marks,"r",today);
        require(progress.count==4&&progress.streak==2&&!progress.days[6],"Streak must continue through yesterday");
        marks.put(PracticeState.dayKey(today),new JSONObject().put("r",true));progress=new PracticeState.RoutineProgress(marks,"r",today);
        require(progress.count==5&&progress.streak==3&&progress.days[6],"Today's streak");
        require(new PracticeState.RoutineProgress(marks,"missing",today).count==0,"Other routine marks included");
        long from=PracticeState.addDays(today,-7),prior=PracticeState.addDays(from,-7);
        ArrayList<JSONObject> records=new ArrayList<>();long yesterday=PracticeState.addDays(today,-1);
        records.add(entry("sleep-late","수면",8,yesterday+22*60*60*1000));records.add(entry("sleep-early","수면",4,yesterday+8*60*60*1000));
        records.add(entry("sleep-other","수면",6,from+12*60*60*1000));records.add(entry("mood-early","마음",1,yesterday+8*60*60*1000));
        records.add(entry("mood-late","마음",5,yesterday+22*60*60*1000));records.add(entry("exercise-a","운동",10,from));records.add(entry("exercise-b","운동",15,from+1000));
        records.add(entry("exercise-c","운동",20,yesterday+1000));records.add(entry("today","운동",999,today));records.add(entry("prior","운동",30,prior));
        records.add(entry("invalid","수면",0,yesterday));
        PracticeState.Period recent=new PracticeState.Period(records,from,today),previous=new PracticeState.Period(records,prior,from);
        require(recent.exerciseMinutes==45&&recent.exerciseDays==2,"Completed exercise period boundaries");
        require(recent.sleepMean==7&&recent.sleepDays==2&&recent.moodMean==5&&recent.moodDays==1,"Daily last record mean");
        require(previous.exerciseMinutes==30&&previous.exerciseDays==1&&Double.isNaN(previous.sleepMean)&&previous.sleepDays==0,"Missing days must stay missing");
        require(Double.isNaN(new PracticeState.Period(new ArrayList<>(),from,today).moodMean),"Empty mean coerced to zero");
    }
    static void run(Instrumentation instrumentation)throws Exception {
        math();
        Context context=instrumentation.getTargetContext();PracticeState store=new PracticeState(context);
        String original=store.prefs.getString("session",null);
        try {
            PracticeState.Session fixture=PracticeState.Session.start("러닝",30,System.currentTimeMillis()-120_000);
            fixture.pause(System.currentTimeMillis());require(store.save(fixture),"Session preference commit failed");
            require(store.load().id.equals(fixture.id)&&!store.load().running,"Real preference recovery failed");
            store.prefs.edit().putString("session","broken JSON").commit();
            try{store.load();throw new AssertionError("Damaged preference accepted");}catch(JSONException expected){}
            require(store.hasSession()&&store.prefs.getString("session","").equals("broken JSON"),"Damaged original was removed");
            store.clear();
            PracticeActivity a=(PracticeActivity)instrumentation.startActivitySync(new Intent(context,PracticeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            final Throwable[] failure={null};
            instrumentation.runOnMainSync(()->{
                ArrayList<JSONObject> originalEntries=new ArrayList<>(a.entries);boolean readable=a.readable;
                try{
                    int before=a.entries.size();PracticeState.Session normal=PracticeState.Session.start("걷기",10,System.currentTimeMillis()-125_000);
                    require(a.store.save(normal),"Activity state commit");a.session=normal;a.finishSession();
                    require(a.entries.size()==before+1&&PracticeState.recorded(a.entries,normal.id)&&!a.store.hasSession(),"Completion did not save and clear");
                    try(java.io.FileInputStream input=a.storage.openRead()){require(new JSONArray(a.read(input)).length()==before+1,"Atomic journal not written");}
                    // Recreate the exact crash window: record persisted but session clearing never ran.
                    PracticeState.Session interrupted=normal.copy();interrupted.anchorAt=System.currentTimeMillis()+5000;interrupted.seenAt=interrupted.anchorAt;
                    require(a.store.save(interrupted),"Crash window preference");a.session=interrupted;a.timerPage=true;a.render();
                    require(a.finishButton!=null&&a.finishButton.isEnabled()&&!a.pauseButton.isEnabled(),"Saved workout cleanup disabled after clock rollback");a.finishButton.performClick();
                    require(a.entries.size()==before+1&&!a.store.hasSession(),"Completion duplicated or clock blocked recovery");
                    PracticeState.Session failed=PracticeState.Session.start("근력",20,System.currentTimeMillis()-90_000);
                    require(a.store.save(failed),"Failure fixture preference");a.session=failed;a.readable=false;a.finishSession();
                    require(a.entries.size()==before+1&&a.store.hasSession()&&!a.store.load().running,"Save failure lost session");
                    a.readable=readable;PracticeState.Session shortRun=PracticeState.Session.start("스트레칭",10,System.currentTimeMillis()-5000);
                    require(a.store.save(shortRun),"Short fixture preference");a.session=shortRun;a.finishSession();
                    require(a.entries.size()==before+1&&a.store.hasSession()&&a.timerMessage.contains("1분"),"Short workout flow");
                }catch(Throwable e){failure[0]=e;}
                finally{a.readable=readable;if(!a.persist(originalEntries)&&failure[0]==null)failure[0]=new AssertionError("Restore test journal failed");a.store.clear();a.session=null;a.finish();}
            });
            if(failure[0]!=null)throw new AssertionError(failure[0]);
        }finally{SharedPreferences.Editor editor=store.prefs.edit();if(original==null)editor.remove("session");else editor.putString("session",original);editor.commit();}
    }
}
