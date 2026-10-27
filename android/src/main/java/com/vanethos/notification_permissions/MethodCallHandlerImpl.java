package com.vanethos.notification_permissions;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.PluginRegistry;

public class MethodCallHandlerImpl
    implements MethodCallHandler, PluginRegistry.RequestPermissionsResultListener {
  private static final String PERMISSION_GRANTED = "granted";
  private static final String PERMISSION_DENIED = "denied";

  private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 9123;
  // Avoid referencing newer SDK constants so this plugin compiles with lower compileSdkVersion.
  private static final int ANDROID_13_API = 33;
  private static final String POST_NOTIFICATIONS_PERMISSION = "android.permission.POST_NOTIFICATIONS";

  private final Context applicationContext;
  @Nullable private Activity activity;

  @Nullable private MethodChannel.Result pendingResult;
  private boolean pendingOpenSettings = true;

  public MethodCallHandlerImpl(Context applicationContext) {
    this.applicationContext = applicationContext;
  }

  public void setActivity(@Nullable Activity activity) {
    this.activity = activity;
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {
    if ("getNotificationPermissionStatus".equalsIgnoreCase(call.method)) {
      result.success(getNotificationPermissionStatus());
    } else if ("requestNotificationPermissions".equalsIgnoreCase(call.method)) {
      boolean openSettings = true;
      final Object args = call.arguments;
      if (args instanceof java.util.Map) {
        final Object openSettingsArg = ((java.util.Map) args).get("openSettings");
        if (openSettingsArg instanceof Boolean) {
          openSettings = (Boolean) openSettingsArg;
        }
      }

      requestNotificationPermissions(openSettings, call.method, result);
    } else {
      result.notImplemented();
    }
  }

  private void requestNotificationPermissions(
      boolean openSettings, @NonNull String method, @NonNull MethodChannel.Result result) {
    if (Build.VERSION.SDK_INT >= ANDROID_13_API) {
      final Activity activity = this.activity;
      if (activity == null) {
        result.error(method, "activity is null", null);
        return;
      }

      if (ContextCompat.checkSelfPermission(activity, POST_NOTIFICATIONS_PERMISSION)
          == PackageManager.PERMISSION_GRANTED) {
        result.success(PERMISSION_GRANTED);
        return;
      }

      // Only one in-flight permission request at a time.
      if (pendingResult != null) {
        result.error(method, "permission request already in progress", null);
        return;
      }

      pendingResult = result;
      pendingOpenSettings = openSettings;
      ActivityCompat.requestPermissions(
          activity,
          new String[] {POST_NOTIFICATIONS_PERMISSION},
          NOTIFICATION_PERMISSION_REQUEST_CODE);
      return;
    }

    // Android < 13 has no runtime POST_NOTIFICATIONS permission.
    if (PERMISSION_DENIED.equalsIgnoreCase(getNotificationPermissionStatus())) {
      if (openSettings) {
        openNotificationSettings(method, result);
      } else {
        result.success(PERMISSION_DENIED);
      }
    } else {
      result.success(PERMISSION_GRANTED);
    }
  }

  private void openNotificationSettings(@NonNull String method, @NonNull MethodChannel.Result result) {
    final Activity activity = this.activity;
    if (activity == null) {
      result.error(method, "activity is null", null);
      return;
    }

    // https://stackoverflow.com/a/45192258
    final Intent intent = new Intent();

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
      intent.putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName());
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
      intent.putExtra("app_package", activity.getPackageName());
      intent.putExtra("app_uid", activity.getApplicationInfo().uid);
    } else {
      intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
      intent.addCategory(Intent.CATEGORY_DEFAULT);
      intent.setData(Uri.parse("package:" + activity.getPackageName()));
    }

    activity.startActivity(intent);
    result.success(PERMISSION_DENIED);
  }

  private String getNotificationPermissionStatus() {
    if (Build.VERSION.SDK_INT >= ANDROID_13_API) {
      final int permission =
          ContextCompat.checkSelfPermission(applicationContext, POST_NOTIFICATIONS_PERMISSION);
      return (permission == PackageManager.PERMISSION_GRANTED) ? PERMISSION_GRANTED : PERMISSION_DENIED;
    }
    return (NotificationManagerCompat.from(applicationContext).areNotificationsEnabled())
        ? PERMISSION_GRANTED
        : PERMISSION_DENIED;
  }

  @Override
  public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    if (requestCode != NOTIFICATION_PERMISSION_REQUEST_CODE) {
      return false;
    }

    final MethodChannel.Result result = pendingResult;
    pendingResult = null;

    if (result == null) {
      return true;
    }

    boolean granted = false;
    if (grantResults != null && grantResults.length > 0) {
      granted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
    }

    if (granted) {
      result.success(PERMISSION_GRANTED);
      return true;
    }

    result.success(PERMISSION_DENIED);
    return true;
  }
}
