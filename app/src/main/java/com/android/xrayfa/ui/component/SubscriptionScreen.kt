package com.android.xrayfa.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.xrayfa.viewmodel.SubscriptionViewmodel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    viewmodel: SubscriptionViewmodel
) {
    var isBottomSheetShow by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
            title = {Text("subscription")},
            navigationIcon = {}
        )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier.padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            FloatingActionButton(
                onClick = {isBottomSheetShow = true},
                modifier = Modifier.align(BiasAlignment(1f,0.9f))
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = ""
                )
            }

            if (isBottomSheetShow) {
                ModalBottomSheet(onDismissRequest = { isBottomSheetShow = false }) {
                    Column {
                        Text("remark")
                    }
                }
            }
        }
    }
}