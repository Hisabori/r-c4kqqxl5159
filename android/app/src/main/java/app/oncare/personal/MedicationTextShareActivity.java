package app.oncare.personal;
import android.app.Activity;import android.os.Bundle;import android.content.Intent;import android.widget.Toast;
public class MedicationTextShareActivity extends Activity {
    static String selected(Intent intent){try{if(intent==null||!Intent.ACTION_SEND.equals(intent.getAction())||!"text/plain".equals(intent.getType())||intent.hasExtra(Intent.EXTRA_STREAM))return null;CharSequence text=intent.getCharSequenceExtra(Intent.EXTRA_TEXT);if(text==null||text.length()>12000)return null;return MedicationBagText.importedText(text.toString());}catch(RuntimeException e){return null;}}
    @Override public void onCreate(Bundle state){super.onCreate(state);String value=selected(getIntent());setIntent(new Intent());if(state==null&&value!=null)startActivity(new Intent(this,MedicationActivity.class).putExtra("oncare-selected-medication-text",value));else if(state==null)Toast.makeText(this,"선택한 문자 내용만 가져올 수 있어요. 복사·붙여넣기도 사용할 수 있어요.",Toast.LENGTH_LONG).show();finish();}
}
