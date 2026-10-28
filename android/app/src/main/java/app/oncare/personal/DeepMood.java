package app.oncare.personal;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.*;
import java.util.*;

final class DeepMood {
    static final String[] STYLES={"미드나잇","로즈","라일락"};
    static final String[] EMOTIONS={"불안","서운함","외로움","질투","분노","공허함","지침","슬픔","그리움","안도","설렘","편안함"};
    static final String[] CONTEXTS={"연락","관계","혼자 있는 시간","일·공부","수면","몸 상태","이유 모름"};
    static final String[] ENERGIES={"선택 안 함","축 처짐","중간","들뜸·긴장"};
    static final String[] REFLECTION={"실제로 있었던 일", "머릿속에서 붙인 의미", "몸에서 느껴진 것", "정말 바랐던 것", "지키고 싶은 선", "지금 할 수 있는 작은 일"};
    static final String[] REVIEW={"해본 행동","도움이 된 점 · 어려웠던 점","다음에 기억할 것"};
    static final String AFTER="돌아본 감정 강도";
    static Map<String,String> answers(Map<String,String> values){Map<String,String> result=new HashMap<>();List<String> keys=new ArrayList<>(Arrays.asList(REFLECTION));keys.addAll(Arrays.asList(REVIEW));for(String key:keys){String value=bounded(values.get(key));if(!value.trim().isEmpty())result.put(key,value);}try{int value=Integer.parseInt(values.get(AFTER));if(value>=0&&value<=10)result.put(AFTER,String.valueOf(value));}catch(Exception ignored){}return result;}
    static String bounded(String text){if(text==null)return "";String value=text.length()>100?text.substring(0,100):text;if(!value.isEmpty()&&Character.isHighSurrogate(value.charAt(value.length()-1)))value=value.substring(0,value.length()-1);return value;}
    static final class Draft {final Map<String,String> reflection=new HashMap<>();boolean reflectionOpen=false;final Set<String> emotions=new HashSet<>(),contexts=new HashSet<>();int prompt=0,intensity=5;boolean useIntensity=false;String energy=ENERGIES[0];}
    static String style(String raw){return Arrays.asList(STYLES).contains(raw)?raw:STYLES[0];}
    static List<String> chosen(String[] allowed,Set<String> selected,int max){ArrayList<String> result=new ArrayList<>();for(String s:allowed)if(selected.contains(s)&&result.size()<max)result.add(s);return result;}
    static String draft(int prompt,String focus,Set<String> emotions,Set<String> contexts,Integer intensity,String energy){
        return draft(prompt,focus,emotions,contexts,intensity,energy,Collections.emptyMap());
    }
    static String draft(int prompt,String focus,Set<String> emotions,Set<String> contexts,Integer intensity,String energy,Map<String,String> reflection){
        StringBuilder text=new StringBuilder("[내 마음 캡슐]\n");List<String> feelings=chosen(EMOTIONS,emotions,3),situations=chosen(CONTEXTS,contexts,2);
        if(!feelings.isEmpty())text.append("섞인 감정: ").append(String.join(" · ",feelings)).append('\n');
        if(!situations.isEmpty())text.append("마음이 움직인 상황: ").append(String.join(" · ",situations)).append('\n');
        if(intensity!=null&&intensity>=0&&intensity<=10)text.append("내가 고른 강도: ").append(intensity).append("/10 (0 아주 약함 · 10 아주 강함)\n");
        if(!ENERGIES[0].equals(energy)&&Arrays.asList(ENERGIES).contains(energy))text.append("지금 에너지: ").append(energy).append('\n');
        StringBuilder detail=new StringBuilder();for(String label:REFLECTION){String answer=bounded(reflection.get(label)).trim();if(!answer.isEmpty())detail.append(label).append(": ").append(answer).append('\n');}
        if(detail.length()>0)text.append("\n[조금 더 깊이, 내 말로]\n").append(detail);
        StringBuilder review=new StringBuilder();for(String label:REVIEW){String answer=bounded(reflection.get(label)).trim();if(!answer.isEmpty())review.append(label).append(": ").append(answer).append('\n');}
        if(review.length()>0)text.append("\n[감정 전후 돌아보기]\n").append(review);
        String after=answers(reflection).get(AFTER);if(after!=null)text.append("돌아본 감정 강도: ").append(after).append("/10 · 내가 직접 남긴 표현\n");
        return text.append('\n').append(DeepMind.template(prompt,focus)).toString();
    }
    static int accent(String style){return style.equals("라일락")?0xFFD3B8FF:0xFFF2BACF;}
    static GradientDrawable background(String style){GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{style.equals("로즈")?0xFF381728:style.equals("라일락")?0xFF241937:0xFF15101C,0xFF19131F});bg.setCornerRadius(30);bg.setStroke(1,0xFF574051);return bg;}
    static TextView text(CareActivity a,String value,int size,boolean bold){return a.tx(value,size,bold?Color.WHITE:0xFFDFD5E5,bold);}
    static void line(CareActivity a,LinearLayout card,String value,int size,boolean bold){a.gap(card,12);card.addView(text(a,value,size,bold));}
    static Button button(CareActivity a,String value,String id,int accent,Runnable action){Button b=new Button(a);b.setText(value);b.setTextSize(15);b.setAllCaps(false);b.setTextColor(accent);b.setMinHeight(a.dp(52));b.setContentDescription(id);b.setBackground(a.bg(0xFF342637,18));b.setOnClickListener(v->action.run());return b;}
    static void chips(CareActivity a,LinearLayout card,String[] choices,Set<String> selected,int max,String prefix,int accent){
        GridLayout grid=new GridLayout(a);grid.setColumnCount(2);card.addView(grid);
        for(String item:choices){Button b=button(a,(selected.contains(item)?"✓ ":"")+item,"deep-mood-"+prefix+"-"+item,accent,()->{});b.setSelected(selected.contains(item));b.setOnClickListener(v->{if(selected.contains(item))selected.remove(item);else if(selected.size()<max)selected.add(item);else{a.toast("최대 "+max+"개까지 골라줘");return;}b.setText((selected.contains(item)?"✓ ":"")+item);b.setSelected(selected.contains(item));});GridLayout.LayoutParams p=new GridLayout.LayoutParams();p.width=0;p.height=-2;p.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);p.setMargins(a.dp(3),a.dp(4),a.dp(3),a.dp(4));grid.addView(b,p);}
    }
    static Spinner spinner(CareActivity a,LinearLayout card,String title,String[] values,int initial,String id,java.util.function.IntConsumer onSelect){
        line(a,card,title,15,true);Spinner spinner=new Spinner(a);spinner.setContentDescription(id);spinner.setPopupBackgroundDrawable(a.bg(0xFF241A2B,16));
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(a,android.R.layout.simple_spinner_dropdown_item,values){@Override public View getView(int pos,View reused,android.view.ViewGroup parent){TextView v=(TextView)super.getView(pos,reused,parent);v.setTextColor(Color.WHITE);v.setMinHeight(a.dp(48));return v;}@Override public View getDropDownView(int pos,View reused,android.view.ViewGroup parent){TextView v=(TextView)super.getDropDownView(pos,reused,parent);v.setTextColor(Color.WHITE);v.setPadding(a.dp(12),a.dp(12),a.dp(12),a.dp(12));return v;}};
        spinner.setAdapter(adapter);spinner.setSelection(Math.max(0,Math.min(values.length-1,initial)));card.addView(spinner);spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int i,long id){onSelect.accept(i);}});return spinner;
    }
    static void render(CareActivity a,LinearLayout card,Draft state){
        card.removeAllViews();android.content.SharedPreferences prefs=a.getSharedPreferences("app-preferences",0);String theme=style(prefs.getString("deep-mind-style",STYLES[0]));int accent=accent(theme);card.setBackground(background(theme));card.setContentDescription("deep-mind-card");
        line(a,card,"A LITTLE ROOM FOR YOU",13,false);line(a,card,"오늘의 마음을,\n천천히 펼쳐봐.",28,true);line(a,card,"말이 되지 않는 마음도 괜찮아. 한 줄부터 남겨봐.",14,false);
        line(a,card,"☾  다 쓰지 않아도, 오늘의 한 줄이면 충분해.",14,false);
        LinearLayout themes=a.row();card.addView(themes);for(String item:STYLES){Button b=button(a,(theme.equals(item)?"✓ ":"")+item,"deep-mood-style-"+item,accent,()->{prefs.edit().putString("deep-mind-style",item).apply();a.render();});themes.addView(b,new LinearLayout.LayoutParams(0,-2,1));}
        state.prompt=DeepMind.promptIndex(prefs.getInt("deep-mind-prompt-v1",state.prompt));
        TextView focusQuestion=text(a,DeepMind.starter(prefs.getString("deep-mind-focus",DeepMind.FOCUS[0])),18,true);focusQuestion.setContentDescription("deep-focus-question");card.addView(focusQuestion);
        card.addView(button(a,"말이 안 나올 땐 · 짧게 기록","deep-short-write",accent,()->a.recordForm("마음",null,3,null,DeepMind.shortTemplate(prefs.getString("deep-mind-focus",DeepMind.FOCUS[0])))));
        line(a,card,"선택·속사정을 넣지 않은 새 짧은 양식을 열어. 저장은 다음 화면에서 네가 결정해.",12,false);
        line(a,card,"마음은 하나만 고를 필요 없어",20,true);line(a,card,"섞인 감정 · 최대 3개",14,false);chips(a,card,EMOTIONS,state.emotions,3,"emotion",accent);
        line(a,card,"어떤 순간이었어? · 최대 2개",16,true);chips(a,card,CONTEXTS,state.contexts,2,"context",accent);
        CheckBox intensity=new CheckBox(a);intensity.setText("감정 강도도 남길래");intensity.setTextColor(Color.WHITE);intensity.setChecked(state.useIntensity);intensity.setContentDescription("deep-mood-intensity-toggle");card.addView(intensity);
        TextView level=text(a,state.intensity+"/10 · 0 아주 약함 → 10 아주 강함",14,false);card.addView(level);SeekBar slider=new SeekBar(a);slider.setMax(10);slider.setProgress(state.intensity);slider.setEnabled(state.useIntensity);slider.setContentDescription("deep-mood-intensity");card.addView(slider);intensity.setOnCheckedChangeListener((b,on)->{state.useIntensity=on;slider.setEnabled(on);});slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar v){}public void onStopTrackingTouch(SeekBar v){}public void onProgressChanged(SeekBar v,int n,boolean user){state.intensity=n;level.setText(n+"/10 · 0 아주 약함 → 10 아주 강함");}});
        spinner(a,card,"지금 에너지",ENERGIES,Arrays.asList(ENERGIES).indexOf(state.energy),"deep-mood-energy",i->state.energy=ENERGIES[i]);
        spinner(a,card,"오늘 더 들여다볼 마음",DeepMind.FOCUS,Arrays.asList(DeepMind.FOCUS).indexOf(DeepMind.focus(prefs.getString("deep-mind-focus",DeepMind.FOCUS[0]))),"deep-mind-focus",i->{prefs.edit().putString("deep-mind-focus",DeepMind.FOCUS[i]).apply();focusQuestion.setText(DeepMind.starter(DeepMind.FOCUS[i]));});
        LinearLayout details=a.col();details.setVisibility(state.reflectionOpen?View.VISIBLE:View.GONE);
        card.addView(button(a,"조금 더 깊이 · 마음의 속사정","deep-reflection-expand",accent,()->{state.reflectionOpen=!state.reflectionOpen;details.setVisibility(state.reflectionOpen?View.VISIBLE:View.GONE);}));card.addView(details);
        line(a,details,"사실과 해석을 따로 놓고, 네가 바랐던 것까지 적어봐. 빈칸은 남겨도 돼. 각 항목은 100자까지야.",13,false);
        for(int n=0;n<REFLECTION.length;n++){final String label=REFLECTION[n];line(a,details,label,15,true);EditText input=new EditText(a);input.setBackground(a.bg(0xFF261F2D,18));input.setPadding(a.dp(16),a.dp(14),a.dp(16),a.dp(14));input.setSingleLine(false);input.setMinLines(2);input.setMaxLines(4);input.setTextColor(Color.WHITE);input.setHintTextColor(0xFFDFD5E5);input.setHint("네 말로, 짧게 적어도 돼");input.setContentDescription("deep-reflection-"+n);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(100)});input.setText(state.reflection.getOrDefault(label,""));input.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence v,int start,int count,int after){}public void onTextChanged(CharSequence v,int start,int before,int count){state.reflection.put(label,bounded(v.toString()));}public void afterTextChanged(android.text.Editable v){}});details.addView(input);}
        details.addView(button(a,"지금 쓴 속사정 비우기","deep-reflection-clear",accent,()->{for(String key:REFLECTION)state.reflection.remove(key);render(a,card,state);}));
        line(a,details,"일기 초안으로만 이어져. 다음 화면에서 저장해야 기록돼. 앱을 종료하면 저장 전 내용은 사라질 수 있어.",12,false);
        DeepReview.render(a,card,state,accent);
        TextView preview=text(a,DeepMind.TEMPLATES[state.prompt],14,false);spinner(a,card,"어디까지 써볼까",DeepMind.PROMPTS,state.prompt,"deep-mind-prompt",i->{state.prompt=DeepMind.promptIndex(i);prefs.edit().putInt("deep-mind-prompt-v1",state.prompt).apply();preview.setText(DeepMind.TEMPLATES[state.prompt]);});line(a,card,"선택한 기록 주제만 이 기기에 기억해. 본문을 이어 쓰려면 위의 임시보관 버튼으로 남겨둬.",12,false);line(a,card,"고른 주제로 초안을 열어줄게",14,false);card.addView(preview);
        a.gap(card,14);card.addView(button(a,"마음 캡슐 열고 일기 쓰기","deep-mind-write",accent,()->a.recordForm("마음",null,3,null,draft(state.prompt,prefs.getString("deep-mind-focus",DeepMind.FOCUS[0]),state.emotions,state.contexts,state.useIntensity?state.intensity:null,state.energy,state.reflection))),new LinearLayout.LayoutParams(-1,-2));
        line(a,card,"선택은 초안이야. 다음 화면에서 고치고 저장해야 기록돼. 강도는 네 표현이고 건강 점수로 환산하지 않아.",12,false);
        line(a,card,"내가 남긴 두 주의 온도",21,true);for(boolean previous:new boolean[]{false,true}){DeepMind.Summary s=DeepMind.summarize(a.entries,System.currentTimeMillis(),previous);line(a,card,(previous?"그 이전 7일":"최근 7일")+" · "+(s.average==null?"기록 없음 · 추정하지 않아":a.fmt(s.average)+" / 5 · "+s.days+"일 기록 · 1~2점 "+s.difficultDays+"일"),14,true);}
        line(a,card,"하루 마지막 직접 입력만 비교해. 기록 없는 날은 빼고, 1~2점 일수는 네가 힘들다고 남긴 날이야. 위험도나 진단은 아니야. 연동 마음 상태는 원본으로 따로 보관해.",12,false);
        a.gap(card,14);card.addView(button(a,"혼자 정리하기 벅찰 땐 · 도움 보기","deep-mind-support",accent,()->PersonalCare.support(a)),new LinearLayout.LayoutParams(-1,-2));line(a,card,"모드를 꺼도 저장한 일기는 남아. ChatGPT 개인 메모리를 자동으로 읽거나 이 모드만으로 내용을 외부 전송하지 않아.",12,false);
    }
}
