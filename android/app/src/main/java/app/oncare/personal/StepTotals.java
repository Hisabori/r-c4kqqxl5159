package app.oncare.personal;
import org.json.*;
import java.time.LocalDate;
import java.util.*;
final class StepTotals {
    static int goal(int raw){return Math.min(30000,Math.max(1000,raw/500*500));}
    static TreeMap<String,Long> valid(JSONArray rows,LocalDate today)throws JSONException{
        TreeMap<String,Long> result=new TreeMap<>();
        if(rows==null)return result;
        if(rows.length()>100)throw new JSONException("size");
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.getJSONObject(i);String day=row.getString("day");double n=row.getDouble("count");
            LocalDate date;try{date=LocalDate.parse(day);}catch(Exception e){throw new JSONException("date");}
            if(!date.toString().equals(day)||!Double.isFinite(n)||n<0||n>1000000||n!=Math.rint(n))throw new JSONException("count");
            if(!date.isBefore(today.minusDays(6))&&!date.isAfter(today))result.put(day,(long)n);
        }
        return result;
    }
    static Long difference(JSONArray rows,LocalDate today)throws JSONException{TreeMap<String,Long> values=valid(rows,today);Long a=values.get(today.toString()),b=values.get(today.minusDays(1).toString());return a==null||b==null?null:a-b;}
    static JSONArray merge(JSONArray old,JSONArray fresh,LocalDate today)throws JSONException{
        TreeMap<String,Long> values=valid(old,today);values.putAll(valid(fresh,today));JSONArray out=new JSONArray();
        for(Map.Entry<String,Long> e:values.entrySet())out.put(new JSONObject().put("day",e.getKey()).put("count",e.getValue()));return out;
    }
}
