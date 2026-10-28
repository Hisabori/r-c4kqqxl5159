package app.oncare.personal;

import android.content.Context;
import org.json.*;
import javax.net.ssl.*;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.*;

/** Test-only fixture proves authentication and TLS transport, without bundling health samples. */
@android.annotation.TargetApi(34)
final class SyncChecks {
    static void run(Context context)throws Exception {
        LocalSyncServer server=new LocalSyncServer(context,new LocalSyncServer.HealthSource(){
            public JSONObject snapshot(String peer)throws Exception {return new JSONObject().put("version",1).put("peer",peer).put("generatedAt",System.currentTimeMillis()).put("windowStart",System.currentTimeMillis()-86400000).put("kinds",new JSONArray().put("체중")).put("entries",new JSONArray().put(new JSONObject().put("id","hc:test-only").put("kind","체중").put("date",System.currentTimeMillis()-60000).put("value",70).put("note","")));}
            public List<String> grantedKinds(){return Arrays.asList("체중");}
        },"health-sync-tests");
        try {
            java.util.concurrent.ExecutorService initializer=java.util.concurrent.Executors.newFixedThreadPool(2);
            try {
                java.util.concurrent.Callable<String> identity=()->server.peer()+"|"+server.token()+"|"+LocalSyncServer.digest(server.keys().getCertificate(LocalSyncServer.KEY_ALIAS).getEncoded());
                java.util.concurrent.Future<String> left=initializer.submit(identity),right=initializer.submit(identity);
                if(!left.get(20,java.util.concurrent.TimeUnit.SECONDS).equals(right.get(20,java.util.concurrent.TimeUnit.SECONDS)))throw new AssertionError("Concurrent pairing initialization changed identity");
            } finally {initializer.shutdownNow();}
            server.enable(true);
            waitReady(server);
            String fingerprint=LocalSyncServer.digest(server.keys().getCertificate(LocalSyncServer.KEY_ALIAS).getEncoded());
            SSLContext tls=pinned(fingerprint);
            try { request(pinned(String.join("", Collections.nCopies(64,"0"))),server.token()); throw new AssertionError("Wrong certificate pin was accepted"); } catch(SSLException expected) {}
            server.transportFailure=null;
            if(request(tls,"incorrect-token")!=401)throw new AssertionError("Unauthorized read was not rejected");
            String token=server.token();if(request(tls,token)!=200)throw new AssertionError("Authenticated TLS read failed");
            server.rotate();waitReady(server);if(request(tls,token)!=401)throw new AssertionError("Old pairing token still valid");
            if(request(tls,server.token())!=200)throw new AssertionError("New token failed");
        } catch(Exception error) { Thread.sleep(100); throw new AssertionError("TLS client: "+error+"; server: "+server.transportFailure,error); } finally {server.enable(false);context.getSharedPreferences("health-sync-tests",Context.MODE_PRIVATE).edit().clear().commit();}
    }
    static SSLContext pinned(String fingerprint)throws Exception {
        SSLContext tls=SSLContext.getInstance("TLS");tls.init(null,new TrustManager[]{new X509TrustManager(){public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}public void checkClientTrusted(X509Certificate[] chain,String auth){}public void checkServerTrusted(X509Certificate[] chain,String auth)throws java.security.cert.CertificateException{try{if(chain.length==0||!LocalSyncServer.digest(chain[0].getEncoded()).equals(fingerprint))throw new java.security.cert.CertificateException("Pin mismatch");}catch(java.security.cert.CertificateException e){throw e;}catch(Exception e){throw new java.security.cert.CertificateException(e);}}}},new java.security.SecureRandom());
        return tls;
    }
    static void waitReady(LocalSyncServer server)throws Exception {for(int i=0;i<100;i++){if(server.status().startsWith("건강 기록 "))return;Thread.sleep(100);}throw new AssertionError("TLS server not ready: "+server.status()+"; startup: "+server.startupFailure);}
    static int request(SSLContext tls,String token)throws Exception {
        HttpsURLConnection c=(HttpsURLConnection)new URL("https://127.0.0.1:8766/v1/health").openConnection();
        c.setSSLSocketFactory(tls.getSocketFactory());c.setHostnameVerifier((host,session)->host.equals("127.0.0.1"));c.setConnectTimeout(8000);c.setReadTimeout(8000);c.setRequestProperty("Authorization","Bearer "+token);
        try {int code=c.getResponseCode();if(code==200){String body=new String(c.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);if(new JSONObject(body).getJSONArray("entries").length()!=1)throw new AssertionError("Snapshot data mismatch");}return code;}finally{c.disconnect();}
    }
}
