package com.example.braviacleaner

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.os.StatFs
import android.os.Environment
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

@Suppress("DEPRECATION")
class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var content: LinearLayout
    private var selectedPackage: String? = null
    private val protectedPrefixes = listOf("android", "com.android.", "com.google.android.gms", "com.google.android.gsf", "com.google.android.tvlauncher", "com.sony.dtv.", "com.mediatek.", "mtktvapi.", "com.uei.")
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        runOnUiThread { toast(if (result == android.content.pm.PackageManager.PERMISSION_GRANTED) "Shizuku authorized" else "Permission denied"); updateStatus() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        buildUi()
        dashboard()
    }
    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        worker.shutdownNow()
        super.onDestroy()
    }
    private fun label(s: String, size: Float = 17f): TextView = TextView(this).apply {
        text = s; textSize = size; setTextColor(0xffeeeeee.toInt()); setPadding(12, 12, 12, 12)
    }
    private fun button(text: String, action: () -> Unit): Button = Button(this).apply {
        this.text = text; isAllCaps = false; setOnClickListener { action() }
        setOnFocusChangeListener { v, focused -> v.alpha = if (focused) 1f else .85f }
    }
    private fun buildUi() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 20, 28, 12); setBackgroundColor(0xff101a2c.toInt()) }
        root.addView(label("BRAVIA Cleaner · Android TV", 25f))
        status = label("Checking Shizuku…", 14f); root.addView(status)
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bar.addView(button("Dashboard") { dashboard() })
        bar.addView(button("App manager") { appManager() })
        bar.addView(button("CPU snapshot") { cpuSnapshot() })
        bar.addView(button("Authorize Shizuku") { authorize() })
        root.addView(bar)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        updateStatus()
    }
    private fun updateStatus() {
        val connected = try { Shizuku.pingBinder() } catch (_: Throwable) { false }
        val permitted = connected && try { Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED } catch (_: Throwable) { false }
        status.text = "Shizuku: " + if (permitted) "connected and authorized" else if (connected) "connected; authorization needed" else "not running — start it using ADB"
    }
    private fun authorize() {
        if (!Shizuku.pingBinder()) { toast("Start Shizuku on the TV first; see README"); return }
        if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) Shizuku.requestPermission(100)
        else toast("Already authorized")
        updateStatus()
    }
    private fun reset(title: String) { content.removeAllViews(); content.addView(label(title, 22f)) }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    private fun dashboard() {
        reset("Memory and storage")
        val info = ActivityManager.MemoryInfo()
        (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        val fs = StatFs(Environment.getDataDirectory().absolutePath)
        fun gb(n: Long) = "%.2f GB".format(n / 1_073_741_824.0)
        content.addView(label("RAM available: ${gb(info.availMem)} / ${gb(info.totalMem)}"))
        content.addView(label("Low-memory warning: ${if (info.lowMemory) "YES" else "No"}"))
        content.addView(label("Internal free storage: ${gb(fs.availableBytes)} / ${gb(fs.totalBytes)}"))
        content.addView(label("Android keeps cached processes for speed. Don't repeatedly force-stop everything.", 15f))
        content.addView(button("Trim disposable system cache (Shizuku)") {
            confirm("Request Android to trim disposable caches?", "This asks the system to free cache space; it does not clear login data. Some firmware may reject the command.") {
                // pm trim-caches takes a target FREE space amount, not a number of bytes to delete.
                runShell("pm trim-caches 999G internal") { output -> toast(output.ifBlank { "Cache trim requested; refresh storage to see results" }); dashboard() }
            }
        })
        content.addView(button("Refresh") { dashboard() })
    }
    private fun appManager() {
        reset("Installed apps — choose one")
        val apps = packageManager.getInstalledApplications(0).sortedBy { packageManager.getApplicationLabel(it).toString().lowercase() }
        content.addView(label("${apps.size} visible apps. System packages are protected from destructive actions here.", 14f))
        apps.forEach { app ->
            val pkg = app.packageName
            val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val name = packageManager.getApplicationLabel(app).toString()
            content.addView(button("$name  ${if (isSystem) "[system]" else ""}\n$pkg") { appDetails(pkg, name, isSystem) })
        }
    }
    private fun isProtected(pkg: String): Boolean = protectedPrefixes.any { pkg == it || (it.endsWith('.') && pkg.startsWith(it)) } || pkg == packageName
    private fun appDetails(pkg: String, name: String, system: Boolean) {
        selectedPackage = pkg
        reset(name)
        content.addView(label(pkg, 15f))
        content.addView(button("Open Android app settings / Storage & cache") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
        })
        if (isProtected(pkg)) {
            content.addView(label("Protected: no force-stop or uninstall from this app. You can inspect it in Android Settings.", 15f))
        } else {
            content.addView(button("Force stop (Shizuku)") {
                confirm("Force stop $name?", "This may interrupt playback, notifications, or app services.") {
                    runShell("am force-stop $pkg") { result -> toast(result.ifBlank { "Force-stop requested" }) }
                }
            })
            if (!system) content.addView(button("Uninstall user app (Shizuku)") {
                confirm("Uninstall $name?", "App data may be lost. This action is not guaranteed to be reversible.") {
                    runShell("pm uninstall --user 0 $pkg") { result -> toast(result.ifBlank { "Uninstall command finished" }); appManager() }
                }
            })
            else content.addView(label("System app: uninstall intentionally disabled. Use Android Settings to disable it if supported.", 15f))
        }
        content.addView(button("Back to apps") { appManager() })
    }
    private fun cpuSnapshot() {
        reset("CPU snapshot (not a long-term measurement)")
        content.addView(label("Take several snapshots while the TV is idle. Look for processes consistently using CPU.", 15f))
        content.addView(button("Refresh CPU snapshot") { cpuSnapshot() })
        runShell("top -b -n 1 -m 15") { result ->
            content.addView(label(result.take(7000).ifBlank { "No output" }, 13f))
        }
    }
    private fun confirm(title: String, message: String, action: () -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setNegativeButton("Cancel", null).setPositiveButton("Proceed") { _, _ -> action() }.show()
    }
    private fun runShell(command: String, onDone: (String) -> Unit) {
        updateStatus()
        if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            toast("Authorize Shizuku first"); return
        }
        worker.execute {
            val output = try {
                // Legacy Shizuku remote-process API. Verify compatibility with your installed Shizuku release.
                val proc = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
                val stdout = proc.inputStream.bufferedReader().use { it.readText() }
                val stderr = proc.errorStream.bufferedReader().use { it.readText() }
                val exit = proc.waitFor()
                "Exit $exit\n$stdout$stderr".trim()
            } catch (e: Throwable) { "Command failed: ${e.javaClass.simpleName}: ${e.message}" }
            runOnUiThread { if (!isFinishing && !isDestroyed) onDone(output) }
        }
    }
}
