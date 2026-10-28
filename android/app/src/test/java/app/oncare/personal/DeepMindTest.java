package app.oncare.personal;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.json.JSONObject;
import java.util.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=26)
public class DeepMindTest {
    JSONObject entry(long date,double value,String kind)throws Exception{return new JSONObject().put("id",UUID.randomUUID().toString()).put("date",date).put("kind",kind).put("value",value);}
    @Test public void latestDailyIgnoresFutureAndOtherKinds()throws Exception{
        long now=1800000000000L;DeepMind.Summary s=DeepMind.summarize(Arrays.asList(entry(now-5,2,"마음"),entry(now-10,5,"마음"),entry(now+1,1,"마음"),entry(now,5,"물"),entry(now,8,"마음")),now,false);assertEquals(1,s.days);assertEquals(2,s.average,0);assertEquals(1,s.difficultDays);
    }
    @Test public void weekBoundariesNeverOverlapAndEmptyIsUnknown()throws Exception{
        Calendar c=Calendar.getInstance();c.set(2026,9,20,0,0,0);c.set(Calendar.MILLISECOND,0);long now=c.getTimeInMillis()+12*3600000L;c.add(Calendar.DAY_OF_YEAR,-6);long boundary=c.getTimeInMillis();List<JSONObject> list=Arrays.asList(entry(boundary,4,"마음"),entry(boundary-1,1,"마음"));assertEquals(4,DeepMind.summarize(list,now,false).average,0);assertEquals(1,DeepMind.summarize(list,now,true).average,0);assertNull(DeepMind.summarize(Collections.emptyList(),now,false).average);
    }
    @Test public void explicitNotificationRequiresBothOptIns(){assertEquals("시발 처먹어 ㅋㅋㅋㅋ",DeepMind.notificationTitle(true,true));assertEquals("복용 일정을 확인할 시간",DeepMind.notificationTitle(false,true));assertNotEquals("시발 처먹어 ㅋㅋㅋㅋ",DeepMind.notificationTitle(true,false));assertTrue(DeepMind.notificationBody(true).contains("먹었으면 추가로 먹지 마"));}
    @Test public void personalizationIsOnlyAllowlisted(){assertTrue(DeepMind.template(0,"외로움").startsWith("오늘 살펴볼 마음: 외로움"));assertEquals(DeepMind.TEMPLATES[0],DeepMind.template(-1,"unknown"));assertEquals(DeepMind.TEMPLATES[DeepMind.TEMPLATES.length-1],DeepMind.template(100,"unknown"));}
    @Test public void modeOffHidesPanelAndOnProvidesDraftWithoutSaving(){
        var controller=Robolectric.buildActivity(CareActivity.class);CareActivity a=controller.get();a.getSharedPreferences("app-preferences",0).edit().clear().commit();controller.setup();a.tab=1;a.render();assertNull(find(a.page,"deep-mind-card"));int before=a.entries.size();a.getSharedPreferences("app-preferences",0).edit().putBoolean("deep-mind",true).commit();a.render();assertNotNull(find(a.page,"deep-mind-card"));assertNotNull(find(a.page,"deep-mind-medication-first"));find(a.page,"deep-mind-write").performClick();assertEquals(before,a.entries.size());assertNotNull(org.robolectric.shadows.ShadowDialog.getLatestDialog());controller.pause().stop().destroy();
    }
    android.view.View find(android.view.View view,String id){if(id.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++){android.view.View found=find(group.getChildAt(i),id);if(found!=null)return found;}}return null;}
}
