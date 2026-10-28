package app.oncare.personal;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=26)
public class ArticleUXTest {
    @Test public void restoresReadingFiltersWithoutOpeningNetworkResources(){
        Bundle state=new Bundle();state.putString("query","마음");state.putString("topic","마음");state.putBoolean("savedOnly",true);
        org.robolectric.android.controller.ActivityController<ArticleActivity> controller=Robolectric.buildActivity(ArticleActivity.class).create(state).start().resume();
        ArticleActivity activity=controller.get();assertEquals("마음",activity.query);assertEquals("마음",activity.topic);assertTrue(activity.savedOnly);assertEquals(View.GONE,activity.publicBody.getVisibility());assertNull(activity.publicResources);controller.pause().stop().destroy();
    }
    @Test public void savingKeepsReaderOpenAndUpdatesSavedList()throws Exception{
        org.robolectric.android.controller.ActivityController<ArticleActivity> controller=Robolectric.buildActivity(ArticleActivity.class).setup();ArticleActivity activity=controller.get();
        org.json.JSONObject article=activity.articles.getJSONObject(0);String id=article.getString("id");activity.prefs.edit().putBoolean(id,false).apply();activity.savedOnly=true;activity.renderList();activity.detail(article);
        android.app.Dialog dialog=activity.publicDialogs.iterator().next();assertTrue("reader initially visible",dialog.isShowing());Button save=find(dialog.getWindow().getDecorView(),"글 저장");assertNotNull("save action exists",save);save.performClick();assertTrue("reader remains visible",dialog.isShowing());assertTrue("bookmark persisted",activity.prefs.getBoolean(id,false));assertEquals("저장 해제",save.getText().toString());assertTrue(activity.list.getChildCount()>0);dialog.dismiss();org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertTrue("dialog tracking cleaned",activity.publicDialogs.isEmpty());controller.pause().stop().destroy();
    }
    @Test public void rapidSearchChangesRenderOnlyLatestQuery(){
        org.robolectric.android.controller.ActivityController<ArticleActivity> controller=Robolectric.buildActivity(ArticleActivity.class).setup();ArticleActivity a=controller.get();org.robolectric.shadows.ShadowLooper loop=org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper());loop.idle();
        android.widget.EditText search=findSearch(a.findViewById(android.R.id.content));assertNotNull(search);int before=a.renderRevision;search.setText("호");search.setText("호흡");assertEquals(before,a.renderRevision);loop.idleFor(java.time.Duration.ofMillis(200));assertEquals(before+1,a.renderRevision);assertEquals(1,a.list.getChildCount());assertEquals("1편 · 추천 글",a.resultCount.getText().toString());
        search.setText("없는 검색어");controller.pause().stop();int stopped=a.renderRevision;loop.idleFor(java.time.Duration.ofMillis(300));assertEquals(stopped,a.renderRevision);controller.destroy();
    }
    android.widget.EditText findSearch(android.view.View view){if(view instanceof android.widget.EditText)return (android.widget.EditText)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){android.widget.EditText found=findSearch(group.getChildAt(i));if(found!=null)return found;}}return null;}
    @Test public void fullTextSearchMatchesEveryWordAcrossFields()throws Exception{
        org.json.JSONObject article=new org.json.JSONObject().put("title","Sleep").put("summary","regular routine").put("takeaways",new org.json.JSONArray().put("Caffeine matters"));
        assertTrue(ArticleSearch.matches(article," sleep  CAFFEINE "));assertTrue(ArticleSearch.matches(article,"　"));assertFalse(ArticleSearch.matches(article,"sleep missing"));
    }
    @Test public void markUnreadRestoresSavedUnreadArticleWithoutRemovingBookmark()throws Exception{
        org.robolectric.android.controller.ActivityController<ArticleActivity> controller=Robolectric.buildActivity(ArticleActivity.class).setup();ArticleActivity a=controller.get();org.json.JSONObject article=a.articles.getJSONObject(0);String id=article.getString("id");a.prefs.edit().putBoolean(id,true).apply();a.savedOnly=true;a.unreadOnly=true;a.detail(article);assertTrue(a.prefs.getBoolean("read:"+id,false));android.app.Dialog dialog=a.publicDialogs.iterator().next();Button unread=find(dialog.getWindow().getDecorView(),"나중에 다시 읽기 · 읽지 않음으로 표시");assertNotNull(unread);unread.performClick();assertFalse(a.prefs.getBoolean("read:"+id,true));assertTrue(a.prefs.getBoolean(id,false));assertFalse(dialog.isShowing());assertEquals(1,a.list.getChildCount());controller.pause().stop().destroy();
    }
    Button find(View view,String text){if(view instanceof Button&&text.contentEquals(((Button)view).getText()))return (Button)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){Button found=find(group.getChildAt(i),text);if(found!=null)return found;}}return null;}
}
