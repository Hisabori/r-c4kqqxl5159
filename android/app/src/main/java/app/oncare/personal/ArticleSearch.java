package app.oncare.personal;
import org.json.*;
final class ArticleSearch {
    static boolean matches(JSONObject article,String query){
        StringBuilder content=new StringBuilder();for(String key:new String[]{"title","subtitle","topic","summary","action"})content.append(article.optString(key)).append(' ');
        JSONArray items=article.optJSONArray("takeaways");for(int i=0;items!=null&&i<items.length();i++)content.append(items.optString(i)).append(' ');
        String hay=content.toString().toLowerCase(java.util.Locale.ROOT);for(String word:query.toLowerCase(java.util.Locale.ROOT).trim().split("[\\s\\p{Z}]+")){if(!word.isEmpty()&&!hay.contains(word))return false;}return true;
    }
}
