import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.path.Path
import kotlin.io.path.readText


@Serializable
class Keys(
    val fsu: FSUKeys,

    @SerialName("personal_groupme_bot_id")
    val groupme: String
)

@Serializable
class FSUKeys(
    val username: String,
    val password: String
)

fun loadKeys(): Keys {
    val codec = Json { ignoreUnknownKeys = true }
    val home = System.getProperty("user.home")
    val text = Path("$home/Documents/keys.json").readText(Charsets.US_ASCII)
    return codec.decodeFromString<Keys>(text)
}