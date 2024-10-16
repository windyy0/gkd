package com.nnnn.myg.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.blankj.utilcode.util.ClipboardUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import com.nnnn.myg.data.GithubPoliciesAsset
import com.nnnn.myg.util.LoadStatus
import com.nnnn.myg.util.launchTry
import com.nnnn.myg.util.privacyStoreFlow
import com.nnnn.myg.util.toast
import com.nnnn.myg.util.uploadFileToGithub
import java.io.File

class UploadOptions(
    private val scope: CoroutineScope,
    private val showHref: (GithubPoliciesAsset) -> String = { it.shortHref }
) {
    private val statusFlow = MutableStateFlow<LoadStatus<GithubPoliciesAsset>?>(null)
    private var job: Job? = null
    private fun buildTask(
        cookie: String,
        getFile: suspend () -> File,
        onSuccessResult: ((GithubPoliciesAsset) -> Unit)?
    ) = scope.launchTry(Dispatchers.IO) {
        statusFlow.value = LoadStatus.Loading()
        try {
            val policiesAsset = uploadFileToGithub(cookie, getFile()) {
                if (statusFlow.value is LoadStatus.Loading) {
                    statusFlow.value = LoadStatus.Loading(it)
                }
            }
            statusFlow.value = LoadStatus.Success(policiesAsset)
            onSuccessResult?.invoke(policiesAsset)
        } catch (e: Exception) {
            statusFlow.value = LoadStatus.Failure(e)
        } finally {
            job = null
        }
    }

    fun startTask(
        getFile: suspend () -> File,
        onSuccessResult: ((GithubPoliciesAsset) -> Unit)? = null
    ) {
        val cookie = privacyStoreFlow.value.githubCookie
        if (cookie.isNullOrBlank()) {
            toast("请先设置 cookie 后再上传")
            return
        }
        if (job != null || statusFlow.value is LoadStatus.Loading) {
            return
        }
        job = buildTask(cookie, getFile, onSuccessResult)
    }

    private fun stopTask() {
        if (statusFlow.value is LoadStatus.Loading && job != null) {
            job?.cancel("您取消了上传")
            job = null
        }
    }


    @Composable
    fun ShowDialog() {
        when (val status = statusFlow.collectAsState().value) {
            null -> {}
            is LoadStatus.Loading -> {
                AlertDialog(
                    title = { Text(text = "上传文件中") },
                    text = {
                        LinearProgressIndicator(
                            progress = { status.progress },
                        )
                    },
                    onDismissRequest = { },
                    confirmButton = {
                        TextButton(onClick = {
                            stopTask()
                        }) {
                            Text(text = "终止上传")
                        }
                    },
                )
            }

            is LoadStatus.Success -> {
                val href = showHref(status.result)
                AlertDialog(title = { Text(text = "上传完成") }, text = {
                    Text(text = href)
                }, onDismissRequest = {}, dismissButton = {
                    TextButton(onClick = {
                        statusFlow.value = null
                    }) {
                        Text(text = "关闭")
                    }
                }, confirmButton = {
                    TextButton(onClick = {
                        ClipboardUtils.copyText(href)
                        toast("复制成功")
                        statusFlow.value = null
                    }) {
                        Text(text = "复制并关闭")
                    }
                })
            }

            is LoadStatus.Failure -> {
                AlertDialog(
                    title = { Text(text = "上传失败") },
                    text = {
                        Text(text = status.exception.let {
                            it.message ?: it.toString()
                        })
                    },
                    onDismissRequest = { statusFlow.value = null },
                    confirmButton = {
                        TextButton(onClick = {
                            statusFlow.value = null
                        }) {
                            Text(text = "关闭")
                        }
                    },
                )
            }
        }
    }
}
