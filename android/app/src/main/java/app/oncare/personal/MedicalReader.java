package app.oncare.personal;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ext.SdkExtensions;
import android.os.OutcomeReceiver;
import android.health.connect.*;
import android.health.connect.datatypes.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
/** Optional personal health record reads. FHIR is preserved, never rendered as HTML or used for prescriptions. */
@android.annotation.TargetApi(34)
final class MedicalReader {
    static final class Type { final int code; final String key,title,permission; Type(int c,String k,String t){code=c;key="Medical"+k;title=t;permission="android.permission.health.READ_MEDICAL_DATA_"+k;}boolean granted(Context c){return supported()&&c.checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED;} }
    static final Type[] TYPES={new Type(2,"ALLERGIES_INTOLERANCES","알레르기·불내성"),new Type(7,"CONDITIONS","진단·질환 기록"),new Type(6,"LABORATORY_RESULTS","검사 결과"),new Type(9,"MEDICATIONS","의료 투약 기록"),new Type(10,"PERSONAL_DETAILS","의료 개인 정보"),new Type(11,"PRACTITIONER_DETAILS","의료진 정보"),new Type(3,"PREGNANCY","임신 기록"),new Type(8,"PROCEDURES","시술·처치 기록"),new Type(4,"SOCIAL_HISTORY","의료 생활 이력"),new Type(1,"VACCINES","예방 접종"),new Type(12,"VISITS","진료 기록"),new Type(5,"VITAL_SIGNS","의료 생체 기록")};
    static boolean supported(){return Build.VERSION.SDK_INT>=36||(Build.VERSION.SDK_INT>=34&&SdkExtensions.getExtensionVersion(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)>=16);}
    static String[] permissions(){return supported()?Arrays.stream(TYPES).map(t->t.permission).toArray(String[]::new):new String[0];}
    // Guard both platform API 36 and U extension 16. AGP 8.9 cannot model the alternative SDK paths.
    @android.annotation.SuppressLint("NewApi")
    static JSONArray read(Context context,Type type,long captured)throws Exception {
        if(!supported()||!type.granted(context))throw new SecurityException("Medical feature or permission unavailable");
        HealthConnectManager manager=context.getSystemService(HealthConnectManager.class);JSONArray records=new JSONArray();ReadMedicalResourcesRequest request=new ReadMedicalResourcesInitialRequest.Builder(type.code).setPageSize(100).build();Set<String> seenTokens=new HashSet<>();
        while(true){if(Thread.currentThread().isInterrupted())throw new InterruptedException();CompletableFuture<ReadMedicalResourcesResponse> future=new CompletableFuture<>();manager.readMedicalResources(request,Runnable::run,new OutcomeReceiver<ReadMedicalResourcesResponse,HealthConnectException>(){public void onResult(ReadMedicalResourcesResponse result){future.complete(result);}public void onError(HealthConnectException error){future.completeExceptionally(error);}});ReadMedicalResourcesResponse response=future.get(20,TimeUnit.SECONDS);for(MedicalResource resource:response.getMedicalResources()){records.put(encode(resource,type,captured));if(records.length()>10000)throw new IllegalArgumentException("Too many documents");}String token=response.getNextPageToken();if(token==null)break;if(!seenTokens.add(token))throw new IllegalArgumentException("Repeated page token");request=new ReadMedicalResourcesPageRequest.Builder(token).setPageSize(100).build();}return records;
    }
    @android.annotation.SuppressLint("NewApi")
    static JSONObject encode(MedicalResource resource,Type type,long captured)throws Exception {FhirResource fhir=resource.getFhirResource();String data=fhir.getData();if(data.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>250000)throw new IllegalArgumentException("Document too large");JSONObject body=new JSONObject(data);validate(body,1,new int[]{0});JSONObject fields=new JSONObject().put("capturedAt",captured).put("dataSourceId",resource.getDataSourceId()).put("fhirVersion",resource.getFhirVersion().toString()).put("fhirResourceType",fhir.getType()).put("fhir",body);String identity=type.key+":"+resource.getDataSourceId()+":"+fhir.getType()+":"+fhir.getId();return new JSONObject().put("id","hc:"+LocalSyncServer.digest(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8))).put("recordType",type.key).put("date",captured).put("title",type.title).put("category","의료").put("fields",fields);}
    static void validate(Object value,int depth,int[] nodes)throws Exception {if(depth>12||++nodes[0]>100000)throw new IllegalArgumentException("Document too complex");if(value instanceof JSONObject){JSONObject o=(JSONObject)value;Iterator<String> keys=o.keys();while(keys.hasNext()){String k=keys.next();if(k.length()>100)throw new IllegalArgumentException("Field name too long");validate(o.get(k),depth+1,nodes);}}else if(value instanceof JSONArray){JSONArray a=(JSONArray)value;for(int i=0;i<a.length();i++)validate(a.get(i),depth+1,nodes);}else if(value instanceof String&&((String)value).length()>100000)throw new IllegalArgumentException("Text too long");else if(value instanceof Number&&!Double.isFinite(((Number)value).doubleValue()))throw new IllegalArgumentException("Non-finite value");}
}
