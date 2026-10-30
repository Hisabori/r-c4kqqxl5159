package app.oncare.personal;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.InputFilter;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import org.json.*;

public class MedicationActivity extends CareActivity {
    MedicationStore medications;
    Runnable afterPermission;
    final ExecutorService documents=Executors.newSingleThreadExecutor();
    byte[] exporting;
    boolean documentBusy=false;
    Dialog pendingImport;
    com.google.mlkit.vision.text.TextRecognizer bagReader;
    String searchQuery=""; EditText searchField; LinearLayout todayResults,registeredResults;
    String lastDay=""; int todayFilter=0; long lastMinute=-1; ScrollView medicationScroll;
    final Runnable calendarRefresh=new Runnable(){public void run(){String day=MedicationStore.dayKey(System.currentTimeMillis());if(!day.equals(lastDay)||System.currentTimeMillis()/60000!=lastMinute){lastDay=day;lastMinute=System.currentTimeMillis()/60000;render();}handler.postDelayed(this,30000);}};
    @Override public void onCreate(Bundle state){medications=new MedicationStore(this);if(state!=null){searchQuery=state.getString("medication-query","");exporting=state.getByteArray("medication-export");todayFilter=Math.max(0,Math.min(2,state.getInt("medication-filter",0)));}super.onCreate(state);if(state==null){String shared=getIntent().getStringExtra("oncare-selected-medication-text");getIntent().removeExtra("oncare-selected-medication-text");if(MedicationBagText.importedText(shared)!=null)handler.post(()->{if(!isFinishing()&&!isDestroyed())textImport(shared);});}}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);if(exporting!=null)state.putByteArray("medication-export",exporting);state.putInt("medication-filter",todayFilter);state.putString("medication-query",searchQuery);}
    @Override protected void onResume(){super.onResume();medications=new MedicationStore(this);MedicationReceiver.schedule(this);lastDay=MedicationStore.dayKey(System.currentTimeMillis());render();}
    @Override protected void onStart(){super.onStart();handler.postDelayed(calendarRefresh,30000);}
    @Override protected void onStop(){handler.removeCallbacks(calendarRefresh);super.onStop();}
    @Override protected void onDestroy(){documents.shutdownNow();if(bagReader!=null)bagReader.close();super.onDestroy();}
    @Override void render(){
        if(medications==null)return; boolean restoreSearch=searchField!=null&&searchField.hasFocus();int cursor=restoreSearch?searchField.getSelectionStart():0;int previousY=medicationScroll==null?0:medicationScroll.getScrollY();
        LinearLayout root=col();root.setBackgroundColor(BACK);LinearLayout top=row();top.setPadding(dp(22),dp(12),dp(22),dp(8));top.addView(tx("투약 알림",21,INK,true),new LinearLayout.LayoutParams(0,-2,1));top.addView(btn("닫기",BLUE,false,"medication-close",this::finish),new LinearLayout.LayoutParams(dp(70),dp(44)));root.addView(top);
        ScrollView scroll=new ScrollView(this);medicationScroll=scroll;page=col();page.setPadding(dp(22),dp(16),dp(22),dp(24));scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);scroll.post(()->scroll.scrollTo(0,previousY));
        page.addView(tx("나를 챙기는 시간",32,INK,true));gap(page,8);page.addView(tx("처방받은 일정대로, 차분히 확인해요.",14,MUTED,false));gap(page,24);
        LinearLayout status=card(page);boolean allowed=MedicationReceiver.allowed(this),exact=MedicationReceiver.exact(this);
        status.addView(tx(!medications.readable?"일정 파일 확인 필요":!allowed?"알림 권한 꺼짐":exact?"알림 허용 · 정확한 알림 허용":"알림 허용 · 지연 가능",16,INK,true));gap(status,10);
        status.addView(tx("알림에는 약 이름을 표시하지 않아요. 정확한 알림 권한이 없거나 배터리·알림 설정에 따라 지연될 수 있어요.",12,MUTED,false));gap(status,12);
        full(status,"앱 알림 설정",BLUE,false,"medication-notification-settings",()->startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName())));
        if(Build.VERSION.SDK_INT>=31){gap(status,8);full(status,"정확한 알림 설정",BLUE,false,"medication-exact-settings",()->{try{startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));}catch(Exception e){toast("시스템 설정에서 알람 및 리마인더를 확인해주세요");}});}
        LinearLayout tools=row();tools.addView(btn("복용 확인 기록",BLUE,false,"medication-history-open",this::history),new LinearLayout.LayoutParams(0,dp(50),1));tools.addView(btn("투약 백업",TEAL,false,"medication-backup-open",this::backupSheet),new LinearLayout.LayoutParams(0,dp(50),1));page.addView(tools);
        full(page,"문자 복약 안내 가져오기",TEAL,false,"medication-text-open",()->textImport(""));
        full(page,"약봉지 사진에서 글자 읽기",TEAL,false,"medication-ocr-open",this::pickMedicationPhoto);
        searchField=field(page,"일정 검색","약 이름·복용 방법",searchQuery,false,"medication-search");searchField.setSingleLine(true);searchField.setFilters(new InputFilter[]{new InputFilter.LengthFilter(120)});
        searchField.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){searchQuery=s.toString();renderPlanResults(System.currentTimeMillis());}public void afterTextChanged(android.text.Editable e){}});
        if(restoreSearch){searchField.requestFocus();searchField.setSelection(Math.max(0,Math.min(cursor,searchField.length())));}
        section("오늘의 복용 일정");Calendar calendar=Calendar.getInstance();long now=calendar.getTimeInMillis();int weekday=calendar.get(Calendar.DAY_OF_WEEK);
        ArrayList<MedicationStore.Plan> today=MedicationTransfer.today(medications,now);
        int confirmed=0;today.sort((a,b)->{int checked=Boolean.compare(medications.checked(a.id,now),medications.checked(b.id,now));if(checked!=0)return checked;int time=Integer.compare(a.hour*60+a.minute,b.hour*60+b.minute);return time!=0?time:a.id.compareTo(b.id);});
            for(MedicationStore.Plan p:today)if(medications.checked(p.id,now))confirmed++;
        LinearLayout progress=card(page);progress.setContentDescription("medication-today-progress");progress.addView(tx("오늘의 복용 확인 · "+confirmed+" / "+today.size()+"개",22,INK,true));gap(progress,10);
        Set<String> checkedIDs=new HashSet<>();today.sort((a,b)->{int checked=Boolean.compare(medications.checked(a.id,now),medications.checked(b.id,now));if(checked!=0)return checked;int time=Integer.compare(a.hour*60+a.minute,b.hour*60+b.minute);return time!=0?time:a.id.compareTo(b.id);});
            for(MedicationStore.Plan p:today)if(medications.checked(p.id,now))checkedIDs.add(p.id);
        MedicationAgenda.Summary agenda=MedicationAgenda.summary(today,checkedIDs,now);
        String remaining=agenda.next!=null?"다음 예정 · "+agenda.next.time()+" · "+agenda.next.name:today.isEmpty()?"등록한 요일에 맞춰 일정이 표시돼요.":confirmed==today.size()?"오늘 일정의 복용 확인을 모두 남겼어요.":"오늘 앞으로 예정된 미확인 일정은 없어요.";
        progress.addView(tx(remaining,14,MUTED,false));if(agenda.due>0){gap(progress,8);progress.addView(tx("시간이 된 일정 · "+agenda.due+"개 확인 전",14,MUTED,false));}
        gap(progress,10);progress.addView(tx("요약은 검색·필터와 관계없이 오늘 전체 일정 기준이에요. 알림을 끈 일정도 포함합니다.",12,MUTED,false));
        gap(progress,10);progress.addView(tx("확인 기록 기준입니다. 미확인은 미복용을 뜻하지 않으며, 지난 시간의 약을 추가로 복용하라는 안내가 아닙니다.",12,MUTED,false));
        LinearLayout filterRow=row();for(int filter=0;filter<3;filter++){final int selected=filter;filterRow.addView(btn(new String[]{"전체","확인 전","확인 완료"}[filter],TEAL,todayFilter==filter,"medication-filter-"+filter,()->{todayFilter=selected;render();}),new LinearLayout.LayoutParams(0,dp(48),1));}page.addView(filterRow);
        todayResults=col();page.addView(todayResults);
        section("등록한 일정 · "+medications.plans.size()+" / 8");Button add=btn("복용 일정 추가",TEAL,true,"medication-add",()->edit(new MedicationStore.Plan()));add.setEnabled(medications.readable&&medications.plans.size()<8);page.addView(add,new LinearLayout.LayoutParams(-1,dp(52)));gap(page,14);
        registeredResults=col();page.addView(registeredResults);renderPlanResults(now);
        page.addView(tx("한 일정에 한 복용 시간을 등록해요. 하루 여러 번이면 각각 추가하세요. 복용 확인은 오늘의 기록이며 알림 시간은 그대로 유지합니다. 투약 백업으로 일정과 확인 기록을 별도로 옮길 수 있어요. 건강 백업·자동 동기화와는 별도입니다.",12,MUTED,false));
    }
    void renderPlanResults(long now){
        if(todayResults==null||registeredResults==null)return;
        LinearLayout original=page;todayResults.removeAllViews();registeredResults.removeAllViews();
        try{page=todayResults;ArrayList<MedicationStore.Plan> today=MedicationTransfer.today(medications,now);int visible=0;
            today.sort((a,b)->{int checked=Boolean.compare(medications.checked(a.id,now),medications.checked(b.id,now));if(checked!=0)return checked;int time=Integer.compare(a.hour*60+a.minute,b.hour*60+b.minute);return time!=0?time:a.id.compareTo(b.id);});
            for(MedicationStore.Plan p:today)if((todayFilter==0||(todayFilter==2)==medications.checked(p.id,now))&&MedicationAgenda.matches(p,searchQuery)){planCard(p,true,now);visible++;}
            if(visible==0){LinearLayout empty=card(page);empty.addView(tx(today.isEmpty()?"오늘 예정된 일정이 없어요":"이 조건에 맞는 일정이 없어요",14,MUTED,false));if(!searchQuery.isEmpty()||todayFilter!=0)full(empty,"검색·필터 초기화",TEAL,false,"medication-search-reset",()->{searchQuery="";todayFilter=0;render();});}
            page=registeredResults;int count=0;for(MedicationStore.Plan p:medications.plans)if(MedicationAgenda.matches(p,searchQuery)){planCard(p,false,now);count++;}
            if(count==0&&!medications.plans.isEmpty())page.addView(tx("등록한 일정에 검색 결과가 없어요.",14,MUTED,false));
        }finally{page=original;}
    }
    void planCard(MedicationStore.Plan plan,boolean today,long now){
        LinearLayout c=card(page);c.addView(tx(plan.time()+" · "+plan.weekdays(),22,TEAL,true));gap(c,8);if(today)c.addView(tx(CareUpgrade.medicationPhase(plan.hour,plan.minute,medications.checked(plan.id,now),now),14,TEAL,true));TextView name=tx(plan.name,18,INK,true);name.setContentDescription("medication-plan-name");c.addView(name);gap(c,8);c.addView(tx(today&&medications.checked(plan.id,now)?"복용 확인됨":plan.enabled?"알림 켜짐":"알림 꺼짐",12,MUTED,false));if(!plan.instructions.isEmpty()){gap(c,8);c.addView(tx(plan.instructions,14,MUTED,false));}gap(c,14);
        if(today)full(c,medications.checked(plan.id,now)?"복용 확인 취소":"오늘 복용 확인",TEAL,false,"medication-taken",()->{if(medications.toggle(plan.id,System.currentTimeMillis()))render();else toast("복용 확인을 저장하지 못했어요");});
        else{full(c,"편집",BLUE,false,"medication-edit",()->{try{edit(MedicationStore.Plan.parse(plan.json()));}catch(Exception e){toast("일정을 읽지 못했어요");}});gap(c,8);full(c,"삭제",CORAL,false,"medication-delete",()->new AlertDialog.Builder(this).setTitle("복용 일정을 삭제할까요?").setNegativeButton("취소",null).setPositiveButton("삭제",(d,w)->{if(medications.remove(plan.id)){MedicationReceiver.schedule(this);render();}else toast("삭제하지 못했어요");}).show());}
    }
    void edit(MedicationStore.Plan plan){
        LinearLayout form=col();form.addView(tx("약 용량과 복용 시간은 처방·약사 안내대로 입력하세요.",13,MUTED,false));EditText name=field(form,"약 이름","직접 입력",plan.name,false,"medication-name"),instructions=field(form,"복용 방법 (선택)","처방받은 방법",plan.instructions,false,"medication-instructions");name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});instructions.setFilters(new InputFilter[]{new InputFilter.LengthFilter(200)});
        gap(form,16);Button time=btn(plan.time(),BLUE,false,"medication-time",()->{});time.setOnClickListener(v->new TimePickerDialog(this,(picker,h,m)->{plan.hour=h;plan.minute=m;time.setText(plan.time());},plan.hour,plan.minute,true).show());form.addView(time,new LinearLayout.LayoutParams(-1,dp(50)));gap(form,16);
        form.addView(tx("반복 요일",14,INK,true));String[] labels={"","일요일","월요일","화요일","수요일","목요일","금요일","토요일"};for(int day:new int[]{2,3,4,5,6,7,1}){CheckBox box=new CheckBox(this);box.setText(labels[day]);box.setTextColor(INK);box.setChecked(plan.days.contains(day));box.setOnCheckedChangeListener((button,on)->{if(on)plan.days.add(day);else plan.days.remove(day);});form.addView(box);}
        Switch enabled=new Switch(this);enabled.setText("알림 켜기");enabled.setChecked(plan.enabled);enabled.setContentDescription("medication-enabled");form.addView(enabled);gap(form,10);TextView error=tx("",12,CORAL,false);form.addView(error);gap(form,12);Dialog dialog=sheet("복용 일정 등록",form);
        full(form,"일정 저장",TEAL,true,"medication-save",()->{
            plan.name=name.getText().toString().trim();plan.instructions=instructions.getText().toString().trim();plan.enabled=enabled.isChecked();try{plan.validate();}catch(Exception e){error.setText("약 이름과 반복 요일을 확인해주세요");return;}
            Runnable save=()->{if(medications.save(plan)){MedicationReceiver.schedule(this);dialog.dismiss();render();toast("복용 일정을 저장했어요");}else error.setText("일정을 저장하지 못했어요. 최대 8개까지 등록할 수 있어요.");};
            Runnable proceed=()->{if(plan.enabled&&!MedicationReceiver.allowed(this)){
                if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED){afterPermission=save;requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},9201);}
                else error.setText("앱 알림 설정에서 허용하거나 알림을 끄고 저장해주세요");
            }else save.run();};
            if(MedicationAgenda.overlaps(plan,medications.plans))new AlertDialog.Builder(this).setTitle("같은 약·시간의 일정이 있어요").setMessage("반복 요일이 겹치는 일정이 있어요. 처방과 기존 일정을 확인해주세요. 이름만 비교한 안내이며 성분·상호작용 판단은 아니에요.").setNegativeButton("돌아가서 확인",null).setPositiveButton("별도 일정으로 저장",(d,w)->proceed.run()).show();else proceed.run();
        });dialog.show();if(dialog.getWindow()!=null)dialog.getWindow().setLayout(-1,-2);
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==9201){Runnable pending=afterPermission;afterPermission=null;if(MedicationReceiver.allowed(this)&&pending!=null)pending.run();else toast("알림 권한이 필요해요. 알림을 끄고 일정만 저장할 수도 있어요.");}}
    int historyDays=7; boolean confirmedOnly=false;
    void history(){
        LinearLayout form=col();form.addView(tx("선택한 기간에 직접 남긴 복용 확인이에요. 확인이 없다고 미복용으로 판단하지 않아요. 약 이름은 현재 등록된 일정 기준입니다.",13,MUTED,false));
        Dialog historyDialog=sheet("복용 확인 기록",form);LinearLayout periods=row();for(int days:new int[]{7,30})periods.addView(btn(days+"일",TEAL,historyDays==days,"medication-history-"+days,()->{historyDays=days;historyDialog.dismiss();history();}),new LinearLayout.LayoutParams(0,dp(48),1));form.addView(periods);CheckBox only=new CheckBox(this);only.setText("확인 기록이 있는 날만");only.setChecked(confirmedOnly);only.setContentDescription("medication-history-confirmed-only");form.addView(only);only.setOnCheckedChangeListener((v,on)->{confirmedOnly=on;historyDialog.dismiss();history();});
        Calendar day=Calendar.getInstance();for(int i=0;i<historyDays;i++){long stamp=day.getTimeInMillis();boolean has=false;for(MedicationStore.Plan p:medications.plans)if(medications.checked(p.id,stamp)){has=true;break;}if(confirmedOnly&&!has){day.add(Calendar.DAY_OF_YEAR,-1);continue;}LinearLayout card=card(form);card.addView(tx(formatDate(stamp,"M월 d일 EEEE"),16,INK,true));int count=0;for(MedicationStore.Plan p:medications.plans)if(medications.checked(p.id,stamp)){gap(card,8);card.addView(tx("✓ "+p.name+" · "+p.time(),14,TEAL,false));count++;}if(count==0){gap(card,8);card.addView(tx("복용 확인 기록 없음",13,MUTED,false));}day.add(Calendar.DAY_OF_YEAR,-1);}historyDialog.show();
    }
    void backupSheet(){
        if(documentBusy){toast("파일을 처리 중이에요");return;}
        LinearLayout form=col();form.addView(tx("투약 일정과 보관된 확인 기록을 iPhone·Android 온케어 3.7 사이에서 같은 JSON 파일로 옮길 수 있어요.",14,INK,false));gap(form,12);form.addView(tx("약 이름과 복용 방법이 담긴 암호화되지 않은 파일입니다. 저장 위치를 직접 선택하세요. 가져오기는 기존 투약 데이터를 대체하며 알림은 모두 꺼집니다.",13,MUTED,false));gap(form,18);Dialog dialog=sheet("투약 백업",form);
        Button save=btn("투약 백업 저장",TEAL,true,"medication-export",()->{try{exporting=MedicationTransfer.encode(medications);dialog.dismiss();Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,"OnCare-Medications-"+MedicationStore.dayKey(System.currentTimeMillis())+".json");startActivityForResult(intent,9301);}catch(Exception e){toast("백업을 만들지 못했어요");}});save.setEnabled(medications.readable);form.addView(save,new LinearLayout.LayoutParams(-1,dp(50)));gap(form,12);
        full(form,"투약 백업 가져오기",BLUE,false,"medication-import",()->{dialog.dismiss();startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json"),9302);});dialog.show();
    }
    void previewBackup(MedicationTransfer.Snapshot snapshot){
        LinearLayout form=col();int count=0;Iterator<String> keys=snapshot.checks.keys();while(keys.hasNext()){JSONArray ids=snapshot.checks.optJSONArray(keys.next());if(ids!=null)count+=ids.length();}
        form.addView(tx("일정 "+snapshot.plans.size()+"개 · 확인 기록 "+count+"개",19,INK,true));gap(form,12);form.addView(tx("기존 투약 일정과 확인 기록을 대체합니다. 가져온 알림은 모두 꺼집니다. 먼저 현재 백업을 저장하세요. 손상된 기존 파일은 기기에 따로 보존합니다.",13,MUTED,false));for(MedicationStore.Plan p:snapshot.plans){gap(form,12);form.addView(tx(p.name+" · "+p.time()+" · "+p.weekdays(),14,INK,false));if(!p.instructions.isEmpty()){gap(form,6);form.addView(tx(p.instructions,12,MUTED,false));}}gap(form,18);Dialog dialog=sheet("가져오기 전 확인",form);pendingImport=dialog;
        full(form,"확인하고 대체하기",CORAL,true,"medication-import-confirm",()->{if(MedicationTransfer.replace(medications,snapshot)){MedicationReceiver.schedule(this);dialog.dismiss();render();toast("투약 백업을 가져왔어요. 알림은 모두 꺼져 있어요.");}else toast("복원하지 못했어요. 기존 일정과 원본 파일은 유지됩니다.");});gap(form,10);full(form,"취소",BLUE,false,"medication-import-cancel",dialog::dismiss);dialog.show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        if(request==9401){if(result==RESULT_OK&&data!=null&&data.getData()!=null)readMedicationPhoto(data.getData());return;}
        if(request!=9301&&request!=9302){super.onActivityResult(request,result,data);return;}
        if(result!=RESULT_OK||data==null||data.getData()==null){exporting=null;return;}
        Uri uri=data.getData();byte[] bytes=exporting;exporting=null;documentBusy=true;
        documents.execute(()->{try{
            if(request==9301){if(bytes==null)throw new IOException("no snapshot");try(OutputStream output=getContentResolver().openOutputStream(uri)){if(output==null)throw new IOException("no output");output.write(bytes);}runOnUiThread(()->{documentBusy=false;if(!isFinishing()&&!isDestroyed())toast("투약 백업을 저장했어요");});}
            else{MedicationTransfer.Snapshot snapshot;try(InputStream input=getContentResolver().openInputStream(uri)){if(input==null)throw new IOException("no input");snapshot=MedicationTransfer.decode(MedicationTransfer.read(input));}runOnUiThread(()->{documentBusy=false;if(!isFinishing()&&!isDestroyed())previewBackup(snapshot);});}
        }catch(Exception e){runOnUiThread(()->{documentBusy=false;if(!isFinishing()&&!isDestroyed())toast("파일을 처리하지 못했어요. 지원하는 투약 백업인지 확인해주세요. 기존 일정은 바뀌지 않았어요.");});}});
    }
    void textImport(String initial){
        if(!medications.readable||medications.plans.size()>=8){toast("일정 파일 상태와 등록 한도를 확인해주세요");return;}
        LinearLayout form=col();form.addView(tx("문자 앱에서 필요한 복약 안내를 복사한 뒤 아래 입력칸을 길게 눌러 붙여넣으세요. 문자함 전체를 읽지 않으며 원문은 투약 기록에 저장하지 않습니다. 연락처·인증번호 등은 지워주세요.",13,MUTED,false));
        EditText input=field(form,"문자 복약 안내","내용 붙여넣기",initial,false,"medication-text-input");input.setSingleLine(false);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);input.setMinLines(4);input.setMaxLines(8);input.setSaveEnabled(false);input.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(150)));TextView error=tx("최대 12,000자·100줄·한 줄 500자. 다음 화면에서 필요한 줄만 선택하고 수정해주세요.",12,MUTED,false);form.addView(error);Dialog dialog=sheet("문자 복약 안내 가져오기",form);
        full(form,"내용에서 사용할 줄 고르기",TEAL,true,"medication-text-review",()->{String value=MedicationBagText.importedText(input.getText().toString());if(value==null){error.setText("내용이 비어 있거나 입력 한도를 넘었어요. 필요한 안내만 남겨주세요.");return;}dialog.dismiss();reviewMedicationText(value);});dialog.show();
    }
    void pickMedicationPhoto(){if(documentBusy){toast("사진·파일을 처리 중이에요");return;}if(!medications.readable||medications.plans.size()>=8){toast("일정 파일 상태와 등록 한도를 확인해주세요");return;}try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"),9401);}catch(ActivityNotFoundException e){toast("사진 선택 앱을 찾지 못했어요");}}
    void readMedicationPhoto(Uri uri){documentBusy=true;toast("약봉지 글자를 읽고 있어요");documents.execute(()->{try{MedicationBagText.Photo photo=MedicationBagText.photo(this,uri);if(isDestroyed()){photo.bitmap.recycle();return;}if(bagReader==null)bagReader=com.google.mlkit.vision.text.TextRecognition.getClient(new com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions.Builder().build());bagReader.process(com.google.mlkit.vision.common.InputImage.fromBitmap(photo.bitmap,photo.rotation)).addOnCompleteListener(task->{photo.bitmap.recycle();runOnUiThread(()->{documentBusy=false;if(isFinishing()||isDestroyed())return;if(task.isSuccessful())reviewMedicationText(task.getResult().getText());else toast("글자를 읽지 못했어요. 선명한 사진을 다시 선택해주세요.");});});}catch(Exception e){runOnUiThread(()->{documentBusy=false;if(!isDestroyed()&&!isFinishing())toast("사진을 읽지 못했어요. 다른 사진을 선택해주세요.");});}});}
    void reviewMedicationText(String text){ArrayList<String> lines=MedicationBagText.lines(text);if(lines.isEmpty()){toast("읽을 수 있는 글자가 없어요");return;}LinearLayout form=col();form.addView(tx("원본과 비교해 사용할 줄을 직접 고르세요. 환자 이름 등 개인정보가 있는 줄은 선택하지 마세요. 사진과 인식 원문은 앱에 저장하지 않습니다.",13,MUTED,false));gap(form,14);ArrayList<String> choices=new ArrayList<>();choices.add("직접 입력 / 사용 안 함");choices.addAll(lines);Spinner name=new Spinner(this),instructions=new Spinner(this);name.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,choices));instructions.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,choices));name.setContentDescription("medication-ocr-name-line");instructions.setContentDescription("medication-ocr-instructions-line");form.addView(tx("약 이름으로 사용할 줄",15,INK,true));form.addView(name);gap(form,12);form.addView(tx("복용 방법으로 사용할 줄",15,INK,true));form.addView(instructions);gap(form,14);form.addView(tx("인식 오류가 있을 수 있어요. 용량·시간·요일은 자동으로 정하지 않으며 알림은 꺼진 상태로 입력 화면을 엽니다. 원본을 확인한 뒤 직접 저장하세요.",12,MUTED,false));gap(form,14);EditText namePreview=field(form,"약 이름 미리보기","선택한 줄을 수정하세요","",false,"ocr-name-preview"),instructionsPreview=field(form,"복용 방법 미리보기","선택한 줄을 수정하세요","",false,"ocr-instructions-preview");for(String candidate:MedicationBagText.candidates(text,false))full(form,"약 이름 후보 · "+candidate,TEAL,false,"ocr-name-candidate",()->namePreview.setText(candidate));for(String candidate:MedicationBagText.candidates(text,true))full(form,"복용 안내에 추가 · "+candidate,BLUE,false,"ocr-instructions-candidate",()->{String updated=MedicationBagText.append(candidate,instructionsPreview.getText().toString());if(updated==null)toast("200자를 넘어서 기존 내용을 유지했어요");else instructionsPreview.setText(updated);});TextView length=tx("약 이름 최대 80자 · 복용 방법 최대 200자. 긴 문장은 직접 줄여주세요.",12,MUTED,false);form.addView(length);AdapterView.OnItemSelectedListener nameListener=new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,android.view.View v,int pos,long id){if(pos>0&&pos<choices.size())namePreview.setText(choices.get(pos));}};name.setOnItemSelectedListener(nameListener);instructions.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,android.view.View v,int pos,long id){if(pos>0&&pos<choices.size())instructionsPreview.setText(choices.get(pos));}});EditText search=field(form,"인식한 글자 검색","메모·약 이름 등 검색","",false,"ocr-line-search");search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void afterTextChanged(android.text.Editable e){}public void onTextChanged(CharSequence s,int start,int before,int count){String query=s.toString().trim().toLowerCase(Locale.KOREA);choices.clear();choices.add("직접 입력 / 사용 안 함");for(String line:lines)if(line.toLowerCase(Locale.KOREA).contains(query))choices.add(line);name.setAdapter(new ArrayAdapter<>(MedicationActivity.this,android.R.layout.simple_spinner_dropdown_item,choices));instructions.setAdapter(new ArrayAdapter<>(MedicationActivity.this,android.R.layout.simple_spinner_dropdown_item,choices));}});gap(form,14);Dialog dialog=sheet("복약 안내 내용 확인",form);full(form,"선택한 줄로 일정 입력",TEAL,true,"medication-ocr-apply",()->{MedicationStore.Plan draft=new MedicationStore.Plan();draft.enabled=false;String n=namePreview.getText().toString().trim(),i=instructionsPreview.getText().toString().trim();if(n.length()>80||i.length()>200){length.setText("입력 한도를 넘었어요. 원본과 비교해 필요한 내용을 줄여주세요.");return;}if(n.isEmpty()&&i.isEmpty()){length.setText("사용할 내용을 선택하거나 입력해주세요.");return;}draft.name=n;draft.instructions=i;dialog.dismiss();edit(draft);});dialog.show();}
}
