package io.github.mahirkabalak.vizitkart

import java.util.Locale

/** Kartvizitten okunan bilgiler. */
data class CardInfo(
    var name: String = "",
    var title: String = "",
    var company: String = "",
    var phone: String = "",
    var phone2: String = "",
    var email: String = "",
    var website: String = "",
    var address: String = "",
)

/**
 * OCR metninden kartvizit alanlarını ayıklar. Tamamen cihaz içinde,
 * basit kurallarla çalışır; kullanıcı kaydetmeden önce alanları düzeltebilir.
 */
object CardParser {
    private val tr = Locale("tr", "TR")

    private val emailRx = Regex("""[A-Za-z0-9._%+\-]+\s?@\s?[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")
    private val urlRx = Regex("""(?i)\b((?:https?://)?(?:www\.)[A-Za-z0-9\-]+(?:\.[A-Za-z0-9\-]+)+(?:/\S*)?|[A-Za-z0-9\-]+\.(?:com|com\.tr|net|org|org\.tr|gov\.tr|edu\.tr|av\.tr|tr|io|co|info|biz)(?:/\S*)?)\b""")
    private val phoneRx = Regex("""(?:\+?\d[\d\s().\-/]{7,}\d)""")

    private val titleWords = listOf(
        "müdür", "mudur", "uzman", "dr.", "dr ", "doç", "doc.", "prof", "op.", "uzm.", "başhekim",
        "hekim", "doktor", "mühendis", "muhendis", "avukat", "av.", "danışman", "danisman",
        "temsilci", "yönetici", "yonetici", "koordinatör", "koordinator", "sorumlu", "şef", "sef",
        "direktör", "direktor", "başkan", "baskan", "kurucu", "ortak", "genel", "satış", "satis",
        "pazarlama", "ceo", "cto", "cfo", "coo", "manager", "director", "engineer", "specialist",
        "consultant", "founder", "owner", "sales", "assistant", "asistan", "eczacı", "hemşire",
        "diş", "psikolog", "diyetisyen", "fizyoterapist", "mimar", "editör", "tasarımcı"
    )
    private val companyWords = listOf(
        "a.ş", "a.s.", "aş.", "ltd", "şti", "sti", "holding", "hastane", "hastanesi", "klinik",
        "kliniği", "tıp merkezi", "poliklinik", "ajans", "inc", "llc", "gmbh", "group", "grup",
        "teknoloji", "biyoteknoloji", "medikal", "medical", "sağlık", "saglik", "san.", "tic.",
        "ticaret", "sanayi", "eczanesi", "laboratuvar", "üniversite", "universite", "vakfı", "derneği"
    )
    private val addressWords = listOf(
        "mah", "mh.", "cad", "cd.", "sok", "sk.", "bulvar", "blv", "no:", "no :", "kat:", "kat ",
        "daire", "d:", "apt", "plaza", "/", "ankara", "istanbul", "izmir", "bursa", "antalya",
        "adana", "konya", "çankaya", "cankaya", "türkiye", "turkiye", "pk.", "posta"
    )
    private val faxWords = listOf("fax", "faks", "f:", "f.")

    fun parse(lines: List<String>): CardInfo {
        val info = CardInfo()
        val rest = mutableListOf<String>()
        val phones = mutableListOf<String>()
        val addressParts = mutableListOf<String>()

        for (raw in lines) {
            var line = raw.trim()
            if (line.isEmpty()) continue
            val lower = line.lowercase(tr)
            var used = false

            emailRx.find(line)?.let {
                if (info.email.isEmpty()) info.email = it.value.replace(" ", "").lowercase(tr)
                line = line.replace(it.value, " ").trim()
                used = true
            }
            urlRx.find(line)?.let {
                val v = it.value
                if (!v.contains("@") && info.website.isEmpty()) {
                    info.website = v.lowercase(tr)
                    line = line.replace(v, " ").trim()
                    used = true
                }
            }
            val isFax = faxWords.any { lower.startsWith(it) || lower.contains(" $it") }
            phoneRx.findAll(line).forEach { m ->
                val digits = m.value.filter { it.isDigit() }
                if (digits.length in 10..13 && !isFax) {
                    phones.add(formatPhone(m.value))
                    used = true
                }
            }
            if (used) {
                line = phoneRx.replace(line, " ")
                    .replace(Regex("""(?i)\b(tel|telefon|gsm|cep|mobil|mobile|phone|t|m|e-?posta|e-?mail|web)\s*[:.]?"""), " ")
                    .trim(' ', ':', '|', '-', '•', '·', ',')
                if (line.length < 3) continue
            }
            if (isFax) continue

            val l2 = line.lowercase(tr)
            when {
                addressWords.any { l2.contains(it) } && line.any { it.isDigit() } -> addressParts.add(line)
                addressWords.any { l2.contains(" $it") || l2.startsWith(it) } && line.length > 18 -> addressParts.add(line)
                else -> rest.add(line)
            }
        }

        info.phone = phones.getOrElse(0) { "" }
        info.phone2 = phones.getOrElse(1) { "" }
        info.address = addressParts.joinToString(", ")

        // Unvan ve firma: anahtar kelimelere göre
        val leftovers = mutableListOf<String>()
        for (line in rest) {
            val l = line.lowercase(tr)
            when {
                info.company.isEmpty() && companyWords.any { l.contains(it) } -> info.company = line
                info.title.isEmpty() && titleWords.any { l.contains(it) } && !looksLikeName(line) -> info.title = line
                else -> leftovers.add(line)
            }
        }

        // İsim: harf ağırlıklı, 2-4 kelimelik ilk satır
        val nameLine = leftovers.firstOrNull { looksLikeName(it) }
            ?: rest.firstOrNull { looksLikeName(it) && it != info.company }
        if (nameLine != null) {
            info.name = toTitleCase(nameLine)
            leftovers.remove(nameLine)
            // Unvan isimle aynı satırdaysa (ör. "Prof. Dr. Ali Veli") ayır
            if (info.title.isEmpty()) {
                val prefix = Regex("""(?i)^((?:prof\.?|doç\.?|doc\.?|dr\.?|op\.?|uzm\.?|av\.?|dt\.?|ecz\.?)\s*)+""").find(nameLine)
                if (prefix != null) info.title = prefix.value.trim()
            }
        }
        if (info.company.isEmpty()) {
            leftovers.firstOrNull { it.length in 3..40 && it.none { c -> c.isDigit() } }?.let {
                info.company = it
                leftovers.remove(it)
            }
        }
        if (info.title.isEmpty()) {
            leftovers.firstOrNull { it.length in 3..50 && it.none { c -> c.isDigit() } }?.let { info.title = it }
        }
        return info
    }

    private fun looksLikeName(line: String): Boolean {
        if (line.any { it.isDigit() } || line.contains("@")) return false
        val cleaned = line.replace(Regex("""(?i)^((?:prof\.?|doç\.?|doc\.?|dr\.?|op\.?|uzm\.?|av\.?|dt\.?|ecz\.?)\s*)+"""), "")
        val words = cleaned.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (words.size !in 2..4) return false
        val l = line.lowercase(tr)
        if (companyWords.any { l.contains(it) }) return false
        return words.all { w -> w.first().isLetter() && w.first().isUpperCase() && w.count { it.isLetter() } >= 2 }
    }

    private fun toTitleCase(s: String): String =
        s.trim().split(Regex("""\s+""")).joinToString(" ") { w ->
            if (w.endsWith(".") && w.length <= 5) w.replaceFirstChar { it.titlecase(tr) }
            else w.lowercase(tr).replaceFirstChar { it.titlecase(tr) }
        }

    private fun formatPhone(raw: String): String {
        var d = raw.filter { it.isDigit() || it == '+' }
        if (d.startsWith("+")) return d
        d = d.filter { it.isDigit() }
        return when {
            d.startsWith("90") && d.length == 12 -> "+$d"
            d.startsWith("0") && d.length == 11 -> "+9$d"
            d.length == 10 && (d.startsWith("5") || d.startsWith("2") || d.startsWith("3") || d.startsWith("4") || d.startsWith("8")) -> "+90$d"
            else -> d
        }
    }
}
