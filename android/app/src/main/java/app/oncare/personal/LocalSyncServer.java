package app.oncare.personal;

import android.content.*;
import android.os.*;
import android.security.keystore.*;
import org.json.*;
import javax.net.ssl.*;
import javax.security.auth.x500.X500Principal;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.*;

/** Authenticated TLS endpoint. No internet service, background reading, or health writes. */
final class LocalSyncServer {
    static final int PORT=8766;
    static final String KEY_ALIAS="oncare-sync-ec";
    private static LocalSyncServer instance;
    private final Context context;
    interface HealthSource { JSONObject snapshot(String peer)throws Exception; java.util.List<String> grantedKinds(); }
    private final HealthSource health;
    private final SharedPreferences prefs;
    private final ExecutorService io=Executors.newFixedThreadPool(3);
    private volatile SSLServerSocket socket;
    private volatile boolean running;
    private int owners;
    private volatile int generation;
    synchronized void acquire(){owners++;start();}
    synchronized void release(){owners=Math.max(0,owners-1);if(owners==0)stop();}
    private volatile JSONObject snapshot;
    private volatile String status="준비 중";
    private volatile long lastRead;
    volatile Throwable transportFailure;
    volatile Throwable startupFailure;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){public void run(){if(running){refreshHealth();handler.postDelayed(this,60000);}}};
    private LocalSyncServer(Context c){this(c,new HealthSource(){public JSONObject snapshot(String peer)throws Exception{return new HealthReader(c.getApplicationContext()).snapshot(peer);}public java.util.List<String> grantedKinds(){return new HealthReader(c.getApplicationContext()).grantedKinds();}},"health-sync");}
    LocalSyncServer(Context c,HealthSource health,String preferences){context=c.getApplicationContext();this.health=health;prefs=context.getSharedPreferences(preferences,Context.MODE_PRIVATE);}
    static synchronized LocalSyncServer get(Context c){if(instance==null)instance=new LocalSyncServer(c);return instance;}
    static String digest(byte[] data)throws Exception {byte[] hash=MessageDigest.getInstance("SHA-256").digest(data);StringBuilder out=new StringBuilder();for(byte b:hash)out.append(String.format(Locale.US,"%02x",b&255));return out.toString();}
    synchronized String peer(){String id=prefs.getString("peer",null);if(id==null){id=UUID.randomUUID().toString();if(!prefs.edit().putString("peer",id).commit())throw new IllegalStateException("Storage unavailable");}return id;}
    synchronized String token(){String token=prefs.getString("token",null);if(token==null){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);token=android.util.Base64.encodeToString(bytes,android.util.Base64.URL_SAFE|android.util.Base64.NO_WRAP|android.util.Base64.NO_PADDING);if(!prefs.edit().putString("token",token).commit())throw new IllegalStateException("Storage unavailable");}return token;}
    synchronized KeyStore keys()throws Exception {
        KeyStore keys=KeyStore.getInstance("AndroidKeyStore");keys.load(null);
        if(!keys.containsAlias(KEY_ALIAS)){
            // Conscrypt delegates prehashed TLS signatures to NONEwithECDSA for hardware-backed EC keys.
            KeyPairGenerator generator=KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC,"AndroidKeyStore");
            generator.initialize(new KeyGenParameterSpec.Builder(KEY_ALIAS,KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_VERIFY).setAlgorithmParameterSpec(new java.security.spec.ECGenParameterSpec("secp256r1")).setDigests(KeyProperties.DIGEST_NONE,KeyProperties.DIGEST_SHA256).setCertificateSubject(new X500Principal("CN=OnCare Local Sync")).setCertificateSerialNumber(java.math.BigInteger.ONE).setCertificateNotBefore(new Date(System.currentTimeMillis()-86400000)).setCertificateNotAfter(new Date(System.currentTimeMillis()+10L*365*86400000)).build());generator.generateKeyPair();
        }return keys;
    }
    String address()throws Exception {
        Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();
        while(interfaces.hasMoreElements()){NetworkInterface n=interfaces.nextElement();if(!n.isUp()||n.isLoopback())continue;String name=n.getName();if(!(name.startsWith("wlan")||name.startsWith("wifi")||name.startsWith("eth")))continue;Enumeration<InetAddress> addresses=n.getInetAddresses();while(addresses.hasMoreElements()){InetAddress a=addresses.nextElement();if(a instanceof Inet4Address&&a.isSiteLocalAddress())return a.getHostAddress();}}
        throw new IOException("Connect to Wi-Fi");
    }
    String pairingCode()throws Exception {
        String host;try{host=address();}catch(IOException offline){host="";}
        JSONObject code=new JSONObject().put("version",host.isEmpty()?2:1).put("host",host).put("port",PORT).put("peer",peer()).put("token",token()).put("fingerprint",digest(keys().getCertificate(KEY_ALIAS).getEncoded()));
        return "oncare1:"+android.util.Base64.encodeToString(code.toString().getBytes(StandardCharsets.UTF_8),android.util.Base64.URL_SAFE|android.util.Base64.NO_WRAP|android.util.Base64.NO_PADDING);
    }
    byte[] bleSnapshot(){JSONObject data=snapshot;if(!enabled()||data==null||System.currentTimeMillis()-lastRead>120000)return null;return data.toString().getBytes(StandardCharsets.UTF_8);}
    String status(){return status;}
    String freshness(){JSONObject data=snapshot;if(data==null)return "아직 Health Connect 기록을 준비하지 못했어요. 연결과 읽기 권한을 확인해주세요.";
        long newest=0,newestSleep=0;JSONArray entries=data.optJSONArray("entries");if(entries!=null)for(int i=0;i<entries.length();i++){JSONObject record=entries.optJSONObject(i);if(record==null)continue;long date=record.optLong("date");newest=Math.max(newest,date);if(record.optString("kind").equals("수면"))newestSleep=Math.max(newestSleep,date);}
        java.text.SimpleDateFormat date=new java.text.SimpleDateFormat("M.d HH:mm",Locale.KOREA);
        return "이 앱에서 읽은 시각 · "+date.format(new Date(lastRead))+"\n최신 건강 기록 · "+(newest==0?"기록 없음":date.format(new Date(newest)))+"\n최신 수면 기록 · "+(newestSleep==0?"기록 없음":date.format(new Date(newestSleep)))+"\n워치에서 삼성 헬스·Health Connect로 넘어오는 데 시간이 걸릴 수 있어요.";
    }
    boolean enabled(){return prefs.getBoolean("enabled",false);}
    void enable(boolean value){if(!prefs.edit().putBoolean("enabled",value).commit())throw new IllegalStateException("Storage unavailable");if(value)start();else stop();}
    void rotate(){stop();if(!prefs.edit().remove("token").commit())throw new IllegalStateException("Storage unavailable");token();if(enabled())start();}
    synchronized void start(){if(running||!enabled()||Build.VERSION.SDK_INT<34)return;running=true;final int epoch=++generation;status="연결 준비 중";startupFailure=null;io.execute(()->{SSLServerSocket listener=null;try{
        KeyStore keys=keys();PrivateKey key=(PrivateKey)keys.getKey(KEY_ALIAS,null);X509Certificate[] chain={(X509Certificate)keys.getCertificate(KEY_ALIAS)};String algorithm=key.getAlgorithm();
        X509ExtendedKeyManager manager=new X509ExtendedKeyManager(){public String[] getClientAliases(String t,Principal[] i){return null;}public String chooseClientAlias(String[] t,Principal[] i,Socket s){return null;}public String[] getServerAliases(String t,Principal[] i){return t.equals(algorithm)?new String[]{"oncare"}:null;}public String chooseServerAlias(String t,Principal[] i,Socket s){return t.equals(algorithm)?"oncare":null;}public String chooseEngineServerAlias(String t,Principal[] i,SSLEngine e){return t.equals(algorithm)?"oncare":null;}public X509Certificate[] getCertificateChain(String a){return chain;}public PrivateKey getPrivateKey(String a){return key;}};
        SSLContext tls=SSLContext.getInstance("TLS");tls.init(new KeyManager[]{manager},null,new SecureRandom());
        listener=(SSLServerSocket)tls.getServerSocketFactory().createServerSocket();listener.setReuseAddress(true);listener.bind(new InetSocketAddress(PORT));
        synchronized(this){if(!running||generation!=epoch){listener.close();return;}socket=listener;}
        status="같은 Wi-Fi의 iPhone 연결 대기";SyncLiveNotification.show(context,status,true);handler.post(refresh);
        while(running&&generation==epoch){SSLSocket client=(SSLSocket)listener.accept();io.execute(()->serve(client));}
    }catch(Exception e){try{if(listener!=null)listener.close();}catch(IOException ignored){}synchronized(this){if(running&&generation==epoch){startupFailure=e;status="연결 서버를 열지 못했어요. Wi-Fi와 앱 상태를 확인해주세요.";running=false;handler.removeCallbacks(refresh);if(socket==listener)socket=null;snapshot=null;}}}}); }
    synchronized void stop(){generation++;running=false;handler.removeCallbacks(refresh);snapshot=null;try{if(socket!=null)socket.close();}catch(IOException ignored){}socket=null;status="연결 꺼짐";SyncLiveNotification.show(context,"앱을 열면 건강 연결을 다시 시작해요",false);}
    void refreshHealth(){if(!running||Build.VERSION.SDK_INT<34)return;final int epoch=generation;io.execute(()->{try{JSONObject data=health.snapshot(peer());if(data.toString().getBytes(StandardCharsets.UTF_8).length>5*1024*1024)throw new IOException("Snapshot too large");if(running&&generation==epoch){lastRead=System.currentTimeMillis();snapshot=data;status="건강 기록 "+(data.getJSONArray("entries").length()+(data.optJSONArray("healthRecords")==null?0:data.optJSONArray("healthRecords").length()))+"개 · iPhone 연결 대기";SyncLiveNotification.show(context,"Android 건강 기록 준비 완료 · iPhone 연결 대기",true);}}catch(Exception e){if(generation!=epoch)return;snapshot=null;status="건강 기록을 읽지 못했어요. 권한과 Health Connect를 확인해주세요.";}});}
    void serve(SSLSocket client){try(SSLSocket c=client){c.setSoTimeout(8000);try{c.startHandshake();}catch(Exception e){transportFailure=e;throw e;}BufferedReader reader=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.US_ASCII));String first=boundedLine(reader);int total=first==null?0:first.length();String authorization="";String line;
        while((line=boundedLine(reader))!=null&&!line.isEmpty()){total+=line.length();if(total>4096)return;if(line.toLowerCase(Locale.US).startsWith("authorization:"))authorization=line.substring(line.indexOf(':')+1).trim();}
        if(!"GET /v1/health HTTP/1.1".equals(first)){reply(c,404,"{}");return;}
        if(!MessageDigest.isEqual(authorization.getBytes(StandardCharsets.UTF_8),("Bearer "+token()).getBytes(StandardCharsets.UTF_8))){reply(c,401,"{}");return;}
        JSONObject data=snapshot;
        if(!running||data==null||System.currentTimeMillis()-lastRead>120000){reply(c,503,"{}");return;}
        JSONArray kinds=data.getJSONArray("kinds");List<String> granted=health.grantedKinds();for(int i=0;i<kinds.length();i++)if(!granted.contains(kinds.getString(i))){reply(c,503,"{}");return;}
        reply(c,200,data.toString());SyncLiveNotification.show(context,"iPhone에 최신 건강 기록 전달 완료",true);
    }catch(Exception ignored){}}
    static String boundedLine(BufferedReader reader)throws IOException {StringBuilder line=new StringBuilder();int c;while((c=reader.read())!=-1){if(c==10)return line.toString().replace("\r","");if(line.length()>=2048)throw new IOException("Header too large");line.append((char)c);}return line.length()==0?null:line.toString();}
    void reply(Socket c,int code,String body)throws IOException {byte[] data=body.getBytes(StandardCharsets.UTF_8);OutputStream out=c.getOutputStream();out.write(("HTTP/1.1 "+code+" "+(code==200?"OK":"Unavailable")+"\r\nContent-Type: application/json\r\nCache-Control: no-store\r\nConnection: close\r\nContent-Length: "+data.length+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(data);out.flush();}
}
