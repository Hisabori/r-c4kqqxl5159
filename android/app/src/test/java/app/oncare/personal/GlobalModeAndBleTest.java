package app.oncare.personal;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.util.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=26)
public class GlobalModeAndBleTest {
    @Test public void globalModeOverridesLightAndRestoresWithoutTouchingRecords(){
        var controller=Robolectric.buildActivity(CareActivity.class);CareActivity a=controller.get();
        a.getSharedPreferences("app-preferences",0).edit().clear().putString("theme","light").commit();controller.setup();
        int original=a.background(), count=a.entries.size();assertFalse(a.night());
        a.getSharedPreferences("app-preferences",0).edit().putBoolean("deep-mind",true).putString("deep-mind-style","로즈").commit();
        for(int tab=0;tab<4;tab++){a.tab=tab;a.render();assertTrue(a.night());assertEquals(a.deepAccent(),a.BLUE);assertEquals(a.background(),a.BACK);assertNotEquals(original,a.background());assertEquals(count,a.entries.size());}
        assertEquals("오늘도 네 편이야 ♡",a.deepCopy("오늘의 건강 요약"));
        a.getSharedPreferences("app-preferences",0).edit().putBoolean("deep-mind",false).commit();a.render();assertFalse(a.night());assertEquals(original,a.background());assertEquals("오늘의 건강 요약",a.deepCopy("오늘의 건강 요약"));controller.pause().stop().destroy();
    }
    @Test public void separateActivitiesUseSameTheme(){var c=Robolectric.buildActivity(HealthSyncActivity.class);HealthSyncActivity a=c.get();a.getSharedPreferences("app-preferences",0).edit().putBoolean("deep-mind",true).putString("deep-mind-style","라일락").commit();assertTrue(a.night());assertEquals(0xFF160E24,a.background());assertEquals(DeepMood.accent("라일락"),a.deepAccent());}
    @Test public void authenticatedCipherRejectsTamperingAndUsesFreshNonces()throws Exception{
        byte[] challenge=new byte[32];Arrays.fill(challenge,(byte)7);byte[] plain="private-health-snapshot".getBytes(java.nio.charset.StandardCharsets.UTF_8);String secret="random-qr-pairing-secret";
        byte[] sealed=BleSyncServer.seal(secret,challenge,plain),again=BleSyncServer.seal(secret,challenge,plain);assertFalse(Arrays.equals(sealed,again));
        byte[] key=BleSyncServer.dataKey(secret,challenge);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,Arrays.copyOf(sealed,12)));cipher.updateAAD(challenge);assertArrayEquals(plain,cipher.doFinal(Arrays.copyOfRange(sealed,12,sealed.length)));
        sealed[sealed.length-1]^=1;cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,Arrays.copyOf(sealed,12)));cipher.updateAAD(challenge);try{cipher.doFinal(Arrays.copyOfRange(sealed,12,sealed.length));fail("tampered health accepted");}catch(AEADBadTagException expected){}
        assertFalse(Arrays.equals(key,BleSyncServer.mac(secret,challenge)));
    }
}
