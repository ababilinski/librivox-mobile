import test from 'node:test';
import assert from 'node:assert/strict';
import { bookLink, sourcePage } from '../docs/open/book-link.mjs';

test('requested books retain their identity and publisher fallback', () => {
  for (const [slug, title] of [['antek', 'Antek'], ['katarynka', 'Katarynka']]) {
    const link = bookLink(`?bookId=wolnelektury-${slug}`);
    assert.equal(link.title, title);
    assert.equal(link.source, `https://wolnelektury.pl/katalog/lektura/${slug}/`);
    assert.ok(link.intent.startsWith(`intent://open?bookId=wolnelektury-${slug}`));
    assert.ok(link.intent.includes(';package=com.librivox.mobile;'));
  }
});
test('chapter identity survives intent encoding', () => {
  const link = bookLink('?bookId=wolnelektury-antek&chapterId=chapter%201%23Intent%3B');
  assert.ok(link.intent.includes('chapterId=chapter+1%23Intent%3B'));
  assert.equal(link.intent.match(/#Intent;/g).length, 1);
});
test('untrusted source cannot redirect users or inject an intent directive', () => {
  for (const source of ['javascript:alert(1)', 'https://evil.test/', 'https://wolnelektury.pl.evil.test/katalog/lektura/antek/', 'https://user@wolnelektury.pl/katalog/lektura/antek/', 'https://wolnelektury.pl:444/katalog/lektura/antek/', 'https://wolnelektury.pl/katalog/lektura/../evil/']) {
    assert.equal(sourcePage(source), null, source);
    assert.equal(bookLink(`?bookId=unknown&source=${encodeURIComponent(source)}`).source, null);
  }
});
test('missing, oversized and control-character identities are rejected', () => {
  for (const search of ['', '?bookId=', '?bookId=%00', '?bookId='+'a'.repeat(513), '?bookId=x&chapterId=%0A', '?bookId=x&extra='+'x'.repeat(8192)]) assert.equal(bookLink(search), null);
});
test('publisher URLs discard irrelevant tracking data', () => {
  assert.equal(sourcePage('https://wolnelektury.pl/katalog/lektura/antek/?utm_source=x#text'), 'https://wolnelektury.pl/katalog/lektura/antek/');
});
