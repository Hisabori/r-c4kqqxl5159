package app.oncare.personal;

import android.app.*;
import android.content.*;
import org.json.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

final class MedicationChecks {
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static android.view.View find(android.view.View view,String description){if(description.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++){android.view.View result=find(group.getChildAt(i),description);if(result!=null)return result;}}return null;}
    static android.service.notification.StatusBarNotification[] own(NotificationManager manager){return Arrays.stream(manager.getActiveNotifications()).filter(n->n.getId()==41).toArray(android.service.notification.StatusBarNotification[]::new);}
    static void run(Instrumentation test)throws Exception {
        test.waitForIdleSync();
        Context c=test.getTargetContext();File temp=new File(c.getCacheDir(),"medication-check.json");temp.delete();
        MedicationStore store=new MedicationStore(temp);MedicationStore.Plan plan=new MedicationStore.Plan();plan.name="PRIVATE_MEDICATION";plan.instructions="PRIVATE_DOSE";
        require(store.save(plan),"Medication initial save");require(new MedicationStore(temp).plans.size()==1,"Medication persistence");
        long now=System.currentTimeMillis();require(store.toggle(plan.id,now)&&new MedicationStore(temp).checked(plan.id,now),"Taken persistence");Calendar tomorrow=Calendar.getInstance();tomorrow.setTimeInMillis(now);tomorrow.add(Calendar.DAY_OF_YEAR,1);require(!store.checked(plan.id,tomorrow.getTimeInMillis()),"Taken date rollover");
        plan.hour=24;require(!store.save(plan)&&new MedicationStore(temp).plans.get(0).hour==9,"Invalid schedule changed original");plan.hour=9;
        plan.days.clear();require(!store.save(plan),"Empty days accepted");plan.days.addAll(Arrays.asList(1,2,3,4,5,6,7));
        for(int i=1;i<8;i++){MedicationStore.Plan extra=new MedicationStore.Plan();extra.name="Prescription";require(store.save(extra),"Eight schedule limit");}MedicationStore.Plan extra=new MedicationStore.Plan();extra.name="Too many";require(!store.save(extra)&&store.plans.size()==8,"Ninth schedule accepted");
        require(store.remove(plan.id)&&!store.checked(plan.id,now),"Deleted medication check retained");
        byte[] corrupt="broken".getBytes(java.nio.charset.StandardCharsets.UTF_8);Files.write(temp.toPath(),corrupt);MedicationStore broken=new MedicationStore(temp);require(!broken.readable&&!broken.save(plan)&&Arrays.equals(Files.readAllBytes(temp.toPath()),corrupt),"Corrupt original overwritten");temp.delete();
        plan.enabled=true;plan.days=new TreeSet<>(Collections.singleton(2));plan.hour=9;plan.minute=0;Calendar monday=Calendar.getInstance(TimeZone.getTimeZone("UTC"));monday.clear();monday.set(2026,Calendar.OCTOBER,12,9,0,0);
        long next=MedicationReceiver.next(plan,monday.getTimeInMillis(),TimeZone.getTimeZone("UTC"));Calendar expected=(Calendar)monday.clone();expected.add(Calendar.DAY_OF_YEAR,7);require(next==expected.getTimeInMillis(),"Exact-time weekday rollover");plan.enabled=false;require(MedicationReceiver.next(plan,now,TimeZone.getDefault())==Long.MAX_VALUE,"Disabled plan scheduled");
        require(MedicationReceiver.alarm(c,now).isImmutable(),"Medication alarm mutable");require(!c.getPackageManager().getReceiverInfo(new ComponentName(c,MedicationReceiver.class),0).exported,"Medication receiver exported");
        File file=new File(c.getFilesDir(),"medications-v1.json");byte[] original=file.exists()?Files.readAllBytes(file.toPath()):null;NotificationManager manager=c.getSystemService(NotificationManager.class);manager.cancelAll();
        MedicationActivity activity=null;
        try {
            if(file.exists())file.delete();store=new MedicationStore(c);Calendar date=Calendar.getInstance();plan=new MedicationStore.Plan();plan.name="PRIVATE_MEDICATION";plan.instructions="PRIVATE_DOSE";plan.hour=date.get(Calendar.HOUR_OF_DAY);plan.minute=date.get(Calendar.MINUTE);plan.enabled=true;require(store.save(plan),"Medication notification fixture");
            long eventTime=date.getTimeInMillis(),due=eventTime-eventTime%60000;new MedicationReceiver().onReceive(c,new Intent(MedicationReceiver.ACTION).putExtra("due",due));
            boolean allowed=MedicationReceiver.allowed(c);for(int i=0;i<30&&own(manager).length!=(allowed?1:0);i++)Thread.sleep(50);
            require(manager.getActiveNotifications().length==(allowed?1:0),"Medication permission handling: count="+own(manager).length+", allowed="+allowed+", fileReadable="+new MedicationStore(c).readable+", schedules="+new MedicationStore(c).plans.size()+", allIds="+Arrays.toString(Arrays.stream(manager.getActiveNotifications()).mapToInt(n->n.getId()).toArray()));
            if(allowed){Notification notification=own(manager)[0].getNotification();require(!notification.extras.toString().contains("PRIVATE_"),"Medication notification exposed name or dose");require(notification.contentIntent.isImmutable(),"Medication content intent mutable");new MedicationReceiver().onReceive(c,new Intent(MedicationReceiver.ACTION).putExtra("due",due));require(manager.getActiveNotifications().length==1,"Duplicate notification");}
            activity=(MedicationActivity)test.startActivitySync(new Intent(c,MedicationActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));final MedicationActivity visible=activity;
            final Throwable[] failure={null};test.runOnMainSync(()->{try{android.view.View taken=find(visible.getWindow().getDecorView(),"medication-taken");require(taken!=null,"Taken control missing");taken.performClick();}catch(Throwable e){failure[0]=e;}});if(failure[0]!=null)throw new AssertionError(failure[0]);require(new MedicationStore(c).checked(plan.id,System.currentTimeMillis()),"UI did not persist taken check");WidgetRouteChecks.capture(test,c,"18-medication-schedule");
        }finally{if(activity!=null){final Activity close=activity;test.runOnMainSync(close::finish);}manager.cancelAll();file.delete();if(original!=null)Files.write(file.toPath(),original);MedicationReceiver.schedule(c);}
    }
}
