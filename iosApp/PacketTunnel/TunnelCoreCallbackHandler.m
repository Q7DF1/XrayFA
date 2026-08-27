#import "TunnelCoreCallbackHandler.h"

static NSString * const kAppGroupSuite = @"group.com.android.xrayfa";
static NSString * const kLastErrorKey = @"vpn_tunnel_last_error";
static NSString * const kStatusKey = @"vpn_tunnel_status_message";

static NSUserDefaults *appGroupDefaults(void) {
    return [[NSUserDefaults alloc] initWithSuiteName:kAppGroupSuite];
}

static BOOL looksLikeFailure(long code, NSString *message) {
    if (code != 0) {
        return YES;
    }
    NSString *lower = [message lowercaseString];
    return [lower containsString:@"fail"]
        || [lower containsString:@"error"]
        || [lower containsString:@"stopped unexpectedly"];
}

@implementation TunnelCoreCallbackHandler

- (long)onEmitStatus:(long)p0 p1:(NSString *)p1 {
    NSString *text = [p1 stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]] ?: @"";
    if (text.length == 0) {
        return 0;
    }
    [appGroupDefaults() setObject:text forKey:kStatusKey];
    if (looksLikeFailure(p0, text)) {
        [appGroupDefaults() setObject:text forKey:kLastErrorKey];
    }
    return 0;
}

- (long)shutdown {
    [appGroupDefaults() setObject:@"Core shutdown" forKey:kStatusKey];
    return 0;
}

- (long)startup {
    [appGroupDefaults() setObject:@"Core startup" forKey:kStatusKey];
    [appGroupDefaults() removeObjectForKey:kLastErrorKey];
    return 0;
}

@end
