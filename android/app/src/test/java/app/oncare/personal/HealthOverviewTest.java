package app.oncare.personal;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.json.*;
import java.util.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=26)
public class HealthOverviewTest {
    JSONObject row(String id,String kind,double n,long date)throws Exception{return new JSONObject().put("id",id).put("kind",kind).put("value",n).put("date",date);}
    HealthOverview.Row find(ArrayList<HealthOverview.Row> rows,String kind){return rows.stream().filter(x->x.kind.equals(kind)).findFirst().get();}
    @Test public void directOnlySumsAndUnknownStaysUnknown()throws Exception {
        long now=System.currentTimeMillis();
        var rows=HealthOverview.rows(Arrays.asList(row("a","물",200,now),row("b","물",300,now),row("hc:c","물",999,now),row("d","수면",8,now).put("source","health-connect:test")),"물",now);
        assertEquals(500,find(rows,"물").value,0.001);assertNull(find(rows,"수면").value);
    }
    @Test public void latestMoodWinsAndFutureIsExcluded()throws Exception {
        long now=System.currentTimeMillis();
        var rows=HealthOverview.rows(Arrays.asList(row("a","마음",1,now-2),row("b","마음",4,now),row("c","마음",5,now+20)),"마음",now);
        assertEquals("마음",rows.get(0).kind);assertEquals(4,rows.get(0).value,0.001);assertNull(find(rows,"운동").value);
    }
    @Test public void calendarDaysAndFocusBounds()throws Exception {
        TimeZone old=TimeZone.getDefault();try{TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));Calendar c=Calendar.getInstance();c.clear();c.set(2026,10,3,12,0);long now=c.getTimeInMillis();c.setTimeInMillis(HealthOverview.day(now));c.add(Calendar.DAY_OF_YEAR,-6);long start=c.getTimeInMillis();
        var rows=HealthOverview.rows(Arrays.asList(row("a","운동",20,start),row("b","운동",30,start+1),row("c","운동",60,start-1)),"invalid",now);
        assertEquals("물",rows.get(0).kind);assertEquals(1,find(rows,"운동").days);assertNull(find(rows,"운동").value);
        }finally{TimeZone.setDefault(old);}
    }
    @Test public void selectedDayUsesCalendarAndClamps()throws Exception{
        TimeZone old=TimeZone.getDefault();try{TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));Calendar c=Calendar.getInstance();c.clear();c.set(2026,10,2,12,0);long now=c.getTimeInMillis(),selected=HealthOverview.snapshotDate(1,now);c.setTimeInMillis(HealthOverview.day(selected));long start=c.getTimeInMillis();c.add(Calendar.DAY_OF_YEAR,1);assertEquals(25*3600000L,c.getTimeInMillis()-start);assertEquals(now,HealthOverview.snapshotDate(-5,now));assertEquals(HealthOverview.snapshotDate(6,now),HealthOverview.snapshotDate(Integer.MAX_VALUE,now));}finally{TimeZone.setDefault(old);}
    }
    @Test public void pastDayExcludesTodayAndReturnsToEditableToday()throws Exception{
        long now=System.currentTimeMillis(),boundary=HealthOverview.day(now);
        var rows=HealthOverview.rows(Arrays.asList(row("a","물",200,boundary-1),row("b","물",300,boundary)),"물",HealthOverview.snapshotDate(1,now));assertEquals(200,find(rows,"물").value,0.001);
        var controller=org.robolectric.Robolectric.buildActivity(CareActivity.class).setup();CareActivity a=controller.get();a.tab=2;a.healthDayOffset=1;a.render();assertNull(view(a.page,"health-overview-next"));android.view.View today=view(a.page,"health-day-today");assertNotNull(today);int count=a.entries.size();today.performClick();assertEquals(0,a.healthDayOffset);assertNotNull(view(a.page,"health-overview-next"));assertEquals(count,a.entries.size());controller.pause().stop().destroy();
    }
    android.view.View view(android.view.View v,String id){if(id.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof android.view.ViewGroup){var g=(android.view.ViewGroup)v;for(int i=0;i<g.getChildCount();i++){var r=view(g.getChildAt(i),id);if(r!=null)return r;}}return null;}
}
