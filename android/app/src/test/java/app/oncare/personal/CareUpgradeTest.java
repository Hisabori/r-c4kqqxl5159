package app.oncare.personal;
import org.junit.Test;import org.junit.runner.RunWith;import org.robolectric.RobolectricTestRunner;import org.robolectric.annotation.Config;import org.json.*;import java.util.*;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=26)
public class CareUpgradeTest {
    @Test public void moodIgnoresFutureInvalidAndDuplicateDays()throws Exception{
        long now=1800000000000L;List<JSONObject> entries=Arrays.asList(entry(now-10000,2),entry(now-20000,4),entry(now+10000,5),entry(now-10000,6));
        CareUpgrade.MoodContext result=CareUpgrade.moodContext(entries,now);assertEquals(1,result.days);assertEquals(2,result.latest,0);assertEquals(0,CareUpgrade.moodContext(Collections.emptyList(),now).days);
    }
    private JSONObject entry(long date,double value)throws Exception{return new JSONObject().put("kind","마음").put("date",date).put("value",value);}
    @Test public void medicationPhaseUsesConfirmationWithoutInferringDose(){Calendar c=Calendar.getInstance();c.set(2026,9,18,10,0,0);long now=c.getTimeInMillis();assertEquals("시간 지남 · 확인 전",CareUpgrade.medicationPhase(9,0,false,now));assertEquals("예정 · 확인 전",CareUpgrade.medicationPhase(11,0,false,now));assertEquals("확인 완료",CareUpgrade.medicationPhase(9,0,true,now));}
    @Test public void coverageIsBoundedAndMissingForPartialEpoch(){assertTrue(Double.isNaN(CareUpgrade.coverage(0,29)));assertTrue(Double.isNaN(CareUpgrade.coverage(-1,60)));assertEquals(.5,CareUpgrade.coverage(1,60),0);assertEquals(1,CareUpgrade.coverage(3,60),0);}
}
