The Lalka reader fixtures were captured from Wolne Lektury on 2026-10-30.

- `lalka-malformed-audio.epub`: metadata, SMIL, navigation, styling, and readable
  text from the original `lalka-tom-pierwszy.audio.epub`, with binary audio omitted.
  Its later audio tracks reuse chapter I's anchors; missing anchors must invalidate
  the timings while preserving the usable XHTML as a static reading fallback.
- `lalka-valid-daisy.zip`: the text, metadata, and timing entries from
  `lalka-tom-pierwszy.daisy.zip`, with binary audio omitted. The original DAISY
  chapter VI begins with `book5.html#sec1` at 0 seconds and `sec2` at 10.360 seconds.

Sources:
https://wolnelektury.pl/media/book/audio.epub/lalka-tom-pierwszy.audio.epub
https://wolnelektury.pl/media/book/daisy.zip/lalka-tom-pierwszy.daisy.zip

## Attribution

*Lalka, tom pierwszy* by Bolesław Prus is in the public domain. Fundacja
Nowoczesna Polska published the digital edition through Wolne Lektury. The
National Library of Poland digitized its copy. Aleksandra Sekuła and Olga
Sutkowska edited the text and wrote the notes. Wolne Lektury distributes the
edition's supplementary materials under the Free Art License 1.3. The
LibriVox Mobile project prepared these test fixtures by omitting the audio.

[Wolne Lektury edition](https://wolnelektury.pl/katalog/lektura/lalka-tom-pierwszy.html) ·
[Free Art License 1.3](https://artlibre.org/licence/lal/en/)
