package app.oncare.personal;
import org.junit.Test;import org.junit.runner.RunWith;import org.robolectric.RobolectricTestRunner;import org.robolectric.annotation.Config;import android.content.Intent;import android.net.Uri;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=26)
public class MedicationTextShareTest {
 @Test public void receivesOnlyExplicitSelectedPlainText(){Intent i=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"A정\n식후 복용");assertEquals("A정\n식후 복용",MedicationTextShareActivity.selected(i));}
 @Test public void rejectsStreamsWrongActionsAndHugeText(){assertNull(MedicationTextShareActivity.selected(null));assertNull(MedicationTextShareActivity.selected(new Intent(Intent.ACTION_SEND_MULTIPLE).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"A정")));assertNull(MedicationTextShareActivity.selected(new Intent(Intent.ACTION_SEND).setType("text/html").putExtra(Intent.EXTRA_TEXT,"A정")));assertNull(MedicationTextShareActivity.selected(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"A정").putExtra(Intent.EXTRA_STREAM,Uri.parse("content://test/file"))));assertNull(MedicationTextShareActivity.selected(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"A".repeat(12001))));}
}
