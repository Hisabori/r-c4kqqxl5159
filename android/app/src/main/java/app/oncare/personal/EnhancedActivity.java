package app.oncare.personal;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** V3: personal routines, comparable weekly reports, search and complete backups. */
public class EnhancedActivity extends CareActivity {
    SharedPreferences care;
    JSONArray routines=new JSONArray();
    JSONObject checks=new JSONObject();
    int reportWeek=0, journalDays=0;
    String query="";
    String summaryDay="";
    final Runnable calendarSummary=new Runnable(){public void run(){String day=MedicationStore.dayKey(System.currentTimeMillis());if(!day.equals(summaryDay)){summaryDay=day;if(tab==0)render();}handler.postDelayed(this,30000);}};
    LinearLayout results;
    @Override public void onCreate(Bundle state) {
        moodDays=getSharedPreferences("app-preferences",MODE_PRIVATE).getInt("mood-days",7)==30?30:7;
        if(state!=null){moodQuery=state.getString("mood-query","");moodDays=state.getInt("mood-days",7)==30?30:7;query=state.getString("journal-query","");journalDays=state.getInt("journal-days",0);if(journalDays!=0&&journalDays!=7&&journalDays!=30)journalDays=0;selectedFilter=Math.max(0,Math.min(4,state.getInt("journal-filter",0)));range=state.getInt("health-range",30);if(range!=7&&range!=30&&range!=90)range=30;}care=getSharedPreferences("care",MODE_PRIVATE);
        try {routines=new JSONArray(care.getString("routines","[]"));checks=new JSONObject(care.getString("checks","{}"));}
        catch(JSONException e){toast("루틴 정보를 읽지 못했어요. 백업을 확인해주세요.");}
        super.onCreate(state);
        ReminderReceiver.schedule(this);
        consumeRoute(getIntent());
    }
    @Override protected void onSaveInstanceState(Bundle state){state.putString("mood-query",moodQuery);state.putInt("mood-days",moodDays);state.putString("journal-query",query);state.putInt("journal-days",journalDays);state.putInt("journal-filter",selectedFilter);state.putInt("health-range",range);super.onSaveInstanceState(state);}
    Dialog activeSheet;
    @Override Dialog sheet(String title,LinearLayout form){Dialog result=super.sheet(title,form);activeSheet=result;return result;}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);consumeRoute(intent);}
    void consumeRoute(Intent intent){String route=AppRoutes.read(intent);if(route==null)return;intent.removeExtra(AppRoutes.KEY);
        handler.post(()->{if(isFinishing()||isDestroyed())return;if(activeSheet!=null)activeSheet.dismiss();
            if(route.equals("exercise")){startActivity(new Intent(this,PracticeActivity.class).putExtra("open-timer",true));return;}
            if(route.equals("connect")){startActivity(new Intent(this,HealthSyncActivity.class));return;}
            tab=route.equals("sleep")?2:route.equals("mood")?1:0;render();if(route.equals("sleep"))startActivity(new Intent(this,SleepActivity.class));else if(route.equals("mood"))record("마음");
        });
    }
    @Override protected void onStart(){super.onStart();handler.postDelayed(calendarSummary,30000);if(Build.VERSION.SDK_INT>=34)LocalSyncServer.get(this).acquire();}
    @Override protected void onStop(){handler.removeCallbacks(calendarSummary);LocalSyncServer.get(this).release();super.onStop();}
    @Override protected void onResume(){
        super.onResume();boolean changed=false;
        try {
            if(storage.getBaseFile().exists()||new File(getFilesDir(),"journal.json.bak").exists()){
                JSONArray file;try(FileInputStream input=storage.openRead()){
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;
                    while((count=input.read(buffer))!=-1)bytes.write(buffer,0,count);
                    file=new JSONArray(bytes.toString("UTF-8"));
                }
                ArrayList<JSONObject> current=new ArrayList<>();for(int i=0;i<file.length();i++)current.add(file.getJSONObject(i));
                current.sort((a,b)->Long.compare(b.optLong("date"),a.optLong("date")));
                JSONArray old=new JSONArray(),next=new JSONArray();for(JSONObject e:entries)old.put(e);for(JSONObject e:current)next.put(e);
                if(!old.toString().equals(next.toString())){entries.clear();entries.addAll(current);changed=true;}
                readable=true;
            }
        }catch(Exception e){readable=false;toast("기록을 다시 읽지 못했어요. 원본은 보관돼요.");}
        try {
            JSONArray r=new JSONArray(care.getString("routines","[]"));JSONObject c=new JSONObject(care.getString("checks","{}"));
            if(!r.toString().equals(routines.toString())||!c.toString().equals(checks.toString())){routines=r;checks=c;changed=true;}
        }catch(Exception e){toast("루틴 정보를 다시 읽지 못했어요. 원본은 보관돼요.");}
        if(changed||tab==0)render();summaryDay=MedicationStore.dayKey(System.currentTimeMillis());OnCareWidget.updateAll(this);MedicationReceiver.schedule(this);
    }
    @Override void healthPage(){
        super.healthPage();full(page,"만보기 · 목표와 최근 7일",TEAL,false,"steps-open",()->startActivity(new Intent(this,StepsActivity.class)));gap(page,12);full(page,"수면 루틴 · 취침 계산과 분석",PURPLE,false,"sleep-tools-open",()->startActivity(new Intent(this,SleepActivity.class)));gap(page,12);full(page,"나의 7일 돌아보기",BLUE,false,"reflection-open",this::reflectionSheet);gap(page,12);
        full(page,"건강 보관함 · 항목별 데이터",BLUE,false,"health-records-open",()->startActivity(new Intent(this,HealthDataActivity.class)));gap(page,12);
        full(page,"읽을거리 · 건강 아티클",PURPLE,false,"articles-open",()->startActivity(new Intent(this,ArticleActivity.class)));gap(page,12);
        full(page,"투약 알림 · 복용 일정",TEAL,false,"medication-open",()->startActivity(new Intent(this,MedicationActivity.class)));gap(page,12);
        full(page,"실천 센터 · 운동 타이머",TEAL,false,"practice-open",()->startActivity(new Intent(this,PracticeActivity.class)));
    }
    String todayKey(){return formatDate(System.currentTimeMillis(),"yyyy-MM-dd");}
    boolean checked(String id,String date){return checks.optJSONObject(date)!=null&&checks.optJSONObject(date).optBoolean(id,false);}
    boolean saveRoutines(JSONArray next,JSONObject marks) {
        if(!care.edit().putString("routines",next.toString()).putString("checks",marks.toString()).commit()){toast("루틴을 저장하지 못했어요");return false;}
        routines=next;checks=marks;OnCareWidget.updateAll(this);return true;
    }
    @Override void home() {
        super.home();
        for(int i=0;i<4;i++)page.removeViewAt(page.getChildCount()-1);
        // Put the daily routine just below the blue summary, before metric cards.
        int first=page.getChildCount();
        section("내가 정한 오늘의 루틴");
        LinearLayout r=card(page);
        LinearLayout head=row();int count=0;
        for(int i=0;i<routines.length();i++)if(checked(routines.optJSONObject(i).optString("id"),todayKey()))count++;
        head.addView(tx(routines.length()==0?"나에게 맞는 작은 실천":"오늘 "+count+" / "+routines.length()+"개 완료",16,INK,true),new LinearLayout.LayoutParams(0,-2,1));
        Button manage=btn("편집",BLUE,false,"routine-manage",this::routineSheet);head.addView(manage,new LinearLayout.LayoutParams(dp(64),dp(44)));r.addView(head);
        if(routines.length()==0){gap(r,10);r.addView(tx("걷기, 스트레칭, 하루 한 줄 일기처럼\n내 생활에 맞는 습관을 골라보세요.",13,MUTED,false));}
        for(int i=0;i<routines.length();i++) {
            JSONObject item=routines.optJSONObject(i);String id=item.optString("id");
            CheckBox box=new CheckBox(this);box.setText(item.optString("title"));box.setTextSize(15);box.setTextColor(INK);box.setPadding(0,dp(10),0,dp(10));box.setButtonTintList(android.content.res.ColorStateList.valueOf(TEAL));box.setChecked(checked(id,todayKey()));box.setContentDescription("routine-check-"+i);
            box.setOnCheckedChangeListener((b,on)->{try{JSONObject copy=new JSONObject(checks.toString());JSONObject d=copy.optJSONObject(todayKey());if(d==null)d=new JSONObject();d.put(id,on);copy.put(todayKey(),d);if(saveRoutines(routines,copy))render();else {box.setOnCheckedChangeListener(null);box.setChecked(!on);}}catch(JSONException e){toast("저장 실패");}});r.addView(box);
        }
        // Move appended routine views together to preserve their layout order.
        ArrayList<View> moved=new ArrayList<>();while(page.getChildCount()>first){View v=page.getChildAt(first);page.removeViewAt(first);moved.add(v);}int index=Math.min(7,page.getChildCount());for(View v:moved)page.addView(v,index++);
        android.content.SharedPreferences personal=getSharedPreferences("personal-care",MODE_PRIVATE);String focus=personal.getString("focus","마음"),displayName=personal.getString("name","");LinearLayout personalCard=card(page);personalCard.addView(tx(displayName.isEmpty()?"나에게 맞춘 오늘":displayName+"님의 오늘",22,INK,true));personalCard.addView(tx("지금 챙기고 싶은 것 · "+focus,14,MUTED,false));personalCard.addView(tx(PersonalCare.message(focus),16,INK,false));full(personalCard,"맞춤 설정",BLUE,false,"personalize-open",()->PersonalCare.settings(this));full(personalCard,"지금 도움이 필요해요",PURPLE,false,"crisis-support-open",()->PersonalCare.support(this));
        if(!getSharedPreferences("app-preferences",MODE_PRIVATE).getBoolean("deep-mind",false)){medicationHomeCard();View medicationSummary=page.getChildAt(page.getChildCount()-1);page.removeView(medicationSummary);page.addView(medicationSummary,index);}
        full(page,"실천 센터 · 운동 타이머",TEAL,true,"practice-open",()->startActivity(new Intent(this,PracticeActivity.class)));gap(page,12);
        full(page,"Health Connect · iPhone 연결",BLUE,false,"health-sync-open",()->startActivity(new Intent(this,HealthSyncActivity.class)));
        
        section("기록이 쌓이면, 흐름이 보여요");LinearLayout report=card(page);
        report.addView(tx("나의 주간 리포트",19,INK,true));gap(report,8);report.addView(tx("운동·마음·수면과 루틴을 지난주와 비교해요.",13,MUTED,false));gap(report,16);full(report,"이번 주 돌아보기",BLUE,true,"open-report",this::reportSheet);gap(report,10);full(report,"하루 기록 알림 설정",BLUE,false,"open-reminder",this::reminderSheet);gap(report,10);full(report,"나의 7일 돌아보기",PURPLE,false,"reflection-home",this::reflectionSheet);articleHomeCard();
    }
    void articleHomeCard(){try(java.io.InputStream input=getAssets().open("health_articles.json")){JSONArray catalog=new JSONArray(new String(ArticleActivity.readBytes(input),StandardCharsets.UTF_8));SharedPreferences prefs=getSharedPreferences("health-articles",MODE_PRIVATE);Set<String> kinds=prefs.getBoolean("use-records",false)?ArticleRank.recent(entries,System.currentTimeMillis()):Collections.emptySet();JSONObject article=ArticleRank.sorted(catalog,prefs,kinds).get(0);section("나를 위한 읽을거리");LinearLayout c=card(page);c.addView(tx(article.optString("title"),21,INK,true));gap(c,8);c.addView(tx(ArticleRank.reason(article,prefs,kinds),13,MUTED,false));gap(c,14);full(c,"추천 아티클 보기",PURPLE,false,"article-featured",()->startActivity(new Intent(this,ArticleActivity.class)));}catch(Exception e){}}
    void medicationHomeCard(){
        MedicationStore store=new MedicationStore(this);long now=System.currentTimeMillis();ArrayList<MedicationStore.Plan> today=MedicationTransfer.today(store,now);int confirmed=0;for(MedicationStore.Plan p:today)if(store.checked(p.id,now))confirmed++;
        LinearLayout card=card(page);card.addView(tx("오늘의 복용 확인",17,TEAL,true));gap(card,10);
        TextView count=tx(!store.readable?"투약 파일 확인 필요":today.isEmpty()?"오늘 예정된 일정이 없어요":"오늘 "+confirmed+" / "+today.size()+"개 확인",20,INK,true);count.setContentDescription("medication-home-count");card.addView(count);gap(card,8);card.addView(tx("직접 남긴 확인 기록이에요. 확인 없음이 미복용을 뜻하지는 않아요.",12,MUTED,false));gap(card,14);full(card,"투약 알림 · 복용 일정",TEAL,false,"medication-open",()->startActivity(new Intent(this,MedicationActivity.class)));
    }
    void routineSheet() {
        LinearLayout form=col();form.addView(tx("완료 여부는 날짜별로 따로 보관해요. 최대 8개까지 추가할 수 있어요.",13,MUTED,false));
        for(int i=0;i<routines.length();i++) {
            final int pos=i;JSONObject item=routines.optJSONObject(i);LinearLayout line=row();gap(form,12);line.addView(tx(item.optString("title"),15,INK,true),new LinearLayout.LayoutParams(0,-2,1));
            Button del=btn("삭제",CORAL,false,"routine-delete-"+i,()->new AlertDialog.Builder(this).setMessage("이 루틴과 완료 이력을 삭제할까요?").setNegativeButton("취소",null).setPositiveButton("삭제",(d,w)->{try{JSONArray next=new JSONArray(routines.toString());String id=next.getJSONObject(pos).getString("id");next.remove(pos);JSONObject marks=new JSONObject(checks.toString());Iterator<String> days=marks.keys();while(days.hasNext())marks.getJSONObject(days.next()).remove(id);if(saveRoutines(next,marks)){render();if(activeRoutine!=null)activeRoutine.dismiss();routineSheet();}}catch(JSONException e){toast("저장 실패");}}).show());line.addView(del,new LinearLayout.LayoutParams(dp(64),dp(44)));form.addView(line);
        }
        EditText name=field(form,"새 루틴","예: 저녁 10분 산책","",false,"routine-name");name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});gap(form,10);
        LinearLayout picks=row();for(String s:new String[]{"10분 걷기","스트레칭","마음 일기"}){Button b=btn(s,TEAL,false,"routine-preset-"+s,()->name.setText(s));b.setTextSize(12);picks.addView(b,new LinearLayout.LayoutParams(0,dp(46),1));}form.addView(picks);gap(form,16);
        Dialog d=sheet("나의 루틴 만들기",form);activeRoutine=d;
        full(form,"루틴 추가하기",TEAL,true,"routine-add",()->{String title=name.getText().toString().trim();if(title.isEmpty()){name.setError("루틴 이름을 적어주세요");return;}if(routines.length()>=8){toast("루틴은 최대 8개까지 만들 수 있어요");return;}for(int i=0;i<routines.length();i++)if(title.equals(routines.optJSONObject(i).optString("title"))){name.setError("이미 있는 루틴이에요");return;}try{JSONArray next=new JSONArray(routines.toString());next.put(new JSONObject().put("id",UUID.randomUUID().toString()).put("title",title));if(saveRoutines(next,checks)){d.dismiss();render();toast("루틴을 추가했어요");}}catch(JSONException e){toast("저장 실패");}});d.show();
    }
    Dialog activeRoutine;
    long addDays(long t,int n){Calendar c=Calendar.getInstance();c.setTimeInMillis(t);c.add(Calendar.DAY_OF_YEAR,n);return c.getTimeInMillis();}
    double periodSum(String kind,long from,long to){double n=0;for(JSONObject e:entries)if(kind.equals(e.optString("kind"))&&e.optLong("date")>=from&&e.optLong("date")<to)n+=e.optDouble("value");return n;}
    TreeMap<Long,JSONObject> periodDays(String kind,long from,long to){TreeMap<Long,JSONObject> map=new TreeMap<>();for(JSONObject e:entries)if(kind.equals(e.optString("kind"))&&e.optLong("date")>=from&&e.optLong("date")<to)map.putIfAbsent(day(e.optLong("date")),e);return map;}
    double mean(TreeMap<Long,JSONObject> data){double n=0;for(JSONObject e:data.values())n+=e.optDouble("value");return data.isEmpty()?Double.NaN:n/data.size();}
    String change(double n,double previous,String suffix){if(!Double.isFinite(n)||!Double.isFinite(previous))return "비교할 기록이 아직 부족해요";double d=n-previous;return "지난주 같은 기간 대비 "+(d>0?"+":"")+fmt(d)+suffix;}
    void reportMetric(LinearLayout form,String label,String value,String comparison,int color){LinearLayout c=card(form);c.addView(tx(label,13,color,true));gap(c,10);c.addView(tx(value,26,INK,true));gap(c,8);c.addView(tx(comparison,12,MUTED,false));}
    void reportSheet() {
        LinearLayout form=col();long start=addDays(monday(),reportWeek*7);Calendar now=Calendar.getInstance();int length=reportWeek==0?(now.get(Calendar.DAY_OF_WEEK)+5)%7+1:7;length=Math.min(7,Math.max(1,length));long end=addDays(start,length),prior=addDays(start,-7),priorEnd=addDays(prior,length);
        form.addView(tx(formatDate(start,"M월 d일")+" – "+formatDate(addDays(end,-1),"M월 d일"),16,INK,true));gap(form,6);form.addView(tx(reportWeek==0?"이번 주와 지난주를 같은 요일 수로 비교해요.":"완료된 한 주와 직전 한 주를 비교해요.",12,MUTED,false));gap(form,16);
        LinearLayout periods=row();Dialog d=sheet("나의 주간 리포트",form);
        for(int i:new int[]{0,-1,-2}){final int w=i;Button b=btn(i==0?"이번 주":i==-1?"지난주":"2주 전",BLUE,reportWeek==i,"report-week-"+i,()->{reportWeek=w;d.dismiss();reportSheet();});periods.addView(b,new LinearLayout.LayoutParams(0,dp(44),1));}form.addView(periods);gap(form,16);
        Set<Long> days=new HashSet<>();for(JSONObject e:entries)if(e.optLong("date")>=start&&e.optLong("date")<end)days.add(day(e.optLong("date")));
        reportMetric(form,"나를 돌아본 날",days.size()+" / "+length+"일","기록을 한 번 이상 남긴 날짜 기준",BLUE);
        double exercise=periodSum("운동",start,end);reportMetric(form,"쌓인 움직임",fmt(exercise)+" 분",change(exercise,periodSum("운동",prior,priorEnd)," 분"),TEAL);
        TreeMap<Long,JSONObject> mood=periodDays("마음",start,end),sleep=periodDays("수면",start,end),weight=periodDays("체중",start,end);
        reportMetric(form,"마음 평균",mood.isEmpty()?"기록 전":fmt(mean(mood))+" / 5",change(mean(mood),mean(periodDays("마음",prior,priorEnd)),"점")+" · "+mood.size()+"일 기록",PURPLE);
        reportMetric(form,"수면 평균",sleep.isEmpty()?"기록 전":fmt(mean(sleep))+" 시간",change(mean(sleep),mean(periodDays("수면",prior,priorEnd)),"시간")+" · "+sleep.size()+"일 기록",PURPLE);
        if(weight.size()>1){double delta=weight.lastEntry().getValue().optDouble("value")-weight.firstEntry().getValue().optDouble("value");reportMetric(form,"체중 변화",(delta>0?"+":"")+fmt(delta)+" kg","선택 주의 첫 기록과 마지막 기록 비교",CORAL);}
        if(routines.length()>0){LinearLayout c=card(form);c.addView(tx("루틴 실천",17,INK,true));for(int i=0;i<routines.length();i++){JSONObject item=routines.optJSONObject(i);int n=0;for(int j=0;j<length;j++)if(checked(item.optString("id"),formatDate(addDays(start,j),"yyyy-MM-dd")))n++;gap(c,13);c.addView(tx(item.optString("title")+" · "+n+" / "+length+"일",14,INK,false));gap(c,8);c.addView(new ProgressView(n/(double)length,TEAL),new LinearLayout.LayoutParams(-1,dp(6)));}}
        form.addView(tx("마음과 수면은 하루 마지막 기록으로 계산해요. 기록이 없는 날은 평균에서 제외해요.",11,MUTED,false));d.show();
    }
    @Override void journalPage() {
        heading("나를 알아가는 기록","MY JOURNAL");
        EditText search=field(page,"기록 검색","메모, 운동 종류, 기록 종류 검색",query,false,"journal-search");search.setTextSize(15);gap(page,14);
        LinearLayout filters=row();String[] names={"전체","마음","체중","운동","생활"};for(int i=0;i<names.length;i++){final int f=i;Button b=btn(names[i],BLUE,selectedFilter==i,"filter-"+i,()->{selectedFilter=f;render();});b.setTextSize(15);b.setPadding(0,0,0,0);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);p.setMargins(0,0,dp(4),0);filters.addView(b,p);}page.addView(filters);gap(page,10);
        LinearLayout periods=row();periods.setPadding(dp(4),dp(4),dp(4),dp(4));periods.setBackground(bg(night()?0xFF2B2D32:0xFFE5E7EB,18));for(int n:new int[]{0,7,30}){Button b=btn(n==0?"모든 날짜":"최근 "+n+"일",BLUE,journalDays==n,"journal-days-"+n,()->{journalDays=n;render();});b.setTextSize(14);periods.addView(b,new LinearLayout.LayoutParams(0,dp(48),1));}page.addView(periods);gap(page,16);results=col();page.addView(results);fillResults();
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){query=s.toString();fillResults();}public void afterTextChanged(Editable e){}});
        section("내 기록은 내가 관리해요");LinearLayout tools=card(page);tools.addView(tx("기록 · 목표 · 루틴을 한 번에",16,INK,true));gap(tools,8);tools.addView(tx("백업은 건강 정보가 담긴 일반 JSON 파일이에요. 보관할 위치를 직접 선택해주세요.",12,MUTED,false));gap(tools,16);full(tools,"나의 정보 공유하기",BLUE,true,"personal-share-open",this::personalShareSheet);gap(tools,8);full(tools,"전체 백업하기",BLUE,false,"export-records",this::exportRecords);gap(tools,8);full(tools,"백업에서 가져오기",BLUE,false,"import-records",this::importRecords);gap(tools,8);full(tools,"하루 기록 알림 설정",BLUE,false,"open-reminder",this::reminderSheet);gap(tools,14);
        full(tools,"모든 기록 삭제",CORAL,false,"delete-all",()->new AlertDialog.Builder(this).setTitle("모든 기록을 삭제할까요?").setMessage("목표와 루틴은 유지돼요. 삭제한 기록은 백업으로만 복구할 수 있어요.").setNegativeButton("취소",null).setPositiveButton("삭제",(d,w)->{try{JSONObject old=HealthArchive.load(this);HealthArchive.save(this,new JSONObject());if(persist(new ArrayList<>()))render();else HealthArchive.save(this,old);}catch(Exception e){toast("삭제하지 못했어요. 건강 보관함을 확인해주세요.");}}).show());gap(page,12);page.addView(tx(PersonalShareReport.version(this)+" · 기기에만 보관\n진단이나 치료를 제공하지 않아요.",11,MUTED,false));
    }
    void reflectionSheet(){LinearLayout form=col();long end=PracticeState.addDays(PracticeState.day(System.currentTimeMillis()),1),start=PracticeState.addDays(end,-7);PracticeState.Period period=new PracticeState.Period(entries,start,end);form.addView(tx("기록으로 돌아보는 7일",24,INK,true));gap(form,14);form.addView(tx("운동 "+fmt(period.exerciseMinutes)+"분 · "+period.exerciseDays+"일 기록\n수면 "+(Double.isFinite(period.sleepMean)?fmt(period.sleepMean)+"시간":"기록 없음")+" · "+period.sleepDays+"일 기록\n마음 "+(Double.isFinite(period.moodMean)?fmt(period.moodMean)+"점":"기록 없음")+" · "+period.moodDays+"일 기록",17,INK,false));gap(form,16);form.addView(tx("내일의 작은 실천",18,INK,true));form.addView(tx(period.exerciseDays==0?"가능한 범위에서 짧게 움직이고 기록을 남겨보세요.":"내 생활에 맞는 움직임을 꾸준히 이어가보세요.",15,MUTED,false));form.addView(tx("기록 없는 날의 실제 상태는 알 수 없어요. 메모·이름을 분석하거나 외부로 보내지 않습니다. Android에서는 기록 통계와 고정된 문장으로 구성하며 AI 생성이 아닙니다.",12,MUTED,false));sheet("나의 7일 돌아보기",form).show();}
    void personalShareSheet() {
        LinearLayout form=col();form.addView(tx("보낼 항목만 골라요. 마음·체중은 처음에는 꺼져 있어요. 메모·투약 정보·연결 코드는 포함하지 않아요.",14,MUTED,false));gap(form,16);
        Spinner range=new Spinner(this);range.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"최근 7일","최근 30일"}));range.setContentDescription("share-range");form.addView(range);
        Set<String> selected=new HashSet<>(Arrays.asList("운동","수면","물","걸음"));
        for(String kind:PersonalShareReport.KINDS){CheckBox check=new CheckBox(this);check.setText(kind);check.setChecked(selected.contains(kind));check.setContentDescription("share-kind-"+kind);check.setOnCheckedChangeListener((b,on)->{if(on)selected.add(kind);else selected.remove(kind);});form.addView(check);}
        EditText name=field(form,"표시할 이름 (선택)","이 화면에서만 사용해요","",false,"share-name");name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(40)});gap(form,12);
        form.addView(tx("기록이 있는 날짜만 계산하며 없는 기록은 채우지 않아요.",12,MUTED,false));gap(form,16);
        full(form,"공유 내용 미리보기",BLUE,true,"share-preview",()->{
            if(selected.isEmpty()){toast("공유할 항목을 하나 이상 골라주세요");return;}
            String report=PersonalShareReport.text(new ArrayList<>(entries),new HashSet<>(selected),range.getSelectedItemPosition()==1?30:7,name.getText().toString(),System.currentTimeMillis());
            ScrollView scroll=new ScrollView(this);TextView text=tx(report+"\n\n선택한 앱과 수신자가 이 내용을 볼 수 있어요.",15,INK,false);text.setPadding(dp(22),dp(16),dp(22),dp(16));text.setTextIsSelectable(true);text.setContentDescription("share-preview-text");scroll.addView(text);
            new AlertDialog.Builder(this).setTitle("공유 내용 확인").setView(scroll).setNegativeButton("돌아가기",null).setPositiveButton("이 내용 공유하기",(d,w)->{try{startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,report),"나의 건강 요약 공유"));}catch(Exception ex){toast("공유할 앱을 열지 못했어요");}}).show();
        });sheet("나의 정보 공유하기",form).show();
    }
    void fillResults(){results.removeAllViews();int matched=0,shown=0;String last="",needle=query.trim().toLowerCase(Locale.KOREA);for(JSONObject e:entries){String k=e.optString("kind");boolean filter=selectedFilter==0||selectedFilter==1&&k.equals("마음")||selectedFilter==2&&k.equals("체중")||selectedFilter==3&&k.equals("운동")||selectedFilter==4&&(k.equals("수면")||k.equals("물")||k.equals("걸음"));String hay=k+" "+e.optString("note")+" "+e.optString("type")+" "+formatDate(e.optLong("date"),"yyyy-MM-dd");if(!filter||journalDays>0&&e.optLong("date")<ago(journalDays-1)||!hay.toLowerCase(Locale.KOREA).contains(needle))continue;matched++;if(shown++>=100)continue;String date=formatDate(e.optLong("date"),"M월 d일 EEEE");if(!date.equals(last)){gap(results,12);results.addView(tx(date,13,MUTED,true));gap(results,10);last=date;}entryCard(results,e);}TextView count=tx(matched+"개 기록"+(matched>100?" · 최근 100개 표시":""),12,MUTED,false);count.setContentDescription("search-result-count");results.addView(count,0);if(matched==0)empty(results,"일치하는 기록이 없어요","검색어나 날짜 범위를 바꿔보세요.");}
    void reminderSheet(){LinearLayout form=col();ReminderReceiver.channel(this);boolean allowed=((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).areNotificationsEnabled();form.addView(tx("하루 한 번, 마음과 실천을 돌아볼 시간을 정해요.",13,MUTED,false));gap(form,16);int[] time={care.getInt("hour",21),care.getInt("minute",0)};Button pick=btn(String.format(Locale.KOREA,"매일 %02d:%02d",time[0],time[1]),BLUE,false,"reminder-time",()->{});pick.setOnClickListener(v->new TimePickerDialog(this,(p,h,m)->{time[0]=h;time[1]=m;pick.setText(String.format(Locale.KOREA,"매일 %02d:%02d",h,m));},time[0],time[1],true).show());form.addView(pick,new LinearLayout.LayoutParams(-1,dp(50)));gap(form,14);Switch on=new Switch(this);on.setText("하루 기록 알림");on.setTextSize(16);on.setChecked(care.getBoolean("reminder",false));on.setContentDescription("reminder-toggle");form.addView(on);gap(form,12);form.addView(tx(allowed?"휴대폰의 절전 설정에 따라 알림이 늦게 도착할 수 있어요.":"휴대폰에서 알림 허용이 필요해요. 저장할 때 권한을 요청합니다.",12,MUTED,false));gap(form,12);if(!allowed){full(form,"휴대폰 알림 설정 열기",BLUE,false,"notification-settings",()->startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName())));gap(form,12);}Dialog d=sheet("하루 기록 알림",form);full(form,"알림 설정 저장",BLUE,true,"save-reminder",()->{if(!care.edit().putInt("hour",time[0]).putInt("minute",time[1]).putBoolean("reminder",on.isChecked()).commit()){toast("저장하지 못했어요");return;}d.dismiss();if(on.isChecked()&&Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},31);else{ReminderReceiver.schedule(this);toast(on.isChecked()?"알림 시간을 저장했어요":"알림을 껐어요");}});d.show();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==31){if(results.length==0||results[0]!=PackageManager.PERMISSION_GRANTED){care.edit().putBoolean("reminder",false).commit();toast("알림을 켜지 않았어요. 설정에서 다시 허용할 수 있어요.");}else toast("하루 기록 알림을 켰어요");ReminderReceiver.schedule(this);}}
    JSONObject backup()throws JSONException {JSONObject out=new JSONObject().put("version",3).put("exportedAt",System.currentTimeMillis());JSONArray records=new JSONArray();for(JSONObject e:entries)records.put(e);out.put("entries",records);JSONObject settings=new JSONObject();for(String k:new String[]{"weight","exercise","water","sleep"})settings.put(k,goal(k,k.equals("weight")?0:k.equals("exercise")?120:k.equals("water")?1500:7));out.put("goals",settings);out.put("routines",routines);out.put("checks",checks);try{JSONObject archive=HealthArchive.load(this);if(archive.has("healthRecords")){out.put("healthRecords",archive.getJSONArray("healthRecords"));out.put("version",4);}if(archive.has("healthAvailability")){out.put("healthAvailability",archive.getJSONArray("healthAvailability"));out.put("version",4);}}catch(Exception e){throw new JSONException("건강 보관함을 읽지 못해 백업을 중단했어요.");}return out;}
    @Override protected void onActivityResult(int request,int result,Intent data) {
        // CareActivity owns request codes 101/102; do not let the V2 importer also run.
        if(request!=101&&request!=102){super.onActivityResult(request,result,data);return;}if(result!=RESULT_OK||data==null||data.getData()==null)return;
        try{if(request==101){try(OutputStream stream=getContentResolver().openOutputStream(data.getData(),"wt")){if(stream==null)throw new IOException();stream.write(backup().toString(2).getBytes(StandardCharsets.UTF_8));}toast("기록과 목표, 루틴을 백업했어요");}
        else {String raw;try(InputStream stream=getContentResolver().openInputStream(data.getData())){if(stream==null)throw new IOException();raw=read(stream);}JSONObject file=raw.trim().startsWith("[")?new JSONObject().put("entries",new JSONArray(raw)):new JSONObject(raw);validateBackup(file);JSONArray a=file.getJSONArray("entries");new AlertDialog.Builder(this).setTitle("백업을 가져올까요?").setMessage(a.length()+"개 기록을 중복 없이 합쳐요."+(file.has("goals")?"\n목표와 루틴은 백업의 설정으로 바뀌어요.":"\n현재 목표와 루틴은 유지돼요.")+"\n알림 설정은 유지돼요.").setNegativeButton("취소",null).setPositiveButton("가져오기",(d,w)->restore(file)).show();}}
        catch(Exception e){toast("백업을 읽지 못했어요. 원래 기록은 그대로예요.");}
    }
    void validateBackup(JSONObject file)throws JSONException {
        if(file.has("version")&&(file.getInt("version")<1||file.getInt("version")>4))throw new JSONException("version");HealthArchive.validate(file);JSONArray a=file.getJSONArray("entries");if(a.length()>20000)throw new JSONException("size");Set<String> ids=new HashSet<>();for(int i=0;i<a.length();i++){JSONObject e=a.getJSONObject(i);String k=e.getString("kind");double n=e.getDouble("value");if((!Arrays.asList(kinds).contains(k)&&!k.equals("걸음"))||!Double.isFinite(n)||n<=0||n>maxValue(k)||(k.equals("마음")||k.equals("걸음"))&&n!=Math.rint(n)||e.getLong("date")<=0||e.getLong("date")>System.currentTimeMillis()||e.optString("note").length()>2000||e.optString("type").length()>100)throw new JSONException("record");int sets=e.optInt("sets"),reps=e.optInt("reps");if(sets<0||sets>100||reps<0||reps>1000||(sets==0)!=(reps==0))throw new JSONException("exercise");SleepTools.validate(e);if(!e.has("id"))e.put("id",UUID.randomUUID().toString());String id=e.getString("id");if(id.isEmpty()||id.length()>100||!ids.add(id))throw new JSONException("id");}
        if(file.has("goals")){JSONObject g=file.getJSONObject("goals");String[] keys={"weight","exercise","water","sleep"};double[] max={500,10080,20000,24};for(int i=0;i<4;i++){double n=g.getDouble(keys[i]);if(!Double.isFinite(n)||n>max[i]||(i==0?n<0:n<=0))throw new JSONException("goal");}}
        if(file.has("routines")!=file.has("checks"))throw new JSONException("routine pair");if(file.has("routines")){JSONArray r=file.getJSONArray("routines");JSONObject c=file.getJSONObject("checks");if(r.length()>8||c.length()>3660)throw new JSONException("routines");Set<String> known=new HashSet<>();for(int i=0;i<r.length();i++){JSONObject item=r.getJSONObject(i);String id=item.getString("id"),title=item.getString("title");if(id.isEmpty()||id.length()>100||!known.add(id)||title.trim().isEmpty()||title.length()>40)throw new JSONException("routine");}Iterator<String> dates=c.keys();java.text.SimpleDateFormat dateFormat=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US);dateFormat.setLenient(false);while(dates.hasNext()){String date=dates.next();try{if(!date.matches("\\d{4}-\\d{2}-\\d{2}")||dateFormat.parse(date).getTime()>ago(0))throw new JSONException("date");}catch(java.text.ParseException ex){throw new JSONException("date");}JSONObject marks=c.getJSONObject(date);Iterator<String> names=marks.keys();while(names.hasNext()){String id=names.next();if(!known.contains(id)||!(marks.get(id) instanceof Boolean))throw new JSONException("check");}}}
    }
    void restore(JSONObject file){try{validateBackup(file);JSONObject oldArchive=HealthArchive.load(this),nextArchive=HealthArchive.merge(oldArchive,file);ArrayList<JSONObject> before=new ArrayList<>(entries);ArrayList<JSONObject> next=new ArrayList<>(entries);Set<String> known=new HashSet<>();for(JSONObject e:entries)known.add(e.optString("id"));JSONArray a=file.getJSONArray("entries");int added=0;for(int i=0;i<a.length();i++){JSONObject e=a.getJSONObject(i);if(known.add(e.getString("id"))){next.add(e);added++;}}if(!persist(next))return;try{HealthArchive.save(this,nextArchive);}catch(Exception e){if(!persist(before))toast("건강 보관함 복원 실패 · 기본 기록의 원본 백업을 확인해주세요.");throw e;}
        boolean settingsOk=true;if(file.has("goals")){SharedPreferences.Editor edit=goals.edit();JSONObject g=file.getJSONObject("goals");for(String k:new String[]{"weight","exercise","water","sleep"})edit.putLong(k,Double.doubleToLongBits(g.getDouble(k)));settingsOk=edit.commit();}if(file.has("routines"))settingsOk=saveRoutines(file.getJSONArray("routines"),file.getJSONObject("checks"))&&settingsOk;render();toast(settingsOk?added+"개 기록과 설정을 가져왔어요":"기록은 가져왔지만 일부 설정 저장에 실패했어요");}catch(Exception e){toast("가져오기를 완료하지 못했어요");}}
}
