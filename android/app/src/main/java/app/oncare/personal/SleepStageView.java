package app.oncare.personal;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import org.json.*;
final class SleepStageView extends View {
 final JSONObject entry;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);SleepStageView(Context c,JSONObject e){super(c);entry=e;setContentDescription("sleep-stage-chart");}
 @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float d=getResources().getDisplayMetrics().density;String[] types={"awake","rem","light","deep"},labels={"깨어 있음","REM","얕은 수면","깊은 수면"};int[] colors={0xFFD77765,0xFF8B72D8,0xFF3467E8,0xFF4A46A7};long bed=entry.optLong("bedTime"),wake=entry.optLong("wakeTime");if(wake<=bed)return;JSONObject sleep=entry.optJSONObject("sleep");JSONArray stages=sleep==null?null:sleep.optJSONArray("stages");for(int row=0;row<4;row++){float top=(row*34+8)*d;paint.setTextSize(11*d);paint.setColor(colors[row]);canvas.drawText(labels[row],0,top+14*d,paint);paint.setColor(0x22888888);canvas.drawRoundRect(70*d,top,getWidth(),top+22*d,7*d,7*d,paint);for(int n=0;stages!=null&&n<stages.length();n++){JSONObject s=stages.optJSONObject(n);if(s==null||!types[row].equals(s.optString("type")))continue;float left=70*d+(getWidth()-70*d)*(s.optLong("start")-bed)/(float)(wake-bed),right=70*d+(getWidth()-70*d)*(s.optLong("end")-bed)/(float)(wake-bed);paint.setColor(colors[row]);canvas.drawRoundRect(left,top,right,top+22*d,4*d,4*d,paint);}}}
}
