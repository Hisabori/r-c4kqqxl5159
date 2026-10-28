package app.oncare.personal;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.*;
import org.json.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
public final class StepsActivity extends GalaxyActivity {
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    SharedPreferences prefs;JSONArray rows=new JSONArray();boolean active=false,busy=false;int epoch=0;Future<?> pending;
    String status="Health Connect에서 최근 7일 걸음을 읽어요.";
    int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    TextView text(String label,int size){TextView t=new TextView(this);t.setText(label);t.setTextSize(size);t.setTextColor(foreground());t.setPadding(0,dp(7),0,dp(7));return t;}
    LinearLayout card(LinearLayout parent){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(20),dp(16),dp(20),dp(18));GradientDrawable shape=new GradientDrawable();shape.setColor(surface());shape.setCornerRadius(dp(28));c.setBackground(shape);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(8),0,dp(8));parent.addView(c,p);return c;}
    void button(LinearLayout c,String label,String id,Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextColor(0xFF3485D9);b.setMinHeight(dp(52));b.setContentDescription(id);b.setOnClickListener(v->action.run());c.addView(b);}
    @Override public void onCreate(Bundle state){super.onCreate(state);prefs=getSharedPreferences("step-pedometer",MODE_PRIVATE);try{String raw=prefs.getString("rows","[]");if(raw.length()>32768)throw new JSONException("size");rows=StepTotals.merge(new JSONArray(),new JSONArray(raw),LocalDate.now());}catch(Exception e){status="보관값을 읽지 못했어요. 새로고침해주세요.";}render();}
    void render(){
        ScrollView scroll=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(22),dp(40),dp(22),dp(28));body.setBackgroundColor(background());scroll.addView(body);setContentView(scroll);
        body.addView(text("나의 작은 발걸음",30));button(body,"돌아가기","steps-close",this::finish);
        TreeMap<String,Long> values;try{values=StepTotals.valid(rows,LocalDate.now());}catch(Exception e){values=new TreeMap<>();}
        int goal=StepTotals.goal(prefs.getInt("goal",8000));Long today=values.get(LocalDate.now().toString());
        LinearLayout hero=card(body);hero.addView(text("오늘의 걸음",18));TextView count=text(today==null?"—":String.format(Locale.KOREA,"%,d",today),44);count.setContentDescription("steps-today");hero.addView(count);
        hero.addView(text(today==null?"오늘 데이터가 아직 없어요. 0걸음으로 추정하지 않아요.":today>=goal?"오늘 정한 목표에 닿았어요. 편안하게 쉬어가도 좋아요.":"목표까지 "+String.format(Locale.KOREA,"%,d",Math.max(0,goal-today))+"걸음 남았어요.",15));
        try{Long change=StepTotals.difference(rows,LocalDate.now());hero.addView(text(change==null?"어제와 오늘 기록이 모두 있을 때 비교해요.":"오늘 현재까지 − 어제 하루: "+(change>0?"+":"")+change+"걸음",14));}catch(Exception ignored){}
        ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(goal);progress.setProgress((int)Math.min(goal,today==null?0:today));progress.setContentDescription("오늘 걸음 목표 진행률");hero.addView(progress);
        hero.addView(text("내 목표 "+goal+"걸음 · 현재까지 읽은 집계",13));
        LinearLayout target=card(body);target.addView(text("나에게 맞는 하루 목표",19));TextView targetText=text(goal+"걸음",18);target.addView(targetText);SeekBar slider=new SeekBar(this);slider.setMax(58);slider.setProgress((goal-1000)/500);slider.setContentDescription("steps-goal");target.addView(slider);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar v){}public void onProgressChanged(SeekBar v,int n,boolean user){targetText.setText((1000+n*500)+"걸음");}public void onStopTrackingTouch(SeekBar v){if(!prefs.edit().putInt("goal",1000+v.getProgress()*500).commit())Toast.makeText(StepsActivity.this,"목표를 저장하지 못했어요",Toast.LENGTH_SHORT).show();render();}});
        target.addView(text("개인 목표예요. 만보를 꼭 채울 필요는 없어요.",13));
        LinearLayout history=card(body);history.addView(text("최근 7일, 나의 걸음",20));long sum=0;int achieved=0;long max=1;for(long n:values.values()){sum+=n;if(n>=goal)achieved++;max=Math.max(max,n);}
        history.addView(text(values.isEmpty()?"읽은 기록이 없어요":values.size()+"일 기록 · 평균 "+sum/values.size()+"걸음 · 현재 목표 이상 "+achieved+"일",14));
        for(int n=6;n>=0;n--){String day=LocalDate.now().minusDays(n).toString();Long value=values.get(day);history.addView(text(day+"   "+(value==null?"데이터 없음":value+" 걸음"),14));if(value!=null){ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(1000);bar.setProgress((int)(value*1000/max));history.addView(bar);}}
        history.addView(text("없는 날짜는 평균에서 제외해요. 과거 달성 기록이 아니라 현재 목표와 비교한 값이에요.",13));
        LinearLayout connection=card(body);connection.addView(text("출처 · Health Connect 일별 집계",18));connection.addView(text(status,14));
        long refreshed=prefs.getLong("refreshed",0);if(refreshed>0)connection.addView(text("마지막 읽기: "+new java.text.SimpleDateFormat("M/d HH:mm",Locale.KOREA).format(new Date(refreshed)),13));
        if(busy)connection.addView(new ProgressBar(this));
        button(connection,busy?"확인 중…":"지금 걸음 새로고침","steps-refresh",this::refresh);
        button(connection,"건강 읽기 권한 · 연결 설정","steps-connect",()->startActivity(new Intent(this,HealthSyncActivity.class)));
        connection.addView(text("Android 14 이상에서 연결된 일별 집계를 읽어요. 원본 앱·휴대폰·워치의 중복 처리는 Health Connect 집계를 사용해요. 앱 센서 실시간 측정이 아닌 마지막 읽기 값입니다. 보관값은 이 화면용으로 따로 저장해요.",13));
        button(connection,"이 화면의 걸음 보관값 지우기","steps-clear",()->new AlertDialog.Builder(this).setMessage("이 화면의 보관값만 지울까요? 목표·건강 원본·기기 간 전송 기록은 유지됩니다.").setNegativeButton("취소",null).setPositiveButton("보관값 지우기",(d,w)->{epoch++;if(pending!=null)pending.cancel(true);busy=false;if(prefs.edit().remove("rows").remove("refreshed").commit()){rows=new JSONArray();status="보관값을 지웠어요.";}else status="지우지 못했어요. 기존 값은 유지돼요.";render();}).show());
    }
    void refresh(){
        if(!active||busy)return;
        if(Build.VERSION.SDK_INT<34){status="Health Connect 걸음 읽기는 Android 14 이상에서 지원해요.";render();return;}
        if(checkSelfPermission("android.permission.health.READ_STEPS")!=PackageManager.PERMISSION_GRANTED){status="연결 설정에서 걸음 읽기 권한을 허용해주세요.";render();return;}
        int request=++epoch;busy=true;status="일별 걸음 확인 중";render();
        pending=worker.submit(()->{try{JSONArray fresh=new HealthReader(this).dailySteps();runOnUiThread(()->{if(!active||request!=epoch)return;busy=false;try{if(fresh.length()==0){status="읽을 수 있는 걸음이 없어 기존 값을 유지했어요.";}else{JSONArray next=StepTotals.merge(rows,fresh,LocalDate.now());if(!prefs.edit().putString("rows",next.toString()).putLong("refreshed",System.currentTimeMillis()).commit())throw new JSONException("save");rows=next;status=fresh.length()+"일 집계를 읽었어요. 결과가 없는 날짜는 기존 값을 유지해요.";}}catch(Exception e){status="보관하지 못했어요. 기존 값은 유지했어요.";}render();});}catch(Exception e){runOnUiThread(()->{if(active&&request==epoch){busy=false;status="걸음을 읽지 못했어요. 연결 권한을 확인해주세요. 기존 값은 유지했어요.";render();}});}});
    }
    @Override protected void onResume(){super.onResume();active=true;refresh();}
    @Override protected void onStop(){active=false;epoch++;busy=false;if(pending!=null)pending.cancel(true);super.onStop();}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
