package app.oncare.personal;
import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;
final class HealthFitnessLinks {
    static boolean allowed(String name){return "com.sec.android.app.shealth".equals(name)||"com.google.android.apps.fitness".equals(name);}
    static void open(Activity activity,String name){
        if(!allowed(name))return;
        try{Intent intent=activity.getPackageManager().getLaunchIntentForPackage(name);if(intent==null)throw new IllegalStateException();activity.startActivity(intent);}catch(Exception e){Toast.makeText(activity,"앱이 설치되어 있는지 확인해주세요. 다른 피트니스 앱도 Health Connect 공유를 지원하면 연결할 수 있어요.",Toast.LENGTH_LONG).show();}
    }
}
