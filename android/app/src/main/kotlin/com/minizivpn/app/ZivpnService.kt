package com.minizivpn.app

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import android.app.PendingIntent
import android.app.Service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import android.content.pm.ServiceInfo
import java.net.InetAddress
import java.util.LinkedList
import androidx.annotation.Keep
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import org.json.JSONObject

import java.io.BufferedReader
import java.io.InputStreamReader

import android.os.PowerManager

/**
 * ZIVPN TunService (Native C Version)
 * Handles the VpnService interface and integrates with C-based tun2socks via ProcessBuilder.
 */
@Keep
class ZivpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.minizivpn.app.CONNECT"
        const val ACTION_DISCONNECT = "com.minizivpn.app.DISCONNECT"
        const val ACTION_LOG = "com.minizivpn.app.LOG"
        const val CHANNEL_ID = "ZIVPN_SERVICE_CHANNEL"
        const val NOTIFICATION_ID = 1
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val processes = mutableListOf<Process>()
    private var wakeLock: PowerManager.WakeLock? = null

    private fun logToApp(msg: String) {
        val intent = Intent(ACTION_LOG)
        intent.putExtra("message", msg)
        sendBroadcast(intent)
        Log.d("ZIVPN-Core", msg)
    }

    private fun captureProcessLog(process: Process, name: String) {
        Thread {
            try {
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    logToApp("[$name] $line")
                }
            } catch (e: Exception) {
                logToApp("[$name] Log stream closed: ${e.message}")
            }
        }.start()
        
        Thread {
            try {
                val reader = BufferedReader(InputStreamReader(process.errorStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    logToApp("[$name-ERR] $line")
                }
            } catch (e: Exception) {}
        }.start()
    }

    /**
     * Copies a native library to internal storage and makes it executable.
     * This is required for Android 10+ (W^X violation fix).
     */
    private fun installBinary(libName: String, targetName: String): String? {
        try {
            val libFile = File(applicationInfo.nativeLibraryDir, libName)
            if (!libFile.exists()) {
                logToApp("Binary not found in libs: $libName")
                return null
            }

            val targetFile = File(filesDir, targetName)
            // Always copy to ensure we have the latest version/clean state
            libFile.copyTo(targetFile, overwrite = true)
            
            // Make executable
            targetFile.setExecutable(true, true)
            return targetFile.absolutePath
        } catch (e: Exception) {
            logToApp("Failed to install binary $libName: ${e.message}")
            return null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CONNECT) {
             startForegroundService()
        }
        
        when (intent?.action) {
            ACTION_CONNECT -> {
                connect()
                return START_STICKY
            }
            ACTION_DISCONNECT -> {
                disconnect()
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }
    
    private fun startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ZIVPN Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MiniZIVPN Running")
            .setContentText("VPN Service is active")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
             startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
             startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
             startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun connect() {
        if (vpnInterface != null) return

        Log.i("ZIVPN-Tun", "Initializing ZIVPN (C-Native engine)...")
        
        val prefs = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
        
        val ip = prefs.getString("server_ip", "") ?: ""
        val range = prefs.getString("server_range", "") ?: ""
        val pass = prefs.getString("server_pass", "") ?: ""
        val obfs = prefs.getString("server_obfs", "") ?: ""
        val multiplier = prefs.getFloat("multiplier", 1.0f)
        val mtu = prefs.getInt("mtu", 1500)
        val autoTuning = prefs.getBoolean("auto_tuning", true)
        val bufferSize = prefs.getString("buffer_size", "4m") ?: "4m"
        val logLevel = prefs.getString("log_level", "info") ?: "info"
        val coreCount = prefs.getInt("core_count", 4)
        val useWakelock = prefs.getBoolean("cpu_wakelock", false)

        if (useWakelock) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MiniZivpn::CoreWakelock")
            wakeLock?.acquire()
            logToApp("CPU Wakelock acquired")
        }

        // 1. START HYSTERIA & LOAD BALANCER
        try {
            startCores(ip, range, pass, obfs, multiplier.toDouble(), coreCount, logLevel)
        } catch (e: Exception) {
            Log.e("ZIVPN-Tun", "Failed to start cores: ${e.message}")
            stopSelf()
            return
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. Build VPN Interface
        val builder = Builder()
        builder.setSession("MiniZivpn")
        builder.setConfigureIntent(pendingIntent)
        builder.setMtu(mtu)
        
        // GLOBAL ROUTING
        try {
            builder.addRoute("0.0.0.0", 0)
            builder.addRoute("198.18.0.0", 15) // Fake-IP range
        } catch (e: Exception) {
            Log.e("ZIVPN-Tun", "Failed to add global route, falling back")
             // Fallback subnets if needed...
             builder.addRoute("0.0.0.0", 1)
             builder.addRoute("128.0.0.0", 1)
        }
        
        // DNS
        builder.addDnsServer("1.1.1.1")
        builder.addDnsServer("8.8.8.8")
        
        // Local IP for Tun interface (Must match what we tell tun2socks)
        val tunIp = "26.26.26.2"
        builder.addAddress("26.26.26.1", 24)

        try {
            vpnInterface = builder.establish()
            val fd = vpnInterface?.fd ?: return

            Log.i("ZIVPN-Tun", "VPN Interface established. FD: $fd")

            // 3. Start tun2socks (C Native Version)
            Thread {
                try {
                    // Install binaries to safe location
                    val tun2socksBin = installBinary("libtun2socks.so", "tun2socks") ?: throw Exception("tun2socks binary missing")
                    
                    // C-badvpn tun2socks arguments
                    // Note: Arguments depend on your specific badvpn version. 
                    // Assuming standard badvpn-tun2socks style:
                    val tun2socksArgs = arrayListOf(
                        tun2socksBin,
                        "--netif-ipaddr", tunIp,
                        "--netif-netmask", "255.255.255.0",
                        "--socks-server-addr", "127.0.0.1:7777",
                        "--tunfd", fd.toString(),
                        "--tunmtu", mtu.toString(),
                        "--loglevel", "3", // 0-5
                        "--enable-udprelay"
                    )

                    logToApp("Starting Tun2Socks Native: $tun2socksArgs")

                    val pb = ProcessBuilder(tun2socksArgs)
                    pb.directory(filesDir)
                    
                    // Native C often needs to know where dynamic libs are
                    val libDir = applicationInfo.nativeLibraryDir
                    pb.environment()["LD_LIBRARY_PATH"] = libDir
                    
                    pb.redirectErrorStream(true)
                    
                    val process = pb.start()
                    processes.add(process)
                    captureProcessLog(process, "tun2socks-c")

                    // Send FD to tun2socks if it waits for it via ancillary data
                    // NOTE: badvpn-tun2socks usually takes --tunfd and uses it directly if compiled with Android support
                    // But if it expects FD passing over socket:
                    Thread.sleep(1000)
                    if (NativeSystem.sendfd(fd) == 0) {
                        logToApp("Tun2Socks FD sent successfully (via ancillary).")
                    } else {
                        // Not necessarily an error if --tunfd works directly
                        logToApp("FD sending via ancillary skipped or failed (using --tunfd argument).")
                    }

                } catch (e: Exception) {
                    logToApp("Engine Error: ${e.message}")
                }
            }.start()

            prefs.edit().putBoolean("flutter.vpn_running", true).apply()

        } catch (e: Throwable) {
            Log.e("ZIVPN-Tun", "Error starting VPN: ${e.message}")
            stopSelf()
        }
    }

    private fun startCores(ip: String, range: String, pass: String, obfs: String, multiplier: Double, coreCount: Int, logLevel: String) {
        // Install binaries first
        val libUzBin = installBinary("libuz.so", "libuz") ?: throw Exception("libuz missing")
        val libLoadBin = installBinary("libload.so", "libload") ?: throw Exception("libload missing")
        val libDir = applicationInfo.nativeLibraryDir
        
        val baseConn = 131072
        val baseWin = 327680
        val dynamicConn = (baseConn * multiplier).toInt()
        val dynamicWin = (baseWin * multiplier).toInt()
        
        val ports = (0 until coreCount).map { 20080 + it }
        val tunnelTargets = mutableListOf<String>()

        val hyLogLevel = when(logLevel) {
            "silent" -> "disable"
            "error" -> "error"
            "debug" -> "debug"
            else -> "info"
        }

        for (port in ports) {
            val hyConfig = JSONObject()
            hyConfig.put("server", "$ip:$range")
            hyConfig.put("obfs", obfs)
            hyConfig.put("auth", pass)
            hyConfig.put("loglevel", hyLogLevel)
            
            val socks5Json = JSONObject()
            socks5Json.put("listen", "127.0.0.1:$port")
            hyConfig.put("socks5", socks5Json)
            
            hyConfig.put("insecure", true)
            hyConfig.put("recvwindowconn", dynamicConn)
            hyConfig.put("recvwindow", dynamicWin)
            
            val hyCmd = arrayListOf(libUzBin, "-s", obfs, "--config", hyConfig.toString())
            val hyPb = ProcessBuilder(hyCmd)
            hyPb.directory(filesDir)
            hyPb.environment()["LD_LIBRARY_PATH"] = libDir
            hyPb.redirectErrorStream(true)
            
            val p = hyPb.start()
            processes.add(p)
            captureProcessLog(p, "Hysteria-$port")
            tunnelTargets.add("127.0.0.1:$port")
        }
        
        logToApp("Waiting for cores to warm up...")
        Thread.sleep(1500)

        val lbCmd = mutableListOf(libLoadBin, "-lport", "7777", "-tunnel")
        lbCmd.addAll(tunnelTargets)
        
        val lbPb = ProcessBuilder(lbCmd)
        lbPb.directory(filesDir)
        lbPb.environment()["LD_LIBRARY_PATH"] = libDir
        lbPb.redirectErrorStream(true)
        
        val lbProcess = lbPb.start()
        processes.add(lbProcess)
        captureProcessLog(lbProcess, "LoadBalancer")
        logToApp("Load Balancer active on port 7777")
    }

    private fun disconnect() {
        Log.i("ZIVPN-Tun", "Stopping VPN and cores...")
        
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            logToApp("CPU Wakelock released")
        }
        wakeLock = null
        
        processes.forEach { 
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    it.destroyForcibly()
                } else {
                    it.destroy()
                }
            } catch(e: Exception){} 
        }
        processes.clear()

        // Clean up processes by name
        Thread {
            try {
                // killall/pkill might not work on all androids, but we try
                val cleanupCmd = arrayOf("sh", "-c", "pkill -9 libuz; pkill -9 libload; pkill -9 tun2socks")
                Runtime.getRuntime().exec(cleanupCmd).waitFor()
            } catch (e: Exception) {}
        }.start()

        vpnInterface?.close()
        vpnInterface = null
        
        val prefs = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
        prefs.edit().putBoolean("flutter.vpn_running", false).apply()
        
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }
}
