package app.oncare.personal;
import org.json.*;
import java.util.*;
final class HealthSurvey {
    static final String[] QUESTIONS={"잠을 자고 충분히 쉬었다고 느낀 날","내 상황에 맞게 몸을 움직인 날","마음이 편안하다고 느낀 날","처방받은 복용 일정을 확인한 날"};
    static final String[] CHOICES={"없음","1~2일","3~4일","5~6일","매일","투약 해당 없음"};
    static JSONArray array(int[] answers){JSONArray a=new JSONArray();for(int n:answers)a.put(n);return a;}
    static boolean valid(int[] answers){if(answers.length!=4)return false;for(int i=0;i<4;i++)if(answers[i]<0||answers[i]>(i==3?5:4))return false;return true;}
    static int[] answers(JSONArray list)throws JSONException{if(list.length()!=4)throw new JSONException("answers");int[] a=new int[4];for(int i=0;i<4;i++){if(!(list.get(i) instanceof Number)||list.getDouble(i)!=list.getInt(i))throw new JSONException("answers");a[i]=list.getInt(i);}if(!valid(a))throw new JSONException("answers");return a;}
    static JSONArray history(String raw)throws JSONException{if(raw.length()>65536)throw new JSONException("size");JSONArray list=new JSONArray(raw);if(list.length()>12)throw new JSONException("size");HashSet<String> ids=new HashSet<>();for(int i=0;i<list.length();i++){JSONObject r=list.getJSONObject(i);String id=r.getString("id");try{if(!UUID.fromString(id).toString().equals(id)||!ids.add(id))throw new IllegalArgumentException();}catch(IllegalArgumentException e){throw new JSONException("id");}long date=r.getLong("date");if(date<=0||date>System.currentTimeMillis()+1000)throw new JSONException("date");answers(r.getJSONArray("answers"));}return list;}
    static JSONArray append(JSONArray existing,int[] answers)throws JSONException{if(!valid(answers))throw new JSONException("answers");JSONArray next=new JSONArray();next.put(new JSONObject().put("id",UUID.randomUUID().toString()).put("date",System.currentTimeMillis()).put("answers",array(answers)));for(int i=0;i<Math.min(11,existing.length());i++)next.put(existing.getJSONObject(i));return history(next.toString());}
}
