package app.oncare.personal;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.health.connect.*;
import android.health.connect.datatypes.*;
import android.health.connect.datatypes.Record;
import android.os.OutcomeReceiver;
import org.json.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Read-only Health Connect access while the Android app is visible. */
@TargetApi(34)
final class HealthReader {
    static final String[] PERMISSIONS={"android.permission.health.READ_WEIGHT","android.permission.health.READ_EXERCISE","android.permission.health.READ_SLEEP","android.permission.health.READ_HYDRATION","android.permission.health.READ_STEPS"};
    static final String[] KINDS={"체중","운동","수면","물","걸음"};
    private final Context context;
    private final HealthConnectManager manager;
    private final Executor callbackExecutor=Runnable::run;
    HealthReader(Context context){this.context=context;manager=context.getSystemService(HealthConnectManager.class);}
    boolean allowed(int n){return context.checkSelfPermission(PERMISSIONS[n])==PackageManager.PERMISSION_GRANTED;}
    List<String> grantedKinds(){List<String> out=new ArrayList<>();for(int i=0;i<KINDS.length;i++)if(allowed(i))out.add(KINDS[i]);for(HealthCatalog.Type t:HealthCatalog.TYPES)if(t.granted(context))out.add("health:"+t.record);for(MedicalReader.Type t:MedicalReader.TYPES)if(t.granted(context))out.add("health:"+t.key);return out;}
    <T extends Record> List<T> read(Class<T> type, Instant start, Instant end)throws Exception {
        List<T> out=new ArrayList<>();long token=-1;
        do {
            CompletableFuture<ReadRecordsResponse<T>> future=new CompletableFuture<>();
            ReadRecordsRequestUsingFilters.Builder<T> builder=new ReadRecordsRequestUsingFilters.Builder<>(type).setTimeRangeFilter(new TimeInstantRangeFilter.Builder().setStartTime(start).setEndTime(end).build()).setPageSize(1000);
            if(token!=-1)builder.setPageToken(token);
            manager.readRecords(builder.build(),callbackExecutor,new OutcomeReceiver<ReadRecordsResponse<T>,HealthConnectException>(){public void onResult(ReadRecordsResponse<T> result){future.complete(result);}public void onError(HealthConnectException error){future.completeExceptionally(error);}});
            ReadRecordsResponse<T> response=future.get(20,TimeUnit.SECONDS);out.addAll(response.getRecords());token=response.getNextPageToken();
            if(out.size()>10000)throw new IllegalStateException("Too many records");
        }while(token!=-1);
        return out;
    }
    static JSONObject record(String id,String kind,long date,double value,String type)throws Exception {
        JSONObject e=new JSONObject().put("id","hc:"+LocalSyncServer.digest((kind+":"+id).getBytes(java.nio.charset.StandardCharsets.UTF_8))).put("kind",kind).put("date",date).put("value",value).put("note","");
        if(type!=null)e.put("type",type);return e;
    }
    static void add(JSONArray records,JSONObject e,long start,long end)throws Exception {
        double value=e.getDouble("value");String kind=e.getString("kind");double max=kind.equals("체중")?500:kind.equals("운동")?1440:kind.equals("수면")?24:kind.equals("걸음")?1000000:20000;
        if(e.getLong("date")>=start&&e.getLong("date")<=end&&value>0&&Double.isFinite(value)&&value<=max)records.put(e);
    }
    static Instant sleepReadStart(Instant windowStart,Instant end){Instant prior=windowStart.minus(1,java.time.temporal.ChronoUnit.DAYS),allowed=end.minus(30,java.time.temporal.ChronoUnit.DAYS);return prior.isBefore(allowed)?allowed:prior;}
    static JSONObject sleepRecord(SleepSessionRecord r)throws Exception {
        long bed=r.getStartTime().toEpochMilli(),wake=r.getEndTime().toEpochMilli();List<SleepExport.Segment> stages=SleepExport.stages(r);
        long awake=SleepExport.awakeMillis(stages);JSONObject entry=record(r.getMetadata().getId(),"수면",wake,(wake-bed-awake)/3600000.0,null);
        return entry.put("bedTime",bed).put("wakeTime",wake).put("awakeMinutes",awake/60000.0).put("sleep",SleepExport.sleep(r,stages));
    }
    static int appendSleepRecords(JSONArray records,List<SleepSessionRecord> sessions,long start,long end)throws Exception {
        int skipped=0;
        for(SleepSessionRecord session:sessions){JSONObject entry;
            // Unsupported source session ranges/stage counts do not block the other health kinds.
            // Permission, I/O and JSON failures still propagate; this only catches conversion limits.
            try{entry=sleepRecord(session);}catch(IllegalArgumentException unsupported){skipped++;continue;}
            add(records,entry,start,end);
        }
        return skipped;
    }

