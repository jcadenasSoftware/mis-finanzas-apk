package com.myfinances.ui.util

data class CountryOption(
    val code: String,
    val displayName: String,
    val suggestedCurrency: String
)

object CountryCurrency {
    val options: List<CountryOption> = listOf(
        CountryOption("CO", "Colombia", "COP"),
        CountryOption("MX", "México", "MXN"),
        CountryOption("US", "Estados Unidos", "USD"),
        CountryOption("ES", "España", "EUR"),
        CountryOption("AR", "Argentina", "ARS"),
        CountryOption("CL", "Chile", "CLP"),
        CountryOption("PE", "Perú", "PEN"),
        CountryOption("VE", "Venezuela", "VES")
    )

    fun suggestedCurrency(countryCode: String): String {
        return options.firstOrNull { it.code == countryCode }?.suggestedCurrency ?: "USD"
    }

    fun displayName(countryCode: String): String {
        return options.firstOrNull { it.code == countryCode }?.displayName ?: countryCode
    }
}
