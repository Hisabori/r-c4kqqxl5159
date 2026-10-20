import XCTest

final class DeepMoodFlowTests: XCTestCase {
    func testStudioSelectionOpensEditableDraftWithoutSaving() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-app-deep-mind", "YES", "-app-start-tab", "1", "-app-deep-style", "미드나잇", "-AppleInterfaceStyle", "Light"]
        app.launch()
        XCTAssertTrue(app.staticTexts["멀쩡한 척,\n여기선 잠깐 꺼도 돼."].waitForExistence(timeout: 20))
        try capture("deep-mood-330-room")
        let feeling = app.buttons["deep-mood-emotion-외로움"]
        reach(feeling, in: app); feeling.tap()
        XCTAssertEqual(feeling.value as? String, "선택됨")
        let context = app.buttons["deep-mood-context-연락"]
        reach(context, in: app); context.tap()
        try capture("deep-mood-330-tags")
        let write = app.buttons["deep-mind-write"]
        reach(write, in: app); write.tap()
        let note = app.descendants(matching: .any)["record-note"].firstMatch
        XCTAssertTrue(note.waitForExistence(timeout: 10))
        XCTAssertTrue((note.value as? String ?? "").contains("섞인 감정: 외로움"))
        XCTAssertTrue((note.value as? String ?? "").contains("상황: 연락"))
        XCTAssertTrue(app.buttons["record-save"].exists)
        try capture("deep-mood-330-draft")
        app.buttons["닫기"].firstMatch.tap()
        app.terminate()
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
