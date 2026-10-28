package app.oncare.personal;
import java.util.*;
import org.json.JSONObject;

final class CareUpgrade {
    static final class MoodContext {final int days;final Double latest;MoodContext(int d,Double v){days=d;latest=v;}}
    static MoodContext moodContext(List<JSONObject> entries,long now){
        Calendar start=Calendar.getInstance();start.setTimeInMillis(now);start.set(Calendar.HOUR_OF_DAY,0);start.set(Calendar.MINUTE,0);start.set(Calendar.SECOND,0);start.set(Calendar.MILLISECOND,0);start.add(Calendar.DAY_OF_MONTH,-6);
        Set<String> days=new HashSet<>();JSONObject latest=null;
        for(JSONObject e:entries){long date=e.optLong("date");double value=e.optDouble("value",Double.NaN);if(!"마음".equals(e.optString("kind"))||date<start.getTimeInMillis()||date>now||!Double.isFinite(value)||value<1||value>5)continue;
            days.add(MedicationStore.dayKey(date));if(latest==null||date>latest.optLong("date")||date==latest.optLong("date")&&e.optString("id").compareTo(latest.optString("id"))<0)latest=e;}
        return new MoodContext(days.size(),latest==null?null:latest.optDouble("value"));
    }
    static String medicationPhase(int hour,int minute,boolean checked,long now){if(checked)return "확인 완료";Calendar c=Calendar.getInstance();c.setTimeInMillis(now);return hour*60+minute<=c.get(Calendar.HOUR_OF_DAY)*60+c.get(Calendar.MINUTE)?"시간 지남 · 확인 전":"예정 · 확인 전";}
    static double coverage(int analyzed,double seconds){return analyzed<0||!Double.isFinite(seconds)||seconds<30?Double.NaN:Math.max(0,Math.min(1,analyzed*30.0/seconds));}
}
