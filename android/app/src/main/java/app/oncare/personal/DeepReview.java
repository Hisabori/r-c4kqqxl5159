package app.oncare.personal;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.util.AtomicFile;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class DeepReview {
    static AtomicFile file(Context context){return new AtomicFile(new File(context.getNoBackupFilesDir(),"mind-capsule.json"));}
    static void save(Context context,Map<String,String> answers)throws Exception{
        byte[] data=new JSONObject(DeepMood.answers(answers)).toString().getBytes(StandardCharsets.UTF_8);if(data.length>32768)throw new IOException();
        AtomicFile file=file(context);FileOutputStream stream=null;try{stream=file.startWrite();stream.write(data);file.finishWrite(stream);}catch(Exception e){if(stream!=null)file.failWrite(stream);throw e;}
    }
    static Map<String,String> load(Context context)throws Exception{
        byte[] data;try(InputStream input=file(context).openRead();ByteArrayOutputStream buffer=new ByteArrayOutputStream()){byte[] part=new byte[1024];int count;while((count=input.read(part))!=-1){if(buffer.size()+count>32768)throw new IOException();buffer.write(part,0,count);}data=buffer.toByteArray();}
        JSONObject object=new JSONObject(new String(data,StandardCharsets.UTF_8));Map<String,String> values=new HashMap<>();for(Iterator<String> keys=object.keys();keys.hasNext();){String key=keys.next();Object value=object.get(key);if(value instanceof String)values.put(key,(String)value);}return DeepMood.answers(values);
    }
    static void remove(Context context)throws IOException{AtomicFile file=file(context);file.delete();if(file.getBaseFile().exists()||new File(file.getBaseFile()+".bak").exists())throw new IOException();}
    static void render(CareActivity a,LinearLayout card,DeepMood.Draft state,int accent){
        LinearLayout box=a.col();box.setVisibility(View.GONE);
        card.addView(DeepMood.button(a,"감정 전후 돌아보기 · 임시보관","deep-review-expand",accent,()->box.setVisibility(box.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE)));card.addView(box);
        DeepMood.line(a,box,"행동 뒤 느낌을 남겨봐. 그대로거나 더 힘들어도 네 표현 그대로야. 강도 차이를 치료 효과나 건강 점수로 계산하지 않아.",13,false);
        CheckBox toggle=new CheckBox(a);toggle.setText("지금의 강도도 남길래");toggle.setTextColor(Color.WHITE);toggle.setContentDescription("deep-review-intensity-toggle");toggle.setChecked(state.reflection.containsKey(DeepMood.AFTER));box.addView(toggle);
        int level=5;try{level=Integer.parseInt(state.reflection.get(DeepMood.AFTER));}catch(Exception ignored){}level=Math.max(0,Math.min(10,level));
        TextView label=DeepMood.text(a,"지금 "+level+"/10 · 0 아주 약함 → 10 아주 강함",14,false);box.addView(label);SeekBar slider=new SeekBar(a);slider.setMax(10);slider.setProgress(level);slider.setEnabled(toggle.isChecked());slider.setContentDescription("deep-review-intensity");box.addView(slider);
        toggle.setOnCheckedChangeListener((b,on)->{if(on)state.reflection.put(DeepMood.AFTER,String.valueOf(slider.getProgress()));else state.reflection.remove(DeepMood.AFTER);slider.setEnabled(on);});slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar v){}public void onStopTrackingTouch(SeekBar v){}public void onProgressChanged(SeekBar v,int n,boolean user){label.setText("지금 "+n+"/10 · 0 아주 약함 → 10 아주 강함");if(toggle.isChecked())state.reflection.put(DeepMood.AFTER,String.valueOf(n));}});
        for(int n=0;n<DeepMood.REVIEW.length;n++){final String key=DeepMood.REVIEW[n];DeepMood.line(a,box,key,15,true);EditText input=new EditText(a);input.setSingleLine(false);input.setMinLines(2);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);input.setTextColor(Color.WHITE);input.setHintTextColor(0xFFDFD5E5);input.setHint("네 말로 남겨봐 · 100자까지");input.setContentDescription("deep-review-"+n);input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(100)});input.setText(state.reflection.getOrDefault(key,""));input.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence v,int st,int count,int after){}public void onTextChanged(CharSequence v,int st,int before,int count){state.reflection.put(key,DeepMood.bounded(v.toString()));}public void afterTextChanged(android.text.Editable v){}});box.addView(input);}
        box.addView(DeepMood.button(a,"속사정·돌아보기 글 임시보관","deep-draft-save",accent,()->{try{save(a,state.reflection);a.toast("이 기기에 한 건 임시보관했어. 일기 저장과는 달라.");}catch(Exception e){a.toast("보관하지 못했어. 지금 입력은 유지돼.");}}));
        box.addView(DeepMood.button(a,"임시보관 이어 쓰기","deep-draft-restore",accent,()->new AlertDialog.Builder(a).setMessage("지금 쓴 속사정·돌아보기를 보관본으로 바꿀까?").setNegativeButton("취소",null).setPositiveButton("보관본 불러오기",(d,w)->{try{Map<String,String> saved=load(a);state.reflection.clear();state.reflection.putAll(saved);state.reflectionOpen=true;a.render();a.toast("보관본을 불러왔어. 일기로 남기려면 다음 화면에서 저장해.");}catch(Exception e){a.toast("보관본이 없거나 읽지 못했어. 지금 입력은 유지돼.");}}).show()));
        box.addView(DeepMood.button(a,"보관한 초안 삭제","deep-draft-delete",accent,()->new AlertDialog.Builder(a).setMessage("이 기기에 보관한 초안 한 건을 삭제할까?").setNegativeButton("취소",null).setPositiveButton("보관본 삭제",(d,w)->{try{remove(a);a.toast("보관본만 삭제했어. 지금 입력과 저장된 일기는 유지돼.");}catch(Exception e){a.toast("보관본을 삭제하지 못했어.");}}).show()));
        DeepMood.line(a,box,"보관 버튼을 누를 때만 이 기기에 한 건 저장해. 감정·상황 선택과 처음 강도는 보관하지 않아. 일기를 저장해도 보관본은 남으니 필요 없으면 삭제해. 일반 백업·기기 간 동기화에는 넣지 않아.",12,false);
    }
}
