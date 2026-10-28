package app.oncare.personal;
import org.junit.Test;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.Config;import org.json.*;import android.widget.*;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=26)
public class ArticleHistoryTest {
    @Test public void boundedKnownHistoryMovesToFrontAndCanBeDisabled()throws Exception{
        var prefs=RuntimeEnvironment.getApplication().getSharedPreferences("history-test",0);prefs.edit().clear().commit();JSONArray catalog=new JSONArray();for(int i=0;i<15;i++)catalog.put(new JSONObject().put("id","id"+i));
        prefs.edit().putString("recent-opened","[\"unknown\",\"id0\",\"id0\"]").apply();assertEquals(1,ArticleHistory.load(prefs,catalog).size());for(int i=0;i<15;i++)ArticleHistory.opened(prefs,catalog,"id"+i);
        assertEquals(10,ArticleHistory.load(prefs,catalog).size());assertEquals("id14",ArticleHistory.load(prefs,catalog).get(0));ArticleHistory.opened(prefs,catalog,"id10");assertEquals("id10",ArticleHistory.load(prefs,catalog).get(0));ArticleHistory.opened(prefs,catalog,"unknown");assertEquals(10,ArticleHistory.load(prefs,catalog).size());
        prefs.edit().putBoolean("keep-recent",false).apply();ArticleHistory.clear(prefs);ArticleHistory.opened(prefs,catalog,"id1");assertTrue(ArticleHistory.load(prefs,catalog).isEmpty());assertFalse(prefs.contains("recent-opened"));
    }
    @Test public void historyClearAndSwitchDoNotRemoveSavedOrReadFlags()throws Exception{
        var controller=Robolectric.buildActivity(ArticleActivity.class).setup();ArticleActivity a=controller.get();JSONObject article=a.articles.getJSONObject(0);String id=article.getString("id");a.prefs.edit().putBoolean(id,true).apply();a.detail(article);android.app.Dialog dialog=a.publicDialogs.iterator().next();dialog.dismiss();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals(id,ArticleHistory.load(a.prefs,a.articles).get(0));
        android.view.View clear=MedicationChecks.find(a.recentBody,"articles-clear-recent");assertNotNull(clear);clear.performClick();assertTrue(ArticleHistory.load(a.prefs,a.articles).isEmpty());assertTrue(a.prefs.getBoolean(id,false));assertTrue(a.prefs.getBoolean("read:"+id,false));
        a.detail(article);a.publicDialogs.iterator().next().dismiss();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();((Switch)MedicationChecks.find(a.recentBody,"articles-keep-recent")).setChecked(false);assertFalse(a.prefs.contains("recent-opened"));a.detail(article);assertTrue(ArticleHistory.load(a.prefs,a.articles).isEmpty());controller.pause().stop().destroy();
    }
}
