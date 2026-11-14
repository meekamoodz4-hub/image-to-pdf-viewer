
package com.meekamoodz.imagepdfviewer

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
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
    private var selectedCompression = CompressionOption.BALANCED

    private enum class CompressionOption(val label: String, val maxDimension: Int, val description: String) {
        ORIGINAL("High quality - larger PDF", 2600, "Best detail, larger file"),
        BALANCED("Balanced - recommended", 1800, "Good quality with smaller size"),
        SMALL("Small PDF - compressed", 1200, "Smaller file, reduced detail")
    }

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
        val page = Color.rgb(8, 18, 48)
        val panel = Color.rgb(19, 38, 83)
        val blue = Color.rgb(24, 117, 255)
        val cyan = Color.rgb(0, 205, 255)
        val pale = Color.rgb(229, 238, 255)
        val muted = Color.rgb(164, 185, 224)

        window.statusBarColor = page
        window.navigationBarColor = page
        window.decorView.systemUiVisibility = 0

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(page)
            setPadding(dp(16), dp(10), dp(16), dp(6))
        }

        val scrollView = ScrollView(this).apply {
            clipToPadding = false
            isFillViewport = true
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(16))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(8), dp(2), dp(18))
        }

        val logo = TextView(this).apply {
            text = "PDF"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            background = roundedBackground(blue, 18)
            layoutParams = LinearLayout.LayoutParams(dp(58), dp(58))
        }

        val headingColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }

        headingColumn.addView(TextView(this).apply {
            text = "Image to PDF"
            textSize = 23f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        headingColumn.addView(TextView(this).apply {
            text = "VIEWER  •  CONVERTER"
            textSize = 10f
            letterSpacing = 0.12f
            setTextColor(muted)
            setPadding(0, dp(3), 0, 0)
        })

        header.addView(logo)
        header.addView(headingColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(header)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(22), dp(20), dp(20))
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(24, 106, 245), Color.rgb(31, 48, 125), Color.rgb(20, 30, 77))
            ).apply { cornerRadius = dp(24).toFloat() }
        }

        hero.addView(TextView(this).apply {
            text = "📸   ➜   📄"
            textSize = 34f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(6))
        })
        hero.addView(TextView(this).apply {
            text = "Everything PDF, made simple"
            textSize = 21f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        hero.addView(TextView(this).apply {
            text = "Convert your pictures, view documents and reduce PDF file sizes."
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(pale)
            setPadding(dp(4), dp(7), dp(4), dp(14))
        })

        imageStatus = TextView(this).apply {
            text = "No images selected yet"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = roundedBackground(Color.argb(45, 255, 255, 255), 14)
        }
        hero.addView(imageStatus)
        hero.addView(makeButton("＋   Select Photos", cyan) { selectImagesWithAd() })
        content.addView(hero)

        content.addView(TextView(this).apply {
            text = "YOUR PDF TOOLS"
            textSize = 12f
            letterSpacing = 0.14f
            setTextColor(muted)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(4), dp(22), dp(4), dp(9))
        })

        val firstRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        firstRow.addView(makeFeatureCard("🖼️", "Image to PDF", "Select photos and convert", Color.rgb(28, 126, 255)) {
            selectImagesWithAd()
        }, LinearLayout.LayoutParams(0, dp(142), 1f).apply { setMargins(0, 0, dp(6), dp(6)) })
        firstRow.addView(makeFeatureCard("📄", "Create PDF", "Save your document", Color.rgb(15, 169, 202)) {
            createPdf()
        }, LinearLayout.LayoutParams(0, dp(142), 1f).apply { setMargins(dp(6), 0, 0, dp(6)) })
        content.addView(firstRow)

        val secondRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        secondRow.addView(makeFeatureCard("🗜️", "PDF Compressor", "Choose quality settings", Color.rgb(32, 174, 111)) {
            chooseCompression()
        }, LinearLayout.LayoutParams(0, dp(142), 1f).apply { setMargins(0, dp(6), dp(6), 0) })
        secondRow.addView(makeFeatureCard("👁️", "PDF Viewer", "Open your last saved PDF", Color.rgb(133, 83, 245)) {
            openLastSavedPdf()
        }, LinearLayout.LayoutParams(0, dp(142), 1f).apply { setMargins(dp(6), dp(6), 0, 0) })
        content.addView(secondRow)

        content.addView(TextView(this).apply {
            text = "Simple  •  Fast  •  Convenient"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(muted)
            setPadding(dp(8), dp(18), dp(8), dp(6))
        })

        val privacyButton = TextView(this).apply {
            text = "🔒  Privacy Policy"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(pale)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener { openPrivacyPolicy() }
        }
        content.addView(privacyButton)

        scrollView.addView(content)
        root.addView(scrollView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val adContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, dp(2))
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

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun makeFeatureCard(
        icon: String,
        title: String,
        subtitle: String,
        accent: Int,
        action: () -> Unit
    ): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(12), dp(10), dp(10))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(20, 37, 78))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.argb(90, accent))
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
            elevation = dp(2).toFloat()
        }

        val iconView = TextView(this).apply {
            text = icon
            textSize = 29f
            gravity = Gravity.CENTER
            background = roundedBackground(accent, 18)
            layoutParams = LinearLayout.LayoutParams(dp(58), dp(54)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(8)
            }
        }
        card.addView(iconView)

        card.addView(TextView(this).apply {
            text = title
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        card.addView(TextView(this).apply {
            text = subtitle
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(177, 194, 229))
            setPadding(0, dp(4), 0, 0)
        })
        return card
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

    private fun chooseCompression() {
        val options = CompressionOption.values()
        val optionLabels = options.map { option ->
            "${option.label}\n${option.description}" 
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Choose PDF quality")
            .setItems(optionLabels) { dialog, which ->
                selectedCompression = options[which]
                dialog.dismiss()
                Toast.makeText(
                    this,
                    "Quality selected: ${selectedCompression.label}. Tap Create PDF when ready.",
                    Toast.LENGTH_LONG
                ).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
            putExtra(Intent.EXTRA_TITLE, "Converted_Images.pdf")
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
                val decodedBitmap = contentResolver
                    .openInputStream(uri)
                    ?.use { input -> BitmapFactory.decodeStream(input) }
                    ?: throw IllegalStateException(
                        "This image format could not be decoded on this Android device (image ${index + 1}). Try saving it as JPG or PNG and selecting it again."
                    )
                val bitmap = resizeForPdf(decodedBitmap, selectedCompression.maxDimension)

                try {
                    if (bitmap !== decodedBitmap) decodedBitmap.recycle()
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

    private fun resizeForPdf(source: Bitmap, maxDimension: Int): Bitmap {
        val largest = maxOf(source.width, source.height)
        if (largest <= maxDimension) return source
        val scale = maxDimension.toFloat() / largest.toFloat()
        val width = maxOf(1, (source.width * scale).toInt())
        val height = maxOf(1, (source.height * scale).toInt())
        return Bitmap.createScaledBitmap(source, width, height, true)
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
