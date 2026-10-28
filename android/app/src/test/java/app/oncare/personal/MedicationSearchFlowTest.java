package app.oncare.personal;
import org.junit.Test;import org.junit.runner.RunWith;import org.robolectric.Robolectric;import org.robolectric.RobolectricTestRunner;import org.robolectric.annotation.Config;import android.view.*;import android.widget.*;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=26)
public class MedicationSearchFlowTest {
    private String content(View v){String s=v instanceof TextView?((TextView)v).getText().toString():"";if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)s+="\n"+content(((ViewGroup)v).getChildAt(i));return s;}
    @Test public void typingFiltersBothListsWithoutReplacingInputOrChangingPlans(){
        var controller=Robolectric.buildActivity(MedicationActivity.class).setup();MedicationActivity a=controller.get();
        MedicationStore.Plan alpha=new MedicationStore.Plan();alpha.name="Alpha";alpha.instructions="아침 식후";assertTrue(a.medications.save(alpha));
        MedicationStore.Plan beta=new MedicationStore.Plan();beta.name="Beta";beta.instructions="저녁 식후";assertTrue(a.medications.save(beta));a.render();EditText field=a.searchField;
        field.setText("beta 저녁");assertSame(field,a.searchField);assertFalse(content(a.todayResults).contains("Alpha"));assertTrue(content(a.todayResults).contains("Beta"));assertFalse(content(a.registeredResults).contains("Alpha"));assertTrue(content(a.registeredResults).contains("Beta"));
        field.setText("결과없음");assertTrue(content(a.todayResults).contains("검색·필터 초기화"));assertFalse(content(a.registeredResults).contains("Beta"));assertEquals(2,new MedicationStore(a).plans.size());
        a.render();assertEquals("결과없음",a.searchField.getText().toString());a.searchField.setText("");assertTrue(content(a.registeredResults).contains("Alpha"));assertTrue(content(a.registeredResults).contains("Beta"));controller.pause().stop().destroy();
    }
}
