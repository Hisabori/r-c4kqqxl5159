package app.oncare.personal;

import android.annotation.TargetApi;
import android.health.connect.datatypes.*;
import org.json.*;
import java.util.*;

/** Canonical, source-provided sleep intervals for the existing Wi-Fi snapshot. */
@TargetApi(34)
final class SleepExport {
    static final int MAX_STAGES=2048;
    static final String[] TYPES={"awake","light","deep","rem","sleeping","unknown"};
    static final class Segment {final long start,end;final String type;Segment(long start,long end,String type){this.start=start;this.end=end;this.type=type;}}
    static String stageType(int value){
        switch(value){case SleepSessionRecord.StageType.STAGE_TYPE_AWAKE:case SleepSessionRecord.StageType.STAGE_TYPE_AWAKE_IN_BED:case SleepSessionRecord.StageType.STAGE_TYPE_AWAKE_OUT_OF_BED:return "awake";
            case SleepSessionRecord.StageType.STAGE_TYPE_SLEEPING_LIGHT:return "light";case SleepSessionRecord.StageType.STAGE_TYPE_SLEEPING_DEEP:return "deep";
            case SleepSessionRecord.StageType.STAGE_TYPE_SLEEPING_REM:return "rem";case SleepSessionRecord.StageType.STAGE_TYPE_SLEEPING:return "sleeping";default:return "unknown";}
    }
    static List<Segment> normalize(long bed,long wake,List<Segment> raw){
        if(bed<=0||wake<=bed||wake-bed>24L*60*60*1000||raw.size()>MAX_STAGES)throw new IllegalArgumentException("Sleep range");
        TreeMap<Long,int[]> events=new TreeMap<>();
        for(Segment stage:raw){long start=Math.max(bed,stage.start),end=Math.min(wake,stage.end);if(end<=start)continue;
            int type=Arrays.asList(TYPES).indexOf(stage.type);if(type<0)type=5;
            events.computeIfAbsent(start,t->new int[6])[type]++;events.computeIfAbsent(end,t->new int[6])[type]--;
        }
        ArrayList<Segment> result=new ArrayList<>();int[] active=new int[6];Long previous=null;
        for(Map.Entry<Long,int[]> event:events.entrySet()){
            long current=event.getKey();if(previous!=null&&current>previous){int chosen=-1,count=0;
                for(int i=0;i<active.length;i++)if(active[i]>0){chosen=i;count++;}
                // Known awake intervals form one union; conflicting sleep types stay unknown.
                if(active[0]>0)chosen=0;else if(count>1)chosen=5;
                if(chosen>=0){String type=TYPES[chosen];if(!result.isEmpty()&&result.get(result.size()-1).end==previous&&result.get(result.size()-1).type.equals(type)){
                    Segment last=result.remove(result.size()-1);result.add(new Segment(last.start,current,type));
                }else result.add(new Segment(previous,current,type));}
            }
            for(int i=0;i<active.length;i++)active[i]+=event.getValue()[i];previous=current;
        }
        if(result.size()>MAX_STAGES)throw new IllegalArgumentException("Too many normalized stages");return result;
    }
    static long awakeMillis(List<Segment> stages){long sum=0;for(Segment stage:stages)if(stage.type.equals("awake"))sum+=stage.end-stage.start;return sum;}
    static String clean(String raw,int max){if(raw==null)return "";String value=raw.replaceAll("[\\p{Cntrl}]"," ").trim();return value.length()<=max?value:value.substring(0,max).trim();}
    static String method(int method){switch(method){case Metadata.RECORDING_METHOD_ACTIVELY_RECORDED:return "active";case Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED:return "automatic";case Metadata.RECORDING_METHOD_MANUAL_ENTRY:return "manual";default:return "unknown";}}
    static JSONObject sleep(SleepSessionRecord record,List<Segment> stages)throws JSONException {
        JSONArray data=new JSONArray();for(Segment stage:stages)data.put(new JSONObject().put("start",stage.start).put("end",stage.end).put("type",stage.type));
        Metadata metadata=record.getMetadata();String origin=metadata.getDataOrigin()==null?"":clean(metadata.getDataOrigin().getPackageName(),200);
        JSONObject out=new JSONObject().put("stages",data).put("recordingMethod",method(metadata.getRecordingMethod()));if(!origin.isEmpty())out.put("originApp",origin);
        Device device=metadata.getDevice();if(device!=null){String maker=clean(device.getManufacturer(),100),model=clean(device.getModel(),100);String name=clean((maker+" "+model).trim(),200);if(!name.isEmpty())out.put("deviceName",name);}
        return out;
    }
    static List<Segment> stages(SleepSessionRecord record){List<Segment> raw=new ArrayList<>();for(SleepSessionRecord.Stage stage:record.getStages())raw.add(new Segment(stage.getStartTime().toEpochMilli(),stage.getEndTime().toEpochMilli(),stageType(stage.getType())));return normalize(record.getStartTime().toEpochMilli(),record.getEndTime().toEpochMilli(),raw);}
}
