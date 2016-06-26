package com.android.server.policy;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.input.InputManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.provider.Settings.System;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

public class PhoneWindowManagers$AryaModKeyPolicy
{
  private Context mContext;

  public PhoneWindowManagers$AryaModKeyPolicy(Context paramContext)
  {
    this.mContext = paramContext;
    regReceiver();
  }

  private boolean getTorchStatus()
  {
    int i = 0;
    if (Settings.System.getIntForUser(this.mContext.getContentResolver(), "torch_light", 0, -2) != 0)
      i = 1;
    return i;
  }

  public void regReceiver()
  {
    IntentFilter localIntentFilter = new IntentFilter();
    localIntentFilter.addAction("android.intent.action.PhoneWindowManagers$AryaModKeyPolicy");
    this.mContext.registerReceiver(new BroadcastReceiver()
    {
      long mDownTime;

      private String getApplicationName(String paramString)
      {
        PackageManager localPackageManager = PhoneWindowManagers.AryaModKeyPolicy.this.mContext.getPackageManager();
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

      private boolean homeApplication(String paramString)
      {
        int j = 0;
        Object localObject = new Intent("android.intent.action.MAIN");
        ((Intent)localObject).addCategory("android.intent.category.HOME");
        localObject = PhoneWindowManagers.AryaModKeyPolicy.this.mContext.getPackageManager().resolveActivity((Intent)localObject, 0);
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

      private boolean unKillApps(String paramString)
      {
        if ("com.android.providers.applications".equals(paramString));
        do
          return true;
        while (("com.sec.android.app.clockpackage".equals(paramString)) || ("android".equals(paramString)) || ("com.aryamod.romcontrol".equals(paramString)) || ("com.android.server.telecom".equals(paramString)) || ("com.android.incallui".equals(paramString)) || ("com.android.systemui".equals(paramString)) || ("com.android.phone".equals(paramString)));
        return false;
      }

      public void DoubleTapLauncherapp()
      {
        Object localObject = Settings.System.getString(PhoneWindowManagers.AryaModKeyPolicy.this.mContext.getContentResolver(), "aryamod_doubletap_home_app");
        if (localObject != null)
        {
          localObject = ((String)localObject).split("##");
          localObject = new ComponentName(localObject[0], localObject[1]);
          Intent localIntent = new Intent();
          localIntent.setFlags(335544320);
          localIntent.setComponent((ComponentName)localObject);
          PhoneWindowManagers.AryaModKeyPolicy.this.mContext.startActivity(localIntent);
        }
      }

      public void DoubleTaphomeapp()
      {
        Object localObject = Settings.System.getString(PhoneWindowManagers.AryaModKeyPolicy.this.mContext.getContentResolver(), "aryamod_doubletap_home_app");
        if (localObject != null)
        {
          localObject = ((String)localObject).split("##");
          localObject = new ComponentName(localObject[0], localObject[1]);
          Intent localIntent = new Intent();
          localIntent.setFlags(335544320);
          localIntent.setComponent((ComponentName)localObject);
          PhoneWindowManagers.AryaModKeyPolicy.this.mContext.startActivity(localIntent);
        }
      }

      public void LongPressbackapp()
      {
        Object localObject = Settings.System.getString(PhoneWindowManagers.AryaModKeyPolicy.this.mContext.getContentResolver(), "aryamod_longpress_back_app");
        if (localObject != null)
        {
          localObject = ((String)localObject).split("##");
          localObject = new ComponentName(localObject[0], localObject[1]);
          Intent localIntent = new Intent();
          localIntent.setFlags(335544320);
          localIntent.setComponent((ComponentName)localObject);
          PhoneWindowManagers.AryaModKeyPolicy.this.mContext.startActivity(localIntent);
        }
      }

      public void LongPresshomeapp()
      {
        Object localObject = Settings.System.getString(PhoneWindowManagers.AryaModKeyPolicy.this.mContext.getContentResolver(), "aryamod_longpress_home_app");
        if (localObject != null)
        {
          localObject = ((String)localObject).split("##");
          localObject = new ComponentName(localObject[0], localObject[1]);
          Intent localIntent = new Intent();
          localIntent.setFlags(335544320);
          localIntent.setComponent((ComponentName)localObject);
          PhoneWindowManagers.AryaModKeyPolicy.this.mContext.startActivity(localIntent);
        }
      }

      public void LongPressmenuapp()
      {
        Object localObject = Settings.System.getString(PhoneWindowManagers.AryaModKeyPolicy.this.mContext.getContentResolver(), "aryamod_longpress_menu_app");
        if (localObject != null)
        {
          localObject = ((String)localObject).split("##");
          localObject = new ComponentName(localObject[0], localObject[1]);
          Intent localIntent = new Intent();
          localIntent.setFlags(335544320);
          localIntent.setComponent((ComponentName)localObject);
          PhoneWindowManagers.AryaModKeyPolicy.this.mContext.startActivity(localIntent);
        }
      }

      public void doScreenShot()
      {
        Intent localIntent = new Intent("android.intent.action.ScreenShot");
        PhoneWindowManagers.AryaModKeyPolicy.this.mContext.sendBroadcast(localIntent);
      }

      public void expandStatusBar()
      {
        Object localObject2 = PhoneWindowManagers.AryaModKeyPolicy.this.mContext.getSystemService("statusbar");
        try
        {
          Object localObject1 = Class.forName("android.app.StatusBarManager");
          if (Build.VERSION.SDK_INT >= 17);
          for (localObject1 = ((Class)localObject1).getMethod("expandNotificationsPanel", new Class[0]); ; localObject1 = ((Class)localObject1).getMethod("expand", new Class[0]))
          {
            ((Method)localObject1).invoke(localObject2, new Object[0]);
            return;
          }
        }
        catch (Exception localException)
        {
          localException.printStackTrace();
        }
      }

      public boolean getMobileDataStatus()
      {
        Object localObject2 = (ConnectivityManager)PhoneWindowManagers.AryaModKeyPolicy.this.mContext.getSystemService("connectivity");
        Class localClass = localObject2.getClass();
        Object localObject1 = Boolean.valueOf(false);
        try
        {
          localObject2 = (Boolean)localClass.getMethod("getMobileDataEnabled", null).invoke(localObject2, null);
          localObject1 = localObject2;
          return ((Boolean)localObject1).booleanValue();
        }
        catch (Exception localException)
        {
          while (true)
            localException.printStackTrace();
        }
      }

      public boolean isBlueToothOn()
      {
        int i = 1;
        BluetoothAdapter localBluetoothAdapter = null;
        if (0 == 0)
          localBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        switch (localBluetoothAdapter.getState())
        {
        default:
          i = 0;
        case 11:
        case 12:
          return i;
        case 10:
          return false;
        case 13:
        }
        return false;
      }

      public void killApp()
      {
        Object localObject = PhoneWindowManagers.AryaModKeyPolicy.this.mContext;
        Context localContext = PhoneWindowManagers.AryaModKeyPolicy.this.mContext;
        localObject = ((ActivityManager)((Context)localObject).getSystemService("activity")).getRunningTasks(1);
        ActivityManager.RunningTaskInfo localRunningTaskInfo;
        String str;
        if ((localObject != null) && (((List)localObject).iterator().hasNext()))
        {
          localRunningTaskInfo = (ActivityManager.RunningTaskInfo)((List)localObject).iterator().next();
          str = localRunningTaskInfo.topActivity.getPackageName();
          if (!unKillApps(str))
          {
            if (homeApplication(str))
              break label140;
            Log.d("Stopped package ", str);
            localObject = "Killed: ";
          }
        }
        try
        {
          IActivityManager localIActivityManager = ActivityManagerNative.getDefault();
          localIActivityManager.removeTask(localRunningTaskInfo.id, 1);
          localIActivityManager.forceStopPackage(str, -2);
          break label141;
          localObject = "White listed: ";
          break label141;
          label140: return;
          label141: str = getApplicationName(str);
          Toast.makeText(localContext, (String)localObject + str, 1).show();
          return;
        }
        catch (RemoteException localRemoteException)
        {
        }
      }

      public void killall()
      {
        Context localContext = PhoneWindowManagers.AryaModKeyPolicy.this.mContext;
        HashSet localHashSet = new HashSet();
        Object localObject = Settings.System.getString(localContext.getContentResolver(), "AryaMod_MemClean_array");
        if (localObject != null)
        {
          localObject = ((String)localObject).split("\\|");
          int j = localObject.length;
          int i = 0;
          while (i < j)
          {
            localHashSet.add(localObject[i]);
            i += 1;
          }
        }
        localObject = ((ActivityManager)PhoneWindowManagers.AryaModKeyPolicy.this.mContext.getSystemService("activity")).getRunningAppProcesses().iterator();
        while (((Iterator)localObject).hasNext())
        {
          ActivityManager.RunningAppProcessInfo localRunningAppProcessInfo = (ActivityManager.RunningAppProcessInfo)((Iterator)localObject).next();
          String str = localRunningAppProcessInfo.processName.split(":")[0];
          if ((localRunningAppProcessInfo.importance <= 300) || (localHashSet.contains(str)))
            continue;
          ((ActivityManager)PhoneWindowManagers.AryaModKeyPolicy.this.mContext.getSystemService("activity")).killBackgroundProcesses(str);
        }
        Toast.makeText(localContext, "Cleaning Up Memory", 0).show();
      }

      public void onReceive(Context paramContext, Intent paramIntent)
      {
        if (!paramIntent.getAction().equals("android.intent.action.PhoneWindowManagers$AryaModKeyPolicy"))
          return;
        switch (paramIntent.getIntExtra("action", 999))
        {
        default:
          return;
        case 0:
          sendSleepEvent();
          return;
        case 1:
          powerMenu();
          return;
        case 2:
          sendBackKeyEvent();
          return;
        case 3:
          sendHomeKeyEvent();
          return;
        case 4:
          sendRecentKeyEvent();
          return;
        case 5:
          sendMenuKeyEvent();
          return;
        case 6:
          sendVolumeUpKeyEvent();
          return;
        case 7:
          sendVolumeDownKeyEvent();
          return;
        case 8:
          sendCalllogKeyEvent();
          return;
        case 9:
          sendSearchKeyEvent();
          return;
        case 10:
          setAudioMode();
          return;
        case 11:
          killApp();
          return;
        case 12:
          setTorch();
          return;
        case 13:
          doScreenShot();
          return;
        case 14:
          expandStatusBar();
          return;
        case 15:
          toggleWifi();
          return;
        case 16:
          toggleMobileData();
          return;
        case 17:
          toggleBlueTooth();
          return;
        case 18:
          LongPressmenuapp();
          return;
        case 19:
          LongPressbackapp();
          return;
        case 20:
          LongPresshomeapp();
          return;
        case 21:
          DoubleTaphomeapp();
          return;
        case 22:
          DoubleTapLauncherapp();
          return;
        case 23:
        }
        killall();
      }

      public void powerMenu()
      {
        sendEvent(0, 128);
        new View(PhoneWindowManagers.AryaModKeyPolicy.this.mContext).sendAccessibilityEvent(2);
      }

      public void sendBackKeyEvent()
      {
        int[] arrayOfInt = new int[2];
        int[] tmp7_5 = arrayOfInt;
        tmp7_5[0] = 0;
        int[] tmp11_7 = tmp7_5;
        tmp11_7[1] = 1;
        tmp11_7;
        int i = 0;
        while (i < arrayOfInt.length)
        {
          long l = SystemClock.uptimeMillis();
          KeyEvent localKeyEvent = new KeyEvent(l, l, arrayOfInt[i], 4, 0, 0, -1, 0, 268435464, 257);
          InputManager.getInstance().injectInputEvent(localKeyEvent, 0);
          i += 1;
        }
      }

      public void sendCalllogKeyEvent()
      {
        int[] arrayOfInt = new int[2];
        int[] tmp7_5 = arrayOfInt;
        tmp7_5[0] = 0;
        int[] tmp11_7 = tmp7_5;
        tmp11_7[1] = 1;
        tmp11_7;
        int i = 0;
        while (i < arrayOfInt.length)
        {
          long l = SystemClock.uptimeMillis();
          KeyEvent localKeyEvent = new KeyEvent(l, l, arrayOfInt[i], 5, 0, 0, -1, 0, 268435464, 257);
          InputManager.getInstance().injectInputEvent(localKeyEvent, 0);
          i += 1;
        }
      }

      void sendEvent(int paramInt1, int paramInt2)
      {
        sendEvent(paramInt1, paramInt2, SystemClock.uptimeMillis());
      }

      void sendEvent(int paramInt1, int paramInt2, long paramLong)
      {
        if ((paramInt2 & 0x80) != 0);
        for (int i = 1; ; i = 0)
        {
          long l = SystemClock.uptimeMillis();
          this.mDownTime = l;
          KeyEvent localKeyEvent = new KeyEvent(l, paramLong, paramInt1, 26, i, 0, -1, 0, paramInt2 | 0x8 | 0x40, 257);
          InputManager.getInstance().injectInputEvent(localKeyEvent, 0);
          return;
        }
      }

      public void sendHomeKeyEvent()
      {
        int[] arrayOfInt = new int[2];
        int[] tmp7_5 = arrayOfInt;
        tmp7_5[0] = 0;
        int[] tmp11_7 = tmp7_5;
        tmp11_7[1] = 1;
        tmp11_7;
        int i = 0;
        while (i < arrayOfInt.length)
        {
          long l = SystemClock.uptimeMillis();
          KeyEvent localKeyEvent = new KeyEvent(l, l, arrayOfInt[i], 3, 0, 0, -1, 0, 268435464, 257);
          InputManager.getInstance().injectInputEvent(localKeyEvent, 0);
          i += 1;
        }
      }

      public void sendMenuKeyEvent()
      {
        int[] arrayOfInt = new int[2];
        int[] tmp7_5 = arrayOfInt;
        tmp7_5[0] = 0;
        int[] tmp11_7 = tmp7_5;
        tmp11_7[1] = 1;
        tmp11_7;
        int i = 0;
        while (i < arrayOfInt.length)
        {
          long l = SystemClock.uptimeMillis();
          KeyEvent localKeyEvent = new KeyEvent(l, l, arrayOfInt[i], 82, 0, 0, -1, 0, 268435464, 257);
          InputManager.getInstance().injectInputEvent(localKeyEvent, 0);
          i += 1;
        }
      }

      public void sendRecentKeyEvent()
      {
        int[] arrayOfInt = new int[2];
        int[] tmp7_5 = arrayOfInt;
        tmp7_5[0] = 0;
        int[] tmp11_7 = tmp7_5;
        tmp11_7[1] = 1;
        tmp11_7;
        int i = 0;
        while (i < arrayOfInt.length)
        {
          long l = SystemClock.uptimeMillis();
          KeyEvent localKeyEvent = new KeyEvent(l, l, arrayOfInt[i], 187, 0, 0, -1, 0, 268435464, 257);
          InputManager.getInstance().injectInputEvent(localKeyEvent, 0);
          i += 1;
        }
      }

      public void sendSearchKeyEvent()
      {
        int[] arrayOfInt = new int[2];
        int[] tmp7_5 = arrayOfInt;
        tmp7_5[0] = 0;
        int[] tmp11_7 = tmp7_5;
        tmp11_7[1] = 1;
        tmp11_7;
        int i = 0;
        while (i < arrayOfInt.length)
        {
          long l = SystemClock.uptimeMillis();
          KeyEvent localKeyEvent = new KeyEvent(l, l, arrayOfInt[i], 84, 0, 0, -1, 0, 268435464, 257);
          InputManager.getInstance().injectInputEvent(localKeyEvent, 0);
          i += 1;
        }
      }

      public void sendSleepEvent()
      {
        ((PowerManager)PhoneWindowManagers.AryaModKeyPolicy.this.mContext.getSystemService("power")).goToSleep(SystemClock.uptimeMillis());
      }

      public void sendVolumeDownKeyEvent()
      {
        int[] arrayOfInt = new int[2];
        int[] tmp7_5 = arrayOfInt;
        tmp7_5[0] = 0;
        int[] tmp11_7 = tmp7_5;
        tmp11_7[1] = 1;
        tmp11_7;
        int i = 0;
        while (i < arrayOfInt.length)
        {
          long l = SystemClock.uptimeMillis();
          KeyEvent localKeyEvent = new KeyEvent(l, l, arrayOfInt[i], 25, 0, 0, -1, 0, 268435464, 257);
          InputManager.getInstance().injectInputEvent(localKeyEvent, 0);
          i += 1;
        }
      }

      public void sendVolumeUpKeyEvent()
      {
        int[] arrayOfInt = new int[2];
        int[] tmp7_5 = arrayOfInt;
        tmp7_5[0] = 0;
        int[] tmp11_7 = tmp7_5;
        tmp11_7[1] = 1;
        tmp11_7;
        int i = 0;
        while (i < arrayOfInt.length)
        {
          long l = SystemClock.uptimeMillis();
          KeyEvent localKeyEvent = new KeyEvent(l, l, arrayOfInt[i], 24, 0, 0, -1, 0, 268435464, 257);
          InputManager.getInstance().injectInputEvent(localKeyEvent, 0);
          i += 1;
        }
      }

      public void setAudioMode()
      {
        AudioManager localAudioManager = (AudioManager)PhoneWindowManagers.AryaModKeyPolicy.this.mContext.getSystemService("audio");
        switch (localAudioManager.getRingerMode())
        {
        default:
          return;
        case 0:
          localAudioManager.setRingerMode(2);
          return;
        case 1:
          localAudioManager.setRingerMode(0);
          return;
        case 2:
        }
        localAudioManager.setRingerMode(1);
      }

      public void setTorch()
      {
        PhoneWindowManagers.AryaModKeyPolicy localAryaModKeyPolicy = PhoneWindowManagers.AryaModKeyPolicy.this;
        if (localAryaModKeyPolicy.getTorchStatus());
        for (Object localObject = "com.samsung.intent.action.ASSISTIVELIGHT_OFF"; ; localObject = "com.samsung.intent.action.ASSISTIVELIGHT_ON")
        {
          localObject = new Intent((String)localObject);
          localAryaModKeyPolicy.mContext.sendBroadcastAsUser((Intent)localObject, UserHandle.ALL);
          return;
        }
      }

      public void toggleBlueTooth()
      {
        Context localContext = PhoneWindowManagers.AryaModKeyPolicy.this.mContext;
        BluetoothAdapter localBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (isBlueToothOn())
        {
          localBluetoothAdapter.disable();
          Toast.makeText(localContext, "Blutooth Switched OFF", 0).show();
          return;
        }
        localBluetoothAdapter.enable();
        Toast.makeText(localContext, "Bluetooth Switched ON", 0).show();
      }

      public void toggleMobileData()
      {
        Context localContext = PhoneWindowManagers.AryaModKeyPolicy.this.mContext;
        ConnectivityManager localConnectivityManager = (ConnectivityManager)localContext.getSystemService("connectivity");
        try
        {
          Method localMethod = localConnectivityManager.getClass().getDeclaredMethod("setMobileDataEnabled", new Class[] { Boolean.TYPE });
          if (getMobileDataStatus())
          {
            localMethod.invoke(localConnectivityManager, new Object[] { Boolean.valueOf(false) });
            Toast.makeText(localContext, "Mobile Data Disabled", 0).show();
            return;
          }
          localMethod.invoke(localConnectivityManager, new Object[] { Boolean.valueOf(true) });
          Toast.makeText(localContext, "Mobile Data Enabled", 0).show();
          return;
        }
        catch (Exception localException)
        {
          localException.printStackTrace();
        }
      }

      public void toggleWifi()
      {
        Context localContext = PhoneWindowManagers.AryaModKeyPolicy.this.mContext;
        WifiManager localWifiManager = (WifiManager)localContext.getSystemService("wifi");
        if (localWifiManager.isWifiEnabled())
        {
          localWifiManager.setWifiEnabled(false);
          Toast.makeText(localContext, "Wi-Fi Disconnected", 0).show();
          return;
        }
        localWifiManager.setWifiEnabled(true);
        Toast.makeText(localContext, "Wi-Fi Connected", 0).show();
      }
    }
    , localIntentFilter);
  }
}

/* Location:           C:\Users\Kamran\Desktop\EXtreme_ApkTool_v1.0.0\1-Sources\d2j\classes-dex2jar.jar
 * Qualified Name:     com.android.server.policy.PhoneWindowManagers.AryaModKeyPolicy
 * JD-Core Version:    0.6.0
 */