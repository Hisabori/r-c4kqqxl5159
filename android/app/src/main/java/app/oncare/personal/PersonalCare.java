package app.oncare.personal;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.widget.*;

final class PersonalCare {
    static String message(String focus){return focus.equals("운동")?"무리하지 않는 목표로 운동 타이머를 시작해보세요.":focus.equals("수면")?"오늘의 수면 기록과 나에게 맞는 취침 시간을 돌아보세요.":focus.equals("투약")?"오늘 예정된 약과 직접 확인한 기록을 살펴보세요.":"잠깐 멈춰 지금의 마음을 기록해보세요. 필요할 때 도움을 요청할 수 있어요.";}
    static void support(CareActivity a){
        LinearLayout body=a.col();body.addView(a.tx("혼자 감당하지 않아도 괜찮아요",23,a.INK,true));body.addView(a.tx("대한민국 위기지원 안내입니다. 생명·신체에 즉각적인 위험이 있다면 긴급 신고를 이용하세요. 안전한 곳으로 이동하고 가까운 사람에게 함께 있어 달라고 요청할 수 있어요.",16,a.MUTED,false));
        dial(a,body,"자살예방상담전화 · 109","+82109");dial(a,body,"긴급 구조·구급 · 119","119");dial(a,body,"경찰 긴급 신고 · 112","112");
        body.addView(a.tx("전화는 자동으로 걸지 않아요. 전화 화면에서 직접 진행하세요. 위치·기록은 전송하지 않습니다. 해외에서는 현지 연락처를 확인하세요. 확인일: 2026.10.15",13,a.MUTED,false));
        a.full(body,"공식 상담 안내",a.BLUE,false,"crisis-source",()->{try{a.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.129.go.kr/109?oai_link_source=model_response_hotline")));}catch(Exception e){a.toast("웹 브라우저를 확인해주세요");}});a.sheet("도움과 위기지원",body).show();
    }
    static void dial(CareActivity a,LinearLayout body,String label,String number){a.full(body,label+" · 전화 화면 열기",a.BLUE,false,"crisis-"+number,()->{try{a.startActivity(new Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+number)));}catch(Exception e){a.toast("전화 앱에 "+number+"을 직접 입력해주세요");}});}
    static void settings(CareActivity a){
        SharedPreferences p=a.getSharedPreferences("personal-care",Context.MODE_PRIVATE);LinearLayout body=a.col();EditText name=new EditText(a);name.setHint("부르고 싶은 이름 (선택)");name.setText(p.getString("name",""));name.setSingleLine();name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(30)});body.addView(name);
        String[] choices={"마음","수면","운동","투약"};Spinner focus=new Spinner(a);focus.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,choices));focus.setSelection(Math.max(0,java.util.Arrays.asList(choices).indexOf(p.getString("focus","마음"))));body.addView(a.tx("지금 관심 있는 분야",16,a.INK,true));body.addView(focus);
        Spinner goal=new Spinner(a);String[] labels={"10분","20분","30분","60분"};int[] minutes={10,20,30,60};goal.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,labels));int selected=1;for(int i=0;i<4;i++)if(minutes[i]==p.getInt("minutes",20))selected=i;goal.setSelection(selected);body.addView(a.tx("기본 운동 목표",16,a.INK,true));body.addView(goal);
        body.addView(a.tx("직접 고른 관심 분야로 홈 안내를 바꿔요. 기기 안에만 저장하며 위기 수준을 추정하지 않습니다.",13,a.MUTED,false));Dialog dialog=a.sheet("나에게 맞추기",body);
        a.full(body,"저장",a.BLUE,true,"personalize-save",()->{String value=name.getText().toString().replaceAll("\\p{Cntrl}","").trim();if(p.edit().putString("name",value).putString("focus",choices[focus.getSelectedItemPosition()]).putInt("minutes",minutes[goal.getSelectedItemPosition()]).commit()){dialog.dismiss();a.render();}else a.toast("저장하지 못했어요");});a.full(body,"개인화 초기화",a.BLUE,false,"personalize-reset",()->{if(p.edit().clear().commit()){dialog.dismiss();a.render();}});dialog.show();
    }
}

