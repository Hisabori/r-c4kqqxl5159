package app.oncare.personal;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=26)
public class MentalPublicFeedTest {
    @Test public void parsesNCMHPublicationAndRejectsUnsafePaths()throws Exception {
        String html="<span class=\"web_ttl\">마음로그人 33호</span><a onclick=\"fn_fileDown('original.pdf','/2025/09/20250918142603470496_2.pdf','10128');\">다운로드</a>공공누리";
        org.json.JSONArray items=NCMHArticles.parse(html);assertEquals(1,items.length());assertEquals("마음로그人 33호",items.getJSONObject(0).getString("title"));assertEquals("2025.09",items.getJSONObject(0).getString("month"));
        for(String path:new String[]{"/2025/09/../secret.pdf","/2025/09/%2e%2e.pdf","https://evil.test/a.pdf","/2025/09/file.exe"})assertFalse(NCMHArticles.validPath(path));
        assertTrue(NCMHArticles.pdfURL(items.getJSONObject(0).getString("path")).startsWith("https://www.ncmh.go.kr/"));
        try{NCMHArticles.parse("<html/> ");fail("Invalid list");}catch(java.io.IOException expected){}
    }
    @Test public void nativeBrowserAllowsOnlyOfficialHTTPSOrigins(){for(String url:new String[]{"file:///private","http://www.ncmh.go.kr/a","https://www.ncmh.go.kr.evil.test/a","https://user@www.ncmh.go.kr/a","https://www.ncmh.go.kr:444/a"})assertFalse(NativePublicReader.allowed(url));assertTrue(NativePublicReader.allowed("https://www.ncmh.go.kr/ncmh/main.do"));}
    @Test public void filtersUnsafeFundingAndDuplicateEntries()throws Exception {
        String xml="<feed xmlns='http://www.w3.org/2005/Atom'><entry><title>Useful &amp; public</title><link rel='alternate' href='https://www.nimh.nih.gov/news/a'/><published>2026-10-14</published></entry><entry><title>Duplicate</title><link rel='alternate' href='https://www.nimh.nih.gov/news/a'/></entry><entry><title>Funding</title><link rel='alternate' href='https://www.nimh.nih.gov/funding/a'/></entry><entry><title>Unsafe</title><link rel='alternate' href='https://evil.test/news/a'/></entry></feed>";
        org.json.JSONArray items=MentalPublicResources.parse(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(1,items.length());assertEquals("Useful & public",items.getJSONObject(0).getString("title"));
        assertEquals("2026-10-14",items.getJSONObject(0).getString("date"));
    }
    @Test public void rejectsImpersonationAndNonFeedDocuments()throws Exception {
        for(String url:new String[]{"http://www.nimh.nih.gov/news/a","https://www.nimh.nih.gov.evil.test/news/a","https://user@www.nimh.nih.gov/news/a","https://www.nimh.nih.gov:444/news/a"})assertFalse(MentalPublicResources.allowed(url));
        try{MentalPublicResources.parse("<html/>".getBytes());fail("Not a feed");}catch(java.io.IOException expected){}
    }
}
