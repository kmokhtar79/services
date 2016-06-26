package com.android.server.policy;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.ActivityManagerInternal;
import android.app.ActivityManagerInternal.SleepToken;
import android.app.ActivityManagerNative;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.AppOpsManager;
import android.app.IActivityManager;
import android.app.IUiModeManager;
import android.app.IUiModeManager.Stub;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.app.UiModeManager;
import android.app.enterprise.EnterpriseDeviceManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.database.ContentObserver;
import android.graphics.Rect;
import android.graphics.Region;
import android.hardware.display.DisplayManagerGlobal;
import android.hardware.hdmi.HdmiControlManager;
import android.hardware.hdmi.HdmiPlaybackClient;
import android.hardware.hdmi.HdmiPlaybackClient.OneTouchPlayCallback;
import android.hardware.input.InputManager;
import android.media.AudioAttributes;
import android.media.AudioAttributes.Builder;
import android.media.AudioManager;
import android.media.AudioSystem;
import android.media.IAudioService;
import android.media.IAudioService.Stub;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.session.MediaSessionLegacyHelper;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Debug;
import android.os.FactoryTest;
import android.os.Handler;
import android.os.IBinder;
import android.os.IDeviceIdleController;
import android.os.IDeviceIdleController.Stub;
import android.os.IPersonaManager.Stub;
import android.os.IVoIPInterface;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.PersonaManager;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.PowerManagerInternal;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UEventObserver;
import android.os.UEventObserver.UEvent;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.Settings.Global;
import android.provider.Settings.Secure;
import android.provider.Settings.System;
import android.sec.enterprise.auditlog.AuditLog;
import android.service.dreams.DreamManagerInternal;
import android.service.dreams.IDreamManager;
import android.service.dreams.IDreamManager.Stub;
import android.telecom.TelecomManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.EventLog;
import android.util.Log;
import android.util.Slog;
import android.util.SparseArray;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.IApplicationToken;
import android.view.IWindowManager;
import android.view.InputChannel;
import android.view.InputEvent;
import android.view.InputEventReceiver;
import android.view.InputEventReceiver.Factory;
import android.view.KeyCharacterMap;
import android.view.KeyCharacterMap.FallbackAction;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.WindowManagerInternal;
import android.view.WindowManagerPolicy;
import android.view.WindowManagerPolicy.InputConsumer;
import android.view.WindowManagerPolicy.OnKeyguardExitResult;
import android.view.WindowManagerPolicy.PointerEventListener;
import android.view.WindowManagerPolicy.ScreenOnListener;
import android.view.WindowManagerPolicy.WindowManagerFuncs;
import android.view.WindowManagerPolicy.WindowState;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.widget.RemoteViews;
import android.widget.Toast;
import com.android.internal.app.AppLockPolicy;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.policy.PhoneWindow;
import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.statusbar.IStatusBarService.Stub;
import com.android.internal.util.ScreenShapeHelper;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.PointerLocationView;
import com.android.server.GestureLauncherService;
import com.android.server.LocalServices;
import com.android.server.pm.PersonaManagerService;
import com.android.server.policy.cocktail.CocktailPhoneWindowManager;
import com.android.server.policy.keyguard.KeyguardServiceDelegate;
import com.android.server.policy.keyguard.KeyguardServiceDelegate.DrawnListener;
import com.android.server.policy.multiwindow.MultiPhoneWindowManager;
import com.android.server.policy.sec.MultitapKeyManager;
import com.android.server.policy.sec.SamsungPhoneWindowManager;
import com.android.server.policy.sec.SamsungPolicyProperties;
import com.android.server.policy.sec.SystemKeyManager;
import com.android.server.policy.sec.TspStateManager;
import com.android.server.power.ShutdownThread;
import com.samsung.android.FrontLED.IFrontLEDManager;
import com.samsung.android.app.CustomBootMsgDialog;
import com.samsung.android.cocktailbar.CocktailBarFeatures;
import com.samsung.android.cocktailbar.CocktailBarManager;
import com.samsung.android.cover.CoverState;
import com.samsung.android.cover.ICoverManager;
import com.samsung.android.cover.ICoverManager.Stub;
import com.samsung.android.dualscreen.DualScreenManager;
import com.samsung.android.multidisplay.common.FallbackArrayList;
import com.samsung.android.multiwindow.MultiWindowFacade;
import com.samsung.android.multiwindow.MultiWindowStyle;
import com.samsung.android.toolbox.TwToolBoxFloatingViewer;
import com.samsung.android.toolbox.TwToolBoxFloatingViewer.DelegateKeyguardShowing;
import com.samsung.android.toolbox.TwToolBoxService;
import com.sec.android.app.CscFeature;
import com.sec.enterprise.knox.shareddevice.EnterpriseSharedDevicePolicy;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

public class PhoneWindowManager
  implements WindowManagerPolicy
{
  private static final String ACTION_WIFI_DISPLAY_VIDEO = "org.codeaurora.intent.action.WIFI_DISPLAY_VIDEO";
  static final int APPLICATION_ABOVE_SUB_PANEL_SUBLAYER = 3;
  static final int APPLICATION_MEDIA_OVERLAY_SUBLAYER = -1;
  static final int APPLICATION_MEDIA_SUBLAYER = -2;
  static final int APPLICATION_PANEL_SUBLAYER = 1;
  static final int APPLICATION_SUB_PANEL_SUBLAYER = 2;
  private static final int BRIGHTNESS_STEPS = 10;
  static final boolean DEBUG = false;
  static final boolean DEBUG_DUAL_STATUSBAR;
  static boolean DEBUG_INPUT = false;
  static boolean DEBUG_KEYGUARD = false;
  static boolean DEBUG_LAYOUT = false;
  static final boolean DEBUG_STARTING_WINDOW = false;
  static boolean DEBUG_WAKEUP = false;
  static final int DEFAULT_LONG_PRESS_POWERON_TIME = 500;
  private static final int DISMISS_KEYGUARD_CONTINUE = 2;
  private static final int DISMISS_KEYGUARD_NONE = 0;
  private static final int DISMISS_KEYGUARD_START = 1;
  static final int DOUBLE_TAP_HOME_NOTHING = 0;
  static final int DOUBLE_TAP_HOME_RECENT_SYSTEM_UI = 1;
  static final boolean ENABLE_CAR_DOCK_HOME_CAPTURE = true;
  static final boolean ENABLE_DESK_DOCK_HOME_CAPTURE = true;
  static final boolean ENABLE_MIRRORLINK_DOCK_HOME_CAPTURE = true;
  private static final int HIDE_SVIEW_COVER_ALWAYS = 1;
  private static final int HIDE_SVIEW_COVER_NONE = 0;
  private static final int HIDE_SVIEW_COVER_ONCE = 2;
  private static final String INTENT_ACTION_SHOW_POPUP = "samsung.vzw.setupwizard.intent.action.SHOW_POPUP";
  private static final String INTENT_ACTION_START_DOCK_OR_HOME = "com.samsung.android.action.START_DOCK_OR_HOME";
  private static final String INTENT_PERMISSION_START_DOCK_OR_HOME = "com.samsung.android.permisson.START_DOCK_OR_HOME";
  private static final float KEYGUARD_SCREENSHOT_CHORD_DELAY_MULTIPLIER = 2.5F;
  static final int LONG_PRESS_HOME_ASSIST = 2;
  static final int LONG_PRESS_HOME_NOTHING = 0;
  static final int LONG_PRESS_HOME_RECENT_SYSTEM_UI = 1;
  static final int LONG_PRESS_HOME_SREMINDER = 3;
  static final int LONG_PRESS_POWER_GLOBAL_ACTIONS = 1;
  static final int LONG_PRESS_POWER_NOTHING = 0;
  static final int LONG_PRESS_POWER_SHUT_OFF = 2;
  static final int LONG_PRESS_POWER_SHUT_OFF_NO_CONFIRM = 3;
  private static final int MSG_DISABLE_EASY_ONE_HAND = 57;
  private static final int MSG_DISABLE_POINTER_LOCATION = 2;
  private static final int MSG_DISABLE_SIDE_KEY_PANEL = 51;
  private static final int MSG_DISABLE_TOOL_BOX = 19;
  private static final int MSG_DISPATCH_MEDIA_KEY_REPEAT_WITH_WAKE_LOCK = 4;
  private static final int MSG_DISPATCH_MEDIA_KEY_WITH_WAKE_LOCK = 3;
  private static final int MSG_DISPATCH_SHOW_GLOBAL_ACTIONS = 10;
  private static final int MSG_DISPATCH_SHOW_RECENTS = 9;
  private static final int MSG_ENABLE_BOTTOM_SOFTKEY = 52;
  private static final int MSG_ENABLE_EASY_ONE_HAND = 56;
  private static final int MSG_ENABLE_POINTER_LOCATION = 1;
  private static final int MSG_ENABLE_SIDE_KEY_PANEL = 50;
  private static final int MSG_ENABLE_SPEN_GESTURE = 16;
  private static final int MSG_ENABLE_TOOL_BOX = 18;
  private static final int MSG_ENDCALL_DELAYED_PRESS = 62;
  private static final int MSG_HIDE_BOOT_MESSAGE = 11;
  private static final int MSG_HIDE_BOTTOM_SOFTKEY = 55;
  private static final int MSG_KEYGUARD_DRAWN_COMPLETE = 5;
  private static final int MSG_KEYGUARD_DRAWN_TIMEOUT = 6;
  private static final int MSG_LAUNCH_VOICE_ASSIST_WITH_WAKE_LOCK = 12;
  private static final int MSG_NOTIFY_DISPLAY_ADDED = 100;
  private static final int MSG_OPEN_BOTTOM_SOFTKEY = 53;
  private static final int MSG_POWER_DELAYED_PRESS = 13;
  private static final int MSG_POWER_LONG_PRESS = 14;
  private static final int MSG_REQUEST_CONFIGURATION_BY_MOBILEKEYBOARD = 61;
  private static final int MSG_REQUEST_TRAVERSAL_BY_PWM = 60;
  private static final int MSG_SD_KEYGUARD_DRAWN_COMPLETE = 105;
  private static final int MSG_SHOW_BOTTOM_SOFTKEY = 54;
  private static final int MSG_SSRM_NOTIFICATION = 17;
  private static final int MSG_UPDATE_DREAMING_SLEEP_TOKEN = 15;
  private static final int MSG_WINDOW_MANAGER_DRAWN_COMPLETE = 7;
  static final int MULTI_PRESS_POWER_BRIGHTNESS_BOOST = 2;
  static final int MULTI_PRESS_POWER_CURTAIN_MODE = 3;
  static final int MULTI_PRESS_POWER_NOTHING = 0;
  static final int MULTI_PRESS_POWER_SOS_MESSAGE_MODE = 4;
  static final int MULTI_PRESS_POWER_THEATER_MODE = 1;
  private static final long PANIC_GESTURE_EXPIRATION = 30000L;
  static final boolean PRINT_ANIM = false;
  static final int QUICKBOOT_LAUNCH_TIMEOUT = 2000;
  static final boolean SAFE_DEBUG;
  private static final long SCREENSHOT_CHORD_DEBOUNCE_DELAY_MILLIS = 150L;
  static final int SHORT_PRESS_POWER_GO_HOME = 4;
  static final int SHORT_PRESS_POWER_GO_TO_SLEEP = 1;
  static final int SHORT_PRESS_POWER_NOTHING = 0;
  static final int SHORT_PRESS_POWER_REALLY_GO_TO_SLEEP = 2;
  static final int SHORT_PRESS_POWER_REALLY_GO_TO_SLEEP_AND_GO_HOME = 3;
  static final int SHORT_PRESS_SLEEP_GO_TO_SLEEP = 0;
  static final int SHORT_PRESS_SLEEP_GO_TO_SLEEP_AND_GO_HOME = 1;
  static final boolean SHOW_PROCESSES_ON_ALT_MENU = false;
  static final boolean SHOW_STARTING_ANIMATIONS = true;
  public static final String SYSTEM_DIALOG_REASON_ASSIST = "assist";
  public static final String SYSTEM_DIALOG_REASON_COVER = "cover";
  public static final String SYSTEM_DIALOG_REASON_GLOBAL_ACTIONS = "globalactions";
  public static final String SYSTEM_DIALOG_REASON_HOME_KEY = "homekey";
  public static final String SYSTEM_DIALOG_REASON_KEY = "reason";
  public static final String SYSTEM_DIALOG_REASON_RECENT_APPS = "recentapps";
  static final int SYSTEM_UI_CHANGING_LAYOUT = -1073709050;
  static final String TAG = "WindowManager";
  private static final AudioAttributes VIBRATION_ATTRIBUTES;
  static final int WAITING_FOR_DRAWN_TIMEOUT = 1000;
  private static final int[] WINDOW_TYPES_WHERE_HOME_DOESNT_WORK;
  static final boolean localLOGV = false;
  private static int mScreenTurnDisplayId;
  static final Rect mTmpCarModeFrame;
  static final Rect mTmpContentFrame;
  static final Rect mTmpDecorFrame;
  static final Rect mTmpDisplayFrame;
  static final Rect mTmpNavigationFrame;
  static final Rect mTmpOutsetFrame;
  static final Rect mTmpOverscanFrame;
  static final Rect mTmpParentFrame;
  static final Rect mTmpStableFrame;
  static final Rect mTmpVisibleFrame;
  static SparseArray<String> sApplicationLaunchKeyCategories;
  private static boolean wasTopFullscreen;
  public final String[] ALARM_STARTED = { "com.samsung.sec.android.clockpackage.alarm.ALARM_STARTED_IN_ALERT", "com.android.deskclock.ALARM_ALERT", "com.samsung.sec.android.clockpackage.alarm.ALARM_ALERT" };
  public final String[] ALARM_STOPPED = { "com.samsung.sec.android.clockpackage.alarm.ALARM_STOPPED_IN_ALERT", "com.android.deskclock.ALARM_DONE", "com.samsung.sec.android.clockpackage.alarm.ALARM_STOP" };
  private Toast alertToast;
  private boolean bIsCharging = false;
  private Region cursorWindowTouchableRegion = Region.obtain();
  private final boolean isElasticEnabled = true;
  private final boolean isFrontLEDMgrEnabled = false;
  PhoneWindowManagers.AryaModKeyPolicy mAbsPhoneWindownManager;
  boolean mAccelerometerDefault;
  AccessibilityManager mAccessibilityManager;
  ActivityManagerInternal mActivityManagerInternal;
  boolean mAlarmReceivedFlag = false;
  BroadcastReceiver mAlarmReceiver = new BroadcastReceiver()
  {
    public void onReceive(Context paramContext, Intent paramIntent)
    {
      paramContext = paramIntent.getAction();
      int i = 0;
      while (i < PhoneWindowManager.this.ALARM_STARTED.length)
      {
        if ((PhoneWindowManager.this.ALARM_STARTED[i].equals(paramContext)) && (PhoneWindowManager.this.mAlarmReceivedFlag != true))
          PhoneWindowManager.this.mAlarmReceivedFlag = true;
        i += 1;
      }
      i = 0;
      while (i < PhoneWindowManager.this.ALARM_STOPPED.length)
      {
        if ((PhoneWindowManager.this.ALARM_STOPPED[i].equals(paramContext)) && (PhoneWindowManager.this.mAlarmReceivedFlag))
          PhoneWindowManager.this.mAlarmReceivedFlag = false;
        i += 1;
      }
      Slog.i("WindowManager", "ALARM Received.status  :  " + PhoneWindowManager.this.mAlarmReceivedFlag);
      if (PhoneWindowManager.this.mAlarmReceivedFlag == true)
        if (PhoneWindowManager.this.mMouseDockedFlag == true)
        {
          Slog.i("WindowManager", "SmartDock Alarm Started");
          PhoneWindowManager.this.mOldMouseDockedValue = PhoneWindowManager.this.mMouseDockedFlag;
          PhoneWindowManager.this.mMouseDockedFlag = false;
        }
      while (true)
      {
        PhoneWindowManager.this.updateRotation(true);
        PhoneWindowManager.this.updateOrientationListenerLp();
        return;
        if ((PhoneWindowManager.this.mMouseDockedFlag) || (PhoneWindowManager.this.mAlarmReceivedFlag) || (!PhoneWindowManager.this.mOldMouseDockedValue))
          continue;
        PhoneWindowManager.this.mMouseDockedFlag = PhoneWindowManager.this.mOldMouseDockedValue;
        PhoneWindowManager.this.mOldMouseDockedValue = false;
        Slog.i("WindowManager", "SmartDock ALARM Stopped");
      }
    }
  };
  int mAllowAllRotations = -1;
  boolean mAllowLockscreenWhenOn;
  private boolean mAllowTheaterModeWakeFromCameraLens;
  private boolean mAllowTheaterModeWakeFromKey;
  private boolean mAllowTheaterModeWakeFromLidSwitch;
  private boolean mAllowTheaterModeWakeFromMotion;
  private boolean mAllowTheaterModeWakeFromMotionWhenNotDreaming;
  private boolean mAllowTheaterModeWakeFromPowerKey;
  private boolean mAllowTheaterModeWakeFromWakeGesture;
  AppOpsManager mAppOpsManager;
  ArrayList<IApplicationToken> mAppsShowWhenLocked = new ArrayList();
  HashSet<IApplicationToken> mAppsThatDismissKeyguard = new HashSet();
  HashSet<IApplicationToken> mAppsToBeHidden = new HashSet();
  HashSet<IApplicationToken> mAppsToBeHiddenBySViewCover = new HashSet();
  boolean mAssistKeyLongPressed;
  BroadcastReceiver mAutoRotation = new BroadcastReceiver()
  {
    public void onReceive(Context paramContext, Intent paramIntent)
    {
      if ("com.samsung.intent.action.AUTOROTATION".equals(paramIntent.getAction()));
      try
      {
        PhoneWindowManager.this.mOrientationListener.setSensorDelay(Integer.parseInt(paramIntent.getStringExtra("delay")));
        return;
      }
      catch (java.lang.NumberFormatException paramContext)
      {
      }
    }
  };
  boolean mAwake;
  private int mBatteryLevel = 100;
  BroadcastReceiver mBatteryReceiver = new BroadcastReceiver()
  {
    public void onReceive(Context paramContext, Intent paramIntent)
    {
      if ("android.intent.action.BATTERY_CHANGED".equals(paramIntent.getAction()))
        PhoneWindowManager.access$4002(PhoneWindowManager.this, paramIntent.getIntExtra("level", 100));
    }
  };
  int mBeforeKeyDown = 0;
  volatile boolean mBeganFromNonInteractive;
  volatile boolean mBeganFromNonInteractiveEndCall;
  boolean mBootMessageNeedsHiding;
  ProgressDialog mBootMsgDialog = null;
  private WindowManagerPolicy.WindowState mBottomKeyPanelWindow;
  PowerManager.WakeLock mBroadcastWakeLock;
  BurnInProtectionHelper mBurnInProtectionHelper;
  long[] mCalendarDateVibePattern;
  int mCameraLensCoverState = -1;
  boolean mCanHideNavigationBar = false;
  boolean mCarDockEnablesAccelerometer;
  Intent mCarDockIntent;
  int mCarDockRotation;
  private WindowManagerPolicy.WindowState mCarModeBar;
  private boolean mCarModeBarOnBottom = false;
  private int mCarModeSize;
  BroadcastReceiver mChargingReceiver = new BroadcastReceiver()
  {
    public void onReceive(Context paramContext, Intent paramIntent)
    {
      if ("android.intent.action.ACTION_POWER_CONNECTED".equals(paramIntent.getAction()))
        PhoneWindowManager.access$4102(PhoneWindowManager.this, true);
      do
        return;
      while (!"android.intent.action.ACTION_POWER_DISCONNECTED".equals(paramIntent.getAction()));
      PhoneWindowManager.access$4102(PhoneWindowManager.this, false);
    }
  };
  private final Runnable mClearHideNavigationFlag = new Runnable()
  {
    public void run()
    {
      synchronized (PhoneWindowManager.this.mWindowManagerFuncs.getWindowManagerLock())
      {
        PhoneWindowManager localPhoneWindowManager = PhoneWindowManager.this;
        localPhoneWindowManager.mForceClearedSystemUiFlags &= -3;
        PhoneWindowManager.this.mWindowManagerFuncs.reevaluateStatusBarVisibility();
        return;
      }
    }
  };
  long[] mClockTickVibePattern;
  private int mCocktail180RotationEnabled = 0;
  private CocktailPhoneWindowManager mCocktailPhoneWindowManager;
  boolean mConsumeSearchKeyUp;
  int mContentBottom;
  int mContentLeft;
  int mContentRight;
  int mContentTop;
  Context mContext;
  long[] mContextClickVibePattern;
  int mCoverCloseRotation;
  ICoverManager mCoverManager;
  CoverState mCoverState;
  int mCurBottom;
  int mCurLeft;
  int mCurRight;
  int mCurTop;
  int mCurrentAppOrientation = -1;
  private int mCurrentDisplayRotation = -1;
  private int mCurrentUserId;
  CustomBootMsgDialog mCustomBootMsgDialog = null;
  boolean mCustomDialog = true;
  private boolean mDeferBindKeyguard;
  int mDemoHdmiRotation;
  boolean mDemoHdmiRotationLock;
  int mDemoRotation;
  boolean mDemoRotationLock;
  boolean mDeskDockEnablesAccelerometer;
  Intent mDeskDockIntent;
  int mDeskDockRotation;
  int mDismissKeyguard = 0;
  Display mDisplay;
  DisplayManagerGlobal mDisplayManager;
  private int mDisplayRotation;
  private SystemGesturesPointerEventListener[] mDisplaySystemGestures = new SystemGesturesPointerEventListener[4];
  FallbackArrayList<DisplayWindowPolicy> mDisplayWindowPolicy = new FallbackArrayList();
  int mDockBottom;
  int mDockLayer;
  int mDockLeft;
  int mDockMode = 0;
  BroadcastReceiver mDockReceiver = new BroadcastReceiver()
  {
    public void onReceive(Context arg1, Intent paramIntent)
    {
      if ("android.intent.action.DOCK_EVENT".equals(paramIntent.getAction()))
        PhoneWindowManager.this.mDockMode = paramIntent.getIntExtra("android.intent.extra.DOCK_STATE", 0);
      while (true)
      {
        PhoneWindowManager.this.updateRotation(true);
        synchronized (PhoneWindowManager.this.mLock)
        {
          PhoneWindowManager.this.updateOrientationListenerLp();
          return;
          try
          {
            ??? = IUiModeManager.Stub.asInterface(ServiceManager.getService("uimode"));
            PhoneWindowManager.this.mUiMode = ???.getCurrentModeType();
          }
          catch (RemoteException )
          {
          }
        }
      }
    }
  };
  int mDockRight;
  int mDockTop;
  int mDoublePressOnPowerBehavior;
  private int mDoubleTapOnHomeBehavior;
  DreamManagerInternal mDreamManagerInternal;
  BroadcastReceiver mDreamReceiver = new BroadcastReceiver()
  {
    public void onReceive(Context paramContext, Intent paramIntent)
    {
      if ("android.intent.action.DREAMING_STARTED".equals(paramIntent.getAction()))
        if (PhoneWindowManager.this.mKeyguardDelegate != null)
          PhoneWindowManager.this.mKeyguardDelegate.onDreamingStarted();
      do
        return;
      while ((!"android.intent.action.DREAMING_STOPPED".equals(paramIntent.getAction())) || (PhoneWindowManager.this.mKeyguardDelegate == null));
      PhoneWindowManager.this.mKeyguardDelegate.onDreamingStopped();
    }
  };
  boolean mDreamingLockscreen;
  ActivityManagerInternal.SleepToken mDreamingSleepToken;
  boolean mDreamingSleepTokenNeeded;
  DualScreenManager mDualScreenManager;
  private EnterpriseDeviceManager mEDM;
  private int mEasyOneHandEnabled = -1;
  boolean mEnableShiftMenuBugReports = false;
  volatile boolean mEndCallKeyHandled;
  volatile int mEndCallKeyPressCounter;
  private final Runnable mEndCallLongPress = new Runnable()
  {
    public void run()
    {
      PhoneWindowManager.this.mEndCallKeyHandled = true;
      if ((FactoryTest.isLongPressOnPowerOffEnabled()) || (FactoryTest.isFactoryMode()) || (FactoryTest.isAutomaticTestMode(PhoneWindowManager.this.mContext)))
      {
        Slog.d("WindowManager", "mEndCallLongPress on FactoryTest conditions");
        PhoneWindowManager.this.performHapticFeedbackLw(null, 0, false);
        PhoneWindowManager.this.sendCloseSystemWindows("globalactions");
        PhoneWindowManager.this.mWindowManagerFuncs.shutdown(false);
      }
      do
        return;
      while ((PhoneWindowManager.this.mSPWM.isCombinationKeyTriggered()) || (PhoneWindowManager.this.mSPWM.ignorePowerKeyInEncrypting()));
      if (!PhoneWindowManager.this.performHapticFeedbackLw(null, 0, false))
        PhoneWindowManager.this.performAuditoryFeedbackForAccessibilityIfNeed();
      PhoneWindowManager.this.showGlobalActionsInternal();
    }
  };
  int mEndcallBehavior;
  WindowManagerPolicy.WindowState mFakeFocusedWindow;
  private final SparseArray<KeyCharacterMap.FallbackAction> mFallbackActions = new SparseArray();
  int mFixedTaskId = -1;
  IApplicationToken mFocusedApp;
  WindowManagerPolicy.WindowState mFocusedWindow;
  private Object mFoldingAndWrapAroundLock = new Object();
  public int mFoldingState = -1;
  int mForceClearedSystemUiFlags = 0;
  private boolean mForceDefaultOrientation = false;
  private boolean mForceHideStatusBarForCocktail;
  boolean mForceStatusBar;
  boolean mForceStatusBarFromKeyguard;
  boolean mForceStatusBarFromSViewCover;
  private boolean mForceStatusBarTransparent;
  ArrayList<WindowManagerPolicy.WindowState> mForceUserActivityTimeoutWin = new ArrayList();
  boolean mForcingShowNavBar;
  int mForcingShowNavBarLayer;
  IFrontLEDManager mFrontLEDManager;
  GlobalActions mGlobalActions;
  private GlobalKeyManager mGlobalKeyManager;
  private boolean mGoToSleepOnButtonPressTheaterMode;
  private UEventObserver mHDMIObserver = new UEventObserver()
  {
    public void onUEvent(UEventObserver.UEvent paramUEvent)
    {
      PhoneWindowManager.this.setHdmiPlugged("1".equals(paramUEvent.get("SWITCH_STATE")));
    }
  };
  Handler mHandler;
  boolean mHasNavigationBar = false;
  boolean mHasSoftInput = false;
  boolean mHaveBuiltInKeyboard;
  boolean mHavePendingMediaKeyRepeatWithWakeLock;
  HdmiControl mHdmiControl;
  boolean mHdmiPlugged;
  private final Runnable mHiddenNavPanic = new Runnable()
  {
    public void run()
    {
      synchronized (PhoneWindowManager.this.mWindowManagerFuncs.getWindowManagerLock())
      {
        if (!PhoneWindowManager.this.isUserSetupComplete())
          return;
        PhoneWindowManager.access$4202(PhoneWindowManager.this, SystemClock.uptimeMillis());
        PhoneWindowManager.this.mNavigationBarController.showTransient();
        return;
      }
    }
  };
  boolean mHideLockScreen;
  boolean mHideLockScreenByCover = false;
  final InputEventReceiver.Factory mHideNavInputEventReceiverFactory = new InputEventReceiver.Factory()
  {
    public InputEventReceiver createInputEventReceiver(InputChannel paramInputChannel, Looper paramLooper)
    {
      return new PhoneWindowManager.HideNavInputEventReceiver(PhoneWindowManager.this, paramInputChannel, paramLooper);
    }
  };
  private boolean mHideSDKeyguard = false;
  int mHideSViewCover = 0;
  WindowManagerPolicy.WindowState mHideSViewCoverWindowState = null;
  boolean mHomeConsumed;
  boolean mHomeDoubleTapPending;
  private final Runnable mHomeDoubleTapTimeoutRunnable = new Runnable()
  {
    public void run()
    {
      if (PhoneWindowManager.this.mHomeDoubleTapPending)
      {
        PhoneWindowManager.this.mHomeDoubleTapPending = false;
        PhoneWindowManager.this.handleShortPressOnHome();
      }
    }
  };
  Intent mHomeIntent;
  boolean mHomeLongPressCanceled;
  boolean mHomePressed;
  private ImmersiveModeConfirmation mImmersiveModeConfirmation;
  int mIncallPowerBehavior;
  WindowManagerPolicy.InputConsumer mInputConsumer = null;
  WindowManagerPolicy.WindowState mInputMethod = null;
  private boolean mIsDefaultKeyguardRotationAnmationAlwaysUsed = false;
  private boolean mIsKNOX2Supported = false;
  private boolean mIsNightClockShow = false;
  private boolean mIsOWCSetting = false;
  boolean mIsSupportFlipCover = false;
  boolean mIsSupportSViewCover = false;
  boolean mIsVolLongPressed;
  long[] mKeyboardTapVibePattern;
  KeyguardServiceDelegate mKeyguardDelegate;
  boolean mKeyguardDrawComplete;
  final KeyguardServiceDelegate.DrawnListener mKeyguardDrawnCallback = new KeyguardServiceDelegate.DrawnListener()
  {
    public void onDrawn()
    {
      if (PhoneWindowManager.DEBUG_WAKEUP)
        Log.d("WindowManager", "mKeyguardDelegate.ShowListener.onDrawn.");
      PhoneWindowManager.this.mHandler.sendEmptyMessage(5);
    }
  };
  private boolean mKeyguardDrawnOnce;
  private boolean mKeyguardHidden;
  volatile boolean mKeyguardOccluded;
  private WindowManagerPolicy.WindowState mKeyguardScrim;
  boolean mKeyguardSecure;
  boolean mKeyguardSecureIncludingHidden;
  private boolean mKeyguardShowingState;
  int mLandscapeRotation = 0;
  boolean mLanguageSwitchKeyPressed;
  boolean mLastCoverAppCovered = false;
  boolean mLastFocusNeedsMenu = false;
  WindowManagerPolicy.WindowState mLastInputMethodTargetWindow = null;
  WindowManagerPolicy.WindowState mLastInputMethodWindow = null;
  int mLastSystemUiFlags;
  private WindowManagerPolicy.WindowState mLastWinShowWhenLocked;
  private int mLastWrapAroundMode = 0;
  boolean mLaunchHomeFromSubHotKey = false;
  boolean mLidControlsSleep;
  int mLidKeyboardAccessibility;
  int mLidNavigationAccessibility;
  int mLidOpenRotation;
  public int mLidState = -1;
  private final Object mLock = new Object();
  private LockPatternUtils mLockPatternUtils;
  int mLockScreenTimeout;
  boolean mLockScreenTimerActive;
  private int mLockTaskModeState = 0;
  private final LogDecelerateInterpolator mLogDecelerateInterpolator = new LogDecelerateInterpolator(100, 0);
  Runnable mLongPressKill;
  private int mLongPressOnHomeBehavior;
  int mLongPressOnPowerBehavior;
  int mLongPressPoweronTime = 500;
  long[] mLongPressVibePattern;
  boolean mMirrorLinkDockEnablesAccelerometer;
  Intent mMirrorLinkDockIntent;
  int mMirrorlinkDockRotation;
  public boolean mMobileKeyboardEnabled = false;
  private int mMobileKeyboardHeight;
  BroadcastReceiver mMouseConnectReceiver = new BroadcastReceiver()
  {
    public void onReceive(Context paramContext, Intent paramIntent)
    {
      Slog.i("WindowManager", "ACTION_USBHID_MOUSE_EVENT Received...");
      if ("android.intent.action.USBHID_MOUSE_EVENT".equals(paramIntent.getAction()))
      {
        PhoneWindowManager.this.mMouseConnectedDock = paramIntent.getIntExtra("android.intent.extra.device_state", 0);
        Slog.i("WindowManager", "ACTION_USBHID_MOUSE_EVENT Received.status  :  " + PhoneWindowManager.this.mMouseConnectedDock);
        if (1 == PhoneWindowManager.this.mMouseConnectedDock)
        {
          PhoneWindowManager.this.mMouseDockedFlag = true;
          PhoneWindowManager.this.mOldMouseDockedValue = PhoneWindowManager.this.mMouseDockedFlag;
          if (PhoneWindowManager.this.mTelephonyManager == null)
            PhoneWindowManager.this.mTelephonyManager = ((TelephonyManager)paramContext.getSystemService("phone"));
          if (PhoneWindowManager.this.mTelephonyManager == null);
        }
      }
      while (true)
      {
        try
        {
          if (PhoneWindowManager.this.mPhoneStateListener != null)
            continue;
          PhoneWindowManager.this.mPhoneStateListener = new PhoneStateListener()
          {
            public void onCallStateChanged(int paramInt, String paramString)
            {
              if ((paramInt == 1) || (paramInt == 2))
                if (PhoneWindowManager.this.mMouseDockedFlag == true)
                  PhoneWindowManager.this.mOldMouseDockedValue = PhoneWindowManager.this.mMouseDockedFlag;
              for (PhoneWindowManager.this.mMouseDockedFlag = false; ; PhoneWindowManager.this.mMouseDockedFlag = PhoneWindowManager.this.mOldMouseDockedValue)
              {
                PhoneWindowManager.this.updateRotation(true);
                PhoneWindowManager.this.updateOrientationListenerLp();
                return;
              }
            }
          };
          PhoneWindowManager.this.mTelephonyManager.listen(PhoneWindowManager.this.mPhoneStateListener, 32);
          PhoneWindowManager.this.updateRotation(true);
          PhoneWindowManager.this.updateOrientationListenerLp();
          return;
        }
        catch (java.lang.SecurityException paramContext)
        {
          Log.w("WindowManager", "Phone window manager doesn't have the permssion READ_PHONE_STATE. please defines it via <uses-permssion> in AndroidManifest.xml.");
          continue;
        }
        PhoneWindowManager.this.mMouseDockedFlag = false;
        PhoneWindowManager.this.mOldMouseDockedValue = PhoneWindowManager.this.mMouseDockedFlag;
        if (PhoneWindowManager.this.mTelephonyManager == null)
          continue;
        PhoneWindowManager.this.mTelephonyManager.listen(PhoneWindowManager.this.mPhoneStateListener, 0);
        continue;
        try
        {
          paramContext = IUiModeManager.Stub.asInterface(ServiceManager.getService("uimode"));
          if (paramContext == null)
            continue;
          PhoneWindowManager.this.mUiMode = paramContext.getCurrentModeType();
        }
        catch (RemoteException paramContext)
        {
        }
      }
    }
  };
  int mMouseConnectedDock = 0;
  boolean mMouseDockedFlag = false;
  private MultiPhoneWindowManager mMultiPhoneWindowManager;
  private MultitapKeyManager mMultitapKeyManager;
  BroadcastReceiver mMultiuserReceiver = new BroadcastReceiver()
  {
    public void onReceive(Context arg1, Intent paramIntent)
    {
      if ("android.intent.action.USER_SWITCHED".equals(paramIntent.getAction()))
      {
        PhoneWindowManager.this.updateSettings();
        PhoneWindowManager.this.mSPWM.updateSettings();
        PhoneWindowManager.this.mMultiPhoneWindowManager.updateSettings();
      }
      synchronized (PhoneWindowManager.this.mWindowManagerFuncs.getWindowManagerLock())
      {
        PhoneWindowManager.this.mLastSystemUiFlags = 0;
        PhoneWindowManager.this.updateSystemUiVisibilityLw();
        PhoneWindowManager.this.mSPWM.setCurrentUser(paramIntent.getIntExtra("android.intent.extra.user_handle", 0));
        return;
      }
    }
  };
  WindowManagerPolicy.WindowState mNavigationBar = null;
  boolean mNavigationBarCanMove = false;
  private final BarController mNavigationBarController = new BarController("NavigationBar", 134217728, 536870912, -2147483648, 2, 134217728);
  int[] mNavigationBarHeightForRotation = new int[4];
  boolean mNavigationBarOnBottom = true;
  int[] mNavigationBarWidthForRotation = new int[4];
  private boolean mNeedTriggerOWC = false;
  private WindowManagerPolicy.WindowState mNightClock;
  boolean mOldMouseDockedValue = false;
  boolean mOpenByNotification = false;
  MyOrientationListener mOrientationListener;
  boolean mOrientationSensorEnabled = false;
  int mOriginalDockBottom;
  int mOriginalDockLeft;
  int mOriginalDockRight;
  int mOriginalDockTop;
  int mOriginalStableBottom;
  int mOriginalStableFullscreenBottom;
  int mOriginalStableFullscreenLeft;
  int mOriginalStableFullscreenRight;
  int mOriginalStableFullscreenTop;
  int mOriginalStableLeft;
  int mOriginalStableRight;
  int mOriginalStableTop;
  int mOriginalSystemBottom;
  int mOriginalSystemLeft;
  int mOriginalSystemRight;
  int mOriginalSystemTop;
  int mOriginalUnrestrictedScreenHeight;
  int mOriginalUnrestrictedScreenLeft;
  int mOriginalUnrestrictedScreenTop;
  int mOriginalUnrestrictedScreenWidth;
  int mOverscanBottom = 0;
  int mOverscanLeft = 0;
  int mOverscanRight = 0;
  int mOverscanScreenHeight;
  int mOverscanScreenLeft;
  int mOverscanScreenTop;
  int mOverscanScreenWidth;
  int mOverscanTop = 0;
  int mPanelOrientation = 0;
  boolean mPendingMetaAction;
  private long mPendingPanicGestureUptime;
  boolean mPendingPowerKeyUpCanceled;
  private PersonaManager mPersonaManager = null;
  private PersonaManagerService mPersonaManagerService;
  public PhoneStateListener mPhoneStateListener;
  int mPointerLocationMode = 0;
  PointerLocationView mPointerLocationView;
  PointerLocationView mPointerLocationViewOnSubscreen;
  int mPortraitRotation = 0;
  volatile boolean mPowerKeyHandled;
  volatile int mPowerKeyPressCounter;
  private long mPowerKeyTime;
  PowerManager.WakeLock mPowerKeyWakeLock;
  PowerManager mPowerManager;
  PowerManagerInternal mPowerManagerInternal;
  boolean mPreloadedRecentApps;
  boolean mPresentationFlag = false;
  BroadcastReceiver mPresentationStartReceiver = new BroadcastReceiver()
  {
    public void onReceive(Context paramContext, Intent paramIntent)
    {
      if ("com.samsung.intent.action.SEC_PRESENTATION_START".equals(paramIntent.getAction()))
      {
        PhoneWindowManager.this.mPresentationFlag = true;
        if (PhoneWindowManager.this.mMouseDockedFlag == true)
        {
          Slog.i("WindowManager", "SmartDock Presentation Started");
          PhoneWindowManager.this.mOldMouseDockedValue = PhoneWindowManager.this.mMouseDockedFlag;
          PhoneWindowManager.this.mMouseDockedFlag = false;
        }
      }
      PhoneWindowManager.this.updateRotation(true);
      PhoneWindowManager.this.updateOrientationListenerLp();
    }
  };
  BroadcastReceiver mPresentationStopReceiver = new BroadcastReceiver()
  {
    public void onReceive(Context paramContext, Intent paramIntent)
    {
      if ("com.samsung.intent.action.SEC_PRESENTATION_STOP".equals(paramIntent.getAction()))
      {
        PhoneWindowManager.this.mPresentationFlag = false;
        if ((!PhoneWindowManager.this.mMouseDockedFlag) && (PhoneWindowManager.this.mOldMouseDockedValue))
        {
          PhoneWindowManager.this.mMouseDockedFlag = PhoneWindowManager.this.mOldMouseDockedValue;
          PhoneWindowManager.this.mOldMouseDockedValue = false;
          Slog.i("WindowManager", "SmartDock Presentation Stopped");
        }
      }
      PhoneWindowManager.this.updateRotation(true);
      PhoneWindowManager.this.updateOrientationListenerLp();
    }
  };
  PowerManager.WakeLock mQuickBootWakeLock;
  int mRecentAppsHeldModifiers;
  private boolean mRecentConsumed;
  boolean mRecentsVisible;
  BroadcastReceiver mReconfigureDebugReceiver = new BroadcastReceiver()
  {
    public void onReceive(Context paramContext, Intent paramIntent)
    {
      if ("android.app.action.DEBUG_RECONFIGURE".equals(paramIntent.getAction()))
      {
        paramContext = (boolean[])(boolean[])paramIntent.getExtra("PWM_DEBUG");
        PhoneWindowManager.this.reconfigureDebug(paramContext);
      }
    }
  };
  int mResettingSystemUiFlags = 0;
  int mRestrictedOverscanScreenHeight;
  int mRestrictedOverscanScreenLeft;
  int mRestrictedOverscanScreenTop;
  int mRestrictedOverscanScreenWidth;
  int mRestrictedScreenHeight;
  int mRestrictedScreenLeft;
  int mRestrictedScreenTop;
  int mRestrictedScreenWidth;
  private SamsungPhoneWindowManager mSPWM;
  int mSViewCoverDockBottom;
  int mSViewCoverDockLeft;
  int mSViewCoverDockRight;
  int mSViewCoverDockTop;
  int mSViewCoverStableBottom;
  int mSViewCoverStableFullscreenBottom;
  int mSViewCoverStableFullscreenLeft;
  int mSViewCoverStableFullscreenRight;
  int mSViewCoverStableFullscreenTop;
  int mSViewCoverStableLeft;
  int mSViewCoverStableRight;
  int mSViewCoverStableTop;
  int mSViewCoverSystemBottom;
  int mSViewCoverSystemLeft;
  int mSViewCoverSystemRight;
  int mSViewCoverSystemTop;
  int mSViewCoverUnrestrictedScreenHeight;
  int mSViewCoverUnrestrictedScreenLeft;
  int mSViewCoverUnrestrictedScreenTop;
  int mSViewCoverUnrestrictedScreenWidth;
  boolean mSafeMode;
  long[] mSafeModeDisabledVibePattern;
  long[] mSafeModeEnabledVibePattern;
  ScreenLockTimeout mScreenLockTimeout = new ScreenLockTimeout();
  ActivityManagerInternal.SleepToken mScreenOffSleepToken;
  boolean mScreenOnEarly;
  boolean mScreenOnFully;
  WindowManagerPolicy.ScreenOnListener mScreenOnListener;
  private boolean mScreenshotChordEnabled;
  private long mScreenshotChordPowerKeyTime;
  private boolean mScreenshotChordPowerKeyTriggered;
  private boolean mScreenshotChordVolumeDownKeyConsumed;
  private long mScreenshotChordVolumeDownKeyTime;
  private boolean mScreenshotChordVolumeDownKeyTriggered;
  private boolean mScreenshotChordVolumeUpKeyTriggered;
  ServiceConnection mScreenshotConnection = null;
  private final Runnable mScreenshotForLog = new Runnable()
  {
    public void run()
    {
      Intent localIntent = new Intent("android.system.agent");
      localIntent.setComponent(new ComponentName("com.qualcomm.agent", "com.qualcomm.agent.SystemAgent"));
      localIntent.putExtra("para", "takeLogs");
      try
      {
        PhoneWindowManager.this.mContext.startService(localIntent);
        return;
      }
      catch (Exception localException)
      {
        Slog.e("WindowManager", "Exception when start SystemAgent service", localException);
      }
    }
  };
  final Object mScreenshotLock = new Object();
  BroadcastReceiver mScreenshotReceiver = new Torch();
  private final Runnable mScreenshotRunnable = new Runnable()
  {
    public void run()
    {
      PhoneWindowManager.this.takeScreenshot();
    }
  };
  final Runnable mScreenshotTimeout = new Runnable()
  {
    public void run()
    {
      synchronized (PhoneWindowManager.this.mScreenshotLock)
      {
        if (PhoneWindowManager.this.mScreenshotConnection != null)
        {
          PhoneWindowManager.this.mContext.unbindService(PhoneWindowManager.this.mScreenshotConnection);
          PhoneWindowManager.this.mScreenshotConnection = null;
        }
        return;
      }
    }
  };
  boolean mSearchKeyShortcutPending;
  SearchManager mSearchManager;
  int mSeascapeRotation = 0;
  int mSecondLcdLastRotation = -1;
  int mSecondLcdUserRotationMode = 0;
  private boolean mSecureDismissingKeyguard;
  final Object mServiceAquireLock = new Object();
  SettingsObserver mSettingsObserver;
  int mShortPressOnPowerBehavior;
  int mShortPressOnSleepBehavior;
  ShortcutManager mShortcutManager;
  boolean mShowFullStatusBar;
  Runnable mShowStatusBarByNotification = new Runnable()
  {
    public void run()
    {
      PhoneWindowManager.this.requestTransientBars();
    }
  };
  boolean mShowingDream;
  boolean mShowingLockscreen;
  boolean mShowingSViewCover;
  private int mSideKeyPanelEnabled = 0;
  int mStableBottom;
  int mStableFullscreenBottom;
  int mStableFullscreenLeft;
  int mStableFullscreenRight;
  int mStableFullscreenTop;
  int mStableLeft;
  int mStableRight;
  int mStableTop;
  boolean mStarKeyLongPressConsumed = false;
  WindowManagerPolicy.WindowState mStatusBar = null;
  private final StatusBarController mStatusBarController = new StatusBarController();
  int mStatusBarHeight;
  int mStatusBarLayer;
  IStatusBarService mStatusBarService;
  ActivityManagerInternal.SleepToken mSubScreenOffSleepToken;
  boolean mSubScreenOnEarly = false;
  boolean mSubScreenOnFully = false;
  boolean mSupportAutoRotation;
  private boolean mSupportLongPressPowerWhenNonInteractive;
  boolean mSystemBooted;
  int mSystemBottom;
  private SystemGesturesPointerEventListener mSystemGestures;
  private SystemKeyManager mSystemKeyManager;
  int mSystemLeft;
  boolean mSystemReady;
  int mSystemRight;
  int mSystemTop;
  BroadcastReceiver mSystemUIReplacedReceiver = new BroadcastReceiver()
  {
    public void onReceive(Context paramContext, Intent paramIntent)
    {
      if ("android.intent.action.PACKAGE_REPLACED".equals(paramIntent.getAction()))
      {
        paramContext = paramIntent.getData();
        if (paramContext == null)
          break label94;
      }
      label94: for (paramContext = paramContext.getSchemeSpecificPart(); ; paramContext = null)
      {
        if ((paramContext != null) && (paramContext.equals("com.android.systemui")))
        {
          paramContext = new AlertDialog.Builder(PhoneWindowManager.this.mContext).setMessage(17041601).setCancelable(false).setPositiveButton(17039370, new DialogInterface.OnClickListener()
          {
            public void onClick(DialogInterface paramDialogInterface, int paramInt)
            {
              ShutdownThread.reboot(PhoneWindowManager.this.mContext, "SystemUI is replaced", false);
            }
          }).create();
          paramContext.getWindow().setType(2014);
          paramContext.show();
        }
        return;
      }
    }
  };
  TelephonyManager mTelephonyManager;
  int mToolBoxMode = 0;
  String mToolBoxPackageList = "";
  WindowManagerPolicy.WindowState mTopFullscreenOpaqueOrDimmingWindowState;
  WindowManagerPolicy.WindowState mTopFullscreenOpaqueWindowState;
  boolean mTopIsFullscreen;
  private boolean mTouchExplorationEnabled;
  boolean mTranslucentDecorEnabled = true;
  int mTriplePressOnPowerBehavior;
  private TspStateManager mTspStateManager;
  TwToolBoxFloatingViewer mTwToolBoxFloatingViewer;
  TwToolBoxPointerEventListener mTwToolBoxPointerEventListener;
  int mUiMode;
  IUiModeManager mUiModeManager;
  int mUndockedHdmiRotation;
  int mUnrestrictedScreenHeight;
  int mUnrestrictedScreenLeft;
  int mUnrestrictedScreenTop;
  int mUnrestrictedScreenWidth;
  int mUpsideDownRotation = 0;
  boolean mUseTvRouting;
  int mUserRotation = 0;
  int mUserRotationMode = 0;
  Vibrator mVibrator;
  long[] mVirtualKeyVibePattern;
  int mVoiceContentBottom;
  int mVoiceContentLeft;
  int mVoiceContentRight;
  int mVoiceContentTop;
  int mVolBtnMusicControls;
  int mVolBtnTimeout;
  int mVolBtnVolDown;
  int mVolBtnVolUp;
  final Runnable mVolumeDownLongPress = new MusicPrev();
  private boolean mVolumeUpKeyConsumedByScreenshotChord;
  private long mVolumeUpKeyTime;
  final Runnable mVolumeUpLongPress = new MusicNext();
  boolean mWakeGestureEnabledSetting;
  MyWakeGestureListener mWakeGestureListener;
  public boolean mWatchLaunching = false;
  boolean mWifiDisplayConnected = false;
  int mWifiDisplayCustomRotation = -1;
  BroadcastReceiver mWifiDisplayReceiver = new BroadcastReceiver()
  {
    public void onReceive(Context paramContext, Intent paramIntent)
    {
      if (paramIntent.getAction().equals("org.codeaurora.intent.action.WIFI_DISPLAY_VIDEO"))
        if (paramIntent.getIntExtra("state", 0) != 1)
          break label54;
      label54: for (PhoneWindowManager.this.mWifiDisplayConnected = true; ; PhoneWindowManager.this.mWifiDisplayConnected = false)
      {
        PhoneWindowManager.this.mWifiDisplayCustomRotation = paramIntent.getIntExtra("wfd_UIBC_rot", -1);
        PhoneWindowManager.this.updateRotation(true);
        return;
      }
    }
  };
  private WindowManagerPolicy.WindowState mWinDismissingKeyguard;
  private WindowManagerPolicy.WindowState mWinShowWhenLocked;
  IWindowManager mWindowManager;
  final Runnable mWindowManagerDrawCallback = new Runnable()
  {
    public void run()
    {
      if (PhoneWindowManager.DEBUG_WAKEUP)
        Log.i("WindowManager", "All windows ready for display!");
      PhoneWindowManager.this.mHandler.sendEmptyMessage(7);
    }
  };
  boolean mWindowManagerDrawComplete;
  WindowManagerPolicy.WindowManagerFuncs mWindowManagerFuncs;
  WindowManagerInternal mWindowManagerInternal;

  static
  {
    boolean bool = true;
    if (Debug.isProductShip() == 1)
      bool = false;
    SAFE_DEBUG = bool;
    DEBUG_INPUT = false;
    DEBUG_KEYGUARD = false;
    DEBUG_LAYOUT = false;
    DEBUG_WAKEUP = SAFE_DEBUG;
    VIBRATION_ATTRIBUTES = new AudioAttributes.Builder().setContentType(4).setUsage(13).build();
    sApplicationLaunchKeyCategories = new SparseArray();
    sApplicationLaunchKeyCategories.append(64, "android.intent.category.APP_BROWSER");
    sApplicationLaunchKeyCategories.append(65, "android.intent.category.APP_EMAIL");
    sApplicationLaunchKeyCategories.append(207, "android.intent.category.APP_CONTACTS");
    sApplicationLaunchKeyCategories.append(208, "android.intent.category.APP_CALENDAR");
    sApplicationLaunchKeyCategories.append(209, "android.intent.category.APP_MUSIC");
    sApplicationLaunchKeyCategories.append(210, "android.intent.category.APP_CALCULATOR");
    sApplicationLaunchKeyCategories.append(1072, "android.intent.category.APP_MESSAGING");
    mTmpParentFrame = new Rect();
    mTmpDisplayFrame = new Rect();
    mTmpOverscanFrame = new Rect();
    mTmpContentFrame = new Rect();
    mTmpVisibleFrame = new Rect();
    mTmpDecorFrame = new Rect();
    mTmpStableFrame = new Rect();
    mTmpNavigationFrame = new Rect();
    mTmpOutsetFrame = new Rect();
    DEBUG_DUAL_STATUSBAR = DEBUG_LAYOUT;
    mScreenTurnDisplayId = 0;
    mTmpCarModeFrame = new Rect();
    WINDOW_TYPES_WHERE_HOME_DOESNT_WORK = new int[] { 2003, 2010 };
    wasTopFullscreen = false;
  }

  private void acquireQuickBootWakeLock()
  {
    if (!this.mQuickBootWakeLock.isHeld())
      this.mQuickBootWakeLock.acquire();
  }

  private void applyLidSwitchState()
  {
    this.mPowerManager.setKeyboardVisibility(isBuiltInKeyboardVisible());
    if (FactoryTest.isRunningFactoryApp())
    {
      Slog.d("WindowManager", "applyLidSwitchState isRunningFactoryApp() = true. ignore it.");
      return;
    }
    if ((this.mLidState == 0) && (this.mLidControlsSleep))
      this.mPowerManager.goToSleep(SystemClock.uptimeMillis(), 3, 1);
    synchronized (this.mLock)
    {
      updateWakeGestureListenerLp();
      return;
    }
  }

  private void applyPostLayoutPolicyForFullStatusBarLw()
  {
  }

  private void applyStableConstraints(int paramInt1, int paramInt2, Rect paramRect)
  {
    if ((paramInt1 & 0x100) != 0)
    {
      if ((paramInt2 & 0x400) == 0)
        break label93;
      if (paramRect.left < this.mStableFullscreenLeft)
        paramRect.left = this.mStableFullscreenLeft;
      if (paramRect.top < this.mStableFullscreenTop)
        paramRect.top = this.mStableFullscreenTop;
      if (paramRect.right > this.mStableFullscreenRight)
        paramRect.right = this.mStableFullscreenRight;
      if (paramRect.bottom > this.mStableFullscreenBottom)
        paramRect.bottom = this.mStableFullscreenBottom;
    }
    label93: 
    do
    {
      return;
      if (paramRect.left < this.mStableLeft)
        paramRect.left = this.mStableLeft;
      if (paramRect.top < this.mStableTop)
        paramRect.top = this.mStableTop;
      if (paramRect.right <= this.mStableRight)
        continue;
      paramRect.right = this.mStableRight;
    }
    while (paramRect.bottom <= this.mStableBottom);
    paramRect.bottom = this.mStableBottom;
  }

  private boolean areTranslucentBarsAllowed()
  {
    return (this.mTranslucentDecorEnabled) && (!this.mAccessibilityManager.isTouchExplorationEnabled());
  }

  private static void awakenDreams()
  {
    IDreamManager localIDreamManager = getDreamManager();
    if (localIDreamManager != null);
    try
    {
      localIDreamManager.awaken();
      return;
    }
    catch (RemoteException localRemoteException)
    {
    }
  }

  private boolean canBeGetSViewCoverSize()
  {
    return (this.mCoverState != null) && (this.mCoverState.widthPixel != 0) && (this.mCoverState.heightPixel != 0);
  }

  private boolean canHideNavigationBar()
  {
    return (this.mHasNavigationBar) && (!this.mAccessibilityManager.isTouchExplorationEnabled());
  }

  private boolean canReceiveInput(WindowManagerPolicy.WindowState paramWindowState)
  {
    int i;
    if ((paramWindowState.getAttrs().flags & 0x8) != 0)
    {
      i = 1;
      if ((paramWindowState.getAttrs().flags & 0x20000) == 0)
        break label48;
    }
    label48: for (int j = 1; ; j = 0)
    {
      if ((i ^ j) != 0)
        break label53;
      return true;
      i = 0;
      break;
    }
    label53: return false;
  }

  private void cancelPendingPowerKeyAction()
  {
    this.mPowerKeyHandled = true;
    this.mHandler.removeMessages(14);
  }

  private void cancelPendingScreenshotChordAction()
  {
    this.mHandler.removeCallbacks(this.mScreenshotRunnable);
  }

  private void cancelPendingScreenshotForLog()
  {
    this.mHandler.removeCallbacks(this.mScreenshotForLog);
  }

  private void cancelPreloadRecentApps()
  {
    if (this.mPreloadedRecentApps)
      this.mPreloadedRecentApps = false;
    try
    {
      IStatusBarService localIStatusBarService = getStatusBarService();
      if (localIStatusBarService != null)
        localIStatusBarService.cancelPreloadRecentApps();
      return;
    }
    catch (RemoteException localRemoteException)
    {
      Slog.e("WindowManager", "RemoteException when cancelling recent apps preload", localRemoteException);
      this.mStatusBarService = null;
    }
  }

  private boolean checkTriggerOWC(boolean paramBoolean)
  {
    if ((paramBoolean) && (this.mIsOWCSetting) && (!this.mLockPatternUtils.isFingerPrintLockscreen(UserHandle.myUserId())));
    for (this.mNeedTriggerOWC = true; ; this.mNeedTriggerOWC = false)
      return this.mNeedTriggerOWC;
  }

  private void clearClearableFlagsLw()
  {
    clearClearableFlagsLw(0);
  }

  private void clearClearableFlagsLw(int paramInt)
  {
    paramInt = this.mResettingSystemUiFlags;
    int i = paramInt | 0x7;
    if (i != paramInt)
    {
      this.mResettingSystemUiFlags = i;
      this.mWindowManagerFuncs.reevaluateStatusBarVisibility();
    }
  }

  private void disablePointerLocation()
  {
    if (this.mPointerLocationView != null)
    {
      this.mWindowManagerFuncs.unregisterPointerEventListener(this.mPointerLocationView);
      ((WindowManager)this.mContext.getSystemService("window")).removeView(this.mPointerLocationView);
      this.mPointerLocationView = null;
    }
  }

  private void disableQbCharger()
  {
    if (SystemProperties.getInt("sys.quickboot.enable", 0) == 1)
      SystemProperties.set("sys.qbcharger.enable", "false");
  }

  private void disableToolBox()
  {
    if (this.mTwToolBoxPointerEventListener != null)
    {
      this.mWindowManagerFuncs.unregisterPointerEventListener(this.mTwToolBoxPointerEventListener);
      this.mTwToolBoxPointerEventListener = null;
    }
    if (this.mTwToolBoxFloatingViewer != null)
    {
      this.mTwToolBoxFloatingViewer.unregisterCallback();
      ((WindowManager)this.mContext.getSystemService("window")).removeView(this.mTwToolBoxFloatingViewer);
      this.mTwToolBoxFloatingViewer = null;
    }
  }

  private void dispatchDirectAudioEvent(KeyEvent paramKeyEvent)
  {
    if (paramKeyEvent.getAction() != 0);
    while (true)
    {
      return;
      int i = paramKeyEvent.getKeyCode();
      String str = this.mContext.getOpPackageName();
      switch (i)
      {
      default:
        return;
      case 24:
        try
        {
          getAudioService().adjustSuggestedStreamVolume(1, -2147483648, 4101, str, "WindowManager");
          return;
        }
        catch (RemoteException paramKeyEvent)
        {
          Log.e("WindowManager", "Error dispatching volume up in dispatchTvAudioEvent.", paramKeyEvent);
          return;
        }
      case 25:
        try
        {
          getAudioService().adjustSuggestedStreamVolume(-1, -2147483648, 4101, str, "WindowManager");
          return;
        }
        catch (RemoteException paramKeyEvent)
        {
          Log.e("WindowManager", "Error dispatching volume down in dispatchTvAudioEvent.", paramKeyEvent);
          return;
        }
      case 164:
      }
      try
      {
        if (paramKeyEvent.getRepeatCount() != 0)
          continue;
        getAudioService().adjustSuggestedStreamVolume(101, -2147483648, 4101, str, "WindowManager");
        return;
      }
      catch (RemoteException paramKeyEvent)
      {
        Log.e("WindowManager", "Error dispatching mute in dispatchTvAudioEvent.", paramKeyEvent);
      }
    }
  }

  private void enablePointerLocation()
  {
    if (this.mPointerLocationView == null)
    {
      this.mPointerLocationView = new PointerLocationView(this.mContext);
      this.mPointerLocationView.setPrintCoords(false);
      WindowManager.LayoutParams localLayoutParams = new WindowManager.LayoutParams(-1, -1);
      localLayoutParams.type = 2015;
      localLayoutParams.flags = 1304;
      if (ActivityManager.isHighEndGfx())
      {
        localLayoutParams.flags |= 16777216;
        localLayoutParams.privateFlags |= 2;
      }
      localLayoutParams.format = -3;
      localLayoutParams.setTitle("PointerLocation");
      WindowManager localWindowManager = (WindowManager)this.mContext.getSystemService("window");
      localLayoutParams.inputFeatures |= 2;
      localWindowManager.addView(this.mPointerLocationView, localLayoutParams);
      this.mWindowManagerFuncs.registerPointerEventListener(this.mPointerLocationView);
    }
  }

  private void enableToolBox()
  {
    if (this.mTwToolBoxFloatingViewer == null)
    {
      this.mTwToolBoxFloatingViewer = new TwToolBoxFloatingViewer(this.mContext);
      ((WindowManager)this.mContext.getSystemService("window")).addView(this.mTwToolBoxFloatingViewer, this.mTwToolBoxFloatingViewer.mWindowAttributes);
      this.mTwToolBoxPointerEventListener = new TwToolBoxPointerEventListener(null);
      this.mWindowManagerFuncs.registerPointerEventListener(this.mTwToolBoxPointerEventListener);
      this.mTwToolBoxFloatingViewer.registerCallback();
      this.mTwToolBoxFloatingViewer.mDelegateKeyguardShowing = new TwToolBoxFloatingViewer.DelegateKeyguardShowing()
      {
        public boolean inKeyguardRestrictedKeyInputMode()
        {
          if (PhoneWindowManager.this.mKeyguardDelegate == null)
            return false;
          return PhoneWindowManager.this.mKeyguardDelegate.isInputRestricted();
        }

        public boolean isKeyguardLocked()
        {
          return PhoneWindowManager.this.keyguardOn();
        }

        public boolean isKeyguardSecure()
        {
          if (PhoneWindowManager.this.mKeyguardDelegate == null)
            return false;
          return PhoneWindowManager.this.mKeyguardDelegate.isSecure();
        }

        public boolean isKeyguardShowing()
        {
          return PhoneWindowManager.this.isKeyguardShowingAndNotOccluded();
        }
      };
    }
  }

  private void endCallPress(long paramLong, boolean paramBoolean, int paramInt)
  {
    if (paramInt == 2)
      powerMultiPressAction(paramLong, paramBoolean, this.mDoublePressOnPowerBehavior);
    String str;
    do
    {
      do
      {
        return;
        if (paramInt != 3)
          continue;
        powerMultiPressAction(paramLong, paramBoolean, this.mTriplePressOnPowerBehavior);
        return;
      }
      while ((!paramBoolean) || (this.mBeganFromNonInteractiveEndCall) || (this.mSystemKeyManager.isSystemKeyEventRequested(6)));
      str = SystemProperties.get("security.ode.encrypting");
    }
    while ((this.mSPWM.isRingingOrOffhook()) || ("true".equals(str)) || (SamsungPolicyProperties.isBlockKey(this.mContext)));
    launchHomeFromHotKey();
  }

  private void finishEndCallKeyPress()
  {
    this.mBeganFromNonInteractiveEndCall = false;
    this.mEndCallKeyPressCounter = 0;
  }

  private void finishKeyguardDrawn(int paramInt)
  {
    synchronized (this.mLock)
    {
      boolean bool = this.mScreenOnEarly;
      if ((!bool) || (this.mKeyguardDrawComplete))
      {
        if (DEBUG_WAKEUP)
          Log.i("WindowManager", "finishKeyguardDrawn is failed... mScreenOnEarly : " + bool + ", mKeyguardDrawComplete : " + this.mKeyguardDrawComplete);
        return;
      }
      this.mKeyguardDrawComplete = true;
      if (this.mKeyguardDelegate != null)
        this.mHandler.removeMessages(6);
      this.mWindowManagerDrawComplete = false;
      if (this.mScreenOnListener != null)
      {
        this.mWindowManagerInternal.waitForAllWindowsDrawn(this.mWindowManagerDrawCallback, 1000L, paramInt);
        return;
      }
    }
    this.mWindowManagerInternal.requestTraversalFromDisplayManager();
    this.mHandler.sendEmptyMessage(7);
  }

  private void finishPowerKeyPress()
  {
    this.mBeganFromNonInteractive = false;
    this.mPowerKeyPressCounter = 0;
    if (this.mPowerKeyWakeLock.isHeld())
      this.mPowerKeyWakeLock.release();
  }

  private void finishScreenTurningOn(int paramInt)
  {
    synchronized (this.mLock)
    {
      updateOrientationListenerLp();
    }
    while (true)
    {
      synchronized (this.mLock)
      {
        if (!DEBUG_WAKEUP)
          continue;
        Log.d("WindowManager", "finishScreenTurningOn: mAwake=" + this.mAwake + ", mScreenOnEarly=" + this.mScreenOnEarly + ", mScreenOnFully=" + this.mScreenOnFully + ", mKeyguardDrawComplete=" + this.mKeyguardDrawComplete + ", mWindowManagerDrawComplete=" + this.mWindowManagerDrawComplete);
        if ((this.mScreenOnFully) || (!this.mScreenOnEarly) || (!this.mWindowManagerDrawComplete) || ((this.mAwake) && (!this.mKeyguardDrawComplete)))
        {
          return;
          localObject2 = finally;
          throw localObject2;
        }
        if (!DEBUG_WAKEUP)
          continue;
        Log.i("WindowManager", "Finished screen turning on...");
        WindowManagerPolicy.ScreenOnListener localScreenOnListener = this.mScreenOnListener;
        this.mScreenOnListener = null;
        this.mScreenOnFully = true;
        if ((!this.mKeyguardDrawnOnce) && (this.mAwake))
        {
          this.mKeyguardDrawnOnce = true;
          int i = 1;
          paramInt = i;
          if (!this.mBootMessageNeedsHiding)
            continue;
          this.mBootMessageNeedsHiding = false;
          hideBootMessages();
          paramInt = i;
          if (localScreenOnListener == null)
            continue;
          localScreenOnListener.onScreenOn();
          if (paramInt == 0);
        }
      }
      try
      {
        this.mWindowManager.enableScreenIfNeeded();
        label253: if ((CocktailBarFeatures.isSystemBarType(this.mContext)) && (this.mTouchExplorationEnabled))
          this.mCocktailPhoneWindowManager.updateGripState(this.mTouchExplorationEnabled, 500);
        if (this.mFocusedWindow == null)
          continue;
        this.mTspStateManager.updateWindowPolicy(this.mFocusedWindow);
        return;
        paramInt = 0;
        continue;
        localObject3 = finally;
        monitorexit;
        throw localObject3;
      }
      catch (RemoteException localRemoteException)
      {
        break label253;
      }
    }
  }

  private void finishWindowsDrawn(int paramInt)
  {
    synchronized (this.mLock)
    {
      if ((!this.mScreenOnEarly) || (this.mWindowManagerDrawComplete))
      {
        if (DEBUG_WAKEUP)
          Log.i("WindowManager", "finishWindowsDrawn is failed... mScreenOnEarly : " + this.mScreenOnEarly + ", mWindowManagerDrawComplete : " + this.mWindowManagerDrawComplete);
        return;
      }
      this.mWindowManagerDrawComplete = true;
      finishScreenTurningOn(paramInt);
      return;
    }
  }

  static IAudioService getAudioService()
  {
    IAudioService localIAudioService = IAudioService.Stub.asInterface(ServiceManager.checkService("audio"));
    if (localIAudioService == null)
      Log.w("WindowManager", "Unable to find IAudioService interface.");
    return localIAudioService;
  }

  private ICoverManager getCoverManager()
  {
    monitorenter;
    try
    {
      if (this.mCoverManager == null)
      {
        this.mCoverManager = ICoverManager.Stub.asInterface(ServiceManager.getService("cover"));
        if (this.mCoverManager == null)
          Slog.w("WindowManager", "warning: no COVER_MANAGER_SERVICE");
      }
      ICoverManager localICoverManager = this.mCoverManager;
      return localICoverManager;
    }
    finally
    {
      monitorexit;
    }
    throw localObject;
  }

  static IDreamManager getDreamManager()
  {
    return IDreamManager.Stub.asInterface(ServiceManager.checkService("dreams"));
  }

  private EnterpriseDeviceManager getEDM()
  {
    if (this.mEDM == null)
      this.mEDM = ((EnterpriseDeviceManager)this.mContext.getSystemService("enterprise_policy"));
    return this.mEDM;
  }

  private IFrontLEDManager getFrontLEDManager()
  {
    monitorenter;
    try
    {
      Slog.d("WindowManager", "getFrontLEDManager");
      IFrontLEDManager localIFrontLEDManager = this.mFrontLEDManager;
      monitorexit;
      return localIFrontLEDManager;
    }
    finally
    {
      localObject = finally;
      monitorexit;
    }
    throw localObject;
  }

  private HdmiControl getHdmiControl()
  {
    if (this.mHdmiControl == null)
    {
      HdmiControlManager localHdmiControlManager = (HdmiControlManager)this.mContext.getSystemService("hdmi_control");
      HdmiPlaybackClient localHdmiPlaybackClient = null;
      if (localHdmiControlManager != null)
        localHdmiPlaybackClient = localHdmiControlManager.getPlaybackClient();
      this.mHdmiControl = new HdmiControl(localHdmiPlaybackClient, null);
    }
    return this.mHdmiControl;
  }

  static long[] getLongIntArray(Resources paramResources, int paramInt)
  {
    int[] arrayOfInt = paramResources.getIntArray(paramInt);
    if (arrayOfInt == null)
    {
      paramResources = null;
      return paramResources;
    }
    long[] arrayOfLong = new long[arrayOfInt.length];
    paramInt = 0;
    while (true)
    {
      paramResources = arrayOfLong;
      if (paramInt >= arrayOfInt.length)
        break;
      arrayOfLong[paramInt] = arrayOfInt[paramInt];
      paramInt += 1;
    }
  }

  private int getMaxMultiPressPowerCount()
  {
    if ((this.mTriplePressOnPowerBehavior != 0) && (this.mSPWM.isSafetyAssuranceEnabled()))
      return 3;
    if ((this.mDoublePressOnPowerBehavior != 0) && (this.mSPWM.isDoubleTapOnPowerEnabled()))
      return 2;
    return 1;
  }

  private PersonaManager getPersonaManagerLocked()
  {
    if (this.mIsKNOX2Supported)
    {
      if (this.mPersonaManager == null)
        this.mPersonaManager = ((PersonaManager)this.mContext.getSystemService("persona"));
      return this.mPersonaManager;
    }
    return null;
  }

  private int getResolvedLongPressOnPowerBehavior()
  {
    if ((FactoryTest.isLongPressOnPowerOffEnabled()) || (FactoryTest.isFactoryMode()) || (FactoryTest.isAutomaticTestMode(this.mContext)))
      return 3;
    return this.mLongPressOnPowerBehavior;
  }

  private long getScreenshotChordLongPressDelay()
  {
    if (this.mKeyguardDelegate.isShowing())
      return ()(2.5F * (float)ViewConfiguration.get(this.mContext).getDeviceGlobalActionKeyTimeout());
    return ViewConfiguration.get(this.mContext).getDeviceGlobalActionKeyTimeout();
  }

  private SearchManager getSearchManager()
  {
    if (this.mSearchManager == null)
      this.mSearchManager = ((SearchManager)this.mContext.getSystemService("search"));
    return this.mSearchManager;
  }

  private WindowManagerPolicy.WindowState getTopFullscreenOpaqueWindowState(int paramInt)
  {
    return this.mTopFullscreenOpaqueWindowState;
  }

  private void handleClickOnRecent()
  {
    int i = Settings.System.getInt(this.mContext.getContentResolver(), "left_shortpress_button", 4);
    if (i != 4)
    {
      if (i > 23)
      {
        nromLaunchLastOption(i);
        return;
      }
      Intent localIntent = new Intent();
      localIntent.setAction("android.intent.action.PhoneWindowManagers$AryaModKeyPolicy");
      localIntent.putExtra("action", i);
      this.mContext.sendBroadcast(localIntent);
      return;
    }
    performHapticFeedbackLw(null, 0, false);
    toggleRecentApps();
  }

  private void handleDoubleTapOnHome_ORG()
  {
    if (this.mDoubleTapOnHomeBehavior == 1)
    {
      this.mHomeConsumed = true;
      toggleRecentApps();
    }
  }

  private void handleHideBootMessage()
  {
    synchronized (this.mLock)
    {
      if (!this.mKeyguardDrawnOnce)
      {
        this.mBootMessageNeedsHiding = true;
        return;
      }
      if (this.mCustomDialog)
      {
        if (this.mCustomBootMsgDialog == null)
          return;
        if (DEBUG_WAKEUP)
          Log.d("WindowManager", "handleHideBootMessage: mCustomBootMsgDialog dismissing");
        this.mCustomBootMsgDialog.dismiss();
        this.mCustomBootMsgDialog = null;
        return;
      }
    }
    if (this.mBootMsgDialog != null)
    {
      if (DEBUG_WAKEUP)
        Log.d("WindowManager", "handleHideBootMessage: dismissing");
      this.mBootMsgDialog.dismiss();
      this.mBootMsgDialog = null;
    }
  }

  private void handleLongPressOnHome(int paramInt)
  {
    this.mHomeConsumed = true;
    paramInt = Settings.System.getInt(this.mContext.getContentResolver(), "home_longpress_button", 11);
    if (paramInt != 4)
    {
      if (paramInt > 23)
        nromLaunchLastOption(paramInt);
      while (true)
      {
        performHapticFeedbackLw(null, 0, false);
        return;
        Intent localIntent = new Intent();
        localIntent.setAction("android.intent.action.PhoneWindowManagers$AryaModKeyPolicy");
        localIntent.putExtra("action", paramInt);
        this.mContext.sendBroadcast(localIntent);
      }
    }
    performHapticFeedbackLw(null, 0, false);
    toggleRecentApps();
  }

  private void handleLongPressOnHome_ORG(int paramInt)
  {
    if (this.mSPWM.handleLongPressOnHome());
    do
    {
      do
        return;
      while (this.mLongPressOnHomeBehavior == 0);
      this.mHomeConsumed = true;
      performHapticFeedbackLw(null, 0, false);
      if (this.mLongPressOnHomeBehavior == 1)
      {
        toggleRecentApps();
        return;
      }
      if (this.mLongPressOnHomeBehavior != 2)
        continue;
      if (!isLockTaskModeEnabled())
      {
        launchAssistAction(null, paramInt);
        return;
      }
      new Handler(Looper.getMainLooper()).postDelayed(new Runnable()
      {
        public void run()
        {
          if (PhoneWindowManager.this.alertToast != null)
            PhoneWindowManager.this.alertToast.cancel();
          ContextThemeWrapper localContextThemeWrapper = new ContextThemeWrapper(PhoneWindowManager.this.mContext, 16974123);
          if (PhoneWindowManager.this.mAccessibilityManager.isEnabled());
          for (String str = localContextThemeWrapper.getResources().getString(17040767); ; str = localContextThemeWrapper.getResources().getString(17040766))
          {
            PhoneWindowManager.access$2602(PhoneWindowManager.this, Toast.makeText(localContextThemeWrapper, str, 1));
            PhoneWindowManager.this.alertToast.setShowForAllUsers();
            PhoneWindowManager.this.alertToast.show();
            return;
          }
        }
      }
      , 0L);
      return;
    }
    while (this.mLongPressOnHomeBehavior != 3);
    if (this.mNeedTriggerOWC)
    {
      launchAssistAction(null, paramInt);
      return;
    }
    this.mSPWM.launchSReminder();
  }

  private void handleLongPressOnRecent()
  {
    this.mRecentConsumed = true;
    int i = Settings.System.getInt(this.mContext.getContentResolver(), "menu_longpress_button", 102);
    if (i != 4)
    {
      if (i > 23)
        nromLaunchLastOption(i);
      while (true)
      {
        performHapticFeedbackLw(null, 0, false);
        return;
        Intent localIntent = new Intent();
        localIntent.setAction("android.intent.action.PhoneWindowManagers$AryaModKeyPolicy");
        localIntent.putExtra("action", i);
        this.mContext.sendBroadcast(localIntent);
      }
    }
    performHapticFeedbackLw(null, 0, false);
    toggleRecentApps();
  }

  private void handleNotifyDisplayAdded(int paramInt)
  {
  }

  private void handleShortPressOnHome()
  {
    getHdmiControl().turnOnTv();
    if ((this.mDreamManagerInternal != null) && (this.mDreamManagerInternal.isDreaming()))
    {
      this.mDreamManagerInternal.stopDream(false);
      return;
    }
    launchHomeFromHotKey();
  }

  private boolean hasLongPressOnPowerBehavior()
  {
    return getResolvedLongPressOnPowerBehavior() != 0;
  }

  private void hideRecentApps(boolean paramBoolean1, boolean paramBoolean2)
  {
    if (this.mSPWM.hideRecentApps(paramBoolean1));
    while (true)
    {
      return;
      this.mPreloadedRecentApps = false;
      try
      {
        IStatusBarService localIStatusBarService = getStatusBarService();
        if (localIStatusBarService == null)
          continue;
        localIStatusBarService.hideRecentApps(paramBoolean1, paramBoolean2);
        return;
      }
      catch (RemoteException localRemoteException)
      {
        Slog.e("WindowManager", "RemoteException when closing recent apps", localRemoteException);
        this.mStatusBarService = null;
      }
    }
  }

  private void interceptEndCallKeyDown(KeyEvent paramKeyEvent, boolean paramBoolean)
  {
    paramKeyEvent = getTelecommService();
    boolean bool2 = false;
    boolean bool1 = bool2;
    if (paramKeyEvent != null)
    {
      bool1 = bool2;
      if (paramBoolean)
        bool1 = paramKeyEvent.endCall();
    }
    if ((paramBoolean) && (!bool1))
    {
      this.mEndCallKeyHandled = false;
      this.mHandler.postDelayed(this.mEndCallLongPress, ViewConfiguration.get(this.mContext).getDeviceGlobalActionKeyTimeout());
    }
    do
    {
      return;
      this.mEndCallKeyHandled = true;
    }
    while ((SamsungPolicyProperties.FolderTypeFeature(this.mContext) == 0) || (getMaxMultiPressPowerCount() <= 1));
    this.mBeganFromNonInteractiveEndCall = true;
    this.mEndCallKeyHandled = false;
  }

  private void interceptEndCallKeyUp(KeyEvent paramKeyEvent, boolean paramBoolean1, boolean paramBoolean2)
  {
    int i;
    long l;
    if (!this.mEndCallKeyHandled)
    {
      this.mHandler.removeCallbacks(this.mEndCallLongPress);
      if (SamsungPolicyProperties.FolderTypeFeature(this.mContext) == 0)
        break label130;
      this.mEndCallKeyPressCounter += 1;
      i = getMaxMultiPressPowerCount();
      l = paramKeyEvent.getDownTime();
      if (this.mEndCallKeyPressCounter >= i)
        break label114;
      paramKeyEvent = this.mHandler;
      if (!paramBoolean1)
        break label108;
      i = 1;
      paramKeyEvent = paramKeyEvent.obtainMessage(62, i, this.mEndCallKeyPressCounter, Long.valueOf(l));
      paramKeyEvent.setAsynchronous(true);
      this.mHandler.sendMessageDelayed(paramKeyEvent, ViewConfiguration.getDoubleTapTimeout());
    }
    label108: label114: 
    do
    {
      return;
      i = 0;
      break;
      endCallPress(l, paramBoolean1, this.mEndCallKeyPressCounter);
      finishEndCallKeyPress();
      return;
    }
    while ((paramBoolean2) || ((this.mEndcallBehavior & 0x1) == 0) || (!goHome()));
    label130:
  }

  private boolean interceptFallback(WindowManagerPolicy.WindowState paramWindowState, KeyEvent paramKeyEvent, int paramInt)
  {
    return ((interceptKeyBeforeQueueing(paramKeyEvent, paramInt) & 0x1) != 0) && (interceptKeyBeforeDispatching(paramWindowState, paramKeyEvent, paramInt) == 0L);
  }

  private void interceptPowerKeyDown(KeyEvent paramKeyEvent, boolean paramBoolean)
  {
    if (!this.mPowerKeyWakeLock.isHeld())
      this.mPowerKeyWakeLock.acquire();
    if (this.mPowerKeyPressCounter != 0)
      this.mHandler.removeMessages(13);
    if (this.mImmersiveModeConfirmation.onPowerKeyDown(paramBoolean, SystemClock.elapsedRealtime(), isImmersiveMode(this.mLastSystemUiFlags)))
      this.mHandler.post(this.mHiddenNavPanic);
    if ((paramBoolean) && (!this.mScreenshotChordPowerKeyTriggered) && ((paramKeyEvent.getFlags() & 0x400) == 0))
      this.mScreenshotChordPowerKeyTriggered = true;
    boolean bool4 = false;
    boolean bool3 = false;
    Object localObject1 = getTelecommService();
    Object localObject2 = this.mSPWM;
    localObject2 = SamsungPhoneWindowManager.getVoIPInterfaceService();
    boolean bool1 = bool3;
    boolean bool2;
    if (localObject1 != null)
      bool2 = bool4;
    try
    {
      if (((TelecomManager)localObject1).isRinging())
      {
        bool2 = bool4;
        ((TelecomManager)localObject1).silenceRinger();
        bool2 = bool4;
        this.mSPWM.insertLog("VCPS", null);
        bool1 = bool3;
        localObject1 = (GestureLauncherService)LocalServices.getService(GestureLauncherService.class);
        if (localObject1 != null)
          ((GestureLauncherService)localObject1).interceptPowerKeyDown(paramKeyEvent, paramBoolean);
        if ((!bool1) && (!this.mSPWM.isCombinationKeyTriggered()))
          break label547;
        bool1 = true;
        this.mPowerKeyHandled = bool1;
        if (!this.mPowerKeyHandled)
        {
          if (!paramBoolean)
            break label596;
          if ((!this.mSPWM.isLiveDemo()) || (!this.mSPWM.isHMTSupportAndConnected()))
            break label552;
          paramKeyEvent = this.mHandler.obtainMessage(14);
          paramKeyEvent.setAsynchronous(true);
          this.mHandler.sendMessageDelayed(paramKeyEvent, 5000L);
        }
        if ((this.mCoverManager == null) && ((this.mIsSupportFlipCover) || (this.mIsSupportSViewCover)))
          this.mCoverManager = getCoverManager();
        if ((this.mCoverManager != null) && (this.mCoverState != null) && ((this.mIsSupportFlipCover) || (this.mIsSupportSViewCover)))
          if ((this.mCoverState.switchState) || (this.mCoverState.getType() != 7));
      }
    }
    catch (RemoteException localRemoteException)
    {
      try
      {
        while (true)
        {
          this.mCoverManager.sendPowerKeyToCover();
          return;
          bool2 = bool4;
          if (SamsungPolicyProperties.FolderTypeFeature(this.mContext) == 0)
          {
            bool2 = bool4;
            if ((this.mIncallPowerBehavior & 0x2) != 0)
            {
              bool2 = bool4;
              if (((TelecomManager)localObject1).isInCall())
                if (!paramBoolean)
                {
                  bool2 = bool4;
                  if (this.mHasNavigationBar);
                }
                else
                {
                  bool2 = bool4;
                  bool1 = ((TelecomManager)localObject1).endCall();
                  bool2 = bool1;
                  this.mSPWM.insertLog("VCPE", null);
                  continue;
                  localRemoteException = localRemoteException;
                  Log.w("WindowManager", "ITelephony threw RemoteException", localRemoteException);
                  bool1 = bool2;
                  continue;
                }
            }
          }
          bool1 = bool3;
          bool2 = bool4;
          if ((this.mIncallPowerBehavior & 0x2) == 0)
            continue;
          bool1 = bool3;
          if (localObject2 == null)
            continue;
          bool2 = bool4;
          if (!((IVoIPInterface)localObject2).isVoIPDialing())
          {
            bool1 = bool3;
            bool2 = bool4;
            if (!((IVoIPInterface)localObject2).isVoIPActivated())
              continue;
          }
          bool1 = bool3;
          bool2 = bool4;
          if (((IVoIPInterface)localObject2).getVoIPCallCount("com.amc.ui") <= 0)
            continue;
          bool2 = bool4;
          bool1 = ((IVoIPInterface)localObject2).hangupVoIPCall();
          continue;
          label547: bool1 = false;
          continue;
          label552: if (!hasLongPressOnPowerBehavior())
            continue;
          paramKeyEvent = this.mHandler.obtainMessage(14);
          paramKeyEvent.setAsynchronous(true);
          this.mHandler.sendMessageDelayed(paramKeyEvent, ViewConfiguration.get(this.mContext).getDeviceGlobalActionKeyTimeout());
        }
        label596: wakeUpFromPowerKey(paramKeyEvent.getDownTime());
        if (getMaxMultiPressPowerCount() <= 1)
          this.mPowerKeyHandled = true;
        while ((SamsungPolicyProperties.FolderTypeFeature(this.mContext) != 0) && (isCallRecord()))
        {
          paramKeyEvent = this.mHandler.obtainMessage(14);
          paramKeyEvent.setAsynchronous(true);
          this.mHandler.sendMessageDelayed(paramKeyEvent, ViewConfiguration.get(this.mContext).getDeviceGlobalActionKeyTimeout());
          break;
          this.mBeganFromNonInteractive = true;
        }
      }
      catch (RemoteException paramKeyEvent)
      {
        Log.w("WindowManager", "CoverManager threw RemoteException", paramKeyEvent);
      }
    }
  }

  private void interceptPowerKeyUp(KeyEvent paramKeyEvent, boolean paramBoolean1, boolean paramBoolean2)
  {
    int j = 0;
    int i;
    if ((paramBoolean2) || (this.mPowerKeyHandled))
      i = 1;
    while (true)
    {
      this.mScreenshotChordPowerKeyTriggered = false;
      cancelPendingScreenshotChordAction();
      cancelPendingPowerKeyAction();
      if (i != 0)
        break;
      this.mPowerKeyPressCounter += 1;
      i = getMaxMultiPressPowerCount();
      long l = paramKeyEvent.getDownTime();
      if (this.mPowerKeyPressCounter < i)
      {
        paramKeyEvent = this.mHandler;
        i = j;
        if (paramBoolean1)
          i = 1;
        paramKeyEvent = paramKeyEvent.obtainMessage(13, i, this.mPowerKeyPressCounter, Long.valueOf(l));
        paramKeyEvent.setAsynchronous(true);
        this.mHandler.sendMessageDelayed(paramKeyEvent, ViewConfiguration.getDoubleTapTimeout());
        return;
        i = 0;
        continue;
      }
      powerPress(l, paramBoolean1, this.mPowerKeyPressCounter);
    }
    finishPowerKeyPress();
  }

  private void interceptScreenshotChord()
  {
    if ((this.mScreenshotChordEnabled) && (this.mScreenshotChordVolumeDownKeyTriggered) && (this.mScreenshotChordPowerKeyTriggered) && (!this.mScreenshotChordVolumeUpKeyTriggered))
    {
      long l = SystemClock.uptimeMillis();
      if ((l <= this.mScreenshotChordVolumeDownKeyTime + 150L) && (l <= this.mScreenshotChordPowerKeyTime + 150L))
      {
        this.mScreenshotChordVolumeDownKeyConsumed = true;
        cancelPendingPowerKeyAction();
        this.mHandler.postDelayed(this.mScreenshotRunnable, getScreenshotChordLongPressDelay());
      }
    }
  }

  private void interceptScreenshotLog()
  {
    if ((this.mScreenshotChordEnabled) && (this.mScreenshotChordVolumeUpKeyTriggered) && (this.mScreenshotChordPowerKeyTriggered) && (!this.mScreenshotChordVolumeDownKeyTriggered))
    {
      long l = SystemClock.uptimeMillis();
      if ((l <= this.mVolumeUpKeyTime + 150L) && (l <= this.mScreenshotChordPowerKeyTime + 150L))
      {
        this.mVolumeUpKeyConsumedByScreenshotChord = true;
        cancelPendingScreenshotForLog();
        this.mHandler.postDelayed(this.mScreenshotForLog, getScreenshotChordLongPressDelay());
      }
    }
  }

  private boolean isAnyPortrait(int paramInt)
  {
    return isAnyPortrait(paramInt, 0);
  }

  private boolean isAnyPortrait(int paramInt1, int paramInt2)
  {
    return (paramInt1 == this.mPortraitRotation) || (paramInt1 == this.mUpsideDownRotation);
  }

  private boolean isBuiltInKeyboardVisible()
  {
    return (this.mHaveBuiltInKeyboard) && (!isHidden(this.mLidKeyboardAccessibility));
  }

  private boolean isCallRecord()
  {
    TelecomManager localTelecomManager = getTelecommService();
    if (Settings.System.getIntForUser(this.mContext.getContentResolver(), "hold_key_record_calls_enable_sharedpref", 0, -2) != 0);
    for (boolean bool = true; ; bool = false)
    {
      Slog.d("WindowManager", "isRecordCallEnable = " + bool);
      if ((!bool) || (localTelecomManager == null) || (!localTelecomManager.isInCall()) || (localTelecomManager.isRinging()))
        break;
      return true;
    }
    return false;
  }

  private boolean isForceHideStatusBarWithFullDisplay(int paramInt)
  {
    return false;
  }

  private boolean isGlobalAccessibilityGestureEnabled()
  {
    return Settings.Global.getInt(this.mContext.getContentResolver(), "enable_accessibility_global_gesture_enabled", 0) == 1;
  }

  private boolean isHidden(int paramInt)
  {
    int i = 1;
    switch (paramInt)
    {
    default:
      i = 0;
    case 1:
    case 2:
    }
    do
    {
      do
        return i;
      while (this.mLidState == 0);
      return false;
    }
    while (this.mLidState == 1);
    return false;
  }

  private boolean isImmersiveMode(int paramInt)
  {
    return (this.mNavigationBar != null) && ((paramInt & 0x2) != 0) && ((paramInt & 0x1800) != 0) && (canHideNavigationBar());
  }

  private boolean isKnoxKeyguardShownForKioskMode()
  {
    if ((getPersonaManagerLocked() != null) && (this.mPersonaManager.isKioskContainerExistOnDevice()) && (this.mPersonaManager.getKeyguardShowState(this.mPersonaManager.getFocusedUser())))
    {
      Log.d("WindowManager", "Animations disallowed by KNOX COM keyguard.");
      return true;
    }
    return false;
  }

  private boolean isKnoxKeyguardShownForKioskMode(int paramInt)
  {
    if ((getPersonaManagerLocked() != null) && (this.mPersonaManager.isKioskContainerExistOnDevice()) && (this.mPersonaManager.getKeyguardShowState(paramInt)))
    {
      Log.d("WindowManager", "Animations disallowed by KNOX COM keyguard.");
      return true;
    }
    return false;
  }

  private boolean isLandscapeOrSeascape(int paramInt)
  {
    return isLandscapeOrSeascape(paramInt, 0);
  }

  private boolean isLandscapeOrSeascape(int paramInt1, int paramInt2)
  {
    return (paramInt1 == this.mLandscapeRotation) || (paramInt1 == this.mSeascapeRotation);
  }

  private boolean isRoundWindow()
  {
    return this.mContext.getResources().getConfiguration().isScreenRound();
  }

  private boolean isSViewCoverHostWindow(WindowManager.LayoutParams paramLayoutParams)
  {
    return paramLayoutParams.type == 2000;
  }

  private boolean isSupportAndAttachedSViewCover()
  {
    return (this.mIsSupportSViewCover) && (this.mCoverState != null) && (((this.mCoverState.type == 1) && (this.mCoverState.model != 3)) || (this.mCoverState.type == 3) || (this.mCoverState.type == 6) || (this.mCoverState.type == 255) || (this.mCoverState.type == 8));
  }

  private boolean isTheaterModeEnabled()
  {
    return Settings.Global.getInt(this.mContext.getContentResolver(), "theater_mode_on", 0) == 1;
  }

  private static boolean isValidGlobalKey(int paramInt)
  {
    switch (paramInt)
    {
    default:
      return true;
    case 26:
    case 223:
    case 224:
    }
    return false;
  }

  private boolean isWakeKeyWhenScreenOff(int paramInt)
  {
    switch (paramInt)
    {
    default:
    case 24:
    case 25:
    case 164:
    case 27:
    case 79:
    case 85:
    case 86:
    case 87:
    case 88:
    case 89:
    case 90:
    case 91:
    case 126:
    case 127:
    case 130:
    case 222:
    case 1069:
    case 3:
    }
    do
    {
      do
      {
        do
          return true;
        while (this.mDockMode != 0);
        return false;
      }
      while (SamsungPolicyProperties.FolderTypeFeature(this.mContext) == 1);
      return false;
    }
    while (Settings.System.getInt(this.mContext.getContentResolver(), "homewake_toggle", 1) != 0);
    return false;
  }

  private void launchAssistAction(String paramString, int paramInt)
  {
    sendCloseSystemWindows("assist");
    if (!isUserSetupComplete());
    while (true)
    {
      return;
      Bundle localBundle1 = null;
      if (paramInt > -2147483648)
      {
        localBundle1 = new Bundle();
        localBundle1.putInt("android.intent.extra.ASSIST_INPUT_DEVICE_ID", paramInt);
      }
      if ((this.mContext.getResources().getConfiguration().uiMode & 0xF) == 4)
      {
        ((SearchManager)this.mContext.getSystemService("search")).launchLegacyAssist(paramString, UserHandle.myUserId(), localBundle1);
        return;
      }
      Bundle localBundle2 = localBundle1;
      if (paramString != null)
      {
        localBundle2 = localBundle1;
        if (localBundle1 != null);
      }
      try
      {
        localBundle2 = new Bundle();
        localBundle2.putBoolean(paramString, true);
        paramString = getStatusBarService();
        if (paramString == null)
          continue;
        paramString.startAssist(localBundle2);
        return;
      }
      catch (RemoteException paramString)
      {
        Slog.e("WindowManager", "RemoteException when starting assist", paramString);
        this.mStatusBarService = null;
      }
    }
  }

  private void launchAssistLongPressAction()
  {
    performHapticFeedbackLw(null, 0, false);
    sendCloseSystemWindows("assist");
    Intent localIntent = new Intent("android.intent.action.SEARCH_LONG_PRESS");
    localIntent.setFlags(268435456);
    try
    {
      SearchManager localSearchManager = getSearchManager();
      if (localSearchManager != null)
        localSearchManager.stopSearch();
      startActivityAsUser(localIntent, UserHandle.CURRENT);
      return;
    }
    catch (ActivityNotFoundException localActivityNotFoundException)
    {
      Slog.w("WindowManager", "No activity to handle assist long press action.", localActivityNotFoundException);
    }
  }

  private void launchKeyguardOwner()
  {
    if (this.mKeyguardDelegate != null)
    {
      this.mKeyguardDelegate.onSystemReady();
      Log.d("WindowManager", "show keyguard");
    }
  }

  private void notifyToSSRM(boolean paramBoolean)
  {
    if (wasTopFullscreen == paramBoolean)
      return;
    wasTopFullscreen = paramBoolean;
    Intent localIntent = new Intent();
    localIntent.setAction("com.sec.android.intent.action.SSRM_REQUEST");
    localIntent.putExtra("SSRM_STATUS_NAME", "FullScreen");
    localIntent.putExtra("SSRM_STATUS_VALUE", wasTopFullscreen);
    this.mContext.sendBroadcast(localIntent);
  }

  private void nromLaunchCamera()
  {
    this.mSPWM.launchDoubleTapOnHomeCommand();
  }

  private void nromLaunchLastOption(int paramInt)
  {
    switch (paramInt)
    {
    default:
      return;
    case 101:
      nromLaunchCamera();
      return;
    case 102:
    }
    nromLaunchLongpressRecentKey();
  }

  private void nromLaunchLongpressRecentKey()
  {
    this.mSPWM.handleLongPressOnRecent();
  }

  private void offsetInputMethodWindowLw(WindowManagerPolicy.WindowState paramWindowState)
  {
    int i = Math.max(paramWindowState.getDisplayFrameLw().top, paramWindowState.getContentFrameLw().top) + paramWindowState.getGivenContentInsetsLw().top;
    if (this.mContentBottom > i)
      this.mContentBottom = i;
    if (this.mVoiceContentBottom > i)
      this.mVoiceContentBottom = i;
    i = paramWindowState.getVisibleFrameLw().top + paramWindowState.getGivenVisibleInsetsLw().top;
    if (this.mCurBottom > i)
      this.mCurBottom = i;
    if (DEBUG_LAYOUT)
      Slog.v("WindowManager", "Input method: mDockBottom=" + this.mDockBottom + " mContentBottom=" + this.mContentBottom + " mCurBottom=" + this.mCurBottom);
  }

  private void offsetVoiceInputWindowLw(WindowManagerPolicy.WindowState paramWindowState)
  {
    int i = Math.max(paramWindowState.getDisplayFrameLw().top, paramWindowState.getContentFrameLw().top) + paramWindowState.getGivenContentInsetsLw().top;
    if (this.mVoiceContentBottom > i)
      this.mVoiceContentBottom = i;
  }

  private void performAuditoryFeedbackForAccessibilityIfNeed()
  {
    if (!isGlobalAccessibilityGestureEnabled());
    do
      return;
    while (((AudioManager)this.mContext.getSystemService("audio")).isSilentMode());
    Ringtone localRingtone = RingtoneManager.getRingtone(this.mContext, Settings.System.DEFAULT_NOTIFICATION_URI);
    localRingtone.setStreamType(3);
    localRingtone.play();
  }

  private void powerLongPress()
  {
    boolean bool = true;
    if (this.mSPWM.ignorePowerKeyInEncrypting())
      return;
    if ((SamsungPolicyProperties.FolderTypeFeature(this.mContext) != 0) && (isCallRecord()))
    {
      this.mPowerKeyHandled = true;
      try
      {
        Object localObject = new ComponentName("com.android.phone", "com.android.phone.PhoneVoiceRecorderService");
        localObject = new Intent("com.samsung.phone.PhoneVoiceRecorderService").setComponent((ComponentName)localObject);
        ((Intent)localObject).putExtra("StartByLongPressPowerKey", true);
        this.mContext.startService((Intent)localObject);
        Slog.d("WindowManager", "Start PhoneVoiceRecorderService");
        return;
      }
      catch (Exception localException)
      {
        Slog.e("WindowManager", "Exception PhoneVoiceRecorderService: ", localException);
        return;
      }
    }
    int j = getResolvedLongPressOnPowerBehavior();
    int i = j;
    if (this.mSPWM.isLiveDemo())
    {
      i = j;
      if (this.mSPWM.isHMTSupportAndConnected())
      {
        Log.i("WindowManager", "LDU HTM set, so no confirm shutdown");
        i = 3;
      }
    }
    switch (i)
    {
    case 0:
    default:
      return;
    case 1:
      this.mPowerKeyHandled = true;
      if (!performHapticFeedbackLw(null, 0, false))
        performAuditoryFeedbackForAccessibilityIfNeed();
      showGlobalActionsInternal();
      this.mSPWM.setProKioskReEnableVolumeUpKey(true);
      return;
    case 2:
    case 3:
    }
    this.mPowerKeyHandled = true;
    sendCloseSystemWindows("globalactions");
    WindowManagerPolicy.WindowManagerFuncs localWindowManagerFuncs = this.mWindowManagerFuncs;
    if (i == 2);
    while (true)
    {
      localWindowManagerFuncs.shutdown(bool);
      return;
      bool = false;
    }
  }

  private void powerMultiPressAction(long paramLong, boolean paramBoolean, int paramInt)
  {
    switch (paramInt)
    {
    case 0:
    default:
    case 1:
    case 2:
    case 3:
    case 4:
    }
    do
    {
      do
      {
        do
        {
          while (true)
          {
            return;
            if (!isUserSetupComplete())
            {
              Slog.i("WindowManager", "Ignoring toggling theater mode - device not setup.");
              return;
            }
            if (!isTheaterModeEnabled())
              break;
            Slog.i("WindowManager", "Toggling theater mode off.");
            Settings.Global.putInt(this.mContext.getContentResolver(), "theater_mode_on", 0);
            if (paramBoolean)
              continue;
            wakeUpFromPowerKey(paramLong);
            return;
          }
          Slog.i("WindowManager", "Toggling theater mode on.");
          Settings.Global.putInt(this.mContext.getContentResolver(), "theater_mode_on", 1);
        }
        while ((!this.mGoToSleepOnButtonPressTheaterMode) || (!paramBoolean));
        this.mPowerManager.goToSleep(paramLong, 4, 0);
        return;
        Slog.i("WindowManager", "Starting brightness boost.");
        if (!paramBoolean)
          wakeUpFromPowerKey(paramLong);
        this.mPowerManager.boostScreenBrightness(paramLong);
        return;
        Slog.i("WindowManager", "Screen Curtain mode toggle.");
      }
      while (!this.mSPWM.isDoubleTapOnPowerEnabled());
      this.mSPWM.callAccessibilityScreenCurtain();
      return;
      Slog.i("WindowManager", "SOS Message mode toggle.");
    }
    while (!this.mSPWM.isSafetyAssuranceEnabled());
    this.mSPWM.sendBroadcastForSafetyAssurance();
  }

  private void powerPress(long paramLong, boolean paramBoolean, int paramInt)
  {
    if ((this.mScreenOnEarly) && (!this.mScreenOnFully) && (this.mTriplePressOnPowerBehavior != 4))
      Slog.i("WindowManager", "Suppressed redundant power key press while already in the process of turning the screen on. Except sos message mode");
    do
    {
      return;
      if (paramInt == 2)
      {
        powerMultiPressAction(paramLong, paramBoolean, this.mDoublePressOnPowerBehavior);
        return;
      }
      if (paramInt != 3)
        continue;
      powerMultiPressAction(paramLong, paramBoolean, this.mTriplePressOnPowerBehavior);
      return;
    }
    while ((!paramBoolean) || (this.mBeganFromNonInteractive));
    switch (this.mShortPressOnPowerBehavior)
    {
    case 0:
    default:
      return;
    case 1:
      this.mPowerManager.goToSleep(paramLong, 4, 0);
      return;
    case 2:
      this.mPowerManager.goToSleep(paramLong, 4, 1);
      return;
    case 3:
      this.mPowerManager.goToSleep(paramLong, 4, 1);
      launchHomeFromHotKey();
      return;
    case 4:
    }
    launchHomeFromHotKey(true, false);
  }

  private void preloadRecentApps()
  {
    this.mPreloadedRecentApps = true;
    try
    {
      IStatusBarService localIStatusBarService = getStatusBarService();
      if (localIStatusBarService != null)
        localIStatusBarService.preloadRecentApps();
      return;
    }
    catch (RemoteException localRemoteException)
    {
      Slog.e("WindowManager", "RemoteException when preloading recent apps", localRemoteException);
      this.mStatusBarService = null;
    }
  }

  private void prepareSViewCoverLayout(int paramInt1, int paramInt2, int paramInt3, int paramInt4, int paramInt5, int paramInt6, int paramInt7, int paramInt8, int paramInt9, int paramInt10, int paramInt11, int paramInt12, int paramInt13, int paramInt14, int paramInt15, int paramInt16, int paramInt17, int paramInt18, int paramInt19, int paramInt20, int paramInt21, int paramInt22, int paramInt23, int paramInt24, int paramInt25)
  {
    this.mOriginalSystemLeft = paramInt4;
    this.mSViewCoverSystemLeft = paramInt4;
    this.mOriginalSystemTop = paramInt5;
    this.mSViewCoverSystemTop = paramInt5;
    this.mOriginalSystemRight = paramInt6;
    this.mSViewCoverSystemRight = paramInt6;
    this.mOriginalSystemBottom = paramInt7;
    this.mSViewCoverSystemBottom = paramInt7;
    this.mOriginalUnrestrictedScreenLeft = paramInt8;
    this.mSViewCoverUnrestrictedScreenLeft = paramInt8;
    this.mOriginalUnrestrictedScreenTop = paramInt9;
    this.mSViewCoverUnrestrictedScreenTop = paramInt9;
    this.mOriginalUnrestrictedScreenWidth = paramInt10;
    this.mSViewCoverUnrestrictedScreenWidth = paramInt10;
    this.mOriginalUnrestrictedScreenHeight = paramInt11;
    this.mSViewCoverUnrestrictedScreenHeight = paramInt11;
    this.mOriginalStableFullscreenLeft = paramInt12;
    this.mSViewCoverStableFullscreenLeft = paramInt12;
    this.mOriginalStableFullscreenTop = paramInt13;
    this.mSViewCoverStableFullscreenTop = paramInt13;
    this.mOriginalStableFullscreenRight = paramInt14;
    this.mSViewCoverStableFullscreenRight = paramInt14;
    this.mOriginalStableFullscreenBottom = paramInt15;
    this.mSViewCoverStableFullscreenBottom = paramInt15;
    this.mSViewCoverStableLeft = paramInt16;
    this.mOriginalStableLeft = paramInt16;
    this.mSViewCoverStableTop = paramInt17;
    this.mOriginalStableTop = paramInt17;
    this.mSViewCoverStableRight = paramInt18;
    this.mOriginalStableRight = paramInt18;
    this.mSViewCoverStableBottom = paramInt19;
    this.mOriginalStableBottom = paramInt19;
    this.mSViewCoverDockLeft = paramInt20;
    this.mOriginalDockLeft = paramInt20;
    this.mSViewCoverDockTop = paramInt21;
    this.mOriginalDockTop = paramInt21;
    this.mSViewCoverDockRight = paramInt22;
    this.mOriginalDockRight = paramInt22;
    this.mSViewCoverDockBottom = paramInt23;
    this.mOriginalDockBottom = paramInt23;
    if ((paramInt24 != 0) && (paramInt25 != 0))
      switch (paramInt1)
      {
      default:
        paramInt1 = 0;
        paramInt2 = 0;
        paramInt4 = 0;
        paramInt5 = paramInt3 - paramInt24;
        paramInt3 = paramInt1;
        paramInt1 = paramInt5;
      case 1:
      case 2:
      case 3:
      }
    while (true)
    {
      this.mSViewCoverSystemLeft = (this.mOriginalSystemLeft + paramInt2);
      this.mSViewCoverSystemTop = (this.mOriginalSystemTop + paramInt4);
      this.mSViewCoverSystemRight = (this.mOriginalSystemRight - paramInt3);
      this.mSViewCoverSystemBottom = (this.mOriginalSystemBottom - paramInt1);
      this.mSViewCoverUnrestrictedScreenLeft = (this.mOriginalUnrestrictedScreenLeft + paramInt2);
      this.mSViewCoverUnrestrictedScreenTop = (this.mOriginalUnrestrictedScreenTop + paramInt4);
      this.mSViewCoverUnrestrictedScreenWidth = (this.mOriginalUnrestrictedScreenWidth - paramInt2 - paramInt3);
      this.mSViewCoverUnrestrictedScreenHeight = (this.mOriginalUnrestrictedScreenHeight - paramInt4 - paramInt1);
      this.mSViewCoverStableFullscreenRight = (this.mOriginalStableFullscreenRight - paramInt3);
      this.mSViewCoverStableFullscreenBottom = (this.mOriginalStableFullscreenBottom - paramInt1);
      this.mSViewCoverStableRight = (this.mOriginalStableRight - paramInt3);
      this.mSViewCoverStableBottom = (this.mOriginalStableBottom - paramInt1);
      this.mSViewCoverDockRight = (this.mOriginalDockRight - paramInt3);
      this.mSViewCoverDockBottom = (this.mOriginalDockBottom - paramInt1);
      return;
      paramInt3 = 0;
      paramInt1 = 0;
      paramInt4 = 0;
      paramInt5 = paramInt2 - paramInt24;
      paramInt2 = paramInt3;
      paramInt3 = paramInt5;
      continue;
      paramInt4 = 0;
      paramInt2 = 0;
      paramInt5 = paramInt3 - paramInt24;
      paramInt1 = 0;
      paramInt3 = paramInt4;
      paramInt4 = paramInt5;
      continue;
      paramInt2 -= paramInt24;
      paramInt1 = 0;
      paramInt4 = 0;
      paramInt3 = 0;
    }
  }

  private boolean processSViewCoverSetHiddenResultLw(int paramInt)
  {
    WindowManager.LayoutParams localLayoutParams;
    if ((this.mStatusBar != null) && ((paramInt & 0x1) != 0))
    {
      localLayoutParams = this.mStatusBar.getAttrs();
      localLayoutParams.samsungFlags |= 268435456;
      return true;
    }
    if ((this.mStatusBar != null) && ((paramInt & 0x2) != 0))
    {
      localLayoutParams = this.mStatusBar.getAttrs();
      localLayoutParams.samsungFlags &= -268435457;
      return true;
    }
    return false;
  }

  private void readCameraLensCoverState()
  {
    this.mCameraLensCoverState = this.mWindowManagerFuncs.getCameraLensCoverState();
  }

  private void readConfigurationDependentBehaviors()
  {
    this.mLongPressOnHomeBehavior = this.mContext.getResources().getInteger(17694809);
    if ((this.mLongPressOnHomeBehavior < 0) || (this.mLongPressOnHomeBehavior > 3))
      this.mLongPressOnHomeBehavior = 0;
    if ((this.mLongPressOnHomeBehavior == 1) && (this.mContext.getResources().getBoolean(17957067)))
      this.mLongPressOnHomeBehavior = 2;
    this.mDoubleTapOnHomeBehavior = this.mContext.getResources().getInteger(17694810);
    if ((this.mDoubleTapOnHomeBehavior < 0) || (this.mDoubleTapOnHomeBehavior > 1))
      this.mDoubleTapOnHomeBehavior = 0;
  }

  private int readRotation(int paramInt)
  {
    try
    {
      paramInt = this.mContext.getResources().getInteger(paramInt);
      switch (paramInt)
      {
      default:
        return -1;
      case 0:
        return 0;
      case 90:
        return 1;
      case 180:
        label56: return 2;
      case 270:
      }
      return 3;
    }
    catch (Resources.NotFoundException localNotFoundException)
    {
      break label56;
    }
  }

  private void reconfigureDebug(boolean[] paramArrayOfBoolean)
  {
    if (paramArrayOfBoolean.length != 4)
      return;
    int i = 0 + 1;
    DEBUG_INPUT = paramArrayOfBoolean[0];
    int j = i + 1;
    DEBUG_KEYGUARD = paramArrayOfBoolean[i];
    DEBUG_LAYOUT = paramArrayOfBoolean[j];
    DEBUG_WAKEUP = paramArrayOfBoolean[(j + 1)];
  }

  private void releaseQuickBootWakeLock()
  {
    if (this.mQuickBootWakeLock.isHeld())
      this.mQuickBootWakeLock.release();
  }

  private void requestTransientBars(WindowManagerPolicy.WindowState paramWindowState)
  {
    requestTransientBars(paramWindowState, 0);
  }

  private void requestTransientBars(WindowManagerPolicy.WindowState paramWindowState, int paramInt)
  {
    if ((FactoryTest.isFactoryMode()) || (FactoryTest.isAutomaticTestMode(this.mContext)) || (FactoryTest.isRunningFactoryApp()))
      Slog.d("WindowManager", "Not showing transient bar, becuase Factory mode");
    do
      return;
    while (isLockTaskModeEnabled());
    if ((AppLockPolicy.isSupportAppLock()) && (this.mSPWM.isAppLockRunning()))
    {
      Slog.d("WindowManager", "Not showing transient bar, becuase AppLock running");
      return;
    }
    WindowManagerPolicy.WindowState localWindowState;
    synchronized (this.mWindowManagerFuncs.getWindowManagerLock())
    {
      localWindowState = this.mStatusBar;
      localWindowState = this.mNavigationBar;
      if (!isUserSetupComplete())
        return;
    }
    if (this.mSPWM.isMirrorLinkEnabled())
    {
      Slog.d("WindowManager", "Block requestTransientBars, isMirrorLinkEnabled() true");
      monitorexit;
      return;
    }
    boolean bool1 = this.mStatusBarController.checkShowTransientBarLw(paramInt);
    boolean bool2 = this.mNavigationBarController.checkShowTransientBarLw(paramInt);
    if ((bool1) || (bool2))
    {
      if ((!bool2) && (paramWindowState == localWindowState))
      {
        monitorexit;
        return;
      }
      if (bool1)
        this.mStatusBarController.showTransient(paramInt);
      if (bool2)
        this.mNavigationBarController.showTransient(paramInt);
      this.mImmersiveModeConfirmation.confirmCurrentPrompt();
      updateSystemUiVisibilityLw(paramInt);
    }
    monitorexit;
  }

  private int rotationForSecondLcdOrientationLw(int paramInt1, int paramInt2)
  {
    while (true)
    {
      synchronized (this.mLock)
      {
        StringBuilder localStringBuilder = new StringBuilder().append("2nd LCD, rotationForSecondLcdOrientationLw(orient=").append(paramInt1).append(", last=").append(paramInt2).append("); user=").append(this.mUserRotation).append(" ");
        if (this.mSecondLcdUserRotationMode == 1)
        {
          String str1 = "USER_ROTATION_LOCKED";
          Slog.v("WindowManager", str1 + " sensorRotation=" + this.mOrientationListener.getProposedRotation());
          this.mSecondLcdLastRotation = paramInt2;
          paramInt2 = this.mOrientationListener.getProposedRotation();
          if (paramInt1 != 14)
            continue;
          paramInt1 = this.mSecondLcdLastRotation;
          this.mSecondLcdLastRotation = paramInt1;
          return paramInt1;
          if ((this.mSecondLcdUserRotationMode != 0) || ((paramInt1 != 2) && (paramInt1 != -1) && (paramInt1 != 11) && (paramInt1 != 12) && (paramInt1 != 13)))
            break label243;
          if ((paramInt2 != 2) || (this.mAllowAllRotations == 1) || (paramInt1 == 10) || (paramInt1 == 13))
            break label269;
          paramInt1 = this.mSecondLcdLastRotation;
          continue;
          if ((this.mSecondLcdUserRotationMode != 1) || (paramInt1 == 5))
            break label274;
          paramInt1 = this.mUserRotation;
        }
      }
      String str2 = "";
      continue;
      label243: if ((paramInt1 == 4) || (paramInt1 == 10) || (paramInt1 == 6))
        continue;
      if (paramInt1 != 7)
        continue;
      continue;
      label269: paramInt1 = paramInt2;
      continue;
      label274: paramInt1 = -1;
    }
  }

  public static void sendCloseSystemWindows(Context paramContext, String paramString)
  {
    PhoneWindow.sendCloseSystemWindows(paramContext, paramString);
  }

  private void setBottomSoftkeyRotation(int paramInt)
  {
    if (this.mCurrentDisplayRotation != paramInt)
    {
      if (paramInt != 2)
        break label29;
      this.mHandler.sendEmptyMessage(54);
    }
    while (true)
    {
      this.mCurrentDisplayRotation = paramInt;
      return;
      label29: if (this.mCurrentDisplayRotation != 2)
        continue;
      this.mHandler.sendEmptyMessage(55);
    }
  }

  private boolean setKeyguardOccludedLw(boolean paramBoolean)
  {
    return setKeyguardOccludedLw(paramBoolean, 0);
  }

  private boolean setKeyguardOccludedLw(boolean paramBoolean, int paramInt)
  {
    WindowManagerPolicy.WindowState localWindowState = this.mStatusBar;
    boolean bool1 = this.mKeyguardOccluded;
    boolean bool2 = this.mKeyguardDelegate.isShowing();
    WindowManager.LayoutParams localLayoutParams;
    if ((bool1) && (!paramBoolean) && (bool2))
    {
      this.mKeyguardOccluded = false;
      if (DEBUG_KEYGUARD)
        Slog.v("WindowManager", "Keyguard setOccluded false  Callers=" + Debug.getCallers(2));
      this.mKeyguardDelegate.setOccluded(false, paramInt);
      localLayoutParams = localWindowState.getAttrs();
      localLayoutParams.privateFlags |= 1024;
      localLayoutParams = localWindowState.getAttrs();
      localLayoutParams.flags &= -9;
      localWindowState.getAttrs().screenOrientation = this.mKeyguardDelegate.getScreenOrientation(false);
      return true;
    }
    if ((!bool1) && (paramBoolean) && (bool2))
    {
      this.mKeyguardOccluded = true;
      if (DEBUG_KEYGUARD)
        Slog.v("WindowManager", "Keyguard setOccluded true Callers=" + Debug.getCallers(2));
      this.mKeyguardDelegate.setOccluded(true, paramInt);
      localLayoutParams = localWindowState.getAttrs();
      localLayoutParams.privateFlags &= -1025;
      localLayoutParams = localWindowState.getAttrs();
      localLayoutParams.flags &= -1048577;
      localLayoutParams = localWindowState.getAttrs();
      localLayoutParams.flags |= 8;
      localWindowState.getAttrs().screenOrientation = this.mKeyguardDelegate.getScreenOrientation(true);
      return true;
    }
    if ((bool1) && (!paramBoolean) && (!bool2))
    {
      this.mKeyguardOccluded = false;
      if (DEBUG_KEYGUARD)
        Slog.v("WindowManager", "Keyguard setOccluded false Callers=" + Debug.getCallers(2));
      this.mKeyguardDelegate.setOccluded(false, paramInt);
    }
    return false;
  }

  private boolean shouldDispatchInputWhenNonInteractive()
  {
    int j = 1;
    int i;
    if ((this.mDisplay == null) || (this.mDisplay.getState() == 1))
      i = 0;
    while (true)
    {
      return i;
      i = j;
      if (isKeyguardShowingAndNotOccluded())
        continue;
      IDreamManager localIDreamManager = getDreamManager();
      if (localIDreamManager != null);
      try
      {
        boolean bool = localIDreamManager.isDreaming();
        i = j;
        if (bool)
          continue;
        return false;
      }
      catch (RemoteException localRemoteException)
      {
        while (true)
          Slog.e("WindowManager", "RemoteException when checking if dreaming", localRemoteException);
      }
    }
  }

  private boolean shouldEnableWakeGestureLp()
  {
    return (this.mWakeGestureEnabledSetting) && (!this.mAwake) && ((!this.mLidControlsSleep) || (this.mLidState != 0)) && (this.mWakeGestureListener.isSupported());
  }

  private boolean shouldUseOutsets(WindowManager.LayoutParams paramLayoutParams, int paramInt)
  {
    return (paramLayoutParams.type == 2013) || ((0x2000400 & paramInt) != 0);
  }

  private void showRecentApps(boolean paramBoolean)
  {
    if (this.mSPWM.showRecentApps(paramBoolean));
    while (true)
    {
      return;
      this.mPreloadedRecentApps = false;
      try
      {
        IStatusBarService localIStatusBarService = getStatusBarService();
        if (localIStatusBarService == null)
          continue;
        localIStatusBarService.showRecentApps(paramBoolean);
        return;
      }
      catch (RemoteException localRemoteException)
      {
        Slog.e("WindowManager", "RemoteException when showing recent apps", localRemoteException);
        this.mStatusBarService = null;
      }
    }
  }

  private void sleepPress(long paramLong)
  {
    if (this.mShortPressOnSleepBehavior == 1)
      launchHomeFromHotKey(false, true);
  }

  private void sleepRelease(long paramLong)
  {
    switch (this.mShortPressOnSleepBehavior)
    {
    default:
      return;
    case 0:
    case 1:
    }
    Slog.i("WindowManager", "sleepRelease() calling goToSleep(GO_TO_SLEEP_REASON_SLEEP_BUTTON)");
    this.mPowerManager.goToSleep(paramLong, 6, 0);
  }

  private void startActivityAsUser(Intent paramIntent, UserHandle paramUserHandle)
  {
    if (isUserSetupComplete());
    try
    {
      this.mContext.startActivityAsUser(paramIntent, paramUserHandle);
      return;
      Slog.i("WindowManager", "Not starting activity because user setup is in progress: " + paramIntent);
      return;
    }
    catch (ActivityNotFoundException paramIntent)
    {
    }
  }

  private void takeScreenshot()
  {
    synchronized (this.mScreenshotLock)
    {
      if (this.mScreenshotConnection != null)
        return;
      Object localObject3 = new ComponentName("com.android.systemui", "com.android.systemui.screenshot.TakeScreenshotService");
      Intent localIntent = new Intent();
      localIntent.setComponent((ComponentName)localObject3);
      localObject3 = new ServiceConnection()
      {
        public void onServiceConnected(ComponentName arg1, IBinder paramIBinder)
        {
          Message localMessage;
          synchronized (PhoneWindowManager.this.mScreenshotLock)
          {
            if (PhoneWindowManager.this.mScreenshotConnection != this)
              return;
            paramIBinder = new Messenger(paramIBinder);
            localMessage = Message.obtain(null, 1);
            localMessage.replyTo = new Messenger(new Handler(PhoneWindowManager.this.mHandler.getLooper(), this)
            {
              public void handleMessage(Message arg1)
              {
                synchronized (PhoneWindowManager.this.mScreenshotLock)
                {
                  if (PhoneWindowManager.this.mScreenshotConnection == this.val$myConn)
                  {
                    PhoneWindowManager.this.mContext.unbindService(PhoneWindowManager.this.mScreenshotConnection);
                    PhoneWindowManager.this.mScreenshotConnection = null;
                    PhoneWindowManager.this.mHandler.removeCallbacks(PhoneWindowManager.this.mScreenshotTimeout);
                  }
                  return;
                }
              }
            });
            localMessage.arg2 = 0;
            localMessage.arg1 = 0;
            if ((PhoneWindowManager.this.mStatusBar != null) && (PhoneWindowManager.this.mStatusBar.isVisibleLw()))
              localMessage.arg1 = 1;
            if ((PhoneWindowManager.this.mNavigationBar != null) && (PhoneWindowManager.this.mNavigationBar.isVisibleLw()))
              localMessage.arg2 = 1;
          }
          try
          {
            paramIBinder.send(localMessage);
            label144: monitorexit;
            return;
            paramIBinder = finally;
            monitorexit;
            throw paramIBinder;
          }
          catch (RemoteException paramIBinder)
          {
            break label144;
          }
        }

        public void onServiceDisconnected(ComponentName paramComponentName)
        {
        }
      };
      if (this.mContext.bindServiceAsUser(localIntent, (ServiceConnection)localObject3, 1, UserHandle.CURRENT))
      {
        this.mScreenshotConnection = ((ServiceConnection)localObject3);
        this.mHandler.postDelayed(this.mScreenshotTimeout, 10000L);
      }
      return;
    }
  }

  private void toggleRecentApps()
  {
    this.mPreloadedRecentApps = false;
    try
    {
      IStatusBarService localIStatusBarService = getStatusBarService();
      if (localIStatusBarService != null)
        localIStatusBarService.toggleRecentApps();
      this.mMultiPhoneWindowManager.toggleRecentApps();
      return;
    }
    catch (RemoteException localRemoteException)
    {
      while (true)
      {
        Slog.e("WindowManager", "RemoteException when toggling recent apps", localRemoteException);
        this.mStatusBarService = null;
      }
    }
  }

  private void updateDisplayRotationInfos(Display paramDisplay, int paramInt1, int paramInt2)
  {
    if (this.mContext == null)
      return;
    paramDisplay = (DisplayWindowPolicy)this.mDisplayWindowPolicy.get(paramDisplay.getDisplayId());
    Resources localResources = this.mContext.getResources();
    if (paramInt1 > paramInt2)
    {
      paramDisplay.mLandscapeRotation = 0;
      paramDisplay.mSeascapeRotation = 2;
      if (localResources.getBoolean(17956926))
      {
        paramDisplay.mPortraitRotation = 1;
        paramDisplay.mUpsideDownRotation = 3;
        return;
      }
      paramDisplay.mPortraitRotation = 3;
      paramDisplay.mUpsideDownRotation = 1;
      return;
    }
    paramDisplay.mPortraitRotation = 0;
    paramDisplay.mUpsideDownRotation = 2;
    if (localResources.getBoolean(17956926))
    {
      paramDisplay.mLandscapeRotation = 3;
      paramDisplay.mSeascapeRotation = 1;
      return;
    }
    paramDisplay.mLandscapeRotation = 1;
    paramDisplay.mSeascapeRotation = 3;
  }

  private void updateDreamingSleepToken(boolean paramBoolean)
  {
    if (paramBoolean)
      if (this.mDreamingSleepToken == null)
        this.mDreamingSleepToken = this.mActivityManagerInternal.acquireSleepToken("Dream");
    do
      return;
    while (this.mDreamingSleepToken == null);
    this.mDreamingSleepToken.release();
    this.mDreamingSleepToken = null;
  }

  private int updateLightStatusBarLw(int paramInt)
  {
    return updateLightStatusBarLw(paramInt, 0);
  }

  private int updateLightStatusBarLw(int paramInt1, int paramInt2)
  {
    Object localObject = this.mStatusBar;
    WindowManagerPolicy.WindowState localWindowState2 = this.mTopFullscreenOpaqueWindowState;
    WindowManagerPolicy.WindowState localWindowState1 = this.mTopFullscreenOpaqueOrDimmingWindowState;
    if ((isStatusBarKeyguard()) && (!this.mHideLockScreen))
    {
      paramInt2 = paramInt1;
      if (localObject != null)
      {
        if (localObject != localWindowState2)
          break label130;
        paramInt2 = paramInt1 & 0xFFFFDFFF | PolicyControl.getSystemUiVisibility((WindowManagerPolicy.WindowState)localObject, null) & 0x2000;
      }
    }
    label130: 
    do
    {
      do
      {
        return paramInt2;
        if ((this.mFocusedWindow != null) && (this.mFocusedWindow != localWindowState1) && (checkTopFullscreenOpaqueWindowState(this.mFocusedWindow, this.mFocusedWindow.getAttrs())) && ((this.mFocusedWindow.getAttrs().flags & 0x400) == 0))
        {
          localObject = this.mFocusedWindow;
          break;
        }
        localObject = localWindowState1;
        break;
        paramInt2 = paramInt1;
      }
      while (localObject == null);
      paramInt2 = paramInt1;
    }
    while (!((WindowManagerPolicy.WindowState)localObject).isDimming());
    return paramInt1 & 0xFFFFDFFF;
  }

  private void updateLockScreenTimeout()
  {
    while (true)
    {
      synchronized (this.mScreenLockTimeout)
      {
        if ((this.mAllowLockscreenWhenOn) && (this.mAwake) && (this.mKeyguardDelegate != null) && (this.mKeyguardDelegate.isSecure()))
        {
          bool = true;
          if (this.mLockScreenTimerActive == bool)
            continue;
          if (!bool)
            continue;
          this.mHandler.postDelayed(this.mScreenLockTimeout, this.mLockScreenTimeout);
          this.mLockScreenTimerActive = bool;
          return;
          this.mHandler.removeCallbacks(this.mScreenLockTimeout);
        }
      }
      boolean bool = false;
    }
  }

  private void updateScreenOffSleepToken(boolean paramBoolean)
  {
    updateScreenOffSleepToken(paramBoolean, 0);
  }

  private void updateScreenOffSleepToken(boolean paramBoolean, int paramInt)
  {
    if (paramBoolean)
    {
      if (((paramInt == 0) || (paramInt == 2)) && (this.mScreenOffSleepToken == null))
        this.mScreenOffSleepToken = this.mActivityManagerInternal.acquireSleepToken("MainScreenOff");
      if (((paramInt == 1) || (paramInt == 2)) && (this.mSubScreenOffSleepToken == null))
        this.mSubScreenOffSleepToken = this.mActivityManagerInternal.acquireSleepToken("SubScreenOff");
    }
    do
    {
      return;
      if (((paramInt != 0) && (paramInt != 2)) || (this.mScreenOffSleepToken == null))
        continue;
      this.mScreenOffSleepToken.release();
      this.mScreenOffSleepToken = null;
    }
    while (((paramInt != 1) && (paramInt != 2)) || (this.mSubScreenOffSleepToken == null));
    this.mSubScreenOffSleepToken.release();
    this.mSubScreenOffSleepToken = null;
  }

  private int updateSystemBarsLw(WindowManagerPolicy.WindowState paramWindowState, int paramInt1, int paramInt2)
  {
    return updateSystemBarsLw(paramWindowState, paramInt1, paramInt2, 0);
  }

  private int updateSystemBarsLw(WindowManagerPolicy.WindowState paramWindowState, int paramInt1, int paramInt2, int paramInt3)
  {
    WindowManagerPolicy.WindowState localWindowState2 = this.mStatusBar;
    WindowManagerPolicy.WindowState localWindowState5 = this.mNavigationBar;
    WindowManagerPolicy.WindowState localWindowState4 = this.mTopFullscreenOpaqueWindowState;
    WindowManagerPolicy.WindowState localWindowState1;
    int i;
    int j;
    int k;
    label197: int n;
    label286: label307: int m;
    label316: label325: label353: boolean bool1;
    label371: boolean bool2;
    if ((isStatusBarKeyguard()) && (!this.mHideLockScreen))
    {
      localWindowState1 = localWindowState2;
      WindowManagerPolicy.WindowState localWindowState3 = localWindowState1;
      if (isStatusBarSViewCover())
      {
        localWindowState3 = localWindowState1;
        if (this.mHideSViewCover == 0)
        {
          localWindowState3 = localWindowState1;
          if (this.mHideSViewCoverWindowState == null)
            localWindowState3 = localWindowState2;
        }
      }
      i = paramInt2;
      if (localWindowState4 != null)
      {
        i = paramInt2;
        if (localWindowState4.getMultiWindowStyleLw().getType() == 1)
          i = paramInt2 | 0x4;
      }
      paramInt2 = i;
      if (this.mForceHideStatusBarForCocktail)
        paramInt2 = i | 0x4;
      i = paramInt2;
      if (this.mPersonaManager != null)
      {
        i = paramInt2;
        if (this.mPersonaManager.isKnoxKeyguardShown(this.mPersonaManager.getFocusedUser()))
          i = paramInt2 | 0x4;
      }
      paramInt2 = this.mStatusBarController.applyTranslucentFlagLw(localWindowState3, i, paramInt1);
      j = this.mNavigationBarController.applyTranslucentFlagLw(localWindowState3, paramInt2, paramInt1);
      if (paramWindowState.getAttrs().type != 2000)
        break label691;
      k = 1;
      i = j;
      if (k != 0)
      {
        i = j;
        if (!isStatusBarKeyguard())
        {
          paramInt2 = 14342;
          if (this.mHideLockScreen)
            paramInt2 = 0x3806 | 0xC0000000;
          i = (paramInt2 ^ 0xFFFFFFFF) & j | paramInt1 & paramInt2;
        }
      }
      paramInt2 = i;
      if (!areTranslucentBarsAllowed())
      {
        paramInt2 = i;
        if (localWindowState3 != localWindowState2)
          paramInt2 = i & 0x3FFF7FFF;
      }
      if ((paramInt2 & 0x1000) == 0)
        break label697;
      i = 1;
      if ((localWindowState4 == null) || ((PolicyControl.getWindowFlags(localWindowState4, null) & 0x400) == 0))
        break label703;
      n = 1;
      if ((paramInt2 & 0x4) == 0)
        break label709;
      m = 1;
      if ((paramInt2 & 0x2) == 0)
        break label715;
      j = 1;
      if ((localWindowState2 == null) || ((n == 0) && ((m == 0) || (i == 0)) && (k == 0)))
        break label721;
      k = 1;
      if ((k == 0) && (!this.mMultiPhoneWindowManager.isStatusBarTransient()))
        break label727;
      bool1 = true;
      if ((localWindowState5 == null) || (j == 0) || (i == 0))
        break label733;
      bool2 = true;
      label389: long l = SystemClock.uptimeMillis();
      if ((this.mPendingPanicGestureUptime == 0L) || (l - this.mPendingPanicGestureUptime > 30000L))
        break label739;
      i = 1;
      label420: if ((i != 0) && (j != 0) && (!isStatusBarKeyguard()) && (this.mKeyguardDrawComplete))
      {
        this.mPendingPanicGestureUptime = 0L;
        this.mStatusBarController.showTransient();
        this.mNavigationBarController.showTransient();
      }
      if ((!this.mStatusBarController.isTransientShowRequested(paramInt3)) || (bool1) || (m == 0))
        break label745;
      i = 1;
      label488: if ((!this.mNavigationBarController.isTransientShowRequested(paramInt3)) || (bool2))
        break label751;
      k = 1;
      label508: if (i == 0)
      {
        i = paramInt2;
        if (k == 0);
      }
      else
      {
        clearClearableFlagsLw();
        i = paramInt2 & 0xFFFFFFF8;
      }
      if ((i & 0x800) == 0)
        break label757;
      paramInt2 = 1;
      label542: if ((i & 0x1000) == 0)
        break label762;
      k = 1;
      label554: if ((paramInt2 == 0) && (k == 0))
        break label768;
    }
    label768: for (paramInt2 = 1; ; paramInt2 = 0)
    {
      k = i;
      if (j != 0)
      {
        k = i;
        if (paramInt2 == 0)
        {
          k = i;
          if (windowTypeToLayerLw(paramWindowState.getBaseType()) > windowTypeToLayerLw(2022))
            k = i & 0xFFFFFFFD;
        }
      }
      paramInt2 = this.mStatusBarController.updateVisibilityLw(bool1, paramInt1, k, paramInt3);
      bool1 = isImmersiveMode(paramInt1);
      boolean bool3 = isImmersiveMode(paramInt2);
      if (bool1 != bool3)
      {
        paramWindowState = paramWindowState.getOwningPackage();
        this.mImmersiveModeConfirmation.immersiveModeChanged(paramWindowState, bool3, isUserSetupComplete());
      }
      return this.mNavigationBarController.updateVisibilityLw(bool2, paramInt1, paramInt2, paramInt3);
      localWindowState1 = localWindowState4;
      break;
      label691: k = 0;
      break label197;
      label697: i = 0;
      break label286;
      label703: n = 0;
      break label307;
      label709: m = 0;
      break label316;
      label715: j = 0;
      break label325;
      label721: k = 0;
      break label353;
      label727: bool1 = false;
      break label371;
      label733: bool2 = false;
      break label389;
      label739: i = 0;
      break label420;
      label745: i = 0;
      break label488;
      label751: k = 0;
      break label508;
      label757: paramInt2 = 0;
      break label542;
      label762: k = 0;
      break label554;
    }
  }

  private int updateSystemUiVisibilityLw()
  {
    return updateSystemUiVisibilityLw(0);
  }

  private int updateSystemUiVisibilityLw(int paramInt)
  {
    int i = this.mResettingSystemUiFlags;
    int j = this.mForceClearedSystemUiFlags;
    int k = this.mLastSystemUiFlags;
    boolean bool1 = this.mLastFocusNeedsMenu;
    WindowManagerPolicy.WindowState localWindowState2 = this.mTopFullscreenOpaqueWindowState;
    if (this.mFocusedWindow != null);
    WindowManagerPolicy.WindowState localWindowState3;
    for (WindowManagerPolicy.WindowState localWindowState1 = this.mFocusedWindow; ; localWindowState1 = localWindowState2)
    {
      localWindowState3 = localWindowState1;
      if (localWindowState1 != null)
      {
        localWindowState3 = localWindowState1;
        if (localWindowState1.getMultiWindowStyleLw().isCascade())
          localWindowState3 = localWindowState2;
      }
      if (localWindowState3 != null)
        break;
      return 0;
    }
    if (((localWindowState3.getAttrs().privateFlags & 0x400) != 0) && (this.mHideLockScreen == true))
      return 0;
    if (((localWindowState3.getAttrs().samsungFlags & 0x10000000) != 0) && (this.mHideSViewCover != 0))
      return 0;
    j = PolicyControl.getSystemUiVisibility(localWindowState3, null) & (i ^ 0xFFFFFFFF) & (j ^ 0xFFFFFFFF);
    i = j;
    if (this.mForcingShowNavBar)
    {
      i = j;
      if (localWindowState3.getSurfaceLayer() < this.mForcingShowNavBarLayer)
        i = j & (PolicyControl.adjustClearableFlags(localWindowState3, 7) ^ 0xFFFFFFFF);
    }
    i = updateSystemBarsLw(localWindowState3, k, updateLightStatusBarLw(i, paramInt), paramInt);
    j = i ^ k;
    boolean bool2 = localWindowState3.getNeedsMenuLw(localWindowState2);
    if ((j == 0) && (bool1 == bool2) && (this.mFocusedApp == localWindowState3.getAppToken()))
      return 0;
    this.mFocusedApp = localWindowState3.getAppToken();
    this.mLastSystemUiFlags = i;
    this.mLastFocusNeedsMenu = bool2;
    this.mHandler.post(new Runnable(paramInt, i, localWindowState3, bool2)
    {
      public void run()
      {
        try
        {
          IStatusBarService localIStatusBarService = PhoneWindowManager.this.getStatusBarService();
          if (localIStatusBarService != null)
          {
            if (this.val$displayId == 0)
            {
              localIStatusBarService.setSystemUiVisibility(this.val$visibility, -1, this.val$win.toString());
              localIStatusBarService.topAppWindowChanged(this.val$needsMenu);
              return;
            }
            localIStatusBarService.setSystemUiVisibilityToDisplay(this.val$visibility, -1, this.val$win.toString(), this.val$displayId);
            localIStatusBarService.topAppWindowChangedToDisplay(this.val$needsMenu, this.val$displayId);
            return;
          }
        }
        catch (RemoteException localRemoteException)
        {
          PhoneWindowManager.this.mStatusBarService = null;
        }
      }
    });
    return j;
  }

  private void updateWakeGestureListenerLp()
  {
    if (shouldEnableWakeGestureLp())
    {
      this.mWakeGestureListener.requestWakeUpTrigger();
      return;
    }
    this.mWakeGestureListener.cancelWakeUpTrigger();
  }

  private boolean wakeUp(long paramLong, boolean paramBoolean, int paramInt)
  {
    boolean bool = isTheaterModeEnabled();
    if ((!paramBoolean) && (bool))
      return false;
    if (bool)
      Settings.Global.putInt(this.mContext.getContentResolver(), "theater_mode_on", 0);
    this.mSPWM.performCPUBoost();
    this.mPowerManager.wakeUp(paramLong, paramInt);
    return true;
  }

  private void wakeUpFromPowerKey(long paramLong)
  {
    wakeUp(paramLong, this.mAllowTheaterModeWakeFromPowerKey, 1);
  }

  // ERROR //
  public View addBackWindow(int paramInt)
  {
    // Byte code:
    //   0: invokestatic 3117	android/hardware/display/DisplayManagerGlobal:getInstance	()Landroid/hardware/display/DisplayManagerGlobal;
    //   3: iload_1
    //   4: invokevirtual 3121	android/hardware/display/DisplayManagerGlobal:getRealDisplay	(I)Landroid/view/Display;
    //   7: astore 11
    //   9: aconst_null
    //   10: astore 5
    //   12: aconst_null
    //   13: astore 6
    //   15: aconst_null
    //   16: astore 7
    //   18: aconst_null
    //   19: astore 8
    //   21: aconst_null
    //   22: astore 9
    //   24: aconst_null
    //   25: astore 4
    //   27: aload 9
    //   29: astore_2
    //   30: aload 5
    //   32: astore_3
    //   33: new 1547	android/view/WindowManager$LayoutParams
    //   36: dup
    //   37: iconst_m1
    //   38: iconst_m1
    //   39: sipush 2097
    //   42: sipush 1800
    //   45: iconst_m1
    //   46: invokespecial 3124	android/view/WindowManager$LayoutParams:<init>	(IIIII)V
    //   49: astore 10
    //   51: aload 9
    //   53: astore_2
    //   54: aload 5
    //   56: astore_3
    //   57: aload 10
    //   59: aconst_null
    //   60: putfield 3128	android/view/WindowManager$LayoutParams:token	Landroid/os/IBinder;
    //   63: aload 9
    //   65: astore_2
    //   66: aload 5
    //   68: astore_3
    //   69: aload 10
    //   71: aload 10
    //   73: getfield 1716	android/view/WindowManager$LayoutParams:privateFlags	I
    //   76: iconst_1
    //   77: ior
    //   78: putfield 1716	android/view/WindowManager$LayoutParams:privateFlags	I
    //   81: aload 9
    //   83: astore_2
    //   84: aload 5
    //   86: astore_3
    //   87: aload 10
    //   89: aload 10
    //   91: getfield 1716	android/view/WindowManager$LayoutParams:privateFlags	I
    //   94: bipush 16
    //   96: ior
    //   97: putfield 1716	android/view/WindowManager$LayoutParams:privateFlags	I
    //   100: aload 9
    //   102: astore_2
    //   103: aload 5
    //   105: astore_3
    //   106: aload 10
    //   108: aload 10
    //   110: getfield 2770	android/view/WindowManager$LayoutParams:samsungFlags	I
    //   113: sipush 8192
    //   116: ior
    //   117: putfield 2770	android/view/WindowManager$LayoutParams:samsungFlags	I
    //   120: aload 9
    //   122: astore_2
    //   123: aload 5
    //   125: astore_3
    //   126: aload 10
    //   128: ldc_w 3130
    //   131: invokevirtual 1725	android/view/WindowManager$LayoutParams:setTitle	(Ljava/lang/CharSequence;)V
    //   134: aload 9
    //   136: astore_2
    //   137: aload 5
    //   139: astore_3
    //   140: new 3132	android/widget/ImageView
    //   143: dup
    //   144: aload_0
    //   145: getfield 1619	com/android/server/policy/PhoneWindowManager:mContext	Landroid/content/Context;
    //   148: invokespecial 3133	android/widget/ImageView:<init>	(Landroid/content/Context;)V
    //   151: astore 5
    //   153: aload 6
    //   155: astore_2
    //   156: aload 7
    //   158: astore_3
    //   159: aload 5
    //   161: ldc_w 3134
    //   164: invokevirtual 3137	android/widget/ImageView:setImageResource	(I)V
    //   167: aload 6
    //   169: astore_2
    //   170: aload 7
    //   172: astore_3
    //   173: aload 5
    //   175: getstatic 3143	android/widget/ImageView$ScaleType:CENTER_CROP	Landroid/widget/ImageView$ScaleType;
    //   178: invokevirtual 3147	android/widget/ImageView:setScaleType	(Landroid/widget/ImageView$ScaleType;)V
    //   181: aload 6
    //   183: astore_2
    //   184: aload 7
    //   186: astore_3
    //   187: aload_0
    //   188: getfield 1619	com/android/server/policy/PhoneWindowManager:mContext	Landroid/content/Context;
    //   191: ldc_w 1621
    //   194: invokevirtual 1627	android/content/Context:getSystemService	(Ljava/lang/String;)Ljava/lang/Object;
    //   197: checkcast 3149	android/view/WindowManagerImpl
    //   200: astore 6
    //   202: aload 6
    //   204: astore 4
    //   206: iload_1
    //   207: ifeq +18 -> 225
    //   210: aload 6
    //   212: astore_2
    //   213: aload 6
    //   215: astore_3
    //   216: aload 6
    //   218: aload 11
    //   220: invokevirtual 3153	android/view/WindowManagerImpl:createPresentationWindowManager	(Landroid/view/Display;)Landroid/view/WindowManagerImpl;
    //   223: astore 4
    //   225: aload 4
    //   227: astore_2
    //   228: aload 4
    //   230: astore_3
    //   231: aload 4
    //   233: aload 5
    //   235: aload 10
    //   237: invokevirtual 3154	android/view/WindowManagerImpl:addView	(Landroid/view/View;Landroid/view/ViewGroup$LayoutParams;)V
    //   240: aload 4
    //   242: astore_2
    //   243: aload 4
    //   245: astore_3
    //   246: aload 5
    //   248: invokevirtual 3158	android/widget/ImageView:getParent	()Landroid/view/ViewParent;
    //   251: astore 6
    //   253: aload 6
    //   255: ifnull +43 -> 298
    //   258: aload 5
    //   260: astore_2
    //   261: aload 5
    //   263: ifnull +33 -> 296
    //   266: aload 5
    //   268: invokevirtual 3158	android/widget/ImageView:getParent	()Landroid/view/ViewParent;
    //   271: ifnonnull +25 -> 296
    //   274: aload 4
    //   276: ifnull +20 -> 296
    //   279: ldc_w 299
    //   282: ldc_w 3160
    //   285: invokestatic 1936	android/util/Log:w	(Ljava/lang/String;Ljava/lang/String;)I
    //   288: pop
    //   289: aload 4
    //   291: aload 5
    //   293: invokevirtual 3163	android/view/WindowManagerImpl:removeViewImmediate	(Landroid/view/View;)V
    //   296: aload_2
    //   297: areturn
    //   298: aconst_null
    //   299: astore_2
    //   300: goto -39 -> 261
    //   303: astore 6
    //   305: aload 8
    //   307: astore 5
    //   309: aload 4
    //   311: astore_2
    //   312: aload 5
    //   314: astore_3
    //   315: ldc_w 299
    //   318: ldc_w 3165
    //   321: aload 6
    //   323: invokestatic 2317	android/util/Log:w	(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I
    //   326: pop
    //   327: aload 4
    //   329: ifnull +33 -> 362
    //   332: aload 4
    //   334: invokevirtual 3158	android/widget/ImageView:getParent	()Landroid/view/ViewParent;
    //   337: ifnonnull +25 -> 362
    //   340: aload 5
    //   342: ifnull +20 -> 362
    //   345: ldc_w 299
    //   348: ldc_w 3160
    //   351: invokestatic 1936	android/util/Log:w	(Ljava/lang/String;Ljava/lang/String;)I
    //   354: pop
    //   355: aload 5
    //   357: aload 4
    //   359: invokevirtual 3163	android/view/WindowManagerImpl:removeViewImmediate	(Landroid/view/View;)V
    //   362: aconst_null
    //   363: areturn
    //   364: astore 6
    //   366: aload_3
    //   367: astore 5
    //   369: aload_2
    //   370: astore 4
    //   372: aload 6
    //   374: astore_3
    //   375: aload 4
    //   377: ifnull +33 -> 410
    //   380: aload 4
    //   382: invokevirtual 3158	android/widget/ImageView:getParent	()Landroid/view/ViewParent;
    //   385: ifnonnull +25 -> 410
    //   388: aload 5
    //   390: ifnull +20 -> 410
    //   393: ldc_w 299
    //   396: ldc_w 3160
    //   399: invokestatic 1936	android/util/Log:w	(Ljava/lang/String;Ljava/lang/String;)I
    //   402: pop
    //   403: aload 5
    //   405: aload 4
    //   407: invokevirtual 3163	android/view/WindowManagerImpl:removeViewImmediate	(Landroid/view/View;)V
    //   410: aload_3
    //   411: athrow
    //   412: astore_3
    //   413: aload 5
    //   415: astore 4
    //   417: aload_2
    //   418: astore 5
    //   420: goto -45 -> 375
    //   423: astore 6
    //   425: aload 5
    //   427: astore 4
    //   429: aload_3
    //   430: astore 5
    //   432: goto -123 -> 309
    //
    // Exception table:
    //   from	to	target	type
    //   33	51	303	java/lang/Exception
    //   57	63	303	java/lang/Exception
    //   69	81	303	java/lang/Exception
    //   87	100	303	java/lang/Exception
    //   106	120	303	java/lang/Exception
    //   126	134	303	java/lang/Exception
    //   140	153	303	java/lang/Exception
    //   33	51	364	finally
    //   57	63	364	finally
    //   69	81	364	finally
    //   87	100	364	finally
    //   106	120	364	finally
    //   126	134	364	finally
    //   140	153	364	finally
    //   315	327	364	finally
    //   159	167	412	finally
    //   173	181	412	finally
    //   187	202	412	finally
    //   216	225	412	finally
    //   231	240	412	finally
    //   246	253	412	finally
    //   159	167	423	java/lang/Exception
    //   173	181	423	java/lang/Exception
    //   187	202	423	java/lang/Exception
    //   216	225	423	java/lang/Exception
    //   231	240	423	java/lang/Exception
    //   246	253	423	java/lang/Exception
  }

  // ERROR //
  public View addStartingWindow(IBinder paramIBinder, String paramString, int paramInt1, android.content.res.CompatibilityInfo paramCompatibilityInfo, CharSequence paramCharSequence, int paramInt2, int paramInt3, int paramInt4, int paramInt5, MultiWindowStyle paramMultiWindowStyle, android.graphics.Bitmap paramBitmap, int paramInt6, int paramInt7, int paramInt8)
  {
    // Byte code:
    //   0: aload_2
    //   1: ifnonnull +7 -> 8
    //   4: aconst_null
    //   5: astore_1
    //   6: aload_1
    //   7: areturn
    //   8: aload_0
    //   9: getfield 3177	com/android/server/policy/PhoneWindowManager:mWinShowWhenLocked	Landroid/view/WindowManagerPolicy$WindowState;
    //   12: ifnull +5 -> 17
    //   15: aconst_null
    //   16: areturn
    //   17: aconst_null
    //   18: astore 30
    //   20: aconst_null
    //   21: astore 32
    //   23: aconst_null
    //   24: astore 31
    //   26: invokestatic 3117	android/hardware/display/DisplayManagerGlobal:getInstance	()Landroid/hardware/display/DisplayManagerGlobal;
    //   29: iload 13
    //   31: invokevirtual 3121	android/hardware/display/DisplayManagerGlobal:getRealDisplay	(I)Landroid/view/Display;
    //   34: astore 34
    //   36: aconst_null
    //   37: astore 29
    //   39: aconst_null
    //   40: astore 28
    //   42: aconst_null
    //   43: astore 27
    //   45: aload 27
    //   47: astore 21
    //   49: aload 31
    //   51: astore 23
    //   53: aload 29
    //   55: astore 20
    //   57: aload 30
    //   59: astore 24
    //   61: aload 28
    //   63: astore 19
    //   65: aload 32
    //   67: astore 22
    //   69: aload_0
    //   70: getfield 1619	com/android/server/policy/PhoneWindowManager:mContext	Landroid/content/Context;
    //   73: astore 26
    //   75: aload 27
    //   77: astore 21
    //   79: aload 31
    //   81: astore 23
    //   83: aload 29
    //   85: astore 20
    //   87: aload 30
    //   89: astore 24
    //   91: aload 28
    //   93: astore 19
    //   95: aload 32
    //   97: astore 22
    //   99: aload 26
    //   101: invokevirtual 3180	android/content/Context:getThemeResId	()I
    //   104: istore 15
    //   106: iload_3
    //   107: iload 15
    //   109: if_icmpne +12 -> 121
    //   112: aload 26
    //   114: astore 25
    //   116: iload 6
    //   118: ifeq +170 -> 288
    //   121: iconst_0
    //   122: istore 15
    //   124: aload 27
    //   126: astore 21
    //   128: aload 31
    //   130: astore 23
    //   132: aload 29
    //   134: astore 20
    //   136: aload 30
    //   138: astore 24
    //   140: aload 28
    //   142: astore 19
    //   144: aload 32
    //   146: astore 22
    //   148: aload 26
    //   150: astore 25
    //   152: iload 14
    //   154: invokestatic 3183	android/os/PersonaManager:isKnoxId	(I)Z
    //   157: ifne +1956 -> 2113
    //   160: aload 27
    //   162: astore 21
    //   164: aload 31
    //   166: astore 23
    //   168: aload 29
    //   170: astore 20
    //   172: aload 30
    //   174: astore 24
    //   176: aload 28
    //   178: astore 19
    //   180: aload 32
    //   182: astore 22
    //   184: aload 26
    //   186: astore 25
    //   188: aload 26
    //   190: invokevirtual 3187	android/content/Context:getPackageManager	()Landroid/content/pm/PackageManager;
    //   193: aload_2
    //   194: invokevirtual 3193	android/content/pm/PackageManager:isThemeChanged	(Ljava/lang/String;)Z
    //   197: ifeq +6 -> 203
    //   200: goto +1913 -> 2113
    //   203: aload 27
    //   205: astore 21
    //   207: aload 31
    //   209: astore 23
    //   211: aload 29
    //   213: astore 20
    //   215: aload 30
    //   217: astore 24
    //   219: aload 28
    //   221: astore 19
    //   223: aload 32
    //   225: astore 22
    //   227: aload 26
    //   229: astore 25
    //   231: aload 26
    //   233: aload_2
    //   234: iload 15
    //   236: new 1592	android/os/UserHandle
    //   239: dup
    //   240: iload 14
    //   242: invokespecial 3195	android/os/UserHandle:<init>	(I)V
    //   245: invokevirtual 3199	android/content/Context:createPackageContextAsUser	(Ljava/lang/String;ILandroid/os/UserHandle;)Landroid/content/Context;
    //   248: astore 26
    //   250: aload 27
    //   252: astore 21
    //   254: aload 31
    //   256: astore 23
    //   258: aload 29
    //   260: astore 20
    //   262: aload 30
    //   264: astore 24
    //   266: aload 28
    //   268: astore 19
    //   270: aload 32
    //   272: astore 22
    //   274: aload 26
    //   276: astore 25
    //   278: aload 26
    //   280: iload_3
    //   281: invokevirtual 3202	android/content/Context:setTheme	(I)V
    //   284: aload 26
    //   286: astore 25
    //   288: aload 27
    //   290: astore 21
    //   292: aload 31
    //   294: astore 23
    //   296: aload 29
    //   298: astore 20
    //   300: aload 30
    //   302: astore 24
    //   304: aload 28
    //   306: astore 19
    //   308: aload 32
    //   310: astore 22
    //   312: new 2848	com/android/internal/policy/PhoneWindow
    //   315: dup
    //   316: aload 25
    //   318: invokespecial 3203	com/android/internal/policy/PhoneWindow:<init>	(Landroid/content/Context;)V
    //   321: astore 26
    //   323: aload 27
    //   325: astore 21
    //   327: aload 31
    //   329: astore 23
    //   331: aload 29
    //   333: astore 20
    //   335: aload 30
    //   337: astore 24
    //   339: aload 28
    //   341: astore 19
    //   343: aload 32
    //   345: astore 22
    //   347: aload 26
    //   349: iconst_1
    //   350: invokevirtual 3206	com/android/internal/policy/PhoneWindow:setIsStartingWindow	(Z)V
    //   353: aload 27
    //   355: astore 21
    //   357: aload 31
    //   359: astore 23
    //   361: aload 29
    //   363: astore 20
    //   365: aload 30
    //   367: astore 24
    //   369: aload 28
    //   371: astore 19
    //   373: aload 32
    //   375: astore 22
    //   377: aload 26
    //   379: invokevirtual 3210	com/android/internal/policy/PhoneWindow:getWindowStyle	()Landroid/content/res/TypedArray;
    //   382: astore 33
    //   384: aload 27
    //   386: astore 21
    //   388: aload 31
    //   390: astore 23
    //   392: aload 29
    //   394: astore 20
    //   396: aload 30
    //   398: astore 24
    //   400: aload 28
    //   402: astore 19
    //   404: aload 32
    //   406: astore 22
    //   408: aload 33
    //   410: bipush 12
    //   412: iconst_0
    //   413: invokevirtual 3215	android/content/res/TypedArray:getBoolean	(IZ)Z
    //   416: ifne +42 -> 458
    //   419: aload 27
    //   421: astore 21
    //   423: aload 31
    //   425: astore 23
    //   427: aload 29
    //   429: astore 20
    //   431: aload 30
    //   433: astore 24
    //   435: aload 28
    //   437: astore 19
    //   439: aload 32
    //   441: astore 22
    //   443: aload 33
    //   445: bipush 14
    //   447: iconst_0
    //   448: invokevirtual 3215	android/content/res/TypedArray:getBoolean	(IZ)Z
    //   451: istore 18
    //   453: iload 18
    //   455: ifeq +17 -> 472
    //   458: aconst_null
    //   459: astore_1
    //   460: iconst_0
    //   461: ifeq -455 -> 6
    //   464: new 3217	java/lang/NullPointerException
    //   467: dup
    //   468: invokespecial 3218	java/lang/NullPointerException:<init>	()V
    //   471: athrow
    //   472: aload 27
    //   474: astore 21
    //   476: aload 31
    //   478: astore 23
    //   480: aload 29
    //   482: astore 20
    //   484: aload 30
    //   486: astore 24
    //   488: aload 28
    //   490: astore 19
    //   492: aload 32
    //   494: astore 22
    //   496: aload 26
    //   498: aload 25
    //   500: invokevirtual 2422	android/content/Context:getResources	()Landroid/content/res/Resources;
    //   503: iload 6
    //   505: aload 5
    //   507: invokevirtual 3222	android/content/res/Resources:getText	(ILjava/lang/CharSequence;)Ljava/lang/CharSequence;
    //   510: invokevirtual 3223	com/android/internal/policy/PhoneWindow:setTitle	(Ljava/lang/CharSequence;)V
    //   513: aload 27
    //   515: astore 21
    //   517: aload 31
    //   519: astore 23
    //   521: aload 29
    //   523: astore 20
    //   525: aload 30
    //   527: astore 24
    //   529: aload 28
    //   531: astore 19
    //   533: aload 32
    //   535: astore 22
    //   537: aload 26
    //   539: iconst_3
    //   540: invokevirtual 3226	com/android/internal/policy/PhoneWindow:setType	(I)V
    //   543: aload 27
    //   545: astore 21
    //   547: aload 31
    //   549: astore 23
    //   551: aload 29
    //   553: astore 20
    //   555: aload 30
    //   557: astore 24
    //   559: aload 28
    //   561: astore 19
    //   563: aload 32
    //   565: astore 22
    //   567: aload_0
    //   568: getfield 1606	com/android/server/policy/PhoneWindowManager:mWindowManagerFuncs	Landroid/view/WindowManagerPolicy$WindowManagerFuncs;
    //   571: invokeinterface 2809 1 0
    //   576: astore 5
    //   578: aload 27
    //   580: astore 21
    //   582: aload 31
    //   584: astore 23
    //   586: aload 29
    //   588: astore 20
    //   590: aload 30
    //   592: astore 24
    //   594: aload 28
    //   596: astore 19
    //   598: aload 32
    //   600: astore 22
    //   602: aload 5
    //   604: monitorenter
    //   605: iload 9
    //   607: istore_3
    //   608: aload_0
    //   609: getfield 3228	com/android/server/policy/PhoneWindowManager:mKeyguardHidden	Z
    //   612: ifeq +10 -> 622
    //   615: iload 9
    //   617: ldc_w 3229
    //   620: ior
    //   621: istore_3
    //   622: aload 5
    //   624: monitorexit
    //   625: aload 27
    //   627: astore 21
    //   629: aload 31
    //   631: astore 23
    //   633: aload 29
    //   635: astore 20
    //   637: aload 30
    //   639: astore 24
    //   641: aload 28
    //   643: astore 19
    //   645: aload 32
    //   647: astore 22
    //   649: aload 26
    //   651: iload_3
    //   652: bipush 16
    //   654: ior
    //   655: bipush 8
    //   657: ior
    //   658: ldc_w 1551
    //   661: ior
    //   662: iload_3
    //   663: bipush 16
    //   665: ior
    //   666: bipush 8
    //   668: ior
    //   669: ldc_w 1551
    //   672: ior
    //   673: invokevirtual 3231	com/android/internal/policy/PhoneWindow:setFlags	(II)V
    //   676: aload 27
    //   678: astore 21
    //   680: aload 31
    //   682: astore 23
    //   684: aload 29
    //   686: astore 20
    //   688: aload 30
    //   690: astore 24
    //   692: aload 28
    //   694: astore 19
    //   696: aload 32
    //   698: astore 22
    //   700: aload 26
    //   702: iload 7
    //   704: invokevirtual 3234	com/android/internal/policy/PhoneWindow:setDefaultIcon	(I)V
    //   707: aload 27
    //   709: astore 21
    //   711: aload 31
    //   713: astore 23
    //   715: aload 29
    //   717: astore 20
    //   719: aload 30
    //   721: astore 24
    //   723: aload 28
    //   725: astore 19
    //   727: aload 32
    //   729: astore 22
    //   731: aload 26
    //   733: iload 8
    //   735: invokevirtual 3237	com/android/internal/policy/PhoneWindow:setDefaultLogo	(I)V
    //   738: aload 27
    //   740: astore 21
    //   742: aload 31
    //   744: astore 23
    //   746: aload 29
    //   748: astore 20
    //   750: aload 30
    //   752: astore 24
    //   754: aload 28
    //   756: astore 19
    //   758: aload 32
    //   760: astore 22
    //   762: aload 26
    //   764: iconst_m1
    //   765: iconst_m1
    //   766: invokevirtual 3240	com/android/internal/policy/PhoneWindow:setLayout	(II)V
    //   769: aload 27
    //   771: astore 21
    //   773: aload 31
    //   775: astore 23
    //   777: aload 29
    //   779: astore 20
    //   781: aload 30
    //   783: astore 24
    //   785: aload 28
    //   787: astore 19
    //   789: aload 32
    //   791: astore 22
    //   793: aload 26
    //   795: invokevirtual 3243	com/android/internal/policy/PhoneWindow:getAttributes	()Landroid/view/WindowManager$LayoutParams;
    //   798: astore 33
    //   800: aload 27
    //   802: astore 21
    //   804: aload 31
    //   806: astore 23
    //   808: aload 29
    //   810: astore 20
    //   812: aload 30
    //   814: astore 24
    //   816: aload 28
    //   818: astore 19
    //   820: aload 32
    //   822: astore 22
    //   824: aload 33
    //   826: aload_1
    //   827: putfield 3128	android/view/WindowManager$LayoutParams:token	Landroid/os/IBinder;
    //   830: aload 27
    //   832: astore 21
    //   834: aload 31
    //   836: astore 23
    //   838: aload 29
    //   840: astore 20
    //   842: aload 30
    //   844: astore 24
    //   846: aload 28
    //   848: astore 19
    //   850: aload 32
    //   852: astore 22
    //   854: aload 33
    //   856: aload_2
    //   857: putfield 3246	android/view/WindowManager$LayoutParams:packageName	Ljava/lang/String;
    //   860: aload 27
    //   862: astore 21
    //   864: aload 31
    //   866: astore 23
    //   868: aload 29
    //   870: astore 20
    //   872: aload 30
    //   874: astore 24
    //   876: aload 28
    //   878: astore 19
    //   880: aload 32
    //   882: astore 22
    //   884: aload 33
    //   886: aload 26
    //   888: invokevirtual 3210	com/android/internal/policy/PhoneWindow:getWindowStyle	()Landroid/content/res/TypedArray;
    //   891: bipush 8
    //   893: iconst_0
    //   894: invokevirtual 3249	android/content/res/TypedArray:getResourceId	(II)I
    //   897: putfield 3252	android/view/WindowManager$LayoutParams:windowAnimations	I
    //   900: aload 27
    //   902: astore 21
    //   904: aload 31
    //   906: astore 23
    //   908: aload 29
    //   910: astore 20
    //   912: aload 30
    //   914: astore 24
    //   916: aload 28
    //   918: astore 19
    //   920: aload 32
    //   922: astore 22
    //   924: aload 33
    //   926: aload 33
    //   928: getfield 1716	android/view/WindowManager$LayoutParams:privateFlags	I
    //   931: iconst_1
    //   932: ior
    //   933: putfield 1716	android/view/WindowManager$LayoutParams:privateFlags	I
    //   936: aload 27
    //   938: astore 21
    //   940: aload 31
    //   942: astore 23
    //   944: aload 29
    //   946: astore 20
    //   948: aload 30
    //   950: astore 24
    //   952: aload 28
    //   954: astore 19
    //   956: aload 32
    //   958: astore 22
    //   960: aload 33
    //   962: aload 33
    //   964: getfield 1716	android/view/WindowManager$LayoutParams:privateFlags	I
    //   967: bipush 16
    //   969: ior
    //   970: putfield 1716	android/view/WindowManager$LayoutParams:privateFlags	I
    //   973: aload 27
    //   975: astore 21
    //   977: aload 31
    //   979: astore 23
    //   981: aload 29
    //   983: astore 20
    //   985: aload 30
    //   987: astore 24
    //   989: aload 28
    //   991: astore 19
    //   993: aload 32
    //   995: astore 22
    //   997: aload 4
    //   999: invokevirtual 3257	android/content/res/CompatibilityInfo:supportsScreen	()Z
    //   1002: ifne +41 -> 1043
    //   1005: aload 27
    //   1007: astore 21
    //   1009: aload 31
    //   1011: astore 23
    //   1013: aload 29
    //   1015: astore 20
    //   1017: aload 30
    //   1019: astore 24
    //   1021: aload 28
    //   1023: astore 19
    //   1025: aload 32
    //   1027: astore 22
    //   1029: aload 33
    //   1031: aload 33
    //   1033: getfield 1716	android/view/WindowManager$LayoutParams:privateFlags	I
    //   1036: sipush 128
    //   1039: ior
    //   1040: putfield 1716	android/view/WindowManager$LayoutParams:privateFlags	I
    //   1043: aload 27
    //   1045: astore 21
    //   1047: aload 31
    //   1049: astore 23
    //   1051: aload 29
    //   1053: astore 20
    //   1055: aload 30
    //   1057: astore 24
    //   1059: aload 28
    //   1061: astore 19
    //   1063: aload 32
    //   1065: astore 22
    //   1067: aload 33
    //   1069: new 1801	java/lang/StringBuilder
    //   1072: dup
    //   1073: invokespecial 1802	java/lang/StringBuilder:<init>	()V
    //   1076: ldc_w 3259
    //   1079: invokevirtual 1807	java/lang/StringBuilder:append	(Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   1082: aload_2
    //   1083: invokevirtual 1807	java/lang/StringBuilder:append	(Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   1086: invokevirtual 1815	java/lang/StringBuilder:toString	()Ljava/lang/String;
    //   1089: invokevirtual 1725	android/view/WindowManager$LayoutParams:setTitle	(Ljava/lang/CharSequence;)V
    //   1092: aload 27
    //   1094: astore 21
    //   1096: aload 31
    //   1098: astore 23
    //   1100: aload 29
    //   1102: astore 20
    //   1104: aload 30
    //   1106: astore 24
    //   1108: aload 28
    //   1110: astore 19
    //   1112: aload 32
    //   1114: astore 22
    //   1116: aload 10
    //   1118: invokevirtual 3072	com/samsung/android/multiwindow/MultiWindowStyle:isCascade	()Z
    //   1121: istore 18
    //   1123: iload 18
    //   1125: ifeq +122 -> 1247
    //   1128: aconst_null
    //   1129: astore_1
    //   1130: iconst_0
    //   1131: ifeq -1125 -> 6
    //   1134: new 3217	java/lang/NullPointerException
    //   1137: dup
    //   1138: invokespecial 3218	java/lang/NullPointerException:<init>	()V
    //   1141: athrow
    //   1142: astore_2
    //   1143: aload 5
    //   1145: monitorexit
    //   1146: aload 27
    //   1148: astore 21
    //   1150: aload 31
    //   1152: astore 23
    //   1154: aload 29
    //   1156: astore 20
    //   1158: aload 30
    //   1160: astore 24
    //   1162: aload 28
    //   1164: astore 19
    //   1166: aload 32
    //   1168: astore 22
    //   1170: aload_2
    //   1171: athrow
    //   1172: astore_2
    //   1173: aload 21
    //   1175: astore 19
    //   1177: aload 23
    //   1179: astore 22
    //   1181: ldc_w 299
    //   1184: new 1801	java/lang/StringBuilder
    //   1187: dup
    //   1188: invokespecial 1802	java/lang/StringBuilder:<init>	()V
    //   1191: aload_1
    //   1192: invokevirtual 2919	java/lang/StringBuilder:append	(Ljava/lang/Object;)Ljava/lang/StringBuilder;
    //   1195: ldc_w 3261
    //   1198: invokevirtual 1807	java/lang/StringBuilder:append	(Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   1201: aload_2
    //   1202: invokevirtual 3264	android/view/WindowManager$BadTokenException:getMessage	()Ljava/lang/String;
    //   1205: invokevirtual 1807	java/lang/StringBuilder:append	(Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   1208: invokevirtual 1815	java/lang/StringBuilder:toString	()Ljava/lang/String;
    //   1211: invokestatic 1936	android/util/Log:w	(Ljava/lang/String;Ljava/lang/String;)I
    //   1214: pop
    //   1215: aload 21
    //   1217: ifnull +28 -> 1245
    //   1220: aload 21
    //   1222: invokevirtual 3267	android/view/View:getParent	()Landroid/view/ViewParent;
    //   1225: ifnonnull +20 -> 1245
    //   1228: ldc_w 299
    //   1231: ldc_w 3160
    //   1234: invokestatic 1936	android/util/Log:w	(Ljava/lang/String;Ljava/lang/String;)I
    //   1237: pop
    //   1238: aload 23
    //   1240: aload 21
    //   1242: invokevirtual 3163	android/view/WindowManagerImpl:removeViewImmediate	(Landroid/view/View;)V
    //   1245: aconst_null
    //   1246: areturn
    //   1247: aload 27
    //   1249: astore 21
    //   1251: aload 31
    //   1253: astore 23
    //   1255: aload 29
    //   1257: astore 20
    //   1259: aload 30
    //   1261: astore 24
    //   1263: aload 28
    //   1265: astore 19
    //   1267: aload 32
    //   1269: astore 22
    //   1271: aload 25
    //   1273: ldc_w 1621
    //   1276: invokevirtual 1627	android/content/Context:getSystemService	(Ljava/lang/String;)Ljava/lang/Object;
    //   1279: checkcast 3149	android/view/WindowManagerImpl
    //   1282: astore_2
    //   1283: aload_2
    //   1284: astore 5
    //   1286: iload 13
    //   1288: ifeq +32 -> 1320
    //   1291: aload 27
    //   1293: astore 21
    //   1295: aload_2
    //   1296: astore 23
    //   1298: aload 29
    //   1300: astore 20
    //   1302: aload_2
    //   1303: astore 24
    //   1305: aload 28
    //   1307: astore 19
    //   1309: aload_2
    //   1310: astore 22
    //   1312: aload_2
    //   1313: aload 34
    //   1315: invokevirtual 3153	android/view/WindowManagerImpl:createPresentationWindowManager	(Landroid/view/Display;)Landroid/view/WindowManagerImpl;
    //   1318: astore 5
    //   1320: aload 27
    //   1322: astore 21
    //   1324: aload 5
    //   1326: astore 23
    //   1328: aload 29
    //   1330: astore 20
    //   1332: aload 5
    //   1334: astore 24
    //   1336: aload 28
    //   1338: astore 19
    //   1340: aload 5
    //   1342: astore 22
    //   1344: aload 26
    //   1346: invokevirtual 3271	com/android/internal/policy/PhoneWindow:getDecorView	()Landroid/view/View;
    //   1349: astore 4
    //   1351: aload 4
    //   1353: astore 21
    //   1355: aload 5
    //   1357: astore 23
    //   1359: aload 4
    //   1361: astore 20
    //   1363: aload 5
    //   1365: astore 24
    //   1367: aload 4
    //   1369: astore 19
    //   1371: aload 5
    //   1373: astore 22
    //   1375: aload_0
    //   1376: getfield 1528	com/android/server/policy/PhoneWindowManager:mCoverState	Lcom/samsung/android/cover/CoverState;
    //   1379: ifnull +44 -> 1423
    //   1382: aload 4
    //   1384: astore 21
    //   1386: aload 5
    //   1388: astore 23
    //   1390: aload 4
    //   1392: astore 20
    //   1394: aload 5
    //   1396: astore 24
    //   1398: aload 4
    //   1400: astore 19
    //   1402: aload 5
    //   1404: astore 22
    //   1406: aload_0
    //   1407: getfield 1528	com/android/server/policy/PhoneWindowManager:mCoverState	Lcom/samsung/android/cover/CoverState;
    //   1410: getfield 2298	com/samsung/android/cover/CoverState:switchState	Z
    //   1413: istore 18
    //   1415: aload 4
    //   1417: astore_2
    //   1418: iload 18
    //   1420: ifeq +247 -> 1667
    //   1423: aload 4
    //   1425: astore 21
    //   1427: aload 5
    //   1429: astore 23
    //   1431: aload 4
    //   1433: astore_2
    //   1434: aload 4
    //   1436: astore 20
    //   1438: aload 5
    //   1440: astore 24
    //   1442: aload 4
    //   1444: astore 19
    //   1446: aload 5
    //   1448: astore 22
    //   1450: invokestatic 1467	android/os/SystemClock:uptimeMillis	()J
    //   1453: lstore 16
    //   1455: aload 11
    //   1457: ifnull +281 -> 1738
    //   1460: aload 4
    //   1462: astore 21
    //   1464: aload 5
    //   1466: astore 23
    //   1468: aload 4
    //   1470: astore_2
    //   1471: aload 4
    //   1473: astore 20
    //   1475: aload 5
    //   1477: astore 24
    //   1479: aload 4
    //   1481: astore 19
    //   1483: aload 5
    //   1485: astore 22
    //   1487: aload_0
    //   1488: getfield 1103	com/android/server/policy/PhoneWindowManager:mMobileKeyboardEnabled	Z
    //   1491: ifne +247 -> 1738
    //   1494: aload 4
    //   1496: astore 21
    //   1498: aload 5
    //   1500: astore 23
    //   1502: aload 4
    //   1504: astore_2
    //   1505: aload 4
    //   1507: astore 20
    //   1509: aload 5
    //   1511: astore 24
    //   1513: aload 4
    //   1515: astore 19
    //   1517: aload 5
    //   1519: astore 22
    //   1521: new 3132	android/widget/ImageView
    //   1524: dup
    //   1525: aload 25
    //   1527: invokespecial 3133	android/widget/ImageView:<init>	(Landroid/content/Context;)V
    //   1530: astore 10
    //   1532: aload 4
    //   1534: astore 21
    //   1536: aload 5
    //   1538: astore 23
    //   1540: aload 4
    //   1542: astore_2
    //   1543: aload 4
    //   1545: astore 20
    //   1547: aload 5
    //   1549: astore 24
    //   1551: aload 4
    //   1553: astore 19
    //   1555: aload 5
    //   1557: astore 22
    //   1559: aload 10
    //   1561: getstatic 3143	android/widget/ImageView$ScaleType:CENTER_CROP	Landroid/widget/ImageView$ScaleType;
    //   1564: invokevirtual 3147	android/widget/ImageView:setScaleType	(Landroid/widget/ImageView$ScaleType;)V
    //   1567: aload 4
    //   1569: astore 21
    //   1571: aload 5
    //   1573: astore 23
    //   1575: aload 4
    //   1577: astore_2
    //   1578: aload 4
    //   1580: astore 20
    //   1582: aload 5
    //   1584: astore 24
    //   1586: aload 4
    //   1588: astore 19
    //   1590: aload 5
    //   1592: astore 22
    //   1594: aload 10
    //   1596: aload 11
    //   1598: invokevirtual 3275	android/widget/ImageView:setImageBitmap	(Landroid/graphics/Bitmap;)V
    //   1601: aload 10
    //   1603: astore 4
    //   1605: aload 4
    //   1607: astore 21
    //   1609: aload 5
    //   1611: astore 23
    //   1613: aload 4
    //   1615: astore_2
    //   1616: aload 4
    //   1618: astore 20
    //   1620: aload 5
    //   1622: astore 24
    //   1624: aload 4
    //   1626: astore 19
    //   1628: aload 5
    //   1630: astore 22
    //   1632: ldc_w 299
    //   1635: new 1801	java/lang/StringBuilder
    //   1638: dup
    //   1639: invokespecial 1802	java/lang/StringBuilder:<init>	()V
    //   1642: ldc_w 3277
    //   1645: invokevirtual 1807	java/lang/StringBuilder:append	(Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   1648: invokestatic 1467	android/os/SystemClock:uptimeMillis	()J
    //   1651: lload 16
    //   1653: lsub
    //   1654: invokevirtual 3280	java/lang/StringBuilder:append	(J)Ljava/lang/StringBuilder;
    //   1657: invokevirtual 1815	java/lang/StringBuilder:toString	()Ljava/lang/String;
    //   1660: invokestatic 1459	android/util/Slog:d	(Ljava/lang/String;Ljava/lang/String;)I
    //   1663: pop
    //   1664: aload 4
    //   1666: astore_2
    //   1667: aload_2
    //   1668: astore 21
    //   1670: aload 5
    //   1672: astore 23
    //   1674: aload_2
    //   1675: astore 20
    //   1677: aload 5
    //   1679: astore 24
    //   1681: aload_2
    //   1682: astore 19
    //   1684: aload 5
    //   1686: astore 22
    //   1688: aload 26
    //   1690: invokevirtual 3283	com/android/internal/policy/PhoneWindow:isFloating	()Z
    //   1693: istore 18
    //   1695: iload 18
    //   1697: ifeq +272 -> 1969
    //   1700: aconst_null
    //   1701: astore 4
    //   1703: aload 4
    //   1705: astore_1
    //   1706: aload_2
    //   1707: ifnull -1701 -> 6
    //   1710: aload 4
    //   1712: astore_1
    //   1713: aload_2
    //   1714: invokevirtual 3267	android/view/View:getParent	()Landroid/view/ViewParent;
    //   1717: ifnonnull -1711 -> 6
    //   1720: ldc_w 299
    //   1723: ldc_w 3160
    //   1726: invokestatic 1936	android/util/Log:w	(Ljava/lang/String;Ljava/lang/String;)I
    //   1729: pop
    //   1730: aload 5
    //   1732: aload_2
    //   1733: invokevirtual 3163	android/view/WindowManagerImpl:removeViewImmediate	(Landroid/view/View;)V
    //   1736: aconst_null
    //   1737: areturn
    //   1738: aload 4
    //   1740: astore_2
    //   1741: iload 12
    //   1743: ifle -76 -> 1667
    //   1746: aload 4
    //   1748: astore 21
    //   1750: aload 5
    //   1752: astore 23
    //   1754: aload 4
    //   1756: astore_2
    //   1757: aload 4
    //   1759: astore 20
    //   1761: aload 5
    //   1763: astore 24
    //   1765: aload 4
    //   1767: astore 19
    //   1769: aload 5
    //   1771: astore 22
    //   1773: aload 26
    //   1775: iload 12
    //   1777: invokevirtual 3286	com/android/internal/policy/PhoneWindow:setContentView	(I)V
    //   1780: aload 4
    //   1782: astore 21
    //   1784: aload 5
    //   1786: astore 23
    //   1788: aload 4
    //   1790: astore_2
    //   1791: aload 4
    //   1793: astore 20
    //   1795: aload 5
    //   1797: astore 24
    //   1799: aload 4
    //   1801: astore 19
    //   1803: aload 5
    //   1805: astore 22
    //   1807: ldc_w 299
    //   1810: new 1801	java/lang/StringBuilder
    //   1813: dup
    //   1814: invokespecial 1802	java/lang/StringBuilder:<init>	()V
    //   1817: ldc_w 3288
    //   1820: invokevirtual 1807	java/lang/StringBuilder:append	(Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   1823: invokestatic 1467	android/os/SystemClock:uptimeMillis	()J
    //   1826: lload 16
    //   1828: lsub
    //   1829: invokevirtual 3280	java/lang/StringBuilder:append	(J)Ljava/lang/StringBuilder;
    //   1832: invokevirtual 1815	java/lang/StringBuilder:toString	()Ljava/lang/String;
    //   1835: invokestatic 1459	android/util/Slog:d	(Ljava/lang/String;Ljava/lang/String;)I
    //   1838: pop
    //   1839: aload 4
    //   1841: astore_2
    //   1842: goto -175 -> 1667
    //   1845: astore 4
    //   1847: aload_2
    //   1848: astore 21
    //   1850: aload 5
    //   1852: astore 23
    //   1854: aload_2
    //   1855: astore 20
    //   1857: aload 5
    //   1859: astore 24
    //   1861: aload_2
    //   1862: astore 19
    //   1864: aload 5
    //   1866: astore 22
    //   1868: ldc_w 299
    //   1871: new 1801	java/lang/StringBuilder
    //   1874: dup
    //   1875: invokespecial 1802	java/lang/StringBuilder:<init>	()V
    //   1878: ldc_w 3290
    //   1881: invokevirtual 1807	java/lang/StringBuilder:append	(Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   1884: aload 4
    //   1886: invokevirtual 2919	java/lang/StringBuilder:append	(Ljava/lang/Object;)Ljava/lang/StringBuilder;
    //   1889: invokevirtual 1815	java/lang/StringBuilder:toString	()Ljava/lang/String;
    //   1892: invokestatic 1459	android/util/Slog:d	(Ljava/lang/String;Ljava/lang/String;)I
    //   1895: pop
    //   1896: goto -229 -> 1667
    //   1899: astore_2
    //   1900: aload 20
    //   1902: astore 19
    //   1904: aload 24
    //   1906: astore 22
    //   1908: ldc_w 299
    //   1911: new 1801	java/lang/StringBuilder
    //   1914: dup
    //   1915: invokespecial 1802	java/lang/StringBuilder:<init>	()V
    //   1918: aload_1
    //   1919: invokevirtual 2919	java/lang/StringBuilder:append	(Ljava/lang/Object;)Ljava/lang/StringBuilder;
    //   1922: ldc_w 3292
    //   1925: invokevirtual 1807	java/lang/StringBuilder:append	(Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   1928: invokevirtual 1815	java/lang/StringBuilder:toString	()Ljava/lang/String;
    //   1931: aload_2
    //   1932: invokestatic 2317	android/util/Log:w	(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I
    //   1935: pop
    //   1936: aload 20
    //   1938: ifnull -693 -> 1245
    //   1941: aload 20
    //   1943: invokevirtual 3267	android/view/View:getParent	()Landroid/view/ViewParent;
    //   1946: ifnonnull -701 -> 1245
    //   1949: ldc_w 299
    //   1952: ldc_w 3160
    //   1955: invokestatic 1936	android/util/Log:w	(Ljava/lang/String;Ljava/lang/String;)I
    //   1958: pop
    //   1959: aload 24
    //   1961: aload 20
    //   1963: invokevirtual 3163	android/view/WindowManagerImpl:removeViewImmediate	(Landroid/view/View;)V
    //   1966: goto -721 -> 1245
    //   1969: aload_2
    //   1970: astore 21
    //   1972: aload 5
    //   1974: astore 23
    //   1976: aload_2
    //   1977: astore 20
    //   1979: aload 5
    //   1981: astore 24
    //   1983: aload_2
    //   1984: astore 19
    //   1986: aload 5
    //   1988: astore 22
    //   1990: aload 5
    //   1992: aload_2
    //   1993: aload 33
    //   1995: invokevirtual 3154	android/view/WindowManagerImpl:addView	(Landroid/view/View;Landroid/view/ViewGroup$LayoutParams;)V
    //   1998: aload_2
    //   1999: astore 21
    //   2001: aload 5
    //   2003: astore 23
    //   2005: aload_2
    //   2006: astore 20
    //   2008: aload 5
    //   2010: astore 24
    //   2012: aload_2
    //   2013: astore 19
    //   2015: aload 5
    //   2017: astore 22
    //   2019: aload_2
    //   2020: invokevirtual 3267	android/view/View:getParent	()Landroid/view/ViewParent;
    //   2023: astore 4
    //   2025: aload 4
    //   2027: ifnull +42 -> 2069
    //   2030: aload_2
    //   2031: astore 4
    //   2033: aload 4
    //   2035: astore_1
    //   2036: aload_2
    //   2037: ifnull -2031 -> 6
    //   2040: aload 4
    //   2042: astore_1
    //   2043: aload_2
    //   2044: invokevirtual 3267	android/view/View:getParent	()Landroid/view/ViewParent;
    //   2047: ifnonnull -2041 -> 6
    //   2050: ldc_w 299
    //   2053: ldc_w 3160
    //   2056: invokestatic 1936	android/util/Log:w	(Ljava/lang/String;Ljava/lang/String;)I
    //   2059: pop
    //   2060: aload 5
    //   2062: aload_2
    //   2063: invokevirtual 3163	android/view/WindowManagerImpl:removeViewImmediate	(Landroid/view/View;)V
    //   2066: aload 4
    //   2068: areturn
    //   2069: aconst_null
    //   2070: astore 4
    //   2072: goto -39 -> 2033
    //   2075: astore_1
    //   2076: aload 19
    //   2078: ifnull +28 -> 2106
    //   2081: aload 19
    //   2083: invokevirtual 3267	android/view/View:getParent	()Landroid/view/ViewParent;
    //   2086: ifnonnull +20 -> 2106
    //   2089: ldc_w 299
    //   2092: ldc_w 3160
    //   2095: invokestatic 1936	android/util/Log:w	(Ljava/lang/String;Ljava/lang/String;)I
    //   2098: pop
    //   2099: aload 22
    //   2101: aload 19
    //   2103: invokevirtual 3163	android/view/WindowManagerImpl:removeViewImmediate	(Landroid/view/View;)V
    //   2106: aload_1
    //   2107: athrow
    //   2108: astore 19
    //   2110: goto -1822 -> 288
    //   2113: iconst_0
    //   2114: iconst_0
    //   2115: ior
    //   2116: istore 15
    //   2118: goto -1915 -> 203
    //
    // Exception table:
    //   from	to	target	type
    //   608	615	1142	finally
    //   622	625	1142	finally
    //   1143	1146	1142	finally
    //   69	75	1172	android/view/WindowManager$BadTokenException
    //   99	106	1172	android/view/WindowManager$BadTokenException
    //   152	160	1172	android/view/WindowManager$BadTokenException
    //   188	200	1172	android/view/WindowManager$BadTokenException
    //   231	250	1172	android/view/WindowManager$BadTokenException
    //   278	284	1172	android/view/WindowManager$BadTokenException
    //   312	323	1172	android/view/WindowManager$BadTokenException
    //   347	353	1172	android/view/WindowManager$BadTokenException
    //   377	384	1172	android/view/WindowManager$BadTokenException
    //   408	419	1172	android/view/WindowManager$BadTokenException
    //   443	453	1172	android/view/WindowManager$BadTokenException
    //   496	513	1172	android/view/WindowManager$BadTokenException
    //   537	543	1172	android/view/WindowManager$BadTokenException
    //   567	578	1172	android/view/WindowManager$BadTokenException
    //   602	605	1172	android/view/WindowManager$BadTokenException
    //   649	676	1172	android/view/WindowManager$BadTokenException
    //   700	707	1172	android/view/WindowManager$BadTokenException
    //   731	738	1172	android/view/WindowManager$BadTokenException
    //   762	769	1172	android/view/WindowManager$BadTokenException
    //   793	800	1172	android/view/WindowManager$BadTokenException
    //   824	830	1172	android/view/WindowManager$BadTokenException
    //   854	860	1172	android/view/WindowManager$BadTokenException
    //   884	900	1172	android/view/WindowManager$BadTokenException
    //   924	936	1172	android/view/WindowManager$BadTokenException
    //   960	973	1172	android/view/WindowManager$BadTokenException
    //   997	1005	1172	android/view/WindowManager$BadTokenException
    //   1029	1043	1172	android/view/WindowManager$BadTokenException
    //   1067	1092	1172	android/view/WindowManager$BadTokenException
    //   1116	1123	1172	android/view/WindowManager$BadTokenException
    //   1170	1172	1172	android/view/WindowManager$BadTokenException
    //   1271	1283	1172	android/view/WindowManager$BadTokenException
    //   1312	1320	1172	android/view/WindowManager$BadTokenException
    //   1344	1351	1172	android/view/WindowManager$BadTokenException
    //   1375	1382	1172	android/view/WindowManager$BadTokenException
    //   1406	1415	1172	android/view/WindowManager$BadTokenException
    //   1450	1455	1172	android/view/WindowManager$BadTokenException
    //   1487	1494	1172	android/view/WindowManager$BadTokenException
    //   1521	1532	1172	android/view/WindowManager$BadTokenException
    //   1559	1567	1172	android/view/WindowManager$BadTokenException
    //   1594	1601	1172	android/view/WindowManager$BadTokenException
    //   1632	1664	1172	android/view/WindowManager$BadTokenException
    //   1688	1695	1172	android/view/WindowManager$BadTokenException
    //   1773	1780	1172	android/view/WindowManager$BadTokenException
    //   1807	1839	1172	android/view/WindowManager$BadTokenException
    //   1868	1896	1172	android/view/WindowManager$BadTokenException
    //   1990	1998	1172	android/view/WindowManager$BadTokenException
    //   2019	2025	1172	android/view/WindowManager$BadTokenException
    //   1450	1455	1845	android/view/InflateException
    //   1487	1494	1845	android/view/InflateException
    //   1521	1532	1845	android/view/InflateException
    //   1559	1567	1845	android/view/InflateException
    //   1594	1601	1845	android/view/InflateException
    //   1632	1664	1845	android/view/InflateException
    //   1773	1780	1845	android/view/InflateException
    //   1807	1839	1845	android/view/InflateException
    //   69	75	1899	java/lang/RuntimeException
    //   99	106	1899	java/lang/RuntimeException
    //   152	160	1899	java/lang/RuntimeException
    //   188	200	1899	java/lang/RuntimeException
    //   231	250	1899	java/lang/RuntimeException
    //   278	284	1899	java/lang/RuntimeException
    //   312	323	1899	java/lang/RuntimeException
    //   347	353	1899	java/lang/RuntimeException
    //   377	384	1899	java/lang/RuntimeException
    //   408	419	1899	java/lang/RuntimeException
    //   443	453	1899	java/lang/RuntimeException
    //   496	513	1899	java/lang/RuntimeException
    //   537	543	1899	java/lang/RuntimeException
    //   567	578	1899	java/lang/RuntimeException
    //   602	605	1899	java/lang/RuntimeException
    //   649	676	1899	java/lang/RuntimeException
    //   700	707	1899	java/lang/RuntimeException
    //   731	738	1899	java/lang/RuntimeException
    //   762	769	1899	java/lang/RuntimeException
    //   793	800	1899	java/lang/RuntimeException
    //   824	830	1899	java/lang/RuntimeException
    //   854	860	1899	java/lang/RuntimeException
    //   884	900	1899	java/lang/RuntimeException
    //   924	936	1899	java/lang/RuntimeException
    //   960	973	1899	java/lang/RuntimeException
    //   997	1005	1899	java/lang/RuntimeException
    //   1029	1043	1899	java/lang/RuntimeException
    //   1067	1092	1899	java/lang/RuntimeException
    //   1116	1123	1899	java/lang/RuntimeException
    //   1170	1172	1899	java/lang/RuntimeException
    //   1271	1283	1899	java/lang/RuntimeException
    //   1312	1320	1899	java/lang/RuntimeException
    //   1344	1351	1899	java/lang/RuntimeException
    //   1375	1382	1899	java/lang/RuntimeException
    //   1406	1415	1899	java/lang/RuntimeException
    //   1450	1455	1899	java/lang/RuntimeException
    //   1487	1494	1899	java/lang/RuntimeException
    //   1521	1532	1899	java/lang/RuntimeException
    //   1559	1567	1899	java/lang/RuntimeException
    //   1594	1601	1899	java/lang/RuntimeException
    //   1632	1664	1899	java/lang/RuntimeException
    //   1688	1695	1899	java/lang/RuntimeException
    //   1773	1780	1899	java/lang/RuntimeException
    //   1807	1839	1899	java/lang/RuntimeException
    //   1868	1896	1899	java/lang/RuntimeException
    //   1990	1998	1899	java/lang/RuntimeException
    //   2019	2025	1899	java/lang/RuntimeException
    //   69	75	2075	finally
    //   99	106	2075	finally
    //   152	160	2075	finally
    //   188	200	2075	finally
    //   231	250	2075	finally
    //   278	284	2075	finally
    //   312	323	2075	finally
    //   347	353	2075	finally
    //   377	384	2075	finally
    //   408	419	2075	finally
    //   443	453	2075	finally
    //   496	513	2075	finally
    //   537	543	2075	finally
    //   567	578	2075	finally
    //   602	605	2075	finally
    //   649	676	2075	finally
    //   700	707	2075	finally
    //   731	738	2075	finally
    //   762	769	2075	finally
    //   793	800	2075	finally
    //   824	830	2075	finally
    //   854	860	2075	finally
    //   884	900	2075	finally
    //   924	936	2075	finally
    //   960	973	2075	finally
    //   997	1005	2075	finally
    //   1029	1043	2075	finally
    //   1067	1092	2075	finally
    //   1116	1123	2075	finally
    //   1170	1172	2075	finally
    //   1181	1215	2075	finally
    //   1271	1283	2075	finally
    //   1312	1320	2075	finally
    //   1344	1351	2075	finally
    //   1375	1382	2075	finally
    //   1406	1415	2075	finally
    //   1450	1455	2075	finally
    //   1487	1494	2075	finally
    //   1521	1532	2075	finally
    //   1559	1567	2075	finally
    //   1594	1601	2075	finally
    //   1632	1664	2075	finally
    //   1688	1695	2075	finally
    //   1773	1780	2075	finally
    //   1807	1839	2075	finally
    //   1868	1896	2075	finally
    //   1908	1936	2075	finally
    //   1990	1998	2075	finally
    //   2019	2025	2075	finally
    //   152	160	2108	android/content/pm/PackageManager$NameNotFoundException
    //   188	200	2108	android/content/pm/PackageManager$NameNotFoundException
    //   231	250	2108	android/content/pm/PackageManager$NameNotFoundException
    //   278	284	2108	android/content/pm/PackageManager$NameNotFoundException
  }

  public void adjustConfigurationLw(Configuration paramConfiguration, int paramInt1, int paramInt2)
  {
    if ((paramInt1 & 0x1) != 0);
    for (boolean bool = true; ; bool = false)
    {
      this.mHaveBuiltInKeyboard = bool;
      readConfigurationDependentBehaviors();
      readLidState();
      applyLidSwitchState();
      if ((paramConfiguration.keyboard == 1) || ((SamsungPolicyProperties.FolderTypeFeature(this.mContext) != 1) && (paramInt1 == 1) && (isHidden(this.mLidKeyboardAccessibility))))
      {
        paramConfiguration.hardKeyboardHidden = 2;
        if (!this.mHasSoftInput)
          paramConfiguration.keyboardHidden = 2;
      }
      if ((paramConfiguration.navigation == 1) || ((paramInt2 == 1) && (isHidden(this.mLidNavigationAccessibility))))
        paramConfiguration.navigationHidden = 2;
      if (!this.mMobileKeyboardEnabled)
        break;
      paramConfiguration.mobileKeyboardCovered = 1;
      return;
    }
    paramConfiguration.mobileKeyboardCovered = 0;
  }

  public int adjustSystemUiVisibilityLw(int paramInt)
  {
    return adjustSystemUiVisibilityLw(paramInt, 0);
  }

  public int adjustSystemUiVisibilityLw(int paramInt1, int paramInt2)
  {
    int i = this.mLastSystemUiFlags;
    int j = this.mResettingSystemUiFlags;
    this.mStatusBarController.adjustSystemUiVisibilityLw(i, paramInt1, paramInt2);
    this.mNavigationBarController.adjustSystemUiVisibilityLw(i, paramInt1, paramInt2);
    if ((paramInt1 & 0x4000) > 0);
    for (boolean bool = true; ; bool = false)
    {
      this.mRecentsVisible = bool;
      paramInt2 = j & paramInt1;
      this.mResettingSystemUiFlags = paramInt2;
      return (paramInt2 ^ 0xFFFFFFFF) & paramInt1 & (this.mForceClearedSystemUiFlags ^ 0xFFFFFFFF);
    }
  }

  public void adjustWindowParamsLw(WindowManager.LayoutParams paramLayoutParams)
  {
    adjustWindowParamsLw(paramLayoutParams, 0);
  }

  public void adjustWindowParamsLw(WindowManager.LayoutParams paramLayoutParams, int paramInt)
  {
    switch (paramLayoutParams.type)
    {
    default:
    case 2006:
    case 2015:
    case 2000:
    }
    while (true)
    {
      if (paramLayoutParams.type != 2000)
      {
        paramLayoutParams.privateFlags &= -1025;
        paramLayoutParams.samsungFlags &= -268435457;
      }
      if ((paramLayoutParams.flags & 0x80000000) != 0)
        paramLayoutParams.subtreeSystemUiVisibility |= 1536;
      return;
      paramLayoutParams.flags |= 24;
      paramLayoutParams.flags &= -262145;
      continue;
      if ((this.mKeyguardHidden) && ((!isKeyguardSecure()) || (this.mHideLockScreen)))
      {
        paramLayoutParams.flags &= -1048577;
        paramLayoutParams.privateFlags &= -1025;
      }
      if (this.mHideSViewCover == 0)
        continue;
      paramLayoutParams.samsungFlags &= -268435457;
    }
  }

  public boolean allowAppAnimationsLw()
  {
    return (!isStatusBarKeyguard()) && (!this.mShowingDream) && (!isKnoxKeyguardShownForKioskMode());
  }

  public void applyPostLayoutPolicyLw(WindowManagerPolicy.WindowState paramWindowState1, WindowManager.LayoutParams paramLayoutParams, WindowManagerPolicy.WindowState paramWindowState2)
  {
    int i = paramWindowState1.getDisplayId();
    Object localObject1 = this.mTopFullscreenOpaqueWindowState;
    WindowManagerPolicy.WindowState localWindowState1 = this.mTopFullscreenOpaqueOrDimmingWindowState;
    WindowManagerPolicy.WindowState localWindowState4 = this.mWinShowWhenLocked;
    this.mMultiPhoneWindowManager.applyPostLayoutPolicyLw(paramWindowState1, paramLayoutParams);
    if (DEBUG_LAYOUT)
      Slog.i("WindowManager", "Win " + paramWindowState1 + ": isVisibleOrBehindKeyguardLw=" + paramWindowState1.isVisibleOrBehindKeyguardLw());
    int n = PolicyControl.getWindowFlags(paramWindowState1, paramLayoutParams);
    if ((localObject1 == null) && (paramWindowState1.isVisibleLw()) && ((paramLayoutParams.type == 2011) || (paramLayoutParams.type == 2280)))
    {
      this.mForcingShowNavBar = true;
      this.mForcingShowNavBarLayer = paramWindowState1.getSurfaceLayer();
    }
    if (paramLayoutParams.type == 2000)
    {
      if ((paramLayoutParams.privateFlags & 0x400) != 0)
      {
        if ((!this.mForceStatusBarFromKeyguard) && (this.mStatusBarController.isTransientHiding(i)))
          this.mStatusBarController.resetTransient(i);
        this.mForceStatusBarFromKeyguard = true;
        this.mShowingLockscreen = true;
      }
      if ((paramLayoutParams.privateFlags & 0x1000) != 0)
        this.mForceStatusBarTransparent = true;
    }
    int j;
    label255: int k;
    label291: label303: int m;
    label315: label375: IApplicationToken localIApplicationToken;
    WindowManagerPolicy.WindowState localWindowState3;
    Object localObject2;
    WindowManagerPolicy.WindowState localWindowState2;
    if ((paramLayoutParams.type == 2000) && ((paramLayoutParams.samsungFlags & 0x10000000) != 0))
    {
      i = 1;
      if ((this.mHideSViewCover != 0) || (!isForceHideBySViewCover()))
        break label1276;
      j = 1;
      if ((i != 0) || (j != 0))
        this.mForceStatusBarFromSViewCover = true;
      if ((paramLayoutParams.type < 1) || (paramLayoutParams.type >= 2000))
        break label1282;
      i = 1;
      if ((0x80000 & n) == 0)
        break label1288;
      k = 1;
      if ((0x400000 & n) == 0)
        break label1294;
      m = 1;
      if ((localObject1 != null) || (!paramWindowState1.isVisibleOrBehindKeyguardLw()) || (paramWindowState1.isGoneForLayoutLw()) || (paramWindowState1.getMultiWindowStyleLw().isCascade()))
        break label1571;
      if ((n & 0x800) != 0)
      {
        if ((paramLayoutParams.privateFlags & 0x400) == 0)
          break label1300;
        this.mForceStatusBarFromKeyguard = true;
        if ((paramLayoutParams.samsungFlags & 0x10000000) != 0)
          this.mForceStatusBarFromSViewCover = true;
        if ((paramLayoutParams.privateFlags & 0x2000) != 0)
        {
          this.mForceStatusBar = true;
          Log.d("Shared devices", "Force status bar SD type privflag");
        }
      }
      if ((paramLayoutParams.samsungFlags & 0x10000000) != 0)
        this.mShowingSViewCover = true;
      this.mMultiPhoneWindowManager.applyPostLayoutPolicyForRecenUI(paramLayoutParams.type);
      j = i;
      if (paramLayoutParams.type == 2023)
        if (this.mDreamingLockscreen)
        {
          j = i;
          if (paramWindowState1.isVisibleLw())
          {
            j = i;
            if (!paramWindowState1.hasDrawnLw());
          }
        }
        else
        {
          this.mShowingDream = true;
          j = 1;
        }
      i = j;
      if (paramLayoutParams.type == 2202)
      {
        i = j;
        if (paramWindowState1.isVisibleLw())
        {
          i = j;
          if (paramWindowState1.hasDrawnLw())
            i = 1;
        }
      }
      localIApplicationToken = paramWindowState1.getAppToken();
      localWindowState3 = localWindowState1;
      localObject2 = localObject1;
      if (i != 0)
      {
        localWindowState3 = localWindowState1;
        localObject2 = localObject1;
        if (paramWindowState2 == null)
        {
          if (k == 0)
            break label1374;
          this.mAppsToBeHidden.remove(localIApplicationToken);
          this.mAppsThatDismissKeyguard.remove(localIApplicationToken);
          localWindowState2 = localWindowState4;
          if (this.mAppsToBeHidden.isEmpty())
          {
            if ((m == 0) || (this.mKeyguardSecure))
              break label1318;
            this.mAppsThatDismissKeyguard.add(localIApplicationToken);
            paramWindowState2 = localWindowState4;
            label639: this.mHideSDKeyguard = true;
            localWindowState2 = paramWindowState2;
          }
          label647: paramWindowState2 = localWindowState1;
          if (checkTopFullscreenOpaqueWindowState(paramWindowState1, paramLayoutParams))
          {
            if (DEBUG_LAYOUT)
              Slog.v("WindowManager", "Fullscreen window: " + paramWindowState1);
            localWindowState3 = paramWindowState1;
            localObject2 = localWindowState1;
            if (localWindowState1 == null)
              localObject2 = paramWindowState1;
            this.mTopFullscreenOpaqueWindowState = localWindowState3;
            this.mTopFullscreenOpaqueOrDimmingWindowState = ((WindowManagerPolicy.WindowState)localObject2);
            if ((this.mAppsThatDismissKeyguard.isEmpty()) || (this.mDismissKeyguard != 0))
              break label1449;
            if (DEBUG_LAYOUT)
              Slog.v("WindowManager", "Setting mDismissKeyguard true by win " + paramWindowState1);
            if ((this.mWinDismissingKeyguard != paramWindowState1) || (this.mSecureDismissingKeyguard != this.mKeyguardSecure))
              break label1443;
            i = 2;
            label791: this.mDismissKeyguard = i;
            this.mWinDismissingKeyguard = paramWindowState1;
            this.mSecureDismissingKeyguard = this.mKeyguardSecure;
            this.mForceStatusBarFromKeyguard = this.mShowingLockscreen;
            label818: paramWindowState2 = (WindowManagerPolicy.WindowState)localObject2;
            localObject1 = localWindowState3;
            if ((n & 0x1) != 0)
            {
              this.mAllowLockscreenWhenOn = true;
              localObject1 = localWindowState3;
              paramWindowState2 = (WindowManagerPolicy.WindowState)localObject2;
            }
          }
          localWindowState3 = paramWindowState2;
          localObject2 = localObject1;
          if (localWindowState2 != null)
          {
            localWindowState3 = paramWindowState2;
            localObject2 = localObject1;
            if (localWindowState2.getAppToken() != paramWindowState1.getAppToken())
            {
              localWindowState3 = paramWindowState2;
              localObject2 = localObject1;
              if ((paramLayoutParams.flags & 0x80000) == 0)
              {
                paramWindowState1.hideLw(false);
                localObject2 = localObject1;
                localWindowState3 = paramWindowState2;
              }
            }
          }
        }
      }
      label912: if ((localWindowState3 == null) && (paramWindowState1.isVisibleOrBehindKeyguardLw()) && (!paramWindowState1.isGoneForLayoutLw()) && (paramWindowState1.isDimming()))
        this.mTopFullscreenOpaqueOrDimmingWindowState = paramWindowState1;
      if ((isSupportAndAttachedSViewCover()) && (!this.mCoverState.switchState))
      {
        this.mHideLockScreenByCover = true;
        if (this.mHideSViewCoverWindowState == null)
        {
          j = paramWindowState1.getCoverMode();
          if (((paramLayoutParams.type < 1) || (paramLayoutParams.type > 99) || ((paramLayoutParams.flags & 0x80000) == 0)) && ((paramLayoutParams.type != 3) || (j == 0)))
            break label1676;
          i = 1;
          label1030: paramWindowState2 = paramWindowState1.getAppToken();
          if (i != 0)
          {
            if (j != 1)
              break label1682;
            this.mHideSViewCover = 1;
            this.mAppsToBeHiddenBySViewCover.remove(paramWindowState2);
          }
        }
      }
    }
    while (true)
    {
      if ((this.mHideSViewCover != 0) && (this.mAppsToBeHiddenBySViewCover.isEmpty()))
      {
        this.mHideSViewCoverWindowState = paramWindowState1;
        this.mForceStatusBarFromSViewCover = this.mShowingSViewCover;
        if (DEBUG_LAYOUT)
          Slog.d("WindowManager", "Hide sview cover : mHideSViewCover =" + this.mHideSViewCover + ", SViewCoverWindow = " + paramWindowState1);
      }
      if ((isKeyguardLocked()) && (k != 0) && (paramWindowState1.isOnScreen()) && (this.mAppsToBeHidden.isEmpty()))
      {
        paramWindowState2 = paramWindowState1.getAppToken();
        if ((paramWindowState2 != null) && (!this.mAppsShowWhenLocked.contains(paramWindowState2)) && (paramWindowState1.getMultiWindowStyleLw().getType() == 0) && (paramLayoutParams.type != 3))
          this.mAppsShowWhenLocked.add(paramWindowState2);
      }
      if (CocktailBarFeatures.isSystemBarType(this.mContext))
      {
        this.mCocktailPhoneWindowManager.applyPostLayoutPolicyLw(paramWindowState1, (WindowManagerPolicy.WindowState)localObject2, paramLayoutParams);
        if (this.mForceHideStatusBarForCocktail)
          this.mForceStatusBar = false;
      }
      if ((paramLayoutParams.samsungFlags & 0x10000) != 0)
        this.mFakeFocusedWindow = paramWindowState1;
      return;
      i = 0;
      break;
      label1276: j = 0;
      break label255;
      label1282: i = 0;
      break label291;
      label1288: k = 0;
      break label303;
      label1294: m = 0;
      break label315;
      label1300: if (this.mMultiPhoneWindowManager.isStatusBarTransient())
        break label375;
      this.mForceStatusBar = true;
      break label375;
      label1318: if (!paramWindowState1.isDrawnLw())
      {
        paramWindowState2 = localWindowState4;
        if (!paramWindowState1.hasAppShownWindows())
          break label639;
      }
      paramWindowState2 = localWindowState4;
      if (!paramWindowState1.getMultiWindowStyleLw().isNormal())
        break label639;
      paramWindowState2 = paramWindowState1;
      this.mWinShowWhenLocked = paramWindowState2;
      this.mHideLockScreen = true;
      this.mForceStatusBarFromKeyguard = false;
      break label639;
      label1374: if (m != 0)
      {
        if (this.mKeyguardSecure)
          this.mAppsToBeHidden.add(localIApplicationToken);
        while (true)
        {
          this.mAppsThatDismissKeyguard.add(localIApplicationToken);
          localWindowState2 = localWindowState4;
          break;
          this.mAppsToBeHidden.remove(localIApplicationToken);
        }
      }
      this.mAppsToBeHidden.add(localIApplicationToken);
      localWindowState2 = localWindowState4;
      break label647;
      label1443: i = 1;
      break label791;
      label1449: if ((!this.mAppsToBeHidden.isEmpty()) || (k == 0) || ((!paramWindowState1.isDrawnLw()) && (!paramWindowState1.hasAppShownWindows())))
        break label818;
      if (DEBUG_LAYOUT)
        Slog.v("WindowManager", "Setting mHideLockScreen to true by win " + paramWindowState1);
      this.mHideLockScreen = true;
      this.mForceStatusBarFromKeyguard = false;
      if (((paramLayoutParams.flags & 0x200000) == 0) || (paramLayoutParams.userActivityTimeout >= 0L) || (!this.mForceUserActivityTimeoutWin.contains(paramWindowState1)))
        break label818;
      paramLayoutParams.userActivityTimeout = 5226L;
      paramLayoutParams.screenDimDuration = 0L;
      break label818;
      label1571: localWindowState3 = localWindowState1;
      localObject2 = localObject1;
      if (localObject1 != null)
        break label912;
      localWindowState3 = localWindowState1;
      localObject2 = localObject1;
      if (localWindowState4 != null)
        break label912;
      localWindowState3 = localWindowState1;
      localObject2 = localObject1;
      if (!paramWindowState1.isAnimatingLw())
        break label912;
      localWindowState3 = localWindowState1;
      localObject2 = localObject1;
      if (i == 0)
        break label912;
      localWindowState3 = localWindowState1;
      localObject2 = localObject1;
      if (k == 0)
        break label912;
      localWindowState3 = localWindowState1;
      localObject2 = localObject1;
      if (!this.mKeyguardHidden)
        break label912;
      this.mHideLockScreen = true;
      this.mWinShowWhenLocked = paramWindowState1;
      localWindowState3 = localWindowState1;
      localObject2 = localObject1;
      break label912;
      label1676: i = 0;
      break label1030;
      label1682: if (j == 2)
      {
        if (!paramWindowState1.willBeHideSViewCoverOnce())
          continue;
        this.mHideSViewCover = 2;
        this.mAppsToBeHiddenBySViewCover.remove(paramWindowState2);
        continue;
      }
      this.mAppsToBeHiddenBySViewCover.add(paramWindowState2);
    }
  }

  public void beginLayoutLw(boolean paramBoolean, int paramInt1, int paramInt2, int paramInt3)
  {
    beginLayoutLw(paramBoolean, paramInt1, paramInt2, paramInt3, 0, true);
  }

  public void beginLayoutLw(boolean paramBoolean1, int paramInt1, int paramInt2, int paramInt3, int paramInt4, boolean paramBoolean2)
  {
    this.mDisplayRotation = paramInt3;
    int k;
    int m;
    int j;
    Object localObject2;
    WindowManagerPolicy.WindowState localWindowState;
    WindowManagerPolicy.InputConsumer localInputConsumer;
    label613: label625: int n;
    label637: int i2;
    label649: label662: label670: label701: label715: boolean bool1;
    label760: int i4;
    label770: boolean bool2;
    if (paramBoolean1)
      switch (paramInt3)
      {
      default:
        k = this.mOverscanLeft;
        m = this.mOverscanTop;
        j = this.mOverscanRight;
        i = this.mOverscanBottom;
        this.mRestrictedOverscanScreenLeft = 0;
        this.mOverscanScreenLeft = 0;
        this.mRestrictedOverscanScreenTop = 0;
        this.mOverscanScreenTop = 0;
        this.mRestrictedOverscanScreenWidth = paramInt1;
        this.mOverscanScreenWidth = paramInt1;
        this.mRestrictedOverscanScreenHeight = paramInt2;
        this.mOverscanScreenHeight = paramInt2;
        this.mSystemLeft = 0;
        this.mSystemTop = 0;
        this.mSystemRight = paramInt1;
        this.mSystemBottom = paramInt2;
        this.mUnrestrictedScreenLeft = k;
        this.mUnrestrictedScreenTop = m;
        this.mUnrestrictedScreenWidth = (paramInt1 - k - j);
        this.mUnrestrictedScreenHeight = (paramInt2 - m - i);
        this.mRestrictedScreenLeft = this.mUnrestrictedScreenLeft;
        this.mRestrictedScreenTop = this.mUnrestrictedScreenTop;
        Object localObject1 = this.mSystemGestures;
        k = this.mUnrestrictedScreenWidth;
        ((SystemGesturesPointerEventListener)localObject1).screenWidth = k;
        this.mRestrictedScreenWidth = k;
        localObject1 = this.mSystemGestures;
        k = this.mUnrestrictedScreenHeight;
        ((SystemGesturesPointerEventListener)localObject1).screenHeight = k;
        this.mRestrictedScreenHeight = k;
        k = this.mUnrestrictedScreenLeft;
        this.mCurLeft = k;
        this.mStableFullscreenLeft = k;
        this.mStableLeft = k;
        this.mVoiceContentLeft = k;
        this.mContentLeft = k;
        this.mDockLeft = k;
        k = this.mUnrestrictedScreenTop;
        this.mCurTop = k;
        this.mStableFullscreenTop = k;
        this.mStableTop = k;
        this.mVoiceContentTop = k;
        this.mContentTop = k;
        this.mDockTop = k;
        k = paramInt1 - j;
        this.mCurRight = k;
        this.mStableFullscreenRight = k;
        this.mStableRight = k;
        this.mVoiceContentRight = k;
        this.mContentRight = k;
        this.mDockRight = k;
        k = paramInt2 - i;
        this.mCurBottom = k;
        this.mStableFullscreenBottom = k;
        this.mStableBottom = k;
        this.mVoiceContentBottom = k;
        this.mContentBottom = k;
        this.mDockBottom = k;
        this.mDockLayer = 268435456;
        this.mStatusBarLayer = -1;
        localObject1 = mTmpParentFrame;
        Rect localRect1 = mTmpDisplayFrame;
        Rect localRect2 = mTmpOverscanFrame;
        Rect localRect3 = mTmpVisibleFrame;
        Rect localRect4 = mTmpDecorFrame;
        localObject2 = mTmpOutsetFrame;
        k = this.mDockLeft;
        localRect3.left = k;
        localRect2.left = k;
        localRect1.left = k;
        ((Rect)localObject1).left = k;
        k = this.mDockTop;
        localRect3.top = k;
        localRect2.top = k;
        localRect1.top = k;
        ((Rect)localObject1).top = k;
        k = this.mDockRight;
        localRect3.right = k;
        localRect2.right = k;
        localRect1.right = k;
        ((Rect)localObject1).right = k;
        k = this.mDockBottom;
        localRect3.bottom = k;
        localRect2.bottom = k;
        localRect1.bottom = k;
        ((Rect)localObject1).bottom = k;
        localRect4.setEmpty();
        if (paramBoolean1)
        {
          localObject2 = this.mStatusBar;
          localWindowState = this.mNavigationBar;
          int i3 = this.mLastSystemUiFlags;
          localInputConsumer = this.mInputConsumer;
          if ((i3 & 0x2) != 0)
            break label2699;
          k = 1;
          if ((0x80008000 & i3) == 0)
            break label2705;
          m = 1;
          if ((i3 & 0x800) == 0)
            break label2711;
          n = 1;
          if ((i3 & 0x1000) == 0)
            break label2717;
          int i1 = 1;
          if ((n == 0) && (i1 == 0))
            break label2723;
          n = 1;
          if (i1 != 0)
            break label2729;
          i1 = 1;
          i2 = m & i1;
          if ((isStatusBarSViewCover()) || (!isStatusBarKeyguard()) || (this.mHideLockScreen))
            break label2735;
          m = 1;
          if ((!isStatusBarSViewCover()) || (this.mLastCoverAppCovered))
            break label2741;
          i1 = i2;
          if (m == 0)
            bool1 = i2 & areTranslucentBarsAllowed();
          if ((k == 0) && (n == 0))
            break label2744;
          if (localInputConsumer != null)
          {
            localInputConsumer.dismiss();
            this.mInputConsumer = null;
          }
          if (canHideNavigationBar())
            break label2776;
          i2 = 1;
          i4 = k | i2;
          i2 = 0;
          k = i2;
          if (localWindowState != null)
          {
            bool2 = this.mNavigationBarController.isTransientShowing(paramInt4);
            if ((this.mNavigationBarCanMove) && (paramInt1 >= paramInt2))
              break label2782;
            paramBoolean2 = true;
            label815: this.mNavigationBarOnBottom = paramBoolean2;
            if (!this.mNavigationBarOnBottom)
              break label2859;
            j = this.mNavigationBarHeightForRotation[paramInt3];
            mTmpNavigationFrame.set(0, paramInt2 - i - j, paramInt1, paramInt2 - i);
            i = mTmpNavigationFrame.top;
            this.mStableFullscreenBottom = i;
            this.mStableBottom = i;
            if (!bool2)
              break label2788;
            this.mNavigationBarController.setBarShowingLw(true, true, paramInt4);
            label893: if ((i4 != 0) && (!bool1) && (n == 0) && (!localWindowState.isAnimatingLw()) && (!this.mNavigationBarController.wasRecentlyTranslucent(paramInt4)))
              this.mSystemBottom = mTmpNavigationFrame.top;
            i = this.mDockTop;
            this.mCurTop = i;
            this.mVoiceContentTop = i;
            this.mContentTop = i;
            i = this.mDockBottom;
            this.mCurBottom = i;
            this.mVoiceContentBottom = i;
            this.mContentBottom = i;
            i = this.mDockLeft;
            this.mCurLeft = i;
            this.mVoiceContentLeft = i;
            this.mContentLeft = i;
            i = this.mDockRight;
            this.mCurRight = i;
            this.mVoiceContentRight = i;
            this.mContentRight = i;
            this.mStatusBarLayer = localWindowState.getSurfaceLayer();
            localWindowState.computeFrameLw(mTmpNavigationFrame, mTmpNavigationFrame, mTmpNavigationFrame, mTmpNavigationFrame, mTmpNavigationFrame, localRect4, mTmpNavigationFrame, mTmpNavigationFrame);
            if (DEBUG_LAYOUT)
              Slog.i("WindowManager", "mNavigationBar frame: " + mTmpNavigationFrame);
            k = i2;
            if (this.mNavigationBarController.checkHiddenLw(paramInt4))
              k = 1;
          }
          if (DEBUG_LAYOUT)
            Slog.i("WindowManager", String.format("mDock rect: (%d,%d - %d,%d)", new Object[] { Integer.valueOf(this.mDockLeft), Integer.valueOf(this.mDockTop), Integer.valueOf(this.mDockRight), Integer.valueOf(this.mDockBottom) }));
          this.mCocktailPhoneWindowManager.beginLayoutLw(paramInt1, paramInt2, paramInt3, this.mOverscanScreenLeft, this.mOverscanScreenTop, this.mOverscanScreenWidth, this.mOverscanScreenHeight);
          if (this.mMobileKeyboardEnabled)
          {
            j = 0;
            i = 0;
            if (paramInt1 >= paramInt2)
              break label3045;
            i = this.mMobileKeyboardHeight;
            label1245: this.mSystemRight -= j;
            this.mSystemBottom -= i;
            n = this.mUnrestrictedScreenWidth - j;
            this.mUnrestrictedScreenWidth = n;
            this.mRestrictedScreenWidth = n;
            this.mRestrictedOverscanScreenWidth = n;
            n = this.mUnrestrictedScreenHeight - i;
            this.mUnrestrictedScreenHeight = n;
            this.mRestrictedScreenHeight = n;
            this.mRestrictedOverscanScreenHeight = n;
            j = this.mCurRight - j;
            this.mCurRight = j;
            this.mStableFullscreenRight = j;
            this.mStableRight = j;
            this.mContentRight = j;
            this.mDockRight = j;
            i = this.mCurBottom - i;
            this.mCurBottom = i;
            this.mStableFullscreenBottom = i;
            this.mStableBottom = i;
            this.mContentBottom = i;
            this.mDockBottom = i;
          }
          if (this.mCarModeBar != null)
            Slog.d("WindowManager", "beginLayoutLw mCarModeBar = " + this.mCarModeBar);
          switch (paramInt3)
          {
          case 2:
          default:
            this.mCarModeBarOnBottom = true;
            mTmpCarModeFrame.set(0, paramInt2 - this.mCarModeSize, paramInt1, paramInt2);
            label1484: if (isCarModeBarVisible())
            {
              if (!this.mCarModeBarOnBottom)
                break label3075;
              this.mSystemBottom -= this.mCarModeSize;
              i = this.mUnrestrictedScreenHeight - this.mCarModeSize;
              this.mUnrestrictedScreenHeight = i;
              this.mRestrictedOverscanScreenHeight = i;
              label1534: this.mRestrictedScreenLeft = this.mUnrestrictedScreenLeft;
              this.mRestrictedScreenTop = this.mUnrestrictedScreenTop;
              this.mRestrictedScreenWidth = this.mUnrestrictedScreenWidth;
              this.mRestrictedScreenHeight = this.mUnrestrictedScreenHeight;
              i = this.mUnrestrictedScreenLeft;
              this.mCurLeft = i;
              this.mStableFullscreenLeft = i;
              this.mStableLeft = i;
              this.mContentLeft = i;
              this.mDockLeft = i;
              if (this.mCarModeBarOnBottom)
              {
                i = this.mCurBottom - this.mCarModeSize;
                this.mCurBottom = i;
                this.mStableFullscreenBottom = i;
                this.mStableBottom = i;
                this.mContentBottom = i;
                this.mDockBottom = i;
              }
            }
            this.mCarModeBar.computeFrameLw(mTmpCarModeFrame, mTmpCarModeFrame, mTmpCarModeFrame, mTmpCarModeFrame, mTmpCarModeFrame, new Rect(), mTmpCarModeFrame, null);
            i = k;
            if (localObject2 != null)
            {
              i = this.mUnrestrictedScreenLeft;
              localRect2.left = i;
              localRect1.left = i;
              ((Rect)localObject1).left = i;
              i = this.mUnrestrictedScreenTop;
              localRect2.top = i;
              localRect1.top = i;
              ((Rect)localObject1).top = i;
              i = this.mUnrestrictedScreenWidth + this.mUnrestrictedScreenLeft;
              localRect2.right = i;
              localRect1.right = i;
              ((Rect)localObject1).right = i;
              i = this.mUnrestrictedScreenHeight + this.mUnrestrictedScreenTop;
              localRect2.bottom = i;
              localRect1.bottom = i;
              ((Rect)localObject1).bottom = i;
              localRect3.left = this.mStableLeft;
              localRect3.top = this.mStableTop;
              localRect3.right = this.mStableRight;
              localRect3.bottom = this.mStableBottom;
              if (CocktailBarFeatures.isSystemBarType(this.mContext))
              {
                i = this.mOverscanScreenLeft;
                localRect3.left = i;
                localRect1.left = i;
                ((Rect)localObject1).left = i;
                i = this.mOverscanScreenTop;
                localRect3.top = i;
                localRect1.top = i;
                ((Rect)localObject1).top = i;
                i = this.mOverscanScreenLeft + this.mOverscanScreenWidth;
                localRect3.right = i;
                localRect1.right = i;
                ((Rect)localObject1).right = i;
                i = this.mOverscanScreenTop + this.mOverscanScreenHeight;
                localRect3.bottom = i;
                localRect1.bottom = i;
                ((Rect)localObject1).bottom = i;
              }
              this.mStatusBarLayer = ((WindowManagerPolicy.WindowState)localObject2).getSurfaceLayer();
              ((WindowManagerPolicy.WindowState)localObject2).computeFrameLw((Rect)localObject1, localRect1, localRect3, localRect3, localRect3, localRect4, localRect3, localRect3);
              if (!this.mForceHideStatusBarForCocktail)
                this.mStableTop = (this.mUnrestrictedScreenTop + this.mStatusBarHeight);
              if ((0x4000000 & i3) == 0)
                break label3137;
              j = 1;
              label2042: if ((0x40008000 & i3) == 0)
                break label3143;
            }
          case 1:
          case 3:
          }
        }
      case 1:
      case 2:
      case 3:
      }
    label2699: label2705: label2711: label2717: label2723: label2729: label2735: label2741: label2744: label3137: label3143: for (int i = 1; ; i = 0)
    {
      n = i;
      if (m == 0)
        n = i & areTranslucentBarsAllowed();
      if ((((WindowManagerPolicy.WindowState)localObject2).isVisibleLw()) && (j == 0) && (m == 0))
      {
        this.mDockTop = (this.mUnrestrictedScreenTop + this.mStatusBarHeight);
        if ((this.mMultiPhoneWindowManager.isSplitTopApplicationWindow()) || (this.mForceHideStatusBarForCocktail))
          this.mDockTop -= this.mStatusBarHeight;
        i = this.mDockTop;
        this.mCurTop = i;
        this.mVoiceContentTop = i;
        this.mContentTop = i;
        i = this.mDockBottom;
        this.mCurBottom = i;
        this.mVoiceContentBottom = i;
        this.mContentBottom = i;
        i = this.mDockLeft;
        this.mCurLeft = i;
        this.mVoiceContentLeft = i;
        this.mContentLeft = i;
        i = this.mDockRight;
        this.mCurRight = i;
        this.mVoiceContentRight = i;
        this.mContentRight = i;
        if (DEBUG_LAYOUT)
          Slog.v("WindowManager", "Status bar: " + String.format("dock=[%d,%d][%d,%d] content=[%d,%d][%d,%d] cur=[%d,%d][%d,%d]", new Object[] { Integer.valueOf(this.mDockLeft), Integer.valueOf(this.mDockTop), Integer.valueOf(this.mDockRight), Integer.valueOf(this.mDockBottom), Integer.valueOf(this.mContentLeft), Integer.valueOf(this.mContentTop), Integer.valueOf(this.mContentRight), Integer.valueOf(this.mContentBottom), Integer.valueOf(this.mCurLeft), Integer.valueOf(this.mCurTop), Integer.valueOf(this.mCurRight), Integer.valueOf(this.mCurBottom) }));
      }
      if ((((WindowManagerPolicy.WindowState)localObject2).isVisibleLw()) && (!((WindowManagerPolicy.WindowState)localObject2).isAnimatingLw()) && (j == 0) && (n == 0) && (m == 0) && (!this.mStatusBarController.wasRecentlyTranslucent(paramInt4)))
        this.mSystemTop = (this.mUnrestrictedScreenTop + this.mStatusBarHeight);
      i = k;
      if (this.mStatusBarController.checkHiddenLw(paramInt4))
        i = 1;
      if (i != 0)
        updateSystemUiVisibilityLw();
      if ((isForceHideBySViewCover()) && (paramBoolean1))
        prepareSViewCoverLayout(paramInt3, paramInt1, paramInt2, this.mSystemLeft, this.mSystemTop, this.mSystemRight, this.mSystemBottom, this.mUnrestrictedScreenLeft, this.mUnrestrictedScreenTop, this.mUnrestrictedScreenWidth, this.mUnrestrictedScreenHeight, this.mStableFullscreenLeft, this.mStableFullscreenTop, this.mStableFullscreenRight, this.mStableFullscreenBottom, this.mStableLeft, this.mStableTop, this.mStableRight, this.mStableBottom, this.mDockLeft, this.mDockTop, this.mDockRight, this.mDockBottom, this.mCoverState.heightPixel, this.mCoverState.widthPixel);
      return;
      k = this.mOverscanTop;
      m = this.mOverscanRight;
      j = this.mOverscanBottom;
      i = this.mOverscanLeft;
      break;
      k = this.mOverscanRight;
      m = this.mOverscanBottom;
      j = this.mOverscanLeft;
      i = this.mOverscanTop;
      break;
      k = this.mOverscanBottom;
      m = this.mOverscanLeft;
      j = this.mOverscanTop;
      i = this.mOverscanRight;
      break;
      k = 0;
      m = 0;
      j = 0;
      i = 0;
      break;
      k = 0;
      break label613;
      m = 0;
      break label625;
      n = 0;
      break label637;
      bool1 = false;
      break label649;
      n = 0;
      break label662;
      bool1 = false;
      break label670;
      m = 0;
      break label701;
      break label715;
      if (localInputConsumer != null)
        break label760;
      this.mInputConsumer = this.mWindowManagerFuncs.addInputConsumer(this.mHandler.getLooper(), this.mHideNavInputEventReceiverFactory);
      break label760;
      label2776: i2 = 0;
      break label770;
      label2782: paramBoolean2 = false;
      break label815;
      label2788: if (i4 != 0)
      {
        this.mNavigationBarController.setBarShowingLw(true, true, paramInt4);
        this.mDockBottom = mTmpNavigationFrame.top;
        this.mRestrictedScreenHeight = (this.mDockBottom - this.mRestrictedScreenTop);
        this.mRestrictedOverscanScreenHeight = (this.mDockBottom - this.mRestrictedOverscanScreenTop);
        break label893;
      }
      this.mNavigationBarController.setBarShowingLw(false, true, paramInt4);
      break label893;
      i = this.mNavigationBarWidthForRotation[paramInt3];
      mTmpNavigationFrame.set(paramInt1 - j - i, 0, paramInt1 - j, paramInt2);
      i = mTmpNavigationFrame.left;
      this.mStableFullscreenRight = i;
      this.mStableRight = i;
      if (bool2)
        this.mNavigationBarController.setBarShowingLw(true, true, paramInt4);
      while ((i4 != 0) && (!bool1) && (n == 0) && (!localWindowState.isAnimatingLw()) && (!this.mNavigationBarController.wasRecentlyTranslucent(paramInt4)))
      {
        this.mSystemRight = mTmpNavigationFrame.left;
        break;
        if (i4 != 0)
        {
          this.mNavigationBarController.setBarShowingLw(true, true, paramInt4);
          this.mDockRight = mTmpNavigationFrame.left;
          this.mRestrictedScreenWidth = (this.mDockRight - this.mRestrictedScreenLeft);
          this.mRestrictedOverscanScreenWidth = (this.mDockRight - this.mRestrictedOverscanScreenLeft);
          continue;
        }
        this.mNavigationBarController.setBarShowingLw(false, true, paramInt4);
      }
      label3045: j = this.mMobileKeyboardHeight;
      break label1245;
      this.mCarModeBarOnBottom = false;
      mTmpCarModeFrame.set(0, 0, this.mCarModeSize, paramInt2);
      break label1484;
      this.mSystemLeft += this.mCarModeSize;
      i = this.mUnrestrictedScreenLeft + this.mCarModeSize;
      this.mUnrestrictedScreenLeft = i;
      this.mRestrictedOverscanScreenLeft = i;
      i = this.mUnrestrictedScreenWidth - this.mCarModeSize;
      this.mUnrestrictedScreenWidth = i;
      this.mRestrictedOverscanScreenWidth = i;
      break label1534;
      j = 0;
      break label2042;
    }
  }

  public void beginPostLayoutPolicyLw(int paramInt1, int paramInt2)
  {
    beginPostLayoutPolicyLw(paramInt1, paramInt2, 0, true);
  }

  public void beginPostLayoutPolicyLw(int paramInt1, int paramInt2, int paramInt3, boolean paramBoolean)
  {
    this.mTopFullscreenOpaqueWindowState = null;
    this.mTopFullscreenOpaqueOrDimmingWindowState = null;
    this.mWinShowWhenLocked = null;
    this.mAppsToBeHidden.clear();
    this.mAppsThatDismissKeyguard.clear();
    this.mForceStatusBar = false;
    this.mForceStatusBarFromKeyguard = false;
    this.mForceStatusBarTransparent = false;
    this.mForcingShowNavBar = false;
    this.mForcingShowNavBarLayer = -1;
    this.mHideSDKeyguard = false;
    this.mHideLockScreen = false;
    this.mAllowLockscreenWhenOn = false;
    this.mDismissKeyguard = 0;
    this.mShowingLockscreen = false;
    this.mShowingDream = false;
    this.mWinShowWhenLocked = null;
    this.mKeyguardSecure = isKeyguardSecure();
    if ((this.mKeyguardSecure) && (this.mKeyguardDelegate != null) && (this.mKeyguardDelegate.isShowing()));
    for (paramBoolean = true; ; paramBoolean = false)
    {
      this.mKeyguardSecureIncludingHidden = paramBoolean;
      this.mAppsShowWhenLocked.clear();
      this.mAppsToBeHiddenBySViewCover.clear();
      this.mForceStatusBarFromSViewCover = false;
      this.mShowingSViewCover = false;
      this.mHideSViewCoverWindowState = null;
      this.mHideSViewCover = 0;
      this.mHideLockScreenByCover = false;
      if ((this.mCoverState != null) && (!this.mCoverState.attached))
        this.mLastCoverAppCovered = false;
      this.mMultiPhoneWindowManager.beginPostLayoutPolicyLw();
      if (CocktailBarFeatures.isSystemBarType(this.mContext))
        this.mCocktailPhoneWindowManager.beginPostLayoutPolicyLw();
      if (this.mWatchLaunching)
        this.mHideLockScreen = true;
      this.mFakeFocusedWindow = null;
      return;
    }
  }

  public boolean canBeForceHidden(WindowManagerPolicy.WindowState paramWindowState, WindowManager.LayoutParams paramLayoutParams)
  {
    switch (paramLayoutParams.type)
    {
    default:
      if (windowTypeToLayerLw(paramWindowState.getBaseType()) >= windowTypeToLayerLw(2000))
        break;
    case 2009:
    case 2226:
    case 2270:
    case 2271:
      return true;
    case 2000:
    case 2013:
    case 2019:
    case 2023:
    case 2029:
    case 2098:
    case 2099:
    case 2200:
    case 2201:
    case 2220:
    case 2221:
      return false;
    }
    return false;
  }

  public boolean canBeForceHiddenByNightClock(WindowManagerPolicy.WindowState paramWindowState, WindowManager.LayoutParams paramLayoutParams)
  {
    int j = 1;
    int k = 0;
    int i = k;
    switch (paramLayoutParams.type)
    {
    default:
      if ((paramWindowState.getAppToken() == null) || ((paramLayoutParams.flags & 0x100000) == 0))
        break;
      i = 1;
    case 2013:
    case 2023:
    case 2029:
    case 2099:
    case 2220:
    case 2221:
    case 2225:
    case 2270:
    case 2271:
    }
    do
    {
      return i;
      i = k;
    }
    while (paramWindowState.getAppToken() != null);
    if (windowTypeToLayerLw(paramWindowState.getBaseType()) <= windowTypeToLayerLw(2000));
    for (i = j; ; i = 0)
      return i;
  }

  public boolean canBeForceHiddenBySViewCover(WindowManagerPolicy.WindowState paramWindowState, WindowManager.LayoutParams paramLayoutParams)
  {
    int k = 1;
    int j = 0;
    int i = paramWindowState.getCoverMode();
    if (i == 0)
      switch (paramLayoutParams.type)
      {
      default:
        if (windowTypeToLayerLw(paramWindowState.getBaseType()) >= windowTypeToLayerLw(2000))
          break;
        j = k;
      case 2000:
      case 2004:
      case 2005:
      case 2013:
      case 2019:
      case 2020:
      case 2099:
      case 2220:
      case 2221:
      case 2009:
      case 2226:
      }
    while (true)
    {
      return j;
      return true;
      if (i == 16)
        continue;
      if (this.mHideSViewCover == 0)
        break;
      if (i == 2)
      {
        if (!paramWindowState.willBeHideSViewCoverOnce())
          break;
        return false;
      }
      if (i != 1)
        break;
      return false;
      j = 0;
    }
  }

  public boolean canBeForceHiddenByVR(WindowManagerPolicy.WindowState paramWindowState, WindowManager.LayoutParams paramLayoutParams)
  {
    switch (paramLayoutParams.type)
    {
    default:
      return false;
    case 2000:
    case 2002:
    case 2003:
    case 2004:
    case 2006:
    case 2007:
    case 2008:
    case 2009:
    case 2010:
    case 2011:
    case 2012:
    case 2014:
    case 2019:
    case 2020:
    case 2029:
    case 2100:
    case 2101:
    case 2102:
    case 2103:
    case 2280:
    }
    return true;
  }

  public boolean canMagnifyWindow(int paramInt)
  {
    int i = 0;
    if (this.mEasyOneHandEnabled > 0)
    {
      switch (paramInt)
      {
      default:
        i = 1;
      case 2027:
      case 2250:
      case 2255:
      }
      return i;
    }
    switch (paramInt)
    {
    case 2011:
    case 2012:
    case 2019:
    case 2027:
    case 2250:
    case 2255:
    case 2280:
    }
    return true;
  }

  public void cancelPendingPowerKey()
  {
    cancelPendingPowerKeyAction();
  }

  public int checkAddPermission(WindowManager.LayoutParams paramLayoutParams, int[] paramArrayOfInt)
  {
    int j = 0;
    int k = paramLayoutParams.type;
    paramArrayOfInt[0] = -1;
    int i;
    if (((k < 1) || (k > 99)) && ((k < 1000) || (k > 1999)) && ((k < 2000) || (k > 2999)))
      i = -10;
    label61: Object localObject1;
    label469: 
    do
    {
      do
      {
        do
        {
          do
          {
            return i;
            i = j;
          }
          while (k < 2000);
          i = j;
        }
        while (k > 2999);
        i = j;
      }
      while (this.mMultiPhoneWindowManager.checkAddPermission(k) == 0);
      Object localObject2 = null;
      localObject1 = localObject2;
      switch (k)
      {
      default:
        localObject1 = "android.permission.INTERNAL_SYSTEM_WINDOW";
      case 2011:
      case 2013:
      case 2023:
      case 2030:
      case 2031:
      case 2032:
      case 2096:
      case 2097:
      case 2098:
      case 2100:
      case 2101:
      case 2262:
      case 2005:
      case 2002:
      case 2003:
      case 2006:
      case 2007:
      case 2010:
      case 2099:
      }
      while (true)
      {
        i = j;
        if (localObject1 == null)
          break;
        if (localObject1 != "android.permission.SYSTEM_ALERT_WINDOW")
          break label469;
        int m = Binder.getCallingUid();
        i = j;
        if (m == 1000)
          break;
        if (this.mSPWM.isMirrorLinkEnabled());
        switch (k)
        {
        default:
          i = j;
          switch (this.mAppOpsManager.checkOp(paramArrayOfInt[0], m, paramLayoutParams.packageName))
          {
          case 0:
          case 1:
          default:
            i = j;
            if (this.mContext.checkCallingPermission((String)localObject1) == 0)
              break label61;
            return -8;
            paramArrayOfInt[0] = 45;
            localObject1 = localObject2;
            continue;
            localObject1 = "android.permission.SYSTEM_ALERT_WINDOW";
            paramArrayOfInt[0] = 24;
          case 2:
          }
        case 2002:
        case 2006:
        }
      }
      Log.w("WindowManager", "checkAddPermission : Blocked by MirrorLink - Window Type : " + k);
      return -100;
      return -8;
      i = j;
    }
    while (this.mContext.checkCallingOrSelfPermission((String)localObject1) == 0);
    return -8;
  }

  public boolean checkShowToOwnerOnly(WindowManager.LayoutParams paramLayoutParams)
  {
    switch (paramLayoutParams.type)
    {
    default:
      if ((paramLayoutParams.privateFlags & 0x10) != 0)
        break;
    case 3:
    case 2000:
    case 2001:
    case 2002:
    case 2007:
    case 2008:
    case 2009:
    case 2014:
    case 2017:
    case 2018:
    case 2019:
    case 2020:
    case 2021:
    case 2022:
    case 2024:
    case 2026:
    case 2027:
    case 2029:
    case 2030:
    case 2099:
    }
    do
      return true;
    while (this.mContext.checkCallingOrSelfPermission("android.permission.INTERNAL_SYSTEM_WINDOW") != 0);
    return false;
  }

  public boolean checkTopFullscreenOpaqueWindowState(WindowManagerPolicy.WindowState paramWindowState, WindowManager.LayoutParams paramLayoutParams)
  {
    if (paramLayoutParams.type == 98);
    do
    {
      do
        return false;
      while (paramWindowState.getMultiWindowStyleLw().isCascade());
      if ((!this.mNavigationBarCanMove) && ((paramLayoutParams.samsungFlags & 0x20) != 0))
        return true;
    }
    while ((paramLayoutParams.x != 0) || (paramLayoutParams.y != 0) || (paramLayoutParams.width != -1) || (paramLayoutParams.height != -1));
    return true;
  }

  public boolean closeMultiWindowTrayBar(boolean paramBoolean)
  {
    return this.mMultiPhoneWindowManager.closeMultiWindowTrayBar(paramBoolean);
  }

  public void closeSystemWindows(String paramString)
  {
    sendCloseSystemWindows(this.mContext, paramString);
  }

  public Animation createForceHideEnterAnimation(boolean paramBoolean1, boolean paramBoolean2)
  {
    if (paramBoolean2)
    {
      localObject = AnimationUtils.loadAnimation(this.mContext, 17432642);
      return localObject;
    }
    Object localObject = this.mContext;
    if (paramBoolean1);
    for (int i = 17432643; ; i = 17432641)
    {
      AnimationSet localAnimationSet = (AnimationSet)AnimationUtils.loadAnimation((Context)localObject, i);
      List localList = localAnimationSet.getAnimations();
      i = localList.size() - 1;
      while (true)
      {
        localObject = localAnimationSet;
        if (i < 0)
          break;
        ((Animation)localList.get(i)).setInterpolator(this.mLogDecelerateInterpolator);
        i -= 1;
      }
    }
  }

  public Animation createForceHideWallpaperExitAnimation(boolean paramBoolean)
  {
    if (paramBoolean)
      return null;
    return AnimationUtils.loadAnimation(this.mContext, 17432646);
  }

  Intent createHomeDockIntent()
  {
    ActivityInfo localActivityInfo = null;
    Object localObject;
    if (this.mUiMode == 3)
    {
      localObject = this.mCarDockIntent;
      if (this.mDockMode != 104)
        break label176;
      localObject = this.mMirrorLinkDockIntent;
    }
    label176: 
    while (true)
    {
      if (localObject == null)
      {
        return null;
        if (this.mUiMode == 2)
        {
          localObject = this.mDeskDockIntent;
          break;
        }
        localObject = localActivityInfo;
        if (this.mUiMode != 6)
          break;
        if ((this.mDockMode != 1) && (this.mDockMode != 4))
        {
          localObject = localActivityInfo;
          if (this.mDockMode != 3)
            break;
        }
        localObject = this.mDeskDockIntent;
        break;
      }
      localActivityInfo = null;
      ResolveInfo localResolveInfo = this.mContext.getPackageManager().resolveActivityAsUser((Intent)localObject, 65664, this.mCurrentUserId);
      if (localResolveInfo != null)
        localActivityInfo = localResolveInfo.activityInfo;
      if ((localActivityInfo != null) && (localActivityInfo.metaData != null) && (localActivityInfo.metaData.getBoolean("android.dock_home")))
      {
        localObject = new Intent((Intent)localObject);
        ((Intent)localObject).setClassName(localActivityInfo.packageName, localActivityInfo.name);
        return localObject;
      }
      return null;
    }
  }

  public void dismissKeyguardLw()
  {
    if ((this.mKeyguardDelegate != null) && (this.mKeyguardDelegate.isShowing()))
    {
      if (DEBUG_KEYGUARD)
        Slog.d("WindowManager", "PWM.dismissKeyguardLw");
      this.mHandler.post(new Runnable()
      {
        public void run()
        {
          PhoneWindowManager.this.mKeyguardDelegate.dismiss();
        }
      });
    }
  }

  void dispatchMediaKeyRepeatWithWakeLock(KeyEvent paramKeyEvent)
  {
    this.mHavePendingMediaKeyRepeatWithWakeLock = false;
    paramKeyEvent = KeyEvent.changeTimeRepeat(paramKeyEvent, SystemClock.uptimeMillis(), 1, paramKeyEvent.getFlags() | 0x80);
    if (DEBUG_INPUT)
      Slog.d("WindowManager", "dispatchMediaKeyRepeatWithWakeLock: " + paramKeyEvent);
    dispatchMediaKeyWithWakeLockToAudioService(paramKeyEvent);
    this.mBroadcastWakeLock.release();
  }

  void dispatchMediaKeyWithWakeLock(KeyEvent paramKeyEvent)
  {
    if (DEBUG_INPUT)
      Slog.d("WindowManager", "dispatchMediaKeyWithWakeLock: " + paramKeyEvent);
    if (this.mHavePendingMediaKeyRepeatWithWakeLock)
    {
      if (DEBUG_INPUT)
        Slog.d("WindowManager", "dispatchMediaKeyWithWakeLock: canceled repeat");
      this.mHandler.removeMessages(4);
      this.mHavePendingMediaKeyRepeatWithWakeLock = false;
      this.mBroadcastWakeLock.release();
    }
    dispatchMediaKeyWithWakeLockToAudioService(paramKeyEvent);
    if ((paramKeyEvent.getAction() == 0) && (paramKeyEvent.getRepeatCount() == 0))
    {
      this.mHavePendingMediaKeyRepeatWithWakeLock = true;
      paramKeyEvent = this.mHandler.obtainMessage(4, paramKeyEvent);
      paramKeyEvent.setAsynchronous(true);
      this.mHandler.sendMessageDelayed(paramKeyEvent, ViewConfiguration.getKeyRepeatTimeout());
      return;
    }
    this.mBroadcastWakeLock.release();
  }

  void dispatchMediaKeyWithWakeLockToAudioService(KeyEvent paramKeyEvent)
  {
    if (ActivityManagerNative.isSystemReady())
      MediaSessionLegacyHelper.getHelper(this.mContext).sendMediaButtonEvent(paramKeyEvent, true);
  }

  public KeyEvent dispatchUnhandledKey(WindowManagerPolicy.WindowState paramWindowState, KeyEvent paramKeyEvent, int paramInt)
  {
    if (DEBUG_INPUT)
      Slog.d("WindowManager", "Unhandled key: win=" + paramWindowState + ", action=" + paramKeyEvent.getAction() + ", flags=" + paramKeyEvent.getFlags() + ", keyCode=" + paramKeyEvent.getKeyCode() + ", scanCode=" + paramKeyEvent.getScanCode() + ", metaState=" + paramKeyEvent.getMetaState() + ", repeatCount=" + paramKeyEvent.getRepeatCount() + ", policyFlags=" + paramInt);
    Object localObject1 = null;
    Object localObject3 = localObject1;
    Object localObject2;
    int j;
    int i;
    if ((paramKeyEvent.getFlags() & 0x400) == 0)
    {
      localObject2 = paramKeyEvent.getKeyCharacterMap();
      j = paramKeyEvent.getKeyCode();
      int k = paramKeyEvent.getMetaState();
      if ((paramKeyEvent.getAction() != 0) || (paramKeyEvent.getRepeatCount() != 0))
        break label380;
      i = 1;
      if (i == 0)
        break label386;
      localObject2 = ((KeyCharacterMap)localObject2).getFallbackAction(j, k);
      label190: localObject3 = localObject1;
      if (localObject2 != null)
      {
        if (DEBUG_INPUT)
          Slog.d("WindowManager", "Fallback: keyCode=" + ((KeyCharacterMap.FallbackAction)localObject2).keyCode + " metaState=" + Integer.toHexString(((KeyCharacterMap.FallbackAction)localObject2).metaState));
        k = paramKeyEvent.getFlags();
        localObject3 = KeyEvent.obtain(paramKeyEvent.getDownTime(), paramKeyEvent.getEventTime(), paramKeyEvent.getAction(), ((KeyCharacterMap.FallbackAction)localObject2).keyCode, paramKeyEvent.getRepeatCount(), ((KeyCharacterMap.FallbackAction)localObject2).metaState, paramKeyEvent.getDeviceId(), paramKeyEvent.getScanCode(), k | 0x400, paramKeyEvent.getDisplayId(), paramKeyEvent.getSource(), null);
        localObject1 = localObject3;
        if (!interceptFallback(paramWindowState, (KeyEvent)localObject3, paramInt))
        {
          ((KeyEvent)localObject3).recycle();
          localObject1 = null;
        }
        if (i == 0)
          break label403;
        this.mFallbackActions.put(j, localObject2);
        localObject3 = localObject1;
      }
    }
    while (true)
    {
      if (DEBUG_INPUT)
      {
        if (localObject3 != null)
          break label436;
        Slog.d("WindowManager", "No fallback.");
      }
      return localObject3;
      label380: i = 0;
      break;
      label386: localObject2 = (KeyCharacterMap.FallbackAction)this.mFallbackActions.get(j);
      break label190;
      label403: localObject3 = localObject1;
      if (paramKeyEvent.getAction() != 1)
        continue;
      this.mFallbackActions.remove(j);
      ((KeyCharacterMap.FallbackAction)localObject2).recycle();
      localObject3 = localObject1;
    }
    label436: Slog.d("WindowManager", "Performing fallback: " + localObject3);
    return (KeyEvent)(KeyEvent)localObject3;
  }

  public void dump(String paramString, PrintWriter paramPrintWriter, String[] paramArrayOfString)
  {
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mSafeMode=");
    paramPrintWriter.print(this.mSafeMode);
    paramPrintWriter.print(" mSystemReady=");
    paramPrintWriter.print(this.mSystemReady);
    paramPrintWriter.print(" mSystemBooted=");
    paramPrintWriter.println(this.mSystemBooted);
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mLidState=");
    paramPrintWriter.print(this.mLidState);
    paramPrintWriter.print(" mLidOpenRotation=");
    paramPrintWriter.print(this.mLidOpenRotation);
    paramPrintWriter.print(" mCameraLensCoverState=");
    paramPrintWriter.print(this.mCameraLensCoverState);
    paramPrintWriter.print(" mHdmiPlugged=");
    paramPrintWriter.println(this.mHdmiPlugged);
    if ((this.mLastSystemUiFlags != 0) || (this.mResettingSystemUiFlags != 0) || (this.mForceClearedSystemUiFlags != 0))
    {
      paramPrintWriter.print(paramString);
      paramPrintWriter.print("mLastSystemUiFlags=0x");
      paramPrintWriter.print(Integer.toHexString(this.mLastSystemUiFlags));
      paramPrintWriter.print(" mResettingSystemUiFlags=0x");
      paramPrintWriter.print(Integer.toHexString(this.mResettingSystemUiFlags));
      paramPrintWriter.print(" mForceClearedSystemUiFlags=0x");
      paramPrintWriter.println(Integer.toHexString(this.mForceClearedSystemUiFlags));
    }
    if (this.mLastFocusNeedsMenu)
    {
      paramPrintWriter.print(paramString);
      paramPrintWriter.print("mLastFocusNeedsMenu=");
      paramPrintWriter.println(this.mLastFocusNeedsMenu);
    }
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mWakeGestureEnabledSetting=");
    paramPrintWriter.println(this.mWakeGestureEnabledSetting);
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mSupportAutoRotation=");
    paramPrintWriter.println(this.mSupportAutoRotation);
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mUiMode=");
    paramPrintWriter.print(this.mUiMode);
    paramPrintWriter.print(" mDockMode=");
    paramPrintWriter.print(this.mDockMode);
    paramPrintWriter.print(" mCarDockRotation=");
    paramPrintWriter.print(this.mCarDockRotation);
    paramPrintWriter.print(" mDeskDockRotation=");
    paramPrintWriter.println(this.mDeskDockRotation);
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mUserRotationMode=");
    paramPrintWriter.print(this.mUserRotationMode);
    paramPrintWriter.print(" mUserRotation=");
    paramPrintWriter.print(this.mUserRotation);
    paramPrintWriter.print(" mAllowAllRotations=");
    paramPrintWriter.println(this.mAllowAllRotations);
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mCurrentAppOrientation=");
    paramPrintWriter.println(this.mCurrentAppOrientation);
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mCarDockEnablesAccelerometer=");
    paramPrintWriter.print(this.mCarDockEnablesAccelerometer);
    paramPrintWriter.print(" mDeskDockEnablesAccelerometer=");
    paramPrintWriter.println(this.mDeskDockEnablesAccelerometer);
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mLidKeyboardAccessibility=");
    paramPrintWriter.print(this.mLidKeyboardAccessibility);
    paramPrintWriter.print(" mLidNavigationAccessibility=");
    paramPrintWriter.print(this.mLidNavigationAccessibility);
    paramPrintWriter.print(" mLidControlsSleep=");
    paramPrintWriter.println(this.mLidControlsSleep);
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mShortPressOnPowerBehavior=");
    paramPrintWriter.print(this.mShortPressOnPowerBehavior);
    paramPrintWriter.print(" mLongPressOnPowerBehavior=");
    paramPrintWriter.println(this.mLongPressOnPowerBehavior);
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mDoublePressOnPowerBehavior=");
    paramPrintWriter.print(this.mDoublePressOnPowerBehavior);
    paramPrintWriter.print(" mTriplePressOnPowerBehavior=");
    paramPrintWriter.println(this.mTriplePressOnPowerBehavior);
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mHasSoftInput=");
    paramPrintWriter.println(this.mHasSoftInput);
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mAwake=");
    paramPrintWriter.println(this.mAwake);
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mScreenOnEarly=");
    paramPrintWriter.print(this.mScreenOnEarly);
    paramPrintWriter.print(" mScreenOnFully=");
    paramPrintWriter.println(this.mScreenOnFully);
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mKeyguardDrawComplete=");
    paramPrintWriter.print(this.mKeyguardDrawComplete);
    paramPrintWriter.print(" mWindowManagerDrawComplete=");
    paramPrintWriter.println(this.mWindowManagerDrawComplete);
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mOrientationSensorEnabled=");
    paramPrintWriter.println(this.mOrientationSensorEnabled);
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mOverscanScreen=(");
    paramPrintWriter.print(this.mOverscanScreenLeft);
    paramPrintWriter.print(",");
    paramPrintWriter.print(this.mOverscanScreenTop);
    paramPrintWriter.print(") ");
    paramPrintWriter.print(this.mOverscanScreenWidth);
    paramPrintWriter.print("x");
    paramPrintWriter.println(this.mOverscanScreenHeight);
    if ((this.mOverscanLeft != 0) || (this.mOverscanTop != 0) || (this.mOverscanRight != 0) || (this.mOverscanBottom != 0))
    {
      paramPrintWriter.print(paramString);
      paramPrintWriter.print("mOverscan left=");
      paramPrintWriter.print(this.mOverscanLeft);
      paramPrintWriter.print(" top=");
      paramPrintWriter.print(this.mOverscanTop);
      paramPrintWriter.print(" right=");
      paramPrintWriter.print(this.mOverscanRight);
      paramPrintWriter.print(" bottom=");
      paramPrintWriter.println(this.mOverscanBottom);
    }
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mRestrictedOverscanScreen=(");
    paramPrintWriter.print(this.mRestrictedOverscanScreenLeft);
    paramPrintWriter.print(",");
    paramPrintWriter.print(this.mRestrictedOverscanScreenTop);
    paramPrintWriter.print(") ");
    paramPrintWriter.print(this.mRestrictedOverscanScreenWidth);
    paramPrintWriter.print("x");
    paramPrintWriter.println(this.mRestrictedOverscanScreenHeight);
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mUnrestrictedScreen=(");
    paramPrintWriter.print(this.mUnrestrictedScreenLeft);
    paramPrintWriter.print(",");
    paramPrintWriter.print(this.mUnrestrictedScreenTop);
    paramPrintWriter.print(") ");
    paramPrintWriter.print(this.mUnrestrictedScreenWidth);
    paramPrintWriter.print("x");
    paramPrintWriter.println(this.mUnrestrictedScreenHeight);
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mRestrictedScreen=(");
    paramPrintWriter.print(this.mRestrictedScreenLeft);
    paramPrintWriter.print(",");
    paramPrintWriter.print(this.mRestrictedScreenTop);
    paramPrintWriter.print(") ");
    paramPrintWriter.print(this.mRestrictedScreenWidth);
    paramPrintWriter.print("x");
    paramPrintWriter.println(this.mRestrictedScreenHeight);
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mStableFullscreen=(");
    paramPrintWriter.print(this.mStableFullscreenLeft);
    paramPrintWriter.print(",");
    paramPrintWriter.print(this.mStableFullscreenTop);
    paramPrintWriter.print(")-(");
    paramPrintWriter.print(this.mStableFullscreenRight);
    paramPrintWriter.print(",");
    paramPrintWriter.print(this.mStableFullscreenBottom);
    paramPrintWriter.println(")");
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mStable=(");
    paramPrintWriter.print(this.mStableLeft);
    paramPrintWriter.print(",");
    paramPrintWriter.print(this.mStableTop);
    paramPrintWriter.print(")-(");
    paramPrintWriter.print(this.mStableRight);
    paramPrintWriter.print(",");
    paramPrintWriter.print(this.mStableBottom);
    paramPrintWriter.println(")");
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mSystem=(");
    paramPrintWriter.print(this.mSystemLeft);
    paramPrintWriter.print(",");
    paramPrintWriter.print(this.mSystemTop);
    paramPrintWriter.print(")-(");
    paramPrintWriter.print(this.mSystemRight);
    paramPrintWriter.print(",");
    paramPrintWriter.print(this.mSystemBottom);
    paramPrintWriter.println(")");
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mCur=(");
    paramPrintWriter.print(this.mCurLeft);
    paramPrintWriter.print(",");
    paramPrintWriter.print(this.mCurTop);
    paramPrintWriter.print(")-(");
    paramPrintWriter.print(this.mCurRight);
    paramPrintWriter.print(",");
    paramPrintWriter.print(this.mCurBottom);
    paramPrintWriter.println(")");
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mContent=(");
    paramPrintWriter.print(this.mContentLeft);
    paramPrintWriter.print(",");
    paramPrintWriter.print(this.mContentTop);
    paramPrintWriter.print(")-(");
    paramPrintWriter.print(this.mContentRight);
    paramPrintWriter.print(",");
    paramPrintWriter.print(this.mContentBottom);
    paramPrintWriter.println(")");
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mVoiceContent=(");
    paramPrintWriter.print(this.mVoiceContentLeft);
    paramPrintWriter.print(",");
    paramPrintWriter.print(this.mVoiceContentTop);
    paramPrintWriter.print(")-(");
    paramPrintWriter.print(this.mVoiceContentRight);
    paramPrintWriter.print(",");
    paramPrintWriter.print(this.mVoiceContentBottom);
    paramPrintWriter.println(")");
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mDock=(");
    paramPrintWriter.print(this.mDockLeft);
    paramPrintWriter.print(",");
    paramPrintWriter.print(this.mDockTop);
    paramPrintWriter.print(")-(");
    paramPrintWriter.print(this.mDockRight);
    paramPrintWriter.print(",");
    paramPrintWriter.print(this.mDockBottom);
    paramPrintWriter.println(")");
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mDockLayer=");
    paramPrintWriter.print(this.mDockLayer);
    paramPrintWriter.print(" mStatusBarLayer=");
    paramPrintWriter.println(this.mStatusBarLayer);
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mShowingLockscreen=");
    paramPrintWriter.print(this.mShowingLockscreen);
    paramPrintWriter.print(" mShowingDream=");
    paramPrintWriter.print(this.mShowingDream);
    paramPrintWriter.print(" mDreamingLockscreen=");
    paramPrintWriter.print(this.mDreamingLockscreen);
    paramPrintWriter.print(" mDreamingSleepToken=");
    paramPrintWriter.println(this.mDreamingSleepToken);
    if (this.mLastInputMethodWindow != null)
    {
      paramPrintWriter.print(paramString);
      paramPrintWriter.print("mLastInputMethodWindow=");
      paramPrintWriter.println(this.mLastInputMethodWindow);
    }
    if (this.mLastInputMethodTargetWindow != null)
    {
      paramPrintWriter.print(paramString);
      paramPrintWriter.print("mLastInputMethodTargetWindow=");
      paramPrintWriter.println(this.mLastInputMethodTargetWindow);
    }
    if (this.mStatusBar != null)
    {
      paramPrintWriter.print(paramString);
      paramPrintWriter.print("mStatusBar=");
      paramPrintWriter.print(this.mStatusBar);
      paramPrintWriter.print(" isStatusBarKeyguard=");
      paramPrintWriter.println(isStatusBarKeyguard());
    }
    if (this.mNavigationBar != null)
    {
      paramPrintWriter.print(paramString);
      paramPrintWriter.print("mNavigationBar=");
      paramPrintWriter.println(this.mNavigationBar);
    }
    if (this.mCarModeBar != null)
    {
      paramPrintWriter.print(paramString);
      paramPrintWriter.print("mCarModeBar=");
      paramPrintWriter.println(this.mCarModeBar);
    }
    if (this.mNightClock != null)
    {
      paramPrintWriter.print(paramString);
      paramPrintWriter.print("mNightClock=");
      paramPrintWriter.println(this.mNightClock);
    }
    if (this.mFocusedWindow != null)
    {
      paramPrintWriter.print(paramString);
      paramPrintWriter.print("mFocusedWindow=");
      paramPrintWriter.println(this.mFocusedWindow);
    }
    if (this.mFakeFocusedWindow != null)
    {
      paramPrintWriter.print(paramString);
      paramPrintWriter.print("mFakeFocusedWindow=");
      paramPrintWriter.println(this.mFakeFocusedWindow);
    }
    if (this.mFocusedApp != null)
    {
      paramPrintWriter.print(paramString);
      paramPrintWriter.print("mFocusedApp=");
      paramPrintWriter.println(this.mFocusedApp);
    }
    if (this.mWinDismissingKeyguard != null)
    {
      paramPrintWriter.print(paramString);
      paramPrintWriter.print("mWinDismissingKeyguard=");
      paramPrintWriter.println(this.mWinDismissingKeyguard);
    }
    if (this.mTopFullscreenOpaqueWindowState != null)
    {
      paramPrintWriter.print(paramString);
      paramPrintWriter.print("mTopFullscreenOpaqueWindowState=");
      paramPrintWriter.println(this.mTopFullscreenOpaqueWindowState);
    }
    if (this.mTopFullscreenOpaqueOrDimmingWindowState != null)
    {
      paramPrintWriter.print(paramString);
      paramPrintWriter.print("mTopFullscreenOpaqueOrDimmingWindowState=");
      paramPrintWriter.println(this.mTopFullscreenOpaqueOrDimmingWindowState);
    }
    if (this.mForcingShowNavBar)
    {
      paramPrintWriter.print(paramString);
      paramPrintWriter.print("mForcingShowNavBar=");
      paramPrintWriter.println(this.mForcingShowNavBar);
      paramPrintWriter.print("mForcingShowNavBarLayer=");
      paramPrintWriter.println(this.mForcingShowNavBarLayer);
    }
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mTopIsFullscreen=");
    paramPrintWriter.print(this.mTopIsFullscreen);
    paramPrintWriter.print(" mHideLockScreen=");
    paramPrintWriter.println(this.mHideLockScreen);
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mForceStatusBar=");
    paramPrintWriter.print(this.mForceStatusBar);
    paramPrintWriter.print(" mForceStatusBarFromKeyguard=");
    paramPrintWriter.println(this.mForceStatusBarFromKeyguard);
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mDismissKeyguard=");
    paramPrintWriter.print(this.mDismissKeyguard);
    paramPrintWriter.print(" mWinDismissingKeyguard=");
    paramPrintWriter.print(this.mWinDismissingKeyguard);
    paramPrintWriter.print(" mHomePressed=");
    paramPrintWriter.println(this.mHomePressed);
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mAllowLockscreenWhenOn=");
    paramPrintWriter.print(this.mAllowLockscreenWhenOn);
    paramPrintWriter.print(" mLockScreenTimeout=");
    paramPrintWriter.print(this.mLockScreenTimeout);
    paramPrintWriter.print(" mLockScreenTimerActive=");
    paramPrintWriter.println(this.mLockScreenTimerActive);
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mEndcallBehavior=");
    paramPrintWriter.print(this.mEndcallBehavior);
    paramPrintWriter.print(" mIncallPowerBehavior=");
    paramPrintWriter.print(this.mIncallPowerBehavior);
    paramPrintWriter.print(" mLongPressOnHomeBehavior=");
    paramPrintWriter.println(this.mLongPressOnHomeBehavior);
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mLandscapeRotation=");
    paramPrintWriter.print(this.mLandscapeRotation);
    paramPrintWriter.print(" mSeascapeRotation=");
    paramPrintWriter.println(this.mSeascapeRotation);
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mPortraitRotation=");
    paramPrintWriter.print(this.mPortraitRotation);
    paramPrintWriter.print(" mUpsideDownRotation=");
    paramPrintWriter.println(this.mUpsideDownRotation);
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mDemoHdmiRotation=");
    paramPrintWriter.print(this.mDemoHdmiRotation);
    paramPrintWriter.print(" mDemoHdmiRotationLock=");
    paramPrintWriter.println(this.mDemoHdmiRotationLock);
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mUndockedHdmiRotation=");
    paramPrintWriter.println(this.mUndockedHdmiRotation);
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mIsDefaultKeyguardRotationAnmationAlwaysUsed=");
    paramPrintWriter.println(this.mIsDefaultKeyguardRotationAnmationAlwaysUsed);
    this.mGlobalKeyManager.dump(paramString, paramPrintWriter);
    this.mStatusBarController.dump(paramPrintWriter, paramString);
    this.mNavigationBarController.dump(paramPrintWriter, paramString);
    PolicyControl.dump(paramString, paramPrintWriter);
    if (this.mWakeGestureListener != null)
      this.mWakeGestureListener.dump(paramPrintWriter, paramString);
    if (this.mOrientationListener != null)
      this.mOrientationListener.dump(paramPrintWriter, paramString);
    if (this.mBurnInProtectionHelper != null)
      this.mBurnInProtectionHelper.dump(paramString, paramPrintWriter);
    if (this.mKeyguardDelegate != null)
      this.mKeyguardDelegate.dump(paramString, paramPrintWriter);
    paramPrintWriter.println("");
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mMobileKeyboardEnabled=");
    paramPrintWriter.println(this.mMobileKeyboardEnabled);
    this.mSPWM.dump(paramString, paramPrintWriter, paramArrayOfString);
    this.mSystemKeyManager.dump(paramString, paramPrintWriter);
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mCoverCloseRotation=");
    paramPrintWriter.println(this.mCoverCloseRotation);
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mIsSupportFlipCover=");
    paramPrintWriter.println(this.mIsSupportFlipCover);
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mIsSupportSViewCover=");
    paramPrintWriter.println(this.mIsSupportSViewCover);
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mHideSViewCoverWindowState=");
    paramPrintWriter.println(this.mHideSViewCoverWindowState);
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("OriginalmUnrestrictedScreen=(");
    paramPrintWriter.print(this.mOriginalUnrestrictedScreenLeft);
    paramPrintWriter.print(",");
    paramPrintWriter.print(this.mOriginalUnrestrictedScreenTop);
    paramPrintWriter.print(") ");
    paramPrintWriter.print(this.mOriginalUnrestrictedScreenWidth);
    paramPrintWriter.print("x");
    paramPrintWriter.println(this.mOriginalUnrestrictedScreenHeight);
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mOriginalStableFullscreen=(");
    paramPrintWriter.print(this.mOriginalStableFullscreenLeft);
    paramPrintWriter.print(",");
    paramPrintWriter.print(this.mOriginalStableFullscreenTop);
    paramPrintWriter.print(")-(");
    paramPrintWriter.print(this.mOriginalStableFullscreenRight);
    paramPrintWriter.print(",");
    paramPrintWriter.print(this.mOriginalStableFullscreenBottom);
    paramPrintWriter.println(")");
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mOriginalStable=(");
    paramPrintWriter.print(this.mOriginalStableLeft);
    paramPrintWriter.print(",");
    paramPrintWriter.print(this.mOriginalStableTop);
    paramPrintWriter.print(")-(");
    paramPrintWriter.print(this.mOriginalStableRight);
    paramPrintWriter.print(",");
    paramPrintWriter.print(this.mOriginalStableBottom);
    paramPrintWriter.println(")");
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mOriginalSystem=(");
    paramPrintWriter.print(this.mOriginalSystemLeft);
    paramPrintWriter.print(",");
    paramPrintWriter.print(this.mOriginalSystemTop);
    paramPrintWriter.print(")-(");
    paramPrintWriter.print(this.mOriginalSystemRight);
    paramPrintWriter.print(",");
    paramPrintWriter.print(this.mOriginalSystemBottom);
    paramPrintWriter.println(")");
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mOriginalDock=(");
    paramPrintWriter.print(this.mOriginalDockLeft);
    paramPrintWriter.print(",");
    paramPrintWriter.print(this.mOriginalDockTop);
    paramPrintWriter.print(")-(");
    paramPrintWriter.print(this.mOriginalDockRight);
    paramPrintWriter.print(",");
    paramPrintWriter.print(this.mOriginalDockBottom);
    paramPrintWriter.println(")");
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mSViewCoverUnrestrictedScreen=(");
    paramPrintWriter.print(this.mSViewCoverUnrestrictedScreenLeft);
    paramPrintWriter.print(",");
    paramPrintWriter.print(this.mSViewCoverUnrestrictedScreenTop);
    paramPrintWriter.print(") ");
    paramPrintWriter.print(this.mSViewCoverUnrestrictedScreenWidth);
    paramPrintWriter.print("x");
    paramPrintWriter.println(this.mSViewCoverUnrestrictedScreenHeight);
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mSViewCoverStableFullscreen=(");
    paramPrintWriter.print(this.mSViewCoverStableFullscreenLeft);
    paramPrintWriter.print(",");
    paramPrintWriter.print(this.mSViewCoverStableFullscreenTop);
    paramPrintWriter.print(")-(");
    paramPrintWriter.print(this.mSViewCoverStableFullscreenRight);
    paramPrintWriter.print(",");
    paramPrintWriter.print(this.mSViewCoverStableFullscreenBottom);
    paramPrintWriter.println(")");
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mSViewCoverStable=(");
    paramPrintWriter.print(this.mSViewCoverStableLeft);
    paramPrintWriter.print(",");
    paramPrintWriter.print(this.mSViewCoverStableTop);
    paramPrintWriter.print(")-(");
    paramPrintWriter.print(this.mSViewCoverStableRight);
    paramPrintWriter.print(",");
    paramPrintWriter.print(this.mSViewCoverStableBottom);
    paramPrintWriter.println(")");
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mSViewCoverSystem=(");
    paramPrintWriter.print(this.mSViewCoverSystemLeft);
    paramPrintWriter.print(",");
    paramPrintWriter.print(this.mSViewCoverSystemTop);
    paramPrintWriter.print(")-(");
    paramPrintWriter.print(this.mSViewCoverSystemRight);
    paramPrintWriter.print(",");
    paramPrintWriter.print(this.mSViewCoverSystemBottom);
    paramPrintWriter.println(")");
    paramPrintWriter.print(paramString);
    paramPrintWriter.print("mSViewCoverDock=(");
    paramPrintWriter.print(this.mSViewCoverDockLeft);
    paramPrintWriter.print(",");
    paramPrintWriter.print(this.mSViewCoverDockTop);
    paramPrintWriter.print(")-(");
    paramPrintWriter.print(this.mSViewCoverDockRight);
    paramPrintWriter.print(",");
    paramPrintWriter.print(this.mSViewCoverDockBottom);
    paramPrintWriter.println(")");
    this.mMultiPhoneWindowManager.dump(paramString, paramPrintWriter);
    this.mCocktailPhoneWindowManager.dump(paramString, paramPrintWriter);
  }

  public void enableKeyguard(boolean paramBoolean)
  {
    if (this.mKeyguardDelegate != null)
      this.mKeyguardDelegate.setKeyguardEnabled(paramBoolean);
  }

  public void enableScreenAfterBoot()
  {
    boolean bool = true;
    readLidState();
    applyLidSwitchState();
    updateRotation(true);
    KeyguardServiceDelegate localKeyguardServiceDelegate;
    if ((SamsungPolicyProperties.FolderTypeFeature(this.mContext) == 2) && (this.mKeyguardDelegate != null))
    {
      localKeyguardServiceDelegate = this.mKeyguardDelegate;
      if (this.mLidState != 1)
        break label52;
    }
    while (true)
    {
      localKeyguardServiceDelegate.changeLidState(bool);
      return;
      label52: bool = false;
    }
  }

  public void exitKeyguardSecurely(WindowManagerPolicy.OnKeyguardExitResult paramOnKeyguardExitResult)
  {
    if (this.mKeyguardDelegate != null)
      this.mKeyguardDelegate.verifyUnlock(paramOnKeyguardExitResult);
  }

  public void finishLayoutLw()
  {
  }

  public int finishPostLayoutPolicyLw()
  {
    return finishPostLayoutPolicyLw(0, true);
  }

  public int finishPostLayoutPolicyLw(int paramInt, boolean paramBoolean)
  {
    int k = 0;
    WindowManagerPolicy.WindowState localWindowState2 = this.mStatusBar;
    Object localObject1 = this.mNavigationBar;
    boolean bool2 = this.mTopIsFullscreen;
    int n = this.mLastSystemUiFlags;
    Object localObject2 = this.mTopFullscreenOpaqueWindowState;
    WindowManagerPolicy.WindowState localWindowState1 = this.mWinShowWhenLocked;
    boolean bool3 = this.mForceStatusBar;
    int m = 0;
    int j = 0;
    boolean bool1 = false;
    int i = m;
    localObject1 = localObject2;
    if (localWindowState1 != null)
    {
      i = m;
      localObject1 = localObject2;
      if (localObject2 != null)
      {
        i = m;
        localObject1 = localObject2;
        if (localWindowState1.getAppToken() != ((WindowManagerPolicy.WindowState)localObject2).getAppToken())
        {
          i = m;
          localObject1 = localObject2;
          if (isKeyguardLocked())
          {
            i = m;
            localObject1 = localObject2;
            if (!checkTopFullscreenOpaqueWindowState(localWindowState1, localWindowState1.getAttrs()))
            {
              i = m;
              localObject1 = localObject2;
              if (localWindowState1.getAttrs().coverMode == 0)
              {
                i = j;
                if ((localWindowState1.getAttrs().flags & 0x100000) == 0)
                {
                  localObject1 = localWindowState1.getAttrs();
                  ((WindowManager.LayoutParams)localObject1).flags |= 1048576;
                  i = 0x0 | 0x4;
                }
                if (localObject2 != null)
                  ((WindowManagerPolicy.WindowState)localObject2).hideLw(false);
                localObject1 = localWindowState1;
              }
            }
          }
        }
      }
    }
    localObject2 = this.mMultiPhoneWindowManager.getLongPressedMinimizeIcon();
    if (localObject2 != null)
    {
      k = 1;
      if (localObject2 == null)
        break label1132;
      localObject2 = ((WindowManagerPolicy.WindowState)localObject2).getAttrs();
      label255: if (this.mShowingDream)
        break label1138;
      this.mDreamingLockscreen = this.mShowingLockscreen;
      if (this.mDreamingSleepTokenNeeded)
      {
        this.mDreamingSleepTokenNeeded = false;
        this.mHandler.obtainMessage(15, 0, 1).sendToTarget();
      }
      label296: j = i;
      if (isForceHideByNightClock())
      {
        if ((!this.mPowerManager.isScreenOn()) || (this.mSPWM.getAodStartState() != 2))
          break label1167;
        j = i;
        if (this.mNightClock.hideLw(false))
        {
          this.mStatusBarController.setBarShowingLw(true, false);
          Log.i("WindowManager", "finishPostLayoutPolicyLw : mNightClock.hideLw by screenTurnedOn");
          j = i | 0x16;
          this.mIsNightClockShow = false;
        }
      }
      label374: i = j;
      paramBoolean = bool1;
      if (localWindowState2 != null)
      {
        i = j;
        paramBoolean = bool1;
        if (!isForceHideByNightClock())
        {
          if (DEBUG_LAYOUT)
            Slog.i("WindowManager", "force=" + bool3 + " forcefkg=" + this.mForceStatusBarFromKeyguard + " top=" + localObject1);
          if ((!this.mForceStatusBarTransparent) || (bool3) || (this.mForceStatusBarFromKeyguard))
            break label1239;
          i = 1;
          label477: if (i != 0)
            break label1244;
          this.mStatusBarController.setShowTransparent(false);
          label489: if (((!bool3) || (k != 0) || (0 != 0)) && (!this.mForceStatusBarFromKeyguard) && (!this.mForceStatusBarTransparent))
            break label1286;
          if (DEBUG_LAYOUT)
            Slog.v("WindowManager", "Showing status bar: forced");
          paramBoolean = localWindowState2.isVisibleLw();
          k = j;
          if (this.mStatusBarController.setBarShowingLw(true, true, paramInt))
          {
            i = j | 0x1;
            k = i;
            if (this.mForceStatusBarFromKeyguard)
            {
              k = i;
              if (!paramBoolean)
                k = i | 0x10;
            }
            this.mMultiPhoneWindowManager.notifySystemUiVisibility(0);
          }
          if ((!bool2) || (!localWindowState2.isAnimatingLw()))
            break label1265;
          i = 1;
          label611: if ((localObject1 == null) || (!((WindowManagerPolicy.WindowState)localObject1).getMultiWindowStyleLw().isSplit()))
            break label1270;
          label629: j = 1;
          label632: bool1 = i | j;
          i = k;
          paramBoolean = bool1;
          if (this.mForceStatusBarFromKeyguard)
          {
            i = k;
            paramBoolean = bool1;
            if (this.mStatusBarController.isTransientShowing(paramInt))
            {
              this.mStatusBarController.updateVisibilityLw(false, n, n, paramInt);
              paramBoolean = bool1;
              i = k;
            }
          }
        }
      }
      label688: j = i;
      bool1 = bool2;
      if (bool2 != paramBoolean)
      {
        j = i;
        if (!paramBoolean)
          j = i | 0x1;
        this.mTopIsFullscreen = paramBoolean;
        bool1 = paramBoolean;
      }
      i = j;
      if (this.mKeyguardDelegate != null)
      {
        i = j;
        if (localWindowState2 != null)
        {
          if ((this.mHideSDKeyguard) && (getPersonaManagerServiceLocked() != null))
            this.mPersonaManagerService.notifyActivityDrawn(0, bool1, this.mHideSDKeyguard);
          if ((this.mDismissKeyguard == 0) || (this.mKeyguardSecure))
            break label1855;
          this.mKeyguardHidden = true;
          if (this.mCocktailPhoneWindowManager.isEdgeScreenWaked())
          {
            if ((this.mWinDismissingKeyguard == null) || ((this.mWinDismissingKeyguard.getAttrs().flags & 0x200000) == 0))
              break label1842;
            this.mCocktailPhoneWindowManager.requestEdgeScreenWakeup(false, -1, 2);
          }
          label832: k = j;
          if (setKeyguardOccludedLw(true, paramInt))
            k = j | 0x17;
          i = k;
          if (this.mKeyguardDelegate.isShowing())
          {
            this.mHandler.post(new Runnable()
            {
              public void run()
              {
                PhoneWindowManager.this.mKeyguardDelegate.keyguardDone(false, false);
              }
            });
            i = k;
          }
        }
      }
      label884: paramInt = i;
      if (isSupportAndAttachedSViewCover())
      {
        if (DEBUG_LAYOUT)
          Slog.d("WindowManager", "finishPostLayoutPolicyLw: mHideSViewCover=" + this.mHideSViewCover);
        k = 0;
        n = 0;
        m = 0;
        j = 0;
        localObject2 = getCoverManager();
        paramInt = i;
        if (localObject2 != null)
        {
          paramInt = i;
          if (localWindowState2 != null)
          {
            if ((this.mHideSViewCover == 0) && ((this.mCoverState.switchState) || (this.mCoverState.type == 255) || (!this.mSPWM.isRingingOrOffhook()) || (this.mSPWM.isTphoneRelaxMode())))
              break label2081;
            paramInt = k;
          }
        }
      }
    }
    while (true)
    {
      try
      {
        if (this.mLastCoverAppCovered)
          continue;
        paramInt = k;
        k = ((ICoverManager)localObject2).onCoverAppCovered(true);
        j = k;
        if ((k & 0x10) == 0)
          continue;
        paramInt = k;
        this.mLastCoverAppCovered = true;
        j = k;
        paramInt = i;
        if (!processSViewCoverSetHiddenResultLw(j))
          continue;
        paramInt = i | 0x1;
        i = paramInt;
        if ((updateSystemUiVisibilityLw() & 0xC0008006) == 0)
          continue;
        i = paramInt | 0x1;
        this.mMultiPhoneWindowManager.finishPostLayoutPolicyLw((WindowManagerPolicy.WindowState)localObject1);
        paramInt = this.mCocktailPhoneWindowManager.finishPostLayoutPolicyLw();
        this.mHandler.sendEmptyMessage(17);
        updateLockScreenTimeout();
        return i | paramInt;
        localObject2 = localObject1;
        break;
        label1132: localObject2 = null;
        break label255;
        label1138: if (this.mDreamingSleepTokenNeeded)
          break label296;
        this.mDreamingSleepTokenNeeded = true;
        this.mHandler.obtainMessage(15, 1, 1).sendToTarget();
        break label296;
        label1167: j = i;
        if (this.mIsNightClockShow)
          break label374;
        j = i;
        if (localWindowState2 == null)
          break label374;
        j = i;
        if ((localWindowState2.getAttrs().flags & 0x100000) == 0)
          break label374;
        this.mIsNightClockShow = true;
        this.mStatusBarController.setBarShowingLw(true, false);
        Log.i("WindowManager", "finishPostLayoutPolicyLw : mNightClock.show");
        j = i | 0x16;
        break label374;
        label1239: i = 0;
        break label477;
        label1244: if (localWindowState2.isVisibleLw())
          break label489;
        this.mStatusBarController.setShowTransparent(true);
        break label489;
        label1265: i = 0;
        break label611;
        label1270: if (this.mMultiPhoneWindowManager.isForceHideStatusBar())
          break label629;
        j = 0;
        break label632;
        label1286: if ((!this.mForceStatusBarFromSViewCover) || (this.mForceHideStatusBarForCocktail) || (0 != 0))
          continue;
        if (!DEBUG_LAYOUT)
          continue;
        Slog.v("WindowManager", "Showing status bar: forcefsvc");
        k = j;
        if (!this.mStatusBarController.setBarShowingLw(true, true, paramInt))
          continue;
        k = j | 0x1 | 0x10;
        if ((!bool2) || (!localWindowState2.isAnimatingLw()))
          continue;
        bool1 = true;
        i = k;
        paramBoolean = bool1;
        if (!this.mStatusBarController.isTransientShowing(paramInt))
          break label688;
        this.mStatusBarController.updateVisibilityLw(false, n, n, paramInt);
        i = k;
        paramBoolean = bool1;
        break label688;
        bool1 = false;
        continue;
        if (localObject1 != null)
          continue;
        i = j;
        paramBoolean = bool1;
        if (k == 0)
          break label688;
        if (((PolicyControl.getWindowFlags(null, (WindowManager.LayoutParams)localObject2) & 0x400) == 0) && ((n & 0x4) == 0) && (!this.mForceHideStatusBarForCocktail))
          continue;
        paramBoolean = true;
        bool1 = paramBoolean;
        if (this.mHideSViewCover == 0)
          continue;
        bool1 = paramBoolean;
        if (this.mHideSViewCoverWindowState == null)
          continue;
        bool1 = paramBoolean;
        if (localObject1 == this.mHideSViewCoverWindowState)
          continue;
        i = PolicyControl.getWindowFlags(null, this.mHideSViewCoverWindowState.getAttrs());
        if ((((i & 0x400) == 0) || ((i & 0x800) != 0)) && ((n & 0x4) == 0))
          continue;
        bool1 = true;
        if ((!bool1) || ((((WindowManager.LayoutParams)localObject2).samsungFlags & 0x2) == 0) || (this.mForceHideStatusBarForCocktail))
          continue;
        paramBoolean = true;
        this.mOpenByNotification = paramBoolean;
        paramBoolean = this.mOpenByNotification;
        if ((!((WindowManagerPolicy.WindowState)localObject1).getMultiWindowStyleLw().isSplit()) && (!this.mMultiPhoneWindowManager.isForceHideStatusBar()))
          continue;
        i = 1;
        this.mOpenByNotification = (i | paramBoolean);
        if (!this.mStatusBarController.isTransientShowing(paramInt))
          continue;
        if (!this.mForceHideStatusBarForCocktail)
          continue;
        this.mStatusBarController.hideTransient();
        i = j;
        paramBoolean = bool1;
        if (!this.mStatusBarController.setBarShowingLw(false, false, paramInt, true))
          break label688;
        i = j | 0x1;
        paramBoolean = bool1;
        break label688;
        paramBoolean = false;
        continue;
        bool1 = false;
        continue;
        paramBoolean = false;
        continue;
        i = 0;
        continue;
        i = j;
        paramBoolean = bool1;
        if (!this.mStatusBarController.setBarShowingLw(true, true, paramInt))
          break label688;
        i = j | 0x1;
        paramBoolean = bool1;
        break label688;
        if (!bool1)
          continue;
        if (!DEBUG_LAYOUT)
          continue;
        Slog.v("WindowManager", "** HIDING status bar");
        if (!this.mStatusBarController.setBarShowingLw(false, false, paramInt, this.mForceHideStatusBarForCocktail))
          continue;
        i = j | 0x1;
        this.mMultiPhoneWindowManager.notifySystemUiVisibility(4);
        paramBoolean = bool1;
        break label688;
        i = j;
        paramBoolean = bool1;
        if (!DEBUG_LAYOUT)
          break label688;
        Slog.v("WindowManager", "Status bar already hiding");
        i = j;
        paramBoolean = bool1;
        break label688;
        if (!DEBUG_LAYOUT)
          continue;
        Slog.v("WindowManager", "** SHOWING status bar: top is not fullscreen");
        i = j;
        paramBoolean = bool1;
        if (!this.mStatusBarController.setBarShowingLw(true, true, paramInt))
          break label688;
        i = j | 0x1;
        this.mMultiPhoneWindowManager.notifySystemUiVisibility(0);
        paramBoolean = bool1;
        break label688;
        label1842: this.mCocktailPhoneWindowManager.requestEdgeScreenWakeup(true, -1, 3);
        break label832;
        label1855: if (!this.mHideLockScreen)
          continue;
        this.mWinDismissingKeyguard = null;
        this.mKeyguardHidden = true;
        if ((!this.mCocktailPhoneWindowManager.isEdgeScreenWaked()) || (this.mHideLockScreenByCover) || (localWindowState1 == null) || ((localWindowState1.getAttrs().flags & 0x200000) == 0))
          continue;
        this.mCocktailPhoneWindowManager.requestEdgeScreenWakeup(false, -1, 2);
        k = j;
        if (!setKeyguardOccludedLw(true, paramInt))
          continue;
        k = j | 0x17;
        i = k;
        if (this.mLastWinShowWhenLocked == localWindowState1)
          break label884;
        this.mLastWinShowWhenLocked = localWindowState1;
        i = k | 0x2;
        break label884;
        if (this.mDismissKeyguard == 0)
          continue;
        this.mKeyguardHidden = false;
        k = j;
        if (!setKeyguardOccludedLw(false, paramInt))
          continue;
        k = j | 0x17;
        i = k;
        if (this.mDismissKeyguard != 1)
          break label884;
        this.mHandler.post(new Runnable()
        {
          public void run()
          {
            PhoneWindowManager.this.mKeyguardDelegate.dismiss();
          }
        });
        i = k;
        break label884;
        this.mWinDismissingKeyguard = null;
        this.mSecureDismissingKeyguard = false;
        this.mKeyguardHidden = false;
        i = j;
        if (!setKeyguardOccludedLw(false, paramInt))
          break label884;
        i = j | 0x17;
      }
      catch (RemoteException localRemoteException1)
      {
        localRemoteException1.printStackTrace();
        j = paramInt;
        continue;
      }
      label2081: j = n;
      paramInt = m;
      try
      {
        if (this.mLastCoverAppCovered)
        {
          paramInt = m;
          k = localRemoteException1.onCoverAppCovered(false);
          j = k;
          if ((k & 0x20) != 0)
          {
            paramInt = k;
            this.mLastCoverAppCovered = false;
            j = k;
          }
        }
        paramInt = i;
        if (!processSViewCoverSetHiddenResultLw(j))
          continue;
        paramInt = i | 0x1;
      }
      catch (RemoteException localRemoteException2)
      {
        while (true)
        {
          localRemoteException2.printStackTrace();
          j = paramInt;
        }
      }
    }
  }

  public void finishedGoingToSleep(int paramInt)
  {
    finishedGoingToSleep(paramInt, 0);
  }

  public void finishedGoingToSleep(int paramInt1, int paramInt2)
  {
    EventLog.writeEvent(70000, 0);
    if (DEBUG_WAKEUP)
      Log.i("WindowManager", "Finished going to sleep... (why=" + paramInt1 + ")");
    MetricsLogger.histogram(this.mContext, "screen_timeout", this.mLockScreenTimeout / 1000);
    synchronized (this.mLock)
    {
      this.mAwake = false;
      updateWakeGestureListenerLp();
      updateOrientationListenerLp();
      updateLockScreenTimeout();
      if ((AppLockPolicy.isSupportAppLock()) && (paramInt1 != 4))
        this.mSPWM.setAppLockedStatus();
      this.mSPWM.goingToSleep(paramInt1);
      if (this.mKeyguardDelegate != null)
        this.mKeyguardDelegate.onFinishedGoingToSleep(paramInt1);
      if (getPersonaManagerServiceLocked() != null)
        getPersonaManagerServiceLocked().onFinishedGoingToSleep(paramInt1);
      if ((getEDM() != null) && (getEDM().getEnterpriseSharedDevicePolicy() != null))
      {
        boolean bool = getEDM().getEnterpriseSharedDevicePolicy().isSharedDeviceEnabled();
        ??? = new Intent();
        ((Intent)???).setComponent(new ComponentName("com.sec.enterprise.knox.shareddevice.keyguard", "com.sec.enterprise.knox.shareddevice.keyguard.SharedDeviceKeyguardService"));
        ((Intent)???).putExtra("SharedDeviceKeyguardEventFlag", 16);
        if (bool)
        {
          Log.d("WindowManager", "Shared devices screen OFF completed");
          ((Intent)???).putExtra("isScreenOff", 1);
          this.mContext.startService((Intent)???);
        }
      }
      if (this.mHideSViewCoverWindowState != null)
        this.mHideSViewCoverWindowState.disableHideSViewCoverOnce(true);
      if (this.mCocktailPhoneWindowManager.isEdgeScreenWaked())
        this.mCocktailPhoneWindowManager.requestEdgeScreenWakeup(false, -1, 1);
      this.mWindowManagerFuncs.cancelDragForcelyWhenScreenTurnOff(true);
      return;
    }
  }

  public void finishedWakingUp()
  {
    if (DEBUG_WAKEUP)
      Log.i("WindowManager", "Finished waking up...");
  }

  public int focusChangedLw(WindowManagerPolicy.WindowState paramWindowState1, WindowManagerPolicy.WindowState paramWindowState2)
  {
    this.mFocusedWindow = paramWindowState2;
    if (this.mFocusedWindow != null)
    {
      paramWindowState1 = this.mFocusedWindow.getAttrs().getTitle().toString().split("/");
      if ((paramWindowState1 != null) && (paramWindowState1.length >= 2))
        this.mSystemKeyManager.updateFocusedWindow(new ComponentName(paramWindowState1[0], paramWindowState1[1]));
      if (isScreenOn())
        this.mTspStateManager.updateWindowPolicy(this.mFocusedWindow);
    }
    if ((updateSystemUiVisibilityLw() & 0xC0008006) != 0)
      return 1;
    return 0;
  }

  public int focusChangedLw(WindowManagerPolicy.WindowState paramWindowState1, WindowManagerPolicy.WindowState paramWindowState2, int paramInt)
  {
    this.mFocusedWindow = paramWindowState2;
    if (this.mFocusedWindow != null)
    {
      paramWindowState1 = this.mFocusedWindow.getAttrs().getTitle().toString().split("/");
      if ((paramWindowState1 != null) && (paramWindowState1.length >= 2))
        this.mSystemKeyManager.updateFocusedWindow(new ComponentName(paramWindowState1[0], paramWindowState1[1]));
    }
    if ((updateSystemUiVisibilityLw() & 0xC0008006) != 0)
      return 1;
    return 0;
  }

  public void forceHideCenterBar(boolean paramBoolean)
  {
    this.mMultiPhoneWindowManager.forceHideCenterBar(paramBoolean);
  }

  public ArrayList<IApplicationToken> getAppsShowWhenLockedLw()
  {
    return this.mAppsShowWhenLocked;
  }

  public Rect getCocktailBarFrame(WindowManagerPolicy.WindowState paramWindowState, boolean paramBoolean)
  {
    if (CocktailBarFeatures.isSystemBarType(this.mContext))
      return this.mCocktailPhoneWindowManager.getCocktailBarFrame(paramWindowState, paramBoolean);
    return new Rect();
  }

  public int getConfigDisplayHeight(int paramInt1, int paramInt2, int paramInt3)
  {
    return getNonDecorDisplayHeight(paramInt1, paramInt2, paramInt3) - this.mStatusBarHeight;
  }

  public int getConfigDisplayWidth(int paramInt1, int paramInt2, int paramInt3)
  {
    return getNonDecorDisplayWidth(paramInt1, paramInt2, paramInt3);
  }

  public void getContentRectLw(Rect paramRect)
  {
    paramRect.set(this.mContentLeft, this.mContentTop, this.mContentRight, this.mContentBottom);
  }

  public boolean getCoverStateSwitch()
  {
    if (this.mCoverState != null)
      return this.mCoverState.getSwitchState();
    return true;
  }

  public ComponentName getCurrentTopActivity()
  {
    return this.mSystemKeyManager.getCurrentTopActivity();
  }

  public int getFixedTaskId()
  {
    return this.mFixedTaskId;
  }

  public int getFloatingStatusBarHeight(WindowManagerPolicy.WindowState paramWindowState)
  {
    return this.mMultiPhoneWindowManager.getFloatingStatusBarHeight(paramWindowState);
  }

  public Rect getFloatingWindowPadding(WindowManagerPolicy.WindowState paramWindowState)
  {
    return this.mMultiPhoneWindowManager.getFloatingWindowPadding(paramWindowState);
  }

  public int getGlobalSystemUiVisibility()
  {
    return this.mMultiPhoneWindowManager.getGlobalSystemUiVisibility(this.mStatusBar, this.mLastSystemUiFlags, this.mTopFullscreenOpaqueWindowState);
  }

  public int getInputMethodWindowVisibleHeightLw()
  {
    return this.mDockBottom - this.mCurBottom;
  }

  public void getInsetHintLw(WindowManager.LayoutParams paramLayoutParams, int paramInt, Rect paramRect1, Rect paramRect2, Rect paramRect3)
  {
    int j = PolicyControl.getWindowFlags(null, paramLayoutParams);
    int k = PolicyControl.getSystemUiVisibility(null, paramLayoutParams) | paramLayoutParams.subtreeSystemUiVisibility;
    int i;
    if ((paramRect3 != null) && (shouldUseOutsets(paramLayoutParams, j)))
    {
      i = 1;
      if (i != 0)
      {
        i = ScreenShapeHelper.getWindowOutsetBottomPx(this.mContext.getResources());
        if (i > 0)
        {
          if (paramInt != 0)
            break label201;
          paramRect3.bottom += i;
        }
      }
      label76: if ((0x10100 & j) != 65792)
        break label406;
      if ((!canHideNavigationBar()) || ((k & 0x200) == 0))
        break label264;
      i = this.mUnrestrictedScreenLeft + this.mUnrestrictedScreenWidth;
      paramInt = this.mUnrestrictedScreenTop + this.mUnrestrictedScreenHeight;
      label125: if ((k & 0x100) == 0)
        break label316;
      if ((j & 0x400) == 0)
        break label288;
      paramRect1.set(this.mStableFullscreenLeft, this.mStableFullscreenTop, i - this.mStableFullscreenRight, paramInt - this.mStableFullscreenBottom);
    }
    while (true)
    {
      paramRect2.set(this.mStableLeft, this.mStableTop, i - this.mStableRight, paramInt - this.mStableBottom);
      return;
      i = 0;
      break;
      label201: if (paramInt == 1)
      {
        paramRect3.right += i;
        break label76;
      }
      if (paramInt == 2)
      {
        paramRect3.top += i;
        break label76;
      }
      if (paramInt != 3)
        break label76;
      paramRect3.left += i;
      break label76;
      label264: i = this.mRestrictedScreenLeft + this.mRestrictedScreenWidth;
      paramInt = this.mRestrictedScreenTop + this.mRestrictedScreenHeight;
      break label125;
      label288: paramRect1.set(this.mStableLeft, this.mStableTop, i - this.mStableRight, paramInt - this.mStableBottom);
      continue;
      label316: if (((j & 0x400) != 0) || ((0x2000000 & j) != 0))
      {
        paramRect1.setEmpty();
        continue;
      }
      if ((k & 0x404) == 0)
      {
        paramRect1.set(this.mCurLeft, this.mCurTop, i - this.mCurRight, paramInt - this.mCurBottom);
        continue;
      }
      paramRect1.set(this.mCurLeft, this.mCurTop, i - this.mCurRight, paramInt - this.mCurBottom);
    }
    label406: paramRect1.setEmpty();
    paramRect2.setEmpty();
  }

  public int getLockTaskMode()
  {
    return this.mLockTaskModeState;
  }

  public int getMaxWallpaperLayer()
  {
    return windowTypeToLayerLw(2000);
  }

  public int getMinimizeSize()
  {
    return this.mMultiPhoneWindowManager.getMinimizeSize();
  }

  public int getNonDecorAlphaScreenDisplayHeight(int paramInt1, int paramInt2)
  {
    return this.mCocktailPhoneWindowManager.getNonDecorAlphaScreenDisplayHeight(paramInt1, paramInt2);
  }

  public int getNonDecorAlphaScreenDisplayWidth(int paramInt1, int paramInt2)
  {
    return this.mCocktailPhoneWindowManager.getNonDecorAlphaScreenDisplayWidth(paramInt1, paramInt2);
  }

  public int getNonDecorDisplayHeight(int paramInt1, int paramInt2, int paramInt3)
  {
    if ((this.mHasNavigationBar) && ((!this.mNavigationBarCanMove) || (paramInt1 < paramInt2)))
      return paramInt2 - this.mNavigationBarHeightForRotation[paramInt3];
    paramInt3 = paramInt2;
    if (this.mMobileKeyboardEnabled)
    {
      paramInt3 = paramInt2;
      if (paramInt1 < paramInt2)
        paramInt3 = paramInt2 - this.mMobileKeyboardHeight;
    }
    paramInt2 = paramInt3;
    if (isCarModeBarVisible())
    {
      paramInt2 = paramInt3;
      if (paramInt1 < paramInt3)
        paramInt2 = paramInt3 - this.mCarModeSize;
    }
    if (CocktailBarFeatures.isSystemBarType(this.mContext))
      return this.mCocktailPhoneWindowManager.getNonDecorDisplayHeight(paramInt1, paramInt2);
    return paramInt2;
  }

  public int getNonDecorDisplayWidth(int paramInt1, int paramInt2, int paramInt3)
  {
    if ((this.mHasNavigationBar) && (this.mNavigationBarCanMove) && (paramInt1 > paramInt2))
      return paramInt1 - this.mNavigationBarWidthForRotation[paramInt3];
    paramInt3 = paramInt1;
    if (this.mMobileKeyboardEnabled)
    {
      paramInt3 = paramInt1;
      if (paramInt1 > paramInt2)
        paramInt3 = paramInt1 - this.mMobileKeyboardHeight;
    }
    paramInt1 = paramInt3;
    if (isCarModeBarVisible())
    {
      paramInt1 = paramInt3;
      if (paramInt3 > paramInt2)
        paramInt1 = paramInt3 - this.mCarModeSize;
    }
    if (CocktailBarFeatures.isSystemBarType(this.mContext))
      return this.mCocktailPhoneWindowManager.getNonDecorDisplayWidth(paramInt1, paramInt2);
    return paramInt1;
  }

  PersonaManagerService getPersonaManagerServiceLocked()
  {
    if (this.mPersonaManagerService == null)
      this.mPersonaManagerService = ((PersonaManagerService)IPersonaManager.Stub.asInterface(ServiceManager.getService("persona")));
    return this.mPersonaManagerService;
  }

  public int getSViewCoverHeight(DisplayInfo paramDisplayInfo)
  {
    if (this.mCoverState != null)
      return this.mCoverState.heightPixel;
    return paramDisplayInfo.appHeight;
  }

  public int getSViewCoverWidth(DisplayInfo paramDisplayInfo)
  {
    if (this.mCoverState != null)
      return this.mCoverState.widthPixel;
    return paramDisplayInfo.appWidth;
  }

  public int getScaleWindowResizableSize()
  {
    return this.mMultiPhoneWindowManager.getScaleWindowResizableSize();
  }

  IStatusBarService getStatusBarService()
  {
    synchronized (this.mServiceAquireLock)
    {
      if (this.mStatusBarService == null)
        this.mStatusBarService = IStatusBarService.Stub.asInterface(ServiceManager.getService("statusbar"));
      IStatusBarService localIStatusBarService = this.mStatusBarService;
      return localIStatusBarService;
    }
  }

  public int getSystemDecorLayerLw()
  {
    if ((this.mStatusBar != null) && (this.mStatusBar.isVisibleLw()))
      return this.mStatusBar.getSurfaceLayer();
    if ((this.mNavigationBar != null) && (this.mNavigationBar.isVisibleLw()))
      return this.mNavigationBar.getSurfaceLayer();
    return 0;
  }

  TelecomManager getTelecommService()
  {
    return (TelecomManager)this.mContext.getSystemService("telecom");
  }

  public WindowManagerPolicy.WindowState getTopFullscreenOpaqueWindowState()
  {
    return this.mTopFullscreenOpaqueWindowState;
  }

  public int getUserRotationMode()
  {
    if (Settings.System.getIntForUser(this.mContext.getContentResolver(), "accelerometer_rotation", 0, -2) != 0)
      return 0;
    return 1;
  }

  public WindowManagerPolicy.WindowState getWinShowWhenLockedLw()
  {
    return getWinShowWhenLockedLw(0);
  }

  public WindowManagerPolicy.WindowState getWinShowWhenLockedLw(int paramInt)
  {
    return this.mWinShowWhenLocked;
  }

  boolean goHome()
  {
    int j = 0;
    int i = j;
    if (getPersonaManagerLocked() != null)
    {
      i = j;
      if (this.mPersonaManager.getPersonaIds() != null)
      {
        i = j;
        if (this.mPersonaManager.getPersonaIds().length > 0)
          i = this.mPersonaManager.getPersonaIds()[0];
      }
    }
    if (isKnoxKeyguardShownForKioskMode(i))
    {
      Log.d("WindowManager", "goHome() > isKnoxKeyguardShownForKioskMode() : true");
      return false;
    }
    if (!isUserSetupComplete())
    {
      Slog.i("WindowManager", "Not going home because user setup is in progress.");
      return false;
    }
    try
    {
      if (SystemProperties.getInt("persist.sys.uts-test-mode", 0) == 1)
        Log.d("WindowManager", "UTS-TEST-MODE");
      while (ActivityManagerNative.getDefault().startActivityAsUser(null, null, this.mHomeIntent, this.mHomeIntent.resolveTypeIfNeeded(this.mContext.getContentResolver()), null, null, 0, 1, null, null, -2) == 1)
      {
        return false;
        ActivityManagerNative.getDefault().stopAppSwitches();
        sendCloseSystemWindows();
        Intent localIntent = createHomeDockIntent();
        if (localIntent == null)
          continue;
        i = ActivityManagerNative.getDefault().startActivityAsUser(null, null, localIntent, localIntent.resolveTypeIfNeeded(this.mContext.getContentResolver()), null, null, 0, 1, null, null, -2);
        if (i == 1)
          return false;
      }
    }
    catch (RemoteException localRemoteException)
    {
    }
    return true;
  }

  public void handleDoubleTapOnHome()
  {
    int i = Settings.System.getInt(this.mContext.getContentResolver(), "home_doubletap_button", 101);
    if (i != 4)
    {
      if (i > 23)
      {
        nromLaunchLastOption(i);
        return;
      }
      Intent localIntent = new Intent();
      localIntent.setAction("android.intent.action.PhoneWindowManagers$AryaModKeyPolicy");
      localIntent.putExtra("action", i);
      this.mContext.sendBroadcast(localIntent);
      return;
    }
    performHapticFeedbackLw(null, 0, false);
    toggleRecentApps();
  }

  public void handleLongPressOnBack()
  {
    int i = Settings.System.getInt(this.mContext.getContentResolver(), "right_longpress_button", 5);
    if (i != 4)
    {
      if (i > 23)
      {
        nromLaunchLastOption(i);
        return;
      }
      Intent localIntent = new Intent();
      localIntent.setAction("android.intent.action.PhoneWindowManagers$AryaModKeyPolicy");
      localIntent.putExtra("action", i);
      this.mContext.sendBroadcast(localIntent);
      return;
    }
    performHapticFeedbackLw(null, 0, false);
    toggleRecentApps();
  }

  void handleVolumeKey(int paramInt1, int paramInt2)
  {
    int j = 1;
    int i = 1;
    IAudioService localIAudioService = getAudioService();
    if (localIAudioService == null)
      return;
    try
    {
      this.mBroadcastWakeLock.acquire();
      if (paramInt1 == 3)
        if (paramInt2 == 24)
        {
          paramInt2 = i;
          localIAudioService.adjustLocalOrRemoteStreamVolume(paramInt1, paramInt2, this.mContext.getOpPackageName());
        }
      while (true)
      {
        return;
        paramInt2 = -1;
        break;
        if (paramInt2 != 24)
          break label132;
        paramInt2 = j;
        localIAudioService.adjustStreamVolume(paramInt1, paramInt2, 0, this.mContext.getOpPackageName());
      }
    }
    catch (RemoteException localRemoteException)
    {
      while (true)
      {
        Log.w("WindowManager", "IAudioService.adjust*StreamVolume() threw RemoteException " + localRemoteException);
        return;
        label132: paramInt2 = -1;
      }
    }
    finally
    {
      this.mBroadcastWakeLock.release();
    }
    throw localObject;
  }

  void handleVolumeLongPress(int paramInt)
  {
    if (paramInt == 24);
    for (Runnable localRunnable = this.mVolumeUpLongPress; ; localRunnable = this.mVolumeDownLongPress)
    {
      this.mHandler.postDelayed(localRunnable, this.mVolBtnTimeout);
      return;
    }
  }

  void handleVolumeLongPressAbort()
  {
    this.mHandler.removeCallbacks(this.mVolumeUpLongPress);
    this.mHandler.removeCallbacks(this.mVolumeDownLongPress);
  }

  public boolean hasNavigationBar()
  {
    return this.mHasNavigationBar;
  }

  public void hideBootMessages()
  {
    this.mHandler.sendEmptyMessage(11);
  }

  public boolean inKeyguardRestrictedKeyInputMode()
  {
    if (this.mKeyguardDelegate == null)
      return false;
    return this.mKeyguardDelegate.isInputRestricted();
  }

  public void init(Context paramContext, IWindowManager paramIWindowManager, WindowManagerPolicy.WindowManagerFuncs paramWindowManagerFuncs)
  {
    this.mAbsPhoneWindownManager = new PhoneWindowManagers.AryaModKeyPolicy(paramContext);
    this.mContext = paramContext;
    this.mWindowManager = paramIWindowManager;
    this.mWindowManagerFuncs = paramWindowManagerFuncs;
    this.mWindowManagerInternal = ((WindowManagerInternal)LocalServices.getService(WindowManagerInternal.class));
    this.mActivityManagerInternal = ((ActivityManagerInternal)LocalServices.getService(ActivityManagerInternal.class));
    this.mDreamManagerInternal = ((DreamManagerInternal)LocalServices.getService(DreamManagerInternal.class));
    this.mPowerManagerInternal = ((PowerManagerInternal)LocalServices.getService(PowerManagerInternal.class));
    this.mAppOpsManager = ((AppOpsManager)this.mContext.getSystemService("appops"));
    boolean bool1 = paramContext.getResources().getBoolean(17957049);
    boolean bool2 = SystemProperties.getBoolean("persist.debug.force_burn_in", false);
    int j;
    int k;
    int m;
    int n;
    int i;
    if ((bool1) || (bool2))
    {
      if (!bool2)
        break label1232;
      j = -8;
      k = 8;
      m = -8;
      n = -4;
      if (!isRoundWindow())
        break label1226;
      i = 6;
    }
    while (true)
    {
      this.mBurnInProtectionHelper = new BurnInProtectionHelper(paramContext, j, k, m, n, i);
      this.mHandler = new PolicyHandler(null);
      this.mWakeGestureListener = new MyWakeGestureListener(this.mContext, this.mHandler);
      this.mOrientationListener = new MyOrientationListener(this.mContext, this.mHandler);
      try
      {
        this.mOrientationListener.setCurrentRotation(paramIWindowManager.getRotation());
        label247: Settings.System.putIntForUser(this.mContext.getContentResolver(), "mobile_keyboard", 0, 0);
        this.mSettingsObserver = new SettingsObserver(this.mHandler);
        this.mSettingsObserver.observe();
        this.mShortcutManager = new ShortcutManager(paramContext);
        this.mUiMode = paramContext.getResources().getInteger(17694790);
        this.mHomeIntent = new Intent("android.intent.action.MAIN", null);
        this.mHomeIntent.addCategory("android.intent.category.HOME");
        this.mHomeIntent.addFlags(270532608);
        this.mCarDockIntent = new Intent("android.intent.action.MAIN", null);
        this.mCarDockIntent.addCategory("android.intent.category.CAR_DOCK");
        this.mCarDockIntent.addFlags(270532608);
        this.mDeskDockIntent = new Intent("android.intent.action.MAIN", null);
        this.mDeskDockIntent.addCategory("android.intent.category.DESK_DOCK");
        this.mDeskDockIntent.addFlags(270532608);
        this.mPowerManager = ((PowerManager)paramContext.getSystemService("power"));
        this.mBroadcastWakeLock = this.mPowerManager.newWakeLock(1, "PhoneWindowManager.mBroadcastWakeLock");
        this.mPowerKeyWakeLock = this.mPowerManager.newWakeLock(1, "PhoneWindowManager.mPowerKeyWakeLock");
        this.mEnableShiftMenuBugReports = "1".equals(SystemProperties.get("ro.debuggable"));
        this.mSupportAutoRotation = this.mContext.getResources().getBoolean(17956923);
        this.mLidOpenRotation = readRotation(17694782);
        this.mCarDockRotation = readRotation(17694787);
        this.mDeskDockRotation = readRotation(17694785);
        this.mUndockedHdmiRotation = readRotation(17694789);
        this.mMirrorlinkDockRotation = readRotation(17694977);
        this.mCarDockEnablesAccelerometer = this.mContext.getResources().getBoolean(17956929);
        this.mDeskDockEnablesAccelerometer = this.mContext.getResources().getBoolean(17956928);
        this.mMirrorLinkDockEnablesAccelerometer = this.mContext.getResources().getBoolean(17957069);
        this.mLidKeyboardAccessibility = this.mContext.getResources().getInteger(17694783);
        this.mLidNavigationAccessibility = this.mContext.getResources().getInteger(17694784);
        this.mLidControlsSleep = this.mContext.getResources().getBoolean(17956927);
        this.mTranslucentDecorEnabled = this.mContext.getResources().getBoolean(17956938);
        this.mAllowTheaterModeWakeFromKey = this.mContext.getResources().getBoolean(17956915);
        if ((this.mAllowTheaterModeWakeFromKey) || (this.mContext.getResources().getBoolean(17956914)))
        {
          bool1 = true;
          label717: this.mAllowTheaterModeWakeFromPowerKey = bool1;
          this.mAllowTheaterModeWakeFromMotion = this.mContext.getResources().getBoolean(17956916);
          this.mAllowTheaterModeWakeFromMotionWhenNotDreaming = this.mContext.getResources().getBoolean(17956917);
          this.mAllowTheaterModeWakeFromCameraLens = this.mContext.getResources().getBoolean(17956913);
          this.mAllowTheaterModeWakeFromLidSwitch = this.mContext.getResources().getBoolean(17956918);
          this.mAllowTheaterModeWakeFromWakeGesture = this.mContext.getResources().getBoolean(17956912);
          this.mGoToSleepOnButtonPressTheaterMode = this.mContext.getResources().getBoolean(17956921);
          this.mSupportLongPressPowerWhenNonInteractive = this.mContext.getResources().getBoolean(17956922);
          this.mShortPressOnPowerBehavior = this.mContext.getResources().getInteger(17694793);
          this.mLongPressOnPowerBehavior = this.mContext.getResources().getInteger(17694792);
          this.mDoublePressOnPowerBehavior = this.mContext.getResources().getInteger(17694794);
          this.mTriplePressOnPowerBehavior = this.mContext.getResources().getInteger(17694795);
          this.mShortPressOnSleepBehavior = this.mContext.getResources().getInteger(17694796);
          if (AudioSystem.getPlatformType(this.mContext) != 2)
            break label1291;
        }
        label1291: for (bool1 = true; ; bool1 = false)
        {
          this.mUseTvRouting = bool1;
          readConfigurationDependentBehaviors();
          this.mAccessibilityManager = ((AccessibilityManager)paramContext.getSystemService("accessibility"));
          paramIWindowManager = new IntentFilter();
          paramIWindowManager.addAction(UiModeManager.ACTION_ENTER_CAR_MODE);
          paramIWindowManager.addAction(UiModeManager.ACTION_EXIT_CAR_MODE);
          paramIWindowManager.addAction(UiModeManager.ACTION_ENTER_DESK_MODE);
          paramIWindowManager.addAction(UiModeManager.ACTION_EXIT_DESK_MODE);
          paramIWindowManager.addAction("android.intent.action.DOCK_EVENT");
          paramIWindowManager = paramContext.registerReceiver(this.mDockReceiver, paramIWindowManager);
          if (paramIWindowManager != null)
            this.mDockMode = paramIWindowManager.getIntExtra("android.intent.extra.DOCK_STATE", 0);
          if (Debug.isProductShip() != 1)
          {
            paramIWindowManager = new IntentFilter();
            paramIWindowManager.addAction("android.app.action.DEBUG_RECONFIGURE");
            paramContext.registerReceiver(this.mReconfigureDebugReceiver, paramIWindowManager);
          }
          paramIWindowManager = new IntentFilter();
          paramIWindowManager.addAction("android.intent.action.PACKAGE_REPLACED");
          paramIWindowManager.addDataScheme("package");
          paramContext.registerReceiver(this.mSystemUIReplacedReceiver, paramIWindowManager);
          paramIWindowManager = new IntentFilter();
          paramIWindowManager.addAction("android.intent.action.DREAMING_STARTED");
          paramIWindowManager.addAction("android.intent.action.DREAMING_STOPPED");
          paramContext.registerReceiver(this.mDreamReceiver, paramIWindowManager);
          paramIWindowManager.addAction("android.intent.action.ScreenShot");
          paramContext.registerReceiver(this.mScreenshotReceiver, paramIWindowManager);
          paramIWindowManager = new IntentFilter();
          paramIWindowManager.addAction("com.samsung.intent.action.SEC_PRESENTATION_START");
          paramContext.registerReceiver(this.mPresentationStartReceiver, paramIWindowManager);
          paramIWindowManager = new IntentFilter();
          paramIWindowManager.addAction("com.samsung.intent.action.SEC_PRESENTATION_STOP");
          paramContext.registerReceiver(this.mPresentationStopReceiver, paramIWindowManager);
          i = 0;
          while (i < 3)
          {
            paramIWindowManager.addAction(this.ALARM_STARTED[i]);
            i += 1;
          }
          label1226: i = -1;
          break;
          label1232: paramWindowManagerFuncs = paramContext.getResources();
          j = paramWindowManagerFuncs.getInteger(17694891);
          k = paramWindowManagerFuncs.getInteger(17694892);
          m = paramWindowManagerFuncs.getInteger(17694893);
          n = paramWindowManagerFuncs.getInteger(17694894);
          i = paramWindowManagerFuncs.getInteger(17694890);
          break;
          bool1 = false;
          break label717;
        }
        i = 0;
        while (i < 3)
        {
          paramIWindowManager.addAction(this.ALARM_STOPPED[i]);
          i += 1;
        }
        paramContext.registerReceiver(this.mAlarmReceiver, paramIWindowManager);
        paramIWindowManager = new IntentFilter("android.intent.action.USER_SWITCHED");
        paramContext.registerReceiver(this.mMultiuserReceiver, paramIWindowManager);
        paramIWindowManager = new IntentFilter("com.samsung.intent.action.AUTOROTATION");
        paramContext.registerReceiver(this.mAutoRotation, paramIWindowManager);
        this.mSystemGestures = new SystemGesturesPointerEventListener(paramContext, new SystemGesturesPointerEventListener.Callbacks()
        {
          public void onDebug()
          {
          }

          public void onDown()
          {
            PhoneWindowManager.this.mOrientationListener.onTouchStart();
          }

          public void onFling(int paramInt)
          {
            if (PhoneWindowManager.this.mPowerManagerInternal != null)
              PhoneWindowManager.this.mPowerManagerInternal.powerHint(2, paramInt);
          }

          public void onSwipeCocktail(int paramInt1, int paramInt2)
          {
            if ((PhoneWindowManager.this.mTopFullscreenOpaqueWindowState == null) || ((PhoneWindowManager.this.mTopFullscreenOpaqueWindowState.getAttrs().samsungFlags & 0x40000000) == 0))
              PhoneWindowManager.this.mCocktailPhoneWindowManager.requestTransientCocktailBar();
          }

          public void onSwipeFromBottom()
          {
            if ((PhoneWindowManager.this.mNavigationBar != null) && (PhoneWindowManager.this.mNavigationBarOnBottom))
              PhoneWindowManager.this.requestTransientBars(PhoneWindowManager.this.mNavigationBar);
            if ((PhoneWindowManager.this.mCocktail180RotationEnabled == 1) && (PhoneWindowManager.this.mCurrentDisplayRotation == 2))
              PhoneWindowManager.this.mHandler.sendEmptyMessage(53);
          }

          public void onSwipeFromRight()
          {
            if ((PhoneWindowManager.this.mNavigationBar != null) && (!PhoneWindowManager.this.mNavigationBarOnBottom))
              PhoneWindowManager.this.requestTransientBars(PhoneWindowManager.this.mNavigationBar);
          }

          public void onSwipeFromTop()
          {
            if ((PhoneWindowManager.this.mStatusBar == null) || (PhoneWindowManager.this.mMultiPhoneWindowManager.getLongPressedMinimizeIcon() != null));
            do
              return;
            while (PhoneWindowManager.this.mForceHideStatusBarForCocktail);
            PhoneWindowManager.this.requestTransientBars(PhoneWindowManager.this.mStatusBar);
          }

          public void onSwipeFromTop(MotionEvent paramMotionEvent)
          {
          }

          public void onSwipeLeftCenterLeft()
          {
            if (PhoneWindowManager.this.mEasyOneHandEnabled > 0)
            {
              Message localMessage = PhoneWindowManager.this.mHandler.obtainMessage(56, Boolean.valueOf(true));
              localMessage.setAsynchronous(true);
              PhoneWindowManager.this.mHandler.sendMessage(localMessage);
            }
          }

          public void onSwipeRightCenterRight()
          {
            if (PhoneWindowManager.this.mEasyOneHandEnabled > 0)
            {
              Message localMessage = PhoneWindowManager.this.mHandler.obtainMessage(56, Boolean.valueOf(false));
              localMessage.setAsynchronous(true);
              PhoneWindowManager.this.mHandler.sendMessage(localMessage);
            }
          }

          public void onUpOrCancel()
          {
            PhoneWindowManager.this.mOrientationListener.onTouchEnd();
          }
        });
        this.mImmersiveModeConfirmation = new ImmersiveModeConfirmation(this.mContext);
        this.mWindowManagerFuncs.registerPointerEventListener(this.mSystemGestures);
        this.mVibrator = ((Vibrator)paramContext.getSystemService("vibrator"));
        paramIWindowManager = new IntentFilter("org.codeaurora.intent.action.WIFI_DISPLAY_VIDEO");
        paramContext.registerReceiver(this.mWifiDisplayReceiver, paramIWindowManager);
        this.mLongPressVibePattern = getLongIntArray(this.mContext.getResources(), 17235996);
        this.mVirtualKeyVibePattern = getLongIntArray(this.mContext.getResources(), 17235997);
        this.mKeyboardTapVibePattern = getLongIntArray(this.mContext.getResources(), 17235998);
        this.mClockTickVibePattern = getLongIntArray(this.mContext.getResources(), 17235999);
        this.mCalendarDateVibePattern = getLongIntArray(this.mContext.getResources(), 17236000);
        this.mSafeModeDisabledVibePattern = getLongIntArray(this.mContext.getResources(), 17236001);
        this.mSafeModeEnabledVibePattern = getLongIntArray(this.mContext.getResources(), 17236002);
        this.mContextClickVibePattern = getLongIntArray(this.mContext.getResources(), 17236004);
        this.mScreenshotChordEnabled = this.mContext.getResources().getBoolean(17956910);
        this.mGlobalKeyManager = new GlobalKeyManager(this.mContext);
        this.mSPWM = new SamsungPhoneWindowManager();
        this.mSPWM.init(this.mContext, this, this.mWindowManager, this.mWindowManagerFuncs);
        this.mSystemKeyManager = new SystemKeyManager();
        this.mMultitapKeyManager = new MultitapKeyManager(this, this.mSPWM);
        this.mLockPatternUtils = new LockPatternUtils(this.mContext);
        this.mMultiPhoneWindowManager = new MultiPhoneWindowManager();
        this.mMultiPhoneWindowManager.init(this.mContext, this.mWindowManager, this.mWindowManagerFuncs, this.mSPWM, this);
        this.mSPWM.setMultiPhoneWindowManager(this.mMultiPhoneWindowManager);
        this.mCocktailPhoneWindowManager = new CocktailPhoneWindowManager();
        this.mCocktailPhoneWindowManager.init(this.mContext, this.mWindowManager, this.mWindowManagerFuncs, this.mSPWM, this);
        this.mCoverCloseRotation = readRotation(17694942);
        this.mIsSupportFlipCover = this.mContext.getPackageManager().hasSystemFeature("com.sec.feature.cover.flip");
        this.mIsSupportSViewCover = this.mContext.getPackageManager().hasSystemFeature("com.sec.feature.cover.sview");
        if ((this.mIsSupportFlipCover) || (this.mIsSupportSViewCover))
        {
          this.mCoverManager = getCoverManager();
          this.mCoverState = new CoverState();
        }
        initializeHdmiState();
        paramIWindowManager = new IntentFilter();
        paramIWindowManager.addAction("android.intent.action.USBHID_MOUSE_EVENT");
        paramIWindowManager = paramContext.registerReceiver(this.mMouseConnectReceiver, paramIWindowManager);
        if (paramIWindowManager != null)
        {
          this.mMouseConnectedDock = paramIWindowManager.getIntExtra("android.intent.extra.device_state", 0);
          if (1 != this.mMouseConnectedDock)
            break label2086;
        }
        label2086: for (this.mMouseDockedFlag = true; ; this.mMouseDockedFlag = false)
        {
          this.mOldMouseDockedValue = this.mMouseDockedFlag;
          Slog.i("WindowManager", "SmartDock Connected  :  " + this.mMouseDockedFlag);
          if (!this.mPowerManager.isInteractive())
          {
            startedGoingToSleep(2);
            finishedGoingToSleep(2);
          }
          this.mWindowManagerInternal.registerAppTransitionListener(this.mStatusBarController.getAppTransitionListener());
          if ("2.0".equals(PersonaManager.getKnoxInfo().getString("version")))
            this.mIsKNOX2Supported = true;
          paramIWindowManager = new IntentFilter();
          paramIWindowManager.addAction("android.intent.action.BATTERY_CHANGED");
          paramContext.registerReceiver(this.mBatteryReceiver, paramIWindowManager);
          paramIWindowManager = new IntentFilter();
          paramIWindowManager.addAction("android.intent.action.ACTION_POWER_CONNECTED");
          paramIWindowManager.addAction("android.intent.action.ACTION_POWER_DISCONNECTED");
          paramContext.registerReceiver(this.mChargingReceiver, paramIWindowManager);
          this.mTspStateManager = new TspStateManager(this.mContext);
          return;
        }
      }
      catch (RemoteException paramIWindowManager)
      {
        break label247;
      }
    }
  }

  // ERROR //
  void initializeHdmiState()
  {
    // Byte code:
    //   0: iconst_1
    //   1: istore 5
    //   3: iconst_0
    //   4: istore_3
    //   5: iconst_0
    //   6: istore_1
    //   7: iload_3
    //   8: istore_2
    //   9: new 4902	java/io/File
    //   12: dup
    //   13: ldc_w 4904
    //   16: invokespecial 4905	java/io/File:<init>	(Ljava/lang/String;)V
    //   19: invokevirtual 4908	java/io/File:exists	()Z
    //   22: ifeq +90 -> 112
    //   25: aload_0
    //   26: getfield 1156	com/android/server/policy/PhoneWindowManager:mHDMIObserver	Landroid/os/UEventObserver;
    //   29: ldc_w 4910
    //   32: invokevirtual 4915	android/os/UEventObserver:startObserving	(Ljava/lang/String;)V
    //   35: aconst_null
    //   36: astore 9
    //   38: aconst_null
    //   39: astore 6
    //   41: aconst_null
    //   42: astore 8
    //   44: new 4917	java/io/FileReader
    //   47: dup
    //   48: ldc_w 4919
    //   51: invokespecial 4920	java/io/FileReader:<init>	(Ljava/lang/String;)V
    //   54: astore 7
    //   56: bipush 15
    //   58: newarray char
    //   60: astore 6
    //   62: aload 7
    //   64: aload 6
    //   66: invokevirtual 4924	java/io/FileReader:read	([C)I
    //   69: istore_2
    //   70: iload_2
    //   71: iconst_1
    //   72: if_icmple +26 -> 98
    //   75: new 988	java/lang/String
    //   78: dup
    //   79: aload 6
    //   81: iconst_0
    //   82: iload_2
    //   83: iconst_1
    //   84: isub
    //   85: invokespecial 4927	java/lang/String:<init>	([CII)V
    //   88: invokestatic 4930	java/lang/Integer:parseInt	(Ljava/lang/String;)I
    //   91: istore_1
    //   92: iload_1
    //   93: ifeq +50 -> 143
    //   96: iconst_1
    //   97: istore_1
    //   98: iload_1
    //   99: istore_2
    //   100: aload 7
    //   102: ifnull +10 -> 112
    //   105: aload 7
    //   107: invokevirtual 4933	java/io/FileReader:close	()V
    //   110: iload_1
    //   111: istore_2
    //   112: iload_2
    //   113: ifne +178 -> 291
    //   116: iconst_1
    //   117: istore 4
    //   119: aload_0
    //   120: iload 4
    //   122: putfield 3934	com/android/server/policy/PhoneWindowManager:mHdmiPlugged	Z
    //   125: aload_0
    //   126: getfield 3934	com/android/server/policy/PhoneWindowManager:mHdmiPlugged	Z
    //   129: ifne +168 -> 297
    //   132: iload 5
    //   134: istore 4
    //   136: aload_0
    //   137: iload 4
    //   139: invokevirtual 4936	com/android/server/policy/PhoneWindowManager:setHdmiPlugged	(Z)V
    //   142: return
    //   143: iconst_0
    //   144: istore_1
    //   145: goto -47 -> 98
    //   148: astore 6
    //   150: aload 8
    //   152: astore 7
    //   154: aload 6
    //   156: astore 8
    //   158: aload 7
    //   160: astore 6
    //   162: ldc_w 299
    //   165: new 1801	java/lang/StringBuilder
    //   168: dup
    //   169: invokespecial 1802	java/lang/StringBuilder:<init>	()V
    //   172: ldc_w 4938
    //   175: invokevirtual 1807	java/lang/StringBuilder:append	(Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   178: aload 8
    //   180: invokevirtual 2919	java/lang/StringBuilder:append	(Ljava/lang/Object;)Ljava/lang/StringBuilder;
    //   183: invokevirtual 1815	java/lang/StringBuilder:toString	()Ljava/lang/String;
    //   186: invokestatic 1951	android/util/Slog:w	(Ljava/lang/String;Ljava/lang/String;)I
    //   189: pop
    //   190: iload_3
    //   191: istore_2
    //   192: aload 7
    //   194: ifnull -82 -> 112
    //   197: aload 7
    //   199: invokevirtual 4933	java/io/FileReader:close	()V
    //   202: iload_3
    //   203: istore_2
    //   204: goto -92 -> 112
    //   207: astore 6
    //   209: iload_3
    //   210: istore_2
    //   211: goto -99 -> 112
    //   214: astore 8
    //   216: aload 9
    //   218: astore 7
    //   220: aload 7
    //   222: astore 6
    //   224: ldc_w 299
    //   227: new 1801	java/lang/StringBuilder
    //   230: dup
    //   231: invokespecial 1802	java/lang/StringBuilder:<init>	()V
    //   234: ldc_w 4938
    //   237: invokevirtual 1807	java/lang/StringBuilder:append	(Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   240: aload 8
    //   242: invokevirtual 2919	java/lang/StringBuilder:append	(Ljava/lang/Object;)Ljava/lang/StringBuilder;
    //   245: invokevirtual 1815	java/lang/StringBuilder:toString	()Ljava/lang/String;
    //   248: invokestatic 1951	android/util/Slog:w	(Ljava/lang/String;Ljava/lang/String;)I
    //   251: pop
    //   252: iload_3
    //   253: istore_2
    //   254: aload 7
    //   256: ifnull -144 -> 112
    //   259: aload 7
    //   261: invokevirtual 4933	java/io/FileReader:close	()V
    //   264: iload_3
    //   265: istore_2
    //   266: goto -154 -> 112
    //   269: astore 6
    //   271: iload_3
    //   272: istore_2
    //   273: goto -161 -> 112
    //   276: astore 7
    //   278: aload 6
    //   280: ifnull +8 -> 288
    //   283: aload 6
    //   285: invokevirtual 4933	java/io/FileReader:close	()V
    //   288: aload 7
    //   290: athrow
    //   291: iconst_0
    //   292: istore 4
    //   294: goto -175 -> 119
    //   297: iconst_0
    //   298: istore 4
    //   300: goto -164 -> 136
    //   303: astore 6
    //   305: iload_1
    //   306: istore_2
    //   307: goto -195 -> 112
    //   310: astore 6
    //   312: goto -24 -> 288
    //   315: astore 8
    //   317: aload 7
    //   319: astore 6
    //   321: aload 8
    //   323: astore 7
    //   325: goto -47 -> 278
    //   328: astore 8
    //   330: goto -110 -> 220
    //   333: astore 8
    //   335: goto -177 -> 158
    //
    // Exception table:
    //   from	to	target	type
    //   44	56	148	java/io/IOException
    //   197	202	207	java/io/IOException
    //   44	56	214	java/lang/NumberFormatException
    //   259	264	269	java/io/IOException
    //   44	56	276	finally
    //   162	190	276	finally
    //   224	252	276	finally
    //   105	110	303	java/io/IOException
    //   283	288	310	java/io/IOException
    //   56	70	315	finally
    //   75	92	315	finally
    //   56	70	328	java/lang/NumberFormatException
    //   75	92	328	java/lang/NumberFormatException
    //   56	70	333	java/io/IOException
    //   75	92	333	java/io/IOException
  }

  // ERROR //
  public long interceptKeyBeforeDispatching(WindowManagerPolicy.WindowState paramWindowState, KeyEvent paramKeyEvent, int paramInt)
  {
    // Byte code:
    //   0: aload_0
    //   1: invokevirtual 4941	com/android/server/policy/PhoneWindowManager:keyguardOn	()Z
    //   4: istore 10
    //   6: aload_2
    //   7: invokevirtual 1669	android/view/KeyEvent:getKeyCode	()I
    //   10: istore_3
    //   11: aload_2
    //   12: invokevirtual 1693	android/view/KeyEvent:getRepeatCount	()I
    //   15: istore 6
    //   17: aload_2
    //   18: invokevirtual 3838	android/view/KeyEvent:getMetaState	()I
    //   21: istore 4
    //   23: aload_2
    //   24: invokevirtual 2254	android/view/KeyEvent:getFlags	()I
    //   27: istore 5
    //   29: aload_2
    //   30: invokevirtual 1666	android/view/KeyEvent:getAction	()I
    //   33: ifne +132 -> 165
    //   36: iconst_1
    //   37: istore 9
    //   39: aload_2
    //   40: invokevirtual 4944	android/view/KeyEvent:isCanceled	()Z
    //   43: istore 11
    //   45: getstatic 837	com/android/server/policy/PhoneWindowManager:DEBUG_INPUT	Z
    //   48: ifeq +87 -> 135
    //   51: ldc_w 299
    //   54: new 1801	java/lang/StringBuilder
    //   57: dup
    //   58: invokespecial 1802	java/lang/StringBuilder:<init>	()V
    //   61: ldc_w 4946
    //   64: invokevirtual 1807	java/lang/StringBuilder:append	(Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   67: iload_3
    //   68: invokevirtual 2571	java/lang/StringBuilder:append	(I)Ljava/lang/StringBuilder;
    //   71: ldc_w 4948
    //   74: invokevirtual 1807	java/lang/StringBuilder:append	(Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   77: iload 9
    //   79: invokevirtual 1810	java/lang/StringBuilder:append	(Z)Ljava/lang/StringBuilder;
    //   82: ldc_w 4950
    //   85: invokevirtual 1807	java/lang/StringBuilder:append	(Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   88: iload 6
    //   90: invokevirtual 2571	java/lang/StringBuilder:append	(I)Ljava/lang/StringBuilder;
    //   93: ldc_w 4952
    //   96: invokevirtual 1807	java/lang/StringBuilder:append	(Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   99: iload 10
    //   101: invokevirtual 1810	java/lang/StringBuilder:append	(Z)Ljava/lang/StringBuilder;
    //   104: ldc_w 4112
    //   107: invokevirtual 1807	java/lang/StringBuilder:append	(Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   110: aload_0
    //   111: getfield 4114	com/android/server/policy/PhoneWindowManager:mHomePressed	Z
    //   114: invokevirtual 1810	java/lang/StringBuilder:append	(Z)Ljava/lang/StringBuilder;
    //   117: ldc_w 4954
    //   120: invokevirtual 1807	java/lang/StringBuilder:append	(Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   123: iload 11
    //   125: invokevirtual 1810	java/lang/StringBuilder:append	(Z)Ljava/lang/StringBuilder;
    //   128: invokevirtual 1815	java/lang/StringBuilder:toString	()Ljava/lang/String;
    //   131: invokestatic 1868	android/util/Log:d	(Ljava/lang/String;Ljava/lang/String;)I
    //   134: pop
    //   135: invokestatic 1467	android/os/SystemClock:uptimeMillis	()J
    //   138: lstore 12
    //   140: aload_0
    //   141: getfield 1315	com/android/server/policy/PhoneWindowManager:mSPWM	Lcom/android/server/policy/sec/SamsungPhoneWindowManager;
    //   144: iload 5
    //   146: invokevirtual 4958	com/android/server/policy/sec/SamsungPhoneWindowManager:getTimeoutTimeOfKeyCombination	(I)J
    //   149: lstore 14
    //   151: lload 12
    //   153: lload 14
    //   155: lcmp
    //   156: ifge +15 -> 171
    //   159: lload 14
    //   161: lload 12
    //   163: lsub
    //   164: lreturn
    //   165: iconst_0
    //   166: istore 9
    //   168: goto -129 -> 39
    //   171: aload_0
    //   172: getfield 1315	com/android/server/policy/PhoneWindowManager:mSPWM	Lcom/android/server/policy/sec/SamsungPhoneWindowManager;
    //   175: aload_2
    //   176: invokevirtual 4961	com/android/server/policy/sec/SamsungPhoneWindowManager:interceptKeyBeforeDispatching	(Landroid/view/KeyEvent;)Z
    //   179: ifeq +7 -> 186
    //   182: ldc2_w 4962
    //   185: lreturn
    //   186: aload_0
    //   187: getfield 1762	com/android/server/policy/PhoneWindowManager:mSystemKeyManager	Lcom/android/server/policy/sec/SystemKeyManager;
    //   190: iload_3
    //   191: invokevirtual 1767	com/android/server/policy/sec/SystemKeyManager:isSystemKeyEventRequested	(I)Z
    //   194: ifeq +57 -> 251
    //   197: aload_1
    //   198: ifnull +46 -> 244
    //   201: aload_1
    //   202: invokeinterface 1545 1 0
    //   207: astore_1
    //   208: aload_1
    //   209: ifnull +40 -> 249
    //   212: aload_1
    //   213: getfield 1707	android/view/WindowManager$LayoutParams:type	I
    //   216: sipush 2000
    //   219: if_icmpne +30 -> 249
    //   222: aload_1
    //   223: getfield 1716	android/view/WindowManager$LayoutParams:privateFlags	I
    //   226: sipush 1024
    //   229: iand
    //   230: ifne +19 -> 249
    //   233: aload_0
    //   234: ldc_w 291
    //   237: invokevirtual 2449	com/android/server/policy/PhoneWindowManager:sendCloseSystemWindows	(Ljava/lang/String;)V
    //   240: ldc2_w 4964
    //   243: lreturn
    //   244: aconst_null
    //   245: astore_1
    //   246: goto -38 -> 208
    //   249: lconst_0
    //   250: lreturn
    //   251: aload_0
    //   252: getfield 4967	com/android/server/policy/PhoneWindowManager:mPendingMetaAction	Z
    //   255: ifeq +15 -> 270
    //   258: iload_3
    //   259: invokestatic 4970	android/view/KeyEvent:isMetaKey	(I)Z
    //   262: ifne +8 -> 270
    //   265: aload_0
    //   266: iconst_0
    //   267: putfield 4967	com/android/server/policy/PhoneWindowManager:mPendingMetaAction	Z
    //   270: aload_0
    //   271: getfield 1083	com/android/server/policy/PhoneWindowManager:mWatchLaunching	Z
    //   274: ifeq +17 -> 291
    //   277: ldc_w 299
    //   280: ldc_w 4972
    //   283: invokestatic 2578	android/util/Slog:v	(Ljava/lang/String;Ljava/lang/String;)I
    //   286: pop
    //   287: ldc2_w 4962
    //   290: lreturn
    //   291: iload_3
    //   292: iconst_3
    //   293: if_icmpne +576 -> 869
    //   296: aload_0
    //   297: invokevirtual 4975	com/android/server/policy/PhoneWindowManager:isSharedDeviceUnlockScreens	()Z
    //   300: ifeq +7 -> 307
    //   303: ldc2_w 4962
    //   306: lreturn
    //   307: iload 9
    //   309: ifne +267 -> 576
    //   312: aload_0
    //   313: invokespecial 4976	com/android/server/policy/PhoneWindowManager:cancelPreloadRecentApps	()V
    //   316: aload_0
    //   317: getfield 4114	com/android/server/policy/PhoneWindowManager:mHomePressed	Z
    //   320: ifne +17 -> 337
    //   323: ldc_w 299
    //   326: ldc_w 4978
    //   329: invokestatic 1818	android/util/Log:i	(Ljava/lang/String;Ljava/lang/String;)I
    //   332: pop
    //   333: ldc2_w 4962
    //   336: lreturn
    //   337: aload_0
    //   338: iconst_0
    //   339: putfield 4114	com/android/server/policy/PhoneWindowManager:mHomePressed	Z
    //   342: aload_0
    //   343: getfield 2091	com/android/server/policy/PhoneWindowManager:mHomeConsumed	Z
    //   346: ifeq +12 -> 358
    //   349: aload_0
    //   350: iconst_0
    //   351: putfield 2091	com/android/server/policy/PhoneWindowManager:mHomeConsumed	Z
    //   354: ldc2_w 4962
    //   357: lreturn
    //   358: iload 11
    //   360: ifeq +17 -> 377
    //   363: ldc_w 299
    //   366: ldc_w 4980
    //   369: invokestatic 1818	android/util/Log:i	(Ljava/lang/String;Ljava/lang/String;)I
    //   372: pop
    //   373: ldc2_w 4962
    //   376: lreturn
    //   377: aload_0
    //   378: invokevirtual 2173	com/android/server/policy/PhoneWindowManager:getTelecommService	()Landroid/telecom/TelecomManager;
    //   381: astore_1
    //   382: aload_1
    //   383: ifnull +69 -> 452
    //   386: aload_1
    //   387: invokevirtual 2261	android/telecom/TelecomManager:isRinging	()Z
    //   390: ifeq +62 -> 452
    //   393: aload_0
    //   394: getfield 1315	com/android/server/policy/PhoneWindowManager:mSPWM	Lcom/android/server/policy/sec/SamsungPhoneWindowManager;
    //   397: invokevirtual 4297	com/android/server/policy/sec/SamsungPhoneWindowManager:isTphoneRelaxMode	()Z
    //   400: ifne +52 -> 452
    //   403: aload_0
    //   404: getfield 1315	com/android/server/policy/PhoneWindowManager:mSPWM	Lcom/android/server/policy/sec/SamsungPhoneWindowManager;
    //   407: invokevirtual 4983	com/android/server/policy/sec/SamsungPhoneWindowManager:isAnyKeyMode	()Z
    //   410: ifeq +28 -> 438
    //   413: ldc_w 299
    //   416: ldc_w 4985
    //   419: invokestatic 1818	android/util/Log:i	(Ljava/lang/String;Ljava/lang/String;)I
    //   422: pop
    //   423: aload_1
    //   424: invokevirtual 4988	android/telecom/TelecomManager:acceptRingingCall	()V
    //   427: aload_0
    //   428: getfield 1315	com/android/server/policy/PhoneWindowManager:mSPWM	Lcom/android/server/policy/sec/SamsungPhoneWindowManager;
    //   431: ldc_w 4990
    //   434: aconst_null
    //   435: invokevirtual 2269	com/android/server/policy/sec/SamsungPhoneWindowManager:insertLog	(Ljava/lang/String;Ljava/lang/String;)V
    //   438: ldc_w 299
    //   441: ldc_w 4992
    //   444: invokestatic 1818	android/util/Log:i	(Ljava/lang/String;Ljava/lang/String;)I
    //   447: pop
    //   448: ldc2_w 4962
    //   451: lreturn
    //   452: aload_0
    //   453: getfield 4833	com/android/server/policy/PhoneWindowManager:mMultitapKeyManager	Lcom/android/server/policy/sec/MultitapKeyManager;
    //   456: aload_2
    //   457: invokevirtual 4995	com/android/server/policy/sec/MultitapKeyManager:dispatchMultitapKeyEvent	(Landroid/view/KeyEvent;)Z
    //   460: ifeq +7 -> 467
    //   463: ldc2_w 4962
    //   466: lreturn
    //   467: aload_0
    //   468: getfield 2089	com/android/server/policy/PhoneWindowManager:mDoubleTapOnHomeBehavior	I
    //   471: ifeq +39 -> 510
    //   474: aload_0
    //   475: getfield 1556	com/android/server/policy/PhoneWindowManager:mHandler	Landroid/os/Handler;
    //   478: aload_0
    //   479: getfield 1192	com/android/server/policy/PhoneWindowManager:mHomeDoubleTapTimeoutRunnable	Ljava/lang/Runnable;
    //   482: invokevirtual 1566	android/os/Handler:removeCallbacks	(Ljava/lang/Runnable;)V
    //   485: aload_0
    //   486: iconst_1
    //   487: putfield 4997	com/android/server/policy/PhoneWindowManager:mHomeDoubleTapPending	Z
    //   490: aload_0
    //   491: getfield 1556	com/android/server/policy/PhoneWindowManager:mHandler	Landroid/os/Handler;
    //   494: aload_0
    //   495: getfield 1192	com/android/server/policy/PhoneWindowManager:mHomeDoubleTapTimeoutRunnable	Ljava/lang/Runnable;
    //   498: invokestatic 2209	android/view/ViewConfiguration:getDoubleTapTimeout	()I
    //   501: i2l
    //   502: invokevirtual 2132	android/os/Handler:postDelayed	(Ljava/lang/Runnable;J)Z
    //   505: pop
    //   506: ldc2_w 4962
    //   509: lreturn
    //   510: aload_0
    //   511: getfield 4999	com/android/server/policy/PhoneWindowManager:mTelephonyManager	Landroid/telephony/TelephonyManager;
    //   514: ifnonnull +20 -> 534
    //   517: aload_0
    //   518: aload_0
    //   519: getfield 1619	com/android/server/policy/PhoneWindowManager:mContext	Landroid/content/Context;
    //   522: ldc_w 5001
    //   525: invokevirtual 1627	android/content/Context:getSystemService	(Ljava/lang/String;)Ljava/lang/Object;
    //   528: checkcast 5003	android/telephony/TelephonyManager
    //   531: putfield 4999	com/android/server/policy/PhoneWindowManager:mTelephonyManager	Landroid/telephony/TelephonyManager;
    //   534: aload_0
    //   535: getfield 4999	com/android/server/policy/PhoneWindowManager:mTelephonyManager	Landroid/telephony/TelephonyManager;
    //   538: invokevirtual 5006	android/telephony/TelephonyManager:getCallState	()I
    //   541: iconst_2
    //   542: if_icmpne +26 -> 568
    //   545: aload_0
    //   546: getfield 4999	com/android/server/policy/PhoneWindowManager:mTelephonyManager	Landroid/telephony/TelephonyManager;
    //   549: invokevirtual 5009	android/telephony/TelephonyManager:isVideoCall	()Z
    //   552: ifne +16 -> 568
    //   555: aload_0
    //   556: getfield 1315	com/android/server/policy/PhoneWindowManager:mSPWM	Lcom/android/server/policy/sec/SamsungPhoneWindowManager;
    //   559: ldc_w 5011
    //   562: ldc_w 5013
    //   565: invokevirtual 2269	com/android/server/policy/sec/SamsungPhoneWindowManager:insertLog	(Ljava/lang/String;Ljava/lang/String;)V
    //   568: aload_0
    //   569: invokespecial 1354	com/android/server/policy/PhoneWindowManager:handleShortPressOnHome	()V
    //   572: ldc2_w 4962
    //   575: lreturn
    //   576: iconst_0
    //   577: istore 5
    //   579: iconst_0
    //   580: istore_3
    //   581: aload_1
    //   582: ifnull +128 -> 710
    //   585: aload_1
    //   586: invokeinterface 1545 1 0
    //   591: astore_1
    //   592: iload 5
    //   594: istore 4
    //   596: aload_0
    //   597: getfield 1151	com/android/server/policy/PhoneWindowManager:mNeedTriggerOWC	Z
    //   600: ifne +126 -> 726
    //   603: iload 5
    //   605: istore 4
    //   607: aload_1
    //   608: ifnull +118 -> 726
    //   611: aload_1
    //   612: getfield 1707	android/view/WindowManager$LayoutParams:type	I
    //   615: istore 7
    //   617: iload 7
    //   619: sipush 2029
    //   622: if_icmpeq +41 -> 663
    //   625: iload 7
    //   627: sipush 2009
    //   630: if_icmpeq +33 -> 663
    //   633: iload 7
    //   635: sipush 2023
    //   638: if_icmpeq +25 -> 663
    //   641: aload_1
    //   642: getfield 2770	android/view/WindowManager$LayoutParams:samsungFlags	I
    //   645: ldc_w 2485
    //   648: iand
    //   649: ifne +14 -> 663
    //   652: aload_1
    //   653: getfield 1716	android/view/WindowManager$LayoutParams:privateFlags	I
    //   656: sipush 1024
    //   659: iand
    //   660: ifeq +16 -> 676
    //   663: aload_1
    //   664: getfield 1716	android/view/WindowManager$LayoutParams:privateFlags	I
    //   667: sipush 1024
    //   670: iand
    //   671: ifeq +44 -> 715
    //   674: iconst_1
    //   675: istore_3
    //   676: getstatic 913	com/android/server/policy/PhoneWindowManager:WINDOW_TYPES_WHERE_HOME_DOESNT_WORK	[I
    //   679: arraylength
    //   680: istore 8
    //   682: iconst_0
    //   683: istore 5
    //   685: iload_3
    //   686: istore 4
    //   688: iload 5
    //   690: iload 8
    //   692: if_icmpge +34 -> 726
    //   695: iload 7
    //   697: getstatic 913	com/android/server/policy/PhoneWindowManager:WINDOW_TYPES_WHERE_HOME_DOESNT_WORK	[I
    //   700: iload 5
    //   702: iaload
    //   703: if_icmpne +14 -> 717
    //   706: ldc2_w 4962
    //   709: lreturn
    //   710: aconst_null
    //   711: astore_1
    //   712: goto -120 -> 592
    //   715: lconst_0
    //   716: lreturn
    //   717: iload 5
    //   719: iconst_1
    //   720: iadd
    //   721: istore 5
    //   723: goto -38 -> 685
    //   726: iload 6
    //   728: ifne +98 -> 826
    //   731: aload_0
    //   732: iconst_1
    //   733: putfield 4114	com/android/server/policy/PhoneWindowManager:mHomePressed	Z
    //   736: aload_0
    //   737: iconst_0
    //   738: putfield 5015	com/android/server/policy/PhoneWindowManager:mHomeLongPressCanceled	Z
    //   741: aload_0
    //   742: getfield 4997	com/android/server/policy/PhoneWindowManager:mHomeDoubleTapPending	Z
    //   745: ifeq +58 -> 803
    //   748: aload_0
    //   749: iconst_0
    //   750: putfield 4997	com/android/server/policy/PhoneWindowManager:mHomeDoubleTapPending	Z
    //   753: aload_0
    //   754: getfield 1556	com/android/server/policy/PhoneWindowManager:mHandler	Landroid/os/Handler;
    //   757: aload_0
    //   758: getfield 1192	com/android/server/policy/PhoneWindowManager:mHomeDoubleTapTimeoutRunnable	Ljava/lang/Runnable;
    //   761: invokevirtual 1566	android/os/Handler:removeCallbacks	(Ljava/lang/Runnable;)V
    //   764: aload_0
    //   765: invokespecial 5017	com/android/server/policy/PhoneWindowManager:handleDoubleTapOnHome	()V
    //   768: aload_0
    //   769: getfield 4833	com/android/server/policy/PhoneWindowManager:mMultitapKeyManager	Lcom/android/server/policy/sec/MultitapKeyManager;
    //   772: aload_2
    //   773: invokevirtual 4995	com/android/server/policy/sec/MultitapKeyManager:dispatchMultitapKeyEvent	(Landroid/view/KeyEvent;)Z
    //   776: pop
    //   777: aload_0
    //   778: getfield 1151	com/android/server/policy/PhoneWindowManager:mNeedTriggerOWC	Z
    //   781: ifne +15 -> 796
    //   784: aload_0
    //   785: invokevirtual 2974	com/android/server/policy/PhoneWindowManager:isStatusBarKeyguard	()Z
    //   788: ifeq +8 -> 796
    //   791: aload_0
    //   792: iconst_1
    //   793: putfield 5015	com/android/server/policy/PhoneWindowManager:mHomeLongPressCanceled	Z
    //   796: iload 4
    //   798: ifeq +67 -> 865
    //   801: lconst_0
    //   802: lreturn
    //   803: aload_0
    //   804: getfield 2111	com/android/server/policy/PhoneWindowManager:mLongPressOnHomeBehavior	I
    //   807: iconst_1
    //   808: if_icmpeq +11 -> 819
    //   811: aload_0
    //   812: getfield 2089	com/android/server/policy/PhoneWindowManager:mDoubleTapOnHomeBehavior	I
    //   815: iconst_1
    //   816: if_icmpne -48 -> 768
    //   819: aload_0
    //   820: invokespecial 5018	com/android/server/policy/PhoneWindowManager:preloadRecentApps	()V
    //   823: goto -55 -> 768
    //   826: aload_2
    //   827: invokevirtual 2254	android/view/KeyEvent:getFlags	()I
    //   830: sipush 128
    //   833: iand
    //   834: ifeq -38 -> 796
    //   837: aload_0
    //   838: getfield 5015	com/android/server/policy/PhoneWindowManager:mHomeLongPressCanceled	Z
    //   841: ifne -45 -> 796
    //   844: aload_0
    //   845: getfield 4833	com/android/server/policy/PhoneWindowManager:mMultitapKeyManager	Lcom/android/server/policy/sec/MultitapKeyManager;
    //   848: invokevirtual 5021	com/android/server/policy/sec/MultitapKeyManager:isHomeConsumed	()Z
    //   851: ifne -55 -> 796
    //   854: aload_0
    //   855: aload_2
    //   856: invokevirtual 3873	android/view/KeyEvent:getDeviceId	()I
    //   859: invokespecial 5023	com/android/server/policy/PhoneWindowManager:handleLongPressOnHome	(I)V
    //   862: goto -66 -> 796
    //   865: ldc2_w 4962
    //   868: lreturn
    //   869: iload_3
    //   870: bipush 82
    //   872: if_icmpne +229 -> 1101
    //   875: aload_1
    //   876: ifnull +45 -> 921
    //   879: aload_1
    //   880: invokeinterface 3022 1 0
    //   885: invokevirtual 3025	com/samsung/android/multiwindow/MultiWindowStyle:getType	()I
    //   888: iconst_1
    //   889: if_icmpne +32 -> 921
    //   892: aload_1
    //   893: invokeinterface 5026 1 0
    //   898: ifeq +23 -> 921
    //   901: getstatic 837	com/android/server/policy/PhoneWindowManager:DEBUG_INPUT	Z
    //   904: ifeq +13 -> 917
    //   907: ldc_w 299
    //   910: ldc_w 5028
    //   913: invokestatic 1459	android/util/Slog:d	(Ljava/lang/String;Ljava/lang/String;)I
    //   916: pop
    //   917: ldc2_w 4962
    //   920: lreturn
    //   921: iload 9
    //   923: ifeq +55 -> 978
    //   926: iload 6
    //   928: ifne +50 -> 978
    //   931: aload_0
    //   932: getfield 923	com/android/server/policy/PhoneWindowManager:mEnableShiftMenuBugReports	Z
    //   935: ifeq +85 -> 1020
    //   938: iload 4
    //   940: iconst_1
    //   941: iand
    //   942: iconst_1
    //   943: if_icmpne +77 -> 1020
    //   946: new 2062	android/content/Intent
    //   949: dup
    //   950: ldc_w 5030
    //   953: invokespecial 2484	android/content/Intent:<init>	(Ljava/lang/String;)V
    //   956: astore_1
    //   957: aload_0
    //   958: getfield 1619	com/android/server/policy/PhoneWindowManager:mContext	Landroid/content/Context;
    //   961: aload_1
    //   962: getstatic 2498	android/os/UserHandle:CURRENT	Landroid/os/UserHandle;
    //   965: aconst_null
    //   966: aconst_null
    //   967: aconst_null
    //   968: iconst_0
    //   969: aconst_null
    //   970: aconst_null
    //   971: invokevirtual 5034	android/content/Context:sendOrderedBroadcastAsUser	(Landroid/content/Intent;Landroid/os/UserHandle;Ljava/lang/String;Landroid/content/BroadcastReceiver;Landroid/os/Handler;ILjava/lang/String;Landroid/os/Bundle;)V
    //   974: ldc2_w 4962
    //   977: lreturn
    //   978: iload 9
    //   980: ifne +17 -> 997
    //   983: aload_0
    //   984: getfield 1315	com/android/server/policy/PhoneWindowManager:mSPWM	Lcom/android/server/policy/sec/SamsungPhoneWindowManager;
    //   987: invokevirtual 5037	com/android/server/policy/sec/SamsungPhoneWindowManager:isMenuConsumed	()Z
    //   990: ifeq +30 -> 1020
    //   993: ldc2_w 4962
    //   996: lreturn
    //   997: aload_2
    //   998: invokevirtual 2254	android/view/KeyEvent:getFlags	()I
    //   1001: sipush 128
    //   1004: iand
    //   1005: ifeq +15 -> 1020
    //   1008: iload 10
    //   1010: ifne +10 -> 1020
    //   1013: aload_0
    //   1014: getfield 1315	com/android/server/policy/PhoneWindowManager:mSPWM	Lcom/android/server/policy/sec/SamsungPhoneWindowManager;
    //   1017: invokevirtual 5040	com/android/server/policy/sec/SamsungPhoneWindowManager:handleLongPressOnMenu	()V
    //   1020: aload_0
    //   1021: getfield 5042	com/android/server/policy/PhoneWindowManager:mSearchKeyShortcutPending	Z
    //   1024: ifeq +1311 -> 2335
    //   1027: aload_2
    //   1028: invokevirtual 3846	android/view/KeyEvent:getKeyCharacterMap	()Landroid/view/KeyCharacterMap;
    //   1031: astore_1
    //   1032: aload_1
    //   1033: iload_3
    //   1034: invokevirtual 5045	android/view/KeyCharacterMap:isPrintingKey	(I)Z
    //   1037: ifeq +1298 -> 2335
    //   1040: aload_0
    //   1041: iconst_1
    //   1042: putfield 5047	com/android/server/policy/PhoneWindowManager:mConsumeSearchKeyUp	Z
    //   1045: aload_0
    //   1046: iconst_0
    //   1047: putfield 5042	com/android/server/policy/PhoneWindowManager:mSearchKeyShortcutPending	Z
    //   1050: iload 9
    //   1052: ifeq +45 -> 1097
    //   1055: iload 6
    //   1057: ifne +40 -> 1097
    //   1060: iload 10
    //   1062: ifne +35 -> 1097
    //   1065: aload_0
    //   1066: getfield 4635	com/android/server/policy/PhoneWindowManager:mShortcutManager	Lcom/android/server/policy/ShortcutManager;
    //   1069: aload_1
    //   1070: iload_3
    //   1071: iload 4
    //   1073: invokevirtual 5051	com/android/server/policy/ShortcutManager:getIntent	(Landroid/view/KeyCharacterMap;II)Landroid/content/Intent;
    //   1076: astore_1
    //   1077: aload_1
    //   1078: ifnull +1224 -> 2302
    //   1081: aload_1
    //   1082: ldc_w 2485
    //   1085: invokevirtual 4650	android/content/Intent:addFlags	(I)Landroid/content/Intent;
    //   1088: pop
    //   1089: aload_0
    //   1090: aload_1
    //   1091: getstatic 2498	android/os/UserHandle:CURRENT	Landroid/os/UserHandle;
    //   1094: invokespecial 2502	com/android/server/policy/PhoneWindowManager:startActivityAsUser	(Landroid/content/Intent;Landroid/os/UserHandle;)V
    //   1097: ldc2_w 4962
    //   1100: lreturn
    //   1101: iload_3
    //   1102: bipush 84
    //   1104: if_icmpne +46 -> 1150
    //   1107: iload 9
    //   1109: ifeq +20 -> 1129
    //   1112: iload 6
    //   1114: ifne +13 -> 1127
    //   1117: aload_0
    //   1118: iconst_1
    //   1119: putfield 5042	com/android/server/policy/PhoneWindowManager:mSearchKeyShortcutPending	Z
    //   1122: aload_0
    //   1123: iconst_0
    //   1124: putfield 5047	com/android/server/policy/PhoneWindowManager:mConsumeSearchKeyUp	Z
    //   1127: lconst_0
    //   1128: lreturn
    //   1129: aload_0
    //   1130: iconst_0
    //   1131: putfield 5042	com/android/server/policy/PhoneWindowManager:mSearchKeyShortcutPending	Z
    //   1134: aload_0
    //   1135: getfield 5047	com/android/server/policy/PhoneWindowManager:mConsumeSearchKeyUp	Z
    //   1138: ifeq -11 -> 1127
    //   1141: aload_0
    //   1142: iconst_0
    //   1143: putfield 5047	com/android/server/policy/PhoneWindowManager:mConsumeSearchKeyUp	Z
    //   1146: ldc2_w 4962
    //   1149: lreturn
    //   1150: iload_3
    //   1151: sipush 1048
    //   1154: if_icmpne +147 -> 1301
    //   1157: iload 9
    //   1159: ifeq +140 -> 1299
    //   1162: ldc_w 299
    //   1165: ldc_w 5053
    //   1168: invokestatic 1868	android/util/Log:d	(Ljava/lang/String;Ljava/lang/String;)I
    //   1171: pop
    //   1172: ldc_w 5055
    //   1175: iconst_0
    //   1176: ldc_w 5057
    //   1179: invokestatic 5063	com/samsung/android/telephony/MultiSimManager:getTelephonyProperty	(Ljava/lang/String;ILjava/lang/String;)Ljava/lang/String;
    //   1182: astore_1
    //   1183: ldc_w 5055
    //   1186: iconst_1
    //   1187: ldc_w 5057
    //   1190: invokestatic 5063	com/samsung/android/telephony/MultiSimManager:getTelephonyProperty	(Ljava/lang/String;ILjava/lang/String;)Ljava/lang/String;
    //   1193: astore_2
    //   1194: aload_1
    //   1195: ldc_w 5057
    //   1198: invokevirtual 1784	java/lang/String:equals	(Ljava/lang/Object;)Z
    //   1201: ifne +13 -> 1214
    //   1204: aload_2
    //   1205: ldc_w 5057
    //   1208: invokevirtual 1784	java/lang/String:equals	(Ljava/lang/Object;)Z
    //   1211: ifeq +5 -> 1216
    //   1214: lconst_0
    //   1215: lreturn
    //   1216: aload_0
    //   1217: getfield 1619	com/android/server/policy/PhoneWindowManager:mContext	Landroid/content/Context;
    //   1220: invokevirtual 2050	android/content/Context:getContentResolver	()Landroid/content/ContentResolver;
    //   1223: ldc_w 5065
    //   1226: iconst_1
    //   1227: invokestatic 2057	android/provider/Settings$System:getInt	(Landroid/content/ContentResolver;Ljava/lang/String;I)I
    //   1230: ifne +34 -> 1264
    //   1233: iconst_1
    //   1234: istore_3
    //   1235: aload_0
    //   1236: getfield 1619	com/android/server/policy/PhoneWindowManager:mContext	Landroid/content/Context;
    //   1239: invokevirtual 2050	android/content/Context:getContentResolver	()Landroid/content/ContentResolver;
    //   1242: ldc_w 5067
    //   1245: iconst_1
    //   1246: invokestatic 2057	android/provider/Settings$System:getInt	(Landroid/content/ContentResolver;Ljava/lang/String;I)I
    //   1249: ifne +20 -> 1269
    //   1252: iconst_1
    //   1253: istore 4
    //   1255: iload_3
    //   1256: iload 4
    //   1258: ior
    //   1259: ifeq +16 -> 1275
    //   1262: lconst_0
    //   1263: lreturn
    //   1264: iconst_0
    //   1265: istore_3
    //   1266: goto -31 -> 1235
    //   1269: iconst_0
    //   1270: istore 4
    //   1272: goto -17 -> 1255
    //   1275: invokestatic 5072	android/telephony/SubscriptionManager:getDefaultVoicePhoneId	()I
    //   1278: istore_3
    //   1279: aload_0
    //   1280: getfield 1619	com/android/server/policy/PhoneWindowManager:mContext	Landroid/content/Context;
    //   1283: invokestatic 5076	android/telephony/SubscriptionManager:from	(Landroid/content/Context;)Landroid/telephony/SubscriptionManager;
    //   1286: iload_3
    //   1287: iconst_1
    //   1288: iadd
    //   1289: iconst_2
    //   1290: irem
    //   1291: invokestatic 5079	android/telephony/SubscriptionManager:getSubId	(I)[I
    //   1294: iconst_0
    //   1295: iaload
    //   1296: invokevirtual 5082	android/telephony/SubscriptionManager:setDefaultVoiceSubId	(I)V
    //   1299: lconst_0
    //   1300: lreturn
    //   1301: iload_3
    //   1302: sipush 187
    //   1305: if_icmpeq +10 -> 1315
    //   1308: iload_3
    //   1309: sipush 1001
    //   1312: if_icmpne +321 -> 1633
    //   1315: iload 11
    //   1317: ifeq +11 -> 1328
    //   1320: aload_0
    //   1321: invokespecial 4976	com/android/server/policy/PhoneWindowManager:cancelPreloadRecentApps	()V
    //   1324: ldc2_w 4962
    //   1327: lreturn
    //   1328: aload_0
    //   1329: invokevirtual 5085	com/android/server/policy/PhoneWindowManager:isSharedDeviceKeyguardOn	()Z
    //   1332: ifeq +11 -> 1343
    //   1335: aload_0
    //   1336: invokespecial 4976	com/android/server/policy/PhoneWindowManager:cancelPreloadRecentApps	()V
    //   1339: ldc2_w 4962
    //   1342: lreturn
    //   1343: aload_0
    //   1344: invokevirtual 4975	com/android/server/policy/PhoneWindowManager:isSharedDeviceUnlockScreens	()Z
    //   1347: ifeq +11 -> 1358
    //   1350: aload_0
    //   1351: invokespecial 4976	com/android/server/policy/PhoneWindowManager:cancelPreloadRecentApps	()V
    //   1354: ldc2_w 4962
    //   1357: lreturn
    //   1358: aload_0
    //   1359: getfield 3447	com/android/server/policy/PhoneWindowManager:mFakeFocusedWindow	Landroid/view/WindowManagerPolicy$WindowState;
    //   1362: ifnull +131 -> 1493
    //   1365: aload_0
    //   1366: getfield 1904	com/android/server/policy/PhoneWindowManager:mFocusedWindow	Landroid/view/WindowManagerPolicy$WindowState;
    //   1369: ifnull +124 -> 1493
    //   1372: aload_0
    //   1373: getfield 3447	com/android/server/policy/PhoneWindowManager:mFakeFocusedWindow	Landroid/view/WindowManagerPolicy$WindowState;
    //   1376: invokeinterface 3077 1 0
    //   1381: aload_0
    //   1382: getfield 1904	com/android/server/policy/PhoneWindowManager:mFocusedWindow	Landroid/view/WindowManagerPolicy$WindowState;
    //   1385: invokeinterface 3077 1 0
    //   1390: if_icmple +103 -> 1493
    //   1393: aload_0
    //   1394: getfield 3447	com/android/server/policy/PhoneWindowManager:mFakeFocusedWindow	Landroid/view/WindowManagerPolicy$WindowState;
    //   1397: invokeinterface 1545 1 0
    //   1402: invokevirtual 4398	android/view/WindowManager$LayoutParams:getTitle	()Ljava/lang/CharSequence;
    //   1405: invokeinterface 4401 1 0
    //   1410: ldc_w 4403
    //   1413: invokevirtual 4407	java/lang/String:split	(Ljava/lang/String;)[Ljava/lang/String;
    //   1416: astore_1
    //   1417: aload_1
    //   1418: ifnull +75 -> 1493
    //   1421: aload_1
    //   1422: arraylength
    //   1423: iconst_2
    //   1424: if_icmplt +69 -> 1493
    //   1427: aload_0
    //   1428: getfield 1762	com/android/server/policy/PhoneWindowManager:mSystemKeyManager	Lcom/android/server/policy/sec/SystemKeyManager;
    //   1431: iload_3
    //   1432: new 2611	android/content/ComponentName
    //   1435: dup
    //   1436: aload_1
    //   1437: iconst_0
    //   1438: aaload
    //   1439: aload_1
    //   1440: iconst_1
    //   1441: aaload
    //   1442: invokespecial 2617	android/content/ComponentName:<init>	(Ljava/lang/String;Ljava/lang/String;)V
    //   1445: invokevirtual 5088	com/android/server/policy/sec/SystemKeyManager:isSystemKeyEventRequested	(ILandroid/content/ComponentName;)Z
    //   1448: ifeq +45 -> 1493
    //   1451: ldc_w 299
    //   1454: new 1801	java/lang/StringBuilder
    //   1457: dup
    //   1458: invokespecial 1802	java/lang/StringBuilder:<init>	()V
    //   1461: ldc_w 5090
    //   1464: invokevirtual 1807	java/lang/StringBuilder:append	(Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   1467: iload_3
    //   1468: invokevirtual 2571	java/lang/StringBuilder:append	(I)Ljava/lang/StringBuilder;
    //   1471: ldc_w 5092
    //   1474: invokevirtual 1807	java/lang/StringBuilder:append	(Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   1477: aload_0
    //   1478: getfield 3447	com/android/server/policy/PhoneWindowManager:mFakeFocusedWindow	Landroid/view/WindowManagerPolicy$WindowState;
    //   1481: invokevirtual 2919	java/lang/StringBuilder:append	(Ljava/lang/Object;)Ljava/lang/StringBuilder;
    //   1484: invokevirtual 1815	java/lang/StringBuilder:toString	()Ljava/lang/String;
    //   1487: invokestatic 1936	android/util/Log:w	(Ljava/lang/String;Ljava/lang/String;)I
    //   1490: pop
    //   1491: lconst_0
    //   1492: lreturn
    //   1493: iload 10
    //   1495: ifne +24 -> 1519
    //   1498: iload 9
    //   1500: ifeq +23 -> 1523
    //   1503: iload 6
    //   1505: ifne +18 -> 1523
    //   1508: aload_0
    //   1509: invokespecial 5018	com/android/server/policy/PhoneWindowManager:preloadRecentApps	()V
    //   1512: aload_0
    //   1513: getfield 1358	com/android/server/policy/PhoneWindowManager:mMultiPhoneWindowManager	Lcom/android/server/policy/multiwindow/MultiPhoneWindowManager;
    //   1516: invokevirtual 5095	com/android/server/policy/multiwindow/MultiPhoneWindowManager:stopDragDropService	()V
    //   1519: ldc2_w 4962
    //   1522: lreturn
    //   1523: iload 9
    //   1525: ifne +86 -> 1611
    //   1528: aload_0
    //   1529: invokevirtual 5098	com/android/server/policy/PhoneWindowManager:isRecentConsumed	()Z
    //   1532: ifeq +11 -> 1543
    //   1535: aload_0
    //   1536: invokespecial 4976	com/android/server/policy/PhoneWindowManager:cancelPreloadRecentApps	()V
    //   1539: ldc2_w 4962
    //   1542: lreturn
    //   1543: invokestatic 5100	com/android/server/policy/PhoneWindowManager:awakenDreams	()V
    //   1546: aload_0
    //   1547: invokespecial 5102	com/android/server/policy/PhoneWindowManager:handleClickOnRecent	()V
    //   1550: aload_0
    //   1551: getfield 4999	com/android/server/policy/PhoneWindowManager:mTelephonyManager	Landroid/telephony/TelephonyManager;
    //   1554: ifnonnull +20 -> 1574
    //   1557: aload_0
    //   1558: aload_0
    //   1559: getfield 1619	com/android/server/policy/PhoneWindowManager:mContext	Landroid/content/Context;
    //   1562: ldc_w 5001
    //   1565: invokevirtual 1627	android/content/Context:getSystemService	(Ljava/lang/String;)Ljava/lang/Object;
    //   1568: checkcast 5003	android/telephony/TelephonyManager
    //   1571: putfield 4999	com/android/server/policy/PhoneWindowManager:mTelephonyManager	Landroid/telephony/TelephonyManager;
    //   1574: aload_0
    //   1575: getfield 4999	com/android/server/policy/PhoneWindowManager:mTelephonyManager	Landroid/telephony/TelephonyManager;
    //   1578: invokevirtual 5006	android/telephony/TelephonyManager:getCallState	()I
    //   1581: iconst_2
    //   1582: if_icmpne -63 -> 1519
    //   1585: aload_0
    //   1586: getfield 4999	com/android/server/policy/PhoneWindowManager:mTelephonyManager	Landroid/telephony/TelephonyManager;
    //   1589: invokevirtual 5009	android/telephony/TelephonyManager:isVideoCall	()Z
    //   1592: ifne -73 -> 1519
    //   1595: aload_0
    //   1596: getfield 1315	com/android/server/policy/PhoneWindowManager:mSPWM	Lcom/android/server/policy/sec/SamsungPhoneWindowManager;
    //   1599: ldc_w 5104
    //   1602: ldc_w 5106
    //   1605: invokevirtual 2269	com/android/server/policy/sec/SamsungPhoneWindowManager:insertLog	(Ljava/lang/String;Ljava/lang/String;)V
    //   1608: goto -89 -> 1519
    //   1611: aload_2
    //   1612: invokevirtual 2254	android/view/KeyEvent:getFlags	()I
    //   1615: sipush 128
    //   1618: iand
    //   1619: ifeq -100 -> 1519
    //   1622: aload_0
    //   1623: invokespecial 4976	com/android/server/policy/PhoneWindowManager:cancelPreloadRecentApps	()V
    //   1626: aload_0
    //   1627: invokespecial 5107	com/android/server/policy/PhoneWindowManager:handleLongPressOnRecent	()V
    //   1630: goto -111 -> 1519
    //   1633: iload_3
    //   1634: bipush 42
    //   1636: if_icmpne +37 -> 1673
    //   1639: aload_2
    //   1640: invokevirtual 5110	android/view/KeyEvent:isMetaPressed	()Z
    //   1643: ifeq +30 -> 1673
    //   1646: iload 9
    //   1648: ifeq -628 -> 1020
    //   1651: aload_0
    //   1652: invokevirtual 1574	com/android/server/policy/PhoneWindowManager:getStatusBarService	()Lcom/android/internal/statusbar/IStatusBarService;
    //   1655: astore_1
    //   1656: aload_1
    //   1657: ifnull -637 -> 1020
    //   1660: aload_1
    //   1661: invokeinterface 5113 1 0
    //   1666: goto -646 -> 1020
    //   1669: astore_1
    //   1670: goto -650 -> 1020
    //   1673: iload_3
    //   1674: sipush 219
    //   1677: if_icmpne +113 -> 1790
    //   1680: iload 9
    //   1682: ifeq +58 -> 1740
    //   1685: iload 6
    //   1687: ifne +12 -> 1699
    //   1690: aload_0
    //   1691: iconst_0
    //   1692: putfield 5115	com/android/server/policy/PhoneWindowManager:mAssistKeyLongPressed	Z
    //   1695: ldc2_w 4962
    //   1698: lreturn
    //   1699: iload 6
    //   1701: iconst_1
    //   1702: if_icmpne -7 -> 1695
    //   1705: aload_0
    //   1706: iconst_1
    //   1707: putfield 5115	com/android/server/policy/PhoneWindowManager:mAssistKeyLongPressed	Z
    //   1710: iload 10
    //   1712: ifne -17 -> 1695
    //   1715: aload_0
    //   1716: getfield 2111	com/android/server/policy/PhoneWindowManager:mLongPressOnHomeBehavior	I
    //   1719: iconst_3
    //   1720: if_icmpne +13 -> 1733
    //   1723: aload_0
    //   1724: getfield 1315	com/android/server/policy/PhoneWindowManager:mSPWM	Lcom/android/server/policy/sec/SamsungPhoneWindowManager;
    //   1727: invokevirtual 2135	com/android/server/policy/sec/SamsungPhoneWindowManager:launchSReminder	()V
    //   1730: goto -35 -> 1695
    //   1733: aload_0
    //   1734: invokespecial 5117	com/android/server/policy/PhoneWindowManager:launchAssistLongPressAction	()V
    //   1737: goto -42 -> 1695
    //   1740: aload_0
    //   1741: getfield 5115	com/android/server/policy/PhoneWindowManager:mAssistKeyLongPressed	Z
    //   1744: ifeq +11 -> 1755
    //   1747: aload_0
    //   1748: iconst_0
    //   1749: putfield 5115	com/android/server/policy/PhoneWindowManager:mAssistKeyLongPressed	Z
    //   1752: goto -57 -> 1695
    //   1755: iload 10
    //   1757: ifne -62 -> 1695
    //   1760: aload_0
    //   1761: getfield 2111	com/android/server/policy/PhoneWindowManager:mLongPressOnHomeBehavior	I
    //   1764: iconst_3
    //   1765: if_icmpne +13 -> 1778
    //   1768: aload_0
    //   1769: getfield 1315	com/android/server/policy/PhoneWindowManager:mSPWM	Lcom/android/server/policy/sec/SamsungPhoneWindowManager;
    //   1772: invokevirtual 2135	com/android/server/policy/sec/SamsungPhoneWindowManager:launchSReminder	()V
    //   1775: goto -80 -> 1695
    //   1778: aload_0
    //   1779: aconst_null
    //   1780: aload_2
    //   1781: invokevirtual 3873	android/view/KeyEvent:getDeviceId	()I
    //   1784: invokespecial 2118	com/android/server/policy/PhoneWindowManager:launchAssistAction	(Ljava/lang/String;I)V
    //   1787: goto -92 -> 1695
    //   1790: iload_3
    //   1791: sipush 231
    //   1794: if_icmpne +85 -> 1879
    //   1797: iload 9
    //   1799: ifne -779 -> 1020
    //   1802: iload 10
    //   1804: ifne +29 -> 1833
    //   1807: new 2062	android/content/Intent
    //   1810: dup
    //   1811: ldc_w 5119
    //   1814: invokespecial 2484	android/content/Intent:<init>	(Ljava/lang/String;)V
    //   1817: astore_1
    //   1818: aload_0
    //   1819: aload_1
    //   1820: getstatic 5122	android/os/UserHandle:CURRENT_OR_SELF	Landroid/os/UserHandle;
    //   1823: invokespecial 2502	com/android/server/policy/PhoneWindowManager:startActivityAsUser	(Landroid/content/Intent;Landroid/os/UserHandle;)V
    //   1826: goto -806 -> 1020
    //   1829: astore_1
    //   1830: goto -810 -> 1020
    //   1833: ldc_w 5124
    //   1836: invokestatic 1943	android/os/ServiceManager:getService	(Ljava/lang/String;)Landroid/os/IBinder;
    //   1839: invokestatic 5129	android/os/IDeviceIdleController$Stub:asInterface	(Landroid/os/IBinder;)Landroid/os/IDeviceIdleController;
    //   1842: astore_1
    //   1843: aload_1
    //   1844: ifnull +12 -> 1856
    //   1847: aload_1
    //   1848: ldc_w 5131
    //   1851: invokeinterface 5136 2 0
    //   1856: new 2062	android/content/Intent
    //   1859: dup
    //   1860: ldc_w 5138
    //   1863: invokespecial 2484	android/content/Intent:<init>	(Ljava/lang/String;)V
    //   1866: astore_1
    //   1867: aload_1
    //   1868: ldc_w 5140
    //   1871: iconst_1
    //   1872: invokevirtual 2524	android/content/Intent:putExtra	(Ljava/lang/String;Z)Landroid/content/Intent;
    //   1875: pop
    //   1876: goto -58 -> 1818
    //   1879: iload_3
    //   1880: bipush 120
    //   1882: if_icmpne +29 -> 1911
    //   1885: iload 9
    //   1887: ifeq +20 -> 1907
    //   1890: iload 6
    //   1892: ifne +15 -> 1907
    //   1895: aload_0
    //   1896: getfield 1556	com/android/server/policy/PhoneWindowManager:mHandler	Landroid/os/Handler;
    //   1899: aload_0
    //   1900: getfield 1186	com/android/server/policy/PhoneWindowManager:mScreenshotRunnable	Ljava/lang/Runnable;
    //   1903: invokevirtual 2249	android/os/Handler:post	(Ljava/lang/Runnable;)Z
    //   1906: pop
    //   1907: ldc2_w 4962
    //   1910: lreturn
    //   1911: iload_3
    //   1912: sipush 221
    //   1915: if_icmpeq +10 -> 1925
    //   1918: iload_3
    //   1919: sipush 220
    //   1922: if_icmpne +196 -> 2118
    //   1925: aload_0
    //   1926: getfield 1149	com/android/server/policy/PhoneWindowManager:bIsCharging	Z
    //   1929: ifne +25 -> 1954
    //   1932: aload_0
    //   1933: getfield 1147	com/android/server/policy/PhoneWindowManager:mBatteryLevel	I
    //   1936: iconst_5
    //   1937: if_icmpgt +17 -> 1954
    //   1940: ldc_w 299
    //   1943: ldc_w 5142
    //   1946: invokestatic 1459	android/util/Slog:d	(Ljava/lang/String;Ljava/lang/String;)I
    //   1949: pop
    //   1950: ldc2_w 4962
    //   1953: lreturn
    //   1954: iload 9
    //   1956: ifeq +153 -> 2109
    //   1959: iload_3
    //   1960: sipush 221
    //   1963: if_icmpne +150 -> 2113
    //   1966: iconst_1
    //   1967: istore_3
    //   1968: aload_0
    //   1969: getfield 1619	com/android/server/policy/PhoneWindowManager:mContext	Landroid/content/Context;
    //   1972: invokevirtual 2050	android/content/Context:getContentResolver	()Landroid/content/ContentResolver;
    //   1975: ldc_w 5144
    //   1978: iconst_0
    //   1979: bipush 253
    //   1981: invokestatic 2389	android/provider/Settings$System:getIntForUser	(Landroid/content/ContentResolver;Ljava/lang/String;II)I
    //   1984: ifeq +20 -> 2004
    //   1987: aload_0
    //   1988: getfield 1619	com/android/server/policy/PhoneWindowManager:mContext	Landroid/content/Context;
    //   1991: invokevirtual 2050	android/content/Context:getContentResolver	()Landroid/content/ContentResolver;
    //   1994: ldc_w 5144
    //   1997: iconst_0
    //   1998: bipush 253
    //   2000: invokestatic 4622	android/provider/Settings$System:putIntForUser	(Landroid/content/ContentResolver;Ljava/lang/String;II)Z
    //   2003: pop
    //   2004: aload_0
    //   2005: getfield 1438	com/android/server/policy/PhoneWindowManager:mPowerManager	Landroid/os/PowerManager;
    //   2008: invokevirtual 5147	android/os/PowerManager:getMinimumScreenBrightnessSetting	()I
    //   2011: istore 4
    //   2013: aload_0
    //   2014: getfield 1438	com/android/server/policy/PhoneWindowManager:mPowerManager	Landroid/os/PowerManager;
    //   2017: invokevirtual 5150	android/os/PowerManager:getMaximumScreenBrightnessSetting	()I
    //   2020: istore 5
    //   2022: iload 5
    //   2024: iload 4
    //   2026: isub
    //   2027: bipush 10
    //   2029: iadd
    //   2030: iconst_1
    //   2031: isub
    //   2032: bipush 10
    //   2034: idiv
    //   2035: istore 6
    //   2037: iload 4
    //   2039: iload 5
    //   2041: aload_0
    //   2042: getfield 1619	com/android/server/policy/PhoneWindowManager:mContext	Landroid/content/Context;
    //   2045: invokevirtual 2050	android/content/Context:getContentResolver	()Landroid/content/ContentResolver;
    //   2048: ldc_w 5152
    //   2051: aload_0
    //   2052: getfield 1438	com/android/server/policy/PhoneWindowManager:mPowerManager	Landroid/os/PowerManager;
    //   2055: invokevirtual 5155	android/os/PowerManager:getDefaultScreenBrightnessSetting	()I
    //   2058: bipush 253
    //   2060: invokestatic 2389	android/provider/Settings$System:getIntForUser	(Landroid/content/ContentResolver;Ljava/lang/String;II)I
    //   2063: iload 6
    //   2065: iload_3
    //   2066: imul
    //   2067: iadd
    //   2068: invokestatic 5158	java/lang/Math:min	(II)I
    //   2071: invokestatic 2549	java/lang/Math:max	(II)I
    //   2074: istore_3
    //   2075: aload_0
    //   2076: getfield 1619	com/android/server/policy/PhoneWindowManager:mContext	Landroid/content/Context;
    //   2079: invokevirtual 2050	android/content/Context:getContentResolver	()Landroid/content/ContentResolver;
    //   2082: ldc_w 5152
    //   2085: iload_3
    //   2086: bipush 253
    //   2088: invokestatic 4622	android/provider/Settings$System:putIntForUser	(Landroid/content/ContentResolver;Ljava/lang/String;II)Z
    //   2091: pop
    //   2092: aload_0
    //   2093: new 2062	android/content/Intent
    //   2096: dup
    //   2097: ldc_w 5160
    //   2100: invokespecial 2484	android/content/Intent:<init>	(Ljava/lang/String;)V
    //   2103: getstatic 5122	android/os/UserHandle:CURRENT_OR_SELF	Landroid/os/UserHandle;
    //   2106: invokespecial 2502	com/android/server/policy/PhoneWindowManager:startActivityAsUser	(Landroid/content/Intent;Landroid/os/UserHandle;)V
    //   2109: ldc2_w 4962
    //   2112: lreturn
    //   2113: iconst_m1
    //   2114: istore_3
    //   2115: goto -147 -> 1968
    //   2118: iload_3
    //   2119: invokestatic 4970	android/view/KeyEvent:isMetaKey	(I)Z
    //   2122: ifeq +48 -> 2170
    //   2125: aload_0
    //   2126: getfield 1762	com/android/server/policy/PhoneWindowManager:mSystemKeyManager	Lcom/android/server/policy/sec/SystemKeyManager;
    //   2129: invokevirtual 5163	com/android/server/policy/sec/SystemKeyManager:isMetaKeyEventRequested	()Z
    //   2132: ifne +38 -> 2170
    //   2135: iload 9
    //   2137: ifeq +12 -> 2149
    //   2140: aload_0
    //   2141: iconst_1
    //   2142: putfield 4967	com/android/server/policy/PhoneWindowManager:mPendingMetaAction	Z
    //   2145: ldc2_w 4962
    //   2148: lreturn
    //   2149: aload_0
    //   2150: getfield 4967	com/android/server/policy/PhoneWindowManager:mPendingMetaAction	Z
    //   2153: ifeq -8 -> 2145
    //   2156: aload_0
    //   2157: ldc_w 5165
    //   2160: aload_2
    //   2161: invokevirtual 3873	android/view/KeyEvent:getDeviceId	()I
    //   2164: invokespecial 2118	com/android/server/policy/PhoneWindowManager:launchAssistAction	(Ljava/lang/String;I)V
    //   2167: goto -22 -> 2145
    //   2170: iload_3
    //   2171: bipush 6
    //   2173: if_icmpne +7 -> 2180
    //   2176: ldc2_w 4962
    //   2179: lreturn
    //   2180: iload_3
    //   2181: bipush 17
    //   2183: if_icmpne -1163 -> 1020
    //   2186: ldc_w 5167
    //   2189: ldc_w 1061
    //   2192: invokevirtual 1784	java/lang/String:equals	(Ljava/lang/Object;)Z
    //   2195: ifeq -1175 -> 1020
    //   2198: iload 9
    //   2200: ifeq +51 -> 2251
    //   2203: iload 6
    //   2205: iconst_3
    //   2206: if_icmpne +29 -> 2235
    //   2209: iload 11
    //   2211: ifne +24 -> 2235
    //   2214: iload 10
    //   2216: ifne -1196 -> 1020
    //   2219: aload_0
    //   2220: iconst_1
    //   2221: putfield 1081	com/android/server/policy/PhoneWindowManager:mStarKeyLongPressConsumed	Z
    //   2224: aload_0
    //   2225: getfield 1315	com/android/server/policy/PhoneWindowManager:mSPWM	Lcom/android/server/policy/sec/SamsungPhoneWindowManager;
    //   2228: invokevirtual 5170	com/android/server/policy/sec/SamsungPhoneWindowManager:handleLongPressOnStar	()V
    //   2231: ldc2_w 4962
    //   2234: lreturn
    //   2235: aload_0
    //   2236: getfield 1081	com/android/server/policy/PhoneWindowManager:mStarKeyLongPressConsumed	Z
    //   2239: ifeq -1219 -> 1020
    //   2242: iload 6
    //   2244: ifle -1224 -> 1020
    //   2247: ldc2_w 4962
    //   2250: lreturn
    //   2251: aload_0
    //   2252: getfield 1081	com/android/server/policy/PhoneWindowManager:mStarKeyLongPressConsumed	Z
    //   2255: ifeq -1235 -> 1020
    //   2258: aload_0
    //   2259: iconst_0
    //   2260: putfield 1081	com/android/server/policy/PhoneWindowManager:mStarKeyLongPressConsumed	Z
    //   2263: ldc2_w 4962
    //   2266: lreturn
    //   2267: astore_1
    //   2268: ldc_w 299
    //   2271: new 1801	java/lang/StringBuilder
    //   2274: dup
    //   2275: invokespecial 1802	java/lang/StringBuilder:<init>	()V
    //   2278: ldc_w 5172
    //   2281: invokevirtual 1807	java/lang/StringBuilder:append	(Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   2284: iload_3
    //   2285: invokestatic 5175	android/view/KeyEvent:keyCodeToString	(I)Ljava/lang/String;
    //   2288: invokevirtual 1807	java/lang/StringBuilder:append	(Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   2291: invokevirtual 1815	java/lang/StringBuilder:toString	()Ljava/lang/String;
    //   2294: aload_1
    //   2295: invokestatic 2505	android/util/Slog:w	(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I
    //   2298: pop
    //   2299: goto -1202 -> 1097
    //   2302: ldc_w 299
    //   2305: new 1801	java/lang/StringBuilder
    //   2308: dup
    //   2309: invokespecial 1802	java/lang/StringBuilder:<init>	()V
    //   2312: ldc_w 5177
    //   2315: invokevirtual 1807	java/lang/StringBuilder:append	(Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   2318: iload_3
    //   2319: invokestatic 5175	android/view/KeyEvent:keyCodeToString	(I)Ljava/lang/String;
    //   2322: invokevirtual 1807	java/lang/StringBuilder:append	(Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   2325: invokevirtual 1815	java/lang/StringBuilder:toString	()Ljava/lang/String;
    //   2328: invokestatic 2647	android/util/Slog:i	(Ljava/lang/String;Ljava/lang/String;)I
    //   2331: pop
    //   2332: goto -1235 -> 1097
    //   2335: iload 9
    //   2337: ifeq +120 -> 2457
    //   2340: iload 6
    //   2342: ifne +115 -> 2457
    //   2345: iload 10
    //   2347: ifne +110 -> 2457
    //   2350: ldc_w 3445
    //   2353: iload 4
    //   2355: iand
    //   2356: ifeq +101 -> 2457
    //   2359: aload_0
    //   2360: getfield 1762	com/android/server/policy/PhoneWindowManager:mSystemKeyManager	Lcom/android/server/policy/sec/SystemKeyManager;
    //   2363: invokevirtual 5163	com/android/server/policy/sec/SystemKeyManager:isMetaKeyEventRequested	()Z
    //   2366: ifne +91 -> 2457
    //   2369: aload_2
    //   2370: invokevirtual 3846	android/view/KeyEvent:getKeyCharacterMap	()Landroid/view/KeyCharacterMap;
    //   2373: astore_1
    //   2374: aload_1
    //   2375: iload_3
    //   2376: invokevirtual 5045	android/view/KeyCharacterMap:isPrintingKey	(I)Z
    //   2379: ifeq +78 -> 2457
    //   2382: aload_0
    //   2383: getfield 4635	com/android/server/policy/PhoneWindowManager:mShortcutManager	Lcom/android/server/policy/ShortcutManager;
    //   2386: aload_1
    //   2387: iload_3
    //   2388: ldc_w 5178
    //   2391: iload 4
    //   2393: iand
    //   2394: invokevirtual 5051	com/android/server/policy/ShortcutManager:getIntent	(Landroid/view/KeyCharacterMap;II)Landroid/content/Intent;
    //   2397: astore_1
    //   2398: aload_1
    //   2399: ifnull +58 -> 2457
    //   2402: aload_1
    //   2403: ldc_w 2485
    //   2406: invokevirtual 4650	android/content/Intent:addFlags	(I)Landroid/content/Intent;
    //   2409: pop
    //   2410: aload_0
    //   2411: aload_1
    //   2412: getstatic 2498	android/os/UserHandle:CURRENT	Landroid/os/UserHandle;
    //   2415: invokespecial 2502	com/android/server/policy/PhoneWindowManager:startActivityAsUser	(Landroid/content/Intent;Landroid/os/UserHandle;)V
    //   2418: ldc2_w 4962
    //   2421: lreturn
    //   2422: astore_1
    //   2423: ldc_w 299
    //   2426: new 1801	java/lang/StringBuilder
    //   2429: dup
    //   2430: invokespecial 1802	java/lang/StringBuilder:<init>	()V
    //   2433: ldc_w 5180
    //   2436: invokevirtual 1807	java/lang/StringBuilder:append	(Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   2439: iload_3
    //   2440: invokestatic 5175	android/view/KeyEvent:keyCodeToString	(I)Ljava/lang/String;
    //   2443: invokevirtual 1807	java/lang/StringBuilder:append	(Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   2446: invokevirtual 1815	java/lang/StringBuilder:toString	()Ljava/lang/String;
    //   2449: aload_1
    //   2450: invokestatic 2505	android/util/Slog:w	(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I
    //   2453: pop
    //   2454: goto -36 -> 2418
    //   2457: iload 9
    //   2459: ifeq +130 -> 2589
    //   2462: iload 6
    //   2464: ifne +125 -> 2589
    //   2467: iload 10
    //   2469: ifne +120 -> 2589
    //   2472: invokestatic 5183	android/os/FactoryTest:isFactoryBinary	()Z
    //   2475: ifne +114 -> 2589
    //   2478: invokestatic 1451	android/os/FactoryTest:isRunningFactoryApp	()Z
    //   2481: ifne +108 -> 2589
    //   2484: invokestatic 5186	com/android/server/policy/sec/SamsungPolicyProperties:isDomesticOtaStart	()Z
    //   2487: ifne +102 -> 2589
    //   2490: aload_0
    //   2491: getfield 1619	com/android/server/policy/PhoneWindowManager:mContext	Landroid/content/Context;
    //   2494: invokestatic 1790	com/android/server/policy/sec/SamsungPolicyProperties:isBlockKey	(Landroid/content/Context;)Z
    //   2497: ifne +92 -> 2589
    //   2500: getstatic 866	com/android/server/policy/PhoneWindowManager:sApplicationLaunchKeyCategories	Landroid/util/SparseArray;
    //   2503: iload_3
    //   2504: invokevirtual 3891	android/util/SparseArray:get	(I)Ljava/lang/Object;
    //   2507: checkcast 988	java/lang/String
    //   2510: astore_1
    //   2511: aload_1
    //   2512: ifnull +77 -> 2589
    //   2515: aload_0
    //   2516: invokevirtual 4553	com/android/server/policy/PhoneWindowManager:sendCloseSystemWindows	()V
    //   2519: ldc_w 4638
    //   2522: aload_1
    //   2523: invokestatic 5189	android/content/Intent:makeMainSelectorActivity	(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;
    //   2526: astore_2
    //   2527: aload_2
    //   2528: ldc_w 2485
    //   2531: invokevirtual 2489	android/content/Intent:setFlags	(I)Landroid/content/Intent;
    //   2534: pop
    //   2535: aload_0
    //   2536: aload_2
    //   2537: getstatic 2498	android/os/UserHandle:CURRENT	Landroid/os/UserHandle;
    //   2540: invokespecial 2502	com/android/server/policy/PhoneWindowManager:startActivityAsUser	(Landroid/content/Intent;Landroid/os/UserHandle;)V
    //   2543: ldc2_w 4962
    //   2546: lreturn
    //   2547: astore_2
    //   2548: ldc_w 299
    //   2551: new 1801	java/lang/StringBuilder
    //   2554: dup
    //   2555: invokespecial 1802	java/lang/StringBuilder:<init>	()V
    //   2558: ldc_w 5191
    //   2561: invokevirtual 1807	java/lang/StringBuilder:append	(Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   2564: iload_3
    //   2565: invokevirtual 2571	java/lang/StringBuilder:append	(I)Ljava/lang/StringBuilder;
    //   2568: ldc_w 5193
    //   2571: invokevirtual 1807	java/lang/StringBuilder:append	(Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   2574: aload_1
    //   2575: invokevirtual 1807	java/lang/StringBuilder:append	(Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   2578: invokevirtual 1815	java/lang/StringBuilder:toString	()Ljava/lang/String;
    //   2581: aload_2
    //   2582: invokestatic 2505	android/util/Slog:w	(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I
    //   2585: pop
    //   2586: goto -43 -> 2543
    //   2589: iload 9
    //   2591: ifeq +77 -> 2668
    //   2594: iload 6
    //   2596: ifne +72 -> 2668
    //   2599: iload_3
    //   2600: bipush 61
    //   2602: if_icmpne +66 -> 2668
    //   2605: aload_0
    //   2606: getfield 5195	com/android/server/policy/PhoneWindowManager:mRecentAppsHeldModifiers	I
    //   2609: ifne +92 -> 2701
    //   2612: iload 10
    //   2614: ifne +87 -> 2701
    //   2617: aload_0
    //   2618: invokevirtual 2452	com/android/server/policy/PhoneWindowManager:isUserSetupComplete	()Z
    //   2621: ifeq +80 -> 2701
    //   2624: aload_0
    //   2625: getfield 1762	com/android/server/policy/PhoneWindowManager:mSystemKeyManager	Lcom/android/server/policy/sec/SystemKeyManager;
    //   2628: invokevirtual 5163	com/android/server/policy/sec/SystemKeyManager:isMetaKeyEventRequested	()Z
    //   2631: ifne +70 -> 2701
    //   2634: aload_2
    //   2635: invokevirtual 5198	android/view/KeyEvent:getModifiers	()I
    //   2638: sipush -194
    //   2641: iand
    //   2642: istore 5
    //   2644: iload 5
    //   2646: iconst_2
    //   2647: invokestatic 5201	android/view/KeyEvent:metaStateHasModifiers	(II)Z
    //   2650: ifeq +51 -> 2701
    //   2653: aload_0
    //   2654: iload 5
    //   2656: putfield 5195	com/android/server/policy/PhoneWindowManager:mRecentAppsHeldModifiers	I
    //   2659: aload_0
    //   2660: iconst_1
    //   2661: invokespecial 1362	com/android/server/policy/PhoneWindowManager:showRecentApps	(Z)V
    //   2664: ldc2_w 4962
    //   2667: lreturn
    //   2668: iload 9
    //   2670: ifne +31 -> 2701
    //   2673: aload_0
    //   2674: getfield 5195	com/android/server/policy/PhoneWindowManager:mRecentAppsHeldModifiers	I
    //   2677: ifeq +24 -> 2701
    //   2680: aload_0
    //   2681: getfield 5195	com/android/server/policy/PhoneWindowManager:mRecentAppsHeldModifiers	I
    //   2684: iload 4
    //   2686: iand
    //   2687: ifne +14 -> 2701
    //   2690: aload_0
    //   2691: iconst_0
    //   2692: putfield 5195	com/android/server/policy/PhoneWindowManager:mRecentAppsHeldModifiers	I
    //   2695: aload_0
    //   2696: iconst_1
    //   2697: iconst_0
    //   2698: invokespecial 5202	com/android/server/policy/PhoneWindowManager:hideRecentApps	(ZZ)V
    //   2701: iload 9
    //   2703: ifeq +64 -> 2767
    //   2706: iload 6
    //   2708: ifne +59 -> 2767
    //   2711: iload_3
    //   2712: sipush 204
    //   2715: if_icmpeq +18 -> 2733
    //   2718: iload_3
    //   2719: bipush 62
    //   2721: if_icmpne +46 -> 2767
    //   2724: iload 4
    //   2726: sipush 28672
    //   2729: iand
    //   2730: ifeq +37 -> 2767
    //   2733: iload 4
    //   2735: sipush 193
    //   2738: iand
    //   2739: ifeq +23 -> 2762
    //   2742: iconst_m1
    //   2743: istore_3
    //   2744: aload_0
    //   2745: getfield 1606	com/android/server/policy/PhoneWindowManager:mWindowManagerFuncs	Landroid/view/WindowManagerPolicy$WindowManagerFuncs;
    //   2748: aload_2
    //   2749: invokevirtual 3873	android/view/KeyEvent:getDeviceId	()I
    //   2752: iload_3
    //   2753: invokeinterface 5205 3 0
    //   2758: ldc2_w 4962
    //   2761: lreturn
    //   2762: iconst_1
    //   2763: istore_3
    //   2764: goto -20 -> 2744
    //   2767: aload_0
    //   2768: getfield 5207	com/android/server/policy/PhoneWindowManager:mLanguageSwitchKeyPressed	Z
    //   2771: ifeq +30 -> 2801
    //   2774: iload 9
    //   2776: ifne +25 -> 2801
    //   2779: iload_3
    //   2780: sipush 204
    //   2783: if_icmpeq +9 -> 2792
    //   2786: iload_3
    //   2787: bipush 62
    //   2789: if_icmpne +12 -> 2801
    //   2792: aload_0
    //   2793: iconst_0
    //   2794: putfield 5207	com/android/server/policy/PhoneWindowManager:mLanguageSwitchKeyPressed	Z
    //   2797: ldc2_w 4962
    //   2800: lreturn
    //   2801: iload_3
    //   2802: invokestatic 5209	com/android/server/policy/PhoneWindowManager:isValidGlobalKey	(I)Z
    //   2805: ifeq +23 -> 2828
    //   2808: aload_0
    //   2809: getfield 4150	com/android/server/policy/PhoneWindowManager:mGlobalKeyManager	Lcom/android/server/policy/GlobalKeyManager;
    //   2812: aload_0
    //   2813: getfield 1619	com/android/server/policy/PhoneWindowManager:mContext	Landroid/content/Context;
    //   2816: iload_3
    //   2817: aload_2
    //   2818: invokevirtual 5213	com/android/server/policy/GlobalKeyManager:handleGlobalKey	(Landroid/content/Context;ILandroid/view/KeyEvent;)Z
    //   2821: ifeq +7 -> 2828
    //   2824: ldc2_w 4962
    //   2827: lreturn
    //   2828: ldc_w 3445
    //   2831: iload 4
    //   2833: iand
    //   2834: ifeq +17 -> 2851
    //   2837: aload_0
    //   2838: getfield 1762	com/android/server/policy/PhoneWindowManager:mSystemKeyManager	Lcom/android/server/policy/sec/SystemKeyManager;
    //   2841: invokevirtual 5163	com/android/server/policy/sec/SystemKeyManager:isMetaKeyEventRequested	()Z
    //   2844: ifne +7 -> 2851
    //   2847: ldc2_w 4962
    //   2850: lreturn
    //   2851: lconst_0
    //   2852: lreturn
    //   2853: astore_1
    //   2854: goto -745 -> 2109
    //   2857: astore_1
    //   2858: goto -1002 -> 1856
    //
    // Exception table:
    //   from	to	target	type
    //   1660	1666	1669	android/os/RemoteException
    //   1818	1826	1829	android/content/ActivityNotFoundException
    //   1089	1097	2267	android/content/ActivityNotFoundException
    //   2410	2418	2422	android/content/ActivityNotFoundException
    //   2535	2543	2547	android/content/ActivityNotFoundException
    //   2092	2109	2853	android/content/ActivityNotFoundException
    //   1847	1856	2857	android/os/RemoteException
  }

  public int interceptKeyBeforeQueueing(KeyEvent paramKeyEvent, int paramInt)
  {
    if (!this.mSystemBooted)
      paramInt = 0;
    boolean bool2;
    boolean bool4;
    label32: boolean bool6;
    int n;
    int i;
    label54: boolean bool3;
    label64: int j;
    label149: int m;
    label246: label252: int k;
    while (true)
    {
      return paramInt;
      if ((0x20000000 & paramInt) != 0)
      {
        bool2 = true;
        if (paramKeyEvent.getAction() != 0)
          break label246;
        bool4 = true;
        bool6 = paramKeyEvent.isCanceled();
        n = paramKeyEvent.getKeyCode();
        if ((0x1000000 & paramInt) == 0)
          break label252;
        i = 1;
        if (this.mKeyguardDelegate != null)
          break label257;
        bool3 = false;
        if (DEBUG_INPUT)
          Log.d("WindowManager", "interceptKeyTq keycode=" + n + " interactive=" + bool2 + " keyguardActive=" + bool3 + " policyFlags=" + Integer.toHexString(paramInt));
        if (((paramInt & 0x1) == 0) && (!paramKeyEvent.isWakeKey()))
          break label283;
        j = 1;
        if ((!bool2) && ((i == 0) || (j != 0)))
          break label289;
        i = 1;
        m = 0;
      }
      while (true)
      {
        j = i;
        if (n == 3)
        {
          j = i;
          if (checkTriggerOWC(bool4))
            j = 1;
        }
        if (!this.mSPWM.interceptKeyBeforeQueueing(paramKeyEvent, paramInt))
          break label355;
        if (m != 0)
        {
          this.mSPWM.performCPUBoost();
          this.mPowerManager.wakeUp(paramKeyEvent.getEventTime(), 1);
        }
        Log.d("WindowManager", "interceptKeyTq : return condition of SPWM");
        return 0;
        bool2 = false;
        break;
        bool4 = false;
        break label32;
        i = 0;
        break label54;
        label257: if (bool2)
        {
          bool3 = isKeyguardShowingAndNotOccluded();
          break label64;
        }
        bool3 = this.mKeyguardDelegate.isShowing();
        break label64;
        label283: j = 0;
        break label149;
        label289: if ((!bool2) && (shouldDispatchInputWhenNonInteractive()))
        {
          i = 1;
          m = j;
          continue;
        }
        k = 0;
        m = j;
        i = k;
        if (j == 0)
          continue;
        if (bool4)
        {
          m = j;
          i = k;
          if (isWakeKeyWhenScreenOff(n))
            continue;
        }
        m = 0;
        i = k;
      }
      label355: if ((!isValidGlobalKey(n)) || (!this.mGlobalKeyManager.shouldHandleGlobalKey(n, paramKeyEvent)))
        break;
      paramInt = j;
      if (m == 0)
        continue;
      wakeUp(paramKeyEvent.getEventTime(), this.mAllowTheaterModeWakeFromKey, 1);
      return j;
    }
    boolean bool1;
    label417: boolean bool5;
    if ((bool4) && (!this.mHasNavigationBar))
    {
      bool1 = bool2;
      switch (n)
      {
      default:
        bool5 = bool1;
        i = j;
        k = m;
        label631: i &= this.mCocktailPhoneWindowManager.interceptKeyBeforeQueueing(paramKeyEvent, i, paramInt);
        if (!bool5)
          break;
        if (!this.mHasNavigationBar)
          this.mSPWM.performSystemKeyFeedback(paramKeyEvent);
      case 24:
      case 25:
      case 164:
      case 6:
      case 26:
      case 223:
      case 224:
      case 79:
      case 85:
      case 126:
      case 127:
      case 86:
      case 87:
      case 88:
      case 89:
      case 90:
      case 91:
      case 130:
      case 222:
      case 5:
      case 231:
      case 3:
      case 1015:
      case 1049:
      }
    }
    while (true)
    {
      paramInt = i;
      if (k == 0)
        break;
      wakeUp(paramKeyEvent.getEventTime(), this.mAllowTheaterModeWakeFromKey, 1);
      return i;
      if ((this.mVibrator.isEnableIntensity()) && ((paramInt & 0x2) != 0) && (paramKeyEvent.getRepeatCount() == 0))
      {
        bool1 = true;
        break label417;
      }
      bool1 = false;
      break label417;
      k = j;
      if (this.mUseTvRouting)
        k = j & 0xFFFFFFFE;
      i = k;
      if (this.mSPWM.isVolumeKeyAppsEnabled())
        i = k & 0xFFFFFFFE;
      if ((!this.mScreenOnFully) && (this.mVolBtnMusicControls != 0))
      {
        if (bool4)
        {
          handleVolumeLongPress(n);
          k = m;
          bool5 = bool1;
          break label631;
        }
        handleVolumeLongPressAbort();
        if (!this.mIsVolLongPressed)
          handleVolumeKey(3, n);
        this.mIsVolLongPressed = false;
      }
      Object localObject = CocktailBarManager.getInstance(this.mContext);
      j = i;
      if (localObject != null)
      {
        j = i;
        if (((CocktailBarManager)localObject).getCocktaiBarWakeUpState())
          j = i & 0xFFFFFFFE;
      }
      localObject = getTelecommService();
      if (localObject != null)
      {
        if ((bool4) && (((TelecomManager)localObject).isRinging()))
        {
          Log.i("WindowManager", "interceptKeyBeforeQueueing: VOLUME key-down while ringing: Silence ringer!");
          ((TelecomManager)localObject).silenceRinger();
          i = j & 0xFFFFFFFE;
          this.mBeforeKeyDown = n;
          this.mSPWM.insertLog("VCVS", null);
          k = m;
          bool5 = bool1;
          break label631;
        }
        if ((!bool4) && (n == this.mBeforeKeyDown))
        {
          i = j & 0xFFFFFFFE;
          this.mBeforeKeyDown = 0;
          k = m;
          bool5 = bool1;
          break label631;
        }
        if ((((TelecomManager)localObject).isInCall()) && ((j & 0x1) == 0))
        {
          MediaSessionLegacyHelper.getHelper(this.mContext).sendVolumeKeyEvent(paramKeyEvent, false);
          k = m;
          i = j;
          bool5 = bool1;
          break label631;
        }
      }
      k = m;
      i = j;
      bool5 = bool1;
      if ((j & 0x1) != 0)
        break label631;
      if (this.mUseTvRouting)
      {
        dispatchDirectAudioEvent(paramKeyEvent);
        k = m;
        i = j;
        bool5 = bool1;
        break label631;
      }
      MediaSessionLegacyHelper.getHelper(this.mContext).sendVolumeKeyEvent(paramKeyEvent, true);
      k = m;
      i = j;
      bool5 = bool1;
      break label631;
      n = 0;
      if (this.mSPWM.isCombinationKeyTriggered())
        n = 1;
      k = m;
      i = j;
      bool5 = bool1;
      if (n != 0)
        break label631;
      if (this.mEndCallKeyPressCounter != 0)
        this.mHandler.removeMessages(62);
      if (bool4)
      {
        interceptEndCallKeyDown(paramKeyEvent, bool2);
        k = m;
        i = j;
        bool5 = bool1;
        break label631;
      }
      interceptEndCallKeyUp(paramKeyEvent, bool2, bool6);
      k = m;
      i = j;
      bool5 = bool1;
      if (this.mEndCallKeyHandled)
        break label631;
      k = m;
      i = j;
      bool5 = bool1;
      if (bool6)
        break label631;
      k = m;
      i = j;
      bool5 = bool1;
      if ((this.mEndcallBehavior & 0x2) == 0)
        break label631;
      this.mPowerManager.goToSleep(paramKeyEvent.getEventTime(), 4, 0);
      k = 0;
      i = j;
      bool5 = bool1;
      break label631;
      if (this.mSystemKeyManager.isSystemKeyEventRequested(n))
      {
        k = m;
        i = j;
        bool5 = bool1;
        if (this.mSPWM.isBlockedPowerKeyByKeyTest(this.mSystemKeyManager.getCurrentTopActivity()))
          break label631;
        this.mPendingPowerKeyUpCanceled = true;
      }
      while (true)
      {
        i = j;
        if (this.mSPWM.isSideSyncPresentationRunning())
        {
          i = j;
          if (paramKeyEvent.getDisplayId() == 0)
            i = j | 0x1;
        }
        k = 0;
        if (!bool4)
          break label1345;
        interceptPowerKeyDown(paramKeyEvent, bool2);
        this.mPendingPowerKeyUpCanceled = false;
        bool5 = bool1;
        break;
        j &= -2;
      }
      label1345: if ((bool6) || (this.mPendingPowerKeyUpCanceled));
      for (bool3 = true; ; bool3 = false)
      {
        interceptPowerKeyUp(paramKeyEvent, bool2, bool3);
        break;
      }
      i = j & 0xFFFFFFFE;
      k = 0;
      if (!this.mPowerManager.isInteractive())
        bool1 = false;
      if (bool4)
      {
        sleepPress(paramKeyEvent.getEventTime());
        bool5 = bool1;
        break label631;
      }
      sleepRelease(paramKeyEvent.getEventTime());
      bool5 = bool1;
      break label631;
      i = j & 0xFFFFFFFE;
      k = 1;
      bool5 = bool1;
      break label631;
      if ((n == 79) && (FactoryTest.isFactoryPBAPhase()))
      {
        Log.i("WindowManager", " KeyEvent.KEYCODE_HEADSETHOOK blocked...");
        k = m;
        i = j;
        bool5 = bool1;
        break label631;
      }
      n = j;
      if (MediaSessionLegacyHelper.getHelper(this.mContext).isGlobalPriorityActive())
        n = j & 0xFFFFFFFE;
      k = m;
      i = n;
      bool5 = bool1;
      if ((n & 0x1) != 0)
        break label631;
      this.mBroadcastWakeLock.acquire();
      localObject = this.mHandler.obtainMessage(3, new KeyEvent(paramKeyEvent));
      ((Message)localObject).setAsynchronous(true);
      ((Message)localObject).sendToTarget();
      k = m;
      i = n;
      bool5 = bool1;
      break label631;
      k = m;
      i = j;
      bool5 = bool1;
      if (!bool4)
        break label631;
      localObject = getTelecommService();
      k = m;
      i = j;
      bool5 = bool1;
      if (localObject == null)
        break label631;
      k = m;
      i = j;
      bool5 = bool1;
      if (!((TelecomManager)localObject).isRinging())
        break label631;
      Log.i("WindowManager", "interceptKeyBeforeQueueing: CALL key-down while ringing: Answer the call!");
      ((TelecomManager)localObject).acceptRingingCall();
      i = j & 0xFFFFFFFE;
      k = m;
      bool5 = bool1;
      break label631;
      k = m;
      i = j;
      bool5 = bool1;
      if ((j & 0x1) != 0)
        break label631;
      k = m;
      i = j;
      bool5 = bool1;
      if (bool4)
        break label631;
      this.mBroadcastWakeLock.acquire();
      localObject = this.mHandler;
      if (bool3);
      for (i = 1; ; i = 0)
      {
        localObject = ((Handler)localObject).obtainMessage(12, i, 0);
        ((Message)localObject).setAsynchronous(true);
        ((Message)localObject).sendToTarget();
        k = m;
        i = j;
        bool5 = bool1;
        break;
      }
      k = m;
      i = j;
      bool5 = bool1;
      if (bool2)
        break label631;
      k = m;
      i = j;
      bool5 = bool1;
      if (!bool4)
        break label631;
      k = m;
      i = j;
      bool5 = bool1;
      if (!this.mSPWM.hasCustomDoubleTapHomeCommand())
        break label631;
      k = m;
      i = j;
      bool5 = bool1;
      if (this.mNeedTriggerOWC)
        break label631;
      k = m;
      i = j;
      bool5 = bool1;
      if (!this.mMultitapKeyManager.dispatchMultitapKeyEvent(paramKeyEvent, true))
        break label631;
      i = j & 0xFFFFFFFE;
      k = m;
      bool5 = bool1;
      break label631;
      i = j | 0x1;
      k = m;
      bool5 = bool1;
      break label631;
      k = m;
      i = j;
      bool5 = bool1;
      if (!bool4)
        break label631;
      k = m;
      i = j;
      bool5 = bool1;
      if (paramKeyEvent.getRepeatCount() != 0)
        break label631;
      this.mWatchLaunching = true;
      this.mSPWM.launchPremiumWatch(true);
      k = m;
      i = j;
      bool5 = bool1;
      break label631;
      performHapticFeedbackLw(null, 1, false);
    }
  }

  public void interceptKeyBeforeQuickAccess(int paramInt, float paramFloat1, float paramFloat2)
  {
    this.mSPWM.handleQuickAccess(paramInt, paramFloat1, paramFloat2);
  }

  public int interceptMotionBeforeQueueingNonInteractive(long paramLong, int paramInt)
  {
    if (((paramInt & 0x1) != 0) && (wakeUp(paramLong / 1000000L, this.mAllowTheaterModeWakeFromMotion, 2)));
    do
    {
      return 0;
      if (shouldDispatchInputWhenNonInteractive())
        return 1;
    }
    while ((!isTheaterModeEnabled()) || ((paramInt & 0x1) == 0));
    wakeUp(paramLong / 1000000L, this.mAllowTheaterModeWakeFromMotionWhenNotDreaming, 2);
    return 0;
  }

  public boolean isAccessiblityEnabled()
  {
    return this.mAccessibilityManager.isEnabled();
  }

  public boolean isAllScreenOnFully()
  {
    return (this.mScreenOnFully) && (this.mSubScreenOnFully);
  }

  public boolean isAwake()
  {
    return this.mAwake;
  }

  public boolean isCarModeBarVisible()
  {
    return (this.mCarModeBar != null) && (this.mCarModeBar.isVisibleLw());
  }

  public boolean isCocktailRotationAnimationNeeded()
  {
    return this.mCocktailPhoneWindowManager.isCocktailRotationAnimationNeeded();
  }

  public boolean isDefaultKeyguardRotationAnimationAlwaysUsed()
  {
    return this.mIsDefaultKeyguardRotationAnmationAlwaysUsed;
  }

  public boolean isDefaultOrientationForced()
  {
    return this.mForceDefaultOrientation;
  }

  boolean isDeviceProvisioned()
  {
    int i = 0;
    if (Settings.Global.getInt(this.mContext.getContentResolver(), "device_provisioned", 0) != 0)
      i = 1;
    return i;
  }

  public boolean isForceHideByNightClock()
  {
    return (this.mNightClock != null) && (this.mNightClock.isVisibleLw());
  }

  public boolean isForceHideBySViewCover()
  {
    int j = 0;
    int i = j;
    if (isSupportAndAttachedSViewCover())
    {
      i = j;
      if (this.mWindowManagerFuncs.canGlobalActionsShow())
      {
        i = j;
        if (!this.mCoverState.switchState)
          i = 1;
      }
    }
    return i;
  }

  public boolean isForceHideCascade()
  {
    return this.mMultiPhoneWindowManager.isForceHideCascade();
  }

  public boolean isForceHiding(WindowManager.LayoutParams paramLayoutParams)
  {
    return ((paramLayoutParams.privateFlags & 0x400) != 0) || ((paramLayoutParams.samsungFlags & 0x10000000) != 0) || ((isKeyguardHostWindow(paramLayoutParams)) && (this.mKeyguardDelegate != null) && (this.mKeyguardDelegate.isShowing())) || (paramLayoutParams.type == 2029);
  }

  public boolean isKeyguardDrawnLw()
  {
    synchronized (this.mLock)
    {
      boolean bool = this.mKeyguardDrawnOnce;
      return bool;
    }
  }

  public boolean isKeyguardHostWindow(WindowManager.LayoutParams paramLayoutParams)
  {
    return paramLayoutParams.type == 2000;
  }

  public boolean isKeyguardLocked()
  {
    return keyguardOn();
  }

  public boolean isKeyguardSecure()
  {
    if (this.mKeyguardDelegate == null)
      return false;
    return this.mKeyguardDelegate.isSecure();
  }

  public boolean isKeyguardShowingAndNotOccluded()
  {
    if (this.mKeyguardDelegate == null);
    do
      return false;
    while ((!this.mKeyguardDelegate.isShowing()) || (this.mKeyguardOccluded));
    return true;
  }

  public boolean isKeyguardShowingAndNotOccluded(int paramInt)
  {
    if (this.mKeyguardDelegate == null)
      return false;
    if ((this.mKeyguardDelegate.isShowing()) && (!((DisplayWindowPolicy)this.mDisplayWindowPolicy.get(paramInt)).mKeyguardOccluded));
    for (int i = 1; ; i = 0)
      return i;
  }

  public boolean isKeyguardShowingAndOccluded()
  {
    if (this.mKeyguardDelegate == null);
    do
      return false;
    while ((!this.mKeyguardDelegate.isShowing()) || (!this.mKeyguardOccluded));
    return true;
  }

  public boolean isKeyguardShowingOrOccluded()
  {
    if (this.mKeyguardDelegate == null)
      return false;
    return this.mKeyguardDelegate.isShowing();
  }

  public boolean isLockTaskModeEnabled()
  {
    return this.mLockTaskModeState != 0;
  }

  public boolean isMetaKeyEventRequested(ComponentName paramComponentName)
  {
    return this.mSystemKeyManager.isMetaKeyEventRequested(paramComponentName);
  }

  public boolean isNavigationBarVisible()
  {
    return (this.mNavigationBar != null) && (this.mNavigationBar.isVisibleLw());
  }

  public boolean isNeedLayoutCoverApplication(WindowManagerPolicy.WindowState paramWindowState)
  {
    if (isSupportAndAttachedSViewCover())
    {
      int i = paramWindowState.getCoverMode();
      if (i == 2)
      {
        if (!paramWindowState.willBeHideSViewCoverOnce());
      }
      else
      {
        do
          return true;
        while ((i == 1) || (i == 16));
        if (i == 0)
          switch (paramWindowState.getAttrs().type)
          {
          case 2005:
          case 2020:
          case 2099:
          }
      }
    }
    return false;
  }

  public boolean isRecentConsumed()
  {
    int i = 0;
    if (this.mRecentConsumed)
    {
      this.mRecentConsumed = false;
      i = 1;
    }
    return i;
  }

  public boolean isScreenOn()
  {
    return isScreenOn(0);
  }

  public boolean isScreenOn(int paramInt)
  {
    return this.mScreenOnFully;
  }

  boolean isSharedDeviceKeyguardOn()
  {
    int j = 0;
    int i = j;
    if (getEDM() != null)
    {
      i = j;
      if (getEDM().getEnterpriseSharedDevicePolicy() != null)
      {
        boolean bool = getEDM().getEnterpriseSharedDevicePolicy().isSharedDeviceEnabled();
        i = j;
        if (getPersonaManagerLocked() != null)
        {
          i = j;
          if (bool)
          {
            i = j;
            if (this.mPersonaManager.getKeyguardShowState(0))
              i = 1;
          }
        }
      }
    }
    return i;
  }

  boolean isSharedDeviceUnlockScreens()
  {
    if ((getEDM() != null) && (getEDM().getEnterpriseSharedDevicePolicy() != null))
    {
      if (!getEDM().getEnterpriseSharedDevicePolicy().isSharedDeviceEnabled())
        return false;
    }
    else
      return false;
    if (isSharedDeviceKeyguardOn())
      return true;
    Object localObject2 = null;
    ActivityManager localActivityManager = (ActivityManager)this.mContext.getSystemService("activity");
    Object localObject1 = localObject2;
    if (localActivityManager != null)
    {
      localObject1 = localObject2;
      if (localActivityManager.getRunningTasks(1) != null)
        localObject1 = ((ActivityManager.RunningTaskInfo)localActivityManager.getRunningTasks(1).get(0)).topActivity.getClassName().toString();
    }
    return (localObject1 != null) && ((((String)localObject1).equalsIgnoreCase("com.sec.enterprise.knox.shareddevice.SetupWizardSecuritySettingActivity")) || (((String)localObject1).equalsIgnoreCase("com.sec.enterprise.knox.shareddevice.SetupWizardChooseLockPassword")) || (((String)localObject1).equalsIgnoreCase("com.sec.enterprise.knox.shareddevice.SetupWizardSetPatternActivity")) || (((String)localObject1).equalsIgnoreCase("com.sec.enterprise.knox.shareddevice.SetupWizardSetFingerPrintActivity")));
  }

  public boolean isSmartHallFlipStateUnfold()
  {
    for (int i = 1; ; i = 0)
      synchronized (this.mFoldingAndWrapAroundLock)
      {
        if (this.mFoldingState == 1)
          return i;
      }
  }

  public boolean isStatusBarKeyguard()
  {
    return isStatusBarKeyguard(0);
  }

  boolean isStatusBarKeyguard(int paramInt)
  {
    return (this.mStatusBar != null) && ((this.mStatusBar.getAttrs().privateFlags & 0x400) != 0);
  }

  public boolean isStatusBarSViewCover()
  {
    return (this.mStatusBar != null) && ((this.mStatusBar.getAttrs().samsungFlags & 0x10000000) != 0);
  }

  public boolean isStatusBarVisible()
  {
    return (this.mStatusBar != null) && (this.mStatusBar.isVisibleLw()) && (!this.mStatusBar.isAnimatingLw());
  }

  public boolean isSupportPowerTriplePress()
  {
    return this.mTriplePressOnPowerBehavior != 0;
  }

  public boolean isSystemKeyEventRequested(int paramInt, ComponentName paramComponentName)
  {
    return this.mSystemKeyManager.isSystemKeyEventRequested(paramInt, paramComponentName);
  }

  public boolean isTopLevelWindow(int paramInt)
  {
    return (paramInt < 1000) || (paramInt > 1999) || (paramInt == 1003);
  }

  public boolean isUserSetupComplete()
  {
    int i = 0;
    if ((Settings.Secure.getIntForUser(this.mContext.getContentResolver(), "user_setup_complete", 0, -2) != 0) || (FactoryTest.isFactoryBinary()))
      i = 1;
    return i;
  }

  public boolean isWakeupPreventionPackage(String paramString)
  {
    return this.mSPWM.isWakeupPreventionPackage(paramString);
  }

  public boolean isWrapAroundMainDown()
  {
    while (true)
    {
      synchronized (this.mFoldingAndWrapAroundLock)
      {
        if ((this.mFoldingState == 2) && (this.mLastWrapAroundMode == 2))
        {
          i = 1;
          return i;
        }
      }
      int i = 0;
    }
  }

  public boolean isWrapAroundMainUpWithQuickAccess()
  {
    for (int i = 1; ; i = 0)
      synchronized (this.mFoldingAndWrapAroundLock)
      {
        if ((this.mFoldingState == 2) && (this.mLastWrapAroundMode == 1) && (this.mSubScreenOnFully) && (!this.mScreenOnFully))
          return i;
      }
  }

  public void keepScreenOnStartedLw()
  {
  }

  public void keepScreenOnStoppedLw()
  {
    if (!isKeyguardShowingAndNotOccluded())
      this.mPowerManager.userActivity(SystemClock.uptimeMillis(), false);
  }

  boolean keyguardOn()
  {
    int i = 0;
    if ((getEDM() != null) && (getEDM().getEnterpriseSharedDevicePolicy() != null) && (getEDM().getEnterpriseSharedDevicePolicy().isSharedDeviceEnabled()) && (getPersonaManagerLocked() != null))
      if (this.mPersonaManager.getKeyguardShowState(0))
        i = 1;
    do
      return i;
    while ((!isKeyguardShowingAndNotOccluded()) && (!inKeyguardRestrictedKeyInputMode()));
    return true;
  }

  public void launchHome()
  {
    launchHomeFromHotKey();
  }

  void launchHomeFromHotKey()
  {
    launchHomeFromHotKey(true, true);
  }

  void launchHomeFromHotKey(boolean paramBoolean1, boolean paramBoolean2)
  {
    int j = 0;
    int i = j;
    if (getPersonaManagerLocked() != null)
    {
      i = j;
      if (this.mPersonaManager.getPersonaIds() != null)
      {
        i = j;
        if (this.mPersonaManager.getPersonaIds().length > 0)
          i = this.mPersonaManager.getPersonaIds()[0];
      }
    }
    if (isKnoxKeyguardShownForKioskMode(i))
      Log.d("WindowManager", "launchHomeFromHotKey() > isKnoxKeyguardShownForKioskMode() : true");
    Object localObject;
    while (true)
    {
      return;
      if (!paramBoolean2)
        break label211;
      if ((!isKeyguardShowingAndNotOccluded()) && (!this.mSPWM.isEnableAccessControl(3)))
        break;
      if (!CocktailBarFeatures.isSystemBarType(this.mContext))
        continue;
      localObject = CocktailBarManager.getInstance(this.mContext);
      if (localObject == null)
        continue;
      ((CocktailBarManager)localObject).switchDefaultCocktail();
      return;
    }
    if ((!this.mWatchLaunching) || ((!this.mHideLockScreen) && (this.mKeyguardDelegate != null) && (this.mKeyguardDelegate.isInputRestricted())))
      if (this.mSPWM.launchHomeDuringVzwSetup())
        Log.v("WindowManager", " VerizonSetupWizard is running, launching home ignore keyguard ...");
    try
    {
      ActivityManagerNative.getDefault().stopAppSwitches();
      label180: sendCloseSystemWindows("homekey");
      startDockOrHome(true, paramBoolean1);
      return;
      this.mKeyguardDelegate.verifyUnlock(new WindowManagerPolicy.OnKeyguardExitResult(paramBoolean1)
      {
        public void onKeyguardExitResult(boolean paramBoolean)
        {
          if (paramBoolean);
          try
          {
            ActivityManagerNative.getDefault().stopAppSwitches();
            label12: PhoneWindowManager.this.sendCloseSystemWindows("homekey");
            PhoneWindowManager.this.startDockOrHome(true, this.val$awakenFromDreams);
            return;
          }
          catch (RemoteException localRemoteException)
          {
            break label12;
          }
        }
      });
      return;
      label211: if (this.mSPWM.launchHomeDuringVzwSetup())
      {
        Log.i("WindowManager", "HOME key pressed. Start Vzw setup wizard service.");
        localObject = new Intent("samsung.vzw.setupwizard.intent.action.SHOW_POPUP");
        ((Intent)localObject).setPackage("com.sec.android.app.setupwizard");
        this.mContext.startServiceAsUser((Intent)localObject, UserHandle.CURRENT);
        return;
      }
      if (!this.mSPWM.isCurrentUserSetupComplete())
      {
        Log.d("WindowManager", "Key was blocked by user setup is not completed");
        return;
      }
      try
      {
        ActivityManagerNative.getDefault().stopAppSwitches();
        label294: if (this.mRecentsVisible)
        {
          if (paramBoolean1)
            awakenDreams();
          sendCloseSystemWindows("homekey");
          hideRecentApps(false, true);
          return;
        }
        sendCloseSystemWindows("homekey");
        startDockOrHome(true, paramBoolean1);
        return;
      }
      catch (RemoteException localRemoteException1)
      {
        break label294;
      }
    }
    catch (RemoteException localRemoteException2)
    {
      break label180;
    }
  }

  void launchVoiceAssistWithWakeLock(boolean paramBoolean)
  {
    Object localObject = IDeviceIdleController.Stub.asInterface(ServiceManager.getService("deviceidle"));
    if (localObject != null);
    try
    {
      ((IDeviceIdleController)localObject).exitIdle("voice-search");
      label23: localObject = new Intent("android.speech.action.VOICE_SEARCH_HANDS_FREE");
      ((Intent)localObject).putExtra("android.speech.extras.EXTRA_SECURE", paramBoolean);
      try
      {
        startActivityAsUser((Intent)localObject, UserHandle.CURRENT_OR_SELF);
        label51: this.mBroadcastWakeLock.release();
        return;
      }
      catch (ActivityNotFoundException localActivityNotFoundException)
      {
        break label51;
      }
    }
    catch (RemoteException localRemoteException)
    {
      break label23;
    }
  }

  public void layoutWindowLw(WindowManagerPolicy.WindowState paramWindowState1, WindowManagerPolicy.WindowState paramWindowState2)
  {
    Object localObject1 = this.mStatusBar;
    Object localObject2 = this.mNavigationBar;
    int k = this.mLastSystemUiFlags;
    WindowManagerPolicy.WindowState localWindowState = this.mTopFullscreenOpaqueWindowState;
    WindowManager.LayoutParams localLayoutParams = paramWindowState1.getAttrs();
    if (((paramWindowState1 == localObject1) && (!canReceiveInput(paramWindowState1)) && ((localLayoutParams.samsungFlags & 0x10000000) == 0)) || (paramWindowState1 == localObject2) || (paramWindowState1 == this.mCarModeBar))
      return;
    int j = 0;
    int i = j;
    if (isForceHideBySViewCover())
    {
      i = j;
      if (isNeedLayoutCoverApplication(paramWindowState1))
      {
        updateSViewCoverLayout(true);
        i = 1;
      }
    }
    boolean bool3 = paramWindowState1.isDefaultDisplay();
    label124: int i2;
    label147: int i3;
    int i5;
    MultiWindowStyle localMultiWindowStyle;
    boolean bool1;
    Rect localRect1;
    Rect localRect2;
    Rect localRect3;
    Rect localRect4;
    Rect localRect5;
    Rect localRect6;
    Rect localRect7;
    int m;
    label307: int i4;
    if ((0x4000000 & k) != 0)
    {
      k = 1;
      if ((!bool3) || (paramWindowState1 != this.mLastInputMethodTargetWindow) || (this.mLastInputMethodWindow == null))
        break label1123;
      j = 1;
      if (j != 0)
      {
        if (DEBUG_LAYOUT)
          Slog.i("WindowManager", "Offset ime target window by the last ime window state");
        offsetInputMethodWindowLw(this.mLastInputMethodWindow);
      }
      i2 = PolicyControl.getWindowFlags(paramWindowState1, localLayoutParams);
      i3 = localLayoutParams.softInputMode;
      i5 = PolicyControl.getSystemUiVisibility(paramWindowState1, null);
      localMultiWindowStyle = paramWindowState1.getMultiWindowStyleLw();
      boolean bool2 = this.mMultiPhoneWindowManager.shouldEnableLayoutInsetsBySoftInput(this.mFocusedWindow, paramWindowState1);
      bool1 = bool2;
      if (bool2)
        bool1 = paramWindowState1.isInputMethodTargetLw();
      localRect1 = mTmpParentFrame;
      localRect2 = mTmpDisplayFrame;
      localRect3 = mTmpOverscanFrame;
      localRect4 = mTmpContentFrame;
      localRect5 = mTmpVisibleFrame;
      localRect6 = mTmpDecorFrame;
      localRect7 = mTmpStableFrame;
      localRect6.setEmpty();
      if ((!bool3) || (!this.mHasNavigationBar) || (localObject2 == null) || (!((WindowManagerPolicy.WindowState)localObject2).isVisibleLw()))
        break label1129;
      m = 1;
      i4 = i3 & 0xF0;
      if (!bool3)
        break label1135;
      localRect7.set(this.mStableLeft, this.mStableTop, this.mStableRight, this.mStableBottom);
      label341: if (bool3)
        break label1308;
      if (paramWindowState2 == null)
        break label1159;
      setAttachedWindowFrames(paramWindowState1, i2, i4, paramWindowState2, true, localRect1, localRect2, localRect3, localRect4, localRect5);
      label371: if (this.mCocktailPhoneWindowManager.layoutWindowLw(paramWindowState1, paramWindowState2, localLayoutParams, localMultiWindowStyle, localRect1, localRect2, localRect3, localRect4, localRect5, localRect6))
        break label2583;
      if (((i2 & 0x200) != 0) && (localLayoutParams.type != 2010))
      {
        localRect2.top = -10000;
        localRect2.left = -10000;
        localRect2.bottom = 10000;
        localRect2.right = 10000;
        if ((localLayoutParams.type != 2013) && (localLayoutParams.type != 2098))
        {
          localRect5.top = -10000;
          localRect5.left = -10000;
          localRect4.top = -10000;
          localRect4.left = -10000;
          localRect3.top = -10000;
          localRect3.left = -10000;
          localRect5.bottom = 10000;
          localRect5.right = 10000;
          localRect4.bottom = 10000;
          localRect4.right = 10000;
          localRect3.bottom = 10000;
          localRect3.right = 10000;
        }
      }
      if ((!shouldUseOutsets(localLayoutParams, i2)) || (!localMultiWindowStyle.isNormal()))
        break label6596;
      j = 1;
      label591: if ((!bool3) || (j == 0))
        break label6677;
      localObject2 = mTmpOutsetFrame;
      ((Rect)localObject2).set(localRect4.left, localRect4.top, localRect4.right, localRect4.bottom);
      j = ScreenShapeHelper.getWindowOutsetBottomPx(this.mContext.getResources());
      localObject1 = localObject2;
      if (j > 0)
      {
        k = this.mDisplayRotation;
        if (k != 0)
          break label6602;
        ((Rect)localObject2).bottom += j;
        label676: localObject1 = localObject2;
        if (DEBUG_LAYOUT)
          Slog.v("WindowManager", "applying bottom outset of " + j + " with rotation " + k + ", result: " + localObject2);
      }
    }
    label1159: label2826: label6668: label6677: for (localObject1 = localObject2; ; localObject1 = null)
    {
      if (DEBUG_LAYOUT)
      {
        localObject2 = new StringBuilder().append("Compute frame ").append(localLayoutParams.getTitle()).append(": sim=#").append(Integer.toHexString(i3)).append(" attach=").append(paramWindowState2).append(" type=").append(localLayoutParams.type).append(String.format(" flags=0x%08x", new Object[] { Integer.valueOf(i2) })).append(" pf=").append(localRect1.toShortString()).append(" df=").append(localRect2.toShortString()).append(" of=").append(localRect3.toShortString()).append(" cf=").append(localRect4.toShortString()).append(" vf=").append(localRect5.toShortString()).append(" dcf=").append(localRect6.toShortString()).append(" sf=").append(localRect7.toShortString()).append(" osf=");
        if (localObject1 != null)
          break label6668;
      }
      for (paramWindowState2 = "null"; ; paramWindowState2 = ((Rect)localObject1).toShortString())
      {
        Slog.v("WindowManager", paramWindowState2);
        paramWindowState1.computeFrameLw(localRect1, localRect2, localRect3, localRect4, localRect5, localRect6, localRect7, (Rect)localObject1);
        if (((localLayoutParams.type == 2011) || (localLayoutParams.type == 2280)) && (paramWindowState1.isVisibleOrBehindKeyguardLw()) && (!paramWindowState1.getGivenInsetsPendingLw()) && (paramWindowState1.isDrawFinishedLw()))
        {
          setLastInputMethodWindowLw(null, null);
          if ((localLayoutParams.type == 2011) || ((this.mInputMethod != null) && (this.mInputMethod.getAttrs().height != -1)))
            offsetInputMethodWindowLw(paramWindowState1);
        }
        if ((localLayoutParams.type == 2031) && (paramWindowState1.isVisibleOrBehindKeyguardLw()) && (!paramWindowState1.getGivenInsetsPendingLw()))
          offsetVoiceInputWindowLw(paramWindowState1);
        if (i == 0)
          break;
        updateSViewCoverLayout(false);
        return;
        k = 0;
        break label124;
        label1123: j = 0;
        break label147;
        label1129: m = 0;
        break label307;
        label1135: localRect7.set(this.mOverscanLeft, this.mOverscanTop, this.mOverscanRight, this.mOverscanBottom);
        break label341;
        j = this.mOverscanScreenLeft;
        localRect4.left = j;
        localRect3.left = j;
        localRect2.left = j;
        localRect1.left = j;
        j = this.mOverscanScreenTop;
        localRect4.top = j;
        localRect3.top = j;
        localRect2.top = j;
        localRect1.top = j;
        j = this.mOverscanScreenLeft + this.mOverscanScreenWidth;
        localRect4.right = j;
        localRect3.right = j;
        localRect2.right = j;
        localRect1.right = j;
        j = this.mOverscanScreenTop + this.mOverscanScreenHeight;
        localRect4.bottom = j;
        localRect3.bottom = j;
        localRect2.bottom = j;
        localRect1.bottom = j;
        break label371;
        label1308: if ((localLayoutParams.type == 2011) || (localLayoutParams.type == 2280))
        {
          j = this.mDockLeft;
          localRect5.left = j;
          localRect4.left = j;
          localRect3.left = j;
          localRect2.left = j;
          localRect1.left = j;
          j = this.mDockTop;
          localRect5.top = j;
          localRect4.top = j;
          localRect3.top = j;
          localRect2.top = j;
          localRect1.top = j;
          j = this.mDockRight;
          localRect5.right = j;
          localRect4.right = j;
          localRect3.right = j;
          localRect2.right = j;
          localRect1.right = j;
          j = this.mUnrestrictedScreenTop + this.mUnrestrictedScreenHeight;
          localRect3.bottom = j;
          localRect2.bottom = j;
          localRect1.bottom = j;
          j = this.mStableBottom;
          localRect5.bottom = j;
          localRect4.bottom = j;
          if ((this.mMobileKeyboardEnabled) && ((localLayoutParams.samsungFlags & 0x800) != 0))
          {
            localRect1.bottom += this.mMobileKeyboardHeight;
            localRect2.bottom += this.mMobileKeyboardHeight;
            localRect3.bottom += this.mMobileKeyboardHeight;
            localRect4.bottom += this.mMobileKeyboardHeight;
            localRect5.bottom += this.mMobileKeyboardHeight;
          }
          localLayoutParams.gravity = 80;
          this.mDockLayer = paramWindowState1.getSurfaceLayer();
          break label371;
        }
        if (localLayoutParams.type == 2031)
        {
          j = this.mUnrestrictedScreenLeft;
          localRect3.left = j;
          localRect2.left = j;
          localRect1.left = j;
          j = this.mUnrestrictedScreenTop;
          localRect3.top = j;
          localRect2.top = j;
          localRect1.top = j;
          j = this.mUnrestrictedScreenLeft + this.mUnrestrictedScreenWidth;
          localRect3.right = j;
          localRect2.right = j;
          localRect1.right = j;
          j = this.mUnrestrictedScreenTop + this.mUnrestrictedScreenHeight;
          localRect3.bottom = j;
          localRect2.bottom = j;
          localRect1.bottom = j;
          j = this.mStableBottom;
          localRect5.bottom = j;
          localRect4.bottom = j;
          j = this.mStableRight;
          localRect5.right = j;
          localRect4.right = j;
          j = this.mStableLeft;
          localRect5.left = j;
          localRect4.left = j;
          j = this.mStableTop;
          localRect5.top = j;
          localRect4.top = j;
          break label371;
        }
        if (paramWindowState1 == localObject1)
        {
          j = this.mUnrestrictedScreenLeft;
          localRect3.left = j;
          localRect2.left = j;
          localRect1.left = j;
          j = this.mUnrestrictedScreenTop;
          localRect3.top = j;
          localRect2.top = j;
          localRect1.top = j;
          j = this.mUnrestrictedScreenWidth + this.mUnrestrictedScreenLeft;
          localRect3.right = j;
          localRect2.right = j;
          localRect1.right = j;
          j = this.mUnrestrictedScreenHeight + this.mUnrestrictedScreenTop;
          localRect3.bottom = j;
          localRect2.bottom = j;
          localRect1.bottom = j;
          j = this.mStableLeft;
          localRect5.left = j;
          localRect4.left = j;
          j = this.mStableTop;
          localRect5.top = j;
          localRect4.top = j;
          j = this.mStableRight;
          localRect5.right = j;
          localRect4.right = j;
          localRect5.bottom = this.mStableBottom;
          localRect4.bottom = this.mContentBottom;
          break label371;
        }
        localRect6.left = this.mSystemLeft;
        localRect6.top = this.mSystemTop;
        localRect6.right = this.mSystemRight;
        localRect6.bottom = this.mSystemBottom;
        int n;
        label2087: int i1;
        if ((localLayoutParams.privateFlags & 0x200) != 0)
        {
          n = 1;
          if ((localLayoutParams.type < 1) || (localLayoutParams.type > 99))
            break label2591;
          i1 = 1;
          label2109: if ((paramWindowState1 != localWindowState) || (paramWindowState1.isAnimatingLw()))
            break label2597;
          label2124: if ((localLayoutParams.x != 0) || (localLayoutParams.y != 0) || (localLayoutParams.width != -1) || (localLayoutParams.height != -1))
            break label2600;
          j = 1;
          label2161: if ((i1 != 0) && (n == 0) && (j != 0) && (localMultiWindowStyle.isNormal()))
          {
            if (((i5 & 0x4) != 0) || ((i2 & 0x400) != 0) || ((0x4000000 & i2) != 0))
              break label2606;
            if ((0x80000000 & i2) == 0)
              localRect6.top = this.mStableTop;
            n = this.mStableTop;
            this.mDockTop = n;
            this.mCurTop = n;
            this.mVoiceContentTop = n;
            this.mContentTop = n;
            if (((0x8000000 & i2) == 0) && ((i5 & 0x2) == 0) && ((0x80000000 & i2) == 0))
            {
              localRect6.bottom = this.mStableBottom;
              localRect6.right = this.mStableRight;
            }
          }
          label2257: if (localMultiWindowStyle.isSplit())
          {
            n = this.mUnrestrictedScreenTop;
            this.mDockTop = n;
            this.mCurTop = n;
            this.mContentTop = n;
          }
          if (localLayoutParams.type == 2255)
            localRect6.top = 0;
          if ((0x10100 & i2) != 65792)
            break label3760;
          if (DEBUG_LAYOUT)
            Slog.v("WindowManager", "layoutWindowLw(" + localLayoutParams.getTitle() + "): IN_SCREEN, INSET_DECOR");
          if (paramWindowState2 == null)
            break label2681;
          setAttachedWindowFrames(paramWindowState1, i2, i4, paramWindowState2, true, localRect1, localRect2, localRect3, localRect4, localRect5);
        }
        while (true)
        {
          if ((j == 0) || (localLayoutParams.type != 2026) || ((localLayoutParams.samsungFlags & 0x80000000) == 0))
            break label6594;
          j = this.mOverscanScreenLeft;
          localRect3.left = j;
          localRect2.left = j;
          localRect1.left = j;
          j = this.mOverscanScreenTop;
          localRect3.top = j;
          localRect2.top = j;
          localRect1.top = j;
          j = this.mOverscanScreenLeft + this.mOverscanScreenWidth;
          localRect3.right = j;
          localRect2.right = j;
          localRect1.right = j;
          j = this.mOverscanScreenTop + this.mOverscanScreenHeight;
          localRect3.bottom = j;
          localRect2.bottom = j;
          localRect1.bottom = j;
          localRect6.set(localRect1);
          break label371;
          label2583: break;
          n = 0;
          break label2087;
          label2591: i1 = 0;
          break label2109;
          label2597: break label2124;
          label2600: j = 0;
          break label2161;
          label2606: if ((i2 & 0x800) != 0)
          {
            n = this.mStableTop;
            this.mDockTop = n;
            this.mCurTop = n;
            this.mVoiceContentTop = n;
            this.mContentTop = n;
            break label2257;
          }
          n = this.mUnrestrictedScreenTop;
          this.mDockTop = n;
          this.mCurTop = n;
          this.mVoiceContentTop = n;
          this.mContentTop = n;
          break label2257;
          label2681: if ((localLayoutParams.type == 2014) || (localLayoutParams.type == 2096) || (localLayoutParams.type == 2017))
            if (m != 0)
            {
              n = this.mDockLeft;
              localRect3.left = n;
              localRect2.left = n;
              localRect1.left = n;
              n = this.mUnrestrictedScreenTop;
              localRect3.top = n;
              localRect2.top = n;
              localRect1.top = n;
              if (m == 0)
                break label3046;
              n = this.mRestrictedScreenLeft + this.mRestrictedScreenWidth;
              localRect3.right = n;
              localRect2.right = n;
              localRect1.right = n;
              if (m == 0)
                break label3060;
              m = this.mRestrictedScreenTop + this.mRestrictedScreenHeight;
              localRect3.bottom = m;
              localRect2.bottom = m;
              localRect1.bottom = m;
              if (DEBUG_LAYOUT)
                Slog.v("WindowManager", String.format("Laying out status bar window: (%d,%d - %d,%d)", new Object[] { Integer.valueOf(localRect1.left), Integer.valueOf(localRect1.top), Integer.valueOf(localRect1.right), Integer.valueOf(localRect1.bottom) }));
              if ((i2 & 0x400) != 0)
                break label3663;
              if (!paramWindowState1.isVoiceInteraction())
                break label3538;
              localRect4.left = this.mVoiceContentLeft;
              localRect4.top = this.mVoiceContentTop;
              localRect4.right = this.mVoiceContentRight;
              localRect4.bottom = this.mVoiceContentBottom;
            }
          while (true)
          {
            label2725: label2789: if (!localMultiWindowStyle.isSplit())
              applyStableConstraints(i5, i2, localRect4);
            label2914: if ((i4 == 48) || (!bool1))
              break label3750;
            localRect5.left = this.mCurLeft;
            localRect5.top = this.mCurTop;
            localRect5.right = this.mCurRight;
            localRect5.bottom = this.mCurBottom;
            break;
            n = this.mUnrestrictedScreenLeft;
            break label2725;
            label3046: n = this.mUnrestrictedScreenLeft + this.mUnrestrictedScreenWidth;
            break label2789;
            label3060: m = this.mUnrestrictedScreenTop + this.mUnrestrictedScreenHeight;
            break label2826;
            if (((0x2000000 & i2) != 0) && (localLayoutParams.type >= 1) && (localLayoutParams.type <= 1999))
            {
              m = this.mOverscanScreenLeft;
              localRect3.left = m;
              localRect2.left = m;
              localRect1.left = m;
              m = this.mOverscanScreenTop;
              localRect3.top = m;
              localRect2.top = m;
              localRect1.top = m;
              m = this.mOverscanScreenLeft + this.mOverscanScreenWidth;
              localRect3.right = m;
              localRect2.right = m;
              localRect1.right = m;
              m = this.mOverscanScreenTop + this.mOverscanScreenHeight;
              localRect3.bottom = m;
              localRect2.bottom = m;
              localRect1.bottom = m;
              break label2914;
            }
            if ((canHideNavigationBar()) && ((i5 & 0x200) != 0) && (localLayoutParams.type >= 1) && (localLayoutParams.type <= 1999))
            {
              m = this.mOverscanScreenLeft;
              localRect2.left = m;
              localRect1.left = m;
              m = this.mOverscanScreenTop;
              localRect2.top = m;
              localRect1.top = m;
              m = this.mOverscanScreenLeft + this.mOverscanScreenWidth;
              localRect2.right = m;
              localRect1.right = m;
              m = this.mOverscanScreenTop + this.mOverscanScreenHeight;
              localRect2.bottom = m;
              localRect1.bottom = m;
              localRect3.left = this.mUnrestrictedScreenLeft;
              localRect3.top = this.mUnrestrictedScreenTop;
              localRect3.right = (this.mUnrestrictedScreenLeft + this.mUnrestrictedScreenWidth);
              localRect3.bottom = (this.mUnrestrictedScreenTop + this.mUnrestrictedScreenHeight);
              break label2914;
            }
            m = this.mRestrictedOverscanScreenLeft;
            localRect2.left = m;
            localRect1.left = m;
            m = this.mRestrictedOverscanScreenTop;
            localRect2.top = m;
            localRect1.top = m;
            m = this.mRestrictedOverscanScreenLeft + this.mRestrictedOverscanScreenWidth;
            localRect2.right = m;
            localRect1.right = m;
            m = this.mRestrictedOverscanScreenTop + this.mRestrictedOverscanScreenHeight;
            localRect2.bottom = m;
            localRect1.bottom = m;
            localRect3.left = this.mUnrestrictedScreenLeft;
            localRect3.top = this.mUnrestrictedScreenTop;
            localRect3.right = (this.mUnrestrictedScreenLeft + this.mUnrestrictedScreenWidth);
            localRect3.bottom = (this.mUnrestrictedScreenTop + this.mUnrestrictedScreenHeight);
            break label2914;
            label3538: if (i4 != 16)
            {
              if ((!localMultiWindowStyle.isSplit()) && (localObject1 != null) && (((WindowManagerPolicy.WindowState)localObject1).isVisibleLw()) && (k == 0));
              for (localRect4.top = this.mStableTop; ; localRect4.top = this.mDockTop)
              {
                localRect4.left = this.mDockLeft;
                localRect4.right = this.mDockRight;
                localRect4.bottom = this.mDockBottom;
                break;
              }
            }
            localRect4.left = this.mContentLeft;
            localRect4.top = this.mContentTop;
            localRect4.right = this.mContentRight;
            localRect4.bottom = this.mContentBottom;
            continue;
            label3663: localRect4.left = this.mRestrictedScreenLeft;
            localRect4.top = this.mRestrictedScreenTop;
            localRect4.right = (this.mRestrictedScreenLeft + this.mRestrictedScreenWidth);
            localRect4.bottom = (this.mRestrictedScreenTop + this.mRestrictedScreenHeight);
            if ((localLayoutParams.samsungFlags & 0x1) == 0)
              continue;
            if (i4 != 16)
            {
              localRect4.bottom = this.mDockBottom;
              continue;
            }
            localRect4.bottom = this.mContentBottom;
          }
          label3750: localRect5.set(localRect4);
          continue;
          label3760: if (((i2 & 0x100) != 0) || ((i5 & 0x600) != 0))
          {
            if (DEBUG_LAYOUT)
              Slog.v("WindowManager", "layoutWindowLw(" + localLayoutParams.getTitle() + "): IN_SCREEN");
            if ((!localMultiWindowStyle.isNormal()) && (paramWindowState2 != null))
            {
              setAttachedWindowFrames(paramWindowState1, i2, i4, paramWindowState2, true, localRect1, localRect2, localRect3, localRect4, localRect5);
              continue;
            }
            if ((localLayoutParams.type == 2014) || (localLayoutParams.type == 2096) || (localLayoutParams.type == 2017) || (localLayoutParams.type == 2020))
              if (m != 0)
              {
                k = this.mDockLeft;
                label3912: localRect4.left = k;
                localRect3.left = k;
                localRect2.left = k;
                localRect1.left = k;
                k = this.mUnrestrictedScreenTop;
                localRect4.top = k;
                localRect3.top = k;
                localRect2.top = k;
                localRect1.top = k;
                if (m == 0)
                  break label4207;
                k = this.mRestrictedScreenLeft + this.mRestrictedScreenWidth;
                localRect4.right = k;
                localRect3.right = k;
                localRect2.right = k;
                localRect1.right = k;
                if (m == 0)
                  break label4221;
                k = this.mRestrictedScreenTop + this.mRestrictedScreenHeight;
                label4034: localRect4.bottom = k;
                localRect3.bottom = k;
                localRect2.bottom = k;
                localRect1.bottom = k;
                if (DEBUG_LAYOUT)
                  Slog.v("WindowManager", String.format("Laying out IN_SCREEN status bar window: (%d,%d - %d,%d)", new Object[] { Integer.valueOf(localRect1.left), Integer.valueOf(localRect1.top), Integer.valueOf(localRect1.right), Integer.valueOf(localRect1.bottom) }));
              }
            while (true)
            {
              if (!localMultiWindowStyle.isSplit())
                applyStableConstraints(i5, i2, localRect4);
              if ((i4 == 48) || (!bool1))
                break label5408;
              localRect5.left = this.mCurLeft;
              localRect5.top = this.mCurTop;
              localRect5.right = this.mCurRight;
              localRect5.bottom = this.mCurBottom;
              break;
              k = this.mUnrestrictedScreenLeft;
              break label3912;
              label4207: k = this.mUnrestrictedScreenLeft + this.mUnrestrictedScreenWidth;
              break label3990;
              label4221: k = this.mUnrestrictedScreenTop + this.mUnrestrictedScreenHeight;
              break label4034;
              if ((localLayoutParams.type == 2019) || (localLayoutParams.type == 2024))
              {
                k = this.mUnrestrictedScreenLeft;
                localRect3.left = k;
                localRect2.left = k;
                localRect1.left = k;
                k = this.mUnrestrictedScreenTop;
                localRect3.top = k;
                localRect2.top = k;
                localRect1.top = k;
                k = this.mUnrestrictedScreenLeft + this.mUnrestrictedScreenWidth;
                localRect3.right = k;
                localRect2.right = k;
                localRect1.right = k;
                k = this.mUnrestrictedScreenTop + this.mUnrestrictedScreenHeight;
                localRect3.bottom = k;
                localRect2.bottom = k;
                localRect1.bottom = k;
                if (!DEBUG_LAYOUT)
                  continue;
                Slog.v("WindowManager", String.format("Laying out navigation bar window: (%d,%d - %d,%d)", new Object[] { Integer.valueOf(localRect1.left), Integer.valueOf(localRect1.top), Integer.valueOf(localRect1.right), Integer.valueOf(localRect1.bottom) }));
                continue;
              }
              if (((localLayoutParams.type == 2015) || (localLayoutParams.type == 2021) || (localLayoutParams.type == 2230) || (localLayoutParams.type == 2225)) && ((i2 & 0x400) != 0))
              {
                k = this.mOverscanScreenLeft;
                localRect4.left = k;
                localRect3.left = k;
                localRect2.left = k;
                localRect1.left = k;
                k = this.mOverscanScreenTop;
                localRect4.top = k;
                localRect3.top = k;
                localRect2.top = k;
                localRect1.top = k;
                k = this.mOverscanScreenLeft + this.mOverscanScreenWidth;
                localRect4.right = k;
                localRect3.right = k;
                localRect2.right = k;
                localRect1.right = k;
                k = this.mOverscanScreenTop + this.mOverscanScreenHeight;
                localRect4.bottom = k;
                localRect3.bottom = k;
                localRect2.bottom = k;
                localRect1.bottom = k;
                continue;
              }
              if ((localLayoutParams.type != 2021) || ((localLayoutParams.type == 2013) || (localLayoutParams.type == 2098)))
              {
                k = this.mOverscanScreenLeft;
                localRect2.left = k;
                localRect1.left = k;
                k = this.mOverscanScreenTop;
                localRect2.top = k;
                localRect1.top = k;
                k = this.mOverscanScreenLeft + this.mOverscanScreenWidth;
                localRect2.right = k;
                localRect1.right = k;
                k = this.mOverscanScreenTop + this.mOverscanScreenHeight;
                localRect2.bottom = k;
                localRect1.bottom = k;
                k = this.mUnrestrictedScreenLeft;
                localRect4.left = k;
                localRect3.left = k;
                k = this.mUnrestrictedScreenTop;
                localRect4.top = k;
                localRect3.top = k;
                k = this.mUnrestrictedScreenLeft + this.mUnrestrictedScreenWidth;
                localRect4.right = k;
                localRect3.right = k;
                k = this.mUnrestrictedScreenTop + this.mUnrestrictedScreenHeight;
                localRect4.bottom = k;
                localRect3.bottom = k;
                continue;
              }
              if (((0x2000000 & i2) != 0) && (localLayoutParams.type >= 1) && (localLayoutParams.type <= 1999))
              {
                k = this.mOverscanScreenLeft;
                localRect4.left = k;
                localRect3.left = k;
                localRect2.left = k;
                localRect1.left = k;
                k = this.mOverscanScreenTop;
                localRect4.top = k;
                localRect3.top = k;
                localRect2.top = k;
                localRect1.top = k;
                k = this.mOverscanScreenLeft + this.mOverscanScreenWidth;
                localRect4.right = k;
                localRect3.right = k;
                localRect2.right = k;
                localRect1.right = k;
                k = this.mOverscanScreenTop + this.mOverscanScreenHeight;
                localRect4.bottom = k;
                localRect3.bottom = k;
                localRect2.bottom = k;
                localRect1.bottom = k;
                continue;
              }
              if ((canHideNavigationBar()) && ((i5 & 0x200) != 0) && ((localLayoutParams.type == 2000) || (localLayoutParams.type == 2005) || (localLayoutParams.type == 2033) || ((localLayoutParams.type >= 1) && (localLayoutParams.type <= 1999))))
              {
                k = this.mUnrestrictedScreenLeft;
                localRect4.left = k;
                localRect3.left = k;
                localRect2.left = k;
                localRect1.left = k;
                k = this.mUnrestrictedScreenTop;
                localRect4.top = k;
                localRect3.top = k;
                localRect2.top = k;
                localRect1.top = k;
                k = this.mUnrestrictedScreenLeft + this.mUnrestrictedScreenWidth;
                localRect4.right = k;
                localRect3.right = k;
                localRect2.right = k;
                localRect1.right = k;
                k = this.mUnrestrictedScreenTop + this.mUnrestrictedScreenHeight;
                localRect4.bottom = k;
                localRect3.bottom = k;
                localRect2.bottom = k;
                localRect1.bottom = k;
                continue;
              }
              k = this.mRestrictedScreenLeft;
              localRect4.left = k;
              localRect3.left = k;
              localRect2.left = k;
              localRect1.left = k;
              k = this.mRestrictedScreenTop;
              localRect4.top = k;
              localRect3.top = k;
              localRect2.top = k;
              localRect1.top = k;
              k = this.mRestrictedScreenLeft + this.mRestrictedScreenWidth;
              localRect4.right = k;
              localRect3.right = k;
              localRect2.right = k;
              localRect1.right = k;
              k = this.mRestrictedScreenTop + this.mRestrictedScreenHeight;
              localRect4.bottom = k;
              localRect3.bottom = k;
              localRect2.bottom = k;
              localRect1.bottom = k;
            }
            localRect5.set(localRect4);
            continue;
          }
          label3990: label5408: if (paramWindowState2 != null)
          {
            if (DEBUG_LAYOUT)
              Slog.v("WindowManager", "layoutWindowLw(" + localLayoutParams.getTitle() + "): attached to " + paramWindowState2);
            setAttachedWindowFrames(paramWindowState1, i2, i4, paramWindowState2, false, localRect1, localRect2, localRect3, localRect4, localRect5);
            continue;
          }
          if (DEBUG_LAYOUT)
            Slog.v("WindowManager", "layoutWindowLw(" + localLayoutParams.getTitle() + "): normal window");
          if ((localLayoutParams.type == 2014) || (localLayoutParams.type == 2020) || (localLayoutParams.type == 2096))
          {
            k = this.mRestrictedScreenLeft;
            localRect4.left = k;
            localRect3.left = k;
            localRect2.left = k;
            localRect1.left = k;
            k = this.mRestrictedScreenTop;
            localRect4.top = k;
            localRect3.top = k;
            localRect2.top = k;
            localRect1.top = k;
            k = this.mRestrictedScreenLeft + this.mRestrictedScreenWidth;
            localRect4.right = k;
            localRect3.right = k;
            localRect2.right = k;
            localRect1.right = k;
            k = this.mRestrictedScreenTop + this.mRestrictedScreenHeight;
            localRect4.bottom = k;
            localRect3.bottom = k;
            localRect2.bottom = k;
            localRect1.bottom = k;
            continue;
          }
          if ((localLayoutParams.type == 2005) || (localLayoutParams.type == 2003))
          {
            k = this.mStableLeft;
            localRect4.left = k;
            localRect3.left = k;
            localRect2.left = k;
            localRect1.left = k;
            k = this.mStableTop;
            localRect4.top = k;
            localRect3.top = k;
            localRect2.top = k;
            localRect1.top = k;
            k = this.mStableRight;
            localRect4.right = k;
            localRect3.right = k;
            localRect2.right = k;
            localRect1.right = k;
            k = this.mStableBottom;
            localRect4.bottom = k;
            localRect3.bottom = k;
            localRect2.bottom = k;
            localRect1.bottom = k;
            continue;
          }
          if (localLayoutParams.type == 2271)
          {
            k = this.mOverscanScreenLeft;
            localRect4.left = k;
            localRect3.left = k;
            localRect2.left = k;
            localRect1.left = k;
            k = this.mOverscanScreenTop;
            localRect4.top = k;
            localRect3.top = k;
            localRect2.top = k;
            localRect1.top = k;
            k = this.mOverscanScreenLeft + this.mOverscanScreenWidth;
            localRect4.right = k;
            localRect3.right = k;
            localRect2.right = k;
            localRect1.right = k;
            k = this.mOverscanScreenTop + this.mOverscanScreenHeight;
            localRect4.bottom = k;
            localRect3.bottom = k;
            localRect2.bottom = k;
            localRect1.bottom = k;
            localRect6.set(localRect1);
            continue;
          }
          localRect1.left = this.mContentLeft;
          if (localMultiWindowStyle.isCascade())
          {
            localRect1.top = 0;
            localRect1.bottom = this.mDockBottom;
            localRect1.right = this.mContentRight;
            localRect1.bottom = this.mContentBottom;
            if (!paramWindowState1.isVoiceInteraction())
              break label6357;
            k = this.mVoiceContentLeft;
            localRect4.left = k;
            localRect3.left = k;
            localRect2.left = k;
            k = this.mVoiceContentTop;
            localRect4.top = k;
            localRect3.top = k;
            localRect2.top = k;
            k = this.mVoiceContentRight;
            localRect4.right = k;
            localRect3.right = k;
            localRect2.right = k;
            k = this.mVoiceContentBottom;
            localRect4.bottom = k;
            localRect3.bottom = k;
            localRect2.bottom = k;
          }
          while (true)
          {
            if ((i4 == 48) || (!bool1))
              break label6586;
            localRect5.left = this.mCurLeft;
            localRect5.top = this.mCurTop;
            localRect5.right = this.mCurRight;
            localRect5.bottom = this.mCurBottom;
            break;
            m = 0;
            k = m;
            if (this.mMultiPhoneWindowManager.getLongPressedMinimizeIcon() != null)
            {
              k = m;
              if (paramWindowState1.getMultiWindowStyleLw().isNormal())
              {
                k = m;
                if (paramWindowState1.isFloating())
                  k = 1;
              }
            }
            if (k != 0)
              localRect1.top += this.mStatusBarHeight;
            while (true)
            {
              localRect1.bottom = this.mContentBottom;
              break;
              localRect1.top = this.mContentTop;
            }
            label6357: if (i4 != 16)
            {
              k = this.mDockLeft;
              localRect4.left = k;
              localRect3.left = k;
              localRect2.left = k;
              k = this.mDockTop;
              localRect4.top = k;
              localRect3.top = k;
              localRect2.top = k;
              k = this.mDockRight;
              localRect4.right = k;
              localRect3.right = k;
              localRect2.right = k;
              k = this.mDockBottom;
              localRect4.bottom = k;
              localRect3.bottom = k;
              localRect2.bottom = k;
              continue;
            }
            k = this.mContentLeft;
            localRect4.left = k;
            localRect3.left = k;
            localRect2.left = k;
            k = this.mContentTop;
            localRect4.top = k;
            localRect3.top = k;
            localRect2.top = k;
            k = this.mContentRight;
            localRect4.right = k;
            localRect3.right = k;
            localRect2.right = k;
            k = this.mContentBottom;
            localRect4.bottom = k;
            localRect3.bottom = k;
            localRect2.bottom = k;
          }
          label6586: localRect5.set(localRect4);
        }
        label6594: break label371;
        label6596: j = 0;
        break label591;
        label6602: if (k == 1)
        {
          ((Rect)localObject2).right += j;
          break label676;
        }
        if (k == 2)
        {
          ((Rect)localObject2).top -= j;
          break label676;
        }
        if (k != 3)
          break label676;
        ((Rect)localObject2).left -= j;
        break label676;
      }
    }
  }

  public void lockNow(Bundle paramBundle)
  {
    this.mContext.enforceCallingOrSelfPermission("android.permission.DEVICE_POWER", null);
    this.mHandler.removeCallbacks(this.mScreenLockTimeout);
    if (paramBundle != null)
      this.mScreenLockTimeout.setLockOptions(paramBundle);
    this.mHandler.post(this.mScreenLockTimeout);
  }

  public boolean needForceHide(WindowManagerPolicy.WindowState paramWindowState1, WindowManager.LayoutParams paramLayoutParams1, MultiWindowStyle paramMultiWindowStyle1, WindowManagerPolicy.WindowState paramWindowState2, WindowManager.LayoutParams paramLayoutParams2, MultiWindowStyle paramMultiWindowStyle2)
  {
    if (this.mCocktailPhoneWindowManager.needForceHide(paramWindowState1))
      return true;
    return this.mMultiPhoneWindowManager.needForceHide(paramWindowState1, paramLayoutParams1, paramMultiWindowStyle1, paramWindowState2, paramLayoutParams2, paramMultiWindowStyle2);
  }

  public boolean needHideTrayBar()
  {
    return this.mMultiPhoneWindowManager.needHideTrayBar(getTopFullscreenOpaqueWindowState(), null);
  }

  boolean needSensorRunningLp()
  {
    if ((this.mSupportAutoRotation) && ((this.mCurrentAppOrientation == 4) || (this.mCurrentAppOrientation == 10) || (this.mCurrentAppOrientation == 7) || (this.mCurrentAppOrientation == 6)));
    do
      return true;
    while (((this.mCarDockEnablesAccelerometer) && (this.mDockMode == 2)) || ((this.mDeskDockEnablesAccelerometer) && ((this.mDockMode == 1) || (this.mDockMode == 3) || (this.mDockMode == 4))) || ((this.mMirrorLinkDockEnablesAccelerometer) && (this.mDockMode == 104)));
    if (SamsungPolicyProperties.FolderTypeFeature(this.mContext) == 2)
    {
      if (((this.mUserRotationMode == 1) && (this.mLidState == 0)) || ((this.mSecondLcdUserRotationMode == 1) && (this.mLidState == 1)))
        return false;
    }
    else if ((this.mUserRotationMode == 1) || (this.mMobileKeyboardEnabled))
      return false;
    return this.mSupportAutoRotation;
  }

  public void notifyActivityDrawnForKeyguardLw()
  {
    if (this.mKeyguardDelegate != null)
      this.mHandler.post(new Runnable()
      {
        public void run()
        {
          PhoneWindowManager.this.mKeyguardDelegate.onActivityDrawn();
        }
      });
  }

  public void notifyCameraLensCoverSwitchChanged(long paramLong, boolean paramBoolean)
  {
    if (paramBoolean);
    for (int i = 1; this.mCameraLensCoverState == i; i = 0)
      return;
    if ((this.mCameraLensCoverState == 1) && (i == 0))
    {
      if (this.mKeyguardDelegate != null)
        break label92;
      paramBoolean = false;
      if (!paramBoolean)
        break label103;
    }
    label92: label103: for (Intent localIntent = new Intent("android.media.action.STILL_IMAGE_CAMERA_SECURE"); ; localIntent = new Intent("android.media.action.STILL_IMAGE_CAMERA"))
    {
      wakeUp(paramLong / 1000000L, this.mAllowTheaterModeWakeFromCameraLens, 3);
      startActivityAsUser(localIntent, UserHandle.CURRENT_OR_SELF);
      this.mCameraLensCoverState = i;
      return;
      paramBoolean = this.mKeyguardDelegate.isShowing();
      break;
    }
  }

  public void notifyCoverSwitchStateChanged(long paramLong, boolean paramBoolean)
  {
    int i = 1;
    if (CscFeature.getInstance().getEnableStatus("CscFeature_Common_DisableNfcHwKeypad"))
      Slog.d("WindowManager", "Mobile Keyboard is disabled by CscFeature.");
    label77: ContentResolver localContentResolver;
    while (true)
    {
      return;
      if (paramBoolean)
        break;
      paramBoolean = true;
      if (!this.mSPWM.isEasyModeEnabled())
        break label160;
      if ((paramLong != 0L) && (paramBoolean))
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable()
        {
          public void run()
          {
            if (PhoneWindowManager.this.alertToast != null)
              PhoneWindowManager.this.alertToast.cancel();
            ContextThemeWrapper localContextThemeWrapper = new ContextThemeWrapper(PhoneWindowManager.this.mContext, 16974123);
            String str1 = localContextThemeWrapper.getResources().getString(17042010);
            String str2 = localContextThemeWrapper.getResources().getString(17042011);
            PhoneWindowManager.access$2602(PhoneWindowManager.this, Toast.makeText(localContextThemeWrapper, String.format(str1, new Object[] { str2 }), 1));
            PhoneWindowManager.this.alertToast.setGravity(17, 0, 0);
            PhoneWindowManager.this.alertToast.setShowForAllUsers();
            PhoneWindowManager.this.alertToast.show();
          }
        }
        , 0L);
      paramBoolean = false;
      if (this.mMobileKeyboardEnabled != paramBoolean)
      {
        this.mMobileKeyboardEnabled = paramBoolean;
        updateOrientationListenerLp();
        this.mHandler.sendEmptyMessage(61);
      }
      if (paramLong == 0L)
        continue;
      localContentResolver = this.mContext.getContentResolver();
      if (!paramBoolean)
        break label187;
    }
    while (true)
    {
      Settings.System.putIntForUser(localContentResolver, "mobile_keyboard", i, 0);
      this.mHandler.postDelayed(new Runnable()
      {
        public void run()
        {
          synchronized (PhoneWindowManager.this.mLock)
          {
            InputManager.getInstance().coverEventFinished();
            return;
          }
        }
      }
      , 100L);
      return;
      paramBoolean = false;
      break;
      label160: if (this.mSPWM.isSideSyncPresentationRunning())
      {
        paramBoolean = false;
        break label77;
      }
      if (isUserSetupComplete())
        break label77;
      paramBoolean = false;
      break label77;
      label187: i = 0;
    }
  }

  public void notifyDisplayAdded(int paramInt)
  {
    if (this.mHandler != null)
    {
      Message localMessage = this.mHandler.obtainMessage(100, paramInt, 0);
      this.mHandler.sendMessage(localMessage);
    }
  }

  public void notifyFoldingSwitchStateChanged(long paramLong, int paramInt)
  {
    Slog.v("WindowManager", "notifyFoldingSwitchStateChanged foldingState=" + paramInt);
    while (true)
    {
      int i;
      synchronized (this.mFoldingAndWrapAroundLock)
      {
        if (this.mFoldingState == paramInt)
          return;
        this.mFoldingState = paramInt;
        i = this.mLastWrapAroundMode;
        this.mActivityManagerInternal.handleSContextChanged(43, paramInt);
        if (paramInt != 2)
          break label172;
        if (i == 1)
        {
          this.mPowerManager.setMultipleScreenStateOverride(1, 3);
          SystemProperties.set("sys.dualscreen.folding_state", Integer.toString(paramInt));
          ??? = new Intent("com.samsung.android.dualscreen.action.FOLDING_STATE_CHANGED");
          ((Intent)???).putExtra("com.samsung.android.dualscreen.extra.FOLDING_STATE", paramInt);
          ((Intent)???).addFlags(1073741824);
          this.mContext.sendStickyBroadcastAsUser((Intent)???, UserHandle.ALL);
          return;
        }
      }
      if (i != 2)
        continue;
      this.mPowerManager.setMultipleScreenStateOverride(2, 3);
      continue;
      label172: this.mWindowManagerFuncs.removeAppBackWindow();
      this.mPowerManager.setMultipleScreenStateOverride(3, 2);
      this.mActivityManagerInternal.onMultipleScreenStateChanged(3, 2);
    }
  }

  public void notifyLidSwitchChanged(long paramLong, boolean paramBoolean)
  {
    if (paramBoolean);
    for (int i = 1; i == this.mLidState; i = 0)
      return;
    this.mLidState = i;
    applyLidSwitchState();
    if (SamsungPolicyProperties.FolderTypeFeature(this.mContext) != 0)
    {
      this.mSPWM.notifyLidSwitchChangedForFolder(paramLong, paramBoolean);
      if ((!this.mLidControlsSleep) && (!paramBoolean))
      {
        int m = Settings.System.getInt(this.mContext.getContentResolver(), "premium_watch_switch_onoff", 0);
        if (Settings.System.getInt(this.mContext.getContentResolver(), "sub_lcd_auto_lock", 0) != 1)
          break label436;
        i = 1;
        label100: int k = 0;
        boolean bool2 = SystemProperties.get("service.camera.running", "0").equals("1");
        boolean bool1 = false;
        if (this.mTelephonyManager == null)
          this.mTelephonyManager = ((TelephonyManager)this.mContext.getSystemService("phone"));
        if (this.mTelephonyManager != null)
          bool1 = this.mTelephonyManager.isOffhook();
        if (i == 0)
          break label442;
        i = k;
        if (m == 1)
        {
          boolean bool3 = this.mLockPatternUtils.isLockScreenDisabled(0);
          i = k;
          if (!bool2)
          {
            i = k;
            if (!bool1)
            {
              i = k;
              if (isUserSetupComplete())
              {
                i = k;
                if (!bool3)
                {
                  this.mSPWM.launchPremiumWatch(false);
                  i = 1;
                }
              }
            }
          }
        }
        Log.v("WindowManager", "isCameraRunning: " + bool2 + ", isOffhook: " + bool1);
        if ((!this.mKeyguardDelegate.isShowing()) && ((isKeyguardSecure()) || ((i == 0) && (!bool1) && (!bool2))))
          lockNow(null);
      }
      label313: i = 0;
      if (SamsungPolicyProperties.FolderTypeFeature(this.mContext) == 2)
        if (!paramBoolean)
          break label455;
    }
    label436: int j;
    label442: label455: for (i = 0; ; j = 500)
    {
      this.mHandler.postDelayed(new Runnable()
      {
        public void run()
        {
          PhoneWindowManager.this.updateOrientationListenerLp();
          PhoneWindowManager.this.updateRotation(true);
        }
      }
      , i);
      if ((SamsungPolicyProperties.FolderTypeFeature(this.mContext) == 1) && (!paramBoolean) && (!FactoryTest.isRunningFactoryApp()) && (!FactoryTest.isFactoryBinary()) && (!SamsungPolicyProperties.isDomesticOtaStart()) && (!SamsungPolicyProperties.isBlockKey(this.mContext)))
        launchHomeFromHotKey();
      if ((!paramBoolean) && (this.mLidControlsSleep))
        break;
      wakeUp(SystemClock.uptimeMillis(), this.mAllowTheaterModeWakeFromLidSwitch, 5);
      this.mPowerManager.userActivity(SystemClock.uptimeMillis(), false);
      return;
      j = 0;
      break label100;
      Slog.v("WindowManager", "Auto lock is not enabled.");
      break label313;
    }
  }

  public void notifyPenSwitchChanged(long paramLong, boolean paramBoolean)
  {
    this.mSPWM.notifyPenSwitchChanged(paramLong, paramBoolean);
  }

  public void notifyWrapAroundModeChanged(int paramInt)
  {
    int i = 2;
    synchronized (this.mFoldingAndWrapAroundLock)
    {
      if (this.mLastWrapAroundMode == paramInt)
        return;
      if ((paramInt != 1) && (paramInt != 2))
        return;
    }
    this.mLastWrapAroundMode = paramInt;
    int j = this.mFoldingState;
    monitorexit;
    this.mActivityManagerInternal.handleSContextChanged(49, paramInt);
    if (j == 2)
    {
      this.mWindowManagerFuncs.removeAppBackWindow();
      if (paramInt == 1)
        i = 1;
      this.mPowerManager.setMultipleScreenStateOverride(i, 3);
      updateRotation(true);
    }
  }

  public void onFixedScreenModeChanged(int paramInt)
  {
    this.mFixedTaskId = paramInt;
  }

  public void onLockTaskModeChanged(int paramInt)
  {
    if (this.mLockTaskModeState != paramInt)
      this.mLockTaskModeState = paramInt;
  }

  public void onMultipleScreenStateChanged(int paramInt1, int paramInt2)
  {
    Slog.v("WindowManager", "onMultipleScreenStateChanged state=" + paramInt1 + ",reason=" + paramInt2);
    this.mActivityManagerInternal.onMultipleScreenStateChanged(paramInt1, paramInt2);
  }

  public boolean performHapticFeedbackLw(WindowManagerPolicy.WindowState paramWindowState, int paramInt, boolean paramBoolean)
  {
    if (this.mSPWM != null)
      return this.mSPWM.performHapticFeedbackLw(paramWindowState, paramInt, paramBoolean);
    if (!this.mVibrator.hasVibrator())
      return false;
    if (Settings.System.getIntForUser(this.mContext.getContentResolver(), "haptic_feedback_enabled", 0, -2) == 0);
    for (int i = 1; (i != 0) && (!paramBoolean); i = 0)
      return false;
    long[] arrayOfLong;
    switch (paramInt)
    {
    default:
      return false;
    case 0:
      arrayOfLong = this.mLongPressVibePattern;
      if (paramWindowState == null)
        break;
      paramInt = paramWindowState.getOwningUid();
      paramWindowState = paramWindowState.getOwningPackage();
      label170: if (arrayOfLong.length == 1)
        this.mVibrator.vibrate(paramInt, paramWindowState, arrayOfLong[0], VIBRATION_ATTRIBUTES);
    case 1:
    case 3:
    case 4:
    case 5:
    case 10000:
    case 10001:
    case 6:
    }
    while (true)
    {
      return true;
      arrayOfLong = this.mVirtualKeyVibePattern;
      break;
      arrayOfLong = this.mKeyboardTapVibePattern;
      break;
      arrayOfLong = this.mClockTickVibePattern;
      break;
      arrayOfLong = this.mCalendarDateVibePattern;
      break;
      arrayOfLong = this.mSafeModeDisabledVibePattern;
      break;
      arrayOfLong = this.mSafeModeEnabledVibePattern;
      break;
      arrayOfLong = this.mContextClickVibePattern;
      break;
      paramInt = Process.myUid();
      paramWindowState = this.mContext.getOpPackageName();
      break label170;
      this.mVibrator.vibrate(paramInt, paramWindowState, arrayOfLong, -1, VIBRATION_ATTRIBUTES);
    }
  }

  public int prepareAddWindowLw(WindowManagerPolicy.WindowState paramWindowState, WindowManager.LayoutParams paramLayoutParams)
  {
    int i = -7;
    switch (paramLayoutParams.type)
    {
    default:
    case 2000:
    case 2019:
    case 2014:
    case 2017:
    case 2024:
    case 2033:
    case 2029:
    case 2256:
    case 2270:
    case 2011:
    case 2225:
    }
    while (true)
    {
      if ((!this.mScreenOnFully) && (!this.mForceUserActivityTimeoutWin.contains(paramWindowState)) && ((paramLayoutParams.flags & 0x200000) != 0))
        this.mForceUserActivityTimeoutWin.add(paramWindowState);
      i = this.mMultiPhoneWindowManager.prepareAddWindowLw(paramWindowState, paramLayoutParams);
      if (i == 0)
        break;
      do
      {
        do
        {
          do
          {
            do
            {
              do
              {
                return i;
                this.mContext.enforceCallingOrSelfPermission("android.permission.STATUS_BAR_SERVICE", "PhoneWindowManager");
              }
              while ((this.mStatusBar != null) && (this.mStatusBar.isAlive()));
              this.mStatusBar = paramWindowState;
              this.mStatusBarController.setWindow(paramWindowState);
              break;
              this.mContext.enforceCallingOrSelfPermission("android.permission.STATUS_BAR_SERVICE", "PhoneWindowManager");
            }
            while ((this.mNavigationBar != null) && (this.mNavigationBar.isAlive()));
            this.mNavigationBar = paramWindowState;
            this.mNavigationBarController.setWindow(paramWindowState);
            if (!DEBUG_LAYOUT)
              break;
            Slog.i("WindowManager", "NAVIGATION BAR: " + this.mNavigationBar);
            break;
            this.mContext.enforceCallingOrSelfPermission("android.permission.STATUS_BAR_SERVICE", "PhoneWindowManager");
            break;
          }
          while (this.mKeyguardScrim != null);
          this.mKeyguardScrim = paramWindowState;
          break;
          this.mBottomKeyPanelWindow = paramWindowState;
          break;
          this.mContext.enforceCallingOrSelfPermission("android.permission.STATUS_BAR_SERVICE", "PhoneWindowManager");
        }
        while ((this.mCarModeBar != null) && (this.mCarModeBar.isAlive()));
        this.mCarModeBar = paramWindowState;
        break;
        this.mInputMethod = paramWindowState;
        break;
        this.mContext.enforceCallingOrSelfPermission("android.permission.STATUS_BAR_SERVICE", "PhoneWindowManager");
      }
      while ((this.mNightClock != null) && (this.mNightClock.isAlive()));
      this.mNightClock = paramWindowState;
    }
    return this.mCocktailPhoneWindowManager.prepareAddWindowLw(paramWindowState, paramLayoutParams);
  }

  void readLidState()
  {
    this.mLidState = this.mWindowManagerFuncs.getLidState();
  }

  public void removeAdaptiveEvent(String paramString)
  {
    if (this.mKeyguardDelegate != null)
      this.mKeyguardDelegate.removeAdaptiveEvent(paramString);
  }

  public void removeBackWindow(View paramView)
  {
    if (paramView != null)
      ((WindowManager)this.mContext.getSystemService("window")).removeView(paramView);
  }

  public void removeBackWindowForAppLaunching()
  {
    if (this.mWindowManagerFuncs.removeAppBackWindow())
      this.mActivityManagerInternal.onMultipleScreenStateChanged(3, 5);
  }

  public void removeStartingWindow(IBinder paramIBinder, View paramView)
  {
    if (paramView != null)
      ((WindowManager)this.mContext.getSystemService("window")).removeView(paramView);
  }

  public void removeWindowLw(WindowManagerPolicy.WindowState paramWindowState)
  {
    if (this.mStatusBar == paramWindowState)
    {
      this.mStatusBar = null;
      this.mStatusBarController.setWindow(null);
      this.mKeyguardDelegate.showScrim();
    }
    while (true)
    {
      if (this.mNavigationBar == paramWindowState)
      {
        this.mNavigationBar = null;
        this.mNavigationBarController.setWindow(null);
      }
      this.mMultiPhoneWindowManager.removeWindowLw(paramWindowState);
      this.mCocktailPhoneWindowManager.removeWindowLw(paramWindowState);
      if (this.mBottomKeyPanelWindow == paramWindowState)
        this.mBottomKeyPanelWindow = null;
      if (this.mCarModeBar == paramWindowState)
        this.mCarModeBar = null;
      if (this.mInputMethod == paramWindowState)
        this.mInputMethod = null;
      if (this.mNightClock == paramWindowState)
        this.mNightClock = null;
      return;
      if (this.mKeyguardScrim != paramWindowState)
        continue;
      Log.v("WindowManager", "Removing keyguard scrim");
      this.mKeyguardScrim = null;
    }
  }

  public void requestDefaultKeyguardRotationAnimation(boolean paramBoolean)
  {
    this.mIsDefaultKeyguardRotationAnmationAlwaysUsed = paramBoolean;
  }

  public void requestMetaKeyEvent(ComponentName paramComponentName, boolean paramBoolean)
  {
    this.mSystemKeyManager.requestMetaKeyEvent(paramComponentName, paramBoolean);
  }

  public boolean requestSystemKeyEvent(int paramInt, ComponentName paramComponentName, boolean paramBoolean)
  {
    return this.mSystemKeyManager.requestSystemKeyEvent(paramInt, paramComponentName, paramBoolean);
  }

  public void requestTransientBars()
  {
    requestTransientBars(0);
  }

  public void requestTransientBars(int paramInt)
  {
    if (this.mStatusBar != null)
      requestTransientBars(this.mStatusBar);
  }

  public void requestTraversalForCoverView()
  {
    if ((this.mCoverState != null) && (!this.mCoverState.switchState) && (this.mLastCoverAppCovered))
      this.mHandler.sendEmptyMessage(60);
  }

  public int rotationForOrientationLw(int paramInt1, int paramInt2, int paramInt3)
  {
    StringBuilder localStringBuilder = new StringBuilder().append("rotationForOrientationLw(orient=").append(paramInt1).append(", last=").append(paramInt2).append("); user=").append(this.mUserRotation).append(" ");
    if (this.mUserRotationMode == 1);
    for (??? = "USER_ROTATION_LOCKED"; ; ??? = "")
    {
      Slog.v("WindowManager", (String)??? + " sensorRotation=" + this.mOrientationListener.getProposedRotation() + " mLidState=" + this.mLidState + " mDockMode=" + this.mDockMode + " mHdmiPlugged=" + this.mHdmiPlugged + " mMobileKeyboardEnabled=" + this.mMobileKeyboardEnabled + " displayId=" + paramInt3);
      if (!this.mForceDefaultOrientation)
        break;
      return this.mPanelOrientation;
    }
    int j;
    int k;
    int m;
    int i;
    while (true)
    {
      int n;
      int i1;
      synchronized (this.mLock)
      {
        j = this.mLandscapeRotation;
        n = this.mSeascapeRotation;
        k = this.mPortraitRotation;
        i1 = this.mUpsideDownRotation;
        m = this.mOrientationListener.getProposedRotation();
        if ((m >= -1) && (m <= 3))
          break label1138;
        Slog.wtf("WindowManager", "rotationForOrientationLw (sensorRotation= " + m + ")");
        break label1138;
        if ((isSupportAndAttachedSViewCover()) && (!this.mCoverState.switchState) && (this.mCoverCloseRotation >= 0))
        {
          Slog.i("WindowManager", "prefer rotation is set by cover");
          i = this.mCoverCloseRotation;
          break;
          if (i < 0)
            break label1128;
          return i;
        }
      }
      if ((SamsungPolicyProperties.FolderTypeFeature(this.mContext) == 2) && (this.mLidState == 1))
      {
        i = rotationForSecondLcdOrientationLw(paramInt1, paramInt2);
        break;
      }
      if ((this.mLidState == 1) && (this.mLidOpenRotation >= 0))
      {
        i = this.mLidOpenRotation;
        break;
      }
      if ((this.mDockMode == 2) && ((this.mCarDockEnablesAccelerometer) || (this.mCarDockRotation >= 0)))
      {
        if (this.mCarDockEnablesAccelerometer)
          break label1223;
        i = this.mCarDockRotation;
        break label1223;
      }
      if ((!this.mMouseDockedFlag) && ((this.mDockMode == 1) || (this.mDockMode == 3) || (this.mDockMode == 4)) && ((this.mDeskDockEnablesAccelerometer) || (this.mDeskDockRotation >= 0)))
      {
        if (this.mDeskDockEnablesAccelerometer)
          break label1226;
        i = this.mDeskDockRotation;
        break label1226;
      }
      if ((this.mDockMode == 104) && ((this.mMirrorLinkDockEnablesAccelerometer) || (this.mMirrorlinkDockRotation >= 0)))
      {
        if (this.mMirrorLinkDockEnablesAccelerometer)
          break label1229;
        i = this.mMirrorlinkDockRotation;
        break label1229;
      }
      if (((this.mHdmiPlugged) || (this.mWifiDisplayConnected)) && (this.mUserRotationMode != 1) && (this.mMouseDockedFlag) && (!this.mPresentationFlag) && (!SystemProperties.get("service.camera.running", "0").equals("1")))
      {
        i = j;
        Slog.i("WindowManager", "Smart Dock Orientation Enabled :  " + this.mMouseDockedFlag + "preferredRotation is ::" + i);
        break;
      }
      if ((this.mWifiDisplayConnected) && (this.mWifiDisplayCustomRotation > -1))
      {
        i = this.mWifiDisplayCustomRotation;
        break;
      }
      if ((this.mHdmiPlugged) && (this.mDockMode == 0) && (this.mUndockedHdmiRotation >= 0))
      {
        i = this.mUndockedHdmiRotation;
        break;
      }
      if (!this.mDemoRotationLock)
        break label1232;
      i = this.mDemoRotation;
      break;
      label702: if (!this.mSupportAutoRotation)
      {
        i = -1;
        break;
      }
      if (this.mSPWM.isUvsOrientationRequested())
      {
        if ((paramInt1 == 0) || (paramInt1 == 8))
          break label1244;
        if (paramInt1 != 6)
          break label1251;
        break label1244;
        label744: i = this.mSPWM.getUvsOrientation();
        paramInt1 = i;
        break;
      }
      if (!this.mMobileKeyboardEnabled)
      {
        if ((this.mUserRotationMode != 0) || ((paramInt1 != 2) && (paramInt1 != -1) && (paramInt1 != 11) && (paramInt1 != 12) && (paramInt1 != 13)))
          break label1275;
        label804: if (this.mAllowAllRotations < 0)
        {
          if (!this.mContext.getResources().getBoolean(17956925))
            break label1304;
          m = 1;
          label830: this.mAllowAllRotations = m;
        }
        if ((i != 2) || (this.mAllowAllRotations == 1) || (paramInt1 == 10))
          break label1301;
        if (paramInt1 != 13)
          break label1310;
        break label1301;
      }
      label865: if ((this.mMobileKeyboardEnabled) && (paramInt1 != 5))
      {
        i = k;
        break;
      }
      if ((this.mUserRotationMode == 1) && (paramInt1 != 5))
      {
        i = this.mUserRotation;
        break;
      }
      if (this.mCocktail180RotationEnabled != 1)
        break label1328;
      switch (paramInt1)
      {
      case 1:
      case 9:
        if (isAnyPortrait(i, paramInt3))
          break label1153;
        if (!isAnyPortrait(paramInt2, paramInt3))
          break label1322;
        i = paramInt2;
        break label1153;
        if (isAnyPortrait(i, paramInt3))
        {
          monitorexit;
          return i;
        }
        monitorexit;
        return k;
        if (isLandscapeOrSeascape(i, paramInt3))
        {
          monitorexit;
          return i;
        }
        monitorexit;
        return j;
        if (isAnyPortrait(i, paramInt3))
        {
          monitorexit;
          return i;
        }
        monitorexit;
        return i1;
        if (isLandscapeOrSeascape(i, paramInt3))
        {
          monitorexit;
          return i;
        }
        monitorexit;
        return n;
        if (isLandscapeOrSeascape(i, paramInt3))
        {
          monitorexit;
          return i;
        }
        if (isLandscapeOrSeascape(paramInt2, paramInt3))
        {
          monitorexit;
          return paramInt2;
        }
        monitorexit;
        return j;
        if (isAnyPortrait(i, paramInt3))
        {
          monitorexit;
          return i;
        }
        if (isAnyPortrait(paramInt2, paramInt3))
        {
          monitorexit;
          return paramInt2;
        }
        monitorexit;
        return k;
        label1128: paramInt1 = this.mPanelOrientation;
        monitorexit;
        return paramInt1;
        label1138: i = m;
        if (m >= 0)
          continue;
        i = paramInt2;
      }
    }
    while (true)
    {
      label1153: switch (paramInt1)
      {
      case 2:
      case 3:
      case 4:
      case 5:
      case 10:
      case 1:
      case 0:
      case 9:
      case 8:
      case 6:
      case 11:
      case 7:
      case 12:
      }
      break;
      label1223: continue;
      label1226: continue;
      label1229: continue;
      label1232: if (paramInt1 != 14)
        break label702;
      i = paramInt2;
      continue;
      label1244: i = j;
      continue;
      label1251: if ((paramInt1 != 1) && (paramInt1 != 9) && (paramInt1 != 7))
        break label744;
      i = k;
      continue;
      label1275: if ((paramInt1 == 4) || (paramInt1 == 10) || (paramInt1 == 6))
        break label804;
      if (paramInt1 != 7)
        break label865;
      break label804;
      label1301: continue;
      label1304: m = 0;
      break label830;
      label1310: i = paramInt2;
      continue;
      i = -1;
      continue;
      label1322: i = -1;
      continue;
      label1328: i = -1;
    }
  }

  public boolean rotationHasCompatibleMetricsLw(int paramInt1, int paramInt2, int paramInt3)
  {
    switch (paramInt1)
    {
    case 2:
    case 3:
    case 4:
    case 5:
    default:
      return true;
    case 1:
    case 7:
    case 9:
      return isAnyPortrait(paramInt2, paramInt3);
    case 0:
    case 6:
    case 8:
    }
    return isLandscapeOrSeascape(paramInt2, paramInt3);
  }

  public void screenTurnedOff()
  {
    screenTurnedOff(0);
  }

  public void screenTurnedOff(int paramInt)
  {
    if (DEBUG_WAKEUP)
      Log.i("WindowManager", "Screen turned off...");
    updateScreenOffSleepToken(true);
    synchronized (this.mLock)
    {
      this.mScreenOnEarly = false;
      this.mScreenOnFully = false;
      this.mKeyguardDrawComplete = false;
      this.mWindowManagerDrawComplete = false;
      this.mScreenOnListener = null;
      updateOrientationListenerLp();
      if (this.mKeyguardDelegate != null)
        this.mKeyguardDelegate.onScreenTurnedOff();
      this.mForceUserActivityTimeoutWin.clear();
      if (SamsungPolicyProperties.isEasyOneHandRunning())
        this.mSPWM.stopEasyOneHandervice(0);
      this.mSPWM.startAodService(3);
      return;
    }
  }

  public void screenTurnedOn()
  {
    synchronized (this.mLock)
    {
      if (this.mKeyguardDelegate != null)
        this.mKeyguardDelegate.onScreenTurnedOn();
      if (this.mSPWM.startAodService(2))
        this.mHandler.sendEmptyMessage(60);
      return;
    }
  }

  public void screenTurningOn(WindowManagerPolicy.ScreenOnListener paramScreenOnListener)
  {
    screenTurningOn(paramScreenOnListener, 0);
  }

  public void screenTurningOn(WindowManagerPolicy.ScreenOnListener paramScreenOnListener, int paramInt)
  {
    if (DEBUG_WAKEUP)
      Log.i("WindowManager", "Screen turning on...");
    if (this.mScreenOnEarly)
      return;
    updateScreenOffSleepToken(false);
    while (true)
    {
      synchronized (this.mLock)
      {
        this.mScreenOnEarly = true;
        this.mScreenOnFully = false;
        this.mKeyguardDrawComplete = false;
        this.mWindowManagerDrawComplete = false;
        this.mScreenOnListener = paramScreenOnListener;
        if (this.mKeyguardDelegate != null)
        {
          this.mHandler.removeMessages(6);
          this.mHandler.sendEmptyMessageDelayed(6, 3000L);
          this.mKeyguardDelegate.onScreenTurningOn(this.mKeyguardDrawnCallback);
          return;
        }
      }
      if (DEBUG_WAKEUP)
        Log.d("WindowManager", "null mKeyguardDelegate: setting mKeyguardDrawComplete.");
      finishKeyguardDrawn(mScreenTurnDisplayId);
    }
  }

  public int selectAnimationLw(WindowManagerPolicy.WindowState paramWindowState, int paramInt)
  {
    Object localObject = paramWindowState.getMultiWindowStyleLw();
    if ((localObject != null) && (((MultiWindowStyle)localObject).getType() != 0) && ((paramInt == 1) || (paramInt == 2)))
      return -1;
    localObject = this.mStatusBar;
    WindowManagerPolicy.WindowState localWindowState = this.mNavigationBar;
    int i;
    if (paramWindowState == localObject)
    {
      if ((paramWindowState.getAttrs().samsungFlags & 0x10000000) != 0)
      {
        i = 1;
        if ((!isForceHideBySViewCover()) || (this.mHideSViewCover != 0))
          break label102;
      }
      label102: for (int j = 1; ; j = 0)
      {
        if ((i == 0) && (j == 0))
          break label108;
        return -1;
        i = 0;
        break;
      }
      label108: if ((paramWindowState.getAttrs().privateFlags & 0x400) != 0)
        i = 1;
      while ((paramInt == 2) || (paramInt == 4))
      {
        if (i != 0)
        {
          return -1;
          i = 0;
          continue;
        }
        return 17432615;
      }
      if ((paramInt == 1) || (paramInt == 3))
      {
        if (i != 0)
          return -1;
        return 17432614;
      }
    }
    else if (paramWindowState == localWindowState)
    {
      if (paramWindowState.getAttrs().windowAnimations != 0)
        return 0;
      if (this.mNavigationBarOnBottom)
      {
        if ((paramInt == 2) || (paramInt == 4))
          return 17432609;
        if ((paramInt == 1) || (paramInt == 3))
          return 17432608;
      }
      else
      {
        if ((paramInt == 2) || (paramInt == 4))
          return 17432613;
        if ((paramInt == 1) || (paramInt == 3))
          return 17432612;
      }
    }
    if (CocktailBarFeatures.isSystemBarType(this.mContext))
    {
      i = this.mCocktailPhoneWindowManager.selectAnimationLw(paramWindowState, paramInt);
      if (i != 0)
        return i;
    }
    if (paramWindowState == this.mCarModeBar)
      if (this.mCarModeBarOnBottom)
      {
        if ((paramInt == 2) || (paramInt == 4))
          return 17432609;
        if ((paramInt == 1) || (paramInt == 3))
          return 17432608;
      }
      else
      {
        if ((paramInt == 2) || (paramInt == 4))
          return 17432611;
        if ((paramInt == 1) || (paramInt == 3))
          return 17432610;
      }
    if (paramInt == 5)
    {
      if (paramWindowState.hasAppShownWindows())
      {
        if (paramWindowState.isCustomStartingAnimationLw())
          return 17432594;
        return 17432593;
      }
    }
    else if ((paramWindowState.getAttrs().type == 2023) && (this.mDreamingLockscreen) && (paramInt == 1))
      return -1;
    return 0;
  }

  public void selectRotationAnimationLw(int[] paramArrayOfInt)
  {
    if ((this.mTopFullscreenOpaqueWindowState != null) && (this.mTopIsFullscreen))
      switch (this.mTopFullscreenOpaqueWindowState.getAttrs().rotationAnimation)
      {
      default:
        paramArrayOfInt[1] = 0;
        paramArrayOfInt[0] = 0;
        if (this.mAllowAllRotations != 0)
          break;
      case 1:
      case 2:
      }
    for (int i = 1; ; i = 0)
    {
      if ((i != 0) && ((isStatusBarKeyguard()) || (this.mIsDefaultKeyguardRotationAnmationAlwaysUsed)))
      {
        paramArrayOfInt[0] = 17432693;
        paramArrayOfInt[1] = 17432691;
      }
      if (isForceHideBySViewCover())
      {
        paramArrayOfInt[0] = 17432692;
        paramArrayOfInt[1] = 17432691;
      }
      if (isCocktailRotationAnimationNeeded())
      {
        paramArrayOfInt[0] = 17432692;
        paramArrayOfInt[1] = 17432691;
      }
      if (isForceHideByNightClock())
      {
        paramArrayOfInt[0] = 17432692;
        paramArrayOfInt[1] = 17432691;
      }
      return;
      paramArrayOfInt[0] = 17432693;
      paramArrayOfInt[1] = 17432691;
      break;
      paramArrayOfInt[0] = 17432692;
      paramArrayOfInt[1] = 17432691;
      break;
      paramArrayOfInt[1] = 0;
      paramArrayOfInt[0] = 0;
      break;
    }
  }

  void sendCloseSystemWindows()
  {
    PhoneWindow.sendCloseSystemWindows(this.mContext, null);
  }

  void sendCloseSystemWindows(String paramString)
  {
    PhoneWindow.sendCloseSystemWindows(this.mContext, paramString);
  }

  protected void sendMediaButtonEvent(int paramInt)
  {
    long l = SystemClock.uptimeMillis();
    new Intent("android.intent.action.MEDIA_BUTTON", null);
    KeyEvent localKeyEvent = new KeyEvent(l, l, 0, paramInt, 0);
    dispatchMediaKeyWithWakeLockToAudioService(localKeyEvent);
    dispatchMediaKeyWithWakeLockToAudioService(KeyEvent.changeAction(localKeyEvent, 1));
  }

  public void setAdaptiveEvent(String paramString, RemoteViews paramRemoteViews1, RemoteViews paramRemoteViews2)
  {
    if (this.mKeyguardDelegate != null)
      this.mKeyguardDelegate.setAdaptiveEvent(paramString, paramRemoteViews1, paramRemoteViews2);
  }

  void setAttachedWindowFrames(WindowManagerPolicy.WindowState paramWindowState1, int paramInt1, int paramInt2, WindowManagerPolicy.WindowState paramWindowState2, boolean paramBoolean, Rect paramRect1, Rect paramRect2, Rect paramRect3, Rect paramRect4, Rect paramRect5)
  {
    if ((paramWindowState1.getSurfaceLayer() > this.mDockLayer) && (paramWindowState2.getSurfaceLayer() < this.mDockLayer))
    {
      paramInt2 = this.mDockLeft;
      paramRect5.left = paramInt2;
      paramRect4.left = paramInt2;
      paramRect3.left = paramInt2;
      paramRect2.left = paramInt2;
      paramInt2 = this.mDockTop;
      paramRect5.top = paramInt2;
      paramRect4.top = paramInt2;
      paramRect3.top = paramInt2;
      paramRect2.top = paramInt2;
      paramInt2 = this.mDockRight;
      paramRect5.right = paramInt2;
      paramRect4.right = paramInt2;
      paramRect3.right = paramInt2;
      paramRect2.right = paramInt2;
      paramInt2 = this.mDockBottom;
      paramRect5.bottom = paramInt2;
      paramRect4.bottom = paramInt2;
      paramRect3.bottom = paramInt2;
      paramRect2.bottom = paramInt2;
      if ((paramInt1 & 0x100) != 0)
        break label474;
      paramRect1.set(paramWindowState2.getFrameLw());
    }
    label186: label192: label468: label474: 
    do
    {
      return;
      if (paramInt2 != 16)
        if ((0x40000000 & paramInt1) != 0)
        {
          paramWindowState1 = paramWindowState2.getContentFrameLw();
          paramRect4.set(paramWindowState1);
          if (!paramBoolean)
            break label468;
        }
      for (paramWindowState1 = paramWindowState2.getDisplayFrameLw(); ; paramWindowState1 = paramRect4)
      {
        paramRect2.set(paramWindowState1);
        if (paramBoolean)
          paramRect4 = paramWindowState2.getOverscanFrameLw();
        paramRect3.set(paramRect4);
        paramRect5.set(paramWindowState2.getVisibleFrameLw());
        break;
        paramWindowState1 = paramWindowState2.getOverscanFrameLw();
        break label186;
        paramRect4.set(paramWindowState2.getContentFrameLw());
        if (paramWindowState2.isVoiceInteraction())
        {
          if (paramRect4.left < this.mVoiceContentLeft)
            paramRect4.left = this.mVoiceContentLeft;
          if (paramRect4.top < this.mVoiceContentTop)
            paramRect4.top = this.mVoiceContentTop;
          if (paramRect4.right > this.mVoiceContentRight)
            paramRect4.right = this.mVoiceContentRight;
          if (paramRect4.bottom <= this.mVoiceContentBottom)
            break label192;
          paramRect4.bottom = this.mVoiceContentBottom;
          break label192;
        }
        if (paramWindowState2.getSurfaceLayer() >= this.mDockLayer)
          break label192;
        if (paramRect4.left < this.mContentLeft)
          paramRect4.left = this.mContentLeft;
        if (paramRect4.top < this.mContentTop)
          paramRect4.top = this.mContentTop;
        if (paramRect4.right > this.mContentRight)
          paramRect4.right = this.mContentRight;
        if (paramRect4.bottom <= this.mContentBottom)
          break label192;
        paramRect4.bottom = this.mContentBottom;
        break label192;
      }
    }
    while ((paramWindowState2.getAttrs().flags & 0x200) != 0);
    paramRect1.set(paramRect2);
  }

  public void setBendedPendingIntent(PendingIntent paramPendingIntent, Intent paramIntent)
  {
    if (this.mKeyguardDelegate != null)
      this.mKeyguardDelegate.setBendedPendingIntent(paramPendingIntent, paramIntent);
  }

  public void setBendedPendingIntentInSecure(PendingIntent paramPendingIntent, Intent paramIntent)
  {
    if (this.mKeyguardDelegate != null)
      this.mKeyguardDelegate.setBendedPendingIntentInSecure(paramPendingIntent, paramIntent);
  }

  public boolean setCoverSwitchStateLocked(CoverState paramCoverState)
  {
    if ((!this.mIsSupportFlipCover) && (!this.mIsSupportSViewCover))
      return false;
    if ((paramCoverState != null) && (paramCoverState.switchState != this.mCoverState.switchState))
    {
      this.mCoverState.copyFrom(paramCoverState);
      Slog.i("WindowManager", "setCoverSwitchState : " + this.mCoverState.switchState);
      if ((paramCoverState.switchState == true) && (isStatusBarSViewCover()) && (!isStatusBarKeyguard()))
        this.mStatusBar.getAttrs().screenOrientation = -1;
      if ((!this.mCoverState.switchState) && (this.mCocktailPhoneWindowManager.isEdgeScreenWaked()))
        this.mCocktailPhoneWindowManager.requestEdgeScreenWakeup(false, -1, 1);
    }
    if ((isSupportAndAttachedSViewCover()) && (this.mCoverState.switchState) && (this.mHideSViewCoverWindowState != null))
      this.mHideSViewCoverWindowState.disableHideSViewCoverOnce(true);
    return true;
  }

  public void setCurrentOrientationLw(int paramInt)
  {
    synchronized (this.mLock)
    {
      if (paramInt != this.mCurrentAppOrientation)
      {
        this.mCurrentAppOrientation = paramInt;
        updateOrientationListenerLp();
        WindowOrientationListener.setCurrentAppOrientation(this.mCurrentAppOrientation);
      }
      return;
    }
  }

  public void setCurrentUserLw(int paramInt)
  {
    this.mCurrentUserId = paramInt;
    if (this.mKeyguardDelegate != null)
      this.mKeyguardDelegate.setCurrentUser(paramInt);
    if (this.mStatusBarService != null);
    try
    {
      this.mStatusBarService.setCurrentUser(paramInt);
      label37: setLastInputMethodWindowLw(null, null);
      return;
    }
    catch (RemoteException localRemoteException)
    {
      break label37;
    }
  }

  public void setDisplayOverscan(Display paramDisplay, int paramInt1, int paramInt2, int paramInt3, int paramInt4)
  {
    if (paramDisplay.getDisplayId() == 0)
    {
      this.mOverscanLeft = paramInt1;
      this.mOverscanTop = paramInt2;
      this.mOverscanRight = paramInt3;
      this.mOverscanBottom = paramInt4;
    }
  }

  public void setForceHideStatusBar(boolean paramBoolean)
  {
    this.mForceHideStatusBarForCocktail = paramBoolean;
  }

  void setHdmiPlugged(boolean paramBoolean)
  {
    if (this.mHdmiPlugged != paramBoolean)
    {
      this.mHdmiPlugged = paramBoolean;
      updateRotation(true, true);
      Intent localIntent = new Intent("android.intent.action.HDMI_PLUGGED");
      localIntent.addFlags(67108864);
      localIntent.putExtra("state", paramBoolean);
      this.mContext.sendStickyBroadcastAsUser(localIntent, UserHandle.ALL);
    }
  }

  public void setInitialDisplaySize(Display paramDisplay, int paramInt1, int paramInt2, int paramInt3)
  {
    if ((this.mContext == null) || (paramDisplay.getDisplayId() != 0))
      return;
    this.mDisplay = paramDisplay;
    this.mPanelOrientation = (SystemProperties.getInt("persist.panel.orientation", 0) / 90);
    paramDisplay = this.mContext.getResources();
    int j;
    int i;
    if (paramInt1 > paramInt2)
    {
      j = paramInt2;
      i = paramInt1;
      this.mLandscapeRotation = 0;
      this.mSeascapeRotation = 2;
      if (paramDisplay.getBoolean(17956926))
      {
        this.mPortraitRotation = 1;
        this.mUpsideDownRotation = 3;
        this.mStatusBarHeight = paramDisplay.getDimensionPixelSize(17104919);
        int[] arrayOfInt1 = this.mNavigationBarHeightForRotation;
        int k = this.mPortraitRotation;
        int[] arrayOfInt2 = this.mNavigationBarHeightForRotation;
        int m = this.mUpsideDownRotation;
        int n = paramDisplay.getDimensionPixelSize(17104920);
        arrayOfInt2[m] = n;
        arrayOfInt1[k] = n;
        arrayOfInt1 = this.mNavigationBarHeightForRotation;
        k = this.mLandscapeRotation;
        arrayOfInt2 = this.mNavigationBarHeightForRotation;
        m = this.mSeascapeRotation;
        n = paramDisplay.getDimensionPixelSize(17104921);
        arrayOfInt2[m] = n;
        arrayOfInt1[k] = n;
        arrayOfInt1 = this.mNavigationBarWidthForRotation;
        k = this.mPortraitRotation;
        arrayOfInt2 = this.mNavigationBarWidthForRotation;
        m = this.mUpsideDownRotation;
        int[] arrayOfInt3 = this.mNavigationBarWidthForRotation;
        n = this.mLandscapeRotation;
        int[] arrayOfInt4 = this.mNavigationBarWidthForRotation;
        int i1 = this.mSeascapeRotation;
        int i2 = paramDisplay.getDimensionPixelSize(17104922);
        arrayOfInt4[i1] = i2;
        arrayOfInt3[n] = i2;
        arrayOfInt2[m] = i2;
        arrayOfInt1[k] = i2;
        j = j * 160 / paramInt3;
        paramInt3 = i * 160 / paramInt3;
        if ((paramInt1 == paramInt2) || (j >= 600))
          break label548;
        bool = true;
        label311: this.mNavigationBarCanMove = bool;
        this.mHasNavigationBar = paramDisplay.getBoolean(17956969);
        if (!"portrait".equals(SystemProperties.get("persist.demo.hdmirotation")))
          break label554;
        this.mDemoHdmiRotation = this.mPortraitRotation;
        label351: this.mDemoHdmiRotationLock = SystemProperties.getBoolean("persist.demo.hdmirotationlock", false);
        if (!"portrait".equals(SystemProperties.get("persist.demo.remoterotation")))
          break label565;
        this.mDemoRotation = this.mPortraitRotation;
        label385: this.mDemoRotationLock = SystemProperties.getBoolean("persist.demo.rotationlock", false);
        if ((paramInt3 < 960) || (j < 720) || (!paramDisplay.getBoolean(17956997)) || ("true".equals(SystemProperties.get("config.override_forced_orient"))))
          break label576;
      }
    }
    label548: label554: label565: label576: for (boolean bool = true; ; bool = false)
    {
      this.mForceDefaultOrientation = bool;
      this.mMultiPhoneWindowManager.setInitialDisplaySize();
      this.mMobileKeyboardHeight = paramDisplay.getDimensionPixelSize(17105556);
      this.mCarModeSize = paramDisplay.getDimensionPixelSize(17105555);
      this.mCocktailPhoneWindowManager.initCocktailBarSize();
      return;
      this.mPortraitRotation = 3;
      this.mUpsideDownRotation = 1;
      break;
      j = paramInt1;
      i = paramInt2;
      this.mPortraitRotation = 0;
      this.mUpsideDownRotation = 2;
      if (paramDisplay.getBoolean(17956926))
      {
        this.mLandscapeRotation = 3;
        this.mSeascapeRotation = 1;
        break;
      }
      this.mLandscapeRotation = 1;
      this.mSeascapeRotation = 3;
      break;
      bool = false;
      break label311;
      this.mDemoHdmiRotation = this.mLandscapeRotation;
      break label351;
      this.mDemoRotation = this.mLandscapeRotation;
      break label385;
    }
  }

  public void setLastInputMethodWindowLw(WindowManagerPolicy.WindowState paramWindowState1, WindowManagerPolicy.WindowState paramWindowState2)
  {
    this.mLastInputMethodWindow = paramWindowState1;
    this.mLastInputMethodTargetWindow = paramWindowState2;
  }

  public void setMultiWindowTrayOpenState(boolean paramBoolean)
  {
    this.mMultiPhoneWindowManager.setMultiWindowTrayOpenState(paramBoolean);
  }

  public void setRotationLw(int paramInt)
  {
    this.mOrientationListener.setCurrentRotation(paramInt);
    this.mSPWM.setRotationLw(paramInt);
    if (this.mCocktail180RotationEnabled == 1)
      setBottomSoftkeyRotation(paramInt);
  }

  public void setSafeMode(boolean paramBoolean)
  {
    this.mSafeMode = paramBoolean;
    int i;
    if (paramBoolean)
    {
      i = 10001;
      performHapticFeedbackLw(null, i, true);
      i = Process.myPid();
      if (!this.mSafeMode)
        break label55;
    }
    label55: for (String str = "Safe Boot"; ; str = "Normal Boot")
    {
      AuditLog.log(5, 2, true, i, "Boot Mode", str);
      return;
      i = 10000;
      break;
    }
  }

  public void setTouchExplorationEnabled(boolean paramBoolean)
  {
    this.mTouchExplorationEnabled = paramBoolean;
    if (CocktailBarFeatures.isSystemBarType(this.mContext))
      this.mCocktailPhoneWindowManager.updateGripState(paramBoolean, 0);
  }

  public void setUserRotationMode(int paramInt1, int paramInt2)
  {
    ContentResolver localContentResolver = this.mContext.getContentResolver();
    if ((SamsungPolicyProperties.FolderTypeFeature(this.mContext) == 2) && (this.mLidState == 1))
    {
      if (paramInt1 == 1)
      {
        Slog.i("WindowManager", "setUserRotationMode 2");
        Settings.System.putIntForUser(localContentResolver, "user_rotation", paramInt2, -2);
        Settings.System.putIntForUser(localContentResolver, "accelerometer_rotation_second", 0, -2);
        return;
      }
      Settings.System.putIntForUser(localContentResolver, "accelerometer_rotation_second", 1, -2);
      return;
    }
    if (paramInt1 == 1)
    {
      Settings.System.putIntForUser(localContentResolver, "user_rotation", paramInt2, -2);
      Settings.System.putIntForUser(localContentResolver, "accelerometer_rotation", 0, -2);
      return;
    }
    Settings.System.putIntForUser(localContentResolver, "accelerometer_rotation", 1, -2);
  }

  public void showBootMessage(CharSequence paramCharSequence, boolean paramBoolean)
  {
    if (this.mContext.getResources().getBoolean(17957079));
    for (paramBoolean = false; ; paramBoolean = true)
    {
      this.mCustomDialog = paramBoolean;
      this.mHandler.post(new Runnable(paramCharSequence)
      {
        public void run()
        {
          int j = 17040257;
          Object localObject;
          Context localContext;
          if ((PhoneWindowManager.this.mBootMsgDialog == null) && (PhoneWindowManager.this.mCustomBootMsgDialog == null))
          {
            if (!PhoneWindowManager.this.mContext.getPackageManager().hasSystemFeature("android.hardware.type.watch"))
              break label140;
            i = 16975035;
            if (!PhoneWindowManager.this.mCustomDialog)
              break label175;
            localObject = PhoneWindowManager.this;
            localContext = PhoneWindowManager.this.mContext;
            if (!PhoneWindowManager.this.mContext.getPackageManager().isUpgrade())
              break label169;
          }
          label140: label169: for (int i = j; ; i = 17040258)
          {
            ((PhoneWindowManager)localObject).mCustomBootMsgDialog = new CustomBootMsgDialog(localContext, i);
            PhoneWindowManager.this.mCustomBootMsgDialog.show();
            if (!PhoneWindowManager.this.mCustomDialog)
              break label366;
            PhoneWindowManager.this.mCustomBootMsgDialog.setProgress(this.val$msg.toString());
            return;
            if (PhoneWindowManager.this.mContext.getPackageManager().hasSystemFeature("android.hardware.type.television"))
            {
              i = 16975002;
              break;
            }
            i = 0;
            break;
          }
          label175: PhoneWindowManager.this.mBootMsgDialog = new ProgressDialog(PhoneWindowManager.this.mContext, i)
          {
            public boolean dispatchGenericMotionEvent(MotionEvent paramMotionEvent)
            {
              return true;
            }

            public boolean dispatchKeyEvent(KeyEvent paramKeyEvent)
            {
              return true;
            }

            public boolean dispatchKeyShortcutEvent(KeyEvent paramKeyEvent)
            {
              return true;
            }

            public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent paramAccessibilityEvent)
            {
              return true;
            }

            public boolean dispatchTouchEvent(MotionEvent paramMotionEvent)
            {
              return true;
            }

            public boolean dispatchTrackballEvent(MotionEvent paramMotionEvent)
            {
              return true;
            }
          };
          if (PhoneWindowManager.this.mContext.getPackageManager().isUpgrade())
            PhoneWindowManager.this.mBootMsgDialog.setTitle(17040257);
          while (true)
          {
            PhoneWindowManager.this.mBootMsgDialog.setProgressStyle(0);
            PhoneWindowManager.this.mBootMsgDialog.setIndeterminate(true);
            PhoneWindowManager.this.mBootMsgDialog.getWindow().setType(2021);
            PhoneWindowManager.this.mBootMsgDialog.getWindow().addFlags(258);
            PhoneWindowManager.this.mBootMsgDialog.getWindow().setDimAmount(1.0F);
            localObject = PhoneWindowManager.this.mBootMsgDialog.getWindow().getAttributes();
            ((WindowManager.LayoutParams)localObject).screenOrientation = 5;
            PhoneWindowManager.this.mBootMsgDialog.getWindow().setAttributes((WindowManager.LayoutParams)localObject);
            PhoneWindowManager.this.mBootMsgDialog.setCancelable(false);
            PhoneWindowManager.this.mBootMsgDialog.show();
            break;
            PhoneWindowManager.this.mBootMsgDialog.setTitle(17040258);
          }
          label366: PhoneWindowManager.this.mBootMsgDialog.setMessage(this.val$msg);
        }
      });
      return;
    }
  }

  public void showGlobalActions()
  {
    this.mHandler.removeMessages(10);
    this.mHandler.sendEmptyMessage(10);
  }

  void showGlobalActionsInternal()
  {
    sendCloseSystemWindows("globalactions");
    if (this.mGlobalActions == null)
      this.mGlobalActions = new GlobalActions(this.mContext, this.mWindowManagerFuncs);
    boolean bool = keyguardOn();
    this.mGlobalActions.showDialog(bool, isDeviceProvisioned());
    if (bool)
      this.mPowerManager.userActivity(SystemClock.uptimeMillis(), false);
  }

  public void showRecentApps()
  {
    this.mHandler.removeMessages(9);
    this.mHandler.sendEmptyMessage(9);
  }

  public void showStatusBarByNotification()
  {
    Slog.d("WindowManager", "showStatusBarByNotification() mOpenByNotification=" + this.mOpenByNotification);
    if (this.mOpenByNotification)
      this.mHandler.postDelayed(this.mShowStatusBarByNotification, 1000L);
  }

  void startDockOrHome(boolean paramBoolean1, boolean paramBoolean2)
  {
    int j = 0;
    int i = j;
    if (getPersonaManagerLocked() != null)
    {
      i = j;
      if (this.mPersonaManager.getPersonaIds() != null)
      {
        i = j;
        if (this.mPersonaManager.getPersonaIds().length > 0)
          i = this.mPersonaManager.getPersonaIds()[0];
      }
    }
    if (isKnoxKeyguardShownForKioskMode(i))
    {
      Log.d("WindowManager", "startDockOrHome() > isKnoxKeyguardShownForKioskMode() : true");
      return;
    }
    Slog.i("WindowManager", "startDockOrHome");
    if (paramBoolean2)
      awakenDreams();
    Intent localIntent1 = new Intent();
    localIntent1.setAction("com.samsung.android.action.START_DOCK_OR_HOME");
    this.mContext.sendBroadcastAsUser(localIntent1, UserHandle.CURRENT, "com.samsung.android.permisson.START_DOCK_OR_HOME");
    localIntent1 = createHomeDockIntent();
    if ((localIntent1 != null) && (this.mSPWM.isDockHomeEnabled(localIntent1)))
    {
      if (paramBoolean1);
      try
      {
        localIntent1.putExtra("android.intent.extra.FROM_HOME_KEY", paramBoolean1);
        localIntent1.setFromHomeKey(true);
        startActivityAsUser(localIntent1, UserHandle.CURRENT);
        return;
      }
      catch (ActivityNotFoundException localActivityNotFoundException1)
      {
      }
    }
    Object localObject;
    if (CocktailBarFeatures.isSystemBarType(this.mContext))
    {
      localObject = CocktailBarManager.getInstance(this.mContext);
      if (localObject != null)
        ((CocktailBarManager)localObject).switchDefaultCocktail();
    }
    if (paramBoolean1)
    {
      localObject = new Intent(this.mHomeIntent);
      ((Intent)localObject).setFromHomeKey(true);
      ((Intent)localObject).putExtra("android.intent.extra.FROM_HOME_KEY", paramBoolean1);
    }
    while (true)
    {
      try
      {
        startActivityAsUser((Intent)localObject, UserHandle.CURRENT);
        return;
      }
      catch (ActivityNotFoundException localActivityNotFoundException2)
      {
        return;
      }
      Intent localIntent2 = this.mHomeIntent;
    }
  }

  public void startKeyguardExitAnimation(long paramLong1, long paramLong2)
  {
    if (this.mKeyguardDelegate != null)
    {
      if (DEBUG_KEYGUARD)
        Slog.d("WindowManager", "PWM.startKeyguardExitAnimation");
      this.mKeyguardDelegate.startKeyguardExitAnimation(paramLong1, paramLong2);
    }
  }

  public void startedGoingToSleep(int paramInt)
  {
    if (DEBUG_WAKEUP)
      Log.i("WindowManager", "Started going to sleep... (why=" + paramInt + ")");
    if (this.mKeyguardDelegate != null)
      this.mKeyguardDelegate.onStartedGoingToSleep(paramInt);
  }

  public void startedWakingUp()
  {
    startedWakingUp(0);
  }

  public void startedWakingUp(int paramInt)
  {
    EventLog.writeEvent(70000, 1);
    if (DEBUG_WAKEUP)
      Log.i("WindowManager", "Started waking up...");
    disableQbCharger();
    while (true)
    {
      boolean bool;
      synchronized (this.mLock)
      {
        this.mAwake = true;
        if (this.mKeyguardDelegate == null)
          continue;
        this.mHandler.removeMessages(6);
        this.mHandler.sendEmptyMessageDelayed(6, 3000L);
        updateWakeGestureListenerLp();
        updateOrientationListenerLp();
        updateLockScreenTimeout();
        this.mSPWM.handleWakingUp();
        if ((getEDM() == null) || (getEDM().getEnterpriseSharedDevicePolicy() == null))
          continue;
        bool = getEDM().getEnterpriseSharedDevicePolicy().isSharedDeviceEnabled();
        ??? = new Intent();
        ((Intent)???).setComponent(new ComponentName("com.sec.enterprise.knox.shareddevice.keyguard", "com.sec.enterprise.knox.shareddevice.keyguard.SharedDeviceKeyguardService"));
        ((Intent)???).putExtra("SharedDeviceKeyguardEventFlag", 16);
        if (getPersonaManagerLocked() == null)
          continue;
        if ((bool) && (!this.mPersonaManager.getKeyguardShowState(UserHandle.myUserId())))
        {
          Log.d("WindowManager", "Shared devices screen ON completed show state false");
          this.mContext.startService((Intent)???);
          if (this.mKeyguardDelegate == null)
            continue;
          this.mKeyguardDelegate.onStartedWakingUp();
          this.mWindowManagerFuncs.cancelDragForcelyWhenScreenTurnOff(false);
          return;
        }
      }
      if (!bool)
        continue;
      Log.d("WindowManager", "Shared devices screen ON completed show state true");
      ((Intent)???).putExtra("isScreenOn", 1);
      this.mContext.startService((Intent)???);
    }
  }

  public int subWindowTypeToLayerLw(int paramInt)
  {
    switch (paramInt)
    {
    default:
      Log.e("WindowManager", "Unknown sub-window type: " + paramInt);
      return 0;
    case 1000:
    case 1003:
    case 1006:
    case 1007:
    case 1100:
      return 1;
    case 1001:
      return -2;
    case 1004:
      return -1;
    case 1002:
    case 2260:
      return 2;
    case 1005:
    }
    return 3;
  }

  public void systemBooted()
  {
    if ((getEDM() != null) && (getEDM().getEnterpriseSharedDevicePolicy() != null) && (getEDM().getEnterpriseSharedDevicePolicy().isSharedDeviceEnabled()))
      Settings.System.putIntForUser(this.mContext.getContentResolver(), "isKeyguardLaunched", 0, 0);
    int i = 0;
    while (true)
    {
      synchronized (this.mLock)
      {
        if (this.mKeyguardDelegate != null)
        {
          i = 1;
          if (i != 0)
          {
            this.mKeyguardDelegate.bindService(this.mContext);
            this.mKeyguardDelegate.onBootCompleted();
          }
        }
      }
      synchronized (this.mLock)
      {
        this.mSystemBooted = true;
        this.mMultiPhoneWindowManager.mSystemBooted = true;
        startedWakingUp();
        screenTurningOn(null);
        screenTurnedOn();
        this.mSPWM.systemBooted();
        if ((getEDM() != null) && (getEDM().getEnterpriseSharedDevicePolicy() != null))
        {
          boolean bool = getEDM().getEnterpriseSharedDevicePolicy().isSharedDeviceEnabled();
          Log.d("WindowManager", "Shared devices on boot completed" + bool);
          ??? = new Intent();
          ((Intent)???).setComponent(new ComponentName("com.sec.enterprise.knox.shareddevice.keyguard", "com.sec.enterprise.knox.shareddevice.keyguard.SharedDeviceKeyguardService"));
          ((Intent)???).putExtra("SharedDeviceKeyguardEventFlag", 16);
          ((Intent)???).putExtra("isDeviceBooted", true);
          if (bool)
          {
            this.mContext.startService((Intent)???);
            this.mHandler.sendEmptyMessageDelayed(105, 200L);
            if (this.mKeyguardDelegate.isShowing())
              this.mHandler.post(new Runnable()
              {
                public void run()
                {
                  Log.d("WindowManager", "Shared devices on boot completed dismiss keyguard");
                  PhoneWindowManager.this.mKeyguardDelegate.keyguardDone(false, false);
                }
              });
          }
        }
        return;
        this.mDeferBindKeyguard = true;
        continue;
        localObject2 = finally;
        throw localObject2;
      }
    }
  }

  public void systemReady()
  {
    this.mKeyguardDelegate = new KeyguardServiceDelegate(this.mContext);
    this.mKeyguardDelegate.onSystemReady();
    this.mSPWM.systemReady(this.mKeyguardDelegate);
    readCameraLensCoverState();
    this.mMultiPhoneWindowManager.onSystemReady(this.mKeyguardDelegate);
    updateUiMode();
    synchronized (this.mLock)
    {
      updateOrientationListenerLp();
      this.mSystemReady = true;
      this.mMultiPhoneWindowManager.mSystemReady = true;
      this.mHandler.post(new Runnable()
      {
        public void run()
        {
          PhoneWindowManager.this.updateSettings();
          PhoneWindowManager.this.mSPWM.updateSettings();
          PhoneWindowManager.this.mMultiPhoneWindowManager.updateSettings();
        }
      });
      boolean bool = this.mDeferBindKeyguard;
      if (bool)
        this.mDeferBindKeyguard = false;
      if (bool)
      {
        this.mKeyguardDelegate.bindService(this.mContext);
        this.mKeyguardDelegate.onBootCompleted();
      }
      this.mSystemGestures.systemReady();
      return;
    }
  }

  public boolean toggleMultiWindowTray()
  {
    return this.mMultiPhoneWindowManager.toggleMultiWindowTray(this.mTopFullscreenOpaqueWindowState);
  }

  public void updateAdaptiveEvent(String paramString, RemoteViews paramRemoteViews1, RemoteViews paramRemoteViews2)
  {
    if (this.mKeyguardDelegate != null)
      this.mKeyguardDelegate.updateAdaptiveEvent(paramString, paramRemoteViews1, paramRemoteViews2);
  }

  public boolean updateCocktailBarVisibility(boolean paramBoolean)
  {
    if (CocktailBarFeatures.isSystemBarType(this.mContext))
      return this.mCocktailPhoneWindowManager.updateCocktailBarVisibility(paramBoolean);
    return false;
  }

  public void updateCocktailLayout(int paramInt1, int paramInt2, int paramInt3, int paramInt4)
  {
    this.mSystemLeft = paramInt1;
    this.mSystemTop = paramInt3;
    this.mSystemRight -= paramInt2;
    this.mSystemBottom -= paramInt4;
    int i = this.mUnrestrictedScreenLeft + paramInt1;
    this.mUnrestrictedScreenLeft = i;
    this.mRestrictedOverscanScreenLeft = i;
    i = this.mUnrestrictedScreenTop + paramInt3;
    this.mUnrestrictedScreenTop = i;
    this.mRestrictedOverscanScreenTop = i;
    paramInt1 = this.mUnrestrictedScreenWidth - paramInt1 - paramInt2;
    this.mUnrestrictedScreenWidth = paramInt1;
    this.mRestrictedOverscanScreenWidth = paramInt1;
    paramInt1 = this.mUnrestrictedScreenHeight - paramInt3 - paramInt4;
    this.mUnrestrictedScreenHeight = paramInt1;
    this.mRestrictedOverscanScreenHeight = paramInt1;
    this.mRestrictedScreenLeft = this.mUnrestrictedScreenLeft;
    this.mRestrictedScreenTop = this.mUnrestrictedScreenTop;
    this.mRestrictedScreenWidth = this.mUnrestrictedScreenWidth;
    this.mRestrictedScreenHeight = this.mUnrestrictedScreenHeight;
    paramInt1 = this.mUnrestrictedScreenLeft;
    this.mCurLeft = paramInt1;
    this.mStableFullscreenLeft = paramInt1;
    this.mStableLeft = paramInt1;
    this.mContentLeft = paramInt1;
    this.mDockLeft = paramInt1;
    paramInt1 = this.mUnrestrictedScreenTop;
    this.mCurTop = paramInt1;
    this.mStableFullscreenTop = paramInt1;
    this.mStableTop = paramInt1;
    this.mContentTop = paramInt1;
    this.mDockTop = paramInt1;
    paramInt1 = this.mCurRight - paramInt2;
    this.mCurRight = paramInt1;
    this.mStableFullscreenRight = paramInt1;
    this.mStableRight = paramInt1;
    this.mContentRight = paramInt1;
    this.mDockRight = paramInt1;
    paramInt1 = this.mCurBottom - paramInt4;
    this.mCurBottom = paramInt1;
    this.mStableFullscreenBottom = paramInt1;
    this.mStableBottom = paramInt1;
    this.mContentBottom = paramInt1;
    this.mDockBottom = paramInt1;
  }

  public void updateCursorWindowInputRect(Region paramRegion)
  {
    this.cursorWindowTouchableRegion.set(paramRegion);
  }

  void updateOrientationListenerLp()
  {
    if (!this.mOrientationListener.canDetectOrientation());
    int i;
    do
    {
      return;
      int j = 1;
      i = j;
      if (!this.mScreenOnEarly)
        continue;
      i = j;
      if (!this.mAwake)
        continue;
      i = j;
      if (!this.mKeyguardDrawComplete)
        continue;
      i = j;
      if (!this.mWindowManagerDrawComplete)
        continue;
      i = j;
      if (!needSensorRunningLp())
        continue;
      j = 0;
      i = j;
      if (this.mOrientationSensorEnabled)
        continue;
      this.mOrientationListener.enable();
      this.mOrientationSensorEnabled = true;
      i = j;
    }
    while ((i == 0) || (!this.mOrientationSensorEnabled));
    this.mOrientationListener.disable();
    this.mOrientationSensorEnabled = false;
  }

  public void updateRotation(boolean paramBoolean)
  {
    try
    {
      this.mWindowManager.updateRotation(paramBoolean, false);
      return;
    }
    catch (RemoteException localRemoteException)
    {
    }
  }

  void updateRotation(boolean paramBoolean1, boolean paramBoolean2)
  {
    try
    {
      this.mWindowManager.updateRotation(paramBoolean1, paramBoolean2);
      return;
    }
    catch (RemoteException localRemoteException)
    {
    }
  }

  public void updateSViewCoverLayout(boolean paramBoolean)
  {
    if (paramBoolean)
    {
      this.mSystemLeft = this.mSViewCoverSystemLeft;
      this.mSystemTop = this.mSViewCoverSystemTop;
      this.mSystemRight = this.mSViewCoverSystemRight;
      this.mSystemBottom = this.mSViewCoverSystemBottom;
      i = this.mSViewCoverUnrestrictedScreenLeft;
      this.mUnrestrictedScreenLeft = i;
      this.mRestrictedScreenLeft = i;
      i = this.mSViewCoverUnrestrictedScreenTop;
      this.mUnrestrictedScreenTop = i;
      this.mRestrictedScreenTop = i;
      i = this.mSViewCoverUnrestrictedScreenWidth;
      this.mUnrestrictedScreenWidth = i;
      this.mRestrictedScreenWidth = i;
      i = this.mSViewCoverUnrestrictedScreenHeight;
      this.mUnrestrictedScreenHeight = i;
      this.mRestrictedScreenHeight = i;
      this.mStableFullscreenLeft = this.mSViewCoverStableFullscreenLeft;
      this.mStableFullscreenTop = this.mSViewCoverStableFullscreenTop;
      this.mStableFullscreenRight = this.mSViewCoverStableFullscreenRight;
      this.mStableFullscreenBottom = this.mSViewCoverStableFullscreenBottom;
      this.mStableLeft = this.mSViewCoverStableLeft;
      this.mStableTop = this.mSViewCoverStableTop;
      this.mStableRight = this.mSViewCoverStableRight;
      this.mStableBottom = this.mSViewCoverStableBottom;
      i = this.mSViewCoverDockLeft;
      this.mCurLeft = i;
      this.mContentLeft = i;
      this.mDockLeft = i;
      i = this.mSViewCoverDockTop;
      this.mCurTop = i;
      this.mContentTop = i;
      this.mDockTop = i;
      i = this.mSViewCoverDockRight;
      this.mCurRight = i;
      this.mContentRight = i;
      this.mDockRight = i;
      i = this.mSViewCoverDockBottom;
      this.mCurBottom = i;
      this.mContentBottom = i;
      this.mDockBottom = i;
      return;
    }
    this.mSystemLeft = this.mOriginalSystemLeft;
    this.mSystemTop = this.mOriginalSystemTop;
    this.mSystemRight = this.mOriginalSystemRight;
    this.mSystemBottom = this.mOriginalSystemBottom;
    int i = this.mOriginalUnrestrictedScreenLeft;
    this.mUnrestrictedScreenLeft = i;
    this.mRestrictedScreenLeft = i;
    i = this.mOriginalUnrestrictedScreenTop;
    this.mUnrestrictedScreenTop = i;
    this.mRestrictedScreenTop = i;
    i = this.mOriginalUnrestrictedScreenWidth;
    this.mUnrestrictedScreenWidth = i;
    this.mRestrictedScreenWidth = i;
    i = this.mOriginalUnrestrictedScreenHeight;
    this.mUnrestrictedScreenHeight = i;
    this.mRestrictedScreenHeight = i;
    this.mStableFullscreenLeft = this.mOriginalStableFullscreenLeft;
    this.mStableFullscreenTop = this.mOriginalStableFullscreenTop;
    this.mStableFullscreenRight = this.mOriginalStableFullscreenRight;
    this.mStableFullscreenBottom = this.mOriginalStableFullscreenBottom;
    this.mStableLeft = this.mOriginalStableLeft;
    this.mStableTop = this.mOriginalStableTop;
    this.mStableRight = this.mOriginalStableRight;
    this.mStableBottom = this.mOriginalStableBottom;
    i = this.mOriginalDockLeft;
    this.mCurLeft = i;
    this.mContentLeft = i;
    this.mDockLeft = i;
    i = this.mOriginalDockTop;
    this.mCurTop = i;
    this.mContentTop = i;
    this.mDockTop = i;
    i = this.mOriginalDockRight;
    this.mCurRight = i;
    this.mContentRight = i;
    this.mDockRight = i;
    i = this.mOriginalDockBottom;
    this.mCurBottom = i;
    this.mContentBottom = i;
    this.mDockBottom = i;
  }

  public void updateSettings()
  {
    ContentResolver localContentResolver = this.mContext.getContentResolver();
    this.mVolBtnMusicControls = Settings.System.getInt(localContentResolver, "volbtn_music_controls", 0);
    this.mVolBtnTimeout = Settings.System.getInt(localContentResolver, "volbtn_timeout", 400);
    this.mVolBtnVolUp = Settings.System.getInt(localContentResolver, "volbtn_vol_up", 87);
    this.mVolBtnVolDown = Settings.System.getInt(localContentResolver, "volbtn_vol_down", 88);
    int j = 0;
    while (true)
    {
      int i;
      label184: int k;
      synchronized (this.mLock)
      {
        this.mEndcallBehavior = Settings.System.getIntForUser(localContentResolver, "end_button_behavior", 2, -2);
        this.mIncallPowerBehavior = Settings.Secure.getIntForUser(localContentResolver, "incall_power_button_behavior", 1, 0);
        if (Settings.Secure.getIntForUser(localContentResolver, "wake_gesture_enabled", 0, -2) != 0)
        {
          bool = true;
          if (this.mWakeGestureEnabledSetting == bool)
            continue;
          this.mWakeGestureEnabledSetting = bool;
          updateWakeGestureListenerLp();
          i = Settings.System.getIntForUser(localContentResolver, "user_rotation", 0, -2);
          if (this.mUserRotation == i)
            continue;
          this.mUserRotation = i;
          j = 1;
          if (Settings.System.getIntForUser(localContentResolver, "accelerometer_rotation", 0, -2) == 0)
            break label827;
          i = 0;
          if (this.mUserRotationMode == i)
            continue;
          this.mUserRotationMode = i;
          j = 1;
          updateOrientationListenerLp();
          i = j;
          if (SamsungPolicyProperties.FolderTypeFeature(this.mContext) != 2)
            continue;
          if (Settings.System.getIntForUser(localContentResolver, "accelerometer_rotation_second", 0, -2) == 0)
            break label832;
          k = 0;
          label232: i = j;
          if (this.mSecondLcdUserRotationMode == k)
            continue;
          this.mSecondLcdUserRotationMode = k;
          i = 1;
          updateOrientationListenerLp();
          ??? = Settings.Secure.getStringForUser(localContentResolver, "assistant", -2);
          if (??? == null)
            continue;
          ??? = ((String)???).split("/");
          if ((???[0] == null) || (!???[0].equals("com.samsung.voiceserviceplatform")))
            break label837;
          this.mIsOWCSetting = true;
          label305: if (!this.mSystemReady)
            continue;
          j = Settings.System.getIntForUser(localContentResolver, "pointer_location", 0, -2);
          if (this.mPointerLocationMode == j)
            continue;
          this.mPointerLocationMode = j;
          ??? = this.mHandler;
          if (j == 0)
            break label853;
          j = 1;
          label349: ((Handler)???).sendEmptyMessage(j);
          if (!TwToolBoxService.TOOLBOX_SUPPORT)
            continue;
          k = Settings.System.getIntForUser(localContentResolver, "toolbox_onoff", 0, -2);
          if (this.mToolBoxMode == k)
            continue;
          this.mToolBoxMode = k;
          ??? = this.mHandler;
          if (k == 0)
            break label858;
          j = 18;
          label400: ((Handler)???).sendEmptyMessage(j);
          String str = Settings.System.getStringForUser(localContentResolver, "toolbox_apps", -2);
          ??? = str;
          if (str != null)
            continue;
          ??? = "";
          if (this.mToolBoxPackageList.equals(???))
            continue;
          this.mToolBoxPackageList = ((String)???);
          if ((k == 0) || (this.mTwToolBoxFloatingViewer == null))
            continue;
          this.mTwToolBoxFloatingViewer.reset();
          j = Settings.System.getIntForUser(localContentResolver, "any_screen_enabled", 0, -2);
          if (j != 1)
            break label864;
          bool = true;
          label489: SamsungPolicyProperties.setEasyOneHandEnabled(bool);
          if (this.mEasyOneHandEnabled == j)
            continue;
          this.mEasyOneHandEnabled = j;
          if ((!this.mSystemReady) || (!this.mSystemBooted) || (this.mEasyOneHandEnabled != 0))
            continue;
          this.mHandler.sendEmptyMessage(57);
          if (Settings.System.getIntForUser(localContentResolver, "any_screen_running", 0, -2) != 1)
            break label870;
          bool = true;
          label556: SamsungPolicyProperties.setEasyOneHandRunning(bool);
          if (!SamsungPolicyProperties.hasSideKeyPanelFeature(this.mContext))
            continue;
          j = Settings.System.getIntForUser(localContentResolver, "sidesoftkey_switch", 0, -2);
          if (this.mSideKeyPanelEnabled == j)
            continue;
          this.mSideKeyPanelEnabled = j;
          ??? = this.mHandler;
          if (j == 0)
            break label876;
          j = 50;
          label609: ((Handler)???).sendEmptyMessage(j);
          j = Settings.System.getIntForUser(this.mContext.getContentResolver(), "cocktail_bar_enabled_180_rotate", 0, -2);
          if ((j < 0) || (this.mCocktail180RotationEnabled == j))
            continue;
          this.mCocktail180RotationEnabled = j;
          if (this.mCocktail180RotationEnabled != 1)
            continue;
          this.mHandler.sendEmptyMessage(52);
          this.mCurrentDisplayRotation = -1;
          this.mAllowAllRotations = this.mCocktail180RotationEnabled;
          if (Settings.System.getIntForUser(localContentResolver, "mobile_keyboard", 0, 0) == 0)
            break label882;
          j = 1;
          break label907;
          label699: notifyCoverSwitchStateChanged(0L, bool);
          this.mLockScreenTimeout = Settings.System.getIntForUser(localContentResolver, "screen_off_timeout", 0, -2);
          ??? = Settings.Secure.getStringForUser(localContentResolver, "default_input_method", -2);
          if ((??? == null) || (((String)???).length() <= 0))
            break label893;
          bool = true;
          if (this.mHasSoftInput == bool)
            continue;
          this.mHasSoftInput = bool;
          i = 1;
          if (this.mImmersiveModeConfirmation == null)
            continue;
          this.mImmersiveModeConfirmation.loadSetting(this.mCurrentUserId);
        }
      }
      label827: label832: label837: label853: label858: label864: label870: label876: label882: label893: 
      do
        synchronized (this.mWindowManagerFuncs.getWindowManagerLock())
        {
          PolicyControl.reloadFromSetting(this.mContext);
          if (i != 0)
            updateRotation(true);
          return;
          bool = false;
          break;
          i = 1;
          break label184;
          k = 1;
          break label232;
          this.mIsOWCSetting = false;
          break label305;
          localObject2 = finally;
          monitorexit;
          throw localObject2;
          j = 2;
          break label349;
          j = 19;
          break label400;
          bool = false;
          break label489;
          bool = false;
          break label556;
          j = 51;
          break label609;
          j = 0;
          continue;
          bool = false;
          break label699;
          bool = false;
        }
      while (j != 0);
      label907: boolean bool = true;
    }
  }

  public void updateTopActivity(ComponentName paramComponentName)
  {
    this.mSystemKeyManager.updateTopActivity(paramComponentName);
  }

  public void updateTspInputMethodPolicy(WindowManagerPolicy.WindowState paramWindowState)
  {
    if ((paramWindowState != null) && (paramWindowState == this.mFocusedWindow))
      this.mTspStateManager.updateInputMethodPolicy();
    do
      return;
    while (this.mFocusedWindow == null);
    this.mTspStateManager.updateWindowPolicy(this.mFocusedWindow);
  }

  public void updateTspViewPolicy(int paramInt)
  {
    this.mTspStateManager.updateViewPolicy(paramInt);
  }

  void updateUiMode()
  {
    if (this.mUiModeManager == null)
      this.mUiModeManager = IUiModeManager.Stub.asInterface(ServiceManager.getService("uimode"));
    try
    {
      this.mUiMode = this.mUiModeManager.getCurrentModeType();
      return;
    }
    catch (RemoteException localRemoteException)
    {
    }
  }

  public void userActivity()
  {
    synchronized (this.mScreenLockTimeout)
    {
      if (this.mLockScreenTimerActive)
      {
        this.mHandler.removeCallbacks(this.mScreenLockTimeout);
        this.mHandler.postDelayed(this.mScreenLockTimeout, this.mLockScreenTimeout);
      }
      return;
    }
  }

  public boolean validateRotationAnimationLw(int paramInt1, int paramInt2, boolean paramBoolean)
  {
    switch (paramInt1)
    {
    default:
    case 17432692:
    case 17432693:
    }
    int[] arrayOfInt;
    do
    {
      return true;
      if (paramBoolean)
        return false;
      arrayOfInt = new int[2];
      selectRotationAnimationLw(arrayOfInt);
    }
    while ((paramInt1 == arrayOfInt[0]) && (paramInt2 == arrayOfInt[1]));
    return false;
  }

  public int windowTypeToLayerLw(int paramInt)
  {
    int j = 29;
    if ((this.mSPWM.isHMTSupportAndConnected()) && (paramInt == 97))
      i = 27;
    while (true)
    {
      return i;
      if ((paramInt >= 1) && (paramInt <= 99))
        return 2;
      i = j;
      switch (paramInt)
      {
      case 2021:
      case 2080:
      case 2081:
      default:
        i = this.mMultiPhoneWindowManager.windowTypeToLayerLw(paramInt);
        if (i > 0)
          return i;
      case 2030:
        return 2;
      case 2013:
        return 2;
      case 2002:
        return 3;
      case 2001:
      case 2033:
        return 4;
      case 2031:
        return 5;
      case 2022:
        return 6;
      case 2008:
        return 7;
      case 2005:
        return 19;
      case 2007:
        return 9;
      case 2023:
        return 10;
      case 2003:
        return 11;
      case 2011:
        return 12;
      case 2012:
      case 2280:
        return 13;
      case 2029:
      case 2098:
        return 14;
      case 2017:
        return 15;
      case 2000:
        return 16;
      case 2014:
      case 2099:
      case 2226:
        return 17;
      case 2009:
        return 18;
      case 2006:
      case 2096:
        return 19;
      case 2020:
        return 20;
      case 2019:
      case 2261:
        return 21;
      case 2024:
        return 22;
      case 2010:
      case 2097:
      case 2262:
        return 23;
      case 2027:
        return 24;
      case 2026:
        return 25;
      case 2016:
      case 2225:
        return 26;
      case 2032:
        return 27;
      case 2015:
        return 28;
      case 2018:
        return 30;
      case 2230:
        return 35;
      case 2100:
        return 3;
      case 2101:
        return 5;
      case 2102:
        return 16;
      case 2103:
        return 17;
      case 2254:
        return 21;
      case 2253:
        return 20;
      case 2252:
        return 17;
      case 2255:
        if ((SamsungPolicyProperties.getEasyOneHandPkgVersion(null) > 2) || (this.mCocktailPhoneWindowManager != null))
          return 28;
        return 23;
      case 2250:
        i = j;
        if (SamsungPolicyProperties.getEasyOneHandPkgVersion(null) > 2)
          continue;
        i = j;
        if (this.mCocktailPhoneWindowManager != null)
          continue;
        return 24;
      case 2256:
        return 25;
      case 2082:
        return 26;
      case 2270:
      case 2271:
        return 19;
      }
    }
    int i = this.mCocktailPhoneWindowManager.windowTypeToLayerLw(paramInt);
    if (i > 0)
      return i;
    Log.e("WindowManager", "Unknown window type: " + paramInt);
    return 2;
  }

  private static class HdmiControl
  {
    private final HdmiPlaybackClient mClient;

    private HdmiControl(HdmiPlaybackClient paramHdmiPlaybackClient)
    {
      this.mClient = paramHdmiPlaybackClient;
    }

    public void turnOnTv()
    {
      if (this.mClient == null)
        return;
      this.mClient.oneTouchPlay(new HdmiPlaybackClient.OneTouchPlayCallback()
      {
        public void onComplete(int paramInt)
        {
          if (paramInt != 0)
            Log.w("WindowManager", "One touch play failed: " + paramInt);
        }
      });
    }
  }

  final class HideNavInputEventReceiver extends InputEventReceiver
  {
    public HideNavInputEventReceiver(InputChannel paramLooper, Looper arg3)
    {
      super(localLooper);
    }

    public void onInputEvent(InputEvent paramInputEvent)
    {
      try
      {
        MotionEvent localMotionEvent;
        int i;
        if (((paramInputEvent instanceof MotionEvent)) && ((paramInputEvent.getSource() & 0x2) != 0))
        {
          localMotionEvent = (MotionEvent)paramInputEvent;
          if (localMotionEvent.getAction() == 0)
            i = 0;
        }
        synchronized (PhoneWindowManager.this.mWindowManagerFuncs.getWindowManagerLock())
        {
          int k = PhoneWindowManager.this.mResettingSystemUiFlags;
          int j = PhoneWindowManager.this.mForceClearedSystemUiFlags;
          int m = k | 0x2 | 0x1 | 0x4;
          if (k != m)
          {
            PhoneWindowManager.this.mResettingSystemUiFlags = m;
            i = 1;
          }
          k = j | 0x2;
          if (j != k)
          {
            PhoneWindowManager.this.mForceClearedSystemUiFlags = k;
            i = 1;
            PhoneWindowManager.this.mHandler.postDelayed(PhoneWindowManager.this.mClearHideNavigationFlag, 1000L);
          }
          if (i != 0)
            PhoneWindowManager.this.mWindowManagerFuncs.reevaluateStatusBarVisibility(localMotionEvent.getDisplayId());
          finishInputEvent(paramInputEvent, false);
          return;
        }
      }
      finally
      {
        finishInputEvent(paramInputEvent, false);
      }
      throw localObject2;
    }
  }

  class LongPressKill
    implements Runnable
  {
    LongPressKill()
    {
    }

    private String getApplicationNameFromPageName(String paramString)
    {
      PackageManager localPackageManager = PhoneWindowManager.this.mContext.getPackageManager();
      try
      {
        paramString = localPackageManager.getApplicationInfo(paramString, 0);
        if (paramString != null)
        {
          paramString = localPackageManager.getApplicationLabel(paramString);
          return (String)paramString;
        }
      }
      catch (android.content.pm.PackageManager.NameNotFoundException paramString)
      {
        while (true)
        {
          paramString = null;
          continue;
          paramString = "Unknown";
        }
      }
    }

    private boolean isHomeApplication(String paramString)
    {
      int j = 0;
      Object localObject = new Intent("android.intent.action.MAIN");
      ((Intent)localObject).addCategory("android.intent.category.HOME");
      localObject = PhoneWindowManager.this.mContext.getPackageManager().resolveActivity((Intent)localObject, 0);
      int i = j;
      if (localObject != null)
      {
        i = j;
        if (((ResolveInfo)localObject).activityInfo != null)
        {
          i = j;
          if (paramString.equals(((ResolveInfo)localObject).activityInfo.packageName))
            i = 1;
        }
      }
      return i;
    }

    private boolean isWhitelisted(String paramString)
    {
      if ("com.android.providers.applications".equals(paramString));
      do
        return true;
      while (("com.aryamod.romcontrol".equals(paramString)) || ("com.ficeto.customsettings".equals(paramString)) || ("android".equals(paramString)) || ("com.sec.android.widgetapp.alarmclock".equals(paramString)) || ("com.sec.phone".equals(paramString)) || ("com.android.defcontainer".equals(paramString)) || ("com.sec.android.app.factorymode".equals(paramString)));
      return false;
    }

    public void run()
    {
      Object localObject = PhoneWindowManager.this.mContext;
      PhoneWindowManager localPhoneWindowManager1 = PhoneWindowManager.this;
      Context localContext = localPhoneWindowManager1.mContext;
      localObject = ((ActivityManager)((Context)localObject).getSystemService("activity")).getRunningTasks(1);
      ActivityManager.RunningTaskInfo localRunningTaskInfo;
      String str;
      if ((localObject != null) && (((List)localObject).iterator().hasNext()))
      {
        localRunningTaskInfo = (ActivityManager.RunningTaskInfo)((List)localObject).iterator().next();
        str = localRunningTaskInfo.topActivity.getPackageName();
        if (!isWhitelisted(str))
        {
          if (isHomeApplication(str))
            break label139;
          Log.d("PhoneWindowManager stopped package ", str);
          localObject = "Killed : ";
        }
      }
      try
      {
        IActivityManager localIActivityManager = ActivityManagerNative.getDefault();
        PhoneWindowManager localPhoneWindowManager2 = PhoneWindowManager.this;
        localIActivityManager.removeTask(localRunningTaskInfo.id, 1);
        break label147;
        localObject = "White listed : ";
        break label147;
        label139: localPhoneWindowManager1.mSearchKeyShortcutPending = true;
        break label179;
        label147: str = getApplicationNameFromPageName(str);
        Toast.makeText(localContext, (String)localObject + str, 1).show();
        label179: localPhoneWindowManager1.performHapticFeedbackLw(null, 0, true);
        return;
      }
      catch (RemoteException localRemoteException)
      {
        break label179;
      }
    }
  }

  public class MusicNext
    implements Runnable
  {
    public MusicNext()
    {
    }

    public void run()
    {
      Object localObject;
      if ("test".equals("com.spotify.mobile.android.ui"))
      {
        localObject = new Intent("com.spotify.mobile.android.service.action.player.NEXT");
        ((Intent)localObject).setClassName("com.spotify.mobile.android.ui", "com.spotify.mobile.android.service.SpotifyService");
        PhoneWindowManager.this.mContext.startService((Intent)localObject);
      }
      while (true)
      {
        localObject = PhoneWindowManager.this;
        ((PhoneWindowManager)localObject).mIsVolLongPressed = true;
        ((PhoneWindowManager)localObject).performHapticFeedbackLw(null, 0, false);
        return;
        PhoneWindowManager.this.sendMediaButtonEvent(PhoneWindowManager.this.mVolBtnVolUp);
      }
    }
  }

  public class MusicPrev
    implements Runnable
  {
    public MusicPrev()
    {
    }

    public void run()
    {
      Object localObject;
      if ("test".equals("com.spotify.mobile.android.ui"))
      {
        localObject = new Intent("com.spotify.mobile.android.service.action.player.PREVIOUS");
        ((Intent)localObject).setClassName("com.spotify.mobile.android.ui", "com.spotify.mobile.android.service.SpotifyService");
        PhoneWindowManager.this.mContext.startService((Intent)localObject);
      }
      while (true)
      {
        localObject = PhoneWindowManager.this;
        ((PhoneWindowManager)localObject).mIsVolLongPressed = true;
        ((PhoneWindowManager)localObject).performHapticFeedbackLw(null, 0, false);
        return;
        PhoneWindowManager.this.sendMediaButtonEvent(PhoneWindowManager.this.mVolBtnVolDown);
      }
    }
  }

  class MyOrientationListener extends WindowOrientationListener
  {
    private final Runnable mUpdateRotationRunnable = new Runnable()
    {
      public void run()
      {
        PhoneWindowManager.this.mPowerManagerInternal.powerHint(2, 0);
        PhoneWindowManager.this.updateRotation(false);
      }
    };

    MyOrientationListener(Context paramHandler, Handler arg3)
    {
      super(localHandler);
    }

    public void onProposedRotationChanged(int paramInt)
    {
      PhoneWindowManager.this.mHandler.post(this.mUpdateRotationRunnable);
    }
  }

  class MyWakeGestureListener extends WakeGestureListener
  {
    MyWakeGestureListener(Context paramHandler, Handler arg3)
    {
      super(localHandler);
    }

    public void onWakeUp()
    {
      synchronized (PhoneWindowManager.this.mLock)
      {
        if (PhoneWindowManager.this.shouldEnableWakeGestureLp())
        {
          PhoneWindowManager.this.performHapticFeedbackLw(null, 1, false);
          PhoneWindowManager.this.wakeUp(SystemClock.uptimeMillis(), PhoneWindowManager.this.mAllowTheaterModeWakeFromWakeGesture, 7);
        }
        return;
      }
    }
  }

  private class PolicyHandler extends Handler
  {
    private PolicyHandler()
    {
    }

    public void handleMessage(Message paramMessage)
    {
      boolean bool2 = true;
      boolean bool3 = true;
      boolean bool1 = true;
      PhoneWindowManager localPhoneWindowManager;
      long l;
      switch (paramMessage.what)
      {
      case 16:
      default:
        return;
      case 1:
        PhoneWindowManager.this.enablePointerLocation();
        return;
      case 2:
        PhoneWindowManager.this.disablePointerLocation();
        return;
      case 3:
        PhoneWindowManager.this.dispatchMediaKeyWithWakeLock((KeyEvent)paramMessage.obj);
        return;
      case 4:
        PhoneWindowManager.this.dispatchMediaKeyRepeatWithWakeLock((KeyEvent)paramMessage.obj);
        return;
      case 9:
        PhoneWindowManager.this.showRecentApps(false);
        return;
      case 10:
        PhoneWindowManager.this.showGlobalActionsInternal();
        return;
      case 105:
        Log.d("WindowManager", "Waiting for Shared Device keyguard");
        if (PhoneWindowManager.this.mKeyguardDelegate.isShowing())
        {
          Log.d("WindowManager", "keyguard shown");
          if (PhoneWindowManager.this.mKeyguardDelegate.isSimLocked())
          {
            Log.d("WindowManager", "SIM lock identify API delay");
            PhoneWindowManager.this.launchKeyguardOwner();
            PhoneWindowManager.this.mHandler.removeMessages(105);
            PhoneWindowManager.this.startedWakingUp();
            PhoneWindowManager.this.finishedWakingUp();
            return;
          }
          PhoneWindowManager.this.startedGoingToSleep(2);
          PhoneWindowManager.this.finishedGoingToSleep(2);
          PhoneWindowManager.this.mHandler.sendEmptyMessageDelayed(105, 1000L);
          return;
        }
        Log.d("WindowManager", "keyguard not shown");
        if (PhoneWindowManager.this.mKeyguardDelegate.isSimLocked())
        {
          Log.d("WindowManager", "SIM lock identify");
          PhoneWindowManager.this.launchKeyguardOwner();
        }
        PhoneWindowManager.this.mHandler.removeMessages(105);
        PhoneWindowManager.this.startedWakingUp();
        PhoneWindowManager.this.finishedWakingUp();
        return;
      case 5:
        if (PhoneWindowManager.DEBUG_WAKEUP)
          Log.w("WindowManager", "Setting mKeyguardDrawComplete");
        PhoneWindowManager.this.finishKeyguardDrawn(PhoneWindowManager.mScreenTurnDisplayId);
        return;
      case 6:
        Slog.w("WindowManager", "!@Boot: Keyguard drawn timeout. Setting mKeyguardDrawComplete");
        PhoneWindowManager.this.finishKeyguardDrawn(PhoneWindowManager.mScreenTurnDisplayId);
        return;
      case 7:
        if (PhoneWindowManager.DEBUG_WAKEUP)
          Log.w("WindowManager", "Setting mWindowManagerDrawComplete");
        PhoneWindowManager.this.finishWindowsDrawn(PhoneWindowManager.mScreenTurnDisplayId);
        return;
      case 11:
        PhoneWindowManager.this.handleHideBootMessage();
        return;
      case 12:
        localPhoneWindowManager = PhoneWindowManager.this;
        if (paramMessage.arg1 != 0);
        for (bool1 = true; ; bool1 = false)
        {
          localPhoneWindowManager.launchVoiceAssistWithWakeLock(bool1);
          return;
        }
      case 13:
        localPhoneWindowManager = PhoneWindowManager.this;
        l = ((Long)paramMessage.obj).longValue();
        if (paramMessage.arg1 != 0);
        while (true)
        {
          localPhoneWindowManager.powerPress(l, bool1, paramMessage.arg2);
          PhoneWindowManager.this.finishPowerKeyPress();
          return;
          bool1 = false;
        }
      case 62:
        localPhoneWindowManager = PhoneWindowManager.this;
        l = ((Long)paramMessage.obj).longValue();
        if (paramMessage.arg1 != 0);
        for (bool1 = bool2; ; bool1 = false)
        {
          localPhoneWindowManager.endCallPress(l, bool1, paramMessage.arg2);
          PhoneWindowManager.this.finishEndCallKeyPress();
          return;
        }
      case 14:
        PhoneWindowManager.this.powerLongPress();
        return;
      case 15:
        localPhoneWindowManager = PhoneWindowManager.this;
        if (paramMessage.arg1 != 0);
        for (bool1 = bool3; ; bool1 = false)
        {
          localPhoneWindowManager.updateDreamingSleepToken(bool1);
          return;
        }
      case 17:
        PhoneWindowManager.this.notifyToSSRM(PhoneWindowManager.this.mTopIsFullscreen);
        return;
      case 18:
        PhoneWindowManager.this.enableToolBox();
        return;
      case 19:
        PhoneWindowManager.this.disableToolBox();
        return;
      case 50:
        PhoneWindowManager.this.mSPWM.updateSideKeyPanelState(true);
        return;
      case 51:
        PhoneWindowManager.this.mSPWM.updateSideKeyPanelState(false);
        return;
      case 52:
        PhoneWindowManager.this.mSPWM.updateBottomKeyPanelState(true, true);
        return;
      case 53:
        PhoneWindowManager.this.mSPWM.updateBottomKeyPanelState(false, true);
        return;
      case 54:
        PhoneWindowManager.this.mSPWM.showBottomKeyPanel(true);
        return;
      case 55:
        PhoneWindowManager.this.mSPWM.showBottomKeyPanel(false);
        return;
      case 56:
        PhoneWindowManager.this.mSPWM.updateEasyOneHandState(((Boolean)paramMessage.obj).booleanValue(), true, false);
        return;
      case 57:
        PhoneWindowManager.this.mSPWM.updateEasyOneHandState(false, false, false);
        return;
      case 60:
        Slog.v("WindowManager", "MSG_REQUEST_TRAVERSAL_BY_PWM");
        PhoneWindowManager.this.mWindowManagerInternal.requestTraversalFromDisplayManager();
        return;
      case 61:
      }
      paramMessage = (MultiWindowFacade)PhoneWindowManager.this.mContext.getSystemService("multiwindow_facade");
      if (paramMessage != null)
      {
        if (!PhoneWindowManager.this.mMobileKeyboardEnabled)
          break label959;
        paramMessage.updateMultiWindowSetting("mobile_keyboard", false);
      }
      while (true)
      {
        try
        {
          PhoneWindowManager.this.mWindowManager.updateDisplay();
          return;
        }
        catch (RemoteException paramMessage)
        {
          return;
        }
        label959: paramMessage.updateMultiWindowSetting("mobile_keyboard", true);
      }
    }
  }

  class ScreenLockTimeout
    implements Runnable
  {
    Bundle options;

    ScreenLockTimeout()
    {
    }

    public void run()
    {
      monitorenter;
      try
      {
        if (PhoneWindowManager.this.mKeyguardDelegate != null)
          PhoneWindowManager.this.mKeyguardDelegate.doKeyguardTimeout(this.options);
        PhoneWindowManager.this.mLockScreenTimerActive = false;
        this.options = null;
        return;
      }
      finally
      {
        monitorexit;
      }
      throw localObject;
    }

    public void setLockOptions(Bundle paramBundle)
    {
      this.options = paramBundle;
    }
  }

  class SettingsObserver extends ContentObserver
  {
    SettingsObserver(Handler arg2)
    {
      super();
    }

    void observe()
    {
      ContentResolver localContentResolver = PhoneWindowManager.this.mContext.getContentResolver();
      localContentResolver.registerContentObserver(Settings.System.getUriFor("end_button_behavior"), false, this, -1);
      localContentResolver.registerContentObserver(Settings.Secure.getUriFor("incall_power_button_behavior"), false, this, -1);
      localContentResolver.registerContentObserver(Settings.Secure.getUriFor("wake_gesture_enabled"), false, this, -1);
      localContentResolver.registerContentObserver(Settings.System.getUriFor("accelerometer_rotation"), false, this, -1);
      localContentResolver.registerContentObserver(Settings.System.getUriFor("user_rotation"), false, this, -1);
      localContentResolver.registerContentObserver(Settings.System.getUriFor("screen_off_timeout"), false, this, -1);
      localContentResolver.registerContentObserver(Settings.System.getUriFor("pointer_location"), false, this, -1);
      localContentResolver.registerContentObserver(Settings.Secure.getUriFor("default_input_method"), false, this, -1);
      localContentResolver.registerContentObserver(Settings.System.getUriFor("volbtn_music_controls"), false, this, -1);
      localContentResolver.registerContentObserver(Settings.System.getUriFor("volbtn_timeout"), false, this, -1);
      localContentResolver.registerContentObserver(Settings.System.getUriFor("volbtn_vol_up"), false, this, -1);
      localContentResolver.registerContentObserver(Settings.System.getUriFor("volbtn_vol_down"), false, this, -1);
      localContentResolver.registerContentObserver(Settings.Secure.getUriFor("immersive_mode_confirmations"), false, this, -1);
      localContentResolver.registerContentObserver(Settings.System.getUriFor("policy_control"), false, this, -1);
      if (TwToolBoxService.TOOLBOX_SUPPORT)
      {
        localContentResolver.registerContentObserver(Settings.System.getUriFor("toolbox_onoff"), false, this, -1);
        localContentResolver.registerContentObserver(Settings.System.getUriFor("toolbox_apps"), false, this, -1);
      }
      Settings.System.putIntForUser(localContentResolver, "any_screen_running", 0, -2);
      localContentResolver.registerContentObserver(Settings.System.getUriFor("any_screen_enabled"), false, this, -1);
      localContentResolver.registerContentObserver(Settings.System.getUriFor("any_screen_running"), false, this, -1);
      SamsungPolicyProperties.getEasyOneHandPkgVersion(PhoneWindowManager.this.mContext);
      if (SamsungPolicyProperties.hasSideKeyPanelFeature(PhoneWindowManager.this.mContext))
        localContentResolver.registerContentObserver(Settings.System.getUriFor("sidesoftkey_switch"), false, this, -1);
      localContentResolver.registerContentObserver(Settings.System.getUriFor("cocktail_bar_enabled_180_rotate"), false, this, -1);
      localContentResolver.registerContentObserver(Settings.Secure.getUriFor("user_setup_complete"), false, this, -1);
      localContentResolver.registerContentObserver(Settings.System.getUriFor("mobile_keyboard"), false, this, -1);
      if (SamsungPolicyProperties.FolderTypeFeature(PhoneWindowManager.this.mContext) == 2)
        localContentResolver.registerContentObserver(Settings.System.getUriFor("accelerometer_rotation_second"), false, this, -1);
      localContentResolver.registerContentObserver(Settings.Secure.getUriFor("assistant"), false, this, -1);
      PhoneWindowManager.this.updateSettings();
    }

    public void onChange(boolean paramBoolean, Uri paramUri)
    {
      PhoneWindowManager.this.updateSettings();
      if ((paramUri != null) && (paramUri.equals(Settings.System.getUriFor("mobile_keyboard"))))
        return;
      PhoneWindowManager.this.updateRotation(false);
    }
  }

  class Torch extends BroadcastReceiver
  {
    Torch()
    {
    }

    public void onReceive(Context paramContext, Intent paramIntent)
    {
      if ("android.intent.action.ScreenShot".equals(paramIntent.getAction()))
        PhoneWindowManager.this.takeScreenshot();
    }
  }

  private final class TwToolBoxPointerEventListener
    implements WindowManagerPolicy.PointerEventListener
  {
    private TwToolBoxPointerEventListener()
    {
    }

    public void onPointerEvent(MotionEvent paramMotionEvent)
    {
      if ((PhoneWindowManager.this.mTwToolBoxFloatingViewer != null) && (!PhoneWindowManager.this.cursorWindowTouchableRegion.contains((int)paramMotionEvent.getRawX(), (int)paramMotionEvent.getRawY())))
        PhoneWindowManager.this.mTwToolBoxFloatingViewer.onTouchEvent(paramMotionEvent);
    }
  }
}

/* Location:           C:\Users\Kamran\Desktop\EXtreme_ApkTool_v1.0.0\1-Sources\d2j\classes-dex2jar.jar
 * Qualified Name:     com.android.server.policy.PhoneWindowManager
 * JD-Core Version:    0.6.0
 */