    JSONArray dailySteps()throws Exception {
        if(manager==null||!allowed(4))throw new SecurityException("Step permission required");
        LocalDate first=LocalDate.now().minusDays(6);LocalDateTime end=LocalDateTime.now();
        AggregateRecordsRequest<Long> request=new AggregateRecordsRequest.Builder<Long>(new LocalTimeRangeFilter.Builder().setStartTime(first.atStartOfDay()).setEndTime(end).build()).addAggregationType(StepsRecord.STEPS_COUNT_TOTAL).build();
        CompletableFuture<List<AggregateRecordsGroupedByPeriodResponse<Long>>> future=new CompletableFuture<>();
        manager.aggregateGroupByPeriod(request,Period.ofDays(1),callbackExecutor,new OutcomeReceiver<List<AggregateRecordsGroupedByPeriodResponse<Long>>,HealthConnectException>(){public void onResult(List<AggregateRecordsGroupedByPeriodResponse<Long>> value){future.complete(value);}public void onError(HealthConnectException error){future.completeExceptionally(error);}});
        JSONArray rows=new JSONArray();
        for(AggregateRecordsGroupedByPeriodResponse<Long> day:future.get(20,TimeUnit.SECONDS)){
            Long count=day.get(StepsRecord.STEPS_COUNT_TOTAL);
            if(count!=null&&count>=0&&count<=1000000)rows.put(new JSONObject().put("day",day.getStartTime().toLocalDate().toString()).put("count",count));
        }
        return rows;
    }

