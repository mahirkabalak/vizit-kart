package io.github.mahirkabalak.vizitkart

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

class MainActivity : ComponentActivity() {

    private val navy = Color.parseColor("#0B1F4D")
    private val gold = Color.parseColor("#E3B23C")
    private val muted = Color.parseColor("#5B6478")

    private lateinit var preview: ImageView
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var form: LinearLayout
    private val fields = linkedMapOf<String, EditText>()
    private var pendingPhoto: Uri? = null

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingPhoto
        if (ok && uri != null) handleImage(uri) else showStatus("Fotoğraf çekilmedi.")
    }

    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) handleImage(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        savedInstanceState?.getString("photo")?.let { pendingPhoto = Uri.parse(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingPhoto?.let { outState.putString("photo", it.toString()) }
    }

    override fun onDestroy() {
        super.onDestroy()
        recognizer.close()
    }

    // ---------- Arayüz ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUi(): View {
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(root)

        // Başlık
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(navy)
            setPadding(dp(22), dp(22), dp(22), dp(22))
        }
        header.addView(TextView(this).apply {
            text = "Vizit Kart"
            setTextColor(Color.WHITE)
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
        })
        header.addView(TextView(this).apply {
            text = "Kartviziti çek, kişiyi saniyede rehbere kaydet."
            setTextColor(Color.parseColor("#C9D3EA"))
            textSize = 15f
            setPadding(0, dp(4), 0, 0)
        })
        root.addView(header)

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
        }
        root.addView(body, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(primaryButton("📷  Kartı Çek") { openCamera() },
            LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginEnd = dp(8) })
        buttons.addView(secondaryButton("🖼  Galeriden") {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }, LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginStart = dp(8) })
        body.addView(buttons)

        preview = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
            background = rounded(Color.WHITE, dp(12))
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        body.addView(preview, LinearLayout.LayoutParams(MATCH_PARENT, dp(200)).apply { topMargin = dp(16) })

        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        progress = ProgressBar(this).apply { visibility = View.GONE }
        statusRow.addView(progress, LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(10) })
        status = TextView(this).apply {
            text = "Kartı düz bir zemine koyup iyi ışıkta çekin. Okuma telefonunuzda yapılır, internet gerekmez."
            setTextColor(muted)
            textSize = 14f
        }
        statusRow.addView(status, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        body.addView(statusRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(14) })

        form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = rounded(Color.WHITE, dp(14))
            setPadding(dp(16), dp(10), dp(16), dp(16))
        }
        addField("name", "Ad Soyad", InputType.TYPE_TEXT_VARIATION_PERSON_NAME or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        addField("title", "Unvan", InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        addField("company", "Firma / Kurum", InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        addField("phone", "Telefon", InputType.TYPE_CLASS_PHONE)
        addField("phone2", "Telefon 2", InputType.TYPE_CLASS_PHONE)
        addField("email", "E-posta", InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        addField("website", "Web sitesi", InputType.TYPE_TEXT_VARIATION_URI)
        addField("address", "Adres", InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        form.addView(primaryButton("Rehbere Kaydet") { saveContact() },
            LinearLayout.LayoutParams(MATCH_PARENT, dp(56)).apply { topMargin = dp(16) })
        body.addView(form, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(16) })

        body.addView(TextView(this).apply {
            text = "Gizlilik politikası"
            setTextColor(navy)
            textSize = 13f
            paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
            setPadding(0, dp(24), 0, 0)
            setOnClickListener {
                openUrl("https://mahirkabalak.github.io/vizit-kart/gizlilik.html")
            }
        })
        body.addView(TextView(this).apply {
            text = "© Griffin Ajans"
            setTextColor(muted)
            textSize = 12f
            setPadding(0, dp(4), 0, 0)
        })

        // Android 15+ kenardan kenara ekran: sistem çubuklarına göre boşluk bırak
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            header.setPadding(dp(22), dp(22) + bars.top, dp(22), dp(22))
            v.setPadding(bars.left, 0, bars.right, bars.bottom)
            insets
        }
        return scroll
    }

    private fun addField(key: String, label: String, type: Int) {
        form.addView(TextView(this).apply {
            text = label
            setTextColor(muted)
            textSize = 12f
            setPadding(0, dp(10), 0, dp(2))
        })
        val et = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or type
            if (type == InputType.TYPE_CLASS_PHONE) inputType = InputType.TYPE_CLASS_PHONE
            textSize = 16f
            setTextColor(Color.parseColor("#1E293B"))
        }
        fields[key] = et
        form.addView(et, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    private fun rounded(color: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
        if (stroke != null) setStroke(dp(2), stroke)
    }

    private fun primaryButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        background = rounded(navy, dp(14))
        stateListAnimator = null
        setOnClickListener { onClick() }
    }

    private fun secondaryButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(navy)
        background = rounded(Color.WHITE, dp(14), gold)
        stateListAnimator = null
        setOnClickListener { onClick() }
    }

    private fun showStatus(msg: String, busy: Boolean = false) {
        status.text = msg
        progress.visibility = if (busy) View.VISIBLE else View.GONE
    }

    // ---------- Akış ----------

    private fun openCamera() {
        val dir = File(cacheDir, "scans").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // eski taramaları temizle
        val file = File(dir, "kart_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        pendingPhoto = uri
        try {
            takePicture.launch(uri)
        } catch (e: ActivityNotFoundException) {
            showStatus("Bu cihazda kamera uygulaması bulunamadı. Galeriden seçebilirsiniz.")
        }
    }

    private fun handleImage(uri: Uri) {
        preview.setImageURI(null)
        preview.setImageURI(uri)
        preview.visibility = View.VISIBLE
        showStatus("Kart okunuyor…", busy = true)
        val image = try {
            InputImage.fromFilePath(this, uri)
        } catch (e: Exception) {
            showStatus("Görsel açılamadı. Lütfen tekrar deneyin.")
            return
        }
        recognizer.process(image)
            .addOnSuccessListener { result ->
                val lines = result.textBlocks.flatMap { b -> b.lines.map { it.text } }
                if (lines.isEmpty()) {
                    showStatus("Kartta yazı bulunamadı. Daha yakından ve net çekmeyi deneyin.")
                    return@addOnSuccessListener
                }
                fill(CardParser.parse(lines))
                showStatus("Bilgileri kontrol edin, gerekirse düzeltin ve kaydedin.")
            }
            .addOnFailureListener {
                showStatus("Okuma sırasında bir sorun oldu. Lütfen tekrar deneyin.")
            }
    }

    private fun fill(c: CardInfo) {
        fields["name"]?.setText(c.name)
        fields["title"]?.setText(c.title)
        fields["company"]?.setText(c.company)
        fields["phone"]?.setText(c.phone)
        fields["phone2"]?.setText(c.phone2)
        fields["email"]?.setText(c.email)
        fields["website"]?.setText(c.website)
        fields["address"]?.setText(c.address)
        form.visibility = View.VISIBLE
        form.post { (form.parent?.parent?.parent as? ScrollView)?.smoothScrollTo(0, form.top) }
    }

    private fun v(key: String) = fields[key]?.text?.toString()?.trim().orEmpty()

    private fun saveContact() {
        if (v("name").isEmpty() && v("phone").isEmpty() && v("email").isEmpty()) {
            Toast.makeText(this, "En az ad, telefon veya e-posta girin.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.NAME, v("name"))
            putExtra(ContactsContract.Intents.Insert.JOB_TITLE, v("title"))
            putExtra(ContactsContract.Intents.Insert.COMPANY, v("company"))
            putExtra(ContactsContract.Intents.Insert.PHONE, v("phone"))
            putExtra(ContactsContract.Intents.Insert.PHONE_TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_WORK)
            if (v("phone2").isNotEmpty()) {
                putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE, v("phone2"))
                putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE_TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
            }
            putExtra(ContactsContract.Intents.Insert.EMAIL, v("email"))
            putExtra(ContactsContract.Intents.Insert.EMAIL_TYPE, ContactsContract.CommonDataKinds.Email.TYPE_WORK)
            putExtra(ContactsContract.Intents.Insert.POSTAL, v("address"))
            putExtra(ContactsContract.Intents.Insert.POSTAL_TYPE, ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK)
            if (v("website").isNotEmpty()) {
                val site = ContentValues().apply {
                    put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE)
                    put(ContactsContract.CommonDataKinds.Website.URL, v("website"))
                    put(ContactsContract.CommonDataKinds.Website.TYPE, ContactsContract.CommonDataKinds.Website.TYPE_WORK)
                }
                putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, arrayListOf(site))
            }
            putExtra(ContactsContract.Intents.Insert.NOTES, "Vizit Kart ile eklendi")
            putExtra("finishActivityOnSaveCompleted", true)
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Rehber uygulaması bulunamadı.", Toast.LENGTH_LONG).show()
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) { }
    }
}
