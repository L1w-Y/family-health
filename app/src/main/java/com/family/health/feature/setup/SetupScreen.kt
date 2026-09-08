// 首次启动配置：服务器地址 + 家庭口令 + 设备署名 → auth → 全量同步（契约：docs/02 §3.2）
package com.family.health.feature.setup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.family.health.data.AppViewModel
import com.family.health.ui.components.FhButton
import com.family.health.ui.components.FhTextField
import com.family.health.ui.components.PageTitle
import com.family.health.ui.theme.FhColors

/** 家庭服务器地址（固定，全家统一） */
private const val SERVER_URL = "http://43.161.199.183"

@Composable
fun SetupScreen(vm: AppViewModel) {
    val st by vm.setup.collectAsStateWithLifecycle()
    var secret by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(horizontal = 10.dp).verticalScroll(rememberScrollState())) {
        PageTitle("连接家庭服务器")
        Text(
            "同一家庭的所有设备使用同一个家庭口令。",
            fontSize = 13.sp, color = FhColors.Text2, lineHeight = 20.sp,
            modifier = Modifier.padding(bottom = 14.dp),
        )
        FhTextField(secret, { secret = it.trim() }, "家庭口令", placeholder = "家人约定的口令")
        FhTextField(name, { name = it.trim() }, "这台设备的署名（如：小枫的手机）")
        st.error?.let {
            Text(it, fontSize = 13.sp, color = FhColors.Amber, lineHeight = 19.sp,
                modifier = Modifier.padding(bottom = 10.dp))
        }
        FhButton(if (st.loading) "连接并同步中…" else "连 接", onClick = {
            if (!st.loading) vm.setup(SERVER_URL, secret, name)
        })
        Spacer(Modifier.height(20.dp))
    }
}
