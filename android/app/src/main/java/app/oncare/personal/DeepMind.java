package app.oncare.personal;

import android.widget.*;
import org.json.JSONObject;
import java.util.*;

final class DeepMind {
    static final String[] PROMPTS={"상황과 감정","반복되는 생각","상담 준비","보내지 않을 편지"};
    static final String[] FOCUS={"자유롭게","불안","외로움","관계","무기력","스트레스"};
    static int promptIndex(int raw){return Math.min(PROMPTS.length-1,Math.max(0,raw));}
    static String starter(String raw){switch(focus(raw)){
        case "불안":return "직접 확인한 일과, 아직 모르는 부분을 따로 적어볼까?";
        case "외로움":return "어떤 순간에 누군가와 연결되고 싶었어?";
        case "관계":return "그때 바랐던 말과 지키고 싶은 선은 뭐였어?";
        case "무기력":return "오늘 마음이나 몸이 무거웠던 순간이 있었어?";
        case "스트레스":return "오늘 네 에너지를 가장 많이 쓴 일은 뭐였어?";
        default:return "지금 남기고 싶은 한 장면은 뭐야?";
    }}
    static String shortTemplate(String raw){String f=focus(raw);return "[짧게 남긴 마음]\n"+(f.equals(FOCUS[0])?"":"오늘 살펴볼 마음: "+f+"\n")+"\n지금 내 말로 표현하면:\n\n몸에서 느껴지는 것:\n\n지금 원하는 도움 또는 쉬고 싶은 것:\n";}
    static final String[] TEMPLATES={
        "무슨 일이 있었나요?\n\n느낀 감정과 강도(내 표현으로):\n\n몸의 느낌:\n\n지금 필요한 것과 작은 행동:\n",
        "마음에 남은 생각:\n\n직접 확인한 사실:\n\n아직 모르는 부분:\n\n다른 가능성과 나에게 건넬 말:\n",
        "최근 가장 불편했던 점과 시작 시기:\n\n생활·수면에 미친 영향:\n\n도움이 됐거나 어려웠던 것:\n\n상담에서 물어볼 것:\n",
        "이 편지를 보내지 않고 여기 남기는 이유:\n\n정말 하고 싶었던 말:\n\n내가 바랐던 것과 지키고 싶은 선:\n\n지금 나를 위해 할 작은 일:\n"
    };
    static String focus(String raw){return Arrays.asList(FOCUS).contains(raw)?raw:FOCUS[0];}
    static String template(int index,String raw){String f=focus(raw);return (f.equals(FOCUS[0])?"":"오늘 살펴볼 마음: "+f+"\n\n")+TEMPLATES[Math.max(0,Math.min(TEMPLATES.length-1,index))];}
    static final class Summary {int days,difficultDays;Double average;}
    static Summary summarize(List<JSONObject> entries,long now,boolean previous){
        Calendar cal=Calendar.getInstance();cal.setTimeInMillis(now);cal.set(Calendar.HOUR_OF_DAY,0);cal.set(Calendar.MINUTE,0);cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0);
        Calendar start=(Calendar)cal.clone();start.add(Calendar.DAY_OF_YEAR,previous?-13:-6);Calendar end=(Calendar)cal.clone();end.add(Calendar.DAY_OF_YEAR,-6);
        TreeMap<Long,JSONObject> latest=new TreeMap<>();
        for(JSONObject e:entries){long t=e.optLong("date",0);double value=e.optDouble("value",Double.NaN);if(!e.optString("kind").equals("마음")||!Double.isFinite(value)||value<1||value>5||t<start.getTimeInMillis()||(previous?t>=end.getTimeInMillis():t>now))continue;
            Calendar d=Calendar.getInstance();d.setTimeInMillis(t);d.set(Calendar.HOUR_OF_DAY,0);d.set(Calendar.MINUTE,0);d.set(Calendar.SECOND,0);d.set(Calendar.MILLISECOND,0);long key=d.getTimeInMillis();JSONObject old=latest.get(key);if(old==null||t>old.optLong("date")||(t==old.optLong("date")&&e.optString("id").compareTo(old.optString("id"))<0))latest.put(key,e);
        }
        Summary s=new Summary();double sum=0;for(JSONObject e:latest.values()){s.days++;sum+=e.optDouble("value");if(e.optDouble("value")<=2)s.difficultDays++;}s.average=s.days==0?null:sum/s.days;return s;
    }
    static String notificationTitle(boolean mode,boolean explicit){return mode&&explicit?"시발 처먹어 ㅋㅋㅋㅋ":mode?"복용 시간 됐어. 일정 확인하자":"복용 일정을 확인할 시간";}
    static String notificationBody(boolean mode){return mode?"등록한 복용 일정이야. 이미 먹었는지 먼저 확인해. 먹었으면 추가로 먹지 마. 헷갈리면 약사에게 확인해.":"온케어에서 등록한 약과 복용 방법을 확인하세요.";}
    static void medication(CareActivity a){MedicationStore store=new MedicationStore(a);long now=System.currentTimeMillis();java.util.ArrayList<MedicationStore.Plan> today=MedicationTransfer.today(store,now);java.util.Set<String> checked=new java.util.HashSet<>();for(MedicationStore.Plan p:today)if(store.checked(p.id,now))checked.add(p.id);MedicationAgenda.Summary summary=MedicationAgenda.summary(today,checked,now);LinearLayout c=a.card(a.page);c.setContentDescription("deep-mind-medication-first");c.addView(a.tx("제일 먼저, 복용 여부부터 확인하자",19,a.TEAL,true));a.gap(c,10);c.addView(a.tx(!store.readable?"일정 파일을 확인해 줘. 기존 일정은 그대로 둘게.":summary.due>0?"아직 확인 안 했네. 시간 지난 일정 "+summary.due+"개야. 복용 여부부터 확인하자. 이미 먹었으면 추가로 먹지 마.":today.isEmpty()?"오늘 예정된 일정은 없어.":"오늘 "+checked.size()+" / "+today.size()+"개 확인했어.",14,a.INK,false));a.full(c,"투약 일정 확인",a.TEAL,false,"deep-mind-medication-open",()->a.startActivity(new android.content.Intent(a,MedicationActivity.class)));}
    static void add(CareActivity a){
        medication(a);
        LinearLayout card=a.card(a.page);DeepMood.render(a,card,a.deepMoodDraft);
    }
}
