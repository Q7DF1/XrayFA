#ifndef XRAYFA_MEASURE_OUTBOUND_DELAY_H
#define XRAYFA_MEASURE_OUTBOUND_DELAY_H

#import <Foundation/Foundation.h>
#include <stdint.h>

/**
 * Wraps Libv2rayMeasureOutboundDelay so Kotlin/Native does not need the
 * unimportable int64_t* out-parameter. Returns -1 on failure.
 */
int64_t XrayFAMeasureOutboundDelay(NSString * _Nullable configJson, NSString * _Nullable url);

#endif
