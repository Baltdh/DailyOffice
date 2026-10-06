package com.dailyoffice.mei.receipt

data class ParsedReceipt(val supplier:String?, val totalCents:Long?, val date:String?, val document:String?)

object ReceiptParser {
    private val money = Regex("""(?i)(?:total|valor\s*total|a\s*pagar)\D{0,12}(\d{1,6}[.,]\d{2})""")
    private val date = Regex("""\b([0-3]?\d/[01]?\d/20\d{2})\b""")
    private val doc = Regex("""(?i)(?:nfce|nfc-e|cupom|nota)\D{0,15}(\d{3,})""")
    fun parse(text:String): ParsedReceipt {
        val lines=text.lines().map{it.trim()}.filter{it.isNotEmpty()}
        val cents=money.find(text)?.groupValues?.get(1)?.replace(".","")?.replace(",",".")?.toBigDecimalOrNull()?.movePointRight(2)?.toLong()
        return ParsedReceipt(lines.firstOrNull(), cents, date.find(text)?.groupValues?.get(1), doc.find(text)?.groupValues?.get(1))
    }
}
