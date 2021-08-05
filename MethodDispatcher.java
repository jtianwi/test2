package com.jci.xlauncher.forflutter;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.icu.text.DateFormatSymbols;
import android.os.Build;
import android.os.HandlerThread;
import android.os.LocaleList;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.widget.Toast;

import com.jci.cola.dictionary.ClassAttr;
import com.jci.cola.services.core.Endpoint;
import com.jci.cola.services.jnbi.OreObject;
import com.jci.cola.services.jnbi.OreParmData;
import com.jci.cola.utils.ColaLog;
import com.jci.rmlauncher.common.locale.LocalePersist;
import com.jci.xlauncher.MainActivity;
import com.jci.xlauncher.MyApplication;
import com.jci.xlauncher.MyConstants;
import com.jci.xlauncher.MyEnvironment;
import com.jci.xlauncher.data.MyOreData;
import com.jci.xlauncher.demo.DemoModeService;
import com.jci.xlauncher.providers.LocalDataProvider;
import com.jci.xlauncher.providers.SPConstants;
import com.jci.xlauncher.providers.XmsProvider;
import com.jci.xlauncher.ui.GpdlToast;
import com.jci.xlauncher.ui.RebootLoadingDialog;
import com.jci.xlauncher.utils.MyUtils;
import com.jci.xlauncher.utils.OreUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;
import java.util.Objects;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;

import static com.jci.xlauncher.MyConstants.ORIENTATION_LANDSCAPE;
import static com.jci.xlauncher.MyConstants.ORIENTATION_PORTRAIT;
import static com.jci.xlauncher.data.MyOreData.DEFAULT_DEVICE_TAG;
import static com.jci.xlauncher.forflutter.FlutterConstants.PARAM_MAC_ADDRESS;
import static com.jci.xlauncher.providers.SPConstants.FLUTTER_PREFERENCE_NAME;
import static com.jci.xlauncher.providers.SPConstants.FLUTTER_PREFIX;
import static com.jci.xlauncher.providers.SPConstants.MODEL_NUMBER;
import static com.jci.xlauncher.providers.SPConstants.SETUP_WIZARD_INDEX;

public class MethodDispatcher {
    private static final String CHANNEL = "com.jci.xlauncher/methods";
    private static final String READ = "read";
    private static final String WRITE = "write";
    private static final String READ_INTERFACE = "read_interface";
    private static final String UN_OBSERVE = "unobs";
    private static final String BUZZER = "buzzer";
    private static final String HALO = "halo";
    private static final String WRITE_SYSTEM = "write_system";
    private static final String READ_SYSTEM = "read_system";
    private static final String SECURITY = "security";
    private static final String RPC = "rpc";
    private static final String TOUCH = "touch";
    private static final String IS_MONKEY = "is_monkey";
    private static final String IS_USER_BUILD = "is_user_build";
    private static final String IS_OUT_OF_FACTORY = "is_out_of_factory";
    private static final String OUT_OF_FACTORY = "out_of_factory";
    private static final String IS_DISCOVERY_DONE = "is_discovery_done";
    private static final String GET_ORE_STATUS = "get_ore_status";
    private static final String DEMO_SWITCH = "demo_switch";
    private static final String SCREEN_ORIENTATION = "screen_orientation";
    private static final String SYSTEM_LOCALE = "system_locale";
    private static final String SYSTEM_DATE = "system_date";
    private static final String SCREEN_BRIGHTNESS = "screen_brightness";
    private static final String SYSTEM_REBOOT = "system_reboot";
    private static final String BUILD_TIME = "build_time";
    private static final String SYSTEM_TOAST = "system_toast";
    private static final String DISPLAY_ESN = "display_esn";
    private static final String DISPLAY_FIRMWARE_VERSION = "display_firmware_version";
    private static final String WRITE_MAC_ADDRESS_FORCE = "write_mac_address_force";
    private static final String READ_TEMP_MAC_ADDRESS = "read_temp_mac_address";

    private static final String AM_PM_AT_START = "am_pm_start";
    private static final String AM_PM_STRINGS = "am_pm_strings";
    private static final String SHORT_MONTHS = "short_months";

    private static final String KEYBOARD = "keyboard";

