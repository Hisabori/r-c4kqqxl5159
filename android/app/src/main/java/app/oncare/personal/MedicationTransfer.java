package app.oncare.personal;

import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

final class MedicationTransfer {
    static final class Snapshot {
        final ArrayList<MedicationStore.Plan> plans;
        final JSONObject checks;
        Snapshot(ArrayList<MedicationStore.Plan> plans,JSONObject checks){this.plans=plans;this.checks=checks;}
    }
    static byte[] encode(MedicationStore store)throws Exception {
        if(!store.readable)throw new IOException("unreadable");MedicationStore.validate(store.plans,store.checks);JSONArray plans=new JSONArray();
        for(MedicationStore.Plan plan:store.plans){JSONObject wire=plan.json();wire.put("weekdays",wire.getJSONArray("days"));wire.remove("days");plans.put(wire);}
        byte[] data=new JSONObject().put("format","oncare-medications").put("version",1).put("plans",plans).put("checks",store.checks).toString(2).getBytes(StandardCharsets.UTF_8);if(data.length>65536)throw new IOException("too large");return data;
    }
    static Snapshot decode(byte[] data)throws Exception {
        if(data.length>65536)throw new IOException("too large");JSONTokener parser=new JSONTokener(new String(data,StandardCharsets.UTF_8));Object value=parser.nextValue();if(!(value instanceof JSONObject)||parser.nextClean()!=0)throw new IOException("JSON");JSONObject root=(JSONObject)value;
        if(!"oncare-medications".equals(root.get("format"))||!(root.get("version") instanceof Number)||root.getDouble("version")!=1)throw new IOException("format");
        JSONArray list=root.getJSONArray("plans");if(list.length()>8)throw new IOException("limit");ArrayList<MedicationStore.Plan> plans=new ArrayList<>();
        for(int i=0;i<list.length();i++){
            JSONObject wire=new JSONObject(list.getJSONObject(i).toString());
            for(String key:new String[]{"id","name","instructions"})if(!(wire.get(key) instanceof String))throw new IOException("text");
            if(!(wire.get("enabled") instanceof Boolean))throw new IOException("enabled");
            for(String key:new String[]{"hour","minute"})if(!(wire.get(key) instanceof Number)||!Double.isFinite(wire.getDouble(key))||wire.getDouble(key)!=wire.getInt(key))throw new IOException("time");
            JSONArray days=wire.getJSONArray("weekdays");for(int j=0;j<days.length();j++)if(!(days.get(j) instanceof Number)||days.getDouble(j)!=days.getInt(j))throw new IOException("weekday");
            wire.put("days",days);plans.add(MedicationStore.Plan.parse(wire));
        }
        JSONObject checks=root.getJSONObject("checks");Iterator<String> keys=checks.keys();while(keys.hasNext()){JSONArray ids=checks.getJSONArray(keys.next());for(int i=0;i<ids.length();i++)if(!(ids.get(i) instanceof String))throw new IOException("id");}
        MedicationStore.validate(plans,checks);return new Snapshot(plans,checks);
    }
    static byte[] read(InputStream input)throws IOException {
        ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;while((count=input.read(buffer))!=-1){if(output.size()+count>65536)throw new IOException("too large");output.write(buffer,0,count);}return output.toByteArray();
    }
    static boolean replace(MedicationStore store,Snapshot snapshot){synchronized(MedicationStore.FILE_LOCK){
        boolean oldReadable=store.readable;
        try {
            MedicationStore.validate(snapshot.plans,snapshot.checks);ArrayList<MedicationStore.Plan> plans=new ArrayList<>();for(MedicationStore.Plan source:snapshot.plans){MedicationStore.Plan plan=MedicationStore.Plan.parse(source.json());plan.enabled=false;plans.add(plan);}
            if(!oldReadable){File source=store.file.getBaseFile(),preserved=new File(source.getParentFile(),"medications-preserved-"+UUID.randomUUID()+".json");Files.copy(source.toPath(),preserved.toPath());}
            store.readable=true;if(store.persist(plans,new JSONObject(snapshot.checks.toString())))return true;
        }catch(Exception ignored){}
        store.readable=oldReadable;return false;
    }}
    static ArrayList<MedicationStore.Plan> today(MedicationStore store,long now){Calendar date=Calendar.getInstance();date.setTimeInMillis(now);ArrayList<MedicationStore.Plan> today=new ArrayList<>();for(MedicationStore.Plan p:store.plans)if(p.days.contains(date.get(Calendar.DAY_OF_WEEK)))today.add(p);today.sort(Comparator.comparingInt((MedicationStore.Plan p)->p.hour*60+p.minute).thenComparing(p->p.id));return today;}
}
