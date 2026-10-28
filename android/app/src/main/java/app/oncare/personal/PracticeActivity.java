package app.oncare.personal;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.widget.*;
import org.json.*;
import java.util.*;

/** One UI styled practice center over the same atomic journal used by the rest of the app. */
public class PracticeActivity extends CareActivity {
    PracticeState store;
    PracticeState.Session session;
    String loadError="";
    String timerMessage="";
    boolean timerPage,visible;
    String selectedType=PracticeState.TYPES[0];
    int selectedGoal=20,analysisDays=7;
    TextView timerValue,timerStatus;
    Button pauseButton,finishButton;
    @Override public void onCreate(Bundle state){
        if(state==null){int value=getSharedPreferences("personal-care",MODE_PRIVATE).getInt("minutes",20);if(java.util.Arrays.asList(10,20,30,60).contains(value))selectedGoal=value;}
        if(state!=null){analysisDays=state.getInt("analysis-days",7)==30?30:7;selectedType=state.getString("selected-exercise",PracticeState.TYPES[0]);if(!Arrays.asList(PracticeState.TYPES).contains(selectedType))selectedType=PracticeState.TYPES[0];selectedGoal=state.getInt("selected-goal",20);}store=new PracticeState(this);
        try{session=store.load();}catch(Exception e){loadError="진행 중인 운동을 읽지 못했어요. 초기화하기 전까지 원본은 보관해요.";}
        timerPage=state!=null?state.getBoolean("timer_page",false):getIntent().getBooleanExtra("open-timer",false);
        super.onCreate(state);
    }
    @Override protected void onSaveInstanceState(Bundle state){state.putBoolean("timer_page",timerPage);state.putInt("analysis-days",analysisDays);state.putString("selected-exercise",selectedType);state.putInt("selected-goal",selectedGoal);super.onSaveInstanceState(state);}
    @Override protected void onStart(){super.onStart();if(Build.VERSION.SDK_INT>=34)LocalSyncServer.get(this).acquire();}
    @Override protected void onResume(){super.onResume();visible=true;refreshTimer();handler.postDelayed(timerTick,1000);}
    @Override protected void onPause(){visible=false;handler.removeCallbacks(timerTick);checkpoint();super.onPause();}
    @Override protected void onStop(){LocalSyncServer.get(this).release();super.onStop();}
    @Override protected void onDestroy(){handler.removeCallbacks(timerTick);super.onDestroy();}
    @Override public void onBackPressed(){if(timerPage){timerPage=false;render();}else super.onBackPressed();}
    final Runnable timerTick=new Runnable(){@Override public void run(){if(!visible)return;refreshTimer();handler.postDelayed(this,1000);}};
    void checkpoint(){if(session==null||PracticeState.recorded(entries,session.id))return;try{PracticeState.Session next=session.copy();next.checkpoint(System.currentTimeMillis());if(store.save(next))session=next;else timerMessage="시간을 보관하지 못했어요. 저장 공간을 확인해주세요.";}catch(Exception e){timerMessage="기기 시각이 바뀌었어요. 시각을 바로잡거나 운동을 초기화해주세요.";}}
    boolean update(PracticeState.Session next){if(!store.save(next)){timerMessage="진행 상태를 저장하지 못했어요. 저장 공간을 확인해주세요.";if(timerStatus!=null)timerStatus.setText(timerMessage);return false;}session=next;timerMessage="";return true;}
    @Override void render(){
        stopBreathing();timerValue=null;timerStatus=null;pauseButton=null;finishButton=null;
        LinearLayout root=col();root.setBackgroundColor(BACK);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        page=col();page.setPadding(dp(22),dp(22),dp(22),dp(32));scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));setContentView(root);
        full(page,timerPage?"‹ 실천 센터로":"‹ 오늘로 돌아가기",BLUE,false,"practice-close",()->{if(timerPage){timerPage=false;render();}else finish();});
        if(timerPage)timerScreen();else centerScreen();
    }
    void centerScreen(){
        heading("오늘의 실천","작은 움직임부터, 나만의 속도로");
        LinearLayout timer=card(page);timer.addView(tx("지금, 몸을 움직일 시간",21,INK,true));gap(timer,10);
        timer.addView(tx(session==null?"걷기부터 스트레칭까지. 목표를 정하고\n쉬는 시간은 빼고 기록해요.":session.type+" · "+session.goal+"분 목표\n진행 중인 운동을 이어갈 수 있어요.",14,MUTED,false));gap(timer,18);
        full(timer,session==null&&!store.hasSession()?"운동 타이머 열기":"진행 중인 운동 확인",TEAL,true,"practice-open-timer",()->{timerPage=true;render();});
        routinesSection();comparisonSection();
        gap(page,12);page.addView(tx("기록과 실천은 이 기기에 보관돼요.\n"+PersonalShareReport.version(this),11,MUTED,false));
    }
    void timerScreen(){
        heading("운동 타이머","쉬는 시간을 빼고, 움직인 시간만");
        LinearLayout c=card(page);
        if(!loadError.isEmpty()){
            c.addView(tx(loadError,15,CORAL,true));gap(c,18);full(c,"운동 초기화",CORAL,false,"timer-reset",this::resetSession);return;
        }
        if(session==null){
            c.addView(tx("오늘은 어떤 운동을 할까요?",19,INK,true));gap(c,14);
            Spinner type=new Spinner(this);type.setContentDescription("timer-type");type.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,PracticeState.TYPES));type.setSelection(Arrays.asList(PracticeState.TYPES).indexOf(selectedType));c.addView(type,new LinearLayout.LayoutParams(-1,dp(56)));
            type.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,android.view.View v,int index,long id){selectedType=PracticeState.TYPES[index];}});
            gap(c,18);c.addView(tx("목표 시간",15,INK,true));gap(c,10);
            for(int i=0;i<2;i++){LinearLayout goals=row();for(int j=0;j<2;j++){int goal=PracticeState.GOALS[i*2+j];Button b=btn(goal+"분",TEAL,goal==selectedGoal,"timer-goal-"+goal,()->{selectedGoal=goal;render();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);p.setMargins(0,0,j==0?dp(8):0,dp(8));goals.addView(b,p);}c.addView(goals);}
            gap(c,14);full(c,"운동 시작",TEAL,true,"timer-start",()->{try{PracticeState.Session next=PracticeState.Session.start(selectedType,selectedGoal,System.currentTimeMillis());if(update(next))render();}catch(Exception e){toast("기기 시각과 목표를 확인해주세요.");}});
        }else{
            c.addView(tx(session.type+" · "+session.goal+"분 목표",17,TEAL,true));gap(c,24);
            timerValue=tx("",40,INK,true);timerValue.setContentDescription("timer-elapsed");c.addView(timerValue);gap(c,12);
            timerStatus=tx("",14,MUTED,false);timerStatus.setContentDescription("timer-status");c.addView(timerStatus);gap(c,22);
            pauseButton=btn(session.running?"일시정지":"계속하기",TEAL,false,"timer-toggle",()->{try{if(PracticeState.recorded(entries,session.id)){toast("이미 저장된 운동이에요. 완료 버튼으로 정리해주세요.");return;}PracticeState.Session next=session.copy();long now=System.currentTimeMillis();if(next.running)next.pause(now);else next.resume(now);if(update(next))render();}catch(Exception e){timerMessage="기기 시각 또는 최대 24시간 범위를 확인해주세요.";refreshTimer();}});
            c.addView(pauseButton,new LinearLayout.LayoutParams(-1,dp(50)));gap(c,10);
            finishButton=btn("완료하고 기록 저장",TEAL,true,"timer-finish",this::finishSession);c.addView(finishButton,new LinearLayout.LayoutParams(-1,dp(50)));gap(c,12);
            full(c,"운동 초기화",CORAL,false,"timer-reset",this::resetSession);refreshTimer();
        }
        gap(page,14);page.addView(tx("앱을 닫아도 시작 시각을 바탕으로 시간이 이어져요. 일시정지한 시간은 제외해요. 1분 이상 운동을 완료하면 기록에 저장돼요.\n\n목표 도달은 앱을 열었을 때 안내해요. 백그라운드 알람이나 센서 측정은 제공하지 않아요. 최대 24시간까지 기록할 수 있어요.",12,MUTED,false));
    }
    void refreshTimer(){
        if(session==null)return;
        try{
            JSONObject saved=PracticeState.savedEntry(entries,session.id);
            if(saved!=null){boolean workout="운동".equals(saved.optString("kind"));
                if(timerValue!=null)timerValue.setText(workout?fmt(saved.optDouble("value"))+"분 저장됨":"기록 확인 필요");
                if(timerStatus!=null)timerStatus.setText(timerMessage.isEmpty()?(workout?"이미 저장된 운동이에요. 완료 버튼으로 정리해주세요.":"같은 ID의 다른 기록이 있어요. 원본을 확인해주세요."):timerMessage);
                if(pauseButton!=null)pauseButton.setEnabled(false);if(finishButton!=null)finishButton.setEnabled(workout);return;
            }
            long now=System.currentTimeMillis(),duration=session.duration(now);
            session.observe(now);
            if(duration>=PracticeState.MAX_MILLIS&&session.running){PracticeState.Session next=session.copy();next.pause(now);update(next);}
            if(duration>=session.goal*60000L&&!session.goalNotified){PracticeState.Session next=session.copy();next.goalNotified=true;if(update(next)){toast("운동 목표에 도달했어요. 완료하면 기록에 저장돼요.");}}
            if(timerValue!=null)timerValue.setText(String.format(Locale.KOREA,"%02d:%02d",duration/60000,(duration/1000)%60));
            String status=session.limited?"최대 24시간에 도달해 일시정지했어요.":duration>=session.goal*60000L?"목표를 채웠어요 · 완료할 때 기록에 저장돼요":session.running?"운동 중 · 일시정지하면 쉬는 시간을 제외해요":"일시정지 · 쉬는 시간은 포함하지 않아요";
            if(timerStatus!=null)timerStatus.setText(timerMessage.isEmpty()?status:timerMessage);
            if(pauseButton!=null){pauseButton.setText(session.running?"일시정지":"계속하기");pauseButton.setEnabled(!session.limited);}
            if(finishButton!=null)finishButton.setEnabled(readable);
        }catch(Exception e){
            if(timerStatus!=null)timerStatus.setText("기기 시각이 뒤로 바뀌었어요. 시각을 바로잡거나 운동을 초기화해주세요.");
            if(timerValue!=null)timerValue.setText("시간 확인 필요");
            if(pauseButton!=null)pauseButton.setEnabled(false);if(finishButton!=null)finishButton.setEnabled(false);
        }
    }
    void finishSession(){
        if(session==null)return;
        try{
            // A persisted record wins even if the device clock changed before session cleanup.
            JSONObject saved=PracticeState.savedEntry(entries,session.id);
            if(saved!=null){
                if(!"운동".equals(saved.optString("kind"))){timerMessage="같은 ID의 다른 기록이 있어요. 원본을 확인해주세요.";refreshTimer();return;}
                clearCompleted(true);return;
            }
            // Freeze and persist first so a journal failure or process death keeps the session recoverable.
            long now=System.currentTimeMillis();PracticeState.Session finished=session.copy();finished.pause(now);
            if(!update(finished))return;
            if(session.duration(now)<60000){timerMessage="1분 이상 운동해야 기록할 수 있어요. 계속하기로 이어가거나 초기화해주세요.";refreshTimer();return;}
            ArrayList<JSONObject> next=new ArrayList<>(entries);next.add(session.record(now));
            if(!persist(next)){timerMessage="기록을 저장하지 못했어요. 진행 중인 운동은 유지돼요.";refreshTimer();return;}
            clearCompleted(false);
        }catch(Exception e){timerMessage="기기 시각과 운동 시간을 확인해주세요. 진행 중인 운동은 유지돼요.";refreshTimer();}
    }
    void clearCompleted(boolean alreadySaved){
        if(!store.clear()){timerMessage="운동 기록은 저장됐어요. 완료 버튼을 다시 누르면 진행 상태를 정리해요.";refreshTimer();return;}
        session=null;timerMessage="";timerPage=false;render();toast(alreadySaved?"저장된 운동의 진행 상태를 정리했어요":"운동을 기록했어요");
    }
    void resetSession(){new AlertDialog.Builder(this).setTitle("운동을 초기화할까요?").setMessage("진행 중인 시간은 지워져요. 이미 저장한 운동 기록은 유지돼요.").setNegativeButton("취소",null).setPositiveButton("초기화",(d,w)->{if(store.clear()){session=null;loadError="";timerMessage="";render();}else toast("진행 상태를 지우지 못했어요.");}).show();}
    void routinesSection(){
        section("최근 7일, 루틴 실천");SharedPreferences care=getSharedPreferences("care",MODE_PRIVATE);
        JSONArray routines;JSONObject marks;
        try{routines=new JSONArray(care.getString("routines","[]"));marks=new JSONObject(care.getString("checks","{}"));}catch(Exception e){LinearLayout c=card(page);c.addView(tx("루틴 정보를 읽지 못했어요. 오늘 화면에서 백업을 확인해주세요.",14,MUTED,false));return;}
        if(routines.length()==0){LinearLayout c=card(page);c.addView(tx("오늘 화면에서 내 루틴을 만들어보세요.",15,INK,true));gap(c,8);c.addView(tx("완료 날짜가 쌓이면 실천 흐름이 보여요.",12,MUTED,false));return;}
        long today=PracticeState.day(System.currentTimeMillis());
        for(int i=0;i<Math.min(8,routines.length());i++){
            JSONObject item=routines.optJSONObject(i);if(item==null)continue;PracticeState.RoutineProgress result=new PracticeState.RoutineProgress(marks,item.optString("id"),today);
            LinearLayout c=card(page);c.addView(tx(item.optString("title"),17,INK,true));gap(c,10);TextView summary=tx(result.count+" / 7일 완료 · 연속 "+result.streak+"일",14,TEAL,true);summary.setContentDescription("practice-routine-"+i);c.addView(summary);gap(c,16);
            // Two rows stay legible at large system font sizes.
            for(int row=0;row<2;row++){StringBuilder labels=new StringBuilder();int from=row==0?0:4,to=row==0?4:7;for(int j=from;j<to;j++){if(labels.length()>0)labels.append("    ");labels.append(formatDate(PracticeState.addDays(today,j-6),"M.d")).append(result.days[j]?" ✓":" ○");}c.addView(tx(labels.toString(),12,MUTED,false));gap(c,6);}
        }
        page.addView(tx("오늘 미완료라면 어제까지 이어진 연속 실천일을 보여줘요. 루틴 편집과 완료 체크는 오늘 화면에서 할 수 있어요.",11,MUTED,false));
    }
    void comparisonSection(){
        section("완료된 "+analysisDays+"일, 돌아보기");LinearLayout ranges=row();for(int days:new int[]{7,30})ranges.addView(btn(days+"일",TEAL,analysisDays==days,"insights-window-"+days,()->{analysisDays=days;render();}),new LinearLayout.LayoutParams(0,dp(50),1));page.addView(ranges);gap(page,12);long end=PracticeState.day(System.currentTimeMillis()),start=PracticeState.addDays(end,-analysisDays),prior=PracticeState.addDays(start,-analysisDays);
        PracticeState.Period current=new PracticeState.Period(entries,start,end),previous=new PracticeState.Period(entries,prior,start);
        page.addView(tx(formatDate(start,"M.d")+"–"+formatDate(PracticeState.addDays(end,-1),"M.d")+"  ·  이전 "+formatDate(prior,"M.d")+"–"+formatDate(PracticeState.addDays(start,-1),"M.d"),12,MUTED,false));gap(page,12);
        compare("운동 합계",current.exerciseDays==0?Double.NaN:current.exerciseMinutes,current.exerciseDays,previous.exerciseDays==0?Double.NaN:previous.exerciseMinutes,previous.exerciseDays,"분",TEAL,"practice-exercise-summary");
        compare("수면 평균",current.sleepMean,current.sleepDays,previous.sleepMean,previous.sleepDays,"시간",PURPLE,"practice-sleep-summary");
        compare("마음 평균",current.moodMean,current.moodDays,previous.moodMean,previous.moodDays,"점",PURPLE,"practice-mood-summary");
        full(page,"날짜별 기록 보기",TEAL,false,"insights-details",()->{LinearLayout details=col();for(int i=analysisDays-1;i>=0;i--){long day=PracticeState.addDays(start,i);PracticeState.Period p=new PracticeState.Period(entries,day,PracticeState.addDays(day,1));details.addView(tx(formatDate(day,"M.d EEEE"),16,INK,true));details.addView(tx("운동 "+(p.exerciseDays>0?fmt(p.exerciseMinutes)+"분":"기록 없음")+" · 수면 "+(Double.isFinite(p.sleepMean)?fmt(p.sleepMean)+"시간":"기록 없음")+" · 마음 "+(Double.isFinite(p.moodMean)?fmt(p.moodMean)+"점":"기록 없음"),13,MUTED,false));gap(details,14);}sheet("날짜별 기록",details).show();});gap(page,10);full(page,"분석 요약 공유",TEAL,false,"insights-share",()->{String text="완료된 "+analysisDays+"일 실천 요약\n"+formatDate(start,"yyyy.MM.dd")+" – "+formatDate(PracticeState.addDays(end,-1),"yyyy.MM.dd")+"\n운동 "+(current.exerciseDays>0?fmt(current.exerciseMinutes)+"분":"기록 없음")+" ("+current.exerciseDays+"일)\n수면 "+(Double.isFinite(current.sleepMean)?fmt(current.sleepMean)+"시간":"기록 없음")+" ("+current.sleepDays+"일)\n마음 "+(Double.isFinite(current.moodMean)?fmt(current.moodMean)+"점":"기록 없음")+" ("+current.moodDays+"일)\n기록한 날 기준이며 메모는 포함하지 않아요.";new AlertDialog.Builder(this).setTitle("공유 내용 확인").setMessage(text).setNegativeButton("취소",null).setPositiveButton("공유",(d,w)->{try{startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,text),"실천 요약 공유"));}catch(Exception e){toast("공유 앱을 확인해주세요.");}}).show();});gap(page,12);
        page.addView(tx("오늘은 비교에서 제외해요. 운동은 기간 합계, 수면·마음은 하루 마지막 기록의 평균이에요. 기록이 없는 날은 평균에서 제외해요.",11,MUTED,false));
    }
    void compare(String title,double value,int days,double previous,int previousDays,String unit,int color,String description){
        LinearLayout c=card(page);c.addView(tx(title,15,color,true));gap(c,12);TextView headline=tx(Double.isFinite(value)?fmt(value)+" "+unit:"기록 없음",28,INK,true);headline.setContentDescription(description);c.addView(headline);gap(c,8);
        c.addView(tx(days+" / "+analysisDays+"일 기록",12,MUTED,false));gap(c,12);c.addView(tx(Double.isFinite(previous)?"이전 "+analysisDays+"일 · "+fmt(previous)+" "+unit+" · "+previousDays+"일 기록":"이전 "+analysisDays+"일 · 기록 없음",12,MUTED,false));gap(c,6);
        c.addView(tx(Double.isFinite(value)&&Double.isFinite(previous)?"이전 "+analysisDays+"일보다 "+(value>previous?"+":"")+fmt(value-previous)+" "+unit:"두 기간에 기록이 쌓이면 변화를 비교해요.",12,MUTED,false));
    }
}
