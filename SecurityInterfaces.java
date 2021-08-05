package com.jci.xlauncher.forflutter;

import android.text.TextUtils;

import com.jci.cola.utils.ColaLog;
import com.jci.xlauncher.database.SecurityBean;
import com.jci.xlauncher.database.SecurityDBDao;
import com.jci.xlauncher.database.SimpleUserBean;
import com.jci.xlauncher.security.SecurityUtils;
import com.jci.xlauncher.security.XmsSecurityManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.flutter.plugin.common.MethodChannel;

/*
 * (C) Copyright 2019 Johnson Controls, Inc.
 * Use or copying of all or any part of the document, except as
 * permitted by the License Agreement is prohibited.
 */
public class SecurityInterfaces {
    //SecurityDBDao
    private static final String LOG_TAG = SecurityInterfaces.class.getSimpleName();
    private static final String METHOD_CLEAR_ALL = "clearAll";
    private static final String METHOD_CLEAR_ALL_WITHOUT = "clearAllWithout";
    private static final String METHOD_DELETE_USER_BY_INDEX = "deleteUserByIndex";
    private static final String METHOD_QUERY_SIMPLE_UER_BEAN_BY_ID = "querySimpleUserBeanById";
    private static final String METHOD_QUERY_BEAN_BY_ID = "queryBeanById";
    private static final String METHOD_DELETE_USER_BY_LIST = "deleteUserByList";
    private static final String METHOD_CREATE_USER = "createUser";
    private static final String METHOD_QUERY_BY_NAME = "queryByName";
    private static final String METHOD_UPDATE_USER = "updateUser";
    private static final String METHOD_GET_ADMIN_COUNT = "getAdminCount";
    private static final String METHOD_GET_USER_COUNT = "getUserCount";
    private static final String METHOD_QUERY_SIMPLE_USER_BEAN_ABOVE_LEVEL = "querySimpleUserBeanAboveLevel";
    private static final String METHOD_QUERY_ALL_TO_SIMPLE_USER_BEAN = "queryAllToSimpleUserBean";
    private static final String METHOD_QUERY_ALL = "queryAll";

    //SecurityUtils
    private static final String METHOD_IS_USER_NAME_VALID = "isUserNameValid";
    private static final String METHOD_IS_PASSWORD_VALID = "isPasswordValid";
    private static final String METHOD_IS_PIN_VALID = "isPinValid";
    private static final String METHOD_CHECK_ACCESS_CODE = "checkAccessCode";

    //XmsSecurityManager
    private static final String METHOD_IS_LOCKED = "isLocked";
    private static final String METHOD_LOGIN_BY_NAME = "loginByName";
    private static final String METHOD_LOGIN_BY_ID = "loginById";
    private static final String METHOD_VERIFY = "verify";
    private static final String METHOD_LOGOUT = "logout";
    private static final String METHOD_GET_LOGIN_ID = "getLoginId";
    private static final String METHOD_LOGIN_DEFAULT_ADMIN = "loginDefaultAdmin";
    private static final String METHOD_GET_LOGIN_LEVEL = "getLoginLevel";
    private static final String METHOD_ENCRYPT = "encrypt";
    private static final String METHOD_DECRYPT = "decrypt";

