package app.oncare.personal;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends GalaxyActivity {
    final String[] kinds = {"마음", "체중", "운동", "수면", "물"};
    final int teal = Color.rgb(16,133,127), ink = Color.rgb(28,46,49);
    final Handler handler = new Handler(Looper.getMainLooper());
    final ArrayList<JSONObject> entries = new ArrayList<>();
    LinearLayout page;
    int tab = 0, breathSeconds = 0;
    String filter = "전체";
    boolean readable = true, breathing = false;
    TextView breathLabel;
    Button breathButton;
    AtomicFile storage;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);tab=Math.max(0,Math.min(3,getSharedPreferences("app-preferences",MODE_PRIVATE).getInt("start-tab",0)));if(state!=null)tab=Math.max(0,Math.min(3,state.getInt("selected-tab",0)));
        storage = new AtomicFile(new File(getFilesDir(), "journal.json"));
        if (storage.getBaseFile().exists() || new File(getFilesDir(), "journal.json.bak").exists()) {
            try (FileInputStream input = storage.openRead()) {
                ByteArrayOutputStream data = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096]; int n;
                while ((n = input.read(buffer)) != -1) data.write(buffer, 0, n);
                JSONArray array = new JSONArray(data.toString("UTF-8"));
                for (int i = 0; i < array.length(); i++) entries.add(array.getJSONObject(i));
            } catch (Exception e) { readable = false; toast("기록을 읽지 못해 새 저장을 중단했습니다. 원본 파일은 보존됩니다."); }
        }
        sort(); render();
    }
    @Override protected void onSaveInstanceState(Bundle state){state.putInt("selected-tab",tab);super.onSaveInstanceState(state);}
    int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    String fmt(double n) { return n == Math.rint(n) ? String.format(Locale.KOREA, "%.0f", n) : String.format(Locale.KOREA, "%.1f", n); }
    String unit(String kind) { switch (kind) { case "마음": return "점"; case "체중": return "kg"; case "운동": return "분"; case "수면": return "시간"; case "걸음": return "걸음"; default: return "ml"; } }
    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    void sort() { entries.sort((a,b) -> Long.compare(b.optLong("date"), a.optLong("date"))); }
    boolean persist(ArrayList<JSONObject> next) {
        if (!readable) { toast("원본 기록을 읽지 못해 저장할 수 없습니다."); return false; }
        FileOutputStream output = null;
        try {
            JSONArray array = new JSONArray(); for (JSONObject e : next) array.put(e);
            output = storage.startWrite(); output.write(array.toString().getBytes(StandardCharsets.UTF_8));
            storage.finishWrite(output); entries.clear(); entries.addAll(next); sort(); OnCareWidget.updateAll(this); return true;
        } catch (Exception e) { if (output != null) storage.failWrite(output); toast("저장 실패: 저장 공간을 확인해주세요."); return false; }
    }
    TextView text(String s, int size, boolean bold) {
        TextView v = new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(ink);
        if (bold) v.setTypeface(null, Typeface.BOLD); v.setPadding(0,dp(8),0,dp(8)); return v;
    }
    GradientDrawable bg(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    Button button(String label, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setTextColor(Color.WHITE); b.setTextSize(16); b.setAllCaps(false);
        b.setBackground(bg(teal,14)); b.setMinHeight(dp(50)); b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,dp(5),0,dp(8)); b.setLayoutParams(p); return b;
    }
    void render() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(246,249,248));
        root.setPadding(dp(12),0,dp(12),0);
        TextView title = text("온케어  ·  " + new String[]{"오늘", "마음", "건강", "기록"}[tab],22,true); root.addView(title);
        ScrollView scroll = new ScrollView(this); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(10),dp(6),dp(10),dp(20)); scroll.addView(page);
        LinearLayout nav = new LinearLayout(this);
        String[] tabs = {"오늘", "마음", "건강", "기록"};
        for (int i=0;i<tabs.length;i++) { final int t=i; Button b=button(tabs[i],()->{stopBreathing();tab=t;render();}); b.setTextSize(14); b.setBackground(bg(t==tab?teal:Color.rgb(91,110,110),12)); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(52),1);p.setMargins(dp(2),dp(5),dp(2),dp(10));nav.addView(b,p); }
        root.addView(nav); setContentView(root);
        if(tab==0) dashboard(); else if(tab==1) mind(); else if(tab==2) health(); else history();
    }
    boolean today(long date) { SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.KOREA);return f.format(new Date(date)).equals(f.format(new Date())); }
    double total(String kind) { double sum=0;for(JSONObject e:entries)if(kind.equals(e.optString("kind"))&&today(e.optLong("date")))sum+=e.optDouble("value");return sum; }
    JSONObject latest(String kind, boolean onlyToday) { for(JSONObject e:entries)if(kind.equals(e.optString("kind"))&&(!onlyToday||today(e.optLong("date"))))return e;return null; }
    void dashboard() {
        page.addView(text("나를 돌보는\n작은 습관, 온케어",30,true));
        page.addView(text("지금 마음은 어떤가요? 하루를 잠깐 돌아보고 기록해보세요.",16,false));
        page.addView(button("오늘의 마음 기록",()->record("마음")));
        for(String k:Arrays.copyOfRange(kinds,1,5)) {
            JSONObject e=latest(k,k.equals("수면"));
            String v=k.equals("물")||k.equals("운동")?fmt(total(k))+" "+unit(k):e==null?"기록 전":fmt(e.optDouble("value"))+" "+unit(k);
            page.addView(button(k+"   "+v+"   +",()->record(k)));
        }
        page.addView(text("기록은 이 기기에 저장돼요. 작은 변화부터 꾸준히 살펴보세요.",14,false));
    }
    void mind() {
        page.addView(text("내 마음에\n잠깐 귀 기울이기",30,true));
        page.addView(text("감정에는 정답이 없어요. 지금 느끼는 마음을 그대로 남겨보세요.",16,false));
        page.addView(button("오늘의 마음 기록",()->record("마음")));
        page.addView(text("1분 쉬어가기",24,true));
        page.addView(text("편안하게 앉아 리듬에 맞춰 쉬어보세요. 불편하면 언제든 멈춰도 돼요.",16,false));
        breathLabel=text("잠시 쉬어갈까요?",23,true);breathLabel.setGravity(Gravity.CENTER);page.addView(breathLabel);
        breathButton=button("호흡 시작",()->{if(breathing){stopBreathing();breathButton.setText("이어하기");}else{if(breathSeconds>=60)breathSeconds=0;breathing=true;breathButton.setText("멈추기");tick.run();}});page.addView(breathButton);
        page.addView(text("온케어는 개인 기록용 앱이며 진단이나 치료를 제공하지 않습니다.",14,false));
    }
    final Runnable tick=new Runnable(){public void run(){if(!breathing)return;if(breathSeconds>=60){stopBreathing();breathLabel.setText("잘 쉬었어요 · 60초 완료");breathButton.setText("다시 시작");return;}breathLabel.setText((breathSeconds%10<4?"천천히 들이쉬기":"편안하게 내쉬기")+"\n"+breathSeconds+" / 60초");breathSeconds++;handler.postDelayed(this,1000);}};
    void stopBreathing(){breathing=false;handler.removeCallbacks(tick);}
    @Override protected void onPause(){super.onPause();stopBreathing();if(breathButton!=null)breathButton.setText("이어하기");}
    @Override protected void onDestroy(){stopBreathing();super.onDestroy();}
    void health() {
        page.addView(text("몸을 돌보는 습관",28,true));
        for(String k:Arrays.copyOfRange(kinds,1,5))page.addView(button(k+" 기록하기",()->record(k)));
        page.addView(text("체중 변화 · 최근 10회",22,true));int count=0;
        for(JSONObject e:entries)if("체중".equals(e.optString("kind"))&&count++<10)page.addView(text(date(e.optLong("date"))+"   "+fmt(e.optDouble("value"))+" kg",16,false));
        if(count==0)page.addView(text("첫 체중을 기록하면 변화가 여기에 보여요.",16,false));
        page.addView(text("운동·물은 오늘 누적, 체중·수면은 시점별 기록입니다. 건강 데이터 자동 연동은 포함하지 않습니다.",14,false));
    }
    String date(long time){return new SimpleDateFormat("yyyy.MM.dd HH:mm",Locale.KOREA).format(new Date(time));}
    void history() {
        Spinner picker=new Spinner(this);String[] choices={"전체","마음","체중","운동","수면","물"};picker.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,choices));picker.setSelection(Arrays.asList(choices).indexOf(filter));page.addView(picker);
        LinearLayout records=new LinearLayout(this);records.setOrientation(LinearLayout.VERTICAL);page.addView(records);
        picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int i,long id){filter=choices[i];records.removeAllViews();populate(records);}});
        page.addView(text("기록은 앱 내부 저장소에 보관되며 서버 전송·광고·분석 수집이 없습니다. 이 버전에서는 기기 자동 백업을 사용하지 않습니다. 앱 삭제 시 기록도 삭제됩니다.",14,false));
        page.addView(button("모든 기록 삭제",()->new AlertDialog.Builder(this).setTitle("모든 기록을 삭제할까요?").setMessage("삭제한 기록은 복구할 수 없습니다.").setNegativeButton("취소",null).setPositiveButton("모두 삭제",(d,w)->{if(persist(new ArrayList<>()))render();}).show()));
    }
    void populate(LinearLayout records){int count=0;for(JSONObject e:entries){String kind=e.optString("kind");if(!filter.equals("전체")&&!filter.equals(kind))continue;count++;LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(14),dp(8),dp(14),dp(8));card.setBackground(bg(Color.WHITE,16));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(8),0,dp(8));records.addView(card,p);card.addView(text(kind+"   "+fmt(e.optDouble("value"))+" "+unit(kind),20,true));card.addView(text(date(e.optLong("date")),14,false));if(!e.optString("note").isEmpty())card.addView(text(e.optString("note"),16,false));card.addView(button("이 기록 삭제",()->new AlertDialog.Builder(this).setMessage("이 기록을 삭제할까요?").setNegativeButton("취소",null).setPositiveButton("삭제",(d,w)->{ArrayList<JSONObject> next=new ArrayList<>(entries);next.remove(e);if(persist(next))render();}).show()));}if(count==0)records.addView(text("아직 기록이 없어요. 첫 기록을 남겨보세요.",16,false));}
    void record(String kind) {
        stopBreathing();
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(22),dp(10),dp(22),dp(10));
        EditText value=new EditText(this);Spinner mood=new Spinner(this);
        if(kind.equals("마음")){mood.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"1 · 많이 힘들어요","2 · 조금 힘들어요","3 · 보통이에요","4 · 좋아요","5 · 아주 좋아요"}));mood.setSelection(2);form.addView(mood);}else{value.setHint(kind+" ("+unit(kind)+")");value.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);form.addView(value);}
        Calendar chosen=Calendar.getInstance();Button dateButton=button(date(chosen.getTimeInMillis()),()->new DatePickerDialog(this,(view,y,m,d)->{chosen.set(y,m,d);new TimePickerDialog(this,(tv,h,min)->{chosen.set(Calendar.HOUR_OF_DAY,h);chosen.set(Calendar.MINUTE,min);},chosen.get(Calendar.HOUR_OF_DAY),chosen.get(Calendar.MINUTE),true).show();},chosen.get(Calendar.YEAR),chosen.get(Calendar.MONTH),chosen.get(Calendar.DAY_OF_MONTH)).show());
        dateButton.setText("날짜·시간 변경");form.addView(dateButton);form.addView(text("선택한 기록 시각으로 저장됩니다. 미래 시각은 저장할 수 없습니다.",13,false));
        EditText note=new EditText(this);note.setHint(kind.equals("운동")?"운동 종류 / 메모":"오늘의 메모");note.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);note.setMinLines(3);note.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2000)});form.addView(note);
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(kind+" 기록").setView(scroll).setNegativeButton("닫기",null).setPositiveButton("저장",null).create();dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{double n=kind.equals("마음")?mood.getSelectedItemPosition()+1:Double.parseDouble(value.getText().toString().replace(',','.'));double max=kind.equals("마음")?5:kind.equals("체중")?500:kind.equals("운동")?1440:kind.equals("수면")?24:20000;if(!Double.isFinite(n)||n<=0||n>max){toast("입력값을 확인해주세요.");return;}if(chosen.getTimeInMillis()>System.currentTimeMillis()){toast("미래 시각은 저장할 수 없습니다.");return;}JSONObject entry=new JSONObject();entry.put("id",UUID.randomUUID().toString());entry.put("date",chosen.getTimeInMillis());entry.put("kind",kind);entry.put("value",n);entry.put("note",note.getText().toString());ArrayList<JSONObject> next=new ArrayList<>(entries);next.add(entry);if(persist(next)){dialog.dismiss();render();}}catch(Exception e){toast("올바른 숫자를 입력해주세요.");}}));dialog.show();
    }
}
