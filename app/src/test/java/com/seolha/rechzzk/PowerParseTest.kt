package com.seolha.rechzzk

import com.seolha.rechzzk.net.ApiResponse
import com.seolha.rechzzk.net.Http
import com.seolha.rechzzk.net.LogPower
import com.seolha.rechzzk.net.Prediction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 실제 서버 응답 샘플로 모델 파싱 검증 */
class PowerParseTest {
    @Test fun parse() {
        val lp = Http.json.decodeFromString(ApiResponse.serializer(LogPower.serializer()),
            """{"code":200,"message":null,"content":{"channelId":"bb38","active":true,"amount":120,"exposureWeeklyRanking":true,"claims":[{"claimId":12345,"claimType":"WATCH_5_MIN","amount":10},{"claimId":"abc","claimType":"FOLLOW","amount":50}]}}""").content!!
        assertEquals(2, lp.claims.size); assertEquals("12345", lp.claims[0].claimId); assertEquals(120L, lp.amount)

        val none = Http.json.decodeFromString(ApiResponse.serializer(Prediction.serializer()),
            """{"code":200,"message":null,"content":{"channelId":"bb","predictionId":null,"totalLogPowers":0,"predictionTitle":null,"totalDuration":0,"remainingDuration":0,"status":null}}""").content!!
        assertEquals("", none.predictionId); assertEquals("", none.status)

        val pr = Http.json.decodeFromString(ApiResponse.serializer(Prediction.serializer()),
            """{"code":200,"content":{"channelId":"ae","predictionId":"5d7958a83315b4ff0ac5","totalLogPowers":1589627,"predictionTitle":"8시간안에 클리어 할 수 있을까요?","totalDuration":3600000,"remainingDuration":0,"status":"EXPIRED","optionList":[{"optionNo":1,"optionText":"한다","totalLogPowers":900000,"participantCount":30},{"optionNo":2,"optionText":"못한다","totalLogPowers":689627,"participantCount":21}],"participation":{"selectedOptionNo":2,"bettingPowers":100},"winningOptionNo":null}}""").content!!
        assertEquals("EXPIRED", pr.status); assertEquals(2, pr.optionList.size); assertEquals(2, pr.participation?.selectedOptionNo); assertNull(pr.winningOptionNo)
        println("OK power=${lp} prediction=${pr.predictionTitle}")
    }
}
