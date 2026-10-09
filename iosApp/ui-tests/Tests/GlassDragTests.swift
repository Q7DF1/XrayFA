import XCTest
import UIKit

final class GlassDragTests: XCTestCase {
    func testSubscriptionsReturnLayout() throws {
        guard #available(iOS 26.0, *) else { throw XCTSkip("Native floating tabs require iOS 26") }
        let app = XCUIApplication(bundleIdentifier: "com.android.xrayfa.ios")
        app.launch()
        let bar = app.tabBars.firstMatch
        XCTAssertTrue(bar.waitForExistence(timeout: 15))
        Thread.sleep(forTimeInterval: 2)
        let nativeHeight = bar.frame.height
        XCTAssertGreaterThanOrEqual(nativeHeight, 70)
        for attempt in 1...3 {
            bar.buttons.element(boundBy: 1).tap()
            let entry = app.buttons.matching(NSPredicate(format: "label == %@ OR label == %@", "订阅", "Subscription")).firstMatch
            // Native interop accessibility may mark the entry as unhittable;
            // a physical coordinate tap exercises the actual touch path.
            let point = entry.frame
            app.coordinate(withNormalizedOffset: CGVector(dx: 0, dy: 0)).withOffset(CGVector(dx: point.midX, dy: point.midY)).tap()
            XCTAssertFalse(bar.exists)
            app.coordinate(withNormalizedOffset: CGVector(dx: 0, dy: 0)).withOffset(CGVector(dx: 28, dy: 91)).tap()
            XCTAssertTrue(bar.waitForExistence(timeout: 5))
            Thread.sleep(forTimeInterval: 2)
            let shot = XCTAttachment(screenshot: app.screenshot())
            shot.name = "Subscriptions return \(attempt)"
            shot.lifetime = .keepAlways
            add(shot)
            XCTAssertEqual(bar.frame.height, nativeHeight, accuracy: 0.5, "Navigation must retain the native content and safe-area height")
            bar.buttons.element(boundBy: 1).tap()
            let homeScreenshot = app.screenshot()
            let homeShot = XCTAttachment(screenshot: homeScreenshot)
            homeShot.name = "Home after subscriptions return \(attempt)"
            homeShot.lifetime = .keepAlways
            add(homeShot)
            assertHomeCaptionSeparated(app, home: bar.buttons.element(boundBy: 1), screenshot: homeScreenshot)
        }
    }

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
    }

    private func assertHomeCaptionSeparated(_ app: XCUIApplication, home: XCUIElement, screenshot: XCUIScreenshot) {
        guard let image = screenshot.image.cgImage else { return XCTFail("Missing screenshot pixels") }
        let scale = CGFloat(image.width) / app.frame.width
        let frame = home.frame
        let rect = CGRect(x: frame.minX * scale, y: frame.minY * scale,
                          width: frame.width * scale, height: frame.height * scale).integral
        guard let crop = image.cropping(to: rect) else { return XCTFail("Missing Home item crop") }
        let width = crop.width
        let height = crop.height
        var pixels = [UInt8](repeating: 0, count: width * height * 4)
        let rendered = pixels.withUnsafeMutableBytes { bytes -> Bool in
            guard let context = CGContext(data: bytes.baseAddress, width: width, height: height,
                                          bitsPerComponent: 8, bytesPerRow: width * 4,
                                          space: CGColorSpaceCreateDeviceRGB(),
                                          bitmapInfo: CGBitmapInfo.byteOrder32Big.rawValue | CGImageAlphaInfo.premultipliedLast.rawValue)
            else { return false }
            context.draw(crop, in: CGRect(x: 0, y: 0, width: width, height: height))
            return true
        }
        XCTAssertTrue(rendered)
        // The selected blue globe has continuous occupied rows. Its caption
        // must form a separate band below it; the reproduced overlap merges
        // both into a single band even though the native outer frame is valid.
        var bands = 0
        var previous = false
        for y in 0..<height {
            var count = 0
            for x in 0..<width {
                let offset = (y * width + x) * 4
                let red = Int(pixels[offset])
                let green = Int(pixels[offset + 1])
                let blue = Int(pixels[offset + 2])
                if blue > red + 60 && blue > green + 25 && green > 50 { count += 1 }
            }
            let occupied = count >= 2
            if occupied && !previous { bands += 1 }
            previous = occupied
        }
        XCTAssertGreaterThanOrEqual(bands, 2, "Home icon and caption must have a visible vertical gap after subscriptions return")
    }
}
