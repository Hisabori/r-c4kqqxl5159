package app.oncare.personal;

import android.health.connect.datatypes.*;
import org.json.*;
import java.time.Instant;
import java.util.*;

final class SleepExportChecks {
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static JSONObject run()throws Exception {
        Instant rangeEnd=Instant.parse("2026-10-20T10:00:00Z"),rangeStart=Instant.parse("2026-09-21T00:00:00Z");
        require(HealthReader.sleepReadStart(rangeStart,rangeEnd).equals(rangeEnd.minus(30,java.time.temporal.ChronoUnit.DAYS)),"Sleep lookback exceeded 30-day permission range");
        require(HealthReader.sleepReadStart(rangeStart,rangeEnd).isBefore(rangeStart),"Prior-night sleep was not requested");
        long bed=1_000_000,wake=bed+60*60*1000;
        List<SleepExport.Segment> normalized=SleepExport.normalize(bed,wake,Arrays.asList(new SleepExport.Segment(bed-1000,bed+10*60*1000,"awake"),new SleepExport.Segment(bed+5*60*1000,bed+15*60*1000,"awake"),new SleepExport.Segment(bed, wake+1000,"light"),new SleepExport.Segment(bed+5*60*1000,bed+15*60*1000,"awake")));
        require(SleepExport.awakeMillis(normalized)==15*60*1000,"Overlapping awake intervals subtracted twice");
        require(normalized.size()==2&&normalized.get(0).start==bed&&normalized.get(0).end==bed+15*60*1000&&normalized.get(1).end==wake,"Clip/sort/merge failed");
        List<SleepExport.Segment> conflict=SleepExport.normalize(bed,wake,Arrays.asList(new SleepExport.Segment(bed,bed+30*60*1000,"light"),new SleepExport.Segment(bed+20*60*1000,bed+40*60*1000,"deep")));
        require(conflict.size()==3&&conflict.get(1).type.equals("unknown")&&conflict.get(2).end==bed+40*60*1000,"Conflicting stages or uncovered gap fabricated");
        require(SleepExport.normalize(bed,wake,new ArrayList<>()).isEmpty(),"Missing stages filled with unknown");
        long nativeWake=System.currentTimeMillis()-60_000,nativeBed=nativeWake-7*60*60*1000;
        Metadata metadata=new Metadata.Builder().setId(UUID.randomUUID().toString()).setDataOrigin(new DataOrigin.Builder().setPackageName("com.sec.android.app.shealth").build()).setDevice(new Device.Builder().setManufacturer("Samsung").setModel("Galaxy Watch6").setType(Device.DEVICE_TYPE_WATCH).build()).setRecordingMethod(Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED).build();
        SleepSessionRecord record=new SleepSessionRecord.Builder(metadata,Instant.ofEpochMilli(nativeBed),Instant.ofEpochMilli(nativeWake)).setNotes("PRIVATE_HEALTH_NOTE").setTitle("PRIVATE_SLEEP_TITLE").setStages(Arrays.asList(new SleepSessionRecord.Stage(Instant.ofEpochMilli(nativeBed),Instant.ofEpochMilli(nativeBed+60*60*1000),SleepSessionRecord.StageType.STAGE_TYPE_SLEEPING_DEEP),new SleepSessionRecord.Stage(Instant.ofEpochMilli(nativeBed+60*60*1000),Instant.ofEpochMilli(nativeBed+90*60*1000),SleepSessionRecord.StageType.STAGE_TYPE_AWAKE),new SleepSessionRecord.Stage(Instant.ofEpochMilli(nativeBed+90*60*1000),Instant.ofEpochMilli(nativeWake),SleepSessionRecord.StageType.STAGE_TYPE_SLEEPING_REM))).build();
        SleepSessionRecord overlong=new SleepSessionRecord.Builder(metadata,Instant.ofEpochMilli(nativeWake-25L*60*60*1000),Instant.ofEpochMilli(nativeWake)).build();
        List<SleepSessionRecord.Stage> excessive=new ArrayList<>();for(int i=0;i<2049;i++)excessive.add(new SleepSessionRecord.Stage(Instant.ofEpochMilli(nativeBed+i*1000L),Instant.ofEpochMilli(nativeBed+(i+1)*1000L),SleepSessionRecord.StageType.STAGE_TYPE_SLEEPING_LIGHT));
        SleepSessionRecord tooMany=new SleepSessionRecord.Builder(metadata,Instant.ofEpochMilli(nativeBed),Instant.ofEpochMilli(nativeWake)).setStages(excessive).build();
        JSONArray snapshotEntries=new JSONArray();long rangeFrom=nativeBed-30L*24*60*60*1000,rangeTo=System.currentTimeMillis();
        HealthReader.add(snapshotEntries,HealthReader.record("weight-fixture","체중",nativeWake,70,null),rangeFrom,rangeTo);
        HealthReader.add(snapshotEntries,HealthReader.record("exercise-fixture","운동",nativeWake,30,"걷기"),rangeFrom,rangeTo);
        int skipped=HealthReader.appendSleepRecords(snapshotEntries,Arrays.asList(overlong,record,tooMany),rangeFrom,rangeTo);
        HealthReader.add(snapshotEntries,HealthReader.record("water-fixture","물",nativeWake,500,null),rangeFrom,rangeTo);
        HealthReader.add(snapshotEntries,HealthReader.record("steps-fixture","걸음",nativeWake,1000,null),rangeFrom,rangeTo);
        require(skipped==2&&snapshotEntries.length()==5,"Unsupported sleep session blocked the shared snapshot array");
        Set<String> retainedKinds=new HashSet<>();for(int i=0;i<snapshotEntries.length();i++)retainedKinds.add(snapshotEntries.getJSONObject(i).getString("kind"));
        require(retainedKinds.containsAll(Arrays.asList("체중","운동","수면","물","걸음")),"Normal health kinds were lost when sleep conversion skipped a record");
        JSONObject entry=HealthReader.sleepRecord(record),sleep=entry.getJSONObject("sleep");
        require(entry.getDouble("value")==6.5&&entry.getDouble("awakeMinutes")==30&&entry.getLong("bedTime")==nativeBed&&entry.getLong("wakeTime")==nativeWake,"Native sleep duration metadata");
        require(sleep.getString("originApp").equals("com.sec.android.app.shealth")&&sleep.getString("deviceName").equals("Samsung Galaxy Watch6")&&sleep.getString("recordingMethod").equals("automatic"),"Source metadata lost");
        require(sleep.getJSONArray("stages").length()==3&&sleep.getJSONArray("stages").getJSONObject(2).getString("type").equals("rem"),"Native stages lost");
        require(!entry.toString().contains("PRIVATE_"),"Private source note forwarded");
        Metadata unknown=new Metadata.Builder().setId(UUID.randomUUID().toString()).setDataOrigin(new DataOrigin.Builder().setPackageName("app.other.sleep").build()).build();
        JSONObject empty=HealthReader.sleepRecord(new SleepSessionRecord.Builder(unknown,Instant.ofEpochMilli(nativeBed),Instant.ofEpochMilli(nativeWake)).build());
        require(empty.getDouble("value")==7&&empty.getDouble("awakeMinutes")==0&&empty.getJSONObject("sleep").getJSONArray("stages").length()==0&&!empty.getJSONObject("sleep").has("deviceName"),"Missing metadata or stages fabricated");
        Metadata noOrigin=new Metadata.Builder().setId(UUID.randomUUID().toString()).build();
        JSONObject noOriginEntry=HealthReader.sleepRecord(new SleepSessionRecord.Builder(noOrigin,Instant.ofEpochMilli(nativeBed),Instant.ofEpochMilli(nativeWake)).build());
        require(!noOriginEntry.getJSONObject("sleep").has("originApp"),"Empty origin breaks iPhone optional-string contract");
        JSONObject partial=HealthReader.sleepRecord(new SleepSessionRecord.Builder(unknown,Instant.ofEpochMilli(nativeBed),Instant.ofEpochMilli(nativeWake)).setStages(Collections.singletonList(new SleepSessionRecord.Stage(Instant.ofEpochMilli(nativeBed),Instant.ofEpochMilli(nativeBed+60*60*1000),SleepSessionRecord.StageType.STAGE_TYPE_UNKNOWN))).build());
        require(partial.getJSONObject("sleep").getJSONArray("stages").length()==1&&partial.getJSONObject("sleep").getJSONArray("stages").getJSONObject(0).getString("type").equals("unknown")&&partial.getDouble("value")==7,"Partial unknown stage coverage altered duration");
        entry.put("source","original-hc-source");JSONObject edited=CareActivity.editedRecord(entry,"수면",entry.getDouble("value"),entry.getLong("date"),"new note");
        require(edited.getJSONObject("sleep").toString().equals(sleep.toString())&&edited.getString("source").equals("original-hc-source")&&edited.getDouble("awakeMinutes")==30,"Memo editing dropped sleep details");
        JSONObject changed=CareActivity.editedRecord(entry,"수면",6,entry.getLong("date"),"new note");
        for(String key:new String[]{"sleep","source","bedTime","wakeTime","awakeMinutes"})require(!changed.has(key),"Changed value retained stale "+key);
        changed=CareActivity.editedRecord(entry,"수면",6.5,entry.getLong("date")-1000,"");require(!changed.has("sleep"),"Changed date retained stale stages");
        JSONObject fractional=new JSONObject(entry.toString());for(String key:new String[]{"date","bedTime","wakeTime"})fractional.put(key,fractional.getDouble(key)+0.25);
        JSONArray fractionalStages=fractional.getJSONObject("sleep").getJSONArray("stages");for(int i=0;i<fractionalStages.length();i++){JSONObject stage=fractionalStages.getJSONObject(i);stage.put("start",stage.getDouble("start")+0.25).put("end",stage.getDouble("end")+0.25);}
        JSONObject memoEdit=CareActivity.editedRecord(fractional,"수면",fractional.getDouble("value"),fractional.optLong("date"),"memo only");
        require(memoEdit.getDouble("date")==fractional.getDouble("date")&&memoEdit.getDouble("wakeTime")==memoEdit.getDouble("date")&&memoEdit.getJSONObject("sleep").toString().equals(fractional.getJSONObject("sleep").toString()),"Memo edit truncated iPhone fractional timestamp");
        return memoEdit;
    }
}
