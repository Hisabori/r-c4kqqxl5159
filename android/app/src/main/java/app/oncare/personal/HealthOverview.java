package app.oncare.personal;
import org.json.JSONObject;
import java.util.*;
final class HealthOverview {
    static final String[] KINDS={"물","운동","수면","마음"};
    static String focus(String raw){return Arrays.asList(KINDS).contains(raw)?raw:"물";}
    static final class Row {String kind;Double value;int days;Row(String k){kind=k;}}
    static long day(long now){Calendar c=Calendar.getInstance();c.setTimeInMillis(now);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c.getTimeInMillis();}
    static long snapshotDate(int raw,long now){int offset=Math.min(6,Math.max(0,raw));if(offset==0)return now;Calendar c=Calendar.getInstance();c.setTimeInMillis(day(now));c.add(Calendar.DAY_OF_YEAR,1-offset);return c.getTimeInMillis()-1;}
    static ArrayList<Row> rows(List<JSONObject> entries,String raw,long now){
        ArrayList<String> order=new ArrayList<>();order.add(focus(raw));for(String kind:KINDS)if(!kind.equals(focus(raw)))order.add(kind);
        long today=day(now);Calendar c=Calendar.getInstance();c.setTimeInMillis(today);c.add(Calendar.DAY_OF_YEAR,-6);long start=c.getTimeInMillis();
        ArrayList<Row> rows=new ArrayList<>();
        for(String kind:order){Row row=new Row(kind);Set<Long> days=new HashSet<>();long latest=-1;String latestID="";double sum=0;boolean found=false;
            for(JSONObject e:entries){double n=e.optDouble("value",Double.NaN);long date=e.optLong("date");double max=kind.equals("마음")?5:kind.equals("수면")?24:kind.equals("운동")?1440:20000;
                if(!kind.equals(e.optString("kind"))||!e.optString("source","").isEmpty()||e.optString("id").startsWith("hc:")||date<start||date<=0||date>now||!Double.isFinite(n)||n<=0||n>max||kind.equals("마음")&&n!=Math.rint(n))continue;
                days.add(day(date));if(date>=today){found=true;sum+=n;String id=e.optString("id");if(date>latest||date==latest&&id.compareTo(latestID)>0){latest=date;latestID=id;row.value=n;}}
            }
            if(found&&(kind.equals("물")||kind.equals("운동")))row.value=sum;row.days=days.size();rows.add(row);
        }
        return rows;
    }
    static void render(CareActivity a){
        android.content.SharedPreferences prefs=a.getSharedPreferences("health-overview",0);String selected=focus(prefs.getString("focus","물"));
        a.healthDayOffset=Math.min(6,Math.max(0,a.healthDayOffset));long snapshot=snapshotDate(a.healthDayOffset,System.currentTimeMillis());ArrayList<Row> rows=rows(a.entries,selected,snapshot);int count=0;for(Row r:rows)if(r.value!=null)count++;
        android.widget.LinearLayout card=a.card(a.page);card.setContentDescription("health-overview");card.addView(a.tx(a.healthDayOffset==0?"오늘, 나를 챙기는 기록":"그날의 나를 돌아보기",22,a.INK,true));
        android.widget.LinearLayout dates=a.row();card.addView(dates);
        android.widget.Button prev=a.btn("이전 날",a.BLUE,false,"health-day-prev",()->{a.healthDayOffset=Math.min(6,a.healthDayOffset+1);a.render();});prev.setEnabled(a.healthDayOffset<6);dates.addView(prev,new android.widget.LinearLayout.LayoutParams(0,-2,1));
        android.widget.TextView date=a.tx(a.healthDayOffset==0?"오늘":a.formatDate(snapshot,"M월 d일"),14,a.INK,true);date.setContentDescription("health-day-label");date.setGravity(android.view.Gravity.CENTER);dates.addView(date,new android.widget.LinearLayout.LayoutParams(0,-2,1));
        android.widget.Button nextDay=a.btn("다음 날",a.BLUE,false,"health-day-next",()->{a.healthDayOffset=Math.max(0,a.healthDayOffset-1);a.render();});nextDay.setEnabled(a.healthDayOffset>0);dates.addView(nextDay,new android.widget.LinearLayout.LayoutParams(0,-2,1));
        android.widget.TextView counter=a.tx("선택일 직접 기록한 항목 "+count+" / 4",15,a.INK,true);counter.setContentDescription("health-overview-count");card.addView(counter);card.addView(a.tx("건강 점수가 아닌 기록 현황이에요. 비어 있어도 괜찮아요.",13,a.MUTED,false));
        a.gap(card,12);android.widget.LinearLayout choices=a.row();card.addView(choices);
        for(String kind:KINDS){android.widget.Button b=a.btn(kind.equals("물")?"수분":kind,a.BLUE,selected.equals(kind),"health-overview-focus-"+kind,()->{if(prefs.edit().putString("focus",kind).commit())a.render();else a.toast("설정을 저장하지 못했어요");});choices.addView(b,new android.widget.LinearLayout.LayoutParams(0,-2,1));}
        String next=selected;boolean missing=false;
        for(Row row:rows){if(row.value==null&&!missing){next=row.kind;missing=true;}a.gap(card,10);
            String value=row.value==null?(a.healthDayOffset==0?"아직 기록 전":"기록 없음"):row.kind.equals("마음")?a.moods[Math.max(0,Math.min(4,row.value.intValue()-1))]:a.fmt(row.value)+" "+a.unit(row.kind);
            if(a.healthDayOffset==0)a.full(card,(row.kind.equals("물")?"수분":row.kind)+" · "+value,a.accent(row.kind),false,"health-overview-"+row.kind,()->a.record(row.kind));else card.addView(a.tx((row.kind.equals("물")?"수분":row.kind)+" · "+value,16,a.INK,true));
            card.addView(a.tx("선택일 포함 7일 · "+row.days+"일 직접 기록",12,a.MUTED,false));
        }
        final String target=next;a.gap(card,12);if(a.healthDayOffset!=0){card.addView(a.tx("지난 날짜는 보기 전용이에요. 새 기록은 오늘로 돌아간 뒤 남겨주세요.",13,a.MUTED,false));a.full(card,"오늘로 돌아가기",a.BLUE,false,"health-day-today",()->{a.healthDayOffset=0;a.render();});}else a.full(card,"기록 열기 · "+(target.equals("물")?"수분":target),a.BLUE,false,"health-overview-next",()->a.record(target));
        a.full(card,"내 목표 조정하기",a.BLUE,false,"health-overview-goals",a::goalSheet);
        card.addView(a.tx("수분·운동은 선택일 합계, 수면·마음은 선택일의 최근 직접 기록이에요. 연동 원본과 만보기는 별도 화면에서 확인해요.",12,a.MUTED,false));
    }
}
