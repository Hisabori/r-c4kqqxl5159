package app.oncare.personal;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import org.json.JSONObject;
import java.util.*;

final class PersonalShareChecks {
    static void require(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    static void run(Instrumentation test) throws Exception {
        String code="oncare1:"+android.util.Base64.encodeToString(("{\"version\":1,\"host\":\"192.168.1.4\",\"port\":8766,\"peer\":\""+UUID.randomUUID()+"\",\"token\":\""+String.join("",Collections.nCopies(43,"a"))+"\",\"fingerprint\":\""+String.join("",Collections.nCopies(64,"b"))+"\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8),android.util.Base64.URL_SAFE|android.util.Base64.NO_PADDING|android.util.Base64.NO_WRAP);
        Bitmap qr=PairQR.image(code,600);int[] pixels=new int[600*600];qr.getPixels(pixels,0,600,0,0,600,600);
        String decoded=new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(600,600,pixels)))).getText();require(decoded.equals(code),"QR payload altered");
        try {PairQR.image("https://example.com",600);throw new AssertionError("Non-pair accepted");}catch(IllegalArgumentException expected){}
        long now=System.currentTimeMillis();ArrayList<JSONObject> records=new ArrayList<>();records.add(new JSONObject().put("kind","운동").put("date",now).put("value",20).put("note","PRIVATE NOTE").put("source","PRIVATE DEVICE"));records.add(new JSONObject().put("kind","운동").put("date",now-31L*86400000).put("value",99));records.add(new JSONObject().put("kind","운동").put("date",now+10000).put("value",99));records.add(new JSONObject().put("kind","체중").put("date",now).put("value",70));
        String report=PersonalShareReport.text(records,new HashSet<>(Arrays.asList("운동","수면")),7," 민지\n ",now);
        require(report.contains("운동 합계: 20 분")&&report.contains("수면: 기록 없음")&&report.contains("이름: 민지"),"Report aggregation");for(String secret:new String[]{"PRIVATE","체중","99","oncare1:"})require(!report.contains(secret),"Unexpected share field "+secret);
        records.clear();records.add(new JSONObject().put("kind","수면").put("date",now-1000).put("value",2));records.add(new JSONObject().put("kind","수면").put("date",now).put("value",8));require(PersonalShareReport.text(records,Collections.singleton("수면"),7,"",now).contains("수면 평균: 8 시간 · 1일 기록"),"Daily last mean");
        require(PersonalShareReport.version(test.getTargetContext()).contains("3.17.0 · 빌드 26"),"Installed version");
        EnhancedActivity a=(EnhancedActivity)test.startActivitySync(new Intent(test.getTargetContext(),EnhancedActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));final Throwable[] failure={null};
        test.runOnMainSync(()->{try{a.personalShareSheet();android.view.View decor=a.activeSheet.getWindow().getDecorView();require(!((android.widget.CheckBox)MedicationChecks.find(decor,"share-kind-마음")).isChecked(),"Mood default");require(!((android.widget.CheckBox)MedicationChecks.find(decor,"share-kind-체중")).isChecked(),"Weight default");MedicationChecks.find(decor,"share-preview").performClick();}catch(Throwable e){failure[0]=e;}});if(failure[0]!=null)throw new AssertionError(failure[0]);WidgetRouteChecks.capture(test,test.getTargetContext(),"23-personal-share-preview");test.runOnMainSync(a::finish);
        HealthSyncActivity connection=(HealthSyncActivity)test.startActivitySync(new Intent(test.getTargetContext(),HealthSyncActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));test.runOnMainSync(()->{try{connection.showPairQRImage(code);}catch(Throwable e){failure[0]=e;}});if(failure[0]!=null)throw new AssertionError(failure[0]);WidgetRouteChecks.capture(test,test.getTargetContext(),"24-qr-connection-demo");test.runOnMainSync(connection::finish);
    }
}
