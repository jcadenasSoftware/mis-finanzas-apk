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
        CountryOption("AR", "Argentina", "ARS"),
        CountryOption("CL", "Chile", "CLP"),
        CountryOption("PE", "Perú", "PEN"),
        CountryOption("EC", "Ecuador", "USD"),
        CountryOption("BO", "Bolivia", "BOB"),
        CountryOption("PY", "Paraguay", "PYG"),
        CountryOption("UY", "Uruguay", "UYU"),
        CountryOption("VE", "Venezuela", "VES"),
        CountryOption("BR", "Brasil", "BRL"),
        CountryOption("PA", "Panamá", "PAB"),
        CountryOption("CR", "Costa Rica", "CRC"),
        CountryOption("GT", "Guatemala", "GTQ"),
        CountryOption("HN", "Honduras", "HNL"),
        CountryOption("SV", "El Salvador", "USD"),
        CountryOption("NI", "Nicaragua", "NIO"),
        CountryOption("DO", "Rep. Dominicana", "DOP"),
        CountryOption("CU", "Cuba", "CUP"),
        CountryOption("US", "Estados Unidos", "USD"),
        CountryOption("ES", "España", "EUR")
    )

    fun suggestedCurrency(countryCode: String): String {
        return options.firstOrNull { it.code == countryCode }?.suggestedCurrency ?: "USD"
    }

    fun displayName(countryCode: String): String {
        return options.firstOrNull { it.code == countryCode }?.displayName ?: countryCode
    }
}
