package com.example.studyappandroid.data.document

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfLayoutReconstructorTest {

    private fun lineGlyphs(
        text: String,
        startX: Float,
        y: Float,
        pageWidth: Float = 600f,
    ): List<PositionedGlyph> {
        return listOf(
            PositionedGlyph(
                text = text,
                x = startX,
                y = y,
                width = text.length * 5.2f,
                height = 10f,
                pageWidth = pageWidth,
                spaceWidth = 4f,
            ),
        )
    }

    @Test
    fun reconstructPage_separatesTwoColumnTextWithoutMergingRows() {
        val glyphs = buildList {
            addAll(lineGlyphs("Cell Biology Handout", 180f, 40f))
            // Left column (x=40) and Right column (x=320) sharing the same Y rows
            val leftRows = listOf(
                "Mitochondria produce ATP",
                "through oxidative phosphorylation",
                "inside the inner membrane.",
                "Ribosomes translate mRNA",
                "into polypeptide chains.",
                "Lysosomes digest waste.",
            )
            val rightRows = listOf(
                "Chloroplasts perform light",
                "reactions inside thylakoid",
                "membranes of plant cells.",
                "The Calvin cycle builds",
                "glucose inside the stroma.",
                "Vacuoles maintain turgor.",
            )
            leftRows.forEachIndexed { i, txt ->
                addAll(lineGlyphs(txt, 40f, 80f + i * 16f))
            }
            rightRows.forEachIndexed { i, txt ->
                addAll(lineGlyphs(txt, 320f, 80f + i * 16f))
            }
        }

        val text = PdfLayoutReconstructor.reconstructPage(glyphs)

        assertTrue(text.contains("Mitochondria produce ATP"))
        assertTrue(text.contains("Chloroplasts perform light"))
        // Left line must NOT be merged horizontally with Right line
        assertFalse(text.contains("Mitochondria produce ATP Chloroplasts perform light"))
        val mitoIdx = text.indexOf("Mitochondria produce ATP")
        val riboIdx = text.indexOf("Ribosomes translate mRNA")
        val chloroIdx = text.indexOf("Chloroplasts perform light")
        assertTrue(mitoIdx < riboIdx && riboIdx < chloroIdx)
    }

    @Test
    fun reconstructPage_preservesTableRowValuesWithPipeDelimiter() {
        val glyphs = buildList {
            addAll(lineGlyphs("Quantity", 40f, 80f))
            addAll(lineGlyphs("Symbol", 210f, 80f))
            addAll(lineGlyphs("SI Unit", 380f, 80f))

            addAll(lineGlyphs("Voltage", 40f, 100f))
            addAll(lineGlyphs("V", 210f, 100f))
            addAll(lineGlyphs("Volt", 380f, 100f))

            addAll(lineGlyphs("Current", 40f, 120f))
            addAll(lineGlyphs("I", 210f, 120f))
            addAll(lineGlyphs("Ampere", 380f, 120f))
        }

        val text = PdfLayoutReconstructor.reconstructPage(glyphs)

        assertTrue(text.contains("Quantity | Symbol | SI Unit"))
        assertTrue(text.contains("Voltage | V | Volt"))
        assertTrue(text.contains("Current | I | Ampere"))
    }
}
