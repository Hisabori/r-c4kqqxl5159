package app.oncare.personal;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

final class MedicationStore {
    static final Object FILE_LOCK=new Object();
    static final class Plan {
        String id=UUID.randomUUID().toString(),name="",instructions="";
        int hour=9,minute=0;
        boolean enabled=false;
        Set<Integer> days=new TreeSet<>(Arrays.asList(1,2,3,4,5,6,7));
        void validate() throws Exception {
            UUID.fromString(id);
            if(name.trim().isEmpty()||name.length()>80||instructions.length()>200||hour<0||hour>23||minute<0||minute>59||days.isEmpty()||days.size()>7)throw new IOException("invalid plan");
            for(int day:days)if(day<1||day>7)throw new IOException("invalid weekday");
        }
        JSONObject json() throws Exception {validate();JSONArray d=new JSONArray();for(int day:days)d.put(day);return new JSONObject().put("id",id).put("name",name).put("instructions",instructions).put("hour",hour).put("minute",minute).put("days",d).put("enabled",enabled);}
        static Plan parse(JSONObject raw)throws Exception {
            Plan p=new Plan();p.id=raw.getString("id");p.name=raw.getString("name");p.instructions=raw.getString("instructions");p.hour=raw.getInt("hour");p.minute=raw.getInt("minute");p.enabled=raw.getBoolean("enabled");p.days.clear();JSONArray d=raw.getJSONArray("days");for(int i=0;i<d.length();i++)if(!p.days.add(d.getInt(i)))throw new IOException("duplicate day");p.validate();return p;
        }
        String time(){return String.format(Locale.KOREA,"%02d:%02d",hour,minute);}
        String weekdays(){if(days.size()==7)return "매일";StringBuilder s=new StringBuilder();String[] names={"","일","월","화","수","목","금","토"};for(int day:new int[]{2,3,4,5,6,7,1})if(days.contains(day)){if(s.length()>0)s.append(" · ");s.append(names[day]);}return s.toString();}
    }
    final AtomicFile file;
    ArrayList<Plan> plans=new ArrayList<>();
    JSONObject checks=new JSONObject();
    boolean readable=true;
    MedicationStore(Context c){this(new File(c.getFilesDir(),"medications-v1.json"));}
    MedicationStore(File path){file=new AtomicFile(path);load();}
    void load(){synchronized(FILE_LOCK){
        if(!file.getBaseFile().exists()&&!new File(file.getBaseFile()+".bak").exists())return;
        try(FileInputStream input=file.openRead()){
            ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;
            while((count=input.read(buffer))!=-1){if(output.size()+count>65536)throw new IOException("too large");output.write(buffer,0,count);}byte[] bytes=output.toByteArray();
            JSONObject state=new JSONObject(new String(bytes,StandardCharsets.UTF_8));if(state.getInt("version")!=1)throw new IOException("version");
            ArrayList<Plan> loaded=new ArrayList<>();JSONArray a=state.getJSONArray("plans");for(int i=0;i<a.length();i++)loaded.add(Plan.parse(a.getJSONObject(i)));
            JSONObject marks=state.getJSONObject("checks");validate(loaded,marks);plans=loaded;checks=marks;
        }catch(Exception e){readable=false;}
    }}
    static String dayKey(long time){return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date(time));}
    static void validate(List<Plan> plans,JSONObject checks)throws Exception {
        if(plans.size()>8||checks.length()>31)throw new IOException("limit");Set<String> ids=new HashSet<>();
        for(Plan p:plans){p.validate();if(!ids.add(p.id))throw new IOException("duplicate id");}
        SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd",Locale.US);format.setLenient(false);
        Iterator<String> days=checks.keys();while(days.hasNext()){String key=days.next();if(!key.matches("\\d{4}-\\d{2}-\\d{2}")||!format.format(format.parse(key)).equals(key))throw new IOException("date");JSONArray marks=checks.getJSONArray(key);Set<String> seen=new HashSet<>();for(int i=0;i<marks.length();i++)if(!ids.contains(marks.getString(i))||!seen.add(marks.getString(i)))throw new IOException("check id");}
    }
    boolean persist(ArrayList<Plan> next,JSONObject marks){synchronized(FILE_LOCK){
        if(!readable)return false;FileOutputStream stream=null;
        try{validate(next,marks);JSONArray a=new JSONArray();for(Plan p:next)a.put(p.json());byte[] bytes=new JSONObject().put("version",1).put("plans",a).put("checks",marks).toString().getBytes(StandardCharsets.UTF_8);stream=file.startWrite();stream.write(bytes);file.finishWrite(stream);plans=next;checks=marks;return true;}
        catch(Exception e){if(stream!=null)file.failWrite(stream);return false;}
    }}
    boolean save(Plan plan){try{Plan copy=Plan.parse(plan.json());ArrayList<Plan> next=new ArrayList<>(plans);next.removeIf(p->p.id.equals(copy.id));next.add(copy);return persist(next,new JSONObject(checks.toString()));}catch(Exception e){return false;}}
    boolean remove(String id){try{ArrayList<Plan> next=new ArrayList<>(plans);next.removeIf(p->p.id.equals(id));JSONObject marks=new JSONObject();Iterator<String> keys=checks.keys();while(keys.hasNext()){String key=keys.next();JSONArray from=checks.getJSONArray(key),to=new JSONArray();for(int i=0;i<from.length();i++)if(!id.equals(from.getString(i)))to.put(from.getString(i));marks.put(key,to);}return persist(next,marks);}catch(Exception e){return false;}}
    boolean checked(String id,long now){JSONArray a=checks.optJSONArray(dayKey(now));if(a!=null)for(int i=0;i<a.length();i++)if(id.equals(a.optString(i)))return true;return false;}
    boolean toggle(String id,long now){
        try{Calendar date=Calendar.getInstance();date.setTimeInMillis(now);boolean exists=false;for(Plan p:plans)if(p.id.equals(id)&&p.days.contains(date.get(Calendar.DAY_OF_WEEK)))exists=true;if(!exists)return false;
            date.add(Calendar.DAY_OF_YEAR,-30);String cutoff=dayKey(date.getTimeInMillis()),today=dayKey(now);JSONObject marks=new JSONObject();Iterator<String> keys=checks.keys();while(keys.hasNext()){String key=keys.next();if(key.compareTo(cutoff)>=0&&key.compareTo(today)<=0)marks.put(key,checks.getJSONArray(key));}
            JSONArray original=marks.optJSONArray(today),updated=new JSONArray();boolean was=checked(id,now);if(original!=null)for(int i=0;i<original.length();i++)if(!id.equals(original.getString(i)))updated.put(original.getString(i));if(!was)updated.put(id);marks.put(today,updated);return persist(new ArrayList<>(plans),marks);
        }catch(Exception e){return false;}
    }
}
