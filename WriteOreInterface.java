package com.jci.xlauncher.forflutter;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;

import com.jci.cola.dictionary.ClassAttr;
import com.jci.cola.services.jnbi.OreObject;
import com.jci.cola.services.jnbi.OreParmData;
import com.jci.cola.utils.ColaLog;
import com.jci.xlauncher.data.MyOreData;
import com.jci.xlauncher.data.XmsModel;
import com.jci.xlauncher.providers.XmsProvider;
import com.jci.xlauncher.utils.MyUtils;
import com.jci.xlauncher.utils.OreUtils;

import io.flutter.plugin.common.MethodChannel;

import static com.jci.xlauncher.data.MyOreData.DEFAULT_DEVICE_TAG;
import static com.jci.xlauncher.forflutter.FlutterConstants.PARAM_MAC_ADDRESS;

/*
 * (C) Copyright 2021 Johnson Controls, Inc.
 * Use or copying of all or any part of the document, except as
 * permitted by the License Agreement is prohibited.
 *
 * This class is used to handle the write operation from flutter,
 * and invoke the corresponding method if it is parsed successfully
 */
public class WriteOreInterface extends Handler {
    private static final String LOG_TAG = WriteOreInterface.class.getSimpleName();
    private static final int MSG_WRITE = 1;
    private static final int TIME_OUT = 3 * 60 * 1000;
    private static final Object mLocker = new Object();
    private Integer mMsgCount = 0;
    private long mLastTime = 0;

    public WriteOreInterface(Looper looper) {
        super(looper);
    }

    @Override
    public void handleMessage(Message msg) {
        super.handleMessage(msg);
        synchronized (mLocker) {
            mMsgCount--;
        }

        if (msg.what == MSG_WRITE && msg.obj instanceof ArgObject) {
            ArgObject argObject = (ArgObject) msg.obj;
            XmsModel xmsModel = XmsProvider.getInstance().getXmsModel();
            if (xmsModel != null) {
                OreObject object = xmsModel.getObjectByTags(argObject.devTag,
                        argObject.roomTag, argObject.tileTag, argObject.paraTag);
                if (object != null) {
                    OreParmData data = new OreParmData();
                    Object value = argObject.value;
                    if (value instanceof Byte) {
                        data.setByte((byte) value);
                    } else if (value instanceof Double) {//All the float value
                        // will be sent from flutter as double type
                        data.setFloat((float) ((double) value));
                    } else if (value instanceof Integer) {
                        data.setEnum((int) value);
                    } else if (value instanceof Boolean) {
                        data.setBool((boolean) value);
                    } else {
                        ColaLog.e(LOG_TAG, "unsupported data type");
                        return;
                    }
                    object.setAttr(ClassAttr.PRESENT_VALUE_ATTR, data);
                } else {
                    ColaLog.e(LOG_TAG,
                            "can't find this object : roomTag = " + argObject.roomTag + " " +
                                    "tileTag = " + argObject.tileTag + "paramTag = " + argObject.paraTag);
                }
            }
        }
    }

    public void invokeMethod(MethodChannel.Result result, String deviceTag, String roomTag,
                             String tileTag, String paraTag, Object value) {
        ColaLog.i(LOG_TAG, "deviceTag = " + deviceTag + " roomTag = "
                + roomTag + " tileTag = " + tileTag + " paraTag = " + paraTag + " value = " + value);
        if (value == null) {
            ColaLog.e(LOG_TAG, "value is null");
            return;
        }

        if (paraTag == null) {
            ColaLog.e(LOG_TAG, "paraTag is null");
            return;
        }

        if (result == null) {
            ColaLog.e(LOG_TAG, "result is null");
            return;
        }

        if (PARAM_MAC_ADDRESS.equals(paraTag)) {
            boolean isSuccess = false;
            OreObject master = OreUtils.getMstpMaster();
            if (master != null) {
                OreParmData data = new OreParmData();
                data.setByte((byte) ((int) value));
                isSuccess = master.setAttr(ClassAttr.MAC_ADDRESS_ATTR,
                        data);
            } else {
                result.error("mstp master is null", "", 4);
            }
            if (isSuccess) {
                result.success(value);
                MyOreData data = new MyOreData("", MyOreData.TYPE_DEVICE,
                        DEFAULT_DEVICE_TAG, roomTag, tileTag, paraTag,
                        value);
                MyUtils.notifyUI(data);
            } else {
                result.error("failed to write mac address", "", 4);
            }

        } else {
            ArgObject args = new ArgObject(deviceTag, roomTag, tileTag, paraTag, value);
            synchronized (mLocker) {
                long cTime = SystemClock.elapsedRealtime();
                if (mMsgCount > 0 && cTime - mLastTime > TIME_OUT) {
                    ColaLog.e(LOG_TAG, "write ore time out current Time = "
                            + cTime + " lastTime = " + mLastTime);
//                    MyApplication.getInstance().forceReboot("write ore time out");
                } else {
                    mMsgCount++;
                    mLastTime = cTime;
                    obtainMessage(MSG_WRITE, args).sendToTarget();
                }
            }
        }
    }

    private static class ArgObject {
        String devTag;
        String roomTag;
        String tileTag;
        String paraTag;
        Object value;

        public ArgObject(String deviceTag, String roomTag,
                         String tileTag, String paraTag, Object value) {
            this.devTag = deviceTag;
            this.roomTag = roomTag;
            this.tileTag = tileTag;
            this.paraTag = paraTag;
            this.value = value;
        }
    }
}
