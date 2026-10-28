package app.oncare.personal;
import org.junit.Test;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.Config;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=26)
public class HealthFitnessLinksTest {
    @Test public void onlyKnownFitnessAppsCanBeLaunched(){assertTrue(HealthFitnessLinks.allowed("com.sec.android.app.shealth"));assertTrue(HealthFitnessLinks.allowed("com.google.android.apps.fitness"));assertFalse(HealthFitnessLinks.allowed("arbitrary.package"));assertFalse(HealthFitnessLinks.allowed(null));}
    @Test public void missingSourceAppDoesNotCrashOrSendHealthData(){var controller=Robolectric.buildActivity(CareActivity.class).setup();CareActivity a=controller.get();HealthFitnessLinks.open(a,"com.google.android.apps.fitness");assertNull(Shadows.shadowOf(a).getNextStartedActivity());HealthFitnessLinks.open(a,"arbitrary.package");assertNull(Shadows.shadowOf(a).getNextStartedActivity());controller.pause().stop().destroy();}
    @Test public void appleMoodBackupIsPreservedWithoutClaimingAndroidMoodAccess()throws Exception{
        org.json.JSONObject record=new org.json.JSONObject().put("id","hc:apple:test").put("recordType","StateOfMindRecord").put("date",System.currentTimeMillis()-1000).put("title","마음 상태").put("category","마음").put("source","apple-health").put("fields",new org.json.JSONObject().put("valence",-.25));
        HealthArchive.validate(new org.json.JSONObject().put("healthRecords",new org.json.JSONArray().put(record)));
        for(HealthCatalog.Type type:HealthCatalog.TYPES)assertNotEquals("StateOfMindRecord",type.record);
    }
}