    //The argument name
    private static final String ARG_EQUIP_NAME = "eq_name";
    private static final String ARG_PARAM_NAME = "para_name";
    private static final String ARG_VALUE = "value";
    //the class of the argument value, such as "TimePeriod"
    private static final String ARG_VALUE_CLASS = "value_class";
    private static final String ARG_METHOD_NAME = "method_name";
    private static final String ARG_INDEX = "index";
    private static final String LOG_TAG = MethodDispatcher.class.getSimpleName();

    private boolean mIsRebooting = false;

    private final MainActivity mActivity;
    private SecurityInterfaces mSecurityInterfaces;
    private RpcInterfaces mRpcInterface;
    private final HaloInterface mHaloInterface;
    private final BuzzerInterface mBuzzerInterface;
    private final WriteOreInterface mWriteOreInterface;

    public MethodDispatcher(MainActivity activity) {
        mActivity = activity;
        HandlerThread thread = new HandlerThread("HaloInterface");
        thread.start();
        mHaloInterface = new HaloInterface(thread.getLooper());

        HandlerThread thread1 = new HandlerThread("BuzzerInterface");
        thread1.start();
        mBuzzerInterface = new BuzzerInterface(thread1.getLooper());

        HandlerThread thread2 = new HandlerThread("WriteOreInterface");
        thread2.start();
        mWriteOreInterface = new WriteOreInterface(thread2.getLooper());

        initChannels();
    }

    public void cleanUp() {

    }

    private void initChannels() {
        new MethodChannel(mActivity.getMyFlutterEngine().getDartExecutor(), CHANNEL).setMethodCallHandler(
                (methodCall, result) -> {
                    ColaLog.d(LOG_TAG, "onMethodCall = " + methodCall.method);
                    dispatchMethodCall(methodCall, result);
                }
        );
    }

