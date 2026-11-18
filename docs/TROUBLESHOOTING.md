# Troubleshooting on Android

Practical steps for the two failures users report most often: the app being killed
while it runs in the background, and the VPN dropping while the app stays alive.

Report results to [issues](https://github.com/Q7DF1/XrayFA/issues) with the details
listed at the end.

## 1. The app is killed in the background

Two different failures look identical from the outside:

- **the system killed the process** — no trace in the app, the notification
  disappears, reopening shows the normal home screen;
- **the app crashed** — the system may show "XrayFA has stopped", and the crash
  buffer has an entry.

Separate them before changing any setting.

### Rule out a crash first

```bash
adb logcat -b crash -v time
```

If the output mentions the app, it is a crash: attach that buffer to the issue.
If the buffer is empty, continue below.

### Capture a log while it dies

```bash
adb logcat -c                    # start clean
adb logcat -v time > xrayfa.txt  # reproduce the kill, then Ctrl+C
```

To keep only this app's output:

```bash
adb logcat --pid=$(adb shell pidof -s com.android.xrayfa)
```

On some builds `pidof` is missing; `adb shell ps -A | grep xray` finds the package
name instead.

**Redact before posting:** subscription URLs, UUIDs, short IDs, server addresses
and passwords can all appear in logs. Replace them with `REDACTED`.

### Check the OEM battery manager

Most "killed in background" reports come from vendor battery policies, not from the
app. Allow the app to run unrestricted, then retest for a day:

| System | Where to change it |
| --- | --- |
| ColorOS / realme UI | Settings → Battery → App battery management → XrayFA → Allow background activity; also Settings → Apps → App management → Auto-launch |
| MIUI / HyperOS | Settings → Apps → Manage apps → XrayFA → Battery saver → No restrictions; also Autostart |
| OriginOS / Funtouch OS | iManager → App manager → Autostart → allow; Background power consumption → allow |
| EMUI / HarmonyOS | Settings → Battery → App launch → XrayFA → Manage manually → enable all three |
| Stock Android | Settings → Apps → XrayFA → Battery → Unrestricted |

Menu names differ between major versions; the shape is always "let this app run in
the background" plus "allow auto-start".

### Android 13+ notification permission

Without the notification permission the foreground service still runs, but its
notification is hidden and the system is more aggressive about reclaiming the
process. Check Settings → Apps → XrayFA → Notifications is enabled.

## 2. The VPN drops but the app stays alive

Usually the VPN session, not the process, was torn down:

- checking the log for `VpnService` teardown reasons;
- switching between TUN and SOCKS mode isolates whether TUN is the problem;
- on some ROMs, "Private DNS" or a system-wide VPN conflict closes the tunnel.

Include which mode was active (TUN / SOCKS), the routing mode, and whether the
status in the app showed connected or disconnected at the moment of the drop.

## 3. What a useful report contains

- app version (Settings → About), Android version, device model and architecture;
- install channel: GitHub Release, F-Droid, or a self-built APK;
- exact reproduction steps and how long it takes to fail;
- the log file from step 1, or a screen recording of the drop;
- battery setting state for the app (unrestricted or not) and whether auto-start is
  allowed.

Missing logs are the most common reason a report stalls: the app does not persist
logs after the process dies, so the capture must be running before the failure
happens.
