package com.cherry.butler.core.markdown

import kotlin.test.Test
import kotlin.test.assertEquals

class EditorHtmlTest {

    @Test
    fun `the editor's HTML is saved in the website's shape`() {
        // What Compose Rich Editor writes (its own decoder, rc11): <br> for an empty line, a
        // heading inside the paragraph, <b>/<i>, rgba() colours, a highlight as a background.
        val editor = "<br><p style=\"text-align: center;\"><h2>Title</h2></p>" +
            "<p>Hello <span style=\"background: rgba(157, 8, 8, 1);\">there</span> " +
            "<span style=\"color: rgba(200, 26, 26, 1);\"><b>bold</b></span>" +
            "<img src=\"https://ella.janitorai.com/media-approved/x.webp\" width=\"208.0\" height=\"341.0\"></p>" +
            "<p><i>it</i> <a href=\"https://example.com\" target=\"_blank\">link</a></p><br>"
        assertEquals(
            "<p></p><h2 style=\"text-align: center\">Title</h2>" +
                "<p>Hello <mark data-color=\"#9d0808\" style=\"background-color: rgb(157, 8, 8); color: inherit;\">there</mark> " +
                "<span style=\"color: rgb(200, 26, 26);\"><strong>bold</strong></span>" +
                "<img src=\"https://ella.janitorai.com/media-approved/x.webp\" width=\"208\" height=\"341\"></p>" +
                "<p><em>it</em> <a target=\"_blank\" rel=\"noopener noreferrer nofollow\" href=\"https://example.com\">link</a></p>",
            EditorHtml.toWebsite(editor),
        )
    }

    @Test
    fun `left alignment is the default and isn't written`() {
        assertEquals("<p>a</p><p style=\"text-align: right\">b</p>", EditorHtml.toWebsite("<p style=\"text-align: left;\">a</p><p style=\"text-align: right;\">b</p>"))
    }

    @Test
    fun `nothing written is nothing saved`() {
        assertEquals("", EditorHtml.toWebsite("<br>"))
        assertEquals("", EditorHtml.toWebsite(""))
    }

    @Test
    fun `the website's HTML loads with its empty lines and highlight colours kept`() {
        val website = "<p></p><p>Hi <mark data-color=\"#9d0808\" style=\"background-color: rgb(157, 8, 8); color: inherit;\">there</mark></p>" +
            "<h2 style=\"text-align: center\">Title</h2>"
        assertEquals(
            "<br><p>Hi <span style=\"background-color: #9d0808;\">there</span></p><h2 style=\"text-align: center\">Title</h2>",
            EditorHtml.toEditor(website),
        )
    }

    @Test
    fun `a bio without tags loads as it is`() {
        assertEquals("just words", EditorHtml.toEditor("just words"))
    }
}
