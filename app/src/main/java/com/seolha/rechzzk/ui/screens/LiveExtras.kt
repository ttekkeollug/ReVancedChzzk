package com.seolha.rechzzk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seolha.rechzzk.Prefs
import com.seolha.rechzzk.net.Api
import com.seolha.rechzzk.net.Prediction
import com.seolha.rechzzk.player.Session
import com.seolha.rechzzk.ui.C
import com.seolha.rechzzk.ui.components.fmtCount
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Log = Color(0xFFB98A4E)     // 통나무 색
private val Cheese = Color(0xFFFFC857)

/** 채널 이름 아래: 통나무 보유량 + 받을 통나무 + 진행 중 투표 */
@Composable
fun PowerBar(onOpenPrediction: () -> Unit) {
    val power by Session.power.collectAsState()
    val prediction by Session.prediction.collectAsState()
    val autoClaim by Prefs.autoClaim.collectAsState()
    val p = power ?: return
    // 통나무를 쓰지 않는 채널이면 보유량·받을 것·투표가 모두 없을 때 숨김
    if (!p.active && p.amount == 0L && p.claims.isEmpty() && prediction == null) return
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("🪵 통나무 ${fmtCount(p.amount)}", Log)
        if (p.claims.isNotEmpty() && !autoClaim) {
            Pill("+${fmtCount(p.claims.sumOf { it.amount })} 받기", C.Accent, filled = true) { Session.claimAll() }
        } else if (autoClaim) {
            Text("자동 받기 켜짐", color = C.Dim, fontSize = 12.sp)
        }
        Spacer(Modifier.weight(1f))
        prediction?.let { pr ->
            val label = when (pr.status) { "ACTIVE" -> "투표 진행 중"; "EXPIRED" -> "투표 마감"; else -> "투표 결과" }
            Pill("🗳 $label", if (pr.status == "ACTIVE") C.Accent else C.Sub, filled = pr.status == "ACTIVE", onClick = onOpenPrediction)
        }
    }
}

@Composable
private fun Pill(text: String, color: Color, filled: Boolean = false, onClick: (() -> Unit)? = null) {
    Text(text, color = if (filled) Color.Black else color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(14.dp))
            .then(if (filled) Modifier.background(color) else Modifier.border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(14.dp)))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 5.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PredictionSheet(onClose: () -> Unit) {
    val pr by Session.prediction.collectAsState()
    val power by Session.power.collectAsState()
    ModalBottomSheet(onDismissRequest = onClose, containerColor = C.Surface) {
        val p = pr
        if (p == null) { Text("진행 중인 투표가 없습니다", color = C.Sub, modifier = Modifier.padding(24.dp)); return@ModalBottomSheet }
        PredictionContent(p, power?.amount ?: 0, onClose)
    }
}

@Composable
private fun PredictionContent(p: Prediction, balance: Long, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val joined = p.participation?.selectedOptionNo
    var selected by remember(p.predictionId) { mutableStateOf(joined) }
    var amountText by remember(p.predictionId) { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var remain by remember(p.predictionId, p.remainingDuration) { mutableLongStateOf(p.remainingDuration) }
    LaunchedEffect(p.predictionId, p.remainingDuration) {
        while (remain > 0) { delay(1000); remain -= 1000 }
    }
    val active = p.status == "ACTIVE" && remain > 0
    val total = p.optionList.sumOf { it.totalLogPowers }.coerceAtLeast(1)
    val already = p.participation?.bettingPowers ?: 0
    val amount = amountText.toLongOrNull() ?: 0
    // 공식 웹과 같은 규칙: 첫 참여 10 이상, 추가 참여 1 이상, 합계 1,000만 이하, 보유량 이하
    val error = when {
        amountText.isEmpty() -> null
        already == 0L && amount < 10 -> "처음 참여할 때는 10 이상 걸어야 합니다"
        already > 0 && amount < 1 -> "1 이상 입력하세요"
        already + amount > 10_000_000 -> "최대 1,000만까지 걸 수 있습니다"
        amount > balance -> "통나무가 부족합니다"
        else -> null
    }

    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
        Text(p.predictionTitle, color = C.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                active -> "남은 시간 ${remain / 60000}분 ${(remain / 1000) % 60}초 · 보유 통나무 ${fmtCount(balance)}"
                p.status == "COMPLETED" -> "결과 발표"
                else -> "참여 마감 · 결과를 기다리는 중"
            }, color = C.Sub, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        p.optionList.forEach { o ->
            val pct = o.totalLogPowers * 100f / total
            val isSel = selected == o.optionNo
            val won = p.winningOptionNo == o.optionNo
            val canPick = active && joined == null
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp))
                .border(1.5.dp, if (isSel || won) C.Accent else C.Surface2, RoundedCornerShape(10.dp))
                .then(if (canPick) Modifier.clickable { selected = o.optionNo } else Modifier)) {
                Box(Modifier.matchParentSize()) {   // 득표율 막대
                    Box(Modifier.fillMaxHeight().fillMaxWidth(pct / 100f).background(C.Accent.copy(alpha = 0.12f)))
                }
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text((if (won) "🏆 " else "") + o.optionText, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${"%.1f".format(pct)}%", color = if (isSel) C.Accent else C.Sub, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("👤 ${fmtCount(o.participantCount)}  🪵 ${fmtCount(o.totalLogPowers)}", color = C.Dim, fontSize = 12.sp)
                }
            }
        }
        if (already > 0) {
            Spacer(Modifier.height(6.dp))
            Text("내 참여: ${fmtCount(already)} 통나무", color = C.Accent, fontSize = 14.sp)
        }
        if (active && selected != null) {
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf(10L, 100L, 1000L, 10000L)) { v ->
                    Pill("+${fmtCount(v)}", C.Sub) { amountText = ((amountText.toLongOrNull() ?: 0) + v).toString() }
                }
                item { Pill("전부", C.Sub) { amountText = balance.coerceAtMost(10_000_000 - already).toString() } }
            }
            Spacer(Modifier.height(8.dp))
            NumberField(amountText, "걸 통나무") { amountText = it }
            (error ?: message)?.let { Text(it, color = if (error != null) C.Live else C.Accent, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
            Spacer(Modifier.height(10.dp))
            ActionButton(if (busy) "참여 중…" else "${fmtCount(amount)} 통나무로 참여", enabled = !busy && error == null && amount > 0) {
                busy = true
                scope.launch {
                    message = Session.participate(selected!!, amount)
                    amountText = ""; busy = false
                }
            }
        } else message?.let { Text(it, color = C.Accent, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(6.dp))
        Text("닫기", color = C.Sub, modifier = Modifier.fillMaxWidth().clickable(onClick = onClose).padding(12.dp))
    }
}

