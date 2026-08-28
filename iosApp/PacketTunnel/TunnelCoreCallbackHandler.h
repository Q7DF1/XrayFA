#import <Foundation/Foundation.h>
#import <LibXrayLite/Libv2ray.objc.h>

/** ObjC subclass so gomobile can assign a Go ref. Swift subclasses crash with go_seq_go_to_refnum. */
@interface TunnelCoreCallbackHandler : Libv2rayCoreCallbackHandler
@end
