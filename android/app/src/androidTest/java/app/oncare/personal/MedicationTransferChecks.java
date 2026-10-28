package app.oncare.personal;

import android.app.*;
import android.content.*;
import org.json.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class MedicationTransferChecks {
    static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static void reject(byte[] data)throws Exception {try{MedicationTransfer.decode(data);}catch(Exception expected){return;}throw new AssertionError("Invalid medication backup accepted");}
    static void concurrentAccess(Context context)throws Exception {
        File file=new File(context.getCacheDir(),"medication-concurrent.json");file.delete();MedicationStore writer=new MedicationStore(file);MedicationStore.Plan plan=new MedicationStore.Plan();plan.name="Thread 0";require(writer.save(plan),"Concurrent fixture save");
        java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Future<?> saving=pool.submit(()->{for(int i=1;i<=20;i++){plan.name="Thread "+i;require(writer.save(plan),"Concurrent save lost original");}});
            java.util.concurrent.Future<?> reading=pool.submit(()->{for(int i=0;i<80;i++){MedicationStore read=new MedicationStore(file);require(read.readable&&read.plans.size()==1&&read.plans.get(0).name.startsWith("Thread "),"Partial medication file read");}});
            saving.get(20,java.util.concurrent.TimeUnit.SECONDS);reading.get(20,java.util.concurrent.TimeUnit.SECONDS);require(new MedicationStore(file).plans.get(0).name.equals("Thread 20"),"Latest write lost");
        }finally{pool.shutdownNow();file.delete();}
    }
    static void run(Instrumentation test)throws Exception {
        Context c=test.getTargetContext();concurrentAccess(c);byte[] fixture;try(InputStream input=test.getContext().getAssets().open("MedicationInterop.json")){fixture=MedicationTransfer.read(input);}
        MedicationTransfer.Snapshot snapshot=MedicationTransfer.decode(fixture);require(snapshot.plans.size()==1&&snapshot.plans.get(0).hour==21&&snapshot.plans.get(0).days.size()==7,"Shared fixture");
        File file=new File(c.getCacheDir(),"transfer-check.json");file.delete();MedicationStore store=new MedicationStore(file);
        require(MedicationTransfer.replace(store,snapshot),"Restore failed");require(!store.plans.get(0).enabled,"Restore silently enabled alarms");
        MedicationTransfer.Snapshot roundtrip=MedicationTransfer.decode(MedicationTransfer.encode(store));require(roundtrip.checks.toString().equals(snapshot.checks.toString()),"Checks lost");require(roundtrip.plans.get(0).name.equals(snapshot.plans.get(0).name),"Name lost");
        String raw=new String(fixture,StandardCharsets.UTF_8);reject(raw.replace("2026-10-12","2026-02-30").getBytes(StandardCharsets.UTF_8));reject(raw.replace("oncare-medications","health-backup").getBytes(StandardCharsets.UTF_8));reject(raw.replace("[1,2,3,4,5,6,7]","[1,1,2]").getBytes(StandardCharsets.UTF_8));reject(new byte[65537]);reject((raw+"garbage").getBytes(StandardCharsets.UTF_8));
        byte[] original="broken original".getBytes(StandardCharsets.UTF_8);Files.write(file.toPath(),original);store=new MedicationStore(file);require(!store.readable,"Corrupt file accepted");require(MedicationTransfer.replace(store,snapshot)&&store.readable,"Recovery failed");
        File[] preserved=file.getParentFile().listFiles((dir,name)->name.startsWith("medications-preserved-"));require(preserved!=null&&preserved.length>0,"Original not preserved");boolean match=false;for(File p:preserved)if(Arrays.equals(Files.readAllBytes(p.toPath()),original))match=true;require(match,"Preserved file altered");file.delete();for(File p:preserved)p.delete();
        File local=new File(c.getFilesDir(),"medications-v1.json");byte[] oldFile=local.exists()?Files.readAllBytes(local.toPath()):null;
        MedicationActivity a=(MedicationActivity)test.startActivitySync(new Intent(c,MedicationActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));final Throwable[] failure={null};
        int oldCount=a.medications.plans.size();
        try{test.runOnMainSync(()->{try{a.previewBackup(snapshot);require(a.medications.plans.size()==oldCount,"Preview changed current schedules");android.view.View cancel=MedicationChecks.find(a.pendingImport.getWindow().getDecorView(),"medication-import-cancel");require(cancel!=null,"Import cancel missing");cancel.performClick();require(!a.pendingImport.isShowing()&&a.medications.plans.size()==oldCount,"Cancel changed schedules");a.previewBackup(snapshot);}catch(Throwable e){failure[0]=e;}});if(failure[0]!=null)throw new AssertionError(failure[0]);WidgetRouteChecks.capture(test,c,"20-medication-import-preview");
            test.runOnMainSync(()->{try{android.view.View confirm=MedicationChecks.find(a.pendingImport.getWindow().getDecorView(),"medication-import-confirm");require(confirm!=null,"Import confirmation missing");confirm.performClick();require(a.medications.plans.size()==1&&!a.medications.plans.get(0).enabled,"Confirmed import enabled alarms");}catch(Throwable e){failure[0]=e;}});if(failure[0]!=null)throw new AssertionError(failure[0]);
        }
        finally{test.runOnMainSync(a::finish);local.delete();if(oldFile!=null)Files.write(local.toPath(),oldFile);MedicationReceiver.schedule(c);}
    }
}
