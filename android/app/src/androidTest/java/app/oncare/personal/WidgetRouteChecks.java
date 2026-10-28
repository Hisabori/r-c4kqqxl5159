package app.oncare.personal;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;

final class WidgetRouteChecks {
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static String text(View view){StringBuilder out=new StringBuilder();if(view instanceof TextView)out.append(((TextView)view).getText());if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)out.append(text(((ViewGroup)view).getChildAt(i)));return out.toString();}
    static void capture(Instrumentation test,Context context,String name)throws Exception {test.waitForIdleSync();android.os.SystemClock.sleep(200);Bitmap image=test.getUiAutomation().takeScreenshot();require(image!=null,"Widget screenshot missing");try(FileOutputStream file=new FileOutputStream(new File(context.getFilesDir(),name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,file);}finally{image.recycle();}}
    static void awaitRoute(Instrumentation test,EnhancedActivity activity,int tab)throws Exception {
        for(int attempt=0;attempt<100;attempt++){final boolean[] ready={false};test.runOnMainSync(()->{ready[0]=activity.tab==tab&&activity.activeSheet!=null&&activity.activeSheet.isShowing();});if(ready[0])return;android.os.SystemClock.sleep(50);}
        throw new AssertionError("Shortcut did not open requested record sheet");
    }
    static void run(Instrumentation test)throws Exception {
        Context context=test.getTargetContext();long now=System.currentTimeMillis(),today=PracticeState.day(now);
        JSONArray countFixture=new JSONArray().put(new JSONObject().put("kind","운동").put("date",today).put("note","PRIVATE_NOTE")).put(new JSONObject().put("kind","수면").put("date",PracticeState.addDays(today,-1)));
        require(OnCareWidget.countRecords(new ByteArrayInputStream(countFixture.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)),now)==1,"Widget date boundary");
        require(OnCareWidget.countRecords(new ByteArrayInputStream(countFixture.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)),PracticeState.addDays(today,1))==0,"Widget date rollover");
        JSONArray fractional=new JSONArray().put(new JSONObject().put("kind","수면").put("date",today+123.456)).put(new JSONObject().put("kind","운동").put("date",PracticeState.addDays(today,1)+0.25));
        require(OnCareWidget.countRecords(new ByteArrayInputStream(fractional.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)),now)==1,"Fractional iPhone backup timestamp broke widget");
        require(AppRoutes.read(new Intent().putExtra(AppRoutes.KEY,"delete-all")).equals("home"),"Unlisted route accepted");
        Map<String,ShortcutInfo> shortcuts=new HashMap<>();for(ShortcutInfo info:context.getSystemService(ShortcutManager.class).getManifestShortcuts())shortcuts.put(info.getId(),info);
        for(String route:new String[]{"exercise","sleep","mood","connect"}){ShortcutInfo info=shortcuts.get(route);require(info!=null&&info.isEnabled()&&route.equals(AppRoutes.read(info.getIntent())),"Static shortcut wrong route: "+route);require(AppRoutes.pending(context,route).isImmutable(),"Mutable widget intent");}
        require(!AppRoutes.pending(context,"sleep").equals(AppRoutes.pending(context,"mood")),"Pending intents collapsed extras");
        EnhancedActivity a=(EnhancedActivity)test.startActivitySync(new Intent(context,EnhancedActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        final Throwable[] failure={null};final ArrayList<JSONObject> original=new ArrayList<>(a.entries);SharedPreferences care=context.getSharedPreferences("care",Context.MODE_PRIVATE);String oldRoutines=care.getString("routines","[]"),oldChecks=care.getString("checks","{}");
        try {
            test.runOnMainSync(()->{try{ArrayList<JSONObject> fixture=new ArrayList<>();fixture.add(new JSONObject().put("id","widget-private-fixture").put("kind","수면").put("date",now).put("value",7.5).put("note","PRIVATE_NOTE"));require(a.persist(fixture),"Widget fixture journal");care.edit().putString("routines",new JSONArray().put(new JSONObject().put("id","r").put("title","PRIVATE_ROUTINE_TITLE")).toString()).putString("checks",new JSONObject().put(PracticeState.dayKey(now),new JSONObject().put("r",true)).toString()).commit();}catch(Throwable e){failure[0]=e;}});
            if(failure[0]!=null)throw new AssertionError(failure[0]);
            for(int mode=0;mode<3;mode++){final int chosen=mode;test.runOnMainSync(()->{try{Context display=a;if(chosen==2){Configuration config=new Configuration(a.getResources().getConfiguration());config.fontScale=1.3f;display=a.createConfigurationContext(config);}FrameLayout host=new FrameLayout(display);host.setBackgroundColor(Color.WHITE);int width=chosen==0?190:chosen==1?300:230,height=chosen==0?190:chosen==1?250:220;View widget=OnCareWidget.views(display,width,height,now).apply(display,host);require(text(widget).contains("오늘 기록 1개")&&text(widget).contains("루틴 1 / 1개 완료"),"Widget counts wrong");require(!text(widget).contains("PRIVATE_")&&!text(widget).contains("7.5"),"Widget exposed sensitive detail");int density=(int)Math.ceil(display.getResources().getDisplayMetrics().density);FrameLayout.LayoutParams layout=new FrameLayout.LayoutParams(width*density,height*density);layout.setMargins(20*density,30*density,0,0);host.addView(widget,layout);a.setContentView(host);}catch(Throwable e){failure[0]=e;}});if(failure[0]!=null)throw new AssertionError(failure[0]);capture(test,context,new String[]{"15-widget-compact","16-widget-wide","17-widget-large-text"}[mode]);}
            Instrumentation.ActivityMonitor practice=test.addMonitor(PracticeActivity.class.getName(),null,false);AppRoutes.pending(context,"exercise").send();Activity child=test.waitForMonitorWithTimeout(practice,5000);require(child instanceof PracticeActivity&&((PracticeActivity)child).timerPage,"Widget exercise route did not open timer");test.runOnMainSync(child::finish);test.removeMonitor(practice);test.waitForIdleSync();
            Instrumentation.ActivityMonitor connect=test.addMonitor(HealthSyncActivity.class.getName(),null,false);AppRoutes.pending(context,"connect").send();child=test.waitForMonitorWithTimeout(connect,5000);require(child instanceof HealthSyncActivity,"Widget connection route wrong");test.runOnMainSync(child::finish);test.removeMonitor(connect);test.waitForIdleSync();
            AppRoutes.pending(context,"sleep").send();awaitRoute(test,a,2);test.runOnMainSync(()->{require(a.tab==2&&a.activeSheet!=null&&a.activeSheet.isShowing(),"Sleep shortcut did not open record sheet");a.activeSheet.dismiss();});
            AppRoutes.pending(context,"mood").send();awaitRoute(test,a,1);test.runOnMainSync(()->{require(a.tab==1&&a.activeSheet!=null&&a.activeSheet.isShowing(),"Mood shortcut did not open record sheet");a.activeSheet.dismiss();});
            test.runOnMainSync(()->{require(a.entries.size()==1,"Shortcuts must not add records");});
        }finally{test.runOnMainSync(()->{a.persist(original);care.edit().putString("routines",oldRoutines).putString("checks",oldChecks).commit();OnCareWidget.updateAll(context);a.finish();});}
    }
}
