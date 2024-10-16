package com.nnnn.myg.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.nnnn.myg.MainActivity
import com.nnnn.myg.app
import com.nnnn.myg.permission.canWriteExternalStorage
import com.nnnn.myg.permission.requiredPermission
import java.io.File

fun Context.shareFile(file: File, title: String) {
    val uri = FileProvider.getUriForFile(
        this, "${packageName}.provider", file
    )
    val intent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_STREAM, uri)
        type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    tryStartActivity(
        Intent.createChooser(
            intent, title
        )
    )
}

suspend fun MainActivity.saveFileToDownloads(file: File) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        requiredPermission(this, canWriteExternalStorage)
        val targetFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            file.name
        )
        targetFile.writeBytes(file.readBytes())
    } else {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        withContext(Dispatchers.IO) {
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("创建URI失败")
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(file.readBytes())
                outputStream.flush()
            }
        }
    }
    toast("已保存 ${file.name} 到下载")
}

fun Context.tryStartActivity(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
        LogUtils.d("tryStartActivity", e)
        // 在某些模拟器上/特定设备 ActivityNotFoundException
        toast(e.message ?: e.stackTraceToString())
    }
}

fun openA11ySettings() {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    app.tryStartActivity(intent)
}

fun Context.openUri(uri: String) {
    val u = try {
        Uri.parse(uri)
    } catch (e: Exception) {
        e.printStackTrace()
        toast("非法链接")
        return
    }
    val intent = Intent(Intent.ACTION_VIEW, u)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    tryStartActivity(intent)
}

fun Context.openApp(appId: String) {
    val intent = packageManager.getLaunchIntentForPackage(appId)
    if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        tryStartActivity(intent)
    } else {
        toast("请检查此应用是否安装")
    }
}