    public void dispatchMethodCall(MethodCall methodCall, MethodChannel.Result result) {
        if (mIsRebooting) {
            return;
        }

        String roomTag;
        String tileTag;
        String paraTag;
        Object value;
        switch (methodCall.method) {
            case READ:
                break;
            case WRITE:
                roomTag = methodCall.argument(FlutterConstants.ROOM_TAG);
                tileTag = methodCall.argument(FlutterConstants.TILE_TAG);
                paraTag = methodCall.argument(FlutterConstants.PARAM_TAG);
                value = methodCall.argument(FlutterConstants.PARAM_VALUE);
                ColaLog.d(LOG_TAG,
                        "write: roomTag : " + roomTag + " tileTag : " + tileTag + " paraTag : " + paraTag + " " +
                                "value : " + value);
                if (XmsProvider.getInstance().isDemo()) {
                    if (TextUtils.isEmpty(paraTag) || value == null) {
                        break;
                    }
                    if (!TextUtils.isEmpty(roomTag)) {
                        boolean isNotify = true;
                        switch (Objects.requireNonNull(paraTag)) {
                            case FlutterConstants.PARAM_ISOLATION:
                                LocalDataProvider.getInstance().set(roomTag + SPConstants.DEMO_ROOM_ISO,
                                        (int) value);
                                break;
                            case FlutterConstants.PARAM_OCCUPANCY:
                                LocalDataProvider.getInstance().set(roomTag + SPConstants.DEMO_ROOM_OCC,
                                        (int) value);
                                break;
                            case FlutterConstants.PARAM_IS_CLEANING:
                                LocalDataProvider.getInstance().set(roomTag + SPConstants.DEMO_ROOM_CLEANING, (int) value);
                                break;
                            case FlutterConstants.PARAM_SPT:
                                LocalDataProvider.getInstance().set(roomTag + tileTag + SPConstants.DEMO_ROOM_SPT, (float) ((double) value));
                                break;
                            default:
                                isNotify = false;
                                break;
                        }
                        if (isNotify) {
                            MyOreData data = new MyOreData("", MyOreData.TYPE_DEVICE,
                                    DEFAULT_DEVICE_TAG, roomTag, tileTag, paraTag,
                                    value);
                            MyUtils.notifyUI(data);
                        }
                    } else if (paraTag != null) {
                        if (paraTag.equals(PARAM_MAC_ADDRESS)) {
                            LocalDataProvider.getInstance().set(SPConstants.DEMO_MAC_ADDRESS,
                                    (int) value);
                            MyOreData data = new MyOreData("", MyOreData.TYPE_DEVICE,
                                    DEFAULT_DEVICE_TAG, roomTag, tileTag, paraTag,
                                    value);
                            MyUtils.notifyUI(data);
                            break;
                        }
                    }
                } else {
                    if (paraTag == null) {
                        result.error("paramTag is null", "paramTag can't be null", "");
                        break;
                    }
                    mWriteOreInterface.invokeMethod(result, DEFAULT_DEVICE_TAG,
                            roomTag, tileTag, paraTag, value);
                }
                break;
            case READ_INTERFACE:
                if (methodCall.argument(ARG_INDEX) instanceof Integer) {
                    int index = methodCall.argument(ARG_INDEX);
//                    OreParmData data = OreManager.getInstance().readLocalParam(index);
//                    if (data != null && !TextUtils.isEmpty(data.toString())) {
//                        result.success(data.toString());
//                        return;
//                    }
                    result.error("read interface " + index + " failed", "", "");
                } else {
                    result.error(ARG_INDEX + "can't be null", "", "");
                }
                break;
            case UN_OBSERVE:
                cleanUp();
                break;
            case BUZZER:
                if (!TextUtils.isEmpty((String) methodCall.arguments)) {
                    mBuzzerInterface.invokeMethod((String) methodCall.arguments, result);
                }
                break;
            case HALO:
                mHaloInterface.invokeMethod(methodCall.argument(ARG_METHOD_NAME),
                        methodCall.argument(ARG_VALUE), result);
                break;
            case WRITE_SYSTEM:
                String method = methodCall.argument(ARG_METHOD_NAME);
                if (!TextUtils.isEmpty(method)) {
                    switch (Objects.requireNonNull(method)) {
                        case SCREEN_ORIENTATION:
                            String orientation = methodCall.argument(ARG_VALUE);
                            if (!TextUtils.isEmpty(orientation)) {
                                setScreenOrientation(Integer.parseInt(orientation));
                            } else {
                                result.error("orientation should not be null", "", "");
                            }
                            break;
                        case SYSTEM_LOCALE:
                            String locale = methodCall.argument(ARG_VALUE);
                            if (!TextUtils.isEmpty(locale)) {
                                setSystemLocale(locale);
                            } else {
                                result.error("locale should not be null", "", "");
                            }
                            break;
                        case KEYBOARD:
                            String keyboardLocale = methodCall.argument(ARG_VALUE);
                            if (!TextUtils.isEmpty(keyboardLocale)) {
                                setKeyboardLocale(keyboardLocale);
                            } else {
                                result.error("locale should not be null", "", "");
                            }
                            break;
                        case SYSTEM_DATE:
                            String date = methodCall.argument("date");
                            String _24Hr = methodCall.argument("is24Hr");
                            if (TextUtils.isEmpty(date) && TextUtils.isEmpty(_24Hr)) {
                                result.error("date time and date format are both null",
                                        "", "");
                                return;
                            }
                            if (!TextUtils.isEmpty(date)) {
                                saveDateTime(date);
                            }
                            if (!TextUtils.isEmpty(_24Hr)) {
                                if ("24".equals(_24Hr)) {
                                    setSystemDate(true);
                                } else if ("12".equals(_24Hr)) {
                                    setSystemDate(false);
                                }
                            }
                            break;
                        case SCREEN_BRIGHTNESS:
                            String brightness = methodCall.argument(ARG_VALUE);
                            if (!TextUtils.isEmpty(brightness)) {
                                setScreenBrightness(Integer.parseInt(brightness));
                            } else {
                                result.error("brightness should not be null", "", "");
                            }
                            break;
                        case SYSTEM_REBOOT:
                            if (!mIsRebooting) {
                                mIsRebooting = true;
                                cleanUp();
                                MyUtils.rebootDevice(MyApplication.getInstance(),
                                        false,
                                        Endpoint.ShutdownReason.UNSPECIFIED.value);
                            }
                            break;
                        default:
                            break;
                    }
                } else {
                    result.error("method_name can't be null", "", "");
                }
                break;
            case READ_SYSTEM:
                String methods = methodCall.argument(ARG_METHOD_NAME);
                if (!TextUtils.isEmpty(methods)) {
                    switch (methods) {
                        case DISPLAY_ESN:
                            result.success(SystemProperties.get("ro.serialno", ""));
                            break;
                        case DISPLAY_FIRMWARE_VERSION:
                            String version =
                                    SystemProperties.get("ro.jci.version", "") + "-"
                                            + SystemProperties.get("ro.build.version.incremental");
                            result.success(version);
                            break;
                        case BUILD_TIME:
                            result.success(Build.TIME);
                            break;
                        case SYSTEM_LOCALE:
                            Locale locale = getCurrentLocale();
                            String country = locale.getCountry();
                            String language = locale.getLanguage();
                            if (!TextUtils.isEmpty(country) && !TextUtils.isEmpty(language)) {
                                result.success(language + "_" + country);
                            } else if (!TextUtils.isEmpty(country) && TextUtils.isEmpty(language)) {
                                result.success(country);
                            } else if (TextUtils.isEmpty(country) && !TextUtils.isEmpty(language)) {
                                result.success(language);
                            } else {
                                result.error("country and language are both null", "", "");
                            }
                            break;
                        case SCREEN_BRIGHTNESS:
                            int brightness = getScreenBrightness();
                            if (brightness <= 100 && brightness >= 0) {
                                result.success(brightness);
                            }
                            break;
                        case SYSTEM_DATE:
                            String dayFormat = null;
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.CUPCAKE) {
                                dayFormat = DateFormat.is24HourFormat(mActivity)
                                        ? "24" : "12";
                            }
                            if (TextUtils.equals("12", dayFormat)
                                    || TextUtils.equals("24", dayFormat)) {
                                result.success(dayFormat);
                            } else {
                                result.error("wrong day format", "", "");
                            }
                            break;
                        case AM_PM_AT_START:
                            Locale loc = getCurrentLocale();
                            String bestDateTimePattern;
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                                bestDateTimePattern = DateFormat.getBestDateTimePattern(loc,
                                        "hm" /* skeleton */);
                                result.success(bestDateTimePattern.startsWith("a"));
                            } else {
                                result.error("wrong time format", "", "");
                            }
                            break;
                        case AM_PM_STRINGS:
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                final Locale locale1 =
                                        mActivity.getResources().getConfiguration().getLocales().get(0);
                                android.icu.text.DateFormatSymbols symbols =
                                        android.icu.text.DateFormatSymbols.getInstance(locale1);
                                final String[] results = symbols.getAmPmStrings();
                                result.success(new ArrayList<String>(Arrays.asList(results)));
                            } else {
                                result.error("wrong time format", "", "");
                            }
                            break;
                        case SHORT_MONTHS:
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                final Locale locale1 =
                                        mActivity.getResources().getConfiguration().getLocales().get(0);
                                android.icu.text.DateFormatSymbols symbols =
                                        DateFormatSymbols.getInstance(locale1);
                                final String[] results = symbols.getMonths();
                                result.success(new ArrayList<>(Arrays.asList(results)));
                            } else {
                                result.error("wrong month format", "", "");
                            }
                            break;
                        case BUZZER:
//                            result.success(BuzzerManager.getInstance().());
                            break;
                        default:
                            break;
                    }
                }
                break;
            case SECURITY:
                if (mSecurityInterfaces == null) {
                    mSecurityInterfaces = new SecurityInterfaces();
                }
                mSecurityInterfaces.invokeMethod(methodCall.argument(ARG_METHOD_NAME),
                        methodCall.argument(ARG_VALUE), result);
                break;
            case RPC:
                if (mRpcInterface == null) {
                    mRpcInterface = new RpcInterfaces();
                }
                mRpcInterface.invokeMethod(methodCall.argument(ARG_METHOD_NAME),
                        methodCall.argument(ARG_VALUE), result);
                break;
            case SYSTEM_TOAST:
                String message = methodCall.argument("msg").toString();
                String length = methodCall.argument("length").toString();
                String gravity = methodCall.argument("gravity").toString();
                int duration;
                if (length.equals("long")) {
                    duration = Toast.LENGTH_LONG;
                } else {
                    duration = Toast.LENGTH_SHORT;
                }
                GpdlToast.show(mActivity, message, duration);
                result.success(true);
                break;
            case TOUCH:
                ColaLog.d(LOG_TAG, "touch screen");
                break;
            case IS_MONKEY:
                result.success(ActivityManager.isUserAMonkey());
                break;
            case IS_USER_BUILD:
                result.success(MyEnvironment.isUserBuild());
                break;
            case IS_OUT_OF_FACTORY:
                result.success(MyUtils.isOutOfFactory());
                break;
            case OUT_OF_FACTORY:
                handleOutOfFactory(result);
                break;
            case IS_DISCOVERY_DONE:
                result.success(XmsProvider.getInstance().isDiscoverDone());
                break;
            case GET_ORE_STATUS:
                result.success(XmsProvider.getInstance().getOreStatus());
                break;
            case DEMO_SWITCH:
                handleDemoSwitch();
                break;
            case WRITE_MAC_ADDRESS_FORCE:
                value = methodCall.argument(FlutterConstants.PARAM_VALUE);
                if (XmsProvider.getInstance().isDemo()) {
                    LocalDataProvider.getInstance().set(SPConstants.DEMO_MAC_ADDRESS,
                            (int) value);
                }
                boolean isSuccess = false;
                OreObject master = OreUtils.getMstpMaster();
                if (master != null) {
                    OreParmData data = new OreParmData();
                    data.setByte((byte) ((int) value));
                    isSuccess = master.setAttr(ClassAttr.MAC_ADDRESS_ATTR,
                            data);
                } else {
                    result.error("mstp master is null", "", "");
                }
                if (isSuccess) {
                    result.success(value);
                } else {
                    result.error("failed to write mac address", "", "");
                }
                break;
            case READ_TEMP_MAC_ADDRESS:
                if (XmsProvider.getInstance().isDemo()) {
                    result.success(LocalDataProvider.getInstance().getInt(SPConstants.DEMO_MAC_ADDRESS, 10));
                } else {
                    OreObject oreObject = OreUtils.getMstpMaster();
                    if (oreObject != null) {
                        OreParmData oreParmData = oreObject.getAttr(ClassAttr.MAC_ADDRESS_ATTR);
                        if (oreParmData != null) {
                            result.success((int) oreParmData.getByte());
                        } else {
                            result.error("failed to get mac address oreParamData is null", "", 0);
                        }
                    } else {
                        result.error("failed to get mac address oreObject is null", "", 0);
                    }
                }
                break;
            default:
                ColaLog.d(LOG_TAG, "the method name is invalid!");
                break;
        }
    }

    private void handleDemoSwitch() {
        Intent intent = new Intent();
        intent.setClass(mActivity, DemoModeService.class);
        if (DemoModeService.getInstance() == null) {
            mActivity.startService(intent);
        } else {
            mActivity.stopService(intent);
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private void setScreenOrientation(int orientation) {
        switch (orientation) {
            case ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:
                SystemProperties.set(MyConstants.FMSNG_ORIENTATION_PROPERTY,
                        ORIENTATION_PORTRAIT);
                mActivity.setRequestedOrientation(
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                break;
            case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
                SystemProperties.set(MyConstants.FMSNG_ORIENTATION_PROPERTY,
                        ORIENTATION_LANDSCAPE);
                mActivity.setRequestedOrientation(
                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                break;
        }
    }

    private void setSystemLocale(String locale) {
        Locale loc;
        String[] langCountry = locale.split("_");
        if (langCountry.length == 2) {
            loc = new Locale(langCountry[0], langCountry[1]);
        } else {
            loc = new Locale(locale);
        }
        LocalePersist.updateLocales(new LocaleList(loc));
    }

    private void setSystemDate(boolean is24Hr) {
        Settings.System.putString(mActivity.getContentResolver(),
                Settings.System.TIME_12_24, is24Hr ? "24" : "12");
    }

    private void saveDateTime(String date) {
        String[] time = date.split("/");
        if (time.length == 5) {
            Calendar c = Calendar.getInstance();
            c.set(Calendar.YEAR, Integer.parseInt(time[0]));
            c.set(Calendar.MONTH, Integer.parseInt(time[1]));
            c.set(Calendar.DAY_OF_MONTH, Integer.parseInt(time[2]));
            c.set(Calendar.HOUR_OF_DAY, Integer.parseInt(time[3]));
            c.set(Calendar.MINUTE, Integer.parseInt(time[4]));
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            AlarmManager mgr =
                    ((AlarmManager) mActivity.getSystemService(Context.ALARM_SERVICE));
            if (mgr != null) {
                mgr.setTime(c.getTimeInMillis());
            }
        }
    }

    private void setScreenBrightness(int screenBrightness) {
        PowerManager pm = (PowerManager) mActivity.getSystemService(Context.POWER_SERVICE);
        if (pm == null) {
            return;
        }
        int minBrightness = pm.getMinimumScreenBrightnessSetting();
        int maxBrightness = pm.getMaximumScreenBrightnessSetting();
        int range = maxBrightness - minBrightness;
        Settings.System.putInt(mActivity.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS,
                minBrightness + (int) (range * (screenBrightness + 0.5f)) / 100);
    }

    private Locale getCurrentLocale() {
        LocaleList local = LocalePersist.getLocales();
        Locale currentLocal;
        if (local.size() > 0) {
            currentLocal = local.get(0);
        } else {
            currentLocal = Locale.ENGLISH;
        }
        return currentLocal;
    }

    private int getScreenBrightness() {
        PowerManager pm = (PowerManager) mActivity.getSystemService(Context.POWER_SERVICE);
        if (pm == null) {
            return 0;
        }
        int minBrightness = pm.getMinimumScreenBrightnessSetting();
        int maxBrightness = pm.getMaximumScreenBrightnessSetting();
        int defaultBrightness = pm.getDefaultScreenBrightnessSetting();
        int range = maxBrightness - minBrightness;
        int currentBrightness = Settings.System.getInt(
                mActivity.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS,
                defaultBrightness);
        currentBrightness = (int) (100f * (Math.min(currentBrightness,
                maxBrightness) - minBrightness) / range + 0.5);
        return currentBrightness;
    }

    private void handleOutOfFactory(MethodChannel.Result result) {
        if (result != null) {
            mIsRebooting = true;
            //Step 1 update the out of factory flag
            MyUtils.updateOutOfFactory();

            SharedPreferences spf
                    = mActivity.getSharedPreferences(FLUTTER_PREFERENCE_NAME, Context.MODE_PRIVATE);

            //Step 2 get model number ,then will write it again after clear user data
            String modelNumber = spf.getString(FLUTTER_PREFIX + MODEL_NUMBER, "LB-CE360-TB");
            ColaLog.d(LOG_TAG, "handleOutOfFactory : get model number : " + modelNumber);

            //Step 3 show animation
            ColaLog.d(LOG_TAG, "handleOutOfFactory : show animation");
            RebootLoadingDialog dialog = new RebootLoadingDialog();
            dialog.show(mActivity.getFragmentManager(),
                    RebootLoadingDialog.class.getSimpleName());

            //Step 4 remove the user data include ore files
            ColaLog.d(LOG_TAG, "handleOutOfFactory : clearUserData");
            MyUtils.clearUserData(mActivity);

            //Step 5 change the setup wizard index to initial setup and set model number
            ColaLog.d(LOG_TAG, "handleOutOfFactory : initial setup");
            spf.edit().putString(FLUTTER_PREFIX + MODEL_NUMBER, modelNumber).apply();
            spf.edit().putLong(FLUTTER_PREFIX + SETUP_WIZARD_INDEX,
                    MyConstants.SETUP_WIZARD_INDEX_OF_INITIAL_SETUP).apply();

            //Step 6 reset the language to English
            ColaLog.d(LOG_TAG, "handleOutOfFactory : reset the language to English");
            LocalePersist.updateLocales(new LocaleList(Locale.ENGLISH));

            //Step 7 reboot device
            ColaLog.d(LOG_TAG, "handleOutOfFactory : reboot device");
            MyUtils.rebootDevice(mActivity,
                    false, Endpoint.ShutdownReason.UNSPECIFIED.value);
        }
    }

    private void setKeyboardLocale(String locale) {
        Locale loc;
        String[] langCountry = locale.split("_");
        if (langCountry.length == 2) {
            loc = new Locale(langCountry[0], langCountry[1]);
        } else {
            loc = new Locale(locale);
        }
        ((MyApplication) MyApplication.getInstance()).changeKeyboardWithLocale(loc);
    }

}
