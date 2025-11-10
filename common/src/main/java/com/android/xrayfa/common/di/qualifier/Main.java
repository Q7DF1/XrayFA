package com.android.xrayfa.common.di.qualifier;


import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.inject.Qualifier;

/**
 * This annotation indicates that the thread or coroutine described is related to the UI.
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
public @interface Main {
}
