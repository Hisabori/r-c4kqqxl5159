package app.oncare.personal;
import org.junit.Test;import static org.junit.Assert.*;import java.util.*;
public class MedicationBagTextTest {
 @Test public void separatesCandidatesAndPreservesOriginal(){String text="타이레놀정 500mg\n아침 식후 30분 복용\n환자 성명 홍길동\n전화 010-1234-5678\n타이레놀정 500mg";assertEquals(Collections.singletonList("타이레놀정 500mg"),MedicationBagText.candidates(text,false));assertEquals(Collections.singletonList("아침 식후 30분 복용"),MedicationBagText.candidates(text,true));}
 @Test public void rejectsPrivateLinesEvenWithMedicineOrMethod(){String text="환자 아침 복용\n성명 타이레놀정\n010-1234-5678 복용\n주소 서울정\nfoo@example.com 식후";assertTrue(MedicationBagText.candidates(text,false).isEmpty());assertTrue(MedicationBagText.candidates(text,true).isEmpty());}
 @Test public void unknownLinesAreNotGuessed(){assertTrue(MedicationBagText.candidates("홍길동\n2026-10-28\n병원",false).isEmpty());assertTrue(MedicationBagText.candidates("타이레놀정",true).isEmpty());}
 @Test public void appendPreservesLinesAndAvoidsExactDuplicate(){assertEquals("아침\n식후",MedicationBagText.append("식후","아침"));assertEquals("아침\n식후",MedicationBagText.append(" 식후 ","아침\n식후"));}
 @Test public void appendRejectsOverflowWithoutTruncation(){assertNull(MedicationBagText.append("추가","가".repeat(200)));assertEquals("기존",MedicationBagText.append("","기존"));}
 @Test public void selectedTextKeepsLines(){assertEquals("A정 10mg\n식후 복용",MedicationBagText.importedText("  A정 10mg\n식후 복용  "));}
 @Test public void selectedTextRejectsEmptyNullAndOversizedRows(){assertNull(MedicationBagText.importedText(null));assertNull(MedicationBagText.importedText(" \n"));assertNull(MedicationBagText.importedText("A\0B"));assertNull(MedicationBagText.importedText("가".repeat(501)));}
 @Test public void selectedTextRejectsTooManyLinesAndHugePayload(){assertNull(MedicationBagText.importedText(String.join("\n",Collections.nCopies(101,"A"))));assertNull(MedicationBagText.importedText("A".repeat(12001)));assertNotNull(MedicationBagText.importedText("A".repeat(500)));}
}