package com.dailyoffice.mei.finance

import com.dailyoffice.mei.data.CompanyType

/** Gross revenue thresholds in cents; not tax due or a monthly sales cap. */
object CompanyCeiling {
    const val MEI_CENTS = 8_100_000L
    const val ME_CENTS = 36_000_000L
    const val EPP_CENTS = 480_000_000L

    fun annualLimit(type: CompanyType, meiConfiguredLimit: Long = MEI_CENTS): Long =
        when (type) {
            CompanyType.MEI -> MEI_CENTS
            CompanyType.ME -> ME_CENTS
            CompanyType.EPP -> EPP_CENTS
            CompanyType.OTHER -> 0L
        }

    fun lowerBoundExclusive(type: CompanyType): Long =
        if (type == CompanyType.EPP) ME_CENTS else 0L

    fun label(type: CompanyType): String = when (type) {
        CompanyType.MEI -> "MEI"
        CompanyType.ME -> "Microempresa (ME)"
        CompanyType.EPP -> "Empresa de Pequeno Porte (EPP)"
        CompanyType.OTHER -> "Outro enquadramento"
    }
}
