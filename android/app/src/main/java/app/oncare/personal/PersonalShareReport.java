package app.oncare.personal;

import android.content.Context;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;

final class PersonalShareReport {
    static final String[] KINDS={"마음","체중","운동","수면","물","걸음"};
    static String version(Context context) {
        try { android.content.pm.PackageInfo p=context.getPackageManager().getPackageInfo(context.getPackageName(),0); return "온케어 "+p.versionName+" · 빌드 "+(android.os.Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode); }
        catch(Exception e) {return "온케어 · 버전 확인 불가";}
    }
    static String text(List<JSONObject> entries, Set<String> selected, int days, String name, long now) {
        days=days==30?30:7;
        Calendar c=Calendar.getInstance();c.setTimeInMillis(now);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);c.add(Calendar.DAY_OF_MONTH,1-days);long start=c.getTimeInMillis();
        SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.US);
        StringBuilder out=new StringBuilder("온케어 · 나의 건강 요약\n기간: "+f.format(new Date(start))+" ~ "+f.format(new Date(now)));
        name=name.trim().replace('\n',' ').replace('\r',' ');if(name.length()>40)name=name.substring(0,40);if(!name.isEmpty())out.append("\n이름: ").append(name);
        for(String kind:KINDS) if(selected.contains(kind)) {
            TreeMap<Long,JSONObject> records=new TreeMap<>(); double sum=0;TreeMap<String,JSONObject> daily=new TreeMap<>();
            ArrayList<JSONObject> sorted=new ArrayList<>(entries);sorted.sort(Comparator.comparingLong(e->e.optLong("date")));
            for(JSONObject e:sorted) {long date=e.optLong("date");double value=e.optDouble("value");if(!kind.equals(e.optString("kind"))||date<start||date>now||!Double.isFinite(value)||value<=0)continue; records.put(date,e);sum+=value;daily.put(f.format(new Date(date)),e);}
            if(records.isEmpty()) {out.append("\n").append(kind).append(": 기록 없음");continue;}
            if(kind.equals("체중")) {JSONObject last=records.lastEntry().getValue();out.append("\n체중 최근: ").append(number(last.optDouble("value"))).append(" kg (").append(f.format(new Date(last.optLong("date")))).append(")");}
            else if(Arrays.asList("운동","물","걸음").contains(kind)) {out.append("\n").append(kind).append(" 합계: ").append(number(sum)).append(" ").append(unit(kind)).append(" · ").append(daily.size()).append("일 기록");}
            else {double total=0;for(JSONObject e:daily.values())total+=e.optDouble("value");out.append("\n").append(kind).append(" 평균: ").append(number(total/daily.size())).append(" ").append(unit(kind)).append(" · ").append(daily.size()).append("일 기록 (날짜별 마지막 기록)");}
        }
        return out.append("\n개인 기록 요약입니다. 진단·치료 판단용 자료가 아닙니다.").toString();
    }
    static String number(double value){return String.format(Locale.KOREA,value==Math.rint(value)?"%.0f":"%.1f",value);}
    static String unit(String kind){switch(kind){case "마음":return "점";case "체중":return "kg";case "운동":return "분";case "수면":return "시간";case "걸음":return "걸음";default:return "ml";}}
}
