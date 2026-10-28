package app.oncare.personal;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Persisted timestamps, not sensor measurements or a background alarm. */
final class PracticeState {
    static final long MAX_MILLIS=24L*60*60*1000;
    static final String[] TYPES={"걷기","러닝","근력","스트레칭"};
    static final int[] GOALS={10,20,30,60};
    final SharedPreferences prefs;
    PracticeState(Context context){prefs=context.getSharedPreferences("practice",Context.MODE_PRIVATE);}
    boolean hasSession(){return prefs.contains("session");}
    Session load()throws JSONException {String raw=prefs.getString("session",null);return raw==null?null:Session.decode(new JSONObject(raw));}
    boolean save(Session session){try{return prefs.edit().putString("session",session.json().toString()).commit();}catch(JSONException ex){return false;}}
    boolean clear(){return prefs.edit().remove("session").commit();}

    static final class Session {
        final String id,type;
        final int goal;
        final long startedAt;
        long anchorAt,seenAt,accumulated;
        boolean running,goalNotified,limited;
        private Session(String id,String type,int goal,long start){this.id=id;this.type=type;this.goal=goal;startedAt=start;anchorAt=start;seenAt=start;running=true;}
        static Session start(String type,int goal,long now)throws JSONException {
            if(!Arrays.asList(TYPES).contains(type)||!validGoal(goal)||now<=0)throw new JSONException("invalid selection");
            return new Session(UUID.randomUUID().toString(),type,goal,now);
        }
        static boolean validGoal(int goal){for(int g:GOALS)if(g==goal)return true;return false;}
        static long number(JSONObject value,String key)throws JSONException {
            Object raw=value.get(key);if(!(raw instanceof Number))throw new JSONException("invalid number");
            double n=((Number)raw).doubleValue();long result=((Number)raw).longValue();
            if(!Double.isFinite(n)||n!=result)throw new JSONException("invalid number");return result;
        }
        static boolean flag(JSONObject value,String key)throws JSONException {Object raw=value.get(key);if(!(raw instanceof Boolean))throw new JSONException("invalid flag");return (Boolean)raw;}
        static Session decode(JSONObject value)throws JSONException {
            if(number(value,"version")!=1)throw new JSONException("version");
            String id=value.getString("id"),type=value.getString("type");
            try{if(!UUID.fromString(id).toString().equals(id))throw new IllegalArgumentException();}catch(IllegalArgumentException e){throw new JSONException("id");}
            long g=number(value,"goal");if(g>Integer.MAX_VALUE||g<Integer.MIN_VALUE||!validGoal((int)g)||!Arrays.asList(TYPES).contains(type))throw new JSONException("selection");
            Session s=new Session(id,type,(int)g,number(value,"startedAt"));
            s.anchorAt=number(value,"anchorAt");s.seenAt=number(value,"seenAt");s.accumulated=number(value,"accumulated");
            s.running=flag(value,"running");s.goalNotified=flag(value,"goalNotified");s.limited=flag(value,"limited");
            if(s.startedAt<=0||s.anchorAt<s.startedAt||s.seenAt<s.anchorAt||s.accumulated<0||s.accumulated>MAX_MILLIS||s.accumulated>s.anchorAt-s.startedAt||s.running&&s.accumulated==MAX_MILLIS||s.limited&&(s.running||s.accumulated!=MAX_MILLIS))throw new JSONException("timestamps");
            return s;
        }
        JSONObject json()throws JSONException {return new JSONObject().put("version",1).put("id",id).put("type",type).put("goal",goal).put("startedAt",startedAt).put("anchorAt",anchorAt).put("seenAt",seenAt).put("accumulated",accumulated).put("running",running).put("goalNotified",goalNotified).put("limited",limited);}
        Session copy()throws JSONException {return decode(json());}
        long duration(long now){
            if(now<=0||now<startedAt||now<anchorAt||now<seenAt)throw new IllegalStateException("clock moved backwards");
            long remaining=MAX_MILLIS-accumulated;
            return accumulated+(running?Math.min(remaining,now-anchorAt):0);
        }
        void observe(long now){duration(now);seenAt=now;}
        void checkpoint(long now){accumulated=duration(now);anchorAt=now;seenAt=now;if(accumulated==MAX_MILLIS){running=false;limited=true;}}
        void pause(long now){checkpoint(now);running=false;}
        void resume(long now){checkpoint(now);if(accumulated>=MAX_MILLIS)throw new IllegalStateException("duration limit");running=true;}
        JSONObject record(long now)throws JSONException {
            long elapsed=duration(now);if(elapsed<60000)throw new IllegalStateException("minimum one minute");
            return new JSONObject().put("id",id).put("date",now).put("kind","운동").put("value",elapsed/60000.0).put("type",type).put("note","").put("sets",0).put("reps",0);
        }
    }

