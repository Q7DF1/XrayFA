#import "XrayFAMeasureOutboundDelay.h"
#import "Libv2ray.objc.h"

int64_t XrayFAMeasureOutboundDelay(NSString *configJson, NSString *url) {
    int64_t delayMs = -1;
    NSError *error = nil;
    BOOL ok = Libv2rayMeasureOutboundDelay(configJson, url, &delayMs, &error);
    return ok ? delayMs : (int64_t)-1;
}
