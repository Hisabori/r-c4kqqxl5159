package app.oncare.personal;
import android.content.Context;
import android.content.pm.PackageManager;
import android.health.connect.datatypes.Record;
import java.lang.reflect.*;
import java.time.*;
import java.util.*;
import org.json.*;
/** Explicit public record inventory. No writes, internal fields or unrelated classes. */
@android.annotation.TargetApi(34)
final class HealthCatalog {
    static final class Type {
        final String record,permission,title,category;
        Type(String r,String p,String t,String c){record=r;permission="android.permission.health.READ_"+p;title=t;category=c;}
        @SuppressWarnings("unchecked") Class<? extends Record> type(){try{return (Class<? extends Record>)Class.forName("android.health.connect.datatypes."+record).asSubclass(Record.class);}catch(ClassNotFoundException|LinkageError e){return null;}}
        boolean granted(Context c){return type()!=null&&c.checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED;}
    }
    static final Type[] TYPES={
        new Type("ActiveCaloriesBurnedRecord","ACTIVE_CALORIES_BURNED","활동 칼로리","활동"),
        new Type("BasalBodyTemperatureRecord","BASAL_BODY_TEMPERATURE","기초 체온","주기"),
        new Type("BasalMetabolicRateRecord","BASAL_METABOLIC_RATE","기초 대사량","신체"),
        new Type("BloodGlucoseRecord","BLOOD_GLUCOSE","혈당","생체"),
        new Type("BloodPressureRecord","BLOOD_PRESSURE","혈압","생체"),
        new Type("BodyFatRecord","BODY_FAT","체지방","신체"),
        new Type("BodyTemperatureRecord","BODY_TEMPERATURE","체온","생체"),
        new Type("BodyWaterMassRecord","BODY_WATER_MASS","체수분량","신체"),
        new Type("BoneMassRecord","BONE_MASS","골량","신체"),
        new Type("CervicalMucusRecord","CERVICAL_MUCUS","자궁경부 점액","주기"),
        new Type("CyclingPedalingCadenceRecord","EXERCISE","자전거 케이던스","활동"),
        new Type("DistanceRecord","DISTANCE","이동 거리","활동"),
        new Type("ElevationGainedRecord","ELEVATION_GAINED","상승 고도","활동"),
        new Type("ExerciseSessionRecord","EXERCISE","운동 세션","활동"),
        new Type("FloorsClimbedRecord","FLOORS_CLIMBED","오른 층수","활동"),
        new Type("HeartRateRecord","HEART_RATE","심박수","생체"),
        new Type("HeartRateVariabilityRmssdRecord","HEART_RATE_VARIABILITY","심박 변이도","생체"),
        new Type("HeightRecord","HEIGHT","키","신체"),
        new Type("HydrationRecord","HYDRATION","수분 섭취","영양"),
        new Type("IntermenstrualBleedingRecord","INTERMENSTRUAL_BLEEDING","부정 출혈","주기"),
        new Type("LeanBodyMassRecord","LEAN_BODY_MASS","제지방량","신체"),
        new Type("MenstruationFlowRecord","MENSTRUATION","월경량","주기"),
        new Type("MenstruationPeriodRecord","MENSTRUATION","월경 기간","주기"),
        new Type("NutritionRecord","NUTRITION","영양 섭취","영양"),
        new Type("OvulationTestRecord","OVULATION_TEST","배란 검사","주기"),
        new Type("OxygenSaturationRecord","OXYGEN_SATURATION","산소포화도","생체"),
        new Type("PowerRecord","POWER","운동 파워","활동"),
        new Type("RespiratoryRateRecord","RESPIRATORY_RATE","호흡수","생체"),
        new Type("RestingHeartRateRecord","RESTING_HEART_RATE","안정 시 심박","생체"),
        new Type("SexualActivityRecord","SEXUAL_ACTIVITY","성생활","주기"),
        new Type("SleepSessionRecord","SLEEP","수면 단계","수면"),
        new Type("SpeedRecord","SPEED","이동 속도","활동"),
        new Type("StepsCadenceRecord","STEPS","보행 케이던스","활동"),
        new Type("StepsRecord","STEPS","걸음 원본","활동"),
        new Type("TotalCaloriesBurnedRecord","TOTAL_CALORIES_BURNED","총 칼로리","활동"),
        new Type("Vo2MaxRecord","VO2_MAX","최대 산소 섭취량","생체"),
        new Type("WeightRecord","WEIGHT","체중","신체"),
        new Type("SkinTemperatureRecord","SKIN_TEMPERATURE","피부 온도","생체"),
        new Type("PlannedExerciseSessionRecord","PLANNED_EXERCISE","운동 계획","활동"),
        new Type("MindfulnessSessionRecord","MINDFULNESS","마음챙김","마음"),
        new Type("WheelchairPushesRecord","WHEELCHAIR_PUSHES","휠체어 추진 횟수","활동"),
        new Type("ActivityIntensityRecord","ACTIVITY_INTENSITY","활동 강도","활동")
    };
    static String[] permissions(){return Arrays.stream(TYPES).filter(t->t.type()!=null).map(t->t.permission).distinct().toArray(String[]::new);}
    static JSONObject encode(Record r,Type t)throws Exception {
        JSONObject fields=(JSONObject)value(r,0,new int[]{0});
        Object date=fields.opt("time");if(!(date instanceof Number))date=fields.opt("endTime");
        if(!(date instanceof Number))throw new IllegalArgumentException("Missing record time");
        return new JSONObject().put("id","hc:"+LocalSyncServer.digest((t.record+":"+r.getMetadata().getId()).getBytes(java.nio.charset.StandardCharsets.UTF_8))).put("recordType",t.record).put("date",date).put("title",t.title).put("category",t.category).put("fields",fields);
    }
    static String fmt(double n){String text=String.format(java.util.Locale.KOREA,"%.1f",n);return text.endsWith(".0")?text.substring(0,text.length()-2):text;}
    static Double measure(JSONObject fields,String key,String unit){Object value=fields.opt(key);if(unit!=null&&value instanceof JSONObject)value=((JSONObject)value).opt(unit);return value instanceof Number?((Number)value).doubleValue():null;}
    static String summary(JSONObject record){JSONObject fields=record.optJSONObject("fields");if(fields==null)return "원본 상세 보기";String type=record.optString("recordType");if(type.startsWith("Medical"))return "원본 의료 문서";if(type.equals("BloodPressureRecord")){Double a=measure(fields,"systolic","inMillimetersOfMercury"),b=measure(fields,"diastolic","inMillimetersOfMercury");if(a!=null&&b!=null)return fmt(a)+" / "+fmt(b)+" mmHg";}String key=null,property=null,unit="";double factor=1;switch(type){case "WeightRecord":key="weight";property="inGrams";factor=0.001;unit="kg";break;case "BoneMassRecord":case "LeanBodyMassRecord":case "BodyWaterMassRecord":key="mass";property="inGrams";factor=0.001;unit="kg";break;case "HeightRecord":key="height";property="inMeters";factor=100;unit="cm";break;case "BloodGlucoseRecord":key="level";property="inMillimolesPerLiter";unit="mmol/L";break;case "RestingHeartRateRecord":key="beatsPerMinute";unit="bpm";break;case "HeartRateVariabilityRmssdRecord":key="heartRateVariabilityMillis";unit="ms";break;case "RespiratoryRateRecord":key="rate";unit="회/분";break;case "BodyFatRecord":case "OxygenSaturationRecord":key="percentage";property="value";unit="%";break;case "BodyTemperatureRecord":case "BasalBodyTemperatureRecord":key="temperature";property="inCelsius";unit="℃";break;case "HydrationRecord":key="volume";property="inLiters";factor=1000;unit="ml";break;case "ActiveCaloriesBurnedRecord":case "TotalCaloriesBurnedRecord":case "NutritionRecord":key="energy";property="inCalories";factor=0.001;unit="kcal";break;case "DistanceRecord":key="distance";property="inMeters";factor=0.001;unit="km";break;case "StepsRecord":key="count";unit="걸음";break;case "WheelchairPushesRecord":key="count";unit="회";break;case "HeartRateRecord":JSONArray samples=fields.optJSONArray("samples");JSONObject latest=null;for(int i=0;samples!=null&&i<samples.length();i++){JSONObject sample=samples.optJSONObject(i);if(sample!=null&&(latest==null||sample.optLong("time")>latest.optLong("time")))latest=sample;}if(latest!=null)return "최근 샘플 "+fmt(latest.optDouble("beatsPerMinute"))+" bpm";break;}Double n=key==null?null:measure(fields,key,property);return n==null?"원본 상세 보기":fmt(n*factor)+" "+unit;}
    static Object value(Object v,int depth,int[] nodes)throws Exception {
        if(v==null)return JSONObject.NULL;
        if(depth>12||++nodes[0]>100000)throw new IllegalArgumentException("Record too complex");
        if(v instanceof Number){if(!Double.isFinite(((Number)v).doubleValue()))throw new IllegalArgumentException("Non-finite value");return v;}
        if(v instanceof Boolean)return v;
        if(v instanceof CharSequence){String s=v.toString();if(s.length()>2000)throw new IllegalArgumentException("Text too long");return s;}
        if(v instanceof Instant)return ((Instant)v).toEpochMilli();
        if(v instanceof Duration)return ((Duration)v).toMillis();
        if(v instanceof ZoneOffset)return ((ZoneOffset)v).getTotalSeconds();
        if(v instanceof Collection){JSONArray a=new JSONArray();for(Object child:(Collection<?>)v)a.put(value(child,depth+1,nodes));return a;}
        String name=v.getClass().getName();
        if(!name.startsWith("android.health.connect.datatypes."))throw new IllegalArgumentException("Unsupported public field "+name);
        JSONObject out=new JSONObject();Method[] methods=v.getClass().getMethods();Arrays.sort(methods,Comparator.comparing(Method::getName));
        for(Method method:methods){String key=method.getName();if(Modifier.isStatic(method.getModifiers())||method.getParameterCount()!=0||key.equals("getClass")||!(key.startsWith("get")||key.startsWith("has")))continue;
            // Alternative unit conversions are retained with their explicit unit names.
            Object child=method.invoke(v);key=key.startsWith("get")?key.substring(3):key;
            key=Character.toLowerCase(key.charAt(0))+key.substring(1);out.put(key,value(child,depth+1,nodes));
        }if(v instanceof android.health.connect.datatypes.SleepSessionRecord.Stage){String stage=SleepExport.stageType(((android.health.connect.datatypes.SleepSessionRecord.Stage)v).getType());String title=stage.equals("awake")?"깨어 있음":stage.equals("light")?"얕은 수면":stage.equals("deep")?"깊은 수면":stage.equals("rem")?"REM 수면":stage.equals("sleeping")?"수면 · 세부 단계 없음":"미분류";out.put("stageName",title);}return out;
    }
}