    static long day(long time){Calendar c=Calendar.getInstance();c.setTimeInMillis(time);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c.getTimeInMillis();}
    static long addDays(long time,int amount){Calendar c=Calendar.getInstance();c.setTimeInMillis(time);c.add(Calendar.DAY_OF_YEAR,amount);return c.getTimeInMillis();}
    static String dayKey(long time){return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date(time));}
    static boolean checked(JSONObject marks,String id,long time){JSONObject date=marks.optJSONObject(dayKey(time));return date!=null&&Boolean.TRUE.equals(date.opt(id));}
    static final class RoutineProgress {
        final boolean[] days=new boolean[7];int count,streak;
        RoutineProgress(JSONObject marks,String id,long now){long today=day(now);for(int i=0;i<7;i++){days[i]=checked(marks,id,addDays(today,i-6));if(days[i])count++;}
            long cursor=checked(marks,id,today)?today:addDays(today,-1);
            // Bounds also defend against damaged preference data.
            for(int i=0;i<3660&&checked(marks,id,cursor);i++){streak++;cursor=addDays(cursor,-1);}
        }
    }
    static final class Period {
        final long from,to;
        int exerciseDays,sleepDays,moodDays;
        double exerciseMinutes,sleepMean=Double.NaN,moodMean=Double.NaN;
        Period(List<JSONObject> entries,long start,long end){from=start;to=end;
            Set<Long> exerciseDates=new HashSet<>();Map<Long,JSONObject> sleep=new HashMap<>(),mood=new HashMap<>();
            for(JSONObject e:entries){long date=e.optLong("date");double value=e.optDouble("value",Double.NaN);String kind=e.optString("kind");
                if(date<start||date>=end||!Double.isFinite(value)||value<=0)continue;
                long key=day(date);
                if(kind.equals("운동")&&value<=1440){exerciseMinutes+=value;exerciseDates.add(key);}
                else if(kind.equals("수면")&&value<=24)last(sleep,key,e);
                else if(kind.equals("마음")&&value<=5&&value==Math.rint(value))last(mood,key,e);
            }
            exerciseDays=exerciseDates.size();sleepDays=sleep.size();moodDays=mood.size();sleepMean=mean(sleep);moodMean=mean(mood);
        }
        static void last(Map<Long,JSONObject> values,long key,JSONObject entry){JSONObject previous=values.get(key);if(previous==null||(entry.optLong("date")>previous.optLong("date")||entry.optLong("date")==previous.optLong("date")&&entry.optString("id").compareTo(previous.optString("id"))>0))values.put(key,entry);}
        static double mean(Map<Long,JSONObject> values){if(values.isEmpty())return Double.NaN;double sum=0;for(JSONObject entry:values.values())sum+=entry.optDouble("value");return sum/values.size();}
    }
    static JSONObject savedEntry(List<JSONObject> entries,String id){for(JSONObject entry:entries)if(id.equals(entry.optString("id")))return entry;return null;}
    static boolean recorded(List<JSONObject> entries,String id){JSONObject entry=savedEntry(entries,id);return entry!=null&&"운동".equals(entry.optString("kind"));}
}
