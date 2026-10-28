package app.oncare.personal;
import android.annotation.TargetApi;
import android.content.Context;
import android.health.connect.datatypes.SleepSessionRecord;
import org.json.*;
import java.time.Instant;
import java.util.*;
@TargetApi(34) final class SleepSource {
 static List<JSONObject> read(Context context)throws Exception{HealthReader reader=new HealthReader(context);if(!reader.allowed(2))throw new SecurityException("수면 읽기 권한을 먼저 허용해주세요.");Instant end=Instant.now(),start=end.minus(30,java.time.temporal.ChronoUnit.DAYS);List<JSONObject> out=new ArrayList<>();for(SleepSessionRecord raw:reader.read(SleepSessionRecord.class,start,end)){JSONObject e=HealthReader.sleepRecord(raw).put("source","health-connect:android");SleepTools.validate(e);out.add(e);}return out;}
}
