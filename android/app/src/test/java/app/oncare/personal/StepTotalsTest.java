package app.oncare.personal;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.json.*;
import java.time.LocalDate;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=26)
public class StepTotalsTest {
    JSONObject row(String day,long n)throws Exception{return new JSONObject().put("day",day).put("count",n);}
    @Test public void zeroReplacesSnapshotMissingDaysStay()throws Exception{
        LocalDate now=LocalDate.of(2026,10,24);
        JSONArray old=new JSONArray().put(row("2026-10-23",4000)).put(row("2026-10-24",90));
        JSONArray merged=StepTotals.merge(old,new JSONArray().put(row("2026-10-24",0)),now);
        assertEquals(Long.valueOf(0),StepTotals.valid(merged,now).get("2026-10-24"));
        assertEquals(Long.valueOf(4000),StepTotals.valid(merged,now).get("2026-10-23"));
        assertEquals(merged.toString(),StepTotals.merge(merged,new JSONArray(),now).toString());
    }
    @Test public void invalidAndFutureDatesDoNotBecomeSteps()throws Exception{
        LocalDate now=LocalDate.of(2026,10,24);
        assertTrue(StepTotals.valid(new JSONArray().put(row("2026-10-25",90)).put(row("2026-10-17",90)),now).isEmpty());
        try{StepTotals.valid(new JSONArray().put(row("2026-10-24",-1)),now);fail();}catch(JSONException expected){}
        assertEquals(30000,StepTotals.goal(Integer.MAX_VALUE));assertEquals(1000,StepTotals.goal(-3));assertEquals(8000,StepTotals.goal(8299));
    }
    @Test public void dailyUpdatesReplaceInsteadOfAddingAndScreenOpensWithoutPermission()throws Exception{
        LocalDate now=LocalDate.of(2026,10,24);
        JSONArray rows=StepTotals.merge(new JSONArray().put(row("2026-10-24",2000)),new JSONArray().put(row("2026-10-24",3000)),now);
        assertEquals(Long.valueOf(3000),StepTotals.valid(rows,now).get("2026-10-24"));
        var controller=org.robolectric.Robolectric.buildActivity(StepsActivity.class).setup();
        StepsActivity a=controller.get();assertNotNull(a.findViewById(android.R.id.content));assertFalse(a.busy);controller.pause().stop().destroy();
    }
    @Test public void comparisonRequiresTwoDaysAndKeepsZero()throws Exception{LocalDate now=LocalDate.of(2026,10,27);assertNull(StepTotals.difference(new JSONArray().put(row("2026-10-27",100)),now));assertEquals(Long.valueOf(-200),StepTotals.difference(new JSONArray().put(row("2026-10-27",0)).put(row("2026-10-26",200)),now));}
}