    public void invokeMethod(String methodName, Object arguments, MethodChannel.Result result) {
        ColaLog.d(LOG_TAG, "methodName = " + methodName + " arguments = "+ arguments);
        if (result == null) {
            ColaLog.d(LOG_TAG, "result is null");
            return;
        }
        if (TextUtils.isEmpty(methodName)) {
            result.error("the method name is null", "", "");
            return;
        }
        switch (methodName) {
            case METHOD_CLEAR_ALL:
                SecurityDBDao.getInstance().clearAll();
                break;
            case METHOD_CLEAR_ALL_WITHOUT:
                if (arguments instanceof String) {
                    SecurityDBDao.getInstance().clearAllWithout((String) arguments);
                } else {
                    result.error("the argument is invalid", "", "");
                }
                break;
            case METHOD_DELETE_USER_BY_INDEX:
                if (arguments instanceof Integer) {
                    SecurityDBDao.getInstance().deleteUserByIndex((Integer) arguments);
                } else {
                    result.error("the argument is invalid", "", "");
                }
                break;
            case METHOD_QUERY_SIMPLE_UER_BEAN_BY_ID:
                if (arguments instanceof Integer) {
                    SimpleUserBean bean = SecurityDBDao.getInstance().querySimpleUserBeanById((Integer) arguments);
                    result.success(bean.toMap());
                } else {
                    result.error("the argument is invalid", "", "");
                }
                break;
            case METHOD_QUERY_BEAN_BY_ID:
                if (arguments instanceof Integer) {
                    SecurityBean bean = SecurityDBDao.getInstance().queryBeanById((Integer) arguments);
                    result.success(bean.toMap());
                } else {
                    result.error("the argument is invalid", "", "");
                }
                break;
            case METHOD_DELETE_USER_BY_LIST:
                if (arguments instanceof ArrayList) {
                    result.success(SecurityDBDao.getInstance().deleteUserByList((ArrayList<Integer>) arguments));
                } else {
                    result.error("the argument is invalid", "", "");
                }
                break;
            case METHOD_CREATE_USER:
                if (arguments instanceof HashMap) {
                    SecurityBean bean = new SecurityBean();
                    if (bean.loadFromMap((HashMap<String, Object>) arguments)) {
                        result.success(SecurityDBDao.getInstance().createUser(bean));
                    } else {
                        result.error("the argument map is invalid", "", "");
                    }
                } else {
                    result.error("the argument is invalid", "", "");
                }
                break;
            case METHOD_QUERY_BY_NAME:
                if (arguments instanceof String) {
                    SecurityBean bean = SecurityDBDao.getInstance().queryByName((String) arguments);
                    result.success(bean);
                } else {
                    result.error("the argument is invalid", "", "");
                }
                break;
            case METHOD_UPDATE_USER:
                if (arguments instanceof HashMap) {
                    SecurityBean bean = new SecurityBean();
                    if (bean.loadFromMap((HashMap<String, Object>) arguments)) {
                        result.success(SecurityDBDao.getInstance().updateUser(bean));
                    } else {
                        result.error("the argument map is invalid", "", "");
                    }
                } else {
                    result.error("the argument is invalid", "", "");
                }
                break;
            case METHOD_GET_ADMIN_COUNT:
                result.success(SecurityDBDao.getInstance().getAdminCount());
                break;
            case METHOD_GET_USER_COUNT:
                result.success(SecurityDBDao.getInstance().getUserCount());
                break;
            case METHOD_QUERY_SIMPLE_USER_BEAN_ABOVE_LEVEL:
                if (arguments instanceof Integer) {
                    List<SimpleUserBean> beanList = SecurityDBDao.getInstance().querySimpleUserBeanAboveLevel((Integer) arguments);
                    List<HashMap<String, Object>> usersList = new ArrayList<>();
                    for (SimpleUserBean bean : beanList) {
                        usersList.add(bean.toMap());
                    }
                    result.success(usersList);
                } else {
                    result.error("the argument is invalid", "", "");
                }
                break;
            case METHOD_QUERY_ALL_TO_SIMPLE_USER_BEAN:
                if (arguments instanceof Integer) {
                    List<SimpleUserBean> beanList = SecurityDBDao.getInstance().queryAllToSimpleUserBean((Integer) arguments);
                    List<HashMap<String, Object>> usersList = new ArrayList<>();
                    for (SimpleUserBean bean : beanList) {
                        usersList.add(bean.toMap());
                    }
                    result.success(usersList);
                } else {
                    result.error("the argument is invalid", "", "");
                }
                break;
            case METHOD_QUERY_ALL:
                List<SecurityBean> beanList = SecurityDBDao.getInstance().queryAll();
                List<HashMap<String, Object>> usersList = new ArrayList<>();
                for (SecurityBean bean : beanList) {
                    usersList.add(bean.toMap());
                }
                result.success(usersList);
                break;

            case METHOD_IS_USER_NAME_VALID:
                if (arguments instanceof String) {
                    result.success(SecurityUtils.isUserNameValid((String) arguments));
                } else {
                    result.error("the argument is invalid", "", "");
                }
                break;
            case METHOD_IS_PASSWORD_VALID:
                if (arguments instanceof String) {
                    result.success(SecurityUtils.isPasswordValid((String) arguments));
                } else {
                    result.error("the argument is invalid", "", "");
                }
                break;
            case METHOD_IS_PIN_VALID:
                if (arguments instanceof String) {
                    result.success(SecurityUtils.isPinValid((String) arguments));
                } else {
                    result.error("the argument is invalid", "", "");
                }
                break;
            case METHOD_CHECK_ACCESS_CODE:
                if (arguments instanceof String) {
                    result.success(SecurityUtils.checkAccessCode((String) arguments));
                } else {
                    result.error("the argument is invalid", "", "");
                }
                break;
            case METHOD_IS_LOCKED:
                result.success(XmsSecurityManager.getInstance().isLocked());
                break;
            case METHOD_LOGIN_BY_NAME:
                if (arguments instanceof HashMap) {
                    HashMap<String, String> map = (HashMap<String, String>) arguments;
                    result.success(XmsSecurityManager.getInstance()
                            .loginByName(map.get("userName"), map.get("password")));
                } else {
                    result.error("the argument is invalid", "", "");
                }
                break;
            case METHOD_LOGIN_BY_ID:
                if (arguments instanceof HashMap) {
                    HashMap<String, Object> map = (HashMap<String, Object>) arguments;
                    if (map.get("userId") instanceof Integer && map.get("password") instanceof String) {
                        result.success(XmsSecurityManager.getInstance()
                                .loginById((Integer) map.get("userId"), (String) map.get("password")));
                    }
                } else {
                    result.error("the argument is invalid", "", "");
                }
                break;
            case METHOD_VERIFY:
                if (arguments instanceof HashMap) {
                    HashMap<String, Object> map = (HashMap<String, Object>) arguments;
                    if (map.get("userId") instanceof Integer && map.get("password") instanceof String) {
                        result.success(XmsSecurityManager.getInstance()
                                .verify((Integer) map.get("userId"), (String) map.get("password")));
                    }
                } else {
                    result.error("the argument is invalid", "", "");
                }
                break;
            case METHOD_LOGOUT:
                XmsSecurityManager.getInstance().logout();
                break;
            case METHOD_GET_LOGIN_ID:
                result.success(XmsSecurityManager.getInstance().getLoginId());
                break;
            case METHOD_LOGIN_DEFAULT_ADMIN:
                XmsSecurityManager.getInstance().loginDefaultAdmin();
                break;
            case METHOD_GET_LOGIN_LEVEL:
                result.success(XmsSecurityManager.getInstance().getLoginLevel());
                break;
            case METHOD_ENCRYPT:
                if(arguments instanceof byte[]) {
                    result.success(XmsSecurityManager.getInstance().encrypt((byte[]) arguments));
                } else {
                    result.error("the argument is invalid", "", "");
                }
                break;
            case METHOD_DECRYPT:
                if(arguments instanceof byte[]) {
                    result.success(XmsSecurityManager.getInstance().decrypt((byte[]) arguments));
                } else {
                    result.error("the argument is invalid", "", "");
                }
                break;
            default:
                result.error("the argument is invalid", "", "");
                break;
        }
    }

}
