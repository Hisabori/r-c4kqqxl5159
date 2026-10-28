package app.oncare.personal;
import android.content.Context;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import java.io.*;
import java.util.*;
final class MedicationBagText {
    static ArrayList<String> lines(String text){ArrayList<String> out=new ArrayList<>();for(String line:text.split("\\R")){line=line.trim();if(!line.isEmpty()){out.add(line.substring(0,Math.min(500,line.length())));if(out.size()==100)break;}}return out;}
    static ArrayList<String> candidates(String text,boolean instructions){
        ArrayList<String> out=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(String line:lines(text)){
            String value=java.text.Normalizer.normalize(line,java.text.Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
            boolean privateLine=value.contains("@")||value.matches(".*\\d{2,4}[- ]\\d{3,4}[- ]\\d{4}.*");
            for(String term:new String[]{"환자","성명","생년","주민","전화","주소","면허","사업자","처방전번호"})privateLine|=value.contains(term);
            if(privateLine)continue;
            boolean method=false;for(String term:new String[]{"식전","식후","식사","취침","복용","하루","매일","아침","점심","저녁","1일","회분"})method|=value.contains(term);
            boolean drug=java.util.regex.Pattern.compile("[가-힣a-z][가-힣a-z0-9 .·()-]*(정|캡슐|시럽|산|액|mg|mcg|밀리그램)(\\s|\\d|$)").matcher(value).find();
            if((instructions?method:drug&&!method)&&seen.add(value))out.add(line);
        }return out;
    }
    static final class Photo {final Bitmap bitmap;final int rotation;Photo(Bitmap b,int r){bitmap=b;rotation=r;}}
    static Photo photo(Context context,Uri uri)throws IOException {
        BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;
        try(InputStream in=context.getContentResolver().openInputStream(uri)){if(in==null)throw new IOException();BitmapFactory.decodeStream(in,null,opts);}
        if(opts.outWidth<=0||opts.outHeight<=0)throw new IOException();int sample=1;while(Math.max(opts.outWidth,opts.outHeight)/sample>2048)sample*=2;
        opts.inJustDecodeBounds=false;opts.inSampleSize=sample;Bitmap bitmap;
        try(InputStream in=context.getContentResolver().openInputStream(uri)){if(in==null)throw new IOException();bitmap=BitmapFactory.decodeStream(in,null,opts);}if(bitmap==null)throw new IOException();
        int rotation=0;try(InputStream in=context.getContentResolver().openInputStream(uri)){if(in!=null){int o=new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION,1);rotation=o==6?90:o==3?180:o==8?270:0;}}catch(IOException ignored){}
        return new Photo(bitmap,rotation);
    }
}
