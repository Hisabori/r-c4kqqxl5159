package app.oncare.personal;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.widget.*;

public final class HealthSyncActivity extends GalaxyActivity {
    private TextView status,freshness,bleStatus;
    private BleSyncServer ble;
    void startBle(){if(ble!=null)ble.stop();ble=new BleSyncServer(this);if(LocalSyncServer.get(this).enabled()&&getSharedPreferences("health-sync",0).getBoolean("ble",false))ble.start();}
    void enableBle(){if(Build.VERSION.SDK_INT>=31&&(checkSelfPermission("android.permission.BLUETOOTH_CONNECT")!=android.content.pm.PackageManager.PERMISSION_GRANTED||checkSelfPermission("android.permission.BLUETOOTH_ADVERTISE")!=android.content.pm.PackageManager.PERMISSION_GRANTED)){requestPermissions(new String[]{"android.permission.BLUETOOTH_CONNECT","android.permission.BLUETOOTH_ADVERTISE"},84);return;}getSharedPreferences("health-sync",0).edit().putBoolean("ble",true).apply();startBle();render();}
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){public void run(){if(status!=null)status.setText(LocalSyncServer.get(HealthSyncActivity.this).status());if(freshness!=null)freshness.setText(LocalSyncServer.get(HealthSyncActivity.this).freshness());if(bleStatus!=null&&ble!=null)bleStatus.setText(ble.status);handler.postDelayed(this,2000);}};
    @Override public void onCreate(Bundle state){super.onCreate(state);render();}
    int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    android.graphics.drawable.GradientDrawable rounded(int color){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(color);d.setCornerRadius(dp(26));return d;}
    TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(foreground());t.setPadding(0,dp(12),0,dp(12));t.setLineSpacing(dp(3),1);return t;}
    void button(LinearLayout parent,String title,String id,Runnable action){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(15);b.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);b.setTextColor(id.equals("health-permissions")?Color.WHITE:Color.rgb(52,120,246));b.setBackground(rounded(id.equals("health-permissions")?Color.rgb(52,120,246):Color.rgb(232,240,255)));b.setPadding(dp(16),dp(12),dp(16),dp(12));b.setContentDescription(id);b.setOnClickListener(v->action.run());LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,-2);layout.setMargins(0,dp(5),0,dp(9));b.setMinHeight(dp(54));parent.addView(b,layout);}
    void render(){ScrollView scroll=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(22),dp(56),dp(22),dp(32));body.setBackgroundColor(background());scroll.addView(body);setContentView(scroll);
        TextView heading=text("건강·피트니스 연결",32);heading.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);body.addView(heading);body.addView(text("피트니스 앱 → Health Connect → 온케어\nAndroid만으로 기록을 가져와 볼 수 있어요. iPhone에도 보내려면 아래에서 Wi-Fi 또는 블루투스 연결을 켜주세요. Galaxy Watch를 iPhone에 직접 페어링하는 기능은 아닙니다.",15));
        if(Build.VERSION.SDK_INT<34){body.addView(text("건강 연동은 Android 14 이상에서 지원해요. 기존 기록 기능은 계속 사용할 수 있어요.",16));button(body,"닫기","health-close",this::finish);return;}
        body.addView(text("활동 · 신체 · 생체 · 영양 · 수면 · 주기 · 마음\n선택한 읽기 권한만 사용하며 원본에 쓰지 않습니다. Google Health·삼성 헬스 등 원본 앱에서 Health Connect 공유를 먼저 켜주세요.",15));
        button(body,"건강 기록 읽기 권한 설정","health-permissions",this::choosePermissions);
        button(body,"지금 가져오기 · 건강 기록 보기","health-data-browser",()->startActivity(new Intent(this,HealthDataActivity.class)));
        body.addView(text("원본 앱에서 Health Connect 쓰기 공유 → 온케어에서 필요한 읽기 권한 허용 → 지금 가져오기 순서로 연결해요. 원본 앱이 공유하지 않은 기록이나 일부 피트니스 링·점수는 가져올 수 없습니다.",14));
        button(body,"삼성 헬스 열기","health-source-samsung",()->HealthFitnessLinks.open(this,"com.sec.android.app.shealth"));
        button(body,"Google Fit 열기 · 설치된 경우","health-source-fit",()->HealthFitnessLinks.open(this,"com.google.android.apps.fitness"));
        button(body,"Google Fit · Health Connect 공식 안내","health-fit-guide",()->openGuide("https://support.google.com/fit/faq/6108483"));
        body.addView(text("마음건강 연동: 지원 기기의 Health Connect에 공유된 마음챙김 세션을 읽어요. 읽기 권한에서 ‘마음챙김’을 선택한 뒤 지금 가져오기를 누르고 건강 정보의 ‘마음’ 분류에서 확인하세요. 기기에 해당 항목이 없으면 지원하지 않는 것으로 표시합니다. 다른 앱의 감정 점수·상담 내용이 자동 공유된다고 가정하지 않아요.",14));
        button(body,"Health Connect 열기","health-settings",()->{try{startActivity(new Intent("android.health.connect.action.HEALTH_HOME_SETTINGS"));}catch(Exception e){new AlertDialog.Builder(this).setMessage("휴대폰 설정 → 보안 및 개인정보 → Health Connect를 열어주세요.").setPositiveButton("확인",null).show();}});
        LocalSyncServer server=LocalSyncServer.get(this);Switch enabled=new Switch(this);enabled.setText("iPhone 연결 허용");enabled.setChecked(server.enabled());enabled.setContentDescription("health-sync-toggle");body.addView(enabled);
        enabled.setOnCheckedChangeListener((b,on)->{try{server.enable(on);startBle();}catch(Exception e){enabled.setChecked(false);}render();});
        button(body,"블루투스 연결 켜기 · 같은 Wi-Fi 없이", "health-ble-start",this::enableBle);
        button(body,"블루투스 연결 끄기", "health-ble-stop",()->{getSharedPreferences("health-sync",0).edit().putBoolean("ble",false).apply();if(ble!=null)ble.stop();render();});
        bleStatus=text(ble==null?"블루투스 꺼짐":ble.status,14);bleStatus.setContentDescription("health-ble-status");body.addView(bleStatus);
        body.addView(text("셀룰러 상태에서도 가까운 iPhone에 Bluetooth LE로 전달해요. iPhone 연결 허용을 켜고 이 화면을 열어두세요. iPhone에서 QR 등록 후 블루투스 방식을 선택하세요. 1MB를 넘는 기록은 Wi-Fi로 보내세요. 전송 실패 시 iPhone 기록은 유지됩니다.",14));
        Switch live=new Switch(this);live.setText("Now Bar · 실시간 알림");live.setContentDescription("health-live-toggle");live.setChecked(getSharedPreferences("health-sync",MODE_PRIVATE).getBoolean("live-notification",false));body.addView(live);live.setOnCheckedChangeListener((b,on)->{getSharedPreferences("health-sync",MODE_PRIVATE).edit().putBoolean("live-notification",on).apply();if(on){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},83);else SyncLiveNotification.show(this,server.status(),server.enabled());}else SyncLiveNotification.cancel(this);});
        body.addView(text("Galaxy에서 알림·잠금 화면·Live notifications와 Now Bar를 허용해주세요. Android 16 이상의 Live Updates를 요청하며 실제 Now Bar 표시는 기기·OS 설정에 따라 결정됩니다. 건강 수치와 메모는 알림에 표시하지 않아요.",13));
        button(body,"앱 알림 설정 열기","health-live-settings",()->startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName())));
        status=text(server.status(),15);status.setContentDescription("health-sync-status");body.addView(status);
        if(server.enabled()){
            button(body,"QR로 iPhone 간편 연결","health-pair-qr",this::showPairQR);
            button(body,"iPhone 연결 코드 복사","health-pair-copy",()->{try{String code=server.pairingCode();android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);ClipData data=ClipData.newPlainText("OnCare connection",code);if(Build.VERSION.SDK_INT>=33){android.os.PersistableBundle extras=new android.os.PersistableBundle();extras.putBoolean("android.content.extra.IS_SENSITIVE",true);data.getDescription().setExtras(extras);}clipboard.setPrimaryClip(data);Toast.makeText(this,"iPhone 온케어의 Android 연결 화면에 붙여넣으세요",Toast.LENGTH_LONG).show();}catch(Exception e){Toast.makeText(this,"같은 Wi-Fi에 연결한 뒤 다시 시도해주세요",Toast.LENGTH_LONG).show();}});
            button(body,"연결 코드 공유","health-pair-share",()->{try{startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,server.pairingCode()),"내 iPhone에 연결 코드 보내기"));}catch(Exception e){Toast.makeText(this,"Wi-Fi 연결을 확인해주세요",Toast.LENGTH_SHORT).show();}});
            button(body,"기존 연결 끊고 코드 새로 만들기","health-pair-reset",()->new AlertDialog.Builder(this).setMessage("기존 iPhone 연결이 해제됩니다. 새 코드를 iPhone에서 다시 등록해야 해요.").setNegativeButton("취소",null).setPositiveButton("새 코드",(d,w)->{server.rotate();startBle();render();}).show());
        }
        freshness=text(server.freshness(),14);freshness.setContentDescription("health-data-freshness");body.addView(freshness);
        TextView watchTitle=text("갤럭시 워치 기록 가져오기",22);watchTitle.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);body.addView(watchTitle);
        body.addView(text("1. 워치를 Android 휴대폰에 연결하고 삼성 헬스에서 최신 수면·운동 기록이 보이는지 확인해요.\n\n2. 삼성 헬스 설정에서 Health Connect 공유를 열고 수면·운동·걸음·체중 등 필요한 항목의 쓰기를 허용해요. 메뉴 이름은 앱 버전에 따라 달라질 수 있어요.\n\n3. Health Connect에서 데이터가 들어왔는지 확인하고 온케어에 필요한 읽기 권한을 허용해요.\n\n4. iPhone 연결을 켜고 연결 코드를 iPhone 온케어에 등록해요. 같은 Wi-Fi에서 두 앱을 열고 최신 기록을 확인해요.",14));
        body.addView(text("삼성 헬스의 지원 항목은 버전에 따라 바뀔 수 있어요. 처리와 공유가 늦거나 권한이 꺼져 있으면 새 기록이 아직 안 보일 수 있어요. 원본에 수면 단계나 기기 정보가 없으면 온케어가 만들어내지 않아요. 지원되는 42가지 운동·건강 기록과 12가지 의료 문서을 항목별 읽기 권한으로 가져와요. 최신 항목은 OS 지원 여부에 따라 달라져요. 의료 문서는 Android 16 또는 Android 14 확장 16 이상에서 별도 읽기 권한을 허용해야 해요. 문서 날짜는 원문에서 확인하며, 가져온 시각을 측정 시각으로 판단하지 않아요. 운동 경로는 세션별 동의가 필요해요.",13));
        button(body,"삼성 헬스 연동 공식 안내","health-samsung-guide",()->openGuide("https://developer.samsung.com/health/blog/en/accessing-samsung-health-data-through-health-connect"));
        button(body,"수면 데이터 공식 안내","health-sleep-guide",()->openGuide("https://developer.samsung.com/health/blog/en/managing-sleep-data-with-samsung-health-and-health-connect"));
        body.addView(text("연결 코드는 내 기기에만 전달하세요. 연결 코드가 있어야 읽을 수 있고, 연결은 암호화되어 내 기기인지 확인합니다. Android 앱이 닫혀 있거나 Wi-Fi 주소가 바뀌면 연결 화면에서 다시 확인하세요. 받은 기록은 iPhone에 남으며, 연동 해제만으로 삭제되지 않습니다.",13));
        button(body,"닫기","health-close",this::finish);
    }
    void choosePermissions(){java.util.LinkedHashMap<String,String> choices=new java.util.LinkedHashMap<>();for(HealthCatalog.Type t:HealthCatalog.TYPES)if(t.type()!=null)choices.merge(t.permission,t.title,(a,b)->a+" · "+b);for(MedicalReader.Type t:MedicalReader.TYPES)if(MedicalReader.supported())choices.put(t.permission,t.title+" · 의료 문서");String[] keys=choices.keySet().toArray(new String[0]),labels=choices.values().toArray(new String[0]);boolean[] selected=new boolean[keys.length];for(int i=0;i<keys.length;i++)selected[i]=checkSelfPermission(keys[i])==android.content.pm.PackageManager.PERMISSION_GRANTED;new AlertDialog.Builder(this).setTitle("가져올 건강 정보 선택").setMultiChoiceItems(labels,selected,(d,n,on)->selected[n]=on).setNeutralButton("전체 읽기 요청",(d,w)->requestPermissions(keys,82)).setNegativeButton("취소",null).setPositiveButton("선택 항목 요청",(d,w)->{java.util.List<String> wanted=new java.util.ArrayList<>();for(int i=0;i<keys.length;i++)if(selected[i])wanted.add(keys[i]);if(!wanted.isEmpty())requestPermissions(wanted.toArray(new String[0]),82);}).show();}
    void showPairQR() {
        try {
            LocalSyncServer server=LocalSyncServer.get(this);
            if(!server.enabled()) throw new IllegalStateException();
            showPairQRImage(server.pairingCode());
        } catch(Exception e) {Toast.makeText(this,"연결을 켜고 같은 Wi-Fi에 연결한 뒤 다시 시도해주세요",Toast.LENGTH_LONG).show();}
    }
    void showPairQRImage(String code) throws Exception {
            android.graphics.Bitmap bitmap=PairQR.image(code,Math.max(400,Math.min(1024,getResources().getDisplayMetrics().widthPixels)));
            LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(20),dp(8),dp(20),dp(16));
            ImageView image=new ImageView(this);image.setImageBitmap(bitmap);image.setAdjustViewBounds(true);image.setContentDescription("health-pair-qr-image"); content.addView(image,new LinearLayout.LayoutParams(-1,-2));
            content.addView(text("iPhone 온케어 → Android 건강 연결 → QR로 간편 연결\n스캔 후 기기 연결을 눌러 확인하세요.\nWi-Fi 또는 블루투스를 선택하고 두 앱을 열어두세요. 이 QR은 연결 암호이므로 내 iPhone에만 보여주세요.",14));
            ScrollView scroll=new ScrollView(this);scroll.addView(content);
            new AlertDialog.Builder(this).setTitle("내 iPhone 연결 QR").setView(scroll).setPositiveButton("닫기",null).show();
    }
    void openGuide(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)));}catch(Exception e){Toast.makeText(this,"웹 안내를 열지 못했어요",Toast.LENGTH_SHORT).show();}}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==84){boolean allowed=results.length==2;for(int result:results)allowed&=result==android.content.pm.PackageManager.PERMISSION_GRANTED;if(allowed){getSharedPreferences("health-sync",0).edit().putBoolean("ble",true).apply();startBle();}else Toast.makeText(this,"근처 기기 권한을 허용해야 블루투스로 연결할 수 있어요",Toast.LENGTH_LONG).show();render();}if(request==83){SyncLiveNotification.show(this,LocalSyncServer.get(this).status(),LocalSyncServer.get(this).enabled());}if(request==82){LocalSyncServer.get(this).refreshHealth();render();}}
    @Override protected void onStart(){super.onStart();if(Build.VERSION.SDK_INT>=34)LocalSyncServer.get(this).acquire();startBle();handler.post(refresh);}
    @Override protected void onStop(){handler.removeCallbacks(refresh);if(ble!=null)ble.stop();LocalSyncServer.get(this).release();super.onStop();}
}
