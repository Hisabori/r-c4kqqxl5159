import XCTest
@testable import OnCare
final class HealthOverviewTests: XCTestCase {
    func testManualOnlyTodayAndMissingStayUnknown() {
        let now = Date()
        let rows = HealthOverviewMetrics.rows([Entry(date: now, kind: "물", value: 200), Entry(date: now, kind: "물", value: 300), Entry(id: "hc:x", date: now, kind: "물", value: 999), Entry(date: now, kind: "수면", value: 8, source: "health-connect:test")], focus: "물", now: now)
        XCTAssertEqual(rows[0].value, 500)
        XCTAssertNil(rows.first { $0.kind == "수면" }?.value)
        XCTAssertEqual(rows.filter { $0.value != nil }.count, 1)
    }
    func testLatestMoodAndSleepAreNotAddedAndFutureIgnored() {
        let now = Date()
        let rows = HealthOverviewMetrics.rows([Entry(id: "a", date: now.addingTimeInterval(-2), kind: "마음", value: 1), Entry(id: "b", date: now, kind: "마음", value: 4), Entry(date: now.addingTimeInterval(20), kind: "마음", value: 5), Entry(date: now, kind: "수면", value: .nan)], focus: "마음", now: now)
        XCTAssertEqual(rows.first?.kind, "마음"); XCTAssertEqual(rows.first?.value, 4)
        XCTAssertNil(rows.first { $0.kind == "수면" }?.value)
    }
    func testCalendarWindowCountsDaysNotNumberOfRecords() {
        var calendar = Calendar(identifier: .gregorian); calendar.timeZone = TimeZone(identifier: "America/Los_Angeles")!
        let now = calendar.date(from: DateComponents(year: 2026, month: 11, day: 3, hour: 12))!
        let start = calendar.date(byAdding: .day, value: -6, to: calendar.startOfDay(for: now))!
        let rows = HealthOverviewMetrics.rows([Entry(date: start, kind: "운동", value: 20), Entry(date: start.addingTimeInterval(1), kind: "운동", value: 30), Entry(date: start.addingTimeInterval(-1), kind: "운동", value: 60)], focus: "unknown", now: now, calendar: calendar)
        XCTAssertEqual(rows.first?.kind, "물")
        XCTAssertEqual(rows.first { $0.kind == "운동" }?.recordedDays, 1)
        XCTAssertNil(rows.first { $0.kind == "운동" }?.value)
    }
}
