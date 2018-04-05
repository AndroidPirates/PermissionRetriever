/*
 * Copyright (c) 2018 Android Pirates
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.androidpirates.permissions.retriever;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.PluralsRes;
import android.support.annotation.StringRes;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PermissionRetriever {
    private final static String LOG_TAG = "PermissionRetriever";
    private final static int REQUEST_PERMISSIONS_CODE = 1689;

    @Nullable private Runnable mPendingIfGrantedAction;
    @Nullable private Runnable mPendingIfUnacceptedAction;
    @Nullable private android.support.v4.app.Fragment mAppCompatFragment;
    @Nullable private android.app.Fragment mFragment;
    @Nullable private Activity mActivity;
    private Boolean mIsLoggingEnabled;
    private Boolean mIsSilentMode;

    private boolean mIsRewriteProtectionDisabled = true;

    private Map<String, Object> mPermissionsRationalesMap = new HashMap<>();

    /**
     * Checks passed permission.
     *
     * @param permission permission for check
     * @param caller     an object who can be instantiated from {@link android.app.Fragment}
     *                   or {@link android.support.v4.app.Fragment}
     *                   or {@link android.app.Activity}
     * @return true if passed permission are grated
     * @throws IllegalArgumentException if passed caller not an instance of expected types
     * @see android.Manifest.permission
     * @see #hasAllPermissions(Object, String...)
     * @see #hasAllPermissions(Object, List)
     */
    public static boolean hasPermission(Object caller, String permission) {
        return hasPermission(getContextFromCaller(caller), permission);
    }

    /**
     * Checks all passed permissions.
     *
     * @param permissions array of permission for check
     * @param caller      an object who can be instantiated from {@link android.app.Fragment}
     *                    or {@link android.support.v4.app.Fragment}
     *                    or {@link android.app.Activity}
     * @return true if all passed permissions are grated
     * @throws IllegalArgumentException if passed caller not an instance of expected types
     * @see android.Manifest.permission
     * @see #hasPermission(Object, String)
     * @see #hasAllPermissions(Object, List)
     */
    public static boolean hasAllPermissions(Object caller, String... permissions) {
        Context context = getContextFromCaller(caller);
        for (String permission : permissions) {
            if (!hasPermission(context, permission)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks all passed permissions.
     *
     * @param permissions collection of permission for check
     * @param caller      an object who can be instantiated from {@link android.app.Fragment}
     *                    or {@link android.support.v4.app.Fragment}
     *                    or {@link android.app.Activity}
     * @return true if all passed permissions are grated
     * @throws IllegalArgumentException if passed caller not an instance of expected types
     * @see android.Manifest.permission
     * @see #hasPermission(Object, String)
     * @see #hasAllPermissions(Object, String...)
     */
    public static boolean hasAllPermissions(Object caller, List<String> permissions) {
        return hasAllPermissions(caller, permissions.toArray(new String[permissions.size()]));
    }

    /**
     * This method defines usage of "silent mode". It means {@link AlertDialog} will not be called
     * after declining some permissions, also if {@code ifUnaccepted} is present, it will be called
     * immediately.
     * <p>
     * This setting overrides same global setting
     *
     * @param isSilentMode value for turning on/off "silent mode"
     * @return this instance for chained calls
     */
    public PermissionRetriever silentMode(boolean isSilentMode) {
        if (mIsRewriteProtectionDisabled) {
            mIsSilentMode = isSilentMode;
        } else {
            logRewriteProtectionEnabled();
        }
        return this;
    }

    /**
     * This method defines usage of error logging.
     * <p>
     * This setting overrides same global setting
     *
     * @param isLoggingEnabled for turning on/off logging
     * @return this instance for chained calls
     */
    public PermissionRetriever logging(boolean isLoggingEnabled) {
        if (mIsRewriteProtectionDisabled) {
            mIsLoggingEnabled = isLoggingEnabled;
        } else {
            logRewriteProtectionEnabled();
        }
        return this;
    }

    /**
     * This method puts a permission without an associated explanation for a request.
     *
     * @param permission name of permission from {@link android.Manifest.permission}
     * @return this instance for chained calls
     * @see android.Manifest.permission
     * @see #withPermission(String, String)
     */
    public PermissionRetriever withPermission(@NonNull String permission) {
        return withPermission(permission, null);
    }


    /**
     * This method puts a permission with associated resource id of explanation for a request.
     *
     * @param permission  name of permission from {@link android.Manifest.permission}
     * @param explanation some information about usage this permission, this part will be displayed
     *                    to user if request will be denied
     * @return this instance for chained calls
     * @see android.Manifest.permission
     * @see #withPermission(String)
     * @see #withPermission(String, String)
     */
    public PermissionRetriever withPermission(@NonNull String permission,
                                              @StringRes int explanation) {
        if (mIsRewriteProtectionDisabled) {
            if (!TextUtils.isEmpty(permission)) {
                mPermissionsRationalesMap.put(permission, explanation);
            }
        } else {
            logRewriteProtectionEnabled();
        }
        return this;
    }


    /**
     * This method puts a permission with associated string explanation for a request.
     *
     * @param permission  name of permission from {@link android.Manifest.permission}
     * @param explanation some information about usage this permission, this part will be displayed
     *                    to user if request will be denied
     * @return this instance for chained calls
     * @see android.Manifest.permission
     * @see #withPermission(String)
     * @see #withPermission(String, int)
     */
    public PermissionRetriever withPermission(@NonNull String permission,
                                              @Nullable String explanation) {
        if (mIsRewriteProtectionDisabled) {
            if (!TextUtils.isEmpty(permission)) {
                mPermissionsRationalesMap.put(permission, explanation);
            }
        } else {
            logRewriteProtectionEnabled();
        }
        return this;
    }

    /**
     * This method requests permissions and does nothing more, neither when a user decline it,
     * nor accept it.
     *
     * @param caller an object who can be instantiated from {@link android.app.Fragment}
     *               or {@link android.support.v4.app.Fragment}
     *               or {@link android.app.Activity}
     */
    public void run(@NonNull Object caller) {
        run(caller, null);
    }

    /**
     * This method requests permissions and if when a user accepts it invokes the presented block of
     * code.
     *
     * @param caller    an object who can be instantiated from {@link android.app.Fragment}
     *                  or {@link android.support.v4.app.Fragment}
     *                  or {@link android.app.Activity}
     * @param ifGranted the runnable who will be invoked when a user will accept the requested
     *                  permissions
     * @see #run(Object, Runnable)
     * @see #run(Object, Runnable, Runnable)
     */
    public void run(@NonNull Object caller, @Nullable Runnable ifGranted) {
        run(caller, ifGranted, null);
    }

    /**
     * This method requests permissions and if when a user accepts it invokes the presented blocks
     * of code.
     *
     * @param caller       an object who can be instantiated from {@link android.app.Fragment}
     *                     or {@link android.support.v4.app.Fragment}
     *                     or {@link android.app.Activity}
     * @param ifGranted    the runnable who will be invoked when a user will accept the requested
     *                     permissions
     * @param ifUnaccepted the runnable who will be invoked when a user will decline at least
     *                     one of the requested permissions
     * @see #run(Object)
     * @see #run(Object, Runnable)
     */
    public void run(@NonNull Object caller,
                    @Nullable Runnable ifGranted,
                    @Nullable Runnable ifUnaccepted) {
        if (mIsRewriteProtectionDisabled) {
            setTrueCaller(caller);
            mPendingIfGrantedAction = ifGranted;
            mPendingIfUnacceptedAction = ifUnaccepted;
            if (mIsSilentMode == null) {
                mIsSilentMode = Global.getInstance().mIsSilentMode;
            }
            if (mIsLoggingEnabled == null) {
                mIsLoggingEnabled = Global.getInstance().mIsLoggingEnabled;
            }
            mIsRewriteProtectionDisabled = false;
            checkAndRun();
        } else {
            logRewriteProtectionEnabled();
        }
    }

    /**
     * This method should be called in {@code onPermissionResult} of your {@code Fragment}
     * or {@code Activity}.
     * <p>
     * It checks request code and if it equaled {@link #REQUEST_PERMISSIONS_CODE} does some checks
     * for showing the {@link AlertDialog} or run {@code ifUnaccepted} block if it present and the
     * {@link #silentMode(boolean)} turned on.
     *
     * @param requestCode a request code delegated from {@code onPermissionResult} of your
     *                    {@code Fragment} or {@code Activity}
     * @return true if {@code requestCode} is equaled {@link #REQUEST_PERMISSIONS_CODE} and
     * {@code PermissionRetriever} will do some stuff
     */
    public boolean onPermissionResult(int requestCode) {
        if (requestCode != REQUEST_PERMISSIONS_CODE) {
            return false;
        } else {
            if (getContext() != null) {
                if (check()) {
                    runGranted();
                } else {
                    if (somePermissionPermanentlyDenied()) {
                        showRationaleToSettings();
                    } else {
                        showRationaleToRequest();
                    }
                }
            } else {
                if (mIsLoggingEnabled) {
                    Log.e(LOG_TAG, "Context is null. I'm gonna do nothing!!!");
                }
            }
            return true;
        }
    }

    /**
     * This method clears internal variables for make this instance are re-used. It will be called
     * automatically after calling {@code ifGranted} or {@code ifUnaccepted} blocks.
     */
    public void clear() {
        mPermissionsRationalesMap.clear();
        mIsSilentMode = false;
        mFragment = null;
        mIsSilentMode = null;
        mIsLoggingEnabled = null;
        mAppCompatFragment = null;
        mActivity = null;
        mPendingIfGrantedAction = null;
        mPendingIfUnacceptedAction = null;
        mIsRewriteProtectionDisabled = true;
    }

    private void logRewriteProtectionEnabled() {
        if (mIsLoggingEnabled) {
            Log.e(LOG_TAG, "Rewrite protection is enabled, call clear() for re-use this " +
                    "instance. @" + hashCode());
        }
    }

    private static boolean hasPermission(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    private static Context getContextFromCaller(Object caller) throws IllegalArgumentException {
        if (caller instanceof android.app.Fragment) {
            return ((android.app.Fragment) caller).getActivity();
        } else if (caller instanceof android.support.v4.app.Fragment) {
            return ((android.support.v4.app.Fragment) caller).getActivity();
        } else if (caller instanceof Activity) {
            return (Activity) caller;
        } else {
            throw new IllegalArgumentException("Cant get context from caller");
        }
    }

    private void setTrueCaller(Object caller) throws IllegalArgumentException {
        if (caller instanceof android.app.Fragment) {
            mFragment = (android.app.Fragment) caller;
        } else if (caller instanceof android.support.v4.app.Fragment) {
            mAppCompatFragment = (android.support.v4.app.Fragment) caller;
        } else if (caller instanceof Activity) {
            mActivity = (Activity) caller;
        } else {
            throw new IllegalArgumentException("Passed wrong caller object");
        }
    }

    private void checkAndRun() {
        if (getContext() != null) {
            if (check()) {
                runGranted();
            } else {
                if (shouldShowRationale()) {
                    showRationaleToRequest();
                } else {
                    request();
                }
            }
        } else {
            if (mIsLoggingEnabled) {
                Log.e(LOG_TAG, "Context is null. I'm gonna do nothing!!!");
            }
        }
    }

    private boolean check() {
        boolean allGranted = true;
        if (!mPermissionsRationalesMap.isEmpty()) {
            List<Map.Entry<String, Object>> granted = new ArrayList<>();
            for (Map.Entry<String, Object> entry : mPermissionsRationalesMap.entrySet()) {
                if (hasPermission(getContext(), entry.getKey())) {
                    granted.add(entry);
                } else {
                    allGranted = false;
                }
            }
            mPermissionsRationalesMap.entrySet().removeAll(granted);
        }
        return allGranted;
    }

    private void runGranted() {
        if (mPendingIfGrantedAction != null) {
            mPendingIfGrantedAction.run();
        }
        clear();
    }

    private void runUnaccepted() {
        if (mPendingIfUnacceptedAction != null) {
            mPendingIfUnacceptedAction.run();
        }
        clear();
    }

    private void request() {
        Set<String> permissionsSet = mPermissionsRationalesMap.keySet();
        requestPermissions(permissionsSet.toArray(new String[permissionsSet.size()]));
    }

    @SuppressWarnings("ConstantConditions")
    private void requestPermissions(String[] permissions) {
        if (mAppCompatFragment != null) {
            mAppCompatFragment.requestPermissions(permissions, REQUEST_PERMISSIONS_CODE);
        } else if (mFragment != null) {
            if (Build.VERSION.SDK_INT >= 23) {
                mFragment.requestPermissions(permissions, REQUEST_PERMISSIONS_CODE);
            } else {
                if (mIsLoggingEnabled) {
                    Log.e(LOG_TAG, "Current sdk < 23 ver api and used platform's Fragment. " +
                            "Request permissions wasn't called");
                }
            }
        } else {
            ActivityCompat.requestPermissions(mActivity, permissions, REQUEST_PERMISSIONS_CODE);
        }
    }

    private boolean somePermissionPermanentlyDenied() {
        for (String permission : mPermissionsRationalesMap.keySet()) {
            if (!shouldShowRequestPermissionRationale(permission)) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldShowRationale() {
        for (String permission : mPermissionsRationalesMap.keySet()) {
            if (shouldShowRequestPermissionRationale(permission)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("ConstantConditions")
    private boolean shouldShowRequestPermissionRationale(String permission) {
        if (mAppCompatFragment != null) {
            return mAppCompatFragment.shouldShowRequestPermissionRationale(permission);
        } else if (mFragment != null) {
            if (Build.VERSION.SDK_INT >= 23) {
                return mFragment.shouldShowRequestPermissionRationale(permission);
            } else {
                if (mIsLoggingEnabled) {
                    Log.w(LOG_TAG, "Current sdk < 23 ver api and used platform's Fragment. " +
                            "Trying to get value from " +
                            "Activity.shouldShowRequestPermissionRationale()");
                }
                return ActivityCompat.shouldShowRequestPermissionRationale(mActivity, permission);
            }
        } else {
            return ActivityCompat.shouldShowRequestPermissionRationale(mActivity, permission);
        }
    }

    private void showRationaleToRequest() {
        if (!mIsSilentMode) {
            prepareDialog()
                    .setPositiveButton(R.string.perm_retriever_button_ask_again, (d, i) ->
                            request())
                    .show();
        } else {
            runUnaccepted();
        }
    }

    private void showRationaleToSettings() {
        if (!mIsSilentMode) {
            prepareDialog()
                    .setPositiveButton(R.string.perm_retriever_button_settings, (d, i) ->
                            getContext().startActivity(intentToSettings()))
                    .show();
        } else {
            runUnaccepted();
        }
    }

    private AlertDialog.Builder prepareDialog() {
        int permCount = mPermissionsRationalesMap.size();
        StringBuilder message = new StringBuilder(
                getPlurals(R.plurals.perm_retriever_message_denied, permCount));

        for (Map.Entry<String, Object> entry : mPermissionsRationalesMap.entrySet()) {
            message.append("\n").append(cutPermissionName(entry.getKey()));

            if (entry.getValue() != null) {
                if (entry.getValue() instanceof Integer) {
                    int value = (Integer) entry.getValue();
                    if (value != -1) {
                        message.append(" - ").append(getContext().getString(value));
                    }
                } else if (entry.getValue() instanceof String) {
                    String value = (String) entry.getValue();
                    if (!TextUtils.isEmpty(value)) {
                        message.append(" - ").append(value);
                    }
                }
            }
        }
        return new AlertDialog.Builder(getContext())
                .setTitle(getPlurals(R.plurals.perm_retriever_title_denied, permCount))
                .setMessage(message)
                .setOnCancelListener(d -> runUnaccepted())
                .setNegativeButton(R.string.perm_retriever_button_cancel, (d, i) ->
                        runUnaccepted());
    }

    private String cutPermissionName(String permission) {
        int lastDotIndex = permission.lastIndexOf('.') + 1;
        return permission.substring(lastDotIndex).replace("_", " ");
    }

    private Intent intentToSettings() {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", getContext().getPackageName(),
                        null));
    }

    private Context getContext() {
        if (mFragment != null) {
            return mFragment.getActivity();
        } else if (mAppCompatFragment != null) {
            return mAppCompatFragment.getActivity();
        } else {
            return mActivity;
        }
    }

    private String getPlurals(@PluralsRes int id, int quantity) {
        return getContext().getResources().getQuantityString(id, quantity);
    }

    public static class Global {
        private static Global sInstance;
        private boolean mIsLoggingEnabled = false;
        private boolean mIsSilentMode = false;

        private Global() {
        }

        /**
         * @return singleton instance of global settings
         */
        private static Global getInstance() {
            if (sInstance == null) {
                sInstance = new Global();
            }
            return sInstance;
        }

        /**
         * This method defines usage of "silent mode". Like as
         * {@link PermissionRetriever#silentMode(boolean)}, but have global effect
         *
         * @param silentMode value for turning on/off global "silent mode"
         */
        public static void setSilentMode(boolean silentMode) {
            getInstance().mIsSilentMode = silentMode;
        }

        /**
         * This method defines usage of error logging. Like as
         * {@link PermissionRetriever#logging(boolean)}, but have global effect
         *
         * @param loggingEnabled for turning on/off global logging
         */
        public static void setLoggingEnabled(boolean loggingEnabled) {
            getInstance().mIsLoggingEnabled = loggingEnabled;
        }
    }
}
