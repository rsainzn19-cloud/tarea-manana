src = open('/home/user/tarea-manana/estudio-diorama/index.html').read()
tpl = open('/home/user/tarea-manana/meteoro-thunderhead/plantilla.html').read()
def between(a, b, inclusive_end=False):
    i = src.index(a); j = src.index(b, i)
    if inclusive_end: j += len(b)
    return src[i:j].rstrip() + '\n'
blocks = {
  'HELPERS': between("const TAU = Math.PI * 2, PI = Math.PI;", "const REDUCED = window.matchMedia('(prefers-reduced-motion: reduce)').matches;", True),
  'MAT': between("// soft, slightly glossy plastic finish", "// ---------- faces"),
  'FACES': between("// ---------- faces", "// ---------- characters ----------"),
  'CHARS': between("// ---------- characters ----------", "const DEFS = {"),
  'BUILDCHAR': between("function buildCharacter(id, d) {", "// ---------- procedural animation ----------"),
  'ANIM': between("// ---------- procedural animation ----------", "// ---------- the floating island ----------"),
  'ENV': between("function jitter(", "const GRASS = (function"),
  'GRASS': between("const GRASS = (function", "buildIsland(world);"),
  'FX': between("// ---------- effects ----------", "const sceneFB = makeFireball();"),
}
bogus = "for (const s of [-1, 1]) strip(i => rowLift(i, s * HW, -0.32), i => rowLift(i, s * HW, -0.32), [0, () => 0], [1, () => 0], mat(0x8c84b0)); // keeps the strip helper's shape; real underside below\n"
assert tpl.count(bogus) == 1; tpl = tpl.replace(bogus, '')
for k, v in blocks.items():
    tag = '//@@' + k + '@@'
    assert tpl.count(tag) == 1, k
    tpl = tpl.replace(tag, v)
open('/home/user/tarea-manana/meteoro-thunderhead/index.html', 'w').write(tpl)
print({k: len(v.splitlines()) for k, v in blocks.items()})
