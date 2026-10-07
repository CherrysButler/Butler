package com.cherry.butler.feature.chat

import kotlin.random.Random

/**
 * What the thinking line says while the model works: "Thinking" or one of these, picked at
 * random per reply. Playful and human; nothing that sounds like a machine at work
 * (computing, processing, synthesizing, hashing and their kind are left out on purpose).
 * In Settings the user can add to this list or take from it, add lists of their own, and
 * switch any of them off (ChatPrefs keeps all that and hands [pool] over).
 */
object ThinkingWords {
    /** What the line draws from right now; with nothing on, [defaults]. */
    @Volatile var pool: List<String> = emptyList()

    /** Claude's list, as shipped. */
    val defaults: List<String> get() = words

    private val words = listOf(
        "Thinking", "Baking", "Beaming", "Beboppin'", "Befuddling", "Billowing", "Blanching", "Bloviating",
        "Boogieing", "Boondoggling", "Booping", "Brewing", "Burrowing", "Canoodling", "Caramelizing",
        "Cascading", "Catapulting", "Channeling", "Choreographing", "Churning", "Coalescing", "Cogitating",
        "Combobulating", "Composing", "Concocting", "Considering", "Contemplating", "Cooking", "Crafting",
        "Creating", "Cultivating", "Daydreaming", "Deciphering", "Deliberating", "Dilly-dallying",
        "Discombobulating", "Doodling", "Drizzling", "Embellishing", "Enchanting", "Envisioning",
        "Fermenting", "Fiddle-faddling", "Finagling", "Flambeing", "Flibbertigibbeting", "Flowing",
        "Flummoxing", "Fluttering", "Forging", "Frolicking", "Frosting", "Gallivanting", "Galloping",
        "Garnishing", "Germinating", "Grooving", "Gusting", "Harmonizing", "Hatching", "Herding", "Honking",
        "Hullaballooing", "Ideating", "Imagining", "Improvising", "Incubating", "Infusing", "Jitterbugging",
        "Julienning", "Kneading", "Leavening", "Levitating", "Lollygagging", "Manifesting", "Marinating",
        "Meandering", "Misting", "Moonwalking", "Moseying", "Mulling", "Mustering", "Musing", "Nesting",
        "Noodling", "Orbiting", "Orchestrating", "Perambulating", "Percolating", "Perusing", "Philosophising",
        "Pollinating", "Pondering", "Pontificating", "Pouncing", "Prestidigitating", "Proofing", "Puttering",
        "Puzzling", "Razzle-dazzling", "Razzmatazzing", "Recombobulating", "Roosting", "Ruminating",
        "Sauteing", "Scampering", "Schlepping", "Scurrying", "Seasoning", "Shenaniganing", "Shimmying",
        "Simmering", "Skedaddling", "Sketching", "Slithering", "Smooshing", "Sock-hopping", "Spelunking",
        "Spinning", "Sprouting", "Stewing", "Swirling", "Swooping", "Tempering", "Thundering", "Tinkering",
        "Tomfoolering", "Topsy-turvying", "Transfiguring", "Twisting", "Undulating", "Unfurling",
        "Unravelling", "Vibing", "Waddling", "Wandering", "Whatchamacalliting", "Whirlpooling", "Whirring",
        "Whisking", "Wibbling", "Wrangling", "Zesting", "Zigzagging",
    )

    /** A word for [seed] (a message's id), so one reply keeps its word across recompositions. */
    fun forSeed(seed: Long): String {
        val from = pool.ifEmpty { words }
        return from[Random(seed).nextInt(from.size)]
    }
}
