package app.oncare.personal;

import android.content.Intent;
import android.net.Uri;
import android.widget.*;
import org.json.*;
import org.xmlpull.v1.XmlPullParser;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;

/** Public feed requests never include journal, survey or medication data. */
final class MentalPublicResources {
    static final String FEED="https://www.nimh.nih.gov/site-info/index-rss.atom";
    static boolean allowed(String link) {
        try { URI u=new URI(link);return "https".equals(u.getScheme())&&"www.nimh.nih.gov".equals(u.getHost())&&u.getUserInfo()==null&&(u.getPort()==-1||u.getPort()==443)&&u.getPath().startsWith("/news/"); }catch(Exception e){return false;}
    }
    static JSONArray parse(byte[] data)throws Exception {
        if(data.length>1000000)throw new IOException("Feed too large");
        XmlPullParser p=android.util.Xml.newPullParser();p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true);
        p.setInput(new ByteArrayInputStream(data),"UTF-8");JSONArray out=new JSONArray();java.util.Set<String> seen=new java.util.HashSet<>();
        boolean hasFeed=false;boolean entry=false;String field="",title="",link="",date="",summary="";
        for(int event=p.getEventType();event!=XmlPullParser.END_DOCUMENT;event=p.next()){
            if(event==XmlPullParser.START_TAG){field=p.getName();if(field.equals("feed"))hasFeed=true;if(field.equals("entry")){entry=true;title="";link="";date="";summary="";}if(entry&&field.equals("link")&&"alternate".equals(p.getAttributeValue(null,"rel")))link=p.getAttributeValue(null,"href");}
            else if(event==XmlPullParser.TEXT&&entry){if(field.equals("title"))title+=p.getText();if(field.equals("published"))date+=p.getText();if(field.equals("updated")&&date.isEmpty())date+=p.getText();if(field.equals("summary")&&summary.length()<6000)summary+=p.getText();}
            else if(event==XmlPullParser.END_TAG){if(p.getName().equals("entry")){if(allowed(link)&&!title.isEmpty()&&title.length()<=500&&out.length()<30&&seen.add(link))out.put(new JSONObject().put("title",title).put("link",link).put("date",date).put("summary",summary));entry=false;}field="";}
        }if(!hasFeed)throw new IOException("Invalid feed");return out;
    }
    static byte[] fetch()throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(FEED).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(15000);
        try {if(c.getResponseCode()!=200)throw new IOException("Feed unavailable");try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>1000000)throw new IOException("Feed too large");out.write(b,0,n);}return out.toByteArray();}}finally{c.disconnect();}
    }
    final ArticleActivity activity;final LinearLayout root,news;final TextView status;final Button refresh;final android.content.SharedPreferences prefs;
    final ExecutorService executor=Executors.newSingleThreadExecutor();boolean loading=false;JSONArray items=new JSONArray();
    MentalPublicResources(ArticleActivity a,LinearLayout body){activity=a;prefs=a.getSharedPreferences("mental-public-feed",0);root=new LinearLayout(a);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(0,a.dp(12),0,a.dp(12));android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(a.background());bg.setCornerRadius(a.dp(28));root.setBackground(bg);body.addView(root);
        root.addView(a.text("마음을 위한 지원과 자료",22));loadSupport();
        root.addView(a.text("공식 정신건강 소식",20));root.addView(a.text("NIMH · 영어 원문 · 연구 소식은 개인 치료 지침이 아니에요.",12));status=a.text("",12);root.addView(status);refresh=new Button(a);refresh.setText("새로고침");root.addView(refresh);refresh.setOnClickListener(v->update(true));news=new LinearLayout(a);news.setOrientation(LinearLayout.VERTICAL);root.addView(news);
        try{String saved=prefs.getString("items","[]");if(saved.length()<=200000)items=new JSONArray(saved);}catch(Exception e){items=new JSONArray();}render();update(false);
    }
    void link(String title,String url){Button b=new Button(activity);b.setText(title+" ↗");b.setOnClickListener(v->{try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){Toast.makeText(activity,"브라우저를 확인해주세요",Toast.LENGTH_SHORT).show();}});root.addView(b);}
    static LinearLayout card(ArticleActivity a,LinearLayout parent,String source,String title,String subtitle){LinearLayout view=new LinearLayout(a);view.setOrientation(LinearLayout.VERTICAL);view.setPadding(a.dp(20),a.dp(14),a.dp(20),a.dp(18));android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(a.surface());bg.setCornerRadius(a.dp(28));view.setBackground(bg);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,a.dp(8),0,a.dp(8));parent.addView(view,lp);TextView label=a.text(source,12);label.setTextColor(a.night()?0xFF9DBBFF:0xFF245BD6);view.addView(label);TextView heading=a.text(title,22);heading.setTypeface(null,android.graphics.Typeface.BOLD);view.addView(heading);view.addView(a.text(subtitle+"  ›",13));view.setFocusable(true);view.setClickable(true);view.setContentDescription(source+", "+title+", "+subtitle);return view;}
    void loadSupport(){try(java.io.InputStream in=activity.getAssets().open("mental_support.json")){JSONArray support=new JSONArray(new String(ArticleActivity.readBytes(in),java.nio.charset.StandardCharsets.UTF_8));for(int i=0;i<support.length();i++){JSONObject item=support.getJSONObject(i);LinearLayout view=card(activity,root,item.optString("source"),item.optString("title"),"앱에서 읽기");view.setOnClickListener(v->NativePublicReader.text(activity,item.optString("title"),item.optString("source"),item.optString("body"),item.optString("url")));}}catch(Exception e){root.addView(activity.text("지원 자료를 열지 못했어요.",12));}}
    void render(){news.removeAllViews();for(int i=0;i<Math.min(8,items.length());i++){JSONObject item=items.optJSONObject(i);if(item==null||!allowed(item.optString("link")))continue;String date=item.optString("date");LinearLayout view=card(activity,news,"NIMH · 영어 원문",item.optString("title"),date.isEmpty()?"앱에서 읽기":date.substring(0,Math.min(10,date.length()))+" · 앱에서 읽기");view.setOnClickListener(v->{String summary=android.text.Html.fromHtml(item.optString("summary"),android.text.Html.FROM_HTML_MODE_LEGACY).toString();NativePublicReader.text(activity,item.optString("title"),"NIMH · "+date,summary.isEmpty()?"제공된 본문 요약이 없어요. 공식 원문에서 내용을 확인할 수 있습니다.":summary,item.optString("link"));});}if(news.getChildCount()==0)news.addView(activity.text("새로 등록된 소식이 없어요.",14));}
    void update(boolean force){if(loading)return;long previous=prefs.getLong("updated",0);if(!force&&previous>0&&System.currentTimeMillis()-previous<3600000){status.setText("마지막 확인 "+java.text.DateFormat.getDateTimeInstance().format(new java.util.Date(previous)));return;}loading=true;refresh.setEnabled(false);status.setText("공식 자료를 확인하고 있어요…");executor.execute(()->{try{JSONArray result=parse(fetch());activity.runOnUiThread(()->{if(activity.isDestroyed())return;items=result;long now=System.currentTimeMillis();prefs.edit().putString("items",result.toString()).putLong("updated",now).apply();status.setText("마지막 확인 "+java.text.DateFormat.getDateTimeInstance().format(new java.util.Date(now)));loading=false;refresh.setEnabled(true);render();});}catch(Exception e){activity.runOnUiThread(()->{if(activity.isDestroyed())return;status.setText("새 소식을 가져오지 못했어요. 저장된 자료와 지원 안내는 계속 볼 수 있어요.");loading=false;refresh.setEnabled(true);});}});}
    void close(){executor.shutdownNow();}
}
