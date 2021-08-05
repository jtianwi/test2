package com.jci.xlauncher.forflutter;

import android.text.TextUtils;

import com.jci.cola.utils.ColaLog;

import io.flutter.plugin.common.MethodChannel;

/*
 * (C) Copyright 2019 Johnson Controls, Inc.
 * Use or copying of all or any part of the document, except as
 * permitted by the License Agreement is prohibited.
 *
 * This class is used to parse the commands of RPC from flutter,
 * and invoke the corresponding method if it is parsed successfully
 */
public class RpcInterfaces {
    private static final String LOG_TAG = RpcInterfaces.class.getSimpleName();
    private static final String METHOD_CREATE_ZERO_OFFSET = "createCaliOffset";
    private static final String METHOD_RESET_ZERO_OFFSET = "resetCaliOffset";

    public void invokeMethod(String methodName, Object arguments, MethodChannel.Result result) {
        ColaLog.d(LOG_TAG, "methodName = " + methodName + " arguments = " + arguments);
        if (result == null) {
            ColaLog.d(LOG_TAG, "result is null");
            return;
        }
        if (TextUtils.isEmpty(methodName)) {
            result.error("the method name is null", "", "");
            return;
        }
        switch (methodName) {
            case METHOD_CREATE_ZERO_OFFSET:
                break;
            case METHOD_RESET_ZERO_OFFSET:

                break;
            default:
                result.error("unknow method : " + methodName, "", "");
                break;
        }
    }
}
