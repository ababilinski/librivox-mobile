package com.librivox.mobile.model

data class SourceDonationLink(
    val source: BookSource,
    val sourceName: String,
    val actionLabel: String,
    val description: String,
    val url: String,
)

val sourceDonationLinks: List<SourceDonationLink> = listOf(
    SourceDonationLink(
        source = BookSource.LibriVox,
        sourceName = "LibriVox",
        actionLabel = "Donate to LibriVox",
        description = "Open LibriVox donation information.",
        url = "https://librivox.org/pages/donate-to-librivox/",
    ),
    SourceDonationLink(
        source = BookSource.Lit2Go,
        sourceName = "Lit2Go",
        actionLabel = "Support Lit2Go",
        description = "Open the official Lit2Go giving page.",
        url = "https://etc.usf.edu/lit2go/giving/",
    ),
)

fun BookSource.donationLink(): SourceDonationLink? =
    sourceDonationLinks.firstOrNull { it.source == this }

fun AudioBook.donationLink(): SourceDonationLink? = source.donationLink()
