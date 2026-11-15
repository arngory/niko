package io.niko.ui

import io.niko.R
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class ExitAddressTest {
    private val string = { id: Int -> if (id == R.string.exit_address_answered) "Answered by" else "?" }

    @Test
    fun answers() {
        val full = describeExitAddress(
            JSONObject("""{"provider":"ipwho","ip":"203.0.113.6","country_code":"FI","country":"Finland","city":"Helsinki"}"""),
            "ipwho.is", string,
        )
        assertEquals("203.0.113.6\nFinland (FI), Helsinki\nAnswered by ipwho.is", full)
        // a code only: the country's name in the phone's language
        Locale.setDefault(Locale.ENGLISH)
        val code = describeExitAddress(JSONObject("""{"ip":"2001:db8::5","country_code":"DE"}"""), "Cloudflare", string)
        assertEquals("2001:db8::5\nGermany (DE)\nAnswered by Cloudflare", code)
        val bare = describeExitAddress(JSONObject("""{"ip":"203.0.113.5"}"""), "ipinfo.io", string)
        assertEquals("203.0.113.5\nAnswered by ipinfo.io", bare)
    }

    @Test
    fun providers() {
        // the ids libcore's exitProviders knows
        assertEquals(listOf("cloudflare", "ipinfo", "ipwho", "ipapi-co"), EXIT_PROVIDERS.map { it.first })
    }
}
