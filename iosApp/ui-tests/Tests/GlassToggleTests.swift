import XCTest

final class GlassToggleTests: XCTestCase {
    func testNativeToggleTapDragAndPersistence() throws {
        let app = XCUIApplication(bundleIdentifier: "com.android.xrayfa.ios")
        app.launch()
        openSettings(app)
        let disabled = app.switches.matching(NSPredicate(format: "label == %@ OR label == %@", "Agent 功能", "Agent functions")).firstMatch
        XCTAssertTrue(disabled.waitForExistence(timeout: 5))
        XCTAssertFalse(disabled.isEnabled)
        let toggle = app.switches.matching(NSPredicate(format: "label CONTAINS[c] %@", "IPv6")).firstMatch
        reveal(toggle, in: app)
        XCTAssertTrue(toggle.waitForExistence(timeout: 5))
        XCTAssertTrue(toggle.isEnabled)
        let original = toggle.value as? String
        XCTAssertNotNil(original)
        defer {
            if toggle.exists && toggle.value as? String != original { tap(toggle, in: app) }
        }
        XCTAssertGreaterThan(toggle.frame.width, 40)
        XCTAssertGreaterThan(toggle.frame.height, 25)
        tap(toggle, in: app)
        XCTAssertNotEqual(toggle.value as? String, original, "A native tap must propagate into shared state")
        toggle.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).press(forDuration: 2)
        // A long native press also toggles on release; subsequent assertions start from the actual state.
        for _ in 0..<2 {
            let beforeDrag = toggle.value as? String
            let startX: CGFloat = beforeDrag == "1" ? 0.8 : 0.2
            let endX: CGFloat = beforeDrag == "1" ? 0.05 : 0.95
            toggle.coordinate(withNormalizedOffset: CGVector(dx: startX, dy: 0.5)).press(
                forDuration: 1,
                thenDragTo: toggle.coordinate(withNormalizedOffset: CGVector(dx: endX, dy: 0.5)),
                withVelocity: .slow,
                thenHoldForDuration: 1
            )
            XCTAssertNotEqual(toggle.value as? String, beforeDrag, "UIKit drag must update shared state")
        }
        let current = toggle.value as? String
        app.terminate()
        app.launch()
        openSettings(app)
        reveal(toggle, in: app)
        XCTAssertTrue(toggle.waitForExistence(timeout: 5))
        XCTAssertEqual(toggle.value as? String, current, "Toggle state must survive relaunch")
        let shot = XCTAttachment(screenshot: app.screenshot())
        shot.name = "Native glass toggle settings"
        shot.lifetime = .keepAlways
        add(shot)
    }

    private func reveal(_ element: XCUIElement, in app: XCUIApplication) {
        for _ in 0..<4 {
            if element.exists && element.frame.midY > 130 && element.frame.midY < app.frame.height - 100 { return }
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.2, dy: 0.8)).press(
                forDuration: 0.05,
                thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.2, dy: 0.35))
            )
        }
    }

    private func openSettings(_ app: XCUIApplication) {
        let bar = app.tabBars.firstMatch
        XCTAssertTrue(bar.waitForExistence(timeout: 15))
        bar.buttons.element(boundBy: 1).tap()
        let settings = app.buttons["Settings"].firstMatch
        XCTAssertTrue(settings.waitForExistence(timeout: 5))
        tap(settings, in: app)
    }

    private func tap(_ element: XCUIElement, in app: XCUIApplication) {
        let frame = element.frame
        app.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(dx: frame.midX, dy: frame.midY)).tap()
    }
}
