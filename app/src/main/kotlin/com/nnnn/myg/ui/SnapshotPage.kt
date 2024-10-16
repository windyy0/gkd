package com.nnnn.myg.ui

import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.ImageUtils
import com.blankj.utilcode.util.UriUtils
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ImagePreviewPageDestination
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.nnnn.myg.MainActivity
import com.nnnn.myg.data.GithubPoliciesAsset
import com.nnnn.myg.data.Snapshot
import com.nnnn.myg.db.DbSet
import com.nnnn.myg.debug.SnapshotExt
import com.nnnn.myg.permission.canWriteExternalStorage
import com.nnnn.myg.permission.requiredPermission
import com.nnnn.myg.ui.component.EmptyText
import com.nnnn.myg.ui.component.StartEllipsisText
import com.nnnn.myg.ui.component.waitResult
import com.nnnn.myg.ui.style.EmptyHeight
import com.nnnn.myg.util.IMPORT_SHORT_URL
import com.nnnn.myg.util.LocalNavController
import com.nnnn.myg.util.ProfileTransitions
import com.nnnn.myg.util.launchAsFn
import com.nnnn.myg.util.saveFileToDownloads
import com.nnnn.myg.util.shareFile
import com.nnnn.myg.util.snapshotZipDir
import com.nnnn.myg.util.throttle
import com.nnnn.myg.util.toast

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun SnapshotPage() {
    val context = LocalContext.current as MainActivity
    val navController = LocalNavController.current
    val colorScheme = MaterialTheme.colorScheme

    val vm = viewModel<SnapshotVm>()
    val snapshots by vm.snapshotsState.collectAsState()

    vm.uploadOptions.ShowDialog()

    var selectedSnapshot by remember {
        mutableStateOf<Snapshot?>(null)
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        TopAppBar(scrollBehavior = scrollBehavior,
            navigationIcon = {
                IconButton(onClick = {
                    navController.popBackStack()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                    )
                }
            },
            title = { Text(text = if (snapshots.isEmpty()) "快照记录" else "快照记录-${snapshots.size}") },
            actions = {
                if (snapshots.isNotEmpty()) {
                    IconButton(onClick = throttle(fn = vm.viewModelScope.launchAsFn(Dispatchers.IO) {
                        context.mainVm.dialogFlow.waitResult(
                            title = "删除记录",
                            text = "确定删除全部快照记录?",
                            error = true,
                        )
                        snapshots.forEach { s ->
                            SnapshotExt.removeAssets(s.id)
                        }
                        DbSet.snapshotDao.deleteAll()
                    })) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null,
                        )
                    }
                }
            })
    }, content = { contentPadding ->
        LazyColumn(
            modifier = Modifier.padding(contentPadding),
        ) {
            items(snapshots, { it.id }) { snapshot ->
                if (snapshot.id != snapshots.firstOrNull()?.id) {
                    HorizontalDivider()
                }
                Column(modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        selectedSnapshot = snapshot
                    }
                    .padding(10.dp)) {
                    Row {
                        Text(
                            text = snapshot.date,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = snapshot.appName ?: snapshot.appId ?: snapshot.id.toString(),
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    val showActivityId = if (snapshot.activityId != null) {
                        if (snapshot.appId != null && snapshot.activityId.startsWith(
                                snapshot.appId
                            )
                        ) {
                            snapshot.activityId.substring(snapshot.appId.length)
                        } else {
                            snapshot.activityId
                        }
                    } else {
                        null
                    }
                    if (showActivityId != null) {
                        StartEllipsisText(text = showActivityId)
                    } else {
                        Text(text = "null", color = LocalContentColor.current.copy(alpha = 0.5f))
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(EmptyHeight))
                if (snapshots.isEmpty()) {
                    EmptyText(text = "暂无记录")
                }
            }
        }

    })

    selectedSnapshot?.let { snapshotVal ->
        Dialog(onDismissRequest = { selectedSnapshot = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                val modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                Text(
                    text = "查看", modifier = Modifier
                        .clickable(onClick = throttle(fn = vm.viewModelScope.launchAsFn {
                            navController
                                .toDestinationsNavigator()
                                .navigate(
                                    ImagePreviewPageDestination(
                                        title = snapshotVal.appName,
                                        uri = snapshotVal.screenshotFile.absolutePath,
                                    )
                                )
                            selectedSnapshot = null
                        }))
                        .then(modifier)
                )
                HorizontalDivider()
                Text(
                    text = "分享到其他应用",
                    modifier = Modifier
                        .clickable(onClick = throttle(fn = vm.viewModelScope.launchAsFn {
                            selectedSnapshot = null
                            val zipFile = SnapshotExt.getSnapshotZipFile(
                                snapshotVal.id,
                                snapshotVal.appId,
                                snapshotVal.activityId
                            )
                            context.shareFile(zipFile, "分享快照文件")
                        }))
                        .then(modifier)
                )
                HorizontalDivider()
                Text(
                    text = "保存到下载",
                    modifier = Modifier
                        .clickable(onClick = throttle(fn = vm.viewModelScope.launchAsFn {
                            selectedSnapshot = null
                            val zipFile = SnapshotExt.getSnapshotZipFile(
                                snapshotVal.id,
                                snapshotVal.appId,
                                snapshotVal.activityId
                            )
                            context.saveFileToDownloads(zipFile)
                        }))
                        .then(modifier)
                )
                HorizontalDivider()
                if (snapshotVal.githubAssetId != null) {
                    Text(
                        text = "复制链接", modifier = Modifier
                            .clickable(onClick = throttle {
                                selectedSnapshot = null
                                ClipboardUtils.copyText(IMPORT_SHORT_URL + snapshotVal.githubAssetId)
                                toast("复制成功")
                            })
                            .then(modifier)
                    )
                } else {
                    Text(
                        text = "生成链接(需科学上网)", modifier = Modifier
                            .clickable(onClick = throttle {
                                selectedSnapshot = null
                                vm.uploadOptions.startTask(
                                    getFile = { SnapshotExt.getSnapshotZipFile(snapshotVal.id) },
                                    onSuccessResult = vm.viewModelScope.launchAsFn<GithubPoliciesAsset>(
                                        Dispatchers.IO
                                    ) {
                                        DbSet.snapshotDao.update(snapshotVal.copy(githubAssetId = it.id))
                                    }
                                )
                            })
                            .then(modifier)
                    )
                }
                HorizontalDivider()

                Text(
                    text = "保存截图到相册",
                    modifier = Modifier
                        .clickable(onClick = throttle(fn = vm.viewModelScope.launchAsFn {
                            requiredPermission(context, canWriteExternalStorage)
                            ImageUtils.save2Album(
                                ImageUtils.getBitmap(snapshotVal.screenshotFile),
                                Bitmap.CompressFormat.PNG,
                                true
                            )
                            toast("保存成功")
                            selectedSnapshot = null
                        }))
                        .then(modifier)
                )
                HorizontalDivider()
                Text(
                    text = "替换截图(去除隐私)",
                    modifier = Modifier
                        .clickable(onClick = throttle(fn = vm.viewModelScope.launchAsFn {
                            val uri = context.pickContentLauncher.launchForImageResult()
                            withContext(Dispatchers.IO) {
                                val oldBitmap = ImageUtils.getBitmap(snapshotVal.screenshotFile)
                                val newBytes = UriUtils.uri2Bytes(uri)
                                val newBitmap = ImageUtils.getBitmap(newBytes, 0)
                                if (oldBitmap.width == newBitmap.width && oldBitmap.height == newBitmap.height) {
                                    snapshotVal.screenshotFile.writeBytes(newBytes)
                                    snapshotZipDir
                                        .listFiles { f -> f.isFile && f.name.endsWith("${snapshotVal.id}.zip") }
                                        ?.forEach { f ->
                                            f.delete()
                                        }
                                    if (snapshotVal.githubAssetId != null) {
                                        // 当本地快照变更时, 移除快照链接
                                        DbSet.snapshotDao.deleteGithubAssetId(snapshotVal.id)
                                    }
                                } else {
                                    toast("截图尺寸不一致,无法替换")
                                    return@withContext
                                }
                            }
                            toast("替换成功")
                            selectedSnapshot = null
                        }))
                        .then(modifier)
                )
                HorizontalDivider()
                Text(
                    text = "删除", modifier = Modifier
                        .clickable(onClick = throttle(fn = vm.viewModelScope.launchAsFn {
                            DbSet.snapshotDao.delete(snapshotVal)
                            withContext(Dispatchers.IO) {
                                SnapshotExt.removeAssets(snapshotVal.id)
                            }
                            selectedSnapshot = null
                        }))
                        .then(modifier), color = colorScheme.error
                )
            }
        }
    }
}


