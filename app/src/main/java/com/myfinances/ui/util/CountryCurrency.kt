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

    fun displayNameWithCode(countryCode: String): String {
        val option = options.firstOrNull { it.code == countryCode }
        return if (option != null) {
            "${option.displayName} (${option.code})"
        } else {
            countryCode
        }
    }

    fun currencyDisplayName(currencyCode: String): String {
        val currencyNames = mapOf(
            "COP" to "Peso colombiano",
            "MXN" to "Peso mexicano",
            "ARS" to "Peso argentino",
            "CLP" to "Peso chileno",
            "PEN" to "Sol peruano",
            "BOB" to "Boliviano",
            "PYG" to "Guaraní",
            "UYU" to "Peso uruguayo",
            "VES" to "Bolívar",
            "BRL" to "Real brasileño",
            "PAB" to "Balboa",
            "CRC" to "Colón costarricense",
            "GTQ" to "Quetzal",
            "HNL" to "Lempira",
            "NIO" to "Córdoba",
            "DOP" to "Peso dominicano",
            "CUP" to "Peso cubano",
            "USD" to "Dólar estadounidense",
            "EUR" to "Euro"
        )
        return currencyNames[currencyCode] ?: currencyCode
    }

    fun currencyDisplayNameWithCode(currencyCode: String): String {
        val name = currencyDisplayName(currencyCode)
        return if (name != currencyCode) {
            "$name ($currencyCode)"
        } else {
            currencyCode
        }
    }
}
