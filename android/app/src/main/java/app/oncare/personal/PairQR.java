package app.oncare.personal;

import android.graphics.Bitmap;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.util.EnumMap;

final class PairQR {
    static Bitmap image(String code, int size) throws Exception {
        if (code == null || !code.startsWith("oncare1:") || code.length() > 2048 || size < 200 || size > 2048) throw new IllegalArgumentException("pair QR");
        EnumMap<EncodeHintType,Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8"); hints.put(EncodeHintType.MARGIN, 4);
        BitMatrix matrix = new QRCodeWriter().encode(code, BarcodeFormat.QR_CODE, size, size, hints);
        int[] pixels = new int[size*size];
        for(int y=0;y<size;y++) for(int x=0;x<size;x++) pixels[y*size+x]=matrix.get(x,y)?0xff000000:0xffffffff;
        return Bitmap.createBitmap(pixels,size,size,Bitmap.Config.ARGB_8888);
    }
}
