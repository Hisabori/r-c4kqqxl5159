package app.oncare.personal;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

final class NativePublicReader {
    static boolean allowed(String value) {
        try {URI u=new URI(value);return "https".equals(u.getScheme())&&u.getUserInfo()==null&&(u.getPort()==-1||u.getPort()==443)&&java.util.Set.of("www.nhs.uk","www.nimh.nih.gov","www.mentalhealth.go.kr","www.hscity.go.kr","www.ncmh.go.kr").contains(u.getHost());}catch(Exception e){return false;}
    }
    static void text(ArticleActivity a,String title,String source,String body,String url){
        LinearLayout layout=new LinearLayout(a);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(a.dp(22),a.dp(12),a.dp(22),a.dp(18));layout.addView(a.text(source,12));TextView content=a.text(body,17);content.setTextIsSelectable(true);content.setLineSpacing(a.dp(5),1);layout.addView(content);ScrollView scroll=new ScrollView(a);scroll.addView(layout);
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle(title).setView(scroll).setPositiveButton("닫기",null).setNeutralButton("공식 원문",(d,w)->web(a,url)).create();a.publicDialogs.add(dialog);dialog.setOnDismissListener(d->a.publicDialogs.remove(dialog));dialog.show();
    }
    static void web(ArticleActivity a,String url){
        if(!allowed(url)){Toast.makeText(a,"공식 출처 주소를 확인해주세요",Toast.LENGTH_SHORT).show();return;}
        WebView web=new WebView(a);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);web.getSettings().setSupportMultipleWindows(false);
        web.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){if(!allowed(request.getUrl().toString())){Toast.makeText(a,"이 링크는 지원되는 공식 출처가 아니에요",Toast.LENGTH_SHORT).show();return true;}return false;}});
        LinearLayout body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);body.addView(a.text("공식 원문 · 홈페이지 이용조건이 적용됩니다",12));body.addView(web,new LinearLayout.LayoutParams(-1,a.dp(440)));
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle("공식 자료").setView(body).setPositiveButton("닫기",null).setNeutralButton("이전 페이지",null).create();a.publicDialogs.add(dialog);dialog.setOnDismissListener(d->{a.publicDialogs.remove(dialog);web.stopLoading();web.loadUrl("about:blank");web.destroy();});dialog.show();dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{if(web.canGoBack())web.goBack();});web.loadUrl(url);
    }
    static void pdf(ArticleActivity a,org.json.JSONObject item){new PDFReader(a,item).open();}
    static final class PDFReader {
        final ArticleActivity a;final org.json.JSONObject item;final ExecutorService executor=Executors.newSingleThreadExecutor();volatile boolean closed=false;PdfRenderer renderer;ParcelFileDescriptor descriptor;Bitmap bitmap;int index=0;ImageView image;TextView status;Button previous,next;AlertDialog dialog;
        PDFReader(ArticleActivity a,org.json.JSONObject item){this.a=a;this.item=item;}
        void open(){LinearLayout body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(a.dp(12),a.dp(8),a.dp(12),a.dp(8));body.addView(a.text("국립정신건강센터 · 자료 등록월 "+item.optString("month")+"\n공공누리 제4유형 · 출처표시 · 비영리 · 변경금지 · 원문 유지",12));status=a.text("원문을 가져오는 중… 문서 크기에 따라 데이터와 시간이 필요해요.",12);body.addView(status);image=new ImageView(a);image.setAdjustViewBounds(true);image.setScaleType(ImageView.ScaleType.FIT_CENTER);body.addView(image,new LinearLayout.LayoutParams(-1,a.dp(380)));LinearLayout controls=new LinearLayout(a);previous=new Button(a);previous.setText("이전");next=new Button(a);next.setText("다음");previous.setEnabled(false);next.setEnabled(false);controls.addView(previous,new LinearLayout.LayoutParams(0,-2,1));controls.addView(next,new LinearLayout.LayoutParams(0,-2,1));body.addView(controls);previous.setOnClickListener(v->{if(index>0){index--;render();}});next.setOnClickListener(v->{if(renderer!=null&&index+1<renderer.getPageCount()){index++;render();}});dialog=new AlertDialog.Builder(a).setTitle(item.optString("title")).setView(body).setPositiveButton("닫기",null).create();a.publicDialogs.add(dialog);dialog.setOnDismissListener(d->{a.publicDialogs.remove(dialog);close();});dialog.show();executor.execute(()->load());}
        void load(){File temporary=null;try{
            String path=item.optString("path");if(!NCMHArticles.validPath(path))throw new IOException("Invalid document");File cache=new File(a.getCacheDir(),"ncmh-"+path.substring(path.lastIndexOf('/')+1));
            if(!cache.isFile()||cache.length()==0){temporary=File.createTempFile("ncmh-",".pdf",a.getCacheDir());String url=NCMHArticles.pdfURL(path);HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(60000);
                try{if(c.getResponseCode()!=200)throw new IOException("Unavailable");try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(temporary)){byte[] buffer=new byte[16384];int n;long total=0;while((n=in.read(buffer))!=-1){total+=n;if(closed||Thread.currentThread().isInterrupted())throw new InterruptedIOException();if(total>50000000)throw new IOException("Too large");out.write(buffer,0,n);}}}finally{c.disconnect();}
                try(FileInputStream in=new FileInputStream(temporary)){byte[] signature=new byte[5];if(in.read(signature)!=5||!java.util.Arrays.equals(signature,"%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII)))throw new IOException("Not a PDF");}
                if(!temporary.renameTo(cache))throw new IOException("Cache failed");temporary=null;
            }
            if(cache.length()>50000000)throw new IOException("Too large");final File ready=cache;
            a.runOnUiThread(()->{if(closed||a.isDestroyed())return;try{descriptor=ParcelFileDescriptor.open(ready,ParcelFileDescriptor.MODE_READ_ONLY);renderer=new PdfRenderer(descriptor);render();}catch(Exception e){status.setText("원문을 열지 못했어요. 다시 시도해주세요.");}});
        }catch(Exception e){a.runOnUiThread(()->{if(!closed&&!a.isDestroyed())status.setText("원문을 불러오지 못했어요. 연결 상태를 확인해주세요.");});}finally{if(temporary!=null)temporary.delete();}}
        void render(){if(renderer==null||closed)return;try(PdfRenderer.Page page=renderer.openPage(index)){int width=Math.min(1400,Math.max(600,a.getResources().getDisplayMetrics().widthPixels));int height=Math.max(1,(int)((double)width*page.getHeight()/page.getWidth()));if(height>4000){width=Math.max(1,(int)((double)width*4000/height));height=4000;}Bitmap current=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);current.eraseColor(android.graphics.Color.WHITE);page.render(current,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);image.setImageBitmap(current);if(bitmap!=null)bitmap.recycle();bitmap=current;status.setText((index+1)+" / "+renderer.getPageCount()+" 페이지");image.setContentDescription(item.optString("title")+", "+(index+1)+"페이지 원문");previous.setEnabled(index>0);next.setEnabled(index+1<renderer.getPageCount());}catch(Exception e){status.setText("이 페이지를 표시하지 못했어요.");}}
        void close(){closed=true;executor.shutdownNow();image.setImageDrawable(null);if(bitmap!=null)bitmap.recycle();if(renderer!=null)renderer.close();if(descriptor!=null)try{descriptor.close();}catch(Exception ignored){}}
    }
}
