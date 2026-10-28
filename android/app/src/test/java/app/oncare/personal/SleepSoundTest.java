package app.oncare.personal;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=26)
public class SleepSoundTest {
    @Test public void relativeEnergyIsFiniteAndPartialMinuteIsPreserved()throws Exception{
        assertEquals(-120,SleepSoundService.db(0),0);assertEquals(-6.0206,SleepSoundService.db(0.5),0.001);
        org.json.JSONObject bin=SleepSoundService.bin(16000,4000,0.5);assertEquals(1,bin.getDouble("seconds"),0);assertEquals(-6.0206,bin.getDouble("rms"),0.001);assertFalse(bin.has("audio"));
    }
    @Test @Config(sdk=34) public void microphoneServiceIsPrivateAndTyped()throws Exception{
        android.content.Context c=org.robolectric.RuntimeEnvironment.getApplication();android.content.pm.ServiceInfo info=c.getPackageManager().getServiceInfo(new android.content.ComponentName(c,SleepSoundService.class),0);assertFalse(info.exported);
        assertTrue((info.getForegroundServiceType() & android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)!=0);
        java.util.List<String> permissions=java.util.Arrays.asList(c.getPackageManager().getPackageInfo(c.getPackageName(),android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions);assertTrue(permissions.contains(android.Manifest.permission.RECORD_AUDIO));assertTrue(permissions.contains(android.Manifest.permission.FOREGROUND_SERVICE_MICROPHONE));
        assertEquals(0,SleepSoundService.history(c).length());
    }
}
