
package com.meekamoodz.imagepdfviewer

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class MainActivity : Activity() {

    private val selectedImages = mutableListOf<Uri>()

    private var lastSavedPdf: Uri? = null
    private var pendingDestination: Uri? = null

    private lateinit var imageStatus: TextView
    private lateinit var adView: AdView

    private var interstitialAd: InterstitialAd? = null
    private var appOpenAd: AppOpenAd? = null
    private var rewardedAd: RewardedAd? = null

    private var activityResumed = false
    private var lastAppOpenAdTime = 0L
    private var rewardEarned = false

    companion object {
        private const val PICK_IMAGES_REQUEST = 100
        private const val CREATE_PDF_REQUEST = 101

        private const val PRIVACY_POLICY_URL =
            "https://sites.google.com/view/droptechdevelopers/image-to-pdf-privacy-policy"

        private const val BANNER_AD_UNIT_ID =
            "ca-app-pub-1083885756946416/5224479810"

        private const val INTERSTITIAL_AD_UNIT_ID =
            "ca-app-pub-1083885756946416/7906483473"

        private const val APP_OPEN_AD_UNIT_ID =
            "ca-app-pub-1083885756946416/8066090568"

        private const val REWARDED_AD_UNIT_ID =
            "ca-app-pub-1083885756946416/3642348560"

        private const val APP_OPEN_INTERVAL_MS =
            4 * 60 * 60 * 1000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createScreen()

        MobileAds.initialize(this) {
            runOnUiThread {
                loadBannerAd()
                loadInterstitialAd()
                loadAppOpenAd()
                loadRewardedAd()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activityResumed = true
        showAppOpenAdIfAvailable()
    }

    override fun onPause() {
        activityResumed = false
        super.onPause()
    }

    private fun createScreen() {
        val page = Color.rgb(16, 22, 42)
        val panel = Color.rgb(29, 38, 65)
        val purple = Color.rgb(119, 87, 255)
        val blue = Color.rgb(74, 166, 255)
        val pale = Color.rgb(220, 226, 247)
        val muted = Color.rgb(166, 178, 211)

        window.statusBarColor = page
        window.navigationBarColor = page
        window.decorView.systemUiVisibility = 0

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(page)
            setPadding(18, 12, 18, 8)
        }

        val scrollView = ScrollView(this).apply {
            clipToPadding = false
            isFillViewport = true
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 4, 0, 16)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(2, 10, 2, 18)
        }

        val logo = TextView(this).apply {
            text = "▣"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = roundedBackground(purple, 18)
            layoutParams = LinearLayout.LayoutParams(58, 58)
        }

        val headingColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 0, 0, 0)
        }

        headingColumn.addView(TextView(this).apply {
            text = "Image to PDF"
            textSize = 23f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        headingColumn.addView(TextView(this).apply {
            text = "VIEWER CONVERTER"
            textSize = 11f
            letterSpacing = 0.12f
            setTextColor(muted)
            setPadding(0, 3, 0, 0)
        })

        header.addView(logo)
        header.addView(headingColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(header)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(20, 24, 20, 22)
            background = roundedBackground(panel, 24)
        }

        hero.addView(TextView(this).apply {
            text = "✦"
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(173, 157, 255))
        })
        hero.addView(TextView(this).apply {
            text = "Turn photos into PDFs"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 8, 0, 6)
        })
        hero.addView(TextView(this).apply {
            text = "Select your pictures and combine them into one document in just a few taps."
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(pale)
            setPadding(4, 0, 4, 18)
        })

        imageStatus = TextView(this).apply {
            text = "No images selected yet"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(muted)
            setPadding(12, 12, 12, 16)
        }
        hero.addView(imageStatus)
        hero.addView(makeButton("＋   Select Images", purple) { selectImagesWithAd() })
        content.addView(hero)

        content.addView(TextView(this).apply {
            text = "QUICK ACTIONS"
            textSize = 12f
            letterSpacing = 0.12f
            setTextColor(muted)
            setPadding(4, 24, 4, 10)
        })

        content.addView(makeButton("▤   Create PDF", blue) { createPdf() })
        content.addView(makeButton("▣   Open Last Saved PDF", panel, pale) { openLastSavedPdf() })
        content.addView(makeButton("♡   Privacy Policy", panel, pale) { openPrivacyPolicy() })

        content.addView(TextView(this).apply {
            text = "Simple • Fast • Easy to use"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(muted)
            setPadding(8, 18, 8, 8)
        })

        scrollView.addView(content)
        root.addView(scrollView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val adContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, 6, 0, 2)
        }
        adView = AdView(this)
        adContainer.addView(adView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(adContainer, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        setContentView(root)
    }

    private fun roundedBackground(color: Int, radiusDp: Int): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusDp * resources.displayMetrics.density
        }
    }

    private fun makeButton(
        buttonText: String,
        backgroundColor: Int,
        textColor: Int = Color.WHITE,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            text = buttonText
            textSize = 15f
            isAllCaps = false
            setTextColor(textColor)
            background = roundedBackground(backgroundColor, 16)
            stateListAnimator = null
            elevation = 0f
            minHeight = (54 * resources.displayMetrics.density).toInt()
            setPadding(16, 10, 16, 10)
            setOnClickListener { action() }
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 5, 0, 5)
            layoutParams = params
        }
    }

    // INTERSTITIAL AD

    private fun loadInterstitialAd() {
        InterstitialAd.load(
            this,
            INTERSTITIAL_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {

                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                }
            }
        )
    }

    private fun selectImagesWithAd() {
        val ad = interstitialAd

        if (ad == null) {
            selectImages()
            loadInterstitialAd()
            return
        }

        interstitialAd = null

        ad.fullScreenContentCallback =
            object : FullScreenContentCallback() {

                override fun onAdDismissedFullScreenContent() {
                    selectImages()
                    loadInterstitialAd()
                }

                override fun onAdFailedToShowFullScreenContent(
                    error: AdError
                ) {
                    selectImages()
                    loadInterstitialAd()
                }
            }

        ad.show(this)
    }

    private fun selectImages() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }

        startActivityForResult(intent, PICK_IMAGES_REQUEST)
    }

    // APP OPEN AD

    private fun loadAppOpenAd() {
        AppOpenAd.load(
            this,
            APP_OPEN_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {

                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    showAppOpenAdIfAvailable()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    appOpenAd = null
                }
            }
        )
    }

    private fun showAppOpenAdIfAvailable() {
        if (!activityResumed) return

        val ad = appOpenAd ?: return
        val now = System.currentTimeMillis()

        if (
            lastAppOpenAdTime != 0L &&
            now - lastAppOpenAdTime < APP_OPEN_INTERVAL_MS
        ) {
            return
        }

        appOpenAd = null
        lastAppOpenAdTime = now

        ad.fullScreenContentCallback =
            object : FullScreenContentCallback() {

                override fun onAdDismissedFullScreenContent() {
                    loadAppOpenAd()
                }

                override fun onAdFailedToShowFullScreenContent(
                    error: AdError
                ) {
                    loadAppOpenAd()
                }
            }

        ad.show(this)
    }

    // REWARDED AD

    private fun loadRewardedAd() {
        RewardedAd.load(
            this,
            REWARDED_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {

                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                }
            }
        )
    }

    private fun createPdf() {
        if (selectedImages.isEmpty()) {
            Toast.makeText(
                this,
                "Please select images first",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
            putExtra(
                Intent.EXTRA_TITLE,
                "Converted_Images.pdf"
            )
        }

        startActivityForResult(intent, CREATE_PDF_REQUEST)
    }

    @Deprecated("Uses the activity result callback for broad Android compatibility")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != RESULT_OK || data == null) {
            return
        }

        when (requestCode) {
            PICK_IMAGES_REQUEST -> {
                selectedImages.clear()

                val clipData = data.clipData

                if (clipData != null) {
                    for (i in 0 until clipData.itemCount) {
                        selectedImages.add(
                            clipData.getItemAt(i).uri
                        )
                    }
                } else {
                    data.data?.let { uri ->
                        selectedImages.add(uri)
                    }
                }

                imageStatus.text = if (selectedImages.isEmpty()) {
                    "No images selected yet"
                } else {
                    "${selectedImages.size} image(s) selected"
                }

                if (selectedImages.isNotEmpty()) {
                    Toast.makeText(
                        this,
                        "${selectedImages.size} image(s) selected",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            CREATE_PDF_REQUEST -> {
                data.data?.let { destination ->
                    pendingDestination = destination
                    showRewardedAd()
                }
            }
        }
    }

    // PDF SAVING REQUIRES A COMPLETED REWARDED AD

    private fun showRewardedAd() {
        val ad = rewardedAd

        if (ad == null) {
            pendingDestination = null

            Toast.makeText(
                this,
                "A rewarded ad isn't available right now. Your PDF was not saved. Please try again later.",
                Toast.LENGTH_LONG
            ).show()

            loadRewardedAd()
            return
        }

        rewardedAd = null
        rewardEarned = false

        ad.fullScreenContentCallback =
            object : FullScreenContentCallback() {

                override fun onAdDismissedFullScreenContent() {
                    if (rewardEarned) {
                        savePendingPdf()
                    } else {
                        pendingDestination = null

                        Toast.makeText(
                            this@MainActivity,
                            "The ad wasn't completed, so your PDF was not saved. Please watch the full ad to save.",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    loadRewardedAd()
                }

                override fun onAdFailedToShowFullScreenContent(
                    error: AdError
                ) {
                    pendingDestination = null

                    Toast.makeText(
                        this@MainActivity,
                        "The ad couldn't be shown. Your PDF was not saved. Please try again later.",
                        Toast.LENGTH_LONG
                    ).show()

                    loadRewardedAd()
                }
            }

        ad.show(this) { _ ->
            rewardEarned = true
        }
    }

    private fun savePendingPdf() {
        val destination = pendingDestination ?: return
        pendingDestination = null
        savePdfToUri(destination)
    }

    private fun savePdfToUri(destination: Uri) {
        var document: PdfDocument? = null

        try {
            document = PdfDocument()

            val pageWidth = 595
            val pageHeight = 842
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            for ((index, uri) in selectedImages.withIndex()) {
                val bitmap = contentResolver
                    .openInputStream(uri)
                    ?.use { input ->
                        BitmapFactory.decodeStream(input)
                    } ?: throw IllegalStateException(
                    "Could not read image ${index + 1}"
                )

                try {
                    val pageInfo = PdfDocument.PageInfo.Builder(
                        pageWidth,
                        pageHeight,
                        index + 1
                    ).create()

                    val page = document.startPage(pageInfo)
                    val canvas: Canvas = page.canvas

                    canvas.drawColor(Color.WHITE)

                    val margin = 24f
                    val availableWidth = pageWidth - margin * 2
                    val availableHeight = pageHeight - margin * 2

                    val scale = minOf(
                        availableWidth / bitmap.width.toFloat(),
                        availableHeight / bitmap.height.toFloat()
                    )

                    val drawWidth = bitmap.width * scale
                    val drawHeight = bitmap.height * scale

                    val left = (pageWidth - drawWidth) / 2f
                    val top = (pageHeight - drawHeight) / 2f

                    canvas.drawBitmap(
                        bitmap,
                        null,
                        RectF(
                            left,
                            top,
                            left + drawWidth,
                            top + drawHeight
                        ),
                        paint
                    )

                    document.finishPage(page)
                } finally {
                    bitmap.recycle()
                }
            }

            contentResolver.openOutputStream(destination)?.use { output ->
                document.writeTo(output)
            } ?: throw IllegalStateException(
                "Could not save the PDF to the selected location"
            )

            lastSavedPdf = destination

            Toast.makeText(
                this,
                "PDF saved successfully. Opening preview...",
                Toast.LENGTH_SHORT
            ).show()

            openLastSavedPdf()

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "PDF error: ${e.message ?: "Unable to create PDF"}",
                Toast.LENGTH_LONG
            ).show()
        } finally {
            document?.close()
        }
    }

    private fun openLastSavedPdf() {
        val pdfUri = lastSavedPdf

        if (pdfUri == null) {
            Toast.makeText(
                this,
                "Create and save a PDF first",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(pdfUri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(intent)

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "No compatible PDF viewer found on this phone",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun openPrivacyPolicy() {
        try {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(PRIVACY_POLICY_URL)
                )
            )
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Could not open the Privacy Policy",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun loadBannerAd() {
        adView.adUnitId = BANNER_AD_UNIT_ID
        adView.setAdSize(AdSize.BANNER)
        adView.loadAd(AdRequest.Builder().build())
    }

    override fun onDestroy() {
        if (::adView.isInitialized) {
            adView.destroy()
        }
        super.onDestroy()
    }
}
