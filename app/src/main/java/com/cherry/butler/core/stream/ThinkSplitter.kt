package com.cherry.butler.core.stream

/**
 * A streamed reply split into the reply and the model's thought, for models that think in the
 * open (`<think>…</think>` inside the text) rather than in a separate field. What comes in is
 * appended to [reply] or [thought]; whatever they held before (a continued reply, its earlier
 * thought) is never touched.
 *
 * Only the tags themselves count, so the word "think" in prose is text, and so is any other
 * angle bracket ([TagStreamParser]). Two ways a model gets the tags wrong, and what happens
 * then, so that no word of a reply is ever lost to the thought:
 *
 * - **A close with no open.** Some providers put the opening tag in the prompt's template and
 *   stream only `</think>`. Everything before that close was the thought, so it moves there
 *   when the close arrives.
 * - **An open never closed.** If the stream ends with the thought still open and no reply
 *   at all, the model wrote its reply inside the tag (or opened one by mistake): what was
 *   taken for thought comes back as the reply.
 */
class ThinkSplitter(private val reply: StringBuilder, private val thought: StringBuilder) {
    private val parser = TagStreamParser(TAGS)
    private val replyStart = reply.length
    private var inThink = false
    private var sawTag = false
    /** Where in [thought] the open that is still unclosed began. */
    private var openAt = -1

    fun feed(chunk: CharSequence) {
        for (e in parser.feed(chunk)) on(e)
    }

    /** End of the stream: a tag left half-written is text, and an unclosed thought may be the reply. */
    fun finish() {
        for (e in parser.finish()) on(e)
        if (inThink && openAt >= 0 && reply.substring(replyStart).isBlank()) {
            reply.setLength(replyStart)
            reply.append(thought.substring(openAt).trimStart())
            thought.setLength(openAt)
            inThink = false
        }
    }

    private fun on(e: TagStreamParser.Event) {
        when (e) {
            is TagStreamParser.Event.Open -> if (!inThink) {
                inThink = true
                openAt = thought.length
                sawTag = true
            }
            is TagStreamParser.Event.Close -> {
                if (inThink) {
                    inThink = false
                } else if (!sawTag) {
                    // The first tag is a close: the reply so far was the thought.
                    thought.append(reply.substring(replyStart))
                    reply.setLength(replyStart)
                }
                sawTag = true
            }
            is TagStreamParser.Event.Text -> if (inThink) thought.append(e.text) else reply.append(e.text)
        }
    }

    companion object {
        val TAGS = listOf("think", "thinking")
    }
}
