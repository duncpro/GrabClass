import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class GroupMeBotMessage(
    val text: String,
    @SerialName("bot_id") val botId: String
)