import XCTest

final class GlassDragTests: XCTestCase {
    func testRealNativeTabDragging() throws {
        guard #available(iOS 26.0, *) else { throw XCTSkip("Liquid Glass requires iOS 26") }
        let app = XCUIApplication(bundleIdentifier: "com.android.xrayfa.ios")
        app.launch()
        let bar = app.tabBars.firstMatch
        XCTAssertTrue(bar.waitForExistence(timeout: 15))
        let config = bar.buttons.element(boundBy: 0)
        let home = bar.buttons.element(boundBy: 1)
        XCTAssertTrue(config.exists)
        XCTAssertTrue(home.exists)
        let before = XCTAttachment(screenshot: app.screenshot())
        before.name = "Before native drag"
        before.lifetime = .keepAlways
        add(before)
        for _ in 0..<3 {
            home.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).press(
                forDuration: 0.8,
                thenDragTo: config.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)),
                withVelocity: .slow,
                thenHoldForDuration: 0.7
            )
            XCTAssertTrue(config.isSelected, "Dragging must select Config through UIKit")
            config.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).press(
                forDuration: 0.8,
                thenDragTo: home.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)),
                withVelocity: .slow,
                thenHoldForDuration: 0.7
            )
            XCTAssertTrue(home.isSelected, "Dragging must select Home through UIKit")
        }
        let after = XCTAttachment(screenshot: app.screenshot())
        after.name = "After native drag"
        after.lifetime = .keepAlways
        add(after)
        // A physical coordinate tap also checks native interop hit testing.
        let entry = app.buttons.matching(NSPredicate(format: "label == %@ OR label == %@", "订阅", "Subscription")).firstMatch
        let point = entry.frame
        app.coordinate(withNormalizedOffset: CGVector(dx: 0, dy: 0)).withOffset(CGVector(dx: point.midX, dy: point.midY)).tap()
        XCTAssertFalse(bar.exists, "The native navigation must be removed on subscriptions")
        app.coordinate(withNormalizedOffset: CGVector(dx: 0, dy: 0)).withOffset(CGVector(dx: 28, dy: 91)).tap()
        XCTAssertTrue(bar.waitForExistence(timeout: 5), "The native navigation must return with its owning page")
        // A fresh process must retain full item layout, not just pass selection
        // checks while UIKit compresses captions over the icons.
        app.terminate()
        app.launch()
        XCTAssertTrue(bar.waitForExistence(timeout: 15))
        Thread.sleep(forTimeInterval: 5)
        XCTAssertGreaterThanOrEqual(bar.frame.height, 70, "The native host must retain full icon/caption layout height")
        XCUIDevice.shared.press(.home)
        app.activate()
        XCTAssertTrue(bar.waitForExistence(timeout: 5))
        XCTAssertGreaterThanOrEqual(bar.frame.height, 70, "Resuming must retain the full native host height")
        let relaunched = XCTAttachment(screenshot: app.screenshot())
        relaunched.name = "Full tab layout after cold relaunch"
        relaunched.lifetime = .keepAlways
        add(relaunched)
    }
}
