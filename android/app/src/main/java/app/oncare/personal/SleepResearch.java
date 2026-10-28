package app.oncare.personal;

import android.content.Context;
import ai.onnxruntime.*;
import java.nio.FloatBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Local, explicitly opted-in research; no audio persistence or network access. */
final class SleepResearch implements AutoCloseable {
    static final String HASH="ccc3c94646cab11d53fab9d2bedae8c777c3c5ec3f0926a421c35fe44c46400c";
    static final double THRESHOLD=.3778610110282898;
    static boolean available(Context c){try(java.io.InputStream s=c.getAssets().open("research-sleep.onnx")){return s.read()!=-1;}catch(Exception e){return false;}}
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy=new AtomicBoolean(),closed=new AtomicBoolean();
    final AtomicInteger analyzed=new AtomicInteger(),candidates=new AtomicInteger(),skipped=new AtomicInteger();
    private final ResearchMel.Decimator decimator=new ResearchMel.Decimator();
    private float[] epoch=new float[ResearchMel.SAMPLES];private int count;
    private OrtSession session;private final OrtEnvironment environment=OrtEnvironment.getEnvironment();
    SleepResearch(Context context)throws Exception{
        byte[] bytes;try(java.io.InputStream input=context.getAssets().open("research-sleep.onnx")){bytes=ArticleActivity.readBytes(input);}
        StringBuilder sha=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(bytes))sha.append(String.format(Locale.ROOT,"%02x",b&255));
        if(!HASH.equals(sha.toString()))throw new SecurityException("Research model checksum mismatch");
        try(OrtSession.SessionOptions options=new OrtSession.SessionOptions()){options.setIntraOpNumThreads(1);options.setInterOpNumThreads(1);session=environment.createSession(bytes,options);}
    }
    void add(short[] source,int length){
        if(closed.get())return;
        for(int i=0;i<length;i++){float sample=decimator.add(source[i]);if(Float.isNaN(sample))continue;epoch[count++]=sample;
            if(count==epoch.length){float[] ready=epoch;epoch=new float[ResearchMel.SAMPLES];count=0;
                if(busy.compareAndSet(false,true))executor.execute(()->analyze(ready));else skipped.incrementAndGet();}}
    }
    private void analyze(float[] audio){
        try{
            if(closed.get()){skipped.incrementAndGet();return;}
            double energy=0;int clipped=0;for(float value:audio){energy+=value*value;if(Math.abs(value)>=.99)clipped++;}
            if(energy/audio.length<Math.pow(10,-6.5)||clipped>audio.length*.01){skipped.incrementAndGet();return;}
            float[] features=ResearchMel.extract(audio);
            try(OnnxTensor input=OnnxTensor.createTensor(environment,FloatBuffer.wrap(features),new long[]{1,64,2998});OrtSession.Result result=session.run(Collections.singletonMap("log_mel",input))){
                float[][] values=(float[][])result.get(0).getValue();float p=values[0][1];
                if(!Float.isFinite(p)||p<0||p>1)throw new IllegalStateException("Invalid output");
                if(!closed.get()){analyzed.incrementAndGet();if(p>=THRESHOLD)candidates.incrementAndGet();}else skipped.incrementAndGet();
            }
        }catch(Exception error){if(!closed.get())skipped.incrementAndGet();}
        finally{Arrays.fill(audio,0);busy.set(false);}
    }
    org.json.JSONObject summary()throws org.json.JSONException{return new org.json.JSONObject().put("model","apsaa-pilot-v1").put("analyzed",analyzed.get()).put("candidates",candidates.get()).put("skipped",skipped.get()).put("researchOnly",true);}
    public void close(){
        if(!closed.compareAndSet(false,true))return;
        Arrays.fill(epoch,0);count=0;
        // Close on the same executor after pending inference, never race native run().
        executor.execute(()->{try{session.close();}catch(Exception ignored){}});executor.shutdown();
        try{executor.awaitTermination(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
    }
}
