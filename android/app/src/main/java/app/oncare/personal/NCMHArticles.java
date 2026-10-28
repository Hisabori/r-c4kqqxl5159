package app.oncare.personal;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.regex.*;
import java.util.concurrent.*;
import android.widget.*;

final class NCMHArticles {
    static final String LIST="https://www.ncmh.go.kr/ncmh/board/webzineList.do?fno=50&menu_cd=04_05";
    static boolean validPath(String path){return path!=null&&path.matches("/[0-9]{4}/[0-9]{2}/[0-9_]+\\.pdf");}
    static String pdfURL(String path)throws Exception {if(!validPath(path))throw new IOException("Invalid path");return "https://www.ncmh.go.kr/static/jsp/viewHtmlDownload.jsp?filePath="+URLEncoder.encode(path,"UTF-8")+"&filename=mental-health.pdf";}
    static JSONArray parse(String html)throws Exception {
        if(html.length()>1000000||!html.contains("web_ttl")||!html.contains("공공누리"))throw new IOException("Invalid list");
        Pattern p=Pattern.compile("(?s)<span\\s+class=\"web_ttl\">([^<]{1,300})</span>.*?fn_fileDown\\('([^']{1,300})','(/[^']{1,200}\\.pdf)','[0-9]+'\\)");Matcher m=p.matcher(html);JSONArray out=new JSONArray();java.util.Set<String> seen=new java.util.HashSet<>();while(m.find()&&out.length()<12){String path=m.group(3);if(validPath(path)&&seen.add(path))out.put(new JSONObject().put("title",m.group(1).trim()).put("path",path).put("month",path.substring(1,8).replace('/','.')));}if(out.length()==0)throw new IOException("Empty or changed list");return out;
    }
    static JSONArray fetch()throws Exception {HttpURLConnection c=(HttpURLConnection)new URL(LIST).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(20000);try{if(c.getResponseCode()!=200)throw new IOException("Unavailable");try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>1000000)throw new IOException("Too large");out.write(b,0,n);}return parse(out.toString("UTF-8"));}}finally{c.disconnect();}}
    final ArticleActivity a;final LinearLayout cards;final TextView status;final Button refresh;final android.content.SharedPreferences prefs;final ExecutorService executor=Executors.newSingleThreadExecutor();boolean loading=false;JSONArray items=new JSONArray();
    NCMHArticles(ArticleActivity a,LinearLayout body){this.a=a;prefs=a.getSharedPreferences("ncmh-library",0);body.addView(a.text("국립정신건강센터 소식지",24));body.addView(a.text("마음로그人 · 원문 PDF · 공공누리 제4유형: 출처표시, 비영리, 변경금지",12));status=a.text("",12);body.addView(status);refresh=new Button(a);refresh.setText("자료 목록 새로고침");body.addView(refresh);refresh.setOnClickListener(v->update(true));cards=new LinearLayout(a);cards.setOrientation(LinearLayout.VERTICAL);body.addView(cards);try{String saved=prefs.getString("items","[]");if(saved.length()<100000)items=new JSONArray(saved);}catch(Exception e){}render();update(false);}
    void render(){cards.removeAllViews();for(int i=0;i<Math.min(12,items.length());i++){JSONObject item=items.optJSONObject(i);if(item==null||!validPath(item.optString("path")))continue;LinearLayout card=MentalPublicResources.card(a,cards,"국립정신건강센터 · 정신건강 소식지",item.optString("title"),"자료 등록월 "+item.optString("month")+" · 앱에서 읽기");card.setOnClickListener(v->NativePublicReader.pdf(a,item));}}
    void update(boolean force){if(loading)return;long previous=prefs.getLong("updated",0);if(!force&&previous>0&&System.currentTimeMillis()-previous<3600000){status.setText("마지막 확인 "+java.text.DateFormat.getDateTimeInstance().format(new java.util.Date(previous)));return;}loading=true;refresh.setEnabled(false);status.setText("공식 자료를 확인하는 중…");executor.execute(()->{try{JSONArray result=fetch();a.runOnUiThread(()->{if(a.isDestroyed())return;items=result;long now=System.currentTimeMillis();prefs.edit().putString("items",result.toString()).putLong("updated",now).apply();status.setText("마지막 확인 "+java.text.DateFormat.getDateTimeInstance().format(new java.util.Date(now)));loading=false;refresh.setEnabled(true);render();});}catch(Exception e){a.runOnUiThread(()->{if(a.isDestroyed())return;status.setText("자료 목록을 갱신하지 못했어요. 이전 자료를 유지합니다.");loading=false;refresh.setEnabled(true);});}});}
    void close(){executor.shutdownNow();}
}
