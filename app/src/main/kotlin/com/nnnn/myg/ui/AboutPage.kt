package com.nnnn.myg.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.nnnn.myg.META
import com.nnnn.myg.ui.style.EmptyHeight
import com.nnnn.myg.ui.style.itemPadding
import com.nnnn.myg.util.LocalNavController
import com.nnnn.myg.util.ProfileTransitions
import com.nnnn.myg.util.REPOSITORY_URL
import com.nnnn.myg.util.format
import com.nnnn.myg.util.openUri

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun AboutPage() {
    val navController = LocalNavController.current
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
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
                title = { Text(text = "关于") }
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
        ) {
            Column(
                modifier = Modifier
                    .clickable {
                        context.openUri(REPOSITORY_URL)
                    }
                    .fillMaxWidth()
                    .itemPadding()
            ) {
                Text(
                    text = "开源地址",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = REPOSITORY_URL,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .itemPadding()
            ) {
                Text(
                    text = "版本代码",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = META.versionCode.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .itemPadding()
            ) {
                Text(
                    text = "版本名称",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = META.versionName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                modifier = Modifier
                    .clickable {
                        context.openUri(META.commitUrl)
                    }
                    .fillMaxWidth()
                    .itemPadding()
            ) {
                Text(
                    text = "代码记录",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = META.commitId,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .itemPadding()
            ) {
                Text(
                    text = "提交时间",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = META.commitTime.format("yyyy-MM-dd HH:mm:ss ZZ"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .itemPadding()
            ) {
                Text(
                    text = "构建渠道",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = META.channel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }
}