/** 치즈 후원. 실제 결제(치즈 차감)가 일어나므로 금액 확인 단계를 한 번 더 거침 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheeseSheet(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var balance by remember { mutableStateOf<Long?>(null) }
    var balanceError by remember { mutableStateOf<String?>(null) }
    var amountText by remember { mutableStateOf("1000") }
    var text by remember { mutableStateOf("") }
    var anonymous by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { Api.cheeseBalance() }.onSuccess { balance = it }.onFailure { balanceError = it.message }
    }
    val amount = amountText.toLongOrNull() ?: 0
    val error = when {
        amount < 1000 -> "최소 1,000치즈부터 후원할 수 있습니다"
        balance != null && amount > balance!! -> "치즈가 부족합니다 (보유 ${fmtCount(balance!!)})"
        else -> null
    }

    ModalBottomSheet(onDismissRequest = onClose, containerColor = C.Surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text("🧀 치즈 후원", color = C.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(balance?.let { "보유 치즈 ${fmtCount(it)}" } ?: balanceError ?: "잔액 확인 중…", color = C.Sub, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            if (result != null) {
                Text(result!!, color = C.Accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                ActionButton("닫기", true, onClose)
                return@Column
            }
            if (!confirm) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf(1000L, 5000L, 10000L, 50000L)) { v ->
                        Pill(fmtCount(v), Cheese, filled = amount == v) { amountText = v.toString() }
                    }
                }
                Spacer(Modifier.height(8.dp))
                NumberField(amountText, "후원할 치즈") { amountText = it }
                Spacer(Modifier.height(8.dp))
                TextField(text, { text = it.take(100) }, placeholder = { Text("후원 메시지 (선택)") }, singleLine = false,
                    shape = RoundedCornerShape(10.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().clickable { anonymous = !anonymous }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("익명으로 후원", color = C.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Switch(anonymous, { anonymous = it }, colors = SwitchDefaults.colors(checkedTrackColor = C.Accent, checkedThumbColor = Color.Black))
                }
                error?.let { Text(it, color = C.Live, fontSize = 13.sp) }
                Spacer(Modifier.height(10.dp))
                ActionButton("다음", error == null) { confirm = true }
            } else {
                Text("${fmtCount(amount)}치즈를 후원할까요?", color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (text.isNotBlank()) Text("“$text”", color = C.Sub, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                Text("후원한 치즈는 되돌릴 수 없습니다.", color = C.Dim, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(14.dp))
                ActionButton(if (busy) "후원 중…" else "${fmtCount(amount)}치즈 후원하기", !busy) {
                    busy = true
                    scope.launch { result = Session.donate(amount, text.trim(), anonymous); busy = false }
                }
                Text("취소", color = C.Sub, modifier = Modifier.fillMaxWidth().clickable { confirm = false }.padding(12.dp))
            }
        }
    }
}

@Composable
private fun fieldColors() = TextFieldDefaults.colors(
    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
    focusedContainerColor = C.Surface2, unfocusedContainerColor = C.Surface2, cursorColor = C.Accent)

@Composable
private fun NumberField(value: String, label: String, onChange: (String) -> Unit) {
    TextField(value, { onChange(it.filter(Char::isDigit).take(9)) }, placeholder = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(10.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth())
}

@Composable
private fun ActionButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Text(text, color = if (enabled) Color.Black else C.Dim, fontSize = 16.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (enabled) C.Accent else C.Surface2)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 14.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}
