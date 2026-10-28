package app.oncare.personal;
import android.app.AlertDialog;
import android.content.*;
import android.widget.*;
import org.json.*;

final class CareUpgradeUI {
    static String prompt(int choice){return choice==1?"마음에 남은 일과 지금 필요한 한 가지를 적어봐요.":choice==2?"주변에서 편안한 색, 닿는 감촉, 들리는 소리를 하나씩 살펴봐요.":"익숙한 1분 호흡 화면으로 이동해요. 불편하면 언제든 멈추세요.";}
    static void mind(CareActivity a){
        LinearLayout card=a.card(a.page);card.setContentDescription("mind-recovery-card");card.addView(a.tx("나에게 맞는 작은 실천",19,a.PURPLE,true));a.gap(card,10);
        CareUpgrade.MoodContext context=CareUpgrade.moodContext(a.entries,System.currentTimeMillis());
        card.addView(a.tx(context.days==0?"아직 이번 주 마음 기록이 없어요. 점수와 관계없이 편한 방법을 골라요.":"최근 7일 중 "+context.days+"일 기록했어요. 기록하지 않은 날은 평가하지 않아요.",14,a.MUTED,false));
        if(context.latest!=null&&context.latest<=2){a.gap(card,8);card.addView(a.tx("최근 기록에서 힘든 마음을 남겼네요. 지금 할 수 있는 작은 한 가지만 골라도 괜찮아요.",13,a.MUTED,false));}
        SharedPreferences prefs=a.getSharedPreferences("app-preferences",Context.MODE_PRIVATE);int selected=Math.max(0,Math.min(2,prefs.getInt("recovery-style",0)));
        Spinner choices=new Spinner(a);choices.setContentDescription("recovery-preference");choices.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,new String[]{"쉼","쓰기","감각"}));choices.setSelection(selected);a.gap(card,12);card.addView(choices);
        TextView description=a.tx(prompt(selected),14,a.MUTED,false);a.gap(card,10);card.addView(description);
        choices.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,android.view.View v,int i,long id){prefs.edit().putInt("recovery-style",i).apply();description.setText(prompt(i));}});
        a.gap(card,12);a.full(card,"선택한 실천 시작",a.PURPLE,false,"recovery-start",()->{
            int choice=choices.getSelectedItemPosition();if(choice==1)a.record("마음");else if(choice==2)new AlertDialog.Builder(a).setTitle("잠깐, 주변에 머물러요").setMessage("편안한 색 하나 → 손끝에 닿는 감촉 하나 → 지금 들리는 소리 하나. 정답 없이 천천히 살펴봐요. 불편하면 멈춰도 괜찮아요.").setNegativeButton("닫기",null).setPositiveButton("마음 기록하기",(d,w)->a.record("마음")).show();else if(a.breathButton!=null){if(!a.breathing)a.breathButton.performClick();a.breathButton.requestFocus();}
        });a.gap(card,8);card.addView(a.tx("선택은 이 기기에 저장돼요. 진단이나 치료 추천이 아닙니다.",12,a.MUTED,false));
    }
    static void sleep(SleepSoundActivity a,LinearLayout body,JSONArray history){
        JSONObject latest=null,prior=null;for(int i=0;i<history.length();i++){JSONObject s=history.optJSONObject(i);if(s==null||!s.has("ended")||seconds(s)<1)continue;if(latest==null)latest=s;else{prior=s;break;}}
        LinearLayout card=new LinearLayout(a);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(a.galaxyDp(18),a.galaxyDp(14),a.galaxyDp(18),a.galaxyDp(14));card.setBackground(a.galaxyShape(a.surface(),24));card.setContentDescription("sleep-sound-review");body.addView(card);card.addView(a.text("측정 돌아보기",21));
        if(latest==null)card.addView(a.text("측정을 한 번 마치면 분석 범위와 이전 측정 비교가 표시돼요.",14));else{
            double seconds=seconds(latest);card.addView(a.text("최근 측정 "+(int)(seconds/60)+"분"+(prior==null?"":" · 그 이전 "+(int)(seconds(prior)/60)+"분"),16));SleepAudioCoverage audio=SleepAudioCoverage.analyze(latest);if(audio!=null){card.addView(a.text(audio.monotonic?"실제 경과 시간 기준":"저장된 기기 시각 기준",13));card.addView(a.text("경과 "+(int)(audio.elapsed/60)+"분 중 소리 요약 "+(int)(audio.recorded/60)+"분 · "+(int)(audio.fraction()*100)+"%",15));card.addView(a.text("요약에 포함되지 않은 시간 약 "+(int)(audio.gap()/60)+"분. 처리 지연·중단 등이 포함될 수 있고 수면 단계나 건강 점수가 아니에요.",13));}else card.addView(a.text("복구 상태·시각 정보로 전체 기록 범위를 확인할 수 없어요.",13));JSONObject research=latest.optJSONObject("research");if(research!=null){double coverage=CareUpgrade.coverage(research.optInt("analyzed"),seconds);if(Double.isFinite(coverage)){card.addView(a.text("모델 처리 비율 "+(int)(coverage*100)+"% · 보류 "+research.optInt("skipped")+"회",15));card.addView(a.text("매우 작은 소리·클리핑·실행 지연·중단은 분석에서 제외될 수 있어요.",13));}}}
        card.addView(a.text("처리 비율은 정확도나 수면 점수가 아니며, 측정 시간은 실제 수면 시간이 아닙니다.",13));
    }
    static double seconds(JSONObject session){double total=0;JSONArray bins=session.optJSONArray("bins");for(int i=0;bins!=null&&i<Math.min(480,bins.length());i++){JSONObject bin=bins.optJSONObject(i);if(bin==null)continue;double value=bin.optDouble("seconds");if(Double.isFinite(value)&&value>0&&value<=65)total+=value;}return total;}
}
