package app.oncare.personal;
import android.app.Instrumentation;
import android.content.Intent;
import org.json.*;
final class HealthBrowserChecks {
    static void run(Instrumentation test)throws Exception {
        ArticleActivity articles=(ArticleActivity)test.startActivitySync(new Intent(test.getTargetContext(),ArticleActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Thread.sleep(500);if(articles.articles.length()!=10)throw new AssertionError("Article catalog missing");for(int i=0;i<articles.articles.length();i++){JSONObject a=articles.articles.getJSONObject(i);if(!a.getString("sourceURL").startsWith("https://www.nhs.uk/")||a.getJSONArray("takeaways").length()!=3)throw new AssertionError("Invalid article source/content");}WidgetRouteChecks.capture(test,test.getTargetContext(),"25-articles-library");test.runOnMainSync(articles::finish);
        HealthDataActivity health=(HealthDataActivity)test.startActivitySync(new Intent(test.getTargetContext(),HealthDataActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Thread.sleep(700);if(health.data==null||health.data.getJSONArray("availability").length()!=54)throw new AssertionError("Permission inventory missing");WidgetRouteChecks.capture(test,test.getTargetContext(),"26-health-browser");test.runOnMainSync(health::finish);
    }
}
