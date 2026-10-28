import XCTest
@testable import OnCare
final class MedicationBagTextTests: XCTestCase {
 func testSeparatesCandidatesAndPreservesOriginal() {
  let text = "타이레놀정 500mg\n아침 식후 30분 복용\n환자 성명 홍길동\n전화 010-1234-5678\n타이레놀정 500mg"
  XCTAssertEqual(MedicationBagText.candidates(text, instructions: false), ["타이레놀정 500mg"])
  XCTAssertEqual(MedicationBagText.candidates(text, instructions: true), ["아침 식후 30분 복용"])
 }
 func testPrivateAndUnknownLinesAreNotSuggested() {
  let text = "환자 아침 복용\n성명 타이레놀정\n010-1234-5678 복용\n주소 서울정\nfoo@example.com 식후"
  XCTAssertTrue(MedicationBagText.candidates(text, instructions: false).isEmpty)
  XCTAssertTrue(MedicationBagText.candidates(text, instructions: true).isEmpty)
  XCTAssertTrue(MedicationBagText.candidates("홍길동\n병원", instructions: false).isEmpty)
 }
}
