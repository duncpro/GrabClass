import java.lang.Thread.sleep
import kotlin.math.max

enum class LoopResult { CONTINUE, BREAK }

fun loop(fn: () -> LoopResult) {
    while (true) {
        when (fn()) {
            LoopResult.CONTINUE -> continue
            LoopResult.BREAK -> break
        }
    }
}

class RateLimiter(private val duration: /* ms */ Long) {
    private var last: Long = 0

    fun <T> runWithDelay(action: () -> T): T {
        val elapsed = System.currentTimeMillis() - last
        val remaining = max(this.duration - elapsed, 0)
        sleep(remaining)
        try {
            return action()
        } finally {
            this.last = System.currentTimeMillis()
        }
    }
}