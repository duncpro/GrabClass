import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.devtools.v120.network.Network
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.lang.Thread.sleep
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpClient.newHttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.min

// GrabClass - Never miss out on a class you need again!
// Configure GrabClass by specifying the name of the upcoming semester and year.
// Then provide a list of target courses which you'd like to receive notification about.
// GrabClass will notify you anytime a spot becomes available.

const val SEMESTER = "Spring"
const val YEAR = "2024"

val TARGETS: List<TargetCourse> = listOf(
    TargetCourse("MAC","2313")
)

// Additionally, you'll need to specify the GroupMe Bot ID corresponding to the GroupMe Bot
// that will dispatch messages to your smartphone. You'll also need to specify the credentials
// of your myFSU account. These credentials should be specified in "~/Documents/keys.json" where `~`
// is your home directory.
//
// # Example keys.json file
// ```
// {
//   "fsu": {
//       "username": "abc123",
//       "password": "9bandedArmadillo"
//   },
//   "personal_groupme_bot_id": "abcdefghijklmnop12345"
// }
// ```

// Once configured correctly, invoking "gradle run" from the command line in the project
// directory will launch the application and begin monitoring the FSU scheduling system.
// You will receive a message confirming connection to the scheduler system and a notification
// upon a subsequent change in the number of available seats.

fun main() {
    val keys: Keys = loadKeys()
    val http: HttpClient = newHttpClient()

    loop {
        notifyall(keys, http, "GrabClass is starting up... (Targets: $TARGETS)")
        notifyall(keys, http, "Prepare to Authenticate: You will be prompted to authenticate " +
                              "yourself with FSU via Duo 2FA.")

        val browser = ChromeDriver()
        browser.devTools.createSession()
        browser.devTools.send(Network.setCacheDisabled(true))

        try {
            authenticateFSU(keys, browser)
            notifyall(keys, http, "Authentication Successful")


            try {
                watch(keys, http, browser)
            } catch (e: Exception) {
                notifyall(keys, http, "An error occurred while attempting to refresh the " +
                        "course population count. Specifically, ${e.message}. " +
                        "As a result, the system will restart in a moment.")
                e.printStackTrace()
                sleep(10 * 1000)
            }
        } finally {
            browser.quit()
        }

        return@loop LoopResult.CONTINUE
    }
}

fun watch(keys: Keys, http: HttpClient, browser: WebDriver) {
    val totalPreviouslyOpenSpots = HashMap<TargetCourse, Int>()
    val limiter = RateLimiter(duration = 5 * 1000 /* ms */)
    loop {
        for (target in TARGETS) {
            val totalCurrentlyOpenSpots = limiter.runWithDelay { check(target, browser) }
            if (totalCurrentlyOpenSpots != totalPreviouslyOpenSpots.getOrDefault(target, defaultValue = -1)) {
                notifyall(keys, http, "Alert: $target has $totalCurrentlyOpenSpots seats available")
            }
            totalPreviouslyOpenSpots[target] = totalCurrentlyOpenSpots
        }
        return@loop LoopResult.CONTINUE
    }
}

fun check(target: TargetCourse, browser: WebDriver): Int {
    browser.get("https://fsu.collegescheduler.com/api/terms/${YEAR}%20${SEMESTER}/subjects/${target.subject}/courses/" +
            "${target.code}/regblocks")

    val rawdata = browser.findElement(By.xpath("//pre")).text
    val data: RegBlocksContainer = FSU_JSON_Codec.decodeFromString(rawdata)

    var totalCurrentlyOpenSpots = 0
    for (section in data.sections) {
        if (section.disabledReasons.contains("The campus \"Panama City, FL\" is not selected.")) continue
        if (section.disabledReasons.contains("This section is full.")) continue
        if (section.freeFormTopics.contains("HONORS")) continue
        totalCurrentlyOpenSpots += section.openSeats
    }

    return totalCurrentlyOpenSpots
}

fun authenticateFSU(keys: Keys, browser: WebDriver) {
    browser.get("https://fsu.collegescheduler.com")

    // Fill username
    run {
        val element = browser.findElement(By.id("username"))
        element.click()
        element.sendKeys(keys.fsu.username)
    }

    // Fill password
    run {
        val element = browser.findElement(By.id("password"))
        element.click()
        element.sendKeys(keys.fsu.password)
    }

    // Press submit
    browser.findElement(By.id("fsu-login-button")).click();

    // Wait for 2fa
    run {
        val selector = By.id("trust-browser-button");
        WebDriverWait(browser, Duration.ofHours(1))
            .until(ExpectedConditions.elementToBeClickable(selector))
        browser.findElement(selector).click()
    }

    WebDriverWait(browser, Duration.ofMinutes(1))
        .until(ExpectedConditions.titleIs("Schedule Assistant"))
}

fun formatLogMessage(message: String): String {
    val time = ZonedDateTime.now()
    val timestamp = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(time)
    return "[GrabClass] [$timestamp]: $message"
}

fun notifyall(keys: Keys, http: HttpClient, message: String) {
    val formatted = formatLogMessage(message)
    println(formatted)
    sms(keys, http, formatted)
}

fun sms(keys: Keys, http: HttpClient, message: String) {
    val authtoken = keys.groupme
    val request = HttpRequest.newBuilder()
        .uri(URI.create("https://api.groupme.com/v3/bots/post"))
        .POST(BodyPublishers.ofString(Json.encodeToString(GroupMeBotMessage(
            message.substring(0, min(message.length, 1000)),
            authtoken
        ))))
        .build()

    val response = http.send(request, HttpResponse.BodyHandlers.ofString())
    if (response.statusCode() != 202) {
        val body = response.body().prependIndent("    ")
        println(formatLogMessage("Failed to send SMS message: \n${body.prependIndent("    ")}"))
    }
}

/** Represents a course (as in, a college course) */
data class TargetCourse(val subject: String, val code: String)
{
    override fun toString(): String {
        return "${subject}${code}"
    }
}

val FSU_JSON_Codec = Json { ignoreUnknownKeys = true }