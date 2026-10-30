import XCTest

final class DeepMoodFlowTests: XCTestCase {
    func testStudioSelectionOpensEditableDraftWithoutSaving() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-app-deep-mind", "YES", "-app-start-tab", "1", "-app-deep-style", "미드나잇", "-AppleInterfaceStyle", "Light"]
        app.launch()
        XCTAssertTrue(app.staticTexts["deep-mind-card"].waitForExistence(timeout: 20))
        try capture("deep-mood-330-room")
        let feeling = app.buttons["deep-mood-emotion-외로움"]
        reach(feeling, in: app); feeling.tap()
        XCTAssertEqual(feeling.value as? String, "선택됨")
        let context = app.buttons["deep-mood-context-연락"]
        reach(context, in: app); context.tap()
        try capture("deep-mood-330-tags")
        let expand = app.staticTexts["조금 더 깊이 · 마음의 속사정"].firstMatch
        reach(expand, in: app); expand.tap()
        let detail = app.descendants(matching: .any)["deep-reflection-0"].firstMatch
        reach(detail, in: app); detail.tap(); detail.typeText("답장이 늦었다")
        app.buttons["deep-reflection-done"].tap()
        try capture("deep-mood-330-reflection")
        let review = app.staticTexts["감정 전후 돌아보기 · 임시보관"].firstMatch
        reach(review, in: app); review.tap()
        let action = app.descendants(matching: .any)["deep-review-0"].firstMatch
        reach(action, in: app); action.tap(); action.typeText("잠깐 산책했다")
        app.buttons["deep-reflection-done"].tap()
        let saveDraft = app.buttons["deep-draft-save"]
        reach(saveDraft, in: app); saveDraft.tap()
        XCTAssertTrue(app.staticTexts["deep-draft-status"].exists)
        let restoreDraft = app.buttons["deep-draft-restore"]
        reach(restoreDraft, in: app); restoreDraft.tap(); app.buttons["보관본 불러오기"].tap()
        let deleteDraft = app.buttons["deep-draft-delete"]
        reach(deleteDraft, in: app); deleteDraft.tap(); app.buttons["보관본 삭제"].tap()
        try capture("deep-mood-330-review")
        let write = app.buttons["deep-mind-write"]
        reach(write, in: app); write.tap()
        let note = app.descendants(matching: .any)["record-note"].firstMatch
        XCTAssertTrue(note.waitForExistence(timeout: 10))
        XCTAssertTrue((note.value as? String ?? "").contains("섞인 감정: 외로움"))
        XCTAssertTrue((note.value as? String ?? "").contains("상황: 연락"))
        XCTAssertTrue((note.value as? String ?? "").contains("실제로 있었던 일: 답장이 늦었다"))
        XCTAssertTrue((note.value as? String ?? "").contains("해본 행동: 잠깐 산책했다"))
        XCTAssertTrue(app.buttons["record-save"].exists)
        try capture("deep-mood-330-draft")
        app.buttons["닫기"].firstMatch.tap()
        app.terminate()
    }
    func testThemeIsAppliedAcrossTabsAndNormalModeReturns() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-app-deep-mind", "YES", "-app-start-tab", "0", "-app-theme", "light", "-app-deep-style", "로즈"]
        app.launch()
        XCTAssertTrue(app.staticTexts["오늘도 네 편이야 ♡"].waitForExistence(timeout: 20))
        try capture("deep-mood-330-global-home")
        app.tabBars.buttons["건강"].tap()
        XCTAssertTrue(app.navigationBars["건강"].waitForExistence(timeout: 10))
        try capture("deep-mood-330-global-health")
        app.tabBars.buttons["기록"].tap()
        XCTAssertTrue(app.navigationBars["기록"].waitForExistence(timeout: 10))
        try capture("deep-mood-330-global-history")
        app.terminate()
        app.launchArguments = ["-app-deep-mind", "NO", "-app-start-tab", "0", "-app-theme", "light"]
        app.launch()
        XCTAssertTrue(app.staticTexts["오늘도, 나답게"].waitForExistence(timeout: 20))
        try capture("deep-mood-330-normal-home")
        app.terminate()
    }
    func testStepDashboardCanOpenWithoutHealthPermission() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-app-deep-mind", "NO", "-app-start-tab", "2", "-step-source-v1", "apple"]
        app.launch()
        let open = app.descendants(matching: .any)["steps-open"].firstMatch
        reach(open, in: app); open.tap()
        XCTAssertTrue(app.staticTexts["steps-today"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.steppers["steps-goal"].exists)
        try capture("steps-dashboard")
        app.buttons["닫기"].firstMatch.tap()
        app.terminate()
    }
    func testHealthOverviewOpensEditableRecordWithoutSaving() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-app-deep-mind", "NO", "-app-start-tab", "2"]
        app.launch()
        XCTAssertTrue(app.staticTexts["health-overview-count"].waitForExistence(timeout: 20))
        let focus = app.segmentedControls["health-overview-focus"]
        XCTAssertTrue(focus.exists); focus.buttons["마음"].tap()
        app.buttons["health-day-prev"].tap()
        XCTAssertNotEqual(app.staticTexts["health-day-label"].label, "오늘")
        XCTAssertFalse(app.buttons["health-overview-next"].exists)
        try capture("health-day-review")
        let today = app.buttons["health-day-today"]
        reach(today, in: app); today.tap()
        XCTAssertEqual(app.staticTexts["health-day-label"].label, "오늘")
        try capture("health-overview")
        let open = app.buttons["health-overview-next"]
        reach(open, in: app); open.tap()
        XCTAssertTrue(app.buttons["record-save"].waitForExistence(timeout: 10))
        app.buttons["닫기"].firstMatch.tap()
        app.terminate()
    }
    func testShortDeepWritingOpensAnEditableTemplateWithoutSaving() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-app-deep-mind", "YES", "-app-start-tab", "1", "-app-deep-focus", "외로움"]
        app.launch()
        let open = app.buttons["deep-short-write"]
        reach(open, in: app); open.tap()
        let note = app.descendants(matching: .any)["record-note"].firstMatch
        XCTAssertTrue(note.waitForExistence(timeout: 10))
        XCTAssertTrue((note.value as? String ?? "").contains("[짧게 남긴 마음]"))
        XCTAssertTrue((note.value as? String ?? "").contains("외로움"))
        XCTAssertTrue(app.buttons["record-save"].exists)
        try capture("deep-short-draft")
        app.buttons["닫기"].firstMatch.tap(); app.terminate()
    }
    func testProgressDestinationsOpenWithoutStartingMicrophoneOrLiveActivity() throws {
        let app = XCUIApplication(); app.launchArguments += ["-sound-live-v1", "NO", "-steps-live-v1", "NO"]; app.launch()
        XCUIDevice.shared.system.open(URL(string: "oncare://open/sleep-sound")!)
        XCTAssertTrue(app.buttons["sleep-sound-start"].waitForExistence(timeout: 15))
        XCTAssertEqual(app.switches["sleep-live-toggle"].value as? String, "0")
        try capture("sleep-sound-progress")
        app.buttons["닫기"].firstMatch.tap()
        XCUIDevice.shared.system.open(URL(string: "oncare://open/steps")!)
        XCTAssertTrue(app.staticTexts["steps-today"].waitForExistence(timeout: 15))
        XCTAssertTrue(app.switches["steps-live-toggle"].exists)
        try capture("steps-progress")
        app.buttons["닫기"].firstMatch.tap(); app.terminate()
    }
    func testMedicationTextImportRequiresReviewWithoutSaving() throws {
        let app = XCUIApplication(); app.launchArguments += ["-app-start-tab", "2"]; app.launch()
        XCUIDevice.shared.system.open(URL(string: "oncare://open/medication")!)
        let add = app.buttons["medication-add"]; XCTAssertTrue(add.waitForExistence(timeout: 15)); reach(add, in: app); add.tap()
        app.buttons["medication-text-open"].tap()
        let input = app.textViews["medication-text-input"]; XCTAssertTrue(input.waitForExistence(timeout: 10)); input.tap(); input.typeText("A정 10mg\n식후 복용")
        app.buttons["medication-text-done"].tap()
        let review = app.buttons["medication-text-review"]; reach(review, in: app); review.tap()
        XCTAssertTrue(app.staticTexts["A정 10mg"].firstMatch.waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["medication-ocr-apply"].isEnabled)
        try capture("medication-text-import")
    }
    private func reach(_ element: XCUIElement, in app: XCUIApplication) {
        for _ in 0..<12 { if element.isHittable { return }; app.swipeUp() }
        XCTAssertTrue(element.isHittable)
    }
    private func capture(_ name: String) throws {
        let folder = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        try XCUIScreen.main.screenshot().pngRepresentation.write(to: folder.appendingPathComponent(name + ".png"))
    }
}
