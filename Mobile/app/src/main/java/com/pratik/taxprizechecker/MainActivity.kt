package com.pratik.taxprizechecker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.pratik.taxprizechecker.ui.theme.TaxPrizeCheckerTheme
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.regex.Pattern
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.concurrent.thread
import com.google.gson.Gson
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen



class MainActivity : ComponentActivity() {
    private val client = OkHttpClient()

    private val apiUrl =
        "https://prize.ird.gov.np/api/v1/public/winners"
    private var coupons by mutableStateOf<List<String>>(emptyList())
    private var results by mutableStateOf<List<PrizeResult>>(emptyList())
    private var isChecking by mutableStateOf(false)
    private var hasChecked by mutableStateOf(false)
    private val imagePicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {

                coupons = emptyList()
                results = emptyList()
                hasChecked = false
                isChecking = false

                readTextFromImage(uri)
            }
        }

    private fun checkCoupons() {

        isChecking = true

        thread {

            try {

                val allWinners = getAllWinners()

                val matches = allWinners.filter { winner ->
                    winner.coupon in coupons
                }

                runOnUiThread {

                    results = matches
                    hasChecked = true
                    isChecking = false

                }

            } catch (e: Exception) {

                runOnUiThread {

                    isChecking = false

                    Toast.makeText(
                        this,
                        "API error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun getWinners(
        offset: Int
    ): WinnerResponse? {

        val request = Request.Builder()
            .url("$apiUrl?limit=100&offset=$offset")
            .build()

        return try {

            client.newCall(request).execute().use { response ->

                val body = response.body?.string()

                if (body != null) {
                    Gson().fromJson(
                        body,
                        WinnerResponse::class.java
                    )
                } else {
                    null
                }
            }

        } catch (e: Exception) {

            null
        }
    }

    private fun getAllWinners(): List<PrizeResult> {

        val allWinners = mutableListOf<PrizeResult>()

        var offset = 0
        val limit = 100

        while (true) {

            val data = getWinners(offset) ?: break

            for (draw in data.draws) {

                for (winner in draw.winners) {

                    allWinners.add(
                        PrizeResult(
                            coupon = winner.prize_coupon_number,
                            rank = winner.winner_rank,
                            category = draw.category_title_en,
                            title = draw.title_en,
                            eligibleFrom = draw.eligible_from,
                            eligibleTo = draw.eligible_to
                        )
                    )
                }
            }

            if (!data.has_more) {
                break
            }

            offset += limit
        }

        return allWinners
    }

    private fun readTextFromImage(uri: Uri) {

        val image = InputImage.fromFilePath(this, uri)

        val recognizer = TextRecognition.getClient(
            TextRecognizerOptions.DEFAULT_OPTIONS
        )

        recognizer.process(image)
            .addOnSuccessListener { visionText ->

                val detectedText = visionText.text

                val pattern = Pattern.compile("\\b\\d{12}\\b")
                val matcher = pattern.matcher(detectedText)

                val coupons = mutableListOf<String>()

                while (matcher.find()) {
                    coupons.add(matcher.group())
                }

                if (coupons.isNotEmpty()) {

                    this@MainActivity.coupons = coupons.distinct()

                } else {

                    Toast.makeText(
                        this,
                        "No 12-digit coupons found",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .addOnFailureListener { exception ->

                Toast.makeText(
                    this,
                    "OCR failed: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        installSplashScreen()

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            TaxPrizeCheckerTheme {
                PrizeCheckerScreen(
                    coupons = coupons,
                    results = results,
                    isChecking = isChecking,
                    hasChecked = hasChecked,
                    onSelectImage = {
                        imagePicker.launch("image/*")
                    },
                    onCheckCoupons = {
                        checkCoupons()
                    }
                )
            }
        }
    }

    @Composable
    fun PrizeCheckerScreen(
        coupons: List<String>,
        results: List<PrizeResult>,
        isChecking: Boolean,
        hasChecked: Boolean,
        onSelectImage: () -> Unit,
        onCheckCoupons: () -> Unit
    ) {
        val background = Color(0xFF07110F)
        val surface = Color(0xFF0D1C19)
        val surfaceLight = Color(0xFF142722)
        val accent = Color(0xFF35D0A0)
        val accentDark = Color(0xFF1B8F70)
        val textPrimary = Color(0xFFF2FAF7)
        val textSecondary = Color(0xFF91AAA3)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
        ) {

            Spacer(modifier = Modifier.height(30.dp))

            // ─────────────────────────────
            // TOP BAR
            // ─────────────────────────────

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Column {

                    Text(
                        text = "Tax Incentive",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        letterSpacing = 1.5.sp
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = "Prize Checker",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = textPrimary,
                        letterSpacing = (-0.8).sp
                    )
                }

                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(surfaceLight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "रु",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Turn your tax payment screenshot\ninto a prize check in seconds.",
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = textSecondary
            )

            Spacer(modifier = Modifier.height(30.dp))

            // ─────────────────────────────
            // HERO / UPLOAD AREA
            // ─────────────────────────────

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(
                    containerColor = surface
                )
            ) {

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF12362D),
                                    Color(0xFF0D1C19),
                                    Color(0xFF101A18)
                                )
                            )
                        )
                        .padding(24.dp)
                ) {

                    Column {

                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(accent.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "☘",
                                fontSize = 30.sp,
                                color = accent
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "Check your\nwinning chances.",
                            fontSize = 27.sp,
                            lineHeight = 31.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = textPrimary,
                            letterSpacing = (-0.7).sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Upload an eSewa tax payment screenshot.\nWe'll find your coupon numbers automatically.",
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            color = textSecondary
                        )

                        Spacer(modifier = Modifier.height(22.dp))

                        Button(
                            onClick = onSelectImage,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = Color(0xFF04120E)
                            )
                        ) {

                            Text(
                                text = "Choose Screenshot",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = "→",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ─────────────────────────────
            // DETECTED COUPONS
            // ─────────────────────────────

            if (coupons.isNotEmpty()) {

                Spacer(modifier = Modifier.height(30.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {

                        Text(
                            text = "DETECTED",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = accent,
                            letterSpacing = 1.5.sp
                        )

                        Spacer(modifier = Modifier.height(3.dp))

                        Text(
                            text = "Coupon numbers",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(accent.copy(alpha = 0.12f))
                            .padding(horizontal = 13.dp, vertical = 7.dp)
                    ) {

                        Text(
                            text = "${coupons.size} found",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                coupons.forEachIndexed { index, coupon ->

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 9.dp),
                        shape = RoundedCornerShape(17.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = surface
                        )
                    ) {

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Text(
                                text = String.format("%02d", index + 1),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = accent
                            )

                            Spacer(modifier = Modifier.width(15.dp))

                            Column {

                                Text(
                                    text = "TAX PRIZE COUPON",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = textSecondary
                                )

                                Spacer(modifier = Modifier.height(3.dp))

                                Text(
                                    text = coupon,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textPrimary,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ─────────────────────────────
                // CHECK BUTTON
                // ─────────────────────────────

                Button(
                    onClick = onCheckCoupons,
                    enabled = coupons.isNotEmpty() && !isChecking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = Color(0xFF04120E),
                        disabledContainerColor = accent.copy(alpha = 0.20f),
                        disabledContentColor = textSecondary
                    )
                ) {

                    if (isChecking) {

                        CircularProgressIndicator(
                            modifier = Modifier.size(21.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.width(11.dp))

                        Text(
                            text = "Searching IRD records...",
                            fontWeight = FontWeight.Bold
                        )

                    } else {

                        Text(
                            text = "Check Prize",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "→",
                            fontSize = 21.sp
                        )
                    }
                }
            }

            // ─────────────────────────────
            // WINNER RESULTS
            // ─────────────────────────────

            if (results.isNotEmpty()) {

                Spacer(modifier = Modifier.height(34.dp))

                Text(
                    text = "RESULT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    letterSpacing = 1.5.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Congratulations",
                    fontSize = 27.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = textPrimary,
                    letterSpacing = (-0.5).sp
                )

                Spacer(modifier = Modifier.height(15.dp))

                results.forEach { result ->

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF102B23)
                        )
                    ) {

                        Column(
                            modifier = Modifier.padding(23.dp)
                        ) {

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {

                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {

                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(accent.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "✓",
                                            fontSize = 23.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = accent
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column {

                                        Text(
                                            text = "WINNING COUPON",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = accent,
                                            letterSpacing = 1.2.sp
                                        )

                                        Text(
                                            text = "Rank #${result.rank}",
                                            fontSize = 12.sp,
                                            color = textSecondary
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(22.dp))

                            Text(
                                text = result.coupon,
                                fontSize = 25.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = textPrimary,
                                letterSpacing = 1.2.sp
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            HorizontalDivider(
                                color = Color.White.copy(alpha = 0.08f)
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            Text(
                                text = "PRIZE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = textSecondary,
                                letterSpacing = 1.3.sp
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = result.category,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = accent
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            Text(
                                text = "DRAW",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = textSecondary,
                                letterSpacing = 1.3.sp
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = result.title,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = textPrimary
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            Text(
                                text = "ELIGIBLE PERIOD",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = textSecondary,
                                letterSpacing = 1.3.sp
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "${result.eligibleFrom}  →  ${result.eligibleTo}",
                                fontSize = 13.sp,
                                color = textPrimary
                            )
                        }
                    }
                }
            }

            // ─────────────────────────────
            // NO WINNER
            // ─────────────────────────────

            if (hasChecked && results.isEmpty()) {

                Spacer(modifier = Modifier.height(32.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = surface
                    )
                ) {

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        Text(
                            text = "○",
                            fontSize = 42.sp,
                            color = textSecondary
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "No prize this time",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )

                        Spacer(modifier = Modifier.height(7.dp))

                        Text(
                            text = "None of your coupons matched\nthe available IRD prize records.",
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            color = textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}