    JSONObject browse()throws Exception {if(!grantedKinds().isEmpty())return snapshot("local-browser");JSONArray availability=new JSONArray();for(HealthCatalog.Type t:HealthCatalog.TYPES)availability.put(new JSONObject().put("recordType",t.record).put("title",t.title).put("category",t.category).put("status",t.type()==null?"unsupported":"permission").put("count",0));for(MedicalReader.Type t:MedicalReader.TYPES)availability.put(new JSONObject().put("recordType",t.key).put("title",t.title).put("category","의료").put("status",MedicalReader.supported()?"permission":"unsupported").put("count",0));return new JSONObject().put("availability",availability).put("healthRecords",new JSONArray());}
    JSONObject snapshot(String peer)throws Exception {
        if(manager==null)throw new IllegalStateException("Health Connect unavailable");
        Instant end=Instant.now();LocalDate first=LocalDate.now().minusDays(29);Instant start=first.atStartOfDay(ZoneId.systemDefault()).toInstant();
        JSONArray records=new JSONArray();List<String> kinds=grantedKinds();if(kinds.isEmpty())throw new SecurityException("No health permissions");
        if(allowed(0))for(WeightRecord r:read(WeightRecord.class,start,end))add(records,record(r.getMetadata().getId(),"체중",r.getTime().toEpochMilli(),r.getWeight().getInGrams()/1000,null),start.toEpochMilli(),end.toEpochMilli());
        if(allowed(1))for(ExerciseSessionRecord r:read(ExerciseSessionRecord.class,start,end)) {
            double minutes=Duration.between(r.getStartTime(),r.getEndTime()).toMillis()/60000.0;
            String type=r.getExerciseType()==ExerciseSessionType.EXERCISE_SESSION_TYPE_WALKING?"걷기":r.getExerciseType()==ExerciseSessionType.EXERCISE_SESSION_TYPE_RUNNING?"달리기":"Health Connect 운동";
            add(records,record(r.getMetadata().getId(),"운동",r.getEndTime().toEpochMilli(),minutes,type),start.toEpochMilli(),end.toEpochMilli());
        }
        if(allowed(2))appendSleepRecords(records,read(SleepSessionRecord.class,sleepReadStart(start,end),end),start.toEpochMilli(),end.toEpochMilli());
        if(allowed(3))for(HydrationRecord r:read(HydrationRecord.class,start,end))add(records,record(r.getMetadata().getId(),"물",r.getEndTime().toEpochMilli(),r.getVolume().getInLiters()*1000,null),start.toEpochMilli(),end.toEpochMilli());
        if(allowed(4)) {
            AggregateRecordsRequest<Long> request=new AggregateRecordsRequest.Builder<Long>(new LocalTimeRangeFilter.Builder().setStartTime(first.atStartOfDay()).setEndTime(LocalDateTime.ofInstant(end,ZoneId.systemDefault())).build()).addAggregationType(StepsRecord.STEPS_COUNT_TOTAL).build();
            CompletableFuture<List<AggregateRecordsGroupedByPeriodResponse<Long>>> future=new CompletableFuture<>();
            manager.aggregateGroupByPeriod(request,Period.ofDays(1),callbackExecutor,new OutcomeReceiver<List<AggregateRecordsGroupedByPeriodResponse<Long>>,HealthConnectException>(){public void onResult(List<AggregateRecordsGroupedByPeriodResponse<Long>> result){future.complete(result);}public void onError(HealthConnectException error){future.completeExceptionally(error);}});
            for(AggregateRecordsGroupedByPeriodResponse<Long> day:future.get(20,TimeUnit.SECONDS)) {Long count=day.get(StepsRecord.STEPS_COUNT_TOTAL);if(count!=null&&count>0){String key=day.getStartTime().toLocalDate().toString();add(records,record("daily-steps-"+key,"걸음",day.getStartTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),count,null),start.toEpochMilli(),end.toEpochMilli());}}
        }
        if(records.length()>10000)throw new IllegalStateException("Too many records");
        JSONArray healthRecords=new JSONArray(),availability=new JSONArray();int rawBytes=0;
        for(HealthCatalog.Type t:HealthCatalog.TYPES){if(Thread.currentThread().isInterrupted())throw new InterruptedException();String status=t.type()==null?"unsupported":!t.granted(context)?"permission":"ready";int count=0;
            if(status.equals("ready")){try{JSONArray batch=new JSONArray();for(Record raw:read(t.type(),start,end)){JSONObject item=HealthCatalog.encode(raw,t);if(raw instanceof ExerciseSessionRecord){String cached=context.getSharedPreferences("health-routes",Context.MODE_PRIVATE).getString(raw.getMetadata().getId(),null);if(cached!=null&&((ExerciseSessionRecord)raw).getRoute()==null)item.getJSONObject("fields").put("route",new JSONObject(cached));}long date=item.getLong("date");if(date>=start.toEpochMilli()&&date<=end.toEpochMilli())batch.put(item);}if(healthRecords.length()+batch.length()>10000)throw new IllegalArgumentException("Snapshot record limit");int bytes=batch.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;if(rawBytes+bytes>4*1024*1024)throw new IllegalArgumentException("Snapshot byte limit");rawBytes+=bytes;for(int i=0;i<batch.length();i++)healthRecords.put(batch.get(i));count=batch.length();status=count==0?"empty":"ready";}catch(Exception e){if(e instanceof InterruptedException)throw e;status="error";kinds.remove("health:"+t.record);}}
            availability.put(new JSONObject().put("recordType",t.record).put("title",t.title).put("category",t.category).put("status",status).put("count",count));
        }
        for(MedicalReader.Type t:MedicalReader.TYPES){if(Thread.currentThread().isInterrupted())throw new InterruptedException();String status=!MedicalReader.supported()?"unsupported":!t.granted(context)?"permission":"ready";int count=0;if(status.equals("ready")){try{JSONArray batch=MedicalReader.read(context,t,end.toEpochMilli());if(healthRecords.length()+batch.length()>10000)throw new IllegalArgumentException("Snapshot limit");int bytes=batch.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;if(rawBytes+bytes>4*1024*1024)throw new IllegalArgumentException("Snapshot byte limit");rawBytes+=bytes;for(int i=0;i<batch.length();i++)healthRecords.put(batch.get(i));count=batch.length();status=count==0?"empty":"ready";}catch(Exception e){if(e instanceof InterruptedException)throw e;status="error";kinds.remove("health:"+t.key);}}availability.put(new JSONObject().put("recordType",t.key).put("title",t.title).put("category","의료").put("status",status).put("count",count));}
        return new JSONObject().put("healthRecords",healthRecords).put("availability",availability).put("version",1).put("peer",peer).put("generatedAt",Instant.now().toEpochMilli()).put("windowStart",start.toEpochMilli()).put("kinds",new JSONArray(kinds)).put("entries",records);
    }
}
