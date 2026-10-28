package app.oncare.personal;
import org.junit.Test;import org.junit.runner.RunWith;import org.robolectric.RobolectricTestRunner;import org.robolectric.annotation.Config;import org.json.*;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=26)
public class SleepAudioCoverageTest {
    JSONObject session(double elapsed,double... bins)throws Exception{JSONArray rows=new JSONArray();for(double n:bins)rows.put(new JSONObject().put("seconds",n));return new JSONObject().put("started",1800000000000L).put("ended",1800000000000L+elapsed*1000).put("bins",rows);}
    @Test public void separateElapsedAndAudio()throws Exception{var c=SleepAudioCoverage.analyze(session(120,60));assertNotNull(c);assertEquals(60,c.recorded,0.001);assertEquals(60,c.gap(),0.001);assertEquals(0.5,c.fraction(),0.001);}
    @Test public void invalidAndRecoveredAreUnknown()throws Exception{assertNull(SleepAudioCoverage.analyze(session(-1)));assertNull(SleepAudioCoverage.analyze(session(10,60)));assertNull(SleepAudioCoverage.analyze(session(60,60).put("reason","복구된 요약")));assertNull(SleepAudioCoverage.analyze(new JSONObject().put("started",1800000000000L)));}
    @Test public void partialBinsAndBounds()throws Exception{assertEquals(1.5,SleepAudioCoverage.analyze(session(2,1.5)).recorded,0.001);assertNull(SleepAudioCoverage.analyze(session(60,66)));assertNull(SleepAudioCoverage.analyze(session(36000)));}
    @Test public void elapsedClockTakesPriorityOverWallClock()throws Exception{var s=session(-100,60).put("elapsedSeconds",120);var c=SleepAudioCoverage.analyze(s);assertNotNull(c);assertTrue(c.monotonic);assertEquals(120,c.elapsed,0.001);assertEquals(0.5,c.fraction(),0.001);assertNull(SleepAudioCoverage.analyze(session(120,60).put("elapsedSeconds","NaN")));}
}
