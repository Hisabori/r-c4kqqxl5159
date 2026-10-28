package app.oncare.personal;
import android.content.SharedPreferences;
import org.json.*;
import java.util.*;
final class ArticleRank {
 static String kind(String topic){return topic.equals("수면")?"수면":topic.equals("마음")?"마음":topic.equals("운동")?"운동":"체중";}
 static List<JSONObject> sorted(JSONArray catalog,SharedPreferences prefs,Set<String> recent){List<JSONObject> list=new ArrayList<>();for(int i=0;i<catalog.length();i++){JSONObject item=catalog.optJSONObject(i);if(item!=null)list.add(item);}list.sort((a,b)->Integer.compare(score(b,prefs,recent),score(a,prefs,recent)));return list;}
 static int score(JSONObject a,SharedPreferences p,Set<String> recent){return (p.getBoolean("interest:"+a.optString("topic"),false)?4:0)+(recent.contains(kind(a.optString("topic")))?2:0)+(p.getBoolean("read:"+a.optString("id"),false)?0:1);}
 static String reason(JSONObject a,SharedPreferences p,Set<String> recent){return p.getBoolean("interest:"+a.optString("topic"),false)?"선택한 관심 주제 · "+a.optString("topic"):recent.contains(kind(a.optString("topic")))?"최근 7일에 "+kind(a.optString("topic"))+" 기록이 있어요":"온케어가 고른 일상 건강 읽을거리";}
 static Set<String> recent(java.util.List<JSONObject> entries,long now){Set<String> kinds=new HashSet<>();for(JSONObject e:entries)if(e.optLong("date")>=now-7L*86400000&&e.optLong("date")<=now)kinds.add(e.optString("kind"));return kinds;}
}
