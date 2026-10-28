package app.oncare.personal;

import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
import org.json.*;

public final class SleepSoundService extends Service {
    static volatile boolean running=false;
    static volatile long activeStarted=0;
    static volatile String status="";
    private volatile boolean stopping=false;
    private AudioRecord recorder;
    private PowerManager.WakeLock wake;
    private Thread worker;
    private SleepResearch research;
    static final int NOTICE=6422;
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent!=null&&"stop".equals(intent.getAction())){stopCapture("직접 종료");return START_NOT_STICKY;}
        if(running||worker!=null)return START_NOT_STICKY;
        double threshold=intent==null?-35:intent.getDoubleExtra("threshold",-35);if(threshold!=-45&&threshold!=-35&&threshold!=-25)threshold=-35;
        try{
            if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED)throw new SecurityException();
            if(intent!=null&&intent.getBooleanExtra("research",false))research=new SleepResearch(this);
            NotificationManager manager=getSystemService(NotificationManager.class);manager.createNotificationChannel(new NotificationChannel("sleep-sound","수면 소리 측정",NotificationManager.IMPORTANCE_LOW));
            PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,SleepSoundActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,SleepSoundService.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            Notification notification=new Notification.Builder(this,"sleep-sound").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("온케어 · 수면 소리 측정 중").setContentText("원음 저장 없음 · 눌러 확인하거나 종료하세요").setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"측정 종료",stop).build()).build();
            if(Build.VERSION.SDK_INT>=30)startForeground(NOTICE,notification,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);else startForeground(NOTICE,notification);
            int size=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);if(size<=0)throw new IllegalStateException();
            recorder=new AudioRecord(MediaRecorder.AudioSource.MIC,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(size,8192));if(recorder.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException();recorder.startRecording();if(recorder.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)throw new IllegalStateException();
            wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,getPackageName()+":sleep-sound");wake.acquire(8*3600*1000L+60000);
            running=true;stopping=false;status="측정 중 · 첫 요약은 약 1분 뒤 표시돼요.";final double selected=threshold;worker=new Thread(()->capture(selected),"oncare-sleep-sound");worker.start();
        }catch(Exception|LinkageError e){status="마이크 또는 연구 모델을 시작하지 못했어요. 권한·모델 포함 여부를 확인해주세요.";release();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
        return START_NOT_STICKY;
    }
    private void capture(double threshold){
        JSONObject session=new JSONObject();JSONArray bins=new JSONArray();short[] buffer=new short[4096];double energy=0,peak=0;long samples=0,start=SystemClock.elapsedRealtime();String reason="최대 8시간 측정 완료";
        try{
            activeStarted=System.currentTimeMillis();session.put("started",activeStarted).put("threshold",threshold).put("bins",bins).put("reason","측정 중");save(session);
            while(!stopping&&SystemClock.elapsedRealtime()-start<8*3600*1000L&&bins.length()<480){
                int n=recorder.read(buffer,0,buffer.length);if(n<=0){reason="마이크 사용 중단";break;}
                if(Build.VERSION.SDK_INT>=29&&recorder.getActiveRecordingConfiguration()!=null&&recorder.getActiveRecordingConfiguration().isClientSilenced()){reason="다른 앱·기기 설정으로 마이크 중단";break;}
                for(int i=0;i<n;i++){double value=buffer[i]/32768.0;energy+=value*value;peak=Math.max(peak,Math.abs(value));}samples+=n;
                if(research!=null)research.add(buffer,n);
                if(samples>=16000*60){bins.put(bin(samples,energy,peak));samples=0;energy=0;peak=0;save(session);}
            }
            if(stopping)reason=status.isEmpty()?"측정 중단":status;
            if(samples>0&&bins.length()<480)bins.put(bin(samples,energy,peak));
        }catch(Exception e){reason="마이크·저장 오류로 중단";}finally{
            long endedWall=System.currentTimeMillis(),elapsedMillis=SystemClock.elapsedRealtime()-start;
            if(research!=null)research.close();
            try{session.put("ended",endedWall).put("elapsedSeconds",elapsedMillis/1000.0).put("reason",reason);save(session);}catch(Exception ignored){reason="요약을 저장하지 못했어요. 저장 공간을 확인해주세요.";}
            status=reason;running=false;release();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
        }
    }
    static double db(double value){return Double.isFinite(value)&&value>0?Math.max(-120,Math.min(0,20*Math.log10(value))):-120;}
    static JSONObject bin(long samples,double energy,double peak)throws JSONException{return new JSONObject().put("seconds",samples/16000.0).put("rms",db(Math.sqrt(energy/Math.max(1,samples)))).put("peak",db(peak));}
    private void save(JSONObject session)throws Exception{
        synchronized(SleepSoundService.class){if(research!=null)session.put("research",research.summary());JSONArray previous=history(this),next=new JSONArray();next.put(session);long started=session.optLong("started");for(int i=0;i<previous.length()&&next.length()<7;i++){JSONObject item=previous.optJSONObject(i);if(item!=null&&item.optLong("started")!=started)next.put(item);}android.util.AtomicFile file=new android.util.AtomicFile(new java.io.File(getNoBackupFilesDir(),"sleep-sound-v1.json"));java.io.FileOutputStream stream=null;try{stream=file.startWrite();stream.write(next.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));file.finishWrite(stream);}catch(Exception e){if(stream!=null)file.failWrite(stream);throw e;}}
    }
    static JSONArray history(Context context){synchronized(SleepSoundService.class){java.io.File file=new java.io.File(context.getNoBackupFilesDir(),"sleep-sound-v1.json");if(file.length()>1000000)return new JSONArray();try(java.io.InputStream input=new android.util.AtomicFile(file).openRead()){return new JSONArray(new String(ArticleActivity.readBytes(input),java.nio.charset.StandardCharsets.UTF_8));}catch(Exception e){return new JSONArray();}}}
    private void stopCapture(String reason){status=reason;stopping=true;if(recorder!=null)try{recorder.stop();}catch(Exception ignored){}if(worker==null){release();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}}
    private synchronized void release(){if(research!=null){research.close();research=null;}if(recorder!=null){try{recorder.stop();}catch(Exception ignored){}recorder.release();recorder=null;}if(wake!=null&&wake.isHeld())wake.release();wake=null;running=false;}
    @Override public void onTaskRemoved(Intent root){stopCapture("앱 종료로 중단");super.onTaskRemoved(root);}
    @Override public void onDestroy(){if(running)stopCapture("서비스 종료로 중단");super.onDestroy();}
}
