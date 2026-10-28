package app.oncare.personal;
import org.junit.Test;import static org.junit.Assert.*;import java.util.*;
public class MedicationBagTextTest {
 @Test public void separatesCandidatesAndPreservesOriginal(){String text="타이레놀정 500mg\n아침 식후 30분 복용\n환자 성명 홍길동\n전화 010-1234-5678\n타이레놀정 500mg";assertEquals(Collections.singletonList("타이레놀정 500mg"),MedicationBagText.candidates(text,false));assertEquals(Collections.singletonList("아침 식후 30분 복용"),MedicationBagText.candidates(text,true));}
 @Test public void rejectsPrivateLinesEvenWithMedicineOrMethod(){String text="환자 아침 복용\n성명 타이레놀정\n010-1234-5678 복용\n주소 서울정\nfoo@example.com 식후";assertTrue(MedicationBagText.candidates(text,false).isEmpty());assertTrue(MedicationBagText.candidates(text,true).isEmpty());}
 @Test public void unknownLinesAreNotGuessed(){assertTrue(MedicationBagText.candidates("홍길동\n2026-10-28\n병원",false).isEmpty());assertTrue(MedicationBagText.candidates("타이레놀정",true).isEmpty());}
}