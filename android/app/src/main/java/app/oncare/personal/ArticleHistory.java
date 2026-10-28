package app.oncare.personal;
import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

final class ArticleHistory {
    static List<String> load(SharedPreferences prefs,JSONArray catalog){
        List<String> result=new ArrayList<>();if(!prefs.getBoolean("keep-recent",true))return result;
        Set<String> known=new HashSet<>();for(int i=0;i<catalog.length();i++){JSONObject a=catalog.optJSONObject(i);if(a!=null)known.add(a.optString("id"));}
        try{JSONArray values=new JSONArray(prefs.getString("recent-opened","[]"));for(int i=0;i<values.length()&&result.size()<10;i++){String id=values.optString(i);if(known.contains(id)&&!result.contains(id))result.add(id);}}catch(Exception ignored){}
        return result;
    }
    static void opened(SharedPreferences prefs,JSONArray catalog,String id){
        if(!prefs.getBoolean("keep-recent",true))return;boolean known=false;for(int i=0;i<catalog.length();i++){JSONObject a=catalog.optJSONObject(i);if(a!=null&&id.equals(a.optString("id"))){known=true;break;}}if(!known)return;
        List<String> values=load(prefs,catalog);values.remove(id);values.add(0,id);if(values.size()>10)values.remove(values.size()-1);prefs.edit().putString("recent-opened",new JSONArray(values).toString()).apply();
    }
    static void clear(SharedPreferences prefs){prefs.edit().remove("recent-opened").apply();}
}
