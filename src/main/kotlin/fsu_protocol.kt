import kotlinx.serialization.Serializable

@Serializable
class RegBlocksContainer(
    val sections: List<Section>
)

@Serializable
class Section(
    val openSeats: Int,
    val sectionNumber: String,
    val disabledReasons: List<String>,
    val freeFormTopics: String
)
