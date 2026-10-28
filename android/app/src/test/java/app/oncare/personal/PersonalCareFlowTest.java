package app.oncare.personal;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=26)
public class PersonalCareFlowTest {
    @Test public void supportOpensDialerWithoutCallingOrSharingRecords(){
        org.robolectric.android.controller.ActivityController<CareActivity> controller=Robolectric.buildActivity(CareActivity.class).setup();CareActivity a=controller.get();android.widget.LinearLayout body=a.col();PersonalCare.dial(a,body,"상담","+82109");body.getChildAt(0).performClick();android.content.Intent intent=org.robolectric.Shadows.shadowOf(a).getNextStartedActivity();assertEquals(android.content.Intent.ACTION_DIAL,intent.getAction());assertEquals("tel:+82109",intent.getDataString());assertNull(intent.getExtras());controller.pause().stop().destroy();
    }
    @Test public void personalWorkoutGoalOnlyChangesNewSessionDefault(){
        org.robolectric.android.controller.ActivityController<PracticeActivity> controller=Robolectric.buildActivity(PracticeActivity.class);PracticeActivity a=controller.get();a.getSharedPreferences("personal-care",0).edit().putInt("minutes",30).commit();controller.setup();assertEquals(30,a.selectedGoal);assertNull(a.session);controller.pause().stop().destroy();
    }
}
