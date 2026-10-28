package app.oncare.personal;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.os.Bundle;
import android.util.AtomicFile;
import android.util.JsonReader;
import android.util.JsonToken;
import android.widget.RemoteViews;
import org.json.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Home-screen counts only. Never renders notes, routine titles, sources or health values. */
public final class OnCareWidget extends AppWidgetProvider {
    static final String REFRESH="app.oncare.personal.WIDGET_REFRESH";
    static final class Summary {
        int records,routineDone,routineTotal;boolean readable=true,routinesReadable=true;
        String recordsText(){return readable?"오늘 기록 "+records+"개":"기록 확인 필요";}
        String routinesText(){return routinesReadable?"루틴 "+routineDone+" / "+routineTotal+"개 완료":"루틴 확인 필요";}
    }
    static int countRecords(InputStream input,long now)throws IOException {
        long from=PracticeState.day(now),to=PracticeState.addDays(from,1);int count=0,seen=0;
        try(JsonReader reader=new JsonReader(new InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8))){reader.beginArray();while(reader.hasNext()){
            if(++seen>100000)throw new IOException("Journal too large");long date=0;String kind="";reader.beginObject();
            while(reader.hasNext()){String key=reader.nextName();if(key.equals("date")&&reader.peek()==JsonToken.NUMBER){double raw=reader.nextDouble();date=Double.isFinite(raw)&&raw>0&&raw<=Long.MAX_VALUE?(long)raw:0;}else if(key.equals("kind")&&reader.peek()==JsonToken.STRING)kind=reader.nextString();else reader.skipValue();}
            reader.endObject();if(date>=from&&date<to&&Arrays.asList("마음","체중","운동","수면","물","걸음").contains(kind))count++;
        }reader.endArray();if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Trailing journal content");}return count;
    }
    static Summary summary(Context context,long now){Summary result=new Summary();AtomicFile file=new AtomicFile(new File(context.getFilesDir(),"journal.json"));
        if(file.getBaseFile().exists()||new File(context.getFilesDir(),"journal.json.bak").exists())try{result.records=countRecords(file.openRead(),now);}catch(Exception e){result.readable=false;}
        try{SharedPreferences care=context.getSharedPreferences("care",Context.MODE_PRIVATE);JSONArray routines=new JSONArray(care.getString("routines","[]"));JSONObject checks=new JSONObject(care.getString("checks","{}"));
            if(routines.length()>8)throw new JSONException("Routine limit");result.routineTotal=routines.length();JSONObject today=checks.optJSONObject(PracticeState.dayKey(now));
            for(int i=0;i<routines.length();i++){String id=routines.getJSONObject(i).getString("id");if(today!=null&&Boolean.TRUE.equals(today.opt(id)))result.routineDone++;}
        }catch(Exception e){result.routinesReadable=false;}return result;
    }
    static RemoteViews views(Context context,int width,int height,long now){boolean compact=width<240||height<220||context.getResources().getConfiguration().fontScale>1.2;
        RemoteViews result=new RemoteViews(context.getPackageName(),compact?R.layout.widget_oncare_compact:R.layout.widget_oncare);Summary counts=summary(context,now);
        result.setTextViewText(R.id.widget_date,new SimpleDateFormat("M월 d일 EEEE",Locale.KOREA).format(new Date(now)));
        result.setTextViewText(R.id.widget_records,counts.recordsText());result.setTextViewText(R.id.widget_routines,counts.routinesText());
        result.setOnClickPendingIntent(R.id.widget_root,AppRoutes.pending(context,"home"));
        int[] buttons={R.id.widget_exercise,R.id.widget_sleep,R.id.widget_mood,R.id.widget_connect};String[] routes={"exercise","sleep","mood","connect"};
        for(int i=0;i<buttons.length;i++)result.setOnClickPendingIntent(buttons[i],AppRoutes.pending(context,routes[i]));return result;
    }
    static void updateAll(Context context){try{AppWidgetManager manager=AppWidgetManager.getInstance(context);int[] ids=manager.getAppWidgetIds(new ComponentName(context,OnCareWidget.class));if(ids.length==0)return;update(context,manager,ids);}catch(Exception ignored){}}
    static void update(Context context,AppWidgetManager manager,int[] ids){for(int id:ids){Bundle options=manager.getAppWidgetOptions(id);manager.updateAppWidget(id,views(context,options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,280),options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,240),System.currentTimeMillis()));}schedule(context);}
    static PendingIntent alarm(Context context){return PendingIntent.getBroadcast(context,459,new Intent(context,OnCareWidget.class).setAction(REFRESH),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static void schedule(Context context){Calendar next=Calendar.getInstance();next.add(Calendar.DAY_OF_YEAR,1);next.set(Calendar.HOUR_OF_DAY,0);next.set(Calendar.MINUTE,1);next.set(Calendar.SECOND,0);next.set(Calendar.MILLISECOND,0);AlarmManager manager=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);if(manager!=null)manager.set(AlarmManager.RTC,next.getTimeInMillis(),alarm(context));}
    @Override public void onUpdate(Context context,AppWidgetManager manager,int[] ids){update(context,manager,ids);}
    @Override public void onAppWidgetOptionsChanged(Context context,AppWidgetManager manager,int id,Bundle options){update(context,manager,new int[]{id});}
    @Override public void onDisabled(Context context){AlarmManager manager=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);if(manager!=null)manager.cancel(alarm(context));}
    @Override public void onReceive(Context context,Intent intent){super.onReceive(context,intent);String action=intent.getAction();if(REFRESH.equals(action)||Intent.ACTION_DATE_CHANGED.equals(action)||Intent.ACTION_TIME_CHANGED.equals(action)||Intent.ACTION_TIMEZONE_CHANGED.equals(action)||Intent.ACTION_BOOT_COMPLETED.equals(action)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(action))updateAll(context);}
}
